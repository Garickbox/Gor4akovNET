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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private enum class MonitorGroup(val title: String) {
    SYSTEM("СИСТЕМА"),
    NDMS("НАСТРОЙКИ NDMS"),
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
    STORAGE(MonitorGroup.SYSTEM, "USB & Накопители", Icons.Rounded.Storage, "storage"),
    APP_CENTER(MonitorGroup.SYSTEM, "Центр пакетов", Icons.Rounded.Apps, "app_center"),
    NDMS_IFACES(MonitorGroup.NDMS, "Интерфейсы & VPN", Icons.Rounded.Router, "ndms_ifaces"),
    NDMS_POLICIES(MonitorGroup.NDMS, "Политики & Сеть", Icons.Rounded.AccountTree, "ndms_policies"),
    NDMS_LAN_SYS(MonitorGroup.NDMS, "LAN, DHCP & Vault", Icons.Rounded.Tune, "ndms_lan_sys"),
    WAN(MonitorGroup.NET, "WAN & Интернет", Icons.Rounded.Public, "wan"),
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
    LOGS_SSH(MonitorGroup.TOOLS, "Логи, Сводка & SSH", Icons.Rounded.Terminal, "logs")
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

// [v8-modularized] parseJsonOut, summarizeRouterError and decodeB64 live in MonitorComponents.kt

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
    var activeLoadJob by remember { mutableStateOf<Job?>(null) }
    var statusMsg by remember { mutableStateOf("") }
    var pageErrMsg by remember { mutableStateOf("") }
    var actionErrMsg by remember { mutableStateOf("") }

    var helpTopic by remember { mutableStateOf<MonitorHelpTopic?>(null) }
    var svcModalKey by remember { mutableStateOf<String?>(null) }
    var confirmStopDropbear by remember { mutableStateOf<Pair<String, String>?>(null) }

    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var services by remember { mutableStateOf<JSONObject?>(null) }
    var storageDoctor by remember { mutableStateOf<JSONObject?>(null) }
    var appCenter by remember { mutableStateOf<JSONObject?>(null) }
    var appCenterOut by remember { mutableStateOf("") }
    var inspectInitId by remember { mutableStateOf<String?>(null) }
    var inspectData by remember { mutableStateOf<JSONObject?>(null) }
    var ndmsState by remember { mutableStateOf<NdmsControlState?>(null) }
    var ndmsIfaceOut by remember { mutableStateOf("") }
    var ndmsCliOut by remember { mutableStateOf("") }
    var routeDoctor by remember { mutableStateOf<JSONObject?>(null) }
    var vaultEntries by remember { mutableStateOf(ConfigVault.listSnapshots(ctx)) }
    var syslogSummary by remember { mutableStateOf<JSONObject?>(null) }
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

    // Backups
    var backupsList by remember { mutableStateOf(prefs.listBackups()) }
    var backupEst by remember { mutableStateOf("") }
    var includeMediaBackup by remember { mutableStateOf(true) }
    var expandedErrDialog by remember { mutableStateOf("") }
    var restoreEntry by remember { mutableStateOf<BackupEntry?>(null) }
    var backupPreviewText by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmSystemRestore by remember { mutableStateOf<BackupEntry?>(null) }
    var bareRestorePrep by remember { mutableStateOf<BareRestorePrepState?>(null) }

    // SSH dialog
    var showCreds by remember { mutableStateOf(false) }
    var confirmReboot by remember { mutableStateOf(false) }

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
                    val opkgBadge = when (p.getOrNull(4)) {
                        "ok" -> "opkg: OK"
                        "missing_bin" -> "opkg: НЕ УСТАНОВЛЕН"
                        "broken_exec", "not_exec" -> "opkg: ПОВРЕЖДЁН"
                        "missing_conf" -> "opkg: нет opkg.conf"
                        else -> ""
                    }
                    val optMntWarn = if (p.getOrNull(5) == "0") " [ВНИМАНИЕ: /opt НЕ на флешке!]" else ""
                    val tail = if (opkgBadge.isNotBlank()) " • $opkgBadge$optMntWarn" else optMntWarn
                    "Система (/opt + конфиг): ~${p[1]} МБ (до сжатия) • Носители: ${p[2]} МБ (${p[3]})$tail"
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

    fun refreshVault() {
        vaultEntries = ConfigVault.listSnapshots(ctx)
    }

    fun inspectService(id: String) {
        inspectInitId = id
        inspectData = null
        scope.launch {
            runCatching {
                parseJsonOut(runSshCmd(prefs, MonitorDiagScripts.serviceInspectCmd(id), readOnly = true), "Инспектор $id")
            }.onSuccess { inspectData = it }
                .onFailure { actionErrMsg = "Инспектор $id: ${it.message}"; inspectInitId = null }
        }
    }

    // applyNdmsWithVault объявлена ПОСЛЕ loadPage — в Kotlin локальная функция
    // не может вызывать функцию, объявленную ниже (Unresolved reference).

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
        activeLoadJob?.cancel()
        val gen = ++loadGen
        activeLoadJob = scope.launch {
            loading = true
            try {
                when (target) {
                    MonitorPage.OVERVIEW -> if (overview == null || force) {
                        overview = parseJsonOut(runSshCmd(prefs, MonitorScripts.buildOverviewCmd(force), readOnly = true), "Обзор")
                    }
                    MonitorPage.SERVICES -> if (services == null || force) {
                        services = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_SERVICES, readOnly = true), "Службы")
                    }
                    MonitorPage.STORAGE -> {
                        if (services == null || force) {
                            services = parseJsonOut(runSshCmd(prefs, MonitorScripts.CMD_SERVICES, readOnly = true), "Хранилище")
                        }
                        if (storageDoctor == null || force) {
                            storageDoctor = runCatching {
                                parseJsonOut(runSshCmd(prefs, MonitorDiagScripts.CMD_STORAGE_DOCTOR, readOnly = true), "Storage Doctor")
                            }.getOrNull() ?: storageDoctor
                        }
                    }
                    MonitorPage.APP_CENTER -> if (appCenter == null || force) {
                        appCenter = parseJsonOut(runSshCmd(prefs, MonitorDiagScripts.CMD_APP_CENTER_STATE, readOnly = true), "Центр пакетов")
                    }
                    MonitorPage.NDMS_IFACES, MonitorPage.NDMS_POLICIES, MonitorPage.NDMS_LAN_SYS -> {
                        refreshVault()
                        if (ndmsState == null || force) {
                            ndmsState = RouterControlClient.loadState(ctx, prefs).getOrThrow()
                        }
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
                    MonitorPage.LOGS_SSH -> {
                        if (logsOut.isBlank() || force) {
                            logsOut = runSshCmd(prefs, MonitorScripts.fetchLog(logSrc), readOnly = true)
                        }
                        if (syslogSummary == null || force) {
                            syslogSummary = runCatching {
                                parseJsonOut(runSshCmd(prefs, MonitorDiagScripts.CMD_SYSLOG_SUMMARY, readOnly = true), "Syslog")
                            }.getOrNull() ?: syslogSummary
                        }
                    }
                    MonitorPage.FILES, MonitorPage.BACKUPS -> {}
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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

    fun applyNdmsWithVault(reason: String, cmds: List<String>, reloadTarget: MonitorPage = page) {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = "$reason..."
            val res = RouterControlClient.executeNdmsWithVault(ctx, prefs, reason, cmds, saveConfig = true)
            refreshVault()
            res.onSuccess { msg ->
                statusMsg = msg
                ndmsState = RouterControlClient.loadState(ctx, prefs).getOrNull() ?: ndmsState
                if (reloadTarget != MonitorPage.NDMS_IFACES && reloadTarget != MonitorPage.NDMS_POLICIES && reloadTarget != MonitorPage.NDMS_LAN_SYS) {
                    loading = false
                    loadPage(reloadTarget, force = true)
                    return@launch
                }
            }.onFailure { err ->
                actionErrMsg = "$reason: ${err.message}"
                statusMsg = ""
            }
            loading = false
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
                loadPage(if (page == MonitorPage.APP_CENTER) MonitorPage.APP_CENTER else MonitorPage.DNS, force = true)
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
                // Валидный полный бэкап /opt не может быть меньше 1 МБ (Entware ~50+ МБ
                // сжатого). Порог 4 КБ пропускал пустой tar от упавшего busybox.
                if (writtenBytes > 1_000_000L) {
                    val crcRes = withContext(Dispatchers.IO) { prefs.verifyGzipArchive(tempFile) }
                    if (crcRes.isFailure) {
                        tempFile.delete()
                        actionErrMsg = "Архив бэкапа повреждён (gzip CRC fail): ${crcRes.exceptionOrNull()?.message}"
                    } else {
                        val b = prefs.commitStreamedBackupFile(
                            file = tempFile,
                            label = if (includeMediaBackup) "Полный 100% + USB" else "Полный снимок v2",
                            model = model,
                            format = "tar_v2",
                            isProtected = false
                        )
                        onBackupSaved(b)
                        refreshBackups()
                        statusMsg = if (includeMediaBackup) {
                            "Бэкап сохранён: ${formatSizeMb(b.sizeBytes)} (включая все файлы USB и корень /opt)"
                        } else {
                            "Бэкап сохранён: ${formatSizeMb(b.sizeBytes)}"
                        }
                    }
                } else {
                    tempFile.delete()
                    actionErrMsg = "Архив бэкапа оказался пустым ($writtenBytes Б)"
                }
            } catch (e: Throwable) {
                tempFile.delete()
                actionErrMsg = "Ошибка бэкапа: ${summarizeRouterError(e.message)}"
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

    fun executeBackupRestore(entry: BackupEntry, mode: String, skipBareCheck: Boolean = false) {
        scope.launch {
            loading = true
            actionErrMsg = ""
            statusMsg = "Восстановление бэкапа (${entry.label})..."
            try {
                val host = prefs.routerHost.ifBlank { "192.168.1.1" }
                if (entry.filePath.endsWith(".tar.gz", true)) {
                    if (!skipBareCheck) {
                        val sshAlive = withContext(Dispatchers.IO) { Gboxcore.probeTCP(host, prefs.sshPort, 1200) }
                        var optOkNow = false
                        if (sshAlive) {
                            val preProbe = runCatching { runSshCmd(prefs, BackupRestoreScripts.OPT_PREFLIGHT_CMD, readOnly = true) }.getOrDefault("")
                            val pp = preProbe.lines().firstOrNull { it.startsWith("PREFLIGHT|") }?.split("|").orEmpty()
                            optOkNow = pp.size >= 2 && pp[1] == "1"
                        }
                        if (!sshAlive || !optOkNow) {
                            statusMsg = "Проверяю готовность роутера (RCI)..."
                            val p = RciClient.probePassport(host, prefs.rciUser, prefs.rciPass).getOrElse {
                                error("SSH :${prefs.sshPort} недоступен (или /opt не смонтирован), а RCI (:80) не отвечает: ${it.message}")
                            }
                            if (!p.hasOpkgComp) {
                                error("В прошивке роутера отсутствует компонент «opkg». Установите его в настройках роутера и повторите откат.")
                            }
                            val usbMedia = p.mediaList.filter { it.isUsb }
                            if (usbMedia.isEmpty()) {
                                error("На роутере не запущен Entware (/opt) и не найден USB-накопитель. Подключите флешку и повторите откат.")
                            }
                            val target = usbMedia.firstOrNull { it.isExt4 || it.usedBy.contains("opkg") } ?: usbMedia.first()
                            if (target.isExt4 || target.usedBy.contains("opkg")) {
                                prepareBareRouterForRestore(ctx, host, prefs, p, target, formatToExt4First = false) { msg ->
                                    statusMsg = msg
                                }
                            } else {
                                bareRestorePrep = BareRestorePrepState(entry, mode, p, target)
                                statusMsg = "Требуется подтверждение подготовки флешки (${target.name})"
                                return@launch
                            }
                        }
                    }
                    // Защита ОЗУ роутера: ДО заливки проверяем что /opt примонтирован на USB (но РАЗРЕШАЕМ откат даже если opkg удалён/сломан!)
                    val preRaw = runSshCmd(prefs, BackupRestoreScripts.OPT_PREFLIGHT_CMD, readOnly = true)
                    val preParts = preRaw.lines().firstOrNull { it.startsWith("PREFLIGHT|") }?.split("|").orEmpty()
                    if (preParts.size >= 4) {
                        val optMounted = preParts[1] == "1"
                        val optFs = preParts[2]
                        val freeKb = preParts[3].toLongOrNull() ?: 0L
                        val needKb = (File(entry.filePath).length() / 1024L) + 16_384L
                        if (!optMounted) {
                            error("Раздел /opt не смонтирован на флешке (fs=$optFs). Смонтируйте USB /opt во избежание переполнения ОЗУ роутера.")
                        }
                        if (freeKb in 1 until needKb) {
                            error("На /opt недостаточно места для отката: свободно ${freeKb / 1024} МБ, нужно ~${needKb / 1024} МБ.")
                        }
                    }
                    withContext(Dispatchers.IO) {
                        Gboxcore.pushFileSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, entry.filePath, "/opt/.gbox_restore.tar.gz")
                    }
                    val out = runSshCmd(prefs, GboxScripts.restoreArchiveCmd(mode), readOnly = false)
                    if (out.contains("PREFLIGHT_FAIL") || out.contains("[FATAL]") || out.contains("FATAL:")) {
                        error(summarizeRouterError(out))
                    }
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
                Text(
                    activeErr,
                    color = Color(0xFFFDE68A),
                    fontSize = 10.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { expandedErrDialog = activeErr }
                )
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
                        onReboot = { confirmReboot = true },
                        onSvcToggle = { svc, on ->
                            scope.launch {
                                loading = true
                                actionErrMsg = ""
                                statusMsg = if (on) "Включаю ${svc.uppercase()}..." else "Отключаю ${svc.uppercase()}..."
                                val r = withContext(Dispatchers.IO) {
                                    RciClient.toggleService(prefs.routerHost, prefs.rciUser, prefs.rciPass, svc, on)
                                }
                                r.fold(
                                    onSuccess = { statusMsg = "$svc: $it" },
                                    onFailure = {
                                        actionErrMsg = "$svc: ${it.message}"
                                        statusMsg = ""
                                    }
                                )
                                loading = false
                                delay(500)
                                loadPage(MonitorPage.OVERVIEW, force = true)
                            }
                        }
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
                        onInspect = { id -> inspectService(id) },
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
                        doctor = storageDoctor,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onToggleHelp = ::showToggleHelp,
                        onRefreshDoctor = {
                            runAction("Диагностика FS", MonitorDiagScripts.CMD_STORAGE_DOCTOR, readOnly = true) { out ->
                                runCatching { parseJsonOut(out, "Storage Doctor") }.onSuccess { storageDoctor = it }
                            }
                        },
                        onCleanupStorage = {
                            runAction("Очистка логов /opt", MonitorDiagScripts.CMD_STORAGE_CLEANUP, MonitorPage.STORAGE, 400L) { out ->
                                statusMsg = out.lines().lastOrNull { it.isNotBlank() } ?: statusMsg
                            }
                        },
                        onAction = { lbl, cmd -> runAction(lbl, cmd, MonitorPage.STORAGE, 500L) }
                    )
                    MonitorPage.APP_CENTER -> AppCenterPage(
                        data = appCenter,
                        actionOut = appCenterOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onInstallAdguard = { installOrBootstrapAdguard() },
                        onAction = { lbl, cmd ->
                            runAction(lbl, cmd, MonitorPage.APP_CENTER, 700L) { out ->
                                appCenterOut = out
                            }
                        },
                        onCopy = { txt -> clipboard.setText(AnnotatedString(txt)); statusMsg = "Скопировано" }
                    )
                    MonitorPage.NDMS_IFACES -> NdmsIfacesPage(
                        state = ndmsState,
                        inspectOut = ndmsIfaceOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onReload = { loadPage(MonitorPage.NDMS_IFACES, true) },
                        onApplyCmds = { reason, cmds -> applyNdmsWithVault(reason, cmds) },
                        onInspectIface = { ifcId ->
                            scope.launch {
                                loading = true
                                RouterControlClient.runNdmsCli(prefs, "show interface $ifcId")
                                    .onSuccess { ndmsIfaceOut = it }
                                    .onFailure { actionErrMsg = it.message ?: "Ошибка" }
                                loading = false
                            }
                        },
                        onCopy = { txt -> clipboard.setText(AnnotatedString(txt)); statusMsg = "Скопировано" }
                    )
                    MonitorPage.NDMS_POLICIES -> NdmsPoliciesPage(
                        state = ndmsState,
                        routeDoctor = routeDoctor,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onRunDoctor = {
                            runAction("Доктор маршрутов", MonitorDiagScripts.CMD_ROUTE_DOCTOR, readOnly = true) { out ->
                                runCatching { parseJsonOut(out, "Route Doctor") }.onSuccess { routeDoctor = it }
                            }
                        },
                        onApplyCmds = { reason, cmds -> applyNdmsWithVault(reason, cmds) }
                    )
                    MonitorPage.NDMS_LAN_SYS -> NdmsLanSysPage(
                        state = ndmsState,
                        vaultEntries = vaultEntries,
                        cliOut = ndmsCliOut,
                        busy = loading,
                        onTopicHelp = ::showTopic,
                        onApplyCmds = { reason, cmds -> applyNdmsWithVault(reason, cmds) },
                        onRunCli = { cmdLine ->
                            scope.launch {
                                loading = true
                                actionErrMsg = ""
                                RouterControlClient.runNdmsCli(prefs, cmdLine)
                                    .onSuccess { ndmsCliOut = it }
                                    .onFailure { actionErrMsg = it.message ?: "Ошибка NDM CLI" }
                                loading = false
                            }
                        },
                        onTakeVault = { lbl ->
                            scope.launch {
                                loading = true
                                RouterControlClient.takeManualVaultSnapshot(ctx, prefs, lbl)
                                    .onSuccess {
                                        refreshVault()
                                        statusMsg = "Снимок Config Vault сохранён"
                                    }.onFailure { actionErrMsg = "Vault: ${it.message}" }
                                loading = false
                            }
                        },
                        onRestoreVault = { entry ->
                            scope.launch {
                                loading = true
                                RouterControlClient.restoreVaultSnapshot(ctx, prefs, entry)
                                    .onSuccess { msg ->
                                        refreshVault()
                                        statusMsg = msg
                                        ndmsState = RouterControlClient.loadState(ctx, prefs).getOrNull() ?: ndmsState
                                    }.onFailure { actionErrMsg = "Откат Vault: ${it.message}" }
                                loading = false
                            }
                        },
                        onDeleteVault = { entry ->
                            ConfigVault.deleteSnapshot(entry)
                            refreshVault()
                        },
                        onFormatUsbExt4 = { dev ->
                            runAction("Format $dev -> ext4", MonitorDiagScripts.usbFormatExt4Cmd(dev), MonitorPage.NDMS_LAN_SYS, 1000L) { out ->
                                ndmsCliOut = out
                            }
                        },
                        onCopy = { txt -> clipboard.setText(AnnotatedString(txt)); statusMsg = "Скопировано" }
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
                        onNewDir = { newEntryMode = "dir" },
                        onNewFile = { newEntryMode = "file" },
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
                        syslogSummary = syslogSummary,
                        busy = loading,
                        logFilter = logFilter,
                        onLogFilterChange = { logFilter = it },
                        customCmd = customCmd,
                        onCustomCmdChange = { customCmd = it },
                        consoleOut = consoleOut,
                        onTopicHelp = ::showTopic,
                        onRefreshSummary = {
                            runAction("Анализ Syslog", MonitorDiagScripts.CMD_SYSLOG_SUMMARY, readOnly = true) { out ->
                                runCatching { parseJsonOut(out, "Syslog") }.onSuccess { syslogSummary = it }
                            }
                        },
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

    helpTopic?.let { tp ->
        MonitorHelpTopicDialog(tp) { helpTopic = null }
    }

    inspectInitId?.let { id ->
        ServiceInspectorDialog(
            initId = id,
            data = inspectData,
            busy = loading,
            onDismiss = { inspectInitId = null; inspectData = null },
            onSvcAct = { svcId, act ->
                inspectInitId = null
                inspectData = null
                if (svcId.contains("dropbear", true) && (act == "stop" || act == "restart")) {
                    confirmStopDropbear = svcId to act
                } else {
                    runAction("$act $svcId", MonitorScripts.svcCmd(svcId, act), MonitorPage.SERVICES, 700L)
                }
            },
            onOpenConfig = { cfgPath ->
                inspectInitId = null
                inspectData = null
                openFile(cfgPath)
            }
        )
    }

    confirmStopDropbear?.let { (id, act) ->
        MonitorStopDropbearDialog(
            id = id,
            act = act,
            onDismiss = { confirmStopDropbear = null },
            onConfirm = {
                confirmStopDropbear = null
                runAction("$act $id", MonitorScripts.svcCmd(id, act), MonitorPage.SERVICES, 700L)
            }
        )
    }

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

    svcModalKey?.let { key ->
        MonitorSvcModalDialog(
            key = key,
            overview = overview,
            warpFull = warpFull,
            onDismiss = { svcModalKey = null },
            onWarpAction = { lbl, cmd -> svcModalKey = null; runAction(lbl, cmd, MonitorPage.WARP) },
            onOpenWarp = { svcModalKey = null; page = MonitorPage.WARP; loadPage(MonitorPage.WARP, true) },
            onTgTunnelAction = { lbl, cmd -> svcModalKey = null; runAction(lbl, cmd, MonitorPage.OVERVIEW) },
            onInitSvcAction = { lbl, cmd -> svcModalKey = null; runAction(lbl, cmd, MonitorPage.OVERVIEW, delayMs = 700L) }
        )
    }

    restoreEntry?.let { entry ->
        RestoreModeDialog(
            entry = entry,
            onDismiss = { restoreEntry = null },
            onExecuteRestore = { e, mode -> restoreEntry = null; executeBackupRestore(e, mode) },
            onRequestSystemConfirm = { e -> restoreEntry = null; confirmSystemRestore = e }
        )
    }

    bareRestorePrep?.let { prep ->
        BareRestoreFormatDialog(
            state = prep,
            busy = loading,
            onDismiss = { bareRestorePrep = null },
            onConfirmFormatAndRestore = { confirmed ->
                bareRestorePrep = null
                scope.launch {
                    loading = true
                    actionErrMsg = ""
                    try {
                        val host = prefs.routerHost.ifBlank { "192.168.1.1" }
                        prepareBareRouterForRestore(ctx, host, prefs, confirmed.passport, confirmed.media, formatToExt4First = true) { msg ->
                            statusMsg = msg
                        }
                        executeBackupRestore(confirmed.entry, confirmed.mode, skipBareCheck = true)
                    } catch (e: Throwable) {
                        actionErrMsg = "Ошибка подготовки к откату: ${e.message}"
                    } finally {
                        loading = false
                    }
                }
            }
        )
    }

    if (expandedErrDialog.isNotBlank()) {
        MonitorExpandedErrDialog(
            errorText = expandedErrDialog,
            onCopy = { txt -> clipboard.setText(AnnotatedString(txt)); expandedErrDialog = "" },
            onDismiss = { expandedErrDialog = "" }
        )
    }

    confirmSystemRestore?.let { entry ->
        MonitorConfirmSystemRestoreDialog(
            entry = entry,
            onDismiss = { confirmSystemRestore = null },
            onConfirm = { confirmed ->
                confirmSystemRestore = null
                executeBackupRestore(confirmed, "system_full")
            }
        )
    }

    backupPreviewText?.let { (lbl, txt) ->
        MonitorBackupPreviewDialog(lbl, txt) { backupPreviewText = null }
    }

    if (fileModalOpen) {
        MonitorFileEditorDialog(
            path = fileModalPath,
            initialText = fileModalText,
            truncated = fileModalTrunc,
            onDismiss = { fileModalOpen = false },
            onRestoreBak = {
                fileModalOpen = false
                runAction("Откат .bak", MonitorScripts.restoreBakFileCmd(fileModalPath.removeSuffix(".bak")))
            },
            onSave = { b64 ->
                fileModalOpen = false
                runAction("Сохранение файла", MonitorScripts.writeFileCmd(fileModalPath, b64))
            }
        )
    }

    newEntryMode?.let { mode ->
        MonitorNewEntryDialog(
            mode = mode,
            dirPath = dirPath,
            onDismiss = { newEntryMode = null },
            onCreate = { nm ->
                newEntryMode = null
                if (nm.isNotBlank()) {
                    val full = if (dirPath == "/") "/$nm" else "$dirPath/$nm"
                    val cmd = if (mode == "dir") MonitorScripts.mkdirCmd(full) else MonitorScripts.touchFileCmd(full)
                    runAction(if (mode == "dir") "Создать папку" else "Создать файл", cmd) { openDir(dirPath) }
                }
            }
        )
    }

    if (showCreds) {
        MonitorSshCredsDialog(
            prefs = prefs,
            onDismiss = { showCreds = false },
            onStatus = { statusMsg = it },
            onError = { actionErrMsg = it },
            onSaved = { showCreds = false; loadPage(page, true) }
        )
    }

    if (confirmReboot) {
        MonitorConfirmRebootDialog(
            onDismiss = { confirmReboot = false },
            onConfirm = {
                confirmReboot = false
                runAction("Перезагрузка роутера", "LD_LIBRARY_PATH= /bin/ndmc -c 'system reboot'")
            }
        )
    }
}

// Shared Monitor UI helpers moved to MonitorComponents.kt

// OverviewPage, ServicesPage moved to MonitorOverviewPage.kt
// StoragePage, WanPage, DnsPage, NatPpePage moved to MonitorNetPages.kt
// BypassModesPage, WarpPage, BypassListsPage moved to MonitorBypassPages.kt
// WifiPage, DevicesPage, DeviceManageDialog moved to MonitorDeviceDialog.kt
// BackupsPage, FilesPage, TorchPage, LogsSshPage, RestoreModeDialog moved to MonitorBackupsPage.kt
