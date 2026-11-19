# db-apk — DB - Local (`com.db.local`) — 1 APK, 2 SISTEM, 100% OFFLINE

Dokumen tunggal proyek = **proposal kerja saya** (asisten). Semua klaim
bertanda **[TERVERIFIKASI]** dibuktikan dari isi APK + **backup data terpasang
asli** (`log/com.Vanguard.twnthe.zip` = data dir app asli, berisi cache HTTP
produksi) — bukan asumsi. Git history & artefak lama = **VOID**. Acuan tunggal:
APK original di repo ini. Rincian dibedah di chat; README = dokumen kerja.

---

## 1. IDENTITAS APK [TERVERIFIKASI]

| Properti | Nilai |
|---|---|
| Package | `com.db.local` (asli sebelum rename: `com.Vanguard.twnthe` / `com.guan.wangys`) |
| Label | `DB - Local` |
| Project name | `DragonBall` |
| versionCode / versionName | `1` / `1.0.1` |
| minSdk / targetSdk | 21 / 30 (compileSdk 23) |
| Ukuran | 104.945.181 bytes |
| SHA-256 | `b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218` |
| Signature | AOSP testkey (ganti keystore BARU saat rebuild) |
| Clear-text HTTP | **DIIZINKAN** — `usesCleartextTraffic="true"` + `network_security_config.xml` |
| Application / Launcher / Host | `com.zhuhuan.game.Application` / `LaunchActivity` / `MainActivity` |
| Native lib | `libegret.so` (Egret Native + libcurl + WebSocket JNI), `libgavesec.so` |
| WebSocket client device | Java-WebSocket (RFC6455) di `assets/runtime-dex.jar` |
| Game SDK | QuickGame, Facebook, Google Billing 3.0.2, GMS — semua OPSIONAL, gagal offline non-fatal |

---

## 2. ARSITEKTUR: 1 APK = 2 SISTEM

```
+------------------------------------------------------------------+
|                     SATU APK com.db.local                        |
|                                                                  |
|  SISTEM 2 (client, BEKU/PATEN)         SISTEM 1 (mini SERVER)    |
|  - logika TIDAK diubah                 - classes2.dex s2.* (baru)|
|  - 4 titik edit, semuanya ALAMAT       - boot di Application     |
|    -> semua jadi 127.0.0.1:4000          .onCreate (paling awal) |
|  - redirect lanjutan via setting JSON  - bind 127.0.0.1:4000     |
|                                        - HTTP + WS + SQLite      |
|                                        - dashboard owner /dash   |
+------------------------------------------------------------------+
```

---

## 3. ENKRIPSI RESOURCE `onepieceencrypts` — TERPECAHKAN [TERVERIFIKASI]

Ditemukan di bytecode `classes.dex` (kelas obfuscated
`cointhreat...customreading`, metode `nationalcommunitymissing([B)[B`).
Skrip pembuktian: **`tools/decrypt_onepiece.py`** (reproducible).

```
[0..16)   header bebas ("T01 " / acak)      — diabaikan
[16..32)  magic "onepieceencrypts"          — penanda
[32..48)  blok KEY  = XOR konstanta "onepiecedecryptk" -> key XXTEA per-file
[48..]    body      = XXTEA varian vendor: delta 0x9E3779B9, word LE,
            SATU PASS p=n-1..0, sum=delta tetap, MX standar, e=(sum>>>2)&3
output    byte LE per word, pad = byte terakhir dibuang
```

| Pack | Ukuran | Isi terdekripsi |
|---|---|---|
| `voyage_fancy_radius.bin` | 1.233.600 B | ZIP 161 entri: `index.html`, `manifest_ts.json`, `js/`, `resource/` |
| `pschent_full_tired.bin` | 9.428.744 B | ZIP 741 entri: `js/` 14 modul (**`main.min` 4,7 MB**, `socket.io.min` = client **v2.1.2**, `lz-string.min`, `md5.min`) + `resource/` |
| `vvcc.bin` / `vvcc_tc.bin` | 183.764 / 10.956 B | peta nama file obfuscated (1.944 + 139 key) |

