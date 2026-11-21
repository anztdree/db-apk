# DB APK — Analisis Lengkap APK Original (com.db.local)

> Dokumen tunggal. Hasil bedah menyeluruh APK `db-local(Original-DO NOT DELETE!).apk`
> dari repo `https://github.com/anztdree/db-apk.git`.
> Riwayat git **tidak disentuh sama sekali** (repo hanya di-clone dan dibaca).
> Workspace analisis: `/home/z/db-apk-analysis/` (di luar repo).

Tanggal analisis: 21 Nov 2026 (zona Asia/Jakarta)

---

## 0. Ringkasan Eksekutif

| Item | Nilai |
|---|---|
| Package | `com.db.local` |
| Label | `DB - Local` |
| versionName / versionCode | `1.0.1` / `1` |
| minSdk / targetSdk | 21 / 30 |
| Ukuran asli | 104.945.181 byte (±100 MB, via Git LFS) |
| SHA256 | `b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218` |
| Engine game | **Egret (HTML5/JS)** — `libegret.so` + runtime `assets/runtime-dex.jar` |
| SDK wrapper | t4game/zhuhuan (`com.zhuhuan.game`) + **QuickGame SDK** (`com.quickgame.android.sdk`) |
| Protokol game | **socket.io v2** (EIO=3) via WebSocket (Java-WebSocket di native, socket.io.min.js di JS) |
| Enkripsi resource | **XXTEA varian** "onepieceencrypts" (sudah dipecahkan, dekriptor Python disertakan) |
| Enkripsi config `.bin` | XOR kunci ASCII **`DragonBall`** |
| Status server | **Config server, CDN game, update server, dan login server (socket.io) MASIH HIDUP saat analisis ini** |

Alur besar: APK boot → unduh config terenkripsi → dapat URL game (`mGameUrl`) →
ekstrak resource lokal → cek update di server → load game Egret →
JS game konek **login server (socket.io)** → dapat **serverList** → konek **game server + chat server + dungeon server**.

---

## 1. Cara APK Diperoleh (dari repo)

1. Clone repo (PAT). Working tree hanya berisi 1 file: `db-local(Original-DO NOT DELETE!).apk` berukuran 4 KB.
2. File itu adalah **Git LFS pointer**, bukan APK:

   ```
   version https://git-lfs.github.com/spec/v1
   oid sha256:b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218
   size 104945181
   ```

3. Objek LFS diunduh lewat GitHub LFS Batch API (`POST /info/lfs/objects/batch`) → URL bertanda tangan → `original.apk`.
4. Verifikasi: ukuran & SHA256 **cocok persis** dengan pointer LFS. Ini APK asli, tidak diubah.

`git lfs` tidak terpasang di sandbox (tidak ada sudo), maka dipakai API langsung — hasil identik.

---

## 2. Struktur APK (3.668 file, 127 MB ter-unzip)

```
original.apk
├── AndroidManifest.xml          package com.db.local, label "DB - Local"
├── classes.dex                  1.879 class (Java layer: wrapper + SDK)
├── lib/
│   ├── arm64-v8a/libegret.so        engine Egret native
│   ├── arm64-v8a/libgavesec.so      proteksi (tidak mengandung URL)
│   └── armeabi-v7a/ (2 file sama)
├── assets/
│   ├── game/                    2.524 file — resource game nama acak (obfuscate)
│   │   ├── infoinfo.bin         [ENKRIPSI] → "voyage_fancy_radius|pschent_full_tired"
│   │   ├── voyage_fancy_radius.bin  [ENKRIPSI] → base.zip  (1.233.549 B, 161 entri, CRC OK)
│   │   ├── pschent_full_tired.bin   [ENKRIPSI] → all.zip    (9.428.692 B, 741 entri, CRC OK)
│   │   ├── vvcc.bin             [ENKRIPSI] → JSON peta nama resource (1.944 mapping)
│   │   ├── vvcc_tc.bin          [ENKRIPSI] → JSON peta (kondisi terpisah)
│   │   ├── base.version         "110"  (versi base lokal)
│   │   ├── resource.version     "11220" (versi resource lokal — server kini 11390)
│   │   └── resource/...         file resource nama-acak (obfuscated)
│   ├── config.properties        EntryPoint1 (Base64) → http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android
│   ├── config_BS.properties     EntryPoint1/2 (Base64) → server config channel "BS"
│   ├── QHinfo                   http://qsdk.t4game.com
│   ├── supplierconfig.json      supplier vivo appid 100215079
│   └── runtime-dex.jar          Egret Native runtime + WebSocket + HTTPDNS
├── res/ (1.111 file) + resources.arsc
└── META-INF/
```

