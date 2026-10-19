# 🗂️ CONFIG SERVER FISIK — Semua Endpoint & Config dalam File yang Bisa Diedit Bebas

> **Prinsip (permintaan owner, 2026-10-19)**: *config bukan buatan kita — semuanya file config
> ASLI milik aplikasi/server yang isinya hanya diarahkan (atau dipanen 1:1)*. Setelah build
> test10/1.0.2, **seluruh alamat server dan respons server** tinggal di file fisik:
> bisa dibaca, dibuka, dan diedit kapan saja — di repo (sebelum build) maupun di perangkat
> (setelah install, tanpa build ulang).

---

## Peta 4 lapis config fisik

| # | File fisik | Milik siapa | Mengatur apa | Lokasi di APK | Lokasi di perangkat (bisa diedit) |
|---|---|---|---|---|---|
| 1 | `assets/config_BS.properties` | **asli aplikasi** | Endpoint CDN/game (`EntryPoint1`/`EntryPoint2` + `_debug`, base64) | `assets/config_BS.properties` | dalam APK (repack) |
| 2 | `assets/QHinfo` | **asli aplikasi** | Endpoint SDK QuickGame (`qsdk.t4game.com`) — base URL, path disambung kode: `base + /v1/…` | `assets/QHinfo` (plaintext, tanpa newline) | dalam APK (repack) |
| 3 | `setting_BS_Android.bin` | **asli SERVER** (XOR `DragonBall` → JSON) | `url` (game), `update` (CDN), `loginServer`, `loginPort` (610), `loginWebPort` (510), `testMode`, `clientParams` | `assets/mirror/bs/db/android/setting_BS_Android.bin` (versi penyambung) | `Android/data/com.db.local/files/server/bs/db/android/setting_BS_Android.bin` |
| 4 | Pohon `mirror/` (endpoint = nama file) | **panenan 1:1** dari server asli | Respons tiap endpoint — file yang ditanyakan app = file yang dijawab | `assets/mirror/<path>` | `Android/data/com.db.local/files/server/<path>` |

> **Kunci desain MiniServer** (`com/db/local/MiniServer.smali`, method `resolve()`):
> request `GET/POST /apapun` → dicari file `files/server/apapun` → fallback `assets/mirror/apapun`
> → tidak ada = 404 (persis perilaku server mati). **Tidak ada route yang di-hardcode di kode.**
> Menambah/mengubah endpoint = menambah/mengganti FILE, bukan mengubah smali.

---

## 1. `config_BS.properties` — endpoint CDN (lapisan 1.0.1, stabil)

Nilai kini (4 kunci, semua sama — aturan aplikasi: unduhan EntryPoint TERAKHIR yang dipakai):

```properties
EntryPoint1=aHR0cDovLzEyNy4wLjAuMToyODExMC9icy9kYi9hbmRyb2lk
EntryPoint1_debug=<sama>
EntryPoint2=<sama>
EntryPoint2_debug=<sama>
```

- `aHR0cDovLzEyNy4wLjAuMToyODExMC9icy9kYi9hbmRyb2lk` = base64 `http://127.0.0.1:28110/bs/db/android`.
- **Edit bebas**: ganti base64 di semua 4 kunci dengan base64 URL tujuan baru
  (`echo -n "http://ALAMAT/path" | base64`). 4 kunci WAJIB sama (Pelajaran 3 BUILD.md).

## 2. `QHinfo` — endpoint SDK (BARU di test10/1.0.2)

| Versi | Isi (plaintext persis, 1 baris tanpa newline) | Ukuran |
|---|---|---|
| Asli (1.0.1) | `http://qsdk.t4game.com` | 22 B |
| test10 (1.0.2) | `http://127.0.0.1:28110/qsdk` | 27 B |

- Kode SDK menyambung `String.format("%s%s", base, "/v1/...")` → test10:
  `http://127.0.0.1:28110/qsdk/v1/system/init` dst.
- **Edit bebas**: isi file ini adalah base SDK apa pun (lokal atau server lain).
  Jika ganti akhiran `/qsdk`, pindahkan nama folder `mirror/qsdk/…` agar cocok.
- Preseden sama: `config_BS.properties` di 1.0.1 (ganti nilai asset config, nol kode).

## 3. `setting_BS_Android.bin` — config server asli (XOR "DragonBall")

