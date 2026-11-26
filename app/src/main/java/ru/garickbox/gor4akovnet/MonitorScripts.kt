package ru.garickbox.gor4akovnet

import java.net.URLEncoder

object MonitorScripts {
    private const val PATH_HDR = "export PATH=/opt/sbin:/opt/bin:/sbin:/usr/sbin:/bin:/usr/bin"
    private const val AWK_ESC =
        "function esc(s, i,c,o,b,n){gsub(/[\\r\\t]+/,\" \",s);gsub(/\\n/,\" \",s);o=\"\";b=sprintf(\"%c\",92);n=length(s);for(i=1;i<=n;i++){c=substr(s,i,1);if(c==b)o=o b b;else if(c==\"\\\"\")o=o b \"\\\"\";else if(c<\" \")o=o \" \";else o=o c}return o}"

    private fun sh(body: String): String = "$PATH_HDR\n" + body.trimIndent().replace('§', '$')
    private fun sq(s: String): String = s.replace("'", "'\"'\"'")
    private fun ue(s: String): String = URLEncoder.encode(s, "UTF-8")

    private const val AGH_INIT_DETECT =
        "AGH_INI=\"\"; for f in /opt/etc/init.d/S99AdGuardHome /opt/etc/init.d/S99adguardhome; do [ -x \"§f\" ] && { AGH_INI=\"§f\"; break; }; done"

    val Z2K_UPDATE_CMD = sh(
        """__LONG__
        if curl -fsS -m 15 -X POST -H 'Referer: http://127.0.0.1:8088/' 'http://127.0.0.1:8088/cgi-bin/api/update/apply' 2>/dev/null | grep -q '"ok":true'; then
          echo "OK: Обновление z2k запущено через панель :8088"
        else
          echo "=== Обновление списков и z2k-enhanced напрямую ==="
          mkdir -p /opt/tmp /opt/zapret2/lists
          for lst in russia-youtube.txt russia-discord.txt russia-blacklist.txt ipset-all.txt; do
            curl -fsSL --max-time 15 "https://raw.githubusercontent.com/necronicle/z2k/z2k-enhanced/lists/§lst" -o "/opt/zapret2/lists/§{lst}.tmp" 2>/dev/null && \
              mv -f "/opt/zapret2/lists/§{lst}.tmp" "/opt/zapret2/lists/§lst" || rm -f "/opt/zapret2/lists/§{lst}.tmp"
          done
          [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart 2>&1 || true
          echo "OK: Списки обновлены и nfqws2 перезапущен"
        fi
        """
    )

    fun buildOverviewCmd(forceUpdates: Boolean): String = sh(
        """
        ver_out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show version" 2>/dev/null)
        w0_out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster0" 2>/dev/null)
        w1_out=§(LD_LIBRARY_PATH= /bin/ndmc -c "show interface WifiMaster1" 2>/dev/null)
        model=§(printf '%s\n' "§ver_out" | awk -F': ' '/^[[:space:]]*model:/{print §2; exit}' | tr -d '\r\n"\\')
        [ -n "§model" ] || model=§(printf '%s\n' "§ver_out" | awk -F': ' '/^[[:space:]]*device:/{print §2; exit}' | tr -d '\r\n"\\')
        [ -n "§model" ] || model="Keenetic / Netcraze"
        ndms=§(printf '%s\n' "§ver_out" | awk -F': ' '/^[[:space:]]*(release|title):/{print §2; exit}' | tr -d '\r\n"\\')
        up_sec=§(cut -d. -f1 /proc/uptime 2>/dev/null | head -n1 | tr -dc '0-9')
        [ -n "§up_sec" ] || up_sec=0
        load=§(cut -d' ' -f1-3 /proc/loadavg 2>/dev/null | tr -d '\r\n"\\')
        cpu_cnt=§(awk '/^processor/{n++} END{print (n>0?n:2)}' /proc/cpuinfo 2>/dev/null | tr -dc '0-9')
        [ -n "§cpu_cnt" ] || cpu_cnt=2
        ct_cnt=§(cat /proc/sys/net/netfilter/nf_conntrack_count 2>/dev/null | head -n1 | tr -dc '0-9')
        [ -n "§ct_cnt" ] || ct_cnt=0
        ct_max=§(cat /proc/sys/net/netfilter/nf_conntrack_max 2>/dev/null | head -n1 | tr -dc '0-9')
        [ -n "§ct_max" ] || ct_max=0
        mem_json=§(awk '/^MemTotal:/{t=§2} /^MemAvailable:/{a=§2} /^MemFree:/{f=§2} /^Buffers:/{b=§2} /^Cached:/{c=§2} /^SwapTotal:/{st=§2} /^SwapFree:/{sf=§2} END{if(a==0)a=f+b+c; tm=int(t/1024); am=int(a/1024); um=tm-am; if(um<0)um=0; stm=int(st/1024); sum=int((st-sf)/1024); printf "{\"total\":%d,\"used\":%d,\"free\":%d,\"swap_t\":%d,\"swap_u\":%d}", tm, um, am, stm, sum}' /proc/meminfo 2>/dev/null)
        [ -n "§mem_json" ] || mem_json='{"total":0,"used":0,"free":0,"swap_t":0,"swap_u":0}'
        disk_json=§(df -Pm /opt 2>/dev/null | awk 'NR==2{gsub(/%/,"",§5); printf "{\"total\":%d,\"used\":%d,\"avail\":%d,\"pct\":%d}", §2+0, §3+0, §4+0, §5+0}')
        [ -n "§disk_json" ] || disk_json='{"total":0,"used":0,"avail":0,"pct":0}'
        wan_dev=§(ip route show default 2>/dev/null | awk '{for(i=1;i<=NF;i++) if(§i=="dev"){print §(i+1); exit}}' | tr -d '\r\n"\\')
        wan=""; wan_spd=""
        if [ -n "§wan_dev" ]; then
          wan=§(ip -4 addr show "§wan_dev" 2>/dev/null | awk '/inet /{print §2; exit}' | cut -d/ -f1 | tr -d '\r\n"\\')
          wan_spd=§(cat "/sys/class/net/§wan_dev/speed" 2>/dev/null | tr -dc '0-9')
        fi
        wan_gw=§(ip route show default 2>/dev/null | awk '/default/{for(i=1;i<=NF;i++)if(§i=="via"){print §(i+1);exit}}' | tr -d '\r\n"\\')
        t24=§(printf '%s\n' "§w0_out" | awk -F': ' '/^[[:space:]]*temperature:/{print §2; exit}' | tr -d ' \r\n"\\')
        t5=§(printf '%s\n' "§w1_out" | awk -F': ' '/^[[:space:]]*temperature:/{print §2; exit}' | tr -d ' \r\n"\\')
        ps_w=§(ps w 2>/dev/null)
        ns_l=§(netstat -lnt 2>/dev/null)
        has_ps() { printf '%s\n' "§ps_w" | grep -q "§1" && echo true || echo false; }
        has_pt() { printf '%s\n' "§ns_l" | grep -q ":§1 " && echo true || echo false; }
        agh_up=false; printf '%s\n' "§ps_w" | grep -q '[A]dGuardHome' && agh_up=true
        agh_inst=false; { [ -x /opt/sbin/AdGuardHome ] || [ -x /opt/bin/AdGuardHome ]; } && agh_inst=true
        z2k_loc=§(tr -d '\r\n"\\' < /opt/zapret2/.z2k-installed-tag 2>/dev/null || echo "-")
        [ -n "§z2k_loc" ] || z2k_loc="-"
        agh_loc=§(opkg list-installed adguardhome-go 2>/dev/null | awk '{print §3; exit}' | tr -d '\r\n"\\')
        [ -n "§agh_loc" ] || agh_loc=§(/opt/sbin/AdGuardHome --version 2>/dev/null | awk '{for(i=1;i<=NF;i++)if(§i~/^v?[0-9]+\./){print §i;exit}}' | tr -d '\r\n"\\')
        nfq_rules=§(iptables -t mangle -S 2>/dev/null | grep -c NFQUEUE | head -n1 | tr -dc '0-9')
        [ -n "§nfq_rules" ] || nfq_rules=0
        zst=§(curl -fsS -m 3 http://127.0.0.1:8088/cgi-bin/api/status 2>/dev/null | tr -d '\r\n' || echo '')
        case "§zst" in '{'*'}') ;; *)
          nfq_c=§(printf '%s\n' "§ps_w" | grep -c '[n]fqws2' | head -n1 | tr -dc '0-9')
          zmode=§(awk -F= '/^(MODE|Z2K_PRESET)=/{gsub(/["'"'"']/,"",§2); print §2; exit}' /opt/zapret2/config 2>/dev/null | tr -d '\r\n"\\')
          zst="{\"ok\":true,\"running\":§([ "§{nfq_c:-0}" -gt 0 ] && echo true || echo false),\"mode\":\"§{zmode:-auto}\"}"
          ;;
        esac
        if [ "${if (forceUpdates) "1" else "0"}" = "1" ]; then
          z2u=§(curl -fsS -m 8 -X POST -H 'Referer: http://127.0.0.1:8088/' 'http://127.0.0.1:8088/cgi-bin/api/update/check' 2>/dev/null | tr -d '\r\n' || echo '{"ok":false}')
        else
          z2u=§(curl -fsS -m 3 'http://127.0.0.1:8088/cgi-bin/api/update/status' 2>/dev/null | tr -d '\r\n' || echo '{"ok":false}')
        fi
        case "§z2u" in '{'*'}') ;; *) z2u='{"ok":false}' ;; esac
        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        pol_name=§(awk -F= '/^POLICY_NAME=/{gsub(/["'"'"'\r\n]/,"",§2); print §2; exit}' /opt/zapret2/config 2>/dev/null | tr -d '"\\')
        pol_mode="policy"
        pol_ok=false
        if [ -n "§pol_name" ]; then
          if printf '%s\n' "§rc" | grep -qx "ip policy §{pol_name}"; then
            pol_ok=true
          else
            # POLICY_NAME в конфиге есть, но политики в running-config НЕТ — это
            # остаток от старой версии APK. Обход z2k работает глобально через NFQUEUE
            # на WAN и политикой не пользуется, поэтому НЕ считаем это ошибкой:
            # помечаем "stale", чтобы UI не рисовал ложную тревогу "политика выкл".
            pol_mode="stale"
            pol_ok=true
          fi
        else
          pol_mode="global"
          pol_ok=true
        fi
        dns_ov=false; printf '%s\n' "§rc" | grep -q "^opkg dns-override" && dns_ov=true
        zram_on=false; printf '%s\n' "§rc" | grep -q "^[[:space:]]*zram" && zram_on=true
        ent_ok=false; [ -x /opt/bin/opkg ] && ent_ok=true
        # head -n1: awk после exit вызывает END и печатает повторно → "SSIDSSID" на 2 мастерах.
        wifi_ssid=§(printf '%s\n' "§rc" | awk '
            /^interface WifiMaster/ { if (inw && isg && ss!="" && !done) { print ss; done=1 } inw=1; isg=0; ss="" }
            inw && /^[ \t]+description Gor4akovNET/ { isg=1 }
            inw && /^[ \t]+ssid / { sub(/^[ \t]+ssid /,""); ss=§0 }
            inw && /^!/ { if (inw && isg && ss!="" && !done) { print ss; done=1 } inw=0 }
            END { if (!done && inw && isg && ss!="") print ss }
        ' | head -n1 | tr -d '\r\n"\\')
        # Пароль НЕ из running-config (там ns3-хеш), а из /opt/gor4akov-wifi.pass.
        # Отдаём ТОЛЬКО если wifi_ssid непуст — иначе это остатки от старой установки.
        wifi_pass=""
        if [ -n "§wifi_ssid" ] && [ -f /opt/etc/gor4akov-wifi.pass ]; then
            wifi_pass=§(head -n1 /opt/etc/gor4akov-wifi.pass 2>/dev/null | tr -d '\r\n"\\')
        fi
        wifi_bridge=""; wifi_subnet=""
        eval_br=§(printf '%s\n' "§rc" | awk '
            /^interface Bridge[0-9]+/ { cur=§2; inb=1; isg=0; ip="" }
            inb && /^[ \t]+description Gor4akovNET/ { isg=1 }
            inb && /^[ \t]+ip address / { ip=§3 }
            inb && /^!/ { if (isg && cur!="") { print cur"|"ip; inb=0; exit } inb=0 }
            END { if (inb && isg && cur!="") print cur"|"ip }
        ')
        wifi_bridge=§(printf '%s' "§eval_br" | cut -d'|' -f1 | tr -d '\r\n"\\')
        wifi_subnet=§(printf '%s' "§eval_br" | cut -d'|' -f2 | tr -d '\r\n"\\')
        printf '{"sys":{"model":"%s","ndms":"%s","up_sec":%s,"load":"%s","cpus":%s,"ct":%s,"ct_max":%s,"mem":%s,"disk":%s,"temp24":"%s","temp5":"%s","zram":%s},"wan":{"dev":"%s","ip":"%s","spd":"%s","gw":"%s","policy_ok":%s,"policy_name":"%s","policy_mode":"%s","nfq_rules":%s,"ent_ok":%s,"agh_inst":%s,"dns_ov":%s},"svc":{"nfqws2":%s,"gvsub":%s,"adguard":%s,"tg_tunnel":%s,"http_tunnel":%s,"warp":%s,"z2k_panel":%s,"scheduler":%s},"ver":{"z2k_local":"%s","agh":"%s","z2u":%s},"z2k":%s,"wifi":{"ssid":"%s","pass":"%s","bridge":"%s","subnet":"%s"}}\n' \
          "§model" "§ndms" "§up_sec" "§load" "§cpu_cnt" "§ct_cnt" "§ct_max" "§mem_json" "§disk_json" "§t24" "§t5" "§zram_on" \
          "§wan_dev" "§wan" "§wan_spd" "§wan_gw" "§pol_ok" "§pol_name" "§pol_mode" "§nfq_rules" "§ent_ok" "§agh_inst" "§dns_ov" \
          "§(has_ps '[n]fqws2')" "§(has_ps 'z2k-detect')" "§agh_up" \
          "§(has_pt 1443)" "§(has_pt 1444)" "§(has_ps 'z2k-warpd')" "§(has_pt 8088)" \
          "§(has_ps 'z2k-scheduler')" \
          "§z2k_loc" "§agh_loc" "§z2u" "§zst" \
          "§wifi_ssid" "§wifi_pass" "§wifi_bridge" "§wifi_subnet"
        """
    )

