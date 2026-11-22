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
