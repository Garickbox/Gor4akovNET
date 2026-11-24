package ru.garickbox.gor4akovnet
import androidx.compose.foundation.layout.Arrangement

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectWizardScreen(
    prefs: Prefs,
    passport: RouterPassport?,
    onPassportUpdated: (RouterPassport) -> Unit,
    onBackupSaved: (BackupEntry) -> Unit,
    onGoToSetups: () -> Unit,
    onGoToMonitor: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf(prefs.routerHost.ifBlank { RciClient.detectDefaultGateway(context) }) }
    var rciUser by remember { mutableStateOf(prefs.rciUser) }
    var rciPass by remember { mutableStateOf(prefs.rciPass) }
    var showRciPass by remember { mutableStateOf(false) }

    var sshPortStr by remember { mutableStateOf(prefs.sshPort.toString()) }
    var sshUser by remember { mutableStateOf(prefs.sshUser) }
    var sshPass by remember { mutableStateOf(prefs.sshPass) }
    var showSshPass by remember { mutableStateOf(false) }

    var currentStep by remember { mutableIntStateOf(if (passport?.ssh222Open == true) 4 else 0) }
    var busy by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var autoBackupDone by remember { mutableStateOf(false) }

    val offlineInfo = remember { OfflineAssets.inspectManifest(context) }

    fun refreshPassport(saveInitialBackup: Boolean = false, advanceStep: Boolean = false) {
        scope.launch {
            busy = true
            errorMsg = ""
            statusMsg = "Подключение к роутеру $host..."
            prefs.routerHost = host
            prefs.rciUser = rciUser
            prefs.rciPass = rciPass
            val res = RciClient.probePassport(host, rciUser, rciPass)
            res.onSuccess { p ->
                onPassportUpdated(p)
                if (saveInitialBackup && !autoBackupDone) {
                    val cfgRes = RciClient.fetchRunningConfig(host, rciUser, rciPass)
                    cfgRes.onSuccess { cfgText ->
                        val entry = prefs.saveBackup("Снимок до настройки", p.model, cfgText)
                        onBackupSaved(entry)
                        autoBackupDone = true
                    }
                }
                statusMsg = "Паспорт получен: ${p.model} (${p.ndmsTitle}, ${p.entwareArch})"
                if (advanceStep) {
                    currentStep = when {
                        p.ssh222Open -> 4
                        p.hasOpkgComp && p.hasNetfilterComp && p.mediaList.any { it.isExt4 || it.isInternalStorage } -> 3
                        p.mediaList.any { it.isExt4 || it.isInternalStorage } -> 2
                        else -> 1
                    }
                }
            }.onFailure {
                errorMsg = it.message ?: "Не удалось подключиться к роутеру"
                statusMsg = ""
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stepper header
        val stepTitles = listOf("1. Вход", "2. USB", "3. Компоненты", "4. SSH :222", "5. Готово")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stepTitles.forEachIndexed { idx, label ->
                val active = currentStep == idx
                val done = when (idx) {
                    0 -> passport != null
                    1 -> passport?.mediaList?.any { it.isExt4 || it.isInternalStorage } == true
                    2 -> passport?.hasOpkgComp == true && passport.hasNetfilterComp
                    3 -> passport?.ssh222Open == true
                    4 -> passport?.ssh222Open == true
                    else -> false
                }
                Surface(
                    onClick = { if (passport != null || idx == 0) currentStep = idx },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        active -> DeckColors.Emerald.copy(alpha = 0.2f)
                        done -> DeckColors.CardAlt
                        else -> DeckColors.Card
                    },
                    border = BorderStroke(1.dp, if (active) DeckColors.Emerald else if (done) DeckColors.Emerald.copy(0.4f) else DeckColors.Border)
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active || done) DeckColors.Emerald else DeckColors.Muted,
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 3.dp),
                        maxLines = 1
                    )
                }
            }
        }

        if (statusMsg.isNotBlank()) {
            Surface(color = DeckColors.Emerald.copy(0.12f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, DeckColors.Emerald.copy(0.4f))) {
                Text(statusMsg, color = DeckColors.Emerald, fontSize = 12.sp, modifier = Modifier.padding(10.dp).fillMaxWidth())
            }
        }
        if (errorMsg.isNotBlank()) {
            Surface(color = DeckColors.Rose.copy(0.12f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, DeckColors.Rose.copy(0.4f))) {
                Text(errorMsg, color = DeckColors.Rose, fontSize = 12.sp, modifier = Modifier.padding(10.dp).fillMaxWidth())
            }
        }

        when (currentStep) {
            0 -> {
                DeckCard {
                    Text("Подключение к роутеру Keenetic / Netcraze", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Подключитесь к Wi-Fi роутера и введите данные от веб-интерфейса. При первом входе приложение автоматически сохранит резервную копию настроек.", color = DeckColors.Muted, fontSize = 12.5.sp)

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP-адрес роутера") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rciUser,
                        onValueChange = { rciUser = it },
                        label = { Text("Логин админа") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rciPass,
                        onValueChange = { rciPass = it },
                        label = { Text("Пароль от веб-интерфейса") },
                        singleLine = true,
                        visualTransformation = if (showRciPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showRciPass = !showRciPass }) {
                                Icon(if (showRciPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { refreshPassport(saveInitialBackup = true, advanceStep = true) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Router, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Подключиться и проверить")
                        }
                    }
                }

                if (passport != null) {
                    PassportSummaryCard(passport)
                }

                DeckCard(borderColor = DeckColors.Sky.copy(0.35f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Inventory2, null, tint = DeckColors.Sky)
                        Text("Встроенный офлайн-пакет APK", color = DeckColors.Sky, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                    Text(
                        if (offlineInfo.available)
                            "Готов: Entware (${offlineInfo.archesReady.joinToString(", ")}) + системные .ipk + z2k ${offlineInfo.z2kTag} (${"%.1f".format(offlineInfo.totalMb)} МБ). Установка работает даже без интернета на роутере."
                        else
                            "Сборка запущена в dev-режиме без локальных архивов — файлы загрузятся с зеркал во время установки.",
                        color = DeckColors.Muted,
                        fontSize = 12.sp
                    )
                }
            }

            1 -> {
                val mediaList = passport?.mediaList.orEmpty()
                val goodUsb = mediaList.firstOrNull { it.isUsb && it.isExt4 }
                val nonExt4Usb = mediaList.firstOrNull { it.isUsb && !it.isExt4 }
                val internalStorage = mediaList.firstOrNull { it.isInternalStorage && it.freeMb >= 32 }

                DeckCard {
                    Text("Шаг 2: Накопитель (USB или внутренняя память)", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Entware и модули обхода требуют раздел ext4 (предпочтительно USB-флешка от 2 ГБ) или встроенную память на KeeneticOS 3.7+.", color = DeckColors.Muted, fontSize = 12.5.sp)

                    if (mediaList.isEmpty()) {
                        StatusTag("НАКОПИТЕЛИ НЕ НАЙДЕНЫ", DeckColors.Amber)
                        Text("Вставьте USB-флешку в роутер, отформатируйте её в ext4 через веб-меню «Накопители» и нажмите «Проверить USB».", color = DeckColors.Text, fontSize = 13.sp)
                    } else {
                        mediaList.forEach { m ->
                            val ok = m.isExt4 || (m.isInternalStorage && m.freeMb >= 32)
                            Surface(
                                color = DeckColors.CardAlt,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (ok) DeckColors.Emerald.copy(0.5f) else DeckColors.Amber.copy(0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${m.name} (${m.label})", color = DeckColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("ФС: ${m.fsType.ifBlank { "flash" }} · Свободно ${m.freeMb} / ${m.totalMb} МБ", color = DeckColors.Muted, fontSize = 11.5.sp)
                                    }
                                    StatusTag(if (ok) "ПОДХОДИТ" else "НУЖЕН EXT4", if (ok) DeckColors.Emerald else DeckColors.Amber)
                                }
                            }
                        }
                    }

                    if (nonExt4Usb != null && goodUsb == null) {
                        Text("Флешка ${nonExt4Usb.label} сейчас в ${nonExt4Usb.fsType}. Откройте веб-меню роутера -> Приложения -> Накопители и отформатируйте её в ext4.", color = DeckColors.Amber, fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$host")))
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Открыть меню роутера")
                        }
                        Button(
                            onClick = { refreshPassport() },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Проверить USB")
                        }
                    }

                    if (goodUsb != null || internalStorage != null) {
                        Button(
                            onClick = { currentStep = 2 },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeckColors.Sky)
                        ) {
                            Text("Далее -> Компоненты прошивки")
                        }
                    }
                }
            }

            2 -> {
                val p = passport
                DeckCard {
                    Text("Шаг 3: Системные компоненты прошивки", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Проверьте наличие обязательных модулей KeeneticOS / Netcraze:", color = DeckColors.Muted, fontSize = 12.5.sp)

                    ComponentRow("Поддержка открытых пакетов OPKG", "opkg", p?.hasOpkgComp == true, required = true)
                    ComponentRow("Модули ядра подсистемы Netfilter", "netfilter / xtables", p?.hasNetfilterComp == true, required = true)
                    ComponentRow("Сервер WebDAV (для авто-заливки с телефона)", "webdav", p?.hasWebdavComp == true, required = false)

                    if (p?.hasOpkgComp != true || p.hasNetfilterComp != true) {
                        Surface(color = DeckColors.CardAlt, shape = RoundedCornerShape(8.dp)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Что нужно сделать на роутере:", color = DeckColors.Amber, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text("1. Нажмите «Открыть компоненты» ниже.", color = DeckColors.Text, fontSize = 12.sp)
                                Text("2. В «Параметры системы -> Изменить набор компонентов» отметьте недостающие пункты (и «Сервер WebDAV» для авто-установки без ПК).", color = DeckColors.Text, fontSize = 12.sp)
                                Text("3. После перезагрузки роутера нажмите «Проверить компоненты».", color = DeckColors.Text, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$host/#controlPanel/components")))
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Открыть компоненты")
                        }
                        Button(
                            onClick = { refreshPassport() },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Проверить компоненты")
                        }
                    }

                    if (p?.hasOpkgComp == true && p.hasNetfilterComp) {
                        Button(
                            onClick = { currentStep = 3 },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeckColors.Sky)
                        ) {
                            Text("Далее -> Установка Entware и SSH :222")
                        }
                    }
                }
            }

            3 -> {
                val p = passport
                val targetMedia = p?.mediaList?.firstOrNull { it.isUsb && it.isExt4 }
                    ?: p?.mediaList?.firstOrNull { it.isInternalStorage }

                DeckCard {
                    Text("Шаг 4: Entware и доступ по SSH (:222)", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Для модели ${p?.model ?: "-"} нужна сборка ${p?.entwareArch ?: "-"} (${p?.entwareInstaller ?: "-"}).", color = DeckColors.Muted, fontSize = 12.5.sp)

                    if (p?.ssh222Open == true) {
                        StatusTag("DROPBEAR SSH :222 ДОСТУПЕН", DeckColors.Emerald)
                    } else {
                        StatusTag("SSH :222 ЕЩЁ НЕ ЗАПУЩЕН", DeckColors.Amber)
                        if (targetMedia != null && p?.hasWebdavComp == true) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        errorMsg = ""
                                        val res = OfflineAssets.uploadEntwareViaWebdavAndConfigure(
                                            context = context,
                                            passport = p,
                                            user = rciUser,
                                            pass = rciPass,
                                            targetMedia = targetMedia
                                        ) { msg -> statusMsg = msg }
                                        res.onSuccess { msg ->
                                            statusMsg = msg
                                            repeat(12) {
                                                delay(4000)
                                                val ok = withContext(Dispatchers.IO) { Gboxcore.probeTCP(host, 222, 1200) }
                                                if (ok) {
                                                    refreshPassport()
                                                    return@repeat
                                                }
                                            }
                                        }.onFailure {
                                            errorMsg = it.message ?: "Ошибка авто-заливки Entware"
                                        }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Outlined.CloudUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Развернуть Entware из APK по WebDAV")
                            }
                        } else {
                            Surface(color = DeckColors.CardAlt, shape = RoundedCornerShape(8.dp)) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Ручной способ (если WebDAV не включён):", color = DeckColors.Sky, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    Text("1. Включите «Сервер WebDAV» в компонентах и повторите, либо создайте на флешке папку /install/ и положите туда ${p?.entwareInstaller ?: "installer.tar.gz"}.", color = DeckColors.Text, fontSize = 12.sp)
                                    Text("2. В настройках OPKG роутера выберите накопитель и путь /opt/etc/init.d/rc.unslung.", color = DeckColors.Text, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DeckColors.Border)
                    Text("Параметры SSH Entware (по умолчанию root / keenetic, порт 222):", color = DeckColors.Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sshPortStr,
                            onValueChange = { sshPortStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Порт") },
                            modifier = Modifier.weight(0.32f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sshUser,
                            onValueChange = { sshUser = it },
                            label = { Text("Логин") },
                            modifier = Modifier.weight(0.68f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = sshPass,
                        onValueChange = { sshPass = it },
                        label = { Text("Пароль SSH") },
                        singleLine = true,
                        visualTransformation = if (showSshPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSshPass = !showSshPass }) {
                                Icon(if (showSshPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = { sshPass = "keenetic" }, label = { Text("keenetic", fontSize = 11.sp) })
                        AssistChip(onClick = { sshPass = "" }, label = { Text("Пустой", fontSize = 11.sp) })
                        AssistChip(onClick = { sshPass = rciPass }, label = { Text("Как RCI", fontSize = 11.sp) })
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    val port = sshPortStr.toIntOrNull() ?: 222
                                    val found = withContext(Dispatchers.IO) {
                                        runCatching { Gboxcore.tryPasswordsSSH(host, port, sshUser, rciPass) }.getOrNull()
                                    }
                                    if (found != null) sshPass = found
                                }
                            },
                            label = { Text("Автоподбор", fontSize = 11.sp) }
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                errorMsg = ""
                                val port = sshPortStr.toIntOrNull() ?: 222
                                prefs.sshPort = port
                                prefs.sshUser = sshUser
                                prefs.sshPass = sshPass
                                val res = withContext(Dispatchers.IO) {
                                    runCatching { Gboxcore.runRouterSSH(host, port, sshUser, sshPass, "uname -a && which opkg") }
                                }
                                res.onSuccess { out ->
                                    statusMsg = "SSH подключён!\n$out"
                                    prefs.wizardCompleted = true
                                    refreshPassport()
                                    currentStep = 4
                                }.onFailure {
                                    errorMsg = "SSH недоступен: ${it.message}"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Проверить SSH :${sshPortStr.ifBlank { "222" }}")
                    }
                }
            }

            4 -> {
                DeckCard(borderColor = DeckColors.Emerald.copy(0.6f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = DeckColors.Emerald)
                        Text("Роутер полностью готов!", color = DeckColors.Emerald, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text("Связь по RCI и Entware SSH (:${prefs.sshPort}) подтверждена. Первичный бэкап конфига сохранён в телефоне.", color = DeckColors.Text, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onGoToSetups,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Перейти в Сетапы ->")
                        }
                        OutlinedButton(
                            onClick = onGoToMonitor,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Открыть Монитор")
                        }
                    }
                }
                if (passport != null) PassportSummaryCard(passport)
            }
        }
    }
}

@Composable
private fun PassportSummaryCard(p: RouterPassport) {
    val badgeColor = when (p.compatLevel) {
        CompatLevel.FULL -> DeckColors.Emerald
        CompatLevel.USB_ONLY -> DeckColors.Amber
        else -> DeckColors.Rose
    }
    DeckCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(p.model, color = DeckColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            StatusTag(
                when (p.compatLevel) {
                    CompatLevel.FULL -> "ПОЛНАЯ СОВМЕСТИМОСТЬ"
                    CompatLevel.USB_ONLY -> "ТОЛЬКО USB EXT4"
                    else -> "ПРОВЕРЬТЕ ПРОШИВКУ"
                },
                badgeColor
            )
        }
        Text("Прошивка: ${p.ndmsTitle} · Арх: ${p.entwareArch} · RAM: ${p.ramTotalMb} МБ", color = DeckColors.Sky, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(p.compatReason, color = DeckColors.Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusTag(if (p.hasOpkgComp) "OPKG OK" else "НЕТ OPKG", if (p.hasOpkgComp) DeckColors.Emerald else DeckColors.Rose)
            StatusTag(if (p.hasNetfilterComp) "NETFILTER OK" else "НЕТ NETFILTER", if (p.hasNetfilterComp) DeckColors.Emerald else DeckColors.Rose)
            StatusTag(if (p.ssh222Open) "SSH :222" else "SSH ОФФ", if (p.ssh222Open) DeckColors.Emerald else DeckColors.Amber)
            StatusTag(if (p.wanOnline) "WAN OK" else "ОФФЛАЙН", if (p.wanOnline) DeckColors.Emerald else DeckColors.Amber)
        }
    }
}

@Composable
private fun ComponentRow(title: String, key: String, ok: Boolean, required: Boolean) {
    Surface(
        color = DeckColors.CardAlt,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (ok) DeckColors.Emerald.copy(0.45f) else if (required) DeckColors.Rose.copy(0.45f) else DeckColors.Border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = DeckColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(if (required) "Обязательно ($key)" else "Рекомендуется ($key)", color = DeckColors.Muted, fontSize = 11.sp)
            }
            StatusTag(if (ok) "ЕСТЬ" else "НЕТ", if (ok) DeckColors.Emerald else if (required) DeckColors.Rose else DeckColors.Amber)
        }
    }
}
