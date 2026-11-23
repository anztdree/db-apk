#!/usr/bin/env python3
# Smoke desktop v17 (Bagian 32) — jawaban laporan device 23 Nov sore:
#  "ituh log anda gk rapi susah di baca ... teks kaya tapi gak efisien !!!"
#  + "setelah sdk selesai LOADING force close"
#  1. DASHBOARD v7 = TABEL sungguhan (satu baris per peristiwa, header sticky,
#     chip filter tahap, Salin tabel = kolom sejajar lebar tetap).
#  2. CRASH CAPTURE (CrashHook): semua crash Java proses game (termasuk kode
#     SDK/game asli) terekam logs/crash-*.txt + banner merah dashboard (GET
#     /crash). Diuji dgn laporan crash sintetis.
#  3. FIX IZIN OVERLAY (OverlayFixer): dialog auto-login SDK = window
#     TYPE_APPLICATION_OVERLAY (2008) tanpa cek izin (bukti smali d/e.smali)
#     = kandidat utama crash "setelah loading". Cek canDrawOverlays di boot +
#     banner + tombol 1-klik BUKA SETTINGS (POST /fix/overlay).
#  (uji v14-v16 tetap — 69 kasus lama harus tetap PASS)
# Bagian 39 — logger v3 (arahan owner 23 Nov malam):
#  4. REDESIGN total dashboard: 🐉 DB MONITOR v3 — teks jelas, tata letak
#     terstruktur & rapi, emoji stylish (chip layanan, badge tahap, verdict
#     ✅❌⚠️ℹ️⚙️, tombol & banner beremoji).
#  5. IZIN OVERLAY = INFO, BUKAN error (arahan owner: game asli tidak
#     pernah minta overlay; SDK tetap berjalan normal): verdict ℹ️ INFO,
#     eksklusi eksplisit di isProblem, banner merah overlay dihapus,
#     teks OverlayFixer/SessionReset dinetralkan.
import base64, hashlib, json, os, shutil, signal, subprocess, sys, time, urllib.request, urllib.error, urllib.parse
import re

SECRET = "0b2a18e45d7df321"
BASE = "http://127.0.0.1:9530"
ROOT = "/tmp/v15root"
WS = __file__[:__file__.rfind("/")] + "/.."   # rebuild-src/
BUILD = WS + "/build-smoke"
DOCROOT = "/tmp/v15doc"   # smoke tidak uji CDN — folder kosong cukup

# profile.json default v15 SEBELUM upgrade (harus PERSIS dgn V15_PROFILE di
# SdkConfig.java — trim-compare)
V15_PROFILE = """{
 "poweredBy": "PHP/5.4.37",
 "contentType": "text/json",
 "alwaysHttp200": true,
 "verifySign": true,
 "signSecret": "0b2a18e45d7df321",
 "products": ["會員帳戶註冊"],
 "note": "profil kawat server asli qsdk.t4game.com (rekam live 23 Nov 2026). products = daftar productCode yang dianggap ada; client mengirim default 會員帳戶註冊 (hw_tomato_register). verifySign=false = matikan cek sign."
}"""
PRODUCT_ASLI = "75880787318053043365515388491588"


def sign_of(p):
    s = ""
    for k in sorted(p): s += k + "=" + ("" if p[k] is None else str(p[k])) + "&"
    return hashlib.md5((s + SECRET).encode()).hexdigest()

