# 📋 LAPORAN ANALISIS APK db-local (Original) — REVISI 3

> Laporan tunggal hasil bedah penuh APK asli + data runtime asli dari perangkat.
> Sumber: `db-local-original.apk` (104.945.181 bytes) + hasil ekstrak data perangkat asli di `log/extracted/`.
> Tanggal analisis: 2026-11-21 (revisi 3) · Status proyek: **FASE 1 (analisis) SELESAI**
>
> **REVISI 3** — koreksi kategori endpoint:
> 1. **§3.1 = endpoint yang BENAR-BENAR ADA DI DALAM APK** (hasil scan byte-per-byte seluruh isi APK: **85 URL unik di 4 file** + 3 file konfigurasi Base64). Ini yang bisa diedit.
> 2. **§3.2 = endpoint yang TIDAK ADA di dalam APK** (`pksilo.com` dll.) — sumbernya data runtime perangkat. Revisi 2 menyajikannya seolah bagian dari APK — **salah**. String `pksilo`, `pigres`, `xgogaming`, `sjflaregame` tidak ada satu pun di APK.
> 3. Struktur `Android/data/<PACKAGE>/` (eksternal) tetap berdasarkan bedah kode (`getExternalFilesDir` → `files/game/`, DecodeUtil `.bin`, update `base.zip`/`all.zip`). Isi `data/data` (internal) dilampirkan di §2.6 sebagai referensi terpisah — **itu BUKAN android/data**.

---

## 1. Identitas APK

| Item | Nilai | Bukti |
|---|---|---|
| Nama aplikasi (repack) | **DB - Local** | resources.arsc |
| Package (repack, di repo ini) | **`com.db.local`** | AndroidManifest.xml |
| Package game asli di perangkat | **`com.Vanguard.twnthe`** | folder data perangkat |
| Versi | **1.0.1** (versionCode 1) | AndroidManifest.xml |
| minSdk / targetSdk | 21 / 30 · `requestLegacyExternalStorage="true"` · `usesCleartextTraffic="true"` · `networkSecurityConfig` ada | AndroidManifest.xml |
| Launcher / Game Activity | `com.zhuhuan.game.LaunchActivity` / `com.zhuhuan.game.MainActivity` | AndroidManifest.xml |
| Engine game | **Egret Engine** (HTML5/TS dijalankan runtime native `libegret.so` + WebView) | `lib/*/libegret.so` |
| Proteksi resource | **libgavesec.so** — kode JS game di `assets/game/` terenkripsi (file `.bin`), didekripsi saat first-run | `lib/*/libgavesec.so` |
| Hot-load DEX | `assets/runtime-dex.jar` (classes.dex 183.948 bytes) | unzip APK |
| SDK akun | **QuickGame SDK** (`com.quickgame.android.sdk.*`, 21 Activity) | manifest |
| Versi paket dasar game | `assets/game/base.version` = **110** · `assets/game/resource.version` = **11220** | isi file asset |
| ABI | arm64-v8a, armeabi-v7a | folder `lib/` |

**12 permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `WAKE_LOCK`, `REORDER_TASKS`, `SYSTEM_ALERT_WINDOW`, `SYSTEM_OVERLAY_WINDOW`, `com.android.vending.BILLING`, `BIND_GET_INSTALL_REFERRER_SERVICE`.

**Komponen manifest:** 21 Activity (semuanya SDK: QuickGame `HW*`/`AccountRecover`/`BindEmail`/`RedeemCode`/`OnestorePay`, Facebook 4, Google Sign-In 2) · 3 Service (`QGConnectionService`, `QuickGameSdkService`, `RevocationBoundService`) · 1 Receiver (`CurrentAccessTokenExpirationBroadcastReceiver`).

---

## 2. BAGIAN 1 — Folder & file yang dibuat di `Android/data/<PACKAGE>/` (eksternal)

### 2.1 Fakta kunci (wajib dibaca dulu)

- Yang dimaksud soal ini adalah **`/storage/emulated/0/Android/data/<PACKAGE>/`** (storage eksternal) — **BUKAN** `/data/data/<PACKAGE>/` (internal). Dua-duanya ada, isinya beda total.
- `<PACKAGE>` untuk **APK asli di perangkat = `com.Vanguard.twnthe`**; untuk **APK repack di repo ini = `com.db.local`**. Struktur isinya identik 1:1, hanya nama foldernya yang mengikuti package.
- Folder ini **TIDAK dibuat saat install**, melainkan saat **pertama kali aplikasi dijalankan** (first launch).
- Bukti di bawah ini adalah **hasil bedah bytecode APK** (bukan tebakan): `LaunchActivity` memanggil `Context.getExternalFilesDir("")` lalu menyambung **`/game/`**, `DecodeUtil` (libgavesec) mendekripsi `assets/game/*.bin` dan mengekstraknya ke folder itu, dan sistem update menulis `base.zip`/`all.zip`/`tmp.zip` di sana juga.

### 2.2 Bukti kode (bytecode classes.dex, nama variabel asli masih tersimpan sebagai debug string)

