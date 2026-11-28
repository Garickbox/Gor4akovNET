package ru.garickbox.gor4akovnet

object GboxScripts {

    fun sq(s: String): String = "'" + s.replace("'", "'\"'\"'") + "'"

    fun sanitizeMac(raw: String): String =
        raw.trim().replace(Regex("[^0-9A-Fa-f:-]"), "").take(17)

    fun sanitizeSsid(raw: String): String =
        raw.replace(Regex("""["'`$\\;\r\n]"""), "")
            .trim()
            .replace(" ", "_")
            .take(32)
            .ifBlank { "Gor4akovNET" }

    fun sanitizeWifiPass(raw: String): String {
        val cleaned = raw.replace(Regex("""["'`$\\;\r\n]"""), "").trim()
        return if (cleaned.length in 8..63) cleaned else "gor4akov2026"
    }

    val DASHBOARD_SCRIPT = """
        set +e
        u1=${'$'}(awk '/^cpu /{print ${'$'}2+${'$'}3+${'$'}4+${'$'}5+${'$'}6+${'$'}7+${'$'}8, ${'$'}5}' /proc/stat)
        sleep 1
        u2=${'$'}(awk '/^cpu /{print ${'$'}2+${'$'}3+${'$'}4+${'$'}5+${'$'}6+${'$'}7+${'$'}8, ${'$'}5}' /proc/stat)
        t1=${'$'}(echo "${'$'}u1" | awk '{print ${'$'}1}'); i1=${'$'}(echo "${'$'}u1" | awk '{print ${'$'}2}')
        t2=${'$'}(echo "${'$'}u2" | awk '{print ${'$'}1}'); i2=${'$'}(echo "${'$'}u2" | awk '{print ${'$'}2}')
        dt=${'$'}((t2 - t1)); di=${'$'}((i2 - i1))
        [ "${'$'}dt" -gt 0 ] && cpu=${'$'}(((dt - di) * 100 / dt)) || cpu=0
        mem_tot=${'$'}(awk '/MemTotal/{print int(${'$'}2/1024)}' /proc/meminfo)
        mem_avail=${'$'}(awk '/MemAvailable/{print int(${'$'}2/1024)}' /proc/meminfo)
        [ -z "${'$'}mem_avail" ] && mem_avail=${'$'}(awk '/MemFree/{print int(${'$'}2/1024)}' /proc/meminfo)
        mem_used=${'$'}((mem_tot - mem_avail))
        zram_used=${'$'}(awk '/zram/{u+=${'$'}4} END{print int(u/1024)}' /proc/swaps 2>/dev/null || echo 0)
        zram_tot=${'$'}(awk '/zram/{t+=${'$'}3} END{print int(t/1024)}' /proc/swaps 2>/dev/null || echo 0)
        sw_used=${'$'}(awk 'NR>1 && !/zram/{u+=${'$'}4} END{print int(u/1024)}' /proc/swaps 2>/dev/null || echo 0)
        sw_tot=${'$'}(awk 'NR>1 && !/zram/{t+=${'$'}3} END{print int(t/1024)}' /proc/swaps 2>/dev/null || echo 0)
        temp=0
        for f in /sys/class/thermal/thermal_zone*/temp; do
            [ -r "${'$'}f" ] || continue
            v=${'$'}(cat "${'$'}f" 2>/dev/null | tr -dc '0-9')
            [ -n "${'$'}v" ] || continue
            [ "${'$'}v" -gt 1000 ] && v=${'$'}((v / 1000))
            [ "${'$'}v" -gt "${'$'}temp" ] && [ "${'$'}v" -lt 130 ] && temp=${'$'}v
        done
        ct_cur=${'$'}(cat /proc/sys/net/netfilter/nf_conntrack_count 2>/dev/null || echo 0)
        ct_max=${'$'}(cat /proc/sys/net/netfilter/nf_conntrack_max 2>/dev/null || echo 0)
        load=${'$'}(awk '{print ${'$'}1" "${'$'}2" "${'$'}3}' /proc/loadavg 2>/dev/null || echo "-")
        up_s=${'$'}(awk '{print int(${'$'}1)}' /proc/uptime 2>/dev/null || echo 0)
        up_d=${'$'}((up_s / 86400)); up_h=${'$'}(((up_s % 86400) / 3600)); up_m=${'$'}(((up_s % 3600) / 60))
        opt_df=${'$'}(df -Pm /opt 2>/dev/null | awk 'NR==2{print ${'$'}4"|"${'$'}2"|"${'$'}1}')
        [ -z "${'$'}opt_df" ] && opt_df="0|0|-"
        arch=${'$'}(uname -m)
        host=${'$'}(cat /proc/sys/kernel/hostname 2>/dev/null || hostname)
        fw=${'$'}(LD_LIBRARY_PATH= ndmc -c "show version" 2>/dev/null | awk -F: '/title|release/{gsub(/^[ \t]+/,"",${'$'}2); print ${'$'}2; exit}')
        echo "DASH|${'$'}cpu|${'$'}mem_used|${'$'}mem_tot|${'$'}zram_used|${'$'}zram_tot|${'$'}sw_used|${'$'}sw_tot|${'$'}temp|${'$'}ct_cur|${'$'}ct_max|${'$'}load|${'$'}up_d|${'$'}up_h|${'$'}up_m|${'$'}opt_df|${'$'}arch|${'$'}host|${'$'}fw"
    """.trimIndent()

