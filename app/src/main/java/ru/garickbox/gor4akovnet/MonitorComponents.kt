package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun formatUptime(sec: Long): String {
    if (sec <= 0L) return "—"
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return if (d > 0) "${d}д ${h}ч ${m}м" else "${h}ч ${m}м"
}

internal fun formatBytes(b: Double): String = when {
    b <= 0.0 -> "0 Б"
    b >= 1073741824.0 -> String.format("%.2f ГБ", b / 1073741824.0)
    b >= 1048576.0 -> String.format("%.1f МБ", b / 1048576.0)
    b >= 1024.0 -> String.format("%.0f КБ", b / 1024.0)
    else -> String.format("%.0f Б", b)
}

internal fun formatSizeMb(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f МБ", bytes / 1048576.0)
    bytes >= 1024L -> "${bytes / 1024L} КБ"
    else -> "$bytes Б"
}

@Composable
internal fun HelpSection(label: String, text: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color(0xFFE2E8F0), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
internal fun HelpBadge(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(17.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E293B))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun MiniCard(
    title: String,
    helpClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = Color(0xFF93C5FD),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (helpClick != null) HelpBadge(helpClick)
            }
            content()
        }
    }
}

@Composable
internal fun MiniRow(
    label: String,
    value: String,
    color: Color = Color.White,
    helpClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                label,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (helpClick != null) HelpBadge(helpClick)
        }
        Text(
            value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun MiniBar(label: String, detail: String, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val c = when {
        p > 0.85f -> Color(0xFFEF4444)
        p > 0.65f -> Color(0xFFF59E0B)
        else -> Color(0xFF3B82F6)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp)
            Text(detail, color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = c,
            trackColor = Color(0xFF1E293B)
        )
    }
}

@Composable
internal fun MiniSwitch(
    title: String,
    sub: String,
    checked: Boolean,
    helpClick: (() -> Unit)? = null,
    onToggle: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                if (helpClick != null) HelpBadge(helpClick)
            }
            Text(sub, color = Color(0xFF94A3B8), fontSize = 9.5.sp, lineHeight = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(if (it) 1 else 0) },
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
internal fun MiniPill(
    label: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        color = if (ok) Color(0xFF052E16) else Color(0xFF1E293B),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (ok) Color(0xFF16A34A) else Color(0xFF334155)),
        modifier = m
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (ok) Color(0xFF22C55E) else Color(0xFF64748B)))
            Text(
                label,
                color = if (ok) Color(0xFF86EFAC) else Color(0xFF94A3B8),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun MonoBox(text: String, onCopy: (() -> Unit)? = null) {
    Surface(
        color = Color(0xFF020617),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Text(
                text,
                color = Color(0xFFCBD5E1),
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(7.dp)
                    .padding(end = if (onCopy != null) 22.dp else 0.dp)
            )
            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        "Копировать",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WrapRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}
