# 📦 Folder `server/` — Mirror File Asli dari Server (Panen & Verifikasi)

> **Prinsip**: mengikuti aturan APLIKASI — aplikasi meminta file dari server, maka file aslinya
> diambil **1:1 tanpa mengubah isi**, disimpan di sini sebagai bahan baku standalone mode.
> Seluruh file diverifikasi valid (bukan 404/korup) pada tanggal panen.

- **Tanggal panen**: 2026-10-18 06:01 WIB (server masih hidup saat panen)
- **Metode**: unduh langsung per-file dari URL asli yang dipakai aplikasi (parameter `rnd`/`v` sesuai pola aplikasi)
- **Aturan**: `sentus 0 byte` — tidak ada file yang diedit, dikonversi, atau dibuat ulang
- **Verifikasi**: 23/23 cek VALID (detail di [VERIFIKASI.md](VERIFIKASI.md))

## Struktur

```
server/
├── configus/                  ← KELOMPOK 1 — server config (Fase 2, dibaca setiap buka app)
│   └── setting_BS_Android.bin     XOR "DragonBall" → JSON: testMode, url, update, login server
├── upgrade/                   ← KELOMPOK 2 — CDN hot-update (Fase 5)
│   ├── base.version               = 110
│   ├── resource.version           = 11390
│   ├── size.json                  ukuran unduhan (progress bar)
│   ├── upgrade.json               peta patch inkremental
│   └── all.zip                    paket penuh 19.385.098 B, 652 entri (JS baru + resource penuh)
└── bs/                        ← KELOMPOK 3 — URL game (Fase 6–8, dicari Egret runtime)
    ├── index-native.html          bootstrap JS (TIDAK ada di APK — wajib dari sini)
    ├── manifest.json              daftar 15 file JS yang dimuat runtime
    └── js/                        15 file JS, versi PERSIS yang ditunjuk manifest.json
```

## Catatan penting

1. **`com_db_local.bin`** (configus) terkonfirmasi **404** di server asli → aplikasi mengabaikannya;
   tidak perlu ada di mirror.
2. **Konsistensi set** (kunci anti force-close): `manifest.json` ↔ 15 file `js/` ↔
   `resource.version` (11390) ↔ isi `all.zip` — semuanya satu genggaman asli dari server yang sama.
3. `all.zip` berisi `resource.version` = **11390**, cocok dengan `upgrade/resource.version` remote →
   setelah instalasi baru selesai unduh, bukaan berikutnya tidak akan mengunduh apa pun.
4. `setting_BS_Android.bin` asli tetap menunjuk server produksi — file ini TIDAK diedit.
   Pengarahan ke server lokal akan dilakukan di tahap integrasi (bukan di sini).
5. Hash SHA-256 semua file tercatat di [VERIFIKASI.md](VERIFIKASI.md) untuk pengecekan ulang kapan pun.