    val SERVICE_RADAR_SCRIPT = """
        set +e
        ent=0; [ -x /opt/bin/opkg ] && ent=1
        z2k_inst=0; [ -d /opt/zapret2 ] && z2k_inst=1
        nfq=0
        if command -v pgrep >/dev/null 2>&1 && pgrep -f nfqws2 >/dev/null 2>&1; then nfq=1
        elif ps w 2>/dev/null | grep -q '[n]fqws2'; then nfq=1
        elif ps 2>/dev/null | grep -q '[n]fqws2'; then nfq=1
        fi
        z2k_run=0; [ "${'$'}nfq" -gt 0 ] && z2k_run=1
        z2k_mode=${'$'}(awk -F= '/^MODE=|^Z2K_PRESET=/{v=${'$'}2} END{gsub(/"/,"",v); print (v==""?"auto":v)}' /opt/zapret2/config 2>/dev/null)
        ipt_cnt=${'$'}(iptables -t mangle -S 2>/dev/null | grep -c NFQUEUE | head -n1 | tr -dc '0-9'); [ -z "${'$'}ipt_cnt" ] && ipt_cnt=0
        agh_inst=0; { [ -x /opt/sbin/AdGuardHome ] || [ -x /opt/bin/AdGuardHome ]; } && agh_inst=1
        agh_run=0; { ps w 2>/dev/null | grep -q '[A]dGuardHome' || ps 2>/dev/null | grep -q '[A]dGuardHome'; } && agh_run=1
        cfg=${'$'}(LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null)
        dns_ov=0; echo "${'$'}cfg" | grep -q "opkg dns-override" && dns_ov=1
        warp_inst=0
        if [ -x /opt/sbin/z2k-warpd ] || [ -f /opt/sbin/z2k-warpd ] || [ -x /opt/zapret2/z2k-warp.sh ] || [ -f /opt/zapret2/z2k-warp.sh ]; then warp_inst=1; fi
        warp_run=0
        if command -v pgrep >/dev/null 2>&1 && pgrep -f z2k-warpd >/dev/null 2>&1; then warp_run=1
        elif ps w 2>/dev/null | grep -q '[z]2k-warpd'; then warp_run=1
        elif ps 2>/dev/null | grep -q '[z]2k-warpd'; then warp_run=1
        fi
        ps_all=${'$'}(ps w 2>/dev/null || ps 2>/dev/null)
        tg_run=0; printf '%s\n' "${'$'}ps_all" | grep -q '[t]g-mtproxy-client' && tg_run=1
        db_run=0; printf '%s\n' "${'$'}ps_all" | grep -q '[d]ropbear' && db_run=1
        pol_name=${'$'}(awk -F= '/^POLICY_NAME=/{gsub(/["'"'"']/,"",${'$'}2); print ${'$'}2; exit}' /opt/zapret2/config 2>/dev/null)
        pol_act=0
        if [ -n "${'$'}pol_name" ]; then
            echo "${'$'}cfg" | grep -q "^ip policy ${'$'}pol_name${'$'}" && pol_act=1
        else
            echo "${'$'}cfg" | grep -q "^ip policy Gor4akovNET" && pol_act=1
            pol_name="Gor4akovNET"
        fi
        inst_mark=0; [ -f /opt/etc/.gor4akov_installed ] && inst_mark=1
        eval_br=${'$'}(echo "${'$'}cfg" | awk '
            /^interface Bridge[0-9]+/ { cur=${'$'}2; inb=1; isg=0; ip="" }
            inb && /^[ \t]+description Gor4akovNET/ { isg=1 }
            inb && /^[ \t]+ip address / { ip=${'$'}3 }
            inb && /^!/ { if (isg && cur!="") { print cur"|"ip; exit } inb=0 }
            END { if (inb && isg && cur!="") print cur"|"ip }
        ')
        br_name=${'$'}(echo "${'$'}eval_br" | cut -d'|' -f1)
        br_sub=${'$'}(echo "${'$'}eval_br" | cut -d'|' -f2)
        # ВАЖНО head -n1: awk после `exit` ВСЁ РАВНО вызывает END и печатает
        # строку повторно. На двух мастерах (WifiMaster0 + WifiMaster1) это даёт
        # склейку "Gor4akovNETGor4akovNET". head -n1 берёт только первый SSID.
        br_ssid=${'$'}(echo "${'$'}cfg" | awk '
            /^interface WifiMaster/ { if (inw && isg && ss!="" && !done) { print ss; done=1 } inw=1; isg=0; ss="" }
            inw && /^[ \t]+description Gor4akovNET/ { isg=1 }
            inw && /^[ \t]+ssid / { sub(/^[ \t]+ssid /,""); ss=${'$'}0 }
            inw && /^!/ { if (inw && isg && ss!="" && !done) { print ss; done=1 } inw=0 }
            END { if (!done && inw && isg && ss!="") print ss }
        ' | head -n1 | tr -d '\r\n|')
        # Пароль НЕ из running-config (там ns3-хеш), а из /opt/etc/gor4akov-wifi.pass
        # (chmod 600), который пишет инсталлер при создании AP. Только если этот
        # AP реально существует — иначе отдаём пусто (см. br_pass ниже по проверке APA).
        br_pass=""
        if [ -n "${'$'}br_ssid" ] && [ -f /opt/etc/gor4akov-wifi.pass ]; then
            br_pass=${'$'}(head -n1 /opt/etc/gor4akov-wifi.pass 2>/dev/null | tr -d '\r\n|')
        fi
        echo "SVC|${'$'}ent|${'$'}z2k_inst|${'$'}z2k_run|${'$'}z2k_mode|${'$'}nfq|${'$'}ipt_cnt|${'$'}agh_inst|${'$'}agh_run|${'$'}dns_ov|${'$'}warp_inst|${'$'}warp_run|${'$'}tg_run|${'$'}db_run|${'$'}pol_name|${'$'}pol_act|${'$'}br_name|${'$'}br_sub|${'$'}br_ssid|${'$'}inst_mark|${'$'}br_pass"
    """.trimIndent()

    val CLIENTS_SCRIPT = """
        set +e
        LD_LIBRARY_PATH= ndmc -c "show ip hotspot" 2>/dev/null | awk '
            function trim(s) { sub(/^[ \t\r]+/, "", s); sub(/[ \t\r]+${'$'}/, "", s); return s }
            function flush() {
                if (mac != "") {
                    gsub(/\|/, "_", name); gsub(/\|/, "_", seg); gsub(/\|/, "_", pol)
                    printf "HOST|%s|%s|%s|%s|%s|%d|%d\n", mac, (ip==""?"-":ip), (name==""?mac:name), (seg==""?"Home":seg), (pol==""?"Default":pol), active+0, static_dhcp+0
                }
            }
            /^[ \t]*mac[ \t]*:/ { flush(); mac=trim(${'$'}2); for(i=3;i<=NF;i++) mac=mac""${'$'}i; ip=""; name=""; seg=""; pol=""; active=0; static_dhcp=0 }
            /^[ \t]*ip[ \t]*:/ { ip=trim(substr(${'$'}0, index(${'$'}0, ":")+1)) }
            /^[ \t]*name[ \t]*:/ { name=trim(substr(${'$'}0, index(${'$'}0, ":")+1)) }
            /^[ \t]*hostname[ \t]*:/ { if (name=="") name=trim(substr(${'$'}0, index(${'$'}0, ":")+1)) }
            /^[ \t]*interface[ \t]*:/ { seg=trim(substr(${'$'}0, index(${'$'}0, ":")+1)) }
            /^[ \t]*policy[ \t]*:/ { pol=trim(substr(${'$'}0, index(${'$'}0, ":")+1)) }
            /^[ \t]*active[ \t]*:/ { v=tolower(trim(substr(${'$'}0, index(${'$'}0, ":")+1))); if (v=="yes" || v=="true") active=1 }
            /^[ \t]*registered[ \t]*:/ { v=tolower(trim(substr(${'$'}0, index(${'$'}0, ":")+1))); if (v=="yes" || v=="true") static_dhcp=1 }
            END { flush() }
        '
    """.trimIndent()

    fun toggleDevicePolicy(mac: String, enableGor4akov: Boolean): String {
        val cleanMac = sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val qMac = sq(cleanMac)
        return """
            set +e
            MAC=$qMac
            if [ "${if (enableGor4akov) 1 else 0}" = "1" ]; then
                LD_LIBRARY_PATH= ndmc -c "ip hotspot host ${'$'}MAC policy Gor4akovNET"
            else
                LD_LIBRARY_PATH= ndmc -c "no ip hotspot host ${'$'}MAC policy"
            fi
            LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null 2>&1
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
            echo "OK"
        """.trimIndent()
    }

