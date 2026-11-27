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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Мастер 1/2: Сервисы + подготовка флешки + установка Entware.
 *
 * Логика:
 *  - Тумблеры WebDAV и SSH — ЗАМОРОЖЕНЫ (обязательны, переключить нельзя).
 *    Показывают текущее состояние ИЛИ "БУДЕТ ВКЛЮЧЕНО" красным.
 *  - AdGuard — опционален. Если уже установлен — показываем "✓ УЖЕ ЕСТЬ",
 *    настройки не трогаем (пользователь подтвердил — хорошо настроен).
 *  - Кнопка "Подготовить флешку" — media initialize ext4 (п. 3.105).
 *  - Кнопка "Далее" → каскад:
 *      Этап 1: enableServicesViaRci(ssh+webdav)          ← тумблеры вкл
 *      Этап 2: detectOpkgDiskRef                         ← UUID флешки
 *      Этап 3: installEntwareViaUrl(opkg disk + initrc)  ← NDMS качает+разворачивает
 *      Этап 4: ждём :222 (dropbear)                      ← Entware готов
 *      Этап 5: опционально AdGuard — оставляем как есть или ставим
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupMasterScreen(
    passport: RouterPassport,
    host: String,
    user: String,
    pass: String,
    prefs: Prefs,
    onFinished: (entwareOk: Boolean, adguardWasInstalled: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Выбор USB-медиа
    val usbMedia = passport.mediaList.filter { it.isUsb }
    var selectedMedia by remember {
        mutableStateOf(
            usbMedia.firstOrNull { it.isExt4 }
                ?: usbMedia.firstOrNull()
        )
    }
    // Текущее состояние флешки (может измениться после инициализации)
    var mediaReady by remember { mutableStateOf(selectedMedia?.isExt4 == true) }
    // Прогресс
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(0) }              // 0=ожидание, 1-5=этапы
    var errMsg by remember { mutableStateOf("") }
    var log by remember { mutableStateOf(listOf<String>()) }
    var showConfirmFormat by remember { mutableStateOf(false) }
    var formatWord by remember { mutableStateOf("") }
    var adguardInstalled by remember { mutableStateOf(prefs.adguardInstalled) }

    fun addLog(s: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        log = log + "[$ts] $s"
    }

    // ЗАМОРОЖЕННЫЕ ТУМБЛЕРЫ — вычисляются из passport и не меняются пользователем
    val webdavState = passport.hasWebdavComp
    val ssh22State = passport.ssh22Open
    val ssh222State = passport.ssh222Open

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
            Text("Шаг 1/2", color = DeckColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("·", color = DeckColors.Muted, fontSize = 12.sp)
            Text("Подготовка", color = DeckColors.Sky, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // === ОБЯЗАТЕЛЬНЫЕ СЕРВИСЫ (заморожены) ===
        DeckCard(borderColor = DeckColors.Emerald.copy(0.4f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = DeckColors.Emerald, modifier = Modifier.size(18.dp))
                Text("ОБЯЗАТЕЛЬНЫЕ СЕРВИСЫ", color = DeckColors.Emerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text(
                "Эти сервисы нужны для заливки архива и управления. Включаются автоматически на «Далее», отключить нельзя.",
                color = DeckColors.Muted, fontSize = 11.sp
            )
            FrozenToggle(
                title = "WebDAV :80/install",
                subtitle = "Файловый сервер для заливки installer.tar.gz с телефона",
                currentlyOn = webdavState
            )
            FrozenToggle(
                title = "SSH :22",
                subtitle = "CLI-доступ администратора (опционально, но нужен для его сервисов)",
                currentlyOn = ssh22State
            )
            HorizontalDivider(color = DeckColors.Border)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (ssh222State) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
                    null, tint = if (ssh222State) DeckColors.Emerald else DeckColors.Amber,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    if (ssh222State) "Entware уже развёрнут (SSH :222 живой)"
                    else "Entware ещё не развёрнут (SSH :222 будет поднят на Этапе 4)",
                    color = if (ssh222State) DeckColors.Emerald else DeckColors.Amber,
                    fontSize = 11.sp
                )
            }
        }

        // === AdGuard Home ===
        DeckCard(borderColor = if (adguardInstalled) DeckColors.Sky.copy(0.4f) else DeckColors.Border) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Shield, null, tint = DeckColors.Sky, modifier = Modifier.size(18.dp))
                Text("AdGuard Home", color = DeckColors.Sky, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (adguardInstalled) {
                StatusTag("✓ УЖЕ УСТАНОВЛЕН", DeckColors.Emerald)
                Text(
                    "Настройки AdGuard (фильтры, DNS, правила) НЕ трогаются. " +
                    "Можно будет доустановить через меню «Сервис» в основной программе.",
                    color = DeckColors.Muted, fontSize = 11.sp
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = adguardInstalled,
                        onCheckedChange = { adguardInstalled = it },
                        colors = CheckboxDefaults.colors(checkedColor = DeckColors.Sky)
                    )
                    Text("Установить AdGuard Home (блокировщик рекламы)", color = DeckColors.Text, fontSize = 12.sp)
                }
                Text(
                    "DNS-фильтр + блокировщик. Порт :3000 → отдельный админ, DNS на :3053. " +
                    "Можно поставить позже через «Сервис».",
                    color = DeckColors.Muted, fontSize = 10.5.sp
                )
            }
        }

        // === ФЛЕШКА (USB) ===
        DeckCard(borderColor = if (mediaReady) DeckColors.Emerald.copy(0.4f) else DeckColors.Amber.copy(0.4f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Storage, null, tint = DeckColors.Sky, modifier = Modifier.size(18.dp))
                Text("USB-НАКОПИТЕЛЬ", color = DeckColors.Sky, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            if (usbMedia.isEmpty()) {
                StatusTag("ФЛЕШКА НЕ НАЙДЕНА", DeckColors.Rose)
                Text(
                    "Вставьте USB-флешку в роутер и нажмите «Проверить снова».",
                    color = DeckColors.Text, fontSize = 12.sp
                )
            } else {
                usbMedia.forEach { m ->
                    Surface(
                        color = DeckColors.CardAlt,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (m.isExt4) DeckColors.Emerald.copy(0.5f) else DeckColors.Amber.copy(0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${m.name} (${m.label})", color = DeckColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                Text(
                                    "ФС: ${m.fsType.ifBlank { "?" }} • ${m.freeMb}/${m.totalMb} МБ",
                                    color = DeckColors.Muted, fontSize = 10.5.sp
                                )
                            }
                            StatusTag(if (m.isExt4) "EXT4" else "НУЖНА ПОДГОТОВКА", if (m.isExt4) DeckColors.Emerald else DeckColors.Amber)
                        }
                    }
                }

                if (!mediaReady) {
                    Text(
                        "⚠️ Подготовка ПЕРЕФОРМАТИРУЕТ флешку: удалит все файлы, создаст swap + ext4. " +
                        "Это уничтожит старые данные — если там что-то важное, сначала сохраните.",
                        color = DeckColors.Amber, fontSize = 11.sp
                    )
                    Button(
                        onClick = { formatWord = ""; showConfirmFormat = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeckColors.Amber.copy(0.85f))
                    ) {
                        Icon(Icons.Outlined.Storage, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Подготовить флешку (ext4)", fontSize = 13.sp)
                    }
                } else {
                    StatusTag("✓ УЖЕ ГОТОВА (ext4)", DeckColors.Emerald)
                }
            }
        }

        // === ЖУРНАЛ ===
        if (log.isNotEmpty() || busy) {
            DeckCard(borderColor = DeckColors.Border) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Terminal, null, tint = DeckColors.Muted, modifier = Modifier.size(16.dp))
                    Text("ЖУРНАЛ УСТАНОВКИ", color = DeckColors.Muted, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
                if (busy && stage in 1..5) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = DeckColors.Sky,
                        trackColor = DeckColors.CardAlt
                    )
                }
                Surface(
                    color = Color(0xFF020617),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, DeckColors.Border),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        log.forEach { line ->
                            Text(line, color = Color(0xFF86EFAC), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Ошибка
        if (errMsg.isNotBlank()) {
            Surface(
                color = Color(0xFF3B0F0F),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚠️ $errMsg", color = Color(0xFFFCA5A5), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
        }

        // === КНОПКИ ===
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.weight(0.35f),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Отмена", fontSize = 13.sp) }

            Button(
                onClick = {
                    if (selectedMedia == null) {
                        errMsg = "Флешка не выбрана"
                        return@Button
                    }
                    if (!mediaReady) {
                        errMsg = "Сначала подготовьте флешку (ext4)"
                        return@Button
                    }
                    scope.launch {
                        busy = true
                        errMsg = ""
                        log = emptyList()
                        try {
                            // ЭТАП 1: включить обязательные сервисы
                            stage = 1
                            addLog("=== Этап 1/4: Включаю SSH :22 + WebDAV :80 ===")
                            val svcRes = withContext(Dispatchers.IO) {
                                RciClient.enableServicesViaRci(
                                    host, user, pass,
                                    enableSsh = true,
                                    enableWebdav = true,
                                    enableFtp = false
                                )
                            }
                            svcRes.fold(
                                onSuccess = { addLog("✓ $it") },
                                onFailure = {
                                    errMsg = "Не удалось включить сервисы: ${it.message}"
                                    addLog("✗ ОШИБКА: ${it.message}")
                                    busy = false; stage = 0; return@launch
                                }
                            )

                            // ЭТАП 2: определить UUID флешки
                            stage = 2
                            addLog("=== Этап 2/4: Определяю diskRef (UUID флешки) ===")
                            val diskRefRes = withContext(Dispatchers.IO) {
                                RciClient.detectOpkgDiskRef(host, user, pass)
                            }
                            val diskRef = diskRefRes.getOrElse {
                                errMsg = "Не могу определить диск: ${it.message}"
                                addLog("✗ ОШИБКА: ${it.message}")
                                busy = false; stage = 0; return@launch
                            }
                            addLog("✓ $diskRef")

                            // ЭТАП 3: opkg disk + initrc
                            stage = 3
                            addLog("=== Этап 3/4: opkg disk + initrc + save ===")
                            val installRes = withContext(Dispatchers.IO) {
                                RciClient.installEntwareViaUrl(host, user, pass, diskRef)
                            }
                            installRes.fold(
                                onSuccess = { addLog("✓ $it") },
                                onFailure = {
                                    errMsg = "Entware не установлен: ${it.message}"
                                    addLog("✗ ОШИБКА: ${it.message}")
                                    busy = false; stage = 0; return@launch
                                }
                            )

                            // ЭТАП 4: ждать :222
                            stage = 4
                            addLog("=== Этап 4/4: Жду SSH :222 (60-90 сек) ===")
                            var sshUp = false
                            for (attempt in 1..40) {
                                delay(3000)
                                val ok = withContext(Dispatchers.IO) { Gboxcore.probeTCP(host, 222, 3000) }
                                if (attempt % 3 == 0 || ok) {
                                    addLog("   попытка $attempt/40 → ${if (ok) "OK!" else "refused"}")
                                }
                                if (ok) { sshUp = true; break }
                            }
                            if (!sshUp) {
                                errMsg = "SSH :222 не поднялся за 120 сек. NDMS может ещё разворачивать installer. Проверьте через 30 сек."
                                addLog("✗ ТАЙМАУТ :222")
                                busy = false; stage = 0; return@launch
                            }

                            // УСПЕХ
                            stage = 5
                            addLog("✓ SSH :222 доступен")
                            addLog("=== УСТАНОВКА ЗАВЕРШЕНА ===")
                            prefs.entwareInstalled = true
                            // ВАЖНО: prefs.adguardInstalled НЕ ставим здесь.
                            // adguardInstalled в этом экране = "пользователь ВЫБРАЛ AdGuard",
                            // а не "AdGuard уже установлен". Реальная установка AdGuard делается
                            // позже на SetupsScreen (там есть полный скрипт через SSH :222),
                            // и только она устанавливает prefs.adguardInstalled=true.
                            prefs.lastInstallTs = System.currentTimeMillis()

                            busy = false
                            onFinished(true, adguardInstalled)
                        } catch (e: Exception) {
                            errMsg = e.message ?: "Ошибка: ${e.javaClass.simpleName}"
                            addLog("✗ ИСКЛЮЧЕНИЕ: ${e.message}")
                            busy = false; stage = 0
                        }
                    }
                },
                enabled = !busy && mediaReady && selectedMedia != null,
                modifier = Modifier.weight(0.65f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeckColors.Sky)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when {
                        busy && stage > 0 -> "Этап $stage/4…"
                        mediaReady -> "Далее →"
                        else -> "Сначала подготовьте флешку ↑"
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        TextButton(
            onClick = onCancel,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Отменить мастер", color = DeckColors.Muted, fontSize = 11.sp)
        }
    }

    // === ДИАЛОГ ПОДГОТОВКИ ФЛЕШКИ ===
    if (showConfirmFormat) {
        val mediaName = selectedMedia?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { if (!busy) showConfirmFormat = false },
            title = { Text("Подготовить флешку в ext4?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ВСЕ ДАННЫЕ на ${selectedMedia?.label ?: "флешке"} будут безвозвратно удалены! " +
                        "Будет создана новая разметка: swap + ext4 (Справочник NDMS 5.1, п. 3.105).",
                        color = DeckColors.Rose, fontSize = 12.sp
                    )
                    Text("Введите EXT4 для подтверждения:", color = DeckColors.Text, fontSize = 12.sp)
                    OutlinedTextField(
                        value = formatWord,
                        onValueChange = { formatWord = it },
                        placeholder = { Text("EXT4") },
                        singleLine = true,
                        enabled = !busy
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && formatWord.trim().equals("EXT4", true) && mediaName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = DeckColors.Rose),
                    onClick = {
                        showConfirmFormat = false
                        formatWord = ""
                        scope.launch {
                            busy = true
                            errMsg = ""
                            log = emptyList()
                            addLog("=== media $mediaName initialize ext4 ===")
                            val res = withContext(Dispatchers.IO) {
                                RciClient.initializeMediaExt4(host, user, pass, mediaName)
                            }
                            res.fold(
                                onSuccess = {
                                    addLog("✓ $it")
                                    addLog("   ждём 10-30 сек, NDMS размечает в фоне…")
                                    for (attempt in 1..20) {
                                        delay(3000)
                                        val check = withContext(Dispatchers.IO) {
                                            RciClient.probePassport(host, user, pass)
                                        }
                                        val found = check.getOrNull()?.mediaList?.firstOrNull { it.name == mediaName }
                                        if (found?.isExt4 == true) {
                                            addLog("✓ флешка готова: ext4 (${found.freeMb}/${found.totalMb} МБ)")
                                            selectedMedia = found
                                            mediaReady = true
                                            break
                                        }
                                        addLog("   попытка $attempt/20: ${found?.fsType ?: "?"}")
                                    }
                                    busy = false
                                },
                                onFailure = {
                                    addLog("✗ ${it.message}")
                                    errMsg = "Не удалось переформатировать: ${it.message}"
                                    busy = false
                                }
                            )
                        }
                    }
                ) { Text("Подготовить", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmFormat = false }, enabled = !busy) { Text("Отмена") }
            }
        )
    }
}

/**
 * Замороженный тумблер — показывает состояние, но НЕ реагирует на клик.
 * Красный "БУДЕТ ВКЛЮЧЕНО" = сейчас выключено, включат на «Далее».
 * Зелёный "ВКЛ" = уже включено.
 */
@Composable
private fun FrozenToggle(title: String, subtitle: String, currentlyOn: Boolean) {
    Surface(
        color = DeckColors.CardAlt,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (currentlyOn) DeckColors.Emerald.copy(0.4f) else DeckColors.Rose.copy(0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (currentlyOn) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                null,
                tint = if (currentlyOn) DeckColors.Emerald else DeckColors.Rose,
                modifier = Modifier.size(16.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(title, color = DeckColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                Text(subtitle, color = DeckColors.Muted, fontSize = 10.5.sp)
            }
            StatusTag(
                if (currentlyOn) "ВКЛ" else "БУДЕТ ВКЛЮЧЕНО",
                if (currentlyOn) DeckColors.Emerald else DeckColors.Rose
            )
        }
    }
}