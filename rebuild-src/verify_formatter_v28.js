#!/usr/bin/env node
/* verify_formatter_v28.js — UJI FORMATTER v5 TEKS-PER-TEKS (Bagian 81.3 + 83.4).
 * Dump device owner (release-mod/server.ndjson, 66 entri, 21:47:35.626-58.530)
 * dirender formatter v5 dashboard (fungsi textLines() PERSIS yang di dashboar
 * 4000) → hasil WAJIB sama teks-per-teks dengan pola 81.3 readme + 1 baris N1
 * (satu-satunya baris baru dari dex v28).
 * Teks dump lama dinormalisasi DUA substitusi terdokumentasi 81.2 (dex v28
 * berhenti mengirim rujukan internal):
 *   1. " — Bagian 21" dihapus (ServerCore BIND 5051)
 *   2. " (owner edit — seed tidak menimpa; atlas §11 baris 9)" dihapus (LoginAck)
 */
'use strict';
const fs = require('fs');

const DASH = '/tmp/v28/dashboard.html';
const DUMP = '/home/z/my-project/db-apk/release-mod/server.ndjson';

/* ---------- 1. muat formatter v5 dari dashboard 4000 ---------- */
const html = fs.readFileSync(DASH, 'utf8');
const mjs = html.match(/<script>([\s\S]*)<\/script>/);
if (!mjs) { console.error('FAIL: blok <script> tidak ketemu'); process.exit(1); }
const jsSrc = mjs[1];

const documentStub = {
  title: '',
  getElementById: function () {
    return { innerHTML: '', style: {}, textContent: '',
             classList: { toggle: function () {} },
             children: [], appendChild: function () {}, removeChild: function () {} };
  },
  querySelectorAll: function () { return []; },
  createElement: function () {
    return { style: {}, setAttribute: function () {}, className: '', innerHTML: '',
             appendChild: function () {} };
  },
  body: { appendChild: function () {}, removeChild: function () {} },
  execCommand: function () { return false; }
};
const sandboxApi = new Function(
    'document', 'window', 'navigator', 'location', 'fetch', 'setTimeout',
    'clearTimeout', 'setInterval',
    jsSrc + '\nreturn { textLines: textLines, emojiOf: emojiOf, mainText: mainText,' +
    ' lineHead: lineHead, detailsOf: detailsOf, isDup: isDup };')(
    documentStub, {}, {}, { port: '4000' },
    function () { return Promise.reject(new Error('no-network-in-test')); },
    function () { return 0; }, function () {}, function () { return 0; });

/* ---------- 2. muat dump device owner ---------- */
const raw = fs.readFileSync(DUMP, 'utf8');
const entries = [];
for (const line of raw.split('\n')) {
  const t = line.trim();
  if (!t) continue;
  entries.push(JSON.parse(t));
}
if (entries.length !== 66) {
  console.error('FAIL: dump harus 66 entri — dapat ' + entries.length);
  process.exit(1);
}

/* normalisasi teks dex v28 (2 substitusi terdokumentasi 81.2) */
for (const e of entries) {
  if (e.path && e.path.indexOf(' — Bagian 21') >= 0)
    e.path = e.path.replace(' — Bagian 21', '');
  if (e.extra && e.extra.indexOf(' (owner edit — seed tidak menimpa; atlas §11 baris 9)') >= 0)
    e.extra = e.extra.replace(' (owner edit — seed tidak menimpa; atlas §11 baris 9)', '');
}

/* ---------- 3. baris N1 (satu-satunya baris baru — dex v28) ---------- */
entries.push({
  seq: 67, ts: 1795704478531, time: '2026-11-26 21:47:58.531', port: 9528,
  kind: 'WATCH', method: 'N1',
  path: "sesi YfXronzl7TC64pR4bX5x diam 10 dtk — client tidak mengirim '5' — upgrade websocket tidak selesai",
  status: 200, durMs: 0, reqLen: 0, respLen: 0
});

