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

    fun request(path: String, method: String = "GET", jsonBody: String? = null, retryAuth: Boolean = true): String {
        val conn = (URL("http://$host$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6000
            readTimeout = 15000
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
            return request(path, method, jsonBody, retryAuth = false)
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
        val raw = request("/rci/show/running-config")
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("message") ?: return raw
        return buildString {
            for (i in 0 until arr.length()) {
                append(arr.optString(i)).append('\n')
            }
        }
    }

    fun parseCli(commands: List<String>): String {
        val payload = JSONArray()
        for (cmd in commands) {
            payload.put(JSONObject().put("parse", cmd))
        }
        return request("/rci/parse", method = "POST", jsonBody = payload.toString())
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

    fun putWebDavFile(port: Int, remotePath: String, bytes: ByteArray): Result<String> = runCatching {
        val cleanPath = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        val url = URL("http://$host:$port$cleanPath")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 8000
            readTimeout = 60000
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
            val mediaRaw = runCatching { session.request("/rci/show/media") }.getOrDefault("{}")
            val runCfg = runCatching { session.getRunningConfigText() }.getOrDefault("")

            val model = verJson.optString("model", verJson.optString("device", "Keenetic / Netcraze"))
            val hwId = verJson.optString("hw_id", "")
            val archRaw = verJson.optString("arch", "")
            val title = verJson.optString("title", verJson.optString("release", ""))
            val ndmObj = verJson.optJSONObject("ndm")
            val ndmTitle = ndmObj?.optString("title", "") ?: ""

            val verMatch = listOf(verJson.optString("release", ""), verJson.optString("title", ""), ndmObj?.optString("exact", "") ?: "")
                .asSequence().mapNotNull { Regex("""(\d+)\.(\d+)""").find(it) }.firstOrNull()
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
                val item = compMap.optJSONObject(key)
                if (item == null || item.optBoolean("installed", true)) {
                    comps.add(key.lowercase())
                }
            }

            val mediaList = parseMedia(mediaRaw)
            val hasUsb = mediaList.any { it.isUsb }
            val hasInternalOpkg = mediaList.any { it.isInternalStorage } || ndmsMajor > 3 || (ndmsMajor == 3 && ndmsMinor >= 7)
            val (compatLevel, compatReason) = CompatibilityMatrix.evaluate(ndmsMajor, ndmsMinor, hasUsb, hasInternalOpkg)

            val opkgDiskLine = runCfg.lines().firstOrNull { it.trim().startsWith("opkg disk ") }?.trim()?.removePrefix("opkg disk ") ?: ""
            val opkgInitrc = runCfg.lines().firstOrNull { it.trim().startsWith("opkg initrc ") }?.trim()?.removePrefix("opkg initrc ") ?: ""
            val ndms5WebdavMatch = Regex("""(?ms)^ip http webdav\b[^!]*?^[ \t]+port (\d+)""").find(runCfg)
            val webdavPortLine = runCfg.lines().firstOrNull { it.trim().startsWith("webdav port ") }?.trim()
            val webdavEnabled5 = Regex("""(?ms)^ip http webdav\b[^!]*?^[ \t]+enable\b""").containsMatchIn(runCfg)
            val webdavPort = ndms5WebdavMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: webdavPortLine?.substringAfter("webdav port ")?.toIntOrNull()
                ?: if (webdavEnabled5) 80 else if (runCfg.contains("webdav enable")) 8080 else 0

            val ssh222 = Gboxcore.probeTCP(host, 222, 1200)
            if (comps.none { it.contains("opkg") } || comps.none { it.contains("netfilter") }) {
                val compListRaw = runCatching { session.request("/rci/components/list") }.getOrDefault("")
                if (compListRaw.contains("opkg", ignoreCase = true)) comps.add("opkg")
                if (compListRaw.contains("netfilter", ignoreCase = true)) comps.add("opkg-kmod-netfilter")
            }
            if (ssh222) {
                if (comps.none { it.contains("opkg") }) comps.add("opkg")
                if (comps.none { it.contains("netfilter") }) comps.add("opkg-kmod-netfilter")
                if (comps.none { it == "ssh" }) comps.add("ssh")
            }
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
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return result
        val mediaObj = root.optJSONObject("media") ?: root
        mediaObj.keys().forEach { devName ->
            val dev = mediaObj.optJSONObject(devName) ?: return@forEach
            val partObj = dev.optJSONObject("partition")
            val isStorage = devName.equals("STORAGE", ignoreCase = true) || devName.contains("STORAGE", ignoreCase = true)
            if (partObj != null) {
                partObj.keys().forEach { uuidKey ->
                    val p = partObj.optJSONObject(uuidKey) ?: return@forEach
                    val fs = p.optString("fstype", p.optString("fs", ""))
                    val total = p.optLong("total", 0L) / 1024L / 1024L
                    val free = p.optLong("free", 0L) / 1024L / 1024L
                    val label = p.optString("label", p.optString("name", uuidKey))
                    result.add(
                        StorageMedia(
                            name = devName,
                            label = label,
                            uuid = p.optString("uuid", uuidKey),
                            fsType = fs,
                            totalMb = total,
                            freeMb = free,
                            isUsb = !isStorage,
                            isExt4 = fs.equals("ext4", ignoreCase = true),
                            isInternalStorage = isStorage
                        )
                    )
                }
            } else {
                val fs = dev.optString("fstype", dev.optString("fs", ""))
                val total = dev.optLong("total", 0L) / 1024L / 1024L
                val free = dev.optLong("free", 0L) / 1024L / 1024L
                result.add(
                    StorageMedia(
                        name = devName,
                        label = dev.optString("label", devName),
                        uuid = dev.optString("uuid", devName),
                        fsType = fs,
                        totalMb = total,
                        freeMb = free,
                        isUsb = !isStorage,
                        isExt4 = fs.equals("ext4", ignoreCase = true) || isStorage,
                        isInternalStorage = isStorage
                    )
                )
            }
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
}
