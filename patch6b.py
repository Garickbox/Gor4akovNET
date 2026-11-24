p = "app/src/main/java/ru/garickbox/gor4akovnet/GboxScripts.kt"
s = open(p, encoding="utf-8").read()
K = "${'$'}"
bad = 'for n in 3 4 5 6 2 1; do cand="' + K + '{master}/AccessPoint' + K + 'n"'
if bad not in s:
    print("BAD NOT FOUND"); raise SystemExit
good = ('for cand in $(echo "' + K + 'CFG" | grep -oE "' + K + '{master}/AccessPoint[0-9]+" | sort -u); do')
s = s.replace(bad, good, 1)
open(p, "w", encoding="utf-8").write(s)
print("OK: dynamic AP loop")
