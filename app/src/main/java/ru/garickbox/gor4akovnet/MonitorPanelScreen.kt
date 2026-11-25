package ru.garickbox.gor4akovnet

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private enum class MonitorGroup(val title: String) {
    SYSTEM("СИСТЕМА"),
    NET("СЕТЬ & DNS"),
    BYPASS("ОБХОД & WARP"),
    WIFI("WI-FI & КЛИЕНТЫ"),
    TOOLS("ИНСТРУМЕНТЫ")
}

private enum class MonitorPage(
    val group: MonitorGroup,
    val label: String,
    val icon: ImageVector,
    val helpKey: String
) {
    OVERVIEW(MonitorGroup.SYSTEM, "Обзор", Icons.Rounded.Speed, "overview"),
    SERVICES(MonitorGroup.SYSTEM, "Службы & opkg", Icons.Rounded.Memory, "services"),
    STORAGE(MonitorGroup.SYSTEM, "USB & NDMS", Icons.Rounded.Storage, "storage"),
    WAN(MonitorGroup.NET, "WAN & Сеть", Icons.Rounded.Public, "wan"),
    DNS(MonitorGroup.NET, "AdGuard DNS", Icons.Rounded.Dns, "dns"),
    NAT_PPE(MonitorGroup.NET, "PPE & Порты", Icons.Rounded.Security, "nat_ppe"),
    Z2K_MODES(MonitorGroup.BYPASS, "Zapret2 (z2k)", Icons.Rounded.Shield, "z2k"),
    WARP(MonitorGroup.BYPASS, "WARP & Игры", Icons.Rounded.Public, "warp"),
    Z2K_LISTS(MonitorGroup.BYPASS, "Подбор & Списки", Icons.Rounded.List, "z2k"),
    WIFI(MonitorGroup.WIFI, "Wi-Fi & Слоты AP", Icons.Rounded.Wifi, "wifi"),
    DEVICES(MonitorGroup.WIFI, "Клиенты", Icons.Rounded.Devices, "devices"),
    BACKUPS(MonitorGroup.TOOLS, "Бэкапы v2", Icons.Rounded.Save, "backups"),
    FILES(MonitorGroup.TOOLS, "X-plore Файлы", Icons.Rounded.Folder, "files"),
    TORCH(MonitorGroup.TOOLS, "Torch & Трафик", Icons.Rounded.NetworkCheck, "torch"),
    LOGS_SSH(MonitorGroup.TOOLS, "Логи & SSH", Icons.Rounded.Terminal, "logs")
}

private suspend fun runSshCmd(prefs: Prefs, cmd: String, readOnly: Boolean = false): String {
    val host = prefs.routerHost.ifBlank { "192.168.1.1" }
    val port = prefs.sshPort
    return withContext(Dispatchers.IO) {
        try {
            val first = Gboxcore.runRouterSSH(host, port, prefs.sshUser, prefs.sshPass, cmd)
            if (!readOnly || first.isNotBlank()) return@withContext first
            Thread.sleep(250)
            Gboxcore.runRouterSSH(host, port, prefs.sshUser, prefs.sshPass, cmd)
        } catch (e: Throwable) {
            val msg = e.message.orEmpty()
            if (readOnly && (msg.contains("EOF", true) || msg.contains("closed", true))) {
                Thread.sleep(250)
                Gboxcore.runRouterSSH(host, port, prefs.sshUser, prefs.sshPass, cmd)
            } else throw e
        }
    }
}

private fun parseJsonOut(raw: String, tag: String): JSONObject {
    val i = raw.indexOf('{')
    if (i < 0) {
        val preview = raw.trim().take(180)
        throw IllegalArgumentException(if (preview.isBlank()) "$tag: пустой ответ роутера" else "$tag (не JSON): $preview")
    }
    var depth = 0
    var inStr = false
    var esc = false
    var end = -1
    for (k in i until raw.length) {
        val c = raw[k]
        if (esc) { esc = false; continue }
        if (c == '\\') { esc = true; continue }
        if (c == '"') { inStr = !inStr; continue }
        if (inStr) continue
        when (c) {
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) { end = k; break } }
        }
    }
    if (end < 0) throw IllegalArgumentException("$tag: незакрытый JSON (${raw.take(120)})")
    return JSONObject(raw.substring(i, end + 1))
}

private fun formatUptime(sec: Long): String {
    if (sec <= 0L) return "—"
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return if (d > 0) "${d}д ${h}ч ${m}м" else "${h}ч ${m}м"
}

private fun formatBytes(b: Double): String = when {
    b <= 0.0 -> "0 Б"
    b >= 1073741824.0 -> String.format("%.2f ГБ", b / 1073741824.0)
    b >= 1048576.0 -> String.format("%.1f МБ", b / 1048576.0)
    b >= 1024.0 -> String.format("%.0f КБ", b / 1024.0)
    else -> String.format("%.0f Б", b)
}

private fun formatSizeMb(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f МБ", bytes / 1048576.0)
    bytes >= 1024L -> "${bytes / 1024L} КБ"
    else -> "$bytes Б"
}

private fun decodeB64(b64: String): String {
    if (b64.isBlank()) return ""
    return runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault("")
}

