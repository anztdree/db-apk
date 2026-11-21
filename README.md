# 📋 LAPORAN ANALISIS APK db-local (Original)

> Laporan tunggal hasil bedah penuh APK asli + data runtime asli dari perangkat.
> Sumber: `db-local-original.apk` (104.945.181 bytes) + hasil ekstrak data perangkat asli di `log/extracted/`.
> Tanggal analisis: 2026-11-21 · Status proyek: **FASE 1 (analisis) SELESAI**

---

## 1. Identitas APK

| Item | Nilai | Bukti |
|---|---|---|
| Nama aplikasi (repack) | **DB - Local** | resources.arsc |
| Package (repack, di repo ini) | **`com.db.local`** | AndroidManifest.xml |
| Package game asli di perangkat | **`com.Vanguard.twnthe`** | folder data perangkat |
| Versi | **1.0.1** (versionCode 1) | AndroidManifest.xml |
| minSdk / targetSdk | 21 / 30 | AndroidManifest.xml |
| Launcher Activity | `com.zhuhuan.game.LaunchActivity` | AndroidManifest.xml |
| Game Activity utama | `com.zhuhuan.game.MainActivity` | AndroidManifest.xml |
| Engine game | **Egret Engine** (HTML5/TS, dijalankan WebView native) | `lib/arm64-v8a/libegret.so` |
| Proteksi/proteksi resource | **libgavesec.so** — semua JS/konfig game di `assets/game/` terenkripsi | `lib/*/libgavesec.so`, isi `assets/game/*` tidak ada teks polos |
| Hot-load DEX | `assets/runtime-dex.jar` (berisi classes.dex 183.948 bytes) | unzip assets |
| SDK akun | **QuickGame SDK** (`com.quickgame.android.sdk.*`, 21 Activity) + **HaoChenHudong / xgogaming** (backend akun) | manifest + `shared_prefs/PREFERENCE_HAOCHENHUDONG.xml` |
| Nama game internal | **The Wild Adventure** (pref key `TheWildAdventure…`) | `shared_prefs/pigres.pksilo.com.xml` |
| Jumlah DEX | 1 (classes.dex 6.697.104 bytes) | unzip APK |
| ABI | arm64-v8a, armeabi-v7a | folder `lib/` |

**12 permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `WAKE_LOCK`, `REORDER_TASKS`, `SYSTEM_ALERT_WINDOW`, `SYSTEM_OVERLAY_WINDOW`, `com.android.vending.BILLING`, `BIND_GET_INSTALL_REFERRER_SERVICE` (custom per package).

**Komponen manifest:** 21 Activity (semuanya SDK: QuickGame `HW*`/`AccountRecover`/`BindEmail`/`RedeemCode`/`OnestorePay`, Facebook 4, Google Sign-In 2) · 3 Service (`QGConnectionService`, `QuickGameSdkService`, `RevocationBoundService`) · 1 Receiver (`CurrentAccessTokenExpirationBroadcastReceiver`).

---

## 2. BAGIAN 1 — Folder & file yang dibuat di `android/data` setelah install

### 2.1 Keterangan penting

- Folder yang dibuat = **`android/data/<PACKAGE>/`** → untuk APK di repo ini: **`android/data/com.db.local/`** (di perangkat asli game berjalan sebagai `com.Vanguard.twnthe`, jadi foldernya `android/data/com.Vanguard.twnthe/` — strukturnya identik 1:1).
- Folder **tidak dibuat saat install**, melainkan saat **pertama kali aplikasi dijalankan** (first launch). Setelah first run, struktur di bawah langsung ada.
- Data acuan di bawah adalah **hasil ekstrak nyata** dari perangkat (bukan tebakan): total **4.816 folder, 16.348 file, 178.011.704 bytes (~170 MB)** setelah bermain.

### 2.2 Struktur lengkap (1:1 dari perangkat)

