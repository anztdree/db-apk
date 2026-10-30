# db-apk — Dokumentasi Rencana: Standalone Local Server

> **Status dokumen:** Analisis Fondasi & Rencana (v2.0) — *rencana saja, belum ada implementasi*
> **Lingkup:** Fondasi infrastruktur client-server. **Analisis gameplay sengaja dikecualikan.**
> **Tanggal:** 2026-10-30
> **Riwayat strategi:**
> - v1.1 — original APK tidak disentuh; server lokal jadi APK pendamping terpisah.
> - v1.2 — port 4000 terkunci (API game + Web Dashboard); server wajib jalan duluan.
> - v1.3 — akses dashboard cukup `127.0.0.1:4000`; desain copy-first.
> - v1.4 — root folder `Android/data/<paket>/server/`; logging = alat bantu, bukan produk.
> - **v2.0 — TIDAK ada APK baru.** Local server **di-embed langsung ke APK LSF**
>   (hasil custom dari `DB-LOCAL.apk`). Original tetap **utama & running online**.
>   Server lokal awalnya **"pajangan tapi work"** — hidup & terbukti, belum mengambil alih.

---

## 1. Ringkasan Proyek

Game ini adalah aplikasi Android berbasis **Egret Engine (H5/TypeScript)** yang dibungkus
runtime native (shell APK). Saat ini seluruh konfigurasi & layanan bergantung pada server
remote pihak ketiga yang **sudah/segera mati**.

**Tujuan akhir:** menjadikan APK **standalone & berjalan lokal (offline)**.
Prinsip yang disepakati (v2.0):

- **Tidak membuat project/APK baru.** APK yang kita edit = **APK LSF**, hasil custom
  langsung dari `DB-LOCAL.apk` (decompile → tambah local server → rebuild).
- **Hanya DITAMBAH local server** di dalam APK LSF — game client di dalamnya tidak diubah.
- Dua APK hidup **bersebelahan** di device: **original (`com.db.local`) tetap utama —
  running online** seperti semula; APK LSF = versi pengembangan (game + server embedded).
- Local server mula-mula **pajangan yang work**: jalan di port 4000, dashboard hidup,
  terbukti setara — tapi belum dihubungkan ke game (endpoint belum diganti).
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

## 3. Konsep Solusi (v2.0): APK LSF — Custom dari DB-LOCAL.apk + Server Embedded

**Satu sumber, dua artefak.** `DB-LOCAL.apk` di-decompile → **ditambah local server**
(service di dalam aplikasi) → rebuild sebagai **APK LSF** dengan package berbeda
agar bisa ter-install **bersebelahan** dengan original.

```
┌── Device Android ────────────────────────────────────────────────────────────┐
│                                                                              │
│  ┌─ APK ORIGINAL (com.db.local) ──┐   ┌─ APK LSF (com.db.local.lsf) ───────┐│
│  │  ★ UTAMA — running online ★    │   │  Game Client (tidak diubah,        ││
│  │  • tidak pernah diedit         │   │    masih online seperti aslinya)   ││
│  │  • pemain utama / oracle uji   │   │  ➕ Local Server — port 4000       ││
│  └────────────────────────────────┘   │    • API game (route stub dulu)    ││
│                                       │    • Web Dashboard (pajangan work) ││
│                                       │    • start di Application.onCreate ││
│                                       │      → PASTI jalan duluan          ││
│                                       │  Root folder:                      ││
│                                       │  Android/data/com.db.local.lsf/    ││
│                                       │    server/                         ││
│                                       └────────────────────────────────────┘│
│  Browser (device/PC via adb) ──► http://127.0.0.1:4000 → Dashboard LSF       │
└────────────────────────────────────────────────────────────────────────────────┘
```

**Kenapa pendekatan ini (v2.0):**

1. **Tidak ada APK/URL-intersep baru** — server tinggal di dalam APK yang kita pegang;
   saat waktunya tiba, endpoint tinggal di-repoint di file config milik APK LSF sendiri.
