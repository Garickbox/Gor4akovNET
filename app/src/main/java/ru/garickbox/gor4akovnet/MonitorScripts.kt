package ru.garickbox.gor4akovnet

object MonitorScripts {
    private const val PATH_HDR = "export PATH=/opt/sbin:/opt/bin:/sbin:/usr/sbin:/bin:/usr/bin"
    private const val AWK_ESC =
        "function esc(s, i,c,o,b){gsub(/[[:cntrl:]]/,\" \",s);o=\"\";b=sprintf(\"%c\",92);for(i=1;i<=length(s);i++){c=substr(s,i,1);if(c==b)o=o b b;else if(c==\"\\\"\")o=o b \"\\\"\";else o=o c}return o}"

    private fun sh(body: String): String = "$PATH_HDR\n" + body.trimIndent().replace('§', '$')
    private fun sq(s: String): String = s.replace("'", "'\"'\"'")

    fun buildOverviewCmd(forceUpdates: Boolean): String = sh(
        """
        ver_out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show version" 2>/dev/null)
        w0_out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster0" 2>/dev/null)
        w1_out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster1" 2>/dev/null)
        ap20_out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster0/AccessPoint2" 2>/dev/null)
        ap21_out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster1/AccessPoint2" 2>/dev/null)
        model=§(printf '%s\n' "§ver_out" | awk -F': ' '/^[[:space:]]*model:/{print §2; exit}' | tr -d '\r\n"\\')
        [ -n "§model" ] || model="Netcraze Giga"
        ndms=§(printf '%s\n' "§ver_out" | awk -F': ' '/^[[:space:]]*release:/{print §2; exit}' | tr -d '\r\n"\\')
        up_sec=§(cut -d. -f1 /proc/uptime 2>/dev/null | tr -d ' \r\n')
        [ -n "§up_sec" ] || up_sec=0
        load=§(cut -d' ' -f1-3 /proc/loadavg 2>/dev/null | tr -d '\r\n"\\')
        cpu_cnt=§(awk '/^processor/{n++} END{print (n>0?n:2)}' /proc/cpuinfo 2>/dev/null)
        ct_cnt=§(cat /proc/sys/net/netfilter/nf_conntrack_count 2>/dev/null | tr -d ' \r\n')
        [ -n "§ct_cnt" ] || ct_cnt=0
        ct_max=§(cat /proc/sys/net/netfilter/nf_conntrack_max 2>/dev/null | tr -d ' \r\n')
        [ -n "§ct_max" ] || ct_max=0
        mem_json=§(awk '/^MemTotal:/{t=§2} /^MemAvailable:/{a=§2} /^MemFree:/{f=§2} /^Buffers:/{b=§2} /^Cached:/{c=§2} /^SwapTotal:/{st=§2} /^SwapFree:/{sf=§2} END{if(a==0)a=f+b+c; tm=int(t/1024); am=int(a/1024); um=tm-am; if(um<0)um=0; stm=int(st/1024); sum=int((st-sf)/1024); printf "{\"total\":%d,\"used\":%d,\"free\":%d,\"swap_t\":%d,\"swap_u\":%d}", tm, um, am, stm, sum}' /proc/meminfo 2>/dev/null)
        [ -n "§mem_json" ] || mem_json='{"total":0,"used":0,"free":0,"swap_t":0,"swap_u":0}'
        disk_json=§(df -Pm /opt 2>/dev/null | awk 'NR==2{gsub(/%/,"",§5); printf "{\"total\":%d,\"used\":%d,\"avail\":%d,\"pct\":%d}", §2, §3, §4, §5}')
        [ -n "§disk_json" ] || disk_json='{"total":0,"used":0,"avail":0,"pct":0}'
        wan=§(ip -4 addr show eth3 2>/dev/null | awk '/inet /{print §2; exit}' | cut -d/ -f1 | tr -d '\r\n"\\')
        wan_spd=§(cat /sys/class/net/eth3/speed 2>/dev/null | tr -d '\r\n"\\')
        wan_gw=§(ip route show default 2>/dev/null | awk '/default/{print §3; exit}' | tr -d '\r\n"\\')
        ts_ip=§(ip -4 addr show tailscale0 2>/dev/null | awk '/inet /{print §2; exit}' | cut -d/ -f1 | tr -d '\r\n"\\')
        ts_peers=§(tailscale status 2>/dev/null | awk 'NR>1 && /100\./{t++; if(!/offline/)o++} END{printf "%d/%d", o+0, t+0}')
        t24=§(printf '%s\n' "§w0_out" | awk -F': ' '/^[[:space:]]*temperature:/{print §2; exit}' | tr -d ' \r\n"\\')
        t5=§(printf '%s\n' "§w1_out" | awk -F': ' '/^[[:space:]]*temperature:/{print §2; exit}' | tr -d ' \r\n"\\')
        m1070=0
        printf '%s\n%s\n' "§ap20_out" "§ap21_out" | grep -qE '^[[:space:]]*(link|state):[[:space:]]*up' && m1070=1
        ps_w=§(ps w 2>/dev/null)
        ns_l=§(netstat -lnt 2>/dev/null)
        has_ps() { printf '%s\n' "§ps_w" | grep -q "§1" && echo true || echo false; }
        has_pt() { printf '%s\n' "§ns_l" | grep -q ":§1 " && echo true || echo false; }
        z2k_loc=§(tr -d '\r\n"\\' < /opt/zapret2/.z2k-installed-tag 2>/dev/null || echo "-")
        agh_loc=§(opkg list-installed adguardhome-go 2>/dev/null | awk '{print §3}' | tr -d '\r\n"\\')
        [ -n "§agh_loc" ] || agh_loc=§(/opt/sbin/AdGuardHome --version 2>/dev/null | awk '{for(i=1;i<=NF;i++)if(§i~/^v?[0-9]+\./){print §i;exit}}' | tr -d '\r\n"\\')
        ts_loc=§(tailscale version 2>/dev/null | head -n1 | tr -d '\r\n"\\')
        zst=§(curl -fsS -m 4 http://192.168.1.1:8088/cgi-bin/api/status 2>/dev/null || echo '{"ok":false}')
        case "§zst" in '{'*) ;; *) zst='{"ok":false}' ;; esac
        if [ "${if (forceUpdates) "1" else "0"}" = "1" ]; then
          z2u=§(curl -fsS -m 10 -X POST -H 'Referer: http://192.168.1.1:8088/' 'http://192.168.1.1:8088/cgi-bin/api/update/check' 2>/dev/null || echo '{"ok":false}')
        else
          z2u=§(curl -fsS -m 4 'http://192.168.1.1:8088/cgi-bin/api/update/status' 2>/dev/null || echo '{"ok":false}')
        fi
        case "§z2u" in '{'*) ;; *) z2u='{"ok":false}' ;; esac
        printf '{"sys":{"model":"%s","ndms":"%s","up_sec":%s,"load":"%s","cpus":%s,"ct":%s,"ct_max":%s,"mem":%s,"disk":%s,"m1070":%d,"temp24":"%s","temp5":"%s"},"wan":{"ip":"%s","spd":"%s","gw":"%s","ts_ip":"%s","ts_peers":"%s"},"svc":{"nfqws2":%s,"gvsub":%s,"adguard":%s,"tailscale":%s,"tg_tunnel":%s,"http_tunnel":%s,"warp":%s,"z2k_panel":%s,"yadoma_pub":%s,"scheduler":%s,"giga_panel":%s},"ver":{"z2k_local":"%s","agh":"%s","ts":"%s","z2u":%s},"z2k":%s}\n' \
          "§model" "§ndms" "§{up_sec:-0}" "§load" "§{cpu_cnt:-2}" "§{ct_cnt:-0}" "§{ct_max:-0}" "§mem_json" "§disk_json" "§m1070" "§t24" "§t5" \
          "§wan" "§wan_spd" "§wan_gw" "§ts_ip" "§ts_peers" \
          "§(has_ps '/nfqws2')" "§(has_ps 'z2k-detect')" "§(has_pt 3080)" "§(has_ps 'tailscaled')" \
          "§(has_pt 1443)" "§(has_pt 1444)" "§(has_ps 'z2k-warpd')" "§(has_pt 8088)" \
          "§(has_pt 8089)" "§(has_ps 'z2k-scheduler')" "§(has_pt 8090)" \
          "§z2k_loc" "§agh_loc" "§ts_loc" "§z2u" "§zst"
        """
    )

