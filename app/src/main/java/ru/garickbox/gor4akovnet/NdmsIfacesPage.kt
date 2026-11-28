package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
        } else {
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
