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

## 8. FILE DI REPO + STATUS PEMULIHAN

| File | Keterangan |
|---|---|
| `db-local(Original-DO NOT DELETE!).apk` | ORIGINAL mentah (Git LFS, 104.945.181 B, SHA-256 `b308c4…2218`) — objek kerja tunggal, JANGAN diubah |
| `log/com.Vanguard.twnthe.zip` | arsip log/pendukung (Git LFS, 154.891.608 B) — bukan acuan desain |
| `README.md` | dokumen ini (doc tunggal; boleh ditimpa setiap update) |

Kondisi nyata yang terungkap dari git history (terverifikasi 2026-11-19):

| Objek | Status | Lokasi pemulihan |
|---|---|---|
| Sumber kernel 14 kelas `s2.kernel.*` (4.190 baris) + `Seed.java` | **UTUH** | history commit `aaf61fe` → `kernel/src/` |
| `zone-seed/` (setting.json, serverList.json, starter-save.json, versionbin.json, handlers tier1, engine.json) | **UTUH** | history `aaf61fe` → `kernel/zone-seed/` |
| `zone-static/game/` (index-native.html, index.html, manifest.json, 14 JS: egret, eui, dragonBones, game, battlelogic, socket.io, main.min 4,7 MB, dll.) | **UTUH** | history `aaf61fe` → `kernel/zone-static/` |
| Build tools (`gen-seed.py`, `build-dex.sh`, `assemble-apk.py`, `libs/ecj-3.33.0.jar`, `libs/rhino-core.jar`, `libs/rhino-1.7.10.jar`) | **UTUH** | history `aaf61fe` → `kernel/` |
| `extract/` (AndroidManifest.xml hasil edit +provider, config.properties & config_BS.properties hasil edit endpoint → `http://127.0.0.1:4000/…`) | **UTUH** | history `aaf61fe` → `extract/` |
| Keystore `serverkernel.keystore` (v1) + `serverkernel-v2.keystore` | **UTUH** | history `473ca2c` / `e94f388` (root) |
| Keystore `serverkernel-v3` (pemegang signature v05) | **HILANG** — tak pernah masuk repo | buat keystore baru (v4) |
| APK `db-local-rebuild-miniserver-05.apk` (FINAL, 116.224.711 B) | **HILANG** — pointer LFS 134 B, objek 404 | rebuild dari sumber (FASE 2) |
| APK v01–v04 | pointer/hilang | tidak diperlukan |

Perintah pemulihan (sudah teruji):

```
git fetch --unshallow
git checkout aaf61fe -- kernel extract      # sumber + tools + extract
git checkout 473ca2c -- serverkernel-v2.keystore   # opsional
```

---

## 9. RENCANA MASTER — LOCAL STANDALONE v06 (1:1 DARI SERVER ASLI)

Prinsip: **client beku, server lokal meniru 1:1 layanan server asli**, semua
bukti diambil dari APK (bedah §3) dan dari catatan kontrak yang tertanam di
sumber kernel (bukti `main.min.js` offset, field ACK, dsb.).

### 9.1 PETA LAYANAN: SERVER ASLI → LOCAL (1:1)

