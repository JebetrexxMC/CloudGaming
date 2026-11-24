# CloudMorph (browser-native Windows app streaming)

[cloud-morph](https://github.com/giongto35/cloud-morph) runs a Windows game under **Wine in Docker** on a Linux box and streams it to any browser over WebRTC. Several people can share one session, as with the Cloud Diablo demo.

| | Apollo + Moonlight (main stack) | CloudMorph |
| --- | --- | --- |
| Genshin Impact | **Yes** (Windows host) | **No**: the anti-cheat doesn't run under Wine |
| Client | Moonlight app / browser via moonlight-web-stream | Any browser, no install |
| GPU | NVENC on a GPU VM | Runs on CPU (light 2D/older games) |
| Cost | GPU VM hours | Fits a cheap VPS or the free Oracle ARM box (x86 emulation is slow, so prefer x86 VPS) |

Use it for older Windows games or collaborative play next to the Genshin host.

## Deploy on a Linux VPS

```bash
./deploy.sh <vps-ip> "/path/to/game/folder" "Game.exe" "Game Window Title"
```

The script clones cloud-morph, writes `config.yaml` in upstream's example layout, then runs `setup_remote.sh`, which installs Docker, Wine and Go, syncs the game, and starts the server under supervisord. It needs root SSH access to the VPS. Then open `http://<vps-ip>`.
