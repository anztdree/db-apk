#!/usr/bin/env python3
# REBUILD RAW-COPY v19 — pola terdokumentasi Bagian 46..55 (kini 56).
# Base = APK v13 (release-mod/db-local(MOD-LOCALSERVER-v13).apk,
# sha256 533aeded…) — struktur RAW-COPY yang terbukti (6.428 entri,
# resources.arsc STORED aligned-4, 3471 deflate + 2957 stored).
#
# METODE: SEMUA entri disalin byte-per-byte (LFH + data raw, metode/flags/
# extra/alignment dipertahankan). v19 mengganti DUA entri:
#   classes.dex  = ORIGINAL + injeksi LocalServerStarter (warisan) + PATCH
#                  AIRPLANE-MODE v19 (8 unit: 6 unit warisan v15/v18 + 2 unit
#                  BARU — gerbang dialog 'mengingat'/'Koneksi ke jaringan
#                  gagal…' → goto melewati dialog):
#                    feedtonight.hoboismrelationbelow pc=001c @5e8180
#                      38003700 -> 28020000 (goto init; nop)
#                    HWLoginActivity.onCreate pc=0194 @5d2438
#                      380c1400 -> 28020000 (goto bindService; nop)
#                  AKAR v19: bedah penuh menemukan bytes patch v15 SALAH RAKIT
#                  (nibble kebalik: [12 10] = const/4 v1,0 BUKAN const/4 v0,1)
#                  — mode pesawat v0=null → gerbang if-eqz v0 TERAMBIL → dialog
#                  SDK init TETAP MUNCUL sejak v15. v19 menutup gerbangnya.
#                  ukuran IDENTIK 6.697.176 B.
#                  sha256 62803bb1592b378da3ebf2664795f538193b6b84a302b44ee247d7e5c1fcc8b4
#   classes2.dex = dex console v18 byte-identik + 4 penanda v18->v19 (judul,
#                  banner, BOOT, crash report) — SHA1+adler header dihitung ulang.
#                  sha256 55e7ff88aff467350d4c52e558582dfe6b59859c33620427db512fd24b58c67b
# CRC32/ukuran dikoreksi di LFH & CD, offset digeser, signing block lama
# hilang (zip dibangun ulang murni LFH+CD+EOCD). Zipalign + signature oleh
# uber-apk-signer (keystore dblocalmod, install-over aman).
import struct, zlib, sys, hashlib, os, zipfile

BASE = 'release-mod/db-local(MOD-LOCALSERVER-v13).apk'
DEX1 = '/tmp/v19/dexout/classes.dex'    # classes.dex  (airplane patch v19)
DEX2 = '/tmp/v19/dexout/classes2.dex'   # classes2.dex (console v19)
OUT  = '/tmp/v19/v19_unsigned.apk'

def sha(b): return hashlib.sha256(b).hexdigest()

base = open(BASE, 'rb').read()
dex1 = open(DEX1, 'rb').read()
dex2 = open(DEX2, 'rb').read()
assert sha(base) == '533aedeee8b8dca957efb825a8c0f3606be84339d81d7dcdf6888daf330ec0f7', \
    'base BUKAN v13 proven: ' + sha(base)
print('sanity: base v13 OK — %d B' % len(base))

# ---- kontrak dex ----
assert dex1[:4] == b'dex\n' and dex2[:4] == b'dex\n', 'bukan dex'
assert sha(dex1) == '62803bb1592b378da3ebf2664795f538193b6b84a302b44ee247d7e5c1fcc8b4', \
    'classes.dex v19 tidak sesuai (patch_airplane_v19.py): ' + sha(dex1)
assert sha(dex2) == '55e7ff88aff467350d4c52e558582dfe6b59859c33620427db512fd24b58c67b', \
    'classes2.dex v19 tidak sesuai (patch_console_v19.py): ' + sha(dex2)
# v19 console markers
for m in (b'MOD-LOCALSERVER v19', b'>v19<', b'URL penuh', b'nol disembunyikan',
          b'semua rincian tampil langsung', b'id=errf', b'doCopy',
          b'fallbackCopy', b'tempel ke chat dev', b'filter ERROR aktif',
          b'open (ws-first)', b'loginServer scope', b'EVENT (no ack)'):
    assert m in dex2, 'marker v19 hilang: %r' % m
for ban in (b'MOD-LOCALSERVER v18', b'>v18<', b'klik untuk buka', b'data-o=',
            b'copyTable', b'refreshStats', b'stchips', b'unduh arsip',
            b'bersihkan tampilan', b'cpcrash', b'id=cpt', b'id=dl '):
    assert ban not in dex2, 'string v18/lama masih ada: %r' % ban
# fabrikasi loginServer TETAP dihapus dari console dex (warisan v18)
for ban in (b'DragonBall Local', b'serverListJson', b'enterGameResponse',
            b'routeEvent', b'ACK: 43'):
    assert ban not in dex2, 'pekerjaan loginServer masih ada di dex2: %r' % ban
for cut in (b'seen.length() < 1800', b'ack.substring(0, 400)'):
    assert cut not in dex2, 'pangkas lama masih ada: %r' % cut
# airplane patch v19 markers (dex1 — verifikasi struktural unit patch)
assert len(dex1) == 6697176, 'dex1 harus ukuran IDENTIK (patch 8 unit, NOL perubahan ukuran)'
assert dex1[0x5e816c] == 0x08 and dex1[0x5e8174:0x5e8176] == b'\x12\x10', 'patch warisan feedtonight hilang'
assert dex1[0x5d242c:0x5d242e] == b'\x12\x1c' and dex1[0x5d2430] == 0x02, 'patch warisan HWLoginActivity hilang'
assert dex1[0x5fd70c:0x5fd710] == bytes([0x12, 0x00, 0x0F, 0x00]), 'patch warisan v18 LaunchActivity.feedtonight hilang'
assert dex1[0x5e8180:0x5e8184] == bytes([0x28, 0x02, 0x00, 0x00]), 'patch BARU v19 gerbang SDK init hilang'
assert dex1[0x5d2438:0x5d243c] == bytes([0x28, 0x02, 0x00, 0x00]), 'patch BARU v19 gerbang HWLoginActivity hilang'
print('dex1 %d B (patch 8 unit, ukuran identik) — dex2 %d B' % (len(dex1), len(dex2)))

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
# catatan v19: ukuran terkompresi dex baru menggeser offset arsc —
# pre-zipalign boleh tak aligned-4; zipalign (uber-apk-signer) yang
# menjamin alignment final sebelum sign (pola sama v14..v18).
print('verifikasi: %d entri, beda HANYA %s' % (len(n2), diff))
assert diff == ['classes.dex', 'classes2.dex'], 'entri berubah tak terduga: %s' % diff
print('resources.arsc: STORED, data_off=%d (%s — final oleh zipalign)'
      % (data_off, 'aligned-4 OK' if data_off % 4 == 0 else 'pre-zipalign, fixed nanti'))
d8 = sum(1 for x in z.infolist() if x.compress_type == 8)
d0 = sum(1 for x in z.infolist() if x.compress_type == 0)
print('metode: %d deflate + %d stored' % (d8, d0))
assert (d8, d0) == (3471, 2957), 'metode kompresi berubah!'
print('REBUILD RAW-COPY v19 OK — %s (%d B)' % (OUT, os.path.getsize(OUT)))
print('sha256 classes.dex  :', sha(dex1))
print('sha256 classes2.dex :', sha(dex2))