    val CMD_BYPASS_STATE = sh(
        """
        zst=§(curl -fsS -m 5 http://192.168.1.1:8088/cgi-bin/api/status 2>/dev/null || echo '{"ok":false}')
        case "§zst" in '{'*) ;; *) zst='{"ok":false}' ;; esac
        wst=§(curl -fsS -m 5 http://192.168.1.1:8088/cgi-bin/api/warp/status 2>/dev/null || echo '{"ok":false}')
        case "§wst" in '{'*) ;; *) wst='{"ok":false}' ;; esac
        pst=§(curl -fsS -m 4 http://192.168.1.1:8088/cgi-bin/api/policy/status 2>/dev/null || echo '{"ok":false}')
        case "§pst" in '{'*) ;; *) pst='{"ok":false}' ;; esac
        ahl=§(curl -fsS -m 4 http://192.168.1.1:8088/cgi-bin/api/autohostlist-domains 2>/dev/null || echo '{"ok":false,"domains":[]}')
        case "§ahl" in '{'*) ;; *) ahl='{"ok":false,"domains":[]}' ;; esac
        list_to_json() {
          if [ -r "§1" ]; then
            awk '$AWK_ESC BEGIN{printf "["} {gsub(/\r/,"");sub(/#.*/,"");gsub(/^[[:space:]]+|[[:space:]]+§/,""); if(§0!=""){printf "%s\"%s\"", (n++?",":""), esc(§0)}} END{printf "]"}' "§1"
          else
            printf '[]'
          fi
        }
        ex_arr=§(list_to_json /opt/zapret2/lists/extra-domains.txt)
        wl_arr=§(list_to_json /opt/zapret2/lists/whitelist.txt)
        rcnt=0
        if [ -r /opt/zapret2/yadoma-pub/yadoma-rules.json ]; then
          rcnt=§(tr ',' '\n' < /opt/zapret2/yadoma-pub/yadoma-rules.json | wc -l | tr -d ' \r\n')
        fi
        printf '{"z2k":%s,"warp":%s,"policy":%s,"autohost":%s,"rules_cnt":%s,"extra":%s,"whitelist":%s}\n' \
          "§zst" "§wst" "§pst" "§ahl" "§{rcnt:-0}" "§ex_arr" "§wl_arr"
        """
    )

    val CMD_WARP_GAMES = sh(
        """
        g=§(curl -fsS -m 6 http://192.168.1.1:8088/cgi-bin/api/warp/games 2>/dev/null || echo '{"ok":false,"games":[]}')
        case "§g" in '{'*) printf '%s\n' "§g" ;; *) echo '{"ok":false,"games":[]}' ;; esac
        """
    )

