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
            val bytes = readAssetBytes(context, assetPath)
            onLog("Читаю из APK ${passport.entwareInstaller} (${bytes.size / 1024} КБ)...")

            val session = RciSession(passport.host, user, pass)
            session.ensureAuth()

            // Enable WebDAV server briefly if component installed
            if (passport.hasWebdavComp) {
                val port = if (passport.webdavPort > 0) passport.webdavPort else 8081
                onLog("Проверяю WebDAV на роутере (порт $port)...")
                session.parseCli(listOf(
                    "webdav enable",
                    "webdav port $port",
                    "webdav anonymous",
                    "system configuration save"
                ))

                val shareName = if (targetMedia.isInternalStorage) "storage" else targetMedia.label.ifBlank { targetMedia.name }
                val candidates = listOf(
                    "/install" to "/install/${passport.entwareInstaller}",
                    "/$shareName/install" to "/$shareName/install/${passport.entwareInstaller}",
                    "/webdav/$shareName/install" to "/webdav/$shareName/install/${passport.entwareInstaller}"
                )
                var uploadedPath: String? = null
                for ((dir, path) in candidates) {
                    session.mkcolWebDav(port, dir)
                    val putRes = session.putWebDavFile(port, path, bytes)
                    if (putRes.isSuccess) {
                        uploadedPath = path
                        onLog("Залито по WebDAV -> $path (${putRes.getOrNull()})")
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
            error("WebDAV недоступен или не включен — переходите к инструкции ручного копирования /install/${passport.entwareInstaller}.")
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