/* ---------- 4. GOLDEN — pola readme 81.3 (teks-per-teks) ---------- */
const G = [];
G.push('21:47:35.687 🚀 4000 mini-server up; logger port 4000; NDJSON archive: /storage/emulated/0/Android/data/com.db.local/server/logs');
G.push('↳ 21:47:35.626 🚀 4000 LocalServerStarter.start (Application.onCreate) — MOD-LOCALSERVER v22 — serverRoot=/storage/emulated/0/Android/data/com.db.local/server');
G.push('↳ 21:47:35.629 🚀 4000 no exit history (normal for a fresh install)');
G.push('↳ 21:47:35.630 🐞 4000 CrashHook installed — java crashes captured to logs/crash-*.txt');
G.push('↳ 21:47:35.633 📱 4000 overlay permission OFF — game does not use overlay');
G.push('↳ 21:47:35.671 📲 9530 DB-BACKEND sqlite dir=/storage/emulated/0/Android/data/com.db.local/server/sdk/data');
G.push('↳ 21:47:35.671 🚀 4000 DB ready: backend=sqlite dir=sdk/data');
G.push('↳ 21:47:35.676 📲 9530 LOGIN-SERVER seed: GetServerList.json (436 B)');
G.push('↳ 21:47:35.679 📲 9530 LOGIN-SERVER seed: loginGame.json (23 B)');
G.push('↳ 21:47:35.680 📲 9530 LOGIN-SERVER seed: SaveHistory.json (46 B)');
G.push('↳ 21:47:35.681 📲 9530 LOGIN-SERVER seed: LoginAnnounce.json (2 B)');
G.push('↳ 21:47:35.683 📲 9530 LOGIN-SERVER seed: SaveLanguage.json (37 B)');
G.push('↳ 21:47:35.683 🚀 4000 LOGIN-SERVER seed ready (5 file event): /storage/emulated/0/Android/data/com.db.local/server/login-server');
G.push('↳ 21:47:35.686 🌐 4000 BIND port 9527 — config bin (mirror configus…:443) — OK');
G.push('↳ 21:47:35.687 🌐 4000 BIND port 9528 — CDN game + socket.io login (mirror dragonh5cdn…:443) — OK');
G.push('↳ 21:47:35.687 🌐 4000 BIND port 5050 — login socket.io v2.1.2 EIO=3 + loginchecknative (mirror login…:610) — OK');
G.push('↳ 21:47:35.687 🌐 4000 BIND port 5051 — server game socket.io v2.1.2 EIO=3 (serverItem.url) — OK');
G.push('↳ 21:47:35.687 🌐 4000 BIND port 9530 — API SDK t4game (mirror qsdk.t4game.com:80) — OK');
G.push('↳ 21:47:35.687 🌐 4000 BIND port 4000 — logger 2-mode — OK');
G.push('21:47:37.445 🧾 9527 GET http://127.0.0.1:9527/bs/db/android/setting_BS_Android.bin?rnd=0.784774 → 200 · 3 ms · 687 B');
G.push('↳ src=assets | respCT=application/octet-stream');
G.push('↳ DECODED-XOR:');
G.push('      {');
G.push('        "testMode":  1,');
G.push('        "url":  "http://127.0.0.1:9528/bs/index-native.html?v=202109150935",');
G.push('        "tsUrl":  "http://dragon.sjmobilegame.com:82/bs103/index-native.html",');
G.push('        "update":  "http://127.0.0.1:9528/bs/upgrade",');
G.push('        "checkTime":  300,');
G.push('        "loginServer":  "http://127.0.0.1",');
G.push('        "tsLoginServer":  "http://118.24.68.91:7003",');
G.push('        "loginPort":  9528,');
G.push('        "loginWebPort":  5050,');
G.push('        "clientParams":  {');
G.push('          "hideList":  [');
G.push('            "1.99",');
G.push('            "2.99",');
G.push('            "14.99",');
G.push('            "59.99",');
G.push('            "89.99"');
G.push('          ],');
G.push('          "supportLang":  [');
G.push('            {');
G.push('              "languageName":  "English",');
G.push('              "lang":  "en"');
G.push('            },');
G.push('            {');
G.push('              "languageName":  "Français",');
G.push('              "lang":  "fr"');
G.push('            },');
G.push('            {');
G.push('              "languageName":  "한국인",');
G.push('              "lang":  "kr"');
G.push('            }');
G.push('          ],');
G.push('          "showCurChannel":  "toFacebook"');
G.push('        }');
G.push('      }');
G.push('21:47:37.508 🧾 9527 GET http://127.0.0.1:9527/bs/db/android/com_db_local.bin?rnd=0.870879 → 200 · 0 ms · 2 B');
G.push('↳ src=assets | respCT=application/octet-stream | DECODED-XOR: {}');
G.push('21:47:37.516 🧾 9527 GET http://127.0.0.1:9527/bs/db/android/setting_BS_Android.bin?rnd=0.954050 → 200 · 3 ms · 687 B');
G.push('21:47:37.523 🧾 9527 GET http://127.0.0.1:9527/bs/db/android/com_db_local.bin?rnd=0.506791 → 200 · 1 ms · 2 B');
G.push('21:47:38.510 🌐 9528 GET http://127.0.0.1:9528/bs/upgrade/base.version?rnd=0.820978 → 200 · 1 ms · 4 B');
G.push('↳ respCT=text/plain; charset=utf-8 · 110');
G.push('21:47:38.517 🌐 9528 GET http://127.0.0.1:9528/bs/upgrade/resource.version?rnd=0.695534 → 200 · 2 ms · 5 B');
G.push('↳ respCT=text/plain; charset=utf-8 · 11390');
G.push('21:47:38.521 🌐 9528 GET http://127.0.0.1:9528/bs/upgrade/upgrade.json?rnd=0.494507 → 200 · 1 ms · 130 B');
G.push('↳ respCT=application/json; charset=utf-8 · {"11389": "11389_11390.zip", "11388": "11388_11390.zip", "11387": "11387_11390.zip", "11386": "11386_11390.zip", "all": "all.zip"}');
G.push('21:47:38.527 🌐 9528 GET http://127.0.0.1:9528/bs/upgrade/size.json?rnd=0.353795 → 200 · 0 ms · 105 B');
G.push('↳ respCT=application/json; charset=utf-8 · {"base": "31015076", "11389": 252562, "11388": 252562, "11387": 252562, "11386": 252562, "all": 19385098}');
G.push('21:47:38.717 📥 9528 GET http://127.0.0.1:9528/bs/upgrade/all.zip?v=11011390 → 200 · 185 ms · 19.4 MB');
G.push('↳ respCT=application/zip');
G.push('21:47:39.959 📲 9530 SDK-CONFIG seed: errors.json');
G.push('21:47:39.960 📲 9530 SDK-CONFIG seed: QHinfo (21 B)');
G.push('21:47:39.966 📲 9530 SDK-CONFIG seed: v1/system/init.json (595 B)');
G.push('21:47:39.971 📲 9530 SDK-CONFIG seed: v1/user/ckRegVistor.json (66 B)');
G.push('21:47:39.973 📲 9530 SDK-CONFIG seed: fallback.json (72 B)');
G.push('21:47:39.975 📲 9530 SDK-CONFIG seed: profile.json (513 B)');
G.push('21:47:39.978 📲 9530 POST http://127.0.0.1:9530/v1/system/init → 200 · 29 ms · 594 B');
G.push('↳ src=sdk/v1/system/init.json | respCT=text/json');
G.push('21:47:39.987 📲 9530 POST http://127.0.0.1:9530/v1/system/getNotice → 200 · 4 ms · 25 B');
G.push('↳ respCT=text/json · {"result":true,"data":""}');
G.push('21:47:40.808 📲 9530 new account uid=1795704460785 username=Guest4796');
G.push('21:47:40.809 📲 9530 POST http://127.0.0.1:9530/v1/user/registerVisitor → 200 · 27 ms · 317 B');
G.push('↳ respCT=text/json · {"result":true,"data":"{\\"authToken\\":\\"04a6a6…8faa\\",\\"checkRealName\\":\\"0\\",\\"userData\\":{\\"uid\\":\\"1795704460785\\",\\"username\\":\\"Guest4796\\",\\"displayUid\\":\\"1795704460785\\",\\"token\\":\\"04a6a6…8faa\\",\\"isGuest\\":\\"1\\",\\"openType\\":\\"12\\",\\"isNewUser\\":1,\\"isTrash\\":0}}"}');
G.push('21:47:42.020 🧩 4000 extract server_root/bs done (2759 files)');
G.push('21:47:42.020 🧩 4000 extract done: 2759 files -> /storage/emulated/0/Android/data/com.db.local/server');
G.push('21:47:42.393 🔑 5050 GET http://127.0.0.1:5050/loginchecknative?uid=1795704460785&usermode=1&username=Guest4796&token=04a6a6b3fbf723a3131b2ea76b7d8faa&appVersion=1&osVersion=31&sourceVersion=20200418&model=itel+S665L&packageName=com.db.local&brand=Itel&os=Android&sdk=BS → 200 · 1 ms · 313 B');
G.push('↳ loginchecknative uid=1795704460785 username=Guest4796 usermode=1 respCT=application/json');
G.push('21:47:42.654 📄 9528 GET http://127.0.0.1:9528/bs/index-native.html?v=202109150935 → 200 · 3 ms · 10.8 KB');
G.push('↳ respCT=text/html; charset=utf-8');
G.push('21:47:42.720 📄 9528 GET http://127.0.0.1:9528/bs/manifest.json?v=0.0065566001245453975 → 200 · 1 ms · 533 B');
G.push('↳ respCT=application/json; charset=utf-8');
G.push('21:47:45.633 🐞 4000 default uncaught handler replaced by third party (crash reporter) — re-wrapped, crashes still captured');
G.push('21:47:46.967 🖼️ 9528 GET http://127.0.0.1:9528/bs/resource/properties/clientversion.json → 200 · 1 ms · 37 B');
G.push('↳ {"clientVersion": "202109150935"}');
G.push('21:47:46.972 🖼️ 9528 GET http://127.0.0.1:9528/bs/resource/properties/serversetting.json → 200 · 2 ms · 66 B');
G.push('↳ {"loginserver": "http://127.0.0.1:9528", "requireSDK": false}');
G.push('21:47:48.145 📥 ERROR 9528 GET http://127.0.0.1:9528/bs/denglujiemianxuanzhejiemiannew21_png → 404 · 1 ms · 55 B');
G.push('21:47:48.150 📥 ERROR 9528 GET http://127.0.0.1:9528/bs/Instagram_png → 404 · 1 ms · 32 B');
G.push('21:47:48.153 📥 ERROR 9528 GET http://127.0.0.1:9528/bs/Telegram_png → 404 · 1 ms · 31 B');
G.push('21:47:48.155 📥 ERROR 9528 GET http://127.0.0.1:9528/bs/Discord_png → 404 · 1 ms · 30 B');
G.push('21:47:48.160 🖼️ 9528 GET http://127.0.0.1:9528/bs/resource/properties/clientversion.json?v=0.3170666716089785 → 200 · 2 ms · 37 B');
G.push('21:47:48.170 🔑 9528 GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=Q8OKxB6 → 200 · 1 ms · 103 B');
G.push('↳ open sid=N1QHulHfMKFnmKfjp70T');
G.push('21:47:48.176 📥 ERROR 9528 GET http://127.0.0.1:9528/bs/Logo18+Full_png → 404 · 1 ms · 34 B');
G.push('21:47:48.196 🌐 9528 GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=websocket&sid=N1QHulHfMKFnmKfjp70T → 101 · 1 ms');
G.push('↳ ws upgrade sid=N1QHulHfMKFnmKfjp70T');
G.push('21:47:48.202 🔑 9528 GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=Q8OKxBb&sid=N1QHulHfMKFnmKfjp70T → 200 · 0 ms · 448 B');
G.push('21:47:48.203 🔑 9528 POST http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=Q8OKxBa&sid=N1QHulHfMKFnmKfjp70T → 200 · 6 ms · 2 B');
G.push('↳ ACK 430 — GetServerList (file GetServerList.json dibaca ulang) | frame: 430[{"ret":0,"data":"{\\"serverList\\":[\\n    {\\n      \\"serverId\\": \\"1\\",\\n      \\"url\\": \\"http://127.0.0.1:9528\\",\\n      \\"new\\": true,\\n      \\"hot\\": false,\\n      \\"name\\": \\"DragonBall Local\\",\\n      \\"online\\": true,\\n      \\"openDate\\": \\"2026-11-26T00:00:00.000Z\\",\\n      \\"offlineReason\\": \\"\\",\\n      \\"merge\\": false,\\n      \\"mergeTo\\": \\"\\",\\n      \\"urlInner\\": \\"\\",\\n      \\"chatInner\\": \\"\\"\\n    }\\n  ],\\"history\\":[]}"}] → dikirim: antre polling');
G.push('21:47:48.334 🌐 9528 WS http://127.0.0.1:9528/socket.io/?EIO=3&transport=websocket&sid=N1QHulHfMKFnmKfjp70T → 200 · 0 ms · 6 B');
G.push('↳ 3probe');
G.push('21:47:48.348 📥 ERROR 9528 GET http://127.0.0.1:9528/bs/resource/assets/image/public/sound/sound_click.mp3?v=2296163726 → 404 · 1 ms · 69 B');
G.push('21:47:48.359 🔑 9528 GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=Q8OKxE2.0&sid=N1QHulHfMKFnmKfjp70T → 200 · 1 ms · 50 B');
G.push('21:47:48.359 🔑 9528 POST http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=Q8OKxE2&sid=N1QHulHfMKFnmKfjp70T → 200 · 4 ms · 2 B');
G.push('↳ ACK 431 — SaveHistory (file SaveHistory.json dibaca ulang) | frame: 431[{"ret":0,"data":"{\\"todayLoginCount\\":1}"}] → dikirim: antre polling');
G.push('21:47:48.467 🌐 9528 WS http://127.0.0.1:9528/socket.io/?EIO=3&transport=websocket&sid=N1QHulHfMKFnmKfjp70T → 200 · 0 ms');
G.push('↳ upgrade selesai — 0 antre pindah ke WS + noop 6 sid=N1QHulHfMKFnmKfjp70T');
G.push('21:47:48.468 🔑 9528 GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=Q8OKxFm → 200 · 2 ms · 103 B');
G.push('↳ open sid=YfXronzl7TC64pR4bX5x');
G.push('21:47:48.476 🌐 9528 WS http://127.0.0.1:9528/socket.io/?EIO=3&transport=websocket&sid=N1QHulHfMKFnmKfjp70T → 200 · 0 ms');
G.push('↳ EVENT (no ack): 422["handler.process",{"type":"User","action":"LoginAnnounce"}] | login-server: LoginAnnounce.json kosong {} = DIAM');
G.push('21:47:48.484 🌐 9528 GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=websocket&sid=YfXronzl7TC64pR4bX5x → 101 · 0 ms');
G.push('↳ ws upgrade sid=YfXronzl7TC64pR4bX5x');
G.push('21:47:48.502 🌐 9528 WS http://127.0.0.1:9528/socket.io/?EIO=3&transport=websocket&sid=YfXronzl7TC64pR4bX5x → 200 · 0 ms · 6 B');
G.push('↳ 3probe');
G.push('21:47:58.530 ⏳ 9528 GET http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=Q8OKxG1&sid=YfXronzl7TC64pR4bX5x → 200 · 10046 ms · 0 B');
G.push('↳ long-poll timeout, no data');
G.push("21:47:58.531 ⏳ 9528 sesi YfXronzl7TC64pR4bX5x diam 10 dtk — client tidak mengirim '5' — upgrade websocket tidak selesai");

/* ---------- 5. render + bandingkan ---------- */
const out = sandboxApi.textLines(entries);
let fail = 0;
const n = Math.max(out.length, G.length);
for (let i = 0; i < n; i++) {
  if (out[i] !== G[i]) {
    fail++;
    console.log('BARIS ' + (i + 1) + ' BEDA:');
    console.log('  formatter: ' + JSON.stringify(out[i] === undefined ? '(tidak ada)' : out[i]));
    console.log('  golden   : ' + JSON.stringify(G[i] === undefined ? '(tidak ada)' : G[i]));
  }
}
console.log('===== FORMATTER v5: ' + (fail === 0 ? 'SAMA TEKS-PER-TEKS' : fail + ' BARIS BEDA') +
    ' — ' + out.length + ' baris render vs ' + G.length + ' baris golden =====');
process.exit(fail === 0 ? 0 : 1);
