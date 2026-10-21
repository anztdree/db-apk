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
> dieksekusi — rencana ada di bagian 10.
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
`compress`, tanpa `language`. Aksi yang tidak dikenal → **`ret:901`** (BUKAN ret:1).

---

## 5. KONTRAK AKSI (`handler.process`, tipe "User") — HASIL PROBE SERVER ASLI 2026-10-21

> Semua respons `data` dari server asli membawa **`errorCode`** (0 = sukses).
> Nilai di bawah = byte asli dari `login.popoh5.com:610` (raw frame disimpan
> di panenan, lihat bagian 11).

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

Server asli membatasi channel origin tertentu. **Kebijakan lokal (menunggu
persetujuan): menerima `game_origin`/channel lokal dengan bentuk sukses, atau
meniru penolakan 103 — diputuskan saat implementasi.** Jalur SDK (BSNative) tidak
pernah memakai aksi ini.

### 5g. Aksi asing

Server asli: envelope `{"ret":901}` tanpa `data`. (Implementasi lama salah: ret:1.)

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

## 7. IMPLEMENTASI SERVER LOKAL YANG DIHARAPKAN (classes2.dex, kelas `com.db.local.LoginServer`)

- Murni Java (`ServerSocket` thread-per-connection), TANPA lib eksternal; dibangunkan
  dari `MiniServer.start()` (satu hook warisan 1.0.1, classes.dex tidak disentuh).
- Sesi socket.io: map `sid → {antrian keluar, status nsp}`; long-poll GET menunggu
  maks ~20 dtk lalu jawab kosong; sweep sesi kedaluwarsa.
- CORS `*` di semua jawaban (page-origin 28110 ≠ port 28112, pola asli sama).
- Config dibaca via pola `resolve` MiniServer (files/server dulu → assets mirror).
- `gamehistory.txt`: `uid<TAB>id,id,…` — maks 5, terbaru di depan, DIJAGA re-sync;
  gagal simpan tidak memblokir START (perilaku klien tetap lanjut).

---

## 8. STATUS & UJI (rencana — build lama TIDAK SAH)

**Rilis `v1.0.3` lama DITOLAK pemilik.** Kesalahan yang terbukti:

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

Uji build baru (rencana): JVM 25+ kasus — kontras byte-exact respons vs panenan live
(9 aksi), guard re-sync, regresi gerbang SDK `loginchecknative`.

---

## 9. RENCANA PEMBUATAN (menunggu persetujuan pemilik — BELUM dieksekusi)

1. **LoginServer v2** di classes2.dex: 6 aksi User/* dengan bentuk respons ASLI
   (`errorCode` di semua data; SaveHistory tanpa loginToken; ret:901 aksi asing;
   loginGame sesuai keputusan kebijakan), `gamehistory.txt` dijaga, CORS `*`.
2. **Mirror +3 file**: `bs/resource/properties/serversetting.json` (lokal 28112),
   `bs/resource/properties/clientversion.json` (asli), `bs/resource/assets/image/
   public/sound/sound_click.mp3` (asli).
3. **Config** → dari folder `config/` ini tanpa diubah makna.
4. **Uji JVM** kontras langsung dengan raw frame panenan (bagian 11).
5. **Build APK** dari basis warisan (classes2.dex baru + mirror + bin) — penomoran
   rilis menggantikan v1.0.3 lama mengikuti keputusan pemilik.
6. **Verifikasi perangkat**: server list tampil di UI, notice, START → SaveHistory →
   (dialog putus ke 28113 = batas tahap ini, normal).

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
