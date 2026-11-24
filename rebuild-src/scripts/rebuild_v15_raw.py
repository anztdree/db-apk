#!/usr/bin/env python3
# REBUILD RAW-COPY v15 — pola terdokumentasi Bagian 46/47/48/49/50/51 (kini Bagian 52).
# Base = APK v13 (release-mod/db-local(MOD-LOCALSERVER-v13).apk,
# sha256 533aeded…) — struktur RAW-COPY yang terbukti (6.428 entri,
# resources.arsc STORED aligned-4, 3471 deflate + 2957 stored).
#
# METODE: SEMUA entri disalin byte-per-byte (LFH + data raw, metode/flags/
# extra/alignment dipertahankan). v15 mengganti DUA entri:
#   classes.dex  = ORIGINAL + injeksi LocalServerStarter (warisan) + PATCH
#                  AIRPLANE-MODE (4 unit bedah, ukuran identik):
#                  QuickGame SDK memeriksa NetworkInfo.isConnected sebelum
#                  init → AlertDialog "network error" saat mode pesawat,
#                  PADAHAL semua server sudah 127.0.0.1. Patch:
#                    A) feedtonight.hoboismrelationbelow (SDK init)
#                       pc0018 offset if-eqz +10→+8 (null → init, bukan dialog)
#                       pc0022 move-result v0 → const/4 v0,1
#                    B) HWLoginActivity.onCreate
#                       pc0400 offset if-eqz +4→+2 (false → init, bukan dialog)
#                       pc0398 move-result v12 → const/4 v12,1
#                  SHA1 signature + adler32 header dex dihitung ulang.
#   classes2.dex = dex console SERVER LOG BERSIH v15 (javac 21 + D8):
#                  tombol tinggal CARI + JEDA (semua tombol sampah + 12 chip
#                  status + select level/port DIHAPUS), rincian headers/
#                  request/response/recv/send/data/error LANGSUNG TAMPIL
#                  utuh (NOL klik, NOL kolaps, NOL chevron).
# CRC32/ukuran dikoreksi di LFH & CD, offset digeser, signing block lama
# hilang (zip dibangun ulang murni LFH+CD+EOCD). Zipalign + signature oleh
# uber-apk-signer.
import struct, zlib, sys, hashlib, os, zipfile

BASE = 'release-mod/db-local(MOD-LOCALSERVER-v13).apk'
DEX1 = '/tmp/v15/classes_patched.dex'   # classes.dex  (airplane patch)
DEX2 = '/tmp/v15/dexout/classes.dex'    # classes2.dex (console v15)
OUT  = '/tmp/v15/v15_unsigned.apk'

def sha(b): return hashlib.sha256(b).hexdigest()

base = open(BASE, 'rb').read()
dex1 = open(DEX1, 'rb').read()
dex2 = open(DEX2, 'rb').read()
assert sha(base) == '533aedeee8b8dca957efb825a8c0f3606be84339d81d7dcdf6888daf330ec0f7', \
    'base BUKAN v13 proven: ' + sha(base)
print('sanity: base v13 OK — %d B' % len(base))

# ---- kontrak dex ----
assert dex1[:4] == b'dex\n' and dex2[:4] == b'dex\n', 'bukan dex'
assert sha(dex1) == '9db1bba326132a8eff93429a54c11d0440ab2c53f4c84b7b131fb1533d642658', \
    'classes.dex patched tidak sesuai: ' + sha(dex1)
assert sha(dex2) == 'e8d02201b76760ebe8a30b599d34b89e8e03783b35608d719fad2c49235f9971', \
    'classes2.dex v15 tidak sesuai: ' + sha(dex2)
# v15 console markers
for m in (b'MOD-LOCALSERVER v15', b'>v15<', b'semua rincian tampil langsung'):
    assert m in dex2, 'marker v15 hilang: %r' % m
for ban in (b'MOD-LOCALSERVER v14', b'>v14<', b'klik untuk buka', b'data-o=',
            b'copyTable', b'refreshStats', b'stchips', b'unduh arsip',
            b'bersihkan tampilan', b'cpcrash'):
    assert ban not in dex2, 'string v14/kolaps masih ada: %r' % ban
