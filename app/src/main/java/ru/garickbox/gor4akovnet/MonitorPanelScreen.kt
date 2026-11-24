package ru.garickbox.gor4akovnet

import android.content.Context
import android.util.Base64
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import gboxcore.Gboxcore

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
    WAN_TS(MonitorGroup.NET, "WAN & Tailscale", Icons.Rounded.Public, "wan_ts"),
    DNS(MonitorGroup.NET, "AdGuard DNS", Icons.Rounded.Dns, "dns"),
    NAT_PPE(MonitorGroup.NET, "PPE & Порты", Icons.Rounded.Security, "nat_ppe"),
    Z2K_MODES(MonitorGroup.BYPASS, "Zapret2 (z2k)", Icons.Rounded.Shield, "z2k"),
    WARP(MonitorGroup.BYPASS, "WARP & Игры", Icons.Rounded.Public, "warp"),
    Z2K_LISTS(MonitorGroup.BYPASS, "Списки & Подбор", Icons.Rounded.List, "z2k"),
    WIFI(MonitorGroup.WIFI, "Wi-Fi 2.4/5G", Icons.Rounded.Wifi, "wifi"),
    PS5(MonitorGroup.WIFI, "PS5 & WoT", Icons.Rounded.SportsEsports, "ps5"),
    DEVICES(MonitorGroup.WIFI, "Клиенты", Icons.Rounded.Devices, "devices"),
    FILES(MonitorGroup.TOOLS, "X-plore Файлы", Icons.Rounded.Folder, "files"),
    TORCH(MonitorGroup.TOOLS, "Torch & Трафик", Icons.Rounded.NetworkCheck, "torch"),
    LOGS_SSH(MonitorGroup.TOOLS, "Логи & SSH", Icons.Rounded.Terminal, "logs")
}

private fun sshTarget(ctx: Context): Pair<String, Int> {
    return Pair(Prefs.sshHost(ctx), Prefs.sshPort(ctx))
}

