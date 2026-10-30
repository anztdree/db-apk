# db-apk — Dokumentasi Rencana: Standalone Local Server

> **Status dokumen:** Analisis Fondasi & Rencana (v1.0) — *tanpa implementasi*
> **Lingkup:** Fondasi infrastruktur client-server. **Analisis gameplay sengaja dikecualikan.**
> **Tanggal:** 2026-10-30

---

## 1. Ringkasan Proyek

Game ini adalah aplikasi Android berbasis **Egret Engine (H5/TypeScript)** yang dibungkus
runtime native (shell APK). Saat ini seluruh konfigurasi & layanan bergantung pada server
remote pihak ketiga yang **sudah/segera mati**.

**Tujuan akhir:** menjadikan APK **standalone & berjalan lokal (offline)**.
Prinsip yang disepakati:

- **Client tidak dirombak** — cukup mengubah konfigurasi endpoint.
- **Server disiapkan di dalam APK** (embedded local server).
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

## 3. Konsep Solusi: Standalone Local Server

```
┌──────────────────────────── APK (satu aplikasi) ────────────────────────────┐
│                                                                             │
│  Game Client (Egret JS, tidak diubah)                                       │
│        │  hanya config endpoint yang diarahkan ulang                        │
│        ▼                                                                    │
│  http://127.0.0.1:<PORT>/  ◄── Embedded Local Server (baru, di dalam APK)   │
│        │                                                                    │
│        ├─ /lzceshi/native_setting/Android   (mantani bootstrap lama)        │
│        ├─ /bs/db/android                    (konfigurasi server, statis)    │
│        └─ API game lainnya                  (dipetakan dari hasil capture)  │
│                                                                             │
│  Data persisten → file lokal (SQLite/files di storage internal APK)         │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Prinsip:** client tetap berpikir sedang bicara dengan server lama. Kita hanya
"menyulap" DNS/endpoint agar menunjuk ke server kecil di dalam APK itu sendiri.

---

## 4. Rencana Tahap Fondasi (per fase, tanpa implementasi)

> Setiap fase punya keluaran (deliverable) & kriteria selesai. Tidak ada fase gameplay di sini.

### Fase 0 — Persiapan Workspace & Toolchain
- [ ] Folder `.analysis/` di-gitignore (hasil bedah APK tidak di-commit).
- [ ] Siapkan toolchain: `apktool` (decompile/rebuild), `jadx` (baca Java),
      `apksigner`/`zipalign` (sign ulang), emulator/HP uji.
- [ ] Simpan salinan APK asli **tidak pernah dimodifikasi** sebagai baseline.
- **Deliverable:** lingkungan siap; baseline tercatat di dokumen ini.
- **Kriteria selesai:** `apktool d` & `apktool b` sukses round-trip pada APK asli, hasilnya masih bisa terinstall.

### Fase 1 — Pemetaan Protokol (Network Capture)
- [ ] Jalankan APK di emulator + intercept trafik (MITM proxy / iptables redirect).
      *Karena endpoint mati, fokus capture adalah: urutan request + format yang dikirim client
      sebelum gagal, dari sisi client.*
- [ ] Alternatif (jika capture mentok): riset skema `onepieceencrypts` di `libgavesec.so`
      untuk membaca daftar endpoint asli dari bundle JS — *ini masih ranah fondasi
      (protokol), bukan gameplay*.
- **Deliverable:** daftar endpoint + skema request/response (ditambahkan ke §2.3).
- **Kriteria selesai:** peta endpoint lengkap cukup untuk merancang API server lokal.

### Fase 2 — Desain Embedded Local Server
- [ ] Pilih library server embedded (kandidat: **NanoHTTPD** atau **AndServer** — murni Java, tanpa root).
- [ ] Definisikan kontrak API berdasarkan hasil Fase 1 (rute, format, enkripsi payload bila perlu).
- [ ] Desain penyimpanan lokal (SQLite) untuk menggantikan state yang tadinya di server.
- [ ] Server berjalan di thread background, listen di `127.0.0.1:<PORT>` saja (aman, tidak terekspos).
- **Deliverable:** dokumen kontrak API + diagram startup (Application → start server → baru load game).
- **Kriteria selesai:** desain disetujui; belum menulis kode.

### Fase 3 — Repointing Endpoint Client
- [ ] Ubah `assets/config.properties` & `config_BS.properties`
      (Base64 URL → `http://127.0.0.1:<PORT>/...` — encoding sama, hanya nilai diganti).