    val CMD_DNS_NET = sh(
        """
        agh_up=false
        netstat -lnt 2>/dev/null | grep -q ':3080 ' && agh_up=true
        agh_meta=§(awk '
          $AWK_ESC
          /^  upstream_dns:/ {in_u=1; next}
          in_u && /^    - / {sub(/^    - /,""); ups[++nu]=esc(§0); next}
          in_u && !/^    / {in_u=0}
          /^filters:/ {in_f=1; next}
          in_f && /^[a-z_]+:/ {in_f=0}
          in_f && /^  - enabled:/ {fe=§3}
          in_f && /^    name:/ {sub(/^    name:[[:space:]]*/,""); fn=esc(§0)}
          in_f && /^    id:/ {fid=§2; f[++nf]=sprintf("{\"id\":%d,\"name\":\"%s\",\"on\":%s}", fid+0, fn, (fe=="true"?"true":"false"))}
          /^  protection_enabled:/ {prot=§2}
          /^  filtering_enabled:/ {filt=§2}
          /^  enable_dnssec:/ {dsec=§2}
          /^  cache_enabled:/ {cach=§2}
          /^  cache_optimistic:/ {copt=§2}
          /^  aaaa_disabled:/ {naaaa=§2}
          /^  upstream_mode:/ {umod=esc(§2)}
          END {
            printf "{\"prot\":%s,\"filt\":%s,\"dnssec\":%s,\"cache\":%s,\"cache_opt\":%s,\"no_aaaa\":%s,\"up_mode\":\"%s\",\"upstreams\":[",
              (prot=="true"?"true":"false"), (filt=="true"?"true":"false"), (dsec=="true"?"true":"false"),
              (cach=="true"?"true":"false"), (copt=="true"?"true":"false"), (naaaa=="true"?"true":"false"), umod
            for(i=1;i<=nu;i++) printf "%s\"%s\"", (i>1?",":""), ups[i]
            printf "],\"filters\":["
            for(i=1;i<=nf;i++) printf "%s%s", (i>1?",":""), f[i]
            printf "]}"
          }' /opt/etc/AdGuardHome/AdGuardHome.yaml 2>/dev/null)
        [ -n "§agh_meta" ] || agh_meta='{"prot":false,"filt":false,"dnssec":false,"cache":false,"cache_opt":false,"no_aaaa":false,"up_mode":"","upstreams":[],"filters":[]}'
        qcnt=0
        [ -r /opt/etc/AdGuardHome/data/querylog.json ] && qcnt=§(wc -l < /opt/etc/AdGuardHome/data/querylog.json 2>/dev/null | tr -d ' \r\n')
        [ -n "§qcnt" ] || qcnt=0
        errs=§(grep '\[error\]' /opt/var/log/AdGuardHome.log 2>/dev/null | tail -n 5 | awk '$AWK_ESC BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        [ -n "§errs" ] || errs='[]'
        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null)
        ndms_json=§(printf '%s\n' "§rc" | awk '
          /^ppe hardware§/ {ph=1} /^no ppe hardware§/ {ph=0}
          /^ppe software§/ {ps=1} /^no ppe software§/ {ps=0}
          /^service upnp§/ {up=1} /^no service upnp§/ {up=0}
          /^opkg dns-override§/ {od=1} /^no opkg dns-override§/ {od=0}
          /^service igmp-proxy§/ {ig=1} /^no service igmp-proxy§/ {ig=0}
          /^ip conntrack max-entries / {ctm=§4+0}
          END { printf "{\"ppe_hw\":%s,\"ppe_sw\":%s,\"upnp\":%s,\"dns_ov\":%s,\"igmp\":%s,\"ct_max\":%d}", (ph?"true":"false"), (ps?"true":"false"), (up?"true":"false"), (od?"true":"false"), (ig?"true":"false"), (ctm>0?ctm:32768) }')
        [ -n "§ndms_json" ] || ndms_json='{"ppe_hw":false,"ppe_sw":false,"upnp":false,"dns_ov":false,"igmp":false,"ct_max":32768}'
        ip_static=§(printf '%s\n' "§rc" | awk '$AWK_ESC /^ip static /{a[++n]=esc(§0)} END{printf "["; for(i=1;i<=n;i++)printf "%s\"%s\"", (i>1?",":""), a[i]; printf "]"}')
        [ -n "§ip_static" ] || ip_static='[]'
        upnp_raw=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip upnp" 2>/dev/null)
        upnp_list=§(printf '%s\n' "§upnp_raw" | awk '$AWK_ESC /->/{gsub(/^[[:space:]]+/,""); if(n<15) a[++n]=esc(§0)} END{printf "["; for(i=1;i<=n;i++)printf "%s\"%s\"", (i>1?",":""), a[i]; printf "]"}')
        [ -n "§upnp_list" ] || upnp_list='[]'
        printf '{"agh_up":%s,"agh":%s,"qcnt":%s,"errs":%s,"ndms":%s,"ip_static":%s,"upnp_list":%s}\n' \
          "§agh_up" "§agh_meta" "§{qcnt:-0}" "§errs" "§ndms_json" "§ip_static" "§upnp_list"
        """
    )

