# CloudyPad (Linux alternative)

[CloudyPad](https://github.com/JebetrexxMC/cloudypad) deploys a Linux gaming VM on AWS, Azure, GCP, Scaleway, Paperspace, or your own server over SSH. It installs the GPU drivers and either **Wolf** or **Sunshine**, then pairs Moonlight for you.

> **Genshin Impact note:** CloudyPad is Linux-only. Genshin's anti-cheat isn't supported under Proton/Wine, and running it there can get your account flagged. For Genshin, use the Windows host in [`../aws`](../aws) or [`../../host-windows`](../../host-windows). Use CloudyPad for Steam, Epic, or GOG games that work on Proton, or as a multi-user box.

## Deploy

```bash
./deploy.sh            # installs the CLI from the JebetrexxMC fork, then runs `cloudypad create`
```

Or run it by hand:

```bash
curl -fsSL https://raw.githubusercontent.com/JebetrexxMC/cloudypad/master/install.sh | bash
cloudypad create       # choose provider "aws", region ap-southeast-1, streaming server Wolf or Sunshine
cloudypad stop mypad   # stop paying when done
cloudypad start mypad
```

CloudyPad has built-in cost alerts and spot-instance support. See the [CloudyPad docs](https://docs.cloudypad.gg).
