# db-apk — DB - Local (`com.db.local`) — 1 APK, 2 SISTEM, 100% OFFLINE

Dokumen ini adalah hasil bedah langsung terhadap objek kerja tunggal repo ini:
**`db-local(Original-DO NOT DELETE!).apk`** (file LFS, 104.945.181 bytes).
Semua klaim bertanda **[TERVERIFIKASI]** dibuktikan dari isi APK itu sendiri
(manifest, bytecode `classes.dex`, asset, native lib) — bukan dari asumsi.

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
| Signature | **AOSP testkey** (CN=Android, SHA1 `61:ED:37:7E:85:D3:86:A8:DF:EE:6B:86:4B:D8:5B:0B:FA:A5:AF:81`, SHA1withRSA) |
| Clear-text HTTP | **DIIZINKAN** — `usesCleartextTraffic="true"` + `network_security_config.xml` `<base-config cleartextTrafficPermitted="true"/>` |
| Application class | `com.zhuhuan.game.Application` |
| Launcher | `com.zhuhuan.game.LaunchActivity` (launchMode singleTop, portrait) |
| Game host | `com.zhuhuan.game.MainActivity` |
| Native lib | `libegret.so` (Egret Native runtime, bundled libcurl), `libgavesec.so` |
| Dynamic runtime | `assets/runtime-dex.jar` (loader hot-update Egret, obfuscated `org.egret.a/b/*`) |
| Game SDK | QuickGame SDK (`com.quickgame.*`), Facebook SDK, Google Billing 3.0.2, GMS Sign-in |
| Izin kunci | INTERNET, ACCESS_NETWORK_STATE, WRITE/READ_EXTERNAL_STORAGE, WAKE_LOCK, `requestLegacyExternalStorage="true"` |

Fakta clear-text true itu **kunci proyek**: client boleh bicara HTTP polos ke
`http://127.0.0.1:4000` tanpa TLS, tanpa konfigurasi tambahan.

---

## 2. ARSITEKTUR: 1 APK = 2 SISTEM

```
+------------------------------------------------------------------+
|                     SATU APK com.db.local                        |
|                                                                  |
|  SISTEM 2 (ada sekarang, BEKU/PATEN)   SISTEM 1 (ditanam, fokus) |
|  Client game Egret + QuickGame SDK     Mini server lokal         |
|  - TIDAK diubah logikanya              - classes2.dex s2.kernel.*|
|  - SATU edit: endpoint -> local        - ContentProvider         |
|    (base64 di config*.properties)        s2.kernel.KernelProvider|
|                                        - bind 127.0.0.1:4000     |
|                                        - dashboard owner /dash   |
+------------------------------------------------------------------+
```

### Urutan boot hasil integrasi [RANCANGAN SISTEM 1]

1. **`s2.kernel.KernelProvider`** (ContentProvider) berjalan PALING AWAN —
   Android selalu menjalankan ContentProvider sebelum `Application.onCreate`.
   Tugas: seed 14 file zona server ke `Android/data/com.db.local/server/`
   (SEBELAH folder `files/` — client tidak tersentuh), lalu menyalakan
   mini server yang bind `127.0.0.1:4000`.
2. `Application` / `LaunchActivity` client jalan seperti biasa — tapi
   endpoint sudah mengarah ke `127.0.0.1:4000`, sehingga semua jawaban
   datang dari mini server lokal.
3. Game hidup tanpa internet. Belum siap = HOLD (tanpa popup error).

> Catatan: di APK ORIGINAL (objek repo ini) **belum ada** `classes2.dex`
> maupun `KernelProvider` — keduanya ditambahkan saat rebuild. Ini
> konsisten dengan isi APK: hanya ada satu `classes.dex` (6.697.104 bytes)
> berisi client + SDK.

---

## 3. SISTEM 2 — CLIENT GAME (BEKU): ALUR BOOT 1:1 [TERVERIFIKASI DARI BYTECODE]

