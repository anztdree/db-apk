#!/usr/bin/env python3
# REBUILD RAW-COPY v13 — pola terdokumentasi Bagian 46/47/48/49 (kini Bagian 50).
# Base = APK v12 (release-mod/db-local(MOD-LOCALSERVER-v12).apk,
# sha256 1c108369…) — struktur RAW-COPY yang terbukti (6.428 entri,
# resources.arsc STORED aligned-4, 3471 deflate + 2957 stored).
#
# METODE: SEMUA entri disalin byte-per-byte (LFH + data raw, metode/flags/
# extra/alignment dipertahankan), HANYA classes2.dex diganti (dex console
# RAW SERVER LOG v13), CRC32/ukuran dikoreksi di LFH & CD, offset digeser,
# signing block lama (v2/v3) ikut hilang karena zip dibangun ulang murni
# LFH+CD+EOCD. Zipalign + tanda tangan dilakukan uber-apk-signer.
import struct, zlib, sys, hashlib, os

BASE = 'release-mod/db-local(MOD-LOCALSERVER-v12).apk'
DEX  = '/tmp/v13/classes2.dex'
OUT  = '/tmp/v13/v13_unsigned.apk'

def sha(b): return hashlib.sha256(b).hexdigest()

base = open(BASE, 'rb').read()
newdex = open(DEX, 'rb').read()
assert sha(base) == '1c1083692e496cb209d9c84fc5042b092ecef7878e97fc295057661ab42885ee', \
    'base BUKAN v12 proven: ' + sha(base)
print('sanity: base v12 OK — %d B' % len(base))

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
print('sanity: QHinfo proven — classes.dex %d B' % cd_['usize'])

# dex lama v12 untuk pembanding
old_dex_data = idx['classes2.dex']['data']
old_dex = old_dex_data if idx['classes2.dex']['method'] == 0 else zlib.decompress(old_dex_data, -15)
print('dex v12 (base): %d B — dex v13 (baru): %d B' % (len(old_dex), len(newdex)))
assert b'>v13</span>' in newdex and b'MOD-LOCALSERVER v13' in newdex
assert b'MOD-LOCALSERVER v12' not in newdex
assert b'Koneksi WebSocket' not in newdex and b'Frame socket' not in newdex
assert b'describe' not in newdex and b'stLbl' not in newdex
assert sha(newdex) == '323641505e9ea9a605b18ff92e78d183b25a75c1aff75d8bb52a395e20541a42', \
    'dex v13 tidak sesuai yang terverifikasi'

# ---- bangun zip baru ----
dex_crc = zlib.crc32(newdex) & 0xFFFFFFFF
dex_usize = len(newdex)
_c = zlib.compressobj(9, zlib.DEFLATED, -15)
dex_comp = _c.compress(newdex) + _c.flush()
dex_csize = len(dex_comp)
print('dex terkompresi: %d B (crc %08x)' % (dex_csize, dex_crc))
out = bytearray()
newoffs = []
diff_check = []
for e in entries:
    newoffs.append(len(out))
    is_dex = e['name'] == b'classes2.dex'
    if is_dex:
        assert e['method'] == 8, 'classes2.dex base bukan DEFLATED — metode harus dipertahankan'
        data, crc, usize, csize = dex_comp, dex_crc, dex_usize, dex_csize
        diff_check.append(('classes2.dex', e['usize'], usize))
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
    is_dex = e['name'] == b'classes2.dex'
    crc, csize, usize = (dex_crc, dex_csize, dex_usize) if is_dex else (e['crc'], e['csize'], e['usize'])
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
import zipfile
z = zipfile.ZipFile(OUT)
n2 = z.namelist()
assert len(n2) == len(entries), 'jumlah entri berubah'
diff = []
zb = zipfile.ZipFile(BASE)
for e in entries:
    n = e['name'].decode()
    if n == 'classes2.dex':
        assert z.read(n) == newdex, 'dex baru tidak persis'
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
assert diff == ['classes2.dex']
print('resources.arsc: STORED, data_off=%d (aligned-4 OK)' % data_off)
d8 = sum(1 for x in z.infolist() if x.compress_type == 8)
d0 = sum(1 for x in z.infolist() if x.compress_type == 0)
print('metode: %d deflate + %d stored' % (d8, d0))
assert (d8, d0) == (3471, 2957), 'metode kompresi berubah!'
print('REBUILD RAW-COPY v13 OK — %s (%d B)' % (OUT, os.path.getsize(OUT)))
print('sha256 dex v13:', sha(newdex))
