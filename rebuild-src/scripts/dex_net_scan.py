#!/usr/bin/env python3
# DEX SCAN — peta LENGKAP semua panggilan connectivity + string network-error
# di classes.dex (bukan karangan: decode bytecode sungguhan).
import struct, sys, re

d = bytearray(open(sys.argv[1], 'rb').read())
assert d[:4] == b'dex\n'

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
fld_n, fld_off = struct.unpack_from('<II', d, 80)
cls_n, cls_off = struct.unpack_from('<II', d, 96)

def string(i):
    so, = struct.unpack_from('<I', d, str_off + 4 * i)
    return cstr(so)

def tdesc(i):
    ti, = struct.unpack_from('<I', d, typ_off + 4 * i)
    return string(ti)

def mname(mid):
    ci, pi, ni = struct.unpack_from('<HHI', d, mth_off + 8 * mid)
    return (tdesc(ci), string(ni))

# --- semua string ---
strings = [string(i) for i in range(str_n)]

# --- ukuran instruksi (code units) per opcode ---
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
for o in range(0x2d, 0x38): SZ[o] = 2          # cmp + if-test
for o in range(0x38, 0x3e): SZ[o] = 2          # if-testz
for o in range(0x44, 0x52): SZ[o] = 2          # aget/aput
for o in range(0x52, 0x60): SZ[o] = 2          # iget/iput
for o in range(0x60, 0x6e): SZ[o] = 2          # sget/sput
for o in range(0x6e, 0x73): SZ[o] = 3          # invoke-kind
SZ[0x74] = 3; SZ[0x75] = 3; SZ[0x76] = 3; SZ[0x77] = 3; SZ[0x78] = 3
for o in range(0x7b, 0x90): SZ[o] = 1          # unop
for o in range(0x90, 0xb0): SZ[o] = 2          # binop
for o in range(0xb0, 0xd0): SZ[o] = 1          # binop/2addr
for o in range(0xd0, 0xd8): SZ[o] = 2          # binop/lit16
for o in range(0xd8, 0xe3): SZ[o] = 2          # binop/lit8
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
    """ukuran (code units) pseudo-payload dalvik; return None bukan payload"""
    ident = d[io2(pc)] | (d[io2(pc) + 1] << 8)
    if ident == 0x0100:   # packed-switch-payload
        size = d[io2(pc) + 2] | (d[io2(pc) + 3] << 8)
        return 4 + 2 * size
    if ident == 0x0200:   # sparse-switch-payload
        size = d[io2(pc) + 2] | (d[io2(pc) + 3] << 8)
        return 4 + 4 * size
    if ident == 0x0300:   # fill-array-data-payload
        ew = d[io2(pc) + 2] | (d[io2(pc) + 3] << 8)
        size = struct.unpack_from('<I', d, io2(pc) + 4)[0]
        return (8 + size * ew + 1) // 2
    return None

_io_ctx = [0]
def io2(pc):
    return _io_ctx[0] + 2 * pc

def walk_insns(io, size_units):
    """yield (pc, opcode, byteB, idx16) — decode penuh, payload pseudo-skip"""
    _io_ctx[0] = io
    pc = 0
    pend = {}   # payload_start -> units
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
        # target payload dari 31t (fill-array-data / packed / sparse switch):
        if op in (0x26, 0x2b, 0x2c):
            off = struct.unpack_from('<i', d, io2(pc) + 2)[0]
            tgt = pc + off
            if 0 <= tgt < size_units and tgt not in pend:
                u = payload_units(tgt)
                if u: pend[tgt] = u
        yield pc, op, b, u1
        pc += w

# ===== 1. peta panggilan connectivity + string network =====
NET_API = re.compile(r'(ConnectivityManager|NetworkInfo|getActiveNetworkInfo|getNetworkInfo|isConnected|isAvailable|isConnectedOrConnecting|isActiveNetworkMetered|registerNetworkCallback|requestNetwork|getAllNetworks|bindProcessToNetwork)', re.I)
NETSTR = re.compile(r'(网络|network|neterror|net_error|noNet|not.?connected|unavailable|disconnect|networkunavai|checknet|netbroken)', re.I)

call_sites = {}
str_sites = {}
for desc, cdo in classes.items():
    for mid, co, kind in methods_of(cdo):
        if not co: continue
        io = co + 16
        sz, = struct.unpack_from('<I', d, co + 12)
        mn = mname(mid)[1]
        for pc, op, b, u1 in walk_insns(io, sz):
            if op in INVOKE:
                tc, tn = mname(u1)
                if NET_API.search(tn) or NET_API.search(tc):
                    call_sites.setdefault((desc, mn), []).append((pc, INVOKE[op], tc, tn))
            elif op == 0x1a:
                s = strings[u1] if u1 < str_n else '?'
                if NETSTR.search(s):
                    str_sites.setdefault((desc, mn), []).append((pc, s))
            elif op == 0x1b:
                u2 = d[io + 2*pc + 4] | (d[io + 2*pc + 5] << 8)
                idx = u1 | (u2 << 16)
                s = strings[idx] if idx < str_n else '?'
                if NETSTR.search(s):
                    str_sites.setdefault((desc, mn), []).append((pc, s))

print('===== A. PANGGILAN API CONNECTIVITY =====')
for (cls, meth), sites in sorted(call_sites.items()):
    short = cls.replace('Lcom/quickgame/android/sdk/', 'QG:').replace('Lcom/db/local/', 'DB:')
    for pc, kind, tc, tn in sites:
        print('  %-72s %-24s pc=%-5x %-9s -> %s %s' % (short[:72], meth[:24], pc, kind, tc, tn))

print()
print('===== B. CONST-STRING network-error (per method, max 5 contoh unik) =====')
for (cls, meth), sites in sorted(str_sites.items()):
    short = cls.replace('Lcom/quickgame/android/sdk/', 'QG:').replace('Lcom/db/local/', 'DB:')
    seen = []
    for pc, s in sites:
        if s not in seen and len(seen) < 5:
            seen.append('%s"@%x' % (s[:56], pc))
    print('  %-72s %-24s %s' % (short[:72], meth[:24], ' | '.join(seen)))
