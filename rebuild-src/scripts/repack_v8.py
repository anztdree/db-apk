#!/usr/bin/env python3
# REPACK v8 — SWAP classes2.dex di atas APK v7 (rantai terdokumentasi,
# pola sama dgn v1..v7: "beda HANYA classes2.dex + 3 META-INF").
#
# ISI PERUBAHAN v8 (Bagian 44 — kata-kata persis owner):
#   "hadeeeh malah makin cacat. ini maunya saya di percantik bukan di
#    pangkas.. teks yg muncul itu seperti teks layaknya server original..
#    bukan teks yg output nya minimalis"
#   CONSOLE v8 — SERVER CONSOLE PREMIUM (dipercantik, BUKAN dipangkas):
#   - teks log VERBOSE seperti server asli: kalimat deskripsi penuh +
#     baris detail ↳ (METHOD PATH · STATUS · req/resp · durasi);
#   - format baris WAKTU(ms) LEVEL [KOMPONEN] pesan — LEVEL INFO/DEBUG/
#     WARN/ERROR berwarna ala log server; KOMPONEN [CORE][CONFIG][CDN]
#     [SDK][LOGIN][GAME][MONITOR] berwarna per layanan;
#   - UI dipercantik: header premium (titik status bercahaya + badge
#     versi), banner boot server, kursor blok berkedip, tombol ikon rapi;
#   - SALIN LOG = teks persis format server (siap tempel);
#   - penanda BOOT/crash/dashboard v7 -> v8;
#   - TIDAK berubah: overlay=INFO (39), tombol ikon (41), port 4000 murni
#     monitoring (sdkconfig/fix-overlay tetap 404 — 41/43), tanpa "tips"
#     (42), poll incremental + filter lengkap + banner CRASH (32).
#   - classes.dex (kode game, starter +2 baris) TIDAK tersentuh.
#   - Endpoint/assets: 100% byte-identik (QHinfo 9530, config_BS 9527,
#     clientversion.json, serversetting.json, setting bin 9528).
import zipfile, os, hashlib, sys, time

V7   = 'release-mod/db-local(MOD-LOCALSERVER-v7).apk'
NEW  = 'work/v8-unsigned.apk'
DEX  = 'tools/out/dex/classes.dex'

def sha(b): return hashlib.sha256(b).hexdigest()

t0 = time.time()
newdex = open(DEX, 'rb').read()

SKIP = ('META-INF/MANIFEST.MF', 'META-INF/DBLOCALM.SF', 'META-INF/DBLOCALM.RSA')
zv = zipfile.ZipFile(V7)
names = zv.namelist()

# sanity: entri kontrak v7
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
print('sanity: v7 OK — %d entri, classes.dex proven, endpoint benar' % len(names))

# sanity: dex baru = v8 console premium + TANPA tips + TANPA sdkconfig
for needle in (b'>v8</span>', b'DB LOCAL SERVER', b'MOD-LOCALSERVER v8',
               b'LISTEN ', b'pembaruan otomatis berhenti',
               b'Konfigurasi game dimuat', b'Tiket login terverifikasi'):
    assert needle in newdex, 'dex baru tidak memuat marker: ' + needle.decode()
for absent in (b'DB MONITOR', b'"tips"', b'sdkconfig', b'>v7</span>'):
    assert absent not in newdex, 'dex baru MASIH memuat: ' + absent.decode()
v7title = 'com.db.local — server console'.encode('utf-8')
assert v7title not in newdex, 'judul v7 masih ada di dex v8'
print('sanity: dex v8 OK — %d B, marker lengkap, jejak lama NOL' % len(newdex))

zw = zipfile.ZipFile(NEW, 'w', zipfile.ZIP_DEFLATED)
diff = []
for n in names:
    if n in SKIP:
        continue  # tanda tangan lama dibuang — uber-apk-signer yang tanda tangan
    data = newdex if n == 'classes2.dex' else zv.read(n)
    if n == 'classes2.dex':
        diff.append(n)
    zw.writestr(n, data)
zw.close()
zv.close()

# verifikasi ulang zip baru (3 META-INF dibuang — signer mengembalikannya)
z2 = zipfile.ZipFile(NEW)
n2 = z2.namelist()
assert len(n2) == len(names) - 3, 'jumlah entri berubah: %d vs %d' % (len(n2), len(names))
assert z2.read('classes.dex') == zipfile.ZipFile(V7).read('classes.dex'), 'classes.dex berubah!'
assert z2.read('classes2.dex') == newdex, 'classes2.dex tidak persis dex baru'
for e in ('assets/QHinfo', 'assets/config_BS.properties',
          'assets/server_root/bs/resource/properties/clientversion.json',
          'assets/server_root/bs/resource/properties/serversetting.json'):
    assert z2.read(e) == zipfile.ZipFile(V7).read(e), 'endpoint berubah: ' + e
same = [n for n in n2 if n != 'classes2.dex' and z2.read(n) == zipfile.ZipFile(V7).read(n)]
print('verifikasi: %d entri, beda HANYA %s' % (len(n2), diff))
assert diff == ['classes2.dex']

print('REPACK OK: %s (%d B) — %s dtk' % (NEW, os.path.getsize(NEW), round(time.time() - t0, 1)))
print('sha256 dex baru:', sha(newdex))
