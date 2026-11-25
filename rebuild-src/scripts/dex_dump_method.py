#!/usr/bin/env python3
# DUMP penuh bytecode method terpilih + resolve semua ref (string & method)
import struct, sys, re

d = bytearray(open(sys.argv[1], 'rb').read())

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
    ci, pi, ni = struct.unpack_from('<HHI', d, mth_off + 8 * mid)
    return (tdesc(ci), string(ni))

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

SZ = [1]*256
SZ[0x02]=2; SZ[0x03]=3; SZ[0x05]=2; SZ[0x06]=3; SZ[0x08]=2; SZ[0x09]=3
SZ[0x13]=2; SZ[0x14]=3; SZ[0x15]=2; SZ[0x16]=2; SZ[0x17]=3; SZ[0x18]=5; SZ[0x19]=2
SZ[0x1a]=2; SZ[0x1b]=3; SZ[0x1c]=2; SZ[0x1f]=2; SZ[0x20]=2; SZ[0x22]=2; SZ[0x23]=2
SZ[0x24]=3; SZ[0x25]=3; SZ[0x26]=3; SZ[0x29]=2; SZ[0x2a]=3; SZ[0x2b]=3; SZ[0x2c]=3
for o in range(0x2d,0x38): SZ[o]=2
for o in range(0x38,0x3e): SZ[o]=2
for o in range(0x44,0x52): SZ[o]=2
for o in range(0x52,0x60): SZ[o]=2
for o in range(0x60,0x6e): SZ[o]=2
for o in range(0x6e,0x73): SZ[o]=3
SZ[0x74]=3; SZ[0x75]=3; SZ[0x76]=3; SZ[0x77]=3; SZ[0x78]=3
for o in range(0x7b,0x90): SZ[o]=1
for o in range(0x90,0xb0): SZ[o]=2
for o in range(0xb0,0xd0): SZ[o]=1
for o in range(0xd0,0xd8): SZ[o]=2
for o in range(0xd8,0xe3): SZ[o]=2
INVOKE = {0x6e:'invoke-virtual',0x6f:'invoke-super',0x70:'invoke-direct',0x71:'invoke-static',0x72:'invoke-interface',
          0x74:'inv-v/range',0x75:'inv-s/range',0x76:'inv-d/range',0x77:'inv-st/range',0x78:'inv-i/range'}
COND = {}
for o in range(0x32,0x38):
    COND[o] = {0x32:'eq',0x33:'ne',0x34:'lt',0x35:'ge',0x36:'gt',0x37:'le'}[o]
for o in range(0x38,0x3e):
    COND[o] = {0x38:'eqz',0x39:'nez',0x3a:'ltz',0x3b:'gez',0x3c:'gtz',0x3d:'lez'}[o]

_io_ctx=[0]
def io2(pc): return _io_ctx[0]+2*pc

def payload_units(pc):
    ident = d[io2(pc)] | (d[io2(pc)+1]<<8)
    if ident==0x0100:
        size = d[io2(pc)+2] | (d[io2(pc)+3]<<8); return 4+2*size
    if ident==0x0200:
        size = d[io2(pc)+2] | (d[io2(pc)+3]<<8); return 4+4*size
    if ident==0x0300:
        ew = d[io2(pc)+2] | (d[io2(pc)+3]<<8)
        size = struct.unpack_from('<I', d, io2(pc)+4)[0]
        return (8+size*ew+1)//2
    return None

TARGET = sys.argv[2]   # contoh: Lcom/quickgame/android/sdk/unnecessarysperrylites/seventygenom;
METH   = sys.argv[3]   # nama method atau '*'

cdo = classes.get(TARGET)
if cdo is None:
    print('class tidak ada:', TARGET); sys.exit(1)
print('== %s ==' % TARGET)
for mid, co, kind in methods_of(cdo):
    if co == 0: continue
    ci, pi, ni = struct.unpack_from('<HHI', d, mth_off + 8 * mid)
    nm = string(ni)
    if METH != '*' and nm != METH: continue
    io = co + 16
    sz, = struct.unpack_from('<I', d, co + 12)
    _io_ctx[0] = io
    print('-- method %s (%s) code_off=%06x insns=%d units' % (nm, kind, co, sz))
    pc = 0; pend = {}
    while pc < sz:
        if pc in pend:
            pc += pend.pop(pc); continue
        op = d[io2(pc)]
        w = SZ[op]
        if pc + w > sz: break
        b = d[io2(pc)+1]
        u1 = d[io2(pc)+2] | (d[io2(pc)+3]<<8) if w >= 2 else 0
        note = ''
        if op in INVOKE:
            tc, tn = mname(u1)
            note = '%s -> %s.%s' % (INVOKE[op], tc, tn)
        elif op == 0x1a:
            note = 'const-string "%s"' % string(u1)
        elif op == 0x1b:
            u2 = d[io2(pc)+4] | (d[io2(pc)+5]<<8)
            idx = u1 | (u2<<16)
            note = 'const-string/jumbo "%s"' % (string(idx) if idx < str_n else '?')
        elif op == 0x22:
            note = 'new-instance %s' % tdesc(u1)
        elif op == 0x1c:
            note = 'const-class %s' % tdesc(u1)
        elif op == 0x1f:
            note = 'check-cast %s' % tdesc(u1)
        elif op in COND:
            if op >= 0x38:
                off = b  # 8-bit? 21t: unit1 = AA|op ... 22t: unit1 B|A, unit2 CCCC
                # 21t (eqz/nez/...): [AA|op, +BBBB] -> offset di unit-1
                off = struct.unpack_from('<h', d, io2(pc)+2)[0]
                note = 'if-%s -> pc=%x' % (COND[op], pc+off)
            else:
                off = struct.unpack_from('<h', d, io2(pc)+4)[0]
                note = 'if-%s v%d,v%d -> pc=%x' % (COND[op], b & 0xf, (b>>4)&0xf, pc+off)
        elif op == 0x28:
            off = struct.unpack_from('<b', d, io2(pc)+1)[0]
            note = 'goto -> pc=%x' % (pc+off)
        elif op == 0x29:
            off = struct.unpack_from('<h', d, io2(pc)+2)[0]
            note = 'goto/16 -> pc=%x' % (pc+off)
        elif op in (0x26, 0x2b, 0x2c):
            off = struct.unpack_from('<i', d, io2(pc)+2)[0]
            note = {0x26:'fill-array-data',0x2b:'packed-switch',0x2c:'sparse-switch'}[op] + ' payload@%x' % (pc+off)
        elif op == 0x12:
            v = (b & 0xf) - ((b >> 4) & 0xf) * 0 if True else 0
            # const/4: B|A -> literal nibble B (signed), reg A
            lit = (b >> 4) & 0xf
            if lit >= 8: lit -= 16
            note = 'const/4 v%d, %d' % (b & 0xf, lit)
        elif op in (0x60,0x61,0x62,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6a,0x6b,0x6c,0x6d):
            note = 'sget/sput op=%02x field@%x' % (op, u1)
        elif 0x52 <= op <= 0x5f:
            note = 'iget/iput op=%02x field@%x' % (op, u1)
        if note:
            print('  pc=%-5x op=%02x  %s' % (pc, op, note))
        pc += w
