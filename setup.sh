#!/usr/bin/env bash
#
# J~Net App Store — Ubuntu setup script
# ---------------------------------------------------------------------------
# Prepares and installs the Node.js store server on Ubuntu/Debian.
#   1. Ensures Node.js (>=18) and npm are available.
#   2. Installs server dependencies (npm install).
#   3. Creates runtime folders (data/, uploads/, logs/).
#   4. Optionally installs a systemd service (--service, needs sudo).
#
# Port: 8090 by default (override with PORT=... or --port N).
#
# Every failure prints a stable ERR-* code so problems can be traced quickly.
# ---------------------------------------------------------------------------

set -euo pipefail

# --- Config ---------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/server"
PORT="${PORT:-8090}"
HOST="${HOST:-0.0.0.0}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://apps.jnetai.com}"
INSTALL_SERVICE=0
NODE_MAJOR_MIN=18
SERVICE_NAME="jnet-app-store"

# --- Helpers --------------------------------------------------------------
log()  { printf '\033[1;36m[setup]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[setup]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[setup][%s]\033[0m %s\n' "$1" "$2" >&2; exit 1; }

usage() {
  cat <<EOF
J~Net App Store setup

Usage: ./setup.sh [options]

Options:
  --port N          Port for the store server (default: 8090)
  --service         Install a systemd service (requires sudo)
  --user NAME       Admin panel username (default: admin)
  --password PASS   Admin panel password (default: randomly generated)
  -h, --help        Show this help

Environment overrides: PORT, HOST, ADMIN_USER, ADMIN_PASSWORD, PUBLIC_BASE_URL
EOF
}

# --- Arguments ------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     PORT="${2:-}"; shift 2 ;;
    --service)  INSTALL_SERVICE=1; shift ;;
    --user)     ADMIN_USER="${2:-}"; shift 2 ;;
    --password) ADMIN_PASSWORD="${2:-}"; shift 2 ;;
    -h|--help)  usage; exit 0 ;;
    *) fail "ERR-SETUP-000" "Unknown option: $1 (use --help)" ;;
  esac
done

[[ "$PORT" =~ ^[0-9]+$ ]] || fail "ERR-SETUP-001" "Invalid port: '$PORT'"

# --- OS check -------------------------------------------------------------
if [[ -r /etc/os-release ]]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  log "Detected OS: ${PRETTY_NAME:-unknown}"
  case "${ID:-}" in
    ubuntu|debian|linuxmint|pop) : ;;
    *) warn "This setup targets Ubuntu/Debian; continuing anyway." ;;
  esac
else
  warn "Could not read /etc/os-release; continuing."
fi

if [[ ! -d "$SERVER_DIR" ]]; then
  fail "ERR-SETUP-002" "Server directory not found: $SERVER_DIR"
fi

