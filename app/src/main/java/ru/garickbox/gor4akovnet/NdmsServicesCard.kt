package ru.garickbox.gor4akovnet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NdmsServiceEntry(
    val name: String,
    val family: String,
    val proto: String,
    val port: String,
    val security: String
)

fun parseNdmsIpService(raw: String): List<NdmsServiceEntry> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.replace('~', '\n').lines()
    val result = mutableListOf<NdmsServiceEntry>()
    var cur = mutableMapOf<String, String>()
    var inBlock = false

    fun flush() {
        val name = cur["service-name"].orEmpty().trim()
        if (name.isNotBlank()) {
            result.add(
                NdmsServiceEntry(
                    name = name,
                    family = cur["family"].orEmpty().trim(),
                    proto = cur["protocol"].orEmpty().trim(),
                    port = cur["port"].orEmpty().trim(),
                    security = cur["security-level"].orEmpty().trim()
                )
            )
        }
        cur = mutableMapOf()
    }

    for (ln in lines) {
        val t = ln.trim()
        if (t == "service:") { flush(); inBlock = true; continue }
        if (!inBlock || t.isEmpty()) continue
        val idx = t.indexOf(':')
        if (idx > 0) cur[t.substring(0, idx).trim()] = t.substring(idx + 1).trim()
    }
    flush()
    return result
}

fun parseNdmsCifs(raw: String): Pair<Boolean, Int> {
    if (raw.isBlank()) return false to 0
    var enabled = false
    var shares = 0
    for (ln in raw.replace('~', '\n').lines()) {
        val t = ln.trim()
        if (t.startsWith("enabled:")) enabled = t.substringAfter("enabled:").trim().equals("yes", true)
        if (t == "share:") shares++
    }
    return enabled to shares
}

fun parseNdmsWebdav(raw: String): Boolean {
    if (raw.isBlank()) return false
    return raw.replace('~', '\n').lines().any {
        it.trim().startsWith("enabled:") && it.trim().substringAfter("enabled:").trim().equals("yes", true)
    }
}

fun parseNdmsDns(raw: String): String {
    if (raw.isBlank()) return ""
    for (ln in raw.replace('~', '\n').lines()) {
        val t = ln.trim()
        if (t.startsWith("address:")) {
            val v = t.substringAfter("address:").trim()
            if (v.isNotBlank()) return v
        }
    }
    val parts = mutableListOf<String>()
    val sb = StringBuilder()
    for (ch in raw) {
        if (ch.isDigit() || ch == '.') sb.append(ch)
        else if (sb.isNotEmpty()) { parts.add(sb.toString()); sb.clear() }
    }
    if (sb.isNotEmpty()) parts.add(sb.toString())
    for (v in parts) {
        val p = v.split('.')
        if (p.size != 4) continue
        var ok = true
        for (seg in p) {
            val n = seg.toIntOrNull() ?: -1
            if (n < 0 || n > 255) { ok = false; break }
        }
        if (ok) return v
    }
    return ""
}

@Composable
fun NdmsServicesCard(
    ipServiceRaw: String,
    cifsRaw: String,
    webdavRaw: String,
    dnsRaw: String
) {
    val services = parseNdmsIpService(ipServiceRaw)
    val (cifsEnabled, cifsShares) = parseNdmsCifs(cifsRaw)
    val webdavEnabled = parseNdmsWebdav(webdavRaw)
    val dns = parseNdmsDns(dnsRaw)
    if (services.isEmpty() && !cifsEnabled && !webdavEnabled && dns.isBlank()) return
    MiniCard("Открытые порты служб NDMS (3.178.71)") {
        for ((name, list) in services.groupBy { it.name }.toSortedMap()) {
            val ports = list.joinToString(", ") { "${it.proto}/${it.port}" }
            val sec = list.firstOrNull()?.security.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name.ifBlank { "?" }, color = DeckColors.Text, fontSize = 11.5.sp)
                Text(ports, color = DeckColors.Sky, fontSize = 11.sp)
                if (sec.isNotBlank()) {
                    StatusTag(sec, if (sec == "private") DeckColors.Emerald
                        else if (sec == "protected") DeckColors.Amber else DeckColors.Rose)
                }
            }
        }
    }
}