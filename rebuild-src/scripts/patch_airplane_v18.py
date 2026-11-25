#!/usr/bin/env python3
# PATCH AIRPLANE-MODE v18 — classes.dex (ukuran byte IDENTIK, 6 unit berubah total).
#
# Kesalahan 1 owner (v18 feedback, kata-kata persis):
#   "kesalahan 1 : masih ada popup notifikasi error network saat mode pesawat..
#    gambar terlampir dan itu jelas sangat salah ."
#
# AKAR MASALAH (bukti decode bytecode classes.dex v13, bukan karangan):
#   com.zhuhuan.game.LaunchActivity.dishbesideshockey (mesin state update boot)
#   memanggil LaunchActivity.feedtonight() — "IP filter" — yang melakukan
#   PEMANGGILAN HTTP KE HOST ASLI:
#     new URL(ironoriginhoblike.representativehodmandodliving).openConnection()
#     getResponseCode() → parse retJSON
#   Saat mode pesawat: DNS/koneksi MATI → exception → catch block dishbesideshockey
#     (pc=4ef: sget mActivity@3ef5; if-eqz skip) → runOnUiThread(
#     "Read Upgrade Config File Fail,Please Check NetWork！" + getString(
#     R.string.check_network)) → POPUP "network error" DI MODE PESAWAT.
#   Jalur normal (terbukti log device): feedtonight() jatuh ke FALSE → jalur
#   download upgrade dari 127.0.0.1:9528 (log seq 18-22). Return false PAKSA =
#   perilaku IDENTIK dengan mode normal, minus exception palsu.
#
# PATCH v18 (2 unit baru) — LaunchActivity.feedtonight (virtual, code_off 5fd6fc):
#   @file 5fd70c  54 80 c6 a8 (iget-object v0,field@a8c6) -> 12 00 0f 00
#                 = const/4 v0,0 ; return v0   (return-false segera, NOL HTTP)
#   Call site terverifikasi (scan payload-aware, seluruh dex): HANYA 2 —
#   LaunchActivity.dishbesideshockey pc=2b (hanya utk log "IsFilterIP: ") dan
#   pc=51 (if-eqz → jalur FALSE = jalur log-terbukti). NOL perubahan perilaku
#   mode normal.
#
# PATCH warisan v15 (4 unit) — tetap dipertahankan:
#   A) com.quickgame.android.sdk.feedtonight / hoboismrelationbelow (SDK init):
#      @file 5e816c  0A->08 (if-eqz +10->+8)   @file 5e8174  0A 00->12 10 (const/4 v0,1)
#   B) com.quickgame.android.sdk.activity.HWLoginActivity / onCreate:
#      @file 5d242c  0A 0C->12 1C (const/4 v12,1)   @file 5d2430  04->02 (if-eqz +4->+2)
#
# opcode dalvik: iget-object 0x54 | const/4 0x12 | return 0x0f |
#                move-result 0x0a | if-eqz 0x38
# Setelah patch: adler32 (off 8) + SHA1 (off 12) header dihitung ulang.
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v18/classes.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v18/dexout/classes.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n', 'bukan dex'
assert len(d) == 6697176, 'ukuran dex base tidak sesuai: %d' % len(d)
print('input %s — %d B' % (IN, len(d)))

def patch(off, before, after, note):
    cur = bytes(d[off:off + len(before)])
    assert cur == before, '@%06x byte kini %s != %s' % (off, cur.hex(), before.hex())
    d[off:off + len(after)] = after
    print('  @%06x %s -> %s  (%s)' % (off, before.hex(), after.hex(), note))

# --- warisan v15: SDK init + HWLoginActivity (2 titik, 4 unit) ---
patch(0x5e816c, bytes([0x0A]), bytes([0x08]), 'feedtonight.hoboismrelationbelow if-eqz +10->+8')
patch(0x5e8174, bytes([0x0A, 0x00]), bytes([0x12, 0x10]), 'feedtonight.hoboismrelationbelow move-result -> const/4 v0,1')
patch(0x5d242c, bytes([0x0A, 0x0C]), bytes([0x12, 0x1C]), 'HWLoginActivity.onCreate move-result v12 -> const/4 v12,1')
patch(0x5d2430, bytes([0x04]), bytes([0x02]), 'HWLoginActivity.onCreate if-eqz +4->+2')
# --- BARU v18: LaunchActivity.feedtonight — return false segera (2 unit) ---
patch(0x5fd70c, bytes([0x54, 0x80, 0xC6, 0xA8]), bytes([0x12, 0x00, 0x0F, 0x00]),
      'LaunchActivity.feedtonight iget-object -> const/4 v0,0 ; return v0 (nol HTTP, popup pesawat mati di akarnya)')

# ---- header dex: checksum adler32 + SHA1 dihitung ulang ----
d[12:32] = hashlib.sha1(d[32:]).digest()
d[8:12] = struct.pack('<I', zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)

open(OUT, 'wb').write(bytes(d))
got = hashlib.sha256(bytes(d)).hexdigest()
print('output %s — %d B (ukuran identik base: %s)' % (OUT, len(d), len(d) == 6697176))
print('sha256 classes.dex v18:', got)
