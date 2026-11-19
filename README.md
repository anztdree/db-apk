# db-apk — DB - Local (`com.db.local`) — 1 APK, 2 SISTEM, 100% OFFLINE

Dokumen tunggal proyek. Semua klaim bertanda **[TERVERIFIKASI]** dibuktikan
dari isi APK itu sendiri (manifest, bytecode `classes.dex`, asset, native lib,
dan **JS game asli hasil dekripsi pack**), bukan asumsi. Git history dan
artefak lama = **VOID** (keputusan owner) — tidak dipulihkan, tidak dijadikan
acuan. Acuan tunggal: file APK original di repo ini.

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
| Native lib | `libegret.so` (Egret Native + libcurl + WebSocket JNI), `libgavesec.so` |
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
|  - 1 edit: EntryPoint -> 127.0.0.1     - boot dari Application   |
|  - semua redirect via setting JSON       (smali patch, 2 instr.) |
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

1. `LaunchActivity` pilih supplier **"BS"** → baca `config_BS.properties`
   EntryPoint1/2 (base64) → HTTP GET (retry 5x, timeout naik) → response
   **di-XOR key `"DragonBall"`** → parse JSON setting → SharedPreferences.
2. `MainActivity`: `EgretNativeAndroid.initialize(gameUrl)`; fallback
   `gameUrlTs` (Base64); bridge `jsbind` (startGame, changeView,
   changeLanguage, refresh, pei, giveLike, contact, report2Third, openURL,
   switchAccount, userCenter, startTsGame, sendLog).
3. Hot-update: bandingkan lokal `base.version`=110 / `resource.version`=11220
   dengan `{updateUrl}/…` (`upgrade.json`, `size.json`, `base.zip`, `all.zip`);
   gagal/404 = aman (pakai resource bawaan APK).

### 4.2 Titik jaringan JS game (dari JS asli, hasil dekripsi §3)

| Titik | Sumber alamat | Kebutuhan |
|---|---|---|
| Setting JSON | EntryPoint di `config*.properties` (satu edit) | JSON field §5.1, XOR `DragonBall` |
| `window.getLoginServer()` | **di-inject native** dari setting `loginServer:loginPort` | URL socket login; fallback bila kosong: `resource/properties/serversetting.json` `{loginserver}` (kita sediakan lewat zona) |
| `window.getSdkLoginInfo()` | di-inject native (SDK) | bila `null` → JS jatuh ke **“无渠道登录” (login tanpa channel)** → `isNoSdk=true` → `checkLastLogin()` — jalur login offline, TANPA Google/Facebook |
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

## 6. SISTEM 1 — SPESIFIKASI MINI SERVER (DITULIS BARU, NOL DEPENDENSI)

Bahasa: **Java 8 murni** (tanpa library eksternal) → `classes2.dex`
(ART auto-load multi-dex, minSdk 21 → tanpa edit manifest).

| Komponen | Kelas | Isi |
|---|---|---|
| Boot | `s2.Boot` | dipanggil 2 instruksi dari `Application.onCreate` (smali patch); bind `127.0.0.1:4000` **sinkron** (instan) lalu return → nol race dengan LaunchActivity |
| Core | `s2.core.ServerCore` | 1 `ServerSocket` :4000, thread pool, sniff: `Upgrade: websocket` → WS engine, selain itu HTTP engine |
| HTTP | `s2.http.*` | parser HTTP/1.1 (Content-Length, `Expect: 100-continue` untuk libcurl), router tabel, responder (json/xor/file) |
| WS | `s2.ws.*` | handshake RFC6455 (SHA-1+base64 accept), frame codec (mask client, unmask, len 125/65536/64-bit), engine.io v3 (`0` open sid, `2/3` heartbeat, `2probe/3probe`), socket.io v2.1.2 (`40`, `42`, `43`) |
| Crypto | `s2.crypto.*` | XXTEA (LE, 32-bit) untuk verify `verification`; XOR `DragonBall`; LZString UTF-16 (compress ACK) |
| Game | `s2.game.*` | `GameSession` (state machine 4 koneksi), `Dispatcher` (type+action), handler: Verify/LoginGame/GetServerList/EnterGame/RegistChat/Chat/Notify/Unknown |
| Zone | `s2.store.ZoneStore` | JSON atomic (tmp+rename) di `Android/data/com.db.local/server/`: `zone.json` (serverlist, config), `players/<uid>.json`, `chat.json`, `kv.json`, `log/http.log`, `log/rpc.log` |
| Dash | `s2.dash.DashPage` | `/dash`: status, jumlah pemain, 100 baris rpc.log terakhir, tombol reload/wipe |
| Util | `s2.util.Json` | JSON writer/parser minimal (~200 baris, tanpa Gson) |

---

## 7. EDIT KE APK — DAFTAR PERSIS (SISTEM 2 DISENTUH MINIM)

1. `assets/config_BS.properties` EntryPoint1/EntryPoint2 →
   base64 `http://127.0.0.1:4000/bs/db/android` =
   `aHR0cDovLzEyNy4wLjAuMTo0MDAwL2JzL2RiL2FuZHJvaWQ=`
