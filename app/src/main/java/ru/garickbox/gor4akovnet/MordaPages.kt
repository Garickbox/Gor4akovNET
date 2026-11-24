package ru.garickbox.gor4akovnet

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MordaPagesScreen(prefs: Prefs, onServicesRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val host = prefs.routerHost.ifBlank { "192.168.1.1" }
    var subTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("z2k PRO", "AdGuard", "NDMS / init.d", "Консоль")

    // z2k states
    var z2kVer by remember { mutableStateOf("-") }
    var z2kNfq by remember { mutableIntStateOf(0) }
    var policyIso by remember { mutableStateOf(prefs.isolatedMode) }
    var disableIpv6 by remember { mutableStateOf(false) }
    var gameWarp by remember { mutableStateOf(false) }
    var discordVoice by remember { mutableStateOf(true) }
    var quicSilence by remember { mutableStateOf(true) }
    var listInfo by remember { mutableStateOf("") }
    var editingListKey by remember { mutableStateOf<String?>(null) }
    var editingListText by remember { mutableStateOf("") }

    // AGH states
    var aghInst by remember { mutableStateOf(false) }
    var aghRun by remember { mutableStateOf(false) }
    var aghOv by remember { mutableStateOf(false) }
    var aghPort by remember { mutableIntStateOf(3000) }
    var aghLogs by remember { mutableStateOf<List<AghQueryItem>>(emptyList()) }

    // NDMS & init.d
    var portForwards by remember { mutableStateOf<List<PortForwardRule>>(emptyList()) }
    var initdItems by remember { mutableStateOf<List<InitServiceItem>>(emptyList()) }

    // Console & syslog
    var sshCmd by remember { mutableStateOf("ps | grep -E 'nfqws|AdGuard|dropbear'") }
    var consoleOut by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun loadZ2k() {
        scope.launch {
            busy = true
            val out = withContext(Dispatchers.IO) {
                runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.Z2K_PRO_STATUS) }.getOrNull()
            } ?: ""
            val counts = mutableListOf<String>()
            out.lines().forEach { line ->
                when {
                    line.startsWith("META|") -> {
                        z2kVer = line.substringAfter("ver=").substringBefore("|")
                        z2kNfq = line.substringAfter("nfq=").toIntOrNull() ?: 0
                    }
                    line.startsWith("CFG|POLICY_NAME=") -> policyIso = line.contains("Gor4akovNET")
                    line.startsWith("CFG|DISABLE_IPV6=") -> disableIpv6 = line.endsWith("=1")
                    line.startsWith("CFG|GAME_WARP_ENABLED=") -> gameWarp = line.endsWith("=1")
                    line.startsWith("CFG|Z2K_CATEGORY_DISCORD_VOICE=") -> discordVoice = !line.endsWith("=0")
                    line.startsWith("CFG|Z2K_QUIC_SILENCE=") -> quicSilence = !line.endsWith("=0")
                    line.startsWith("LIST_CNT|") -> counts.add(line.removePrefix("LIST_CNT|"))
                }
            }
            listInfo = counts.joinToString(" · ")
            busy = false
        }
    }

    fun loadAgh() {
        scope.launch {
            busy = true
            val out = withContext(Dispatchers.IO) {
                runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.AGH_STATUS_AND_LOG) }.getOrNull()
            } ?: ""
            val items = mutableListOf<AghQueryItem>()
            out.lines().forEach { line ->
                if (line.startsWith("AGH_META|")) {
                    aghInst = line.contains("inst=1")
                    aghRun = line.contains("run=1")
                    aghOv = line.contains("ov=1")
                    aghPort = line.substringAfter("port=").toIntOrNull() ?: 3000
                } else if (line.startsWith("QLOG|")) {
                    val p = line.split("|")
                    if (p.size >= 7) {
                        items.add(AghQueryItem(p[1], p[2], p[3], p[4], p[5] == "1", p[6]))
                    }
                }
            }
            aghLogs = items.reversed()
            busy = false
        }
    }

    fun loadNdms() {
        scope.launch {
            busy = true
            val out = withContext(Dispatchers.IO) {
                runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.NDMS_PORTS_AND_INITD) }.getOrNull()
            } ?: ""
            val pfs = mutableListOf<PortForwardRule>()
            val inits = mutableListOf<InitServiceItem>()
            out.lines().forEach { line ->
                val p = line.split("|")
                if (p[0] == "PF" && p.size >= 3) {
                    pfs.add(PortForwardRule(p[1], "NAT", "-", "-", "-", p[2]))
                } else if (p[0] == "INITD" && p.size >= 4) {
                    inits.add(InitServiceItem(p[1], p[2] == "1", p[3] == "1"))
                }
            }
            portForwards = pfs
            initdItems = inits
            busy = false
        }
    }

    LaunchedEffect(subTab) {
        when (subTab) {
            0 -> loadZ2k()
            1 -> loadAgh()
            2 -> loadNdms()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab, containerColor = DeckColors.Card, contentColor = DeckColors.Emerald) {
            tabs.forEachIndexed { idx, title ->
                Tab(selected = subTab == idx, onClick = { subTab = idx }, text = { Text(title, fontSize = 11.5.sp, maxLines = 1) })
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (subTab) {
                0 -> {
                    DeckCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("z2k PRO ($z2kVer)", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Процессов nfqws2: $z2kNfq · $listInfo", color = DeckColors.Muted, fontSize = 11.5.sp)
                            }
                            IconButton(onClick = { loadZ2k() }) { Icon(Icons.Outlined.Refresh, null, tint = DeckColors.Emerald) }
                        }

                        ToggleLine("Изолировать только сеть Gor4akovNET", "POLICY_NAME='Gor4akovNET', остальные в Home не трогаем", policyIso) { policyIso = it }
                        ToggleLine("Discord Voice / UDP обход", "Включает стратегии для звонков Discord", discordVoice) { discordVoice = it }
                        ToggleLine("Глушить QUIC (UDP 443) для браузеров", "Ускоряет стабильный TCP-обход YouTube", quicSilence) { quicSilence = it }
                        ToggleLine("Cloudflare WARP для заблокированных IP", "GAME_WARP_ENABLED в z2k", gameWarp) { gameWarp = it }
                        ToggleLine("Отключить IPv6 в обходе", "Иногда помогает при кривом IPv6 у провайдера", disableIpv6) { disableIpv6 = it }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                Gboxcore.runRouterSSH(
                                                    host, prefs.sshPort, prefs.sshUser, prefs.sshPass,
                                                    GboxMordaScripts.z2kSaveFlagsAndRestart(policyIso, disableIpv6, gameWarp, discordVoice, quicSilence)
                                                )
                                            }
                                        }
                                        loadZ2k()
                                        onServicesRefresh()
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Применить и перезапустить")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        consoleOut = withContext(Dispatchers.IO) {
                                            runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.Z2K_ONLINE_UPDATE) }
                                                .getOrElse { it.message ?: "err" }
                                        }
                                        loadZ2k()
                                        onServicesRefresh()
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Обновить z2k")
                            }
                        }
                    }

                    DeckCard {
                        Text("Пользовательские списки доменов", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("whitelist" to "Whitelist", "extra" to "Extra", "exclude" to "Исключения").forEach { (k, lbl) ->
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val txt = withContext(Dispatchers.IO) {
                                                runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.readZ2kList(k)) }.getOrDefault("")
                                            }
                                            editingListKey = k
                                            editingListText = txt
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text(lbl, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    DeckCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("AdGuard Home (Антиреклама DNS)", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    when {
                                        !aghInst -> "Не установлен (опция: можно доустановить сейчас)"
                                        aghRun -> "Работает (порт :$aghPort · dns-override: ${if (aghOv) "ВКЛ" else "ВЫКЛ"})"
                                        else -> "Установлен, но остановлен"
                                    },
                                    color = DeckColors.Muted,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { loadAgh() }) { Icon(Icons.Outlined.Refresh, null, tint = DeckColors.Emerald) }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        withContext(Dispatchers.IO) {
                                            runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.AGH_INSTALL_OR_TOGGLE) }
                                        }
                                        loadAgh()
                                        onServicesRefresh()
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (aghInst) "Вкл/Перезапустить AGH" else "Установить AdGuardHome")
                            }
                            if (aghInst) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$host:$aghPort"))) }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Веб :$aghPort")
                                }
                            }
                        }

                        if (aghInst) {
                            Text("Быстрая смена DoH Upstream DNS:", color = DeckColors.Muted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("cloudflare" to "1.1.1.1", "quad9" to "Quad9", "adguard" to "AdGuard", "google" to "Google").forEach { (id, lbl) ->
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.aghSetUpstreams(id)) }
                                                }
                                                loadAgh()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(4.dp)
                                    ) { Text(lbl, fontSize = 11.sp) }
                                }
                            }
                        }
                    }

                    if (aghLogs.isNotEmpty()) {
                        DeckCard {
                            Text("Живой журнал DNS-запросов (последние 35)", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            aghLogs.forEach { q ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(q.domain, color = if (q.blocked) DeckColors.Rose else DeckColors.Text, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
                                        Text("${q.time} · ${q.client} · ${q.qtype}", color = DeckColors.Muted, fontSize = 10.sp)
                                    }
                                    StatusTag(if (q.blocked) "BLOCK" else "OK", if (q.blocked) DeckColors.Rose else DeckColors.Emerald)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    DeckCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Службы автозапуска (/opt/etc/init.d)", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            IconButton(onClick = { loadNdms() }) { Icon(Icons.Outlined.Refresh, null, tint = DeckColors.Emerald) }
                        }
                        initdItems.forEach { svc ->
                            Surface(color = DeckColors.CardAlt, shape = RoundedCornerShape(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(svc.name, color = DeckColors.Text, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp)
                                        Text("Автозапуск: ${if (svc.enabled) "ВКЛ" else "ВЫКЛ"}", color = DeckColors.Muted, fontSize = 11.sp)
                                    }
                                    StatusTag(if (svc.running) "RUN" else "STOP", if (svc.running) DeckColors.Emerald else DeckColors.Muted)
                                    Spacer(Modifier.width(6.dp))
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.initdAction(svc.name, "restart")) }
                                                }
                                                loadNdms()
                                                onServicesRefresh()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) { Text("Рестарт", fontSize = 11.sp) }
                                }
                            }
                        }
                    }

                    DeckCard {
                        Text("Проброс портов NDMS (ip static)", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (portForwards.isEmpty()) {
                            Text("Активных правил проброса портов нет.", color = DeckColors.Muted, fontSize = 12.sp)
                        } else {
                            portForwards.forEach { pf ->
                                Text("• ${pf.desc}", color = DeckColors.Sky, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }

                3 -> {
                    DeckCard {
                        Text("SSH Консоль и Системный журнал", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        OutlinedTextField(
                            value = sshCmd,
                            onValueChange = { sshCmd = it },
                            label = { Text("Команда SSH на роутер") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        consoleOut = withContext(Dispatchers.IO) {
                                            runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, sshCmd) }
                                                .getOrElse { "Ошибка: ${it.message}" }
                                        }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Выполнить") }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        consoleOut = withContext(Dispatchers.IO) {
                                            runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.SYSLOG_TAIL) }
                                                .getOrElse { "Ошибка: ${it.message}" }
                                        }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Syslog") }
                        }
                        if (consoleOut.isNotBlank()) {
                            Surface(color = DeckColors.Bg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, DeckColors.Border)) {
                                Text(consoleOut.takeLast(8000), color = DeckColors.Emerald, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(10.dp).fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    editingListKey?.let { key ->
        AlertDialog(
            onDismissRequest = { editingListKey = null },
            containerColor = DeckColors.Card,
            title = { Text("Редактировать $key.txt", color = DeckColors.Text, fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = editingListText,
                    onValueChange = { editingListText = it },
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val b64 = Base64.encodeToString(editingListText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    editingListKey = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.writeZ2kList(key, b64)) }
                        }
                        loadZ2k()
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editingListKey = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ToggleLine(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = DeckColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = DeckColors.Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