> Seluruh kode client + kontrak protokol **di tangan**. Kontrak wire di §5–§7
> diambil dari JS asli + **frame produksi nyata** dari cache backup (§7) —
> bukan tebakan.

---

## 4. SISTEM 2 — CLIENT BEKU: SEMUA TITIK JARINGAN [TERVERIFIKASI]

### 4.1 Boot native (bytecode)

1. `LaunchActivity` pilih supplier **"BS"** → `config_BS.properties`
   EntryPoint1/2 (base64) → HTTP GET (retry 5x, timeout naik) → response
   **XOR key `"DragonBall"`** (`const-string 'DragonBall'` di
   `seventygenom->nationalcommunitymissing` + `LaunchActivity.<init>`)
   → JSON setting → SharedPreferences.
2. `MainActivity`: `EgretNativeAndroid.initialize(gameUrl)`; fallback
   `gameUrlTs` (Base64); bridge `jsbind`; param runtime (`hardlinerspare`):
   `clientParams`, `loginServer`, `mSdkParams`, `mGameUrl`, `mPreloadPath`.
3. Hot-update: lokal `base.version`=110 / `resource.version`=11220 vs
   `{updateUrl}/…`; gagal/404 = aman (resource bawaan).

### 4.2 Titik jaringan JS game (dari JS asli hasil dekripsi)

| Titik | Sumber alamat | Status offline |
|---|---|---|
| Setting JSON | EntryPoint `config*.properties` (1 edit base64) | dilayani kernel |
| `window.getLoginServer()` | nilai `loginServer:loginPort` dari native_setting — JS **tidak punya definisinya** (1 referensi = pemanggil) dan fallback `serversetting.json` **TIDAK ADA** di pack & peta vvcc ⇒ satu-satunya sumber = setting JSON kita | dilayani kernel |
| `window.getSdkLoginInfo()` | via bridge; `null` → **无渠道登录** (login tanpa channel) → `checkLastLogin()` | jalur offline |
| socket login-server | `getLoginServer()` | dilayani kernel |
| socket main-server | `serverItem.url` dari **ACK GetServerList** | dilayani kernel |
| socket chat-server | **bukan dari serverList!** — dari ACK `registChat`: `_chatServerUrl` (+ `_worldRoomId`, `_guildRoomId`, `_teamDungeonChatRoom`) | dilayani kernel |
| socket dungeon-server | **dari ACK team**: `_myTeamServerSocketUrl` (`myTeamServerSocketUrl`) | dilayani kernel |
| `./manifest.json?v=` | XHR relatif (shell lokal) | sudah di pack |
| error report battle | `http://log.sjflaregame.com:10000/error/battle/client` (dari JS) | route kernel |
| SDK (FB/Google/QuickGame/PEI/qsdk) | host SDK asli | mati non-fatal |

---

## 5. SERVER ASLI MEREKA — PETA BUKTI DARI BACKUP DATA TERPASANG [TERVERIFIKASI]

`log/com.Vanguard.twnthe.zip` = backup data dir app asli
(`com.Vanguard.twnthe`). Di dalamnya: cache HTTP WebView tersusun **per
hostname** + frame produksi nyata + prefs SDK. Ini peta infra asli mereka:

