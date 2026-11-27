#!/usr/bin/env python3
# PATCH MARKER DEX v27 — classes2.dex LOGIN-SERVER (pola v20..v26 — rumus spec
# DEX + verifikasi struktur penuh, warisan fix force-close v25).
#
# ARAHAN OWNER (27 Nov 2026, kata-kata persis):
#   "itu kan ana tinggal belajar pola rebuild lalu tinggal lanjutkan port log
#    debug sudah jelas ada di 4000 lanjutkan lah jangan bikin Bru"
# → v27 = LANJUTAN log debug port 4000 (BUKAN sistem baru): capture client v3
#   = capture v2 + WORKER WATCHDOG + script-start + ExternalInterface hook.
#
# ============================================================
# AKAR YANG DITEMUKAN v27 (bukti DUMP DEVICE v26, 27 Nov 2026 — bukan karangan):
# ============================================================
# Dump device owner v26 (12:57:41 — 12:57:53) menunjukkan:
#   #48-60  13 script manifest tercatat sampai ThinkingAnalyticsSDK … lalu HILANG.
#   #62     clientversion.json 200 37 B sampai ke SERVER (12:57:53.420) —
#           padahal file itu HANYA dipanggil kode main.min.js (RES.getResByUrl,
#           dibuktikan grep APK). Artinya default.thm + main.min.js (4,9 MB)
#           TER-LOAD dan JALAN, TAPI event capture-nya (script load, xhr)
#           TIDAK PERNAH sampai ke server.
#   Tidak ada fps/heartbeat/resource sweep setelahnya → main thread WebView
#   BEKU: eksekusi 4,9 MB JS minified di device lemah (itel S665L) memblokir
#   setTimeout tick 500 ms → buffer capture tak pernah ter-flush → dashboard
#   "ngeyel cuma itu doang". ERUDA JUGA BEKU di situasi yang sama (satu main
#   thread) — inilah celah utk lebih canggih.
# FIX v27 (capture v3 — lanjutan v2, bukan sistem baru):
#   1. WORKER WATCHDOG: buffer+flush pindah ke Web Worker (thread terpisah);
#      pengiriman log TIDAK tergantung main thread lagi. Beat 1 dtk berhenti
#      >4 dtk → worker melapor WARN tiap 5 dtk: "watchdog main thread tidak
#      merespons ~Ns — kejadian terakhir: <type>"; pulih → baris "pulih
#      setelah beku ~Ns". KEKOSONGAN jadi TERLIHAT + BERLABEL.
#   2. SCRIPT-START (MutationObserver): pemuatan script/media tercatat saat
#      elemen dibuat+src — loader asli tanpa onerror (hang senyap) kini
#      meninggalkan jejak "script mulai: …" TANPA baris load = bukti hang.
#   3. EXTERNALINTERFACE HOOK: saksi jembatan native — "ExternalInterface
#      call startGame" tanpa kelanjutan = game menunggu jawaban native
#      (runEgret hanya jalan setelah native menjawab).
#   Worker tak didukung/gagal = fallback PENUH ke jalur v2 — nol yang hilang.
#
# RUMUS HEADER DEX (spec — warisan v23/v25/v26, dipertahankan):
#   signature (SHA-1)  @ offset 12..32, dihitung atas d[32:] s/d akhir file
#   checksum (adler32) @ offset 8..12,  dihitung atas d[12:] s/d akhir file
# VERIFIKASI STRUKTUR (warisan v25 — wajib): checksum+signature self-check,
# header magic/file_size/header_size/endian_tag, map_list di-parse PENUH
# (17 entri berakhir TEPAT di EOF, offset naik, elemen tetap dicek,
# map_list 0x1000 penutup), section header dalam batas. Gagal = sys.exit.
#
# Marker (4, semuanya user-visible, panjang sama 3 byte "v19"->"v27"):
#   LoggerHandler <span id=ver>v27</span>          (judul dashboard)
#   LoggerHandler banner ...MOD-LOCALSERVER v27    (banner dashboard)
#   LocalServerStarter ...MOD-LOCALSERVER v27 — serverRoot=  (BOOT line)
#   CrashHook      MOD-LOCALSERVER v27 — CRASH REPORT        (header crash)
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v27/dexout/classes.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v27/dexout/classes2.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n', 'bukan dex'
n = d.count(b'v19')
assert n == 4, 'v19 count %d != 4 — dex base tidak sesuai' % n
assert b'v27' not in d, 'v27 sudah ada di base? (sumber harus netral)'
for ban in (b'v20', b'v21', b'v22', b'v23', b'v24', b'v25', b'v26'):
    assert ban not in d, ban.decode() + ' ada di base?'
