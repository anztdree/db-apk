#!/usr/bin/env python3
"""
decrypt_onepiece.py — dekriptor resource `onepieceencrypts` milik com.db.local.

ALGORITMA (terverifikasi dari bytecode classes.dex,
kelas cointhreat.cointhreat.cointhreat.reweavingsiamesedpropertylessnesses.customreading,
metode nationalcommunitymissing([B)[B):

  Layout file terenkripsi:
    [0..16)   header bebas (mis. tag versi "T01 " / acak) — diabaikan
    [16..32)  magic ASCII "onepieceencrypts" (wajib)
    [32..48)  blok KEY: XOR per-byte dengan konstanta "onepiecedecryptk"
              -> hasilnya key XXTEA 16 byte (4 word)
    [48..]    body terenkripsi XXTEA varian vendor:
              - delta 0x9E3779B9, word 32-bit, packing LITTLE-ENDIAN
              - SATU PASS p=n-1..1 (z=v[p-1], y=v[0] awal), lalu p=0 (z=v[n-1])
              - sum = delta TETAP (tanpa q-rounds ala XXTEA standar)
              - MX = (((z>>>5 ^ y<<2) + (y>>>3 ^ z<<4)) ^ ((sum^y) + (key[(p&3)^e]^z)))
              - e = (sum>>>2)&3 dihitung SEKALI
  Output:
    word -> byte LE, padLen = byte terakhir, buang padLen byte terakhir.

Bukti hasil: voyage_fancy_radius.bin & pschent_full_tired.bin -> ZIP
(isi js/*.js game), vvcc.bin & vvcc_tc.bin -> JSON peta nama file.
"""
import sys, os, io, json, zipfile

M = 0xFFFFFFFF
DELTA = 0x9E3779B9
MAGIC = b"onepieceencrypts"
XORKEY = b"onepiecedecryptk"


def words_le(b, n):
    out = []
    for w in range(n):
        val = 0
        for j in range(4):
            if w * 4 + j < len(b):
                val |= b[w * 4 + j] << (8 * j)
        out.append(val & M)
    return out


def mx(z, y, s, key, p, e):
    return (((z >> 5 ^ (y << 2) & M) + (y >> 3 ^ (z << 4) & M))
            ^ ((s ^ y) + (key[(p & 3) ^ e] ^ z))) & M


def decrypt_bytes(d):
    if d[16:32] != MAGIC:
        raise ValueError("magic onepieceencrypts tidak ditemukan di offset 16")
    key = words_le(bytes(d[32:48][i] ^ XORKEY[i % 16] for i in range(16)), 4)
    body = d[48:]
    v = words_le(body, len(body) // 4)
    n = len(v)
    s = DELTA
    e = (s >> 2) & 3
    y = v[0]
    for p in range(n - 1, 0, -1):
        z = v[p - 1]
        y = v[p] = (v[p] - mx(z, y, s, key, p, e)) & M
    v[0] = (v[0] - mx(v[n - 1], y, s, key, 0, e)) & M
    b = bytearray()
    for w in v:
        b += bytes([w & 0xFF, (w >> 8) & 0xFF, (w >> 16) & 0xFF, (w >> 24) & 0xFF])
    pad = b[-1]
    return bytes(b[: len(b) - pad]) if 0 <= pad <= 16 else bytes(b)


def unpack(path, outdir):
    raw = decrypt_bytes(open(path, "rb").read())
    os.makedirs(outdir, exist_ok=True)
    if raw[:4] == b"PK\x03\x04":
        zipfile.ZipFile(io.BytesIO(raw)).extractall(outdir)
        return "ZIP -> " + outdir
    try:
        j = json.loads(raw.decode("utf-8"))
        open(os.path.join(outdir, "decoded.json"), "w").write(json.dumps(j, indent=1, ensure_ascii=False))
        return "JSON (%d keys) -> %s" % (len(j), outdir)
    except Exception:
        open(os.path.join(outdir, "decoded.bin"), "wb").write(raw)
        return "RAW -> " + outdir


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("usage: decrypt_onepiece.py <file.bin|dir> [outdir]")
        sys.exit(1)
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/packs"
    if os.path.isdir(src):
        for f in sorted(os.listdir(src)):
            if f.endswith(".bin"):
                try:
                    print(f, "->", unpack(os.path.join(src, f), os.path.join(out, f[:-4])))
                except ValueError:
                    print(f, "-> skip (bukan pack onepieceencrypts)")
    else:
        print(unpack(src, out))
