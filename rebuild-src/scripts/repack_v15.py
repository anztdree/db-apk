#!/usr/bin/env python3
# Repack APK MOD-LOCALSERVER v15 — SDK DATA + QHINFO MASUK sdk/ + PANEL SDK CONFIG
# (Bagian 30 — koreksi owner: "data/ dan juga QHinfo Masuk ke folder sdk"
#  + approval sekali-jalan panel SDK CONFIG di dashboard 4000).
#
# Isi v15 (classes2.dex baru; classes.dex BYTE-IDENTIK v11 — SDK smali game
# 100% original, forbidden zone dihormati):
#  1. LOKASI DB PINDAH: server/data/ -> server/sdk/data/ (Db.open).
#     Migrasi otomatis dari lokasi lama: db.sqlite (instalan v14) +
#     account.json/accounts.json (legacy v8-v13) disalin ke sdk/data bila
#     target belum ada — uid/token PEMAIN TETAP. Lokasi lama tidak dihapus.
#  2. QHinfo masuk sdk/ — self-seed berisi nilai AKTIF di APK
#     (http://127.0.0.1:9530, 21 B, tanpa trailing slash — mirror asli).
#     CATATAN JUJUR: client membaca QHinfo dari DALAM APK
#     (QuickGameManager.initThirdDataSDK — xref 1 pemakai,
#     verify-artifacts/verify_total.txt A) — file sdk/QHinfo = sumber
#     kebenaran owner yang dibakar ke assets/QHinfo pada rebuild berikutnya.
#  3. PANEL SDK CONFIG di dashboard 4000 (browser HP, tanpa PC):
#       GET  /sdkconfig        halaman editor (gaya sama MONITOR SERVER)
#       GET  /sdkconfig/api    JSON: backend, guest, accounts, isi 5 file
#       POST /sdkconfig/save   edit whitelist: init/profile/ckReg/fallback/QHinfo
#                              (validasi: JSON {..}, init wajib payTypes+
#                              loginTypes, QHinfo http:// tanpa slash)
#       POST /sdkconfig/preset E1 ["1"] / E2 ["12"] / E3 ["1","13"]
#       + tabel accounts (tamu + email) + backend aktif
#  4. DB dibuka DI BOOT (LocalServerStarter) — migrasi lokasi selesai sebelum
#     listener mana pun melayani request; log BOOT "DB siap di boot".
#  - sdk/index.txt diperbarui (dokumentasi QHinfo + sdk/data + panel).
#  - smoke desktop 57/57 PASS (37 lama diperluas: qhinfo-seeded/isi,
#    migrasi-lokasi db.sqlite+legacy json, panel GET/api/save/preset/reject).
#
#  - base    : release-mod/db-local(MOD-LOCALSERVER-v14).apk (semua entri lain
#              byte-identik — assets, classes.dex v11, manifest)
#  - replace : classes2.dex (server v15 — Bagian 30)
#  - buang   : META-INF signature lama (di-sign ulang v1v2v3 keystore sama)
#  - TIDAK menyentuh: smali SDK/game, assets, config_BS.properties, QHinfo APK.
import zipfile, os, sys, time

BASE_APK  = 'release-mod/db-local(MOD-LOCALSERVER-v14).apk'
NEW_DEX   = 'tools/out/dex/classes.dex'
OUT       = 'tools/out/db-local(MOD-LOCALSERVER-v15)-unsigned.apk'

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
        # pertahankan metode kompresi asli (STORED/DEFLATED)
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
print('REPACK v15 OK: %d entri dipertahankan, classes2.dex diganti (%d B)' % (kept, len(new_dex)))
print('unsigned: %s (%d B) sha256 %s…' % (OUT, size, sha[:8]))
print('durasi %.1fs' % (time.time() - t0))
