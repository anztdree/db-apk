#!/usr/bin/env python3
# Repack APK MOD-LOCALSERVER v18 — Bagian 33 (jawaban owner "logika orang bodoh
# ... apk original cuma kita edit endpoint ... bisa force close?"):
#  1. EXITINFO (ApplicationExitInfo via refleksi, API 30+): cara sesi SEBELUMNYA
#     mati (CRASH/CRASH_NATIVE/ANR/LOW_MEMORY) + trace — terekam di boot.
#  2. SESSION RESET (jalur aman TANPA izin overlay): hapus sesi tersimpan SDK
#     (prefs "qg_Users" + info.qg namespace qg_Users) -> HWLoginActivity buka
#     UI LOGIN MANUAL (d/f) — jalur yang TERBUKTI aman (login email 06:08).
#     Bukti smali: d/e show() TANPA cek canDrawOverlays (0 pemakai di SDK),
#     window type 0x7d8 (2008) -> BadTokenException = crash 'setelah loading'.
#     AUTO-GUARD boot: overlay OFF + crash belum ditangani + sesi ada -> reset.
#  3. CrashHook v18: Wrapper bernama + watchdog re-wrap (anti handler SDK)
#     + buku besar crash-handled.list.
#  4. Dashboard v8: banner SESI (reset otomatis / sesi+overlay off), sysline
#     sesi+overlay, panel tombol RESET SESI SDK, ver v18.
#
#  - base    : release-mod/db-local(MOD-LOCALSERVER-v17).apk (semua entri lain
#              byte-identik — assets, classes.dex v11, manifest, resources)
#  - replace : classes2.dex (server v18 — Bagian 33)
#  - buang   : META-INF signature lama (di-sign ulang v1v2v3 keystore sama)
#  - TIDAK menyentuh: smali SDK/game (classes.dex byte-identik), assets, QHinfo.
import zipfile, os, time, hashlib

BASE_APK  = 'release-mod/db-local(MOD-LOCALSERVER-v17).apk'
NEW_DEX   = 'tools/out/dex/classes.dex'
OUT       = 'tools/out/db-local(MOD-LOCALSERVER-v18)-unsigned.apk'

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
print('REPACK v18 OK: %d entri dipertahankan, classes2.dex diganti (%d B)' % (kept, len(new_dex)))
print('unsigned: %s (%d B) sha256 %s…' % (OUT, size, sha[:8]))
print('durasi %.1fs' % (time.time() - t0))
