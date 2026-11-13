# db-apk — Dokumentasi Rencana: Standalone Local Server

> **Status dokumen:** Fondasi & Implementasi (**v4.7 — build 0.4.7**) — **LOG BAHASA MANUSIA: BOOT 3 BARIS + PEMETAAN ARAH KONEKSI** (laporan owner *"login server muncul .. saya rasa isi config loginserver anda masih mengarah ke server aslinya.. log ini pun tidak membantu kurang efektif kurang jelas"*, §2.15): **BOOT 3 BARIS** — `✔ Server lokal siap` → `ℹ Mode LOCAL — <peta arah koneksi>` → `ℹ Pemain: …` (dulu 5-6 baris siap bertumpuk tanpa cerita); dugaan "config loginserver masih ke server asli" DIJAWAB DENGAN BUKTI (login **LOKAL** — sesi 8610; daftar server = snapshot asli **sengaja**; gameplay = server game asli — progres di sana, §2.15); peta arah koneksi tampil permanen di dasbor (modemap + kartu lipat); dedupe `loginchecknative OK` diperlebar 10 dtk → 10 menit. Di bawahnya (v4.6): **MODEL FINAL LOG: ALUR LINEAR + GRUP BATCH** (kesepakatan owner *"group itu kalau terdeteksi emang config/resource yg banyak. kalau alur ya gk usah di group"*, §2.14): **ALUR** (boot → SDK → login → masuk game) = baris linear yang tidak pernah dikelompokkan; **AKTIVITAS BATCH** (mirror CDN per-file, request game, 404 fallback, passthrough ONLINE) = **GRUP lipat** di dasbor — satu baris judul dengan **statistik hidup** (`▸ Mirror CDN 470 file · 84,2 MB · 0 gagal (3 dtk)`), diklik untuk rincian per-file, **kegagalan naik sendiri sebagai ⚠ linear**, grup kosong tak tampil; `logs/human.log` = cerita tempel — grup ditulis **SEKALI sebagai ringkasan Σ satu baris** (`Mirror CDN: 470 file · 84,2 MB · 0 gagal — rincian di dasbor`); dasbor baru: **panel status atas (game/login/sesi) + alur + grup di bawahnya**, bukti teknis dilipat di bawah.
> **Temuan 2026-10-31 (probe ulang, mengoreksi catatan lama):** **SERVER ASLI MASIH HIDUP** — `setting_BS_Android.bin` (711 B, terenkripsi XOR kunci **`DragonBall`**) berhasil didekripsi → membongkar host sesungguhnya: **dragonh5cdn.popoh5.com** (H5 game + folder upgrade). Seluruh file fisik server asli berhasil diunduh & menjadi isi mirror (daftar lengkap §2.9). Catatan lama "server asli sudah mati" TERBANTAHKAN — server hanya memindahkan file ke host CDN.
> **Lingkup:** Fondasi infrastruktur client-server. **Analisis gameplay sengaja dikecualikan.**
> **Tanggal:** 2026-11-02
> **Riwayat strategi:**
> - v1.1 — original APK tidak disentuh; server lokal jadi APK pendamping terpisah.
> - v1.2 — port 4000 terkunci (API game + Web Dashboard); server wajib jalan duluan.
> - v1.3 — akses dashboard cukup `127.0.0.1:4000`; desain copy-first.
> - v1.4 — root folder `Android/data/<paket>/server/`; logging = alat bantu, bukan produk.
> - **v2.0 — TIDAK ada APK baru.** Local server **di-embed langsung ke APK LSF**
>   (hasil custom dari `DB-LOCAL.apk`). Original tetap **utama & running online**.
>   Server lokal awalnya **"pajangan tapi work"** — hidup & terbukti, belum mengambil alih.
> - **v2.1 — Nama paket TETAP `com.db.local`** (tanpa rename; custom APK menggantikan
>   original di device). **Log dashboard wajib bahasa manusia** — mudah dibaca siapa pun,
>   bukan dump teknis mentah.
> - **v2.2 — Penegasan: perubahan murni PENAMBAHAN (monitoring, bukan penggantian).**
>   Local server berjalan **bersebelahan dengan server asli (remote)** yang tetap
>   melayani game seperti biasa. Tiga mode progresif: **M1 pajangan → M2 monitor
>   (passthrough) → M3 ambil alih bertahap**.
> - **v2.3 — EKSEKUSI DIMULAI (Fase L0).** Build custom pertama **`APK-LSF-0.1.0.apk`**
>   sukses dibuat & diverifikasi (round-trip murni, tanpa server). Keystore tetap dibuat
>   & di-commit agar signature konsisten lintas sesi/build. Uji install di device menunggu owner.
> - **v2.4 — Build 0.1.0 DIBANGUN ULANG dari LSF original, kini DENGAN FITUR NYATA.**
>   Round-trip kosong dinilai tidak bermanfaat (keputusan owner). Hasil baru = **L0+L2+L3
>   digabung di level build**: local server (NanoHTTPD, port 4000) **embedded & auto-start**
>   via `BootProvider` (jalan sebelum `Application.onCreate`), dashboard web lengkap
>   (log bahasa manusia, monitoring dua sisi, APK Reader, analisis config, copy-first),
>   **kode game & traffic TIDAK disentuh** (dex original byte-identical; mode M1).
> - **v2.5 — UJI DEVICE L4 SUKSES + analisis titik masuk (tanpa implementasi).**
>   Dari device owner: folder siap, M1 aktif, dasbor hidup, monitoring server asli jalan.
>   Kontrak bootstrap terpetakan penuh di §2.8; ketiga host asli TCP-connect sukses
>   dari device (server asli belum tentu mati).
> - **v2.6 — Build 0.2.0 (cermin otomatis + switch) DITARIK.** Owner menilai implementasi
>   melampaui arahan (yang diminta saat itu = rencana), dan **sistem salin-otomatis bukan
>   yang diminta**. Build & Release v0.2.0 dicabut dari deliverable; pelajarannya jadi
>   dasar arsitektur v2.7.
> - **v2.7 — ARSITEKTUR TERKOREKSI (arahan owner) + build 0.1.0 DIBANGUN ULANG.**
>   Prinsip owner: **"bukan sistem yang menyalin — kita salin MANUAL lalu edit endpoint
>   agar menuju local"**. Poin: (1) **port dipisah** — 4000 = dasbor debug developer,
>   **8080 = port game** (endpoint client menunjuk ke sini); (2) **isi config asli ditaruh
>   MANUAL** di `Android/data/com.db.local/server/www/` dengan struktur folder = path URL
>   server asli, dilayani **seakan server asli** — TIDAK disembunyikan di assets APK;
>   (3) **switch mode LOCAL/ONLINE** lewat **tombol melayang** di APK — ditekan → mode
>   tersimpan di `server.json` → **aplikasi restart otomatis** → game baca config sesuai
>   mode baru; (4) client hanya berubah di **nilai endpoint** (3 Base64 → `127.0.0.1:8080`),
>   dex game byte-identical; (5) **log tenang** — polling dasbor tidak dicatat lagi,
>   monitor hanya mencatat saat kondisi berubah + denyut ringkasan 10 menit.
> - **v2.8 — KOREKSI OWNER: TIDAK ADA langkah manual — "semua itu terekstrak ke sana".**
>   Payload config asli dibawa **di dalam APK** (`assets/server/payload/`) dan otomatis
>   diekstrak ke `Android/data/com.db.local/server/www/` oleh BootProvider saat aplikasi
>   **pertama kali jalan** (Android tidak memberi hook saat install; bukaan pertama = detik
>   pertama, sebelum splash selesai — hasilnya sama: user tidak melakukan apa-apa).
>   www/ dihapus / clear data → terisi ulang otomatis. Game TETAP membaca via HTTP dari
>   port game lokal (assets hanya gudang payload, BUKAN jalur baca game) — aturan
>   "baca seakan dari server" tetap terjaga. Payload = 4 file cermin byte-identik
>   versi bawaan APK (`base.version` `110`, `resource.version` `11220`, `upgrade.json`/`size.json` `{}`).
> - **v3.0 — RENCANA FINAL DISepAKTI OWNER & DIEKSEKUSI (build 0.3.0).** Koreksi owner:
>   (1) **"namanya floating kok gk bisa di geser"** → tombol melayang kini **bisa digeser**
>   (drag bebas, dibatasi layar, posisi tersimpan di `server.json`, tap = panel mode dengan
>   konfirmasi dua-tahap, hanya tampil saat game di depan); (2) **"config fisik server ada
>   banyak contoh all.zip!! clientversion.json!! dan banyak lainnya"** → mirror www/ kini
>   **PENUH** (31 file nyata dari server asli yang masih hidup — daftar §2.9), bukan 4 file
>   minimal; `clientversion.json` **ditemukan** di `resource/properties/` (dipanggil JS game);
>   (3) **"android/data server isinya banyak sampah"** → folder server hanya boleh berisi
>   `config/ www/ logs/ tmp/` — **pembersih whitelist** menghapus apa pun di luarnya tiap
>   boot (menyapu sampah peninggalan build lama otomatis); penanda versi pindah ke
>   `config/www.stamp` (www/ murni cermin server); endpoint `/api/apk/*`, `/api/analyze`,
>   `/api/monitor/run` dihapus; (4) penemuan analisis: setting bin terenkripsi XOR
>   `DragonBall` → didekripsi → **host H5 asli = dragonh5cdn.popoh5.com** → seluruh file
>   fisik server diunduh & dibawa APK; setting bin yang disajikan di mode LOCAL di-rewrite
>   3 URL-nya ke `127.0.0.1:8080` (satu-satunya konten yang berubah — bukan edit client).

---

## 1. Ringkasan Proyek

Game ini adalah aplikasi Android berbasis **Egret Engine (H5/TypeScript)** yang dibungkus
runtime native (shell APK). Saat ini seluruh konfigurasi & layanan bergantung pada server
remote pihak ketiga yang **sudah/segera mati**.

**Tujuan akhir:** menjadikan APK **standalone & berjalan lokal (offline)**.
Prinsip yang disepakati (terakhir diperbarui v2.2):

- **Tidak membuat project/APK baru.** APK yang kita edit = **APK LSF**, hasil custom
  langsung dari `DB-LOCAL.apk` (decompile → tambah local server → rebuild).
- **Hanya DITAMBAH local server** di dalam APK LSF — game client di dalamnya tidak diubah.
- **Nama paket TETAP `com.db.local`** (v2.1) — tanpa rename; custom APK = pengganti
  langsung original di device.
- **Bersebelahan = di dalam satu aplikasi**: game dan local server berjalan berdampingan
  (activity game + service server), bukan dua aplikasi terpisah.
- **Yang utama = yang running online.** Game tetap berperilaku online persis seperti
  aslinya; local server mula-mula **pajangan yang work**: jalan di port 4000, dashboard
  hidup, terbukti setara — tapi belum dihubungkan ke game (endpoint belum diganti).
- **Local server = monitor/observator (v2.2).** Ia berjalan bersebelahan dengan
  **server asli (remote)** — server asli tetap melayani game seperti biasa; local
  server hanya mengamati & mencatat, tidak mengubah apa pun sampai paritas terbukti.
- Semua pekerjaan didokumentasikan di file ini (dokumentasi tunggal).

---

## 2. Hasil Analisis Fondasi APK

### 2.1 Identitas APK

| Atribut | Nilai |
|---|---|
| File | `DB-LOCAL.apk` (101 MB, tersimpan via Git LFS) |
| SHA-256 | `62418cc46a2cbc6458aceb4738fa5dd6657ac754473c9f625f99b7be794fe23d` |
| Package name | `com.db.local` |
| Entry point native | `com.zhuhuan.game.LaunchActivity` → `com.zhuhuan.game.MainActivity` |
| Application class | `com.zhuhuan.game.Application` (SDK "zhuhuan"/quickgame) |
| Game engine | Egret (native runtime: `libegret.so` + `assets/runtime-dex.jar`) |
| Target arsitektur | `arm64-v8a`, `armeabi-v7a` |

### 2.2 Arsitektur Teknis (3 Lapis)

```
┌─────────────────────────────────────────────────┐
│  LAPIS 1 — Android Shell                        │
│  com.db.local / com.zhuhuan.game.*              │
│  Launcher, SDK pihak ketiga, billing, iklan     │
├─────────────────────────────────────────────────┤
│  LAPIS 2 — Egret Native Runtime                 │
│  libegret.so + runtime-dex.jar + libgavesec.so  │
│  Menjalankan JS game + dekripsi (AES native)    │
├─────────────────────────────────────────────────┤
│  LAPIS 3 — Konten Game (assets/game/)           │
│  Bundle JS TERENKRIPSI + resource ter-obfuscate │
│  (marker enkripsi: "onepieceencrypts")          │
└─────────────────────────────────────────────────┘
```

**Poin penting untuk fondasi:**

- Kode JS game (termasuk daftar endpoint server game yang sesungguhnya) berada di dalam
  bundle terenkripsi: `assets/game/pschent_full_tired.bin` (9.4 MB) &
  `voyage_fancy_radius.bin` (1.2 MB) — dipetakan oleh `infoinfo.bin`.
- Nama file resource **diofuscate** (acak); manifest resource ada di dalam bundle terenkripsi.
- Dekripsi dilakukan oleh native lib `libgavesec.so`
  (terdapat simbol: `Java_com_utils_EncryptUtil_nativeAES`, `encrypt(char*, char*)`).

### 2.3 Peta Endpoint & Alur Bootstrap (fondasi jaringan)

Alur koneksi client saat start (dari file konfigurasi di `assets/`):

```
APK Start
   │
   ├─ assets/config.properties ──(Base64)──► http://dragon.sjmobilegame.com:82
   │                                          /lzceshi/native_setting/Android
   │                                          → bootstrap "native_setting"
   │
   ├─ assets/config_BS.properties ─(Base64)─► https://configus.sjmobilegame.com/bs/db/android
   │                                            (fallback: Tencent COS
   │                                             configus-1257066086.cos.na-siliconvalley.myqcloud.com)
   │                                          → konfigurasi server utama (list endpoint game)
   │
   └─ assets/QHinfo ────────────────────────► http://qsdk.t4game.com
                                              (server SDK quickgame/zhuhuan)
```

Hasil dekode `EntryPoint` (Base64) sudah tercantum di atas — tidak ada endpoint lain
yang terlihat jelas di lapisan shell; **endpoint game sesungguhnya kemungkinan besar
dikirim via respons `config_BS`** (dinamis), lalu dipakai oleh JS di dalam bundle terenkripsi.

### 2.4 Status Kesehatan Endpoint (hasil probe ulang, 2026-10-31 — MENGOREKSI catatan lama)

| Endpoint | Status | Catatan |
|---|---|---|
| `configus.sjmobilegame.com/bs/db/android/setting_BS_Android.bin` | ✅ **HIDUP — 200 OK** | 711 B, terenkripsi XOR kunci `DragonBall` (berhasil didekripsi → JSON peta server) |
| `dragonh5cdn.popoh5.com/bs/…` | ✅ **HIDUP** | **Host baru yang ditemukan** dari isi setting bin — H5 game (`index-native.html`, `manifest.json`, 15 JS) + folder `upgrade/` (zip + versi) |
| `dragon.sjmobilegame.com:82` | ✅ HIDUP (flaky dari luar negeri) | `lzceshi/native_setting/Android/setting_BS_Android.bin` → 200 OK (350 B, kunci XOR sama) |
| `qsdk.t4game.com` | ✅ HIDUP | Respon `Hello,<ip>` — server SDK (fase berikutnya) |
| `configus …/base.version`, `resource.version`, dll | 404 | Memang bukan di sini — file versi/zip ada di `dragonh5cdn/bs/upgrade/` (temuan setting bin). Fallback dex terbukti benar |

**Kesimpulan baru:** rantai bootstrap **UTUH**. Kesimpulan lama "server mati" keliru — server
hanya memindahkan isi ke host CDN (`dragonh5cdn.popoh5.com`). Karena masih hidup, seluruh
isi fisik berhasil di-SNAPSHOT dan dibawa dalam APK sebagai mirror (§2.9) — pondasi tidak
lagi menebak format dari dex, tapi memakai file nyata.

**Catatan arsip (v1.1):** pencarian arsip publik (Wayback Machine + CDX API) untuk
`sjmobilegame.com` dan `t4game.com` **tidak menemukan snapshot sama sekali**. Artinya
format respons asli bootstrap **tidak dapat dipulihkan dari luar** — harus direkonstruksi
dari sisi client (analisis dex/smali + observasi perilaku runtime). Lihat Fase L1.

### 2.5 Dependensi Cloud yang Akan Mati (perlu di-stub nanti)

| Dependensi | Fungsi | Dampak jika mati |
|---|---|---|
| Google Play Billing | IAP | Crash/fitur beli gagal |
| Google Sign-In (GMS) | Login | Login gagal |
| Facebook SDK | Login/share | Login gagal |
| Adjust (`app.adjust.com`) | Atribusi/analitik | Biasanya hanya log error |
| quickgame/zhuhuan SDK (`qsdk.t4game.com`) | Channel/login SDK | Boot flow bisa terganggu |

### 2.6 Izin (Permissions) yang Sudah Ada

`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`,
`READ/WRITE_EXTERNAL_STORAGE`, `WAKE_LOCK`, `SYSTEM_ALERT_WINDOW`, `REORDER_TASKS`.
→ Cukup untuk menjalankan **server HTTP lokal** di dalam aplikasi (tidak perlu izin baru).

### 2.7 Konfigurasi Keamanan Jaringan (TLS/HTTP) — temuan v1.2

`res/xml/network_security_config.xml` berisi: `base-config` + `cleartextTrafficPermitted`
(**tanpa** `<certificates src="user"/>`).

Implikasi untuk strategi v2.0 (kita pegang client-nya langsung — tidak perlu intersep):

| Skenario | Implikasi |
|---|---|
| HTTP (`dragon:82`, `cleartextTrafficPermitted=true`) | ✅ Repoint ke `http://127.0.0.1:4000` aman — tidak ada halangan TLS |
| HTTPS (`configus`) | ✅ Justru **mudah**: saat repointing, cukup ubah skema ke `http://127.0.0.1:4000` (server lokal tidak butuh sertifikat) |
| MITM paksa (pendekatan lama) | ❌ Tidak diperlukan lagi — dihapus dari strategi |

**Kesimpulan v2.0:** hambatan TLS praktis hilang karena kita mengedit client sendiri.
Prioritas tetap jalur HTTP; Fase L1 memverifikasi endpoint mana yang benar-benar
dipanggil client + protokolnya.

### 2.9 Peta Server Asli yang Nyata (temuan 2026-10-31 — hasil dekripsi setting bin + probe)

Rantai bootstrap penuh, kini tersambung sampai ujung:

```
config_BS.properties EntryPoint (Base64) → configus/bs/db/android
  └─ setting_BS_Android.bin (XOR "DragonBall") → JSON peta:
       url        → dragonh5cdn.popoh5.com/bs/index-native.html   (H5 Egret, langkah 5)
       tsUrl      → dragon.sjmobilegame.com:82/bs103/…            (varian uji)
       update     → dragonh5cdn.popoh5.com/bs/upgrade             (zip + versi, langkah 4)
       loginServer→ login.popoh5.com (+loginPort 610)             (fase login — berikutnya)
  └─ JS game (main.min.js) meminta:
       resource/properties/clientversion.json  ("clientVersion":"20260929_180158-EN")
       resource/properties/serversetting.json  ("loginserver":"login.popoh5.com:610")
```

**Daftar file fisik server asli — SEMUA diunduh & menjadi isi mirror www/ (31 file, ±56 MB):**

| Lokasi (path URL) | Ukuran | Isi |
|---|---|---|
| `bs/db/android/setting_BS_Android.bin` | 711 B | peta server (XOR `DragonBall`) |
| `bs/index-native.html` | 10.792 B | halaman Egret (entry `Main`) |
| `bs/manifest.json` | 533 B | daftar 15 JS |
| `bs/js/*.js` (15 file) | ±6,5 MB | engine + game (`main.min` 4,9 MB dst.) |
| `bs/resource/properties/clientversion.json` | 38 B | `20260929_180158-EN` |
| `bs/resource/properties/serversetting.json` | 73 B | loginserver (fase berikutnya) |
| `bs/upgrade/base.version` | 4 B | `110` |
| `bs/upgrade/resource.version` | 5 B | `11390` |
| `bs/upgrade/upgrade.json` | 130 B | `11386–11389_11390.zip` + `all.zip` |
| `bs/upgrade/size.json` | 105 B | `base` 31.015.076 · `all` 19.385.098 |
| `bs/upgrade/base.zip` | 31.015.076 B | SHA256 `31dedf27…` — 2.367 file terbongkar |
| `bs/upgrade/all.zip` | 19.385.098 B | SHA256 `6b838206…` — 591 file terbongkar |
| `bs/upgrade/1138[6-9]_11390.zip` | 4 × 252.562 B | zip incremental |
| `lzceshi/native_setting/Android/setting_BS_Android.bin` | 350 B | varian uji (kunci XOR sama) |

### 2.10 Protokol LOGIN — tersambung sampai ujung (temuan 2026-11-02: bedah main.min + dex + probe langsung server asli)

Rantai login lengkap, kini terpetakan 100% dari client sampai wire-format:

```
setting bin (XOR "DragonBall") → loginServer https://login.popoh5.com + loginPort 610 + loginWebPort 510
  └─ native (LaunchActivity) membacanya (jsonObject.getString("loginServer") — ada di dex)
  └─ H5 (main.min) connectToLogin():
       TSBrowser.executeFunction("getLoginServer") → TIDAK ADA implementasinya di dex
       (grep penuh jadx: 0 hasil) → SELALU fallback:
       RES.getResByUrl("resource/properties/serversetting.json") → {"loginserver":"https://login.popoh5.com:610","requireSDK":false}
  └─ loginClient.connectToServer(loginserver) → socket.io
```

**Wire-format (hasil probe langsung ke login.popoh5.com:610 yang MASIH HIDUP, 2026-11-02):**

| Aspek | Temuan |
|---|---|
| Library client | **socket.io v2.1.0** (header `socket.io.min_57ea9db7.js` di bundle) → **engine.io v3** |
| Transport | polling + websocket; handshake polling = framing `"<panjang>:<paket>"` berurutan, contoh nyata: `96:0{"sid":"…","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}2:40` (OPEN engine.io + OPEN socket.io `40`) |
| Event | **`handler.process`** dengan args `(pesan, callback)` — callback TIDAK dikirim di args (socket.io v2: cukup ack-id di packet `42<id>["handler.process",{…}]`) |
| Jawaban | ACK socket.io `43<id>[{ret, data}]` — `ret=0` sukses; `data` = **STRING berisi JSON** (di-parse H5 dengan `JSON.parse`, bisa LZString-compress bila `compress` flag) |
| Aksi (5, SEMUanya — hasil grep penuh main.min) | `loginGame` (masuk; daftar otomatis), `GetServerList`, `LoginAnnounce`, `SaveHistory`, `SaveLanguage`, `SaveUserEnterInfo` — type `"User"` semua |
| Skema `loginGame` | `{loginToken, userId, nickName, headerImage, isCreated, errorCode, errorMessage, channelCode}` — channel asing ditolak `errorCode 103` "本服务器只允许特定的渠道来源" (hanya channel tertentu) |
| Skema `GetServerList` | `{errorCode:0, serverList:[{serverId,url,new,hot,name,online,openDate,offlineReason,merge,mergeTo,urlInner,chatInner}], history}` — **2109 server asli tertangkap utuh** (Server 1 → Server 2109, gateway `https://s<N>-bs.popoh5.com:<port>`) — snapshot jadi `assets/server/login/servers.json` |
| POST polling | WAJIB berframing `"<panjang>:<paket>"` — probe berframing kini `200 "ok"` (kesimpulan lama "nginx 502" keliru — probe mentah tanpa framing yang ditolak) |

**Jalur tanpa-SDK di H5 (kunci PR3):** `main.min checkLastLogin()` — bila URL H5 memuat `?userid=X`, H5 **TIDAK memanggil SDK sama sekali** dan langsung `doOriginLoginRequest({username:X, password:""})` → password diisi `"game_origin"` → `clientLoginUser` → aksi `loginGame` → `GetServerList` → pilih server → masuk. Artinya: **guest/login lokal cukup dengan (1) url H5 + `?userid=<guest>` (rewrite endpoint, bukan edit client) + (2) server login lokal yang memakai wire-format yang sama.** Layar SDK (guest button QuickGame) tetap ada di mode ONLINE apa adanya.

**Implikasi:** tahap SDK bisa dilokalkan penuh tanpa menyentuh client — `LoginServer.java` (0.4.0) memakai wire-format di atas.

### 2.11 Protokol TAHAP SDK quickgame/zhuhuan — layar login SDK ASLI dilayani lokal (temuan 0.4.2: bedah dex com.quickgame.android.sdk + PROBE LANGSUNG qsdk.t4game.com & login.popoh5.com:510)

**Alur asli APK (testMode 1 di setting bin — sudah terbukti dari dex `MainActivity`):**

```
LaunchActivity (channel "BS", LaunchActivity.onCreate:388)
  └─ QuickGameManager.init(activity, product_code="75880787318053043365515388491588", callback)
       └─ POST http://qsdk.t4game.com/v1/system/init   (host dari assets/QHinfo)
  └─ onInitFinished(true) → QuickGameManager.login(activity)
       └─ HWLoginActivity (UI SDK ASLI: tamu 1-klik / akun+password / daftar / lupa sandi)
            ├─ tamu  → POST /v1/user/registerVisitor
            ├─ masuk → POST /v1/user/loginByName {username, password=MD5}
            ├─ daftar→ POST /v1/user/registerUser {vcode?, username, password=MD5}
            └─ auto  → POST /v1/user/autoLogin {authToken}   (sesi tersimpan model.b)
  └─ onLoginFinished(QGUserData{uid, username, token, isGuest})
       └─ shell: GET <loginServer>:<loginWebPort>/loginchecknative?uid=…&username=…&token=…
            &usermode=…&osVersion=…&packageName=…&appVersion=…&sourceVersion=…
            &brand=…&model=…&os=Android&sdk=BS
       └─ {"code":0} → MainActivity.customreading() → boot Egret (startGame JSON)
  └─ H5 checkLastLogin() → url ?userid=<uid> → doOriginLoginRequest(uid,"game_origin")
       → loginGame → GetServerList → pilih server → masuk
```

**Wire-format SDK (body form-urlencoded — BUKAN JSON):**

