#!/usr/bin/env python3
# REBUILD RAW-COPY v28 — pola terdokumentasi Bagian 46..83 (REBUILD-PATTERN.md).
# ARAHAN OWNER (28 Nov 2026): "REBUILD YG BENER" → implementasi satu ronde
# Bagian 83.4 = dex v28 (N1+N2 + formatter v5 + bersih teks) + APK v28.
#
# Base = APK v27 SIGNED (sha256 874232d1…, 202.699.742 B) — rantai terbukti
# jalan di device (pola v26/v27: base signed sebelumnya + dex baru + re-sign).
# Invariant lineage yang TETAP diverifikasi (pengganti cek sha base v23):
#   - classes.dex = airplane v19 byte-identik (6.697.176 B, sha 62803bb1…,
#     byte patch @0x5e8180/@0x5d2438)
#   - QHinfo = http://127.0.0.1:9530 (proven)
#   - resources.arsc STORED; entri kontrak assets lengkap
#
# METODE: SEMUA entri disalin byte-per-byte (metode/flags/extra dipertahankan).
# v28 mengganti SATU entri: classes2.dex = dex login-server v28
# (patch_marker_v28.py: rumus spec DEX + verifikasi struktur penuh + kontrak
# v28 N1/N2 + formatter v5 + seluruh kontrak warisan).
#   classes.dex  = TETAP — byte-identik v23/v24/v25/v26/v27 (= airplane v19).
import struct, zlib, sys, hashlib, os, zipfile

BASE = '/tmp/v28/base/base_v27.apk'
DEX2 = '/tmp/v28/dexout/classes2.dex'   # classes2.dex (login-server v28)
OUT  = '/tmp/v28/v28_unsigned.apk'

def sha(b): return hashlib.sha256(b).hexdigest()

base = open(BASE, 'rb').read()
dex2 = open(DEX2, 'rb').read()
assert sha(base) == '874232d18a5b194bc41bb2c39852afd2f70e2a77f67cdd71e0ac30334ce0a59b', \
    'base BUKAN APK v27: ' + sha(base)
print('sanity: base APK v27 OK — %d B' % len(base))

# ---- kontrak dex ----
assert dex2[:4] == b'dex\n', 'bukan dex'
assert dex2.count(b'v28') == 4
for ban in ('v18', 'v19', 'v20', 'v21', 'v22', 'v23', 'v24', 'v25', 'v26', 'v27'):
    assert ban.encode() not in dex2, 'marker lama masih ada di dex v28: ' + ban
for m in (b'MOD-LOCALSERVER v28', b'>v28<', b'handler.process',
          b'DragonBall Local', b'LOGIN-SERVER', b'login-server',
          b'history.sqlite', b'SaveUserEnterInfo',
          b'server.ndjson', b'level":"ERROR', b'404 MISSING: ',
          b'abnormal exit: ', b'antre polling', b'WS langsung',
          b'upgrade selesai', b'__listenerport',
          b'pola socket GAME', b'gameserver belum dibangun',
          b"menunggu event 'verify' dari server",
          b'long-poll timeout 10 dtk tanpa data',
          b"tidak pernah mengirim '5'",
          b'berisi: ', b'serverlist SAMPAI ke client',
          b'WS berakhir (EOF/koneksi putus tanpa close frame)',
          b'WS close dari client',
          b'/log/client', b'__dbClientCapture', b'CLIENT',
          b'console.error dari halaman game',
          b'window.onerror dari halaman game',
          b'capture client terinjeksi (script ',
          b'pola socket GAME: client menunggu event ',
          b'loading macet di sini',
          b'__dbSid',
          b'device halaman game: ua=',
          b'resource sweep dibatasi 3000',
          b'longtask',
          b'setTimeout(tick,400)',
          b'__dbWorkerReady',
          b'workerMain',
          b'watchdog main thread tidak merespons ~',
          b'watchdog main thread pulih setelah beku ~',
          b'watchdog main thread belum pernah merespons ~',
          b'beat 1 dtk berhenti',
          b'kejadian terakhir: ',
          b"m=='watchdog'",
          b'script mulai: ',
          b'media mulai: ',
          b'MutationObserver',
          b'ExternalInterface call ',
          b'ExternalInterface addCallback ',
          b'ExternalInterface hook terpasang',
          b"'request: '+e.reqPreview",
          b'CLIENT v3',
          b"'('+workerMain.toString()+')()'",
          # kontrak v28 — N1/N2 watchdog + formatter v5
          b"k=='WATCH'", b"m=='N2'",
          b" dtk \xe2\x80\x94 client tidak mengirim '5' \xe2\x80\x94 upgrade websocket tidak selesai",
          b'client bisu ', b' dtk \xe2\x80\x94 batch terakhir sid ', b' \xe2\x80\x94 worker beat mati',
          b'watchdog-n1-n2', b'mini-server up',
          b'function textLines', b'function emojiOf'):
    assert m in dex2, 'marker v28 hilang: %r' % m
