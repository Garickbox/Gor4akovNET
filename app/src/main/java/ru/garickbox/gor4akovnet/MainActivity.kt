package ru.garickbox.gor4akovnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(applicationContext)
        setContent {
            Gor4akovTheme {
                Gor4akovRootApp(prefs)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gor4akovRootApp(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var privileged by remember { mutableStateOf(prefs.privilegedMode) }

    if (!unlocked) {
        AppLockScreen(
            isFirstSetup = !prefs.hasAppPin,
            onUnlocked = { priv ->
                if (priv) privileged = true
                unlocked = true
            },
            verifyPin = { prefs.verifyAppPin(it) },
            createPin = { prefs.setAppPin(it) }
        )
        return
    }

    var tab by remember { mutableIntStateOf(if (prefs.wizardCompleted) 1 else 0) }
    var passport by remember { mutableStateOf<RouterPassport?>(null) }
    var services by remember { mutableStateOf<ServiceStatus?>(null) }
    var backups by remember { mutableStateOf(prefs.listBackups()) }
    var showHelp by remember { mutableStateOf(false) }

    var titleTaps by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    var showPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var showPinText by remember { mutableStateOf(false) }
    var pinErr by remember { mutableStateOf("") }

    fun refreshAllTelemetry() {
        val host = prefs.routerHost.ifBlank { return }
        scope.launch {
            withContext(Dispatchers.IO) {
                if (Gboxcore.probeTCP(host, prefs.sshPort, 900)) {
                    runCatching {
                        val svcRaw = Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxScripts.SERVICE_RADAR_SCRIPT)
                        svcRaw.lines().firstOrNull { it.startsWith("SVC|") }?.let { line ->
                            val p = line.split("|")
                            if (p.size >= 19) {
                                services = ServiceStatus(
                                    entwareOk = p[1] == "1",
                                    z2kInstalled = p[2] == "1",
                                    z2kRunning = p[3] == "1",
                                    z2kMode = p[4],
                                    nfqwsCount = p[5].toIntOrNull() ?: 0,
                                    iptablesRules = p[6].toIntOrNull() ?: 0,
                                    aghInstalled = p[7] == "1",
                                    aghRunning = p[8] == "1",
                                    aghDnsOverride = p[9] == "1",
                                    warpInstalled = p[10] == "1",
                                    warpRunning = p[11] == "1",
                                    tgProxyRunning = p[12] == "1",
                                    dropbearRunning = p[13] == "1",
                                    policyName = p[14],
                                    policyActive = p[15] == "1",
                                    wifiBridgeName = p[16],
                                    wifiSubnet = p[17],
                                    wifiSsid = p[18],
                                    installMarker = p.getOrNull(19) == "1",
                                    wifiPass = p.getOrNull(20)?.trim().orEmpty()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        Telemetry.recordOpen(prefs, passport?.model ?: "", passport?.ndmsTitle ?: "")
    }

    // Одноразовая чистка legacy-хука 090-gor4akov-policy.sh на роутере при старте.
    // Для тех, кто обновил APK, но ещё не запускал установку заново: хук от старых
    // версий мог остаться в /opt/etc/ndm/netfilter.d/ и при ребуте роутера NDMS
    // применит его — CONNMARK-exclude заглушит обход в Home/Guest. Удаляем тихо.
    LaunchedEffect(Unit) {
        val host = prefs.routerHost.ifBlank { return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            runCatching {
                if (Gboxcore.probeTCP(host, prefs.sshPort, 700)) {
                    Gboxcore.runRouterSSH(
                        host, prefs.sshPort, prefs.sshUser, prefs.sshPass,
                        "rm -f /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh 2>/dev/null; " +
                            "sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d' /opt/zapret2/config 2>/dev/null; " +
                            "echo HOOK_CLEANED"
                    )
                }
            }
        }
    }

    LaunchedEffect(tab) {
        if (tab == 1 || tab == 2) refreshAllTelemetry()
    }

    Scaffold(
        containerColor = DeckColors.Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeckColors.Card),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Gor4akovNET",
                            color = DeckColors.Emerald,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val now = System.currentTimeMillis()
                                titleTaps = if (now - lastTapMs < 2000L) titleTaps + 1 else 1
                                lastTapMs = now
                                if (titleTaps >= 5) {
                                    titleTaps = 0
                                    newPinInput = ""
                                    pinErr = ""
                                    showPinDialog = true
                                }
                            }
                        )
                        passport?.let {
                            StatusTag(it.model, DeckColors.Sky)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "Справка", tint = DeckColors.Text)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = DeckColors.Card) {
                val baseItems = mutableListOf(
                    Triple("Подключение", Icons.Outlined.Cable, 0),
                    Triple("Монитор", Icons.Outlined.MonitorHeart, 1),
                    Triple("Сетапы", Icons.Outlined.RocketLaunch, 2)
                )
                if (privileged) {
                    baseItems.add(Triple("Статистика", Icons.Outlined.QueryStats, 3))
                }
                baseItems.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.5.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DeckColors.Emerald,
                            selectedTextColor = DeckColors.Emerald,
                            indicatorColor = DeckColors.Emerald.copy(alpha = 0.14f)
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(modifier = Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> ConnectWizardScreen(
                    prefs = prefs,
                    passport = passport,
                    onPassportUpdated = { p ->
                        passport = p
                        scope.launch { Telemetry.recordOpen(prefs, p.model, p.ndmsTitle) }
                        if (p.ssh222Open) refreshAllTelemetry()
                    },
                    onBackupSaved = { b -> backups = listOf(b) + backups },
                    onGoToSetups = { tab = 2 },
                    onGoToMonitor = { tab = 1 }
                )
                1 -> MonitorPanelScreen(
                    prefs = prefs,
                    passport = passport,
                    onBackupSaved = { b -> backups = listOf(b) + backups },
                    onBack = { tab = 0 }
                )
                2 -> SetupsScreen(
                    prefs = prefs,
                    passport = passport,
                    services = services,
                    onServicesRefresh = { refreshAllTelemetry() },
                    onBackupSaved = { b -> backups = listOf(b) + backups }
                )
                3 -> if (privileged) {
                    StatsScreen(prefs = prefs)
                } else {
                    tab = 0
                }
            }
        }
    }

    if (showHelp) {
        HelpDialog { showHelp = false }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = DeckColors.Card,
            title = { Text("Пароль приложения", color = DeckColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите новый пароль доступа (от 4 символов):", color = DeckColors.Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { newPinInput = it; pinErr = "" },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = if (showPinText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPinText = !showPinText }) {
                                Icon(if (showPinText) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinErr.isNotBlank()) Text(pinErr, color = DeckColors.Rose, fontSize = 11.5.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = newPinInput.trim()
                    if (clean.length < 4) {
                        pinErr = "Минимум 4 символа"
                        return@TextButton
                    }
                    val isPriv = prefs.setAppPin(clean)
                    privileged = isPriv
                    if (!isPriv && tab == 3) tab = 0
                    showPinDialog = false
                }) { Text("Сохранить", color = DeckColors.Emerald) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Отмена") }
            }
        )
    }
}
