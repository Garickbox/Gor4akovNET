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
internal fun WifiPage(
    data: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onAction: (String, String) -> Unit
) {
    val wifi = data?.optJSONObject("wifi") ?: JSONObject()
    val aps = wifi.optJSONArray("aps") ?: JSONArray()

    MiniCard("Радиомодули Wi-Fi", { onTopicHelp("wifi") }) {
        val g24Ssid = wifi.optString("g24_ssid").ifBlank { "Home" }
        val g24Ch = wifi.optString("g24_ch").ifBlank { "—" }
        val g24Bw = wifi.optString("g24_bw").ifBlank { "—" }
        val g24Temp = wifi.optString("g24_temp").ifBlank { "—" }
        MiniRow("$g24Ssid (2.4 ГГц)", "${if (wifi.optBoolean("g24_up")) "ON" else "OFF"} • кан.$g24Ch • ${g24Bw}M • ${wifi.optInt("g24_rate", 0)}M • ${g24Temp}°C", if (wifi.optBoolean("g24_up")) Color(0xFF4ADE80) else Color(0xFFF87171))
        val bz24 = wifi.optString("g24_busy")
        if (bz24.isNotBlank()) Text("Занятые 2.4G: $bz24", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        val g5Ssid = wifi.optString("g5_ssid").ifBlank { "Home" }
        val g5Ch = wifi.optString("g5_ch").ifBlank { "—" }
        val g5Bw = wifi.optString("g5_bw").ifBlank { "—" }
        val g5Temp = wifi.optString("g5_temp").ifBlank { "—" }
        MiniRow("$g5Ssid (5 ГГц)", "${if (wifi.optBoolean("g5_up")) "ON" else "OFF"} • кан.$g5Ch • ${g5Bw}M • ${wifi.optInt("g5_rate", 0)}M • ${g5Temp}°C", if (wifi.optBoolean("g5_up")) Color(0xFF4ADE80) else Color(0xFFF87171))
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
        Text("5 ГГц (без DFS: 36–48 • ширина авто или явная):", color = Color(0xFF94A3B8), fontSize = 10.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("auto", "36", "40", "44", "48", "52", "149").forEach { ch ->
                OutlinedButton(onClick = {
                    val cmd = if (ch == "auto") {
                        MonitorScripts.ndmcSaveCmd("interface WifiMaster1 channel auto", "no interface WifiMaster1 channel width")
                    } else {
                        MonitorScripts.ndmcSaveCmd("interface WifiMaster1 channel $ch", "no interface WifiMaster1 channel width")
                    }
                    onAction("5G канал $ch", cmd)
                }, modifier = Modifier.height(26.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Кан.$ch", fontSize = 10.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("auto" to "Авто-ширина", "20" to "20 МГц", "40-above" to "40 МГц", "160" to "160 МГц").forEach { (bw, lbl) ->
                OutlinedButton(onClick = {
                    val cmd = if (bw == "auto") {
                        MonitorScripts.ndmcSaveCmd("no interface WifiMaster1 channel width")
                    } else {
                        MonitorScripts.ndmcSaveCmd("interface WifiMaster1 channel width $bw")
                    }
                    onAction("5G ширина $lbl", cmd)
                }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text(lbl, fontSize = 9.5.sp) }
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
internal fun DevicesPage(
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
internal fun DeviceManageDialog(
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
