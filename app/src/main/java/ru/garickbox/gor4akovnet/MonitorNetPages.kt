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
internal fun StoragePage(
    data: JSONObject?,
    doctor: JSONObject?,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onRefreshDoctor: () -> Unit,
    onCleanupStorage: () -> Unit,
    onAction: (String, String) -> Unit
) {
    val disks = data?.optJSONArray("disks") ?: JSONArray()
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    MiniCard("Накопители USB и /opt", { onTopicHelp("storage") }) {
        if (disks.length() == 0) Text("Примонтированные разделы не найдены", color = Color(0xFF64748B), fontSize = 11.sp)
        for (i in 0 until disks.length()) {
            val d = disks.optJSONObject(i) ?: continue
            MiniBar(d.optString("mnt"), "${d.optInt("used")} / ${d.optInt("tot")} МБ (${d.optInt("pct")}%)", d.optInt("pct") / 100f)
        }
    }
    MiniCard("Системные функции NDMS", { onTopicHelp("storage") }) {
        MiniSwitch("Windows-сеть (SMB / CIFS)", "Общие папки флешки в локальной сети", ndms.optBoolean("cifs"), { onToggleHelp("cifs") }) { v ->
            onAction("CIFS", MonitorScripts.ndmcSaveCmd(if (v == 1) "service cifs" else "no service cifs"))
        }
        MiniSwitch("WebDAV-сервер", "Доступ по HTTP к файлам накопителя и заливка Entware", ndms.optBoolean("webdav"), { onToggleHelp("webdav") }) { v ->
            val cmds = if (v == 1) arrayOf("ip http webdav", "ip http webdav enable") else arrayOf("no ip http webdav enable")
            onAction("WebDAV", MonitorScripts.ndmcSaveCmd(*cmds))
        }
        MiniSwitch("ZRAM (сжатие RAM)", "Увеличивает доступную память без износа флешки", ndms.optBoolean("zram"), { onToggleHelp("zram") }) { v ->
            onAction("ZRAM", MonitorScripts.ndmcSaveCmd(if (v == 1) "system zram" else "system no zram"))
        }
        MiniSwitch("Режим USB 2.0", "Повышает стабильность питания и убирает помехи 2.4 ГГц", ndms.optBoolean("usb2"), { onToggleHelp("usb2") }) { v ->
            onAction("USB 2.0", MonitorScripts.ndmcSaveCmd(if (v == 1) "system set dev.usb.force_usb2 1" else "system set dev.usb.force_usb2 0"))
        }
    }
    StorageDoctorCard(
        doctor = doctor,
        busy = busy,
        onRefresh = onRefreshDoctor,
        onCleanup = onCleanupStorage
    )
}

@Composable
internal fun WanPage(data: JSONObject?, benchOut: String, busy: Boolean, onTopicHelp: (String) -> Unit, onBench: () -> Unit) {
    MiniCard("Интернет и маршруты (WAN)", { onTopicHelp("wan") }) {
        val dev = data?.optString("dev").orEmpty().ifBlank { "—" }
        MiniRow("Активный WAN", "$dev • ${data?.optString("wan", "—")}")
        MiniRow("Шлюз провайдера", data?.optString("gw", "—") ?: "—")
        MiniRow("Скорость порта", "${data?.optString("spd", "—")} Мбит/с")
        MiniRow("DNS системы", data?.optString("dns", "—") ?: "—")
        MiniRow("Трафик WAN", "↓ ${formatBytes(data?.optDouble("rx", 0.0) ?: 0.0)}  ↑ ${formatBytes(data?.optDouble("tx", 0.0) ?: 0.0)}")
        val ndmWan = data?.optString("ndm_wan").orEmpty()
        if (ndmWan.isNotBlank()) MonoBox(ndmWan)
        val pol = data?.optString("pol_info").orEmpty()
        if (pol.isNotBlank()) {
            Text("Политика маршрутизации:", color = Color(0xFF93C5FD), fontSize = 10.sp)
            MonoBox(pol)
        }
        Button(onClick = onBench, enabled = !busy, modifier = Modifier.fillMaxWidth().height(34.dp)) {
            Text("Замерить скорость и пинг с роутера", fontSize = 11.sp)
        }
        if (benchOut.isNotBlank()) MonoBox(benchOut)
    }
}