```
android/data/com.db.local/          ← (perangkat asli: com.Vanguard.twnthe/)
├── app_textures/                       (kosong; dibuat Egret/WebView, reserved)
├── app_webview/                        36 file · 123.605 B — data Chromium WebView
│   ├── variations_stamp, variations_seed_new, webview_data.lock, last-exit-info
│   └── Default/
│       ├── Cookies, Cookies-journal, "Network Persistent State",
│       │   "Persistent Origin Trials"/{LOG,LOCK},
│       │   "Local Storage"/leveldb/{LOG,CURRENT,LOCK,MANIFEST-000001,000003.log …}
│       │   ← berisi cache Chatwoot (cw.sdkapi.xgogaming.com)
│       ├── "Session Storage"/{LOG,CURRENT,LOCK,MANIFEST-000001,000003.log}
│       ├── "Shared Dictionary"/cache/index-dir/
│       ├── blob_storage/<GUID>/         ← GUID acak per install
│       ├── shared_proto_db/{LOG,LOCK,CURRENT,MANIFEST-000001,000003.log}
│       │   └── metadata/LOG
│       └── PersistentOriginTrials/
├── cache/                              32 file · 1.705.931 B
│   ├── "Crash Reports"/"ANR Variations"/
│   ├── WebView/{Crash Reports, SafeBrowsing}/
│   ├── WebView/Crashpad/{attachments,completed,new,pending}/
│   ├── WebView/Default/"HTTP Cache"/{Cache_Data/index-dir, "Code Cache"/js/index-dir,
│   │   "Code Cache"/wasm/index-dir, No_Vary_Search}/
│   ├── image_manager_disk_cache/
│   ├── instrument/
│   └── oat_primary/arm64/base.art
├── code_cache/                         (kosong)
├── databases/                          6 file · 98.304 B
│   ├── com.google.android.datatransport.events (+ -journal)
│   ├── evenLog1.db (+ -journal)
│   └── google_app_measurement_local.db (+ -journal)
├── files/                              16.254 file · 171.840.892 B  ← 96% dari total
│   ├── AppEventsLogger.persistedevents     (event queue Facebook)
│   ├── GameDataCache
│   ├── profileInstalled
│   ├── profileinstaller_profileWrittenFor_lastUpdateTime.dat
│   ├── puIdMessage.txt                     (hex ID perangkat, 40 hex)
│   ├── runtime-dex.jar                     (salinan dex hot-load SDK)
│   ├── userMessage.txt                     (payload terenkripsi)
│   ├── AFRequestCache/{AttrCache, ConversionsCache, OtherCache}/   (cache AppsFlyer)
│   ├── oat/arm64/                          (profile dexopt)
│   ├── phenotype/{shared}, phenotype_storage_info/{shared}         (Firebase Phenotype)
│   └── games/                              ← CACHE GAME EGRET (inti semua kegiatan)
│       ├── http/   9.688 file  · mirror resource server pigres.pksilo.com:
│       │   └── pigres.pksilo.com/
│       │       ├── activity_en/{主题卡活动, 假日活动, 新服活动, 新用户活动, 强者之路,
│       │       │              抢占先机, 每周累充, 未分类}/   (banner event, 236+ file)
│       │       └── client/
│       │           ├── manifest.json#, index-native.html#,
│       │           ├── properties/clientversion.json#…
│       │           └── resource/…    4.676 subfolder — pohon resource Egret lengkap
│       │               (assets/, assets_ts/, sprite `_ske.json`/`_tex.json`/`_tex.png`, dll.)
│       └── https/  6.551 file · cache socket.io game server:
│           ├── login.pksilo.com#0A18110/socket.io/#index#/<md5>(+#header)
│           ├── s422-zd.pksilo.com#0A10100/socket.io/…
│           ├── s431-zd.pksilo.com#0A10300/socket.io/…
│           ├── s432-zd.pksilo.com#0A10800/socket.io/…
│           └── s49931-zd.pksilo.com#0A10507/socket.io/…
│               (format nama cache: <host>#0A<PORT>, tiap respons disimpan
│                <md5> + <md5>#header)
├── no_backup/.webview/                     1 file · 4.194.304 B (library WebView)
├── oat/arm64/                              4 file · 720 B (art profil compiled)
└── shared_prefs/                           15 file · 47.948 B
    ├── AwOriginVisitLoggerPrefs.xml
    ├── FirebaseHeartBeatW0RFRkFVTFRd+….xml
    ├── PREFERENCE_HAOCHENHUDONG.xml        ← CONFIG BACKEND AKUN (INIT, ACCOUNT,
    │                                          TOKEN, Puid — lihat Bagian 3.2)
    ├── WebViewChromiumPrefs.xml
    ├── appsflyer-data.xml
    ├── com.Vanguard.twnthe_preferences.xml  (→ di repack: com.db.local_preferences.xml)
    ├── com.facebook.internal.MODEL_STORE.xml
    ├── com.facebook.internal.preferences.APP_GATEKEEPERS.xml
    ├── com.facebook.internal.preferences.APP_SETTINGS.xml
    ├── com.facebook.sdk.USER_SETTINGS.xml
    ├── com.facebook.sdk.appEventPreferences.xml
    ├── com.facebook.sdk.attributionTracking.xml
    ├── com.google.android.gms.measurement.prefs.xml
    ├── decode_bin.xml                      (tanda "decodeBinFinish" — dekripsi game selesai)
    └── pigres.pksilo.com.xml               30 KB · 306 key — save data Egret/localStorage
                                               game (2 akun: GUID 75aeb99f… dan 31eef082…)
```

