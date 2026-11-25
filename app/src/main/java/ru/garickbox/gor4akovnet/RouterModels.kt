package ru.garickbox.gor4akovnet

enum class CompatLevel {
    FULL,
    USB_ONLY,
    UNSUPPORTED,
    UNKNOWN
}

data class StorageMedia(
    val name: String,
    val label: String,
    val uuid: String,
    val fsType: String,
    val totalMb: Long,
    val freeMb: Long,
    val isUsb: Boolean,
    val isExt4: Boolean,
    val isInternalStorage: Boolean
)

data class RouterPassport(
    val host: String,
    val model: String = "Неизвестная модель",
    val hwId: String = "",
    val ndmsTitle: String = "",
    val ndmsMajor: Int = 0,
    val ndmsMinor: Int = 0,
    val cpuArch: String = "",
    val entwareArch: String = "aarch64-k3.10",
    val entwareInstaller: String = "aarch64-installer.tar.gz",
    val compatLevel: CompatLevel = CompatLevel.UNKNOWN,
    val compatReason: String = "",
    val ramTotalMb: Int = 0,
    val cpuCount: Int = 0,
    val uptimeSec: Long = 0L,
    val mediaList: List<StorageMedia> = emptyList(),
    val components: Set<String> = emptySet(),
    val hasOpkgComp: Boolean = false,
    val hasNetfilterComp: Boolean = false,
    val hasWebdavComp: Boolean = false,
    val hasSshComp: Boolean = false,
    val webdavPort: Int = 0,
    val opkgDisk: String = "",
    val opkgInitrc: String = "",
    val ssh222Open: Boolean = false,
    val ssh22Open: Boolean = false,
    val agh3000Open: Boolean = false,
    val wanOnline: Boolean = false,
    val gor4akovPolicyExists: Boolean = false,
    val gor4akovBridgeExists: Boolean = false,
    val gor4akovSsid: String = ""
)

data class SystemStats(
    val cpuPct: Int = 0,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val zramUsedMb: Int = 0,
    val zramTotalMb: Int = 0,
    val swapUsedMb: Int = 0,
    val swapTotalMb: Int = 0,
    val tempCpuC: Int = 0,
    val conntrackCur: Int = 0,
    val conntrackMax: Int = 0,
    val loadAvg: String = "0.00",
    val uptimeStr: String = "-",
    val optFreeMb: Int = 0,
    val optTotalMb: Int = 0,
    val optMount: String = "-",
    val arch: String = "-",
    val hostname: String = "-",
    val firmware: String = "-"
)

data class ServiceStatus(
    val entwareOk: Boolean = false,
    val z2kInstalled: Boolean = false,
    val z2kRunning: Boolean = false,
    val z2kMode: String = "-",
    val nfqwsCount: Int = 0,
    val iptablesRules: Int = 0,
    val aghInstalled: Boolean = false,
    val aghRunning: Boolean = false,
    val aghDnsOverride: Boolean = false,
    val warpInstalled: Boolean = false,
    val warpRunning: Boolean = false,
    val tgProxyRunning: Boolean = false,
    val dropbearRunning: Boolean = false,
    val policyName: String = "Gor4akovNET",
    val policyActive: Boolean = false,
    val wifiBridgeName: String = "",
    val wifiSubnet: String = "",
    val wifiSsid: String = "",
    val wifiPass: String = "",
    val installMarker: Boolean = false
)

data class ClientHost(
    val mac: String,
    val ip: String,
    val name: String,
    val segment: String,
    val policy: String,
    val qosClass: Int,
    val isOnline: Boolean,
    val isStaticDhcp: Boolean,
    val rxTxInfo: String = ""
) {
    val inGor4akov: Boolean get() = policy.equals("Gor4akovNET", ignoreCase = true) || segment.contains("Gor4akov", ignoreCase = true)
}

data class BackupEntry(
    val id: String,
    val timestamp: String,
    val label: String,
    val model: String,
    val sizeBytes: Long,
    val filePath: String,
    val format: String = if (filePath.endsWith(".tar.gz", true)) "tar_v1" else "txt",
    val isProtected: Boolean = false,
    val sha256: String = ""
)

data class SetupPresetInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val available: Boolean,
    val description: String
)

