# CloudSync (one Android app)

`.github/workflows/build-apk.yml` builds a single app, **CloudSync** (`cloudsync-debug.apk`).

It is [moonlight-android](https://github.com/JebetrexxMC/moonlight-android) (your fork of moonlight-stream/moonlight-android) (the streaming engine) with a launcher screen added from `android/moonlight-overlay/`. The look follows [Collapse Launcher](https://github.com/JebetrexxMC/Collapse) (MIT):

* Live game backgrounds, logos, banners, news and social links from the official HoYoPlay launcher feed, the same content Collapse shows. No game art is bundled.
* Game switcher for Genshin Impact, Honkai: Star Rail, Zenless Zone Zero and Honkai Impact 3rd.
* Layered parallax that follows phone tilt, slow zoom, rotating backgrounds with pips.
* Background blurs as you scroll panels over it; acrylic glass panels.
* Swipeable banner carousel, Events / Notices / Info tabs, playtime tracking.
* Collapse-style yellow split **Start Game** button; the arrow opens host controls.



| Button | What it does |
| --- | --- |
| Status card | Reads `status.json` from the `live-status` branch (host online, ping) |
| Start Game | Adds your host to Moonlight and opens it, then you pick Genshin Impact |
| Start host / Stop host | Runs the `vm-control` workflow through the GitHub API (needs a token) |
| All hosts (Moonlight) | The stock Moonlight host list and settings |
| Wake my PC | Sends a Wake-on-LAN magic packet (replaces the separate WakeOnLan APK) |
| Private network | Opens Tailscale, or tells you where to get it |
| Setup | Repo, GitHub token, host address, auto-stop hours, WoL MAC |

The app ID is `com.limelight.cloudsync`, so it installs next to the official Moonlight.

## Setup on the phone

1. Install `cloudsync-debug.apk` from the latest [Release](https://github.com/JebetrexxMC/CloudGaming/releases) and allow "install unknown apps".
2. Create a fine-grained GitHub token: only this repo, **Actions: Read and write**. Paste it in **Setup**.
3. Enter the host address: the Elastic IP from `terraform output`, or the Tailscale name if you use a VPN.
4. Tap **Start host**, wait 2-3 minutes, then tap **Start Game**. Pair once with the PIN in the Apollo web UI.

## Why VPN is not inside the APK

Android allows only one VPN app at a time, and Tailscale's engine is a separate Go build. If you need a private network, install Tailscale as a second app. You can skip it if the host uses a public IP (`vpn = "None"` in Terraform), since Moonlight pairing already needs a PIN.

To also build the Tailscale APK, run the workflow manually and tick **Also build the optional Tailscale APK**.

## Custom icon / strings

Put files under `android/moonlight-overlay/` using the same layout as `moonlight-android/app/`, for example `src/main/res/mipmap-xxxhdpi/ic_launcher.png`. The workflow copies them in before building.

## Signed release (optional)

Add these repository secrets: `ANDROID_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. With them set, the workflow also produces `cloudsync-release.apk`.