### 2.3 Yang dibuat saat first-run (sebelum login)

`app_webview/`, `cache/`, `code_cache/`, `files/{AFRequestCache, oat, phenotype, phenotype_storage_info}`, `no_backup/`, `oat/`, `shared_prefs/{decode_bin.xml, WebViewChromiumPrefs.xml, …}` dibuat langsung. Folder `files/games/`, `databases/`, dan `shared_prefs` lainnya bertambah **saat login & bermain** (cache resource + socket.io).

---

## 3. BAGIAN 2 — Inventaris endpoint (1:1, dengan bukti)

### 3.1 Layer A — Endpoint di dalam APK (file assets, base64)

Semua endpoint native disimpan sebagai **Base64** dan baru di-decode saat runtime.

| # | File | Key | Nilai ter-decode | Status server (dicek 2026-11-21) |
|---|---|---|---|---|
| A1 | `assets/config.properties` | `EntryPoint1` / `EntryPoint1_debug` | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | ❌ 301 → dir listing kosong (mati) |
| A2 | `assets/config_BS.properties` | `EntryPoint1` / `EntryPoint1_debug` | `https://configus.sjmobilegame.com/bs/db/android` | ❌ 404 `NoSuchKey` (Tencent COS) |
| A3 | `assets/config_BS.properties` | `EntryPoint2` / `EntryPoint2_debug` | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` | ❌ (fallback COS) |
| A4 | `assets/QHinfo` | (isi file) | `http://qsdk.t4game.com` | ✅ 200 — API base QuickGame SDK |

*Catatan: `lzceshi` = "测式/测试" (env testing); `bs` = bootstrap; file `config_BS.properties` adalah varian channel produksi.*

### 3.2 Layer B — Backend SDK akun (xgogaming / HaoChenHudong)

Ditemukan di `shared_prefs/PREFERENCE_HAOCHENHUDONG.xml` → field `INIT` (konfigurasi yang di-download SDK saat init; `gc":"lz"` = channel lz):

| # | Endpoint | Fungsi |
|---|---|---|
| B1 | `https://log.sdkapi.xgogaming.com` | log host SDK |
| B2 | `https://chat.sdkapi.xgogaming.com/chat/index?...group_id=1&ext={"gc":"lz"}...` | customer service (chat in-app) |
| B3 | `https://cw.sdkapi.xgogaming.com` | widget Chatwoot (di WebView, ada Cookies + Local Storage) |
| B4 | `https://policy.cdnres.xgogaming.com/terms.html` | halaman Terms of Service |
| B5 | `https://www.facebook.com/groups/1395815103915846/` | komunitas FB (link saja) |

### 3.3 Layer C — Endpoint game inti (pksilo.com) — HARDCODE di JS Egret terenkripsi

Bukti berasal dari cache runtime perangkat (`files/games/…`, 483 file cache berisi URL asli). Semua koneksi game memakai **socket.io** (cookie `io=…`, Content-Encoding deflate).

| # | Endpoint | Fungsi | Bukti |
|---|---|---|---|
| C1 | `https://login.pksilo.com:18110` | Login + `getServerList` (socket.io) | cache `login.pksilo.com#0A18110` |
| C2 | `https://sNNN-zd.pksilo.com:PORT` | Server battle/game per-world (socket.io) | **98 host:port terdeteksi** (tabel 3.4) |
| C3 | `http://pigres.pksilo.com` | CDN resource Egret: `client/resource/…`, `client/properties/clientversion.json`, `activity_en/…` — di belakang Tencent EdgeOne (header `EO-*`) | cache `files/games/http/` |
| C4 | `http://log.sjflaregame.com:10000/error/battle/client` | error-reporting client game | cache pigres `client/resource` |
| C5 | `http://www.parajoymobile.com/privacy_en.html` | halaman privacy (WebView) | cache resource |

**Format respons `getServerList` (tertangkap utuh dari cache login):**

```
<panjang>:430[{"ret":0,"data":"{\"serverList\":[{\"serverId\":\"436\",\"new\":true,
\"online\":true,\"hot\":false,\"url\":\"https://s436-zd.pksilo.com:10900\",
\"name\":\"Server 436\",\"offlineReason\":\"Sever offline,  please wait....\"}, …]}"}]
```

→ JSON di dalam JSON (field `data` berupa string). Ini **kontrak wajib** yang harus ditiru local server.
**Format file versi resource:** `clientversion.json` = `{"clientVersion":"20260929_180158-EN"}`.

