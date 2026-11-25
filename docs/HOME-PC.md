# Free option: your own PC as the CloudSync host

No cloud bill and no time limits. Your gaming PC at home runs Genshin Impact,
CloudSync on your phone streams it. Based on the
[Moonlight Setup Guide](https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide).

## 1. Install the host (one command, PowerShell as admin)

```powershell
irm https://raw.githubusercontent.com/JebetrexxMC/CloudGaming/main/host-windows/install.ps1 -OutFile install.ps1
.\install.ps1 -InternetHostingTool
```

This installs Sunshine (from your fork build) and the Moonlight Internet Hosting
Tool (from [JebetrexxMC/Internet-Hosting-Tool](https://github.com/JebetrexxMC/Internet-Hosting-Tool)
if it has a release, else [moonlight-stream](https://github.com/moonlight-stream/Internet-Hosting-Tool/releases)).
The tool keeps the router port forwards open. Install Genshin with HoYoPlay on this PC.

## 2. Pair once, at home

1. Open `https://localhost:47990/` on the PC and create the Sunshine account.
2. On the same Wi-Fi, open CloudSync, tap Play, enter the PIN on the Sunshine page.

## 3. Play from anywhere

Run "Moonlight Internet Streaming Tester" from the Start Menu. Put the IP it shows
into CloudSync (Enter host) or the repo variable `CG_HOST` so it is built into the APK.

If the tool cannot open ports, forward them manually to the PC:

| Protocol | Ports |
|---|---|
| TCP | 47984, 47989, 48010 |
| UDP | 47998, 47999, 48000, 48002, 48010 |

If it reports CGNAT (common on PH mobile/fiber ISPs), use Tailscale or ZeroTier
on both PC and phone instead, see [NETWORKS.md](NETWORKS.md).

Tips: wired Ethernet on the PC, 5 GHz Wi-Fi on the phone, and keep the PC awake.
