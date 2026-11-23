# PANDUAN FOLDER `sdk/` — CONFIG PENUH TANPA REBUILD APK

> Permintaan owner: *"tanpa perlu rebuild apk .. anda buat folder sdk isinya nanti
> full config yg akan saya download dan tempelkan ke android/data/sdk"*.
>
> **KOREKSI OWNER 23 Nov (Bagian 36 readme)**: `init.json` lama hanya 2 field
> (`payTypes`+`loginTypes`) padahal SERVER ORIGINAL mengirim SEMUA field di
> bawah — semua field itu DIPARSE SDK (bukti smali §5). Folder `sdk/` repo
> sekarang = **mirror penuh respons init original**.
>
> **KOREKSI v2 (Bagian 37 readme)**: crash device 23 Nov 21:49 —
> `NullPointerException model.d.worktopichardtails` di `HWLoginActivity.singersmooth()`
> — membuktikan klaim lama "field hilang = tidak crash" SALAH. APK v2:
> seed DEFAULT di dex = mirror PENUH (SdkConfig.DEFAULT_INIT) + preset E1/E2/E3
> menahan struktur penuh. Fresh install v2 = langsung aman TANPA tempel;
> folder `sdk/` repo tetap sumber kebenaran bila file di device rusak/dihapus.

## 1. LOKASI PERSIS DI DEVICE (BUKTI KODE, JANGAN KESALAH TEMPAT)

Bukti `LocalServerStarter.java:47-49`:
```java
extBase    = ctx.getExternalFilesDir(null);   // /storage/emulated/0/Android/data/com.db.local/files
base       = extBase.getParentFile();         // /storage/emulated/0/Android/data/com.db.local
serverRoot = new File(base, "server");        // /storage/emulated/0/Android/data/com.db.local/server
```
Jadi path lengkap folder config:

```
/storage/emulated/0/Android/data/com.db.local/server/sdk/
```

Struktur penuh `server/` di device (yang mana milik siapa):

```
Android/data/com.db.local/server/
├── bs/      <- MILIK APK (ekstraksi assets tiap boot; JANGAN diedit)
├── sdk/     <- MILIK OWNER (config; TIDAK pernah disentuh ekstraksi APK)
│   ├── profile.json
│   ├── v1/system/init.json
│   ├── v1/user/ckRegVistor.json
│   ├── fallback.json
│   ├── errors.json
│   ├── QHinfo
│   └── data/            <- db.sqlite AKUN (dibuat server; jangan ganti-ganti)
└── logs/    <- log NDJSON + crash-*.txt (dashboard 127.0.0.1:4000)
```

## 2. CARA PASTE (TANPA PC, TANPA REBUILD)

1. Download folder `sdk/` dari repo ini (GitHub: buka folder `sdk` -> tiap file
   -> "Download raw file"; atau download ZIP repo dan ambil folder `sdk/`-nya).
2. Buka file manager di HP (yang bisa akses `Android/data`, mis. file manager
   dengan akses storage penuh).
3. Masuk ke `Android/data/com.db.local/server/`.
4. **Bila folder `sdk/` belum ada** -> salin folder `sdk/` dari download ke situ.
   **Bila sudah ada** -> timpa FILE config-nya saja (jangan hapus folder `data/`).
5. Selesai. Tidak perlu restart: **server membaca ulang file config pada TIAP
   request** (bukti `SdkConfig.java:17` + `contract()` `:142-153`) — ulangi
   aksinya di game (mis. buka lagi layar login) dan config baru langsung berlaku.

## 3. ISI FOLDER + EFEKNYA (semua ada bukti di docs/SDK-STUDY.md)