### 3.4 Tabel lengkap 98 server game terdeteksi (host:port)

`login.pksilo.com:18110` + `s1-zd:9000, s149-zd:9400, s181-zd:10900, s187-zd:10000, s191-zd:10500, s195-zd:9500, s201-zd:10600, s206-zd:9200, s211-zd:9700, s216-zd:10200, s221-zd:10700, s226-zd:11100, s231-zd:11300, s236-zd:11600, s241-zd:11800, s246-zd:9100, s251-zd:9300, s256-zd:9600, s261-zd:9800, s266-zd:10100, s271-zd:10300, s276-zd:10600, s281-zd:10800, s286-zd:11100, s291-zd:11300, s296-zd:11500, s301-zd:10100, s306-zd:11800, s311-zd:9100, s316-zd:9800, s321-zd:10100, s326-zd:9000, s331-zd:9100, s336-zd:10800, s341-zd:9500, s346-zd:9600, s351-zd:9200, s356-zd:9400, s361-zd:10200, s366-zd:10200, s371-zd:9900, s376-zd:10300, s381-zd:10800, s382-zd:10700, s383-zd:10400, s384-zd:10500, s385-zd:10900, s386-zd:10900, … s422-zd:10100, s423-zd:9800, s424-zd:10400, s425-zd:10400, s426-zd:10400, s427-zd:10200, s428-zd:10600, s429-zd:10500, s430-zd:10500, s431-zd:10300, s432-zd:10800, s433-zd:10700, s434-zd:10600, s435-zd:10700, s436-zd:10900, s49931-zd:10507`

(daftar penuh tersimpan pada analisis internal; pola = `s<N>-zd.pksilo.com` dengan port 9000–11800, + server event khusus `s49931`.)

### 3.5 Layer D — Endpoint SDK pihak ketiga di classes.dex (tidak wajib diedit)

| SDK | Host |
|---|---|
| AppsFlyer | pola `%s` runtime: `app./attr./conversions./inapps./launches./register./sdk-services./stats./validate./onelink./gcdsdk./dlsdk./monitorsdk./impression./adrevenue.` + domain vendor |
| Adjust | `app.adjust.com/.net.in/.world`, `gdpr.*`, `subscription.*` |
| Facebook | `graph.facebook.com`, `graph-video.facebook.com`, `facebook.com`, `fb.gg` |
| Google | `accounts.google.com`, `www.googleapis.com/*`, `play.google.com`, `pagead2.googlesyndication.com` |

---

## 4. BAGIAN 3 — Rencana edit endpoint ke LOCAL SERVER

### 4.1 Mapping redirect (target)

| Endpoint asli | Redirect ke (contoh; sesuaikan IP/PORT lokal) |
|---|---|
| `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | `http://192.168.1.100:8080/lzceshi/native_setting/Android` |
| `https://configus.sjmobilegame.com/bs/db/android` | `http://192.168.1.100:8080/bs/db/android` |
| `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` | `http://192.168.1.100:8080/bs/db/android` |
| `http://qsdk.t4game.com` | `http://192.168.1.100:8080/qsdk` *(opsional — hanya jika SDK akun juga di-mock)* |
| `https://login.pksilo.com:18110` | `wss/ws atau https://192.168.1.100:18110` *(via JS — lihat 4.3)* |
| `https://sNNN-zd.pksilo.com:PORT` | `https://192.168.1.100:PORT` *(via JS atau serverList yang dijawab local server)* |
| `http://pigres.pksilo.com` | `http://192.168.1.100:8081` *(via JS atau DNS)* |
| `http://log.sjflaregame.com:10000/error/battle/client` | `http://192.168.1.100:10000/error/battle/client` |

### 4.2 Edit 1 — File native di APK (DAPAT diedit langsung, mudah)

Ketiga file ini plaintext base64 — decode → ubah → encode ulang:

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

Endpoint **A1–A4 selesai dengan 3 edit file ini**.

### 4.3 Edit 2 — Endpoint game pksilo (SULIT — hardcode di JS terenkripsi)

Endpoint C1–C3 **tidak ada dalam teks polos di APK** (sudah diverifikasi scan penuh 16.254 file + dex): semuanya dikompilasi di dalam JS Egret yang dienkripsi `libgavesec.so`, lalu di-decrypt ke memori saat runtime. Opsi (urut prioritas):