Alur berikut ditelusuri langsung dari smali/bytecode `classes.dex`.

### 3.1 Endpoint asli (base64 di assets)

`LaunchActivity.onCreate` memanggil pemilihan supplier **"BS"** [TERVERIFIKASI],
sehingga file yang dibaca adalah `config_BS.properties` (pola `config_%s.properties`).

| File | Key | Base64 | Hasil decode |
|---|---|---|---|
| `assets/config.properties` | EntryPoint1 | `aHR0cDovL2RyYWdvbi5zam1vYmlsZWdhbWUuY29tOjgyL2x6Y2VzaGkvbmF0aXZlX3NldHRpbmcvQW5kcm9pZA==` | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` |
| `assets/config_BS.properties` | EntryPoint1 | `aHR0cHM6Ly9jb25maWd1cy5zam1vYmlsZWdhbWUuY29tL2JzL2RiL2FuZHJvaWQ=` | `https://configus.sjmobilegame.com/bs/db/android` |
| `assets/config_BS.properties` | EntryPoint2 | `aHR0cHM6Ly9jb25maWd1cy0xMjU3MDY2MDg2LmNvcy5uYS1zaWxpY29udmFsbGV5Lm15cWNsb3VkLmNvbS9icy9kYi9hbmRyb2lk` | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` |
| `assets/QHinfo` | (teks polos) | — | `http://qsdk.t4game.com` |

**SATU-SATUNYA sentuhan ke client** = ganti nilai EntryPoint (base64) menjadi
`http://127.0.0.1:4000/lzceshi/native_setting/Android`. Logika client tidak disentuh.

### 3.2 Fetch + dekripsi setting [TERVERIFIKASI]

Dari bytecode `LaunchActivity`:

1. HTTP GET EntryPoint (retry 5x, timeout naik: `5 * i * 1000` ms).
2. Response bytes di-XOR **repeating-key**: `srcData[i] ^= keyData[i % keyLen]`.
3. `keyData` berasal dari konstanta string **`"DragonBall"`** di
   `LaunchActivity.<init>` [TERVERIFIKASI — `const-string 'DragonBall'` lalu
   `iput-object` ke field byte[]-nya].
4. Hasil XOR didekode UTF-8 → parse JSON setting.
5. Setting disimpan (SharedPreferences, termasuk key `nativeRender`).

### 3.3 Field JSON native_setting yang DIBACA client [TERVERIFIKASI]

Semua key di bawah diekstrak dari bytecode parser setting. Mini server
**wajib** mengirim field-field ini (XOR-"DragonBall"):

| Field | Tipe | Peran |
|---|---|---|
| `url` / `webUrl` | string | URL shell game untuk render NATIVE (EgretNativeAndroid) |
| `tsUrl` / `tsWebUrl` | string | varian shell TS |
| `gameUrl` / `gameUrlTs` | string | dipakai `EgretNativeAndroid.initialize(gameUrl)`; fallback `gameUrlTs` di-decode Base64 [TERVERIFIKASI di `MainActivity$cointhreat.run`] |
| `nativeRender` | int/bool | `1` = render native via libegret.so |
| `nativeUnzipJson` | bool | unzip patch native |
| `update` / `forceBuild` / `noForceBuild` / `forceUrl` | string | kontrol update/force |
| `loginServer` / `loginPort` | string | **target socket login game** — arahkan ke `127.0.0.1` / `4000` |
| `tsLoginServer` | string | varian TS |
| `loginKey` | string | key protokol login (dipakai layer JS) |
| `loginWebPort` | string | port web login |
| `productPrefix` / `gameIcon` / `gameLanguage` | string | branding/bahasa |
| `peiUrl` / `wpeiurl` / `wpei` | string/bool | layanan PEI |
| `blackWhiteL` / `blackWhiteLNetSegment` / `currentNetIpUrl` | string | filter IP |
| `checkGoogleTicketUrl` | string | verifikasi ticket Google Play |
| `checkTime`, `clientParams{}`, `extra{}`, `productIdMap{}` | objek | parameter SDK/billing |

