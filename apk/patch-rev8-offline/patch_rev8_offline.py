#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
patch_rev8_offline.py — REVISI 8-OFFLINE (mode pesawat) atas APK 1.0.3 revN
=============================================================================

AKAR MASALAH (bedah dex, bukti docs/offline/doc.md):
  QuickGame SDK memakai 2 "gerbang jaringan" yang menolak lanjut saat
  ConnectivityManager.getActiveNetworkInfo() null/disconnected — padahal
  seluruh server lokal (28110/28112/28113/28114/28115) hidup di 127.0.0.1
  dan loopback TIDAK terpengaruh mode pesawat:

  1) com.quickgame.android.sdk.feedtonight;->hoboismrelationbelow()
     = gerbang SDK init. Tanpa jaringan → dialog "jaringan tidak tersedia",
     'begin init' TIDAK PERNAH dijalankan → seluruh SDK mati.
  2) com.quickgame.android.sdk.activity.HWLoginActivity;->onCreate()
     = gerbang layar login. Tanpa jaringan → dialog, bindService ke
     QuickGameSdkService TIDAK PERNAH dipanggil → login mati.

  PATCH = 2 byte (const/4 <reg>, 0 → const/4 <reg>, 1) di classes.dex,
  ukuran dex TIDAK berubah, diikuti perbaikan header dex (checksum adler32
  + sha1). Semua entry lain APK byte-identik.

SYARAT:
  - input classes.dex sha256 = e45b3044ac83be9a0d08831408ed8934160a227a63ab03df3c47bf5da1c79e60
    (classes.dex yang tak pernah disentuh sejak release 1.0.1 — berlaku utk
    semua build 1.0.2 / 1.0.3 rev1..rev7)
  - java + build/uber-apk-signer.jar utk zipalign+sign (opsional: --no-sign)

HASIL:
  DB-Local-1.0.3-rev8-offline.apk
  - delta vs rev7: classes.dex (2 byte) + 3 META-INF (re-sign)
  - sertifikat = debug uber-apk-signer (SHA256 1e08a903…) — SAMA dgn rev7
    → `adb install -r` tanpa uninstall (DB akun guest ikut).

Pemakaian:
  python3 patch_rev8_offline.py DB-Local-1.0.3-rev7-debug.apk [output.apk] [--no-sign]
"""
import sys, os, zlib, hashlib, zipfile, subprocess, shutil

EXPECTED_CLASSES_SHA256 = "e45b3044ac83be9a0d08831408ed8934160a227a63ab03df3c47bf5da1c79e60"

# (nama, code_item_off, off instruksi dari awal insns, byte literal lama, byte literal baru)
# instruksi const/4 (format 11n) = 2 byte: [0x12, A|B<<4]; byte ke-2 = register|literal<<4
SITES = [
    ("feedtonight.hoboismrelationbelow (gerbang SDK init)", 0x5E8128, 0x0036, 0x00, 0x10),
    ("HWLoginActivity.onCreate (gerbang layar login)",      0x5D20F0, 0x0326, 0x0C, 0x1C),
]

def patch_classes_dex(data: bytes):
    buf = bytearray(data)
    h = hashlib.sha256(bytes(buf)).hexdigest()
    if h != EXPECTED_CLASSES_SHA256:
        raise SystemExit("FATAL: classes.dex sha256 %s != %s (bukan basis yang diharapkan)" % (h, EXPECTED_CLASSES_SHA256))
    for name, code_off, rel, old, new in SITES:
        op_fo = code_off + 0x10 + rel      # byte opcode const/4 (0x12)
        lit_fo = op_fo + 1                 # byte operand: register(A nibble rendah) | literal(B nibble tinggi)
        if buf[op_fo] != 0x12 or buf[lit_fo] != old:
            raise SystemExit("FATAL: situs %s tidak cocok (byte %02x %02x, harapan 12 %02x)" % (name, buf[op_fo], buf[lit_fo], old))
        buf[lit_fo] = new
        print("[PATCH] %-46s file_off=%#x const/4 %02x → %02x (literal 0→1)" % (name, lit_fo, old, new))
    # header dex: sha1 @12..32 (atas bytes 32..), checksum adler32 @8 (atas bytes 32..)
    body = bytes(buf[32:])
    buf[12:32] = hashlib.sha1(body).digest()
    buf[8:12] = (zlib.adler32(body) & 0xFFFFFFFF).to_bytes(4, "little")
    out = bytes(buf)
    assert len(out) == len(data), "ukuran dex berubah!"
    print("[DEX] sha256 %s → %s (ukuran %d B tetap)" % (h[:16], hashlib.sha256(out).hexdigest()[:16], len(out)))
    return out

def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].startswith("--") else "DB-Local-1.0.3-rev8-offline.apk"
    do_sign = "--no-sign" not in sys.argv

    staging = "_staging_" + os.path.basename(out) if out.endswith(".apk") else "_staging_tmp.apk"
    newdex = None
    zin = zipfile.ZipFile(src, "r")
    zout = zipfile.ZipFile(staging, "w", zipfile.ZIP_DEFLATED)
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == "classes.dex":
            newdex = patch_classes_dex(data)
            data = newdex
        zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
        zi.compress_type = item.compress_type
        zi.external_attr = item.external_attr
        zi.internal_attr = item.internal_attr
        zi.create_system = item.create_system
        zout.writestr(zi, data)
    zout.close(); zin.close()

    # audit delta
    za, zb = zipfile.ZipFile(src), zipfile.ZipFile(staging)
    diff = sorted(n for n in za.namelist() if za.read(n) != zb.read(n))
    print("[AUDIT] entry berubah:", diff)
    assert set(diff) <= {"classes.dex", "META-INF/MANIFEST.MF", "META-INF/ANDROIDD.SF", "META-INF/ANDROIDD.RSA"}, diff
    za.close(); zb.close()

    if not do_sign:
        shutil.move(staging, out)
        print("[DONE] %s (belum ditandatangani)" % out)
        return

    signer = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uber-apk-signer.jar")
    if not os.path.exists(signer):
        signer = "uber-apk-signer.jar"
    tmpdir = "_signed_out"
    r = subprocess.run(["java", "-jar", signer, "--apks", staging, "--out", tmpdir, "--allowResign"],
                       capture_output=True, text=True)
    sys.stdout.write(r.stdout[-2500:])
    if r.returncode != 0:
        sys.stderr.write(r.stderr[-2000:]); sys.exit(1)
    cand = [f for f in os.listdir(tmpdir) if f.endswith("-aligned-debugSigned.apk")]
    assert len(cand) == 1, cand
    shutil.move(os.path.join(tmpdir, cand[0]), out)
    shutil.rmtree(tmpdir, ignore_errors=True)
    os.remove(staging)
    data = open(out, "rb").read()
    print("[DONE] %s — %d B — sha256 %s" % (out, len(data), hashlib.sha256(data).hexdigest()))

if __name__ == "__main__":
    main()
