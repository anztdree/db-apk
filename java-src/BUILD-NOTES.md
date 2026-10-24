# DB-Local 1.0.1 — Rebuild Opsi A (100% dari sumber Java)

Tanggal: 2026-10-24. Kompilasi: ecj 3.33.0 (`-source 8 -target 8`, android.jar platform-34) → d8 r34 → classes2.dex.

## Latar belakang
Tiga force close berturut-turut (`VerifyError` di `com.db.local.Core` lalu `com.db.local.Db.setWal`)
terbukti berasal dari teknik patch smali manual (8 metode di-edit langsung di bytecode). Solusi Opsi A
(yang disetujui user): SEMUA kelas `com.db.local` ditulis ulang 100% sebagai sumber Java di folder ini,
lalu dikompilasi `javac/ecj → d8` — tanpa satu pun edit smali manual.

## Isi folder
11 file Java untuk 29 kelas (kelas anonim/inner mengikuti urutan asli):
MiniServer (kontrak: `start(Context)`, `checkOverlay(Context)` — dipanggil classes.dex),
Core+Core$1..4 (3 listener 10100/10101/10102 + mirror server/→mirror/→fallback), Db+TemplateDirHolder
(SQLite WAL, INSERT OR REPLACE×5), Sio+Session (engine.io v3 + socket.io v2, framing `<len>:`),
Ws+SessionOwner (RFC6455), Xxtea, Jx, Texts, Fx, Dbg, DbgService+11 inner (floating logger + tombol CP/copy).

## Fitur (sama dengan build cd7a1d4a, kini dari Java murni)
- Semua server lokal: 10100 CDN, 10101 SDK, 10102 LoginServer; folder override `Android/data/com.db.local/server`
- P5 (offline gate) & hook LaunchActivity tetap di classes.dex — TIDAK disentuh (byte-identik 19d4d713…)
- Floating debug logger (seperti eruda): chip DB-LOCAL, minim/maksim, pause, clear, tombol **CP** = copy
  400 baris terakhir ke clipboard, auto-buka izin overlay sekali per install

## Gerbang audit (semua LOLOS)
1. Set metode non-synthetic: 223/223 identik dengan dex lama (0 hilang, 0 tambahan)
2. Set string const: 343/343 verbatim identik (1 tambahan = inlining konstanta platform oleh javac, semantik sama)
3. Verify.java v2 (propagasi tipe + invoke-arg check ala ART — alat yang menangkap 3 VerifyError lama):
   221/221 `com.db.local` LOLOS; game+SDK+egret 2.154 metode LOLOS; 16 temuan di kelas vendor bawaan
   (adjust/appsflyer/zxing/facebook-ml/gms-ads, TIDAK disentuh, terbukti jalan di device) = imprecision
   dexlib2 yang terdokumentasi
4. Debug info: 1.479 entri `.line` — metode-metode yang dulu hasil patch smali (mirrorFallback, serverDir,
   initServerDir, setWal, MiniServer.checkOverlay) kini punya line info = bukti asal Java
5. Xxtea: identik bit-per-bit dengan referensi JS independen untuk n≥2 (termasuk payload JSON wire);
   kasus n=1 (≤4 byte) tak-invertible = sifat algoritma pasangan game (terbukti sama pada referensi JS)
6. classes.dex byte-identik build terverifikasi terakhir (19d4d713…); aset mirror/server utuh (3.715 entri)

## Identitas APK
- File: DB-Local-1.0.1-A-87262a0d.apk (125.301.793 B)
- SHA-256: 87262a0d215e860c133c98f13756a3a526f02a79a66e813b49358c6718013920
- classes2.dex: 64.860 B (7be569b8…) — 100% hasil d8 dari sumber Java folder ini
- Cert: 9f516096…40265 (SAMA dgn semua build 1.0.1 sebelumnya → install-over tanpa uninstall, data aman)
- versionCode 1, versionName 1.0.1, minSdk 21, targetSdk 30
