#!/usr/bin/env python3
"""Staging mirror 1.0.2 — SEMUA bahan dari bukti repo (bukan karangan).

Sumber:
  repo-102/docs/sdk/bukti/panen-live/          → statik config boot (server hidup asli)
  repo-102/docs/sdk/bukti/panen-gerbang4/      → sesi live (acuan bentuk template)
  repo-102/docs/sdk/config-local/              → template registerVisitor + loginchecknative
  /tmp/DB-Local-1.0.1.apk                      → setting bin mirror (diedit gerbang lokal)

Keluaran: /home/z/my-project/work-re/build-102/mirror/
"""
import json
import os
import shutil

REPO = "/home/z/my-project/work-re/repo-102/docs/sdk"
PANEN_LIVE = REPO + "/bukti/panen-live"
PANEN_G4 = REPO + "/bukti/panen-gerbang4"
CFG = REPO + "/config-local"
MIRROR = "/home/z/my-project/work-re/build-102/mirror"
APK101 = "/tmp/DB-Local-1.0.1.apk"
XOR_KEY = b"DragonBall"

# (sumber, tujuan relatif mirror) — STATIK LIVE byte-exact, tanpa identitas panen
STATIK = [
    (PANEN_LIVE + "/system__init.bin", "qsdk/v1/system/init"),
    (PANEN_LIVE + "/system__getNotice.bin", "qsdk/v1/system/getNotice"),
    (PANEN_LIVE + "/system__getAgreement.bin", "qsdk/v1/system/getAgreement"),
    (PANEN_LIVE + "/system__onNewToken.bin", "qsdk/v1/system/onNewToken"),
    (PANEN_LIVE + "/system__dmsg.bin", "qsdk/v1/system/dmsg"),
    (PANEN_LIVE + "/user__ckRegVistor.bin", "qsdk/v1/user/ckRegVistor"),
    (PANEN_LIVE + "/user__getServerAgreement.bin", "qsdk/v1/user/getServerAgreement"),
    # TEMPLATE (bentuk live, identitas → placeholder — diisi DbGen saat melayani)
    (CFG + "/user/registerVisitor.json", "qsdk/v1/user/registerVisitor.template"),
    (CFG + "/game/loginchecknative.json", "qsdk/game/loginchecknative.template"),
]

# Template yg dibangun dari live bytes: token/identitas panen DIGANTI placeholder
TEMPLATE_DARI_LIVE = [
    (PANEN_G4 + "/sukses-sekali__user__registerUser.bin",
     "qsdk/v1/user/registerUser.template",
     {"isNewUser": 1}),
    (PANEN_G4 + "/live__user__loginByName.bin",
     "qsdk/v1/user/loginByName.template",
     None),
    (PANEN_G4 + "/live__auth__getUserInfo-guest.bin",
     "qsdk/v1/auth/getUserInfo.template",
     None),
]


def xor_crypt(data: bytes) -> bytes:
    return bytes(b ^ XOR_KEY[i % len(XOR_KEY)] for i, b in enumerate(data))


def templatekan_sesi(raw: bytes) -> bytes:
    """Ganti identitas panen dgn placeholder (HANYA nilai, bentuk tak diubah)."""
    d = json.loads(raw.decode("utf-8"))
    data = d["data"]
    data["authToken"] = "{{AUTH_TOKEN}}"
    ud = data["userData"]
    ud["uid"] = "{{UID}}"
    ud["username"] = "{{USERNAME}}"
    ud["token"] = "{{TOKEN}}"
    bi = ud.get("bindInfo") or {}
    for _, b in bi.items():
        if isinstance(b, dict) and b.get("isBind") == 1:
            # identitas binding ikut placeholder (mis. bindEmail utk register/login)
            b["otherAccountName"] = "{{USERNAME}}"
            b["buid"] = "{{UID}}"
    return (json.dumps(d, ensure_ascii=False, separators=(",", ":"))).encode("utf-8")