# v15 airplane patch markers (unit const/4 setelah isConnected — verifikasi
# struktural dilakukan patch_airplane.py; di sini cek ukuran identik dex v13)
import zipfile as zf
z13 = zf.ZipFile(BASE)
assert z13.getinfo('classes.dex').file_size == len(dex1), \
    'classes.dex harus ukuran IDENTIK (patch 4 unit, NOL perubahan ukuran)'
print('dex1 %d B (patch 4 unit, ukuran identik) — dex2 %d B' % (len(dex1), len(dex2)))

# ---- parse EOCD ----
eocd_i = base.rfind(b'PK\x05\x06')
assert eocd_i >= 0, 'EOCD tidak ditemukan'
(disk, cdisk, n_disk, n_tot, cd_size, cd_off, clen) = struct.unpack_from('<HHHHIIH', base, eocd_i + 4)
assert disk == 0 and cdisk == 0 and n_disk == n_tot, 'EOCD multi-disk?'
assert eocd_i + 22 + clen == len(base), 'ada comment/dataran setelah EOCD'
print('EOCD: %d entri, CD @ %d (%d B)' % (n_tot, cd_off, cd_size))

# ---- parse central directory → daftar entri raw ----
entries = []
i = cd_off
for _ in range(n_tot):
    sig, = struct.unpack_from('<I', base, i)
    assert sig == 0x02014b50, 'CD sig rusak di %d' % i
    (vmade, vneed, flags, method, mtime, mdate, crc, csize, usize,
     nlen, elen, clen2, dstart, iattr, eattr, loff) = struct.unpack_from('<HHHHHHIIIHHHHHII', base, i + 4)
    name = base[i + 46:i + 46 + nlen]
    extra = base[i + 46 + nlen:i + 46 + nlen + elen]
    comment = base[i + 46 + nlen + elen:i + 46 + nlen + elen + clen2]
    lfh_sig, = struct.unpack_from('<I', base, loff)
    assert lfh_sig == 0x04034b50, 'LFH sig rusak di %d' % loff
    lvneed, lflags, lmethod, lmtime, ldate, lcrc, lcsize, lusize, lnlen, lelen = \
        struct.unpack_from('<HHHHHIIIHH', base, loff + 4)
    assert lflags == flags and lmethod == method, 'LFH/CD flags-method beda: %r' % name
    assert (lflags & 0x8) == 0, 'data descriptor tidak didukung skrip ini: %r' % name
    lfh_fixed = base[loff:loff + 30]
    lfh_name  = base[loff + 30:loff + 30 + lnlen]
    lfh_extra = base[loff + 30 + lnlen:loff + 30 + lnlen + lelen]
    data = base[loff + 30 + lnlen + lelen:loff + 30 + lnlen + lelen + csize]
    assert name == lfh_name, 'nama LFH/CD beda: %r' % name
    entries.append(dict(name=name, vmade=vmade, vneed=vneed, flags=flags, method=method,
                        mtime=mtime, mdate=mdate, crc=crc, csize=csize, usize=usize,
                        iattr=iattr, eattr=eattr, lfh_fixed=bytearray(lfh_fixed),
                        lfh_extra=lfh_extra, cd_extra=extra, cd_comment=comment, data=data))
    i += 46 + nlen + elen + clen2
assert i == cd_off + cd_size, 'CD tidak habis tepat'

names = [e['name'].decode() for e in entries]
print('entri terbaca: %d (deflate=%d stored=%d)' % (
    len(entries),
    sum(1 for e in entries if e['method'] == 8),
    sum(1 for e in entries if e['method'] == 0)))

# ---- sanity kontrak rantai ----
for must in ('classes.dex', 'classes2.dex', 'assets/QHinfo',
             'assets/config_BS.properties',
             'assets/server_root/bs/resource/properties/clientversion.json'):
    assert must in names, 'entri kontrak hilang: ' + must
idx = {e['name'].decode(): e for e in entries}
qh = idx['assets/QHinfo']
qd = qh['data'] if qh['method'] == 0 else zlib.decompress(qh['data'], -15)
assert qd == b'http://127.0.0.1:9530', 'QHinfo berubah!'
cd_ = idx['classes.dex']
old_dex1_data = cd_['data']
old_dex1 = old_dex1_data if cd_['method'] == 0 else zlib.decompress(old_dex1_data, -15)
assert len(old_dex1) == len(dex1), 'dex1 ukuran beda dari base'
print('sanity: QHinfo proven — classes.dex base %d B' % len(old_dex1))

