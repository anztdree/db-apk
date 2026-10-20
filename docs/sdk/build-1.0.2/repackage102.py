#!/usr/bin/env python3
"""Repackage basis 1.0.1 -> 1.0.2 (unsigned).

Patch (semua berjangkar bukti; lihat docs/sdk/RENCANA-GERBANG-LOGIN.md):
  1. assets/QHinfo  : http://qsdk.t4game.com -> http://127.0.0.1:28110
                      (penunjuk server SDK — sama seperti tes1 yang terbukti jalan)
  2. classes.dex    : TIDAK disentuh — injeksi MiniServer.start() sudah ada di 1.0.1
  3. classes2.dex   : MiniServer 1.0.2 (gerbang lokal + generator + re-sync versi)
  4. setting bin mirror : gerbang loginServer -> http://127.0.0.1 + loginWebPort 28110
  5. + mirror 1.0.2 : VERSION, generator.json, qsdk/* (statik live + template generator)
  6. buang signature META-INF lama (di-sign ulang uber-apk-signer)

Semua entri lain disalin apa adanya (compress_type dipertahankan).
"""
import hashlib
import os
import zipfile

BASE = "/tmp/DB-Local-1.0.1.apk"
OUT = "/home/z/my-project/work-re/build-102/out/DB-Local-1.0.2-unsigned.apk"
DEX2 = "/home/z/my-project/work-re/build-102/dexout/classes.dex"
MIRROR = "/home/z/my-project/work-re/build-102/mirror"
QH = b"http://127.0.0.1:28110"

REPLACED = {
    "assets/QHinfo": QH,
    "classes2.dex": open(DEX2, "rb").read(),
    "assets/mirror/bs/db/android/setting_BS_Android.bin":
        open(MIRROR + "/bs/db/android/setting_BS_Android.bin", "rb").read(),
}

# mirror 1.0.2 → path APK (HANYA entri yang belum ada di basis; file lama sudah
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
print("-- mirror 1.0.2 byte-exact terhadap staging ✓")
raw2 = zb.read("classes2.dex")
print("-- classes2.dex 1.0.2:", len(raw2), "B | loginchecknative:",
      b"loginchecknative" in raw2, "| DbGen:", b"DbGen" in raw2,
      "| generator.json:", b"generator.json" in raw2, "| VERSION:", b"VERSION" in raw2)
# setting bin di APK = gerbang lokal?
import sys
sys.path.insert(0, "/home/z/my-project/work-re/build-102")
data = zb.read("assets/mirror/bs/db/android/setting_BS_Android.bin")
key = b"DragonBall"
dec = bytes(b ^ key[i % len(key)] for i, b in enumerate(data)).decode("utf-8")
assert '"loginServer": "http://127.0.0.1"' in dec, "gerbang tidak lokal!"
assert '"loginWebPort": 28110' in dec, "port gerbang salah!"
print("-- gerbang di bin APK: loginServer=http://127.0.0.1 loginWebPort=28110 ✓")
# tidak ada identitas panen di mirror baru
PANEN_MARKS = [b"28486093", b"28506361", b"cz90406643", b"vifen44624", b"KdddFRi"]
bocor = []
for n in sorted(nb - na) + ["assets/mirror/bs/db/android/setting_BS_Android.bin"]:
    body = zb.read(n)
    for m in PANEN_MARKS:
        if m in body:
            bocor.append((n, m.decode()))
print("-- kebocoran identitas panen:", bocor if bocor else "TIDAK ADA (bersih) ✓")
sha = hashlib.sha256(open(OUT, "rb").read()).hexdigest()
print("\nSHA256 (unsigned):", sha)
