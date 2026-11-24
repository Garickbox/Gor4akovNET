package ru.garickbox.gor4akovnet

object GboxMordaScripts {

    val Z2K_PRO_STATUS = """
        set +e
        cfg="/opt/zapret2/config"
        ver=${'$'}(head -1 /opt/zapret2/.z2k-installed-tag 2>/dev/null || echo "неизвестно")
        nfq=${'$'}(ps | grep -c '[n]fqws2')
        echo "META|ver=${'$'}ver|nfq=${'$'}nfq"
        if [ -f "${'$'}cfg" ]; then
            grep -E '^(POLICY_NAME|POLICY_EXCLUDE|DISABLE_IPV6|GAME_WARP_ENABLED|Z2K_AUTOCIRCULAR|Z2K_INSTA_DNS|Z2K_QUIC_SILENCE|Z2K_CATEGORY_DISCORD_VOICE|FWTYPE|MODE)=' "${'$'}cfg" | sed 's/^/CFG|/'
        fi
        echo "LIST_CNT|whitelist=${'$'}(grep -cvE '^[[:space:]]*(#|${'$'})' /opt/zapret2/lists/whitelist.txt 2>/dev/null || echo 0)"
        echo "LIST_CNT|extra=${'$'}(grep -cvE '^[[:space:]]*(#|${'$'})' /opt/zapret2/lists/extra-domains.txt 2>/dev/null || echo 0)"
        echo "LIST_CNT|exclude=${'$'}(grep -cvE '^[[:space:]]*(#|${'$'})' /opt/zapret2/ipset/zapret-hosts-user-exclude.txt 2>/dev/null || echo 0)"
    """.trimIndent()

    fun z2kSaveFlagsAndRestart(
        policyIsolated: Boolean,
        disableIpv6: Boolean,
        gameWarp: Boolean,
        discordVoice: Boolean,
        quicSilence: Boolean
    ): String = """
        set +e
        cfg="/opt/zapret2/config"
        [ -f "${'$'}cfg" ] || { echo "NO_CONFIG"; exit 1; }
        sed -i '/^POLICY_NAME=/d;/^POLICY_EXCLUDE=/d;/^DISABLE_IPV6=/d;/^GAME_WARP_ENABLED=/d;/^Z2K_CATEGORY_DISCORD_VOICE=/d;/^Z2K_QUIC_SILENCE=/d' "${'$'}cfg"
        ${if (policyIsolated) "printf 'POLICY_NAME=\"Gor4akovNET\"\\nPOLICY_EXCLUDE=0\\n' >> \"${'$'}cfg\"" else ""}
        printf 'DISABLE_IPV6=%s\nGAME_WARP_ENABLED=%s\nZ2K_CATEGORY_DISCORD_VOICE=%s\nZ2K_QUIC_SILENCE=%s\n' \
            "${if (disableIpv6) 1 else 0}" "${if (gameWarp) 1 else 0}" "${if (discordVoice) 1 else 0}" "${if (quicSilence) 1 else 0}" >> "${'$'}cfg"
        /opt/etc/init.d/S99zapret2 restart
        [ -x /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh ] && sh /opt/etc/ndm/netfilter.d/090-gor4akov-policy.sh >/dev/null 2>&1 || true
        echo "SAVED_OK"
    """.trimIndent()

    val Z2K_ONLINE_UPDATE = """__LONG__
        set +e
        export TMPDIR=/opt/tmp
        mkdir -p /opt/tmp
        sh -c 'curl -fsSL --connect-timeout 8 https://raw.githubusercontent.com/necronicle/z2k/z2k-enhanced/z2k.sh -o /opt/tmp/z2k-online.sh && sh /opt/tmp/z2k-online.sh install'
    """.trimIndent()

    fun readZ2kList(which: String): String {
        val path = when (which) {
            "whitelist" -> "/opt/zapret2/lists/whitelist.txt"
            "exclude" -> "/opt/zapret2/ipset/zapret-hosts-user-exclude.txt"
            else -> "/opt/zapret2/lists/extra-domains.txt"
        }
        return "cat '$path' 2>/dev/null || true"
    }

    fun writeZ2kList(which: String, b64Content: String): String {
        val path = when (which) {
            "whitelist" -> "/opt/zapret2/lists/whitelist.txt"
            "exclude" -> "/opt/zapret2/ipset/zapret-hosts-user-exclude.txt"
            else -> "/opt/zapret2/lists/extra-domains.txt"
        }
        return """
            mkdir -p "${'$'}(dirname '$path')"
            printf '%s' '$b64Content' | base64 -d > '$path'
            /opt/etc/init.d/S99zapret2 restart >/dev/null 2>&1 || true
            echo "LIST_SAVED"
        """.trimIndent()
    }

