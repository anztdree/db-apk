# db-apk — DB - Local (`com.db.local`) — 1 APK, 2 SISTEM, 100% OFFLINE

Dokumen tunggal proyek = **proposal kerja saya** (asisten). Semua klaim
bertanda **[TERVERIFIKASI]** dibuktikan dari isi APK itu sendiri (manifest,
bytecode `classes.dex`, asset, native lib, dan **JS game asli hasil dekripsi
pack**), bukan asumsi. Git history dan artefak lama = **VOID** (keputusan
owner) — tidak dipulihkan, tidak dijadikan acuan. Acuan tunggal: file APK
original di repo ini. Rincian dibedah di chat; README ini adalah dokumen
kerja untuk eksekusi.

---

## 1. IDENTITAS APK [TERVERIFIKASI]

| Properti | Nilai |
|---|---|
| Package | `com.db.local` (asli sebelum rename: `com.guan.wangys`) |
| Label | `DB - Local` |
| Project name | `DragonBall` |
| versionCode / versionName | `1` / `1.0.1` |
| minSdk / targetSdk | 21 / 30 (compileSdk 23) |
| Ukuran | 104.945.181 bytes |
| SHA-256 | `b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218` |
| Signature | AOSP testkey (akan diganti keystore BARU saat rebuild) |
| Clear-text HTTP | **DIIZINKAN** — `usesCleartextTraffic="true"` + `network_security_config.xml` base-config `cleartextTrafficPermitted="true"` |
| Application class | `com.zhuhuan.game.Application` |
| Launcher / Game host | `com.zhuhuan.game.LaunchActivity` / `MainActivity` |
| Native lib | `libegret.so` (Egret Native + libcurl + WebSocket JNI), `libgavesec.so` (pelindung string) |
| WebSocket client di device | **TooTallNate Java-WebSocket** (tertananam di `assets/runtime-dex.jar`) — RFC6455 standar |
| Dynamic runtime | `assets/runtime-dex.jar` (loader hot-update Egret, obfuscated) |
| Game SDK | QuickGame SDK, Facebook SDK, Google Billing 3.0.2, GMS Sign-in (semuanya OPSIONAL — gagal offline non-fatal) |

---

## 2. ARSITEKTUR: 1 APK = 2 SISTEM

```
+------------------------------------------------------------------+
|                     SATU APK com.db.local                        |
|                                                                  |
|  SISTEM 2 (client, BEKU/PATEN)         SISTEM 1 (mini SERVER)    |
|  - logika TIDAK diubah                 - classes2.dex s2.* (baru)|
|  - 4 titik edit (semuanya alamat)        - boot di Application   |
|    -> semua jadi 127.0.0.1:4000          .onCreate (paling awal, |
|  - redirect lanjutan via setting JSON     SEBELUM LaunchActivity)|
|                                        - bind 127.0.0.1:4000     |
|                                        - HTTP + WebSocket 1 port |
|                                        - dashboard owner /dash   |
+------------------------------------------------------------------+
```

---

## 3. ENKRIPSI RESOURCE `onepieceencrypts` — TERPECAHKAN [TERVERIFIKASI]

Ditemukan di bytecode `classes.dex`, kelas obfuscated
`cointhreat...customreading`, metode `nationalcommunitymissing([B)[B`.
Skrip pembuktian: **`tools/decrypt_onepiece.py`** (reproducible).

```
[0..16)   header bebas ("T01 " / acak)      — diabaikan
[16..32)  magic "onepieceencrypts"          — penanda
[32..48)  blok KEY  = XOR konstanta "onepiecedecryptk" -> key XXTEA per-file
[48..]    body      = XXTEA varian vendor: delta 0x9E3779B9, word LE,
            SATU PASS p=n-1..0, sum=delta tetap, MX standar, e=(sum>>>2)&3
output    byte LE per word, pad = byte terakhir dibuang
```

Hasil dekripsi (bukti nyata):

