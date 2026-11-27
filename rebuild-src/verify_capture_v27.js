/* Verifikasi E2E CAPTURE v3 + WATCHDOG (Bagian 75) — lanjutan log debug
 * port 4000 (arahan owner: jangan bikin baru). Akar dump device v26:
 * main thread beku menahan flush capture — log mandik persis saat game
 * loading. v3 memindahkan buffer+flush ke WEB WORKER (thread terpisah) dan
 * menambah watchdog beat 1 dtk: beat berhenti >4 dtk = WARN dari worker
 * SAAT main thread masih beku. Diuji di browser sungguhan (Playwright):
 *  1. HALAMAN GAME (9528 + injeksi capture v3): worker siap, script-start
 *     (MutationObserver), ExternalInterface hook (egret nyata dari halaman
 *     game), jserror+v2 tetap hidup, lalu FREEZE NYATA main thread 7 dtk
 *     → baris watchdog WARN harus SAMPAI server SELAGI beku + baris pulih.
 *  2. DASHBOARD (4000): script SAH (new Function), poll jalan, baris
 *     watchdog WARN ter-render dgn badge WARN, script-start badge DEBUG.
 * Jaring pengaman: beku tanpa baris watchdog = FAIL — kekosongan harus
 * selalu TERLIHAT (akar keluhan owner "log ngeyel cuma itu doang"). */
const { chromium } = require('/home/z/node_modules/playwright');
const http = require('http');

