# LOGINSERVER — DOC TUNGGAL (tahap loginserver = versi 1.0.3)

> **Ini satu-satunya doc loginserver — selalu di-upgrade file ini, TIDAK pernah dibuat
> doc/folder baru atau per-versi.** Folder `config/` di samping file ini = satu-satunya
> config loginserver yang benar (bukan jejak).
>
> **Tahap loginserver = VERSI 1.0.3.** Rilis tayang: release `v1.0.3`, aset
> `DB-Local-1.0.3.apk` (125.289.218 B), sha256 `df7466c6…5e0ec2`. Sertifikat IDENTIK
> dengan 1.0.1/1.0.2 (serial `56c1a15`, SHA256 `1e08a903…b5953`) → install-over aman.
> Tahap sebelumnya (SDK) = 1.0.2 — doc-nya terpisah:
> [`docs/sdk/CONFIG-SDK-ASLI.md`](../sdk/CONFIG-SDK-ASLI.md).
>
> Penanda `VERSION` di mirror (`1.0.6-login`) = pemicu re-sync internal antar-install
> SAJA — BUKAN nomor versi produk.

---

## 1. POSISI TAHAP INI (peta rantai)

```
SDK lokal (1.0.2, port 28110)  ← SUDAH LOKAL
  └─ gerbang loginchecknative (code:0, 10 field)
       └─ native MainActivity.hardlinerspare → payload event "startGame" ke JS
            ├─ loginServer = holder.cointhreat()   ← ALAMAT LOGINSERVER (bagian 2)
            ├─ thirdParams = jawaban gerbang utuh
            └─ clientParams/version/... dari setting bin
                 └─ JS Egret (main.min.js) → TSSocketClient("login-server", verifyEnable=FALSE)
                      └─ LOGINSERVER LOKAL (TAHAP INI, port 28112)
                           ├─ GetServerList → pemain pilih server, tombol START aktif
                           ├─ LoginAnnounce → pengumuman layar login
                           └─ SaveHistory/SaveUserEnterInfo/loginGame
                                └─ [TAHAP BERIKUTNYA = DUNIA GAME, port 28113]
                                   mainClient ("main-server", verifyEnable=TRUE)
                                   enterGame + userData + gameplay
```

Yang TIDAK disentuh tahap ini: `tsLoginServer`/`tsUrl` (hanya jalur mode 2), telemetry
FB/AppsFlyer (noise SDK, bukan jalur game), pembayaran (fase C).

---

## 2. SUMBER ALAMAT LOGINSERVER (bukti dex — bedah classes.dex asli)

Parser setting bin = `LaunchActivity.worktopichardtails` (XOR "DragonBall"). Pemegang
nilai = `cointhreat...pursetruechild.ironoriginhoblike`:

| Field bin | Field holder | Dipakai |
|---|---|---|
| `loginServer` | `customreading` | dasar dua alamat |
| `loginWebPort` | `tidyleadership` | **gerbang SDK** (`seventygenom()`) |
| `loginPort` | `hoboismrelationbelow` | **LOGINSERVER untuk JS** (`cointhreat()`) |
| `tsLoginServer` | `gangclothing` | hanya `seventygenom()` saat mode==2 |
| `testMode` (atau kunci `Android<versionCode>`) | `cointhreat` (int) | mode jalur |

Dua getter holder (mode == 2 adalah cabang "ts"; jalur kita testMode=1):

```
seventygenom()  →  mode==2 ? tsLoginServer
                   : loginWebPort>0 ? "%s:%d"(loginServer, loginWebPort)   // gerbang = 127.0.0.1:28110
                   : loginServer
cointhreat()    →  mode==2 ? "null"
                   : loginPort>0    ? "%s:%d"(loginServer, loginPort)     // LOGINSERVER = 127.0.0.1:28112
                   : loginServer
```

Konsumen terbukti: `MainActivity.onCreate` + Runnable gerbang (`nationalcommunitymissing.run`)
memakai `seventygenom()`; `MainActivity.hardlinerspare` menaruh `cointhreat()` ke payload
`startGame` sebagai `"loginServer"` → JS: `loginServer = json.loginServer` (index-native.html)
→ `connectToLogin()` → `io.connect(loginServer)`.

**Perubahan bin tahap ini: `loginPort` 610 → 28112.** Field lain bin tidak diubah.
Server lokal membaca port dari bin yang SAMA (edit bin → server ikut setelah restart;
default fallback 28112). Pola asli juga dua port (`login.popoh5.com:510` gerbang web +
`:610` login socket) — pemetaan lokal: 28110 gerbang, 28112 loginserver.

---

## 3. PROTOKOL (bukti main.min.js — socket.io client v2.1.0 + Engine.IO v3 POLLING)

Transport = kelas `TSSocketClient` (satu untuk semua server):

```
io.connect(url, {reconnectionAttempts:10})
  on connect → verifyEnable ? socketOnVerify() : langsung siap
  emit "handler.process" (pesan, callback-ack)
  on "Notify" (push server→klien)
```