@Composable
fun MonitorPanelScreen(
    prefs: Prefs,
    passport: RouterPassport? = null,
    onBackupSaved: (BackupEntry) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var page by remember { mutableStateOf(MonitorPage.OVERVIEW) }
    var sidebarOpen by remember { mutableStateOf(true) }
    var autoCollapse by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var loadGen by remember { mutableIntStateOf(0) }
    var statusMsg by remember { mutableStateOf("") }
    var pageErrMsg by remember { mutableStateOf("") }
    var actionErrMsg by remember { mutableStateOf("") }

    var helpTopic by remember { mutableStateOf<MonitorHelpTopic?>(null) }
    var svcModalKey by remember { mutableStateOf<String?>(null) }
    var confirmStopDropbear by remember { mutableStateOf<Pair<String, String>?>(null) }

    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var services by remember { mutableStateOf<JSONObject?>(null) }
    var wanNet by remember { mutableStateOf<JSONObject?>(null) }
    var dnsNet by remember { mutableStateOf<JSONObject?>(null) }
    var bypass by remember { mutableStateOf<JSONObject?>(null) }
    var warpFull by remember { mutableStateOf<JSONObject?>(null) }
    var wifiDevs by remember { mutableStateOf<JSONObject?>(null) }

    // Sites probe & Optimizer Do/Posle
    var siteProbeJson by remember { mutableStateOf<JSONObject?>(null) }
    var diagOut by remember { mutableStateOf("") }
    var benchBaseline by remember {
        mutableStateOf(
            runCatching {
                if (prefs.benchmarkBaselineJson.isNotBlank()) JSONObject(prefs.benchmarkBaselineJson) else null
            }.getOrNull()
        )
    }
    var benchAfter by remember { mutableStateOf<JSONObject?>(null) }

    // Sub-outputs
    var opkgOut by remember { mutableStateOf("") }
    var benchOut by remember { mutableStateOf("") }
    var dnsProbeOut by remember { mutableStateOf("") }
    var siteProbeOut by remember { mutableStateOf("") }
    var pickOut by remember { mutableStateOf("") }
    var warpTraceOut by remember { mutableStateOf("") }
    var torchHost by remember { mutableStateOf("1.1.1.1") }
    var torchOut by remember { mutableStateOf("") }
    var torchLive by remember { mutableStateOf(false) }
    var torchData by remember { mutableStateOf<JSONObject?>(null) }
    var logsOut by remember { mutableStateOf("") }
    var logSrc by remember { mutableStateOf("agh") }
    var logFilter by remember { mutableStateOf("") }
    var consoleOut by remember { mutableStateOf("") }
    var customCmd by remember { mutableStateOf("") }

    // Inputs
    var domainInput by remember { mutableStateOf("") }
    var wlInput by remember { mutableStateOf("") }
    var pickDomain by remember { mutableStateOf("youtube.com") }
    var devSearch by remember { mutableStateOf("") }
    var devFilter by remember { mutableStateOf("all") }
    var selectedDevice by remember { mutableStateOf<JSONObject?>(null) }

    // Files
    var dirPath by remember { mutableStateOf("/opt/zapret2") }
    var dirData by remember { mutableStateOf<JSONObject?>(null) }
    var fileModalPath by remember { mutableStateOf("") }
    var fileModalText by remember { mutableStateOf("") }
    var fileModalTrunc by remember { mutableStateOf(false) }
    var fileModalOpen by remember { mutableStateOf(false) }
    var newEntryMode by remember { mutableStateOf<String?>(null) }
    var newEntryName by remember { mutableStateOf("") }

    // Backups
    var backupsList by remember { mutableStateOf(prefs.listBackups()) }
    var backupEst by remember { mutableStateOf("") }
    var includeMediaBackup by remember { mutableStateOf(false) }
    var restoreEntry by remember { mutableStateOf<BackupEntry?>(null) }
    var backupPreviewText by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmSystemRestore by remember { mutableStateOf<BackupEntry?>(null) }

    // SSH dialog
    var showCreds by remember { mutableStateOf(false) }
    var confirmReboot by remember { mutableStateOf(false) }
    var sshHost by remember { mutableStateOf(prefs.routerHost.ifBlank { "192.168.1.1" }) }
    var sshUser by remember { mutableStateOf(prefs.sshUser) }
    var sshPort by remember { mutableStateOf(prefs.sshPort.toString()) }
    var sshPass by remember { mutableStateOf(prefs.sshPass) }
    var showPass by remember { mutableStateOf(false) }
    var bruteBusy by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true
                actionErrMsg = ""
                val res = withContext(Dispatchers.IO) { prefs.importBackupFromUri(uri) }
                res.onSuccess { entry ->
                    onBackupSaved(entry)
                    backupsList = prefs.listBackups()
                    statusMsg = "Импортирован: ${entry.label} (${formatSizeMb(entry.sizeBytes)})"
                }.onFailure {
                    actionErrMsg = "Импорт не удался: ${it.message}"
                }
                loading = false
            }
        }
    }

    fun showTopic(key: String) {
        helpTopic = MonitorHelp.topics[key]
    }

    fun showToggleHelp(key: String) {
        MonitorHelp.toggleHelps[key]?.let { helpTopic = it }
    }

    fun showQuickHelp(title: String, what: String, why: String, rec: String, caution: String = "") {
        helpTopic = MonitorHelpTopic(title, what, why, rec, caution)
    }

    fun refreshBackups() {
        backupsList = prefs.listBackups()
    }

    fun openDir(target: String) {
        scope.launch {
            loading = true
            pageErrMsg = ""
            try {
                val j = parseJsonOut(runSshCmd(prefs, MonitorScripts.listDirCmd(target), readOnly = true), "Файлы")
                dirData = j
                dirPath = j.optString("path", target)
            } catch (e: Throwable) {
                pageErrMsg = e.message ?: "Ошибка чтения каталога"
            } finally {
                loading = false
            }
        }
    }

    fun openFile(path: String) {
        scope.launch {
            loading = true
            actionErrMsg = ""
            try {
                val j = parseJsonOut(runSshCmd(prefs, MonitorScripts.readFileCmd(path), readOnly = true), "Файл")
                val er = j.optString("err")
                if (er.isNotBlank()) {
                    actionErrMsg = er
                } else {
                    fileModalPath = path
                    fileModalTrunc = j.optBoolean("trunc", false)
                    fileModalText = decodeB64(j.optString("b64"))
                    fileModalOpen = true
                }
            } catch (e: Throwable) {
                actionErrMsg = e.message ?: "Ошибка чтения файла"
            } finally {
                loading = false
            }
        }
    }

    fun estimateBackupSize() {
        scope.launch {
            loading = true
            actionErrMsg = ""
            try {
                val raw = runSshCmd(prefs, GboxScripts.ESTIMATE_BACKUP_SIZE_CMD, readOnly = true)
                val line = raw.lines().firstOrNull { it.startsWith("EST|") } ?: ""
                val p = line.split("|")
                backupEst = if (p.size >= 4) {
                    "Система (/opt + конфиг): ~${p[1]} МБ (до сжатия) • Носители: ${p[2]} МБ (${p[3]})"
                } else {
                    raw.take(180)
                }
            } catch (e: Throwable) {
                actionErrMsg = "Оценка размера: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun loadPage(target: MonitorPage, force: Boolean = false) {
        pageErrMsg = ""
        if (target == MonitorPage.BACKUPS) {
            refreshBackups()
            if (backupEst.isBlank() || force) estimateBackupSize()
            return
        }
        if (target == MonitorPage.FILES) {
            if (dirData == null || force) openDir(dirPath)
            return
        }
        val gen = ++loadGen
        scope.launch {
            loading = true
            try {
                when (target) {
                    MonitorPage.OVERVIEW -> if (overview == null || force) {
                        overview = parseJsonOut(runSshCmd(prefs, MonitorScripts.buildOverviewCmd(force), readOnly = true), "Обзор")
                    }
                    MonitorPage.SERVICES, MonitorPage.STORAGE -> if (services == null || force) {
                        services = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_SERVICES, readOnly = true), "Службы")
                    }
                    MonitorPage.WAN -> if (wanNet == null || force) {
                        wanNet = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_WAN_NET, readOnly = true), "WAN")
                    }
                    MonitorPage.DNS -> if (dnsNet == null || force) {
                        dnsNet = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_DNS_NET, readOnly = true), "AdGuard DNS")
                    }
                    MonitorPage.NAT_PPE -> {
                        if (dnsNet == null || force) dnsNet = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_DNS_NET, readOnly = true), "PPE/Порты")
                        if (wifiDevs == null || force) {
                            wifiDevs = runCatching {
                                parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_WIFI_DEVICES, readOnly = true), "Клиенты")
                            }.getOrNull() ?: wifiDevs
                        }
                    }
                    MonitorPage.Z2K_MODES, MonitorPage.Z2K_LISTS -> if (bypass == null || force) {
                        bypass = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_BYPASS_STATE, readOnly = true), "Zapret2")
                    }
                    MonitorPage.WARP -> if (warpFull == null || force) {
                        warpFull = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_WARP_FULL, readOnly = true), "WARP")
                    }
                    MonitorPage.WIFI, MonitorPage.DEVICES -> if (wifiDevs == null || force) {
                        wifiDevs = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_WIFI_DEVICES, readOnly = true), "Wi-Fi/Клиенты")
                    }
                    MonitorPage.TORCH -> if (torchData == null || force) {
                        torchData = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_TORCH_LIVE, readOnly = true), "Live Torch")
                    }
                    MonitorPage.LOGS_SSH -> if (logsOut.isBlank() || force) {
                        logsOut = runSshCmd(prefs, MonitorScripts.fetchLog(logSrc), readOnly = true)
                    }
                    MonitorPage.FILES, MonitorPage.BACKUPS -> {}
                }
            } catch (e: Throwable) {
                pageErrMsg = e.message ?: "Нет связи по SSH"
                Telemetry.recordError(
                    prefs,
                    "monitor_${target.name.lowercase()}",
                    pageErrMsg,
                    overview?.optJSONObject("sys")?.optString("model") ?: "",
                    overview?.optJSONObject("sys")?.optString("ndms") ?: "",
                    countAsFail = false
                )
            } finally {
                if (gen == loadGen) loading = false
            }
        }
    }

    fun runAction(
        label: String,
        cmd: String,
        reload: MonitorPage? = null,
        delayMs: Long = 350L,
        readOnly: Boolean = false,
        onOutput: ((String) -> Unit)? = null
    ) {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = "$label..."
            try {
                val res = runSshCmd(prefs, cmd, readOnly = readOnly)
                if (onOutput != null) onOutput(res)
                statusMsg = "$label: готово"
                if (delayMs > 0) delay(delayMs)
                reload?.let { loadPage(it, force = true) }
            } catch (e: Throwable) {
                actionErrMsg = "$label: ${e.message}"
                statusMsg = ""
                Telemetry.recordError(prefs, "monitor_action", actionErrMsg, countAsFail = false)
            } finally {
                loading = false
            }
        }
    }

    fun run24SiteProbe() {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = "Проверка 24 популярных сайтов..."
            try {
                val raw = runSshCmd(prefs, MonitorScripts.CMD_PROBE_SITES_JSON, readOnly = true)
                siteProbeJson = parseJsonOut(raw, "Тест 24 сайтов")
                statusMsg = "Тест 24 сайтов завершён"
            } catch (e: Throwable) {
                actionErrMsg = "Тест сайтов: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun measureBenchmark(saveAsBaseline: Boolean) {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = if (saveAsBaseline) "Замер точки «До»..." else "Контрольный замер «После»..."
            try {
                val snap = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_BENCH_SNAPSHOT, readOnly = true), "Замер")
                if (saveAsBaseline || benchBaseline == null) {
                    benchBaseline = snap
                    prefs.benchmarkBaselineJson = snap.toString()
                    benchAfter = null
                } else {
                    benchAfter = snap
                }
                statusMsg = "Замер завершён"
            } catch (e: Throwable) {
                actionErrMsg = "Замер: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun installOrBootstrapAdguard() {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = "Установка / настройка AdGuard Home v1.0.0..."
            try {
                val host = prefs.routerHost.ifBlank { "192.168.1.1" }
                val arch = passport?.entwareArch ?: "aarch64-k3.10"
                val pushRes = OfflineAssets.pushSingleAdguardOverSSH(
                    context = ctx,
                    host = host,
                    sshPort = prefs.sshPort,
                    sshUser = prefs.sshUser,
                    sshPass = prefs.sshPass,
                    entwareArch = arch
                ) { msg -> statusMsg = msg }
                if (pushRes.isFailure) {
                    statusMsg = "Офлайн бинарник пропущен — пробую opkg / конфиг..."
                }
                val bootOut = runSshCmd(prefs, MonitorScripts.adguardBootstrapInitCmd(), readOnly = false)
                dnsProbeOut = bootOut
                statusMsg = "AdGuard Home настроен"
                loadPage(MonitorPage.DNS, force = true)
            } catch (e: Throwable) {
                actionErrMsg = "AdGuard: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun createFullBackup() {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = if (includeMediaBackup) "Потоковый 100%-бэкап (включая USB/Storage)..." else "Потоковый бэкап v2 (/opt + конфиг)..."
            val tempFile = prefs.allocateStreamBackupTempFile(
                if (includeMediaBackup) "Полный_100pct" else "Полный_снимок_v2",
                "tar.gz"
            )
            try {
                val host = prefs.routerHost.ifBlank { "192.168.1.1" }
                val model = overview?.optJSONObject("sys")?.optString("model")?.ifBlank { passport?.model ?: "Router" }
                    ?: (passport?.model ?: "Router")
                val cmd = GboxScripts.buildFullBackupCmd(includeUserMedia = includeMediaBackup)
                val writtenBytes = withContext(Dispatchers.IO) {
                    Gboxcore.pullFileSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, cmd, tempFile.absolutePath)
                }
                if (writtenBytes > 4096L) {
                    val b = prefs.commitStreamedBackupFile(
                        file = tempFile,
                        label = if (includeMediaBackup) "Полный 100% + USB" else "Полный снимок v2",
                        model = model,
                        format = "tar_v2",
                        isProtected = false
                    )
                    onBackupSaved(b)
                    refreshBackups()
                    statusMsg = "Бэкап сохранён: ${formatSizeMb(b.sizeBytes)}"
                } else {
                    tempFile.delete()
                    actionErrMsg = "Архив бэкапа оказался пустым ($writtenBytes Б)"
                }
            } catch (e: Throwable) {
                tempFile.delete()
                actionErrMsg = "Ошибка бэкапа: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun createConfigBackup() {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = "Сохранение startup/running-config..."
            try {
                val host = prefs.routerHost.ifBlank { "192.168.1.1" }
                val model = overview?.optJSONObject("sys")?.optString("model")?.ifBlank { passport?.model ?: "Router" }
                    ?: (passport?.model ?: "Router")
                val cfgRes = RciClient.fetchRunningConfig(host, prefs.rciUser, prefs.rciPass)
                val text = cfgRes.getOrElse {
                    runSshCmd(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c 'show running-config'", readOnly = true)
                }
                if (text.isBlank()) error("Пустой ответ конфигурации")
                val b = prefs.saveBackup("NDMS_Config", model, text)
                onBackupSaved(b)
                refreshBackups()
                statusMsg = "Конфиг сохранён (${formatSizeMb(b.sizeBytes)})"
            } catch (e: Throwable) {
                actionErrMsg = "Ошибка сохранения конфига: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun executeBackupRestore(entry: BackupEntry, mode: String) {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = "Восстановление бэкапа (${entry.label})..."
            try {
                val host = prefs.routerHost.ifBlank { "192.168.1.1" }
                if (entry.filePath.endsWith(".tar.gz", true)) {
                    val bytes = withContext(Dispatchers.IO) { File(entry.filePath).readBytes() }
                    withContext(Dispatchers.IO) {
                        Gboxcore.pushBytesSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, "/tmp/gbox_restore.tar.gz", bytes)
                    }
                    val out = runSshCmd(prefs, GboxScripts.restoreArchiveCmd(mode), readOnly = false)
                    statusMsg = out.lines().lastOrNull { it.isNotBlank() } ?: "Откат завершён"
                } else {
                    val cfgText = withContext(Dispatchers.IO) { prefs.readBackupText(entry) }
                    if (cfgText.isBlank()) error("Файл конфигурации пуст")
                    val rciRes = RciClient.restoreRunningConfig(host, prefs.rciUser, prefs.rciPass, cfgText)
                    rciRes.onSuccess { (ok, fail) ->
                        statusMsg = "NDMS конфиг применён: $ok строк ОК, пропущено/ошибок $fail"
                    }.onFailure { throw it }
                }
                loadPage(MonitorPage.OVERVIEW, true)
            } catch (e: Throwable) {
                actionErrMsg = "Ошибка отката: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun shareBackupFile(entry: BackupEntry) {
        runCatching {
            val file = File(entry.filePath)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val mime = if (entry.filePath.endsWith(".tar.gz")) "application/gzip" else "text/plain"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Экспорт бэкапа"))
        }.onFailure { actionErrMsg = "Не удалось поделиться: ${it.message}" }
    }

    // Live Torch auto-poller when user stays on TORCH with Live ON
    LaunchedEffect(page, torchLive) {
        while (isActive && page == MonitorPage.TORCH && torchLive) {
            runCatching {
                val raw = runSshCmd(prefs, MonitorScripts.CMD_TORCH_LIVE, readOnly = true)
                torchData = parseJsonOut(raw, "Live Torch")
            }
            delay(2000L)
        }
    }

    LaunchedEffect(Unit) { loadPage(MonitorPage.OVERVIEW, false) }
    LaunchedEffect(statusMsg) { if (statusMsg.isNotBlank()) { delay(4500); statusMsg = "" } }

    val deadApWarn = remember(wifiDevs) {
        val aps = wifiDevs?.optJSONObject("wifi")?.optJSONArray("aps")
        aps != null && (0 until aps.length()).any { aps.optJSONObject(it)?.optBoolean("dead") == true }
    }

    val activeErr = actionErrMsg.ifBlank { pageErrMsg }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090E1A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                Surface(
                    color = if (sidebarOpen) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                    modifier = Modifier.clickable { sidebarOpen = !sidebarOpen }
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (sidebarOpen) Icons.Rounded.ChevronLeft else Icons.Rounded.Menu, null, tint = Color(0xFF93C5FD), modifier = Modifier.size(16.dp))
                        Text(if (sidebarOpen) "Скрыть" else "Меню", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(Modifier.padding(start = 2.dp)) {
                    Text("${page.group.title} › ${page.label}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (activeErr.isBlank()) Color(0xFF22C55E) else Color(0xFFF59E0B)))
                        Text("SSH ${prefs.routerHost.ifBlank { "192.168.1.1" }}:${prefs.sshPort}", color = Color(0xFF94A3B8), fontSize = 9.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showTopic(page.helpKey) }, modifier = Modifier.size(32.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF1D4ED8)), contentAlignment = Alignment.Center) {
                        Text("?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = { loadPage(page, true) }, enabled = !loading, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Refresh, "Обновить", tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showCreds = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Settings, "SSH", tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF3B82F6))
        if (statusMsg.isNotBlank()) {
            Text(statusMsg, color = Color(0xFF86EFAC), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF052E16)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (activeErr.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF451A03)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(activeErr, color = Color(0xFFFDE68A), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { actionErrMsg = ""; loadPage(page, true) }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                        Text("Повтор", fontSize = 10.sp, color = Color(0xFF93C5FD))
                    }
                    TextButton(onClick = { showCreds = true }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                        Text("SSH", fontSize = 10.sp, color = Color.White)
                    }
                    TextButton(onClick = { actionErrMsg = ""; pageErrMsg = "" }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                        Text("✕", fontSize = 10.sp, color = Color(0xFFFCA5A5))
                    }
                }
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedVisibility(
                visible = sidebarOpen,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
            ) {
                Row(Modifier.fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .width(146.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF0C1322))
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Разделы", color = Color(0xFF93C5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                color = if (!autoCollapse) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.clickable { autoCollapse = !autoCollapse }
                            ) {
                                Text(if (!autoCollapse) "Закреп." else "Автоскр.", color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        MonitorGroup.entries.forEach { grp ->
                            Text(grp.title, color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 1.dp))
                            MonitorPage.entries.filter { it.group == grp }.forEach { item ->
                                val sel = page == item
                                val warnDot = item == MonitorPage.WIFI && deadApWarn
                                Surface(
                                    color = if (sel) Color(0xFF172554) else Color(0xFF111827),
                                    shape = RoundedCornerShape(7.dp),
                                    border = BorderStroke(1.dp, if (sel) Color(0xFF3B82F6) else Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        page = item
                                        if (autoCollapse) sidebarOpen = false
                                        loadPage(item, false)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(item.icon, null, tint = if (sel) Color(0xFF60A5FA) else Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                        Text(item.label, color = if (sel) Color.White else Color(0xFFCBD5E1), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (warnDot) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF1E293B)))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (page) {
                    MonitorPage.OVERVIEW -> OverviewPage(
                        prefs = prefs,
                        passport = passport,
                        data = overview,
                        siteProbeJson = siteProbeJson,
                        benchBefore = benchBaseline,
                        benchAfter = benchAfter,
                        diagOut = diagOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onSvcClick = { svcModalKey = it },
                        onProbe24 = { run24SiteProbe() },
                        onAutoDiag = { runAction("Авторемонт", GboxScripts.AUTO_DIAG_REPAIR_SCRIPT, MonitorPage.OVERVIEW, onOutput = { diagOut = it }) },
                        onUpdateLists = { runAction("Обновление списков z2k", MonitorScripts.Z2K_UPDATE_CMD, MonitorPage.OVERVIEW, onOutput = { diagOut = it }) },
                        onBenchBefore = { measureBenchmark(saveAsBaseline = true) },
                        onBenchAfter = { measureBenchmark(saveAsBaseline = false) },
                        onApplyOptimize = { zram, ppeHw, ppeSw, cOpt, ct ->
                            runAction("Автооптимизация роутера", MonitorScripts.applyOptimizerBundleCmd(zram, ppeHw, ppeSw, cOpt, ct), MonitorPage.OVERVIEW, delayMs = 800L)
                        },
                        onReboot = { confirmReboot = true }
                    )
                    MonitorPage.SERVICES -> ServicesPage(
                        data = services,
                        opkgOut = opkgOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onHelpGuide = { guide ->
                            helpTopic = MonitorHelpTopic(
                                title = "${guide.title} (${guide.port})",
                                what = guide.summary,
                                why = guide.details,
                                recommend = guide.tips,
                                caution = if (guide.configPath.isNotBlank()) "Конфиг: ${guide.configPath}" else ""
                            )
                        },
                        onSvcAct = { id, act ->
                            if (id.contains("dropbear", true) && (act == "stop" || act == "restart")) {
                                confirmStopDropbear = id to act
                            } else {
                                runAction("$act $id", MonitorScripts.svcCmd(id, act), MonitorPage.SERVICES, 700L)
                            }
                        },
                        onCronAdd = { expr, cmd -> runAction("Добавить Cron", MonitorScripts.addCronJobCmd(expr, cmd), MonitorPage.SERVICES, 500L) },
                        onCronDel = { line -> runAction("Удалить Cron", MonitorScripts.delCronJobCmd(line), MonitorPage.SERVICES, 400L) },
                        onOpkg = { lbl, cmd, ro -> runAction(lbl, cmd, reload = if (ro) null else MonitorPage.SERVICES, readOnly = ro, onOutput = { opkgOut = it }) }
                    )
                    MonitorPage.STORAGE -> StoragePage(
                        data = services,
                        onTopicHelp = ::showTopic,
                        onToggleHelp = ::showToggleHelp,
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.STORAGE, 500L) }
                    )
                    MonitorPage.WAN -> WanPage(
                        data = wanNet,
                        benchOut = benchOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onBench = { runAction("Замер WAN", MonitorScripts.CMD_BENCH_WAN, readOnly = true, onOutput = { benchOut = it }) }
                    )
                    MonitorPage.DNS -> DnsPage(
                        data = dnsNet,
                        probeOut = dnsProbeOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onToggleHelp = ::showToggleHelp,
                        onInstallAdguard = { installOrBootstrapAdguard() },
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.DNS, 800L) },
                        onQuickBypassDomain = { dom -> runAction("+Обход $dom", MonitorScripts.addExtraDomain(dom)) },
                        onQuickWhitelistDomain = { dom -> runAction("+Исключение $dom", MonitorScripts.addWhitelistDomain(dom)) },
                        onProbe = { runAction("Тест DNS", MonitorScripts.CMD_PROBE_DNS, readOnly = true, onOutput = { dnsProbeOut = it }) }
                    )
                    MonitorPage.NAT_PPE -> NatPpePage(
                        data = dnsNet,
                        devicesData = wifiDevs,
                        onTopicHelp = ::showTopic,
                        onToggleHelp = ::showToggleHelp,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.NAT_PPE, 500L) }
                    )
                    MonitorPage.Z2K_MODES -> BypassModesPage(
                        data = bypass,
                        probeOut = siteProbeOut,
                        onTopicHelp = ::showTopic,
                        onToggleHelp = ::showToggleHelp,
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.Z2K_MODES, 600L) },
                        onProbe = { runAction("Проверка сайтов", GboxScripts.VERIFY_BYPASS_SCRIPT, readOnly = true, onOutput = { siteProbeOut = it }) }
                    )
                    MonitorPage.WARP -> WarpPage(
                        data = warpFull,
                        traceOut = warpTraceOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onTrace = { runAction("Проверка Cloudflare Trace", MonitorScripts.CMD_WARP_TRACE, readOnly = true, onOutput = { warpTraceOut = it }) },
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.WARP, 700L) }
                    )
                    MonitorPage.Z2K_LISTS -> BypassListsPage(
                        data = bypass,
                        domainInput = domainInput,
                        onDomainChange = { domainInput = it },
                        wlInput = wlInput,
                        onWlChange = { wlInput = it },
                        pickDomain = pickDomain,
                        onPickChange = { pickDomain = it },
                        pickOut = pickOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.Z2K_LISTS, 600L) },
                        onPick = { d, m -> runAction("Подбор стратегии ($d · $m)", MonitorScripts.strategyPickCmd(d, m), onOutput = { pickOut = it }) },
                        onApplyPool = { pool, dom, strat ->
                            runAction("Применить в пул $pool", MonitorScripts.strategyApplyPoolCmd(pool, dom, strat), MonitorPage.Z2K_LISTS, 600L) {
                                pickOut = if (pickOut.isBlank()) it else "$pickOut\n$it"
                            }
                        }
                    )
                    MonitorPage.WIFI -> WifiPage(
                        data = wifiDevs,
                        onTopicHelp = ::showTopic,
                        onToggleHelp = ::showToggleHelp,
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.WIFI, 800L) }
                    )
                    MonitorPage.DEVICES -> DevicesPage(
                        data = wifiDevs,
                        search = devSearch,
                        onSearchChange = { devSearch = it },
                        filterMode = devFilter,
                        onFilterChange = { devFilter = it },
                        onTopicHelp = ::showTopic,
                        onSelectDevice = { selectedDevice = it },
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.DEVICES, 700L) }
                    )
                    MonitorPage.BACKUPS -> BackupsPage(
                        backups = backupsList,
                        estText = backupEst,
                        includeMedia = includeMediaBackup,
                        onToggleMedia = { includeMediaBackup = it },
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onEstimate = { estimateBackupSize() },
                        onCreateFull = { createFullBackup() },
                        onCreateCfg = { createConfigBackup() },
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onShare = { shareBackupFile(it) },
                        onRestore = { restoreEntry = it },
                        onViewTxt = { e -> backupPreviewText = e.label to prefs.readBackupText(e) },
                        onDelete = { id -> prefs.deleteBackup(id); refreshBackups() }
                    )
                    MonitorPage.FILES -> FilesPage(
                        dirPath = dirPath,
                        data = dirData,
                        onTopicHelp = ::showTopic,
                        onOpenDir = ::openDir,
                        onOpenFile = ::openFile,
                        onRestoreBak = { full -> runAction("Откат .bak", MonitorScripts.restoreBakFileCmd(full.removeSuffix(".bak"))) { openDir(dirPath) } },
                        onNewDir = { newEntryName = ""; newEntryMode = "dir" },
                        onNewFile = { newEntryName = ""; newEntryMode = "file" },
                        onDeleteEntry = { full -> runAction("Удалить ${full.substringAfterLast('/')}", MonitorScripts.rmPathCmd(full)) { openDir(dirPath) } }
                    )
                    MonitorPage.TORCH -> TorchPage(
                        host = torchHost,
                        onHostChange = { torchHost = it },
                        liveData = torchData,
                        liveEnabled = torchLive,
                        onToggleLive = { torchLive = it },
                        out = torchOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onEnableAcct = { runAction("Счётчик conntrack_acct", MonitorScripts.ENABLE_CONNTRACK_ACCT_CMD, MonitorPage.TORCH, 300L) },
                        onCopy = { clipboard.setText(AnnotatedString(torchOut)); statusMsg = "Скопировано" },
                        onRun = { mode -> runAction("Torch: $mode", MonitorScripts.probeHostCmd(torchHost, mode), readOnly = true, onOutput = { torchOut = it }) }
                    )
                    MonitorPage.LOGS_SSH -> LogsSshPage(
                        logSrc = logSrc,
                        logsOut = logsOut,
                        logFilter = logFilter,
                        onLogFilterChange = { logFilter = it },
                        customCmd = customCmd,
                        onCustomCmdChange = { customCmd = it },
                        consoleOut = consoleOut,
                        onTopicHelp = ::showTopic,
                        onSelectLog = { src ->
                            logSrc = src
                            runAction("Лог $src", MonitorScripts.fetchLog(src), readOnly = true, onOutput = { logsOut = it })
                        },
                        onRunCmd = { cmd -> runAction("SSH", cmd, onOutput = { consoleOut = it }) },
                        onCopy = { txt -> clipboard.setText(AnnotatedString(txt)); statusMsg = "Скопировано" }
                    )
                }
            }
        }
    }

    // Help Modal
    helpTopic?.let { tp ->
        AlertDialog(
            onDismissRequest = { helpTopic = null },
            title = { Text(tp.title, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSection("Что это", tp.what, Color(0xFF93C5FD))
                    HelpSection("Зачем нужно", tp.why, Color(0xFF86EFAC))
                    HelpSection("Рекомендация", tp.recommend, Color(0xFFE2E8F0))
                    if (tp.caution.isNotBlank()) HelpSection("Важно", tp.caution, Color(0xFFFCD34D))
                }
            },
            confirmButton = { TextButton(onClick = { helpTopic = null }) { Text("Понятно") } }
        )
    }

    // Dropbear danger confirmation
    confirmStopDropbear?.let { (id, act) ->
        AlertDialog(
            onDismissRequest = { confirmStopDropbear = null },
            title = { Text("Осторожно: SSH Dropbear", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Остановка $id отключит доступ по SSH (:222) — приложение потеряет связь до ручного перезапуска через веб-интерфейс роутера. Рестарт обычно безопасен (1–2 сек). Продолжить?", fontSize = 12.5.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmStopDropbear = null
                        runAction("$act $id", MonitorScripts.svcCmd(id, act), MonitorPage.SERVICES, 700L)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Да, выполнить $act") }
            },
            dismissButton = { TextButton(onClick = { confirmStopDropbear = null }) { Text("Отмена") } }
        )
    }

    // Client Rich Management Dialog
    selectedDevice?.let { dev ->
        DeviceManageDialog(
            dev = dev,
            busy = loading,
            onDismiss = { selectedDevice = null },
            onExec = { lbl, cmd, closeDialog ->
                if (closeDialog) selectedDevice = null
                runAction(lbl, cmd, MonitorPage.DEVICES, 700L)
            },
            onPingDevice = { ip ->
                selectedDevice = null
                torchHost = ip
                page = MonitorPage.TORCH
                runAction("Ping $ip", MonitorScripts.probeHostCmd(ip, "ping"), readOnly = true, onOutput = { torchOut = it })
            }
        )
    }

    // Overview Service Pill Modal
    svcModalKey?.let { key ->
        val guide = MonitorHelp.services[key]
        val svcObj = overview?.optJSONObject("svc")
        val isUp = svcObj?.optBoolean(key, false) == true
        val warpInstalled = svcObj?.optBoolean("warp", false) == true || warpFull?.optJSONObject("status")?.optBoolean("installed") == true
        val initMap = mapOf(
            "nfqws2" to "S99zapret2", "adguard" to "S99AdGuardHome",
            "tg_tunnel" to "S98tg-tunnel", "http_tunnel" to "S97z2k-http-tunnel", "warp" to "S51z2k-warp",
            "z2k_panel" to "S96z2k-webpanel", "scheduler" to "S99z2k-scheduler"
        )
        AlertDialog(
            onDismissRequest = { svcModalKey = null },
            title = { Text(guide?.title ?: key, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniPill(if (isUp) "Активна" else "Остановлена", isUp)
                        if (!guide?.port.isNullOrBlank()) Text("Порт: ${guide?.port}", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (guide != null) {
                        HelpSection("Назначение", guide.summary, Color(0xFF93C5FD))
                        HelpSection("Подробно", guide.details, Color(0xFFE2E8F0))
                        HelpSection("Совет", guide.tips, Color(0xFFFCD34D))
                        if (guide.configPath.isNotBlank()) Text("Конфиг: ${guide.configPath}", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    HorizontalDivider(color = Color(0xFF1E293B))
                    when (key) {
                        "warp" -> {
                            if (!warpInstalled) {
                                Button(onClick = { svcModalKey = null; runAction("Установка WARP", MonitorScripts.warpInstallCmd(), MonitorPage.WARP) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Установить Cloudflare WARP", fontSize = 12.sp)
                                }
                            } else {
                                Button(onClick = { svcModalKey = null; runAction(if (isUp) "Стоп WARP" else "Старт WARP", MonitorScripts.toggleZ2k("game-warp", if (isUp) 0 else 1), MonitorPage.WARP) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (isUp) "Выключить WARP" else "Включить WARP", fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(onClick = { svcModalKey = null; page = MonitorPage.WARP; loadPage(MonitorPage.WARP, true) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Открыть раздел WARP & Игры", fontSize = 11.sp)
                            }
                        }
                        "tg_tunnel" -> {
                            Button(onClick = { svcModalKey = null; runAction(if (isUp) "Стоп TG туннеля" else "Старт TG туннеля", MonitorScripts.tunnelSetCmd(!isUp), MonitorPage.OVERVIEW) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (isUp) "Выключить TG :1443" else "Включить TG :1443", fontSize = 12.sp)
                            }
                        }
                        else -> {
                            val initId = initMap[key]
                            if (initId != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { svcModalKey = null; runAction("Рестарт $initId", MonitorScripts.svcCmd(initId, "restart"), MonitorPage.OVERVIEW, delayMs = 700L) }, modifier = Modifier.weight(1f)) { Text("Рестарт", fontSize = 11.sp) }
                                    OutlinedButton(onClick = { svcModalKey = null; runAction("${if (isUp) "Стоп" else "Старт"} $initId", MonitorScripts.svcCmd(initId, if (isUp) "stop" else "start"), MonitorPage.OVERVIEW, delayMs = 700L) }, modifier = Modifier.weight(1f)) { Text(if (isUp) "Стоп" else "Старт", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { svcModalKey = null }) { Text("Закрыть") } }
        )
    }

    // Restore Mode Dialog (supports tar_v1, tar_v2, AND .txt NDMS config)
    restoreEntry?.let { entry ->
        val isTar = entry.filePath.endsWith(".tar.gz", true)
        AlertDialog(
            onDismissRequest = { restoreEntry = null },
            title = { Text("Восстановление из бэкапа", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${entry.label} (${entry.timestamp})", color = Color(0xFF93C5FD), fontSize = 12.sp)
                    Text("Устройство: ${entry.model} • ${formatSizeMb(entry.sizeBytes)}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    if (isTar) {
                        Text("Выберите глубину отката (совместимо с v1 и v2):", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Button(
                            onClick = { restoreEntry = null; executeBackupRestore(entry, "bypass") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("1. Только обход (/opt/zapret2 + hooks)", fontSize = 11.5.sp) }
                        OutlinedButton(
                            onClick = { restoreEntry = null; executeBackupRestore(entry, "opt_full") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("2. Весь /opt (/opt/etc, AdGuard, SSH, Cron)", fontSize = 11.5.sp) }
                        OutlinedButton(
                            onClick = { restoreEntry = null; confirmSystemRestore = entry },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24))
                        ) { Text("3. 100% Полный откат (+ NDMS config + USB)", fontSize = 11.sp) }
                    } else {
                        Text("Это снимок конфигурации роутера (.txt). Применение прогонит команды через RCI (пропуская смену паролей/пользователей) и сохранит конфиг.", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Button(
                            onClick = { restoreEntry = null; executeBackupRestore(entry, "ndms_txt") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Применить NDMS конфиг по RCI", fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { restoreEntry = null }) { Text("Отмена") } }
        )
    }

    confirmSystemRestore?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmSystemRestore = null },
            title = { Text("Подтвердите 100% откат", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Будут перезаписаны /opt, пользовательские файлы носителя (если есть в архиве) и конфигурация NDMS (Wi-Fi, политики, порты). Во время применения Wi-Fi может кратковременно переподключиться.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmSystemRestore = null
                        executeBackupRestore(entry, "system_full")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) { Text("Откатить систему полностью") }
            },
            dismissButton = { TextButton(onClick = { confirmSystemRestore = null }) { Text("Отмена") } }
        )
    }

    backupPreviewText?.let { (lbl, txt) ->
        AlertDialog(
            onDismissRequest = { backupPreviewText = null },
            title = { Text(lbl, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(txt.take(14000), color = Color(0xFFCBD5E1), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                }
            },
            confirmButton = { TextButton(onClick = { backupPreviewText = null }) { Text("Закрыть") } }
        )
    }

    if (fileModalOpen) {
        AlertDialog(
            onDismissRequest = { fileModalOpen = false },
            title = { Text(fileModalPath.substringAfterLast('/'), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fileModalPath, color = Color(0xFF93C5FD), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    if (fileModalTrunc) Text("Файл > 32 КБ: показана только первая часть (сохранение отключено).", color = Color(0xFFFBBF24), fontSize = 11.sp)
                    OutlinedTextField(
                        value = fileModalText,
                        onValueChange = { if (!fileModalTrunc) fileModalText = it },
                        readOnly = fileModalTrunc,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 340.dp)
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        fileModalOpen = false
                        runAction("Откат .bak", MonitorScripts.restoreBakFileCmd(fileModalPath.removeSuffix(".bak")))
                    }) { Text("Откатить .bak", color = Color(0xFFFBBF24), fontSize = 11.sp) }
                    if (!fileModalTrunc) {
                        TextButton(onClick = {
                            val b64 = Base64.encodeToString(fileModalText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                            fileModalOpen = false
                            runAction("Сохранение файла", MonitorScripts.writeFileCmd(fileModalPath, b64))
                        }) { Text("Сохранить (.bak)") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { fileModalOpen = false }) { Text("Закрыть") } }
        )
    }

    newEntryMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { newEntryMode = null },
            title = { Text(if (mode == "dir") "Новая папка" else "Новый файл") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("В папке: $dirPath", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    OutlinedTextField(value = newEntryName, onValueChange = { newEntryName = it }, singleLine = true, label = { Text("Имя") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val nm = newEntryName.trim().replace("/", "")
                    newEntryMode = null
                    if (nm.isNotBlank()) {
                        val full = if (dirPath == "/") "/$nm" else "$dirPath/$nm"
                        val cmd = if (mode == "dir") MonitorScripts.mkdirCmd(full) else MonitorScripts.touchFileCmd(full)
                        runAction(if (mode == "dir") "Создать папку" else "Создать файл", cmd) { openDir(dirPath) }
                    }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { newEntryMode = null }) { Text("Отмена") } }
        )
    }

    if (showCreds) {
        AlertDialog(
            onDismissRequest = { showCreds = false },
            title = { Text("SSH подключение к роутеру") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sshHost, onValueChange = { sshHost = it }, label = { Text("LAN Host / IP") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sshUser, onValueChange = { sshUser = it }, label = { Text("Логин") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text("Порт") }, singleLine = true, modifier = Modifier.width(88.dp))
                    }
                    OutlinedTextField(
                        value = sshPass,
                        onValueChange = { sshPass = it },
                        label = { Text("Пароль SSH (Dropbear)") },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                            }
                        }
                    )
                    OutlinedButton(
                        enabled = !bruteBusy,
                        onClick = {
                            scope.launch {
                                bruteBusy = true
                                val p = sshPort.toIntOrNull() ?: 222
                                val found = withContext(Dispatchers.IO) {
                                    runCatching { Gboxcore.tryPasswordsSSH(sshHost.ifBlank { "192.168.1.1" }, p, sshUser, prefs.rciPass) }.getOrNull()
                                }
                                if (found != null) {
                                    sshPass = found
                                    statusMsg = "Пароль SSH подобран!"
                                } else {
                                    actionErrMsg = "Стандартный пароль SSH не подошёл — введите вручную"
                                }
                                bruteBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (bruteBusy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Tune, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (bruteBusy) "Подбор..." else "Автоподбор пароля SSH", fontSize = 11.5.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.setSshHost(ctx, sshHost)
                    Prefs.setSshUser(ctx, sshUser)
                    Prefs.setSshPort(ctx, sshPort.toIntOrNull() ?: 222)
                    Prefs.setSshPass(ctx, sshPass)
                    showCreds = false
                    loadPage(page, true)
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showCreds = false }) { Text("Отмена") } }
        )
    }

    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            title = { Text("Перезагрузить роутер?") },
            text = { Text("Интернет и Wi-Fi пропадут примерно на 1–2 минуты.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReboot = false
                    runAction("Перезагрузка роутера", "LD_LIBRARY_PATH= /bin/ndmc -c 'system reboot'")
                }) { Text("Перезагрузить", color = Color(0xFFFCA5A5)) }
            },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun HelpSection(label: String, text: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color(0xFFE2E8F0), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun HelpBadge(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(17.dp).clip(CircleShape).background(Color(0xFF1E293B)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text("?", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun MiniCard(title: String, helpClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color(0xFF93C5FD), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (helpClick != null) HelpBadge(helpClick)
            }
            content()
        }
    }
}

@Composable
private fun MiniRow(label: String, value: String, color: Color = Color.White, helpClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (helpClick != null) HelpBadge(helpClick)
        }
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MiniBar(label: String, detail: String, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val c = when { p > 0.85f -> Color(0xFFEF4444); p > 0.65f -> Color(0xFFF59E0B); else -> Color(0xFF3B82F6) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp)
            Text(detail, color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)), color = c, trackColor = Color(0xFF1E293B))
    }
}

@Composable
private fun MiniSwitch(title: String, sub: String, checked: Boolean, helpClick: (() -> Unit)? = null, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                if (helpClick != null) HelpBadge(helpClick)
            }
            if (sub.isNotBlank()) Text(sub, color = Color(0xFF64748B), fontSize = 9.5.sp)
        }
        Switch(checked = checked, onCheckedChange = { onToggle(if (it) 1 else 0) }, modifier = Modifier.scale(0.78f))
    }
}

@Composable
private fun MiniPill(label: String, ok: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        color = if (ok) Color(0xFF052E16) else Color(0xFF1E293B),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (ok) Color(0xFF16A34A) else Color(0xFF334155)),
        modifier = m
    ) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (ok) Color(0xFF22C55E) else Color(0xFF64748B)))
            Text(label, color = if (ok) Color(0xFF86EFAC) else Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MonoBox(text: String, onCopy: (() -> Unit)? = null) {
    Surface(color = Color(0xFF020617), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            if (onCopy != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Копировать", color = Color(0xFF60A5FA), fontSize = 9.sp, modifier = Modifier.clickable(onClick = onCopy).padding(bottom = 3.dp))
                }
            }
            Text(text, color = Color(0xFFCBD5E1), fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.5.sp)
        }
    }
}

@Composable
private fun OverviewPage(
    prefs: Prefs,
    passport: RouterPassport?,
    data: JSONObject?,
    siteProbeJson: JSONObject?,
    benchBefore: JSONObject?,
    benchAfter: JSONObject?,
    diagOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onSvcClick: (String) -> Unit,
    onProbe24: () -> Unit,
    onAutoDiag: () -> Unit,
    onUpdateLists: () -> Unit,
    onBenchBefore: () -> Unit,
    onBenchAfter: () -> Unit,
    onApplyOptimize: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onReboot: () -> Unit
) {
    val sys = data?.optJSONObject("sys")
    val mem = sys?.optJSONObject("mem")
    val disk = sys?.optJSONObject("disk")
    val wan = data?.optJSONObject("wan")
    val svc = data?.optJSONObject("svc")
    val ver = data?.optJSONObject("ver")

    // Health traffic light
    if (data != null) {
        val entOk = wan?.optBoolean("ent_ok", false) == true
        val nfqUp = svc?.optBoolean("nfqws2", false) == true
        val nfqRules = wan?.optInt("nfq_rules", 0) ?: 0
        val aghInst = wan?.optBoolean("agh_inst", false) == true
        val aghUp = svc?.optBoolean("adguard", false) == true
        val dnsOv = wan?.optBoolean("dns_ov", false) == true
        val polMode = wan?.optString("policy_mode", "policy") ?: "policy"
        val polName = wan?.optString("policy_name", "Gor4akovNET").orEmpty().ifBlank { "Gor4akovNET" }
        val polOk = wan?.optBoolean("policy_ok", false) == true || polMode == "global"

        val warns = mutableListOf<String>()
        if (!entOk) warns.add("Entware не смонтирован")
        if (!nfqUp) warns.add("nfqws2 остановлен")
        if (nfqRules == 0) warns.add("нет правил NFQUEUE")
        if (dnsOv && !aghUp) warns.add("dns-override при спящем AdGuard!")
        if (polMode != "global" && !polOk) warns.add("политика $polName выкл")
        if (aghInst && !aghUp && !dnsOv) warns.add("AdGuard остановлен")

        val isGreen = warns.isEmpty()
        val isRed = !entOk || !nfqUp || (dnsOv && !aghUp)
        val bannerColor = when {
            isRed -> Color(0xFF450A0A)
            !isGreen -> Color(0xFF451A03)
            else -> Color(0xFF052E16)
        }
        val borderColor = when {
            isRed -> Color(0xFFEF4444)
            !isGreen -> Color(0xFFF59E0B)
            else -> Color(0xFF22C55E)
        }
        Surface(color = bannerColor, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, borderColor.copy(0.65f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            isRed -> "Критично: ${warns.joinToString(", ")}"
                            !isGreen -> "Внимание: ${warns.joinToString(", ")}"
                            else -> "Светофор: все системы работают штатно"
                        },
                        color = borderColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("NFQUEUE правил: $nfqRules • Entware: ${if (entOk) "OK" else "Нет"} • Политика: ${if (polMode == "global") "Общая (LAN)" else if (polOk) "ON ($polName)" else "OFF"}", color = Color(0xFFCBD5E1), fontSize = 9.5.sp)
                }
                if (!isGreen) {
                    Button(onClick = onAutoDiag, enabled = !busy, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Починить", fontSize = 10.sp)
                    }
                }
            }
        }
    }

    MiniCard("${sys?.optString("model", "Keenetic / Netcraze")} • NDMS ${sys?.optString("ndms", "—")}", { onTopicHelp("overview") }) {
        val ramT = mem?.optInt("total", 0) ?: 0
        val ramU = mem?.optInt("used", 0) ?: 0
        val swT = mem?.optInt("swap_t", 0) ?: 0
        val swU = mem?.optInt("swap_u", 0) ?: 0
        val ct = sys?.optInt("ct", 0) ?: 0
        val ctMax = sys?.optInt("ct_max", 0) ?: 0
        val diskPct = disk?.optInt("pct", 0) ?: 0
        val loadFirst = sys?.optString("load", "0")?.substringBefore(' ')?.toFloatOrNull() ?: 0f
        val cpus = (sys?.optInt("cpus", 2) ?: 2).coerceAtLeast(1)

        MiniBar("CPU Load (${sys?.optString("load", "—")})", "${(loadFirst / cpus * 100f).toInt().coerceIn(0, 100)}%", loadFirst / cpus)
        MiniBar("RAM", "$ramU / $ramT МБ", if (ramT > 0) ramU.toFloat() / ramT else 0f)
        if (swT > 0) MiniBar("ZRAM / Swap", "$swU / $swT МБ", swU.toFloat() / swT)
        MiniBar("NAT Conntrack", "$ct / $ctMax", if (ctMax > 0) ct.toFloat() / ctMax else 0f)
        MiniBar("Память /opt", "Свободно ${disk?.optInt("avail", 0) ?: 0} из ${disk?.optInt("total", 0) ?: 0} МБ", diskPct / 100f)

        MiniRow("Аптайм • Темп. Wi-Fi", "${formatUptime(sys?.optLong("up_sec", 0L) ?: 0L)} • ${sys?.optString("temp24", "—")}°/${sys?.optString("temp5", "—")}°C")
        val wanDev = wan?.optString("dev").orEmpty().ifBlank { "WAN" }
        MiniRow("WAN ($wanDev) • Скорость", "${wan?.optString("ip", "—")} • ${wan?.optString("spd", "—")} Мбит/с")
    }

    MiniCard("Службы роутера (нажми для управления)", { onTopicHelp("services") }) {
        val list = listOf(
            "nfqws2" to "Zapret2 nfqws2", "adguard" to "AdGuard Home",
            "gvsub" to "Autocircular", "warp" to "Cloudflare WARP",
            "tg_tunnel" to "TG :1443", "http_tunnel" to "HTTP :1444",
            "z2k_panel" to "z2k Веб :8088", "scheduler" to "Планировщик"
        )
        list.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { (k, title) ->
                    MiniPill(title, svc?.optBoolean(k) == true, Modifier.weight(1f)) { onSvcClick(k) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("z2k: ${ver?.optString("z2k_local", "—")}", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("AGH: ${ver?.optString("agh", "—")}", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }

    // 24-Site Probe Card
    MiniCard("Тест 24 популярных сайтов (с роутера)") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onProbe24, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) {
                Text("Проверить 24 сайта", fontSize = 10.5.sp)
            }
            OutlinedButton(onClick = onAutoDiag, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) {
                Text("Авторемонт", fontSize = 10.5.sp)
            }
        }
        val sitesArr = siteProbeJson?.optJSONArray("sites")
        if (sitesArr != null && sitesArr.length() > 0) {
            val items = (0 until sitesArr.length()).mapNotNull { sitesArr.optJSONObject(it) }
            val okCnt = items.count { it.optBoolean("ok") }
            Text("Доступно: $okCnt из ${items.size}", color = if (okCnt >= items.size - 2) Color(0xFF4ADE80) else Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val cats = items.groupBy { it.optString("cat") }
            cats.forEach { (cat, groupItems) ->
                Text(cat, color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                groupItems.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        rowItems.forEach { s ->
                            val ok = s.optBoolean("ok")
                            val code = s.optInt("code", 0)
                            val ms = s.optInt("tot_ms", 0)
                            Surface(
                                color = if (ok) Color(0xFF052E16) else Color(0xFF450A0A),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (ok) Color(0xFF16A34A) else Color(0xFFDC2626)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.optString("host"), color = Color.White, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(if (ok) "${ms}мс" else "[$code]", color = if (ok) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Optimizer Do / Posle
    var optZram by remember { mutableStateOf(true) }
    var optPpeHw by remember { mutableStateOf(true) }
    var optPpeSw by remember { mutableStateOf(true) }
    var optCache by remember { mutableStateOf(true) }
    var optCt by remember { mutableStateOf(true) }
    MiniCard("Тест и автооптимизация (До / После)") {
        Text("Замерьте скорость, включите оптимизации ядра/AdGuard и сравните реальный прирост.", color = Color(0xFF94A3B8), fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = onBenchBefore, enabled = !busy, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(3.dp)) { Text("1. Замер «До»", fontSize = 9.5.sp) }
            Button(onClick = { onApplyOptimize(optZram, optPpeHw, optPpeSw, optCache, optCt) }, enabled = !busy, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(3.dp)) { Text("2. Ускорить", fontSize = 9.5.sp) }
            OutlinedButton(onClick = onBenchAfter, enabled = !busy && benchBefore != null, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(3.dp)) { Text("3. «После»", fontSize = 9.5.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = optZram, onClick = { optZram = !optZram }, label = { Text("ZRAM", fontSize = 9.sp) })
            FilterChip(selected = optPpeHw, onClick = { optPpeHw = !optPpeHw }, label = { Text("PPE HW", fontSize = 9.sp) })
            FilterChip(selected = optPpeSw, onClick = { optPpeSw = !optPpeSw }, label = { Text("PPE SW", fontSize = 9.sp) })
            FilterChip(selected = optCache, onClick = { optCache = !optCache }, label = { Text("Опт.Кэш DNS", fontSize = 9.sp) })
            FilterChip(selected = optCt, onClick = { optCt = !optCt }, label = { Text("CT 65K", fontSize = 9.sp) })
        }
        if (benchBefore != null) {
            val b = benchBefore
            val a = benchAfter
            Surface(color = Color(0xFF020617), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (a != null) "Сравнение «До -> После»:" else "Базовый замер «До» (примените ускорение и нажмите «После»):", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val pB = b.optDouble("ping_ms", 0.0); val pA = a?.optDouble("ping_ms", 0.0)
                    MiniRow("Пинг 1.1.1.1", if (pA != null) "${"%.1f".format(pB)} -> ${"%.1f".format(pA)} мс" else "${"%.1f".format(pB)} мс")
                    val dB = b.optInt("dns_ms", 0); val dA = a?.optInt("dns_ms", 0)
                    MiniRow("DNS резолв", if (dA != null) "$dB -> $dA мс" else "$dB мс")
                    val yB = b.optInt("yt_ms", 0); val yA = a?.optInt("yt_ms", 0)
                    MiniRow("YouTube TLS", if (yA != null) "$yB -> $yA мс" else "$yB мс")
                    val dcB = b.optInt("dc_ms", 0); val dcA = a?.optInt("dc_ms", 0)
                    MiniRow("Discord TLS", if (dcA != null) "$dcB -> $dcA мс" else "$dcB мс")
                    val rB = b.optInt("ram_free_mb", 0); val rA = a?.optInt("ram_free_mb", 0)
                    MiniRow("Свободно RAM", if (rA != null) "$rB -> $rA МБ" else "$rB МБ")
                }
            }
        }
    }

    // Actions & QR
    MiniCard("Системные действия") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onUpdateLists, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) { Text("Обновить списки", fontSize = 10.5.sp) }
            OutlinedButton(onClick = onReboot, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))) {
                Text("Ребут роутера", fontSize = 10.5.sp)
            }
        }
        if (diagOut.isNotBlank()) MonoBox(diagOut)
    }

    val activeSsid = passport?.gor4akovSsid?.ifBlank { null } ?: prefs.wifiSsid.takeIf { wan?.optBoolean("policy_ok") == true }
    if (!activeSsid.isNullOrBlank()) {
        WiFiQrCard(ssid = activeSsid, pass = prefs.wifiPass, subnetInfo = "Gor4akovNET")
    }

    DonateCard(compact = true)
}

@Composable
private fun ServicesPage(
    data: JSONObject?,
    opkgOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onHelpGuide: (ServiceGuide) -> Unit,
    onSvcAct: (String, String) -> Unit,
    onCronAdd: (String, String) -> Unit,
    onCronDel: (String) -> Unit,
    onOpkg: (String, String, Boolean) -> Unit
) {
    MiniCard("Службы Entware — CPU, RAM, PID и порты", { onTopicHelp("services") }) {
        val svcs = data?.optJSONArray("svcs") ?: JSONArray()
        if (svcs.length() == 0) {
            Text("Скрипты в /opt/etc/init.d/S* не найдены", color = Color(0xFF64748B), fontSize = 11.sp)
        }
        for (i in 0 until svcs.length()) {
            val s = svcs.optJSONObject(i) ?: continue
            val id = s.optString("id")
            val inst = s.optBoolean("inst")
            val up = s.optBoolean("up")
            val ramMb = s.optDouble("ram_mb", 0.0)
            val ramPct = s.optDouble("ram_pct", 0.0)
            val cpu = s.optDouble("cpu", 0.0)
            val pids = s.optString("pids")
            val ports = s.optString("ports")
            val guide = MonitorHelp.guideForInitId(id)

            Surface(
                color = Color(0xFF111827),
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, if (up) Color(0xFF14532D) else Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(when { up -> Color(0xFF22C55E); inst -> Color(0xFFF59E0B); else -> Color(0xFF475569) }))
                            Text(id, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (guide != null) {
                                HelpBadge { onHelpGuide(guide) }
                            }
                        }
                        if (inst) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { onSvcAct(id, "restart") }, enabled = !busy, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Рест.", fontSize = 9.sp) }
                                OutlinedButton(onClick = { onSvcAct(id, if (up) "stop" else "start") }, enabled = !busy, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (up) "Стоп" else "Старт", fontSize = 9.sp) }
                            }
                        } else {
                            Text("не исполн.", color = Color(0xFF64748B), fontSize = 9.5.sp)
                        }
                    }
                    if (up || guide != null) {
                        val metrics = if (up) {
                            "RAM: ${"%.1f".format(ramMb)} МБ (${"%.1f".format(ramPct)}%) • CPU: ${"%.1f".format(cpu)}% • PID: ${pids.ifBlank { "-" }}${if (ports.isNotBlank()) " • Порт: $ports" else ""}"
                        } else {
                            guide?.summary ?: "Остановлена"
                        }
                        Text(metrics, color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontFamily = if (up) FontFamily.Monospace else FontFamily.Default)
                    }
                }
            }
        }
    }

    val topProcs = data?.optJSONArray("top_procs") ?: JSONArray()
    if (topProcs.length() > 0) {
        MiniCard("Топ-10 процессов по памяти (всего: ${data?.optInt("mem_free_mb", 0) ?: 0} / ${data?.optInt("mem_tot_mb", 0) ?: 0} МБ свободно)") {
            for (i in 0 until topProcs.length()) {
                val p = topProcs.optJSONObject(i) ?: continue
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.optString("pid")} ${p.optString("cmd")}", color = Color(0xFFCBD5E1), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${"%.1f".format(p.optDouble("ram_mb", 0.0))} МБ", color = Color(0xFF93C5FD), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    // Cron Manager
    val cronArr = data?.optJSONArray("cron") ?: JSONArray()
    var cronExpr by remember { mutableStateOf("0 4 * * *") }
    var cronCmd by remember { mutableStateOf("") }
    MiniCard("Планировщик Cron (${cronArr.length()})") {
        for (i in 0 until cronArr.length()) {
            val line = cronArr.optString(i)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(line, color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { onCronDel(line) }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = cronExpr, onValueChange = { cronExpr = it }, label = { Text("Расписание", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.width(108.dp))
            OutlinedTextField(value = cronCmd, onValueChange = { cronCmd = it }, label = { Text("Команда sh", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            Button(onClick = {
                if (cronExpr.isNotBlank() && cronCmd.isNotBlank()) {
                    onCronAdd(cronExpr, cronCmd)
                    cronCmd = ""
                }
            }, enabled = !busy, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+", fontSize = 14.sp) }
        }
    }

    // OPKG Manager + quick chips
    var opkgQuery by remember { mutableStateOf("") }
    MiniCard("Менеджер пакетов opkg (установлено: ${data?.optInt("opkg_cnt", 0) ?: 0})") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { onOpkg("Проверка opkg", MonitorScripts.CMD_OPKG_CHECK, false) }, enabled = !busy, modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(4.dp)) { Text("Проверить обновления", fontSize = 10.sp) }
            OutlinedButton(onClick = { onOpkg("opkg upgrade", MonitorScripts.CMD_OPKG_UPGRADE_ALL, false) }, enabled = !busy, modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(4.dp)) { Text("Обновить всё", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("tcpdump", "htop", "nano", "mtr").forEach { pkg ->
                AssistChip(onClick = { opkgQuery = pkg }, label = { Text("+ $pkg", fontSize = 9.5.sp) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = opkgQuery, onValueChange = { opkgQuery = it }, placeholder = { Text("Пакет...", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { if (opkgQuery.isNotBlank()) onOpkg("Поиск $opkgQuery", MonitorScripts.opkgSearchCmd(opkgQuery), true) }, enabled = !busy, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Найти", fontSize = 9.5.sp) }
            Button(onClick = { if (opkgQuery.isNotBlank()) onOpkg("Уст. $opkgQuery", MonitorScripts.opkgInstallCmd(opkgQuery), false) }, enabled = !busy, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Уст.", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { if (opkgQuery.isNotBlank()) onOpkg("Удал. $opkgQuery", MonitorScripts.opkgRemoveCmd(opkgQuery), false) }, enabled = !busy, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 7.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))) { Text("Удал.", fontSize = 9.5.sp) }
        }
        if (opkgOut.isNotBlank()) MonoBox(opkgOut)
    }
}

@Composable
private fun StoragePage(
    data: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onAction: (String, String) -> Unit
) {
    val disks = data?.optJSONArray("disks") ?: JSONArray()
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    MiniCard("Накопители USB и /opt", { onTopicHelp("storage") }) {
        if (disks.length() == 0) Text("Примонтированные разделы не найдены", color = Color(0xFF64748B), fontSize = 11.sp)
        for (i in 0 until disks.length()) {
            val d = disks.optJSONObject(i) ?: continue
            MiniBar(d.optString("mnt"), "${d.optInt("used")} / ${d.optInt("tot")} МБ (${d.optInt("pct")}%)", d.optInt("pct") / 100f)
        }
    }
    MiniCard("Системные функции NDMS", { onTopicHelp("storage") }) {
        MiniSwitch("Windows-сеть (SMB / CIFS)", "Общие папки флешки в локальной сети", ndms.optBoolean("cifs"), { onToggleHelp("cifs") }) { v ->
            onAction("CIFS", MonitorScripts.ndmcSaveCmd(if (v == 1) "service cifs" else "no service cifs"))
        }
        MiniSwitch("WebDAV-сервер", "Доступ по HTTP к файлам накопителя и заливка Entware", ndms.optBoolean("webdav"), { onToggleHelp("webdav") }) { v ->
            onAction("WebDAV", MonitorScripts.ndmcSaveCmd(if (v == 1) "ip http webdav" else "no ip http webdav", if (v == 1) "ip http webdav enable" else "no ip http webdav enable"))
        }
        MiniSwitch("ZRAM (сжатие RAM)", "Увеличивает доступную память без износа флешки", ndms.optBoolean("zram"), { onToggleHelp("zram") }) { v ->
            onAction("ZRAM", MonitorScripts.ndmcSaveCmd(if (v == 1) "system zram" else "system no zram"))
        }
        MiniSwitch("Режим USB 2.0", "Повышает стабильность питания и убирает помехи 2.4 ГГц", ndms.optBoolean("usb2"), { onToggleHelp("usb2") }) { v ->
            onAction("USB 2.0", MonitorScripts.ndmcSaveCmd(if (v == 1) "system set dev.usb.force_usb2 1" else "system set dev.usb.force_usb2 0"))
        }
    }
}

@Composable
private fun WanPage(data: JSONObject?, benchOut: String, busy: Boolean, onTopicHelp: (String) -> Unit, onBench: () -> Unit) {
    MiniCard("Интернет и маршруты (WAN)", { onTopicHelp("wan") }) {
        val dev = data?.optString("dev").orEmpty().ifBlank { "—" }
        MiniRow("Активный WAN", "$dev • ${data?.optString("wan", "—")}")
        MiniRow("Шлюз провайдера", data?.optString("gw", "—") ?: "—")
        MiniRow("Скорость порта", "${data?.optString("spd", "—")} Мбит/с")
        MiniRow("DNS системы", data?.optString("dns", "—") ?: "—")
        MiniRow("Трафик WAN", "↓ ${formatBytes(data?.optDouble("rx", 0.0) ?: 0.0)}  ↑ ${formatBytes(data?.optDouble("tx", 0.0) ?: 0.0)}")
        val ndmWan = data?.optString("ndm_wan").orEmpty()
        if (ndmWan.isNotBlank()) MonoBox(ndmWan)
        val pol = data?.optString("pol_info").orEmpty()
        if (pol.isNotBlank()) {
            Text("Политика маршрутизации:", color = Color(0xFF93C5FD), fontSize = 10.sp)
            MonoBox(pol)
        }
        Button(onClick = onBench, enabled = !busy, modifier = Modifier.fillMaxWidth().height(34.dp)) {
            Text("Замерить скорость и пинг с роутера", fontSize = 11.sp)
        }
        if (benchOut.isNotBlank()) MonoBox(benchOut)
    }
}

@Composable
private fun DnsPage(
    data: JSONObject?,
    probeOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onInstallAdguard: () -> Unit,
    onAction: (String, String) -> Unit,
    onQuickBypassDomain: (String) -> Unit,
    onQuickWhitelistDomain: (String) -> Unit,
    onProbe: () -> Unit
) {
    val agh = data?.optJSONObject("agh") ?: JSONObject()
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    val upstreams = agh.optJSONArray("upstreams") ?: JSONArray()
    val filters = agh.optJSONArray("filters") ?: JSONArray()
    val errs = data?.optJSONArray("errs") ?: JSONArray()
    val qlog = data?.optJSONArray("qlog") ?: JSONArray()
    val port = data?.optInt("agh_port", 3000) ?: 3000
    val aghUp = data?.optBoolean("agh_up") == true

    MiniCard("AdGuard Home v1.0.0 (:$port) — Запросов: ${data?.optInt("qcnt", 0) ?: 0}", { onTopicHelp("dns") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MiniPill(if (aghUp) "AdGuard Работает" else "AdGuard Остановлен", aghUp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (!aghUp || upstreams.length() == 0) {
                    Button(onClick = onInstallAdguard, enabled = !busy, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Развернуть AGH", fontSize = 10.sp) }
                }
                OutlinedButton(onClick = onProbe, enabled = !busy, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Тест DNS", fontSize = 10.sp) }
            }
        }
        MiniSwitch("Защита (protection_enabled)", "Общий рубильник фильтрации рекламы", agh.optBoolean("prot"), { onToggleHelp("agh_prot") }) { v ->
            onAction("AdGuard защита", MonitorScripts.setAghKeyCmd("protection_enabled", v == 1))
        }
        MiniSwitch("Фильтры доменов (filtering)", "Блокировка рекламы по чёрным спискам", agh.optBoolean("filt"), { onToggleHelp("agh_filt") }) { v ->
            onAction("Фильтры AdGuard", MonitorScripts.setAghKeyCmd("filtering_enabled", v == 1))
        }
        MiniSwitch("Перехват DNS роутером (opkg dns-override)", "Направляет весь DNS сети на AdGuard :53", ndms.optBoolean("dns_ov"), { onToggleHelp("dns_ov") }) { v ->
            onAction("dns-override", MonitorScripts.ndmcSaveCmd(if (v == 1) "opkg dns-override" else "no opkg dns-override"))
        }
        MiniSwitch("DNSSEC", "Криптографическая проверка подлинности ответов DNS", agh.optBoolean("dnssec"), { onToggleHelp("dnssec") }) { v ->
            onAction("DNSSEC", MonitorScripts.setAghKeyCmd("enable_dnssec", v == 1))
        }
        MiniSwitch("Оптимистичный кэш", "Мгновенная отдача кэша с фоновым обновлением", agh.optBoolean("cache_opt"), { onToggleHelp("cache_opt") }) { v ->
            onAction("Опт. кэш", MonitorScripts.setAghKeyCmd("cache_optimistic", v == 1))
        }
        MiniSwitch("Блокировать IPv6 (AAAA)", "Исключает утечку трафика мимо IPv4 правил", agh.optBoolean("no_aaaa"), { onToggleHelp("no_aaaa") }) { v ->
            onAction("AAAA block", MonitorScripts.setAghKeyCmd("aaaa_disabled", v == 1))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { onAction("Профиль DoH Стабильный", MonitorScripts.setAghDnsProfile("doh_stable")) }, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("DoH Стабильный", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { onAction("Профиль Fast UDP", MonitorScripts.setAghDnsProfile("fast_udp")) }, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Быстрый UDP", fontSize = 9.5.sp) }
        }
        if (probeOut.isNotBlank()) MonoBox(probeOut)
    }

    MiniCard("Апстримы и фильтры (${filters.length()})") {
        if (upstreams.length() > 0) {
            MonoBox((0 until upstreams.length()).joinToString("\n") { upstreams.optString(it) })
        } else {
            Text("AdGuardHome.yaml не найден — нажмите «Развернуть AGH» выше.", color = Color(0xFF64748B), fontSize = 10.sp)
        }
        var customUps by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = customUps, onValueChange = { customUps = it }, placeholder = { Text("https://dns.quad9.net/dns-query ...", fontSize = 10.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f), maxLines = 2)
            Button(onClick = { if (customUps.isNotBlank()) { onAction("Обновить DNS", MonitorScripts.setAghCustomUpstreamsCmd(customUps)); customUps = "" } }, modifier = Modifier.height(34.dp)) { Text("Задать", fontSize = 10.sp) }
        }
        for (i in 0 until filters.length()) {
            val f = filters.optJSONObject(i) ?: continue
            val fid = f.optInt("id")
            MiniSwitch(f.optString("name", "Фильтр #$fid"), "ID: $fid", f.optBoolean("on")) { v ->
                onAction("Фильтр $fid", MonitorScripts.setAghFilterCmd(fid, v == 1))
            }
        }
    }

    if (qlog.length() > 0) {
        MiniCard("Живой журнал DNS-запросов (нажми +z2k / +Искл.)") {
            for (i in 0 until qlog.length()) {
                val q = qlog.optJSONObject(i) ?: continue
                val blk = q.optBoolean("blk")
                val dom = q.optString("dom")
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${q.optString("t")} • $dom", color = if (blk) Color(0xFFFCA5A5) else Color(0xFFCBD5E1), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${q.optString("ip")} • ${if (blk) "BLOCK" else q.optString("qt")}", color = if (blk) Color(0xFFF87171) else Color(0xFF64748B), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(color = Color(0xFF172554), shape = RoundedCornerShape(4.dp), modifier = Modifier.clickable { onQuickBypassDomain(dom) }) {
                            Text("+z2k", color = Color(0xFF93C5FD), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                        Surface(color = Color(0xFF064E3B), shape = RoundedCornerShape(4.dp), modifier = Modifier.clickable { onQuickWhitelistDomain(dom) }) {
                            Text("+Искл", color = Color(0xFF86EFAC), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }

    if (errs.length() > 0) {
        MiniCard("Ошибки AdGuard dnsproxy") {
            MonoBox((0 until errs.length()).joinToString("\n") { errs.optString(it) })
        }
    }
}

@Composable
private fun NatPpePage(
    data: JSONObject?,
    devicesData: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit
) {
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    val stat = data?.optJSONArray("ip_static") ?: JSONArray()
    val upnp = data?.optJSONArray("upnp_list") ?: JSONArray()

    MiniCard("Аппаратное ускорение (PPE), UPnP и Conntrack", { onTopicHelp("nat_ppe") }) {
        MiniSwitch("Hardware NAT (ppe hardware)", "Аппаратный fast-path коммутатора", ndms.optBoolean("ppe_hw"), { onToggleHelp("ppe_hw") }) { v ->
            onAction("Hardware NAT", MonitorScripts.ndmcSaveCmd(if (v == 1) "ppe hardware" else "no ppe hardware"))
        }
        MiniSwitch("Software PPE (ppe software)", "Ядерный fast-path ускоритель", ndms.optBoolean("ppe_sw"), { onToggleHelp("ppe_sw") }) { v ->
            onAction("Software PPE", MonitorScripts.ndmcSaveCmd(if (v == 1) "ppe software" else "no ppe software"))
        }
        MiniSwitch("UPnP", "Автоматическое открытие портов играми и торрентами", ndms.optBoolean("upnp"), { onToggleHelp("upnp") }) { v ->
            onAction("UPnP", MonitorScripts.ndmcSaveCmd(if (v == 1) "service upnp" else "no service upnp"))
        }
        MiniSwitch("IGMP Proxy (IPTV)", "Пропуск мультикаст-потоков ТВ провайдера", ndms.optBoolean("igmp"), { onToggleHelp("igmp") }) { v ->
            onAction("IGMP", MonitorScripts.ndmcSaveCmd(if (v == 1) "service igmp-proxy" else "no service igmp-proxy"))
        }
        val ctMax = ndms.optInt("ct_max", 32768)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Лимит Conntrack: $ctMax", color = Color(0xFFCBD5E1), fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { onAction("Conntrack 32K", MonitorScripts.ndmcSaveCmd("ip conntrack max-entries 32768")) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("32K", fontSize = 9.sp) }
                OutlinedButton(onClick = { onAction("Conntrack 65K", MonitorScripts.ndmcSaveCmd("ip conntrack max-entries 65536")) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("65K", fontSize = 9.sp) }
            }
        }
    }

    var proto by remember { mutableStateOf("tcp") }
    var extPort by remember { mutableStateOf("") }
    var targetIp by remember { mutableStateOf("") }
    var locPort by remember { mutableStateOf("") }
    val devArr = devicesData?.optJSONArray("devices") ?: JSONArray()

    MiniCard("Проброс портов (ip static) (${stat.length()})", {
        onQuickHelp("Проброс портов (ip static)", "Перенаправляет входящие соединения с WAN на устройство внутри локальной сети.", "Полезно для серверов, NAS, камер и игровых консолей.", "Диапазон можно указать через дефис (напр. 3478-3480).", "")
    }) {
        for (i in 0 until stat.length()) {
            val rule = stat.optString(i)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(rule, color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAction("Удалить правило", MonitorScripts.delStaticPortCmd(rule)) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(15.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = proto == "tcp", onClick = { proto = "tcp" }, label = { Text("TCP", fontSize = 10.sp) })
            FilterChip(selected = proto == "udp", onClick = { proto = "udp" }, label = { Text("UDP", fontSize = 10.sp) })
            OutlinedTextField(value = extPort, onValueChange = { extPort = it }, placeholder = { Text("порт / 8080-8090", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = targetIp, onValueChange = { targetIp = it }, label = { Text("IP устройства", fontSize = 9.sp) }, placeholder = { Text("192.168.1.x", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedTextField(value = locPort, onValueChange = { locPort = it }, label = { Text("Внутр. порт", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.width(88.dp))
            Button(onClick = {
                if (extPort.isNotBlank() && targetIp.isNotBlank()) {
                    onAction("Проброс $proto $extPort", MonitorScripts.addStaticPortCmd(proto, extPort, targetIp, locPort))
                    extPort = ""; locPort = ""
                }
            }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+", fontSize = 14.sp) }
        }
        if (devArr.length() > 0) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until minOf(devArr.length(), 8)) {
                    val d = devArr.optJSONObject(i) ?: continue
                    val ip = d.optString("ip")
                    if (ip.isBlank() || ip == "0.0.0.0") continue
                    val nm = d.optString("name").ifBlank { d.optString("hostname") }.ifBlank { ip }.take(14)
                    AssistChip(onClick = { targetIp = ip }, label = { Text("$nm ($ip)", fontSize = 9.sp) })
                }
            }
        }
    }

    MiniCard("Активные сессии UPnP (${upnp.length()})") {
        if (upnp.length() == 0) Text("Сейчас активных UPnP-портов нет", color = Color(0xFF64748B), fontSize = 11.sp)
        else MonoBox((0 until upnp.length()).joinToString("\n") { upnp.optString(it) })
    }
}

@Composable
private fun BypassModesPage(
    data: JSONObject?,
    probeOut: String,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onAction: (String, String) -> Unit,
    onProbe: () -> Unit
) {
    val z2k = data?.optJSONObject("z2k") ?: JSONObject()
    val pol = data?.optJSONObject("policy") ?: JSONObject()
    val cfgLines = data?.optJSONArray("cfg_lines") ?: JSONArray()
    val nfqPkts = data?.optJSONArray("nfq_pkts") ?: JSONArray()
    val mode = z2k.optString("mode", "auto")

    MiniCard("Управление Zapret2 / z2k", { onTopicHelp("z2k") }) {
        MiniRow("Статус nfqws2", if (z2k.optBoolean("running", false)) "Активен" else "Остановлен", if (z2k.optBoolean("running", false)) Color(0xFF4ADE80) else Color(0xFFF87171))
        MiniRow("Текущий режим", mode)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("autohostlist" to "Autohost", "hostlist" to "Hostlist", "direct" to "Direct").forEach { (id, lbl) ->
                FilterChip(
                    selected = mode.contains(id, ignoreCase = true),
                    onClick = { onAction("Режим $lbl", MonitorScripts.setZ2kModeCmd(id)) },
                    label = { Text(lbl, fontSize = 10.sp) }
                )
            }
        }
        MiniSwitch("Блокировать IPv6", "Предотвращает обход правил через IPv6", z2k.optBoolean("no_ipv6"), { onToggleHelp("no_ipv6") }) { v ->
            onAction("IPv6", MonitorScripts.toggleZ2k("ipv6-disable", v))
        }
        MiniSwitch("Autohostlist", "Автоматически добавляет заблокированные сайты при сбоях TLS", z2k.optBoolean("autohost"), { onToggleHelp("autohost") }) { v ->
            onAction("Autohostlist", MonitorScripts.toggleZ2k("autohostlist", v))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { onAction("Рестарт nfqws2", MonitorScripts.svcCmd("S99zapret2", "restart")) }, modifier = Modifier.weight(1f).height(32.dp)) { Text("Рестарт z2k", fontSize = 10.5.sp) }
            OutlinedButton(onClick = onProbe, modifier = Modifier.weight(1f).height(32.dp)) { Text("Тест сайтов", fontSize = 10.5.sp) }
        }
        if (probeOut.isNotBlank()) MonoBox(probeOut)
    }

    var polName by remember(pol) { mutableStateOf(pol.optString("name", "Gor4akovNET")) }
    var polEx by remember(pol) { mutableStateOf(pol.optInt("exclude", 0) == 1) }
    MiniCard("Политика маршрутизации NDMS") {
        OutlinedTextField(value = polName, onValueChange = { polName = it }, label = { Text("Имя политики (напр. Gor4akovNET)", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.fillMaxWidth())
        MiniSwitch("Инвертировать (exclude)", "Если выкл — обход работает только внутри указанной политики", polEx, { onToggleHelp("pol_ex") }) { v -> polEx = v == 1 }
        Button(onClick = { if (polName.isNotBlank()) onAction("Сохранить политику", MonitorScripts.setPolicyCmd(polName, if (polEx) 1 else 0)) }, modifier = Modifier.fillMaxWidth().height(32.dp)) {
            Text("Применить политику и перезапустить z2k", fontSize = 11.sp)
        }
    }

    if (nfqPkts.length() > 0) {
        MiniCard("Счётчики пакетов NFQUEUE (реально перехвачено)") {
            MonoBox((0 until nfqPkts.length()).joinToString("\n") { nfqPkts.optString(it) })
        }
    }

    if (cfgLines.length() > 0) {
        MiniCard("Текущий /opt/zapret2/config") {
            MonoBox((0 until cfgLines.length()).joinToString("\n") { cfgLines.optString(it) })
        }
    }
}

@Composable
private fun WarpPage(
    data: JSONObject?,
    traceOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onTrace: () -> Unit,
    onAction: (String, String) -> Unit
) {
    val st = data?.optJSONObject("status") ?: JSONObject()
    val installed = st.optBoolean("installed") || st.optBoolean("running") || st.optBoolean("ready")
    val running = st.optBoolean("running") || st.optBoolean("ready")
    val transport = st.optString("transport", "auto")
    val tunIp = data?.optString("tun_ip").orEmpty()
    val tunMtu = data?.optString("tun_mtu").orEmpty()
    val ramMb = data?.optString("ram_mb").orEmpty()
    val games = data?.optJSONObject("games")?.optJSONArray("games") ?: JSONArray()
    val listDoms = data?.optJSONObject("lists")?.optJSONArray("domains") ?: JSONArray()
    val exclDoms = data?.optJSONObject("exclude")?.optJSONArray("domains") ?: JSONArray()
    val devArr = data?.optJSONObject("devices")?.optJSONArray("devices") ?: JSONArray()

    var subTab by remember { mutableIntStateOf(0) }
    var warpDomInput by remember { mutableStateOf("") }
    var warpExInput by remember { mutableStateOf("") }
    var confirmRereg by remember { mutableStateOf(false) }

    MiniCard("Cloudflare WARP (туннель z2k)", { onTopicHelp("warp") }) {
        MiniRow("Статус", if (!installed) "Не установлен" else if (running) "Работает" else "Выключен", if (running) Color(0xFF4ADE80) else Color(0xFFFBBF24))
        if (installed) {
            MiniRow("Интерфейс z2ktun0", "${tunIp.ifBlank { "не поднят" }} • MTU ${tunMtu.ifBlank { "—" }} • RAM ${ramMb.ifBlank { "—" }} МБ")
            Text("Транспорт WARP (при блокировке WireGuard выберите MASQUE H2):", color = Color(0xFF94A3B8), fontSize = 9.5.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("auto" to "Auto", "wg" to "WireGuard", "h2" to "MASQUE H2").forEach { (id, lbl) ->
                    FilterChip(selected = transport.equals(id, true), onClick = { onAction("Транспорт $lbl", MonitorScripts.warpTransportCmd(id)) }, label = { Text(lbl, fontSize = 9.5.sp) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Button(onClick = { onAction(if (running) "Стоп WARP" else "Старт WARP", MonitorScripts.toggleZ2k("game-warp", if (running) 0 else 1)) }, enabled = !busy, modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(4.dp)) {
                    Text(if (running) "Выключить" else "Включить", fontSize = 10.sp)
                }
                OutlinedButton(onClick = onTrace, enabled = !busy, modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(4.dp)) {
                    Text("Cloudflare Trace", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { confirmRereg = true }, enabled = !busy, modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(4.dp)) {
                    Text("Ключи заново", fontSize = 9.5.sp)
                }
            }
        } else {
            Button(onClick = { onAction("Установка WARP", MonitorScripts.warpInstallCmd()) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(34.dp)) {
                Text("Установить Cloudflare WARP", fontSize = 11.sp)
            }
        }
        if (traceOut.isNotBlank()) MonoBox(traceOut)
    }

    if (installed) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("Игры (${games.length()})", fontSize = 9.5.sp) })
            FilterChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("Домены (${listDoms.length()}/${exclDoms.length()})", fontSize = 9.5.sp) })
            FilterChip(selected = subTab == 2, onClick = { subTab = 2 }, label = { Text("Устройства (${devArr.length()})", fontSize = 9.5.sp) })
        }

        when (subTab) {
            0 -> MiniCard("Игры и сервисы через WARP (${games.length()})") {
                if (games.length() == 0) Text("Список игр z2k пуст (требуется активный S96z2k-webpanel :8088)", color = Color(0xFF64748B), fontSize = 10.sp)
                for (i in 0 until games.length()) {
                    val g = games.optJSONObject(i) ?: continue
                    val nm = g.optString("name")
                    val en = g.optInt("enabled", 0) == 1 || g.optBoolean("enabled", false)
                    MiniSwitch(nm, g.optString("desc", "Точечная маршрутизация в z2ktun0"), en) { v ->
                        onAction("WARP $nm", MonitorScripts.warpGameToggleCmd(nm, v))
                    }
                }
            }
            1 -> {
                MiniCard("Домены через WARP (${listDoms.length()})") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = warpDomInput, onValueChange = { warpDomInput = it }, placeholder = { Text("claude.ai / spotify.com", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
                        Button(onClick = {
                            if (warpDomInput.isNotBlank()) {
                                onAction("+WARP $warpDomInput", MonitorScripts.warpDomainRuleCmd("lists", warpDomInput, true))
                                warpDomInput = ""
                            }
                        }, modifier = Modifier.height(34.dp)) { Text("+") }
                    }
                    for (i in 0 until minOf(listDoms.length(), 40)) {
                        val d = listDoms.optString(i)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(d, color = Color(0xFFCBD5E1), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = { onAction("-WARP $d", MonitorScripts.warpDomainRuleCmd("lists", d, false)) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                MiniCard("Исключения WARP — всегда напрямую (${exclDoms.length()})") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = warpExInput, onValueChange = { warpExInput = it }, placeholder = { Text("sberbank.ru", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
                        Button(onClick = {
                            if (warpExInput.isNotBlank()) {
                                onAction("+Искл WARP $warpExInput", MonitorScripts.warpDomainRuleCmd("exclude", warpExInput, true))
                                warpExInput = ""
                            }
                        }, modifier = Modifier.height(34.dp)) { Text("+") }
                    }
                    for (i in 0 until minOf(exclDoms.length(), 40)) {
                        val d = exclDoms.optString(i)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(d, color = Color(0xFFCBD5E1), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = { onAction("-Искл WARP $d", MonitorScripts.warpDomainRuleCmd("exclude", d, false)) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            2 -> MiniCard("Весь трафик устройства через WARP (${devArr.length()})") {
                if (devArr.length() == 0) Text("Список устройств WARP пуст (доступен при включённой панели z2k :8088)", color = Color(0xFF64748B), fontSize = 10.sp)
                for (i in 0 until devArr.length()) {
                    val d = devArr.optJSONObject(i) ?: continue
                    val ip = d.optString("ip").ifBlank { d.optString("mac") }
                    val nm = d.optString("name", ip)
                    val en = d.optInt("enabled", 0) == 1 || d.optBoolean("enabled", false)
                    MiniSwitch(nm, ip, en) { v -> onAction("WARP $nm", MonitorScripts.warpDeviceToggleCmd(ip, v == 1)) }
                }
            }
        }
    }

    if (confirmRereg) {
        AlertDialog(
            onDismissRequest = { confirmRereg = false },
            title = { Text("Перерегистрировать WARP?") },
            text = { Text("Создаст новый ключ аккаунта Cloudflare WARP. Помогает при бане текущего IP или сбоях рукопожатия.") },
            confirmButton = {
                Button(onClick = { confirmRereg = false; onAction("Перерегистрация WARP", MonitorScripts.warpReregisterCmd()) }) { Text("Выполнить") }
            },
            dismissButton = { TextButton(onClick = { confirmRereg = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun BypassListsPage(
    data: JSONObject?,
    domainInput: String,
    onDomainChange: (String) -> Unit,
    wlInput: String,
    onWlChange: (String) -> Unit,
    pickDomain: String,
    onPickChange: (String) -> Unit,
    pickOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onAction: (String, String) -> Unit,
    onPick: (String, String) -> Unit,
    onApplyPool: (String, String, String) -> Unit
) {
    val exArr = data?.optJSONArray("extra") ?: JSONArray()
    val wlArr = data?.optJSONArray("whitelist") ?: JSONArray()
    val wlPath = data?.optString("wl_file", "/opt/zapret2/lists/whitelist.txt") ?: "/opt/zapret2/lists/whitelist.txt"
    val nfqPkts = data?.optJSONArray("nfq_pkts") ?: JSONArray()
    val poolInfo = data?.optJSONArray("pool_info") ?: JSONArray()

    var pickMode by remember { mutableStateOf("tcp13") }
    var targetPool by remember { mutableStateOf("rkn_tcp") }

    val parsedPick = remember(pickOut) { runCatching { parseJsonOut(pickOut, "pick") }.getOrNull() }
    val extractedStrategy = remember(pickOut, parsedPick) {
        parsedPick?.optString("best_strategy")?.ifBlank { parsedPick.optString("strategy") }
            ?: Regex("""(--lua-desync=[^\n"]+)""").find(pickOut)?.groupValues?.getOrNull(1)
            ?: ""
    }

    MiniCard("Подбор рабочей стратегии z2k", { onTopicHelp("z2k") }) {
        OutlinedTextField(value = pickDomain, onValueChange = onPickChange, label = { Text("Домен для теста", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("tcp13" to "TLS 1.3", "tcp12" to "TLS 1.2", "mixed" to "Mixed", "quic" to "QUIC/UDP", "voice" to "Voice UDP").forEach { (id, lbl) ->
                FilterChip(selected = pickMode == id, onClick = { pickMode = id }, label = { Text(lbl, fontSize = 9.5.sp) })
            }
        }
        Button(
            onClick = { if (pickDomain.isNotBlank()) onPick(pickDomain, pickMode) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(34.dp)
        ) { Text("Запустить автоподбор ($pickMode)", fontSize = 11.sp) }

        if (pickOut.isNotBlank()) {
            if (extractedStrategy.isNotBlank()) {
                Surface(color = Color(0xFF052E16), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF16A34A)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Найдена рабочая стратегия:", color = Color(0xFF86EFAC), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(extractedStrategy, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("rkn_tcp", "yt_tcp", "gv_tcp", "yt_quic", "discord_udp").forEach { p ->
                                FilterChip(selected = targetPool == p, onClick = { targetPool = p }, label = { Text(p, fontSize = 9.sp) })
                            }
                        }
                        Button(onClick = { onApplyPool(targetPool, pickDomain, extractedStrategy) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(30.dp)) {
                            Text("Применить в пул $targetPool", fontSize = 10.sp)
                        }
                    }
                }
            }
            MonoBox(pickOut.takeLast(3500))
        }
    }

    MiniCard("Активные пулы стратегий и Autocircular") {
        if (poolInfo.length() > 0) {
            MonoBox((0 until poolInfo.length()).joinToString("\n") { poolInfo.optString(it) })
        }
        if (nfqPkts.length() > 0) {
            MonoBox((0 until nfqPkts.length()).joinToString("\n") { nfqPkts.optString(it) })
        }
        OutlinedButton(onClick = { onAction("Сброс Autocircular", MonitorScripts.RESET_AUTOCIRCULAR_CMD) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(30.dp)) {
            Text("Сбросить кэш Autocircular и перезапустить nfqws2", fontSize = 10.sp)
        }
    }

    MiniCard("Доп. домены для обхода (${exArr.length()})") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = domainInput, onValueChange = onDomainChange, placeholder = { Text("example.com", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            Button(onClick = {
                val d = domainInput.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
                if (d.matches(Regex("""^[a-z0-9.-]+$""")) && d.contains('.')) {
                    onDomainChange("")
                    onAction("Добавить $d", MonitorScripts.addExtraDomain(d))
                }
            }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+", fontSize = 13.sp) }
        }
        for (i in 0 until minOf(exArr.length(), 40)) {
            val d = exArr.optString(i)
            if (d.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(d, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    IconButton(onClick = { onAction("Удалить $d", MonitorScripts.delListDomain("/opt/zapret2/lists/extra-domains.txt", d)) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }

    MiniCard("Белый список / Исключения (${wlArr.length()})") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = wlInput, onValueChange = onWlChange, placeholder = { Text("sberbank.ru", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            Button(onClick = {
                val d = wlInput.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
                if (d.matches(Regex("""^[a-z0-9.-]+$""")) && d.contains('.')) {
                    onWlChange("")
                    onAction("Исключение $d", MonitorScripts.addWhitelistDomain(d, wlPath))
                }
            }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+", fontSize = 13.sp) }
        }
        for (i in 0 until minOf(wlArr.length(), 40)) {
            val d = wlArr.optString(i)
            if (d.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(d, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    IconButton(onClick = { onAction("Убрать $d", MonitorScripts.delListDomain(wlPath, d)) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiPage(
    data: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onAction: (String, String) -> Unit
) {
    val wifi = data?.optJSONObject("wifi") ?: JSONObject()
    val aps = wifi.optJSONArray("aps") ?: JSONArray()

    MiniCard("Радиомодули Wi-Fi", { onTopicHelp("wifi") }) {
        MiniRow("${wifi.optString("g24_ssid", "Home")} (2.4 ГГц)", "${if (wifi.optBoolean("g24_up")) "ON" else "OFF"} • кан.${wifi.optString("g24_ch", "—")} • ${wifi.optString("g24_bw", "—")}M • ${wifi.optInt("g24_rate", 0)}M • ${wifi.optString("g24_temp", "—")}°C", if (wifi.optBoolean("g24_up")) Color(0xFF4ADE80) else Color(0xFFF87171))
        val bz24 = wifi.optString("g24_busy")
        if (bz24.isNotBlank()) Text("Занятые 2.4G: $bz24", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        MiniRow("${wifi.optString("g5_ssid", "Home")} (5 ГГц)", "${if (wifi.optBoolean("g5_up")) "ON" else "OFF"} • кан.${wifi.optString("g5_ch", "—")} • ${wifi.optString("g5_bw", "—")}M • ${wifi.optInt("g5_rate", 0)}M • ${wifi.optString("g5_temp", "—")}°C", if (wifi.optBoolean("g5_up")) Color(0xFF4ADE80) else Color(0xFFF87171))
        val bz5 = wifi.optString("g5_busy")
        if (bz5.isNotBlank()) Text("Занятые 5G: $bz5", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }

    if (aps.length() > 0) {
        MiniCard("Обнаруженные точки AccessPoint (${aps.length()})") {
            for (i in 0 until aps.length()) {
                val a = aps.optJSONObject(i) ?: continue
                val apId = a.optString("id")
                val ssid = a.optString("ssid").ifBlank { "(без SSID)" }
                val alias = a.optString("alias")
                val grp = a.optString("grp")
                val isUp = a.optBoolean("up")
                val isDead = a.optBoolean("dead")
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$apId ${if (alias.isNotBlank()) "($alias)" else ""}", color = if (isDead) Color(0xFFFCD34D) else Color.White, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                        Text("SSID: $ssid • Сегмент: ${grp.ifBlank { "-" }}", color = Color(0xFF94A3B8), fontSize = 9.5.sp)
                    }
                    if (isDead) {
                        OutlinedButton(onClick = { onAction("Очистить $apId", MonitorScripts.disableDeadApCmd(apId)) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) {
                            Text("Очистить", fontSize = 9.sp, color = Color(0xFFFBBF24))
                        }
                    } else {
                        MiniPill(if (isUp) "UP" else "DOWN", isUp)
                    }
                }
            }
        }
    }

    MiniCard("Каналы и ширина полосы", { onTopicHelp("wifi") }) {
        Text("2.4 ГГц (реком. 1, 6, 11 • 20 МГц):", color = Color(0xFF94A3B8), fontSize = 10.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("auto", "1", "6", "11").forEach { ch ->
                OutlinedButton(onClick = { onAction("2.4G канал $ch", MonitorScripts.setWifiChannelCmd(0, ch, "20")) }, modifier = Modifier.height(26.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Кан.$ch", fontSize = 10.sp) }
            }
        }
        Text("5 ГГц (без DFS: 36–48 • ширина 80 МГц):", color = Color(0xFF94A3B8), fontSize = 10.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("auto", "36", "40", "44", "48", "52", "149").forEach { ch ->
                OutlinedButton(onClick = { onAction("5G канал $ch", MonitorScripts.setWifiChannelCmd(1, ch, "40-above/80")) }, modifier = Modifier.height(26.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Кан.$ch", fontSize = 10.sp) }
            }
        }
    }

    MiniCard("Функции Wi-Fi и Гостевая сеть") {
        MiniSwitch("Band Steering (Bridge0 / MWS)", "Переводит гаджеты между 2.4G и 5G", wifi.optBoolean("band_steer"), { onToggleHelp("band_steer") }) { v ->
            onAction("Band Steering", MonitorScripts.setBandSteeringCmd(v == 1))
        }
        MiniSwitch("Быстрый роуминг 802.11r (FT)", "Мгновенный переход 2.4G <-> 5G", wifi.optBoolean("ft"), { onToggleHelp("ft") }) { v ->
            onAction("802.11r FT", MonitorScripts.setWifiFtCmd(v == 1))
        }
        MiniSwitch("Target Wake Time (Wi-Fi 6 AX)", "Экономит батарею устройств на 5 ГГц", wifi.optBoolean("twt"), { onToggleHelp("twt") }) { v ->
            onAction("Wi-Fi 6 TWT", MonitorScripts.ndmcSaveCmd("interface WifiMaster1 ${if (v == 1) "target-waketime" else "no target-waketime"}"))
        }
        MiniSwitch("Гостевая сеть (Guest)", "Изолированный Wi-Fi (AccessPoint1)", wifi.optBoolean("guest_up"), { onToggleHelp("guest_up") }) { v ->
            val act = if (v == 1) "up" else "down"
            onAction("Гостевой Wi-Fi", MonitorScripts.ndmcSaveCmd("interface WifiMaster0/AccessPoint1 $act", "interface WifiMaster1/AccessPoint1 $act"))
        }
        var newSsid by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newSsid, onValueChange = { newSsid = it }, placeholder = { Text("Новый основной SSID", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { if (newSsid.isNotBlank()) { onAction("Смена SSID", MonitorScripts.setWifiSsidCmd(newSsid)); newSsid = "" } }, modifier = Modifier.height(34.dp)) { Text("Задать", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun DevicesPage(
    data: JSONObject?,
    search: String,
    onSearchChange: (String) -> Unit,
    filterMode: String,
    onFilterChange: (String) -> Unit,
    onTopicHelp: (String) -> Unit,
    onSelectDevice: (JSONObject) -> Unit,
    onAction: (String, String) -> Unit
) {
    val arr = data?.optJSONArray("devices") ?: JSONArray()
    val all = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    val filtered = all
        .filter { d ->
            val matchText = search.isBlank() ||
                d.optString("name").contains(search, true) ||
                d.optString("hostname").contains(search, true) ||
                d.optString("ip").contains(search, true) ||
                d.optString("mac").contains(search, true)
            val matchFilter = when (filterMode) {
                "online" -> d.optString("link") == "up"
                "bypass" -> d.optString("route_pol").equals("Gor4akovNET", true)
                "deny" -> d.optString("policy") == "deny"
                "timer" -> d.optString("timer").isNotBlank() || d.optString("sched").isNotBlank()
                else -> true
            }
            matchText && matchFilter
        }
        .sortedWith(compareByDescending<JSONObject> { it.optString("link") == "up" }.thenBy { it.optString("name").ifBlank { it.optString("hostname") } })

    MiniCard("Клиенты сети (${filtered.size} / ${all.size})", { onTopicHelp("devices") }) {
        OutlinedTextField(value = search, onValueChange = onSearchChange, placeholder = { Text("Поиск по имени, IP или MAC...", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("all" to "Все", "online" to "Онлайн", "bypass" to "В обходе", "deny" to "В блоке", "timer" to "С таймером").forEach { (id, lbl) ->
                FilterChip(selected = filterMode == id, onClick = { onFilterChange(id) }, label = { Text(lbl, fontSize = 9.5.sp) })
            }
        }
    }

    filtered.forEach { d ->
        val mac = d.optString("mac")
        val ip = d.optString("ip").ifBlank { "—" }
        val title = d.optString("name").ifBlank { d.optString("hostname") }.ifBlank { mac }
        val online = d.optString("link") == "up"
        val blocked = d.optString("policy") == "deny"
        val routePol = d.optString("route_pol", "Default")
        val inG4 = routePol.equals("Gor4akovNET", true)
        val prio = d.optInt("prio", 6)
        val shapeKbps = d.optInt("shape_kbps", 0)
        val timerStr = d.optString("timer")
        val schedStr = d.optString("sched")
        val ap = d.optString("ap")
        val rssi = d.optString("rssi")
        val txr = d.optString("txrate")
        val spd = d.optString("speed")

        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, if (blocked) Color(0xFF991B1B) else if (inG4) Color(0xFF065F46) else if (online) Color(0xFF1E3A8A) else Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth().clickable { onSelectDevice(d) }
        ) {
            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (blocked) Color(0xFFEF4444) else if (online) Color(0xFF22C55E) else Color(0xFF475569)))
                        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(ip, color = Color(0xFF93C5FD), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                val radio = if (ap.isNotBlank()) "Wi-Fi ${d.optString("mode")} • RSSI ${rssi}dBm • ${txr}M" else "LAN ${spd}M"
                Text("$mac • ${d.optString("iface", "-")} • $radio • ↓${formatBytes(d.optDouble("rx"))} ↑${formatBytes(d.optDouble("tx"))}", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)

                val tags = mutableListOf<String>()
                if (inG4) tags.add("Обход")
                if (prio != 6) tags.add("QoS P$prio")
                if (shapeKbps > 0) tags.add("${shapeKbps / 1000} Мбит/с")
                if (timerStr.isNotBlank()) tags.add("Таймер $timerStr")
                if (schedStr.isNotBlank()) tags.add("Расписание")
                if (tags.isNotEmpty()) {
                    Text(tags.joinToString(" • "), color = Color(0xFF86EFAC), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { onSelectDevice(d) }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Управлять / Таймер", fontSize = 9.5.sp)
                    }
                    FilterChip(
                        selected = inG4,
                        onClick = { onAction("Политика $title", MonitorScripts.setHostPolicyRouteCmd(mac, if (inG4) null else "Gor4akovNET")) },
                        label = { Text(if (inG4) "Обход: ВКЛ" else "В обход", fontSize = 9.5.sp) }
                    )
                    OutlinedButton(onClick = { onAction(if (blocked) "Разблокировать" else "Блок", MonitorScripts.ndmcSaveCmd(if (blocked) "ip hotspot host $mac permit" else "ip hotspot host $mac deny")) }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(if (blocked) "Разблок." else "Блок", fontSize = 9.5.sp, color = if (blocked) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
                    }
                    OutlinedButton(onClick = { onAction("WoL", MonitorScripts.wolCmd(mac)) }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("WoL", fontSize = 9.5.sp) }
                }
            }
        }
    }
}

@Composable
private fun DeviceManageDialog(
    dev: JSONObject,
    busy: Boolean,
    onDismiss: () -> Unit,
    onExec: (String, String, Boolean) -> Unit,
    onPingDevice: (String) -> Unit
) {
    val mac = dev.optString("mac")
    val ip = dev.optString("ip").ifBlank { "—" }
    val curName = dev.optString("name").ifBlank { dev.optString("hostname") }.ifBlank { mac }
    val blocked = dev.optString("policy") == "deny"
    val inG4 = dev.optString("route_pol").equals("Gor4akovNET", true)
    val prio = dev.optInt("prio", 6)
    val cnf = dev.optBoolean("conform")
    val curShape = dev.optInt("shape_kbps", 0)
    val curTimer = dev.optString("timer")
    val curSched = dev.optString("sched")

    var timerMode by remember { mutableStateOf("permit") }
    var shapeText by remember { mutableStateOf(if (curShape > 0) curShape.toString() else "") }
    var schedStart by remember { mutableStateOf("9") }
    var schedStop by remember { mutableStateOf("22") }
    var staticIpInput by remember { mutableStateOf(if (ip == "—") "" else ip) }
    var renameInput by remember { mutableStateOf(curName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(curName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("$mac • $ip", color = Color(0xFF93C5FD), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("1. Таймер доступа на роутере (автономно)", color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (curTimer.isNotBlank()) Text("Активный таймер: $curTimer", color = Color(0xFF86EFAC), fontSize = 10.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(selected = timerMode == "permit", onClick = { timerMode = "permit" }, label = { Text("Интернет (после — Блок)", fontSize = 9.sp) })
                    FilterChip(selected = timerMode == "gor4akov", onClick = { timerMode = "gor4akov" }, label = { Text("Обход (после — Выкл)", fontSize = 9.sp) })
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(15, 30, 60, 120).forEach { m ->
                        Button(onClick = { onExec("Таймер ${m}м ($curName)", MonitorScripts.setHostTimerCmd(mac, m, timerMode), true) }, enabled = !busy, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("${m}м", fontSize = 10.sp)
                        }
                    }
                    OutlinedButton(onClick = { onExec("Отмена таймера", MonitorScripts.setHostTimerCmd(mac, 0, timerMode), true) }, enabled = !busy, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Отмена", fontSize = 10.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))
                Text("2. Ежедневное расписание NDMS${if (curSched.isNotBlank()) " ($curSched)" else ""}", color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = schedStart, onValueChange = { schedStart = it.filter { c -> c.isDigit() } }, label = { Text("С часа", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = schedStop, onValueChange = { schedStop = it.filter { c -> c.isDigit() } }, label = { Text("До часа", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
                    Button(onClick = {
                        val s = schedStart.toIntOrNull() ?: 9
                        val e = schedStop.toIntOrNull() ?: 22
                        onExec("Расписание $s-$e", MonitorScripts.setHostScheduleCmd(mac, s, e, true), true)
                    }, modifier = Modifier.height(34.dp)) { Text("Вкл", fontSize = 10.sp) }
                    OutlinedButton(onClick = { onExec("Убрать расписание", MonitorScripts.setHostScheduleCmd(mac, 0, 0, false), true) }, modifier = Modifier.height(34.dp)) { Text("Выкл", fontSize = 10.sp) }
                }

                HorizontalDivider(color = Color(0xFF1E293B))
                Text("3. Ограничение скорости (Шейпер)", color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "Без лимита", 10000 to "10М", 25000 to "25М", 50000 to "50М", 100000 to "100М").forEach { (kbps, lbl) ->
                        FilterChip(selected = curShape == kbps, onClick = { onExec("Шейпер $lbl", MonitorScripts.setHostShapeCmd(mac, kbps), true) }, label = { Text(lbl, fontSize = 9.5.sp) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = shapeText, onValueChange = { shapeText = it.filter { c -> c.isDigit() } }, label = { Text("Лимит (кбит/с)", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
                    Button(onClick = { onExec("Шейпер", MonitorScripts.setHostShapeCmd(mac, shapeText.toIntOrNull() ?: 0), true) }, modifier = Modifier.height(34.dp)) { Text("Прим.", fontSize = 10.sp) }
                }

                HorizontalDivider(color = Color(0xFF1E293B))
                Text("4. Маршрут и приоритет QoS", color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(selected = !inG4, onClick = { onExec("Маршрут Default", MonitorScripts.setHostPolicyRouteCmd(mac, null), true) }, label = { Text("Обычный LAN", fontSize = 9.5.sp) })
                    FilterChip(selected = inG4, onClick = { onExec("Маршрут Gor4akovNET", MonitorScripts.setHostPolicyRouteCmd(mac, "Gor4akovNET"), true) }, label = { Text("Обход Gor4akovNET", fontSize = 9.5.sp) })
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { p ->
                        FilterChip(selected = prio == p, onClick = { onExec("QoS P$p", MonitorScripts.setHostPriorityCmd(mac, p, cnf), true) }, label = { Text("P$p", fontSize = 9.sp) })
                    }
                    FilterChip(selected = cnf, onClick = { onExec("Conform", MonitorScripts.setHostPriorityCmd(mac, prio, !cnf), true) }, label = { Text("Conform", fontSize = 9.sp) })
                }

                HorizontalDivider(color = Color(0xFF1E293B))
                Text("5. Статический DHCP и Имя", color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = renameInput, onValueChange = { renameInput = it }, label = { Text("Имя устройства", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = staticIpInput, onValueChange = { staticIpInput = it }, label = { Text("Фикс. IP", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
                    Button(onClick = { onExec("Сохранить DHCP", MonitorScripts.setHostStaticIpCmd(mac, staticIpInput, renameInput), true) }, modifier = Modifier.height(34.dp)) { Text("Закреп.", fontSize = 10.sp) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (ip != "—") {
                        OutlinedButton(onClick = { onPingDevice(ip) }, modifier = Modifier.weight(1f).height(30.dp)) { Text("Ping", fontSize = 10.sp) }
                    }
                    OutlinedButton(onClick = { onExec("WoL", MonitorScripts.wolCmd(mac), false) }, modifier = Modifier.weight(1f).height(30.dp)) { Text("WoL", fontSize = 10.sp) }
                    OutlinedButton(
                        onClick = { onExec(if (blocked) "Разблокировать" else "Блок", MonitorScripts.ndmcSaveCmd(if (blocked) "ip hotspot host $mac permit" else "ip hotspot host $mac deny"), true) },
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) { Text(if (blocked) "Разблок" else "Блок", fontSize = 10.sp, color = if (blocked) Color(0xFF86EFAC) else Color(0xFFFCA5A5)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun BackupsPage(
    backups: List<BackupEntry>,
    estText: String,
    includeMedia: Boolean,
    onToggleMedia: (Boolean) -> Unit,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onEstimate: () -> Unit,
    onCreateFull: () -> Unit,
    onCreateCfg: () -> Unit,
    onImport: () -> Unit,
    onShare: (BackupEntry) -> Unit,
    onRestore: (BackupEntry) -> Unit,
    onViewTxt: (BackupEntry) -> Unit,
    onDelete: (String) -> Unit
) {
    MiniCard("Полный 100% потоковый бэкап v2", { onTopicHelp("backups") }) {
        Text("Включает running/startup-config, весь /opt/etc (Dropbear SSH ключи, AdGuard, конфиги), /opt/zapret2, /opt/root, /opt/home, Cron и при необходимости все файлы USB/Storage.", color = Color(0xFF94A3B8), fontSize = 10.sp)
        MiniSwitch("Включить файлы USB / Storage (100%)", "Может занять много памяти телефона — оцените размер ниже", includeMedia) { v ->
            onToggleMedia(v == 1)
        }
        if (estText.isNotBlank()) {
            MonoBox(estText)
        } else {
            Text("Нажмите «Проверить USB / размер», чтобы увидеть подключённые накопители и размер снимка.", color = Color(0xFF94A3B8), fontSize = 9.5.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = onCreateFull, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) { Text("Полный v2 (.tar.gz)", fontSize = 10.sp) }
            OutlinedButton(onClick = onCreateCfg, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) { Text("NDMS (.txt)", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = onEstimate, enabled = !busy, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(4.dp)) { Text("Проверить USB / размер", fontSize = 9.5.sp) }
            OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(4.dp)) { Text("Импорт с телефона", fontSize = 9.5.sp) }
        }
    }

    MiniCard("Сохранённые снимки в телефоне (${backups.size})") {
        if (backups.isEmpty()) {
            Text("Пока нет сохранённых бэкапов", color = Color(0xFF64748B), fontSize = 11.sp)
        } else {
            backups.forEach { b ->
                val isTar = b.filePath.endsWith(".tar.gz", true)
                Surface(
                    color = Color(0xFF111827),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, if (b.isProtected) Color(0xFF065F46) else Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${if (b.isProtected) "🛡 " else ""}${b.label}",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            MiniPill(b.format.uppercase(), isTar)
                        }
                        Text(
                            "${b.timestamp} • ${b.model} • ${formatSizeMb(b.sizeBytes)}${if (b.sha256.isNotBlank()) " • sha:${b.sha256.take(8)}" else ""}",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Button(onClick = { onRestore(b) }, enabled = !busy, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Откат", fontSize = 10.sp) }
                            if (!isTar) {
                                OutlinedButton(onClick = { onViewTxt(b) }, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Просмотр", fontSize = 10.sp) }
                            }
                            OutlinedButton(onClick = { onShare(b) }, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Экспорт", fontSize = 10.sp) }
                            Spacer(Modifier.weight(1f))
                            if (!b.isProtected) {
                                IconButton(onClick = { onDelete(b.id) }, modifier = Modifier.size(27.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = Color(0xFFFCA5A5), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesPage(
    dirPath: String,
    data: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRestoreBak: (String) -> Unit,
    onNewDir: () -> Unit,
    onNewFile: () -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    MiniCard("X-plore • $dirPath", { onTopicHelp("files") }) {
        val bookmarks = listOf("/opt/zapret2" to "z2k", "/opt/zapret2/lists" to "Списки", "/opt/etc/AdGuardHome" to "AdGuard", "/opt/etc/init.d" to "init.d", "/tmp" to "/tmp")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bookmarks.forEach { (p, lbl) -> AssistChip(onClick = { onOpenDir(p) }, label = { Text(lbl, fontSize = 9.5.sp) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onNewDir, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.CreateNewFolder, "Папка", tint = Color(0xFF93C5FD), modifier = Modifier.size(16.dp)) }
                IconButton(onClick = onNewFile, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.NoteAdd, "Файл", tint = Color(0xFF93C5FD), modifier = Modifier.size(16.dp)) }
                if (dirPath != "/") {
                    OutlinedButton(onClick = { onOpenDir(dirPath.substringBeforeLast('/').ifBlank { "/" }) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("↑ Вверх", fontSize = 10.sp) }
                }
            }
        }
        val err = data?.optString("err").orEmpty()
        if (err.isNotBlank()) Text(err, color = Color(0xFFFCA5A5), fontSize = 11.sp)
        val entries = data?.optJSONArray("entries") ?: JSONArray()
        val sorted = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
            .sortedWith(compareByDescending<JSONObject> { it.optBoolean("dir") }.thenBy { it.optString("name").lowercase() })
        sorted.forEach { e ->
            val nm = e.optString("name")
            val isDir = e.optBoolean("dir")
            val isLink = e.optBoolean("link")
            val full = if (dirPath == "/") "/$nm" else "$dirPath/$nm"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF111827))
                    .clickable { if (isDir) onOpenDir(full) else onOpenFile(full) }
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Icon(
                        if (isDir || isLink) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                        null,
                        tint = when { isDir -> Color(0xFFFBBF24); isLink -> Color(0xFF38BDF8); else -> Color(0xFF94A3B8) },
                        modifier = Modifier.size(15.dp)
                    )
                    Text(nm, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (!isDir && nm.endsWith(".bak")) {
                        Surface(color = Color(0xFF451A03), shape = RoundedCornerShape(4.dp), modifier = Modifier.clickable { onRestoreBak(full) }) {
                            Text("Откатить", color = Color(0xFFFBBF24), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    Text(if (isDir) "DIR" else formatBytes(e.optDouble("size", 0.0)), color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    if (dirPath.startsWith("/opt") || dirPath.startsWith("/tmp")) {
                        IconButton(onClick = { onDeleteEntry(full) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TorchPage(
    host: String,
    onHostChange: (String) -> Unit,
    liveData: JSONObject?,
    liveEnabled: Boolean,
    onToggleLive: (Boolean) -> Unit,
    out: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onEnableAcct: () -> Unit,
    onCopy: () -> Unit,
    onRun: (String) -> Unit
) {
    val ifaces = liveData?.optJSONArray("ifaces") ?: JSONArray()
    val flows = liveData?.optJSONArray("flows") ?: JSONArray()
    val acct = liveData?.optInt("acct", 0) ?: 0
    var flowFilter by remember { mutableStateOf("") }

    MiniCard("Live Torch — Скорость и потоки в реальном времени", { onTopicHelp("torch") }) {
        MiniSwitch("Автообновление каждые 2 сек", "Живой съём интерфейсов и активных соединений", liveEnabled) { v ->
            onToggleLive(v == 1)
        }
        if (liveData != null && acct == 0) {
            Surface(color = Color(0xFF451A03), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFFF59E0B)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Счётчик байтов потоков (conntrack_acct) выключен", color = Color(0xFFFDE68A), fontSize = 9.5.sp, modifier = Modifier.weight(1f))
                    Button(onClick = onEnableAcct, modifier = Modifier.height(26.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Включить", fontSize = 9.sp) }
                }
            }
        } else if (liveData != null && acct < 0) {
            Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                Text("Ядро этой модели Keenetic без conntrack_acct — доступны live Мбит/с, PPS и активные соединения.", color = Color(0xFF94A3B8), fontSize = 9.sp, modifier = Modifier.padding(7.dp))
            }
        }
        for (i in 0 until ifaces.length()) {
            val f = ifaces.optJSONObject(i) ?: continue
            val rxK = f.optDouble("rx_kbps", 0.0)
            val txK = f.optDouble("tx_kbps", 0.0)
            if (rxK < 0.5 && txK < 0.5 && f.optDouble("pps", 0.0) < 1) continue
            MiniRow(
                f.optString("dev"),
                "↓ ${"%.2f".format(rxK / 1000.0)} Мб/с  ↑ ${"%.2f".format(txK / 1000.0)} Мб/с • ${f.optInt("pps", 0)} pps",
                if (f.optInt("drops", 0) > 0) Color(0xFFFBBF24) else Color(0xFF86EFAC)
            )
        }
    }

    if (flows.length() > 0) {
        MiniCard("Активные потоки трафика (${flows.length()})") {
            OutlinedTextField(value = flowFilter, onValueChange = { flowFilter = it }, placeholder = { Text("Фильтр по клиенту / домену / порту...", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.fillMaxWidth())
            for (i in 0 until flows.length()) {
                val fl = flows.optJSONObject(i) ?: continue
                val src = fl.optString("src")
                val target = fl.optString("host").ifBlank { fl.optString("dst") }
                val port = fl.optString("port")
                if (flowFilter.isNotBlank() && !src.contains(flowFilter, true) && !target.contains(flowFilter, true) && !port.contains(flowFilter, true)) continue
                val bytes = fl.optDouble("bytes", 0.0)
                Row(
                    Modifier.fillMaxWidth().clickable { onHostChange(fl.optString("host").ifBlank { fl.optString("dst") }) }.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$src -> $target:$port (${fl.optString("proto")})", color = Color(0xFFCBD5E1), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${fl.optInt("conns", 1)}x ${if (bytes > 0) formatBytes(bytes) else fl.optString("st")}", color = Color(0xFF93C5FD), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    MiniCard("Глубокая диагностика узла и DPI") {
        OutlinedTextField(value = host, onValueChange = onHostChange, label = { Text("IP или домен", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { onRun("ping") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Ping", fontSize = 9.5.sp) }
            Button(onClick = { onRun("trace") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Trace", fontSize = 9.5.sp) }
            Button(onClick = { onRun("dns_cmp") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Сравн.DNS", fontSize = 9.5.sp) }
            Button(onClick = { onRun("dpi") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("DPI/TLS", fontSize = 9.5.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onRun("http") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("HTTP", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { onRun("tcpdump") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Tcpdump", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { onRun("ports") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Порты", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { onRun("traffic") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Трафик", fontSize = 9.5.sp) }
        }
        if (out.isNotBlank()) MonoBox(out, onCopy = onCopy)
    }
}

@Composable
private fun LogsSshPage(
    logSrc: String,
    logsOut: String,
    logFilter: String,
    onLogFilterChange: (String) -> Unit,
    customCmd: String,
    onCustomCmdChange: (String) -> Unit,
    consoleOut: String,
    onTopicHelp: (String) -> Unit,
    onSelectLog: (String) -> Unit,
    onRunCmd: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    MiniCard("Логи служб роутера", { onTopicHelp("logs") }) {
        val logBtns = listOf("agh" to "AdGuard", "ndms" to "NDMS", "sched" to "Планир.", "au" to "Автообн.", "tg" to "TG", "http" to "HTTP", "warp" to "WARP", "circ" to "Autocirc", "dmesg" to "dmesg")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            logBtns.forEach { (k, lbl) -> FilterChip(selected = logSrc == k, onClick = { onSelectLog(k) }, label = { Text(lbl, fontSize = 10.sp) }) }
        }
        OutlinedTextField(value = logFilter, onValueChange = onLogFilterChange, placeholder = { Text("Фильтр строк (напр. error)", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.fillMaxWidth())
        val shownLog = if (logFilter.isBlank()) logsOut else logsOut.lines().filter { it.contains(logFilter, ignoreCase = true) }.joinToString("\n").ifBlank { "(нет совпадений)" }
        if (shownLog.isNotBlank()) MonoBox(shownLog, onCopy = { onCopy(shownLog) })
    }
    MiniCard("SSH-терминал") {
        val presets = listOf("free -m", "df -h", "netstat -lnt", "ps | grep nfqws", "top -bn1 | head -n 20", "LD_LIBRARY_PATH= /bin/ndmc -c 'show system'")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { p -> AssistChip(onClick = { onCustomCmdChange(p); onRunCmd(p) }, label = { Text(p, fontSize = 9.sp) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = customCmd, onValueChange = onCustomCmdChange, placeholder = { Text("команда sh...", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            Button(onClick = { if (customCmd.isNotBlank()) onRunCmd(customCmd) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Пуск", fontSize = 11.sp) }
        }
        if (consoleOut.isNotBlank()) MonoBox(consoleOut, onCopy = { onCopy(consoleOut) })
    }
}
