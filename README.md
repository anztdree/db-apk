# db-apk — Dokumentasi Rencana: Standalone Local Server

> **Status dokumen:** Fondasi & Implementasi (v2.7) — **ARSITEKTUR TERKOREKSI + build 0.1.0 dibangun ulang**: port game terpisah (8080) dengan www/ isi manual + passthrough ONLINE, switch mode LOCAL/ONLINE via tombol melayang, endpoint client diarahkan ke lokal; build 0.2.0 (cermin otomatis) DITARIK
> **Lingkup:** Fondasi infrastruktur client-server. **Analisis gameplay sengaja dikecualikan.**
> **Tanggal:** 2026-10-31
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

### 2.4 Status Kesehatan Endpoint (hasil probe, 2026-10-30)

| Endpoint | Status | Catatan |
|---|---|---|
| `dragon.sjmobilegame.com:82` | ❌ **MATI** | Koneksi gagal total (DNS/timeout) |
| `configus.sjmobilegame.com/bs/db/android` | ⚠️ Domain hidup, **404** | Path konfigurasi sudah dihapus |
| Tencent COS (fallback) | ⚠️ Bucket hidup, **404** | Path konfigurasi sudah dihapus |
| `qsdk.t4game.com` | ❓ Belum diprobe mendalam | Server SDK pihak ketiga |

**Kesimpulan:** rantai bootstrap sudah terputus. APK saat ini **tidak mungkin** mendapatkan
konfigurasi server game → sesuai prediksi "game akan segera mati".

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
| **Logging** | Semua request tercetak **dalam bahasa manusia yang mudah dibaca** (format di bawah) — live stream + riwayat SQLite; detail mentah tetap tersedia via expand. Endpoint tak dikenal otomatis ter-log dengan peringatan jelas |
| **Monitoring** | Kesehatan **dua sisi** dalam bahasa manusia: server lokal (hidup/uptime/jumlah request/gagal) + **server asli** (dicek berkala: bisa dihubungi / mati, latensi) — tanpa menyentuh traffic game |
| **Debugging** | Detail request/response, replay request, mock override per-endpoint (edit respons on-the-fly, toggle on/off), template respons endpoint baru |
| **APK Reader** | Buka APK dari device: tree assets, manifest, `config.properties` + auto-decode Base64, strings/dex grep endpoint |
| **Copy / Export (copy-first)** | Salin **satu klik** semua data: raw body, headers, `curl` siap-replay, config ter-decode, **timeline alur** per sesi boot → ditempel ke asisten untuk dianalisis |

**Posisi logging & dashboard:** **alat bantu**, bukan produk. Log = indikator bahwa
(1) server lokal running dan (2) jawabannya mirip server asli. Jangan gold-plate —
produk utama = **server lokal yang setara**.

**Format log dashboard — WAJIB bahasa manusia (v2.1, diperhatikan dengan teliti):**

Setiap baris log ditulis seperti kalimat yang bisa dipahami siapa pun — siapa meminta
apa, server menjawab apa, seberapa lama. Detail teknis (header, body raw, curl) tetap
lengkap tapi ditaruh di balik expand + tombol copy (copy-first tetap berlaku).

```
14:32:01  ▶ Game meminta konfigurasi bootstrap     (GET /bs/db/android)
          ◀ Dijawab 200 OK · 12 ms · 1,4 KB · sumber: mock bs_android.json
14:32:05  ▶ Game membuka pengaturan native         (GET /lzceshi/native_setting/Android)
          ◀ Dijawab 200 OK · 8 ms · 0,6 KB · sumber: template bawaan
14:32:09  ⚠ Endpoint belum dikenal: POST /game/login — belum ada handler, dijawab 404
14:32:10  ✔ Server lokal siap di port 4000 — menunggu panggilan game
```