def sdk_post(path, params=None, sign=None, data_raw=None):
    if data_raw is None:
        js = json.dumps(params or {}, separators=(",", ":"), ensure_ascii=False)
        data_raw = base64.b64encode(js.encode()).decode()
    s = sign if sign is not None else (sign_of(params) if params is not None else "")
    body = "sv=v2&data=" + urllib.parse.quote(data_raw, safe="") + "&sign=" + urllib.parse.quote(s, safe="")
    req = urllib.request.Request(BASE + path, data=body.encode(), method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    with urllib.request.urlopen(req, timeout=10) as r:
        return r.status, dict(r.headers), r.read().decode("utf-8", "replace")

def form_post(path, fields):
    body = urllib.parse.urlencode(fields).encode()
    req = urllib.request.Request("http://127.0.0.1:4000" + path, data=body, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, dict(r.headers), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace")

def get400(path):
    with urllib.request.urlopen("http://127.0.0.1:4000" + path, timeout=10) as r:
        return r.status, dict(r.headers), r.read().decode("utf-8", "replace")

def get(path):
    with urllib.request.urlopen(BASE + path, timeout=10) as r:
        return r.status, dict(r.headers), r.read().decode("utf-8", "replace")

def must_params(email=None, token=None, product="會員帳戶註冊", **extra):
    p = {
        "sdkVersion": 223, "gameVersion": 1, "platform": 1,
        "productCode": product, "channelCode": "", "clientLang": "id",
        "suggestCurrency": "", "time": int(time.time() * 1000),
        "authToken": token or hashlib.md5(str(time.time_ns()).encode()).hexdigest(),
        "deviceId": "0123456789abcdef0123456789abcdef", "serialNum": "",
        "devIDShort": "a53", "osName": "android", "osVersion": 30,
    }
    if email: p["username"] = email
    p.update(extra)
    return p

PASS, FAIL = [], []
def check(name, cond, info=""):
    (PASS if cond else FAIL).append(name)
    print(("PASS " if cond else "FAIL ") + name + (("  | " + info) if info and not cond else ""))

def envelope(id_, msg):
    return {"result": False, "data": [], "error": {"id": id_, "message": msg}}

# ================= setup =================
shutil.rmtree(ROOT, ignore_errors=True)
os.makedirs(ROOT + "/data", exist_ok=True)
os.makedirs(DOCROOT, exist_ok=True)
# v3 FIX STUCK: isi CDN persis assets/server_root/bs/... — clientversion.json
# (bunuh popup 404 "Server clientversion is Not Exist") + serversetting.json
# loginserver → 9528 (origin halaman — satu-satunya port yang terbukti dicapai
# runtime XHR Egret di log device)
os.makedirs(DOCROOT + "/bs/resource/properties", exist_ok=True)
open(DOCROOT + "/bs/resource/properties/clientversion.json", "w").write(
    '{\n\t"clientVersion": "202109150935"\n}\n')
open(DOCROOT + "/bs/resource/properties/serversetting.json", "w").write(
    '{\n\t"loginserver": "http://127.0.0.1:9528",\n\t"requireSDK": false\n}\n')
# legacy migration source (v8-v13 formats)
GUEST_UID, GUEST_TOKEN, GUEST_NAME = "1700000000001", "a" * 32, "Guest1234"
EMAIL_UID, EMAIL_TOKEN, EMAIL_PW = "1700000000002", "b" * 32, hashlib.md5(b"rahasia123").hexdigest()
open(ROOT + "/data/account.json", "w").write(json.dumps({
    "uid": GUEST_UID, "token": GUEST_TOKEN, "username": GUEST_NAME,
    "createTime": 1700000000001}))
open(ROOT + "/data/accounts.json", "w").write(json.dumps({"accounts": [{
    "email": "lama@db.local", "uid": EMAIL_UID, "token": EMAIL_TOKEN,
    "passMd5": EMAIL_PW, "createTime": 1700000000002}]}))
# Bagian 30: instalan v14 lama punya server/data/db.sqlite — harus MIGRASI
# (disalin, bukan dipindah) ke sdk/data/db.sqlite oleh migrateFromOldLocation
OLD_DB = "DB-SQLITE-LAMA-v14-ini-hanya-junk-untuk-uji-copy-file"
open(ROOT + "/data/db.sqlite", "w").write(OLD_DB)
# v16: simulasikan instalan v15 — profil lama (products tanpa kode client)
# + index.txt sampah — harus di-upgrade / dibersihkan saat boot
os.makedirs(ROOT + "/sdk", exist_ok=True)
open(ROOT + "/sdk/profile.json", "w").write(V15_PROFILE)
open(ROOT + "/sdk/index.txt", "w").write("SAMPAH v15 — harus dihapus v16")

proc = subprocess.Popen(
    ["java", "-cp", BUILD, "SmokeMain", ROOT, DOCROOT, "synthcrash"],
    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, preexec_fn=os.setpgrp)
time.sleep(2.0)

try:
    # ================= 1. kontrak file + header PHP =================
    st, hd, bd = get("/v1/system/init")
    check("init-GET-200", st == 200)
    check("header-poweredby", hd.get("X-Powered-By") == "PHP/5.4.37", str(hd))
    check("header-ct-text-json", hd.get("Content-Type") == "text/json", str(hd))
    j = json.loads(bd)
    check("init-default-loginTypes", j["data"]["loginTypes"] == ["1"], bd)
    check("init-payTypes-wajib", "payTypes" in j.get("data", {}), bd)
    # v2 KOREKSI OWNER (crash HWLoginActivity NPE model.d.worktopichardtails):
    # seed default = mirror PENUH original — productConfig + version + lainnya
    check("v2-init-penuh-productConfig", "productConfig" in j.get("data", {})
          and j["data"]["productConfig"].get("guestShowBind") == "1"
          and len(j["data"]["productConfig"]) == 10, bd)
    check("v2-init-penuh-version", j["data"].get("version", {}).get("versionNo") == "empty"
          and j["data"].get("version", {}).get("isMust") == "empty"
          and len(j["data"].get("version", {})) == 6, bd)
    check("v2-init-penuh-first-install-country", j["data"].get("first_install") == 1
          and j["data"].get("countryCode") == "HK", bd)
    check("sdk-files-seeded", os.path.exists(ROOT + "/sdk/v1/system/init.json")
          and os.path.exists(ROOT + "/sdk/profile.json")
          and os.path.exists(ROOT + "/sdk/errors.json")
          and os.path.exists(ROOT + "/sdk/fallback.json")
          and os.path.exists(ROOT + "/sdk/v1/user/ckRegVistor.json"))
    # v16: index.txt TIDAK ADA lagi (dibersihkan dari instalan lama)
    check("v16-index-txt-dihapus", not os.path.exists(ROOT + "/sdk/index.txt"))
    # v16: profile.json bawaan v15 di-upgrade otomatis (products += kode client)
    prof = open(ROOT + "/sdk/profile.json").read()
    check("v16-upgrade-profile-30001", PRODUCT_ASLI in prof, prof[:200])
    check("v16-upgrade-profile-legacy-awet", "會員帳戶註冊" in prof)
    # Bagian 30: QHinfo masuk folder sdk/
    check("qhinfo-seeded", os.path.exists(ROOT + "/sdk/QHinfo"), bd)
    check("qhinfo-isi", open(ROOT + "/sdk/QHinfo").read().strip() == "http://127.0.0.1:9530")
    # Bagian 30: DB pindah ke sdk/data/ — migrasi dari server/data/ lama
    check("migrasi-lokasi-db-sqlite",
          open(ROOT + "/sdk/data/db.sqlite").read() == OLD_DB)
    check("migrasi-lokasi-legacy-json",
          os.path.exists(ROOT + "/sdk/data/account.json")
          and os.path.exists(ROOT + "/sdk/data/accounts.json"))
    check("lokasi-lama-tidak-hilang", os.path.exists(ROOT + "/data/db.sqlite"))

    # 2. live-reload kontrak: edit file -> jawaban ikut
    p = ROOT + "/sdk/v1/system/init.json"
    orig = open(p).read()
    open(p, "w").write(json.dumps({"result": True, "data": {"payTypes": [], "loginTypes": ["1", "13"]}}))
    st, hd, bd2 = get("/v1/system/init")
    check("init-livereload-edit", json.loads(bd2)["data"]["loginTypes"] == ["1", "13"], bd2)
    open(p, "w").write(orig)

    # 3. gerbang live: 20000 data kosong
    st, hd, bd = sdk_post("/v1/system/init", data_raw="", sign="")
    check("gerbang-20000-data-kosong", json.loads(bd) == envelope(20000, "Params error"), bd)

    # 4. gerbang live: 20002 sign salah
    p = must_params()
    st, hd, bd = sdk_post("/v1/system/init", p, sign="0" * 32)
    check("gerbang-20002-sign-salah", json.loads(bd) == envelope(20002, "Params sign error"), bd)

    # 5. gerbang live: 20003 bertahap (urutan asli)
    p = must_params(); p["sdkVersion"] = ""
    st, hd, bd = sdk_post("/v1/system/init", p)
    check("gerbang-20003-sdkVersion", json.loads(bd) == envelope(20003, "LostSomeMustParams:sdkVersion"), bd)
    p = must_params(); p["productCode"] = ""
    st, hd, bd = sdk_post("/v1/system/init", p)
    check("gerbang-20003-productCode", json.loads(bd) == envelope(20003, "LostSomeMustParams:productCode"), bd)
    p = must_params(); p["authToken"] = ""
    st, hd, bd = sdk_post("/v1/system/init", p)
    check("gerbang-20003-authToken", json.loads(bd) == envelope(20003, "LostSomeMustParams:authToken"), bd)

    # 6. gerbang live: 30001 product tidak dikenal
    p = must_params(product="tidak-ada")
    st, hd, bd = sdk_post("/v1/system/init", p)
    check("gerbang-30001-product", json.loads(bd) == envelope(30001, "Product doesn't exist"), bd)

    # 7. POST sah -> lolos gerbang -> init sukses
    st, hd, bd = sdk_post("/v1/system/init", must_params())
    check("init-POST-lolos-gerbang", json.loads(bd)["result"] is True, bd)

    # 7b. v16: POST sah dgn productCode ASLI client (rekam device 23 Nov)
    st, hd, bd = sdk_post("/v1/system/init", must_params(product=PRODUCT_ASLI))
    check("v16-init-product-asli-lolos", json.loads(bd)["result"] is True, bd)

    # 8. migrasi legacy: tamu = uid lama
    st, hd, bd = sdk_post("/v1/user/registerVisitor", must_params(token=GUEST_TOKEN))
    j = json.loads(bd)
    sess = json.loads(j["data"])
    check("migrasi-tamu-uid-awet", sess["userData"]["uid"] == GUEST_UID, bd)
    check("migrasi-tamu-nama", sess["userData"]["username"] == GUEST_NAME, bd)
    check("session-isGuest-12", sess["userData"]["isGuest"] == "1" and sess["userData"]["openType"] == "12", bd)

    # 9. akun email legacy bisa login
    st, hd, bd = sdk_post("/v1/user/loginByName", must_params(email="lama@db.local"))
    p = must_params()
    p["username"] = "lama@db.local"; p["password"] = EMAIL_PW
    st, hd, bd = sdk_post("/v1/user/loginByName", p)
    j = json.loads(bd)
    sess = json.loads(j["data"])
    check("migrasi-email-login", j["result"] is True and sess["userData"]["uid"] == EMAIL_UID, bd)
    check("login-isGuest-0-openType-1", sess["userData"]["isGuest"] == "0" and sess["userData"]["openType"] == "1", bd)

    # 10. login salah sandi -> 20004 (20002 LIVE = sign)
    p["password"] = hashlib.md5(b"s-lah").hexdigest()
    st, hd, bd = sdk_post("/v1/user/loginByName", p)
    check("login-salah-sandi-20004", json.loads(bd)["error"]["id"] == 20004, bd)

    # 11. login email tak terdaftar -> 20001
    p["username"] = "hantu@db.local"
    st, hd, bd = sdk_post("/v1/user/loginByName", p)
    check("login-belum-daftar-20001", json.loads(bd)["error"]["id"] == 20001, bd)

    # 12. registerUser baru
    PW2 = hashlib.md5(b"dua-kata").hexdigest()
    p = must_params(email="baru@db.local"); p["password"] = PW2
    st, hd, bd = sdk_post("/v1/user/registerUser", p)
    j = json.loads(bd)
    check("register-user-sukses", j["result"] is True, bd)
    new_uid = json.loads(j["data"])["userData"]["uid"]
    st, hd, bd = sdk_post("/v1/user/registerUser", p)
    check("register-dup-20001", json.loads(bd)["error"]["id"] == 20001, bd)

    # 13. autoLogin dgn token email baru
    st, hd, bd = sdk_post("/v1/user/registerUser", p)  # dup - dapat token? tidak. pakai token dari login:
    p = must_params(email="baru@db.local"); p["password"] = PW2
    st, hd, bd = sdk_post("/v1/user/loginByName", p)
    tok_new = json.loads(json.loads(bd)["data"])["userData"]["token"]
    st, hd, bd = sdk_post("/v1/user/autoLogin", must_params(token=tok_new))
    j = json.loads(bd)
    check("autoLogin-email", j["result"] is True and json.loads(j["data"])["userData"]["uid"] == new_uid, bd)
    st, hd, bd = sdk_post("/v1/user/autoLogin", must_params(token="f" * 32))
    check("autoLogin-bogus-fallback-tamu", json.loads(bd)["result"] is True
          and json.loads(json.loads(bd)["data"])["userData"]["uid"] == GUEST_UID, bd)

    # 14. getUserInfo email + tamu
    st, hd, bd = get("/v1/auth/getUserInfo?uid=" + new_uid)
    j = json.loads(bd)
    check("getUserInfo-email", j["result"] is True and j["data"]["isGuest"] == "0", bd)
    st, hd, bd = get("/v1/auth/getUserInfo?uid=" + GUEST_UID)
    j = json.loads(bd)
    check("getUserInfo-tamu", j["result"] is True and j["data"]["isGuest"] == "1", bd)

    # 15. bindMail TAMU -> email; uid/token tetap; tamu lama hilang
    OLD_GUEST_TOKEN = GUEST_TOKEN
    p = must_params(); p.update({"uid": GUEST_UID, "email": "bind@db.local",
                                 "code": "123456", "password": PW2})
    st, hd, bd = sdk_post("/v1/auth/bindMail", p)
    check("bindMail-sukses", json.loads(bd)["result"] is True, bd)
    p = must_params(email="bind@db.local"); p["password"] = PW2
    st, hd, bd = sdk_post("/v1/user/loginByName", p)
    j = json.loads(bd)
    sess = json.loads(j["data"])
    check("bindMail-login-uid-token-awet", j["result"] is True
          and sess["userData"]["uid"] == GUEST_UID
          and sess["userData"]["token"] == OLD_GUEST_TOKEN, bd)
    # TAMU baru = uid baru (tamu lama jadi akun email)
    st, hd, bd = sdk_post("/v1/user/registerVisitor", must_params())
    new_guest = json.loads(json.loads(bd)["data"])["userData"]["uid"]
    check("bindMail-tamu-baru-uid-bedaa", new_guest != GUEST_UID, bd)
    GUEST_NOW = new_guest

    # 16. changePassword
    p = must_params(); p.update({"uid": GUEST_UID,
                                 "oldPassword": PW2,
                                 "newPassword": hashlib.md5(b"tiga-kata").hexdigest()})
    st, hd, bd = sdk_post("/v1/auth/changePassword", p)
    check("changePassword-sukses", json.loads(bd)["result"] is True, bd)
    p = must_params(email="bind@db.local"); p["password"] = PW2
    st, hd, bd = sdk_post("/v1/user/loginByName", p)
    check("changePassword-sandi-lama-ditolak", json.loads(bd)["error"]["id"] == 20004, bd)

    # 17. ckRegVistor dari file (mirror live asli)
    st, hd, bd = get("/v1/user/ckRegVistor")
    j = json.loads(bd)
    check("ckRegVistor-kontrak", j["result"] is True and j["data"]["isReged"] == 0, bd)

    # 18. fallback path tak dikenal
    st, hd, bd = get("/v1/tidak/ada")
    check("fallback-envelope", json.loads(bd) == envelope(20000, "Params error"), bd)

    # 19. findPassByEmail belum daftar -> 20001
    p = must_params(email="hantu@db.local"); p["username"] = "hantu@db.local"
    st, hd, bd = sdk_post("/v1/user/findPassByEmail", p)
    check("findPass-belum-daftar-20001", json.loads(bd)["error"]["id"] == 20001, bd)

    # 20. profile.json diedit -> header ikut
    pfile = ROOT + "/sdk/profile.json"
    prof = open(pfile).read()
    open(pfile, "w").write(prof.replace("PHP/5.4.37", "PHP/5.4.40"))
    st, hd, bd = get("/v1/system/init")
    check("profile-header-livereload", hd.get("X-Powered-By") == "PHP/5.4.40", str(hd))
    open(pfile, "w").write(prof)

    # 21. backend fallback desktop = json (DB aktif di sdk/data/, bukan data/)
    check("backend-desktop-json", not os.path.exists(ROOT + "/sdk/data/db.json")
          and os.path.exists(ROOT + "/sdk/data/account.json"))

    # ============ Bagian 30: PANEL SDK CONFIG (dashboard 4000) ============
    st, hd, bd = get400("/sdkconfig")
    check("panel-GET-200", st == 200 and "SDK CONFIG" in bd)
    st, hd, bd = get400("/sdkconfig/api")
    api = json.loads(bd)
    check("panel-api-backend", api["backend"] == "json", bd[:120])
    check("panel-api-tamu", api["guest"] and api["guest"]["uid"] == GUEST_NOW, bd[:200])
    check("panel-api-file-init", "payTypes" in api["files"]["init.json"])
    check("panel-api-qhinfo", api["files"]["QHinfo"] == "http://127.0.0.1:9530")
    # v16: api membawa status product
    check("v2-panel-api-ver", api.get("ver") == "v3", bd[:80])
    check("v16-panel-api-products", PRODUCT_ASLI in api.get("products", []))

    # v16: endpoint product-add — tambah kode + validasi
    st, hd, bd = form_post("/sdkconfig/product-add", {"code": "TestProd123"})
    check("v16-product-add-ok", json.loads(bd)["result"] is True, bd)
    st, hd, bd = get400("/sdkconfig/api")
    api2 = json.loads(bd)
    check("v16-product-add-berlaku", "TestProd123" in api2.get("products", []))
    st, hd, bd = form_post("/sdkconfig/product-add", {"code": "a,b"})
    check("v16-product-add-tolak-koma", json.loads(bd)["result"] is False, bd)
    st, hd, bd = form_post("/sdkconfig/product-add", {"code": "x" * 65})
    check("v16-product-add-tolak-panjang", json.loads(bd)["result"] is False, bd)
    st, hd, bd = form_post("/sdkconfig/product-add", {"code": "TestProd123"})
    check("v16-product-add-idempotent", json.loads(bd)["result"] is True, bd)
    prof2 = open(ROOT + "/sdk/profile.json").read()
    check("v16-product-add-satu-kali", prof2.count("\"TestProd123\"") == 1)

    # save init.json via panel -> live-reload
    new_init = json.dumps({"result": True, "data": {"payTypes": [], "loginTypes": ["12"]}})
    st, hd, bd = form_post("/sdkconfig/save", {"name": "init.json", "content": new_init})
    check("panel-save-init-ok", json.loads(bd)["result"] is True, bd)
    st, hd, bd = get("/v1/system/init")
    check("panel-save-init-livereload", json.loads(bd)["data"]["loginTypes"] == ["12"], bd)

    # preset E3 -> loginTypes ["1","13"]
    st, hd, bd = form_post("/sdkconfig/preset", {"types": "1,13"})
    check("panel-preset-e3", json.loads(bd)["result"] is True, bd)
    st, hd, bd = get("/v1/system/init")
    check("panel-preset-e3-livereload", json.loads(bd)["data"]["loginTypes"] == ["1", "13"], bd)
    # preset kembali E1 (default)
    form_post("/sdkconfig/preset", {"types": "1"})

    # penolakan panel: nama tak dikenal (whitelist), init tanpa payTypes, QHinfo slash
    st, hd, bd = form_post("/sdkconfig/save", {"name": "../../evil", "content": "{}"})
    check("panel-tolak-nama-asing", json.loads(bd)["result"] is False, bd)
    st, hd, bd = form_post("/sdkconfig/save", {"name": "init.json", "content": "{\"result\":true}"})
    check("panel-tolak-init-tanpa-payTypes", json.loads(bd)["result"] is False, bd)
    st, hd, bd = form_post("/sdkconfig/save", {"name": "QHinfo", "content": "http://127.0.0.1:9530/"})
    check("panel-tolak-qhinfo-slash", json.loads(bd)["result"] is False, bd)
    st, hd, bd = form_post("/sdkconfig/save", {"name": "QHinfo", "content": "http://127.0.0.1:9530"})
    check("panel-save-qhinfo-ok", json.loads(bd)["result"] is True, bd)
    check("panel-qhinfo-berlaku", open(ROOT + "/sdk/QHinfo").read().strip() == "http://127.0.0.1:9530")
    # file asing TIDAK dibuat (anti path traversal)
    check("panel-tidak-buat-file-asing", not os.path.exists(ROOT + "/evil"))

    # ============ Bagian 32: DASHBOARD v7 TABEL + CRASH + OVERLAY ============
    st, hd, bd = get400("/")
    # Bagian 39 — judul dashboard baru "DB MONITOR v3"
    check("v17-dashboard-200", st == 200 and "DB MONITOR" in bd)
    check("v17-tabel-markup", "<table id=tbl" in bd and "PERISTIWA" in bd
          and "<tbody id=tb>" in bd, bd[:80])
    check("v17-chip-tahap", "renderStages" in bd and "stagechips" in bd)
    check("v17-salin-tabel", "copyTable" in bd and "Salin tabel" in bd)
    check("v17-banner-crash-js", "renderCrash" in bd and "fetch('/crash')" in bd
          and "id=cpcrash" in bd)  # Bagian 41: tombol icon-only 📋
    # Bagian 39 — overlay bukan lagi banner merah: renderOverlay DIHAPUS,
    # status overlay tampil netral di sysline ("overlay OFF (normal)");
    # endpoint POST /fix/overlay tetap ada (kompatibilitas API)
    check("v17-banner-overlay-js", "renderOverlay" not in bd
          and "BUKA SETTINGS OVERLAY" not in bd
          and "overlay OFF (normal)" in bd)
    check("v17-ver-dashboard", "v5" in bd and "/v1/system/init" in bd)  # v5: penanda versi dinaikkan (Bagian 41)

    # /crash: awalnya bisa sudah ada (crash sintetis 1,5 dtk setelah start —
    # smoke sudah jalan >2 dtk) -> latest harus ada dan berisi uji
    st, hd, bd = get400("/crash")
    cj = json.loads(bd)
    check("v17-crash-route", st == 200 and "files" in cj, bd[:120])
    if cj.get("latest"):
        check("v17-crash-sintetis", "uji-crash-sintetis-v17" in cj["latest"]["text"]
              and "STACK TRACE" in cj["latest"]["text"]
              and "LOG TERAKHIR" in cj["latest"]["text"],
              cj["latest"]["text"][:200])
    else:
        # belum sempat ditulis (race) — tunggu sebentar lalu cek lagi
        time.sleep(2.0)
        st, hd, bd = get400("/crash")
        cj = json.loads(bd)
        check("v17-crash-sintetis", bool(cj.get("latest"))
              and "uji-crash-sintetis-v17" in cj["latest"]["text"], bd[:200])
    # entri CRASH masuk buffer logger
    st, hd, bd = get400("/logs?limit=100")
    lg = json.loads(bd)
    check("v17-crash-entry-logger", any(e.get("kind") == "CRASH" for e in lg["entries"]),
          str(lg["entries"][-1])[:200] if lg["entries"] else "kosong")
    # file crash fisik ada di logs/
    crash_files = [f for f in os.listdir(ROOT + "/logs") if f.startswith("crash-")]
    check("v17-crash-file-disk", len(crash_files) >= 1, str(crash_files))

    # /stats membawa status overlay
    st, hd, bd = get400("/stats")
    stats = json.loads(bd)
    check("v17-stats-overlay", "overlay" in stats, bd[-120:])

    # ============ v18: RESET SESI SDK + EXITINFO + BANNER SESI ============
    # /sdkconfig/api: ver=v18 + overlay + session
    st, hd, bd = get400("/sdkconfig/api")
    api = json.loads(bd)
    check("v2-api-ver", api.get("ver") == "v3", api.get("ver"))
    check("v1-api-overlay-session", "overlay" in api and "session" in api
          and set(api["session"]) >= {"saved", "resetAuto", "resetInfo"}, bd[-200:])
    check("v1-api-desktop-sesi", api["session"]["saved"] is False
          and api["session"]["resetAuto"] is False, str(api["session"]))
    check("v18-api-overlay-desktop", api["overlay"]["checked"] is False
          and api["overlay"]["granted"] is True, str(api["overlay"]))

    # POST /sdkconfig/session-reset di desktop -> jujur result:false + pesan desktop
    st, hd, bd = form_post("/sdkconfig/session-reset", {})
    j = json.loads(bd)
    check("v18-route-session-reset", st in (200, 400) and j["result"] is False
          and "desktop" in j.get("error", ""), bd)

    # /fix/overlay di desktop (tanpa context) -> result:false jujur
    st, hd, bd = form_post("/fix/overlay", {})
    j = json.loads(bd)
    check("v17-fix-overlay-desktop", st in (200, 400) and j["result"] is False
          and "bukan Android" in j.get("error", ""), bd)

    # dashboard monitor: banner sesi + fungsi renderSession + ver v18
    st, hd, bd = get400("/")
    check("v1-ver-dashboard", "v1" in bd, bd[:80])
    check("v41-banner-sesi-dihapus", "renderSession" not in bd and "sesbn" not in bd
          and "SESI SDK DIRESET OTOMATIS" not in bd)  # Bagian 41: sesi reset kini hanya entri log SYS (SESI-RESET)
    check("v41-sysline-dihapus", "sysline" not in bd
          and "sdkconfig" not in bd)  # Bagian 41: data SDK CONFIG keluar dari monitor
    check("v41-banner-crash-js-masih", "renderCrash" in bd and "id=cpcrash" in bd)

    # panel /sdkconfig: seksi SESI SDK + tombol RESET + JS sesReset
    st, hd, bd = get400("/sdkconfig")
    check("v18-panel-sesi", "RESET SESI SDK" in bd and "sesReset" in bd
          and "session-reset" in bd, bd[:80])
    check("v18-panel-seschip", "seschip" in bd and "SESI SDK" in bd, bd[:80])
    # JS panel tidak boleh mengandung string unterminated akibat \n mentah
    check("v18-panel-js-rapi", "confirm('RESET SESI SDK?\\n" in bd,
          "escape \\n dalam JS string")

    # EXITINFO masuk log SYS di desktop (entri "desktop — dilewati")
    st, hd, bd = get400("/logs?limit=200")
    lg = json.loads(bd)
    check("v18-exitinfo-desktop", any(
        e.get("method") == "EXITINFO" and "desktop" in (e.get("path") or "")
        for e in lg["entries"]), str(lg["entries"][:3]))

    # entri SESI-GUARD tidak boleh muncul di desktop (ctx null)
    check("v18-autoguard-desktop-skip", not any(
        e.get("method") == "SESI-GUARD" for e in lg["entries"]))

    # bootstrap: crash-handled.list boleh tidak ada, tapi unhandledCrashFiles
    # harus tidak menganggap handled file (uji tak langsung: /crash tetap
    # menampilkan crash sintetis v17 sebagai latest)
    st, hd, bd = get400("/crash")
    cj = json.loads(bd)
    check("v18-crash-route-masih", st == 200 and ("files" in cj), bd[:120])

    # ================= v3 FIX STUCK + FIX POPUP 404 =================
    # 1. clientversion.json dilayani 9528 — request 404 inilah sumber POPUP
    #    pengingat (smali LaunchActivity memetakan 404 → string "404" → popup;
    #    readme Bagian 19 — pola sama dgn "Server base.version is Not Exist")
    with urllib.request.urlopen(
            "http://127.0.0.1:9528/bs/resource/properties/clientversion.json",
            timeout=10) as r:
        cvst, cvbd = r.status, r.read().decode()
    check("v3-clientversion-200", cvst == 200 and "clientVersion" in cvbd, cvbd[:80])

    # 2. serversetting.json loginserver → 9528 (dulu 5050 — tak pernah dicapai JS)
    with urllib.request.urlopen(
            "http://127.0.0.1:9528/bs/resource/properties/serversetting.json",
            timeout=10) as r:
        ssst, ssbd = r.status, r.read().decode()
    ssj = json.loads(ssbd)
    check("v3-serversetting-9528", ssst == 200
          and ssj.get("loginserver") == "http://127.0.0.1:9528", ssbd[:100])

    # 3. chain /socket.io/ DI 9528: OPEN handshake dijawab listener CDN
    sock_base = "http://127.0.0.1:9528"
    with urllib.request.urlopen(sock_base + "/socket.io/?EIO=3&transport=polling"
                                + "&t=smoke" + str(int(time.time() * 1000) % 100000),
                                timeout=10) as r:
        opst, opbd = r.status, r.read().decode()
    check("v3-sockio-open-9528", opst == 200 and ":0{" in opbd and ":40" in opbd,
          opbd[:120])
    sid = ""
    try:
        import re as _re
        msid = _re.search(r'"sid":"([A-Za-z0-9]+)"', opbd)
        if msid:
            sid = msid.group(1)
    except Exception:
        sid = ""
    check("v3-sockio-sid-9528", len(sid) == 20, opbd[:120])

    # 4. GetServerList via 9528 → ACK serverItem.url = http://127.0.0.1:9528
    msg = json.dumps({"type": "User", "action": "GetServerList",
                      "userId": "1700000000001", "subChannel": "",
                      "channel": "quick"}, separators=(",", ":"))
    pkt = '421["handler.process",' + msg + ']'
    body = "".join("%d:%s" % (len(p), p) for p in (pkt,))
    req = urllib.request.Request(
        sock_base + "/socket.io/?EIO=3&transport=polling&sid=" + sid,
        data=body.encode(), method="POST")
    req.add_header("Content-Type", "text/plain; charset=UTF-8")
    with urllib.request.urlopen(req, timeout=10) as r:
        postst, postbd = r.status, r.read().decode()
    check("v3-sockio-post-ok", postst == 200 and postbd == "ok", postbd[:40])
    with urllib.request.urlopen(
            sock_base + "/socket.io/?EIO=3&transport=polling&sid=" + sid,
            timeout=10) as r:
        plst, plbd = r.status, r.read().decode()
    check("v3-sockio-ack-serverlist", plst == 200 and "serverList" in plbd
          and "127.0.0.1:9528" in plbd, plbd[:200])

    # 5. bind label 9528 baru (BIND tercatat di BOOT — ambil arsip awal)
    st, hd, bd = get400("/logs?limit=2000")
    lg = json.loads(bd)
    check("v3-bind-label-cdn-chain", any(
        "CDN game + socket.io login" in (e.get("path") or "")
        for e in lg["entries"]), "")

    # ================= Bagian 39 — logger v3 (dashboard redesign) ==========
    # Arahan owner: overlay = INFO (bukan error); teks jelas, mudah dibaca;
    # tata letak terstruktur & rapi; emoji stylish; efisien.
    st, hd, bd = get400("/")
    # 1. identitas + emoji stylish
    check("v39-title-monitor", "DB MONITOR" in bd and "\U0001f409" in bd, bd[:80])
    check("v39-emoji-verdict", "\u2705" in bd and "\u274c" in bd
          and "\u26a0\ufe0f" in bd and "\u2139\ufe0f" in bd, "")
    check("v39-emoji-stage", "\u2699\ufe0f" in bd and "\U0001f310" in bd
          and "\U0001f3ae" in bd, "")
    check("v39-emoji-buttons", "\U0001f4cb" in bd and "\U0001f4be" in bd
          and "\U0001f9f9" in bd, "")
    # 2. overlay = INFO bukan error: verdict INFO + eksklusi eksplisit
    #    isProblem + banner merah overlay lama DIHAPUS
    check("v39-overlay-info-verdict",
          "m=='OVERLAY'" in bd and "['INFO','i']" in bd, "")
    check("v39-overlay-not-problem", "=='OVERLAY')return false" in bd, "")
    check("v39-overlay-banner-dihapus",
          "IZIN Display over other apps OFF" not in bd
          and "BUKA SETTINGS OVERLAY" not in bd
          and "tombol merah BUKA SETTINGS" not in bd, "")
    # 3. banner sesi reset = info amber (class note), heading tetap dikenali
    check("v40-sesi-lewat-log", "renderSession" not in bd
          and "SESI SDK DIRESET OTOMATIS" not in bd, "")
    # 4. tabel baru 7 kolom: STATUS + PERISTIWA (tanpa kolom ERROR terpisah)
    check("v39-tabel-status", "<th>STATUS</th>" in bd
          and "<th>PERISTIWA</th>" in bd and "vEmoji" in bd, "")
    # 5. sysline overlay netral + panduan menyatakan overlay normal
    check("v39-sysline-overlay-normal", "overlay OFF (normal)" in bd, "")
    check("v39-panduan-overlay", "Izin overlay OFF = <b>NORMAL</b>" in bd, "")
    # 6. log entri OverlayFixer sendiri berbunyi netral (uji unit fungsi
    #    deskripsi: teks baru ada di JS describe untuk SYS/OVERLAY)
    check("v39-describe-overlay-info", "Izin overlay</b> — '+esc(p)" in bd, "")


    # ================= Bagian 41 — port 4000 MURNI log monitoring ==========
    # Arahan owner (kata-kata persis):
    #  "LOG GAK MUNCUL SAMA SEKALI , EMOJI STYLE ITUU LOG NYAA !!
    #   TOMBOLNYA YAA ICON LAH"
    #  "DAN ITU LAGI CONFIG SDK YG MUNCUL DI WEBSITE.. ITU DI LUAR
    #   PERSETUJUAN SAYA KARENA AI CACAT SEBELUMNYA.. SEMENTARA PORT 4000
    #   CUMA UNTUK LOG MONITORING"
    st, hd, bd = get400("/")
    # 1. NOL jejak SDK CONFIG di halaman monitor (link/tombol/sysline/data)
    check("v41-no-sdkconfig-leak", "sdkconfig" not in bd and "SDK CONFIG" not in bd
          and "renderSession" not in bd and "fixCode" not in bd, bd[:100])
    # 2. tombol toolbar icon-only (tanpa label teks panjang)
    check("v41-icon-buttons", ">\u2b50</button>" in bd and ">\u274c</button>" in bd
          and ">\U0001f4dc</button>" in bd and "PENTING</button>" not in bd
          and "MASALAH</button>" not in bd and "Salin tabel</button>" not in bd, "")
    # 3. GUARD REGRESI — akar bug "log gak muncul": $('id') tanpa elemen HTML
    #    = TypeError = seluruh JS mati = poll log tidak pernah jalan.
    #    Setiap id dirujuk $('...') di JS WAJIB ada di HTML (cpcrash dinamis).
    js = bd[bd.find("<script>"):bd.find("</script>")]
    html = bd[:bd.find("<script>")]
    ids_js = set(re.findall(r"\$\('([A-Za-z0-9_-]+)'\)", js))
    ids_html = set(re.findall(r"id=[\"']?([A-Za-z0-9_-]+)", html))
    missing = sorted(ids_js - ids_html - set(["cpcrash"]))
    check("v41-js-id-semua-ada", not missing, "hilang: %s" % missing)
    # 4. elemen probn ada (banner masalah) + tombol modal icon
    check("v41-probn-ada", "id=probn" in html and "id=crashbn" in html, "")
    check("v41-modal-icon", ">\U0001f4cb</button>" in bd and ">\u2716\ufe0f</button>" in bd, "")
finally:
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)

print("\n===== SMOKE v2: %d PASS, %d FAIL =====" % (len(PASS), len(FAIL)))
if FAIL:
    print("GAGAL:", FAIL)
    sys.exit(1)
