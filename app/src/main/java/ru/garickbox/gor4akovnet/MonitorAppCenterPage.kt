@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
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

private data class AppCatalogMeta(
    val id: String,
    val title: String,
    val category: String,
    val desc: String,
    val hasService: Boolean
)

private val APP_CATALOG = listOf(
    AppCatalogMeta("adguard", "AdGuard Home", "DNS", "Фильтрация рекламы, шифрованный DoH/DoT DNS и живой журнал запросов", true),
    AppCatalogMeta("singbox", "Sing-box", "Туннели", "Универсальный прокси-движок VLESS / HY2 / Reality / TUN через Entware", true),
    AppCatalogMeta("mihomo", "Mihomo / XKeen", "Туннели", "Прокси-роутинг Clash Meta / Xray (если установлен в Entware)", true),
    AppCatalogMeta("amneziawg", "AmneziaWG Tools", "Туннели", "Утилиты awg для обфусцированного WireGuard-туннеля", true),
    AppCatalogMeta("iperf3", "iperf3", "Сеть CLI", "Замер реальной пропускной способности LAN / Wi-Fi / VPN до роутера", false),
    AppCatalogMeta("tcpdump", "tcpdump", "Сеть CLI", "Захват и анализ пакетов прямо на интерфейсах роутера (для Torch)", false),
    AppCatalogMeta("mtr", "MTR", "Сеть CLI", "Непрерывная трассировка маршрута с процентом потерь по каждому хопу", false),
    AppCatalogMeta("htop", "htop", "Утилиты", "Интерактивный просмотр процессов, потоков и памяти по SSH", false),
    AppCatalogMeta("mc", "Midnight Commander", "Утилиты", "Двухпанельный файловый менеджер (mc) для SSH-консоли", false),
    AppCatalogMeta("nano", "nano", "Утилиты", "Простой консольный текстовый редактор для быстрых правок", false)
)

@Composable
internal fun AppCenterPage(
    data: JSONObject?,
    actionOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onInstallAdguard: () -> Unit,
    onAction: (String, String) -> Unit,
    onCopy: (String) -> Unit
) {
    var cat by remember { mutableStateOf("Все") }
    var customPkg by remember { mutableStateOf("") }
    var confirmRemoveId by remember { mutableStateOf<String?>(null) }

    val arch = data?.optString("arch", "—").orEmpty().ifBlank { "—" }
    val entOk = data?.optBoolean("entware", false) == true
    val freeMb = data?.optInt("free_mb", 0) ?: 0
    val appsArr = data?.optJSONArray("apps") ?: JSONArray()
    val stateMap = remember(data) {
        val m = mutableMapOf<String, JSONObject>()
        for (i in 0 until appsArr.length()) {
            val o = appsArr.optJSONObject(i) ?: continue
            m[o.optString("id")] = o
        }
        m
    }

    MiniCard("Центр пакетов и модулей (Entware)", { onTopicHelp("app_center") }) {
        MiniRow("Архитектура • Entware", "$arch • ${if (entOk) "Готов" else "Нет opkg"}", if (entOk) Color(0xFF4ADE80) else Color(0xFFFCA5A5))
        MiniRow("Свободно на /opt", "$freeMb МБ", if (freeMb >= 30) Color(0xFF86EFAC) else Color(0xFFFBBF24))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Все", "Туннели", "DNS", "Сеть CLI", "Утилиты").forEach { c ->
                FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c, fontSize = 9.5.sp) })
            }
        }
    }

    val visibleApps = APP_CATALOG.filter { cat == "Все" || it.category == cat }
    visibleApps.forEach { meta ->
        val st = stateMap[meta.id]
        val installed = st?.optBoolean("inst", false) == true
        val running = st?.optBoolean("run", false) == true
        val ver = st?.optString("ver", "").orEmpty()
        val initName = st?.optString("init", "").orEmpty()

        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, if (running) Color(0xFF166534) else if (installed) Color(0xFF1E3A8A) else Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(meta.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${meta.category}${if (ver.isNotBlank()) " • v$ver" else ""}${if (initName.isNotBlank()) " • $initName" else ""}",
                            color = Color(0xFF93C5FD),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    MiniPill(
                        label = when {
                            running -> "Работает"
                            installed -> "Установлен"
                            else -> "Не уст."
                        },
                        ok = running || installed
                    )
                }
                Text(meta.desc, color = Color(0xFF94A3B8), fontSize = 10.sp, lineHeight = 13.sp)
                WrapRow {
                    if (!installed) {
                        Button(
                            onClick = {
                                if (meta.id == "adguard") onInstallAdguard()
                                else onAction("Уст. ${meta.title}", MonitorDiagScripts.appCenterActionCmd(meta.id, "install"))
                            },
                            enabled = !busy && (entOk || meta.id == "adguard"),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Установить", fontSize = 10.sp)
                        }
                    } else {
                        if (meta.hasService) {
                            Button(
                                onClick = {
                                    val act = if (running) "stop" else "start"
                                    onAction("${if (running) "Стоп" else "Старт"} ${meta.title}", MonitorDiagScripts.appCenterActionCmd(meta.id, act))
                                },
                                enabled = !busy,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 9.dp)
                            ) {
                                Text(if (running) "Стоп" else "Старт", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onAction("Рестарт ${meta.title}", MonitorDiagScripts.appCenterActionCmd(meta.id, "restart")) },
                                enabled = !busy,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 9.dp)
                            ) {
                                Text("Рестарт", fontSize = 10.sp)
                            }
                        }
                        if (meta.id != "adguard") {
                            OutlinedButton(
                                onClick = { confirmRemoveId = meta.id },
                                enabled = !busy,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 9.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))
                            ) {
                                Text("Удалить", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    MiniCard("Быстрая установка любого пакета opkg") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customPkg,
                onValueChange = { customPkg = it },
                placeholder = { Text("Имя пакета opkg (напр. curl)", fontSize = 10.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (customPkg.isNotBlank()) {
                        onAction("Уст. $customPkg", MonitorScripts.opkgInstallCmd(customPkg))
                        customPkg = ""
                    }
                },
                enabled = !busy && entOk && customPkg.isNotBlank(),
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text("Старт", fontSize = 10.sp)
            }
        }
        if (actionOut.isNotBlank()) {
            MonoBox(actionOut, onCopy = { onCopy(actionOut) })
        }
    }

    confirmRemoveId?.let { remId ->
        val title = APP_CATALOG.firstOrNull { it.id == remId }?.title ?: remId
        AlertDialog(
            onDismissRequest = { confirmRemoveId = null },
            title = { Text("Удалить $title?", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Служба будет остановлена, а пакет удалён через opkg remove.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveId = null
                        onAction("Удалить $title", MonitorDiagScripts.appCenterActionCmd(remId, "remove"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveId = null }) { Text("Отмена") } }
        )
    }
}
