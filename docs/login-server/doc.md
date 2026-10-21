# LOGINSERVER — DOC TUNGGAL (tahap loginserver = versi 1.0.3)

> **Ini satu-satunya doc loginserver — selalu di-upgrade file ini, TIDAK pernah dibuat
> doc/folder baru atau per-versi.** Folder `config/` di samping file ini = satu-satunya
> config loginserver yang benar.
>
> **STATUS: CONFIG & KONTRAK TERVERIFIKASI — IMPLEMENTASI LAMA DITOLAK.**
> Rilis `v1.0.3` yang lama dinyatakan TIDAK SAH oleh pemilik (dibangun dari doc, bukan
> dari bedah asli). Doc ini + `config/` ditulis ulang dari **dua sumber asli**:
> (1) bedah byte APK original & server config asli, (2) **probe langsung ke server
> asli yang MASIH HIDUP** `login.popoh5.com:610` (2026-10-21). Build baru belum
> dieksekusi — rencana ada di bagian 9.
>
> **ATURAN NAMA VERSI (perintah pemilik):** kerja yang belum terbukti jalan stabil
> TIDAK boleh diberi nama versi baru (tidak ada "LoginServer v2" atau sejenisnya) —
> yang belum teruji tetap memakai **versi default tahap ini = 1.0.3**. Versi hanya
> berubah bila pemilik menetapkannya SETELAH rilis terbukti stabil jalan.
>
> Tahap sebelumnya (SDK) = 1.0.2 — doc-nya terpisah:
> [`docs/sdk/CONFIG-SDK-ASLI.md`](../sdk/CONFIG-SDK-ASLI.md).
> Penanda `VERSION` di mirror (`1.0.6-login`) = pemicu re-sync internal antar-install
> SAJA — BUKAN nomor versi produk.

---

## 1. POSISI TAHAP INI (peta rantai)

```
SDK lokal (1.0.2, port 28110)  ← SUDAH LOKAL
  └─ gerbang loginchecknative (code:0, 10 field) ← SUDAH LOKAL
       └─ native MainActivity → payload event "startGame" ke JS
            ├─ loginServer = holder.cointhreat()   ← ALAMAT LOGINSERVER (bagian 2)
            ├─ thirdParams = jawaban gerbang utuh
            └─ version/versionConfig/language/clientParams dari native+bin
                 └─ JS Egret (main.min.js) → TSSocketClient("login-server", verifyEnable=FALSE)
                      └─ LOGINSERVER LOKAL (TAHAP INI, port 28112)
                           ├─ GetServerList → pemain pilih server, tombol START aktif
                           ├─ LoginAnnounce → pengumuman layar login
                           └─ SaveHistory / SaveLanguage / SaveUserEnterInfo / loginGame
                                └─ [TAHAP BERIKUTNYA = DUNIA GAME, port 28113]
                                   mainClient ("main-server", verifyEnable=TRUE)
                                   enterGame + userData + registChat + gameplay
```

Yang TIDAK disentuh tahap ini: `tsLoginServer`/`tsUrl` (hanya jalur mode 2), telemetry
FB/AppsFlyer/ThinkingData (noise SDK), pembayaran (fase C), chat/dungeon server.

### 1a. AUDIT BASIS RILIS v1.0.2 (bedah isi APK rilis `v1.0.2`, 2026-10-21)

Basis = `DB-Local-1.0.2.apk` dari release `v1.0.2` — 125.280.859 B, sha256
`b32352ce35d0006111a6a1ba766b61881ccd1e65399cf558ca62abf6be585300` (cocok doc SDK).
Hasil bedah isi dengan lensa loginserver:

| Item di APK 1.0.2 | Kondisi terverifikasi | Konsekuensi untuk 1.0.3 |
|---|---|---|
| `bs/js/main.min_777039fc.js` (4.914.149 B) + `bs/index-native.html` (10.792 B) | sha256 IDENTIK byte-per-byte dengan CDN asli (`2a14dca3…`, `59dba3e4…`) | semua kontrak JS di doc ini berlaku apa adanya — tidak ada yang perlu disentuh |
| `classes.dex` (hook `MiniServer.start` di `LaunchActivity.onCreate`) | utuh, warisan 1.0.1 | satu-satunya titik membangunkan server lokal — TIDAK diubah |
| Gerbang `loginchecknative` | SUDAH LOKAL — bin `loginServer http://127.0.0.1` + `loginWebPort 28110`; template generator `code:0` di mirror (terbukti HIJAU di perangkat) | tidak perlu diubah |
| Bin mirror (686 B) — **`loginPort`** | **masih 610** → `cointhreat()` = `http://127.0.0.1:610`, tidak ada yang mendengarkan → koneksi login-server JS gagal (ini BATAS 1.0.2) | SATU field redirect tersisa: `loginPort → 28112` (bin `config/` 688 B — 686+2 byte konsisten dgn panjang "28112") |
| `serversetting.json` / `clientversion.json` / `sound_click.mp3` | TIDAK ADA di mirror (42 entri: bs/ + qsdk/ + upgrade/ + generator.json + VERSION) | +3 file wajib ditambah — isi sudah ada di `config/` |
| `classes2.dex` (35.056 B) | MiniServer + generator + DB akun + 8 endpoint akun/keamanan; TIDAK ada `LoginServer`/`28112`/socket listener/`gamehistory` | +1 kelas `LoginServer` (rencana bagian 9) — tidak ada yang dibongkar |
| `socket.io.min_57ea9db7.js` | ada di mirror (manifest group "game") | klien socket.io v2 JS siap konek ke 28112 tanpa perubahan |

**Kesimpulan audit: tidak ada penghalang.** Build 1.0.3 = basis 1.0.2 + redirect
`loginPort` (1 field bin) + kelas `LoginServer` (28112) + 3 file web-layer.
Setelah itu rantai §1 menjadi 100% lokal sampai batas tahap ini (START → mainClient
28113 = tahap dunia game, gagal konek = jalur error normal klien).

---

## 2. SUMBER ALAMAT LOGINSERVER — DUA JALUR (bukti main.min.js + index-native.html asli)