- **loginClient = TSSocketClient("login-server", false)** → **TANPA verify** di tahap ini.
- mainClient/chatClient/dungeonClient = verifyEnable TRUE → handshake verify dipakai
  mulai tahap dunia game (kunci sudah terdokumentasi, bagian 8).

Frame Engine.IO v3 polling (semua teks, prefix panjang UTF-8):

| Kondisi | Paket |
|---|---|
| handshake GET tanpa `sid` | `<len>:0{"sid":"…","upgrades":[],"pingInterval":25000,"pingTimeout":60000}` — upgrades kosong = tetap polling (tanpa websocket) |
| klien konek namespace | kirim `2:40` → server balas `2:40` |
| detak jantung | klien POST `1:2` → server jawab `1:3` (interval 25 dtk) |
| emit dengan ack | `42<id>["handler.process",{…}]` → server `43<id>[{…}]` |
| disconnect | `1:41` (server buang sesi) |

Amplop respons aksi (parser `processHandlerWithLogin`): `{ret:0, data:"<JSON string>"}`
— `ret==0` → klien `JSON.parse(data)` (wajib ADA); `ret!=0` → hanya log + callback error
(tanpa baca data) → aksi asing cukup `{ret:1}`. Field `compress` tidak dipakai server
lokal (tanpa LZString).

---

## 4. KONTRAK AKSI (`handler.process`, tipe "User")

### 4a. `GetServerList` — daftar server (memicu koneksi + pemilihan server)

```
req : {type:"User", action:"GetServerList", userId, subChannel, channel}
resp: data = {"serverList":[<isi servers.json>], "history":["<serverId>", …]}
```

Konsumen JS: `sdkLoginSuccess()` → `clientRequestServerList(userId, sdk)`.
Item server yang dibaca: `serverId` (STRING — `parseInt` nanti), `name`, `online`
(0/1 — 0 = tampil `offlineReason`), `url` (dipakai mainClient saat START),
`hot`/`new` (ikon, opsional), `offlineReason` (opsional). `history` = riwayat per uid
(paling baru di depan); kosong → auto-pilih `serverList[0]`. Respons juga boleh membawa
`language` (diabaikan server lokal).

### 4b. `LoginAnnounce` — pengumuman layar login

```
req : {type:"User", action:"LoginAnnounce"}
resp: data = {"data":[<isi notice.json>]}
```

Item notice: `text:{<lang>:…}`, `title:{<lang>:…}`, `version`, `orderNo`, `alwaysPopup`.
`[]` = tanpa pengumuman (tombol notice disembunyikan — normal).

### 4c. `SaveHistory` — kapan START ditekan (sebelum masuk server)

```
req : {type:"User", action:"SaveHistory", accountToken, channelCode, serverId, securityCode?, subChannel, version:"1.0"}
resp: data = {"loginToken":"<32 hex baru>","todayLoginCount":1}
```

Konsumen JS: `startBtnTap()` → callback memakai `loginToken` baru (diganti ke
`ts.loginInfo.userInfo`) → `clientStartGame()`; `todayLoginCount` hanya telemetri.
Server lokal: riwayat disimpan di `gamehistory.txt` (`uid<TAB>id,id,…`, maks 5, paling
baru di depan) — **file DIJAGA re-sync**. Gagal simpan tidak memblokir START (klien
tetap lanjut).

### 4d. `SaveUserEnterInfo` — laporan setelah masuk dunia game

```
req : {type:"User", action:"SaveUserEnterInfo", accountToken, channelCode, subChannel, createTime, userLevel, version}
resp: data = {}
```

Callback JS langsung `loginClient.destroy()` (socket login ditutup). Server lokal cukup
`{ret:0, data:"{}"}`.

### 4e. `loginGame` — login origin (jalur non-SDK, kebijakan lokal)

```
req : {type:"User", action:"loginGame", userId, password, fromChannel, channelName, headImageUrl, nickName, subChannel, version:"1.0"}
resp: data = {"userId":"<echo>","channelCode":"game_origin"}
```

Jalur SDK (BSNative) tidak pernah memakai ini. Kebijakan lokal: diterima apa adanya
(userId di-echo) — realm akun game terpisah dari DB akun SDK (`accounts.txt`).

---

## 5. THE CONFIG — `docs/login-server/config/` + aturan edit

| File (config/) | Lokasi di perangkat | Peran |
|---|---|---|
| `setting_BS_Android.bin` | `files/server/bs/db/android/` | alamat semua server (XOR "DragonBall"); **`loginPort: 28112`** = kunci tahap ini |
| `servers.json` | `files/server/loginserver/` | serverList — bebas ditambah server (lihat bentuk 4a) |
| `notice.json` | `files/server/loginserver/` | pengumuman layar login (`[]` = kosong) |
| `gamehistory.txt` | `files/server/` (dibuat server) | riwayat server per uid — **DIJAGA re-sync** (kini 6 file dijaga: generator, accounts, codes, bindings, migcodes, outbox + gamehistory) |

Aturan edit:

1. `servers.json`/`notice.json` boleh **pretty-print** — server memadatkan otomatis
   (`compactJson`: whitespace luar string dibuang, spasi di dalam string utuh).
