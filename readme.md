# DB APK — Analisis APK Original com.db.local — REVISI FAKTA (audit ulang)

> Dokumen tunggal. Hasil bedah menyeluruh APK `db-local(Original-DO NOT DELETE!).apk`
> dari repo `https://github.com/anztdree/db-apk.git`.
>
> **REVISI ini lahir dari audit ulang berbasis FAKTA** (permintaan owner: bukan asumsi).
> Setiap klaim kini menyertai penunjuk bukti: file + baris kode / byte / hasil uji langsung.
> Kesalahan versi sebelumnya didaftar terbuka di **Bagian 10 (Daftar Koreksi)**.
>
> Riwayat git repo tidak pernah di-rewrite — readme hanya di-push sebagai commit tambahan.
> Workspace analisis: `/home/z/db-apk-analysis/` (di luar repo).

Tanggal analisis awal: 21 Nov 2026 — Tanggal audit ulang fakta: 21 Nov 2026 (Asia/Jakarta)
Commit push readme di repo: `3239d05` (append-only; lalu commit revisi berikutnya)

---

## 0. Ringkasan Eksekutif (semua nilai terverifikasi ulang)

| Item | Nilai (FAKTA) | Bukti |
|---|---|---|
| SHA256 APK | `b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218` | `sha256sum` cocok persis dengan LFS pointer repo |
| Ukuran | 104.945.181 byte | `stat` + LFS pointer `size` |
| Package | `com.db.local` | AndroidManifest.xml (androguard, dari bytes AXML asli) |
| versionName / versionCode | `1.0.1` / `1` | idem |
| minSdk / targetSdk | 21 / 30 | idem |
| Label | `DB - Local` | idem |
| Application class | `com.zhuhuan.game.Application` | idem |
| Launcher | `com.zhuhuan.game.LaunchActivity` | `a.get_main_activity()` |
| File APK ter-unzip | 3.668 file, 131 MB | `find \| wc -l`, `du` |
| classes.dex | 6.697.104 byte — **6.108 class**, 42.826 method, 48.243 string | header DEX `class_defs_size@0x60` dll (koreksi dari "1.879 class") |
| Engine game | Egret Native — `libegret.so` (arm64 16.975.928 B / v7a 11.181.200 B) + `assets/runtime-dex.jar` | `ls lib/`, simbol JNI `org.egret.*` di .so |
| SDK wrapper | t4game/zhuhuan + QuickGame SDK (`com.quickgame.android.sdk.*`) | manifest (13 aktivitas HW/GooglePlay/Onestore) |
| Protokol socket | socket.io **v2.1.2** (EIO=3) | `js/socket.io.min_57ea9db7.js` (APK): `version:"2.1.2"`; handshake live `96:0{...}` EIO=3 |
| Enkripsi resource | XXTEA varian magic `onepieceencrypts` | uji dekripsi ulang: `infoinfo.bin`→38 B valid, 2 zip CRC semua valid |
| Enkripsi config `.bin` | XOR kunci ASCII `DragonBall` | uji ulang live: `setting_BS_Android.bin` → JSON valid |
| `Facebook App ID` | `563635417808635` (scheme `fb563635417808635`) | `res/values/strings.xml:84-85` |

**Status server (uji ulang segar hari ini, 21 Nov 2026):**

| Server | Status teruji |
|---|---|
| `https://configus.sjmobilegame.com` (config) | **HIDUP** — HTTP 200, 711 B |
| `https://dragonh5cdn.popoh5.com` (CDN+update) | **HIDUP** — HTTP 200 |
| `https://login.popoh5.com:610` (login socket.io) | **HIDUP** — handshake OK: `96:0{"sid":"AinptGYFIyuMMmcfbn0Z","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}2:40` |
| `https://login.popoh5.com:510` (loginWebPort) | merespons `{"code":404,"msg":"没有开通"}` (belum dibuka) |
| `login.popoh5.com:443` | **MATI** — koneksi ditolak (refused) |
| `http://118.24.68.91:7003` (tsLoginServer) | **MATI** — timeout 12 s |
| `http://log.sjflaregame.com:10000` (battle-log) | **DIBLOKIR** — HTTP 302 → `dnspod.qcloud.com/static/webblock.html?d=log.sjflaregame.com:10000` (domain diparked Tencent) |
| `https://ssweb.episodezz.com` (ThinkingData) | **TIDAK TERJANGKAU** — timeout |
| `http://qsdk.t4game.com` (SDK t4game) | **HIDUP** — HTTP 200 |

Alur besar (semua langkah berbukti kode, lihat Bagian 6):
APK boot → unduh config terenkripsi (XOR `DragonBall`) → dapat URL game → ekstrak
resource lokal → cek update → load game Egret → JS konek login server (socket.io) →
`GetServerList` → konek game/chat/dungeon server (URL dinamis per-server).

---

## 1. Cara APK Diperoleh (dari repo)

1. Clone repo (PAT). Working tree hanya berisi 1 file: `db-local(Original-DO NOT DELETE!).apk` berukuran 134 byte.
2. File itu adalah **Git LFS pointer**, bukan APK (isi pointer tercetak persis di bawah):

   ```
   version https://git-lfs.github.com/spec/v1
   oid sha256:b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218
   size 104945181
   ```

3. Objek LFS diunduh lewat GitHub LFS Batch API (`POST /info/lfs/objects/batch`) → URL bertanda tangan → `original.apk`.
4. Verifikasi ulang: `sha256sum original.apk` = `b308c44d…bc2218`, `stat -c %s` = `104945181` — **cocok persis** dengan pointer. APK asli, tidak diubah.

`git lfs` tidak terpasang di sandbox (tanpa sudo); API langsung menghasilkan byte identik (bukti: hash).

---

## 2. Struktur APK (3.668 file, 131 MB ter-unzip)

```
original.apk
├── AndroidManifest.xml          package com.db.local, label "DB - Local"
├── classes.dex                  6.697.104 B — 6.108 class (header DEX, bukan tebakan)
├── lib/
│   ├── arm64-v8a/libegret.so        16.975.928 B — engine Egret (simbol org.egret.*)
│   ├── arm64-v8a/libgavesec.so      30.184 B
│   └── armeabi-v7a/ (2 file, 11.181.200 B / 21.724 B)
├── assets/
│   ├── game/                    2.524 file — resource nama acak (obfuscated)
│   │   ├── infoinfo.bin         [XXTEA] → "voyage_fancy_radius|pschent_full_tired" (38 B)
│   │   ├── voyage_fancy_radius.bin  [XXTEA] → base.zip  (1.233.549 B, 161 entri, CRC OK)
│   │   ├── pschent_full_tired.bin   [XXTEA] → all.zip    (9.428.692 B, 741 entri, CRC OK)
│   │   ├── vvcc.bin             [XXTEA] → JSON peta nama resource (183.712 B)
│   │   ├── vvcc_tc.bin          [XXTEA] → JSON peta (10.907 B)
│   │   ├── base.version         "110"
│   │   ├── resource.version     "11220"  (server kini 11390)
│   │   └── resource/...         file resource nama-acak
│   ├── config.properties        EntryPoint1 (Base64) → http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android
│   ├── config_BS.properties     EntryPoint1/2 (Base64) → server config channel "BS"
│   ├── QHinfo                   http://qsdk.t4game.com
│   ├── supplierconfig.json      supplier vivo appid 100215079
│   └── runtime-dex.jar          Egret runtime + WebSocket + HTTPDNS 119.29.29.29
├── res/ (1.111 file) + resources.arsc
└── META-INF/
```

**Permission lengkap (androguard, 12 entri — koreksi: versi lama hanya mencatat 7):**

```
READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, INTERNET, ACCESS_NETWORK_STATE,
ACCESS_WIFI_STATE, CHANGE_NETWORK_STATE, WAKE_LOCK, SYSTEM_ALERT_WINDOW,
SYSTEM_OVERLAY_WINDOW, REORDER_TASKS, com.android.vending.BILLING,
com.db.local_com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE
```

**Atribut application terverifikasi:** `requestLegacyExternalStorage=true`,
`usesCleartextTraffic=true`, `networkSecurityConfig=@xml/network_security_config`.
Isi file asli `res/xml/network_security_config.xml` (baca dari APK):

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true"/>
</network-security-config>
```

→ **HTTP polos diizinkan ke SEMUA domain (base-config)** — krusial untuk redirect local server.

Manifest juga memuat Facebook App ID `563635417808635` (scheme `fb563635417808635`,
`strings.xml` baris 84–85; authority FacebookContentProvider baris 91).

Aktivitas SDK terverifikasi dari manifest: `com.quickgame.android.sdk.activity.*`
(HW/Huawei Login/Pay/Account, GooglePlayActivity, OnestorePayActivity, BindEmail,
AccountRecover, TrashAccount, RedeemCode, BindThirdLogin).

---

## 3. FASE 1 — Struktur Folder yang Dibuat di `android/data`

Semua nilai dari kode ter-decompile, dengan **nomor baris persis**.

### 3.1 Penentu path (bukti kode)

| Kode | Lokasi persis | Nilai |
|---|---|---|
| field `ironoriginhoblike` | `jadx-out/sources/com/zhuhuan/game/LaunchActivity.java:59` | `public String ironoriginhoblike = "/game/";` |
| field `feedtonight` | `jadx-out/sources/com/zhuhuan/game/LaunchActivity.java:60` | `public String feedtonight = "DragonBall";` |
| `getExternalFilesDir("")` | LaunchActivity (badan boot) | `/storage/emulated/0/Android/data/com.db.local/files` |
| `cachePath` | `jadx-out/sources/org/egret/egretnativeandroid/EgretNativeAndroid.java:50` | `aVar.cachePath = activity.getFilesDir().getAbsolutePath();` |
| nama OBB | `jadx-badcode/sources/com/zhuhuan/game/LaunchActivity.java:360-361` | `"main." + versionCode + "." + packageName + ".obb"`; `getObbDir()` |
| cek marker OBB | `.../reweavingsiamesedpropertylessnesses/worktopichardtails.java:29-36` | `new File(mDestPath + "/" + obbFileName + ".done").exists()` |
| escape URL → folder | `reweavingsiamesedpropertylessnesses.java` (singersmooth), juga `MainActivity.java:1447`, `feedtonight.java:115`, `cointhreat.java:147` | substring sampai `/` terakhir → `"://"→"/"` → `":"→"#0A"` |

Escape URL persis (dari `reweavingsiamesedpropertylessnesses.singersmooth(String)`):

```java
String substring = str.substring(0, lastIndexOf('/') + 1);   // sampai '/' terakhir
return replace(replace(substring, "://", "/"), ":", "#0A");   // 2 penggantian
```

### 3.2 Pohon folder hasil install & jalankan APK

**Eksternal (`Android/data`):**

```
/storage/emulated/0/
├── Android/data/com.db.local/
│   └── files/                          ← getExternalFilesDir("")
│       └── game/                       ← konstanta "/game/"
│           └── https/                  ← hasil escape "://"→"/" dari URL game
│               └── dragonh5cdn.popoh5.com/
│                   └── bs/             ← bagian path URL game live (…/bs/index-native.html)
│                       ├── base.version        "110"
│                       ├── resource.version    "11220" → naik per update
│                       ├── manifest.json, index-native.html
│                       ├── js/                 kode Egret (egret, main, socket.io, dst)
│                       ├── resource/           json/, language/, assets/
│                       └── main.1.com.db.local.obb.done   (marker, hanya jika ada OBB)
└── Android/obb/com.db.local/
    └── main.1.com.db.local.obb         (getObbDir(); hanya jika vc_obb aktif)
```

**Contoh fakta escape (diuji dari rumus di 3.1):**
- URL live `https://dragonh5cdn.popoh5.com/bs/index-native.html` → folder
  `files/game/https/dragonh5cdn.popoh5.com/bs/`
- URL `http://127.0.0.1:8080/index-native.html` → folder
  `files/game/http/127.0.0.1#0A8080/`  (TITIK DUA JUGA jadi `#0A` — koreksi dari versi lama
  yang menulis `127.0.0.1:8080` dengan titik dua di nama folder)

Catatan operasional: saat mengganti URL game, **hapus isi `files/game/`** agar tidak
campur cache lama (folder cache mengikuti escape URL).

**Internal (pelengkap):**

```
/data/data/com.db.local/
├── files/                ← cachePath Egret runtime (EgretNativeAndroid.java:50)
├── shared_prefs/         ← state SDK QuickGame, state login
└── code_cache/
```

### 3.3 Urutan penulisan folder saat boot (dengan bukti langkah)

1. `LaunchActivity.onCreate` → init channel `BS` → minta izin storage
   (`f886singersmooth = {"android.permission.WRITE_EXTERNAL_STORAGE"}` — `LaunchActivity.java:58`).
2. Boot thread: baca `config_BS.properties` → Base64 decode EntryPoint (lihat 4.A).
3. Unduh config (pola persis, `jadx-badcode/.../LaunchActivity.java:291,300`):
   `{EntryPoint}/setting_BS_Android.bin?rnd={random}` (retry 10) dan
   `{EntryPoint}/com_db_local.bin?rnd={random}` (retry 3; `packageName.replace(".","_")`).
4. XOR `DragonBall` → JSON → `mSdkParams`.
5. `getExternalFilesDir("") + "/game/"` = preloadPath.
6. `assets/game/infoinfo.bin` dekripsi → nama 2 zip lokal → ekstrak ke preloadPath.
7. `vvcc.bin` + `vvcc_tc.bin` → JSON peta resource + versionConfig (`vc_obb` opsional).
8. Alur update (lihat 4.E) → MainActivity dengan extra preloadPath.

---

## 4. FASE 2 — Inventaris Endpoint Lengkap

### 4.A Endpoint di dalam APK (byte asli — metode: Base64 decode + byte-scan)

| # | Endpoint | Sumber & bukti | Keterangan |
|---|---|---|---|
| 1 | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | `assets/config.properties` `EntryPoint1` — Base64 `aHR0cDovL2RyYWdvbi5zam1vYmlsZWdhbWUuY29tOjgy…` | Channel kosong (channel aktif = "BS") |
| 2 | `https://configus.sjmobilegame.com/bs/db/android` | `assets/config_BS.properties` `EntryPoint1` — Base64 terverifikasi decode | **Server config aktif** |
| 3 | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` | `assets/config_BS.properties` `EntryPoint2` | Backup config (Tencent COS) |
| 4 | `http://qsdk.t4game.com` | `assets/QHinfo` (teks mentah 1:1) | Domain SDK t4game |
| 5 | `http://119.29.29.29/d?dn=` | di dalam `assets/runtime-dex.jar` → `classes.dex` string #1244 (HTTPDNS Tencent) | HANYA untuk retry POST pelaporan data — bukan WebSocket game (lihat Baris 10 #11) |

**Byte-scan menyeluruh APK** (`tools/urlscan.py`, regex `https?|wss?://` atas 3.668 file):
total 852 kecocokan mentah, terurai:

| Sumber | Jumlah URL | Isi |
|---|---|---|
| `classes.dex` | 81 | semua SDK pihak ketiga: Facebook, Google (oauth/fitness/games scopes), Adjust (app/gdpr/subscription .com/.net.in/.world), AppsFlyer (template `%sadrevenue|%sapp|%sattr|%simpression|%sinapps|%sregister|%sstats…`), login.live.com, login.yahoo.com, twitter.com, linkedin.com, paypal.com, **web-pay.line.me**, play.google.com, pagead2.googlesyndication.com |
| `libegret.so` (2 ABI) | 12 | `http://local/index.html` (URL default engine), curl.haxx.se (doc cookie libcurl) |
| `AndroidManifest.xml` | 1 | hanya namespace `schemas.android.com` |
| `assets/game/resource/**/*.jpg` | ±700 | **noise** — metadata XMP Adobe (`ns.adobe.com/xap/…`), bukan endpoint |
| `res/**/*.xml` | ±55 | **noise** — namespace Android (`schemas.android.com/apk/res/android`) |
| `assets/QHinfo` | 1 | `http://qsdk.t4game.com` |

**Bukti negatif (penting — domain TIDAK di-hardcode di APK):**
`rg -a` atas seluruh bytes APK → `popoh5`=0, `sjmobilegame`=0 (hanya ada versi Base64),
`dragonh5cdn`=0, `sjflaregame`=0, `parajoymobile`=0, `episodezz`=0, `119.29.29.29`=0
(nya di dalam zip runtime-dex.jar), `qsdk.t4game`=1.

### 4.B Skema JSON config (field yang dibaca kode — semua terverifikasi di `LaunchActivity.java`)

Baris persis: `webVer`:625, `url`:633, `webUrl`:640, `wpei`:763, `wpeiurl`:769.
Daftar lengkap dari konstanta string class: `Android<versionCode>`, `testMode`, `webVer`,
`nativeRender`, `url`, `webUrl`, `tsUrl`, `tsWebUrl`, `update`, `forceBuild`, `noForceBuild`,
`forceUrl`, `checkTime`, `tsLoginServer`, `loginServer`, `loginKey`, `loginPort`,
`loginWebPort`, `productPrefix`, `productIdMap`, `gameIcon`, `extra`, `clientParams`,
`peiUrl`, `wpei`, `wpeiurl`, `blackWhiteL`, `blackWhiteLNetSegment`, `currentNetIpUrl`,
`unityGameSdkLogin`, `checkGoogleTicketUrl`, `gameLanguage`, `nativeUnzipJson`
(+ `healthNotice`, `heishi`, `jiuwan` dari dump string class).

Pemilihan URL game: mode `2` → `tsWebUrl` (jika webVer≥SDK) / `tsUrl`; selain itu
`webUrl` (web mode) / `url` (native mode).

### 4.C Config LIVE — diunduh ulang & didekripsi ulang SAAT AUDIT INI

`https://configus.sjmobilegame.com/bs/db/android/setting_BS_Android.bin?rnd=…`
→ HTTP 200, 711 byte → XOR `DragonBall` → (hasil asli utuh):

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

