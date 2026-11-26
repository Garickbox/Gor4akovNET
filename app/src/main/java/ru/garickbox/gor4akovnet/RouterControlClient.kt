package ru.garickbox.gor4akovnet

import android.content.Context
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class NdmsIfaceInfo(
    val id: String,
    val description: String = "",
    val type: String = "",
    val group: String = "",
    val up: Boolean = false,
    val connected: Boolean = false,
    val ip: String = "",
    val mask: String = "",
    val mtu: Int = 0,
    val isGlobal: Boolean = false,
    val mac: String = ""
)

data class NdmsPolicyInfo(
    val name: String,
    val description: String = "",
    val members: List<Pair<String, Int>> = emptyList()
)

data class NdmsRouteInfo(
    val rawCmd: String,
    val dest: String,
    val target: String
)

data class NdmsDhcpPoolInfo(
    val name: String,
    val bind: String = "",
    val rangeStart: String = "",
    val rangeSize: Int = 0,
    val leaseSec: Int = 0,
    val dnsServers: List<String> = emptyList()
)

data class NdmsMediaPartInfo(
    val mediaName: String,
    val label: String,
    val uuid: String,
    val fsType: String,
    val totalMb: Long,
    val freeMb: Long,
    val isOpt: Boolean,
    val devNode: String
)

data class NdmsControlState(
    val transport: String,
    val model: String,
    val hostname: String,
    val domainName: String,
    val runningConfig: String,
    val interfaces: List<NdmsIfaceInfo>,
    val policies: List<NdmsPolicyInfo>,
    val routes: List<NdmsRouteInfo>,
    val dhcpPools: List<NdmsDhcpPoolInfo>,
    val mediaParts: List<NdmsMediaPartInfo>,
    val blockDevs: List<String>
)

data class ConfigVaultEntry(
    val id: String,
    val timestamp: String,
    val host: String,
    val reason: String,
    val sizeBytes: Long,
    val filePath: String
)

object ConfigVault {
    private const val MAX_ENTRIES = 25

    private fun vaultDir(ctx: Context): File = File(ctx.filesDir, "config_vault").apply { mkdirs() }

    fun listSnapshots(ctx: Context): List<ConfigVaultEntry> {
        val dir = vaultDir(ctx)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { f ->
            runCatching {
                val obj = JSONObject(f.readText(Charsets.UTF_8))
                ConfigVaultEntry(
                    id = obj.optString("id"),
                    timestamp = obj.optString("timestamp"),
                    host = obj.optString("host"),
                    reason = obj.optString("reason"),
                    sizeBytes = obj.optString("config").toByteArray(Charsets.UTF_8).size.toLong(),
                    filePath = f.absolutePath
                )
            }.getOrNull()
        }.sortedByDescending { it.id }
    }

    fun saveSnapshot(ctx: Context, host: String, reason: String, configText: String): ConfigVaultEntry? {
        if (configText.isBlank()) return null
        val dir = vaultDir(ctx)
        val existing = listSnapshots(ctx)
        val latest = existing.firstOrNull()
        if (latest != null) {
            val latestText = readSnapshotText(latest)
            if (latestText == configText) {
                return latest
            }
        }
        val tsId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            "_" + UUID.randomUUID().toString().take(4)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val f = File(dir, "vault_$tsId.json")
        val obj = JSONObject().apply {
            put("id", tsId)
            put("timestamp", stamp)
            put("host", host)
            put("reason", reason)
            put("config", configText)
        }
        f.writeText(obj.toString(), Charsets.UTF_8)
        // Rotate oldest entries beyond MAX_ENTRIES
        val updated = listSnapshots(ctx)
        updated.drop(MAX_ENTRIES).forEach { old ->
            runCatching { File(old.filePath).delete() }
        }
        return ConfigVaultEntry(
            id = tsId,
            timestamp = stamp,
            host = host,
            reason = reason,
            sizeBytes = configText.toByteArray(Charsets.UTF_8).size.toLong(),
            filePath = f.absolutePath
        )
    }

