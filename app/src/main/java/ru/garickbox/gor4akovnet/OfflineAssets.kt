package ru.garickbox.gor4akovnet

import android.content.Context
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class OfflineManifestInfo(
    val available: Boolean,
    val z2kTag: String,
    val archesReady: List<String>,
    val totalMb: Float
)

object OfflineAssets {
    private fun assetExists(context: Context, path: String): Boolean =
        runCatching { context.assets.open(path).close(); true }.getOrDefault(false)

    private fun readAssetBytes(context: Context, path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }

    fun inspectManifest(context: Context): OfflineManifestInfo {
        return runCatching {
            val json = JSONObject(readAssetBytes(context, "offline/manifest.json").toString(Charsets.UTF_8))
            val ent = json.optJSONObject("entware")
            val z2k = json.optJSONObject("z2k")
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
            OfflineManifestInfo(
                available = arches.isNotEmpty(),
                z2kTag = z2k?.optString("tag", "unknown") ?: "unknown",
                archesReady = arches,
                totalMb = totalBytes / 1024f / 1024f
            )
        }.getOrElse {
            OfflineManifestInfo(false, "-", emptyList(), 0f)
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
            val assetPath = "offline/entware/${passport.entwareInstaller}"
            if (!assetExists(context, assetPath)) {
                error("Оффлайн-архив $assetPath не найден в APK.")
            }
            if (targetMedia.freeMb in 1..15) {
                error("Накопитель ${targetMedia.label} почти заполнен (${targetMedia.freeMb} МБ свободно, нужно минимум 16 МБ).")
            }
            val bytes = readAssetBytes(context, assetPath)
            onLog("Читаю из APK ${passport.entwareInstaller} (${bytes.size / 1024} КБ)...")

            val session = RciSession(passport.host, user, pass)
            session.ensureAuth()

            if (passport.hasWebdavComp) {
                onLog("Включаю WebDAV на роутере...")
                // NDMS 4.x/5.x syntax + NDMS 3.x fallback (ignored cleanly if unsupported)
                runCatching {
                    session.parseCli(listOf(
                        "ip http webdav",
                        "ip http webdav enable",
                        "ip http webdav security-level private",
                        "system configuration save"
                    ))
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
                    "/install" to "/install/${passport.entwareInstaller}",
                    "/$shareName/install" to "/$shareName/install/${passport.entwareInstaller}",
                    "/webdav/$shareName/install" to "/webdav/$shareName/install/${passport.entwareInstaller}",
                    "/webdav/install" to "/webdav/install/${passport.entwareInstaller}",
                    "" to "/${passport.entwareInstaller}"
                )

                // BUG-T5/T6: сначала пробуем ТОЛЬКО первый живой порт с первым кандидатом.
                // Если не вышло — перебираем остальное, но НЕ больше 90 секунд суммарно.
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
                    if (System.currentTimeMillis() > deadline) {
                        onLog("[WARN] WebDAV PUT исчерпал 90с — прерываю перебор")
                        break
                    }
                    if (dir.isNotBlank()) session.mkcolWebDav(livePort, dir)
                    val putRes = session.putWebDavFile(livePort, path, bytes)
                    if (putRes.isSuccess) {
                        uploadedPath = "$livePort:$path"
                        onLog("Залито по WebDAV -> http://${passport.host}:$livePort$path (${putRes.getOrNull()})")
                        break
                    }
                }

                if (uploadedPath != null) {
                    val diskId = if (targetMedia.isInternalStorage) "storage:" else "${targetMedia.uuid}:"
                    onLog("Настраиваю opkg disk $diskId и автозапуск Entware...")
                    session.parseCli(listOf(
                        "no opkg dns-override",
                        "opkg disk $diskId",
                        "opkg initrc /opt/etc/init.d/rc.unslung",
                        "system configuration save"
                    ))
                    return@runCatching "Entware загружен на роутер и запущен на $diskId. Подождите ~40 сек пока распакуется Dropbear SSH :222!"
                }
            }
            error("WebDAV недоступен или не включён — переходите к инструкции ручного копирования /install/${passport.entwareInstaller}.")
        }
    }

    suspend fun pushOfflineBypassBundleOverSSH(
        context: Context,
        host: String,
        sshPort: Int,
        sshUser: String,
        sshPass: String,
        entwareArch: String,
        onLog: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ipksPath = "offline/ipks/ipks-$entwareArch.tar.gz"
            val z2kRepoPath = "offline/z2k/z2k-repo.tar.gz"
            val zapret2Path = "offline/z2k/zapret2-openwrt-embedded.tar.gz"

            Gboxcore.runRouterSSH(host, sshPort, sshUser, sshPass, "mkdir -p /opt/gor4akov-offline/ipks /opt/gor4akov-offline/z2k")

            if (assetExists(context, ipksPath)) {
                val data = readAssetBytes(context, ipksPath)
                onLog("Отправка офлайн-пакетов ($entwareArch, ${data.size / 1024} КБ) -> роутер...")
                Gboxcore.pushBytesSSH(host, sshPort, sshUser, sshPass, "/opt/gor4akov-offline/ipks.tar.gz", data)
            } else {
                onLog("Внимание: $ipksPath отсутствует в APK — будут использованы пакеты из сети.")
            }

            if (assetExists(context, z2kRepoPath)) {
                val data = readAssetBytes(context, z2kRepoPath)
                onLog("Отправка z2k-enhanced (${data.size / 1024} КБ) -> роутер...")
                Gboxcore.pushBytesSSH(host, sshPort, sshUser, sshPass, "/opt/gor4akov-offline/z2k-repo.tar.gz", data)
            }

            if (assetExists(context, zapret2Path)) {
                val data = readAssetBytes(context, zapret2Path)
                onLog("Отправка движка nfqws2 / zapret2 (${data.size / 1024} КБ) -> роутер...")
                Gboxcore.pushBytesSSH(host, sshPort, sshUser, sshPass, "/opt/gor4akov-offline/openwrt-embedded.tar.gz", data)
            }
        }
    }
}
