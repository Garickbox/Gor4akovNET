package ru.garickbox.gor4akovnet

internal object MonitorDnsWanScripts {

    val CMD_DNS_NET = MonitorShellSupport.sh(
        """
        agh_up=false
        (ps w 2>/dev/null || ps 2>/dev/null) | grep -q '[A]dGuardHome' && agh_up=true
        agh_port=§(awk -F: '/^  address:/{p=§NF; gsub(/[^0-9]/,"",p); if(p!="") print p; exit}' /opt/etc/AdGuardHome/AdGuardHome.yaml 2>/dev/null | tr -dc '0-9')
        [ -n "§agh_port" ] || agh_port=3000
        agh_meta=""
        if [ -r /opt/etc/AdGuardHome/AdGuardHome.yaml ]; then
          agh_meta=§(awk '
            ${MonitorShellSupport.AWK_ESC}
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
          ${MonitorShellSupport.AWK_ESC}
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
        errs=§(grep '\[error\]' /opt/var/log/AdGuardHome.log 2>/dev/null | tail -n 5 | tr -d '\r' | awk '${MonitorShellSupport.AWK_ESC} BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(substr(§0,1,220))} END{printf "]"}')
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
        ip_static=§(printf '%s\n' "§rc" | awk '${MonitorShellSupport.AWK_ESC} /^ip static /{a[++n]=esc(§0)} END{printf "["; for(i=1;i<=n;i++)printf "%s\"%s\"", (i>1?",":""), a[i]; printf "]"}')
        [ -n "§ip_static" ] || ip_static='[]'
        # Мануал 3.178.126 (стр. 751): show upnp redirect — блоки "entry:" с полями
        # index/protocol/port/to-address/to-port/description/policy/packets/bytes.
        upnp_raw=§(LD_LIBRARY_PATH= /bin/ndmc -c "show upnp redirect" 2>/dev/null | tr -d '\r')
        upnp_list=§(printf '%s\n' "§upnp_raw" | awk '
          /^[[:space:]]*entry:/ { if(d!=""){emit()} p="";t="";tp="";d="";i2="" }
          /^[[:space:]]*protocol:/{p=§2}
          /^[[:space:]]*port:/{t=§2}
          /^[[:space:]]*to-address:/{a=§2}
          /^[[:space:]]*to-port:/{tp=§2}
          /^[[:space:]]*description:/{sub(/^[[:space:]]*description:[[:space:]]*/,"");d=§0}
          function emit(){ if(d!=""||a!=""){ s=sprintf("%s → %s:%s (%s/%s)", d, a, tp, p, t); if(n<20) a2[++n]=esc(s) }; p="";t="";tp="";d="";a="" }
          END{ if(d!=""||a!=""){ s=sprintf("%s → %s:%s (%s/%s)", d, a, tp, p, t); if(n<20) a2[++n]=esc(s) }; printf "["; for(i2=1;i2<=n;i2++)printf "%s\"%s\"", (i2>1?",":""), a2[i2]; printf "]" }
        ')
        [ -n "§upnp_list" ] || upnp_list='[]'
        printf '{"agh_up":%s,"agh_port":%s,"agh":%s,"qcnt":%s,"qlog":%s,"errs":%s,"ndms":%s,"ip_static":%s,"upnp_list":%s}\n' \
          "§agh_up" "§agh_port" "§agh_meta" "§qcnt" "§qlog" "§errs" "§ndms_json" "§ip_static" "§upnp_list"
        """
    )

