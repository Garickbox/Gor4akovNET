package ru.garickbox.gor4akovnet

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MonitorPanelScreen(
    prefs: Prefs,
    passport: RouterPassport?,
    stats: SystemStats?,
    services: ServiceStatus?,
    clients: List<ClientHost>,
    backups: List<BackupEntry>,
    onRefreshAll: () -> Unit,
    onBackupSaved: (BackupEntry) -> Unit,
    onBackupDeleted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val host = prefs.routerHost.ifBlank { "192.168.1.1" }
    var diagOut by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showQrModal by remember { mutableStateOf(false) }
    var viewingBackup by remember { mutableStateOf<BackupEntry?>(null) }
    var onlyOnline by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Монитор роутера", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${stats?.hostname ?: passport?.model ?: host} · ${stats?.uptimeStr ?: "-"}", color = DeckColors.Muted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            diagOut = withContext(Dispatchers.IO) {
                                runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxScripts.AUTO_DIAG_REPAIR_SCRIPT) }
                                    .getOrElse { "Ошибка: ${it.message}" }
                            }
                            onRefreshAll()
                            busy = false
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.Build, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Авто-починка", fontSize = 11.5.sp)
                }
                IconButton(onClick = onRefreshAll) {
                    Icon(Icons.Outlined.Refresh, null, tint = DeckColors.Emerald)
                }
            }
        }

        if (diagOut.isNotBlank()) {
            DeckCard(borderColor = DeckColors.Sky.copy(0.5f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Диагностика", color = DeckColors.Sky, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    TextButton(onClick = { diagOut = "" }, contentPadding = PaddingValues(0.dp)) { Text("Скрыть", fontSize = 11.sp) }
                }
                Text(diagOut, color = DeckColors.Text, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
            }
        }

        // System metrics card
        if (stats != null) {
            DeckCard {
                Text("Ресурсы и железо", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("CPU", "${stats.cpuPct}%", "LA ${stats.loadAvg}", Modifier.weight(1f))
                    MetricTile("RAM", "${stats.ramUsedMb}/${stats.ramTotalMb} МБ", "ZRAM ${stats.zramUsedMb}/${stats.zramTotalMb}", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Сессии", "${stats.conntrackCur}", "из ${stats.conntrackMax}", Modifier.weight(1f))
                    MetricTile(
                        "Темп / /opt",
                        if (stats.tempCpuC > 0) "${stats.tempCpuC}°C" else "—",
                        "Своб. ${stats.optFreeMb}/${stats.optTotalMb} МБ",
                        Modifier.weight(1f)
                    )
                }
            }
        }

        // Live Gor4akovNET network card
        DeckCard(borderColor = if (services?.policyActive == true) DeckColors.Emerald.copy(0.5f) else DeckColors.Border) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Живая сеть Gor4akovNET", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text(
                        if (services?.wifiBridgeName?.isNotBlank() == true)
                            "SSID: ${services.wifiSsid.ifBlank { prefs.wifiSsid }} · ${services.wifiBridgeName} (${services.wifiSubnet})"
                        else
                            "Отдельная сеть ещё не развёрнута (настраивается во вкладке «Сетапы»)",
                        color = DeckColors.Muted,
                        fontSize = 12.sp
                    )
                }
                if (services?.wifiBridgeName?.isNotBlank() == true) {
                    IconButton(onClick = { showQrModal = true }) {
                        Icon(Icons.Outlined.QrCode2, null, tint = DeckColors.Emerald)
                    }
                }
            }
            if (services != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusTag("nfqws2: ${services.nfqwsCount}", if (services.z2kRunning) DeckColors.Emerald else DeckColors.Rose)
                    StatusTag("iptables: ${services.iptablesRules}", if (services.iptablesRules > 0) DeckColors.Emerald else DeckColors.Amber)
                    StatusTag(if (services.aghRunning) "AGH ON" else "AGH OFF", if (services.aghRunning) DeckColors.Emerald else DeckColors.Muted)
                    StatusTag(if (services.warpRunning) "WARP ON" else "WARP OFF", if (services.warpRunning) DeckColors.Sky else DeckColors.Muted)
                }
            }
        }

        // Clients list (universal, no hardcoded PS5/personal devices)
        DeckCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Устройства в сети (${clients.count { !onlyOnline || it.isOnline }})", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Онлайн", color = DeckColors.Muted, fontSize = 11.5.sp)
                    Switch(checked = onlyOnline, onCheckedChange = { onlyOnline = it }, modifier = Modifier.padding(start = 4.dp))
                }
            }

            val shown = clients.filter { !onlyOnline || it.isOnline }
            if (shown.isEmpty()) {
                Text("Нет данных об устройствах (проверьте SSH :${prefs.sshPort})", color = DeckColors.Muted, fontSize = 12.sp)
            } else {
                shown.forEach { c ->
                    Surface(
                        color = DeckColors.CardAlt,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (c.inGor4akov) DeckColors.Emerald.copy(0.45f) else DeckColors.Border)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.name, color = DeckColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${c.ip} · ${c.mac} · ${c.segment}", color = DeckColors.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                                StatusTag(
                                    if (c.inGor4akov) "GOR4AKOVNET" else c.policy,
                                    if (c.inGor4akov) DeckColors.Emerald else DeckColors.Muted
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxScripts.toggleDevicePolicy(c.mac, !c.inGor4akov))
                                                }
                                            }
                                            onRefreshAll()
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(if (c.inGor4akov) "Убрать из обхода" else "Включить обход Gor4akovNET", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxScripts.toggleDeviceQos(c.mac, true))
                                                }
                                            }
                                            onRefreshAll()
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("QoS P1", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Backups section
        DeckCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Резервные копии (${backups.size})", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            RciClient.fetchRunningConfig(host, prefs.rciUser, prefs.rciPass).onSuccess { cfg ->
                                onBackupSaved(prefs.saveBackup("Ручной снимок", passport?.model ?: "Router", cfg))
                            }
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("+ Сделать бэкап", fontSize = 11.5.sp)
                }
            }
            if (backups.isEmpty()) {
                Text("Пока нет сохранённых снимков.", color = DeckColors.Muted, fontSize = 12.sp)
            } else {
                backups.take(6).forEach { b ->
                    Surface(color = DeckColors.CardAlt, shape = RoundedCornerShape(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(b.label, color = DeckColors.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                Text("${b.timestamp} · ${b.model} · ${b.sizeBytes / 1024} КБ", color = DeckColors.Muted, fontSize = 11.sp)
                            }
                            TextButton(onClick = { viewingBackup = b }) { Text("Открыть", fontSize = 11.5.sp) }
                            IconButton(onClick = { onBackupDeleted(b.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, null, tint = DeckColors.Rose, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrModal) {
        AlertDialog(
            onDismissRequest = { showQrModal = false },
            containerColor = DeckColors.Bg,
            title = { Text("QR-код Wi-Fi Gor4akovNET", color = DeckColors.Text) },
            text = {
                WiFiQrCard(
                    ssid = services?.wifiSsid?.ifBlank { prefs.wifiSsid } ?: prefs.wifiSsid,
                    pass = prefs.wifiPass,
                    subnetInfo = services?.wifiSubnet?.ifBlank { "192.168.77.0/24" } ?: "192.168.77.0/24"
                )
            },
            confirmButton = {
                TextButton(onClick = { showQrModal = false }) { Text("Закрыть") }
            }
        )
    }

    viewingBackup?.let { b ->
        val txt = remember(b) { prefs.readBackupText(b) }
        AlertDialog(
            onDismissRequest = { viewingBackup = null },
            containerColor = DeckColors.Card,
            title = { Text(b.label, color = DeckColors.Text, fontSize = 15.sp) },
            text = {
                Box(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(txt.take(12000), color = DeckColors.Emerald, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingBackup = null }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun MetricTile(title: String, value: String, sub: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = DeckColors.CardAlt,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, DeckColors.Border)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = DeckColors.Muted, fontSize = 11.sp)
            Text(value, color = DeckColors.Emerald, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            Text(sub, color = DeckColors.Muted, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
