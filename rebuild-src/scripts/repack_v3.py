#!/usr/bin/env python3
# REBUILD "v3" — SATU PASS dari APK ORIGINAL (base yang sama dgn v2).
#
# PERISTIWA 23 Nov malam (log device 20:37 + 22:43, sesi v18/v2):
#   1. POPUP PENGINGAT — /bs/resource/properties/clientversion.json = 404
#      → smali LaunchActivity/reweavingsiamesedpropertylessnesses memetakan
#      exception "404" → string "404" → POPUP "Server clientversion is Not
#      Exist" (pola 1:1 dgn "Server base.version is Not Exist" Bagian 19).
#      Server original tidak pernah menampilkan teks ini karena file tsb ADA
#      di CDN original. FIX = clientversion.json {"clientVersion":"202109150935"}.
#   2. SDK/GAME STUCK "selesai loading" — TIDAK PERNAH ada satu pun request
#      socket.io di 6 log device (5050/5051 tak pernah disentuh JS). Satu-
#      satunya host:port yang runtime XHR Egret bukti tercapai = ORIGIN
#      HALAMAN (9528) — manifest/clientversion/serversetting semua datang
#      dari 9528. FIX 2 arah:
#        a. serversetting.json loginserver + config bin loginPort → 9528
#           (origin halaman; loginchecknative tetap 5050 via loginWebPort —
#           split terverifikasi smali: cointhreat()=loginPort utk JS,
#           seventygenom()=loginWebPort utk HttpURLConnection native)
#        b. SocketIoHandler di-chain di listener 9528 untuk path /socket.io/
#           → handshake dijawab DI PORT YANG SAMA apa pun perilaku parser
#           URL runtime.
#
# PRINSIP TIDAK BERUBAH (sesuai keputusan owner):
#   A. APK ORIGINAL hanya DIEDIT ENDPOINT:
#      - assets/QHinfo              : http://127.0.0.1:9530 (byte-identik v2)
#      - assets/config_BS.properties: http://127.0.0.1:9527 (byte-identik v2)
#      - classes.dex                : 100% original + starter (+2 baris) —
#        byte-identik rantai v11..v18 (sha256 1df64d91…, terverifikasi ulang)
#   B. MINI-SERVER = classes2.dex (v3: +chain socket.io 9528) +
#      assets/server_root/** (v3: 3 file kontrak berubah/ditambah).
#   C. TIDAK MENYENTUH: smali SDK/game, resources.arsc, AndroidManifest.xml,
#      lib/**, assets game lain.
import zipfile, os, hashlib, time

ORIG = 'work/original.apk'
V18  = 'work/v18.apk'
NEWDEX = 'tools/out/dex/classes.dex'
OUT  = 'tools/out/db-local(MOD-LOCALSERVER-v3)-unsigned.apk'

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

server_root = sorted(n for n in vnames if n.startswith('assets/server_root/'))
orig_cls = zo.read('classes.dex')
print('sanity: original %d entri | v18 %d entri | server_root %d file' %
      (len(onames), len(vnames), len(server_root)))
print('sanity: classes.dex original %d B -> proven %d B (starter %s)' %
      (len(orig_cls), len(cls_proven), 'ADA' if len(cls_proven) != len(orig_cls) else 'SAMA'))

# ---------- v3 kontrak: 2 diganti + 1 baru (src = release-mod/server/**) ----------
def rd(rel):
    return open('release-mod/server/' + rel, 'rb').read()

SS = 'assets/server_root/bs/resource/properties/serversetting.json'
CV = 'assets/server_root/bs/resource/properties/clientversion.json'
BIN = 'assets/server_root/bs/db/android/setting_BS_Android.bin'

new_ss = rd('bs/resource/properties/serversetting.json')
new_cv = rd('bs/resource/properties/clientversion.json')
new_bin = rd('bs/db/android/setting_BS_Android.bin')
assert b'"loginserver": "http://127.0.0.1:9528"' in new_ss
assert b'clientVersion' in new_cv
# XOR "DragonBall" round-trip pada bin baru
key = b'DragonBall'
dec = bytes(b ^ key[i % len(key)] for i, b in enumerate(new_bin)).decode('utf-8')
assert '"loginPort": 9528' in dec and '"loginWebPort": 5050' in dec
print('v3 kontrak: serversetting(loginserver 9528) + clientversion(BARU) + bin(loginPort 9528, loginWebPort 5050)')

def is_signature(name):
    n = name.upper()
    return (n.startswith('META-INF/') and (
        n.endswith('.SF') or n.endswith('.RSA') or n.endswith('.DSA') or
        n.endswith('.EC') or n == 'META-INF/MANIFEST.MF' or
        n.startswith('META-INF/COM/ANDROID/BUILD')))

OVERRIDE = {SS: new_ss, BIN: new_bin}
NEWFILES = {CV: new_cv}

zout = zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED)
kept = replaced = added = 0
changed_report = []
for item in zo.infolist():
    if is_signature(item.filename):
        continue                                    # signature lama dibuang
    zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
    zi.compress_type = item.compress_type
    zi.external_attr = item.external_attr
    if item.filename == 'classes.dex':
        zout.writestr(zi, cls_proven)
        replaced += 1
        changed_report.append(('classes.dex (starter proven)', item.file_size, len(cls_proven)))
    elif item.filename == 'assets/QHinfo':
        zout.writestr(zi, qhinfo)
        replaced += 1
        changed_report.append(('assets/QHinfo', item.file_size, len(qhinfo)))
    elif item.filename == 'assets/config_BS.properties':
        zout.writestr(zi, zv.read('assets/config_BS.properties'))
        replaced += 1
        changed_report.append(('assets/config_BS.properties', item.file_size,
                               len(zv.read('assets/config_BS.properties'))))
    else:
        zout.writestr(zi, zo.read(item.filename))
        kept += 1
for n in server_root:
    zi = zipfile.ZipInfo(n, date_time=zv.getinfo(n).date_time)
    zi.compress_type = zv.getinfo(n).compress_type
    zi.external_attr = zv.getinfo(n).external_attr
    if n in OVERRIDE:
        zout.writestr(zi, OVERRIDE[n])
        changed_report.append((n, zv.getinfo(n).file_size, len(OVERRIDE[n])))
    else:
        zout.writestr(zi, zv.read(n))
    added += 1
for n, data in NEWFILES.items():
    zi = zipfile.ZipInfo(n, date_time=zv.getinfo(SS).date_time)
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = zv.getinfo(SS).external_attr
    zout.writestr(zi, data)
    added += 1
    changed_report.append((n + ' (BARU)', 0, len(data)))
zi = zipfile.ZipInfo('classes2.dex', date_time=zv.getinfo('classes2.dex').date_time)
zi.compress_type = zipfile.ZIP_DEFLATED
zout.writestr(zi, newdex)
added += 1
changed_report.append(('classes2.dex v3', zv.getinfo('classes2.dex').file_size, len(newdex)))
zout.close(); zo.close(); zv.close()

size = os.path.getsize(OUT)
print('REPACK v3 OK: %d byte-identik + %d diganti (endpoint+starter) + %d server_root/classes2' %
      (kept, replaced, added))
for f, a, b in changed_report:
    print('  %-58s %6d -> %6d B' % (f, a, b))
print('classes2.dex v3: %d B sha256 %s' % (len(newdex), sha(newdex)[:16]))
print('unsigned: %s (%d B) sha256 %s' % (OUT, size, sha(open(OUT, 'rb').read())[:16]))
print('durasi %.1fs' % (time.time() - t0))
