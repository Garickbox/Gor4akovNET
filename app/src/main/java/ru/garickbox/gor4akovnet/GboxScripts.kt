package ru.garickbox.gor4akovnet

object GboxScripts {

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
        zram_used=${'$'}(awk '/zram/{u+=${'$'}3} END{print int(u/1024)}' /proc/swaps 2>/dev/null || echo 0)
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
        nfq=${'$'}(ps | grep -c '[n]fqws2')
        z2k_run=0; [ "${'$'}nfq" -gt 0 ] && z2k_run=1
        z2k_mode=${'$'}(awk -F= '/^MODE=|^Z2K_PRESET=/{v=${'$'}2} END{gsub(/"/,"",v); print (v==""?"auto":v)}' /opt/zapret2/config 2>/dev/null)
        ipt_cnt=${'$'}(iptables -t mangle -S 2>/dev/null | grep -c NFQUEUE)
        agh_inst=0; [ -x /opt/sbin/AdGuardHome ] || [ -x /opt/bin/AdGuardHome ] && agh_inst=1
        agh_run=0; ps | grep -q '[A]dGuardHome' && agh_run=1
        cfg=${'$'}(LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null)
        dns_ov=0; echo "${'$'}cfg" | grep -q "opkg dns-override" && dns_ov=1
        warp_inst=0; [ -x /opt/sbin/z2k-warpd ] || [ -x /opt/zapret2/z2k-warp.sh ] && warp_inst=1
        warp_run=0; ps | grep -q '[z]2k-warpd' && warp_run=1
        tg_run=0; ps | grep -q '[t]g-mtproxy-client' && tg_run=1
        db_run=0; ps | grep -q '[d]ropbear' && db_run=1
        pol_name=${'$'}(awk -F= '/^POLICY_NAME=/{gsub(/["'"'"']/,"",${'$'}2); print ${'$'}2; exit}' /opt/zapret2/config 2>/dev/null)
        [ -z "${'$'}pol_name" ] && pol_name="Gor4akovNET"
        pol_act=0; echo "${'$'}cfg" | grep -q "ip policy Gor4akovNET" && pol_act=1
        br_name=""; br_sub=""; br_ssid=""
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
            inw && /^[ \t]+ssid / { ss=${'$'}2 }
            inw && /^!/ { if (isg && ss!="") { print ss; exit } inw=0 }
        ')
        echo "SVC|${'$'}ent|${'$'}z2k_inst|${'$'}z2k_run|${'$'}z2k_mode|${'$'}nfq|${'$'}ipt_cnt|${'$'}agh_inst|${'$'}agh_run|${'$'}dns_ov|${'$'}warp_inst|${'$'}warp_run|${'$'}tg_run|${'$'}db_run|${'$'}pol_name|${'$'}pol_act|${'$'}br_name|${'$'}br_sub|${'$'}br_ssid"
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

    fun toggleDevicePolicy(mac: String, enableGor4akov: Boolean): String = """
        set +e
        if [ "${if (enableGor4akov) 1 else 0}" = "1" ]; then
            LD_LIBRARY_PATH= ndmc -c "ip hotspot host $mac policy Gor4akovNET"
        else
            LD_LIBRARY_PATH= ndmc -c "no ip hotspot host $mac policy"
        fi
        LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null 2>&1
        [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
        echo "OK"
    """.trimIndent()

    fun toggleDeviceQos(mac: String, highPriority: Boolean): String = """
        set +e
        if [ "${if (highPriority) 1 else 0}" = "1" ]; then
            LD_LIBRARY_PATH= ndmc -c "ip hotspot host $mac qos class 1"
        else
            LD_LIBRARY_PATH= ndmc -c "no ip hotspot host $mac qos"
        fi
        LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null 2>&1
        echo "OK"
    """.trimIndent()