| Bukti | Lokasi kode | Arti |
|---|---|---|
| `invoke-virtual ... getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;` + const-string `"/game/"` | `Lcom/zhuhuan/game/LaunchActivity;` | Folder eksternal `Android/data/<pkg>/files/game/` dibuat/dipakai wrapper |
| const-string `"config_%s.properties"`, `"config.properties"`, `"EntryPoint"` + Base64 | `LaunchActivity` (GameUpdateUtil) | Konfigurasi update dibaca dari assets |
| const-string `"game/base.version"`, `"game/resource.version"` (assets) → banding URL remote `"%s/base.version?rnd=%f"`, `"%s/resource.version?rnd=%f"` | `LaunchActivity` | Versi lokal (110 / 11220) dibanding versi server update |
| const-string `"/base.zip?v="`, `"/all.zip?v="`, `"%s/upgrade.json?rnd=%f"`, `"%s/size.json?rnd=%f"`, `"tmp.zip"` | `LaunchActivity` + util | Paket update diunduh sebagai `tmp.zip` lalu diekstrak |
| Method DecodeUtil: SharedPreferences **`decode_bin`** key **`decodeBinFinish`**, baca `assets/game/<bin>.bin`, log `"开始解压bin文件"` (mulai ekstrak bin), argumen kedua bernama **`mPreloadPath`** | `Lcointhreat/.../reweavingsiamesedpropertylessnesses/cointhreat;` | File `.bin` terenkripsi gavesec didekripsi → **unzip ke `mPreloadPath` = `Android/data/<pkg>/files/game/`** |
| Log `"更新资源路径: "` + `FileOutputStream` + pembersihan tree `<host>#0A<port>/resource/assets/image`, `/resource/assets/image_merge` | util update | Cache resource per-host juga ditulis di bawah `files/game/` |
| `EgretNativeAndroid` → `cachePath = Activity.getFilesDir()` (TIDAK dioverride ke external) | `Lorg/egret/egretnativeandroid/EgretNativeAndroid;` | Cache runtime Egret (`files/games/`) ada di **internal**, bukan Android/data |
| string `"/sdcard/egret_internal"` berdampingan dengan `saveImageDataToAlbum` | `lib/arm64-v8a/libegret.so` | Hanya saat game menyimpan gambar ke album → folder staging di root sdcard (bukan di Android/data; Android ≤10 dengan legacy storage) |

### 2.3 Struktur 1:1 `Android/data/<PACKAGE>/`

```
/storage/emulated/0/Android/data/<PACKAGE>/          ← <PACKAGE> = com.Vanguard.twnthe (APK asli) / com.db.local (repack)
│
├── files/                                            ← dibuat saat first launch (parent dari game/)
│   └── game/                                         ← DIBUAT WRAPPER: getExternalFilesDir("") + "/game/"
│       │   ── ISI = paket game H5 Egret hasil DEKRIPSI assets/game/*.bin (libgavesec)
│       │   ── + hasil update base.zip / all.zip bila server update lebih baru dari versi asset 110/11220
│       │
│       ├── index-native.html                         ← entry HTML game (Egret)
│       ├── manifest.json                             ← manifest resource Egret
│       ├── clientversion.json → properties/clientversion.json   {"clientVersion":"..."}
│       ├── resource/                                 ← pohon resource Egret (identik dgn mirror CDN, ±4.676 subfolder)
│       │   └── assets/
│       │       ├── image/                            ← sprite sheet (folder khusus — dibersihkan saat update: bukti kode)
│       │       ├── image_merge/                      ← sprite sheet hasil merge (idem)
│       │       └── ...                               ← *_ske.json / *_tex.json / *_tex.png (Dragon Bones), asset TS, dll.
│       ├── <host>#0A<port>/resource/assets/…         ← cache resource per-host (mode download native), mis. pigres.pksilo.com#0A80/
│       ├── base.version                              ← versi base terakhir yang terpasang (mis. 110)
│       ├── resource.version                          ← versi resource terakhir (mis. 11220)
│       ├── upgrade.json / size.json                  ← daftar update & ukuran paket (saat ada update)
│       ├── tmp.zip                                   ← file download sementara (dihapus setelah diekstrak — bukti kode File.delete())
│       └── base.zip / all.zip                        ← paket update (kode dasar / resource lengkap) saat mode zip aktif
│
└── cache/                                            ← dibuat OS, kosong (tidak ada kode app yang menulis di sini)
```

**Catatan jujur soal isi `files/game/`:** daftar **nama folder/file di atas = struktur game Egret** yang terverifikasi dari mirror CDN di cache perangkat (pigres.pksilo.com, §3.2) + titik tulis yang terverifikasi di kode. Nama file `.bin` asli tidak bisa dilist per-file dari APK karena terenkripsi gavesec (dekripsi hanya terjadi di runtime — fasa berikutnya bisa didump).

### 2.4 Yang dibuat saat first-run vs saat update

| Waktu | Yang muncul di `Android/data/<pkg>/` |
|---|---|
| First launch (sebelum login) | `files/game/` + hasil dekripsi & ekstrak `.bin` (seluruh paket dasar game H5). Tanda selesai: `data/data/<pkg>/shared_prefs/decode_bin.xml` → `decodeBinFinish=true` |
| Saat ada update versi | `tmp.zip` (proses) → diekstrak menimpa isi `game/`; `base.version`, `resource.version`, `upgrade.json`, `size.json`, `base.zip`/`all.zip` |
| Saat fitur simpan gambar dipakai | `/sdcard/egret_internal/` (root sdcard — BUKAN di dalam Android/data) |
| Tidak pernah dibuat oleh kode app | `Android/data/<pkg>/cache/` (hanya OS, kosong) |

### 2.5 Yang TIDAK ada di `Android/data` (sering salah dikira)

Semua folder ini ada di **`/data/data/<pkg>/`** (internal) — **bukan** di `Android/data`: `shared_prefs/`, `databases/`, `code_cache/`, `no_backup/`, `app_webview/`, `app_textures/`, `oat/`, cache WebView/HTTP Chromium, dan cache runtime Egret `files/games/` (semua mirror resource server: 96% isi dump perangkat ada DI SINI, di internal).

