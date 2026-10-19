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