Jalur 1 (utama, native): `MainActivity` menaruh `holder.cointhreat()` ke payload
`startGame` → `index-native.html`: `loginServer = json.loginServer` → JS
`getLoginServer()` mengembalikannya → `connectToServer(url)`.

Jalur 2 (fallback, TERBUKTI DIPANGGIL di perangkat — logcat 1.0.1 menunjukkan request
`serversetting.json` yang 404): bila bridge kosong, JS fetch
`resource/properties/serversetting.json?v=<acak>` dan memakai `n.loginserver`.

```
getLoginServer = function(){ return loginServer; }         // index-native.html asli
// main.min.js: var n=TSBrowser.executeFunction("getLoginServer");
//   if(n) connectToServer(n) else fetch("resource/properties/serversetting.json")
//   → connectToServer(n.loginserver)
```

Karena jalur 2 terbukti nyata di perangkat, **`serversetting.json` WAJIB ada di
mirror lokal** dan isinya harus konsisten dengan bin (`loginServer:loginPort`).

### 2a. Pemegang nilai = setting bin (parser `LaunchActivity`, XOR "DragonBall")

Parser dex membaca **32 kunci** (urutan asli): `testMode` (atau `Android<versionCode>`),
`webVer`, `nativeRender`, `url`, `webUrl`, `tsUrl`, `tsWebUrl`, `update`, `forceBuild`,
`noForceBuild`, `forceUrl`, `checkTime`, `tsLoginServer`, `loginServer`, `loginKey`,
`loginPort`, `loginWebPort`, `productPrefix`, `productIdMap`, `gameIcon`, `extra`,
`clientParams`, `peiUrl`, `wpei`, `wpeiurl`, `blackWhiteL`, `blackWhiteLNetSegment`,
`currentNetIpUrl`, `unityGameSdkLogin`, `checkGoogleTicketUrl`, `gameLanguage`,
`nativeUnzipJson`.

Getter holder (mode == 2 adalah cabang "ts"; jalur kita testMode=1):

```
cointhreat()    →  mode==2 ? "null"
                   : loginPort>0 ? "%s:%d"(loginServer, loginPort)   // LOGINSERVER JS
                   : loginServer                                     // = 127.0.0.1:28112
seventygenom()  →  mode==2 ? tsLoginServer
                   : loginWebPort>0 ? "%s:%d"(loginServer,loginWebPort) // gerbang = 127.0.0.1:28110
                   : loginServer
```

### 2b. Bin ASLI vs bin lokal (bukti panen configus + diff)

Bin asli dari server config asli (711 B, sha256 `ad6fd64c…`, 2 mirror configus
identik) membaca:

```json
{ "testMode": 1,
  "url": "https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935",
  "tsUrl": "http://dragon.sjmobilegame.com:82/bs103/index-native.html",
  "update": "https://dragonh5cdn.popoh5.com/bs/upgrade",
  "checkTime": 300,
  "loginServer": "https://login.popoh5.com",
  "tsLoginServer": "http://118.24.68.91:7003",
  "loginPort": 610,
  "loginWebPort": 510,
  "clientParams": { "hideList": ["1.99","2.99","14.99","59.99","89.99"],
                    "supportLang": [{"languageName":"English","lang":"en"},
                                    {"languageName":"Français","lang":"fr"},
                                    {"languageName":"한국인","lang":"kr"}],
                    "showCurChannel": "toFacebook" } }
```

Bin lokal (`config/setting_BS_Android.bin`, 688 B) = **satu-satunya perubahan adalah
redirect**: `loginServer→http://127.0.0.1`, `loginWebPort→28110`, `loginPort→28112`,
`url/update→http://127.0.0.1:28110/…`; `clientParams`, `tsLoginServer`, `tsUrl`,
`checkTime`, `testMode` **tetap asli**. Pola asli juga dua port (`:510` gerbang web +
`:610` login socket) — pemetaan lokal: 28110 gerbang, 28112 loginserver.

---

## 3. PAYLOAD `startGame` (native → JS, bukti MainActivity + index-native.html asli)

JS membaca dari `json`:

| Field JS | Sumber native | Efek di JS |
|---|---|---|
| `loginServer` | `mSdkParams.cointhreat()` | alamat loginserver (jalur 1) |
| `thirdParams` | string SDK (jawaban gerbang `{"code":0,"sdk":"BSNative","nickName","loginToken","userId","sign",…}`) | `getSdkLoginInfo()` → userInfo; `osType` dari `thirdParams.osType` |
| `thirdChannel` | jika ada | `window.gameIcon` (diganti jika clientParams.gameIcon ada) |
| `clientParams` | `mSdkParams.unnecessarysperrylites` (dari bin) | `window.hideList`, `window.gameIcon`, `window.supportLang`, `window.battleAudio`, `window.sdkNativeChannel`, `window.showCurChannel`; (refreshPage juga baca `show18Login`,`show18Home`) |
| `version` | dihitung native (mPreloadPath/mGameUrl) | `window.version` (+` for osType`) |
| `versionConfig` | objek native | `window.versionConfig` |
| `language` | setelan bahasa native | `window.debugLanguage` |

Cabang lama native (tak terpakai jalur BS) juga punya `packageName`, `loginKey`,
`screenSize`, `versionConfigObb`, `jsonDecodeB64Map`/`jsonB64Map` — dicatat supaya
tidak dianggap hilang.

**Global JS untuk server list** — `window.serverList` (map whitelist+rename
`{serverId:name}`), `window.hiddenServersRange` (`[{from,to}]`), `window.replaceServerName`
(`[{from,to,name,reset}]`) TIDAK di-set oleh index-native.html native → **undefined di
jalur kita** → tidak menyaring/mengganti apa pun (perilaku default).

---

## 4. PROTOKOL (bukti main.min.js asli — socket.io client v2 + Engine.IO v3 POLLING)

Transport = kelas `TSSocketClient(serverName, verifyEnable)`:

