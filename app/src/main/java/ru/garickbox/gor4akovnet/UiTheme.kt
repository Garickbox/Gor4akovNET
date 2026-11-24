package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object DeckColors {
    val Bg = Color(0xFF0A0D14)
    val Card = Color(0xFF121722)
    val CardAlt = Color(0xFF171E2C)
    val Border = Color(0xFF232D42)
    val Text = Color(0xFFE8EEF8)
    val Muted = Color(0xFF8C9AB5)
    val Emerald = Color(0xFF00E5A3)
    val Sky = Color(0xFF38BDF8)
    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFF43F5E)
}

private val DarkScheme = darkColorScheme(
    primary = DeckColors.Emerald,
    onPrimary = Color(0xFF042218),
    secondary = DeckColors.Sky,
    background = DeckColors.Bg,
    surface = DeckColors.Card,
    onBackground = DeckColors.Text,
    onSurface = DeckColors.Text,
    surfaceVariant = DeckColors.CardAlt,
    onSurfaceVariant = DeckColors.Muted,
    outline = DeckColors.Border,
    error = DeckColors.Rose
)

@Composable
fun Gor4akovTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}

@Composable
fun DeckCard(
    modifier: Modifier = Modifier,
    borderColor: Color = DeckColors.Border,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = DeckColors.Card,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun StatusTag(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun WiFiQrCard(ssid: String, pass: String, subnetInfo: String = "192.168.77.0/24") {
    val clipboard = LocalClipboardManager.current
    val qrPayload = remember(ssid, pass) {
        val escS = ssid.replace("\\", "\\\\").replace(";", "\\;").replace(":", "\\:")
        val escP = pass.replace("\\", "\\\\").replace(";", "\\;").replace(":", "\\:")
        "WIFI:T:WPA;S:$escS;P:$escP;;"
    }
    val matrix = remember(qrPayload) {
        runCatching { QRCodeWriter().encode(qrPayload, BarcodeFormat.QR_CODE, 49, 49) }.getOrNull()
    }

    DeckCard(borderColor = DeckColors.Emerald.copy(alpha = 0.55f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Сеть готова к подключению", color = DeckColors.Emerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Изолированный сегмент $subnetInfo", color = DeckColors.Muted, fontSize = 12.sp)
            }
            StatusTag("ACTIVE", DeckColors.Emerald)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (matrix != null) {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = matrix.width
                        val h = matrix.height
                        val cellW = size.width / w
                        val cellH = size.height / h
                        for (y in 0 until h) {
                            for (x in 0 until w) {
                                if (matrix.get(x, y)) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(x * cellW, y * cellH),
                                        size = Size(cellW + 0.4f, cellH + 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Wi-Fi: $ssid", color = DeckColors.Text, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Пароль: $pass", color = DeckColors.Sky, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(pass)) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Копировать пароль", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeckColors.Card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = DeckColors.Emerald)
                Text("Справка Gor4akovNET", color = DeckColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GboxHelp.TOPICS.forEach { topic ->
                    Surface(
                        color = DeckColors.CardAlt,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DeckColors.Border)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(topic.title, color = DeckColors.Emerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(topic.summary, color = DeckColors.Text, fontSize = 12.sp)
                            topic.steps.forEachIndexed { idx, step ->
                                Text("${idx + 1}. $step", color = DeckColors.Muted, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно", color = DeckColors.Emerald)
            }
        }
    )
}
