#!/usr/bin/env bash
# Create a Nebula CA + certs for: lighthouse (the free Oracle box), genshin-host, phone, pc.
# Then zip the host bundle and serve it (privately) as NEBULA_CONFIG_URL for install.ps1.
#   ./make-certs.sh <lighthouse-public-ip>
set -euo pipefail
LH_IP="${1:?usage: $0 <lighthouse-public-ip>}"
VER="${NEBULA_VERSION:-v1.12.0}"
cd "$(dirname "$0")"
mkdir -p out && cd out
if [ ! -x ./nebula-cert ]; then
  curl -fsSL "https://github.com/slackhq/nebula/releases/download/${VER}/nebula-linux-amd64.tar.gz" | tar xz
fi
[ -f ca.key ] || ./nebula-cert ca -name "CloudGaming"
sign() { [ -f "$1.crt" ] || ./nebula-cert sign -name "$1" -ip "$2" -groups "$3"; }
sign lighthouse   10.42.0.1/24 infra
sign genshin-host 10.42.0.10/24 host
sign phone        10.42.0.20/24 client
sign pc           10.42.0.21/24 client

for n in lighthouse genshin-host phone pc; do
  am_lh=false; [ "$n" = lighthouse ] && am_lh=true
  hosts="    - \"10.42.0.1\""; $am_lh && hosts=""
  cat > "$n.yml" <<YML
pki:
  ca: ca.crt
  cert: host.crt
  key: host.key
static_host_map:
  "10.42.0.1": ["${LH_IP}:4242"]
lighthouse:
  am_lighthouse: ${am_lh}
  hosts:
${hosts}
listen:
  host: 0.0.0.0
  port: 4242
punchy:
  punch: true
relay:
  am_relay: ${am_lh}
  use_relays: true
  relays:
${hosts}
tun:
  dev: nebula1
firewall:
  outbound:
    - { port: any, proto: any, host: any }
  inbound:
    - { port: any, proto: icmp, host: any }
    - { port: 47984-48010, proto: any, group: client }
    - { port: any, proto: any, group: infra }
YML
  mkdir -p "bundle-$n" && cp ca.crt "bundle-$n/" && cp "$n.crt" "bundle-$n/host.crt" && cp "$n.key" "bundle-$n/host.key" && cp "$n.yml" "bundle-$n/config.yml"
  (cd "bundle-$n" && zip -q -r "../nebula-$n.zip" .)
done
echo "Done. Bundles in $(pwd):"
ls -1 nebula-*.zip
echo "Phone: import ca.crt + phone.crt/key into Mobile Nebula (or scan the cert QR from 'nebula-cert print -json')."
echo "Keep ca.key offline. Never commit out/."
