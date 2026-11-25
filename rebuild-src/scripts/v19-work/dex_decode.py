#!/usr/bin/env python3
# DEX DECODE v19 — full operand decode of specific methods (branch targets, registers).
# Methods of interest: every dialog path + the connectivity checks gating them.
import struct, sys

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

INVOKE = {0x6e: 'invoke-virtual', 0x6f: 'invoke-super', 0x70: 'invoke-direct',
          0x71: 'invoke-static', 0x72: 'invoke-interface',
          0x74: 'invoke-virtual/range', 0x75: 'invoke-super/range',
          0x76: 'invoke-direct/range', 0x77: 'invoke-static/range', 0x78: 'invoke-interface/range'}
SGET = {0x60: 'sget', 0x61: 'sget-wide', 0x62: 'sget-object', 0x63: 'sget-boolean',
        0x64: 'sget-byte', 0x65: 'sget-char', 0x66: 'sget-short'}
IGET = {0x52: 'iget', 0x53: 'iget-wide', 0x54: 'iget-object', 0x55: 'iget-boolean',
        0x56: 'iget-byte', 0x57: 'iget-char', 0x58: 'iget-short'}
IPUT = {0x59: 'iput', 0x5a: 'iput-wide', 0x5b: 'iput-object', 0x5c: 'iput-boolean',
        0x5d: 'iput-byte', 0x5e: 'iput-char', 0x5f: 'iput-short'}
CONSTS = {0x12: 'const/4', 0x13: 'const/16', 0x14: 'const', 0x15: 'const/high16'}
IFTEST = {0x38: 'if-eqz', 0x39: 'if-nez', 0x3a: 'if-ltz', 0x3b: 'if-gez', 0x3c: 'if-gtz', 0x3d: 'if-lez'}
IFCMP = {0x32: 'if-eq', 0x33: 'if-ne', 0x34: 'if-lt', 0x35: 'if-ge', 0x36: 'if-gt', 0x37: 'if-le'}
AOPS2 = {0x0a: 'move-result', 0x0b: 'move-result-wide', 0x0c: 'move-result-object',
         0x0d: 'move-exception', 0x0e: 'return-void', 0x0f: 'return', 0x10: 'return-wide',
         0x11: 'return-object', 0x00: 'nop', 0x01: 'move', 0x04: 'move-wide', 0x07: 'move-object',
         0x27: 'throw', 0x1d: 'monitor-enter', 0x1e: 'monitor-exit', 0x21: 'array-length'}

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

def reglist(b, u1, wide=False):
    A = b & 0xf
    if wide:
        return 'v%d, v%d' % (A, u1 & 0xf)
    return 'v%d' % A