Permission penting: `INTERNET`, `WRITE/READ_EXTERNAL_STORAGE`, `ACCESS_NETWORK_STATE`,
`CHANGE_NETWORK_STATE`, `BILLING`, `WAKE_LOCK`, `SYSTEM_ALERT_WINDOW`.
`networkSecurityConfig`: **cleartextTrafficPermitted="true"** (HTTP polos diizinkan semua domain —
sangat memudahkan redirect ke local server). `requestLegacyExternalStorage=true`.

Manifest juga memuat Facebook App ID `563635417808635` (scheme `fb563635417808635`).

### Kerangka izin/aktivitas

- Application: `com.zhuhuan.game.Application`
- Launcher: `com.zhuhuan.game.LaunchActivity` → game: `com.zhuhuan.game.MainActivity`
- SDK: `com.quickgame.android.sdk.*` (HW/Huawei, OneStore, Google Play signin, Billing, Facebook)
- Analytics: Adjust, AppsFlyer, Facebook, ThinkingData (via JS)

---

## 3. FASE 1 — Struktur Folder yang Dibuat di `android/data`

Semua nilai di bawah **berasal dari kode ter-decompile**, bukan tebakan.

### 3.1 Penentu path (bukti kode)

| Kode | Lokasi | Nilai |
|---|---|---|
| `LaunchActivity.ironoriginhoblike` (field) | `LaunchActivity.java:59` | `"/game/"` |
| `LaunchActivity.feedtonight` (field) | `LaunchActivity.java:60` | `"DragonBall"` (kunci XOR) |
| `getExternalFilesDir("")` | `LaunchActivity` (badan boot) | `/storage/emulated/0/Android/data/com.db.local/files` |
| `EgretNativeAndroid.config.cachePath` | `EgretNativeAndroid.java:50` | `activity.getFilesDir()` (internal) |
| `f39singersmooth` (nama OBB) | badan boot | `main.<versionCode>.<packageName>.obb` = `main.1.com.db.local.obb` |
| Escape URL game | `reweavingsiamesedpropertylessnesses.singersmooth()` | `"://"` diganti `"/"`, lalu `":"` diganti `#0A` |

### 3.2 Pohon folder hasil install & jalankan APK

**Eksternal (yang Anda minta — `Android/data`):**

```
/storage/emulated/0/
├── Android/data/com.db.local/
│   └── files/                          ← getExternalFilesDir("")
│       └── game/                       ← konstanta "/game/"
│           └── https/                  ← escape "://" dari URL game
│               └── dragonh5cdn.popoh5.com/
│                   └── bs/             ← folder game (escape URL mGameUrl live)
│                       ├── base.version        "110"
│                       ├── resource.version    "11220" → naik per update (server kini 11390)
│                       ├── index-native.html   (jika web mode)
│                       ├── manifest.json
│                       ├── js/                 kode Egret (egret.min, main.min, socket.io.min, dst)
│                       ├── resource/           json/, language/, assets/
│                       └── main.1.com.db.local.obb.done   (marker, hanya jika ada OBB)
└── Android/obb/com.db.local/
    └── main.1.com.db.local.obb         (hanya jika vc_obb aktif; jarang dipakai)
```

Catatan penting: ** nama folder `https/dragonh5cdn.popoh5.com/bs/` BUKAN tetap —**
dihasilkan dari escape URL game pada config (`url`/`tsUrl`). Jika URL game diganti
(misal jadi `http://127.0.0.1:8080/index-native.html`), foldernya otomatis menjadi
`files/game/http/127.0.0.1:8080/` (`:` diganti `#0A` → `127.0.0.1#0A8080`).
Karena itu, saat uji local server sebaiknya **hapus dulu isi `files/game/`** agar tidak campur cache lama.

**Internal (pelengkap):**

```
/data/data/com.db.local/
├── files/                ← cachePath Egret runtime (cache resource native render)
├── shared_prefs/         ← state SDK QuickGame, state login, dsb.
└── code_cache/
```

### 3.3 Urutan penulisan folder saat boot