| Pack | Ukuran | Isi terdekripsi |
|---|---|---|
| `voyage_fancy_radius.bin` | 1.233.600 B | **ZIP, 161 entri**: `index.html`, `manifest_ts.json`, `js/` (egret, game, eui, fairygui, dragonBones, assetsmanager, main…), `resource/` |
| `pschent_full_tired.bin` | 9.428.744 B | **ZIP, 741 entri**: `js/` 14 modul (termasuk **`main.min_7eae4d6e.js` 4,7 MB** = logika game penuh, `socket.io.min_57ea9db7.js` = **socket.io client v2.1.2**, `lz-string.min`, `md5.min`) + `resource/` |
| `vvcc.bin` | 183.764 B | **JSON peta nama file** (1.944 key: path asli ↔ nama obfuscated) |
| `vvcc_tc.bin` | 10.956 B | **JSON peta nama file** (139 key) |

> Artinya: **seluruh kode client + kontrak protokol sudah di tangan**.
> Kontrak wire di §5 diambil langsung dari JS asli ini — bukan tebakan.

---

## 4. SISTEM 2 — CLIENT BEKU: SEMUA TITIK JARINGAN [TERVERIFIKASI]

### 4.1 Boot native (bytecode)

1. `LaunchActivity` pilih supplier **"BS"** (`const-string 'BS'` di
   `LaunchActivity`) → baca `config_BS.properties` EntryPoint1/2 (base64)
   → HTTP GET (retry 5x, timeout naik `5*i*1000`) → response
   **di-XOR key `"DragonBall"`** (`const-string 'DragonBall'` di
   `cointhreat/.../seventygenom->nationalcommunitymissing` dan
   `LaunchActivity.<init>`) → parse JSON setting → SharedPreferences.
2. `MainActivity`: `EgretNativeAndroid.initialize(gameUrl)`; fallback
   `gameUrlTs` (Base64); bridge `jsbind` (`addJavascriptInterface`,
   string `'jsbind'` di `classes.dex`); penyusun parameter runtime
   (`MainActivity->hardlinerspare`): `clientParams`, `loginServer`,
   `mSdkParams`, `mGameUrl`, `mPreloadPath`, `language`, `version`.
3. Hot-update: bandingkan lokal `base.version`=110 / `resource.version`=11220
   dengan `{updateUrl}/…` (`upgrade.json`, `size.json`, `base.zip`, `all.zip`);
   gagal/404 = aman (pakai resource bawaan APK).

### 4.2 Titik jaringan JS game (dari JS asli, hasil dekripsi §3)

| Titik | Sumber alamat | Kebutuhan |
|---|---|---|
| Setting JSON | EntryPoint di `config*.properties` (edit alamat saja) | JSON field §5.1, XOR `DragonBall` |
| `window.getLoginServer()` | **nilai dari native_setting `loginServer:loginPort`** — dibaca `classes.dex` (`jsonObject.getString("loginServer")`), dibawa layer native, di-expose ke JS. JS sendiri **TIDAK punya definisinya** (1 referensi di `main.min` = pemanggilnya saja) dan fallback `resource/properties/serversetting.json` **TIDAK ADA** di pack (peta `vvcc` 1.944 entri tanpa `serversetting`) ⇒ satu-satunya sumber alamat = setting JSON yang kita kendalikan 100% | URL socket login |
| `window.getSdkLoginInfo()` | via bridge (`excuteFunction("getSdkLoginInfo")`); bila `null` → JS jatuh ke **“无渠道登录” (login tanpa channel)** → `isNoSdk=true` → `checkLastLogin()` — jalur login offline, TANPA Google/Facebook | identitas |
| `socket login-server` | `getLoginServer()` | handshake verify + `loginGame` + `GetServerList` |
| `socket main-server` | `serverItem.url` (dari jawaban GetServerList) | semua RPC gameplay (`handler.process`) |
| `socket chat-server` | `serverItem.chaturl` | `registChat`, chat room |
| `socket dungeon-server` | `serverItem.dungeonurl` | dungeon/battle realtime |
| `./manifest.json?v=` | XHR relatif (dir shell lokal) | daftar JS — sudah ada di pack |
| `checkTime` | setting | sinkron waktu server |

SDK lain (Facebook/Billing/GMS/PEI/qsdk) = opsional, gagal offline non-fatal.

---

## 5. KONTRAK WIRE 1:1 [TERVERIFIKASI DARI JS ASLI]

### 5.1 JSON native_setting (server kirim, XOR `DragonBall`)

