# 🔍 Dokumentasi Riset: Bongkar & Bedah DB-Local.apk

> **Repo**: `github.com/anztdree/db-apk` — dokumentasi lengkap hasil bedah APK, alur aplikasi, dan komunikasi server ↔ aplikasi.
> **Status**: ✅ Bongkar APK selesai · ✅ **Alur depan (boot → layar login) terbedah TOTAL, langkah-per-langkah** (§4) · ✅ Protokol server↔aplikasi terbedah & teruji live
> **Rencana standalone mode**: ✅ tersusun ulang **bertahap per fase alur boot** (§9) — setiap tahap diverifikasi sebelum lanjut, mengurangi risiko force close & salah config.

> ⚠️ **Disclaimer**: Dokumen ini murni untuk tujuan riset/preservasi game yang backend-nya mendekati mati.
> Semua kunci/rahasia di bawah **memang tertanam di dalam klien** (bukan hasil serangan ke server).
> Jangan gunakan untuk kegiatan ilegal (cheat, bot ekonomi, penyalahgunaan server).

> ## 📦 STATUS RILIS — **1.0.1 STABIL** (rilis default)
> **`DB-Local-1.0.1.apk`** = build test9 (byte-identik; SHA-256 `a946ef6cc2d710e3b8812d836f654da42ec1f572370ef9f715e7ba3487c70b8e`) — **teruji di perangkat** (HAR+logcat): boot → unduh & ekstraksi → layar login → **login SDK sukses** → engine Egret jalan.
> Arsitektur: **redirection murni** — endpoint CDN diarahkan ke mini server `127.0.0.1:28110` via mekanisme config aplikasi sendiri (`config_BS.properties` + setting bin); konten file 1:1 (23 file, gerbang siap-dulu); APK & pola client-server asli 100% utuh.
> Masih ke internet (perilaku asli, layanan masih hidup): `login.popoh5.com:510/:610`, `qsdk.t4game.com`, telemetry → dijadwalkan jadi **1.0.2** (rencana: [`docs/RENCANA-1.0.2.md`](docs/RENCANA-1.0.2.md)).

---

## Daftar Isi

