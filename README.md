# CloudGaming

[![Website](https://img.shields.io/badge/website-live-5ee6d0)](https://jebetrexxmc.github.io/CloudGaming/)
[![Genshin host](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/JebetrexxMC/CloudGaming/live-status/host.json)](https://jebetrexxmc.github.io/CloudGaming/#status)
[![APK](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/JebetrexxMC/CloudGaming/live-status/apk.json)](../../actions/workflows/build-apk.yml)

Your own self-hosted cloud gaming rig for **Genshin Impact**, with **no session timer and no queue**.

**Website + autobot console:** [jebetrexxmc.github.io/CloudGaming](https://jebetrexxmc.github.io/CloudGaming/). It shows live host status, APK downloads, and a console bot that checks everything and tells you how to bring the host online.

Commercial cloud services (including Genshin's own cloud version) limit how long you can play or put you in a queue. Here you rent a GPU machine (or use your own PC), stream it with **Apollo / Sunshine**, and play on any device with **Moonlight**. Nothing in this stack disconnects you after a set time. The only limit is your cloud bill, so remember to stop the VM when you're done.

```
 ┌──────────── Cloud GPU VM (Windows) ─────────────┐        ┌────────── Your devices ──────────┐
 │ Genshin Impact  (HoYoPlay / Collapse / Starward)│        │ Android  → Moonlight APK (CI)    │
 │ Apollo or Sunshine  (NVENC capture + encode)    │ ─────► │ PC/Mac   → moonlight-qt          │
 │ ViGEmBus  (virtual Xbox/DS4 controller)         │ stream │ Browser  → moonlight-web-stream  │
 │ keepalive watchdog (no sleep, no disconnect)    │        │ Friends  → Nearcade (co-op view) │
 │ Tailscale/Headscale/ZeroTier/EasyTier/Nebula    │ ◄────► │ Tailscale APK / ZeroTier / Nebula│
 └─────────────────────────────────────────────────┘        └──────────────────────────────────┘
 ┌──── Oracle Always Free ARM box (24/7, 0 USD) ───┐
 │ Headscale · moonlight-web-stream · Caddy HTTPS  │
 └─────────────────────────────────────────────────┘        └──────────────────────────────────┘
```

## Quick start

### 1. Get a host

| Option | When to use | How |
| --- | --- | --- |
| **AWS GPU VM (recommended)** | No gaming PC. Singapore region is closest to the Philippines | [`cloud/aws`](cloud/aws) Terraform, see below |
| **Your own Windows PC** | You already have a gaming PC | Run [`host-windows/install.ps1`](host-windows/install.ps1) on it |
| **CloudyPad (Linux)** | One-command deploy for Steam/Proton games | [`cloud/cloudypad`](cloud/cloudypad). Not for Genshin (anti-cheat) |
| **Linux + Wolf** | Other games, multi-user | [`linux-wolf`](linux-wolf). Not recommended for Genshin (anti-cheat) |

```bash
cd cloud/aws
cp terraform.tfvars.example terraform.tfvars   # set allowed_cidr (curl ifconfig.me) and vpn
terraform init && terraform apply
```

The VM runs [`host-windows/install.ps1`](host-windows/install.ps1) on first boot. It installs the NVIDIA gaming driver, Apollo, ViGEmBus, and the Genshin launcher, adds the Genshin app to Apollo, and turns on the keepalive watchdog.

**Private network (recommended):** set `vpn` to `Tailscale`, `Headscale`, `ZeroTier`, `EasyTier` or `Nebula`. The Moonlight ports then stay closed to the internet, and you connect over the VPN IP. See [`docs/NETWORKS.md`](docs/NETWORKS.md).

**Extras** (`extras` in tfvars, or switches on `install.ps1`): `RustDesk` (remote admin without breaking the stream), `VirtualDisplay` (Virtual-Display-Driver), `Playnite` (fullscreen library app), `-InternetHostingTool` (home PC).

### 2. Pair a client

1. Open the Apollo web UI at `https://<vm-ip>:47990` and set a username and password.
2. In Moonlight, add the host by IP. Enter the PIN from Moonlight into the Apollo UI (**PIN** tab).
3. RDP in once (`terraform output rdp`), open the launcher, log in, and install Genshin. You can also use the **Desktop** entry in Moonlight for this step.
4. Start **Genshin Impact** from Moonlight.

### 3. Play anywhere

* **Android**: download `moonlight-cloudgaming-debug.apk` (and `tailscale-debug.apk` if you use Tailscale) from the latest [Build Android APKs](../../actions/workflows/build-apk.yml) run (Artifacts), or from Releases when you push a `v*` tag.
* **Windows/macOS/Linux**: [moonlight-qt](https://github.com/JebetrexxMC/moonlight-qt).
* **Browser (no install)**: [`web-client`](web-client) runs moonlight-web-stream.

### 4. Stop paying when you stop playing

From the browser: **Actions → vm-control → Run workflow** (`start` / `stop`, optional auto-stop timer). Or from a terminal:

```bash
./scripts/vm.sh stop     # stops the VM (disk kept, game stays installed)
./scripts/vm.sh start    # start it again, prints the new IP
```

## Repository layout

| Path | Contents |
| --- | --- |
| `host-windows/install.ps1` | One-shot host setup (drivers, Apollo/Sunshine, ViGEmBus, launcher, app entry) |
| `host-windows/keepalive.ps1` | Disables sleep, idle lock, and screensaver. Restarts the streaming service if it stops |
| `host-windows/apps.genshin.json` | Apollo/Sunshine app entry for Genshin Impact |
| `cloud/aws/` | Terraform for a Windows GPU VM (g4dn/g5, optional spot) with locked-down ports and optional Tailscale/ZeroTier |
| `cloud/cloudypad/` | CloudyPad (JebetrexxMC fork) deploy script for a Linux VM |
| `cloud/oracle-free/` | Free 24/7 control box: Headscale + moonlight-web-stream + Caddy |
| `cloud/nebula/` | Nebula CA and certificate generator (lighthouse, host, phone, PC) |
| `site/` | GitHub Pages website with live status, the autobot console (engine from wagu) and an EmulatorJS Arcade page |
| `cloud-morph/` | Deploy a Windows app to a Linux VPS with cloud-morph (browser play, Wine). Not for Genshin |
| `net/` | NetWire networking toolkit copied from wagu |
| `scripts/vm.sh` | Start, stop, and check the status of the VM |
| `web-client/` | Docker Compose setup for moonlight-web-stream (play in a browser) |
| `linux-wolf/` | Wolf and Wolf Den Compose setup (Linux alternative) |
| `.github/workflows/build-apk.yml` | Builds the Moonlight, Tailscale and WakeOnLan Android APKs (signed Moonlight release when secrets are set) |
| `.github/workflows/pages.yml` | Deploys `site/` to GitHub Pages |
| `.github/workflows/live-status.yml` | Every 15 min: probes host and control box, writes `status.json` and badges to the `live-status` branch |
| `.github/workflows/vm-control.yml` | Start or stop the AWS host from the Actions tab |
| `android/` | APK branding overlay and signing notes |
| `.github/workflows/validate.yml` | Lints PowerShell, Terraform, Compose files, and shell scripts |
| `docs/` | Genshin tips, clients, networks, troubleshooting |

## Repository secrets (optional)

| Secret | Used by |
| --- | --- |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `CG_INSTANCE_ID`, `CG_REGION` | vm-control |
| `CG_HOST`, `CG_VPN`, `HEADSCALE_URL`, `PLAY_URL` | live-status probes (the host address is never published, only online or offline) |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Signed Moonlight release APK |
| `tools/README.md` | What each upstream tool does in this stack |

## Recommended stream settings for Genshin

| Setting | Phone (4G/5G) | Wi-Fi / PC |
| --- | --- | --- |
| Resolution | 1280×720 | 1920×1080 |
| FPS | 60 | 60 |
| Bitrate | 10–15 Mbps | 20–30 Mbps |
| Codec | HEVC (H.265) | HEVC or AV1 (g5 or newer) |

In-game, set **FPS = 60** and turn **V-Sync off**. Render resolution 1.0 is enough on a T4 (g4dn).

## Fair use

This streams **your own** copy of Genshin Impact from **your own** machine to you, like Steam Remote Play. It doesn't change, inject into, or automate the game. Don't share accounts, and don't run bots or macros, since that breaks HoYoverse's Terms of Service.

See [`docs/`](docs) for more detail.