| Aspek | Temuan (probe server ASLI, 2026-11-02) |
|---|---|
| Body | `sv=v2&data=<base64(JSON param)>&sign=<md5>` — header `application/x-www-form-urlencoded` |
| `sign` | md5 dari `k=v&k=v&…` **urut abjad** + rahasia **`0b2a18e45d7df321`** (dex `unnecessarysperrylites.seventygenom.nationalcommunitymissing`) |
| Param dasar | `sdkVersion=223, gameVersion, deviceId, serialNum, devIDShort, platform=1, productCode, channelCode, authToken, clientLang, suggestCurrency, time` + param per-endpoint |
| Envelope jawaban | `{"result":bool,"data":…,"error":{"id":N,"message":"…"}}` — pesan galat mengikuti `clientLang` (`in` → "Otorisasi gagal", "akun atau password tidak ada") |
| `/v1/system/init` | `data:{payTypes:[], version:{…all "empty"}, productConfig:{useServiceCenter,isShowFloat,showShare,guestShowBind,…}, loginTypes:["1","13"], nodes:{…}, requestIp, countryCode, first_install}` |
| `/v1/user/registerVisitor` | `data:{authToken:"@…", userData:{uid, username, token, isGuest:1, isNewUser, mobile:"", bindInfo:{bindFB,bindGameCenter,bindGoogle,bindEmail,bindNaver,bindTwitter,bindLine,bindVK,signApple,bindPlay,bd94hi,bdhw}}, checkRealName:0}` — **deviceId sama = tamu sama (isNewUser→0)** |
| `/v1/user/loginByName` | data sama dgn isGuest:0; salah → `{"result":false,"error":{"id":40101,"message":"akun atau password tidak ada"}}` |
| `/v1/user/autoLogin` | token valid → sesi diperbarui; token asal → `{"id":40005,"message":"Otorisasi gagal"}` |
| `/v1/user/ckRegVistor` | `data:{"isReged":1}` |
| `loginchecknative` (GET login.popoh5.com:**510**) | token valid → `{"code":0,"sdk":"BSNative","nickName":…,"loginToken":"<32hex>","userId":…,"sign":"<md5>","sdkType":"BS","osType":"Android"}`; token asal → `{"code":500,"msg":"登录参数验证失败"}`; `sdk=BS` WAJIB (tanpa itu `{"code":500,"msg":"no sdk"}`) |

**Implementasi lokal (0.4.2):**

1. `assets/QHinfo` ditulis-ulang `http://qsdk.t4game.com` → `http://127.0.0.1:8610` (endpoint saja — dex/UI SDK tidak disentuh). Layar login SDK ASLI tampil duluan dan bertanya ke server SDK lokal.
2. `SdkApi.java` + `SdkData.java` (port 8610, sebelahan socket.io): semua endpoint di atas dengan **skema respons salinan respons asli** (hasil probe). Tamu dipetakan per `deviceId` (`config/sdk-guests.txt`); **tamu pertama = guest lama pemilik** (`config/local-user.txt` — progres tidak lekat). Akun = `config/accounts.txt` (dipakai bersama loginGame).
3. FIX 0.4.1→0.4.2: bin `loginServer` kini **TANPA port** (`http://127.0.0.1` + `loginPort`/`loginWebPort` 8610) — dulu `http://127.0.0.1:8610:8610` (dobel port) → `new URL` gagal → rantai SDK tidak pernah selesai.
4. `userid` url H5 = **uid sesi SDK terakhir** (`config/sdk-last.txt`), bukan guest karangan. Karena bin disajikan SEBELUM layar SDK boot berjalan, `loginGame` mengikuti sesi SDK terakhir bila url membawa userid lain — identitas layar SDK = identitas game, tanpa mengubah client.
5. Mode ONLINE: `/v1/*` diteruskan utuh ke `qsdk.t4game.com`, `loginchecknative` ke `login.popoh5.com:510` — perilaku online = perilaku asli.

**Verifikasi sandbox (harness 0.4.2 — 39/39 LULUS):** envelope+sign, init (loginTypes `["1","13"]`, versi "empty"), registerVisitor (migrasi guest, isGuest/isNewUser/bindInfo), tamu ulang (isNewUser 0), autoLogin (token benar/asal→40005), registerUser (+dobel ditolak), loginByName (benar/salah→40101), ckRegVistor, loginchecknative (code 0 skema BSNative + code 500), loginGame mengikuti sesi SDK, handshake+GetServerList (regresi 0.4.0), rewrite bin (loginServer tanpa port, userid sesi SDK, tanpa host asli).

### 2.12 DATABASE SQLITE `dragonball.db` — data profesional, tahan reboot (0.4.3; nama FINAL dari owner; bukti alur natural = bedah dex lanjutan 2026-11-03)

**Permintaan owner:** *"kalau bisa ini bukan karangan. dan output txt? ini database yg profesional lahh masak iyaaa database txt"* → lalu *"database dragonball.db sqlite . final ."* dan *"belum buat karena rencana sdk masih belum jelas agar work, guest login register sampai ke login secara natural"*.

**Bukti alur natural (bedah dex lanjutan — bukan karangan):** ketiga tombol layar SDK diikuti sampai selesai di `HWLoginActivity`/`QuickGameSdkService` (jadx 1.5.0 atas dex asli):

| Tombol | Endpoint | Event internal | Bukti string di dex | Akhir |
|---|---|---|---|---|
| Tamu | `/v1/user/registerVisitor` | `LOGIN_RAND` | `seventygenom(14)` → type 14 = string **"hw_guest"** | langsung selesai |
| Masuk | `/v1/user/loginByName` | `LOGIN` | log **"login auth success"** → type 12 | dialog sukses → selesai |
| Daftar | `/v1/user/registerUser` | `ACCOUNT_REGIST` | log **"register success"** → type 13 | dialog sukses → selesai = **AUTO-LOGIN** |

`hardlinerspare()` = simpan akun-terakhir ke `model.b` → `singersmooth()` → tutup layar SDK → serahkan `QGUserData{uid, username, token, isGuest}` ke shell. Ketiga tombol bermuara di SATU pintu — cukup server lokal menjawab `result:true + data sesi` skema asli. Lanjutannya (juga dari dex): shell `GET /loginchecknative` (kelas `nationalcommunitymissing`: format URL, retry 3×, baca `code`) → `code:0` = `MainActivity.customreading()` → boot Egret; H5 `checkLastLogin()` (main.min asli) baca `?userid=` → `doOriginLoginRequest` (password kosong otomatis `"game_origin"`) → socket.io `loginGame` → `GetServerList` → pilih server → masuk. **Rantai guest/login/register → login game UTUH tanpa satu langkah pun karangan.**

**Database (menggantikan txt — alasan: txt = tulis-ulang file penuh (korup bila mati di tengah), tanpa lock, parsing tab rapuh, tanpa indeks, tidak bisa di-SELECT, dan — bug tersembunyi 0.4.2 — `config/*.txt` TIDAK masuk whitelist Cleaner sehingga TERHAPUS tiap boot = guest/akun tidak pernah tahan reboot!):**

Satu file **`config/dragonball.db`** — engine SQLite **bawaan Android** (`android.database.sqlite`, tanpa Context: `openOrCreateDatabase(path)`) — nol tambahan APK, engine yang sama dipakai aplikasi sistem:

```sql
CREATE TABLE accounts     (username TEXT PRIMARY KEY, pass_md5 TEXT, created_at TEXT,
                           last_login TEXT, login_count INTEGER);
CREATE TABLE sdk_guests   (device_id TEXT PRIMARY KEY, uid TEXT, created_at TEXT);
CREATE TABLE sdk_sessions (auth_token TEXT PRIMARY KEY, uid TEXT, is_guest INTEGER,
                           created_at TEXT, last_seen TEXT);
CREATE TABLE kv           (key TEXT PRIMARY KEY, value TEXT);   -- local_user, last_uid
```

- **Tamu** → `sdk_guests` (+baris `accounts`); tamu pertama = migrasi guest lama (`kv local_user`) — progres pemilik tidak hilang; perangkat sama = tamu sama (`isNewUser 0` seperti server asli)
- **Masuk/daftar** → `accounts` (password MD5 dari client, tak diubah); **loginGame** → `accounts` yang sama — identitas SDK = identitas game
- **Sesi** → `sdk_sessions` (token stabil md5(uid|device) — tahan mati/hidup proses, `model.b` client selalu cocok)
- **Keunggulan**: transaksi ACID + journal crash-safe + lock + indeks PK + bisa diperiksa `sqlite3 dragonball.db "SELECT * FROM accounts"`
- **Engine (rantai)**: `DbAndroid` (device) → `DbJdbc` (HANYA harness desktop — SQL sama persis, class TIDAK masuk APK) → `DbMem` (jaring memori — boot tetap jalan apa pun yang terjadi)
- **MIGRASI OTOMATIS** (boot pertama, SEBELUM Cleaner): `local-user.txt` → `kv local_user` · `accounts.txt` → `accounts` · `sdk-guests.txt` → `sdk_guests` · `sdk-session.txt` → `sdk_sessions` · `sdk-last.txt` → `kv last_uid` → file lama di-rename `*.imported` (nol data hilang; diimpor sekali — tahan dobel)
- **Cleaner**: whitelist `config/` kini melindungi `dragonball.db` (+`-journal/-wal/-shm/-corrupt`)
- **Korupsi?** `DbAndroid` memulihkan hanya untuk korupsi SQLite SUNGUHAN (`SQLiteDatabaseCorruptException`/malformed) — galat lain (lock sesaat, engine tak tersedia) TIDAK menyentuh file (bug awal harness: rename otomatis membuat data "hilang" — sudah difix & diuji)
- File yang TETAP file (sengaja): `server.json` (config aplikasi) · `servers.json` (snapshot daftar server — boleh disunting pemilik) · `status.json` · log

**Verifikasi sandbox (harness 0.4.3 — 50/50 LULUS, `local-server/harness/HarnessSdk.java` di-commit):** engine = SQLite nyata via JDBC; migrasi kelima file warisan (guest pemilik, akun + login_count 7, sesi, last_uid); file lama di-rename `.imported`; **REBOOT** (buka ulang DB) = guest/akun/sesi utuh tanpa impor dobel; autoLogin token warisan; regresi SDK 0.4.2 PENUH (init, registerVisitor migrasi/ulang, registerUser +dobel ditolak, loginByName benar/salah→40101, autoLogin 40005, ckRegVistor, loginchecknative code 0/500, loginGame ikut sesi SDK via socket.io BERFRAMING, handshake+GetServerList); rewrite bin (userid = pilihan layar SDK dari DB).

---

### 2.13 AKUN LAYAR SDK = EMAIL — akar masalah "login register gak work" + perbaikan (0.4.4; bedah dex 2026-11-03)

**Laporan owner:** *"login register gak work .. ini jelas PR"* — tamu (TAMU) JALAN sampai masuk game, tapi MASUK dan DAFTAR mati.

**Bedah dex (bukan karangan — kelas dibedah satu per satu, jadx 1.5.0):**

| Lapis | Kelas | Temuan |
|---|---|---|
| UI MASUK | `mesoamericanhochmagandies.cointhreat.timidcompletely` | kolom "akun" divalidasi **EMAIL**: `feedtonight.singersmooth.singersmooth` = regex `\w+([-+.]\w+)*@\w+([-.]\w+)*\.\w+([-.]\w+)*` (pesan `hw_error_email_empty/invalid`), tolak huruf besar di awal (`(\w*)[A-Z]`, log "邮箱包含大写字母"), sandi `^.{6,}$` (min 6 karakter). **Tidak valid → `return` TANPA mengirim request apa pun** |
| UI DAFTAR | `mesoamericanhochmagandies.cointhreat.navyshop` | wajib **centang `cb_agree`** dulu (toast `hw_register_agree_error`; status di SharedPreferences "isChecked") + email valid + sandi ≥6 → `registerUser(vcode?, email, md5(sandi))` |
| Validator | `feedtonight.singersmooth` | sandi `Pattern "^.{6,}$"` · email regex di atas |
| HTTP | `bindcommunist.cointhreat` (POST, retry 3×) + `bindcommunist.singersmooth.nationalcommunitymissing` | body `{sv,data,sign}`; respons dibaca `getBoolean("result")` → `getString("data")` → parser `psalmicvolleyball.cointhreat.cointhreat`: butuh `authToken` + `userData` (+`bindInfo` opsional) — **skema respons lokal sudah benar** (tamu terbukti jalan memakai parser yang sama) |
| Pengingat akun | `QuickGameSdkService.seventygenom(13)` | simpan username+token terakhir ke `model.b` — aman untuk email |

**Akar masalah (kenapa TAMU jalan tapi MASUK/DAFTAR mati):** yang tiba di server utk MASUK/DAFTAR adalah `username` = **alamat email** — sedangkan `SdkData.registerUser` lokal menolak `@` dan `+` (charset lama `a-z0-9_.-`, panjang 3-32) → **DAFTAR selalu gagal 40102**; dan karena tak pernah ada akun email tersimpan → **MASUK selalu gagal 40101**. TAMU tidak menyentuh validasi itu (`registerVisitor` pakai deviceId) — makanya satu-satunya tombol yang jalan.

**Perbaikan 0.4.4 (server lokal — client tetap TIDAK disentuh):**

1. `SdkData.registerUser` — menerima akun email: normalisasi **huruf kecil**, charset `[a-z0-9_.+-@]`, panjang 3-64 (email bisa panjang); nama lama tanpa `@` tetap diterima (kompatibel).
2. `SdkData.loginByName` — pencarian **tidak peka huruf besar/kecil** (coba persis dulu, lalu huruf kecil) — `Budi@GMail.com` mendarat di akun `budi@gmail.com`.
3. `SdkApi` — **log verbose MASUK/DAFTAR** (akun + panjang kata sandi, sukses & gagal dgn kode 40101/40102 + sebab: "sudah dipakai" vs "tidak valid") — percobaan gagal selalu terlihat di dasbor (dulu gagal diam tanpa jejak).
4. Identitas utuh: `uid == username == email` → loginchecknative → url H5 `?userid=<email>` → loginGame — akun **yang sama** di tahap game (`loginGameResponse` tidak membatasi charset — SQLite TEXT; `nickName` = email).
5. `Cleaner` whitelist + `servers.json` (bug lanjutan 0.4.3 yang terlihat di log boot: file dihapus lalu di-restor di boot yang sama — suntingsan pemilik hilang tiap reboot, janji "boleh disunting" patah).
6. Pesan log tamu dirapikan: yang BARU adalah **ikatan perangkat**, bukan tamunya ("perangkat … diikat ke tamu … — guest lokal pemilik (migrasi identitas)").

**Verifikasi sandbox (harness 0.4.4 — 63/63 LULUS):** +13 tes baru — DAFTAR email (`Budi.New@GMail.com` → tersimpan `budi.new@gmail.com`), dobel ditolak 40102, karakter liar (`!`) ditolak, MASUK email HURUF BESAR → sukses (tidak peka kasus), sandi salah → 40101, email tak terdaftar → 40101, loginchecknative uid email → code 0, kv last_uid = email, loginGame mengikuti sesi SDK (email sampai tahap game), akun email tercatat di `accounts`; +2 tes Cleaner (servers.json & dragonball.db tidak dihapus); 48 regresi lama tetap hijau.

### 2.14 MODEL FINAL LOG — alur linear + grup batch (0.4.6; kesepakatan owner 2026-11-03)

Laporan owner yang memicu: *"group itu kalau terdeteksi emang config/resource yg banyak. kalau alur ya gk usah di group"* — menegaskan model yang sudah disepakati bertahap. **Hukum log 0.4.6:**

1. **ALUR tidak pernah dikelompokkan** — boot → SDK → daftar/masuk → loginchecknative → daftar server → masuk game = baris linear urut waktu. Tidak ada kelompok di jalur ini.
2. **Hanya aktivitas BATCH besar yang masuk GRUP** (`LogGroups.java`): `mirror` = **Mirror CDN** (file diambil dari server asli → `www/`), `req` = **Request game** (setiap 200 dari `www/`, termasuk setting bin/serversetting), `404` = **404 fallback** (normal), `pass` = **Passthrough ONLINE** (mode ONLINE).
3. **Baris grup = judul + statistik hidup**: `▸ Mirror CDN 470 file · 84,2 MB · 0 gagal (3 dtk)` — angka berubah sendiri selama aktivitas berjalan; diklik → daftar rincian per-file (waktu + nama + ukuran; maks 600 item/grup di memori dasbor).
4. **Kegagalan naik sendiri** sebagai baris ⚠ linear (`Mirror CDN gagal — templeTest.json: server asli tidak terjangkau (dicoba ulang saat diminta lagi)`) — laporan pertama langsung tampil, beruntun sesudahnya hanya menaikkan hitungan "N gagal" di judul grup (jeda 30 dtk; CDN mati tidak membanjiri 100 baris kembar). 404 TIDAK dianggap gagal (perilaku normal — game lanjut boot).
5. **Grup kosong tidak tampil** sama sekali — tidak ada item → tidak ada baris.
6. **`logs/human.log` = cerita untuk ditempel**: grup ditulis SEKALI sebagai ringkasan Σ saat aktivitas diam ±45 dtk — `Mirror CDN: 470 file · 84,2 MB · 0 gagal (3 dtk) — rincian di dasbor`. Rincian per-file tidak masuk file (tinggal di dasbor). Tombol **Copy** = alur + ringkasan Σ per grup; **Copy penuh** = ekor `human.log` dari disk.
7. **Dasbor 4000 dibangun sesuai model**: panel status atas — `PORT GAME siap · SERVER LOGIN siap · SESI PEMAIN <uid>` — lalu alur linear, lalu kelompok batch di bawahnya; bukti teknis (server asli, 6 request terakhir, isi `www/`, identitas) dilipat di bagian bawah.
8. **Dedupe `loginchecknative OK`**: shell+H5 kadang memanggil dua kali — uid sama < 10 dtk = satu baris ✔ saja (dulu muncul dua baris kembar). *(0.4.7: diperlebar ke 10 menit — denyut 21 dtk masih bocor dua baris.)*

---

### 2.15 PEMETAAN ARAH KONEKSI + BOOT 3 BARIS (0.4.7; laporan owner "login server muncul .. saya rasa isi config loginserver anda masih mengarah ke server aslinya.. log ini pun tidak membantu kurang efektif kurang jelas")

Dugaan "config loginserver masih mengarah ke server asli" dijawab dengan **BUKTI lewat kode yang sama saat serving** (`SettingBin.rewrittenLocal` + `GameServer.serveServersetting` dijalankan langsung terhadap mirror — bukan tebakan):

**a) Bukti rewrite (output nyata):**

| Field | File di www/ (asli) | Dijawab server (mode LOCAL) |
|---|---|---|
| setting bin `loginServer` | `https://login.popoh5.com` | **`http://127.0.0.1`** (+`loginPort`/`loginWebPort` → 8610) |
| setting bin `tsLoginServer` | `http://118.24.68.91:7003` | **`http://127.0.0.1:8610`** |
| setting bin `url` (H5) | `https://dragonh5cdn.popoh5.com/bs/index-native.html?v=…` | **`http://127.0.0.1:8080/bs/index-native.html?v=…`** |
| `serversetting.json` `loginserver` | `https://login.popoh5.com:610` | **`http://127.0.0.1:8610`** |

File di `www/` TIDAK pernah diubah — penulisan-ulang terjadi **SAAT DIJAWAB server** (aturan §4 "rewrite sisi server"). Bukti login memang lokal dari log owner sendiri: sesi `UHeZbC…`/`ElOe1M…` = klien H5 tersambung ke **8610 lokal** (bila loginserver masih ke server asli, sesi itu tidak akan pernah ada).

**b) Peta arah koneksi (mode LOCAL):**

| Koneksi | Arah | Sengaja/by design? |
|---|---|---|
| Konten game (H5, JS, config, resource) | **LOKAL** — mirror `www/` (kurang → diambil sekali dari CDN, tersimpan) | ya — inti proyek |
| Layar SDK, daftar/masuk, loginchecknative | **LOKAL** port 8610 (dragonball.db) | ya — §2.11–§2.12 |
| Daftar server (GetServerList) | **SNAPSHOT ASLI bawaan APK** (2109 server — `assets/server/login/servers.json`) | **SENGAJA** — §2.10; boleh diganti pemilik lewat `config/servers.json` tanpa rebuild |
| Gameplay (setelah pilih server) | **SERVER GAME ASLI** `s<N>-bs.popoh5.com:<port>` | **SENGAJA** — progres/keselamatan data ada di sana; server lokal tidak menjalankan logika game (lingkup §Lingkup: analisis gameplay dikecualikan). Mengosongkan daftar = game tak bisa masuk |

**c) BOOT 3 BARIS** (menggantikan 5-6 baris siap bertumpuk 0.4.5–0.4.6: "dragonball.db siap" + "Server siap — mirror…" + "Mode:" + "Server lokal hidup" + "Tombol melayang tampil"):

```
06:47:24  ✔ Server lokal siap — game :8080 · login :8610 · dasbor :4000
06:47:24  ℹ Mode LOCAL — konten game: mirror www/ (31 file) · login & akun: lokal :8610 · daftar server: snapshot asli (2109 server) · gameplay: server game asli — progres tersimpan di sana
06:47:24  ℹ Pemain: guest99599159 — identitas & sesi tersimpan lokal · dragonball.db 44,0 KB (tahan reboot)
```

Lalu alur SDK linear: `▶ SDK: perangkat 8655A9EB39… → tamu guest99599159 (identitas tamu lama dipertahankan)` → `✔ SDK: loginchecknative OK — tamu guest99599159` → `▶ Login lokal OK — guest99599159 (akun lama) · 0 ms` → `◀ Daftar server dikirim: 2109 server (snapshot asli bawaan APK) · 0 ms`.

Perubahan kecil 0.4.7: dedupe `loginchecknative OK` 10 dtk → **10 menit** per uid (ganti uid tetap dilaporkan); turun tingkat ke TEKNIS (tetap terekam di `human.log`): `dragonball.db siap`, `Tombol melayang tampil`, `Login lokal: client tersambung`; bahasa diperbaiki: **"Pemain"** (akun email juga pemain — bukan "guest"), **"identitas & sesi tersimpan lokal"** (bukan "progres lokal dipakai" — progres ada di server game asli), **"perangkat → tamu"** (bukan "diikat … migrasi identitas"); dasbor + baris **modemap** permanen (`Konten game: MIRROR lokal www/ · Login & akun: LOKAL :8610 · Daftar server: snapshot asli · Gameplay: server game asli`) + kartu lipat **"Ke mana koneksi mengarah"** di Data & jaringan.

### 2.16 LOG CERITA ALUR — SATU KEJADIAN SATU BARIS + PENJELAS TUNTAS (0.4.8; laporan owner sesi 09:39–09:41: *"tidak ada revisi!!!! log masih tidak jelas, padahal sampai loginserver munculkan serverlist.. tapi log cuma Sampah gak jelas"*)

**Diagnosis dari paste owner** (paste itu = keluaran tombol **Copy** dasbor — jadi format Copy ikut diperbaiki). Lima akar masalah:

1. **Format Copy rusak** — jam+ikon+teks nempel tanpa jarak (`09:39:54✔Server lokal siap`) dan ringkasan grup menggantung tanpa jam/ikon di bawah (`404 fallback: 9 file · …`) → terlihat seperti sampah saat ditempel.
2. **Satu kejadian = dua baris** — `▶SDK: MASUK — akun …` lalu `⚠SDK: MASUK gagal — …` berdempet 0 dtk (percobaan yang langsung selesai tidak perlu diumumkan).
3. **Tidak ada akibat/lanjutan** — setelah login gagal tidak dijelaskan "lanjut apa"; setelah daftar server log diam tanpa penjelasan → owner menilai log "tidak menceritakan sampai serverlist".
4. **Jargon & ikon samar** — `▶`/`◀`, awalan `SDK:`, "snapshot asli bawaan APK", "identitas tamu lama dipertahankan".
5. **Umpan balik UI dicatat sebagai log server** — tiga baris `Mode log dasbor: RINCI/BERSIH` (itu aksi owner sendiri di dasbor, bukan kejadian server).

**Aturan 0.4.8** (menyempurnakan model §2.14 — alur tetap linear, batch tetap grup):

- **Satu kejadian satu baris**: hasil dulu, lalu sebab, lalu akibat/lanjutan bila relevan.
- Ikon hanya **✔ hasil · ⚠ masalah · ℹ info** — `▶`/`◀` dihapus, awalan `SDK:` dibuang.
- **Baris TUNTAS** setelah GetServerList (dedupe 10 menit): daftar = **bawaan APK yang DISAJIKAN server lokal** (bukan diambil dari server asli) + penjelas kenapa log diam setelahnya — koneksi gameplay **langsung ke server game asli**, tidak lewat server lokal.
- **Detail (sebab teknis) kini TAMPIL** kecil di dasbor (dulu hanya hidup di `human.log`) dan ikut disalin.
- Baris umpan balik UI **tidak dilog** lagi (toggle RINCI: status terlihat di tombol + `server.json`).
- Boot: baris Mode dipangkas jadi satu kalimat (peta penuh tetap di dasbor — modemap §2.15).
- Format Copy: `09:40:24 ⚠ pesan` + detail menjorok + ringkasan grup berawalan `Σ` dengan jam.

**Alur yang sama (sesi 09:39–09:41) SEBELUM → SESUDAH:**

```
# SEBELUM (0.4.7)                                # SESUDAH (0.4.8)
09:40:24▶SDK: MASUK — akun "tes@mail.con" ·      (tidak ada — percobaan yang langsung
  kata sandi 32 karakter                          selesai tidak diumumkan)
09:40:24⚠SDK: MASUK gagal — akun tidak dikenal / 09:40:24 ⚠ Login akun "tes@mail.con" DITOLAK
  kata sandi salah → 40101 (tes@mail.con)          — akun tidak dikenal / kata sandi salah (40101)
09:40:26▶SDK: perangkat 8655A9EB39… → tamu       09:40:26 ℹ Lanjut masuk sebagai tamu
  guest23312769 (identitas tamu lama               guest23312769 (perangkat ini memang
  dipertahankan)                                   terikat ke tamu itu)
09:40:29✔SDK: loginchecknative OK — tamu         09:40:29 ✔ Sesi tamu guest23312769 disahkan
  guest23312769 (mode tamu)                        (loginchecknative) — boot game dilanjutkan
09:40:36◀Daftar server dikirim: 2109 server      09:40:36 ✔ Daftar server terkirim — 2109 server
  (snapshot asli bawaan APK) · 0 ms                (daftar bawaan APK, disajikan server lokal)
                                                 09:40:36 ℹ Tahap login lokal TUNTAS — berikutnya
                                                   game menyambung LANGSUNG ke server game asli
                                                   pilihan pemain (koneksi gameplay tidak lewat
                                                   server lokal — wajar log tidak menambah
                                                   baris di tahap ini)
```

