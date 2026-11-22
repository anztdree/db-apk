#!/usr/bin/env python3
# Repack APK MOD-LOCALSERVER v6 (Bagian 21 — LOGIN NATURAL + LOGGER v5)
#  - replace : assets/config_BS.properties (byte-identik v5), assets/QHinfo (byte-identik v5),
#              classes.dex (starter hook + PATCH LOGIN TAMU: feedtonight.hardlinerspare
#              ACTION_LOGIN -> ACTION_AUTO_REGISTER_AND_LOGIN)
#  - tambah  : classes2.dex (mini-server v6: 5050 event ACK + loginchecknative + akun tamu
#              persisten + 5051 server game enterGame + dashboard logger v5; 22 class)
#  - tambah  : assets/server_root/** (byte-identik v5 — serversetting.json sudah 5050)
#  - buang   : META-INF signature lama
import zipfile, os, sys, base64, hashlib, time

SRC         = '/home/z/db-apk-analysis-ws/original.apk'
V5          = '/home/z/db-apk-analysis-ws/release-mod/db-local(MOD-LOCALSERVER-v5).apk'
OUT         = '/home/z/db-apk-analysis-ws/rebuild/work/db-local(MOD-LOCALSERVER-v6)-unsigned.apk'
CLASSES_DEX = '/home/z/db-apk-analysis-ws/rebuild/work/classes_v6.dex'   # starter + patch login tamu
SERVER_DEX  = '/home/z/db-apk-analysis-ws/rebuild/work/dexout6/classes.dex'  # mini-server v6
SERVER_ROOT = '/home/z/db-apk-analysis-ws/rebuild/work/server_root'      # byte-identik v5

NEW_URL = 'http://127.0.0.1:9527/bs/db/android'
new_cfg = (
    'EntryPoint1=' + base64.b64encode(NEW_URL.encode()).decode() + '\n'
    'EntryPoint1_debug=' + base64.b64encode(NEW_URL.encode()).decode() + '\n'
    '\n'
    'EntryPoint2=' + base64.b64encode(NEW_URL.encode()).decode() + '\n'
    'EntryPoint2_debug=' + base64.b64encode(NEW_URL.encode()).decode()
).encode('utf-8')
QH = b'http://127.0.0.1:9530'

v5z = zipfile.ZipFile(V5)
chk_cfg = v5z.read('assets/config_BS.properties')
chk_qh  = v5z.read('assets/QHinfo')
assert new_cfg == chk_cfg, 'config_BS beda dari v5!'
assert QH == chk_qh, 'QHinfo beda dari v5!'
# server_root v5 == work tree (byte-identik)
bad = 0
for name in v5z.namelist():
    if name.startswith('assets/server_root/'):
        rel = name[len('assets/server_root/'):]
        full = os.path.join(SERVER_ROOT, rel)
        if not os.path.exists(full) or open(full, 'rb').read() != v5z.read(name):
            bad += 1
assert bad == 0, 'server_root beda dari v5: %d file' % bad
print('config_BS.properties:', len(new_cfg), 'B — byte-identik v5')
print('QHinfo              :', len(QH), 'B — byte-identik v5')
print('server_root         : byte-identik v5 (diff 0)')

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
        data = open(CLASSES_DEX, 'rb').read()
        replaced.append((name, 'REPLACE (starter + patch login tamu, %dB)' % len(data)))
    else:
        data = zin.read(name); copied += 1
    zi = zipfile.ZipInfo(name, date_time=info.date_time)
    zi.compress_type = info.compress_type          # pertahankan (resources.arsc STORED)
    zi.external_attr = info.external_attr
    zout.writestr(zi, data)

d2 = open(SERVER_DEX, 'rb').read()
zi = zipfile.ZipInfo('classes2.dex', date_time=(2026, 11, 22, 19, 30, 0))
zi.compress_type = zipfile.ZIP_DEFLATED
zi.external_attr = 0o600 << 16
zout.writestr(zi, d2)
print('classes2.dex ditambah:', len(d2), 'B (sha256', hashlib.sha256(d2).hexdigest()[:16] + ')')
replaced.append(('classes2.dex', 'ADD (mini-server v6 — login natural + 5051 + logger v5)'))

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