assert b"icon:'check}}\n" not in dex2, 'console rusak masih ada — v28 menolak'
assert b'exit-history.ndjson' not in dex2, \
    'exit-history.ndjson masih ada — Bagian 70 menghapus file terpisah'
for ban in (b'serverListJson', b'enterGameResponse', b'routeEvent', b'ACK: 43',
            b'atlas \xc2\xa7', b'LOGIN-SERVER-PLAN.md', b' \xe2\x80\x94 Bagian 21',
            b'owner edit \xe2\x80\x94 seed tidak menimpa'):
    assert ban not in dex2, 'string terlarang masih ada: %r' % ban
print('dex2 %d B (login-server v28 — N1/N2 watchdog + formatter v5 + bersih teks)' % len(dex2))

# ---- VERIFIKASI STRUKTUR DEX di dalam rantai rebuild (jaga-jaga kedua) ----
def u32(buf, off): return struct.unpack_from('<I', buf, off)[0]
assert u32(dex2, 8) == (zlib.adler32(dex2[12:]) & 0xFFFFFFFF), 'adler32 header tidak sah'
assert dex2[12:32] == hashlib.sha1(dex2[32:]).digest(), 'SHA1 signature tidak sah'
map_off = u32(dex2, 52)
nmap = u32(dex2, map_off)
assert map_off + 4 + nmap * 12 == len(dex2), 'map tidak berakhir di EOF'
for i in range(nmap):
    off = u32(dex2, map_off + 4 + i * 12 + 8)
    assert 0 <= off < len(dex2), 'map offset di luar file (entri %d)' % i
print('verifikasi dex terpasang: checksum+signature sah, map %d entri < EOF' % nmap)

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
             'assets/server_root/bs/resource/properties/clientversion.json',
             'assets/server_root/bs/upgrade/all.zip',
             'assets/server_root/bs/resource/properties/serversetting.json'):
    assert must in names, 'entri kontrak hilang: ' + must
idx = {e['name'].decode(): e for e in entries}
qh = idx['assets/QHinfo']
qd = qh['data'] if qh['method'] == 0 else zlib.decompress(qh['data'], -15)
assert qd == b'http://127.0.0.1:9530', 'QHinfo berubah!'
d1 = idx['classes.dex']
old_dex1 = d1['data'] if d1['method'] == 0 else zlib.decompress(d1['data'], -15)
# classes.dex TETAP: airplane patch v19 (8 unit) — sha + ukuran + byte patch
assert len(old_dex1) == 6697176, 'classes.dex harus 6.697.176 B (patch identik ukuran)'
assert sha(old_dex1) == '62803bb1592b378da3ebf2664795f538193b6b84a302b44ee247d7e5c1fcc8b4', \
    'classes.dex bukan airplane v19: ' + sha(old_dex1)
assert old_dex1[0x5e8180:0x5e8184] == bytes([0x28, 0x02, 0x00, 0x00]), 'patch v19 gerbang SDK init hilang'
assert old_dex1[0x5d2438:0x5d243c] == bytes([0x28, 0x02, 0x00, 0x00]), 'patch v19 gerbang HWLoginActivity hilang'
print('sanity: QHinfo proven — classes.dex airplane v19 dipertahankan byte-identik (%d B)' % len(old_dex1))

# ---- bangun zip baru ----
def comp(raw):
    _c = zlib.compressobj(9, zlib.DEFLATED, -15)
    return _c.compress(raw) + _c.flush()

newdata = {b'classes2.dex': (dex2, zlib.crc32(dex2) & 0xFFFFFFFF, len(dex2))}
comp_cache = {k: comp(v[0]) for k, v in newdata.items()}

out = bytearray()
newoffs = []
for e in entries:
    newoffs.append(len(out))
    nm = e['name']
    if nm in newdata:
        assert e['method'] == 8, '%s base bukan DEFLATED — metode harus dipertahankan' % nm.decode()
        raw = newdata[nm][0]
        data = comp_cache[nm]
        crc, usize = newdata[nm][1], newdata[nm][2]
        csize = len(data)
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
    if n == 'classes2.dex':
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
# catatan v22: ukuran terkompresi dex baru menggeser offset — pre-zipalign
# boleh tak aligned-4; zipalign (uber-apk-signer) menjamin alignment final
# sebelum sign (pola sama v14..v27).
print('verifikasi: %d entri, beda HANYA %s (META-INF ikut base — diganti re-sign)' % (len(n2), diff))
assert diff == ['classes2.dex'], 'entri berubah tak terduga: %s' % diff
print('resources.arsc: STORED, data_off=%d (%s — final oleh zipalign)'
      % (data_off, 'aligned-4 OK' if data_off % 4 == 0 else 'pre-zipalign, fixed nanti'))
d8 = sum(1 for x in z.infolist() if x.compress_type == 8)
d0 = sum(1 for x in z.infolist() if x.compress_type == 0)
print('metode: %d deflate + %d stored' % (d8, d0))
print('REBUILD RAW-COPY v28 OK — %s (%d B)' % (OUT, os.path.getsize(OUT)))
print('sha256 classes2.dex v28:', sha(dex2))
