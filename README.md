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

### 4.5 Sumber zona server

- `zone-seed/` = SUMBER TUNGGAL isi zona (divalidasi JSON oleh `gen-seed.py`
  sebelum ditanam ke `Seed.java`).
- `zone-static/` = shell + 14 JS asli client (jangan diubah).
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

## 6. RENCANA REBUILD (client beku + kernel ditanam)

```
cd kernel
python3 gen-seed.py        # zone-seed/ -> Seed.java (validasi JSON dulu)
bash build-dex.sh          # ECJ + D8 -> out/classes2.dex (s2.kernel.*)
python3 assemble-apk.py out/vXX-unsigned.apk   # dari ORIGINAL.apk repo ini
java -jar uber-apk-signer.jar --apks out/vXX-unsigned.apk \
  --ks keystore/serverkernel-v3.keystore --ksAlias serverkernel \
  --ksPass dblocal-kernel-2026 --ksKeyPass dblocal-kernel-2026
```

Prinsip rebuild:

1. Original APK (`db-local(Original-DO NOT DELETE!).apk`) **tidak pernah diubah**.
2. Sistem 2 (client) hanya menerima SATU edit: nilai EntryPoint base64 di
   `config.properties` + `config_BS.properties` →
   `http://127.0.0.1:4000/lzceshi/native_setting/Android`.
3. Sistem 1 (kernel) masuk sebagai `classes2.dex` tambahan + deklarasi
   `<provider android:name="s2.kernel.KernelProvider" .../>` di manifest —
   menjamin kernel hidup SEBELUM Application/Activity client.
4. Signature: original memakai AOSP testkey; rebuild memakai keystore
   `serverkernel-v3`. **Ganti signature = WAJIB uninstall APK lama** sebelum
   pasang versi baru.

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
| `db-local(Original-DO NOT DELETE!).apk` | ORIGINAL mentah (Git LFS, 104.945.181 B, SHA-256 `b308c4…2218`) — objek kerja tunggal, JANGAN diubah |
| `log/com.Vanguard.twnthe.zip` | arsip log/pendukung (Git LFS, 154.891.608 B) — bukan acuan desain |
| `README.md` | dokumen ini (doc tunggal; boleh ditimpa setiap update) |

---

*Dokumen dibuat dari bedah langsung APK: manifest (AXML), bytecode
`classes.dex` (androguard), hexdump asset, dan native lib. Klaim yang tidak
bisa dibuktikan dari APK ditandai [RANCANGAN].*
