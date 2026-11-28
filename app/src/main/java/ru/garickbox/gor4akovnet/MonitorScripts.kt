package ru.garickbox.gor4akovnet

object MonitorScripts {

    val Z2K_UPDATE_CMD = MonitorShellSupport.sh(
        """__LONG__
        if curl -fsS -m 15 -X POST -H "§ZH" "§ZB/cgi-bin/api/update/apply" 2>/dev/null | grep -q '"ok":true'; then
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

    fun buildOverviewCmd(forceUpdates: Boolean): String = MonitorShellSupport.sh(
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
        zst=§(curl -fsS -m 3 §ZB/cgi-bin/api/status 2>/dev/null | tr -d '\r\n' || echo '')
        case "§zst" in '{'*'}') ;; *)
          nfq_c=§(printf '%s\n' "§ps_w" | grep -c '[n]fqws2' | head -n1 | tr -dc '0-9')
          zmode=§(awk -F= '/^(MODE|Z2K_PRESET)=/{gsub(/["'"'"']/,"",§2); print §2; exit}' /opt/zapret2/config 2>/dev/null | tr -d '\r\n"\\')
          zst="{\"ok\":true,\"running\":§([ "§{nfq_c:-0}" -gt 0 ] && echo true || echo false),\"mode\":\"§{zmode:-auto}\"}"
          ;;
        esac
        if [ "${if (forceUpdates) "1" else "0"}" = "1" ]; then
          z2u=§(curl -fsS -m 8 -X POST -H "§ZH" "§ZB/cgi-bin/api/update/check" 2>/dev/null | tr -d '\r\n' || echo '{"ok":false}')
        else
          z2u=§(curl -fsS -m 3 "§ZB/cgi-bin/api/update/status" 2>/dev/null | tr -d '\r\n' || echo '{"ok":false}')
        fi
        case "§z2u" in '{'*'}') ;; *) z2u='{"ok":false}' ;; esac
        # Справочник NDMS 5.1: 3.178.71 show ip service, 3.178.9 show cifs,
        # 3.178.65 show ip http webdav, 3.178.66 show ip name-server
        svc_srv=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip service" 2>/dev/null | tr -d '\r')
        svc_cifs=§(LD_LIBRARY_PATH= /bin/ndmc -c "show cifs" 2>/dev/null | tr -d '\r')
        svc_webdav=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip http webdav" 2>/dev/null | tr -d '\r')
        svc_dns=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip name-server" 2>/dev/null | tr -d '\r')
        esc_json() { printf '%s' "§1" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr '\n' '~' | tr -d '\r'; }
        svc_srv_j=§(esc_json "§svc_srv")
        svc_cifs_j=§(esc_json "§svc_cifs")
        svc_webdav_j=§(esc_json "§svc_webdav")
        svc_dns_j=§(esc_json "§svc_dns")
        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        # Тумблеры служб из running-config (мануал 3.15x-3.17x): service ssh/ftp/cifs/
        # dlna/upnp/torrent + ip http webdav enable. ВАЖНО: читает §rc ПОСЛЕ его объявления.
        svc_toggles=§(printf '%s\n' "§rc" | awk '
          /^service ssh$/ {s="1"} /^service ftp$/ {f="1"} /^service cifs$/ {c="1"}
          /^service dlna$/ {d="1"} /^service upnp$/ {u="1"} /^service torrent$/ {t="1"}
          /^service telnet$/ {tn="1"} /^service igmp-proxy$/ {ig="1"}
          /^ip http webdav$/ {inweb=1; next}
          inweb && /^[[:space:]]+enable$/ {w="1"; inweb=0; next}
          inweb && /^[^[:space:]]/ {inweb=0}
          END{printf "{\"ssh\":%s,\"ftp\":%s,\"cifs\":%s,\"dlna\":%s,\"upnp\":%s,\"torrent\":%s,\"telnet\":%s,\"igmp\":%s,\"webdav\":%s}", (s=="1"?"true":"false"), (f=="1"?"true":"false"), (c=="1"?"true":"false"), (d=="1"?"true":"false"), (u=="1"?"true":"false"), (t=="1"?"true":"false"), (tn=="1"?"true":"false"), (ig=="1"?"true":"false"), (w=="1"?"true":"false")}
        ')
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
        printf '{"sys":{"model":"%s","ndms":"%s","up_sec":%s,"load":"%s","cpus":%s,"ct":%s,"ct_max":%s,"mem":%s,"disk":%s,"temp24":"%s","temp5":"%s","zram":%s},"wan":{"dev":"%s","ip":"%s","spd":"%s","gw":"%s","policy_ok":%s,"policy_name":"%s","policy_mode":"%s","nfq_rules":%s,"ent_ok":%s,"agh_inst":%s,"dns_ov":%s},"svc":{"nfqws2":%s,"gvsub":%s,"adguard":%s,"tg_tunnel":%s,"http_tunnel":%s,"warp":%s,"z2k_panel":%s,"scheduler":%s},"ver":{"z2k_local":"%s","agh":"%s","z2u":%s},"z2k":%s,"wifi":{"ssid":"%s","pass":"%s","bridge":"%s","subnet":"%s"},"srv":{"ip_service":"%s","cifs":"%s","webdav":"%s","dns":"%s"},"tog":%s}\n' \
          "§model" "§ndms" "§up_sec" "§load" "§cpu_cnt" "§ct_cnt" "§ct_max" "§mem_json" "§disk_json" "§t24" "§t5" "§zram_on" \
          "§wan_dev" "§wan" "§wan_spd" "§wan_gw" "§pol_ok" "§pol_name" "§pol_mode" "§nfq_rules" "§ent_ok" "§agh_inst" "§dns_ov" \
          "§(has_ps '[n]fqws2')" "§(has_ps 'z2k-detect')" "§agh_up" \
          "§(has_pt 1443)" "§(has_pt 1444)" "§(has_ps 'z2k-warpd')" "§(has_pt 8088)" \
          "§(has_ps 'z2k-scheduler')" \
          "§z2k_loc" "§agh_loc" "§z2u" "§zst" \
          "§wifi_ssid" "§wifi_pass" "§wifi_bridge" "§wifi_subnet" \
          "§svc_srv_j" "§svc_cifs_j" "§svc_webdav_j" "§svc_dns_j" "§svc_toggles"
        """
    )

