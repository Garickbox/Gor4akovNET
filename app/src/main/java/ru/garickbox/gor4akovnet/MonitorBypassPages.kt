@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ru.garickbox.gor4akovnet

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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun BypassModesPage(
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

    val polNameJson = pol.optString("name", "").trim()
    val polExJson = pol.optInt("exclude", 0) == 1
    MiniCard("Политика маршрутизации") {
        Text(
            "Обход работает ГЛОБАЛЬНО через NFQUEUE на WAN — для всех сетей (Home, Guest, Gor4akovNET) сразу. " +
                "POLICY_NAME в конфиге zapret2 не используется: он включает CONNMARK-exclude и глушит обход вне указанной политики.",
            color = DeckColors.Muted,
            fontSize = 11.sp
        )
        if (polNameJson.isNotBlank()) {
            MiniRow("POLICY_NAME в config", polNameJson, Color(0xFFFBBF24))
            MiniRow("EXCLUDE", if (polExJson) "1" else "0", Color(0xFFFBBF24))
            Button(
                onClick = { onAction("Сбросить POLICY_NAME", MonitorScripts.setPolicyCmd("", 0)) },
                modifier = Modifier.fillMaxWidth().height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) {
                Text("Сбросить POLICY_NAME (обход во всех сетях)", fontSize = 11.sp)
            }
        } else {
            MiniRow("POLICY_NAME", "не задан — обход во всех сетях", Color(0xFF4ADE80))
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
internal fun WarpPage(
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
internal fun BypassListsPage(
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
                        val pickVerd = parsedPick?.optString("verdict").orEmpty()
                        val pickReason = parsedPick?.optString("reason").orEmpty()
                        val pickOK = pickVerd == "poisonable" || pickVerd.isBlank()
                        Text(
                            text = if (pickOK) "Найдена рабочая стратегия:" else "Подобрано, но не работает ($pickVerd):",
                            color = if (pickOK) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(extractedStrategy, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        if (pickReason.isNotBlank()) Text(pickReason, color = Color(0xFF94A3B8), fontSize = 9.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("rkn_tcp", "yt_tcp", "gv_tcp", "quic", "discord_udp").forEach { p ->
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