# --- sudo helper ----------------------------------------------------------
SUDO=""
if [[ "$(id -u)" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
  fi
fi

apt_install() {
  if [[ -z "$SUDO" && "$(id -u)" -ne 0 ]]; then
    warn "Cannot install packages without root/sudo: $*"
    return 1
  fi
  $SUDO apt-get update -y >/dev/null 2>&1 || true
  $SUDO DEBIAN_FRONTEND=noninteractive apt-get install -y "$@"
}

# --- Node.js --------------------------------------------------------------
node_major() {
  node -v 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/' || echo 0
}

install_node() {
  log "Installing Node.js ${NODE_MAJOR_MIN}+ ..."
  if command -v apt-get >/dev/null 2>&1; then
    apt_install curl ca-certificates gnupg || true
    local ver="20"
    if [[ -n "$SUDO" || "$(id -u)" -eq 0 ]]; then
      curl -fsSL "https://deb.nodesource.com/setup_${ver}.x" | $SUDO -E bash - \
        || fail "ERR-SETUP-003" "Failed to add NodeSource repository"
      apt_install nodejs || fail "ERR-SETUP-004" "Failed to install Node.js"
    else
      fail "ERR-SETUP-005" "Node.js is missing and cannot be installed without root/sudo. Install Node.js >= ${NODE_MAJOR_MIN} manually."
    fi
  else
    fail "ERR-SETUP-006" "apt-get not found; install Node.js >= ${NODE_MAJOR_MIN} manually."
  fi
}

if ! command -v node >/dev/null 2>&1; then
  install_node
else
  CUR_MAJOR="$(node_major)"
  if [[ "${CUR_MAJOR:-0}" -lt "$NODE_MAJOR_MIN" ]]; then
    warn "Node.js v${CUR_MAJOR} is too old (need >= ${NODE_MAJOR_MIN}); installing newer version."
    install_node
  else
    log "Node.js $(node -v) already installed."
  fi
fi

command -v npm >/dev/null 2>&1 || install_node

log "Node: $(node -v)   npm: $(npm -v)"

# --- Install dependencies -------------------------------------------------
log "Installing server dependencies in $SERVER_DIR ..."
( cd "$SERVER_DIR" && npm install --no-audit --no-fund ) \
  || fail "ERR-SETUP-007" "npm install failed"

# --- Runtime folders ------------------------------------------------------
mkdir -p "$SERVER_DIR/data" "$SERVER_DIR/uploads" "$SERVER_DIR/logs" "$SERVER_DIR/images"
log "Ensured data/, uploads/, logs/ and images/ directories exist."

# --- Admin credentials ----------------------------------------------------
random_password() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 18 | tr -d '/+=' | cut -c1-16
  else
    head -c 24 /dev/urandom | base64 | tr -d '/+=' | cut -c1-16
  fi
}

SETTINGS_FILE="$SERVER_DIR/data/settings.json"
GENERATED_PASSWORD=""
if [[ -f "$SETTINGS_FILE" ]]; then
  EXISTING_USER="$(node -e "try{process.stdout.write(require('$SETTINGS_FILE').adminUser||'admin')}catch(e){process.stdout.write('admin')}" 2>/dev/null || echo admin)"
  ADMIN_USER="$EXISTING_USER"
  log "Existing admin credentials found in data/settings.json (password unchanged)."
else
  if [[ -z "$ADMIN_PASSWORD" ]]; then
    ADMIN_PASSWORD="$(random_password)"
    GENERATED_PASSWORD="$ADMIN_PASSWORD"
  fi
  ESC_USER="${ADMIN_USER//\"/\\\"}"
  ESC_PASS="${ADMIN_PASSWORD//\"/\\\"}"
  printf '{\n  "adminUser": "%s",\n  "adminPassword": "%s"\n}\n' "$ESC_USER" "$ESC_PASS" > "$SETTINGS_FILE"
  chmod 600 "$SETTINGS_FILE"
  log "Created admin credentials in data/settings.json (mode 0600)."
fi

# --- Optional systemd service --------------------------------------------
if [[ "$INSTALL_SERVICE" -eq 1 ]]; then
  if [[ -z "$SUDO" && "$(id -u)" -ne 0 ]]; then
    warn "Skipping systemd service (needs root/sudo)."
  else
    NODE_BIN="$(command -v node)"
    UNIT_PATH="/etc/systemd/system/${SERVICE_NAME}.service"
    log "Installing systemd service: $UNIT_PATH"
    $SUDO tee "$UNIT_PATH" >/dev/null <<EOF
[Unit]
Description=J~Net App Store server
After=network.target

[Service]
Type=simple
WorkingDirectory=${SERVER_DIR}
Environment=PORT=${PORT}
Environment=HOST=${HOST}
Environment=ADMIN_USER=${ADMIN_USER}
Environment=ADMIN_PASSWORD=${ADMIN_PASSWORD}
Environment=PUBLIC_BASE_URL=${PUBLIC_BASE_URL}
ExecStart=${NODE_BIN} ${SERVER_DIR}/server.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
    $SUDO systemctl daemon-reload
    $SUDO systemctl enable "$SERVICE_NAME"
    $SUDO systemctl restart "$SERVICE_NAME"
    log "Service '${SERVICE_NAME}' installed and started."
    log "Status: sudo systemctl status ${SERVICE_NAME}"
  fi
fi

# --- Done -----------------------------------------------------------------
echo
log "Setup complete."
echo "  Port:        ${PORT}  (reverse proxy apps.jnetai.com -> 127.0.0.1:${PORT})"
echo "  Store page:  http://<server-ip>:${PORT}/"
echo "  Admin panel: http://<server-ip>:${PORT}/admin"
echo "  Catalog API: http://<server-ip>:${PORT}/api/apps"
echo "  Admin user:  ${ADMIN_USER}"
if [[ -n "$GENERATED_PASSWORD" ]]; then
  echo
  echo "  ============================================================"
  echo "   ADMIN PASSWORD (generated):  ${GENERATED_PASSWORD}"
  echo "   Save this now — you can change it later in the admin panel."
  echo "  ============================================================"
else
  echo "  Admin pass:  (unchanged — see $SETTINGS_FILE)"
fi
if [[ "$INSTALL_SERVICE" -eq 1 ]]; then
  echo "  Start/stop:  sudo systemctl {start|stop|restart} ${SERVICE_NAME}"
else
  echo "  Start now:   ./start.sh --port ${PORT}"
fi
echo
