# BUILD-NOTES 1.0.1-E — Perbaikan seed config server + versionName kembali 1.0.1

Tanggal: 2025-10-24 · Base: DB-Local-1.0.2-D-3b0bd303.apk · Base LSF: 45414ff1 (tidak berubah)

## 1. Laporan user (pemicu)
> "version tetap 1.0.1. tapi kejanggalan file, nama folder dan isi file sama itu pasti kesalahan
> contoh ada folder generator.json isi generator.json, lalu folder all.zip isi all.zip"

## 2. Diagnosa (bukti fisik, sebelum revisi)
- Repo commit f832471 TIDAK memuat sumber Core.java build D (Core di repo = versi lama 3 port,
  BUILD-NOTES-1.0.2.md tidak ada) → kode asli hanya ada di classes2.dex 94a9f6d1 build D.
- Baksmali D `Core.seedAssetDir`:
  - cabang direktori: untuk tiap anak → `seedAssetDir(ctx, sub, File(out, nama))`
  - cabang file (`assets.list()` kosong utk file): menulis ke `new File(out, basename(assetPath))`
  → basename ditambahkan DUA KALI → setiap file jadi folder bernama file itu berisi file bernama sama.
- Struktur `assets/mirror/` di APK D BENAR (53 entri: generator.json & upgrade/all.zip adalah file).
- Game tetap jalan: `serveMirror` menerima hanya `isFile()` dari server/ → fallback ke mirror internal.
- Simulasi logika dex D atas mirror = 104 node (52 file × 2), pola `SERVER/VERSION/VERSION` dst;
  pass besar (skipZip=false) menghasilkan `server/upgrade/all.zip/all.zip` — persis laporan user.

## 3. Perbaikan (java-src/com/db/local/Core.java, satu-satunya sumber kebenaran)
1. `seedAssetDir` cabang file: tulis ke `out` LANGSUNG (sudah = File(out, nama) dari pemanggil).
2. `repairNested` (baru): perangkat yang terlanjur install D memiliki pohon rusak — folder bernama
   file berisi file bernama sama dinaikkan jadi file biasa (isi utuh, idempoten, editan tak tertimpa).
3. versionName: 1.0.2 → 1.0.1 (in-place AXML UTF-8 pool, offset 1244, panjang sama;
   aapt2 xmltree diff = tepat 1 baris; versionCode tetap 1).

## 4. Metode build (reproducible)
- ecj 3.34.0 (-source/-target 8, android.jar platform-34-ext7) → d8 r34 → core.dex (6 kelas Core*)
- MergeCore (dexlib2 2.5.2): dex D buang 6 kelas Core* → sisipkan 6 Core* baru → classes2-new.dex
  (24 kelas non-Core = instruksi bit-identik dex D yang sudah teruji di device)
- Repack raw-copy (ukuran dari central directory), buang sig v1 lama → zipalign -f -p 4 →
  apksigner v1+v2+v3 (keystore dblocal-release.keystore, pass dblocal103f, cert 9f516096…40265 SAMA)

## 5. Gerbang audit (semua lolos)
- Baksmali-diff (tanpa .line): 24 kelas non-Core = 0 diff; Core$1..Core$5 = 0 diff;
  Core.smali hanya: seedAssetDir (fix), repairNested (baru), selainnya 0 perubahan logika
  (selisih kecil = gaya codegen ecj: append(Object) vs toString(), label renumber — semantik identik)
- Metode com.db.local: 235 → 236 (satu-satunya tambahan: repairNested)
- String const: +3 hanya milik fix (".fixtmp", "seed perbaiki: ", "seed repair gagal: ")
- Verify v2: 34.160 metode dianalisis; 92 fail = baseline vendor D 100% identik (imprecision
  dexlib2 terdokumentasi); com.db.local 0 gagal
- Verify-after-pack: dex di dalam APK bertanda tangan == hasil audit; versionName 1.0.1
- Diff D vs E: 5 entri berubah (manifest, classes2.dex, 3 sig) + 3717 byte-identik

## 6. Artefak
- DB-Local-1.0.1-E-58c8030b.apk — 125.822.586 B
- SHA-256: 58c8030b63e54961f57eab2b2d39558b119e68a3ff72001614db51c9e6818ef8
- URL: https://github.com/anztdree/db-apk/releases/tag/1.0.1 (aset tunggal E; release 1.0.2 dihapus)
- Round-trip unduh ulang: SHA-256 COCOK

## 7. Simulasi hasil seed di device (logika E)
52 file langsung sebagai file: server/VERSION, server/generator.json, server/bs/... (19 file js),
server/qsdk/... (17), server/upgrade/{base,resource}.version + size.json + upgrade.json,
server/override/facebook/{5}, server/override/appsflyer/README.TXT; upgrade/all.zip (pass besar) —
0 folder-hantu.