1. [Identitas Aplikasi](#1-identitas-aplikasi)
2. [Arsitektur Teknis](#2-arsitektur-teknis)
3. [Sistem Enkripsi 3 Lapis (Semua Terpecahkan)](#3-sistem-enkripsi-3-lapis-semua-terpecahkan)
4. [Alur Aplikasi Mendalam (End-to-End, Fase 0–9)](#4-alur-aplikasi-mendalam-end-to-end)
   - [4.10 Inventaris Config Lengkap (boot → layar login)](#410-inventaris-config-lengkap-boot--layar-login)
   - [4.11 Peta Titik Gagal — kapan force close, kapan cuma hang](#411-peta-titik-gagal--kapan-force-close-kapan-cuma-hang)
5. [Bedah Komunikasi Server ↔ Aplikasi](#5-bedah-komunikasi-server--aplikasi)
6. [Uji Protokol Live (Bukti Nyata)](#6-uji-protokol-live-bukti-nyata)
7. [Hasil Ekstraksi: Kode, Data, Aset](#7-hasil-ekstraksi-kode-data-aset)
8. [Infrastruktur Server & Status](#8-infrastruktur-server--status)
9. [Rencana Standalone Mode](#9-rencana-standalone-mode)
10. [Artefak & Tools Riset](#10-artefak--tools-riset)
11. [Kesimpulan](#11-kesimpulan)

---

## 1. Identitas Aplikasi

| Properti | Nilai |
|---|---|
| Nama app | **DB - Local** |
| Package | `com.db.local` |
| Versi | 1.0.1 (versionCode 1) |
| SDK | minSdk 21 (Android 5.0), targetSdk 30 (Android 11) |
| Main Activity | `com.zhuhuan.game.LaunchActivity` |
| Nama rilis internasional | **"Super Fighter Idle"** (dari halaman FB resmi di kode) |
| Tema | Idle RPG kartu bertema **Dragon Ball** |
| Bahasa | EN, FR, KR, CN, TW, DE, VI |
| Tanggal build aset | Desember 2021 |
| Ukuran APK | 105 MB (2.522 file aset di `assets/game/`) |

---

## 2. Arsitektur Teknis

```
┌────────────────────────────────────────────────────────────┐
│                     APK (com.db.local)                     │
│                                                            │
│  LaunchActivity ──► ambil config server ──► HOT UPDATE     │
│       (Java, obfuscated kata-acak)                         │
│         │                                                  │
│         ▼                                                  │
│  MainActivity ──► Egret Native Runtime (libegret.so 17MB)  │
│         │           └─ embed V8 JavaScript engine          │
│         ▼                                                  │
│  index-native.html ──► manifest.json ──► 15 file JS game   │
│         │       (JS polos di CDN/cache — onepiece hanya    │
│         ▼          di dalam paket .bin bawaan APK)         │
│  [ GAME JS ] main.min.js 4.7MB (1.897 class)               │
│              battlelogic.min.js (simulasi battle lokal)    │
│              socket.io.min.js v2.1.0 (jaringan)            │
│         │                                                  │
│         ▼                                                  │
│  Jembatan JS↔Native: egret.ExternalInterface               │
│  (startGame, pei/pembayaran, report2Third, refresh, dll)   │
│                                                            │
│  SDK pihak-3: QuickGame, Google Billing, Facebook Login,   │
│  AppsFlyer, Adjust, Firebase FCM, TiViCloud (libgavesec)   │
└────────────────────────────────────────────────────────────┘
```

**Struktur DEX**: kode Java di-obfuscate dengan nama kata-acak (`cointhreat`, `singersmooth`, `reweavingsiamesedpropertylessnesses`, ...).
- `com.zhuhuan.game.*` — LaunchActivity (updater), MainActivity (bridge)
- `com.quickgame.android.sdk.*` — SDK publikasi
- `cointhreat.*/singersmooth.*` — util (termasuk dekriptor resource `customreading.java`)

---

## 3. Sistem Enkripsi 3 Lapis (Semua Terpecahkan)

### 3a. Enkripsi #1 — Konfigurasi server (XOR)

`config.properties` di APK berisi `EntryPoint1..3` = Base64 URL server konfigurasi:

| Channel | URL |
|---|---|
| default | `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` |
| BS (aktif) | `https://configus.sjmobilegame.com/bs/db/android` |
| BS cadangan | `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` (Tencent COS) |

File `setting_<channel>_Android.bin` dienkripsi **XOR dengan kunci berulang `"DragonBall"`**
(sumber: `LaunchActivity.worktopichardtails()`):
```java
bArr[i5] = (byte) (bArr[i5] ^ key[i5 % keyLength]); // key = "DragonBall".getBytes()
```

Hasil dekripsi `setting_BS_Android.bin`:
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
  "clientParams": { "hideList": ["1.99","2.99","14.99","59.99","89.99"], ... }
}
```

### 3b. Enkripsi #2 — Kode game (XXTEA varian "onepiece")

Format file terenkripsi:
```
[16B acak][16B magic "onepieceencrypts"][16B kunci ter-XOR][payload]
```
- Kunci asli = `bytes[32:48] XOR "onepiecedecryptk"`
- Payload = **XXTEA varian** (delta `0x9E3779B9`, satu pass, fungsi MX kustom)
  — sumber: `cointhreat...customreading.java` (`nationalcommunitymissing()` = dekripsi)
- Dekriptor Python dibuat dengan port eksak algoritme di atas (simulasi `uint32` Java) —
  **berfungsi 100%** (artefak riset lokal, lihat §10)

### 3c. Enkripsi #3 — Kunci runtime native

`NativeKey.getKey()` (JNI, `libegret.so`) = **`5ce4b0e76844477`**
(ditemukan via disassembly capstone — string teks biasa di section rodata)

---

## 4. Alur Aplikasi Mendalam (End-to-End)

### Fase 0 — LaunchActivity dimulai (channel `"BS"` hardcoded)

`LaunchActivity.onCreate()` (terverifikasi di DEX):

```java
setContentView(R.layout.launch_new);
getWindow().addFlags(FLAG_KEEP_SCREEN_ON);   // layar tetap nyala
mActivity = this;
// channel dikode-kan PERMANEN di dalam APK:
cointhreat...seventygenom.hardlinerspare.cointhreat("BS");   // channel = "BS"
// siapkan singleton mSdkParams (wadah hasil setting bin)
// layar "health notice" DILEWATI: kondisinya "heishi" != "jiuwan" (selalu true)
pursetruechild();   // → minta izin → mulai thread config
```

**Alur izin & thread (detail penting):**
1. Diminta izin `WRITE_EXTERNAL_STORAGE` (via permission manager).
2. **Baik diberikan MAUPUN ditolak**, kode tetap lanjut: keduanya men-start
   `new Thread(...)` yang menjalankan alur config (Fase 1–5) di **background thread** —
   jaringan tidak pernah diblok di UI thread.
3. Seluruh pembaruan UI layar loading lewat `runOnUiThread` (pesan dari `strings.xml`):
   `read_cfg` (membaca config) → `decompressing` (membongkar) → `check_update` (cek update).
4. Tekan BACK saat loading → dialog konfirmasi keluar.

> Karena channel = `"BS"`, Fase 1 membaca **`assets/config_BS.properties`**
> (bukan `config.properties` default).

### Fase 1 — Baca config bawaan APK (`assets/`)

#### Tabel file config yang DIBAWA di dalam APK (`assets/`)

| File | Isi | Peran |
|---|---|---|
| `config.properties` (channel default) | `EntryPoint1` = Base64 `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | kandidat server config (varian `_debug` juga ada untuk tiap EntryPoint) |
| `config_BS.properties` (channel BS/aktif) | `EntryPoint1` = Base64 `https://configus.sjmobilegame.com/bs/db/android`<br>`EntryPoint2` = Base64 `https://configus-1257066086.cos.na-siliconvalley.myqcloud.com/bs/db/android` | kandidat server config (2 URL fallback) |
| `supplierconfig.json` | `{"supplier":{"vivo":{"appid":"100215079"}, "xiaomi":{}, "huawei":{}, "oppo":{}}}` | app ID SDK OEM China |
| `QHinfo` | teks: `http://qsdk.t4game.com` | endpoint QuickGame SDK |
| `runtime-dex.jar` | runtime Egret Native (dex terpisah) | engine runner |
| `game/infoinfo.bin` | teks: `voyage_fancy_radius\|pschent_full_tired` | nama paket kode bawaan (dipisah `\|`) |
| `game/base.version` | teks: `110` | versi kode bawaan |
| `game/resource.version` | teks: `11220` | versi resource bawaan |
| `game/vvcc.bin` | **onepiece-encrypted** → JSON 1.944 entri `{path_asli: path_obfuscated}` | peta nama file resource (contoh: `/image/en/bitMapFont/activity1.png → /absorb_sialoid/missile_eighth.png`) |
| `game/vvcc_tc.bin` | **onepiece-encrypted** → JSON 139 entri | peta nama file (subset, dibaca bila `infoinfo.bin` mengandung `\|`) |
| `game/pschent_full_tired.bin` (9,4 MB) | **onepiece-encrypted** → ZIP kode game lengkap | paket kode #1 |
| `game/voyage_fancy_radius.bin` (1,2 MB) | **onepiece-encrypted** → ZIP build kedua | paket kode #2 |
| `game/*` lainnya (33 file total) | PNG/JPG polos (nama acak), .data, .bin, .version | aset & metadata |

Cara kerja: loop `i = 1..3` membaca property `EntryPoint<i>` dari `config_<channel>.properties`, setiap nilai di-**Base64.decode** → daftar URL server config. Semua EntryPoint **dicoba berurutan** (EntryPoint1 → 2 → ...) hingga unduhan sukses.

### Fase 2 — Unduh `setting_BS_Android.bin` (wajib setiap kali app dibuka)

```
GET {EntryPoint}/setting_{channel}_Android.bin?rnd=<Math.random()>
  → https://configus.sjmobilegame.com/bs/db/android/setting_BS_Android.bin
retry maks 10×, timeout = 5 × n! detik  (5 s, 10 s, 30 s, 120 s, 10 mnt, 1 jam, ...)
→ XOR-decrypt kunci "DragonBall" → parse JSON → simpan ke mSdkParams
```

**Semantik kegagalan (penting untuk standalone!):**

| Kondisi | Perilaku kode | Efek ke user |
|---|---|---|
| Download gagal total (10× retry, timeout 5s→10s→30s→120s→…) | helper `return true` → **LANJUT** dengan field default | di kode, percobaan fallback **OBB** (`main.<versionCode>.com.db.local.obb`) disiapkan, tapi mode tetap default `0` → dialog **"check_network"** → berhenti di layar loading |
| Download sukses, dekrip/parse gagal (log `解析服务器配置文件错误`) | helper `return false` | percobaan fallback OBB → dialog **"unknown_error"** → berhenti |
| `com_db_local.bin` gagal/404 | diabaikan | app **tetap lanjut** |

> Artinya: **config server harus terjangkau di setiap peluncuran** — tidak ada cache
> setting bin offline. Ini titik kunci standalone mode (§9).

Selanjutnya config per-package:
```
GET {EntryPoint}/com_db_local.bin?rnd=...   (packageName "com.db.local" dengan "." → "_")
retry maks 3×
✦ VERIFIKASI LIVE: file ini 404 di server produksi → diabaikan dengan anggun, app lanjut
```

Prioritas kunci mode di JSON: `"Android<versionCode>"` (nested) > `"testMode"` (root).
Kasus nyata: root flat dengan `testMode: 1` (key `Android1` tidak ada).

#### Tabel lengkap field `setting_*.bin` (dibaca LaunchActivity → `mSdkParams`)

| Key JSON | Tipe | Disimpan sebagai | Fungsi |
|---|---|---|---|
| `Android<versionCode>` | objek | mode container | config per-versi app (nested); prioritas di atas testMode |
| `testMode` | int | mode | **1** = server produksi (`url`), **2** = server test (`tsUrl`/`tsLoginServer`), 3 = lewati hot update, 4 = URL TS debug |
| `webVer` | int | webVer | threshold: bila Android API ≥ nilai → pakai varian web (`webUrl`) |
| `nativeRender` | int | — | flag render native |
| `url` | string | gameUrlNative | **URL game yang dimuat runtime** (index-native.html) |
| `webUrl` | string | gameUrlWeb | URL varian web (H5 di browser) |
| `tsUrl` | string | testUrlNative | URL game server TEST |
| `tsWebUrl` | string | testUrlWeb | URL game server TEST varian web |
| `update` | string | updateUrl | base URL hot-update (`base.version`, `resource.version`, `size.json`, `upgrade.json`, `base.zip`, `all.zip`) |
| `forceBuild` | string | — | versi minimal yang dipaksa update |
| `noForceBuild` | string | — | pengecualian force update |
| `forceUrl` | string | — | URL force update |
| `checkTime` | int | checkTime | interval cek ulang config (detik) — nilai nyata: **300** |
| `tsLoginServer` | string | tsLoginServer | login server TEST — nilai nyata: `http://118.24.68.91:7003` |
| `loginServer` | string | loginServer | login server produksi — nilai nyata: `https://login.popoh5.com` |
| `loginKey` | string | loginKey | kunci tambahan login (dikirim ke JS saat mode non-SDK) |
| `loginPort` | int | loginPort | **port socket login NATIVE** — nilai nyata: **610** |
| `loginWebPort` | int | loginWebPort | port login versi web — nilai nyata: **510** |
| `productPrefix` | string | — | prefix produk IAP |
| `productIdMap` | objek | — | peta produk IAP ke SDK |
| `gameIcon` | string | — | ikon per-channel |
| `extra` | objek | — | data ekstra bebas |
| `clientParams` | objek | clientParams | diteruskan ke JS: `hideList` (harga IAP disembunyikan), `supportLang`, `gameIcon`, `battleAudio`, `sdkNativeChannel`, `showCurChannel` |
| `peiUrl` / `wpei` / `wpeiurl` | — | — | konfigurasi pembayaran (dibaca, belum dipakai runtime ini) |
| `blackWhiteL` / `blackWhiteLNetSegment` | string | — | daftar hitam/putih (maintenance/IP) |
| `currentNetIpUrl` | string | — | URL laporan IP jaringan |
| `unityGameSdkLogin` | bool | — | flag alur login SDK Unity |
| `checkGoogleTicketUrl` | string | — | verifikasi ticket Google Play |
| `gameLanguage` | string | — | bahasa paksa |
| `nativeUnzipJson` | bool | — | bila true: native kirim `jsonDecodeB64Map` (gzip) ke JS |

### Fase 3 — Dispatch mode (semantik persis dari kode)

Setelah setting terbaca, `LaunchActivity.dishbesideshockey()` memutuskan jalur berdasarkan `mode`:

| Kondisi | Perilaku |
|---|---|
| mode **0** (default/gagal) | dialog **"check_network"**, **BERHENTI** |
| mode **1** (`testMode:1` — kasus nyata) | alur penuh: ekstraksi → hot update → MainActivity |
| mode **2** (`testMode:2`, server uji) | seperti 1, tapi URL memakai varian `ts*`; native mengirim `loginServer` = string `"null"` ke JS |
| mode **3** | LaunchActivity **langsung lompat ke MainActivity** (tanpa ekstraksi, tanpa hot update) — TAPI `MainActivity` untuk mode 3 **tidak memanggil apa-apa** → **layar kosong total** (mode mati/terpakai untuk alur SDK tertentu saja) |
| mode **4** | ekstraksi paket kedua → MainActivity memuat URL TS hardcode — Base64 terenkode di MainActivity: `http://tool.egret-labs.org/Weiduan/game/index.html` (URL debug Egret!) |
| **IP filter cocok** (`blackWhiteL` / `blackWhiteLNetSegment` via `currentNetIpUrl`) | **LEWATI hot update** → ekstraksi paket **pertama** (`voyage_fancy_radius`) → langsung ke MainActivity |

> ⚠️ **KOREKSI PENTING UNTUK STANDALONE**: `testMode: 3` **BUKAN** opsi aman seperti dugaan awal —
> mode 3 menghasilkan **layar kosong** karena MainActivity tidak menginisialisasi apa pun,
> dan ekstraksi paket bawaan juga terlewat. Jalur aman tetap `testMode: 1` + server update
> milik sendiri yang menyajikan versi **sama** dengan bawaan APK (lihat §9).

### Fase 4 — Ekstraksi paket kode bawaan (mode 1/2) — kelas `DecodeUtil`

1. Baca `assets/game/infoinfo.bin` → `voyage_fancy_radius|pschent_full_tired` (teks polos)
   — bila ada `|`, pakai nama **setelah** tanda itu (`pschent_full_tired`) sebagai paket aktif.
2. Bila file tidak ada → `"info file not found"` → berhenti (dialog).
3. Cek OBB expansion: `main.<versionCode>.com.db.local.obb` di `getObbDir()`; bila ada → baca juga `vc_obb` (peta nama tambahan).
4. Dekripsi `vvcc.bin` (onepiece) → peta `{path_asli → nama_obfuscated}` (**1.944 entri**, sudah terbukti terdekripsi 100%) + baca `vvcc_tc.bin` (139 entri) bila `infoinfo.bin` mengandung `|`.
   Bila `vvcc.bin` gagal terbaca → `read_version_config_error` → berhenti.
5. **Ekstraksi (kelas `DecodeUtil`) — mekanika eksak:**
   - Flag sekali-jalan di SharedPreferences `decode_bin` → key `decodeBinFinish`:
     bila `true`, ekstraksi **dilewati** ("bin文件已经处理，无需再处理").
   - Sumber: `assets/game/<nama_paket>.bin` (contoh `game/pschent_full_tired.bin`, 9,4 MB).
   - Dekripsi **onepiece/XXTEA varian** (kelas dekriptor sama dengan kode game).
   - Tujuan: **dir lokal yang diturunkan dari URL game** —
     `<preloadDir>/https/dragonh5cdn.popoh5.com/bs/`
     (skema penamaan: buang nama file dari URL, `://` → `/`, `:` → `#0A` —
     konvensi yang dipakai juga oleh mesin Egret Native untuk memetakan URL→file lokal).
   - Isi paket (terverifikasi dengan dekriptor buatan sendiri) = ZIP **741 entri**: `js/*.js` (**14 file** —
     TANPA `ThinkingAnalyticsSDK`) + `resource/…` (`default.res.json`, `default.res-en.json`,
     `default.thm.json`, `gameEui.json`, `json/` **434 config**, `language/` **7 bahasa**,
     `assets/`).
     ⚠️ **TIDAK mengandung `index-native.html` maupun `manifest.json`** — kedua file
     bootstrap itu **tidak pernah ada di APK** dan selalu diambil dari URL game (server).
     Hash JS bawaan juga lebih lama dari yang ditunjuk `manifest.json` CDN
     (mis. bawaan `main.min_7eae4d6e.js` vs CDN `main.min_777039fc.js`) —
     unduhan update pertama (`all.zip`) yang menyediakan versi baru.
   - Sukses → set `decodeBinFinish=true`.
6b. Isi paket kedua `voyage_fancy_radius.bin` (terverifikasi) = ZIP **161 entri** — build
     **varian berbeda** (TypeScript/fairygui: `assets_ts/configs/*.json`, `fairygui.min`,
     `promise.min`, `rawinflate.min`, `main.min_8c078fc0.js`). Bukan mirror build utama —
     ini build cadangan untuk jalur maintenance/IP-filter.
7. Jalur IP-filter (Fase 3) memakai varian `z=true`: paket **pertama** (`voyage_fancy_radius`)
   diekstrak **langsung ke `preloadDir`** (tanpa sub-folder URL) dan **tanpa** men-set flag.

### Fase 5 — Hot update (cek versi & unduh patch) — pohon keputusan eksak

```
LOKAL  : <preloadDir>/<urlDir>/base.version & resource.version
         (fallback: assets/game/base.version = 110, resource.version = 11220)
REMOTE : GET {update}/base.version?rnd=...      (retry 3)
         GET {update}/resource.version?rnd=...  (retry 3)
→ remote berisi string "404" → dialog "Server base.version/resource.version is Not Exist" → berhenti
→ bandingkan dengan Integer.parseInt → remote bukan angka murni (mis. HTML error page)
   → NumberFormatException → tertangkap → dialog
   "Read Upgrade Config File Fail,Please Check NetWork！" → berhenti
```

Pohon keputusan unduh (persis dari kode `GameUpdateUtil`):

| Kondisi | Aksi |
|---|---|
| `baseVersionRemote > baseVersionLocal` (atau lokal kosong & remote ada) | antre `baseZipUrl = {update}/base.zip?v=<baseRemote>` (±31 MB) |
| `resVersionRemote > resVersionLocal` dan **lokal KOSONG** | antre `resZipUrl = {update}/all.zip?v=<baseRemote><resRemote>` (±19 MB) |
| `resVersionRemote > resVersionLocal` dan lokal ADA | baca `{update}/upgrade.json?rnd=` → pilih entry key `<resLocal>` (contoh `11389_11390.zip`); key tidak ada → fallback `all.zip` |
| semua sama | **tanpa download** → log `最新资源文件，无需更新` → lanjut MainActivity |

Eksekusi unduhan (urut): `base.zip` dulu → (bila ada) `resZip` → diekstrak dari `tmp.zip`
ke dir lokal; bila unduhan gagal → dialog `Download Base Zip Fail,Please Check NetWork！` /
`Download Resource Zip Fail,Please Check  NetWork！` → berhenti.
Setelah ekstraksi, file di `<dir>/image` & `<dir>/image_merge` **dinama-ulang** memakai peta
`vvcc.bin` / `vc_obb` (path asli → nama obfuscated) supaya cocok dengan penamaan aset bawaan APK,
lalu lanjut ke MainActivity.

`{update}/size.json?rnd=` → `{"base": "31015076", "11389": 252562, ..., "all": 19385098}` —
hanya untuk progress bar (bukan keputusan).

**Hasil uji live saat riset (re-cek ulang terkonfirmasi)**: APK bawa base `110` / res `11220`;
server menyediakan `base.version = 110` (sama → `base.zip` **tidak diunduh**) dan
`resource.version = 11389` (lebih baru) dengan
`upgrade.json` = `{"11389": "11389_11390.zip", "11388": "11388_11390.zip", …, "all": "all.zip"}`
→ sistem mendukung **patch inkremental** dan **paket penuh**.
⚠️ Untuk **instalasi baru**: res lokal `11220` **tidak ada** sebagai key di `upgrade.json`
(key hanya 11386–11389) → `patchFile = null` → fallback **`all.zip` ±19,4 MB**.
Patch ±250 KB hanya berlaku untuk instalasi yang sudah berada di res 11386–11389.
(`all.zip` terverifikasi isinya: `js/` 11 file versi baru + `resource/` penuh + `resource.version` = `11390` —
652 entri.)

### Fase 6 — MainActivity: runtime Egret Native

`MainActivity.onCreate()` menerima extra `preloadPath`, lalu:

1. **Pre-init**: baca ulang `vvcc.bin` (→ `versionConfig` untuk JS), `infoinfo.bin` (→ `vvcc_tc.bin` bila ada `|`), `vc_obb` bila OBB ada.
2. **Keputusan EgretNative vs WebView mandiri** (logika eksak dari kode):
   ```java
   isWebMode   = (webVer > 0 && webVer >= Build.VERSION.SDK_INT)  // method mSdkParams
   nativeMode  = !isWebMode
   // webVer TIDAK ADA di setting bin (default -1) → isWebMode = false
   //   → nativeMode = TRUE → SELALU jalur EgretNativeAndroid (libegret.so),
   //     bukan WebView mandiri ("jsbind") yang hanya untuk varian webVer tertentu.
   ```
   - **NATIVE** (kasus nyata): dibuat `EgretNativeAndroid` — perlu cek **GLES 2.0**
     (`reqGlEsVersion >= 0x20000`; gagal → Toast "This device does not support OpenGL ES 2.0.").
   - **WEBVIEW** (hanya bila `webVer` diset dan API ≥ webVer): dibuat `WebView` (JS on,
     akses file OFF) + jembatan `@JavascriptInterface "jsbind"`; native→JS lewat
     `evaluateJavascript("javascript:startGame('<base64 json>')")`.
3. **Setup `EgretNativeAndroid`** (`org.egret.egretnativeandroid` — pembungkus **`libegret.so`** yang meng-embed **mesin V8**):
   ```
   config.showFPS = false;  config.fpsLogTime = -1;  config.clearCache = false;
   config.loadingTimeout = 0;          // 0 = tanpa timeout → hang, bukan crash
   config.preloadPath = mPreloadPath;  // cache root konten game
   config.disableNativeRender = (nativeRender <= 0);
   // ⚠️ TERVERIFIKASI: setting produksi TIDAK punya field "nativeRender" → default -1
   //    → disableNativeRender = TRUE → parameter "nativeRender: NO" dikirim ke libegret.so.
   //    Kedua engine TERTANAM di .so (namespace C++ egret::nativeRender::customRender +
   //    GLES + V8, 1733 string V8) dan pilihan engine = saklar dari setting bin —
   //    yang berarti saklar ini KENDALI PENUH kita di standalone mode (§9).
   ```
4. **Daftarkan 13 jembatan ExternalInterface** (JS↔Java):
   `changeView, changeLanguage, refresh, startGame, pei, giveLike, contact, report2Third, openURL, switchAccount, userCenter, startTsGame, sendLog`
5. `initialize(gameUrl)` — membangun parameter native (`Entry=<url>`, `nativeRender=YES/NO`,
   `preloadPath`, `loadingTimeout`, `cachePath`, `transparent`, `immersiveMode`, `useCutout`),
   memeriksa keberadaan `.so` ("Libs not found") dan **kesamaan versi jar vs .so** —
   bila gagal → Toast **"Initialize native failed."** → layar loading diam (hang, bukan crash).
   Bila berhasil → `setContentView(rootFrameLayout)` + overlay loading
   (gambar `bg` + mask + spinner tak-berujung) di atasnya.
6. Inisialisasi SDK channel bila channel aktif & mode bukan 3/4: QuickGame `init(productCode)` + `showFloatView` + `QuickGameManager.onCreate`.

Mekanisme **restart dalam runtime**: event `refresh` (dipanggil JS saat ganti bahasa/akun) →
native log `重新启动游戏` → `startActivity(LaunchActivity)` + finish → **seluruh alur boot
diulang dari Fase 0** (ekstraksi dilewati karena flag `decodeBinFinish`).

Pemilihan `gameUrl` & login (logika eksak dari `mSdkParams`):

```java
gameUrl()     = (mode == 2) ? (filterIP ? tsWebUrl : tsUrl)
                             : (filterIP ? webUrl    : url)
loginNative() = (loginPort > 0) ? format("%s:%d", loginServer, loginPort)   // :610
                                : loginServer
loginWeb()    = (mode == 2) ? tsLoginServer
            : (loginWebPort > 0) ? format("%s:%d", loginServer, loginWebPort) // :510
                                 : loginServer
```

Nilai nyata (`testMode: 1`, tanpa `webVer`, tanpa filter) → native memuat
`https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935` dan login
`https://login.popoh5.com:610`. ✓ cocok dengan hasil live test.

### Fase 7 — Handshake `startGame` (native ↔ JS) — mekanika persis

Runtime native memuat `gameUrl` → JS mulai berjalan; keduanya berjumpa di handshake ini:

```
JS  (index-native.html, setelah 15 script termuat):
    egret.ExternalInterface.addCallback("startGame", function(msg){ … })   ← pasang penerima
    egret.ExternalInterface.call("startGame", "get Sdk Channel")           ← MINTA data
        │
        ▼  (native)
    dispatcher case 7 → handler START_GAME:
      susun JSON konfigurasi →
      callExternalInterface("startGame", json)                             ← BALAS
        │
        ▼  (JS)
    addCallback("startGame") menerima json → simpan global → egret.runEgret()
```

> Varian WebView (tidak aktif di build ini): native memanggil
> `evaluateJavascript("javascript:startGame('<base64 json>')")` — fungsi global JS `startGame()`.

**Payload lengkap yang dikirim native → JS (jalur native):**

| Key | Nilai nyata | Sumber |
|---|---|---|
| `packageName` | `com.db.local` | aplikasi |
| `language` | bahasa tersimpan | SharedPreferences |
| `loginServer` | **`https://login.popoh5.com:610`** | `format("%s:%d", loginServer, loginPort)` dari setting bin |
| `loginKey` | (bila ada) | setting bin |
| `screenSize` | `{"width":720,"height":<terhitung>}` | ukuran layar aktual |
| `version` | **`110.11220`** | `<baseVersion>.<resVersion>` dari preload dir (fallback assets) |
| `versionConfig` | isi `vvcc.bin` (peta 1.944 nama file) | assets |
| `versionConfigObb` | isi `vc_obb` (bila ada) | OBB |
| `thirdParams` | JSON login SDK | QuickGame |
| `thirdChannel` | channel ikon/nama | setting/gameIcon |
| `clientParams` | `{"hideList":["1.99",...], ...}` | setting bin |

> Catatan penting: meta-data `project_name = "DragonBall"` di manifest membuat native **TIDAK** mengirim `jsonB64Map`/`jsonDecodeB64Map` (jalur injeksi JSON offline Egret Native dinonaktifkan) → semua JSON config game dimuat lewat jalur RES/CDN biasa.

### Fase 8 — Boot engine → layar login

`index-native.html` — **selalu diunduh dari `url` game (server); tidak ada salinannya di APK** —
berjalan di dalam runtime Egret Native (`libegret.so`):

```js
window["Log_Clean"]=true; window["debug"]=false; window["CacheNum"]=20;
window["reportBattlleLog"]="http://log.sjflaregame.com:10000/error/battle/client";
window["privacyUrl"]="http://www.parajoymobile.com/privacy_en.html";
// <div class="egret-player" data-entry-class="Main" 750×1334 @60fps, portrait, showAll>
xhr('./manifest.json?v=' + Math.random())
  → loadScript(manifest.initial + manifest.game)   // 15 file JS BERURUTAN (synchronous)
  → window["loadJsonFunc"] = RES.getRes;  window["refreshPage"] = …
  → pasang callback "startGame" & "refresh" → panggil native (Fase 7)
  → (menerima jawaban) set global → egret.runEgret({renderMode:"webgl"})
```

**Daftar persis 15 file JS dari `manifest.json` (hasil unduh live):**

| Kelompok | File |
|---|---|
| initial (7) | `egret.min`, `egret.web.min`, `game.min`, `tween.min`, `assetsmanager.min`, `eui.min`, `dragonBones.min` |
| game (8) | `md5.min`, `lz-string.min`, `socket.io.min` **v2.1.0**, `blockmessage.min` (filter chat), `battlelogic.min`, `ThinkingAnalyticsSDK.min`, `default.thm_*.js`, `main.min` (4,7 MB — kode game utama) |

Setelah `egret.runEgret()`: entry class **`Main`** → muat `default.res.json`
(**7.844 entri resource**, grup: `preload` 223 item, `login` 28, `main` 173, `first` 16, …)
+ `default.thm.json`/`gameEui.json` → grup `preload` & `login` diunduh → scene booting →
**layar login** tampil.

> **`resource/properties/serversetting.json`** dipakai di `connectToLogin()` (main.min.js):
> sumber login = `window.getLoginServer()` (injeksi native, Fase 7) **dulu**; bila kosong
> (jalur H5/browser) fallback fetch `resource/properties/serversetting.json` → `loginserver`.
> ✦ VERIFIKASI LIVE file ini: `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}`
> — nilainya **identik** dengan yang disuntik native → kedua jalur konvergen ke titik yang sama.

### Fase 9 — Login & Masuk Game (dari main.min.js)


> ⚠️ Catatan jujur: seluruh alur di bawah ini hasil **analisis kode** (dapat dipercaya —
> nama class/method nyata), tetapi **belum diuji end-to-end** karena butuh akun SDK valid.
> Yang sudah teruji live = level koneksi (lihat §6). Bagian ini dibiarkan sebagai
> dokumentasi kode; pembuktian penuh menunggu sesi nyata (MITM) bila diperlukan.

Dua jalur login:

**Jalur A — SDK (QuickGame):**
```
window.checkSDK() → window.getSdkLoginInfo()
  → { loginToken, userId, nickName, sdk }        // thirdParams.data
  → clientRequestServerList(userId, sdk)          // action "GetServerList" ke login server
  → selectNewServer() (auto-pilih server "new"/history)
  → onLoginSuccess() → tombol START tersedia
```

**Jalur B — Origin login (username/password manual):**
```js
loginBtnTap → doOriginLoginRequest({username, password, lastServerID:""})
  if (!password) password = "game_origin"          // ← password default bila kosong!
  → clientLoginUser(username, password, ...)       // action "loginGame"
  → clientRequestServerList(...)                   // action "GetServerList"
  → buka UI "ServerListNode" (pilih server manual)
  → startBtnTap → action "SaveHistory" → clientStartGame()
```
Juga ada auto-login: `checkLastLogin()` membaca query `?userid=` atau localStorage
`last_game_server` → login ulang otomatis dengan password `"game_origin"`.

**Sequence lengkap setelah pilih server:**
```
1. clientStartGame()
2.   mainClient.connectToServer(serverItem.url)        // socket.io
3.   (verify handshake — hanya main/chat/dungeon, lihat §5)
4.   startListenNotify() → clientEnterGame()
5.     mainClient: {type:"user", action:"enterGame", loginToken, userId,
6.                  serverId, language, gameVersion, version:"1.0"}
7.     ← server kirim SELURUH data user (UserDataParser.saveUserData)
8.   registChat() polling 3 detik → dapat chaturl + roomId
9.   chatClient.connect(chaturl) → {type:"chat", action:"login"} → join 4 room
   (world, guild, teamDungeon, team)
10.  dungeonClient.connect(serverItem.dungeonurl)
11.  loginClient: action "SaveUserEnterInfo" → loginClient.destroy()
12.  runScene("OverScene") → masuk Home
```

### 4.10 Inventaris Config Lengkap (boot → layar login)

Satu tabel induk: **semua** config yang dibaca/diunduh dari saat APK dibuka sampai layar
login tampil. (Kolom "Bila gagal" = gejala yang dilihat user; detail di 4.11.)

| # | Config | Sumber | Enkripsi | Dibaca di | Fungsi | Bila gagal |
|---|---|---|---|---|---|---|
| 1 | `assets/config_BS.properties` → `EntryPoint1..3` | di dalam APK | Base64 (bukan enkripsi) | Fase 1 | URL server config (2 URL untuk BS) | tidak ada URL → mode 0 → dialog check_network |
| 2 | `setting_BS_Android.bin` | `{EntryPoint}/setting_BS_Android.bin?rnd=` | **XOR "DragonBall"** | Fase 2 (setiap peluncuran) | **otak seluruh alur**: mode, url game, url update, login server, clientParams (tabel lengkap di Fase 2) | download gagal → mode 0; parse gagal → unknown_error |
| 3 | `com_db_local.bin` | `{EntryPoint}/com_db_local.bin?rnd=` | XOR "DragonBall" | Fase 2 | config per-paket (opsional) | 404 → diabaikan, lanjut |
| 4 | `assets/game/infoinfo.bin` | di dalam APK | teks polos | Fase 4 | nama paket kode `voyage_fancy_radius\|pschent_full_tired` | "info file not found" → berhenti |
| 5 | `assets/game/vvcc.bin` | di dalam APK | **onepiece/XXTEA** | Fase 4 | peta 1.944 nama file (asli → obfuscated); dikirim juga ke JS sebagai `versionConfig` | "read_version_config_error" → berhenti |
| 6 | `assets/game/vvcc_tc.bin` | di dalam APK | **onepiece/XXTEA** | Fase 4 (bila `infoinfo` ada `\|`) | peta kedua (139 entri) untuk paket kedua | tidak fatal |
| 7 | `vc_obb` (dari `main.<vc>.com.db.local.obb`) | OBB expansion | — | Fase 4 (bila OBB ada) | peta nama tambahan | tidak fatal (kasus normal: tanpa OBB) |
| 8 | paket kode `pschent_full_tired.bin` (9,4 MB) | di dalam APK | **onepiece/XXTEA → ZIP** | Fase 4 (sekali, flag `decodeBinFinish`) | kode game: **14 JS + resource penuh** (TANPA `index-native.html`/`manifest.json` — lihat #16) → diekstrak ke `<preloadDir>/<urlDir>/` | gagal dekrip → JS/resource harus semuanya dari jaringan (atau hang) |
| 9 | paket kode `voyage_fancy_radius.bin` (1,2 MB) | di dalam APK | **onepiece/XXTEA → ZIP** | Fase 4 varian IP-filter | build varian TS/fairygui (161 entri), diekstrak langsung ke `preloadDir` | — (jalur alternatif saja) |
| 10 | `assets/game/base.version` = `110` | di dalam APK (fallback) | teks polos | Fase 5 | versi kode lokal | fallback tanpa masalah |
| 11 | `assets/game/resource.version` = `11220` | di dalam APK (fallback) | teks polos | Fase 5 | versi resource lokal | fallback tanpa masalah |
| 12 | `{update}/base.version` & `resource.version` | CDN hot-update | teks polos | Fase 5 (setiap peluncuran mode 1) | keputusan unduh patch | "404" → dialog; bukan angka → dialog network fail |
| 13 | `{update}/upgrade.json` | CDN hot-update | teks polos | Fase 5 (bila patch res perlu) | peta patch inkremental `<lokal>_<remote>.zip` | gagal → fallback `all.zip` |
| 14 | `{update}/size.json` | CDN hot-update | teks polos | Fase 5 | ukuran unduhan (progress bar saja) | gagal → progress tak akurat, lanjut |
| 15 | `{update}/base.zip` / `all.zip` / `*_*.zip` | CDN hot-update | ZIP polos | Fase 5 (bila versi beda) | konten game terbaru | gagal → dialog "Download … Zip Fail" → berhenti |
| 16 | `index-native.html` + `manifest.json` | `{url}` (server, **WAJIB ada — tidak ada salinan di APK**) | polos | Fase 6–8 | bootstrap JS runtime | tidak ada di cache lokal → bila server gagal → hang di layar loading |
| 17 | 15 file JS (daftar Fase 8) | `{url}/js/…` atau cache lokal (union paket bawaan + `all.zip`) | polos | Fase 8 | engine Egret + kode game | salah satu gagal → tidak sampai `runEgret` → layar loading selamanya |
| 18 | `default.res.json` (7.844 entri) + `default.thm.json` + `gameEui.json` | `{url}/resource/…` atau cache lokal | polos | Fase 8 (Main) | indeks resource & tema UI | grup `preload`/`login` gagal → layar login tak tampil |
| 19 | grup resource `preload` + `login` (±251 item) | `{url}/resource/assets/…` | polos | Fase 8 | gambar/suara booting & login | timeout RES → hang di loading |
| 20 | `resource/properties/serversetting.json` | `{url}/resource/properties/` | polos | Fase 8–9 (hanya bila `getLoginServer()` kosong) | fallback alamat login (web) | tidak dipakai di APK native |
| 21 | `NativeKey.getKey()` | `libegret.so` (JNI) | string di rodata | runtime native | kunci internal engine | — (bawaan) |

**Pola penting**: setelah Fase 4, hampir semua request `url` game **dilayani dari cache lokal**
(`<preloadDir>/<urlDir>/`) oleh mesin Egret Native; jaringan hanya dipakai untuk hot-update
(Fase 5) dan request resource yang tidak ada di cache. Inilah sebabnya mirror statis cukup
untuk membawa app sampai layar login.

### 4.11 Peta Titik Gagal — kapan force close, kapan cuma hang

Hasil bedah alur depan menyimpulkan: **mayoritas kegagalan config TIDAK langsung force close** —
Java menangkap exception menjadi dialog/berhenti. Yang benar-benar berbahaya adalah lapisan JS
dan lapisan resource. Peta lengkap:

| Titik | Pemicu | Gejala | Klasifikasi |
|---|---|---|---|
| Fase 2: setting bin tak terjangkau | server mati/tidak ada | dialog "check_network" (mode 0) | **dialog, aman** |
| Fase 2: setting bin rusak (XOR/JSON salah) | kunci/isi salah | dialog "unknown_error" | **dialog, aman** |
| Fase 4: infoinfo/vvcc hilang | assets diutak-atik | dialog "info file not found" / "read_version_config_error" | **dialog, aman** |
| Fase 5: version file remote berisi HTML/teks bukan angka | server mock salah kirim | dialog "Read Upgrade Config File Fail…" | **dialog, aman** |
| Fase 5: zip unduhan korup/terputus | koneksi putus | dialog "Download … Zip Fail" | **dialog, aman** |
| Fase 6: GLES < 2.0 / libegret.so hilang / versi jar≠so | device/APK rusak | Toast "Initialize native failed." → layar hitam + spinner | **hang, tidak crash** |
| Fase 6: mode 3 | testMode:3 di setting | **layar kosong total** (tidak ada init) | **dead screen** |
| Fase 8: manifest.json gagal / daftar JS kosong | CDN mock salah path | JS error `manifest is null` → script tak termuat → spinner selamanya | **hang** |
| Fase 8: JS error saat boot (file JS ada tapi setengah isi / versi campur) | mirror tidak konsisten | exception JS di V8 → render macet, kadang fatal di native (SIGSEGV saat JS memanggil bridge dengan state setengah jadi) | **RISIKO FORCE CLOSE** |
| Fase 8: resource grup `preload`/`login` hilang/404 | mirror aset tidak lengkap | RES throw → exception JS berulang → kadang crash native renderer (nativeRender=YES membaca tekstur langsung) | **RISIKO FORCE CLOSE** |
| Fase 8: peta `vvcc` tidak cocok dengan nama file di disk | regenerasi nama acak sendiri | file tak ketemu saat render native → crash di native side | **RISIKO FORCE CLOSE** |
| Fase 9: login server mati | socket.io mati | pesan "koneksi gagal" di UI login (retry), layar login tetap jalan | **dialog di dalam game, aman** |

**Kesimpulan praktis** (jadi dasar strategi §9):
1. Selama **config chain (1–15)** benar dan lengkap → app dijamin sampai layar login TANPA force close.
2. Sumber force close nyata = **ketidakkonsistenan mirror JS/aset** (#16–19) dan **peta vvcc** —
   bukan setting bin.
3. Karena itu rencana standalone: **jangan regenerasi apa pun** — mirror 1:1 konten asli,
   dan ubah hanya config (setting bin + EntryPoint).

---

## 5. Bedah Komunikasi Server ↔ Aplikasi

### 5a. Topologi Koneksi (4 koneksi socket.io terpisah)

```js
// main.min.js baris 21366:
t.loginClient   = new TSSocketClient("login-server",   false);  // ← verifyEnable = FALSE
t.mainClient    = new TSSocketClient("main-server",    true );
t.chatClient    = new TSSocketClient("chat-server",    true );
t.dungeonClient = new TSSocketClient("dungeon-server", true );
```

| Client | Sumber URL | Verify? | Fungsi |
|---|---|---|---|
| loginClient | `loginServer:610` (disuntik native) | ❌ | loginGame, GetServerList, SaveHistory, SaveUserEnterInfo, SaveLanguage |
| mainClient | `serverItem.url` (dari GetServerList) | ✅ | semua gameplay (60+ tipe pesan) |
| chatClient | `serverItem.chaturl` (dari registChat) | ✅ | chat world/guild/team + broadcast |
| dungeonClient | `serverItem.dungeonurl` | ✅ | team dungeon realtime + push event |

Engine: **Socket.IO v2.1.0** (header file `socket.io.min_57ea9db7.js`).

### 5b. Format Pesan (Envelope Seragam)

**Request** — event `"handler.process"`, argumen `(message, ackCallback)`:
```js
socket.emit("handler.process",
  {
    type: "user",              // modul (user/hero/arena/dungeon/...)
    action: "enterGame",       // operasi
    ...params,                 // param per-action
    version: "1.0"             // selalu ada
  },
  function(response) { ... }   // server balas via socket.io ACK
);
```

**Response** (semua modul sama):
```json
{
  "ret": 0,                       // 0 = sukses; lainnya = error (lihat §5g)
  "data": "<JSON string>",        // payload TERENCODE STRING, bukan objek!
  "compress": true,               // bila true → data = LZString UTF16
  "serverTime": 1699...000,       // sinkronisasi waktu
  "server0Time": ...
}
```
Alur dekode: `ret==0` → (compress? `LZString.decompressFromUTF16(data)`) → `JSON.parse` → callback.
(HTTP request `ts.httpReqHandler` memakai format sama: POST `Content-Type: application/x-www-form-urlencoded`, body `data=<JSON.stringify(payload)>`, timeout 20 detik, retry 1x.)

**Server push** — event `"Notify"` (dan chat `"Notify"` ret "SUCCESS"):
```json
{ "ret": "SUCCESS", "data": "...", "compress": false }
```
Action push yang dikenali client: `payFinish` (pembayaran selesai), `redDotDataChange`,
`itemChange`, `guildAgree`, `beKickedOutGuild`, `heroBackpackFull`, `onlineBulletin`,
`scheduleModelRefresh`, `monthCard`, `vipLevel`, `notifySummon`, `warStageChange`,
`warRankChange`, `warAutoSign`, `ballWarStateChange`, `ballWarPointRankChange`,
`topBattleBeAttack`, `topBattleStageChange`, `teamDungeon*` (5 jenis), `timeTrialReset`,
`battleMedalRefresh`, `battleMedalTaskChange`, `broadcast`, `userMessage`, `updateForbiddenChat`,
`mainTaskChange`, `joinTeamSuccess`, `areanRecord`, `FGAddMsg`, `FGAddChatMsg`, `FGNewApply`, ...

### 5c. Handshake `verify` (anti-cloning koneksi)

Hanya untuk main/chat/dungeon server. Segera setelah connect:
```
server  ──► event "verify" dengan nonce string
client  ──► TEA_encrypt(nonce, "verification") → Base64
client  ──► emit "verify" (ciphertext, ack)
server  ──► ack {ret: 0} → koneksi resmi; selain itu → destroy
```

Implementasi TEA (port eksak dari main.min.js — klasik **XXTEA**):
```js
// delta = 2654435769 (0x9E3779B9), rounds = floor(6 + 52/n)
// key = strToLongs(keyStr.slice(0,16)) → "verification" = 3 longs + padding
// MX = (s>>>5 ^ l<<2) + (l>>>3 ^ s<<4) ^ (p^l) + (key[3&d^e] ^ s)
// Output: Base64
```
Port ini sudah diuji roundtrip encrypt/decrypt konsisten dengan implementasi asli game.

> Catatan: skrip bukti & dekriptor adalah artefak riset lokal (tidak disertakan di repo
> ini — lihat §10).

### 5d. API Surface — 60+ Tipe & 200+ Action

Inventaris lengkap (hasil scan `type: "..."` / `action: "..."` di main.min.js):

| Tipe | Jumlah call | Contoh action |
|---|---|---|
| `activity` | 124 | getActivityDetail, getLoginActivityReward, getLevelReward |
| `friend` | 46 | getFriends, friendBattle, friendServerAction |
| `guild` | 42 | getGuildList, getGuildDetail, requestGuild, quitGuild |
| `hero` | 27 | getAttrs, wear, takeOff, merge, reborn, splitHero, resolve |
| `topBattle` | 26 | startBattle, getRank, topAward |
| `war` | 21 | getSignUpInfo, bet, getChampionRank (war lintas server) |
| `teamDungeonGame` | 20 | startBattle, getTeamInfo, tryMatch, join |
| `expedition` | 19 | startBattle, sweep |
| `user` / `User` | 16+5 | enterGame, registChat, loginGame, GetServerList, SaveHistory |
| `rank` | 16 | getRank, getRankInfo, getRankReward |
| `ballWar` | 16 | startBattle, bet, ballSignUp |
| `arena` | 14 | startBattle, getFriendArenaDefenceTeam, buyTimes |
| `entrust` | 13 | userEntrustBook, investigation |
| `weapon`/`equip`/`imprint` | 12/11/12 | wear, takeOff, merge |
| `tower` | 12 | startBattle, reset, getReward |
| `teamDungeonTeam` | 12 | getTeamInfo, saveTeam |
| `summon` | 12 | summonOne, summonTen, summonOneFree, openBox |
| `hangup` | 11 | (idle/on-hook rewards) |
| `gift`/`shop`/`mail` | 11/6/6 | buy, getReward, readMsg |
| `snake`/`cellGame` | 10/10 | minigame |
| `training`/`task` | 9/9 | queryTask, finishEvent |
| `dungeon` | 9 | startBattle, checkBattleResult, sweep |
| `trial`/`mine`/`maha`/`bossCompetition`/`battleMedal`/`timeTrial`/`strongEnemy` | 7 | startBattle, getRecord |
| `superSkill`/`resonance`/`genki`/`gemstone`/`gravity` | 5/5/4/4/3 | sistem upgrade |
| `chat` | 5 | login, sendMsg, join |
| `timeMachine`/`teamTraining` | 4 | — |
| `timeBonus`/`retrieve`/`monthCard`/`market`/`downloadReward`/`YouTuber` | 2 | — |
| `vipMarket`/`recharge`/`questionnaire`/`guide`/`checkin`/`buryPoint` | 1 | — |
| `battleRecordCheck`/`battle` | 1 | checkBattleRecord (anti-cheat channel TanWanH5) |

Action terpopuler: `startBattle` (19), `getRank` (19), `friendServerAction` (19),
`getActivityDetail` (16), `checkBattleResult` (15), `getReward` (11), `getInfo` (9),
`wear` (8), `getTeamInfo` (8), `buyTimes` (8), `setTeam` (7), `getAttrs` (7).

### 5e. Protokol Battle (inti game idle)

Pola untuk SEMUA mode battle (dungeon/arena/topBattle/expedition/tower/...):

```
CLIENT                                    SERVER
  │ {type, action:"startBattle",           │
  │  team, super, battleField, ...}        │
  │ ────────────────────────────────────►  │ validasi + pilih musuh
  │                                        │ jalankan simulasi server-side
  │ ◄────────────────────────────────────  │
  │ { _battleId,                           │  ← ID pertarungan
  │   _battleResult,          ← HASIL SUDAH DITENTUKAN SERVER
  │   _rightTeam, _rightSuper,← tim musuh utk animasi
  │   _rand,                  ← SEED RNG!  │
  │   items/_arena/_signs/... }            │
  │                                        │
  │  [VISUAL REPLAY lokal, deterministik]  │  battlelogic.min.js
  │  simulasi dengan seed _rand → hasil    │  (mesin battle lengkap di klien)
  │  HARUS sama dgn _battleResult          │
  │                                        │
  │ {action:"checkBattleResult",           │
  │  battleId, checkResult: 0/1}           │
  │ ────────────────────────────────────►  │ bandingkan dgn hasil server
  │ ◄────────────────────────────────────  │
  │ { _battleResult, reward items,         │
  │   _changeInfo._items, _curMaxLevel }   │
```

**Temuan kunci**:
- Battle = **simulasi deterministik dua sisi** (server authoritative, klien replay visual).
- Klien punya **mesin battle lengkap** (`battlelogic.min.js` 312KB, `GameFieldType`:
  ARENA, EQUIPDUNGEON, EXPDUNGEON, METALDUNGEON, ZSTONEDUNGEON, SIGNDUNGEON, ...) —
  termasuk konfigurasi per-level `battleRecord_XXXX.json` (70+ file).
- Anti-cheat: hasil klien ≠ server → `ret: 22` (`BATTLE_CHECK_ERR`) → client kirim
  log battle ke `http://log.sjflaregame.com:10000/error/battle/client` (JSON:
  gameModel "dragonball", channel, serverId, userId, battleId, errorRecord).
- Sistem `snakeLineUpCheck`, `arenaSaveCheck`, tim idle (`LAST_TEAM_TYPE`) semua ada di klien.

### 5f. Chat Report & Signing MD5

Rahasia hardcoded: **`0acc53af8679849572afd232158c`** — dipakai `reportToXXData()`
(laporan chat ke platform channel "XX", mis. TanWan):
```js
sign = md5(gkey + server_id + qid + time + secret)
// gkey      = "288"
// tkey      = "10"
// server_id = "S" + serverId
// qid       = userId (channel)
// role_id   = userId (game), name = nickname, content = isi chat, time = Date.now()
```

### 5g. Kode Error (362 entri)

Sumber: `resource/json/errorDefine.json` (362 kode). Kode penting:

| ret | errorType | Arti |
|---|---|---|
| 1 | ERROR_UNKNOWN | error umum |
| 7 | ERROR_LACK_ITEM | item kurang |
| 16 | TIMES_NOT_ENOUGH | jumlah kesempatan habis |
| 17 | GM_IP_ERR | kick (isKick:1) |
| 20 | CONFIG_ERROR | kick |
| 22 | **BATTLE_CHECK_ERR** | **hasil battle klien ≠ server** → kirim battle log |
| 38 | — | paksa reload game (`window.reload()`) |
| 70001 | (dari data) | minta client kirim battle log |

---

## 6. Uji Protokol Live (Bukti Nyata)

Dilakukan saat riset (socket.io-client v2.1.0 — versi sama persis dengan klien game):

**a) Probe HTTP endpoint socket.io** (`curl`):
```
https://login.popoh5.com:610/socket.io/?EIO=3&transport=polling
  → 96:0{"sid":"gQ85FjHy8xyUdyWgaXL0","upgrades":["websocket"],
         "pingInterval":25000,"pingTimeout":5000}2:40
```
→ **Server socket.io HIDUP** (engine.io handshake lengkap + packet CONNECT namespace `40`).
`pingInterval 25000` = konfigurasi khas socket.io v2.

**b) Koneksi socket.io penuh (node)**:
```
[NET] connect: https://login.popoh5.com:610
[NET] CONNECTED id = IkfGbmQiJbfYju_faXLc     ← handshake sukses
```
Login server TIDAK mengirim event `verify` (sesuai kode: `loginClient` dibuat dengan
`verifyEnable=false` — verify hanya untuk main/chat/dungeon).

**c) Rate limiting**: 2 koneksi pertama diterima; koneksi ke-3+ ditolak server dengan
error namespace socket.io (`server error`) — meski dengan header Origin/Referer asli.
→ Server hidup tapi **protektif terhadap koneksi berulang** (bukan zombie).

**d) Port 510 (`loginWebPort`)**: HTTPS-nya menjawab `{"code":404,"msg":"没有开通"}`
("belum dibuka") → port web memang tidak aktif; hanya 610 (native) yang dipakai.

**e) Kunci TEA "verification"**: roundtrip encrypt/decrypt terbukti konsisten
(dengan port eksak dari kode game).

> Untuk pengujian lebih jauh (loginGame/GetServerList nyata) diperlukan akun SDK yang
> valid — tidak dilakukan agar tidak menyalahgunakan server yang sekarat.

---

## 7. Hasil Ekstraksi: Kode, Data, Aset

### Kode game (dari `pschent_full_tired.bin` 9,4 MB → ZIP terdekripsi)
- **14 file JS**: `main.min_7eae4d6e.js` (4,7 MB, 1.897 class), `battlelogic.min_394b3477.js`,
  `blockmessage.min_4c0caf6b.js` (ternyata **filter kata sensor chat**, BUKAN protokol!),
  engine Egret/eui/dragonBones/tween/lz-string/md5/socket.io
  *(paket bawaan = build sedikit lebih lama dari CDN yang berisi 15 JS —
  bedanya: `ThinkingAnalyticsSDK` + hash file beda; CDN selalu menimpa saat hot update)*
- **434 file JSON konfigurasi** (`resource/json/`): VIP, arena, activity, hero, weapon,
  `battleRecord_1505..1634.json` (konfig battle per-level), `errorDefine.json`, `constant.json`, dll
- **7 file bahasa** (language-en.json, ...)
- `default.res.json` + peta aset `default.thm.json`

### Aset gambar
- 2.522 file `assets/game/`: 2.067 PNG + 156 JPG (**polos/terbuka**, hanya NAMA file yang
  di-obfuscate) + 74 .data + 77 .bin + 76 .version
- Peta nama asli: `vvcc_tc.bin` → 139 entri (mis. `heros/2001/2001_tex.png` ↔
  `resource/assets_ts/sibbs_consent_pschent/thirty_tozies_favourable.png`)

### Rahasia yang ditemukan (semua tertanam di klien)
| Rahasia | Nilai | Fungsi |
|---|---|---|
| Kunci XOR config | `DragonBall` | dekripsi setting server |
| Magic + kunci kode | `onepieceencrypts` / `onepiecedecryptk` | dekripsi ZIP kode game |
| NativeKey (libegret.so) | `5ce4b0e76844477` | runtime key |
| Kunci handshake verify | `verification` | TEA antar koneksi game |
| Secret MD5 chat report | `0acc53af8679849572afd232158c` | sign laporan chat channel XX |
| Password default origin login | `game_origin` | login username manual/auto |

---

## 8. Infrastruktur Server & Status

| Server | Fungsi | Status saat riset |
|---|---|---|
| `dragon.sjmobilegame.com:82` | config + game uji | ✅ HIDUP (HTTP 200) |
| `configus.sjmobilegame.com` | config channel BS | ✅ HIDUP |
| `configus-1257066086.cos.na-siliconvalley.myqcloud.com` | CDN Tencent COS (cadangan config) | ✅ HIDUP |
| `qsdk.t4game.com` | QuickGame SDK endpoint | ✅ HIDUP |
| `dragonh5cdn.popoh5.com` | CDN game H5 (manifest, JS, hot-update) | ✅ HIDUP — manifest & JS terunduh |
| `login.popoh5.com:610` | **login server (socket.io v2)** | ✅ HIDUP — handshake lengkap, ada rate-limit per IP |
| `login.popoh5.com:510` | port web (loginWebPort) | ⚠️ menjawab `{"code":404,"msg":"没有开通"}` (tidak dibuka) |
| `log.sjflaregame.com:10000` | battle/error log | (endpoint dari kode) |
| `118.24.68.91:7003` | tsLoginServer (uji, IP mentah) | (dari setting) |

---

## 9. Rencana Standalone Mode

> ⚠️ **KARAKTER APK**: ini **native game**, bukan wrapper WebView. Runtime = `libegret.so`
> (Egret Native) yang meng-embed **mesin V8** + `runtime-dex.jar`, terikat ketat ke Java
> lewat **13 jembatan ExternalInterface**. Bedah alur depan (§4.11) menunjukkan:
> sebagian besar kesalahan config hanya menghasilkan dialog/hang, TAPI ketidakkonsistenan
> mirror JS/aset + peta `vvcc` bisa **force close**. Maka prinsip rencana ini:
> **(1) pengerjaan bertahap mengikuti urutan alur boot, (2) setiap tahap diverifikasi
> sebelum lanjut, (3) jangan pernah menyentuh kode native, (4) jangan regenerasi konten —
> mirror 1:1, ubah hanya config.**

### 9.1 Keputusan desain (kenapa patch minimal)

| Layer | Keputusan | Alasan |
|---|---|---|
| `libegret.so` + `runtime-dex.jar` | ❌ **JANGAN disentuh** | V8 + JSB + `NativeKey` (`5ce4b0e76844477`) hidup di dalamnya; tidak ada yang perlu diubah di sana |
| Java `LaunchActivity` / `MainActivity` | ❌ JANGAN diubah | Semua URL/endpoint datang dari `setting_*.bin` — Java sudah "dependency-inject" lewat config |
| `assets/config_BS.properties` | ✅ **satu-satunya patch di APK** | Channel `"BS"` hardcoded (Fase 0) → `EntryPoint1` di file inilah satu-satunya titik kontrol |
| `setting_BS_Android.bin` (server kita) | ✅ kontrol penuh | XOR `DragonBall` sudah terpecahkan — kita bisa menulis JSON sendiri |
| Kode JS game (CDN) | ✅ boleh dimodifikasi — tapi **jangan dulu** | File JS di CDN **polos** (tidak terenkripsi); edit JS baru dipertimbangkan SETELAH boot bersih (Tahap D+) |
| Aset/resource | ✅ mirror apa adanya | Nama file mengikuti peta `vvcc.bin` — jangan regenerasi nama acak sendiri (lihat 4.11 #vvcc) |

**Fakta pendukung (semua terverifikasi):**

1. **`cleartextTrafficPermitted = true`** (`res/xml/network_security_config.xml`, `base-config`) + `usesCleartextTraffic=true` → server kita cukup **HTTP polos**, tidak perlu TLS/sertifikat.
2. `loginServer` **disuntik native** ke JS lewat event `startGame` (bukan hardcode di JS) → cukup ubah setting bin.
3. ❌ ~~`testMode: 3` sebagai saklar matikan update~~ — **TERKOREKSI**: mode 3 = **layar kosong** (MainActivity tidak init apa-apa, Fase 3). Jangan pakai.
4. `jsonB64Map` dinonaktifkan (`project_name = DragonBall`) → tidak ada kebutuhan struktur `json.gz` khusus.
5. Ekstraksi pertama dijalankan **sekali** (flag `decodeBinFinish`) dari paket bawaan APK —
   artinya **14 JS + resource** selalu ada lokal walau server mati. Yang **TIDAK punya salinan
   lokal** dan WAJIB tersedia di server setiap peluncuran hanya **4 file kecil**:
   `index-native.html`, `manifest.json`, `serversetting.json` (+ `setting_BS_Android.bin`
   di config server). Inilah permukaan serangan minimal standalone.

### 9.2 Arsitektur target

```
                        ┌──────────────────────────────────────────────┐
                        │             SERVER STANDALONE                │
                        │        (1 mesin, HTTP saja, LAN/VPS)         │
                        └──────────────────────────────────────────────┘
                              │
  APK patched                 │
  (1 file diubah:     ──────► ├─ /bs/db/android/setting_BS_Android.bin  ← config server
   EntryPoint1)              │    { testMode:1, url, update, loginServer → server ini }
                             │
                             ├─ /bs/index-native.html + manifest.json + 15 JS  ← static mirror
                             │    (+ default.res.json, aset bila tidak lewat zip)
                             │
                             ├─ /bs/upgrade/{ base.version, resource.version,   ← hot update
                             │              size.json, upgrade.json, base.zip, all.zip }
                             │
                             └─ :610  socket.io v2 emulator                     ← game server
```

Tiga komponen:

1. **Config server** — file statis `setting_BS_Android.bin` ter-XOR `DragonBall` (isi: lihat Fase 2).
2. **Static host** — mirror `index-native.html`, `manifest.json`, 15 file JS, version files, `size.json`, `upgrade.json`, `base.zip`, `all.zip`.
3. **Game server** — emulator socket.io v2 (endpoint minimal dulu: login → server list → enter game), data simpanan JSON/SQLite. *(Menyusul — socket diabaikan dulu sesuai kesepakatan.)*

### 9.3 Opsi implementasi APK

| Opsi | Cara | Penilaian |
|---|---|---|
| **A — rekomendasi** | Patch `assets/config_BS.properties` → `EntryPoint1 = http://<server-kita>/bs/db/android`, lalu zip ulang assets + zipalign + apksigner | Satu titik perubahan; tidak menyentuh `resources.arsc`/`.so` |
| B — DNS/hosts redirect | Arahkan domain asli ke server kita | ❌ Gagal: 3 domain kunci memakai **HTTPS** (`configus`, `dragonh5cdn`, `login.popoh5.com`) → sertifikat palsu ditolak Android; user-CA tidak dipercaya default Android 7+ |
| C — patch smali | Edit URL/hardcode di smali | Cadangan saja — lebih invasif; tidak diperlukan bila Opsi A cukup |

Catatan re-sign: versi lama harus **di-uninstall dulu** (signature beda); jangan menaikkan `targetSdk`; jangan mengaktifkan `android:debuggable`.

### 9.4 Roadmap bertahap — mengikuti urutan alur boot

Setiap tahap = satu lapisan alur boot (§4). Aturan main: **tahap berikutnya hanya boleh
dimulai bila tahap sebelumnya lolos kriteria verifikasi**. Dengan cara ini, kalau ada yang
rusak, kita tahu PASTI lapisan mana yang salah — tidak menebak-nebak.

#### Tahap A — Snapshot total (URGENT, server asli masih hidup) ⚠️

> Tanpa tahap ini, semua tahap lain tidak punya bahan baku. **Kerjakan pertama kali.**

| Unduh | Dari | Tujuan arsip | Status riset |
|---|---|---|---|
| `setting_BS_Android.bin` (raw) + hasil dekrip | `configus…/bs/db/android/` | cadangan config otak | ✅ sudah diambil |
| `base.version` (`110`), `resource.version` (`11389`), `size.json`, `upgrade.json` | `dragonh5cdn…/bs/upgrade/` | state update | ✅ sudah diambil |
| `all.zip` (±19,4 MB — 652 entri: 11 JS versi baru + resource penuh) | `dragonh5cdn…/bs/upgrade/` | **sumber utama mirror JS + resource** | ✅ sudah diambil |
| `base.zip` (±31 MB) | `dragonh5cdn…/bs/upgrade/` | cadangan konten base versi CDN | ⬜ belum |
| `index-native.html` (10,8 KB), `manifest.json` (533 B) | `dragonh5cdn…/bs/` | bootstrap (tidak ada di APK!) | ✅ sudah diambil |
| `serversetting.json` (73 B) | `dragonh5cdn…/bs/resource/properties/` | fallback alamat login | ✅ sudah diambil |
| (cadangan) 4 JS yang tidak ada di `all.zip` (`tween`, `assetsmanager`, `dragonBones`, `default.thm`) | dari paket bawaan APK (`pschent_full_tired.bin`, hash sama dgn manifest) | melengkapi 15 JS | ✅ ada di paket bawaan |

**Aturan konsistensi mirror (KRITIS — mencegah force close):**
- JS = **union** `all.zip` (11 file, hash baru) + paket bawaan (4 file hash lama yang tak berubah)
  → tepat 15 file sesuai `manifest.json`. Jangan campur file dari snapshot waktu berbeda.
- Resource = union paket bawaan + `all.zip` (nama file mengikuti peta `vvcc.bin`, jangan regenerasi).

✅ **Kriteria lolos**: jumlah file & ukuran tercatat; checksum; arsip di ≥2 tempat.
Uji kembali: 15 JS dari manifest semuanya ada di mirror.

#### Tahap B — Replika config server (Fase 1–2 dari alur boot)

Bangun **satu HTTP server statis** (port bebas, HTTP polos — boleh cleartext, lihat 9.1 #1):

```
/bs/db/android/setting_BS_Android.bin   ← buatan sendiri, isi di bawah, di-XOR "DragonBall"
/bs/db/android/com_db_local.bin         ← BOLEH 404 (app mengabaikannya — terbukti live)
```

Isi `setting_BS_Android.bin` versi standalone (JSON sebelum di-XOR):
```json
{
  "testMode": 1,
  "url": "http://<server-kita>/bs/index-native.html",
  "update": "http://<server-kita>/bs/upgrade",
  "loginServer": "http://<server-kita>",
  "loginPort": 610,
  "loginWebPort": 510,
  "clientParams": { … salin utuh dari setting asli … }
}
```

Cara bikin (contoh):
```bash
python3 -c "print(open('setting.json','rb').read().hex())"  # lalu XOR per-byte dengan b'DragonBall'
# XOR(kunci berulang): out[i] = in[i] ^ b'DragonBall'[i % 10]
```

⚠️ **Verifikasi WAJIB sebelum dipasang**: dekrip ulang file buatan sendiri dengan dekriptor
yang sama → hasil harus JSON valid persis. (Kesalahan kunci/urutan byte di sini hanya
menghasilkan dialog `unknown_error` — tidak merusak apa pun — tapi bikin pemborosan waktu.)

✅ **Kriteria lolos**: `curl` ke kedua path berhasil; dekrip-ulang = JSON valid;
setting asli & setting baru dibandingkan key-per-key (tidak ada key yang hilang).

#### Tahap C — Patch APK (Opsi A) + uji boot sampai Fase 2

1. Unzip APK → edit **hanya** `assets/config_BS.properties`:
   `EntryPoint1 = base64("http://<server-kita>/bs/db/android")`
   (EntryPoint2 boleh dihapus/kosongkan — loop Fase 1 hanya mencoba EntryPoint yang ada).
2. Zip ulang → zipalign → apksigner (v1+v2). Jangan sentuh file lain.
3. Install di emulator/perangkat uji (uninstall versi lama dulu).

**Monitoring wajib** (`adb logcat -s LaunchActivity GameUpdateUtil DecodeUtil MainActivity EgretNative`):

| Marker log (urutan) | Artinya |
|---|---|
| `客户端配置文件服务器地址: http://<server-kita>…` | Fase 1 OK — EntryPoint kita terbaca |
| `配置文件Url: http://<server-kita>…/setting_BS_Android.bin` | Fase 2 mulai |
| `CurrentMode: 1` | Fase 3 OK — testMode terbaca |
| `开始处理bin文件：pschent_full_tired` → `bin文件解压完成` | Fase 4 OK — ekstraksi bawaan |
| `开始检查更新` → `最新资源文件，无需更新` | Fase 5 OK — tanpa unduhan |

✅ **Kriteria lolos Tahap C**: kelima marker muncul berurutan dari server KITA
(cek access log server: hanya ada hit `setting_BS_Android.bin` + `com_db_local.bin`).
Bila `check_network`/`unknown_error` muncul → berhenti, perbaiki Tahap B, ulangi.

#### Tahap D — Replika CDN statis (Fase 5–8: sampai layar login)

Lengkapi server statis dengan mirror 1:1 (file disalin apa adanya dari arsip Tahap A,
**tanpa mengubah satu byte pun**):

```
/bs/index-native.html            ← salinan asli (WAJIB — tidak ada di APK)
/bs/manifest.json                ← salinan asli, 15 JS (WAJIB — tidak ada di APK)
/bs/js/*.js                      ← 15 file: union all.zip (11) + paket bawaan (4)
/bs/resource/default.res.json    ← salinan asli (7.844 entri; dari paket bawaan/all.zip)
/bs/resource/properties/serversetting.json  ← salinan asli (WAJIB — fallback login)
/bs/resource/assets/…            ← aset grup preload + login dulu, sisanya menyusul
/bs/upgrade/base.version         ← tulis "110"  (SAMA dengan bawaan APK)
/bs/upgrade/resource.version     ← tulis "11220" (SAMA dengan bawaan APK)
/bs/upgrade/size.json            ← salinan asli
/bs/upgrade/upgrade.json         ← salinan asli (tidak akan dipakai bila versi sama)
/bs/upgrade/base.zip, all.zip    ← salinan asli (cadangan bila versi dinaikkan nanti)
```

> **Dua kunci trik Tahap D:**
> 1. Version files diset **sama dengan bawaan APK** (110 / 11220) → jalur Fase 5 selalu
>    memilih cabang "最新资源文件，无需更新" → tidak ada unduhan → nol risiko patch salah.
>    Zip asli tetap diarsip untuk kebutuhan nanti.
> 2. Setelah boot pertama sukses, **cache lokal device sudah berisi 14 JS + resource**
>    (hasil ekstraksi bawaan). Request yang benar-benar menyentuh server tiap peluncuran
>    tinggal ±6 file kecil: setting bin, 2 version files, `index-native.html`,
>    `manifest.json`, `serversetting.json`. Sisanya dilayani native dari cache.
>    (Mirror lengkap tetap dipasang sebagai jaring pengaman cold-cache.)

✅ **Kriteria lolos Tahap D**: app boot **sampai layar login tampil**, tanpa satu pun
request ke domain asli (buktikan dari access log + `adb logcat` tidak ada DNS keluar).
Login BOLEH gagal terkoneksi — itu tahap E.

#### Tahap E — (menyusul, di luar lingkup "alur depan") Game server socket.io

Baru setelah D stabil berhari-hari: emulator socket.io v2 :610 → `loginGame` (Origin,
password `game_origin`) → `GetServerList` → `enterGame` → dst. (detail protokol §5).
Socket **sengaja diabaikan dulu** sesuai kesepakatan — layar login tampil tanpa server game.

#### Tahap F — (opsional, sangat belakangan) gameplay & battle

`startBattle`/`checkBattleResult` + `battlelogic.min.js` di Node; fitur sosial.

### 9.5 Risiko force close / salah config & mitigasi (revisi)

| # | Risiko | Gejala | Mitigasi |
|---|---|---|---|
| 1 | XOR setting bin keliru 1 byte | dialog `unknown_error` | Uji dekrip-ulang sebelum deploy (Tahap B) |
| 2 | Config server mati saat peluncuran | dialog `check_network` | Server harus selalu on; mode default = 0 |
| 3 | **Version file remote bukan angka murni** (mis. terbalik isi HTML 404) | dialog `Read Upgrade Config File Fail…` | Server statis kirim `110`/`11220` literal; uji curl |
| 4 | `manifest.json`/JS 404 atau versi JS campur (dari arsip beda periode) | hang, atau **RISIKO FORCE CLOSE** (4.11) | Mirror dari SATU snapshot Tahap A; jangan campur file dari waktu berbeda |
| 5 | Aset grup `preload`/`login` tidak lengkap | hang di loading; **RISIKO FORCE CLOSE** | Unduh penuh kedua grup; verifikasi vs `default.res.json` |
| 6 | Regenerasi nama file acak sendiri | crash native saat render | DILARANG — pakai peta `vvcc` bawaan apa adanya |
| 7 | Mode diset salah (`0`/`2`/`3`) | check_network / loginServer `"null"` / **layar kosong** | Selalu `testMode: 1` |
| 8 | `url` pakai HTTPS ke server sendiri tanpa TLS | hang / gagal load | Pakai **HTTP** (boleh, lihat 9.1 #1) ATAU TLS valid |
| 9 | Signature/versionCode bentrok | install gagal | Uninstall lama; versionCode jangan diturunkan |
| 10 | Anti-tamper (`libgavesec`/TiViCloud) | crash startup bila patch besar | Patch 1 file asset teks = tidak memicu; jangan strip SDK |
| 11 | `update` lupa diarahkan ke server sendiri | patch ASLI menimpa konten lokal | Setting bin kita: `update` → server kita; versi sama → tak pernah unduh |
| 12 | Data lama (cache `decodeBinFinish` + dir lama) bentrok dengan URL baru | boot aneh setelah ganti server | Setelah ganti URL, bersihkan data app (Clear Data) sebelum uji |

### 9.6 Yang TIDAK perlu dikerjakan

- Dekripsi ulang `pschent_full_tired.bin` untuk runtime — paket bawaan dipakai apa adanya; runtime memuat JS dari CDN (server kita).
- Re-implement engine Egret/V8 — `libegret.so` asli tetap jalan.
- Urusan TLS/domain — `cleartextTrafficPermitted=true` membuat HTTP cukup.
- Menulis ulang battle engine dari nol — `battlelogic.min.js` bisa jalan di Node (Tahap F).
- **Menyentuh socket/protokol game sebelum Tahap D stabil** — layar login tidak butuh server game.

> **Kriteria sukses alur depan (target saat ini)**: HP/emulator memasang APK hasil patch →
> boot 100% dari server standalone (tanpa domain asli) → layar login tampil penuh dengan
> gambar/teks benar. Force close nol. Baru setelah itu kita bicara protokol game (Tahap E+).

## 10. Artefak & Tools Riset

Repo ini sengaja hanya berisi **dokumentasi (README.md) + APK asli** — semua artefak
riset disimpan terpisah sebagai catatan kerja:

```
repo ini (github.com/anztdree/db-apk)
├── README.md        ← dokumen ini (satu-satunya dokumentasi)
└── DB-Local.apk     ← APK asli (Git LFS)
```

### Artefak riset lokal (di luar repo)
| Artefak | Isi |
|---|---|
| Dekriptor XXTEA `onepiece` (Python + Java) | dekripsi ZIP kode game, berfungsi 100% — sudah dipakai ulang untuk memverifikasi `pschent_full_tired.bin` (741 entri), `voyage_fancy_radius.bin` (161 entri), `vvcc.bin` (1.944), `vvcc_tc.bin` (139) |
| Skrip uji protokol (Node/socket.io-client v2.1.0) | port TEA `"verification"`, logger paket mentah, uji `handler.process` |
| `server-dump/` | setting server terdekripsi, `index-native.html`, `manifest.json`, `serversetting.json`, `base.version`/`resource.version`/`size.json`/`upgrade.json`, `all.zip` (652 entri) |
| Kode game terdekripsi | main.min.js (4,7 MB, 1.897 class) + battlelogic + 434 JSON + aset (versi paket bawaan) |

### Toolchain yang dipakai untuk bedah
androguard (manifest) · jadx 1.5.1 (Java decompile) · capstone + pyelftools
(disassembly libegret.so) · pycryptodome · js-beautify (main.min.js 4,7 MB → 126.080 baris)

---

## 11. Kesimpulan

- DB-Local.apk = klien H5 **"Super Fighter Idle"** (tema Dragon Ball) build lokal/test
  (Des 2021), dibuat developer China (namespace `zhuhuan`), dipublikasikan via QuickGame
  untuk pasar internasional.
- Tiga lapis enkripsi (XOR config → XXTEA kode → native key) **semuanya terpecahkan**
  karena semua kunci tertanam di klien.
- Protokol jaringan sepenuhnya terpetakan: socket.io v2.1.0, envelope
  `handler.process {type, action, version}` + response `{ret, data, compress}`
  (LZString UTF16), handshake verify TEA `"verification"`, 4 koneksi dengan
  `verifyEnable` berbeda, 60+ tipe / 200+ action, battle deterministik berbasis seed.
- **Server backend masih hidup** (login.popoh5.com:610 merespons socket.io handshake
  penuh, meski rate-limit) — jendela waktu untuk preservasi masih terbuka.
- **Alur depan (boot → layar login) terbedah TOTAL & terverifikasi ulang baris-per-baris**
  (§4): 21 item config terinventarisasi (4.10), peta titik gagal dipisah antara "cuma
  dialog/hang" vs "risiko force close" (4.11). Koreksi penting: `testMode: 3` = layar
  kosong — bukan opsi aman.
- **Temuan verifikasi ulang (baru):**
  - `index-native.html`, `manifest.json`, `serversetting.json` **TIDAK ada di APK** —
    3 file bootstrap ini wajib tersedia di server setiap peluncuran (permukaan minimal
    standalone); 14 JS + resource tetap ada lokal dari ekstraksi paket bawaan.
  - JS mirror tepat = **union** paket bawaan (14) + `all.zip` (11) → 15 file sesuai
    manifest CDN; instalasi baru mengunduh `all.zip` ±19,4 MB (bukan patch 250 KB).
  - Saklar engine render (`nativeRender`) TIDAK diset di setting produksi → param
    `nativeRender: NO` ke `libegret.so` — dan karena sumbernya setting bin, saklar ini
    kendali penuh standalone (§9.1).
  - Paket `voyage_fancy_radius.bin` = build varian TS/fairygui (161 entri), dipakai
    jalur IP-filter/maintenance — bukan mirror build utama.
- Rencana standalone tersusun ulang **bertahap mengikuti alur boot** (§9): Tahap A snapshot →
  B replika config → C patch APK + uji Fase 1–5 → D replika CDN sampai layar login →
  E/F server game (menyusul, socket diabaikan dulu). Setiap tahap punya kriteria verifikasi
  dan marker logcat. **Langkah paling mendesak: Tahap A (snapshot total) selagi server
  asli masih hidup.**
