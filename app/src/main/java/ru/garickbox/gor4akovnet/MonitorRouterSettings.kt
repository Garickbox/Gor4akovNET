package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun NdmsIfacesPage(
    state: NdmsControlState?,
    inspectOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onReload: () -> Unit,
    onApplyCmds: (String, List<String>) -> Unit,
    onInspectIface: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    var filterCat by remember { mutableStateOf("Все") }
    var search by remember { mutableStateOf("") }
    var confirmToggle by remember { mutableStateOf<Pair<NdmsIfaceInfo, Boolean>?>(null) }
    var editMtuIface by remember { mutableStateOf<NdmsIfaceInfo?>(null) }
    var mtuText by remember { mutableStateOf("") }

    MiniCard("Интерфейсы роутера и VPN (${state?.transport ?: "RCI/SSH"})", { onTopicHelp("ndms_ifaces") }) {
        if (state == null) {
            Text("Загрузка интерфейсов из running-config / RCI...", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
            OutlinedButton(onClick = onReload, enabled = !busy, modifier = Modifier.fillMaxWidth().height(32.dp)) {
                Text("Обновить список", fontSize = 10.5.sp)
            }
            return@MiniCard
        }
        MiniRow("Модель • Транспорт", "${state.model} • ${state.transport}", Color(0xFF86EFAC))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Поиск интерфейса (Wireguard, ISP, Bridge...)", fontSize = 10.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Все", "WAN", "VPN", "LAN", "Wi-Fi").forEach { cat ->
                FilterChip(selected = filterCat == cat, onClick = { filterCat = cat }, label = { Text(cat, fontSize = 9.5.sp) })
            }
        }
    }

    if (state != null) {
        val list = state.interfaces.filter { iface ->
            val matchText = search.isBlank() ||
                iface.id.contains(search, true) ||
                iface.description.contains(search, true) ||
                iface.ip.contains(search, true)
            val matchCat = when (filterCat) {
                "WAN" -> iface.isGlobal || iface.type.contains("WAN") || iface.type.contains("Модем")
                "VPN" -> iface.type in listOf("WireGuard", "OpenVPN", "VPN")
                "LAN" -> iface.type.contains("Bridge")
                "Wi-Fi" -> iface.type.contains("Wi-Fi")
                else -> true
            }
            matchText && matchCat
        }

        list.forEach { iface ->
            val title = if (iface.description.isNotBlank()) "${iface.id} (${iface.description})" else iface.id
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, if (iface.up && iface.connected) Color(0xFF1E3A8A) else Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 6.dp)) {
                            Text(title, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${iface.type}${if (iface.ip.isNotBlank()) " • ${iface.ip}" else ""}${if (iface.mtu > 0) " • MTU ${iface.mtu}" else ""}",
                                color = Color(0xFF93C5FD),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        MiniPill(if (iface.up) (if (iface.connected) "UP" else "WAIT") else "DOWN", iface.up && iface.connected)
                    }
                    WrapRow {
                        val nextUp = !iface.up
                        OutlinedButton(
                            onClick = {
                                val risky = !nextUp && (iface.id.equals("Bridge0", true) || iface.isGlobal)
                                if (risky) {
                                    confirmToggle = iface to false
                                } else {
                                    onApplyCmds(
                                        "${if (nextUp) "Вкл" else "Откл"} ${iface.id}",
                                        listOf("interface ${iface.id} ${if (nextUp) "up" else "down"}")
                                    )
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.height(27.dp),
                            contentPadding = PaddingValues(horizontal = 9.dp)
                        ) {
                            Text(if (iface.up) "Отключить" else "Включить", fontSize = 9.5.sp, color = if (iface.up) Color(0xFFFCA5A5) else Color(0xFF86EFAC))
                        }
                        OutlinedButton(
                            onClick = { onInspectIface(iface.id) },
                            enabled = !busy,
                            modifier = Modifier.height(27.dp),
                            contentPadding = PaddingValues(horizontal = 9.dp)
                        ) { Text("Детали / Peer", fontSize = 9.5.sp) }
                        OutlinedButton(
                            onClick = {
                                editMtuIface = iface
                                mtuText = if (iface.mtu > 0) iface.mtu.toString() else "1420"
                            },
                            enabled = !busy,
                            modifier = Modifier.height(27.dp),
                            contentPadding = PaddingValues(horizontal = 9.dp)
                        ) { Text("MTU", fontSize = 9.5.sp) }
                    }
                }
            }
        }
    }

    if (inspectOut.isNotBlank()) {
        MiniCard("Состояние интерфейса (show interface)") {
            MonoBox(inspectOut, onCopy = { onCopy(inspectOut) })
        }
    }

    confirmToggle?.let { (iface, targetUp) ->
        AlertDialog(
            onDismissRequest = { confirmToggle = null },
            title = { Text("Отключить ${iface.id}?", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Это основной LAN-мост или WAN-интерфейс. Если вы подключены через него, связь с роутером прервётся до ручного включения.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmToggle = null
                        onApplyCmds("Откл ${iface.id}", listOf("interface ${iface.id} ${if (targetUp) "up" else "down"}"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Отключить") }
            },
            dismissButton = { TextButton(onClick = { confirmToggle = null }) { Text("Отмена") } }
        )
    }

    editMtuIface?.let { iface ->
        AlertDialog(
            onDismissRequest = { editMtuIface = null },
            title = { Text("MTU для ${iface.id}", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Рекомендуемые значения: WAN 1492/1500, WireGuard/VPN 1280–1420.", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                    OutlinedTextField(
                        value = mtuText,
                        onValueChange = { mtuText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("MTU (576..1500)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val v = mtuText.toIntOrNull()
                        if (v != null && v in 576..1500) {
                            editMtuIface = null
                            onApplyCmds("MTU ${iface.id}=$v", listOf("interface ${iface.id} ip mtu $v"))
                        }
                    }
                ) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { editMtuIface = null }) { Text("Отмена") } }
        )
    }
}

@Composable
internal fun NdmsPoliciesPage(
    state: NdmsControlState?,
    routeDoctor: JSONObject?,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onRunDoctor: () -> Unit,
    onApplyCmds: (String, List<String>) -> Unit
) {
    var newPolName by remember { mutableStateOf("") }
    var polTarget by remember { mutableStateOf("") }
    var polIface by remember { mutableStateOf("") }
    var routeDest by remember { mutableStateOf("") }
    var routeMask by remember { mutableStateOf("255.255.255.0") }
    var routeVia by remember { mutableStateOf("") }
    var confirmDelPol by remember { mutableStateOf<String?>(null) }

    // 1. Route Inspector & Network Doctor
    MiniCard("Network Doctor & Инспектор маршрутов", { onTopicHelp("ndms_policies") }) {
        Text(
            "Проверяет Linux ip rule, таблицы маршрутизации, реальный исходящий IP по каждому интерфейсу (WAN / WARP / WG) и утечки IPv6/DNS.",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp
        )
        Button(
            onClick = onRunDoctor,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text("Запустить Доктор маршрутов и утечек", fontSize = 10.5.sp)
        }
        if (routeDoctor != null) {
            val egress = routeDoctor.optJSONArray("egress") ?: JSONArray()
            if (egress.length() > 0) {
                Text("Исходящий IP по интерфейсам:", color = Color(0xFF86EFAC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                for (i in 0 until egress.length()) {
                    val e = egress.optJSONObject(i) ?: continue
                    MiniRow(e.optString("iface"), e.optString("ip").ifBlank { "нет ответа" }, Color(0xFF93C5FD))
                }
            }
            val dnsEg = routeDoctor.optString("dns_egress").orEmpty()
            MiniRow("DNS egress (whoami.akamai)", dnsEg.ifBlank { "не определён" })
            val ipv6 = routeDoctor.optJSONArray("ipv6") ?: JSONArray()
            MiniRow("Глобальный IPv6 на роутере", if (ipv6.length() == 0) "Нет (утечки v6 нет)" else ipv6.optString(0), if (ipv6.length() == 0) Color(0xFF4ADE80) else Color(0xFFFBBF24))

            val rules = routeDoctor.optJSONArray("rules") ?: JSONArray()
            if (rules.length() > 0) {
                Text("Linux ip rule (${rules.length()}):", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                MonoBox((0 until rules.length()).joinToString("\n") { rules.optString(it) })
            }
            val tbls = routeDoctor.optJSONArray("tables") ?: JSONArray()
            if (tbls.length() > 0) {
                Text("Таблицы политик (${tbls.length()}):", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                val txt = buildString {
                    for (i in 0 until tbls.length()) {
                        val t = tbls.optJSONObject(i) ?: continue
                        append("[table ${t.optString("table")}]\n")
                        val rs = t.optJSONArray("routes") ?: JSONArray()
                        for (j in 0 until rs.length()) append("  ").append(rs.optString(j)).append('\n')
                    }
                }.trim()
                if (txt.isNotBlank()) MonoBox(txt)
            }
            val mainRt = routeDoctor.optJSONArray("main_routes") ?: JSONArray()
            if (mainRt.length() > 0) {
                Text("Основная таблица маршрутов:", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                MonoBox((0 until mainRt.length()).joinToString("\n") { mainRt.optString(it) })
            }
        }
    }

    // 2. NDMS Policies Builder
    val policies = state?.policies ?: emptyList()
    val availIfaces = state?.interfaces?.filter {
        it.isGlobal || it.type in listOf("WireGuard", "OpenVPN", "VPN", "WAN / Порт", "USB Модем")
    }?.map { it.id } ?: emptyList()

    MiniCard("Политики маршрутизации NDMS (${policies.size})") {
        Text(
            "Обход Zapret2 работает глобально через NFQUEUE на WAN. Политики NDMS нужны, если вы направляете отдельные устройства через WireGuard / второй WAN.",
            color = Color(0xFF94A3B8),
            fontSize = 9.5.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPolName,
                onValueChange = { newPolName = it.replace(Regex("[^A-Za-z0-9_-]"), "") },
                placeholder = { Text("Новая политика (напр. VPN_Work)", fontSize = 10.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newPolName.isNotBlank()) {
                        onApplyCmds("Создать политику $newPolName", listOf("ip policy $newPolName"))
                        polTarget = newPolName
                        newPolName = ""
                    }
                },
                enabled = !busy && newPolName.isNotBlank(),
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("Создать", fontSize = 10.sp) }
        }

        policies.forEach { pol ->
            Surface(
                color = Color(0xFF020617),
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(pol.name, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        WrapRow(Modifier.weight(1f, fill = false)) {
                            OutlinedButton(
                                onClick = { polTarget = pol.name },
                                modifier = Modifier.height(24.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp)
                            ) { Text("+ Интерфейс", fontSize = 9.sp) }
                            if (!pol.name.equals("Policy0", true) && !pol.name.equals("Home", true)) {
                                IconButton(onClick = { confirmDelPol = pol.name }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    if (pol.members.isEmpty()) {
                        Text("Интерфейсы не добавлены", color = Color(0xFF64748B), fontSize = 9.5.sp)
                    } else {
                        pol.members.forEach { (iface, ord) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("• $iface (order $ord)", color = Color(0xFF93C5FD), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                IconButton(
                                    onClick = {
                                        onApplyCmds("Убрать $iface из ${pol.name}", listOf("ip policy ${pol.name} no permit global $iface"))
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (polTarget.isNotBlank()) {
            HorizontalDivider(color = Color(0xFF1E293B))
            Text("Добавить интерфейс в политику «$polTarget»:", color = Color(0xFF86EFAC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (availIfaces.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    availIfaces.forEach { ifc ->
                        AssistChip(onClick = { polIface = ifc }, label = { Text(ifc, fontSize = 9.sp) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = polIface,
                    onValueChange = { polIface = it },
                    placeholder = { Text("Wireguard0 / ISP", fontSize = 10.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (polIface.isNotBlank()) {
                            onApplyCmds("+$polIface в $polTarget", listOf("ip policy $polTarget permit global ${polIface.trim()}"))
                            polIface = ""
                        }
                    },
                    enabled = !busy && polIface.isNotBlank(),
                    modifier = Modifier.height(34.dp)
                ) { Text("Добавить", fontSize = 10.sp) }
            }
        }
    }

    // 3. Static Routes
    val routes = state?.routes ?: emptyList()
    MiniCard("Статические маршруты NDMS (${routes.size})") {
        routes.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${r.dest} -> ${r.target}", color = Color(0xFFE2E8F0), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onApplyCmds("Удалить маршрут", listOf("no ${r.rawCmd}")) },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(value = routeDest, onValueChange = { routeDest = it }, label = { Text("Сеть (IP)", fontSize = 9.sp) }, placeholder = { Text("10.8.0.0", fontSize = 9.5.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedTextField(value = routeMask, onValueChange = { routeMask = it }, label = { Text("Маска", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = routeVia, onValueChange = { routeVia = it }, label = { Text("Шлюз или интерфейс", fontSize = 9.sp) }, placeholder = { Text("Wireguard0 / 192.168.1.2", fontSize = 9.5.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (routeDest.isNotBlank() && routeMask.isNotBlank() && routeVia.isNotBlank()) {
                        onApplyCmds("Маршрут $routeDest", listOf("ip route ${routeDest.trim()} ${routeMask.trim()} ${routeVia.trim()} auto"))
                        routeDest = ""; routeVia = ""
                    }
                },
                enabled = !busy && routeDest.isNotBlank() && routeVia.isNotBlank(),
                modifier = Modifier.height(34.dp)
            ) { Text("+", fontSize = 13.sp) }
        }
    }

    confirmDelPol?.let { pName ->
        AlertDialog(
            onDismissRequest = { confirmDelPol = null },
            title = { Text("Удалить политику $pName?", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Привязанные клиенты перейдут на политику по умолчанию.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelPol = null
                        onApplyCmds("Удалить политику $pName", listOf("no ip policy $pName"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelPol = null }) { Text("Отмена") } }
        )
    }
}

@Composable
internal fun NdmsLanSysPage(
    state: NdmsControlState?,
    vaultEntries: List<ConfigVaultEntry>,
    cliOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onApplyCmds: (String, List<String>) -> Unit,
    onRunCli: (String) -> Unit,
    onTakeVault: (String) -> Unit,
    onRestoreVault: (ConfigVaultEntry) -> Unit,
    onDeleteVault: (ConfigVaultEntry) -> Unit,
    onFormatUsbExt4: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    var cliInput by remember { mutableStateOf("") }
    var hostInput by remember(state?.hostname) { mutableStateOf(state?.hostname.orEmpty()) }
    var domInput by remember(state?.domainName) { mutableStateOf(state?.domainName.orEmpty()) }
    var editPool by remember { mutableStateOf<NdmsDhcpPoolInfo?>(null) }
    var poolDns1 by remember { mutableStateOf("") }
    var poolDns2 by remember { mutableStateOf("") }
    var poolLease by remember { mutableStateOf("") }
    var viewVaultEntry by remember { mutableStateOf<Pair<ConfigVaultEntry, String>?>(null) }
    var confirmVaultRestore by remember { mutableStateOf<ConfigVaultEntry?>(null) }
    var confirmFormatDev by remember { mutableStateOf<String?>(null) }
    var formatConfirmWord by remember { mutableStateOf("") }

    // 1. Config Vault
    MiniCard("Config Vault: снимки NDMS и быстрый откат (${vaultEntries.size})", { onTopicHelp("ndms_lan_sys") }) {
        Text(
            "Перед каждой правкой NDMS снимок сохраняется автоматически. Можно в 1 нажатие откатиться к рабочей конфигурации.",
            color = Color(0xFF94A3B8),
            fontSize = 9.5.sp
        )
        Button(
            onClick = { onTakeVault("Снимок перед настройкой") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(31.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text("Создать снимок running-config сейчас", fontSize = 10.sp)
        }
        vaultEntries.take(8).forEach { v ->
            Surface(
                color = Color(0xFF020617),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v.reason, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${v.timestamp} • ${formatSizeMb(v.sizeBytes)}", color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    WrapRow {
                        Button(onClick = { confirmVaultRestore = v }, enabled = !busy, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Откатить", fontSize = 9.5.sp)
                        }
                        OutlinedButton(onClick = { viewVaultEntry = v to ConfigVault.readSnapshotText(v) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Просмотр", fontSize = 9.5.sp)
                        }
                        IconButton(onClick = { onDeleteVault(v) }, modifier = Modifier.size(25.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    // 2. DHCP Pools
    val pools = state?.dhcpPools ?: emptyList()
    MiniCard("LAN и DHCP-пулы (${pools.size})") {
        if (pools.isEmpty()) Text("Пулы DHCP не найдены", color = Color(0xFF64748B), fontSize = 10.sp)
        pools.forEach { pool ->
            Surface(
                color = Color(0xFF020617),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${pool.name}${if (pool.bind.isNotBlank()) " (${pool.bind})" else ""}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = {
                                editPool = pool
                                poolDns1 = pool.dnsServers.getOrNull(0).orEmpty()
                                poolDns2 = pool.dnsServers.getOrNull(1).orEmpty()
                                poolLease = if (pool.leaseSec > 0) pool.leaseSec.toString() else "25200"
                            },
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Изм. DNS/Lease", fontSize = 9.sp) }
                    }
                    Text(
                        "Диапазон: ${pool.rangeStart.ifBlank { "—" }} (${pool.rangeSize}) • Аренда: ${pool.leaseSec}с",
                        color = Color(0xFF93C5FD),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (pool.dnsServers.isNotEmpty()) {
                        Text("DNS: ${pool.dnsServers.joinToString(", ")}", color = Color(0xFF86EFAC), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }

    // 3. System Identity & USB ext4 format
    MiniCard("Система NDMS и USB ext4") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = hostInput, onValueChange = { hostInput = it }, label = { Text("Hostname", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedTextField(value = domInput, onValueChange = { domInput = it }, label = { Text("Домен", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
        }
        WrapRow {
            Button(
                onClick = {
                    val cmds = mutableListOf<String>()
                    if (hostInput.isNotBlank()) cmds.add("system hostname ${hostInput.trim()}")
                    if (domInput.isNotBlank()) cmds.add("ip domain-name ${domInput.trim()}")
                    if (cmds.isNotEmpty()) onApplyCmds("Имя роутера", cmds)
                },
                enabled = !busy,
                modifier = Modifier.height(29.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("Применить имя", fontSize = 10.sp) }
            OutlinedButton(
                onClick = { onApplyCmds("Сохранить Flash", listOf("system configuration save")) },
                enabled = !busy,
                modifier = Modifier.height(29.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("Сохранить во Flash", fontSize = 10.sp) }
        }

        val mediaParts = state?.mediaParts ?: emptyList()
        val blockDevs = state?.blockDevs ?: emptyList()
        if (mediaParts.isNotEmpty() || blockDevs.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF1E293B))
            Text("USB разделы (${mediaParts.size} /dev: ${blockDevs.size}):", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            mediaParts.forEach { m ->
                MiniRow("${m.mediaName} (${m.label})", "${m.fsType.ifBlank { "?" }} • ${m.freeMb}/${m.totalMb} МБ${if (m.isOpt) " [/opt]" else ""}")
            }
            if (blockDevs.isNotEmpty()) {
                WrapRow {
                    blockDevs.forEach { dev ->
                        val isOptPart = mediaParts.any { it.isOpt && dev.endsWith(it.devNode.substringAfterLast('/')) }
                        OutlinedButton(
                            onClick = { formatConfirmWord = ""; confirmFormatDev = dev },
                            enabled = !busy && !isOptPart,
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isOptPart) Color(0xFF64748B) else Color(0xFFFBBF24))
                        ) {
                            Text(if (isOptPart) "$dev (/opt защищён)" else "Format $dev -> ext4", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }

    // 4. Interactive NDM CLI Console
    MiniCard("Интерактивная NDM CLI-консоль (RCI / ndmc)") {
        val presets = listOf("show version", "show interface", "show ip policy", "show ip route", "show media", "show running-config")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { p ->
                AssistChip(onClick = { cliInput = p; onRunCli(p) }, label = { Text(p, fontSize = 9.sp) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cliInput,
                onValueChange = { cliInput = it },
                placeholder = { Text("Команда NDMS (show ...)", fontSize = 10.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { if (cliInput.isNotBlank()) onRunCli(cliInput) },
                enabled = !busy && cliInput.isNotBlank(),
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("Вып.", fontSize = 10.sp) }
        }
        if (cliOut.isNotBlank()) {
            MonoBox(cliOut, onCopy = { onCopy(cliOut) })
        }
    }

    // Dialogs
    editPool?.let { pool ->
        AlertDialog(
            onDismissRequest = { editPool = null },
            title = { Text("DHCP пул ${pool.name}", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = poolDns1, onValueChange = { poolDns1 = it }, label = { Text("DNS 1 (пусто — убрать)") }, singleLine = true)
                    OutlinedTextField(value = poolDns2, onValueChange = { poolDns2 = it }, label = { Text("DNS 2 (опционально)") }, singleLine = true)
                    OutlinedTextField(value = poolLease, onValueChange = { poolLease = it.filter { c -> c.isDigit() } }, label = { Text("Срок аренды (сек)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cmds = mutableListOf("ip dhcp pool ${pool.name} no dns-server")
                    if (poolDns1.isNotBlank()) cmds.add("ip dhcp pool ${pool.name} dns-server ${poolDns1.trim()}")
                    if (poolDns2.isNotBlank()) cmds.add("ip dhcp pool ${pool.name} dns-server ${poolDns2.trim()}")
                    poolLease.toIntOrNull()?.takeIf { it >= 60 }?.let { sec ->
                        cmds.add("ip dhcp pool ${pool.name} lease $sec")
                    }
                    editPool = null
                    onApplyCmds("DHCP ${pool.name}", cmds)
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editPool = null }) { Text("Отмена") } }
        )
    }

    viewVaultEntry?.let { (entry, text) ->
        AlertDialog(
            onDismissRequest = { viewVaultEntry = null },
            title = { Text("Снимок: ${entry.reason}", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    MonoBox(text.take(8000), onCopy = { onCopy(text) })
                }
            },
            confirmButton = { TextButton(onClick = { viewVaultEntry = null }) { Text("Закрыть") } }
        )
    }

    confirmVaultRestore?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmVaultRestore = null },
            title = { Text("Откатить конфигурацию NDMS?", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Применит снимок «${entry.reason}» (${entry.timestamp}) через RCI. Текущая конфигурация предварительно сохранится в Config Vault.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmVaultRestore = null
                        onRestoreVault(entry)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) { Text("Откатить") }
            },
            dismissButton = { TextButton(onClick = { confirmVaultRestore = null }) { Text("Отмена") } }
        )
    }

    confirmFormatDev?.let { dev ->
        AlertDialog(
            onDismissRequest = { confirmFormatDev = null },
            title = { Text("Форматировать $dev в ext4?", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ВСЕ ДАННЫЕ на разделе $dev будут безвозвратно удалены! Введите EXT4 для подтверждения:", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    OutlinedTextField(
                        value = formatConfirmWord,
                        onValueChange = { formatConfirmWord = it },
                        placeholder = { Text("EXT4") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmFormatDev = null
                        onFormatUsbExt4(dev)
                    },
                    enabled = formatConfirmWord.trim().equals("EXT4", true),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Форматировать") }
            },
            dismissButton = { TextButton(onClick = { confirmFormatDev = null }) { Text("Отмена") } }
        )
    }
}
