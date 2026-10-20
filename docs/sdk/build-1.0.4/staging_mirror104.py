#!/usr/bin/env python3
"""Staging mirror 1.0.4 — SEMUA bahan dari bukti repo (bukan karangan).

Delta vs 1.0.3 (keberatan user: "masuk ranah database !! password cocok
login !!"):
  + qsdk/v1/user/error.template ← panen error LIVE 40008
                                  (panen-gerbang4/live__user__registerUser.bin)
                                  dgn id/message → {{ERROR_ID}}/{{ERROR_MESSAGE}}
                                  (BENTUK live; ISI = kebijakan lokal —
                                  tidak ada materi live error login/register)
  ~ VERSION                     = 1.0.4 (pemicu re-sync penuh)
  ~ generator.json              + lastUid (sesi akun antar boot)
  ~ selain itu: byte-identik salinan mirror 1.0.3 ter-commit (provenance satu
    tempat: autoLogin.template, autoLogin.error.template, statik live,
    setting bin gerbang lokal, bs/js, upgrade, manifest, index-native)

Keluaran: docs/sdk/build-1.0.4/mirror/  (di dalam repo)
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE)) + "/sdk"
PANEN_G4 = os.path.join(REPO, "bukti/panen-gerbang4")
MIRROR103 = os.path.join(REPO, "build-1.0.3/mirror")
MIRROR = os.path.join(HERE, "mirror")


def templatekan_error(raw: bytes) -> bytes:
    """BENTUK error live 40008 → placeholder id/message (isi = kebijakan lokal)."""
    d = json.loads(raw.decode("utf-8"))
    assert d["result"] is False and d["data"] == [], "bentuk error live berubah!"
    assert d["error"]["id"] == 40008, "id error live tak terduga!"
    d["error"]["id"] = "{{ERROR_ID}}"
    d["error"]["message"] = "{{ERROR_MESSAGE}}"
    return (json.dumps(d, ensure_ascii=False, separators=(",", ":"))).encode("utf-8")


def main():
    # 1) salin mirror 1.0.3 ter-commit (byte-exact)
    if os.path.exists(MIRROR):
        shutil.rmtree(MIRROR)
    shutil.copytree(MIRROR103, MIRROR)
    n0 = sum(len(f) for _, _, f in os.walk(MIRROR))
    print(f"  salin mirror 1.0.3 → {n0} file (byte-exact)")

    # 2) BARU 1.0.4: error.template ← panen error LIVE 40008
    err_raw = open(os.path.join(PANEN_G4, "live__user__registerUser.bin"), "rb").read()
    err = templatekan_error(err_raw)
    dstp = os.path.join(MIRROR, "qsdk/v1/user/error.template")
    open(dstp, "wb").write(err)
    # bentuk identik dgn autoLogin.error.template (sama-sama 40008 live)
    al_err = open(os.path.join(MIRROR, "qsdk/v1/user/autoLogin.error.template"), "rb").read()
    assert err.replace(b"{{ERROR_ID}}", b"X").replace(b"{{ERROR_MESSAGE}}", b"Y") == \
        al_err.replace(b"{{ERROR_ID}}", b"X").replace(b"{{ERROR_MESSAGE}}", b"Y"), \
        "bentuk error.template ≠ autoLogin.error.template!"
    print(f"  qsdk/v1/user/error.template  ({len(err)} B) "
          f"← live__user__registerUser.bin [BENTUK error live; isi = kebijakan lokal]")

    # 3) VERSION + generator.json (+ lastUid)
    open(os.path.join(MIRROR, "VERSION"), "w").write("1.0.4")
    open(os.path.join(MIRROR, "generator.json"), "w").write(
        '{\n  "uid": "",\n  "username": "",\n  "salt": "dblocal-102",\n'
        '  "lastAuthToken": "",\n  "lastUid": "",\n  "lastUsername": "",\n'
        '  "lastIsGuest": 1\n}\n')
    print("  VERSION = 1.0.4")
    print("  generator.json (+ lastUid)")

    # 4) VERIFIKASI
    assert open(os.path.join(MIRROR, "qsdk/v1/user/autoLogin.template"), "rb").read(), \
        "autoLogin.template hilang!"
    assert open(os.path.join(MIRROR, "qsdk/v1/user/registerUser.template"), "rb").read(), \
        "registerUser.template hilang!"
    assert open(os.path.join(MIRROR, "qsdk/v1/user/loginByName.template"), "rb").read(), \
        "loginByName.template hilang!"
    data = open(os.path.join(MIRROR, "bs/db/android/setting_BS_Android.bin"), "rb").read()
    key = b"DragonBall"
    dec = bytes(b ^ key[i % len(key)] for i, b in enumerate(data)).decode("utf-8")
    assert '"loginServer": "http://127.0.0.1"' in dec and '"loginWebPort": 28110' in dec, \
        "gerbang tidak lokal!"
    print("  setting bin: gerbang → http://127.0.0.1:28110 ✓")

    print("\nIsi mirror 1.0.4:")
    total = 0
    for root, _, files in sorted(os.walk(MIRROR)):
        for f in sorted(files):
            p = os.path.join(root, f)
            print(f"  {os.path.relpath(p, MIRROR)}  ({os.path.getsize(p)} B)")
            total += 1
    print(f"TOTAL: {total} file")


if __name__ == "__main__":
    main()