    val CMD_WIFI_PS5_DEVICES = sh(
        """
        LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" > /tmp/.gp_rc 2>/dev/null || : > /tmp/.gp_rc
        LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster0" > /tmp/.gp_w0 2>/dev/null || : > /tmp/.gp_w0
        LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster1" > /tmp/.gp_w1 2>/dev/null || : > /tmp/.gp_w1
        LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster0/AccessPoint0" > /tmp/.gp_i24 2>/dev/null || : > /tmp/.gp_i24
        LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster1/AccessPoint0" > /tmp/.gp_i5 2>/dev/null || : > /tmp/.gp_i5
        LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster0/AccessPoint1" > /tmp/.gp_ig 2>/dev/null || : > /tmp/.gp_ig
        LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster0/AccessPoint2" > /tmp/.gp_ia2 2>/dev/null || : > /tmp/.gp_ia2
        LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster1/AccessPoint2" > /tmp/.gp_ia25 2>/dev/null || : > /tmp/.gp_ia25
        LD_LIBRARY_PATH= /bin/ndmc -c "show ip hotspot" > /tmp/.gp_hs 2>/dev/null || : > /tmp/.gp_hs
        LD_LIBRARY_PATH= /bin/ndmc -c "show ip upnp" > /tmp/.gp_upnp 2>/dev/null || : > /tmp/.gp_upnp
        wifi_json=§(awk '
          $AWK_ESC
          function rf(fn,   l, out) { out=""; while((getline l < fn) > 0) out=out l "\n"; close(fn); return out }
          function getv(txt, pat,   n, a, k, v) {
            n=split(txt, a, "\n")
            for(k=1;k<=n;k++) if(a[k] ~ pat) { v=a[k]; sub(/^[^:]+:[[:space:]]*/, "", v); return esc(v) }
            return ""
          }
          function getlist(txt, pat,   n, a, k, v, res) {
            n=split(txt, a, "\n"); res=""
            for(k=1;k<=n;k++) if(a[k] ~ pat) { v=a[k]; sub(/^[^:]+:[[:space:]]*/, "", v); res=(res==""?v:res "," v) }
            return esc(res)
          }
          BEGIN {
            rc=rf("/tmp/.gp_rc")
            w0=rf("/tmp/.gp_w0"); w1=rf("/tmp/.gp_w1")
            i24=rf("/tmp/.gp_i24"); i5=rf("/tmp/.gp_i5"); ig=rf("/tmp/.gp_ig"); ia2=rf("/tmp/.gp_ia2"); ia25=rf("/tmp/.gp_ia25")
            g24u=(getv(i24, "^[[:space:]]*link:") == "up" || getv(i24, "^[[:space:]]*state:") == "up")
            g5u=(getv(i5, "^[[:space:]]*link:") == "up" || getv(i5, "^[[:space:]]*state:") == "up")
            ggu=(getv(ig, "^[[:space:]]*link:") == "up" || getv(ig, "^[[:space:]]*state:") == "up")
            a2u=(getv(ia2, "^[[:space:]]*link:") == "up" || getv(ia25, "^[[:space:]]*link:") == "up" || getv(ia2, "^[[:space:]]*state:") == "up" || getv(ia25, "^[[:space:]]*state:") == "up")
            bs=0; gr=0; ft=0; txb0=0; bf0=0; twt1=0; cur=""
            n=split(rc, lines, "\n")
            for(i=1;i<=n;i++) {
              l=lines[i]
              if(l ~ /^interface Bridge0§/) cur="b0"
              else if(l ~ /^interface Bridge1§/) cur="b1"
              else if(l ~ /^interface WifiMaster0§/) cur="w0"
              else if(l ~ /^interface WifiMaster1§/) cur="w1"
              else if(l ~ /^interface WifiMaster0\/AccessPoint0§/) cur="ap0"
              else if(l ~ /^!/) cur=""
              if(cur=="b0" && l ~ /^[[:space:]]*band-steering§/) bs=1
              if(cur=="b1" && l ~ /^[[:space:]]*traffic-shape rate /) { split(l, p, " "); gr=p[3]+0 }
              if(cur=="ap0" && l ~ /^[[:space:]]*ft enable§/) ft=1
              if(cur=="w0" && l ~ /^[[:space:]]*tx-burst§/) txb0=1
              if(cur=="w0" && l ~ /^[[:space:]]*beamforming /) bf0=1
              if(cur=="w1" && l ~ /^[[:space:]]*target-waketime§/) twt1=1
            }
            s24=getv(i24,"ssid:"); if(s24=="") s24="Gorchakov"
            s5=getv(i5,"ssid:"); if(s5=="") s5="Gorchakov"
            c24=getv(w0,"channel:"); if(c24=="") c24=getv(i24,"channel:"); if(c24=="") c24="—"
            c5=getv(w1,"channel:"); if(c5=="") c5=getv(i5,"channel:"); if(c5=="") c5="—"
            b24=getv(w0,"bandwidth:"); if(b24=="") b24="—"
            b5=getv(w1,"bandwidth:"); if(b5=="") b5="—"
            r24=int((getv(w0,"bitrate:")+0)/1000000)
            r5=int((getv(w1,"bitrate:")+0)/1000000)
            t24=getv(w0,"temperature:"); t5=getv(w1,"temperature:")
            bz24=getlist(w0,"busy-channels:"); bz5=getlist(w1,"busy-channels:")
            printf "{\"g24_up\":%s,\"g24_ssid\":\"%s\",\"g24_ch\":\"%s\",\"g24_bw\":\"%s\",\"g24_rate\":%d,\"g24_temp\":\"%s\",\"g24_busy\":\"%s\",\"g5_up\":%s,\"g5_ssid\":\"%s\",\"g5_ch\":\"%s\",\"g5_bw\":\"%s\",\"g5_rate\":%d,\"g5_temp\":\"%s\",\"g5_busy\":\"%s\",\"guest_up\":%s,\"guest_rate\":%d,\"band_steer\":%s,\"ft\":%s,\"txb\":%s,\"bf\":%s,\"twt\":%s,\"m1070\":%s}",
              (g24u?"true":"false"), s24, c24, b24, r24, t24, bz24,
              (g5u?"true":"false"), s5, c5, b5, r5, t5, bz5,
              (ggu?"true":"false"), gr, (bs?"true":"false"), (ft?"true":"false"), (txb0?"true":"false"), (bf0?"true":"false"), (twt1?"true":"false"), (a2u?"true":"false")
          }')
        [ -n "§wifi_json" ] || wifi_json='{"g24_up":false,"g24_ssid":"Gorchakov","g24_ch":"—","g24_bw":"—","g24_rate":0,"g24_temp":"","g24_busy":"","g5_up":false,"g5_ssid":"Gorchakov","g5_ch":"—","g5_bw":"—","g5_rate":0,"g5_temp":"","g5_busy":"","guest_up":false,"guest_rate":0,"band_steer":false,"ft":false,"txb":false,"bf":false,"twt":false,"m1070":false}'
        deny_macs=§(awk '/^[[:space:]]*host [0-9a-f:]+ deny/{print §2}' /tmp/.gp_rc | tr '\n' ' ')
        conf_macs=§(awk '/^[[:space:]]*host [0-9a-f:]+ conform/{print §2}' /tmp/.gp_rc | tr '\n' ' ')
        devs=§(DM="§deny_macs" CM="§conf_macs" awk '
          $AWK_ESC
          function flush(){
            if(mac!=""){
              pol=(index(" " ENVIRON["DM"] " ", " " mac " ")>0)?"deny":"permit"
              cnf=(index(" " ENVIRON["CM"] " ", " " mac " ")>0)?"true":"false"
              printf "%s{\"mac\":\"%s\",\"ip\":\"%s\",\"hostname\":\"%s\",\"name\":\"%s\",\"iface\":\"%s\",\"ap\":\"%s\",\"link\":\"%s\",\"speed\":\"%s\",\"rx\":%.0f,\"tx\":%.0f,\"policy\":\"%s\",\"prio\":%d,\"conform\":%s,\"rssi\":\"%s\",\"mode\":\"%s\",\"txrate\":\"%s\"}",
                (cnt?",":""), esc(mac), esc(ip), esc(hn), esc(nm), esc(ifc), esc(ap), esc(lk), esc(sp), rx+0, tx+0, pol, (pr>0?pr:6), cnf, esc(rssi), esc(md), esc(txr)
              cnt++
            }
            mac="";ip="";hn="";nm="";ifc="";ap="";lk="";sp="";rx=0;tx=0;pr=6;rssi="";md="";txr="";in_i=0;in_ts=0
          }
          /^[[:space:]]*host:/ {flush()}
          /^[[:space:]]*mac:/ {mac=§2}
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
          END {flush()}' /tmp/.gp_hs)
        ps5_static=§(awk '$AWK_ESC /^ip static .*192\.168\.1\.69/{a[++n]=esc(§0)} END{printf "["; for(i=1;i<=n;i++)printf "%s\"%s\"", (i>1?",":""), a[i]; printf "]"}' /tmp/.gp_rc)
        [ -n "§ps5_static" ] || ps5_static='[]'
        ps5_upnp=§(awk '/192\.168\.1\.69/{n++} END{print n+0}' /tmp/.gp_upnp 2>/dev/null)
        rm -f /tmp/.gp_rc /tmp/.gp_w0 /tmp/.gp_w1 /tmp/.gp_i24 /tmp/.gp_i5 /tmp/.gp_ig /tmp/.gp_ia2 /tmp/.gp_ia25 /tmp/.gp_hs /tmp/.gp_upnp
        printf '{"wifi":%s,"ps5_upnp":%s,"ps5_static":%s,"devices":[%s]}\n' \
          "§wifi_json" "§{ps5_upnp:-0}" "§ps5_static" "§devs"
        """
    )

