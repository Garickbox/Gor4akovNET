package ru.garickbox.gor4akovnet

import android.content.Context
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class OfflineManifestInfo(
    val available: Boolean,
    val z2kTag: String,
    val adguardTag: String,
    val archesReady: List<String>,
    val totalMb: Float
)

object OfflineAssets {
    private fun assetExists(context: Context, path: String): Boolean =
        runCatching { context.assets.open(path).close(); true }.getOrDefault(false)

    private fun readAssetBytes(context: Context, path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }

    private fun resolveVariantAsset(context: Context, xzPath: String, gzPath: String): Pair<String, Boolean>? = when {
        assetExists(context, xzPath) -> xzPath to true
        assetExists(context, gzPath) -> gzPath to false
        else -> null
    }

    fun inspectManifest(context: Context): OfflineManifestInfo {
        return runCatching {
            val json = JSONObject(readAssetBytes(context, "offline/manifest.json").toString(Charsets.UTF_8))
            val ent = json.optJSONObject("entware")
            val z2k = json.optJSONObject("z2k")
            val agh = json.optJSONObject("adguard")
            val arches = mutableListOf<String>()
            var totalBytes = 0L
            ent?.keys()?.forEach { k ->
                arches.add(k)
                totalBytes += ent.optJSONObject(k)?.optLong("size", 0L) ?: 0L
            }
            val ipks = json.optJSONObject("ipks")
            ipks?.keys()?.forEach { k ->
                totalBytes += ipks.optJSONObject(k)?.optLong("size", 0L) ?: 0L
            }
            totalBytes += z2k?.optLong("repo_size", 0L) ?: 0L
            totalBytes += z2k?.optLong("zapret2_size", 0L) ?: 0L
            val aghArches = agh?.optJSONObject("arches")
            aghArches?.keys()?.forEach { k ->
                totalBytes += aghArches.optJSONObject(k)?.optLong("size", 0L) ?: 0L
            }
            OfflineManifestInfo(
                available = arches.isNotEmpty(),
                z2kTag = z2k?.optString("tag", "unknown") ?: "unknown",
                adguardTag = agh?.optString("tag", "-") ?: "-",
                archesReady = arches,
                totalMb = totalBytes / 1024f / 1024f
            )
        }.getOrElse {
            OfflineManifestInfo(false, "-", "-", emptyList(), 0f)
        }
    }