### 2.6 Lampiran referensi — isi `data/data/<pkg>/` (INTERNAL, hasil ekstrak perangkat asli)

Ini dump asli dari perangkat (`log/extracted/com.Vanguard.twnthe`, 4.816 folder / 16.348 file / 178.011.704 B) — dilampirkan hanya sebagai referensi, **BUKAN** jawaban bagian `Android/data`:

```
/data/data/com.Vanguard.twnthe/
├── app_textures/                        (kosong — dibuat getDir(), internal)
├── app_webview/                         36 file · 123.605 B — Chromium WebView (Cookies, Local Storage/leveldb [berisi cache Chatwoot cw.sdkapi.xgogaming.com], Session Storage, shared_proto_db, blob_storage/<GUID>, PersistentOriginTrials)
├── cache/                               32 file · 1.705.931 B — Crash Reports/ANR, WebView/{Crash Reports,SafeBrowsing,Crashpad}, HTTP Cache + Code Cache js/wasm, image_manager_disk_cache, instrument, oat_primary/arm64/base.art
├── code_cache/                          (kosong)
├── databases/                           6 file — com.google.android.datatransport.events(+journal), evenLog1.db(+journal), google_app_measurement_local.db(+journal)
├── files/                               16.254 file · 171.840.892 B (96% total)
│   ├── AFRequestCache/{AttrCache,ConversionsCache,OtherCache}/   (AppsFlyer)
│   ├── AppEventsLogger.persistedevents, puIdMessage.txt (40 hex), userMessage.txt, GameDataCache, runtime-dex.jar, profileInstalled, profileinstaller_profileWrittenFor_lastUpdateTime.dat
│   ├── oat/arm64/, phenotype/{shared}, phenotype_storage_info/{shared}
│   └── games/                           ← CACHE RUNTIME EGRET (cachePath = getFilesDir())
│       ├── http/pigres.pksilo.com/      9.688 file — mirror CDN: activity_en/{8 grup banner}, client/{manifest.json#, index-native.html#, properties/clientversion.json#, resource/… 4.676 subfolder}
│       └── https/                       6.551 file — cache socket.io: login.pksilo.com#0A18110, s422-zd…#0A10100, s431-zd…#0A10300, s432-zd…#0A10800, s49931-zd…#0A10507 (format <host>#0A<port>, tiap respons <md5> + <md5>#header)
├── no_backup/.webview/                  1 file · 4.194.304 B
├── oat/arm64/                           4 file · 720 B
└── shared_prefs/                        15 file — AwOriginVisitLoggerPrefs, FirebaseHeartBeat…, PREFERENCE_HAOCHENHUDONG (CONFIG BACKEND AKUN), WebViewChromiumPrefs, appsflyer-data, com.Vanguard.twnthe_preferences (→ com.db.local_preferences di repack), com.facebook.* (5 file), com.google.android.gms.measurement.prefs, decode_bin.xml (decodeBinFinish), pigres.pksilo.com.xml (30 KB · 306 key — save data Egret/localStorage game)
```

---

## 3. BAGIAN 2 — Inventaris endpoint (LENGKAP 1:1, dengan bukti)

### 3.1 ENDPOINT YANG BENAR-BENAR ADA DI DALAM APK — hasil scan byte-per-byte

Metode: seluruh isi APK di-scan byte-per-byte (AndroidManifest.xml, semua file `assets/`, `res/`, `resources.arsc`, `classes.dex`, `lib/*.so`, `META-INF/`, file properti Google) dengan regex URL, lalu noise XML/EXIF (namespace Adobe/W3C di metadata gambar) dibuang. Hasil mentah: **85 URL unik, hanya di 4 file**: `classes.dex`, `lib/arm64-v8a/libegret.so`, `lib/armeabi-v7a/libegret.so`, `assets/QHinfo`. Plus **3 file konfigurasi yang URL-nya Base64-encoded** (tidak tertangkap regex, didecode manual).

**⚠️ FAKTA PENTING:** string `pksilo.com`, `pigres`, `sjmobilegame.com` (plaintext), `sjflaregame.com`, `xgogaming.com`, `sdkapi` — **NOL kecocokan di seluruh isi APK**. Endpoint game ada di level RUNTIME (dijawab server config / respons login / JS terenkripsi), bukan hardcode plaintext di APK.

#### 3.1.1 Endpoint aplikasi — YANG DIEDIT UNTUK REDIRECT (3 file kecil)

| # | File di dalam APK | Isi mentah | Ter-decode | Status server |
|---|---|---|---|---|
| APK1 | `assets/config.properties` | `EntryPoint1=` + `EntryPoint1_debug=` (Base64, 2 baris) | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | ❌ 301 → mati |
| APK2 | `assets/config_BS.properties` | `EntryPoint1=` + `EntryPoint1_debug=` (Base64, 2 baris) | `https://configus.sjmobilegame.com/bs/db/android` | ❌ 404 COS `NoSuchKey` |
| APK3 | `assets/config_BS.properties` | `EntryPoint2=` + `EntryPoint2_debug=` (Base64, 2 baris) | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` | ❌ fallback |
| APK4 | `assets/QHinfo` | plaintext 1 baris | `http://qsdk.t4game.com` | ✅ 200 — API base QuickGame SDK |

**Rantai runtime setelah edit 3 file ini** (dibaca dari kode `LaunchActivity` + `MainActivity`):

