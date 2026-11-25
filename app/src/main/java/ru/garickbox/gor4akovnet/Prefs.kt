package ru.garickbox.gor4akovnet

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("gor4akovnet_prefs", Context.MODE_PRIVATE)
    private val backupsDir = File(context.filesDir, "backups").apply { mkdirs() }

    var routerHost: String
        get() = sp.getString("router_host", "") ?: ""
        set(v) = sp.edit().putString("router_host", v.trim()).apply()

    var rciUser: String
        get() = sp.getString("rci_user", "admin") ?: "admin"
        set(v) = sp.edit().putString("rci_user", v.trim()).apply()

    var rciPass: String
        get() = sp.getString("rci_pass", "") ?: ""
        set(v) = sp.edit().putString("rci_pass", v).apply()

    var sshPort: Int
        get() = sp.getInt("ssh_port", 222)
        set(v) = sp.edit().putInt("ssh_port", v).apply()

    var sshUser: String
        get() = sp.getString("ssh_user", "root") ?: "root"
        set(v) = sp.edit().putString("ssh_user", v.trim()).apply()

    var sshPass: String
        get() = sp.getString("ssh_pass", "keenetic") ?: "keenetic"
        set(v) = sp.edit().putString("ssh_pass", v).apply()

    var wifiSsid: String
        get() = sp.getString("wifi_ssid", "Gor4akovNET") ?: "Gor4akovNET"
        set(v) = sp.edit().putString("wifi_ssid", v.trim().ifBlank { "Gor4akovNET" }).apply()

    var wifiPass: String
        get() = sp.getString("wifi_pass", "gor4akov2026") ?: "gor4akov2026"
        set(v) = sp.edit().putString("wifi_pass", v).apply()

    var isolatedMode: Boolean
        get() = sp.getBoolean("isolated_mode", true)
        set(v) = sp.edit().putBoolean("isolated_mode", v).apply()

    var optAdguard: Boolean
        get() = sp.getBoolean("opt_adguard", false)
        set(v) = sp.edit().putBoolean("opt_adguard", v).apply()

    var optWarp: Boolean
        get() = sp.getBoolean("opt_warp", false)
        set(v) = sp.edit().putBoolean("opt_warp", v).apply()

    var wizardCompleted: Boolean
        get() = sp.getBoolean("wizard_done", false)
        set(v) = sp.edit().putBoolean("wizard_done", v).apply()

    var initialBackupDoneHost: String
        get() = sp.getString("initial_bkp_host", "") ?: ""
        set(v) = sp.edit().putString("initial_bkp_host", v.trim()).apply()

    var benchmarkBaselineJson: String
        get() = sp.getString("bench_baseline_json", "") ?: ""
        set(v) = sp.edit().putString("bench_baseline_json", v).apply()

    // App access lock & one-time install licensing
    var appPinHash: String
        get() = sp.getString("app_pin_hash", "") ?: ""
        set(v) = sp.edit().putString("app_pin_hash", v).apply()

    val hasAppPin: Boolean
        get() = appPinHash.isNotBlank()

    var privilegedMode: Boolean
        get() = sp.getBoolean("priv_mode", false)
        set(v) = sp.edit().putBoolean("priv_mode", v).apply()

    var networkInstallLocked: Boolean
        get() = sp.getBoolean("net_install_locked", false)
        set(v) = sp.edit().putBoolean("net_install_locked", v).apply()

    var installId: String
        get() {
            val cur = sp.getString("install_id", "") ?: ""
            if (cur.isNotBlank()) return cur
            val fresh = UUID.randomUUID().toString().replace("-", "").take(12)
            sp.edit().putString("install_id", fresh).apply()
            return fresh
        }
        private set(v) = sp.edit().putString("install_id", v).apply()

    var telemetryJson: String
        get() = sp.getString("telemetry_state", "{}") ?: "{}"
        set(v) = sp.edit().putString("telemetry_state", v).apply()

    fun setAppPin(rawPin: String): Boolean {
        val clean = rawPin.trim()
        val priv = isPrivilegedInput(clean)
        appPinHash = sha256("pin_v1:$clean")
        privilegedMode = priv
        return priv
    }

    fun verifyAppPin(rawPin: String): Pair<Boolean, Boolean> {
        val clean = rawPin.trim()
        val priv = isPrivilegedInput(clean)
        if (priv) {
            privilegedMode = true
            return true to true
        }
        val ok = appPinHash.isNotBlank() && sha256("pin_v1:$clean") == appPinHash
        return ok to privilegedMode
    }

    fun saveBackup(label: String, model: String, configText: String, isProtected: Boolean = false): BackupEntry {
        return saveBinaryBackup(
            label = label,
            model = model,
            bytes = configText.toByteArray(Charsets.UTF_8),
            ext = "txt",
            format = "txt",
            isProtected = isProtected
        )
    }

    fun saveBinaryBackup(
        label: String,
        model: String,
        bytes: ByteArray,
        ext: String = "tar.gz",
        format: String = if (ext.contains("tar")) "tar_v1" else "txt",
        isProtected: Boolean = false
    ): BackupEntry {
        val id = uniqueBackupId()
        val safeLabel = label.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]"), "_").take(32)
        val cleanExt = ext.trimStart('.').ifBlank { "txt" }
        val file = File(backupsDir, "backup_${id}_${safeLabel}.$cleanExt")
        file.writeBytes(bytes)
        return registerBackupFile(id, label, model, file, format, isProtected)
    }

    fun allocateStreamBackupTempFile(label: String, ext: String = "tar.gz"): File {
        val id = uniqueBackupId()
        val safeLabel = label.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]"), "_").take(32)
        val cleanExt = ext.trimStart('.').ifBlank { "tar.gz" }
        return File(backupsDir, "backup_${id}_${safeLabel}.$cleanExt")
    }

    fun commitStreamedBackupFile(
        file: File,
        label: String,
        model: String,
        format: String = "tar_v2",
        isProtected: Boolean = false
    ): BackupEntry {
        val id = file.name.removePrefix("backup_").substringBefore("_", uniqueBackupId())
        return registerBackupFile(id, label, model, file, format, isProtected)
    }

    fun importBackupFromUri(uri: Uri): Result<BackupEntry> = runCatching {
        val inp = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть выбранный файл")
        val temp = allocateStreamBackupTempFile("Импорт", "tmp")
        inp.use { src ->
            temp.outputStream().use { dst -> src.copyTo(dst) }
        }
        val size = temp.length()
        if (size < 32L) {
            temp.delete()
            error("Файл слишком мал ($size Б)")
        }
        val head = ByteArray(minOf(4, size.toInt()))
        FileInputStream(temp).use { it.read(head) }
        val isGzip = head.size >= 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()
        val ext = if (isGzip) "tar.gz" else "txt"
        val format = if (isGzip) "tar_v2" else "txt"
        val id = uniqueBackupId()
        val dest = File(backupsDir, "backup_${id}_Импорт.$ext")
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
        registerBackupFile(id, "Импортированный бэкап", "Внешний файл", dest, format, isProtected = false)
    }

    private fun uniqueBackupId(): String {
        val base = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${base}_${UUID.randomUUID().toString().take(4)}"
    }

    private fun registerBackupFile(
        id: String,
        label: String,
        model: String,
        file: File,
        format: String,
        isProtected: Boolean
    ): BackupEntry {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val size = file.length()
        val digest = sha256File(file)
        val arr = readBackupMetaArray()
        val obj = JSONObject().apply {
            put("id", id)
            put("timestamp", stamp)
            put("label", label)
            put("model", model)
            put("size", size)
            put("file", file.absolutePath)
            put("format", format)
            put("protected", isProtected)
            put("sha256", digest)
        }

        // Ротация: защищённые снимки хранятся вечно, обычные обрезаем после 35 записей с удалением старых файлов
        val existing = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        val keptProtected = existing.filter { it.optBoolean("protected", false) }
        val regular = existing.filter { !it.optBoolean("protected", false) }
        val keptRegular = regular.take(35)
        val evictedRegular = regular.drop(35)
        evictedRegular.forEach { old ->
            val p = old.optString("file")
            if (p.isNotBlank() && p != file.absolutePath) {
                runCatching { File(p).delete() }
            }
        }

        val next = JSONArray().apply {
            put(obj)
            keptRegular.forEach { put(it) }
            keptProtected.forEach { put(it) }
        }
        sp.edit().putString("backups_meta", next.toString()).apply()
        return BackupEntry(id, stamp, label, model, size, file.absolutePath, format, isProtected, digest)
    }

    fun listBackups(): List<BackupEntry> {
        val arr = readBackupMetaArray()
        val list = mutableListOf<BackupEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val path = o.optString("file")
            val f = File(path)
            if (f.exists()) {
                val fmt = o.optString("format", "").ifBlank {
                    if (path.endsWith(".tar.gz", true)) "tar_v1" else "txt"
                }
                list.add(
                    BackupEntry(
                        id = o.optString("id"),
                        timestamp = o.optString("timestamp"),
                        label = o.optString("label"),
                        model = o.optString("model"),
                        sizeBytes = o.optLong("size", f.length()),
                        filePath = path,
                        format = fmt,
                        isProtected = o.optBoolean("protected", false),
                        sha256 = o.optString("sha256", "")
                    )
                )
            }
        }
        return list.sortedWith(compareByDescending<BackupEntry> { it.isProtected }.thenByDescending { it.timestamp })
    }

    fun readBackupText(entry: BackupEntry): String =
        runCatching { File(entry.filePath).readText(Charsets.UTF_8) }.getOrDefault("")

    fun deleteBackup(id: String) {
        val arr = readBackupMetaArray()
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                runCatching { File(o.optString("file")).delete() }
            } else {
                next.put(o)
            }
        }
        sp.edit().putString("backups_meta", next.toString()).apply()
    }

    private fun readBackupMetaArray(): JSONArray {
        val raw = sp.getString("backups_meta", "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    companion object {
        private const val K_SALT = "gbox_ndms_2026_k9"
        private const val K_DIGEST = "b58f2f8500566b2694c6d842da1263eb5a981f42fdab71c8f87e1369ab513d75"

        private fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(s.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun sha256File(file: File): String = runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(65536)
            FileInputStream(file).use { fis ->
                while (true) {
                    val r = fis.read(buf)
                    if (r <= 0) break
                    md.update(buf, 0, r)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")

        fun isPrivilegedInput(raw: String): Boolean =
            sha256("$K_SALT:${raw.trim()}") == K_DIGEST

        private fun of(c: Context) = Prefs(c)
        fun sshHost(c: Context): String = of(c).routerHost.ifBlank { "192.168.1.1" }
        fun sshPort(c: Context): Int = of(c).sshPort
        fun sshUser(c: Context): String = of(c).sshUser
        fun sshPass(c: Context): String = of(c).sshPass
        fun setSshHost(c: Context, v: String) { of(c).routerHost = v }
        fun setSshUser(c: Context, v: String) { of(c).sshUser = v }
        fun setSshPort(c: Context, v: Int) { of(c).sshPort = v }
        fun setSshPass(c: Context, v: String) { of(c).sshPass = v }
    }
}
