package ru.garickbox.gor4akovnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class RciException(message: String) : Exception(message)

class RciSession(
    val host: String,
    private val user: String,
    private val pass: String,
    private var cookieHeader: String = ""
) {
    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5")
            .digest(s.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun ensureAuth() {
        val url = URL("http://$host/auth")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 7000
            if (cookieHeader.isNotBlank()) setRequestProperty("Cookie", cookieHeader)
        }
        val code = conn.responseCode
        val setCookies = conn.headerFields["Set-Cookie"] ?: conn.headerFields["set-cookie"]
        if (!setCookies.isNullOrEmpty()) {
            cookieHeader = setCookies.joinToString("; ") { it.substringBefore(";") }
        }
        if (code == 200) {
            conn.disconnect()
            return
        }
        val realm = conn.getHeaderField("X-NDM-Realm") ?: "Keenetic"
        val challenge = conn.getHeaderField("X-NDM-Challenge") ?: ""
        conn.disconnect()

        if (challenge.isBlank()) {
            throw RciException("Роутер вернул HTTP $code без X-NDM-Challenge")
        }

        val md5 = md5Hex("$user:$realm:$pass")
        val sha = sha256Hex("$challenge$md5")
        val body = JSONObject().apply {
            put("login", user)
            put("password", sha)
        }.toString()

        val post = (URL("http://$host/auth").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 7000
            setRequestProperty("Content-Type", "application/json")
            if (cookieHeader.isNotBlank()) setRequestProperty("Cookie", cookieHeader)
            outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val postCode = post.responseCode
        val postCookies = post.headerFields["Set-Cookie"] ?: post.headerFields["set-cookie"]
        if (!postCookies.isNullOrEmpty()) {
            cookieHeader = postCookies.joinToString("; ") { it.substringBefore(";") }
        }
        post.disconnect()
        if (postCode !in 200..299) {
            throw RciException("Неверный логин или пароль роутера (HTTP $postCode)")
        }
    }

    fun request(
        path: String,
        method: String = "GET",
        jsonBody: String? = null,
        retryAuth: Boolean = true,
        readTimeoutMs: Int = 45000
    ): String {
        val conn = (URL("http://$host$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6000
            readTimeout = readTimeoutMs
            if (cookieHeader.isNotBlank()) setRequestProperty("Cookie", cookieHeader)
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(jsonBody.toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        val code = conn.responseCode
        if (code == 401 && retryAuth) {
            conn.disconnect()
            ensureAuth()
            return request(path, method, jsonBody, retryAuth = false, readTimeoutMs = readTimeoutMs)
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.let { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            throw RciException("RCI $path -> HTTP $code: ${text.take(200)}")
        }
        return text
    }

    fun getRunningConfigText(): String {
        val raw = request("/rci/show/running-config", readTimeoutMs = 60000).trim()
        if (raw.isBlank() || raw == "{}" || raw == "[]") {
            throw RciException("Роутер вернул пустой running-config")
        }
        val obj = runCatching { JSONObject(raw) }.getOrNull()
        if (obj != null) {
            val arr = obj.optJSONArray("message")
                ?: throw RciException("running-config не содержит секцию message")
            val text = buildString {
                for (i in 0 until arr.length()) {
                    append(arr.optString(i)).append('\n')
                }
            }
            if (text.isBlank()) throw RciException("running-config пуст")
            return text
        }
        if (raw.lines().none { it.trim().startsWith("interface ") || it.trim().startsWith("system ") || it.trim().startsWith("ip ") }) {
            throw RciException("Некорректный формат running-config: ${raw.take(80)}")
        }
        return raw
    }

    fun parseCli(commands: List<String>, readTimeoutMs: Int = 45000): String {
        val payload = JSONArray()
        for (cmd in commands) {
            payload.put(JSONObject().put("parse", cmd))
        }
        return request("/rci/parse", method = "POST", jsonBody = payload.toString(), readTimeoutMs = readTimeoutMs)
    }

    private fun countParseErrors(raw: String, fallbackCount: Int): Int {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return fallbackCount
        fun hasItemError(item: JSONObject?): Boolean {
            val stArr = item?.optJSONObject("parse")?.optJSONArray("status")
                ?: item?.optJSONArray("status")
                ?: return false
            for (i in 0 until stArr.length()) {
                val st = stArr.optJSONObject(i)?.optString("status", "") ?: ""
                if (st.equals("error", ignoreCase = true)) return true
            }
            return false
        }
        return runCatching {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    var errs = 0
                    for (i in 0 until arr.length()) {
                        if (hasItemError(arr.optJSONObject(i))) errs++
                    }
                    errs
                }
                trimmed.startsWith("{") -> if (hasItemError(JSONObject(trimmed))) 1 else 0
                else -> 0
            }
        }.getOrDefault(0)
    }

    fun parseCliChecked(commands: List<String>, readTimeoutMs: Int = 45000): Pair<Int, List<String>> {
        if (commands.isEmpty()) return 0 to emptyList()
        val raw = parseCli(commands, readTimeoutMs = readTimeoutMs).trim()
        if (raw.isBlank()) return commands.size to listOf("Пустой ответ RCI (таймаут или сессия сброшена)")
        val errors = mutableListOf<String>()
        fun inspectItem(idx: Int, item: JSONObject?) {
            val stArr = item?.optJSONObject("parse")?.optJSONArray("status")
                ?: item?.optJSONArray("status")
                ?: return
            for (j in 0 until stArr.length()) {
                val stObj = stArr.optJSONObject(j) ?: continue
                val st = stObj.optString("status", "")
                if (st.equals("error", ignoreCase = true)) {
                    val msg = stObj.optString("message", stObj.optString("code", "Ошибка команды"))
                    val cmd = commands.getOrNull(idx) ?: "#$idx"
                    errors.add("$cmd -> $msg")
                }
            }
        }
        runCatching {
            when {
                raw.startsWith("[") -> {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) inspectItem(i, arr.optJSONObject(i))
                }
                raw.startsWith("{") -> inspectItem(0, JSONObject(raw))
            }
        }
        val ok = (commands.size - errors.size).coerceAtLeast(0)
        return ok to errors
    }

    private fun flattenNdmsConfigLines(cfgText: String, skipDangerous: Boolean = true): List<String> {
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
            if (!indented) {
                block = trimmed
            }
            if (skipDangerous && (
                    fullCmd.startsWith("user ") ||
                        fullCmd.startsWith("no user ") ||
                        fullCmd.startsWith("system mode")
                    )
            ) {
                continue
            }
            // Do not emit bare container line if child commands will carry it; emit leaf/top commands directly
            if (indented || (
                    trimmed != "system" &&
                        trimmed != "cifs" &&
                        trimmed != "dns-proxy" &&
                        trimmed != "ntce" &&
                        trimmed != "ip http webdav" &&
                        !trimmed.startsWith("interface ") &&
                        !trimmed.startsWith("ip dhcp pool ") &&
                        !trimmed.startsWith("ip policy ") &&
                        !trimmed.startsWith("mws wlan ") &&
                        !trimmed.startsWith("schedule ") &&
                        !trimmed.startsWith("ipv6 subnet ")
                    )
            ) {
                out.add(fullCmd)
            } else {
                // Also ensure container object exists before subcommands
                out.add(trimmed)
            }
        }
        return out
    }

    fun restoreRunningConfigChunked(cfgText: String, skipDangerous: Boolean = true): Pair<Int, Int> {
        val lines = flattenNdmsConfigLines(cfgText, skipDangerous)
        var applied = 0
        var failed = 0
        lines.chunked(30).forEach { chunk ->
            val res = runCatching { parseCli(chunk, readTimeoutMs = 60000) }
            if (res.isSuccess) {
                val errs = countParseErrors(res.getOrDefault(""), chunk.size)
                val ok = (chunk.size - errs).coerceAtLeast(0)
                applied += ok
                failed += errs
            } else {
                failed += chunk.size
            }
        }
        runCatching { parseCli(listOf("system configuration save"), readTimeoutMs = 30000) }
        return applied to failed
    }

    fun mkcolWebDav(port: Int, remoteDir: String) {
        runCatching {
            val cleanDir = if (remoteDir.startsWith("/")) remoteDir else "/$remoteDir"
            val conn = (URL("http://$host:$port$cleanDir").openConnection() as HttpURLConnection).apply {
                requestMethod = "MKCOL"
                connectTimeout = 5000
                readTimeout = 5000
                val basic = Base64.getEncoder().encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8))
                setRequestProperty("Authorization", "Basic $basic")
            }
            conn.responseCode
            conn.disconnect()
        }
    }

    fun putWebDavFile(port: Int, remotePath: String, bytes: ByteArray, readTimeoutMs: Int = 60000): Result<String> = runCatching {
        val cleanPath = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        val url = URL("http://$host:$port$cleanPath")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 8000
            readTimeout = readTimeoutMs.coerceIn(8000, 120000)
            setFixedLengthStreamingMode(bytes.size)
            val basic = Base64.getEncoder().encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8))
            setRequestProperty("Authorization", "Basic $basic")
            setRequestProperty("Content-Type", "application/octet-stream")
            outputStream.use { it.write(bytes) }
        }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299 && code != 201 && code != 204) {
            error("WebDAV PUT $cleanPath HTTP $code")
        }
        "HTTP $code (${bytes.size} B)"
    }
}