def templatekan_getuserinfo(raw: bytes) -> bytes:
    d = json.loads(raw.decode("utf-8"))
    d["data"]["nickName"] = "{{USERNAME}}"
    return (json.dumps(d, ensure_ascii=False, separators=(",", ":"))).encode("utf-8")


def main():
    # bersihkan staging
    if os.path.exists(MIRROR):
        shutil.rmtree(MIRROR)
    os.makedirs(MIRROR)

    # 1) statik live + template siap-repo
    for src, dst in STATIK:
        dstp = os.path.join(MIRROR, dst)
        os.makedirs(os.path.dirname(dstp), exist_ok=True)
        shutil.copyfile(src, dstp)
        print(f"  {dst}  ({os.path.getsize(dstp)} B) ← {os.path.basename(src)}")

    # 2) template dari live bytes
    for src, dst, extra in TEMPLATE_DARI_LIVE:
        raw = open(src, "rb").read()
        if dst.endswith("getUserInfo.template"):
            out = templatekan_getuserinfo(raw)
        else:
            out = templatekan_sesi(raw)
        dstp = os.path.join(MIRROR, dst)
        os.makedirs(os.path.dirname(dstp), exist_ok=True)
        open(dstp, "wb").write(out)
        print(f"  {dst}  ({len(out)} B) ← {os.path.basename(src)} [template-kan]")

    # 3) setting bin: gerbang loginserver → LOKAL (murni edit config, XOR DragonBall)
    import zipfile
    with zipfile.ZipFile(APK101) as z:
        bin_cur = z.read("assets/mirror/bs/db/android/setting_BS_Android.bin")
    dec = xor_crypt(bin_cur).decode("utf-8")
    old_ls = '"loginServer": "https://login.popoh5.com"'
    old_wp = '"loginWebPort": 510'
    assert old_ls in dec and old_wp in dec, "format bin tak terduga!"
    dec = dec.replace(old_ls, '"loginServer": "http://127.0.0.1"')
    dec = dec.replace(old_wp, '"loginWebPort": 28110')
    dstp = os.path.join(MIRROR, "bs/db/android/setting_BS_Android.bin")
    os.makedirs(os.path.dirname(dstp), exist_ok=True)
    open(dstp, "wb").write(xor_crypt(dec.encode("utf-8")))
    print(f"  bs/db/android/setting_BS_Android.bin  ({len(xor_crypt(dec.encode('utf-8')))} B) "
          f"[gerbang → http://127.0.0.1:28110]")

    # 4) VERSION + generator.json (aturan generate — bisa diedit user)
    open(os.path.join(MIRROR, "VERSION"), "w").write("1.0.2")
    open(os.path.join(MIRROR, "generator.json"), "w").write(
        '{\n  "uid": "",\n  "username": "",\n  "salt": "dblocal-102"\n}\n')
    print("  VERSION = 1.0.2")
    print("  generator.json (uid/username kosong = auto-seed; salt utk sign)")

    # 5) salin file mirror LAMA 1.0.1 yang tak diubah (22 file: bs/js, upgrade, manifest,
    #    index-native) — byte-identik
    with zipfile.ZipFile(APK101) as z:
        for n in z.namelist():
            if n.startswith("assets/mirror/") and not n.startswith("assets/mirror/qsdk/"):
                rel = n[len("assets/mirror/"):]
                if rel == "bs/db/android/setting_BS_Android.bin":
                    continue  # sudah diganti
                dstp = os.path.join(MIRROR, rel)
                os.makedirs(os.path.dirname(dstp), exist_ok=True)
                open(dstp, "wb").write(z.read(n))
        print("  + 22 file mirror lama 1.0.1 (byte-identik)")

    print("\nIsi mirror 1.0.2:")
    for root, _, files in sorted(os.walk(MIRROR)):
        for f in sorted(files):
            p = os.path.join(root, f)
            print(f"  {os.path.relpath(p, MIRROR)}  ({os.path.getsize(p)} B)")


if __name__ == "__main__":
    main()