    val CMD_WAN_TAILSCALE = sh(
        """
        wan=§(ip -4 addr show eth3 2>/dev/null | awk '/inet /{print §2; exit}' | cut -d/ -f1 | tr -d '\r\n"\\')
        gw=§(ip route show default 2>/dev/null | awk '/default/{print §3; exit}' | tr -d '\r\n"\\')
        spd=§(cat /sys/class/net/eth3/speed 2>/dev/null | tr -d '\r\n"\\')
        rx=§(cat /sys/class/net/eth3/statistics/rx_bytes 2>/dev/null || echo 0)
        tx=§(cat /sys/class/net/eth3/statistics/tx_bytes 2>/dev/null || echo 0)
        dns=§(awk '/^nameserver/{a=a (a?", ":"") §2} END{print a}' /etc/resolv.conf 2>/dev/null | tr -d '\r\n"\\')
        eth_info=§(LD_LIBRARY_PATH= /bin/ndmc -c "show interface GigabitEthernet1" 2>/dev/null | awk '$AWK_ESC /link:|speed:|duplex:/{gsub(/^[[:space:]]+/,""); o=(o?o " • ":"") §0} END{print esc(o)}')
        ts_ip=§(ip -4 addr show tailscale0 2>/dev/null | awk '/inet /{print §2; exit}' | cut -d/ -f1 | tr -d '\r\n"\\')
        ts_ver=§(tailscale version 2>/dev/null | head -n1 | tr -d '\r\n"\\')
        tp=§(tailscale debug prefs 2>/dev/null)
        ts_exit=false; printf '%s\n' "§tp" | grep -q '"0.0.0.0/0"' && ts_exit=true
        ts_lan=false; printf '%s\n' "§tp" | grep -q '"192.168.1.0/24"' && ts_lan=true
        ts_dns=false; printf '%s\n' "§tp" | grep -q '"CorpDNS":[[:space:]]*true' && ts_dns=true
        ts_shd=false; printf '%s\n' "§tp" | grep -q '"ShieldsUp":[[:space:]]*true' && ts_shd=true
        peers=§(tailscale status 2>/dev/null | awk '$AWK_ESC /^100\./{ip=§1;nm=§2;os=§4;st="";for(i=5;i<=NF;i++)st=(st==""?§i:st " " §i);on=(st ~ /offline/)?"false":"true";printf "%s{\"ip\":\"%s\",\"name\":\"%s\",\"os\":\"%s\",\"on\":%s,\"info\":\"%s\"}",(n++?",":""),esc(ip),esc(nm),esc(os),on,esc(st)}')
        printf '{"wan":"%s","gw":"%s","spd":"%s","rx":%.0f,"tx":%.0f,"dns":"%s","eth":"%s","ts_ip":"%s","ts_ver":"%s","ts_exit":%s,"ts_lan":%s,"ts_dns":%s,"ts_shd":%s,"peers":[%s]}\n' \
          "§wan" "§gw" "§spd" "§{rx:-0}" "§{tx:-0}" "§dns" "§eth_info" "§ts_ip" "§ts_ver" "§ts_exit" "§ts_lan" "§ts_dns" "§ts_shd" "§peers"
        """
    )

    val CMD_SERVICES = sh(
        """
        ps_w=§(ps w 2>/dev/null)
        ns_l=§(netstat -lnt 2>/dev/null)
        chk_s() {
          inst=false; [ -x "/opt/etc/init.d/§1" ] && inst=true
          up=false
          if [ -n "§2" ] && printf '%s\n' "§ps_w" | grep -q "§2"; then up=true; fi
          if [ -n "§3" ] && printf '%s\n' "§ns_l" | grep -q ":§3 "; then up=true; fi
          printf '{"id":"%s","inst":%s,"up":%s}' "§1" "§inst" "§up"
        }
        svcs="[§(chk_s S99zapret2 '/nfqws2' ''),§(chk_s S99adguardhome '' 3080),§(chk_s S06tailscaled 'tailscaled' ''),§(chk_s S98tg-tunnel '' 1443),§(chk_s S51z2k-warp 'z2k-warpd' ''),§(chk_s S98yadoma-rules '' 8089),§(chk_s S96z2k-webpanel '' 8088),§(chk_s S99z2k-scheduler 'z2k-scheduler' ''),§(chk_s S96z2k-rt-proxy 'z2k-rt-proxy' ''),§(chk_s S97z2k-http-tunnel '' 1444),§(chk_s S98giga-panel '' 8090),§(chk_s S10cron 'crond' '')]"
        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null)
        cifs=false; printf '%s\n' "§rc" | grep -q '^service cifs§' && cifs=true
        webdav=false; printf '%s\n' "§rc" | awk '/^ip http webdav§/{in_w=1;next} /^!/{in_w=0} in_w && /^[[:space:]]*enable§/{f=1} END{exit !f}' && webdav=true
        zram=false; printf '%s\n' "§rc" | grep -q '^[[:space:]]*zram§' && zram=true
        usb2=false; printf '%s\n' "§rc" | grep -q 'dev\.usb\.force_usb2 1' && usb2=true
        opkg_cnt=§(opkg list-installed 2>/dev/null | wc -l | tr -d ' \r\n')
        disks=§(df -Pm 2>/dev/null | awk '$AWK_ESC NR>1 && (§6=="/opt" || §6 ~ /^\/tmp\/mnt\//){gsub(/%/,"",§5); printf "%s{\"mnt\":\"%s\",\"tot\":%d,\"used\":%d,\"avail\":%d,\"pct\":%d}", (n++?",":""), esc(§6), §2, §3, §4, §5}')
        printf '{"svcs":%s,"ndms":{"cifs":%s,"webdav":%s,"zram":%s,"usb2":%s},"opkg_cnt":%s,"disks":[%s]}\n' \
          "§svcs" "§cifs" "§webdav" "§zram" "§usb2" "§{opkg_cnt:-0}" "§disks"
        """
    )

    val CMD_REAPPLY_PS5_NAT = "echo OK-PS5-removed"

    val CMD_WOT_PING = sh(
        """
        echo "=== PS5 (LAN 192.168.1.69) ==="
        ping -c 3 -W 1 192.168.1.69 2>&1 | tail -n 3
        echo "=== Сервера WoT Console EU и DNS ==="
        for h in login.p1.worldoftanks.eu 92.223.24.1 1.1.1.1; do
          r=§(ping -c 4 -W 2 "§h" 2>/dev/null | awk -F'/' '/round-trip|rtt/{print §5 " ms"}')
          echo "§h: §{r:-таймаут}"
        done
        echo "=== PPE / UPnP / Статика / QoS ==="
        LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | grep -E '^(ppe|service upnp|ip static .*192\.168\.1\.69)|b4:0a:d8:37:d1:a4'
        """
    )

    val CMD_BENCH_WAN = sh(
        """
        echo "=== Пинг с роутера ==="
        for h in 1.1.1.1 8.8.8.8 youtube.com; do
          r=§(ping -c 3 -W 2 "§h" 2>/dev/null | awk -F'/' '/round-trip|rtt/{print §5 " ms"}')
          echo "§h: §{r:-таймаут}"
        done
        echo "=== Замер скорости (10 МБ с Cloudflare) ==="
        curl -fsSL -m 20 -o /dev/null -w '%{speed_download} %{time_total}\n' 'https://speed.cloudflare.com/__down?bytes=10000000' 2>&1 | \
          awk '{if(§1+0>0) printf "Скорость: %.2f Мбит/с (%.2f МБ/с) за %sс\n", (§1*8)/1000000, §1/1048576, §2; else print §0}'
        """
    )