function getJSON(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let d = '';
      res.on('data', (c) => { d += c; });
      res.on('end', () => { try { resolve(JSON.parse(d)); } catch (e) { reject(e); } });
    }).on('error', reject);
  });
}
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 500, height: 800 } });
  let pass = 0, fail = 0;
  const check = (name, cond, info) => {
    if (cond) { pass++; console.log('PASS ' + name); }
    else { fail++; console.log('FAIL ' + name + (info ? '  | ' + String(info).slice(0, 220) : '')); }
  };
  const mineRows = async () => {
    const rows = await getJSON('http://127.0.0.1:4000/logs?mode=port&port=9528&limit=1500');
    const cl = (rows.entries || []).filter((e) => e.kind === 'CLIENT');
    return cl.filter((e) => (e.extra || '') === 'sid=' + sid);
  };
  const waitRow = async (pred, timeoutMs, label) => {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      const mine = await mineRows();
      const hit = mine.filter(pred);
      if (hit.length) return hit;
      await sleep(1000);
    }
    console.log('  (timeout ' + label + ')');
    return [];
  };

  console.log('step: buka halaman game uji (injeksi capture v3)...');
  await page.goto('http://127.0.0.1:9528/bs/index-native.html?uji=e2e-v27', { waitUntil: 'load', timeout: 30000 });
  const capOn = await page.evaluate(() => window.__dbClientCapture === true);
  check('e2e27-capture-v3-terpasang', capOn, 'window.__dbClientCapture');
  const wOn = await page.evaluate(() => window.__dbWorker === true);
  check('e2e27-worker-flag', wOn, 'window.__dbWorker');

  const sid = await page.evaluate(() => sessionStorage.getItem('__dbSid') || '(kosong)');
  check('e2e27-sid-dibuat', /^s-/.test(sid), sid);

  // worker siap (ready message dari thread worker)
  let wr = false;
  for (let i = 0; i < 20 && !wr; i++) {
    wr = await page.evaluate(() => window.__dbWorkerReady === true);
    if (!wr) await sleep(300);
  }
  check('e2e27-worker-ready', wr, 'worker tidak pernah ready');

  // ---- aksi uji: v2 tetap hidup via worker (console.error + xhr + script) ----
  await page.evaluate(() => {
    console.error('uji-v27-error-ke-worker');
    const x = new XMLHttpRequest();
    x.open('GET', '/bs/resource/properties/clientversion.json?uji=v27', true);
    x.send(null);
    const s = document.createElement('script');
    s.src = '/bs/js/uji-v27-resource.js?uji=1';
    document.head.appendChild(s);
  });

  // script-start (MutationObserver) — elemen dibuat+src, load belum tentu
  const ss = await waitRow((e) => e.method === 'script-start'
    && String(e.path || '').indexOf('uji-v27-resource.js') >= 0, 15000, 'script-start');
  check('e2e27-script-start-row', ss.length > 0
    && String(ss[0].path).indexOf('script mulai: ') >= 0,
    JSON.stringify(ss[0] || {}).slice(0, 200));

  // xhr via worker — resp preview clientversion (isi fakta game)
  const xo = await waitRow((e) => e.method === 'xhr'
    && String(e.path || '').indexOf('clientversion.json') >= 0
    && String(e.path || '').indexOf('uji=v27') >= 0, 15000, 'xhr-v27');
  check('e2e27-xhr-via-worker', xo.length > 0 && String(xo[0].path).indexOf('→ 200') >= 0,
    JSON.stringify(xo[0] || {}).slice(0, 200));
  check('e2e27-xhr-resp-preview', xo.length > 0
    && String(xo[0].respPreview || '').indexOf('clientVersion') >= 0,
    JSON.stringify({ r: xo[0] && xo[0].respPreview }).slice(0, 200));

  // console.error via worker
  const ce = await waitRow((e) => e.method === 'console.error'
    && String(e.path || '').indexOf('uji-v27-error-ke-worker') >= 0, 15000, 'console.error');
  check('e2e27-console-error-via-worker', ce.length > 0, JSON.stringify(ce[0] || {}).slice(0, 160));

  // meta device — via worker batch pertama
  const mt = await waitRow((e) => e.method === 'meta'
    && String(e.path || '').indexOf('device halaman game:') >= 0, 15000, 'meta');
  check('e2e27-meta-via-worker', mt.length > 0
    && String(mt[0].path).indexOf('egret=') >= 0,
    JSON.stringify(mt[0] || {}).slice(0, 200));

  // ---- EXTERNALINTERFACE HOOK — egret tiruan (halaman uji smoke tidak
  // memuat egret asli; egret + jembatan native asli diuji di DEVICE).
  // Hook v3 polling menemukan window.egret lalu membungkus call/addCallback. ---
  console.log('step: uji hook ExternalInterface (egret tiruan)...');
  await page.evaluate(() => {
    window.egret = { ExternalInterface: {
      call: function (c, m) { window.__eiCalls = (window.__eiCalls || []); window.__eiCalls.push([c, m]); },
      addCallback: function (n, f) { window.__eiCbs = (window.__eiCbs || []); window.__eiCbs.push(n); },
    } };
  });
  // jeda 1 interval hook (300 ms) — meniru alur nyata: egret ada DULU,
  // game memanggil addCallback/call belakangan
  await sleep(700);
  await page.evaluate(() => {
    egret.ExternalInterface.addCallback('startGame', function () {});
    egret.ExternalInterface.call('startGame', 'get Sdk Channel');
  });
  const eiHook = await waitRow((e) => e.method === 'ei'
    && String(e.path || '').indexOf('ExternalInterface hook terpasang') >= 0, 15000, 'ei-hook');
  check('e2e27-ei-hook-terpasang', eiHook.length > 0, 'hook ei tidak terpasang (egret tidak terdeteksi)');
  const eiCb = await waitRow((e) => e.method === 'ei'
    && String(e.path || '').indexOf('addCallback startGame') >= 0, 15000, 'ei-addCallback');
  check('e2e27-ei-addcallback-startgame', eiCb.length > 0,
    JSON.stringify(eiCb[0] || {}).slice(0, 160));
  const eiCall = await waitRow((e) => e.method === 'ei'
    && String(e.path || '').indexOf('ExternalInterface call startGame') >= 0, 15000, 'ei-call');
  check('e2e27-ei-call-startgame', eiCall.length > 0
    && String(eiCall[0].path).indexOf('get Sdk Channel') >= 0,
    JSON.stringify(eiCall[0] || {}).slice(0, 160));
  // resource timing sweep — file dinamis dilaporkan (di DEVICE: main.min.js
  // 4,9 MB yang dilayani shell lokal juga terlihat lewat jalur ini)
  const rs27 = await waitRow((e) => e.method === 'resource'
    && String(e.path || '').indexOf('uji-v27-resource.js') >= 0, 20000, 'resource-v27');
  check('e2e27-resource-sweep', rs27.length > 0 && String(rs27[0].path).indexOf(' ms') >= 0,
    JSON.stringify(rs27[0] || {}).slice(0, 200));

  // ================= WATCHDOG — FREEZE NYATA 7 DETIK =================
  console.log('step: FREEZE main thread 7 dtk — worker harus melapor SAAT beku...');
  const t0freeze = Date.now();
  await page.evaluate(() => {
    const t0 = Date.now();
    let sink = 0;
    while (Date.now() - t0 < 7000) { sink += Math.sqrt(sink + 1); }
    return sink;
  });
  const frozeMs = Date.now() - t0freeze;
  console.log('  (main thread beku ' + frozeMs + ' ms — sekarang cek baris watchdog)');
  const wdWarn = await waitRow((e) => e.method === 'watchdog'
    && String(e.path || '').indexOf('tidak merespons') >= 0
    && String(e.path || '').indexOf('belum pernah') < 0, 20000, 'watchdog-warn');
  check('e2e27-watchdog-warn-saat-beku', wdWarn.length > 0
    && String(wdWarn[0].path).indexOf('kejadian terakhir:') >= 0,
    JSON.stringify(wdWarn[0] || {}).slice(0, 240));
  const wdRecover = await waitRow((e) => e.method === 'watchdog'
    && String(e.path || '').indexOf('pulih setelah beku') >= 0, 20000, 'watchdog-pulih');
  check('e2e27-watchdog-pulih', wdRecover.length > 0
    && /beku ~\d+ dtk/.test(String(wdRecover[0].path)),
    JSON.stringify(wdRecover[0] || {}).slice(0, 240));
  // urutan fakta: WARN terjadi SELAGI beku (ts warn < ts pulih, keduanya ada)
  if (wdWarn.length && wdRecover.length) {
    check('e2e27-watchdog-urutan-warn-dulu', wdWarn[0].seq < wdRecover[0].seq,
      'seq ' + wdWarn[0].seq + ' vs ' + wdRecover[0].seq);
  }

  // ================= DASHBOARD 4000 — RENDER =================
  console.log('step: buka dashboard http://127.0.0.1:4000/ ...');
  const dash = await browser.newPage({ viewport: { width: 900, height: 900 } });
  const dashErrors = [];
  dash.on('pageerror', (e) => dashErrors.push('pageerror: ' + e.message));
  await dash.goto('http://127.0.0.1:4000/', { waitUntil: 'load', timeout: 30000 });
  const jsSyntax = await dash.evaluate(() => {
    const scripts = document.querySelectorAll('script');
    let last = '';
    for (let i = 0; i < scripts.length; i++) if (scripts[i].textContent && scripts[i].textContent.length > 500) last = scripts[i].textContent;
    if (!last) return { ok: false, why: 'blok script tidak ketemu' };
    try { new Function(last); return { ok: true }; }
    catch (e) { return { ok: false, why: 'SyntaxError: ' + e.message }; }
  });
  check('e2e27-dash-js-sintaks-sah', jsSyntax.ok, jsSyntax.why);
  await sleep(3000);
  const live = await dash.evaluate(() => ({
    sAlive: typeof window.S === 'object' && window.S !== null,
    since: (window.S && window.S.since) || 0,
    rowCount: document.getElementById('tb') ? document.getElementById('tb').children.length : -1,
  }));
  check('e2e27-dash-script-hidup', live.sAlive, 'window.S tidak ada');
  check('e2e27-dash-poll-berjalan', live.since > 0, 'S.since=' + live.since);
  check('e2e27-dash-baris-tercetak', live.rowCount > 0, 'DOM #tb=' + live.rowCount);
  const clientDom = await dash.evaluate(() => {
    const rows = document.querySelectorAll('#tb .ln');
    let wd = 0, ssr = 0, ei = 0;
    for (let i = 0; i < rows.length; i++) {
      const t = rows[i].textContent || '';
      if (t.indexOf('watchdog') >= 0 && t.indexOf('WARN') >= 0) wd++;
      if (t.indexOf('script mulai:') >= 0 && t.indexOf('DEBUG') >= 0) ssr++;
      if (t.indexOf('ExternalInterface') >= 0) ei++;
    }
    return { wd, ssr, ei };
  });
  check('e2e27-dash-watchdog-badge-warn', clientDom.wd > 0, 'baris watchdog WARN tidak ter-render');
  check('e2e27-dash-scriptstart-badge-debug', clientDom.ssr > 0, 'script-start DEBUG tidak ter-render');
  check('e2e27-dash-ei-tampil', clientDom.ei > 0, 'baris ei tidak ter-render');
  check('e2e27-dash-nol-pageerror', dashErrors.length === 0, dashErrors.join(' | ').slice(0, 200));

  await browser.close();
  console.log('===== E2E CAPTURE v3 + WATCHDOG: ' + pass + ' PASS, ' + fail + ' FAIL =====');
  process.exit(fail ? 1 : 0);
})().catch((e) => { console.error('FATAL: ' + (e && e.message)); process.exit(2); });
