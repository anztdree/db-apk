#!/usr/bin/env python3
# PATCH CONSOLE DEX v19 — classes2.dex (ukuran byte IDENTIK, 4 string marker).
# Console v18 utuh (URL penuh, filter error, salin, jeda, socket.io TRANSPORT
# ONLY, nol pangkas) — HANYA penanda versi dinaikkan v18 -> v19 (MUTF-8 string
# pool, panjang sama, offset tak berubah; SHA1 + adler32 header dihitung ulang).
# Marker (4, semuanya user-visible, xref byte penuh):
#   01557d <span id=ver>v18</span>            (judul dashboard)
#   0157d8 ...MOD-LOCALSERVER v18</div>       (banner dashboard)
#   017643 ...MOD-LOCALSERVER v18 — serverRoot=  (BOOT line LocalServerStarter)
#   017846 MOD-LOCALSERVER v18 — CRASH REPORT   (header laporan crash)
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/home/z/my-project/rebuild-src/scripts/v19-work/classes2.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v19/dexout/classes2.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n'
n = d.count(b'v18')
assert n == 4, 'v18 count %d != 4 — dex base tidak sesuai' % n
assert b'v19' not in d, 'v19 sudah ada di base?'
d = d.replace(b'v18', b'v19')
assert d.count(b'v19') == 4 and b'v18' not in d
print('marker v18 -> v19: 4 string (judul, banner, BOOT, crash)')

# ---- header dex: checksum adler32 + SHA1 dihitung ulang ----
d[12:32] = hashlib.sha1(d[32:]).digest()
d[8:12] = struct.pack('<I', zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)

open(OUT, 'wb').write(bytes(d))
print('output %s — %d B (identik ukuran)' % (OUT, len(d)))
print('sha256 classes2.dex v19:', hashlib.sha256(bytes(d)).hexdigest())