@Composable
internal fun DnsPage(
    data: JSONObject?,
    probeOut: String,
    busy: Boolean,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onInstallAdguard: () -> Unit,
    onAction: (String, String) -> Unit,
    onQuickBypassDomain: (String) -> Unit,
    onQuickWhitelistDomain: (String) -> Unit,
    onProbe: () -> Unit
) {
    val agh = data?.optJSONObject("agh") ?: JSONObject()
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    val upstreams = agh.optJSONArray("upstreams") ?: JSONArray()
    val filters = agh.optJSONArray("filters") ?: JSONArray()
    val errs = data?.optJSONArray("errs") ?: JSONArray()
    val qlog = data?.optJSONArray("qlog") ?: JSONArray()
    val port = data?.optInt("agh_port", 3000) ?: 3000
    val aghUp = data?.optBoolean("agh_up") == true

    MiniCard("AdGuard Home v1.0.0 (:$port) — Запросов: ${data?.optInt("qcnt", 0) ?: 0}", { onTopicHelp("dns") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MiniPill(if (aghUp) "AdGuard Работает" else "AdGuard Остановлен", aghUp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (!aghUp || upstreams.length() == 0) {
                    Button(onClick = onInstallAdguard, enabled = !busy, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Развернуть AGH", fontSize = 10.sp) }
                }
                OutlinedButton(onClick = onProbe, enabled = !busy, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Тест DNS", fontSize = 10.sp) }
            }
        }
        MiniSwitch("Защита (protection_enabled)", "Общий рубильник фильтрации рекламы", agh.optBoolean("prot"), { onToggleHelp("agh_prot") }) { v ->
            onAction("AdGuard защита", MonitorScripts.setAghKeyCmd("protection_enabled", v == 1))
        }
        MiniSwitch("Фильтры доменов (filtering)", "Блокировка рекламы по чёрным спискам", agh.optBoolean("filt"), { onToggleHelp("agh_filt") }) { v ->
            onAction("Фильтры AdGuard", MonitorScripts.setAghKeyCmd("filtering_enabled", v == 1))
        }
        MiniSwitch("Перехват DNS роутером (opkg dns-override)", "Направляет весь DNS сети на AdGuard :53", ndms.optBoolean("dns_ov"), { onToggleHelp("dns_ov") }) { v ->
            onAction("dns-override", MonitorScripts.ndmcSaveCmd(if (v == 1) "opkg dns-override" else "no opkg dns-override"))
        }
        MiniSwitch("DNSSEC", "Криптографическая проверка подлинности ответов DNS", agh.optBoolean("dnssec"), { onToggleHelp("dnssec") }) { v ->
            onAction("DNSSEC", MonitorScripts.setAghKeyCmd("enable_dnssec", v == 1))
        }
        MiniSwitch("Оптимистичный кэш", "Мгновенная отдача кэша с фоновым обновлением", agh.optBoolean("cache_opt"), { onToggleHelp("cache_opt") }) { v ->
            onAction("Опт. кэш", MonitorScripts.setAghKeyCmd("cache_optimistic", v == 1))
        }
        MiniSwitch("Блокировать IPv6 (AAAA)", "Исключает утечку трафика мимо IPv4 правил", agh.optBoolean("no_aaaa"), { onToggleHelp("no_aaaa") }) { v ->
            onAction("AAAA block", MonitorScripts.setAghKeyCmd("aaaa_disabled", v == 1))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { onAction("Профиль DoH Стабильный", MonitorScripts.setAghDnsProfile("doh_stable")) }, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("DoH Стабильный", fontSize = 9.5.sp) }
            OutlinedButton(onClick = { onAction("Профиль Fast UDP", MonitorScripts.setAghDnsProfile("fast_udp")) }, modifier = Modifier.height(27.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Быстрый UDP", fontSize = 9.5.sp) }
        }
        if (probeOut.isNotBlank()) MonoBox(probeOut)
    }

    MiniCard("Апстримы и фильтры (${filters.length()})") {
        if (upstreams.length() > 0) {
            MonoBox((0 until upstreams.length()).joinToString("\n") { upstreams.optString(it) })
        } else {
            Text("AdGuardHome.yaml не найден — нажмите «Развернуть AGH» выше.", color = Color(0xFF64748B), fontSize = 10.sp)
        }
        var customUps by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = customUps, onValueChange = { customUps = it }, placeholder = { Text("https://dns.quad9.net/dns-query ...", fontSize = 10.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f), maxLines = 2)
            Button(onClick = { if (customUps.isNotBlank()) { onAction("Обновить DNS", MonitorScripts.setAghCustomUpstreamsCmd(customUps)); customUps = "" } }, modifier = Modifier.height(34.dp)) { Text("Задать", fontSize = 10.sp) }
        }
        for (i in 0 until filters.length()) {
            val f = filters.optJSONObject(i) ?: continue
            val fid = f.optInt("id")
            MiniSwitch(f.optString("name", "Фильтр #$fid"), "ID: $fid", f.optBoolean("on")) { v ->
                onAction("Фильтр $fid", MonitorScripts.setAghFilterCmd(fid, v == 1))
            }
        }
    }

    if (qlog.length() > 0) {
        MiniCard("Живой журнал DNS-запросов (нажми +z2k / +Искл.)") {
            for (i in 0 until qlog.length()) {
                val q = qlog.optJSONObject(i) ?: continue
                val blk = q.optBoolean("blk")
                val dom = q.optString("dom")
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${q.optString("t")} • $dom", color = if (blk) Color(0xFFFCA5A5) else Color(0xFFCBD5E1), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${q.optString("ip")} • ${if (blk) "BLOCK" else q.optString("qt")}", color = if (blk) Color(0xFFF87171) else Color(0xFF64748B), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(color = Color(0xFF172554), shape = RoundedCornerShape(4.dp), modifier = Modifier.clickable { onQuickBypassDomain(dom) }) {
                            Text("+z2k", color = Color(0xFF93C5FD), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                        Surface(color = Color(0xFF064E3B), shape = RoundedCornerShape(4.dp), modifier = Modifier.clickable { onQuickWhitelistDomain(dom) }) {
                            Text("+Искл", color = Color(0xFF86EFAC), fontSize = 8.5.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }

    if (errs.length() > 0) {
        MiniCard("Ошибки AdGuard dnsproxy") {
            MonoBox((0 until errs.length()).joinToString("\n") { errs.optString(it) })
        }
    }
}

@Composable
internal fun NatPpePage(
    data: JSONObject?,
    devicesData: JSONObject?,
    onTopicHelp: (String) -> Unit,
    onToggleHelp: (String) -> Unit,
    onQuickHelp: (String, String, String, String, String) -> Unit,
    onAction: (String, String) -> Unit
) {
    val ndms = data?.optJSONObject("ndms") ?: JSONObject()
    val stat = data?.optJSONArray("ip_static") ?: JSONArray()
    val upnp = data?.optJSONArray("upnp_list") ?: JSONArray()

    MiniCard("Аппаратное ускорение (PPE), UPnP и Conntrack", { onTopicHelp("nat_ppe") }) {
        MiniSwitch("Hardware NAT (ppe hardware)", "Аппаратный fast-path коммутатора", ndms.optBoolean("ppe_hw"), { onToggleHelp("ppe_hw") }) { v ->
            onAction("Hardware NAT", MonitorScripts.ndmcSaveCmd(if (v == 1) "ppe hardware" else "no ppe hardware"))
        }
        MiniSwitch("Software PPE (ppe software)", "Ядерный fast-path ускоритель", ndms.optBoolean("ppe_sw"), { onToggleHelp("ppe_sw") }) { v ->
            onAction("Software PPE", MonitorScripts.ndmcSaveCmd(if (v == 1) "ppe software" else "no ppe software"))
        }
        MiniSwitch("UPnP", "Автоматическое открытие портов играми и торрентами", ndms.optBoolean("upnp"), { onToggleHelp("upnp") }) { v ->
            onAction("UPnP", MonitorScripts.ndmcSaveCmd(if (v == 1) "service upnp" else "no service upnp"))
        }
        MiniSwitch("IGMP Proxy (IPTV)", "Пропуск мультикаст-потоков ТВ провайдера", ndms.optBoolean("igmp"), { onToggleHelp("igmp") }) { v ->
            onAction("IGMP", MonitorScripts.ndmcSaveCmd(if (v == 1) "service igmp-proxy" else "no service igmp-proxy"))
        }
        val ctMax = ndms.optInt("ct_max", 32768)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Лимит Conntrack: $ctMax", color = Color(0xFFCBD5E1), fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { onAction("Conntrack 32K", MonitorScripts.ndmcSaveCmd("ip conntrack max-entries 32768")) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("32K", fontSize = 9.sp) }
                OutlinedButton(onClick = { onAction("Conntrack 65K", MonitorScripts.ndmcSaveCmd("ip conntrack max-entries 65536")) }, modifier = Modifier.height(25.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("65K", fontSize = 9.sp) }
            }
        }
    }

    var proto by remember { mutableStateOf("tcp") }
    var extPort by remember { mutableStateOf("") }
    var targetIp by remember { mutableStateOf("") }
    var locPort by remember { mutableStateOf("") }
    val devArr = devicesData?.optJSONArray("devices") ?: JSONArray()

    MiniCard("Проброс портов (ip static) (${stat.length()})", {
        onQuickHelp("Проброс портов (ip static)", "Перенаправляет входящие соединения с WAN на устройство внутри локальной сети.", "Полезно для серверов, NAS, камер и игровых консолей.", "Диапазон можно указать через дефис (напр. 3478-3480).", "")
    }) {
        for (i in 0 until stat.length()) {
            val rule = stat.optString(i)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(rule, color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAction("Удалить правило", MonitorScripts.delStaticPortCmd(rule)) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(15.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = proto == "tcp", onClick = { proto = "tcp" }, label = { Text("TCP", fontSize = 10.sp) })
            FilterChip(selected = proto == "udp", onClick = { proto = "udp" }, label = { Text("UDP", fontSize = 10.sp) })
            OutlinedTextField(value = extPort, onValueChange = { extPort = it }, placeholder = { Text("порт / 8080-8090", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = targetIp, onValueChange = { targetIp = it }, label = { Text("IP устройства", fontSize = 9.sp) }, placeholder = { Text("192.168.1.x", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.weight(1f))
            OutlinedTextField(value = locPort, onValueChange = { locPort = it }, label = { Text("Внутр. порт", fontSize = 9.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), modifier = Modifier.width(88.dp))
            Button(onClick = {
                if (extPort.isNotBlank() && targetIp.isNotBlank()) {
                    onAction("Проброс $proto $extPort", MonitorScripts.addStaticPortCmd(proto, extPort, targetIp, locPort))
                    extPort = ""; locPort = ""
                }
            }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+", fontSize = 14.sp) }
        }
        if (devArr.length() > 0) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until minOf(devArr.length(), 8)) {
                    val d = devArr.optJSONObject(i) ?: continue
                    val ip = d.optString("ip")
                    if (ip.isBlank() || ip == "0.0.0.0") continue
                    val nm = d.optString("name").ifBlank { d.optString("hostname") }.ifBlank { ip }.take(14)
                    AssistChip(onClick = { targetIp = ip }, label = { Text("$nm ($ip)", fontSize = 9.sp) })
                }
            }
        }
    }

    MiniCard("Активные сессии UPnP (${upnp.length()})") {
        if (upnp.length() == 0) Text("Сейчас активных UPnP-портов нет", color = Color(0xFF64748B), fontSize = 11.sp)
        else MonoBox((0 until upnp.length()).joinToString("\n") { upnp.optString(it) })
    }
}