### 3.4 Engine & hot-update [TERVERIFIKASI]

- `MainActivity` menyiapkan `EgretNativeAndroid` (libegret.so) dan WebView
  cadangan `mGameWebView` dengan bridge JS `jsbind`.
- **External interface JS <-> native** yang terpasang: `startGame`,
  `changeView`, `changeLanguage`, `refresh`, `pei`, `giveLike`, `contact`,
  `report2Third`, `openURL`, `switchAccount`, `userCenter`, `startTsGame`,
  `sendLog`.
- **GameUpdateUtil** (hot-update, dibaca dari bytecode):
  - bandingkan lokal `game/base.version` = **110** dan `game/resource.version`
    = **11220** dengan server: `{url}/base.version?rnd=%f`, `{url}/resource.version?rnd=%f`.
  - ambil `{url}/upgrade.json?rnd=%f` dan `{url}/size.json?rnd=%f`.
  - unduh patch `{url}/base.zip?v=` dan `{url}/all.zip?v=`, ekstrak ke
    `preloadPath`.
  - gagal jaringan -> "Read Upgrade Config File Fail,Please Check NetWork!",
    404 -> dianggap tidak ada patch (fallback resource bawaan APK).
- Patch bawaan terenkripsi: `assets/game/vvcc.bin`, `vvcc_tc.bin`,
  `voyage_fancy_radius.bin`, `pschent_full_tired.bin` — semua punya magic
  **`onepieceencrypts`** di offset 16 [TERVERIFIKASI via hexdump]; daftar
  patch ada di `assets/game/infoinfo.bin` (`voyage_fancy_radius|pschent_full_tired`).
- Runtime-dex `assets/runtime-dex.jar` = komponen loader/patcher Egret.

### 3.5 Login game (layer JS)

Setelah shell + JS hidup, client menghubungi `loginServer:loginPort`
(dari setting 3.3). Protokol di sisi server (socket.io EIO3, TEA, dsb.)
ada di lapisan JS game — kontrak lengkapnya ada di **§4**.

---

## 4. SISTEM 1 — MINI SERVER (FOKUS PROYEK, JALAN PERTAMA)

Ditulis ke `classes2.dex` (`s2.kernel.*`), dipicu `s2.kernel.KernelProvider`.
Semua di bawah ini adalah **kontrak wajib** yang menyusuri kebutuhan client
di §3 — bukan perkiraan.

### 4.1 Lingkungan

- Bind **`127.0.0.1:4000`** saja (tidak expose ke luar).
- Root data: `Android/data/com.db.local/server/` (dibuat oleh KernelProvider,
  di luar `files/` supaya tidak dianggap milik client).
- Zig-zag jaringan TIDAK diperlukan: cleartext sudah diizinkan (§1).

### 4.2 Endpoint HTTP wajib di port 4000

| Endpoint | Respons | Sumber kebutuhan |
|---|---|---|
| `GET /lzceshi/native_setting/Android` | body = JSON §3.3 **di-XOR dengan key `DragonBall`** | LaunchActivity |
| `GET /base.version?rnd=...` | angka versi (mis. `110`) | GameUpdateUtil |
| `GET /resource.version?rnd=...` | angka versi (mis. `11220`) | GameUpdateUtil |
| `GET /upgrade.json?rnd=...` | daftar patch (atau 404 = tanpa patch) | GameUpdateUtil |
| `GET /size.json?rnd=...` | daftar ukuran | GameUpdateUtil |
| `GET /base.zip?v=...`, `/all.zip?v=...` | patch resource (atau 404) | GameUpdateUtil |
| `GET /` (shell) — `index-native.html`, `manifest.json`, 14 JS asli | file shell game | EgretNativeAndroid.initialize |
| `GET /dash` | dashboard owner | fitur proyek |

