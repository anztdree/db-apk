#!/usr/bin/env python3
# Repack APK MOD-LOCALSERVER v17 — Bagian 32 (jawaban laporan device 23 Nov sore):
#  1. DASHBOARD v7 = TABEL (keluhan owner: "log anda gk rapi susah di baca ...
#     teks kaya tapi gak efisien"): satu baris per peristiwa, header sticky,
#     chip filter tahap, Salin tabel = kolom sejajar, modal detail per baris.
#  2. CRASH CAPTURE (CrashHook): crash "setelah sdk selesai LOADING" terjadi
#     di kode SDK/game (classes.dex, forbidden zone) — logger HTTP buta karena
#     tidak ada request berikutnya. Hook default UncaughtExceptionHandler
#     menulis logs/crash-*.txt (thread + full stack + 50 log terakhir) +
#     entri kind=CRASH -> banner merah dashboard (GET /crash) dgn tombol salin.
#  3. FIX IZIN OVERLAY (OverlayFixer): kandidat utama crash = dialog auto-login
#     SDK (com/quickgame/android/sdk/d/e) memakai window TYPE_APPLICATION_OVERLAY
#     (2008) TANPA cek canDrawOverlays (float view type 1003 gagal-nya ditelan
#     try/catch — makanya diam). Android 12 tanpa izin "Display over other apps"
#     -> BadTokenException saat dialog muncul = "setelah loading" crash.
#     Cek di boot + banner + tombol 1-klik BUKA SETTINGS (POST /fix/overlay)
#     membuka halaman izin dari server thread (FLAG_ACTIVITY_NEW_TASK).
#  - BOOT log += marker "MOD-LOCALSERVER v17"; /sdkconfig/api ver=v17;
#    /stats += overlay{checked,granted}; /logs & panel v16 tetap.
#  - SMOKE DESKTOP 82/82 PASS (69 lama + 13 baru: dashboard tabel, crash route/
#    sintetis/entry/disk, stats overlay, fix overlay desktop).
#
#  - base    : release-mod/db-local(MOD-LOCALSERVER-v16).apk (semua entri lain
#              byte-identik — assets, classes.dex v11, manifest, resources)
#  - replace : classes2.dex (server v17 — Bagian 32)
#  - buang   : META-INF signature lama (di-sign ulang v1v2v3 keystore sama)
#  - TIDAK menyentuh: smali SDK/game (classes.dex byte-identik), assets, QHinfo.
import zipfile, os, time, hashlib

BASE_APK  = 'release-mod/db-local(MOD-LOCALSERVER-v16).apk'
NEW_DEX   = 'tools/out/dex/classes.dex'
OUT       = 'tools/out/db-local(MOD-LOCALSERVER-v17)-unsigned.apk'

def is_signature(name):
    n = name.upper()
    return (n.startswith('META-INF/') and (
        n.endswith('.SF') or n.endswith('.RSA') or n.endswith('.DSA') or n.endswith('.EC')
        or n == 'META-INF/MANIFEST.MF' or n.startswith('META-INF/COM/ANDROID/BUILD')))

t0 = time.time()
new_dex = open(NEW_DEX, 'rb').read()
zin = zipfile.ZipFile(BASE_APK)
names = zin.namelist()
assert 'classes2.dex' in names and 'classes.dex' in names, 'APK dasar tidak lengkap'

zout = zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED)
kept, replaced = 0, 0
for item in zin.infolist():
    if is_signature(item.filename):
        continue                                   # buang signature lama
    if item.filename == 'classes2.dex':
        zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
        zi.compress_type = item.compress_type
        zi.external_attr = item.external_attr
        zout.writestr(zi, new_dex)
        replaced += 1
        continue
    zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
    zi.compress_type = item.compress_type
    zi.external_attr = item.external_attr
    zout.writestr(zi, zin.read(item.filename))
    kept += 1
zout.close()
zin.close()

assert replaced == 1, 'classes2.dex tidak terganti'
size = os.path.getsize(OUT)
sha = hashlib.sha256(open(OUT, 'rb').read()).hexdigest()
print('REPACK v17 OK: %d entri dipertahankan, classes2.dex diganti (%d B)' % (kept, len(new_dex)))
print('unsigned: %s (%d B) sha256 %s…' % (OUT, size, sha[:8]))
print('durasi %.1fs' % (time.time() - t0))
