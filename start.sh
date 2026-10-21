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

# Warn if the port is already in use.
if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":${PORT} "; then
  fail "ERR-START-005" "Port ${PORT} is already in use. Choose another with --port N."
fi

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