2. **Urutan start otomatis terjamin** — server dinyalakan di `Application.onCreate`
   sebelum activity game dimuat; tidak mungkin game "kelebihan" server.
3. **Original tetap utama** — running online seperti biasa, tak tersentuh, jadi oracle.
4. **Server = "pajangan tapi work"** di awal — hidup, dashboard jalan, terbukti setara;
   repointing endpoint baru dilakukan setelah paritas terbukti.

**Web Dashboard di port 4000 (alat bantu — mission control kalibrasi):**

| Fitur | Isi |
|---|---|
| **Logging** | Semua request yang masuk ke server (path, method, header, body, status, durasi) — live stream + riwayat SQLite. Endpoint tak dikenal otomatis ter-log |
| **Debugging** | Detail request/response, replay request, mock override per-endpoint (edit respons on-the-fly, toggle on/off), template respons endpoint baru |
| **APK Reader** | Buka APK dari device: tree assets, manifest, `config.properties` + auto-decode Base64, strings/dex grep endpoint |
| **Copy / Export (copy-first)** | Salin **satu klik** semua data: raw body, headers, `curl` siap-replay, config ter-decode, **timeline alur** per sesi boot → ditempel ke asisten untuk dianalisis |

**Posisi logging & dashboard:** **alat bantu**, bukan produk. Log = indikator bahwa
(1) server lokal running dan (2) jawabannya mirip server asli. Jangan gold-plate —
produk utama = **server lokal yang setara**.

**Root folder server (tetap sesuai v1.4):**

```
Android/data/com.db.local.lsf/server/
├── logs/          ← log request/response + status server (indikator hidup)
├── config/        ← konfigurasi server + mock/override respons
├── data/          ← SQLite (state)
└── responses/     ← template respons statis per-endpoint
```

Keuntungan: mudah di-backup/diintip dari PC (USB), terhapus bersih saat uninstall,
tanpa izin storage tambahan (folder own-app).

---

## 4. Rencana Tahap Fondasi (v2.0 — Track L: Custom APK LSF)

> Setiap fase punya keluaran (deliverable) & kriteria selesai. Tidak ada fase gameplay di sini.
> Semua pekerjaan dilakukan pada **salinan** APK; `DB-LOCAL.apk` di repo = baseline immutable.

#### Fase L0 — Toolchain & Custom Dasar (round-trip)
- [ ] Siapkan toolchain: `apktool` (decompile/rebuild), `jadx` (baca Java),
      `apksigner` + `zipalign`, keystore sendiri, emulator/device uji.
- [ ] Uji **round-trip**: decompile `DB-LOCAL.apk` → ganti package ke
      `com.db.local.lsf` (agar bisa bersebelahan dengan original) → rebuild → sign
      → install → pastikan game boot **persis seperti aslinya** (masih online, tak ada perilaku baru).
- **Deliverable:** `APK-LSF-0.apk` (custom dasar tanpa server) + catatan uji.
- **Kriteria selesai:** APK LSF terinstall **bersebelahan** dengan original;
      keduanya jalan normal; tidak ada regresi.

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
- [ ] **Root folder:** `Android/data/com.db.local.lsf/server/` (logs/, config/, data/, responses/).
- [ ] Route stub untuk endpoint bootstrap (hasil L1); SQLite untuk log + state.
- [ ] Server listen `127.0.0.1:4000` (tidak terekspos jaringan luar).
- **Deliverable:** `APK-LSF-1.apk` (game + server hidup di dalam satu aplikasi).
- **Kriteria selesai:** dari device, `curl http://127.0.0.1:4000/bs/db/android`
      mengembalikan respons sesuai kontrak Fase L1; log muncul di root folder.

#### Fase L3 — Web Dashboard ("pajangan yang work")
> *Alat bantu — cukup lengkap, jangan gold-plate. Log = indikator server hidup & setara.*
- [ ] **Logging:** capture semua request → SQLite + live stream ke browser.
- [ ] **Debugging:** detail viewer, replay, mock override per-endpoint (edit + toggle).
- [ ] **APK Reader:** tree assets, manifest, `config.properties` + decode Base64 otomatis,
      grep strings/dex untuk endpoint.
