p = "app/src/main/java/ru/garickbox/gor4akovnet/ConnectWizardScreen.kt"
s = open(p, encoding="utf-8").read()
if "AssistChip(onClick = { sshPass = \"keenetic\"" in s:
    print("ALREADY PATCHED"); raise SystemExit
anchor = '''                            IconButton(onClick = { showSshPass = !showSshPass }) {
                                Icon(if (showSshPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
'''
if anchor not in s:
    print("ANCHOR NOT FOUND"); raise SystemExit
add = anchor + '''
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = { sshPass = "keenetic" }, label = { Text("keenetic", fontSize = 11.sp) })
                        AssistChip(onClick = { sshPass = "" }, label = { Text("Пустой", fontSize = 11.sp) })
                        AssistChip(onClick = { sshPass = rciPass }, label = { Text("Как RCI", fontSize = 11.sp) })
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    val port = sshPortStr.toIntOrNull() ?: 222
                                    val found = withContext(Dispatchers.IO) {
                                        runCatching { Gboxcore.tryPasswordsSSH(host, port, sshUser, rciPass) }.getOrNull()
                                    }
                                    if (found != null) sshPass = found
                                }
                            },
                            label = { Text("Автоп = 11.sp) }
                        )
                    }
'''
s = s.replace(anchor, add, 1)
open(p, "w", encoding="utf-8").write(s)
print("OK: SSH chips added")