```json
{
  "url":  "http://127.0.0.1:4000/",  "webUrl": "http://127.0.0.1:4000/",
  "tsUrl": "http://127.0.0.1:4000/", "tsWebUrl": "http://127.0.0.1:4000/",
  "gameUrl": "http://127.0.0.1:4000/index-native.html",
  "gameUrlTs": "aHR0cDovLzEyNy4wLjAuMTo0MDAwL2luZGV4LW5hdGl2ZS5odG1s",
  "nativeRender": 1, "nativeUnzipJson": true,
  "update": "1", "forceBuild": "", "noForceBuild": "1", "forceUrl": "",
  "loginServer": "127.0.0.1", "loginPort": "4000",
  "tsLoginServer": "127.0.0.1", "loginKey": "verification",
  "loginWebPort": "4000",
  "productPrefix": "dblocal", "gameIcon": "", "gameLanguage": "id",
  "peiUrl": "", "wpeiurl": "", "wpei": false,
  "blackWhiteL": "", "blackWhiteLNetSegment": "", "currentNetIpUrl": "",
  "checkGoogleTicketUrl": "", "checkTime": "0",
  "clientParams": {}, "extra": {}, "productIdMap": {}
}
```

### 5.2 HTTP port 4000 (satu ServerSocket, multiplex dengan WS)

| Route | Respons |
|---|---|
| `GET /bs/db/android` dan `GET /lzceshi/native_setting/Android` | JSON §5.1 **di-XOR `"DragonBall"`** |
| `GET /game/base.version?rnd=` / `resource.version?rnd=` | `110` / `11220` (persis assets → client TIDAK download apa pun) |
| `GET /game/upgrade.json?rnd=` / `size.json?rnd=` | manifest versi-identik (no-op) |
| `GET /game/base.zip?v=` / `all.zip?v=` | 404 (tidak dibutuhkan bila versi identik) |
| `GET /static/**` | shell + JS (cadangan bila native minta lewat HTTP) |
| `GET /health`, `GET /dash` | cek hidup + dashboard owner |

### 5.3 Realtime — socket.io client **v2.1.2** (EIO=3, path `/socket.io`)

- Transport: websocket (RFC6455 via Java-WebSocket) + polling (fallback).
- `io.connect(url, {reconnectionAttempts:10})`; heartbeat ping/pong EIO;
  probe upgrade `2probe`/`3probe`; framing `40` connect, `42["event",data]`,
  `43` ack, `2`/`3` ping/pong.

**Handshake verify (persis):**
```
server  -> emit "verify" dengan challenge string <n>
client  -> o = (new TEA).encrypt(n, "verification")     // XXTEA JS klasik:
           delta 2654435769, rounds floor(6+52/len), strToLongs LE,
           key = Utf8("verification").slice(0,16)
client  -> emit "verify", o, ackFn
server  -> ack {ret: 0}                                  // ret!=0 = gagal login
```

**RPC:** `socket.emit("handler.process", {type, action, ...}, ackFn)`
— inventaris **419 action unik** tersimpan di **`tools/rpc-inventory.txt`**
(loginGame, GetServerList, enterGame, registChat, startBattle, getRank,
friendServerAction, getActivityDetail, checkBattleResult, getReward, …).

**Envelope ACK (persis):**
```json
{ "ret": 0, "data": "<STRING JSON>", "compress": 0|1,
  "serverTime": <ms>, "server0Time": <ms> }
```
- `data` adalah **STRING JSON** (client `JSON.parse` setelah proses).
- `compress` truthy → data = **LZString UTF-16** (`LZString.decompressFromUTF16`)
  — bukan zlib. Server boleh kirim compress=0.
- `serverTime`/`server0Time` → `ServerTime.updateServerTime(...)` (sinkron jam).
- `ret != 0` → jalur error client (`"38"` khusus force-update, `"70001"`
  reportBattleLog). Unknown route → balas `{ret:0,data:"{}"}` + **catat di
  `rpc.log`** (game jalan terus; kita isi handler belakangan).

**Channel notify (server push):** `socket.on("Notify", fn)` dengan
`{ret:"SUCCESS", data:"<string json>", compress}`; action seperti `"Kickout"`,
`"onlineBulletin"`.

