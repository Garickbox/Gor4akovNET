package ru.garickbox.gor4akovnet

import org.json.JSONArray
import org.json.JSONObject

internal object NdmsConfigParser {
    fun flattenConfigLinesForSsh(cfgText: String): List<String> {
        val out = mutableListOf<String>()
        var block = ""
        for (rawLine in cfgText.lines()) {
            val cleanEnd = rawLine.trimEnd()
            val trimmed = cleanEnd.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("!")) {
                if (trimmed == "!") block = ""
                continue
            }
            val indented = cleanEnd.firstOrNull()?.let { it == ' ' || it == '\t' } == true
            val fullCmd = if (indented && block.isNotBlank()) "$block $trimmed" else trimmed
            if (!indented) block = trimmed
            // Исключения при восстановлении снимка (как в restoreArchiveCmd):
            //  - user / no user / system mode — не блокируем себя же;
            //  - opkg disk — привязка флешки делается отдельно по UUID, чужой UUID
            //    на другом роутере оставит Entware неактивным;
            //  - interface Bridge0 ip address / mac address — MGMT-LAN, применение
            //    чужого IP = потеря доступа к роутеру (лечится только сбросом).
            // Остальные BridgeN и их IP восстанавливаем — это пользовательские сети.
            if (fullCmd.startsWith("user ") || fullCmd.startsWith("no user ") || fullCmd.startsWith("system mode")) continue
            if (fullCmd.startsWith("opkg disk ") || fullCmd.startsWith("no opkg disk")) continue
            if (fullCmd.startsWith("interface Bridge0 ip address") || fullCmd.startsWith("interface Bridge0 mac address")) continue
            out.add(fullCmd)
        }
        return out
    }

    fun formatParseOutput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "(пустой ответ)"
        return runCatching {
            val item = when {
                trimmed.startsWith("[") -> JSONArray(trimmed).optJSONObject(0)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> null
            } ?: return@runCatching trimmed
            val p = item.optJSONObject("parse") ?: item
            val msgArr = p.optJSONArray("message")
            val stArr = p.optJSONArray("status")
            val sb = StringBuilder()
            if (msgArr != null) {
                for (i in 0 until msgArr.length()) {
                    sb.append(msgArr.optString(i)).append('\n')
                }
            }
            if (stArr != null) {
                for (i in 0 until stArr.length()) {
                    val s = stArr.optJSONObject(i) ?: continue
                    val m = s.optString("message", "")
                    if (m.isNotBlank()) sb.append("[${s.optString("status")}] $m\n")
                }
            }
            val out = sb.toString().trim()
            if (out.isNotBlank()) out else p.toString(2).take(4000)
        }.getOrDefault(trimmed.take(4000))
    }

    fun parseTopValue(cfg: String, prefix: String): String? {
        for (line in cfg.lines()) {
            val t = line.trim()
            if (t.startsWith(prefix)) {
                return t.removePrefix(prefix).trim().trim('"')
            }
        }
        return null
    }

    fun parseInterfacesFromConfig(cfg: String): Map<String, NdmsIfaceInfo> {
        val map = linkedMapOf<String, NdmsIfaceInfo>()
        var curId = ""
        var desc = ""
        var up = true
        var ip = ""
        var mask = ""
        var isGlobal = false
        var mtu = 0
        var group = ""

        fun flush() {
            if (curId.isBlank()) return
            map[curId] = NdmsIfaceInfo(
                id = curId,
                description = desc,
                type = classifyIfaceType(curId),
                group = group,
                up = up,
                connected = up,
                ip = ip,
                mask = mask,
                mtu = mtu,
                isGlobal = isGlobal
            )
            curId = ""; desc = ""; up = true; ip = ""; mask = ""; isGlobal = false; mtu = 0; group = ""
        }

        for (raw in cfg.lines()) {
            val cleanEnd = raw.trimEnd()
            val t = cleanEnd.trim()
            val indented = cleanEnd.firstOrNull()?.let { it == ' ' || it == '\t' } == true
            if (!indented) {
                if (t.startsWith("interface ")) {
                    flush()
                    curId = t.removePrefix("interface ").trim()
                } else if (t == "!" || curId.isNotBlank()) {
                    flush()
                }
                continue
            }
            if (curId.isBlank()) continue
            when {
                t.startsWith("description ") -> desc = t.removePrefix("description ").trim()
                t == "down" -> up = false
                t == "up" -> up = true
                t.startsWith("ip address ") -> {
                    val parts = t.removePrefix("ip address ").trim().split(Regex("\\s+"))
                    if (parts.isNotEmpty() && !parts[0].equals("dhcp", true)) {
                        ip = parts[0]
                        mask = parts.getOrNull(1).orEmpty()
                    }
                }
                t.startsWith("ip global ") -> isGlobal = true
                t.startsWith("ip mtu ") -> mtu = t.removePrefix("ip mtu ").trim().toIntOrNull() ?: 0
                t.startsWith("security-level ") -> group = t.removePrefix("security-level ").trim()
            }
        }
        flush()
        return map
    }

    fun parseInterfacesFromRci(raw: String): Map<String, NdmsIfaceInfo> {
        val map = linkedMapOf<String, NdmsIfaceInfo>()
        runCatching {
            val trimmed = raw.trim()
            val obj = if (trimmed.startsWith("{")) JSONObject(trimmed) else return@runCatching
            obj.keys().forEach { id ->
                val o = obj.optJSONObject(id) ?: return@forEach
                val type = o.optString("type", "").ifBlank { classifyIfaceType(id) }
                val up = o.optString("state", "").equals("up", true) ||
                    o.optBoolean("up", false) ||
                    o.optString("link", "").equals("up", true)
                val conn = o.optString("connected", "").equals("yes", true) ||
                    o.optBoolean("connected", false) ||
                    o.optString("link", "").equals("up", true)
                map[id] = NdmsIfaceInfo(
                    id = id,
                    description = o.optString("description", ""),
                    type = type,
                    group = o.optString("group", o.optString("security-level", "")),
                    up = up,
                    connected = conn,
                    ip = o.optString("address", ""),
                    mask = o.optString("mask", ""),
                    mtu = o.optInt("mtu", 0),
                    isGlobal = o.optBoolean("global", false),
                    mac = o.optString("mac", "")
                )
            }
        }
        return map
    }

    fun mergeInterfaces(
        cfg: Map<String, NdmsIfaceInfo>,
        rci: Map<String, NdmsIfaceInfo>
    ): List<NdmsIfaceInfo> {
        val keys = (rci.keys + cfg.keys).distinct()
        return keys.map { id ->
            val c = cfg[id]
            val r = rci[id]
            when {
                r != null && c != null -> r.copy(
                    description = r.description.ifBlank { c.description },
                    ip = r.ip.ifBlank { c.ip },
                    mask = r.mask.ifBlank { c.mask },
                    mtu = if (r.mtu > 0) r.mtu else c.mtu,
                    isGlobal = r.isGlobal || c.isGlobal,
                    group = r.group.ifBlank { c.group }
                )
                r != null -> r
                else -> c!!
            }
        }
    }

    private fun classifyIfaceType(id: String): String {
        val u = id.uppercase()
        return when {
            u.startsWith("WIREGUARD") || u.startsWith("WG") -> "WireGuard"
            u.startsWith("OPENVPN") -> "OpenVPN"
            u.startsWith("SSTP") || u.startsWith("PPTP") || u.startsWith("L2TP") -> "VPN"
            u.startsWith("USBLTE") || u.startsWith("USBMODEM") || u.startsWith("CDC") -> "USB Модем"
            u.startsWith("BRIDGE") -> "Bridge LAN"
            u.startsWith("WIFIMASTER") -> "Wi-Fi"
            u.startsWith("ISP") || u.contains("VLAN") || u.startsWith("PPPOE") -> "WAN / Порт"
            else -> "Интерфейс"
        }
    }

    fun parsePoliciesFromConfig(cfg: String): List<NdmsPolicyInfo> {
        val list = mutableListOf<NdmsPolicyInfo>()
        var curName = ""
        var desc = ""
        val members = mutableListOf<Pair<String, Int>>()

        fun flush() {
            if (curName.isNotBlank()) {
                list.add(NdmsPolicyInfo(curName, desc, members.sortedBy { it.second }))
            }
            curName = ""; desc = ""; members.clear()
        }

        for (raw in cfg.lines()) {
            val cleanEnd = raw.trimEnd()
            val t = cleanEnd.trim()
            val indented = cleanEnd.firstOrNull()?.let { it == ' ' || it == '\t' } == true
            if (!indented) {
                if (t.startsWith("ip policy ")) {
                    flush()
                    curName = t.removePrefix("ip policy ").trim()
                } else {
                    flush()
                }
                continue
            }
            if (curName.isBlank()) continue
            when {
                t.startsWith("description ") -> desc = t.removePrefix("description ").trim()
                t.startsWith("permit global ") -> {
                    val rest = t.removePrefix("permit global ").trim().split(Regex("\\s+"))
                    val iface = rest.getOrNull(0).orEmpty()
                    val ordIdx = rest.indexOf("order")
                    val order = if (ordIdx >= 0) rest.getOrNull(ordIdx + 1)?.toIntOrNull() ?: 100 else 100
                    if (iface.isNotBlank()) members.add(iface to order)
                }
            }
        }
        flush()
        return list
    }

    fun parseRoutesFromConfig(cfg: String): List<NdmsRouteInfo> {
        val list = mutableListOf<NdmsRouteInfo>()
        for (raw in cfg.lines()) {
            val t = raw.trim()
            if (t.startsWith("ip route ") && !t.startsWith("ip route default")) {
                val parts = t.removePrefix("ip route ").trim().split(Regex("\\s+"))
                val dest = parts.take(2).joinToString(" ")
                val target = parts.drop(2).joinToString(" ")
                list.add(NdmsRouteInfo(t, dest, target))
            }
        }
        return list
    }

    fun parseDhcpPoolsFromConfig(cfg: String): List<NdmsDhcpPoolInfo> {
        val list = mutableListOf<NdmsDhcpPoolInfo>()
        var name = ""
        var bind = ""
        var start = ""
        var size = 0
        var lease = 0
        val dns = mutableListOf<String>()

        fun flush() {
            if (name.isNotBlank()) {
                list.add(NdmsDhcpPoolInfo(name, bind, start, size, lease, dns.toList()))
            }
            name = ""; bind = ""; start = ""; size = 0; lease = 0; dns.clear()
        }

        for (raw in cfg.lines()) {
            val cleanEnd = raw.trimEnd()
            val t = cleanEnd.trim()
            val indented = cleanEnd.firstOrNull()?.let { it == ' ' || it == '\t' } == true
            if (!indented) {
                if (t.startsWith("ip dhcp pool ")) {
                    flush()
                    name = t.removePrefix("ip dhcp pool ").trim()
                } else {
                    flush()
                }
                continue
            }
            if (name.isBlank()) continue
            when {
                t.startsWith("bind ") -> bind = t.removePrefix("bind ").trim()
                t.startsWith("range ") -> {
                    val p = t.removePrefix("range ").trim().split(Regex("\\s+"))
                    start = p.getOrNull(0).orEmpty()
                    val arg2 = p.getOrNull(1).orEmpty()
                    size = arg2.toIntOrNull() ?: run {
                        fun ipToLong(s: String): Long? {
                            val oct = s.split(".")
                            if (oct.size != 4) return null
                            var res = 0L
                            for (o in oct) {
                                val v = o.toLongOrNull() ?: return null
                                if (v !in 0L..255L) return null
                                res = (res shl 8) or v
                            }
                            return res
                        }
                        val a = ipToLong(start)
                        val b = ipToLong(arg2)
                        if (a != null && b != null && b >= a) (b - a + 1).toInt().coerceAtLeast(1) else 0
                    }
                }
                t.startsWith("lease ") -> lease = t.removePrefix("lease ").trim().toIntOrNull() ?: 0
                t.startsWith("dns-server ") -> {
                    val ip = t.removePrefix("dns-server ").trim()
                    if (ip.isNotBlank()) dns.add(ip)
                }
            }
        }
        flush()
        return list
    }

    fun parseMediaParts(raw: String, optDev: String): List<NdmsMediaPartInfo> {
        val result = mutableListOf<NdmsMediaPartInfo>()
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return result
        runCatching {
            fun addDev(devName: String, dev: JSONObject) {
                fun addPart(key: String, p: JSONObject) {
                    val fs = p.optString("fstype", p.optString("fs", p.optString("filesystem", "")))
                    val rawTotal = p.optLong("total", 0L).let { if (it > 0L) it else p.optLong("size", 0L) }
                    val tot = rawTotal / 1024L / 1024L
                    val free = p.optLong("free", 0L) / 1024L / 1024L
                    val uuid = p.optString("uuid", key)
                    val label = p.optString("label", p.optString("name", key)).ifBlank { devName }
                    val devNode = p.optString("device", p.optString("dev", ""))
                    val isOpt = optDev.isNotBlank() && devNode.isNotBlank() && optDev.endsWith(devNode.substringAfterLast('/'))
                    result.add(NdmsMediaPartInfo(devName, label, uuid, fs, tot, free, isOpt, devNode))
                }
                val partObj = dev.optJSONObject("partition")
                val partArr = dev.optJSONArray("partition")
                when {
                    partObj != null -> partObj.keys().forEach { k ->
                        partObj.optJSONObject(k)?.let { addPart(k, it) }
                    }
                    partArr != null -> for (i in 0 until partArr.length()) {
                        partArr.optJSONObject(i)?.let { addPart(it.optString("uuid", "$devName-$i"), it) }
                    }
                    else -> addPart(dev.optString("uuid", devName), dev)
                }
            }
            val root = JSONObject(trimmed)
            val mArr = root.optJSONArray("media")
            if (mArr != null) {
                for (i in 0 until mArr.length()) {
                    mArr.optJSONObject(i)?.let { addDev(it.optString("name", "MEDIA$i"), it) }
                }
            } else {
                val mObj = root.optJSONObject("media") ?: root
                mObj.keys().forEach { k -> mObj.optJSONObject(k)?.let { addDev(k, it) } }
            }
        }
        return result
    }
}
