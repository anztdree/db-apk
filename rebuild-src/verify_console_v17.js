/* Verifikasi browser console v17 — dashboard http://127.0.0.1:4000/
 * Fokus v17 (arahan owner): URL PENUH di baris log — nol pangkas,
 * nol tersembunyi. Cek: badge/banner v17, 4 kontrol fungsional, rincian
 * inline, entri socket.io memuat http://127.0.0.1:<port>/socket.io/?EIO=3&
 * transport=... apa adanya, WS frame juga URL penuh, NOL error console. */
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
  check('badge v17', ver.trim() === 'v17', ver);
  const banner = await page.textContent('#banner .b1');
  check('banner v17', banner.includes('MOD-LOCALSERVER v17'), banner);
  const bannerSub = await page.textContent('#banner .bl');
  check('banner klaim URL penuh', bannerSub.includes('URL penuh') && bannerSub.includes('nol disembunyikan'), bannerSub);

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

  // 5. v17 INTI — URL PENUH socket.io di baris log (tidak dipangkas)
  const fullUrlRows = await page.$$eval('#tb .ln .ev', els =>
    els.filter(e => /http:\/\/127\.0\.0\.1:\d+\/socket\.io\/\?EIO=3&transport=(polling|websocket)&/.test(e.textContent))
       .map(e => e.textContent.trim()));
  check('ada baris dgn URL PENUH socket.io', fullUrlRows.length > 0,
    String(fullUrlRows.length) + ' baris');
  const wsFull = fullUrlRows.filter(t => t.startsWith('WS') && t.includes('transport=websocket'));
  check('WS frame pakai URL penuh koneksi', wsFull.length > 0, wsFull[0] || '');
  const pollingFull = fullUrlRows.filter(t => t.includes('transport=polling'));
  check('polling GET/POST pakai URL penuh', pollingFull.length > 0, pollingFull[0] || '');
  const sample = pollingFull[0] || wsFull[0] || '';
  check('URL utuh (query t=… dan/atau sid=… ikut tampil)',
    /EIO=3&transport=[a-z]+(&t=[^ ]+|&sid=[^ ]+)/.test(sample), sample.slice(0, 160));
  const nakedRows = await page.$$eval('#tb .ln .ev', els =>
    els.filter(e => /(^|>)GET \/socket\.io\/\?|(^|>)POST \/socket\.io\/\?|(^|>)WS sid=/.test(e.textContent)).length);
  check('nol lagi path telanjang ("/socket.io/.." atau "WS sid=..")', nakedRows === 0, String(nakedRows));
  // URL penuh JUGA di baris tersalin? (sample clipboard di bawah memakai format sama)
  const evSample = fullUrlRows[0] || '';
  check('baris URL penuh tidak terpotong elipsis "…"',
    !/socket\.io\/\?EIO=3[^ ]*…/.test(evSample), evSample.slice(0, 120));

  // 6. sub-baris recv/send frame tampil inline
  const wsSub = await page.$$eval('#tb .ln .sub', els =>
    els.filter(e => /recv:|send:/.test(e.textContent)).length);
  check('sub-baris recv/send ada', wsSub > 0, String(wsSub));

  // 7. FILTER ERROR: aktif -> hanya WARN/ERROR yang terlihat
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

  // 8. SALIN: teks mentah protokol masuk clipboard (readText di-race dgn
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
  const clipFullUrl = /http:\/\/127\.0\.0\.1:\d+\/socket\.io\/\?EIO=3&transport=[a-z]+&/.test(clip);
  check('URL penuh ikut tersalin', clipFullUrl, clip.split('\n').slice(0, 3).join(' | '));
  const copyMsg = await page.textContent('#sum');
  check('footer konfirmasi salin', copyMsg.includes('tersalin'), copyMsg);

  // 9. filter error MATI lagi -> semua level kembali
  await page.click('#errf');
  await page.waitForTimeout(400);
  const visAll = await page.$$eval('#tb .ln', els => els.filter(e => e.offsetHeight > 0).length);
  check('filter mati: semua baris kembali', visAll > visLv.length, visAll + ' vs ' + visLv.length);

  // 10. JEDA: poll berhenti, mark muncul
  await page.click('#pause');
  await page.waitForTimeout(300);
  const pm = await page.textContent('#pausemark');
  check('jeda: penanda muncul', pm.includes('jeda'), pm);
  await page.click('#pause');

  // 11. NOL error console/page
  check('nol error console/page', errors.length === 0, errors.join(' ; ').slice(0, 300));

  // 12. tampilan mobile (375px) tetap utuh
  await page.setViewportSize({ width: 375, height: 700 });
  await page.waitForTimeout(600);
  const mobVis = await page.$$eval('#hdr button', els => els.filter(e => e.offsetHeight > 0).length);
  check('mobile: 3 tombol terlihat', mobVis === 3, String(mobVis));

  await browser.close();
  console.log('\n===== BROWSER VERIFY: %d PASS, %d FAIL ====='.replace('%d', String(pass)).replace('%d', String(fail)));
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error('FATAL', e); process.exit(2); });
