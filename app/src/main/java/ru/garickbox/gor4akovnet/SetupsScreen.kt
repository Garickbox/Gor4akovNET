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
fun SetupsScreen(
    prefs: Prefs,
    passport: RouterPassport?,
    services: ServiceStatus?,
    onServicesRefresh: () -> Unit,
    onBackupSaved: (BackupEntry) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var wifiSsid by remember { mutableStateOf(prefs.wifiSsid) }
    var wifiPass by remember { mutableStateOf(prefs.wifiPass) }
    var isolatedMode by remember { mutableStateOf(prefs.isolatedMode) }
    var optAdguard by remember { mutableStateOf(prefs.optAdguard) }
    var optWarp by remember { mutableStateOf(prefs.optWarp) }

    var installing by remember { mutableStateOf(false) }
    var installLog by remember { mutableStateOf("") }
    var verifyOut by remember { mutableStateOf("") }
    var confirmRollback by remember { mutableStateOf(false) }
    var removeZapretToo by remember { mutableStateOf(false) }

    val sshReady = passport?.ssh222Open == true || Gboxcore.probeTCP(prefs.routerHost.ifBlank { "192.168.1.1" }, prefs.sshPort, 400)
    val host = prefs.routerHost.ifBlank { "192.168.1.1" }

    fun appendLog(line: String) {
        installLog = if (installLog.isBlank()) line else "$installLog\n$line"
    }

    fun runOneClickInstall() {
        scope.launch {
            installing = true
            verifyOut = ""
            installLog = "Старт установки «Обход Gor4akovNET»..."
            prefs.wifiSsid = wifiSsid
            prefs.wifiPass = wifiPass
            prefs.isolatedMode = isolatedMode
            prefs.optAdguard = optAdguard
            prefs.optWarp = optWarp

            // 1. Pre-install backup
            RciClient.fetchRunningConfig(host, prefs.rciUser, prefs.rciPass).onSuccess { cfg ->
                val b = prefs.saveBackup("Перед Gor4akovNET", passport?.model ?: "Router", cfg)
                onBackupSaved(b)
                appendLog("[OK] Бэкап перед установкой сохранён (${b.sizeBytes / 1024} КБ)")
            }

            // 2. Push offline payloads from APK -> /opt/gor4akov-offline/
            val arch = passport?.entwareArch ?: "aarch64-k3.10"
            val pushRes = OfflineAssets.pushOfflineBypassBundleOverSSH(
                context = context,
                host = host,
                sshPort = prefs.sshPort,
                sshUser = prefs.sshUser,
                sshPass = prefs.sshPass,
                entwareArch = arch
            ) { msg -> appendLog(msg) }

            if (pushRes.isFailure) {
                appendLog("[ERR] Ошибка передачи файлов по SSH: ${pushRes.exceptionOrNull()?.message}")
                installing = false
                return@launch
            }

            // 3. Execute remote setup script
            appendLog("Запуск установки на роутере (обычно 30–90 сек)...")
            val script = GboxScripts.buildOfflineAndBypassInstallScript(
                ssid = wifiSsid,
                wifiPass = wifiPass,
                isolatedMode = isolatedMode,
                installAdguard = optAdguard,
                enableWarp = optWarp
            )
            val execRes = withContext(Dispatchers.IO) {
                runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, script) }
            }
            execRes.onSuccess { out ->
                appendLog(out)
                onServicesRefresh()
            }.onFailure { err ->
                appendLog("[ERR] ${err.message}")
            }
            installing = false
        }
    }

    fun runVerify() {
        scope.launch {
            installing = true
            verifyOut = "Проверка работы обхода и доступности сайтов..."
            val res = withContext(Dispatchers.IO) {
                runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxScripts.VERIFY_BYPASS_SCRIPT) }
            }
            verifyOut = res.getOrElse { "Ошибка проверки: ${it.message}" }
            installing = false
        }
    }

    fun runOnlineUpdate() {
        scope.launch {
            installing = true
            appendLog("Запуск онлайн-обновления z2k-enhanced...")
            val res = withContext(Dispatchers.IO) {
                runCatching { Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxMordaScripts.Z2K_ONLINE_UPDATE) }
            }
            appendLog(res.getOrElse { "Ошибка обновления: ${it.message}" })
            onServicesRefresh()
            installing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Каталог сетапов", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Готовые конфигурации роутера в 1 клик без поломки основной домашней сети.", color = DeckColors.Muted, fontSize = 12.5.sp)

        if (!sshReady) {
            DeckCard(borderColor = DeckColors.Amber.copy(0.55f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = DeckColors.Amber)
                    Text("Сначала завершите подключение во вкладке «Подключение» (нужен SSH :${prefs.sshPort})", color = DeckColors.Amber, fontSize = 12.5.sp)
                }
            }
        }

        // Main active preset: Обход Gor4akovNET
        DeckCard(borderColor = DeckColors.Emerald.copy(0.45f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Обход Gor4akovNET", color = DeckColors.Emerald, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("z2k-enhanced (nfqws2) + отдельная Wi-Fi сеть и политика", color = DeckColors.Muted, fontSize = 12.sp)
                }
                StatusTag(
                    if (services?.z2kRunning == true) "АКТИВЕН" else "ПРЕСЕТ #1",
                    if (services?.z2kRunning == true) DeckColors.Emerald else DeckColors.Sky
                )
            }

            if (isolatedMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = { Text("Имя новой Wi-Fi сети") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = wifiPass,
                        onValueChange = { wifiPass = it },
                        label = { Text("Пароль Wi-Fi") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Отдельная сеть Gor4akovNET", color = DeckColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Не трогает основную домашнюю Wi-Fi сеть", color = DeckColors.Muted, fontSize = 11.5.sp)
                }
                Switch(checked = isolatedMode, onCheckedChange = { isolatedMode = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Антиреклама (AdGuard Home)", color = DeckColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Фильтрация рекламы + журнал запросов в Мониторе", color = DeckColors.Muted, fontSize = 11.5.sp)
                }
                Checkbox(checked = optAdguard, onCheckedChange = { optAdguard = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cloudflare WARP для заблокированных IP", color = DeckColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Дополнительный туннель z2k для игр и CDN", color = DeckColors.Muted, fontSize = 11.5.sp)
                }
                Checkbox(checked = optWarp, onCheckedChange = { optWarp = it })
            }

            Button(
                onClick = { runOneClickInstall() },
                enabled = !installing && sshReady,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (installing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Идёт установка...")
                } else {
                    Icon(Icons.Outlined.RocketLaunch, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (services?.z2kInstalled == true) "Переустановить / применить сетап" else "Установить в 1 клик (офлайн из APK)")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { runVerify() },
                    enabled = !installing && sshReady,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Проверить обход", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { runOnlineUpdate() },
                    enabled = !installing && sshReady,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Обновить онлайн", fontSize = 12.sp)
                }
            }

            if (services?.policyActive == true || services?.wifiBridgeName?.isNotBlank() == true || installLog.contains("INSTALL_DONE")) {
                OutlinedButton(
                    onClick = { confirmRollback = true },
                    enabled = !installing && sshReady,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeckColors.Rose),
                    border = BorderStroke(1.dp, DeckColors.Rose.copy(0.5f))
                ) {
                    Icon(Icons.Outlined.Restore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Откатить сеть Gor4akovNET", fontSize = 12.sp)
                }
            }
        }

        if (services?.wifiBridgeName?.isNotBlank() == true || installLog.contains("INSTALL_DONE")) {
            WiFiQrCard(
                ssid = services?.wifiSsid?.ifBlank { wifiSsid } ?: wifiSsid,
                pass = wifiPass,
                subnetInfo = services?.wifiSubnet?.ifBlank { "192.168.77.0/24" } ?: "192.168.77.0/24"
            )
        }

        if (verifyOut.isNotBlank()) {
            DeckCard(borderColor = DeckColors.Sky.copy(0.5f)) {
                Text("Результат проверки", color = DeckColors.Sky, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(verifyOut, color = DeckColors.Text, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
            }
        }

        if (installLog.isNotBlank()) {
            DeckCard {
                Text("Журнал установки", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Surface(color = DeckColors.Bg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = installLog.takeLast(5000),
                        color = DeckColors.Emerald,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp).fillMaxWidth()
                    )
                }
            }
        }

        // Future modular catalog slots
        Text("Будущие модули (в разработке)", color = DeckColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        FutureSetupCard(
            title = "Я Дома VPN",
            subtitle = "Универсальный пресет для личного VPS (VLESS / AmneziaWG / WireGuard) с политикой роуминга и Kill Switch."
        )
        FutureSetupCard(
            title = "Чистый DNS Shield",
            subtitle = "Автономная настройка AdGuard Home + DoH/DoT + шифрованный DNS для всей семьи без обхода DPI."
        )
    }

    if (confirmRollback) {
        AlertDialog(
            onDismissRequest = { confirmRollback = false },
            containerColor = DeckColors.Card,
            title = { Text("Откатить настройку?", color = DeckColors.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Удалит отдельный мост, Wi-Fi точку доступа и политику Gor4akovNET. Домашний Wi-Fi останется нетронутым.", color = DeckColors.Muted, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = removeZapretToo, onCheckedChange = { removeZapretToo = it })
                        Text("Также полностью удалить /opt/zapret2", color = DeckColors.Text, fontSize = 12.5.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRollback = false
                        scope.launch {
                            installing = true
                            val out = withContext(Dispatchers.IO) {
                                runCatching {
                                    Gboxcore.runRouterSSH(
                                        host,
                                        prefs.sshPort,
                                        prefs.sshUser,
                                        prefs.sshPass,
                                        GboxScripts.rollbackGor4akovScript(removeZapretToo)
                                    )
                                }
                            }
                            appendLog(out.getOrElse { "Ошибка отката: ${it.message}" })
                            onServicesRefresh()
                            installing = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeckColors.Rose)
                ) {
                    Text("Откатить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRollback = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun FutureSetupCard(title: String, subtitle: String) {
    DeckCard(borderColor = DeckColors.Border.copy(0.65f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = DeckColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            StatusTag("СКОРО", DeckColors.Muted)
        }
        Text(subtitle, color = DeckColors.Muted, fontSize = 12.sp)
    }
}
