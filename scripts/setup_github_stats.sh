#!/usr/bin/env bash
set -euo pipefail

# Автонастройка GitHub-статистики (и опционально подписи APK) для Gor4akovNET.
# Запускается локально на ПК или в Termux.

REPO="${1:-Garickbox/Gor4akovNET}"

echo "=================================================="
echo "  Gor4akovNET — Настройка GitHub Статистики & CI  "
echo "  Репозиторий: $REPO"
echo "=================================================="
echo ""

need_cmd() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "[ERR] Не найдена команда: $1"
        exit 1
    }
}

need_cmd curl
need_cmd python3

USE_GH=0
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    USE_GH=1
    echo "[OK] Обнаружен авторизованный GitHub CLI (gh)."
fi

echo ""
echo "Выберите способ сбора статистики:"
echo "  1) Секретный GitHub Gist (бесплатно, 100% на GitHub; токен с правом ONLY 'gist' зашивается в BuildConfig)"
echo "  2) Внешний HTTPS Endpoint / Cloudflare Worker (GBOX_STATS_URL — GitHub токен вообще НЕ попадает в APK)"
read -r -p "Ваш выбор [1/2, по умолчанию 1]: " MODE
MODE="${MODE:-1}"

set_gh_secret() {
    local name="$1"
    local val="$2"
    if [ "$USE_GH" = "1" ]; then
        printf '%s' "$val" | gh secret set "$name" -R "$REPO"
        echo "[OK] Secret $name записан в $REPO через gh."
        return 0
    fi
    if [ -z "${ADMIN_PAT:-}" ]; then
        echo "[WARN] Установите gh CLI или задайте ADMIN_PAT с правом repo/secrets для автозаписи $name."
        echo "       Вручную добавьте в GitHub -> Settings -> Secrets and variables -> Actions:"
        echo "       $name = $val"
        return 1
    fi
    # Шифрование секрета без gh через python3 + PyNaCl (если установлен)
    python3 - "$REPO" "$name" "$val" "$ADMIN_PAT" <<'PY'
import sys, json, base64, urllib.request
try:
    from nacl import encoding, public
except ImportError:
    print("[INFO] PyNaCl не установлен — выведите или добавьте секрет вручную / через `gh secret set`.", file=sys.stderr)
    sys.exit(2)

repo, sname, sval, pat = sys.argv[1:]
req = urllib.request.Request(
    f"https://api.github.com/repos/{repo}/actions/secrets/public-key",
    headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json", "User-Agent": "Gor4akovNET-Setup"}
)
with urllib.request.urlopen(req, timeout=15) as r:
    pk = json.loads(r.read().decode())

pub_key = public.PublicKey(pk["key"].encode("utf-8"), encoding.Base64Encoder())
sealed = public.SealedBox(pub_key).encrypt(sval.encode("utf-8"))
payload = json.dumps({"encrypted_value": base64.b64encode(sealed).decode("utf-8"), "key_id": pk["key_id"]}).encode("utf-8")

put = urllib.request.Request(
    f"https://api.github.com/repos/{repo}/actions/secrets/{sname}",
    data=payload,
    method="PUT",
    headers={
        "Authorization": f"Bearer {pat}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
        "User-Agent": "Gor4akovNET-Setup"
    }
)
with urllib.request.urlopen(put, timeout=15) as r:
    if r.status not in (201, 204):
        raise RuntimeError(f"HTTP {r.status}")
print(f"[OK] Secret {sname} записан в {repo}.")
PY
}

if [ "$MODE" = "2" ]; then
    echo ""
    echo "--- Шаблон Cloudflare Worker (если ещё не развёрнут) ---"
    cat <<'WORKER'
// Сохраните в Cloudflare Workers и привяжите KV namespace `STATS_KV`:
export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === "POST") {
      const data = await req.json();
      if (!data.id || !/^[a-zA-Z0-9_-]{4,32}$/.test(data.id)) {
        return new Response("bad id", { status: 400 });
      }
      await env.STATS_KV.put("dev_" + data.id, JSON.stringify(data));
      return Response.json({ ok: true });
    }
    if (req.method === "GET") {
      const list = await env.STATS_KV.list({ prefix: "dev_" });
      const items = [];
      for (const k of list.keys) {
        const v = await env.STATS_KV.get(k.name);
        if (v) items.push(JSON.parse(v));
      }
      return Response.json(items);
    }
    return new Response("Method Not Allowed", { status: 405 });
  }
};
WORKER
    echo ""
    read -r -p "Вставьте URL Worker'а (например https://gbox-stats.example.workers.dev): " STATS_URL
    if [ -n "$STATS_URL" ]; then
        if [ "$USE_GH" = "0" ]; then
            read -r -s -p "GitHub PAT с правом repo (для записи Secret, пусто = показать инструкцию): " ADMIN_PAT
            echo ""
        fi
        set_gh_secret "GBOX_STATS_URL" "$STATS_URL" || true
    fi
else
    echo ""
    echo "Для Gist-режима рекомендуется отдельный токен ТОЛЬКО с правом 'gist' (Classic token -> галочка ONLY gist)."
    read -r -s -p "Вставьте токен для статистики (с правом gist): " GIST_TOKEN
    echo ""
    if [ -z "$GIST_TOKEN" ]; then
        echo "[ERR] Токен не может быть пустым."
        exit 1
    fi

    echo "[*] Создаю секретный Gist для сбора анонимной телеметрии Gor4akovNET..."
    GIST_ID="$(python3 - "$GIST_TOKEN" <<'PY'
import sys, json, urllib.request
tok = sys.argv[1]
body = json.dumps({
    "description": "Gor4akovNET anonymous installation & diagnostics telemetry",
    "public": False,
    "files": {"init.json": {"content": "{\"status\":\"initialized\"}\n"}}
}).encode("utf-8")
req = urllib.request.Request(
    "https://api.github.com/gists",
    data=body,
    method="POST",
    headers={
        "Authorization": f"Bearer {tok}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
        "User-Agent": "Gor4akovNET-Setup"
    }
)
with urllib.request.urlopen(req, timeout=20) as r:
    resp = json.loads(r.read().decode())
print(resp["id"])
PY
)"
    echo "[OK] Создан секретный Gist ID: $GIST_ID"

    if [ "$USE_GH" = "0" ]; then
        read -r -s -p "GitHub PAT с правом repo для записи Actions Secrets (Enter = использовать тот же или вручную): " ADMIN_PAT
        echo ""
        ADMIN_PAT="${ADMIN_PAT:-$GIST_TOKEN}"
    fi

    set_gh_secret "GBOX_STATS_GIST_ID" "$GIST_ID" || true
    set_gh_secret "GBOX_STATS_TOKEN" "$GIST_TOKEN" || true
fi

echo ""
read -r -p "Пересобрать APK в GitHub Actions прямо сейчас? [y/N]: " TRIG
if [ "${TRIG:-N}" = "y" ] || [ "${TRIG:-N}" = "Y" ]; then
    if [ "$USE_GH" = "1" ]; then
        gh workflow run build.yml -R "$REPO" && echo "[OK] Сборка build.yml запущена!"
    else
        echo "[INFO] Откройте вкладку Actions в GitHub и нажмите Run workflow на 'Build Gor4akovNET APK'."
    fi
fi

echo ""
echo "[DONE] Готово! После пересборки APK вкладка 'Статистика' автоматически подключится."