1. `LaunchActivity.onCreate` → init config channel `BS` → minta izin storage.
2. Boot thread: baca `config_BS.properties` (Base64 → EntryPoint).
3. Unduh `setting_BS_Android.bin` + `com_db_local.bin` (retry 10x/3x) → XOR `DragonBall` → JSON → `mSdkParams`.
4. `getExternalFilesDir("") + "/game/"` dibuat sebagai **preloadPath**.
5. Baca `assets/game/infoinfo.bin` (dekripsi XXTEA) → nama 2 zip lokal.
6. Ekstrak zip lokal ke `preloadPath` (log "decompressing").
7. Baca `vvcc.bin` (peta resource) + `vvcc_tc.bin` (jika infoinfo berisi `|`).
8. `GameUpdateUtil`: bandingkan `base.version`/`resource.version` lokal vs server →
   unduh `base.zip`/`all.zip` bila lebih baru → ekstrak ke
   `preloadPath + escape(url) + "/..."`.
9. `MainActivity` dijalankan dengan extra `preloadPath` → Egret load game dari sana.

---

## 4. FASE 2 — Inventaris Endpoint Lengkap

### 4.A Endpoint di dalam APK (hardcoded / Base64)

| # | Endpoint | Sumber file | Keterangan |
|---|---|---|---|
| 1 | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | `assets/config.properties` `EntryPoint1` (Base64) | Channel kosong (TIDAK terpakai — channel aktif "BS") |
| 2 | `https://configus.sjmobilegame.com/bs/db/android` | `assets/config_BS.properties` `EntryPoint1` (Base64) | **Server config aktif** (channel BS) |
| 3 | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` | `assets/config_BS.properties` `EntryPoint2` (Base64) | Backup config (Tencent COS) |
| 4 | `http://qsdk.t4game.com` | `assets/QHinfo` | Domain SDK t4game (wrapper) |
| 5 | `http://119.29.29.29/d?dn=` | `assets/runtime-dex.jar` | HTTPDNS Tencent — resolve domain→IP untuk WebSocket |

Pola unduhan config (dari kode boot):
```
{EntryPoint}/setting_BS_Android.bin?rnd={random}      (retry 10x)
{EntryPoint}/com_db_local.bin?rnd={random}           (retry 3x, package "."→"_" → com_db_local.bin)
```

### 4.B Skema JSON config (field yang dibaca kode `LaunchActivity`)

`Android<versionCode>`, `testMode`, `webVer`, `nativeRender`, `url`, `webUrl`, `tsUrl`, `tsWebUrl`,
`update`, `forceBuild`, `noForceBuild`, `forceUrl`, `checkTime`, `tsLoginServer`, `loginServer`,
`loginKey`, `loginPort`, `loginWebPort`, `productPrefix`, `productIdMap`, `gameIcon`, `extra`,
`clientParams`, `peiUrl`, `wpei`, `wpeiurl`, `blackWhiteL`, `blackWhiteLNetSegment`,
`currentNetIpUrl`, `unityGameSdkLogin`, `checkGoogleTicketUrl`, `gameLanguage`, `nativeUnzipJson`.

Pemilihan URL (class SdkParams `cointhreat...ironoriginhoblike`):
- mode `2` → `tsWebUrl` (jika webVer≥SDK) / `tsUrl`
- selain itu → `webUrl` (web mode) / `url` (native mode)

### 4.C Config LIVE yang baru diunduh saat analisis (server masih hidup!)

`https://configus.sjmobilegame.com/bs/db/android/setting_BS_Android.bin` → XOR `DragonBall` → JSON:

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
	"clientParams": {
		"hideList": ["1.99","2.99","14.99","59.99","89.99"],
		"supportLang": [ {"languageName":"English","lang":"en"},
		                 {"languageName":"Français","lang":"fr"},
		                 {"languageName":"한국인","lang":"kr"} ],
		"showCurChannel": "toFacebook"
	}
}
```

`com_db_local.bin` → **404** (tidak ada, wajar karena package sudah di-rebuild jadi com.db.local).

### 4.D Endpoint runtime hasil config di atas (dengan status uji langsung)

| # | Endpoint | Peran | Uji live |
|---|---|---|---|
| 1 | `https://dragonh5cdn.popoh5.com/bs/index-native.html` | **URL game** (mGameUrl, HTML Egret) | **200 OK** |
| 2 | `https://dragonh5cdn.popoh5.com/bs/upgrade` | **Update server** resource | **200 OK** |
| 3 | `https://login.popoh5.com:610` | **Login server (socket.io v2)** | **HANDSHAKE OK** (`{"sid":"VKSDW_xv_rtjTEgVbnvy","upgrades":["websocket"]...}`) |
| 4 | `https://login.popoh5.com:510` | loginWebPort (web mode) | 404 `{"code":404,"msg":"没有开通"}` (tidak aktif) |
| 5 | `https://login.popoh5.com:443` | (tanpa port eksplisit) | timeout |
| 6 | `http://dragon.sjmobilegame.com:82/bs103/index-native.html` | tsUrl (mode TS) | **200 OK** |
| 7 | `http://118.24.68.91:7003` | tsLoginServer (mode TS) | **MATI** (timeout) |
| 8 | `http://log.sjflaregame.com:10000/error/battle/client` | battle-log report (ada di index-native.html live: `window.reportBattlleLog`) | **302 (hidup)** |
| 9 | `http://www.parajoymobile.com/privacy_en.html` | privacyUrl (HTML live) | hidup |
| 10 | `https://ssweb.episodezz.com` | **ThinkingData analytics** (appId `da5e91639fc948399ba6c9523f593944`, di main.min live) | TIDAK TERJANGKAU |
| 11 | `http://qsdk.t4game.com` | QHinfo | **200 OK** |

