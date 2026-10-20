#!/usr/bin/env python3
"""Staging mirror 1.0.3 — SEMUA bahan dari bukti repo (bukan karangan).

Delta vs 1.0.2 (bug autoLogin dari HAR perangkat, log/sdk.har @ 5a580c3):
  + qsdk/v1/user/autoLogin.template       ← registerVisitor.template (config-local)
                                            dengan "isGuest" → {{IS_GUEST}}
                                            (bentuk live; jenis sesi diisi DbGen)
  + qsdk/v1/user/autoLogin.error.template ← panen error LIVE 40008
                                            (panen-gerbang4/live__user__registerUser.bin)
                                            dgn id/message → {{ERROR_ID}}/{{ERROR_MESSAGE}}
                                            (BENTUK live; ISI = kebijakan lokal —
                                            TIDAK ada materi live error autoLogin)
  ~ VERSION                               = 1.0.3 (pemicu re-sync penuh)
  ~ generator.json                        + lastAuthToken/lastUsername/lastIsGuest

Sumber:
  repo-102/docs/sdk/bukti/panen-live/          → statik config boot (server hidup asli)
  repo-102/docs/sdk/bukti/panen-gerbang4/      → sesi live (acuan bentuk template)
  repo-102/docs/sdk/config-local/              → template registerVisitor + loginchecknative
  /tmp/DB-Local-1.0.1.apk                      → setting bin mirror (diedit gerbang lokal)

Keluaran: docs/sdk/build-1.0.3/mirror/  (di dalam repo — provenance satu tempat)
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE)) + "/sdk"
PANEN_LIVE = os.path.join(REPO, "bukti/panen-live")
PANEN_G4 = os.path.join(REPO, "bukti/panen-gerbang4")
CFG = os.path.join(REPO, "config-local")
MIRROR = os.path.join(HERE, "mirror")
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


def templatekan_error(raw: bytes) -> bytes:
    """BENTUK error live 40008 → placeholder id/message (isi = kebijakan lokal)."""
    d = json.loads(raw.decode("utf-8"))
    assert d["result"] is False and d["data"] == [], "bentuk error live berubah!"
    d["error"]["id"] = "{{ERROR_ID}}"
    d["error"]["message"] = "{{ERROR_MESSAGE}}"
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

    # 3) BARU 1.0.3: autoLogin.template ← registerVisitor.template (config-local)
    #    dengan isGuest → {{IS_GUEST}} (bentuk live; jenis sesi diisi DbGen)
    rv = open(os.path.join(CFG, "user/registerVisitor.json"), "rb").read().decode("utf-8")
    assert '"isGuest": 1' in rv, "registerVisitor.template tidak berbentuk harapan!"
    auto = rv.replace('"isGuest": 1,', '"isGuest": {{IS_GUEST}},')
    assert "{{IS_GUEST}}" in auto, "parameterisasi isGuest gagal"
    dstp = os.path.join(MIRROR, "qsdk/v1/user/autoLogin.template")
    os.makedirs(os.path.dirname(dstp), exist_ok=True)
    open(dstp, "wb").write(auto.encode("utf-8"))
    print(f"  qsdk/v1/user/autoLogin.template  ({len(auto)} B) "
          f"← registerVisitor.json [isGuest → {{{{IS_GUEST}}}}]")

    # 4) BARU 1.0.3: autoLogin.error.template ← panen error LIVE 40008
    err_raw = open(os.path.join(PANEN_G4, "live__user__registerUser.bin"), "rb").read()
    err = templatekan_error(err_raw)
    dstp = os.path.join(MIRROR, "qsdk/v1/user/autoLogin.error.template")
    open(dstp, "wb").write(err)
    print(f"  qsdk/v1/user/autoLogin.error.template  ({len(err)} B) "
          f"← live__user__registerUser.bin [BENTUK error live; isi = kebijakan lokal]")

    # 5) setting bin: gerbang loginserver → LOKAL (murni edit config, XOR DragonBall)
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
    print(f"  bs/db/android/setting_BS_Android.bin  "
          f"[gerbang → http://127.0.0.1:28110]")

    # 6) VERSION + generator.json (1.0.3: + state sesi terakhir)
    open(os.path.join(MIRROR, "VERSION"), "w").write("1.0.3")
    open(os.path.join(MIRROR, "generator.json"), "w").write(
        '{\n  "uid": "",\n  "username": "",\n  "salt": "dblocal-102",\n'
        '  "lastAuthToken": "",\n  "lastUsername": "",\n  "lastIsGuest": 1\n}\n')
    print("  VERSION = 1.0.3")
    print("  generator.json (+ lastAuthToken/lastUsername/lastIsGuest)")

    # 7) salin file mirror LAMA 1.0.1 yang tak diubah (bs/js, upgrade, manifest,
    #    index-native) — byte-identik
    n_legacy = 0
    with zipfile.ZipFile(APK101) as z:
        for n in z.namelist():
            if n.startswith("assets/mirror/") and not n.startswith("assets/mirror/qsdk/"):
                rel = n[len("assets/mirror/"):]
                if rel == "bs/db/android/setting_BS_Android.bin":
                    continue  # sudah diganti
                dstp = os.path.join(MIRROR, rel)
                os.makedirs(os.path.dirname(dstp), exist_ok=True)
                open(dstp, "wb").write(z.read(n))
                n_legacy += 1
    print(f"  + {n_legacy} file mirror lama 1.0.1 (byte-identik)")

    print("\nIsi mirror 1.0.3:")
    total = 0
    for root, _, files in sorted(os.walk(MIRROR)):
        for f in sorted(files):
            p = os.path.join(root, f)
            print(f"  {os.path.relpath(p, MIRROR)}  ({os.path.getsize(p)} B)")
            total += 1
    print(f"TOTAL: {total} file")


if __name__ == "__main__":
    main()