```
io.connect(url, {reconnectionAttempts:10})
  on connect → verifyEnable ? socketOnVerify() : langsung siap
  emit "handler.process" (pesan, callback-ack)
  on "Notify" (push server→klien)
  on error / reconnect_error / reconnect_failed / connect_timeout / disconnect
     → error path (disconnect > 10 menit = wajib login ulang)
```

- **loginClient = TSSocketClient("login-server", FALSE)** → **TANPA verify** di tahap ini.
- mainClient/chatClient/dungeonClient = verifyEnable TRUE → handshake verify dipakai
  mulai tahap dunia game: server emit `42["verify",<challenge>]` → klien balas
  `emit("verify", (new TEA).encrypt(challenge,"verification"), ack)` → ack `{ret:0}`.
  XXTEA kunci "verification", longs little-endian, keluaran Base64.

Frame Engine.IO v3 polling (semua teks, prefix panjang UTF-8):

| Kondisi | Paket |
|---|---|
| handshake GET tanpa `sid` | `<len>:0{"sid":"…","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}` — server asli hidup: persis begini |
| klien konek namespace | kirim `2:40` → server balas `2:40` |
| detak jantung | klien POST `1:2` → server jawab `1:3` (interval 25 dtk) |
| emit dengan ack | `42<id>["handler.process",{…}]` → server `43<id>[{…}]` |
| disconnect | `1:41` (server buang sesi) |

**Amplop respons** (parser `processHandlerWithLogin`):

```js
if (0===e.ret){ var t=e.data;
  e.compress && (t=LZString.decompressFromUTF16(t));   // compress DIDUKUNG klien
  var a=JSON.parse(t);                                  // WAJIB JSON valid (tanpa catch!)
  a.language && (ts.language=a.language);               // language di ENVELOPE mengubah bahasa klien
  n && n(a);
} else { log("登录出错", e.ret); o && o(); }            // ret!=0 → cukup {ret:<angka>}
```

Server asli (probe) mengirim **`{"ret":0,"data":"<JSON>"}` saja** — tanpa
`compress`, tanpa `language`. Aksi yang tidak dikenal → **`ret:901`** (BUKAN ret:1)
dengan bentuk byte (raw-unknown.txt):

```json
{"ret":901,"message":"command not definded"}
```

(tanpa field `data`; teks pesan — termasuk typo aslinya — dipertahankan byte persis.)

---

## 5. KONTRAK AKSI (`handler.process`, tipe "User") — HASIL PROBE SERVER ASLI 2026-10-21

> Semua respons `data` dari server asli membawa **`errorCode`** (0 = sukses).
> Nilai di bawah = byte asli dari `login.popoh5.com:610` (raw frame disimpan
> di panenan, lihat bagian 10).

### 5a. `GetServerList` — daftar server (memicu koneksi + pemilihan server)

```
req : {type:"User", action:"GetServerList", userId, subChannel, channel}
resp: data = {"errorCode":0, "serverList":[<item 12 field>], "history":["<serverId>",…]}
```

Item server ASLI (contoh nyata dari probe — 2107 server di server asli):

```json
{"serverId":"2107", "url":"https://s2107-bs.popoh5.com:8061", "new":true, "hot":false,
 "name":"Server 2107", "online":true, "openDate":"2026-10-16T07:00:06.372Z",
 "offlineReason":"", "merge":false, "mergeTo":"", "urlInner":"http://127.0.0.1:8071",
 "chatInner":""}
```

Tipe: `serverId` STRING, `online`/`new`/`hot` BOOLEAN, `openDate` ISO-8601,
`offlineReason` string, `merge` boolean, `mergeTo` string, `urlInner`/`chatInner`
internal server asli (dipertahankan di config lokal). **TIDAK ada `chaturl`/
`dungeonurl` di item** — keduanya diisi runtime oleh main-server
(`registChat` → `_chatServerUrl` → `serverItem.chaturl`; `userData`/tim →
`_myTeamServerSocketUrl`/`myTeamServerSocketUrl` → `serverItem.dungeonurl`).

Logika klien setelah respons (bukti JS):
1. `filterByWhiteList(serverList)` — hanya jika `window.serverList` ada (tidak ada di native);
2. `changeServerInfo(resp)` — `resp.offlineReason` (top-level, opsional) diterapkan ke
   SEMUA item; hanya item index 0 yang boleh `new:true` (sisanya diubah jadi `hot:true`);
3. pilih server: `history[0]` bila history tidak kosong, else `serverList[0].serverId`;
4. tidak ketemu → buka window `ServerListNode` (pemain pilih manual; tap server offline
   → tampil `offlineReason`; ikon `hot`/`new`/maintenance);
5. `onLoginSuccess` → `localStorage lastServerID` → tombol START aktif.

`history` per userId, array `serverId` STRING, **terbaru di depan** — TERBUKTI LIVE:
probe `userId=1` → `history:["1","1191"]`; probe baru `[]` → SaveHistory → `["2107"]`.

### 5b. `LoginAnnounce` — pengumuman layar login

```
req : {type:"User", action:"LoginAnnounce"}
resp: data = {"data":[<isi notice.json>], "errorCode":0}     ← probe: {"data":[],"errorCode":0}
```

Item notice (dibaca JS `getNotice`): `text:{<lang>:…}`, `title:{<lang>:…}`, `version`,
`orderNo`, `alwaysPopup`. `[]` = tombol notice disembunyikan (normal).

### 5c. `SaveHistory` — kapan START ditekan (sebelum masuk server)

```
req : {type:"User", action:"SaveHistory", accountToken, channelCode, serverId,
       securityCode?, subChannel, version:"1.0"}    // securityCode undefined → tidak dikirim
resp: data = {"errorCode":0, "todayLoginCount":1}
```

**Server asli TIDAK mengembalikan `loginToken`** di jalur ini (raw frame tercatat) —
`loginToken` klien tetap dari `thirdParams` (gerbang SDK). JS menghormati itu:
`e&&e.loginToken&&(loginInfo.userInfo.loginToken=e.loginToken)`. Implementasi lokal
yang lama MENGARANG field ini — salah. `todayLoginCount` = hitungan login hari itu
(telemetri; klien melaporkan count 4 dan 6 ke SDK-report).