    fun buildOfflineAndBypassInstallScript(
        ssid: String,
        wifiPass: String,
        isolatedMode: Boolean,
        installAdguard: Boolean,
        enableWarp: Boolean
    ): String {
        val safeSsid = ssid.replace("\"", "").replace("'", "").replace(" ", "_").ifBlank { "Gor4akovNET" }
        val safePass = wifiPass.replace("\"", "").replace("'", "").ifBlank { "gor4akov2026" }
        val isoFlag = if (isolatedMode) "1" else "0"
        val aghFlag = if (installAdguard) "1" else "0"
        val warpFlag = if (enableWarp) "1" else "0"

        return """__LONG__
            set +e
            export PATH="/opt/bin:/opt/sbin:/opt/usr/bin:/opt/usr/sbin:/bin:/sbin:/usr/bin:/usr/sbin"
            export TMPDIR=/opt/tmp
            mkdir -p /opt/tmp /opt/etc/opkg /opt/etc/ndm/netfilter.d /opt/gor4akov-offline/ipks

            echo "=== [1/6] Установка локальных системных пакетов ==="
            if [ -s /opt/gor4akov-offline/ipks.tar.gz ]; then
                rm -rf /opt/gor4akov-offline/ipks/*
                tar -xzf /opt/gor4akov-offline/ipks.tar.gz -C /opt/gor4akov-offline/ipks/
                cp -f /opt/etc/opkg.conf /opt/etc/opkg.conf.bak 2>/dev/null || true
                printf 'src/gz gor4akov_local file:///opt/gor4akov-offline/ipks\n' > /opt/etc/opkg.conf.local
                grep -E '^(dest|option|arch)' /opt/etc/opkg.conf >> /opt/etc/opkg.conf.local 2>/dev/null || true
                opkg -f /opt/etc/opkg.conf.local update >/dev/null 2>&1 || true
                for pkg in libmnl libnetfilter-queue libnfnetlink libcap zlib curl flock unzip iptables xtables-addons_legacy kmod_ndms ipset openssl-util; do
                    if ! opkg list-installed | grep -q "^${'$'}{pkg} "; then
                        opkg -f /opt/etc/opkg.conf.local install "${'$'}pkg" >/dev/null 2>&1 || opkg -f /opt/etc/opkg.conf.local install /opt/gor4akov-offline/ipks/"${'$'}pkg"_*.ipk >/dev/null 2>&1 || true
                    fi
                done
                echo "[OK] Критичные пакеты развёрнуты локально из APK"
            else
                opkg update || true
                opkg install libmnl libnetfilter-queue libnfnetlink libcap zlib curl flock unzip iptables xtables-addons_legacy kmod_ndms ipset openssl-util || true
            fi

            echo "=== [2/6] Распаковка z2k-enhanced и nfqws2 ==="
            WORK_STAGE="/opt/z2k-work"
            rm -rf "${'$'}WORK_STAGE" /opt/gor4akov-offline/z2k_src
            mkdir -p "${'$'}WORK_STAGE/zapret2_build" /opt/gor4akov-offline/z2k_src
            if [ -s /opt/gor4akov-offline/z2k-repo.tar.gz ]; then
                tar -xzf /opt/gor4akov-offline/z2k-repo.tar.gz -C /opt/gor4akov-offline/z2k_src --strip-components=1
                cp -a /opt/gor4akov-offline/z2k_src/* "${'$'}WORK_STAGE/"
                echo "[OK] z2k-enhanced подготовлен локально"
            else
                echo "[INFO] Пробую скачать z2k-enhanced из сети..."
                curl -fsSL "https://github.com/necronicle/z2k/archive/refs/heads/z2k-enhanced.tar.gz" -o /opt/gor4akov-offline/z2k-repo.tar.gz && \
                tar -xzf /opt/gor4akov-offline/z2k-repo.tar.gz -C "${'$'}WORK_STAGE" --strip-components=1
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

            # Source z2k.sh without invoking main
            if [ -f "${'$'}WORK_STAGE/z2k.sh" ]; then
                sed '/^main "\${'$'}@"/d' "${'$'}WORK_STAGE/z2k.sh" > "${'$'}WORK_STAGE/.z2k_lib_only.sh"
                . "${'$'}WORK_STAGE/.z2k_lib_only.sh"
                for m in ${'$'}MODULES; do
                    [ -f "${'$'}LIB_DIR/${'$'}{m}.sh" ] && . "${'$'}LIB_DIR/${'$'}{m}.sh"
                done
                # Offline local file resolver wrapper around z2k_fetch if WORK_DIR already has it
                if type z2k_fetch >/dev/null 2>&1; then
                    eval "orig_$(declare -f z2k_fetch 2>/dev/null || true)"
                fi
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

                # Prevent step_build_zapret2 from wiping our staged openwrt-embedded.tar.gz inside build_dir
                mkdir -p /opt/gor4akov-offline/cache
                [ -s "${'$'}WORK_STAGE/zapret2_build/openwrt-embedded.tar.gz" ] && mv "${'$'}WORK_STAGE/zapret2_build/openwrt-embedded.tar.gz" /opt/gor4akov-offline/cache/openwrt-embedded.tar.gz
                download_openwrt_embedded_release() {
                    if [ -s /opt/gor4akov-offline/cache/openwrt-embedded.tar.gz ]; then
                        cp -f /opt/gor4akov-offline/cache/openwrt-embedded.tar.gz "${'$'}2"
                        return 0
                    fi
                    curl -fsSL --connect-timeout 8 --max-time 180 "${'$'}1" -o "${'$'}2"
                }

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

            echo "=== [4/6] Создание изолированной сети и политики Gor4akovNET ==="
            CFG="${'$'}(LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null)"
            if ! echo "${'$'}CFG" | grep -q "ip policy Gor4akovNET"; then
                LD_LIBRARY_PATH= ndmc -c "ip policy Gor4akovNET" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "ip policy Gor4akovNET description Gor4akovNET" >/dev/null
            fi

            ISO="$isoFlag"
            BR_NAME="${'$'}(echo "${'$'}CFG" | awk '/^interface Bridge[0-9]+/{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g){print cur; exit} inb=0} END{if(inb && g) print cur}')"
            if [ -z "${'$'}BR_NAME" ]; then
                bridge_count=$(echo "${'$'}CFG" | grep -c "^interface Bridge")
                for n in $(seq 1 $((bridge_count + 1))); do
                    if ! echo "${'$'}CFG" | grep -q "^interface Bridge${'$'}n"; then
                        BR_NAME="Bridge${'$'}n"
                        break
                    fi
                done
            fi
            SUB_THIRD=77
            while echo "${'$'}CFG" | grep -q "192.168.${'$'}{SUB_THIRD}." && ! echo "${'$'}CFG" | grep -A8 "^interface ${'$'}BR_NAME" | grep -q "192.168.${'$'}{SUB_THIRD}."; do
                SUB_THIRD=${'$'}((SUB_THIRD + 1))
            done
            SUB_PREFIX="192.168.${'$'}{SUB_THIRD}"

            if [ -n "${'$'}BR_NAME" ]; then
                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME description Gor4akovNET" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME security-level private" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME ip address ${'$'}{SUB_PREFIX}.1 255.255.255.0" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME ip policy Gor4akovNET" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}BR_NAME up" >/dev/null

                LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET range ${'$'}{SUB_PREFIX}.10 ${'$'}{SUB_PREFIX}.200" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET bind ${'$'}BR_NAME" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "ip dhcp pool Gor4akovNET enable" >/dev/null

                # Bind AccessPoint1 (2.4GHz and 5GHz if exists) without touching Home AccessPoint0!
                for master in WifiMaster0 WifiMaster1; do
                    if echo "${'$'}CFG" | grep -q "^interface ${'$'}{master}$"; then
                        ap=$(echo "${'$'}CFG" | grep -B1 "description Gor4akovNET" | grep -oE "${'$'}{master}/AccessPoint[0-9]+" | head -1)
if [ -z "${'$'}ap" ]; then
for cand in $(echo "${'$'}CFG" | grep -oE "${'$'}{master}/AccessPoint[0-9]+" | sort -u); do
if ! echo "${'$'}CFG" | grep -q "^interface ${'$'}cand${'$'}"; then continue; fi
if echo "${'$'}CFG" | grep -q "include ${'$'}cand${'$'}"; then continue; fi
blk=$(echo "${'$'}CFG" | grep -A20 "^interface ${'$'}cand${'$'}" | sed '/^!/q')
if echo "${'$'}blk" | grep -q "ssid "; then continue; fi
ap="${'$'}cand"; break; done
fi
[ -z "${'$'}ap" ] && continue
                        LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap" >/dev/null
                        LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap description Gor4akovNET" >/dev/null
                        LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap ssid $safeSsid" >/dev/null
                        LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap encryption wpa2" >/dev/null
                        LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap authentication wpa-psk $safePass" >/dev/null
                        LD_LIBRARY_PATH= ndmc -c "interface ${'$'}{BR_NAME} include ${'$'}ap" >/dev/null
                        LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap up" >/dev/null
                    fi
                done
                LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null
                echo "[OK] Wi-Fi сеть '$safeSsid' поднята на ${'$'}BR_NAME (${'$'}{SUB_PREFIX}.0/24)"
            fi

            # Update zapret2 config for Gor4akovNET policy isolation
            ZCFG="/opt/zapret2/config"
            if [ -f "${'$'}ZCFG" ]; then
                sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d' "${'$'}ZCFG"
                if [ "${'$'}ISO" = "1" ]; then
                    printf 'POLICY_NAME="Gor4akovNET"\nPOLICY_EXCLUDE=0\n' >> "${'$'}ZCFG"
                fi
            fi

            # Netfilter hook so Bridge subnet 192.168.77.0/24 is ALWAYS included in bypass while Home Wi-Fi is isolated
            cat > /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh << EOFHOOK
#!/bin/sh
[ "${'$'}ISO" = "1" ] || exit 0
[ "${'$'}{type:-ip6tables}" = "iptables" ] || exit 0
MARK=\${'$'}(LD_LIBRARY_PATH= ndmc -c "show ip policy" 2>/dev/null | awk 'tolower(${'$'}0)~/description.*gor4akovnet/{f=1} f && tolower(${'$'}0)~/mark/{sub(/.*[:=][ \t]*/,""); sub(/\/.*/,""); sub(/^0x/,""); print "0x"${'$'}1"/0x0fffffff"; exit}')
WAN=\${'$'}(ip route show default 2>/dev/null | awk '{for(i=1;i<=NF;i++) if(${'$'}i=="dev"){print ${'$'}(i+1); exit}}')
[ -n "\${'$'}WAN" ] || exit 0
for w in \${'$'}WAN; do
    if [ -n "\${'$'}MARK" ]; then
        iptables -w -t mangle -C POSTROUTING -o "\${'$'}w" ! -s ${'$'}{SUB_PREFIX}.0/24 -m mark ! --mark "\${'$'}MARK" -j CONNMARK --set-xmark 0x20000000/0x20000000 2>/dev/null || \
        iptables -w -t mangle -I POSTROUTING -o "\${'$'}w" ! -s ${'$'}{SUB_PREFIX}.0/24 -m mark ! --mark "\${'$'}MARK" -j CONNMARK --set-xmark 0x20000000/0x20000000 2>/dev/null || true
    else
        iptables -w -t mangle -C POSTROUTING -o "\${'$'}w" ! -s ${'$'}{SUB_PREFIX}.0/24 -j CONNMARK --set-xmark 0x20000000/0x20000000 2>/dev/null || \
        iptables -w -t mangle -I POSTROUTING -o "\${'$'}w" ! -s ${'$'}{SUB_PREFIX}.0/24 -j CONNMARK --set-xmark 0x20000000/0x20000000 2>/dev/null || true
    fi
done
EOFHOOK
            chmod +x /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh

            echo "=== [5/6] Опции (AdGuard Home / WARP) ==="
            if [ "$aghFlag" = "1" ]; then
                echo "[INFO] Установка AdGuard Home (требуется выход в интернет)..."
                if opkg update && opkg install adguardhome-go; then
                    LD_LIBRARY_PATH= ndmc -c "opkg dns-override" >/dev/null
                    LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null
                    /opt/etc/init.d/S99AdGuardHome start >/dev/null 2>&1 || true
                    echo "[OK] AdGuard Home установлен (:3000) и opkg dns-override включён"
                else
                    echo "[WARN] Не удалось скачать adguardhome-go сейчас — можно доустановить в Управление -> AdGuardHome после подключения к сети"
                fi
            fi

            if [ "$warpFlag" = "1" ] && [ -x /opt/zapret2/z2k-warp.sh ]; then
                echo "[INFO] Активация Cloudflare WARP в z2k..."
                sh /opt/zapret2/z2k-warp.sh enable >/dev/null 2>&1 || true
            fi

            echo "=== [6/6] Запуск обхода и финальная проверка ==="
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart
            sh /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh >/dev/null 2>&1 || true
            sleep 2
            echo "NFQWS_PROCS=${'$'}(ps | grep -c '[n]fqws2')"
            echo "INSTALL_DONE"
        """.trimIndent()
    }

