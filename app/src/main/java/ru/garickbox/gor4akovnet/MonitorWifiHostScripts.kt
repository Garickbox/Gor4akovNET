package ru.garickbox.gor4akovnet

internal object MonitorWifiHostScripts {

    private fun ensureKnownHostSh(cleanMac: String, nameHint: String = ""): String {
        val rawName = nameHint.ifBlank { "host_" + cleanMac.replace(":", "").takeLast(6).lowercase() }
        val safeName = rawName.replace(Regex("[^A-Za-z0-9_. -]"), "").trim().replace(" ", "_").take(32).ifBlank {
            "host_" + cleanMac.replace(":", "").takeLast(6).lowercase()
        }
        val mSq = MonitorShellSupport.sq(cleanMac)
        val nSq = MonitorShellSupport.sq(safeName)
        return """
        _km='$mSq'
        _kn='$nSq'
        _rc_h=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        if ! printf '%s\n' "§_rc_h" | grep -qiE "(^|[[:space:]])host §_km([[:space:]]|§)"; then
          LD_LIBRARY_PATH= /bin/ndmc -c "known host \"§_kn\" §_km" >/dev/null 2>&1 || LD_LIBRARY_PATH= /bin/ndmc -c "known host §_kn §_km" >/dev/null 2>&1 || true
        fi
        """.trimIndent()
    }

