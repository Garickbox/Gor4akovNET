import sys
p = "app/src/main/java/ru/garickbox/gor4akovnet/RciClient.kt"
s = open(p, encoding="utf-8").read()
o = s
s = s.replace(
    'ndmObj?.optString("exact", ndmObj.optString("title", title)) ?: title',
    'ndmObj?.optString("title", "") ?: ""'
)
old = 'val verMatch = Regex("""(\\d+)\\.(\\d+)""").find(ndmTitle)'
new = (
    'val verMatch = listOf(verJson.optString("release", ""), verJson.optString("title", ""),("exact", "") ?: "")'
    '\n                .asSequence().mapNotNull { Regex("""(\\d+)\\.(\\d+)""").find(it) }.firstOrNull()'
)
s = s.replace(old, new)
if s == o:
    print("NO CHANGE (already patched or text mismatch)")
    sys.exit(1)
open(p, "w", encoding="utf-8").write(s)
print("OK: firmware parser patched")
