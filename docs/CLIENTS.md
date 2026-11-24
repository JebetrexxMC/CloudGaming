# Connecting from your devices

## Direct (public IP)
Add the VM's public IP in Moonlight. Terraform only opens the ports to `allowed_cidr`. If your home IP changes, update it and run `terraform apply`.

## Tailscale (recommended)
1. Create an auth key at login.tailscale.com → Settings → Keys, and set `vpn = "Tailscale"` and `tailscale_authkey` in `terraform.tfvars`.
2. On your phone, install the **Tailscale APK** from this repo's Actions artifacts (or Play Store) and log in to the same account.
3. In Moonlight, add the host by its `100.x.y.z` Tailscale IP (or by the MagicDNS name `genshin-host`).

## ZeroTier
1. Create a network at my.zerotier.com and set `vpn = "ZeroTier"` and `zerotier_network`.
2. Authorize the `genshin-host` member, then join the same network from the ZeroTier One app on your phone or PC.
3. Use the ZeroTier IP in Moonlight.

## Clients
* Android: `cloudgaming-debug.apk`, one app with status, start/stop, Play and Wake-on-LAN (Releases, or Actions → Build Android APKs → Artifacts)
* PC/Mac/Linux: moonlight-qt
* Browser: [`web-client/`](../web-client)
* Friends watching or joining co-op from a browser: Nearcade on the host