    val AGH_STATUS_AND_LOG = """
        set +e
        inst=0; [ -x /opt/sbin/AdGuardHome ] || [ -x /opt/bin/AdGuardHome ] && inst=1
        run=0; ps | grep -q '[A]dGuardHome' && run=1
        ov=0; LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null | grep -q "opkg dns-override" && ov=1
        yaml="/opt/etc/AdGuardHome/AdGuardHome.yaml"
        port=3000
        [ -f "${'$'}yaml" ] && port=${'$'}(awk '/bind_port:|address:.*:/{if(match(${'$'}0,/:([0-9]+)/,a)){print a[1]; exit}}' "${'$'}yaml" 2>/dev/null || echo 3000)
        echo "AGH_META|inst=${'$'}inst|run=${'$'}run|ov=${'$'}ov|port=${'$'}port"
        qlog=""
        for p in /opt/var/log/AdGuardHome/querylog.json /opt/etc/AdGuardHome/data/querylog.json /opt/var/AdGuardHome/data/querylog.json; do
            [ -f "${'$'}p" ] && { qlog="${'$'}p"; break; }
        done
        if [ -n "${'$'}qlog" ]; then
            tail -n 35 "${'$'}qlog" | awk '
                function ext(k, s,   r) {
                    if (match(s, "\""k"\":\"([^\"]*)\"", a)) return a[1]
                    return "-"
                }
                {
                    t=ext("T", ${'$'}0); c=ext("CP", ${'$'}0); if (c=="-") c=ext("IP", ${'$'}0)
                    h=ext("QH", ${'$'}0); qt=ext("QT", ${'$'}0)
                    blk=(${'$'}0 ~ /"IsFiltered":true/) ? 1 : 0
                    rs=ext("Reason", ${'$'}0)
                    if (h != "-") printf "QLOG|%s|%s|%s|%s|%d|%s\n", substr(t,12,8), c, h, qt, blk, rs
                }
            '
        fi
    """.trimIndent()

    val AGH_INSTALL_OR_TOGGLE = """__LONG__
        set +e
        if [ ! -x /opt/sbin/AdGuardHome ] && [ ! -x /opt/bin/AdGuardHome ]; then
            opkg update && opkg install adguardhome-go || { echo "AGH_INSTALL_FAIL"; exit 1; }
        fi
        LD_LIBRARY_PATH= ndmc -c "opkg dns-override" >/dev/null
        LD_LIBRARY_PATH= ndmc -c "system configuration save" >/dev/null
        [ -x /opt/etc/init.d/S99AdGuardHome ] && /opt/etc/init.d/S99AdGuardHome restart
        echo "AGH_READY"
    """.trimIndent()

    fun aghSetUpstreams(dnsMode: String): String {
        val upstreams = when (dnsMode) {
            "quad9" -> "https://dns.quad9.net/dns-query"
            "adguard" -> "https://dns.adguard-dns.com/dns-query"
            "google" -> "https://dns.google/dns-query"
            else -> "https://cloudflare-dns.com/dns-query"
        }
        return """
            set +e
            for url in "$upstreams"; do
                echo "UPSTREAM -> ${'$'}url"
            done
            yaml="/opt/etc/AdGuardHome/AdGuardHome.yaml"
            if [ -f "${'$'}yaml" ]; then
                sed -i '/upstream_dns:/,/^  [a-z_]*:/{/^    -/d}' "${'$'}yaml" 2>/dev/null || true
                sed -i "s|upstream_dns:|upstream_dns:\n    - $upstreams|" "${'$'}yaml"
                [ -x /opt/etc/init.d/S99AdGuardHome ] && /opt/etc/init.d/S99AdGuardHome restart >/dev/null 2>&1 || true
                echo "UPSTREAM_SAVED"
            else
                echo "NO_YAML"
            fi
        """.trimIndent()
    }

    val NDMS_PORTS_AND_INITD = """
        set +e
        LD_LIBRARY_PATH= ndmc -c "show running-config" 2>/dev/null | awk '
            /^ip static / {
                printf "PF|%d|%s\n", ++c, ${'$'}0
            }
        '
        for s in /opt/etc/init.d/S*; do
            [ -f "${'$'}s" ] || continue
            b=${'$'}(basename "${'$'}s")
            en=0; [ -x "${'$'}s" ] && en=1
            run=0
            case "${'$'}b" in
                *zapret2*) ps | grep -q '[n]fqws2' && run=1 ;;
                *AdGuardHome*) ps | grep -q '[A]dGuardHome' && run=1 ;;
                *dropbear*) ps | grep -q '[d]ropbear' && run=1 ;;
                *tg-tunnel*) ps | grep -q '[t]g-mtproxy-client' && run=1 ;;
            esac
            echo "INITD|${'$'}b|${'$'}en|${'$'}run"
        done
    """.trimIndent()

    fun initdAction(name: String, action: String): String {
        val clean = name.replace(Regex("[^a-zA-Z0-9._-]"), "")
        return when (action) {
            "enable" -> "chmod +x '/opt/etc/init.d/$clean' && '/opt/etc/init.d/$clean' start; echo OK"
            "disable" -> "'/opt/etc/init.d/$clean' stop 2>/dev/null; chmod -x '/opt/etc/init.d/$clean'; echo OK"
            "start", "stop", "restart" -> "'/opt/etc/init.d/$clean' $action; echo OK"
            else -> "echo UNKNOWN"
        }
    }

    val SYSLOG_TAIL = """
        set +e
        LD_LIBRARY_PATH= ndmc -c "show log" 2>/dev/null | tail -n 60
    """.trimIndent()
}