    val CMD_BYPASS_STATE = sh(
        """
        panel_up=true
        if command -v nc >/dev/null 2>&1; then
          nc -z -w 1 127.0.0.1 8088 2>/dev/null || panel_up=false
        fi
        cp_get() { if [ "§panel_up" = true ]; then curl -fsS "§@" 2>/dev/null | tr -d '\000-\037' || echo ''; else echo ''; fi; }
        zst=§(cp_get -m 4 http://127.0.0.1:8088/cgi-bin/api/status)
        case "§zst" in '{'*'}') ;; *)
          zrun=false
          if pgrep -f nfqws2 >/dev/null 2>&1; then zrun=true
          elif ps w 2>/dev/null | grep -q '[n]fqws2'; then zrun=true
          elif ps 2>/dev/null | grep -q '[n]fqws2'; then zrun=true
          fi
          zcfg_json=§(ZRUN="§zrun" awk -F= '$AWK_ESC /^([A-Z0-9_]+)=/{k=§1; v=§2; gsub(/^["'"'"']|["'"'"']§/,"",v); if(k=="MODE"||k=="Z2K_PRESET") md=v; if(k=="DISABLE_IPV6") ipv6=v; if(k=="POLICY_NAME") pol=v; if(k=="POLICY_EXCLUDE") pex=v; if(k=="AUTOHOSTLIST") ahl=v} END{printf "{\"ok\":true,\"direct\":true,\"running\":%s,\"mode\":\"%s\",\"no_ipv6\":%s,\"policy\":\"%s\",\"policy_exclude\":%d,\"autohost\":%s}", ENVIRON["ZRUN"], esc(md==""?"auto":md), (ipv6=="1"?"true":"false"), esc(pol), pex+0, (ahl=="1"?"true":"false")}' /opt/zapret2/config 2>/dev/null)
          zst="§{zcfg_json:-{\"ok\":true,\"direct\":true}}"
          ;;
        esac
        wst=§(cp_get -m 4 http://127.0.0.1:8088/cgi-bin/api/warp/status)
        case "§wst" in '{'*) ;; *)
          winst=false
          if [ -x /opt/sbin/z2k-warpd ] || [ -f /opt/sbin/z2k-warpd ] || [ -x /opt/zapret2/z2k-warp.sh ] || [ -f /opt/zapret2/z2k-warp.sh ]; then winst=true; fi
          wrun=false
          if pgrep -f z2k-warpd >/dev/null 2>&1; then wrun=true
          elif ps w 2>/dev/null | grep -q '[z]2k-warpd'; then wrun=true
          elif ps 2>/dev/null | grep -q '[z]2k-warpd'; then wrun=true
          fi
          wst="{\"ok\":true,\"installed\":§winst,\"running\":§wrun}"
          ;;
        esac
        pst=§(cp_get -m 3 http://127.0.0.1:8088/cgi-bin/api/policy/status)
        case "§pst" in '{'*) ;; *)
          pol_nm=§(awk -F= '/^POLICY_NAME=/{gsub(/["'"'"'\r\n]/,"",§2); print §2; exit}' /opt/zapret2/config 2>/dev/null)
          pol_ex=§(awk -F= '/^POLICY_EXCLUDE=/{print §2+0; exit}' /opt/zapret2/config 2>/dev/null | head -n1 | tr -dc '0-9')
          pst="{\"ok\":true,\"name\":\"§{pol_nm:-Gor4akovNET}\",\"exclude\":§{pol_ex:-0}}"
          ;;
        esac
        ahl=§(cp_get -m 3 http://127.0.0.1:8088/cgi-bin/api/autohostlist-domains)
        case "§ahl" in '{'*) ;; *) ahl='{"ok":false,"domains":[]}' ;; esac
        list_to_json() {
          if [ -r "§1" ]; then
            awk '$AWK_ESC BEGIN{printf "["} {gsub(/\r/,"");sub(/#.*/,"");gsub(/^[[:space:]]+|[[:space:]]+§/,""); if(§0!=""){printf "%s\"%s\"", (n++?",":""), esc(§0)}} END{printf "]"}' "§1"
          else
            printf '[]'
          fi
        }
        ex_arr=§(list_to_json /opt/zapret2/lists/extra-domains.txt)
        wl_file="/opt/zapret2/lists/whitelist.txt"
        [ -r /opt/zapret2/lists/exclude.txt ] && [ ! -s "§wl_file" ] && wl_file="/opt/zapret2/lists/exclude.txt"
        wl_arr=§(list_to_json "§wl_file")
        raw_cfg=§(head -n 200 /opt/zapret2/config 2>/dev/null | awk '$AWK_ESC BEGIN{printf "["} /^[A-Z0-9_]+=/{printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        [ -n "§raw_cfg" ] || raw_cfg='[]'
        nfq_pkts=§(iptables -t mangle -L -v -n 2>/dev/null | awk '$AWK_ESC /NFQUEUE/{a[++n]=sprintf("%s pkts | %s B | %s", §1, §2, esc(§0))} END{printf "["; for(i=1;i<=n;i++)printf "%s\"%s\"", (i>1?",":""), a[i]; printf "]"}')
        [ -n "§nfq_pkts" ] || nfq_pkts='[]'
        # Извлечь реальные пулы из config или extra_strats
        pool_info=§(grep -hE '^(RKN_TCP|YT_TCP|GV_TCP|YT_QUIC|DISCORD_UDP|AUTOCIRCULAR)=' /opt/zapret2/config /opt/zapret2/extra_strats/pool*.conf 2>/dev/null | head -n 20 | awk '$AWK_ESC BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        [ -n "§pool_info" ] || pool_info='[]'
        printf '{"z2k":%s,"warp":%s,"policy":%s,"autohost":%s,"extra":%s,"whitelist":%s,"wl_file":"%s","cfg_lines":%s,"nfq_pkts":%s,"pool_info":%s}\n' \
          "§zst" "§wst" "§pst" "§ahl" "§ex_arr" "§wl_arr" "§wl_file" "§raw_cfg" "§nfq_pkts" "§pool_info"
        """
    )

    val CMD_WARP_FULL = sh(
        """
        panel_up=true
        if command -v nc >/dev/null 2>&1; then nc -z -w 1 127.0.0.1 8088 2>/dev/null || panel_up=false; fi
        cp_get() { if [ "§panel_up" = true ]; then curl -fsS "§@" 2>/dev/null | tr -d '\000-\037' || echo ''; else echo ''; fi; }

        wst=§(cp_get -m 4 http://127.0.0.1:8088/cgi-bin/api/warp/status)
        case "§wst" in '{'*'}') ;; *)
          if [ -x /opt/zapret2/z2k-warp.sh ]; then
            wst=§(sh /opt/zapret2/z2k-warp.sh status --json 2>/dev/null | tr -d '\r\n' || echo '')
          fi
          case "§wst" in '{'*'}') ;; *)
            winst=false; [ -x /opt/sbin/z2k-warpd ] || [ -f /opt/sbin/z2k-warpd ] || [ -f /opt/zapret2/z2k-warp.sh ] && winst=true
            wrun=false; ps w 2>/dev/null | grep -q '[z]2k-warpd' && wrun=true
            tr=§(awk -F= '/^Z2K_WARP_TRANSPORT=/{gsub(/["'"'"']/,"",§2); print §2; exit}' /opt/zapret2/config 2>/dev/null)
            wst="{\"ok\":true,\"installed\":§winst,\"running\":§wrun,\"ready\":§wrun,\"transport\":\"§{tr:-auto}\"}"
            ;;
          esac
          ;;
        esac

        wgames=§(cp_get -m 4 http://127.0.0.1:8088/cgi-bin/api/warp/games)
        case "§wgames" in '{'*'}') ;; *) wgames='{"ok":false,"games":[]}' ;; esac

        wlists=§(cp_get -m 4 http://127.0.0.1:8088/cgi-bin/api/warp/lists)
        case "§wlists" in '{'*'}') ;; *)
          if [ -r /opt/zapret2/lists/warp-domains.txt ]; then
            wlists=§(awk '$AWK_ESC BEGIN{printf "{\"ok\":true,\"domains\":["} {gsub(/\r/,"");sub(/#.*/,"");gsub(/^[[:space:]]+|[[:space:]]+§/,""); if(§0!="") printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]}"}' /opt/zapret2/lists/warp-domains.txt 2>/dev/null)
          fi
          case "§wlists" in '{'*'}') ;; *) wlists='{"ok":true,"domains":[]}' ;; esac
          ;;
        esac

        wexcl=§(cp_get -m 4 http://127.0.0.1:8088/cgi-bin/api/warp/exclude)
        case "§wexcl" in '{'*'}') ;; *)
          if [ -r /opt/zapret2/lists/warp-exclude.txt ]; then
            wexcl=§(awk '$AWK_ESC BEGIN{printf "{\"ok\":true,\"domains\":["} {gsub(/\r/,"");sub(/#.*/,"");gsub(/^[[:space:]]+|[[:space:]]+§/,""); if(§0!="") printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]}"}' /opt/zapret2/lists/warp-exclude.txt 2>/dev/null)
          fi
          case "§wexcl" in '{'*'}') ;; *) wexcl='{"ok":true,"domains":[]}' ;; esac
          ;;
        esac

        wdevs=§(cp_get -m 4 http://127.0.0.1:8088/cgi-bin/api/warp/devices)
        case "§wdevs" in '{'*'}') ;; *) wdevs='{"ok":false,"devices":[]}' ;; esac

        tun_ip=§(ip -4 addr show z2ktun0 2>/dev/null | awk '/inet /{print §2; exit}' | tr -d '\r\n"\\')
        tun_mtu=§(cat /sys/class/net/z2ktun0/mtu 2>/dev/null | tr -dc '0-9')
        w_ram=§(ps w 2>/dev/null | awk '/[z]2k-warpd/{pid=§1; exit} END{if(pid!=""){while((getline l < ("/proc/"pid"/status"))>0){if(l~/^VmRSS:/){split(l,p," "); printf "%.1f", p[2]/1024; exit}}}}')
        printf '{"status":%s,"games":%s,"lists":%s,"exclude":%s,"devices":%s,"tun_ip":"%s","tun_mtu":"%s","ram_mb":"%s"}\n' \
          "§wst" "§wgames" "§wlists" "§wexcl" "§wdevs" "§tun_ip" "§tun_mtu" "§w_ram"
        """
    )

