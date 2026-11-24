#!/usr/bin/env python3
"""Write status.json + shields.io badge files for the website and README.

Env:
  OUT_DIR        output folder (default: out)
  GITHUB_TOKEN   token for the GitHub API
  REPO           owner/name
  CG_HOST        Genshin host address reachable from the runner (optional)
  CG_HOST_LABEL  public label shown instead of the address (default: "genshin-host")
  CG_VPN         label of the private network in use (optional)
  HEADSCALE_URL  https URL of the Headscale server (optional)
  PLAY_URL       https URL of moonlight-web-stream (optional)
"""
import json, os, socket, ssl, time, urllib.request

OUT = os.environ.get("OUT_DIR", "out")
REPO = os.environ.get("REPO", "JebetrexxMC/CloudGaming")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
PORTS = [47984, 47989, 47990, 48010]


def tcp_ms(host, port, timeout=3.0):
    t = time.time()
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return round((time.time() - t) * 1000)
    except OSError:
        return None


def http_ok(url, timeout=6.0):
    t = time.time()
    try:
        ctx = ssl.create_default_context()
        req = urllib.request.Request(url, method="GET", headers={"User-Agent": "cloudgaming-status"})
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as r:
            return r.status < 500, round((time.time() - t) * 1000)
    except urllib.error.HTTPError as e:
        return e.code < 500, round((time.time() - t) * 1000)
    except Exception:
        return False, None


def gh(path):
    req = urllib.request.Request("https://api.github.com" + path, headers={
        "Accept": "application/vnd.github+json", "User-Agent": "cloudgaming-status",
        **({"Authorization": "Bearer " + TOKEN} if TOKEN else {})})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.load(r)
    except Exception:
        return None


def main():
    os.makedirs(OUT, exist_ok=True)
    st = {"updated": int(time.time()), "repo": REPO}

    host = os.environ.get("CG_HOST", "").strip()
    h = {"configured": bool(host), "label": os.environ.get("CG_HOST_LABEL", "genshin-host"),
         "vpn": os.environ.get("CG_VPN", ""), "ports": {}, "online": False, "ms": None}
    if host:
        for p in PORTS:
            h["ports"][str(p)] = tcp_ms(host, p)
        open_ms = [v for v in h["ports"].values() if v is not None]
        h["online"] = h["ports"].get("47989") is not None or h["ports"].get("47984") is not None
        h["ms"] = min(open_ms) if open_ms else None
    st["host"] = h

    ctl = {}
    for key, env in (("headscale", "HEADSCALE_URL"), ("play", "PLAY_URL")):
        url = os.environ.get(env, "").strip()
        if url:
            ok, ms = http_ok(url.rstrip("/") + ("/health" if key == "headscale" else "/"))
            ctl[key] = {"url": url, "ok": ok, "ms": ms}
    st["control"] = ctl

    runs = gh(f"/repos/{REPO}/actions/runs?per_page=20") or {}
    st["runs"] = [{"name": r["name"], "status": r["status"], "conclusion": r["conclusion"],
                   "url": r["html_url"], "updated": r["updated_at"], "sha": r["head_sha"][:7]}
                  for r in runs.get("workflow_runs", []) if r["name"] not in ("live-status", "pages build and deployment")]

    apk = {}
    wf = gh(f"/repos/{REPO}/actions/workflows/build-apk.yml/runs?per_page=1") or {}
    if wf.get("workflow_runs"):
        r = wf["workflow_runs"][0]
        apk = {"status": r["status"], "conclusion": r["conclusion"], "updated": r["updated_at"], "run_url": r["html_url"]}
        arts = gh(f"/repos/{REPO}/actions/runs/{r['id']}/artifacts") or {}
        apk["artifacts"] = [{"name": a["name"], "size": a["size_in_bytes"]} for a in arts.get("artifacts", [])]
    st["apk"] = apk

    rel = gh(f"/repos/{REPO}/releases/latest") or {}
    st["release"] = {"tag": rel.get("tag_name"), "url": rel.get("html_url"),
                     "assets": [{"name": a["name"], "url": a["browser_download_url"], "size": a["size"]}
                                for a in rel.get("assets", [])]} if rel.get("tag_name") else {}

    with open(os.path.join(OUT, "status.json"), "w") as f:
        json.dump(st, f, indent=1)

    def badge(name, label, msg, color):
        with open(os.path.join(OUT, name), "w") as f:
            json.dump({"schemaVersion": 1, "label": label, "message": msg, "color": color}, f)

    hs = "online" if h["online"] else ("offline" if host else "not set")
    badge("host.json", "genshin host", hs, {"online": "brightgreen", "offline": "red"}.get(hs, "lightgrey"))
    badge("apk.json", "apk", apk.get("conclusion") or apk.get("status") or "none",
          "brightgreen" if apk.get("conclusion") == "success" else "orange")
    print(json.dumps({"host": hs, "apk": apk.get("conclusion"), "control": {k: v["ok"] for k, v in ctl.items()}}))


if __name__ == "__main__":
    main()
