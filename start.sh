#!/usr/bin/env bash
#
# J~Net App Store — start script
# ---------------------------------------------------------------------------
# Starts the Node.js store server for the J~Net App Store web/app.
# Installs dependencies on first run if node_modules is missing.
#
# Port: 8090 by default (override with PORT=... or --port N).
# ---------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/server"
PORT="${PORT:-8090}"
HOST="${HOST:-0.0.0.0}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-jnetai}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://apps.jnetai.com}"

log()  { printf '\033[1;36m[start]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[start][%s]\033[0m %s\n' "$1" "$2" >&2; exit 1; }

usage() {
  cat <<EOF
J~Net App Store server

Usage: ./start.sh [options]

Options:
  --port N          Port to listen on (default: 8090)
  --host HOST       Bind address (default: 0.0.0.0)
  --user NAME       Admin panel username (default: admin)
  --password PASS   Admin panel password (default: jnetai)
  -h, --help        Show this help

Environment overrides: PORT, HOST, ADMIN_USER, ADMIN_PASSWORD, PUBLIC_BASE_URL
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     PORT="${2:-}"; shift 2 ;;
    --host)     HOST="${2:-}"; shift 2 ;;
    --user)     ADMIN_USER="${2:-}"; shift 2 ;;
    --password) ADMIN_PASSWORD="${2:-}"; shift 2 ;;
    -h|--help)  usage; exit 0 ;;
    *) fail "ERR-START-000" "Unknown option: $1 (use --help)" ;;
  esac
done

[[ "$PORT" =~ ^[0-9]+$ ]] || fail "ERR-START-001" "Invalid port: '$PORT'"
[[ -f "$SERVER_DIR/server.js" ]] || fail "ERR-START-002" "server.js not found in $SERVER_DIR"
command -v node >/dev/null 2>&1 || fail "ERR-START-003" "Node.js not found. Run ./setup.sh first."

if [[ ! -d "$SERVER_DIR/node_modules" ]]; then
  log "node_modules missing — installing dependencies ..."
  ( cd "$SERVER_DIR" && npm install --no-audit --no-fund ) \
    || fail "ERR-START-004" "npm install failed"
fi

mkdir -p "$SERVER_DIR/data" "$SERVER_DIR/uploads" "$SERVER_DIR/logs"

# --- Free the port (restart behaviour) ------------------------------------
# Find PIDs listening on the port, stop them, then start fresh. This makes
# start.sh act as a restart and resolves "port already in use" automatically.
pids_on_port() {
  local pids=""
  if command -v lsof >/dev/null 2>&1; then
    pids="$(lsof -ti tcp:"$PORT" -sTCP:LISTEN 2>/dev/null || true)"
  fi
  if [[ -z "$pids" ]] && command -v ss >/dev/null 2>&1; then
    pids="$(ss -ltnp 2>/dev/null | awk -v p=":${PORT}" '$4 ~ p {print}' \
      | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u || true)"
  fi
  if [[ -z "$pids" ]] && command -v fuser >/dev/null 2>&1; then
    pids="$(fuser "${PORT}/tcp" 2>/dev/null || true)"
  fi
  echo "$pids"
}

free_port() {
  # If a systemd service owns the port, stop it so it doesn't respawn.
  if command -v systemctl >/dev/null 2>&1 \
     && systemctl is-active --quiet jnet-app-store 2>/dev/null; then
    log "Stopping systemd service jnet-app-store ..."
    sudo systemctl stop jnet-app-store 2>/dev/null || systemctl stop jnet-app-store 2>/dev/null || true
    sleep 1
  fi

  local pids
  pids="$(pids_on_port)"
  [[ -z "$pids" ]] && return 0

  local me="$$"
  for pid in $pids; do
    [[ "$pid" == "$me" || "$pid" == "1" ]] && continue
    log "Port ${PORT} in use by PID ${pid} — stopping it ..."
    kill "$pid" 2>/dev/null || sudo kill "$pid" 2>/dev/null || true
  done

  # Give processes a moment to exit, then force-kill any stragglers.
  for _ in 1 2 3 4 5; do
    sleep 1
    [[ -z "$(pids_on_port)" ]] && break
  done
  for pid in $(pids_on_port); do
    [[ "$pid" == "$me" || "$pid" == "1" ]] && continue
    log "Force-killing PID ${pid} on port ${PORT} ..."
    kill -9 "$pid" 2>/dev/null || sudo kill -9 "$pid" 2>/dev/null || true
  done

  if [[ -n "$(pids_on_port)" ]]; then
    fail "ERR-START-005" "Port ${PORT} is still in use after stopping processes. Choose another with --port N."
  fi
  log "Port ${PORT} is now free."
}

free_port

echo
log "J~Net App Store server starting"
echo "  Port:        ${PORT}"
echo "  Bind:        ${HOST}"
echo "  Store page:  http://localhost:${PORT}/"
echo "  Admin panel: http://localhost:${PORT}/admin   (user: ${ADMIN_USER})"
echo "  Catalog API: http://localhost:${PORT}/api/apps"
echo "  Reverse proxy apps.jnetai.com -> 127.0.0.1:${PORT}"
echo

cd "$SERVER_DIR"
exec env \
  PORT="$PORT" \
  HOST="$HOST" \
  ADMIN_USER="$ADMIN_USER" \
  ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  PUBLIC_BASE_URL="$PUBLIC_BASE_URL" \
  node server.js
