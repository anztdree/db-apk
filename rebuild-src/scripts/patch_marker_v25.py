#!/usr/bin/env python3
# PATCH MARKER DEX v25 — classes2.dex LOGIN-SERVER (pola v20..v24, DIPERBAIKI).
# FIX FORCE CLOSE v24 (laporan owner 26 Nov malam: "versi 23 belum force
# close, namun versi 24 saat apk di buka langsung force CLOSE tanpa log").
#
# AKAR MASALAH (bukti forensik classes2.dex v24 yang terkirim, 164.748 B):
#   patch_marker_v24.py MENGHITUNG ULANG HEADER DEX DENGAN SALAH:
#     - menulis SHA1 ke 20 BYTE TERAKHIR FILE  (d[size-20:size] = sha)
#     - menghitung SHA1 atas rentang d[12:size-8]  (salah)
#     - menghitung adler32 atas rentang d[12:size-8]  (salah)
#   PADAHAL 20 byte terakhir file = EKOR map_list (map_off 164540,
#   4 + 17x12 = 208 byte, berakhir TEPAT di EOF) — ekor map v24 berisi
#   entri sampah (offset 1.205.860.973 / 1.289.697.428) + checksum header
#   salah (0x49561284, benar 0xee901673) + signature @12..32 basi.
#   ART (DexFileVerifier) menolak dex itu saat kelas pertama di com.db.local
#   .server disentuh di open → SELURUH classes2.dex gagal termuat → force
#   close INSTAN, dan karena LoggerCore/CrashHook juga di dex yang sama,
#   NOL baris log tertulis = persis laporan owner "force CLOSE tanpa log".
#   Desktop smoke 263 PASS tidak menangkapnya karena uji berjalan atas
#   class JVM (bukan dex) dan rebuild hanya memverifikasi struktur zip.
#
# RUMUS BENAR (spec DEX — sama dgn v23 yang terbukti jalan di device):
#   signature (SHA-1)  @ offset 12..32, dihitung atas d[32:] s/d akhir file
#   checksum (adler32) @ offset 8..12,  dihitung atas d[12:] s/d akhir file
#
# BARU v25 — VERIFIKASI STRUKTUR DEX SETELAH PATCH (cermin DexFileVerifier):
#   1. checksum & signature dihitung ulang lalu dibandingkan = SAH;
#   2. header: magic/file_size/header_size/endian_tag;
#   3. map_list di-parse PENUH: jumlah entri, akhir TEPAT di EOF, offset
#      naik & di dalam file, TYPE_MAP_LIST menunjuk map_off sendiri;
#   4. section header (string/type/proto/field/method/class_def) di dalam
#      batas file. Gagal = sys.exit — dex cacat TIDAK PERNAH sampai ke APK.
#
# Marker (4, semuanya user-visible, panjang sama 3 byte "v19"->"v25"):
#   LoggerHandler <span id=ver>v25</span>          (judul dashboard)
#   LoggerHandler banner ...MOD-LOCALSERVER v25    (banner dashboard)
#   LocalServerStarter ...MOD-LOCALSERVER v25 — serverRoot=  (BOOT line)
#   CrashHook      MOD-LOCALSERVER v25 — CRASH REPORT        (header crash)
# Kode Java v25 = KODE v24 (Bagian 72, capture client) TANPA perubahan —
# yang berubah hanya langkah patch header (dan verifikasinya).
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v25/classes.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v25/dexout/classes2.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n', 'bukan dex'
n = d.count(b'v19')
assert n == 4, 'v19 count %d != 4 — dex base tidak sesuai' % n
assert b'v20' not in d, 'v20 ada di dex baru? (sumber harus netral)'
assert b'v21' not in d, 'v21 sudah ada di base?'
assert b'v22' not in d, 'v22 sudah ada di base?'
assert b'v23' not in d, 'v23 sudah ada di base?'
assert b'v24' not in d, 'v24 sudah ada di base?'
d = d.replace(b'v19', b'v25')
assert d.count(b'v25') == 4 and b'v19' not in d