```
APK1/2/3 (EntryPoint, Base64) → GET native_setting/Android → respons JSON = config runtime
   (channel, gameUrl, nativeUnzipJson, URL server update, dsb.)
APK4 (QHinfo) → QuickGame SDK init/login → respons SDK berisi "gameUrl" (Base64)
   — bukti bytecode: log "gameUrl: " + "Base64.decode(gameUrlTs.…, Base64.DEFAULT)" di MainActivity
→ wrapper memuat game (Egret/WebView) → JS game menghubungi server login/battle/CDN (§3.2)
```

→ **Dengan mengedit APK1–APK4, seluruh alur config + gameUrl diambil alih local server.**

**Endpoint turunan yang URL-nya diturunkan dari config native_setting** (dipanggil `GameUpdateUtil` di `LaunchActivity`; bukan string mandiri di APK):

| # | Path yang dipanggil di server update | Fungsi |
|---|---|---|
| A5 | `<updateServer>/base.version?rnd=<acak>` | versi paket dasar (lokal=110) |
| A6 | `<updateServer>/resource.version?rnd=<acak>` | versi resource (lokal=11220) |
| A7 | `<updateServer>/upgrade.json?rnd=<acak>` | daftar upgrade |
| A8 | `<updateServer>/size.json?rnd=<acak>` | ukuran paket (key `base`, `all`) |
| A9 | `<updateServer>/base.zip?v=<versi>` | paket dasar (kode H5) |
| A10 | `<updateServer>/all.zip?v=<versi>` | paket resource lengkap |

#### 3.1.2 URL SDK pihak ketiga yang hardcode di classes.dex (bukan game — tidak perlu diedit)

Ini semua bagian dari SDK vendor yang di-bundle (AppsFlyer, Adjust, Facebook, Google, dll.), template `%s` diisi runtime oleh SDK:

| Kelompok | URL di classes.dex (1:1) |
|---|---|
| AppsFlyer (17) | `https://%sapp.%s`, `https://%sadrevenue.%s/api/v`, `https://%sattr.%s/api/v`, `https://%sconversions.%s/api/v`, `https://%sdlsdk.%s/v1.0/android/`, `https://%sgcdsdk.%s/install_data/v4.0/`, `https://%simpression.%s`, `https://%sinapps.%s/api/v`, `https://%slaunches.%s/api/v`, `https://%smonitorsdk.%s/remote-debug?app_id=`, `https://%sonelink.%s/shortlink-sdk/v1`, `https://%sregister.%s/api/v`, `https://%ssdk-services.%s/validate-android-signature`, `https://%sstats.%s/stats`, `https://%svalidate.%s/api/v`, `https://%s/%s`, `https://.facebook.com` |
| Adjust (9) | `https://app.adjust.com`, `https://app.adjust.net.in`, `https://app.adjust.world`, `https://gdpr.adjust.com`, `https://gdpr.adjust.net.in`, `https://gdpr.adjust.world`, `https://subscription.adjust.com`, `https://subscription.adjust.net.in`, `https://subscription.adjust.world` |
| Facebook (9+) | `https://www.facebook.com`, `https://facebook.com`, `https://facebook.com/device?user_code=%1$s&qr=1`, `https://graph.%s`, `https://graph-video.%s`, `https://fb.gg/me/community/`, `https://fb.gg/me/friendfinder/`, `https://fb.gg/me/media_asset/`, `https://developers.facebook.com/docs/android/getting-started`, `https://developers.facebook.com/docs/sharing/android`, `https://developers.facebook.com/docs/app-events/getting-started-app-events-android#disable-auto-events` |
| Google/Play (30) | `https://accounts.google.com`, `https://accounts.google.com/o/oauth2/revoke?token=`, `https://pagead2.googlesyndication.com/pagead/gen_204?id=gmob-apps`, `https://play.google.com/store/apps/details?id=`, `http://play.google.com/store/apps/details?id=com.facebook.orca`, `https://plus.google.com/`, + 24 scope `https://www.googleapis.com/auth/*`: `appstate`, `datastoremobile`, `drive`, `drive.appdata`, `drive.apps`, `drive.file`, `fitness.activity.read/write`, `fitness.blood_glucose.read/write`, `fitness.blood_pressure.read/write`, `fitness.body.read/write`, `fitness.body_temperature.read/write`, `fitness.location.read/write`, `fitness.nutrition.read/write`, `fitness.oxygen_saturation.read/write`, `fitness.reproductive_health.read/write`, `games`, `games.firstparty`, `games_lite`, `plus.login`, `plus.me` |
| Lain-lain (6) | `https://login.live.com`, `https://login.yahoo.com`, `https://twitter.com`, `https://web-pay.line.me`, `https://www.linkedin.com`, `https://www.paypal.com`, `https://www.example.com` |

#### 3.1.3 URL di libegret.so (noise, bukan konfigurasi)

`http://local/index.html` (fallback internal runtime Egret) · `http://www.ibm.com/data/dtd/v11/ibmxhtml1-transitional.dtd` · `https://curl.haxx.se/docs/http-cookies.html` · `https://www.openssl.org/support/faq.html` — dokumen/teks, tidak dipanggil sebagai endpoint game.

### 3.2 ENDPOINT YANG TIDAK ADA DI DALAM APK — sumbernya data RUNTIME perangkat

⚠️ Ini **BUKAN string di APK** (bukti: scan §3.1 nol kecocokan). Sumbernya: cache runtime Egret di perangkat (`data/data/…/files/games/`, 483 file cache berisi URL asli) + `shared_prefs/PREFERENCE_HAOCHENHUDONG.xml`. Dilist karena **local server harus meniru semuanya**, bukan karena bisa diedit di APK:

