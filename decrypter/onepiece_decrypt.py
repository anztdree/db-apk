#!/usr/bin/env python3
"""
Dekriptor resource 'onepieceencrypts' untuk APK DB-Local.
Algoritma diekstrak dari cointhreat/.../customreading.java (DEX classes.dex).

Format file:
  [0:16]   acak (diabaikan)
  [16:32]  magic b"onepieceencrypts"
  [32:48]  kunci XXTEA ter-XOR dengan b"onepiecedecryptk"
  [48:]    payload terenkripsi (varian XXTEA dengan delta konstan)
"""
import sys, os, struct

MASK32 = 0xFFFFFFFF
DELTA = 2654435769          # 0x9E3779B9  (long j = 1 * 2654435769L)
E = (DELTA >> 2) & 3        # long j2 = (j >>> 2) & 3  -> konstan


def u32(x):
    return x & MASK32


def mx(delta, y, z, p, e, key):
    """singersmooth() — varian MX. Semua op disimulasikan 32-bit;
    bit tinggi (garbage dari sign-extension Java) tidak memengaruhi
    32-bit rendah hasil akhir."""
    y &= MASK32
    z &= MASK32
    # A = ((int)(j2 ^ j)) + seventygenom((int)(j3 ^ key[(j4&3)^j5]))
    a = u32(y ^ delta) + u32(z ^ key[(p & 3) ^ e])
    # B = seventygenom((z >>> 5) ^ (y << 2)) + seventygenom((z << 4) ^ (y >>> 3))
    b = u32((z >> 5) ^ u32(y << 2)) + u32(u32(z << 4) ^ (y >> 3))
    return u32(a) ^ u32(b)


def pack(data):
    """cointhreat(bArr, false): byte -> long[] little-endian."""
    n = len(data)
    if n < 4:
        return [0, 0]
    if n % 4 != 0:
        raise ValueError("panjang bukan kelipatan 4 -> Java akan exception")
    return list(struct.unpack('<%dI' % (n // 4), data))


def unpack(v):
    return struct.pack('<%dI' % len(v), *[x & MASK32 for x in v])


def decrypt(data):
    if len(data) < 48 or data[16:32] != b"onepieceencrypts":
        return None  # tidak terenkripsi
    key_b = bytearray(data[32:48])
    kb = b"onepiecedecryptk"
    for i in range(16):
        key_b[i] ^= kb[i % len(kb)]
    key = pack(bytes(key_b))
    v = pack(data[48:])
    n = len(v)
    # dekripsi persis mengikuti nationalcommunitymissing()
    y = v[0]           # long j3 = cointhreat2[0]
    p = n - 1          # long j4 = length2
    while p > 0:
        y = u32(v[p] - mx(DELTA, y, v[p - 1], p, E, key))
        v[p] = y
        p -= 1
    v[0] = u32(v[0] - mx(DELTA, y, v[n - 1], 0, E, key))
    out = unpack(v)
    pad = out[-1]
    if 0 < pad <= 4:
        out = out[:len(out) - pad]
    return out


if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    raw = open(src, 'rb').read()
    res = decrypt(raw)
    if res is None:
        print(f"[skip] {src}: tidak memakai enkripsi onepiece")
        sys.exit(2)
    open(dst, 'wb').write(res)
    print(f"[ok]   {src} -> {dst} ({len(raw)} -> {len(res)} bytes) head={res[:48]!r}")