    val CMD_PROBE_SITES = sh(
        """
        for u in https://www.youtube.com https://discord.com https://www.instagram.com https://chatgpt.com https://web.telegram.org https://rutracker.org; do
          res=§(curl -IsS --max-time 4 -o /dev/null -w 'HTTP %{http_code} • %{time_total}s' "§u" 2>&1)
          echo "§u -> §res"
        done
        """
    )

    val CMD_PROBE_DNS = sh(
        """
        for d in google.com youtube.com playstation.net github.com; do
          t0=§(cut -d' ' -f1 /proc/uptime 2>/dev/null || echo 0)
          ip=§(nslookup "§d" 127.0.0.1 2>/dev/null | awk '/^Address/{a=§NF} END{print a}')
          t1=§(cut -d' ' -f1 /proc/uptime 2>/dev/null || echo 0)
          ms=§(awk -v a="§t0" -v b="§t1" 'BEGIN{printf "%.0f ms", (b-a)*1000}')
          echo "§d -> §{ip:-ошибка резолва} (§ms)"
        done
        """
    )

    fun z2kApiJob(ep: String, postData: String? = "", maxWaitSec: Int = 45): String {
        val url = "http://192.168.1.1:8088/cgi-bin/api/${sq(ep)}"
        val req = if (postData != null) {
            "curl -sS -m 12 -X POST -H 'Referer: http://192.168.1.1:8088/' -d '${sq(postData)}' '$url'"
        } else {
            "curl -sS -m 12 '$url'"
        }
        return sh(
            """
            res=§($req 2>&1)
            jid=§(printf '%s' "§res" | sed -n 's/.*"job":"\([0-9]*\)".*/\1/p')
            if [ -n "§jid" ]; then
              i=0
              while [ §i -lt $maxWaitSec ]; do
                jr=§(curl -sS -m 5 "http://192.168.1.1:8088/cgi-bin/api/job?id=§jid" 2>/dev/null || break)
                case "§jr" in
                  *'"done":true'*)
                    printf '%s' "§jr" | awk '
                      /"exit":/{ex=§0; sub(/.*"exit":/,"",ex); sub(/,.*/,"",ex)}
                      {
                        s=§0; sub(/.*"log":"/,"",s); sub(/"\}[[:space:]]*§/,"",s)
                        gsub(/\\r\\n/,"\n",s); gsub(/\\n/,"\n",s); gsub(/\\"/,"\"",s); gsub(/\\\\/,"\\",s)
                        print (ex=="0"||ex=="null"?"OK:\n":"ERR (exit " ex "):\n") s
                      }'
                    exit 0 ;;
                esac
                sleep 1
                i=§((i+1))
              done
              echo "Задача #§jid запущена на роутере (выполняется в фоне)."
            else
              printf '%s\n' "§res"
            fi
            """
        )
    }

    fun toggleZ2k(ep: String, v: Int): String = z2kApiJob("toggle/$ep", "value=$v", 30)
    fun tunnelSetCmd(enable: Boolean): String = z2kApiJob(if (enable) "tunnel/enable" else "tunnel/disable", "", 30)
    fun warpInstallCmd(): String = z2kApiJob("warp/install", "", 75)
    fun warpRemoveCmd(): String = z2kApiJob("warp/remove", "", 40)
    fun warpReregisterCmd(): String = z2kApiJob("warp/reregister", "", 45)
    fun warpTransportCmd(mode: String): String = z2kApiJob("warp/transport", "value=${sq(mode)}", 35)
    fun warpWdttCmd(v: Int): String = z2kApiJob("warp/wdtt", "value=$v", 15)
    fun warpLicenseCmd(key: String): String = z2kApiJob("warp/license", "key=${sq(key.trim())}", 30)
    fun warpGameToggleCmd(name: String, v: Int): String = z2kApiJob("warp/games/toggle", "name=${sq(name)}&value=$v", 15)
    fun z2kUpdateApplyCmd(): String = z2kApiJob("update/apply", "", 75)
    fun z2kScheduleHourCmd(h: String): String = z2kApiJob("update/schedule", "hour=${sq(h)}", 10)
    fun strategyPickCmd(domain: String, mode: String): String = z2kApiJob("strategy/pick", "domain=${sq(domain.trim())}&mode=${sq(mode)}", 65)
    fun setPolicyCmd(name: String, exclude: Int): String = z2kApiJob("policy/save", "name=${sq(name.trim())}&exclude=$exclude", 30)

    fun addExtraDomain(d: String): String = sh(
        """
        res=§(curl -sS -m 8 -X POST -H 'Referer: http://192.168.1.1:8088/' -d 'domain=${sq(d)}' 'http://192.168.1.1:8088/cgi-bin/api/extra-domains/add' 2>&1)
        case "§res" in *'"ok":true'*) ;; *) printf '%s\n' '${sq(d)}' >> /opt/zapret2/lists/extra-domains.txt ;; esac
        /opt/bin/yadoma-export-rules.sh >/dev/null 2>&1 || true
        echo "OK: Добавлен ${sq(d)} (правила YaDoma обновлены)"
        """
    )

    fun delListDomain(path: String, d: String): String = sh(
        """
        p='${sq(path)}'; t='${sq(d)}'
        [ -f "§p" ] && cp -f "§p" "§p.bak"
        awk -v target="§t" '{gsub(/\r/,""); x=§0; gsub(/^[[:space:]]+|[[:space:]]+§/,"",x); if(x!=target) print §0}' "§p" > "§p.tmp" && mv -f "§p.tmp" "§p"
        /opt/bin/yadoma-export-rules.sh >/dev/null 2>&1 || true
        /opt/etc/init.d/S99zapret2 reload >/dev/null 2>&1 || /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
        echo "OK: Удалён §t"
        """
    )

    fun addWhitelistDomain(d: String): String = sh(
        """
        res=§(curl -sS -m 8 -X POST -H 'Referer: http://192.168.1.1:8088/' -d 'domain=${sq(d)}' 'http://192.168.1.1:8088/cgi-bin/api/whitelist/add' 2>&1)
        case "§res" in
          *'"ok":true'*) ;;
          *)
            touch /opt/zapret2/lists/whitelist.txt
            grep -qxF '${sq(d)}' /opt/zapret2/lists/whitelist.txt || printf '%s\n' '${sq(d)}' >> /opt/zapret2/lists/whitelist.txt
            /opt/etc/init.d/S99zapret2 reload >/dev/null 2>&1 || true
            ;;
        esac
        /opt/bin/yadoma-export-rules.sh >/dev/null 2>&1 || true
        echo "OK: ${sq(d)} в белом списке"
        """
    )

    fun delAutohostDomain(d: String): String = z2kApiJob("autohostlist-domains/delete", "domain=${sq(d)}", 15)

