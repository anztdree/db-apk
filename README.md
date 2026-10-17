# 🔍 Dokumentasi Riset: Bongkar & Bedah DB-Local.apk

> **Repo**: `github.com/anztdree/db-apk` — dokumentasi lengkap hasil bedah APK, alur aplikasi, dan komunikasi server ↔ aplikasi.
> **Status**: ✅ Bongkar APK selesai · ✅ Alur aplikasi terpetakan mendalam · ✅ Protokol server↔aplikasi terbedah & teruji live
> **Rencana standalone mode**: ⏸️ ditunda — disusun menyusul berdasarkan hasil bedah kode (lihat §9 catatan dasar).

> ⚠️ **Disclaimer**: Dokumen ini murni untuk tujuan riset/preservasi game yang backend-nya mendekati mati.
> Semua kunci/rahasia di bawah **memang tertanam di dalam klien** (bukan hasil serangan ke server).
> Jangan gunakan untuk kegiatan ilegal (cheat, bot ekonomi, penyalahgunaan server).

---

## Daftar Isi

1. [Identitas Aplikasi](#1-identitas-aplikasi)
2. [Arsitektur Teknis](#2-arsitektur-teknis)
3. [Sistem Enkripsi 3 Lapis (Semua Terpecahkan)](#3-sistem-enkripsi-3-lapis-semua-terpecahkan)
4. [Alur Aplikasi Mendalam (End-to-End)](#4-alur-aplikasi-mendalam-end-to-end)
5. [Bedah Komunikasi Server ↔ Aplikasi](#5-bedah-komunikasi-server--aplikasi)
6. [Uji Protokol Live (Bukti Nyata)](#6-uji-protokol-live-bukti-nyata)
7. [Hasil Ekstraksi: Kode, Data, Aset](#7-hasil-ekstraksi-kode-data-aset)
8. [Infrastruktur Server & Status](#8-infrastruktur-server--status)
9. [Catatan Dasar Menuju Standalone Mode](#9-catatan-dasar-menuju-standalone-mode)
10. [Artefak & Tools Riset](#10-artefak--tools-riset)
11. [Kesimpulan](#11-kesimpulan)

---

## 1. Identitas Aplikasi

| Properti | Nilai |
|---|---|
| Nama app | **DB - Local** |
| Package | `com.db.local` |
| Versi | 1.0.1 (versionCode 1) |
| SDK | minSdk 21 (Android 5.0), targetSdk 30 (Android 11) |
| Main Activity | `com.zhuhuan.game.LaunchActivity` |
| Nama rilis internasional | **"Super Fighter Idle"** (dari halaman FB resmi di kode) |
| Tema | Idle RPG kartu bertema **Dragon Ball** |
| Bahasa | EN, FR, KR, CN, TW, DE, VI |
| Tanggal build aset | Desember 2021 |
| Ukuran APK | 105 MB (2.522 file aset di `assets/game/`) |

---

## 2. Arsitektur Teknis

```
┌────────────────────────────────────────────────────────────┐
│                     APK (com.db.local)                     │
│                                                            │
│  LaunchActivity ──► ambil config server ──► HOT UPDATE     │
│       (Java, obfuscated kata-acak)                         │
│         │                                                  │
│         ▼                                                  │
│  MainActivity ──► Egret Native Runtime (libegret.so 17MB)  │
│         │           └─ embed V8 JavaScript engine          │
│         ▼                                                  │
│  index-native.html ──► manifest.json ──► 14 file JS game   │
│         │                (terenkripsi onepiece/XXTEA)      │
│         ▼                                                  │
│  [ GAME JS ] main.min.js 4.7MB (1.897 class)               │
│              battlelogic.min.js (simulasi battle lokal)    │
│              socket.io.min.js v2.1.0 (jaringan)            │
│         │                                                  │
│         ▼                                                  │
│  Jembatan JS↔Native: egret.ExternalInterface               │
│  (startGame, pei/pembayaran, report2Third, refresh, dll)   │
│                                                            │
│  SDK pihak-3: QuickGame, Google Billing, Facebook Login,   │
│  AppsFlyer, Adjust, Firebase FCM, TiViCloud (libgavesec)   │
└────────────────────────────────────────────────────────────┘
```

**Struktur DEX**: kode Java di-obfuscate dengan nama kata-acak (`cointhreat`, `singersmooth`, `reweavingsiamesedpropertylessnesses`, ...).
- `com.zhuhuan.game.*` — LaunchActivity (updater), MainActivity (bridge)
- `com.quickgame.android.sdk.*` — SDK publikasi
- `cointhreat.*/singersmooth.*` — util (termasuk dekriptor resource `customreading.java`)

---

## 3. Sistem Enkripsi 3 Lapis (Semua Terpecahkan)

### 3a. Enkripsi #1 — Konfigurasi server (XOR)

`config.properties` di APK berisi `EntryPoint1..3` = Base64 URL server konfigurasi:

| Channel | URL |
|---|---|
| default | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` |
| BS (aktif) | `https://configus.sjmobilegame.com/bs/db/android` |
| BS cadangan | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` (Tencent COS) |

File `setting_<channel>_Android.bin` dienkripsi **XOR dengan kunci berulang `"DragonBall"`**
(sumber: `LaunchActivity.worktopichardtails()`):
```java
bArr[i5] = (byte) (bArr[i5] ^ key[i5 % keyLength]); // key = "DragonBall".getBytes()
```

Hasil dekripsi `setting_BS_Android.bin`:
```json
{
  "testMode": 1,
  "url": "https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935",
  "tsUrl": "http://dragon.sjmobilegame.com:82/bs103/index-native.html",
  "update": "https://dragonh5cdn.popoh5.com/bs/upgrade",
  "checkTime": 300,
  "loginServer": "https://login.popoh5.com",
  "tsLoginServer": "http://118.24.68.91:7003",
  "loginPort": 610,
  "loginWebPort": 510,
  "clientParams": { "hideList": ["1.99","2.99","14.99","59.99","89.99"], ... }
}
```

### 3b. Enkripsi #2 — Kode game (XXTEA varian "onepiece")

Format file terenkripsi:
```
[16B acak][16B magic "onepieceencrypts"][16B kunci ter-XOR][payload]
```
- Kunci asli = `bytes[32:48] XOR "onepiecedecryptk"`
- Payload = **XXTEA varian** (delta `0x9E3779B9`, satu pass, fungsi MX kustom)
  — sumber: `cointhreat...customreading.java` (`nationalcommunitymissing()` = dekripsi)
- Dekriptor Python dibuat dengan port eksak algoritme di atas (simulasi `uint32` Java) —
  **berfungsi 100%** (artefak riset lokal, lihat §10)

### 3c. Enkripsi #3 — Kunci runtime native

`NativeKey.getKey()` (JNI, `libegret.so`) = **`5ce4b0e76844477`**
(ditemukan via disassembly capstone — string teks biasa di section rodata)

---

## 4. Alur Aplikasi Mendalam (End-to-End)

### Fase 1 — Bootstrap Native (Java) — DETAIL LENGKAP

#### 1a. File config yang DIBAWA di dalam APK (`assets/`)

| File | Isi | Peran |
|---|---|---|
| `config.properties` (channel default) | `EntryPoint1` = Base64 `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | kandidat server config |
| `config_BS.properties` (channel BS/aktif) | `EntryPoint1` = Base64 `https://configus.sjmobilegame.com/bs/db/android`<br>`EntryPoint2` = Base64 `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` | kandidat server config (2 URL fallback) |
| `supplierconfig.json` | `{"supplier":{"vivo":{"appid":"100215079"}, "xiaomi":{}, "huawei":{}, "oppo":{}}}` | app ID SDK OEM China |
| `QHinfo` | teks: `http://qsdk.t4game.com` | endpoint QuickGame SDK |
| `runtime-dex.jar` | runtime Egret Native (dex terpisah) | engine runner |
| `game/infoinfo.bin` | teks: `voyage_fancy_radius\|pschent_full_tired` | nama paket kode bawaan (dipisah `\|`) |
| `game/base.version` | teks: `110` | versi kode bawaan |
| `game/resource.version` | teks: `11220` | versi resource bawaan |
| `game/vvcc.bin` | **onepiece-encrypted** → JSON 1.944 entri `{path_asli: path_obfuscated}` | peta nama file resource |
| `game/vvcc_tc.bin` | **onepiece-encrypted** → JSON 139 entri | peta nama file (subset teamwork) |
| `game/pschent_full_tired.bin` (9,4 MB) | **onepiece-encrypted** → ZIP kode game lengkap | paket kode #1 |
| `game/voyage_fancy_radius.bin` (1,2 MB) | **onepiece-encrypted** → ZIP build kedua | paket kode #2 |
| `game/*` lainnya (33 file total) | PNG/JPG polos (nama acak), .data, .bin, .version | aset & metadata |

#### 1b. Rantai download config saat app dibuka (urutan persis + pola URL)

Semua langkah dieksekusi `LaunchActivity` (kode decompiled, terverifikasi):

```
LANGKAH 1 — Bangun daftar kandidat config server
   baca assets/config.properties + config_BS.properties
   → untuk i in 1..3: kumpulkan property "EntryPoint<i>" (ada 1..2)
   → Base64.decode tiap entry → URL config server

LANGKAH 2 — Unduh setting global (wajib)
   GET {entrypoint}/setting_{channel}_Android.bin?rnd=<Math.random>
       → file nyata: setting_BS_Android.bin
   retry maks 10x, timeout eksponensial: 5s, 10s, 30s, ...
   → XOR-decrypt kunci "DragonBall" (field binKeyData = "DragonBall".getBytes(UTF-8))
   → parse JSON → simpan ke mSdkParams (tabel field: §1c)
   Jika key "Android<versionCode>" ada → config per-versi app (nested); jika tidak →
   pakai root JSON (kasus nyata: root flat, key "Android1" tidak ada)

LANGKAH 3 — Unduh config per-package (opsional)
   GET {entrypoint}/{packageName dengan "."→"_"}.bin?rnd=<Math.random>
       → file nyata: com_db_local.bin
   retry maks 3x
   ✦ VERIFIKASI LIVE: file ini 404 (TIDAK ADA di server produksi) → dilewati
     dengan anggun (fungsi return true bila download gagal, app lanjut)

LANGKAH 4 — Baca paket kode bawaan
   assets/game/infoinfo.bin → "voyage_fancy_radius|pschent_full_tired"
   cek OBB expansion: main.<versionCode>.<package>.obb di getObbDir()
   proses vvcc.bin (dekripsi onepiece) → peta nama file resource

LANGKAH 5 — Cek versi hot-update (dua sumber lokal)
   lokal  : file <destDir>/base.version & <destDir>/resource.version
            (hasil instalasi sebelumnya); fallback assets/game/*.version
   remote : GET {update}/base.version?rnd=<Math.random>      (retry 3)
            GET {update}/resource.version?rnd=<Math.random>   (retry 3)
   → remote "404" → tampil "Server base.version is Not Exist" & berhenti

LANGKAH 6 — Unduh size.json (untuk progress bar)
   GET {update}/size.json?rnd=<Math.random>
   → {"base": ukuran_base.zip, "<resVer>": ukuran_patch, "all": ukuran_all.zip}

LANGKAH 7 — Pilih & unduh paket update
   ┌─ base.version lokal ≠ remote → GET {update}/base.zip?v=<ver>  (31 MB)
   ├─ resource.version beda → cek GET {update}/upgrade.json?rnd
   │     upgrade.json = PETA PATCH INKREMENTAL:
   │     {"11389":"11389_11390.zip", "11388":"11388_11390.zip", ...,
   │      "all":"all.zip"}
   │     → key cocok dgn versi LOKAL → unduh patch {localVer}_{remoteVer}.zip (~250 KB)
   │     → tidak ada key cocok → unduh {update}/all.zip?v=<ver> (19 MB)
   └─ semua versi sama → langsung lanjut tanpa download

LANGKAH 8 — Muat game URL ke runtime
   pilih URL dari mSdkParams (logika §1d) → index-native.html → Fase 2
```

#### 1c. Tabel lengkap field `setting_*.bin` (dibaca LaunchActivity → `mSdkParams`)

| Key JSON | Tipe | Disimpan sebagai | Fungsi |
|---|---|---|---|
| `Android<versionCode>` | objek | mode container | config per-versi app (nested); prioritas di atas testMode |
| `testMode` | int | mode | **1** = server produksi (`url`), **2** = server test (`tsUrl`/`tsLoginServer`), 3 = alur SDK alternatif, 4 = varian khusus |
| `webVer` | int | webVer | threshold: bila Android API ≥ nilai → pakai varian web (`webUrl`) |
| `nativeRender` | int | — | flag render native |
| `url` | string | gameUrlNative | **URL game yang dimuat runtime** (index-native.html) |
| `webUrl` | string | gameUrlWeb | URL varian web (H5 di browser) |
| `tsUrl` | string | testUrlNative | URL game server TEST |
| `tsWebUrl` | string | testUrlWeb | URL game server TEST varian web |
| `update` | string | updateUrl | base URL hot-update (`base.version`, `resource.version`, `size.json`, `upgrade.json`, `base.zip`, `all.zip`) |
| `forceBuild` | string | — | versi minimal yang dipaksa update |
| `noForceBuild` | string | — | pengecualian force update |
| `forceUrl` | string | — | URL force update |
| `checkTime` | int | checkTime | interval cek ulang config (detik) — nilai nyata: **300** |
| `tsLoginServer` | string | tsLoginServer | login server TEST — nilai nyata: `http://118.24.68.91:7003` |
| `loginServer` | string | loginServer | login server produksi — nilai nyata: `https://login.popoh5.com` |
| `loginKey` | string | loginKey | kunci tambahan login (dikirim ke JS saat mode non-SDK) |
| `loginPort` | int | loginPort | **port socket login NATIVE** — nilai nyata: **610** |
| `loginWebPort` | int | loginWebPort | port login versi web — nilai nyata: **510** |
| `productPrefix` | string | — | prefix produk IAP |
| `productIdMap` | objek | — | peta produk IAP ke SDK |
| `gameIcon` | string | — | ikon per-channel |
| `extra` | objek | — | data ekstra bebas |
| `clientParams` | objek | clientParams | diteruskan ke JS: `hideList` (harga IAP disembunyikan), `supportLang`, `gameIcon`, `battleAudio`, `sdkNativeChannel`, `showCurChannel` |
| `peiUrl` / `wpei` / `wpeiurl` | — | — | konfigurasi pembayaran (dibaca, belum dipakai runtime ini) |
| `blackWhiteL` / `blackWhiteLNetSegment` | string | — | daftar hitam/putih (maintenance/IP) |
| `currentNetIpUrl` | string | — | URL laporan IP jaringan |
| `unityGameSdkLogin` | bool | — | flag alur login SDK Unity |
| `checkGoogleTicketUrl` | string | — | verifikasi ticket Google Play |
| `gameLanguage` | string | — | bahasa paksa |

#### 1d. Logika pemilihan URL (mode & varian)

```java
// mSdkParams (obfuscated: cointhreat...ironoriginhoblike):
gameUrl()   = (mode == 2) ? (apiLevel ≥ webVer ? tsWebUrl : tsUrl)
                           : (apiLevel ≥ webVer ? webUrl    : url)
loginNative() = (loginPort > 0) ? format("%s:%d", loginServer, loginPort)   // :610
                                : loginServer
loginWeb()    = (mode == 2) ? tsLoginServer
            : (loginWebPort > 0) ? format("%s:%d", loginServer, loginWebPort) // :510
                                 : loginServer
```
Nilai nyata (`testMode: 1`, tanpa `webVer`) → native memuat
`https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935` dan login
`https://login.popoh5.com:610`. ✓ cocok dengan hasil live test.

#### 1e. Hot update — verifikasi live saat riset

| Item | APK (lokal) | CDN (server) | Keputusan app |
|---|---|---|---|
| `base.version` | `110` | `110` | sama → **tidak** unduh base.zip |
| `resource.version` | `11220` | `11389` | **beda** → cek upgrade.json → patch `11220_11390.zip`? |

Isi live `size.json`: `{"base": "31015076", "11389": 252562, "11388": 252562, ..., "all": 19385098}`
Isi live `upgrade.json`: `{"11389": "11389_11390.zip", "11388": "11388_11390.zip", ..., "all": "all.zip"}`

> Kunci versi di `size.json`/`upgrade.json` bergerak bersama `resource.version` server —
> sistem mendukung **patch inkremental** (±250 KB) dan **paket penuh** all.zip (±19 MB).

#### 1f. Serahkan ke MainActivity

Semua hasil config (`mSdkParams`) → `MainActivity` → muat `gameUrl()` di runtime Egret
Native (V8) → payload `startGame` (lihat Fase 2) → game JS hidup.

### Fase 2 — Runtime JS (index-native.html)

`index-native.html` (file CDN asli, disimpan di artefak riset lokal) melakukan:

```js
xhr('./manifest.json') → daftar script (manifest.initial + manifest.game)
loadScript(...) semuanya → window["loadJsonFunc"] = RES.getRes
egret.ExternalInterface.addCallback("startGame", function(msg){
    json = JSON.parse(msg)                 // dikirim native
    loginServer = json.loginServer         // ← URL login server disuntik NATIVE
    thirdParams = json.thirdParams         // {sdk, nickname, userid, securityCode}
    clientParams = json.clientParams       // hideList, supportLang, dll
    egret.runEgret({renderMode:"webgl"})   // ← game mulai
})
egret.ExternalInterface.call("startGame", "get Sdk Channel")  // minta data ke native
```

**Poin penting**: URL `loginServer:610` TIDAK di-hardcode di JS — native menyuntikkannya lewat event `startGame` (dari `setting_*.bin` hasil XOR). Native menyusunnya sebagai `String.format("%s:%d", loginServer, loginPort)` → `https://login.popoh5.com:610` (versi web game pakai `loginWebPort` → `:510`).

### Fase 3 — Jembatan JS ↔ Native (egret.ExternalInterface)

| Arah | Nama | Fungsi |
|---|---|---|
| native→JS | `startGame` | loginServer, thirdParams (data login SDK), clientParams, versi |
| native→JS | `refresh` | reload game / switch user |
| JS→native | `pei` | **pembayaran** (QuickSDK; `data.power = data.money`) |
| JS→native | `report2Third` | laporan event ke SDK channel (level up, chapter, tutorial, shop) |
| JS→native | `giveLike` / `fbGiveLive` | share/like Facebook |
| JS→native | `contact` / `userCenter` / `gifBag` | customer service, user center, gift code |
| JS→native | `switchAccount` / `changeLanguage` / `openURL` | utilitas |

Juga di-inject oleh HTML: `window.checkSDK`, `window.getSdkLoginInfo`, `window.getAppId`,
`window.serverList`, `window.supportLang`, `window.hideList`, `window.reportBattlleLog`,
`window.reload`, `window.clipboardData`, dll — semua dipakai main.min.js.

### Fase 4 — Login & Masuk Game (dari main.min.js)

> ⚠️ Catatan jujur: seluruh alur di bawah ini hasil **analisis kode** (dapat dipercaya —
> nama class/method nyata), tetapi **belum diuji end-to-end** karena butuh akun SDK valid.
> Yang sudah teruji live = level koneksi (lihat §6). Bagian ini dibiarkan sebagai
> dokumentasi kode; pembuktian penuh menunggu sesi nyata (MITM) bila diperlukan.

Dua jalur login:

**Jalur A — SDK (QuickGame):**
```
window.checkSDK() → window.getSdkLoginInfo()
  → { loginToken, userId, nickName, sdk }        // thirdParams.data
  → clientRequestServerList(userId, sdk)          // action "GetServerList" ke login server
  → selectNewServer() (auto-pilih server "new"/history)
  → onLoginSuccess() → tombol START tersedia
```

**Jalur B — Origin login (username/password manual):**
```js
loginBtnTap → doOriginLoginRequest({username, password, lastServerID:""})
  if (!password) password = "game_origin"          // ← password default bila kosong!
  → clientLoginUser(username, password, ...)       // action "loginGame"
  → clientRequestServerList(...)                   // action "GetServerList"
  → buka UI "ServerListNode" (pilih server manual)
  → startBtnTap → action "SaveHistory" → clientStartGame()
```
Juga ada auto-login: `checkLastLogin()` membaca query `?userid=` atau localStorage
`last_game_server` → login ulang otomatis dengan password `"game_origin"`.

**Sequence lengkap setelah pilih server:**
```
1. clientStartGame()
2.   mainClient.connectToServer(serverItem.url)        // socket.io
3.   (verify handshake — hanya main/chat/dungeon, lihat §5)
4.   startListenNotify() → clientEnterGame()
5.     mainClient: {type:"user", action:"enterGame", loginToken, userId,
6.                  serverId, language, gameVersion, version:"1.0"}
7.     ← server kirim SELURUH data user (UserDataParser.saveUserData)
8.   registChat() polling 3 detik → dapat chaturl + roomId
9.   chatClient.connect(chaturl) → {type:"chat", action:"login"} → join 4 room
   (world, guild, teamDungeon, team)
10.  dungeonClient.connect(serverItem.dungeonurl)
11.  loginClient: action "SaveUserEnterInfo" → loginClient.destroy()
12.  runScene("OverScene") → masuk Home
```

---

## 5. Bedah Komunikasi Server ↔ Aplikasi

### 5a. Topologi Koneksi (4 koneksi socket.io terpisah)

```js
// main.min.js baris 21366:
t.loginClient   = new TSSocketClient("login-server",   false);  // ← verifyEnable = FALSE
t.mainClient    = new TSSocketClient("main-server",    true );
t.chatClient    = new TSSocketClient("chat-server",    true );
t.dungeonClient = new TSSocketClient("dungeon-server", true );
```

| Client | Sumber URL | Verify? | Fungsi |
|---|---|---|---|
| loginClient | `loginServer:610` (disuntik native) | ❌ | loginGame, GetServerList, SaveHistory, SaveUserEnterInfo, SaveLanguage |
| mainClient | `serverItem.url` (dari GetServerList) | ✅ | semua gameplay (60+ tipe pesan) |
| chatClient | `serverItem.chaturl` (dari registChat) | ✅ | chat world/guild/team + broadcast |
| dungeonClient | `serverItem.dungeonurl` | ✅ | team dungeon realtime + push event |

Engine: **Socket.IO v2.1.0** (header file `socket.io.min_57ea9db7.js`).

### 5b. Format Pesan (Envelope Seragam)

**Request** — event `"handler.process"`, argumen `(message, ackCallback)`:
```js
socket.emit("handler.process",
  {
    type: "user",              // modul (user/hero/arena/dungeon/...)
    action: "enterGame",       // operasi
    ...params,                 // param per-action
    version: "1.0"             // selalu ada
  },
  function(response) { ... }   // server balas via socket.io ACK
);
```

**Response** (semua modul sama):
```json
{
  "ret": 0,                       // 0 = sukses; lainnya = error (lihat §5g)
  "data": "<JSON string>",        // payload TERENCODE STRING, bukan objek!
  "compress": true,               // bila true → data = LZString UTF16
  "serverTime": 1699...000,       // sinkronisasi waktu
  "server0Time": ...
}
```
Alur dekode: `ret==0` → (compress? `LZString.decompressFromUTF16(data)`) → `JSON.parse` → callback.
(HTTP request `ts.httpReqHandler` memakai format sama: POST `Content-Type: application/x-www-form-urlencoded`, body `data=<JSON.stringify(payload)>`, timeout 20 detik, retry 1x.)

**Server push** — event `"Notify"` (dan chat `"Notify"` ret "SUCCESS"):
```json
{ "ret": "SUCCESS", "data": "...", "compress": false }
```
Action push yang dikenali client: `payFinish` (pembayaran selesai), `redDotDataChange`,
`itemChange`, `guildAgree`, `beKickedOutGuild`, `heroBackpackFull`, `onlineBulletin`,
`scheduleModelRefresh`, `monthCard`, `vipLevel`, `notifySummon`, `warStageChange`,
`warRankChange`, `warAutoSign`, `ballWarStateChange`, `ballWarPointRankChange`,
`topBattleBeAttack`, `topBattleStageChange`, `teamDungeon*` (5 jenis), `timeTrialReset`,
`battleMedalRefresh`, `battleMedalTaskChange`, `broadcast`, `userMessage`, `updateForbiddenChat`,
`mainTaskChange`, `joinTeamSuccess`, `areanRecord`, `FGAddMsg`, `FGAddChatMsg`, `FGNewApply`, ...

### 5c. Handshake `verify` (anti-cloning koneksi)

Hanya untuk main/chat/dungeon server. Segera setelah connect:
```
server  ──► event "verify" dengan nonce string
client  ──► TEA_encrypt(nonce, "verification") → Base64
client  ──► emit "verify" (ciphertext, ack)
server  ──► ack {ret: 0} → koneksi resmi; selain itu → destroy
```

Implementasi TEA (port eksak dari main.min.js — klasik **XXTEA**):
```js
// delta = 2654435769 (0x9E3779B9), rounds = floor(6 + 52/n)
// key = strToLongs(keyStr.slice(0,16)) → "verification" = 3 longs + padding
// MX = (s>>>5 ^ l<<2) + (l>>>3 ^ s<<4) ^ (p^l) + (key[3&d^e] ^ s)
// Output: Base64
```
Port ini sudah diuji roundtrip encrypt/decrypt konsisten dengan implementasi asli game.

> Catatan: skrip bukti & dekriptor adalah artefak riset lokal (tidak disertakan di repo
> ini — lihat §10).

### 5d. API Surface — 60+ Tipe & 200+ Action

Inventaris lengkap (hasil scan `type: "..."` / `action: "..."` di main.min.js):

| Tipe | Jumlah call | Contoh action |
|---|---|---|
| `activity` | 124 | getActivityDetail, getLoginActivityReward, getLevelReward |
| `friend` | 46 | getFriends, friendBattle, friendServerAction |
| `guild` | 42 | getGuildList, getGuildDetail, requestGuild, quitGuild |
| `hero` | 27 | getAttrs, wear, takeOff, merge, reborn, splitHero, resolve |
| `topBattle` | 26 | startBattle, getRank, topAward |
| `war` | 21 | getSignUpInfo, bet, getChampionRank (war lintas server) |
| `teamDungeonGame` | 20 | startBattle, getTeamInfo, tryMatch, join |
| `expedition` | 19 | startBattle, sweep |
| `user` / `User` | 16+5 | enterGame, registChat, loginGame, GetServerList, SaveHistory |
| `rank` | 16 | getRank, getRankInfo, getRankReward |
| `ballWar` | 16 | startBattle, bet, ballSignUp |
| `arena` | 14 | startBattle, getFriendArenaDefenceTeam, buyTimes |
| `entrust` | 13 | userEntrustBook, investigation |
| `weapon`/`equip`/`imprint` | 12/11/12 | wear, takeOff, merge |
| `tower` | 12 | startBattle, reset, getReward |
| `teamDungeonTeam` | 12 | getTeamInfo, saveTeam |
| `summon` | 12 | summonOne, summonTen, summonOneFree, openBox |
| `hangup` | 11 | (idle/on-hook rewards) |
| `gift`/`shop`/`mail` | 11/6/6 | buy, getReward, readMsg |
| `snake`/`cellGame` | 10/10 | minigame |
| `training`/`task` | 9/9 | queryTask, finishEvent |
| `dungeon` | 9 | startBattle, checkBattleResult, sweep |
| `trial`/`mine`/`maha`/`bossCompetition`/`battleMedal`/`timeTrial`/`strongEnemy` | 7 | startBattle, getRecord |
| `superSkill`/`resonance`/`genki`/`gemstone`/`gravity` | 5/5/4/4/3 | sistem upgrade |
| `chat` | 5 | login, sendMsg, join |
| `timeMachine`/`teamTraining` | 4 | — |
| `timeBonus`/`retrieve`/`monthCard`/`market`/`downloadReward`/`YouTuber` | 2 | — |
| `vipMarket`/`recharge`/`questionnaire`/`guide`/`checkin`/`buryPoint` | 1 | — |
| `battleRecordCheck`/`battle` | 1 | checkBattleRecord (anti-cheat channel TanWanH5) |

Action terpopuler: `startBattle` (19), `getRank` (19), `friendServerAction` (19),
`getActivityDetail` (16), `checkBattleResult` (15), `getReward` (11), `getInfo` (9),
`wear` (8), `getTeamInfo` (8), `buyTimes` (8), `setTeam` (7), `getAttrs` (7).

### 5e. Protokol Battle (inti game idle)

Pola untuk SEMUA mode battle (dungeon/arena/topBattle/expedition/tower/...):

```
CLIENT                                    SERVER
  │ {type, action:"startBattle",           │
  │  team, super, battleField, ...}        │
  │ ────────────────────────────────────►  │ validasi + pilih musuh
  │                                        │ jalankan simulasi server-side
  │ ◄────────────────────────────────────  │
  │ { _battleId,                           │  ← ID pertarungan
  │   _battleResult,          ← HASIL SUDAH DITENTUKAN SERVER
  │   _rightTeam, _rightSuper,← tim musuh utk animasi
  │   _rand,                  ← SEED RNG!  │
  │   items/_arena/_signs/... }            │
  │                                        │
  │  [VISUAL REPLAY lokal, deterministik]  │  battlelogic.min.js
  │  simulasi dengan seed _rand → hasil    │  (mesin battle lengkap di klien)
  │  HARUS sama dgn _battleResult          │
  │                                        │
  │ {action:"checkBattleResult",           │
  │  battleId, checkResult: 0/1}           │
  │ ────────────────────────────────────►  │ bandingkan dgn hasil server
  │ ◄────────────────────────────────────  │
  │ { _battleResult, reward items,         │
  │   _changeInfo._items, _curMaxLevel }   │
```

**Temuan kunci**:
- Battle = **simulasi deterministik dua sisi** (server authoritative, klien replay visual).
- Klien punya **mesin battle lengkap** (`battlelogic.min.js` 312KB, `GameFieldType`:
  ARENA, EQUIPDUNGEON, EXPDUNGEON, METALDUNGEON, ZSTONEDUNGEON, SIGNDUNGEON, ...) —
  termasuk konfigurasi per-level `battleRecord_XXXX.json` (70+ file).
- Anti-cheat: hasil klien ≠ server → `ret: 22` (`BATTLE_CHECK_ERR`) → client kirim
  log battle ke `http://log.sjflaregame.com:10000/error/battle/client` (JSON:
  gameModel "dragonball", channel, serverId, userId, battleId, errorRecord).
- Sistem `snakeLineUpCheck`, `arenaSaveCheck`, tim idle (`LAST_TEAM_TYPE`) semua ada di klien.

### 5f. Chat Report & Signing MD5

Rahasia hardcoded: **`0acc53af8679849572afd232158c`** — dipakai `reportToXXData()`
(laporan chat ke platform channel "XX", mis. TanWan):
```js
sign = md5(gkey + server_id + qid + time + secret)
// gkey      = "288"
// tkey      = "10"
// server_id = "S" + serverId
// qid       = userId (channel)
// role_id   = userId (game), name = nickname, content = isi chat, time = Date.now()
```

### 5g. Kode Error (362 entri)

Sumber: `resource/json/errorDefine.json` (362 kode). Kode penting:

| ret | errorType | Arti |
|---|---|---|
| 1 | ERROR_UNKNOWN | error umum |
| 7 | ERROR_LACK_ITEM | item kurang |
| 16 | TIMES_NOT_ENOUGH | jumlah kesempatan habis |
| 17 | GM_IP_ERR | kick (isKick:1) |
| 20 | CONFIG_ERROR | kick |
| 22 | **BATTLE_CHECK_ERR** | **hasil battle klien ≠ server** → kirim battle log |
| 38 | — | paksa reload game (`window.reload()`) |
| 70001 | (dari data) | minta client kirim battle log |

---

## 6. Uji Protokol Live (Bukti Nyata)

Dilakukan saat riset (socket.io-client v2.1.0 — versi sama persis dengan klien game):

**a) Probe HTTP endpoint socket.io** (`curl`):
```
https://login.popoh5.com:610/socket.io/?EIO=3&transport=polling
  → 96:0{"sid":"gQ85FjHy8xyUdyWgaXL0","upgrades":["websocket"],
         "pingInterval":25000,"pingTimeout":5000}2:40
```
→ **Server socket.io HIDUP** (engine.io handshake lengkap + packet CONNECT namespace `40`).
`pingInterval 25000` = konfigurasi khas socket.io v2.

**b) Koneksi socket.io penuh (node)**:
```
[NET] connect: https://login.popoh5.com:610
[NET] CONNECTED id = IkfGbmQiJbfYju_faXLc     ← handshake sukses
```
Login server TIDAK mengirim event `verify` (sesuai kode: `loginClient` dibuat dengan
`verifyEnable=false` — verify hanya untuk main/chat/dungeon).

**c) Rate limiting**: 2 koneksi pertama diterima; koneksi ke-3+ ditolak server dengan
error namespace socket.io (`server error`) — meski dengan header Origin/Referer asli.
→ Server hidup tapi **protektif terhadap koneksi berulang** (bukan zombie).

**d) Port 510 (`loginWebPort`)**: HTTPS-nya menjawab `{"code":404,"msg":"没有开通"}`
("belum dibuka") → port web memang tidak aktif; hanya 610 (native) yang dipakai.

**e) Kunci TEA "verification"**: roundtrip encrypt/decrypt terbukti konsisten
(dengan port eksak dari kode game).

> Untuk pengujian lebih jauh (loginGame/GetServerList nyata) diperlukan akun SDK yang
> valid — tidak dilakukan agar tidak menyalahgunakan server yang sekarat.

---

## 7. Hasil Ekstraksi: Kode, Data, Aset

### Kode game (dari `pschent_full_tired.bin` 9,4 MB → ZIP terdekripsi)
- **14 file JS**: `main.min_7eae4d6e.js` (4,7 MB, 1.897 class), `battlelogic.min_394b3477.js`,
  `blockmessage.min_4c0caf6b.js` (ternyata **filter kata sensor chat**, BUKAN protokol!),
  engine Egret/eui/dragonBones/tween/lz-string/md5/socket.io
- **434 file JSON konfigurasi** (`resource/json/`): VIP, arena, activity, hero, weapon,
  `battleRecord_1505..1634.json` (konfig battle per-level), `errorDefine.json`, `constant.json`, dll
- **7 file bahasa** (language-en.json, ...)
- `default.res.json` + peta aset `default.thm.json`

### Aset gambar
- 2.522 file `assets/game/`: 2.067 PNG + 156 JPG (**polos/terbuka**, hanya NAMA file yang
  di-obfuscate) + 74 .data + 77 .bin + 76 .version
- Peta nama asli: `vvcc_tc.bin` → 139 entri (mis. `heros/2001/2001_tex.png` ↔
  `resource/assets_ts/sibbs_consent_pschent/thirty_tozies_favourable.png`)

### Rahasia yang ditemukan (semua tertanam di klien)
| Rahasia | Nilai | Fungsi |
|---|---|---|
| Kunci XOR config | `DragonBall` | dekripsi setting server |
| Magic + kunci kode | `onepieceencrypts` / `onepiecedecryptk` | dekripsi ZIP kode game |
| NativeKey (libegret.so) | `5ce4b0e76844477` | runtime key |
| Kunci handshake verify | `verification` | TEA antar koneksi game |
| Secret MD5 chat report | `0acc53af8679849572afd232158c` | sign laporan chat channel XX |
| Password default origin login | `game_origin` | login username manual/auto |

---

## 8. Infrastruktur Server & Status

| Server | Fungsi | Status saat riset |
|---|---|---|
| `dragon.sjmobilegame.com:82` | config + game uji | ✅ HIDUP (HTTP 200) |
| `configus.sjmobilegame.com` | config channel BS | ✅ HIDUP |
| `configus-1257066086.cos.na-siliconvalley.myqcloud.com` | CDN Tencent COS (cadangan config) | ✅ HIDUP |
| `qsdk.t4game.com` | QuickGame SDK endpoint | ✅ HIDUP |
| `dragonh5cdn.popoh5.com` | CDN game H5 (manifest, JS, hot-update) | ✅ HIDUP — manifest & JS terunduh |
| `login.popoh5.com:610` | **login server (socket.io v2)** | ✅ HIDUP — handshake lengkap, ada rate-limit per IP |
| `login.popoh5.com:510` | port web (loginWebPort) | ⚠️ menjawab `{"code":404,"msg":"没有开通"}` (tidak dibuka) |
| `log.sjflaregame.com:10000` | battle/error log | (endpoint dari kode) |
| `118.24.68.91:7003` | tsLoginServer (uji, IP mentah) | (dari setting) |

---

## 9. Catatan Dasar Menuju Standalone Mode

> ⏸️ **Rencana lengkap ditunda** (permintaan owner) — ini temuan dasar dari hasil bedah
> yang paling relevan saat nanti disusun:

1. **Battle deterministik** — server kirim `_battleId + _battleResult + _rightTeam + _rand`
   (seed). Klien punya mesin battle penuh. Server standalone "cukup" menghitung hasil
   dengan logika yang sama → animasi klien tetap jalan.
2. **Login origin dibangun ke klien**: `loginGame` + password `game_origin` + auto-login
   dari localStorage → server sendiri sangat mungkin tidak butuh SDK asli.
3. **`testMode: 1`** di setting server + server uji `tsLoginServer` (`118.24.68.91:7003`)
   menandakan tooling test/dev pernah ada.
4. **Semua data game lokal** (434 JSON + 7 bahasa + battleRecord per-level) → klien bisa
   boot sampai layar login tanpa server; yang butuh server = gameplay (enterGame & seterusnya).
5. **CDN masih hidup** → aset/kode bisa dimirror sekarang sebelum mati.
6. Blokir: `hideList` harga IAP, TiViCloud recharge, dan channel-SDK report — semuanya
   bisa di-bypass/di-mock di server standalone.

---

## 10. Artefak & Tools Riset

Repo ini sengaja hanya berisi **dokumentasi (README.md) + APK asli** — semua artefak
riset disimpan terpisah sebagai catatan kerja:

```
repo ini (github.com/anztdree/db-apk)
├── README.md        ← dokumen ini (satu-satunya dokumentasi)
└── DB-Local.apk     ← APK asli (Git LFS)
```

### Artefak riset lokal (di luar repo)
| Artefak | Isi |
|---|---|
| Dekriptor XXTEA `onepiece` (Python + Java) | dekripsi ZIP kode game, berfungsi 100% |
| Skrip uji protokol (Node/socket.io-client v2.1.0) | port TEA `"verification"`, logger paket mentah, uji `handler.process` |
| `server-dump/` | setting server terdekripsi, `index-native.html`, `manifest.json`, `game.min.js` |
| Kode game terdekripsi | main.min.js (4,7 MB, 1.897 class) + battlelogic + 434 JSON + aset |

### Toolchain yang dipakai untuk bedah
androguard (manifest) · jadx 1.5.1 (Java decompile) · capstone + pyelftools
(disassembly libegret.so) · pycryptodome · js-beautify (main.min.js 4,7 MB → 126.080 baris)

---

## 11. Kesimpulan

- DB-Local.apk = klien H5 **"Super Fighter Idle"** (tema Dragon Ball) build lokal/test
  (Des 2021), dibuat developer China (namespace `zhuhuan`), dipublikasikan via QuickGame
  untuk pasar internasional.
- Tiga lapis enkripsi (XOR config → XXTEA kode → native key) **semuanya terpecahkan**
  karena semua kunci tertanam di klien.
- Protokol jaringan sepenuhnya terpetakan: socket.io v2.1.0, envelope
  `handler.process {type, action, version}` + response `{ret, data, compress}`
  (LZString UTF16), handshake verify TEA `"verification"`, 4 koneksi dengan
  `verifyEnable` berbeda, 60+ tipe / 200+ action, battle deterministik berbasis seed.
- **Server backend masih hidup** (login.popoh5.com:610 merespons socket.io handshake
  penuh, meski rate-limit) — jendela waktu untuk preservasi masih terbuka.
- Semua temuan terdokumentasi lengkap di README ini — pondasi untuk rencana
  standalone mode selanjutnya.
