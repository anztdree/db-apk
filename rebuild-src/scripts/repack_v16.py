#!/usr/bin/env python3
# Repack APK MOD-LOCALSERVER v16 — FIX LAPORAN DEVICE 23 Nov (Bagian 31):
#  1. rawQuery(String,Object[]) -> String[]  (stub salah -> NoSuchMethodError
#     di Android 12 API 31: "MIGRASI gagal" saat boot; migrasi legacy JSON
#     sekarang juga try/catch per-sumber).
#  2. FIX 30001 "silent error": profile.json products += productCode ASLI
#     client 75880787318053043365515388491588 (rekam log device 23 Nov 16:54)
#     + UPGRADE OTOMATIS profil v15 yang belum diedit (SDK-CONFIG-UPGRADE).
#     Gerbang 30001 tetap ada (PHP-faithful, tanpa bypass) — hanya daftar
#     products kini memuat kode yang benar; ditolak = log jelas + tombol FIX.
#  3. sdk/index.txt DIHAPUS (keluhan owner "berasa jadi sampah") — sisa
#     instal v15 dibersihkan otomatis tiap boot.
#  4. DASHBOARD v6 (keluhan owner "masih sulit dibaca"):
#       - pill status: SEMUA NORMAL / N MASALAH
#       - BANNER MASALAH bahasa manusia + tombol TERIMA PRODUCT INI (satu
#         klik menambahkan productCode yang ditolak -> force-stop game)
#       - tampilan PENTING (default, tanpa noise aset CDN) / MASALAH / SEMUA
#       - verdict KATA (OK/GAGAL/CEK/SISTEM), deskripsi error SDK dari
#         envelope + petunjuk aksi per kode (20000/20002/20003/30001/...)
#       - chip STATUS: DB backend + productCode terakhir + tamu/akun
#       - panel SDK CONFIG += seksi PRODUCT (daftar + TERIMA PRODUCT)
#     endpoint baru: POST /sdkconfig/product-add; /sdkconfig/api +=
#     ver/productCode/productOk/products.
#  - BOOT log memuat marker "MOD-LOCALSERVER v16" (verifikasi versi mudah).
#
#  - base    : release-mod/db-local(MOD-LOCALSERVER-v15).apk (semua entri lain
#              byte-identik — assets, classes.dex v11, manifest)
#  - replace : classes2.dex (server v16 — Bagian 31)
#  - buang   : META-INF signature lama (di-sign ulang v1v2v3 keystore sama)
#  - TIDAK menyentuh: smali SDK/game, assets, config_BS.properties, QHinfo APK.
import zipfile, os, sys, time

BASE_APK  = 'release-mod/db-local(MOD-LOCALSERVER-v15).apk'
NEW_DEX   = 'tools/out/dex/classes.dex'
OUT       = 'tools/out/db-local(MOD-LOCALSERVER-v16)-unsigned.apk'

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
import hashlib
sha = hashlib.sha256(open(OUT, 'rb').read()).hexdigest()
print('REPACK v16 OK: %d entri dipertahankan, classes2.dex diganti (%d B)' % (kept, len(new_dex)))
print('unsigned: %s (%d B) sha256 %s…' % (OUT, size, sha[:8]))
print('durasi %.1fs' % (time.time() - t0))
