package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

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
                    val ip = e.optString("ip").ifBlank { "нет выхода" }
                    MiniRow(e.optString("iface"), ip, if (ip == "нет выхода") Color(0xFFFCA5A5) else Color(0xFF4ADE80))
                }
            }
            val dnsEg = routeDoctor.optString("dns_egress").orEmpty()
            MiniRow("DNS egress (whoami.akamai)", dnsEg.ifBlank { "не определён" })
            val ipv6 = routeDoctor.optJSONArray("ipv6") ?: JSONArray()
            MiniRow("Глобальный IPv6 на роутере", if (ipv6.length() == 0) "Нет (утечки v6 нет)" else ipv6.optString(0), if (ipv6.length() == 0) Color(0xFF4ADE80) else Color(0xFFFBBF24))

            val rules = routeDoctor.optJSONArray("rules") ?: JSONArray()
            if (rules.length() > 0) {
                Text("Linux ip rule (${rules.length()}):", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                MonoBox((0 until rules.length()).joinToString("
") { rules.optString(it) })
            }
            val tbls = routeDoctor.optJSONArray("tables") ?: JSONArray()
            if (tbls.length() > 0) {
                Text("Таблицы политик (${tbls.length()}):", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                val txt = buildString {
                    for (i in 0 until tbls.length()) {
                        val t = tbls.optJSONObject(i) ?: continue
                        append("[table ${t.optString("table")}]
")
                        val rs = t.optJSONArray("routes") ?: JSONArray()
                        for (j in 0 until rs.length()) append("  ").append(rs.optString(j)).append('
')
                    }
                }.trim()
                if (txt.isNotBlank()) MonoBox(txt)
            }
            val mainRt = routeDoctor.optJSONArray("main_routes") ?: JSONArray()
            if (mainRt.length() > 0) {
                Text("Основная таблица маршрутов:", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                MonoBox((0 until mainRt.length()).joinToString("
") { mainRt.optString(it) })
            }
        }
    }

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