def decode(io, size_units):
    _io_ctx[0] = io
    pc = 0
    pend = {}
    lines = []
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
        u2 = d[io2(pc) + 4] | (d[io2(pc) + 5] << 8) if w >= 3 else 0
        txt = 'op=%02x ???' % op
        if op in AOPS2:
            txt = '%s %s' % (AOPS2[op], reglist(b, u1) if op not in (0x0e,) else '')
        elif op == 0x28:  # goto/8
            off = b - 256 if b > 127 else b
            txt = 'goto +0x%x -> pc=%04x' % (off & 0xff, pc + off)
        elif op == 0x29:  # goto/16
            off = struct.unpack_from('<h', d, io2(pc) + 2)[0]
            txt = 'goto/16 +%d -> pc=%04x' % (off, pc + off)
        elif op == 0x2a:
            off = struct.unpack_from('<i', d, io2(pc) + 2)[0]
            txt = 'goto/32 +%d -> pc=%04x' % (off, pc + off)
        elif op in IFTEST:
            off = struct.unpack_from('<h', d, io2(pc) + 2)[0]
            txt = '%s v%d, +%d -> pc=%04x' % (IFTEST[op], b, off, pc + off)
        elif op in IFCMP:
            off = struct.unpack_from('<h', d, io2(pc) + 2)[0]
            txt = '%s v%d, v%d, +%d -> pc=%04x' % (IFCMP[op], b & 0xf, u1 & 0xf, off, pc + off)
        elif op in CONSTS:
            if op == 0x12:
                val = b & 0xf
                val = val - 16 if val > 7 else val
                txt = 'const/4 v%d, %d' % (b >> 4, val)
            elif op == 0x13:
                txt = 'const/16 v%d, %d' % (b, struct.unpack_from('<h', d, io2(pc) + 2)[0])
            elif op == 0x14:
                txt = 'const v%d, 0x%08x' % (b, struct.unpack_from('<I', d, io2(pc) + 2)[0])
            elif op == 0x15:
                txt = 'const/high16 v%d, 0x%04x0000' % (b, struct.unpack_from('<H', d, io2(pc) + 2)[0])
        elif op == 0x1a:
            s = strings[u1] if u1 < str_n else '?'
            txt = "const-string v%d, %r" % (b, s[:60])
        elif op == 0x1b:
            idx = u1 | (u2 << 16)
            s = strings[idx] if idx < str_n else '?'
            txt = "const-string/jumbo v%d, %r" % (b, s[:60])
        elif op == 0x1c:
            txt = 'const-class v%d, %s' % (b, tdesc(u1))
        elif op == 0x1f:
            txt = 'check-cast v%d, %s' % (b, tdesc(u1))
        elif op == 0x20:
            txt = 'instance-of v%d, %s' % (b, tdesc(u1))
        elif op == 0x22:
            txt = 'new-instance v%d, %s' % (b, tdesc(u1))
        elif op == 0x23:
            txt = 'new-array v%d, v%d, %s' % (b, u1 & 0xf, tdesc(struct.unpack_from('<H', d, io2(pc) + 2)[0]))
        elif op == 0x25:
            txt = 'filled-new-array/range'
        elif op in SGET:
            fc, ft, fn = fname(u1)
            txt = '%s v%d, %s.%s:%s' % (SGET[op], b, fc, fn, ft)
        elif op in IGET:
            fc, ft, fn = fname(u1)
            txt = '%s v%d <- %s.%s:%s' % (IGET[op], b & 0xf, fc, fn, ft)
        elif op in IPUT:
            fc, ft, fn = fname(u1)
            txt = '%s v%d -> %s.%s:%s' % (IPUT[op], b & 0xf, fc, fn, ft)
        elif op in INVOKE:
            tc, tn = mname(u1)
            txt = '%s %s.%s' % (INVOKE[op], tc, tn)
            if op < 0x74:
                # format 35c: unit0=[op|A|G] A=argc G=5th reg; unit1=method; unit2=[F|E|D|C]
                argc = (b >> 4) & 0xf
                u2regs = d[io2(pc) + 4] | (d[io2(pc) + 5] << 8)
                regs = [u2regs & 0xf, (u2regs >> 4) & 0xf, (u2regs >> 8) & 0xf, (u2regs >> 12) & 0xf][:argc]
                if argc == 5:
                    regs.append(b & 0xf)
                txt += ' (%s)' % ', '.join('v%d' % r for r in regs)
        elif op in (0x26, 0x2b, 0x2c):
            off = struct.unpack_from('<i', d, io2(pc) + 2)[0]
            tgt = pc + off
            txt = 'switch* +%d -> payload pc=%04x' % (off, tgt)
            if 0 <= tgt < size_units and tgt not in pend:
                u = payload_units(tgt)
                if u: pend[tgt] = u
        lines.append((pc, txt))
        pc += w
    return lines

TARGETS = sys.argv[2:] if len(sys.argv) > 2 else []
for desc, cdo in classes.items():
    for mid, co, kind in methods_of(cdo):
        key = '%s %s' % (desc, mname(mid)[1])
        if TARGETS and not any(t in key for t in TARGETS):
            continue
        if not co:
            continue
        io = co + 16
        sz, = struct.unpack_from('<I', d, co + 12)
        reg, ins, outs = struct.unpack_from('<HHH', d, co)
        print('==== %s code_off=%06x regs=%d ins=%d outs=%d units=%d' % (key, co, reg, ins, outs, sz))
        for pc, txt in decode(io, sz):
            print('  pc=%04x %s' % (pc, txt))
        print()