Contoh payload native_setting (sebelum di-XOR), field minimum agar game hidup:

```json
{
  "url": "http://127.0.0.1:4000/",
  "webUrl": "http://127.0.0.1:4000/",
  "gameUrl": "http://127.0.0.1:4000/index-native.html",
  "gameUrlTs": "",
  "tsUrl": "http://127.0.0.1:4000/",
  "tsWebUrl": "http://127.0.0.1:4000/",
  "nativeRender": 1,
  "nativeUnzipJson": true,
  "update": "1",
  "forceBuild": "",
  "noForceBuild": "1",
  "forceUrl": "",
  "loginServer": "127.0.0.1",
  "loginPort": "4000",
  "tsLoginServer": "127.0.0.1",
  "loginKey": "DragonBall",
  "loginWebPort": "4000",
  "productPrefix": "dblocal",
  "gameIcon": "",
  "gameLanguage": "id",
  "peiUrl": "", "wpeiurl": "", "wpei": false,
  "blackWhiteL": "", "blackWhiteLNetSegment": "", "currentNetIpUrl": "",
  "checkGoogleTicketUrl": "",
  "checkTime": "0",
  "clientParams": {}, "extra": {}, "productIdMap": {}
}
```

### 4.3 Protokol socket login (port yang sama, 4000)

- Socket.io v2 / **EIO3** (kompatibel client JS lama).
- Urutan sesuai alur game:
  1. **verify** — handshake TEA, jawab **ACK**;
  2. `loginGame` — autentikasi akun lokal;
  3. `GetServerList` — kirim `{serverList, history}` (daftar server owner);
  4. `user.enterGame` — kirim save lengkap pemain;
  5. `registChat` + `chat.*` — channel chat lokal;
  6. routing `user.*`, `chat.*`, handler gameplay lain.
- Belum siap melayani = **HOLD** koneksi (client diam, TANPA popup error).

### 4.4 Dashboard owner — `http://127.0.0.1:4000/dash`

| Menu | Fungsi |
|---|---|
| Status | hidup/mati kernel, versi zona, jumlah pemain |
| Database | lihat + edit save pemain (JSON) |
| Config | edit nama server / gameplay / setting; **hot-swap ~2 detik** |
| Handlers | aktif/nonaktif modul logic (`user.*`, `chat.*`, dll.) |
| Logs | log runtime server |
| Update resource | taruh patch baru (base.zip/all.zip + version) |

### 4.5 Sumber zona server (dibangun BARU)

- `zona/` = SUMBER TUNGGAL isi zona — dibuat baru dari nol (config + data
  + handler tier1/tier2). Ditanam ke dalam dex sebagai seed tertanam.
- `static-game/` = shell + 14 JS — WAJIB 1:1 dari paket client asli
  (hasil FASE B), NOL byte buatan kita.
- Seed diekstrak KernelProvider ke `Android/data/com.db.local/server/`
  saat boot pertama; owner mengubah lewat dashboard, bukan APK.

---

## 5. PETA STRUKTUR APK [TERVERIFIKASI]