    val CMD_BYPASS_STATE = MonitorShellSupport.sh(
        """
        panel_up=true
        if command -v nc >/dev/null 2>&1; then
          nc -z -w 1 "§PH" 8088 2>/dev/null || panel_up=false
        fi
        cp_get() { if [ "§panel_up" = true ]; then curl -fsS "§@" 2>/dev/null | tr -d '\000-\037' || echo ''; else echo ''; fi; }
        zst=§(cp_get -m 4 §ZB/cgi-bin/api/status)
        case "§zst" in '{'*'}') ;; *)
          zrun=false
          if pgrep -f nfqws2 >/dev/null 2>&1; then zrun=true
          elif ps w 2>/dev/null | grep -q '[n]fqws2'; then zrun=true
          elif ps 2>/dev/null | grep -q '[n]fqws2'; then zrun=true
          fi
          zcfg_json=§(ZRUN="§zrun" awk -F= '${MonitorShellSupport.AWK_ESC} /^([A-Z0-9_]+)=/{k=§1; v=§2; gsub(/^["'"'"']|["'"'"']§/,"",v); if(k=="MODE"||k=="Z2K_PRESET") md=v; if(k=="DISABLE_IPV6") ipv6=v; if(k=="POLICY_NAME") pol=v; if(k=="POLICY_EXCLUDE") pex=v; if(k=="AUTOHOSTLIST") ahl=v} END{printf "{\"ok\":true,\"direct\":true,\"running\":%s,\"mode\":\"%s\",\"no_ipv6\":%s,\"policy\":\"%s\",\"policy_exclude\":%d,\"autohost\":%s}", ENVIRON["ZRUN"], esc(md==""?"auto":md), (ipv6=="1"?"true":"false"), esc(pol), pex+0, (ahl=="1"?"true":"false")}' /opt/zapret2/config 2>/dev/null)
          zst="§{zcfg_json:-{\"ok\":true,\"direct\":true}}"
          ;;
        esac
        wst=§(cp_get -m 4 §ZB/cgi-bin/api/warp/status)
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
        pst=§(cp_get -m 3 §ZB/cgi-bin/api/policy/status)
        case "§pst" in '{'*) ;; *)
          pol_nm=§(awk -F= '/^POLICY_NAME=/{gsub(/["'"'"'\r\n]/,"",§2); print §2; exit}' /opt/zapret2/config 2>/dev/null)
          pol_ex=§(awk -F= '/^POLICY_EXCLUDE=/{print §2+0; exit}' /opt/zapret2/config 2>/dev/null | head -n1 | tr -dc '0-9')
          pst="{\"ok\":true,\"name\":\"§{pol_nm:-Gor4akovNET}\",\"exclude\":§{pol_ex:-0}}"
          ;;
        esac
        ahl=§(cp_get -m 3 §ZB/cgi-bin/api/autohostlist-domains)
        case "§ahl" in '{'*) ;; *) ahl='{"ok":false,"domains":[]}' ;; esac
        list_to_json() {
          if [ -r "§1" ]; then
            awk '${MonitorShellSupport.AWK_ESC} BEGIN{printf "["} {gsub(/\r/,"");sub(/#.*/,"");gsub(/^[[:space:]]+|[[:space:]]+§/,""); if(§0!=""){printf "%s\"%s\"", (n++?",":""), esc(§0)}} END{printf "]"}' "§1"
          else
            printf '[]'
          fi
        }
        ex_arr=§(list_to_json /opt/zapret2/lists/extra-domains.txt)
        wl_file="/opt/zapret2/lists/whitelist.txt"
        [ -r /opt/zapret2/lists/exclude.txt ] && [ ! -s "§wl_file" ] && wl_file="/opt/zapret2/lists/exclude.txt"
        wl_arr=§(list_to_json "§wl_file")
        raw_cfg=§(head -n 200 /opt/zapret2/config 2>/dev/null | awk '${MonitorShellSupport.AWK_ESC} BEGIN{printf "["} /^[A-Z0-9_]+=/{printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        [ -n "§raw_cfg" ] || raw_cfg='[]'
        nfq_pkts=§(iptables -t mangle -L -v -n 2>/dev/null | awk '${MonitorShellSupport.AWK_ESC} /NFQUEUE/{a[++n]=sprintf("%s pkts | %s B | %s", §1, §2, esc(§0))} END{printf "["; for(i=1;i<=n;i++)printf "%s\"%s\"", (i>1?",":""), a[i]; printf "]"}')
        [ -n "§nfq_pkts" ] || nfq_pkts='[]'
        # Извлечь реальные пулы из config или extra_strats
        pool_info=§(grep -hE '^(RKN_TCP|YT_TCP|GV_TCP|YT_QUIC|DISCORD_UDP|AUTOCIRCULAR)=' /opt/zapret2/config /opt/zapret2/extra_strats/pool*.conf 2>/dev/null | head -n 20 | awk '${MonitorShellSupport.AWK_ESC} BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        [ -n "§pool_info" ] || pool_info='[]'
        printf '{"z2k":%s,"warp":%s,"policy":%s,"autohost":%s,"extra":%s,"whitelist":%s,"wl_file":"%s","cfg_lines":%s,"nfq_pkts":%s,"pool_info":%s}\n' \
          "§zst" "§wst" "§pst" "§ahl" "§ex_arr" "§wl_arr" "§wl_file" "§raw_cfg" "§nfq_pkts" "§pool_info"
        """
    )

