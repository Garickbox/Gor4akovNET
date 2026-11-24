package ru.garickbox.gor4akovnet

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatErrorItem(
    val ts: String,
    val stage: String,
    val msg: String,
    val device: String = "",
    val router: String = ""
)

data class DeviceStatSummary(
    val id: String,
    val device: String,
    val appVer: String,
    val routerModel: String,
    val ndms: String,
    val okCount: Int,
    val failCount: Int,
    val helped: Boolean,
    val lastSeen: String,
    val errors: List<StatErrorItem>
)

data class GlobalStatsReport(
    val totalApk: Int,
    val helpedUsers: Int,
    val failedOnlyUsers: Int,
    val totalSuccessInstalls: Int,
    val totalErrors: Int,
    val remoteConnected: Boolean,
    val remoteNote: String,
    val devices: List<DeviceStatSummary>,
    val allErrors: List<StatErrorItem>
)

object Telemetry {
    private fun nowStr(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun deviceLabel(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})".trim()

    val isRemoteConfigured: Boolean
        get() = (BuildConfig.STATS_GIST_ID.isNotBlank() && BuildConfig.STATS_TOKEN.isNotBlank()) ||
            BuildConfig.STATS_URL.isNotBlank()

    private fun readLocalObj(prefs: Prefs): JSONObject {
        val raw = prefs.telemetryJson
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        if (!obj.has("id")) obj.put("id", prefs.installId)
        if (!obj.has("device")) obj.put("device", deviceLabel())
        if (!obj.has("app_ver")) obj.put("app_ver", BuildConfig.VERSION_NAME)
        if (!obj.has("first_seen")) obj.put("first_seen", nowStr())
        if (!obj.has("ok_count")) obj.put("ok_count", 0)
        if (!obj.has("fail_count")) obj.put("fail_count", 0)
        if (!obj.has("helped")) obj.put("helped", false)
        if (!obj.has("errors")) obj.put("errors", JSONArray())
        return obj
    }

    suspend fun recordOpen(prefs: Prefs, routerModel: String = "", ndms: String = "") {
        withContext(Dispatchers.IO) {
            runCatching {
                val obj = readLocalObj(prefs)
                obj.put("device", deviceLabel())
                obj.put("app_ver", BuildConfig.VERSION_NAME)
                obj.put("last_seen", nowStr())
                if (routerModel.isNotBlank()) obj.put("router_model", routerModel)
                if (ndms.isNotBlank()) obj.put("ndms", ndms)
                prefs.telemetryJson = obj.toString()
                pushRemote(prefs.installId, obj)
            }
        }
    }

