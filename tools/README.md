# Tools used in this stack

## Streaming host (Windows)
| Tool | Role here |
| --- | --- |
| [Apollo](https://github.com/ClassicOldSong/Apollo) | **Default host.** Sunshine fork with a built-in virtual display (SudoVDA), so headless cloud GPUs get a real monitor |
| [Sunshine](https://github.com/JebetrexxMC/Sunshine) | Alternative host (`-StreamHost Sunshine`) |
| [Virtual-Display-Driver](https://github.com/VirtualDrivers/Virtual-Display-Driver) | Virtual monitor for Sunshine on headless VMs (`-VirtualDisplay`, automatic with Sunshine) |
| [ViGEmBus](https://github.com/JebetrexxMC/ViGEmBus) | Virtual Xbox 360 / DS4 driver, so Moonlight controllers work in Genshin |
| [Moonlight Internet Hosting Tool](https://github.com/moonlight-stream/Internet-Hosting-Tool) | Makes a **home** PC reachable over the internet (UPnP, NAT checks) (`-InternetHostingTool`) |
| [RustDesk](https://github.com/rustdesk/rustdesk) | Remote admin without RDP, since RDP breaks the stream's display (`-RustDesk`) |

## Game launchers / library
| Tool | Role here |
| --- | --- |
| HoYoPlay (official) | Default Genshin launcher |
| [Collapse](https://github.com/JebetrexxMC/Collapse) | HoYoverse launcher (`-Launcher Collapse`) |
| [Starward](https://github.com/JebetrexxMC/Starward) | HoYoverse launcher with play-time and gacha records (`-Launcher Starward`) |
| [Playnite](https://github.com/JebetrexxMC/Playnite) | One fullscreen library for every launcher, added as a Moonlight app (`-Playnite`) |
| [H.NotifyIcon](https://github.com/JebetrexxMC/H.NotifyIcon) | Tray-icon library used by the WinUI launchers |
| [ColorThief](https://github.com/JebetrexxMC/ColorThief) | Colour extraction used by Collapse theming |

## Clients
| Tool | Role here |
| --- | --- |
| [moonlight-android](https://github.com/moonlight-stream/moonlight-android) | Android client, **built into an APK by CI** |
| [moonlight-qt](https://github.com/JebetrexxMC/moonlight-qt) | Windows / macOS / Linux / Steam Deck client |
| [moonlight-web-stream](https://github.com/MrCreativ3001/moonlight-web-stream) | Browser client ([`web-client/`](../web-client), [`cloud/oracle-free/`](../cloud/oracle-free)) |
| [WakeOnLan](https://github.com/Florianisme/WakeOnLan) | Wake a home gaming PC from Android, **built into an APK by CI** |
| [Nearcade](https://github.com/TheRealFame/Nearcade) | Friends watch or join co-op from a browser |
| [Deskreen](https://github.com/pavlobu/deskreen) | Turn any device into a second screen or viewer over Wi-Fi |
| [EmulatorJS](https://github.com/EmulatorJS/EmulatorJS) | Browser retro emulators on the website's [Arcade page](../site/arcade.html) (your own ROM files, loaded locally) |
| [shadPS4](https://github.com/JebetrexxMC/shadPS4) | PS4 emulator on the Windows host (`install.ps1 -ShadPS4`), streamed as "PS4 (shadPS4)" in Big Picture mode; your own dumped games |
| [Cloudstream extensions](https://github.com/JebetrexxMC/extensions) | YouTube, Twitch, Dailymotion, Internet Archive, Invidious providers; CloudSync's Watch panel adds this repo to Cloudstream |
| [Collapse](https://github.com/JebetrexxMC/Collapse) | Windows launcher on the host, and the UI model for the CloudSync app (MIT) |
| [REA](https://github.com/mpsamp-open/rea) | Reverse-engineering toolkit used to verify the shipped CloudSync APK ([report](../docs/REA.md)) |
| [cloud-morph](https://github.com/giongto35/cloud-morph) | Windows apps under Wine streamed to the browser over WebRTC, collaborative mode ([`cloud-morph/`](../cloud-morph)). Not for Genshin (anti-cheat) |

## Private networking (no open ports)
| Tool | Role here |
| --- | --- |
| [Tailscale Android](https://github.com/JebetrexxMC/tailscale-android) | Phone VPN client, **built into an APK by CI**. Works with Tailscale or Headscale |
| [Headscale](https://github.com/juanfont/headscale) | Self-hosted Tailscale control server on the free Oracle box ([`cloud/oracle-free`](../cloud/oracle-free)) |
| [ZeroTierOne](https://github.com/mpsamp-open/ZeroTierOne) | Virtual LAN (`-Vpn ZeroTier`) |
| [EasyTier](https://github.com/EasyTier/EasyTier) | Decentralized P2P mesh with public relay peers (`-Vpn EasyTier`) |
| [Nebula](https://github.com/slackhq/nebula) + [Mobile Nebula](https://github.com/DefinedNet/mobile_nebula) | Certificate-based overlay network (`-Vpn Nebula`, [`cloud/nebula`](../cloud/nebula)) |
| [netbird](https://github.com/JebetrexxMC/netbird), [firezone](https://github.com/JebetrexxMC/firezone), [amnezia-client](https://github.com/JebetrexxMC/amnezia-client), [algo](https://github.com/JebetrexxMC/algo), [openvpn-install](https://github.com/JebetrexxMC/openvpn-install) | Other VPN options from your account. Any of them works: put both ends on the same network and use the VPN IP in Moonlight |
| [bore](https://github.com/JebetrexxMC/bore) | TCP tunnel. Fine for the Apollo web UI, but **not** for the game stream (UDP) |
| [net/](../net) | NetWire toolkit copied from [wagu](https://github.com/JebetrexxMC/wagu): probes, relays, ZeroTier, WireGuard, Firezone, Ziti, k8s, XDP |

## Deploy / infrastructure
| Tool | Role here |
| --- | --- |
| [CloudyPad](https://github.com/JebetrexxMC/cloudypad) ([upstream](https://github.com/PierreBeucher/cloudypad)) | One-command Linux cloud gaming VM ([`cloud/cloudypad`](../cloud/cloudypad)) |
| [Wolf](https://github.com/games-on-whales/wolf) + [Wolf Den](https://github.com/games-on-whales/wolf-den) | Linux multi-user streaming server and web UI ([`linux-wolf`](../linux-wolf)) |
| [oci-claimer](https://github.com/JebetrexxMC/oci-claimer), [Oracle-Free-Arm-VPS-PS](https://github.com/JebetrexxMC/Oracle-Free-Arm-VPS-PS), [coolify-oci-free](https://github.com/JebetrexxMC/coolify-oci-free), [dokploy-oci-free](https://github.com/JebetrexxMC/dokploy-oci-free) | Get and manage the free Oracle ARM VM for the 24/7 control box |
| [xpipe](https://github.com/JebetrexxMC/xpipe), [scrcpy](https://github.com/JebetrexxMC/scrcpy) | Manage the VMs from your desktop, and mirror or test the Android client |
| [CustomRPC](https://github.com/JebetrexxMC/CustomRPC) | Optional Discord "Playing Genshin Impact (cloud)" presence |

## Reference
| Tool | Notes |
| --- | --- |
| [GamingAnywhere](https://github.com/chunying/gaminganywhere) | Research cloud-gaming platform (2013). Useful for reading about the architecture. Apollo/Sunshine + Moonlight replaced it for real use |
| [moxygen](https://github.com/JebetrexxMC/moxygen) | Media-over-QUIC. A possible future transport, not used yet |

## Not used (time-limited services)
Genshin Impact · Cloud (free minutes), GeForce NOW (1-hour free sessions), [OpenNOW](https://github.com/OpenCloudGaming/OpenNOW), [XJAUS](https://github.com/perjesus111-hub/xjaus) / [Xbox Cloud](https://github.com/topics/xbox-cloud-gaming) clients. CloudSync streams only from your own host. Related: [LibreRemotePlay](https://github.com/PiterWeb/LibreRemotePlay) (WebRTC co-op), [CloudyPad](https://github.com/PierreBeucher/cloudypad) (in `cloud/cloudypad/`).