`com_db_local.bin` → **HTTP 404** (uji ulang; file per-package tidak ada di server).

### 4.D Endpoint runtime hasil config (status uji segar)

| # | Endpoint | Peran | Uji live |
|---|---|---|---|
| 1 | `https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935` | **URL game** (mGameUrl) | **200 OK** (10.792 B) |
| 2 | `https://dragonh5cdn.popoh5.com/bs/upgrade` | **Update server** resource | **200 OK** (lihat 4.E) |
| 3 | `https://login.popoh5.com:610` | **Login server (socket.io v2)** | **HANDSHAKE OK** (`sid":"AinptGYFIyuMMmcfbn0Z"`) |
| 4 | `https://login.popoh5.com:510` | loginWebPort (web mode) | `{"code":404,"msg":"没有开通"}` (tidak dibuka) |
| 5 | `login.popoh5.com:443` | tanpa port eksplisit | **connection refused** |
| 6 | `http://dragon.sjmobilegame.com:82/bs103/index-native.html` | tsUrl (mode TS) | **200 OK** |
| 7 | `http://118.24.68.91:7003` | tsLoginServer (mode TS) | **MATI** (timeout 12 s) |
| 8 | `http://log.sjflaregame.com:10000/error/battle/client` | battle-log report (di `index-native.html` live: `window.reportBattlleLog`) | **302 → webblock.qcloud (DIBLOKIR)** |
| 9 | `http://www.parajoymobile.com/privacy_en.html` | privacyUrl (di HTML live) | ada di HTML live |
| 10 | `https://ssweb.episodezz.com` | **ThinkingData** — appId `da5e91639fc948399ba6c9523f593944`, `ThinkingdataServerUrl` (main.min live) | **TIDAK TERJANGKAU** (timeout) |
| 11 | `http://qsdk.t4game.com` | QHinfo | **200 OK** |

### 4.E Protokol update server — BUKTI KODE PERSIS (koreksi atribusi)

Kelas `GameUpdateUtil` **TIDAK ADA** di APK (versi lama salah nama). Protokol aslinya
ada di boot thread `LaunchActivity` — bukti `jadx-badcode/sources/com/zhuhuan/game/LaunchActivity.java`:

```
baris 730 : {update}/base.version?rnd=%f
baris 742 : {update}/resource.version?rnd=%f
baris 779 : {update}/base.zip?v={baseVer}
baris 784 : {update}/all.zip?v={baseVer}{resVer}
baris 786 : {update}/upgrade.json?rnd=%f
baris 810 : {update}/size.json?rnd=%f    (juga baris 865)
```

Hasil unduh live (faktanya juga): `base.version`=110, `resource.version`=11390,
`size.json`=`{"base":"31015076","11389":252562,"11388":252562,"11387":252562,"11386":252562,"all":19385098}`,
`upgrade.json`=`{"11389":"11389_11390.zip","11388":"11388_11390.zip","11387":"11387_11390.zip","11386":"11386_11390.zip","all":"all.zip"}`.

Tambahan fakta: versi lokal juga dibaca **dari file lokal** dulu —
`reweavingsiamesedpropertylessnesses.feedtonight(activity, preloadPath, gameUrl)` membaca
`{preloadPath + escape(url)}/base.version` & `/resource.version` via `FileInputStream`
(dengan fallback ke `assets/game/*.version` bila konten "0").

### 4.F Fallback config di server game (terverifikasi live)

`{host game}/resource/properties/serversetting.json` — dipanggil JS:
`RES.getResByUrl(o+"resource/properties/serversetting.json?v="+Math.random(),…)` (main.min live).
Uji live: **HTTP 200** → `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}`.

### 4.G Arsitektur koneksi runtime game (bukti dari JS APK + JS live)

Bukti dari JS yang TERTANAM di APK (`game_extracted/pschent/js/main.min_7eae4d6e.js`):
`GetServerList`(1), `loginServer`(5), `loginserver`(4), `chaturl`(2), `dungeonurl`(5),
`teamServerHttpUrl`(17), `teamChatRoomId`(12), `guildRoomId`(12), `worldRoomId`(4).
`socket.io.min_57ea9db7.js`: `version:"2.1.2"`. Crypto: `TEA` (244 referensi) + `verify`.

Kontrak `GetServerList` (main.min live): `{type:"User",action:"GetServerList",userId,subChannel,channel}`.

```
[JS game] --socket.io v2.1.2--> loginClient  --> loginServer (login.popoh5.com:610)
   → serverList[] : serverId, name, online, new, url (game server),
     teamChatRoomId, worldRoomId, guildRoomId, dungeonurl, chaturl, serverTag, ...
[JS game] --> mainClient    → serverItem.url        (GAME SERVER per-server)
[JS game] --> chatClient    → serverItem.chaturl
[JS game] --> dungeonClient → serverItem.dungeonurl / teamServerHttpUrl
[JS game] --HTTP POST--> ts.httpReqHandler(url, {type, action, ...})
        → {ret:0, data:"..."} atau {compress:true, data:"<LZString UTF16>"}
```

Alamat game/chat/dungeon server **dinamis** dari `GetServerList` — tidak hardcode di APK.

### 4.H Endpoint SDK pihak ketiga (dari byte-scan classes.dex — bukti 4.A)

| SDK | Endpoint (byte asli) |
|---|---|
| Facebook (App ID 563635417808635) | graph.facebook.com / graph-video.%s, facebook.com, fb.gg |
| Google Play Services | accounts.google.com, www.googleapis.com (auth scopes), play.google.com, pagead2.googlesyndication.com |
| Adjust | app/gdpr/subscription.adjust.com/.net.in/.world |
| AppsFlyer | template `%sapp.%s`, `%sattr.%s/api/v`, `%sregister.%s/api/v`, `%sstats.%s/stats`, `%simpression.%s`, `%sinapps.%s/api/v`, `%sadrevenue.%s/api/v`, dll. |
| Line Pay | web-pay.line.me |
| Tencent HTTPDNS | 119.29.29.29/d?dn= (runtime-dex.jar) |

### 4.I Halaman/community yang tertanam di JS live (bukti string di main.min live)

```
https://discord.gg/5dYCqtCrdJ
https://t.me/AlphaGamingIran
https://www.instagram.com/alphagamingiran
https://www.facebook.com/Super-Fighter-Idle-108619837471134/
```

---

## 5. Enkripsi & Kripto yang Ditemukan (lengkap + uji ulang)

### 5.1 XXTEA varian "onepiece" (resource game)

Dekriptor: `/home/z/db-apk-analysis/tools/onepiece_decrypt.py` — port literal
`customreading.nationalcommunitymissing`. Layout file:

```
byte[16:32] = magic "onepieceencrypts"
byte[32:48] = key tersimpan, dikembalikan dengan XOR "onepiecedecryptk"
byte[48:]   = payload XXTEA varian (long LE 32-bit, 1 pass, delta 0x9E3779B9)
padding     = byte terakhir payload = jumlah byte pad (dibuang)
```

**Uji ulang audit ini (semua lulus):**
- `infoinfo.bin` → 38 B: `voyage_fancy_radius|pschent_full_tired`
- `vvcc.bin` → 183.712 B JSON peta resource
- `vvcc_tc.bin` → 10.907 B JSON peta
- `voyage_fancy_radius.zip` → 161 entri, **CRC semua valid**
- `pschent_full_tired.zip` → 741 entri, **CRC semua valid**

### 5.2 XOR config `.bin`

Semua config `.bin` dari EntryPoint didekripsi XOR string `DragonBall`
(`LaunchActivity.java:60` field `feedtonight`; variabel `binKeyData` di badan boot).
Uji ulang live → JSON valid (lihat 4.C).

### 5.3 startGame → JS

MainActivity memanggil `EgretNativeAndroid.callExternalInterface("startGame", json)` atau
`webView.evaluateJavascript("javascript:startGame('<base64>')")` dengan JSON berisi:
`packageName, language, loginServer (host:port), loginKey, screenSize, version, versionConfig,
jsonB64Map, thirdParams, clientParams` → JS menyimpan ke `window.loginServer` dll.
(`index-native.html` live: fungsi `getLoginServer()` terverifikasi ada.)

---

## 6. Alur Boot Lengkap (dengan penunjuk bukti)

```
LaunchActivity.onCreate
 └─ init channel "BS" → minta izin WRITE_EXTERNAL_STORAGE (LaunchActivity.java:58)
 └─ Thread badan boot (jadx-badcode LaunchActivity)
     ├─ binKeyData = "DragonBall".getBytes(UTF8)                 ← :60
     ├─ extDir = getExternalFilesDir("") + "/game/"              ← :59
     ├─ load assets config_BS.properties → EntryPoint (Base64)
     ├─ GET {ep}/setting_BS_Android.bin?rnd=   (retry 10)        ← :291
     ├─ GET {ep}/com_db_local.bin?rnd=         (retry 3)         ← :300
     ├─ XOR DragonBall → JSON → mSdkParams
     ├─ assets game/infoinfo.bin → "voyage_fancy_radius|pschent_full_tired"
     ├─ vvcc.bin (XXTEA) → versionConfig; vc_obb opsional (assets; jika tanpa OBB:
     │   log "没有Obb文件" — reweavingsiamesedpropertylessnesses.java:301)
     ├─ OBB: nama "main."+versionCode+"."+pkg+".obb" di getObbDir()
     │   + marker .done (badcode:360-361; worktopichardtails.java:29-36)
     └─ alur update (badcode:730-865 — lihat 4.E)
         └─ selesai → start MainActivity (extra preloadPath)
MainActivity.onCreate
 ├─ mGameUrl = SdkParams pilih url/webUrl/tsUrl (webVer: LaunchActivity.java:625)
 ├─ QuickGame SDK init
 ├─ EgretNativeAndroid: cachePath=getFilesDir() (:50), registerExternalInterface
 │   startGame, startTsGame, switchAccount, userCenter, changeView, changeLanguage,
 │   refresh, pei, giveLike, contact, report2Third, openURL, sendLog
 └─ initialize(mGameUrl) → Egret load game dari preloadPath + escape(url)
[JS game]
 ├─ startGame(b64json) → window.loginServer dsb.
 ├─ loginClient.connectToServer(loginServer:port)   socket.io v2.1.2
 ├─ GetServerList → serverList[] (url per server)
 ├─ mainClient.connectToServer(serverItem.url)
 ├─ chatClient.connectToServer(serverItem.chaturl)
 └─ dungeonClient.connectToServer(serverItem.dungeonurl)
```

---

## 7. DAFTAR PERUBAHAN ENDPOINT KE LOCAL (rencana — setiap baris berlandaskan fakta Bagian 3–6)

### 7.1 Prinsip (fakta pendukung)

- `base-config cleartextTrafficPermitted="true"` → HTTP polos ke `127.0.0.1` LEGAL,
  tanpa perlu mengubah manifest/security config (Bukti: `res/xml/network_security_config.xml`).
- Satu port HTTP (contoh 8080) cukup untuk config + CDN game + update;
  satu port socket.io (contoh 610) untuk login; game/chat/dungeon port bebas —
  URL-nya datang dari respons `GetServerList` (kontrak 4.G).
- Folder cache device mengikuti escape URL → setelah mengganti URL, WAJIB hapus
  `Android/data/com.db.local/files/game/` (Bukti: 3.1–3.2).

### 7.2 Tabel perubahan endpoint (list yang diminta)

| # | Endpoint / field asli (FAKTA) | Nilai BARU untuk local | Cara ubah (bukti lokasi) | Server local harus menyediakan |
|---|---|---|---|---|
| 1 | `assets/config_BS.properties` EntryPoint1 = `https://configus.sjmobilegame.com/bs/db/android` | `http://127.0.0.1:8080/bs/db/android` | Edit asset (Base64 re-encode) → rebuild APK; ATAU spoof hosts/domain ke 127.0.0.1 | `GET /bs/db/android/setting_BS_Android.bin` → JSON (4.C dengan field diubah) di-XOR `DragonBall`; `GET /bs/db/android/com_db_local.bin` → opsional (JSON override/kosong di-XOR `DragonBall`) — boot tetap jalan bila 404 (Baris 10 #12) |
| 2 | field `url` = `https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935` | `http://127.0.0.1:8080/bs/index-native.html` | Di dalam JSON `setting_BS_Android.bin` | Static file: `index-native.html` + `manifest.json` + `js/*` + `resource/*` (salinan dari zip terdekripsi; atau unduh v11390 sekali dari CDN lama) |
| 3 | field `update` = `https://dragonh5cdn.popoh5.com/bs/upgrade` | `http://127.0.0.1:8080/bs/upgrade` | idem | `GET base.version` (=110), `GET resource.version` (=11220 untuk offline penuh; =11390 bila pakai resource baru), `GET size.json`, `GET upgrade.json`, `GET *.zip` (protokol 4.E; bila versi == lokal, update dilewati) |
| 4 | `loginServer` = `https://login.popoh5.com` + `loginPort` = 610 | `http://127.0.0.1` + `loginPort` = 610 | idem | Server socket.io **v2.1.2 (EIO=3)** di port 610: handshake (`96:0{...}`) + event login + `GetServerList` (kontrak 4.G) + verify `loginKey` (TEA) |
| 5 | `loginWebPort` = 510 | 510 (opsional) | idem | Web-mode login — native tidak memakainya (uji live: belum dibuka) |
| 6 | `serverItem.url` / `.chaturl` / `.dungeonurl` (dinamis dari GetServerList — tidak ada di APK) | `http://127.0.0.1:<port_game>` / `<port_chat>` / `<port_dungeon>` | Isi respons `GetServerList` dari server local kita | Server socket.io per layanan game/chat/dungeon |
| 7 | Fallback `{host game}/resource/properties/serversetting.json` = `login.popoh5.com:610` | `loginserver`: `http://127.0.0.1:610` | File statis di host game local (Bukti 4.F) | `GET /bs/resource/properties/serversetting.json` |
| 8 | `tsUrl` / `tsLoginServer` (mode TS) | opsional → local atau biarkan mati | idem JSON config | Tidak perlu untuk mode normal |
| 9 | HTTPDNS `http://119.29.29.29/d?dn=` | **TIDAK PERLU DIUBAH** | Baris 10 #11 — hanya dipakai retry POST pelaporan data | — |
| 10 | `QHinfo` (qsdk.t4game.com) + SDK Facebook/Google/Adjust/AppsFlyer/Line | **TIDAK PERLU DIUBAH** untuk gameplay | byte-scan 4.A | Opsional di-stub untuk offline penuh |

### 7.3 Urutan eksekusi (mengikuti urutan boot fakta, Bagian 6)

1. Static HTTP 8080: host salinan file game (dari zip terdekripsi / CDN).
2. Config bin: buat `setting_BS_Android.bin` (JSON local di-XOR `DragonBall`) + opsional `com_db_local.bin`.
3. Socket.io v2.1.2 (EIO=3) port 610: handshake + `GetServerList` + verify login.
4. Edit `config_BS.properties` EntryPoint1 (Base64) → rebuild APK (atau spoof hosts).
5. Hapus `Android/data/com.db.local/files/game/` → install → uji boot.
6. `GetServerList` dari local mengembalikan `url`/`chaturl`/`dungeonurl` 127.0.0.1:<port> → uji koneksi game/chat/dungeon.

### 7.4 Checkpoint field-test (BELUM terverifikasi — ditandai jujur, bukan asumsi)

- Format persis payload login verify (`loginKey`/TEA) JS ↔ login server: perlu capture
  login live untuk kontrak 1:1 sebelum ditiru.
- Perilaku HTTPDNS retry saat POST pelaporan ke 127.0.0.1 gagal: resolve "127.0.0.1"
  via HTTPDNS belum diketahui hasilnya (pelaporan bukan gameplay-critical).
- Batasan/handshake lanjutan socket.io v2 (polling→websocket upgrade) perlu dicek
  di server local buatan sendiri.

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
| Dekriptor XXTEA "onepiece" | `/home/z/db-apk-analysis/tools/onepiece_decrypt.py` |
| Scanner URL byte-per-byte | `/home/z/db-apk-analysis/tools/urlscan.py` |
| Hasil byte-scan lengkap | `/tmp/urlscan_apk.txt` (852 kecocokan mentah) |
| Config live terdekripsi (fresh) | `/tmp/fresh_setting.bin` (XOR DragonBall → JSON) |
| HTML live | `/tmp/live_index.html`; JS live: `/tmp/live_main.min.js`, `/tmp/live_manifest.json` |
| Zip game terdekripsi | `/home/z/db-apk-analysis/decrypted/*.zip` |
| Isi zip game | `/home/z/db-apk-analysis/game_extracted/{voyage,pschent}/` |

Repo `db-apk` di `/home/z/db-apk-analysis/db-apk/`: readme.md ini di-push sebagai
**commit tambahan** (append-only) — tidak ada rewrite/amend/force-push pada riwayat lama.

---

## 9. Status Akhir Pekerjaan

- [x] Clone + verifikasi APK (LFS, sha256) — diulang saat audit
- [x] Bedah manifest (androguard, dari bytes AXML) + NSC + permission lengkap (12)
- [x] **Fase 1** — struktur folder `android/data` + nomor baris bukti + aturan escape URL
- [x] **Fase 2** — inventaris endpoint: APK-embedded (Base64/QHinfo/HTTPDNS) + byte-scan
      3.668 file + endpoint runtime (uji live ulang)
- [x] Pemecahan XXTEA "onepieceencrypts" — uji ulang CRC valid
- [x] XOR `DragonBall` — uji ulang live
- [x] Liveness server — uji ulang segar (login:610 hidup; battle-log ternyata DIBLOKIR)
- [x] readme.md di-push ke GitHub (commit `3239d05`, lalu commit revisi)
- [ ] (Tahap berikutnya, belum dieksekusi) penerapan local server 127.0.0.1:<port>

---

## 10. Daftar Koreksi dari Versi Sebelumnya (transparansi — fakta menggantikan asumsi)

| # | Klaim lama | Fakta terverifikasi | Bukti |
|---|---|---|---|
| 1 | classes.dex = "1.879 class" | **6.108 class**, 42.826 method, 48.243 string | header DEX `class_defs_size@0x60` |
| 2 | Protokol update dari "class GameUpdateUtil" | Kelas itu **tidak ada**; protokol ada di boot thread `LaunchActivity` (badcode:730-865) | grep `size.json`/`upgrade.json` hanya di LaunchActivity badcode |
| 3 | Permission hanya 7 | **12 permission** (tambahan: ACCESS_WIFI_STATE, REORDER_TASKS, SYSTEM_OVERLAY_WINDOW, BIND_GET_INSTALL_REFERRER_SERVICE) | androguard atas AXML asli |
| 4 | Contoh folder lokal `http/127.0.0.1:8080` | Titik dua juga di-escape → `http/127.0.0.1#0A8080` | rumus escape (4 lokasi kode, 3.1) |
| 5 | `log.sjflaregame.com:10000` "302 (hidup)" | 302 → `dnspod.qcloud.com/static/webblock.html` = **domain diblokir/parked** | curl -w redirect_url |
| 6 | `login.popoh5.com:443` "timeout" | **connection refused** | curl (7) |
| 7 | OBB "jarang dipakai, tidak jelas" | Nama OBB + marker `.done` **terbukti di kode** (badcode:360-361; worktopichardtails.java) | kutipan kode |
| 8 | (penguatan) socket.io "v2" | **v2.1.2** — literal `version:"2.1.2"` di socket.io.min APK; handshake EIO=3 live | JS APK + curl |
| 9 | (penguatan) ThinkingData appId | `da5e91639fc948399ba6c9523f593944`, serverUrl `https://ssweb.episodezz.com` | string `ThinkingdataAppId=` di main.min live |
| 10 | (penguatan) base.version/resource.version update | juga dibaca dari FILE LOKAL dulu (FileInputStream) sebelum fallback assets | reweavingsiamesedpropertylessnesses.feedtonight |
| 11 | (penguatan) HTTPDNS "untuk WebSocket" | HTTPDNS HANYA jalur retry POST pelaporan data: `VersionController.a(host)` = GET `119.29.29.29/d?dn={domain}` (timeout 5 s); dari `b(url,body,flag)` — flag 0 = POST langsung, flag 1 = resolve→POST ke IP + header `Host:`; jika resolve kosong → POST gagal; pemanggil `VersionController$2.run()` = POST pertama flag 0, retry flag 1. WebSocket game = Java-WebSocket langsung, tanpa HTTPDNS | dekompilasi `org.egret.launcher.versioncontroller1_0.VersionController` (+ inner class $2) dari runtime-dex.jar |
| 12 | (baru) `com_db_local.bin` "retry 3x" | Jika download gagal: tampil dialog `unknown_error` TAPI boot TIDAK berhenti — kode lanjut baca `mSdkParams.webVer` (`Float.parseFloat`) dst. Menjelaskan kenapa live 404 namun game tetap boot | `LaunchActivity` (androguard): cabang `if ((v7_9 == null) \|\| (v6_8 == null))` tanpa `return`, dilanjut `Float.parseFloat(...webVer)` |
| 13 | (baru) downloader config | `LaunchActivity.worktopichardtails(String,int)` = loop retry timeout tumbuh (5×i detik) → GET → XOR `DragonBall` per-byte (`srcData[i] ^ keyData[i % len]`) → parse JSON → return sukses/gagal | dekompilasi `LaunchActivity.worktopichardtails` |

---

## 11. FAKTA BARU SDK t4game (audit kode-level, 22 Nov 2026 — sesi lanjutan)

Pertanyaan owner: "apakah qsdk.t4game.com benar-benar dipakai, dan jalur apa saja?" — dijawab
dengan bedah bytecode `classes.dex` (androguard `AnalyzeDex` + xref string/field/method).

### 11.1 Rantai bukti QHinfo → URL

| Langkah | Fakta | Bukti |
|---|---|---|
| 1 | `assets/QHinfo` = 22 byte `http://qsdk.t4game.com` — **tanpa trailing slash** | `wc -c` + `cat` byte asli |
| 2 | Dibaca di `QuickGameManager.initThirdDataSDK()` → disimpan ke `bindcommunist.cointhreat.seventygenom[0]` | xref string `"QHinfo"` → 1 pemakai saja |
| 3 | Field `seventygenom` hanya punya **1 pembaca** di seluruh DEX: `bindcommunist.cointhreat.cointhreat(String action, Map params)` | xref field read = 1 (write = `<clinit>` saja; isi elemen array ditulis initThirdDataSDK) |
| 4 | URL dibangun: `String.format("%s%s", domain[0], action)` → **domain + path digabung langsung** | dekompilasi `connect()` baris format (tools/qsdk/bindcommunist_cointhreat_connect.java) |
| 5 | Semua request SDK = **POST**, `Content-Type: application/x-www-form-urlencoded`, body JSON params, respons **JSON**, timeout 8000 ms, retry ≤2 putaran array domain | dekompilasi `connect()` |
| 6 | Cabang khusus: bila action mengandung `postGooglePlayVerify` **DAN** domain mengandung `springgame`/`topgame`/`summertimegame` → jalur verifikasi billing terpisah. Domain local `127.0.0.1` **tidak mengandung** string itu → cabang TIDAK aktif (dipakai jalur POST normal) | dekompilasi `connect()` cabang `contains()` |
| 7 | Dispatcher contoh: `QuickGameSdkService.cointhreat(String,Message)` memanggil `connect("/v1/user/autoLogin", …)` | dekompilasi dispatcher (tools/qsdk/QuickGameSdkService_dispatcher.txt) |

**Kesimpulan tegas:** `qsdk.t4game.com` DIPAKAI dan adalah server API utama SDK t4game
(login, register, bind, order/verify, event FB, notice). Rencana lama "TIDAK PERLU DIUBAH"
pada tabel 7.2 #10 adalah KESALAHAN — dikoreksi atas instruksi owner (game native Android;
SDK ini jalur utama di dalam APK). Koreksi ini mencabut baris 7.2 #10 dan menggantinya
menjadi WAJIB diganti.

