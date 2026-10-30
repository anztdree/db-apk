# db-apk — Dokumentasi Rencana: Standalone Local Server

> **Status dokumen:** Analisis Fondasi & Rencana (v1.1) — *rencana saja, belum ada implementasi*
> **Lingkup:** Fondasi infrastruktur client-server. **Analisis gameplay sengaja dikecualikan.**
> **Tanggal:** 2026-10-30
> **Perubahan v1.1:** strategi diubah — **original APK tidak dimodifikasi sama sekali**
> (endpoint dibiarkan). Server lokal dibangun sebagai **APK pendamping (companion)**
> yang berjalan **bersebelahan** dengan APK asli. Merge endpoint = tahap akhir nanti.

---

## 1. Ringkasan Proyek

Game ini adalah aplikasi Android berbasis **Egret Engine (H5/TypeScript)** yang dibungkus
runtime native (shell APK). Saat ini seluruh konfigurasi & layanan bergantung pada server
remote pihak ketiga yang **sudah/segera mati**.

**Tujuan akhir:** menjadikan APK **standalone & berjalan lokal (offline)**.
Prinsip yang disepakati:

- **Original APK tidak disentuh** — endpoint, smali, signature: semuanya tetap utuh.
- **Server lokal dibangun terpisah sebagai APK pendamping** (companion), disempurnakan
  dulu hingga sempurna sambil **bersebelahan** dengan APK asli.
- Perubahan endpoint pada client **ditunda** sampai server lokal terbukti sempurna.
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

**Catatan tambahan (v1.1):** pencarian arsip publik (Wayback Machine + CDX API) untuk
`sjmobilegame.com` dan `t4game.com` **tidak menemukan snapshot sama sekali**. Artinya
format respons asli bootstrap **tidak dapat dipulihkan dari luar** — harus direkonstruksi
dari sisi client (analisis dex/smali + observasi perilaku runtime). Lihat Fase S1 & S4.

### 2.5 Dependensi Cloud yang Akan Mati (perlu di-stub)

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

---

## 3. Konsep Solusi: APK Pendamping Server Lokal (Side-by-Side)

Dua aplikasi terpisah berjalan di device yang sama. **Original APK tidak pernah diubah.**

```
┌── Device Android ───────────────────────────────────────────────────────────┐
│                                                                             │
│  ┌─ APK ASLI (com.db.local) ─────────┐  ┌─ APK PENDAMPING (baru, kita) ────┐│
│  │  Game Client (Egret JS)           │  │  Embedded Local Server           ││
│  │  • endpoint TIDAK diubah          │◄─┤  • HTTP di 127.0.0.1:<PORT>      ││
│  │  • smali/signature utuh           │  │  • SQLite (state lokal)          ││
│  └──────────────┬────────────────────┘  │  • Lapisan intersep: VpnService  ││
│                 │                       └──────────────────────────────────┘│
│                 │  "dragon.sjmobilegame.com" ──DNS──► VPN ✂ diintersep      │
│                 │                                     ► 127.0.0.1:<PORT>    │
└─────────────────┴────────────────────────────────────────────────────────────┘
```

**Prinsip kerja:**

1. Client asli **tetap mengira** sedang bicara dengan `sjmobilegame.com` — tidak ada
   satu byte pun dari APK asli yang diubah.
2. APK pendamping menyadap resolusi DNS domain game (via **VpnService**, tanpa root)
   dan menjawab semua request dari server lokal di `127.0.0.1`.
3. Keunggulan side-by-side:
   - APK asli = **oracle uji**: setiap respons server kita divalidasi langsung dari
     reaksi client (logcat), tanpa risiko merusak original.
   - Rollback instan: matikan APK pendamping → kondisi kembali seperti semula.
   - Ketika sempurna → baru pertimbangkan merge endpoint di Track A.

---

## 4. Rencana Tahap Fondasi (v1.1 — Track B aktif)

> Setiap fase punya keluaran (deliverable) & kriteria selesai. Tidak ada fase gameplay di sini.

