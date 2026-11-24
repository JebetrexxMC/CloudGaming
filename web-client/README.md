# Browser client (moonlight-web-stream)

```bash
cp .env.example .env    # set PUBLIC_OR_VPN_IP
docker compose up -d
```

1. Open `http://<ip>:8080` and add your Apollo/Sunshine host. Pair it with the PIN in the Apollo web UI.
2. Controllers and keyboard lock only work over **HTTPS** (a secure context). Put it behind a reverse proxy with TLS, or use Tailscale's `tailscale serve`/`tailscale cert`.
3. To play from outside your network, open UDP `40000-40100` or use the VPN IP.

Upstream project: [moonlight-web-stream](https://github.com/MrCreativ3001/moonlight-web-stream)