- [ ] Netralkan `QHinfo` (t4game) bila SDK menyentuhnya.
- [ ] Pastikan string `sjmobilegame.com`/`t4game.com` tersisa di smali di-stub/redirect seragam.
- **Deliverable:** diff konfigurasi minimum yang bisa di-review.
- **Kriteria selesai:** client boot tanpa memanggil domain lama (dicek via logcat/pcap).

### Fase 4 — Stub SDK Cloud
- [ ] Stub/neutralisasi: Billing, Google Sign-In, Facebook, Adjust, quickgame SDK
      (gunakan no-op agar alur boot tidak crash).
- [ ] Ganti login dengan identitas lokal (device-local account).
- **Deliverable:** daftar kelas SDK yang di-stub + perilaku penggantinya.
- **Kriteria selesai:** APK boot & masuk game **tanpa GMS/FB/Adjust** (mode pesawat pun lolos).

### Fase 5 — Repackage, Sign & Uji Offline
- [ ] Rebuild APK (`apktool b`), sign (`apksigner`), install bersih.
- [ ] Uji: mode pesawat penuh — boot → konfigurasi lokal terbaca → masuk layar login/game.
- [ ] Uji: reinstall/restart (pastikan state lokal tidak hilang).
- **Deliverable:** `DB-LOCAL-standalone.apk` + catatan hasil uji di dokumen ini.
- **Kriteria selesai:** semua langkah uji lolos di device/emulator target.

---

## 5. Keputusan Terbuka (perlu keputusan owner)

1. **Port lokal** yang dipakai server embedded (usulan: `12729` / acak tinggi, hindari port umum).
2. **Strategi bundle terenkripsi:** biarkan runtime asli yang mendekripsi (pilihan aman, Fase 1
   kemungkinan tidak butuh dekripsi) vs. dekripsi statis (lebih fleksibel tapi perlu riset kunci AES).
3. **Login lokal:** satu user default otomatis, atau layar pilih-profil sederhana.
4. **Penyimpanan:** SQLite vs file JSON (skala data menentukan).

---

## 6. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Skema `onepieceencrypts` tidak terpecahkan | Tidak bisa baca endpoint dari JS | Gunakan MITM dari sisi client; runtime asli tetap dipertahankan |
| Client mengirim payload terenkripsi ke server | API lokal harus replikasi format | Replikasi via `libgavesec.so` (JNI bridge) alih-alih reimplement |
| SDK boot-flow hard-gagal saat cloud mati | APK crash saat start | Fase 4: stub agresif + try-catch di jalur boot |
| Play Protect/signature mismatch | Install ditolak | Sign dengan key sendiri, uninstall versi lama |
| Regression saat rebuild APK | APK rusak | Fase 0: uji round-trip sebelum perubahan apa pun |

---

## 7. Kriteria Selesai Tahap Fondasi (Definition of Done)

1. APK boot **penuh tanpa jaringan eksternal** (mode pesawat).
2. Semua request keluar dari proses game hanya menuju `127.0.0.1`.
3. Tidak ada lagi panggilan ke `sjmobilegame.com`, `myqcloud.com`, `t4game.com`, GMS, FB, Adjust.
4. Kontrak API server lokal terdokumentasi §2.3 (hasil Fase 1).
5. Dokumen ini selalu menjadi sumber kebenaran tunggal — setiap fase selesai diperbarui di sini.

---

## 8. Aturan Kerja Workspace

- **Dokumentasi tunggal:** semua catatan/keputusan/hasil uji hanya di `README.md` ini.
- `DB-LOCAL.apk` asli = baseline **immutable** (tidak pernah ditimpa).
- Hasil bedah/dump berada di `.analysis/` (gitignored, tidak masuk repo).
- Cabang kerja: `main` untuk dokumentasi; buat branch per fase saat implementasi dimulai.
- **Batas analisis:** fondasi infrastruktur saja. Analisis mekanik/gameplay dilarang sampai fondasi selesai.