| # | Endpoint | Fungsi | Bukti (runtime) |
|---|---|---|---|
| C1 | `https://login.pksilo.com:18110` | Login + `getServerList` (socket.io) | cache `login.pksilo.com#0A18110` |
| C2 | `https://s<N>-zd.pksilo.com:<PORT>` — 99 host:port (tabel §3.3) | Server battle/game per-world (socket.io) | cache perangkat |
| C3 | `http://pigres.pksilo.com` | CDN resource Egret: `client/resource/…`, `client/properties/clientversion.json`, `activity_en/…` — di belakang Tencent EdgeOne | cache `files/games/http/` |
| C4 | `http://log.sjflaregame.com:10000/error/battle/client` | error-reporting client game | cache pigres |
| C5 | `http://www.parajoymobile.com/privacy_en.html` | halaman privacy (WebView) | cache resource |
| B1 | `https://log.sdkapi.xgogaming.com` | log host SDK | PREFERENCE_HAOCHENHUDONG.xml |
| B2 | `https://chat.sdkapi.xgogaming.com/chat/index?...group_id=1&ext={"gc":"lz"}…` | customer service in-app | idem |
| B3 | `https://cw.sdkapi.xgogaming.com` | widget Chatwoot (Cookies + Local Storage) | app_webview + pref |
| B4 | `https://policy.cdnres.xgogaming.com/terms.html` | halaman Terms | idem |
| B5 | `https://www.facebook.com/groups/1395815103915846/` | komunitas FB (link) | idem |

### 3.3 Tabel LENGKAP 100 server game (1:1, dari runtime perangkat — bukan dari APK)

