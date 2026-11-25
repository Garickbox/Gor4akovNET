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
        br_ssid=${'$'}(echo "${'$'}cfg" | awk '
            /^interface WifiMaster/ { cur=${'$'}2; inw=1; isg=0; ss="" }
            inw && /^[ \t]+description Gor4akovNET/ { isg=1 }
            inw && /^[ \t]+ssid / { sub(/^[ \t]+ssid /,""); ss=${'$'}0 }
            inw && /^!/ { if (inw && isg && ss!="") { print ss; exit } inw=0 }
            END { if (inw && isg && ss!="") print ss }
        ')
        echo "SVC|${'$'}ent|${'$'}z2k_inst|${'$'}z2k_run|${'$'}z2k_mode|${'$'}nfq|${'$'}ipt_cnt|${'$'}agh_inst|${'$'}agh_run|${'$'}dns_ov|${'$'}warp_inst|${'$'}warp_run|${'$'}tg_run|${'$'}db_run|${'$'}pol_name|${'$'}pol_act|${'$'}br_name|${'$'}br_sub|${'$'}br_ssid|${'$'}inst_mark"
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
            # GNU tar обязателен: busybox tar в /opt/usr/bin НЕ умеет -c и --strip-components
            TAR=/opt/bin/tar
            [ -x "${'$'}TAR" ] || TAR=tar
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
                for pkg in libmnl libnetfilter-queue libnfnetlink libcap zlib curl flock unzip iptables xtables-addons_legacy kmod_ndms ipset openssl-util; do
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
                opkg install libmnl libnetfilter-queue libnfnetlink libcap zlib curl flock unzip iptables xtables-addons_legacy kmod_ndms ipset openssl-util || true
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
                ${'$'}{TAR} -xzf /opt/gor4akov-offline/z2k-repo.tar.gz -C /opt/gor4akov-offline/z2k_src --strip-components=1
                cp -a /opt/gor4akov-offline/z2k_src/* "${'$'}WORK_STAGE/"
                echo "[OK] z2k-enhanced подготовлен локально"
            else
                echo "[INFO] Пробую скачать z2k-enhanced из сети..."
                curl -fsSL "https://github.com/necronicle/z2k/archive/refs/heads/z2k-enhanced.tar.gz" -o /opt/gor4akov-offline/z2k-repo.tar.gz && \
                ${'$'}{TAR} -xzf /opt/gor4akov-offline/z2k-repo.tar.gz -C "${'$'}WORK_STAGE" --strip-components=1
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

            SUB_PREFIX="192.168.77"
            if [ "${'$'}ISO_FLAG" = "1" ]; then
                BR_NAME="${'$'}(echo "${'$'}CFG" | awk '/^interface Bridge[0-9]+/{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g){print cur; exit} inb=0} END{if(inb && g) print cur}')"
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
                    LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET enable" >/dev/null

                    for master in WifiMaster0 WifiMaster1; do
                        if echo "${'$'}CFG" | grep -q "^interface ${'$'}{master}${'$'}"; then
                            ap="${'$'}(echo "${'$'}CFG" | awk -v m="^interface ${'$'}{master}/" '${'$'}0 ~ m{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g){print cur; exit} inb=0} END{if(inb && g) print cur}')"
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
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}{BR_NAME} include ${'$'}ap" >/dev/null
                            LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap up" >/dev/null
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

            ZCFG="/opt/zapret2/config"
            if [ -f "${'$'}ZCFG" ]; then
                sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d' "${'$'}ZCFG"
                if [ "${'$'}ISO_FLAG" = "1" ]; then
                    printf 'POLICY_NAME="Gor4akovNET"\nPOLICY_EXCLUDE=0\n' >> "${'$'}ZCFG"
                fi
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
                        LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null
                        echo "[OK] AdGuard Home v1.0.0 запущен (:3000 / DNS :53), opkg dns-override включён"
                    else
                        echo "[WARN] AdGuard Home не слушает :53 — dns-override НЕ включён (безопасность DNS). Проверьте Монитор → DNS."
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
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart
            sh /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh >/dev/null 2>&1 || true
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
            if [ "${'$'}{nfq_cnt:-0}" -gt 0 ]; then
                touch /opt/etc/.gor4akov_installed 2>/dev/null || true
                echo "NFQWS_PROCS=${'$'}nfq_cnt"
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
        CFG=${'$'}(LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null)
        BR_NAME=${'$'}(echo "${'$'}CFG" | awk '/^interface Bridge[0-9]+/{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g){print cur; exit} inb=0} END{if(inb && g) print cur}')
        APS=${'$'}(echo "${'$'}CFG" | awk '/^interface WifiMaster[0-9]+\/[^ \t]+/{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g) print cur; inb=0} END{if(inb && g) print cur}')
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

    val ESTIMATE_BACKUP_SIZE_CMD = """
        set +e
        # /opt целиком (все пакеты и файлы пользователя) минус мусор установки и логи.
        core_kb=${'$'}(du -sk /opt 2>/dev/null | awk '{print ${'$'}1+0}')
        for _ex in /opt/gor4akov-offline /opt/z2k-work /opt/z2k-rollback /opt/tmp /opt/var/log; do
            [ -e "${'$'}_ex" ] && core_kb=${'$'}((core_kb - ${'$'}(du -sk "${'$'}_ex" 2>/dev/null | awk '{print ${'$'}1+0}')))
        done
        [ "${'$'}core_kb" -lt 0 ] && core_kb=0
        media_kb=0
        mounts=""
        opt_dev=${'$'}(awk '${'$'}2=="/opt"{print ${'$'}1; exit}' /proc/mounts 2>/dev/null)
        opt_ino=${'$'}(ls -id /opt 2>/dev/null | awk '{print ${'$'}1}')
        MNT_LIST="/tmp/.gbox_mnts_${'$'}${'$'}"
        awk '{
            gsub(/\\040/," ",${'$'}2)
            if (${'$'}2 ~ /^\/(tmp\/mnt|media|mnt)\/.+/ || ${'$'}2 == "/storage") {
                if (${'$'}3 != "tmpfs" && ${'$'}3 != "devtmpfs" && ${'$'}3 != "proc" && ${'$'}3 != "sysfs") print ${'$'}2
            }
        }' /proc/mounts 2>/dev/null | awk '!seen[${'$'}0]++' > "${'$'}MNT_LIST"
        if [ ! -s "${'$'}MNT_LIST" ]; then
            for m in /tmp/mnt/* /media/* /mnt/* /storage; do
                [ -d "${'$'}m" ] || continue
                m_fs=${'$'}(df -PT "${'$'}m" 2>/dev/null | awk 'NR==2{print ${'$'}2}')
                [ "${'$'}m_fs" = "tmpfs" ] && continue
                printf '%s\n' "${'$'}m" >> "${'$'}MNT_LIST"
            done
        fi
        while IFS= read -r m; do
            [ -n "${'$'}m" ] && [ -d "${'$'}m" ] || continue
            df_info=${'$'}(df -Pm "${'$'}m" 2>/dev/null | awk 'NF==1{d=${'$'}0; getline; ${'$'}0=d" "${'$'}0} NR==2{print ${'$'}1"|"(${'$'}2+0)"|"(${'$'}3+0)"|"(${'$'}4+0)}')
            m_dev=${'$'}(printf '%s' "${'$'}df_info" | cut -d'|' -f1)
            m_tot=${'$'}(printf '%s' "${'$'}df_info" | cut -d'|' -f2)
            m_used=${'$'}(printf '%s' "${'$'}df_info" | cut -d'|' -f3)
            m_free=${'$'}(printf '%s' "${'$'}df_info" | cut -d'|' -f4)
            ms=${'$'}(du -sk "${'$'}m" 2>/dev/null | awk '{print ${'$'}1+0}')
            opt_tag=""
            if [ -n "${'$'}opt_dev" ] && [ "${'$'}m_dev" = "${'$'}opt_dev" ]; then
                m_ino=${'$'}(ls -id "${'$'}m" 2>/dev/null | awk '{print ${'$'}1}')
                if [ -n "${'$'}opt_ino" ] && [ "${'$'}m_ino" = "${'$'}opt_ino" ]; then
                    opt_tag=", корень /opt"
                    ms=0
                else
                    for sub in "${'$'}m"/* "${'$'}m"/*/*; do
                        [ -d "${'$'}sub" ] || continue
                        sub_ino=${'$'}(ls -id "${'$'}sub" 2>/dev/null | awk '{print ${'$'}1}')
                        if { [ -n "${'$'}opt_ino" ] && [ "${'$'}sub_ino" = "${'$'}opt_ino" ]; } || { [ -x "${'$'}sub/bin/opkg" ] && [ -d "${'$'}sub/etc/init.d" ]; }; then
                            opt_sub_kb=${'$'}(du -sk "${'$'}sub" 2>/dev/null | awk '{print ${'$'}1+0}')
                            ms=${'$'}((ms - opt_sub_kb))
                            [ "${'$'}ms" -lt 0 ] && ms=0
                            opt_tag=", Entware: ${'$'}{sub##*/}"
                            break
                        fi
                    done
                fi
            fi
            media_kb=${'$'}((media_kb + ms))
            mounts="${'$'}{mounts}${'$'}{mounts:+; }${'$'}m (~${'$'}((ms/1024))MB данных, занято ${'$'}{m_used:-0}/${'$'}{m_tot:-0}MB, своб. ${'$'}{m_free:-0}MB${'$'}opt_tag)"
        done < "${'$'}MNT_LIST"
        rm -f "${'$'}MNT_LIST"
        echo "EST|${'$'}((core_kb/1024))|${'$'}((media_kb/1024))|${'$'}{mounts:-none}"
    """.trimIndent()

    fun buildFullBackupCmd(includeUserMedia: Boolean): String = """__BACKUP__
        set +e
        # GNU tar из Entware: busybox tar в /opt/usr/bin/tar НЕ умеет -c (создание)
        TAR=/opt/bin/tar
        [ -x "${'$'}TAR" ] || TAR=tar
        # /opt отсутствует (флешка отвалилась) — полный бэкап собрал бы только 4 txt по 1 КБ,
        # а UI принял бы это за валидный «полный бэкап v2». Явный fail -> на Android сработает RCI-fallback.
        if [ ! -x /opt/bin/opkg ] || [ ! -d /opt/etc ]; then
            echo "FATAL: /opt отсутствует или Entware не смонтирован — полный бэкап невозможен." >&2
            exit 1
        fi
        TMPB="/tmp/.gbox_bkp_${'$'}${'$'}"
        mkdir -p "${'$'}TMPB"
        LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" > "${'$'}TMPB/running-config.txt" 2>/dev/null || true
        LD_LIBRARY_PATH= /bin/ndmc -c "show startup-config" > "${'$'}TMPB/startup-config.txt" 2>/dev/null || cp -f "${'$'}TMPB/running-config.txt" "${'$'}TMPB/startup-config.txt" 2>/dev/null || true
        {
            echo "=== DATE ===" && date
            echo "=== UNAME ===" && uname -a
            echo "=== VERSION ===" && LD_LIBRARY_PATH= /bin/ndmc -c "show version" 2>/dev/null
            echo "=== DF ===" && df -h 2>/dev/null
            echo "=== MOUNTS ===" && cat /proc/mounts 2>/dev/null
            echo "=== OPKG_LIST ===" && /opt/bin/opkg list-installed 2>/dev/null
        } > "${'$'}TMPB/router-info.txt" 2>/dev/null || true
        printf '{"version":2,"arch":"%s","include_media":%s}\n' "${'$'}(uname -m)" "${if (includeUserMedia) "true" else "false"}" > "${'$'}TMPB/manifest.json"

        set -- -C "${'$'}TMPB" running-config.txt startup-config.txt router-info.txt manifest.json
        # ВЕСЬ /opt — никаких белых списков. Исплючения только масками ниже.
        [ -d /opt ] && set -- "${'$'}@" -C / opt

        opt_excl=""
        # Полный дамп USB/Storage (все внешние/внутренние носители без дублирования папки Entware /opt)
        if [ "${if (includeUserMedia) 1 else 0}" = "1" ]; then
            opt_dev=${'$'}(awk '${'$'}2=="/opt"{print ${'$'}1; exit}' /proc/mounts 2>/dev/null)
            opt_ino=${'$'}(ls -id /opt 2>/dev/null | awk '{print ${'$'}1}')
            MNT_LIST="${'$'}TMPB/.mnts"
            awk '{
                gsub(/\\040/," ",${'$'}2)
                if (${'$'}2 ~ /^\/(tmp\/mnt|media|mnt)\/.+/ || ${'$'}2 == "/storage") {
                    if (${'$'}3 != "tmpfs" && ${'$'}3 != "devtmpfs" && ${'$'}3 != "proc" && ${'$'}3 != "sysfs") print ${'$'}2
                }
            }' /proc/mounts 2>/dev/null | awk '!seen[${'$'}0]++' > "${'$'}MNT_LIST"
            if [ ! -s "${'$'}MNT_LIST" ]; then
                for m in /tmp/mnt/* /media/* /mnt/* /storage; do
                    [ -d "${'$'}m" ] || continue
                    m_fs=${'$'}(df -PT "${'$'}m" 2>/dev/null | awk 'NR==2{print ${'$'}2}')
                    [ "${'$'}m_fs" = "tmpfs" ] && continue
                    printf '%s\n' "${'$'}m" >> "${'$'}MNT_LIST"
                done
            fi
            while IFS= read -r m; do
                [ -n "${'$'}m" ] && [ -d "${'$'}m" ] || continue
                m_dev=${'$'}(df -Pm "${'$'}m" 2>/dev/null | awk 'NF==1{d=${'$'}0; getline; ${'$'}0=d" "${'$'}0} NR==2{print ${'$'}1}')
                skip_m=0
                if [ -n "${'$'}opt_dev" ] && [ "${'$'}m_dev" = "${'$'}opt_dev" ]; then
                    m_ino=${'$'}(ls -id "${'$'}m" 2>/dev/null | awk '{print ${'$'}1}')
                    if [ -n "${'$'}opt_ino" ] && [ "${'$'}m_ino" = "${'$'}opt_ino" ]; then
                        skip_m=1
                    else
                        for sub in "${'$'}m"/* "${'$'}m"/*/*; do
                            [ -d "${'$'}sub" ] || continue
                            sub_ino=${'$'}(ls -id "${'$'}sub" 2>/dev/null | awk '{print ${'$'}1}')
                            if { [ -n "${'$'}opt_ino" ] && [ "${'$'}sub_ino" = "${'$'}opt_ino" ]; } || { [ -x "${'$'}sub/bin/opkg" ] && [ -d "${'$'}sub/etc/init.d" ]; }; then
                                opt_excl="${'$'}{sub#/}"
                                break
                            fi
                        done
                    fi
                fi
                [ "${'$'}skip_m" = "0" ] && set -- "${'$'}@" -C / "${'$'}{m#/}"
            done < "${'$'}MNT_LIST"
        fi

        # Пишем архив на флешку (/opt), а НЕ в /tmp (tmpfs = RAM, OOM) и НЕ в SSH-стрим.
        # После проверки целостности отдаём содержимое в stdout через cat.
        STAGE="/opt/.gbox_backup_stage.${'$'}${'$'}.tar.gz"
        rm -f "${'$'}STAGE"
        if [ -n "${'$'}opt_excl" ]; then
            ${'$'}{TAR} --exclude='opt/.gbox_backup_stage*' --exclude='opt/gor4akov-offline' --exclude='opt/z2k-work' --exclude='opt/z2k-rollback' --exclude='opt/tmp' --exclude='opt/var/log' --exclude='*.log' --exclude='*.swap' --exclude='swapfile' --exclude="${'$'}opt_excl" \
                -czf "${'$'}STAGE" "${'$'}@"
        else
            ${'$'}{TAR} --exclude='opt/.gbox_backup_stage*' --exclude='opt/gor4akov-offline' --exclude='opt/z2k-work' --exclude='opt/z2k-rollback' --exclude='opt/tmp' --exclude='opt/var/log' --exclude='*.log' --exclude='*.swap' --exclude='swapfile' \
                -czf "${'$'}STAGE" "${'$'}@"
        fi
        tar_rc=${'$'}?
        rm -rf "${'$'}TMPB"
        if [ "${'$'}tar_rc" -ne 0 ] || [ ! -s "${'$'}STAGE" ]; then
            rm -f "${'$'}STAGE"
            echo "FATAL: tar не смог создать архив (rc=${'$'}tar_rc)" >&2
            exit 1
        fi
        if ! ${'$'}{TAR} -tzf "${'$'}STAGE" >/dev/null 2>&1; then
            rm -f "${'$'}STAGE"
            echo "FATAL: архив повреждён (${'$'}{TAR} -tzf fail)" >&2
            exit 1
        fi
        cat "${'$'}STAGE"
        cat_rc=${'$'}?
        rm -f "${'$'}STAGE"
        exit ${'$'}cat_rc
    """.trimIndent()

    val FULL_BACKUP_TAR_CMD: String get() = buildFullBackupCmd(includeUserMedia = false)

    fun restoreArchiveCmd(mode: String): String = """__LONG__
        set +e
        TAR=/opt/bin/tar
        [ -x "${'$'}TAR" ] || TAR=tar
        ARCH="/opt/.gbox_restore.tar.gz"
        [ -s "${'$'}ARCH" ] || { echo "ERR: Архив не найден на /opt/.gbox_restore.tar.gz"; exit 1; }
        if [ "$mode" = "opt_full" ] || [ "$mode" = "system_full" ]; then
            if [ ! -d /opt/etc ] || [ ! -x /opt/bin/opkg ]; then
                echo "[FATAL] Entware /opt не смонтирован. Сначала установи Entware на флешку."
                echo "PREFLIGHT_FAIL"
                rm -f "${'$'}ARCH"
                exit 1
            fi
        fi
        find /opt -maxdepth 1 -name '.gbox_rst_*' -type d -exec rm -rf {} + 2>/dev/null || true
        RST="/opt/.gbox_rst_${'$'}${'$'}"
        rm -rf "${'$'}RST" && mkdir -p "${'$'}RST"
        echo "=== Распаковка архива ==="
        "${'$'}TAR" -xzf "${'$'}ARCH" -C "${'$'}RST" || { echo "ERR: битый архив (сохранён для повтора)"; rm -rf "${'$'}RST"; exit 1; }
        echo "OK: архив распакован"

        PRE="/opt/.gbox_prerestore.${'$'}${'$'}.tar.gz"
        echo "=== Снимок текущего /opt ==="
        "${'$'}TAR" --exclude="opt/.gbox_prerestore.*" --exclude="opt/.gbox_restore.tar.gz" --exclude="opt/.gbox_rst_*" --exclude="opt/gor4akov-offline" --exclude="opt/z2k-work" --exclude="opt/tmp" --exclude="opt/var/log" --exclude="*.log" -czf "${'$'}PRE" -C / opt 2>/dev/null
        PRE_RC=${'$'}?
        if [ "${'$'}PRE_RC" -ne 0 ] || [ ! -s "${'$'}PRE" ]; then
            echo "[WARN] Pre-snapshot не создан (нет места?). Работаем БЕЗ отката."
            PRE=""
        else
            PRE_KB=${'$'}(du -sk "${'$'}PRE" 2>/dev/null | awk '{print ${'$'}1}')
            echo "[OK] Снимок: ${'$'}PRE (${'$'}PRE_KB КБ)"
        fi

        echo "=== Остановка служб ==="
        for ini in /opt/etc/init.d/S99zapret2 /opt/etc/init.d/S99AdGuardHome /opt/etc/init.d/S99adguardhome /opt/etc/init.d/S10cron; do
            [ -x "${'$'}ini" ] && "${'$'}ini" stop >/dev/null 2>&1 || true
        done
        killall nfqws2 2>/dev/null || true
        killall AdGuardHome 2>/dev/null || true
        sleep 1

        RESTORE_OK=1
        if [ "$mode" = "bypass" ]; then
            echo "=== bypass: /opt/zapret2 + netfilter.d ==="
            if [ -d "${'$'}RST/opt/zapret2" ]; then
                rm -rf /opt/zapret2 && cp -a "${'$'}RST/opt/zapret2" /opt/zapret2 || RESTORE_OK=0
            fi
            if [ -d "${'$'}RST/opt/etc/ndm/netfilter.d" ]; then
                rm -rf /opt/etc/ndm/netfilter.d && mkdir -p /opt/etc/ndm/netfilter.d
                cp -a "${'$'}RST/opt/etc/ndm/netfilter.d/." /opt/etc/ndm/netfilter.d/ 2>/dev/null || RESTORE_OK=0
            fi
        else
            echo "=== ПОЛНАЯ замена /opt ==="
            for d in etc zapret2 root home var bin sbin lib libexec share usr local; do
                if [ -d "${'$'}RST/opt/${'$'}d" ]; then
                    rm -rf "/opt/${'$'}d" && cp -a "${'$'}RST/opt/${'$'}d" "/opt/${'$'}d" || RESTORE_OK=0
                    echo "  OK: /opt/${'$'}d"
                fi
            done
            mkdir -p /opt/tmp /opt/var/log 2>/dev/null || true
        fi

        if [ "${'$'}RESTORE_OK" -ne 1 ]; then
            echo "[FATAL] Ошибка распаковки -> откат pre-snapshot"
            if [ -n "${'$'}PRE" ] && [ -s "${'$'}PRE" ]; then
                "${'$'}TAR" -xzf "${'$'}PRE" -C / 2>&1 | tail -3
                echo "[OK] Pre-snapshot восстановлен"
            else
                echo "[ERR] Pre-snapshot отсутствует"
            fi
            rm -rf "${'$'}RST"
            exit 1
        fi

        if [ "$mode" = "system_full" ]; then
            echo "=== system_full: носители + NDMS ==="
            for d in tmp/mnt media mnt storage; do
                if [ -d "${'$'}RST/${'$'}d" ]; then
                    mkdir -p "/${'$'}d" 2>/dev/null || true
                    cp -af "${'$'}RST/${'$'}d/." "/${'$'}d/" 2>/dev/null || true
                fi
            done
            CFG_SRC="${'$'}RST/running-config.txt"
            [ -s "${'$'}CFG_SRC" ] || CFG_SRC="${'$'}RST/startup-config.txt"
            if [ -s "${'$'}CFG_SRC" ]; then
                echo "=== NDMS конфиг (как в бэкапе, MAC+IP) ==="
                err_cnt=0
                ok_cnt=0
                tr -d '\r' < "${'$'}CFG_SRC" | awk '
                  /^[[:space:]]*${'$'}/ {next}
                  /^[[:space:]]*#/ {next}
                  /^[[:space:]]*!/ {if (${'$'}0 ~ /^!${'$'}/) blk=""; next}
                  /^[[:space:]]/ {sub(/^[[:space:]]+/,""); cmd=(blk!="" ? blk" "${'$'}0 : ${'$'}0); if (cmd !~ /^(user|no user|system mode|opkg disk|no opkg disk) /) print cmd; next}
                  {blk=${'$'}0; if (blk !~ /^(user|no user|system mode|opkg disk|no opkg disk) /) print blk}
                ' > "${'$'}RST/.ndms_flat.txt"
                while IFS= read -r line; do
                    [ -n "${'$'}line" ] || continue
                    out_c=${'$'}(LD_LIBRARY_PATH= /bin/ndmc -c "${'$'}line" 2>&1)
                    if printf '%s' "${'$'}out_c" | grep -qiE 'error|invalid|no such command'; then
                        err_cnt=${'$'}((err_cnt + 1))
                    else
                        ok_cnt=${'$'}((ok_cnt + 1))
                    fi
                done < "${'$'}RST/.ndms_flat.txt"
                LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1 || true
                echo "[OK] NDMS: применено ${'$'}ok_cnt, ошибок ${'$'}err_cnt"
            fi
            # Auto-UUID: после restore могла быть другая флешка. Привязываем opkg disk к текущей.
            OPT_DEV=${'$'}(mount 2>/dev/null | awk '${'$'}3=="/opt"{print ${'$'}1;exit}')
            if [ -n "${'$'}OPT_DEV" ]; then
                OPT_UUID=${'$'}(blkid "${'$'}OPT_DEV" 2>/dev/null | sed -n 's/.*UUID="\([^"]*\)".*/\1/p')
                if [ -n "${'$'}OPT_UUID" ]; then
                    LD_LIBRARY_PATH= /bin/ndmc -c "opkg disk ${'$'}OPT_UUID:" >/dev/null 2>&1
                    LD_LIBRARY_PATH= /bin/ndmc -c "opkg initrc /opt/etc/init.d/rc.unslung" >/dev/null 2>&1
                    LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
                    echo "[OK] opkg disk привязан к текущей флешке: ${'$'}OPT_UUID"
                else
                    echo "[WARN] UUID текущей /opt не определён"
                fi
            fi
        fi

        echo "=== Запуск служб ==="
        for ini in /opt/etc/init.d/S99zapret2 /opt/etc/init.d/S99AdGuardHome /opt/etc/init.d/S99adguardhome /opt/etc/init.d/S10cron; do
            [ -x "${'$'}ini" ] && "${'$'}ini" start >/dev/null 2>&1 || true
        done
        [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
        sleep 2
        NFQ=${'$'}(ps w 2>/dev/null | grep -c "[n]fqws2")
        if [ "${'$'}NFQ" -gt 0 ]; then
            echo "=== Готово: nfqws2 OK (${'$'}NFQ) ==="
        else
            echo "=== ВНИМАНИЕ: nfqws2 НЕ стартовал ==="
        fi

        rm -f "${'$'}ARCH"
        rm -rf "${'$'}RST"
        if [ -n "${'$'}PRE" ] && [ -s "${'$'}PRE" ]; then
            echo "[OK] Pre-snapshot сохранён: ${'$'}PRE"
        fi
        echo "INSTALL_RESTORE_DONE"
    """.trimIndent()
    val AUTO_DIAG_REPAIR_SCRIPT = """
        set +e
        echo "=== Автодиагностика Gor4akovNET ==="
        if [ ! -x /opt/bin/opkg ]; then
            echo "[FAIL] Entware (/opt/bin/opkg) не смонтирован"
            exit 1
        fi
        echo "[OK] Entware активен"
        pol_name=${'$'}(awk -F= '/^POLICY_NAME=/{gsub(/["'"'"'\r\n]/,"",${'$'}2); print ${'$'}2; exit}' /opt/zapret2/config 2>/dev/null)
        if [ -n "${'$'}pol_name" ]; then
            rc_pol=${'$'}(LD_LIBRARY_PATH= /bin/ndmc -c "show running-config" 2>/dev/null)
            if ! printf '%s\n' "${'$'}rc_pol" | grep -q "^ip policy ${'$'}pol_name${'$'}"; then
                echo "[FIX] Создаю отсутствующую политику NDMS: ${'$'}pol_name..."
                LD_LIBRARY_PATH= /bin/ndmc -c "ip policy ${'$'}pol_name" >/dev/null 2>&1
                LD_LIBRARY_PATH= /bin/ndmc -c "ip policy ${'$'}pol_name description Gor4akovNET" >/dev/null 2>&1
                LD_LIBRARY_PATH= /bin/ndmc -c "system configuration save" >/dev/null 2>&1
            else
                echo "[OK] Политика ${'$'}pol_name активна"
            fi
        else
            echo "[OK] Режим общего обхода (без отдельной политики)"
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
        [ -x /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh ] && sh /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh
        echo "[OK] Netfilter правила синхронизированы"
    """.trimIndent()
}
