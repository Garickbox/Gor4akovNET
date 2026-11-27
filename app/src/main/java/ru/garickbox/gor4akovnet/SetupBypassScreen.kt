package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Мастер 2/2: Установка SSID с обходом (z2k / zapret2).
 *
 * Использует готовый скрипт GboxScripts.buildOfflineAndBypassInstallScript() и
 * запускает его через SSH :222 (Entware уже должен быть развёрнут на Шаге 1/2).
 *
 * AdGuard:
 *  - Если prefs.adguardInstalled=true — installAdguard=false, ставим только SSID+обход.
 *    Пользователь сказал: AdGuard уже настроен хорошо, не трогаем.
 *  - Иначе — installAdguard=флажок, полная установка с AdGuard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupBypassScreen(
    host: String,
    sshPort: Int,
    sshUser: String,
    sshPass: String,
    prefs: Prefs,
    onFinished: () -> Unit,
    onSkip: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var ssid by remember { mutableStateOf(prefs.wifiSsid) }
    var wifiPass by remember { mutableStateOf(prefs.wifiPass) }
    var passVisible by remember { mutableStateOf(false) }
    var isolated by remember { mutableStateOf(prefs.isolatedMode) }
    var installAdguard by remember { mutableStateOf(prefs.optAdguard) }
    var enableWarp by remember { mutableStateOf(prefs.optWarp) }

    val adguardAlready = prefs.adguardInstalled

    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf(listOf<String>()) }
    var errMsg by remember { mutableStateOf("") }
    var installOk by remember { mutableStateOf(false) }

    fun addLog(s: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        log = log + "[$ts] $s"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeckColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Заголовок
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Шаг 2/2", color = DeckColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("·", color = DeckColors.Muted, fontSize = 12.sp)
            Text("Установка обхода", color = DeckColors.Sky, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // SSID
        DeckCard(borderColor = DeckColors.Sky.copy(0.4f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Wifi, null, tint = DeckColors.Sky, modifier = Modifier.size(18.dp))
                Text("НАШ SSID + ОБХОД", color = DeckColors.Sky, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("Имя Wi-Fi сети") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = wifiPass,
                onValueChange = { wifiPass = it },
                label = { Text("Пароль Wi-Fi (8-63 символа)") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(
                            if (passVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            null, tint = DeckColors.Muted
                        )
                    }
                }
            )

            // Опции
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isolated,
                    onCheckedChange = { isolated = it },
                    enabled = !busy,
                    colors = CheckboxDefaults.colors(checkedColor = DeckColors.Sky)
                )
                Text("Изолированная сеть (отдельная политика Gor4akovNET)", color = DeckColors.Text, fontSize = 12.sp)
            }

            if (adguardAlready) {
                StatusTag("✓ AdGuard уже установлен — не трогаем", DeckColors.Emerald)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = installAdguard,
                        onCheckedChange = { installAdguard = it },
                        enabled = !busy,
                        colors = CheckboxDefaults.colors(checkedColor = DeckColors.Sky)
                    )
                    Text("Установить AdGuard Home (DNS-фильтр :3053)", color = DeckColors.Text, fontSize = 12.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = enableWarp,
                    onCheckedChange = { enableWarp = it },
                    enabled = !busy,
                    colors = CheckboxDefaults.colors(checkedColor = DeckColors.Sky)
                )
                Text("Enable Warp (z2k-warpd)", color = DeckColors.Text, fontSize = 12.sp)
            }

            Text(
                "Устанавливает z2k/zapret2 (nfqws2), политику Gor4akovNET и SSID. " +
                "Требует работающего Entware (/opt) и SSH :222.",
                color = DeckColors.Muted, fontSize = 10.5.sp
            )
        }

        // Журнал
        if (log.isNotEmpty() || busy) {
            DeckCard(borderColor = DeckColors.Border) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Terminal, null, tint = DeckColors.Muted, modifier = Modifier.size(16.dp))
                    Text("ЖУРНАЛ УСТАНОВКИ", color = DeckColors.Muted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = DeckColors.Sky, trackColor = DeckColors.CardAlt
                    )
                }
                Surface(
                    color = Color(0xFF020617),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, DeckColors.Border),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        log.forEach { line ->
                            Text(
                                line,
                                color = when {
                                    line.contains("FATAL") || line.contains("✗") -> Color(0xFFFCA5A5)
                                    line.contains("OK") || line.contains("✓") -> Color(0xFF86EFAC)
                                    line.contains("WARN") -> Color(0xFFFCD34D)
                                    else -> Color(0xFF94A3B8)
                                },
                                fontSize = 10.5.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        if (errMsg.isNotBlank()) {
            Surface(
                color = Color(0xFF3B0F0F), shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚠️ $errMsg", color = Color(0xFFFCA5A5), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
        }

        // Кнопки
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    errMsg = ""
                    installOk = false
                    log = emptyList()
                    addLog("=== УСТАНОВКА SSID «$ssid» с обходом ===")
                    addLog("Параметры: isolated=$isolated, adguard=${if (adguardAlready) "уже есть" else installAdguard}, warp=$enableWarp")

                    // 1) Проверка SSH :222
                    addLog("--- Проверка SSH :222 ---")
                    val sshOk = withContext(Dispatchers.IO) { Gboxcore.probeTCP(host, sshPort, 2000) }
                    if (!sshOk) {
                        errMsg = "SSH :222 не отвечает. Сначала завершите Шаг 1/2 (Entware)."
                        addLog("✗ SSH :$sshPort refused")
                        busy = false
                        return@launch
                    }
                    addLog("✓ SSH :$sshPort доступен")

                    // 2) Собираем скрипт
                    val script = GboxScripts.buildOfflineAndBypassInstallScript(
                        ssid = ssid,
                        wifiPass = wifiPass,
                        isolatedMode = isolated,
                        installAdguard = installAdguard && !adguardAlready,
                        enableWarp = enableWarp
                    )
                    addLog("--- Скрипт собран (${script.length} символов), запуск... ---")

                    // 3) Запускаем
                    addLog("--- Выполнение (может занять 5-10 минут) ---")
                    val out = withContext(Dispatchers.IO) {
                        runCatching {
                            Gboxcore.runRouterSSH(host, sshPort, sshUser, sshPass, script)
                        }
                    }

                    out.fold(
                        onSuccess = { text ->
                            text.lines().forEach { line ->
                                if (line.isNotBlank()) addLog(line)
                            }
                            if (text.contains("PREFLIGHT_FAIL") || text.contains("[FATAL]")) {
                                errMsg = "Установка прервана на preflight. Смотрите журнал."
                                addLog("✗ УСТАНОВКА ПРОВАЛЕНА (preflight)")
                            } else {
                                installOk = true
                                prefs.wifiSsid = ssid
                                prefs.wifiPass = wifiPass
                                prefs.isolatedMode = isolated
                                prefs.optAdguard = installAdguard
                                prefs.optWarp = enableWarp
                                if (installAdguard && !adguardAlready) {
                                    prefs.adguardInstalled = true
                                }
                                addLog("=== УСТАНОВКА ЗАВЕРШЕНА ===")
                            }
                        },
                        onFailure = {
                            errMsg = "Ошибка SSH: ${it.message}"
                            addLog("✗ SSH ошибка: ${it.message}")
                        }
                    )
                    busy = false
                }
            },
            enabled = !busy && ssid.isNotBlank() && wifiPass.length >= 8,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (installOk) DeckColors.Emerald else DeckColors.Sky)
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (installOk) "✓ Установлено — Готово →"
                else if (busy) "Устанавливаю…"
                else "🚀 Установить SSID с обходом",
                fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onSkip,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("Пропустить", color = DeckColors.Muted, fontSize = 12.sp) }

            if (installOk) {
                Button(
                    onClick = onFinished,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeckColors.Emerald)
                ) { Text("В программу →", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}