### 4.E Protokol update server (`update` field) — dari kode GameUpdateUtil

```
GET {update}/base.version?rnd=        → 110
GET {update}/resource.version?rnd=    → 11390   (APK bawa 11220)
GET {update}/size.json?rnd=           → {"base":"31015076","11389":252562,...,"all":19385098}
GET {update}/upgrade.json?rnd=        → {"11389":"11389_11390.zip", ..., "all":"all.zip"}
GET {update}/base.zip?v={baseVer}     (jika base lebih baru)
GET {update}/all.zip?v={base}{res}    (jika resource lokal kosong)
GET {update}/{versi}.zip              (dari upgrade.json, bila resource lebih baru)
```

Semua nilai di atas **berhasil diunduh live** saat analisis.

### 4.F Fallback config di server game

`{host game}/resource/properties/serversetting.json` (dipakai JS bila `getLoginServer()` kosong):

```json
{ "loginserver": "https://login.popoh5.com:610", "requireSDK": false }
```

Status: **200 OK** — dan mengonfirmasi login server + port 610.

### 4.G Arsitektur koneksi runtime game (socket.io, semua dari JS `main.min*.js`)

```
[JS game] --socket.io v2--> loginClient  --> loginServer (login.popoh5.com:610)
   pesan: {type:"User", action:"GetServerList", userId, subChannel, channel}
   hasil: serverList[] → tiap entri: serverId, name, online, new, url (game server),
          teamChatRoomId, worldRoomId, guildRoomId, dungeonurl, chaturl, serverTag, ...

[JS game] --socket.io v2--> mainClient    --> serverItem.url       (GAME SERVER, per-server)
[JS game] --socket.io v2--> chatClient    --> serverItem.chaturl   (_chatServerUrl dari login)
[JS game] --socket.io v2--> dungeonClient --> serverItem.dungeonurl / teamServerHttpUrl
[JS game] --HTTP POST----> ts.httpReqHandler(url, {type, action, ...})
        respons: {ret:0, data:"..."} atau {compress:true, data:"<LZString UTF16>"}
```

Kunci kripto jalur login: `loginKey` (field config) dipakai untuk verify login (TEA verification).
Aliran data respons bisa dikompresi **LZString (UTF16)**.

Alamat **game server per-server (serverItem.url) bersifat dinamis** — hanya didapat setelah
login sukses lewat login server. Tidak hardcode di APK.

### 4.H Endpoint SDK pihak ketiga (bundel, tidak mengganggu gameplay)

| SDK | Endpoint |
|---|---|
| Facebook (App ID 563635417808635) | graph.facebook.com, facebook.com, fb.gg |
| Google Play Services / Billing | accounts.google.com, play.googleapis.com, android.googleapis.com |
| Adjust | app.adjust.com / .net.in / .world, gdpr.*, subscription.* |
| AppsFlyer | register/stats/launches/conversions/inapps/impression <appId>.appsflyer.com |
| Line Pay (webview HW) | web-pay.line.me |
| Tencent HTTPDNS | 119.29.29.29/d?dn= |

### 4.I Halaman/community yang tertanam di JS live

- `https://discord.gg/5dYCqtCrdJ`
- `https://t.me/AlphaGamingIran`
- `https://www.instagram.com/alphagamingiran`
- `https://www.facebook.com/Super-Fighter-Idle-108619837471134/`

---