    val CMD_WARP_TRACE = sh(
        """
        echo "=== Проверка канала через Cloudflare WARP ==="
        if ip link show z2ktun0 >/dev/null 2>&1; then
          curl -fsSL --interface z2ktun0 --connect-timeout 5 --max-time 8 https://cloudflare.com/cdn-cgi/trace 2>&1 || echo "Не удалось связаться через z2ktun0"
        else
          echo "Интерфейс z2ktun0 не поднят — включите WARP."
        fi
        """
    )

    fun warpTransportCmd(mode: String): String = sh(
        """
        m='${sq(mode)}'
        resp=§(curl -sS -m 10 -X POST -H 'Referer: http://127.0.0.1:8088/' -d "transport=${ue(mode)}" 'http://127.0.0.1:8088/cgi-bin/api/warp/transport' 2>/dev/null || true)
        if printf '%s' "§resp" | grep -q '"ok":true'; then
          echo "§resp"
        else
          CFG="/opt/zapret2/config"
          [ -f "§CFG" ] && sed -i '/^Z2K_WARP_TRANSPORT=/d' "§CFG" && printf 'Z2K_WARP_TRANSPORT="%s"\n' "§m" >> "§CFG"
          [ -x /opt/zapret2/z2k-warp.sh ] && sh /opt/zapret2/z2k-warp.sh restart 2>&1 || true
          echo "OK: Транспорт WARP -> §m"
        fi
        """
    )

    fun warpReregisterCmd(): String = sh(
        """__LONG__
        resp=§(curl -sS -m 20 -X POST -H 'Referer: http://127.0.0.1:8088/' 'http://127.0.0.1:8088/cgi-bin/api/warp/reregister' 2>/dev/null || true)
        if printf '%s' "§resp" | grep -q '"ok":true'; then
          echo "§resp"
        elif [ -x /opt/zapret2/z2k-warp.sh ]; then
          sh /opt/zapret2/z2k-warp.sh reregister 2>&1 || { rm -f /opt/zapret2/warp*.conf /opt/etc/z2k-warp* 2>/dev/null; sh /opt/zapret2/z2k-warp.sh enable 2>&1; }
        else
          echo "ERR: z2k-warp.sh не найден"
        fi
        """
    )

    fun warpDomainRuleCmd(kind: String, domain: String, add: Boolean): String {
        val clean = domain.trim().lowercase()
        val ep = when {
            kind == "exclude" && add -> "warp/exclude/add"
            kind == "exclude" && !add -> "warp/exclude/delete"
            add -> "warp/lists/add"
            else -> "warp/lists/delete"
        }
        val file = if (kind == "exclude") "/opt/zapret2/lists/warp-exclude.txt" else "/opt/zapret2/lists/warp-domains.txt"
        return sh(
            """
            d='${sq(clean)}'; f='${sq(file)}'
            resp=§(curl -sS -m 8 -X POST -H 'Referer: http://127.0.0.1:8088/' -d "domain=${ue(clean)}" "http://127.0.0.1:8088/cgi-bin/api/$ep" 2>/dev/null || true)
            mkdir -p /opt/zapret2/lists && touch "§f"
            if [ "${if (add) 1 else 0}" = "1" ]; then
              grep -qxF "§d" "§f" || printf '%s\n' "§d" >> "§f"
            else
              awk -v t="§d" '{gsub(/\r/,""); x=§0; gsub(/^[[:space:]]+|[[:space:]]+§/,"",x); if(x!=t) print §0}' "§f" > "§f.tmp" && mv -f "§f.tmp" "§f"
            fi
            [ -x /opt/zapret2/z2k-warp.sh ] && sh /opt/zapret2/z2k-warp.sh reload >/dev/null 2>&1 || true
            echo "OK: §d"
            """
        )
    }

    fun warpDeviceToggleCmd(ipOrMac: String, enable: Boolean): String =
        z2kApiJob("warp/devices/toggle", "ip=${ue(ipOrMac)}&enabled=${if (enable) 1 else 0}", 12)

    val CMD_DNS_NET = sh(
        """
        agh_up=false
        (ps w 2>/dev/null || ps 2>/dev/null) | grep -q '[A]dGuardHome' && agh_up=true
        agh_port=§(awk -F: '/^  address:/{p=§NF; gsub(/[^0-9]/,"",p); if(p!="") print p; exit}' /opt/etc/AdGuardHome/AdGuardHome.yaml 2>/dev/null | tr -dc '0-9')
        [ -n "§agh_port" ] || agh_port=3000
        agh_meta=""
        if [ -r /opt/etc/AdGuardHome/AdGuardHome.yaml ]; then
          agh_meta=§(awk '
            $AWK_ESC
            /^  upstream_dns:/ {in_u=1; next}
            in_u && /^    - / {sub(/^    - /,""); ups[++nu]=esc(§0); next}
            in_u && !/^    / {in_u=0}
            function flush_f() {
              if(f_act) { f[++nf]=sprintf("{\"id\":%d,\"name\":\"%s\",\"on\":%s}", fid+0, (fn==""?("Filter #" (fid+0)):fn), (fe=="true"?"true":"false")); f_act=0; fe="false"; fn=""; fid=0 }
            }
            /^filters:/ {in_f=1; next}
            in_f && /^[a-z_]+:/ {flush_f(); in_f=0}
            in_f && /^  - / {
              flush_f(); f_act=1
              if(§0 ~ /^  - enabled:/) fe=§3
              else if(§0 ~ /^  - name:/){ sub(/^  - name:[[:space:]]*/,""); fn=esc(§0) }
              else if(§0 ~ /^  - id:/){ fid=§3 }
              next
            }
            in_f && /^    enabled:/ {fe=§2}
            in_f && /^    name:/ {sub(/^    name:[[:space:]]*/,""); fn=esc(§0)}
            in_f && /^    id:/ {fid=§2}
            /^  protection_enabled:/ {prot=§2}
            /^  filtering_enabled:/ {filt=§2}
            /^  enable_dnssec:/ {dsec=§2}
            /^  cache_enabled:/ {cach=§2}
            /^  cache_optimistic:/ {copt=§2}
            /^  aaaa_disabled:/ {naaaa=§2}
            /^  upstream_mode:/ {umod=esc(§2)}
            END {
              flush_f()
              printf "{\"prot\":%s,\"filt\":%s,\"dnssec\":%s,\"cache\":%s,\"cache_opt\":%s,\"no_aaaa\":%s,\"up_mode\":\"%s\",\"upstreams\":[",
                (prot=="true"?"true":"false"), (filt=="true"?"true":"false"), (dsec=="true"?"true":"false"),
                (cach=="true"?"true":"false"), (copt=="true"?"true":"false"), (naaaa=="true"?"true":"false"), umod
              for(i=1;i<=nu;i++) printf "%s\"%s\"", (i>1?",":""), ups[i]
              printf "],\"filters\":["
              for(i=1;i<=nf;i++) printf "%s%s", (i>1?",":""), f[i]
              printf "]}"
            }' /opt/etc/AdGuardHome/AdGuardHome.yaml 2>/dev/null)
        fi
        [ -n "§agh_meta" ] || agh_meta='{"prot":false,"filt":false,"dnssec":false,"cache":false,"cache_opt":false,"no_aaaa":false,"up_mode":"","upstreams":[],"filters":[]}'
        qcnt=0
        if [ -r /opt/etc/AdGuardHome/data/querylog.json ]; then
          qcnt=§(wc -l < /opt/etc/AdGuardHome/data/querylog.json 2>/dev/null | head -n1 | tr -dc '0-9')
        fi
        [ -n "§qcnt" ] || qcnt=0
        qlog=§(tail -n 30 /opt/etc/AdGuardHome/data/querylog.json 2>/dev/null | awk '
          $AWK_ESC
          function jstr(line, key,   pat, pos, rest, q) {
            pat="\"" key "\":\""
            pos=index(line, pat)
            if(pos==0) return ""
            rest=substr(line, pos+length(pat))
            q=index(rest, "\"")
            if(q==0) return ""
            return substr(rest, 1, q-1)
          }
          {
            qh=jstr(§0,"QH"); qt=jstr(§0,"QT"); ip=jstr(§0,"IP"); tm=jstr(§0,"T")
            if(qh=="") next
            blk=(index(§0,"\"IsFiltered\":true")>0) ? "true" : "false"
            if(length(tm)>19) tm=substr(tm,12,8)
            rows[++nr]=sprintf("{\"t\":\"%s\",\"ip\":\"%s\",\"dom\":\"%s\",\"qt\":\"%s\",\"blk\":%s}", esc(tm), esc(ip), esc(qh), esc(qt), blk)
          }
          END { printf "["; for(i=nr;i>=1;i--) printf "%s%s", (i<nr?",":""), rows[i]; printf "]" }
        ')
        [ -n "§qlog" ] || qlog='[]'
        errs=§(grep '\[error\]' /opt/var/log/AdGuardHome.log 2>/dev/null | tail -n 5 | tr -d '\r' | awk '$AWK_ESC BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(substr(§0,1,220))} END{printf "]"}')
        [ -n "§errs" ] || errs='[]'
        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
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
        printf '{"agh_up":%s,"agh_port":%s,"agh":%s,"qcnt":%s,"qlog":%s,"errs":%s,"ndms":%s,"ip_static":%s,"upnp_list":%s}\n' \
          "§agh_up" "§agh_port" "§agh_meta" "§qcnt" "§qlog" "§errs" "§ndms_json" "§ip_static" "§upnp_list"
        """
    )

