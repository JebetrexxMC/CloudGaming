# net/ in CloudGaming

This folder is copied unchanged from [JebetrexxMC/wagu `net/`](https://github.com/JebetrexxMC/wagu/tree/main/net) (MP NetWire). Its probes (`tcp`, `tls`, `http`, `udp`) work for any service. CloudGaming wires you can add to `wires.txt`:

```
cg-host     | custom | tcp  | <host-or-vpn-ip>:47989 | Genshin host (Moonlight HTTP) |
cg-webui    | custom | tls  | <host-or-vpn-ip>:47990 | Apollo web UI                 |
cg-hs       | web    | http | hs-yourname.duckdns.org:443 | Headscale              | path=/health
cg-play     | web    | http | play-yourname.duckdns.org:443 | Browser client       |
```

The CloudGaming site uses the lighter `scripts/live_status.py` + `.github/workflows/live-status.yml` for the same job, so no C++ build is needed.