## 5. Enkripsi & Kripto yang Ditemukan (lengkap + bukti)

### 5.1 XXTEA varian "onepiece" (resource game)

Ditemukan di `customreading.java` (class `cointhreat.cointhreat.cointhreat.reweavingsiamesedpropertylessnesses.customreading`):

```
byte[16:32] == "onepieceencrypts"            ← magic header
key       = byte[32:48] XOR "onepiecedecryptk"
payload   = byte[48:]
dekripsi  = XXTEA varian 1 pass (delta 0x9E3779B9),
            MX: ((y^sum) + u32(z^key[(p&3)^e])) ^ (u32((i>>>5)^(i2<<2)) + u32((i<<4)^(i2>>>3)))
            langkah terakhir: v[0] -= MX(sum, y, v[terakhir], p=0)
padding   = byte terakhir payload = jumlah byte pad (dibuang)
```

Dekriptor Python: `/home/z/db-apk-analysis/tools/onepiece_decrypt.py` (TERUJI:
`infoinfo.bin`, `vvcc.bin`, `vvcc_tc.bin`, dan kedua zip game terdekripsi penuh,
CRC zip semua valid).

### 5.2 XOR config `.bin`

Semua config `.bin` yang diunduh dari EntryPoint didekripsi **XOR dengan string `DragonBall`**
(dari `LaunchActivity.feedtonight`, baris 231–237 badan boot). TERUJI terhadap
`setting_BS_Android.bin` live → JSON valid.

### 5.3startGame → JS

MainActivity memanggil `EgretNativeAndroid.callExternalInterface("startGame", json)` atau
`webView.evaluateJavascript("javascript:startGame('<base64>')")` dengan JSON berisi:
`packageName, language, loginServer (host:port), loginKey, screenSize, version, versionConfig,
jsonB64Map (peta resource), thirdParams, clientParams` → game JS menyimpan ke
`window.loginServer` dll. (`index-native.html`: `getLoginServer()`).

---

## 6. Alur Boot Lengkap (map method, untuk dokumentasi)

```
LaunchActivity.onCreate
 └─ init "BS" (cointhreat...seventygenom.hardlinerspare.cointhreat("BS"))
 └─ pursetruechild()                    minta izin WRITE_EXTERNAL_STORAGE
 └─ Thread → ironoriginhoblike(this)    [badan boot]
     ├─ binKeyData = "DragonBall".getBytes(UTF8)         ← kunci XOR
     ├─ extDir = getExternalFilesDir("") + "/game/"      ← preloadPath
     ├─ load assets config_BS.properties → EntryPoint[] (Base64 decode)
     ├─ GET {ep}/setting_BS_Android.bin?rnd=  (retry 10) ─┐
     ├─ GET {ep}/com_db_local.bin?rnd=        (retry 3)  ─┤ XOR DragonBall → JSON → mSdkParams
     ├─ (gagal → tampil error & opsi unduh OBB ke Android/obb/)
     ├─ assets open game/infoinfo.bin → "voyage_fancy_radius|pschent_full_tired"
     ├─ vvcc.bin (XXTEA) → versionConfig ; vc_obb (opsional)
     └─ dishbesideshockey()               [alur update]
         ├─ CurrentMode: {mode}  IsFilterIP: {feedtonight()}   (cek IP via currentNetIpUrl + blackWhiteL[NetSegment])
         ├─ mode 0 → error network ; mode 3/4 → jalur khusus
         ├─ ekstrak zip lokal ke preloadPath (decompressing)
         ├─ base = preloadPath + escape(urlGame)
         ├─ baca {base}/base.version & {base}/resource.version (fallback: assets)
         ├─ GET {update}/base.version / resource.version / size.json / upgrade.json
         ├─ unduh base.zip / {versi}.zip / all.zip bila perlu → ekstrak ke {base}
         └─ selesai → start MainActivity (extra preloadPath)
MainActivity.onCreate
 ├─ mGameUrl = mSdkParams.singersmooth()      (url / webUrl / tsUrl sesuai mode+webVer)
 ├─ vvcc.bin, vvcc_tc.bin, vc_obb, infoinfo.bin dibaca ulang
 ├─ QuickGame SDK init (productCode)
 ├─ EgretNativeAndroid: cachePath=/data/data/com.db.local/files, preloadPath=ext/game
 │   registerExternalInterface: startGame, startTsGame, switchAccount, userCenter,
 │   changeView, changeLanguage, refresh, pei, giveLike, contact, report2Third, openURL, sendLog
 └─ initialize(mGameUrl) → runtime Egret load game
[JS game]
 ├─ startGame(b64json) → window.loginServer dsb.
 ├─ loginClient.connectToServer(loginServer:port)   socket.io v2
 ├─ GetServerList → serverList[] (url per server)
 ├─ mainClient.connectToServer(serverItem.url)
 ├─ chatClient.connectToServer(serverItem.chaturl)
 └─ dungeonClient.connectToServer(serverItem.dungeonurl)
```

