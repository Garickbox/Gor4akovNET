p = "app/src/main/java/ru/garickbox/gor4akovnet/ConnectWizardScreen.kt"
s = open(p, encoding="utf-8").read()
add = []
if "import androidx.compose.foundation.layout.Arrangement" not in s:
    add.append("import androidx.compose.foundation.layout.Arrangement")
if "import androidx.compose.material3.AssistChip" not in s and "import androidx.compose.material3.*" not in s:
    add.append("import androidx.compose.material3.AssistChip")
if not add:
    print("IMPORTS OK")
else:
    # вставка после первой строки package
    idx = s.index("\n", s.index("package "))
    s = s[:idx+1] + "\n".join(add) + "\n" + s[idx+1:]
    open(p, "w", encoding="utf-8").write(s)
    print("ADDED:", len(add))