    val CMD_WARP_FULL = MonitorShellSupport.sh(
        """
        panel_up=true
        if command -v nc >/dev/null 2>&1; then nc -z -w 1 "§PH" 8088 2>/dev/null || panel_up=false; fi
        cp_get() { if [ "§panel_up" = true ]; then curl -fsS "§@" 2>/dev/null | tr -d '\000-\037' || echo ''; else echo ''; fi; }

        wst=§(cp_get -m 4 §ZB/cgi-bin/api/warp/status)
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

        wgames=§(cp_get -m 4 §ZB/cgi-bin/api/warp/games)
        case "§wgames" in '{'*'}') ;; *) wgames='{"ok":false,"games":[]}' ;; esac

        wlists=§(cp_get -m 4 §ZB/cgi-bin/api/warp/lists)
        case "§wlists" in '{'*'}') ;; *)
          if [ -r /opt/zapret2/lists/warp-domains.txt ]; then
            wlists=§(awk '${MonitorShellSupport.AWK_ESC} BEGIN{printf "{\"ok\":true,\"domains\":["} {gsub(/\r/,"");sub(/#.*/,"");gsub(/^[[:space:]]+|[[:space:]]+§/,""); if(§0!="") printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]}"}' /opt/zapret2/lists/warp-domains.txt 2>/dev/null)
          fi
          case "§wlists" in '{'*'}') ;; *) wlists='{"ok":true,"domains":[]}' ;; esac
          ;;
        esac

        wexcl=§(cp_get -m 4 §ZB/cgi-bin/api/warp/exclude)
        case "§wexcl" in '{'*'}') ;; *)
          if [ -r /opt/zapret2/lists/warp-exclude.txt ]; then
            wexcl=§(awk '${MonitorShellSupport.AWK_ESC} BEGIN{printf "{\"ok\":true,\"domains\":["} {gsub(/\r/,"");sub(/#.*/,"");gsub(/^[[:space:]]+|[[:space:]]+§/,""); if(§0!="") printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]}"}' /opt/zapret2/lists/warp-exclude.txt 2>/dev/null)
          fi
          case "§wexcl" in '{'*'}') ;; *) wexcl='{"ok":true,"domains":[]}' ;; esac
          ;;
        esac

        wdevs=§(cp_get -m 4 §ZB/cgi-bin/api/warp/devices)
        case "§wdevs" in '{'*'}') ;; *) wdevs='{"ok":false,"devices":[]}' ;; esac

        tun_ip=§(ip -4 addr show z2ktun0 2>/dev/null | awk '/inet /{print §2; exit}' | tr -d '\r\n"\\')
        tun_mtu=§(cat /sys/class/net/z2ktun0/mtu 2>/dev/null | tr -dc '0-9')
        w_ram=§(ps w 2>/dev/null | awk '/[z]2k-warpd/{pid=§1; exit} END{if(pid!=""){while((getline l < ("/proc/"pid"/status"))>0){if(l~/^VmRSS:/){split(l,p," "); printf "%.1f", p[2]/1024; exit}}}}')
        printf '{"status":%s,"games":%s,"lists":%s,"exclude":%s,"devices":%s,"tun_ip":"%s","tun_mtu":"%s","ram_mb":"%s"}\n' \
          "§wst" "§wgames" "§wlists" "§wexcl" "§wdevs" "§tun_ip" "§tun_mtu" "§w_ram"
        """
    )