    val CMD_WIFI_DEVICES = sh(
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
          $AWK_ESC
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
          $AWK_ESC
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

    val CMD_WAN_NET = sh(
        """
        wan_dev=§(ip route show default 2>/dev/null | awk '{for(i=1;i<=NF;i++) if(§i=="dev"){print §(i+1); exit}}' | tr -d '\r\n"\\')
        wan=""; spd=""; rx=0; tx=0
        if [ -n "§wan_dev" ]; then
          wan=§(ip -4 addr show "§wan_dev" 2>/dev/null | awk '/inet /{print §2; exit}' | cut -d/ -f1 | tr -d '\r\n"\\')
          spd=§(cat "/sys/class/net/§wan_dev/speed" 2>/dev/null | tr -dc '0-9')
          rx=§(cat "/sys/class/net/§wan_dev/statistics/rx_bytes" 2>/dev/null | head -n1 | tr -dc '0-9')
          tx=§(cat "/sys/class/net/§wan_dev/statistics/tx_bytes" 2>/dev/null | head -n1 | tr -dc '0-9')
        fi
        gw=§(ip route show default 2>/dev/null | awk '/default/{for(i=1;i<=NF;i++)if(§i=="via"){print §(i+1);exit}}' | tr -d '\r\n"\\')
        dns=§(awk '/^nameserver/{a=a (a?", ":"") §2} END{print a}' /etc/resolv.conf 2>/dev/null | tr -d '\r\n"\\')
        ndm_wan=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip route default" 2>/dev/null | awk '$AWK_ESC /interface:|gateway:|metric:/{gsub(/^[[:space:]]+/,""); o=(o?o " • ":"") §0} END{print esc(o)}')
        pol_info=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip policy" 2>/dev/null | awk '$AWK_ESC tolower(§0)~/gor4akovnet/{f=1} f{gsub(/^[[:space:]]+/,""); o=(o?o "\n":"") §0; if(++n>=6)exit} END{print esc(o)}')
        printf '{"dev":"%s","wan":"%s","gw":"%s","spd":"%s","rx":%s,"tx":%s,"dns":"%s","ndm_wan":"%s","pol_info":"%s"}\n' \
          "§wan_dev" "§wan" "§gw" "§spd" "§{rx:-0}" "§{tx:-0}" "§dns" "§ndm_wan" "§pol_info"
        """
    )

    val CMD_SERVICES = sh(
        """
        PFX="/tmp/.gps_§$"
        mem_tot_kb=§(awk '/^MemTotal:/{print §2; exit}' /proc/meminfo 2>/dev/null | tr -dc '0-9')
        [ -n "§mem_tot_kb" ] || mem_tot_kb=262144
        mem_free_mb=§(awk '/^MemAvailable:/{a=§2} /^MemFree:/{f=§2} END{print int((a>0?a:f)/1024)}' /proc/meminfo 2>/dev/null)
        ps w 2>/dev/null > "§{PFX}_ps"
        (netstat -lntp 2>/dev/null || netstat -lnt 2>/dev/null) > "§{PFX}_ns"
        awk '
          NR>1 && §1 ~ /^[0-9]+§/{
            pid=§1; cpu=0
            for(i=2;i<=NF;i++) if(§i ~ /^[0-9]+(\.[0-9]+)?%§/){ gsub(/%/,"",§i); cpu=§i; break }
            rss=0; nm=""
            stf="/proc/" pid "/status"
            while((getline l < stf) > 0){
              if(l ~ /^VmRSS:/){ split(l,a," "); rss=a[2]+0 }
              else if(l ~ /^Name:/){ sub(/^Name:[[:space:]]*/,"",l); nm=l }
            }
            close(stf)
            cmd=""
            cf="/proc/" pid "/cmdline"
            if((getline cmd < cf) > 0) gsub(/\0/," ",cmd)
            close(cf)
            if(cmd==""){
              for(i=5;i<=NF;i++) cmd=(cmd==""?§i:cmd" "§i)
              if(cmd=="") cmd=nm
            }
            gsub(/\|/," ",cmd)
            if(length(cmd)>140) cmd=substr(cmd,1,140)
            printf "%s|%d|%s|%s\n", pid, rss, cpu, cmd
          }' "§{PFX}_ps" > "§{PFX}_pr" 2>/dev/null

        svcs_json=""
        for ini in /opt/etc/init.d/S*; do
          [ -e "§ini" ] || continue
          id=§(basename "§ini")
          inst=false; [ -x "§ini" ] && inst=true
          pat=§(awk -F= '/^(PROCS|DESC|NAME)=/{gsub(/["'"'"']/,"",§2); if(§2!=""){print §2; exit}}' "§ini" 2>/dev/null)
          case "§id" in
            *zapret*) pat="nfqws2" ;;
            *AdGuard*|*adguard*) pat="AdGuardHome" ;;
            *warp*) pat="z2k-warpd" ;;
            *tg-tunnel*) pat="tg-mtproxy" ;;
            *http-tunnel*) pat="z2k-http" ;;
            *webpanel*|*lighttpd*) pat="lighttpd" ;;
            *scheduler*) pat="z2k-scheduler" ;;
            *cron*) pat="crond" ;;
            *dropbear*) pat="dropbear" ;;
          esac
          res=§(awk -v PAT="§pat" -v MEM="§mem_tot_kb" -v PFX="§PFX" '$AWK_ESC BEGIN{
            pat=tolower(PAT); mem=MEM+0; tot_rss=0; tot_cpu=0; pids=""; ports=""
            while((getline l < (PFX "_pr")) > 0){
              split(l, a, "|")
              if(pat!="" && index(tolower(a[4]), pat)>0){
                tot_rss+=a[2]+0; tot_cpu+=a[3]+0; pids=(pids==""?a[1]:pids","a[1])
              }
            }
            close(PFX "_pr")
            while((getline l < (PFX "_ns")) > 0){
              split(l, np, " "); split(np[4], hp, ":"); pt=hp[length(hp)]
              if(pt !~ /^[0-9]+§/) continue
              hit=0
              if(pat!="" && index(tolower(l), pat)>0) hit=1
              else if(pat=="adguardhome" && (pt=="3000" || pt=="53")) hit=1
              else if((pat=="lighttpd" || pat=="z2k-webpanel") && pt=="8088") hit=1
              else if(pat=="tg-mtproxy" && pt=="1443") hit=1
              else if((pat=="z2k-http" || pat=="z2k-http-tunnel") && pt=="1444") hit=1
              else if(pat=="dropbear" && (pt=="222" || pt=="22")) hit=1
              if(hit && index(ports, ":"pt)==0) ports=(ports==""?":"pt:ports",:"pt)
            }
            close(PFX "_ns")
            rmb=tot_rss/1024; rpct=(mem>0?tot_rss*100/mem:0)
            printf "%d|%.1f|%.1f|%.1f|%s|%s", (pids!=""?1:0), rmb, rpct, tot_cpu, esc(pids), esc(ports)
          }' 2>/dev/null)
          up_i=§(echo "§res" | cut -d'|' -f1)
          up=false; [ "§up_i" = "1" ] && up=true
          ram_mb=§(echo "§res" | cut -d'|' -f2)
          ram_pct=§(echo "§res" | cut -d'|' -f3)
          cpu_pct=§(echo "§res" | cut -d'|' -f4)
          pids=§(echo "§res" | cut -d'|' -f5)
          ports=§(echo "§res" | cut -d'|' -f6)
          item="{\"id\":\"§id\",\"inst\":§inst,\"up\":§up,\"ram_mb\":§{ram_mb:-0},\"ram_pct\":§{ram_pct:-0},\"cpu\":§{cpu_pct:-0},\"pids\":\"§pids\",\"ports\":\"§ports\"}"
          svcs_json="§{svcs_json}§([ -n "§svcs_json" ] && echo ',')§item"
        done

        top_procs=§(awk '$AWK_ESC {split(§0,a,"|"); if(a[2]+0>512){c=a[4]; if(length(c)>55) c=substr(c,1,55); printf "%08d|%s|%.1f|%s|%s\n", a[2]+0, a[1], a[2]/1024, a[3], esc(c)}}' "§{PFX}_pr" 2>/dev/null | sort -rn | head -n 10 | awk 'BEGIN{printf "["}{split(§0,p,"|"); printf "%s{\"pid\":\"%s\",\"ram_mb\":%s,\"cpu\":%s,\"cmd\":\"%s\"}", (n++?",":""), p[2], p[3]+0, p[4]+0, p[5]} END{printf "]"}')
        [ -n "§top_procs" ] || top_procs='[]'

        cron_jobs=§(grep -hvE '^[[:space:]]*$|^[[:space:]]*#' /opt/var/spool/cron/crontabs/root /opt/etc/crontab 2>/dev/null | head -n 25 | awk '$AWK_ESC BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        [ -n "§cron_jobs" ] || cron_jobs='[]'

        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        cifs=false; printf '%s\n' "§rc" | grep -q '^service cifs§' && cifs=true
        webdav=false; printf '%s\n' "§rc" | awk '/^ip http webdav§/{inw=1; next} /^!/{inw=0} inw && /^[[:space:]]*enable§/{f=1} inw && /^[[:space:]]*no enable§/{f=0} /^webdav enable§/{f=1} END{exit !f}' && webdav=true
        zram=false; printf '%s\n' "§rc" | grep -q '^[[:space:]]*zram§' && zram=true
        usb2=false; printf '%s\n' "§rc" | grep -q 'dev\.usb\.force_usb2 1' && usb2=true
        opkg_cnt=§(opkg list-installed 2>/dev/null | wc -l | head -n1 | tr -dc '0-9')
        [ -n "§opkg_cnt" ] || opkg_cnt=0
        disks=§(df -Pm 2>/dev/null | awk '$AWK_ESC NF==1{d=§0; getline; §0=d" "§0} NR>1{mnt=""; for(i=6;i<=NF;i++) mnt=(mnt==""?§i:mnt" "§i); if(mnt=="/opt" || mnt ~ /^\/(tmp\/mnt|media|mnt)\// || mnt=="/storage"){gsub(/%/,"",§5); printf "%s{\"mnt\":\"%s\",\"tot\":%d,\"used\":%d,\"avail\":%d,\"pct\":%d}", (n++?",":""), esc(mnt), §2+0, §3+0, §4+0, §5+0}}')
        rm -f "§{PFX}_ps" "§{PFX}_ns" "§{PFX}_pr"
        printf '{"svcs":[%s],"top_procs":%s,"cron":%s,"mem_tot_mb":%d,"mem_free_mb":%d,"ndms":{"cifs":%s,"webdav":%s,"zram":%s,"usb2":%s},"opkg_cnt":%s,"disks":[%s]}\n' \
          "§svcs_json" "§top_procs" "§cron_jobs" "§((mem_tot_kb/1024))" "§{mem_free_mb:-0}" "§cifs" "§webdav" "§zram" "§usb2" "§opkg_cnt" "§disks"
        """
    )

    fun addCronJobCmd(expr: String, cmd: String): String = sh(
        """
        mkdir -p /opt/var/spool/cron/crontabs
        cf="/opt/var/spool/cron/crontabs/root"
        touch "§cf"
        printf '%s %s\n' '${sq(expr.trim())}' '${sq(cmd.trim())}' >> "§cf"
        [ -x /opt/etc/init.d/S10cron ] && /opt/etc/init.d/S10cron restart >/dev/null 2>&1 || true
        echo "OK: Задача Cron добавлена"
        """
    )

    fun delCronJobCmd(exactLine: String): String = sh(
        """
        cf="/opt/var/spool/cron/crontabs/root"
        [ -f "§cf" ] && awk -v t='${sq(exactLine)}' '{if(§0!=t) print §0}' "§cf" > "§cf.tmp" && mv -f "§cf.tmp" "§cf"
        [ -x /opt/etc/init.d/S10cron ] && /opt/etc/init.d/S10cron restart >/dev/null 2>&1 || true
        echo "OK: Задача Cron удалена"
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

    val CMD_PROBE_SITES_JSON = sh(
        """
        TMPP="/tmp/.gbox_probes_§$"
        : > "§TMPP"
        chk() {
          cat="§1"; host="§2"; url="§3"
          res=§(curl -IsS -L --connect-timeout 4 --max-time 6 -o /dev/null -w '%{http_code}|%{time_namelookup}|%{time_appconnect}|%{time_total}' "§url" 2>/dev/null || echo '000|0|0|0')
          printf '%s|%s|%s\n' "§cat" "§host" "§res" >> "§TMPP"
        }
        chk "Видео" "youtube.com" "https://www.youtube.com" &
        chk "Видео" "youtu.be" "https://youtu.be" &
        chk "Видео" "googlevideo" "https://redirector.googlevideo.com" &
        chk "Видео" "twitch.tv" "https://www.twitch.tv" &
        chk "Видео" "vimeo.com" "https://vimeo.com" &
        chk "Видео" "soundcloud" "https://soundcloud.com" &
        chk "Мессенджеры" "discord.com" "https://discord.com" &
        chk "Мессенджеры" "gateway.dc" "https://gateway.discord.gg" &
        chk "Мессенджеры" "web.tg" "https://web.telegram.org" &
        chk "Мессенджеры" "signal.org" "https://signal.org" &
        chk "Соцсети" "instagram.com" "https://www.instagram.com" &
        chk "Соцсети" "facebook.com" "https://www.facebook.com" &
        chk "Соцсети" "x.com" "https://x.com" &
        chk "Соцсети" "threads.net" "https://www.threads.net" &
        chk "ИИ" "chatgpt.com" "https://chatgpt.com" &
        chk "ИИ" "claude.ai" "https://claude.ai" &
        chk "ИИ" "gemini.google" "https://gemini.google.com" &
        chk "ИИ" "perplexity.ai" "https://www.perplexity.ai" &
        chk "Dev/Игры" "github.com" "https://github.com" &
        chk "Dev/Игры" "rutracker.org" "https://rutracker.org" &
        chk "Dev/Игры" "store.steam" "https://store.steampowered.com" &
        chk "Dev/Игры" "spotify.com" "https://open.spotify.com" &
        chk "Контроль РФ" "gosuslugi.ru" "https://www.gosuslugi.ru" &
        chk "Контроль РФ" "vk.com" "https://vk.com" &
        wait
        awk '$AWK_ESC BEGIN{printf "{\"sites\":["} {
          split(§0, p, "|")
          code=p[3]+0; dns=int((p[4]+0)*1000); tls=int((p[5]+0)*1000); tot=int((p[6]+0)*1000)
          ok=(code>=200 && code<500)?"true":"false"
          printf "%s{\"cat\":\"%s\",\"host\":\"%s\",\"code\":%d,\"dns_ms\":%d,\"tls_ms\":%d,\"tot_ms\":%d,\"ok\":%s}",
            (n++?",":""), esc(p[1]), esc(p[2]), code, dns, tls, tot, ok
        } END{printf "]}"}' "§TMPP"
        rm -f "§TMPP"
        """
    )

    val CMD_BENCH_SNAPSHOT = sh(
        """
        ping_1=§(ping -c 3 -W 2 1.1.1.1 2>/dev/null | awk -F'/' '/round-trip|rtt/{print §5+0; exit}')
        [ -n "§ping_1" ] || ping_1=0
        yt=§(curl -IsS -L --connect-timeout 4 --max-time 6 -o /dev/null -w '%{http_code}|%{time_namelookup}|%{time_total}' https://www.youtube.com 2>/dev/null || echo '000|0|0')
        dc=§(curl -IsS -L --connect-timeout 4 --max-time 6 -o /dev/null -w '%{http_code}|%{time_total}' https://discord.com 2>/dev/null || echo '000|0')
        ram_free=§(awk '/^MemAvailable:/{a=§2} /^MemFree:/{f=§2} END{print int((a>0?a:f)/1024)}' /proc/meminfo 2>/dev/null)
        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        ppe_hw=false; printf '%s\n' "§rc" | grep -q '^ppe hardware§' && ppe_hw=true
        ppe_sw=false; printf '%s\n' "§rc" | grep -q '^ppe software§' && ppe_sw=true
        zram=false; printf '%s\n' "§rc" | grep -q '^[[:space:]]*zram§' && zram=true
        ct_max=§(cat /proc/sys/net/netfilter/nf_conntrack_max 2>/dev/null | tr -dc '0-9')
        copt=false; grep -q '^  cache_optimistic: true' /opt/etc/AdGuardHome/AdGuardHome.yaml 2>/dev/null && copt=true
        yt_code=§(echo "§yt" | cut -d'|' -f1); dns_ms=§(echo "§yt" | cut -d'|' -f2 | awk '{print int(§1*1000)}'); yt_ms=§(echo "§yt" | cut -d'|' -f3 | awk '{print int(§1*1000)}')
        dc_code=§(echo "§dc" | cut -d'|' -f1); dc_ms=§(echo "§dc" | cut -d'|' -f2 | awk '{print int(§1*1000)}')
        printf '{"ping_ms":%.1f,"yt_code":%d,"yt_ms":%d,"dns_ms":%d,"dc_code":%d,"dc_ms":%d,"ram_free_mb":%d,"ct_max":%d,"ppe_hw":%s,"ppe_sw":%s,"zram":%s,"cache_opt":%s}\n' \
          "§ping_1" "§{yt_code:-0}" "§{yt_ms:-0}" "§{dns_ms:-0}" "§{dc_code:-0}" "§{dc_ms:-0}" "§{ram_free:-0}" "§{ct_max:-32768}" "§ppe_hw" "§ppe_sw" "§zram" "§copt"
        """
    )

    fun applyOptimizerBundleCmd(
        enableZram: Boolean,
        enablePpeHw: Boolean,
        enablePpeSw: Boolean,
        enableCacheOpt: Boolean,
        boostConntrack: Boolean
    ): String = sh(
        """__LONG__
        ndm_cmds=""
        ${if (enableZram) "LD_LIBRARY_PATH= /bin/ndmc -c 'system zram' >/dev/null 2>&1 || true" else ""}
        ${if (enablePpeHw) "LD_LIBRARY_PATH= /bin/ndmc -c 'ppe hardware' >/dev/null 2>&1 || true" else ""}
        ${if (enablePpeSw) "LD_LIBRARY_PATH= /bin/ndmc -c 'ppe software' >/dev/null 2>&1 || true" else ""}
        ${if (boostConntrack) "LD_LIBRARY_PATH= /bin/ndmc -c 'ip conntrack max-entries 65536' >/dev/null 2>&1 || true" else ""}
        LD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save' >/dev/null 2>&1 || true
        if [ "${if (enableCacheOpt) 1 else 0}" = "1" ] && [ -f /opt/etc/AdGuardHome/AdGuardHome.yaml ]; then
          cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak 2>/dev/null || true
          sed -i 's/^  cache_optimistic: .*/  cache_optimistic: true/' /opt/etc/AdGuardHome/AdGuardHome.yaml
          $AGH_INIT_DETECT
          [ -n "§AGH_INI" ] && "§AGH_INI" restart >/dev/null 2>&1 || true
        fi
        echo "OK: Оптимизации применены"
        """
    )

    val CMD_PROBE_DNS = sh(
        """
        for d in youtube.com discord.com instagram.com sberbank.ru; do
          res=§(nslookup "§d" 127.0.0.1 2>&1 | awk '/^Address/{a=§NF} END{print a}')
          echo "§d -> §{res:-ошибка}"
        done
        """
    )

    fun z2kApiJob(ep: String, postData: String, waitSec: Int = 25): String {
        val url = "http://127.0.0.1:8088/cgi-bin/api/${sq(ep)}"
        val cmd = if (postData.isEmpty()) {
            "curl -sS -m 10 -X POST -H 'Referer: http://127.0.0.1:8088/' '$url'"
        } else {
            "curl -sS -m 12 -X POST -H 'Referer: http://127.0.0.1:8088/' -d '${sq(postData)}' '$url'"
        }
        return sh(
            """__LONG__
            resp=§($cmd 2>&1)
            printf '%s\n' "§resp"
            jid=§(printf '%s' "§resp" | sed -n 's/.*"job":"\([^"]*\)".*/\1/p')
            [ -n "§jid" ] || jid=§(printf '%s' "§resp" | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
            if [ -n "§jid" ]; then
              i=0
              while [ §i -lt $waitSec ]; do
                sleep 1
                jr=§(curl -sS -m 5 "http://127.0.0.1:8088/cgi-bin/api/job?id=§jid" 2>/dev/null || break)
                printf '%s\n' "§jr"
                case "§jr" in *'"done":true'*|*'"status":"done"'*|*'"status":"error"'*) break ;; esac
                i=§((i+1))
              done
            fi
            """
        )
    }

    fun setZ2kConfigKey(key: String, value: String): String = sh(
        """
        CFG="/opt/zapret2/config"
        [ -f "§CFG" ] || { echo "ERR: /opt/zapret2/config не найден"; exit 1; }
        cp -f "§CFG" "§CFG.bak" 2>/dev/null || true
        k='${sq(key)}'; v='${sq(value)}'
        sed -i "/^§{k}=/d" "§CFG"
        printf '%s=%s\n' "§k" "§v" >> "§CFG"
        [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart 2>&1 || true
        echo "OK: §k=§v применён"
        """
    )

    fun toggleZ2k(feature: String, value: Int): String = sh(
        """
        resp=§(curl -sS -m 10 -X POST -H 'Referer: http://127.0.0.1:8088/' -d 'feature=${ue(feature)}&value=$value' 'http://127.0.0.1:8088/cgi-bin/api/toggle' 2>/dev/null || true)
        if printf '%s' "§resp" | grep -q '"ok":true'; then
          echo "§resp"
        else
          CFG="/opt/zapret2/config"
          [ -f "§CFG" ] && cp -f "§CFG" "§CFG.bak" 2>/dev/null || true
          case "${sq(feature)}" in
            ipv6-disable|disable-ipv6)
              [ -f "§CFG" ] && sed -i '/^DISABLE_IPV6=/d' "§CFG" && printf 'DISABLE_IPV6=$value\n' >> "§CFG"
              ;;
            autohostlist)
              [ -f "§CFG" ] && sed -i '/^AUTOHOSTLIST=/d' "§CFG" && printf 'AUTOHOSTLIST=$value\n' >> "§CFG"
              ;;
            game-warp|warp)
              [ -x /opt/zapret2/z2k-warp.sh ] && sh /opt/zapret2/z2k-warp.sh ${if (value == 1) "enable" else "disable"} 2>&1 || true
              ;;
          esac
          [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
          echo "OK: ${sq(feature)}=$value"
        fi
        """
    )

    fun setZ2kModeCmd(mode: String): String = sh(
        """
        resp=§(curl -sS -m 10 -X POST -H 'Referer: http://127.0.0.1:8088/' -d 'mode=${ue(mode)}' 'http://127.0.0.1:8088/cgi-bin/api/mode' 2>/dev/null || true)
        if printf '%s' "§resp" | grep -q '"ok":true'; then
          echo "§resp"
        else
          CFG="/opt/zapret2/config"
          [ -f "§CFG" ] || { echo "ERR: нет /opt/zapret2/config"; exit 1; }
          cp -f "§CFG" "§CFG.bak"
          sed -i '/^MODE=/d' "§CFG"
          printf 'MODE="%s"\n' '${sq(mode)}' >> "§CFG"
          [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart 2>&1 || true
          echo "OK: Режим -> ${sq(mode)}"
        fi
        """
    )

    fun tunnelSetCmd(enable: Boolean): String = z2kApiJob(if (enable) "tunnel/enable" else "tunnel/disable", "", 20)
    fun warpInstallCmd(): String = sh(
        """__LONG__
        if [ -x /opt/zapret2/z2k-warp.sh ]; then
          sh /opt/zapret2/z2k-warp.sh install 2>&1 || sh /opt/zapret2/z2k-warp.sh enable 2>&1
        else
          curl -sS -m 15 -X POST -H 'Referer: http://127.0.0.1:8088/' 'http://127.0.0.1:8088/cgi-bin/api/warp/install' 2>&1
        fi
        """
    )
    fun warpGameToggleCmd(name: String, v: Int): String = z2kApiJob("warp/games/toggle", "name=${ue(name)}&value=$v", 15)
    fun z2kScheduleHourCmd(h: String): String = z2kApiJob("update/schedule", "hour=${ue(h)}", 10)

    /**
     * Valid z2k modes: tcp13 | tcp12 | mixed | quic | voice
     * Uses HTTP panel job API (:8088) with automatic SSH actions.sh fallback.
     */
    fun strategyPickCmd(domain: String, mode: String): String {
        val cleanDomain = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
        val mappedMode = when (mode.lowercase()) {
            "tcp" -> "tcp13"
            "udp" -> "quic"
            "tcp13", "tcp12", "mixed", "quic", "voice" -> mode.lowercase()
            else -> "tcp13"
        }
        return sh(
            """__LONG__
            d='${sq(cleanDomain)}'; m='${sq(mappedMode)}'
            resp=§(curl -sS -m 10 -X POST -H 'Referer: http://127.0.0.1:8088/' -d "domain=${ue(cleanDomain)}&mode=${ue(mappedMode)}" 'http://127.0.0.1:8088/cgi-bin/api/strategy/pick' 2>/dev/null || true)
            jid=§(printf '%s' "§resp" | sed -n 's/.*"job":"\([^"]*\)".*/\1/p')
            [ -n "§jid" ] || jid=§(printf '%s' "§resp" | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
            if [ -n "§jid" ]; then
              i=0
              last=""
              while [ §i -lt 90 ]; do
                sleep 1
                jr=§(curl -sS -m 5 "http://127.0.0.1:8088/cgi-bin/api/job?id=§jid" 2>/dev/null || break)
                last="§jr"
                case "§jr" in *'"done":true'*|*'"status":"done"'*|*'"status":"error"'*) break ;; esac
                i=§((i+1))
              done
              printf '%s\n' "§last"
            elif [ -r /opt/zapret2/webpanel/cgi/actions.sh ]; then
              echo "[INFO] Веб-панель :8088 недоступна — запускаю подбор напрямую через actions.sh..."
              . /opt/zapret2/webpanel/cgi/actions.sh 2>/dev/null || true
              if command -v strategy_pick_run >/dev/null 2>&1; then
                strategy_pick_run "§d" "§m" 2>&1
                command -v strategy_pick_last >/dev/null 2>&1 && strategy_pick_last 2>&1 || true
              else
                echo "ERR: strategy_pick_run не найден в actions.sh"
              fi
            else
              echo "ERR: Панель :8088 не запущена и actions.sh не найден. Включите S96z2k-webpanel в разделе Службы."
            fi
            """
        )
    }

    fun strategyApplyPoolCmd(pool: String, domain: String, strategy: String): String = sh(
        """
        resp=§(curl -sS -m 12 -X POST -H 'Referer: http://127.0.0.1:8088/' -d "pool=${ue(pool)}&domain=${ue(domain)}&strategy=${ue(strategy)}" 'http://127.0.0.1:8088/cgi-bin/api/strategy/pool/save' 2>/dev/null || true)
        if printf '%s' "§resp" | grep -q '"ok":true'; then
          echo "§resp"
        elif [ -r /opt/zapret2/webpanel/cgi/actions.sh ]; then
          . /opt/zapret2/webpanel/cgi/actions.sh 2>/dev/null || true
          if command -v strategy_pool_save >/dev/null 2>&1; then
            strategy_pool_save '${sq(pool)}' '${sq(domain)}' '${sq(strategy)}' 2>&1
          else
            echo "ERR: strategy_pool_save недоступен"
          fi
        else
          echo "§{resp:-ERR: не удалось сохранить стратегию в пул}"
        fi
        """
    )

    val RESET_AUTOCIRCULAR_CMD = sh(
        """
        rm -rf /opt/zapret2/extra_strats/cache/autocircular/* 2>/dev/null || true
        [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart 2>&1 || true
        echo "OK: Кэш autocircular сброшен, nfqws2 перезапущен"
        """
    )

    fun setPolicyCmd(name: String, exclude: Int): String = sh(
        """
        # АРХИТЕКТУРА: POLICY_NAME в zapret2/config включает CONNMARK-exclude в S99zapret2
        # и глушит обход вне указанной политики. Мы его НЕ пишем. Эта команда —
        # очистка остатков от старых установок, а не «применить политику».
        CFG="/opt/zapret2/config"
        if [ -f "§CFG" ]; then
          cp -f "§CFG" "§CFG.bak"
          sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d' "§CFG"
        fi
        [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
        echo "OK: POLICY_NAME/EXCLUDE удалены — обход работает глобально через NFQUEUE на WAN"
        """
    )

    fun addExtraDomain(d: String): String {
        val clean = d.trim().lowercase()
        if (!clean.matches(Regex("""^[a-z0-9.-]+$""")) || !clean.contains('.')) {
            return "echo 'ERR: Некорректный домен'"
        }
        return sh(
            """
            mkdir -p /opt/zapret2/lists
            touch /opt/zapret2/lists/extra-domains.txt
            grep -qxF '${sq(clean)}' /opt/zapret2/lists/extra-domains.txt || printf '%s\n' '${sq(clean)}' >> /opt/zapret2/lists/extra-domains.txt
            curl -sS -m 5 -X POST -H 'Referer: http://127.0.0.1:8088/' -d 'domain=${ue(clean)}' 'http://127.0.0.1:8088/cgi-bin/api/extra-domains/add' >/dev/null 2>&1 || true
            [ -x /opt/etc/init.d/S99zapret2 ] && (/opt/etc/init.d/S99zapret2 reload >/dev/null 2>&1 || /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true)
            echo "OK: Добавлен ${sq(clean)}"
            """
        )
    }

    fun delListDomain(path: String, d: String): String {
        val clean = d.trim().lowercase()
        if (clean.isBlank() || clean.contains('\n') || clean.contains('\r')) {
            return "echo 'ERR: Некорректный домен'"
        }
        return sh(
            """
            p='${sq(path)}'; t='${sq(clean)}'
            [ -f "§p" ] && cp -f "§p" "§p.bak"
            awk -v target="§t" '{gsub(/\r/,""); x=§0; gsub(/^[[:space:]]+|[[:space:]]+§/,"",x); if(x!=target) print §0}' "§p" > "§p.tmp" && mv -f "§p.tmp" "§p"
            [ -x /opt/etc/init.d/S99zapret2 ] && (/opt/etc/init.d/S99zapret2 reload >/dev/null 2>&1 || /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true)
            echo "OK: Удалён §t"
            """
        )
    }

    fun addWhitelistDomain(d: String, wlPath: String = "/opt/zapret2/lists/whitelist.txt"): String {
        val clean = d.trim().lowercase()
        if (!clean.matches(Regex("""^[a-z0-9.-]+$""")) || !clean.contains('.')) {
            return "echo 'ERR: Некорректный домен'"
        }
        return sh(
            """
            mkdir -p /opt/zapret2/lists
            wp='${sq(wlPath)}'
            [ -n "§wp" ] || wp="/opt/zapret2/lists/whitelist.txt"
            touch "§wp"
            grep -qxF '${sq(clean)}' "§wp" || printf '%s\n' '${sq(clean)}' >> "§wp"
            curl -sS -m 5 -X POST -H 'Referer: http://127.0.0.1:8088/' -d 'domain=${ue(clean)}' 'http://127.0.0.1:8088/cgi-bin/api/whitelist/add' >/dev/null 2>&1 || true
            [ -x /opt/etc/init.d/S99zapret2 ] && (/opt/etc/init.d/S99zapret2 reload >/dev/null 2>&1 || /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true)
            echo "OK: ${sq(clean)} добавлен в исключения"
            """
        )
    }

    fun delAutohostDomain(d: String): String = z2kApiJob("autohostlist-domains/delete", "domain=${ue(d)}", 15)

    fun setAghKeyCmd(key: String, enable: Boolean): String {
        val k = sq(key)
        val v = if (enable) "true" else "false"
        return sh(
            """
            [ -f /opt/etc/AdGuardHome/AdGuardHome.yaml ] || { echo "ERR: AdGuardHome.yaml не найден"; exit 1; }
            cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak
            sed -i 's/^  $k: .*/  $k: $v/' /opt/etc/AdGuardHome/AdGuardHome.yaml
            $AGH_INIT_DETECT
            [ -n "§AGH_INI" ] && "§AGH_INI" restart 2>&1 || echo "OK: изменено (перезапустите AdGuard)"
            """
        )
    }

    fun setAghFilterCmd(id: Int, enable: Boolean): String = sh(
        """
        [ -f /opt/etc/AdGuardHome/AdGuardHome.yaml ] || { echo "ERR: AdGuardHome.yaml не найден"; exit 1; }
        cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak
        awk -v fid="$id" -v val="${if (enable) "true" else "false"}" '
          function flush_item(   j, found_en) {
            if (k == 0) return
            if (matched) {
              found_en = 0
              for (j = 1; j <= k; j++) {
                if (arr[j] ~ /^  - enabled:/) { arr[j] = "  - enabled: " val; found_en = 1 }
                else if (arr[j] ~ /^    enabled:/) { arr[j] = "    enabled: " val; found_en = 1 }
              }
              if (!found_en && k >= 1) {
                sub(/^  - /, "    ", arr[1])
                print "  - enabled: " val
              }
            }
            for (j = 1; j <= k; j++) print arr[j]
            k = 0; matched = 0
          }
          /^filters:/ { print; in_f=1; k=0; matched=0; next }
          in_f && /^[a-z_]+:/ { flush_item(); in_f=0; print; next }
          in_f && /^  - / {
            flush_item()
            k = 1; arr[1] = §0
            if (§0 ~ /^  - id:[[:space:]]*/ && §3 == fid) matched = 1
            next
          }
          in_f {
            arr[++k] = §0
            if (/^    id:[[:space:]]*/ && §2 == fid) matched = 1
            next
          }
          { print }
          END { flush_item() }
        ' /opt/etc/AdGuardHome/AdGuardHome.yaml > /tmp/agh_f.yaml && mv -f /tmp/agh_f.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml
        $AGH_INIT_DETECT
        [ -n "§AGH_INI" ] && "§AGH_INI" restart 2>&1 || echo "OK"
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
            [ -f /opt/etc/AdGuardHome/AdGuardHome.yaml ] || { echo "ERR: AdGuardHome.yaml не найден"; exit 1; }
            cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak
            REPS='${sq(clean)}' awk '
              /^  upstream_dns:/ {print; printf "%s\n", ENVIRON["REPS"]; skip=1; next}
              skip && /^    - / {next}
              skip {skip=0}
              {print}
            ' /opt/etc/AdGuardHome/AdGuardHome.yaml > /tmp/agh_new.yaml && mv -f /tmp/agh_new.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml
            $AGH_INIT_DETECT
            [ -n "§AGH_INI" ] && "§AGH_INI" restart 2>&1 || echo "OK"
            """
        )
    }

    fun adguardBootstrapInitCmd(): String = sh(
        """__LONG__
        AGH_BIN="/opt/sbin/AdGuardHome"
        [ -x /opt/bin/AdGuardHome ] && AGH_BIN="/opt/bin/AdGuardHome"
        if [ ! -x "§AGH_BIN" ]; then
          opkg update >/dev/null 2>&1 && opkg install adguardhome-go 2>&1 || true
          [ -x /opt/bin/AdGuardHome ] && AGH_BIN="/opt/bin/AdGuardHome"
        fi
        [ -x "§AGH_BIN" ] || { echo "ERR: Бинарник AdGuardHome не найден"; exit 1; }
        mkdir -p /opt/etc/AdGuardHome /opt/var/log
        if [ ! -s /opt/etc/AdGuardHome/AdGuardHome.yaml ]; then
          cat > /opt/etc/AdGuardHome/AdGuardHome.yaml << 'EOFAGH'
http:
  address: 0.0.0.0:3000
users: []
dns:
  bind_hosts:
    - 0.0.0.0
  port: 53
  upstream_dns:
    - https://dns.quad9.net/dns-query
    - https://cloudflare-dns.com/dns-query
  bootstrap_dns:
    - 9.9.9.9
    - 1.1.1.1
  upstream_mode: parallel
  cache_size: 4194304
  cache_optimistic: true
querylog:
  enabled: true
  interval: 24h
  size_memory: 2000
filters:
  - enabled: true
    url: https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt
    name: AdGuard DNS filter
    id: 1
schema_version: 29
EOFAGH
        fi
        $AGH_INIT_DETECT
        if [ -z "§AGH_INI" ]; then
          AGH_INI="/opt/etc/init.d/S99AdGuardHome"
          {
            printf '#!/bin/sh\nENABLED=yes\nBIN="%s"\nARGS="-c /opt/etc/AdGuardHome/AdGuardHome.yaml -w /opt/etc/AdGuardHome --no-check-update"\n' "§AGH_BIN"
            cat << 'EOFINI'
case "$1" in
  start) ps w 2>/dev/null | grep -q '[A]dGuardHome' || nohup "§BIN" §ARGS >/opt/var/log/AdGuardHome.log 2>&1 & ;;
  stop) killall AdGuardHome 2>/dev/null || true ;;
  restart) $0 stop; sleep 1; $0 start ;;
esac
EOFINI
          } > "§AGH_INI"
          chmod +x "§AGH_INI"
        fi
        "§AGH_INI" restart >/dev/null 2>&1 || "§AGH_INI" start >/dev/null 2>&1 || true
        sleep 2
        if { netstat -ln 2>/dev/null || ss -ln 2>/dev/null; } | grep -q ':53 '; then
          LD_LIBRARY_PATH= /bin/ndmc -c "opkg dns-override" >/dev/null 2>&1
          LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
          echo "OK: AdGuard Home запущен и dns-override включён"
        else
          echo "WARN: AdGuard запущен, но :53 ещё не слушается"
        fi
        """
    )

    fun ndmcSaveCmd(vararg cmds: String): String = sh(
        cmds.joinToString("\n") { "LD_LIBRARY_PATH= /bin/ndmc -c '${sq(it)}'" } +
            "\nLD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save'\necho OK"
    )

    fun addStaticPortCmd(proto: String, extPort: String, localIp: String, localPort: String): String {
        val pr = if (proto.lowercase() == "udp") "udp" else "tcp"
        val ep = extPort.trim().replace("-", " through ")
        val lp = localPort.trim()
        val tail = if (lp.isNotBlank() && lp != extPort.trim() && !ep.contains("through")) {
            "$ep ${localIp.trim()} $lp"
        } else {
            "$ep ${localIp.trim()}"
        }
        return sh(
            """
            wan_if=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | awk '
              /^interface / { cur=§2; inb=1; isw=0; rnm="" }
              inb && /^[ \t]+rename / { rnm=§2 }
              inb && /^[ \t]+ip global / { isw=1 }
              inb && /^!/ { if (isw) { print (rnm!=""?rnm:cur); inb=0; exit } inb=0 }
              END { if (inb && isw) print (rnm!=""?rnm:cur) }
            ')
            [ -n "§wan_if" ] || wan_if="ISP"
            LD_LIBRARY_PATH= /bin/ndmc -c "ip static $pr §wan_if ${sq(tail)}"
            LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
            echo "OK: Проброс $pr (§wan_if) сохранён"
            """
        )
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
        val s = GboxScripts.sanitizeSsid(ssid)
        return sh(
            """
            s='${sq(s)}'
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

