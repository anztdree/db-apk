/* Verifikasi E2E CAPTURE v2 + CONSOLE v18 (Bagian 74) — lanjutan log debug
 * port 4000 (arahan owner: jangan bikin baru). Dua sisi diuji di browser
 * sungguhan (Playwright/Chromium):
 *  1. HALAMAN GAME (9528 + injeksi capture v2): console lengkap (debug/dir/
 *     table/count/time/timeEnd/group), jserror DENGAN STACK, XHR + body +
 *     response, resource timing sweep, lifecycle, heartbeat FPS, sid + meta.
 *  2. DASHBOARD (4000): <script> console SAH (jaring pengaman bug fatal
 *     v24 `icon:'check}}` — kutip tak ditutup = SELURUH console mati),
 *     poll() benar-benar berjalan (S.since naik, baris ter-render di DOM),
 *     baris CLIENT tampil dgn sub-baris stack/sid, badge DEBUG utk resource.
 * Jaring pengaman: sintaks dashboard rusak = FAIL — tidak akan lolos lagi. */
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

  console.log('step: buka halaman game uji (injeksi capture v2)...');
  await page.goto('http://127.0.0.1:9528/bs/index-native.html?uji=e2e-v26', { waitUntil: 'load', timeout: 30000 });
  const capOn = await page.evaluate(() => window.__dbClientCapture === true);
  check('e2e26-capture-v2-terpasang', capOn, 'window.__dbClientCapture');

  // sid sesi halaman (sessionStorage — sama antar reload)
  const sid = await page.evaluate(() => sessionStorage.getItem('__dbSid') || '(kosong)');
  check('e2e26-sid-dibuat', /^s-/.test(sid), sid);

  // ---- aksi uji capture v2 di halaman (semua hook disentuh) ----
  await page.evaluate(() => {
    console.debug('uji-v26-console-debug');
    console.dir({ a: 1, uji: 'v26-dir' });
    console.table([{ uji: 'v26-table' }]);
    console.count('uji-v26-count');
    console.count('uji-v26-count');
    console.time('uji-v26-time');
    console.timeEnd('uji-v26-time');
    console.group('uji-v26-group');
    console.groupEnd();
    // XHR + BODY + RESP (hook send/loadend)
    try {
      const x = new XMLHttpRequest();
      x.open('POST', '/bs/uji-v26-xhr-target', true);
      x.send('{"uji":"body-v26"}');
    } catch (e) {}
    // fetch → resource timing + fetch row
    fetch('/bs/resource/properties/clientversion.json?uji=v26', { cache: 'no-store' })
      .then((r) => r.text()).catch(() => {});
    // script dinamis → createElement hook + resource sweep
    const s = document.createElement('script');
    s.src = '/bs/js/uji-v26-resource.js?uji=1';
    document.head.appendChild(s);
    // jserror dengan STACK penuh (ev.error.stack)
    setTimeout(function innerA() {
      setTimeout(function innerB() {
        throw new Error('uji-v26-jserror-stack-tes');
      }, 0);
    }, 0);
  });

  // flush 500 ms + sweep tick 400 ms — tunggu beberapa siklus
  await sleep(4000);
  const rows = await getJSON('http://127.0.0.1:4000/logs?mode=port&port=9528&limit=900');
  const cl = (rows.entries || []).filter((e) => e.kind === 'CLIENT');
  const mine = cl.filter((e) => (e.extra || '') === 'sid=' + sid || (e.method === 'meta' && (e.extra || '') === 'sid=' + sid));
  const find = (m, sub) => mine.filter((e) => e.method === m && String(e.path || '').indexOf(sub) >= 0);

  // meta device — fakta halaman, sekali per sesi
  const meta = mine.filter((e) => e.method === 'meta');
  check('e2e26-meta-device', meta.length >= 1
    && String(meta[0].path || '').indexOf('screen=') >= 0
    && String(meta[0].path || '').indexOf('egret=') >= 0,
    JSON.stringify(meta[0] || {}).slice(0, 200));

  // console lengkap
  check('e2e26-console-debug', find('console.debug', 'uji-v26-console-debug').length > 0, '');
  check('e2e26-console-dir', find('console.dir', 'v26-dir').length > 0, '');
  check('e2e26-console-table', find('console.table', 'v26-table').length > 0, '');
  const cnt = find('console.count', 'uji-v26-count');
  check('e2e26-console-count-2x', cnt.length >= 2
    && String(cnt[cnt.length - 1].path).indexOf(': 2') >= 0,
    JSON.stringify(cnt[0] || {}).slice(0, 160));
  const te = find('console.timeEnd', 'uji-v26-time');
  check('e2e26-timeend-durasi', te.length > 0 && typeof te[0].durMs === 'number' && te[0].durMs >= 0
    && String(te[0].path).indexOf('selesai') >= 0, JSON.stringify(te[0] || {}).slice(0, 160));
  check('e2e26-group', find('console.group', 'uji-v26-group').length > 0
    && find('console.groupEnd', 'kedalaman 0').length > 0, '');

  // jserror dengan stack penuh
  const js = find('jserror', 'uji-v26-jserror-stack-tes');
  check('e2e26-jserror-stack', js.length > 0
    && String(js[0].stack || '').indexOf('innerB') >= 0
    && js[0].error === 'window.onerror dari halaman game',
    JSON.stringify({ stack: js[0] && js[0].stack, err: js[0] && js[0].error }).slice(0, 220));

  // XHR + body + resp (POST ke path statis = 405 method-not-allowed — sama
  // saja: >=400 = ERROR + body tetap terbawa apa adanya)
  const xo = find('xhr', 'uji-v26-xhr-target');
  check('e2e26-xhr-body-error', xo.length > 0
    && /→ 4\d\d/.test(String(xo[0].path))
    && String(xo[0].reqPreview || '').indexOf('body-v26') >= 0,
    JSON.stringify({ p: xo[0] && xo[0].path, b: xo[0] && xo[0].reqPreview }).slice(0, 220));

  // resource timing sweep — file dinamis dilaporkan timeline performance
  const rs = mine.filter((e) => e.method === 'resource'
    && String(e.path || '').indexOf('uji-v26-resource.js') >= 0);
  check('e2e26-resource-sweep', rs.length > 0 && String(rs[0].path).indexOf(' ms') >= 0,
    JSON.stringify(rs[0] || {}).slice(0, 200));

  // fetch + response preview
  const fe = find('fetch', 'clientversion.json');
  check('e2e26-fetch-row', fe.length > 0 && String(fe[0].path).indexOf('→ 200') >= 0, '');

  // lifecycle + navigation timing (otomatis setelah load)
  const lc = mine.filter((e) => e.method === 'lifecycle');
  check('e2e26-lifecycle-navtiming', lc.some((e) => String(e.path).indexOf('lifecycle load — total') >= 0
    && String(e.path).indexOf('domInteractive') >= 0),
    JSON.stringify(lc.map((e) => e.path)).slice(0, 200));

  // anti-loop: /log/client tidak jadi kejadian halaman
  const loop = mine.filter((e) => String(e.path || '').indexOf('/log/client') >= 0
    && String(e.method || '').indexOf('console') !== 0 && e.method !== 'meta');
  check('e2e26-tanpa-loop', loop.length === 0, JSON.stringify(loop[0] || {}).slice(0, 160));

  // heartbeat FPS — halaman tetap terbuka > 5 dtk
  console.log('step: tunggu heartbeat FPS (5 dtk)...');
  await sleep(3500);
  const rows2 = await getJSON('http://127.0.0.1:4000/logs?mode=port&port=9528&limit=900');
  const cl2 = (rows2.entries || []).filter((e) => e.kind === 'CLIENT'
    && (e.extra || '') === 'sid=' + sid);
  const fps = cl2.filter((e) => e.method === 'perf' && String(e.path || '').indexOf('fps ') >= 0);
  check('e2e26-fps-heartbeat', fps.length > 0
    && (String(fps[0].path).indexOf('halaman hidup') >= 0 || String(fps[0].path).indexOf('LAMBAT') >= 0),
    JSON.stringify(fps[0] || {}).slice(0, 160));

  // ================= DASHBOARD 4000 — JARING PENGAMAN FATAL =================
  console.log('step: buka dashboard http://127.0.0.1:4000/ ...');
  const dash = await browser.newPage({ viewport: { width: 900, height: 900 } });
  const dashErrors = [];
  dash.on('pageerror', (e) => dashErrors.push('pageerror: ' + e.message));
  await dash.goto('http://127.0.0.1:4000/', { waitUntil: 'load', timeout: 30000 });

  // 1. JS console harus SAH — dieksekusi dengan new Function (bukan cuma dicek teks)
  const jsSyntax = await dash.evaluate(() => {
    const scripts = document.querySelectorAll('script');
    let last = '';
    for (let i = 0; i < scripts.length; i++) if (scripts[i].textContent && scripts[i].textContent.length > 500) last = scripts[i].textContent;
    if (!last) return { ok: false, why: 'blok script tidak ketemu' };
    try { new Function(last); return { ok: true }; }
    catch (e) { return { ok: false, why: 'SyntaxError: ' + e.message }; }
  });
  check('e2e26-dash-js-sintaks-sah', jsSyntax.ok, jsSyntax.why);
  check('e2e26-dash-fix-kutip', await dash.evaluate(() =>
    document.documentElement.innerHTML.indexOf("icon:'check'}}") >= 0
    && document.documentElement.innerHTML.indexOf("icon:'check}}\n") < 0), 'marker fix tidak ada');

  // 2. poll() benar-benar berjalan: script hidup + baris ter-render di DOM
  await sleep(3000);
  const live = await dash.evaluate(() => ({
    sAlive: typeof window.S === 'object' && window.S !== null,
    since: (window.S && window.S.since) || 0,
    rowCount: document.getElementById('tb') ? document.getElementById('tb').children.length : -1,
    sum: (document.getElementById('sum') || {}).textContent || '',
    ver: (document.getElementById('ver') || {}).textContent || '',
  }));
  check('e2e26-dash-script-hidup', live.sAlive, 'window.S tidak ada — script mati');
  check('e2e26-dash-poll-berjalan', live.since > 0, 'S.since=' + live.since + ' — poll tak pernah jalan');
  check('e2e26-dash-baris-tercetak', live.rowCount > 0, 'DOM #tb=' + live.rowCount + ' baris — log TIDAK tercetak');
  // catatan: smoke desktop menjalankan CLASS JVM (SEBELUM patch marker) —
  // ver dashboard = v19 di sini; rotasi v19→v26 dibuktikan di patch/dex.
  check('e2e26-dash-versi-marker', live.ver === 'v19', 'ver=' + live.ver);
  check('e2e26-dash-nol-pageerror', dashErrors.length === 0, dashErrors.join(' | ').slice(0, 200));

  // 3. baris CLIENT ter-render dengan badge + sub-baris stack/sid
  const clientDom = await dash.evaluate(() => {
    const rows = document.querySelectorAll('#tb .ln');
    let client = 0, stackSub = 0, sidSub = 0, dbg = 0;
    for (let i = 0; i < rows.length; i++) {
      const t = rows[i].textContent || '';
      if (t.indexOf('CLIENT') >= 0) {
        client++;
        if (t.indexOf('stack:') >= 0) stackSub++;
        if (t.indexOf('sid:') >= 0) sidSub++;
      }
      if (t.indexOf('resource') >= 0 && t.indexOf('DEBUG') >= 0) dbg++;
    }
    return { client, stackSub, sidSub, dbg };
  });
  check('e2e26-dash-baris-client', clientDom.client > 0, 'tidak ada baris CLIENT di DOM');
  check('e2e26-dash-subrow-stack', clientDom.stackSub > 0, 'sub-baris stack tidak tampil');
  check('e2e26-dash-subrow-sid', clientDom.sidSub > 0, 'sub-baris sid tidak tampil');
  check('e2e26-dash-badge-debug-resource', clientDom.dbg > 0, 'badge DEBUG resource tidak tampil');

  await browser.close();
  console.log('===== E2E CAPTURE v2 + CONSOLE v18: ' + pass + ' PASS, ' + fail + ' FAIL =====');
  process.exit(fail ? 1 : 0);
})().catch((e) => { console.error('FATAL: ' + (e && e.message)); process.exit(2); });
