#!/usr/bin/env bash
set -euo pipefail
if [[ "\${EUID}" -ne 0 ]]; then
  echo "Bu kurulum sudo ile çalıştırılmalı: sudo bash install.sh" >&2
  exit 1
fi
SOURCE_DIR="$(cd "$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="/opt/cloud-agent"
STATE_DIR="/var/lib/cloud-agent"
CONFIG_DIR="/etc/cloud-agent"
SERVICE_USER="cloud-agent"
[[ -f "$SOURCE_DIR/agent.py" ]] || { echo "agent.py bulunamadı." >&2; exit 1; }
if ! id "$SERVICE_USER" >/dev/null 2>&1; then
  useradd --system --home-dir "$STATE_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
fi
install -d -o root -g root -m 0755 "$APP_DIR"
install -d -o "$SERVICE_USER" -g "$SERVICE_USER" -m 0750 "$STATE_DIR"
install -d -o root -g "$SERVICE_USER" -m 0750 "$CONFIG_DIR"
install -o root -g root -m 0755 "$SOURCE_DIR/agent.py" "$APP_DIR/agent.py"
if [[ ! -f "$CONFIG_DIR/agent.env" ]]; then
  read -r -s -p "OpenAI API anahtarını gir (ekranda görünmez): " OPENAI_API_KEY
  echo
  [[ -n "$OPENAI_API_KEY" ]] || { echo "OpenAI API anahtarı boş olamaz." >&2; exit 1; }
  read -r -p "OpenAI model adı [gpt-6]: " OPENAI_MODEL
  OPENAI_MODEL="\${OPENAI_MODEL:-gpt-6}"
  ACCESS_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(36))')"
  umask 077
  {
    printf 'OPENAI_API_KEY=%s\n' "$OPENAI_API_KEY"
    printf 'OPENAI_MODEL=%s\n' "$OPENAI_MODEL"
    printf 'CLOUD_AGENT_TOKEN=%s\n' "$ACCESS_TOKEN"
    printf 'CLOUD_AGENT_PORT=8765\n'
    printf 'CLOUD_AGENT_DB=/var/lib/cloud-agent/agent.db\n'
  } > "$CONFIG_DIR/agent.env"
  chown root:"$SERVICE_USER" "$CONFIG_DIR/agent.env"
  chmod 0640 "$CONFIG_DIR/agent.env"
  unset OPENAI_API_KEY ACCESS_TOKEN
else
  echo "Mevcut /etc/cloud-agent/agent.env korunuyor."
fi
cat > /etc/systemd/system/cloud-agent.service <<'UNIT'
[Unit]
Description=Approval-gated OpenAI Cloud Agent
After=network-online.target
Wants=network-online.target
[Service]
Type=simple
User=cloud-agent
Group=cloud-agent
WorkingDirectory=/opt/cloud-agent
EnvironmentFile=/etc/cloud-agent/agent.env
ExecStart=/usr/bin/python3 /opt/cloud-agent/agent.py
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/cloud-agent
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now cloud-agent.service
echo
echo "Cloud Agent kuruldu ve başlatıldı."
echo "Servis: systemctl status cloud-agent"
echo "Günlük: journalctl -u cloud-agent -f"
echo "API yalnızca 127.0.0.1:8765 üzerinde dinler."
echo "Token /etc/cloud-agent/agent.env içinde; paylaşmayın."