| # | Layanan | Host:Port ASLI | Bukti (dari backup/APK) |
|---|---|---|---|
| 1 | Config service | `https://configus.sjmobilegame.com/bs/db/android` (:443) + fallback COS `configus-1257066086.cos.na-siliconvalley.myqcloud.com` | `config_BS.properties` |
| 2 | Config alt | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | `config.properties` |
| 3 | CDN resource/shell | `http://pigres.pksilo.com` (:80) | 14.428 file cache webview per-host + localStorage prefs `pigres.pksilo.com.xml` |
| 4 | **Login server (socket.io)** | `https://login.pksilo.com:443` path `/socket.io/` | cache polling `/socket.io/` 1.792 file, frame engine.io nyata |
| 5 | **Main server (banyak instance)** | `https://s431-zd.pksilo.com:10300`, `s432-zd:10800`, `s433-zd:10700`, `s434-zd:10600`, `s435-zd:10700`, `s436-zd:10900`, … — **serverList asli berisi 432 entri** | ACK GetServerList 81 KB tertinggal di cache |
| 6 | Error/log battle | `http://log.sjflaregame.com:10000/error/battle/client` | URL di JS yang di-cache |
| 7 | SDK CS/log/policy (HAOCHENHUDONG) | `chat/log/cw.sdkapi.xgogaming.com`, `policy.cdnres.xgogaming.com` (:443) | prefs `PREFERENCE_HAOCHENHUDONG` |
| 8 | qsdk | `qsdk.t4game.com` | `assets/QHinfo` |
| 9 | Facebook / Google / GMS / Billing | host SDK | prefs SDK |

### 5.1 Tools server asli yang TERBUKTI dari frame cache produksi

| Alat | Bukti mentah |
|---|---|
| **nginx** | header cache: `Server: nginx`, `Connection: keep-alive` |
| Node socket.io v2 di belakang nginx | `Set-Cookie: io=<sid>; Path=/; HttpOnly; SameSite=Strict`, `Access-Control-Allow-Origin: *`, frame `0{sid...}`+`40`, ACK `430/431/433/434` |
| engine.io v3 params asli | open packet: `0{"sid":"cZ-ZtuTl7FSJ5Uj_N8w7","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}` |
| **Transport polling DIPAKAI NYATA** | seluruh cache login = polling (XHR), bukan WS — polling wajib didukung |
| Header custom backend | `Egret-Response-Time: <ms>`, `Egret-Save-Time: <ms>` |
| Kompresi | `Content-Encoding: deflate` (HTTP), ACK `compress:true` = **LZString UTF-16** (dilihat pola karakternya) |
| Perilaku unknown action | ACK nyata: `{"ret":902,"message":"unknown socket error"}` |
| Data record chat/mail | `{_record:[{_id,_time,_kind,_name,_image,_headEffect,_headBox,_content,_type,_param,_oriServerId,...}]}` |
| DB backend asli | TIDAK terlihat dari client (ada di datacenter) — yang terverifikasi hanya kontrak JSON |

---

## 6. KONTRAK WIRE 1:1 [TERVERIFIKASI DARI JS ASLI + FRAME PRODUKSI]

### 6.1 JSON native_setting (kernel kirim, XOR `DragonBall`)

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

### 6.2 HTTP port 4000 (satu ServerSocket, multiplex dengan WS)

| Route | Respons |
|---|---|
| `GET /bs/db/android`, `GET /lzceshi/native_setting/Android` | JSON §6.1 di-XOR `"DragonBall"` |
| `GET /game/base.version` / `resource.version` | `110` / `11220` (versi identik → nol download) |
| `GET /game/upgrade.json` / `size.json` | manifest versi-identik (no-op) |
| `GET /game/base.zip` / `all.zip` | 404 (aman, tak dibutuhkan bila versi identik) |
| `POST /error/battle/client` | 200 + catat log (pengganti `log.sjflaregame.com:10000`) |
| `GET /index-native.html`, `/static/**` | bootstrap HTML + statis (kernel juga definisikan `window.getLoginServer` di sini sebagai jaring pengaman) |
| `GET /health`, `/dash` | cek hidup + dashboard owner |

Header respons HTTP kernel (miror asli): `Access-Control-Allow-Origin: *`,
`Connection: keep-alive`, `Set-Cookie: io=<sid>` (untuk polling),
`Egret-Response-Time` / `Egret-Save-Time`.

### 6.3 Realtime — socket.io client v2.1.2 (EIO=3, path `/socket.io`)