    val CMD_WIFI_DEVICES = MonitorShellSupport.sh(
        """
        PFX="/tmp/.gp_§$"
        # ВАЖНО tr -d '\r': ndmc отдаёт CRLF. Без срезания '\r' все regex с '$'
        # (/^interface Bridge0$/, /^interface WifiMaster0$/, /^!/ и т.д.) НЕ матчатся —
        # awk видит "interface Bridge0\r". Из-за этого не парсились band_steering, ft,
        # target-waketime, ap_seen, и секция AP работала наполовину.
        { LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null || true; } | tr -d '\r' > "§{PFX}_rc"
        { LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster0" 2>/dev/null || true; } | tr -d '\r' > "§{PFX}_w0"
        { LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster1" 2>/dev/null || true; } | tr -d '\r' > "§{PFX}_w1"
        { LD_LIBRARY_PATH= /bin/ndmc -c "show ip hotspot" 2>/dev/null || true; } | tr -d '\r' > "§{PFX}_hs"
        : > "§{PFX}_aps"
        ap_list=§(printf 'WifiMaster0/AccessPoint0\nWifiMaster1/AccessPoint0\nWifiMaster0/AccessPoint1\nWifiMaster1/AccessPoint1\n' ; awk '
          /^interface WifiMaster[0-9]+\/AccessPoint[0-9]+/ { cur=§2; in_ap=1; keep=0; next }
          in_ap && /^!/ { if (keep) print cur; in_ap=0; cur=""; next }
          in_ap && /^[[:space:]]*(ssid |description |up§)/ { keep=1 }
          /^[[:space:]]*include WifiMaster[0-9]+\/AccessPoint[0-9]+/ { print §2 }
          END { if (in_ap && keep) print cur }
        ' "§{PFX}_rc" 2>/dev/null)
        ap_list=§(printf '%s\n' "§ap_list" | awk 'NF && !seen[§0]++')
        for ap in §ap_list; do
          out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show interface §ap" 2>/dev/null | tr -d '\r')
          [ -n "§out" ] || continue
          printf '===AP_START %s===\n%s\n===AP_END===\n' "§ap" "§out" >> "§{PFX}_aps"
        done
        cat /tmp/gbox_timer_*.meta 2>/dev/null > "§{PFX}_tm" || : > "§{PFX}_tm"

        wifi_json=§(awk -v PFX="§PFX" '
          ${MonitorShellSupport.AWK_ESC}
          function val_after_colon(line) { sub(/^[^:]+:[[:space:]]*/, "", line); return esc(line) }
          function read_radio(fn,   l, k, v) {
            r_ch="—"; r_bw="—"; r_rate=0; r_temp=""; r_busy=""
            while((getline l < fn) > 0) {
              if(l ~ /^[[:space:]]*channel:/) r_ch=val_after_colon(l)
              else if(l ~ /^[[:space:]]*bandwidth:/) r_bw=val_after_colon(l)
              else if(l ~ /^[[:space:]]*bitrate:/) { v=val_after_colon(l); r_rate=int((v+0)/1000000) }
              else if(l ~ /^[[:space:]]*temperature:/) r_temp=val_after_colon(l)
              else if(l ~ /^[[:space:]]*busy-channels:/) { v=val_after_colon(l); r_busy=(r_busy==""?v:r_busy","v) }
            }
            close(fn)
          }
          BEGIN {
            read_radio(PFX "_w0"); c24=r_ch; b24=r_bw; r24=r_rate; t24=r_temp; bz24=r_busy
            read_radio(PFX "_w1"); c5=r_ch; b5=r_bw; r5=r_rate; t5=r_temp; bz5=r_busy
            nrc=0; naps=0; nrcy=0
            bs=0; gr=0; ft=0; twt1=0; cur=""; guest_up="false"; g24_up="false"; g5_up="false"; s24="Home"; s5="Home"
            while((getline l < (PFX "_rc")) > 0) {
              nrc++
              if(l ~ /^interface Bridge0§/) cur="b0"
              else if(l ~ /^interface Bridge1§/) cur="b1"
              else if(l ~ /^interface WifiMaster0§/) cur="w0"
              else if(l ~ /^interface WifiMaster1§/) cur="w1"
              else if(l ~ /^interface WifiMaster0\/AccessPoint0§/) cur="ap0"
              else if(l ~ /^mws wlan Home§/) cur="mws_home"
              else if(l ~ /^interface WifiMaster[0-9]+\/AccessPoint[0-9]+§/) { cur=""; ap_seen[substr(l,11)]=1 }
              else if(l ~ /^interface /) cur=""
              else if(l ~ /^!/) cur=""
              if((cur=="b0" || cur=="mws_home") && l ~ /^[[:space:]]*band-steering([[:space:]]+enable)?§/) bs=1
              if(cur=="b1" && l ~ /^[[:space:]]*traffic-shape rate /) { split(l, p, " "); gr=p[3]+0 }
              if((cur=="ap0" || cur=="mws_home") && l ~ /^[[:space:]]*ft enable§/) ft=1
              if(cur=="w1" && l ~ /^[[:space:]]*target-waketime§/) twt1=1
              if(l ~ /^[[:space:]]*include WifiMaster/) { split(l, p, " "); inc_seen[p[2]]=1 }
            }
            close(PFX "_rc")
            cur_ap=""; ap_json=""
            nrcy=0
            while((getline l < (PFX "_aps")) > 0) {
              nrcy++
              if(l ~ /^===AP_START /) {
                naps++
                cur_ap=l; sub(/^===AP_START /,"",cur_ap); sub(/===${'$'}/,"",cur_ap)
                idn=""; desc=""; st=""; lk=""; ss=""; grp=""
              } else if(l ~ /^===AP_END===/) {
                isup=(lk=="up" || st=="up")
                dead=((ss=="" && st=="down" && desc=="") || ((cur_ap in inc_seen) && ss=="" && st=="down")) ? 1 : 0
                if(cur_ap=="WifiMaster0/AccessPoint0") { if(isup) g24_up="true"; if(ss!="") s24=ss }
                if(cur_ap=="WifiMaster1/AccessPoint0") { if(isup) g5_up="true"; if(ss!="") s5=ss }
                if((cur_ap=="WifiMaster0/AccessPoint1" || tolower(idn) ~ /guest/) && isup) guest_up="true"
                if(ss!="" || isup || (cur_ap in ap_seen)) {
                  ap_json = ap_json (ap_json==""?"":",") sprintf("{\"id\":\"%s\",\"alias\":\"%s\",\"ssid\":\"%s\",\"desc\":\"%s\",\"grp\":\"%s\",\"up\":%s,\"dead\":%s}", esc(cur_ap), idn, ss, desc, grp, (isup?"true":"false"), (dead?"true":"false"))
                }
                cur_ap=""
              } else if(cur_ap!="") {
                if(l ~ /^[[:space:]]*interface-name:/) idn=val_after_colon(l)
                else if(l ~ /^[[:space:]]*description:/) desc=val_after_colon(l)
                else if(l ~ /^[[:space:]]*state:/) st=val_after_colon(l)
                else if(l ~ /^[[:space:]]*link:/) lk=val_after_colon(l)
                else if(l ~ /^[[:space:]]*ssid:/) ss=val_after_colon(l)
                else if(l ~ /^[[:space:]]*group:/) grp=val_after_colon(l)
              }
            }
            close(PFX "_aps")
            printf "{\"g24_up\":%s,\"g24_ssid\":\"%s\",\"g24_ch\":\"%s\",\"g24_bw\":\"%s\",\"g24_rate\":%d,\"g24_temp\":\"%s\",\"g24_busy\":\"%s\",\"g5_up\":%s,\"g5_ssid\":\"%s\",\"g5_ch\":\"%s\",\"g5_bw\":\"%s\",\"g5_rate\":%d,\"g5_temp\":\"%s\",\"g5_busy\":\"%s\",\"guest_up\":%s,\"guest_rate\":%d,\"band_steer\":%s,\"ft\":%s,\"twt\":%s,\"dbg\":{\"rc_lines\":%d,\"aps_lines\":%d,\"ap_count\":%d},\"aps\":[%s]}",
              g24_up, s24, c24, b24, r24, t24, bz24,
              g5_up, s5, c5, b5, r5, t5, bz5,
              guest_up, gr, (bs?"true":"false"), (ft?"true":"false"), (twt1?"true":"false"),
              nrc, nrcy, naps,
              ap_json
          }' 2>/dev/null)
        [ -n "§wifi_json" ] || wifi_json='{"g24_up":false,"g24_ssid":"—","g24_ch":"—","g24_bw":"—","g24_rate":0,"g24_temp":"","g24_busy":"","g5_up":false,"g5_ssid":"—","g5_ch":"—","g5_bw":"—","g5_rate":0,"g5_temp":"","g5_busy":"","guest_up":false,"guest_rate":0,"band_steer":false,"ft":false,"twt":false,"dbg":{"rc_lines":0,"aps_lines":0,"ap_count":0},"aps":[]}'

        now_s=§(date +%s 2>/dev/null || echo 0)
        devs=§(awk -v PFX="§PFX" -v NOWS="§now_s" '
          ${MonitorShellSupport.AWK_ESC}
          BEGIN {
            while((getline l < (PFX "_rc")) > 0) {
              if(l ~ /^[[:space:]]*host [0-9a-fA-F:]+ /) {
                split(l, hp, " ")
                hm=tolower(hp[2])
                if(hp[3]=="deny") deny[hm]=1
                else if(hp[3]=="conform") conf[hm]=1
                else if(hp[3]=="policy" && hp[4]=="Gor4akovNET") g4[hm]=1
                else if(hp[3]=="traffic-shape" && hp[4]=="rate") sh[hm]=hp[5]+0
                else if(hp[3]=="schedule") sc[hm]=hp[4]
              } else if(l ~ /^ip traffic-shape host [0-9a-fA-F:]+ rate /) {
                split(l, hp, " ")
                sh[tolower(hp[4])] = hp[6]+0
              }
            }
            close(PFX "_rc")
            while((getline l < (PFX "_tm")) > 0) {
              if(l=="") continue
              split(l, tp, "|")
              rem=tp[2]-NOWS
              if(rem>0) tmr[tolower(tp[1])]=int(rem/60)"м ("tp[3]")"
            }
            close(PFX "_tm")
          }
          function flush(){
            if(mac!=""){
              lm=tolower(mac)
              pol=(lm in deny)?"deny":"permit"
              gpol=((lm in g4) || tolower(ifc) ~ /gor4akov/)?"Gor4akovNET":"Default"
              cnf=(lm in conf)?"true":"false"
              shape=(lm in sh)?sh[lm]:0
              sched=(lm in sc)?sc[lm]:""
              tmr_s=(lm in tmr)?tmr[lm]:""
              printf "%s{\"mac\":\"%s\",\"ip\":\"%s\",\"hostname\":\"%s\",\"name\":\"%s\",\"iface\":\"%s\",\"ap\":\"%s\",\"link\":\"%s\",\"speed\":\"%s\",\"rx\":%.0f,\"tx\":%.0f,\"policy\":\"%s\",\"route_pol\":\"%s\",\"prio\":%d,\"conform\":%s,\"shape_kbps\":%d,\"sched\":\"%s\",\"timer\":\"%s\",\"rssi\":\"%s\",\"mode\":\"%s\",\"txrate\":\"%s\"}",
                (cnt?",":""), esc(mac), esc(ip), esc(hn), esc(nm), esc(ifc), esc(ap), esc(lk), esc(sp), rx+0, tx+0, pol, esc(gpol), (pr>0?pr:6), cnf, shape, esc(sched), esc(tmr_s), esc(rssi), esc(md), esc(txr)
              cnt++
            }
            mac="";ip="";hn="";nm="";ifc="";ap="";lk="";sp="";rx=0;tx=0;pr=6;rssi="";md="";txr="";in_i=0;in_ts=0
          }
          /^[[:space:]]*host:/ {flush()}
          /^[[:space:]]*mac:/ {if(mac!="") flush(); mac=§2}
          /^[[:space:]]*ip:/ {ip=§2}
          /^[[:space:]]*hostname:/ {sub(/^[^:]*:[[:space:]]*/,""); hn=§0}
          /^[[:space:]]*name:/ && !in_i {sub(/^[^:]*:[[:space:]]*/,""); nm=§0}
          /^[[:space:]]*interface:/ {in_i=1}
          in_i && /^[[:space:]]*name:/ {sub(/^[^:]*:[[:space:]]*/,""); ifc=§0; in_i=0}
          in_i && /^[[:space:]]*id:/ {sub(/^[^:]*:[[:space:]]*/,""); ifc=§0}
          /^[[:space:]]*priority:/ {pr=§2+0}
          /^[[:space:]]*link:/ {lk=§2}
          /^[[:space:]]*speed:/ {sp=§2}
          /^[[:space:]]*rxbytes:/ {rx=§2}
          /^[[:space:]]*txbytes:/ {tx=§2}
          /^[[:space:]]*rssi:/ {rssi=§2}
          /^[[:space:]]*mode:/ && !in_ts {md=§2}
          /^[[:space:]]*ap:/ {ap=§2}
          /^[[:space:]]*txrate:/ {txr=§2}
          /^[[:space:]]*traffic-shape:/ {in_ts=1}
          END {flush()}' "§{PFX}_hs" 2>/dev/null)
        rm -f "§{PFX}_rc" "§{PFX}_w0" "§{PFX}_w1" "§{PFX}_aps" "§{PFX}_hs" "§{PFX}_tm"
        printf '{"wifi":%s,"devices":[%s]}\n' "§wifi_json" "§devs"
        """
    )