d = d.replace(b'v19', b'v27')
assert d.count(b'v27') == 4 and b'v19' not in d

# ---- kontrak login-server (warisan v20..v26 — tetap wajib) ----
for m in (b'MOD-LOCALSERVER v27', b'>v27<', b'handler.process',
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

# ---- kontrak v24 (capture client — Bagian 72, tetap wajib di v27) ----
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
          b'XHR halaman game gagal/4xx di client'):
    assert m in d, 'kontrak v24 hilang: %s' % m.decode()

# ---- kontrak v26 (capture v2 + fix fatal console — tetap wajib di v27) ----
for m in (b"icon:'check'}}",                      # FIX FATAL console (kutip ditutup)
          b'__dbSid',                             # sid sesi halaman
          b'device halaman game: ua=',            # baris CLIENT meta
          b'resource sweep dibatasi 3000',        # sweep resource timing
          b'halaman hidup',                       # heartbeat FPS
          b'longtask',                            # UI macet >50 ms
          b'lifecycle DOMContentLoaded',          # lifecycle
          b'console.timeEnd ',                    # console lengkap
          b'console.group',                       # kedalaman group
          b'PerformanceObserver',                 # longtask observer
          b"m=='longtask'",                       # badge WARN longtask
          b'stack:',                              # sub-baris stack dashboard
          b'"stack":"',                           # NDJSON field stack
          b'navigator.sendBeacon',                # flush terakhir
          b'visibilitychange',
          b'setTimeout(tick,400)'):               # loop tick backoff capture v2
    assert m in d, 'kontrak v26 hilang: %s' % m.decode()

# ---- kontrak v27 BARU (capture v3: worker watchdog + script-start + ei) ----
for m in (b'__dbWorkerReady',                          # tanda worker siap di window
          b'workerMain',                               # sumber worker (toString)
          b'watchdog main thread tidak merespons ~',   # WARN saat main thread beku
          b'watchdog main thread pulih setelah beku ~',# INFO pemulihan
          b'watchdog main thread belum pernah merespons ~',  # beku sejak awal
          b'beat 1 dtk berhenti',                      # fakta sinyal beku
          b'kejadian terakhir: ',                      # label aktivitas terakhir
          b"m=='watchdog'",                            # badge WARN watchdog
          b"m=='script-start'",                        # badge DEBUG script-start
          b'script mulai: ',                           # MutationObserver mulai
          b'media mulai: ',                            # MutationObserver media
          b'MutationObserver',                         # mekanisme script-start
          b'ExternalInterface call ',                  # saksi jembatan native
          b'ExternalInterface addCallback ',           # callback terdaftar
          b'ExternalInterface hook terpasang',         # hook terpasang (egret ada)
          b"m=='script-start'||m=='media-start'",      # baris badge DEBUG v27
          b'CLIENT v3',                                # komentar render dashboard
          b"'('+workerMain.toString()+')()'"):         # pembentukan worker Blob
    assert m in d, 'kontrak v27 hilang: %s' % m.decode()

# ---- FIX FATAL bersyarat: kutip penutup console TIDAK BOLEH berbentuk rusak ----
assert b"icon:'check}}\n" not in d, "console rusak (kutip tak ditutup) MASIH ADA — v27 menolak"