### 5d. `SaveLanguage` — aksi ke-6 (HILANG dari implementasi lama!)

```
req : {type:"User", action:"SaveLanguage", userid, sdk, appid, language}   // "userid" lowercase — quirk asli JS
resp: data = {"errorCode":0, "todayLoginCount":0}
```

JS: sukses (`errorCode===0`) → tutup window + `changeLanguage(lang)`; gagal → fallback
`window.changeLanguage(lang)` tetap jalan. Server lokal wajib punya aksi ini.

### 5e. `SaveUserEnterInfo` — laporan setelah masuk dunia game

```
req : {type:"User", action:"SaveUserEnterInfo", accountToken, channelCode, subChannel,
       createTime, userLevel, version}
resp: data = {"errorCode":0}      ← probe: persis {"errorCode":0}
```

Callback JS langsung `loginClient.destroy()` (socket login ditutup).

### 5f. `loginGame` — login origin (jalur non-SDK; server asli MENOLAK channel asing)

```
req : {type:"User", action:"loginGame", userId, password, fromChannel, channelName,
       headImageUrl, nickName, subChannel, version:"1.0"}   // password default "game_origin"
resp: probe (userId tidak dikenal di server asli):
      {"loginToken":"","userId":"","nickName":"","headerImage":"","isCreated":false,
       "errorCode":103, "errorMessage":"本服务器只允许特定的渠道来源", "channelCode":""}
```

Server asli membatasi channel origin tertentu.

**Kebijakan loginGame lokal — DIPUTUSKAN pemilik: (a) TIRU ASLI.** Server lokal
menolak channel asing persis seperti asli: bentuk byte sesuai probe di atas
(`errorCode:103`, `errorMessage:"本服务器只允许特定的渠道来源"`, field `loginToken`/
`userId`/`nickName`/`headerImage`/`isCreated`/`channelCode` kosong sesuai asli).
**SDK BUKAN jalur `game_origin`:** channel SDK (BSNative) TIDAK dimasukkan ke
daftar channel origin dan TIDAK boleh dilewatkan lewat aksi ini — jalur masuk
game lokal tetap lewat gerbang SDK (`thirdParams` → `getSdkLoginInfo()`), bukan
lewat `loginGame`. Tidak ada jalur sukses `loginGame` di tahap ini: server asli
tidak pernah teramati mengizinkan channel asing, bentuk suksesnya tidak pernah
ada di bukti, dan TIDAK diarang-arang. Jalur origin sesungguhnya (bila suatu saat
dibutuhkan) = keputusan tahap DUNIA GAME, bukan tahap ini.

### 5g. Aksi asing

Server asli: envelope `{"ret":901,"message":"command not definded"}` — tanpa `data`,
typo asli dipertahankan (bentuk byte raw-unknown.txt). (Implementasi lama salah: ret:1.)

---

## 6. THE CONFIG — `docs/login-server/config/` + aturan edit

| File (config/) | Asal | Lokasi di perangkat | Peran |
|---|---|---|---|
| `setting_BS_Android.bin` | redirect dari bin asli 711 B | `files/server/bs/db/android/` | alamat semua server; **`loginPort: 28112`** = kunci tahap ini |
| `servers.json` | bentuk asli 12 field (hasil probe) | `files/server/loginserver/` | serverList — bentuk item WAJIB 12 field (bagian 5a) |
| `notice.json` | `[]` (bentuk item sesuai JS 5b) | `files/server/loginserver/` | isi `data` respons LoginAnnounce |
| `serversetting.json` | asli 73 B, `loginserver`→lokal | `files/server/bs/resource/properties/` | fallback alamat loginserver (jalur 2) |
| `clientversion.json` | asli 38 B byte-exact | `files/server/bs/resource/properties/` | `gameVersion` enterGame + tampilan "ver …" |
| `sound_click.mp3` | asli 1585 B byte-exact | `files/server/bs/resource/assets/image/public/sound/` | menutup 404 terbukti di perangkat |
| `gamehistory.txt` | dibuat server | `files/server/` | riwayat server per uid — **DIJAGA re-sync** |

Aturan edit:

1. `servers.json`/`notice.json` boleh pretty-print — server memadatkan otomatis.
   **Bentuk field tidak boleh diubah**: `serverId` string; `online`/`new`/`hot`
   boolean; wajib ada 12 kunci (5a) — klien mengabaikan item yang kunci wajibnya hilang.
2. Hanya item pertama boleh `"new":true` — klien mengubah `new` item lain menjadi `hot`.
3. `chaturl`/`dungeonurl` TIDAK ditulis di item — datang dari main-server (tahap
   berikutnya). Selama 28113 belum hidup: START → dialog koneksi terputus → kembali ke
   layar login (jalur error normal klien).
4. Edit `loginPort` di bin → port server ikut **setelah restart**.
5. `serversetting.json.loginserver` HARUS = `loginServer:loginPort` dari bin
   (`http://127.0.0.1:28112`) — dua jalur alamat tidak boleh berbeda.
6. Semua respons `data` wajib membawa `errorCode:0` (sukses) mengikuti server asli.
7. `requireSDK` di serversetting.json = false (nilai asli; field ini TIDAK dibaca
   main.min.js — dipertahankan apa adanya).

---

## 7. IMPLEMENTASI SERVER LOKAL (DIBANGUN — classes2.dex, kelas `com.db.local.LoginServer`)

- Murni Java (`ServerSocket` thread-per-connection), TANPA lib eksternal; dilatari
  dari `MiniServer.start()` (satu hook warisan 1.0.1, classes.dex tidak disentuh).
- Port **28112** (bin `loginPort`), bind 127.0.0.1; socket.io v2 / Engine.IO v3 POLLING:
  handshake `0{...}` — sejak REVISI 3 mengiklankan `"upgrades":[]` (sengaja, server
  polling-only; mencegah klien asli mencoba upgrade websocket yang pada implementasi
  polling akan mengganggu sesi — lihat §8b), ping 25000/5000, connect `40`, ping `2`→`3`,
  emit `42<id>["handler.process",…]` → ack `43<id>[…]`, disconnect `41`; frame
  `<len>:<isi>` dengan len = panjang STRING (unit UTF-16) — bukti probe parse karakter.