| No | Host:Port | Fungsi |
|---|---|---|
| 1 | `login.pksilo.com`, port `18110` | **Server login** — socket.io, menjawab `getServerList` |
| 2 | `s1-zd.pksilo.com`, port `9000` | Server battle/game (socket.io) |
| 3 | `s149-zd.pksilo.com`, port `9400` | Server battle/game (socket.io) |
| 4 | `s181-zd.pksilo.com`, port `10900` | Server battle/game (socket.io) |
| 5 | `s187-zd.pksilo.com`, port `10000` | Server battle/game (socket.io) |
| 6 | `s191-zd.pksilo.com`, port `10500` | Server battle/game (socket.io) |
| 7 | `s195-zd.pksilo.com`, port `9500` | Server battle/game (socket.io) |
| 8 | `s201-zd.pksilo.com`, port `10600` | Server battle/game (socket.io) |
| 9 | `s206-zd.pksilo.com`, port `9200` | Server battle/game (socket.io) |
| 10 | `s211-zd.pksilo.com`, port `9700` | Server battle/game (socket.io) |
| 11 | `s216-zd.pksilo.com`, port `10200` | Server battle/game (socket.io) |
| 12 | `s221-zd.pksilo.com`, port `10700` | Server battle/game (socket.io) |
| 13 | `s226-zd.pksilo.com`, port `11100` | Server battle/game (socket.io) |
| 14 | `s231-zd.pksilo.com`, port `11300` | Server battle/game (socket.io) |
| 15 | `s236-zd.pksilo.com`, port `11600` | Server battle/game (socket.io) |
| 16 | `s241-zd.pksilo.com`, port `11800` | Server battle/game (socket.io) |
| 17 | `s246-zd.pksilo.com`, port `9100` | Server battle/game (socket.io) |
| 18 | `s251-zd.pksilo.com`, port `9300` | Server battle/game (socket.io) |
| 19 | `s256-zd.pksilo.com`, port `9600` | Server battle/game (socket.io) |
| 20 | `s261-zd.pksilo.com`, port `9800` | Server battle/game (socket.io) |
| 21 | `s266-zd.pksilo.com`, port `10100` | Server battle/game (socket.io) |
| 22 | `s271-zd.pksilo.com`, port `10300` | Server battle/game (socket.io) |
| 23 | `s276-zd.pksilo.com`, port `10600` | Server battle/game (socket.io) |
| 24 | `s281-zd.pksilo.com`, port `10800` | Server battle/game (socket.io) |
| 25 | `s286-zd.pksilo.com`, port `11100` | Server battle/game (socket.io) |
| 26 | `s291-zd.pksilo.com`, port `11300` | Server battle/game (socket.io) |
| 27 | `s296-zd.pksilo.com`, port `11500` | Server battle/game (socket.io) |
| 28 | `s301-zd.pksilo.com`, port `10100` | Server battle/game (socket.io) |
| 29 | `s306-zd.pksilo.com`, port `11800` | Server battle/game (socket.io) |
| 30 | `s311-zd.pksilo.com`, port `9100` | Server battle/game (socket.io) |
| 31 | `s316-zd.pksilo.com`, port `9800` | Server battle/game (socket.io) |
| 32 | `s321-zd.pksilo.com`, port `10100` | Server battle/game (socket.io) |
| 33 | `s326-zd.pksilo.com`, port `9000` | Server battle/game (socket.io) |
| 34 | `s331-zd.pksilo.com`, port `9100` | Server battle/game (socket.io) |
| 35 | `s336-zd.pksilo.com`, port `10800` | Server battle/game (socket.io) |
| 36 | `s341-zd.pksilo.com`, port `9500` | Server battle/game (socket.io) |
| 37 | `s346-zd.pksilo.com`, port `9600` | Server battle/game (socket.io) |
| 38 | `s351-zd.pksilo.com`, port `9200` | Server battle/game (socket.io) |
| 39 | `s356-zd.pksilo.com`, port `9400` | Server battle/game (socket.io) |
| 40 | `s361-zd.pksilo.com`, port `10200` | Server battle/game (socket.io) |
| 41 | `s366-zd.pksilo.com`, port `10200` | Server battle/game (socket.io) |
| 42 | `s371-zd.pksilo.com`, port `9900` | Server battle/game (socket.io) |
| 43 | `s376-zd.pksilo.com`, port `10300` | Server battle/game (socket.io) |
| 44 | `s381-zd.pksilo.com`, port `10800` | Server battle/game (socket.io) |
| 45 | `s382-zd.pksilo.com`, port `10700` | Server battle/game (socket.io) |
| 46 | `s383-zd.pksilo.com`, port `10400` | Server battle/game (socket.io) |
| 47 | `s384-zd.pksilo.com`, port `10500` | Server battle/game (socket.io) |
| 48 | `s385-zd.pksilo.com`, port `10900` | Server battle/game (socket.io) |
| 49 | `s386-zd.pksilo.com`, port `10900` | Server battle/game (socket.io) |
| 50 | `s387-zd.pksilo.com`, port `11000` | Server battle/game (socket.io) |
| 51 | `s388-zd.pksilo.com`, port `11000` | Server battle/game (socket.io) |
| 52 | `s389-zd.pksilo.com`, port `11000` | Server battle/game (socket.io) |
| 53 | `s390-zd.pksilo.com`, port `11000` | Server battle/game (socket.io) |
| 54 | `s391-zd.pksilo.com`, port `11200` | Server battle/game (socket.io) |
| 55 | `s392-zd.pksilo.com`, port `11100` | Server battle/game (socket.io) |
| 56 | `s393-zd.pksilo.com`, port `11200` | Server battle/game (socket.io) |
| 57 | `s394-zd.pksilo.com`, port `11100` | Server battle/game (socket.io) |
| 58 | `s395-zd.pksilo.com`, port `11300` | Server battle/game (socket.io) |
| 59 | `s396-zd.pksilo.com`, port `11200` | Server battle/game (socket.io) |
| 60 | `s397-zd.pksilo.com`, port `9200` | Server battle/game (socket.io) |
| 61 | `s398-zd.pksilo.com`, port `9300` | Server battle/game (socket.io) |
| 62 | `s399-zd.pksilo.com`, port `9400` | Server battle/game (socket.io) |
| 63 | `s400-zd.pksilo.com`, port `9000` | Server battle/game (socket.io) |
| 64 | `s401-zd.pksilo.com`, port `9000` | Server battle/game (socket.io) |
| 65 | `s402-zd.pksilo.com`, port `9500` | Server battle/game (socket.io) |
| 66 | `s403-zd.pksilo.com`, port `9200` | Server battle/game (socket.io) |
| 67 | `s404-zd.pksilo.com`, port `9300` | Server battle/game (socket.io) |
| 68 | `s405-zd.pksilo.com`, port `9700` | Server battle/game (socket.io) |
| 69 | `s406-zd.pksilo.com`, port `9400` | Server battle/game (socket.io) |
| 70 | `s407-zd.pksilo.com`, port `9100` | Server battle/game (socket.io) |
| 71 | `s408-zd.pksilo.com`, port `9500` | Server battle/game (socket.io) |
| 72 | `s409-zd.pksilo.com`, port `9800` | Server battle/game (socket.io) |
| 73 | `s410-zd.pksilo.com`, port `9700` | Server battle/game (socket.io) |
| 74 | `s411-zd.pksilo.com`, port `9300` | Server battle/game (socket.io) |
| 75 | `s412-zd.pksilo.com`, port `9600` | Server battle/game (socket.io) |
| 76 | `s413-zd.pksilo.com`, port `9900` | Server battle/game (socket.io) |
| 77 | `s414-zd.pksilo.com`, port `9900` | Server battle/game (socket.io) |
| 78 | `s415-zd.pksilo.com`, port `9600` | Server battle/game (socket.io) |
| 79 | `s416-zd.pksilo.com`, port `9900` | Server battle/game (socket.io) |
| 80 | `s417-zd.pksilo.com`, port `10000` | Server battle/game (socket.io) |
| 81 | `s418-zd.pksilo.com`, port `10000` | Server battle/game (socket.io) |
| 82 | `s419-zd.pksilo.com`, port `9700` | Server battle/game (socket.io) |
| 83 | `s420-zd.pksilo.com`, port `10000` | Server battle/game (socket.io) |
| 84 | `s421-zd.pksilo.com`, port `10300` | Server battle/game (socket.io) |
| 85 | `s422-zd.pksilo.com`, port `10100` | Server battle/game (socket.io) |
| 86 | `s423-zd.pksilo.com`, port `9800` | Server battle/game (socket.io) |
| 87 | `s424-zd.pksilo.com`, port `10400` | Server battle/game (socket.io) |
| 88 | `s425-zd.pksilo.com`, port `10400` | Server battle/game (socket.io) |
| 89 | `s426-zd.pksilo.com`, port `10400` | Server battle/game (socket.io) |
| 90 | `s427-zd.pksilo.com`, port `10200` | Server battle/game (socket.io) |
| 91 | `s428-zd.pksilo.com`, port `10600` | Server battle/game (socket.io) |
| 92 | `s429-zd.pksilo.com`, port `10500` | Server battle/game (socket.io) |
| 93 | `s430-zd.pksilo.com`, port `10500` | Server battle/game (socket.io) |
| 94 | `s431-zd.pksilo.com`, port `10300` | Server battle/game (socket.io) |
| 95 | `s432-zd.pksilo.com`, port `10800` | Server battle/game (socket.io) |
| 96 | `s433-zd.pksilo.com`, port `10700` | Server battle/game (socket.io) |
| 97 | `s434-zd.pksilo.com`, port `10600` | Server battle/game (socket.io) |
| 98 | `s435-zd.pksilo.com`, port `10700` | Server battle/game (socket.io) |
| 99 | `s436-zd.pksilo.com`, port `10900` | Server battle/game (socket.io) |
| 100 | `s49931-zd.pksilo.com`, port `10507` | Server event khusus |

