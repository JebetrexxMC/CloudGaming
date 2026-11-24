# Private network options

With a private network, the Moonlight ports never face the internet. Terraform closes them automatically when `vpn` is not `None`.

| Option | Setup effort | Cost | Phone app | Notes |
| --- | --- | --- | --- | --- |
| **Tailscale** | lowest | free (personal) | Tailscale APK (CI) / Play Store | `vpn = "Tailscale"` + auth key |
| **Headscale** | medium | free (Oracle box) | Same Tailscale APK → "alternate server" | Your own control server, no device limit. [`cloud/oracle-free`](../cloud/oracle-free) |
| **ZeroTier** | low | free tier | ZeroTier One | `vpn = "ZeroTier"` + network ID, authorize the host in my.zerotier.com |
| **EasyTier** | low | free | EasyTier (GitHub releases) | `vpn = "EasyTier"`, pick any network name + secret. Uses public relay peers |
| **Nebula** | higher | free | Mobile Nebula | `cloud/nebula/make-certs.sh`, the Oracle box acts as lighthouse (UDP 4242) |
| Public IP | none | free | – | Ports open only to `allowed_cidr` (your IP) |

Latency tip: all of these try a direct P2P path first. When `tailscale status` shows `relay`, `direct` hasn't been negotiated yet. Open the P2P UDP port (Terraform does this) and give it a few seconds.

## Home PC instead of a cloud VM
* Run `host-windows/install.ps1 -InternetHostingTool` to install Moonlight's Internet Hosting Tool. It handles UPnP and NAT checks, so Moonlight can reach the PC from outside your home.
* Behind CGNAT (common on PH mobile and fiber ISPs), use a VPN option instead.
* Wake the PC with the **Wake my PC** button in the CloudGaming app (enable WoL in BIOS and in the network adapter first).
