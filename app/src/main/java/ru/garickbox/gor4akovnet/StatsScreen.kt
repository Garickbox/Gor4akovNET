package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var loading by remember { mutableStateOf(true) }
    var report by remember { mutableStateOf<GlobalStatsReport?>(null) }
    var errFilter by remember { mutableStateOf("") }
    var copiedMsg by remember { mutableStateOf("") }

    fun refresh() {
        loading = true
        scope.launch {
            report = Telemetry.fetchReport(prefs)
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val r = report
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
            Column(modifier = Modifier.weight(1f)) {
                Text("Статистика и диагностика", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(r?.remoteNote ?: "Загрузка отчёта...", color = DeckColors.Muted, fontSize = 11.5.sp)
            }
            IconButton(onClick = { refresh() }, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Обновить", tint = DeckColors.Emerald)
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DeckColors.Emerald)
        }

        if (r != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCounterCard("Всего APK", "${r.totalApk}", DeckColors.Sky, Modifier.weight(1f))
                StatCounterCard("Помогли", "${r.helpedUsers}", DeckColors.Emerald, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCounterCard("Не помогли", "${r.failedOnlyUsers}", DeckColors.Amber, Modifier.weight(1f))
                StatCounterCard("Успешн. установок", "${r.totalSuccessInstalls}", DeckColors.Emerald, Modifier.weight(1f))
            }
            StatCounterCard("Ошибок в логе", "${r.totalErrors}", if (r.totalErrors > 0) DeckColors.Rose else DeckColors.Muted, Modifier.fillMaxWidth())

            if (!Telemetry.isRemoteConfigured) {
                DeckCard(borderColor = DeckColors.Sky.copy(0.45f)) {
                    Text("Подключение общей статистики на GitHub", color = DeckColors.Sky, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "1. Создай на gist.github.com один секретный Gist (с любым файлом init.json).\n" +
                            "2. Создай в GitHub Settings -> Developer settings -> Fine-grained / Classic token права только на Gist.\n" +
                            "3. В репозитории Gor4akovNET -> Settings -> Secrets -> Actions добавь:\n" +
                            "   • GBOX_STATS_GIST_ID (ID Gist из URL)\n" +
                            "   • GBOX_STATS_TOKEN (токен с правом gist)\n" +
                            "4. Пересобери APK — каждое устройство само запишет файл dev_<id>.json без конфликтов.",
                        color = DeckColors.Muted,
                        fontSize = 11.5.sp
                    )
                }
            }

            DeckCard {
                Text("Установки (${r.devices.size})", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (r.devices.isEmpty()) {
                    Text("Данных пока нет.", color = DeckColors.Muted, fontSize = 12.sp)
                } else {
                    r.devices.forEach { d ->
                        Surface(
                            color = DeckColors.CardAlt,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DeckColors.Border)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${d.device} (${d.id})", color = DeckColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    StatusTag(
                                        if (d.helped) "ПОМОГЛО (${d.okCount})" else if (d.failCount > 0) "СБОЙ (${d.failCount})" else "ОТКРЫТ",
                                        if (d.helped) DeckColors.Emerald else if (d.failCount > 0) DeckColors.Rose else DeckColors.Sky
                                    )
                                }
                                Text("Роутер: ${d.routerModel} · NDMS: ${d.ndms} · v${d.appVer}", color = DeckColors.Sky, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Последняя активность: ${d.lastSeen}", color = DeckColors.Muted, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }

            DeckCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Лог ошибок для анализа (${r.allErrors.size})", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (r.allErrors.isNotEmpty()) {
                        IconButton(onClick = {
                            val txt = r.allErrors.joinToString("\n") { "[${it.ts}] (${it.device} / ${it.router}) ${it.stage}: ${it.msg}" }
                            clipboard.setText(AnnotatedString(txt))
                            copiedMsg = "Лог ошибок скопирован"
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Копировать", tint = DeckColors.Sky)
                        }
                    }
                }
                if (copiedMsg.isNotBlank()) Text(copiedMsg, color = DeckColors.Emerald, fontSize = 11.sp)
                OutlinedTextField(
                    value = errFilter,
                    onValueChange = { errFilter = it },
                    placeholder = { Text("Фильтр (этап / роутер / текст ошибки)...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val filtered = if (errFilter.isBlank()) r.allErrors else r.allErrors.filter {
                    it.stage.contains(errFilter, true) || it.msg.contains(errFilter, true) || it.router.contains(errFilter, true) || it.device.contains(errFilter, true)
                }
                if (filtered.isEmpty()) {
                    Text("Ошибок не зафиксировано.", color = DeckColors.Muted, fontSize = 12.sp)
                } else {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filtered.take(40).forEach { e ->
                                Surface(color = DeckColors.Bg, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, DeckColors.Border)) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text("${e.ts} · ${e.stage} · ${e.router}", color = DeckColors.Amber, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                                        Text("${e.device}: ${e.msg}", color = DeckColors.Rose, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCounterCard(title: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(
        color = DeckColors.Card,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, DeckColors.Border),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = DeckColors.Muted, fontSize = 11.5.sp)
            Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
