#!/usr/bin/env python3
"""Baca entri tertentu dari zip besar via HTTP Range (tanpa unduh penuh).
Usage: python3 zip_partial.py <url> <daftar-nama-entri...>
Nama entri dicari dengan substring; hasil disimpan ./out/<nama-basename>
"""
import io, os, re, sys, urllib.request, zlib

URL = sys.argv[1]
WANT = sys.argv[2:]
OUT = "out"

def head():
    r = urllib.request.Request(URL, method="HEAD")
    with urllib.request.urlopen(r, timeout=60) as h:
        return int(h.headers["Content-Length"])

def rng(a, b):
    # inklusif [a, b]
    r = urllib.request.Request(URL, headers={"Range": "bytes=%d-%d" % (a, b)})
    with urllib.request.urlopen(r, timeout=120) as h:
        return h.read()

def find_eocd(tail):
    i = tail.rfind(b"PK\x05\x06")
    assert i >= 0, "EOCD tidak ketemu"
    (sig, disk, cddisk, ndisk, ntotal, cdsize, cdoff, clen) = io.Struct = __import__("struct").unpack("<IHHHHIIH", tail[i:i+22])
    return cdoff, cdsize, ntotal

def parse_cd(cd):
    ents = []
    i = 0
    st = __import__("struct")
    while i < len(cd):
        if i + 46 > len(cd):
            break  # sisa byte bukan record penuh (comment/trailing)
        if cd[i:i+4] != b"PK\x01\x02":
            break
        (sig, vmade, vneed, flag, method, mtime, mdate, crc, csize, usize,
         nlen, elen, clen, dstart, iattr, eattr, loff) = st.unpack("<IHHHHHHIIIHHHHHII", cd[i:i+46])
        name = cd[i+46:i+46+nlen].decode("utf-8", "replace")
        ents.append((name, method, csize, usize, loff))
        i += 46 + nlen + elen + clen
    return ents

def read_entry(name, method, csize, usize, loff):
    # lokal header: baca 30 byte + nama + extra dulu
    lh = rng(loff, loff + 29)
    st = __import__("struct")
    sig, vneed, flag, meth, mt, md, crc, csize_l, usize_l, nlen, elen = st.unpack("<IHHHHHIIIHH", lh)
    assert sig == 0x04034b50, "local header salah utk %s" % name
    data_off = loff + 30 + nlen + elen
    blob = rng(data_off, data_off + csize_l - 1)
    if meth == 0:
        return blob
    if meth == 8:
        return zlib.decompress(blob, -15)
    raise SystemExit("metode %d tidak didukung" % meth)

def main():
    os.makedirs(OUT, exist_ok=True)
    total = head()
    print("ukuran zip:", total)
    tail = rng(max(0, total - 65557), total - 1)  # cukup utk EOCD saja
    cdoff, cdsize, ntotal = find_eocd(tail)
    print("CD off=%d size=%d entri=%d" % (cdoff, cdsize, ntotal))
    cd = rng(cdoff, cdoff + cdsize - 1)  # fetch CD persis
    ents = parse_cd(cd)
    print("entri:", len(ents))
    for want in WANT:
        pat = re.compile(re.escape(want).replace(r"\*", ".*"))
        hits = [e for e in ents if pat.search(e[0])]
        if not hits:
            print("TIDAK KETEMU:", want)
            continue
        for (name, method, csize, usize, loff) in hits:
            print("- %s method=%d csize=%d usize=%d" % (name, method, csize, usize))
            data = read_entry(name, method, csize, usize, loff)
            dst = os.path.join(OUT, os.path.basename(name))
            with open(dst, "wb") as f:
                f.write(data)
            print("  ->", dst, len(data), "B")

if __name__ == "__main__":
    main()
