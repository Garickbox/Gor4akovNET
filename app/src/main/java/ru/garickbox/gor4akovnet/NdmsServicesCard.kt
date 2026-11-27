package ru.garickbox.gor4akovnet

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
    return Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""").find(raw)?.value ?: ""
}