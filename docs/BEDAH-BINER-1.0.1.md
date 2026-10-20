# BEDAH BINER 1.0.1 — Bukti Fisik & Dasar Rebuild 1.0.2 (SDK)

Sumber: `DB-Local-1.0.1.apk` dari GitHub release tag 1.0.1
(125.262.811 B, SHA-256 `a946ef6cc2d710e3b8812d836f654da42ec1f572370ef9f715e7ba3487c70b8e` — cocok dgn catatan release).
Dekompilasi: jadx 1.5.1. Tanggal bedah: 2026-10-20.

## 1. APA YANG BERUBAH DARI APK ASLI (level biner)

| File | Asli | 1.0.1 | Status |
|---|---|---|---|
| AndroidManifest.xml | 15.232 B | 15.232 B | **byte-identik** (`4399ebd9…`) |
| classes.dex | 6.697.104 B | 6.697.160 B | **+56 byte** — satu hook |
| classes2.dex | (tidak ada) | 6.020 B | **file BARU** — 2 kelas saja |
| assets/config_BS.properties | configus+myqcloud | 4 EntryPoint → `http://127.0.0.1:28110/bs/db/android` | diganti (base64, 4 kunci) |
| assets/QHinfo | `http://qsdk.t4game.com` | `http://qsdk.t4game.com` | **TIDAK disentuh di 1.0.1** |
| assets/mirror/** | — | 23 file | BARU: 15 JS + html + manifest + 5 upgrade + setting bin |

Kesimpulan: pola 1.0.1 = patch **minimal**. Hook 56 byte + dex mini 6 KB. Kode game, manifest,
engine tidak tersentuh. classes.dex hasil 1.0.1 bisa langsung dipakai ulang untuk 1.0.2.

## 2. MESIN PENGGERAK 1.0.1 (dari biner, bukan asumsi)

### Hook (classes.dex)
`com.zhuhuan.game.LaunchActivity.onCreate()`:
```java
super.onCreate(bundle);
MiniServer.start(this);        // ← baris pertama, SEBELUM setContentView
setContentView(R.layout.launch_new);
```
Import di dex: `Lcom/db/local/MiniServer;` (bukti string classes.dex).

### classes2.dex (6.020 B) — `com.db.local.MiniServer` + `ConnRun`
- `start(ctx)`: guard `started`, simpan ctx, `bootstrapSync`, lalu thread daemon.
- `bootstrapSync`: deadline **8 detik**; salin `assets/mirror/**` →
  `getExternalFilesDir(null)/server/**` via **`.tmp` + renameTo**, skip file yang sudah ada.
- `run()`: `ServerSocket.bind("127.0.0.1", 28110)` + `setReuseAddress(true)`;
  1 thread daemon per koneksi (`ConnRun` → `handle`).
- `handle()`: SoTimeout 8 s; baca request line + header sampai baris kosong;
  **body POST TIDAK dibaca/dibuang eksplisit** (aman krn respons file lokal);
  `parsePath`: buang `?query`, buang `/` awal, **reject `..`**;
  `resolve`: file `server/<path>` dulu → fallback `assets/mirror/<path>`;
  `respond`: `HTTP/1.1 200 OK` + CT + Content-Length + `Connection: close`.
- `mimeFor`: .json→application/json, .html→text/html, .js→application/javascript,
  .version→text/plain, selain itu octet-stream.
- **Tidak ada proxy sama sekali di 1.0.1** — path tak dikenal = 404.

### Mirror 23 file — 1:1 dgn repo `server/`
`index-native.html` (10.792 B), `manifest.json` (533 B), 15 JS (6.757.277 B),
`upgrade.json`(130 B), `base.version`(4), `resource.version`(5), `size.json`(105),
`all.zip` **19.385.098 B** — semua **byte-identik** dengan `server/` repo.
Pengecualian `setting_BS_Android.bin`: 711 B (asli) → **692 B** (mirror 1.0.1) —
hanya `url` & `update` diflip ke `http://127.0.0.1:28110/…`; `loginServer` TETAP
`https://login.popoh5.com`; struktur JSON & field lain byte-preserving (XOR "DragonBall").

## 3. JALUR SDK — TERBUKTI DARI DEX (dasar TAHAP 2)

### Pembaca QHinfo (satu-satunya sumber base URL SDK)
`QuickGameManager.initThirdDataSDK` (smali):
```
r7 = asset "QHinfo"
if (r7.isEmpty()) skip
com.quickgame.android.sdk.bindcommunist.cointhreat.seventygenom[0] = r7
```
Array `seventygenom = {""}` — grep seluruh dex: **tidak ada penulis lain**
(init respons tidak menimpanya).

### Eksekutor HTTP (`bindcommunist.cointhreat.cointhreat(String,Map)`)
```java
String url = String.format("%s%s", strArr[i2], endpoint);   // base + "/v1/…"
Log.d("QGConnectManager", "post request===" + url);
POST, CT application/x-www-form-urlencoded, connectTimeout 8000
body = "sv=v2" + "data=" + Base64(JSON perangkat) + "sign=" + MD5   // QGHttpRequestUtils
```
- Syarat sukses: **`getResponseCode() == 200`** lalu parse JSON dari InputStream.
  **Content-Type tidak dicek** (tetap dipertahankan `text/json` demi kesetiaan).
- Gagal → percobaan ke-2 (timeout 5 s) → rotasi base (array 1 elemen → berhenti) → `null`.

### Konsekuensi (jawaban "gimana ini akan work?")
1. `QHinfo` = `http://127.0.0.1:28110` (**22 B → 22 B**, panjang persis sama)
   → SEMUA `/v1/*` mendarat di MiniServer dgn path `v1/…` (parsePath sudah cocok).
2. Mirror file berarti: `mirror/v1/system/getNotice`, `mirror/v1/system/getAgreement`
   (TANPA prefiks `qsdk/` — QHinfo tanpa suffix path).
3. Endpoint lain (`init`, `registerVisitor`, `loginchecknative`, dst.) harus **di-proxy
   verbatim** ke `qsdk.t4game.com` — karena responsnya mengandung data per-pemanggil
   (`requestIp`, `countryCode`, uid, token). Proxy jalan DI PERANGKAT → server asli
   tetap melihat IP perangkat sendiri. **Body POST wajib di-drain** sebelum forward.
4. Mirror hanya boleh utk konten statis murni: getNotice (55 B) & getAgreement (188 B)
   — terbukti **byte-identik antar dua hari** panen live (19 Okt) → lolos uji kelayakan.
   `init` (766 B, ada requestIp/countryCode) **tidak di-mirror** → proxy.

## 4. PERUBAHAN 1.0.2 (TES1) — SDK, berbasis bukti di atas

| # | Item | Aksi | Basis bukti |
|---|---|---|---|
| 1 | classes.dex | **TIDAK disentuh** (pakai milik 1.0.1) | hook sudah ada (+56 B) |
| 2 | classes2.dex | **v3**: MiniServer+ConnRun + drain body POST + rute `v1/` mirror-or-proxy verbatim ke `qsdk.t4game.com` + mime `text/json` utk `v1/` | §2, §3 |
| 3 | assets/QHinfo | `http://qsdk.t4game.com` → `http://127.0.0.1:28110` (22→22 B) | §3.1 satu-satunya penulis base URL |
| 4 | mirror/v1/system/ | + getNotice 55 B, getAgreement 188 B, CT `text/json` | panen live 19 Okt, byte-identik |
| 5 | mirror lain | tetap 23 file 1.0.1 (sudah 1:1) | §2 |
| 6 | config_BS.properties, setting bin, manifest, game | **tidak disentuh** | pola terbukti |
| 7 | Re-sign + zipalign (uber-apk-signer) | wajib | pola 1.0.1 |

### Gerbang verifikasi perangkat (1.0.2)
- logcat: `客户端配置文件服务器地址` → `CurrentMode: 1` → `bin文件解压完成` (layer config lokal)
- logcat: `post request===http://127.0.0.1:28110/v1/system/init` (marker dex QGConnectManager)
- MiniServer log: `200 v1/system/getNotice (mirror)`, `PROXY 200 v1/user/registerVisitor`
- Login SDK sukses → masuk game → label **1.0.2**

### Urutan proxy di MiniServer v3
```
handle():
  baca request line + header (Content-Length)
  path = parsePath(...)
  if path diawali "v1/":
      body = baca Content-Length byte dari socket      // drain — wajib
      if mirror/v1/<path> ada  → 200, text/json, byte mirror
      else                     → forward verbatim ke qsdk.t4game.com/<path>
                                  (POST, CT & body persis), balas status+body apa adanya
  else: (perilaku 1.0.1 — resolve file mirror)
```
Fallback jika server SDK mati total (fase akhir, bukan 1.0.2): 404 → client SDK
sudah terbukti menerima `null` tanpa crash (init → default; login → gagal bersih).

## 5. KLARIFIKASI: kenapa QHinfo TANPA path (dan itu sesuai aslinya)

Bukti byte (hexdump asli & 1.0.1 identik):
```
QHinfo = 22 byte, persis: http://qsdk.t4game.com   (tanpa newline, tanpa path)
```
- Config lain memang ada path-nya — karena **aslinya memang begitu**:
  `EntryPoint1 = https://configus.sjmobilegame.com/bs/db/android` (path `/bs/db/android`
  bagian dari nilai asli), `update = …/bs/upgrade`, `url = …/bs/index-native.html`.
- QHinfo **sejak aslinya TANPA path** — pilihan pembuat APK asli. Path `/v1/…` BUKAN
  bagian config; ia ditulis di kode dex (34+ call site `cointhreat("/v1/…")`) dan
  ditempel di belakang base saat runtime: `String.format("%s%s", base, endpoint)`.

Jadi flip mirror hanya mengganti HOST, struktur path tidak diubah:
```
asli : http://qsdk.t4game.com  + /v1/system/init  →  qsdk.t4game.com/v1/system/init
local: http://127.0.0.1:28110 + /v1/system/init  →  127.0.0.1:28110/v1/system/init
                                                     parsePath → v1/system/init
                                                     mirror/v1/system/getNotice
```
Struktur path lokal = identik 1:1 dengan layout server asli → audit gampang,
nol transformasi tambahan.

Alternatif menambah path (`http://127.0.0.1:28110/qsdk`) secara mekanis juga jalan
(parsePath akan menghasilkan `qsdk/v1/system/init`, mirror di `mirror/qsdk/v1/…`),
tetapi itu mengarang struktur yang tidak ada di asli dan merusak kesetiaan 1:1 —
ditolak sesuai prinsip "tanpa merubah isi".

Pembaca QHinfo di seluruh APK: HANYA SATU (`QuickGameManager.initThirdDataSDK`).
Tidak ada hardcode `qsdk`/`t4game` lain di dex, tidak ada di native lib (.so),
tidak ada di asset lain → satu flip ini menutup seluruh jalur SDK.

## 6. LOKASI FISIK FILE SDK LOKAL ("path local ditaruh di mana")

QHinfo tanpa path TIDAK berarti file tanpa lokasi. Path request datang dari KODE DEX
(`/v1/…`), dan file mirror ditaruh MENGIKUTI path itu:

```
APK 1.0.2 — assets/mirror/
├── bs/
│   ├── db/android/setting_BS_Android.bin  692 B   ← configus…/bs/db/android/…
│   ├── index-native.html                  10.792 B← dragonh5cdn…/bs/…
│   ├── js/*.js                            15 file ← dragonh5cdn…/bs/js/…
│   └── manifest.json                      533 B
├── upgrade/
│   ├── upgrade.json 130 B, base.version 4 B, resource.version 5 B,
│   ├── size.json 105 B, all.zip 19.385.098 B       ← dragonh5cdn…/bs/upgrade/…
└── v1/                                     ★ BARU 1.0.2 ← qsdk.t4game.com/v1/…
    └── system/
        ├── getNotice      55 B
        └── getAgreement   188 B
```

Di perangkat, bootstrapSync (8 dtk pertama) menyalin mirror →
`/sdcard/Android/data/com.db.local/files/server/` (struktur sama persis).
`resolve()`: `files/server/<path>` dulu → fallback `assets/mirror/<path>` →
kalau `v1/…` dan tidak ada file → PROXY verbatim ke qsdk.t4game.com → kalau
bukan v1/ → 404.

Hasil simulasi resolusi (logika parsePath+resolve persis dex 1.0.1):
```
/v1/system/init               → v1/system/init               → PROXY (tidak di-mirror)
/v1/system/getNotice          → v1/system/getNotice          → mirror (perangkat dulu, lalu APK)
/v1/system/getAgreement       → v1/system/getAgreement       → mirror (perangkat dulu, lalu APK)
/v1/user/registerVisitor      → v1/user/registerVisitor      → PROXY
/bs/db/android/setting…bin    → bs/db/android/setting…bin    → mirror
/bs/index-native.html?v=…     → bs/index-native.html         → mirror
/upgrade/all.zip              → upgrade/all.zip               → mirror
```
Kesimpulan: satu direktori `mirror/` = peta URL server asli 1:1. QHinfo cukup
host saja — pembagian rute dilakukan oleh KEADAAN FILE di mirror (ada file =
lokal; tidak ada = proxy).

## 7. KONFIRMASI: config SDK mendarat di Android/data (pola sama 1.0.1)

Dari dex 1.0.1 (kode sama dipakai 1.0.2):
- `bootstrapSync`: `getExternalFilesDir(null)` + subfolder **`server`** → salin
  `assets/mirror/**` ke situ (deadline 8 dtk, skip file yang sudah ada, .tmp+rename).
- `resolve`: baca **`files/server/<path>` DULU** → baru fallback `assets/mirror/<path>`.

Lokasi perangkat (package com.db.local):
```
/storage/emulated/0/Android/data/com.db.local/files/server/
├── bs/db/android/setting_BS_Android.bin      ← sudah jalan di 1.0.1
├── bs/index-native.html + js/ (15) + manifest.json
├── upgrade/upgrade.json, base.version, resource.version, size.json, all.zip
└── v1/system/                                 ← BARU 1.0.2
    ├── getNotice      (55 B)
    └── getAgreement   (188 B)
```
APK (`assets/mirror/`) tetap master copy — Android/data adalah salinan runtime.
Perilaku praktis (dari kode): file yang diedit user di Android/data TIDAK
ditimpa saat boot; file yang dihapus → disalin ulang dari APK; file baru buatan
user di situ → langsung disajikan. Cocok utk audit/edit manual tanpa rebuild APK.
