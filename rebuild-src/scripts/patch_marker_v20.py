#!/usr/bin/env python3
# PATCH MARKER DEX v20 — classes2.dex LOGIN-SERVER (ukuran byte IDENTIK, 4 string marker).
# Dex v20 = KODE BARU (Bagian 67): compile javac 21.0.13 (stubs + javasrc,
# 24 file com/db/local/server) -> D8 8.3.37 --release --min-api 21 ->
# 147.952 B. Kini patch penanda v19 -> v20 (MUTF-8 string pool, panjang sama,
# offset tak berubah; SHA1 + adler32 header dihitung ulang).
# Marker (4, semuanya user-visible, xref byte penuh):
#   LoggerHandler <span id=ver>v20</span>          (judul dashboard)
#   LoggerHandler banner ...MOD-LOCALSERVER v20    (banner dashboard)
#   LocalServerStarter ...MOD-LOCALSERVER v20 — serverRoot=  (BOOT line)
#   CrashHook      MOD-LOCALSERVER v20 — CRASH REPORT        (header laporan crash)
# Kode login-server v20 SAH ada di dex ini (atlas Bagian 64/66/67):
#   seed 'DragonBall Local' (GetServerList.json), 'LOGIN-SERVER corrupted',
#   handler.process — DILARANG diblokir; string fabrikasi lama yang dihapus
#   sejak v18 TETAP dilarang (serverListJson/enterGameResponse/routeEvent/ACK: 43).
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v20/dexout/classes.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v20/dexout/classes2.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n'
n = d.count(b'v19')
assert n == 4, 'v19 count %d != 4 — dex base tidak sesuai' % n
assert b'v20' not in d, 'v20 sudah ada di base?'
assert b'v18' not in d, 'sisa v18 masih ada'
d = d.replace(b'v19', b'v20')
assert d.count(b'v20') == 4 and b'v19' not in d

# ---- kontrak login-server v20 (Bagian 67) ----
# nama file = action + ".json" dirakit runtime — di dex ada sebagai konstanta
# action + ".json" terpisah (bukan literal gabungan).
for m in (b'MOD-LOCALSERVER v20', b'>v20<', b'handler.process',
          b'DragonBall Local', b'LOGIN-SERVER', b'GetServerList',
          b'loginGame', b'SaveHistory', b'LoginAnnounce', b'SaveLanguage',
          b'SaveUserEnterInfo', b'.json', b'login-server', b'history.sqlite',
          b'jawab', b'tolak103', b'whitelist', b'historyMode',
          b'todayLoginCount', b'loginToken'):
    assert m in d, 'marker v20/login-server hilang: %r' % m
# fabrikasi lama v18 TETAP DIHAPUS dari dex (warisan v18 — tidak kembali)
for ban in (b'serverListJson', b'enterGameResponse', b'routeEvent', b'ACK: 43'):
    assert ban not in d, 'string lama terlarang masih ada: %r' % ban
# pangkas lama TETAP tidak ada (disiplin v17: URL penuh, nol pangkas)
for cut in (b'seen.length() < 1800', b'ack.substring(0, 400)'):
    assert cut not in d, 'pangkas lama masih ada: %r' % cut

# ---- header dex: checksum adler32 + SHA1 dihitung ulang ----
d[12:32] = hashlib.sha1(d[32:]).digest()
d[8:12] = struct.pack('<I', zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)

open(OUT, 'wb').write(bytes(d))
print('marker v19 -> v20: 4 string (judul, banner, BOOT, crash)')
print('output %s — %d B (identik ukuran)' % (OUT, len(d)))
print('sha256 classes2.dex v20:', hashlib.sha256(bytes(d)).hexdigest())