| File | Efek |
|---|---|
| `profile.json` | Gerbang `/v1/system/init`: daftar `products` (30001), `verifySign`, `signSecret`, header PHP. Menambah productCode = edit array `products` |
| `v1/system/init.json` | **Mirror PENUH respons init server original** (isinya lihat §5). `loginTypes` menentukan UI login SDK: `["1"]` = email+TAMU (keputusan owner, struktur original), `["12"]` = TAMU saja, `["1","13"]` = replay asli (+Kode Migrasi). `payTypes` WAJIB ada (parser `model/e.smali:171`); `productConfig`+`version` juga diparse (`model/e.smali:166/:156`) |
| `v1/user/ckRegVistor.json` | Kontrak cek tamu (`isReged`) |
| `fallback.json` | Jawaban endpoint yang belum dipetakan (result:false 20000) |
| `errors.json` | Katalog error (dokumentasi live server asli) |
| `QHinfo` | CATATAN endpoint. PENTING: client membaca endpoint dari **assets/QHinfo di dalam APK** (bukti QuickGameManager.smali:66-75), jadi mengubah file ini TIDAK mengubah endpoint client — hanya rebuild APK yang bisa |

## 4. ATURAN AMAN (dari kode server — bukan asumsi)

1. **JSON wajib `{`...`}`** — kalau rusak, server otomatis seed ulang default dan
   mencatat log `SDK-CONFIG rusak` (SdkConfig.java:148). Editan hilang = tulis ulang.
2. **`init.json` wajib berisi `payTypes` + `loginTypes`** (SdkConfig.java:208-210).
3. **`QHinfo` wajib `http://`/`https://`, TANPA `/` di akhir** (SdkConfig.java:197-204).
4. **Jangan buat `index.txt`** — server menghapusnya otomatis tiap boot
   (SdkConfig.java:259-263; keluhan owner lama "berasa jadi sampah").
5. **Jangan sentuh `sdk/data/`** — itu DB akun (db.sqlite). Salin antar device =
   pindahkan akun, bukan config.
6. **`bs/` bukan tempat config** — file di sana ditimpa ekstraksi APK tiap boot.
7. Edit lebih praktis dari browser HP: panel `http://127.0.0.1:4000/sdkconfig`
   (edit file yang sama + preset E1/E2/E3 + tabel akun) — folder `sdk/` ini adalah
   versi OFFLINE dari panel itu.
8. **Preset E1/E2/E3 di panel AMAN sejak APK v2** — preset membangun init.json
   struktur PENUH (hanya loginTypes berubah, sisanya persis original — bukti
   `SdkConfig.presetInitJson` v2 = `DEFAULT_INIT.replace(loginTypes)`).
   Di APK v1 preset masih menimpa dengan format minimal — pakai v2.
9. **Bila file `init.json` dihapus/dirusak** di device -> server seed ulang
   DEFAULT dari dex. Di APK **v2** default itu = MIRROR PENUH original (sama
   dengan file repo). Di APK v1 default masih minimal — pakai v2 atau tempel ulang.

## 5. PERBANDINGAN — SERVER ORIGINAL vs KITA (koreksi owner 23 Nov)

Respons `/v1/system/init` server original (bukti: JSON persis dari owner +
replay sukses 23 Nov, readme Bagian 26.3) vs yang kita kirim:

| Field `data` | ORIGINAL | KITA DULU (SALAH) | KITA SEKARANG |
|---|---|---|---|
| `payTypes` | `[]` | `[]` ✔ | `[]` |
| `loginTypes` | `["1"]` (owner; replay asli 26.3 pernah `["1","13"]`) | `["1"]` ✔ | `["1"]` |
| `first_install` | `1` | **TIDAK ADA** ✗ | `1` |
| `countryCode` | `"HK"` | **TIDAK ADA** ✗ | `"HK"` |
| `productConfig` (10 field) | ada semua: `guestShowBind:"1"`, `isShowFloat:"0"`, `useSms:"0"`, `useServiceCenter:"0"`, `single_play:"0"`, `adZhifuSwt:"0"`, `initCZurl:""`, `serviceInfo:""`, `logo:""`, `title:""` | **TIDAK ADA** ✗ | ada semua, persis |
| `version` (6 field) | semua `"empty"`: `versionNo`, `versionName`, `versionUrl`, `updateTime`, `isMust`, `updateTips` | **TIDAK ADA** ✗ | semua `"empty"`, persis |

