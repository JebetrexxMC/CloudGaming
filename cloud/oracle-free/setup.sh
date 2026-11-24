#!/usr/bin/env bash
# One-shot setup on an Oracle Cloud Always Free Ubuntu/Debian ARM VM (or any small VPS).
#   git clone https://github.com/JebetrexxMC/CloudGaming && cd CloudGaming/cloud/oracle-free
#   cp .env.example .env && nano .env && sudo ./setup.sh
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] || { echo "Create .env first (cp .env.example .env)"; exit 1; }
# shellcheck disable=SC1091
. ./.env

if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi

# Oracle images ship iptables rules that drop everything except SSH.
if command -v iptables >/dev/null; then
  for p in 80 443; do iptables -C INPUT -p tcp --dport "$p" -j ACCEPT 2>/dev/null || iptables -I INPUT 6 -p tcp --dport "$p" -j ACCEPT; done
  iptables -C INPUT -p udp --dport 3478 -j ACCEPT 2>/dev/null || iptables -I INPUT 6 -p udp --dport 3478 -j ACCEPT
  iptables -C INPUT -p udp --dport 40000:40100 -j ACCEPT 2>/dev/null || iptables -I INPUT 6 -p udp --dport 40000:40100 -j ACCEPT
  (command -v netfilter-persistent >/dev/null && netfilter-persistent save) || true
fi

sed -i "s#^server_url: .*#server_url: https://${HS_DOMAIN}#" headscale/config.yaml
docker compose up -d
sleep 5
docker compose exec headscale headscale users create gamer 2>/dev/null || true
echo
echo "Pre-auth key for the Genshin host (put it in cloud/aws terraform.tfvars as tailscale_authkey):"
UID_GAMER=$(docker compose exec headscale headscale users list -o json | python3 -c 'import json,sys;print([u["id"] for u in json.load(sys.stdin) if u["name"]=="gamer"][0])')
docker compose exec headscale headscale preauthkeys create --user "$UID_GAMER" --reusable --expiration 720h
echo
echo "Headscale:     https://${HS_DOMAIN}   (phone: Tailscale app -> ... -> Use alternate server)"
echo "Browser play:  https://${PLAY_DOMAIN}"
echo "Also open in the Oracle VCN security list: TCP 80,443  UDP 3478,40000-40100"