- [ ] **Copy-first:** tombol salin satu klik — raw body, headers, `curl` siap-replay,
      config ter-decode, timeline alur per sesi boot.
- **Deliverable:** dashboard hidup di `http://127.0.0.1:4000` (browser device,
      atau PC via `adb forward tcp:4000 tcp:4000`).
- **Kriteria selesai:** satu siklus kalibrasi bisa dijalankan penuh dari dashboard
      (lihat request → edit mock → replay → lihat hasil) tanpa tools eksternal.

#### Fase L4 — Validasi Bersebelahan (original utama online, LSF pajangan)
- [ ] Install original + APK LSF bersebelahan di satu device.
- [ ] Buka **original** → main online seperti biasa (status: utama, tak tersentuh).
- [ ] Buka **APK LSF** → game berjalan normal seperti aslinya (masih online),
      DAN server lokal hidup otomatis (terbukti via dashboard + log di root folder).
- [ ] Bandingkan perilaku boot keduanya → pastikan injeksi server **tidak mengubah**
      perilaku game sama sekali (pure addition).
- **Deliverable:** catatan uji side-by-side + bukti server hidup (screenshot/log).
- **Kriteria selesai:** original = utama online normal; LSF = game normal + server
      "pajangan yang work" di port 4000.

> ── Batas fondasi ── Fase berikut dieksekusi setelah L0–L4 lulus & owner setuju ──

#### Fase L5 — Repointing Endpoint (di APK LSF saja, kalibrasi)
- [ ] Ubah endpoint di `assets/config*.properties` **milik APK LSF**
      (Base64 → `http://127.0.0.1:4000/...`); original tetap tidak disentuh.
- [ ] Oracle loop: amati request game LSF di dashboard → lengkapi/mock respons →
      replay → ulangi sampai client lolos rantai bootstrap & API berikutnya.
- [ ] Setiap kemajuan dicatat di §2.3 (endpoint + format yang sudah "diterima" client).
- **Deliverable:** `APK-LSF-2.apk` + log kalibrasi.
- **Kriteria selesai:** game di APK LSF lolos seluruh rantai bootstrap dari server lokal
      (dicatat sebatas protokol, bukan gameplay-nya).

#### Fase L6 — Hardening & Standalone (APK LSF)
- [ ] Stub SDK cloud di LSF bila mengganggu (Billing/GMS/FB/Adjust) → no-op aman.
- [ ] Persistensi state (SQLite solid); uji reboot, kill/restart, **mode pesawat penuh**.
- **Deliverable:** `APK-LSF-3.apk` (standalone) + catatan uji di dokumen ini.
- **Kriteria selesai:** APK LSF berdiri sendiri tanpa internet; original tetap utama online.

---

## 5. Keputusan Terbuka (perlu keputusan owner)

1. ~~Mekanisme intersep~~ → **DIHAPUS (v2.0)** — tidak diperlukan; kita mengedit client
   (APK LSF) secara langsung.
2. ~~Port lokal~~ → **DIPUTUSKAN (v1.2): port 4000** — satu server untuk API game + dashboard.
3. **Package name APK LSF:** usulan `com.db.local.lsf` (agar bisa bersebelahan dengan
   original `com.db.local`). Root folder mengikuti: `Android/data/com.db.local.lsf/server/`.
4. ~~Akses dashboard~~ → **DIPUTUSKAN (v1.3): cukup `127.0.0.1:4000`**; desain copy-first.
5. **HTTPS wajib?** (tergantung hasil L1): untuk server lokal tidak jadi soal — repoint
   tinggal ganti skema ke `http://`. Pertanyaannya tinggal: endpoint HTTPS mana yang
   client harapkan formatnya (untuk replikasi di server lokal).