2. Edit `loginPort` di bin → port server ikut **setelah restart** (dibaca saat boot).
3. `url`/`chaturl`/`dungeonurl` item server menunjuk **28113** = alamat dunia game
   (tahap berikutnya). Selama 28113 belum hidup: START → dialog koneksi terputus →
   kembali ke layar login (jalur error normal klien).
4. Bentuk item server: `serverId` string, `online` 0/1 — hilangnya kunci wajib bikin
   item diabaikan klien.

---

## 6. IMPLEMENTASI SERVER LOKAL (classes2.dex, kelas `com.db.local.LoginServer`)

- Murni Java (`ServerSocket` thread-per-connection), TANPA lib eksternal; dibangunkan
  dari `MiniServer.start()` (satu hook warisan 1.0.1, classes.dex tidak disentuh).
- Sesi socket.io: map `sid → {antrian keluar, status nsp}`; long-poll GET menunggu maks
  20 dtk lalu jawab kosong; sweep sesi > 10 menit.
- CORS `*` di semua jawaban (WebView page-origin 28110 ≠ port 28112, pola asli sama).
- Config dibaca via pola `resolve` MiniServer (files/server dulu → assets mirror).
- loginToken = md5(ts|uid|serverId|"dblocal-login") — opaque, baru per SaveHistory
  (mengikuti pola "token baru per aksi"; parser JS hanya `getString`).
- classes2.dex final: **45.792 B** (1.0.2: 35.056 B) — ConnRun + DbGen + DbJson +
  MiniServer + **LoginServer**.

---

## 7. STATUS & UJI (lab JVM — 25/25 PASS, kelas persis yang masuk APK)

| Kelompok | Uji |
|---|---|
| A. guard re-sync (4) | gamehistory.txt + accounts.txt + generator.json selamat saat VERSION beda; VERSION terpasang baru |
| B. socket.io (19) | handshake `0{...}`+prefix benar, OPTIONS+CORS, CONNECT→40, ping→pong, GetServerList (ret:0, serverList dari config, history), LoginAnnounce, SaveHistory (loginToken 32 hex + persist), GetServerList history diperbarui, aksi asing ret:1, SaveUserEnterInfo, loginGame, emit tanpa ack aman, sid asing 400 |
| C. regresi SDK (2) | `loginchecknative` tetap 200 + code:0 + 10 field |

**APK rilis**: `DB-Local-1.0.3.apk` — sha256 `df7466c6725b63988e09552bf102e30922917bbd938cc5817d62fe3f6c5e0ec2`;
classes2.dex di APK byte-identik dengan dex hasil uji; bin dalam APK terverifikasi
(loginPort 28112, loginWebPort 28110, testMode 1, QHinfo tak berubah); sertifikat
identik 1.0.1/1.0.2.

Belum terverifikasi perangkat (menunggu uji lapangan user): koneksi WebView nyata ke
28112, tampil server list di UI game, notice, dan alur START.

---

## 8. KONTRAK TAHAP BERIKUTNYA (dunia game — sudah terbedah, belum diimplement)

- **main-server** (port 28113 dari `url` server): TSSocketClient verifyEnable=TRUE →
  handshake **verify**: server emit `42["verify",<challenge>]` → klien balas
  `emit("verify", XXTEA.encrypt(challenge,"verification"), ack)` → server cek → ack
  `{ret:0}`. XXTEA (kunci "verification", longs little-endian, Base64 keluaran; kunci
  12 karakter → long ke-4 = 0 pada indeks `(d&3)^a==3`) — replikasi persis perilaku JS.
- Setelah verify: `{type:"user", action:"enterGame", loginToken, userId, serverId,
  version:"1.0", language, gameVersion}` → respons = **userData game** (parsing
  `UserDataParser.saveUserData`) + lanjutan `registChat` (`_chatServerUrl`,
  `_worldRoomId`, …), chat-server, dungeon-server, event "Notify".
- `SaveUserEnterInfo` (4d) dikirim setelah enterGame sukses.

---

## 9. INDEKS BAHAN

| Bahan | Isi |
|---|---|
| `log running/sdk.har` | capture perangkat tahap SDK (gerbang hijau; belum memuat trafik loginserver) |
| `docs/sdk/CONFIG-SDK-ASLI.md` | doc tahap SDK 1.0.2 (fondasi: MiniServer, generator, DB akun, gerbang) |
| `docs/login-server/config/` | config loginserver yang benar (bin + 2 json) |
| main.min.js (dalam APK, mirror `bs/js/`) | sumber kontrak: `connectToLogin`, `TSSocketClient`, `processHandlerWithLogin`, `sdkLoginSuccess`, `startBtnTap`, `getNotice`, TEA/XXTEA |
| index-native.html (mirror `bs/`) | penerima payload `startGame` (`loginServer`, `thirdParams`, `clientParams`) |
| classes.dex (APK) | parser bin `worktopichardtails`, getter `cointhreat()`/`seventygenom()`, builder payload `hardlinerspare` |