    val VERIFY_BYPASS_SCRIPT = """
        set +e
        nfq=${'$'}(ps | grep -c '[n]fqws2')
        rules=${'$'}(iptables -t mangle -S 2>/dev/null | grep -c NFQUEUE)
        echo "PROCESSES|nfqws2=${'$'}nfq|nfqueue_rules=${'$'}rules"
        for host in youtube.com discord.com instagram.com; do
            code=${'$'}(curl -sI --connect-timeout 4 --max-time 6 -o /dev/null -w "%{http_code}" "https://${'$'}host" 2>/dev/null || echo "000")
            echo "PROBE|${'$'}host|HTTP_${'$'}code"
        done
    """.trimIndent()

    fun rollbackGor4akovScript(removeZapret: Boolean): String = """__LONG__
        set +e
        CFG=${'$'}(LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null)
        BR_NAME=${'$'}(echo "${'$'}CFG" | awk '/^interface Bridge[0-9]+/{cur=${'$'}2; inb=1; g=0} inb && /description Gor4akovNET/{g=1} inb && /^!/{if(g){print cur; exit} inb=0} END{if(inb && g) print cur}')
        for master in WifiMaster0 WifiMaster1; do
            ap="${'$'}{master}/AccessPoint1"
            if echo "${'$'}CFG" | grep -A6 "^interface ${'$'}ap" | grep -q "description Gor4akovNET"; then
                LD_LIBRARY_PATH= ndmc -c "interface ${'$'}ap down" >/dev/null
                [ -n "${'$'}BR_NAME" ] && LD_LIBRARY_PATH= ndmc -c "no interface ${'$'}BR_NAME include ${'$'}ap" >/dev/null
                LD_LIBRARY_PATH= ndmc -c "no interface ${'$'}ap" >/dev/null
            fi
        done
        [ -n "${'$'}BR_NAME" ] && LD_LIBRARY_PATH= ndmc -c "no interface ${'$'}BR_NAME" >/dev/null
        LD_LIBRARY_PATH= ndmc -c "no ip dhcp pool Gor4akovNET" >/dev/null
        LD_LIBRARY_PATH= ndmc -c "no ip policy Gor4akovNET" >/dev/null
        LD_LIBRARY_PATH= ndmc -c "no opkg dns-override" >/dev/null 2>&1 || true
        LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null
        rm -f /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh
        if [ "${if (removeZapret) 1 else 0}" = "1" ]; then
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 stop >/dev/null 2>&1 || true
            rm -rf /opt/zapret2 /opt/etc/init.d/S99zapret2 /opt/etc/ndm/netfilter.d/000-zapret2.sh
        else
            [ -f /opt/zapret2/config ] && sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d' /opt/zapret2/config
            [ -x /opt/etc/init.d/S99zapret2 ] && /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
        fi
        echo "ROLLBACK_OK"
    """.trimIndent()

    val AUTO_DIAG_REPAIR_SCRIPT = """
        set +e
        echo "=== Автодиагностика Gor4akovNET ==="
        if [ ! -x /opt/bin/opkg ]; then
            echo "[FAIL] Entware (/opt/bin/opkg) не смонтирован"
            exit 1
        fi
        echo "[OK] Entware активен"
        if [ -x /opt/etc/init.d/S99zapret2 ]; then
            if [ "${'$'}(ps | grep -c '[n]fqws2')" -eq 0 ]; then
                echo "[FIX] nfqws2 был остановлен — запускаю S99zapret2..."
                /opt/etc/init.d/S99zapret2 restart
            else
                echo "[OK] nfqws2 работает"
            fi
        else
            echo "[WARN] zapret2 не установлен"
        fi
        [ -x /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh ] && sh /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh
        echo "[OK] Netfilter правила синхронизированы"
    """.trimIndent()
}
