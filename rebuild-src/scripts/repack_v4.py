#!/usr/bin/env python3
# REPACK v4 — SWAP classes2.dex di atas APK v3 (rantai terdokumentasi,
# pola sama dgn v11..v18: "beda HANYA classes2.dex + 3 META-INF").
#
# ISI PERUBAHAN v4 (Bagian 39 — logger v3, arahan owner):
#   - Dashboard port 4000 redesign total: 🐉 DB MONITOR — teks jelas,
#     tata letak terstruktur & rapi, emoji stylish (chip layanan, badge
#     tahap, verdict ✅❌⚠️ℹ️⚙️, tabel 7 kolom, modal detail beremoji).
#   - IZIN OVERLAY = INFO, BUKAN error (verdict ℹ️, eksklusi isProblem,
#     banner merah overlay dihapus, teks OverlayFixer/SessionReset netral).
#   - Penanda versi device: BOOT/crash/dashboard = v4.
#   - classes.dex (kode game, starter +2 baris) TIDAK tersentuh.
#   - Endpoint/assets: 100% byte-identik v3 (QHinfo 9530, config_BS 9527,
#     clientversion.json, serversetting.json, setting bin 9528).
import zipfile, os, hashlib, sys, time

V3   = 'work/v3.apk'
NEW  = 'work/v4-unsigned.apk'
DEX  = 'tools/out/dex/classes.dex'

def sha(b): return hashlib.sha256(b).hexdigest()

t0 = time.time()
newdex = open(DEX, 'rb').read()

SKIP = ('META-INF/MANIFEST.MF', 'META-INF/DBLOCALM.SF', 'META-INF/DBLOCALM.RSA')
zv = zipfile.ZipFile(V3)
names = zv.namelist()

# sanity: entri kontrak v3
for must in ('classes.dex', 'classes2.dex', 'assets/QHinfo',
             'assets/config_BS.properties',
             'assets/server_root/bs/resource/properties/clientversion.json'):
    assert must in names, 'entri kontrak hilang: ' + must
assert sha(zv.read('classes.dex')) == \
    '1df64d91219c01db729b13449a606ad88326d1aa260f3fa316aa44dc6ead787e', \
    'classes.dex BUKAN rantai proven!'
assert zv.read('assets/QHinfo') == b'http://127.0.0.1:9530'
# config_BS.properties = base64 EntryPoint (9527/bs/db/android) — hex 9527=0x252F
assert b'aHR0cDovLzEyNy4wLjAuMTo5NTI3' in zv.read('assets/config_BS.properties')
print('sanity: v3 OK — %d entri, classes.dex proven, endpoint benar' % len(names))

# sanity: dex baru benar-benar logger v4 (marker dashboard + overlay INFO)
for needle in (b'DB MONITOR', b'overlay OFF (normal)', b'>v4</span>'):
    assert needle in newdex, 'dex baru tidak memuat marker: ' + needle.decode()
print('sanity: classes2.dex baru %d B — marker v4 ada' % len(newdex))

# ---------- tulis v4-unsigned ----------
zo = zipfile.ZipFile(NEW, 'w', zipfile.ZIP_DEFLATED)
n_changed, n_skip = 0, 0
for info in zv.infolist():
    if info.filename in SKIP:
        n_skip += 1
        continue                      # sign lama dibuang — diganti signer
    data = zv.read(info.filename)
    if info.filename == 'classes2.dex':
        data = newdex
        n_changed += 1
    ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
    ni.compress_type = info.compress_type
    ni.external_attr = info.external_attr
    ni.internal_attr = info.internal_attr
    ni.create_system = info.create_system
    zo.writestr(ni, data)
zo.close()
print('repack: %d entri disalin, classes2.dex diganti (1), sign lama dibuang (%d)' %
      (len(names) - n_skip, n_skip))

# ---------- verifikasi diff ----------
za, zb = zipfile.ZipFile(V3), zipfile.ZipFile(NEW)
na, nb = set(za.namelist()), set(zb.namelist())
gone, new_ = sorted(na - nb), sorted(nb - na)
diff = sorted(n for n in (na & nb) if za.read(n) != zb.read(n))
print('diff v3->v4: hilang=%s tambah=%s beda=%s' % (gone, new_, diff))
assert sorted(gone) == sorted(SKIP) and not new_ and diff == ['classes2.dex'], \
    'DIFF BUKAN HANYA classes2.dex + META-INF!'
assert sha(zb.read('classes.dex')) == \
    '1df64d91219c01db729b13449a606ad88326d1aa260f3fa316aa44dc6ead787e'
print('VERIFIKASI: diff bersih — classes2.dex saja; classes.dex proven utuh')
print('selesai %.1fs — %s (%d B) sha256 %s' %
      (time.time() - t0, NEW, os.path.getsize(NEW), sha(open(NEW, 'rb').read())))
