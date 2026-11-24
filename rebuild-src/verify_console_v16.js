/* Verifikasi browser console v16 — dashboard http://127.0.0.1:4000/
 * Cek: 4 kontrol fungsional SAJA (cari, filter error, salin, jeda),
 * rincian langsung tampil (nol klik), filter error memfilter, salin
 * menyalin teks mentah, NOL error console. */
const { chromium } = require('/home/z/node_modules/playwright');

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const ctx = await browser.newContext({
    viewport: { width: 1366, height: 900 },
    permissions: ['clipboard-read', 'clipboard-write'],
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()); });

  let pass = 0, fail = 0;
  const check = (name, cond, info) => {
    if (cond) { pass++; console.log('PASS ' + name); }
    else { fail++; console.log('FAIL ' + name + (info ? '  | ' + info : '')); }
  };

  console.log('step: goto...');
  await page.goto('http://127.0.0.1:4000/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  console.log('step: page loaded, wait 3.5s');
  await page.waitForTimeout(3500);

  // 1. versi + banner
  const ver = await page.textContent('#ver');
  check('badge v16', ver.trim() === 'v16', ver);
  const banner = await page.textContent('#banner .b1');
  check('banner v16', banner.includes('MOD-LOCALSERVER v16'), banner);

  // 2. HANYA 4 kontrol: cari + errf + copy + pause; NOL tombol lain di header
  const hdrButtons = await page.$$eval('#hdr button', els => els.map(e => e.id));
  check('header buttons = [errf,copy,pause]', JSON.stringify(hdrButtons) === JSON.stringify(['errf', 'copy', 'pause']), JSON.stringify(hdrButtons));
  check('cari ada', !!(await page.$('#q')), '');
  const allButtons = await page.$$eval('button', els => els.map(e => e.id || e.className));
  check('total tombol di halaman = 3', allButtons.length === 3, JSON.stringify(allButtons));

  // 3. ikon SVG sungguhan (nol emoji)
  const iconSvg = await page.$eval('#errf', e => !!e.querySelector('svg.ic')) &&
                  await page.$eval('#copy', e => !!e.querySelector('svg.ic')) &&
                  await page.$eval('#pause', e => !!e.querySelector('svg.ic'));
  check('ikon SVG di 3 tombol', iconSvg, '');
  const bodyText = await page.evaluate(() => document.body.innerText);
  const emojiRe = /[\u{1F000}-\u{1FFFF}]|[\u{2600}-\u{27BF}]/u;
  check('nol emoji', !emojiRe.test(bodyText), '');

  // 4. log mengalir + rincian LANGSUNG TAMPIL (nol klik)
  await page.waitForTimeout(3000);
  const rows = await page.$$eval('#tb .ln', els => els.length);
  check('baris log tampil', rows > 5, String(rows));
  const dtl = await page.$$eval('#tb .ln .dtl', els => els.length);
  const dtlVisible = await page.$$eval('#tb .ln .dtl', els =>
    els.filter(e => e.offsetHeight > 0 && e.children.length > 0).length);
  check('rincian inline ada & terlihat (nol klik)', dtl > 0 && dtlVisible === dtl,
    'dtl=' + dtl + ' visible=' + dtlVisible);
  const subLabels = await page.$$eval('#tb .sub .lbl', els => els.slice(0, 6).map(e => e.textContent));
  check('label sub-baris (headers/request/response/…)',
    subLabels.some(s => /headers:|request:|response:|recv:|send:|data:|error:/.test(s)),
    JSON.stringify(subLabels));

  // 5. FILTER ERROR: aktif -> hanya WARN/ERROR yang terlihat
  await page.click('#errf');
  await page.waitForTimeout(400);
  const visLv = await page.$$eval('#tb .ln', els =>
    els.filter(e => e.offsetHeight > 0)
       .map(e => e.querySelector('.lv') ? e.querySelector('.lv').textContent.trim() : ''));
  const uniq = [...new Set(visLv)];
  check('filter error: hanya WARN/ERROR', uniq.length > 0 && uniq.every(w => /WARN|ERROR/.test(w)),
    JSON.stringify(uniq.slice(0, 8)));
  const errfOn = await page.$eval('#errf', e => e.classList.contains('erron'));
  check('tombol errf menyala (erron)', errfOn, '');
  const footerMsg = await page.textContent('#sum');
  check('footer konfirmasi filter', footerMsg.includes('filter ERROR aktif'), footerMsg);

  // 6. SALIN: teks mentah protokol masuk clipboard (readText di-race dgn
  //    timeout 3 dtk — readText bisa menggantung di headless)
  await page.click('#copy');
  await page.waitForTimeout(600);
  const clip = await Promise.race([
    page.evaluate(() => navigator.clipboard.readText()),
    new Promise((res) => setTimeout(() => res(null), 3000)),
  ]);
  check('clipboard berisi baris log', clip.trim().length > 0, 'len=' + clip.trim().length);
  const okFmt = /^20\d\d-\d\d-\d\d \d\d:\d\d:\d\d\.\d+ #\d+ \d+ (WARN|ERROR|INFO|DEBUG) [A-Z]+ /m.test(clip);
  check('format mentah: time #seq port LEVEL COMP ...', okFmt, clip.split('\n')[0]);
  const subInClip = clip.includes('  headers: ') || clip.includes('  request: ') ||
                    clip.includes('  response: ') || clip.includes('  error: ') ||
                    clip.includes('  data: ');
  check('sub-baris rincian ikut tersalin', subInClip, clip.slice(0, 200));
  const copyMsg = await page.textContent('#sum');
  check('footer konfirmasi salin', copyMsg.includes('tersalin'), copyMsg);

  // 7. filter error MATI lagi -> semua level kembali
  await page.click('#errf');
  await page.waitForTimeout(400);
  const visAll = await page.$$eval('#tb .ln', els => els.filter(e => e.offsetHeight > 0).length);
  check('filter mati: semua baris kembali', visAll > visLv.length, visAll + ' vs ' + visLv.length);

  // 8. JEDA: poll berhenti, mark muncul
  await page.click('#pause');
  await page.waitForTimeout(300);
  const pm = await page.textContent('#pausemark');
  check('jeda: penanda muncul', pm.includes('jeda'), pm);
  await page.click('#pause');

  // 9. NOL error console/page
  check('nol error console/page', errors.length === 0, errors.join(' ; ').slice(0, 300));

  // 10. tampilan mobile (375px) tetap utuh
  await page.setViewportSize({ width: 375, height: 700 });
  await page.waitForTimeout(600);
  const mobVis = await page.$$eval('#hdr button', els => els.filter(e => e.offsetHeight > 0).length);
  check('mobile: 3 tombol terlihat', mobVis === 3, String(mobVis));

  await browser.close();
  console.log('\n===== BROWSER VERIFY: %d PASS, %d FAIL ====='.replace('%d', String(pass)).replace('%d', String(fail)));
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error('FATAL', e); process.exit(2); });