- DUA transport wajib (asli dipakai nyata):
  **polling** (XHR GET/POST, frame `<len>:<payload>` beruntun,
  `Content-Type: text/plain`, cookie `io=`) dan
  **websocket** (RFC6455, upgrade `2probe/3probe`).
- Open packet kernel = identik asli:
  `0{"sid":"<24char>","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}`
- Heartbeat `2/3` per 25 s, timeout 5 s. Framing: `40` connect, `42["event",data]`,
  `43<id>[ack]`.

**Handshake verify (persis):**
```
server -> emit "verify" challenge <n>
client -> o = XXTEA.encrypt(n, "verification")   // delta 2654435769, LE
client -> emit "verify", o, ackFn
server -> ack {ret: 0}
```

**RPC:** `emit("handler.process", {type, action, ...}, ackFn)` —
**419 action unik** di `tools/rpc-inventory.txt`.

**Envelope ACK (bentuk produksi nyata):**
```json
{ "ret": 0, "data": "<STRING JSON>", "compress": false,
  "serverTime": <ms>, "server0Time": <ms> }
```
- `compress:true` → `data` = LZString UTF-16 (terbukti di cache). Kernel
  default `compress:false` (plain), switch di `zone.json`.
- Unknown action (mode `native`): ACK `{"ret":902,"message":"unknown socket error"}`
  (persis asli). Mode `silent` (default kernel): `{"ret":0,"data":"{}"}` —
  dipilih di `zone.json`, dicatat di `rpc.log`.

**Urutan sesi (dari JS + cache produksi):**
1. connect (polling dulu → upgrade WS) → verify → `{ret:0}`
2. `loginGame` `{userId,password,fromChannel,channelName,nickName,subChannel}` → `{loginToken,userId,nickName,channelCode,sign,...}`
3. `GetServerList` → produksi asli: `{serverList:[432 entri {serverId,new,online,hot,url,name,offlineReason}], history:[...], offlineReason}` — field terverifikasi persis; kernel kirim 1 entri `serverId:"1", new:true, online:true, url:"http://127.0.0.1:4000"` (jumlah/urutan atur di `zone.json`)
4. main-server connect (`serverItem.url`) → `enterGame` → server kirim save pemain + `myTeamServerSocketUrl:"http://127.0.0.1:4000"` (→ `dungeonurl`)
5. `registChat` (di main-server) → ACK **`_chatServerUrl:"http://127.0.0.1:4000"`, `_worldRoomId`, `_guildRoomId`, `_teamDungeonChatRoom`** → baru chat-server connect (retry 3 dtk, max 15x)
6. dungeon-server connect bila battle
7. 419 action gameplay — UnknownHandler aman (§6.3)

**Identitas offline:** 无渠道登录 → kernel buat/muat pemain
`userId = "local-" + hash(ANDROID_ID)` (pola sama dengan asli: backup
menyimpan `puIdMessage.txt` = UID device). Tanpa Google/Facebook.

---

## 7. PENGANTIAN 1:1 — ALAT ASLI vs ALAT KITA [RINCI]

