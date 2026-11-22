#!/usr/bin/env python3
# Repack APK MOD-LOCALSERVER v11 — SDK 100% ORIGINAL (Bagian 25)
# Koreksi atas temuan uji device v10: auto-register + popup spinner disebabkan
# patch feedtonight (ACTION_LOGIN -> ACTION_AUTO_REGISTER_AND_LOGIN) yang masih
# dibawa v9/v10. Bukti smali: HWLoginActivity$singersmooth.onServiceConnected
#   - ACTION_LOGIN                  -> tampilkan fragment UI login (tombol Guest)
#   - ACTION_AUTO_REGISTER_AND_LOGIN-> log "游客模式"/"游客登录" -> registerVisitor
#     langsung + WaitingDialog, TANPA UI  (persis urutan logcat device 23 Nov)
#
#  - replace : assets/config_BS.properties (byte-identik v8, 256 B)
#              assets/QHinfo             (byte-identik v8, 21 B)
#              classes.dex               (original + HANYA starter LocalServerStarter)
#  - tambah  : classes2.dex (mini-server dari v8, byte-identik) + assets/server_root/** (2.758 file)
#  - buang   : META-INF signature lama
#  - TIDAK menyentuh: feedtonight.smali (ORIGINAL), f/a/n.smali (setCancelable tetap 0x0),
#                     QuickGameSdkService.smali, dan seluruh smali SDK/game lain.
import zipfile, os, hashlib, time

SRC         = '/home/z/ws9/original.apk'
OUT         = "/home/z/ws9/db-local(MOD-LOCALSERVER-v11)-unsigned.apk"
CLASSES_DEX = "/home/z/ws9/classes_v11.dex"
SERVER_DEX  = '/home/z/ws9/classes2_v8.dex'
SERVER_ROOT = '/home/z/ws9/server_root'
CFG_V8      = '/home/z/ws9/config_BS_v8.properties'
QH_V8       = '/home/z/ws9/QHinfo_v8'

new_cfg = open(CFG_V8, 'rb').read()
QH      = open(QH_V8, 'rb').read()
assert len(new_cfg) == 256, 'config_BS harus 256 B'
assert QH == b'http://127.0.0.1:9530', 'QHinfo harus http://127.0.0.1:9530'
import base64
dec = base64.b64decode(new_cfg.decode().split('EntryPoint1=')[1].split('\n')[0]).decode()
assert dec == 'http://127.0.0.1:9527/bs/db/android', 'URL config salah: %s' % dec

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
print('entri original:', len(names))

zout = zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED, compresslevel=9)
copied = 0
replaced = []
for name in names:
    if is_signature(name):
        continue
    info = zin.getinfo(name)
    if name == 'assets/config_BS.properties':
        data = new_cfg; replaced.append((name, 'REPLACE (endpoint local, byte-identik v8)'))
    elif name == 'assets/QHinfo':
        data = QH; replaced.append((name, 'REPLACE (endpoint SDK local, byte-identik v8)'))
    elif name == 'classes.dex':
        data = open(CLASSES_DEX, 'rb').read()
        replaced.append((name, 'REPLACE (original + HANYA starter, feedtonight ORIGINAL, %dB)' % len(data)))
    else:
        data = zin.read(name); copied += 1
    zi = zipfile.ZipInfo(name, date_time=info.date_time)
    zi.compress_type = info.compress_type          # pertahankan (resources.arsc STORED)
    zi.external_attr = info.external_attr
    zout.writestr(zi, data)

d2 = open(SERVER_DEX, 'rb').read()
zi = zipfile.ZipInfo('classes2.dex', date_time=(2026, 11, 23, 0, 0, 0))
zi.compress_type = zipfile.ZIP_DEFLATED
zi.external_attr = 0o600 << 16
zout.writestr(zi, d2)
print('classes2.dex ditambah:', len(d2), 'B (byte-identik v8, sha256', hashlib.sha256(d2).hexdigest()[:16] + ')')
replaced.append(('classes2.dex', 'ADD (mini-server, byte-identik v8)'))

n_server = 0
for full, rel in server_files():
    data = open(full, 'rb').read()
    zi = zipfile.ZipInfo(rel, date_time=(2026, 11, 23, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_STORED if rel.endswith('.zip') else zipfile.ZIP_DEFLATED
    zi.external_attr = 0o600 << 16
    zout.writestr(zi, data)
    n_server += 1
print('assets/server_root ditambah:', n_server, 'file')

zout.close()
sz = os.path.getsize(OUT)
h = hashlib.sha256(open(OUT, 'rb').read()).hexdigest()
print('unsigned APK:', sz, 'B, sha256', h[:16] + '…', '| waktu %.1fs' % (time.time() - t0))
for r in replaced:
    print(' ', r[0], '->', r[1])
