package ru.garickbox.gor4akovnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var tab by remember { mutableIntStateOf(if (prefs.wizardCompleted) 1 else 0) }
    var passport by remember { mutableStateOf<RouterPassport?>(null) }
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var services by remember { mutableStateOf<ServiceStatus?>(null) }
    var clients by remember { mutableStateOf<List<ClientHost>>(emptyList()) }
    var backups by remember { mutableStateOf(prefs.listBackups()) }
    var showHelp by remember { mutableStateOf(false) }

    fun refreshAllTelemetry() {
        val host = prefs.routerHost.ifBlank { return }
        scope.launch {
            withContext(Dispatchers.IO) {
                if (Gboxcore.probeTCP(host, prefs.sshPort, 900)) {
                    runCatching {
                        val dashRaw = Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxScripts.DASHBOARD_SCRIPT)
                        dashRaw.lines().firstOrNull { it.startsWith("DASH|") }?.let { line ->
                            val p = line.split("|")
                            if (p.size >= 20) {
                                stats = SystemStats(
                                    cpuPct = p[1].toIntOrNull() ?: 0,
                                    ramUsedMb = p[2].toIntOrNull() ?: 0,
                                    ramTotalMb = p[3].toIntOrNull() ?: 0,
                                    zramUsedMb = p[4].toIntOrNull() ?: 0,
                                    zramTotalMb = p[5].toIntOrNull() ?: 0,
                                    swapUsedMb = p[6].toIntOrNull() ?: 0,
                                    swapTotalMb = p[7].toIntOrNull() ?: 0,
                                    tempCpuC = p[8].toIntOrNull() ?: 0,
                                    conntrackCur = p[9].toIntOrNull() ?: 0,
                                    conntrackMax = p[10].toIntOrNull() ?: 0,
                                    loadAvg = p[11],
                                    uptimeStr = "${p[12]}д ${p[13]}ч ${p[14]}м",
                                    optFreeMb = p[15].toIntOrNull() ?: 0,
                                    optTotalMb = p[16].toIntOrNull() ?: 0,
                                    optMount = p[17],
                                    arch = p[18],
                                    hostname = p[19],
                                    firmware = p.getOrElse(20) { "-" }
                                )
                            }
                        }
                    }
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
                                    wifiSsid = p[18]
                                )
                            }
                        }
                    }
                    runCatching {
                        val cliRaw = Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, GboxScripts.CLIENTS_SCRIPT)
                        clients = cliRaw.lines()
                            .filter { it.startsWith("HOST|") }
                            .mapNotNull { line ->
                                val p = line.split("|")
                                if (p.size >= 8) {
                                    ClientHost(
                                        mac = p[1],
                                        ip = p[2],
                                        name = p[3],
                                        segment = p[4],
                                        policy = p[5],
                                        qosClass = 0,
                                        isOnline = p[6] == "1",
                                        isStaticDhcp = p[7] == "1"
                                    )
                                } else null
                            }
                    }
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
                        Text("Gor4akovNET", color = DeckColors.Emerald, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
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
                val items = listOf(
                    Triple("Подключение", Icons.Outlined.Cable, 0),
                    Triple("Монитор", Icons.Outlined.MonitorHeart, 1),
                    Triple("Сетапы", Icons.Outlined.RocketLaunch, 2),
                    Triple("Управление", Icons.Outlined.Tune, 3)
                )
                items.forEach { (label, icon, idx) ->
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
                        if (p.ssh222Open) refreshAllTelemetry()
                    },
                    onBackupSaved = { b -> backups = listOf(b) + backups },
                    onGoToSetups = { tab = 2 },
                    onGoToMonitor = { tab = 1 }
                )
                1 -> MonitorPanelScreen(onBack = { tab = 0 })
                2 -> SetupsScreen(
                    prefs = prefs,
                    passport = passport,
                    services = services,
                    onServicesRefresh = { refreshAllTelemetry() },
                    onBackupSaved = { b -> backups = listOf(b) + backups }
                )
                3 -> MordaPagesScreen(
                    prefs = prefs,
                    onServicesRefresh = { refreshAllTelemetry() }
                )
            }
        }
    }

    if (showHelp) {
        HelpDialog { showHelp = false }
    }
}
