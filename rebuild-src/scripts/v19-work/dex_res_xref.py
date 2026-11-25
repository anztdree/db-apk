#!/usr/bin/env python3
# DEX XREF v19 — find EVERY method referencing the popup string resources:
#   hw_network_dialog_message 0x7f0c010f, hw_network_dialog_title 0x7f0c0111,
#   qg_err_connect_service 0x7f0c0144, check_network 0x7f0c0022, update_fail 0x7f0c0157
# Two reference styles:
#   (a) sget/sget-object of R$string.<name>:I field
#   (b) const v?, 0x7f0c010f literal (d8 inlining)
# Full bytecode dump for every containing method.
import struct, sys, json

d = bytearray(open(sys.argv[1], 'rb').read())
assert d[:4] == b'dex\n'
print('dex size:', len(d))

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
pr_n, pr_off = struct.unpack_from('<II', d, 72)
fld_n, fld_off = struct.unpack_from('<II', d, 80)
mth_n, mth_off = struct.unpack_from('<II', d, 88)
cls_n, cls_off = struct.unpack_from('<II', d, 96)

def string(i):
    so, = struct.unpack_from('<I', d, str_off + 4 * i)
    return cstr(so)

def tdesc(i):
    ti, = struct.unpack_from('<I', d, typ_off + 4 * i)
    return string(ti)

def fname(i):
    ci, ti, ni = struct.unpack_from('<HHI', d, fld_off + 8 * i)
    return (tdesc(ci), tdesc(ti), string(ni))

def mname(mid):
    ci, pi, ni = struct.unpack_from('<HHI', d, mth_off + 8 * mid)
    return (tdesc(ci), string(ni))

strings = [string(i) for i in range(str_n)]

SZ = [1]*256
SZ[0x02] = 2; SZ[0x03] = 3
SZ[0x05] = 2; SZ[0x06] = 3
SZ[0x08] = 2; SZ[0x09] = 3
SZ[0x13] = 2; SZ[0x14] = 3; SZ[0x15] = 2
SZ[0x16] = 2; SZ[0x17] = 3; SZ[0x18] = 5; SZ[0x19] = 2
SZ[0x1a] = 2; SZ[0x1b] = 3
SZ[0x1c] = 2; SZ[0x1f] = 2; SZ[0x20] = 2; SZ[0x22] = 2; SZ[0x23] = 2
SZ[0x24] = 3; SZ[0x25] = 3; SZ[0x26] = 3
SZ[0x29] = 2; SZ[0x2a] = 3; SZ[0x2b] = 3; SZ[0x2c] = 3
for o in range(0x2d, 0x38): SZ[o] = 2
for o in range(0x38, 0x3e): SZ[o] = 2
for o in range(0x44, 0x52): SZ[o] = 2
for o in range(0x52, 0x60): SZ[o] = 2
for o in range(0x60, 0x6e): SZ[o] = 2
for o in range(0x6e, 0x73): SZ[o] = 3
SZ[0x74] = 3; SZ[0x75] = 3; SZ[0x76] = 3; SZ[0x77] = 3; SZ[0x78] = 3
for o in range(0x7b, 0x90): SZ[o] = 1
for o in range(0x90, 0xb0): SZ[o] = 2
for o in range(0xb0, 0xd0): SZ[o] = 1
for o in range(0xd0, 0xd8): SZ[o] = 2
for o in range(0xd8, 0xe3): SZ[o] = 2
SZ[0xfa] = 4; SZ[0xfb] = 4; SZ[0xfc] = 3; SZ[0xfd] = 3; SZ[0xfe] = 3; SZ[0xff] = 3

INVOKE = {0x6e: 'virtual', 0x6f: 'super', 0x70: 'direct', 0x71: 'static', 0x72: 'iface',
          0x74: 'virtual/range', 0x75: 'super/range', 0x76: 'direct/range',
          0x77: 'static/range', 0x78: 'iface/range'}

classes = {}
for i in range(cls_n):
    cls_idx, acc, sup, ifo, sfi, anno, cdo, svo = struct.unpack_from('<8I', d, cls_off + 32 * i)
    classes[tdesc(cls_idx)] = cdo

def methods_of(cdo):
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
            out.append((idx, co, kind))
    return out

def payload_units(pc):
    ident = d[io2(pc)] | (d[io2(pc) + 1] << 8)
    if ident == 0x0100:
        size = d[io2(pc) + 2] | (d[io2(pc) + 3] << 8)
        return 4 + 2 * size
    if ident == 0x0200:
        size = d[io2(pc) + 2] | (d[io2(pc) + 3] << 8)
        return 4 + 4 * size
    if ident == 0x0300:
        ew = d[io2(pc) + 2] | (d[io2(pc) + 3] << 8)
        size = struct.unpack_from('<I', d, io2(pc) + 4)[0]
        return (8 + size * ew + 1) // 2
    return None