| Asli (bukti §5) | Pengganti KITA | Detail implementasi |
|---|---|---|
| **nginx** (front semua host) | **`s2.http` — engine HTTP buatan sendiri di kernel** (java.net.ServerSocket) | HTTP/1.1: GET/POST, query string, Content-Length, `Expect: 100-continue` (libcurl), keep-alive, header miror (CORS *, io cookie, Egret-* headers). Bukan nginx, TIDAK bisa — tapi wire-nya identik, client tidak cek banner `Server:` |
| **Node.js + socket.io v2** (login/main/chat/dungeon) | **`s2.ws` — engine.io v3 + socket.io v2 di Java murni** | polling (XHR long-poll, frame `<len>:`) + websocket (RFC6455: handshake SHA-1/base64, mask, len 125/65536/64-bit) — DUA-DUANYA, bukti polling dipakai produksi |
| **432 main server** (s431…s436-zd, port 10300–10900) | **1 kernel :4000, peran virtual** | alamat dari serverList KITA sendiri → cukup 1 port; 4 peran (login/main/chat/dungeon) dibedakan di layer sesi dari packet pertama (`loginGame` vs `enterGame` vs `registChat` vs battle). Opsional `multiPort` di `zone.json` → 4001 login / 4002 main / 4003 chat / 4004 dungeon |
| **COS myqcloud** (hot-update) | route `/game/*` versi-identik | nol download, 404 aman |
| **log.sjflaregame.com:10000** | route `/error/battle/client` | terima POST, catat `log/error.log`, balas 200 |
| **SDK cluster xgogaming/t4game/FB/Google** | TIDAK digantikan | mati non-fatal; login lewat 无渠道登录 |
| **DB backend (tak terlihat; datacenter mereka)** | **SQLite bawaan Android** (`android.database.sqlite`, nol dependensi) | file `Android/data/com.db.local/server/db.sqlite` — schema §8.2 |
| **XXTEA `verification`** | `s2.crypto.Xxtea` | delta 2654435769, LE, round `6+52/n` |
| **LZString UTF-16** | `s2.crypto.LzString` | default OFF, switch `zone.json` |
| **XOR `DragonBall`** | `s2.crypto.Xor` | repeating-key, byte-identik |

**PORT MAP TEGAS (pertanyaan "port berapa"):**

| Server asli | Port asli | Local |
|---|---|---|
| configus / dragon (config) | 443 / 82 | **127.0.0.1:4000** |
| pigres (CDN shell) | 80 | tidak dibutuhkan (shell dari pack); `/static/**` cadangan |
| login.pksilo.com | 443 | **127.0.0.1:4000** (peran login) |
| s431–s436-zd (main, 432 instance) | 10300–10900 | **127.0.0.1:4000** (peran main) |
| chat server | dari ACK registChat | **127.0.0.1:4000** (peran chat) |
| dungeon server | dari ACK team | **127.0.0.1:4000** (peran dungeon) |
| log server | 10000 | **127.0.0.1:4000** route `/error/battle/client` |
| dash owner | — | **127.0.0.1:4000/dash** |

---

## 8. SISTEM 1 — MINI SERVER: KOMPONEN, BOOT PERTAMA, DATABASE

### 8.1 Komponen kernel (Java 8 murni, nol dependensi → `classes2.dex`)

| Komponen | Kelas | Isi |
|---|---|---|
| Boot | `s2.Boot` | dipanggil 2 instruksi dari `Application.onCreate`; bind :4000 **SINKRON**; idempotent |
| Core | `s2.core.ServerCore` | 1 ServerSocket :4000, thread pool, sniff transport |
| HTTP | `s2.http.HttpParser`, `HttpWriter`, `Router` + handler `Config`, `Hot`, `Static`, `ErrorReport`, `Dash` | HTTP/1.1 + routing |
| WS | `s2.ws.WsHandshake`, `WsFrame`, `EngineIo` (polling+WS), `SocketIo` | engine.io v3 + socket.io v2 |
| Crypto | `s2.crypto.Xxtea`, `Xor`, `LzString` | protokol |
| Game | `s2.game.GameSession`, `Dispatcher`, handler `Verify`, `User` (loginGame/GetServerList/enterGame), `Chat` (registChat/room), `Dungeon`, `Notify`, `Unknown` | state machine 4 peran |
| Store | `s2.store.Db` (SQLite), `Zone` (zone.json) | persist |
| Dash | `s2.dash.DashPage` | status + log + reload/wipe |
| Util | `s2.util.Json` | parser/writer JSON minimal |

### 8.2 DATABASE — SQLite (bawaan Android, nol dependensi)

File: `Android/data/com.db.local/server/db.sqlite`

