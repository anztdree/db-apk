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

## 5. Audit menyeluruh config (2026-10-19, pra-build test10) — SEMUA kemungkinan sumber config diperiksa

> Pemicu: arah owner *"pastikan lagi config SDK, mungkin ada yang terlewat"*.
> Metode: APK asli SHA `3650cbe5…` dibedah habis — string table kedua dex
> (androguard `DalvikVMFormat`) + grep biner + xref `const-string`/`AssetManager->open`
> + meta-data manifest + grep 4 native lib `.so` + isi `assets/runtime-dex.jar`.

### 5a. Hasil: peta lengkap SEMUA file config di APK (aktif & tidak)

| File | Isi | Dibaca oleh | Status |
|---|---|---|---|
| `assets/QHinfo` (22 B) | `http://qsdk.t4game.com` | `QuickGameManager.initThirdDataSDK` (SATU-SATUNYA pemanggil) | ✅ **AKTIF** — satu-satunya sumber URL SDK; bukti: string `t4game`/`qsdk` = **0** di kedua dex & semua `.so` |
| `assets/config_BS.properties` (396 B) | 4 kunci EntryPoint | `LaunchActivity.ironoriginhoblike` via pola `config_%s.properties` | ✅ **AKTIF** — channel **"BS" di-hardcode** `LaunchActivity.onCreate` (`const-string 'BS'`) |
| `assets/config.properties` (208 B) | 2 kunci EntryPoint → base64 `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | method yang SAMA, cabang **IF/ELSE** ketika channel kosong | ⛔ **NONAKTIF** (fallback build tanpa channel — channel "BS" selalu ter-set; HAR perangkat: 0 request ke dragon) |
| `assets/quickgame_sdk/channel_id.txt` | channelId SDK | `feedtonight.nationalcommunitymissing` (GameSDKImpl init) | ❌ **TIDAK ADA di APK** → fallback meta-data `channelId` (juga tiada) → `"default"` |
| `assets/supplierconfig.json` (136 B) | appid vivo 100215079 | **tidak ada referensi** di dex maupun `.so` | ⛔ config mati (bawaan template channel) |
| `assets/adjust_config.properties` | config Adjust SDK | `Adjust ActivityHandler.readConfigFile` | ❌ TIDAK ADA → cuma log "not read", tak berpengaruh |
| `assets/game/infoinfo.bin` (38 B) | `voyage_fancy_radius\|pschent_full_tired` | LaunchActivity/MainActivity (preload engine) | lokal murni — manifest asset game, BUKAN config server |
| `assets/runtime-dex.jar` | 175 kelas egret runtime | ClassLoader dinamis | tanpa URL game (hanya resolver DNS publik `119.29.29.29` untuk socket.io) |
| `libegret.so` / `libgavesec.so` ×2 ABI | native engine | — | **BERSIH**: 0 string `t4game`/`qsdk`/`QHinfo`/`popoh5`/`config_BS`/`loginServer` |

### 5b. Konfirmasi alur IF/ELSE config_%s.properties (bukti gabungan smali + HAR)

```
LaunchActivity.onCreate:
  invoke-super onCreate          ← sisipan MiniServer.start() tepat setelah baris ini
  const-string v0, 'BS'          ← channel HARDCODE, selalu ter-set
  seventygenom.customreading = "BS"

LaunchActivity.ironoriginhoblike:
  if channel tidak kosong → kandidat = [ config_BS.properties ]   (config.properties TIDAK ikut)
  else                    → kandidat = [ config.properties ]       (fallback)
  → Properties.load(kandidat) → loop EntryPoint1..3 (+_debug) → unduh setting_<pkg>_Android.bin