    fun setAghKeyCmd(key: String, enable: Boolean): String {
        val k = sq(key)
        val v = if (enable) "true" else "false"
        return sh(
            """
            cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak
            sed -i 's/^  $k: .*/  $k: $v/' /opt/etc/AdGuardHome/AdGuardHome.yaml
            /opt/etc/init.d/S99adguardhome restart 2>&1
            """
        )
    }

    fun setAghFilterCmd(id: Int, enable: Boolean): String = sh(
        """
        cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak
        awk -v fid="$id" -v val="${if (enable) "true" else "false"}" '
          function flush_item(   j) { for(j=1;j<=k;j++) print arr[j]; k=0 }
          /^filters:/ { print; in_f=1; k=0; next }
          in_f && /^[a-z_]+:/ { flush_item(); in_f=0; print; next }
          in_f && /^  - enabled:/ { flush_item(); k=1; arr[1]=§0; next }
          in_f {
            arr[++k]=§0
            if (/^    id:[[:space:]]*/ && §2 == fid) {
              arr[1] = "  - enabled: " val
              flush_item()
            }
            next
          }
          { print }
          END { flush_item() }
        ' /opt/etc/AdGuardHome/AdGuardHome.yaml > /tmp/agh_f.yaml && mv -f /tmp/agh_f.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml
        /opt/etc/init.d/S99adguardhome restart 2>&1
        """
    )

    fun setAghDnsProfile(profile: String): String {
        val upstreams = when (profile) {
            "doh_stable" -> "    - https://dns.quad9.net/dns-query\n    - https://cloudflare-dns.com/dns-query\n    - https://dns.adguard-dns.com/dns-query"
            "fast_udp" -> "    - 1.1.1.1\n    - 77.88.8.8\n    - 8.8.8.8"
            else -> "    - https://dns.adguard-dns.com/dns-query\n    - https://cloudflare-dns.com/dns-query"
        }
        return setAghCustomUpstreamsCmd(upstreams)
    }