---

## 7. Implikasi untuk Rencana Local Server (127.0.0.1:<port>)

Titik-titik penggantian (urutan prioritas):

1. **Config bootstrap** — opsi paling bersih: edit `assets/config_BS.properties`
   EntryPoint1 (Base64) → arahkan ke local server yang menyajikan
   `setting_BS_Android.bin` (XOR `DragonBall`, JSON sama seperti 4.C dengan `url`,
   `update`, `loginServer`, `loginPort` diubah ke 127.0.0.1).
   Alternatif cepat untuk eksperimen tanpa rebuild APK: hosts/DNS/spoof
   `configus.sjmobilegame.com` + `dragonh5cdn.popoh5.com` + `login.popoh5.com` ke 127.0.0.1
   (cleartext diizinkan, `usesCleartextTraffic=true`).
2. **URL game (`url`)** — HTML + JS bisa di-host local (isi base.zip/all.zip sudah
   tertangan di zip terdekripsi; APK juga sudah punya salinan versi 11220 di assets).
3. **Update server (`update`)** — untuk mode offline, cukup sediakan
   `base.version`=110 & `resource.version` sama dengan lokal → update dilewati
   ("最新资源文件，无需更新").
4. **Login server (socket.io v2, port 610)** — perlu server socket.io v2 (EIO=3)
   yang menjawab `GetServerList` dan login verify (kontrak `loginKey`/TEA di JS).
5. **Game/chat/dungeon server** — alamatnya dinamis dari `GetServerList`; server
   local cukup mengembalikan entri berisi URL ke 127.0.0.1:<port lain>.
6. **Anti-galat lokal**: hapus `Android/data/com.db.local/files/game/` saat ganti
   URL game (folder cache mengikuti escape URL), dan ingat fallback
   `serversetting.json` di host game juga menyebut `login.popoh5.com:610`.

---

## 8. Artefak Analisis (lokasi di workspace)

| Artefak | Path |
|---|---|
| APK asli (sha256 terverifikasi) | `/home/z/db-apk-analysis/original.apk` |
| APK ter-unzip | `/home/z/db-apk-analysis/apk/` |
| Dekompil Java (bersih) | `/home/z/db-apk-analysis/jadx-out/sources/` |
| Dekompil Java (--show-bad-code) | `/home/z/db-apk-analysis/jadx-badcode/sources/` |
| Dekompil resource | `/home/z/db-apk-analysis/jadx-res/` |
| Dekompil runtime-dex.jar | `/home/z/db-apk-analysis/jadx-rtdex/sources/` |
| Dekriptor XXTEA "onepiece" (Python) | `/home/z/db-apk-analysis/tools/onepiece_decrypt.py` |
| Config live terdekripsi | `/tmp/setting.bin` (XOR DragonBall → JSON) |
| Zip game terdekripsi | `/home/z/db-apk-analysis/decrypted/*.zip` |
| Isi zip game | `/home/z/db-apk-analysis/game_extracted/{voyage,pschent}/` |

Repo `db-apk` di `/home/z/db-apk-analysis/db-apk/` **tidak diubah**: tidak ada commit,
tidak ada push, tidak ada penulisan file di dalamnya. Dokumen readme.md ini disimpan
di luar repo.

---

## 9. Status Akhir Pekerjaan

- [x] Clone + verifikasi APK (LFS, sha256)
- [x] Bedah manifest, dex, native lib, assets
- [x] **Fase 1** — struktur folder `android/data` lengkap + bukti kode + aturan escape URL
- [x] **Fase 2** — semua endpoint di APK + endpoint runtime (config live diunduh & didekripsi)
- [x] Pemecahan enkripsi XXTEA "onepieceencrypts" + dekripsi penuh 2 zip game (CRC valid)
- [x] Uji liveness semua endpoint (login server masih hidup!)
- [x] Semua terdokumentasi di readme.md tunggal ini
- [ ] (Tahap berikutnya, belum dieksekusi) penerapan local server 127.0.0.1:<port>
