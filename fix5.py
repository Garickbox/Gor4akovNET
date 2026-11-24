p = "app/src/main/java/ru/garickbox/gor4akovnet/ConnectWizardScreen.kt"
s = open(p, encoding="utf-8").read()
bad = 'label = { Text("Автоп = 11.sp) }'
good = 'label = { Text("Автоподбор", fontSize = 11.sp) }'
if bad not in s:
    print("BAD NOT FOUND — скиньте sed-вывод")
else:
    s = s.replace(bad, good)
    open(p, "w", encoding="utf-8").write(s)
    print("OK: label fixed")