Sumber: `login.pksilo.com#0A18110` (respons `getServerList`) + struktur cache socket.io. Pola: `s<N>-zd.pksilo.com`, port 9000–11800, plus server event khusus `s49931-zd:10507`. Respons login hanya memuat server tertentu (mis. s436 aktif `new:true online:true`) — sisanya kumpulan historis yang tertangkap di cache.

### 3.4 Schema respons (kontrak untuk local server)

```
getServerList → <panjang>:430[{"ret":0,"data":"{\"serverList\":[{\"serverId\":\"436\",
\"new\":true,\"online\":true,\"hot\":false,\"url\":\"https://s436-zd.pksilo.com:10900\",
\"name\":\"Server 436\",\"offlineReason\":\"Sever offline,  please wait....\"}, …]}"}]
```
JSON di dalam JSON (field `data` berupa string). `clientversion.json` → `{"clientVersion":"20260929_180158-EN"}`.

---

## 4. BAGIAN 3 — Rencana edit endpoint ke LOCAL SERVER

### 4.1 Mapping redirect

| Endpoint | Kategori | Cara redirect |
|---|---|---|
| APK1–APK3 (`dragon.sjmobilegame.com:82`, `configus.sjmobilegame.com`, `configus-…myqcloud.com`) | **DI DALAM APK (Base64)** | Edit Base64 di 2 file assets → tunjuk local server (§4.2) |
| APK4 (`http://qsdk.t4game.com`) | **DI DALAM APK (plaintext)** | Edit `assets/QHinfo` → local server (jika SDK akun juga di-mock) |
| A5–A10 (`base.version`, `resource.version`, `upgrade.json`, `size.json`, `base.zip`, `all.zip`) | turunan config native_setting | Ikut masuk — local server yang menjawab native_setting menentukan URL update ini |
| C1 `login.pksilo.com:18110` | runtime (JS terenkripsi) | Opsi DNS/hosts+TLS (§4.3) ATAU dari `gameUrl`/config yang dijawab local server, ATAU dump JS (Fase 5) |
| C2 99 server battle | runtime (JS terenkripsi) | Lewat respons `getServerList` yang dijawab local login server → field `url` menunjuk local battle server |
| C3 `pigres.pksilo.com` | runtime (JS terenkripsi) | DNS/hosts + reverse-proxy (HTTP polos — mudah) |
| C4 `log.sjflaregame.com:10000` | runtime (JS terenkripsi) | DNS/hosts (HTTP polos) |
| SDK vendor dex (§3.1.2) | di dalam APK | Tidak perlu diedit (biarkan; traffic analytics tidak mengganggu game) |

### 4.2 Edit 1 — File native di APK (DAPAT diedit langsung, mudah)

Tiga file ini plaintext base64 — decode → ubah → encode ulang:

```bash
# 1. assets/config.properties
#    EntryPoint1  = BASE64("http://192.168.1.100:8080/lzceshi/native_setting/Android")
#    EntryPoint1_debug = sama
# 2. assets/config_BS.properties
#    EntryPoint1/_debug = BASE64("http://192.168.1.100:8080/bs/db/android")
#    EntryPoint2/_debug = BASE64("http://192.168.1.100:8080/bs/db/android")
# 3. assets/QHinfo  (plaintext, bukan base64)
#    http://192.168.1.100:8080/qsdk

# Helper generate base64:
echo -n "http://192.168.1.100:8080/bs/db/android" | base64
```

Endpoint **APK1–APK4 + A5–A10 selesai dengan 3 edit file ini** (A5–A10 otomatis mengikuti karena URL-nya diambil dari respons `native_setting`/konfigurasi yang sama — local server tinggal menyediakan `base.version`, `resource.version`, `upgrade.json`, `size.json`, `base.zip`, `all.zip`).

### 4.3 Edit 2 — Endpoint game pksilo (SULIT — tidak ada di APK, hidup di JS terenkripsi)

Endpoint C1–C3 **tidak ada dalam teks polos di APK** (diverifikasi scan §3.1): dikompilasi di dalam JS Egret yang dienkripsi `libgavesec.so`, di-decrypt ke memori saat runtime. Opsi (urut prioritas):

- **Opsi 1 — DNS/hosts + TLS (tanpa menyentuh JS):** arahkan domain ke server lokal:
  - `pigres.pksilo.com` & `log.sjflaregame.com` = **HTTP polos** → cukup redirect DNS/hosts (root/emulator: edit `/etc/hosts`; tanpa root: app VPN DNS seperti Virtual Hosts) → reverse-proxy lokal melayani resource.
  - `login.pksilo.com` & `sNNN-zd.pksilo.com` = **HTTPS** → butuh sertifikat: repack APK dengan `network_security_config.xml` yang trust user CA + install CA lokal, ATAU install CA sebagai system cert di emulator rooted. Local server meniru sertifikat domain tsb (SAN lengkap).
  - Mock server hanya perlu menjawab: `clientversion.json`, pohon resource, `manifest.json`, `index-native.html`, socket.io handshake + frame `getServerList` (schema §3.4), dan endpoint error-log.