    fun toggleDeviceQos(mac: String, highPriority: Boolean): String {
        val cleanMac = sanitizeMac(mac)
        if (cleanMac.length < 11) return "echo 'ERR: Некорректный MAC'; exit 1"
        val qMac = sq(cleanMac)
        return """
            set +e
            MAC=$qMac
            if [ "${if (highPriority) 1 else 0}" = "1" ]; then
                LD_LIBRARY_PATH= ndmc -c "ip hotspot host ${'$'}MAC permit" >/dev/null 2>&1
                LD_LIBRARY_PATH= ndmc -c "ip hotspot host ${'$'}MAC priority 1"
            else
                LD_LIBRARY_PATH= ndmc -c "ip hotspot host ${'$'}MAC priority 6"
            fi
            LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null 2>&1
            echo "OK"
        """.trimIndent()
    }

    fun buildOfflineAndBypassInstallScript(
        ssid: String,
        wifiPass: String,
        isolatedMode: Boolean,
        installAdguard: Boolean,
        enableWarp: Boolean
    ): String {
        val safeSsid = sanitizeSsid(ssid)
        val safePass = sanitizeWifiPass(wifiPass)
        val qSsid = sq(safeSsid)
        val qPass = sq(safePass)
        val isoFlag = if (isolatedMode) "1" else "0"
        val aghFlag = if (installAdguard) "1" else "0"
        val warpFlag = if (enableWarp) "1" else "0"

        return """__LONG__
            set +e
            export PATH="/opt/bin:/opt/sbin:/opt/usr/bin:/opt/usr/sbin:/bin:/sbin:/usr/bin:/usr/sbin"
            export TMPDIR=/opt/tmp
${BackupRestoreScripts.SH_ARCHIVER_HELPERS}
            ensure_archiver 0
            _gbox_unpack_strip1() {
                _src_tgz="${'$'}1"; _dst_dir="${'$'}2"
                mkdir -p "${'$'}_dst_dir"
                if [ "${'$'}_tar_can_strip" = "1" ]; then
                    "${'$'}TAR" -xzf "${'$'}_src_tgz" -C "${'$'}_dst_dir" --strip-components=1
                else
                    _utmp="${'$'}TMPDIR/.gbox_u1_${'$'}${'$'}"
                    rm -rf "${'$'}_utmp" && mkdir -p "${'$'}_utmp"
                    (cd "${'$'}_utmp" && "${'$'}TAR" -xzf "${'$'}_src_tgz") || { rm -rf "${'$'}_utmp"; return 1; }
                    _top="${'$'}(find "${'$'}_utmp" -mindepth 1 -maxdepth 1 -type d | head -n1)"
                    if [ -n "${'$'}_top" ]; then
                        cp -a "${'$'}_top"/. "${'$'}_dst_dir"/
                    else
                        cp -a "${'$'}_utmp"/. "${'$'}_dst_dir"/
                    fi
                    rm -rf "${'$'}_utmp"
                fi
            }
            SAFE_SSID=$qSsid
            SAFE_PASS=$qPass
            ISO_FLAG='$isoFlag'
            if [ ! -x /opt/bin/opkg ]; then
                echo "[FATAL] Entware (/opt/bin/opkg) не смонтирован — установка невозможна."
                echo "[FATAL] Вернитесь на шаг 4 мастера: USB ext4 + Entware + SSH :222"
                echo "PREFLIGHT_FAIL"
                exit 1
            fi
            echo "[OK] Preflight: Entware на /opt смонтирован"
            mkdir -p /opt/tmp /opt/etc/opkg /opt/etc/ndm/netfilter.d /opt/gor4akov-offline/ipks

            echo "=== [1/6] Установка локальных системных пакетов ==="
            if [ -s /opt/gor4akov-offline/ipks.tar.gz ]; then
                if ! ${'$'}{TAR} -tzf /opt/gor4akov-offline/ipks.tar.gz >/dev/null 2>&1; then
                    echo "[FATAL] ipks.tar.gz повреждён (${'$'}{TAR} -tzf fail). Проверьте передачу по SSH."
                    echo "PREFLIGHT_FAIL"
                    exit 1
                fi
                rm -rf /opt/gor4akov-offline/ipks/*
                ${'$'}{TAR} -xzf /opt/gor4akov-offline/ipks.tar.gz -C /opt/gor4akov-offline/ipks/
                cp -f /opt/etc/opkg.conf /opt/etc/opkg.conf.bak 2>/dev/null || true
                printf 'src/gz gor4akov_local file:///opt/gor4akov-offline/ipks\n' > /opt/etc/opkg.conf.local
                grep -E '^(dest|option|arch)' /opt/etc/opkg.conf >> /opt/etc/opkg.conf.local 2>/dev/null || true
                opkg -f /opt/etc/opkg.conf.local update >/dev/null 2>&1 || true
                for pkg in libmnl libnetfilter-queue libnfnetlink libcap zlib curl flock unzip tar gzip iptables xtables-addons_legacy kmod_ndms ipset openssl-util; do
                    if ! opkg list-installed | grep -q "^${'$'}{pkg} "; then
                        opkg -f /opt/etc/opkg.conf.local install "${'$'}pkg" >/dev/null 2>&1 || opkg -f /opt/etc/opkg.conf.local install /opt/gor4akov-offline/ipks/"${'$'}pkg"_*.ipk >/dev/null 2>&1 || true
                    fi
                done
                missing_pkgs=""
                for pkg in libmnl libnetfilter-queue libnfnetlink libcap iptables; do
                    if ! opkg list-installed 2>/dev/null | grep -q "^${'$'}{pkg} "; then
                        missing_pkgs="${'$'}missing_pkgs ${'$'}pkg"
                    fi
                done
                if [ -n "${'$'}missing_pkgs" ]; then
                    echo "[FATAL] Не установились критичные пакеты:${'$'}missing_pkgs"
                    echo "PREFLIGHT_FAIL"
                    exit 1
                fi
                echo "[OK] Критичные пакеты развёрнуты локально из APK"
            else
                opkg update || true
                opkg install libmnl libnetfilter-queue libnfnetlink libcap zlib curl flock unzip tar gzip iptables xtables-addons_legacy kmod_ndms ipset openssl-util || true
                missing_pkgs=""
                for pkg in libmnl libnetfilter-queue libnfnetlink libcap iptables; do
                    if ! opkg list-installed 2>/dev/null | grep -q "^${'$'}{pkg} "; then
                        missing_pkgs="${'$'}missing_pkgs ${'$'}pkg"
                    fi
                done
                if [ -n "${'$'}missing_pkgs" ]; then
                    echo "[FATAL] Не установились критичные пакеты:${'$'}missing_pkgs (нет интернета и нет локальных ipk)"
                    echo "PREFLIGHT_FAIL"
                    exit 1
                fi
            fi

            _gbox_scan_archivers
            echo "=== [2/6] Распаковка z2k-enhanced и nfqws2 ==="
            WORK_STAGE="/opt/z2k-work"
            rm -rf "${'$'}WORK_STAGE" /opt/gor4akov-offline/z2k_src
            mkdir -p "${'$'}WORK_STAGE/zapret2_build" /opt/gor4akov-offline/z2k_src
            if [ -s /opt/gor4akov-offline/z2k-repo.tar.gz ]; then
                if ! ${'$'}{TAR} -tzf /opt/gor4akov-offline/z2k-repo.tar.gz >/dev/null 2>&1; then
                    echo "[FATAL] z2k-repo.tar.gz повреждён. Проверьте передачу по SSH."
                    echo "PREFLIGHT_FAIL"
                    exit 1
                fi
                _gbox_unpack_strip1 /opt/gor4akov-offline/z2k-repo.tar.gz /opt/gor4akov-offline/z2k_src
                cp -a /opt/gor4akov-offline/z2k_src/* "${'$'}WORK_STAGE/"
                echo "[OK] z2k-enhanced подготовлен локально"
            else
                echo "[INFO] Пробую скачать z2k-enhanced из сети..."
                curl -fsSL "https://github.com/necronicle/z2k/archive/refs/heads/z2k-enhanced.tar.gz" -o /opt/gor4akov-offline/z2k-repo.tar.gz && \
                _gbox_unpack_strip1 /opt/gor4akov-offline/z2k-repo.tar.gz "${'$'}WORK_STAGE"
            fi
            if [ -s /opt/gor4akov-offline/openwrt-embedded.tar.gz ]; then
                cp -f /opt/gor4akov-offline/openwrt-embedded.tar.gz "${'$'}WORK_STAGE/zapret2_build/openwrt-embedded.tar.gz"
            fi

            echo "=== [3/6] Запуск установки zapret2 (z2k) ==="
            export WORK_DIR="${'$'}WORK_STAGE"
            export LIB_DIR="${'$'}WORK_STAGE/lib"
            export ZAPRET2_DIR="/opt/zapret2"
            export Z2K_WORKDIR_ON_OPT=1
            export Z2K_ALLOW_INCOMPLETE_DEPS=1
            export GITHUB_RAW="https://raw.githubusercontent.com/necronicle/z2k/z2k-enhanced"
            MODULES="utils install strategies config config_official webpanel menu auto_update wan"

            if [ -f "${'$'}WORK_STAGE/z2k.sh" ]; then
                sed '/^main "\${'$'}@"/d' "${'$'}WORK_STAGE/z2k.sh" > "${'$'}WORK_STAGE/.z2k_lib_only.sh"
                . "${'$'}WORK_STAGE/.z2k_lib_only.sh"
                set +e
                WORK_DIR="${'$'}WORK_STAGE"
                LIB_DIR="${'$'}WORK_STAGE/lib"
                export WORK_DIR LIB_DIR
                for m in ${'$'}MODULES; do
                    [ -f "${'$'}LIB_DIR/${'$'}{m}.sh" ] && . "${'$'}LIB_DIR/${'$'}{m}.sh"
                done
                z2k_fetch_offline_or_net() {
                    _u="${'$'}1"; _d="${'$'}2"
                    _rel="${'$'}{_u#${'$'}{GITHUB_RAW}/}"
                    if [ "${'$'}_rel" != "${'$'}_u" ] && [ -s "${'$'}WORK_STAGE/${'$'}_rel" ]; then
                        mkdir -p "${'$'}(dirname "${'$'}_d")" 2>/dev/null
                        cp -f "${'$'}WORK_STAGE/${'$'}_rel" "${'$'}_d" && return 0
                    fi
                    curl -fsSL --connect-timeout 6 --max-time 60 "${'$'}_u" -o "${'$'}_d"
                }
                z2k_fetch() { z2k_fetch_offline_or_net "${'$'}@"; }

                mkdir -p /opt/gor4akov-offline/cache
                [ -s "${'$'}WORK_STAGE/zapret2_build/openwrt-embedded.tar.gz" ] && mv "${'$'}WORK_STAGE/zapret2_build/openwrt-embedded.tar.gz" /opt/gor4akov-offline/cache/openwrt-embedded.tar.gz
                download_openwrt_embedded_release() {
                    if [ -s /opt/gor4akov-offline/cache/openwrt-embedded.tar.gz ]; then
                        cp -f /opt/gor4akov-offline/cache/openwrt-embedded.tar.gz "${'$'}2"
                        return 0
                    fi
                    curl -fsSL --connect-timeout 8 --max-time 180 "${'$'}1" -o "${'$'}2"
                }

                if ! command -v step_check_root >/dev/null 2>&1; then
                    echo "[FATAL] z2k-модули не загрузились: step_check_root не определён."
                    echo "[FATAL] Проверьте ${'$'}{WORK_STAGE}/lib/install.sh"
                    echo "PREFLIGHT_FAIL"
                    exit 1
                fi
                step_check_root
                step_install_dependencies || true
                step_load_kernel_modules || true
                if step_build_zapret2 && step_verify_installation && step_check_and_select_fwtype && \
                   step_download_domain_lists && step_disable_hwnat_and_offload && step_configure_tmpdir && \
                   step_tcp_tuning && step_create_config_and_init && step_install_netfilter_hook && step_finalize; then
                    apply_autocircular_strategies --auto || true
                    z2k_commit_install || true
                    echo "[OK] zapret2 / z2k установлен!"
                else
                    z2k_restore_old_tree || true
                    echo "[WARN] Полная сборка завершилась с предупреждением — проверяем /opt/zapret2"
                fi
            else
                echo "[ERR] z2k.sh не найден!"
                exit 1
            fi

            echo "=== [4/6] Настройка политики и изолированной сети ==="
            CFG="${'$'}(LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null)"
            if ! echo "${'$'}CFG" | grep -q "^ip policy Gor4akovNET"; then
                LD_LIBRARY_PATH= ndmc -c "ip policy Gor4akovNET" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "ip policy Gor4akovNET description Gor4akovNET" >/dev/null
            fi
            # ВАЖНО: у NDMS-политики по умолчанию "no permit global ISP" — хост,
            # которому выдали политику (ip hotspot host <MAC> policy Gor4akovNET),
            # остался бы БЕЗ интернета. Ставим permit global ISP идемпотентно —
            # это исправляет и старые установки, где политика создана без permit.
            LD_LIBRARY_PATH= ndmc -c "ip policy Gor4akovNET permit global ISP" >/dev/null 2>&1 || true

            SUB_PREFIX="192.168.77"
            if [ "${'$'}ISO_FLAG" = "1" ]; then
                BR_NAME="${'$'}(echo "${'$'}CFG" | awk '/^interface Bridge[0-9]+/{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g){print cur; inb=0; exit} inb=0} END{if(inb && g) print cur}')"
                if [ -z "${'$'}BR_NAME" ]; then
                    n=1
                    while [ "${'$'}n" -le 15 ]; do
                        if ! echo "${'$'}CFG" | grep -q "^interface Bridge${'$'}n${'$'}"; then
                            BR_NAME="Bridge${'$'}n"
                            break
                        fi
                        n=${'$'}((n + 1))
                    done
                fi
                SUB_THIRD=77
                while echo "${'$'}CFG" | grep -q "192.168.${'$'}{SUB_THIRD}." && ! echo "${'$'}CFG" | awk -v b="^interface ${'$'}BR_NAME${'$'}" '${'$'}0 ~ b{f=1} f && /^!/{f=0} f' | grep -q "192.168.${'$'}{SUB_THIRD}."; do
                    SUB_THIRD=${'$'}((SUB_THIRD + 1))
                done
                SUB_PREFIX="192.168.${'$'}{SUB_THIRD}"

                if [ -n "${'$'}BR_NAME" ]; then
                    LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME" >/dev/null
                    LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME description Gor4akovNET" >/dev/null
                    LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME security-level private" >/dev/null
                    LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME ip address ${'$'}{SUB_PREFIX}.1 255.255.255.0" >/dev/null
                    # ВАЖНО: НЕ вешаем ip policy Gor4akovNET на Bridge — у неё "no permit global ISP",
                    # это блокирует интернет. Обход работает глобально через NFQUEUE на WAN (eth3),
                    # а точечно политика выдаётся клиентам в Мониторе через ip hotspot host <MAC> policy.
                    # Для интернета в новой сети нужен NAT:
                    LD_LIBRARY_PATH= ndmc -c "ip nat ${'$'}BR_NAME" >/dev/null 2>&1
                    LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME up" >/dev/null

                    LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET" >/dev/null
                    LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET range ${'$'}{SUB_PREFIX}.10 ${'$'}{SUB_PREFIX}.200" >/dev/null
                    LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET bind ${'$'}BR_NAME" >/dev/null
                    # DNS-сервер для клиентов: СНАЧАЛА ставим рабочий внешний fallback (Яндекс DNS).
                    # Переключение на 77.1 (AdGuard) делается ПОЗЖЕ в [5/6], только после
                    # того, как AdGuard реально запустился и слушает :53. Иначе, если
                    # AdGuard не поднялся, DHCP раздаёт 77.1 → клиент без DNS → "нет интернета".
                    LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET dns-server 77.88.8.8" >/dev/null
                    LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET dns-server 77.88.8.1" >/dev/null
                    LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET enable" >/dev/null

                    APS_LIST=""
                    for master in WifiMaster0 WifiMaster1; do
                        if echo "${'$'}CFG" | grep -q "^interface ${'$'}{master}${'$'}"; then
                            ap="${'$'}(echo "${'$'}CFG" | awk -v m="^interface ${'$'}{master}/" '${'$'}0 ~ m{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g){print cur; inb=0; exit} inb=0} END{if(inb && g) print cur}')"
                            if [ -z "${'$'}ap" ]; then
                                for slot in 1 2 3 4 5 6 7; do
                                    cand="${'$'}{master}/AccessPoint${'$'}{slot}"
                                    if ! LD_LIBRARY_PATH= ndmc -c "show interface ${'$'}cand" 2>/dev/null | grep -qiE "id:|AccessPoint|type:"; then
                                        continue
                                    fi
                                    blk="${'$'}(echo "${'$'}CFG" | awk -v c="^interface ${'$'}cand${'$'}" '${'$'}0 ~ c{f=1} f{print} f && /^!/{exit}')"
                                    if [ -n "${'$'}blk" ]; then
                                        alias="${'$'}(echo "${'$'}blk" | awk '/^[ \t]+rename /{print ${'$'}2; exit}')"
                                        echo "${'$'}blk" | grep -qE "^[ \t]+(ssid |description |authentication |encryption (enable|wpa))" && continue
                                        echo "${'$'}CFG" | grep -qE "^[ \t]+include (${'$'}cand|${'$'}{alias:-__NONE__})${'$'}" && continue
                                    fi
                                    ap="${'$'}cand"
                                    break
                                done
                            fi
                            [ -z "${'$'}ap" ] && continue
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap" >/dev/null
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap description Gor4akovNET" >/dev/null
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap ssid ${'$'}SAFE_SSID" >/dev/null
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap encryption enable" >/dev/null 2>&1 || true
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap encryption wpa2" >/dev/null
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap authentication wpa-psk ${'$'}SAFE_PASS" >/dev/null
                            # NDMS хранит в running-config только base64-хеш ns3, а не пароль.
                            # Сохраняем настоящий пароль в файл chmod 600 — это единственный
                            # способ показать его пользователю после переустановки APK.
                            printf '%s\n' "${'$'}SAFE_PASS" > /opt/etc/gor4akov-wifi.pass 2>/dev/null || true
                            chmod 600 /opt/etc/gor4akov-wifi.pass 2>/dev/null || true
                            APS_LIST="${'$'}{APS_LIST} ${'$'}ap"
                            # ПОРЯДОК ВАЖЕН: сначала up, потом include в Bridge. Если include сделать
                            # до up — NDMS его молча игнорирует (race), и клиенты не получают IP.
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap up" >/dev/null 2>&1 || true
                            # Ждём state=up (до 5 сек), иначе retry
                            _ap_up=0
                            for _try in 1 2 3 4 5; do
                                sleep 1
                                _st=${'$'}(LD_LIBRARY_PATH= ndmc -c "show interface ${'$'}ap" 2>/dev/null | awk '/^[ \t]*state:/{print ${'$'}2; exit}')
                                if [ "${'$'}_st" = "up" ]; then _ap_up=1; break; fi
                                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap up" >/dev/null 2>&1 || true
                            done
                            if [ "${'$'}_ap_up" = "1" ]; then
                                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}{BR_NAME} include ${'$'}ap" >/dev/null
                            else
                                echo "[WARN] ${'$'}ap не поднялся за 5 сек — include пропущен"
                            fi
                        fi
                    done

                    # ПЕРВЫЙ save: фиксируем AP в state=up ДО того, как include
                    # пересоберёт Bridge. Без этого если пользователь ребутнёт роутер
                    # между include и финальным save, startup останется с down.
                    LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null 2>&1 || true

                    # После include Bridge пересобирается и AP может кратковременно
                    # уйти в state:down. save фиксирует ТЕКУЩЕЕ NVRAM — если AP в down
                    # в этот момент, после ребута он останется down навсегда.
                    # Дожимаем up и ждём state=up для каждого AP из APS_LIST, и только
                    # потом save.
                    for _ap in ${'$'}APS_LIST; do
                        [ -n "${'$'}_ap" ] || continue
                        _fixup_ok=0
                        for _ftry in 1 2 3 4 5 6; do
                            _fst=${'$'}(LD_LIBRARY_PATH= ndmc -c "show interface ${'$'}_ap" 2>/dev/null | awk '/^[ \t]*state:/{print ${'$'}2; exit}')
                            if [ "${'$'}_fst" = "up" ]; then _fixup_ok=1; break; fi
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}_ap up" >/dev/null 2>&1 || true
                            sleep 1
                        done
                        if [ "${'$'}_fixup_ok" = "1" ]; then
                            echo "[OK] ${'$'}_ap state=up (готов к save)"
                        else
                            echo "[WARN] ${'$'}_ap не поднялся перед save — startup может зафиксировать down"
                        fi
                    done
                    LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null
                    ap_count=${'$'}(echo "${'$'}CFG" | grep -cE '^interface WifiMaster[0-9]+/AccessPoint' 2>/dev/null)
                    if [ "${'$'}{ap_count:-0}" -gt 0 ]; then
                        echo "[OK] Wi-Fi сеть '${'$'}SAFE_SSID' поднята на ${'$'}BR_NAME (${'$'}{SUB_PREFIX}.0/24)"
                    else
                        echo "[WARN] На роутере нет Wi-Fi AP — сеть '${'$'}SAFE_SSID' не создана. Политика Gor4akovNET работает для проводных."
                    fi
                fi
            else
                echo "[INFO] Режим без отдельного Wi-Fi: политика Gor4akovNET создана (можно назначать на устройства в Мониторе)."
            fi

            # ВАЖНО: POLICY_NAME в zapret2/config запускает CONNMARK-exclude в S99zapret2 —
            # это ИСКЛЮЧАЕТ из обхода все сети, кроме указанной политики. Обход должен
            # работать глобально через NFQUEUE на WAN, поэтому POLICY_NAME НЕ пишем.
            # Точечный обход по устройствам — ip hotspot host <MAC> policy Gor4akovNET.
            ZCFG="/opt/zapret2/config"
            if [ -f "${'$'}ZCFG" ]; then
                sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d' "${'$'}ZCFG"
            fi

            # ВНИМАНИЕ: раньше здесь создавался хук 090-gor4akov-policy.sh, который
            # ставил CONNMARK 0x20000000 (EXCLUDE из обхода) на ВЕСЬ трафик, кроме
            # подсети Gor4akovNET. Это ЛОМАЛО обход в Home/Guest — обход работал только
            # в изолированной сети. Обход z2k работает глобально через NFQUEUE на WAN
            # (eth3), поэтому такой хук не нужен. Точечный обход по устройствам -
            # через ip hotspot host <MAC> policy Gor4akovNET.

            echo "=== [5/6] Опции (AdGuard Home v1.0.0 оффлайн / WARP) ==="
            if [ "$aghFlag" = "1" ]; then
                AGH_BIN="/opt/sbin/AdGuardHome"
                [ -x /opt/bin/AdGuardHome ] && AGH_BIN="/opt/bin/AdGuardHome"
                if [ ! -x "${'$'}AGH_BIN" ]; then
                    echo "[INFO] Пробую установить adguardhome-go через opkg..."
                    opkg update >/dev/null 2>&1 && opkg install adguardhome-go >/dev/null 2>&1 || true
                    [ -x /opt/bin/AdGuardHome ] && AGH_BIN="/opt/bin/AdGuardHome"
                fi
                if [ -x "${'$'}AGH_BIN" ]; then
                    mkdir -p /opt/etc/AdGuardHome /opt/var/log
                    chmod 755 "${'$'}AGH_BIN"
                    # Если yaml УЖЕ есть, но bind_hosts не 0.0.0.0 — добавим 0.0.0.0, иначе
                    # AdGuard не будет слушать в изолированной сети (77.1) и DNS клиентов умрёт.
                    if [ -s /opt/etc/AdGuardHome/AdGuardHome.yaml ] && ! grep -q "^[[:space:]]*-[[:space:]]*0\.0\.0\.0" /opt/etc/AdGuardHome/AdGuardHome.yaml; then
                        echo "[FIX] Добавляю 0.0.0.0 в bind_hosts AdGuard"
                        awk '
                          /^dns:/ {print; in_dns=1; next}
                          in_dns && /^[[:space:]]+bind_hosts:/ {print; print "    - 0.0.0.0"; inserted=1; in_bind=1; next}
                          in_dns && in_bind && /^[[:space:]]+-[[:space:]]/ {next}
                          {print; if (in_bind && !/^[[:space:]]+-[[:space:]]/) {in_bind=0}}
                        ' /opt/etc/AdGuardHome/AdGuardHome.yaml > /opt/etc/AdGuardHome/AdGuardHome.yaml.new
                        mv /opt/etc/AdGuardHome/AdGuardHome.yaml.new /opt/etc/AdGuardHome/AdGuardHome.yaml
                    fi
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
                    AGH_INIT=""
                    for ini in /opt/etc/init.d/S99AdGuardHome /opt/etc/init.d/S99adguardhome; do
                        [ -x "${'$'}ini" ] && AGH_INIT="${'$'}ini" && break
                    done
                    if [ -z "${'$'}AGH_INIT" ]; then
                        AGH_INIT="/opt/etc/init.d/S99AdGuardHome"
                        {
                            printf '#!/bin/sh\nENABLED=yes\nPROCS=AdGuardHome\nBIN="%s"\nARGS="-c /opt/etc/AdGuardHome/AdGuardHome.yaml -w /opt/etc/AdGuardHome --no-check-update"\nPREARGS=""\nDESC="AdGuard Home"\nPATH=/opt/sbin:/opt/bin:/opt/usr/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n' "${'$'}AGH_BIN"
                            cat << 'EOFINI'
PIDFILE="/opt/var/run/AdGuardHome.pid"
mkdir -p /opt/var/run
case "$1" in
    start)
        if ps w 2>/dev/null | grep -q '[A]dGuardHome'; then echo "AdGuardHome already running"; exit 0; fi
        nohup "${'$'}BIN" ${'$'}ARGS >/opt/var/log/AdGuardHome.log 2>&1 &
        echo $! > "${'$'}PIDFILE"
        ;;
    stop)
        killall AdGuardHome 2>/dev/null || true
        rm -f "${'$'}PIDFILE"
        ;;
    restart)
        $0 stop; sleep 1; $0 start
        ;;
    status)
        ps w 2>/dev/null | grep -q '[A]dGuardHome' && echo "alive" || echo "dead"
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        ;;
esac
EOFINI
                        } > "${'$'}AGH_INIT"
                        chmod +x "${'$'}AGH_INIT"
                    fi
                    "${'$'}AGH_INIT" restart >/dev/null 2>&1 || "${'$'}AGH_INIT" start >/dev/null 2>&1 || true
                    sleep 3
                    dns_listen=0
                    { netstat -ln 2>/dev/null || ss -ln 2>/dev/null; } | grep -q ':53 ' && dns_listen=1
                    if { ps w 2>/dev/null | grep -q '[A]dGuardHome' || ps 2>/dev/null | grep -q '[A]dGuardHome'; } && [ "${'$'}dns_listen" = "1" ]; then
                        LD_LIBRARY_PATH= ndmc -c "opkg dns-override" >/dev/null
                        # AdGuard реально слушает :53 → можно безопасно переключить клиентов
                        # изолированной сети на 77.1 (DNS через AdGuard с фильтрацией).
                        if [ "${'$'}ISO_FLAG" = "1" ]; then
                            LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET dns-server ${'$'}{SUB_PREFIX}.1" >/dev/null 2>&1 || true
                        fi
                        LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null
                        echo "[OK] AdGuard Home v1.0.0 запущен (:3000 / DNS :53), opkg dns-override включён"
                        [ "${'$'}ISO_FLAG" = "1" ] && echo "[OK] DHCP Gor4akovNET раздаёт DNS ${'$'}{SUB_PREFIX}.1 (AdGuard)"
                    else
                        echo "[WARN] AdGuard Home не слушает :53 — dns-override НЕ включён (безопасность DNS). Проверьте Монитор → DNS."
                        echo "[WARN] DHCP Gor4akovNET остаётся на 77.88.8.8/77.88.8.1 (рабочий fallback), чтобы клиенты не остались без DNS."
                    fi
                else
                    echo "[WARN] Бинарник AdGuardHome не найден (нет в кэше и не скачался по opkg)."
                fi
            fi

            if [ "$warpFlag" = "1" ]; then
                if [ -x /opt/zapret2/z2k-warp.sh ] || [ -f /opt/zapret2/z2k-warp.sh ]; then
                    echo "[INFO] Активация Cloudflare WARP в z2k..."
                    sh /opt/zapret2/z2k-warp.sh enable >/dev/null 2>&1 || true
                else
                    echo "[WARN] z2k-warp.sh не найден — WARP можно установить из Монитора"
                fi
            fi

            echo "=== [6/6] Запуск обхода и финальная проверка ==="
            # Устаревший хук 090-gor4akov-policy.sh ставил CONNMARK-exclude 0x20000000
            # на весь трафик, кроме 77.x, и ломал обход в Home/Guest. Если остался
            # от прошлых установок — УДАЛЯЕМ (а не запускаем). Обход z2k работает
            # глобально через NFQUEUE на WAN (eth3), точечно — через ip hotspot host policy.
            rm -f /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh 2>/dev/null || true
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart
            sleep 2
            nfq_cnt=0
            if command -v pgrep >/dev/null 2>&1 && pgrep -f nfqws2 >/dev/null 2>&1; then nfq_cnt=1
            elif ps w 2>/dev/null | grep -q '[n]fqws2'; then nfq_cnt=1
            elif ps 2>/dev/null | grep -q '[n]fqws2'; then nfq_cnt=1
            fi
            if [ "${'$'}nfq_cnt" = "0" ]; then
                sleep 2
                if ps w 2>/dev/null | grep -q '[n]fqws2' || ps 2>/dev/null | grep -q '[n]fqws2'; then nfq_cnt=1; fi
            fi
            # Реальная проверка обхода: процесс nfqws2 может крутиться, но не обходить.
            # Проверяем curl'ом сквозь NFQUEUE — если YouTube/Discord оживают, обход точно живой.
            bypass_live=0
            if [ "${'$'}{nfq_cnt:-0}" -gt 0 ]; then
                for _probe in youtube.com discord.com; do
                    _code=${'$'}(curl -sI -L --connect-timeout 4 --max-time 8 -o /dev/null -w "%{http_code}" "https://${'$'}_probe" 2>/dev/null || echo "000")
                    case "${'$'}_code" in
                        2*|3*|4*) bypass_live=1; echo "[OK] Обход работает: ${'$'}_probe -> HTTP ${'$'}_code"; break ;;
                        *) echo "[WARN] ${'$'}_probe -> HTTP ${'$'}_code (обход может не работать)" ;;
                    esac
                done
            fi
            # ФИНАЛЬНЫЙ save: гарантирует, что всё применённое (AP state=up, DHCP pool,
            # dns-server) уйдёт в startup-config. Без этого на сценариях без ISO/AdGuard
            # save вообще не вызывался.
            LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null 2>&1 || true
            if [ "${'$'}{nfq_cnt:-0}" -gt 0 ]; then
                touch /opt/etc/.gor4akov_installed 2>/dev/null || true
                echo "NFQWS_PROCS=${'$'}nfq_cnt"
                echo "BYPASS_LIVE=${'$'}bypass_live"
                echo "INSTALL_DONE"
            else
                echo "NFQWS_PROCS=0"
                echo "[ERR] nfqws2 не запустился — обход НЕ активен, установка НЕ завершена."
            fi
        """.trimIndent()
    }

