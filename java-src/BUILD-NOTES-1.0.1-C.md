# DB-Local 1.0.1 (build C) — koreksi build B

**Satu-satunya perubahan vs build B: SDK dikembalikan ORIGINAL (tidak ada SDK yang dimatikan).**

## Identitas file
- File: `DB-Local-1.0.1-C-03d23bae.apk` · 125.821.985 byte
- SHA-256: `03d23bae2e88b8606a67e1cb1f1a2c7590830b7deda2842a197b4fe3777b8672`
- Cert: `9f516096…40265` (sama dgn build 1.0.1-A/B → install-over aman)

## Isi build (base = APK LSF 1.0.0 milik Anda, ikon/label kustom utuh byte-identik)
1. **Manifest LSF original** — HANYA `versionName 1.0.0 → 1.0.1` (1 edit, panjang sama).
   - `DEV_KEY = u9vDL2dhmKxEacneJnjvoP` **ORIGINAL** (build B mengubah jadi "unknown" — DIKEMBALIKAN)
   - `com.facebook.sdk.ApplicationId = @0x7f0c0051` **ORIGINAL** (build B mengubah nama jadi "Applicationld" — DIKEMBALIKAN)
2. **classes.dex** = LSF + 3 patch terbukti (SHA `c0e00b05…`, identik byte dgn build B yang lolos verifier):
   LaunchActivity → MiniServer.start, MainActivity → checkOverlay, feedtonight → P5.
3. **classes2.dex** = com.db.local 100% sumber Java (SHA `7be569b8…`, byte-identik build ter-audit, 221/221 metode lolos verifikasi ART):
   MiniServer 3 port — **10100 CDN / 10101 SDK / 10102 LoginServer** + floating logger (bisa di-copy) + folder override.
4. **Aset port-lokal**: `QHinfo` = `http://127.0.0.1:10101` · `config_BS.properties` EntryPoint = `http://127.0.0.1:10100/bs/db/android…`
5. **assets/mirror/** 46 file (mirror qsdk/bs/upgrade) — dipakai saat internet OFF.

## Config server di Android/data (tanpa repack)
```
/storage/emulated/0/Android/data/com.db.local/files/server/
```
File di folder ini MENGGANTIKAN file yang diambil dari server online (struktur asli: `bs/…`, `upgrade/…`, `qsdk/…`, contoh `bs/resource/properties/clientversion.json`). README.TXT dibuat otomatis saat aplikasi pertama jalan.

## Audit diff
- **C vs B**: berubah HANYA `AndroidManifest.xml` (+3 file tanda tangan) → bukti tidak ada perubahan lain.
- **C vs LSF**: berubah 5 entri (manifest, MANIFEST.MF, QHinfo, config_BS, classes.dex) + tambah 49 (classes2.dex, 46 mirror, tanda tangan) + hapus 2 (tanda tangan lama) — sisanya byte-identik.

## Catatan instal
- Dari build 1.0.1-A/B: langsung install-over (cert sama).
- Dari APK LSF 1.0.0 original: **uninstall dulu** (cert pembuat beda).
- Logcat debug: aktifkan overlay izinnya, chip **DB-LOCAL** muncul — log bisa di-copy.