```sql
CREATE TABLE players(uid TEXT PRIMARY KEY, name TEXT, data TEXT, updated INTEGER);
  -- data = save JSON penuh persis kontrak enterGame client
CREATE TABLE kv(k TEXT PRIMARY KEY, v TEXT);          -- state dunia, flag, counter
CREATE TABLE chat(room TEXT, ts INTEGER, sender TEXT, msg TEXT);
CREATE TABLE records(kind INTEGER, ts INTEGER, data TEXT);  -- _record: mail/bulletin
CREATE TABLE serverlist(serverId TEXT PRIMARY KEY, name TEXT,
  new INTEGER, online INTEGER, hot INTEGER, offlineReason TEXT);
  -- url TIDAK disimpan: kernel selalu bangun "http://127.0.0.1:4000"
```

- `zone.json` (teks, owner-edit): konfigurasi server (serverlist seed, jumlah
  entri serverList, mode unknownAction, compress, multiPort).
- Log teks: `log/http.log`, `log/rpc.log`, `log/boot.log`, `log/error.log`.
- Backup = copy `db.sqlite` + `zone.json`. Tanpa MySQL/Redis/mesh apapun.

### 8.3 Prioritas boot — "server jalan PERTAMA"

```
t0 proses com.db.local mulai
t1 Application.onCreate()                 <- titik eksekusi paling awal di APK
t2 └─ s2.Boot.start(): bind 127.0.0.1:4000 SINKRON (< 50 ms)
t3 └─ onCreate() return (logika original utuh)
t4 LaunchActivity.onCreate()              <- GET config ke 127.0.0.1:4000 → SUDAH dilayani
t5 MainActivity → shell Egret (pack assets, tanpa internet)
t6 JS socket.io → 127.0.0.1:4000 (polling → upgrade WS)
```

- `Application.onCreate` dipanggil framework **sebelum activity apa pun** —
  titik boot paling awal tanpa edit manifest (ContentProvider DITOLAK:
  melanggar minimalis).
- Margin aman bawaan client: LaunchActivity retry 5x, timeout naik.
- `Boot.start()` idempotent; gagal bind → log zona, client retry menutup risiko.

---

## 9. SISTEM 2 — MODIFIKASI MINIMALIS: 4 TITIK EDIT, SISANYA BYTE-IDENTIK

| # | File | Edit | Nilai |
|---|---|---|---|
| 1 | `assets/config_BS.properties` | EntryPoint1/2 (+`_debug`) → base64 local | `aHR0cDovLzEyNy4wLjAuMTo0MDAwL2JzL2RiL2FuZHJvaWQ=` |
| 2 | `assets/config.properties` | EntryPoint1 (+`_debug`) → base64 local | `aHR0cDovLzEyNy4wLjAuMTo0MDAwL2x6Y2VzaGkvbmF0aXZlX3NldHRpbmcvQW5kcm9pZA==` |
| 3 | `classes.dex` → `Application.onCreate` | sisip `invoke-static {}, Ls2/Boot;->start()V` (baksmali→smali) | boot server |
| 4 | entri baru `classes2.dex` | kernel `s2.*` | mini server |

Manifest, arsc, lib, asset, JS: **raw copy byte-identik**. Semua alamat
lanjutan (gameUrl, loginServer, serverList url, `_chatServerUrl`,
`myTeamServerSocketUrl`) mengalir dari SATU JSON setting + ACK kernel —
satu titik kontrol. Keystore BARU → install pertama wajib uninstall lama;
setelah itu semua rebuild bisa install-over.

---

## 10. STRUKTUR FOLDER — ATURAN "1x REBUILD" [DESAIN FINAL]

### 10.1 Repo (GitHub)