- **Opsi 1 — DNS/hosts + TLS (tanpa menyentuh JS):** arahkan domain ke server lokal:
  - `pigres.pksilo.com` & `log.sjflaregame.com` = **HTTP polos** → cukup redirect DNS/hosts (root/emulator: edit `/etc/hosts`; tanpa root: app VPN DNS seperti Virtual Hosts) → reverse-proxy lokal melayani resource.
  - `login.pksilo.com` & `sNNN-zd.pksilo.com` = **HTTPS** → butuh sertifikat: repack APK dengan `network_security_config.xml` yang trust user CA + install CA lokal, ATAU install CA sebagai system cert di emulator rooted. Local server meniru sertifikat domain tsb (SAN lengkap).
  - Mock server hanya perlu menjawab: `clientversion.json`, pohon resource, `manifest.json`, `index-native.html`, socket.io handshake + frame `getServerList` (schema §3.3), dan endpoint error-log.
- **Opsi 2 — Dekripsi JS (kontrol penuh, fase lanjutan):** dump JS hasil decrypt dari memori (Frida pada emulator: hook `libgavesec`/WebView `loadUrl`/file cache plaintext), lalu cari-ganti string `pksilo.com` langsung di sumber JS, re-encrypt ATAU patch loader agar memuat JS plaintext. Ini membuka editing semua logika server.
- **Opsi 3 — Proxy mitm transparan** (mitmproxy + CA) untuk debugging protokol socket.io sebelum membangun mock.

### 4.4 Edit 3 — Build ulang APK

```bash
# ganti 3 file assets di atas, lalu re-zip (jangan lupa simpan META-INF lama = signature hilang)
zip -r mod.apk AndroidManifest.xml assets/ res/ resources.arsc classes.dex lib/ …
zipalign -p -f 4 mod.apk mod-aligned.apk
apksigner sign --ks my.keystore --out db-local-mod.apk mod-aligned.apk
```

Risiko tercatat: `libgavesec` bisa memverifikasi integritas APK (signature/class hash) — jika setelah repack app force-close saat dekripsi, berarti integrity check aktif → perlu di-bypass (fase Opsi 2). Belum terbukti terjadi; uji dulu.

### 4.5 Spesifikasi minimum local server (untuk fase berikutnya)

1. **Resource server (port 80):** serve `client/properties/clientversion.json` → `{"clientVersion":"<baru>-EN"}` + seluruh pohon `client/resource/…` + `activity_en/…` + `manifest.json` + `index-native.html`; header `Access-Control-Allow-Origin: *`, `Cache-Control: max-age=2592000` (meniru EdgeOne).
2. **Login server (port 18110, TLS):** engine.io/socket.io handshake (cookie `io=`), balas frame `430[{"ret":0,"data":"{\"serverList\":[…] json-string}"}]` dengan 1 server lokal: `{"serverId":"1","new":true,"online":true,"hot":true,"url":"https://192.168.1.100:9000","name":"Local 1","offlineReason":""}`.
3. **Battle server (port 9000, TLS):** socket.io gameplay (protokol frame diteliti di fase 5 dari dump JS).
4. **Error log (port 10000, HTTP):** `POST /error/battle/client` → 200 OK.

---

## 5. Roadmap proyek (panjang)

- [x] **Fase 1** — Bedah APK + inventaris endpoint + struktur android/data (laporan ini) ✅
- [ ] **Fase 2** — Edit 3 file native (§4.2) + repack + uji install (apakah integrity check gavesec aktif)
- [ ] **Fase 3** — Mock resource server (port 80) + redirect pigres → game bisa boot sampai layar login
- [ ] **Fase 4** — Mock login server socket.io (18110) dengan schema serverList
- [ ] **Fase 5** — Dump JS Egret terdekripsi (Frida) → patch endpoint pksilo langsung di JS + pahami protokol battle frame
- [ ] **Fase 6** — Mock battle server + ekosistem local (akun/SDK opsional)

---

## 6. Metode & bukti

- Clone repo → unzip `db-local-original.apk` → parse manifest (pyaxmlparser) → scan URL 469 file (assets, dex, arsc, lib) → decode base64 config.
- Data perangkat `log/extracted/com.Vanguard.twnthe` (4816 folder / 16348 file) dibaca menyeluruh: cache header Egret menyimpan URL asli → 99 host:port + protokol `getServerList` tertangkap utuh.
- Status server langsung dicek: `configus` (COS NoSuchKey), `dragon:82` (301 mati), `qsdk.t4game.com` (200 hidup).
- Semua string di `assets/game/` dicek: **tidak ada** endpoint plaintext (terenkripsi gavesec) — bukti dasar Opsi 2.
- Catatan keamanan: laporan ini sengaja TIDAK memuat token/credential akun yang ditemukan di data perangkat.