2. `assets/config.properties` EntryPoint1 →
   base64 `http://127.0.0.1:4000/lzceshi/native_setting/Android` =
   `aHR0cDovLzEyNy4wLjAuMTo0MDAwL2x6Y2VzaGkvbmF0aXZlX3NldHRpbmcvQW5kcm9pZA==`
3. Smali patch `com.zhuhuan.game.Application.onCreate` → sisip
   `invoke-static {}, Ls2/Boot;->start()V` (2 instruksi, baksmali/smali).
4. Tambah entri `classes2.dex` (kernel `s2.*`).
5. **Sisanya byte-identik** → assemble zip (entri raw copy) → zipalign →
   sign v1+v2 (keystore BARU) → **wajib uninstall APK lama** sebelum pasang.

---

## 8. TOOLCHAIN BUILD (1:1 FUNGSI DENGAN TOOL ASLI)

| Peran | Dunia asli | Kita |
|---|---|---|
| Config service | `configus.sjmobilegame.com` + COS backup | `s2.http` route native_setting (XOR `DragonBall`) |
| CDN patch | host update asli | route `/game/*` versi-identik (no-op) |
| Login/game/chat/dungeon server | 4 host realtime | 1 kernel :4000, 4 `GameSession` virtual |
| Kripto | XXTEA+`verification`, XOR `DragonBall`, LZString | `s2.crypto` setia protokol JS asli |
| DB pemain | backend asli | zona JSON atomic |
| Kompilasi kernel | Android SDK | **ECJ (javac 8) + d8 --min-api 21** → classes2.dex |
| Patch dex | — | **baksmali/smali** (2 instruksi di Application) |
| Assemble | aapt/gradle | **python zipfile raw-copy** (entri original byte-identik) |
| Align/sign | — | **zipalign + apksigner** (uber-apk-signer; v1+v2) |
| Verifikasi akhir | — | androguard parse manifest/dex APK hasil |

---

## 9. CHECKLIST OFFLINE & VERIFIKASI LAPANGAN

- [x] Cleartext 127.0.0.1 diizinkan manifest [TERVERIFIKASI]
- [x] Endpoint setting dipindah via 1 edit base64 [TERVERIFIKASI]
- [x] `loginServer/loginPort` dari setting (bukan hardcode) [TERVERIFIKASI]
- [x] Hot-update 404/fallback aman; versi identik → nol download [TERVERIFIKASI]
- [x] Kontrak protokol penuh dari JS asli (verify TEA, ACK, LZString, 4 socket, 419 action) [TERVERIFIKASI §5]
- [x] Login tanpa SDK (无渠道登录) tersedia di client [TERVERIFIKASI JS]
- [ ] Kernel `s2.*` classes2.dex (komponen §6)
- [ ] Rebuild v06 (§7) + sanity diff entri zip
- [ ] Field test: mode pesawat → boot → login → masuk game → gameplay → /dash hidup → matikan app, buka lagi (save persist)

---

## 10. FILE DI REPO

| File | Keterangan |
|---|---|
| `db-local(Original-DO NOT DELETE!).apk` | ORIGINAL (LFS, 104.945.181 B, SHA-256 `b308c4…2218`) — ACUAN TUNGGAL, jangan diubah |
| `log/com.Vanguard.twnthe.zip` | arsip log/pendukung (LFS) — bukan acuan desain |
| `tools/decrypt_onepiece.py` | dekriptor `onepieceencrypts` (reproducible, §3) |
| `tools/rpc-inventory.txt` | 419 action RPC unik + frekuensi (dari JS asli) |
| `README.md` | dokumen ini (timpa setiap update) |

**KEPUTUSAN OWNER (mengikat):** seluruh git history & artefak lama (kernel
lama, APK v01–v05, keystore lama) **VOID/CACAT** — tidak diulik, tidak
dipulihkan. Semua kernel ditulis baru dari nol mengikuti kontrak §5.

---

## 11. POIN DISKUSI (dibedah di chat, bukan di doc)

1. Satu port 4000 (HTTP+WS multiplex) vs dua port — saya rekomendasikan satu.
2. `compress` ACK: default 0 (plain) dulu, LZString menyusul — setuju?
3. Identitas pemain: `local-` + hash ANDROID_ID (auto guest) vs manual daftar
   nama di `/dash`?
4. 419 action: urutan implementasi mengikuti `rpc.log` dari device (capture-
   driven) + prioritas manapun yang owner minta duluan.
5. Detail URL `serverItem.url/chaturl/dungeonurl` (`http://` vs `ws://`) —
   divalidasi di field test, dua-duanya dilayani kernel.
6. Push README ini = commit baru di repo; reviu di chat ini.

---

*Dokumen dibuat dari bedah langsung APK: manifest AXML, bytecode classes.dex
(androguard), hexdump asset, dekripsi pack `onepieceencrypts` (bukti di
tools/), dan JS game asli (main.min 4,7 MB, socket.io v2.1.2).*