    val VERIFY_BYPASS_SCRIPT = """
        set +e
        nfq=0
        if command -v pgrep >/dev/null 2>&1 && pgrep -f nfqws2 >/dev/null 2>&1; then nfq=1
        elif ps w 2>/dev/null | grep -q '[n]fqws2'; then nfq=1
        elif ps 2>/dev/null | grep -q '[n]fqws2'; then nfq=1
        fi
        rules=${'$'}(iptables -t mangle -S 2>/dev/null | grep -c NFQUEUE | head -n1 | tr -dc '0-9')
        agh_dns=0; { netstat -ln 2>/dev/null || ss -ln 2>/dev/null; } | grep -q ':53 ' && agh_dns=1
        echo "PROCESSES|nfqws2=${'$'}{nfq:-0}|nfqueue_rules=${'$'}{rules:-0}|dns53=${'$'}agh_dns"
        TMPP="/tmp/.gbox_v_$$"
        rm -f "${'$'}TMPP"
        for host in youtube.com youtu.be discord.com instagram.com x.com rutracker.org chatgpt.com spotify.com soundcloud.com gosuslugi.ru; do
            (
                out=${'$'}(curl -sI -L --connect-timeout 4 --max-time 6 -o /dev/null -w "%{http_code}|%{time_total}" "https://${'$'}host" 2>/dev/null || echo "000|0")
                echo "PROBE|${'$'}host|HTTP_${'$'}{out%|*}|${'$'}{out#*|}" >> "${'$'}TMPP"
            ) &
        done
        wait
        cat "${'$'}TMPP" 2>/dev/null | sort
        rm -f "${'$'}TMPP"
    """.trimIndent()