```
db-apk/
├── db-local(Original-DO NOT DELETE!).apk    [LFS] ACUAN TUNGGAL — TIDAK PERNAH diedit
├── log/com.Vanguard.twnthe.zip              [LFS] backup data asli (bukti server §5)
├── README.md                                dokumen tunggal
├── tools/
│   ├── decrypt_onepiece.py                  dekriptor pack
│   └── rpc-inventory.txt                    419 action RPC
└── local/                                   FOLDER KERJA REBUILD — SATU-SATUNYA
    ├── rebuild.sh                           SATU PERINTAH: dex → assemble → sign
    ├── kernel/
    │   ├── build.sh                         ECJ + d8 --min-api 21 → classes2.dex
    │   └── src/s2/
    │       ├── Boot.java                    entry boot (Application.onCreate)
    │       ├── core/
    │       │   ├── ServerCore.java          ServerSocket :4000 + thread pool + sniff
    │       │   └── Session.java             abstraksi koneksi (HTTP/WS/polling)
    │       ├── http/
    │       │   ├── HttpParser.java          request parser (CL, 100-continue, keep-alive)
    │       │   ├── HttpWriter.java          response + header miror (CORS, io, Egret-*)
    │       │   ├── Router.java              tabel route
    │       │   ├── HConfig.java             native_setting (XOR DragonBall)
    │       │   ├── HHo.java                 /game/* versi-identik
    │       │   ├── HStatic.java             /index-native.html, /static/**
    │       │   ├── HError.java              /error/battle/client
    │       │   └── HDash.java               /dash + /health
    │       ├── ws/
    │       │   ├── WsHandshake.java         RFC6455 accept
    │       │   ├── WsFrame.java             frame codec (mask, len 125/65536/64bit)
    │       │   ├── EngineIo.java            polling + websocket, open/heartbeat/probe
    │       │   └── SocketIo.java            40/42/43 framing, ack id
    │       ├── crypto/
    │       │   ├── Xxtea.java               verify "verification"
    │       │   ├── Xor.java                 DragonBall
    │       │   └── LzString.java            UTF-16 compress (opsional)
    │       ├── game/
    │       │   ├── GameSession.java         state machine peran login/main/chat/dungeon
    │       │   ├── Dispatcher.java          type+action → handler
    │       │   ├── HVerify.java             challenge XXTEA
    │       │   ├── HUser.java               loginGame, GetServerList, enterGame
    │       │   ├── HChat.java               registChat (_chatServerUrl...), room chat
    │       │   ├── HDungeon.java            team, _myTeamServerSocketUrl, battle
    │       │   ├── HNotify.java             push Notify (Kickout, onlineBulletin)
    │       │   └── HUnknown.java            ret:0 silent / ret:902 native (zone.json)
    │       ├── store/
    │       │   ├── Db.java                  SQLite (schema §8.2)
    │       │   └── Zone.java                zone.json load/save atomic
    │       ├── dash/
    │       │   └── DashPage.java            dashboard owner
    │       └── util/
    │           └── Json.java                JSON minimal
    ├── patch/
    │   ├── config.properties                EDIT 2
    │   ├── config_BS.properties             EDIT 1
    │   └── application-oncreate.patch       EDIT 3 (2 instruksi smali)
    ├── build/
    │   ├── assemble.py                      original.apk + patch + classes2.dex → APK baru
    │   ├── sign.sh                          zipalign + apksigner v1+v2
    │   └── keystore/db-local.keystore       kunci rilis — sekali buat, SELAMANYA
    └── dist/
        └── DB-Local-Offline.apk             OUTPUT 1x rebuild
```

### 10.2 Device

```
/sdcard/Android/data/com.db.local/server/     ← zona server (LUAR APK)
├── zone.json                                  config owner-editable
├── db.sqlite                                  DATABASE (schema §8.2)
└── log/
    ├── boot.log                               timeline boot
    ├── http.log                               request HTTP
    ├── rpc.log                                419 action + payload unknown (bahan handler)
    └── error.log                              error report client (/error/battle/client)
```

### 10.3 Matriks "kapan perlu rebuild?"