# ---- bangun zip baru ----
def comp(raw):
    _c = zlib.compressobj(9, zlib.DEFLATED, -15)
    return _c.compress(raw) + _c.flush()

newdata = {
    b'classes.dex':  (dex1, zlib.crc32(dex1) & 0xFFFFFFFF, len(dex1)),
    b'classes2.dex': (dex2, zlib.crc32(dex2) & 0xFFFFFFFF, len(dex2)),
}
comp_cache = {k: comp(v[0]) for k, v in newdata.items()}

out = bytearray()
newoffs = []
diff_check = []
for e in entries:
    newoffs.append(len(out))
    nm = e['name']
    if nm in newdata:
        assert e['method'] == 8, '%s base bukan DEFLATED — metode harus dipertahankan' % nm.decode()
        raw = newdata[nm][0]
        data = comp_cache[nm]
        crc, usize = newdata[nm][1], newdata[nm][2]
        csize = len(data)
        diff_check.append((nm.decode(), e['usize'], usize))
    else:
        data = e['data']
        crc, csize, usize = e['crc'], e['csize'], e['usize']
    lfh = bytearray(e['lfh_fixed'])
    struct.pack_into('<III', lfh, 14, crc, csize, usize)
    out += lfh
    out += e['name']
    out += e['lfh_extra']
    out += data

cd_start = len(out)
for k, e in enumerate(entries):
    nm = e['name']
    if nm in newdata:
        crc, csize, usize = newdata[nm][1], len(comp_cache[nm]), newdata[nm][2]
    else:
        crc, csize, usize = e['crc'], e['csize'], e['usize']
    rec = struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, e['vmade'], e['vneed'], e['flags'],
                      e['method'], e['mtime'], e['mdate'],
                      crc, csize, usize,
                      len(e['name']), len(e['cd_extra']), len(e['cd_comment']),
                      0, e['iattr'], e['eattr'], newoffs[k])
    out += rec + e['name'] + e['cd_extra'] + e['cd_comment']
cd_size = len(out) - cd_start
out += struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, len(entries), len(entries), cd_size, cd_start, 0)

open(OUT, 'wb').write(out)
print('tulis: %s — %d B' % (OUT, len(out)))

# ---- verifikasi ulang zip baru ----
z = zipfile.ZipFile(OUT)
n2 = z.namelist()
assert len(n2) == len(entries), 'jumlah entri berubah'
diff = []
zb = zipfile.ZipFile(BASE)
for e in entries:
    n = e['name'].decode()
    if n == 'classes.dex':
        assert z.read(n) == dex1, 'dex1 baru tidak persis'
        diff.append(n)
    elif n == 'classes2.dex':
        assert z.read(n) == dex2, 'dex2 baru tidak persis'
        diff.append(n)
    else:
        if z.read(n) != zb.read(n):
            raise AssertionError('entri berubah: ' + n)
info = z.getinfo('resources.arsc')
assert info.compress_type == 0, 'arsc tidak STORED!'
k = names.index('resources.arsc')
lo = newoffs[k]
lnlen, = struct.unpack_from('<H', out, lo + 26)
lelen, = struct.unpack_from('<H', out, lo + 28)
data_off = lo + 30 + lnlen + lelen
assert data_off % 4 == 0, 'arsc data_off tidak aligned-4: %d' % data_off
print('verifikasi: %d entri, beda HANYA %s' % (len(n2), diff))
assert diff == ['classes.dex', 'classes2.dex'], 'entri berubah tak terduga: %s' % diff
print('resources.arsc: STORED, data_off=%d (aligned-4 OK)' % data_off)
d8 = sum(1 for x in z.infolist() if x.compress_type == 8)
d0 = sum(1 for x in z.infolist() if x.compress_type == 0)
print('metode: %d deflate + %d stored' % (d8, d0))
assert (d8, d0) == (3471, 2957), 'metode kompresi berubah!'
print('REBUILD RAW-COPY v15 OK — %s (%d B)' % (OUT, os.path.getsize(OUT)))
print('sha256 classes.dex  :', sha(dex1))
print('sha256 classes2.dex :', sha(dex2))
