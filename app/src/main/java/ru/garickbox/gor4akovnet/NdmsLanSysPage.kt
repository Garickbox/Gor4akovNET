package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NdmsLanSysPage(
    state: NdmsControlState?,
    vaultEntries: List<ConfigVaultEntry>,
    cliOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onApplyCmds: (String, List<String>) -> Unit,
    onRunCli: (String) -> Unit,
    onTakeVault: (String) -> Unit,
    onRestoreVault: (ConfigVaultEntry) -> Unit,
    onDeleteVault: (ConfigVaultEntry) -> Unit,
    onFormatUsbExt4: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    var cliInput by remember { mutableStateOf("") }
    var hostInput by remember(state?.hostname) { mutableStateOf(state?.hostname.orEmpty()) }
    var domInput by remember(state?.domainName) { mutableStateOf(state?.domainName.orEmpty()) }
    var editPool by remember { mutableStateOf<NdmsDhcpPoolInfo?>(null) }
    var poolDns1 by remember { mutableStateOf("") }
    var poolDns2 by remember { mutableStateOf("") }
    var poolLease by remember { mutableStateOf("") }
    var viewVaultEntry by remember { mutableStateOf<Pair<ConfigVaultEntry, String>?>(null) }
    var confirmVaultRestore by remember { mutableStateOf<ConfigVaultEntry?>(null) }
    var confirmFormatDev by remember { mutableStateOf<String?>(null) }
    var formatConfirmWord by remember { mutableStateOf("") }

    MiniCard("Config Vault: снимки NDMS и быстрый откат (${vaultEntries.size})", { onTopicHelp("ndms_lan_sys") }) {
        Text(
            "Перед каждой правкой NDMS снимок сохраняется автоматически. Можно в 1 нажатие откатиться к рабочей конфигурации.",
            color = Color(0xFF94A3B8),
            fontSize = 9.5.sp
        )
        Button(
            onClick = { onTakeVault("Снимок перед настройкой") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(31.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text("Создать снимок running-config сейчас", fontSize = 10.sp)
        }
        vaultEntries.take(8).forEach { v ->
            Surface(
                color = Color(0xFF020617),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v.reason, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${v.timestamp} • ${formatSizeMb(v.sizeBytes)}", color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    WrapRow {
                        Button(onClick = { confirmVaultRestore = v }, enabled = !busy, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Откатить", fontSize = 9.5.sp)
                        }
                        OutlinedButton(onClick = { viewVaultEntry = v to ConfigVault.readSnapshotText(v) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Просмотр", fontSize = 9.5.sp)
                        }
                        IconButton(onClick = { onDeleteVault(v) }, modifier = Modifier.size(25.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    val pools = state?.dhcpPools ?: emptyList()
    MiniCard("LAN и DHCP-пулы (${pools.size})") {
        if (pools.isEmpty()) Text("Пулы DHCP не найдены", color = Color(0xFF64748B), fontSize = 10.sp)
        pools.forEach { pool ->
            Surface(
                color = Color(0xFF020617),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${pool.name}${if (pool.bind.isNotBlank()) " (${pool.bind})" else ""}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = {
                                editPool = pool
                                poolDns1 = pool.dnsServers.getOrNull(0).orEmpty()
                                poolDns2 = pool.dnsServers.getOrNull(1).orEmpty()
                                poolLease = if (pool.leaseSec > 0) pool.leaseSec.toString() else "25200"
                            },
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Изм. DNS/Lease", fontSize = 9.sp) }
                    }
                    Text(
                        "Диапазон: ${pool.rangeStart.ifBlank { "—" }} (${pool.rangeSize}) • Аренда: ${pool.leaseSec}с",
                        color = Color(0xFF93C5FD),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (pool.dnsServers.isNotEmpty()) {
                        Text("DNS: ${pool.dnsServers.joinToString(", ")}", color = Color(0xFF86EFAC), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }

    MiniCard("Система NDMS и USB ext4") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = hostInput, onValueChange = { hostInput = it }, label = { Text("Hostname", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedTextField(value = domInput, onValueChange = { domInput = it }, label = { Text("Домен", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
        }
        WrapRow {
            Button(
                onClick = {
                    val cmds = mutableListOf<String>()
                    if (hostInput.isNotBlank()) cmds.add("system hostname ${hostInput.trim()}")
                    if (domInput.isNotBlank()) cmds.add("ip domain-name ${domInput.trim()}")
                    if (cmds.isNotEmpty()) onApplyCmds("Имя роутера", cmds)
                },
                enabled = !busy,
                modifier = Modifier.height(29.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("Применить имя", fontSize = 10.sp) }
            OutlinedButton(
                onClick = { onApplyCmds("Сохранить Flash", listOf("system configuration save")) },
                enabled = !busy,
                modifier = Modifier.height(29.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("Сохранить во Flash", fontSize = 10.sp) }
        }

        val mediaParts = state?.mediaParts ?: emptyList()
        val blockDevs = state?.blockDevs ?: emptyList()
        if (mediaParts.isNotEmpty() || blockDevs.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF1E293B))
            Text("USB разделы (${mediaParts.size} /dev: ${blockDevs.size}):", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            mediaParts.forEach { m ->
                MiniRow("${m.mediaName} (${m.label})", "${m.fsType.ifBlank { "?" }} • ${m.freeMb}/${m.totalMb} МБ${if (m.isOpt) " [/opt]" else ""}")
            }
            if (blockDevs.isNotEmpty()) {
                WrapRow {
                    blockDevs.forEach { dev ->
                        val isOptPart = mediaParts.any { it.isOpt && dev.endsWith(it.devNode.substringAfterLast('/')) }
                        OutlinedButton(
                            onClick = { formatConfirmWord = ""; confirmFormatDev = dev },
                            enabled = !busy && !isOptPart,
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isOptPart) Color(0xFF64748B) else Color(0xFFFBBF24))
                        ) {
                            Text(if (isOptPart) "$dev (/opt защищён)" else "Format $dev -> ext4", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }

    MiniCard("Интерактивная NDM CLI-консоль (RCI / ndmc)") {
        val presets = listOf("show version", "show interface", "show ip policy", "show ip route", "show media", "show running-config")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { p ->
                AssistChip(onClick = { cliInput = p; onRunCli(p) }, label = { Text(p, fontSize = 9.sp) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cliInput,
                onValueChange = { cliInput = it },
                placeholder = { Text("Команда NDMS (show ...)", fontSize = 10.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { if (cliInput.isNotBlank()) onRunCli(cliInput) },
                enabled = !busy && cliInput.isNotBlank(),
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("Вып.", fontSize = 10.sp) }
        }
        if (cliOut.isNotBlank()) {
            MonoBox(cliOut, onCopy = { onCopy(cliOut) })
        }
    }

    editPool?.let { pool ->
        AlertDialog(
            onDismissRequest = { editPool = null },
            title = { Text("DHCP пул ${pool.name}", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = poolDns1, onValueChange = { poolDns1 = it }, label = { Text("DNS 1 (пусто — убрать)") }, singleLine = true)
                    OutlinedTextField(value = poolDns2, onValueChange = { poolDns2 = it }, label = { Text("DNS 2 (опционально)") }, singleLine = true)
                    OutlinedTextField(value = poolLease, onValueChange = { poolLease = it.filter { c -> c.isDigit() } }, label = { Text("Срок аренды (сек)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cmds = mutableListOf("ip dhcp pool ${pool.name} no dns-server")
                    if (poolDns1.isNotBlank()) cmds.add("ip dhcp pool ${pool.name} dns-server ${poolDns1.trim()}")
                    if (poolDns2.isNotBlank()) cmds.add("ip dhcp pool ${pool.name} dns-server ${poolDns2.trim()}")
                    poolLease.toIntOrNull()?.takeIf { it >= 60 }?.let { sec ->
                        cmds.add("ip dhcp pool ${pool.name} lease $sec")
                    }
                    editPool = null
                    onApplyCmds("DHCP ${pool.name}", cmds)
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editPool = null }) { Text("Отмена") } }
        )
    }

    viewVaultEntry?.let { (entry, text) ->
        AlertDialog(
            onDismissRequest = { viewVaultEntry = null },
            title = { Text("Снимок: ${entry.reason}", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    MonoBox(text.take(8000), onCopy = { onCopy(text) })
                }
            },
            confirmButton = { TextButton(onClick = { viewVaultEntry = null }) { Text("Закрыть") } }
        )
    }

    confirmVaultRestore?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmVaultRestore = null },
            title = { Text("Откатить конфигурацию NDMS?", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Применит снимок «${entry.reason}» (${entry.timestamp}) через RCI. Текущая конфигурация предварительно сохранится в Config Vault.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmVaultRestore = null
                        onRestoreVault(entry)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) { Text("Откатить") }
            },
            dismissButton = { TextButton(onClick = { confirmVaultRestore = null }) { Text("Отмена") } }
        )
    }

    confirmFormatDev?.let { dev ->
        AlertDialog(
            onDismissRequest = { confirmFormatDev = null },
            title = { Text("Форматировать $dev в ext4?", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ВСЕ ДАННЫЕ на разделе $dev будут безвозвратно удалены! Введите EXT4 для подтверждения:", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    OutlinedTextField(
                        value = formatConfirmWord,
                        onValueChange = { formatConfirmWord = it },
                        placeholder = { Text("EXT4") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmFormatDev = null
                        onFormatUsbExt4(dev)
                    },
                    enabled = formatConfirmWord.trim().equals("EXT4", true),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Форматировать") }
            },
            dismissButton = { TextButton(onClick = { confirmFormatDev = null }) { Text("Отмена") } }
        )
    }
}