    fun setWifiChannelCmd(masterIdx: Int, ch: String, bw: String): String {
        val m = "WifiMaster$masterIdx"
        val cmds = mutableListOf<String>()
        if (ch.equals("auto", true)) cmds.add("interface $m channel auto") else cmds.add("interface $m channel $ch")
        if (bw.isNotBlank()) cmds.add("interface $m channel width $bw")
        return MonitorShellSupport.ndmcSaveCmd(*cmds.toTypedArray())
    }

    fun setWifiSsidCmd(ssid: String): String {
        val s = GboxScripts.sanitizeSsid(ssid)
        return MonitorShellSupport.sh(
            """
            s='${MonitorShellSupport.sq(s)}'
            rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
            if printf '%s\n' "§rc" | grep -q '^mws wlan Home§'; then
              LD_LIBRARY_PATH= /bin/ndmc -c "mws wlan Home ssid §s"
            fi
            LD_LIBRARY_PATH= /bin/ndmc -c "interface WifiMaster0/AccessPoint0 ssid §s" >/dev/null 2>&1 || true
            LD_LIBRARY_PATH= /bin/ndmc -c "interface WifiMaster1/AccessPoint0 ssid §s" >/dev/null 2>&1 || true
            LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
            echo "OK: SSID обновлён на §s"
            """
        )
    }

