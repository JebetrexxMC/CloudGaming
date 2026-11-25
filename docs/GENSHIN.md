# Genshin Impact on your cloud host

## No exe, no PC: the fastest option
You don't need to download the game or install an .exe if you use a cloud service that already has it:

* **Genshin Impact · Cloud** (official, by HoYoverse). It's an app ([Google Play](https://play.google.com/store/apps/details?id=com.hoyoverse.cloudgames.GenshinImpact)) or a [web page](https://cloudgenshin.hoyoverse.com/en-us). It supports the US, Canada and SEA, including the Philippines. You log in with the same HoYoverse account and keep your saves.
* **GeForce NOW**. The free tier gives 1-hour sessions with ads and a queue. You can start again right after, with no daily limit.

**No Play Store?** CloudSync has **Install APK / XAPK from phone**. It installs an .apk/.xapk/.apks file you downloaded yourself (split APKs are handled), using Android's normal installer prompt. CloudSync does not bundle or host any game files. For Genshin, install **Genshin Impact · Cloud** (`com.hoyoverse.cloudgames.GenshinImpact`, small, streams). The full Genshin XAPK installs the whole game on the phone (20+ GB after first start), so it does not stream.

In CloudSync, use **Play without a PC** or the arrow next to Start Game. The rest of this guide is for your **own** host (AWS/home PC + Sunshine/Apollo), which has no session limit but needs the Windows game files (~100 GB) on that host.

## Why it's "unlimited"
* Your own VM has **no session timer, no queue, and no idle kick**. `keepalive.ps1` stops Windows from sleeping or locking, and restarts Apollo/Sunshine if it crashes.
* Genshin itself still logs you out after long AFK periods. That comes from the game, not the stream.
* The only limit is cost. A spot `g4dn.xlarge` in Singapore is usually well under USD 1/hour. Check [AWS pricing](https://aws.amazon.com/ec2/pricing/) and set a billing alarm.

## Install the game
1. RDP to the VM (`terraform output rdp`, password: `terraform output -raw admin_password`), or stream the **Desktop** app in Moonlight.
2. Run **Install HoYoPlay** (or Collapse/Starward) from the desktop, log in, and install Genshin to the default path.
3. If you change the install path, re-run:
   `powershell -ExecutionPolicy Bypass -File C:\CloudGaming\repo\CloudGaming-main\host-windows\install.ps1 -GenshinExe 'D:\Games\Genshin Impact game\GenshinImpact.exe'`

## Settings
* Graphics: 1080p, FPS 60, V-Sync **off**, Render resolution 1.0, Shadows Medium. A T4 holds 60 fps fine.
* Controller: Genshin detects the ViGEm virtual Xbox pad automatically (Settings → Controls → Controller).
* Touch on a phone: in Moonlight → Settings, turn **Touchscreen trackpad mode** off so taps act as direct mouse clicks.

## Rules
Streaming your own game to yourself is fine. Account sharing, bots, macros, and FPS unlockers or other game-file mods break HoYoverse's ToS and can get you banned. This repo doesn't do any of that.

## Storage: where the game lives

The game is installed on the cloud PC's disk (200 GB gp3 by default), not on your phone or home PC. Your phone only needs the CloudSync app (about 30 MB).

Streaming the Android version of Genshin (an APK on a cloud Android emulator) is not recommended:

* It still needs about 30 GB on the cloud machine after the in-game resource download, so it saves no space.
* Cloud Android (Redroid, Waydroid) has no good NVIDIA GPU support, so it runs slowly.
* The Windows version on Apollo looks better, supports gamepads through ViGEmBus, and is what this stack is tuned for.

Install the PC version from the official HoYoPlay launcher (or Collapse / Starward). It downloads straight from HoYoverse to the cloud PC at datacenter speed.