object RciClient {
    fun detectDefaultGateway(context: Context): String {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return@runCatching "192.168.1.1"
            val caps = cm.getNetworkCapabilities(net)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val lp = cm.getLinkProperties(net)
                val gw = lp?.routes
                    ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                    ?.gateway?.hostAddress
                if (!gw.isNullOrBlank()) return@runCatching gw
            }
            "192.168.1.1"
        }.getOrDefault("192.168.1.1")
    }

    suspend fun probePassport(host: String, user: String, pass: String): Result<RouterPassport> = withContext(Dispatchers.IO) {
        runCatching {
            val session = RciSession(host, user, pass)
            session.ensureAuth()

            val verJson = JSONObject(session.request("/rci/show/version"))
            val sysJson = runCatching { JSONObject(session.request("/rci/show/system")) }.getOrNull()
            val compJson = runCatching { JSONObject(session.request("/rci/show/components")) }.getOrNull()
            val mediaRes = runCatching { session.request("/rci/show/media") }
            val mediaRaw = mediaRes.getOrDefault("{}")
            val runCfg = runCatching { session.getRunningConfigText() }.getOrDefault("")

            val model = verJson.optString("model", verJson.optString("device", "Keenetic / Netcraze"))
            val hwId = verJson.optString("hw_id", "")
            val archRaw = verJson.optString("arch", "")
            val ndmObj = verJson.optJSONObject("ndm")
            val ndmTitle = ndmObj?.optString("title", "") ?: verJson.optString("title", "")

            val verMatch = listOf(
                verJson.optString("release", ""),
                verJson.optString("title", ""),
                ndmTitle,
                ndmObj?.optString("exact", "") ?: ""
            ).asSequence().mapNotNull { Regex("""(\d+)\.(\d+)""").find(it) }.firstOrNull()
            val ndmsMajor = verMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val ndmsMinor = verMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0

            val (entwareArch, entwareInstaller) = CompatibilityMatrix.resolveArch(hwId, model, archRaw)

            val ramBytes = sysJson?.optLong("memtotal", 0L) ?: 0L
            val ramMb = if (ramBytes > 1_000_000L) (ramBytes / 1024L / 1024L).toInt() else (ramBytes / 1024L).toInt()
            val cpuCount = sysJson?.optInt("cpucount", 0) ?: 0
            val uptimeSec = sysJson?.optLong("uptime", 0L) ?: 0L

            val comps = mutableSetOf<String>()
            val compMap = compJson?.optJSONObject("component") ?: compJson
            compMap?.keys()?.forEach { key ->
                val item = compMap.optJSONObject(key) ?: return@forEach
                // С п. 3.10.7: поле "installed:" — это СТРОКА ВЕРСИИ
                // (например "2.13.B.1.0-1"), а не boolean. optBoolean("installed", true)
                // ошибочно возвращал true и для отсутствующего поля, и для непустой строки,
                // из-за чего на роутере после factory reset "OPKG" показывался установленным,
                // хотя /opt пуст. Разбираем строку буквально: пусто/нет = не установлен.
                val installedVer = item.optString("installed", "").trim()
                if (installedVer.isNotBlank()) comps.add(key.lowercase())
            }
            fun addCompTokens(raw: String?) {
                raw?.split(',', ' ', ';', '\n')?.forEach { tok ->
                    val t = tok.trim().lowercase()
                    if (t.isNotBlank()) comps.add(t)
                }
            }
            addCompTokens(verJson.optString("components", ""))
            addCompTokens(ndmObj?.optString("components", ""))
            addCompTokens(verJson.optJSONObject("ndw")?.optString("components", ""))
            listOf(verJson.optJSONArray("components"), ndmObj?.optJSONArray("components"), verJson.optJSONObject("ndw")?.optJSONArray("components")).forEach { arr ->
                if (arr != null) {
                    for (i in 0 until arr.length()) addCompTokens(arr.optString(i, ""))
                }
            }

            val mediaList = parseMedia(mediaRaw)
            val hasUsb = mediaList.any { it.isUsb }
            val hasInternalOpkg = mediaList.any { it.isInternalStorage && (it.freeMb >= 32 || it.totalMb >= 64) }

            val opkgDiskLine = runCfg.lines().firstOrNull { it.trim().startsWith("opkg disk ") }?.trim()?.removePrefix("opkg disk ") ?: ""
            val opkgInitrc = runCfg.lines().firstOrNull { it.trim().startsWith("opkg initrc ") }?.trim()?.removePrefix("opkg initrc ") ?: ""
            val ndms5WebdavMatch = Regex("""(?ms)^ip http webdav\b[^!]*?^[ \t]+port (\d+)""").find(runCfg)
            val webdavPortLine = runCfg.lines().firstOrNull { it.trim().startsWith("webdav port ") }?.trim()
            val webdavEnabled5 = Regex("""(?ms)^ip http webdav\b[^!]*?^[ \t]+enable\b""").containsMatchIn(runCfg)
            val webdavPort = ndms5WebdavMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: webdavPortLine?.substringAfter("webdav port ")?.toIntOrNull()
                ?: if (webdavEnabled5) 80 else if (runCfg.contains("webdav enable")) 8080 else 0

            if (webdavEnabled5 || ndms5WebdavMatch != null || runCfg.contains("ip http webdav") || runCfg.contains("webdav enable")) {
                comps.add("webdav")
            }
            if (opkgDiskLine.isNotBlank() || opkgInitrc.isNotBlank()) {
                comps.add("opkg")
            }

            val ssh222 = Gboxcore.probeTCP(host, 222, 1200)
            // Справочник NDMS 5.1, п. 3.10.7: "/rci/components/list" возвращает весь каталог песочницы,
            // включая НЕустановленные компоненты. contains("opkg") как признак установки даёт ложное
            // "OPKG установлен" после factory reset и ломает сценарий восстановления. Реальность модуля
            // OPKG видна из running-config (opkg disk / opkg initrc, пп. 3.143/3.145) и по SSH :222.
            if (ssh222) {
                if (comps.none { it.contains("opkg") }) comps.add("opkg")
                if (comps.none { it.contains("netfilter") }) comps.add("opkg-kmod-netfilter")
                if (comps.none { it == "ssh" }) comps.add("ssh")
            }

            val (compatLevel, compatReason) = CompatibilityMatrix.evaluate(
                major = ndmsMajor,
                minor = ndmsMinor,
                hasUsb = hasUsb,
                hasInternalOpkg = hasInternalOpkg,
                mediaProbed = mediaRes.isSuccess,
                sshAlreadyUp = ssh222
            )

            val ssh22 = Gboxcore.probeTCP(host, 22, 1000)
            val agh3000 = Gboxcore.probeTCP(host, 3000, 700) || Gboxcore.probeTCP(host, 3080, 700)

            val wanOnline = runCatching {
                val pingOut = session.parseCli(listOf("tools ping 1.1.1.1 count 1"))
                pingOut.contains("received", ignoreCase = true) && !pingOut.contains("0 received")
            }.getOrDefault(false)

            val polExists = runCfg.contains("ip policy Gor4akovNET")
            val brMatch = Regex("""(?ms)^interface (Bridge\d+)\s*[\r\n]+[^!]*?description Gor4akovNET""").find(runCfg)
            val ssidMatch = Regex("""(?ms)^interface WifiMaster\d+/\S+[\r\n]+[^!]*?description Gor4akovNET[^!]*?^[ \t]+ssid (\S+)""").find(runCfg)

            RouterPassport(
                host = host,
                model = model,
                hwId = hwId,
                ndmsTitle = ndmTitle.ifBlank { "NDMS" },
                ndmsMajor = ndmsMajor,
                ndmsMinor = ndmsMinor,
                cpuArch = archRaw.ifBlank { entwareArch },
                entwareArch = entwareArch,
                entwareInstaller = entwareInstaller,
                compatLevel = compatLevel,
                compatReason = compatReason,
                ramTotalMb = ramMb,
                cpuCount = cpuCount,
                uptimeSec = uptimeSec,
                mediaList = mediaList,
                components = comps,
                hasOpkgComp = comps.any { it == "opkg" || it.contains("opkg") },
                hasNetfilterComp = comps.any { it.contains("netfilter") || it.contains("xtables") },
                hasWebdavComp = comps.any { it.contains("webdav") },
                hasSshComp = comps.any { it == "ssh" },
                webdavPort = webdavPort,
                opkgDisk = opkgDiskLine,
                opkgInitrc = opkgInitrc,
                ssh222Open = ssh222,
                ssh22Open = ssh22,
                agh3000Open = agh3000,
                wanOnline = wanOnline,
                gor4akovPolicyExists = polExists,
                gor4akovBridgeExists = brMatch != null,
                gor4akovSsid = ssidMatch?.groupValues?.getOrNull(1) ?: ""
            )
        }
    }

    private fun parseMedia(raw: String): List<StorageMedia> {
        val result = mutableListOf<StorageMedia>()
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return result

        // Разбор поля "supported" — может быть JSON-массив или строка через запятую
        fun parseListField(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key)
            if (arr != null) {
                val out = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "").trim()
                    if (s.isNotBlank()) out.add(s.lowercase())
                }
                return out
            }
            val s = obj.optString(key, "").trim()
            if (s.isBlank()) return emptyList()
            return s.split(',', ' ', ';').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        }

        fun addDevice(devName: String, dev: JSONObject) {
            val isStorage = devName.equals("STORAGE", ignoreCase = true) ||
                devName.equals("INTERNAL", ignoreCase = true) ||
                devName.contains("STORAGE", ignoreCase = true)

            // Справочник NDMS 5.1, п. 3.105: initialize-supported живёт на уровне media
            // (а не partition). Для Asolid USB_0114 часто пусто — тогда initialize не работает.
            val initSupportedMedia = parseListField(dev, "initialize-supported")
                .ifEmpty { parseListField(dev, "initialize_supported") }

            fun addPart(uuidKey: String, p: JSONObject) {
                val fs = p.optString("fstype", p.optString("fs", p.optString("filesystem", "")))
                val rawTotal = p.optLong("total", 0L).let { if (it > 0L) it else p.optLong("size", 0L) }
                val total = rawTotal / 1024L / 1024L
                val free = p.optLong("free", 0L) / 1024L / 1024L
                val label = p.optString("label", p.optString("name", uuidKey)).ifBlank { devName }
                // П. 3.107: format-supported — на уровне partition (не media).
                val fmtSupported = parseListField(p, "format-supported")
                    .ifEmpty { parseListField(p, "format_supported") }
                // used-by: cifs/opkg/dlna — на уровне partition. Если содержит "opkg",
                // этот раздел держит Entware (/opt), форматировать НЕЛЬЗЯ.
                val usedByList = parseListField(p, "used-by")
                    .ifEmpty { parseListField(p, "used_by") }
                result.add(
                    StorageMedia(
                        name = devName,
                        label = label,
                        uuid = p.optString("uuid", uuidKey),
                        fsType = fs,
                        totalMb = total,
                        freeMb = free,
                        isUsb = !isStorage,
                        isExt4 = fs.equals("ext4", ignoreCase = true) || (isStorage && fs.isBlank()),
                        isInternalStorage = isStorage,
                        initializeSupported = initSupportedMedia,
                        formatSupported = fmtSupported,
                        usedBy = usedByList
                    )
                )
            }

            val partObj = dev.optJSONObject("partition")
            val partArr = dev.optJSONArray("partition")
            when {
                partObj != null -> partObj.keys().forEach { uuidKey ->
                    val p = partObj.optJSONObject(uuidKey) ?: return@forEach
                    addPart(uuidKey, p)
                }
                partArr != null -> for (i in 0 until partArr.length()) {
                    val p = partArr.optJSONObject(i) ?: continue
                    addPart(p.optString("uuid", "$devName-$i"), p)
                }
                else -> addPart(dev.optString("uuid", devName), dev)
            }
        }

        if (trimmed.startsWith("[")) {
            val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return result
            for (i in 0 until arr.length()) {
                val dev = arr.optJSONObject(i) ?: continue
                val devName = dev.optString("name", dev.optString("label", "MEDIA$i"))
                addDevice(devName, dev)
            }
            return result
        }

        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return result
        val mediaArr = root.optJSONArray("media")
        if (mediaArr != null) {
            for (i in 0 until mediaArr.length()) {
                val dev = mediaArr.optJSONObject(i) ?: continue
                val devName = dev.optString("name", dev.optString("label", "MEDIA$i"))
                addDevice(devName, dev)
            }
            return result
        }
        val mediaObj = root.optJSONObject("media") ?: root
        mediaObj.keys().forEach { devName ->
            val dev = mediaObj.optJSONObject(devName) ?: return@forEach
            addDevice(devName, dev)
        }
        return result
    }

    suspend fun runCli(host: String, user: String, pass: String, cmds: List<String>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val s = RciSession(host, user, pass)
            s.ensureAuth()
            s.parseCli(cmds)
        }
    }

    suspend fun fetchRunningConfig(host: String, user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val s = RciSession(host, user, pass)
            s.ensureAuth()
            s.getRunningConfigText()
        }
    }

    suspend fun restoreRunningConfig(host: String, user: String, pass: String, cfgText: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val s = RciSession(host, user, pass)
            s.ensureAuth()
            s.restoreRunningConfigChunked(cfgText)
        }
    }

    // Справочник NDMS 5.1, пп. 3.10.5 (components commit) и 3.10.6 (components install):
    // "components install X" только ОТМЕЧАЕТ компонент как queued — окончательная установка
    // выполняется отдельной командой "components commit". Без commit компонент не ставится.
    // После успешного commit NDMS обычно перезагружается — вызывающий код должен это учесть.
    suspend fun installComponents(
        host: String,
        user: String,
        pass: String,
        components: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = components.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (clean.isEmpty()) error("Список компонентов пуст")
            val s = RciSession(host, user, pass)
            s.ensureAuth()
            val cmds = clean.map { "components install $it" } + listOf("components commit")
            val (ok, errs) = s.parseCliChecked(cmds, readTimeoutMs = 60000)
            if (errs.isNotEmpty()) {
                error("components install/commit: " + errs.joinToString("; "))
            }
            if (ok == 0) {
                error("Роутер отклонил components install/commit (нет подключения к серверу обновлений или компонент недоступен в песочнице)")
            }
            "Компоненты (${clean.joinToString(", ")}) отмечены и зафиксированы (components commit). Роутер может перезагрузиться — подождите 60-90 сек."
        }
    }

    // Справочник NDMS 5.1, п. 3.105: "media <name> initialize <preset>" УДАЛЯЕТ старую таблицу
    // разделов и создаёт заново swap+ext4/ntfs. Это деструктивная операция — вызывающий код
    // обязан предупредить пользователя. Возвращает сразу, т.к. NDMS выполняет разметку в фоне;
    // прогресс виден через "show media" (п. 3.104).
    suspend fun initializeMediaExt4(
        host: String,
        user: String,
        pass: String,
        mediaName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = mediaName.trim()
            if (clean.isBlank()) error("Не указано имя накопителя (например Media0)")
            val s = RciSession(host, user, pass)
            s.ensureAuth()

            // --- ЗАЩИТА 1: читаем show media и проверяем раздел ---
            // Справочник NDMS 5.1, п. 3.105: initialize УДАЛЯЕТ старую таблицу разделов,
            // создаёт заново swap+ext4/ntfs. Если раздел держит Entware (used-by: opkg)
            // или CIFS (used-by: cifs) — initialize снесёт их начисто.
            val mediaRaw = runCatching { s.request("/rci/show/media") }.getOrDefault("")
            val parts = parseMedia(mediaRaw).filter { it.name == clean }
            if (parts.isEmpty()) error("Накопитель $clean не найден в show media")
            val usedByList = parts.flatMap { it.usedBy }.distinct()
            if (usedByList.contains("opkg")) {
                error(
                    "Раздел $clean используется для Entware (/opt). " +
                        "Форматирование УДАЛИТ ВСЕ пакеты (/opt/*). Отказ. " +
                        "Сначала отвяжите opkg: no opkg disk."
                )
            }
            if (usedByList.contains("cifs")) {
                error("Раздел $clean используется для CIFS (общая папка). Отвяжите CIFS перед форматированием.")
            }

            // --- ЗАЩИТА 2: проверяем, что media поддерживает initialize ---
            // П. 3.105 (стр. 475): "Поддерживаемые файловые системы можно проверить
            // в строке initialize-supported". Пусто = initialize НЕ работает на этой флешке
            // (например Asolid USB_0114 — только NTFS, без ext4).
            val initSupported = parts.flatMap { it.initializeSupported }.distinct()
            if (initSupported.isEmpty()) {
                error(
                    "Накопитель $clean не поддерживает initialize (initialize-supported пусто). " +
                        "Это флешка или контроллер, где NDMS не умеет пересобирать разделы. " +
                        "Варианты: другая флешка ИЛИ форматирование в ext4 вручную через SSH+mkfs.ext4."
                )
            }
            val preset = if (initSupported.contains("ext4")) "ext4"
                else if (initSupported.contains("ntfs")) "ntfs"
                else initSupported.first()
            if (preset != "ext4") {
                error(
                    "Накопитель $clean не поддерживает ext4 (доступно только: ${initSupported.joinToString(", ")}). " +
                        "Entware требует ext4 для /opt. Используйте другую флешку."
                )
            }

            // --- Собственно initialize ---
            val (ok, errs) = s.parseCliChecked(
                listOf("media $clean initialize $preset", "system configuration save"),
                readTimeoutMs = 120000
            )
            if (errs.isNotEmpty() || ok == 0) {
                error(errs.firstOrNull() ?: "Роутер отклонил media $clean initialize $preset")
            }
            "media $clean инициализирована в $preset (swap+ext4). NDMS размечает в фоне — show media покажет прогресс через 10-30 сек."
        }
    }

    // ЭТАП 2 установки (Справочник NDMS 5.1):
    //  - п. 3.171 (стр. 582): service ssh — тумблер SSH-сервера :22
    //  - п. 3.71/3.71.1 (стр. 413): ip http webdav enable — тумблер WebDAV на :80/install
    //  - п. 3.71.3 (стр. 414): ip http webdav security-level private
    //  - п. 3.159 (стр. 576): service ftp — тумблер FTP :21 (опционально)
    //  - п. 3.188.10 (стр. 785): system configuration save
    // Всё через RCI :80 — БЕЗ :22 и :222. Именно эти же команды шлёт веб-UI при клике тумблера.
    suspend fun enableServicesViaRci(
        host: String,
        user: String,
        pass: String,
        enableSsh: Boolean = true,
        enableWebdav: Boolean = true,
        enableFtp: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val s = RciSession(host, user, pass)
            s.ensureAuth()
            val cmds = mutableListOf<String>()
            if (enableSsh) cmds.add("service ssh")
            if (enableWebdav) {
                cmds.add("ip http webdav")
                cmds.add("ip http webdav enable")
                cmds.add("ip http webdav security-level private")
            }
            if (enableFtp) {
                cmds.add("service ftp")
                cmds.add("ip ftp security-level private")
            }
            cmds.add("system configuration save")
            val (ok, errs) = s.parseCliChecked(cmds, readTimeoutMs = 30000)
            if (errs.isNotEmpty()) error("Сервисы: " + errs.joinToString("; "))
            if (ok == 0) error("Роутер отклонил включение сервисов")
            buildString {
                append("Включено через RCI: ")
                val on = mutableListOf<String>()
                if (enableSsh) on.add("SSH :22")
                if (enableWebdav) on.add("WebDAV :80/install")
                if (enableFtp) on.add("FTP :21")
                append(on.joinToString(", "))
                append(". Конфигурация сохранена (system configuration save).")
            }
        }
    }

    // Универсальный toggle любой службы NDMS (Справочник 3.15x-3.17x):
    // ssh/ftp/cifs/dlna/torrent/upnp/telnet/afp/snmp/mdns/igmp-proxy/oc-server/vpn-server.
    suspend fun toggleService(host: String, user: String, pass: String, svc: String, on: Boolean): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val s = RciSession(host, user, pass); s.ensureAuth()
            val cmd = if (on) "service $svc" else "no service $svc"
            val (ok, errs) = s.parseCliChecked(listOf(cmd, "system configuration save"), 20000)
            if (errs.isNotEmpty()) error("$svc: ${errs.joinToString("; ")}")
            if (ok == 0) error("Роутер отклонил: $cmd")
            "$cmd → OK"
        }
    }

    // ЭТАП 4 установки (Справочник NDMS 5.1, п. 3.143, стр. 554):
    //   opkg disk <disk> [<url>]
    // При наличии <url> NDMS САМ скачивает installer по URL, распаковывает в /opt,
    // монтирует раздел и запускает initrc — БЕЗ WebDAV, БЕЗ SSH, БЕЗ :222 на этом шаге.
    // П. 3.145 (стр. 555): opkg initrc /opt/etc/init.d/rc.unslung
    // П. 3.188.10 (стр. 785): system configuration save
    // ВАЖНО: не включаем здесь "opkg dns-override" — это про AdGuard (п. 3.144), не про Entware.
    //        Включение dns-override при живом AdGuard на :3053 сломает DNS.
    suspend fun installEntwareViaUrl(
        host: String,
        user: String,
        pass: String,
        diskRef: String,
        installerUrls: List<String> = listOf(
            "http://85.192.60.175/entware/aarch64-k3.10/installer/aarch64-installer.tar.gz",
            "https://bin.entware.net/aarch64-k3.10/installer/aarch64-installer.tar.gz"
        )
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val disk = diskRef.trim()
            if (disk.isBlank()) error("Не указан diskRef (UUID:/ или LABEL:/)")
            if (installerUrls.isEmpty()) error("Список URL installer пуст")
            val s = RciSession(host, user, pass)
            s.ensureAuth()

            // --- ЗАЩИТА ОТ ПЕРЕУСТАНОВКИ ---
            // Если opkg disk уже настроен И :222 живой — Entware уже работает.
            // Переустановка снесла бы пакеты (opkg update + install заново = 20-60 мин).
            // Если :222 мёртв, но opkg disk есть — только перезапускаем initrc (быстро).
            val preCfg = runCatching { s.getRunningConfigText() }.getOrDefault("")
            val existingDisk = preCfg.lines().firstOrNull { it.trim().startsWith("opkg disk ") }?.trim() ?: ""
            val ssh222Pre = Gboxcore.probeTCP(host, 222, 2500)
            if (existingDisk.isNotBlank() && ssh222Pre) {
                return@runCatching "Entware уже установлен и работает ($existingDisk, SSH :222 открыт). Переустановка не нужна."
            }
            if (existingDisk.isNotBlank() && !ssh222Pre) {
                s.parseCliChecked(
                    listOf("opkg initrc /opt/etc/init.d/rc.unslung", "system configuration save"),
                    readTimeoutMs = 20000
                )
                return@runCatching "opkg disk уже настроен ($existingDisk). Перезапустил initrc. Ждите 30-60 сек, затем проверьте SSH :222."
            }

            var lastErr = ""
            for (url in installerUrls) {
                val cmds = listOf(
                    "no opkg disk",
                    "opkg disk $disk $url",
                    "opkg initrc /opt/etc/init.d/rc.unslung",
                    "system configuration save"
                )
                val (ok, errs) = s.parseCliChecked(cmds, readTimeoutMs = 45000)
                if (errs.isEmpty() && ok > 0) {
                    return@runCatching "opkg disk $disk <- ${url.substringAfterLast('/')}. " +
                        "NDMS скачивает installer и разворачивает в /opt. Ждите 60-90 сек, затем проверьте SSH :222."
                }
                lastErr = errs.joinToString("; ").ifBlank { "ok=$ok" }
            }
            error("Все URL installer отклонены NDMS: $lastErr")
        }
    }

    // Определение диска для opkg: берём первый смонтированный ext4/ntfs раздел USB
    // (media с bus=usb) и возвращаем его UUID:/ (или LABEL:/, если UUID нет).
    suspend fun detectOpkgDiskRef(host: String, user: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val s = RciSession(host, user, pass)
            s.ensureAuth()
            val mediaRaw = s.request("/rci/show/media")
            val list = parseMedia(mediaRaw).filter { it.isUsb && it.fsType.isNotBlank() && it.fsType != "swap" }
            val target = list.firstOrNull { it.isExt4 } ?: list.firstOrNull()
                ?: error("Не найден USB-раздел с файловой системой — вставьте флешку и отформатируйте")
            val ref = if (target.uuid.isNotBlank()) "${target.uuid}:/" else "${target.label}:/"
            "diskRef=$ref (${target.name}/${target.label}, ${target.fsType}, ${target.freeMb}/${target.totalMb} МБ)"
        }
    }
}
