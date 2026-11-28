@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ru.garickbox.gor4akovnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun BackupsPage(
    backups: List<BackupEntry>,
    estText: String,
    includeMedia: Boolean,
    onToggleMedia: (Boolean) -> Unit,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onEstimate: () -> Unit,
    onCreateFull: () -> Unit,
    onCreateCfg: () -> Unit,
    onImport: () -> Unit,
    onShare: (BackupEntry) -> Unit,
    onRestore: (BackupEntry) -> Unit,
    onViewTxt: (BackupEntry) -> Unit,
    onDelete: (String) -> Unit
) {
    MiniCard("Полный 100% потоковый бэкап v2", { onTopicHelp("backups") }) {
        Text("Включает running/startup-config, весь /opt/etc (Dropbear SSH ключи, AdGuard, конфиги), /opt/zapret2, /opt/root, /opt/home, Cron и при необходимости все файлы USB/Storage.", color = Color(0xFF94A3B8), fontSize = 10.sp)
        MiniSwitch("Включить ВСЕ файлы USB / Storage (включая корень)", "Рекомендуется — захватывает все скрипты и папки вне /opt", includeMedia) { v ->
            onToggleMedia(v == 1)
        }
        if (estText.isNotBlank()) {
            MonoBox(estText)
        } else {
            Text("Нажмите «Проверить USB / размер», чтобы увидеть подключённые накопители и размер снимка.", color = Color(0xFF94A3B8), fontSize = 9.5.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = onCreateFull, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) { Text("Полный v2 (.tar.gz)", fontSize = 10.sp) }
            OutlinedButton(onClick = onCreateCfg, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) { Text("NDMS (.txt)", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = onEstimate, enabled = !busy, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(4.dp)) { Text("Проверить USB / размер", fontSize = 9.5.sp) }
            OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(4.dp)) { Text("Импорт с телефона", fontSize = 9.5.sp) }
        }
    }

    MiniCard("Сохранённые снимки в телефоне (${backups.size})") {
        if (backups.isEmpty()) {
            Text("Пока нет сохранённых бэкапов", color = Color(0xFF64748B), fontSize = 11.sp)
        } else {
            backups.forEach { b ->
                val isTar = b.filePath.endsWith(".tar.gz", true)
                Surface(
                    color = Color(0xFF111827),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, if (b.isProtected) Color(0xFF065F46) else Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${if (b.isProtected) "🛡 " else ""}${b.label}",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            MiniPill(b.format.uppercase(), isTar)
                        }
                        Text(
                            "${b.timestamp} • ${b.model} • ${formatSizeMb(b.sizeBytes)}${if (b.sha256.isNotBlank()) " • sha:${b.sha256.take(8)}" else ""}",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Button(onClick = { onRestore(b) }, enabled = !busy, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Откат", fontSize = 10.sp) }
                            if (!isTar) {
                                OutlinedButton(onClick = { onViewTxt(b) }, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Просмотр", fontSize = 10.sp) }
                            }
                            OutlinedButton(onClick = { onShare(b) }, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Экспорт", fontSize = 10.sp) }
                            Spacer(Modifier.weight(1f))
                            if (!b.isProtected) {
                                IconButton(onClick = { onDelete(b.id) }, modifier = Modifier.size(27.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = Color(0xFFFCA5A5), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilesPage(
    dirPath: String,
    data: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRestoreBak: (String) -> Unit,
    onNewDir: () -> Unit,
    onNewFile: () -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    MiniCard("X-plore • $dirPath", { onTopicHelp("files") }) {
        val bookmarks = listOf("/opt/zapret2" to "z2k", "/opt/zapret2/lists" to "Списки", "/opt/etc/AdGuardHome" to "AdGuard", "/opt/etc/init.d" to "init.d", "/tmp" to "/tmp")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bookmarks.forEach { (p, lbl) -> AssistChip(onClick = { onOpenDir(p) }, label = { Text(lbl, fontSize = 9.5.sp) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onNewDir, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.CreateNewFolder, "Папка", tint = Color(0xFF93C5FD), modifier = Modifier.size(16.dp)) }
                IconButton(onClick = onNewFile, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.NoteAdd, "Файл", tint = Color(0xFF93C5FD), modifier = Modifier.size(16.dp)) }
                if (dirPath != "/") {
                    OutlinedButton(onClick = { onOpenDir(dirPath.substringBeforeLast('/').ifBlank { "/" }) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("↑ Вверх", fontSize = 10.sp) }
                }
            }
        }
        val err = data?.optString("err").orEmpty()
        if (err.isNotBlank()) Text(err, color = Color(0xFFFCA5A5), fontSize = 11.sp)
        val entries = data?.optJSONArray("entries") ?: JSONArray()
        val sorted = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
            .sortedWith(compareByDescending<JSONObject> { it.optBoolean("dir") }.thenBy { it.optString("name").lowercase() })
        sorted.forEach { e ->
            val nm = e.optString("name")
            val isDir = e.optBoolean("dir")
            val isLink = e.optBoolean("link")
            val full = if (dirPath == "/") "/$nm" else "$dirPath/$nm"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF111827))
                    .clickable { if (isDir) onOpenDir(full) else onOpenFile(full) }
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Icon(
                        if (isDir || isLink) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                        null,
                        tint = when { isDir -> Color(0xFFFBBF24); isLink -> Color(0xFF38BDF8); else -> Color(0xFF94A3B8) },
                        modifier = Modifier.size(15.dp)
                    )
                    Text(nm, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (!isDir && nm.endsWith(".bak")) {
                        Surface(color = Color(0xFF451A03), shape = RoundedCornerShape(4.dp), modifier = Modifier.clickable { onRestoreBak(full) }) {
                            Text("Откатить", color = Color(0xFFFBBF24), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    Text(if (isDir) "DIR" else formatBytes(e.optDouble("size", 0.0)), color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    if (dirPath.startsWith("/opt") || dirPath.startsWith("/tmp")) {
                        IconButton(onClick = { onDeleteEntry(full) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TorchPage(
    host: String,
    onHostChange: (String) -> Unit,
    liveData: JSONObject?,
    liveEnabled: Boolean,
    onToggleLive: (Boolean) -> Unit,
    out: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onEnableAcct: () -> Unit,
    onCopy: () -> Unit,
    onRun: (String) -> Unit
) {
    val ifaces = liveData?.optJSONArray("ifaces") ?: JSONArray()
    val flows = liveData?.optJSONArray("flows") ?: JSONArray()
    val acct = liveData?.optInt("acct", 0) ?: 0
    var flowFilter by remember { mutableStateOf("") }

    MiniCard("Live Torch — Скорость и потоки в реальном времени", { onTopicHelp("torch") }) {
        MiniSwitch("Автообновление каждые 2 сек", "Живой съём интерфейсов и активных соединений", liveEnabled) { v ->
            onToggleLive(v == 1)
        }
        if (liveData != null && acct == 0) {
            Surface(color = Color(0xFF451A03), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFFF59E0B)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Счётчик байтов потоков (conntrack_acct) выключен", color = Color(0xFFFDE68A), fontSize = 9.5.sp, modifier = Modifier.weight(1f))
                    Button(onClick = onEnableAcct, modifier = Modifier.height(26.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Включить", fontSize = 9.sp) }
                }
            }
        } else if (liveData != null && acct < 0) {
            Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                Text("Ядро этой модели Keenetic без conntrack_acct — доступны live Мбит/с, PPS и активные соединения.", color = Color(0xFF94A3B8), fontSize = 9.sp, modifier = Modifier.padding(7.dp))
            }
        }
        for (i in 0 until ifaces.length()) {
            val f = ifaces.optJSONObject(i) ?: continue
            val rxK = f.optDouble("rx_kbps", 0.0)
            val txK = f.optDouble("tx_kbps", 0.0)
            if (rxK < 0.5 && txK < 0.5 && f.optDouble("pps", 0.0) < 1) continue
            MiniRow(
                f.optString("dev"),
                "↓ ${"%.2f".format(rxK / 1000.0)} Мб/с  ↑ ${"%.2f".format(txK / 1000.0)} Мб/с • ${f.optInt("pps", 0)} pps",
                if (f.optInt("drops", 0) > 0) Color(0xFFFBBF24) else Color(0xFF86EFAC)
            )
        }
    }

    if (flows.length() > 0) {
        MiniCard("Активные потоки трафика (${flows.length()})") {
            OutlinedTextField(value = flowFilter, onValueChange = { flowFilter = it }, placeholder = { Text("Фильтр по клиенту / домену / порту...", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.fillMaxWidth())
            for (i in 0 until flows.length()) {
                val fl = flows.optJSONObject(i) ?: continue
                val src = fl.optString("src")
                val target = fl.optString("host").ifBlank { fl.optString("dst") }
                val port = fl.optString("port")
                if (flowFilter.isNotBlank() && !src.contains(flowFilter, true) && !target.contains(flowFilter, true) && !port.contains(flowFilter, true)) continue
                val bytes = fl.optDouble("bytes", 0.0)
                Row(
                    Modifier.fillMaxWidth().clickable { onHostChange(fl.optString("host").ifBlank { fl.optString("dst") }) }.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$src -> $target:$port (${fl.optString("proto")})", color = Color(0xFFCBD5E1), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${fl.optInt("conns", 1)}x ${if (bytes > 0) formatBytes(bytes) else fl.optString("st")}", color = Color(0xFF93C5FD), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    MiniCard("Глубокая диагностика узла и DPI") {
        OutlinedTextField(value = host, onValueChange = onHostChange, label = { Text("IP или домен", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { onRun("ping") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Ping", fontSize = 9.5.sp) }
            Button(onClick = { onRun("trace") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Trace", fontSize = 9.5.sp) }
            Button(onClick = { onRun("dns_cmp") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Сравн.DNS", fontSize = 9.5.sp) }
            Button(onClick = { onRun("dpi") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("DPI/TLS", fontSize = 9.5.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onRun("http") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("HTTP", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { onRun("tcpdump") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Tcpdump", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { onRun("ports") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Порты", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { onRun("traffic") }, enabled = !busy, modifier = Modifier.weight(1f).height(28.dp), contentPadding = PaddingValues(2.dp)) { Text("Трафик", fontSize = 9.5.sp) }
        }
        if (out.isNotBlank()) MonoBox(out, onCopy = onCopy)
    }
}

@Composable
internal fun LogsSshPage(
    logSrc: String,
    logsOut: String,
    syslogSummary: JSONObject?,
    busy: Boolean,
    logFilter: String,
    onLogFilterChange: (String) -> Unit,
    customCmd: String,
    onCustomCmdChange: (String) -> Unit,
    consoleOut: String,
    onTopicHelp: (String) -> Unit,
    onRefreshSummary: () -> Unit,
    onSelectLog: (String) -> Unit,
    onRunCmd: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    SyslogSummaryCard(
        summary = syslogSummary,
        busy = busy,
        onRefresh = onRefreshSummary,
        onCopy = onCopy
    )
    MiniCard("Логи служб роутера", { onTopicHelp("logs") }) {
        val logBtns = listOf("agh" to "AdGuard", "ndms" to "NDMS", "sched" to "Планир.", "au" to "Автообн.", "tg" to "TG", "http" to "HTTP", "warp" to "WARP", "circ" to "Autocirc", "dmesg" to "dmesg")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            logBtns.forEach { (k, lbl) -> FilterChip(selected = logSrc == k, onClick = { onSelectLog(k) }, label = { Text(lbl, fontSize = 10.sp) }) }
        }
        OutlinedTextField(value = logFilter, onValueChange = onLogFilterChange, placeholder = { Text("Фильтр строк (напр. error)", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.fillMaxWidth())
        val shownLog = if (logFilter.isBlank()) logsOut else logsOut.lines().filter { it.contains(logFilter, ignoreCase = true) }.joinToString("\n").ifBlank { "(нет совпадений)" }
        if (shownLog.isNotBlank()) MonoBox(shownLog, onCopy = { onCopy(shownLog) })
    }
    MiniCard("SSH-терминал") {
        val presets = listOf("free -m", "df -h", "netstat -lnt", "ps | grep nfqws", "top -bn1 | head -n 20", "LD_LIBRARY_PATH= /bin/ndmc -c 'show system'")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { p -> AssistChip(onClick = { onCustomCmdChange(p); onRunCmd(p) }, label = { Text(p, fontSize = 9.sp) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = customCmd, onValueChange = onCustomCmdChange, placeholder = { Text("команда sh...", fontSize = 11.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            Button(onClick = { if (customCmd.isNotBlank()) onRunCmd(customCmd) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Пуск", fontSize = 11.sp) }
        }
        if (consoleOut.isNotBlank()) MonoBox(consoleOut, onCopy = { onCopy(consoleOut) })
    }
}

@Composable
internal fun RestoreModeDialog(
    entry: BackupEntry,
    onDismiss: () -> Unit,
    onExecuteRestore: (BackupEntry, String) -> Unit,
    onRequestSystemConfirm: (BackupEntry) -> Unit
) {
    val isTar = entry.filePath.endsWith(".tar.gz", true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Восстановление из бэкапа", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${entry.label} (${entry.timestamp})", color = Color(0xFF93C5FD), fontSize = 12.sp)
                Text("Устройство: ${entry.model} • ${formatSizeMb(entry.sizeBytes)}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                if (isTar) {
                    Text("Выберите глубину отката (совместимо с v1 и v2):", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Button(
                        onClick = { onExecuteRestore(entry, "bypass") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("1. Только обход (/opt/zapret2 + hooks)", fontSize = 11.5.sp) }
                    OutlinedButton(
                        onClick = { onExecuteRestore(entry, "opt_full") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("2. Весь /opt (/opt/etc, AdGuard, SSH, Cron)", fontSize = 11.5.sp) }
                    OutlinedButton(
                        onClick = { onRequestSystemConfirm(entry) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24))
                    ) { Text("3. 100% Полный откат (+ NDMS config + USB)", fontSize = 11.sp) }
                } else {
                    Text("Это снимок конфигурации роутера (.txt). Применение прогонит команды через RCI (пропуская смену паролей/пользователей) и сохранит конфиг.", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Button(
                        onClick = { onExecuteRestore(entry, "ndms_txt") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Применить NDMS конфиг по RCI", fontSize = 12.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

internal data class BareRestorePrepState(
    val entry: BackupEntry,
    val mode: String,
    val passport: RouterPassport,
    val media: StorageMedia
)

internal suspend fun prepareBareRouterForRestore(
    ctx: android.content.Context,
    host: String,
    prefs: Prefs,
    initialPassport: RouterPassport,
    initialMedia: StorageMedia,
    formatToExt4First: Boolean,
    onStatus: (String) -> Unit
) {
    var curPassport = initialPassport
    var targetMedia = initialMedia

    if (formatToExt4First) {
        onStatus("Форматирую ${targetMedia.name} (${targetMedia.label}) в ext4...")
        val fmtRes = RciClient.initializeMediaExt4(host, prefs.rciUser, prefs.rciPass, targetMedia.name)
        fmtRes.getOrElse { error("Не удалось отформатировать ${targetMedia.name} в ext4: ${it.message}") }
        var ready = false
        for (attempt in 1..20) {
            kotlinx.coroutines.delay(3000)
            onStatus("Разметка ext4 на ${targetMedia.name} ($attempt/20)...")
            val check = RciClient.probePassport(host, prefs.rciUser, prefs.rciPass).getOrNull()
            if (check != null) {
                curPassport = check
                val found = check.mediaList.firstOrNull { it.name == targetMedia.name }
                if (found?.isExt4 == true) {
                    targetMedia = found
                    ready = true
                    break
                }
            }
        }
        if (!ready) error("Флешка ${targetMedia.name} не перешла в ext4 за 60 сек.")
    }

    onStatus("Включаю SSH :22 и WebDAV :80 на роутере...")
    RciClient.enableServicesViaRci(
        host, prefs.rciUser, prefs.rciPass,
        enableSsh = true,
        enableWebdav = curPassport.hasWebdavComp,
        enableFtp = false
    ).getOrElse { error("Не удалось включить SSH/WebDAV через RCI: ${it.message}") }

    curPassport = RciClient.probePassport(host, prefs.rciUser, prefs.rciPass).getOrDefault(curPassport)
    targetMedia = curPassport.mediaList.firstOrNull { it.name == targetMedia.name } ?: targetMedia

    onStatus("Разворачиваю стартовый Entware на ${targetMedia.label.ifBlank { targetMedia.name }}...")
    val offRes = OfflineAssets.uploadEntwareViaWebdavAndConfigure(
        context = ctx,
        passport = curPassport,
        user = prefs.rciUser,
        pass = prefs.rciPass,
        targetMedia = targetMedia
    ) { msg -> onStatus(msg) }

    if (offRes.isFailure) {
        val diskRef = if (targetMedia.uuid.isNotBlank()) "${targetMedia.uuid}:/" else "${targetMedia.label}:/"
        val urls = listOf(
            "http://85.192.60.175/entware/${curPassport.entwareArch}/installer/${curPassport.entwareInstaller}",
            "https://bin.entware.net/${curPassport.entwareArch}/installer/${curPassport.entwareInstaller}"
        )
        onStatus("WebDAV недоступен — ставлю базовый Entware по URL на $diskRef...")
        RciClient.installEntwareViaUrl(host, prefs.rciUser, prefs.rciPass, diskRef, urls)
            .getOrElse { error("Не удалось поставить стартовый Entware: ${it.message}") }
    }

    var sshUp = false
    for (attempt in 1..40) {
        kotlinx.coroutines.delay(3000)
        onStatus("Жду запуска SSH :222 ($attempt/40)...")
        if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { gboxcore.Gboxcore.probeTCP(host, 222, 2500) }) {
            sshUp = true
            break
        }
    }
    if (!sshUp) error("SSH :222 не поднялся за 120 сек после установки Entware.")

    prefs.sshPort = 222
    if (prefs.sshUser.isBlank()) prefs.sshUser = "root"
    val sshCheck = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { gboxcore.Gboxcore.runRouterSSH(host, 222, prefs.sshUser, prefs.sshPass, "echo OK") }.getOrDefault("")
    }
    if (!sshCheck.contains("OK") && prefs.sshPass != "keenetic") {
        val defCheck = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { gboxcore.Gboxcore.runRouterSSH(host, 222, "root", "keenetic", "echo OK") }.getOrDefault("")
        }
        if (defCheck.contains("OK")) {
            prefs.sshUser = "root"
            prefs.sshPass = "keenetic"
        }
    }
}

@Composable
internal fun BareRestoreFormatDialog(
    state: BareRestorePrepState,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirmFormatAndRestore: (BareRestorePrepState) -> Unit
) {
    var confirmWord by remember(state) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Роутер пустой — нужна подготовка флешки", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "На роутере не запущен Entware (SSH :222), а USB-накопитель ${state.media.name} (${state.media.label.ifBlank { state.media.fsType }}, ${state.media.fsType}) не в ext4.",
                    color = Color(0xFFCBD5E1), fontSize = 12.sp
                )
                Text(
                    "Чтобы откатить бэкап с нуля, программа отформатирует ${state.media.name} в swap + ext4 (ВСЕ ФАЙЛЫ на флешке будут удалены), сама включит SSH/WebDAV, поднимет Entware и сразу раскатает архив.",
                    color = Color(0xFFFCA5A5), fontSize = 11.5.sp
                )
                Text("Введите EXT4 для подтверждения:", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                OutlinedTextField(
                    value = confirmWord,
                    onValueChange = { confirmWord = it },
                    placeholder = { Text("EXT4") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && confirmWord.trim().equals("EXT4", true),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                onClick = { onConfirmFormatAndRestore(state) }
            ) { Text("Подготовить ext4 и откатить", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
        }
    )
}