    val CMD_WARP_TRACE = MonitorShellSupport.sh(
        """
        echo "=== Проверка канала через Cloudflare WARP ==="
        if ip link show z2ktun0 >/dev/null 2>&1; then
          curl -fsSL --interface z2ktun0 --connect-timeout 5 --max-time 8 https://cloudflare.com/cdn-cgi/trace 2>&1 || echo "Не удалось связаться через z2ktun0"
        else
          echo "Интерфейс z2ktun0 не поднят — включите WARP."
        fi
        """
    )

    fun warpTransportCmd(mode: String): String = MonitorShellSupport.sh(
        """
        m='${MonitorShellSupport.sq(mode)}'
        resp=§(curl -sS -m 10 -X POST -H "§ZH" -d "transport=${MonitorShellSupport.ue(mode)}" "§ZB/cgi-bin/api/warp/transport" 2>/dev/null || true)
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

    fun warpReregisterCmd(): String = MonitorShellSupport.sh(
        """__LONG__
        resp=§(curl -sS -m 20 -X POST -H "§ZH" "§ZB/cgi-bin/api/warp/reregister" 2>/dev/null || true)
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
        return MonitorShellSupport.sh(
            """
            d='${MonitorShellSupport.sq(clean)}'; f='${MonitorShellSupport.sq(file)}'
            resp=§(curl -sS -m 8 -X POST -H "§ZH" -d "domain=${MonitorShellSupport.ue(clean)}" "§ZB/cgi-bin/api/$ep" 2>/dev/null || true)
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
        z2kApiJob("warp/devices/toggle", "ip=${MonitorShellSupport.ue(ipOrMac)}&enabled=${if (enable) 1 else 0}", 12)


    fun z2kApiJob(ep: String, postData: String, waitSec: Int = 25): String {
        val cleanEp = ep.trim().trimStart('/').replace(Regex("""["'\s`$\\;]"""), "")
        val hasPost = postData.isNotEmpty()
        return MonitorShellSupport.sh(
            """__LONG__
            _ep='${MonitorShellSupport.sq(cleanEp)}'
            _pd='${MonitorShellSupport.sq(postData)}'
            _url="§ZB/cgi-bin/api/§_ep"
            if [ "${if (hasPost) 1 else 0}" = "1" ]; then
              resp=§(curl -sS -m 12 -X POST -H 'X-Z2K-Panel: 1' -d "§_pd" "§_url" 2>&1)
            else
              resp=§(curl -sS -m 10 -X POST -H 'X-Z2K-Panel: 1' "§_url" 2>&1)
            fi
            printf '%s\n' "§resp"
            jid=§(printf '%s' "§resp" | sed -n 's/.*"job":"\([^"]*\)".*/\1/p')
            [ -n "§jid" ] || jid=§(printf '%s' "§resp" | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
            if [ -n "§jid" ]; then
              i=0
              while [ §i -lt $waitSec ]; do
                sleep 1
                jr=§(curl -sS -m 5 "§ZB/cgi-bin/api/job?id=§jid" 2>/dev/null || break)
                printf '%s\n' "§jr"
                case "§jr" in *'"done":true'*|*'"status":"done"'*|*'"status":"error"'*) break ;; esac
                i=§((i+1))
              done
            fi
            """
        )
    }

    fun setZ2kConfigKey(key: String, value: String): String = MonitorShellSupport.sh(
        """
        CFG="/opt/zapret2/config"
        [ -f "§CFG" ] || { echo "ERR: /opt/zapret2/config не найден"; exit 1; }
        cp -f "§CFG" "§CFG.bak" 2>/dev/null || true
        k='${MonitorShellSupport.sq(key)}'; v='${MonitorShellSupport.sq(value)}'
        sed -i "/^§{k}=/d" "§CFG"
        printf '%s=%s\n' "§k" "§v" >> "§CFG"
        [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart 2>&1 || true
        echo "OK: §k=§v применён"
        """
    )

    fun toggleZ2k(feature: String, value: Int): String = MonitorShellSupport.sh(
        """
        resp=§(curl -sS -m 10 -X POST -H "§ZH" -d 'feature=${MonitorShellSupport.ue(feature)}&value=$value' "§ZB/cgi-bin/api/toggle" 2>/dev/null || true)
        if printf '%s' "§resp" | grep -q '"ok":true'; then
          echo "§resp"
        else
          CFG="/opt/zapret2/config"
          [ -f "§CFG" ] && cp -f "§CFG" "§CFG.bak" 2>/dev/null || true
          case "${MonitorShellSupport.sq(feature)}" in
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
          echo "OK: ${MonitorShellSupport.sq(feature)}=$value"
        fi
        """
    )

    fun setZ2kModeCmd(mode: String): String = MonitorShellSupport.sh(
        """
        resp=§(curl -sS -m 10 -X POST -H "§ZH" -d 'mode=${MonitorShellSupport.ue(mode)}' "§ZB/cgi-bin/api/mode" 2>/dev/null || true)
        if printf '%s' "§resp" | grep -q '"ok":true'; then
          echo "§resp"
        else
          CFG="/opt/zapret2/config"
          [ -f "§CFG" ] || { echo "ERR: нет /opt/zapret2/config"; exit 1; }
          cp -f "§CFG" "§CFG.bak"
          sed -i '/^MODE=/d' "§CFG"
          printf 'MODE="%s"\n' '${MonitorShellSupport.sq(mode)}' >> "§CFG"
          [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart 2>&1 || true
          echo "OK: Режим -> ${MonitorShellSupport.sq(mode)}"
        fi
        """
    )