    val CMD_WAN_NET = MonitorShellSupport.sh(
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
        ndm_wan=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip route default" 2>/dev/null | awk '${MonitorShellSupport.AWK_ESC} /interface:|gateway:|metric:/{gsub(/^[[:space:]]+/,""); o=(o?o " • ":"") §0} END{print esc(o)}')
        pol_info=§(LD_LIBRARY_PATH= /bin/ndmc -c "show ip policy" 2>/dev/null | awk '${MonitorShellSupport.AWK_ESC} tolower(§0)~/gor4akovnet/{f=1} f{gsub(/^[[:space:]]+/,""); o=(o?o "\n":"") §0; if(++n>=6)exit} END{print esc(o)}')
        printf '{"dev":"%s","wan":"%s","gw":"%s","spd":"%s","rx":%s,"tx":%s,"dns":"%s","ndm_wan":"%s","pol_info":"%s"}\n' \
          "§wan_dev" "§wan" "§gw" "§spd" "§{rx:-0}" "§{tx:-0}" "§dns" "§ndm_wan" "§pol_info"
        """
    )

    val CMD_SERVICES = MonitorShellSupport.sh(
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
          res=§(awk -v PAT="§pat" -v MEM="§mem_tot_kb" -v PFX="§PFX" '${MonitorShellSupport.AWK_ESC} BEGIN{
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

        top_procs=§(awk '${MonitorShellSupport.AWK_ESC} {split(§0,a,"|"); if(a[2]+0>512){c=a[4]; if(length(c)>55) c=substr(c,1,55); printf "%08d|%s|%.1f|%s|%s\n", a[2]+0, a[1], a[2]/1024, a[3], esc(c)}}' "§{PFX}_pr" 2>/dev/null | sort -rn | head -n 10 | awk 'BEGIN{printf "["}{split(§0,p,"|"); printf "%s{\"pid\":\"%s\",\"ram_mb\":%s,\"cpu\":%s,\"cmd\":\"%s\"}", (n++?",":""), p[2], p[3]+0, p[4]+0, p[5]} END{printf "]"}')
        [ -n "§top_procs" ] || top_procs='[]'

        cron_jobs=§(grep -hvE '^[[:space:]]*$|^[[:space:]]*#' /opt/var/spool/cron/crontabs/root /opt/etc/crontab 2>/dev/null | head -n 25 | awk '${MonitorShellSupport.AWK_ESC} BEGIN{printf "["} {printf "%s\"%s\"", (n++?",":""), esc(§0)} END{printf "]"}')
        [ -n "§cron_jobs" ] || cron_jobs='[]'

        rc=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        cifs=false; printf '%s\n' "§rc" | grep -q '^service cifs§' && cifs=true
        webdav=false; printf '%s\n' "§rc" | awk '/^ip http webdav§/{inw=1; next} /^!/{inw=0} inw && /^[[:space:]]*enable§/{f=1} inw && /^[[:space:]]*no enable§/{f=0} /^webdav enable§/{f=1} END{exit !f}' && webdav=true
        zram=false; printf '%s\n' "§rc" | grep -q '^[[:space:]]*zram§' && zram=true
        usb2=false; printf '%s\n' "§rc" | grep -q 'dev\.usb\.force_usb2 1' && usb2=true
        opkg_cnt=§(opkg list-installed 2>/dev/null | wc -l | head -n1 | tr -dc '0-9')
        [ -n "§opkg_cnt" ] || opkg_cnt=0
        disks=§(df -Pm 2>/dev/null | awk '${MonitorShellSupport.AWK_ESC} NF==1{d=§0; getline; §0=d" "§0} NR>1{mnt=""; for(i=6;i<=NF;i++) mnt=(mnt==""?§i:mnt" "§i); if(mnt=="/opt" || mnt ~ /^\/(tmp\/mnt|media|mnt)\// || mnt=="/storage"){gsub(/%/,"",§5); printf "%s{\"mnt\":\"%s\",\"tot\":%d,\"used\":%d,\"avail\":%d,\"pct\":%d}", (n++?",":""), esc(mnt), §2+0, §3+0, §4+0, §5+0}}')
        rm -f "§{PFX}_ps" "§{PFX}_ns" "§{PFX}_pr"
        printf '{"svcs":[%s],"top_procs":%s,"cron":%s,"mem_tot_mb":%d,"mem_free_mb":%d,"ndms":{"cifs":%s,"webdav":%s,"zram":%s,"usb2":%s},"opkg_cnt":%s,"disks":[%s]}\n' \
          "§svcs_json" "§top_procs" "§cron_jobs" "§((mem_tot_kb/1024))" "§{mem_free_mb:-0}" "§cifs" "§webdav" "§zram" "§usb2" "§opkg_cnt" "§disks"
        """
    )

    fun addCronJobCmd(expr: String, cmd: String): String = MonitorShellSupport.sh(
        """
        mkdir -p /opt/var/spool/cron/crontabs
        cf="/opt/var/spool/cron/crontabs/root"
        touch "§cf"
        printf '%s %s\n' '${MonitorShellSupport.sq(expr.trim())}' '${MonitorShellSupport.sq(cmd.trim())}' >> "§cf"
        [ -x /opt/etc/init.d/S10cron ] && /opt/etc/init.d/S10cron restart >/dev/null 2>&1 || true
        echo "OK: Задача Cron добавлена"
        """
    )