**Urutan sesi (dari JS):**
1. connect → verify → `{ret:0}`
2. `{type:"User",action:"loginGame",userId,password,fromChannel,channelName,headImageUrl,nickName,subChannel}` → jawab `{loginToken,userId,nickName,channelCode,sign,...}`
3. `{type:"User",action:"GetServerList",userId,subChannel,channel}` → jawab `{serverList:[{serverId,name,"new":1,online:1,url,chaturl,dungeonurl,...}],history:[...]}` — client auto-pilih entri pertama `new:1 && online:1`
4. main-server connect (`serverItem.url`) → `{type:"user",action:"enterGame",loginToken,userId,serverId,version:"1.0",language,gameVersion}` → kirim **save pemain lengkap** (zona)
5. chat-server connect (`serverItem.chaturl`) → `{type:"user",action:"registChat",...}` (retry 3 dtk, max 15x) → room ids (`guildRoomId`,`teamChatRoomId`,`worldRoomId`,`teamDungeonChatRoom`)
6. dungeon-server connect (`serverItem.dungeonurl`) bila battle dungeon
7. selanjutnya: 419 action gameplay — handler bertahap, UnknownHandler aman

**Identitas offline:** tanpa SDK → `getSdkLoginInfo()` null → 无渠道登录 →
server buat/muat pemain `userId = "local-" + hash(ANDROID_ID)`; password
bebas (disimpan zona). Tidak butuh Google/Facebook sama sekali.

---

## 6. ALUR LENGKAP APLIKASI ↔ SERVER, SAMPAI TITIK loginServer [TERVERIFIKASI]

### 6.1 Tahap A — boot native (semua sebelum satu byte pun keluar masuk)

| t | Yang terjadi | Bukti |
|---|---|---|
| t0 | Proses `com.db.local` start | manifest |
| t1 | `Application.onCreate` → **kernel bind 127.0.0.1:4000 SINKRON (beberapa ms) → return** | smali patch 2 instruksi (§8) |
| t2 | `LaunchActivity`: baca `assets/config.properties` + `supplierconfig.json` → pilih supplier `"BS"` → baca `assets/config_BS.properties` EntryPoint1/2 (base64) | `const-string 'BS'`, `config_%s.properties` di `classes.dex` |
| t3 | HTTP GET EntryPoint1 (retry 5x, timeout `5*i*1000` naik) → response bytes | bytecode LaunchActivity |
| t4 | Response **di-XOR `"DragonBall"`** → JSON native_setting | `const-string 'DragonBall'` di `seventygenom->nationalcommunitymissing` |
| t5 | Parse setting: `jsonObject.getString("loginServer")`, `"tsLoginServer"`, `loginPort`, `loginWebPort` → SharedPreferences | `LaunchActivity->worktopichardtails` |
| t6 | `MainActivity`: `EgretNativeAndroid.initialize(gameUrl)` + fallback `gameUrlTs` (Base64) + param map (`clientParams`/`loginServer`/`mSdkParams`/`mGameUrl`/`mPreloadPath`) | `MainActivity->hardlinerspare` |
| t7 | Egret Native load shell **dari pack di assets** (`nativeRender=1`, `nativeUnzipJson`) — dekripsi `onepieceencrypts` on-device, TANPA internet | §3 |

### 6.2 Tahap B — dari shell JS sampai titik loginServer

| t | Yang terjadi | Bukti |
|---|---|---|
| t8 | JS `connectToLogin` (`main.min`, **satu-satunya referensi `getLoginServer` di seluruh JS**): `t = window.getLoginServer()` | `main.min_7eae4d6e.js` |
| t9 | Fallback JS bila `t` kosong: `resource/properties/serversetting.json` — **FILE INI TIDAK ADA** di pack & peta vvcc ⇒ fallback mustahil terpakai ⇒ alamat login server **pasti** berasal dari native_setting | grep penuh pack + vvcc |
| t10 | `window.getLoginServer()` = `loginServer:loginPort` dari setting JSON (kita isi `127.0.0.1:4000`) | §5.1 |
| t11 | socket.io client **v2.1.2** connect → engine.io v3: `GET/WS /socket.io/?EIO=3` → open `{sid}` → probe `2probe/3probe` → `40` connect | `socket.io.min_57ea9db7.js` |
| t12 | Handshake verify: server emit `"verify"` challenge → client XXTEA-encrypt dengan key `"verification"` → ack `{ret:0}` | JS TEA + `verification` |
| t13 | `loginGame` → `{loginToken,userId,...}` | §5.3 urutan sesi |
| t14 | `GetServerList` → `serverList[]` (`url`/`chaturl`/`dungeonurl` — kita isi semua `127.0.0.1:4000`) → auto-pilih `new:1 && online:1` | §5.3 |
| t15 | main-server connect → `enterGame`; chat-server → `registChat`; dungeon-server bila battle | §5.3 |