### 11.2 Daftar lengkap 33 path action API SDK (semua POST; regex string `^/(v\d|api|…)/` di DEX)

| Grup | Path | Jumlah |
|---|---|---|
| `/v1/system` | `init`, `getNotice`, `getAgreement`, `dmsg`, `onNewToken` | 5 |
| `/v1/user` | `autoLogin`, `registerVisitor`, `ckRegVistor`, `registerUser`, `loginByName`, `findPassByEmail`, `sendCodeByEmail`, `actCdKey`, `getServerAgreement`, `postGooglePlayVerify`, `userLoginByOtherSdk` | 11 |
| `/v1/auth` | `bindMail`, `cUserTrash`, `changePassword`, `cksnslogin`, `createOrder`, `fbActInit`, `fbInviteEvent`, `fbLikeEvent`, `fbShareEvent`, `fbUserClaimEvent`, `getUserInfo`, `jianchaLayLx`, `setGameRoleInfo`, `unBindOtherInfo`, `userBindOtherSdk`, `viewMigCode` | 16 |
| `/v2` | `purchase` | 1 |

Artefak dekompilasi disimpan tahan-reset di `tools/qsdk/` (4 file).

---

## 12. KEPUTUSAN FINAL RENCANA LOCAL (owner disepakati — port, root folder, 6 endpoint)

### 12.1 Skop yang disepakati owner

