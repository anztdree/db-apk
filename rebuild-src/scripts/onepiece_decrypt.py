#!/usr/bin/env python3
# Port LITERAL dari class smali:
# cointhreat/cointhreat/cointhreat/reweavingsiamesedpropertylessnesses/customreading.smali
# (asli jadx: customreading.nationalcommunitymissing) — dibaca ulang dari classes.dex APK.
#
# Layout file:
#   byte[16:32] = magic "onepieceencrypts" (bila beda -> return input tanpa diubah)
#   byte[32:48] = key tersimpan, XOR "onepiecedecryptk" (i % 16)
#   byte[48:]   = payload, long LE 32-bit per word (4 byte per long)
# Varian XXTEA (beda dari standar):
#   sum  = delta KONSTAN 0x9E3779B9 (1 pass — bukan rounds*delta)
#   e    = (delta >> 2) & 3 KONSTAN
#   loop p = n-1 downto 1: z = v[p-1], y = v[p+1] (y awal = v[0]; y iterasi
#          berikut = v[p] yang BARU di-update); v[p] -= MX(sum, y, z, p, e, k)
#   final p = 0: y = v[1], z = v[n-1]; v[0] -= MX(...)
#   MX = mask32((y^sum)) + mask32(z^key[(p&3)^e])) ^ (mask32((z>>>5)^(y<<2)) + mask32((z<<4)^(y>>>3)))
#   padding: byte terakhir payload = jumlah pad (dibuang)
import struct, sys

DELTA = 0x9E3779B9
M32 = 0xFFFFFFFF

def mask32(x):
    return x & M32

def mx(s, y, z, p, e, k):
    # port literal singersmooth(JJJJJ[J)J — semua operasi int 32-bit (low word)
    y &= M32; z &= M32
    t1 = mask32(((z >> 5) & M32) ^ ((y << 2) & M32))
    t2 = mask32(((z << 4) & M32) ^ (y >> 3))
    a = mask32(y ^ s)
    b = mask32(z ^ k[(p & 3) ^ e])
    return mask32((a + b) ^ (t1 + t2))

def decrypt_words(v, k):
    n = len(v)
    s = DELTA                 # KONSTAN (mul-long 1 * 0x9e3779b9)
    e = (s >> 2) & 3          # KONSTAN
    y = v[0]
    for p in range(n - 1, 0, -1):
        z = v[p - 1]
        v[p] = (v[p] - mx(s, y, z, p, e, k)) & M32
        y = v[p]              # y iterasi berikut = v[p] barusan di-update
    z = v[n - 1]
    y = v[1]
    v[0] = (v[0] - mx(s, y, z, 0, e, k)) & M32
    return v

def decrypt_file(path, out_path=None):
    data = open(path, "rb").read()
    if data[16:32] != b"onepieceencrypts":
        out = data  # fakta smali: cond_f5 return-object p1 (input apa adanya)
        if out_path:
            open(out_path, "wb").write(out)
        return out
    keyb = bytearray(data[32:48])
    xk = b"onepiecedecryptk"
    for i in range(len(keyb)):
        keyb[i] ^= xk[i % len(xk)]
    k = list(struct.unpack("<4I", bytes(keyb)))
    payload = data[48:]
    n = len(payload) // 4
    v = list(struct.unpack("<%dI" % n, payload[: n * 4]))
    dec = decrypt_words(v, k)
    out = struct.pack("<%dI" % n, *dec)
    pad = out[-1]
    out = out[: len(out) - pad]  # port literal: v1 -= v0 (tanpa clamp)
    if out_path:
        open(out_path, "wb").write(out)
    return out

if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    out = decrypt_file(src, dst)
    print("%s -> %s (%d bytes)" % (src, dst, len(out)))
