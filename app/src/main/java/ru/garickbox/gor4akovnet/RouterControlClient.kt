package ru.garickbox.gor4akovnet

import android.content.Context
import gboxcore.Gboxcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
                if (verJson != null) {
                    model = verJson.optString("model", verJson.optString("device", model)).orEmpty().ifBlank { model }
                }
            }.isSuccess

            if (!rciOk || runCfg.isBlank()) {
                transport = "SSH ndmc"
                val sshCfg = runSshDirect(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c 'show running-config' 2>/dev/null | tr -d '\\r'")
                if (sshCfg.isBlank()) error("Не удалось получить running-config ни по RCI, ни по SSH")
                runCfg = sshCfg
                val verOut = runCatching {
                    runSshDirect(prefs, "LD_LIBRARY_PATH= /bin/ndmc -c 'show version' 2>/dev/null | grep -iE 'model:|device:' | head -n1")
                }.getOrDefault("")
                if (verOut.contains(":")) model = verOut.substringAfter(":").trim().ifBlank { model }
            }

            val probeDiskCmd = """
                opt_dev=§(awk '§2=="/opt"{print §1; exit}' /proc/mounts 2>/dev/null)
                devs=§(ls /dev/sd* 2>/dev/null | tr '\n' ',')
                echo "OPT=§opt_dev"
                echo "DEVS=§devs"
            """.trimIndent().replace('§', '$')
            val sshDiskInfo = runCatching { runSshDirect(prefs, probeDiskCmd) }.getOrDefault("")
            val optDev = sshDiskInfo.lines().firstOrNull { it.startsWith("OPT=") }?.removePrefix("OPT=")?.trim().orEmpty()
            val blockDevs = sshDiskInfo.lines().firstOrNull { it.startsWith("DEVS=") }
                ?.removePrefix("DEVS=")?.split(',')
                ?.map { it.trim() }?.filter { it.matches(Regex("""^/dev/sd[a-z][0-9]+$""")) } ?: emptyList()

            val cfgIfaces = NdmsConfigParser.parseInterfacesFromConfig(runCfg)
            val rciIfaces = if (ifaceRaw.isNotBlank()) NdmsConfigParser.parseInterfacesFromRci(ifaceRaw) else emptyMap()

            NdmsControlState(
                transport = transport,
                model = model,
                hostname = NdmsConfigParser.parseTopValue(runCfg, "system hostname ") ?: "Keenetic",
                domainName = NdmsConfigParser.parseTopValue(runCfg, "ip domain-name ") ?: "local",
                runningConfig = runCfg,
                interfaces = NdmsConfigParser.mergeInterfaces(cfgIfaces, rciIfaces),
                policies = NdmsConfigParser.parsePoliciesFromConfig(runCfg),
                routes = NdmsConfigParser.parseRoutesFromConfig(runCfg),
                dhcpPools = NdmsConfigParser.parseDhcpPoolsFromConfig(runCfg),
                mediaParts = NdmsConfigParser.parseMediaParts(mediaRaw, optDev),
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
                    // Каждую команду выполняем отдельно и помечаем её маркером, чтобы
                    // разобрать какой именно ndmc-вызов упал. Раньше весь вывод склеивался
                    // в один "OK" — и `error: interface not found` выглядел как успех.
                    val sshScript = buildString {
                        finalCmds.forEachIndexed { i, c ->
                            append("echo \"@@CMD_${i}_BEGIN@@\"\n")
                            append("LD_LIBRARY_PATH= /bin/ndmc -c '${sq(c)}' 2>&1\n")
                            append("echo \"@@CMD_${i}_END:rc=\$?@@\"\n")
                        }
                    }
                    val out = runSshDirect(prefs, sshScript)
                    val failed = Regex("""@@CMD_(\d+)_END:rc=([1-9]\d*)@@""")
                        .findAll(out)
                        .mapNotNull { m -> m.groupValues[1].toIntOrNull()?.let { it to m.groupValues[2] } }
                        .toList()
                    val errLines = out.lines().filter { l ->
                        val t = l.lowercase()
                        (t.contains("error") || t.contains("not found") || t.contains("failed") || t.contains("invalid")) &&
                            !t.startsWith("@@cmd_")
                    }
                    when {
                        failed.isEmpty() && errLines.isEmpty() ->
                            "OK (SSH ndmc): ${finalCmds.size} команд применено, снимок в Config Vault"
                        failed.isEmpty() ->
                            "Внимание (SSH ndmc): команды прошли, но в выводе есть сообщения:\n${errLines.take(8).joinToString("\n")}"
                        else -> {
                            val detail = failed.joinToString("\n") { (i, rc) ->
                                "  [rc=$rc] ${finalCmds.getOrNull(i) ?: "?"}"
                            }
                            "ОШИБКА (SSH ndmc): ${failed.size} из ${finalCmds.size} команд не применились:\n$detail" +
                                (if (errLines.isNotEmpty()) "\nВывод ndmc:\n${errLines.take(8).joinToString("\n")}" else "")
                        }
                    }
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
                NdmsConfigParser.formatParseOutput(s.parseCli(listOf(clean), readTimeoutMs = 30000))
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
                    val flat = NdmsConfigParser.flattenConfigLinesForSsh(cfgText)
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
}