- **Opsi 2 — Dekripsi JS (kontrol penuh, fase lanjutan):** dump JS hasil decrypt dari memori (Frida pada emulator: hook `libgavesec`/WebView `loadUrl`/file cache plaintext), lalu cari-ganti string `pksilo.com` langsung di sumber JS, re-encrypt ATAU patch loader agar memuat JS plaintext. Ini membuka editing semua logika server.
- **Opsi 3 — Proxy mitm transparan** (mitmproxy + CA) untuk debugging protokol socket.io sebelum membangun mock.

### 4.4 Edit 3 — Build ulang APK

```bash
# ganti 3 file assets di atas, lalu re-zip (simpan META-INF lama = signature hilang)
zip -r mod.apk AndroidManifest.xml assets/ res/ resources.arsc classes.dex lib/ …
zipalign -p -f 4 mod.apk mod-aligned.apk
apksigner sign --ks my.keystore --out db-local-mod.apk mod-aligned.apk
```

Risiko tercatat: `libgavesec` bisa memverifikasi integritas APK (signature/class hash) — jika setelah repack app force-close saat dekripsi, berarti integrity check aktif → perlu di-bypass (fase Opsi 2). Belum terbukti terjadi; uji dulu.

### 4.5 Spesifikasi minimum local server (untuk fase berikutnya)

1. **Update server (port 80):** `native_setting/Android` (JSON config: channel, gameUrl, nativeUnzipJson, dsb.) + `base.version` → `110` (atau lebih baru), `resource.version` → `11220`, `upgrade.json`, `size.json` (`{"base":…,"all":…}`), `base.zip`, `all.zip`.
2. **Resource server (port 80):** serve `client/properties/clientversion.json` → `{"clientVersion":"<baru>-EN"}` + seluruh pohon `client/resource/…` + `activity_en/…` + `manifest.json` + `index-native.html`; header `Access-Control-Allow-Origin: *`, `Cache-Control: max-age=2592000` (meniru EdgeOne).
3. **Login server (port 18110, TLS):** engine.io/socket.io handshake (cookie `io=`), balas frame `430[{"ret":0,"data":"{\"serverList\":[…] json-string}"}]` dengan 1 server lokal: `{"serverId":"1","new":true,"online":true,"hot":true,"url":"https://192.168.1.100:9000","name":"Local 1","offlineReason":""}`.
4. **Battle server (port 9000, TLS):** socket.io gameplay (protokol frame diteliti di fase 5 dari dump JS).
5. **Error log (port 10000, HTTP):** `POST /error/battle/client` → 200 OK.

---

## 5. Roadmap proyek (panjang)

- [x] **Fase 1** — Bedah APK + inventaris endpoint (APK vs runtime) + struktur Android/data (laporan ini, revisi 3) ✅
- [ ] **Fase 2** — Edit 3 file native (§4.2) + repack + uji install (apakah integrity check gavesec aktif)
- [ ] **Fase 3** — Mock update+resource server (port 80) + redirect pigres → game bisa boot sampai layar login
- [ ] **Fase 4** — Mock login server socket.io (18110) dengan schema serverList
- [ ] **Fase 5** — Dump JS Egret terdekripsi (Frida) → patch endpoint pksilo langsung di JS + pahami protokol battle frame
- [ ] **Fase 6** — Mock battle server + ekosistem local (akun/SDK opsional)

---

## 6. Metode & bukti (revisi 3)

- Clone repo → unzip `db-local-original.apk` → parse manifest (androguard/pyaxmlparser) → **scan byte-per-byte seluruh isi APK** → 85 URL unik di 4 file (`classes.dex`, 2× `libegret.so`, `assets/QHinfo`) + decode manual 6 baris Base64 di 2 file config → **nol string pksilo/xgogaming/sjflaregame/sjmobilegame plaintext di APK**.
- **Koreksi struktur:** dump perangkat `log/extracted/com.Vanguard.twnthe` terbukti adalah **`/data/data` (internal)** — bukti: ada `shared_prefs/`, `databases/`, `code_cache/`, `no_backup/`, `app_webview/`, `app_textures/` (= hasil `Context.getDir()`, prefix `app_`). Folder `Android/data` eksternal tidak ikut dalam dump, jadi strukturnya direkonstruksi dari **kode APK**: `LaunchActivity` → `getExternalFilesDir("")+"/game/"`, DecodeUtil (dekripsi `.bin` → unzip ke situ, pref `decode_bin/decodeBinFinish`), update `base.zip/all.zip/tmp.zip`, dan `EgretNativeAndroid.cachePath = getFilesDir()` (cache Egret = internal).
- Endpoint game 1:1 dari cache header Egret di perangkat: **100 host:port** (tabel §3.3) + `getServerList` tertangkap utuh (schema JSON-in-JSON) — dikategorikan RUNTIME, bukan APK.
- Status server langsung dicek: `configus` (COS NoSuchKey), `dragon:82` (301 mati), `qsdk.t4game.com` (200 hidup).
- Semua string di `assets/game/` dicek: kode JS terenkripsi (file `.bin`); resource gambar/audio plaintext — tidak ada endpoint game plaintext.
- Catatan keamanan: laporan ini sengaja TIDAK memuat token/credential akun yang ditemukan di data perangkat.