| # | Layanan SERVER ASLI (online) | Bukti dari APK | LOCAL pengganti (127.0.0.1:4000) | Implementasi kernel |
|---|---|---|---|---|
| A | Config server `dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` — JSON XOR `DragonBall` | `config.properties` EntryPoint1; loop XOR + const `"DragonBall"` (smali) | handler `GET /lzceshi/native_setting/Android` — body `config/setting.json` XOR on-the-fly | tier1 `handlers/10-tier1-config.json` (transform `xorDragonBall`) |
| B | Config supplier BS `configus.sjmobilegame.com/bs/db/android` + backup COS myqcloud | `config_BS.properties` EntryPoint1+2 (supplier "BS" dipilih `onCreate`) | `GET /bs/db/android/setting_BS_Android.bin` + `/bs/db/android/com_db_local.bin` (XOR `DragonBall`) | tier1 idem |
| C | Resource/version CDN (`base.version`, `resource.version`, `upgrade.json`, `size.json`, `base.zip`, `all.zip`) | `GameUpdateUtil` (string table smali); lokal `game/base.version`=110, `game/resource.version`=11220 | `GET /version/base.version` + `/version/resource.version` = **110/11220 persis assets APK** → Egret NOL download; `upgrade.json`/`size.json`/`*.zip` tidak diperlukan | tier1 + `versionbin.json` |
| D | Shell/JS host (`index-native.html`, `manifest.json`, 14 JS) | `EgretNativeAndroid.initialize(gameUrl)` | static engine menyajikan `static/game/**` — file static MENANG di atas `files/game/` fallback | merge statis (Http.java) |
| E | Realtime login/game (`loginServer:loginPort`) | setting JSON `loginServer`/`loginPort` | **engine.io v3** (EIO=3 polling + upgrade WebSocket RFC6455) + **socket.io v2** (`40/42/43`, ping 5/pong 6, probe `2probe/3probe`) | `Wire.java` |
| F | Auth handshake TEA | layer JS (bukti catatan kernel) | server EMIT `verify` challenge → client jawab **XXTEA** (key `"verification"`), self-check round-trip | `Wire.java` + `Codec.java` |
| G | Dispatcher gameplay | layer JS | `handler.process` → ACK `{ret, data, compress, serverTime, server0Time}`; tidak match = **HOLD** (NOL popup) | `Wire.java` + `Js.java` (Rhino tier2) |
| H | Server list pilih server | bukti `main.min.js` ~offset 1353k (`selectNewServer/onLoginSuccess/ServerListNodeViewData`) | ACK `User.GetServerList` = `{serverList:[…], history:[…]}`; entri `serverId,name,url,chaturl,dungeonurl,online,new`; `new:1+online:1` → auto-pilih | `zone-seed/config/serverList.json` |
| I | DB pemain (save server) | save lengkap via `user.enterGame` | `starter-save.json` + KV zona; save pemain hidup di `Android/data/com.db.local/server/` | `KV.java` + zona |
| J | Layanan pelengkap: `checkGoogleTicketUrl`, PEI (`peiUrl/wpeiurl`), `qsdk.t4game.com`, Facebook, GMS | SDK opsional, non-fatal | dikosongkan / stub (`/ipcheck`, `/log`); gagalnya tidak menahan boot | tier1 + setting kosong |
| K | — (tidak ada di asli) | — | **Dashboard owner `/dash`**: status / database / config (hot-swap ±2 dtk) / handlers / logs | `Dash.java` + `Ui.java` + `Hot.java` |

### 9.2 PETA TOOLS: DUNIA ASLI → LOCAL (1:1)

| Peran | Dunia ASLI | LOCAL kita pakai |
|---|---|---|
| Runtime game native | Egret Native `libegret.so` di APK | **SAMA** — NOL perubahan (beku) |
| Engine H5 | Egret + 14 JS dari server asli | **SAMA** — disajikan dari `zone-static/` |
| Realtime | engine.io v3 + socket.io v2 | **SAMA** — re-implementasi setia di `Wire.java` |
| Kripto | XXTEA (`verification`) + XOR `DragonBall` + LZString UTF16 + md5 + base64 | **SAMA** — `Codec.java` (delta `0x9E3779B9`, longs LE, kunci `slice(0,16)`) |
| Format config client | `.properties` + base64 | **SAMA** — hanya nilai EntryPoint diganti ke `127.0.0.1:4000` |
| Kontrak versi resource | server CDN | **SAMA** — `versionbin.json` 110/11220 = assets APK |
| Handler backend | server privat asli (tertutup) | tier1 **JSON deklaratif** + tier2 **JS (Rhino 1.7)** — owner edit tanpa recompile |
| Database | backend asli | KV/JSON zona (`starter-save.json`) |
| Kompilasi kernel | — | `build-dex.sh`: **ECJ 3.33** (Java 8, classpath `android.jar`+`rhino-core`) → jar → **D8/R8** `--min-api 21` → `classes2.dex` |
| Assemble | aapt (asli) | **`assemble-apk.py`**: entri original byte-identik + `classes2.dex` + manifest(+provider) + config edit + `assets/static/game/**` |
| Signature | AOSP testkey (original) | keystore kernel — **v3 hilang** → buat `serverkernel-v4` baru (atau pulihkan v2 dari history) + `uber-apk-signer` |
| Runtime server | VPS luar | **proses Java dalam APK** — `KernelProvider` boot dulu → `Engine.start` bind `127.0.0.1:4000` |

### 9.3 FASE EKSEKUSI