```

Bukti silang HAR 1.0.1 (perangkat): `setting_BS_Android.bin` diambil **2× dari 127.0.0.1**
(EP1+EP2, keduanya lokal) dan **0 request** ke `dragon.sjmobilegame.com` → cabang fallback
tidak pernah jalan → dokumentasi lama TEPAT, tidak ada jalur config yang bocor.

### 5c. Implikasi praktis

1. **Config SDK lengkap = QHinfo saja.** Tidak ada file kedua. Mengganti base SDK = edit
   1 file 1 baris (repo: `apk/QHinfo`; perangkat: repack karena asset). 5 endpoint
   `mirror/qsdk/v1/…` = config respons (file fisik, bisa diedit di perangkat).
2. **Ingin set channelId SDK?** Buat file `assets/quickgame_sdk/channel_id.txt` (atau
   `server/quickgame_sdk/channel_id.txt` untuk versi perangkat via mirror) — SDK membacanya
   kalau ada, tanpa perlu ubah kode.
3. **`config.properties` JANGAN ikut diarahkan** — tidak terbaca pada build BS; keberadaannya
   tidak berpengaruh. Tidak perlu dipanen (fallback build lain, server `lzceshi` kemungkinan
   sudah mati).
4. **`supplierconfig.json` dibiarkan apa adanya** (1:1 original) — config mati, aman.

> ⚠️ Arahan titik 1 di atas (mirror/qsdk sebagai file respons) telah **diperbarui owner** →
> lihat **§6a**.

---

## 6. Peta config lengkap DUA LAPIS (2026-10-19, arah owner: "full config harus jelas" untuk local rasa online)

### 6a. Koreksi arah (peringatan keras owner)

**HAR = alat VERIFIKASI, bukan pola untuk ditiru.** Isi verifikasi HAR (4 capture owner):

| Layer | Trafik nyata di HAR | Status terverifikasi |
|---|---|---|
| Konten game (CDN pondasi) | `127.0.0.1:28110` → `bs/db/*`, `upgrade/*` | ✅ sudah running LOKAL (sejak 1.0.1) |
| SDK | `qsdk.t4game.com` (init, getNotice, registerVisitor/registerUser, getUserInfo) | ⏳ endpoint **MASIH di server asli** |
| Loginserver game | `login.popoh5.com:510` | masih server asli → ranah **1.0.3** |

Akibat koreksi: file `mirror/qsdk/` hasil replay HAR + perubahan `QHinfo` 22→27 B pada
rebuild `e6e8ad4d` = **salah arah, tidak dipakai**; koreksi build menyusul keputusan owner.
Tujuan akhir yang dikonfirmasi owner: **local rasa online beneran** → semua config harus jelas
(dokumen ini), bukan tiruan hitam-putih.

### 6b. Lapis 1 — file config fisik di APK (lokal, isi asli apa adanya)

| File | Isi asli | Status |
|---|---|---|
| `assets/QHinfo` 22 B | `http://qsdk.t4game.com` | ✅ AKTIF — satu-satunya penentu base URL SDK |
| `assets/config_BS.properties` 257 B | EntryPoint1/2 = `http://127.0.0.1:28110/bs/db/android` | ✅ AKTIF — server konten game (lokal, terbukti) |
| `assets/config.properties` 208 B | EntryPoint1 = `http://dragon.sjmobilegame.com:82/lzceshi/native_setting/Android` | ⛔ NONAKTIF (fallback else-branch) |
| `assets/supplierconfig.json` 136 B | vivo appid 100215079; xiaomi/huawei/oppo kosong | ⛔ config mati (0 referensi) |
| `assets/quickgame_sdk/channel_id.txt` | **TIDAK ADA** → channelId = `"default"` | bisa dibuat kapan saja tanpa kode |
| `assets/adjust_config.properties` | TIDAK ADA | tidak berpengaruh |

### 6c. Lapis 2 — config RUNTIME server = respons `POST /v1/system/init` (isi asli penuh)

Inilah "config server" sesungguhnya — dikirim `qsdk.t4game.com` tiap startup
(request: `sv=v2` + `data`=base64 JSON fingerprint device + `sign`=MD5). Isi 1:1 panenan:

```json
{
 "result": true,
 "data": {
  "payTypes": [],
  "version": {"versionName":"empty","versionNo":"empty","versionUrl":"empty",
              "updateTime":"empty","isMust":"empty","updateTips":"empty"},
  "productConfig": {
   "useServiceCenter":"0","isShowFloat":"0","showShare":"1","title":"","logo":"",
   "useSms":"0","skinStyle":"0","serviceInfo":"","guestShowBind":1,"single_play":0,
   "adZhifuSwt":0,"noticeNode":0,"regMailVerify":0,"pycenttype":0,"onGoToPUrl":0,
   "onGoToPUrlData":"","trashTime":0,"noFirebaseLog":0,"logEvtReport":0
  },
  "otherpayType": 2, "payFast": [], "isNotGift": 0, "showShare": "1",
  "requestIp": "182.9.2.96", "showDiscount": 0,
  "nodes": {"deleteAccount":0,"serverInfo":0,"oneYeaerTips":0,"removeEmail":0},
  "loginTypes": ["1","13"],
  "countryCode": "ID", "first_install": 0
 },
 "error": {"id": 0, "message": ""}
}
```

Arti field yang mengatur perilaku (nilai asli game ini):

| Field | Nilai asli | Arti |
|---|---|---|
| `loginTypes` | `["1","13"]` | **GOVERNOR metode login** (lihat 6d) |
| `version.*` | semua `"empty"` | tidak ada paksaan update |
| `productConfig.regMailVerify` | `0` | **register email TANPA verifikasi** |
| `productConfig.guestShowBind` | `1` | tamu diminta bind akun |
| `productConfig.isShowFloat` / `useServiceCenter` / `useSms` / `skinStyle` | `0/0/0/0` | float menu, CS, SMS, tema = mati |
| `nodes.deleteAccount/removeEmail` | `0` | menu hapus akun & email disembunyikan |
| `payTypes`/`payFast` | `[]` | pembayaran tidak aktif |

### 6d. Bukti kode: bagaimana `loginTypes` mengatur login/register (baksmali classes.dex)

`com.quickgame.android.sdk.model.e` men-parse `loginTypes`, lalu cek per metode dengan
`loginTypes.toString().contains("N")`:

| Tipe | Jalur | Efek (bukti kode/panenan) |
|---|---|---|
| **1** | inti (bukan flag) | **Tamu/Visitor** — auto `POST /v1/user/registerVisitor`, payload = fingerprint device SAJA (stateless) |
| **13** | inti (bukan flag) | **Akun Email** — `registerUser` (username + password MD5), `loginByName`, `sendCodeByEmail` |
| 6 | flag `model.e` | Facebook — **default jika loginTypes hilang**: log `"missing loginTypes，only show default fb login"` |
| 8 / 17 | flag + `GoogleApiAvailability` (`"support google login"`) | Google / Play Games |
| 9,10,11,12,14 | flag `model.e` | HW/VK/Twitter/Line/Naver dst. (resource `hw_vk_login`, `tomato_login_line`, … ada di APK) |

Rantai lengkap: startup → baca `QHinfo` → `POST init` → server kirim config →
`LoginFragment` (`sdk/f/a/d.smali`) bangun UI sesuai `loginTypes` → tamu auto
`registerVisitor` / email `registerUser`→ sesi via `getUserInfo`
(`authToken` encoding `@NN@` XOR + uid).

Endpoint dorman di kode (tidak aktif utk game ini, baru hidup jika config menyuruh):
`ckRegVistor`, `findPassByEmail`, `getServerAgreement`, `userLoginByOtherSdk`,
`postGooglePlayVerify`, `fbShareEvent`, `setGameRoleInfo`, `jianchaLayLx`.

### 6e. Keuntungan terkonfirmasi: register TANPA verifikasi email

`regMailVerify=0` adalah **config server asli** (bukan buatan) → `registerUser` cukup
`username` + `password(MD5)` — **tidak butuh server email sama sekali**. Panenan membuktikan:
respons `registerUser` sukses langsung `result:true, isNewUser:1, uid, token`.
→ saat tahap "local rasa online", registrasi akun lokal jalan penuh tanpa dependensi email.

### 6f. Persiapan tahap "local rasa online" (BELUM dieksekusi — menunggu owner)

1. `QHinfo` diputar ke MiniServer (1 baris, keputusan owner kapan pun).
2. 5 respons endpoint (`init`, `getNotice`, `registerVisitor`, `registerUser`, `getUserInfo`)
   menjadi file config yang bisa diedit — sumber asli: HAR panenan di `log/`
   (verifikasi silang 5/5 byte-identik antar capture, termasuk HAR terhapus).
3. Catatan replay-safety: `getNotice` statis penuh (3 sesi identik); `init` hanya
   `countryCode`/`requestIp` berubah; `registerVisitor`/`registerUser`/`getUserInfo`
   memuat identitas sesi (uid/token) → satu identitas utk semua perangkat sampai
   loginserver (1.0.3) menangani dinamika akun.

---

## 7. MESIN yang belum ada (2026-10-19, arah owner: "ada otak tapi mesin gak ada, gimana mau running — jangan asal implementasi")

### 7a. Diagnosis: MiniServer hari ini = pemutar file statis

MiniServer in-APK (`127.0.0.1:28110`, resolve `path → files/server → assets/mirror → 404`)
**cukup** untuk Lapis 1 (konten game — terbukti 1.0.1) tapi **TIDAK cukup** untuk SDK
"local rasa online", karena 3 endpoint akun bersifat DINAMIS. Bukti dari panenan:

1. **Server asli mengingat tamu per perangkat** — `registerVisitor` dari dua perangkat
   berbeda (`389050BAB…` vs `2F188C67…`) dibalas dua akun tamu lama berbeda
   (`pt88098516`/uid 28503874 vs `cz90406643`/uid 28486093), keduanya `isNewUser:0`
   → server memetakan **fingerprint device → akun tamu persisten** (lookup/create).
2. **`getUserInfo` = profil per sesi** — `lastDev` (perangkat peminta), `lastLoginTime`,
   `regIp` di-generate server, bukan data mati.
3. **`registerUser` membaca permintaan** — username + password(MD5) dari request → akun
   baru (uid baru). File statis = semua registrasi balik akun yang sama = bukan register.

### 7b. Tabel kebutuhan per endpoint: statis vs dinamis

| Endpoint | File statis cukup? | Yang HARUS dihitung mesin |
|---|---|---|
| `/v1/system/init` | ✅ YA — config murni (otak) | — |
| `/v1/system/getNotice` | ✅ YA — statis 3 sesi | — |
| `/v1/user/registerVisitor` | ❌ | lookup fingerprint→akun tamu; buat baru bila tak dikenal (uid, username pola `2 huruf+8 digit`, token, authToken) |
| `/v1/user/registerUser` | ❌ | ambil username+password dari request → uid baru, `isNewUser:1`, simpan; tolak username duplikat (error.id) |
| `/v1/auth/getUserInfo` | ❌ | baca uid+authToken dari request → profil akun itu + `lastDev` perangkat peminta |
| (dorman) `loginByName` | ❌ | cek kredensial dari store (untuk login email di kemudian hari) |

### 7c. Lima komponen mesin yang belum ada (semua = nol hari ini)

1. **Parser POST** — baca `sv`/`data`/`sign`, decode base64 → JSON (ambil `deviceId`,
   `serialNum`, `username`, `password`, `uid`, `authToken`). Saat ini body cuma
   di-drain (fix classes2.dex), TIDAK pernah di-parse.
2. **Store akun** — file persisten di dir privat app (mis. `getFilesDir()/qgsdk_accounts.json`):
   peta `deviceId → akun tamu` + daftar akun email (username, hash, uid, token).
   Hilang saat uninstall (konsekuensi dikenal).
3. **Generator identitas** — uid unik (8 digit), username tamu (pola panenan:
   `cz90406643`, `pt88098516` = 2 huruf + 8 digit), token MD5-like, dan **encoding
   authToken `@NN@`** (XOR — algoritma persisnya harus dibalik dari fungsi encode SDK
   di smali, satu fungsi; mesin harus menghasilkan format yang SDK itu sendiri decode).
4. **Builder respons** — susun JSON per skema yang SDK harapkan. Sumber skema =
   **kode parser SDK sendiri** (kelas `model.*` hasil baksmali — otoritatif);
   panenan = verifikasi bahwa skema itu benar (bukan pola untuk ditiru buta).
5. **Router cerdas** — `/v1/system/*` → file config statis (otak, editable);
   `/v1/user/*` + `/v1/auth/*` → mesin (dinamis). Static mirror TETAP untuk game content.

### 7d. Di mana mesin tinggal — dua opsi

| Opsi | Isi | Plus | Minus |
|---|---|---|---|
| **B. Mesin di dalam APK** (rekomendasi) | perluas MiniServer (classes2.dex) dgn modul akun kecil (satu kelas: parse→logic→build→store) | benar-benar offline on-device, tanpa PC; konsisten dgn arsitektur proyek (MiniServer sudah in-APK, QHinfo cukup 1 baris) | kerja smali/DEX nyata; butuh disiplin build seperti sebelumnya |
| **C. Mesin di luar** (PC/HP lain sbg server) | server config+akun (Python/Node), QHinfo menunjuk IP LAN | mudah dibangun/di-debug | butuh mesin hidup = bukan "local" murni; QHinfo perlu IP eksternal |

### 7e. Batas tahap ini (jangan overshoot)

- Mesin tahap SDK = **5 endpoint SDK saja**. Setelah SDK login, GAME bicara ke
  loginserver sendiri (`login.popoh5.com:510`) → itu mesin berbeda, ranah **1.0.3**.
- Urutan sehat: (1) balik tes10 ke arah benar → (2) mesin akun + QHinfo flip →
  (3) uji 2 peluncuran: tamu sama antar-restart, register email baru sukses,
  getUserInfo konsisten → (4) baru loginserver 1.0.3.
- Kasus error (`username` sudah ada → `error.id`): nilai id diambil dari konstanta
  error di kode SDK (baksmali), bukan dikarang.

---

## 8. KEPUTUSAN FINAL: STANDALONE — semua mesin dalam 1 APK (arah owner 2026-10-19)

> Owner: *"saya cuma punya Android, dan ini proyek rencana STANDALONE apk — artinya semua
> mesin server kalau bisa dalam 1 apk ini"*. → **Opsi C (mesin luar/LAN) DITOLAK.
> Opsi B (mesin dalam APK) DIKUNCI.**

### 8a. Arsitektur 1-APK tiga mesin bertingkat

| Mesin | Status | Letak | Tugas |
|---|---|---|---|
| 1. MiniServer statis | ✅ ada | classes2.dex | konten game (`bs/db`, `upgrade`) + file config SDK (`/v1/system/*`) |
| 2. **Mesin akun SDK** | 🔨 akan dibangun | classes2.dex (modul baru) | `/v1/user/*` + `/v1/auth/*` dinamis: parse POST → store → generate → build respons |
| 3. Mesin loginserver game | ⏳ 1.0.3 | classes2.dex | pengganti `login.popoh5.com:510` |

`QHinfo` akhir = `http://127.0.0.1:28110/qsdk` (27 B) — **flip dilakukan BERSAMAAN dengan
mesin siap**, tidak sebelum itu (pelajaran: jangan arahkan endpoint ke server yang mesinnya
belum ada). Catatan jujur: QHinfo 27 B + 5 file panenan pada rebuild lama `e6e8ad4d` =
versi **prematur** dari keadaan akhir ini — arahnya ternyata benar untuk standalone, tapi
eksekusinya melompat (mesin belum ada). Materi dipakai ulang saat mesin siap.

### 8b. Blueprint mesin akun (dalam classes2.dex)

- **Input**: body POST (`sv`/`data`/`sign`) → decode base64 → JSON
  (`deviceId`, `serialNum`, `username`, `password`, `uid`, `authToken`)
- **Store**: `getFilesDir()/qgsdk_accounts.json` — peta `deviceId → akun tamu`
  (persisten antar restart, dikenali seperti server asli) + daftar akun email
  (username, hash, uid, token). Hilang saat uninstall (konsekuensi dikenal).
- **Generator**: uid 8 digit; username tamu pola `2 huruf + 8 digit` (`cz90406643`,
  `pt88098516`); token MD5-like; **authToken encoding `@NN@`** — algoritma dibalik dari
  fungsi encode SDK di smali (mesin wajib menghasilkan format yang SDK-nya sendiri decode).
- **Builder**: JSON sesuai skema **kode parser SDK** (`model.*` baksmali = otoritatif);
  panenan = verifikasi skema. `lastDev`/`lastLoginTime` di-generate dari request nyata.
- **Router MiniServer ditingkatkan**: `/v1/system/*` → file statis (otak, editable);
  `/v1/user/*`,`/v1/auth/*` → mesin; sisanya → mirror game content (perilaku lama utuh).

### 8c. Persiapan teknis pra-koding (reverse dari SDK, bukan karangan)

1. Balik algoritma `@NN@` (fungsi encode authToken di smali SDK).
2. Ambil konstanta `error.id` (username duplikat, dll.) dari kode SDK.
3. Daftar field wajib tiap respons dari kelas parser `model.*` (cross-check panenan).

### 8d. Rencana build & uji (disiplin: satu fase = satu build test)

1. Tulis mesin (smali) → assemble classes2.dex → `check_invoke_regs` bersih.
2. QHinfo flip 27 B + file config `mirror/qsdk/v1/system/{init,getNotice}`.
3. zip + zipalign + sign v1/v2/v3 → **test10 standalone**.
4. Uji perangkat (HAR baru = VERIFIKASI): (a) init/getNotice terlayani lokal;
   (b) tamu → uid SAMA antar restart; (c) register email baru → `isNewUser:1`,
   uid baru; (d) `getUserInfo` konsisten; (e) uninstall→install = store reset (dikenal).
5. SDK hijau penuh → baru mesin loginserver (**1.0.3**). Offline total =
   setelah 1.0.3 (game sendiri masih panggil popoh5 di tahap SDK).

---

## 9. CARA KERJA GABUNGAN: OTAK (config) × MESIN (akun) saat runtime
(2026-10-19, arah owner: *"mesin itu gimana, kalau digabung dengan config tadi cara kerjanya jadi gimana?"*)

### 9a. Satu kalimat

**OTAK memutuskan, MESIN mengeksekusi, ROUTER yang menjodohkan.**
Config tanpa mesin = instruksi tanpa tangan (keadaan hari ini). Mesin tanpa config =
tangan tanpa perintah (tidak tahu harus menyediakan apa). Digabung, keduanya menjadi
satu "server SDK" utuh yang kebetulan tinggal di dalam APK sendiri.

### 9b. Tiga titik sambungan mereka

| # | Titik sambung | Otak bilang | Mesin terima |
|---|---|---|---|
| 1 | `QHinfo` (Lapis 1) | "bicaralah ke `http://127.0.0.1:28110/qsdk`" | SEMUA trafik SDK mendarat di ROUTER MiniServer |
| 2 | respons `init` (Lapis 2) | `loginTypes ["1","13"]`, `regMailVerify:0`, `payTypes:[]`, `version:"empty"` | **daftar tugas mesin terkunci**: hanya registerVisitor, registerUser, loginByName, getUserInfo |
| 3 | ROUTER (per path) | `/v1/system/*` = konten statis otak (file editable) | `/v1/user/*` + `/v1/auth/*` = pekerjaan dinamis mesin |

Poin 2 adalah inti pertanyaan ini: **config bukan pajangan — ia menuliskan daftar
pekerjaan mesin**. Karena otak asli hanya menyalakan 2 metode login dan mematikan
semua fitur lain (§6c), mesin CUKUP 3 handler dinamis + 2 file statis. Delapan
endpoint dorman (§6d) tak pernah perlu handler.

### 9c. Alur satu peluncuran (jalur TAMU) dari awal ke akhir

```
[APK start]
   │ MiniServer hidup dulu (127.0.0.1:28110)   ← syarat urutan startup
   ▼
SDK baca QHinfo ──► "server SDK = APK-nya sendiri"
   ▼
POST /qsdk/v1/system/init ──► ROUTER ──► FILE CONFIG (otak, editable)
   ◄── loginTypes ["1","13"], regMailVerify:0, version "empty", …
   ▼
SDK bangun UI login: hanya [Tamu] + [Email]      ← keputusan OTAK
   ▼ (user tidak apa-apa → tamu auto)
POST /v1/user/registerVisitor {deviceId,…} ──► ROUTER ──► MESIN
   │ 1. PARSER : decode sv/data/sign → deviceId
   │ 2. STORE  : qgsdk_accounts.json — deviceId ini dikenal?
   │      dikenal → pakai akun lama (isNewUser:0)  ← persis server asli (panenan: 2 device = 2 tamu lama)
   │      asing   → GENERATOR: uid 8 digit + username 2huruf-8digit + token + authToken @NN@
   │ 3. STORE  : tulis pemetaan baru (persisten antar restart)
   │ 4. BUILDER: JSON sesuai skema parser SDK (model.*) → dibalas
   ▼
POST /v1/auth/getUserInfo ──► MESIN → profil sesi (lastDev/lastLoginTime di-generate)
   ▼
SDK serah uid+token ke game ──► game konek loginserver (popoh5:510)
   = mesin ke-3, ranah 1.0.3 — DI LUAR batas tahap ini (§7e)
```

### 9d. Jalur EMAIL — dan kenapa jalan TANPA server email

```
POST /v1/user/registerUser {username, password MD5} ──► MESIN
   1. PARSER  : ambil username + password
   2. STORE   : username sudah dipakai? → error.id (konstanta dari kode SDK, bukan karangan)
   3. GENERATOR: uid baru → STORE tulis akun email
   4. BUILDER : result:true, isNewUser:1, uid, token
```
TIDAK ADA langkah email sama sekali — karena OTAK bilang `regMailVerify=0`
(config server asli, §6e). Panenan membuktikan `registerUser` asli sukses tanpa
interaksi email apa pun. Inilah "keuntungan dari sana" yang dimaksud owner.

### 9e. Peluncuran ke-2 — ingatan itu STORE, bukan kebetulan

Device sama → PARSER baca deviceId sama → STORE hit → akun tamu SAMA persis
(uid sama, username sama) → `isNewUser:0`. Ini meniru satu-satunya perilaku server
asli yang terbukti di panenan: **fingerprint device → akun tamu persisten**.
Uninstall = store hilang (getFilesDir) = tamu baru — konsekuensi dikenal (§8b).

### 9f. Tabel kendali: setiap field config → akibatnya ke mesin

| Config (otak) | Nilai sekarang | Akibat ke mesin |
|---|---|---|
| `QHinfo` | (setelah flip) `127.0.0.1:28110/qsdk` | semua request tiba di router kita |
| `loginTypes` | `["1","13"]` | mesin WAJIB 4 jalur: registerVisitor, registerUser, loginByName, getUserInfo — dan CUMA itu |
| `loginTypes` diubah +8 (Google) | jangan dulu | mesin harus tumbuh handler `postGooglePlayVerify` dll. — jalurnya tak pernah terobservasi panenan |
| `regMailVerify` | `0` | mesin TIDAK perlu `sendCodeByEmail`; jika diubah `1` → SDK sendiri menuntut kode email → mesin harus tumbuh handler itu |
| `version.*` | `"empty"` | mesin tidak perlu endpoint cek-update |
| `payTypes` / `payFast` | `[]` | mesin tidak perlu endpoint pembayaran |
| `nodes.*` | `0` semua | menu terkait disembunyikan SDK → endpoint terkait tetap dorman |
| `guestShowBind` | `1` | UI menampilkan ajakan bind tamu→akun; jalur bind TIDAK pernah terobservasi di panenan → endpoint-nya harus diidentifikasi dari smali SEBELUM mesin dijanjikan bisa bind |
| `getNotice` | statis 3 sesi | cukup file statis |

Baris `loginTypes` + `regMailVerify` = alasan mesin sekecil ini CUKUP:
**otak mengecilkan mesin, bukan membesarkannya.**

### 9g. Aturan main gabungan (pelajaran QHinfo prematur, diulang di sini)

1. **Jangan nyalakan fitur di config yang mesinnya belum ada.** Persis kasus
   `e6e8ad4d`: endpoint diarahkan duluan, mesin nihil. Aturan sama berlaku per
   field — edit `loginTypes`/`regMailVerify` hanya SETELAH handler-nya ada.
2. **Skema respons tetap terkunci ke parser SDK** (`model.*` baksmali = otoritatif):
   config mengubah KONTEN (nilai field), bukan BENTUK (field apa saja yang wajib ada).
3. **Panenan tetap alat VERIFIKASI**, bukan pola: respons mesin dibandingkan
   STRUKTUR-nya dengan panenan; byte-identik justru TIDAK diharapkan — isi yang
   berbeda adalah tanda mesin benar-benar bekerja.

### 9h. Ringkasan satu paragraf

Digabung, "config + mesin" = satu server SDK pribadi di dalam APK. Config (file
editable) memberi tahu SDK ke mana bicara (QHinfo), metode login apa yang tampil
(loginTypes), dan fitur apa yang nyala (productConfig/nodes) — dan justru karena
config aslinya hemat, daftar tugas mesin jadi pendek: 2 file statis + 3 handler
dinamis (parse → lookup/generate → build). Otak tidak bisa jalan tanpa tangan
(keadaan sekarang); tangan tidak tahu bekerja tanpa otak. Keduanya baru terasa
"local rasa online" ketika QHinfo di-flip PERSIS setelah mesin siap (§8d).

---

## 10. PRINSIP PONDASI UNTUK TAHAP SDK: server = FILE FISIK
(arah owner: *"seharusnya ada config yang mengatur — seperti pada pondasi terdapat file fisik server"*)

### 10a. Prinsip yang sudah terbukti di pondasi

MiniServer `resolve()`: request apa pun → cari `files/server/<path>` → fallback
`assets/mirror/<path>` → tidak ada = 404. **Semua yang dijawab server = file fisik;
mengedit file = mengedit server.** Bootstrap menyalin asset → files/server sekali di
awal; override manual tidak pernah ditimpa (desain test7).

### 10b. Peta pondasi ↔ tahap SDK — peran file fisik yang setara

| Peran | Pondasi (game — ✅ jalan sejak 1.0.1) | Tahap SDK (saat mesin siap + flip) |
|---|---|---|
| Penentu ALAMAT (asset APK; repack utk edit) | `config_BS.properties` → EntryPoint1/2 = `127.0.0.1:28110/bs/db/android` | `QHinfo` → `http://127.0.0.1:28110/qsdk` (27 B) |
| **Config runtime server** (file editable di perangkat) | `files/server/bs/db/android/setting_BS_Android.bin` (XOR DragonBall) | `files/server/qsdk/v1/system/init` (JSON editable: `loginTypes`, `regMailVerify`, …) |
| Respons statis lain | `files/server/bs/*`, `files/server/upgrade/*` | `files/server/qsdk/v1/system/getNotice` |
| MEMORI server (file fisik juga) | — (konten tidak butuh ingatan) | `qgsdk_accounts.json` (dir privat app): peta `deviceId → akun tamu` + akun email |
| Dihitung, bukan file | — | `registerVisitor`/`registerUser`/`getUserInfo`/`loginByName` = **output mesin** |

Kalau dibandingkan: `init` bagi SDK = persis `setting_BS_Android.bin` bagi game —
config server yang sudah jadi file fisik yang bisa diedit di perangkat, dengan
resolve() yang sama.

### 10c. Kenapa file config SDK BELUM ada di perangkat hari ini

`QHinfo` masih `qsdk.t4game.com` → trafik SDK **belum pernah mampir** ke MiniServer →
`files/server/qsdk/*` belum terbentuk (tidak ada yang memintanya). File-file itu baru
ter-copy (bootstrap) dan hidup saat: **mesin siap + QHinfo di-flip** (§8d). Lima file
`assets/mirror/qsdk/` dari rebuild prematur `e6e8ad4d` = contoh wujud fisiknya — masuk
koreksi bukan karena bentuknya salah, tapi karena mesinnya nihil (§6a).

### 10d. Batas jujur prinsip file

1. Tiga endpoint dinamis **tidak mungkin file murni** — server asli menghitung
   respons dari isi request (bukti §7a: guest per-device, profil per-sesi, register
   membaca request). Yang tetap fisik: SEMUA config statis + memori mesin (store).
2. Opsional (bila ingin 100% file): ubah 1 method smali `initThirdDataSDK` agar
   `QHinfo` ikut aturan resolve (`files/server/QHinfo` dulu → fallback asset).
   Tidak wajib — QHinfo cukup diset sekali saat flip, dan +kode = +risiko.

---

## 11. FILE FISIK YANG "SEBENARNYA" — TIGA LOKASI
(arah owner: *"127.0.0.1:28110/qsdk bukan alamat SDK sebenarnya"* + *"API/socket pasti punya
file fisik asli yang tersembunyi"* — dua-duanya benar, dijawab tuntas di sini)

### 11a. Alamat — diluruskan

- **Alamat SDK SEBENARNYA** = `http://qsdk.t4game.com` (22 B — QHinfo asli release 1.0.1;
  audit per-entry commit `a2f7ace`; HAR memang mengirim ke sana).
- `http://127.0.0.1:28110/qsdk` (27 B) = **target flip buatan kita** — bukan alamat asli,
  belum seharusnya diterapkan. Working tree `apk/QHinfo` masih memuat versi prematur
  `e6e8ad4d` (koreksi tes10 pending — memang belum dikoreksi). Kolom "Tahap SDK" di §10b
  menampilkan target flip itu; supaya tak disalahpahami: itu ALAMAT TUJUAN, bukan alamat asli.

### 11b. Tiga lokasi file fisik

| Lokasi | Ada apa di sana | Kita pegang? |
|---|---|---|
| 1. **Server asli** (`qsdk.t4game.com`) | kode API + database + config server mereka = **DAPUR** | ❌ TIDAK pernah ikut ke kita — yang keluar dari sana hanya RESPONS; yang bisa kita lakukan = mengamati (HAR) |
| 2. **APK** (sisi klien) | `assets/QHinfo` (22 B asli) + **classes.dex SDK** = file fisik "tersembunyi" yang sesungguhnya di sisi kita: di dalamnya KONTRAK API lengkap — path endpoint, format request (`sv`/`data`/`sign` MD5), skema respons (kelas `model.*` parser), logika `loginTypes`, konstanta `error.id`, encoding `authToken @NN@` | ✅ **PUNYA** — dibedah via baksmali. Hasil ekstraksi `/tmp/smali_out` hilang saat sandbox reset, tapi itu hanya HASIL ekstraksi: APK base (`a946ef6c`, asset release) + resep BUILD.md + toolchain Maven Central = bisa dibedah ulang kapan saja (sudah pernah dua kali) |
| 3. **Perangkat kita** | `files/server/qsdk/*` + `qgsdk_accounts.json` | 🔨 file KITA — lahir saat mesin siap + flip (meniru PERAN server, bukan meniru file server) |

### 11c. Bukti dapur server = HASIL HITUNGAN, bukan file yang dilayani

- Endpoint yang sama, jawaban berbeda per perangkat: 2 device → 2 akun tamu lama berbeda
  (`isNewUser:0`); `getUserInfo` → `lastDev`/`lastLoginTime`/`regIp` berubah;
  `registerUser` → respons mengikuti username+password di request.
- File murni TIDAK bisa bersikap begini (file = byte yang sama tiap kali dilayani).
- **Kecuali dua**: `getNotice` (55 B, identik 3 sesi) = perilaku file murni bahkan di
  server asli; `init` (764 B, hanya `requestIp` berubah) = nyaris file config murni.
  Intuisi owner BENAR sampai ke server mereka: kemungkinan besar `init` di sana juga
  dibaca dari config file mereka sendiri — tapi file itu tidak bisa diambil; yang bisa
  diamati hanya isinya yang terkirim (panenan) dan kontraknya (smali). Server asli
  sendiri CAMPURAN file + hitungan — persis tabel statis-vs-dinamis §7b.

### 11d. Akibat desain (mengunci arah)

Setengah dari pasangan API (sisi server) tidak bisa diambil → kita **TULIS ULANG
MESINNYA**, bukan mengambil file servernya. Kontrak dikunci ke file fisik yang KITA
pegang: **smali SDK = otoritatif** (skema `model.*`, konstanta `error.id`, algo `@NN@`
tinggal dibalik — bukan ditebak), **panenan = verifikasi**. Akibatnya pilihan bentuk
menjadi natural: statis → file fisik; dinamis → mesin hitung.

---

## 12. SYARAT KEPERCAYAAN LOCAL RUNNING — tesis owner sebagai kriteria penerimaan
(arah owner: *"qsdk.t4game.com alamat yg benar; kalau config SDK dapat dipanen dari server
asli maka baru bisa local running ke 127.0.0.1:28110/qsdk; kalau config ga dapat, local
running = bohong, path flip = sampah"*)

### 12a. Tesis diterima — dengan satu pemerkasaan

Tesis benar: **flip tanpa panenan asli = bohong.** Pemerkasaannya: yang bisa dipanen dari
server asli ada DUA jenis, dan masing-masing cara panennya beda:

1. **CONFIG** (`init`, `getNotice`) — di server mereka pun berperilaku file (§11c) →
   bisa dipanen **byte-exact** → sudah dilakukan.
2. **RESPONS AKUN** (`registerVisitor`, `registerUser`, `getUserInfo`) — di server asli
   pun **hasil hitungan**, bukan config → yang bisa dipanen = **KONTRAKnya** (skema +
   perilaku per-device), bukan byte-nya. Memaksakan replay byte-nya justru bentuk
   bohong yang lain: satu identitas untuk semua perangkat (kesalahan prematur `e6e8ad4d`).

### 12b. Checklist syarat "local jujur" — status hari ini

| # | Syarat | Status | Bukti di repo |
|---|---|---|---|
| 1 | Config `init` dipanen dari server asli | ✅ **SUDAH** | `docs/panenan-1.0.2/qsdk_v1_system_init.json`; mirror 764 B — sha256 `a6ab4470…`; byte-identik antar sesi (hanya `requestIp` berubah) |
| 2 | `getNotice` dipanen | ✅ **SUDAH** | 55 B — sha256 `424866f4…`; identik 3 sesi |
| 3 | Kontrak endpoint akun (skema + perilaku) | ✅ **SUDAH** | **Sumber = smali SDK** (classes.dex: path, `sv`/`data`/`sign` MD5, kelas `model.*`) — panenan 3 endpoint (2345/2397/280 B + `req_*.txt`) = **VERIFIKASI kontrak**, bukan sumbernya (lihat 12d) |
| 4 | **MESIN** akun per-device | ❌ **BELUM — nol** | blueprint saja (§7c/§8b) |
| 5 | Koreksi tes10 (QHinfo kembali 22 B `qsdk.t4game.com`) | ⏳ pending | working tree `apk/QHinfo` masih 27 B prematur |
| 6 | Flip QHinfo → `127.0.0.1:28110/qsdk` | ⛔ **TAHAN** | baru boleh SETELAH #4 selesai (§8d) |
| 7 | Verifikasi HAR baru (tamu sama antar-restart, register email baru, getUserInfo konsisten) | ⏳ setelah build | HAR = alat verifikasi, bukan sumber |

**Menurut kriteria owner sendiri: syarat "config dapat" = TERPENUHI (1–3 ✅).
Yang belum ada = mesin (#4) — dan mesin memang bukan sesuatu yang dipanen,
sesuatu yang DIBANGUN.**

### 12c. Konsekuensi tesis (diterima sebagai aturan main)

1. Jika #4 tidak pernah selesai → flip TIDAK BOLEH dilakukan → QHinfo tetap
   `qsdk.t4game.com` → **tidak ada yang bohong, tidak ada sampah** (koreksi tes10
   mengembalikan keadaan jujur ini).
2. Path `127.0.0.1:28110/qsdk` menjadi sampah HANYA dalam satu keadaan: dipakai saat
   mesinnya nihil — kasus `e6e8ad4d`. Bukan path-nya yang salah; **urutannya yang salah**.
3. Config panenan = snapshot kondisi server hari ini (channel BS, country ID, versi ini).
   Untuk standalone offline itu justru target yang benar: config tidak akan berubah lagi
   karena tidak ada server luar yang menyalakannya.

### 12d. Sumber kontrak endpoint akun — diluruskan (pertanyaan owner: *"registerUser, registerVisitor dapat dari config asli atau cuma HAR? isinya disamakan ke HAR?"*)

**Jawaban: dari KODE SDK, bukan dari HAR.** Kode SDK = `classes.dex` (file fisik asli di APK,
dibedah baksmali). Di dalamnya sudah ada SEMUA yang dibutuhkan untuk mengenali kedua
endpoint: path-nya, cara membangun request (`sv=v2` + `data` base64 JSON + `sign` MD5),
dan skema respons (kelas parser `model.*`) — itu kontrak **otoritatif**.

Peran panenan 3 endpoint (registerVisitor 2345 B, registerUser 2397 B, getUserInfo 280 B)
= **VERIFIKASI saja**: membuktikan (a) skema smali yang kita baca benar, (b) perilaku
server asli yang kita desain ke mesin itu nyata (`isNewUser:0` per device yang dikenal,
`isNewUser:1` untuk register baru, profil sesi dinamis).

**Isi respons mesin TIDAK akan disamakan ke HAR:**

| Field | Sumber nilainya |
|---|---|
| `uid`, `username` tamu, `token` | generator mesin — **nilai BARU**; format pola (`2 huruf+8 digit`) = fakta perilaku server asli yang TERVERIFIKASI panenan, bukan byte yang disalin |
| `authToken` (`@NN@`) | algoritma dari smali SDK (dibalik), bukan byte HAR |
| `lastDev`, `lastLoginTime`, `regIp` | dihitung dari request nyata saat itu |
| `isNewUser` | hasil lookup STORE (per-device), bukan angka dari HAR |

Justru **sebaliknya**: jika byte mesin = byte HAR, itu tanda mesin PALSU (replay —
kasus `e6e8ad4d`). Yang wajib cocok hanya dua: **STRUKTUR** (skema dari smali) dan
**PERILAKU** (tamu sama antar-restart, register baru sukses, profil sesi konsisten) —
dan verifikasinya memakai **HAR BARU dari build lokal**, bukan HAR lama (§8d).

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
