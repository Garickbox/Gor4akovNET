package ru.garickbox.gor4akovnet

object MonitorDiagScripts {
    private const val PATH_HDR = "export PATH=/opt/sbin:/opt/bin:/sbin:/usr/sbin:/bin:/usr/bin"
    private const val AWK_ESC =
        "function esc(s, i,c,o,b,n){gsub(/[\\r\\t]+/,\" \",s);gsub(/\\n/,\" \",s);o=\"\";b=sprintf(\"%c\",92);n=length(s);for(i=1;i<=n;i++){c=substr(s,i,1);if(c==b)o=o b b;else if(c==\"\\\"\")o=o b \"\\\"\";else if(c<\" \")o=o \" \";else o=o c}return o}"

    private fun sh(body: String): String = "$PATH_HDR\n" + body.trimIndent().replace('§', '$')
    private fun sq(s: String): String = s.replace("'", "'\"'\"'")

    fun serviceInspectCmd(initId: String): String {
        val cleanId = initId.trim().substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "")
        if (cleanId.isBlank()) return "echo '{\"err\":\"Некорректный ID службы\"}'"
        return sh(
            """
            id='${sq(cleanId)}'
            ini="/opt/etc/init.d/§id"
            PFX="/tmp/.gi_§§"
            inst=false; [ -x "§ini" ] && inst=true
            exists=false; [ -e "§ini" ] && exists=true
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
              *sing-box*|*singbox*) pat="sing-box" ;;
              *mihomo*|*xkeen*) pat="mihomo" ;;
              *awg*) pat="awg" ;;
            esac
            pkg=§(opkg search "§ini" 2>/dev/null | head -n1 | awk -F' - ' '{print §1}')
            ps w 2>/dev/null > "§{PFX}_ps"
            (netstat -lntp 2>/dev/null || netstat -lnt 2>/dev/null) > "§{PFX}_ns"
            awk -v PAT="§pat" -v PFX="§PFX" '$AWK_ESC BEGIN{
              pat=tolower(PAT); tot_rss=0; pids=""; procs=""; ports=""
              while((getline l < (PFX "_ps")) > 0){
                split(l, a, " ")
                pid=a[1]
                if(pid !~ /^[0-9]+§/) continue
                cmd=""
                cf="/proc/" pid "/cmdline"
                if((getline cmd < cf) > 0) gsub(/\0/," ",cmd)
                close(cf)
                if(cmd==""){
                  for(i=5;i<=length(a);i++) cmd=(cmd==""?a[i]:cmd" "a[i])
                }
                if(pat!="" && index(tolower(cmd), pat)>0){
                  rss=0
                  stf="/proc/" pid "/status"
                  while((getline s < stf) > 0){
                    if(s ~ /^VmRSS:/){ split(s,x," "); rss=x[2]+0 }
                  }
                  close(stf)
                  tot_rss+=rss
                  pids=(pids==""?pid:pids","pid)
                  if(length(cmd)>90) cmd=substr(cmd,1,90)
                  procs=(procs==""?"":procs",") "\"" esc(pid ": " cmd) "\""
                }
              }
              close(PFX "_ps")
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
              printf "%d|%.1f|%s|%s|[%s]", (pids!=""?1:0), tot_rss/1024, esc(pids), esc(ports), procs
            }' > "§{PFX}_res" 2>/dev/null
            res=§(cat "§{PFX}_res" 2>/dev/null)
            up_i=§(echo "§res" | cut -d'|' -f1)
            up=false; [ "§up_i" = "1" ] && up=true
            ram_mb=§(echo "§res" | cut -d'|' -f2)
            pids=§(echo "§res" | cut -d'|' -f3)
            ports=§(echo "§res" | cut -d'|' -f4)
            proc_arr=§(echo "§res" | cut -d'|' -f5-)
            cfg=""; logf=""
            case "§id" in
              *zapret*) cfg="/opt/zapret2/config"; logf="/tmp/zapret2.log" ;;
              *AdGuard*|*adguard*) cfg="/opt/etc/AdGuardHome.yaml"; for f in /opt/var/log/adguardhome.log /opt/var/log/AdGuardHome.log; do [ -f "§f" ] && logf="§f"; done ;;
              *warp*) cfg="/opt/zapret2/warp.conf"; logf="/tmp/z2k-warp.log" ;;
              *tg-tunnel*) logf="/tmp/tg-tunnel.log" ;;
              *http-tunnel*) logf="/tmp/z2k-http-tunnel.log" ;;
              *webpanel*|*lighttpd*) cfg="/opt/zapret2/webpanel/lighttpd.conf"; logf="/opt/var/log/lighttpd/error.log" ;;
              *sing-box*|*singbox*) cfg="/opt/etc/sing-box/config.json"; logf="/opt/var/log/sing-box.log" ;;
              *mihomo*|*xkeen*) cfg="/opt/etc/mihomo/config.yaml"; logf="/opt/var/log/mihomo.log" ;;
              *dropbear*) cfg="/opt/etc/dropbear" ;;
              *cron*) cfg="/opt/var/spool/cron/crontabs/root"; logf="/opt/var/log/cron.log" ;;
            esac
            if [ -z "§logf" ]; then
              for f in "/opt/var/log/§{pat}.log" "/tmp/§{pat}.log" "/opt/var/log/§{id}.log"; do
                [ -f "§f" ] && { logf="§f"; break; }
              done
            fi
            log_tail=""
            if [ -n "§logf" ] && [ -f "§logf" ]; then
              log_tail=§(tail -n 35 "§logf" 2>/dev/null | tr -d '\r' | awk '$AWK_ESC {o=(o==""?esc(§0):o"\\n"esc(§0))} END{print o}')
            fi
            pkg_e=§(printf '%s' "§pkg" | awk '$AWK_ESC {print esc(§0)}')
            cfg_e=§(printf '%s' "§cfg" | awk '$AWK_ESC {print esc(§0)}')
            logf_e=§(printf '%s' "§logf" | awk '$AWK_ESC {print esc(§0)}')
            rm -f "§{PFX}_ps" "§{PFX}_ns" "§{PFX}_res"
            printf '{"id":"%s","exists":%s,"inst":%s,"up":%s,"pkg":"%s","ram_mb":%s,"pids":"%s","ports":"%s","cfg":"%s","log_file":"%s","procs":%s,"log_tail":"%s"}\n' \
              "§id" "§exists" "§inst" "§up" "§pkg_e" "§{ram_mb:-0}" "§pids" "§ports" "§cfg_e" "§logf_e" "§{proc_arr:-[]}" "§log_tail"
            """
        )
    }

    val CMD_STORAGE_DOCTOR = sh(
        """
        opt_line=§(grep ' /opt ' /proc/mounts 2>/dev/null | head -n1)
        opt_mnt=§(echo "§opt_line" | awk '{print §1" ("§3")"}')
        opt_ro=false
        echo "§opt_line" | awk '{split(§4,f,","); for(i in f) if(f[i]=="ro") exit 0; exit 1}' && opt_ro=true
        inodes=§(df -i 2>/dev/null | awk '$AWK_ESC NF==1{d=§0; getline; §0=d" "§0} NR>1{mnt=""; for(i=6;i<=NF;i++) mnt=(mnt==""?§i:mnt" "§i); if(mnt=="/opt" || mnt ~ /^\/(tmp\/mnt|media|mnt)\// || mnt=="/storage"){gsub(/%/,"",§5); printf "%s{\"mnt\":\"%s\",\"itot\":%d,\"iused\":%d,\"ifree\":%d,\"ipct\":%d}", (n++?",":""), esc(mnt), §2+0, §3+0, §4+0, §5+0}}')
        dmesg_errs=§(dmesg 2>/dev/null | grep -iE 'usb.*(error|reset|disconnect|fail|over-current)|ext4.*(error|corrupt|abort|read-only)|scsi.*(error|reset)|i/o error|journal commit' | tail -n 20 | awk '$AWK_ESC BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        [ -n "§dmesg_errs" ] || dmesg_errs='[]'
        top_dirs=§(du -sm /opt/var/log/* /opt/var/* /opt/tmp/* /tmp/*.log 2>/dev/null | sort -rn | head -n 12 | awk '$AWK_ESC BEGIN{printf "["} {mb=§1+0; §1=""; sub(/^[[:space:]]+/,""); printf "%s{\"mb\":%d,\"path\":\"%s\"}", (n++?",":""), mb, esc(§0)} END{printf "]"}')
        [ -n "§top_dirs" ] || top_dirs='[]'
        opt_e=§(printf '%s' "§opt_mnt" | awk '$AWK_ESC {print esc(§0)}')
        printf '{"opt_mount":"%s","opt_ro":%s,"inodes":[%s],"dmesg":%s,"top_dirs":%s}\n' "§opt_e" "§opt_ro" "§inodes" "§dmesg_errs" "§top_dirs"
        """
    )

    val CMD_STORAGE_CLEANUP = sh(
        """
        before=§(df -Pm /opt 2>/dev/null | awk 'NR==2{print §4+0}')
        rm -f /opt/var/log/*.old /opt/var/log/*.1 /opt/var/AdGuardHome/data/querylog.json.1 2>/dev/null || true
        rm -rf /opt/var/cache/opkg/* /opt/tmp/opkg-* 2>/dev/null || true
        for f in /opt/var/log/*.log /tmp/*.log; do
          [ -f "§f" ] || continue
          sz=§(wc -c < "§f" 2>/dev/null || echo 0)
          if [ "§sz" -gt 2097152 ]; then
            tail -c 262144 "§f" > "§{f}.trim" 2>/dev/null && mv -f "§{f}.trim" "§f" || rm -f "§{f}.trim"
          fi
        done
        after=§(df -Pm /opt 2>/dev/null | awk 'NR==2{print §4+0}')
        echo "OK: Очистка завершена. Свободно на /opt: §{before:-0} -> §{after:-0} МБ"
        """
    )

    val CMD_ROUTE_DOCTOR = sh(
        """
        rules=§(ip rule show 2>/dev/null | head -n 30 | awk '$AWK_ESC BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        main_rt=§(ip route show 2>/dev/null | head -n 30 | awk '$AWK_ESC BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        tbls=§(ip rule show 2>/dev/null | awk '{for(i=1;i<=NF;i++) if(§i=="lookup" && §(i+1)!="main" && §(i+1)!="local" && §(i+1)!="default") print §(i+1)}' | sort -u | head -n 8)
        tbl_json=""
        for t in §tbls; do
          lines=§(ip route show table "§t" 2>/dev/null | head -n 12 | awk '$AWK_ESC BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
          t_e=§(printf '%s' "§t" | awk '$AWK_ESC {print esc(§0)}')
          tbl_json="§{tbl_json}§([ -n "§tbl_json" ] && echo ','){\"table\":\"§t_e\",\"routes\":§lines}"
        done
        ndm_pol=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip policy" 2>/dev/null | tr -d '\r' | head -n 45 | awk '$AWK_ESC {o=(o==""?esc(§0):o"\\n"esc(§0))} END{print o}')
        ipv6_glob=§(ip -6 addr show scope global 2>/dev/null | awk '$AWK_ESC /inet6/{printf "%s\"%s\"", (n++?",":""), esc(§2)}' | sed 's/^/[/;s/$/]/')
        [ -n "§ipv6_glob" ] || ipv6_glob='[]'
        egress_json=""
        for ifc in §(ip -o -4 addr show up 2>/dev/null | awk '{print §2}' | grep -vE '^(lo|br|ra|rai|wlan|eth|nwg)' | head -n 6); do
          ip_out=§(curl -fsS --interface "§ifc" --max-time 3 https://api.ipify.org 2>/dev/null || true)
          ifc_e=§(printf '%s' "§ifc" | awk '$AWK_ESC {print esc(§0)}')
          ip_e=§(printf '%s' "§ip_out" | awk '$AWK_ESC {print esc(§0)}')
          egress_json="§{egress_json}§([ -n "§egress_json" ] && echo ','){\"iface\":\"§ifc_e\",\"ip\":\"§ip_e\"}"
        done
        dns_leak=§(nslookup -type=A whoami.akamai.net 127.0.0.1 2>&1 | awk '/^Address/{a=§NF} END{print a}' | tr -dc '0-9.')
        printf '{"rules":%s,"main_routes":%s,"tables":[%s],"ndm_policy":"%s","ipv6":%s,"egress":[%s],"dns_egress":"%s"}\n' \
          "§rules" "§main_rt" "§tbl_json" "§ndm_pol" "§ipv6_glob" "§egress_json" "§dns_leak"
        """
    )

    val CMD_SYSLOG_SUMMARY = sh(
        """
        PFX="/tmp/.gls_§§"
        LD_LIBRARY_PATH= /bin/ndmc -c "show log" 2>/dev/null | tr -d '\r' | tail -n 250 > "§{PFX}_log"
        dmesg 2>/dev/null | tail -n 80 >> "§{PFX}_log"
        awk '$AWK_ESC
          BEGIN { tot=0; wan=0; usb=0; dns=0; auth=0; errs=0 }
          {
            tot++
            l=tolower(§0)
            is_hi=0
            if(l ~ /(\[e\]|error|critical|fail|fatal|panic|oom|killed)/) { errs++; is_hi=1 }
            if(l ~ /(wan|pppoe|link down|dhcp.*fail|no carrier)/) { wan++; is_hi=1 }
            if(l ~ /(usb|ext4|scsi|over-current|read-only|i\/o error)/) { usb++; is_hi=1 }
            if(l ~ /(adguard|dns|nxdomain|servfail|bind.*53)/) { dns++; if(l~/fail|error|warn/) is_hi=1 }
            if(l ~ /(auth|login|dropbear|password|denied)/) { auth++; if(l~/fail|bad|invalid|denied/) is_hi=1 }
            if(is_hi) {
              if(hc < 25) {
                arr[hc++]=§0
              } else {
                for(i=0;i<24;i++) arr[i]=arr[i+1]
                arr[24]=§0
              }
            }
          }
          END {
            printf "{\"total\":%d,\"errors\":%d,\"wan\":%d,\"usb\":%d,\"dns\":%d,\"auth\":%d,\"highlights\":[", tot, errs, wan, usb, dns, auth
            for(i=0;i<hc;i++) printf "%s\"%s\"", (i?",":""), esc(arr[i])
            printf "]}\n"
          }
        ' "§{PFX}_log" 2>/dev/null
        rm -f "§{PFX}_log"
        """
    )

    val CMD_APP_CENTER_STATE = sh(
        """
        arch=§(uname -m 2>/dev/null || echo unknown)
        ent=false; [ -x /opt/bin/opkg ] && ent=true
        free_mb=§(df -Pm /opt 2>/dev/null | awk 'NR==2{print §4+0}')
        [ -n "§free_mb" ] || free_mb=0
        PFX="/tmp/.gac_§§"
        opkg list-installed 2>/dev/null > "§{PFX}_opkg"
        ps w 2>/dev/null > "§{PFX}_ps"
        chk_app() {
          id="§1"; bins="§2"; pkgs="§3"; inis="§4"; proc="§5"
          inst=false; ver=""; run=false; ini_f=""
          for b in §(echo "§bins" | tr '|' ' '); do
            if command -v "§b" >/dev/null 2>&1 || [ -x "/opt/bin/§b" ] || [ -x "/opt/sbin/§b" ]; then inst=true; break; fi
          done
          for p in §(echo "§pkgs" | tr '|' ' '); do
            line=§(grep -m1 "^§p - " "§{PFX}_opkg" 2>/dev/null || true)
            if [ -n "§line" ]; then inst=true; ver=§(echo "§line" | awk -F' - ' '{print §2}'); break; fi
          done
          for pat in §(echo "§inis" | tr '|' ' '); do
            for f in /opt/etc/init.d/§pat; do
              [ -e "§f" ] && { ini_f=§(basename "§f"); [ -x "§f" ] && inst=true; break; }
            done
            [ -n "§ini_f" ] && break
          done
          if [ -n "§proc" ] && grep -iE "§proc" "§{PFX}_ps" | grep -qv grep; then run=true; fi
          printf '{"id":"%s","inst":%s,"run":%s,"ver":"%s","init":"%s"}' "§id" "§inst" "§run" "§ver" "§ini_f"
        }
        items=""
        for spec in \
          "adguard:AdGuardHome:adguardhome:S99AdGuardHome|S99adguardhome:AdGuardHome" \
          "singbox:sing-box:sing-box:S*sing-box*:sing-box" \
          "mihomo:mihomo|xkeen:mihomo|xkeen:S*mihomo*|S*xkeen*:mihomo|xray" \
          "amneziawg:awg:amneziawg-tools:S*awg*:awg" \
          "iperf3:iperf3:iperf3::iperf3" \
          "tcpdump:tcpdump:tcpdump::tcpdump" \
          "mtr:mtr:mtr-tiny|mtr::" \
          "htop:htop:htop::" \
          "mc:mc:mc::" \
          "nano:nano:nano::"
        do
          IFS=':' read -r a1 a2 a3 a4 a5 <<EOF
§spec
EOF
          item=§(chk_app "§a1" "§a2" "§a3" "§a4" "§a5")
          items="§{items}§([ -n "§items" ] && echo ',')§item"
        done
        rm -f "§{PFX}_opkg" "§{PFX}_ps"
        printf '{"arch":"%s","entware":%s,"free_mb":%s,"apps":[%s]}\n' "§arch" "§ent" "§free_mb" "§items"
        """
    )

    fun appCenterActionCmd(appId: String, action: String): String {
        val pkgs = when (appId) {
            "singbox" -> listOf("sing-box")
            "mihomo" -> listOf("mihomo", "xkeen")
            "amneziawg" -> listOf("amneziawg-tools")
            "iperf3" -> listOf("iperf3")
            "tcpdump" -> listOf("tcpdump")
            "mtr" -> listOf("mtr-tiny", "mtr")
            "htop" -> listOf("htop")
            "mc" -> listOf("mc")
            "nano" -> listOf("nano")
            else -> emptyList()
        }
        val initGlobs = when (appId) {
            "adguard" -> "S99AdGuardHome S99adguardhome"
            "singbox" -> "S*sing-box*"
            "mihomo" -> "S*mihomo* S*xkeen*"
            "amneziawg" -> "S*awg*"
            else -> ""
        }
        return when (action) {
            "install" -> {
                if (pkgs.isEmpty()) return "echo 'ERR: Для $appId используйте встроенный установщик'"
                val tries = pkgs.joinToString(" || ") { "opkg install '${sq(it)}'" }
                sh(
                    """__LONG__
                    echo "=== Обновление индексов opkg ==="
                    opkg update 2>&1 | tail -n 12
                    echo "=== Установка $appId ==="
                    ($tries) 2>&1 | tail -n 25
                    """
                )
            }
            "remove" -> {
                if (pkgs.isEmpty()) return "echo 'ERR: Пакет $appId не управляется через opkg remove'"
                val rmCmds = pkgs.joinToString("; ") { "opkg remove '${sq(it)}' 2>&1 || true" }
                sh(
                    """
                    for pat in $initGlobs; do
                      for f in /opt/etc/init.d/§pat; do [ -x "§f" ] && "§f" stop >/dev/null 2>&1 || true; done
                    done
                    $rmCmds
                    echo "OK: Удаление завершено"
                    """
                )
            }
            "start", "stop", "restart" -> {
                if (initGlobs.isBlank()) return "echo 'CLI-утилита $appId запускается из терминала'"
                sh(
                    """
                    done_any=0
                    for pat in $initGlobs; do
                      for f in /opt/etc/init.d/§pat; do
                        if [ -x "§f" ]; then
                          "§f" '${sq(action)}' 2>&1
                          done_any=1
                        fi
                      done
                    done
                    [ "§done_any" = "1" ] || echo "ERR: init-скрипт для $appId не найден"
                    """
                )
            }
            else -> "echo 'Неизвестное действие'"
        }
    }

    fun usbFormatExt4Cmd(devPath: String): String {
        val clean = devPath.trim()
        if (!clean.matches(Regex("""^/dev/sd[a-z][0-9]*$"""))) {
            return "echo 'ERR: Укажите раздел /dev/sdX1'"
        }
        return sh(
            """__LONG__
            dev='${sq(clean)}'
            if grep -q "^§dev /opt " /proc/mounts 2>/dev/null; then
              echo "ERR: Раздел §dev сейчас смонтирован как /opt — форматирование запрещено!"
              exit 1
            fi
            mnt=§(awk -v d="§dev" '§1==d{print §2; exit}' /proc/mounts 2>/dev/null)
            if [ -n "§mnt" ]; then
              umount "§mnt" 2>&1 || umount -l "§mnt" 2>&1 || true
            fi
            if ! command -v mkfs.ext4 >/dev/null 2>&1 && ! command -v mke2fs >/dev/null 2>&1; then
              opkg update >/dev/null 2>&1 && opkg install e2fsprogs >/dev/null 2>&1 || true
            fi
            if command -v mkfs.ext4 >/dev/null 2>&1; then
              mkfs.ext4 -F -L OPKG "§dev" 2>&1
            elif command -v mke2fs >/dev/null 2>&1; then
              mke2fs -t ext4 -F -L OPKG "§dev" 2>&1
            else
              echo "ERR: mkfs.ext4 не найден (установите пакет e2fsprogs)"
              exit 1
            fi
            echo "OK: Раздел §dev отформатирован в ext4 (метка OPKG)"
            """
        )
    }
}