### Track A — Modifikasi APK Asli (DIBEKUKAN / di-pause)

| Fase | Konten | Status |
|---|---|---|
| A-1 | Repointing endpoint (config Base64) | ⏸ Ditunda sampai server lokal sempurna |
| A-2 | Stub SDK cloud (Billing/GMS/FB/Adjust) | ⏸ Ditunda |
| A-3 | Repackage & sign ulang | ⏸ Ditunda |

> Prinsip: Track A **hanya boleh** dilakukan setelah Track B membuktikan server lokal
> mampu melayani seluruh rantai bootstrap & API yang dibutuhkan client.

### Track B — APK Pendamping Server Lokal (AKTIF)

#### Fase B0 — Persiapan Workspace & Keputusan Arsitektur
- [ ] Project Android baru untuk companion (Gradle + Kotlin/Java).
- [ ] Pilih library server embedded (kandidat: **NanoHTTPD** / **AndServer** — murni Java, tanpa root).
- [ ] Tentukan mekanisme intersep (lihat §5): **VpnService (rekomendasi)** vs root/hosts vs proxy debug.
- **Deliverable:** skeleton project companion + keputusan arsitektur tercatat di §5.
- **Kriteria selesai:** struktur project disepakati; belum menulis kode bisnis.

#### Fase B1 — Rekonstruksi Kontrak Bootstrap (dari sisi client)
- [ ] Analisis dex/smali + runtime: apa yang client **harapkan** dari
      `/lzceshi/native_setting/Android` dan `/bs/db/android` (format, field, encoding)?
- [ ] Catat urutan request saat boot original APK (logcat + capture) — client asli
      dipakai sebagai **oracle**, tidak diubah.
- [ ] Fallback jika terbentur enkripsi payload: riset `onepieceencrypts`/`libgavesec.so`
      (masih ranah fondasi/protokol, bukan gameplay).
- **Deliverable:** spesifikasi kontrak bootstrap → ditulis menggantikan bagian
      "endpoint game sesungguhnya" di §2.3.
- **Kriteria selesai:** format request/response kedua endpoint bootstrap jelas.

#### Fase B2 — Skeleton Embedded Server
- [ ] Implementasi HTTP server di companion: route stub untuk kedua endpoint bootstrap.
- [ ] Penyimpanan: SQLite (state) + file konfigurasi statis.
- [ ] Server listen **hanya** di `127.0.0.1:<PORT>` (tidak terekspos jaringan).
- **Deliverable:** APK companion v0.1 (server jalan, belum dihubungkan ke game).
- **Kriteria selesai:** `curl http://127.0.0.1:<PORT>/bs/db/android` dari device
      mengembalikan respons sesuai kontrak Fase B1.

#### Fase B3 — Lapisan Intersep (VpnService)
- [ ] Companion menjalankan VpnService yang menangkap DNS domain game
      (`sjmobilegame.com`, `t4game.com`, dst. dari hasil B1) → arahkan ke server lokal.
- [ ] Non-domain game **diteruskan apa adanya** (passthrough transparan).
- **Deliverable:** APK companion v0.2 (VPN + server menyatu).
- **Kriteria selesai:** dari device, query `dragon.sjmobilegame.com` tersedot ke lokal
      tanpa mengganggu trafik aplikasi lain.

#### Fase B4 — Kalibrasi Bersama APK Asli (oracle loop)
- [ ] Install keduanya bersebelahan → nyalakan companion → boot original APK.
- [ ] Iterasi: amati logcat client → perbaiki respons server → ulangi, sampai client
      lolos rantai bootstrap (config terbaca, tidak lagi gagal koneksi).
- [ ] Setiap kemajuan dicatat di §2.3 (endpoint + format yang sudah "diterima" client).
- **Deliverable:** log kalibrasi + respons server yang lolos tiap tahap.
- **Kriteria selesai:** client melewati seluruh rantai bootstrap tanpa error jaringan
      (masuk gerbang berikutnya — dicatat sebatas protokol, bukan gameplay-nya).