```
com.db.local (104,9 MB)
├── AndroidManifest.xml          (binary; dibedah di §1-2)
├── classes.dex                  6.697.104 B  (client + SDK — BEKU)
├── assets/                      89 MB
│   ├── config.properties        EntryPoint1 (base64) — endpoint global
│   ├── config_BS.properties     EntryPoint1+2 (base64) — endpoint supplier BS (dipakai)
│   ├── QHinfo                   "http://qsdk.t4game.com"
│   ├── runtime-dex.jar          loader hot-update Egret (obfuscated)
│   └── game/                    resource client
│       ├── base.version         "110"
│       ├── resource.version     "11220"
│       ├── infoinfo.bin         daftar patch: voyage_fancy_radius|pschent_full_tired
│       ├── vvcc.bin / vvcc_tc.bin             magic "onepieceencrypts"
│       ├── voyage_fancy_radius.bin (1,2 MB)   magic "onepieceencrypts"
│       ├── pschent_full_tired.bin  (9,4 MB)   magic "onepieceencrypts"
│       └── resource/… assets/… assets_ts/…    tree resource obfuscated (Egret)
├── lib/arm64-v8a/ + armeabi-v7a/
│   ├── libegret.so              runtime Egret Native + libcurl
│   └── libgavesec.so            proteksi SDK
├── res/xml/network_security_config.xml   cleartextTrafficPermitted=true
├── META-INF/CERT.RSA            AOSP testkey
└── resources.arsc               841.628 B
```

---

## 6. TOOLCHAIN REBUILD BARU (dibangun dari nol)

```
FASE B/C/D — alur produksi v06 (semua artefak BARU):

[1] KONTRAK  : bedah JS client -> KONTRAK.md (lampiran README §9.1)
[2] STATIC   : shell + manifest.json + 14 JS hasil FASE B -> assets/static/game/
[3] ZONA     : zona/ (config + data + handlers) -> divalidasi JSON -> seed tertanam dex
[4] KERNEL   : src-kernel/ s2.kernel.* -> javac -8 (classpath android.jar)
             -> d8 --min-api 21 -> classes2.dex
[5] ASSEMBLE : skrip zip (dibuat baru): entri ORIGINAL byte-identik
             + AndroidManifest(+provider) + config*.properties (edit endpoint)
             + classes2.dex + assets/static/game/**
[6] SIGN     : keystore BARU (keytool, RSA 2048, validitas >30 thn) + apksigner
[7] SANITY   : diff entri zip vs ORIGINAL — hanya entri yang direncanakan
```

Prinsip rebuild:

1. Original APK (`db-local(Original-DO NOT DELETE!).apk`) **tidak pernah diubah**
   — acuan tunggal seluruh kontrak.
2. Sistem 2 (client) hanya menerima SATU edit: nilai EntryPoint base64 di
   `config.properties` + `config_BS.properties` →
   `http://127.0.0.1:4000/lzceshi/native_setting/Android` (+ `/bs/db/android`).
3. Sistem 1 (kernel) masuk sebagai `classes2.dex` tambahan + deklarasi
   `<provider android:name="s2.kernel.KernelProvider" .../>` di manifest —
   menjamin kernel hidup SEBELUM Application/Activity client.
4. Signature: original memakai AOSP testkey; v06 memakai **keystore BARU**
   (disimpan + password dicatat di README, jangan hilang).
   **Ganti signature = WAJIB uninstall APK lama** sebelum pasang versi baru.
5. Setiap APK yang masuk repo via LFS **wajib diverifikasi unduh balik**
   (pointer tidak cukup — objek harus benar-benar ada di LFS).

---

## 7. CHECKLIST OFFLINE 100%

- [x] Cleartext HTTP ke 127.0.0.1 diizinkan manifest + network config [TERVERIFIKASI]
- [x] Endpoint setting bisa dipindah ke local lewat SATU edit base64 [TERVERIFIKASI]
- [x] Client membaca `loginServer`/`loginPort` dari setting (bukan hardcode) [TERVERIFIKASI]
- [x] Hot-update punya jalur 404/fallback aman (resource bawaan tetap dipakai) [TERVERIFIKASI]
- [x] Semua alamat lain (Facebook/Billing/GMS/PEI/qsdk) tidak menghalangi boot offline; gagalnya non-fatal [TERVERIFIKASI dari struktur SDK opsional]
- [ ] classes2.dex `s2.kernel.*` + KernelProvider ditanam (rebuild)
- [ ] Mini server bind 127.0.0.1:4000 + kontrak §4 lengkap
- [ ] Dashboard /dash hidup
- [ ] Alur 1:1 dinyatakan selesai saat: buka ikon -> login -> masuk game tanpa internet

