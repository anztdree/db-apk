#!/usr/bin/env python3
"""Repackage basis 1.0.1 -> 1.0.3 (unsigned).

Patch (semua berjangkar bukti; lihat docs/sdk/BUILD-1.0.3.md):
  1. assets/QHinfo  : http://127.0.0.1:28110 (sama seperti 1.0.2)
  2. classes.dex    : TIDAK disentuh — injeksi MiniServer.start() sudah ada di 1.0.1
  3. classes2.dex   : MiniServer 1.0.3 (gerbang lokal + generator + autoLogin)
  4. setting bin mirror : gerbang loginServer -> http://127.0.0.1 + loginWebPort 28110
  5. + mirror 1.0.3 : VERSION, generator.json, qsdk/* (statik live + template generator
                      + autoLogin.template + autoLogin.error.template)
  6. buang signature META-INF lama (di-sign ulang uber-apk-signer — kunci bawaan
     signer = AOSP debug key, IDENTIK 1.0.1/1.0.2 → install-over aman)

Semua entri lain disalin apa adanya (compress_type dipertahankan).
"""
import hashlib
import os
import zipfile

BASE = "/tmp/DB-Local-1.0.1.apk"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out", "DB-Local-1.0.3-unsigned.apk")
DEX2 = os.path.join(HERE, "dexout", "classes.dex")
MIRROR = os.path.join(HERE, "mirror")
QH = b"http://127.0.0.1:28110"

REPLACED = {
    "assets/QHinfo": QH,
    "classes2.dex": open(DEX2, "rb").read(),
    "assets/mirror/bs/db/android/setting_BS_Android.bin":
        open(MIRROR + "/bs/db/android/setting_BS_Android.bin", "rb").read(),
}

# mirror 1.0.3 → path APK (HANYA entri yang belum ada di basis; file lama sudah
# disalin verbatim oleh loop utama — mencegah entri ZIP duplikat)
with zipfile.ZipFile(BASE) as _z:
    _base_names = set(_z.namelist())
ADDED = {}
for root, _, files in os.walk(MIRROR):
    for f in files:
        p = os.path.join(root, f)
        rel = "assets/mirror/" + os.path.relpath(p, MIRROR)
        if rel in REPLACED or rel in _base_names:
            continue
        ADDED[rel] = p


def is_old_sig(name):
    if not name.startswith("META-INF/"):
        return False
    up = name.upper()
    return up.endswith((".SF", ".RSA", ".DSA", ".EC")) or up.endswith("MANIFEST.MF")


zin = zipfile.ZipFile(BASE)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
zout = zipfile.ZipFile(OUT, "w")
copied = replaced = dropped = 0
for item in zin.infolist():
    name = item.filename
    if is_old_sig(name):
        dropped += 1
        continue
    if name in REPLACED:
        zout.writestr(item, REPLACED[name])
        replaced += 1
    else:
        zout.writestr(item, zin.read(name))
        copied += 1
for name, path in sorted(ADDED.items()):
    zi = zipfile.ZipInfo(name, date_time=(2026, 10, 20, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    zout.writestr(zi, open(path, "rb").read())
zout.close()
zin.close()
print(f"disalin={copied} diganti={replaced} sig-lama-dibuang={dropped} mirror-ditambah={len(ADDED)}")
print("UKURAN:", os.path.getsize(OUT), "bytes")

# ================= VERIFIKASI =================
za = zipfile.ZipFile(BASE)
zb = zipfile.ZipFile(OUT)
na = set(za.namelist())
nb = set(zb.namelist())
print("\n-- entri baru (%d):" % len(nb - na))
for n in sorted(nb - na):
    print("   +", n)
print("-- entri dibuang (%d):" % len(na - nb))
for n in sorted(na - nb):
    print("   -", n)
print("-- isi berubah:")
for n in sorted(na & nb):
    da, dbb = za.read(n), zb.read(n)
    if da != dbb:
        print(f"   * {n}: {len(da)} -> {len(dbb)} B")
        if n == "assets/QHinfo":
            assert dbb == QH, "QHinfo salah!"
            print("     QHinfo =", dbb.decode())
changed_mirror = [
    n for n in (na & nb)
    if n.startswith("assets/mirror/")
    and not n.startswith("assets/mirror/qsdk/")
    and n != "assets/mirror/bs/db/android/setting_BS_Android.bin"
    and za.read(n) != zb.read(n)
]
print("-- mirror lama berubah (di luar setting bin):",
      changed_mirror if changed_mirror else "TIDAK ADA (identik byte)")
for n, p in ADDED.items():
    assert zb.read(n) == open(p, "rb").read(), f"mirror mismatch: {n}"
print("-- mirror 1.0.3 byte-exact terhadap staging ✓")
raw2 = zb.read("classes2.dex")
print("-- classes2.dex 1.0.3:", len(raw2), "B | loginchecknative:",
      b"loginchecknative" in raw2, "| DbGen:", b"DbGen" in raw2,
      "| autoLogin:", b"autoLogin" in raw2,
      "| lastAuthToken:", b"lastAuthToken" in raw2,
      "| VERSION:", b"VERSION" in b"VERSION" and b"1.0.3" in zb.read("assets/mirror/VERSION"))
assert zb.read("assets/mirror/VERSION") == b"1.0.3", "VERSION mirror salah!"
assert b"autoLogin" in raw2, "rute autoLogin tidak ada di dex!"
assert zb.read("assets/mirror/qsdk/v1/user/autoLogin.template"), "autoLogin.template hilang!"
assert zb.read("assets/mirror/qsdk/v1/user/autoLogin.error.template"), "autoLogin.error.template hilang!"
# setting bin di APK = gerbang lokal?
import sys
data = zb.read("assets/mirror/bs/db/android/setting_BS_Android.bin")
key = b"DragonBall"
dec = bytes(b ^ key[i % len(key)] for i, b in enumerate(data)).decode("utf-8")
assert '"loginServer": "http://127.0.0.1"' in dec, "gerbang tidak lokal!"
assert '"loginWebPort": 28110' in dec, "port gerbang salah!"
print("-- gerbang di bin APK: loginServer=http://127.0.0.1 loginWebPort=28110 ✓")
# tidak ada identitas panen di entri baru
PANEN_MARKS = [b"28486093", b"28506361", b"cz90406643", b"vifen44624", b"KdddFRi",
               b"e963968f", b"15ea6e27"]  # + token device dari HAR (tidak dibekukan)
bocor = []
for n in sorted(nb - na) + ["assets/mirror/bs/db/android/setting_BS_Android.bin",
                            "classes2.dex"]:
    body = zb.read(n)
    for m in PANEN_MARKS:
        if m in body:
            bocor.append((n, m.decode()))
print("-- kebocoran identitas panen/device:", bocor if bocor else "TIDAK ADA (bersih) ✓")
assert not bocor, "ADA KEBOCORAN!"
sha = hashlib.sha256(open(OUT, "rb").read()).hexdigest()
print("\nSHA256 (unsigned):", sha)