    suspend fun recordInstallSuccess(prefs: Prefs, routerModel: String, ndms: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val obj = readLocalObj(prefs)
                obj.put("last_seen", nowStr())
                if (routerModel.isNotBlank()) obj.put("router_model", routerModel)
                if (ndms.isNotBlank()) obj.put("ndms", ndms)
                obj.put("ok_count", obj.optInt("ok_count", 0) + 1)
                obj.put("helped", true)
                prefs.telemetryJson = obj.toString()
                pushRemote(prefs.installId, obj)
            }
        }
    }

    suspend fun recordError(prefs: Prefs, stage: String, message: String, routerModel: String = "", ndms: String = "", countAsFail: Boolean = true) {
        withContext(Dispatchers.IO) {
            runCatching {
                val obj = readLocalObj(prefs)
                obj.put("last_seen", nowStr())
                if (routerModel.isNotBlank()) obj.put("router_model", routerModel)
                if (ndms.isNotBlank()) obj.put("ndms", ndms)
                if (countAsFail) obj.put("fail_count", obj.optInt("fail_count", 0) + 1)
                val arr = obj.optJSONArray("errors") ?: JSONArray()
                val next = JSONArray()
                next.put(JSONObject().apply {
                    put("ts", nowStr())
                    put("stage", stage)
                    put("msg", message.take(400))
                })
                for (i in 0 until minOf(arr.length(), 19)) {
                    next.put(arr.getJSONObject(i))
                }
                obj.put("errors", next)
                prefs.telemetryJson = obj.toString()
                pushRemote(prefs.installId, obj)
            }
        }
    }

    private fun pushRemote(installId: String, payload: JSONObject) {
        if (BuildConfig.STATS_GIST_ID.isNotBlank() && BuildConfig.STATS_TOKEN.isNotBlank()) {
            val gistUrl = "https://api.github.com/gists/${BuildConfig.STATS_GIST_ID}"
            val body = JSONObject().apply {
                put("files", JSONObject().apply {
                    put("dev_$installId.json", JSONObject().apply {
                        put("content", payload.toString(2))
                    })
                })
            }.toString()
            val conn = (URL(gistUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                connectTimeout = 6000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer ${BuildConfig.STATS_TOKEN}")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "Gor4akovNET/${BuildConfig.VERSION_NAME}")
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            conn.responseCode
            conn.disconnect()
            return
        }
        if (BuildConfig.STATS_URL.isNotBlank()) {
            val conn = (URL(BuildConfig.STATS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            conn.responseCode
            conn.disconnect()
        }
    }

    private fun parseDeviceObj(idFallback: String, o: JSONObject): DeviceStatSummary {
        val dev = o.optString("device", "Android")
        val router = o.optString("router_model", "—")
        val errsJson = o.optJSONArray("errors") ?: JSONArray()
        val errs = (0 until errsJson.length()).mapNotNull { idx ->
            val e = errsJson.optJSONObject(idx) ?: return@mapNotNull null
            StatErrorItem(
                ts = e.optString("ts", ""),
                stage = e.optString("stage", ""),
                msg = e.optString("msg", ""),
                device = dev,
                router = router
            )
        }
        val ok = o.optInt("ok_count", 0)
        return DeviceStatSummary(
            id = o.optString("id", idFallback),
            device = dev,
            appVer = o.optString("app_ver", "—"),
            routerModel = router,
            ndms = o.optString("ndms", "—"),
            okCount = ok,
            failCount = o.optInt("fail_count", 0),
            helped = o.optBoolean("helped", ok > 0),
            lastSeen = o.optString("last_seen", "—"),
            errors = errs
        )
    }

    suspend fun fetchReport(prefs: Prefs): GlobalStatsReport = withContext(Dispatchers.IO) {
        val local = parseDeviceObj(prefs.installId, readLocalObj(prefs))
        val devices = mutableMapOf(local.id to local)
        var remoteOk = false
        var note = "Локальная статистика устройства. Для глобальной задайте GBOX_STATS_GIST_ID и GBOX_STATS_TOKEN в GitHub Secrets."

        if (BuildConfig.STATS_GIST_ID.isNotBlank() && BuildConfig.STATS_TOKEN.isNotBlank()) {
            runCatching {
                val conn = (URL("https://api.github.com/gists/${BuildConfig.STATS_GIST_ID}").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.STATS_TOKEN}")
                    setRequestProperty("User-Agent", "Gor4akovNET/${BuildConfig.VERSION_NAME}")
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.let { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() } ?: ""
                conn.disconnect()
                require(code in 200..299) { "GitHub HTTP $code" }
                val files = JSONObject(text).optJSONObject("files") ?: JSONObject()
                files.keys().forEach { fn ->
                    if (fn.startsWith("dev_") && fn.endsWith(".json")) {
                        val content = files.optJSONObject(fn)?.optString("content", "") ?: ""
                        runCatching {
                            val dObj = JSONObject(content)
                            val parsed = parseDeviceObj(fn.removePrefix("dev_").removeSuffix(".json"), dObj)
                            devices[parsed.id] = parsed
                        }
                    }
                }
                remoteOk = true
                note = "Синхронизировано с GitHub Gist (${devices.size} устр.)"
            }.onFailure { e ->
                note = "Ошибка синхронизации GitHub: ${e.message} (показаны локальные данные)"
            }
        } else if (BuildConfig.STATS_URL.isNotBlank()) {
            runCatching {
                val conn = (URL(BuildConfig.STATS_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 10000
                }
                val text = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).readText()
                conn.disconnect()
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val parsed = parseDeviceObj("u$i", o)
                    devices[parsed.id] = parsed
                }
                remoteOk = true
                note = "Синхронизировано с внешним эндпоинтом (${devices.size} устр.)"
            }.onFailure { e ->
                note = "Ошибка эндпоинта: ${e.message}"
            }
        }

        val devList = devices.values.sortedByDescending { it.lastSeen }
        val helped = devList.count { it.helped }
        val failedOnly = devList.count { !it.helped && it.failCount > 0 }
        val totalOk = devList.sumOf { it.okCount }
        val allErrs = devList.flatMap { it.errors }.sortedByDescending { it.ts }

        GlobalStatsReport(
            totalApk = devList.size,
            helpedUsers = helped,
            failedOnlyUsers = failedOnly,
            totalSuccessInstalls = totalOk,
            totalErrors = allErrs.size,
            remoteConnected = remoteOk,
            remoteNote = note,
            devices = devList,
            allErrors = allErrs
        )
    }
}