| Yang diubah | Cara | Rebuild? |
|---|---|---|
| Handler/gameplay baru (Java) | edit `local/kernel/src` → `bash local/rebuild.sh` | **1x** |
| Serverlist/config/flag | edit `zone.json` atau tombol `/dash` | **TIDAK** |
| Save pemain/chat/record | otomatis kernel → `db.sqlite` | **TIDAK** |
| Analisis log | `/dash` atau baca `log/rpc.log` | **TIDAK** |
| Alamat endpoint | SELESAI SELAMANYA (`127.0.0.1:4000`) | **TIDAK** |
| APK original | TIDAK PERNAH disentuh | — |

Kenapa menjamin 1x rebuild: assemble **selalu dari original.apk** + patch
terkumpul di `local/patch` (tidak ada APK turunan dari turunan); data yang
sering berubah ada DI LUAR APK; satu perintah `rebuild.sh` atomik; keystore
tetap → install-over.

---

## 11. CHECKLIST OFFLINE & VERIFIKASI LAPANGAN

- [x] Cleartext 127.0.0.1 diizinkan manifest [TERVERIFIKASI]
- [x] Endpoint setting dipindah via edit base64 (2 file) [TERVERIFIKASI]
- [x] `loginServer/loginPort` dari setting; JS tanpa fallback [TERVERIFIKASI]
- [x] Hot-update 404/fallback aman; versi identik → nol download [TERVERIFIKASI]
- [x] Kontrak protokol dari JS asli + FRAME PRODUKSI (verify TEA, ACK, LZString, polling, serverList 432 entri, _chatServerUrl, 902 unknown) [TERVERIFIKASI §6]
- [x] Peta server asli lengkap dari backup (nginx, port, host, engine params) [TERVERIFIKASI §5]
- [x] Login tanpa SDK (无渠道登录) [TERVERIFIKASI JS]
- [x] Struktur folder 1x rebuild + schema SQLite [DESAIN §10, §8.2]
- [ ] Kernel `s2.*` classes2.dex (komponen §8.1)
- [ ] Rebuild (§9) + sanity diff entri zip
- [ ] Field test: mode pesawat → boot → login → masuk game → gameplay → /dash → restart app (save persist)

---

## 12. FILE DI REPO

| File | Keterangan |
|---|---|
| `db-local(Original-DO NOT DELETE!).apk` | ORIGINAL (LFS, 104.945.181 B) — ACUAN TUNGGAL |
| `log/com.Vanguard.twnthe.zip` | backup data dir app asli (154.891.608 B) — BUKTI peta server §5 |
| `tools/decrypt_onepiece.py` | dekriptor `onepieceencrypts` |
| `tools/rpc-inventory.txt` | 419 action RPC |
| `local/` | folder kerja rebuild (§10) — dibuat saat proposal disetujui |
| `README.md` | dokumen ini |

**KEPUTUSAN OWNER (mengikat):** git history & artefak lama **VOID** — semua
kernel ditulis baru dari nol mengikuti kontrak §6.

---

## 13. POIN DISKUSI (dibedah di chat)

1. Port tunggal 4000 (4 peran virtual) vs multiPort 4001–4004 — default
   tunggal, switch `zone.json`.
2. Unknown action: default `silent` `{ret:0,data:"{}"}` vs `native`
   `{ret:902,...}` — switch `zone.json`, dicatat rpc.log.
3. compress ACK: default `false` (plain), switch aktif LZString.
4. serverList: kirim 1 entri (Server 1) atau N entri (miror "Server 432"
   style) — atur di zone.json.
5. Identitas `local-` + hash ANDROID_ID vs daftar manual di /dash.
6. Urutan implementasi 419 action: capture-driven dari rpc.log + prioritas
   owner.
7. Proposal menunggu persetujuan owner di chat → eksekusi satu paket
   (§9 edit + §8 kernel + §10 struktur), rebuild cukup 1x.

---

*Dibuat dari bedah langsung APK (manifest AXML, bytecode androguard, hexdump
asset, dekripsi pack `onepieceencrypts`, JS asli) DAN backup data terpasang
asli (cache HTTP produksi: nginx header, frame engine.io/socket.io nyata,
serverList 432 entri, ACK compress, record chat).*
