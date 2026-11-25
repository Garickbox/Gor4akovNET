package gboxcore

import (
	"bufio"
	"bytes"
	"compress/gzip"
	"context"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/ulikunitz/xz"
	"golang.org/x/crypto/ssh"
)

func dialRouterSSH(host string, port int32, user, pass string, timeout time.Duration) (*ssh.Client, error) {
	cfg := &ssh.ClientConfig{
		User: strings.TrimSpace(user),
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
	addr := net.JoinHostPort(strings.TrimSpace(host), fmt.Sprintf("%d", port))
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

func parseCommandTimeout(cmd string, def time.Duration) (string, time.Duration) {
	if strings.Contains(cmd, "__LONG__") {
		return strings.ReplaceAll(cmd, "__LONG__", ""), 300 * time.Second
	}
	if strings.Contains(cmd, "__BACKUP__") {
		return strings.ReplaceAll(cmd, "__BACKUP__", ""), 600 * time.Second
	}
	return cmd, def
}

func shellQuote(s string) string {
	return "'" + strings.ReplaceAll(s, "'", "'\"'\"'") + "'"
}

// RunRouterSSH executes a command on the router via SSH without requiring a proxy.
func RunRouterSSH(host string, port int32, user, pass, cmd string) (string, error) {
	if port <= 0 {
		port = 222
	}
	cleanCmd, timeout := parseCommandTimeout(cmd, 28*time.Second)
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
		done <- sess.Run(cleanCmd)
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
	timeout := 240 * time.Second
	if len(data) > 15*1024*1024 {
		timeout = 600 * time.Second
	}
	return pushReaderSSH(host, port, user, pass, remotePath, bytes.NewReader(data), timeout, false, false)
}

// PushXzAsGzSSH decompresses an .xz archive in a stream, gzips it (BestSpeed), and writes remotePath.
func PushXzAsGzSSH(host string, port int32, user, pass, remotePath string, xzData []byte) error {
	xr, err := xz.NewReader(bytes.NewReader(xzData))
	if err != nil {
		return fmt.Errorf("xz init %s: %w", remotePath, err)
	}
	pr, pw := io.Pipe()
	go func() {
		gw, gerr := gzip.NewWriterLevel(pw, gzip.BestSpeed)
		if gerr != nil {
			_ = pw.CloseWithError(gerr)
			return
		}
		if _, cerr := io.Copy(gw, xr); cerr != nil {
			_ = gw.Close()
			_ = pw.CloseWithError(cerr)
			return
		}
		if cerr := gw.Close(); cerr != nil {
			_ = pw.CloseWithError(cerr)
			return
		}
		_ = pw.Close()
	}()
	return pushReaderSSH(host, port, user, pass, remotePath, pr, 360*time.Second, false, false)
}

// PushXzBinarySSH decompresses an .xz binary on the phone, compresses fast with gzip over Wi-Fi,
// and writes the final executable binary atomically onto the router via gunzip.
func PushXzBinarySSH(host string, port int32, user, pass, remotePath string, xzData []byte) error {
	xr, err := xz.NewReader(bytes.NewReader(xzData))
	if err != nil {
		return fmt.Errorf("xz init %s: %w", remotePath, err)
	}
	pr, pw := io.Pipe()
	go func() {
		gw, gerr := gzip.NewWriterLevel(pw, gzip.BestSpeed)
		if gerr != nil {
			_ = pw.CloseWithError(gerr)
			return
		}
		if _, cerr := io.Copy(gw, xr); cerr != nil {
			_ = gw.Close()
			_ = pw.CloseWithError(cerr)
			return
		}
		if cerr := gw.Close(); cerr != nil {
			_ = pw.CloseWithError(cerr)
			return
		}
		_ = pw.Close()
	}()
	return pushReaderSSH(host, port, user, pass, remotePath, pr, 420*time.Second, true, true)
}

// DecompressXzToGz converts an in-memory .tar.xz payload (e.g. Entware installer) into .tar.gz for Keenetic WebDAV.
func DecompressXzToGz(xzData []byte) ([]byte, error) {
	xr, err := xz.NewReader(bytes.NewReader(xzData))
	if err != nil {
		return nil, fmt.Errorf("xz reader: %w", err)
	}
	var out bytes.Buffer
	gw, err := gzip.NewWriterLevel(&out, gzip.DefaultCompression)
	if err != nil {
		return nil, err
	}
	if _, err := io.Copy(gw, xr); err != nil {
		_ = gw.Close()
		return nil, fmt.Errorf("xz->gz copy: %w", err)
	}
	if err := gw.Close(); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}

func pushReaderSSH(host string, port int32, user, pass, remotePath string, src io.Reader, timeout time.Duration, gunzipRemote bool, makeExec bool) error {
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
	sess.Stdin = src
	var stderr bytes.Buffer
	sess.Stderr = &stderr

	dir := ""
	if idx := strings.LastIndex(remotePath, "/"); idx > 0 {
		dir = remotePath[:idx]
	}
	safePath := shellQuote(remotePath)
	safeTmp := shellQuote(remotePath + ".tmp")

	var transfer string
	if gunzipRemote {
		transfer = "gzip -dc > " + safeTmp + " && mv -f " + safeTmp + " " + safePath
	} else {
		transfer = "cat > " + safeTmp + " && mv -f " + safeTmp + " " + safePath
	}
	if makeExec {
		transfer += " && chmod 755 " + safePath
	}

	cmd := transfer
	if dir != "" {
		cmd = "mkdir -p " + shellQuote(dir) + " && " + transfer
	}

	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	done := make(chan error, 1)
	go func() {
		done <- sess.Run(cmd)
	}()
	select {
	case <-ctx.Done():
		_ = sess.Signal(ssh.SIGTERM)
		return fmt.Errorf("push %s: timeout after %s", remotePath, timeout)
	case runErr := <-done:
		if runErr != nil {
			return fmt.Errorf("push %s: %w (%s)", remotePath, runErr, strings.TrimSpace(stderr.String()))
		}
		return nil
	}
}

// PullBytesSSH runs a remote command (e.g. tar -czf -) and returns raw stdout bytes.
func PullBytesSSH(host string, port int32, user, pass, cmd string) ([]byte, error) {
	if port <= 0 {
		port = 222
	}
	cleanCmd, timeout := parseCommandTimeout(cmd, 180*time.Second)
	client, err := dialRouterSSH(host, port, user, pass, 12*time.Second)
	if err != nil {
		return nil, err
	}
	defer client.Close()
	sess, err := client.NewSession()
	if err != nil {
		return nil, fmt.Errorf("ssh session: %w", err)
	}
	defer sess.Close()

	var stdout, stderr bytes.Buffer
	sess.Stdout = &stdout
	sess.Stderr = &stderr
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	done := make(chan error, 1)
	go func() {
		done <- sess.Run(cleanCmd)
	}()
	select {
	case <-ctx.Done():
		_ = sess.Signal(ssh.SIGTERM)
		return nil, fmt.Errorf("ssh pull timeout after %s", timeout)
	case err := <-done:
		if err != nil {
			return nil, fmt.Errorf("pull exit: %w (%s)", err, strings.TrimSpace(stderr.String()))
		}
		return stdout.Bytes(), nil
	}
}

// PullFileSSH streams stdout of a remote backup command directly to localPath on disk (zero phone RAM OOM).
func PullFileSSH(host string, port int32, user, pass, cmd, localPath string) (int64, error) {
	if port <= 0 {
		port = 222
	}
	cleanCmd, timeout := parseCommandTimeout(cmd, 420*time.Second)
	if err := os.MkdirAll(filepath.Dir(localPath), 0o755); err != nil {
		return 0, fmt.Errorf("mkdir local: %w", err)
	}
	tmpPath := localPath + ".part"
	f, err := os.Create(tmpPath)
	if err != nil {
		return 0, fmt.Errorf("create local %s: %w", tmpPath, err)
	}
	cleanupTmp := true
	defer func() {
		if f != nil {
			_ = f.Close()
		}
		if cleanupTmp {
			_ = os.Remove(tmpPath)
		}
	}()

	client, err := dialRouterSSH(host, port, user, pass, 12*time.Second)
	if err != nil {
		return 0, err
	}
	defer client.Close()
	sess, err := client.NewSession()
	if err != nil {
		return 0, fmt.Errorf("ssh session: %w", err)
	}
	defer sess.Close()

	bw := bufio.NewWriterSize(f, 64*1024)
	sess.Stdout = bw
	var stderr bytes.Buffer
	sess.Stderr = &stderr

	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	done := make(chan error, 1)
	go func() {
		done <- sess.Run(cleanCmd)
	}()

	select {
	case <-ctx.Done():
		_ = sess.Signal(ssh.SIGTERM)
		return 0, fmt.Errorf("ssh pull stream timeout after %s", timeout)
	case runErr := <-done:
		if ferr := bw.Flush(); ferr != nil {
			return 0, fmt.Errorf("flush local: %w", ferr)
		}
		if runErr != nil {
			return 0, fmt.Errorf("pull exit: %w (%s)", runErr, strings.TrimSpace(stderr.String()))
		}
		if cerr := f.Close(); cerr != nil {
			f = nil
			return 0, fmt.Errorf("close local: %w", cerr)
		}
		f = nil
		st, serr := os.Stat(tmpPath)
		if serr != nil {
			return 0, serr
		}
		if rerr := os.Rename(tmpPath, localPath); rerr != nil {
			return 0, fmt.Errorf("rename local: %w", rerr)
		}
		cleanupTmp = false
		return st.Size(), nil
	}
}

// ProbeTCP checks if a local/router TCP port responds within timeoutMs milliseconds.
func ProbeTCP(host string, port int32, timeoutMs int32) bool {
	if timeoutMs <= 0 {
		timeoutMs = 1200
	}
	addr := net.JoinHostPort(strings.TrimSpace(host), fmt.Sprintf("%d", port))
	conn, err := net.DialTimeout("tcp", addr, time.Duration(timeoutMs)*time.Millisecond)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}

// TryPasswordsSSH tries common default passwords (empty, "keenetic", "admin")
// followed by extraPass, returns the first that authenticates and can run a command.
func TryPasswordsSSH(host string, port int32, user, extraPass string) (string, error) {
	if port <= 0 {
		port = 222
	}
	candidates := []string{"", "keenetic", "admin"}
	if extraPass != "" {
		candidates = append(candidates, extraPass)
	}
	var lastErr error
	for _, p := range candidates {
		client, err := dialRouterSSH(host, port, user, p, 8*time.Second)
		if err != nil {
			lastErr = err
			continue
		}
		sess, err := client.NewSession()
		if err == nil {
			err = sess.Run("echo OK")
			sess.Close()
		}
		client.Close()
		if err == nil {
			return p, nil
		}
		lastErr = err
	}
	if lastErr == nil {
		lastErr = fmt.Errorf("no password worked")
	}
	return "", lastErr
}