1. **HANYA endpoint DI DALAM APK** (plus field config yang disajikan server local kita).
   URL dinamis game/chat/dungeon dari `GetServerList` (tabel 7.2 #6) **TIDAK termasuk**
   — owner: "yang di luar APK ya biarin aja".
2. `qsdk.t4game.com` **WAJIB diganti** (koreksi owner; lihat 11.1).
3. HTTPDNS `119.29.29.29` **TIDAK diubah** (fakta 7.2 #9 / koreksi #11: hanya retry POST
   pelaporan, bukan gameplay; bukan server game).

### 12.2 Hasil: tepat 6 endpoint yang diganti (konfirmasi hitungan owner)

| # | Endpoint asli (FAKTA) | Sumber di APK/server local | Nilai BARU |
|---|---|---|---|
| 1 | `https://configus.sjmobilegame.com/bs/db/android` | `assets/config_BS.properties` EntryPoint1 (Base64) — config AKTIF; EntryPoint2 backup Tencent COS dalam file asset yang SAMA (1 paket edit, keduanya diarahkan ke local) | `http://127.0.0.1:9527/sdk/bs/db/android` |
| 2 | `https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935` | field `url` di JSON `setting_BS_Android.bin` (disajikan server kita) | `http://127.0.0.1:9527/sdk/bs/index-native.html` |
| 3 | `https://dragonh5cdn.popoh5.com/bs/upgrade` | field `update` di JSON yang sama | `http://127.0.0.1:9527/sdk/bs/upgrade` |
| 4 | `https://login.popoh5.com` + `loginPort` 610 (socket.io v2.1.2 EIO=3) | field `loginServer`+`loginPort` di JSON yang sama | `http://127.0.0.1` + `loginPort` 9527 |
| 5 | fallback `{host game}/bs/resource/properties/serversetting.json` (`login.popoh5.com:610`) | file statis di host game local (fakta 4.F) | isi file: `{"loginserver":"http://127.0.0.1:9527"}` — di-serve di `…/sdk/bs/resource/properties/serversetting.json` |
| 6 | `http://qsdk.t4game.com` (22 byte, tanpa slash) | `assets/QHinfo` | `http://127.0.0.1:9527/sdk` — **tanpa trailing slash** (mirror asli; concat `%s%s` → `/sdk/v1/…`) |

**Tidak diganti (dengan bukti):** config.properties EntryPoint1 `dragon.sjmobilegame.com:82/lzceshi/…`
(channel kosong; channel aktif = "BS"); HTTPDNS (11.2/7.2 #9); `tsUrl`/`tsLoginServer` (mode TS
opsional); `loginWebPort` 510 (native tidak pakai); SDK pihak ketiga (Facebook/Google/Adjust/
AppsFlyer/Line/ThinkingData — bukan gameplay).

### 12.3 PORT: 9527 — satu port untuk SEMUA

| Keputusan | Nilai | Alasan (fakta) |
|---|---|---|
| Port tunggal | **9527** | (a) >1024 → bind oleh proses app biasa tanpa root — mini-server embedded di dalam APK (DIKOREKSI, lihat 13.2: BUKAN Termux); (b) <32768 → di luar range port ephemeral Linux/Android; (c) bukan port umum tool (8080/8000/3000/8888/1080/5555 dihindari); (d) `cleartextTrafficPermitted="true"` → HTTP polos ke 127.0.0.1 LEGAL tanpa ubah manifest (fakta res/xml/network_security_config.xml) |
| Satu layanan | HTTP statis + config bin + API SDK + **socket.io v2.1.2 (EIO=3) login** | socket.io v2 = HTTP server + upgrade; hidup di port yang sama dengan file statis |
| `loginPort` | 9527 | field dibaca dari JSON config yang KITA sajikan — bebas ditentukan |
| `loginServer` | `http://127.0.0.1` | klien io → `http://127.0.0.1:9527` |
| Cadangan (reserved, belum dibuat) | 9528/9529/9530 | bila suatu saat perlu server game/chat/dungeon terpisah untuk isi `GetServerList` (di luar skop saat ini) |

### 12.4 ROOT FOLDER — [DIKOREKSI oleh owner: lihat 13.1 — root = `com.db.local/server/`, SEBELAH `files/`, BUKAN di dalam `files/`]

```
com.db.local/server/                       ← SEBELAH files/ (koreksi 13.1); URL http://127.0.0.1:9527/sdk/
├─ bs/
│  ├─ db/
│  │  └─ android/
│  │     ├─ setting_BS_Android.bin         ← JSON config local di-XOR "DragonBall" (fakta 4.C)
│  │     └─ com_db_local.bin               ← opsional; boot tetap jalan bila 404 (koreksi #12)
│  ├─ index-native.html                    ← salinan dari zip terdekripsi / CDN v202109150935
│  ├─ manifest.json                        ← satu paket dengan html game
│  ├─ js/                                  ← seluruh JS game
│  ├─ resource/
│  │  ├─ …                                 ← seluruh resource game
│  │  └─ properties/
│  │     └─ serversetting.json             ← fallback loginserver (fakta 4.F)
│  └─ upgrade/
│     ├─ base.version                      ← "110"
│     ├─ resource.version                  ← "11220" (offline penuh) / "11390" (resource baru)
│     ├─ size.json
│     ├─ upgrade.json
│     └─ (paket .zip bila ada update)
└─ (API SDK t4game: 33 rute POST di bawah /sdk/v1/…, /sdk/v2/purchase — di-handle kode server,
   bukan file statis; daftar path di 11.2)
```

### 12.5 Konsekuensi wajib setelah penggantian

1. Wipe cache client `Android/data/com.db.local/files/game/` SEKALI setelah ganti URL — folder
   cache mengikuti escape URL; URL baru menghasilkan folder escape baru (`http/127.0.0.1#0A9527/…`)
   (fakta 3.1–3.2). CATATAN KOREKSI 13.1: `files/` adalah folder client; server TIDAK MENULIS di
   sana — wipe cache adalah operasi client sekali jalan (manual di device, atau opsional
   dijalankan otomatis oleh app versi baru saat boot pertama).
2. `QHinfo` baru harus TANPA trailing slash (mirror asli 22 byte) — concat `%s%s` memang
   menyambung langsung; action sudah diawali `/v1/`.
3. Server local harus menjawab handshake socket.io v2 (EIO=3) di port 9527 + 33 route POST
   JSON (format respons 1:1 akan dicapture saat field-test — checkpoint 7.4 tetap berlaku).

---

## 13. KOREKSI OWNER (22 Nov 2026): lokasi root server & arsitektur mini-server

### 13.1 Koreksi 1 — root server DI SEBELAH `files/`, BUKAN di dalam `files/`

Klaim salah (versi 12.4): "ROOT FOLDER: `files/server/`".

FAKTA dari owner: **folder `files/` adalah folder CLIENT yang tidak boleh diotak-atik.**
Server root ada **di sebelah** `files/`. Struktur benar di device:

```
/storage/emulated/0/Android/data/com.db.local/
├─ files/              ← FOLDER CLIENT — DILARANG disentuh server
│  └─ game/            ← preloadPath client: getExternalFilesDir("") + "/game/" (fakta 3.1–3.2)
│     └─ http/127.0.0.1#0A9527/…   ← folder escape URL cache — DIBUAT SENDIRI oleh downloader game,
│                                     bukan oleh server kita
└─ server/             ← ROOT SERVER (sebelah files/) — web root mini-server, URL /sdk/
   ├─ bs/
   │  ├─ db/android/setting_BS_Android.bin   ← JSON config local di-XOR "DragonBall"
   │  ├─ db/android/com_db_local.bin         ← opsional
   │  ├─ index-native.html + manifest.json + js/ + resource/   ← file game
   │  ├─ resource/properties/serversetting.json
   │  └─ upgrade/{base.version, resource.version, size.json, upgrade.json, *.zip}
   └─ (33 rute API SDK /sdk/v1/…, /sdk/v2/purchase — di-handle kode mini-server, bukan file statis)
```

- `files/` berisi cache/preload client yang diisi otomatis oleh game (escape URL) — server
  TIDAK pernah menulis ke situ. Satu-satunya operasi yang menyentuh `files/game/` adalah
  wipe cache SEKALI setelah penggantian URL (operasi client, bukan penempatan file server).
- App boleh membuat/menulis folder sendiri di root data external-nya (`com.db.local/`)
  di luar `files/` — perilaku platform Android untuk app-specific dir; akan dikonfirmasi
  saat field-test (ditandai jujur, bukan asumsi teruji).

### 13.2 Koreksi 2 — TANPA Termux: mini-server EMBEDDED di dalam APK

Klaim salah (versi 12.3): menyebut "Termux/emulator non-root" sebagai cara bind port.

FAKTA dari owner: **TIDAK pakai Termux. Mini-server dibuat di dalam APK** (versi APK hasil
modifikasi membawa server-nya sendiri):

| Komponen mini-server di dalam APK | Detail |
|---|---|
| HTTP server | serve file statis dari web root `com.db.local/server/` (pohon di 13.1) |
| Config | `GET /sdk/bs/db/android/setting_BS_Android.bin` (+ `com_db_local.bin` opsional) — XOR `DragonBall` |
| API SDK t4game | 33 rute POST JSON di `/sdk/v1/…`, `/sdk/v2/purchase` (11.2) |
| Login socket.io | **v2.1.2 (EIO=3)** — handshake + event login + `GetServerList`, port 9527 sama |
| Start | dijalankan di jalur boot app (sebelum downloader config jalan) → bind `127.0.0.1:9527` |
| Isi folder `server/` | diekstrak dari assets APK saat boot pertama (proposal implementasi — assets adalah jalur andal menaruh file ke app-specific dir; alternatif adb push dibatasi Android 11+). Dikonfirmasi saat build. |

Alasan port 9527 TETAP berlaku: >1024 (proses app biasa bisa bind, tanpa root), <32768
(di luar ephemeral), bukan port umum tool.

### 13.3 Status arsitektur setelah koreksi

```
APK modifikasi com.db.local
├─ mini-server embedded (HTTP + socket.io v2.1.2 EIO=3) → 127.0.0.1:9527
│    web root: Android/data/com.db.local/server/  (SEBELAH files/)
├─ assets diubah: config_BS.properties EntryPoint1+2 (Base64) → 127.0.0.1:9527/sdk/...,
│                 QHinfo → http://127.0.0.1:9527/sdk (tanpa slash)
└─ client game (files/) — TIDAK diubah, TIDAK ditulis server
```

Rencana lama 12.4 (`files/server/`) dan penyebutan Termux (12.3) DIBATALKAN oleh koreksi ini.

---

## 14. PETA 1:1 ENDPOINT & PORT — VERIFIKASI ULANG TOTAL (22 Nov 2026, sesi pasca-reset)

Semua fakta DIVERIFIKASI ULANG dari nol di workspace baru (sandbox lama hilang; APK
diunduh ulang dari LFS repo, identitas dicek). Artefak verifikasi: `tools/verify/`.

### 14.A Log verifikasi ulang total (perintah fresh, hasil nyata)

| # | Item | Hasil verifikasi ulang |
|---|---|---|
| 1 | APK sha256 | `b308c44de6ec082a70c39e388a2cef4e9cdc1cee30b38c14a30bef86a7bc2218`, 104.945.181 B — **identik** dengan LFS pointer |
| 2 | `assets/QHinfo` | 22 byte `http://qsdk.t4game.com` — **tanpa trailing slash** |
| 3 | `assets/config.properties` | EntryPoint1 + EntryPoint1_debug (Base64) → `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` (channel non-aktif) |
| 4 | `assets/config_BS.properties` | **4 kunci**: EntryPoint1, EntryPoint1_debug, EntryPoint2, EntryPoint2_debug (Base64) → `https://configus.sjmobilegame.com/bs/db/android` + `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` |
| 5 | Config live fresh | HTTP 200, 711 B, XOR `DragonBall` → JSON persis (14.B) |
| 6 | `com_db_local.bin` live | HTTP 404 (boot tetap lanjut — koreksi #12) |
| 7 | Handshake login:610 | `96:0{"sid":…,"upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}2:40` — EIO=3 HIDUP |
| 8 | POST `qsdk.t4game.com/v1/system/init` (body `{}`, CT urlencoded) | **HTTP 200** → `{"result":false,"data":[],"error":{"id":20000,"message":"Params error"}}` — ENVELOPE respons SDK tertangkap 1:1 (artefak `tools/verify/`) |
| 9 | Xref string `QHinfo` | 1 pemakai: `QuickGameManager.initThirdDataSDK` |
| 10 | Xref field `seventygenom` (domain) | 1 pembaca: `connect(String,Map)` |
| 11 | Path API `^/(v\d)/` | **33 path — daftar identik** dengan 11.2 |
| 12 | `connect()` | `String.format("%s%s", …)` + `setRequestMethod("POST")` + `application/x-www-form-urlencoded` — dua cabang (timeout 5000/8000) |
| 13 | Template config | string `config_%s.properties` + `getChannelId` (QuickGameManager) — file dibaca = config_ + channel |
| 14 | NSC | `<base-config cleartextTrafficPermitted="true"/>` — HTTP ke 127.0.0.1 legal |
| 15 | Literal `#0A` (escape cache) | ada; dipakai di ≥6 titik, termasuk `singersmooth(String)String` & `seventygenom(String)String` (rumus 3.1 terkonfirmasi) |
| 16 | `loginServer`/`loginPort` | DIBACA di Java: `LaunchActivity.worktopichardtails(String,int)Z` + `hardlinerspare(...)` |
| 17 | `loginserver`/`serversetting.json` | **TIDAK ada di Java** → 100% jalur JS (sesuai 4.F) |
| 18 | HTTPDNS di runtime-dex.jar | `http://119.29.29.29/d?dn=` DITEMUKAN ulang (scan bytes jar) |

### 14.B Peta 1:1 — 6 endpoint yang diganti (lama → baru)

| # | Komponen | LAMA (verifikasi 14.A) | BARU | Perubahan port |
|---|---|---|---|---|
| 1a | Config AKTIF — `config_BS.properties` EntryPoint1 (+ EntryPoint1_debug) | `https://configus.sjmobilegame.com:443/bs/db/android` | `http://127.0.0.1:9527/sdk/bs/db/android` | 443 → **9527**, https→http |
| 1b | Config BACKUP — EntryPoint2 (+ EntryPoint2_debug), file asset SAMA | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com:443/bs/db/android` | `http://127.0.0.1:9527/sdk/bs/db/android` (sama dgn 1a) | 443 → **9527** |
| 2 | Game URL — field `url` di JSON config | `https://dragonh5cdn.popoh5.com:443/bs/index-native.html?v=202109150935` | `http://127.0.0.1:9527/sdk/bs/index-native.html?v=202109150935` (query dipertahankan; server abaikan query saat match file) | 443 → **9527** |
| 3 | Update server — field `update` | `https://dragonh5cdn.popoh5.com:443/bs/upgrade` | `http://127.0.0.1:9527/sdk/bs/upgrade` | 443 → **9527** |
| 4 | Login socket.io — field `loginServer` + `loginPort` (dibaca Java, 14.A#16) | `https://login.popoh5.com:443` + port 610 → efektif `https://login.popoh5.com:610` | `http://127.0.0.1` + `loginPort` 9527 → efektif `http://127.0.0.1:9527` | 610 → **9527** |
| 4b | `loginWebPort` = 510 | efektif `https://login.popoh5.com:510` | diubah menjadi 9527 demi konsistensi (native TIDAK memakainya — fakta 7.2 #5; bisa juga dibiarkan) | 510 → **9527** |
| 5 | Fallback serversetting.json — dipanggil JS dari HOST GAME (4.F) | URL `https://dragonh5cdn.popoh5.com:443/bs/resource/properties/serversetting.json?v=<random>`; isi `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}` | URL `http://127.0.0.1:9527/sdk/bs/resource/properties/serversetting.json`; isi `{"loginserver":"http://127.0.0.1:9527","requireSDK":false}` | 443 → **9527** |
| 6 | SDK t4game — `assets/QHinfo` (22 B, tanpa slash) + 33 path (11.2) | `http://qsdk.t4game.com:80` → concat: `…/v1/system/init` dst. | `http://127.0.0.1:9527/sdk` (tanpa slash) → concat: `/sdk/v1/system/init` dst. | 80 → **9527** |

### 14.C Tabel port 1:1

| Layanan | Port lama | Port baru | Status |
|---|---|---|---|
| Config server (https) | 443 | **9527** (http) | DIGANTI |
| CDN game + update (https) | 443 | **9527** (http) | DIGANTI |
| Login socket.io v2.1.2 | 610 | **9527** | DIGANTI |
| loginWebPort (tidak dipakai native) | 510 | **9527** (konsistensi) | DIGANTI (opsional) |
| API SDK t4game (http) | 80 | **9527** | DIGANTI |
| HTTPDNS Tencent | 80 | 80 | TIDAK diubah (hanya retry POST pelaporan — fakta 7.2 #9) |
| `dragon.sjmobilegame.com:82` (config channel non-aktif; tsUrl mode TS) | 82 | 82 | TIDAK diubah |
| `tsLoginServer` mode TS | 7003 | 7003 | TIDAK diubah (biarkan nilai asli) |
| game/chat/dungeon dinamis (GetServerList) | dinamis | dinamis | DI LUAR SKOP (keputusan owner) |

### 14.D Field JSON config — 1:1 field per field

| Field | Nilai asli (fresh 14.A#5) | Nilai di `server/bs/db/android/setting_BS_Android.bin` (XOR DragonBall) | Status |
|---|---|---|---|
| `testMode` | 1 | 1 (biarkan) | sama |
| `url` | `https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935` | `http://127.0.0.1:9527/sdk/bs/index-native.html?v=202109150935` | DIGANTI |
| `tsUrl` | `http://dragon.sjmobilegame.com:82/bs103/index-native.html` | biarkan asli (mode TS tidak aktif) | sama |
| `update` | `https://dragonh5cdn.popoh5.com/bs/upgrade` | `http://127.0.0.1:9527/sdk/bs/upgrade` | DIGANTI |
| `checkTime` | 300 | 300 | sama |
| `loginServer` | `https://login.popoh5.com` | `http://127.0.0.1` | DIGANTI |
| `tsLoginServer` | `http://118.24.68.91:7003` | biarkan asli | sama |
| `loginPort` | 610 | 9527 | DIGANTI |
| `loginWebPort` | 510 | 9527 | DIGANTI (opsional) |
| `clientParams` | hideList/supportLang/showCurChannel | salin 1:1 | sama |

### 14.E Folder cache client (rumus escape 3.1 — terkonfirmasi ulang 14.A#15)

Rumus: `substring(0, lastIndexOf('/')+1)` → `"://"`→`"/"` → `":"`→`"#0A"`.

| URL baru | Folder cache di `files/game/` (dibuat SENDIRI oleh client — server tidak menulis di files/) |
|---|---|
| #2 game + #3 update | `http/127.0.0.1#0A9527/sdk/bs/` — SATU folder untuk keduanya (substring sampai `/` terakhir; live lama: `https/dragonh5cdn.popoh5.com/bs/`) |
| Cache lama yang di-wipe sekali | `files/game/https/dragonh5cdn.popoh5.com/bs/` (plus semua isi `files/game/`) |

#1 config bin, #5 serversetting.json, #6 API SDK = request HTTP biasa (bukan path downloader
Egret) — tidak menghasilkan folder escape; tidak ada bukti mereka di-cache via rumus 3.1.

### 14.F Ringkasan satu kalimat

Enam penggantian: config (443→9527, dua EntryPoint×2 varian dalam 1 file asset), CDN game
(443→9527), update (443→9527), login socket.io (610→9527 + loginWebPort 510→9527 opsional),
serversetting.json (443→9527, isi loginserver ikut diubah), QHinfo SDK (80→9527, 33 path
POST `/sdk/v1/…` `/sdk/v2/purchase`); port tunggal 9527; HTTPDNS + TS + channel non-aktif +
SDK pihak ketiga tidak disentuh; root server `com.db.local/server/` SEBELAH `files/`;
mini-server embedded di APK.

---

## 15. KEPUTUSAN FINAL PORT v2 — OPSI B MULTI-PORT (MIRROR DOMAIN ASLI) + LOGGER 2 MODE (22 Nov 2026)

Owner memutuskan lewat diskusi chat (22 Nov 2026): keputusan port "satu port 9527" (12.3)
DIGANTI menjadi **Opsi B — tiap domain asli → port lokal sendiri**. Root folder
`com.db.local/server/` (13.1) dan pohon folder 12.4/13.1 TIDAK berubah. Peta 12.2 dan
14.B–14.D digantikan oleh peta di bawah ini (15.2–15.3) — versi ini yang berlaku.

### 15.1 Alasan owner (kutipan keputusan)

1. "path jadi identik dengan asli, log per layanan terpisah, dan arsitekturnya meniru asli".
2. Logger/monitoring 2 mode: "mode semua agar kita juga bisa lihat alur, mode port untuk
   rincian mendalam kalau ada config error di dalam port itu sendiri".
3. Cadangan masa depan game/chat/dungeon: "port nya kita bikin beda misal: 5050, 5051, 5052"
   (menggantikan usulan awal 9531/9532/9533).

### 15.2 Peta port FINAL (Opsi B)

| Layanan (mirror domain asli) | Port FINAL | Port asli | Status |
|---|---|---|---|
| Config bin — mirror `configus.sjmobilegame.com:443` | **9527** | 443 → 9527 | listener mini-server |
| CDN game + upgrade + serversetting.json — mirror `dragonh5cdn.popoh5.com:443` | **9528** | 443 → 9528 | listener mini-server |
| Login socket.io v2.1.2 EIO=3 — mirror `login.popoh5.com:610` | **9529** | 610 → 9529 | listener mini-server |
| API SDK 33 path — mirror `qsdk.t4game.com:80` | **9530** | 80 → 9530 | listener mini-server |
| Logger/monitoring — **KOMPONEN BARU** (bukan dari APK; string logger/monitor/4000 tidak ada di artefak analisis) | **4000** (fallback 4001 bila bind gagal) | — | listener mini-server |
| Cadangan masa depan: game | **5050** | — | reserved (belum dibuat) |
| Cadangan masa depan: chat | **5051** | — | reserved |
| Cadangan masa depan: dungeon | **5052** | — | reserved |
| HTTPDNS `119.29.29.29:80`; TS `:82`/`:7003`; `config.properties` channel non-aktif | tak berubah | tak berubah | TIDAK diubah (12.1) |

Cek aturan port: semua port FINAL >1024 (bind oleh proses app tanpa root), <32768 (di luar
ephemeral Linux/Android), bukan port tool umum; cleartext HTTP ke 127.0.0.1 legal via NSC
(fakta 14.A#14).

### 15.3 Peta endpoint 1:1 FINAL (Opsi B — path identik asli, TANPA prefix /sdk)

| # | Komponen | LAMA | BARU (FINAL) |
|---|---|---|---|
| 1a | `config_BS.properties` EntryPoint1 (+debug) | `https://configus.sjmobilegame.com:443/bs/db/android` | `http://127.0.0.1:9527/bs/db/android` |
| 1b | EntryPoint2 (+debug) — file asset sama | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com:443/bs/db/android` | `http://127.0.0.1:9527/bs/db/android` |
| 2 | field `url` | `https://dragonh5cdn.popoh5.com:443/bs/index-native.html?v=202109150935` | `http://127.0.0.1:9528/bs/index-native.html?v=202109150935` |
| 3 | field `update` | `https://dragonh5cdn.popoh5.com:443/bs/upgrade` | `http://127.0.0.1:9528/bs/upgrade` |
| 4 | `loginServer` + `loginPort` | `https://login.popoh5.com` + 610 | `http://127.0.0.1` + 9529 → efektif `http://127.0.0.1:9529` |
| 4b | `loginWebPort` | 510 | 9529 (opsional, konsistensi) |
| 5 | `serversetting.json` | URL `https://dragonh5cdn.popoh5.com:443/bs/resource/properties/serversetting.json?v=<random>`; isi `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}` | URL `http://127.0.0.1:9528/bs/resource/properties/serversetting.json`; isi `{"loginserver":"http://127.0.0.1:9529","requireSDK":false}` |
| 6 | `assets/QHinfo` (22 B, tanpa slash) | `http://qsdk.t4game.com` | `http://127.0.0.1:9530` (tanpa slash) |

Path server 1:1 asli (fakta fetch 4.2): `{EntryPoint}/setting_BS_Android.bin?rnd=…` (retry 10)
dan `{EntryPoint}/com_db_local.bin?rnd=…` (retry 3) → file di `server/bs/db/android/` TANPA
perubahan path. API SDK: `http://127.0.0.1:9530/v1/…` + `/v2/purchase` — path identik asli
(concat `%s%s`, fakta 14.A#12). Field JSON yang tidak diganti tetap sesuai 14.D (`testMode`,
`tsUrl`, `checkTime`, `tsLoginServer`, `clientParams`). Envelope SDK tetap 14.A#8; handshake
socket.io tetap 14.A#7.

### 15.4 Folder cache client versi Opsi B (rumus escape 3.1 tetap berlaku)

| URL baru | Folder cache di `files/game/` (dibuat sendiri oleh client) |
|---|---|
| game (9528) + update (9528) | `http/127.0.0.1#0A9528/bs/` — SATU folder untuk keduanya |

### 15.5 Arsitektur mini-server FINAL (Opsi B)

Satu proses embedded di APK, 5 listener, semua bind `127.0.0.1`, plus event bus internal
(logger menerima laporan request dari listener via bus in-memory — tanpa HTTP hop antar layanan):

| Listener | Tugas |
|---|---|
| 9527 | config bin (XOR `DragonBall`) — path asli 1:1 |
| 9528 | HTTP statis game + upgrade + serversetting.json — path asli 1:1 |
| 9529 | socket.io v2.1.2 EIO=3 (handshake pingInterval 25000 / pingTimeout 5000, fakta 14.A#7) |
| 9530 | 33 rute POST API SDK — path asli 1:1 |
| 4000 | logger/monitoring 2 mode (15.6) |

### 15.6 Logger/monitoring 2 MODE (usulan owner dikonfirmasi)

| Mode | Tujuan | Endpoint |
|---|---|---|
| ALL (alur) | kronologis menyeluruh lintas 4 port layanan — melihat urutan boot: config → download → login → SDK | `GET /logs?mode=all&limit=N` |
| PORT (rincian) | detail mendalam satu port: header, body req/resp (preview), status, durasi, error — diagnosis config error di port itu sendiri | `GET /logs?mode=port&port=9527|9528|9529|9530&limit=N` |
| Dashboard | status listener + switch mode | `GET /` |
| Stats | counter per layanan | `GET /stats` |
| Health | status bind semua port | `GET /health` |

Detail teknis: ring buffer in-memory per port + index gabungan; preview body dibatasi (2 KB)
agar file statis besar tidak memakan memori; khusus port 9527 log menyertakan JSON config
TER-DECODE (pasca-XOR `DragonBall`) agar config error langsung terlihat; bind `127.0.0.1`.
Logger = komponen BARU milik APK modifikasi — bukan fakta APK asli (jujur ditandai).

### 15.7 Status implementasi (belum mulai — urutan berikutnya)

1. Edit 2 asset APK: `config_BS.properties` (4 kunci Base64 → 9527) + `QHinfo` (→ `http://127.0.0.1:9530`).
2. Generate `server/bs/db/android/setting_BS_Android.bin` (JSON versi Opsi B, XOR `DragonBall`)
   + opsional `com_db_local.bin`.
3. Isi `server/bs/` (index-native.html + manifest.json + js/ + resource/ + upgrade/) dari zip
   terdekripsi / CDN.
4. Tulis mini-server (5 listener + logger 2 mode) dan tautkan ke jalur boot app SEBELUM
   downloader config jalan.
5. Field-test: capture handshake 9529, envelope SDK 9530, config bin 9527 → kalibrasi 1:1
   (checkpoint jujur; acuan awal = 14.A#7–8).

CATATAN push: commit Bagian 15 dibuat lokal; push GitHub menunggu PAT tersedia kembali
(sandbox reset menghapus kredensial; tidak ada token tersimpan di workspace).

---

## 16. PETA FINAL TERKONFIRMASI OWNER + HASIL REBUILD APK (22 Nov 2026)

Owner konfirmasi "cocok" atas peta di bawah ini (22 Nov 2026). Revisi tunggal dari Bagian 15:
**port login socket.io 9529 → 5050** — alasan owner: `login.popoh5.com:610` adalah bagian dari
**CONFIG UTAMA GAMEPLAY** (sumber `GetServerList`), jadi masuk keluarga port 5050. Sub-bagian
15.2/15.3 digantikan oleh 16.2/16.3. Struktur folder 13.1 TIDAK berubah. Peta 12.2 dan 14.B–14.D
digantikan oleh peta ini.

### 16.1 Kutipan keputusan owner (22 Nov 2026)

1. "login.popoh5.com:610 ini karena sudah masuk config utama gameplay port jadi 5050".
2. "selebihnya bagus" (peta Opsi B multi-port disetujui).
3. "cocok" atas peta final lengkap; perintah: "rebuild apk lalu push juga ke github".

### 16.2 Peta port FINAL (terkonfirmasi)

| Layanan (mirror domain asli) | Port FINAL | Port asli | Status |
|---|---|---|---|
| Config bin — mirror `configus.sjmobilegame.com:443` | **9527** | 443 → 9527 | listener mini-server |
| CDN game + upgrade + serversetting.json — mirror `dragonh5cdn.popoh5.com:443` | **9528** | 443 → 9528 | listener mini-server |
| Login socket.io v2.1.2 EIO=3 — mirror `login.popoh5.com:610` — CONFIG UTAMA GAMEPLAY | **5050** | 610 → 5050 | listener mini-server |
| API SDK 33 path — mirror `qsdk.t4game.com:80` | **9530** | 80 → 9530 | listener mini-server |
| Logger/monitoring — **KOMPONEN BARU** (bukan dari APK) | **4000** (fallback 4001) | — | listener mini-server |
| Cadangan masa depan: chat | **5051** | — | reserved (belum dibuat) |
| Cadangan masa depan: dungeon | **5052** | — | reserved |
| HTTPDNS `119.29.29.29:80`; TS `:82`/`:7003`; channel non-aktif; SDK pihak ketiga | tak berubah | tak berubah | TIDAK diubah |
| game/chat/dungeon dinamis (`GetServerList`) | dinamis — berasal dari login 5050 | dinamis | di luar skop saat ini |

Semua port lolos aturan: >1024 (bind tanpa root), <32768 (di luar ephemeral), bukan port tool
umum (8080/8000/3000/8888/1080/5555), cleartext HTTP ke 127.0.0.1 legal via NSC (14.A#14).

### 16.3 Peta endpoint FINAL (path identik asli, TANPA prefix /sdk)

| # | Komponen | LAMA | BARU (FINAL) |
|---|---|---|---|
| 1a | `config_BS.properties` EntryPoint1 (+debug) | `https://configus.sjmobilegame.com:443/bs/db/android` | `http://127.0.0.1:9527/bs/db/android` |
| 1b | EntryPoint2 (+debug) — file asset sama | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com:443/bs/db/android` | `http://127.0.0.1:9527/bs/db/android` |
| 2 | field `url` | `https://dragonh5cdn.popoh5.com:443/bs/index-native.html?v=202109150935` | `http://127.0.0.1:9528/bs/index-native.html?v=202109150935` |
| 3 | field `update` | `https://dragonh5cdn.popoh5.com:443/bs/upgrade` | `http://127.0.0.1:9528/bs/upgrade` |
| 4 | `loginServer` + `loginPort` | `https://login.popoh5.com` + 610 → efektif `https://login.popoh5.com:610` | `http://127.0.0.1` + **5050** → efektif `http://127.0.0.1:5050` |
| 4b | `loginWebPort` | 510 | **5050** (opsional, konsistensi; native tidak memakainya) |
| 5 | `serversetting.json` | URL `https://dragonh5cdn.popoh5.com:443/bs/resource/properties/serversetting.json?v=<random>`; isi `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}` | URL `http://127.0.0.1:9528/bs/resource/properties/serversetting.json`; isi `{"loginserver":"http://127.0.0.1:5050","requireSDK":false}` |
| 6 | `assets/QHinfo` | `http://qsdk.t4game.com` (22 B, tanpa slash) | `http://127.0.0.1:9530` (21 B, tanpa slash) |

Fetch config tetap 1:1 asli: `{EntryPoint}/setting_BS_Android.bin?rnd=…` (retry 10) +
`{EntryPoint}/com_db_local.bin?rnd=…` (retry 3). Field JSON lain TIDAK berubah (1:1 asli):
`testMode`=1, `tsUrl` asli, `checkTime`=300, `tsLoginServer` asli, `clientParams` asli.
Envelope SDK tetap 14.A#8; handshake tetap 14.A#7 (EIO=3, pingInterval 25000, pingTimeout 5000).

### 16.4 Folder cache client (rumus escape 3.1)

| URL baru | Folder cache di `files/game/` (dibuat sendiri oleh client) |
|---|---|
| game (9528) + update (9528) | `http/127.0.0.1#0A9528/bs/` — SATU folder untuk keduanya |

### 16.5 Arsitektur mini-server FINAL

Satu proses embedded di APK, 5 listener, semua bind `127.0.0.1`, event bus internal ke logger;
start di jalur boot app SEBELUM downloader config jalan:

| Listener | Tugas |
|---|---|
| 9527 | config bin (XOR `DragonBall`) — path asli 1:1 |
| 9528 | HTTP statis game + upgrade + serversetting.json — path asli 1:1 |
| 5050 | login socket.io v2.1.2 EIO=3 — config utama gameplay |
| 9530 | 33 rute POST API SDK — path asli 1:1 |
| 4000 | logger/monitoring 2 mode (16.6) |

### 16.6 Logger/monitoring 2 MODE (usulan owner dikonfirmasi)

| Mode | Tujuan | Endpoint |
|---|---|---|
| ALL (alur) | kronologis menyeluruh lintas 4 port layanan — urutan boot: config → download → login → SDK | `GET /logs?mode=all&limit=N` |
| PORT (rincian) | detail mendalam satu port: header, body req/resp (preview), status, durasi, error — diagnosis config error di port itu sendiri | `GET /logs?mode=port&port=9527|9528|5050|9530&limit=N` |
| Dashboard / Stats / Health | status listener + switch mode / counter per layanan / status bind | `GET /`, `GET /stats`, `GET /health` |

Ring buffer in-memory per port + index gabungan; preview body dibatasi 2 KB; khusus port 9527
log menyertakan JSON config TER-DECODE (pasca-XOR `DragonBall`). Logger = komponen BARU
milik APK modifikasi — bukan fakta APK asli.

### 16.7 HASIL REBUILD APK (dilakukan 22 Nov 2026 — perubahan asset endpoint saja)

| Item | Nilai |
|---|---|
| Sumber | `db-local(Original-DO NOT DELETE!).apk` — sha256 `b308c44d…2218` (LFS pull ulang, identik 14.A#1) |
| Perubahan | TEPAT 2 entry, selebihnya byte utuh: (a) `assets/config_BS.properties` — 4 kunci Base64 EntryPoint1/1_debug/2/2_debug semuanya → `http://127.0.0.1:9527/bs/db/android` (396 B → 256 B, tanpa trailing newline, format persis asli); (b) `assets/QHinfo` — `http://qsdk.t4game.com` (22 B) → `http://127.0.0.1:9530` (21 B, tanpa slash, tanpa newline) |
| Metode | zip-swap 2 entry → zipalign (bawaan uber-apk-signer 1.3.0) → re-sign v1+v2+v3 |
| Keystore | `dblocal-mod.keystore` (alias `dblocalmod`, CN=DB Local Mod,O=Owner,C=ID, RSA 2048, valid s.d. 2056, storepass/keypass `dblocalmod123`) — ikut di-commit di `release-mod/` agar APK update berikutnya WAJIB di-sign pakai keystore yang sama (kalau hilang: uninstall dulu di device sebelum pasang APK sign baru) |
| Hasil | `release-mod/db-local(MOD-LOCALSERVER).apk` — 105.035.407 byte, sha256 `79d78e7d7db70abb75cd7c2f42818de453cc491b3ae4eea7e0c436cceaa03d65` |
| Verifikasi | zipalign verified; signature verified [v1,v2,v3]; 2 asset di-extract ulang dari APK final → `cmp` byte-identik dengan sumber modifikasi; jumlah file 3.668 (= asli); META-INF baru `DBLOCALM.SF`/`DBLOCALM.RSA`/`MANIFEST.MF` |
| TIDAK ada di rebuild ini | mini-server embedded (16.9 #4 — langkah terpisah berikutnya); isi folder `server/`; file `setting_BS_Android.bin` (disajikan server, bukan di APK — fakta 4.C) |
| Cara instal | sideload; jika APK asli sudah terpasang → UNINSTALL dulu (sertifikat beda), lalu wipe `Android/data/com.db.local/files/game/` sekali setelah pertama jalan |

### 16.8 Status push (22 Nov 2026)

- Commit `87649bc` (Bagian 14 RE-CREATE) + `f73d375` (Bagian 15 RE-CREATE) dibuat ulang dari
  backup karena dua commit asli (`6b4944a`, `c77c1d6`) hilang saat reset sandbox dan TIDAK
  pernah sampai GitHub (remote sempat masih di `c03a18f` = Bagian 13; readme backup terbukti
  prefix persis sehingga re-create aman).
- Bagian 16 + APK rebuild (LFS `release-mod/`) di-push bersamaan dalam satu rangkaian push.
- PAT owner dipakai sementara via credential store lokal — TIDAK ditulis di readme.

### 16.9 Urutan implementasi setelah push ini

1. [SELESAI — 16.7] Edit 2 asset APK + rebuild + sign.
2. Generate `server/bs/db/android/setting_BS_Android.bin` (JSON versi final 16.3, XOR
   `DragonBall`) + opsional `com_db_local.bin`.
3. Isi `server/bs/` (index-native.html + manifest.json + js/ + resource/ + upgrade/) dari zip
   terdekripsi / CDN.
4. Tulis mini-server (5 listener + logger 2 mode) dan tautkan ke jalur boot app SEBELUM
   downloader config jalan.
5. Field-test: capture handshake 5050, envelope SDK 9530, config bin 9527 → kalibrasi 1:1
   (checkpoint jujur; acuan awal = 14.A#7–8).

---

## 17. REBUILD LENGKAP v2 — 16.9 #2/#3/#4 SELESAI (22 Nov 2026)

Bagian ini menjawab protes owner ("rebuild setengah-setengah"): hasil 16.7 hanya menyelesaikan
16.9 #1 (2 asset). Bagian 17 menyelesaikan #2 (bin config), #3 (isi `server/bs/`), #4 (mini-server
embedded + patch boot) — SEMUA ke dalam satu APK baru `db-local(MOD-LOCALSERVER-v2).apk`.
Semua angka di bawah hasil uji nyata di sandbox (bukan rencana).

### 17.1 Port literal XXTEA "onepiece" dibaca ULANG dari smali (koreksi spesifikasi 5.1)

Spesifikasi 5.1 tidak menyebut satu perbedaan kunci yang hanya terlihat dari smali class
`cointhreat/cointhreat/cointhreat/reweavingsiamesedpropertylessnesses/customreading`
(= `customreading.nationalcommunitymissing` di jadx; port lama hilang saat reset sandbox):

| Aspek | Spesifikasi 5.1 (lengkapnya di sini) |
|---|---|
| `sum` | **KONSTAN = delta 0x9E3779B9** (`mul-long 1 * 0x9e3779b9`) — BUKAN `rounds*delta` yang turun per round. "1 pass" artinya SATU pass p = n-1 → 0 |
| `e` | **KONSTAN = (delta >> 2) & 3** (dihitung sekali sebelum loop) |
| `y` iterasi | y awal = v[0]; setiap selesai update v[p], y BERIKUTNYA = v[p] yang baru di-update (= v[p'+1]) |
| p = 0 | y = v[1] (sudah ter-update), z = v[n-1] (sudah ter-update) |
| aritmetika | long 64-bit dengan mask32 di tiap komponen MX — hasil modulo 2^32 identik int 32-bit |
| MX | `mask32(y^sum) + mask32(z^key[(p&3)^e])) ^ (mask32((z>>>5)^(y<<2)) + mask32((z<<4)^(y>>>3))` |
| pad | byte terakhir = jumlah pad, dibuang TANPA clamp (uji nyata: pad all.zip = 4) |
| infoinfo.bin | [16:32] ≠ magic → dikembalikan apa adanya (isi "voyage_fancy_radius\|pschent_full_tired" 38 B plaintext) |

Uji ulang dengan port baru — SEMUA CRC VALID dan ukuran PERSIS sama dengan audit:
`base.zip` 1.233.549 B (161 entri), `all.zip` 9.428.692 B (741 entri), `vvcc.json` 183.712 B
(1.944 entri peta), `vvcc_tc.json` 10.907 B. Port baru: `rebuild-src/scripts/onepiece_decrypt.py`.

### 17.2 CDN ASLI MASIH HIDUP — isi server/bs/ = salinan LIVE 1:1 (bukan tebakan)

Uji live 22 Nov 2026: `dragonh5cdn.popoh5.com` masih 200 OK untuk SEMUA file. Unduhan nyata:

| File CDN | Ukuran | Keterangan |
|---|---|---|
| `/bs/index-native.html` | 10.792 B | identik angka 4.D#1; referensi eksternal hanya privacy parajoymobile + battle-log (di luar skop) |
| `/bs/manifest.json` | 533 B | 15 js (`initial` 7 + `game` 8) |
| `/bs/upgrade/base.version` | `110\n` | = assets APK `base.version` (110) |
| `/bs/upgrade/resource.version` | `11390\n` | = assets APK `resource.version` (11390) |
| `/bs/upgrade/size.json` | 105 B | `{"base":"31015076",…,"all":19385098}` — persis 4.E |
| `/bs/upgrade/upgrade.json` | 130 B | persis 4.E |
| `/bs/upgrade/base.zip` | 31.015.076 B | = angka size.json; 2.616 entri, CRC valid |
| `/bs/upgrade/all.zip` | 19.385.098 B | = angka size.json; 652 entri, CRC valid |
| `/bs/resource/properties/serversetting.json` | 73 B | asli `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}` |

FAKTA PENTING (jujur): js di zip APK LEBIH LAMA dari CDN — APK memuat `main.min_7eae4d6e.js`
(fakta 4.G), CDN `manifest.json` sekarang mereferensikan `main.min_777039fc.js`. Karena itu
isi `server/bs/js/**` + `resource/**` diambil dari UNION ekstrak CDN `base.zip` + `all.zip`
(versi v110/11390 yang masih dilayani asli) — BUKAN dari zip dalam APK. Verifikasi: 15 js
manifest SEMUA ada di pohon; `index-native.html` byte-identik disajikan kembali oleh mini-server.

### 17.3 Pohon `assets/server_root/**` dalam APK v2 (= isi server/ setelah ekstraksi)

```
server/                          ← Android/data/com.db.local/server/ (SEBELAH files/, 13.1)
└─ bs/
   ├─ db/android/setting_BS_Android.bin   687 B  ← JSON final 16.3, XOR "DragonBall", ROUNDTRIP valid
   ├─ db/android/com_db_local.bin         2 B    ← "{}" XOR (live asli = 404; {} = tanpa override, boot lanjut)
   ├─ index-native.html                   10.792 B  ← salinan byte-identik CDN
   ├─ manifest.json                       533 B     ← salinan byte-identik CDN
   ├─ js/                                 25 file (11,3 MB) ← union CDN
   ├─ resource/                           3.239 file (118,3 MB) ← union CDN, termasuk
   │    └─ properties/serversetting.json  66 B ← format tab 1:1, loginserver → http://127.0.0.1:5050
   └─ upgrade/                            base.version, resource.version, size.json, upgrade.json,
                                          base.zip (31.015.076 B), all.zip (19.385.098 B) ← byte CDN asli
```

`setting_BS_Android.bin` di-generate dari `setting_BS_Android_live_fresh.bin` (backup) — decode
XOR → TEPAT 5 penggantian kontekstual (`url`, `update`, `loginServer`, `loginPort` 610→5050,
`loginWebPort` 510→5050) → format tab, field lain (testMode=1, tsUrl, checkTime=300,
tsLoginServer, clientParams) byte-identik → encode ulang. 711 B → 687 B.

### 17.4 Mini-server embedded (16.9 #4) — 9 class Java → `classes2.dex` (37.696 B)

Sumber: `rebuild-src/javasrc/com/db/local/server/` (HttpWorker, JsonUtil, LocalServerStarter,
LoggerCore, LoggerHandler, SdkHandler, ServerCore, SocketIoHandler, StaticHandler).
Build: ECJ 3.33.0 (`-source/-target 8`) → D8 8.3.37 (`--min-api 21`, minSdk=21 → ART memuat
classes2.dex native). `java.util.Base64` TIDAK dipakai (Android API 26+) — encoder sendiri.

| Listener (bind 127.0.0.1) | Perilaku teruji (curl/WS di sandbox) |
|---|---|
| 9527 config bin | `/bs/db/android/setting_BS_Android.bin?rnd=…` → 687 B 200; decode XOR valid, `loginPort` 5050; dilayani LANGSUNG dari assets (anti-race ekstraksi); log menyertakan JSON TER-DECODE (16.6); `com_db_local.bin` → `{}` |
| 9528 CDN | `index-native.html` 10.792 B byte-identik sumber; `serversetting.json` isi baru; `upgrade/*` lengkap; anti-race: tunggu latch ekstraksi tahap awal (db→root→js) maks 60 s |
| 5050 login socket.io | **PERILAKU 1:1 CAPTURE LIVE** (server asli login.popoh5.com:610 masih hidup, dicapture 22 Nov 2026 — script `rebuild-src/scripts/capture_login2.py`): poll pertama → body PERSIS 103 byte `96:0{"sid":"…20char","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}2:40` (uji: 103 byte ✓), header `Access-Control-Allow-Origin: *` + `Set-Cookie io=<sid>; Path=/; HttpOnly` ✓; POST → `ok` (text/html, 2 B) ✓; long-poll di-hold ✓; sid bodoh → 400 `{"code":1,"message":"Session ID unknown"}` ✓; `2probe`→`3probe`, `2`→`3` ✓; WebSocket upgrade 101 + Sec-WebSocket-Accept ✓ (uji frame nyata: `2probe`→`3probe`, ping `2`→`3`, close `41` di-log). Event `42[...]` di-log APAPA ADANYA — server asli juga tidak membalas event sintetis tanpa login valid, dan mini-server TIDAK mengarang data game (jujur 16.6) |
| 9530 API SDK | semua path POST/GET → 200 envelope capture live `{"result":false,"data":[],"error":{"id":20000,"message":"Params error"}}` ✓ (kontrak 14.A#8, backup verify-artifacts) |
| 4000 logger (fallback 4001) | `/` dashboard HTML; `/stats` counter per port; `/health` status bind (uji: 5 listener AKTIF); `/logs?mode=all&limit=N` kronologis naik; `/logs?mode=port&port=9527|9528|5050|9530&limit=N` — 9527 menyertakan `extra` = JSON ter-decode (uji: tampil) ✓; preview body ≤ 2 KB; ring buffer global 2.000 + per-port 400 |

Ekstraksi pertama boot: `assets/server_root/**` → `Android/data/com.db.local/server/**`
(2.758 file, ±156 MB — device butuh ruang; cache client `files/game/http/127.0.0.1#0A9528/bs/`
dibuat SENDIRI oleh client, 14.F/16.4 — files/ tidak disentuh server).

### 17.5 Patch boot (16.9 #4b) — smali `Application.onCreate`

`com/zhuhuan/game/Application.smali` (hasil baksmali classes.dex) — sisipan SEBELUM
`invoke-super`:

```smali
invoke-static {p0}, Lcom/db/local/server/LocalServerStarter;->start(Landroid/content/Context;)V
```

classes.dex re-assemble (smali 2.5.2): 6.697.104 → **6.697.176 B** (+72 B instruksi starter).
`start()` menelan SEMUA exception (boot app TIDAK bisa kena crash oleh mini-server).

### 17.6 HASIL APK v2 + verifikasi menyeluruh

| Item | Nilai |
|---|---|
| Sumber | `db-local(Original-DO NOT DELETE!).apk` (sha256 b308c44d…2218) |
| Entri | asli 3.668 − 3 META-INF lama + classes2.dex + 2.758 server_root + 3 META-INF baru = **6.427** |
| Replaced | `classes.dex` (+72 B starter), `assets/config_BS.properties` (396→256 B, identik artefak 16.7 byte), `assets/QHinfo` (22→21 B identik) |
| resources.arsc | tetap STORED (compress=0) — wajib targetSdk 30 |
| Sign | keystore `dblocalmod` SAMA (CN=DB Local Mod, O=Owner, C=ID, exp 2056) — **APK v1 (79d78e7d…) HARUS DI-UNINSTALL dulu bila terpasang** |
| Verifikasi | zipalign verified; signature verified **[v1, v2, v3]**; 2.758 file server_root di-extract dari APK → cmp vs pohon sumber = **0 beda**; bin decode JSON valid; starter call terkonfirmasi di `Application.smali` hasil baksmali ULANG dari dex dalam APK; 9 class server ada di classes2.dex |
| File | `release-mod/db-local(MOD-LOCALSERVER-v2).apk` — **202.638.196 B**, sha256 **bf403c4d9fbfc60b85119212bd1f019b9068954b93684eb5ffa865354fa01bb8** |
| Tool | baksmali/smali 2.5.2 (classpath Maven), D8/r8 8.3.37, ECJ 3.33.0, uber-apk-signer 1.3.0, git-lfs 3.5.1 (semua di-download ulang di `tools/` — jar TIDAK di-commit) |

### 17.7 Batasan jujur ( checkpoint 16.9 #5 sebagian — yang belum bisa diuji di sandbox)

1. Uji di sandbox = JVM desktop (perilaku HTTP/WS/socket.io 1:1 terkonfirmasi). **Uji device
   (instal APK, boot game) belum dilakukan** — perlu device/emulator Android, bukan bagian
   dari lingkungan ini. Checkpoint 16.9 #5 tinggal ini.
2. GetServerList: mini-server hanya HANDSHAKE + LOG. Balasan daftar server (game/chat/dungeon,
   16.2 "di luar skop saat ini") TIDAK dikarang — game akan berhenti di login dengan log lengkap
   di 4000. Kapan pun owner siap, port 5051/5052 (reserved) tinggal ditambah sebagai listener.
3. Semua request SDK 9530 dijawab envelope "Params error" (yang benar-benar tertangkap live).
   Respons path spesifik lain TIDAK dikarang.
4. Battle-log (`log.sjflaregame.com:10000`, 4.D#8) dan privacy page — di luar skop, tidak
   dimirror (battle-log memang diblokir qcloud di live).
5. APK membesar 105 MB → 202 MB karena CDN mirror lengkap (js 11 MB + resource 118 MB
   uncompressed + zips upgrade 50 MB). Alternative penghematan (buang zips upgrade — versi
   lokal=server sehingga tak pernah diunduh) TIDAK diambil demi 1:1 penuh.
6. `index-native.html` asli memuat 2 URL pihak ketiga (privacy parajoymobile, battle-log
   sjflaregame) — tetap apa adanya (mirror 1:1), tidak dimodifikasi.

### 17.8 Status push

- Bagian 17 + `rebuild-src/` (9 java + 4 script) + APK v2 (LFS) di-commit dan di-push bersamaan.
- README append-only: Bagian 1–16 TIDAK diubah sedikit pun.
- git-lfs 3.5.1 dipasang lokal (`~/.local/bin`) — sandbox reset sebelumnya menghapus binary;
  objek LFS lama (APK v1 105 MB) terverifikasi MASIH ADA di GitHub LFS (uji batch API 22 Nov).

---

## 18. LOGGER TOTAL v3 — jawaban "popup error + log port 4000 tidak lengkap" (22 Nov 2026)

Laporan owner setelah uji device APK v2: "Oke work. hanya saja muncul popup error. dan log kita
pada web port 4000 tidak lengkap. mohon totalitas.. karena log itu utama". Bagian ini = audit
jujur AKAR MASALAH log v2 + perbaikan menyeluruh (LOGGER TOTAL) + APK v3. Semua angka hasil uji
nyata sandbox (desktop JVM + browser), bukan rencana.

### 18.1 Akar masalah "log tidak lengkap" di v2 — 6 temuan (diagnosis dari kode v2)

| # | Akar masalah | Efek nyata di device |
|---|---|---|
| 1 | Ring buffer kecil: global 2.000, per-port 400 | Boot pertama game men-download ~3.300 file statis via 9528 → buffer penuh dalam DETIK; entri boot penting (9527 config, 5050 login, 9530 SDK) TERGESER keluar buffer sebelum owner sempat buka web 4000 |
| 2 | TIDAK ada persistensi disk (memori saja) | App mati/restart proses = SEMUA log hilang |
| 3 | Dashboard hanya menampilkan 50 baris terakhir, tanpa body/extra/error, tanpa filter | Yang terlihat di web = piringan kecil dari log yang sudah miskin (1+2) |
| 4 | /logs ikut ter-catat sendiri (self-pollution) | Setiap refresh dashboard memakan slot buffer alur game |
| 5 | Header request TIDAK dicatat (spec 16.6 menjanjikan "header"); preview body 2 KB; limit API dipatok 2000 | Diagnosis kurang data |
| 6 | Body POST `Transfer-Encoding: chunked` DIABAIKAN (v2 hanya Content-Length) | Body event socket.io/POST tertentu bisa hilang dari log + log menunjukkan reqLen 0 padahal client kirim data |

### 18.2 LOGGER TOTAL v3 — 9 komponen (semua di classes2.dex, murni java.* minSdk 21)

1. **LoggerCore v3**: buffer global **15.000** + per-port **6.000**; counter **droppedGlobal /
   droppedPort** (kelengkapan TRANSPARAN — bila ada yang terbuang dari buffer, angkanya kelihatan
   di /stats & dashboard, bukan diam-diam); field baru `kind` (HTTP|WS|BOOT|EXTRACT|SYS) dan
   `headers` (header request kompakt, cap 1,5 KB); preview teks 4.096 B / teks statis 2.048 B;
   **request milik logger sendiri TIDAK disimpan** (hanya counter — anti-pollution #4).
2. **DiskWriter (KOMPONEN BARU — inti "log itu utama")**: SETIAP entri ditulis baris NDJSON ke
   `<serverRoot>/logs/log-<waktu-boot>.ndjson` (thread tunggal + queue 20.000, flush per baris →
   data selamat walau proses mati mendadak; rotasi 16 MB/part; maks 8 file, tertua dihapus; SEMUA
   exception ditelan). Arsip disk TIDAK PERNAH meluap — buffer penuh pun arsip tetap utuh 1:1.
   Diunduh via logger: `/download` (list), `/download?latest=1`, `/download?file=NAME`.
3. **HttpWorker**: dukung body **chunked** (#6 — diuji: POST chunked 28 B ter-parse utuh,
   reqPreview penuh); body > 8 MB → truncate + koneksi ditutup (tertandai); **HEAD tanpa body**;
   request line rusak → entry SYS 400; header request di-capture (fulfil 16.6).
4. **StaticHandler**: file teks (html/js/json/css/xml) preview s.d. 2 KB; file biner (png/zip/mp3/…)
   **kompak `[bin N B]`** + `respCT` di extra — ribuan file resource tidak membanjiri buffer dengan
   hex (metadata ukuran/status/durasi tetap utuh, arsip disk tetap lengkap); khusus 9527 tetap
   menyertakan JSON config TER-DECODE di extra (XOR DragonBall).
5. **SocketIoHandler**: entry WS ber-`kind=WS`; **ping→pong (WS-level) ikut ter-log**; body OPEN
   (103 byte) tercatat di respPreview (v2 hanya di extra); long-poll payload tetap penuh.
6. **SdkHandler**: header request + body s.d. 4 KB — parameter yang game kirim ke /v1/* kelihatan.
7. **ServerCore**: hasil bind tiap port masuk log (kind SYS).
8. **LocalServerStarter**: DiskWriter.init PALING AWAL (semua entry BOOT/EXTRACT/bind tertulis
   dari detik pertama); milestone ekstraksi per folder top-level (db/ → html → js/ → resource/ →
   upgrade/) + total file — boot flow kelihatan kronologis.
9. **LoggerHandler v3 (web port 4000 DITULIS TOTAL)**: dashboard live auto-refresh 1,5 dtk
   (incremental `since` — tidak mengirim ulang yang sudah dilihat); filter Mode ALL/PORT, port
   9527/9528/5050/9530, kind, pencarian subset, hanya-error, limit (s.d. semua buffer); **klik
   baris = detail penuh** (headers/req/resp/extra/error — termasuk JSON config ter-decode); baris
   status berwarna (>=400 merah, WS biru); papan /stats hidup (uptime, seq, counter per port,
   arsip disk + ukuran + baris, dropped merah bila >0); **notifikasi GAP** bila ada entri buffer
   yang tidak terlihat client; tombol Arsip NDJSON (list file → klik unduh) + unduh terbaru;
   responsif (mobile 390 px teruji); seksi "Cara baca (Bagian 18)" menjelaskan alur boot + status
   popup yang diharapkan. API: `/logs?mode=all|port&port=P&limit=N&since=SEQ`, `/stats`,
   `/health`, `/download[?latest=1|file=NAME]` (nama tervalidasi anti path-traversal).

### 18.3 Uji nyata v3 di sandbox (desktop JVM, server_root asli dari v2)

| Uji | Hasil |
|---|---|
| Bind 5 listener + /health | semua AKTIF; disk `enabled:true` sejak sebelum request pertama |
| 9527 bin | 687 B; XOR decode → `loginPort:5050`, `loginServer:http://127.0.0.1` ✓; extra dashboard = JSON penuh |
| 9528 | index-native.html byte-identik; serversetting.json isi baru (loginserver 127.0.0.1:5050) ✓ |
| 9530 | envelope capture live ✓ (header + body request ter-log) |
| 5050 polling | OPEN 103 byte 1:1 ✓; POST chunked `42["login","user","pass123"]` ter-parse utuh (reqLen 28, preview penuh) ✓; framing benar `6:2probe` → long-poll mengembalikan `6:3probe` ✓ |
| 5050 WebSocket | 101 + Sec-WebSocket-Accept ✓; `2probe`→`3probe` ✓; ping `2`→`3` ✓; close ter-log (kind WS) ✓ |
| HEAD statis | header tanpa body ✓ |
| Incremental | `/logs?since=SEQ` hanya entri baru ✓ |
| Anti-pollution | buffer 2.000 entri via dashboard: **0 entri logger-HTTP di buffer** ✓ |
| **BANJIR 16.000 req/3 dtk (5.287 req/s)** | seq 16.026; droppedGlobal **1.026** (terhitung, terlihat); droppedPort 9530 **10.002**; **arsip disk 16.026 baris = seq, dropped 0** — arsip 100% utuh melebihi buffer; GAP flag TRUE di dashboard |
| Dashboard (browser nyata) | tabel live naik, detail baris 9527 menampilkan JSON config ter-decode, filter PORT 9527 hanya entri 9527, arsip list + unduh jalan, mobile 390 px terbaca (screenshot) |
| Integritas arsip | `seq == disk.lines` (26/26 dan 16.026/16.026) — TANPA kehilangan |

### 18.4 HASIL APK v3 + verifikasi menyeluruh

| Item | Nilai |
|---|---|
| Sumber | APK asli (sha256 b308c44d…2218) — BUKAN v2; 3 entri di-replace dari artefak terverifikasi |
| Replaced | `classes.dex` (starter +72 B — **byte-identik v2**, sha256 d506fae9f8377db2 terkonfirmasi ulang via baksmali: `invoke-static {p0}, Lcom/db/local/server/LocalServerStarter;->start(Landroid/content/Context;)V` di Application.smali), `assets/config_BS.properties` (256 B identik), `assets/QHinfo` (21 B identik) |
| Added | `classes2.dex` **57.384 B** (10 class + inner/lambda = 25 smali, DiskWriter ikut — baksmali verifikasi), `assets/server_root/**` 2.758 file (byte-identik v2 — 12 sampel acak cmp 0 beda) |
| resources.arsc | tetap STORED (compress=0) — wajib targetSdk 30 |
| Entri | 6.427 (= v2) |
| Sign | keystore SAMA `dblocalmod` (CN=DB Local Mod, O=Owner, C=ID, exp 2056) — **v2 HARUS di-uninstall dulu bila terpasang** (sertifikat sama sebenarnya bisa install-over, tapi wipe `server/` lama disarankan agar ekstraksi baru mulai bersih) |
| Verifikasi | zipalign verified; signature verified **[v1, v2, v3]** |
| File | `release-mod/db-local(MOD-LOCALSERVER-v3).apk` — **202.650.484 B**, sha256 **f3b6b81d00890036dafaeeeb97b99a740b793b188e7f9554d43fb901d42e6763** |
| Tool | ECJ 3.33.0 + D8 8.3.37 (r8, Google Maven) + baksmali 2.5.2 (fat) + uber-apk-signer 1.3.0 — jar TIDAK di-commit |

### 18.5 Cara pakai log v3 di device (INI YANG UTAMA)

1. Buka `http://127.0.0.1:4000/` di browser device (atau via `adb reverse tcp:4000 tcp:4000` lalu
   `http://127.0.0.1:4000/` di browser PC) — dashboard LANGSUNG hidup, auto-refresh.
2. Alur boot kelihatan kronologis: SYS bind → EXTRACT milestone → 9527 bin (extra=JSON) →
   9528 html/js/serversetting → 5050 OPEN/login event → 9530 /v1/*.
3. **Popup error saat game berhenti di login = PERILAKU TERDOKUMENTASI 17.7#2** (GetServerList
   TIDAK dikarang — kesepakatan tidak ada data game palsu). Dengan log v3, entry terakhir port
   5050 SEBELUM popup menunjukkan event tepat yang dikirim game — dasar kalibrasi berikutnya bila
   owner siap menambah listener 5051/5052.
4. Arsip penuh tanpa kecuali: tombol **Arsip NDJSON** di dashboard / `/download?latest=1`, atau
   langsung dari disk: `Android/data/com.db.local/server/logs/log-*.ndjson`
   (`adb pull /sdcard/Android/data/com.db.local/server/logs/`). Tahan 8 file (rotasi 16 MB).
5. `/stats` menampilkan dropped buffer — bila > 0, arsip disk tetap 100% lengkap (teruji banjir
   16.000 req: buffer kehilangan 1.026, disk 0).

### 18.6 Batasan jujur v3

1. Isi body di arsip = preview (teks 4.096/2.048 B; biner metadata `[bin N B]`) — bukan capture
   byte penuh; keputusan sadar agar 3.300 file resource tidak menulis GB data. Semua yang
   kecil-penting (config JSON, event socket.io, body SDK) muat utuh dalam preview.
2. Ring buffer RAM (15.000 global/6.000 port) bisa meluap pada boot pertama (ribuan file statis)
   — dropped counter terlihat; ARSIP DISK tetap utuh (teruji).
3. Ping/pong WS ikut di-log (±1 entry/25 dtk/sesi aktif) — kelengkapan > kepadatan.
4. Dashboard mengambil `/logs` tiap 1,5 dtk — request ini tidak masuk buffer (countSelf saja).
5. Popup login tetap ada sampai owner setuju listener 5051/5052 (server list game) ditambah —
   TIDAK dikarang tanpa izin (kesepakatan 17.7#2).

### 18.7 Status push

- Bagian 18 + `rebuild-src/javasrc/` (10 java: + DiskWriter) + `rebuild-src/scripts/repack_v3.py`
  + APK v3 (LFS) di-commit dan di-push bersamaan.
- README append-only: Bagian 1–17 TIDAK diubah sedikit pun.

---

## 19. ANTI-RACE POPUP (APK v4) + DASHBOARD LOGGER v4 — jawaban owner "teks sampah / kurang rinci / gak bisa copy" (22 Nov 2026)

Laporan owner setelah uji APK v3 (22 Nov 2026), disertai paste 15 entri log pertama dari dashboard:
<b>"banyak teks sampah keterangan yg gak perlu . kurang rinci kurang jelas juga, gak bisa copy teks .
dan ada error itu di apk mohon di benahi"</b>. Dua pekerjaan: (A) akar popup error dibuktikan dari
smali dan DIPERBAIKI (bukan lagi "perilaku terdokumentasi" — ini bug server kita sendiri);
(B) dashboard logger v4 ditulis total. Semua angka = hasil uji nyata sandbox.

### 19.1 AKAR POPUP ERROR — BUKTI SMALI (bukan tebakan)

Urutan log owner (paste, 22:08:02–22:08:06):

| t | port | event |
|---|---|---|
| 22:08:02.196 | 9527 | setting_BS_Android.bin 200 (687 B) |
| 22:08:02.262 | 9527 | com_db_local.bin 200 (2 B) |
| 22:08:03.336 | 9528 | **GET /bs/upgrade/base.version?rnd=… → 404** |
| 22:08:03.342 | 9528 | **GET /bs/upgrade/resource.version?rnd=… → 404** |
| 22:08:06.832 | 4000 | EXTRACT "ekstraksi server_root/bs selesai (2758 file)" |

= request version check datang SEBELUM ekstraksi folder upgrade/ selesai (race).
Bukti konsekuensi 404 di smali `com/zhuhuan/game/LaunchActivity.smali` (protokol update,
badcode 730–865): helper HTTP memetakan HTTP 404 → string literal `"404"`; kemudian
`cointhreat(v7,"404")` → `runOnUiThread(new ironoriginhoblike("Server base.version is Not Exist"))`
→ `goto end` (game berhenti; tidak pernah memuat index-native.html — cocok: log owner TIDAK
memiliki request html/js sama sekali). Idem `"Server resource.version is Not Exist"`.
JADI popup error owner = 404 version check buatan server kita sendiri, bukan GetServerList.

Fakta pendukung:
- File upgrade/ SEMUA ada di mirror (capture live 17.2): base.version=`110`,
  resource.version=`11390`, upgrade.json, size.json, all.zip 19.385.098 B, base.zip 31.015.076 B.
  Yang 404 bukan karena hilang — karena BELUM TEREKSTRAK (upgrade/ diekstrak paling akhir,
  readyEarly v3 dilepas setelah js/ selesai).
- Urutan versi setelah fix (dibaca dari APK asli): assets/game/base.version=`110`,
  assets/game/resource.version=`11220` → baseVersionRemote(110) == local(110) = TANPA update
  base; resVersionRemote(11390) > local(11220) = game menjalankan update resource: fetch
  upgrade.json → 11220 bukan kunci → fallback `/all.zip?v=…` (19 MB dari 127.0.0.1 — lokal,
  cepat) → PERILAKU ASLI 1:1 (smali: kunci tidak cocok → `/all.zip?v=`; size.json ikut
  di-404-check dan ada).
- Zip inkremental (11386_11390.zip dst) juga TIDAK ADA di CDN live (folder live hanya
  berisi all/base) — mirror konsisten 1:1.

### 19.2 FIX POPUP — 3 PERUBAHAN KODE

1. **StaticHandler — fallback generik disk→assets (anti-race total)**: bila file tidak ada di
   server/, dilayani LANGSUNG dari assets APK. Kedua listener kini membawa AssetsSource
   (ServerCore: cdnHandler juga). Penanda di extra log: `sumber=ASSETS (fast-path 9527)` bila
   lewat jalur cepat .bin, `sumber=ASSETS (file belum terekstrak — anti-race 19.1)` bila lewat
   fallback generik — owner bisa lihat persis kapan race terjadi.
2. **LocalServerStarter — reorder ekstraksi**: `upgrade/` naik ke rank 3 (sebelum `resource/`
   rank 4) → base.version/resource.version/upgrade.json/size.json terekstrak paling awal
   sesudah db/+html/js.
3. **copyAsset ATOMIK**: tulis ke `<nama>.extracting.tmp` lalu `renameTo` — file yang sudah
   tampak di server/ dijamin UTUH (tidak ada partial file yang dilayani); fallback bila rename
   gagal = salin byte manual (java.io murni — java.nio.file = API 26+ TIDAK dipakai demi minSdk 21).

Uji anti-race (desktop JVM; docroot sengaja DIKURANGI — folder upgrade/ DIHAPUS — assets
simulasi lengkap; kondisi persis device saat ekstraksi berjalan):

| Request ke 9528 | Hasil |
|---|---|
| /bs/upgrade/base.version | **200** `110` (4 B) — extra "file belum terekstrak" |
| /bs/upgrade/resource.version | **200** `11390` (5 B) |
| /bs/upgrade/upgrade.json | **200** 130 B (isi penuh) |
| /bs/upgrade/size.json | **200** 105 B (isi penuh) |
| /bs/upgrade/all.zip (HEAD) | **200** |
| 9527 setting_BS_Android.bin | 200 687 B (fast-path) |
| 404 tersisa | TIDAK ADA (semua endpoint game terlayani) |

### 19.3 DASHBOARD LOGGER v4 — jawaban 3 keluhan owner

| Keluhan owner | Jawaban v4 |
|---|---|
| "banyak teks sampah keterangan yg gak perlu" | seksi "Cara baca (Bagian 18)" + semua paragraf penjelasan DIHAPUS TOTAL; dashboard kini = header 1 baris (LOGGER v4 :port + uptime/seq/counter/arsip) + kontrol 1 baris + tabel. Tanpa teks lain. |
| "kurang rinci kurang jelas" | klik baris → blok TERSTRUKTUR: **REQUEST** (method + URL penuh `http://127.0.0.1:<port><path>` + header per-baris + body), **RESPON** (status + body), **EXTRA**, **ERROR**, **SEMUA (teks polos)**. JSON otomatis di-pretty (JSON config XOR ter-decode ikut pretty — indent 2). Filter: Mode ALL/PORT, Port, Kind, Status (2xx/3xx/4xx/5xx/error), Cari subset, Limit s.d. semua buffer. Kolom tabel: # / waktu / port / kind / method / path / st / dur / req / resp. |
| "gak bisa copy teks" | `user-select:text` seluruh halaman + tombol **salin** per-baris (kolom terakhir) dan per-blok detail + **Salin TSV** (kolom tabel bersih, tab-separated) + **Salin JSON** (array entri UTUH: headers/req/resp/extra/error). Rantai clipboard: navigator.clipboard → execCommand('copy') → modal "Salin manual — Ctrl+C" dengan teks otomatis terpilih (untuk browser HTTP biasa) + toast konfirmasi "tersalin N karakter". |

Uji browser nyata (agent-browser, dashboard dari server desktop):
| Uji | Hasil |
|---|---|
| Desktop 1280 px | tabel bersih live, tanpa teks sampah (screenshot `rebuild/work/dashboard-v4-desktop.png`) |
| Mobile 390 px | kontrol wrap rapi, tabel scroll horizontal (screenshot `dashboard-v4-mobile.png`) |
| Klik baris base.version | blok REQUEST/RESPON/EXTRA/SEMUA tampil; EXTRA = `sumber=ASSETS (file belum terekstrak — anti-race 19.1) \| respCT=text/plain` |
| Klik baris 9527 bin | EXTRA: DECODED-XOR di-pretty (JSON indent 2, `loginServer: http://127.0.0.1`, `update: http://127.0.0.1:9528/bs/upgrade`) |
| Tombol salin | headless tanpa izin clipboard → modal fallback terbuka, teks terpilih 295 char (rantai fallback bekerja) |
| Salin TSV | modal berisi TSV 11 kolom bersih (seq/waktu/port/kind/method/path/status/durMs/reqLen/respLen/error) |
| Filter Status=error | 0 visible / 11 hidden (semua entri 200 — benar) |
| **Banjir 6.000 req / 1,3 dtk (4.644 req/s)** | seq 6.008; **droppedGlobal 0; droppedPort semua 0**; arsip NDJSON **6.008 baris == seq (100% utuh)** |

### 19.4 HASIL APK v4 + verifikasi menyeluruh

| Item | Nilai |
|---|---|
| Sumber | APK asli (sha256 b308c44d…2218) — BUKAN v2/v3 |
| Replaced | `classes.dex` (starter — byte-identik v2/v3), `assets/config_BS.properties` (256 B identik artefak), `assets/QHinfo` (21 B identik) |
| Added | `classes2.dex` **63.120 B** (10 class — baksmali verifikasi di APK ter-sign: string "anti-race 19.1", "LOGGER v4", `extracting.tmp`/`renameTo`, rank `upgrade` ada), `assets/server_root/**` 2.758 file (byte-identik v2/v3) |
| Entri | 6.427 (= v2/v3) |
| Sign | keystore SAMA `dblocalmod` (CN=DB Local Mod, O=Owner, C=ID, exp 2056) — **zipalign verified; signature verified [v1, v2, v3]** |
| File | `release-mod/db-local(MOD-LOCALSERVER-v4).apk` — **202.650.484 B** (kebetulan sama dengan v3 — terbukti konten beda: dex v4 214f1c94… vs v3 9f1c0bd7…; 63.120 vs 57.384 B), sha256 **e1a4f3b5c32d2a4b3e1321076c3a5dc3e87b5872c00f73f714a940645d803535** |
| Tool | ECJ 3.33.0 + D8 8.3.37 (r8) + baksmali 2.5.2 (fat) + uber-apk-signer 1.3.0 (di /home/z/my-project — jar TIDAK di-commit) |

### 19.5 Perilaku yang diharapkan di device setelah v4 (jujur)

1. Popup "Server base.version is Not Exist" / "Server resource.version is Not Exist" HILANG —
   version check selalu 200 (fallback assets menutup seluruh race ekstraksi).
2. Setelah itu game berlanjut: update resource (11390 > 11220) → download all.zip 19 MB dari
   9528 lokal → ekstraksi oleh game sendiri → load index-native.html → socket.io 5050.
3. **Jika kemudian muncul popup pada TAHAP LOGIN (GetServerList)** — itu batasan 17.7#2 yang
   TIDAK berubah (balasan daftar server game/chat/dungeon TIDAK dikarang tanpa keputusan
   owner). Log 5050 di dashboard v4 kini memperlihatkan event `42[…GetServerList…]` tepat
   yang dikirim game — dasar putusan berikutnya.
4. Dashboard: `http://127.0.0.1:4000/` — semua log bisa disalin (per-baris/per-blok/TSV/JSON);
   arsip penuh tetap di `/download?latest=1` dan `Android/data/com.db.local/server/logs/`.

### 19.6 Status push

- Bagian 19 + `rebuild-src/javasrc/` (10 java) + `rebuild-src/scripts/repack_v4.py` + APK v4
  (LFS) di-commit dan di-push bersamaan.
- README append-only: Bagian 1–18 TIDAK diubah sedikit pun.


## Bagian 20 — FIX "stuck tanpa popup": SDK init result:false → result:true (2026-11-22)

### 20.1 Laporan owner + fakta dari log dashboard v4 (port 4000, sesi 23:43)

Log owner (19 entri, dashboard v4 — fitur salin bekerja): semua tahap SEBELUM init sudah
sehat setelah Bagian 19 — bind 5 listener AKTIF; 9527 config bin 200 (fast-path, XOR decoded);
9528 upgrade `base.version` 200 `110`, `resource.version` 200 `11390`, `upgrade.json` 200,
`size.json` 200, `all.zip` 200 19.385.098 B (193 ms — popup 404 Bagian 19.5 #1 terbukti hilang);
ekstraksi 2758 file selesai 23:43:24.9. Kemudian: **`POST /v1/system/init` (9530) 200 →
envelope `{"result":false,"data":[],"error":{"id":20000,"message":"Params error"}}`** — dan
SETELAH ITU TIDAK ADA REQUEST APA PUN LAGI (tidak ada index-native.html, tidak ada 5050).
Owner: "setelah update terdapat stuck tanpa popup".

### 20.2 Bukti akar dari smali klien (bukan tebakan)

| # | Bukti | Kode |
|---|---|---|
| 1 | Setelah `connect("/v1/system/init")` klien baca `getBoolean("result")`; jika **false** hanya `QGLog` lalu `return-void` — **callback init TIDAK PERNAH dipanggil** → game menunggu init selamanya = stuck tanpa popup | `com/quickgame/android/sdk/psalmicvolleyball/singersmooth.smali` cabang `:cond_17f` |
| 2 | Parser `data` init: kunci **`payTypes` WAJIB ADA** — `has()` false → log "pay type no key" → **`return-object null`** → callback tidak dipanggil → stuck. Field lain semuanya `has()`-guarded (opsional): `nodes`, `loginTypes`, `isNotGift`, `showShare`, `showDiscount`, `webUrl`, `requestIp`, `productConfig`, `otherpayType`, `payFast`, `lightPackage` | `com/quickgame/android/sdk/model/e.smali` method `cointhreat(String)` `:cond_48d` |
| 3 | Jika `version` (JSONObject) ADA → `model.g` terisi → cek waktu → runnable `singersmooth$cointhreat` = log "version update" + `startActivity(download_update)` = **popup update** → kami TIDAK mengirim `version` → runnable tidak pernah dibuat | `singersmooth.smali` `:cond_98` + `singersmooth$cointhreat.smali` |
| 4 | Callback sukses: simpan productInfo → panggil `/v1/system/getNotice` (semua di try/catch; `result:false` atau `data` ≤2 char → skip notice, aman) → "init success one/two" → **SELALU** `runOnUiThread(singersmooth$seventygenom)` = "init success three" → `onInitFinished(true)` → **game dilanjutkan** | `com/quickgame/android/sdk/singersmooth.smali` + `singersmooth$seventygenom.smali` |
| 5 | **Asal mula envelope result:false (koreksi jujur)**: capture `14.A#8` dibuat saat analisis dengan **body kosong `{}`** (fakta 14.A#8) — server asli menolak body kosong, wajar `Params error`. Mini-server v2–v4 membalaskan envelope itu ke request game yang SAH dan lengkap (`sv=v2&data=…&sign=…`, 1249 B — terbukti di log) → game yang benar diperlakukan salah → stuck | `SdkHandler` v4 + readme 14.A#8 |

### 20.3 Fix SdkHandler v5 — path-aware (satu-satunya perubahan kode)

| Path 9530 | Respons v5 | Dasar |
|---|---|---|
| `/v1/system/init` | `{"result":true,"data":{"payTypes":[],"loginTypes":["12"]}}` | 20.2 #2: `payTypes` kunci wajib; `loginTypes ["12"]` = tanpa cek GMS + tanpa counter; **tanpa `version`** (hindari popup update 20.2 #3); **tanpa `nodes`** (daftar server SDK tidak di-set — GetServerList adalah jalur socket.io 5050) |
| `/v1/system/getNotice` | `{"result":true,"data":""}` | 20.2 #4: `data` ≤2 char → skip notice (tidak ada notice untuk ditampilkan) |
| `/v1/system/onNewToken`, `/v1/system/dmsg`, `/v1/system/getAgreement` | `{"result":true,"data":""}` | sukses kosong — tidak ada isi untuk ditampilkan |
| semua path lain (v1/user/*, v1/auth/*, v2/purchase) | envelope capture 14.A#8 `result:false` **TIDAK berubah** | belum ada bukti format respons yang benar — semua request tetap terekam utuh di logger untuk iterasi berikutnya |

Penanda extra di log: `INIT-SUCCESS 20.1 | data dari parsing smali model/e.cointhreat: …` —
owner bisa melihat persis respons apa yang dikirim dan alasannya.

### 20.4 Verifikasi v5 (toolchain dibangun ulang — sandbox reset ke-2)

Toolchain hilang karena reset; dibangun ulang dan semuanya terverifikasi: ECJ 3.33.0 (mirror
Aliyun), baksmali/smali org.smali 2.5.2 + dexlib2/util/guava 27.1-android/jcommander/jsr305
(mirror Aliyun), r8/D8 8.3.37 (dl.google.com), android.jar API 21 (Sable), uber-apk-signer 1.3.0
(selamat di my-project). Keystore `dblocalmod` (pass tercatat Bagian 19) selamat di repo.

| Item | Hasil |
|---|---|
| Kompilasi | ECJ `-source 8 -target 8` + `android.jar` API21 → 19 class → D8 `--release --min-api 21` → `classes2.dex` **63.692 B** sha256 `61899dd3…` (+572 B vs v4) |
| Baksmali verifikasi (dex dari APK TER-SIGN) | string `INIT-SUCCESS 20.1`, `payTypes`/`loginTypes ["12"]`, `getNotice`, `Params error` — semua ada di `SdkHandler.smali` |
| Smoke test desktop JVM (handler asli, 5 request) | init → `result:true data:{payTypes:[],loginTypes:["12"]}`; getNotice/onNewToken → `result:true data:""`; `/v1/auth/getUserInfo` + `/v2/purchase` → envelope capture (fallback) |
| Repack v5 (`repack_v5.py`) | base APK asli (sha256 `b308c44d…` — diunduh ulang via media LFS, identik); `classes.dex` starter + `config_BS.properties` 256 B + `QHinfo` 21 B + `assets/server_root/**` 2758 file — **semuanya byte-identik v4** (diff 0); sign ulang META-INF dibuang |
| APK final | `release-mod/db-local(MOD-LOCALSERVER-v5).apk` — **202.650.484 B**, sha256 **`70fc5b7ff103d999cac2144ab6c605c9d8a6e0a7bfe2e2e4ed19a244b274bd3d`**, 6.427 entri (= v4), zipalign verified, **signature verified [v1, v2, v3]**, sertifikat CN=DB Local Mod exp 2056 (keystore SAMA) |

### 20.5 Perilaku yang diharapkan di device setelah v5 (jujur)

1. Init SDK: parser klien baca `result:true` → `data` → `payTypes` ada → model QGProductInfo
   non-null → callback jalan → getNotice (skip) → "init success three" → **`onInitFinished(true)`**
   → game melanjutkan alur (update resource sudah terbukti 200 di log Bagian 20.1).
2. Tahap berikutnya = load `index-native.html` (9528) + login socket.io 5050 (`GetServerList`
   dsb.). Jika di sana ada tahap yang macet/gagal, dashboard v4 memperlihatkan event socket.io
   persis seperti yang dikirim game — dasar putusan iterasi berikutnya (batasan 17.7 #2 untuk
   daftar server game/chat/dungeon tetap: TIDAK dikarang tanpa keputusan owner).
3. Popup update SDK tidak akan muncul (`version` tidak dikirim — 20.2 #3).

### 20.6 Status push

- Bagian 20 + `rebuild-src/javasrc/com/db/local/server/SdkHandler.java` (baru) +
  `rebuild-src/scripts/repack_v5.py` (baru) + APK v5 (LFS) di-commit dan di-push bersamaan;
  `db-local(MOD-LOCALSERVER-v4).apk` dihapus dari repo (diganti v5 — pola sama dengan v2→v3→v4).
- README append-only: Bagian 1–19 TIDAK diubah sedikit pun.


---

## 21. LOGIN NATURAL "LOCAL TAPI BERASA ONLINE" + LOGGER v5 (22 Nov 2026)

Laporan owner setelah v5: **menu game muncul "masukin kode migrasi"** ("bentar tampilan menu kok
masukin kode migrasi") lalu permintaan: *"saya mau ini berfungsi sempurna.. jadi mohon untuk
rebuild secara natural.. local tapi berasa nyata online. dan juga soal log monitoring bagi saya
masih terasa sulit di baca"*. Bagian ini = penjelasan akar layar kode migrasi dari smali + JS
(bukan tebakan), implementasi alur login natural lengkap, server game 5051, dan dashboard logger
v5 yang dibaca manusia. Semua angka = hasil uji nyata sandbox.

### 21.1 Akar layar "kode migrasi" — rantai lengkap dari smali + JS

| # | Bukti | Kode |
|---|---|---|
| 1 | Game memanggil `QuickGameManager.init(...)` → callback `onInitFinished(true)` → **langsung `QuickGameManager.login(activity)`** | `cointhreat/cointhreat/cointhreat/pursetruechild/feedtonight/singersmooth.smali` `onInitFinished` |
| 2 | `login()` → `feedtonight.hardlinerspare()`: bila ada session tersimpan → langsung `onLoginFinished` TANPA UI; bila TIDAK ada → buka `HWLoginActivity` action **`ACTION_LOGIN`** → karena tidak ada akun tersimpan (user_name/last_token kosong di model/b) → tampil **layar login SDK** (`hw_fragment_login.xml` + `hw_view_login_cdkey.xml`) | `feedtonight.smali` `hardlinerspare` `:cond_6b`; `HWLoginActivity$singersmooth.onServiceConnected :cond_84` |
| 3 | Layar itu = milik SDK (bukan game) — string "Enter migration code" / "\nMigration code:\n" ada di `resources.arsc` APK asli (en/fr/de). Ini yang dilihat owner | `strings resources.arsc` original.apk (grep biner) |
| 4 | Game (MainActivity) TIDAK PERNAH memanggil `freeLogin()` (auto-register) — hanya `login()` standar → UI muncul tiap instalasi bersih | grep invoke QuickGameManager di `MainActivity.smali`: hanya init/pay/logout/floatview/lifecycle |

### 21.2 Kontrak yang ditemukan (dasar implementasi — semua dari smali/JS, tidak dikarang)

**SDK (9530, via QHinfo):**
- `POST /v1/user/registerVisitor` — tamu (HWLoginActivity ACTION_AUTO_REGISTER_AND_LOGIN →
  `dishbesideshockey$nationalcommunitymissing$seventygenom`). Respons: `getBoolean("result")` +
  `getString("data")` → session string diparse `psalmicvolleyball/cointhreat.cointruth`:
  **`authToken` wajib**, **`userData` (JSONObject) wajib** → `QGUserData.generateFromJson`:
  `username/uid/displayUid/token` getString wajib, `isGuest` "1"=tamu, `openType` optString,
  `isNewUser` optInt, `isTrash` optInt; `tips/bindInfo/checkRealName` opsional.
- `POST /v1/user/autoLogin` — launch berikutnya (`dishbesideshockey$hardlinerspare` →
  `service.cointruth(authToken)`) — format respons sama.
- Login sukses → `service.seventygenom(14)` → akun TERSIMPAN di prefs klien → launch berikutnya
  tanpa UI sama sekali (perilaku "server nyata").

**Game wrapper native (setelah onLoginFinished):**
- `pursetruechild/feedtonight/singersmooth.onLoginFinished` → HashMap
  `uid/username/token/usermode` → **`GET {loginServer}:{loginWebPort}/loginchecknative?…&os=Android&sdk=…`**
  (HttpURLConnection GET, retry 3× pada kegagalan). Karena config `loginServer=http://127.0.0.1`
  + `loginWebPort=5050` → URL = `http://127.0.0.1:5050/loginchecknative` (DILAYANI SocketIoHandler
  5050, bukan 9530).
- Respons: **body RAW disimpan sebagai `thirdParams`** untuk JS, lalu divalidasi
  `getInt("code")==0` → `MainActivity.customreading()` → WebView `index-native.html` lanjut.
- Java→JS `startGame` payload (`EgretNativeAndroid.callExternalInterface`): `loginServer`
  (= loginServer:loginPort = `http://127.0.0.1:5050`), `thirdParams` (= body loginchecknative),
  `clientParams` (dari config bin) → `index-native.html` `egret.ExternalInterface.addCallback("startGame")`.

**JS game (index-native.html + main.min_777039fc.js):**
- `getSdkLoginInfo()` membaca `thirdParams` (flat: `sdk/userid/userId/nickname/securityCode/…`)
  → Login scene: `ts.loginUserInfo.userId/sign/sdk` → `isFromSdk=true` → `sdkLoginSuccess(o)` →
  **langsung `GetServerList` (tanpa form username/password)**.
- `clientRequestServerList` = `socket.emit("handler.process", {type:"User",action:"GetServerList",
  userId, subChannel, channel}, ack)` — **socket.io v2.1.2 ACK** (wire: `42<id>["handler.process",{…}]`,
  jawaban server `43<id>[<args>]`; konfirmasi di socket.io.min_2e0501e.js `ack=function(t){…packet({type:ACK,id:t,data:r})}`).
- `processHandlerWithLogin`: `ret!==0` → "登录出错"; `data` = STRING JSON; `compress` → LZString.
- `selectNewServer`: `t.serverList[0].serverId` (atau `t.history[0]`) → item server dibaca
  `serverId/name/online/hot/new/url/offlineReason` (ServerListNode/ServerListItem).
- `clientStartGame`: `mainClient.connectToServer(ts.loginInfo.serverItem.url)` = **socket KEDUA
  (server game)** → `user/enterGame` `{loginToken,userId,serverId,version,language,gameVersion}`
  → respons = data pemain → `UserDataParser.saveUserData(t)` → `e.newUser` → laporan create-role →
  `ts.runScene("OverScene", FIRSTENTER)` (scene utama game).
- `saveUserData` membaca (yang WAJIB ada): `currency` (string kode), `user._*`, `hangup._*`,
  `globalWar*`, `summon._*`, `totalProps._items`, `backpackLevel`, `superSkill` (**array** —
  `for(n=0;n<e.length)` crash bila undefined), `heros`, `curMainTask` (**{} wajib** —
  `Object.keys(e)` crash bila undefined), `scheduleInfo` (AllRefreshCount.initData),
  `dragonEquiped` (**{} wajib** — `for-in`), sisanya optional-guarded (`imprint/checkin/userGuild/
  guildLevel/guide/vipLog/cardLog/clickSystem/giftInfo/dungeon/teamTraining/channelSpecial`).
- Lanjutan: `user/getBulletinBrief` → `{_brief:{}}`.

### 21.3 Implementasi v6

**Patch smali (2 titik — seminimal mungkin):**
| File | Patch | Alasan |
|---|---|---|
| `com/zhuhuan/game/Application.smali` | sisip `LocalServerStarter.start(p0)` sebelum invoke-super (pola 17.5, byte-identik v4/v5) | starter server |
| `com/quickgame/android/sdk/feedtonight.smali` `hardlinerspare` `:cond_6b` | `const-string "com.quickgame.android.sdk.ACTION_LOGIN"` → **`"com.quickgame.android.sdk.ACTION_AUTO_REGISTER_AND_LOGIN"`** (SATU baris, 1 kemunculan di file) | login() game → jalur **tamu auto-register** yang sudah ada di SDK (bukan layar login): autologin=true (default feedtonight), tanpa akun tersimpan → `registerVisitor` otomatis + dialog "Logging in…" sebentar → sukses → akun tersimpan di klien → launch berikutnya TANPA UI |

**Mini-server v6 (`classes2.dex` — 22 class, 80.876 B):**
| Komponen | Isi |
|---|---|
| `AccountStore.java` (baru) | akun tamu PERSISTEN `<serverRoot>/data/account.json` (uid/token/username) — uid sama antar restart seperti server nyata; menyusun session JSON & thirdParams PERSIS kontrak 21.2 |
| `SdkHandler.java` | + `/v1/user/registerVisitor` (register tamu, isNewUser=1 di register, 0 di autoLogin), `/v1/user/autoLogin`, `/v1/auth/setGameRoleInfo` & `/v1/user/getServerAgreement` (sukses kosong); init/getNotice v5 TIDAK berubah; path lain tetap envelope 14.A#8 |
| `SocketIoHandler.java` | 2 mode (LOGIN/GAME): **lapisan event socket.io `handler.process` dengan ACK v2** (`42<id>`→`43<id>[{ret,data,compress}]`) di polling POST & frame WS; **`/loginchecknative`** dilayani di 5050 (body raw thirdParams + `code:0`); routing: GetServerList/loginGame/LoginAnnounce/SaveUserEnterInfo/SaveLanguage/saveHistory/getNotice/getBulletinBrief/enterGame; action dibandingkan case-insensitive; fallback = `{ret:0,data:"{}"}` + terekam utuh di logger |
| `ServerCore.java`/`LoggerCore.java` | listener **baru 5051** (GAME, `serverItem.url`); `PORT_GAME=5051` masuk counter/droppedPort/stats |
| `GameData` (dalam SocketIoHandler) | `serverList` = 1 server "DragonBall Local" id 1 hot url `http://127.0.0.1:5051`; `enterGame` = payload pemain baru (newUser:true) dengan SEMUA kunci wajib 21.2 (defensif: objek/array kosong) |
| `LoggerHandler.java` | **dashboard v5 "MONITOR SERVER"** (21.4) |

### 21.4 Dashboard logger v5 — jawaban "masih terasa sulit di baca"

- **Kartu alur** per kejadian (bukan tabel teknis): BADAN TAHAP berwarna (SISTEM/KONFIG/CDN/SDK/
  LOGIN/GAME) + **deskripsi bahasa Indonesia manusia** diturunkan dari port+path (`describe()`):
  "Buat akun tamu — uid … tersimpan permanen", "Cek versi dasar — 110", "Unduh paket update —
  19.4MB dalam 187ms", "Verifikasi akun (loginchecknative) — code 0, game dilanjutkan",
  "Event login GetServerList — dijawab", "Masuk server 1 — data pemain baru dikirim (1.4KB)".
- Titik status besar berwarna (hijau/kuning/merah), meta ringkas (#seq, jam, durasi, ukuran).
- **STATUS SERVER**: chip per port (9527 KONFIG / 9528 CDN / 5050 LOGIN / **5051 GAME** / 9530 SDK /
  4000 LOGGER) — hijau bila ada aktivitas, jumlah req + gagal.
- **RINGKASAN**: "Terakhir penting:" — kalimat milestone terakhir (akun dibuat/verifikasi sukses/
  masuk server/UI dimuat), peringatan ⚠ otomatis untuk status ≥400.
- Semua kemampuan v4 tetap: klik kartu = blok REQUEST/RESPON/EXTRA/ERROR (JSON auto-pretty),
  tombol salin per blok, Salin daftar (TSV), Salin JSON, Unduh arsip NDJSON, filter tahap/status/
  cari/jumlah, jeda; arsip NDJSON `<serverRoot>/logs` tidak berubah.

### 21.5 Verifikasi v6 (semua di sandbox, angka nyata)

| Item | Hasil |
|---|---|
| Kompilasi server | ECJ 3.33.0 `-source 8 -target 8` + android.jar API21 → **22 class** → D8 8.3.37 → `classes2.dex` **80.876 B** sha256 `f359a1ee…` (v5: 63.692 B) |
| Smoke test desktop (9 uji) | init ✓; registerVisitor ✓ (`result:true`, session `authToken/userData/isGuest:"1/openType:"12"/isNewUser`, akun PERSISTEN — uid sama antar run); autoLogin ✓; loginchecknative ✓ (`code:0`+userid+loginToken); **GetServerList ACK** ✓ (`serverList`,`127.0.0.1:5051`); **enterGame ACK** ✓ (payload 1.397 B lengkap kunci wajib); getBulletinBrief ✓; LoginAnnounce ✓; SaveUserEnterInfo ✓ — **9/9 PASS** |
| Smoke test **WebSocket** | upgrade 101 → open `0{...}` → `2probe/3probe` → event `42` via WS → **ACK `43` dikirim via frame WS** (5050 GetServerList ✓, 5051 enterGame ✓, LoginAnnounce ✓) — jalur upgrade socket.io v2 klien terjawab |
| Patch smali → dex | smali 2.5.2 assemble: `classes_v6.dex` **6.697.176 B**; baksmali-verifikasi: `ACTION_AUTO_REGISTER_AND_LOGIN` ×1, `ACTION_LOGIN"` ×0, `LocalServerStarter` ×1 |
| Repack v6 | base APK asli sha256 `b308c44d…`; config_BS 256 B & QHinfo 21 B & server_root 2.758 file **byte-identik v5** (assert diff 0); classes2.dex + server_root ditambah |
| APK final | `release-mod/db-local(MOD-LOCALSERVER-v6).apk` — **202.658.676 B**, sha256 **`cbbc04f2287030ddadddb4a073e5a1dcce0c177afe615be1a5b1acaccc4ddb06`**, 6.427 entri (=v5), zipalign verified, **signature verified [v1, v2, v3]**, CN=DB Local Mod exp 2056 (keystore SAMA) |
| Baksmali dari APK TER-SIGN | classes.dex: patch login tamu ✓ starter ✓; classes2.dex: 22 class v6 ✓ (`AccountStore`, `SocketIoHandler$GameData`, const `0x13bb`=5051 di SocketIoHandler/ServerCore/LoggerCore, `loginchecknative`/`registerVisitor`/`autoLogin` ✓) |
| Dashboard v5 | diuji live di sandbox: HTML ter-serve, /stats memuat 5051, /logs kronologis, kartu tahap + deskripsi tampil |

### 21.6 Perilaku yang diharapkan di device (jujur — dua lapis)

1. **Layar kode migrasi HILANG**: `login()` game → jalur tamu auto-register → POST
   `registerVisitor` (9530) → dialog "Logging in…" sebentar → sukses → akun tersimpan klien.
   **Instalasi kedua dst. langsung masuk tanpa UI sama sekali.**
2. **Verifikasi akun**: `loginchecknative` (5050) → `code:0` → WebView `index-native.html` (9528).
3. **Login game**: JS `getSdkLoginInfo` → `GetServerList` (5050 ACK) → server "DragonBall Local"
   (hot) → pilih otomatis (`selectNewServer`) → connect `http://127.0.0.1:5051`.
4. **Masuk game**: `enterGame` (5051 ACK) → data pemain baru → `saveUserData` → scene utama
   (OverScene). **Batasan jujur**: payload pemain baru = struktur MINIMAL hasil parsing
   `saveUserData` — kemungkinan besar perlu 1–3 iterasi device (ada field yang belum terlihat
   aksesnya di JS) — SEMUA request game selanjutnya (getInfo dkk.) dijawab `{ret:0,data:"{}"}`
   dan TEREKAM UTUH di logger → iterasi berikutnya berbasis log, bukan tebakan. Gameplay penuh
   (battle/guild/dll) = server game sungguhan, di luar skop rebuild ini.
5. Dashboard 4000 = "MONITOR SERVER" — tahap + kalimat manusia; klik untuk detail teknis.

### 21.7 Status push

- Bagian 21 + `rebuild-src/javasrc/com/db/local/server/AccountStore.java` (baru) +
  5 java (SdkHandler/SocketIoHandler/ServerCore/LoggerCore/LoggerHandler) +
  `rebuild-src/scripts/repack_v6.py` (baru) + APK v6 (LFS) di-commit & di-push bersamaan;
  `db-local(MOD-LOCALSERVER-v5).apk` dihapus dari repo (diganti v6 — pola sama v2→v3→v4→v5→v6).
- README append-only: Bagian 1–20 TIDAK diubah sedikit pun.