    fun rollbackGor4akovScript(removeZapret: Boolean): String = """__LONG__
        set +e
        CFG=${'$'}(LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null | tr -d '\r')
        BR_NAME=${'$'}(echo "${'$'}CFG" | awk '/^interface Bridge[0-9]+/{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g){print cur; inb=0; exit} inb=0} END{if(inb && g) print cur}')
        APS=${'$'}(echo "${'$'}CFG" | awk '/^interface WifiMaster[0-9]+\/[^ \t]+/{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g) print cur; inb=0} END{if(inb && g) print cur}')
        # Если нет ни Bridge, ни AP с description Gor4akovNET — нечего откатывать.
        # Раньше функция мол создание сети заново.
        if [ -z "${'$'}BR_NAME" ] && [ -z "${'$'}APS" ]; then
            # проверим, есть ли хоть политика / dhcp pool (могли остаться от старой версии)
            if ! echo "${'$'}CFG" | grep -qE '^ip policy Gor4akovNET|^ip dhcp pool Gor4akovNET'; then
                echo "NOTHING_TO_ROLLBACK"
                echo "Сеть Gor4akovNET не найдена — откатывать нечего."
                exit 0
            fi
        fi
        for ap in ${'$'}APS; do
            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap down" >/dev/null 2>&1
            [ -n "${'$'}BR_NAME" ] && LD_LIBRARY_PATH= ndmc -c "no interface ${'$'}BR_NAME include ${'$'}ap" >/dev/null 2>&1
            LD_LIBRARY_PATH= ndmc -c "no interface ${'$'}ap" >/dev/null 2>&1
        done
        [ -n "${'$'}BR_NAME" ] && LD_LIBRARY_PATH= ndmc -c "no interface ${'$'}BR_NAME" >/dev/null 2>&1
        LD_LIBRARY_PATH= ndmc -c "no ip dhcp pool Gor4akovNET" >/dev/null 2>&1
        LD_LIBRARY_PATH= ndmc -c "no ip policy Gor4akovNET" >/dev/null 2>&1
        if ! { ps w 2>/dev/null | grep -q '[A]dGuardHome' || ps 2>/dev/null | grep -q '[A]dGuardHome'; }; then
            LD_LIBRARY_PATH= ndmc -c "no opkg dns-override" >/dev/null 2>&1 || true
        fi
        LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null 2>&1
        rm -f /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh /opt/etc/.gor4akov_installed
        if [ "${if (removeZapret) 1 else 0}" = "1" ]; then
            for ini in /opt/etc/init.d/S99zapret2 /opt/etc/init.d/S96z2k-webpanel /opt/etc/init.d/S99z2k-scheduler /opt/etc/init.d/S51z2k-warp /opt/etc/init.d/S98tg-tunnel /opt/etc/init.d/S97z2k-http-tunnel; do
                [ -x "${'$'}ini" ] && "${'$'}ini" stop >/dev/null 2>&1 || true
                rm -f "${'$'}ini"
            done
            rm -rf /opt/zapret2 /opt/etc/ndm/netfilter.d/000-zapret2.sh
        else
            [ -f /opt/zapret2/config ] && sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d' /opt/zapret2/config
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
        fi
        echo "ROLLBACK_OK"
    """.trimIndent()

