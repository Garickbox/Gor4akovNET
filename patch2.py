p = "app/src/main/java/ru/garickbox/gor4akovnet/RciClient.kt"
s = open(p, encoding="utf-8").read()
if "components/list" in s:
    print("ALREADY PATCHED"); raise SystemExit
anchor = 'val ssh222 = Gboxcore.probeTCP(host, 222, 1200)'
if anchor not in s:
    print("ANCHOR NOT FOUND"); raise SystemExit
add = anchor + '''
            if (comps.none { it.contains("opkg") } || comps.none { it.contains("netfilter") }) {
                val compListRaw = runCatching { session.request("/rci/components/list") }.getOrDefault("")
                if (compListRaw.contains("opkg", ignoreCase = true)) comps.add("opkg")
                if (compListRaw.contains("netfilter", ignoreCase = true)) comps.add("opkg-kmod-netfilter")
            }
            if (ssh222) {
                if (comps.none { it.contains("opkg") }) comps.add("opkg")
                if (comps.none { it.contains("netfilter") }) comps.add("opkg-kmod-netfilter")
                if (comps.none { it == "ssh" }) comps.add("ssh")
            }'''
s = s.replace(anchor, add, 1)
open(p, "w", encoding="utf-8").write(s)
print("OK: components fallback patched")
