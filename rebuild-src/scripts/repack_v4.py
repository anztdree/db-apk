#!/usr/bin/env python3
# Repack APK MOD-LOCALSERVER v4 (Bagian 19 — ANTI-RACE + DASHBOARD v4)
#  - replace : assets/config_BS.properties (9527, identik artefak), assets/QHinfo (9530),
#              classes.dex (patch starter di Application.onCreate — dipakai ulang byte-identik dari v2)
#  - tambah  : classes2.dex (mini-server v3: + DiskWriter NDJSON, buffer 15000/6000,
#              dashboard live, /download, since, chunked, HEAD, headers, milestone),
#              assets/server_root/** (isi server/ — dipakai ulang byte-identik dari v2)
#  - buang   : META-INF signature lama (di-sign ulang uber-apk-signer)
#  - pertahankan compress_type per entry (resources.arsc wajib STORED utk targetSdk 30)
import zipfile, os, sys, base64, hashlib, time

SRC = '/home/z/db-apk-analysis-ws/db-local(Original-DO NOT DELETE!).apk'
OUT = '/home/z/db-apk-analysis-ws/rebuild/work/db-local(MOD-LOCALSERVER-v4)-unsigned.apk'
PATCHED_DEX = '/home/z/db-apk-analysis-ws/rebuild/work/classes_patched.dex'   # dari v2 (starter) — byte-identik
SERVER_DEX  = '/home/z/db-apk-analysis-ws/rebuild/work/dexout/classes.dex'   # mini-server v4
SERVER_ROOT = '/home/z/db-apk-analysis-ws/rebuild/work/server_root'           # dari v2 — byte-identik

NEW_URL = 'http://127.0.0.1:9527/bs/db/android'
new_cfg = (
    'EntryPoint1=' + base64.b64encode(NEW_URL.encode()).decode() + '\n'
    'EntryPoint1_debug=' + base64.b64encode(NEW_URL.encode()).decode() + '\n'
    '\n'
    'EntryPoint2=' + base64.b64encode(NEW_URL.encode()).decode() + '\n'
    'EntryPoint2_debug=' + base64.b64encode(NEW_URL.encode()).decode()
).encode('utf-8')
QH = b'http://127.0.0.1:9530'

# bandingkan dengan artefak v2 (harus identik byte)
chk_cfg = open('/home/z/db-apk-analysis-ws/rebuild/check/assets/config_BS.properties', 'rb').read()
chk_qh = open('/home/z/db-apk-analysis-ws/rebuild/check/assets/QHinfo', 'rb').read()
assert new_cfg == chk_cfg, 'config_BS beda dari v2! (%d vs %d)' % (len(new_cfg), len(chk_cfg))
assert QH == chk_qh, 'QHinfo beda dari v2!'
print('config_BS.properties:', len(new_cfg), 'B — identik artefak v2')
print('QHinfo              :', len(QH), 'B — identik artefak v2')

def is_signature(name):
    n = name.upper()
    return (n.startswith('META-INF/') and (
        n.endswith('.SF') or n.endswith('.RSA') or n.endswith('.DSA') or n.endswith('.EC')
        or n == 'META-INF/MANIFEST.MF' or n.startswith('META-INF/COM/ANDROID/BUILD')))

def server_files():
    out = []
    for root, dirs, files in os.walk(SERVER_ROOT):
        dirs.sort()
        for f in sorted(files):
            full = os.path.join(root, f)
            rel = 'assets/server_root/' + os.path.relpath(full, SERVER_ROOT).replace(os.sep, '/')
            out.append((full, rel))
    return out

t0 = time.time()
zin = zipfile.ZipFile(SRC)
names = zin.namelist()
print('entri asli:', len(names))

zout = zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED, compresslevel=9)
copied = 0
replaced = []
for name in names:
    if is_signature(name):
        continue
    info = zin.getinfo(name)
    if name == 'assets/config_BS.properties':
        data = new_cfg; replaced.append((name, 'REPLACE'))
    elif name == 'assets/QHinfo':
        data = QH; replaced.append((name, 'REPLACE'))
    elif name == 'classes.dex':
        data = open(PATCHED_DEX, 'rb').read(); replaced.append((name, 'REPLACE (starter, byte-identik v2)'))
    else:
        data = zin.read(name); copied += 1
    zi = zipfile.ZipInfo(name, date_time=info.date_time)
    zi.compress_type = info.compress_type          # pertahankan (resources.arsc STORED)
    zi.external_attr = info.external_attr
    zout.writestr(zi, data)

# classes2.dex (mini-server v3) — DEFLATED
d2 = open(SERVER_DEX, 'rb').read()
zi = zipfile.ZipInfo('classes2.dex', date_time=(2026, 11, 22, 16, 20, 0))
zi.compress_type = zipfile.ZIP_DEFLATED
zi.external_attr = 0o600 << 16
zout.writestr(zi, d2)
print('classes2.dex ditambah:', len(d2), 'B')
replaced.append(('classes2.dex', 'ADD (mini-server v3)'))

# assets/server_root/** — .zip STORED, lain DEFLATED
n_server = 0
for full, rel in server_files():
    data = open(full, 'rb').read()
    zi = zipfile.ZipInfo(rel, date_time=(2026, 11, 22, 13, 0, 0))
    zi.compress_type = zipfile.ZIP_STORED if rel.endswith('.zip') else zipfile.ZIP_DEFLATED
    zi.external_attr = 0o600 << 16
    zout.writestr(zi, data)
    n_server += 1
print('assets/server_root ditambah:', n_server, 'file')

zout.close()
sz = os.path.getsize(OUT)
h = hashlib.sha256(open(OUT, 'rb').read()).hexdigest()
print('unsigned APK:', sz, 'B, sha256', h[:16] + '…', '| waktu %.1fs' % (time.time() - t0))
print('replaced:', replaced)