Ringkasan grup di Copy kini berbentuk `09:41:20 Σ Mirror CDN — 105 file · 2,4 MB · 0 gagal (3,9 dtk) — rincian di dasbor` (berjam + penanda Σ — tidak lagi menggantung tanpa identitas).

### 2.17 LOG EMOJI + BLOK RINCIAN (0.4.9; umpan balik owner: *"emoji sudah bagus, hanya saja masih kurang rinci"* + *"ingat gak boleh disingkat"* — setelah menyetujui contoh output baris-per-baris)

**Aturan ikon 0.4.9** — emoji per kategori (menggantikan simbol tipis ✔/⚠/ℹ yang samar; `HumanLog` ikon `char` → `String` karena emoji = 2 char UTF-16):

| Emoji | Arti | Emoji | Arti |
|---|---|---|---|
| 🚀 | server boot / siap | ✅ | aksi sukses |
| 👤 | pemain (tamu / akun) | ⚠️ | masalah (tetap jalan) |
| 🔑 | pengesahan sesi (loginchecknative) | ❌ | gagal (terhenti) |
| 📡 | koneksi (socket / handshake) | 📋 | daftar server |
| 💾 | data / dragonball.db | 🧹 | pembersihan / mirror |
| ℹ️ | info lain | | |

**Blok rincian `├─/└─`** — setiap baris penting membawa rincian PENUH (aturan owner: **TIDAK boleh disingkat**): fakta nyata yang dimiliki server (ms, byte, uid, sid, sumber data, sebab, akibat/lanjutan). Rincian tampil sama di tiga tempat: dasbor (baris kecil di bawah teks, pohon penuh), `human.log` (`HumanLog.tree`), dan hasil **Copy**. Contoh alur sesi 09:39–09:41 versi 0.4.9:

```
09:39:54 🚀 Server lokal siap — mode LOCAL
         ├─ Port    : game :8080 · login :8610 · dasbor :4000
         ├─ Mode    : LOCAL — konten game: mirror www/ · login & akun: LOKAL :8610
         │            (config ditulis-ulang otomatis) · daftar server: snapshot asli
         │            bawaan APK · gameplay: SERVER GAME ASLI (progres tersimpan di sana)
         ├─ Folder  : /storage/emulated/0/Android/data/com.db.local/server (penyimpanan eksternal)
         └─ Pemain  : guest99599159 — sesi & data di dragonball.db 44,0 KB (tahan reboot)

09:40:12 👤 Layar SDK — masuk sebagai tamu guest99599159
         ├─ Perangkat  : 8655A9EB39…
         ├─ Cara masuk : registerVisitor — perangkat ini sudah terikat ke tamu itu
         │               (identitas lama dipakai lagi)
         └─ Identitas  : tamu guest99599159 — tersimpan dragonball.db (tahan reboot)

09:40:29 🔑 Sesi tamu guest99599159 disahkan — game lanjut boot
         ├─ Uid      : guest99599159 · mode tamu
         ├─ Hasil    : code 0 (skema respons asli login.popoh5.com:510) — shell
         │             melanjutkan boot ke H5
         └─ Sesi     : disimpan ke dragonball.db (sdk_sessions) — url game memakai
                       userid ini

09:40:33 📡 Klien game tersambung ke server login lokal
         ├─ Sesi     : UHeZbC… (engine.io v3 · polling, tanpa websocket)
         ├─ Tujuan   : 127.0.0.1:8610 (loginServer hasil rewrite setting bin)
         └─ Lanjutan : menunggu loginGame + GetServerList dari klien H5

09:40:34 ✅ Login game sukses — akun lama
         ├─ Akun     : guest99599159
         ├─ Proses   : 2 ms · jawaban 340 B (ret 0, skema asli login.popoh5.com:610)
         └─ Sumber   : dragonball.db (accounts) — identitas mengikuti pilihan layar SDK

09:40:36 📋 Daftar server terkirim — 2109 server
         ├─ Sumber   : snapshot bawaan APK (config/servers.json) — disajikan server
         │             lokal, bukan diambil dari server asli
         ├─ Ukuran   : 180,4 KB · 0 ms · pemain sekarang di layar pilih server
         └─ Lanjutan : pemain pilih server → gameplay LANGSUNG ke server game asli
                       pilihan pemain (koneksi gameplay tidak lewat server lokal —
                       wajar log tidak menambah baris di tahap ini)
```

**Perubahan mekanis 0.4.9:**

1. 📡 handshake socket **naik dari TEKNIS ke penting** (alur harus terlihat "sampai serverlist") — dedupe 10 menit supaya reconnect tidak membanjiri.
2. 👤 layar SDK = **SATU baris** meskipun `registerVisitor` dan `autoLogin` sama-sama terpanggil saat boot — `SdkData.noteSdkLogin` (dedupe 10 mnt per uid, ganti uid selalu dilog). Duplikat log daftar akun (`SdkData` + `SdkApi` berdempet 0 dtk) dihapus — cukup baris `SdkApi`.
3. Baris ℹ "TUNTAS" 0.4.8 **dilebur ke rincian `Lanjutan`** milik 📋 — satu kejadian satu baris tetap terjaga, isi penjelasan tidak dipangkas.
4. Gagal tetap satu baris + rincian: `⚠️ Login akun "tes@mail.con" DITOLAK…` + `Sebab/Bukti/Lanjutan`.
5. Dasbor: teks log 12→13px, waktu 10→11px, rincian 10→11.5px dengan kontras lebih tinggi (dulu abu-abu tipis), kolom ikon dilebari untuk emoji, tanpa pemenggalan (`overflow-wrap:anywhere`), legenda footer emoji. Copy memuat blok `├─/└─` penuh.
6. Boot: tiga baris 0.4.7–0.4.8 (siap/Mode/Pemain) dilebur jadi SATU baris 🚀 ber-rincian — isi peta arah koneksi tetap lengkap di rincian `Mode`.

### 2.18 REKAM 0.5.0 — perekam payload mentah + CONFIG ASLI semua lapisan (permintaan owner: *"seperti capture HttpCanary tapi kita buat ini lebih canggih — HttpCanary itu cuma jejaknya, bukan config aslinya"*)

**Latar masalah.** Log 0.4.9 adalah buku cerita (ringkasan kejadian), bukan alat tangkap: isi mentah `loginGame`/`GetServerList` dibuang setelah dihitung ukurannya, body SDK lewat tanpa rekaman, setting bin tidak pernah disimpan dalam bentuk terbaca, dan tahap setelah 📋 buta total — klien `io.connect` LANGSUNG ke `sXXXX-bs.popoh5.com:PORT` (hasil bedah `main.min.js`: `ts.loginInfo.serverItem.url` → `connectToServer` → handshake `verify` TEA kunci `"verification"` → action `enterGame`). Selama ini loginserver dibangun dari bedah JS + probe manual — tidak dari rekaman hidup yang bisa diverifikasi ulang.

**Tiga keunggulan yang HttpCanary tidak mungkin punya:**

1. **Loopback terlihat 100%** — HttpCanary (VPN-capture) buta terhadap trafik ke `127.0.0.1:8080/8610` — justru SELURUH alur LOCAL. REKAM berdiri di dalam server sendiri: tidak ada byte yang luput.
2. **Payload didekode** — body SDK dibuka dari `base64+sign`, setting bin didekripsi XOR `"DragonBall"` + hasil rewrite; yang terekam adalah config asli yang BENAR-BENAR diterima klien, bukan hexdump.
3. **Config asli jadi FILE** — setiap jawaban config utuh di `capture/payload/NNNNNN-arah-nama.json` — bisa dipakai ulang sebagai aset (pola yang sama dengan `servers.json` snapshot yang kini dilayani `GetServerList`). HttpCanary menampilkan jejak; REKAM memberi bahan bangun.

**Yang direkam (arah dari sudut pandang server; → masuk, ← keluar):**

