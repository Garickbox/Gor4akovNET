p = "core/ssh.go"
s = open(p, encoding="utf-8").read()
if "TryPasswordsSSH" in s:
    print("ALREADY PATCHED"); raise SystemExit
add = '''
// TryPasswordsSSH tries common default passwords (empty, "keenetic", "admin")
// followed by extraPass, returns the first that authenticates and can run a command.
func TryPasswordsSSH(host string, port int32, user, extraPass string) (string, error) {
\tif port <= 0 {
\t\tport = 222
\t}
\tcandidates := []string{"", "keenetic", "admin"}
\tif extraPass != "" {
\t\tcandidates = append(candidates, extraPass)
\t}
\tvar lastErr error
\tfor _, p := range candidates {
\t\tclient, err := dialRouterSSH(host, port, user, p, 8*time.Second)
\t\tif err != nil {
\t\t\tlastErr = err
\t\t\tcontinue
\t\t}
\t\tsess, err := client.NewSession()
\t\tif err == nil {
\t\t\terr = sess.Run("echo OK")
\t\t\tsess.Close()
\t\t}
\t\tclient.Close()
\t\tif err == nil {
\t\t\treturn p, nil
\t\t}
\t\tlastErr = err
\t}
\tif lastErr == nil {
\t\tlastErr = fmt.Errorf("no password worked")
\t}
\treturn "", lastErr
}
'''
s = s.rstrip() + "\n" + add
open(p, "w", encoding="utf-8").write(s)
print("OK: TryPasswordsSSH added")
