/* Verifikasi E2E CAPTURE CLIENT v72 — pola eruda (contoh owner):
 * halaman game nyata (dengan injeksi capture) dijalankan di browser
 * sungguhan → hook console/error/script/xhr aktif → event terkirim ke
 * POST /log/client → baris kind CLIENT muncul di log global server.
 * Semua aksi uji ditrigger halaman itu sendiri (bs/index-native.html smoke). */
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

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 500, height: 800 } });
  let pass = 0, fail = 0;
  const check = (name, cond, info) => {
    if (cond) { pass++; console.log('PASS ' + name); }
    else { fail++; console.log('FAIL ' + name + (info ? '  | ' + String(info).slice(0, 200) : '')); }
  };

  console.log('step: buka halaman game uji (dengan injeksi capture)...');
  await page.goto('http://127.0.0.1:9528/bs/index-native.html?uji=e2e', { waitUntil: 'load', timeout: 30000 });

  // capture terpasang DI HALAMAN (bukti injeksi hidup di browser nyata)
  const capOn = await page.evaluate(() => window.__dbClientCapture === true);
  check('e2e-capture-terpasang', capOn, 'window.__dbClientCapture');

  // demo aksi halaman sudah jalan (body script); tunggu flush capture 500 ms
  await page.waitForTimeout(2500);

  const rows = await getJSON('http://127.0.0.1:4000/logs?mode=port&port=9528&limit=800');
  const cl = (rows.entries || []).filter((e) => e.kind === 'CLIENT');
  const find = (m, sub) => cl.filter((e) => e.method === m && String(e.path || '').indexOf(sub) >= 0);

  check('e2e-console-error-tersampai', find('console.error', 'uji-console-error-v72').length > 0
    && find('console.error', 'uji-console-error-v72')[0].error === 'console.error dari halaman game',
    JSON.stringify(find('console.error', 'uji-console-error-v72')[0] || {}).slice(0, 200));
  check('e2e-console-log-tersampai', find('console.log', 'uji-console-log-v72').length > 0, '');
  check('e2e-console-warn-tersampai', find('console.warn', 'uji-console-warn-v72').length > 0, '');
  const jserr = find('jserror', 'uji-jserror-v72');
  check('e2e-jserror-tersampai', jserr.length > 0
    && jserr[0].error === 'window.onerror dari halaman game'
    && jserr[0].level === 'ERROR', JSON.stringify(jserr[0] || {}).slice(0, 200));
  const sg = find('script', 'uji-gagal.js');
  check('e2e-script-gagal-tersampai', sg.length > 0 && sg[0].path.indexOf('GAGAL') >= 0
    && sg[0].error === 'script halaman game gagal dimuat di client', JSON.stringify(sg[0] || {}).slice(0, 200));
  const so = find('script', 'uji-ada.js');
  check('e2e-script-sukses-tersampai', so.length > 0 && so[0].path.indexOf('selesai') >= 0
    && !('level' in so[0]), JSON.stringify(so[0] || {}).slice(0, 200));
  const xo = find('xhr', 'uji-xhr-ok.json');
  check('e2e-xhr-ok-tersampai', xo.length > 0 && xo[0].path.indexOf('→ 200') >= 0,
    JSON.stringify(xo[0] || {}).slice(0, 200));
  const x4 = find('xhr', 'uji-xhr-404.json');
  check('e2e-xhr-404-tersampai', x4.length > 0 && x4[0].path.indexOf('→ 404') >= 0
    && x4[0].level === 'ERROR', JSON.stringify(x4[0] || {}).slice(0, 200));

  // anti-loop: request /log/client milik capture TIDAK dicapture sebagai event
  const loop = cl.filter((e) => String(e.path || '').indexOf('/log/client') >= 0
    && String(e.method || '').indexOf('console') !== 0);
  check('e2e-tanpa-loop-capture', loop.length === 0, JSON.stringify(loop[0] || {}).slice(0, 200));

  await browser.close();
  console.log('===== E2E CAPTURE v72: ' + pass + ' PASS, ' + fail + ' FAIL =====');
  process.exit(fail ? 1 : 0);
})().catch((e) => { console.error('FATAL: ' + (e && e.message)); process.exit(2); });