    suspend fun uploadEntwareViaWebdavAndConfigure(
        context: Context,
        passport: RouterPassport,
        user: String,
        pass: String,
        targetMedia: StorageMedia,
        onLog: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val gzName = passport.entwareInstaller
            val xzName = gzName.removeSuffix(".gz") + ".xz"
            val resolved = resolveVariantAsset(context, "offline/entware/$xzName", "offline/entware/$gzName")
                ?: error("Офлайн-архив $gzName/.xz не найден в APK.")
            if (targetMedia.freeMb in 1..15) {
                error("Накопитель ${targetMedia.label} почти заполнен (${targetMedia.freeMb} МБ свободно, нужно минимум 16 МБ).")
            }

            val rawAsset = readAssetBytes(context, resolved.first)
            val bytes = if (resolved.second) {
                onLog("Распаковываю ${resolved.first.substringAfterLast('/')} (${rawAsset.size / 1024} КБ) -> .tar.gz для NDMS...")
                Gboxcore.decompressXzToGz(rawAsset)
            } else {
                rawAsset
            }
            onLog("Подготовлен $gzName (${bytes.size / 1024} КБ)")

            val session = RciSession(passport.host, user, pass)
            session.ensureAuth()

            if (passport.hasWebdavComp) {
                onLog("Включаю WebDAV на роутере...")
                runCatching {
                    val (_, wErrs) = session.parseCliChecked(listOf(
                        "ip http webdav",
                        "ip http webdav enable",
                        "ip http webdav security-level private",
                        "system configuration save"
                    ))
                    if (wErrs.isNotEmpty()) onLog("[WARN] WebDAV CLI: ${wErrs.first()}")
                }
                if (passport.ndmsMajor in 1..3) {
                    runCatching {
                        session.parseCli(listOf(
                            "webdav enable",
                            "webdav port ${if (passport.webdavPort > 0) passport.webdavPort else 8081}",
                            "webdav anonymous",
                            "system configuration save"
                        ))
                    }
                }

                val portsToTry = listOfNotNull(
                    passport.webdavPort.takeIf { it > 0 },
                    80,
                    8081,
                    8080
                ).distinct()

                val shareName = if (targetMedia.isInternalStorage) "storage" else targetMedia.label.ifBlank { targetMedia.name }
                val candidates = listOf(
                    "/install" to "/install/$gzName",
                    "/$shareName/install" to "/$shareName/install/$gzName",
                    "/webdav/$shareName/install" to "/webdav/$shareName/install/$gzName",
                    "/webdav/install" to "/webdav/install/$gzName",
                    "" to "/$gzName"
                )

                var uploadedPath: String? = null
                var livePort: Int? = null
                for (port in portsToTry) {
                    if (Gboxcore.probeTCP(passport.host, port, 1500)) { livePort = port; break }
                }
                if (livePort == null) {
                    error("WebDAV недоступен ни на одном порту (${portsToTry.joinToString("/")}). Включите «Сервер WebDAV» в компонентах роутера.")
                }
                onLog("WebDAV живой на порту $livePort, пробую залить ${bytes.size / 1024} КБ...")
                val deadline = System.currentTimeMillis() + 90_000L
                for ((dir, path) in candidates) {
                    val remaining = (deadline - System.currentTimeMillis()).toInt()
                    if (remaining <= 4000) {
                        onLog("[WARN] WebDAV PUT исчерпал 90с — прерываю перебор")
                        break
                    }
                    if (dir.isNotBlank()) session.mkcolWebDav(livePort, dir)
                    val putRes = session.putWebDavFile(livePort, path, bytes, readTimeoutMs = remaining)
                    if (putRes.isSuccess) {
                        uploadedPath = "$livePort:$path"
                        onLog("Залито по WebDAV -> http://${passport.host}:$livePort$path (${putRes.getOrNull()})")
                        break
                    }
                }

                if (uploadedPath != null) {
                    val diskId = if (targetMedia.isInternalStorage) "storage:" else "${targetMedia.uuid}:"
                    onLog("Настраиваю opkg disk $diskId и автозапуск Entware...")
                    val (oErrCnt, oErrs) = session.parseCliChecked(listOf(
                        "no opkg dns-override",
                        "opkg disk $diskId",
                        "opkg initrc /opt/etc/init.d/rc.unslung",
                        "system configuration save"
                    ))
                    if (oErrCnt > 0 && oErrs.isNotEmpty()) {
                        error("Ошибка opkg disk $diskId: ${oErrs.joinToString("; ")}")
                    }
                    return@runCatching "Entware загружен на роутер и запущен на $diskId. Подождите ~40 сек пока распакуется Dropbear SSH :222!"
                }
            }
            error("WebDAV недоступен или не включён — переходите к инструкции ручного копирования /install/$gzName.")
        }
    }

    suspend fun pushOfflineBypassBundleOverSSH(
        context: Context,
        host: String,
        sshPort: Int,
        sshUser: String,
        sshPass: String,
        entwareArch: String,
        includeAdguard: Boolean = false,
        onLog: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val optFreeKb = Gboxcore.runRouterSSH(host, sshPort, sshUser, sshPass, "df -Pk /opt 2>/dev/null | awk 'NR==2{print \$4}'")
                .trim().toLongOrNull() ?: 0L
            val neededKb = if (includeAdguard) 65_000L else 28_000L
            if (optFreeKb in 1 until neededKb) {
                error("На /opt свободно всего ${optFreeKb / 1024} МБ, нужно минимум ${neededKb / 1024} МБ для распаковки.")
            }

            Gboxcore.runRouterSSH(host, sshPort, sshUser, sshPass, "mkdir -p /opt/gor4akov-offline/ipks /opt/gor4akov-offline/z2k /opt/sbin")

            suspend fun pushTarPayload(label: String, xzAsset: String, gzAsset: String, remoteTarGz: String) {
                val resolved = resolveVariantAsset(context, xzAsset, gzAsset)
                if (resolved == null) {
                    onLog("Внимание: $xzAsset отсутствует в APK — будут использованы пакеты из сети.")
                    return
                }
                val data = readAssetBytes(context, resolved.first)
                onLog("Отправка $label (${data.size / 1024} КБ) -> роутер...")
                if (resolved.second) {
                    Gboxcore.pushXzAsGzSSH(host, sshPort, sshUser, sshPass, remoteTarGz, data)
                    val ok = Gboxcore.runRouterSSH(host, sshPort, sshUser, sshPass, "tar -tzf '$remoteTarGz' >/dev/null 2>&1 && echo OK || echo BAD")
                    if (!ok.contains("OK")) error("$label повреждён при потоковой распаковке на роутер.")
                } else {
                    Gboxcore.pushBytesSSH(host, sshPort, sshUser, sshPass, remoteTarGz, data)
                    val sizeOut = Gboxcore.runRouterSSH(host, sshPort, sshUser, sshPass, "wc -c < '$remoteTarGz' 2>/dev/null | tr -d ' '")
                    val remoteSize = sizeOut.trim().toLongOrNull() ?: 0L
                    if (remoteSize != data.size.toLong()) {
                        error("$label обрезан при передаче: локально ${data.size} Б, на роутере $remoteSize Б.")
                    }
                }
            }

            pushTarPayload(
                "офлайн-пакетов ($entwareArch)",
                "offline/ipks/ipks-$entwareArch.tar.xz",
                "offline/ipks/ipks-$entwareArch.tar.gz",
                "/opt/gor4akov-offline/ipks.tar.gz"
            )
            pushTarPayload(
                "z2k-enhanced",
                "offline/z2k/z2k-repo.tar.xz",
                "offline/z2k/z2k-repo.tar.gz",
                "/opt/gor4akov-offline/z2k-repo.tar.gz"
            )
            pushTarPayload(
                "движка nfqws2 / zapret2",
                "offline/z2k/zapret2-openwrt-embedded.tar.xz",
                "offline/z2k/zapret2-openwrt-embedded.tar.gz",
                "/opt/gor4akov-offline/openwrt-embedded.tar.gz"
            )

            if (includeAdguard) {
                val aghAsset = "offline/adguard/AdGuardHome-$entwareArch.bin.xz"
                if (assetExists(context, aghAsset)) {
                    val xzBytes = readAssetBytes(context, aghAsset)
                    onLog("Передача офлайн AdGuard Home (${xzBytes.size / 1024} КБ в .xz -> /opt/sbin/AdGuardHome)...")
                    Gboxcore.pushXzBinarySSH(host, sshPort, sshUser, sshPass, "/opt/sbin/AdGuardHome", xzBytes)
                    val sz = Gboxcore.runRouterSSH(host, sshPort, sshUser, sshPass, "wc -c < /opt/sbin/AdGuardHome 2>/dev/null | tr -d ' '")
                        .trim().toLongOrNull() ?: 0L
                    if (sz < 5_000_000L) {
                        error("Бинарник AdGuardHome не дописан на роутер ($sz Б).")
                    }
                    onLog("[OK] AdGuard Home развёрнут в /opt/sbin/AdGuardHome (${sz / 1024 / 1024} МБ)")
                } else {
                    onLog("Внимание: офлайн AdGuardHome ($aghAsset) не найден в APK — попробуем opkg из сети.")
                }
            }
        }
    }

    suspend fun pushSingleAdguardOverSSH(
        context: Context,
        host: String,
        sshPort: Int,
        sshUser: String,
        sshPass: String,
        entwareArch: String,
        onLog: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val aghAsset = "offline/adguard/AdGuardHome-$entwareArch.bin.xz"
            if (!assetExists(context, aghAsset)) {
                error("Офлайн AdGuardHome для $entwareArch не найден в APK.")
            }
            val xzBytes = readAssetBytes(context, aghAsset)
            onLog("Отправка AdGuard Home v1.0.0 (${xzBytes.size / 1024} КБ .xz) -> /opt/sbin/AdGuardHome...")
            Gboxcore.pushXzBinarySSH(host, sshPort, sshUser, sshPass, "/opt/sbin/AdGuardHome", xzBytes)
            val sz = Gboxcore.runRouterSSH(host, sshPort, sshUser, sshPass, "wc -c < /opt/sbin/AdGuardHome 2>/dev/null | tr -d ' '")
                .trim().toLongOrNull() ?: 0L
            if (sz < 5_000_000L) error("Бинарник AdGuardHome повреждён ($sz Б)")
        }
    }
}
