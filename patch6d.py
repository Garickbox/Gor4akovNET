p = "app/src/main/java/ru/garickbox/gor4akovnet/GboxScripts.kt"
s = open(p, encoding="utf-8").read()
old = "for n in 1 2 3 4 5; do"
if old not in s:
    print("OLD NOT FOUND"); raise SystemExit
new = "\n".join([
    "bridge_count=$(echo \"${'$'}CFG\" | grep -c \"^interface Bridge\")",
    "                for n in $(seq 1 $((bridge_count + 1))); do",
])
s = s.replace(old, new, 1)
open(p, "w", encoding="utf-8").write(s)
print("OK: Bridge dynamic")