    fun delCronJobCmd(exactLine: String): String = MonitorShellSupport.sh(
        """
        cf="/opt/var/spool/cron/crontabs/root"
        [ -f "§cf" ] && awk -v t='${MonitorShellSupport.sq(exactLine)}' '{if(§0!=t) print §0}' "§cf" > "§cf.tmp" && mv -f "§cf.tmp" "§cf"
        [ -x /opt/etc/init.d/S10cron ] && /opt/etc/init.d/S10cron restart >/dev/null 2>&1 || true
        echo "OK: Задача Cron удалена"
        """
    )

    val CMD_BENCH_WAN = MonitorShellSupport.sh(
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

    val CMD_PROBE_SITES_JSON = MonitorShellSupport.sh(
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
        awk '${MonitorShellSupport.AWK_ESC} BEGIN{printf "{\"sites\":["} {
          split(§0, p, "|")
          code=p[3]+0; dns=int((p[4]+0)*1000); tls=int((p[5]+0)*1000); tot=int((p[6]+0)*1000)
          ok=(code>=200 && code<500)?"true":"false"
          printf "%s{\"cat\":\"%s\",\"host\":\"%s\",\"code\":%d,\"dns_ms\":%d,\"tls_ms\":%d,\"tot_ms\":%d,\"ok\":%s}",
            (n++?",":""), esc(p[1]), esc(p[2]), code, dns, tls, tot, ok
        } END{printf "]}"}' "§TMPP"
        rm -f "§TMPP"
        """
    )

    val CMD_BENCH_SNAPSHOT = MonitorShellSupport.sh(
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
    ): String = MonitorShellSupport.sh(
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
          ${MonitorShellSupport.AGH_INIT_DETECT}
          [ -n "§AGH_INI" ] && "§AGH_INI" restart >/dev/null 2>&1 || true
        fi
        echo "OK: Оптимизации применены"
        """
    )

    val CMD_PROBE_DNS = MonitorShellSupport.sh(
        """
        for d in youtube.com discord.com instagram.com sberbank.ru; do
          res=§(nslookup "§d" 127.0.0.1 2>&1 | awk '/^Address/{a=§NF} END{print a}')
          echo "§d -> §{res:-ошибка}"
        done
        """
    )

    fun setAghKeyCmd(key: String, enable: Boolean): String {
        val k = MonitorShellSupport.sq(key)
        val v = if (enable) "true" else "false"
        return MonitorShellSupport.sh(
            """
            [ -f /opt/etc/AdGuardHome/AdGuardHome.yaml ] || { echo "ERR: AdGuardHome.yaml не найден"; exit 1; }
            cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak
            sed -i 's/^  $k: .*/  $k: $v/' /opt/etc/AdGuardHome/AdGuardHome.yaml
            ${MonitorShellSupport.AGH_INIT_DETECT}
            [ -n "§AGH_INI" ] && "§AGH_INI" restart 2>&1 || echo "OK: изменено (перезапустите AdGuard)"
            """
        )
    }

    fun setAghFilterCmd(id: Int, enable: Boolean): String = MonitorShellSupport.sh(
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
        ${MonitorShellSupport.AGH_INIT_DETECT}
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
        return MonitorShellSupport.sh(
            """
            [ -f /opt/etc/AdGuardHome/AdGuardHome.yaml ] || { echo "ERR: AdGuardHome.yaml не найден"; exit 1; }
            cp -f /opt/etc/AdGuardHome/AdGuardHome.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml.bak
            REPS='${MonitorShellSupport.sq(clean)}' awk '
              /^  upstream_dns:/ {print; printf "%s\n", ENVIRON["REPS"]; skip=1; next}
              skip && /^    - / {next}
              skip {skip=0}
              {print}
            ' /opt/etc/AdGuardHome/AdGuardHome.yaml > /tmp/agh_new.yaml && mv -f /tmp/agh_new.yaml /opt/etc/AdGuardHome/AdGuardHome.yaml
            ${MonitorShellSupport.AGH_INIT_DETECT}
            [ -n "§AGH_INI" ] && "§AGH_INI" restart 2>&1 || echo "OK"
            """
        )
    }

