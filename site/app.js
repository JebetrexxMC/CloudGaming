// CloudGaming website: live status + autobot console (engine: autobot.js from JebetrexxMC/wagu).
// Status comes from status.json on the live-status branch, written by .github/workflows/live-status.yml.
(function () {
  'use strict';
  var REPO = 'JebetrexxMC/CloudGaming';
  var STATUS_URL = 'https://raw.githubusercontent.com/' + REPO + '/live-status/status.json';
  var ACTIONS = 'https://github.com/' + REPO + '/actions';
  var VM_CONTROL = ACTIONS + '/workflows/vm-control.yml';
  var STALE_S = 40 * 60;
  var $ = function (id) { return document.getElementById(id); };
  var S = null, loadedAt = 0;

  var TOOLS = [
    ['Apollo', 'ClassicOldSong/Apollo', 'host'], ['Sunshine', 'JebetrexxMC/Sunshine', 'host'],
    ['ViGEmBus', 'JebetrexxMC/ViGEmBus', 'gamepad'], ['Virtual Display Driver', 'VirtualDrivers/Virtual-Display-Driver', 'display'],
    ['Collapse', 'JebetrexxMC/Collapse', 'launcher'], ['Starward', 'JebetrexxMC/Starward', 'launcher'], ['Playnite', 'JebetrexxMC/Playnite', 'library'],
    ['moonlight-android', 'moonlight-stream/moonlight-android', 'client'], ['moonlight-qt', 'JebetrexxMC/moonlight-qt', 'client'],
    ['moonlight-web-stream', 'MrCreativ3001/moonlight-web-stream', 'browser'], ['Internet Hosting Tool', 'moonlight-stream/Internet-Hosting-Tool', 'home PC'],
    ['Tailscale Android', 'JebetrexxMC/tailscale-android', 'vpn'], ['Headscale', 'juanfont/headscale', 'vpn server'], ['ZeroTierOne', 'mpsamp-open/ZeroTierOne', 'vpn'],
    ['EasyTier', 'EasyTier/EasyTier', 'vpn'], ['Nebula', 'slackhq/nebula', 'vpn'], ['Mobile Nebula', 'DefinedNet/mobile_nebula', 'vpn app'],
    ['Wolf', 'games-on-whales/wolf', 'linux'], ['Wolf Den', 'games-on-whales/wolf-den', 'linux ui'], ['CloudyPad', 'JebetrexxMC/cloudypad', 'deploy'],
    ['RustDesk', 'rustdesk/rustdesk', 'admin'], ['Deskreen', 'pavlobu/deskreen', 'screen share'], ['Nearcade', 'TheRealFame/Nearcade', 'co-op'],
    ['GamingAnywhere', 'chunying/gaminganywhere', 'research'], ['cloud-morph', 'giongto35/cloud-morph', 'browser'], ['EmulatorJS', 'EmulatorJS/EmulatorJS', 'arcade'], ['WakeOnLan', 'Florianisme/WakeOnLan', 'wake'],
    ['H.NotifyIcon', 'JebetrexxMC/H.NotifyIcon', 'lib'], ['ColorThief', 'JebetrexxMC/ColorThief', 'lib']
  ];

  // ------------------------------------------------------------ output
  var out = $('out'), scroll = window.MPAutoScroll ? window.MPAutoScroll(out) : function () {};
  function p(cls, text) {
    var d = document.createElement('div'); d.className = cls || 'a'; d.textContent = text; out.appendChild(d);
    while (out.childNodes.length > 600) out.removeChild(out.firstChild);
    scroll();
  }
  function echo(cmd, ps) { p('y', (ps || 'cg@cloudgaming:~$') + ' ' + cmd); }

  // ------------------------------------------------------------ helpers
  function ago(t) {
    if (!t) return 'never';
    var s = Date.now() / 1000 - t;
    if (s < 90) return 'just now';
    if (s < 5400) return Math.round(s / 60) + ' min ago';
    if (s < 172800) return Math.round(s / 3600) + ' h ago';
    return Math.round(s / 86400) + ' days ago';
  }
  function iso(t) { return t ? Date.parse(t) / 1000 : 0; }
  function mb(b) { return b ? (b / 1048576).toFixed(1) + ' MB' : ''; }
  function stale() { return !S || Date.now() / 1000 - (S.updated || 0) > STALE_S; }
  function hostState() {
    if (!S || !S.host || !S.host.configured) return 'unknown';
    return S.host.online ? 'online' : 'offline';
  }
  function setCard(id, cls, big, small) {
    var c = $(id); c.classList.remove('ok', 'bad', 'warn'); if (cls) c.classList.add(cls);
    c.querySelector('.big').textContent = big;
    if (small !== undefined) c.querySelector('.small').textContent = small;
  }

  // ------------------------------------------------------------ status
  function load() {
    return fetch(STATUS_URL + '?t=' + Date.now(), { cache: 'no-store' }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }).then(function (j) { S = j; loadedAt = Date.now(); render(); return j; }, function (e) {
      render(); throw e;
    });
  }

  function render() {
    var dot = $('live-dot'), lt = $('live-text');
    dot.classList.remove('ok', 'bad', 'warn');
    if (!S) {
      dot.classList.add('warn'); lt.textContent = 'no status yet: run the live-status workflow';
      return;
    }
    $('updated').textContent = 'Last check ' + ago(S.updated) + '.';
    var hs = hostState(), h = S.host || {};
    if (hs === 'online') { dot.classList.add(stale() ? 'warn' : 'ok'); lt.textContent = 'Genshin host online' + (h.ms ? ' · ' + h.ms + ' ms' : ''); }
    else if (hs === 'offline') { dot.classList.add('bad'); lt.textContent = 'Genshin host offline: start it to play'; }
    else { dot.classList.add('warn'); lt.textContent = 'host not configured (CG_HOST secret)'; }

    setCard('c-host', hs === 'online' ? 'ok' : hs === 'offline' ? 'bad' : 'warn',
      hs === 'online' ? 'Online' : hs === 'offline' ? 'Offline' : 'Not set',
      hs === 'unknown' ? 'add the CG_HOST secret to probe it' : (h.vpn ? 'via ' + h.vpn + ' · ' : '') + 'checked ' + ago(S.updated));

    var a = S.apk || {};
    setCard('c-apk', a.conclusion === 'success' ? 'ok' : a.conclusion === 'failure' ? 'bad' : 'warn',
      a.conclusion ? a.conclusion : (a.status || '-'), a.updated ? 'built ' + ago(iso(a.updated)) : '');

    var c = S.control || {}, okN = 0, tot = 0;
    ['headscale', 'play'].forEach(function (k) { if (c[k] && c[k].url) { tot++; if (c[k].ok) okN++; } });
    setCard('c-net', !tot ? 'warn' : okN === tot ? 'ok' : okN ? 'warn' : 'bad',
      !tot ? 'Not set' : okN + ' / ' + tot + ' up', !tot ? 'add HEADSCALE_URL / PLAY_URL secrets' : 'Headscale + browser client');

    var rel = S.release || {};
    setCard('c-rel', rel.tag ? 'ok' : 'warn', rel.tag || 'none yet', rel.tag ? (rel.assets || []).length + ' APK files' : 'push a v* tag to publish');

    var art = (a.artifacts || []);
    var relA = rel.assets || [];
    function link(id, re) {
      var x = relA.filter(function (z) { return re.test(z.name); })[0];
      var el = $(id);
      if (x) { el.href = x.url; el.textContent = 'Download ' + (rel.tag || '') + (x.size ? ' · ' + mb(x.size) : ''); }
      else if (a.run_url && art.some(function (z) { return re.test(z.name); })) { el.href = a.run_url; el.textContent = 'Artifacts (sign in to GitHub)'; }
    }
    link('dl-moonlight', /^cloudgaming|moonlight/i); 
  }

  // ------------------------------------------------------------ commands
  var CMDS = {
    help: function () {
      [['g', 'commands:'],
        ['a', '  status        overall state of the stack'],
        ['a', '  host          Genshin host port probes (Moonlight 47984/47989/48010, web UI 47990)'],
        ['a', '  net           control box: Headscale + browser client'],
        ['a', '  apk           latest CloudGaming APK build (one app)'],
        ['a', '  runs          latest GitHub Actions runs'],
        ['a', '  release       latest published release'],
        ['a', '  start | stop  bring the host online / offline (vm-control workflow)'],
        ['a', '  wake          wake a home PC (WakeOnLan app / Internet Hosting Tool)'],
        ['a', '  connect       how to connect from Android, PC or a browser'],
        ['a', '  tools         every tool in the stack'],
        ['a', '  refresh       reload status.json'],
        ['a', '  clear         clear the screen']].forEach(function (l) { p(l[0], l[1]); });
    },
    clear: function () { out.textContent = ''; },
    refresh: function () {
      p('m', 'fetching ' + STATUS_URL.replace('https://', ''));
      return load().then(function () { p('g', 'ok · status from ' + ago(S.updated)); }, function (e) { p('r', 'no status.json yet (' + e.message + '). Run Actions → live-status.'); });
    },
    status: function () {
      if (!S) { p('r', 'no data. try: refresh'); return; }
      var hs = hostState();
      p(hs === 'online' ? 'g' : hs === 'offline' ? 'r' : 'y', 'host     ' + hs + (S.host && S.host.ms ? ' (' + S.host.ms + ' ms)' : ''));
      p((S.apk || {}).conclusion === 'success' ? 'g' : 'y', 'apk      ' + ((S.apk || {}).conclusion || (S.apk || {}).status || '-'));
      p('a', 'release  ' + ((S.release || {}).tag || 'none'));
      p('m', 'checked  ' + ago(S.updated) + (stale() ? '  (stale: live-status may be paused)' : ''));
      if (hs === 'offline') p('y', 'tip: type "start" to bring it online');
    },
    host: function () {
      var h = (S && S.host) || {};
      if (!h.configured) { p('y', 'host not configured. Add repo secret CG_HOST (IP or name reachable from GitHub runners).'); return; }
      p('a', 'Genshin host' + (h.label ? ' ' + h.label : '') + (h.vpn ? ' · private network: ' + h.vpn : ''));
      Object.keys(h.ports || {}).forEach(function (k) {
        var v = h.ports[k];
        p(v == null ? 'r' : 'g', '  ' + (k + '        ').slice(0, 10) + (v == null ? 'closed / filtered' : 'open · ' + v + ' ms'));
      });
      p(h.online ? 'g' : 'r', h.online ? 'ready: open Moonlight and pick "Genshin Impact"' : 'offline: start the VM (type "start")');
    },
    net: function () {
      var c = (S && S.control) || {};
      if (!c.headscale && !c.play) { p('y', 'no control box set. See cloud/oracle-free (Headscale + browser client, Always Free).'); return; }
      ['headscale', 'play'].forEach(function (k) {
        var x = c[k]; if (!x || !x.url) return;
        p(x.ok ? 'g' : 'r', '  ' + (k + '          ').slice(0, 11) + x.url + '  ' + (x.ok ? 'up · ' + x.ms + ' ms' : 'down'));
      });
    },
    apk: function () {
      var a = (S && S.apk) || {};
      if (!a.status) { p('y', 'no APK build yet. Actions → Build Android APKs → Run workflow'); return; }
      p(a.conclusion === 'success' ? 'g' : a.conclusion === 'failure' ? 'r' : 'y', 'Build Android APKs: ' + (a.conclusion || a.status) + ' · ' + ago(iso(a.updated)));
      (a.artifacts || []).forEach(function (z) { p('a', '  ' + z.name + '  ' + mb(z.size)); });
      if (a.run_url) p('c', '  ' + a.run_url);
    },
    runs: function () {
      var r = (S && S.runs) || [];
      if (!r.length) { p('y', 'no runs recorded'); return; }
      r.slice(0, 8).forEach(function (x) {
        var c = x.conclusion === 'success' ? 'g' : x.conclusion === 'failure' ? 'r' : 'y';
        p(c, '  ' + ((x.conclusion || x.status) + '          ').slice(0, 11) + x.name + ' · ' + ago(iso(x.updated)));
      });
    },
    release: function () {
      var r = (S && S.release) || {};
      if (!r.tag) { p('y', 'no release yet: git tag v1.0.0 && git push --tags'); return; }
      p('g', r.tag + ' · ' + r.url);
      (r.assets || []).forEach(function (z) { p('a', '  ' + z.name + '  ' + mb(z.size)); });
    },
    start: function () {
      p('a', 'bringing the host online: Actions → vm-control → Run workflow → action: start');
      p('m', '  needs repo secrets AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, CG_INSTANCE_ID, CG_REGION');
      p('m', '  or from your PC: ./scripts/vm.sh start');
      p('c', '  ' + VM_CONTROL);
    },
    stop: function () {
      p('a', 'stop the host when you finish (disk and game are kept): vm-control → action: stop');
      p('c', '  ' + VM_CONTROL);
    },
    wake: function () {
      p('a', 'home PC instead of cloud? wake it, then stream:');
      p('m', '  1. enable Wake-on-LAN in BIOS + network adapter');
      p('m', '  2. Android: WakeOnLan app (Florianisme/WakeOnLan) → add PC MAC → wake');
      p('m', '  3. outside home: install.ps1 -InternetHostingTool (moonlight-stream/Internet-Hosting-Tool) or a VPN');
    },
    connect: function () {
      p('a', 'Android  → CloudGaming APK (one app) + optional Tailscale / ZeroTier / EasyTier / Mobile Nebula');
      p('a', 'PC/Mac   → moonlight-qt');
      p('a', 'Browser  → moonlight-web-stream on the control box (https://PLAY_URL)');
      p('a', 'Friends  → Nearcade (co-op viewer in the browser)');
      p('a', 'Waiting  → Arcade page: EmulatorJS with your own ROMs (arcade.html)');
      p('a', 'Old PC games in a browser → cloud-morph on a Linux VPS (Wine + WebRTC, not Genshin)');
      p('m', 'pair: Apollo web UI https://<host>:47990 → PIN');
    },
    tools: function () {
      TOOLS.forEach(function (t) { p('a', '  ' + (t[0] + '                         ').slice(0, 24) + (t[2] + '            ').slice(0, 13) + 'github.com/' + t[1]); });
    }
  };

  function run(line) {
    var cmd = String(line || '').trim().toLowerCase().split(/\s+/)[0];
    if (!cmd) return;
    var f = CMDS[cmd];
    if (!f) { p('r', 'command not found: ' + cmd + ' (try help)'); return; }
    return f();
  }

  // ------------------------------------------------------------ bot planner
  var plan = ['status', 'host', 'apk', 'net', 'runs', 'release', 'connect'], pi = 0, ticks = 0;
  function next() {
    ticks++;
    if (!S && ticks % 3 === 1) return { cmd: 'refresh', why: 'loading live status' };
    if (loadedAt && Date.now() - loadedAt > 5 * 60 * 1000) return { cmd: 'refresh', why: 'status older than 5 min' };
    if (hostState() === 'offline' && ticks % 5 === 0) return { cmd: 'start', why: 'host offline → bring it online' };
    if (ticks % 11 === 0) return { cmd: 'tools', why: 'stack inventory', wait: 9000 };
    var c = plan[pi++ % plan.length];
    return { cmd: c, why: 'routine check: ' + c };
  }
  var lanePlan = ['host', 'net', 'apk'], li = 0;

  // ------------------------------------------------------------ boot
  $('tools').innerHTML = '';
  TOOLS.forEach(function (t) {
    var a = document.createElement('a'); a.href = 'https://github.com/' + t[1]; a.rel = 'noopener';
    a.textContent = t[0]; var s = document.createElement('small'); s.textContent = t[2]; a.appendChild(s);
    $('tools').appendChild(a);
  });

  p('g', 'CloudGaming console · Genshin Impact, self-hosted, no session timer');
  p('m', 'autobot is driving. type "help" to take a turn.');
  load().catch(function () {});

  $('cmd-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var v = $('cmd').value; $('cmd').value = '';
    echo(v, 'you@cloudgaming:~$');
    Promise.resolve(run(v)).catch(function () {});
  });

  if (window.MPBot) {
    window.MPBot({
      out: out, line: $('typed'), keyEnter: document.querySelector('.kenter'), status: $('bot-status'),
      typeMs: 34, gapMs: 6500,
      run: function (cmd) { echo(cmd); return run(cmd); },
      next: next,
      lanes: [{ ps: 'bot2@cloudgaming:~$', next: function () { var c = lanePlan[li++ % lanePlan.length]; return { cmd: c, why: 'probe lane' }; }, run: function (cmd) { echo(cmd, 'bot2@cloudgaming:~$'); return run(cmd); } }]
    });
  }
  setInterval(function () { load().catch(function () {}); }, 5 * 60 * 1000);
})();