---

## 8. FILE DI REPO

| File | Keterangan |
|---|---|
| `db-local(Original-DO NOT DELETE!).apk` | ORIGINAL mentah (Git LFS, 104.945.181 B, SHA-256 `b308c4…2218`) — **ACUAN TUNGGAL**, JANGAN diubah |
| `log/com.Vanguard.twnthe.zip` | arsip log/pendukung (Git LFS, 154.891.608 B) — bukan acuan desain |
| `README.md` | dokumen ini (doc tunggal; boleh ditimpa setiap update) |

**KEPUTUSAN OWNER (mengikat):** SELURUH git history dan artefak lama
(kernel lama, APK rebuild lama v01–v05, keystore lama) dinyatakan **VOID /
CACAT** — TIDAK dijadikan acuan, TIDAK dipulihkan, TIDAK dibedah.
Rencana di §9 dibangun dari NOL dengan sumber acuan satu-satunya: file
APK original + (hasil FASE B) isi paket client itu sendiri. Yang dipakai
dari rencana lama hanyalah **arasetktur umum** (provider boot dulu,
port 4000, dashboard) yang sudah cocok dengan bukti APK.

---

## 9. RENCANA MASTER BARU — LOCAL STANDALONE v06 (DARI NOL, 1:1 SERVER ASLI)

Prinsip: **client beku, server lokal baru meniru 1:1 layanan server asli**.
Seluruh acuan = file APK original (bedah §1–§5). Kontrak yang belum bisa
dibuktikan dari native layer akan **ditarik langsung dari JS client**
(bagian dari paket asli) pada FASE A — bukan dari dokumen lama apa pun.

### 9.1 PETA LAYANAN: SERVER ASLI → LOCAL (1:1)

| # | Layanan SERVER ASLI (online) | Bukti | LOCAL pengganti (127.0.0.1:4000) | Modul kernel BARU |
|---|---|---|---|---|
| A | Config server `dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` — JSON XOR `DragonBall` | §3.2 [TERVERIFIKASI] | `GET /lzceshi/native_setting/Android` — body setting.json XOR on-the-fly | ROUTE (deklaratif) + CODEC |
| B | Config supplier BS `configus.sjmobilegame.com/bs/db/android` + backup COS | §3.1 [TERVERIFIKASI] | `GET /bs/db/android/…` (XOR `DragonBall`) | ROUTE idem |
| C | Resource/version CDN (`base.version`, `resource.version`, `upgrade.json`, `size.json`, `*.zip`) | §3.4 [TERVERIFIKASI]; lokal 110/11220 | jawab versi = **persis assets APK** → Egret NOL download; `*.zip`/json update tidak diperlukan | ROUTE + VERSIONBIN |
| D | Shell/JS host (`index-native.html`, `manifest.json`, 14 JS) | §3.4 [TERVERIFIKASI] | static engine menyajikan `static-game/**` (hasil FASE B, 1:1 paket client) | STATIC |
| E | Realtime login/game (`loginServer:loginPort`) | §3.3 [TERVERIFIKASI] | engine.io v3 (polling + WS RFC6455) + socket.io v2 (`40/42/43`, ping/pong, probe) | WIRE |
| F | Handshake auth crypto | layer JS — **ditarik dari JS client (FASE A)** | server emit `verify` challenge → jawaban TEA/XXTEA sesuai kontrak JS | WIRE + CODEC |
| G | Dispatcher gameplay | layer JS — **FASE A** | ACK format persis kontrak JS; tidak match = **HOLD** (NOL popup) | WIRE + HANDLER |
| H | Server list pilih server | layer JS — **FASE A** | ACK `{serverList:[…], history:[…]}` (field entri ikut kontrak JS) | ZONA config |
| I | DB pemain (save server) | §3.3 (`user.enterGame`) | save lokal di `Android/data/com.db.local/server/` | KV + ZONA |
| J | Pelengkap: `checkGoogleTicketUrl`, PEI, `qsdk`, FB, GMS | §1/§3.3 — SDK opsional | kosongkan/stub; gagalnya non-fatal | ROUTE stub |
| K | — (tidak ada di asli) | — | **Dashboard owner `/dash`** (status/db/config hot-swap/handlers/logs) | DASH + HOT-SWAP |

