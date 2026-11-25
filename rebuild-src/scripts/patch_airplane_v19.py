#!/usr/bin/env python3
# PATCH AIRPLANE-MODE v19 — classes.dex (ukuran byte IDENTIK, 8 unit berubah total).
#
# Kesalahan 1 owner (v19 feedback, kata-kata persis):
#   "MASIH MUNCUL ERROR NETWORK PADA SAAT MODE PESAWAT. SEPERTI PADA GAMBAR YG
#    TERLAMPIR DI GITHUB. MOHON TELUSURI SAMPAI AKAR !!"
#
# BEDAH AKAR SELESAI (bukan karangan — decode instruksi + parse resources.arsc):
#   Popup screenshot owner: title 'mengingat' (R.string.hw_network_dialog_title
#   0x7f0c0111) + message 'Koneksi ke jaringan gagal，Silahkan periksa apakah
#   jaringan sudah terhubung。' (R.string.hw_network_dialog_message 0x7f0c010f,
#   values-in) — DUA-DUANYA dipakai quickgame SDK.
#
#   Dialog dibangun di 3 lokasi (xref penuh classes.dex):
#     1) com.quickgame.android.sdk.feedtonight.hoboismrelationbelow (code_off
#        5e8138) — SDK init, berjalan DI ATAS BOOT SCREEN LaunchActivity =
#        POPUP SCREENSHOT OWNER. Gerbang pc=001c: if-eqz v0,+55 -> dialog 0053.
#     2) com.quickgame.android.sdk.activity.HWLoginActivity.onCreate (5d2100)
#        — gerbang pc=0194: if-eqz v12,+20 -> dialog 01a8.
#     3) feedtonight.worktopichardtails (5e8f6c) — dialog TIPS isi dari server
#        (bukan teks network; bukan popup screenshot).
#
#   AKAR KELOLOSAN v15..v18 — BYTES PATCH v15 SALAH RAKIT (nibble kebalik):
#     v15 menulis @5e8174 [12 10] dengan niat "const/4 v0,1"; dalvik 0x12 0x10
#     terbaca const/4 v1, 0 — v0 TIDAK pernah di-set 1. Hasil:
#       - mode normal : v0 = objek NetworkInfo (non-null) → lolos ke init
#         (kebetulan benar — itulah kenapa login lokal jalan sejak v15);
#       - MODE PESAWAT: getActiveNetworkInfo() = NULL → v0 tetap NULL →
#         gerbang pc=001c if-eqz v0 TERAMBIL → DIALOG 'mengingat' TETAP MUNCUL.
#     (HWLoginActivity.onCreate: @5d242c [12 1c] niatnya "const/4 v12,1",
#      terbaca const/4 v1,-4 — move-result v12 hancur; v12 = objek NetworkInfo
#      non-null → dialog onCreate kebetulan tak terjangkau. Tapi gerbangnya
#      tetap ada — ditutup juga di v19 supaya TIDAK bergantung kebetulan.)
#
# PATCH v19 (2 unit BARU — kedua gerbang dialog → goto melewati dialog):
#   A) feedtonight.hoboismrelationbelow pc=001c @file 5e8180:
#      38 00 37 00 (if-eqz v0,+55 -> 0053 dialog)  ->  28 02 00 00
#      = goto +2 -> 001e ('begin init') ; nop  — DIALOG MATI, init jalan
#        di SEMUA kondisi jaringan (pesawat: init thread -> 127.0.0.1:9530,
#        terbukti log device seq 32-35).
#   B) HWLoginActivity.onCreate pc=0194 @file 5d2438:
#      38 0c 14 00 (if-eqz v12,+20 -> 01a8 dialog) ->  28 02 00 00
#      = goto +2 -> 0196 (bindService QuickGameSdkService — path asli mode
#        normal & path null original) ; nop  — DIALOG MATI permanen.
#
# PATCH warisan v18 (6 unit) — tetap dipertahankan:
#   A) com.quickgame.android.sdk.feedtonight / hoboismrelationbelow (SDK init):
#      @file 5e816c  0A->08 (if-eqz +10->+8)   @file 5e8174  0A 00->12 10
#   B) com.quickgame.android.sdk.activity.HWLoginActivity / onCreate:
#      @file 5d242c  0A 0C->12 1C   @file 5d2430  04->02 (if-eqz +4->+2)
#   C) com.zhuhuan.game.LaunchActivity.feedtonight (IsFilterIP HTTP ke host
#      asli — mati di akar, v18): @file 5fd70c  5480c6a8 -> 12000f00
#      = const/4 v0,0 ; return v0
#
# opcode dalvik: iget-object 0x54 | const/4 0x12 | return 0x0f | move-result
#                0x0a | if-eqz 0x38 | goto/8 0x28 | nop 0x00
# Setelah patch: adler32 (off 8) + SHA1 (off 12) header dex dihitung ulang.
import struct, sys, hashlib, zlib