**Bukti field-field itu DIPARSE SDK (decode fresh APK original, nol asumsi):**

- `model/e.smali` (parser respons init) — `:171` `"payTypes"`, `:195`
  `"loginTypes"`, `:166` `"productConfig"`, `:156` `"version"` + field lain
  (`:152-:207`: downloadUrl/title/lightPackage/payFast/otherpayType/requestIp/
  showDiscount/serverInfoData/showShare/isNotGift/removeEmail/deleteAccount/
  serverInfo/nodes). Ekstraksi pakai `JSONObject.has()` + `getJSONObject()` —
  di dalam parser memang dijaga `has()`, TAPI PEMAKAI FIELD-NYA TIDAK DIJAGA:
  **CRASH NYATA terbukti 23 Nov 21:49** (laporan crash device):
  `HWLoginActivity.singersmooth()` (HWLoginActivity.smali:4111-4136, dipanggil
  runnable `awfulcongratulatepunishment.run` via Handler) membaca
  `e.singersmooth` (objek model/d hasil parse productConfig) lalu
  `.worktopichardtails` TANPA null-check — init tanpa productConfig = NPE PASTI.
  (Klaim lama "field hilang = tidak crash" SALAH — dikoreksi.)
- `model/d.smali` (parser `productConfig`) — SEMUA 10 field owner diparse:
  `:74` adZhifuSwt, `:76` initCZurl, `:78` single_play, `:80` guestShowBind,
  `:82` isShowFloat, `:84` serviceInfo, `:86` useSms, `:88` logo, `:90` title,
  `:92` useServiceCenter. Peta field obfuscated (bukti iput per blok):
  `guestShowBind` -> **`worktopichardtails`** (`:296-:331` — inilah field yang
  NULL saat crash), `isShowFloat` -> `feedtonight` (`:243-:280`),
  `useServiceCenter` -> `cointhreat` (`:63-:89`), `single_play` ->
  `pursetruechild` (`:347-:382`), `initCZurl` ->
  `reweavingsiamesedpropertylessnesses` (`:398-:433`), `adZhifuSwt` ->
  `dishbesideshockey` (`:449-:482`), `useSms` -> `nationalcommunitymissing`
  (`:170-:189`), `serviceInfo` -> `ironoriginhoblike` (String `:193-:210`),
  `logo` -> `seventygenom` (String `:93-:108`), `title` -> `singersmooth`
  (String `:124-:154`). Nilai dibandingkan dgn konstanta perilaku `"1"`
  (`:114`), awalan `"http"` (`:190`), `"0"` (`:309`) — NILAI server original
  yang menentukan cabang UI.
- `model/g.smali` (parser `version`) — `:59` konstanta literal `"empty"`
  (sentinel milik SDK SENDIRI — itulah kenapa server original mengirim
  `"empty"`), `:74` versionNo, `:118` versionName, `:152` versionUrl,
  `:186` isMust, `:240` updateTips, `:276` updateTime.
- `first_install` — grep seluruh smali APK = **0 hit** (tidak diparse client;
  tetap dikirim karena mirror asli, bukan karena dipakai).
- `countryCode` — dikirim client sebagai PARAM REQUEST init
  (psalmicvolleyball/singersmooth.smali:171); di RESPONS ikut dikirim server
  asli — kita mirror apa adanya.

Isi `init.json` sekarang (persis bukti owner, bisa dilihat langsung di file):
`payTypes []`, `loginTypes ["1"]`, `first_install 1`, `countryCode "HK"`,
`productConfig` 10 field, `version` 6 field `"empty"`.

**loginTypes**: `["1"]` = keputusan owner (form email + TAMU). Server asli
(23 Nov, 26.3) mengirim `["1","13"]`. Ganti kapan pun lewat file/panel —
struktur field lain jangan dikurangi.
