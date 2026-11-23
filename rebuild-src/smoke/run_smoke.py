#!/usr/bin/env python3
# Smoke desktop v14 (Bagian 29) — uji mesin kawat PHP-faithful + SQLite layer
# (desktop pakai backend json; semantik sama dgn DbSqlite).
import base64, hashlib, json, os, shutil, signal, subprocess, sys, time, urllib.request, urllib.error

SECRET = "0b2a18e45d7df321"
BASE = "http://127.0.0.1:9530"
ROOT = "/tmp/v14root"
WS = "/home/z/ws9"

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
# legacy migration source (v8-v13 formats)
GUEST_UID, GUEST_TOKEN, GUEST_NAME = "1700000000001", "a" * 32, "Guest1234"
EMAIL_UID, EMAIL_TOKEN, EMAIL_PW = "1700000000002", "b" * 32, hashlib.md5(b"rahasia123").hexdigest()
open(ROOT + "/data/account.json", "w").write(json.dumps({
    "uid": GUEST_UID, "token": GUEST_TOKEN, "username": GUEST_NAME,
    "createTime": 1700000000001}))
open(ROOT + "/data/accounts.json", "w").write(json.dumps({"accounts": [{
    "email": "lama@db.local", "uid": EMAIL_UID, "token": EMAIL_TOKEN,
    "passMd5": EMAIL_PW, "createTime": 1700000000002}]}))

proc = subprocess.Popen(
    ["java", "-cp", WS + "/server-v14/build-classes", "SmokeMain", ROOT, WS + "/server_root"],
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
    check("sdk-files-seeded", os.path.exists(ROOT + "/sdk/v1/system/init.json")
          and os.path.exists(ROOT + "/sdk/profile.json")
          and os.path.exists(ROOT + "/sdk/index.txt")
          and os.path.exists(ROOT + "/sdk/errors.json")
          and os.path.exists(ROOT + "/sdk/fallback.json")
          and os.path.exists(ROOT + "/sdk/v1/user/ckRegVistor.json"))

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

    # 21. backend fallback desktop = json (db.sqlite TIDAK ada)
    check("backend-desktop-json", not os.path.exists(ROOT + "/data/db.sqlite"))

finally:
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)

print("\n===== SMOKE v14: %d PASS, %d FAIL =====" % (len(PASS), len(FAIL)))
if FAIL:
    print("GAGAL:", FAIL)
    sys.exit(1)