IN  = sys.argv[1] if len(sys.argv) > 1 else '/tmp/v19/classes.dex'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/v19/dexout/classes.dex'

d = bytearray(open(IN, 'rb').read())
assert d[:4] == b'dex\n', 'bukan dex'
assert len(d) == 6697176, 'ukuran dex base tidak sesuai: %d' % len(d)
print('input %s — %d B' % (IN, len(d)))

def patch(off, before, after, note):
    cur = bytes(d[off:off + len(before)])
    assert cur == before, '@%06x byte kini %s != %s' % (off, cur.hex(), before.hex())
    d[off:off + len(after)] = after
    print('  @%06x %s -> %s  (%s)' % (off, before.hex(), after.hex(), note))

# --- warisan v18 (6 unit): SDK init + HWLoginActivity + IsFilterIP ---
patch(0x5e816c, bytes([0x0A]), bytes([0x08]), 'feedtonight.hoboismrelationbelow if-eqz +10->+8 (warisan v15)')
patch(0x5e8174, bytes([0x0A, 0x00]), bytes([0x12, 0x10]), 'feedtonight.hoboismrelationbelow move-result -> const/4 v1,0 (warisan v15)')
patch(0x5d242c, bytes([0x0A, 0x0C]), bytes([0x12, 0x1C]), 'HWLoginActivity.onCreate move-result v12 -> const/4 v1,-4 (warisan v15)')
patch(0x5d2430, bytes([0x04]), bytes([0x02]), 'HWLoginActivity.onCreate if-eqz +4->+2 (warisan v15)')
patch(0x5fd70c, bytes([0x54, 0x80, 0xC6, 0xA8]), bytes([0x12, 0x00, 0x0F, 0x00]),
      'LaunchActivity.feedtonight iget-object -> const/4 v0,0 ; return v0 (warisan v18, nol HTTP IsFilterIP)')
# --- BARU v19: gerbang dialog -> goto (4 unit) ---
patch(0x5e8180, bytes([0x38, 0x00, 0x37, 0x00]), bytes([0x28, 0x02, 0x00, 0x00]),
      'feedtonight.hoboismrelationbelow if-eqz v0,+55(dialog) -> goto +2 (init) ; nop — AKAR POPUP PESAWAT MATI')
patch(0x5d2438, bytes([0x38, 0x0C, 0x14, 0x00]), bytes([0x28, 0x02, 0x00, 0x00]),
      'HWLoginActivity.onCreate if-eqz v12,+20(dialog) -> goto +2 (bindService) ; nop — dialog mati permanen')

# ---- header dex: checksum adler32 + SHA1 dihitung ulang ----
d[12:32] = hashlib.sha1(d[32:]).digest()
d[8:12] = struct.pack('<I', zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)

open(OUT, 'wb').write(bytes(d))
got = hashlib.sha256(bytes(d)).hexdigest()
print('output %s — %d B (ukuran identik base: %s)' % (OUT, len(d), len(d) == 6697176))
print('sha256 classes.dex v19:', got)