data class AghQueryItem(
    val time: String,
    val client: String,
    val domain: String,
    val qtype: String,
    val blocked: Boolean,
    val reason: String
)

data class PortForwardRule(
    val index: String,
    val proto: String,
    val port: String,
    val toIp: String,
    val toPort: String,
    val desc: String
)

data class InitServiceItem(
    val name: String,
    val enabled: Boolean,
    val running: Boolean
)

object CompatibilityMatrix {
    private val AARCH64_MODELS = setOf(
        "KN-1012", "NC-1012", "KN-1811", "KN-2710", "KN-3510", "KN-3811", "KN-3812",
        "KN-3911", "KN-4710", "KN-4910", "KN-1912"
    )
    private val MIPSEL_MODELS = setOf(
        "KN-1010", "KN-1011", "KN-1810", "KN-1910", "KN-1913", "KN-2310", "KN-2311",
        "KN-2610", "KN-2910", "KN-3010", "KN-3012", "KN-3710", "KN-3711", "KN-3810",
        "KN-1710", "KN-1711", "KN-1713", "KN-1610", "KN-1611", "KN-1613"
    )
    private val MIPS_MODELS = setOf(
        "KN-2010", "KN-2011", "KN-2012", "KN-2110", "KN-2111", "KN-2112",
        "KN-2210", "KN-2211", "KN-2410", "KN-2510", "KN-3610"
    )

    fun resolveArch(hwId: String, model: String, rawArch: String): Pair<String, String> {
        val hay = "$hwId $model $rawArch".uppercase()
        return when {
            hay.contains("AARCH64") || hay.contains("ARM64") || AARCH64_MODELS.any { hay.contains(it) } ->
                "aarch64-k3.10" to "aarch64-installer.tar.gz"
            hay.contains("MIPSEL") || MIPSEL_MODELS.any { hay.contains(it) } ->
                "mipselsf-k3.4" to "mipsel-installer.tar.gz"
            hay.contains("MIPS") || MIPS_MODELS.any { hay.contains(it) } ->
                "mipssf-k3.4" to "mips-installer.tar.gz"
            else -> "aarch64-k3.10" to "aarch64-installer.tar.gz"
        }
    }

    fun evaluate(
        major: Int,
        minor: Int,
        hasUsb: Boolean,
        hasInternalOpkg: Boolean,
        mediaProbed: Boolean = true,
        sshAlreadyUp: Boolean = false
    ): Pair<CompatLevel, String> {
        if (!mediaProbed && !sshAlreadyUp) {
            return CompatLevel.UNKNOWN to
                "Не удалось прочитать список накопителей по RCI. Проверьте USB-диск или внутреннюю память на шаге 2."
        }
        if (!hasUsb && !hasInternalOpkg && !sshAlreadyUp) {
            return CompatLevel.UNSUPPORTED to
                "Нет USB-флешки (ext4) и нет пригодной внутренней памяти (>= 32 МБ) для OPKG. Вставьте USB ext4 и нажмите «Проверить USB»."
        }
        if (!hasUsb && hasInternalOpkg) {
            return CompatLevel.USB_ONLY to
                "USB не обнаружен. Установка во внутреннюю память ($major.$minor) возможна, но для AdGuard Home / WARP и бэкапов рекомендуем USB ext4."
        }
        if (major in 1..3 && !(major == 3 && minor >= 7)) {
            return CompatLevel.USB_ONLY to
                "KeeneticOS $major.$minor: работает ТОЛЬКО с USB-флешкой в ext4."
        }
        if (major >= 4) {
            return CompatLevel.FULL to
                "Netcraze / KeeneticOS $major.$minor: полная поддержка (USB ext4, OPKG + Netfilter + изолированный Wi-Fi сегмент)."
        }
        if (major == 3 && minor >= 7) {
            return CompatLevel.FULL to
                "KeeneticOS $major.$minor: полная поддержка (USB ext4 или внутренняя память, сегменты и OPKG)."
        }
        if (sshAlreadyUp) {
            return CompatLevel.FULL to
                "Entware SSH :222 уже активен — роутер готов к установке и мониторингу (версия ОС: $major.$minor)."
        }
        return CompatLevel.UNKNOWN to
            "Версия прошивки не определена точно. Проверьте наличие OPKG и Netfilter."
    }
}