    fun setBandSteeringCmd(enable: Boolean): String = MonitorShellSupport.sh(
        """
        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        if printf '%s\n' "§rc" | grep -q '^mws wlan Home§'; then
          LD_LIBRARY_PATH= /bin/ndmc -c "mws wlan Home ${if (enable) "band-steering enable" else "no band-steering enable"}"
        else
          LD_LIBRARY_PATH= /bin/ndmc -c "interface Bridge0 ${if (enable) "band-steering" else "no band-steering"}"
        fi
        LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
        echo "OK: Band Steering ${if (enable) "включён" else "выключен"}"
        """
    )

    fun setWifiFtCmd(enable: Boolean): String = MonitorShellSupport.sh(
        """
        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        if printf '%s\n' "§rc" | grep -q '^mws wlan Home§'; then
          LD_LIBRARY_PATH= /bin/ndmc -c "mws wlan Home ${if (enable) "ft enable" else "no ft enable"}"
        fi
        LD_LIBRARY_PATH= /bin/ndmc -c "interface WifiMaster0/AccessPoint0 ${if (enable) "ft enable" else "no ft enable"}" >/dev/null 2>&1 || true
        LD_LIBRARY_PATH= /bin/ndmc -c "interface WifiMaster1/AccessPoint0 ${if (enable) "ft enable" else "no ft enable"}" >/dev/null 2>&1 || true
        LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
        echo "OK: 802.11r FT ${if (enable) "включён" else "выключен"}"
        """
    )

