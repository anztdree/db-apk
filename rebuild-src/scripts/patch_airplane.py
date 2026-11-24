#!/usr/bin/env python3
# PATCH AIRPLANE-MODE classes.dex (base v13 -> patched, ukuran byte IDENTIK).
#
# Owner: "lagi running local kalau mode pesawat kok notifikasi error network
# dasar gk logis" — semua endpoint sudah 127.0.0.1, tapi QuickGame SDK masih
# mengecek NetworkInfo.isConnected SEBELUM init dan menampilkan AlertDialog
# "network error" saat mode pesawat.
#
# Patch 4 unit (6 byte berubah). Ground truth = diff byte classes.dex
# v13 <-> v15 (offset file absolut) + identitas method pembungkusnya:
#   A) com.quickgame.android.sdk.feedtonight  / method hoboismrelationbelow (SDK init)
#        @file 5e816c  if-eqz operand +10 -> +8      (null -> jalur init, bukan dialog)
#        @file 5e8174  move-result v0 -> const/4 v0,1 (hasil isConnected dipaksa true)
#   B) com.quickgame.android.sdk.activity.HWLoginActivity / method onCreate
#        @file 5d242c  move-result v12 -> const/4 v12,1
#        @file 5d2430  if-eqz operand +4 -> +2       (false -> jalur init, bukan dialog)
#
# opcode dalvik: move-result 0x0a | const/4 0x12 (byte1 = B<<4|A) |
#                if-eqz 0x38 (operand unit sesudah opcode)
# Setelah patch: adler32 (off 8) + SHA1 (off 12) header dihitung ulang.
# VERBAL: sha256 hasil harus 9db1bba326132a8eff93429a54c11d0440ab2c53f4c84b7b131fb1533d642658
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v16/classes_v13.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v16/classes_patched.dex'
EXPECT = '9db1bba326132a8eff93429a54c11d0440ab2c53f4c84b7b131fb1533d642658'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n', 'bukan dex'
print('input %s — %d B' % (IN, len(d)))

def uleb(buf, off):
    r, s = 0, 0
    while True:
        b = buf[off]; off += 1
        r |= (b & 0x7f) << s
        if not (b & 0x80): break
        s += 7
    return r, off

def cstr(off):
    n, p = uleb(d, off)
    e = d.find(b'\x00', p)
    return d[p:e].decode('utf-8', 'replace')

str_off, = struct.unpack_from('<I', d, 60)
typ_off, = struct.unpack_from('<I', d, 68)
mth_off, = struct.unpack_from('<I', d, 92)
cls_n, cls_off = struct.unpack_from('<II', d, 96)

def string(i):
    so, = struct.unpack_from('<I', d, str_off + 4 * i); return cstr(so)

def tdesc(i):
    ti, = struct.unpack_from('<I', d, typ_off + 4 * i); return string(ti)

classes = {}
for i in range(cls_n):
    cls_idx, acc, sup, ifo, sfi, anno, cdo, svo = struct.unpack_from('<8I', d, cls_off + 32 * i)
    classes[tdesc(cls_idx)] = cdo

def methods_of(cdo):
    """[(mid, code_off)] — daftar field di-skip, idx di-reset per daftar"""
    if cdo == 0: return []
    p = cdo
    sf, p = uleb(d, p); inf, p = uleb(d, p); dm, p = uleb(d, p); vm, p = uleb(d, p)
    for _ in range(sf): _, p = uleb(d, p); _, p = uleb(d, p)
    for _ in range(inf): _, p = uleb(d, p); _, p = uleb(d, p)
    out = []
    for kind, n in (('direct', dm), ('virtual', vm)):
        idx = 0
        for _ in range(n):
            idxd, p = uleb(d, p); acc, p = uleb(d, p); co, p = uleb(d, p); idx += idxd
            out.append((idx, co))
    return out

def covering_method(cls_desc, file_off):
    """method SATU-SATUNYA milik cls_desc yang insns-nya membungkus file_off"""
    cdo = classes[cls_desc]
    assert cdo, 'class tidak ada: ' + cls_desc
    hits = []
    for mid, co in methods_of(cdo):
        if not co: continue
        sz, = struct.unpack_from('<I', d, co + 12)
        io = co + 16
        if io <= file_off < io + sz * 2:
            ci, pi, ni = struct.unpack_from('<HHI', d, mth_off + 8 * mid)
            hits.append((string(ni), io, co))
    assert len(hits) == 1, '%s: %d method membungkus %06x' % (cls_desc, len(hits), file_off)
    return hits[0]

def patch(cls_desc, m_expect, file_off, before, after, note):
    m_name, io, co = covering_method(cls_desc, file_off)
    assert m_name == m_expect, '%s @%06x: method %s != %s' % (cls_desc, file_off, m_name, m_expect)
    cur = bytes(d[file_off:file_off + len(before)])
    assert cur == before, '@%06x byte kini %s != %s' % (file_off, cur.hex(), before.hex())
    d[file_off:file_off + len(after)] = after
    print('  %-52s @%06x insns+%03x %s -> %s  (%s)' % (
        cls_desc.rsplit('/', 1)[-1] + '.' + m_name, file_off, file_off - io,
        before.hex(), after.hex(), note))

# A) SDK init — feedtonight.hoboismrelationbelow
patch('Lcom/quickgame/android/sdk/feedtonight;', 'hoboismrelationbelow',
      0x5e816c, bytes([0x0A]), bytes([0x08]), 'if-eqz +10->+8')
patch('Lcom/quickgame/android/sdk/feedtonight;', 'hoboismrelationbelow',
      0x5e8174, bytes([0x0A, 0x00]), bytes([0x12, 0x10]), 'move-result v0 -> const/4 v0,1')
# B) HWLoginActivity.onCreate
patch('Lcom/quickgame/android/sdk/activity/HWLoginActivity;', 'onCreate',
      0x5d242c, bytes([0x0A, 0x0C]), bytes([0x12, 0x1C]), 'move-result v12 -> const/4 v12,1')
patch('Lcom/quickgame/android/sdk/activity/HWLoginActivity;', 'onCreate',
      0x5d2430, bytes([0x04]), bytes([0x02]), 'if-eqz +4->+2')

# ---- header dex: checksum adler32 + SHA1 dihitung ulang ----
d[12:32] = hashlib.sha1(d[32:]).digest()
d[8:12] = struct.pack('<I', zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)

open(OUT, 'wb').write(bytes(d))
got = hashlib.sha256(bytes(d)).hexdigest()
print('output %s — %d B (ukuran identik base: %s)' % (OUT, len(d), len(d) == 6697176))
print('sha256 classes_patched:', got)
if got == EXPECT:
    print('VERBAL OK — byte-identik dengan dex terpakai di v15')
else:
    print('GAGAL: sha BEDA dari v15 (%s)' % EXPECT)
    sys.exit(2)
