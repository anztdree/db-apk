#!/usr/bin/env python3
# REBUILD "v2" — SATU PASS dari APK ORIGINAL (permintaan owner 23 Nov:
#  "apk 1x Rebuild, apk originalnya hanya edit endpoint, jangan lakukan di luar itu!!"
#  + koreksi crash 23 Nov 21:49: init.json minimal -> NPE model.d
#  (HWLoginActivity.singersmooth) -> DEFAULT_INIT dex = mirror PENUH original).
#
# PRINSIP (sesuai keputusan owner):
#   A. APK ORIGINAL hanya DIEDIT ENDPOINT (tidak ada di luar itu):
#      - assets/QHinfo              : SDK endpoint -> http://127.0.0.1:9530
#      - assets/config_BS.properties: config endpoint -> http://127.0.0.1:9527
#      - classes.dex                : 100% original + HANYA starter (+2 baris
#        Application.smali) untuk memanggil mini-server — byte-identik rantai
#        v11..v18 (sha256 1df64d91..., terbukti baksmali-diff = 1 file).
#   B. MINI-SERVER = SISTEM TERPISAH, NOL GANGGUAN, tetap dalam 1 APK:
#      - classes2.dex (kode server v1, ECJ+D8, smoke 97/97 PASS)
#      - assets/server_root/** (web root server lokal — data, bukan kode game)
#   C. TIDAK MENYENTUH: smali SDK/game, resources.arsc, AndroidManifest.xml,
#      lib/**, assets game lain — semuanya byte-identik original.
#
# Sumber bytes proven:
#   - original  : db-local(Original-DO NOT DELETE!).apk  (sha b308c44d...)
#   - classes.dex proven: work/v18.apk (sha 1df64d91... — diverifikasi ulang)
#   - endpoint files proven: work/v18.apk (nilai aktif di device)
#   - server_root proven: work/v18.apk (mirror CDN tangkapan server asli)
import zipfile, os, hashlib, sys, time

ORIG = 'work/original.apk'
V18  = 'work/v18.apk'
NEWDEX = 'tools/out/dex/classes.dex'
OUT  = 'tools/out/db-local(MOD-LOCALSERVER-v2)-unsigned.apk'

def sha(b): return hashlib.sha256(b).hexdigest()

t0 = time.time()
zo = zipfile.ZipFile(ORIG); zv = zipfile.ZipFile(V18)
onames, vnames = set(zo.namelist()), set(zv.namelist())

# ---------- sanity: bytes proven ----------
cls_proven = zv.read('classes.dex')
assert sha(cls_proven) == '1df64d91219c01db729b13449a606ad88326d1aa260f3fa316aa44dc6ead787e', \
    'classes.dex proven BUKAN rantai v11-v18!'
newdex = open(NEWDEX, 'rb').read()
qhinfo = zv.read('assets/QHinfo')
assert qhinfo == b'http://127.0.0.1:9530', 'QHinfo endpoint salah'
cfg = zv.read('assets/config_BS.properties')

server_root = sorted(n for n in vnames if n.startswith('assets/server_root/'))
orig_cls = zo.read('classes.dex')
print('sanity: original %d entri | v18 %d entri | server_root %d file' %
      (len(onames), len(vnames), len(server_root)))
print('sanity: classes.dex original %d B -> proven %d B (starter %s)' %
      (len(orig_cls), len(cls_proven), 'ADA' if len(cls_proven) != len(orig_cls) else 'SAMA'))

def is_signature(name):
    n = name.upper()
    return (n.startswith('META-INF/') and (
        n.endswith('.SF') or n.endswith('.RSA') or n.endswith('.DSA') or
        n.endswith('.EC') or n == 'META-INF/MANIFEST.MF' or
        n.startswith('META-INF/COM/ANDROID/BUILD')))

REPLACE = { 'classes.dex': cls_proven,
            'assets/QHinfo': qhinfo,
            'assets/config_BS.properties': cfg }

zout = zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED)
kept = replaced = added = 0
changed_report = []
for item in zo.infolist():
    if is_signature(item.filename):
        continue                                    # signature lama dibuang
    zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
    zi.compress_type = item.compress_type
    zi.external_attr = item.external_attr
    if item.filename in REPLACE:
        zout.writestr(zi, REPLACE[item.filename])
        replaced += 1
        changed_report.append((item.filename, item.file_size, len(REPLACE[item.filename])))
    else:
        zout.writestr(zi, zo.read(item.filename))
        kept += 1
for n in server_root:
    zi = zipfile.ZipInfo(n, date_time=zv.getinfo(n).date_time)
    zi.compress_type = zv.getinfo(n).compress_type
    zi.external_attr = zv.getinfo(n).external_attr
    zout.writestr(zi, zv.read(n))
    added += 1
zi = zipfile.ZipInfo('classes2.dex', date_time=zv.getinfo('classes2.dex').date_time)
zi.compress_type = zipfile.ZIP_DEFLATED
zout.writestr(zi, newdex)
added += 1
zout.close(); zo.close(); zv.close()

size = os.path.getsize(OUT)
print('REPACK v2 OK: %d byte-identik + %d diganti (endpoint+starter) + %d tambahan (mini-server)' %
      (kept, replaced, added))
for f, a, b in changed_report:
    print('  GANTI %-32s %d -> %d B' % (f, a, b))
print('classes2.dex v2: %d B' % len(newdex))
print('unsigned: %s (%d B) sha256 %s' % (OUT, size, sha(open(OUT,"rb").read())[:16]))
print('durasi %.1fs' % (time.time() - t0))
