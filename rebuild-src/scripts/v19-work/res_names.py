#!/usr/bin/env python3
# Resolves R.string names for the popup resource IDs (real arsc parse).
import struct, json, sys

data = open('/home/z/my-project/rebuild-src/scripts/v19-work/resources.arsc', 'rb').read()

def parse_pool(off, label):
    typ, hdr = struct.unpack_from('<HH', data, off)
    if typ != 0x0001:
        raise SystemExit('%s: not a string pool @%06x type=%04x' % (label, off, typ))
    size, strCount, styleCount, flags, strStart, styleStart = struct.unpack_from('<IIIIII', data, off + 4)
    base = off + strStart
    utf8 = bool(flags & 0x100)
    def alen(p):
        b = data[p]
        if b & 0x80:
            return ((b & 0x7f) << 8) | data[p + 1], p + 2
        return b, p + 1
    pool_end = off + size
    out = []
    for i in range(strCount):
        so, = struct.unpack_from('<I', data, off + hdr + 4 * i)
        p = base + so
        if p >= pool_end:
            out.append('?'); continue
        if utf8:
            l, p = alen(p)
            n, p = alen(p)
        else:
            # UTF-16 pool: length is uint16; high bit -> ((b&0x7f)<<8)|next
            b0, = struct.unpack_from('<H', data, p)
            if b0 & 0x8000:
                n = ((b0 & 0x7f) << 8) | data[p + 2]
                p += 4
            else:
                n = b0
                p += 2
        if n > pool_end - p or n < 0 or n > 100000:
            out.append('?%d' % n); continue
        if utf8:
            out.append(data[p:p + n].decode('utf-8', 'replace'))
        else:
            out.append(data[p:p + 2 * n].decode('utf-16-le', 'replace'))
    return out

ttyp, thdr = struct.unpack_from('<HH', data, 0)
_, _, tsize = struct.unpack_from('<HHI', data, thdr)
off = thdr + tsize
steps = 0
while steps < 64:
    typ, hdr, size = struct.unpack_from('<HHI', data, off)
    if size < 8:
        raise SystemExit('bad top chunk size @%06x' % off)
    if typ == 0x0200:
        break
    off += size
    steps += 1

typestr = parse_pool(off + struct.unpack_from('<I', data, off + 268)[0], 'typestr')
keystr = parse_pool(off + struct.unpack_from('<I', data, off + 276)[0], 'keystr')
print('types:', typestr)
pkg_size, = struct.unpack_from('<I', data, off + 4)
print('keys:', len(keystr))

names = {}
p = off + hdr
end = off + pkg_size
chunks = 0
while p < end and chunks < 100000:
    ctyp, chdr, csize = struct.unpack_from('<HHI', data, p)
    if csize < 8:
        print('bad chunk @%06x' % p); break
    if ctyp == 0x0201:
        cid = data[p + 8]
        entryCount, entriesStart = struct.unpack_from('<II', data, p + 12)
        cfg_size, = struct.unpack_from('<I', data, p + 20)
        if entryCount > 50000:
            print('implausible entryCount %d @%06x' % (entryCount, p)); break
        tbl = p + 20 + cfg_size
        tname = typestr[cid - 1] if cid - 1 < len(typestr) else '?%d' % cid
        for i in range(entryCount):
            eo, = struct.unpack_from('<I', data, tbl + 4 * i)
            if eo & 0x80000000:
                continue
            eabs = p + entriesStart + eo
            if eabs + 8 > len(data):
                continue
            key, = struct.unpack_from('<I', data, eabs + 4)
            names[(tname, i)] = keystr[key] if key < len(keystr) else '?'
    p += csize
    chunks += 1

for tid, idx in [(12, 0x010f), (12, 0x0111), (12, 0x0144), (12, 0x0022), (12, 0x0157)]:
    nm = names.get(('string', idx), '?')
    print('R.string.%-38s = 0x7f%02x%04x' % (nm, tid, idx))

with open('/home/z/my-project/rebuild-src/scripts/v19-work/string_names.json', 'w') as f:
    json.dump({'string.0x%04x' % i: names.get(('string', i)) for i in range(4000) if ('string', i) in names}, f, indent=1)
print('done, saved string_names.json')