- 6 aksi User/* + aksi asing `ret:901` (bentuk byte §5g) + `loginGame` kebijakan (a)
  tolak channel asing `errorCode:103` byte persis probe.
- Sesi: map `sid → {antrean keluar, status nsp, userId}`; long-poll GET menunggu maks
  ~20 dtk lalu jawab kosong; sweep sesi kedaluwarsa (TTL 60 dtk); sid acak 17 char.
- CORS `*` di semua jawaban (page-origin 28110 ≠ port 28112, pola asli sama).
- Config dibaca via pola `resolve` MiniServer (files/server dulu → assets mirror):
  `loginserver/servers.json` + `loginserver/notice.json` — edit file langsung efektif.
- `gamehistory.txt`: `uid<TAB>id,id,…` — maks 5, terbaru di depan, **DIJAGA re-sync**
  (bootstrapSync kini memulihkannya bersama 5 file DB + generator.json);
  `todayLoginCount` per accountToken per hari (SaveHistory mulai 1; SaveLanguage 0).
- MiniServer: `mimeFor` + `.mp3 → audio/mpeg` dan `.zip → application/zip` (CT CDN asli).
- `serverList` di-rebuild urutan asli 12 field; item tanpa `serverId` dilewati;
  `notice.json` = isi array diteruskan apa adanya.

---

## 8. STATUS & UJI (build 1.0.3 — 2026-10-21)

**Build baru SUDAH DIEKSEKUSI & terverifikasi JVM; verifikasi perangkat menunggu
pemilik.** Rilis `v1.0.3` lama (tidak sah) DIGANTI asetnya di release yang sama.

APK: `DB-Local-1.0.3.apk` — 125.293.629 B, sha256
`42d66ba122aca19dc8c1198e0ac82b66ffdcfe223eae8099717f5f788b399239`, zipalign verified,
signature v1+v2+v3, sertifikat IDENTIK garis 1.0.1/1.0.2 (SHA1 `5D:08:26:4B…`) →
`adb install -r` di atas 1.0.2 tanpa uninstall.

Delta terverifikasi byte terhadap basis rilis v1.0.2 (3.713 entri): 3 penggantian
(classes2.dex 46.452 B; bin 688 B loginPort 28112; VERSION `1.0.6-login` = pemicu
re-sync internal) + 5 file baru (loginserver/servers.json, loginserver/notice.json,
bs/resource/properties/serversetting.json + clientversion.json byte-exact asli,
bs/resource/assets/image/public/sound/sound_click.mp3 byte-exact asli). Sisanya
byte-identik, TIDAK ada entri hilang.

Uji JVM **30 PASS / 0 FAIL** (kelas PERSIS dex; kontras langsung raw frame panenan):
- handshake byte persis + prefix = panjang string; connect `2:40`; ping `1:2`→`1:3`;
  multipaket POST (pong+ack satu poll);
- **LoginAnnounce / SaveHistory / SaveLanguage / SaveUserEnterInfo byte-exact** vs
  panenan; **loginGame 103 byte-exact** (Mandarin UTF-8 benar); **aksi asing byte-exact**
  (`ret:901` + pesan asli);
- GetServerList: item 12 field urutan asli + tipe bool; history `[]` → SaveHistory →
  `["1"]` → kap 5 terbaru di depan; gamehistory.txt format `uid<TAB>ids`;
- re-sync VERSION menjaga gamehistory.txt + menyalin ulang config;
- regresi MiniServer: bin verbatim 688 B, index-native verbatim, 404, gerbang
  `loginchecknative` tetap hijau (`code:0`).

Kesalahan build lama (terverifikasi dari server asli):

| # | Kesalahan | Bukti dari server asli |
|---|---|---|
| 1 | `servers.json` 6 field, respons tanpa `errorCode`/`history` | item asli 12 field; respons `{"errorCode":0,serverList,history}` |
| 2 | `SaveHistory` mengarang `loginToken` | asli TIDAK mengembalikan loginToken |
| 3 | Aksi `SaveLanguage` tidak ada | asli menerima + jawab `{"errorCode":0,…}` |
| 4 | Aksi asing `ret:1` | asli `ret:901` |
| 5 | `loginGame` di-echo sukses | asli menolak channel asing (`errorCode:103`) |
| 6 | `serversetting.json`+`clientversion.json`+`sound_click.mp3` tidak di mirror | fallback terbukti dipanggil perangkat; 3 file ada di CDN asli |
| 7 | Doc lama: 5 kunci bin, 5 aksi | parser 32 kunci; 6 aksi User/* |

Yang terbukti BENAR dari build lama (dipertahankan): redirect bin, arsitektur 2 port,
`gamehistory.txt` dijaga terbaru-di-depan, login-server tanpa verify, mirror JS
autentik (`main.min_777039fc.js` + `index-native.html` sha256 identik byte-per-byte
dengan CDN asli).

Uji build baru: **SELESAI — 30 PASS / 0 FAIL** (rincian di atas) — kontras byte-exact
respons vs panenan live (9 aksi), guard re-sync, regresi gerbang SDK `loginchecknative`.

### 8a. REVISI 2 — verifikasi perangkat pemilik (HAR `log running/loginserver.har`, 2026-10-21)

Keluhan pemilik: port 28112 tidak pernah dihubungi, list server tidak muncul. Telusuri
HAR (58 entri, Reqable): SEMUA jalur sebelum loginserver hijau (bin 688 B `loginPort
28112` sampai ke perangkat — dekript respons HAR; upgrade/SDK/loginchecknative 200),
TIDAK ADA satu pun koneksi ke 28112. **LoginServer tidak cacat — ia tidak pernah
ditakdeer.** Akar masalah (bukti `main.min_777039fc.js` + dex asli):

1. `connectToLogin` = dua jalur: `TSBrowser.executeFunction("getLoginServer")` dulu
   (JALUR 1), lalu fallback baca `resource/properties/serversetting.json` RELATIF dari
   root web (JALUR 2). `getLoginServer` di index-native.html = `var loginServer = null`
   dan TIDAK pernah diisi (classes.dex asli tidak mengenal string itu) → JALUR 1 selalu
   kosong → JALUR 2 = satu-satunya.
2. Root web perangkat = folder hasil ekstrak `upgrade/all.zip` oleh update-manager
   native (bukan `server/bs/`), dan all.zip (asli maupun build 1.0.3) TIDAK berisi
   `resource/properties/serversetting.json` → `RES.getResByUrl` gagal diam →
   `connectToServer` TIDAK pernah dipanggil.
3. Perangkat tidak re-ekstrak karena `resource.version` server (11390) = warisan 1.0.2.

Perbaikan rev2 (TANPA menyentuh kode; delta = isi mirror saja):

| # | Perubahan | Isi |
|---|---|---|
| 1 | `upgrade/all.zip` | +3 file path relatif root web: `resource/properties/serversetting.json` (`http://127.0.0.1:28112`, bentuk tab asli), `resource/properties/clientversion.json` (byte-exact panen), `resource/assets/image/public/sound/sound_click.mp3` (byte-exact panen); internal `resource.version` → 11391 |
| 2 | `upgrade/resource.version` | 11390 → **11391** (pemicu unduh+ekstrak) |
| 3 | `upgrade/size.json` | +`"11390": <size all.zip baru>`, `"all"` diperbarui |
| 4 | `upgrade/upgrade.json` | +`"11390": "all.zip"` |
| 5 | `VERSION` | `1.0.6-login` → `1.0.8-login` (pemicu re-sync mirror) |

Uji rev2 (JVM, satu rantai dari APK final): update-manager sim (lokal 11390) →
`resource.version` 11391 → unduh all.zip (size cocok size.json) → ekstrak → root web
berisi 3 file + `resource.version` 11391 → konek socket.io 28112 (framing `len:content`,
emit bentuk panenan `{"type":"User","action":…}`) → `2:40` → GetServerList ack
`{"ret":0,"data":"{\"errorCode\":0,\"serverList\":[1 server 12 field url 28113],…}"}` —
**LIST SERVER MUNCUL**. TestLogin regresi tetap 30/30. Catatan pengetesan: klien socket.io
asli mengirim POST berframing `len:content`; emit tanpa `type`/bentuk asing dijawab
`ret:901` sesuai kontrak.

### 8b. REVISI 3 — penelusuran mandiri tanpa log baru (2026-10-21)

Laporan pemilik: rev2 terpasang, hasil sama, "loginserver tidak ada tanda-tanda terbuka",
tidak akan update log lagi. Penelusuran dilakukan penuh dari artefak (APK tayang, dex,
runtime-dex.jar, libegret.so, HAR lama, main.min/index-native asli). Rantai bukti:

1. **Rantai start utuh**: `LaunchActivity.onCreate` instruksi ke-2 = `MiniServer.start`
   (classes.dex, tidak pernah disentuh) → `LoginServer.start` dipanggil TANPA syarat
   SEBELUM thread MiniServer → bind `127.0.0.1:28112` hardcoded. Karena 28110 terbukti
   hidup di perangkat (HAR 200 berulang), `LoginServer.start` pasti tereksekusi; bind
   28112 tidak mungkin gagal (port kosong; INTERNET ada — 28110 bind sukses) →
   **port 28112 LISTENING di perangkat**. "Tidak ada tanda-tanda" = butanya Reqable:
   WebView/mesin native Chromium & engine HTTP tidak mem-proksi loopback — semua entri
   28110 di HAR berasal dari HttpURLConnection Java, bukan klien game.
2. **JALUR 1 (payload `startGame`) mati di perangkat — bukti empiris 2 sumber**:
   (a) logcat 1.0.1 penulis: JS mem-fetch `serversetting.json` (JALUR 2) padahal bin
   saat itu punya `loginServer` internet; (b) `sdk.har` 1.0.2: UI login hidup
   (loginByName ×3 + loginchecknative) tapi NOL trafik ke `login.popoh5.com:610` yang
   saat itu HIDUP. `loginServer = json.loginServer` tidak pernah terisi → klien SELALU
   JALUR 2. (Payload builder terbukti benar: kedua cabang `gangclothing` memuat
   `loginServer = cointhreat()`; penyebab pasti jalur native→JS — tidak perlu
   diperbaiki karena JALUR 2 kini dijamin.)
3. **JALUR 2 (serversetting.json) salah root di semua build sebelumnya**: mesin native
   Egret memetakan path `resource/...` ke root sendiri — string `assetsRoot` + `game/`
   di `libegret.so` + `preloadPath` (folder ekstrak all.zip). Bukan mirror `bs/`
   (rev1 — salinan di sana tidak pernah dibaca), dan bukan folder ekstrak untuk
   perangkat ber-versi 11220: **`assets/game/resource.version` = 11220** sedangkan
   `upgrade.json` tak punya kunci `"11220"` → update-manager batalkan ekstrak diam-diam
   → all.zip rev2 (berisi serversetting.json) TIDAK PERNAH diekstrak → 404 JALUR 2
   (persis logcat penulis: clientversion.json ×3, serversetting.json, sound_click.mp3).
4. **Mekanisme bunuh sesi rev2 (baru ditemukan)**: handshake mengiklankan
   `upgrades:["websocket"]`; klien asli (mesin native punya WebSocket) mencoba upgrade
   `GET /socket.io/?...transport=websocket` — `handleIo` menjawabnya lewat jalur
   long-poll biasa → `sess.drain()` bisa menelan paket antrean (mis. pong `3`) →
   heartbeat klien timeout → reconnect ×10 → mati tanpa list.

Perbaikan rev3 (basis = aset tayang rev2 `b4b095db…`; delta 3 ganti + 3 tambah):

| # | Perubahan | Tujuan |
|---|---|---|
| 1 | `classes2.dex`: literal handshake `"upgrades":["websocket"]` → `"upgrades":           []` (24 byte sama panjang, SHA1+Adler32 header dex dihitung ulang) | klien tak pernah mencoba WS; sesi tetap di jalur polling yang terverifikasi byte-exact |
| 2 | +`assets/game/resource/properties/serversetting.json` (67 B, 28112) | JALUR 2 di root embedded `assetsRoot=game/` — root yang benar-benar dipakai mesin |
| 3 | +`assets/game/resource/properties/clientversion.json` (38 B) + `resource/assets/image/public/sound/sound_click.mp3` (1585 B) | menutup 3 × 404 lain dari logcat penulis di root yang sama |
| 4 | `upgrade.json` +`"11220":"all.zip"`; `size.json` +`"11220":19387001` | perangkat ber-versi lokal 11220 kini benar-benar mengekstrak all.zip (root preload) |

Verifikasi: androguard parse dex valid (12 kelas); hanya 24 byte offset 35028 berubah;
3 entri baru tepat; upgrade/size JSON valid; sign v1+v2+v3 + zipalign.

**SERTIFIKAT BARU** — keystore 1.0.1–rev2 hilang bersama sandbox lama → rev3 dipakai
kunci baru (SHA256 `a8c47b4e…8602`, berlaku s.d. 2056) → **WAJIB UNINSTALL build lama
dulu**, lalu pasang rev3. Konsekuensi: DB akun generator mulai dari nol (guest baru).

**Kesengajaan divergensi dari server asli**: handshake `upgrades:[]` (asli: websocket).
Ini satu-satunya beda byte dari bentuk panenan — dipilih agar klien asli tetap di
transport polling yang kontraknya terverifikasi penuh, bukan WS yang tidak pernah
dipanen. Catatan di §7.

---

### 8c. REVISI 4-DEBUG — konsol debug melayang Eruda-like (2026-10-21)

**Motif**: verifikasi perangkat tidak lagi bergantung logcat PC/Reqable. Alat debug
sendiri (self-logger) tertanam di APK: layer tampilan terpisah yang mengambang di
atas game dan tetap membaca log layer utama — persis pola Eruda pada web, tapi
versi native. Semua diagnosis "28112 tidak terbuka" kini terlihat di layar perangkat.

**Prinsip build (sumber kebenaran tidak disentuh)**:

| Komponen | Status vs rev3 |
|---|---|
| `classes.dex` (game + hook) | byte-identik ✓ |
| `classes2.dex` (MiniServer/LoginServer/DbGen v4) | byte-identik ✓ |
| `AndroidManifest.xml` | flip same-length 28 char (UTF-16 pool): `com.zhuhuan.game.Application` → `com.db.dbg.DbgAppApplication`; 15232 B tetap, 13 byte beda, AXML tetap valid |
| `classes3.dex` | BARU 41.520 B — layer debug `com.db.dbg.*` (33 kelas: DLog, DebugOverlay, OverlayHost, DbgAppApplication) |

`DbgAppApplication` = subclass murni dari Application asli (`super` selalu dipanggil
lebih dulu; seluruh perilaku asli diwarisi). Semua kode debug dibungkus try/catch —
bila layer debug gagal, aplikasi tetap jalan normal.

**Arsitektur DLog — 5 sumber log → ring 4000 entri + file + UI**:

| Sumber | Mekanisme | Menangkap |
|---|---|---|
| API sendiri | `DLog.add` | diagnostik rev4: BOOT, PORT |
| stdout/stderr | tee `PrintStream` (asli tetap jalan) | print Java engine/SDK |
| Crash | `defaultUncaughtExceptionHandler` + chain | crash Java apa pun (tampil sebelum proses mati + disimpan ke file) |
| Bridge logcat | `logcat -v threadtime -T 300`, filter PID sendiri | `Log.i` LoginServer/MiniServer/DbGen v4 + log engine/JS — TANPA PC |
| Verifier port | connect `127.0.0.1:28112` & `28110` (1s×12 lalu 10s) | `LISTENING ✓` / `TIDAK TERBUKA ✗` + waktu naik/turun |

**Overlay (layer DecorView — tanpa izin, tanpa ubah window type)**: bubble 46 dp
bisa digeser (snap ke tepi), badge merah = jumlah error baru; panel berisi tab
**LIVE** (semua log), **NET** (hanya tag server: MiniServer/LoginServer/LoginConn/
DbGen/PORT/BOOT), **INFO** (status port real-time, bridge, memori, path file, jumlah
file mirror); filter level V/D/I/W/E, pencarian, jeda, salin, bersihkan; sentuhan di
luar panel lolos ke game. File log otomatis:
`Android/data/com.db.local/files/dbg/log.txt` (+`log.1.txt`, rotasi 512 KB).

**Uji JVM DLog: 28 PASS / 0 FAIL** — init, tee stdout/stderr, parser threadtime
(4 kasus: normal, PID asing difilter, tag padded, colon ganda), crash-chain ke
handler sebelumnya, filter level/query/netOnly, format baris, rotasi deterministik,
ring cap 4000.

**Sertifikat**: keystore rev3 tidak bisa dibuka (password hilang bersama sandbox
lama) → keystore debug baru `build-dbg/keys/dblocal-dbg.keystore` (alias `dblocal`,
pass `dblocal-dbg-2026`, cert SHA256 `96dfa852…c337f`, berlaku s.d. 2056) →
**WAJIB UNINSTALL rev3 dulu**, lalu pasang rev4-debug.

**Hasil build**: `DB-Local-1.0.3-rev4-debug.apk` 125.121.953 B, sha256
`e5ba4132128423673e1e3067383de5b89cea9445582bd1aebe1c81d14b9067c9`; audit: hanya
manifest (13 byte) berubah + 1 entri baru; sign v1+v2+v3; zipalign verified.
Sumber: `work-re/build-dbg/` (di luar repo, aturan anti-sampah). Build debug —
keputusan ikut/tidaknya di 1.0.3 stabil tetap milik pemilik.

**Protokol diagnosis perangkat (langsung menjawab keluhan "28112 tidak terbuka")**:

1. Pasang → buka app → bubble `DBG` muncul di kiri atas layar.
2. Ketuk bubble → tab LIVE. Wajib ada: `S/BOOT` (perangkat+pid), `S/DLog: bridge
   logcat aktif`, `S/PORT: 28112 (LoginServer) LISTENING ✓`.
3. Bila `PORT 28112 ✗` → baca baris `E/LoginServer` (penyebab bind) dan
   `E/CRASH` (bila proses mati) — semuanya tampil di layar, tanpa PC.
4. Tab NET: request MiniServer (setting bin, upgrade.json, all.zip, v1/*) +
   handshake/polling LoginServer, lengkap dgn status.
5. Tab INFO: status port real-time, path file log, memori, mirror.
6. Tombol BAGIKAN → kirim log sebagai teks (WhatsApp/email) tanpa PC.

---


## 9. RENCANA PEMBUATAN (DIEKSEKUSI 2026-10-21 — menunggu verifikasi perangkat pemilik)

1. **LoginServer** (kelas `com.db.local.LoginServer`, classes2.dex — TANPA nama
   versi baru; tetap versi default tahap = 1.0.3): 6 aksi User/* dengan bentuk
   respons ASLI (`errorCode` di semua data; SaveHistory tanpa loginToken; ret:901
   aksi asing; **loginGame = kebijakan (a) tiru asli — tolak channel asing
   `errorCode:103`, SDK bukan jalur game_origin**), `gamehistory.txt` dijaga,
   CORS `*`. → **SELESAI, uji JVM 30/30 (bagian 8).**
2. **Mirror +3 file**: `bs/resource/properties/serversetting.json` (lokal 28112),
   `bs/resource/properties/clientversion.json` (asli), `bs/resource/assets/image/
   public/sound/sound_click.mp3` (asli). → **SELESAI (byte-exact).**
3. **Config** → dari folder `config/` ini tanpa diubah makna. → **SELESAI**
   (+ `loginserver/servers.json` & `notice.json` masuk mirror).
4. **Uji JVM** kontras langsung dengan raw frame panenan (bagian 10). → **SELESAI,
   30 PASS / 0 FAIL.**
5. **Build APK** dari basis rilis v1.0.2 terverifikasi (sha256 `b32352ce…585300`,
   audit bagian 1a) — classes2.dex baru + mirror + bin — penomoran
   rilis = **1.0.3 (versi default tahap, menggantikan v1.0.3 lama yang tidak sah)**;
   nomor baru HANYA jika pemilik menetapkannya setelah build terbukti stabil jalan.
   → **SELESAI — sha256 `42d66ba1…99239` (bagian 8), aset diganti di release GitHub.**
6. **Verifikasi perangkat**: server list tampil di UI, notice, START → SaveHistory →
   (dialog putus ke 28113 = batas tahap ini, normal). → **MENUNGGU pemilik** —
   logcat: tag `LoginServer` (listening 28112, 200 handshake, 200 ok) + tag
   `MiniServer` (re-sync 1.0.5 → 1.0.6-login, 200, 200 gate).

---

## 10. BASIS BUKTI (panenan 2026-10-21 — server asli hidup saat probe)

| Artefak | Isi / sha256 |
|---|---|
| `setting_189006.bin` / `setting_64b7aa.bin` | bin asli 711 B dari 2 config server (identik) — `ad6fd64c…f12cf4a` |
| `all.zip` (CDN asli) | 19.385.098 B; `resource.version` 11389 |
| `main.min_777039fc.js` | original 4.914.149 B — `2a14dca3…f5986065` (= mirror APK rilis, byte-identik) |
| `index-native.html` | original 10.792 B live — `59dba3e4…cda36b3` (= mirror APK rilis) |
| `web-layer/resource/properties/serversetting.json` | asli 73 B: `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}` |
| `web-layer/resource/properties/clientversion.json` | asli 38 B: `{"clientVersion":"20260929_180158-EN"}` |
| `web-layer/resource/assets/image/public/sound/sound_click.mp3` | asli 1585 B (MPEG 22.05 kHz) |
| `raw-*.txt` + `*.json` (9 aksi) | frame ack mentah + data JSON: getserverlist-1/2/3, loginannounce, logingame, savehistory, savelanguage, saveuserenterinfo, unknown |
| Probe | `login.popoh5.com:610` — handshake `96:0{…}`; sid hidup; polling EIO3; 2107 server dipanen |
| Dekompilasi | jadx classes.dex original: parser 32 kunci, holder getter, builder payload startGame |

Panenan berada di luar repo (`work-re/panen-loginserver/original/`); masuk repo hanya
doc ini + `config/` (aturan anti-sampah).

---

## 11. INDEKS BAHAN

| Bahan | Isi |
|---|---|
| `log running/sdk.har` | capture perangkat tahap SDK (gerbang hijau; belum memuat trafik loginserver) |
| `docs/sdk/CONFIG-SDK-ASLI.md` | doc tahap SDK 1.0.2 (fondasi: MiniServer, generator, DB akun, gerbang) |
| `docs/login-server/config/` | config loginserver yang benar (bin + 2 json + 3 web-layer) |
| main.min.js (CDN asli = mirror `bs/js/`) | sumber kontrak: `connectToLogin`, `TSSocketClient`, `processHandlerWithLogin`, `sdkLoginSuccess`, `selectNewServer`, `startBtnTap`, `getNotice`, `saveLanguage`, TEA/XXTEA |
| index-native.html (CDN asli = mirror `bs/`) | penerima payload `startGame` + bridge `getLoginServer`/`getSdkLoginInfo` |
| classes.dex (APK original, jadx) | parser bin 32 kunci, getter `cointhreat()`/`seventygenom()`, builder payload |
| Server config asli | `configus.sjmobilegame.com/bs/db/android` (+mirror COS qcloud) |
| Server loginserver asli | `login.popoh5.com:610` (socket.io v2, HIDUP saat probe 2026-10-21) |
