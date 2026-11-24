# Oracle Always Free control box

A free 24/7 ARM VM (Oracle Cloud Always Free: up to 4 OCPU / 24 GB) that stays online while the GPU VM is off:

| Service | URL | Purpose |
| --- | --- | --- |
| [Headscale](https://github.com/juanfont/headscale) | `https://HS_DOMAIN` | Your own Tailscale control server, with unlimited devices and no account |
| [moonlight-web-stream](https://github.com/MrCreativ3001/moonlight-web-stream) | `https://PLAY_DOMAIN` | Play Genshin in a browser through the tailnet |
| Caddy | | Automatic HTTPS |

## Setup

1. Create an Always Free **Ampere A1** Ubuntu VM. If capacity is short, your forks [oci-claimer](https://github.com/JebetrexxMC/oci-claimer) and [Oracle-Free-Arm-VPS-PS](https://github.com/JebetrexxMC/Oracle-Free-Arm-VPS-PS) keep retrying until one is free.
2. Point two [DuckDNS](https://www.duckdns.org) names at the VM's public IP.
3. On the VM:
   ```bash
   git clone https://github.com/JebetrexxMC/CloudGaming && cd CloudGaming/cloud/oracle-free
   cp .env.example .env && nano .env
   sudo ./setup.sh
   ```
4. Copy the printed pre-auth key into `cloud/aws/terraform.tfvars`:
   ```hcl
   vpn               = "Headscale"
   headscale_url     = "https://hs-yourname.duckdns.org"
   tailscale_authkey = "<key>"
   ```
5. Phone: install the Tailscale APK (from this repo's Actions), then go to **Settings → Accounts → ⋮ → Use an alternate server** and enter your `HS_DOMAIN`.

The moonlight-web container reaches the Genshin host over the tailnet once you add this VM to Headscale too (`curl -fsSL https://tailscale.com/install.sh | sh && tailscale up --login-server https://HS_DOMAIN`).