# terlarang (fabrikasi lama v18 — nol)
for m in (b'serverListJson', b'enterGameResponse', b'routeEvent', b'"ACK: 43'):
    assert m not in d, 'fabrikasi lama terdeteksi: %s' % m.decode()

# ======================================================================
# HEADER DEX — RUMUS SPEC (warisan v23/v25/v26):
#   SHA-1   @12..32 atas d[32:]        — BUKAN ekor file
#   adler32 @8..12  atas d[12:]        — rentang PENUH s/d akhir file
# dilarang keras menulis APAPUN setelah offset header — 20 byte terakhir
# dex adalah ekor map_list (pelajaran force-close v24).
# ======================================================================
d[12:32] = hashlib.sha1(bytes(d[32:])).digest()
d[8:12]  = struct.pack('<I', zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)

# ======================================================================
# VERIFIKASI STRUKTUR DEX (warisan v25) — cermin DexFileVerifier ART.
# Kegagalan APAPUN = sys.exit(1) — dex cacat tidak pernah sampai ke APK.
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
map_off = u32(52)
assert 0x70 <= map_off < size, 'map_off di luar file'
nmap = u32(map_off)
assert map_off + 4 + nmap * 12 == size, \
    'map_list tidak berakhir TEPAT di EOF (map_off=%d entri=%d akhir=%d size=%d)' \
    % (map_off, nmap, map_off + 4 + nmap * 12, size)
VALID = {0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006,
         0x1000, 0x1001, 0x1002, 0x1003,
         0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006}
ELEM = {0x0000: 0, 0x0001: 4, 0x0002: 4, 0x0003: 12, 0x0004: 8,
        0x0005: 8, 0x0006: 32, 0x1001: 0, 0x1002: 4, 0x1003: 4,
        0x2000: 0, 0x2001: 0, 0x2002: 0, 0x2003: 0, 0x2004: 0,
        0x2005: 0, 0x2006: 0}
items = []
for i in range(nmap):
    e = map_off + 4 + i * 12
    typ    = struct.unpack_from('<H', d, e)[0]
    unused = struct.unpack_from('<H', d, e + 2)[0]
    cnt    = u32(e + 4)
    off    = u32(e + 8)
    assert unused == 0, 'map_item.unused != 0 (entri %d)' % i
    assert typ in VALID, 'tipe map tidak sah: 0x%04x (entri %d)' % (typ, i)
    assert 0 <= off < size, 'offset map di luar file: tipe 0x%04x off %d' % (typ, off)
    items.append((off, cnt, typ, i))
items.sort()
assert items[0][2] == 0x0000 and items[0][0] == 0 and items[0][1] == 1, \
    'map tidak diawali header section'
for a, b in zip(items, items[1:]):
    assert a[0] < b[0], 'offset map tidak naik: 0x%04x @%d lalu 0x%04x @%d' % (a[2], a[0], b[2], b[0])
    ea = ELEM.get(a[2], 0)
    if ea:
        assert a[0] + a[1] * ea <= b[0], \
            'section 0x%04x (off %d cnt %d) menimpa section 0x%04x' % (a[2], a[0], a[1], b[2])
assert items[-1][2] == 0x1000 and items[-1][0] == map_off and items[-1][1] == 1, \
    'map_list bukan section penutup (ditemukan tipe 0x%04x)' % items[-1][2]
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
print('PATCH OK (v27 — rumus spec + verifikasi struktur penuh): %s -> %s (%d B)' % (IN, OUT, len(d)))
print('  checksum 0x%08x | sha1 %s | map %d entri berakhir tepat di EOF' % (
    u32(8), hashlib.sha1(bytes(d[32:])).hexdigest()[:16] + '…', nmap))
print('sha256 classes2.dex v27: %s' % hashlib.sha256(bytes(d)).hexdigest())
