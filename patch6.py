p = "app/src/main/java/ru/garickbox/gor4akovnet/GboxScripts.kt"
s = open(p, encoding="utf-8").read()
K = "${'$'}"
old = 'ap="' + K + '{master}/AccessPoint1"'
if old not in s:
    print("OLD NOT FOUND"); raise SystemExit
new = (
    'ap=""\n'
    'for n in 3 4 5 6 2 1; do cand="' + K + '{master}/AccessPoint' + K + 'n"\n'
    'if ! echo "' + K + 'CFG" | grep -q "^interface ' + K + 'cand' + K + '"; then continue; fi\n'
    'if echo "' + K + 'CFG" | grep -q "include ' + K + 'cand' + K + '"; then continue; fi\n'
    'blk=$(echo "' + K + 'CFG" | grep -A20 "^interface ' + K + 'cand' + K + '" | sed \'/^!/q\')\n'
    'if echo "' + K + 'blk" | grep -q "ssid "; then continue; fi\n'
    'ap="' + K + 'cand"; break; done\n'
    '[ -z "' + K + 'ap" ] && continue'
)
s = s.replace(old, new, 1)
open(p, "w", encoding="utf-8").write(s)
print("OK: AP selection patched")
