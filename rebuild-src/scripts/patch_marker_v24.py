#!/usr/bin/env python3
# PATCH MARKER DEX v24 — classes2.dex LOGIN-SERVER (pola sama dengan v20..v23).
# Dex v24 = KODE BARU (Bagian 72): CAPTURE CLIENT — cara kerja eruda (contoh
# owner) → log global SATU FILE. Jawaban laporan owner (kata-kata persis):
#   "sudah di bilang di game itu sedang loading !! artinya ada log yg jelas
#    gk tertangkap malah log anda itu isinya apa babi ."
#   "Sudah di bilang log natural masih aja ngeyel !!! kalau loading ya dia
#    itu loading apa ! lihat log eruda sebagai contoh goblok !!"
# compile javac 21.0.13 (stubs 8 + javasrc 26 file com/db/local/server; BARU:
# ClientCapture.java + ClientLogHandler.java; edit StaticHandler + ServerCore
# + SocketIoHandler + LoggerHandler; TANPA SmokeMain) -> D8 8.3.37 --release
# --min-api 21 (input jar) -> patch penanda v19 -> v24 (MUTF-8 string pool,
# panjang sama, offset tak berubah; SHA1 + adler32 header dihitung ulang).
# Marker (4, semuanya user-visible, xref byte penuh):
#   LoggerHandler <span id=ver>v24</span>          (judul dashboard)
#   LoggerHandler banner ...MOD-LOCALSERVER v24    (banner dashboard)
#   LocalServerStarter ...MOD-LOCALSERVER v24 — serverRoot=  (BOOT line)
#   CrashHook      MOD-LOCALSERVER v24 — CRASH REPORT        (header laporan crash)
# Kontrak BARU v24 (Bagian 72 — capture client):
#   /log/client (endpoint POST batch) · __dbClientCapture (penanda script) ·
#   kind CLIENT (baris kejadian halaman game) · console.error dari halaman
#   game · window.onerror dari halaman game · capture client terinjeksi
#   (script N B — extra baris 200 halaman) · pola socket GAME: client
#   menunggu event 'verify' ... loading macet di sini (SETIAP long-poll
#   timeout sesi game ber-alasan — bukan hanya di open).
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v24/classes.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v24/dexout/classes2.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n'
n = d.count(b'v19')
assert n == 4, 'v19 count %d != 4 — dex base tidak sesuai' % n
assert b'v20' not in d, 'v20 ada di dex baru? (sumber harus netral v20)'
assert b'v21' not in d, 'v21 sudah ada di base?'
assert b'v22' not in d, 'v22 sudah ada di base?'
assert b'v23' not in d, 'v23 sudah ada di base?'
assert b'v24' not in d, 'v24 sudah ada di base?'
d = d.replace(b'v19', b'v24')
assert d.count(b'v24') == 4 and b'v19' not in d

# ---- kontrak login-server (warisan v20/v21/v22/v23 — tetap wajib) ----
for m in (b'MOD-LOCALSERVER v24', b'>v24<', b'handler.process',
          b'DragonBall Local', b'LOGIN-SERVER', b'login-server',
          b'history.sqlite', b'SaveUserEnterInfo',
          b'server.ndjson', b'level":"ERROR', b'404 MISSING: ',
          b'abnormal exit: ', b'antre polling', b'WS langsung',
          b'upgrade selesai', b'__listenerport',
          b'pola socket GAME', b'gameserver belum dibangun',
          b"menunggu event 'verify' dari server",
          b'long-poll timeout 10 dtk tanpa data',
          b"tidak pernah mengirim '5'",
          b'berisi: ', b'serverlist SAMPAI ke client',
          b'WS berakhir (EOF/koneksi putus tanpa close frame)',
          b'WS close dari client'):
    assert m in d, 'kontrak lama hilang: %s' % m.decode()

# ---- kontrak BARU v24 (capture client — Bagian 72) ----
for m in (b'/log/client',
          b'__dbClientCapture',
          b'CLIENT',
          b'console.error dari halaman game',
          b'window.onerror dari halaman game',
          b'unhandledrejection dari halaman game',
          b'capture client terinjeksi (script ',
          b'pola socket GAME: client menunggu event ',
          b'loading macet di sini',
          b'batch log client tidak valid',
          b'script halaman game gagal dimuat di client',
          b'XHR halaman game gagal/4xx di client',
          b'setInterval'):
    assert m in d, 'kontrak BARU v24 hilang: %s' % m.decode()

# terlarang (fabrikasi lama v18 — nol)
for m in (b'serverListJson', b'enterGameResponse', b'routeEvent', b'"ACK: 43'):
    assert m not in d, 'fabrikasi lama terdeteksi: %s' % m.decode()

# ---- header dex: SHA1 + adler32 dihitung ulang ----
def u32(off): return struct.unpack_from('<I', d, off)[0]
def put_u32(off, val): struct.pack_into('<I', d, off, val)

size = len(d)
put_u32(32, size)
body = bytes(d[12:size - 8])
sha = hashlib.sha1(body).digest()
d[size - 20:size] = sha
a = zlib.adler32(bytes(d[12:size - 8])) & 0xFFFFFFFF
put_u32(8, a)

import os
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'wb').write(bytes(d))
print('PATCH OK: %s -> %s (%d B) sha256 %s' % (IN, OUT, len(d), hashlib.sha256(bytes(d)).hexdigest()))