| Lapisan | Isi rekaman |
|---|---|
| `login` (:8610) | handshake + `open` engine.io, event `handler.process` utuh (`loginGame`, `GetServerList`, `LoginAnnounce`, `Save*`, aksi tak dikenal), ACK `{"ret":0,...}` utuh (daftar server 520 KB ikut tersimpan file), `ping→pong`, `disconnect`/`expired` |
| `sdk` (/v1/*, loginchecknative) | body DIDEKODE dari base64+sign + jawaban penuh; mode ONLINE = jawaban ASLI `qsdk.t4game.com` / `login.popoh5.com:510` ikut terekam (pembanding langsung versi lokal) |
| `game` (:8080) | setting bin DIDEKRIPSI + ditulis-ulang, `serversetting.json` hasil rewrite, config `json/version/properties/txt/bin` ≤ 512 KB (mirror & passthrough ONLINE dibuffer lalu disajikan — perilaku tidak berubah); resource biner (png/zip/js) cukup barisnya |
| `proxy` | disiapkan untuk proxy gameplay 0.5.1 (`verify` TEA → `enterGame` → action gameplay) |

**Penyimpanan dua sisi** (folder baru `capture/` — masuk whitelist Cleaner, bukti tidak terhapus tiap boot):

- `capture/rekam.jsonl` — jejak lengkap berurutan (payload ≤ 32 KB tertanam utuh; rotasi 8 MB).
- `capture/payload/` — payload utuh per kejadian (≤ 2 MB), maks 400 file, terlama dipangkas. File inilah config asli.
- Memori: cincin 600 entri untuk tab REKAM (ringan — hanya preview).

**Dasbor — bagian REKAM** di bawah grup batch: `⏺ ON/OFF` (tersimpan `server.json` `"rekam"`, bawaan ON), filter lapisan `login/sdk/game`, arah `→/←`, pencarian payload, klik baris = payload utuh + **Salin payload utuh** (`/api/rekam/payload?id=N`), **Unduh** `rekam.jsonl`, **Bersihkan** (ring dasbor saja — file tetap), dan **Tes server game**.

**Baris log baru 0.5.0** (alur tidak diam lagi setelah 📋):

```
09:41:10 📡 Server game bisa dihubungi — S2109 — s2109-bs.popoh5.com:8061 · 210 ms
         ├─ Server  : 2109 — urutan #1 daftar (pilihan paling mungkin)
         ├─ DNS     : s2109-bs.popoh5.com → 47.88.x.x
         └─ Lanjutan: klien io.connect LANGSUNG ke sini setelah pemain memilih
                       (bukan lewat server lokal)

09:41:52 🔌 Klien menutup sesi login — normal setelah pilih server (pindah ke server game)
         ├─ Sesi     : kFdzRO… · hidup 14 dtk
         ├─ Terakhir : GetServerList
         └─ Lanjutan : klien io.connect LANGSUNG ke url server pilihan pemain
                       (https://sXXXX-bs.popoh5.com — di luar server lokal; alurnya
                       masuk tab REKAM saat proxy gameplay 0.5.1 aktif)
```

Jika server game TIDAK terjangkau (seperti `dragon.sjmobilegame.com:82` di beberapa jaringan): baris ⚠️ menjelaskan bahwa game akan DIAM di layar menyambung tanpa log lain — jawaban atas pertanyaan berulang "kenapa log berhenti".

**API dasbor baru:** `GET /api/rekam?since=` · `GET /api/rekam/payload?id=` · `GET /api/rekam/toggle?on=0|1` · `GET /api/rekam/clear` · `GET /api/rekam/download` · `GET /api/rekam/probe`.

**Cara pakai untuk membangun/ menambah server** (alur kerja berbasis bukti): nyalakan REKAM → mainkan alurnya → buka tab REKAM → bandingkan payload asli (ONLINE) dengan jawaban lokal (LOCAL) → tiru skemanya. Bukan menebak dari bedah JS saja — bukti hidup ada di `capture/`.

### 2.19 CONFIG LOGIN SEMUA MODE (0.5.1 — tegasan owner: *"SAYA TEGASKAN LAGI CONFIG YG JELAS BUKAN REKAM JEJAAAAK"*; laporan *"config Setelah SDK login ini masuk ke loginserver sampai detik ini anda ngeyel"* + *"saya sudah pilih server lalu klik play dan log hanya tampil itu"*)

**Latar.** Log ONLINE owner (22:57) membuktikan titik lemah 0.5.0: mode ONLINE menyajikan setting bin **ASLI tanpa tulis-ulang** → setelah SDK login, `loginchecknative` shell dan socket.io H5 (`loginGame`/`GetServerList`) **langsung ke `login.popoh5.com:610/510`** — tidak lewat server lokal, log bisu, REKAM buta. Owner menegaskan tuntutannya sejak 0.4.7 dan mengulanginya: **config setelah SDK login HARUS masuk ke login server lokal — di SEMUA mode**. Itu pekerjaan config, bukan perekam.

**Bukti probe langsung (2026-11-05, sandbox):**

| Target | Hasil |
|---|---|
| `https://login.popoh5.com:610/socket.io/?EIO=3&transport=polling` | **HTTPS wajib** — jawab `96:0{"sid":"…","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":5000}2:40`; HTTP polos ditolak: `400 The plain HTTP request was sent to HTTPS port` |
| Sertifikat 610 | TLSv1.2 · ECDHE-RSA-AES128-GCM-SHA256 · CN `*.popoh5.com` · issuer DigiCert (Encryption Everywhere DV TLS CA - G2) — valid |
| `https://login.popoh5.com:510/loginchecknative?uid=x&sdk=BS` | `{"code":500,"msg":"登录参数验证失败"}` — skema asli (HTTPS juga) |
| `dragonh5cdn.popoh5.com/bs/resource/properties/serversetting.json` | `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}` |

**Perubahan 0.5.1 — klien TIDAK PERNAH lagi langsung ke login.popoh5.com di mode mana pun:**

| Lapisan | Mode LOCAL (tidak berubah) | Mode ONLINE (baru 0.5.1) |
|---|---|---|
| Setting bin | tulis-ulang penuh (LOCAL) | diteruskan dari `configus`/COS → **tulis-ulang jalur login + url H5** (`SettingBin.rewrittenOnline`): `loginServer`→`http://127.0.0.1`, `tsLoginServer`→`:8610`, `loginPort/loginWebPort`→8610, `url`/`update`→`http://127.0.0.1:8080`; `tsUrl` tetap asli (persis gaya LOCAL); TANPA `userid` di url (identitas boot = mekanisme shell asli) |
| `serversetting.json` | tulis-ulang `loginserver` (sudah ada) | passthrough dari `dragonh5cdn` → **tulis-ulang `loginserver` → `http://127.0.0.1:8610`** sebelum disajikan |
| `loginchecknative` (shell) | dilayani lokal | **diteruskan utuh** ke `https://login.popoh5.com:510` (`SdkApi.proxyLoginCheckNative` — jalur BAKU sekarang) + baris 🔑 "Sesi disahkan via SERVER ASLI (passthrough)" (dedupe 10 mnt/uid) |
| socket.io login | dilayani lokal (LoginData) | **diteruskan utuh byte-per-byte** ke `https://login.popoh5.com:610` (`LoginServer.proxyOnline` — HTTPS upstream, hasil probe; websocket TIDAK diteruskan → engine.io client tetap polling, perilaku terbukti di device) |
| Daftar server | snapshot bawaan APK | **DAFTAR ASLI HIDUP dari server** (passthrough) — bukan snapshot |
| Gameplay | langsung ke server game asli | sama (tidak berubah) — progres tetap tersimpan di sana |

**Akibat yang langsung terasa (jawaban keluhan owner):**

1. Config setelah SDK login **selalu menunjuk server login lokal** — modemap dasbor & baris 🚀 Mode diperbarui ("semua mode").
2. Log ONLINE kini bercerita penuh: 📡 *Klien tersambung — diteruskan ke SERVER ASLI* → 🔑 *Sesi disahkan via SERVER ASLI (passthrough)* → ✅ *Login game diteruskan ke server ASLI* → 📋 *Daftar server ASLI terkirim — N server (passthrough)* → 🔌 *Klien menutup sesi (pindah ke server game)*. "Klik play lalu log diam" tidak terjadi lagi di tahap login.
3. REKAM lapisan `login` di mode ONLINE kini **merekam payload ASLI** `loginGame`/`GetServerList` (pembanding utama versi lokal) — bahan bangun 0.6.
4. Token & identitas tetap dikeluarkan **SERVER ASLI** → progres/keselematan data pemain tidak berubah sedikit pun; server lokal hanya penghubung transparan.
5. Kalau `login.popoh5.com` tidak terjangkau lewat passthrough → baris ⚠️ jelas + 502 (perilaku gagal = sama seperti koneksi langsung gagal — tidak ada regresi).

**Pengaman:** passthrough tanpa logika protokol (tidak ada yang bisa "salah tiru"); websocket dijawab 400 → client fallback polling; framing rusak tidak meledak (`parseFrames` tahan); `rewrittenOnline` gagal → bin asli disajikan (perilaku lama).

**Harness:** +16 tes TAHAP 16 (bin ONLINE: url/update/loginServer/ports/tsLoginServer/tsUrl-asli/tanpa-userid/bin-tetap-terenkripsi; serversetting ONLINE; framing parser + tahan rusak; hitung server ACK; upstream HTTPS) — **96/96 LULUS** (80 lama + 16 baru).

**API dasbor baru:** `GET /api/rekam?since=` · `GET /api/rekam/payload?id=` · `GET /api/rekam/toggle?on=0|1` · `GET /api/rekam/clear` · `GET /api/rekam/download` · `GET /api/rekam/probe`.

---

### 2.20 AKAR CONFIG — ISI CONFIG DIBEDAH DI LOG (0.5.2 — tegasan owner: *"pakai config online murni untuk server lokal, padahal akarnya aja gak tau .. Saya sudah TEGASKAN log yg jelas rinci itu penting"*)

**Latar.** 0.5.1 sudah menulis-ulang config di SEMUA mode, tetapi log hanya **MENGATAKAN** "endpoint login ditulis-ulang ke server lokal" — isi config-nya sendiri tidak pernah tampil. Owner tidak bisa memeriksa akarnya. 0.5.2 menjadikan isi config **BUKTI DI LOG**: setiap config yang disajikan (mode mana pun) **dibedah field-per-field** — nilai **ASLI dari server → nilai yang benar-benar diterima klien** — baris `📦` PENTING (selalu tampil), tanpa singkatan.

**Hasil bedah akar (file nyata, didekripsi XOR "DragonBall" → JSON):**

| Field | Nilai ASLI (server) | Klien terima (server lokal) | Peran (akar) |
|---|---|---|---|
| `testMode` | `1` | `1` (asli) | flag mode uji bawaan server |
| `url` | `https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935` | `http://127.0.0.1:8080/bs/index-native.html?v=202109150935&userid=<uid>` (LOCAL; ONLINE tanpa `userid`) | URL H5 game dibuka shell WebView (boot game) |
| `tsUrl` | `http://dragon.sjmobilegame.com:82/bs103/index-native.html` | **ASLI** — varian uji, jalur produksi tidak memakainya | — |
| `update` | `https://dragonh5cdn.popoh5.com/bs/upgrade` | `http://127.0.0.1:8080/bs/upgrade` | paket pembaruan (zip + upgrade.json) |
| `checkTime` | `300` | `300` (asli) | jarak cek pembaruan (detik) |
| `loginServer` | `https://login.popoh5.com` | `http://127.0.0.1` | **HOST login** — shell menambah `:loginPort` (socket.io) dan `:loginWebPort` (loginchecknative) sendiri |
| `tsLoginServer` | `http://118.24.68.91:7003` | `http://127.0.0.1:8610` | HOST login varian uji |
| `loginPort` | `610` | `8610` | port socket.io login |
| `loginWebPort` | `510` | `8610` | port web login SDK (loginchecknative) |
| `clientParams` | `hideList` 5 harga · `supportLang` 3 bahasa (en, fr, kr) · `showCurChannel` toFacebook | **ASLI** | pengaturan klien (toko/bahasa/kanal) |

Bin kedua `lzceshi/native_setting/Android/setting_BS_Android.bin` (350 B) = config sama **TANPA** `clientParams`. `serversetting.json` (JSON polos, dibaca H5 `main.min connectToLogin`): `loginserver` `https://login.popoh5.com:610` → `http://127.0.0.1:8610`; `requireSDK` false (asli).

**Bentuk baris log baru (contoh nyata hasil bedah — mode ONLINE):**

```
📦 AKAR CONFIG /bs/db/android/setting_BS_Android.bin — dibedah field-per-field (ONLINE · dari server asli)
    ├─ Enkripsi     : XOR kunci "DragonBall" — dibedah field-per-field sebelum disajikan
    ├─ testMode     : 1  (asli) — flag mode uji bawaan server
    ├─ url          : https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935  →  http://127.0.0.1:8080/bs/index-native.html?v=202109150935
    │               : URL H5 game dibuka shell WebView (boot game)
    ├─ tsUrl        : http://dragon.sjmobilegame.com:82/bs103/index-native.html  (asli) — varian uji — jalur produksi tidak memakainya
    ├─ update       : https://dragonh5cdn.popoh5.com/bs/upgrade  →  http://127.0.0.1:8080/bs/upgrade
    │               : endpoint paket pembaruan (zip + upgrade.json)
    ├─ checkTime    : 300  (asli) — jarak cek pembaruan (detik)
    ├─ loginServer  : https://login.popoh5.com  →  http://127.0.0.1
    │               : HOST login — shell menambah :loginPort (socket.io) dan :loginWebPort (loginchecknative) sendiri
    ├─ tsLoginServer: http://118.24.68.91:7003  →  http://127.0.0.1:8610
    ├─ loginPort    : 610  →  8610
    ├─ loginWebPort : 510  →  8610
    ├─ clientParams : hideList 5 harga disembunyikan · supportLang 3 bahasa (en, fr, kr) · showCurChannel toFacebook
    └─ Diterima klien: 716 B · 12 ms
```

**Rincian tambahan alur login (passthrough ONLINE, 0.5.2):** ✅ *Login game diteruskan* kini membawa **Akun + Sandi (panjang karakter, diteruskan UTUH)**; 📋 *Daftar server ASLI terkirim* kini membawa **Gerbang pertama … terakhir** dari daftar ASLI (`https://s1-bs… … https://s2109-bs…`) — jadi sebelum tombol Play ditekan, log sudah menunjukkan ke mana klien akan lompat.

**Perubahan kode:** `ConfigAkar.java` (BARU — pembedah config: setting bin + serversetting, field mentah via `SettingBin.getJsonRaw`, ringkas `clientParams`); `SettingBin` +`getJsonRaw`/`decryptedPlain(File)`/`decryptedPlain(byte[])`; `GameServer.logAkar` (dedupe per isi — config sama beruntun turun ke TEKNIS, tetap terekam human.log); ikon 📦 masuk legenda (HumanLog).

**Harness:** +24 tes TAHAP 17 (bedah bin ASLI→klien penuh; `getJsonRaw` angka murni/string/bool/tak-ada; `clientParams` hideList/supportLang/showCurChannel; bedah serversetting; gerbang pertama/terakhir + tahan rusak) — **129 tes: 105 LULUS + 15 GAGAL LINGKUNGAN** (engine "SQLite sungguhan" butuh native lib sqlite-jdbc yang tidak bisa dimuat di sandbox lari saat ini — 15 GAGAL yang sama persis di kode 0.5.1/HEAD, bukan regresi; semua tes 0.5.2 LULUS 24/24).

---

### 2.21 DUA JALUR — CONFIG OFFLINE MANUAL / ONLINE PASSTHROUGH (0.5.3 — tegasan owner: *"100% beda jalur!! offline dan online .. config offline murni disiapkan manual"* + *"online tebaca itu cuma jadikan jejak arah bukan untuk dijadikan config asli ke offline"*)

**Akar yang menutup kasus "log diam setelah Play" (dibaca langsung dari klien asli `main.min_777039fc.js` + `index-native.html`, dikonfirmasi server LIVE):**

```
1. Native unduh bin dari configus.sjmobilegame.com → XOR "DragonBall" → JSON
2. Native → WebView index-native.html, callback "startGame" kirim {loginServer, thirdParams, clientParams, …}
3. H5 connectToLogin(): TANYA NATIVE DULU (getLoginServer) → connect; fallback web/WeChat: fetch serversetting.json
4. io.connect(loginServer+":"+loginPort) → emit("handler.process", {type:"User",action:…}) — SATU event untuk semua
   respons: {ret:0, data:<LZString-UTF16>, compress} → decompress → JSON
   ├─ loginGame {userId,password,fromChannel,subChannel,…}
   ├─ GetServerList {userId,subChannel,channel} → {errorCode:0, serverList:[…], history:[]}
   └─ enterGame {loginToken,userId,serverId,version,language,gameVersion}
5. KLIK PLAY (baris asli klien): var a = ts.loginInfo.serverItem.url;  mainClient.connectToServer(a, …)
   → io.connect ke FIELD url ENTRI DAFTAR SERVER (dulu: https://sXXXX-bs.popoh5.com — di luar lokal → log diam)
```

**Yang berubah di 0.5.3 — dua jalur 100% terpisah:**

| | **OFFLINE (LOCAL)** | **ONLINE** |
|---|---|---|
| Sumber config | **Paket MANUAL `config-offline/`** (disiapkan saat build — bukan turunan online, bukan tulis-ulang runtime) | **Server asli** (passthrough `configus`/`dragonh5cdn`/`dragon`) + tulis-ulang jalur login (0.5.1) |
| Setting bin | config-offline/setting_BS_Android.bin **dilayani apa adanya** (bin menunjuk 127.0.0.1 sejak dibuat; `setting.json` terbaca + manifest sha256) | bin ASLI dari server + `SettingBin.rewrittenOnline` (hanya jalur login + url H5) |
| `serversetting.json` | config-offline/ — `loginserver http://127.0.0.1:8610`, apa adanya | ASLI + tulis-ulang `loginserver` |
| `GetServerList` | **1 entri manual "Server Lokal" `url http://127.0.0.1:8080`** — klik Play masuk SERVER GAME LOKAL | daftar ASLI (gerbang popoh5 diteruskan; progres pemain di sana) |
| Jaringan luar | **NOL** — `trySeed` (mirror-melengkapi-diri) dihapus dari LOCAL; 404 = lanjut boot normal | passthrough (butuh jaringan) |
| Log | alur penuh satu kolom: 📡 → 🔑 → ✅ → 📋 (url 127.0.0.1) → 🔌 → 🔌 Play | 📡 → 🔑 → ✅ → 📋 (gerbang ASLI) → 🔌 |

**Isi paket manual (assets/server/config-offline/ → `<root>/config-offline/`, selalu ditimpa = kanonik):**
```
setting.json (sumber terbaca — 668 B):
  url/tsUrl → http://127.0.0.1:8080/bs/index-native.html · update → http://127.0.0.1:8080/bs/upgrade
  loginServer → http://127.0.0.1 (tanpa port — shell sambung sendiri) · tsLoginServer → http://127.0.0.1:8610
  loginPort/loginWebPort → 8610 · testMode 1 · checkTime 300
  clientParams → hideList 5 harga · supportLang en/fr/kr · showCurChannel toFacebook (UI pemain tidak berubah)
setting_BS_Android.bin (668 B) = setting.json XOR "DragonBall" (protokol shell asli)
serversetting.json: {"loginserver":"http://127.0.0.1:8610","requireSDK":false}
servers.json: 1 entri {serverId "1", url http://127.0.0.1:8080, name "Server Lokal", online,new,hot}
manifest.txt: sha256 keempat file + catatan (port beda di server.json → paket dibuat ulang, bukan runtime)
```

**Pembersihan jalur aktif (online hanya jejak arah — tidak lagi config offline):** snapshot online DIHAPUS dari assets — `server/login/servers.json` (2109 server, 520.5 KB), `www/bs/db/android/setting_BS_Android.bin`, `www/lzceshi/…/setting_BS_Android.bin`, `www/bs/resource/properties/serversetting.json`; `Payload` mirror v4 (www/ = resource H5 murni); build.sh menghapus cermin config online dari APK; `LoginData.init(configDir, offlineDir)` — daftar server **hanya** dari `config-offline/servers.json`, fallback darurat **TETAP lokal** (`http://127.0.0.1:<gamePort>`, tidak pernah popoh5); `Cleaner` melindungi `config-offline/`.

**Dasbor SATU LAYAR (owner: "kebanyakan tab semua di pisahkan malah bikin kacau"):** seksi REKAM dihapus (CONFIG bukan rekam jejak; mesin Capture tetap untuk jalur ONLINE — tanpa UI), satu kolom log kronologis, baris jalur config selalu terlihat di kepala (`OFFLINE — CONFIG MANUAL config-offline/…` / `ONLINE — config dari SERVER ASLI (passthrough)`), bingkai log ikut warna jalur (hijau/kuning); kartu-kartu teknis yang tumpang tindih dihapus; bug dasbor lama (`e-login2`/`e-srv` elemen tak ada → TypeError → pill selalu "PUTUS") ikut hilang bersama penulisan-ulang.

**Log OFFLINE sekarang (alur penuh, contoh):**
```
📋 Daftar server terkirim — 1 server (paket MANUAL)
    ├─ Sumber   : config-offline/servers.json — paket CONFIG OFFLINE MANUAL bawaan APK
    │             (bukan snapshot online — owner: online cuma jejak arah, bukan config asli offline)
    ├─ Tujuan   : url tiap entri = 127.0.0.1:8080 — klik Play masuk SERVER GAME LOKAL
    └─ Ukuran   : 340 B · 1 ms · pemain sekarang di layar pilih server
📦 AKAR CONFIG /bs/db/android/setting_BS_Android.bin — dibedah field-per-field (OFFLINE · paket manual config-offline/)
    └─ (semua field = nilai paket manual — yang diterima klien PERSIS file ini)
```

**Harness:** +13 tes TAHAP 18 (daftar dari paket manual & bukan snapshot `config/` popoh5; fallback tanpa paket tetap lokal; templat bin manual semua endpoint 127.0.0.1 + loginServer tanpa port; serversetting manual + requireSDK; Cleaner melindungi `config-offline/`; GetServerList HTTP berisi url 127.0.0.1 & nol popoh5; bedah akar tetap bekerja utk paket manual) — **133 tes: 118 LULUS + 15 GAGAL LINGKUNGAN** (SQLite native sandbox — 15 yang sama persis di HEAD sebelum perubahan, bukan regresi). Kompilasi penuh ECJ + d8 + zipalign + sign LULUS; APK-LSF-0.5.3.apk (sha256 `137f12b6…`) berisi paket `config-offline/` lengkap dan TANPA cermin config online.

---

### 2.22 LOG DUA JALUR + RELAY SERVER GAME (0.5.4 — laporan owner: *"log campur adukk, padahal di posisi original online tapi lognya local malah ikut .. log isi config di ikutkan ngapain di pisah, kalau isi config besar ya bisa show hide .. kalau sedang local log ya isinya local sesuai lah .. kalai log Online ya log aslinya seperti httpcanary .. udah log gak jelas, sekarang cuma loading muter2 gk ada kejelasan di log"*)

**Empat koreksi, satu prinsip: isi feed log = mode yang sedang aktif, tanpa sampah.**

**a) LOG CAMPUR ADUK ANTAR MODE — dibereskan di sumbernya.** Setiap entri log kini membawa tag mode (`HumanLog.setMode` → `"m":"local|online"` di events.jsonl). Baris boot milik jalur lain DIHAPUS dari emit: 💾 *"Config OFFLINE manual siap"* (dulu muncul juga di boot ONLINE — karena dicatat sebelum `server.json` dibaca) kini hanya di LOCAL; baris [📼] REKAM **dihapus total** (Capture.java dijadikan no-op — CONFIG bukan rekam jejak; tidak ada lagi folder `capture/` dan endpoint `/api/rekam*` dihapus). Probe server game di boot hanya jalan di LOCAL (di ONLINE dulu malah memeriksa `127.0.0.1:8080` milik sendiri → *"bisa dihubungi · 2 ms"* = **positif palsu** yang menyesatkan).

**b) ISI CONFIG = SHOW/HIDE, BUKAN FEED.** Bedah field-per-field ConfigAkar (📦 0.5.2) **keluar dari feed log** — kini disimpan di `ConfigView` dan tampil di **seksi CONFIG dasbor** (show/hide per berkas: bedah ASLI → klien + teks config). Feed hanya menerima SATU baris padat per config (dedupe 10 menit — dulu 4× beruntun banjir): `📦 CONFIG setting bin disajikan — paket manual config-offline/ · 668 B · 1 ms`.

**c) MODE ONLINE = GAYA HTTPCANARY; MODE LOCAL = GAYA LOKAL.**
- ONLINE: setiap request passthrough = satu baris — `🌐 ⇄ GET dragonh5cdn.popoh5.com/bs/js/game.min.js → HTTP 200 · 412,3 KB · 340 ms` (config/protokol/galat = PENTING; aset statis = TEKNIS/RINCI). SDK passthrough ikut: `🌐 ⇄ POST qsdk.t4game.com/v1/system/init → 200 · 1,2 KB · 340 ms`.
- LOCAL: `📄 GET /bs/js/game.min.js · mirror www/ · 412,3 KB · 12 ms`; config paket manual = baris 📦 padat. Grup Σ (LogGroups) tetap ada sebagai statistik ringkas.

**d) LOADING MUTER-MUTER SETELAH PLAY — ditutup dengan RELAY SERVER GAME + baris penjelasan.** Akar: url entri daftar server manual = `http://127.0.0.1:8080` → klik Play membuat klien `io.connect` **ke port game** memakai engine.io v3 polling; dulu :8080 hanya HTTP statis → klien retry selamanya **tanpa satu baris log pun**. Kini:
- **`/socket.io/` di :8080 (LOCAL) = relay utuh ke SERVER GAME ASLI** — mesin yang sama dengan passthrough login 0.5.1 (byte-per-byte, websocket ditolak → klien tetap polling). Alur Play terlihat: `🎮 Play → socket game lokal :8080 — diteruskan ke SERVER GAME ASLI` + `🎮 → enterGame diteruskan ke server game ASLI · 132 B`.
- **Upstream ditentukan manual** (owner: config offline murni manual): field `"upstream"` pada entri `config-offline/servers.json`, atau lebih cepat **tanpa build ulang**: tulis SATU baris URL di `config/game-upstream.txt` di perangkat (folder `config/` tidak pernah ditimpa boot) — contoh `https://s2109-bs.popoh5.com:8061` (server yang pemain tuju).
- **Upstream kosong = bukan loading diam lagi**: baris `❌ Play berhenti — server game lokal :8080 belum punya UPSTREAM` yang menjelaskan persis cara mengisi. Probe boot + tombol dasbor **"Tes game"** (`/api/probe/game`) kini menguji upstream (bukan diri sendiri).

**Yang berubah di 0.5.4:** `HumanLog` +tag mode; `ConfigView.java` baru (ring 12 entri → `/api/config`); `GameServer` — relay socket game + baris 🌐/📄/🎮 + config compact; `LoginServer` — 📋/🔌 menyebut upstream + isi servers.json masuk seksi CONFIG; `Monitor.probeGameServers` — LOCAL-only, target upstream; `Starter` — boot log sesuai mode + `HumanLog.setMode` + teks 🚀 LOCAL jujur (paket manual apa adanya + status upstream); `SdkApi` — baris ⇄ SDK; `Capture` no-op (REKAM mati total); `LocalServer` — `/api/config` + `/api/probe/game`, `/api/rekam*` dihapus; dasbor — seksi CONFIG show/hide + tombol Tes game + legenda ikon baru; `config-offline/servers.json` +field `"upstream"` (manifest sha256 diperbarui); `StatusStore` 0.5.4.

### 2.23 UTAMANYA LOCAL — MODE BAWAAN SAAT APK DI-INSTALL & DIBUKA (0.5.5 — arahan owner: *"kalau sudah paham implementasi rebuild apk, jika sebelumnya defaultnya saat aplikasi di install dan di buka adalah mode online, sekarang tahap ini saat apk di install dan di buka adalah Utamanya Local"*)

**Yang berubah — SATU nilai: bawaan mode.**

- **APK baru di-install & dibuka** (`config/server.json` belum ada) → `Config.load` menulis DEFAULT_JSON dengan **`"mode": "local"`** → boot LOCAL: konten game = mirror `www/`, login & akun = server lokal, config = paket MANUAL `config-offline/` apa adanya (tanpa tulis-ulang runtime), daftar server = paket manual (1 entri lokal). Baris 🚀 menyebut `mode LOCAL` + status upstream Play.
- **server.json pemilik yang sudah ada TETAP DIHORMATI** — pilihan ONLINE via tombol melayang tersimpan dan tidak dipaksa balik LOCAL; server.json lama berisi `"mode": "online"` (instalan 0.5.4 yang tidak di-uninstall) tetap ONLINE sampai pemilik mengubahnya sendiri.
- Titik ubah: `Config.java` (field bawaan + DEFAULT_JSON + teks pesan ⚠️ bawaan), `StatusStore.java` (fallback mode). Jalur/protokol tidak berubah — mode ONLINE tetap tersedia via tombol melayang.

### 2.24 CAPTURE ISI DI DASBOR :4000 (0.5.6 — arahan owner: *"capture nya ya di port 4000 .. github live preview cuma sebagai contoh tampilan"* — halaman Pages hanyalah contoh bentuk; capture yang sebenarnya hidup di dasbor)

**Satu prinsip: setiap komunikasi di feed dasbor MEMBAWA isi komunikasinya** (gaya HttpCanary — klik ▸ = isi), dengan sumber isi hanya dari yang MEMANG dimiliki server — tanpa rekonstruksi:

- **LOCAL**: file mirror = **byte persis** (teks ≤ 16 KB; potongan file besar diberi catatan jujur ukuran asli; binary = catatan) — baris 📄 (TEKNIS/RINCI); paket config-offline: setting bin = 📦 **bedah field-per-field + teks didekode XOR "DragonBall"**, serversetting = bedah + teks — baris 📦 (dedupe 10 mnt tetap).
- **ONLINE**: config & file teks ≤ 512 KB dibuffer — baris ⇄ membawa **isi persis yang disajikan**; setting bin = bedah **ASLI → klien** + teks yang diterima klien (sama dengan seksi CONFIG); file teks biasa = byte yang dilayani; **qsdk passthrough = jawaban asli server** (byte persis yang diteruskan); binary/besar = catatan jujur (diteruskan utuh, tidak di-capture); 404/502 = isi jawaban pada barisnya.
- **Infrastruktur**: `HumanLog.evIsi/evTechIsi` + field `"isiLabel"/"isi"` di events.jsonl/memori dasbor + blok `▸ ISI ·` di human.log dan hasil Copy (bukti penuh — aturan 0.4.5 tetap); dasbor merender blok show/hide (jsonbox) per baris ber-isi; batas isi 16 KB/event dengan catatan jujur (bukan pemangkasan diam-diam). Tingkat penting/TEKNIS baris TIDAK berubah (tidak ada banjir baru).

**Yang berubah di 0.5.6:** `HumanLog` (+`evIsi`/`evTechIsi`/`capIsi`/`ISI_CAP`, JSON `isiLabel`/`isi`, blok ISI human.log); `GameServer` (isiFile/isTextExt; 📄 & 📦 & ⇄ & 404/502 bawa isi; buffer passthrough diperluas ke teks ≤512 KB); `SdkApi` (⇄ POST qsdk bawa jawaban asli); `dashboard.html` (blok ▸ show/hide + Copy membawa ISI); `StatusStore` 0.5.6; `build.sh` → APK-LSF-0.5.6.apk (0.5.5+0.5.6 tergabung, dasar 0.5.4).

### 2.25 LOG ITU NYAWA — TANPA SINGKATAN + PLAY ONLINE TERTANGKAP (0.6.0 — perintah owner: *"log itu adalah nyawa kita agar tidak tersesat!! dari awal saya tekankan tidak boleh ada singkatan di log. kalau memang log itu panjang ya wajib tampil dan juga log txt harus menyimpan"* + laporan: *"sudah klik play pada mode online dan itu gak muncul — tidak menangkap log dengan jelas"*)

**Akar laporan Play-ONLINE-buta (bukti log device 13:35):** daftar server ASLI passthrough **UTUH** (2110 entri, url = server game asli) → klien `io.connect` **LANGSUNG ke server asli** → server lokal buta (tidak satu baris pun). `config/game-upstream.txt` hanya dipakai mode LOCAL — itu sebabnya isian owner "gak muncul" di log ONLINE. Bukan config salah — blind spot desain.

**Yang berubah di 0.6.0:**
1. **Isi log SELALU UTUH** — `capIsi` tidak memangkas apa pun (dulu 16 KB + catatan); `isiFile` mirror = byte utuh; sid/sesi/query/url **tanpa elipsis** (`sid.substring(0,6)+"…"`, `shortId`, `ringkasQuery` 80 char, `joinParams` 400 char — semua dihapus); human.log + events.jsonl + dasbor menyimpan penuh.
2. **Play ONLINE tertangkap** — `LoginServer.rewriteServerListUrls` menulis-ulang url **setiap entri** daftar server ASLI → `http://127.0.0.1:8080` (regex tahan JSON polos & ter-escape — sebab lama "(url tidak terbaca)": pola `"url":"` gagal di respons ber-escape); relay game meneruskan ke **url ASLI per entri** (tabel `parseServerTable` serverId↔url). Parse gagal → passthrough utuh tanpa rewrite (perilaku lama aman).
3. **Relay :8080 aktif di SEMUA mode** — `resolveGameUpstream`: isian owner menang; mode ONLINE lanjut ke tabel daftar ASLI (serverId pilihan pemain dari paket loginGame/SaveUserEnterInfo/enterGame bila terbaca — bila tidak entri pertama); Monitor probe ikut resolusi sama; `UpResp.body` tidak lagi final.
4. **[📋] Daftar server = PENTING + ISI UTUH** — gerbang pertama/terakhir URL PENUH, jumlah url di-rewrite, respons persis yang diterima klien di blok ▸ ISI.
5. **[🎮] Relay game ber-isi utuh** — POST aksi + GET event membawa payload penuh di blok ISI (dulu hanya "Catatan: diteruskan UTUH" tanpa isi).
6. **Delta 0.5.9 dipulihkan manual ke source** (dari dex terbit + log device): pesan ❌ upstream minimal, probe Monitor/simpan upstream/SWITCH MODE/tombol melayang tanpa narasi, 🔑 passthrough 2 field.
7. **Harness +12 tes TAHAP 19** (gerbang URL penuh + ter-escape; tabel serverId↔url; rewrite semua entri + field lain utuh; upstream ONLINE pilihan pemain/entri pertama/tabel kosong) — **129 LULUS + 15 GAGAL LINGKUNGAN** (SQLite native sandbox — sama persis sejak 0.5.2, bukan regresi). Sign v1/v2/v3 LULUS; CRC classes.dex game & config endpoint identik dengan dasar.
8. `android.jar` build diganti platform resmi Google **API 35** (jar lama tanpa simbol API 23/26); `r8.jar` lama ternyata korup (isinya pesan rate-limit) → r8 **9.5.32** resmi Google Maven. Keduanya dibackup LFS di `tools/`.

`build-0.6.0.sh` → APK-LSF-0.6.0.apk (dasar 0.5.9, sha256 `0a8a321d…`).

---

### 2.26 SUB JUDUL JALUR PER BARIS + JEJAK ASLI HTTPCANARY (0.6.1 — laporan owner: *"sub judul pada log (Online / local) gk ada jelas bikin bingung"* + *"Log Emang saya campur Biar jelas current state yg jalan apa .. jelas jelas isi log anda yg ngawur"* + *"bkan masalah rekam aslinya dulu, kalau anda paham konsep httpcanary capturre atau log akan jelas"* + *"log dari online nanti sebagai bahan diskusi .. server local kita gak asal bikin nama : local, alamat; local"*)

**Prinsip owner yang ditegaskan:** timeline log campur ONLINE/LOCAL = **desain owner** (satu layar, current state terlihat) — yang salah adalah ISI barisnya. Log harus seperti HttpCanary: setiap interaksi jelas jalurnya, **nilai asli utuh per field** (contoh owner: `nama : budi, alamat : jakarta`), dan jejak online = bahan diskusi/diagnosis untuk menemukan cacat server local dan tahu harus kemana — server local dilarang mengarang nilai asal (`nama: local`).

**Yang berubah di 0.6.1:**
1. **Sub judul jalur per baris** — field `"m"` (`local`/`online`) sudah di-stamp HumanLog sejak 0.5.4, tapi `renderRows` dasbor **membuangnya**. Kini: badge **[LOCAL]** (hijau) / **[ONLINE]** (kuning) di depan setiap kalimat (warna = badge kepala), baris **pertama dropdown rincian** = `Jalur : …`, dan hasil Copy ikut format human.log `[ICON|JALUR]`.
2. **human.log txt membawa tag jalur** — kepala tiap baris kini `[✅|LOCAL]` / `[📡·|ONLINE]` (dulu hanya JSON yang bawa `"m"`; janji komentar 0.5.4 "dasbor & human.log bisa membedakan" baru benar-benar ditepati sekarang).
3. **Jejak ASLI ditepati (akar "isi log ngawur" 0.6.0)** — dulu: Capture arah ← + blok ISI baris 📋 memakai teks **HASIL tulis-ulang**, padahal komentarnya mengaku "merekam respons ASLI" → nilai asli hilang dari jejak (Gerbang tampil `127.0.0.1:8080` dua kali). Kini: teks ASLI **sebelum** tulis-ulang disimpan khusus (`respAsli`/`asliLen`) — REKAM + blok ISI menampilkan **respons ASLI utuh dari login.popoh5.com:610** (nilai asli apa adanya = bahan diskusi/diagnosis), dan field **Routing** TERPISAH menyatakan apa yang diterima klien (url = relay lokal → diteruskan ke url ASLI per entri) dengan tegasan: **tulis-ulang = routing, BUKAN sumber config**.
4. **Semua baris relay nyebut jalur** — 📡 tersambung (ONLINE + versi LOCAL kini "— jalur LOCAL", istilah "hasil rewrite setting bin" yang kadaluarsa diganti "paket MANUAL config-offline"), ✅ loginGame, 🔌 tutup sesi, 🎮 Play relay, ⚠️ upstream tidak terjangkau — semuanya membawa field `Jalur :`; ❌ Play berhenti kini beda pesan per jalur (LOCAL = isian manual kosong + Solusi mengisi `config/game-upstream.txt`; ONLINE = tabel daftar server ASLI masih kosong).
5. **Versi dasbor** naik 0.5.6 → 0.6.1 (`StatusStore.VERSION` — dulu tertinggal tiga versi).
6. **Audit nilai asal-asalan (paket manual)** — nilai `127.0.0.1` di config-offline = keputusan manual sah (routing, tertulis di `manifest.txt`). Satu nilai masih menunggu telusur jejak: `"name":"Server Lokal"` di `servers.json` — nama asli server harus dibaca dari jejak ASLI (0.6.1 sekarang merekam daftar server ASLI utuh di human.log; begitu jejak terkumpul, nama diisi manual oleh owner — TIDAK dikarang).
7. **Harness +3 tes TAHAP 20** (tag `[✅|LOCAL]` / `[ℹ️·|ONLINE]` di human.log txt + rincian ikut utuh) — **132 LULUS + 15 GAGAL LINGKUNGAN** (SQLite native sandbox — identik dengan tree 0.6.0 bersih yang diuji ulang 129 LULUS + 15 GAGAL; nol regresi). Sintaks dasbor `node --check` LULUS. Verifikasi dex build kini python byte-count (`strings` memangkas baris ber-em-dash): 7 marker baru ≥1, 4 narasi lama = 0, CRC `config.properties`/`config_BS.properties` identik dasar, sign v1/v2/v3 LULUS (keystore `faf4b71d…`).

`build-0.6.1.sh` → APK-LSF-0.6.1.apk (dasar 0.5.9, sha256 `7dbe004c…`).

---


## 3. Konsep Solusi (v2.2): APK LSF — Custom dari DB-LOCAL.apk + Server Embedded

**Satu sumber, satu APK.** `DB-LOCAL.apk` di-decompile → **ditambah local server**
(service di dalam aplikasi) → rebuild dengan **nama paket tetap `com.db.local`** —
custom APK menggantikan original di device (signature berbeda → uninstall original
dulu; lihat §6). "Bersebelahan" berarti: (1) game & local server hidup berdampingan
**di dalam satu aplikasi** — bukan dua aplikasi terpisah; (2) local server hidup
**bersebelahan dengan server asli (remote)** yang tetap melayani game seperti biasa —
tidak ada yang digantikan sampai saatnya tiba.

```
┌── Device Android ────────────────────────────────────────────────────────────┐
│                                                                              │
│  ┌─ SATU APK CUSTOM — paket tetap com.db.local ─────────────────────────────┐│
│  │                                                                          ││
│  │   GAME CLIENT                              LOCAL SERVER (ditambah)       ││
│  │   ★ UTAMA — running online ★               port 4000                     ││
│  │   • tidak diubah sama sekali               • M1 pajangan → M2 monitor    ││
│  │   • pengalaman persis seperti aslinya        → M3 ambil alih bertahap   ││
│  │          │                                 • Web Dashboard + monitoring  ││
│  │          │                                 • start Application.onCreate  ││
│  │          │  BERSEBELAHAN (satu aplikasi)     → PASTI jalan duluan        ││
│  │          └──────────────────────────────────────┘                        ││
│  │                                                                          ││
│  │   Root folder: Android/data/com.db.local/server/                         ││
│  └──────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  Browser (device/PC via adb) ──► http://127.0.0.1:4000 → Web Dashboard       │
└────────────────────────────────────────────────────────────────────────────────┘
            │ traffic game (M1: langsung ke server asli · M2: lewat passthrough)
            ▼
┌─ SERVER ASLI (remote) — tetap melayani game seperti biasa, tidak diubah ─────┐
│  dragon / configus / COS / qsdk — sumber data utama sampai paritas terbukti  │
└───────────────────────────────────────────────────────────────────────────────┘
```

**Kenapa pendekatan ini (v2.2):**

1. **Tanpa rename, tanpa proyek baru** — paket tetap `com.db.local` menghindari risiko
   bug resource/manifest akibat rename; APK hasil edit = pengganti setia original.
2. **Tidak ada URL-intersep** — server tinggal di dalam APK yang kita pegang; saat
   waktunya tiba, endpoint tinggal di-repoint di file config milik APK sendiri.
3. **Urutan start otomatis terjamin** — server dinyalakan di `Application.onCreate`
   sebelum activity game dimuat; tidak mungkin game "kelebihan" server.
4. **Yang utama tetap online** — game berperilaku persis aslinya; baseline `DB-LOCAL.apk`
   di repo immutable sebagai pembanding/oracle.
5. **Server = "pajangan tapi work"** di awal — hidup, dashboard jalan, terbukti setara;
   repointing endpoint baru dilakukan setelah paritas terbukti.
6. **Monitor dulu, ambil alih kemudian** — mode passthrough (M2) merekam respons ASLI
   dari server asli; paritas dibangun dari data nyata, bukan tebakan.

**Tiga mode local server — progresif, satu arah (v2.2):**

| Mode | Apa yang terjadi | Posisi game & server asli | Fase |
|---|---|---|---|
| **M1 — Pajangan** | Server lokal hidup, dashboard + monitoring aktif. Traffic game **tidak disentuh** | Game online normal **langsung ke server asli** | L2–L4 |
| **M2 — Monitor (passthrough)** | Endpoint di-repoint → server lokal **meneruskan** request ke server asli → jawaban ASLI kembali ke game; seluruh lalu-lintas dicatat dua arah | Game tetap online dengan **data asli**; server asli tetap bekerja seperti biasa | L5a |
| **M3 — Ambil alih bertahap** | Per-endpoint yang paritasnya terbukti: jawaban diganti lokal; sisanya tetap passthrough | Game hidup dari server lokal, endpoint demi endpoint | L5b → L6 |

**Kenapa M2 penting:** kita **melihat format respons asli** langsung dari server asli —
tidak perlu menebak. Oracle terbaik untuk membangun paritas, tanpa mengubah pengalaman
online sama sekali.

**Web Dashboard di port 4000 (alat bantu — mission control kalibrasi):**

| Fitur | Isi |
|---|---|
| **Logging** | Semua request tercetak **dalam bahasa manusia yang mudah dibaca** (format di bawah) — live stream di dasbor + file `logs/human.log`; polling dasbor tidak dicatat. Baris placeholder "memuat…" **tidak pernah tersisa** di kotak log (dibuang otomatis saat jawaban pertama masuk — koreksi owner 0.3.2); bila dasbor gagal mengambil data, pesan kesalahan jelas tampil, bukan teks memuat menggantung |
| **Monitoring** | Kesehatan **dua sisi** dalam bahasa manusia: server lokal (hidup/uptime/jumlah request/gagal) + **server asli** (dicek berkala: bisa dihubungi / mati, latensi) — tanpa menyentuh traffic game |
| **Bukti local running** | Tabel **5 permintaan game terakhir** — URL asli yang diminta client + status HTTP + durasi; tab **Isi www/** = penjelajah mirror (nama file + ukuran, 31 file) |
| **Mode server (baca-saja)** | Badge `MODE LOCAL` / `MODE ONLINE` di header — **informasi saja, TANPA tombol**. Ganti mode = tombol melayang di layar game (koreksi owner 0.3.2: "website pindah mode itu salah cuma menuhin tempat") |
| **Copy / bersihkan log** | Tombol `Copy log` (salin semua baris) & `Bersihkan log dasbor` (memori dasbor dibersihkan; file `logs/human.log` tetap tersimpan) |

**Posisi logging & dashboard:** **alat bantu**, bukan produk. Log = indikator bahwa
(1) server lokal running dan (2) jawabannya mirip server asli. Jangan gold-plate —
produk utama = **server lokal yang setara**.

**Format log dashboard — WAJIB bahasa manusia (v2.1, diperhatikan dengan teliti):**

Setiap baris log ditulis seperti kalimat yang bisa dipahami siapa pun — siapa meminta
apa, server menjawab apa, seberapa lama. Detail teknis (header, body raw, curl) tetap
lengkap tapi ditaruh di balik expand + tombol copy (copy-first tetap berlaku).

```
09:05:57  ✔ Folder server siap: /storage/emulated/0/Android/data/com.db.local/server (penyimpanan eksternal)
09:05:57  ℹ Mode: LOCAL — permintaan game dilayani dari www/ (mirror server asli)
09:05:58  ◀ GET http://127.0.0.1:8080/bs/db/android/setting_BS_Android.bin → 200 dari www/ · 693 B · 1 ms — host di dalam bin ditulis-ulang ke http://127.0.0.1:8080
09:05:58  ℹ GET http://127.0.0.1:8080/bs/db/android/com_db_local.bin → 404 · tidak ada di www/ (di server asli juga tidak ada) · 0 ms — game melanjutkan boot (perilaku normal)
09:05:58  ◀ GET http://127.0.0.1:8080/bs/upgrade/all.zip → 200 dari www/ · 18,5 MB · 2 ms
09:05:58  ◀ GET http://127.0.0.1:8080/bs/upgrade/base.version → HTTP 200 dari server asli · diteruskan ke https://dragonh5cdn.popoh5.com/bs/upgrade/base.version · 4 B · 214 ms   (mode ONLINE)
```

Aturan penulisan log:
1. **Satu event = satu kalimat manusia** — "siapa minta apa → jawab apa · berapa lama".
2. **Baris request memuat URL ASLI yang diminta client** (`http://127.0.0.1:8080/…`) —
   koreksi owner 0.3.1: TIDAK ada lagi label `LOCAL`/`ONLINE` di baris request (mode
   cukup diceritakan di baris status saat boot); mode ONLINE menambahkan keterangan
   `diteruskan ke <server asli>` di baris yang sama.
3. **Anti-banjir (0.3.1):** request kembar (status+path sama) dalam jendela 10 detik
   tidak dicatat ulang — saat jendela lewat, jumlahnya dilaporkan sekali
   (`· (+N request kembar dalam 10 dtk tidak dicatat)`); polling dasbor tetap tidak
   dicatat; log "tombol melayang tampil" hanya SEKALI per proses (game Egret me-
   pause/resume activity beberapa kali saat boot — dulu lognya berulang 6×).
3b. **Narasi mode dirampingkan (0.3.2):** baris boot singkat satu kalimat —
   `Mode: LOCAL — permintaan game dilayani dari www/ (mirror server asli)` ·
   `Port game hidup di 8080 (mode LOCAL) — endpoint client menunjuk ke sini` ·
   `Tombol melayang tampil — GESER = pindah posisi · TEKAN = ganti mode`.
   Tanpa iklan "halaman 8080 berisi tombol ganti mode" (halaman itu sudah tidak ada).
4. 404 normal tetap tenang: `com_db_local.bin` & kawan-kawan dilog `ℹ` dengan
   keterangan "di server asli juga tidak ada — perilaku normal"; hanya setting bin
   yang hilang dari mirror yang dilog `⚠` (itu benar-benar perlu perhatian).
5. Simbol visual konsisten: `▶` permintaan masuk · `◀` jawaban keluar · `⚠` perlu
   perhatian · `✔` sehat/selesai.
6. Angka ramah manusia: waktu lokal, ukuran dalam KB, durasi dalam ms.
7. Baris `⚠` wajib menjelaskan **apa yang kurang** dan **langkah berikutnya**
   (mis. "belum ada handler → buat mock dari dashboard").
8. Panel status di atas dashboard juga kalimat manusia, mis.:
   `Server lokal HIDUP sejak 14:30 · sudah menjawab 37 permintaan · 0 gagal · port 4000`.

**Root folder server — ANTI-SAMPAH (v3.0, aturan keras):**

```
Android/data/com.db.local/server/          ← HANYA 4 entri ini, tidak boleh lain
├── config/
│   ├── server.json                        ← mode (local/online), gamePort, debugPort,
│   │                                        toggleX/toggleY (posisi terakhir tombol melayang)
│   ├── www.stamp                          ← penanda versi mirror (bukan di dalam www/!)
│   └── status.json                        ← snapshot status (alat bantu luar dasbor)
├── logs/                                  ← human.log + events.jsonl (ring 800, rotasi 4 MB)
├── tmp/                                   ← DIKOSONGKAN setiap boot
└── www/                                   ← MIRROR PENUH SERVER ASLI — ekstraksi OTOMATIS dari APK
    ├── bs/db/android/setting_BS_Android.bin
    ├── bs/index-native.html · manifest.json · js/ (15 file) · resource/properties/ (2 json)
    ├── bs/upgrade/ (base.version, resource.version, upgrade.json, size.json,
    │               base.zip, all.zip, 11386–11389_11390.zip)
    └── lzceshi/native_setting/Android/setting_BS_Android.bin
```

**Pembersih whitelist (`Cleaner.java`, v3.0):** setiap boot, apa pun di dalam `server/` di luar
4 entri di atas — dan di dalam `config/` di luar 3 file di atas — DIHAPUS otomatis (jumlah +
ukuran tercatat di log). Ini menyapu sampah peninggalan build lama tanpa langkah manual.
Sumber sampah lama dihilangkan dari akarnya: `ApkReader` (dan endpoint `/api/apk/*`,
`/api/analyze`, `/api/monitor/run`) DIHAPUS dari server — tidak ada lagi pembacaan/cadangan
APK yang menumpuk.

**Mirror & ekstraksi otomatis (v3.0):** seluruh isi fisik server asli (31 file — daftar §2.9)
dibawa di dalam APK (`assets/server/www/`) dan diekstrak otomatis `Payload.java` → `www/`
sebelum server menyala (penanda versi = `config/www.stamp`; www/ dihapus / clear data →
terisi ulang otomatis). Game TETAP membaca via HTTP dari port game lokal — assets hanya
gudang, BUKAN jalur baca game. Mode LOCAL menyajikan mirror apa adanya KECUALI setting
bin yang di-rewrite hostnya (lihat bawah). File yang diminta game tapi belum ada di mirror
dicoba diambil SEKALI dari server asli lalu DISIMPAN ke www/ (mirror melengkapi dirinya
sendiri); bila server asli juga tidak memberikannya → 404 dan game melanjutkan boot
(fallback dex terbukti §2.8: `Server base.version is Not Exist`).

**Rewrite sisi server (v3.0 — BUKAN edit client):** satu-satunya konten yang berubah saat
disajikan di mode LOCAL = field `url` + `update` di setting bin (`https://dragonh5cdn.popoh5.com`
→ `http://127.0.0.1:8080`) — dekripsi XOR `DragonBall` → ganti host → enkripsi ulang
(`SettingBin.java`). `tsUrl` (varian uji), `loginServer`, `tsLoginServer` TIDAK disentuh
(fase login berikutnya). Mode ONLINE menyajikan file asli tanpa perubahan apa pun.

**Dua port, dua peran (v2.7):**

| Port | Untuk siapa | Fungsi |
|---|---|---|
| **4000** (debugPort) | Developer | Dasbor debug + log + pantauan — game tidak tahu port ini |
| **8080** (gamePort) | Game client | Melayani kontrak server: LOCAL = www/, ONLINE = passthrough |

**Switch mode (v3.0 — tombol melayang BISA DIGESER; sejak v3.2 SATU-satunya tempat):**
bulatan overlay (`L`/`O`, warna mengikuti mode) di atas game.
- **GESER**: ikut jari, dibatasi tetap di dalam layar; lepas → posisi tersimpan ke
  `server.json` (`toggleX`/`toggleY`) dan dipulihkan saat tampil lagi. Geser vs tekan
  dibedakan lewat ambang touch-slop Android.
- **TEKAN**: panel mode muncul di dekat bulatan → tombol "Ganti ke X + restart aplikasi"
  dengan **konfirmasi dua-tahap** (tekan sekali → "tekan SEKALI LAGI untuk restart") →
  mode ditulis ke `config/server.json` → aplikasi direstart via AlarmManager
  (`RestartHelper`) → BootProvider menyalakan server lagi → game membaca config sesuai
  mode baru. Panel juga punya "Tutup".
- **Hanya tampil saat game di depan** — dipasang/lepas lewat `ActivityLifecycleCallbacks`
  (resumed/paused), tanpa mengubah kode game sama sekali.
- Izin overlay (SYSTEM_ALERT_WINDOW) sudah ada di manifest original; di Android 6+ pemilik
  mengaktifkannya sekali di Setelan → “Tampil di atas aplikasi lain”.
- **Website TANPA switch mode (0.3.2 — koreksi owner "website pindah mode itu salah cuma
  menuhin tempat"):** switch yang di 0.3.1 ditaruh di dua halaman web (dasbor 4000 +
  halaman port game 8080) **DIHAPUS semua** — dinilai owner hanya menempati tempat.
  Dasbor 4000 kini menampilkan mode sebagai **badge baca-saja** di header; halaman root
  `http://127.0.0.1:8080` di browser menjadi **status mini 3 baris tanpa tombol/JS**
  (bukti port hidup + penunjuk dasbor & tombol melayang). Endpoint `POST /api/mode`
  ikut dihapus dari kedua port (tidak ada lagi pemakainya).

Keuntungan: mudah di-backup/diintip dari PC (USB), terhapus bersih saat uninstall,
tanpa izin storage tambahan (folder own-app).

---

## 4. Rencana Tahap Fondasi (v2.2 — Track L: Custom APK LSF)

> Setiap fase punya keluaran (deliverable) & kriteria selesai. Tidak ada fase gameplay di sini.
> Semua pekerjaan dilakukan pada **salinan** APK; `DB-LOCAL.apk` di repo = baseline immutable.

#### Fase L0 — Toolchain & Custom Dasar (round-trip) — **STATUS: build ✓ · uji device ⏳**
- [x] Siapkan toolchain: `apktool` 3.0.3, `uber-apk-signer` 1.3.0 (zipalign + sign
      v1/v2/v3), JDK 21; keystore sendiri dibuat (`keystore/lsf.keystore` — lihat
      *Riwayat Build & Artefak* di akhir §4).
- [x] Uji **round-trip** (level build): decompile `DB-LOCAL.apk` → satu-satunya
      perubahan = `versionName: 1.0.0 → 0.1.0` → rebuild → zipalign → sign →
      **terverifikasi penuh** (detail: *Riwayat Build & Artefak* di akhir §4).
- [ ] Uji di device: install → boot → pastikan game **persis seperti aslinya**
      (masih online, tak ada perilaku baru). *Menunggu owner — langkah ada di §4 Riwayat Build.*
- **Deliverable:** `releases/APK-LSF-0.1.0.apk` (custom dasar tanpa server, tersimpan via LFS).
- **Kriteria selesai:** APK LSF terinstall menggantikan original (paket sama) dan
      boot normal; tidak ada regresi vs baseline. *(Menunggu konfirmasi device owner.)*

#### Fase L1 — Rekonstruksi Kontrak Bootstrap (dari sisi client)
- [ ] Analisis dex/smali + runtime: apa yang client **harapkan** dari
      `/lzceshi/native_setting/Android` dan `/bs/db/android` (format, field, encoding)?
- [ ] Verifikasi: endpoint mana yang benar-benar dipanggil client, **HTTP atau HTTPS**?
- [ ] Catat urutan request saat boot (logcat + capture) — original & LSF dipakai sebagai oracle.
- [ ] Fallback jika terbentur enkripsi payload: riset `onepieceencrypts`/`libgavesec.so`
      (masih ranah fondasi/protokol, bukan gameplay).
- **Deliverable:** spesifikasi kontrak bootstrap → ditulis menggantikan bagian
      "endpoint game sesungguhnya" di §2.3.
- **Kriteria selesai:** format request/response endpoint bootstrap jelas.

#### Fase L2 — Embed Local Server ke APK LSF (port 4000)
- [ ] Injeksi kode server (kandidat: **NanoHTTPD** — murni Java) sebagai service
      di dalam APK LSF; dipanggil dari `Application.onCreate` → **server pasti jalan duluan**.
- [ ] **Root folder:** `Android/data/com.db.local/server/` (logs/, config/, data/, responses/).
- [ ] Route stub untuk endpoint bootstrap (hasil L1); SQLite untuk log + state.
- [ ] Server listen `127.0.0.1:4000` (tidak terekspos jaringan luar).
- **Deliverable:** `APK-LSF-1.apk` (game + server hidup di dalam satu aplikasi).
- **Kriteria selesai:** dari device, `curl http://127.0.0.1:4000/bs/db/android`
      mengembalikan respons sesuai kontrak Fase L1; log muncul di root folder.

#### Fase L3 — Web Dashboard ("pajangan yang work")
> *Alat bantu — cukup lengkap, jangan gold-plate. Log = indikator server hidup & setara.*
- [ ] **Logging:** capture semua request → SQLite + live stream ke browser;
      **setiap baris log dalam bahasa manusia** (format §3), detail mentah di balik expand.
- [ ] **Monitoring:** panel kesehatan dua sisi (server lokal + server asli) — server
      asli dicek berkala & ditampilkan dalam bahasa manusia (mis.
      "Server asli dragon…: MATI — sudah tidak bisa dihubungi").
- [ ] **Debugging:** detail viewer, replay, mock override per-endpoint (edit + toggle).
- [ ] **APK Reader:** tree assets, manifest, `config.properties` + decode Base64 otomatis,
      grep strings/dex untuk endpoint.
- [ ] **Copy-first:** tombol salin satu klik — raw body, headers, `curl` siap-replay,
      config ter-decode, timeline alur per sesi boot.
- **Deliverable:** dashboard hidup di `http://127.0.0.1:4000` (browser device,
      atau PC via `adb forward tcp:4000 tcp:4000`).
- **Kriteria selesai:** satu siklus kalibrasi bisa dijalankan penuh dari dashboard
      (lihat request → edit mock → replay → lihat hasil) tanpa tools eksternal; log
      dashboard terbaca manusia biasa (non-programmer paham apa yang terjadi).

#### Fase L4 — Validasi Bersebelahan (M1: game online via server asli, server lokal pajangan)
- [ ] Install APK LSF di device uji (paket sama dengan original → menggantikan;
      baseline `DB-LOCAL.apk` tetap utuh di repo sebagai pembanding).
- [ ] Buka **APK LSF** → game berjalan normal seperti aslinya (masih online),
      DAN server lokal hidup otomatis bersebelahan (terbukti via dashboard + log root folder).
- [ ] Bandingkan perilaku boot dengan baseline (uji bergantian di device yang sama)
      → pastikan injeksi server **tidak mengubah** perilaku game sama sekali (pure addition).
- **Deliverable:** catatan uji perbandingan + bukti server hidup (screenshot/log).
- **Kriteria selesai:** game = utama, online normal; server = "pajangan yang work"
      di port 4000 tanpa mengganggu game.

> ── Batas fondasi ── Fase berikut dieksekusi setelah L0–L4 lulus & owner setuju ──

#### Fase L5a — Repointing + Mode Monitor/Passthrough (game tetap online, data asli)
- [ ] Ubah endpoint di `assets/config*.properties` **milik APK LSF**
      (Base64 → `http://127.0.0.1:4000/...`); baseline `DB-LOCAL.apk` di repo tetap tidak disentuh.
- [ ] Server lokal = **penyambung transparan**: terima request game → teruskan ke
      server asli → kembalikan jawaban ASLI tanpa diubah → catat dua arah.
- [ ] Dashboard kini memperlihatkan **format respons asli** dari server asli
      (bukan tebakan) → di-copy untuk membangun template paritas.
- **Deliverable:** `APK-LSF-2a.apk` + rekaman traffic asli lengkap per sesi boot.
- **Kriteria selesai:** game bermain online seperti biasa (lewat passthrough) dan
      seluruh aliran request/response tercatat di dashboard.

#### Fase L5b — Ambil Alih Bertahap (per-endpoint, kalibrasi)
- [ ] Endpoint demi endpoint: template jawaban lokal dibangun dari rekaman asli (L5a)
      → mock diaktifkan → client lolos → endpoint itu "berpindah" ke lokal; sisanya
      tetap passthrough.
- [ ] Oracle loop: amati di dashboard → perbaiki template → replay → ulangi sampai
      client lolos rantai bootstrap & API berikutnya.
- [ ] Setiap kemajuan dicatat di §2.3 (endpoint + format yang sudah "diterima" client).
- **Deliverable:** `APK-LSF-2b.apk` + log kalibrasi.
- **Kriteria selesai:** game di APK LSF lolos seluruh rantai bootstrap dari server lokal
      (dicatat sebatas protokol, bukan gameplay-nya).

#### Fase L6 — Hardening & Standalone (APK LSF)
- [ ] Stub SDK cloud di LSF bila mengganggu (Billing/GMS/FB/Adjust) → no-op aman.
- [ ] Persistensi state (SQLite solid); uji reboot, kill/restart, **mode pesawat penuh**.
- **Deliverable:** `APK-LSF-3.apk` (standalone) + catatan uji di dokumen ini.
- **Kriteria selesai:** APK LSF berdiri sendiri tanpa internet; server asli tidak lagi dibutuhkan.

---

### Riwayat Build & Artefak (log eksekusi — sumber kebenaran hasil build)

**Skema versi build LSF** (versionCode LSF = nomor urut rilis LSF sendiri):

| Versi | Isi | Fase |
|---|---|---|
| **0.5.6** | **(v5.6) CAPTURE ISI DI DASBOR :4000 — SETIAP KOMUNIKASI BAWA ISI KOMUNIKASINYA** (arahan owner *"capture nya ya di port 4000 .. github live preview cuma sebagai contoh tampilan"* — §2.24): `HumanLog.evIsi/evTechIsi` + `capIsi` (16 KB, potongan = catatan jujur) — field `"isiLabel"/"isi"` di events.jsonl + memori dasbor + blok `▸ ISI ·` di human.log & Copy; **LOCAL**: 📄 file mirror = byte persis (teks ≤16 KB/binary = catatan jujur), 📦 setting bin = bedah field-per-field + teks didekode, serversetting = bedah + teks; **ONLINE**: config & teks ≤512 KB dibuffer — ⇄ bawa isi persis yang disajikan, setting bin = bedah ASLI→klien, qsdk passthrough = **jawaban asli server**, binary/besar = catatan jujur, 404/502 = isi jawaban; dasbor: blok ▸ show/hide per baris ber-isi + Copy membawa ISI; tingkat penting/TEKNIS tidak berubah (tanpa banjir); build.sh → **APK-LSF-0.5.6.apk** (0.5.5+0.5.6 tergabung, dasar 0.5.4) | **L7 ✓ (capture HttpCanary hidup di dasbor — isi pada barisnya)** |
| **0.5.5** | **(v5.5) UTAMANYA LOCAL — MODE BAWAAN SAAT INSTALL & BUKA = LOCAL** (arahan owner *"kalau sudah paham implementasi rebuild apk, jika sebelumnya defaultnya saat aplikasi di install dan di buka adalah mode online, sekarang tahap ini saat apk di install dan di buka adalah Utamanya Local"* — §2.23): `Config.java` — field bawaan + `DEFAULT_JSON` (server.json yang ditulis saat boot pertama) kini `"mode": "local"` (dulu `"online"`) + teks pesan ⚠️ bawaan menyebut LOCAL; `StatusStore.java` fallback mode `local`; **server.json pemilik yang sudah ada tetap dihormati** (pilihan ONLINE tidak dipaksa balik LOCAL); jalur/protokol/log tidak berubah — ONLINE tetap tersedia via tombol melayang; build.sh: BASE 0.5.4 → keluar **APK-LSF-0.5.5.apk** | **L7 ✓ (install & buka = utamanya Local)** |
| **0.5.1** | **(v5.1) + CONFIG LOGIN SEMUA MODE — BUKAN REKAM JEJAK** (tegasan owner *"SAYA TEGASKAN LAGI CONFIG YG JELAS BUKAN REKAM JEJAAAAK"*; laporan *"config Setelah SDK login ini masuk ke loginserver sampai detik ini anda ngeyel"* + *"saya sudah pilih server lalu klik play dan log hanya tampil itu"* — jawaban atas bisunya alur login ONLINE, §2.19): **klien TIDAK PERNAH lagi langsung ke login.popoh5.com di mode mana pun** — `SettingBin.rewrittenOnline` (mode ONLINE: bin dari `configus`/COS ditulis-ulang HANYA jalur login + url H5 → server lokal; `tsUrl` tetap asli persis gaya LOCAL; TANPA `userid` di url — identitas boot = mekanisme shell asli); `GameServer.servePassthrough` menulis-ulang setting bin + `serversetting.json` (loginserver → `http://127.0.0.1:8610`) SEBELUM disajikan + catatan grup "endpoint login ditulis-ulang ke server lokal"; `LoginServer.proxyOnline` — socket.io **diteruskan UTUH byte-per-byte** ke `https://login.popoh5.com:610` (probe 2026-11-05: **HTTPS wajib** — HTTP polos ditolak `400 plain HTTP request sent to HTTPS port`; TLSv1.2 DigiCert `*.popoh5.com`; websocket dijawab 400 → engine.io client tetap polling — perilaku terbukti device); `SdkApi` loginchecknative = **jalur BAKU** ke `https://login.popoh5.com:510` + baris 🔑 *Sesi disahkan via SERVER ASLI (passthrough)* (dedupe 10 mnt/uid); **log alur ONLINE kini hidup**: 📡 *Klien tersambung — diteruskan ke SERVER ASLI* → ✅ *Login game diteruskan ke server ASLI* → 📋 *Daftar server ASLI terkirim — N server (passthrough; dihitung dari payload, bukan snapshot)* → 🔌 *pindah ke server game* (dedupe = gaya LOCAL; REKAM lapisan login ONLINE kini berisi payload ASLI `loginGame`/`GetServerList` — pembanding utama 0.6); dasbor: modemap + kartu "Ke mana koneksi mengarah" = **semua mode**; baris 🚀 Mode ONLINE diperbarui; `StatusStore` 0.5.1; token & identitas tetap dikeluarkan SERVER ASLI — progres pemain tidak berubah; passthrough gagal → ⚠️ + 502 (gagal = sama seperti langsung gagal, tanpa regresi); **uji asap HIDUP**: handshake relayed memuat sid ASLI upstream; urutan langsung vs lewat proxy = jawaban identik (byte-per-byte); harness **96/96 LULUS** (+16 tes TAHAP 16) | **L5b ✓ (config login semua mode — pembanding asli-vs-lokal hidup)** |
| **0.5.2** | **(v5.2) + AKAR CONFIG — ISI CONFIG DIBEDAH DI LOG** (tegasan owner *"pakai config online murni untuk server lokal, padahal akarnya aja gak tau .. Saya sudah TEGASKAN log yg jelas rinci itu penting"* — log 0.5.1 hanya MENGATAKAN "endpoint ditulis-ulang" tanpa menunjukkan isinya, §2.20): **`ConfigAkar.java` BARU** — pembedah config field-per-field; setiap config yang disajikan (setting bin `bs/db/android` + `lzceshi` + `serversetting.json`, mode LOCAL & ONLINE) kini di-log 📦 *AKAR CONFIG … dibedah field-per-field* **PENTING** (selalu tampil): nilai **ASLI server → nilai yang diterima klien** utuh tanpa singkatan + peran tiap field (`testMode`, `url`, `tsUrl`, `update`, `checkTime`, `loginServer`, `tsLoginServer`, `loginPort`, `loginWebPort`, `clientParams` — anatomi penuh README §2.20); `SettingBin` +`getJsonRaw` (nilai mentah angka/string/bool) + `decryptedPlain(File/byte[])`; `GameServer.logAkar` dedupe per isi (config sama beruntun → TEKNIS, tetap terekam human.log); rincian alur login ONLINE diperkaya — ✅ loginGame kini +**Akun/Sandi (panjang, diteruskan UTUH)**, 📋 daftar server ASLI kini +**Gerbang pertama … terakhir** (`firstAndLastGateway`) — sebelum Play ditekan log sudah menunjukkan gerbang tujuan; ikon 📦 masuk legenda 0.4.9; StatusStore 0.5.2; harness +24 tes TAHAP 17 — **129 tes: 105 LULUS + 15 GAGAL LINGKUNGAN** (SQLite native sandbox, sama di HEAD — bukan regresi; 0.5.2 LULUS 24/24); APK-LSF-0.5.2.apk | **L5b ✓ (akar config terbuka di log)** |
| **0.5.4** | **(v5.4) + LOG DUA JALUR + RELAY SERVER GAME** (laporan owner *"log campur adukk, padahal di posisi original online tapi lognya local malah ikut .. log isi config di ikutkan ngapain di pisah, kalau isi config besar ya bisa show hide .. kalau sedang local log ya isinya local sesuai lah .. kalai log Online ya log aslinya seperti httpcanary .. udah log gak jelas, sekarang cuma loading muter2 gk ada kejelasan di log"* — §2.22): **tag mode per baris log** (HumanLog `"m"`); baris boot jalur lain dihapus dari emit — 💾 config OFFLINE hanya di LOCAL, **REKAM dihapus TOTAL** (Capture no-op, tanpa capture/, /api/rekam\* dihapus); **isi config keluar dari feed** → `ConfigView` + **seksi CONFIG dasbor show/hide** (bedah field-per-field + teks config), feed cuma SATU baris 📦 padat per file (dedupe 10 mnt); **ONLINE = gaya HttpCanary** `🌐 ⇄ GET <host><path> → HTTP 200 · 412 KB · 340 ms` (aset = TEKNIS) + `⇄ POST qsdk…` untuk SDK; **LOCAL = gaya lokal** `📄 GET … · mirror www/`; **RELAY SOCKET GAME :8080** — klik Play `io.connect(127.0.0.1:8080)` kini diteruskan utuh ke SERVER GAME ASLI (mesin passthrough login 0.5.1; websocket ditolak → polling), upstream = field `"upstream"` di config-offline/servers.json **atau** satu baris `config/game-upstream.txt` (tanpa build ulang); upstream kosong = baris `❌ Play berhenti … belum punya UPSTREAM` + cara mengisi (bukan loading diam); probe boot & tombol dasbor **"Tes game"** (`/api/probe/game`) = uji upstream (positif palsu 127.0.0.1:8080 dihapus); ConfigView.java baru; StatusStore 0.5.4; servers.json manual + `"upstream"` (manifest sha256 diperbarui) | **L7 ✓ (log = mode aktif; isi config show/hide; Play → relay upstream)** |
| **0.5.3** | **(v5.3) + DUA JALUR — CONFIG OFFLINE MANUAL / ONLINE PASSTHROUGH** (tegasan owner *"100% beda jalur!! offline dan online .. config offline murni disiapkan manual"* + *"online tebaca itu cuma jadikan jejak arah bukan untuk dijadikan config asli ke offline"*; plus keluhan *"kebanyakan tab semua di pisahkan malah bikin kacau"* — §2.21): **paket `config-offline/` MANUAL** (setting.json + bin XOR "DragonBall" yang menunjuk 127.0.0.1 sejak dibuat + serversetting + servers.json 1 entri "Server Lokal" url `http://127.0.0.1:8080` + manifest sha256) — dilayani **APA ADANYA** tanpa penulisan-ulang runtime, `trySeed` dihapus dari LOCAL (OFFLINE = nol sentuh jaringan luar); **GetServerList dari paket manual** → klik Play `io.connect(serverItem.url)` masuk SERVER GAME LOKAL (akar kasus "log diam setelah Play" ditutup — dibaca langsung dari `main.min`: `serverItem.url` = tujuan io.connect, bukan dari bin/serversetting); snapshot online DIHAPUS dari assets (`server/login/servers.json` 2109 entri, bin asli www/, serversetting www/) + dibuang dari APK saat build — online hanya jejak arah format/protokol; `LoginData.init(configDir, offlineDir)` + fallback darurat TETAP lokal (tidak pernah popoh5); `Cleaner` melindungi `config-offline/`; dasbor **SATU LAYAR** (REKAM dihapus dari UI — config bukan rekam; satu kolom log kronologis; jalur OFFLINE/ONLINE terlihat di kepala + garis config; bug pill "PUTUS" akibat elemen `e-login2`/`e-srv` hilang ikut dibereskan); `Payload` mirror v4 (www/ = resource H5 murni); `StatusStore` 0.5.3; harness +13 tes TAHAP 18 — **133 tes: 118 LULUS + 15 GAGAL LINGKUNGAN** (SQLite native sandbox — sama persis di HEAD, bukan regresi); build.sh: BASE 0.5.2, cermin config online dibuang dari APK, `config-offline/` disertakan; APK-LSF-0.5.3.apk (sha256 137f12b6…) | **L6 ✓ (OFFLINE murni manual — Play masuk lokal)** |
| **0.5.0** | **(v5.0) + REKAM — perekam payload mentah + CONFIG ASLI semua lapisan** (permintaan owner *"seperti capture HttpCanary tapi kita buat ini lebih canggih — HttpCanary itu cuma jejaknya, bukan config aslinya"*, §2.18): `Capture.java` — ring 600 + `capture/rekam.jsonl` (embed ≤32 KB, rotasi 8 MB) + `capture/payload/` (≤2 MB, maks 400 file); tap di LoginServer/SdkApi/GameServer/SettingBin (event/ack/handshake/ping/disconnect utuh; body SDK didekode base64+sign; setting bin DIDEKRIPSI; jawaban ASLI qsdk/login.popoh5 mode ONLINE terekam); baris 🔌 disconnect sesi; probe server game boot + tombol "Tes server game" (`/api/rekam/probe`); `server.json +"rekam"`; Cleaner whitelist +`capture/`; dasbor bagian REKAM (filter/payload utuh/Salin/Unduh/Bersihkan) | **L5b ✓ (alat tangkap semua lapisan)** |
| **0.4.9** | **(v4.9) + LOG EMOJI + BLOK RINCIAN PENUH** (umpan balik owner *"emoji sudah bagus, hanya saja masih kurang rinci"* + *"ingat gak boleh disingkat"* — setelah menyetujui contoh output, §2.17): **ikon emoji per kategori** menggantikan simbol tipis ✔/⚠/ℹ (🚀 siap · 👤 pemain · 🔑 sesi · 📡 koneksi · ✅ sukses · ⚠️ masalah · ❌ terhenti · 📋 daftar server · 💾 data · 🧹 mirror · ℹ️ info; `HumanLog` ikon `char` → `String`); **blok rincian ├─/└─ di SEMUA baris penting** (fakta nyata: ms, byte, uid, sid, sumber data, sebab, akibat/lanjutan) — tampil penuh & identik di dasbor, human.log, dan Copy (**TIDAK dipangkas**); alur sesi 09:39–09:41 kini: 🚀 Server lokal siap (rincian Port/Mode/Folder/Pemain) → 👤 Layar SDK (rincian Perangkat/Cara/Identitas) → 🔑 Sesi disahkan (rincian Uid/Hasil/Sesi) → 📡 Klien tersambung (dari TEKNIS naik ke penting, dedupe 10 mnt) → ✅ Login game sukses (rincian Akun/Proses/Sumber) → 📋 Daftar server terkirim (rincian Sumber/Ukuran/Lanjutan — baris ℹ TUNTAS 0.4.8 dilebur ke rincian, isi tidak hilang); 👤 layar SDK = SATU baris walau registerVisitor+autoLogin sama-sama terpanggil (`SdkData.noteSdkLogin` dedupe 10 mnt per uid); duplikat log daftar akun berdempet dihapus; boot 3 baris → SATU baris 🚀 ber-rincian; dasbor: teks diperbesar & kontras dinaikkan, kolom ikon emoji, rincian pohon tanpa pemenggalan; harness **80/80 LULUS** (1 redaksi detail ⚠️ disesuaikan) | **L5a–L5b ✓ (log emoji + rinci)** |
| **0.4.8** | **(v4.8) + LOG CERITA ALUR — SATU KEJADIAN SATU BARIS + PENJELAS TUNTAS + COPY RAPI** (laporan owner sesi 09:39–09:41 "tidak ada revisi!!!! log masih tidak jelas, padahal sampai loginserver munculkan serverlist.. tapi log cuma Sampah gak jelas", §2.16 — paste-nya = keluaran Copy): **satu kejadian satu baris** — percobaan login yang langsung selesai tidak diumumkan dua kali ("SDK: MASUK — …" + "SDK: MASUK gagal — …" berdempet 0 dtk dihapus), setiap baris membawa hasil+sebab(+akibat); **ikon ▶/◀ dihapus + awalan "SDK:" dibuang** (alur kini cerita: Login ditolak → Lanjut masuk sebagai tamu → Sesi disahkan → Daftar server terkirim → TUNTAS); **baris TUNTAS setelah GetServerList** (dedupe 10 mnt) — daftar = bawaan APK DISAJIKAN server lokal + penjelas kenapa log diam setelahnya (gameplay langsung ke server game asli); **format Copy dirapikan** (jam-ikon-teks berjarak, ringkasan grup berawalan Σ + jam, detail sebab TAMPIL kecil di dasbor & ikut disalin); baris toggle RINCI/BERSIH tidak dilog lagi; boot baris Mode dipangkas satu kalimat; harness **80/80 LULUS** | **L5a–L5b ✓ (log = cerita alur)** |
| **0.4.7** | **(v4.7) + LOG BAHASA MANUSIA: BOOT 3 BARIS + PEMETAAN ARAH KONEKSI** (laporan owner "login server muncul .. saya rasa isi config loginserver anda masih mengarah ke server aslinya.. log ini pun tidak membantu kurang efektif kurang jelas", §2.15): **BOOT 3 BARIS** — `✔ Server lokal siap — game :8080 · login :8610 · dasbor :4000` → `ℹ Mode LOCAL — <peta arah koneksi>` → `ℹ Pemain: … — identitas & sesi tersimpan lokal` (dulu 5-6 baris siap bertumpuk); **peta arah koneksi eksplisit** di baris Mode, di GetServerList ("snapshot asli bawaan APK"), dan di dasbor (modemap + kartu "Ke mana koneksi mengarah") — bukti rewrite login LOKAL tercatat §2.15-a; dedupe **loginchecknative OK 10 dtk → 10 menit** per uid; `dragonball.db siap`/`Tombol melayang tampil`/handshake socket → TEKNIS (tetap terekam human.log); bahasa log diperbaiki ("Pemain", "identitas & sesi tersimpan lokal", "perangkat → tamu"); harness **80/80 LULUS** | **L5a–L5b ✓ (log & peta koneksi)** |
| **0.4.6** | **(v4.6) + MODEL FINAL LOG — ALUR LINEAR + GRUP BATCH** (kesepakatan owner "group itu kalau terdeteksi emang config/resource yg banyak. kalau alur ya gk usah di group", §2.14): **LogGroups.java** — mirror CDN/request game/404 fallback/passthrough ONLINE TIDAK PERNAH jadi baris log linear; satu baris judul + **statistik hidup** di dasbor (diklik = rincian per-file, cap 600 item/grup), **gagal naik OTOMATIS sebagai ⚠ linear** (jeda 30 dtk), grup kosong tak tampil; **human.log = ringkasan Σ satu baris** per periode diam ±45 dtk ("Mirror CDN: 470 file · 84,2 MB · 0 gagal — rincian di dasbor"); API baru `GET /api/groups`; **DASBOR sesuai model**: panel status atas (PORT GAME/SERVER LOGIN/SESI PEMAIN) + alur linear + grup lipat di bawahnya + bukti teknis dilipat; Copy = alur + Σ per grup; dedupe **loginchecknative OK** ganda (uid sama <10 dtk = 1 baris); `HumanLog.writeRawLine` (Σ → file saja); harness **80/80 LULUS** (+9 tes grup) | **L5a–L5b ✓ (model final log disepakati owner)** |
| **0.4.5** | **(v4.5) + LOG BERSIH + DASBOR FOKUS** (laporan owner "jujur terus terang log anda bagi saya cuma sampah apalagi UI website banyak informasi yg tidak bermanfaat"): log **DUA TINGKAT** (`HumanLog.ev` penting / `HumanLog.evTech` teknis) — **PENTING selalu tampil**: boot dipadatkan **12+ → ±5 baris** ("Server siap — mirror www/ N file · guest X · N server" + "Server lokal hidup — game 8080 · login 8610 · dasbor 4000"), mode, tamu/daftar/masuk/loginchecknative, daftar server dikirim, ⚠/500; **TEKNIS hanya di mode RINCI**: semua baris request per-file (200 dari www/, 404 normal, seed CDN, passthrough), probe DNS ✔, denyut pantauan, geser tombol, dasbor dimuat — toggle **RINCI** di dasbor tersimpan `server.json "logTech"` (tahan reboot); **file `logs/human.log` TETAP LENGKAP** (penting+teknis — mode bersih tidak menghilangkan bukti) + API baru `/api/logtech` & `/api/logs/file?tail=` ("Copy penuh" dari disk); penghitung dasbor (`mirrorSeeded/mirrorSeedBytes/notFound`) menggantikan 100+ baris GET resource; **DASBOR DIROMBAK**: Log pusat (filter Semua/⚠ saja + toggle RINCI + Copy/Copy penuh/Bersihkan) + strip status 1 baris + tab Data (dragonball.db + server asli) & Teknis (root/port/penghitung, 6 request terakhir, daftar www/ dilipat) — kartu besar berulang dihapus; harness **71/71 LULUS** (+9 tes: dua tingkat, file tetap lengkap, tailText, logTech persisten) | **L5a–L5b ✓ (log & dasbor didengarkan owner)** |
| **0.4.4** | **(v4.4) + FIX MASUK/DAFTAR — AKUN LAYAR SDK = EMAIL** (laporan owner "login register gak work .. ini jelas PR"): bedah dex fragmen UI MASUK/DAFTAR (`timidcompletely`/`navyshop`) + validator (`feedtonight.singersmooth`) → akun yang dikirim client = **EMAIL** (regex `\w+@\w+.\w+`, sandi ≥6, DAFTAR wajib centang persetujuan) — server lokal dulu menolak `@`/`+` (charset 3-32) → **DAFTAR selalu 40102, MASUK selalu 40101**; kini `SdkData.registerUser` menerima email (huruf kecil, `[a-z0-9_.+-@]`, 3-64), `loginByName` tidak peka huruf besar/kecil, `SdkApi` log verbose MASUK/DAFTAR (sukses+gagal+sebab), uid=email mengalir utuh ke loginchecknative→url H5→loginGame; + whitelist Cleaner **`servers.json`** (bug lanjutan: file dihapus-restor tiap boot — suntingsan pemilik hilang); pesan log tamu dirapikan; harness **63/63 LULUS** (+15 tes baru: email + Cleaner) | **L5a–L5b ✓ (login/daftar natural)** |
| **0.4.3** | **(v4.3) + DATABASE SQLITE `dragonball.db`** (nama FINAL owner — "masak iyaaa database txt"): semua data akun/tamu/sesi pindah dari txt ke SQLite bawaan Android (`Db.java`+`DbAndroid.java`+`DbMem.java`; `DbJdbc.java` hanya harness, tak masuk APK) — tabel `accounts`, `sdk_guests`, `sdk_sessions`, `kv` (local_user, last_uid) §2.12; **MIGRASI OTOMATIS** txt warisan → DB di boot pertama SEBELUM Cleaner (file lama → `*.imported` — nol data hilang) + **FIX bug tersembunyi**: txt lama tak masuk whitelist Cleaner = TERHAPUS tiap boot (guest/akun tak pernah tahan reboot!) → kini `dragonball.db` (+jurnal) masuk whitelist; korupsi hanya dipulihkan utk korupsi SQLite sungguhan (bukan galat lock/engine); harness 0.4.3 di-commit (`local-server/harness/HarnessSdk.java`) **50/50 LULUS** (migrasi, REBOOT-persistence, SQL nyata via JDBC, regresi SDK 0.4.2 penuh + socket.io berframing)** | **L5a–L5b ✓ (data profesional — dragonball.db)** |
| **0.4.2** | **(v4.2) + ALUR SDK — layar login SDK ASLI DULUAN, seluruh tahap SDK dilayani LOKAL (koreksi owner "SDK itu lebih dulu … jangan ngawur bikin config SDK"): dex `com.quickgame.android.sdk` dibedah + server asli qsdk.t4game.com & login.popoh5.com:510 DIPROBE langsung → `SdkApi.java`+`SdkData.java` (port 8610) melayani `/v1/system/init, getNotice, getAgreement, onNewToken`, `/v1/user/registerVisitor (tamu per deviceId — tamu pertama = guest lama pemilik), autoLogin, loginByName (password MD5), registerUser, ckRegVistor, findPassByEmail, postGooglePlayVerify`, `/v1/auth/* (bindMail, changePassword, setGameRoleInfo, …)` + `GET /loginchecknative` — SEMUA skema respons = salinan respons asli (body form `{sv,data,sign}` md5 rahasia "0b2a18e45d7df321" dari dex; pesan galat Indonesia seperti asli) + `assets/QHinfo` → `http://127.0.0.1:8610` (endpoint saja, dex/UI SDK tidak disentuh) + FIX bin `loginServer` TANPA port (dulu `127.0.0.1:8610:8610` dobel → URL loginchecknative shell rusak) + userid url H5 = uid SESI SDK terakhir (config/sdk-last.txt; loginGame mengikuti sesi SDK bila url beda) + mode ONLINE: API SDK diteruskan utuh ke qsdk asli + dasbor kartu Tahap SDK (sesi, perangkat)** | **L5a–L5b ✓ (alur SDK lokal penuh — harness 39/39)** |
| **0.4.1** | **(v4.1) + ANTI "ERROR NETWORK" — FIX Payload.collect (prefix ASSET_DIR dipakai buat path login → servers.json salah tulis ke config/login/; kini base eksplisit + sisa config/login/ dibersihkan) + loop accept NanoHTTPD tahan Throwable (dulu RuntimeException/Error = thread accept mati diam-diam → port "hidup mati" → game "Read Upgrade Config File Fail,Please Check NetWork!" TANPA log; kini dicatat via CrashListener + loop lanjut) + watchdog port 8080+8610 tiap 5 dtk (mati = ⚠, pulih = ✔)** | **L5a–L5b ✓ (stabilisasi LOCAL)** |
| **0.4.0** | **(v4.0) + LOGIN LOKAL — server login lokal port 8610 memakai protokol PERSIS client game (engine.io v3 + socket.io v2, event `handler.process`: loginGame/GetServerList/LoginAnnounce/Save*) + endpoint login di setting bin (loginServer, tsLoginServer, loginPort 610→8610, loginWebPort 510→8610) & serversetting.json (loginserver) ditulis-ulang ke 127.0.0.1 di mode LOCAL (koreksi owner "endpoint masih menunjuk server pusat") + guest lokal tahan lama via url H5 `?userid=<guest>` (config `useSdk`, bawaan mati) + akun lokal config/accounts.txt + daftar server snapshot ASLI 2109 server (config/servers.json, boleh disunting) + dasbor log baru: font 11px + baris terpisah garis + rincian redup (koreksi owner "font kegedean & nyampur")** | **L5a–L5b ✓ (tahap SDK dilokalkan)** |
| **0.3.2** | **(v3.2) + WEBSITE TANPA SWITCH MODE — switch dihapus dari dasbor 4000 & halaman 8080 (status mini tanpa tombol), ganti mode cukup tombol melayang (koreksi owner "website pindah mode itu salah cuma menuhin tempat") + `POST /api/mode` dihapus + badge mode baca-saja di dasbor + baris "memuat…" tak pernah tersisa di kotak log + narasi mode di log dirampingkan** | **L1–L2b ✓ (build — v3.2)** |
| 0.3.1 | (v3.1) + log request menulis URL ASLI `http://127.0.0.1:8080/…` (tanpa label LOCAL/ONLINE — koreksi owner) + switch mode di KEDUA website (DIBATALKAN di 0.3.2) + log anti-banjir (dedupe request kembar <10 dtk + log floating sekali per proses) + 404 normal (com_db_local.bin) dilog tenang & jelas | L1–L2b ✓ (build — v3.1) |
| 0.3.0 | (v3.0) + mirror PENUH server asli 31 file (setting bin, H5+15 JS, properties, versi, base.zip/all.zip, incremental, lzceshi) diekstrak otomatis + tombol melayang BISA DIGESER (posisi tersimpan) + pembersih whitelist anti-sampah + rewrite setting bin (XOR DragonBall) + peta dragonh5cdn + self-seed mirror + dasbor baru (5 path terakhir + penjelajah www/ + switch mode) | L1–L2b ✓ (build — v3.0) |
| 0.1.0 | (v2.8) + payload config asli dibawa APK & diekstrak OTOMATIS ke www/ saat pertama jalan (tanpa langkah manual) + port game terpisah 8080 (LOCAL www/ / ONLINE passthrough) + switch mode tombol melayang + restart otomatis + endpoint client → 127.0.0.1:8080 + log tenang | L1–L2b ✓ (build — v2.8) |
| 0.1.x | Uji device: switch mode, payload www/ otomatis, pantau log | L4b |
| 0.5.0 | Ambil alih bertahap per-endpoint (M3) | L5b |
| 1.0.0 | Fondasi lulus penuh — paritas endpoint terbukti | L5b ✓ |
| 1.1.0 | Persistensi state (SQLite) + stub SDK cloud | L6 awal |
| 2.0.0 | Standalone penuh tanpa internet | L6 |

**Build 0.5.1 (CONFIG LOGIN SEMUA MODE, v5.1) — 2026-11-04 — BUILD TERKINI:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.5.1.apk` (163.991.547 byte; repo = pointer LFS) |
| sha256 | `226959ff76953eb4d5789d3034a11190c3edb09299c721ad5ad97da08c73c4d8` |
| Basis | `releases/APK-LSF-0.5.0.apk` (sha256 `53c3f94721d6853a661a0fd467ecdefccc26c0a731b4ee79b553623515f4e45e` — diunduh dari Git LFS via API batch & diverifikasi sha256 penuh; sandbox reset menyebabkan clone + unduh ulang) |
| Verifikasi build | `classes.dex` game **identik dasar** (CRC `281225eb`, 6.697.104 B — dex game tidak disentuh) · `AndroidManifest.xml` `2486fd1c` · `resources.arsc` `c6b5e22c` · config properties IDENTIK · `classes2.dex` baru (181.988 B) · `dashboard.html` baru (38.434 B) · QHinfo `http://127.0.0.1:8610` · mirror www/ 31 file utuh (base.zip/all.zip terbawa dari dasar) · payload lama bersih · ditandatangani keystore LSF tetap (zipalign + sign) |
| Perubahan server lokal | `SettingBin.java` (+`rewrittenOnline`/`plainOnline` — mode ONLINE: jalur login + url H5 → server lokal, tsUrl asli, tanpa userid; `setJsonValue` naik publik) · `GameServer.java` (servePassthrough: setting bin + serversetting.json ditulis-ulang sebelum disajikan + catatan grup; javadoc ONLINE) · `LoginServer.java` (+`proxyOnline` passthrough byte-per-byte → `https://login.popoh5.com:610` HTTPS hasil probe; websocket → 400; log alur 📡/✅/📋/🔌 passthrough berdedupe; +`parseFrames`/`countServerIds` publik) · `SdkApi.java` (loginchecknative = jalur BAKU ONLINE ke 510 + baris 🔑 passthrough berdedupe; komentar diperbarui) · `Starter.java` (teks Mode ONLINE) · `dashboard.html` (modemap + kartu koneksi = semua mode) · `StatusStore.java` (0.5.1) · `HarnessSdk.java` (+16 tes TAHAP 16) · `build.sh` 0.5.1 (basis 0.5.0 + changelog) |
| Probe langsung (2026-11-05) | `login.popoh5.com:610` = **HTTPS wajib** (`96:0{"sid":"…","upgrades":["websocket"],…}2:40`; HTTP polos → `400 The plain HTTP request was sent to HTTPS port`) · TLSv1.2 ECDHE-RSA-AES128-GCM-SHA256 · CN `*.popoh5.com` (DigiCert Encryption Everywhere DV TLS CA - G2, valid) · `:510/loginchecknative` HTTPS → `{"code":500,"msg":"登录参数验证失败"}` (skema asli) · `dragonh5cdn …/serversetting.json` = `{"loginserver":"https://login.popoh5.com:610","requireSDK":false}` |
| Uji asap HIDUP (sandbox) | LoginServer lokal 8615 mode ONLINE → handshake klien lokal = OPEN ASLI upstream (sid `iJPI-13m2N3TeQXWbBlj` diteruskan) → POST CONNECT + event loginGame = `ok` → jawaban upstream tersalur identik dengan koneksi langsung (dua-duanya `1:1` — transparansi byte-per-byte terbukti, bukan bug proxy) |
| Catatan lingkungan | sandbox reset kedua menghapus repo di tengah kerja → di-clone ulang; ECJ 3.33.0/r8 8.3.37/android.jar (API 33, platform-33_r02)/sqlite-jdbc 3.45.1.0(+slf4j 2.0.9)/uber-apk-signer 1.3.0 diunduh ulang; APK dasar 0.5.0 ditarik dari Git LFS (API batch, sha256 terverifikasi) |
| Harness | **96/96 LULUS** (JVM + sqlite-jdbc 3.45.1 + slf4j 2.0.9; +16 tes 0.5.1: bin ONLINE url/update/loginServer/port/tsLoginServer/tsUrl-asli/tanpa-userid/bin-terenkripsi, serversetting ONLINE, framing parser + tahan rusak, hitung server ACK, upstream HTTPS) |

**Build 0.4.9 (LOG EMOJI + BLOK RINCIAN, v4.9) — 2026-11-04:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.9.apk` (163.958.779 byte; repo = pointer LFS) |
| sha256 | `624d995dea72586897819d70f6c8c47a3b47fce3eb0bf3c45e4deabc528d0e9b` |
| Basis | `releases/APK-LSF-0.4.8.apk` (sha256 `3e74d7ac10081667e189d864de2908c3f2aae0718792bb70b4a1f54ac585077b` — diunduh dari Git LFS via API batch & diverifikasi sha256 penuh; sandbox reset menyebabkan unduhan ulang) |
| Verifikasi build | `classes.dex` game **identik dasar** (CRC `281225eb`, 6.697.104 B — dex game tidak disentuh) · `AndroidManifest.xml` `2486fd1c` · `resources.arsc` `c6b5e22c` · config properties IDENTIK · `classes2.dex` baru (157.616 B) · `dashboard.html` baru (28.222 B) · QHinfo `http://127.0.0.1:8610` · mirror www/ 31 file utuh (base.zip/all.zip terbawa dari dasar) · payload lama bersih · ditandatangani keystore LSF tetap (zipalign + sign) |
| Perubahan server lokal | `HumanLog.java` (ikon `char`→`String` emoji + `tree()` blok rincian ├─/└─ — file human.log ikut pohon penuh) · `Starter.java` (boot 3 baris → SATU baris 🚀 ber-rincian Port/Mode/Folder/Pemain) · `SdkData.java` (`noteSdkLogin` 👤 dedupe 10 mnt per uid — registerVisitor & autoLogin tak menduakan baris; 🔑 sesi rinci) · `SdkApi.java` (👤/⚠️ MASUK/DAFTAR ber-rincian; duplikat log daftar akun dihapus) · `LoginServer.java` (📡 handshake naik ke penting + dedupe 10 mnt; ✅ loginGame dgn ms+byte; 📋 serverlist ber-rincian Sumber/Ukuran/Lanjutan) · `FloatingToggle/Monitor/GameServer/Config/Cleaner/RestartHelper/Db/LocalServer/LoginData/SettingBin/LogGroups.java` (ikon emoji + rincian) · `StatusStore.java` (**v0.4.9**) · `dashboard.html` (teks 13px + kontras naik, kolom ikon emoji, rincian pohon `├─/└─` penuh tanpa pemenggalan, Copy ikut blok rincian, legenda footer emoji) · `HarnessSdk.java` (ikon String) · `build.sh` 0.4.9 (basis 0.4.8 + pemeriksaan upgrade paket lintas dasar) |
| Catatan lingkungan | sandbox reset menghapus `/home/z/db-apk` + `tools2` → repo di-clone ulang dari GitHub, APK dasar 0.4.8 ditarik dari Git LFS (API batch, sha256 terverifikasi), ECJ/r8/android.jar(API 33)/sqlite-jdbc(+slf4j) diunduh ulang; `android.jar` API lama tidak memuat simbol API 23-26 (TYPE_APPLICATION_OVERLAY, FLAG_IMMUTABLE) → dipakai API 33 |
| Harness | **80/80 LULUS** (JVM + sqlite-jdbc 3.45.1 + slf4j 2.0.9; 1 redaksi detail ⚠️ token disesuaikan agar tidak mengganggu penghitung dedupe) |

**Build 0.4.8 (LOG CERITA ALUR — SATU KEJADIAN SATU BARIS, v4.8) — 2026-11-04:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.8.apk` (163.954.683 byte; repo = pointer LFS) |
| sha256 | `3e74d7ac10081667e189d864de2908c3f2aae0718792bb70b4a1f54ac585077b` |
| Basis | `releases/APK-LSF-0.4.7.apk` (sha256 `0683119f…` — diunduh dari Release v0.4.7 & diverifikasi; rilis lama v0.3.2 sudah tidak tersedia di GitHub) |
| Verifikasi build | `classes.dex` game **identik dasar** (CRC `281225eb`, 6.697.104 B — dex game tidak disentuh) · `AndroidManifest.xml` `2486fd1c` · `resources.arsc` `c6b5e22c` · config properties IDENTIK · `classes2.dex` baru (153.416 B) · QHinfo `http://127.0.0.1:8610` · mirror www/ 31 file utuh (sha base.zip/all.zip = pin §2.9, diverifikasi saat unduh ulang via `tools/snapshot.sh`) · payload lama bersih · ditandatangani keystore LSF tetap (zipalign + sign v1+v2+v3) |
| Perubahan server lokal | `SdkApi.java` (MASUK/DAFTAR hasil-saja — pengumuman percobaan dihapus; masuk otomatis = ✔) · `SdkData.java` (bahasa cerita: "Lanjut masuk sebagai tamu …", "Sesi … disahkan (loginchecknative) — boot game dilanjutkan") · `LoginServer.java` (milestone daftar server + **baris TUNTAS**, dedupe 10 mnt) · `LoginData.java` (loginGame override dirapikan) · `Starter.java` (boot Mode 1 kalimat; Pemain + db digabung rapi) · `LocalServer.java` (toggle RINCI tidak lagi dilog) · `HumanLog.java` (aturan 0.4.8) · `StatusStore.java` (**v0.4.8** — dulu tertinggal 0.4.6) · `dashboard.html` (Copy rapi + detail tampil + Σ berjam) · `HarnessSdk.java` (tes dedupe ikut pesan baru) · `build.sh` 0.4.8 |
| Harness | **80/80 LULUS** (JVM + sqlite-jdbc; 1 tes disesuaikan ke pesan baru) |

**Build 0.4.7 (LOG BAHASA MANUSIA — BOOT 3 BARIS + PEMETAAN KONEKSI, v4.7) — 2026-11-04:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.7.apk` (163.954.683 byte; repo = pointer LFS) |
| sha256 | `0683119f4576b984cabc9d3e7894d0bc8da65b9cac52d92010cd2aaa537fd603` |
| Basis | `releases/APK-LSF-0.3.2.apk` (sha256 `b64cba53bf96fd6de3bcb5b15c33f6bb9f84c1c170a7808fea7b897dc9ab96d2` — terverifikasi saat unduh ulang) |
| Verifikasi build | `classes.dex` game **identik dasar** (6.697.104 B — dex game tidak disentuh) · `classes2.dex` baru (155.320 B) · QHinfo `http://127.0.0.1:8610` · mirror www/ 31 file utuh (sha base.zip/all.zip = pin §2.9) · payload lama bersih · ditandatangani keystore LSF tetap (zipalign + sign) |
| Perubahan server lokal | `Starter.java` (BOOT 3 BARIS + peta arah koneksi — "Server siap"/"Mode"/"Server lokal hidup" lama digabung) · `LoginServer.java` (handshake → TEKNIS; "Login lokal OK — …"; GetServerList "(snapshot asli bawaan APK)") · `SdkData.java` (dedupe loginchecknative 10 MENIT; "perangkat → tamu") · `Db.java` (db siap → TEKNIS) · `FloatingToggle.java` (tampil → TEKNIS) · `dashboard.html` (modemap + kartu "Ke mana koneksi mengarah" + footer) · `build.sh` 0.4.7 |
| Bukti rewrite (§2.15-a) | `loginServer` → `http://127.0.0.1` · `tsLoginServer` → `http://127.0.0.1:8610` · `url` H5 → `http://127.0.0.1:8080` · `serversetting.json loginserver` → `http://127.0.0.1:8610` — dijalankan lewat `SettingBin.rewrittenLocal`/`serveServersetting` yang sama saat serving |
| Harness | **80/80 LULUS** (JVM + sqlite-jdbc; tanpa perubahan tes) |

**Build 0.4.6 (MODEL FINAL LOG — GRUP BATCH, v4.6) — 2026-11-03:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.6.apk` (163.950.587 byte; repo = pointer LFS) |
| SHA-256 | `4a3bb492b361dca16d3d680d6556215dce6d5692a8c02884820e7e55c50e4a6e` |
| versionCode / versionName | **4 / 0.1.0** (manifest TIDAK disentuh — alur PATCH) |
| Dasar | **Release 0.3.2** (`b64cba53…` diunduh ulang dari Release GitHub & diverifikasi sebelum dipakai) |
| Toolchain | ECJ 3.33.0 · r8 8.3.37 (D8 → `classes2.dex`; `DbJdbc.class` DISINGKIRKAN) · uber-apk-signer 1.3.0 · android.jar **API 28** (diunduh ulang — sandbox; jar lama ternyata API 22 yang tidak punya `TYPE_APPLICATION_OVERLAY`) · sqlite-jdbc **3.42.0.0** (dari Release GitHub xerial; 3.45+ butuh SLF4J terpisah) — HANYA harness |
| **Permintaan owner** | *"group itu kalau terdeteksi emang config/resource yg banyak. kalau alur ya gk usah di group"* + *"Cuma ganti log aja harus anda pecah jadi beberapa bagian"* — dieksekusi SATU tarikan: log + dasbor + dedupe + build |
| Perubahan server lokal | `LogGroups.java` (BARU — grup batch: mirror/req/404/pass; statistik hidup; naik-gagal 30 dtk; Σ ke human.log ±45 dtk; toJson) · `HumanLog` (+`writeRawLine` untuk Σ) · `GameServer` (semua baris batch → `LogGroups.item`; ReqLog khusus baris penting 404 tak terpetakan/502) · `LocalServer` (+`/api/groups`; clear ikut kosongkan grup) · `SdkData` (dedupe loginchecknative OK) · `StatusStore` (v0.4.6) · `dashboard.html` (ROMBAK TOTAL — panel status + alur + grup lipat) · `HarnessSdk` (+9 tes) · `build.sh` 0.4.6 |
| Verifikasi (sandbox) | ✅ ECJ bersih; ✅ **harness 80/80 LULUS** (71 regresi + 9 tes baru: grup kosong/statistik/bukan-linear/gagal-naik/jeda-30dtk/Σ-file-saja/dedupe-OK-ganda); ✅ CRC diff dasar 0.3.2: `classes.dex` game `281225eb`, `AndroidManifest.xml` `2486fd1c`, `resources.arsc` `c6b5e22c`, config properties IDENTIK — hanya `classes2.dex` + `dashboard.html` + mirror www + `QHinfo` |
| Perubahan client | **TIDAK ADA** — dex game/manifest/resources identik (CRC) |
| Status | ✅ build & verifikasi selesai · ⏳ uji device owner |

**Build 0.4.5 (LOG BERSIH + DASBOR FOKUS, v4.5) — 2026-11-03:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.5.apk` (163.942.395 byte; repo = pointer LFS) + **asset Release GitHub `v0.4.5`** (163.942.395 B, diunduh balik & sha256 diverifikasi identik) |
| SHA-256 | `e7daa954f3b80dba77dd25b7e6342d9ce37c6849da10cd3eebd2bc61ed29a8f0` |
| versionCode / versionName | **4 / 0.1.0** (manifest TIDAK disentuh — alur PATCH) |
| Dasar | **Release 0.3.2** (`b64cba53…`) |
| Toolchain | ECJ 3.33.0 · r8 8.3.37 (D8 → `classes2.dex`; `DbJdbc.class` DISINGKIRKAN) · uber-apk-signer 1.3.0 · JDK 21 · android.jar API 34 · sqlite-jdbc 3.42.0.0 (HANYA harness) |
| **Permintaan owner** | *"jujur terus terang log anda bagi saya cuma sampah apalagi UI website banyak informasi yg tidak bermanfaat.. perlu susun rencana lagi untuk ini"* (log device 18:07: DAFTAR email test@mail.com SUKSES — 0.4.4 terbukti; sisanya = kebisingan log + UI) |
| Perubahan server lokal | `HumanLog` (dua tingkat `ev`/`evTech` + `setTechEnabled` + `tailText`; teknis tidak masuk memori dasbor saat BERSIH — file tetap lengkap) · `GameServer` (semua baris request → `evTech`; 404 normal & seed dihitung ke `StatusStore`) · `Starter` (boot dipadatkan: "Server siap …" + "Server lokal hidup …" — dulu 3+3 baris) · `Monitor` (probe ✔ + denyut → teknis; ⚠ tetap penting) · `SdkApi` (init/rute tak dikenal/penerusan → teknis) · `FloatingToggle` (geser → teknis) · `Db` (kalimat dragonball.db dipendekkan) · `Config` (+`logTech`) · `StatusStore` (+penghitung mirror/404 + flag teknis) · `LocalServer` (+`/api/logtech`, +`/api/logs/file`) · `dashboard.html` (ROMBAK TOTAL) · `HarnessSdk` (+9 tes) · `build.sh` 0.4.5 |
| Verifikasi (sandbox) | ✅ ECJ bersih; ✅ **harness 71/71 LULUS** (62 regresi + 9 tes baru); ✅ CRC diff dasar 0.3.2: `classes.dex` game `281225eb`, `AndroidManifest.xml` `2486fd1c`, `resources.arsc` `c6b5e22c`, config properties IDENTIK — hanya `classes2.dex` + `dashboard.html` + tanda tangan |
| Perubahan client | **TIDAK ADA** — dex game/manifest/resources identik (CRC) |
| Status | ✅ build & verifikasi selesai · ⏳ uji device owner |

**Build 0.4.4 (FIX MASUK/DAFTAR — AKUN EMAIL, v4.4) — 2026-11-03:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.4.apk` (163.938.299 byte; repo = pointer LFS) + **asset Release GitHub `v0.4.4`** (163.938.299 B, diunduh balik & sha256 diverifikasi identik) |
| SHA-256 | `a1605e8de5b3c26c9ebb02c534a62c09b74ad8f62562f97e3c7506531007f5b8` |
| versionCode / versionName | **4 / 0.1.0** (manifest TIDAK disentuh — alur PATCH) |
| Dasar | **Release 0.3.2** (`b64cba53…` diverifikasi sebelum dipakai) |
| Toolchain | ECJ 3.33.0 · r8 8.3.37 (D8 → `classes2.dex`; `DbJdbc.class` DISINGKIRKAN) · uber-apk-signer 1.3.0 · JDK 21 · android.jar API 34 (Sable) · sqlite-jdbc 3.42.0.0 (HANYA harness — 3.43+ butuh SLF4J terpisah) |
| **Permintaan owner** | *"login register gak work .. ini jelas PR"* (log device: tamu jalan sampai daftar server, MASUK/DAFTAR mati) |
| Akar (bedah dex §2.13) | akun layar SDK = EMAIL — `registerUser` lokal menolak `@` → 40102; tak ada akun email → 40101. Client TIDAK disentuh |
| Perubahan server lokal | `SdkData` (registerUser/loginByName email-aware + pesan tamu) · `SdkApi` (log verbose MASUK/DAFTAR + pesan 40102 terperinci) · `Cleaner` (+`servers.json`) · `HarnessSdk` (+15 tes) · `build.sh` 0.4.4 · README §2.13 |
| Verifikasi (sandbox) | ✅ ECJ bersih; ✅ **harness 63/63 LULUS** (48 regresi + 13 tes email + 2 tes Cleaner); ✅ CRC diff dasar 0.3.2: `classes.dex` game, `AndroidManifest.xml`, `resources.arsc`, config properties IDENTIK — hanya `classes2.dex` + `dashboard.html` + `QHinfo` + tanda tangan |
| Perubahan client | **TIDAK ADA** — dex game/manifest/resources identik (CRC) |
| Status | ✅ build & verifikasi selesai · ⏳ uji device owner |

**Build 0.4.3 (DRAGONBALL.DB — SQLITE, v4.3) — 2026-11-03:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.3.apk` (163.938.299 byte) + **asset Release GitHub `v0.4.3`** |
| SHA-256 | `9f86aca7620d6593b8f46255fe200067cefe0f62182efff260fb6f74a9c29d87` |
| versionCode / versionName | **4 / 0.1.0** (manifest TIDAK disentuh — alur PATCH) |
| Dasar | **Release 0.3.2** (`b64cba53…` diverifikasi sebelum dipakai) |
| Toolchain | ECJ 3.33.0 · r8 8.3.37 (D8 → `classes2.dex`; `DbJdbc.class` DISINGKIRKAN) · uber-apk-signer 1.3.0 · JDK 21 · android.jar API 34 (`platform-34-ext7_r03.zip`) · sqlite-jdbc 3.45.1.0 + slf4j (HANYA harness desktop) |
| **Permintaan owner** | *"ini database yg profesional lahh masak iyaaa database txt"* → *"database dragonball.db sqlite . final ."* → *"belum buat karena rencana sdk masih belum jelas agar work, guest login register sampai ke login secara natural"* (rencana dibuktikan dari dex — §2.12 — lalu dibangun) |
| Perubahan server lokal | `Db.java` + `DbAndroid.java` + `DbJdbc.java` + `DbMem.java` BARU; `LoginData`/`SdkData` pindah ke DB (API publik tetap); `Cleaner` whitelist + `dragonball.db*`; `Starter` boot DB SEBELUM Cleaner; `build.sh` 0.4.3; `HarnessSdk.java` di-commit |
| Verifikasi (sandbox) | ✅ ECJ bersih (23 sumber); ✅ **harness 50/50 LULUS** (migrasi 5 file warisan, REBOOT utuh, SQL nyata, regresi 0.4.2 penuh); ✅ CRC diff dasar 0.3.2: 3.695 entri IDENTIK — `classes.dex` game, `AndroidManifest.xml`, `resources.arsc`, setting bin, `clientversion.json`, `serversetting.json` SEMUA sama; hanya `classes2.dex` + `dashboard.html` + `QHinfo` (21 B) + `login/servers.json` (BARU) + tanda tangan |
| Perubahan client | **TIDAK ADA** — dex game/manifest/resources identik (CRC) |
| Status | ✅ build & verifikasi selesai · ⏳ uji device owner |

**Build 0.4.1 (ANTI "ERROR NETWORK", v4.1) — 2026-11-02:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.1.apk` (163.909.627 byte) + **asset Release GitHub `v0.4.1`** |
| SHA-256 | `97a9674c096e609b2aab2eabe685fd3718b6edfe300626e9c555d7079e985ba3` |
| Dasar | **Release 0.3.2** (`b64cba53…`) — alur PATCH tanpa apktool (dex game & resource tidak disentuh) |
| Laporan owner | "ada error network saat mode local" — dialog game **"Read Upgrade Config File Fail,Please Check NetWork！"** (string ditemukan di dex LaunchActivity — catch persis di alur cek pembaruan) |
| Akar (bedah jadx + log device) | (1) `Payload.collect()` memangkas prefix dengan `ASSET_DIR` = "server/www" yang hardcoded — dipanggil juga untuk "server/login" → `servers.json` tertulis ke `config/login/` (BUKAN `config/servers.json`) → log "daftar darurat 1 server"; (2) setelah `resource.version` (200, 17:42:02) game berhenti meminta apa pun — upgrade.json/size.json tak pernah sampai server (kegagalan level koneksi TIDAK terlihat log) → dialog Check NetWork; lubang kematian: loop accept vendored NanoHTTPD hanya menangkap `IOException` — satu `RuntimeException/Error` = thread accept mati diam-diam → port "hidup mati" (dasbor 4000 tetap hidup, monitor jalan) |
| Perbaikan | (a) `collect(am, base, dir, out)` — prefix eksplisit + bersihkan sisa `config/login/` warisan bug; (b) loop accept menangkap `Throwable` → dicatat via hook `NanoHTTPD.acceptCrashListener` (HumanLog) → loop LANJUT melayani; (c) watchdog: probe `127.0.0.1:8080`+`8610` tiap 5 dtk — gagal pertama + tiap ±1 mnt = ⚠, pulih = ✔ |
| Verifikasi (sandbox) | ✅ ECJ bersih 62 kelas; ✅ harness protokol 7/7 cek (READY servers=**2109**, handshake + PING→PONG `1:3`, probe TCP OK); ✅ CRC diff dasar 0.3.2: dex game `281225eb`, `resources.arsc` `c6b5e22c`, `QHinfo` `af014748` IDENTIK — hanya `classes2.dex` (`0587eb7f`, 118.512 B) + `dashboard.html` + `login/servers.json`; sign v1/v2/v3 keystore tetap |
| Perubahan client | **TIDAK ADA** — identik dasar (CRC) |

**Build 0.4.0 (LOGIN LOKAL, v4.0) — 2026-11-02:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.4.0.apk` (163.905.531 byte) + **asset Release GitHub `v0.4.0`** |
| SHA-256 | `2fa3f59d14d9d76cc855bf7f11b9c4b3ffb78dab00626988466404fb481b9509` |
| versionCode / versionName | **4 / 0.1.0** (manifest TIDAK disentuh — alur PATCH; identitas versi dibawa dasbor/Release) |
| Dasar | **Release 0.3.2** (`b64cba53…`) — workspace dipulihkan dari GitHub setelah reset sandbox; APK dasar & baseline di-unduh ulang via LFS manual (batch API → unduh → verify sha256) sebelum dipakai |
| Toolchain | ECJ 3.33.0 · r8 8.5.35 (D8 → `classes2.dex`) · uber-apk-signer 1.3.0 · JDK 21 · android.jar API 34 (`platform-34-ext7_r03.zip` dl.google.com) — tanpa apktool (patch langsung ke APK 0.3.2) |
| **3 PR (permintaan owner)** | **(1) PR1 — endpoint login tak lagi ke server pusat**: mode LOCAL menulis-ulang `loginServer` (`https://login.popoh5.com` → `http://127.0.0.1:8610`), `tsLoginServer`, `loginPort` (610→8610), `loginWebPort` (510→8610) di setting bin + `loginserver` di `serversetting.json` (asal `https://login.popoh5.com:610`) — file mirror asli tak disentuh, hanya jawaban server; **(2) PR3 — SDK stage dilokalkan**: `LoginServer.java` port 8610 memakai wire-format PERSIS client (engine.io v3 polling + socket.io v2, framing `"<len>:<pkt>"`, event `handler.process`, ACK `43<id>[{ret,data}]`): `loginGame` = masuk + daftar otomatis (akun lokal `config/accounts.txt`, skema respons persis server asli + sign/sdk/newUser untuk jalur tanpa-SDK), `GetServerList` = snapshot ASLI 2109 server (`config/servers.json` — dibawa APK, boleh disunting tanpa rebuild), `LoginAnnounce`/`SaveHistory`/`SaveLanguage`/`SaveUserEnterInfo` = ret 0; guest lokal TAHAN LAMA (`config/local-user.txt`) di-inject ke url H5 sebagai `?userid=<guest>` (jalur tanpa-SDK di main.min — §2.10); bisa dimatikan dengan `server.json` `"useSdk": true` (layar SDK asli dipakai); **(3) PR2 — log dasbor baru**: font 11px (dulu 12,5px), setiap baris = baris tabel dengan **garis pemisah**, waktu redup, rincian teknis di baris kedua terpisah, hover jelas; kartu port game kini menampilkan status login lokal (dot hidup/mati, alamat, jumlah akun + guest + jumlah server) |
| Perubahan server lokal | `LoginServer.java` + `LoginData.java` BARU; `SettingBin` (rewrite login endpoint + userid), `GameServer` (serversetting.json + log), `Starter` (LoginServer hidup + default login + target monitor `login.popoh5.com:610`), `Config` (`loginPort`, `useSdk`), `Payload` (`extractLoginDefaults`), `StatusStore` (field login) diubah; `dashboard.html` (PR2) |
| Verifikasi (sandbox) | ✅ kompilasi ECJ bersih (59 kelas); ✅ **harness JVM protokol penuh LULUS**: handshake framing `81:0{…}2:40` (persis pola server asli), CONNECT 40 → ok, `loginGame` → `{ret:0, userId:guest…, isCreated:true}` lalu login ulang `isCreated:false` (akun persisten + sign terbentuk), `GetServerList` → `{ret:0, errorCode:0, 2109 server}` (paket 617 KB), PING→PONG `3` — semua LULUS di text/plain DAN application/x-www-form-urlencoded; ✅ hardening terakhir LoginServer: body POST dibaca LANGSUNG dari stream (`readBody`, persis Content-Length) — akar temuan: vendored NanoHTTPD memakai aturan form utk Content-Type `application/x-www-form-urlencoded` sehingga `files["postData"]` kosong dan framing engine.io hilang; baca langsung membuat server login tahan SEMUA Content-Type (client game asli tetap text/plain); ✅ rewrite setting bin diuji (JSON valid — semua endpoint ke lokal, `clientParams` utuh); ✅ probe server asli: POST polling BERFRAMING = `200 "ok"` (kesimpulan 502 lama terkoreksi — §2.10); ✅ CRC diff: hanya `classes2.dex` + `dashboard.html` + `assets/server/login/servers.json` (BARU) — dex game `281225eb`, manifest `2486fd1c`, `resources.arsc` `c6b5e22c`, config `62eab0f4`/`1cc6e06c`, `QHinfo` `af014748`, seluruh mirror www/ IDENTIK dasar 0.3.2 |
| Perubahan client | **TIDAK ADA** — `classes.dex` game, manifest, `resources.arsc`, `QHinfo`, config endpoint: semua identik (diverifikasi CRC saat build). Perubahan endpoint ada di isi setting bin/serversetting.json yang DILAYANI server lokal (server-side, bukan edit APK) |
| Signature | ✅ v1 + v2 + v3 — cert `CN=DB Local LSF` (keystore tetap) |
| Status | ✅ build & verifikasi level build selesai · ⏳ uji device owner (mode LOCAL: boot → langsung daftar server (tanpa layar SDK) → pilih server → masuk dunia game via gateway asli; mode ONLINE: layar SDK asli seperti biasa; dasbor log baru) |

**Build 0.3.2 (WEBSITE TANPA SWITCH MODE, v3.2) — 2026-11-02 — DIGANTIKAN 0.4.0:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.3.2.apk` (163.356.589 byte) + **asset Release GitHub `v0.3.2`** |
| SHA-256 | `b64cba53bf96fd6de3bcb5b15c33f6bb9f84c1c170a7808fea7b897dc9ab96d2` |
| versionCode / versionName | **4 / 0.1.0** (manifest TIDAK disentuh — alur PATCH; identitas versi dibawa dasbor/Release) |
| Dasar | **Release 0.3.1** (`252984ce…`, diunduh dari asset Release GitHub v0.3.1 — diverifikasi sha256 sebelum dipakai) |
| Toolchain | ECJ 3.33.0 · r8 8.5.35 (D8 → `classes2.dex`) · uber-apk-signer 1.3.0 · JDK 21 · android.jar API 33 (mirror Sable — sandbox baru, toolchain lama hilang) — tanpa apktool (patch langsung ke APK 0.3.1) |
| **Koreksi owner (uji device 0.3.1)** | **(1) "website pindah mode itu salah cuma menuhin tempat"** → switch mode DIHAPUS dari SEMUA halaman web: dasbor 4000 tanpa kartu MODE SERVER, halaman root port game 8080 jadi status mini 3 baris tanpa tombol/JavaScript; ganti mode cukup lewat **tombol melayang** di layar game (satu tempat); endpoint `POST /api/mode` dihapus dari kedua port; **(2) baris "memuar…" (memuat…) tersisa di atas kotak log** → placeholder kini dibuang otomatis saat jawaban `/api/logs` pertama masuk; bila dasbor gagal dihubungi muncul pesan jelas ("gagal memuat status/log — dasbor tidak bisa dihubungi"), bukan teks memuat menggantung; **(3) narasi mode di log dirampingkan** → `Mode: LOCAL — permintaan game dilayani dari www/ (mirror server asli)` · `Port game hidup di 8080 (mode LOCAL) — endpoint client menunjuk ke sini` · `Tombol melayang tampil — GESER = pindah posisi · TEKAN = ganti mode` (tanpa iklan halaman 8080) |
| Perubahan dasbor 4000 | Kartu mode besar dihapus; mode = badge `MODE LOCAL/ONLINE` baca-saja di header (hijau/amber); catatan kecil di kartu port game: "Ganti mode = lewat tombol melayang di layar game … Website sengaja tanpa tombol mode"; footer dirampingkan |
| Verifikasi (sandbox) | ✅ kompilasi ECJ bersih (55 kelas); ✅ CRC diff penuh 3.701 entri: **hanya** `classes2.dex` + `assets/server/dashboard.html` (+ META-INF hasil sign ulang) yang berubah — dex game `281225eb`, manifest, `resources.arsc`, `QHinfo`, config, seluruh mirror www/ IDENTIK; ✅ dasbor & halaman mini 8080 diuji di browser nyata (stub API): badge `MODE LOCAL` hijau, **0** elemen kartu mode, baris pertama kotak log = event nyata (bukan "memuat…"), halaman mini 8080 = 0 elemen interaktif, tata letak desktop + mobile 390 px + dark mode rapi, tanpa error JS |
| Perubahan client | **TIDAK ADA** — `classes.dex` game, manifest, `resources.arsc`, `QHinfo`, config endpoint: semua identik (diverifikasi CRC penuh saat build) |
| Signature | ✅ v1 + v2 + v3 — cert `CN=DB Local LSF` (keystore tetap) |
| Status | ✅ build & verifikasi level build selesai · ⏳ uji device owner (website bersih tanpa switch, log tanpa "memuat…", ganti mode lewat tombol melayang) |

**Build 0.3.1 (LOG URL ASLI + SWITCH MODE DI KEDUA WEBSITE, v3.1) — 2026-11-01 — DIGANTIKAN 0.3.2 (switch mode dari website dibatalkan owner):**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.3.1.apk` (pointer Git LFS — 163.364.781 byte) |
| SHA-256 | `252984ce190d4e2ebbb9dd9693c9ca0d48b3521c48a02d98cac5d07f7602436d` |
| versionCode / versionName | **4 / 0.1.0** (manifest TIDAK disentuh — alur PATCH; identitas versi dibawa dasbor/Release) |
| Toolchain | ECJ 3.33.0 · r8 8.5.35 (D8 → `classes2.dex`) · uber-apk-signer 1.3.0 · JDK 21 (tanpa apktool — patch langsung ke APK release 0.1.0 v2.8) |
| **Koreksi owner (uji device 0.3.0)** | **(1) "log ini gak jelas sih, jangan di tulis LOCAL tapi emang request asli jika 127.0.0.1:8080 ya tulis itu"** → semua baris request kini memuat URL utuh: `GET http://127.0.0.1:8080/bs/upgrade/all.zip → 200 dari www/ · 18,5 MB · 1 ms`; mode ONLINE menulis `→ HTTP 200 dari server asli · diteruskan ke https://dragonh5cdn.popoh5.com/…`; 404 normal (`com_db_local.bin` — di server asli juga tidak ada) dilog `ℹ` tenang & jelas, bukan `⚠`; **(2) "Tidak ada mode switch di dalam website"** → switch kini ada di KEDUA website: halaman port game `http://127.0.0.1:8080` (dua tombol besar + `POST /api/mode` + tautan dasbor) dan dasbor 4000 (kartu **MODE SERVER — TEKAN UNTUK GANTI** paling atas, dua tombol besar, mode aktif ✓ hijau, konfirmasi dua-tahap); **(3) log banjir** → dedupe request kembar (status+path sama <10 dtk dienyuh, dilaporkan `+N request kembar`), log "tombol melayang tampil" hanya sekali per proses (dulu 6× karena game Egret pause/resume activity berulang saat boot) |
| Verifikasi (sandbox) | ✅ harness JVM: 6 request → hanya 3 baris log (kembar ditenyapkan); baris log memuat URL asli & tanpa label LOCAL/ONLINE; halaman port game ter-render (2.773 B); ✅ dasbor & halaman port game diuji di browser nyata (mock API): switch dua-tahap berfungsi (mode berubah), tampilan desktop + mobile (390 px) rapi, tabel request menampilkan URL asli, tanpa error JS |
| Perubahan client | **TIDAK ADA** — `classes.dex` game, manifest, `resources.arsc`, `QHinfo`, config endpoint: semua identik dasar 0.1.0 (diverifikasi CRC saat build) |
| Signature | ✅ v1 + v2 + v3 — cert `CN=DB Local LSF` (keystore tetap) |
| Status | ✅ build & verifikasi level build selesai · ⏳ uji device owner (log URL asli, switch di kedua website, log tidak banjir) |

**Build 0.3.0 (MIRROR PENUH + FLOATING DRAGGABLE, v3.0) — 2026-10-31:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.3.0.apk` (pointer Git LFS — 163.356.589 byte) + **asset Release GitHub `v0.3.0`** |
| SHA-256 | `0af16244981400efa7d122280036def9f73f8db2e170219d7d27a1c25731050e` |
| versionCode / versionName | **4 / 0.1.0** (manifest TIDAK disentuh — alur PATCH; identitas versi dibawa dasbor/Release) |
| Toolchain | ECJ 3.33.0 · r8 8.5.35 (D8 → `classes2.dex`) · uber-apk-signer 1.3.0 · JDK 21 (tanpa apktool — patch langsung ke APK release 0.1.0 v2.8) |
| **Fitur baru (v3.0 — 3 koreksi owner)** | **(1) FLOATING BISA DIGESER** — drag bebas (ambang touch-slop membedakan geser/tekan), clamp layar, posisi tersimpan `server.json` (`toggleX/toggleY`), tap → panel mode konfirmasi dua-tahap, hanya tampil saat game di depan (`ActivityLifecycleCallbacks`); **(2) MIRROR PENUH** — 31 file server asli (§2.9; `assets/server/www/`, versi payload `3`) diekstrak otomatis → www/; penanda pindah ke `config/www.stamp`; **(3) ANTI-SAMPAH** — `Cleaner.java` whitelist `config/www/logs/tmp` tiap boot; `ApkReader` + endpoint `/api/apk/*`, `/api/analyze`, `/api/monitor/run` DIHAPUS; `status.json` pindah ke `config/` |
| Fitur baru (analisis) | Setting bin didekripsi (XOR `DragonBall`) → host **dragonh5cdn.popoh5.com**; semua file fisik diunduh & dibawa APK; `GameServer` LOCAL me-rewrite field `url`+`update` setting bin → `127.0.0.1:8080` (`SettingBin.java`, cache per file+port); peta passthrough `/bs/` → dragonh5cdn; **self-seed**: file yang belum ada di mirror diambil sekali dari server asli & disimpan ke www/ |
| Perubahan client | **TIDAK ADA** — `classes.dex` game CRC `281225eb`, manifest `2486fd1c`, `resources.arsc` `c6b5e22c`, `QHinfo` `af014748`, config `62eab0f4`/`1cc6e06c` — SEMUA identik dengan dasar 0.1.0 |
| Signature | ✅ v1 + v2 + v3 — cert `CN=DB Local LSF` (keystore tetap) |
| Verifikasi isi | ✅ classes2.dex baru 99.272 B; dashboard 16.449 B; mirror www/ 31 entri Stored; payload lama terhapus; ukuran APK 155,8 MB (±48 MB = zip mirror, wajar) |
| Status | ✅ build & verifikasi level build selesai · ⏳ uji device owner (geser floating, switch mode, mirror www/, dasbor baru) |

**Build 0.1.0 (PAYLOAD OTOMATIS, v2.8) — 2026-10-31 — DASAR BUILD 0.3.0:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.1.0.apk` (pointer Git LFS — 105.104.870 byte) + **asset Release GitHub `v0.1.0` (diganti)** |
| SHA-256 | `911c15fe3991f747c263bba2b4014bffb50f791b0ed6567ad329c5736f9f045d` |
| versionCode / versionName | **4** / **0.1.0** (manifest TIDAK disentuh — CRC identik dengan v2.7) |
| Toolchain | ECJ 3.33.0 · r8 8.5.35 (D8 → `classes2.dex`) · uber-apk-signer 1.3.0 · JDK 21 (tanpa apktool — patch langsung ke APK release v2.7) |
| **Fitur baru (v2.8 — koreksi owner)** | **(1) Payload config asli dibawa APK** (`assets/server/payload/` — 4 file): `base.version`=`110`, `resource.version`=`11220` (keduanya byte-identik `assets/game/` bawaan), `upgrade.json`/`size.json`=`{}`; **(2) `Payload.java`** — ekstraksi OTOMATIS `assets/server/payload/` → `www/` oleh BootProvider saat pertama jalan; penanda `www/.payload.txt` mengatur ekstraksi ulang (versi naik / file hilang / clear data); tanpa langkah manual; **(3) `GameServer` LOCAL** melayani www/ hasil payload; 404 untuk path di luar payload (fallback dex); **(4) dasbor** baris baru "Payload www/ N file" + semua narasi "ditaruh manual" diganti ekstraksi otomatis; **(5) `build.sh` baru** — alur PATCH (tanpa apktool): ECJ+D8 → classes2.dex baru → ganti dashboard → tambah payload → buang sign lama → zipalign+sign keystore tetap |
| Temuan penting | **Server asli MATI**: configus → HTTP 404 `NoSuchKey` untuk SEMUA key (`base.version`, `resource.version`, `upgrade.json`, `size.json`), dragon → tak terjangkau (HTTP 000). Mode ONLINE kehilangan sumber jawaban → mode LOCAL (payload) kini jalur utama |
| Perubahan client | **TIDAK ADA** — dex game CRC `281225eb` & manifest CRC `2486fd1c` identik dengan v2.7; endpoint config CRC identik (`62eab0f4` / `1cc6e06c`) |
| Signature | ✅ v1 + v2 + v3 — cert `CN=DB Local LSF` (keystore tetap, SHA-256 `FD:4B:…:CF:D7`) |
| Verifikasi isi | ✅ 3.674 entri (3670 + 4 payload); `classes2.dex` baru 88.676 B (13 kelas, + `Payload`); dashboard 23.249 B; payload Stored 4 entri |
| Status | ✅ build & verifikasi level build selesai · ⏳ uji device owner (payload otomatis, switch mode, log) |

**Build 0.1.0 (ARSITEKTUR TERKOREKSI, v2.7) — 2026-10-31 — SEJARAH (www/ masih manual, digantikan v2.8):**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.1.0.apk` (pointer Git LFS — 105.100.360 byte) + **asset Release GitHub `v0.1.0` (diganti)** |
| SHA-256 | `0d5cfa7cfd241dbf88ddfafd547017eda6e0cdbcdcedd6402a3ac0bd6f435114` |
| versionCode / versionName | **4** / **0.1.0** (bump code agar bisa install-over 0.1.0 lama (2) & 0.2.0 tertarik (3)) |
| Toolchain | apktool 3.0.3 · ECJ 3.33.0 · r8 8.5.35 (D8 → `classes2.dex`) · uber-apk-signer 1.3.0 · JDK 21 |
| **Fitur baru (arsitektur terkoreksi)** | **(1) Port game terpisah 8080** (`GameServer`) — dasbor 4000 murni debug; **(2) dua mode** di `config/server.json`: LOCAL = layani `www/` (isi manual, 404 bila belum ada), **ONLINE (default) = passthrough** ke server asli (dragon/configus + fallback COS, query `?rnd=` diteruskan apa adanya); **(3) tombol melayang switch mode** (`FloatingToggle`, overlay, `L`/`O`) → tulis `server.json` → **restart otomatis** (`RestartHelper` via AlarmManager) → BootProvider menyalakan ulang; **(4) endpoint client diedit** → `127.0.0.1:8080` (EntryPoint1/2 config_BS + EntryPoint1 config.properties, termasuk varian `_debug`; `QHinfo` TIDAK diubah); **(5) log tenang** — polling dasbor tak dicatat, monitor catat saat kondisi berubah + denyut 10 mnt; **(6) www/ skeleton** dibuat saat boot + `base.version`/`resource.version` = `110` (isi LAINNYA ditaruh manual sesuai aturan owner) |
| Perubahan client | **HANYA nilai endpoint** (4 baris Base64 di 2 file config assets) + `classes2.dex` baru (12 kelas) + `assets/server/dashboard.html` + 1 baris provider + versi. **Dex game byte-identical** — SHA256 `classes.dex` = `9c60ac1c…5d1e28e` = original |
| Signature | ✅ v1 + v2 + v3 — cert `CN=DB Local LSF` (keystore tetap, SHA-256 `FD:4B:…:CF:D7`) |
| Verifikasi isi | ✅ 3.670 entri; manifest terbaca `com.db.local` + `BootProvider` + versionName `0.1.0`; endpoint lokal terbaca di assets; kelas baru (`GameServer`, `FloatingToggle`, `RestartHelper`, `Config`) ada di `classes2.dex` |
| Status | ✅ build & verifikasi level build selesai · ⏳ uji device owner (switch mode, isi www/, log) |

**Build 0.1.0 (REBUILT, v2.4) — 2026-10-30 — SEJARAH (arsitektur lama: hanya dasbor, endpoint belum diubah):**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.1.0.apk` (pointer Git LFS — 105.026.632 byte) + **asset Release GitHub `v0.1.0`** |
| SHA-256 | `c5d6a5d4284aab0ad7995be6b45d8312a50d1280b634c5467e68adb83720fc12` |
| versionCode / versionName | **2** / **0.1.0** (bump code agar bisa install-over build round-trip lama, signature sama) |
| Toolchain | apktool 3.0.3 · ECJ 3.33.0 (javac tidak tersedia di workspace) · r8 8.5.35 (D8, `classes2.dex`) · uber-apk-signer 1.3.0 · JDK 21 |
| **Fitur baru** | **Local server embedded (M1):** NanoHTTPD 2.3.1 bind `127.0.0.1:4000` · **auto-start via `BootProvider`** (provider di-init Android SEBELUM `Application.onCreate` → pasti jalan duluan) · dasbor `http://127.0.0.1:4000` · log bahasa manusia (▶ ◀ ⚠ ✔ ℹ, satu event satu kalimat) · **monitoring dua sisi** (server lokal + server asli: dragon/configus/qsdk, TCP-connect tiap 60 dtk) · **APK Reader** (list/pratinjau/unduh isi APK) · **analisis config** (config.properties, config_BS.properties, QHinfo + dekode Base64 otomatis) · copy-first (tombol salin di semua nilai) · root folder `Android/data/com.db.local/server/{logs,config,data,responses}` + `status.json` |
| Perubahan vs baseline | **PENAMBAHAN MURNI — kode game tidak disentuh:** (1) `classes2.dex` baru (8 kelas `com.db.local.server.*` + NanoHTTPD), dex original di-carry byte-identical; (2) manifest: +1 baris `<provider com.db.local.server.boot>` (exported=false, authorities unik); (3) asset baru `assets/server/dashboard.html`; (4) versionName 0.1.0 / versionCode 2. Tidak ada resource game yang berubah |
| Signature | ✅ v1 + v2 + v3 verified — cert `CN=DB Local LSF` (keystore tetap, SHA-256 `FD:4B:…:CF:D7`) |
| Verifikasi isi | ✅ 3.667 entri = 3.665 baseline + 2 tambahan (`classes2.dex`, `assets/server/dashboard.html`); `BootProvider` & authority terbaca di manifest AXML; `classes2.dex` 70.660 byte |
| Fail-safe | Semua kode server dibungkus try/catch — server gagal ⟹ game tetap normal; dasbor hanya melayani dirinya, bukan traffic game |
| Status | ✅ build & verifikasi level build selesai · ✅ terbit di Release GitHub `v0.1.0` · ⏳ uji install/boot/dasbor di device owner |

**Build 0.1.0 (round-trip L0, v2.3) — DICABUT sebagai deliverable, disimpan sebagai sejarah:**

| Item | Nilai |
|---|---|
| Artefak | `releases/APK-LSF-0.1.0.apk` (via Git LFS — 104.993.732 byte) |
| SHA-256 | `137bdf315a953f4c8f6d874cd2bd7e3c3eda3e098ac33db4178886761e5daac8` |
| Toolchain | apktool 3.0.3 · uber-apk-signer 1.3.0 (zipalign bawaan) · JDK 21 |
| Perubahan vs baseline | **Hanya `versionName: 1.0.0 → 0.1.0`** — versionCode tetap 1, paket tetap `com.db.local`, game tidak disentuh |
| Signature | ✅ v1 + v2 + v3 verified — cert `CN=DB Local LSF, OU=LocalServer, O=db-apk, C=ID` |
| Verifikasi isi | ✅ 3.665 entri = 3.665 entri (tidak ada hilang/tambahan); 15 file beda-CRC **wajar**: manifest (versi), `classes.dex` (recompile smali), XML resource (recompile aapt2), 5 PNG ter-crunch ulang (lossless — dimensi diverifikasi identik) |
| Status | ✅ build & verifikasi level build selesai · ⏳ uji install/boot di device owner |

**Keystore tetap (wajib dipakai SEMUA build LSF — signature konsisten):**

| Item | Nilai |
|---|---|
| File | `keystore/lsf.keystore` (di-commit — agar tidak pernah berubah lintas sesi) |
| Alias / password | `lsf` / `lsf-local-2025` (sama untuk store & key) |
| Cert SHA-256 | `FD:4B:7A:95:CB:5B:96:32:28:93:97:03:8F:8C:E6:88:3C:0C:BA:10:85:C5:1D:5A:30:B1:9E:42:42:3A:CF:D7` |
| Berlaku s.d. | 22 Okt 2056 |

> Password didokumentasikan sengaja: repo = area kerja owner, reproducibility > kerahasiaan
> (APK sideload, bukan Play Store). Jika keystore berubah/hilang → build baru wajib uninstall dulu.

**Cara uji install + dasbor (owner):**
1. Siapkan **device uji** (bukan device utama). Uninstall `DB-LOCAL` original & build round-trip lama dulu bila ada —
   paket sama + signature beda → tanpa uninstall, install pasti gagal. Build baru (versionCode 2) bisa
   install-over **build round-trip lama** karena signature sama.
2. Unduh `APK-LSF-0.1.0.apk` dari **Release GitHub `v0.1.0`** (atau pointer LFS di `releases/`) →
   transfer ke device → install (izinkan "install unknown apps"; Play Protect protes → install anyway).
3. Buka game → harus boot **persis seperti original** (masih online; server asli tetap melayani).
4. Saat game/splash tampil, buka **browser HP** → `http://127.0.0.1:4000` → dasbor harus tampil:
   pill **HIDUP**, status server asli (dragon/configus/qsdk), log bahasa manusia.
   Alternatif dari PC: `adb reverse tcp:4000 tcp:4000` lalu buka `http://127.0.0.1:4000` di browser PC.
5. Cek folder `Android/data/com.db.local/server/` → `logs/human.log`, `status.json` terisi.
6. Laporkan hasilnya di percakapan → v0.1.0 ditutup, lanjut L4 (validasi bersebelahan).

**Reproducibility:** seluruh sumber server lokal ada di `local-server/` (Java + dashboard HTML + `build.sh`);
vendor `NanoHTTPD.java` di-commit (lihat `local-server/NOTICE.md`); proyek apktool hidup di `.analysis/lsf-src/`
(gitignored — dibangkitkan dari `DB-LOCAL.apk` via `apktool d -s` bila workspace reset).

**Cara uji install (build lama — sejarah):**
1. Siapkan **device uji** (bukan device utama). Uninstall `DB-LOCAL` original dulu —
   paket sama + signature beda → tanpa uninstall, install pasti gagal.
2. Unduh `releases/APK-LSF-0.1.0.apk` dari repo ini → transfer ke device → install
   (izinkan "install unknown apps"; jika Play Protect protes → install anyway).
3. Buka game → harus boot **persis seperti original** (masih online, tidak ada fitur baru).
4. Laporkan hasilnya di percakapan → status L0 ditutup, lanjut L1.

**Catatan kejadian (2026-10-30):** filesystem workspace pernah ter-reset di antara sesi —
workspace dipulihkan penuh dari GitHub (README + baseline pointer) dan baseline
`DB-LOCAL.apk` diunduh ulang via LFS lalu diverifikasi SHA-256 **cocok** dengan
`62418cc4…fe23d`. Bukti mekanisme *baseline immutable* bekerja. Pelajaran: keystore &
artefak build wajib hidup di repo (seperti sekarang), bukan hanya di workspace.

---

## 5. Keputusan Terbuka (perlu keputusan owner)

1. ~~Mekanisme intersep~~ → **DIHAPUS (v2.0)** — tidak diperlukan; kita mengedit client
   (APK LSF) secara langsung.
2. ~~Port lokal~~ → **DIPUTUSKAN (v1.2): port 4000** — satu server untuk API game + dashboard.
3. ~~Package name~~ → **DIPUTUSKAN (v2.1): tetap `com.db.local`** — tanpa rename;
   custom APK menggantikan original saat diinstall (pakai device uji dianjurkan).
4. ~~Akses dashboard~~ → **DIPUTUSKAN (v1.3): cukup `127.0.0.1:4000`**; desain copy-first.
5. **HTTPS wajib?** (tergantung hasil L1): untuk server lokal tidak jadi soal — repoint
   tinggal ganti skema ke `http://`. Pertanyaannya tinggal: endpoint HTTPS mana yang
   client harapkan formatnya (untuk replikasi di server lokal).
6. **Strategi bundle terenkripsi:** biarkan runtime asli mendekripsi (aman) vs dekripsi
   statis (fleksibel tapi perlu riset AES `libgavesec.so`) — Fase L1 yang menentukan.
7. **Penyimpanan:** SQLite (rekomendasi) vs file JSON.
8. **Library server embedded:** NanoHTTPD (ringan, murni Java — rekomendasi) vs AndServer.
9. ~~Mode transisi sebelum ambil alih~~ → **DIPUTUSKAN (v2.2): monitor/passthrough** —
   request diteruskan ke server asli & dicatat dua arah; game tetap menerima data asli.
10. ~~Port game terpisah~~ → **DIPUTUSKAN (v2.7): 8080** — dasbor 4000 murni developer;
    client menunjuk 8080.
11. ~~Isi www/~~ → **DIPUTUSKAN (v3.0): mirror PENUH server asli** (31 file, termasuk
    `all.zip`, `clientversion.json`, dst. — daftar §2.9), ekstraksi otomatis dari APK;
    file yang belum ada diisi otomatis (self-seed) saat diminta game.
12. ~~Nasib Release v0.2.0~~ → **DIPUTUSKAN (v3.0): dibiarkan sebagai histori** — tidak
    dipakai sebagai dasar apa pun (build 0.3.0 dibangun dari dasar 0.1.0).

---

## 6. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Rebuild APK (round-trip) gagal / rusak | LSF tidak bisa diinstall | L0: uji round-trip dulu tanpa perubahan apa pun sebelum injeksi |
| Signature berbeda dari original → install custom MENIMPA original (data device terhapus) | Kehilangan data pemain di device | Pakai device/emulator uji; dokumentasikan backup dulu; sign key konsisten agar update custom→custom mulus |
| Signature tak dikenal → Play Protect menolak instalasi | Install ditolak | Sign key sendiri yang konsisten + dokumentasikan langkah "install anyway" |
| Injeksi server mengubah perilaku game | Regresi di LSF | Prinsip **pure addition**: hanya menambah service; L4 bandingkan boot original vs LSF |
| Format respons bootstrap salah ditebak | Client menolak config, kalibrasi macet | L5a merekam respons ASLI via passthrough → template dibangun dari data asli, bukan tebakan |
| Mode passthrough jadi titik gagal tunggal (server lokal mati → game kehilangan koneksi) | Game online terganggu | Repoint baru dilakukan setelah stabilitas L2–L4 terbukti; foreground service + restart otomatis |
| Payload client terenkripsi (bukan JSON polos) | Server lokal harus replikasi skema | Replikasi via `libgavesec.so` (JNI bridge), bukan reimplement; riset `onepieceencrypts` cadangan |
| Port 4000 bentrok (aplikasi lain memakai port) | Server gagal bind | Hanya satu aplikasi (APK custom) yang menjalankan server; deteksi bentrok + fallback port tercatat |
| Game LSF dibuka sebelum server siap | Request pertama gagal | Server start di `Application.onCreate` — sebelum activity game; urutan terjamin |
| SDK cloud di client mem-blok boot saat mati | Client crash sebelum menyentuh server | Catat di L4/L5; solusi permanen di L6 (stub SDK) |

---

## 7. Kriteria Selesai Tahap Fondasi (Definition of Done — Track L)

1. `DB-LOCAL.apk` di repo **identik dengan baseline** — tidak pernah ditimpa.
2. APK LSF memakai **paket tetap `com.db.local`** (tanpa rename); game di dalamnya
   **tetap utama & running online** tanpa perubahan perilaku apa pun (pure addition).
3. APK LSF = game berperilaku normal **+ local server hidup di port 4000** (pajangan
   yang work), murni penambahan tanpa regresi.
4. **Server pasti jalan duluan** — dinyalakan di `Application.onCreate` sebelum game dimuat.
5. **Web Dashboard** (monitoring, logging, debugging, APK reader, copy-first) berfungsi
   sebagai alat bantu kalibrasi di `127.0.0.1:4000` — **lognya mudah dibaca manusia**
   (format §3), termasuk panel kesehatan server lokal & server asli.
6. Kontrak API bootstrap + endpoint yang sudah "diterima" client terdokumentasi §2.3.
7. Semua artefak server (log, config, data, respons) hidup di `Android/data/com.db.local/server/`.
8. Dokumen ini selalu menjadi sumber kebenaran tunggal — setiap fase selesai diperbarui di sini.

---

## 8. Aturan Kerja Workspace

- **Dokumentasi tunggal:** semua catatan/keputusan/hasil uji hanya di `README.md` ini.
- **Tidak membuat project/APK baru** — satu-satunya artefak yang diedit adalah custom dari
  `DB-LOCAL.apk` (APK LSF).
- **Paket tetap `com.db.local`** — tidak ada rename package, kapan pun.
- **Pengalaman online game = utama, tidak diubah.** Baseline `DB-LOCAL.apk` di repo
  immutable; semua eksperimen hanya pada APK LSF.
- **Server asli tetap melayani game** sampai paritas per-endpoint terbukti; mode monitor
  (passthrough) tidak pernah mengubah jawaban.
- **Port 4000 = kontrak tetap** (API game + Web Dashboard).
- **Root folder server = `Android/data/com.db.local/server/`** — log/config/data/respons hidup di sini.
- **Logging = alat bantu**, bukan produk: indikator server running & paritas dengan server asli.
- **Log dashboard = bahasa manusia** — setiap baris mudah dipahami non-programmer
  (format §3); detail teknis mentah hanya di balik expand/copy.
- `DB-LOCAL.apk` asli = baseline **immutable** (tidak pernah ditimpa); hasil bedah di
  `.analysis/` (gitignored).
- Cabang kerja: `main` untuk dokumentasi; implementasi di branch `feat/lsf-custom`.
- **Batas analisis:** fondasi infrastruktur saja. Analisis mekanik/gameplay dilarang sampai fondasi selesai.

---

## 9. Backup & Transfer (anti reset sandbox)

Kesepakatan: **GitHub = gudang utuh proyek; sandbox = meja kerja sementara.** Sandbox
di-reset terlalu sering — folder kerja di luar `/home/z/my-project` bisa lenyap kapan pun.

**Aturan:**

1. **Setiap hasil kerja langsung di-push** ke `anztdree/db-apk` — tidak menunggu sesi berakhir.
2. **Sandbox reset = bukan bencana.** Restore 1 perintah: `bash tools/restore.sh`
   (token otomatis dari `/home/z/my-project/db-apk-backup/token`, chmod 600, tidak ikut repo).
3. **Yang dibackup:**
   - `local-server/**` — semua source + assets (termasuk `dashboard.html` terbit).
   - `releases/*.apk` — APK terbit (LFS).
   - `tools/*.jar` — android.jar, r8.jar (9.5.32, Google Maven), ecj-3.33.0.jar
     (Maven Central), uber-apk-signer.jar, sqlite-jdbc.jar (LFS).
   - `dist/<versi>/` — bukti build terbit (classes2.dex + SHA256).
   - `docs/build-scripts/` — snapshot skrip build versi (0.5.7, 0.5.8).
4. **Yang tidak dibackup (re-downloadable, terdokumentasi):**
   - jadx 1.5.0 (2024-04-20) — github.com/skylot/jadx/releases/tag/v1.5.0.
   - base.zip/all.zip mirror — `tools/snapshot.sh` (SHA256 ter-pin, §2.9).
5. **Bukti verifikasi push** wajib: nama commit + output push + ukuran/HTTP asset di GitHub.
   Klaim "sudah di-push" tanpa bukti terima = laporan keliru.
