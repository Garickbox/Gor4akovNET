package ru.garickbox.gor4akovnet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppLockScreen(
    isFirstSetup: Boolean,
    onUnlocked: (isPrivileged: Boolean) -> Unit,
    verifyPin: (String) -> Pair<Boolean, Boolean>,
    createPin: (String) -> Boolean
) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Surface(
        color = DeckColors.Bg,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            DeckCard(borderColor = DeckColors.Emerald.copy(0.45f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = DeckColors.Emerald, modifier = Modifier.size(26.dp))
                    Column {
                        Text(
                            if (isFirstSetup) "Защита приложения" else "Вход в Gor4akovNET",
                            color = DeckColors.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            if (isFirstSetup) "Придумайте пароль для защиты доступа к настройкам роутера"
                            else "Введите ваш пароль приложения",
                            color = DeckColors.Muted,
                            fontSize = 12.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = pin1,
                    onValueChange = { pin1 = it; error = "" },
                    label = { Text(if (isFirstSetup) "Новый пароль (от 4 символов)" else "Пароль") },
                    singleLine = true,
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(if (showPin) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isFirstSetup) {
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { pin2 = it; error = "" },
                        label = { Text("Повторите пароль") },
                        singleLine = true,
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (error.isNotBlank()) {
                    Text(error, color = DeckColors.Rose, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val p1 = pin1.trim()
                        if (isFirstSetup) {
                            if (p1.length < 4) {
                                error = "Минимум 4 символа"
                                return@Button
                            }
                            if (p1 != pin2.trim()) {
                                error = "Пароли не совпадают"
                                return@Button
                            }
                            val priv = createPin(p1)
                            onUnlocked(priv)
                        } else {
                            if (p1.isBlank()) {
                                error = "Введите пароль"
                                return@Button
                            }
                            val (ok, priv) = verifyPin(p1)
                            if (ok) {
                                onUnlocked(priv)
                            } else {
                                error = "Неверный пароль"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isFirstSetup) "Сохранить и войти" else "Войти")
                }
            }
        }
    }
}
