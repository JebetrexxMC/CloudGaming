# Genshin Impact on your cloud host

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