private suspend fun runSshRetry(ctx: Context, cmd: String): String {
    val (host, port) = sshTarget(ctx)
    return withContext(Dispatchers.IO) {
        try {
            val first = Gboxcore.runRouterSSH(host, port, Prefs.sshUser(ctx), Prefs.sshPass(ctx), cmd)
            if (first.isNotBlank()) return@withContext first
            Thread.sleep(250)
            Gboxcore.runRouterSSH(host, port, Prefs.sshUser(ctx), Prefs.sshPass(ctx), cmd)
        } catch (e: Throwable) {
            val msg = e.message.orEmpty()
            if (msg.contains("EOF", true) || msg.contains("closed", true)) {
                Thread.sleep(250)
                Gboxcore.runRouterSSH(host, port, Prefs.sshUser(ctx), Prefs.sshPass(ctx), cmd)
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
    return JSONObject(raw.substring(i))
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

private fun decodeB64(b64: String): String {
    if (b64.isBlank()) return ""
    return runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault("")
}

@Composable
fun MonitorPanelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var page by remember { mutableStateOf(MonitorPage.OVERVIEW) }
    var sidebarOpen by remember { mutableStateOf(true) }
    var autoCollapse by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var loadGen by remember { mutableIntStateOf(0) }
    var statusMsg by remember { mutableStateOf("") }
    var errMsg by remember { mutableStateOf("") }

    var helpTopic by remember { mutableStateOf<HelpTopic?>(null) }
    var svcModalKey by remember { mutableStateOf<String?>(null) }

    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var services by remember { mutableStateOf<JSONObject?>(null) }
    var wanTs by remember { mutableStateOf<JSONObject?>(null) }
    var dnsNet by remember { mutableStateOf<JSONObject?>(null) }
    var bypass by remember { mutableStateOf<JSONObject?>(null) }
    var warpGames by remember { mutableStateOf<JSONObject?>(null) }
    var wifiDevs by remember { mutableStateOf<JSONObject?>(null) }

    var dirPath by remember { mutableStateOf("/opt/zapret2") }
    var dirData by remember { mutableStateOf<JSONObject?>(null) }
    var fileModalPath by remember { mutableStateOf("") }
    var fileModalText by remember { mutableStateOf("") }
    var fileModalTrunc by remember { mutableStateOf(false) }
    var fileModalOpen by remember { mutableStateOf(false) }
    var newEntryMode by remember { mutableStateOf<String?>(null) }
    var newEntryName by remember { mutableStateOf("") }

    var diagOut by remember { mutableStateOf("") }
    var opkgOut by remember { mutableStateOf("") }
    var wotOut by remember { mutableStateOf("") }
    var tsPingOut by remember { mutableStateOf("") }
    var dnsProbeOut by remember { mutableStateOf("") }
    var siteProbeOut by remember { mutableStateOf("") }
    var pickOut by remember { mutableStateOf("") }
    var torchHost by remember { mutableStateOf("1.1.1.1") }
    var torchOut by remember { mutableStateOf("") }
    var logsOut by remember { mutableStateOf("") }
    var logSrc by remember { mutableStateOf("agh") }
    var logFilter by remember { mutableStateOf("") }
    var consoleOut by remember { mutableStateOf("") }
    var customCmd by remember { mutableStateOf("") }
    var domainInput by remember { mutableStateOf("") }
    var wlInput by remember { mutableStateOf("") }
    var devSearch by remember { mutableStateOf("") }
    var onlineOnly by remember { mutableStateOf(false) }

    var showCreds by remember { mutableStateOf(false) }
    var sshHost by remember { mutableStateOf(Prefs.sshHost(ctx)) }
    var sshPort by remember { mutableStateOf(Prefs.sshPort(ctx).toString()) }
    var sshUser by remember { mutableStateOf(Prefs.sshUser(ctx)) }
    var sshPass by remember { mutableStateOf(Prefs.sshPass(ctx)) }
    var showPass by remember { mutableStateOf(false) }
    var confirmReboot by remember { mutableStateOf(false) }

    val (curHost, curPort) = sshTarget(ctx)
    val routeBadge = "LAN • $curHost:$curPort"

    fun showTopic(key: String) {
        helpTopic = MonitorHelp.topics[key]
    }

    fun showQuickHelp(t: String, what: String, why: String, rec: String, warn: String = "") {
        helpTopic = HelpTopic(t, what, why, rec, warn)
    }

    fun loadPage(target: MonitorPage, force: Boolean = true) {
        val gen = ++loadGen
        loading = true
        errMsg = ""
        scope.launch {
            runCatching {
                when (target) {
                    MonitorPage.OVERVIEW ->
                        if (force || overview == null) overview = parseJsonOut(runSshRetry(ctx, MonitorScripts.buildOverviewCmd(force)), "Обзор")
                    MonitorPage.SERVICES, MonitorPage.STORAGE ->
                        if (force || services == null) services = parseJsonOut(runSshRetry(ctx, MonitorScripts.CMD_SERVICES), "Службы")
                    MonitorPage.WAN_TS ->
                        if (force || wanTs == null) wanTs = parseJsonOut(runSshRetry(ctx, MonitorScripts.CMD_WAN_TAILSCALE), "WAN/TS")
                    MonitorPage.DNS, MonitorPage.NAT_PPE -> {
                        if (force || dnsNet == null) dnsNet = parseJsonOut(runSshRetry(ctx, MonitorScripts.CMD_DNS_NET), "DNS/NAT")
                        if (target == MonitorPage.NAT_PPE && wifiDevs == null) {
                            wifiDevs = parseJsonOut(runSshRetry(ctx, MonitorScripts.CMD_WIFI_PS5_DEVICES), "Клиенты")
                        }
                    }
                    MonitorPage.Z2K_MODES, MonitorPage.Z2K_LISTS ->
                        if (force || bypass == null) bypass = parseJsonOut(runSshRetry(ctx, MonitorScripts.CMD_BYPASS_STATE), "z2k")
                    MonitorPage.WARP -> {
                        if (force || bypass == null) bypass = parseJsonOut(runSshRetry(ctx, MonitorScripts.CMD_BYPASS_STATE), "WARP")
                        if (force || warpGames == null) warpGames = parseJsonOut(runSshRetry(ctx, MonitorScripts.CMD_WARP_GAMES), "WARP игры")
                    }
                    MonitorPage.WIFI, MonitorPage.PS5, MonitorPage.DEVICES ->
                        if (force || wifiDevs == null) wifiDevs = parseJsonOut(runSshRetry(ctx, MonitorScripts.CMD_WIFI_PS5_DEVICES), "WiFi/PS5")
                    MonitorPage.FILES ->
                        if (force || dirData == null) dirData = parseJsonOut(runSshRetry(ctx, MonitorScripts.listDirCmd(dirPath)), "Файлы")
                    MonitorPage.LOGS_SSH ->
                        if (force || logsOut.isBlank()) logsOut = runSshRetry(ctx, MonitorScripts.fetchLog(logSrc))
                    MonitorPage.TORCH -> {}
                }
            }.onFailure { e ->
                if (gen == loadGen) errMsg = e.message ?: "Ошибка SSH"
            }
            if (gen == loadGen) loading = false
        }
    }

    fun openDir(path: String) {
        dirPath = path
        val gen = ++loadGen
        loading = true
        errMsg = ""
        scope.launch {
            runCatching {
                val res = parseJsonOut(runSshRetry(ctx, MonitorScripts.listDirCmd(path)), "Файлы")
                dirData = res
                res.optString("path").takeIf { it.isNotBlank() }?.let { dirPath = it }
            }.onFailure { e -> if (gen == loadGen) errMsg = e.message ?: "Ошибка папки" }
            if (gen == loadGen) loading = false
        }
    }

    fun openFile(fullPath: String) {
        val gen = ++loadGen
        loading = true
        errMsg = ""
        scope.launch {
            runCatching {
                val obj = parseJsonOut(runSshRetry(ctx, MonitorScripts.readFileCmd(fullPath)), "Файл")
                val err = obj.optString("err")
                if (err.isNotBlank()) throw IllegalStateException(err)
                fileModalPath = fullPath
                fileModalText = decodeB64(obj.optString("b64"))
                fileModalTrunc = obj.optBoolean("trunc", false)
                fileModalOpen = true
            }.onFailure { e -> if (gen == loadGen) errMsg = e.message ?: "Ошибка чтения" }
            if (gen == loadGen) loading = false
        }
    }

    fun runAction(
        title: String,
        cmd: String,
        refreshPage: MonitorPage? = null,
        syncRulesAfter: Boolean = false,
        delayMs: Long = 0L,
        onOutput: ((String) -> Unit)? = null
    ) {
        val gen = ++loadGen
        loading = true
        errMsg = ""
        statusMsg = "$title..."
        scope.launch {
            runCatching {
                val out = runSshRetry(ctx, cmd)
                if (syncRulesAfter) { /* tailscale removed */ }
                if (onOutput != null) onOutput(out.ifBlank { "OK (пустой вывод, rc=0)" })
                else if (out.isNotBlank()) consoleOut = out
                statusMsg = "$title — готово"
                if (delayMs > 0L) delay(delayMs)
                if (refreshPage != null) loadPage(refreshPage, true)
            }.onFailure { e ->
                if (gen == loadGen) {
                    errMsg = e.message ?: "Ошибка выполнения"
                    statusMsg = ""
                }
            }
            if (gen == loadGen && refreshPage == null) loading = false
        }
    }

    BackHandler {
        when {
            svcModalKey != null -> svcModalKey = null
            helpTopic != null -> helpTopic = null
            fileModalOpen -> fileModalOpen = false
            newEntryMode != null -> newEntryMode = null
            !sidebarOpen -> sidebarOpen = true
            else -> onBack()
        }
    }

    LaunchedEffect(Unit) { loadPage(MonitorPage.OVERVIEW, false) }
    LaunchedEffect(statusMsg) { if (statusMsg.isNotBlank()) { delay(3500); statusMsg = "" } }

    val m1070Warn = (overview?.optJSONObject("sys")?.optInt("m1070", 0) == 1) ||
        (wifiDevs?.optJSONObject("wifi")?.optBoolean("m1070", false) == true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090E1A))
            .systemBarsPadding()
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
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (errMsg.isBlank()) Color(0xFF22C55E) else Color(0xFFF59E0B)))
                        Text(routeBadge, color = Color(0xFF94A3B8), fontSize = 9.sp)
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "В YaDoma", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF3B82F6))
        if (statusMsg.isNotBlank()) {
            Text(statusMsg, color = Color(0xFF86EFAC), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF052E16)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (errMsg.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF451A03)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(errMsg, color = Color(0xFFFDE68A), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { loadPage(page, true) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                        Text("Повтор", fontSize = 10.sp, color = Color(0xFF93C5FD))
                    }
                    TextButton(onClick = { showCreds = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                        Text("SSH", fontSize = 10.sp, color = Color.White)
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
                            Text("WinBox Меню", color = Color(0xFF93C5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                                val warnDot = (item == MonitorPage.WIFI || item == MonitorPage.OVERVIEW) && m1070Warn
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
                                        Text(item.label, color = if (sel) Color.White else Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
                        data = overview,
                        diagOut = diagOut,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onServiceTap = { svcModalKey = it },
                        onCheckUpdates = { loadPage(MonitorPage.OVERVIEW, true) },
                        onUpdateZ2k = { runAction("Обновление Zapret2", MonitorScripts.z2kUpdateApplyCmd(), MonitorPage.OVERVIEW, syncRulesAfter = true) },
                        onKill1070 = {
                            runAction(
                                "Отключить Netcraze-1070",
                                MonitorScripts.ndmcSaveCmd("no mws wlan Home enable", "interface WifiMaster0/AccessPoint2 down", "interface WifiMaster1/AccessPoint2 down"),
                                MonitorPage.OVERVIEW
                            )
                        },
                        onBenchWan = { runAction("Замер WAN", MonitorScripts.CMD_BENCH_WAN, onOutput = { diagOut = it }) }
                    )
                    MonitorPage.SERVICES -> ServicesPage(
                        data = services,
                        opkgOut = opkgOut,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onSvcAction = { id, act -> runAction("$id ($act)", MonitorScripts.svcCmd(id, act), MonitorPage.SERVICES, delayMs = 700L) },
                        onOpkgRun = { title, cmd -> runAction(title, cmd, MonitorPage.SERVICES, onOutput = { opkgOut = it }) }
                    )
                    MonitorPage.STORAGE -> StoragePage(
                        data = services,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { title, cmd -> runAction(title, cmd, MonitorPage.STORAGE, delayMs = 500L) },
                        onFix8090 = { runAction("Починить :8090", "/opt/etc/init.d/S98giga-panel restart 2>&1") },
                        onReboot = { confirmReboot = true }
                    )
                    MonitorPage.WAN_TS -> WanTsPage(
                        data = wanTs,
                        tsPingOut = tsPingOut,
                        diagOut = diagOut,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onTsPing = { ip -> runAction("TS Ping $ip", MonitorScripts.tailscalePingCmd(ip), onOutput = { tsPingOut = it }) },
                        onBench = { runAction("Замер WAN", MonitorScripts.CMD_BENCH_WAN, onOutput = { diagOut = it }) },
                        onAction = { t, cmd -> runAction(t, cmd, MonitorPage.WAN_TS, delayMs = 600L) }
                    )
                    MonitorPage.DNS -> DnsPage(
                        data = dnsNet,
                        probeOut = dnsProbeOut,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { t, cmd -> runAction(t, cmd, MonitorPage.DNS, delayMs = 600L) },
                        onProbeDns = { runAction("Тест DNS", MonitorScripts.CMD_PROBE_DNS, onOutput = { dnsProbeOut = it }) }
                    )
                    MonitorPage.NAT_PPE -> NatPpePage(
                        data = dnsNet,
                        devicesData = wifiDevs,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { t, cmd -> runAction(t, cmd, MonitorPage.NAT_PPE, delayMs = 500L) }
                    )
                    MonitorPage.Z2K_MODES -> BypassModesPage(
                        data = bypass,
                        probeOut = siteProbeOut,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onToggle = { lbl, ep, v -> runAction(lbl, MonitorScripts.toggleZ2k(ep, v), MonitorPage.Z2K_MODES, syncRulesAfter = true) },
                        onAction = { t, cmd, sync -> runAction(t, cmd, MonitorPage.Z2K_MODES, syncRulesAfter = sync, delayMs = 400L) },
                        onProbe = { runAction("Проверка сайтов", MonitorScripts.CMD_PROBE_SITES, onOutput = { siteProbeOut = it }) }
                    )
                    MonitorPage.WARP -> WarpPage(
                        bypass = bypass,
                        gamesData = warpGames,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { t, cmd -> runAction(t, cmd, MonitorPage.WARP, delayMs = 400L) }
                    )
                    MonitorPage.Z2K_LISTS -> BypassListsPage(
                        data = bypass,
                        domainInput = domainInput,
                        onDomainChange = { domainInput = it },
                        wlInput = wlInput,
                        onWlChange = { wlInput = it },
                        pickOut = pickOut,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { t, cmd, sync -> runAction(t, cmd, MonitorPage.Z2K_LISTS, syncRulesAfter = sync) },
                        onPick = { d, m -> runAction("Подбор для $d", MonitorScripts.strategyPickCmd(d, m), onOutput = { pickOut = it }) }
                    )
                    MonitorPage.WIFI -> WifiPage(
                        data = wifiDevs,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { t, cmd -> runAction(t, cmd, MonitorPage.WIFI, delayMs = 900L) }
                    )
                    MonitorPage.PS5 -> Ps5Page(
                        data = wifiDevs,
                        wotOut = wotOut,
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { t, cmd -> runAction(t, cmd, MonitorPage.PS5, delayMs = 500L) },
                        onWotPing = { runAction("Тест WoT & PS5", MonitorScripts.CMD_WOT_PING, onOutput = { wotOut = it }) }
                    )
                    MonitorPage.DEVICES -> DevicesPage(
                        data = wifiDevs,
                        search = devSearch,
                        onSearchChange = { devSearch = it },
                        onlineOnly = onlineOnly,
                        onOnlineOnlyChange = { onlineOnly = it },
                        onTopicHelp = ::showTopic,
                        onQuickHelp = ::showQuickHelp,
                        onAction = { t, cmd -> runAction(t, cmd, MonitorPage.DEVICES, delayMs = 500L) },
                        onPing = { t, ip -> runAction(t, MonitorScripts.probeHostCmd(ip, "ping"), onOutput = { torchOut = it; page = MonitorPage.TORCH }) }
                    )
                    MonitorPage.FILES -> FilesPage(
                        dirPath = dirPath,
                        data = dirData,
                        onTopicHelp = ::showTopic,
                        onOpenDir = ::openDir,
                        onOpenFile = ::openFile,
                        onNewDir = { newEntryName = ""; newEntryMode = "dir" },
                        onNewFile = { newEntryName = ""; newEntryMode = "file" },
                        onDeleteEntry = { full -> runAction("Удаление", MonitorScripts.rmPathCmd(full)) { openDir(dirPath) } }
                    )
                    MonitorPage.TORCH -> TorchPage(
                        host = torchHost,
                        onHostChange = { torchHost = it },
                        out = torchOut,
                        onTopicHelp = ::showTopic,
                        onCopy = { clipboard.setText(AnnotatedString(torchOut)); statusMsg = "Скопировано" },
                        onRun = { mode -> runAction("Утилита $mode", MonitorScripts.probeHostCmd(torchHost, mode), onOutput = { torchOut = it }) }
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
                            runAction("Лог $src", MonitorScripts.fetchLog(src), onOutput = { logsOut = it })
                        },
                        onRunCmd = { cmd -> runAction("SSH", cmd, onOutput = { consoleOut = it }) },
                        onCopy = { txt -> clipboard.setText(AnnotatedString(txt)); statusMsg = "Скопировано" }
                    )
                }
            }
        }
    }

    helpTopic?.let { tp ->
        AlertDialog(
            onDismissRequest = { helpTopic = null },
            title = { Text(tp.title, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSection("Что это", tp.what, Color(0xFF93C5FD))
                    HelpSection("Зачем нужно", tp.why, Color(0xFF86EFAC))
                    HelpSection("Рекомендация для Giga", tp.recommend, Color(0xFFE2E8F0))
                    if (tp.caution.isNotBlank()) HelpSection("Важно", tp.caution, Color(0xFFFCD34D))
                }
            },
            confirmButton = { TextButton(onClick = { helpTopic = null }) { Text("Понятно") } }
        )
    }

    svcModalKey?.let { key ->
        val guide = MonitorHelp.services[key]
        val svcObj = overview?.optJSONObject("svc")
        val isUp = svcObj?.optBoolean(key, false) == true
        val warpObj = bypass?.optJSONObject("warp")
        val warpInstalled = warpObj?.optBoolean("installed", false) ?: (key != "warp" || services?.optJSONArray("svcs")?.let { arr ->
            (0 until arr.length()).any { arr.optJSONObject(it)?.optString("id") == "S51z2k-warp" && arr.optJSONObject(it)?.optBoolean("inst") == true }
        } ?: false)
        val initMap = mapOf(
            "nfqws2" to "S99zapret2", "adguard" to "S99adguardhome", "tailscale" to "S06tailscaled",
            "tg_tunnel" to "S98tg-tunnel", "http_tunnel" to "S97z2k-http-tunnel", "warp" to "S51z2k-warp",
            "z2k_panel" to "S96z2k-webpanel", "yadoma_pub" to "S98yadoma-rules", "scheduler" to "S99z2k-scheduler",
            "giga_panel" to "S98giga-panel"
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
                        HelpSection("Влияние на PS5 / Игры", guide.forPs5, Color(0xFFFCD34D))
                        if (guide.configPath.isNotBlank()) Text("Конфиг: ${guide.configPath}", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    HorizontalDivider(color = Color(0xFF1E293B))
                    when (key) {
                        "warp" -> {
                            if (!warpInstalled) {
                                Button(onClick = { svcModalKey = null; runAction("Установка WARP", MonitorScripts.warpInstallCmd(), MonitorPage.OVERVIEW) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Установить Cloudflare WARP", fontSize = 12.sp)
                                }
                            } else {
                                Button(onClick = { svcModalKey = null; runAction(if (isUp) "Стоп WARP" else "Старт WARP", MonitorScripts.toggleZ2k("game-warp", if (isUp) 0 else 1), MonitorPage.OVERVIEW) }, modifier = Modifier.fillMaxWidth()) {
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

    if (fileModalOpen) {
        AlertDialog(
            onDismissRequest = { fileModalOpen = false },
            title = { Text(fileModalPath.substringAfterLast('/'), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fileModalPath, color = Color(0xFF93C5FD), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    if (fileModalTrunc) Text("Файл > 32 КБ: показана только первая часть (сохранение заблокировано).", color = Color(0xFFFBBF24), fontSize = 11.sp)
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
                if (!fileModalTrunc) {
                    TextButton(onClick = {
                        val b64 = Base64.encodeToString(fileModalText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        fileModalOpen = false
                        runAction("Сохранение файла", MonitorScripts.writeFileCmd(fileModalPath, b64), syncRulesAfter = fileModalPath.startsWith("/opt/zapret2/lists/"))
                    }) { Text("Сохранить (.bak)") }
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
            title = { Text("SSH подключение к Giga") },
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
            title = { Text("Перезагрузить Giga?") },
            text = { Text("Интернет и Wi-Fi пропадут примерно на 1–2 минуты.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReboot = false
                    runAction("Перезагрузка Giga", "LD_LIBRARY_PATH= /bin/ndmc -c 'system reboot'")
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
                Text(title, color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
            Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (helpClick != null) HelpBadge(helpClick)
        }
        Text(value, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MiniBar(label: String, detail: String, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val c = when { p > 0.85f -> Color(0xFFEF4444); p > 0.65f -> Color(0xFFF59E0B); else -> Color(0xFF3B82F6) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Color(0xFFCBD5E1), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(progress = { p }, color = c, trackColor = Color(0xFF1E293B), modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun MiniSwitch(title: String, sub: String, checked: Boolean, helpClick: (() -> Unit)? = null, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                if (helpClick != null) HelpBadge(helpClick)
            }
            if (sub.isNotBlank()) Text(sub, color = Color(0xFF64748B), fontSize = 9.sp)
        }
        Switch(checked = checked, onCheckedChange = { onToggle(if (it) 1 else 0) }, modifier = Modifier.scale(0.76f))
    }
}

@Composable
private fun MiniPill(label: String, ok: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        color = if (ok) Color(0xFF052E16) else Color(0xFF1F1625),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (ok) Color(0xFF166534) else Color(0xFF3F2336)),
        modifier = m
    ) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(if (ok) "ON" else "OFF", color = if (ok) Color(0xFF4ADE80) else Color(0xFFFCA5A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MonoBox(text: String, onCopy: (() -> Unit)? = null) {
    Surface(color = Color(0xFF020617), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            if (onCopy != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(22.dp)) { Icon(Icons.Rounded.ContentCopy, "Копировать", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp)) }
                }
            }
            SelectionContainer { Text(text, color = Color(0xFFCBD5E1), fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp) }
        }
    }
}

@Composable
private fun OverviewPage(
    data: JSONObject?,
    diagOut: String,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onServiceTap: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onUpdateZ2k: () -> Unit,
    onKill1070: () -> Unit,
    onBenchWan: () -> Unit
) {
    if (data == null) {
        MiniCard("Подключение к Giga...", { onTopicHelp("overview") }) {
            Text("Ожидаю ответ SSH с роутера... Проверь Wi-Fi «Gorchakov» или VPN YaDoma и настройки в ⚙.", color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
        return
    }
    val sys = data.optJSONObject("sys") ?: JSONObject()
    val mem = sys.optJSONObject("mem") ?: JSONObject()
    val disk = sys.optJSONObject("disk") ?: JSONObject()
    val wan = data.optJSONObject("wan") ?: JSONObject()
    val svc = data.optJSONObject("svc") ?: JSONObject()
    val ver = data.optJSONObject("ver") ?: JSONObject()
    val z2u = ver.optJSONObject("z2u") ?: JSONObject()

    if (sys.optInt("m1070", 0) == 1) {
        Surface(color = Color(0xFF451A03), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Color(0xFFD97706)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Активна лишняя Wi-Fi точка Netcraze-1070", color = Color(0xFFFCD34D), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Button(onClick = onKill1070, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)), contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(26.dp)) {
                    Text("Убрать", fontSize = 10.sp, color = Color.White)
                }
            }
        }
    }

    MiniCard("Система и ресурсы", { onTopicHelp("overview") }) {
        MiniRow("Модель / NDMS", "${sys.optString("model", "Giga")} • ${sys.optString("ndms", "—")}")
        MiniRow("Аптайм / CPU (${sys.optInt("cpus", 2)} ядра)", "${formatUptime(sys.optLong("up_sec"))} • ${sys.optString("load", "—")}")
        val t24 = sys.optString("temp24")
        val t5 = sys.optString("temp5")
        if (t24.isNotBlank() || t5.isNotBlank()) MiniRow("Температура Wi-Fi", "2.4G: ${t24.ifBlank { "—" }}°C • 5G: ${t5.ifBlank { "—" }}°C", Color(0xFF93C5FD))
        MiniRow("Conntrack NAT", "${sys.optInt("ct", 0)} / ${sys.optInt("ct_max", 0)}", helpClick = {
            onQuickHelp("Conntrack NAT", "Таблица активных сетевых соединений через роутер.", "Чем больше запас свободных слотов, тем стабильнее торренты, игры и браузер.", "32768 для дома с большим запасом.", "")
        })
        val mt = mem.optInt("total", 0).coerceAtLeast(1)
        val mu = mem.optInt("used", 0)
        MiniBar("RAM", "$mu/${mt} МБ (Swap ${mem.optInt("swap_u", 0)}/${mem.optInt("swap_t", 0)})", mu.toFloat() / mt)
        val dt = disk.optInt("total", 0).coerceAtLeast(1)
        val du = disk.optInt("used", 0)
        MiniBar("USB /opt", "своб. ${disk.optInt("avail", 0)} МБ (${disk.optInt("pct", 0)}%)", du.toFloat() / dt)
    }

    val wanIp = wan.optString("ip", "—")
    val isCgnat = Regex("""^(10\.|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.)""").containsMatchIn(wanIp)
    MiniCard("Интернет и Tailscale", { onTopicHelp("wan_ts") }) {
        MiniRow("WAN (eth3 ${wan.optString("spd", "?")}M)", "${wanIp.ifBlank { "Нет IP" }} ${if (isCgnat) "(CGNAT)" else ""}", if (isCgnat) Color(0xFFFBBF24) else Color(0xFF86EFAC), helpClick = {
            onQuickHelp("WAN IP и CGNAT", "Внешний адрес от провайдера ФринЭт.", "Адреса 100.64.0.0/10 означают CGNAT (общий IP на несколько абонентов): входящие порты с улицы режет провайдер.", "На Tailscale и исходящие игры не мешает; PS5 даёт NAT Type 2.", "")
        })
        MiniRow("Шлюз ISP", wan.optString("gw", "—"))
        MiniRow("Tailscale", "${wan.optString("ts_ip", "—")} • пиры ${wan.optString("ts_peers", "0/0")}", Color(0xFF60A5FA))
    }

    MiniCard("Радар служб (нажми на службу для управления и справки)", { onTopicHelp("services") }) {
        val items = listOf(
            "nfqws2" to "Zapret2", "gvsub" to "gvsub",
            "adguard" to "AdGuard", "tailscale" to "Tailscale",
            "tg_tunnel" to "TG :1443", "http_tunnel" to "HTTP :1444",
            "warp" to "WARP", "z2k_panel" to "z2k :8088",
            "yadoma_pub" to "Rules :8089", "scheduler" to "Планир.",
            "giga_panel" to "Giga :8090"
        )
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                pair.forEach { (key, lbl) ->
                    MiniPill(lbl, svc.optBoolean(key), Modifier.weight(1f)) { onServiceTap(key) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    val z2kLoc = ver.optString("z2k_local", "—")
    val z2kLat = z2u.optString("available", "").ifBlank { z2u.optString("latest", "") }
    val behind = z2u.optInt("behind", 0)
    val z2kUpd = behind > 0 || z2u.optBoolean("update_available", false)
    MiniCard("Версии ПО и замер WAN") {
        MiniRow("Zapret2 (z2k)", if (z2kLat.isNotBlank() && z2kUpd) "$z2kLoc → $z2kLat (+$behind)" else z2kLoc, if (z2kUpd) Color(0xFFFBBF24) else Color.White)
        MiniRow("AdGuard / Tailscale", "${ver.optString("agh", "—")} • ${ver.optString("ts", "—")}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.weight(1f).height(29.dp), contentPadding = PaddingValues(2.dp)) { Text("Проверить обн.", fontSize = 10.sp) }
            Button(onClick = onBenchWan, modifier = Modifier.weight(1f).height(29.dp), contentPadding = PaddingValues(2.dp)) { Text("Тест WAN", fontSize = 10.sp) }
            if (z2kUpd) Button(onClick = onUpdateZ2k, modifier = Modifier.height(29.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Обновить z2k", fontSize = 10.sp) }
        }
        if (diagOut.isNotBlank()) MonoBox(diagOut)
    }
}

@Composable
private fun ServicesPage(
    data: JSONObject?,
    opkgOut: String,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onSvcAction: (String, String) -> Unit,
    onOpkgRun: (String, String) -> Unit
) {
    val labels = mapOf(
        "S99zapret2" to "Zapret2 DPI (nfqws2)", "S99adguardhome" to "AdGuard Home (:3080/:53)",
        "S06tailscaled" to "Tailscale VPN", "S98tg-tunnel" to "Telegram прокси (:1443)",
        "S51z2k-warp" to "Cloudflare WARP", "S98yadoma-rules" to "Экспорт YaDoma (:8089)",
        "S96z2k-webpanel" to "Веб-панель z2k (:8088)", "S99z2k-scheduler" to "Планировщик z2k",
        "S96z2k-rt-proxy" to "z2k RT Proxy", "S97z2k-http-tunnel" to "z2k HTTP Туннель (:1444)",
        "S98giga-panel" to "Веб-панель Giga (:8090)", "S10cron" to "Планировщик Cron"
    )
    MiniCard("Службы Entware (/opt/etc/init.d)", { onTopicHelp("services") }) {
        val svcs = data?.optJSONArray("svcs") ?: JSONArray()
        for (i in 0 until svcs.length()) {
            val s = svcs.optJSONObject(i) ?: continue
            if (!s.optBoolean("inst")) continue
            val id = s.optString("id")
            val up = s.optBoolean("up")
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (up) Color(0xFF22C55E) else Color(0xFFEF4444)))
                    Column {
                        Text(labels[id] ?: id, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(id, color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { onSvcAction(id, "restart") }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("↻", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onSvcAction(id, if (up) "stop" else "start") }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text(if (up) "Стоп" else "Старт", fontSize = 10.sp) }
                }
            }
            if (i < svcs.length() - 1) HorizontalDivider(color = Color(0xFF1E293B))
        }
    }

    var pkgQuery by remember { mutableStateOf("") }
    MiniCard("Менеджер пакетов Entware (opkg)", {
        onQuickHelp("Пакеты Entware (opkg)", "Магазин консольных пакетов Linux на USB-диске /opt (сейчас установлено: ${data?.optInt("opkg_cnt", 0) ?: 0}).", "Позволяет ставить и обновлять программы прямо с телефона: например, AdGuardHome, Tailscale, iperf3 для замера скорости, htop, tcpdump.", "Сначала нажми «Проверить обн.», затем можно обновить всё или поставить нужный пакет.", "После обновления tailscale или adguardhome служба перезапустится.")
    }) {
        MiniRow("Установлено пакетов", "${data?.optInt("opkg_cnt", 0) ?: 0} шт.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { onOpkgRun("Проверка opkg", MonitorScripts.CMD_OPKG_CHECK) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Проверить обн.", fontSize = 10.sp) }
            Button(onClick = { onOpkgRun("Обновление opkg", MonitorScripts.CMD_OPKG_UPGRADE_ALL) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Обновить всё", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("htop", "iperf3", "mtr", "tcpdump", "mc", "nano").forEach { pkg ->
                AssistChip(onClick = { onOpkgRun("Установка $pkg", MonitorScripts.opkgInstallCmd(pkg)) }, label = { Text("+ $pkg", fontSize = 9.sp) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = pkgQuery, onValueChange = { pkgQuery = it }, placeholder = { Text("имя пакета (напр. curl)", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { if (pkgQuery.isNotBlank()) onOpkgRun("Поиск $pkgQuery", MonitorScripts.opkgSearchCmd(pkgQuery)) }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Поиск", fontSize = 10.sp) }
            Button(onClick = { if (pkgQuery.isNotBlank()) onOpkgRun("Установка $pkgQuery", MonitorScripts.opkgInstallCmd(pkgQuery)) }, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Уст.", fontSize = 10.sp) }
        }
        if (opkgOut.isNotBlank()) MonoBox(opkgOut)
    }
}

@Composable
private fun StoragePage(
    data: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit,
    onFix8090: () -> Unit,
    onReboot: () -> Unit
) {
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    val disks = data?.optJSONArray("disks") ?: JSONArray()
    MiniCard("USB-накопитель и разделы", { onTopicHelp("storage") }) {
        if (disks.length() == 0) Text("Нажмите ⟳ для загрузки", color = Color(0xFF64748B), fontSize = 11.sp)
        for (i in 0 until disks.length()) {
            val dk = disks.optJSONObject(i) ?: continue
            val tot = dk.optInt("tot", 0).coerceAtLeast(1)
            val used = dk.optInt("used", 0)
            MiniBar(dk.optString("mnt"), "своб. ${dk.optInt("avail")} / $tot МБ (${dk.optInt("pct")}%)", used.toFloat() / tot)
        }
    }
    MiniCard("Системные параметры NDMS", { onTopicHelp("storage") }) {
        MiniSwitch("SMB / Windows шара (CIFS)", "Доступ к USB-флешке с ПК/X-plore", ndms.optBoolean("cifs")) { v ->
            onAction("CIFS", MonitorScripts.ndmcSaveCmd(if (v == 1) "service cifs" else "no service cifs"))
        }
        MiniSwitch("WebDAV сервер", "Доступ к файлам по HTTP/WebDAV", ndms.optBoolean("webdav")) { v ->
            onAction("WebDAV", MonitorScripts.ndmcSaveCmd(if (v == 1) "ip http webdav enable" else "no ip http webdav enable"))
        }
        MiniSwitch("ZRAM (сжатие RAM)", "Увеличивает запас памяти под AdGuard/z2k", ndms.optBoolean("zram"), helpClick = {
            onQuickHelp("ZRAM", "Сжатый своп прямо в оперативной памяти.", "Помогает Giga не упираться в 512 МБ RAM при больших списках доменов.", "Рекомендуется держать включённым (ON).", "")
        }) { v ->
            onAction("ZRAM", MonitorScripts.ndmcSaveCmd(if (v == 1) "zram" else "no zram"))
        }
        MiniSwitch("Принудительный USB 2.0", "Снижает скорость USB ради чистоты Wi-Fi 2.4G", ndms.optBoolean("usb2"), helpClick = {
            onQuickHelp("USB 2.0 vs USB 3.0", "USB 3.0 создаёт радиошум в диапазоне 2.4 ГГц.", "Если флешка работает быстро и 2.4 ГГц не лагает — оставь OFF (USB 3.0).", "Выключай USB 3.0 (ON здесь) только если 2.4 ГГц сильно проседает рядом с роутером.", "Требует перезагрузки роутера.")
        }) { v ->
            onAction("USB режим", MonitorScripts.ndmcSaveCmd("system set dev.usb.force_usb2 $v"))
        }
    }
    MiniCard("Питание и конфигурация") {
        Button(onClick = { onAction("Сохранение NDMS", MonitorScripts.ndmcSaveCmd()) }, modifier = Modifier.fillMaxWidth().height(32.dp)) { Text("Сохранить конфиг NDMS во флеш", fontSize = 11.sp) }
        OutlinedButton(onClick = onFix8090, modifier = Modifier.fillMaxWidth().height(32.dp)) { Text("Перезапустить локальную панель :8090", fontSize = 11.sp) }
        Button(onClick = onReboot, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)), modifier = Modifier.fillMaxWidth().height(32.dp)) { Text("Перезагрузить роутер Giga", fontSize = 11.sp, color = Color.White) }
    }
}

@Composable
private fun WanTsPage(
    data: JSONObject?,
    tsPingOut: String,
    diagOut: String,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onTsPing: (String) -> Unit,
    onBench: () -> Unit,
    onAction: (String, String) -> Unit
) {
    MiniCard("WAN интерфейс eth3 / Порт ISP", { onTopicHelp("wan_ts") }) {
        MiniRow("IP / Шлюз", "${data?.optString("wan", "—") ?: "—"} / ${data?.optString("gw", "—") ?: "—"}")
        MiniRow("Порт / Линк", "${data?.optString("spd", "?")} Мбит/с • ${data?.optString("eth", "—")}")
        MiniRow("Сист. DNS", data?.optString("dns", "—") ?: "—")
        MiniRow("Трафик с ребута", "↓ ${formatBytes(data?.optDouble("rx", 0.0) ?: 0.0)}  ↑ ${formatBytes(data?.optDouble("tx", 0.0) ?: 0.0)}")
        Button(onClick = onBench, modifier = Modifier.fillMaxWidth().height(30.dp), contentPadding = PaddingValues(4.dp)) { Text("Замерить пинг и скорость WAN (10 МБ)", fontSize = 10.sp) }
        if (diagOut.isNotBlank()) MonoBox(diagOut)
    }

    val tsExit = data?.optBoolean("ts_exit", false) == true
    val tsLan = data?.optBoolean("ts_lan", false) == true
    val tsDns = data?.optBoolean("ts_dns", false) == true
    val tsShd = data?.optBoolean("ts_shd", false) == true
    MiniCard("Настройки Tailscale на роутере", { onTopicHelp("wan_ts") }) {
        MiniRow("IP / Версия", "${data?.optString("ts_ip", "—") ?: "—"} • v${data?.optString("ts_ver", "—") ?: "—"}", Color(0xFF60A5FA))
        MiniSwitch("Exit Node (выход в интернет)", "Разрешить телефону YaDoma ходить через Giga", tsExit, helpClick = {
            onQuickHelp("Exit Node", "Делает роутер шлюзом для мобильного интернета.", "Нужно для работы YaDoma на LTE.", "Должно быть ON.", "Выключение сразу отрежет выход YaDoma в интернет через дом.")
        }) { v -> onAction("TS Exit Node", MonitorScripts.setTailscalePrefsCmd(v == 1, tsLan, tsDns, tsShd)) }
        MiniSwitch("Доступ к подсети 192.168.1.0/24", "Видеть 192.168.1.1, PS5 и ПК удалённо", tsLan) { v ->
            onAction("TS LAN Subnet", MonitorScripts.setTailscalePrefsCmd(tsExit, v == 1, tsDns, tsShd))
        }
        MiniSwitch("Accept DNS (Tailscale DNS)", "Принимать DNS из панели Tailscale", tsDns) { v ->
            onAction("TS Accept DNS", MonitorScripts.setTailscalePrefsCmd(tsExit, tsLan, v == 1, tsShd))
        }
        MiniSwitch("Shields Up (блок входящих TS)", "Запрещать входящие подключения из Tailscale", tsShd) { v ->
            onAction("TS Shields Up", MonitorScripts.setTailscalePrefsCmd(tsExit, tsLan, tsDns, v == 1))
        }
        OutlinedButton(onClick = { onAction("Обновление Tailscale", MonitorScripts.opkgInstallCmd("tailscale")) }, modifier = Modifier.fillMaxWidth().height(28.dp), contentPadding = PaddingValues(2.dp)) {
            Text("Проверить / обновить Tailscale (opkg)", fontSize = 10.sp)
        }
    }

    val peers = data?.optJSONArray("peers") ?: JSONArray()
    MiniCard("Пиры Tailscale (${peers.length()})") {
        for (i in 0 until peers.length()) {
            val pr = peers.optJSONObject(i) ?: continue
            val ip = pr.optString("ip")
            val on = pr.optBoolean("on")
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Color(0xFF22C55E) else Color(0xFF475569)))
                    Column {
                        Text("${pr.optString("name")} ($ip)", color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pr.optString("os")} • ${pr.optString("info")}", color = Color(0xFF94A3B8), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(onClick = { onTsPing(ip) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Ping", fontSize = 10.sp) }
            }
            if (i < peers.length() - 1) HorizontalDivider(color = Color(0xFF1E293B))
        }
        if (tsPingOut.isNotBlank()) MonoBox(tsPingOut)
    }
}

@Composable
private fun DnsPage(
    data: JSONObject?,
    probeOut: String,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit,
    onProbeDns: () -> Unit
) {
    val aghUp = data?.optBoolean("agh_up", false) == true
    val agh = data?.optJSONObject("agh") ?: JSONObject()
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    val ups = agh.optJSONArray("upstreams") ?: JSONArray()
    val flts = agh.optJSONArray("filters") ?: JSONArray()
    val errs = data?.optJSONArray("errs") ?: JSONArray()

    MiniCard("AdGuard Home (:53 / :3080)", { onTopicHelp("dns") }) {
        MiniRow("Статус / Запросов в логе", "${if (aghUp) "Онлайн" else "Стоп"} • ${data?.optInt("qcnt", 0) ?: 0}", if (aghUp) Color(0xFF4ADE80) else Color(0xFFF87171))
        MiniSwitch("Защита AdGuard", "Общий рубильник фильтрации рекламы", agh.optBoolean("prot")) { v ->
            onAction("Защита AdGuard", MonitorScripts.setAghKeyCmd("protection_enabled", v == 1))
        }
        MiniSwitch("Фильтры доменов", "Применение чёрных списков", agh.optBoolean("filt")) { v ->
            onAction("Фильтрация AGH", MonitorScripts.setAghKeyCmd("filtering_enabled", v == 1))
        }
        MiniSwitch("Entware DNS Override", "Направлять весь DNS дома в AdGuard", ndms.optBoolean("dns_ov"), helpClick = {
            onQuickHelp("opkg dns-override", "Перехватывает системный DNS-сервер Keenetic и отдаёт клиентов в Entware AdGuard.", "Без этой галочки реклама не режется и списки z2k могут расходиться.", "Обязательно ON.", "")
        }) { v ->
            onAction("DNS Override", MonitorScripts.ndmcSaveCmd(if (v == 1) "opkg dns-override" else "no opkg dns-override"))
        }
        MiniSwitch("DNS-кэш / Оптимистичный", "Ускоряет повторные запросы сайтов", agh.optBoolean("cache")) { v ->
            onAction("Кэш AGH", MonitorScripts.setAghKeyCmd("cache_enabled", v == 1))
        }
        MiniSwitch("Оптимистичный кэш", "Отдаёт кэш мгновенно, обновляя в фоне", agh.optBoolean("cache_opt")) { v ->
            onAction("Оптим. кэш", MonitorScripts.setAghKeyCmd("cache_optimistic", v == 1))
        }
        MiniSwitch("DNSSEC проверка", "Криптоподпись доменов (чуть медленнее)", agh.optBoolean("dnssec")) { v ->
            onAction("DNSSEC", MonitorScripts.setAghKeyCmd("enable_dnssec", v == 1))
        }
        MiniSwitch("Отключить IPv6 (AAAA)", "Иногда лечит подвисания при кривом IPv6", agh.optBoolean("no_aaaa")) { v ->
            onAction("Блок AAAA", MonitorScripts.setAghKeyCmd("aaaa_disabled", v == 1))
        }
    }

    if (flts.length() > 0) {
        MiniCard("Списки фильтров AdGuard") {
            for (i in 0 until flts.length()) {
                val f = flts.optJSONObject(i) ?: continue
                MiniSwitch(f.optString("name"), "ID #${f.optInt("id")}", f.optBoolean("on")) { v ->
                    onAction("Фильтр #${f.optInt("id")}", MonitorScripts.setAghFilterCmd(f.optInt("id"), v == 1))
                }
            }
        }
    }

    var customUps by remember(ups.toString()) { mutableStateOf((0 until ups.length()).joinToString("\n") { ups.optString(it) }) }
    MiniCard("Upstream DNS серверы", {
        onQuickHelp("Upstream DNS", "Куда именно AdGuard Home пересылает неизвестные ему доменные имена.", "DoH (https://...) шифрует запросы от провайдера; обычный UDP (1.1.1.1 / 77.88.8.8) даёт минимальную задержку, но без шифрования.", "При ошибках DoT (:853) используй профиль «DoH Стаб.».", "")
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { onAction("DoH Quad9+CF", MonitorScripts.setAghDnsProfile("doh_stable")) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("DoH Стаб.", fontSize = 10.sp) }
            OutlinedButton(onClick = { onAction("Быстрый UDP", MonitorScripts.setAghDnsProfile("fast_udp")) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("UDP Быстр.", fontSize = 10.sp) }
            OutlinedButton(onClick = { onAction("AdGuard+CF", MonitorScripts.setAghDnsProfile("agh_cf")) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("AGH+CF", fontSize = 10.sp) }
        }
        OutlinedTextField(
            value = customUps,
            onValueChange = { customUps = it },
            label = { Text("Список Upstream (по 1 на строке)", fontSize = 10.sp) },
            textStyle = LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp, max = 140.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = { if (customUps.isNotBlank()) onAction("Сохранить Upstream", MonitorScripts.setAghCustomUpstreamsCmd(customUps)) }, modifier = Modifier.weight(1f).height(29.dp), contentPadding = PaddingValues(2.dp)) { Text("Применить DNS", fontSize = 10.sp) }
            OutlinedButton(onClick = onProbeDns, modifier = Modifier.weight(1f).height(29.dp), contentPadding = PaddingValues(2.dp)) { Text("Тест DNS", fontSize = 10.sp) }
        }
        OutlinedButton(onClick = { onAction("Обновить AdGuardHome", MonitorScripts.opkgInstallCmd("adguardhome-go")) }, modifier = Modifier.fillMaxWidth().height(28.dp), contentPadding = PaddingValues(2.dp)) {
            Text("Обновить AdGuardHome через opkg", fontSize = 10.sp)
        }
        if (probeOut.isNotBlank()) MonoBox(probeOut)
        if (errs.length() > 0) {
            Text("Последние ошибки dnsproxy:", color = Color(0xFFFCA5A5), fontSize = 10.sp)
            MonoBox((0 until errs.length()).joinToString("\n") { errs.optString(it) })
        }
    }
}

@Composable
private fun NatPpePage(
    data: JSONObject?,
    devicesData: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit
) {
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    val stat = data?.optJSONArray("ip_static") ?: JSONArray()
    val upnp = data?.optJSONArray("upnp_list") ?: JSONArray()

    MiniCard("Аппаратное ускорение (PPE), UPnP и Сеть", { onTopicHelp("nat_ppe") }) {
        MiniSwitch("Hardware NAT (ppe hardware)", "Аппаратный fast-path коммутатора", ndms.optBoolean("ppe_hw")) { v ->
            onAction("Hardware NAT", MonitorScripts.ndmcSaveCmd(if (v == 1) "ppe hardware" else "no ppe hardware"))
        }
        MiniSwitch("Software PPE (ppe software)", "Ядерный fast-path ускоритель", ndms.optBoolean("ppe_sw")) { v ->
            onAction("Software PPE", MonitorScripts.ndmcSaveCmd(if (v == 1) "ppe software" else "no ppe software"))
        }
        MiniSwitch("UPnP для игр и PS5", "Авто-открытие портов в домашней сети", ndms.optBoolean("upnp")) { v ->
            onAction("UPnP", MonitorScripts.ndmcSaveCmd(if (v == 1) "service upnp" else "no service upnp"))
        }
        MiniSwitch("IGMP Proxy (IPTV)", "Пропуск мультикаст-потоков ТВ", ndms.optBoolean("igmp")) { v ->
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
    var targetIp by remember { mutableStateOf("192.168.1.69") }
    var locPort by remember { mutableStateOf("") }
    val devArr = devicesData?.optJSONArray("devices") ?: JSONArray()

    MiniCard("Проброс портов (ip static) (${stat.length()})", {
        onQuickHelp("Static NAT (проброс портов)", "Перенаправляет входящие соединения с WAN-порта ISP на локальное устройство.", "Удобно для PS5 (192.168.1.69), домашнего сервера, удалённого доступа.", "Можно указать диапазон через дефис, например 3478-3480.", "При CGNAT входящие с улицы могут резаться провайдером, но внутри сети и UPnP правила остаются корректными.")
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
            OutlinedTextField(value = extPort, onValueChange = { extPort = it }, placeholder = { Text("порт / 3478-3480", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = targetIp, onValueChange = { targetIp = it }, label = { Text("IP внутри", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
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
        OutlinedButton(onClick = { onAction("Порты PS5", MonitorScripts.CMD_REAPPLY_PS5_NAT) }, modifier = Modifier.fillMaxWidth().height(29.dp), contentPadding = PaddingValues(2.dp)) {
            Text("Пресет: Применить все порты + приоритет PS5", fontSize = 10.sp)
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
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onToggle: (String, String, Int) -> Unit,
    onAction: (String, String, Boolean) -> Unit,
    onProbe: () -> Unit
) {
    val z2k = data?.optJSONObject("z2k") ?: JSONObject()
    val t = z2k.optJSONObject("toggles") ?: JSONObject()
    val tunnelOn = z2k.optJSONObject("tunnel")?.optBoolean("running", false) == true
    val policy = data?.optJSONObject("policy") ?: JSONObject()

    MiniCard("Категории и режимы Zapret2 (:8088)", { onTopicHelp("z2k") }) {
        MiniSwitch("YouTube + Google Video", "Основной обход YouTube", t.optString("category_youtube") == "1") { onToggle("YouTube", "category-youtube", it) }
        MiniSwitch("Сайты РКН (IG, ChatGPT, X, Discord)", "Обход TCP 443 по спискам RKN", t.optString("category_rkn") == "1") { onToggle("РКН", "category-rkn", it) }
        MiniSwitch("Discord Voice / STUN (UDP)", "Голосовые каналы Discord", t.optString("category_discord_voice") == "1") { onToggle("Discord Voice", "category-discord-voice", it) }
        MiniSwitch("Мой список (extra-domains.txt)", "Персонально добавленные домены", t.optString("customd") == "1") { onToggle("Extra-domains", "customd", it) }
        MiniSwitch("Автохостлист (autohostlist)", "Автоматически ловит заблокированные домены", t.optString("autohostlist") == "1", helpClick = {
            onQuickHelp("Автохостлист", "Если сайт не открывается на обычной проверке, nfqws2 сам дописывает его домен в отдельный список.", "Удобно тем, что новые блокировки подхватываются сами.", "Можно включать, лишние домены легко почистить на вкладке «Списки & Подбор».", "")
        }) { onToggle("Автохостлист", "autohostlist", it) }
        MiniSwitch("YouTube без QUIC (только TCP)", "Лечит зависания и буферизацию видео", t.optString("yt_quic_off") == "1", helpClick = {
            onQuickHelp("YouTube без QUIC", "Блокирует протокол QUIC (UDP 443) к YouTube/GoogleVideo.", "Браузер и телефон переходят на обычный TCP, где десинк работает намного надёжнее.", "Включи, если ролики крутятся или долго стартуют.", "")
        }) { onToggle("YT без QUIC", "yt-quic-off", it) }
        MiniSwitch("Весь QUIC через TCP", "Отключает UDP 443 для всех сайтов из списков", t.optString("quic_off") == "1") { onToggle("Весь QUIC OFF", "quic-off", it) }
        MiniSwitch("Динамический Auto-TTL", "Точный подбор TTL фейков до ТСПУ", t.optString("dynamic_ttl") == "1") { onToggle("Auto-TTL", "dynamic-ttl", it) }
        MiniSwitch("GV Preflight (видеоответы)", "Повышает стабильность серверов googlevideo", t.optString("gv_preflight") == "1") { onToggle("GV Preflight", "gv-preflight", it) }
        MiniSwitch("Telegram туннель (:1443)", "Прокси для звонков и медиа в TG", tunnelOn) { v ->
            onAction("TG туннель", MonitorScripts.tunnelSetCmd(v == 1), false)
        }
    }

    var auHour by remember(t.optString("au_hour", "02")) { mutableStateOf(t.optString("au_hour", "02")) }
    MiniCard("Обновление, Политика и Синк") {
        MiniSwitch("Ночное автообновление", "Текущее время: ${t.optString("au_hour", "02")}:00", t.optString("auto_update") == "1") { onToggle("Автообновление", "auto-update", it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Час (00-23):", color = Color(0xFF94A3B8), fontSize = 10.sp)
            OutlinedTextField(value = auHour, onValueChange = { if (it.length <= 2) auHour = it }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.width(64.dp))
            OutlinedButton(onClick = { val h = auHour.trim().padStart(2, '0'); onAction("Час автообн. $h", MonitorScripts.z2kScheduleHourCmd(h), false) }, modifier = Modifier.height(29.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Задать", fontSize = 10.sp) }
            Button(onClick = { onAction("Обновление z2k", MonitorScripts.z2kUpdateApplyCmd(), true) }, modifier = Modifier.weight(1f).height(29.dp), contentPadding = PaddingValues(2.dp)) { Text("Обновить z2k", fontSize = 10.sp) }
        }
        val pName = policy.optString("name", "nfqws")
        val pExcl = policy.optString("exclude", "0") == "1"
        MiniRow("Политика доступа Keenetic", if (pName.isBlank()) "Все устройства" else "$pName (${if (pExcl) "исключить" else "только её"})")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { onAction("Обновление списков", "/opt/zapret2/z2k.sh update-lists 2>&1 && /opt/bin/yadoma-export-rules.sh", true) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Обн. списки", fontSize = 10.sp) }
            OutlinedButton(onClick = { onAction("Экспорт в YaDoma", "/opt/bin/yadoma-export-rules.sh", true) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Синк (${data?.optInt("rules_cnt", 0) ?: 0})", fontSize = 10.sp) }
            OutlinedButton(onClick = { onAction("Рестарт z2k", "/opt/etc/init.d/S99zapret2 restart 2>&1", false) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Рестарт z2k", fontSize = 10.sp) }
        }
    }

    MiniCard("Тест сайтов прямо с роутера") {
        Button(onClick = onProbe, modifier = Modifier.fillMaxWidth().height(30.dp), contentPadding = PaddingValues(4.dp)) { Text("Проверить YouTube / Discord / IG / ChatGPT / TG", fontSize = 10.sp) }
        if (probeOut.isNotBlank()) MonoBox(probeOut)
    }
}

@Composable
private fun WarpPage(
    bypass: JSONObject?,
    gamesData: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit
) {
    val warp = bypass?.optJSONObject("warp") ?: JSONObject()
    val installed = warp.optBoolean("installed", false)
    val enabled = warp.optString("enabled") == "1"
    val ready = warp.optBoolean("ready", false)
    val trMode = warp.optString("transport_mode", "auto")
    val trCur = warp.optString("transport", "—")
    val colo = warp.optString("edge_colo")
    val country = warp.optString("edge_country")
    val rtt = warp.optInt("edge_rtt_ms", 0)

    MiniCard("Cloudflare WARP в z2k", { onTopicHelp("warp") }) {
        MiniRow("Движок / Статус", "${if (installed) "Установлен" else "НЕ установлен"} • ${if (ready) "Готов" else if (enabled) "Запуск..." else "Выкл"}", if (ready) Color(0xFF4ADE80) else if (installed) Color(0xFFFBBF24) else Color(0xFFFCA5A5))
        if (installed) {
            MiniRow("Транспорт / План", "$trCur ($trMode) • ${warp.optString("plan", "WARP")} • RAM ${warp.optInt("mem_kb", 0) / 1024} МБ")
            if (colo.isNotBlank()) MiniRow("Сервер Edge", "$colo ($country) • ${rtt} мс")
            MiniRow("IP / Правила", "${warp.optString("addr", "—")} • ${warp.optInt("entries", 0)} записей")
            MiniSwitch("Включить WARP туннель", "Запускает z2k-warpd и маршрутизацию WARP-списков", enabled) { v ->
                onAction(if (v == 1) "Включение WARP" else "Выключение WARP", MonitorScripts.toggleZ2k("game-warp", v))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Транспорт:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                listOf("auto" to "Auto", "wg" to "WireGuard", "h2" to "HTTP/2").forEach { (k, lbl) ->
                    FilterChip(selected = trMode == k, onClick = { onAction("Транспорт $lbl", MonitorScripts.warpTransportCmd(k)) }, label = { Text(lbl, fontSize = 9.sp) })
                }
            }
            MiniSwitch("WDTT Watchdog", "Перезапускает WARP при падении канала", warp.optBoolean("wdtt_enabled")) { v ->
                onAction("WARP WDTT", MonitorScripts.warpWdttCmd(v))
            }
            var licKey by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = licKey, onValueChange = { licKey = it }, placeholder = { Text("Ключ WARP+ (необязат.)", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
                Button(onClick = { if (licKey.isNotBlank()) { onAction("Ключ WARP+", MonitorScripts.warpLicenseCmd(licKey)); licKey = "" } }, modifier = Modifier.height(34.dp)) { Text("OK", fontSize = 10.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                OutlinedButton(onClick = { onAction("Перерегистрация WARP", MonitorScripts.warpReregisterCmd()) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Перерегистр.", fontSize = 10.sp) }
                OutlinedButton(onClick = { onAction("Удаление WARP", MonitorScripts.warpRemoveCmd()) }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Удалить WARP", fontSize = 10.sp, color = Color(0xFFFCA5A5)) }
            }
        } else {
            Text("WARP сейчас не установлен на флешке роутера. Это нормально: он ставится по желанию (~7 МБ), если нужно пускать заблокированные по IP игры или AI через Cloudflare.", color = Color(0xFF94A3B8), fontSize = 10.sp)
            Button(onClick = { onAction("Установка WARP", MonitorScripts.warpInstallCmd()) }, modifier = Modifier.fillMaxWidth().height(34.dp)) {
                Text("Установить Cloudflare WARP в z2k", fontSize = 11.sp)
            }
        }
    }

    val games = gamesData?.optJSONArray("games") ?: JSONArray()
    if (installed && games.length() > 0) {
        MiniCard("Игры через WARP (${games.length()})", {
            onQuickHelp("Игры в WARP", "Готовые списки IP/подсетей игровых серверов из z2k.", "Включай только те игры, которые у тебя не коннектятся напрямую. Остальные игры оставь OFF для минимального пинга.", "", "")
        }) {
            for (i in 0 until games.length()) {
                val g = games.optJSONObject(i) ?: continue
                val gn = g.optString("name")
                val ge = g.optInt("entries", 0)
                val gon = g.optInt("enabled", 0) == 1
                MiniSwitch(gn, "$ge IP/подсетей", gon) { v ->
                    onAction("Игра $gn", MonitorScripts.warpGameToggleCmd(gn, v))
                }
            }
        }
    }
}

@Composable
private fun BypassListsPage(
    data: JSONObject?,
    domainInput: String,
    onDomainChange: (String) -> Unit,
    wlInput: String,
    onWlChange: (String) -> Unit,
    pickOut: String,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String, Boolean) -> Unit,
    onPick: (String, String) -> Unit
) {
    var pickDomain by remember { mutableStateOf("") }
    var pickMode by remember { mutableStateOf("tcp13") }
    MiniCard("Автоподбор рабочей стратегии", {
        onQuickHelp("Подбор стратегии (z2k)", "Роутер сам перебирает доступные Lua-стратегии десинка на конкретном домене и показывает, какие из них реально пробивают блокировку.", "Полезно, если какой-то сайт вдруг перестал открываться.", "Режим tcp13 подходит для большинства современных сайтов.", "")
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = pickDomain, onValueChange = { pickDomain = it }, placeholder = { Text("домен (напр. discord.com)", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            Button(onClick = { if (pickDomain.isNotBlank()) onPick(pickDomain, pickMode) }, modifier = Modifier.height(34.dp)) { Text("Подобрать", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("tcp13" to "TCP 1.3", "mixed" to "Mixed", "quic" to "QUIC", "voice" to "Voice").forEach { (m, l) ->
                FilterChip(selected = pickMode == m, onClick = { pickMode = m }, label = { Text(l, fontSize = 9.sp) })
            }
        }
        if (pickOut.isNotBlank()) MonoBox(pickOut)
    }

    val extraArr = data?.optJSONArray("extra") ?: JSONArray()
    MiniCard("Мои домены в обход (${extraArr.length()})", { onTopicHelp("z2k") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = domainInput, onValueChange = onDomainChange, placeholder = { Text("twitch.tv", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            Button(onClick = {
                val d = domainInput.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
                if (d.matches(Regex("""^[a-z0-9.-]+$""")) && d.contains('.')) {
                    onDomainChange("")
                    onAction("Добавлен $d", MonitorScripts.addExtraDomain(d), true)
                }
            }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+", fontSize = 13.sp) }
        }
        for (i in 0 until extraArr.length()) {
            val d = extraArr.optString(i)
            if (d.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(d, color = Color(0xFFE2E8F0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    IconButton(onClick = { onAction("Удалён $d", MonitorScripts.delListDomain("/opt/zapret2/lists/extra-domains.txt", d), true) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }

    val ahl = data?.optJSONObject("autohost")?.optJSONArray("domains") ?: JSONArray()
    if (ahl.length() > 0) {
        MiniCard("Пойманные автохостлистом (${ahl.length()})") {
            for (i in 0 until minOf(ahl.length(), 30)) {
                val d = ahl.optString(i)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(d, color = Color(0xFF93C5FD), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    IconButton(onClick = { onAction("Убран $d", MonitorScripts.delAutohostDomain(d), true) }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    val wlArr = data?.optJSONArray("whitelist") ?: JSONArray()
    MiniCard("Белый список / Исключения (${wlArr.length()})") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = wlInput, onValueChange = onWlChange, placeholder = { Text("sberbank.ru", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            Button(onClick = {
                val d = wlInput.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
                if (d.matches(Regex("""^[a-z0-9.-]+$""")) && d.contains('.')) {
                    onWlChange("")
                    onAction("Исключение $d", MonitorScripts.addWhitelistDomain(d), true)
                }
            }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+", fontSize = 13.sp) }
        }
        for (i in 0 until minOf(wlArr.length(), 40)) {
            val d = wlArr.optString(i)
            if (d.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(d, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    IconButton(onClick = { onAction("Убран $d", MonitorScripts.delListDomain("/opt/zapret2/lists/whitelist.txt", d), true) }, modifier = Modifier.size(24.dp)) {
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
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit
) {
    val wifi = data?.optJSONObject("wifi") ?: JSONObject()
    MiniCard("Точки доступа Wi-Fi", { onTopicHelp("wifi") }) {
        MiniRow("${wifi.optString("g24_ssid", "Gorchakov")} (2.4 ГГц)", "${if (wifi.optBoolean("g24_up", true)) "ON" else "OFF"} • кан.${wifi.optString("g24_ch", "—")} • ${wifi.optString("g24_bw", "—")}M • ${wifi.optInt("g24_rate", 0)}M • ${wifi.optString("g24_temp", "—")}°C", if (wifi.optBoolean("g24_up", true)) Color(0xFF4ADE80) else Color(0xFFF87171))
        val bz24 = wifi.optString("g24_busy")
        if (bz24.isNotBlank()) Text("Занятые 2.4G: $bz24", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        MiniRow("${wifi.optString("g5_ssid", "Gorchakov")} (5 ГГц)", "${if (wifi.optBoolean("g5_up", true)) "ON" else "OFF"} • кан.${wifi.optString("g5_ch", "—")} • ${wifi.optString("g5_bw", "—")}M • ${wifi.optInt("g5_rate", 0)}M • ${wifi.optString("g5_temp", "—")}°C", if (wifi.optBoolean("g5_up", true)) Color(0xFF4ADE80) else Color(0xFFF87171))
        val bz5 = wifi.optString("g5_busy")
        if (bz5.isNotBlank()) Text("Занятые 5G: $bz5", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        if (wifi.optBoolean("m1070")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Активна точка Netcraze-1070", color = Color(0xFFFCD34D), fontSize = 11.sp)
                Button(onClick = {
                    onAction("Убрать Netcraze-1070", MonitorScripts.ndmcSaveCmd("no mws wlan Home enable", "interface WifiMaster0/AccessPoint2 down", "interface WifiMaster1/AccessPoint2 down"))
                }, modifier = Modifier.height(26.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Убрать", fontSize = 10.sp) }
            }
        }
    }

    MiniCard("Каналы и ширина полосы", { onTopicHelp("wifi") }) {
        Text("2.4 ГГц (реком. 1, 6, 11 и ширина 20 МГц):", color = Color(0xFF94A3B8), fontSize = 10.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("auto", "1", "6", "11").forEach { ch ->
                OutlinedButton(onClick = { onAction("2.4G канал $ch", MonitorScripts.setWifiChannelCmd(0, ch, "20")) }, modifier = Modifier.height(26.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Кан.$ch", fontSize = 10.sp) }
            }
        }
        Text("5 ГГц (без DFS: 36, 40, 44, 48 • ширина 80 МГц):", color = Color(0xFF94A3B8), fontSize = 10.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("auto", "36", "40", "44", "48", "52", "149").forEach { ch ->
                OutlinedButton(onClick = { onAction("5G канал $ch", MonitorScripts.setWifiChannelCmd(1, ch, "40-above/80")) }, modifier = Modifier.height(26.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Кан.$ch", fontSize = 10.sp) }
            }
        }
    }

    MiniCard("Тонкие функции Wi-Fi и Гостевая сеть") {
        MiniSwitch("Band Steering (Bridge0)", "Переводит гаджеты с 2.4G на быстрый 5G", wifi.optBoolean("band_steer")) { v ->
            onAction("Band Steering", MonitorScripts.ndmcSaveCmd("interface Bridge0 ${if (v == 1) "band-steering" else "no band-steering"}"))
        }
        MiniSwitch("Быстрый роуминг 802.11r (FT)", "Мгновенный переход 2.4G ↔ 5G", wifi.optBoolean("ft")) { v ->
            val c = if (v == 1) "ft enable" else "no ft enable"
            onAction("802.11r FT", MonitorScripts.ndmcSaveCmd("interface WifiMaster0/AccessPoint0 $c", "interface WifiMaster1/AccessPoint0 $c"))
        }
        MiniSwitch("Target Wake Time (Wi-Fi 6 AX)", "Экономит батарею телефонов на 5 ГГц", wifi.optBoolean("twt")) { v ->
            onAction("Wi-Fi 6 TWT", MonitorScripts.ndmcSaveCmd("interface WifiMaster1 ${if (v == 1) "target-waketime" else "no target-waketime"}"))
        }
        MiniSwitch("Гостевая сеть (Guest)", "Изолированный Wi-Fi (${wifi.optInt("guest_rate", 0) / 1024} Мбит/с)", wifi.optBoolean("guest_up")) { v ->
            val act = if (v == 1) "up" else "down"
            onAction("Гостевой Wi-Fi", MonitorScripts.ndmcSaveCmd("interface WifiMaster0/AccessPoint1 $act", "interface WifiMaster1/AccessPoint1 $act"))
        }
        var newSsid by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newSsid, onValueChange = { newSsid = it }, placeholder = { Text("Новый SSID (имя Wi-Fi)", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { if (newSsid.isNotBlank()) { onAction("Смена SSID", MonitorScripts.setWifiSsidCmd(newSsid)); newSsid = "" } }, modifier = Modifier.height(34.dp)) { Text("Задать", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun Ps5Page(
    data: JSONObject?,
    wotOut: String,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit,
    onWotPing: () -> Unit
) {
    val ps5Static = data?.optJSONArray("ps5_static") ?: JSONArray()
    val arr = data?.optJSONArray("devices") ?: JSONArray()
    val ps5Dev = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.firstOrNull {
        it.optString("ip") == "192.168.1.69" || it.optString("mac").equals("b4:0a:d8:37:d1:a4", true)
    }
    val up = ps5Dev?.optString("link") == "up"
    val spd = ps5Dev?.optString("speed").orEmpty().ifBlank { "1000" }
    val prio = ps5Dev?.optInt("prio", 1) ?: 1

    MiniCard("PlayStation 5 (192.168.1.69)", { onTopicHelp("ps5") }) {
        MiniRow("Статус / Канал", "${if (up) "Онлайн" else "Оффлайн"} • LAN ${spd}M • Приоритет P$prio", if (up) Color(0xFF4ADE80) else Color(0xFF94A3B8))
        MiniRow("Static NAT / UPnP", "${ps5Static.length()} правил • UPnP ${data?.optInt("ps5_upnp", 0) ?: 0}", if (ps5Static.length() > 0) Color(0xFF86EFAC) else Color(0xFFFBBF24))
        if (ps5Static.length() > 0) MonoBox((0 until ps5Static.length()).joinToString("\n") { ps5Static.optString(it) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = onWotPing, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(2.dp)) { Text("Тест WoT & PS5", fontSize = 10.sp) }
            OutlinedButton(onClick = { onAction("WoL PS5", MonitorScripts.wolCmd("b4:0a:d8:37:d1:a4")) }, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(2.dp)) { Text("WoL PS5", fontSize = 10.sp) }
            OutlinedButton(onClick = { onAction("Порты + QoS PS5", MonitorScripts.CMD_REAPPLY_PS5_NAT) }, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(2.dp)) { Text("Оптим. NAT", fontSize = 10.sp) }
        }
        if (wotOut.isNotBlank()) MonoBox(wotOut)
    }

    MiniCard("Чек-лист для мин. пинга в WoT Console") {
        Text(
            "• Кабель Cat5e/6 прямо в порт Giga (LAN 1000M), не Wi-Fi.\n" +
                "• Приоритет PS5 = P1 + IntelliQoS (уже настроен).\n" +
                "• PPE Hardware + PPE Software = ON, WARP для PS5 = OFF.\n" +
                "• В настройках PS5: DNS = 192.168.1.1 (AdGuard), MTU = Авто (1500), сервер в WoT = EU.",
            color = Color(0xFFCBD5E1),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun DevicesPage(
    data: JSONObject?,
    search: String,
    onSearchChange: (String) -> Unit,
    onlineOnly: Boolean,
    onOnlineOnlyChange: (Boolean) -> Unit,
    onTopicHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit,
    onPing: (String, String) -> Unit
) {
    val arr = data?.optJSONArray("devices") ?: JSONArray()
    val all = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    val onlineCnt = all.count { it.optString("link") == "up" }
    val q = search.trim().lowercase()
    val list = all.filter { dev ->
        (!onlineOnly || dev.optString("link") == "up") &&
            (q.isEmpty() || dev.optString("name").lowercase().contains(q) || dev.optString("hostname").lowercase().contains(q) || dev.optString("ip").contains(q) || dev.optString("mac").lowercase().contains(q))
    }.sortedByDescending { it.optString("link") == "up" }

    MiniCard("Клиенты LAN/Wi-Fi ($onlineCnt / ${all.size})", { onTopicHelp("devices") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = onSearchChange, placeholder = { Text("Поиск (имя/IP/MAC)", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            FilterChip(selected = onlineOnly, onClick = { onOnlineOnlyChange(!onlineOnly) }, label = { Text("Онл ($onlineCnt)", fontSize = 10.sp) })
        }
        list.forEach { dev ->
            val mac = dev.optString("mac")
            val ip = dev.optString("ip", "0.0.0.0")
            val title = dev.optString("name").ifBlank { dev.optString("hostname").ifBlank { mac } }
            val up = dev.optString("link") == "up"
            val spd = dev.optString("speed")
            val denied = dev.optString("policy") == "deny"
            val prio = dev.optInt("prio", 6)
            val cnf = dev.optBoolean("conform", false)
            val ap = dev.optString("ap")
            val rssi = dev.optString("rssi")
            val md = dev.optString("mode")
            val txr = dev.optString("txrate")
            val band = when {
                ap.contains("WifiMaster1") -> "5G ${md.ifBlank { "" }} ${if (txr.isNotBlank()) "${txr}M" else ""} ${if (rssi.isNotBlank()) "(${rssi}dBm)" else ""}"
                ap.contains("WifiMaster0") -> "2.4G ${md.ifBlank { "" }} ${if (txr.isNotBlank()) "${txr}M" else ""} ${if (rssi.isNotBlank()) "(${rssi}dBm)" else ""}"
                spd.isNotBlank() -> "LAN ${spd}M"
                else -> dev.optString("iface").ifBlank { "LAN" }
            }.trim()
            Surface(color = Color(0xFF111827), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$ip • $band", color = Color(0xFF93C5FD), fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("P$prio • ↓${formatBytes(dev.optDouble("rx", 0.0))} ↑${formatBytes(dev.optDouble("tx", 0.0))}", color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (denied) Text("БЛОК", color = Color(0xFFFCA5A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (up) Color(0xFF22C55E) else Color(0xFF475569)))
                        }
                    }
                    if (mac.matches(Regex("""^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$"""))) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { onAction("WoL $title", MonitorScripts.wolCmd(mac)) }, modifier = Modifier.weight(1f).height(24.dp), contentPadding = PaddingValues(0.dp)) { Text("WoL", fontSize = 9.sp) }
                            if (ip != "0.0.0.0" && ip.isNotBlank()) {
                                OutlinedButton(onClick = { onPing("Пинг $title", ip) }, modifier = Modifier.weight(1f).height(24.dp), contentPadding = PaddingValues(0.dp)) { Text("Пинг", fontSize = 9.sp) }
                                OutlinedButton(onClick = { onAction("Фикс IP $ip", MonitorScripts.ndmcSaveCmd("ip dhcp host $mac $ip")) }, modifier = Modifier.weight(1f).height(24.dp), contentPadding = PaddingValues(0.dp)) { Text("Фикс IP", fontSize = 9.sp) }
                            }
                            OutlinedButton(onClick = {
                                val nextP = if (prio == 1) 6 else 1
                                onAction("Приоритет P$nextP", MonitorScripts.setHostPriorityCmd(mac, nextP, cnf))
                            }, modifier = Modifier.weight(1f).height(24.dp), contentPadding = PaddingValues(0.dp)) {
                                Text(if (prio == 1) "P1→P6" else "P1 Высш", fontSize = 9.sp, color = Color(0xFFFCD34D))
                            }
                            OutlinedButton(onClick = {
                                val next = if (denied) "permit" else "deny"
                                onAction(if (denied) "Разблок $title" else "Блок $title", MonitorScripts.ndmcSaveCmd("ip hotspot host $mac $next"))
                            }, modifier = Modifier.weight(1f).height(24.dp), contentPadding = PaddingValues(0.dp)) {
                                Text(if (denied) "Разблок" else "Блок", fontSize = 9.sp, color = if (denied) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
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
    onNewDir: () -> Unit,
    onNewFile: () -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    val marks = listOf("/opt/zapret2", "/opt/zapret2/lists", "/opt/etc/AdGuardHome", "/opt/etc/init.d", "/opt", "/tmp/mnt", "/tmp")
    MiniCard("X-plore Файлы роутера", { onTopicHelp("files") }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            marks.forEach { m ->
                FilterChip(selected = dirPath == m, onClick = { onOpenDir(m) }, label = { Text(m.substringAfterLast('/').ifBlank { "/" }, fontSize = 10.sp) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(dirPath, color = Color(0xFF93C5FD), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onNewDir, modifier = Modifier.size(26.dp)) { Icon(Icons.Rounded.CreateNewFolder, "Папка", tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp)) }
                IconButton(onClick = onNewFile, modifier = Modifier.size(26.dp)) { Icon(Icons.Rounded.NoteAdd, "Файл", tint = Color(0xFF93C5FD), modifier = Modifier.size(16.dp)) }
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
                    .clickable { if (isDir || isLink) onOpenDir(full) else onOpenFile(full) }
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun TorchPage(host: String, onHostChange: (String) -> Unit, out: String, onTopicHelp: (String) -> Unit, onCopy: () -> Unit, onRun: (String) -> Unit) {
    MiniCard("Сетевая диагностика (Torch / Инструменты)", { onTopicHelp("torch") }) {
        OutlinedTextField(value = host, onValueChange = onHostChange, label = { Text("IP или домен", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = { onRun("ping") }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Ping", fontSize = 10.sp) }
            Button(onClick = { onRun("trace") }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Trace", fontSize = 10.sp) }
            Button(onClick = { onRun("dns") }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("DNS", fontSize = 10.sp) }
            Button(onClick = { onRun("http") }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("HTTP", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = { onRun("traffic") }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Трафик (1с)", fontSize = 10.sp) }
            OutlinedButton(onClick = { onRun("conntrack") }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Conntrack", fontSize = 10.sp) }
            OutlinedButton(onClick = { onRun("ports") }, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Порты", fontSize = 10.sp) }
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
        val logBtns = listOf("agh" to "AdGuard", "sched" to "Планир.", "au" to "Автообн.", "tg" to "TG", "http" to "HTTP", "warp" to "WARP", "circ" to "Autocirc", "dmesg" to "dmesg")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            logBtns.forEach { (k, lbl) -> FilterChip(selected = logSrc == k, onClick = { onSelectLog(k) }, label = { Text(lbl, fontSize = 10.sp) }) }
        }
        OutlinedTextField(value = logFilter, onValueChange = onLogFilterChange, placeholder = { Text("Фильтр строк (напр. error)", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.fillMaxWidth())
        val shownLog = if (logFilter.isBlank()) logsOut else logsOut.lines().filter { it.contains(logFilter, ignoreCase = true) }.joinToString("\n").ifBlank { "(нет совпадений)" }
        if (shownLog.isNotBlank()) MonoBox(shownLog, onCopy = { onCopy(shownLog) })
    }
    MiniCard("SSH-терминал") {
        val presets = listOf("free -m", "df -h", "netstat -lnt", "tailscale status", "top -bn1 | head -n 20", "LD_LIBRARY_PATH= /bin/ndmc -c 'show system'")
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
