#!/usr/bin/env python3
# ARSC DUMP v19 — map every resource ID to its string values (all configs),
# find which resource ID holds the airplane-mode popup texts.
# Real parse of resources.arsc (ResTable structure), no guessing.
import struct, sys, json

data = open(sys.argv[1] if len(sys.argv) > 1 else '/home/z/my-project/rebuild-src/scripts/v19-work/resources.arsc', 'rb').read()
print('arsc size:', len(data))

def parse_pool(off):
    typ, hdr = struct.unpack_from('<HH', data, off)
    assert typ == 0x0001, 'bukan string pool @%x: %04x' % (off, typ)
    size, strCount, styleCount, flags, strStart, styleStart = struct.unpack_from('<IIIIII', data, off + 4)
    base = off + strStart
    UTF8_FLAG = 1 << 8
    def alen(p):
        # Android modified-length encoding: if high bit set -> 2 bytes ((b&0x7f)<<8|next)
        b = data[p]
        if b & 0x80:
            return ((b & 0x7f) << 8) | data[p + 1], p + 2
        return b, p + 1
    out = []
    for i in range(strCount):
        so, = struct.unpack_from('<I', data, off + hdr + 4 * i)
        p = base + so
        if flags & UTF8_FLAG:
            l, p = alen(p)   # u16 length (unused)
            n, p = alen(p)   # u8 length = real byte count
            out.append(data[p:p + n].decode('utf-8', 'replace'))
        else:
            n, p = alen(p)
            out.append(data[p:p + 2 * n].decode('utf-16-le', 'replace'))
    return out

ttyp, thdr = struct.unpack_from('<HH', data, 0)
assert ttyp == 0x0002
gstr = parse_pool(thdr)
print('global string pool:', len(gstr), 'strings')

res_strings = {}
off = thdr
_, _, size = struct.unpack_from('<HHI', data, off)
off += size

pkg_count = 0
while off < len(data):
    typ, hdr = struct.unpack_from('<HH', data, off)
    if typ != 0x0200:
        _, _, csize = struct.unpack_from('<HHI', data, off)
        off += csize
        continue
    pkg_size, = struct.unpack_from('<I', data, off + 4)
    pkg_id, = struct.unpack_from('<I', data, off + 8)
    name = data[off + 12:off + 12 + 256].decode('utf-16-le', 'replace').split('\x00')[0]
    if pkg_size < hdr or off + pkg_size > len(data):
        print('bad package size @%x, abort' % off); break
    typeStrings_off, lastPublicType, keyStrings_off, lastPublicKey = struct.unpack_from('<IIII', data, off + 268)
    typestr = parse_pool(off + typeStrings_off)
    keystr = parse_pool(off + keyStrings_off)
    print('package id=0x%02x %s: %d types, %d keys' % (pkg_id, name, len(typestr), len(keystr)))
    p = off + hdr
    end = off + pkg_size
    while p < end:
        ctyp, chdr = struct.unpack_from('<HH', data, p)
        _, _, csize = struct.unpack_from('<HHI', data, p)
        if csize < 8:
            print('bad chunk size @%x' % p); break
        if ctyp == 0x0201:
            cid, res0, res1 = struct.unpack_from('<HBB', data, p + 8)
            entryCount, entriesStart = struct.unpack_from('<II', data, p + 12)
            cfg_size, = struct.unpack_from('<I', data, p + 20)
            cfg = data[p + 20:p + 20 + cfg_size]
            lang = cfg[8:10].decode('latin1', 'replace')
            ctry = cfg[10:12].decode('latin1', 'replace')
            label = (lang or 'default') + (('-' + ctry) if ctry.strip('\x00') else '')
            # offsets table: right after config (p+20+cfg_size); entries: at p+entriesStart
            tbl = p + 20 + cfg_size
            for i in range(entryCount):
                eo, = struct.unpack_from('<I', data, tbl + 4 * i)
                if eo & 0x80000000:
                    continue
                eabs = p + entriesStart + eo
                esz, eflags = struct.unpack_from('<HH', data, eabs)
                if eflags & 0x0001:
                    continue
                voff = eabs + esz
                vsize, res0, vtype = struct.unpack_from('<HBB', data, voff)
                vdata, = struct.unpack_from('<I', data, voff + 4)
                if vtype == 0x03:
                    resId = (pkg_id << 24) | (cid << 16) | i
                    txt = gstr[vdata] if vdata < len(gstr) else '?'
                    res_strings.setdefault(resId, {})[label] = txt
        p += csize
    off += pkg_size
    pkg_count += 1
    if pkg_count > 8:
        print('too many packages, stop'); break

print('parsed packages:', pkg_count)
print()
print('===== POPUP STRING -> RESOURCE ID =====')
targets = ['Koneksi ke jaringan gagal', 'mengingat', 'Silahkan periksa']
found = set()
for resId in sorted(res_strings):
    cfgs = res_strings[resId]
    hit = False
    for lbl, txt in cfgs.items():
        if any(t in txt for t in targets):
            print('  0x%08x [%s] %r' % (resId, lbl, txt[:130]))
            hit = True
    if hit:
        found.add(resId)
        for l2 in sorted(cfgs):
            print('        [%-8s] %r' % (l2, cfgs[l2][:130]))

print()
print('===== ALL STRING RESOURCES CONTAINING "jaringan" / "network" =====')
for resId in sorted(res_strings):
    for lbl, txt in res_strings[resId].items():
        tl = txt.lower()
        if 'jaringan' in tl or ('network' in tl and len(txt) < 90):
            print('  0x%08x [%-8s] %r' % (resId, lbl, txt[:130]))

with open('/home/z/my-project/rebuild-src/scripts/v19-work/res_string_map.json', 'w') as f:
    json.dump({hex(k): v for k, v in res_strings.items()}, f, ensure_ascii=False, indent=1)
print('saved res_string_map.json')