6. **Strategi bundle terenkripsi:** biarkan runtime asli mendekripsi (aman) vs dekripsi
   statis (fleksibel tapi perlu riset AES `libgavesec.so`) — Fase L1 yang menentukan.
7. **Penyimpanan:** SQLite (rekomendasi) vs file JSON.
8. **Library server embedded:** NanoHTTPD (ringan, murni Java — rekomendasi) vs AndServer.

---

## 6. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Rebuild APK (round-trip) gagal / rusak | LSF tidak bisa diinstall | L0: uji round-trip dulu tanpa perubahan apa pun sebelum injeksi |
| Package rename memicu bug resource/manifest | Game LSF crash saat boot | L0: verifikasi boot penuh sebelum lanjut; rollback ke baseline |
| Signature berubah → Play Protect/instalasi | Install ditolak | Different-package + sign key sendiri; dokumentasikan langkah install |
| Injeksi server mengubah perilaku game | Regresi di LSF | Prinsip **pure addition**: hanya menambah service; L4 bandingkan boot original vs LSF |
| Format respons bootstrap salah ditebak | Client menolak config, kalibrasi macet | Oracle loop L5 via dashboard: perbaiki mock, replay, iterasi cepat |
| Payload client terenkripsi (bukan JSON polos) | Server lokal harus replikasi skema | Replikasi via `libgavesec.so` (JNI bridge), bukan reimplement; riset `onepieceencrypts` cadangan |
| Port 4000 bentrok (dua APK LSF berjalan?) | Server gagal bind | Hanya LSF yang punya server; original tidak. Deteksi bentrok + fallback port tercatat |
| Game LSF dibuka sebelum server siap | Request pertama gagal | Server start di `Application.onCreate` — sebelum activity game; urutan terjamin |
| SDK cloud di client mem-blok boot saat mati | Client crash sebelum menyentuh server | Catat di L4/L5; solusi permanen di L6 (stub SDK) |

---

## 7. Kriteria Selesai Tahap Fondasi (Definition of Done — Track L)

1. `DB-LOCAL.apk` di repo **identik dengan baseline** — tidak pernah ditimpa.
2. APK LSF terinstall **bersebelahan** dengan original; **original tetap utama & running
   online** tanpa perubahan apa pun.
3. APK LSF = game berperilaku normal **+ local server hidup di port 4000** (pajangan
   yang work), murni penambahan tanpa regresi.
4. **Server pasti jalan duluan** — dinyalakan di `Application.onCreate` sebelum game dimuat.
5. **Web Dashboard** (logging, debugging, APK reader, copy-first) berfungsi sebagai alat
   bantu kalibrasi di `127.0.0.1:4000`.
6. Kontrak API bootstrap + endpoint yang sudah "diterima" client terdokumentasi §2.3.
7. Semua artefak server (log, config, data, respons) hidup di `Android/data/com.db.local.lsf/server/`.
8. Dokumen ini selalu menjadi sumber kebenaran tunggal — setiap fase selesai diperbarui di sini.

---

## 8. Aturan Kerja Workspace

- **Dokumentasi tunggal:** semua catatan/keputusan/hasil uji hanya di `README.md` ini.
- **Tidak membuat project/APK baru** — satu-satunya artefak yang diedit adalah custom dari
  `DB-LOCAL.apk` (APK LSF).
- **Original = utama, online, immutable.** Semua eksperimen hanya pada APK LSF.
- **Port 4000 = kontrak tetap** (API game + Web Dashboard).
- **Root folder server = `Android/data/com.db.local.lsf/server/`** — log/config/data/respons hidup di sini.
- **Logging = alat bantu**, bukan produk: indikator server running & paritas dengan server asli.
- `DB-LOCAL.apk` asli = baseline **immutable** (tidak pernah ditimpa); hasil bedah di
  `.analysis/` (gitignored).
- Cabang kerja: `main` untuk dokumentasi; implementasi di branch `feat/lsf-custom`.
- **Batas analisis:** fondasi infrastruktur saja. Analisis mekanik/gameplay dilarang sampai fondasi selesai.