    fun disableDeadApCmd(apId: String): String = MonitorShellSupport.sh(
        """
        ap='${MonitorShellSupport.sq(apId)}'
        LD_LIBRARY_PATH= /bin/ndmc -c "interface §ap down" >/dev/null 2>&1 || true
        LD_LIBRARY_PATH= /bin/ndmc -c "no interface §ap" >/dev/null 2>&1 || true
        LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
        echo "OK: Слот §ap очищен"
        """
    )

    fun setHostPolicyRouteCmd(mac: String, policyName: String?): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val cleanPol = (policyName ?: "").replace(Regex("[^A-Za-z0-9_-]"), "")
        return MonitorShellSupport.sh(
            ensureKnownHostSh(cleanMac) + "\n" +
            """
            m='${MonitorShellSupport.sq(cleanMac)}'
            pol='${MonitorShellSupport.sq(cleanPol)}'
            if [ -n "§pol" ]; then
              LD_LIBRARY_PATH= /bin/ndmc -c "ip hotspot host §m policy §pol"
            else
              LD_LIBRARY_PATH= /bin/ndmc -c "no ip hotspot host §m policy"
            fi
            LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
            echo "OK: Политика маршрутизации для §m обновлена"
            """.trimIndent()
        )
    }

    fun setHostPriorityCmd(mac: String, prio: Int, conform: Boolean): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val pVal = prio.coerceIn(1, 7)
        val cCmd = if (conform) "ip hotspot host $cleanMac conform" else "ip hotspot host $cleanMac no conform"
        return MonitorShellSupport.sh(
            ensureKnownHostSh(cleanMac) + "\n" +
            """
            LD_LIBRARY_PATH= /bin/ndmc -c 'ip hotspot host $cleanMac permit'
            LD_LIBRARY_PATH= /bin/ndmc -c 'ip hotspot host $cleanMac priority $pVal'
            LD_LIBRARY_PATH= /bin/ndmc -c '$cCmd'
            LD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save'
            echo OK
            """.trimIndent()
        )
    }

    fun setHostShapeCmd(mac: String, kbps: Int): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        return MonitorShellSupport.ndmcSaveCmd(
            if (kbps > 0) "ip traffic-shape host $cleanMac rate ${kbps.coerceIn(64, 1000000)}"
            else "no ip traffic-shape host $cleanMac"
        )
    }

    fun setHostTimerCmd(mac: String, minutes: Int, mode: String): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val key = cleanMac.replace(":", "").lowercase()
        if (minutes <= 0) {
            return MonitorShellSupport.sh(
                """
                pidf="/tmp/gbox_timer_${key}.pid"
                [ -f "§pidf" ] && kill §(cat "§pidf" 2>/dev/null) 2>/dev/null || true
                rm -f "/tmp/gbox_timer_${key}."*
                echo "OK: Таймер для $cleanMac отменён"
                """
            )
        }
        val sec = minutes * 60
        val expireAtCmd = "expr \$(date +%s) + $sec"
        val startNdm = when (mode) {
            "permit" -> "ip hotspot host $cleanMac permit"
            "gor4akov" -> "ip hotspot host $cleanMac policy Gor4akovNET"
            else -> "ip hotspot host $cleanMac permit"
        }
        val endNdm = when (mode) {
            "permit" -> "ip hotspot host $cleanMac deny"
            "gor4akov" -> "no ip hotspot host $cleanMac policy"
            else -> "ip hotspot host $cleanMac deny"
        }
        return MonitorShellSupport.sh(
            """
            pidf="/tmp/gbox_timer_${key}.pid"
            [ -f "§pidf" ] && kill §(cat "§pidf" 2>/dev/null) 2>/dev/null || true
            LD_LIBRARY_PATH= /bin/ndmc -c '${MonitorShellSupport.sq(startNdm)}' >/dev/null 2>&1
            LD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save' >/dev/null 2>&1
            exp=§($expireAtCmd 2>/dev/null || echo 0)
            printf '%s|%s|%s\n' '${MonitorShellSupport.sq(cleanMac)}' "§exp" '${MonitorShellSupport.sq(mode)}' > "/tmp/gbox_timer_${key}.meta"
            (
              sleep $sec
              LD_LIBRARY_PATH= /bin/ndmc -c '${MonitorShellSupport.sq(endNdm)}' >/dev/null 2>&1
              LD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save' >/dev/null 2>&1
              rm -f "/tmp/gbox_timer_${key}.meta" "/tmp/gbox_timer_${key}.pid"
            ) >/dev/null 2>&1 &
            echo $! > "§pidf"
            echo "OK: Таймер на ${minutes} мин для $cleanMac запущен на роутере"
            """
        )
    }

    fun setHostScheduleCmd(mac: String, startHour: Int, stopHour: Int, enable: Boolean): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val schedName = "g4_${cleanMac.replace(":", "").takeLast(6).lowercase()}"
        return if (!enable) {
            MonitorShellSupport.ndmcSaveCmd(
                "no ip hotspot host $cleanMac schedule",
                "no schedule $schedName"
            )
        } else {
            val stH = startHour.coerceIn(0, 23)
            val spH = stopHour.coerceIn(0, 23)
            return MonitorShellSupport.sh(
                ensureKnownHostSh(cleanMac) + "\n" +
                """
                LD_LIBRARY_PATH= /bin/ndmc -c 'schedule $schedName'
                LD_LIBRARY_PATH= /bin/ndmc -c 'schedule $schedName action start 0 $stH *'
                LD_LIBRARY_PATH= /bin/ndmc -c 'schedule $schedName action stop 0 $spH *'
                LD_LIBRARY_PATH= /bin/ndmc -c 'ip hotspot host $cleanMac schedule $schedName'
                LD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save'
                echo OK
                """.trimIndent()
            )
        }
    }

    fun setHostStaticIpCmd(mac: String, ip: String, name: String): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val cleanIp = ip.trim().replace(Regex("[^0-9.]"), "")
        val cleanName = name.trim().replace(Regex("[^A-Za-z0-9_. -]"), "").trim().replace(" ", "_").take(32)
        if (cleanIp.isBlank() && cleanName.isBlank()) return "echo 'ERR: Укажите IP или имя'; exit 1"
        val hostLabel = cleanName.ifBlank { "host_" + cleanMac.replace(":", "").takeLast(6).lowercase() }
        val cmds = mutableListOf("known host $hostLabel $cleanMac")
        if (cleanIp.isNotBlank()) cmds.add("ip dhcp host $hostLabel $cleanMac $cleanIp")
        return MonitorShellSupport.ndmcSaveCmd(*cmds.toTypedArray())
    }

}
