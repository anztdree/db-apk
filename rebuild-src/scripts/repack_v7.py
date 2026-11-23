#!/usr/bin/env python3
# REPACK v7 — SWAP classes2.dex di atas APK v6 (rantai terdokumentasi,
# pola sama dgn v11..v18 & v1..v6: "beda HANYA classes2.dex + 3 META-INF").
#
# ISI PERUBAHAN v7 (Bagian 43 — kata-kata persis owner):
#   "sekarang mari rombak lagi logger dan UI di website kita, logger ini
#    emang stylish tapi gak seperti layaknya server yg Natural sedang
#    berjalan.."
#   CONSOLE v7 — dashboard port 4000 jadi SERVER NATURAL:
#   - terminal monospace hitam pekat; TANPA kartu/chip/pill/badge berwarna;
#   - entri log TANPA emoji — teks polos seperti server sungguhan;
#   - satu baris per peristiwa mengalir ke bawah (kolom tetap: WAKTU TAHAP
#     PORT ST DUR PERISTIWA — # ikut saat disalin);
#   - baris status ala server: "LISTEN 9527 9528 ... · uptime · seq · arsip
#     · status: NORMAL/N MASALAH";
#   - kursor blok berkedip (server hidup); saat jeda tampil "‖ jeda";
#   - tombol tetap IKON saja (arahan Bagian 41) — ⭐ ❌ 📜 ⚙️ ⏸️ 🧹 ❓ 📋 📄 💾;
#   - PORT 4000 MURNI log monitoring: seluruh rute /sdkconfig* (+/sdkconfig/api,
#     save, preset, product-add, session-reset) dan POST /fix/overlay DIHAPUS
#     dari LoggerHandler → 404. Pesan log PRODUCT DITOLAK 30001 tidak lagi
#     mengarahkan ke panel (kini: edit sdk/profile.json manual).
#   - overlay tetap INFO bukan error (Bagian 39, tidak berubah);
#   - popup "tips" tetap tidak ada (v6 — guard sama);
#   - classes.dex (kode game, starter +2 baris) TIDAK tersentuh.
#   - Endpoint/assets: 100% byte-identik (QHinfo 9530, config_BS 9527,
#     clientversion.json, serversetting.json, setting bin 9528).
import zipfile, os, hashlib, sys, time

V6   = 'release-mod/db-local(MOD-LOCALSERVER-v6).apk'
NEW  = 'work/v7-unsigned.apk'
DEX  = 'tools/out/dex/classes.dex'

def sha(b): return hashlib.sha256(b).hexdigest()

t0 = time.time()
newdex = open(DEX, 'rb').read()

SKIP = ('META-INF/MANIFEST.MF', 'META-INF/DBLOCALM.SF', 'META-INF/DBLOCALM.RSA')
zv = zipfile.ZipFile(V6)
names = zv.namelist()

# sanity: entri kontrak v6
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
print('sanity: v6 OK — %d entri, classes.dex proven, endpoint benar' % len(names))

# sanity: dex baru = v7 console natural + TANPA tips + TANPA sdkconfig
for needle in (b'>v7</span>', b'server console', b'MOD-LOCALSERVER v7',
               b'LISTEN ', b'pembaruan otomatis berhenti'):
    assert needle in newdex, 'dex baru tidak memuat marker: ' + needle.decode()
for absent in (b'DB MONITOR', b'"tips"', b'sdkconfig'):
    assert absent not in newdex, 'dex baru MASIH memuat: ' + absent.decode()
print('sanity: classes2.dex baru %d B — console v7, tanpa tips, tanpa sdkconfig' % len(newdex))

# ---------- tulis v7-unsigned ----------
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
za, zb = zipfile.ZipFile(V6), zipfile.ZipFile(NEW)
na, nb = set(za.namelist()), set(zb.namelist())
gone, new_ = sorted(na - nb), sorted(nb - na)
diff = sorted(n for n in (na & nb) if za.read(n) != zb.read(n))
print('diff v6->v7: hilang=%s tambah=%s beda=%s' % (gone, new_, diff))
assert sorted(gone) == sorted(SKIP) and not new_ and diff == ['classes2.dex'], \
    'DIFF BUKAN HANYA classes2.dex + META-INF!'
assert sha(zb.read('classes.dex')) == \
    '1df64d91219c01db729b13449a606ad88326d1aa260f3fa316aa44dc6ead787e'
print('VERIFIKASI: diff bersih — classes2.dex saja; classes.dex proven utuh')
print('selesai %.1fs — %s (%d B) sha256 %s' %
      (time.time() - t0, NEW, os.path.getsize(NEW), sha(open(NEW, 'rb').read())))
