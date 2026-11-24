package gboxcore

import (
	"bytes"
	"context"
	"fmt"
	"net"
	"strings"
	"time"

	"golang.org/x/crypto/ssh"
)

func dialRouterSSH(host string, port int32, user, pass string, timeout time.Duration) (*ssh.Client, error) {
	cfg := &ssh.ClientConfig{
		User: user,
		Auth: []ssh.AuthMethod{
			ssh.Password(pass),
			ssh.KeyboardInteractive(func(_, _ string, questions []string, _ []bool) ([]string, error) {
				ans := make([]string, len(questions))
				for i := range questions {
					ans[i] = pass
				}
				return ans, nil
			}),
		},
		HostKeyCallback: ssh.InsecureIgnoreHostKey(),
		Timeout:         timeout,
	}
	addr := net.JoinHostPort(host, fmt.Sprintf("%d", port))
	conn, err := net.DialTimeout("tcp", addr, timeout)
	if err != nil {
		return nil, fmt.Errorf("tcp dial %s: %w", addr, err)
	}
	c, chans, reqs, err := ssh.NewClientConn(conn, addr, cfg)
	if err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("ssh handshake %s: %w", addr, err)
	}
	return ssh.NewClient(c, chans, reqs), nil
}

// RunRouterSSH executes a command on the router via SSH without requiring a proxy.
func RunRouterSSH(host string, port int32, user, pass, cmd string) (string, error) {
	if port <= 0 {
		port = 222
	}
	timeout := 28 * time.Second
	if strings.Contains(cmd, "__LONG__") {
		cmd = strings.ReplaceAll(cmd, "__LONG__", "")
		timeout = 300 * time.Second
	}

	client, err := dialRouterSSH(host, port, user, pass, 10*time.Second)
	if err != nil {
		return "", err
	}
	defer client.Close()

	sess, err := client.NewSession()
	if err != nil {
		return "", fmt.Errorf("ssh session: %w", err)
	}
	defer sess.Close()

	var stdout, stderr bytes.Buffer
	sess.Stdout = &stdout
	sess.Stderr = &stderr

	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	done := make(chan error, 1)
	go func() {
		done <- sess.Run(cmd)
	}()

	select {
	case <-ctx.Done():
		_ = sess.Signal(ssh.SIGTERM)
		return stdout.String() + "\n" + stderr.String(), fmt.Errorf("ssh timeout after %s", timeout)
	case runErr := <-done:
		out := strings.TrimSpace(stdout.String())
		errOut := strings.TrimSpace(stderr.String())
		if runErr != nil {
			combined := out
			if errOut != "" {
				if combined != "" {
					combined += "\n"
				}
				combined += errOut
			}
			if combined == "" {
				combined = runErr.Error()
			}
			return combined, fmt.Errorf("ssh exit: %w\n%s", runErr, combined)
		}
		if out == "" && errOut != "" {
			return errOut, nil
		}
		return out, nil
	}
}

// PushBytesSSH streams binary payloads directly to a remote file over BusyBox SSH stdin.
func PushBytesSSH(host string, port int32, user, pass, remotePath string, data []byte) error {
	if port <= 0 {
		port = 222
	}
	client, err := dialRouterSSH(host, port, user, pass, 12*time.Second)
	if err != nil {
		return err
	}
	defer client.Close()

	sess, err := client.NewSession()
	if err != nil {
		return fmt.Errorf("ssh session: %w", err)
	}
	defer sess.Close()

	sess.Stdin = bytes.NewReader(data)
	var stderr bytes.Buffer
	sess.Stderr = &stderr

	dir := ""
	if idx := strings.LastIndex(remotePath, "/"); idx > 0 {
		dir = remotePath[:idx]
	}
	safePath := "'" + strings.ReplaceAll(remotePath, "'", "'\"'\"'") + "'"
	cmd := "cat > " + safePath
	if dir != "" {
		safeDir := "'" + strings.ReplaceAll(dir, "'", "'\"'\"'") + "'"
		cmd = "mkdir -p " + safeDir + " && cat > " + safePath
	}

	if err := sess.Run(cmd); err != nil {
		return fmt.Errorf("push %s: %w (%s)", remotePath, err, strings.TrimSpace(stderr.String()))
	}
	return nil
}

// ProbeTCP checks if a local/router TCP port responds within timeoutMs milliseconds.
func ProbeTCP(host string, port int32, timeoutMs int32) bool {
	if timeoutMs <= 0 {
		timeoutMs = 1200
	}
	addr := net.JoinHostPort(host, fmt.Sprintf("%d", port))
	conn, err := net.DialTimeout("tcp", addr, time.Duration(timeoutMs)*time.Millisecond)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}
