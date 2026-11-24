p = "app/src/main/java/ru/garickbox/gor4akovnet/GboxScripts.kt"
s = open(p, encoding="utf-8").read()
K = "${'$'}"
old = '                        ap=""\nfor cand in'
if old not in s:
    print("OLD NOT FOUND"); raise SystemExit
new = (
    '                        ap=$(echo "' + K + 'CFG" | grep -B1 "description Gor4akovNET" | grep -oE "' + K + '{master}/AccessPoint[0-9]+" | head -1)\n'
    'if [ -z "' + K + 'ap" ]; then\n'
    'for cand in'
)
s = s.replace(old, new, 1)
# закрываем if перед [ -z "$ap" ] && continue
old2 = 'ap="' + K + 'cand"; break; done\n[ -z "'
if old2 not in s:
    print("OLD2 NOT FOUND"); raise SystemExit
new2 = 'ap="' + K + 'cand"; break; done\nfi\n[ -z "'
s = s.replace(old2, new2, 1)
open(p, "w", encoding="utf-8").write(s)
print("OK: AP idempotency")