    fun adguardBootstrapInitCmd(): String = MonitorShellSupport.sh(
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
        ${MonitorShellSupport.AGH_INIT_DETECT}
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
        if [ -f /opt/etc/AdGuardHome/AdGuardHome.yaml ]; then
          sed -i 's/bind_hosts: *\[ *127\.0\.0\.1 *\]/bind_hosts:\n    - 0.0.0.0/; s/^    - 127\.0\.0\.1$/    - 0.0.0.0/' /opt/etc/AdGuardHome/AdGuardHome.yaml 2>/dev/null || true
        fi
        AGH_DNS_PORT=§(awk '/^dns:/{f=1;next} f&&/^[^ ]/{f=0} f&&/^  port:/{print §2; exit}' /opt/etc/AdGuardHome/AdGuardHome.yaml 2>/dev/null | tr -dc '0-9')
        [ -n "§AGH_DNS_PORT" ] || AGH_DNS_PORT=53
        AGH_WEB_PORT=§(awk -F: '/^  address:/{p=§NF; gsub(/[^0-9]/,"",p); if(p!="") print p; exit}' /opt/etc/AdGuardHome/AdGuardHome.yaml 2>/dev/null | tr -dc '0-9')
        [ -n "§AGH_WEB_PORT" ] || AGH_WEB_PORT=3000
        if [ "§AGH_DNS_PORT" = "53" ]; then
          # Порядок без deadlock: сначала opkg dns-override освобождает :53, затем старт AGH, затем проверка + rollback
          LD_LIBRARY_PATH= /bin/ndmc -c "opkg dns-override" >/dev/null 2>&1 || true
          sleep 1
        fi
        "§AGH_INI" restart >/dev/null 2>&1 || "§AGH_INI" start >/dev/null 2>&1 || true
        sleep 3
        dns_listen=0
        { netstat -ln 2>/dev/null || ss -ln 2>/dev/null; } | grep -qE ":§{AGH_DNS_PORT}([[:space:]]|$)" && dns_listen=1
        agh_alive=0
        (ps w 2>/dev/null || ps 2>/dev/null) | grep -q '[A]dGuardHome' && agh_alive=1
        if [ "§AGH_DNS_PORT" = "53" ]; then
          if [ "§agh_alive" = "1" ] && [ "§dns_listen" = "1" ] && nslookup youtube.com 127.0.0.1 >/dev/null 2>&1; then
            LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1 || true
            echo "OK: AdGuard Home запущен (:§AGH_WEB_PORT / :53) и dns-override включён"
          else
            LD_LIBRARY_PATH= /bin/ndmc -c "no opkg dns-override" >/dev/null 2>&1 || true
            LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1 || true
            echo "WARN: AdGuard не ответил на :53 — включён безопасный откат no opkg dns-override"
          fi
        elif [ "§agh_alive" = "1" ] && [ "§dns_listen" = "1" ]; then
          echo "OK: AdGuard Home запущен (:§AGH_WEB_PORT / DNS :§AGH_DNS_PORT, без dns-override)"
        else
          echo "WARN: AdGuard не слушает порт :§AGH_DNS_PORT"
        fi
        """
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
        return MonitorShellSupport.sh(
            """
            wan_if=§(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null | awk '
              /^interface / { cur=§2; inb=1; isw=0; rnm="" }
              inb && /^[ \t]+rename / { rnm=§2 }
              inb && /^[ \t]+ip global / { isw=1 }
              inb && /^!/ { if (isw) { print (rnm!=""?rnm:cur); inb=0; exit } inb=0 }
              END { if (inb && isw) print (rnm!=""?rnm:cur) }
            ')
            [ -n "§wan_if" ] || wan_if="ISP"
            _tail='${MonitorShellSupport.sq(tail)}'
            LD_LIBRARY_PATH= /bin/ndmc -c "ip static $pr §wan_if §_tail"
            LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
            echo "OK: Проброс $pr (§wan_if) сохранён"
            """
        )
    }

    fun delStaticPortCmd(fullRule: String): String = MonitorShellSupport.ndmcSaveCmd("no ${fullRule.trim()}")

    val ENABLE_CONNTRACK_ACCT_CMD = MonitorShellSupport.sh(
        """
        if [ -w /proc/sys/net/netfilter/nf_conntrack_acct ]; then
          echo 1 > /proc/sys/net/netfilter/nf_conntrack_acct 2>/dev/null || sysctl -w net.netfilter.nf_conntrack_acct=1 2>&1 || true
          echo "OK: Счётчик байтов conntrack_acct включён"
        else
          echo "INFO: Ядро этой модели Keenetic собрано без nf_conntrack_acct — график Мбит/с и активные потоки работают в обычном режиме."
        fi
        """
    )

    val CMD_TORCH_LIVE = MonitorShellSupport.sh(
        """
        PFX="/tmp/.gth_§$"
        snap_dev() { awk 'NR>2{sub(/:/," ",§0); if(§1~/^(eth|br|ra|rai|wg|z2k|tun|ppp)/) printf "%s %s %s %s %s %s %s\n", §1, §2, §3, §4, §10, §11, §12}' /proc/net/dev; }
        a=§(snap_dev)
        sleep 1
        b=§(snap_dev)
        ifaces=§(awk -v A="§a" -v B="§b" '${MonitorShellSupport.AWK_ESC} BEGIN{
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
          flows=§(awk -v PFX="§PFX" '${MonitorShellSupport.AWK_ESC} BEGIN{
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
        val h = MonitorShellSupport.sq(host.trim().ifEmpty { "youtube.com" })
        return when (mode) {
            "ping" -> MonitorShellSupport.sh("ping -c 5 -W 2 '$h' 2>&1")
            "trace" -> MonitorShellSupport.sh("traceroute -n -w 2 -m 15 '$h' 2>&1 || tracepath -n '$h' 2>&1")
            "dns" -> MonitorShellSupport.sh("nslookup '$h' 127.0.0.1 2>&1")
            "dns_cmp" -> MonitorShellSupport.sh(
                """
                echo "=== Сравнение DNS для '$h' ==="
                for srv in "127.0.0.1 (Роутер/AGH)" "1.1.1.1 (Cloudflare)" "8.8.8.8 (Google)" "77.88.8.8 (Yandex)"; do
                  ip="${'$'}{srv%% *}"
                  ans=§(nslookup '$h' "§ip" 2>&1 | awk '/^Address/{a=§NF} END{print a}')
                  echo "§srv -> §{ans:-таймаут/подмена}"
                done
                """
            )
            "dpi" -> MonitorShellSupport.sh(
                """
                echo "=== Диагностика DPI / TLS ClientHello: '$h' ==="
                curl -vI --connect-timeout 5 --max-time 8 'https://$h' 2>&1 | grep -iE 'Trying|Connected|SSL connection|ALPN|HTTP/|subject:|issuer:|error|reset|timed out' | head -n 25
                echo "--- Статус NFQUEUE mangle ---"
                iptables -t mangle -L -v -n 2>/dev/null | grep -E 'Chain|NFQUEUE' | head -n 15
                """
            )
            "tcpdump" -> MonitorShellSupport.sh(
                """
                if ! command -v tcpdump >/dev/null 2>&1; then
                  echo "tcpdump не установлен. Установите его в разделе Службы -> пакет 'tcpdump'."
                else
                  echo "=== Захват первых 25 пакетов (5с) для '$h' ==="
                  if timeout -t 5 true >/dev/null 2>&1; then
                    timeout -t 5 tcpdump -nn -c 25 host '$h' 2>&1
                  elif timeout 5 true >/dev/null 2>&1; then
                    timeout 5 tcpdump -nn -c 25 host '$h' 2>&1
                  else
                    tcpdump -nn -c 25 host '$h' 2>&1 &
                    tpid=§!; (sleep 5; kill "§tpid" 2>/dev/null || true) >/dev/null 2>&1 &
                    kpid=§!; wait "§tpid" 2>/dev/null || true; kill "§kpid" 2>/dev/null || true
                  fi
                fi
                """
            )
            "http" -> MonitorShellSupport.sh("curl -IsS --max-time 6 'https://$h' 2>&1 | head -n 20")
            "conntrack" -> MonitorShellSupport.sh("awk '{for(i=1;i<=NF;i++) if(§i~/^src=/){s=substr(§i,5); c[s]++; break}} END{for(k in c) printf \"%5d  %s\\n\", c[k], k}' /proc/net/nf_conntrack 2>/dev/null | sort -rn | head -n 20")
            "ports" -> MonitorShellSupport.sh("netstat -tulnp 2>/dev/null || netstat -lnt 2>/dev/null")
            "traffic" -> MonitorShellSupport.sh(
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
            else -> MonitorShellSupport.sh("ping -c 4 -W 2 '$h' 2>&1")
        }
    }}