    fun setBandSteeringCmd(enable: Boolean): String = sh(
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

    fun setWifiFtCmd(enable: Boolean): String = sh(
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

    fun disableDeadApCmd(apId: String): String = sh(
        """
        ap='${sq(apId)}'
        LD_LIBRARY_PATH= /bin/ndmc -c "interface §ap down" >/dev/null 2>&1 || true
        LD_LIBRARY_PATH= /bin/ndmc -c "no interface §ap" >/dev/null 2>&1 || true
        LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
        echo "OK: Слот §ap очищен"
        """
    )

    fun setHostPolicyRouteCmd(mac: String, policyName: String?): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        return sh(
            """
            m='${sq(cleanMac)}'
            if [ -n '${sq(policyName ?: "")}' ]; then
              LD_LIBRARY_PATH= /bin/ndmc -c "ip hotspot host §m policy ${sq(policyName ?: "")}"
            else
              LD_LIBRARY_PATH= /bin/ndmc -c "no ip hotspot host §m policy"
            fi
            LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
            echo "OK: Политика маршрутизации для §m обновлена"
            """
        )
    }

    fun setHostPriorityCmd(mac: String, prio: Int, conform: Boolean): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        return ndmcSaveCmd(
            "ip hotspot host $cleanMac permit",
            "ip hotspot host $cleanMac priority ${prio.coerceIn(1, 7)}",
            if (conform) "ip hotspot host $cleanMac conform" else "ip hotspot host $cleanMac no conform"
        )
    }

