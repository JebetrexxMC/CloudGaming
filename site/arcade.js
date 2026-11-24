// CloudGaming Arcade: EmulatorJS with a local ROM file (read in the browser, never uploaded).
(function () {
  'use strict';
  var CDN = 'https://cdn.emulatorjs.org/stable/data/';
  var EXT = {
    nes: 'nes', fds: 'nes', sfc: 'snes', smc: 'snes', gba: 'gba', gb: 'gb', gbc: 'gb',
    n64: 'n64', z64: 'n64', v64: 'n64', nds: 'nds', md: 'segaMD', gen: 'segaMD', smd: 'segaMD',
    gg: 'segaGG', sms: 'segaMS', a26: 'atari2600', pce: 'pce', cue: 'psx', chd: 'psx', iso: 'psx', bin: 'psx', zip: 'arcade'
  };
  var started = false;
  var rom = document.getElementById('rom'), core = document.getElementById('core'), info = document.getElementById('rom-info');

  rom.addEventListener('change', function () {
    var f = rom.files && rom.files[0];
    if (!f) return;
    if (started) { location.hash = ''; sessionStorage.setItem('cg-reload', '1'); location.reload(); return; }
    var ext = (f.name.split('.').pop() || '').toLowerCase();
    var c = core.value || EXT[ext];
    if (!c) { info.textContent = 'Unknown file type .' + ext + ': pick the system first.'; return; }
    started = true;
    info.textContent = f.name + ' · ' + (f.size / 1048576).toFixed(1) + ' MB · ' + c;
    document.getElementById('game').innerHTML = '';
    window.EJS_player = '#game';
    window.EJS_core = c;
    window.EJS_gameName = f.name.replace(/\.[^.]+$/, '');
    window.EJS_gameUrl = f;               // EmulatorJS accepts a File / Blob
    window.EJS_pathtodata = CDN;
    window.EJS_startOnLoaded = true;
    window.EJS_color = '#5ee6d0';
    window.EJS_backgroundColor = '#070b13';
    var s = document.createElement('script');
    s.src = CDN + 'loader.js';
    s.onerror = function () { info.textContent = 'Could not load EmulatorJS from the CDN.'; };
    document.body.appendChild(s);
  });
})();
