#!/usr/bin/env python3
# PATCH MARKER DEX v23 — classes2.dex LOGIN-SERVER (pola sama dengan v20/v21/v22).
# Dex v23 = KODE BARU (Bagian 71): LOG NATURAL FASE LOADING — jawaban laporan
# owner: "heee itu hlo loading tanpa ada keterangan apapun di log yg sudah
# saya update !!!" + "KALAU ERROR YA ERROR ! KALAU KURANG YAA KURANG !
# BIAR JELAS KITA ITU KURANGNYA DIMANAAA".
# compile javac 21.0.13 (stubs 8 + javasrc 24 file com/db/local/server;
# edit SocketIoHandler + LoggerCore; TANPA SmokeMain) -> D8 8.3.37
# --release --min-api 21 -> patch penanda v19 -> v23 (MUTF-8 string pool,
# panjang sama, offset tak berubah; SHA1 + adler32 header dihitung ulang).
# Marker (4, semuanya user-visible, xref byte penuh):
#   LoggerHandler <span id=ver>v23</span>          (judul dashboard)
#   LoggerHandler banner ...MOD-LOCALSERVER v23    (banner dashboard)
#   LocalServerStarter ...MOD-LOCALSERVER v23 — serverRoot=  (BOOT line)
#   CrashHook      MOD-LOCALSERVER v23 — CRASH REPORT        (header laporan crash)
# Marker BARU v23 (Bagian 71 — keterangan natural fase loading/socket):
#   open sesi ke-2 = pola socket GAME + fase gameserver belum dibangun +
#   menunggu event 'verify' (atlas §1) · long-poll timeout ber-fakta sesi ·
#   "berisi:" desc payload polling (ACK/noop) · "serverlist SAMPAI ke client" ·
#   WS close/EOF tidak lagi senyap ("WS berakhir") · seq == urutan tulis
#   file (LoggerCore SEQLOCK).
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v23/dexout/classes.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v23/dexout/classes2.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n'
n = d.count(b'v19')
assert n == 4, 'v19 count %d != 4 — dex base tidak sesuai' % n
assert b'v20' not in d, 'v20 ada di dex baru? (sumber harus netral v20)'
assert b'v21' not in d, 'v21 sudah ada di base?'
assert b'v22' not in d, 'v22 sudah ada di base?'
assert b'v23' not in d, 'v23 sudah ada di base?'
d = d.replace(b'v19', b'v23')
assert d.count(b'v23') == 4 and b'v19' not in d

# ---- kontrak login-server (warisan v20 — Bagian 67, tetap wajib) ----
for m in (b'MOD-LOCALSERVER v23', b'>v23<', b'handler.process',
          b'DragonBall Local', b'LOGIN-SERVER', b'GetServerList',
          b'loginGame', b'SaveHistory', b'LoginAnnounce', b'SaveLanguage',
          b'SaveUserEnterInfo', b'.json', b'login-server', b'history.sqlite',
          b'jawab', b'tolak103', b'whitelist', b'historyMode',
          b'todayLoginCount', b'loginToken'):
    assert m in d, 'marker v23/login-server hilang: %r' % m
# ---- kontrak v21 (Bagian 69 — routing transport ACK, tetap wajib) ----
for m in (b'antre polling', b'WS langsung', b'upgrade selesai',
          b'dikirim: ', b'__listenerport', b'sesi sebelumnya berakhir',
          b'completeUpgrade'):
    assert m in d, 'marker v21 hilang: %r' % m
# ---- kontrak v22 (Bagian 70 — log 1 file + 404 natural, tetap wajib) ----
for m in (b'server.ndjson', b'level":"ERROR', b'404 MISSING: ',
          b'tidak ada di server/', b'dan tidak ada di APK (assets)',
          b'abnormal exit: ', b'EXITINFO', b'"kind":"EXIT"',
          b'no exit history (normal for a fresh install)'):
    assert m in d, 'marker v22 hilang: %r' % m
# ---- kontrak BARU v23 (Bagian 71 — keterangan natural fase loading) ----
for m in (b'pola socket GAME',
          b'gameserver belum dibangun',
          b"menunggu event 'verify' dari server",
          b'long-poll timeout 10 dtk tanpa data',
          b"tidak pernah mengirim '5'",
          b'berisi: ',
          b'serverlist SAMPAI ke client',
          b'noop 6 (upgrade selesai',
          b'WS berakhir (EOF/koneksi putus tanpa close frame)',
          b'WS close dari client',
          b'hanya probe WS',
          b'client belum memulai probe WS',
          b"belum ('5' tak diterima)",
          b'umur '):
    assert m in d, 'marker v23 hilang: %r' % m
# ---- exit-history.ndjson TETAP dihapus (v22 — dilarang kembali) ----
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
print('marker v19 -> v23: 4 string (judul, banner, BOOT, crash) + kontrak v23')
print('output %s — %d B' % (OUT, len(d)))
print('sha256 classes2.dex v23:', hashlib.sha256(bytes(d)).hexdigest())