_io_ctx = [0]
def io2(pc):
    return _io_ctx[0] + 2 * pc

# target R$string field names
TARGET_NAMES = {'hw_network_dialog_message', 'hw_network_dialog_title',
                'qg_err_connect_service', 'check_network', 'update_fail'}
TARGET_CONST = {0x7f0c010f, 0x7f0c0111, 0x7f0c0144, 0x7f0c0022, 0x7f0c0157}

# 1. find R field ids by name+type I
target_fields = {}
for fi in range(fld_n):
    cls, typ, nm = fname(fi)
    if nm in TARGET_NAMES and typ == 'I':
        target_fields[fi] = (cls, nm)
print('target R fields:')
for fi, (cls, nm) in sorted(target_fields.items()):
    print('  field@%x %s %s' % (fi, cls, nm))

def walk(io, size_units):
    _io_ctx[0] = io
    pc = 0
    pend = {}
    while pc < size_units:
        if pc in pend:
            pc += pend.pop(pc)
            continue
        op = d[io2(pc)]
        w = SZ[op]
        if pc + w > size_units:
            break
        b = d[io2(pc) + 1]
        u1 = d[io2(pc) + 2] | (d[io2(pc) + 3] << 8) if w >= 2 else 0
        if op in (0x26, 0x2b, 0x2c):
            off = struct.unpack_from('<i', d, io2(pc) + 2)[0]
            tgt = pc + off
            if 0 <= tgt < size_units and tgt not in pend:
                u = payload_units(tgt)
                if u: pend[tgt] = u
        yield pc, op, b, u1
        pc += w

# 2. walk all methods
hits = {}   # (cls, meth, co) -> list of (pc, desc)
for desc, cdo in classes.items():
    for mid, co, kind in methods_of(cdo):
        if not co: continue
        mn = mname(mid)[1]
        io = co + 16
        sz, = struct.unpack_from('<I', d, co + 12)
        for pc, op, b, u1 in walk(io, sz):
            note = None
            if op == 0x14:      # const v, +32bit
                val = struct.unpack_from('<I', d, io2(pc) + 2)[0]
                if val in TARGET_CONST:
                    note = ('const 0x%08x' % val, val)
            elif op == 0x13:    # const/16
                val = u1
                if val in TARGET_CONST:
                    note = ('const/16 0x%04x' % val, val)
            elif op in (0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67):
                if u1 in target_fields:
                    cls_, nm = target_fields[u1]
                    note = ('sget %s.%s' % (cls_, nm), None)
            elif op in INVOKE:
                tc, tn = mname(u1)
                # record dialog building context for hit methods later
                pass
            if note:
                hits.setdefault((desc, mn, co), []).append((pc, note[0]))

print()
print('===== XREF HITS =====')
for (desc, mn, co), lst in sorted(hits.items()):
    print('%s %s (code_off %06x)' % (desc, mn, co))
    for pc, txt in lst:
        print('    pc=%-5x %s' % (pc, txt))

# 3. dump full bytecode of hit methods
print()
print('===== FULL DUMP =====')
out = open('/home/z/my-project/rebuild-src/scripts/v19-work/popup_xref_dump.txt', 'w')
for (desc, mn, co), lst in sorted(hits.items()):
    cdo = classes[desc]
    out.write('==== %s %s code_off=%06x ====\n' % (desc, mn, co))
    io = co + 16
    sz, = struct.unpack_from('<I', d, co + 12)
    reg, ins, outs = struct.unpack_from('<HHH', d, co)
    out.write('registers=%d ins=%d outs=%d insns_units=%d\n' % (reg, ins, outs, sz))
    for pc, op, b, u1 in walk(io, sz):
        line = '  pc=%04x op=%02x' % (pc, op)
        if op == 0x14:
            val = struct.unpack_from('<I', d, io2(pc) + 2)[0]
            line += ' const v%d, 0x%08x' % (b & 0xf, val)
            if val in TARGET_CONST: line += '  <<< POPUP RES'
        elif op == 0x1a:
            s = strings[u1] if u1 < str_n else '?'
            line += ' const-string v%d, %r' % (b & 0xf, s[:70])
        elif op in INVOKE:
            tc, tn = mname(u1)
            narg = ((b >> 4) & 0xf) or 16 if op < 0x74 else 0
            line += ' invoke-%s %s %s' % (INVOKE[op], tc, tn)
        elif op in (0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67):
            if u1 in target_fields:
                cls_, nm = target_fields[u1]
                line += ' sget* v%d, %s.%s  <<< POPUP RES' % (b & 0xf, cls_, nm)
            else:
                line += ' sget* field@%x' % u1
        elif op == 0x70 or op in (0x6e, 0x6f, 0x71, 0x72):
            tc, tn = mname(u1)
            line += ' invoke %s %s' % (tc, tn)
        out.write(line + '\n')
    out.write('\n')
out.close()
print('dump saved: popup_xref_dump.txt')
