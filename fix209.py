p = "app/src/main/java/ru/garickbox/gor4akovnet/RciClient.kt"
s = open(p, encoding="utf-8").read()
bad = 'verJson.optString("title", ""),("exact", "") ?: "")'
good = 'verJson.optString("title", ""), ndmObj?.optString("exact", "") ?: "")'
if bad not in s:
    print("BAD NOT FOUND — уже исправлено или другая строка")
else:
    s = s.replace(bad, good)
    open(p, "w", encoding="utf-8").write(s)
    print("OK: line 209 fixed")