| Fase | Isi | Keluaran / Bukti selesai |
|---|---|---|
| 0 — PEMULIHAN | `git checkout aaf61fe -- kernel extract`; inventaris; checksum libs | folder `kernel/` + `extract/` kembali di repo |
| 1 — DEP BUILD | siapkan `android.jar` (path `/home/z/my-project/android.jar`) + `r8.jar` (D8 8.5) + JRE | build-dex.sh jalan mulai langkah [1/4] |
| 2 — REBUILD v06 | `gen-seed.py` → `build-dex.sh` → `assemble-apk.py out/v06-unsigned.apk` (base: ORIGINAL) → sign | **target reproducible: classes2.dex = 983.856 B, sha256 `32b47c2f…`** (catatan commit v05); kalau beda, wajib diff dan beri alasan |
| 3 — VERIFIKASI LAPANGAN | uninstall APK lama → install v06 → **mode pesawat** → buka ikon | alur: boot provider → native_setting XOR ok → shell → 14 JS → verify XXTEA ACK → `loginGame` → `GetServerList` auto-pilih (`new:1 online:1`) → `user.enterGame` save → `registChat`+`chat.*` → game jalan; `/dash` 5 menu hidup |
| 4 — AUDIT GAP 1:1 | bedah log Wire + logcat vs tabel §9.1 baris per baris | daftar gap (bila ada) |
| 5 — ITERASI v07+ | tutup gap; owner-edit zona; LINTAS LFS: APK baru **wajib ter-upload LFS** (verifikasi curl, bukan cuma push) | APK v07 + catatan di README |
| 6 — RELEASE | keystore final + password tercatat di §8; PAT revoked; backup | rilis stabil |

### 9.4 DEFINITION OF DONE

1. Install bersih di HP + **mode pesawat penuh** → buka ikon → sampai gameplay **tanpa satu pun popup error** (layar belum siap cukup HOLD).
2. `http://127.0.0.1:4000/dash` hidup: status / database lihat+edit / config edit (hot-swap <2 dtk) / handlers / logs.
3. Owner mengganti nama server & setting → perubahan terlihat di client tanpa rebuild.
4. Netmonitor: NOL koneksi keluar perangkat — semua hanya `127.0.0.1:4000`.
5. `classes2.dex` sha256 = `32b47c2f…` (reproducible) atau diverifikasi fungsional setara dengan alasan terdokumentasi.

### 9.5 RISIKO & MITIGASI

| Risiko | Dampak | Mitigasi |
|---|---|---|
| LFS upload gagal lagi (kasus v05: pointer ada, objek 404) | APK hasil hilang permanen | setelah push: `git lfs ls-files` + unduh balik via media URL untuk memastikan objek benar-benar tersimpan; simpan salinan offline |
| Keystore v3 hilang | v06 tidak bisa di-upgrade di atas instalasi v05 | v05 juga hilang → tak relevan; tetapkan keystore BARU dan JANGAN ganti-ganti lagi |
| Versi `android.jar`/r8 berbeda dari mesin build lama | dex ≠ v05 (tak byte-identik) | pakai catatan build (D8 8.5, min-api 21); jika berbeda, audit fungsional §9.3-F3 |
| Kontrak tersembunyi di `main.min.js` (field setting/ACK tambahan) | client hang/gagal di fase tertentu | HOLD + log `Wire.java` menangkap event yang tak terjawab → FASE 4 menutup |
| Manifest provider salah (authorities/exported) | kernel tidak boot | pakai `extract/AndroidManifest.xml` dari history — jangan edit manual baru |
| Memori: kernel + Rhino di HP kelas rendah | lag/Crash | `largeHeap=true` sudah ada; pantau via dash; kurangi handler tier2 aktif |

### 9.6 PERTANYAAN DISKUSI (dibedah bersama di chat)

1. **Reproducible vs fungsional** — target FASE 2: wajib dex sha256 `32b47c2f…` sama seperti v05, atau cukup fungsional setara?
2. **Keystore** — buat `serverkernel-v4` baru, atau pakai `serverkernel-v2` dari history?
3. **zone-static/game** — catatan commit lama menyebut "14 JS build-kita"; catatan zona bilang "isi final dibangun dari paket client asli — NOL byte buatan kita". Sebelum klaim 1:1 penuh: apakah perlu audit byte terhadap paket client asli (`pschent_full_tired.bin` dkk.)?
4. **Hot-update** — pertahankan `versionbin` 110/11220 (resource dari APK, NOL download) atau siapkan jalur `all.zip` untuk update resource oleh owner?
5. **Datapoint dashboard** — fitur owner mana yang masuk FASE 5 (reset save pemain, multi-server, export/import zona, dsb.)?
6. **Distribusi** — APK final disebar apa adanya (sideload) atau perlu rename package per-kebutuhan?

---

*Dokumen dibuat dari bedah langsung APK: manifest (AXML), bytecode
`classes.dex` (androguard), hexdump asset, native lib, DAN audit git history
(sumber kernel v05). Klaim yang tidak bisa dibuktikan ditandai [RANCANGAN].*