### 9.2 PETA TOOLS: DUNIA ASLI → LOCAL (1:1)

| Peran | Dunia ASLI | LOCAL kita pakai |
|---|---|---|
| Runtime game native | Egret Native `libegret.so` di APK | **SAMA** — NOL perubahan (beku) |
| Engine H5 | Egret + 14 JS dari server asli | **SAMA** — 1:1 dari paket client (FASE B), disajikan lokal |
| Realtime | engine.io v3 + socket.io v2 | **SAMA** — implementasi baru setia (WIRE) |
| Kripto | TEA/XXTEA + XOR `DragonBall` + LZString + md5 + base64 (key final dari FASE A) | **SAMA** — CODEC baru (delta XXTEA `0x9E3779B9`, longs LE) |
| Format config client | `.properties` + base64 | **SAMA** — hanya nilai EntryPoint diganti |
| Kontrak versi resource | CDN asli | **SAMA** — 110/11220 persis assets APK |
| Handler backend | server privat asli (tertutup) | tier1 JSON deklaratif + tier2 JS runtime (Rhino) — owner edit tanpa recompile |
| Database | backend asli | KV/JSON zona |
| Kompilasi kernel | Android SDK (asli) | **javac 8 + d8 --min-api 21** (+ android.jar API 30) → `classes2.dex` |
| Assemble | aapt (asli) | skrip python zip baru: entri ORIGINAL byte-identik + 4 tambahan terencana |
| Signature | AOSP testkey (asli) | **keystore BARU** (keytool RSA 2048) + apksigner — dijaga, password dicatat |
| Runtime server | VPS luar | proses Java dalam APK — `KernelProvider` boot dulu → bind `127.0.0.1:4000` |

### 9.3 FASE EKSEKUSI (URUT, TANPA MELOMPANG)

| Fase | Isi | Keluaran / Bukti selesai |
|---|---|---|
| A — SHELL & ENGINE 1:1 | tarik shell + manifest.json + 14 JS dari paket client: dekripsi arsip `onepieceencrypts` (`vvcc.bin`, `pschent_full_tired.bin` — algoritma dicari di `runtime-dex.jar`/`libegret.so`) ATAU panen cache device yang pernah main online (jalur bersih) | `static-game/` — setiap file punya asal tercatat + hash; manifest.json asli jadi daftar wajib |
| B — KONTRAK DARI CLIENT | bedah JS client (hasil FASE A) — teks polos `main.min` dkk.: string `verify`, TEA/XXTEA key, `GetServerList`, format ACK, field setting yang dibaca JS (`window.getLoginServer` dll.) | **KONTRAK.md** — lampiran README: setiap field/route/protokol dengan bukti offset JS |
| C — KERNEL BARU | tulis `s2.kernel.*` dari nol: ROUTE, CODEC, WIRE (EIO3), STATIC, KV, HANDLER (Rhino), DASH, HOT-SWAP, KernelProvider + seed tertanam — **hanya setelah KONTRAK.md disetujui** | `classes2.dex` + sha256 dicatat (identitas v06) |
| D — REBUILD | assemble (entri original byte-identik + manifest+provider + config edit + static-game + classes2.dex) → sign keystore BARU → sanity diff zip | `db-local-rebuild-v06.apk` + daftar perbedaan entri vs ORIGINAL |
| E — VERIFIKASI LAPANGAN | uninstall lama → install v06 → **mode pesawat** → buka ikon | boot provider → native_setting XOR ok → shell → 14 JS → verify ACK → loginGame → serverList auto-pilih → enterGame save → chat → gameplay; `/dash` hidup |
| F — AUDIT GAP 1:1 | log WIRE vs KONTRAK.md baris per baris | daftar gap (bila ada) → v07 |
| G — RELEASE | keystore + password tercatat; LFS diverifikasi unduh balik; backup offline | v06 FINAL stabil |