### 6.3 Tabel 1:1 — alamat ASLI vs LOCAL (per titik)

| Titik | ASLI (server mereka) | LOCAL (kita) | Sentuh APK? |
|---|---|---|---|
| EntryPoint config | `https://configus.sjmobilegame.com/bs/db/android` + fallback COS `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` | `http://127.0.0.1:4000/bs/db/android` (fallback juga) | **EDIT 1** |
| EntryPoint config (jalur alt) | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | `http://127.0.0.1:4000/lzceshi/native_setting/Android` | **EDIT 2** |
| Isi setting (XOR `DragonBall`) | host asli mereka | JSON §5.1 semua alamat `127.0.0.1:4000` | TIDAK (client sama) |
| `loginServer:loginPort` | host asli dari server asli | `127.0.0.1:4000` dari JSON kita | TIDAK |
| socket login/main/chat/dungeon | 4 host realtime asli | 1 kernel :4000, 4 sesi virtual | TIDAK |
| hot-update `upgrade.json`… | CDN asli | route versi-identik → nol download | TIDAK |

**Kesimpulan modifikasi minimalis: client TIDAK diubah sama sekali logikanya.
Yang berubah hanya ALAMAT (2 file properties) + 1 titik boot (2 instruksi
smali) + 1 dex tambahan (server).**

---

## 7. TOOLS SERVER MEREKA vs KITA — 1:1 PROTOKOL, BUKAN 1:1 SOFTWARE [TERVERIFIKASI]

Kita **tidak meniru software** mereka (nginx/Node/COS tidak ada di APK dan
tidak dibutuhkan). Kita meniru **protokol wire** yang mereka bicarakan ke
client — client tidak bisa membedakan.

| Layanan | Yang dipakai server ASLI (bukti) | Yang KITA pakai 1:1 | Kenapa setara |
|---|---|---|---|
| Config service | HTTP(S) GET, body JSON **XOR `DragonBall`** — `configus.sjmobilegame.com` + COS fallback (`config_BS.properties`) | route HTTP di kernel `s2.http`, body sama persis di-XOR `"DragonBall"` | client hanya GET + XOR-decode; asal host `127.0.0.1` |
| CDN hot-update | COS myqcloud (`upgrade.json`, `size.json`, `base.zip`, `all.zip`) | route `/game/*` versi-identik (`110`/`11220`) → nol download; 404 aman (fallback bawaan) | client bandingkan versi → sama → tidak download |
| Realtime server | **Node.js socket.io v2.x** — bukti: client v2.1.2 (`socket.io.min`), framing `40/42/43`, EIO=3, probe | Java murni: **RFC6455 + engine.io v3 + socket.io v2 wire**, reimplementasi 1:1, nol dependensi | wire identik; transport device Java-WebSocket RFC6455 |
| Handshake verify | XXTEA key `"verification"` (JS TEA klasik) | `s2.crypto.XXTEA` (LE, delta 2654435769) | algoritma sama, key sama |
| Kompresi ACK | LZString UTF-16 (`lz-string.min` di pack) | default `compress=0` + `s2.crypto` LZString UTF-16 siap | client menerima dua-duanya |
| DB pemain | backend asli (tidak terlihat dari client, tidak relevan) | zona JSON atomic (`players/<uid>.json`) | client hanya menerima JSON save |
| Transport WS di device | Java-WebSocket (RFC6455) tertanam runtime | kernel = server RFC6455 (handshake SHA-1+base64, mask, len 125/65536/64-bit) | standar sama |
| Toolchain build | Android SDK/aapt (pembuat APK asli) | **ECJ (javac 8) + d8 --min-api 21** → `classes2.dex`; **baksmali/smali** (2 instruksi); **python zipfile raw-copy**; **zipalign + apksigner** | hasil = APK valid, entri original byte-identik |

---

## 8. SISTEM 2 — MODIFIKASI MINIMALIS: 4 TITIK EDIT, SISANYA BYTE-IDENTIK [TERVERIFIKASI]

