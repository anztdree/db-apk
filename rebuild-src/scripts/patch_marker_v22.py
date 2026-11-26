#!/usr/bin/env python3
# PATCH MARKER DEX v22 — classes2.dex LOGIN-SERVER (pola sama dengan v20/v21).
# Dex v22 = KODE BARU (Bagian 70): compile javac 21.0.13 (stubs 8 + javasrc
# 24 file com/db/local/server; edit DiskWriter + ExitInfo + LoggerCore +
# StaticHandler + LoggerHandler levelOf; TANPA SmokeMain) -> D8 8.3.37
# --release --min-api 21 -> patch penanda v19 -> v22 (MUTF-8 string pool,
# panjang sama, offset tak berubah; SHA1 + adler32 header dihitung ulang).
# Marker (4, semuanya user-visible, xref byte penuh):
#   LoggerHandler <span id=ver>v22</span>          (judul dashboard)
#   LoggerHandler banner ...MOD-LOCALSERVER v22    (banner dashboard)
#   LocalServerStarter ...MOD-LOCALSERVER v22 — serverRoot=  (BOOT line)
#   CrashHook      MOD-LOCALSERVER v22 — CRASH REPORT        (header laporan crash)
# Kode login-server v22 SAH ada di dex ini (atlas Bagian 64/66/67):
#   seed 'DragonBall Local' (GetServerList.json), 'LOGIN-SERVER corrupted',
#   handler.process — DILARANG diblokir; string fabrikasi lama yang dihapus
#   sejak v18 TETAP dilarang (serverListJson/enterGameResponse/routeEvent/ACK: 43).
# Marker BARU v22 (Bagian 70 — LOG 1 FILE DI TUMPUK + 404 NATURAL):
#   DiskWriter server.ndjson (1 file append) · LoggerCore level ERROR ·
#   StaticHandler 404 MISSING · ExitInfo entri EXIT dedup exitTime ·
#   exit-history.ndjson DIHAPUS dari dex (tidak boleh kembali).
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v22/dexout/classes.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v22/dexout/classes2.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n'
n = d.count(b'v19')
assert n == 4, 'v19 count %d != 4 — dex base tidak sesuai' % n
assert b'v20' not in d, 'v20 ada di dex baru? (sumber harus netral v20)'
assert b'v21' not in d, 'v21 sudah ada di base?'
assert b'v22' not in d, 'v22 sudah ada di base?'
d = d.replace(b'v19', b'v22')
assert d.count(b'v22') == 4 and b'v19' not in d

# ---- kontrak login-server (warisan v20 — Bagian 67, tetap wajib) ----
for m in (b'MOD-LOCALSERVER v22', b'>v22<', b'handler.process',
          b'DragonBall Local', b'LOGIN-SERVER', b'GetServerList',
          b'loginGame', b'SaveHistory', b'LoginAnnounce', b'SaveLanguage',
          b'SaveUserEnterInfo', b'.json', b'login-server', b'history.sqlite',
          b'jawab', b'tolak103', b'whitelist', b'historyMode',
          b'todayLoginCount', b'loginToken'):
    assert m in d, 'marker v22/login-server hilang: %r' % m
# ---- kontrak v21 (Bagian 69 — routing transport ACK, tetap wajib) ----
for m in (b'antre polling', b'WS langsung', b'upgrade selesai',
          b'dikirim: ', b'__listenerport', b'sesi sebelumnya berakhir',
          b'completeUpgrade'):
    assert m in d, 'marker v21 hilang: %r' % m
# ---- kontrak BARU v22 (Bagian 70 — log 1 file di-tumpuk + 404 natural) ----
for m in (b'server.ndjson', b'level":"ERROR', b'404 MISSING: ',
          b'tidak ada di server/', b'dan tidak ada di APK (assets)',
          b'abnormal exit: ', b'EXITINFO', b'"kind":"EXIT"',
          b'no exit history (normal for a fresh install)'):
    assert m in d, 'marker v22 hilang: %r' % m
# ---- exit-history.ndjson DIHAPUS (v22 — dilarang kembali) ----
assert b'exit-history.ndjson' not in d, \
    'exit-history.ndjson masih ada di dex — Bagian 70 menghapus file terpisah'
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
print('marker v19 -> v22: 4 string (judul, banner, BOOT, crash) + kontrak v22')
print('output %s — %d B' % (OUT, len(d)))
print('sha256 classes2.dex v22:', hashlib.sha256(bytes(d)).hexdigest())