#### Fase B5 — Hardening & Kemandirian
- [ ] Foreground service + auto-start: server siap **sebelum** game dibuka.
- [ ] Persistensi state lintas restart (SQLite solid, migrasi aman).
- [ ] Uji: mode pesawat (VPN lokal tetap bekerja), reboot device, kill/restart companion.
- **Deliverable:** APK companion v1.0 + catatan uji di dokumen ini.
- **Kriteria selesai:** semua skenario uji lolos; companion berdiri sendiri tanpa internet.

> Setelah B5 selesai → kembali ke Track A (repoint endpoint → merge single APK)
> sebagai tahap akhir opsional.

---

## 5. Keputusan Terbuka (perlu keputusan owner)

1. **Mekanisme intersep (kunci):**
   - **(a) VpnService di APK pendamping — rekomendasi**: tanpa root, tanpa mengubah
     client; konsekuensi: ikon VPN aktif + izin VPN sekali di awal.
   - (b) hosts/DNS dengan root: sederhana, tapi butuh device rooted — cocok hanya lab uji.
   - (c) WiFi proxy debug: rapuh, game bisa mengabaikan proxy — hanya cadangan.
2. **Port lokal** server embedded (usulan: `12729`, hindari port umum).
3. **minSdk companion:** mengikuti device uji yang dipakai (menentukan fitur VpnService).
4. **Strategi bundle terenkripsi:** biarkan runtime asli mendekripsi (aman) vs dekripsi
   statis (fleksibel tapi perlu riset AES `libgavesec.so`) — Fase B1 yang menentukan.
5. **Penyimpanan:** SQLite (rekomendasi) vs file JSON.

---

## 6. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Format respons bootstrap salah ditebak | Client menolak config, kalibrasi macet | Oracle loop B4: perbaiki dari logcat, iterasi cepat |
| Payload client terenkripsi (bukan JSON polos) | Server lokal harus replikasi skema | Replikasi via `libgavesec.so` (JNI bridge), bukan reimplement; riset `onepieceencrypts` sebagai cadangan |
| VpnService gagal start / user menolak izin VPN | Intersep tidak jalan | B3: UI izin jelas; fallback proxy debug di build debug |
| Game dibuka sebelum server siap | Request pertama gagal | B5: foreground service + start-on-boot, urutan aman |
| VPN memengaruhi trafik aplikasi lain | Mengganggu device | Passthrough ketat: hanya domain game yang diintersep |
| SDK cloud di client mem-blok boot saat mati | Client crash sebelum menyentuh server | Catat di B4; solusi permanen ada di Track A (stub SDK) |
| Companion dibunuh OS (battery saver) | Server mati diam-diam | B5: foreground service + battery optimization exemption |

---

## 7. Kriteria Selesai Tahap Fondasi (Definition of Done — Track B)

1. Original APK **identik dengan baseline** — tidak pernah diubah sedikit pun.
2. Companion APK melayani seluruh rantai bootstrap; client asli lolos tanpa error jaringan.
3. Semua trafik game yang diintersep hanya menuju server lokal `127.0.0.1`.
4. Kontrak API bootstrap + endpoint yang sudah "diterima" client terdokumentasi §2.3.
5. Companion berjalan stabil: start-on-boot, mode pesawat, tanpa internet.
6. Dokumen ini selalu menjadi sumber kebenaran tunggal — setiap fase selesai diperbarui di sini.

---

## 8. Aturan Kerja Workspace

- **Dokumentasi tunggal:** semua catatan/keputusan/hasil uji hanya di `README.md` ini.
- `DB-LOCAL.apk` asli = baseline **immutable** (tidak pernah ditimpa).
- Hasil bedah/dump berada di `.analysis/` (gitignored, tidak masuk repo).
- Cabang kerja: `main` untuk dokumentasi; implementasi Track B di branch `feat/companion-server`.
- **Batas analisis:** fondasi infrastruktur saja. Analisis mekanik/gameplay dilarang sampai fondasi selesai.