    fun readSnapshotText(entry: ConfigVaultEntry): String = runCatching {
        JSONObject(File(entry.filePath).readText(Charsets.UTF_8)).optString("config", "")
    }.getOrDefault("")

    fun deleteSnapshot(entry: ConfigVaultEntry) {
        runCatching { File(entry.filePath).delete() }
    }
}

object RouterControlClient {
    private fun sq(s: String): String = s.replace("'", "'\"'\"'")

    private fun runSshDirect(prefs: Prefs, cmd: String): String {
        val host = prefs.routerHost.ifBlank { "192.168.1.1" }
        return Gboxcore.runRouterSSH(host, prefs.sshPort, prefs.sshUser, prefs.sshPass, cmd)
    }

    suspend fun loadState(ctx: Context, prefs: Prefs): Result<NdmsControlState> = withContext(Dispatchers.IO) {
        runCatching {
            val host = prefs.routerHost.ifBlank { "192.168.1.1" }
            var transport = "RCI"
            var model = "Keenetic / Netcraze"
            var runCfg = ""
            var ifaceRaw = ""
            var mediaRaw = ""

            val rciOk = runCatching {
                val s = RciSession(host, prefs.rciUser, prefs.rciPass)
                s.ensureAuth()
                runCfg = s.getRunningConfigText()
                ifaceRaw = runCatching { s.request("/rci/show/interface", readTimeoutMs = 20000) }.getOrDefault("")
                mediaRaw = runCatching { s.request("/rci/show/media", readTimeoutMs = 15000) }.getOrDefault("")
                val verJson = runCatching { JSONObject(s.request("/rci/show/version", readTimeoutMs = 10000)) }.getOrNull()
                model = verJson?.optString("model", verJson.optString("device", model)).orEmpty().ifBlank { model }
            }.isSuccess

            if (!rciOk || runCfg.isBlank()) {
                transport = "SSH ndmc"
                val sshCfg = runSshDirect(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c 'show running-config' 2>/dev/null | tr -d '\\r'")
                if (sshCfg.isBlank()) error("Не удалось получить running-config ни по RCI, ни по SSH")
                runCfg = sshCfg
                val verOut = runCatching { runSshDirect(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c 'show version' 2>/dev/null | grep -iE 'model:|device:' | head -n1") }.getOrDefault("")
                if (verOut.contains(":")) model = verOut.substringAfter(":").trim().ifBlank { model }
            }

            // Detect real Linux block devices & /opt mount over SSH (optional enrichment)
            val sshDiskInfo = runCatching {
                runSshDirect(
                    prefs,
                    "opt_dev=$(awk '\$2==\"/opt\"{print \$1; exit}' /proc/mounts 2>/dev/null); " +
                        "devs=$(ls /dev/sd* 2>/dev/null | tr '\\n' ','); " +
                        "echo \"OPT=$opt_dev\"; echo \"DEVS=$devs\""
                )
            }.getOrDefault("")
            val optDev = sshDiskInfo.lines().firstOrNull { it.startsWith("OPT=") }?.removePrefix("OPT=")?.trim().orEmpty()
            val blockDevs = sshDiskInfo.lines().firstOrNull { it.startsWith("DEVS=") }
                ?.removePrefix("DEVS=")?.split(',')
                ?.map { it.trim() }?.filter { it.matches(Regex("""^/dev/sd[a-z][0-9]+$""")) } ?: emptyList()

            val hostname = parseTopValue(runCfg, "system hostname ") ?: "Keenetic"
            val domainName = parseTopValue(runCfg, "ip domain-name ") ?: "local"
            val cfgIfaces = parseInterfacesFromConfig(runCfg)
            val rciIfaces = if (ifaceRaw.isNotBlank()) parseInterfacesFromRci(ifaceRaw) else emptyMap()

            val mergedIfaces = mergeInterfaces(cfgIfaces, rciIfaces)
            val policies = parsePoliciesFromConfig(runCfg)
            val routes = parseRoutesFromConfig(runCfg)
            val pools = parseDhcpPoolsFromConfig(runCfg)
            val media = parseMediaParts(mediaRaw, optDev)

            NdmsControlState(
                transport = transport,
                model = model,
                hostname = hostname,
                domainName = domainName,
                runningConfig = runCfg,
                interfaces = mergedIfaces,
                policies = policies,
                routes = routes,
                dhcpPools = pools,
                mediaParts = media,
                blockDevs = blockDevs
            )
        }
    }

    suspend fun executeNdmsWithVault(
        ctx: Context,
        prefs: Prefs,
        reason: String,
        commands: List<String>,
        saveConfig: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val host = prefs.routerHost.ifBlank { "192.168.1.1" }
            val cleanCmds = commands.map { it.trim() }.filter { it.isNotBlank() }
            if (cleanCmds.isEmpty()) error("Список команд пуст")

            // Auto-snapshot current running-config to Config Vault before mutation
            val preCfg = RciClient.fetchRunningConfig(host, prefs.rciUser, prefs.rciPass).getOrElse {
                runCatching { runSshDirect(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c 'show running-config' 2>/dev/null | tr -d '\\r'") }.getOrDefault("")
            }
            if (preCfg.isNotBlank()) {
                ConfigVault.saveSnapshot(ctx, host, reason, preCfg)
            }

            val finalCmds = if (saveConfig && cleanCmds.none { it.equals("system configuration save", true) }) {
                cleanCmds + "system configuration save"
            } else {
                cleanCmds
            }

            val rciRes = runCatching {
                val s = RciSession(host, prefs.rciUser, prefs.rciPass)
                s.ensureAuth()
                s.parseCliChecked(finalCmds, readTimeoutMs = 45000)
            }

            rciRes.fold(
                onSuccess = { (ok, errs) ->
                    if (errs.isEmpty()) {
                        "OK (RCI): применено $ok команд, снимок сохранён в Config Vault"
                    } else {
                        "Частично (RCI: OK $ok, ошибок ${errs.size}):\n${errs.joinToString("\n")}"
                    }
                },
                onFailure = {
                    // Fallback to SSH ndmc -c
                    val sshScript = finalCmds.joinToString("\n") {
                        "LD_LIBRARY_PATH= /bin/ndmc -c '${sq(it)}' 2>&1"
                    }
                    val out = runSshDirect(prefs, sshScript)
                    "OK (SSH ndmc): ${out.trim().ifBlank { "команды выполнены, снимок в Config Vault" }}"
                }
            )
        }
    }

    suspend fun runNdmsCli(prefs: Prefs, cmdLine: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = cmdLine.trim()
            if (clean.isBlank()) error("Введите команду NDMS")
            val host = prefs.routerHost.ifBlank { "192.168.1.1" }
            val rciTry = runCatching {
                val s = RciSession(host, prefs.rciUser, prefs.rciPass)
                s.ensureAuth()
                formatParseOutput(s.parseCli(listOf(clean), readTimeoutMs = 30000))
            }
            rciTry.getOrElse {
                runSshDirect(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c '${sq(clean)}' 2>&1 | tr -d '\\r'")
            }
        }
    }

    suspend fun takeManualVaultSnapshot(ctx: Context, prefs: Prefs, label: String): Result<ConfigVaultEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val host = prefs.routerHost.ifBlank { "192.168.1.1" }
            val cfg = RciClient.fetchRunningConfig(host, prefs.rciUser, prefs.rciPass).getOrElse {
                runSshDirect(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c 'show running-config' 2>/dev/null | tr -d '\\r'")
            }
            if (cfg.isBlank()) error("Не удалось прочитать running-config")
            ConfigVault.saveSnapshot(ctx, host, label.ifBlank { "Ручной снимок" }, cfg)
                ?: error("Ошибка записи снимка")
        }
    }

    suspend fun restoreVaultSnapshot(ctx: Context, prefs: Prefs, entry: ConfigVaultEntry): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val host = prefs.routerHost.ifBlank { "192.168.1.1" }
            val cfgText = ConfigVault.readSnapshotText(entry)
            if (cfgText.isBlank()) error("Снимок пуст")
            // Save current config before rollback so rollback itself is reversible
            val curCfg = RciClient.fetchRunningConfig(host, prefs.rciUser, prefs.rciPass).getOrNull()
            if (!curCfg.isNullOrBlank()) {
                ConfigVault.saveSnapshot(ctx, host, "Перед откатом к ${entry.timestamp}", curCfg)
            }
            val rciRes = RciClient.restoreRunningConfig(host, prefs.rciUser, prefs.rciPass, cfgText)
            rciRes.fold(
                onSuccess = { (ok, fail) ->
                    "Откат Config Vault (RCI): применено $ok строк, пропущено/ошибок $fail"
                },
                onFailure = {
                    val flat = flattenConfigLinesForSsh(cfgText)
                    if (flat.isEmpty()) error("Не удалось разобрать команды снимка")
                    flat.chunked(25).forEach { chunk ->
                        val sc = chunk.joinToString("\n") { "LD_LIBRARY_PATH= /bin/ndmc -c '${sq(it)}' >/dev/null 2>&1 || true" }
                        runSshDirect(prefs, sc)
                    }
                    runSshDirect(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save' >/dev/null 2>&1 || true")
                    "Откат Config Vault (SSH ndmc): применено ${flat.size} команд"
                }
            )
        }
    }

    private fun flattenConfigLinesForSsh(cfgText: String): List<String> {
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
            val indented = cleanEnd.firstOrNull()?.let { it == ' ' || it == '	' } == true
            val fullCmd = if (indented && block.isNotBlank()) "$block $trimmed" else trimmed
            if (!indented) block = trimmed
            if (fullCmd.startsWith("user ") || fullCmd.startsWith("no user ") || fullCmd.startsWith("system mode")) continue
            out.add(fullCmd)
        }
        return out
    }

    private fun formatParseOutput(raw: String): String {
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

    private fun parseTopValue(cfg: String, prefix: String): String? {
        for (line in cfg.lines()) {
            val t = line.trim()
            if (t.startsWith(prefix)) {
                return t.removePrefix(prefix).trim().trim('"')
            }
        }
        return null
    }

    private fun parseInterfacesFromConfig(cfg: String): Map<String, NdmsIfaceInfo> {
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
            val t = classifyIfaceType(curId)
            map[curId] = NdmsIfaceInfo(
                id = curId,
                description = desc,
                type = t,
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
                } else if (t == "!") {
                    flush()
                } else if (curId.isNotBlank()) {
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

    private fun parseInterfacesFromRci(raw: String): Map<String, NdmsIfaceInfo> {
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
                val desc = o.optString("description", "")
                val ip = o.optString("address", "")
                val mask = o.optString("mask", "")
                val mtu = o.optInt("mtu", 0)
                val mac = o.optString("mac", "")
                val grp = o.optString("group", o.optString("security-level", ""))
                val glob = o.optBoolean("global", false)
                map[id] = NdmsIfaceInfo(
                    id = id,
                    description = desc,
                    type = type,
                    group = grp,
                    up = up,
                    connected = conn,
                    ip = ip,
                    mask = mask,
                    mtu = mtu,
                    isGlobal = glob,
                    mac = mac
                )
            }
        }
        return map
    }

    private fun mergeInterfaces(
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

    private fun parsePoliciesFromConfig(cfg: String): List<NdmsPolicyInfo> {
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

    private fun parseRoutesFromConfig(cfg: String): List<NdmsRouteInfo> {
        val list = mutableListOf<NdmsRouteInfo>()
        for (raw in cfg.lines()) {
            val t = raw.trim()
            if (t.startsWith("ip route ") && !t.startsWith("ip route default")) {
                val rest = t.removePrefix("ip route ").trim()
                val parts = rest.split(Regex("\\s+"))
                val dest = parts.take(2).joinToString(" ")
                val target = parts.drop(2).joinToString(" ")
                list.add(NdmsRouteInfo(t, dest, target))
            }
        }
        return list
    }

    private fun parseDhcpPoolsFromConfig(cfg: String): List<NdmsDhcpPoolInfo> {
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
                    size = p.getOrNull(1)?.toIntOrNull() ?: 0
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

    private fun parseMediaParts(raw: String, optDev: String): List<NdmsMediaPartInfo> {
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
