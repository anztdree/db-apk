#!/usr/bin/env python3
# Repack APK MOD-LOCALSERVER v14 — SDK CONFIG-FILE + SQLITE + PHP-FAITHFUL (Bagian 29)
# Jawaban owner 23 Nov:
#  - "kalau server aslinya php artinya kita juga harus secara php, kalau error ya
#    error. kalau work ya work, kalau alurnya A ya A. TIDAK ADA BYPASS"
#  - "jika php berat kita harus realistis cari yg setara misal sqlite"
# Isi v14 (classes2.dex baru; classes.dex BYTE-IDENTIK v11 — SDK smali game 100% original):
#  1. MESIN KAWAT PHP-FAITHFUL (mirror live qsdk.t4game.com, rekam 23 Nov — server asli
#     masih hidup dan diprobe ber-sign valid):
#       selalu HTTP 200; header X-Powered-By: PHP/5.4.37 + Content-Type: text/json;
#       urutan validasi = oracle live: data rusak->20000 "Params error",
#       sign salah->20002 "Params sign error" (algoritma TreeMap k=v&+secret
#       0b2a18e45d7df321 dari smali, DIVERIFIKASI diterima server asli),
#       param wajib kosong->20003 "LostSomeMustParams:<nama>" urutan ASLI
#       sdkVersion->productCode->authToken, product tak dikenal->30001
#       "Product doesn't exist".
#  2. SDK CONFIG SEBAGAI FILE (permintaan owner: config = file seperti bs/ & logs/):
#       <serverRoot>/sdk/{index.txt, profile.json, fallback.json, errors.json,
#       v1/system/init.json, v1/user/ckRegVistor.json} — SELF-SEEDING (dibuat bila
#       tak ada; TIDAK dikirim via assets karena ekstraksi assets menimpa tiap boot),
#       dibaca ulang TIAP request -> edit loginTypes = eksperimen crash tanpa rebuild!
#       init default tetap ["1"] (perilaku v13 dipertahankan; eksperimen = edit file).
#  3. STORAGE SQLITE ("setara realistis" pilihan owner):
#       Db/DbSqlite (android.database.sqlite, bawaan Android, data/db.sqlite) +
#       DbJson fallback desktop smoke; MIGRASI otomatis account.json/accounts.json
#       lama (uid/token PEMAIN TIDAK berubah); bindMail = UPDATE in-place (uid+token
#       tetap); backend aktif dilaporkan "DB-BACKEND ..." di log SYS.
#  4. Katalog error lokal digeser supaya tidak bentrok arti katalog ASLI:
#       salah sandi 20002->20004; findPassByEmail 20003->20005 (20002 live = SIGN,
#       20003 live = LostSomeMustParams — didokumentasikan di sdk/errors.json).
#  5. GET tanpa body data+sign = jalur diagnostik (dashboard/curl) — alur game POST
#     selalu lewat gerbang kontrak penuh.
#  - smoke desktop 37/37 PASS (gerbang 20000/20002/20003 bertahap/30001, header PHP,
#    live-reload kontrak, migrasi legacy, register/login/autoLogin/bindMail
#    uid-token-awet/changePassword/getUserInfo/ckReg/fallback/profile-edit).
#
#  - replace : assets/config_BS.properties (byte-identik v8, 256 B)
#              assets/QHinfo             (byte-identik v8, 21 B)
#              classes.dex               (identik v11 = original + HANYA starter)
#              classes2.dex              (server v14 — Bagian 29)
#  - tambah  : assets/server_root/** (2.758 file — TANPA sdk/; sdk/ self-seeding)
#  - buang   : META-INF signature lama
#  - TIDAK menyentuh: smali SDK/game (classes.dex byte-identik v11).
import zipfile, os, hashlib, time

SRC         = '/home/z/ws9/original.apk'
OUT         = "/home/z/ws9/db-local(MOD-LOCALSERVER-v14)-unsigned.apk"
CLASSES_DEX = "/home/z/ws9/classes_v11.dex"
SERVER_DEX  = '/home/z/ws9/classes2_v14.dex'
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
        replaced.append((name, 'REPLACE (identik v11: original + starter, %dB)' % len(data)))
    else:
        data = zin.read(name); copied += 1
    zi = zipfile.ZipInfo(name, date_time=info.date_time)
    zi.compress_type = info.compress_type          # pertahankan (resources.arsc STORED)
    zi.external_attr = info.external_attr
    zout.writestr(zi, data)

d2 = open(SERVER_DEX, 'rb').read()
zi = zipfile.ZipInfo('classes2.dex', date_time=(2026, 11, 23, 3, 40, 0))
zi.compress_type = zipfile.ZIP_DEFLATED
zi.external_attr = 0o600 << 16
zout.writestr(zi, d2)
print('classes2.dex ditambah:', len(d2), 'B (server v14: sdk config-file + sqlite + php-faithful, sha256', hashlib.sha256(d2).hexdigest()[:16] + ')')
replaced.append(('classes2.dex', 'ADD (mini-server v14 Bagian 29: gerbang kontrak mirror live 20000/20002/20003/30001, sign verify, sdk/ config self-seeding, SQLite+DB-BACKEND log, migrasi legacy)'))

n_server = 0
for full, rel in server_files():
    data = open(full, 'rb').read()
    zi = zipfile.ZipInfo(rel, date_time=(2026, 11, 23, 3, 40, 0))
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