Isi asli server (711 B, didekode — **TIDAK dikarang, panenan 1:1 dari server produksi**):

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
  "clientParams": { "hideList": [...], "supportLang": [...], "showCurChannel": "toFacebook" }
}
```

- Versi di APK = **penyambung** (dibuat `apk/make_setting_bin.py` dari asli server:
  hanya `url`+`update` diarahkan ke `http://127.0.0.1:28110`, path+query dipertahankan
  persis, field lain byte-asli; 711 → 692 B).
- **Edit bebas**: decode (`XOR DragonBall`) → ubah field apa pun (`loginServer`,
  `loginPort`, `loginWebPort`, …) → encode ulang. Untuk 1.0.3 nanti, inilah tombol
  pengarah `:510`/`:610`. Perhatikan JSON harus tetap valid.

## 4. Pohon `mirror/` — endpoint = FILE (respons 1:1 panenan)

### 4a. Lapisan CDN/game (23 file, terbukti test6–test9)

```
mirror/bs/db/android/setting_BS_Android.bin   ← penyambung (lapis 3)
mirror/upgrade/{base.version,resource.version,size.json,upgrade.json,all.zip}
mirror/bs/{index-native.html, manifest.json, js/…×15}
```

### 4b. Lapisan SDK (BARU di test10 — 5 file, panenan RAW byte 1:1)

| File fisik (= path request) | Respons asli | Ukuran | SHA-256 |
|---|---|---|---|
| `mirror/qsdk/v1/system/init` | POST /v1/system/init | 764 B | `a6ab4470…` |
| `mirror/qsdk/v1/system/getNotice` | POST /v1/system/getNotice | 55 B | `424866f4…` |
| `mirror/qsdk/v1/user/registerVisitor` | POST /v1/user/registerVisitor | 2.345 B | `95e8d974…` |
| `mirror/qsdk/v1/user/registerUser` | POST /v1/user/registerUser | 2.397 B | `c4105fb9…` |
| `mirror/qsdk/v1/auth/getUserInfo` | POST /v1/auth/getUserInfo | 280 B | `dfc72556…` |

Sumber panenan: `docs/panenan-1.0.2/` (sha256 tercatat di `docs/RENCANA-1.0.2.md` §1c/1d).
Bukti replay: respons `init`/`getNotice` byte-identik antar sesi; auth guest stateless
(registerVisitor = fingerprint device saja); `:510` asli menerima token hasil replay (`code:0`).

### 4c. Cara edit di perangkat (tanpa build ulang)

1. File manager/ADB menuju `Android/data/com.db.local/files/server/`.
2. `resolve()` membaca folder ini **lebih dulu** daripada asset → suntingan langsung berlaku
   di peluncuran berikutnya (bootstrap hanya menyalin file yang BELUM ada — override manual
   tidak pernah ditimpa, desain test7).
3. Contoh: ganti respons `getNotice` = timpa file `server/qsdk/v1/system/getNotice`
   dengan teks JSON berformat sama. Tambah endpoint baru (mis. nanti `/v1/user/autoLogin`)
   = buat file dengan nama path itu. Hapus file = endpoint 404 (perilaku server mati).

> Catatan Android 11+: `Android/data` dibatasi di sebagian file manager — pakai ADB
> (`adb pull`/`adb push`) atau file manager dengan akses Shizuku/SAF.

---

## Yang TIDAK boleh dilanggar saat mengedit

1. **Set konsisten**: `manifest.json` ↔ 15 file `js/` ↔ `resource.version` ↔ isi `all.zip`
   adalah satu genggaman asli — jangan campur versi lintas sumber (anti force-close).
2. **setting bin** harus tetap JSON valid setelah encode ulang; `config_BS.properties`
   base64-nya harus valid dan 4 kunci seragam.
3. **Nama file mirror = path endpoint persis** (tanpa ekstensi untuk lapisan SDK —
   klien SDK mengabaikan Content-Type, jadi nama apa pun valid selama path cocok).
4. **Respons SDK** = byte JSON persis format panenan (parser SDK: byte → `JSONObject`).
5. Semua perubahan besar tetap lewat disiplin proyek: satu fase = satu build test
   (`offline-testN`) + bukti HAR/logcat sebelum naik rilis.
