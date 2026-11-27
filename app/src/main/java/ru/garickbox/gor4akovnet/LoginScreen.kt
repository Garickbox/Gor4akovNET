package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран 1: Логин на роутер через RCI (:80).
 *
 * Пользователь вводит host/user/pass. По нажатию «Подключиться и проверить»
 * - выполняется GET /auth (challenge + cookie),
 * - POST /auth с sha256(challenge + md5(user:realm:pass)),
 * - probePassport (модель, NDMS, media, компоненты, SSH :22/:222).
 *
 * При успехе вызывает onSuccess(passport), родитель (MainActivity) решает
 * куда идти: в мастер (первый запуск) или сразу в основную программу.
 *
 * Пароль НЕ сохраняется здесь — родитель сам решит (EncryptedSharedPrefs или Prefs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    initialHost: String,
    initialUser: String,
    initialPass: String,
    onSuccess: (RouterPassport, String, String, String) -> Unit,
    onSkip: (() -> Unit)? = null
) {
    var host by remember { mutableStateOf(initialHost.ifBlank { "192.168.1.1" }) }
    var user by remember { mutableStateOf(initialUser.ifBlank { "admin" }) }
    var pass by remember { mutableStateOf(initialPass) }
    var passVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errMsg by remember { mutableStateOf("") }
    var stageMsg by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Заголовок
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F1A2E),
                border = BorderStroke(1.dp, Color(0xFF1E3A5F))
            ) {
                Icon(
                    Icons.Outlined.Router,
                    null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.padding(10.dp).size(28.dp)
                )
            }
            Column {
                Text("Gor4akovNET", color = Color(0xFF10B981), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("Вход в роутер по RCI (HTTP :80)", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Поля
        OutlinedTextField(
            value = host,
            onValueChange = { host = it.trim().filterNot { c -> c.isWhitespace() } },
            label = { Text("Хост / IP роутера") },
            placeholder = { Text("192.168.1.1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Логин администратора") },
            placeholder = { Text("admin") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        )
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Пароль") },
            placeholder = { Text("пароль от веб-интерфейса") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        if (passVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passVisible) "Скрыть пароль" else "Показать пароль",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }
        )

        Spacer(Modifier.height(4.dp))

        // Кнопка "Подключиться"
        Button(
            onClick = {
                if (host.isBlank() || pass.isBlank()) {
                    errMsg = "Заполните хост и пароль"
                    return@Button
                }
                scope.launch {
                    busy = true
                    errMsg = ""
                    stageMsg = "GET /auth — запрос challenge…"
                    try {
                        val res = withContext(Dispatchers.IO) {
                            RciClient.probePassport(host, user, pass)
                        }
                        res.fold(
                            onSuccess = { passport ->
                                stageMsg = "OK — ${passport.model}, NDMS ${passport.ndmsMajor}.${passport.ndmsMinor}, " +
                                    "arch ${passport.entwareArch}. SSH :22=${passport.ssh22Open}, :222=${passport.ssh222Open}"
                                busy = false
                                onSuccess(passport, host, user, pass)
                            },
                            onFailure = { e ->
                                errMsg = e.message ?: "Не удалось подключиться"
                                stageMsg = ""
                                busy = false
                            }
                        )
                    } catch (e: Exception) {
                        errMsg = e.message ?: "Ошибка: ${e.javaClass.simpleName}"
                        stageMsg = ""
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF))
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(if (busy) "Проверяю…" else "Подключиться и проверить", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        // Статусы
        if (stageMsg.isNotBlank()) {
            Surface(
                color = Color(0xFF0F1A2E),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stageMsg,
                    color = Color(0xFF93C5FD),
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        if (errMsg.isNotBlank()) {
            Surface(
                color = Color(0xFF3B0F0F),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "⚠️ $errMsg",
                    color = Color(0xFFFCA5A5),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Подсказки
        Surface(
            color = Color(0xFF0F1A2E),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ℹ️ Как это работает:", color = Color(0xFF93C5FD), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Text("• APK логинится на :80 через /auth (challenge + sha256)", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                Text("• Пароль не сохраняется, пока вы не подключитесь успешно", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                Text("• Роутер должен быть уже настроен (задан пароль админа)", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                Text("• После сброса прошивки сначала пройдите веб-setup http://192.168.1.1", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
            }
        }

        if (onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Пропустить (у меня уже настроен роутер)", color = Color(0xFF64748B), fontSize = 11.sp)
            }
        }
    }
}