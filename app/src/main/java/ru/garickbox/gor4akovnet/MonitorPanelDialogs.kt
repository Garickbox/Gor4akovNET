@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ru.garickbox.gor4akovnet

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun MonitorHelpTopicDialog(topic: MonitorHelpTopic, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(topic.title, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HelpSection("Что это", topic.what, Color(0xFF93C5FD))
                HelpSection("Зачем нужно", topic.why, Color(0xFF86EFAC))
                HelpSection("Рекомендация", topic.recommend, Color(0xFFE2E8F0))
                if (topic.caution.isNotBlank()) HelpSection("Важно", topic.caution, Color(0xFFFCD34D))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } }
    )
}

@Composable
internal fun MonitorStopDropbearDialog(
    id: String,
    act: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Осторожно: SSH Dropbear", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Остановка $id отключит доступ по SSH (:222) — приложение потеряет связь до ручного перезапуска через веб-интерфейс роутера. Рестарт обычно безопасен (1–2 сек). Продолжить?",
                fontSize = 12.5.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) { Text("Да, выполнить $act") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
internal fun MonitorSvcModalDialog(
    key: String,
    overview: JSONObject?,
    warpFull: JSONObject?,
    onDismiss: () -> Unit,
    onWarpAction: (String, String) -> Unit,
    onOpenWarp: () -> Unit,
    onTgTunnelAction: (String, String) -> Unit,
    onInitSvcAction: (String, String) -> Unit
) {
    val guide = MonitorHelp.services[key]
    val svcObj = overview?.optJSONObject("svc")
    val isUp = svcObj?.optBoolean(key, false) == true
    val warpInstalled = svcObj?.optBoolean("warp", false) == true ||
        warpFull?.optJSONObject("status")?.optBoolean("installed") == true
    val initMap = mapOf(
        "nfqws2" to "S99zapret2", "adguard" to "S99AdGuardHome",
        "tg_tunnel" to "S98tg-tunnel", "http_tunnel" to "S97z2k-http-tunnel", "warp" to "S51z2k-warp",
        "z2k_panel" to "S96z2k-webpanel", "scheduler" to "S99z2k-scheduler"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(guide?.title ?: key, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniPill(if (isUp) "Активна" else "Остановлена", isUp)
                    if (!guide?.port.isNullOrBlank()) {
                        Text("Порт: ${guide?.port}", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (guide != null) {
                    HelpSection("Назначение", guide.summary, Color(0xFF93C5FD))
                    HelpSection("Подробно", guide.details, Color(0xFFE2E8F0))
                    HelpSection("Совет", guide.tips, Color(0xFFFCD34D))
                    if (guide.configPath.isNotBlank()) {
                        Text("Конфиг: ${guide.configPath}", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                HorizontalDivider(color = Color(0xFF1E293B))
                when (key) {
                    "warp" -> {
                        if (!warpInstalled) {
                            Button(onClick = { onWarpAction("Установка WARP", MonitorScripts.warpInstallCmd()) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Установить Cloudflare WARP", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    onWarpAction(
                                        if (isUp) "Стоп WARP" else "Старт WARP",
                                        MonitorScripts.toggleZ2k("game-warp", if (isUp) 0 else 1)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isUp) "Выключить WARP" else "Включить WARP", fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(onClick = onOpenWarp, modifier = Modifier.fillMaxWidth()) {
                            Text("Открыть раздел WARP & Игры", fontSize = 11.sp)
                        }
                    }
                    "tg_tunnel" -> {
                        Button(
                            onClick = {
                                onTgTunnelAction(
                                    if (isUp) "Стоп TG туннеля" else "Старт TG туннеля",
                                    MonitorScripts.tunnelSetCmd(!isUp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isUp) "Выключить TG :1443" else "Включить TG :1443", fontSize = 12.sp)
                        }
                    }
                    else -> {
                        val initId = initMap[key]
                        if (initId != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onInitSvcAction("Рестарт $initId", MonitorScripts.svcCmd(initId, "restart")) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Рестарт", fontSize = 11.sp) }
                                OutlinedButton(
                                    onClick = {
                                        onInitSvcAction(
                                            "${if (isUp) "Стоп" else "Старт"} $initId",
                                            MonitorScripts.svcCmd(initId, if (isUp) "stop" else "start")
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (isUp) "Стоп" else "Старт", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
internal fun MonitorExpandedErrDialog(errorText: String, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Полный текст ошибки", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = { MonoBox(errorText) },
        confirmButton = { TextButton(onClick = { onCopy(errorText) }) { Text("Копировать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
internal fun MonitorConfirmSystemRestoreDialog(
    entry: BackupEntry,
    onDismiss: () -> Unit,
    onConfirm: (BackupEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтвердите 100% откат", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Будут перезаписаны /opt, пользовательские файлы носителя (если есть в архиве) и конфигурация NDMS (Wi-Fi, политики, порты). Во время применения Wi-Fi может кратковременно переподключиться.",
                fontSize = 12.sp
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(entry) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) { Text("Откатить систему полностью") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
internal fun MonitorBackupPreviewDialog(label: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(text.take(14000), color = Color(0xFFCBD5E1), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
internal fun MonitorFileEditorDialog(
    path: String,
    initialText: String,
    truncated: Boolean,
    onDismiss: () -> Unit,
    onRestoreBak: () -> Unit,
    onSave: (String) -> Unit
) {
    var editText by remember(path, initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(path.substringAfterLast('/'), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(path, color = Color(0xFF93C5FD), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                if (truncated) {
                    Text("Файл > 32 КБ: показана только первая часть (сохранение отключено).", color = Color(0xFFFBBF24), fontSize = 11.sp)
                }
                OutlinedTextField(
                    value = editText,
                    onValueChange = { if (!truncated) editText = it },
                    readOnly = truncated,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 340.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRestoreBak) { Text("Откатить .bak", color = Color(0xFFFBBF24), fontSize = 11.sp) }
                if (!truncated) {
                    TextButton(onClick = {
                        val b64 = Base64.encodeToString(editText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        onSave(b64)
                    }) { Text("Сохранить (.bak)") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
internal fun MonitorNewEntryDialog(
    mode: String,
    dirPath: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember(mode, dirPath) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == "dir") "Новая папка" else "Новый файл") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("В папке: $dirPath", fontSize = 11.sp, color = Color(0xFF94A3B8))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Имя") })
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim().replace("/", "")) }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
internal fun MonitorSshCredsDialog(
    prefs: Prefs,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit,
    onError: (String) -> Unit,
    onSaved: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var sshHost by remember { mutableStateOf(prefs.routerHost.ifBlank { "192.168.1.1" }) }
    var sshUser by remember { mutableStateOf(prefs.sshUser) }
    var sshPort by remember { mutableStateOf(prefs.sshPort.toString()) }
    var sshPass by remember { mutableStateOf(prefs.sshPass) }
    var showPass by remember { mutableStateOf(false) }
    var bruteBusy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SSH подключение к роутеру") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sshHost, onValueChange = { sshHost = it }, label = { Text("LAN Host / IP") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sshUser, onValueChange = { sshUser = it }, label = { Text("Логин") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text("Порт") }, singleLine = true, modifier = Modifier.width(88.dp))
                }
                OutlinedTextField(
                    value = sshPass,
                    onValueChange = { sshPass = it },
                    label = { Text("Пароль SSH (Dropbear)") },
                    singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                        }
                    }
                )
                OutlinedButton(
                    enabled = !bruteBusy,
                    onClick = {
                        scope.launch {
                            bruteBusy = true
                            val p = sshPort.toIntOrNull() ?: 222
                            val found = withContext(Dispatchers.IO) {
                                runCatching { Gboxcore.tryPasswordsSSH(sshHost.ifBlank { "192.168.1.1" }, p, sshUser, prefs.rciPass) }.getOrNull()
                            }
                            if (found != null) {
                                sshPass = found
                                onStatus("Пароль SSH подобран!")
                            } else {
                                onError("Стандартный пароль SSH не подошёл — введите вручную")
                            }
                            bruteBusy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (bruteBusy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Tune, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (bruteBusy) "Подбор..." else "Автоподбор пароля SSH", fontSize = 11.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.setSshHost(ctx, sshHost)
                Prefs.setSshUser(ctx, sshUser)
                Prefs.setSshPort(ctx, sshPort.toIntOrNull() ?: 222)
                Prefs.setSshPass(ctx, sshPass)
                onSaved()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
internal fun MonitorConfirmRebootDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перезагрузить роутер?") },
        text = { Text("Интернет и Wi-Fi пропадут примерно на 1–2 минуты.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Перезагрузить", color = Color(0xFFFCA5A5)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
