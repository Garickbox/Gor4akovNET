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
internal fun OverviewPage(
    prefs: Prefs,
    passport: RouterPassport?,
    data: JSONObject?,
    siteProbeJson: JSONObject?,
    benchBefore: JSONObject?,
    benchAfter: JSONObject?,
    diagOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onSvcClick: (String) -> Unit,
    onProbe24: () -> Unit,
    onAutoDiag: () -> Unit,
    onUpdateLists: () -> Unit,
    onBenchBefore: () -> Unit,
    onBenchAfter: () -> Unit,
    onApplyOptimize: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onReboot: () -> Unit,
    onSvcToggle: (String, Boolean) -> Unit = { _, _ -> }
) {
    val sys = data?.optJSONObject("sys")
    val mem = sys?.optJSONObject("mem")
    val disk = sys?.optJSONObject("disk")
    val wan = data?.optJSONObject("wan")
    val svc = data?.optJSONObject("svc")
    val ver = data?.optJSONObject("ver")
    // Справочник NDMS 5.1: 3.178.71 show ip service, 3.178.9 show cifs,
    // 3.178.65 show ip http webdav, 3.178.66 show ip name-server.
    // raw-текст от NDMS (экранированный `~` вместо \n).
    val srv = data?.optJSONObject("srv")

    // Health traffic light
    if (data != null) {
        val entOk = wan?.optBoolean("ent_ok", false) == true
        val nfqUp = svc?.optBoolean("nfqws2", false) == true
        val nfqRules = wan?.optInt("nfq_rules", 0) ?: 0
        val aghInst = wan?.optBoolean("agh_inst", false) == true
        val aghUp = svc?.optBoolean("adguard", false) == true
        val dnsOv = wan?.optBoolean("dns_ov", false) == true
        val polMode = wan?.optString("policy_mode", "policy") ?: "policy"
        val polName = wan?.optString("policy_name", "Gor4akovNET").orEmpty().ifBlank { "Gor4akovNET" }
        val polOk = wan?.optBoolean("policy_ok", false) == true || polMode == "global" || polMode == "stale"
        val polGlobalish = polMode == "global" || polMode == "stale"

        val warns = mutableListOf<String>()
        if (!entOk) warns.add("Entware не смонтирован")
        if (!nfqUp) warns.add("nfqws2 остановлен")
        if (nfqRules == 0) warns.add("нет правил NFQUEUE")
        if (dnsOv && !aghUp) warns.add("dns-override при спящем AdGuard!")
        if (!polGlobalish && !polOk) warns.add("политика $polName выкл")
        if (aghInst && !aghUp && !dnsOv) warns.add("AdGuard остановлен")

        val isGreen = warns.isEmpty()
        val isRed = !entOk || !nfqUp || (dnsOv && !aghUp)
        val bannerColor = when {
            isRed -> Color(0xFF450A0A)
            !isGreen -> Color(0xFF451A03)
            else -> Color(0xFF052E16)
        }
        val borderColor = when {
            isRed -> Color(0xFFEF4444)
            !isGreen -> Color(0xFFF59E0B)
            else -> Color(0xFF22C55E)
        }
        Surface(color = bannerColor, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, borderColor.copy(0.65f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            isRed -> "Критично: ${warns.joinToString(", ")}"
                            !isGreen -> "Внимание: ${warns.joinToString(", ")}"
                            else -> "Светофор: все системы работают штатно"
                        },
                        color = borderColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val polLabel = when (polMode) {
                        "global" -> "Общая (LAN)"
                        "stale" -> "Глобальная (POLICY_NAME остался, не мешает)"
                        else -> if (polOk) "ON ($polName)" else "OFF"
                    }
                    Text("NFQUEUE правил: $nfqRules • Entware: ${if (entOk) "OK" else "Нет"} • Политика: $polLabel", color = Color(0xFFCBD5E1), fontSize = 9.5.sp)
                }
                if (!isGreen) {
                    Button(onClick = onAutoDiag, enabled = !busy, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Починить", fontSize = 10.sp)
                    }
                }
            }
        }
    }

    MiniCard("${sys?.optString("model", "Keenetic / Netcraze")} • NDMS ${sys?.optString("ndms", "—")}", { onTopicHelp("overview") }) {
        val ramT = mem?.optInt("total", 0) ?: 0
        val ramU = mem?.optInt("used", 0) ?: 0
        val swT = mem?.optInt("swap_t", 0) ?: 0
        val swU = mem?.optInt("swap_u", 0) ?: 0
        val ct = sys?.optInt("ct", 0) ?: 0
        val ctMax = sys?.optInt("ct_max", 0) ?: 0
        val diskPct = disk?.optInt("pct", 0) ?: 0
        val loadFirst = sys?.optString("load", "0")?.substringBefore(' ')?.toFloatOrNull() ?: 0f
        val cpus = (sys?.optInt("cpus", 2) ?: 2).coerceAtLeast(1)

        MiniBar("CPU Load (${sys?.optString("load", "—")})", "${(loadFirst / cpus * 100f).toInt().coerceIn(0, 100)}%", loadFirst / cpus)
        MiniBar("RAM", "$ramU / $ramT МБ", if (ramT > 0) ramU.toFloat() / ramT else 0f)
        if (swT > 0) MiniBar("ZRAM / Swap", "$swU / $swT МБ", swU.toFloat() / swT)
        MiniBar("NAT Conntrack", "$ct / $ctMax", if (ctMax > 0) ct.toFloat() / ctMax else 0f)
        MiniBar("Память /opt", "Свободно ${disk?.optInt("avail", 0) ?: 0} из ${disk?.optInt("total", 0) ?: 0} МБ", diskPct / 100f)

        val t24s = sys?.optString("temp24").orEmpty().ifBlank { "—" }
        val t5s = sys?.optString("temp5").orEmpty().ifBlank { "—" }
        MiniRow("Аптайм • Темп. Wi-Fi", "${formatUptime(sys?.optLong("up_sec", 0L) ?: 0L)} • ${t24s}°/${t5s}°C")
        val wanDev = wan?.optString("dev").orEmpty().ifBlank { "WAN" }
        MiniRow("WAN ($wanDev) • Скорость", "${wan?.optString("ip", "—")} • ${wan?.optString("spd", "—")} Мбит/с")
    }

    MiniCard("Службы роутера (нажми для управления)", { onTopicHelp("services") }) {
        val list = listOf(
            "nfqws2" to "Zapret2 nfqws2", "adguard" to "AdGuard Home",
            "gvsub" to "Autocircular", "warp" to "Cloudflare WARP",
            "tg_tunnel" to "TG :1443", "http_tunnel" to "HTTP :1444",
            "z2k_panel" to "z2k Веб :8088", "scheduler" to "Планировщик"
        )
        list.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { (k, title) ->
                    MiniPill(title, svc?.optBoolean(k) == true, Modifier.weight(1f)) { onSvcClick(k) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("z2k: ${ver?.optString("z2k_local", "—")}", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("AGH: ${ver?.optString("agh", "—")}", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }

    // 24-Site Probe Card
    MiniCard("Тест 24 популярных сайтов (с роутера)") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onProbe24, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) {
                Text("Проверить 24 сайта", fontSize = 10.5.sp)
            }
            OutlinedButton(onClick = onAutoDiag, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) {
                Text("Авторемонт", fontSize = 10.5.sp)
            }
        }
        val sitesArr = siteProbeJson?.optJSONArray("sites")
        if (sitesArr != null && sitesArr.length() > 0) {
            val items = (0 until sitesArr.length()).mapNotNull { sitesArr.optJSONObject(it) }
            val okCnt = items.count { it.optBoolean("ok") }
            Text("Доступно: $okCnt из ${items.size}", color = if (okCnt >= items.size - 2) Color(0xFF4ADE80) else Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val cats = items.groupBy { it.optString("cat") }
            cats.forEach { (cat, groupItems) ->
                Text(cat, color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                groupItems.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        rowItems.forEach { s ->
                            val ok = s.optBoolean("ok")
                            val code = s.optInt("code", 0)
                            val ms = s.optInt("tot_ms", 0)
                            Surface(
                                color = if (ok) Color(0xFF052E16) else Color(0xFF450A0A),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (ok) Color(0xFF16A34A) else Color(0xFFDC2626)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.optString("host"), color = Color.White, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(if (ok) "${ms}мс" else "[$code]", color = if (ok) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Optimizer Do / Posle
    var optZram by remember { mutableStateOf(true) }
    var optPpeHw by remember { mutableStateOf(true) }
    var optPpeSw by remember { mutableStateOf(true) }
    var optCache by remember { mutableStateOf(true) }
    var optCt by remember { mutableStateOf(true) }
    MiniCard("Тест и автооптимизация (До / После)") {
        Text("Замерьте скорость, включите оптимизации ядра/AdGuard и сравните реальный прирост.", color = Color(0xFF94A3B8), fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = onBenchBefore, enabled = !busy, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(3.dp)) { Text("1. Замер «До»", fontSize = 9.5.sp) }
            Button(onClick = { onApplyOptimize(optZram, optPpeHw, optPpeSw, optCache, optCt) }, enabled = !busy, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(3.dp)) { Text("2. Ускорить", fontSize = 9.5.sp) }
            OutlinedButton(onClick = onBenchAfter, enabled = !busy && benchBefore != null, modifier = Modifier.weight(1f).height(30.dp), contentPadding = PaddingValues(3.dp)) { Text("3. «После»", fontSize = 9.5.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = optZram, onClick = { optZram = !optZram }, label = { Text("ZRAM", fontSize = 9.sp) })
            FilterChip(selected = optPpeHw, onClick = { optPpeHw = !optPpeHw }, label = { Text("PPE HW", fontSize = 9.sp) })
            FilterChip(selected = optPpeSw, onClick = { optPpeSw = !optPpeSw }, label = { Text("PPE SW", fontSize = 9.sp) })
            FilterChip(selected = optCache, onClick = { optCache = !optCache }, label = { Text("Опт.Кэш DNS", fontSize = 9.sp) })
            FilterChip(selected = optCt, onClick = { optCt = !optCt }, label = { Text("CT 65K", fontSize = 9.sp) })
        }
        if (benchBefore != null) {
            val b = benchBefore
            val a = benchAfter
            Surface(color = Color(0xFF020617), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (a != null) "Сравнение «До -> После»:" else "Базовый замер «До» (примените ускорение и нажмите «После»):", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val pB = b.optDouble("ping_ms", 0.0); val pA = a?.optDouble("ping_ms", 0.0)
                    MiniRow("Пинг 1.1.1.1", if (pA != null) "${"%.1f".format(pB)} -> ${"%.1f".format(pA)} мс" else "${"%.1f".format(pB)} мс")
                    val dB = b.optInt("dns_ms", 0); val dA = a?.optInt("dns_ms", 0)
                    MiniRow("DNS резолв", if (dA != null) "$dB -> $dA мс" else "$dB мс")
                    val yB = b.optInt("yt_ms", 0); val yA = a?.optInt("yt_ms", 0)
                    MiniRow("YouTube TLS", if (yA != null) "$yB -> $yA мс" else "$yB мс")
                    val dcB = b.optInt("dc_ms", 0); val dcA = a?.optInt("dc_ms", 0)
                    MiniRow("Discord TLS", if (dcA != null) "$dcB -> $dcA мс" else "$dcB мс")
                    val rB = b.optInt("ram_free_mb", 0); val rA = a?.optInt("ram_free_mb", 0)
                    MiniRow("Свободно RAM", if (rA != null) "$rB -> $rA МБ" else "$rB МБ")
                }
            }
        }
    }

    // Actions & QR
    MiniCard("Системные действия") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onUpdateLists, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp)) { Text("Обновить списки", fontSize = 10.5.sp) }
            OutlinedButton(onClick = onReboot, enabled = !busy, modifier = Modifier.weight(1f).height(34.dp), contentPadding = PaddingValues(4.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))) {
                Text("Ребут роутера", fontSize = 10.5.sp)
            }
        }
        if (diagOut.isNotBlank()) MonoBox(diagOut)
    }

    // Wi-Fi данные берём из overview JSON (data.wifi) — скрипт парсит их прямо из
    // running-config роутера. Fallback: passport → prefs из Android. Так SSID
    // и пароль видны даже после переустановки APK, когда prefs.wifiPass пуст.
    // Показываем WiFi только если СЕТЬ ЕСТЬ (wifi.bridge непуст). Иначе prefs.wifiSsid
    // по умолчанию = "Gor4akovNET" → карточка светит даже на "голом" роутере.
    val wifiJson = data?.optJSONObject("wifi")
    val liveWifiBridge = wifiJson?.optString("bridge")?.trim().orEmpty()
    val liveWifiSsid = wifiJson?.optString("ssid")?.trim().orEmpty()
    val liveWifiPass = wifiJson?.optString("pass")?.trim().orEmpty()
    val liveWifiSubnet = wifiJson?.optString("subnet")?.trim().orEmpty()
    if (liveWifiBridge.isNotBlank() && liveWifiSsid.isNotBlank()) {
        val activePass = liveWifiPass.ifBlank { prefs.wifiPass.trim() }
        val activeSubnet = liveWifiSubnet.ifBlank { "192.168.77.0/24" }
        WiFiQrCard(ssid = liveWifiSsid, pass = activePass, subnetInfo = activeSubnet)
    }

    // Мануал 3.178: службы NDMS (ip service/cifs/webdav/name-server). Сворачивается
    // внутрь, чтобы не загромождать при пустом. Показывается только если есть данные.
    if (srv != null) {
        NdmsServicesCard(
            ipServiceRaw = srv.optString("ip_service").orEmpty(),
            cifsRaw = srv.optString("cifs").orEmpty(),
            webdavRaw = srv.optString("webdav").orEmpty(),
            dnsRaw = srv.optString("dns").orEmpty()
        )
    }

    NdmsServicesTogglesCard(
        toggles = data?.optJSONObject("tog"),
        busy = busy,
        onToggle = onSvcToggle
    )

    DonateCard(compact = true)
}

@Composable
internal fun ServicesPage(
    data: JSONObject?,
    opkgOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onHelpGuide: (ServiceGuide) -> Unit,
    onInspect: (String) -> Unit,
    onSvcAct: (String, String) -> Unit,
    onCronAdd: (String, String) -> Unit,
    onCronDel: (String) -> Unit,
    onOpkg: (String, String, Boolean) -> Unit
) {
    MiniCard("Службы Entware — CPU, RAM, PID и порты", { onTopicHelp("services") }) {
        val svcs = data?.optJSONArray("svcs") ?: JSONArray()
        if (svcs.length() == 0) {
            Text("Скрипты в /opt/etc/init.d/S* не найдены", color = Color(0xFF64748B), fontSize = 11.sp)
        }
        for (i in 0 until svcs.length()) {
            val s = svcs.optJSONObject(i) ?: continue
            val id = s.optString("id")
            val inst = s.optBoolean("inst")
            val up = s.optBoolean("up")
            val ramMb = s.optDouble("ram_mb", 0.0)
            val ramPct = s.optDouble("ram_pct", 0.0)
            val cpu = s.optDouble("cpu", 0.0)
            val pids = s.optString("pids")
            val ports = s.optString("ports")
            val guide = MonitorHelp.guideForInitId(id)

            Surface(
                color = Color(0xFF111827),
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, if (up) Color(0xFF14532D) else Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(when { up -> Color(0xFF22C55E); inst -> Color(0xFFF59E0B); else -> Color(0xFF475569) }))
                            Text(id, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (guide != null) {
                                HelpBadge { onHelpGuide(guide) }
                            }
                        }
                        if (inst) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { onInspect(id) }, enabled = !busy, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Инсп.", fontSize = 9.sp) }
                                OutlinedButton(onClick = { onSvcAct(id, "restart") }, enabled = !busy, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Рест.", fontSize = 9.sp) }
                                OutlinedButton(onClick = { onSvcAct(id, if (up) "stop" else "start") }, enabled = !busy, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (up) "Стоп" else "Старт", fontSize = 9.sp) }
                            }
                        } else {
                            Text("не исполн.", color = Color(0xFF64748B), fontSize = 9.5.sp)
                        }
                    }
                    if (up || guide != null) {
                        val metrics = if (up) {
                            "RAM: ${"%.1f".format(ramMb)} МБ (${"%.1f".format(ramPct)}%) • CPU: ${"%.1f".format(cpu)}% • PID: ${pids.ifBlank { "-" }}${if (ports.isNotBlank()) " • Порт: $ports" else ""}"
                        } else {
                            guide?.summary ?: "Остановлена"
                        }
                        Text(metrics, color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontFamily = if (up) FontFamily.Monospace else FontFamily.Default)
                    }
                }
            }
        }
    }

    val topProcs = data?.optJSONArray("top_procs") ?: JSONArray()
    if (topProcs.length() > 0) {
        MiniCard("Топ-10 процессов по памяти (всего: ${data?.optInt("mem_free_mb", 0) ?: 0} / ${data?.optInt("mem_tot_mb", 0) ?: 0} МБ свободно)") {
            for (i in 0 until topProcs.length()) {
                val p = topProcs.optJSONObject(i) ?: continue
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.optString("pid")} ${p.optString("cmd")}", color = Color(0xFFCBD5E1), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${"%.1f".format(p.optDouble("ram_mb", 0.0))} МБ", color = Color(0xFF93C5FD), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    // Cron Manager
    val cronArr = data?.optJSONArray("cron") ?: JSONArray()
    var cronExpr by remember { mutableStateOf("0 4 * * *") }
    var cronCmd by remember { mutableStateOf("") }
    MiniCard("Планировщик Cron (${cronArr.length()})") {
        for (i in 0 until cronArr.length()) {
            val line = cronArr.optString(i)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(line, color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { onCronDel(line) }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = cronExpr, onValueChange = { cronExpr = it }, label = { Text("Расписание", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.width(108.dp))
            OutlinedTextField(value = cronCmd, onValueChange = { cronCmd = it }, label = { Text("Команда sh", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            Button(onClick = {
                if (cronExpr.isNotBlank() && cronCmd.isNotBlank()) {
                    onCronAdd(cronExpr, cronCmd)
                    cronCmd = ""
                }
            }, enabled = !busy, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+", fontSize = 14.sp) }
        }
    }

    // OPKG Manager + quick chips
    var opkgQuery by remember { mutableStateOf("") }
    MiniCard("Менеджер пакетов opkg (установлено: ${data?.optInt("opkg_cnt", 0) ?: 0})") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { onOpkg("Проверка opkg", MonitorScripts.CMD_OPKG_CHECK, false) }, enabled = !busy, modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(4.dp)) { Text("Проверить обновления", fontSize = 10.sp) }
            OutlinedButton(onClick = { onOpkg("opkg upgrade", MonitorScripts.CMD_OPKG_UPGRADE_ALL, false) }, enabled = !busy, modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(4.dp)) { Text("Обновить всё", fontSize = 10.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("tcpdump", "htop", "nano", "mtr").forEach { pkg ->
                AssistChip(onClick = { opkgQuery = pkg }, label = { Text("+ $pkg", fontSize = 9.5.sp) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = opkgQuery, onValueChange = { opkgQuery = it }, placeholder = { Text("Пакет...", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { if (opkgQuery.isNotBlank()) onOpkg("Поиск $opkgQuery", MonitorScripts.opkgSearchCmd(opkgQuery), true) }, enabled = !busy, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Найти", fontSize = 9.5.sp) }
            Button(onClick = { if (opkgQuery.isNotBlank()) onOpkg("Уст. $opkgQuery", MonitorScripts.opkgInstallCmd(opkgQuery), false) }, enabled = !busy, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Уст.", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { if (opkgQuery.isNotBlank()) onOpkg("Удал. $opkgQuery", MonitorScripts.opkgRemoveCmd(opkgQuery), false) }, enabled = !busy, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 7.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))) { Text("Удал.", fontSize = 9.5.sp) }
        }
        if (opkgOut.isNotBlank()) MonoBox(opkgOut)
    }
}
