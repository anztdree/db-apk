# db-apk — Dokumentasi Rencana: Standalone Local Server

> **Status dokumen:** Fondasi & Implementasi (**v4.0 — build 0.4.0**) — **LOGIN LOKAL** (tahap SDK dilokalkan: server login lokal port 8610 memakai protokol engine.io v3 + socket.io v2 PERSIS client game — guest/login/daftar server dilayani dari data lokal; endpoint login di setting bin & serversetting.json ditulis-ulang ke 127.0.0.1 di mode LOCAL; dasbor log baru: font 11px + baris terpisah garis). Di bawahnya (v3.2): **WEBSITE TANPA SWITCH MODE** (ganti mode cukup tombol melayang; `POST /api/mode` dihapus). Di bawahnya (v3.0): **MIRROR PENUH server asli** (31 file, ±56 MB) dibawa APK (`assets/server/www/`) & diekstrak OTOMATIS ke www/; tombol melayang BISA DIGESER; folder server anti-sampah
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

**Build 0.4.0 (LOGIN LOKAL, v4.0) — 2026-11-02 — BUILD TERKINI:**

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
