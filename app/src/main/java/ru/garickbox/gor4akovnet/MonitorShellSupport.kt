package ru.garickbox.gor4akovnet

import java.net.URLEncoder

internal object MonitorShellSupport {

    const val PATH_HDR = """export PATH=/opt/sbin:/opt/bin:/sbin:/usr/sbin:/bin:/usr/bin
ZB=""
LPC=/opt/zapret2/webpanel/lighttpd.conf
if [ -f "${'$'}LPC" ]; then
  PB=${'$'}(sed -n 's/^[[:space:]]*server\.bind[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "${'$'}LPC" 2>/dev/null | head -n1)
  [ -n "${'$'}PB" ] && [ "${'$'}PB" != "0.0.0.0" ] && [ "${'$'}PB" != "::" ] && ZB="http://${'$'}PB:8088"
fi
[ -n "${'$'}ZB" ] || ZB="http://127.0.0.1:8088"
PH=${'$'}{ZB#http://}; PH=${'$'}{PH%%:*}
ZH='X-Z2K-Panel: 1'"""

    const val AWK_ESC =
        "function esc(s, i,c,o,b,n){gsub(/[\\r\\t]+/,\" \",s);gsub(/\\n/,\" \",s);o=\"\";b=sprintf(\"%c\",92);n=length(s);for(i=1;i<=n;i++){c=substr(s,i,1);if(c==b)o=o b b;else if(c==\"\\\"\")o=o b \"\\\"\";else if(c<\" \")o=o \" \";else o=o c}return o}"

    const val AGH_INIT_DETECT =
        "AGH_INI=\"\"; for f in /opt/etc/init.d/S99AdGuardHome /opt/etc/init.d/S99adguardhome; do [ -x \"§f\" ] && { AGH_INI=\"§f\"; break; }; done"

    fun sh(body: String): String = "$PATH_HDR\n" + body.trimIndent().replace('§', '$')
    fun sq(s: String): String = s.replace("'", "'\"'\"'")
    fun ue(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun ndmcSaveCmd(vararg cmds: String): String = sh(
        cmds.joinToString("\n") { "LD_LIBRARY_PATH= /bin/ndmc -c '${sq(it)}'" } +
            "\nLD_LIBRARY_PATH= /bin/ndmc -c 'system configuration save'\necho OK"
    )
}
