#!/usr/bin/env python3
# ============================================================
# ASSEMBLE APK — rebuild 1x dari ORIGINAL.apk (NOL sentuh gameplay).
# Perubahan HANYA:
#   1. TAMBAH classes2.dex (kernel mini-server, seed tertanam)
#   2. REUSE AndroidManifest.xml hasil edit lama (+1 provider)
#   3. REUSE assets/config.properties + config_BS.properties
#      (EDIT ENDPOINT -> http://127.0.0.1:4000)
#   NOL entri assets/server — zona server hidup di
#   Android/data/com.db.local/server (SEBELAH folder files/),
#   ditanam kernel saat boot pertama.
# ============================================================
import zipfile, hashlib, os, sys

REPO = os.path.dirname(os.path.abspath(__file__)) + '/..'
ORIG = os.path.join(REPO, 'db-local(Original-DO NOT DELETE!).apk')
EXTRACT = os.path.join(REPO, 'extract')
DEX = os.path.join(REPO, 'kernel/out/classes2.dex')
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, 'out/db-local-rebuild-miniserver-02-unsigned.apk')

reuse = {
    'AndroidManifest.xml': open(os.path.join(EXTRACT, 'AndroidManifest.xml'), 'rb').read(),
    'assets/config.properties': open(os.path.join(EXTRACT, 'assets_config.properties'), 'rb').read(),
    'assets/config_BS.properties': open(os.path.join(EXTRACT, 'assets_config_BS.properties'), 'rb').read(),
}
dex = open(DEX, 'rb').read()

orig = zipfile.ZipFile(ORIG, 'r')
out = zipfile.ZipFile(OUT, 'w')
kept = 0
for info in orig.infolist():
    name = info.filename
    if name.startswith('META-INF/'):
        continue  # tanda tangan lama -> sign ulang
    data = reuse.get(name)
    if data is None:
        data = orig.read(name)
        kept += 1
    out.writestr(zipfile.ZipInfo(name, info.date_time), data, compress_type=info.compress_type)
out.writestr(zipfile.ZipInfo('classes2.dex', (2026, 11, 19, 0, 0, 0)), dex, compress_type=zipfile.ZIP_DEFLATED)
out.close()

z = zipfile.ZipFile(OUT)
names = z.namelist()
assert not any(n.startswith('assets/server/') for n in names), 'assets/server DILARANG ada'
assert z.getinfo('resources.arsc').compress_type == 0, 'resources.arsc wajib STORED'
print(f'assemble OK: {kept} entri asli identik + 3 reuse + classes2.dex ({len(dex)} B)')
print(f'sha256 unsigned: {hashlib.sha256(open(OUT, "rb").read()).hexdigest()}')