| # | File di APK | Edit | Nilai |
|---|---|---|---|
| 1 | `assets/config_BS.properties` | `EntryPoint1` & `EntryPoint1_debug` & `EntryPoint2`(+`_debug`) → base64 local | `http://127.0.0.1:4000/bs/db/android` = `aHR0cDovLzEyNy4wLjAuMTo0MDAwL2JzL2RiL2FuZHJvaWQ=` |
| 2 | `assets/config.properties` | `EntryPoint1` & `EntryPoint1_debug` → base64 local | `http://127.0.0.1:4000/lzceshi/native_setting/Android` = `aHR0cDovLzEyNy4wLjAuMTo0MDAwL2x6Y2VzaGkvbmF0aXZlX3NldHRpbmcvQW5kcm9pZA==` |
| 3 | `classes.dex` → `com.zhuhuan.game.Application.onCreate` | sisip 2 instruksi (baksmali → smali): `invoke-static {}, Ls2/Boot;->start()V` + `return-void` guard | boot server paling awal (§9) |
| 4 | entri baru `classes2.dex` | kernel `s2.*` (§9) | mini server |

- Manifest, `resources.arsc`, lib, asset game, JS, semua entri lain:
  **raw copy byte-identik** dari original (assemble zip, bukan rebuild gradle).
- TIDAK ada edit endpoint lain: semua alamat lanjutan (gameUrl, loginServer,
  serverList url/chaturl/dungeonurl, hot-update) mengalir dari **SATU JSON
  setting** yang kita kirim (§5.1) — jadi satu titik kontrol.
- Signature: keystore BARU (v1+v2) → **wajib uninstall APK lama sebelum
  pasang** (testkey original berbeda tanda tangan).

---

## 9. SISTEM 1 — SPESIFIKASI MINI SERVER + PRIORITAS BOOT

Bahasa: **Java 8 murni** (tanpa library eksternal) → `classes2.dex`
(ART auto-load multi-dex, minSdk 21 → tanpa edit manifest).

### 9.1 Komponen

| Komponen | Kelas | Isi |
|---|---|---|
| Boot | `s2.Boot` | dipanggil 2 instruksi dari `Application.onCreate`; bind `127.0.0.1:4000` **SINKRON** (instan) lalu return; idempotent (guard double-bind) |
| Core | `s2.core.ServerCore` | 1 `ServerSocket` :4000, thread pool, sniff: `Upgrade: websocket` → WS engine, selain itu HTTP engine |
| HTTP | `s2.http.*` | parser HTTP/1.1 (Content-Length, `Expect: 100-continue` untuk libcurl), router tabel, responder (json/xor/file) |
| WS | `s2.ws.*` | handshake RFC6455, frame codec, engine.io v3 (`0` open sid, `2/3` heartbeat, `2probe/3probe`), socket.io v2.1.2 (`40`, `42`, `43`) |
| Crypto | `s2.crypto.*` | XXTEA (LE) untuk verify `verification`; XOR `DragonBall`; LZString UTF-16 |
| Game | `s2.game.*` | `GameSession` (state machine 4 koneksi), `Dispatcher` (type+action), handler: Verify/LoginGame/GetServerList/EnterGame/RegistChat/Chat/Notify/Unknown |
| Zone | `s2.store.ZoneStore` | JSON atomic (tmp+rename) di `Android/data/com.db.local/server/`: `zone.json`, `players/<uid>.json`, `chat.json`, `kv.json`, `log/http.log`, `log/rpc.log` |
| Dash | `s2.dash.DashPage` | `/dash`: status, jumlah pemain, 100 baris rpc.log terakhir, tombol reload/wipe |
| Util | `s2.util.Json` | JSON writer/parser minimal (~200 baris, tanpa Gson) |

### 9.2 Prioritas boot — "server jalan PERTAMA sebelum apapun butuh dia"

```
t0  proses com.db.local dimulai
t1  Application.onCreate()            <- TITIK EKSEKUSI PALING AWAL di APK
t2  └─ s2.Boot.start(): bind 127.0.0.1:4000 SINKRON (ServerSocket bind < 50 ms)
t3  └─ onCreate() return (logika original lanjut UTUH)
t4  LaunchActivity.onCreate()         <- HTTP GET config ke 127.0.0.1:4000
                                         port SUDAH listening sejak t2 → langsung dilayani
t5  MainActivity → shell Egret (pack assets, tanpa internet)
t6  JS socket.io → 127.0.0.1:4000 (login/main/chat/dungeon)
```

- `Application.onCreate` dipanggil framework **sebelum activity apa pun** —
  ini pemicu boot paling awal yang bisa disisip tanpa mengubah logika app.
