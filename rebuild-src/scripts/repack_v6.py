#!/usr/bin/env python3
# REPACK v6 — SWAP classes2.dex di atas APK v5 (rantai terdokumentasi,
# pola sama dgn v11..v18 & v1..v5: "beda HANYA classes2.dex + 3 META-INF").
#
# ISI PERUBAHAN v6 (Bagian 42 — laporan device v5, kata-kata persis owner):
#   "maksud saya gini kok adaa teks popup mengingat. padahal setahu saya
#    aslinya itu emang gak ada.. dan saya mohon jika emang gk ada jangan
#    di paksakan untuk ada"
#   AKAR (bukti smali APK v5): parser sesi SDK psalmicvolleyball/cointhreat
#   membaca key "tips" BILA ADA (json.has) → feedtonight.worktopichardtails
#   (QGUserHolder) menampilkan AlertDialog judul hw_network_dialog_title
#   ("提醒"/Notification) berisi teks tips SETIAP selesai register/login —
#   syarat: authToken non-empty (SELALU utk sesi sukses) + userData != null
#   + tips != null. Response lama kita "tips":"" (bukan null) → popup muncul.
#   Server ASLI tidak pernah mengirim key ini (live capture 20261122 = nol).
#   FIX: key "tips" DIHAPUS dari AccountStore.sessionJsonOf — popup tidak
#   pernah ada lagi; alur login/register 100% tak berubah (key opsional).
#   - GUARD REGRESI SMOKE: 3 test v42 (registerVisitor/autoLogin/loginByName
#     tidak boleh memuat "tips") — 127/127 PASS.
#   - Penanda versi device: BOOT/crash/dashboard = v6 (BOOT & crash sebelumnya
#     masih v4 di Bagian 41 — kini konsisten).
#   - classes.dex (kode game, starter +2 baris) TIDAK tersentuh.
#   - Endpoint/assets: 100% byte-identik (QHinfo 9530, config_BS 9527,
#     clientversion.json, serversetting.json, setting bin 9528).
import zipfile, os, hashlib, sys, time

V5   = 'release-mod/db-local(MOD-LOCALSERVER-v5).apk'
NEW  = 'work/v6-unsigned.apk'
DEX  = 'tools/out/dex/classes.dex'

def sha(b): return hashlib.sha256(b).hexdigest()

t0 = time.time()
newdex = open(DEX, 'rb').read()

SKIP = ('META-INF/MANIFEST.MF', 'META-INF/DBLOCALM.SF', 'META-INF/DBLOCALM.RSA')
zv = zipfile.ZipFile(V5)
names = zv.namelist()

# sanity: entri kontrak v5
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
print('sanity: v5 OK — %d entri, classes.dex proven, endpoint benar' % len(names))

# sanity: dex baru = v6 + TANPA tips (akar popup pengingat)
for needle in (b'DB MONITOR', b'overlay OFF (normal)', b'>v6</span>'):
    assert needle in newdex, 'dex baru tidak memuat marker: ' + needle.decode()
assert b'"tips"' not in newdex, 'dex baru MASIH memuat "tips" — akar popup tidak hilang!'
print('sanity: classes2.dex baru %d B — marker v6 ada, TANPA "tips"' % len(newdex))

# ---------- tulis v6-unsigned ----------
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
za, zb = zipfile.ZipFile(V5), zipfile.ZipFile(NEW)
na, nb = set(za.namelist()), set(zb.namelist())
gone, new_ = sorted(na - nb), sorted(nb - na)
diff = sorted(n for n in (na & nb) if za.read(n) != zb.read(n))
print('diff v5->v6: hilang=%s tambah=%s beda=%s' % (gone, new_, diff))
assert sorted(gone) == sorted(SKIP) and not new_ and diff == ['classes2.dex'], \
    'DIFF BUKAN HANYA classes2.dex + META-INF!'
assert sha(zb.read('classes.dex')) == \
    '1df64d91219c01db729b13449a606ad88326d1aa260f3fa316aa44dc6ead787e'
print('VERIFIKASI: diff bersih — classes2.dex saja; classes.dex proven utuh')
print('selesai %.1fs — %s (%d B) sha256 %s' %
      (time.time() - t0, NEW, os.path.getsize(NEW), sha(open(NEW, 'rb').read())))