    fun tunnelSetCmd(enable: Boolean): String = z2kApiJob(if (enable) "tunnel/enable" else "tunnel/disable", "", 20)
    fun warpInstallCmd(): String = MonitorShellSupport.sh(
        """__LONG__
        if [ -x /opt/zapret2/z2k-warp.sh ]; then
          sh /opt/zapret2/z2k-warp.sh install 2>&1 || sh /opt/zapret2/z2k-warp.sh enable 2>&1
        else
          curl -sS -m 15 -X POST -H "§ZH" "§ZB/cgi-bin/api/warp/install" 2>&1
        fi
        """
    )
    fun warpGameToggleCmd(name: String, v: Int): String = z2kApiJob("warp/games/toggle", "name=${MonitorShellSupport.ue(name)}&value=$v", 15)
    fun z2kScheduleHourCmd(h: String): String = z2kApiJob("update/schedule", "hour=${MonitorShellSupport.ue(h)}", 10)

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
        return MonitorShellSupport.sh(
            """__LONG__
            d='${MonitorShellSupport.sq(cleanDomain)}'; m='${MonitorShellSupport.sq(mappedMode)}'
            resp=§(curl -sS -m 10 -X POST -H "§ZH" -d "domain=${MonitorShellSupport.ue(cleanDomain)}&mode=${MonitorShellSupport.ue(mappedMode)}" "§ZB/cgi-bin/api/strategy/pick" 2>/dev/null || true)
            jid=§(printf '%s' "§resp" | sed -n 's/.*"job":"\([^"]*\)".*/\1/p')
            [ -n "§jid" ] || jid=§(printf '%s' "§resp" | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
            if [ -z "§jid" ]; then
              re=§(printf '%s' "§resp" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
              printf '{"ok":false,"error":"панель не приняла запрос: %s"}\n' "§{re:-пустой ответ}"
              exit 0
            fi
            i=0; last=""
            while [ §i -lt 120 ]; do
              sleep 1
              jr=§(curl -sS -m 5 -H "§ZH" "§ZB/cgi-bin/api/job?id=§jid" 2>/dev/null || true)
              [ -n "§jr" ] && last="§jr"
              case "§jr" in *'"done":true'*) break ;; esac
              case "§jr" in *'"status":"error"'*) break ;; esac
              i=§((i+1))
            done
            pick=§(curl -sS -m 8 -H "§ZH" "§ZB/cgi-bin/api/strategy/pick" 2>/dev/null || true)
            str=§(printf '%s' "§pick" | sed -n 's/.*"strategy"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
            verdict=§(printf '%s' "§pick" | sed -n 's/.*"verdict"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
            reason=§(printf '%s' "§pick" | sed -n 's/.*"reason"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
            target=§(printf '%s' "§pick" | sed -n 's/.*"target"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
            st=§(printf '%s' "§last" | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
            logb=§(printf '%s' "§last" | sed -n 's/.*"log"[[:space:]]*:[[:space:]]*"\(.*\)".*/\1/p' | head -n1)
            str_e=§(printf '%s' "§str" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
            verdict_e=§(printf '%s' "§verdict" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
            reason_e=§(printf '%s' "§reason" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
            target_e=§(printf '%s' "§target" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
            log_e=§(printf '%s' "§logb" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
            printf '{"ok":true,"status":"%s","mode":"%s","target":"%s","strategy":"%s","verdict":"%s","reason":"%s","log":"%s"}\n' \
              "§{st:-unknown}" "§m" "§target_e" "§str_e" "§verdict_e" "§reason_e" "§log_e"
            """
        )
    }

    fun strategyApplyPoolCmd(pool: String, domain: String, strategy: String): String = MonitorShellSupport.sh(
        """
        pool='${MonitorShellSupport.sq(pool)}'
        pe=§(printf '%s' "§pool" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
        tf="/tmp/z2k-strat-§§.txt"
        printf '%s' '${MonitorShellSupport.sq(strategy)}' > "§tf" 2>/dev/null || true
        if [ ! -s "§tf" ]; then
          printf '{"ok":false,"pool":"%s","error":"пустая стратегия — нечего сохранять"}\n' "§pe"
          exit 0
        fi
        resp=§(curl -sS -m 20 -X POST -H "§ZH" --data-binary "@§tf" "§ZB/cgi-bin/api/strategy/pool/save?pool=${MonitorShellSupport.ue(pool)}" 2>/dev/null || true)
        rm -f "§tf"
        if printf '%s' "§resp" | grep -q '"ok":true'; then
          printf '{"ok":true,"pool":"%s","saved":true}\n' "§pe"
        else
          re=§(printf '%s' "§resp" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
          printf '{"ok":false,"pool":"%s","error":"%s"}\n' "§pe" "§{re:-пустой ответ}"
        fi
        """
    )

    val RESET_AUTOCIRCULAR_CMD = MonitorShellSupport.sh(
        """
        rm -rf /opt/zapret2/extra_strats/cache/autocircular/* 2>/dev/null || true
        [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart 2>&1 || true
        echo "OK: Кэш autocircular сброшен, nfqws2 перезапущен"
        """
    )

    fun setPolicyCmd(name: String, exclude: Int): String = MonitorShellSupport.sh(
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
        return MonitorShellSupport.sh(
            """
            mkdir -p /opt/zapret2/lists
            touch /opt/zapret2/lists/extra-domains.txt
            grep -qxF '${MonitorShellSupport.sq(clean)}' /opt/zapret2/lists/extra-domains.txt || printf '%s\n' '${MonitorShellSupport.sq(clean)}' >> /opt/zapret2/lists/extra-domains.txt
            curl -sS -m 5 -X POST -H "§ZH" -d 'domain=${MonitorShellSupport.ue(clean)}' "§ZB/cgi-bin/api/extra-domains/add" >/dev/null 2>&1 || true
            [ -x /opt/etc/init.d/S99zapret2 ] && (/opt/etc/init.d/S99zapret2 reload >/dev/null 2>&1 || /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true)
            echo "OK: Добавлен ${MonitorShellSupport.sq(clean)}"
            """
        )
    }

    fun delListDomain(path: String, d: String): String {
        val clean = d.trim().lowercase()
        if (clean.isBlank() || clean.contains('\n') || clean.contains('\r')) {
            return "echo 'ERR: Некорректный домен'"
        }
        return MonitorShellSupport.sh(
            """
            p='${MonitorShellSupport.sq(path)}'; t='${MonitorShellSupport.sq(clean)}'
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
        return MonitorShellSupport.sh(
            """
            mkdir -p /opt/zapret2/lists
            wp='${MonitorShellSupport.sq(wlPath)}'
            [ -n "§wp" ] || wp="/opt/zapret2/lists/whitelist.txt"
            touch "§wp"
            grep -qxF '${MonitorShellSupport.sq(clean)}' "§wp" || printf '%s\n' '${MonitorShellSupport.sq(clean)}' >> "§wp"
            curl -sS -m 5 -X POST -H "§ZH" -d 'domain=${MonitorShellSupport.ue(clean)}' "§ZB/cgi-bin/api/whitelist/add" >/dev/null 2>&1 || true
            [ -x /opt/etc/init.d/S99zapret2 ] && (/opt/etc/init.d/S99zapret2 reload >/dev/null 2>&1 || /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true)
            echo "OK: ${MonitorShellSupport.sq(clean)} добавлен в исключения"
            """
        )
    }

    fun delAutohostDomain(d: String): String = z2kApiJob("autohostlist-domains/delete", "domain=${MonitorShellSupport.ue(d)}", 15)


    fun svcCmd(id: String, action: String): String = MonitorShellSupport.sh(
        """
        scr="/opt/etc/init.d/${MonitorShellSupport.sq(id)}"
        if [ ! -x "§scr" ] && [ "${MonitorShellSupport.sq(id)}" = "S99AdGuardHome" ] && [ -x /opt/etc/init.d/S99adguardhome ]; then
          scr="/opt/etc/init.d/S99adguardhome"
        fi
        [ -x "§scr" ] || { echo "ERR: скрипт §scr не найден"; exit 1; }
        "§scr" '${MonitorShellSupport.sq(action)}' 2>&1
        """
    )

    val CMD_OPKG_CHECK = MonitorShellSupport.sh(
        """__LONG__
        export TMPDIR=/opt/tmp; mkdir -p /opt/tmp
        opkg update 2>&1 | tail -n 12
        echo "=== Доступные обновления ==="
        up=§(opkg list-upgradable 2>/dev/null)
        [ -n "§up" ] && printf '%s\n' "§up" || echo "Все пакеты Entware актуальны."
        """
    )

    val CMD_OPKG_UPGRADE_ALL = MonitorShellSupport.sh(
        """__LONG__
        export TMPDIR=/opt/tmp; mkdir -p /opt/tmp
        opkg update 2>&1 | tail -n 5
        opkg upgrade 2>&1
        """
    )

    fun opkgSearchCmd(q: String): String = MonitorShellSupport.sh(
        "opkg find '*${MonitorShellSupport.sq(q.trim())}*' 2>&1 | head -n 40"
    )

    fun opkgInstallCmd(pkg: String): String = MonitorShellSupport.sh(
        """__LONG__
        export TMPDIR=/opt/tmp; mkdir -p /opt/tmp
        opkg install '${MonitorShellSupport.sq(pkg.trim())}' 2>&1
        """
    )

    fun opkgRemoveCmd(pkg: String): String = MonitorShellSupport.sh(
        "opkg remove '${MonitorShellSupport.sq(pkg.trim())}' 2>&1"
    )

    fun fetchLog(src: String): String = when (src) {
        "agh" -> MonitorShellSupport.sh("tail -n 90 /opt/var/log/AdGuardHome.log 2>/dev/null || echo 'Лог пуст'")
        "sched" -> MonitorShellSupport.sh("tail -n 90 /opt/var/log/z2k-scheduler.log 2>/dev/null || echo 'Лог пуст'")
        "au" -> MonitorShellSupport.sh("tail -n 90 /opt/var/log/z2k-auto-update.log 2>/dev/null || echo 'Лог пуст'")
        "tg" -> MonitorShellSupport.sh("tail -n 90 /tmp/z2k-log/tg-tunnel.log 2>/dev/null || echo 'Лог пуст'")
        "http" -> MonitorShellSupport.sh("tail -n 90 /tmp/z2k-log/z2k-http-tunnel.log 2>/dev/null || echo 'Лог пуст'")
        "warp" -> MonitorShellSupport.sh("tail -n 90 /tmp/z2k-log/z2k-warp.log /tmp/z2k-warp.log 2>/dev/null || echo 'Лог WARP пуст'")
        "circ" -> MonitorShellSupport.sh("tail -n 90 /opt/zapret2/extra_strats/cache/autocircular/debug.log 2>/dev/null || echo 'Лог autocircular пуст'")
        "ndms" -> MonitorShellSupport.sh("LD_LIBRARY_PATH= /bin/ndmc -c 'show log' 2>/dev/null | tail -n 90 || echo 'Системный журнал пуст'")
        else -> MonitorShellSupport.sh("dmesg 2>/dev/null | tail -n 70")
    }

    fun wolCmd(mac: String): String {
        val cleanMac = GboxScripts.sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        return MonitorShellSupport.sh(
            """
            m='${MonitorShellSupport.sq(cleanMac)}'
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

    fun listDirCmd(dir: String): String = MonitorShellSupport.sh(
        """
        d='${MonitorShellSupport.sq(dir)}'
        if [ ! -d "§d" ]; then
          dp=§(printf '%s' "§d" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
          printf '{"path":"%s","err":"Папка не найдена","entries":[]}\n' "§dp"
          exit 0
        fi
        real=§(cd "§d" 2>/dev/null && pwd)
        [ -n "§real" ] || real="§d"
        items=§(ls -la "§real" 2>/dev/null | awk -v REAL="§real" '${MonitorShellSupport.AWK_ESC} NR>1{perm=§1;sz=§5+0;nm="";for(i=9;i<=NF;i++)nm=(nm==""?§i:nm " " §i);if(nm==""||nm=="."||nm=="..")next;sub(/ -> .*§/,"",nm);tc=substr(perm,1,1);isl=(tc=="l")?"true":"false";isd=(tc=="d")?"true":"false";if(tc=="l"){q=REAL"/"nm; gsub(/'\''/,"'\''\\'\'''\''",q); if(system("test -d '\''" q "'\''")==0) isd="true"}printf "%s{\"name\":\"%s\",\"dir\":%s,\"link\":%s,\"size\":%d,\"perm\":\"%s\"}",(n++?",":""),esc(nm),isd,isl,sz,esc(perm)}')
        rp=§(printf '%s' "§real" | awk '${MonitorShellSupport.AWK_ESC} {print esc(§0)}')
        printf '{"path":"%s","entries":[%s]}\n' "§rp" "§items"
        """
    )

    fun readFileCmd(path: String): String = MonitorShellSupport.sh(
        """
        p='${MonitorShellSupport.sq(path)}'
        [ -r "§p" ] || { echo '{"err":"Нет доступа или файл не найден"}'; exit 0; }
        sz=§(wc -c < "§p" 2>/dev/null | head -n1 | tr -dc '0-9')
        [ -n "§sz" ] || sz=0
        trunc=false
        [ "§sz" -gt 32768 ] && trunc=true
        b64=§(head -c 32768 "§p" 2>/dev/null | base64 2>/dev/null | tr -d '\r\n')
        printf '{"size":%s,"trunc":%s,"b64":"%s"}\n' "§sz" "§trunc" "§b64"
        """
    )

    fun writeFileCmd(path: String, b64: String): String = MonitorShellSupport.sh(
        """
        p='${MonitorShellSupport.sq(path)}'
        [ -f "§p" ] && cp -f "§p" "§p.bak" 2>/dev/null || true
        printf '%s' '${MonitorShellSupport.sq(b64)}' | base64 -d > "§p.tmp" 2>/dev/null && mv -f "§p.tmp" "§p" && {
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

    fun restoreBakFileCmd(path: String): String = MonitorShellSupport.sh(
        """
        p='${MonitorShellSupport.sq(path)}'
        [ -f "§p.bak" ] || { echo "ERR: Резервная копия .bak не найдена"; exit 1; }
        cp -f "§p.bak" "§p" && echo "OK: Восстановлено из .bak"
        """
    )

    fun mkdirCmd(path: String): String = MonitorShellSupport.sh("mkdir -p '${MonitorShellSupport.sq(path)}' 2>&1 && echo 'OK: Папка создана'")
    fun touchFileCmd(path: String): String = MonitorShellSupport.sh("touch '${MonitorShellSupport.sq(path)}' 2>&1 && echo 'OK: Файл создан'")
    fun rmPathCmd(path: String): String = MonitorShellSupport.sh("rm -rf '${MonitorShellSupport.sq(path)}' 2>&1 && echo 'OK: Удалено'")



    val CMD_DNS_NET: String get() = MonitorDnsWanScripts.CMD_DNS_NET
    val CMD_WIFI_DEVICES: String get() = MonitorWifiHostScripts.CMD_WIFI_DEVICES
    val CMD_WAN_NET: String get() = MonitorDnsWanScripts.CMD_WAN_NET
    val CMD_SERVICES: String get() = MonitorDnsWanScripts.CMD_SERVICES
    fun addCronJobCmd(expr: String, cmd: String): String = MonitorDnsWanScripts.addCronJobCmd(expr, cmd)
    fun delCronJobCmd(exactLine: String): String = MonitorDnsWanScripts.delCronJobCmd(exactLine)
    val CMD_BENCH_WAN: String get() = MonitorDnsWanScripts.CMD_BENCH_WAN
    val CMD_PROBE_SITES_JSON: String get() = MonitorDnsWanScripts.CMD_PROBE_SITES_JSON
    val CMD_BENCH_SNAPSHOT: String get() = MonitorDnsWanScripts.CMD_BENCH_SNAPSHOT
    fun applyOptimizerBundleCmd(
        enableZram: Boolean,
        enablePpeHw: Boolean,
        enablePpeSw: Boolean,
        enableCacheOpt: Boolean,
        boostConntrack: Boolean
    ): String = MonitorDnsWanScripts.applyOptimizerBundleCmd(enableZram, enablePpeHw, enablePpeSw, enableCacheOpt, boostConntrack)
    val CMD_PROBE_DNS: String get() = MonitorDnsWanScripts.CMD_PROBE_DNS

    fun setAghKeyCmd(key: String, enable: Boolean): String = MonitorDnsWanScripts.setAghKeyCmd(key, enable)
    fun setAghFilterCmd(id: Int, enable: Boolean): String = MonitorDnsWanScripts.setAghFilterCmd(id, enable)
    fun setAghDnsProfile(profile: String): String = MonitorDnsWanScripts.setAghDnsProfile(profile)
    fun setAghCustomUpstreamsCmd(rawLines: String): String = MonitorDnsWanScripts.setAghCustomUpstreamsCmd(rawLines)
    fun adguardBootstrapInitCmd(): String = MonitorDnsWanScripts.adguardBootstrapInitCmd()
    fun ndmcSaveCmd(vararg cmds: String): String = MonitorShellSupport.ndmcSaveCmd(*cmds)
    fun addStaticPortCmd(proto: String, extPort: String, localIp: String, localPort: String): String =
        MonitorDnsWanScripts.addStaticPortCmd(proto, extPort, localIp, localPort)
    fun delStaticPortCmd(fullRule: String): String = MonitorDnsWanScripts.delStaticPortCmd(fullRule)

    fun setWifiChannelCmd(masterIdx: Int, ch: String, bw: String): String = MonitorWifiHostScripts.setWifiChannelCmd(masterIdx, ch, bw)
    fun setWifiSsidCmd(ssid: String): String = MonitorWifiHostScripts.setWifiSsidCmd(ssid)
    fun setBandSteeringCmd(enable: Boolean): String = MonitorWifiHostScripts.setBandSteeringCmd(enable)
    fun setWifiFtCmd(enable: Boolean): String = MonitorWifiHostScripts.setWifiFtCmd(enable)
    fun disableDeadApCmd(apId: String): String = MonitorWifiHostScripts.disableDeadApCmd(apId)
    fun setHostPolicyRouteCmd(mac: String, policyName: String?): String = MonitorWifiHostScripts.setHostPolicyRouteCmd(mac, policyName)
    fun setHostPriorityCmd(mac: String, prio: Int, conform: Boolean): String = MonitorWifiHostScripts.setHostPriorityCmd(mac, prio, conform)
    fun setHostShapeCmd(mac: String, kbps: Int): String = MonitorWifiHostScripts.setHostShapeCmd(mac, kbps)
    fun setHostTimerCmd(mac: String, minutes: Int, mode: String): String = MonitorWifiHostScripts.setHostTimerCmd(mac, minutes, mode)
    fun setHostScheduleCmd(mac: String, startHour: Int, stopHour: Int, enable: Boolean): String = MonitorWifiHostScripts.setHostScheduleCmd(mac, startHour, stopHour, enable)
    fun setHostStaticIpCmd(mac: String, ip: String, name: String): String = MonitorWifiHostScripts.setHostStaticIpCmd(mac, ip, name)

    val ENABLE_CONNTRACK_ACCT_CMD: String get() = MonitorDnsWanScripts.ENABLE_CONNTRACK_ACCT_CMD
    val CMD_TORCH_LIVE: String get() = MonitorDnsWanScripts.CMD_TORCH_LIVE
    fun probeHostCmd(host: String, mode: String): String = MonitorDnsWanScripts.probeHostCmd(host, mode)

}
