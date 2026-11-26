package ru.garickbox.gor4akovnet

import android.content.Context
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
        if (latest != null && readSnapshotText(latest) == configText) {
            return latest
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
        listSnapshots(ctx).drop(MAX_ENTRIES).forEach { old ->
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