- Alternatif ContentProvider (boot lebih awal lagi) **DITOLAK** karena
  menuntut edit manifest — melanggar prinsip minimalis. Bind sinkron di t2
  sudah menutup seluruh race: permintaan jaringan pertama client baru terjadi
  di t4.
- Margin aman bawaan client: LaunchActivity sendiri retry 5x dengan timeout
  naik — jadi bahkan delay puluh ms pun tertelan.
- Boot `s2.Boot.start()` idempotent: kalau dipanggil dua kali (proses
  restart, crash recovery) → tidak dobel bind.
- Jika bind gagal (port dipakai proses lain — mustahil normal karena
  127.0.0.1 per-app sandbox): log ke zona, client retry menutup sisa risiko.

---

## 10. STRUKTUR FOLDER — ATURAN "1x REBUILD" [DESAIN FINAL]

### 10.1 Struktur repo (GitHub)

```
db-apk/
├── db-local(Original-DO NOT DELETE!).apk    [LFS] ACUAN TUNGGAL — TIDAK PERNAH diedit
├── log/com.Vanguard.twnthe.zip              [LFS] pendukung
├── README.md                                dokumen tunggal (timpa tiap update)
├── tools/                                   alat bedah (decrypt_onepiece.py, rpc-inventory.txt)
└── local/                                   FOLDER KERJA SATU-SATUNYA UNTUK REBUILD
    ├── kernel/
    │   ├── src/s2/…                          kode Java kernel (§9.1) — sumber kebenaran server
    │   └── build.sh                          ECJ + d8 --min-api 21 → classes2.dex
    ├── patch/
    │   ├── config.properties                 EDIT 2 (EntryPoint → 127.0.0.1:4000/lzceshi/…)
    │   ├── config_BS.properties              EDIT 1 (EntryPoint1/2 → 127.0.0.1:4000/bs/…)
    │   └── application-oncreate.patch        EDIT 3 (baksmali→sisip→smali, 2 instruksi)
    ├── build/
    │   ├── assemble.py                       original.apk + patch + classes2.dex → APK baru
    │   │                                     (zip raw-copy: entri original byte-identik)
    │   ├── sign.sh                           zipalign + apksigner v1+v2
    │   └── keystore/db-local.keystore        kunci rilis — sekali buat, dipakai SELAMANYA
    ├── rebuild.sh                            SATU PERINTAH: build.sh → assemble → sign
    └── dist/DB-Local-Offline.apk             OUTPUT 1x rebuild (hasil akhir)
```

### 10.2 Struktur di device (data server, di luar APK)

```
/sdcard/Android/data/com.db.local/server/
├── zone.json          serverlist, config gameplay, flag fitur — EDITABLE TANPA REBUILD
├── players/<uid>.json save per pemain
├── chat.json          riwayat chat room
├── kv.json            key-value bebas
└── log/
    ├── http.log       permintaan HTTP
    └── rpc.log        419 action + payload unknown (bahan menulis handler)
```

### 10.3 Matriks "kapan perlu rebuild?"

| Yang diubah | Cara | Rebuild APK? |
|---|---|---|
| Handler/gameplay baru (kode Java kernel) | edit `local/kernel/src` → `bash local/rebuild.sh` | **1x** (satu perintah, satu output) |
| Serverlist / config / flag | edit `zone.json` di device, atau tombol reload di `/dash` | **TIDAK** |
| Save pemain | otomatis oleh kernel | **TIDAK** |
| Log untuk analisis | baca `log/rpc.log` via `/dash` | **TIDAK** |
| Alamat endpoint | SELESAI SELAMANYA — sudah `127.0.0.1:4000`, tidak ada alamat lain | **TIDAK** |
| APK original | TIDAK PERNAH disentuh | — |

### 10.4 Kenapa struktur ini menjamin "cukup 1x Rebuild"

1. **Assemble selalu dari original.apk** (acuan tunggal) + patch dari
   `local/patch` — tidak ada "APK turunan dari turunan"; diff selalu bisa
   dihitung dari 4 titik edit §8.
2. **Semua yang sering berubah (data) ada DI LUAR APK** (zona device +
   `/dash`) — pekerjaan server harian tidak menyentuh APK sama sekali.
3. **Satu perintah** `local/rebuild.sh` = build dex → assemble → sign,
   atomik, satu output di `local/dist/`.
