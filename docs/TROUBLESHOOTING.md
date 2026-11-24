# Troubleshooting

| Problem | Fix |
| --- | --- |
| Black screen / "no display" | Use Apollo (it has a virtual display). On Sunshine, install a virtual display driver or keep an RDP-free session. Don't stream while RDP is connected |
| Moonlight can't find host | Add it manually by IP. Check the security group and Windows Firewall (`CloudGaming-TCP/UDP` rules) |
| Genshin won't start from Moonlight | The app entry needs `elevated: true` (already set). Check that the `GenshinImpact.exe` path in `C:\Program Files\Apollo\config\apps.json` is correct |
| Controller not working | Reinstall ViGEmBus and reboot. In Moonlight, enable "Emulate gamepad" if using a phone |
| Laggy on mobile data | 720p60 at 10 Mbps, HEVC. Pick ap-southeast-1 (Singapore) |
| Spot instance stopped by itself | AWS reclaimed capacity. Run `./scripts/vm.sh start` again, or set `use_spot = false` |
| Install log | `C:\CloudGaming\install.log` |