> Ketergantungan: FASE A menutup dua risiko sekaligus (sumber shell + sumber
> kontrak JS). Jika dekripsi `onepieceencrypts` macet, jalur cadangan = panen
> cache device yang pernah main online (folder Egret di penyimpanan). Kedua
> jalur tetap "1:1 dari paket asli". FASE B–C TIDAK BOLEH mulai sebelum
> `static-game/` dan KONTRAK.md beres.

### 9.4 DEFINITION OF DONE

1. Install bersih + **mode pesawat penuh** → buka ikon → sampai gameplay **tanpa satu pun popup error** (belum siap cukup HOLD).
2. `http://127.0.0.1:4000/dash` hidup: status / database lihat+edit / config edit (**hot-swap <2 dtk**) / handlers / logs.
3. Owner mengganti nama server & setting → terlihat di client tanpa rebuild.
4. Netmonitor: NOL koneksi keluar perangkat — semua hanya `127.0.0.1:4000`.
5. KONTRAK.md lengkap (setiap layanan §9.1 punya bukti dari APK atau JS client).

### 9.5 RISIKO & MITIGASI

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Dekripsi `onepieceencrypts` gagal/sulit | shell+JS tidak keluar dari APK | jalur cadangan FASE B: panen cache device yang pernah main online; keduanya tetap 1:1 |
| Kontrak JS tersembunyi (field/ACK tak terduga) | client hang di fase tertentu | FASE A mencetak kontrak SEBELUM kernel ditulis; WIRE selalu HOLD + log untuk event tak dikenal |
| LFS upload gagal (pointer tanpa objek) | APK hilang permanen | setiap push APK: verifikasi unduh balik via media URL + salinan offline |
| Keystore hilang lagi | upgrade berantakan | keystore baru dijaga + password dicatat di README + backup offline |
| Rhino/jdk di HP kelas rendah | lag/crash | `largeHeap=true` sudah ada (§1); pantau via dash; batasi handler tier2 |
| Manifest provider salah | kernel tidak boot | template provider ditulis sekali + sanity diff zip (FASE D step 7) |

### 9.6 PERTANYAAN DISKUSI (dibedah bersama di chat)

1. **FASE B** — jalur mana yang kita tempuh: (a) dekripsi patch `onepieceencrypts`, (b) panen cache device, atau (c) coba (a) dulu, (b) cadangan?
2. **FASE C** — stack kernel baru: Java 8 + Rhino (disarankan: ringan, no-dependency selain rhino-core) atau ada preferensi lain?
3. **loginKey/TEA** — key autentikasi ditarik dari JS client (FASE A); kalau hasilnya bukan `verification`, ikut kontrak JS sepenuhnya — setuju?
4. **Dashboard FASE F/G** — fitur owner mana yang wajib di v06 dan mana yang boleh menunggu v07 (reset save, multi-server, export/import zona, update resource)?
5. **Distribusi** — sideload apa adanya, atau perlu rename package per-varian?
6. **Ritme release** — setiap perbaikan gap = v07, v08 dst. dengan README selalu diperbarui — setuju?

---

*Dokumen dibuat dari bedah langsung APK: manifest (AXML), bytecode
`classes.dex` (androguard), hexdump asset, native lib. Acuan tunggal =
file APK original. History/artefak lama = VOID (keputusan owner §8).
Klaim yang belum terbukti ditandai dan dijadwalkan dibuktikan (FASE A/B).*