    fun setHostShapeCmd(mac: String, kbps: Int): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        return ndmcSaveCmd(
            if (kbps > 0) "ip hotspot host $cleanMac traffic-shape rate $kbps" else "ip hotspot host $cleanMac no traffic-shape"
        )
    }

    fun setHostTimerCmd(mac: String, minutes: Int, mode: String): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val key = cleanMac.replace(":", "").lowercase()
        if (minutes <= 0) {
            return sh(
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
        return sh(
            """
            pidf="/tmp/gbox_timer_${key}.pid"
            [ -f "§pidf" ] && kill §(cat "§pidf" 2>/dev/null) 2>/dev/null || true
            LD_LIBRARY_PATH= /bin/ndmc -c '${sq(startNdm)}' >/dev/null 2>&1
            LD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save' >/dev/null 2>&1
            exp=§($expireAtCmd 2>/dev/null || echo 0)
            printf '%s|%s|%s\n' '${sq(cleanMac)}' "§exp" '${sq(mode)}' > "/tmp/gbox_timer_${key}.meta"
            (
              sleep $sec
              LD_LIBRARY_PATH= /bin/ndmc -c '${sq(endNdm)}' >/dev/null 2>&1
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
            ndmcSaveCmd(
                "no ip hotspot host $cleanMac schedule",
                "no schedule $schedName"
            )
        } else {
            ndmcSaveCmd(
                "schedule $schedName",
                "schedule $schedName action start 0 ${startHour.coerceIn(0, 23)} *",
                "schedule $schedName action stop 0 ${stopHour.coerceIn(0, 23)} *",
                "ip hotspot host $cleanMac schedule $schedName"
            )
        }
    }

    fun setHostStaticIpCmd(mac: String, ip: String, name: String): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val cmds = mutableListOf<String>()
        if (ip.isNotBlank()) cmds.add("ip dhcp host $cleanMac ${ip.trim()}")
        if (name.isNotBlank()) cmds.add("ip hotspot host $cleanMac name ${name.trim().replace("\"", "")}")
        if (cmds.isEmpty()) return "echo 'ERR: Укажите IP или имя'"
        return ndmcSaveCmd(*cmds.toTypedArray())
    }

    fun svcCmd(id: String, action: String): String = sh(
        """
        scr="/opt/etc/init.d/${sq(id)}"
        if [ ! -x "§scr" ] && [ "${sq(id)}" = "S99AdGuardHome" ] && [ -x /opt/etc/init.d/S99adguardhome ]; then
          scr="/opt/etc/init.d/S99adguardhome"
        fi
        [ -x "§scr" ] || { echo "ERR: скрипт §scr не найден"; exit 1; }
        "§scr" '${sq(action)}' 2>&1
        """
    )

    val CMD_OPKG_CHECK = sh(
        """__LONG__
        export TMPDIR=/opt/tmp; mkdir -p /opt/tmp
        opkg update 2>&1 | tail -n 12
        echo "=== Доступные обновления ==="
        up=§(opkg list-upgradable 2>/dev/null)
        [ -n "§up" ] && printf '%s\n' "§up" || echo "Все пакеты Entware актуальны."
        """
    )

    val CMD_OPKG_UPGRADE_ALL = sh(
        """__LONG__
        export TMPDIR=/opt/tmp; mkdir -p /opt/tmp
        opkg update 2>&1 | tail -n 5
        opkg upgrade 2>&1
        """
    )

    fun opkgSearchCmd(q: String): String = sh(
        "opkg find '*${sq(q.trim())}*' 2>&1 | head -n 40"
    )

    fun opkgInstallCmd(pkg: String): String = sh(
        """__LONG__
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
        "ndms" -> sh("LD_LIBRARY_PATH= /bin/ndmc -c 'show log' 2>/dev/null | tail -n 90 || echo 'Системный журнал пуст'")
        else -> sh("dmesg 2>/dev/null | tail -n 70")
    }

    fun wolCmd(mac: String): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        return sh(
            """
            m='${sq(cleanMac)}'
            if LD_LIBRARY_PATH= /bin/ndmc -c "ip hotspot wake §m" 2>/dev/null | grep -qiE 'error|such command|invalid'; then
              bcast=§(ip -4 addr show br0 2>/dev/null | awk '/brd /{for(i=1;i<=NF;i++) if(§i=="brd"){print §(i+1); exit}}')
              [ -n "§bcast" ] || bcast=§(ip -4 addr show 2>/dev/null | awk '/brd / && /192\.168\.|10\.|172\./{for(i=1;i<=NF;i++) if(§i=="brd"){print §(i+1); exit}}')
              [ -n "§bcast" ] || bcast="255.255.255.255"
              LC_ALL=C awk -v m="§m" 'BEGIN{m=tolower(m);gsub(/:/,"",m);h="0123456789abcdef";p="";for(i=0;i<6;i++)p=p sprintf("%c",255);for(i=1;i<=16;i++)for(j=1;j<=12;j+=2){v=(index(h,substr(m,j,1))-1)*16+(index(h,substr(m,j+1,1))-1);p=p sprintf("%c",v)}printf "%s",p}' | nc -u -w1 "§bcast" 9 2>/dev/null || true
            fi
            echo "OK: WoL отправлен на §m"
            """
        )
    }

    fun listDirCmd(dir: String): String = sh(
        """
        d='${sq(dir)}'
        if [ ! -d "§d" ]; then
          dp=§(printf '%s' "§d" | awk '$AWK_ESC {print esc(§0)}')
          printf '{"path":"%s","err":"Папка не найдена","entries":[]}\n' "§dp"
          exit 0
        fi
        real=§(cd "§d" 2>/dev/null && pwd)
        [ -n "§real" ] || real="§d"
        items=§(ls -la "§real" 2>/dev/null | awk -v REAL="§real" '$AWK_ESC NR>1{perm=§1;sz=§5+0;nm="";for(i=9;i<=NF;i++)nm=(nm==""?§i:nm " " §i);if(nm==""||nm=="."||nm=="..")next;sub(/ -> .*§/,"",nm);tc=substr(perm,1,1);isl=(tc=="l")?"true":"false";isd=(tc=="d")?"true":"false";if(tc=="l"){q=REAL"/"nm; gsub(/'\''/,"'\''\\'\'''\''",q); if(system("test -d '\''" q "'\''")==0) isd="true"}printf "%s{\"name\":\"%s\",\"dir\":%s,\"link\":%s,\"size\":%d,\"perm\":\"%s\"}",(n++?",":""),esc(nm),isd,isl,sz,esc(perm)}')
        rp=§(printf '%s' "§real" | awk '$AWK_ESC {print esc(§0)}')
        printf '{"path":"%s","entries":[%s]}\n' "§rp" "§items"
        """
    )

    fun readFileCmd(path: String): String = sh(
        """
        p='${sq(path)}'
        [ -r "§p" ] || { echo '{"err":"Нет доступа или файл не найден"}'; exit 0; }
        sz=§(wc -c < "§p" 2>/dev/null | head -n1 | tr -dc '0-9')
        [ -n "§sz" ] || sz=0
        trunc=false
        [ "§sz" -gt 32768 ] && trunc=true
        b64=§(head -c 32768 "§p" 2>/dev/null | base64 2>/dev/null | tr -d '\r\n')
        printf '{"size":%s,"trunc":%s,"b64":"%s"}\n' "§sz" "§trunc" "§b64"
        """
    )

    fun writeFileCmd(path: String, b64: String): String = sh(
        """
        p='${sq(path)}'
        [ -f "§p" ] && cp -f "§p" "§p.bak" 2>/dev/null || true
        printf '%s' '${sq(b64)}' | base64 -d > "§p.tmp" 2>/dev/null && mv -f "§p.tmp" "§p" && {
          case "§p" in
            /opt/zapret2/*) [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 reload >/dev/null 2>&1 || true ;;
          esac
          echo "OK: Сохранено (§p, копия .bak)"
        } || {
          rm -f "§p.tmp" 2>/dev/null
          echo "ERR: Ошибка записи §p"
        }
        """
    )

    fun restoreBakFileCmd(path: String): String = sh(
        """
        p='${sq(path)}'
        [ -f "§p.bak" ] || { echo "ERR: Резервная копия .bak не найдена"; exit 1; }
        cp -f "§p.bak" "§p" && echo "OK: Восстановлено из .bak"
        """
    )

    fun mkdirCmd(path: String): String = sh("mkdir -p '${sq(path)}' 2>&1 && echo 'OK: Папка создана'")
    fun touchFileCmd(path: String): String = sh("touch '${sq(path)}' 2>&1 && echo 'OK: Файл создан'")
    fun rmPathCmd(path: String): String = sh("rm -rf '${sq(path)}' 2>&1 && echo 'OK: Удалено'")

    val ENABLE_CONNTRACK_ACCT_CMD = sh(
        """
        if [ -w /proc/sys/net/netfilter/nf_conntrack_acct ]; then
          echo 1 > /proc/sys/net/netfilter/nf_conntrack_acct 2>/dev/null || sysctl -w net.netfilter.nf_conntrack_acct=1 2>&1 || true
          echo "OK: Счётчик байтов conntrack_acct включён"
        else
          echo "INFO: Ядро этой модели Keenetic собрано без nf_conntrack_acct — график Мбит/с и активные потоки работают в обычном режиме."
        fi
        """
    )

    val CMD_TORCH_LIVE = sh(
        """
        PFX="/tmp/.gth_§$"
        snap_dev() { awk 'NR>2{sub(/:/," ",§0); if(§1~/^(eth|br|ra|rai|wg|z2k|tun|ppp)/) printf "%s %s %s %s %s %s %s\n", §1, §2, §3, §4, §10, §11, §12}' /proc/net/dev; }
        a=§(snap_dev)
        sleep 1
        b=§(snap_dev)
        ifaces=§(awk -v A="§a" -v B="§b" '$AWK_ESC BEGIN{
          na=split(A,la,"\n"); for(i=1;i<=na;i++){split(la[i],p," "); r1[p[1]]=p[2]; rp1[p[1]]=p[3]; rd1[p[1]]=p[4]; t1[p[1]]=p[5]; tp1[p[1]]=p[6]; td1[p[1]]=p[7]}
          nb=split(B,lb,"\n"); printf "["
          for(i=1;i<=nb;i++){
            split(lb[i],p," "); dev=p[1]; if(dev=="") continue
            dr=(p[2]-r1[dev])*8/1000; dt=(p[5]-t1[dev])*8/1000
            pps=(p[3]-rp1[dev])+(p[6]-tp1[dev])
            drops=(p[4]-rd1[dev])+(p[7]-td1[dev])
            printf "%s{\"dev\":\"%s\",\"rx_kbps\":%.1f,\"tx_kbps\":%.1f,\"pps\":%.0f,\"drops\":%.0f}", (n++?",":""), esc(dev), (dr<0?0:dr), (dt<0?0:dt), (pps<0?0:pps), (drops<0?0:drops)
          }
          printf "]"
        }')
        LD_LIBRARY_PATH= /bin/ndmc -c "show ip hotspot" 2>/dev/null | awk '/^[[:space:]]*ip:/{ip=§2} /^[[:space:]]*name:/{sub(/^[^:]*:[[:space:]]*/,""); nm=§0; if(ip!="") print ip"="nm; ip=""; nm=""}' > "§{PFX}_hn" 2>/dev/null || : > "§{PFX}_hn"
        : > "§{PFX}_qm"
        if [ -r /opt/etc/AdGuardHome/data/querylog.json ]; then
          tail -n 120 /opt/etc/AdGuardHome/data/querylog.json 2>/dev/null | awk '
            function jstr(line, key,   pat, pos, rest, q) {
              pat="\"" key "\":\""; pos=index(line, pat); if(pos==0) return ""
              rest=substr(line, pos+length(pat)); q=index(rest, "\""); if(q==0) return ""
              return substr(rest, 1, q-1)
            }
            {
              qh=jstr(§0,"QH"); if(qh=="") next
              pos=index(§0,"\"Answer\":[")
              if(pos>0){
                rest=substr(§0, pos)
                if(match(rest, /[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/)){
                  print substr(rest, RSTART, RLENGTH)"="qh
                }
              }
            }' > "§{PFX}_qm" 2>/dev/null || true
        fi
        flows=""
        if [ -r /proc/net/nf_conntrack ]; then
          flows=§(awk -v PFX="§PFX" '$AWK_ESC BEGIN{
            while((getline l < (PFX "_hn")) > 0){ split(l,p,"="); if(p[1]!="") names[p[1]]=p[2] }
            close(PFX "_hn")
            while((getline l < (PFX "_qm")) > 0){ split(l,p,"="); if(p[1]!="") doms[p[1]]=p[2] }
            close(PFX "_qm")
          }
          NR<=220{
            proto=§3; st=""; src=""; dst=""; dport=""; by=0
            for(i=1;i<=NF;i++){
              if(§i ~ /^(ESTABLISHED|SYN_SENT|TIME_WAIT|CLOSE|ASSURED)/) st=§i
              if(§i ~ /^src=/ && src=="") src=substr(§i,5)
              if(§i ~ /^dst=/ && dst=="") dst=substr(§i,5)
              if(§i ~ /^dport=/ && dport=="") dport=substr(§i,7)
              if(§i ~ /^bytes=/) by+=substr(§i,7)+0
            }
            if(src=="") next
            sn=(src in names)?names[src]:src
            dn=(dst in doms)?doms[dst]:dst
            key=sn"|"dst"|"dport"|"proto
            cnt[key]++; bytes[key]+=by; state[key]=(st!=""?st:"UDP"); host[key]=dn
          }
          END{
            printf "["; c=0
            for(k in cnt){
              if(c>=35) break
              split(k, p, "|")
              printf "%s{\"src\":\"%s\",\"dst\":\"%s\",\"host\":\"%s\",\"port\":\"%s\",\"proto\":\"%s\",\"st\":\"%s\",\"conns\":%d,\"bytes\":%.0f}",
                (c++?",":""), esc(p[1]), esc(p[2]), esc(host[k]), esc(p[3]), esc(p[4]), esc(state[k]), cnt[k], bytes[k]
            }
            printf "]"
          }' /proc/net/nf_conntrack 2>/dev/null)
        fi
        rm -f "§{PFX}_hn" "§{PFX}_qm"
        [ -n "§flows" ] || flows='[]'
        if [ -e /proc/sys/net/netfilter/nf_conntrack_acct ]; then
          acct=§(cat /proc/sys/net/netfilter/nf_conntrack_acct 2>/dev/null | tr -dc '0-9')
          [ -n "§acct" ] || acct=0
        else
          acct=-1
        fi
        printf '{"ifaces":%s,"flows":%s,"acct":%s}\n' "§{ifaces:-[]}" "§flows" "§acct"
        """
    )

    fun probeHostCmd(host: String, mode: String): String {
        val h = sq(host.trim().ifEmpty { "youtube.com" })
        return when (mode) {
            "ping" -> sh("ping -c 5 -W 2 '$h' 2>&1")
            "trace" -> sh("traceroute -n -w 2 -m 15 '$h' 2>&1 || tracepath -n '$h' 2>&1")
            "dns" -> sh("nslookup '$h' 127.0.0.1 2>&1")
            "dns_cmp" -> sh(
                """
                echo "=== Сравнение DNS для '$h' ==="
                for srv in "127.0.0.1 (Роутер/AGH)" "1.1.1.1 (Cloudflare)" "8.8.8.8 (Google)" "77.88.8.8 (Yandex)"; do
                  ip="${'$'}{srv%% *}"
                  ans=§(nslookup '$h' "§ip" 2>&1 | awk '/^Address/{a=§NF} END{print a}')
                  echo "§srv -> §{ans:-таймаут/подмена}"
                done
                """
            )
            "dpi" -> sh(
                """
                echo "=== Диагностика DPI / TLS ClientHello: '$h' ==="
                curl -vI --connect-timeout 5 --max-time 8 'https://$h' 2>&1 | grep -iE 'Trying|Connected|SSL connection|ALPN|HTTP/|subject:|issuer:|error|reset|timed out' | head -n 25
                echo "--- Статус NFQUEUE mangle ---"
                iptables -t mangle -L -v -n 2>/dev/null | grep -E 'Chain|NFQUEUE' | head -n 15
                """
            )
            "tcpdump" -> sh(
                """
                if ! command -v tcpdump >/dev/null 2>&1; then
                  echo "tcpdump не установлен. Установите его в разделе Службы -> пакет 'tcpdump'."
                else
                  echo "=== Захват первых 25 пакетов (5с) для '$h' ==="
                  timeout 5 tcpdump -nn -c 25 host '$h' 2>&1 || tcpdump -nn -c 25 2>&1 | head -n 25
                fi
                """
            )
            "http" -> sh("curl -IsS --max-time 6 'https://$h' 2>&1 | head -n 20")
            "conntrack" -> sh("awk '{for(i=1;i<=NF;i++) if(§i~/^src=/){s=substr(§i,5); c[s]++; break}} END{for(k in c) printf \"%5d  %s\\n\", c[k], k}' /proc/net/nf_conntrack 2>/dev/null | sort -rn | head -n 20")
            "ports" -> sh("netstat -tulnp 2>/dev/null || netstat -lnt 2>/dev/null")
            "traffic" -> sh(
                """
                a=§(awk 'NR>2{sub(/:/," ",§0); if(§1~/^(eth|br|ra|rai|wg|z2k|tun|ppp)/) print §1, §2, §10}' /proc/net/dev)
                sleep 1
                b=§(awk 'NR>2{sub(/:/," ",§0); if(§1~/^(eth|br|ra|rai|wg|z2k|tun|ppp)/) print §1, §2, §10}' /proc/net/dev)
                awk -v A="§a" -v B="§b" 'BEGIN{
                  na=split(A,la,"\n"); for(i=1;i<=na;i++){split(la[i],p," "); r1[p[1]]=p[2]; t1[p[1]]=p[3]}
                  nb=split(B,lb,"\n"); for(i=1;i<=nb;i++){split(lb[i],p," "); if(p[1]!=""){dr=(p[2]-r1[p[1]])*8/1000; dt=(p[3]-t1[p[1]])*8/1000; printf "%-14s ↓ %8.1f kbps   ↑ %8.1f kbps\n", p[1], dr, dt}}
                }'
                """
            )
            else -> sh("ping -c 4 -W 2 '$h' 2>&1")
        }
    }
}