4. **Keystore tetap** → semua rebuild setelah yang pertama bisa
   install-over tanpa uninstall.
5. Kernel dirancang kecil & stabil (protokol §5 tidak berubah) —
   pekerjaan lanjutan = handler gameplay di `s2.game.*` + data zona,
   keduanya tercakup poin 1 dan 2.

---

## 11. CHECKLIST OFFLINE & VERIFIKASI LAPANGAN

- [x] Cleartext 127.0.0.1 diizinkan manifest [TERVERIFIKASI]
- [x] Endpoint setting dipindah via edit base64 (2 file) [TERVERIFIKASI]
- [x] `loginServer/loginPort` dari setting (bukan hardcode) [TERVERIFIKASI]
- [x] Hot-update 404/fallback aman; versi identik → nol download [TERVERIFIKASI]
- [x] Kontrak protokol penuh dari JS asli (verify TEA, ACK, LZString, 4 socket, 419 action) [TERVERIFIKASI §5]
- [x] Login tanpa SDK (无渠道登录) tersedia di client [TERVERIFIKASI JS]
- [x] Alur lengkap sampai loginServer dipetakan [TERVERIFIKASI §6]
- [x] Struktur folder 1x rebuild ditetapkan [DESAIN §10]
- [ ] Kernel `s2.*` classes2.dex (komponen §9.1)
- [ ] Rebuild (§8) + sanity diff entri zip
- [ ] Field test: mode pesawat → boot → login → masuk game → gameplay → /dash hidup → matikan app, buka lagi (save persist)

---

## 12. FILE DI REPO

| File | Keterangan |
|---|---|
| `db-local(Original-DO NOT DELETE!).apk` | ORIGINAL (LFS, 104.945.181 B, SHA-256 `b308c4…2218`) — ACUAN TUNGGAL, jangan diubah |
| `log/com.Vanguard.twnthe.zip` | arsip log/pendukung (LFS) — bukan acuan desain |
| `tools/decrypt_onepiece.py` | dekriptor `onepieceencrypts` (reproducible, §3) |
| `tools/rpc-inventory.txt` | 419 action RPC unik + frekuensi (dari JS asli) |
| `local/` | folder kerja rebuild (§10) — dibuat saat proposal disetujui |
| `README.md` | dokumen ini (timpa setiap update) |

**KEPUTUSAN OWNER (mengikat):** seluruh git history & artefak lama (kernel
lama, APK v01–v05, keystore lama) **VOID/CACAT** — tidak diulik, tidak
dipulihkan. Semua kernel ditulis baru dari nol mengikuti kontrak §5.

---

## 13. POIN DISKUSI (dibedah di chat, bukan di doc)

1. Satu port 4000 (HTTP+WS multiplex) vs dua port — saya rekomendasikan satu.
2. `compress` ACK: default 0 (plain) dulu, LZString menyusul — setuju?
3. Identitas pemain: `local-` + hash ANDROID_ID (auto guest) vs manual daftar
   nama di `/dash`?
4. 419 action: urutan implementasi mengikuti `rpc.log` dari device (capture-
   driven) + prioritas manapun yang owner minta duluan.
5. Detail URL `serverItem.url/chaturl/dungeonurl` (`http://` vs `ws://`) —
   divalidasi di field test, dua-duanya dilayani kernel.
6. Injeksi `window.getLoginServer` terjadi di native runtime (string
   dilindungi `libgavesec`, tidak terbaca statis) — tidak menjadi masalah:
   nilainya datang dari setting JSON yang kita kirim; kalau field test
   tunjukkan perlu, kernel juga menyajikan halaman `gameUrl` (bootstrap
   HTML) yang bisa mendefinisikan `window.getLoginServer` sendiri —
   dua-duanya sudah tercakup tanpa sentuh client.
7. Proposal ini menunggu persetujuan owner di chat: setelah disetujui,
   eksekusi mengikuti §8 (4 titik edit) + §9 (kernel) + §10 (struktur) —
   TANPA fase, satu paket kerja, rebuild dijalankan cukup 1x.

---

*Dokumen dibuat dari bedah langsung APK: manifest AXML, bytecode classes.dex
(androguard), hexdump asset, dekripsi pack `onepieceencrypts` (bukti di
tools/), dan JS game asli (main.min 4,7 MB, socket.io v2.1.2).*