    val ESTIMATE_BACKUP_SIZE_CMD: String get() = BackupRestoreScripts.ESTIMATE_BACKUP_SIZE_CMD

    fun buildFullBackupCmd(includeUserMedia: Boolean): String =
        BackupRestoreScripts.buildFullBackupCmd(includeUserMedia)

    val FULL_BACKUP_TAR_CMD: String get() = BackupRestoreScripts.FULL_BACKUP_TAR_CMD

    fun restoreArchiveCmd(mode: String): String =
        BackupRestoreScripts.restoreArchiveCmd(mode)

    val AUTO_DIAG_REPAIR_SCRIPT = """
        set +e
        echo "=== Автодиагностика Gor4akovNET ==="
        if [ ! -x /opt/bin/opkg ]; then
            echo "[FAIL] Entware (/opt/bin/opkg) не смонтирован"
            exit 1
        fi
        echo "[OK] Entware активен"
        # Логика: если POLICY_NAME остался от старой установки — УДАЛЯЕМ его.
        # Он включает CONNMARK-exclude в S99zapret2 — обход умирает вне указанной политики.
        if grep -q '^POLICY_NAME=' /opt/zapret2/config 2>/dev/null; then
            echo '[FIX] Найден POLICY_NAME — удаляю (ломает обход вне политики)'
            sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d' /opt/zapret2/config 2>/dev/null || true
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
            echo '[OK] POLICY_* удалены, S99zapret2 перезапущен (обход во всех сетях)'
        else
            echo '[OK] Режим общего обхода (без отдельной политики)'
        fi
        if [ -x /opt/etc/init.d/S99zapret2 ]; then
            ps_all=${'$'}(ps w 2>/dev/null || ps 2>/dev/null)
            nfq=${'$'}(printf '%s\n' "${'$'}ps_all" | grep -c '[n]fqws2' | head -n1 | tr -dc '0-9')
            if [ "${'$'}{nfq:-0}" -eq 0 ]; then
                echo "[FIX] nfqws2 был остановлен — запускаю S99zapret2..."
                /opt/etc/init.d/S99zapret2 restart
            else
                echo "[OK] nfqws2 работает (${'$'}nfq проц.)"
            fi
        else
            echo "[WARN] zapret2 не установлен"
        fi
        # Хук 090-gor4akov-policy.sh ставил CONNMARK-exclude на весь трафик, кроме 77.x —
        # это ломало обход в Home/Guest. Если остался от старой установки — удаляем.
        if [ -f /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh ]; then
            rm -f /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh
            echo '[FIX] Удалён устаревший хук 090-gor4akov-policy.sh (ломал обход в Home)'
        fi
        echo "[OK] Netfilter правила синхронизированы"

        # === Реальная проверка: интернет и обход ===
        echo "=== Проверка связи ==="
        WAN_IP=${'$'}(ip route show default 2>/dev/null | awk '{for(i=1;i<=NF;i++)if(${'$'}i=="via"){print ${'$'}(i+1);exit}}')
        if [ -n "${'$'}WAN_IP" ] && ping -c 1 -W 2 "${'$'}WAN_IP" >/dev/null 2>&1; then
            echo "[OK] Шлюз провайдера ${'$'}WAN_IP пингуется"
        else
            echo "[WARN] Шлюз провайдера недоступен — проверьте WAN-кабель"
        fi
        DNS_RC=${'$'}(nslookup youtube.com 2>/dev/null | grep -c '^Address')
        if [ "${'$'}{DNS_RC:-0}" -ge 2 ]; then
            echo "[OK] DNS резолвит youtube.com"
        else
            echo "[WARN] DNS не резолвит youtube.com — проверьте opkg dns-override / AdGuard"
        fi
        BYPASS_CODES=""
        for _h in youtube.com discord.com; do
            _c=${'$'}(curl -sI -L --connect-timeout 4 --max-time 8 -o /dev/null -w "%{http_code}" "https://${'$'}_h" 2>/dev/null || echo "000")
            BYPASS_CODES="${'$'}{BYPASS_CODES}${'$'}{BYPASS_CODES:+, }${'$'}_h=${'$'}_c"
            case "${'$'}_c" in
                2*|3*|4*) ;;
                *) echo "[WARN] ${'$'}_h -> HTTP ${'$'}_c — обход может не работать, смотри nfqws2 / списки" ;;
            esac
        done
        echo "[INFO] Тест обхода: ${'$'}BYPASS_CODES"

        # Сброс кэша DNS после фикса политики/хука — чтобы клиенты не сидели на отравленном DNS
        if [ -x /opt/etc/init.d/S99AdGuardHome ]; then
            /opt/etc/init.d/S99AdGuardHome restart >/dev/null 2>&1 || true
            echo "[OK] AdGuard перезапущен"
        fi
        echo "=== Диагностика завершена ==="
    """.trimIndent()
}