Aturan penulisan log:
1. **Satu event = satu kalimat manusia** — "siapa minta apa → jawab apa · berapa lama".
2. Simbol visual konsisten: `▶` permintaan masuk · `◀` jawaban keluar · `⚠` perlu
   perhatian · `✔` sehat/selesai.
3. Angka ramah manusia: waktu lokal, ukuran dalam KB, durasi dalam ms.
4. Baris `⚠` wajib menjelaskan **apa yang kurang** dan **langkah berikutnya**
   (mis. "belum ada handler → buat mock dari dashboard").
5. Panel status di atas dashboard juga kalimat manusia, mis.:
   `Server lokal HIDUP sejak 14:30 · sudah menjawab 37 permintaan · 0 gagal · port 4000`.

**Root folder server (tetap sesuai v1.4):**

```
Android/data/com.db.local/server/
├── config/        ← server.json (mode local/online, gamePort 8080, debugPort 4000)
├── logs/          ← log bahasa manusia + status server (indikator hidup)
├── tmp/
└── www/           ← ISI config ASLI ditaruh MANUAL (struktur = path URL server asli)
    ├── bs/db/android/                    ← cermin configus.sjmobilegame.com/bs/db/android
    │   ├── base.version                  ← "110" (versi bawaan → game skip hot-update)
    │   ├── resource.version              ← "110"
    │   └── (setting_BS_Android.bin, upgrade.json, size.json, … — taruh manual)
    └── lzceshi/native_setting/Android/   ← cermin dragon.sjmobilegame.com:82/lzceshi
```

Aturan pemilik (v2.7): **bukan sistem yang menyalin** — isi server asli disalin **manual**
ke `www/` (bahan: hasil probe/unduhan jawaban asli, atau rekonstruksi minimal).
Server lokal hanya **melayani file yang ada**; yang belum ada dijawab 404 dan game
melanjutkan boot (terbukti dari analisis §2.8 — `Server base.version is Not Exist`).
Mode ONLINE (default) = permintaan diteruskan ke server asli (passthrough, fallback
COS otomatis untuk path `/bs/db/android`), jadi game tetap berperilaku online persis
seperti aslinya sampai pemilik siap berpindah ke LOCAL.

**Dua port, dua peran (v2.7):**

| Port | Untuk siapa | Fungsi |
|---|---|---|
| **4000** (debugPort) | Developer | Dasbor debug + log + APK Reader — game tidak tahu port ini |
| **8080** (gamePort) | Game client | Melayani kontrak server: LOCAL = www/, ONLINE = passthrough |

**Switch mode (v2.7):** tombol melayang bulat di atas game (`L`/`O`). Ditekan → mode
ditulis ke `config/server.json` → aplikasi direstart via AlarmManager → BootProvider
menyalakan server lagi → game membaca config sesuai mode. Izin overlay
(SYSTEM_ALERT_WINDOW) sudah ada di manifest original; di Android 6+ pemilik
mengaktifkannya sekali di Setelan → “Tampil di atas aplikasi lain”.

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
| **0.1.0** | **(v2.7) + port game terpisah 8080 (www/ manual LOCAL / passthrough ONLINE) + switch mode tombol melayang + restart otomatis + endpoint client → 127.0.0.1:8080 + log tenang** | **L1–L2a ✓ (build — v2.7)** |
| 0.1.x | Uji device: switch mode, isi www/ manual, pantau log | L4b |
| 0.3.0 | Passthrough penuh + rekam jawaban ASLI dua arah (M2) | L5a |
| 0.4.0 | Ambil alih bertahap per-endpoint (M3) | L5b |
| 1.0.0 | Fondasi lulus penuh — paritas endpoint terbukti | L5b ✓ |
| 1.1.0 | Persistensi state (SQLite) + stub SDK cloud | L6 awal |
| 2.0.0 | Standalone penuh tanpa internet | L6 |

**Build 0.1.0 (ARSITEKTUR TERKOREKSI, v2.7) — 2026-10-31 — SUPERSEDE build v2.4 di bawah:**

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