    fun setAghCustomUpstreamsCmd(rawLines: String): String {
        val clean = rawLines.lines()
            .map { it.trim().removePrefix("- ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { "    - $it" }
        return sh(
            """
            cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak
            REPS='${sq(clean)}' awk '
              /^  upstream_dns:/ {print; printf "%s\n", ENVIRON["REPS"]; skip=1; next}
              skip && /^    - / {next}
              skip {skip=0}
              {print}
            ' /opt/etc/AdGuardHome/AdGuardHome.yaml > /tmp/agh_new.yaml && mv -f /tmp/agh_new.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml
            /opt/etc/init.d/S99adguardhome restart 2>&1
            """
        )
    }

    fun ndmcSaveCmd(vararg cmds: String): String = sh(
        cmds.joinToString("\n") { "LD_LIBRARY_PATH= /bin/ndmc -c '${sq(it)}'" } +
            "\nLD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save'\necho OK"
    )

    fun addStaticPortCmd(proto: String, extPort: String, localIp: String, localPort: String): String {
        val pr = if (proto.lowercase() == "udp") "udp" else "tcp"
        val ep = extPort.trim().replace("-", " through ")
        val lp = localPort.trim()
        val rule = if (lp.isNotBlank() && lp != extPort.trim() && !ep.contains("through")) {
            "ip static $pr ISP $ep ${localIp.trim()} $lp"
        } else {
            "ip static $pr ISP $ep ${localIp.trim()}"
        }
        return ndmcSaveCmd(rule)
    }

    fun delStaticPortCmd(fullRule: String): String = ndmcSaveCmd("no ${fullRule.trim()}")

    fun setWifiChannelCmd(masterIdx: Int, ch: String, bw: String): String {
        val m = "WifiMaster$masterIdx"
        val cmds = mutableListOf<String>()
        if (ch.equals("auto", true)) cmds.add("interface $m channel auto") else cmds.add("interface $m channel $ch")
        if (bw.isNotBlank()) cmds.add("interface $m channel width $bw")
        return ndmcSaveCmd(*cmds.toTypedArray())
    }

    fun setWifiSsidCmd(ssid: String): String {
        val s = ssid.trim()
        return ndmcSaveCmd(
            "interface WifiMaster0/AccessPoint0 ssid $s",
            "interface WifiMaster1/AccessPoint0 ssid $s"
        )
    }

    fun setHostPriorityCmd(mac: String, prio: Int, conform: Boolean): String = ndmcSaveCmd(
        "ip hotspot host $mac permit",
        "ip hotspot host $mac priority ${prio.coerceIn(1, 7)}",
        if (conform) "ip hotspot host $mac conform" else "ip hotspot host $mac no conform"
    )

    fun setHostShapeCmd(mac: String, kbps: Int): String = ndmcSaveCmd(
        if (kbps > 0) "ip hotspot host $mac traffic-shape rate $kbps" else "ip hotspot host $mac no traffic-shape"
    )

    fun setTailscalePrefsCmd(exitNode: Boolean, subnetLan: Boolean, acceptDns: Boolean, shieldsUp: Boolean): String = "OK: Tailscale disabled"

    fun svcCmd(id: String, action: String): String = sh(
        "/opt/etc/init.d/${sq(id)} ${sq(action)} 2>&1"
    )

    val CMD_OPKG_CHECK = sh(
        """
        export TMPDIR=/opt/tmp; mkdir -p /opt/tmp
        opkg update 2>&1 | tail -n 12
        echo "=== Доступные обновления ==="
        up=§(opkg list-upgradable 2>/dev/null)
        [ -n "§up" ] && printf '%s\n' "§up" || echo "Все пакеты Entware актуальны."
        """
    )

    val CMD_OPKG_UPGRADE_ALL = sh(
        """
        export TMPDIR=/opt/tmp; mkdir -p /opt/tmp
        opkg update 2>&1 | tail -n 5
        opkg upgrade 2>&1
        """
    )

    fun opkgSearchCmd(q: String): String = sh(
        "opkg find '*${sq(q.trim())}*' 2>&1 | head -n 40"
    )

    fun opkgInstallCmd(pkg: String): String = sh(
        """
        export TMPDIR=/opt/tmp; mkdir -p /opt/tmp
        opkg install '${sq(pkg.trim())}' 2>&1
        """
    )

    fun opkgRemoveCmd(pkg: String): String = sh(
        "opkg remove '${sq(pkg.trim())}' 2>&1"
    )

    fun fetchLog(src: String): String = when (src) {
        "agh" -> sh("tail -n 90 /opt/var/log/AdGuardHome.log 2>/dev/null || echo 'Лог пуст'")
        "sched" -> sh("tail -n 90 /opt/var/log/z2k-scheduler.log 2>/dev/null || echo 'Лог пуст'")
        "au" -> sh("tail -n 90 /opt/var/log/z2k-auto-update.log 2>/dev/null || echo 'Лог пуст'")
        "tg" -> sh("tail -n 90 /tmp/z2k-log/tg-tunnel.log 2>/dev/null || echo 'Лог пуст'")
        "http" -> sh("tail -n 90 /tmp/z2k-log/z2k-http-tunnel.log 2>/dev/null || echo 'Лог пуст'")
        "warp" -> sh("tail -n 90 /tmp/z2k-log/z2k-warp.log /tmp/z2k-warp.log 2>/dev/null || echo 'Лог WARP пуст'")
        "circ" -> sh("tail -n 90 /opt/zapret2/extra_strats/cache/autocircular/debug.log 2>/dev/null || echo 'Лог autocircular пуст'")
        else -> sh("dmesg 2>/dev/null | tail -n 70")
    }

    fun wolCmd(mac: String): String = sh(
        """
        m='${sq(mac)}'
        if LD_LIBRARY_PATH= /bin/ndmc -c "ip hotspot wake §m" | grep -qiE 'error|such command'; then
          LC_ALL=C awk -v m="§m" 'BEGIN{m=tolower(m);gsub(/:/,"",m);h="0123456789abcdef";p="";for(i=0;i<6;i++)p=p sprintf("%c",255);for(i=1;i<=16;i++)for(j=1;j<=12;j+=2){v=(index(h,substr(m,j,1))-1)*16+(index(h,substr(m,j+1,1))-1);p=p sprintf("%c",v)}printf "%s",p}' | nc -u -w1 192.168.1.255 9
        fi
        echo "OK: WoL отправлен на §m"
        """
    )

    fun listDirCmd(dir: String): String = sh(
        """
        d='${sq(dir)}'
        [ -d "§d" ] || { printf '{"path":"%s","err":"Папка не найдена","entries":[]}\n' "§d"; exit 0; }
        real=§(cd "§d" 2>/dev/null && pwd)
        [ -n "§real" ] || real="§d"
        items=§(ls -la "§real" 2>/dev/null | awk '$AWK_ESC NR>1{perm=§1;sz=§5+0;nm="";for(i=9;i<=NF;i++)nm=(nm==""?§i:nm " " §i);if(nm==""||nm=="."||nm=="..")next;isd=(substr(perm,1,1)=="d")?"true":"false";isl=(substr(perm,1,1)=="l")?"true":"false";sub(/ -> .*§/,"",nm);printf "%s{\"name\":\"%s\",\"dir\":%s,\"link\":%s,\"size\":%d,\"perm\":\"%s\"}",(n++?",":""),esc(nm),isd,isl,sz,esc(perm)}')
        rp=§(printf '%s' "§real" | awk '$AWK_ESC {print esc(§0)}')
        printf '{"path":"%s","entries":[%s]}\n' "§rp" "§items"
        """
    )

    fun readFileCmd(path: String): String = sh(
        """
        p='${sq(path)}'
        [ -r "§p" ] || { echo '{"err":"Нет доступа или файл не найден"}'; exit 0; }
        sz=§(wc -c < "§p" 2>/dev/null | tr -d ' \r\n')
        trunc=false
        [ "§{sz:-0}" -gt 32768 ] && trunc=true
        b64=§(head -c 32768 "§p" 2>/dev/null | base64 2>/dev/null | tr -d '\r\n')
        printf '{"size":%s,"trunc":%s,"b64":"%s"}\n' "§{sz:-0}" "§trunc" "§b64"
        """
    )

    fun writeFileCmd(path: String, b64: String): String = sh(
        """
        p='${sq(path)}'
        [ -f "§p" ] && cp -f "§p" "§p.bak" 2>/dev/null || true
        printf '%s' '${sq(b64)}' | base64 -d > "§p.tmp" 2>/dev/null && mv -f "§p.tmp" "§p" && {
          case "§p" in
            /opt/zapret2/lists/*) /opt/bin/yadoma-export-rules.sh >/dev/null 2>&1 || true ;;
          esac
          echo "OK: Сохранено (§p, копия .bak)"
        } || {
          rm -f "§p.tmp" 2>/dev/null
          echo "ERR: Ошибка записи §p"
        }
        """
    )

    fun mkdirCmd(path: String): String = sh("mkdir -p '${sq(path)}' 2>&1 && echo 'OK: Папка создана'")
    fun touchFileCmd(path: String): String = sh("touch '${sq(path)}' 2>&1 && echo 'OK: Файл создан'")
    fun rmPathCmd(path: String): String = sh("rm -rf '${sq(path)}' 2>&1 && echo 'OK: Удалено'")

    fun probeHostCmd(host: String, mode: String): String {
        val h = sq(host.trim().ifEmpty { "1.1.1.1" })
        return when (mode) {
            "ping" -> sh("ping -c 5 -W 2 '$h' 2>&1")
            "trace" -> sh("traceroute -n -w 2 -m 15 '$h' 2>&1 || tracepath -n '$h' 2>&1")
            "dns" -> sh("nslookup '$h' 127.0.0.1 2>&1")
            "http" -> sh("curl -IsS --max-time 6 'https://$h' 2>&1 | head -n 20")
            "conntrack" -> sh("awk '{for(i=1;i<=NF;i++) if(§i~/^src=/){s=substr(§i,5); c[s]++; break}} END{for(k in c) printf \"%5d  %s\n\", c[k], k}' /proc/net/nf_conntrack 2>/dev/null | sort -rn | head -n 20")
            "ports" -> sh("netstat -tulnp 2>/dev/null || netstat -lnt 2>/dev/null")
            "traffic" -> sh(
                """
                a=§(awk 'NR>2{gsub(/:/,"",§1); if(§1~/^(eth|br|ra|rai|tailscale|tun)/) print §1, §2, §10}' /proc/net/dev)
                sleep 1
                b=§(awk 'NR>2{gsub(/:/,"",§1); if(§1~/^(eth|br|ra|rai|tailscale|tun)/) print §1, §2, §10}' /proc/net/dev)
                awk -v A="§a" -v B="§b" 'BEGIN{
                  na=split(A,la,"\n"); for(i=1;i<=na;i++){split(la[i],p," "); r1[p[1]]=p[2]; t1[p[1]]=p[3]}
                  nb=split(B,lb,"\n"); for(i=1;i<=nb;i++){split(lb[i],p," "); if(p[1]!=""){dr=(p[2]-r1[p[1]])*8/1000; dt=(p[3]-t1[p[1]])*8/1000; printf "%-14s ↓ %8.1f kbps   ↑ %8.1f kbps\n", p[1], dr, dt}}
                }'
                """
            )
            else -> sh("ping -c 4 -W 2 '$h' 2>&1")
        }
    }

    fun tailscalePingCmd(ip: String): String = "Tailscale disabled"
}
