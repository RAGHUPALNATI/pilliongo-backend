#!/usr/bin/env bash
# One-time setup for a fresh Ubuntu server. Safe to run again.
#   1. adds 2 GB of swap (spare "overflow" memory, so a 1 GB server
#      doesn't crash when Java has a busy moment)
#   2. installs Docker
#   3. downloads docker-compose.yml, Caddyfile and .env.example
set -euo pipefail

REPO_RAW="https://raw.githubusercontent.com/RAGHUPALNATI/pilliongo-backend/main/deploy"
APP_DIR="$HOME/pilliongo"

echo "==> 1/3 Swap"
if ! sudo swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "==> 2/3 Docker"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

echo "==> 3/3 App files in $APP_DIR"
mkdir -p "$APP_DIR"
cd "$APP_DIR"
curl -fsSLO "$REPO_RAW/docker-compose.yml"
curl -fsSLO "$REPO_RAW/Caddyfile"
curl -fsSL  "$REPO_RAW/.env.example" -o .env.example
[ -f .env ] || cp .env.example .env

cat <<MSG

Done. Next:
  1. Log out and back in (so your user can run docker).
  2. cd $APP_DIR && nano .env      # fill in the real values
  3. docker compose pull && docker compose up -d
  4. docker compose logs -f app   # watch it start (Ctrl+C to stop watching)
MSG
