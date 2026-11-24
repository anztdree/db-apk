#!/usr/bin/env python3
# probe: cari kandidat unit patch airplane dengan pola byte
import struct

d = bytearray(open('/tmp/v16/classes_v13.dex', 'rb').read())

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

str_n, str_off = struct.unpack_from('<II', d, 56)
typ_n, typ_off = struct.unpack_from('<II', d, 64)
mth_n, mth_off = struct.unpack_from('<II', d, 88)
cls_n, cls_off = struct.unpack_from('<II', d, 96)

def string(i):
    so, = struct.unpack_from('<I', d, str_off + 4 * i); return cstr(so)

def tdesc(i):
    ti, = struct.unpack_from('<I', d, typ_off + 4 * i); return string(ti)

def mname(mid):
    ci, pi, ni = struct.unpack_from('<HHI', d, mth_off + 8 * mid); return string(ni)

classes = {}
for i in range(cls_n):
    cls_idx, acc, sup, ifo, sfi, anno, cdo, svo = struct.unpack_from('<8I', d, cls_off + 32 * i)
    classes[tdesc(cls_idx)] = cdo

cands_a, cands_b = [], []

def methods_of(cdo):
    """kembalikan [(mid, code_off)] — melewati daftar field dengan benar"""
    if cdo == 0: return []
    p = cdo
    sf, p = uleb(d, p); inf, p = uleb(d, p); dm, p = uleb(d, p); vm, p = uleb(d, p)
    for _ in range(sf):          # encoded_field: idx_diff + access_flags
        _, p = uleb(d, p); _, p = uleb(d, p)
    for _ in range(inf):
        _, p = uleb(d, p); _, p = uleb(d, p)
    out = []
    idx = 0
    for _ in range(dm):
        idxd, p = uleb(d, p); acc, p = uleb(d, p); co, p = uleb(d, p); idx += idxd
        out.append((idx, co))
    idx = 0
    for _ in range(vm):
        idxd, p = uleb(d, p); acc, p = uleb(d, p); co, p = uleb(d, p); idx += idxd
        out.append((idx, co))
    return out

def scan(mid, co, desc):
    if co == 0: return
    io = co + 16
    sz, = struct.unpack_from('<I', d, co + 12)
    if sz * 2 < 0x24: return
    # pola A (byte offset): 0x18 if-eqz v?, +10 ; 0x22 move-result v0
    if d[io+0x18] == 0x38 and d[io+0x1b] == 0 and d[io+0x1a] == 0x0A and d[io+0x22] == 0x0c and d[io+0x23] == 0:
        cands_a.append((desc, mname(mid), 'byte'))
    # pola A (unit offset -> byte 0x30/0x44)
    if sz * 2 >= 0x48 and d[io+0x30] == 0x38 and d[io+0x33] == 0 and d[io+0x32] == 0x0A and d[io+0x44] == 0x0c and d[io+0x45] == 0:
        cands_a.append((desc, mname(mid), 'unit'))
    # pola A (byte): move-result-wide/object varian
    if d[io+0x18] == 0x38 and d[io+0x1a] == 0x0A and d[io+0x22] in (0x0d, 0x0e, 0x0f) and d[io+0x23] == 0:
        cands_a.append((desc, mname(mid), 'byte-vrvar'))
    if sz * 2 < 0x410: return
    # pola B (byte offset): 0x398 move-result v12 ; 0x400 if-eqz v?, +4
    if d[io+0x398] == 0x0c and d[io+0x399] == 0x0c and d[io+0x400] == 0x38 and d[io+0x403] == 0 and d[io+0x402] == 0x04:
        cands_b.append((desc, mname(mid), 'byte'))
    # pola B (unit offset -> byte 0x730/0x800)
    if sz * 2 >= 0x804 and d[io+0x730] == 0x0c and d[io+0x731] == 0x0c and d[io+0x800] == 0x38 and d[io+0x803] == 0 and d[io+0x802] == 0x04:
        cands_b.append((desc, mname(mid), 'unit'))

for desc, cdo in classes.items():
    for mid, co in methods_of(cdo):
        scan(mid, co, desc)

print('kandidat A (pc0018 if-eqz +10, pc0022 move-result v0):')
for c in cands_a[:20]: print('  ', c)
print('kandidat B (pc0398 move-result v12, pc0400 if-eqz +4):')
for c in cands_b[:20]: print('  ', c)

# ---- locate methods covering the EXACT v15 diff offsets ----
TARGETS = [0x5d242c, 0x5d2430, 0x5e816c, 0x5e8174]
def covering(off):
    res = []
    for desc, cdo in classes.items():
        for mid, co in methods_of(cdo):
            if co and co + 16 <= off < co + 16 + struct.unpack_from('<I', d, co + 12)[0] * 2:
                res.append((desc, mname(mid), co))
    return res
for t in TARGETS:
    print('offset %06x berada di:' % t, covering(t))