# ---- kontrak login-server (warisan v20..v24 — tetap wajib) ----
for m in (b'MOD-LOCALSERVER v25', b'>v25<', b'handler.process',
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

# ---- kontrak v24 (capture client — Bagian 72, tetap wajib di v25) ----
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
    assert m in d, 'kontrak v24 hilang: %s' % m.decode()

# terlarang (fabrikasi lama v18 — nol)
for m in (b'serverListJson', b'enterGameResponse', b'routeEvent', b'"ACK: 43'):
    assert m not in d, 'fabrikasi lama terdeteksi: %s' % m.decode()

# ======================================================================
# HEADER DEX — RUMUS BENAR (spec DEX / sama dgn patch v23):
#   SHA-1   @12..32 atas d[32:]        — ditulis di offset 12, BUKAN ekor file
#   adler32 @8..12  atas d[12:]        — rentang PENUH s/d akhir file
# dilarang keras menulis APAPUN setelah offset header — 20 byte terakhir
# dex v24 adalah ekor map_list dan menimpanya = penyebab force close.
# ======================================================================
d[12:32] = hashlib.sha1(bytes(d[32:])).digest()
d[8:12]  = struct.pack('<I', zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)

# ======================================================================
# VERIFIKASI STRUKTUR DEX (BARU v25) — cermin pemeriksaan DexFileVerifier
# ART saat memuat dex. Kegagalan APAPUN = sys.exit(1) — dex cacat tidak
# boleh pernah sampai ke APK.
# ======================================================================
def u32(off): return struct.unpack_from('<I', d, off)[0]

size = len(d)
# 1) checksum & signature SAH (dihitung ulang dari file final)
assert u32(8) == (zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF), 'adler32 header tidak sah'
assert bytes(d[12:32]) == hashlib.sha1(bytes(d[32:])).digest(), 'SHA1 signature tidak sah'
# 2) header
assert bytes(d[:8]) == b'dex\n035\x00', 'magic dex bukan 035'
assert u32(32) == size, 'file_size != ukuran file'
assert u32(36) == 0x70, 'header_size != 0x70'
assert u32(40) == 0x12345678, 'endian_tag tidak sah'
# 3) map_list di-parse PENUH — layout map_item YANG BENAR:
#      ushort type; ushort unused; uint size(=jumlah item); uint offset;
#    (patch v24 membolak-balikkan ini dan menimpa ekor map — dua cacat)
map_off = u32(52)
assert 0x70 <= map_off < size, 'map_off di luar file'
nmap = u32(map_off)
assert map_off + 4 + nmap * 12 == size, \
    'map_list tidak berakhir TEPAT di EOF (map_off=%d entri=%d akhir=%d size=%d)' \
    % (map_off, nmap, map_off + 4 + nmap * 12, size)
VALID = {0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006,
         0x1000, 0x1001, 0x1002, 0x1003,
         0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006}
# ukuran elemen tetap per tipe (dex spec) — 0 = variabel
ELEM = {0x0000: 0, 0x0001: 4, 0x0002: 4, 0x0003: 12, 0x0004: 8,
        0x0005: 8, 0x0006: 32, 0x1001: 0, 0x1002: 4, 0x1003: 4,
        0x2000: 0, 0x2001: 0, 0x2002: 0, 0x2003: 0, 0x2004: 0,
        0x2005: 0, 0x2006: 0}
items = []
for i in range(nmap):
    e = map_off + 4 + i * 12
    typ    = struct.unpack_from('<H', d, e)[0]
    unused = struct.unpack_from('<H', d, e + 2)[0]
    cnt    = u32(e + 4)      # size = jumlah item
    off    = u32(e + 8)      # offset dari awal file
    assert unused == 0, 'map_item.unused != 0 (entri %d)' % i
    assert typ in VALID, 'tipe map tidak sah: 0x%04x (entri %d)' % (typ, i)
    assert 0 <= off < size, 'offset map di luar file: tipe 0x%04x off %d' % (typ, off)
    items.append((off, cnt, typ, i))
items.sort()
# header = item pertama, tepat di offset 0
assert items[0][2] == 0x0000 and items[0][0] == 0 and items[0][1] == 1, \
    'map tidak diawali header section'
# offset naik ketat antar section (tidak tumpang tindih)
for a, b in zip(items, items[1:]):
    assert a[0] < b[0], 'offset map tidak naik: 0x%04x @%d lalu 0x%04x @%d' % (a[2], a[0], b[2], b[0])
    ea = ELEM.get(a[2], 0)
    if ea:
        assert a[0] + a[1] * ea <= b[0], \
            'section 0x%04x (off %d cnt %d) menimpa section 0x%04x' % (a[2], a[0], a[1], b[2])
# section terakhir (terbesar offset) harus map_list sendiri, berakhir di EOF
assert items[-1][2] == 0x1000 and items[-1][0] == map_off and items[-1][1] == 1, \
    'map_list bukan section penutup (ditemukan tipe 0x%04x)' % items[-1][2]
# 4) section header di dalam batas file — pasangan (size_off, off_off)
#    yang benar: size dan offset SELANG-SELING di header
sec = [(56, 60, 'string_ids', 4), (64, 68, 'type_ids', 4),
       (72, 76, 'proto_ids', 12), (80, 84, 'field_ids', 8),
       (88, 92, 'method_ids', 8), (96, 100, 'class_defs', 32)]
for sz_field, off_field, name, esz in sec:
    cnt = u32(sz_field)
    off = u32(off_field)
    assert cnt == 0 or (0x70 <= off and off + cnt * esz <= size), \
        'section %s di luar file (off=%d cnt=%d)' % (name, off, cnt)

import os
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'wb').write(bytes(d))
print('PATCH OK (v25 — rumus spec + verifikasi struktur penuh): %s -> %s (%d B)' % (IN, OUT, len(d)))
print('  checksum 0x%08x | sha1 %s | map %d entri berakhir tepat di EOF' % (
    u32(8), hashlib.sha1(bytes(d[32:])).hexdigest()[:16] + '…', nmap))
print('sha256 classes2.dex v25: %s' % hashlib.sha256(bytes(d)).hexdigest())
