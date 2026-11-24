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
#     pernah minta overlay; SDK tetap berjalan normal): verdict INFO,
#     eksklusi eksplisit di isProblem, banner merah overlay dihapus,
#     teks OverlayFixer/SessionReset dinetralkan.
# Bagian 43 — rombak total ATAS ARAHAN OWNER (kata-kata persis):
#  "sekarang mari rombak lagi logger dan UI di website kita, logger ini
#   emang stylish tapi gak seperti layaknya server yg Natural sedang
#   berjalan.."
#  6. CONSOLE v7 — dashboard SERVER NATURAL: terminal monospace hitam
#     pekat, entri log TANPA emoji (teks polos seperti server sungguhan),
#     satu baris per peristiwa mengalir ke bawah, kursor blok berkedip,
#     baris status LISTEN/uptime/seq/arsip/status. Tidak ada lagi kartu/
#     chip/pill/badge warna-warni dan judul "DB MONITOR".
#  7. PORT 4000 MURNI log monitoring — rute /sdkconfig*, /sdkconfig/api,
#     /sdkconfig/save, /sdkconfig/preset, /sdkconfig/product-add,
#     /sdkconfig/session-reset dan POST /fix/overlay DIHAPUS dari
#     LoggerHandler (404). Bukan cuma disembunyikan dari tampilan — rute
#     benar-benar tidak ada (penegasan arahan Bagian 41).
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

def get400s(path):
    """GET :4000 yang TIDAK melempar saat 4xx/5xx (utk uji rute dihapus 404)."""
    try:
        with urllib.request.urlopen("http://127.0.0.1:4000" + path, timeout=10) as r:
            return r.status, dict(r.headers), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8", "replace")

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

    # ============ Bagian 43: PORT 4000 MURNI LOG MONITORING ============
    # Arahan owner (kata-kata persis, Bagian 41 + penegasan Bagian 43):
    #  "PORT 4000 CUMA UNTUK LOG MONITORING" — kini rute config SDK
    #  benar-benar DIHAPUS dari LoggerHandler (404), bukan disembunyikan.
    for del_route in ("/sdkconfig", "/sdkconfig/api", "/sdkconfig/save",
                      "/sdkconfig/preset", "/sdkconfig/product-add",
                      "/sdkconfig/session-reset", "/fix/overlay"):
        st, hd, bd = get400s(del_route)
        check("v43-rute-dihapus-404 " + del_route, st == 404, "%s -> %s" % (del_route, st))
    st, hd, bd = form_post("/sdkconfig/session-reset", {})
    check("v43-session-reset-404", st == 404, "%s" % st)
    st, hd, bd = form_post("/sdkconfig/save", {"name": "init.json", "content": "{}"})
    check("v43-save-404", st == 404, "%s" % st)
    st, hd, bd = form_post("/fix/overlay", {})
    check("v43-fix-overlay-404", st == 404, "%s" % st)
    # QHinfo tidak bisa diubah lewat port 4000 lagi — isi tetap benar
    check("v43-qhinfo-utuh", open(ROOT + "/sdk/QHinfo").read().strip() == "http://127.0.0.1:9530")

    # ============ Bagian 32/43: CONSOLE NATURAL + CRASH + OVERLAY =========
    st, hd, bd = get400("/")
    # Bagian 44 — console SERVER PREMIUM (dipercantik, teks verbose ala server asli)
    check("v43-console-200", st == 200 and "DB LOCAL SERVER" in bd
          and "MOD-LOCALSERVER v14" in bd, bd[:120])
    check("v43-stream-markup", "id=stream" in bd and "id=tb" in bd
          and "id=tail" in bd, bd[:80])
    check("v17-salin-tabel", "copyTable" in bd and "plainLine(e)" in bd)
    check("v17-banner-crash-js", "renderCrash" in bd and "fetch('/crash')" in bd
          and "id=cpcrash" in bd)  # Bagian 41: tombol icon-only 📋
    # Bagian 39 — overlay tetap INFO netral ("overlay OFF (normal)")
    check("v17-banner-overlay-js", "renderOverlay" not in bd
          and "BUKA SETTINGS OVERLAY" not in bd
          and "overlay OFF (normal)" not in bd
          and "OVERLAY <b>OFF</b>" in bd)
    check("v17-ver-dashboard", ">v14</span>" in bd and "raw server log" in bd)  # v14 (Bagian 51)

    # /crash: awalnya bisa sudah ada (crash sintetis 1,5 dtk setelah start —
    # smoke sudah jalan >2 dtk) -> latest harus ada dan berisi uji
    st, hd, bd = get400("/crash")
    cj = json.loads(bd)
    check("v17-crash-route", st == 200 and "files" in cj, bd[:120])
    if cj.get("latest"):
        check("v17-crash-sintetis", "uji-crash-sintetis-v17" in cj["latest"]["text"]
              and "STACK TRACE" in cj["latest"]["text"]
              and "LAST 50 LOG" in cj["latest"]["text"],
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

    # ============ v18: RESET SESI via log SYS + EXITINFO ===================
    # Bagian 43: rute sdkconfig sudah dihapus — sesi reset kini HANYA terlihat
    # sebagai entri log SYS (SESI-RESET). Panel web di port 4000 tidak ada.
    st, hd, bd = get400("/")
    check("v1-ver-dashboard", "v14" in bd, bd[:80])
    check("v41-banner-sesi-dihapus", "renderSession" not in bd and "sesbn" not in bd
          and "SESI SDK DIRESET OTOMATIS" not in bd)  # sesi reset kini hanya entri log SYS (SESI-RESET)
    check("v41-sysline-dihapus", "sysline" not in bd
          and "sdkconfig" not in bd)  # Bagian 41/43: data SDK CONFIG benar-benar keluar dari port 4000
    check("v41-banner-crash-js-masih", "renderCrash" in bd and "id=cpcrash" in bd)

    # EXITINFO masuk log SYS di desktop (entri "desktop — dilewati")
    st, hd, bd = get400("/logs?limit=200")
    lg = json.loads(bd)
    check("v18-exitinfo-desktop", any(
        e.get("method") == "EXITINFO" and "desktop" in (e.get("path") or "")
        for e in lg["entries"]), str(lg["entries"][:3]))

    # entri SESI-GUARD tidak boleh muncul di desktop (ctx null)
    check("v18-autoguard-desktop-skip", not any(
        e.get("method") in ("SESI-GUARD", "SESSION-GUARD") for e in lg["entries"]))

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

    # ============ Bagian 50 — console RAW SERVER LOG (guard RAW) ==========
    # Arahan owner (kata-kata persis):
    #  "itu log apa bisa di baca!!!! natural.. kalau log Inggris ya inggirs .
    #   log jangan anda karang lah !!! gmana mau tau apa yg server ini
    #   butuhkan kalau semuanya karangan gak jelas dari anda !!!"
    st, hd, bd = get400("/")
    # 1. identitas console raw + judul lama hilang (v3 & v7)
    check("v44-console-title", "DB LOCAL SERVER" in bd and "DB MONITOR" not in bd
          and "com.db.local — server console" not in bd, bd[:80])
    # 2. level ala log server: INFO/DEBUG/WARN/ERROR + overlay = INFO
    check("v60-level-of", "word:'INFO',cls:'g',icon:'check'" in bd
          and "word:'DEBUG',cls:'d',icon:'search'" in bd
          and "word:'WARN',cls:'w',icon:'warn'" in bd
          and "word:'ERROR',cls:'r',icon:'x'" in bd, "")
    check("v44-fmtB-detail", "function fmtB(n)" in bd and "function mainLine(e)" in bd, "")
    # 3. komponen server berwarna: [CORE][CONFIG][CDN][SDK][LOGIN][GAME][MONITOR]
    check("v44-comp-of", "function compOf(e)" in bd, "")
    check("v44-comp-tags", "c-CORE" in bd and "c-CONFIG" in bd and "c-CDN" in bd
          and "c-SDK" in bd and "c-LOGIN" in bd and "c-GAME" in bd
          and "c-MONITOR" in bd, "")
    # 4. banner boot = teks RAW persis protokol
    check("v44-banner-boot", "id=banner" in bd and "class=b1" in bd
          and "DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v14" in bd, "")
    # v14 — banner SATU BARIS, tanpa narasi karangan
    check("v14-banner-slim", "raw server log — http://127.0.0.1:4000" in bd
          and "PERSIS PROTOKOLNYA" not in bd, "")
    # 5. header premium: titik status bercahaya + badge versi
    check("v44-hdr-premium", "id=dot" in bd and "id=ver" in bd
          and "@keyframes pl" in bd, "")
    # 6. overlay tetap INFO (Bagian 39 — tidak berubah)
    check("v39-overlay-info-verdict",
          "m=='OVERLAY'" in bd and "m=='OVERLAY')return {word:'INFO',cls:'g',icon:'check'}" in bd, "")
    check("v39-overlay-not-problem", "=='OVERLAY')return false" in bd, "")
    # 7. teks overlay TIDAK di-hardcode di dashboard (datang dari log — raw)
    check("v63-overlay-from-data", "overlay permission OFF" not in bd, "")

    # ================= Bagian 47 — v9 FULL DISCLOSURE (tanpa klik) ==========
    # Arahan owner (kata-kata persis):
    #  "UI website port 4000 itu elegan !! estetik !!!"
    #  "dan kenapa harus di klik bagian tertentu baru muncul keterangan
    #   lengkap . !! itu bukan definisi server log !!"
    #  "yg bener lah !! banyak itu log gk jelas !!!"
    st, hd, bd = get400("/")
    # 1. modal klik-detail DIHAPUS TOTAL (bukan definisi server log)
    check("v49-no-modal", "modal" not in bd.lower() and "openModal" not in bd
          and "detOf" not in bd and "allText" not in bd, "")
    check("v49-stream-no-click", "stream').addEventListener" not in bd, "")
    # 2. SEMUA keterangan tampil INLINE di tiap entri — label RAW lowercase
    check("v63-inline-raw", "'s-det','corner','headers:'" in bd
          and "'s-req','up','request:'" in bd
          and "'s-res','down','response:'" in bd
          and "'s-req','up','recv:'" in bd
          and "'s-res','down','send:'" in bd
          and "'s-ext','tag','data:'" in bd
          and "'s-err','x','error:'" in bd, "")
    # 3. FULL DISCLOSURE: NOL pemotongan preview 200/160 (dulu data disembunyikan)
    check("v63-full-disclosure", "trunc(e.reqPreview,200)" not in bd
          and "trunc(e.respPreview,200)" not in bd
          and "trunc(''+e.extra,160)" not in bd, "")
    # 4. helper trunc + sub-baris bergaya aksen tipis + body panjang menggulir
    check("v49-trunc", "function trunc(s,n)" in bd, "")
    check("v60-sub-style", ".dtl{margin-top:7px" in bd
          and ".ln.r .dtl{border-left-color:#3a2026}" in bd
          and "max-height:280px" in bd, "")
    # v14 — detail kolaps: header .sh klik + isi .sc tersembunyi default
    check("v14-sub-collapse", ".sh{display:flex" in bd
          and ".sc{display:none" in bd
          and ".sub[data-o='1']>.sc{display:block}" in bd
          and "data-o=" in bd and "IC.chev" in bd, "")
    check("v14-sub-sum", ".sum{flex:1" in bd and "class=sum>" in bd
          and ".sz{color:" in bd, "")
    # 5. salin log = teks mentah persis protokol (time #seq port METHOD path status)
    check("v63-salin-raw", "'    headers: '+e.headers" in bd
          and "'    request: '+e.reqPreview" in bd
          and "'    response: '+e.respPreview" in bd
          and "'    recv: ('+fmtB(e.reqLen||0)+') " in bd
          and "'    send: ('+fmtB(e.respLen||0)+') " in bd
          and "plainLine(e)" in bd, "")
    # 6. v14 — panel "Panduan" DIHAPUS (narasi karangan, arahan owner)
    check("v49-guide-panel", "id=guide" not in bd and "guidebtn" not in bd
          and "ALUR NORMAL BOOT" not in bd, "")
    # 7. versi v13 di badge + banner
    check("v49-ver", ">v14</span>" in bd and "MOD-LOCALSERVER v14" in bd, "")

    # ================= Bagian 50 — v13 RAW SERVER LOG ========================
    # Arahan owner (kata-kata persis):
    #  "itu yg annda pakai untuk website Masuh emoji bukan icon !!"
    #  "itu log apa bisa di baca!!!! natural.. kalau log Inggris ya inggirs .
    #   log jangan anda karang lah !!!"
    st, hd, bd = get400("/")
    # 1. registry ICON SVG sungguhan (bukan emoji) — kecantikan dari icon
    check("v60-icon-registry", "function svg(p)" in bd and "var IC={}" in bd
          and "IC.flame" in bd and "function ic(n)" in bd
          and "function paintIcons" in bd and "[data-ic]" in bd, "")
    check("v60-icon-shape", bd.count('=svg("') >= 30, str(bd.count('=svg("')))
    # 2. NOL emoji di MANA PUN — chrome UI + TEKS LOG (log server asli tak
    #    pakai emoji; arahan Bagian 50 menyupersei Bagian 48)
    def _emoji(s2):
        for ch in s2:
            o = ord(ch)
            if (0x1F000 <= o <= 0x1FFFF) or (0x2600 <= o <= 0x27BF) \
               or (0x2B00 <= o <= 0x2BFF) or (0xFE00 <= o <= 0xFE0F):
                return True
        return False
    check("v63-no-emoji-all", not _emoji(bd), "emoji ditemukan di console!")
    # 3. tombol ikon digambar via paintIcons (bukan karakter emoji)
    check("v60-btn-paint", "$('pause').innerHTML=ic(S.auto?'pause':'play')" in bd
          and "$('reset').innerHTML=ic('trash')" in bd
          and "$('dl').innerHTML=ic('download')" in bd
          and "$('cpt').innerHTML=ic('copy')" in bd, "")
    # 4. chip status berikon + label manusia
    check("v60-chips", "class=schip" in bd and "id=stchips" in bd
          and "ic('server')+'LISTEN" in bd and "ic('clock')+'UPTIME" in bd
          and "ic('hash')+'SEQ" in bd and "ic('archive')+'ARSIP" in bd
          and "ic('layers')+'OVERLAY" in bd and "ic('activity')+'STATUS" in bd, "")
    check("v60-chip-ports", "'9527','settings','KONFIG'" in bd
          and "'9528','globe','CDN'" in bd and "'5050','shield','LOGIN'" in bd
          and "'5051','gamepad','GAME'" in bd and "'9530','card','SDK'" in bd
          and "'4000','radio','LOG'" in bd, "")
    # 5. tata letak: kartu log + spine warna level + meta kanan
    check("v60-row-card", ".ln{position:relative" in bd
          and "border-left:3px solid" in bd
          and ".ln.g{border-left-color:#3ddc84}" in bd
          and ".ln.r{border-left-color:#ff5d5d" in bd, "")
    check("v60-meta-right", ".rmeta{margin-left:auto" in bd
          and "class=rmeta" in bd, "")
    # 6. RAW main line: method + FULL path (query TIDAK dipangkas) + status angka
    check("v63-raw-mainline", "function mainLine(e)" in bd
          and "'<b>'+esc(m||'GET')+'</b> '+esc(p)+' '+stHtml" in bd
          and "function stHtml(n)" in bd and "c='s5'" in bd and "c='s2'" in bd, "")
    check("v63-raw-ws", "'<b>WS</b> '+esc(p)" in bd
          and "p.indexOf('ping->pong')>=0" in bd
          and "if(m=='WS')return 'WS '+p" in bd, "")
    # 7. NOL karangan: fungsi naratif lama DIHAPUS + kalimat sintetis hilang
    check("v63-no-describe", "function describe" not in bd and "stLbl" not in bd
          and "function epName" not in bd and "function sdkErr" not in bd
          and "function evName" not in bd and "function ms2" not in bd
          and "function plain(" not in bd, "")
    check("v63-no-synthetic", "Koneksi WebSocket" not in bd and "Frame socket" not in bd
          and "Endpoint : " not in bd and "Kirim : " not in bd
          and "Terima : " not in bd and "Durasi : " not in bd
          and "' RUSAK'" not in bd and "' GAGAL'" not in bd
          and "' ALIH'" not in bd, "")
    # 8. chip level & komponen berikon
    check("v60-comp-icon", "name:'CORE',icon:'cpu'" in bd
          and "name:'CONFIG',icon:'settings'" in bd
          and "name:'CDN',icon:'globe'" in bd and "name:'SDK',icon:'card'" in bd
          and "name:'LOGIN',icon:'shield'" in bd
          and "name:'GAME',icon:'gamepad'" in bd
          and "name:'MONITOR',icon:'radio'" in bd, "")
    check("v60-lv-tint", ".l-g{color:#3ddc84;background:rgba(61,220,132,.1)" in bd
          and ".l-r{color:#ff5d5d;background:rgba(255,93,93,.12)" in bd, "")
    # 9. animasi cantik: fade-in baris baru + toast meluncur + judul gradasi
    check("v60-fade", "@keyframes fadein" in bd and "animation:fadein .2s" in bd, "")
    check("v60-toast-rise", "@keyframes rise" in bd, "")
    check("v60-title-grad", "linear-gradient(90deg,#f2f8fd,#8fe6b0)" in bd, "")
    # 10. salin log = RAW (LVE/CPE emoji map DIHAPUS)
    check("v63-no-emoji-copy", "LVE={" not in bd and "CPE={" not in bd, "")
    check("v60-stbadge", "ic('check')+' NORMAL'" in bd
          and "ic('x')+' '+n+' MASALAH'" in bd, "")
    # 11. v14 — panduan DIHAPUS; petunjuk singkat berpindah ke banner
    check("v63-guide", "ALUR NORMAL BOOT" not in bd
          and "detail panjang: klik untuk buka" in bd, "")
    check("v39-overlay-banner-dihapus",
          "IZIN Display over other apps OFF" not in bd
          and "BUKA SETTINGS OVERLAY" not in bd, "")
    # 12. baris status ala server: LISTEN + uptime + arsip + status badge
    check("v43-stline-server", "ic('server')+'LISTEN" in bd and "uptimeStr" in bd
          and "id=stbadge" in bd and "STATUS <b" in bd, "")
    # 13. live feel: kursor blok berkedip + penanda jeda
    check("v43-live-cursor", "id=cursor" in bd and "@keyframes bl" in bd
          and "id=pausemark" in bd, "")
    # 14. v14 — filter ringkas SELALU TAMPIL: cari + level + port
    #     (tahap/status/jumlah DIHAPUS — tombol sampah, arahan owner)
    check("v43-filter-lengkap", "id=q" in bd and "id=lv" in bd
          and "id=port" in bd and "id=stage" not in bd
          and "id=limit" not in bd, "")


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
    # 2. tombol toolbar icon-only: ikon SVG digambar via paintIcons —
    #    NOL karakter emoji/teks di dalam <button>
    check("v41-icon-buttons", "id=pause class=ib" in bd
          and "id=reset class=ib" in bd and "id=dl class=ib" in bd
          and "id=cpt class=ib" in bd
          and "id=vimp" not in bd and "id=vmal" not in bd and "id=vall" not in bd
          and "id=pf" not in bd and "id=guidebtn" not in bd
          and "PENTING</button>" not in bd
          and "MASALAH</button>" not in bd and "Salin tabel</button>" not in bd, "")
    # 3. GUARD REGRESI — akar bug "log gak muncul": $('id') tanpa elemen HTML
    #    = TypeError = seluruh JS mati = poll log tidak pernah jalan.
    #    Setiap id dirujuk $('...') di JS WAJIB ada di HTML (cpcrash & stbadge
    #    dinamis — dibuat lewat innerHTML).
    js = bd[bd.find("<script>"):bd.find("</script>")]
    html = bd[:bd.find("<script>")]
    ids_js = set(re.findall(r"\$\('([A-Za-z0-9_-]+)'\)", js))
    ids_html = set(re.findall(r"id=[\"']?([A-Za-z0-9_-]+)", html))
    missing = sorted(ids_js - ids_html - set(["cpcrash", "stbadge"]))
    check("v41-js-id-semua-ada", not missing, "hilang: %s" % missing)
    # 4. crashbn + notices ada (banner crash + catatan gap/dropped)
    check("v41-crashbn-ada", "id=crashbn" in html and "id=notices" in html, "")
    check("v41-salin-icon", "id=cpt class=ib" in bd and "id=dl class=ib" in bd
          and "id=cpjs" not in bd, "")


    # ================= Bagian 51 — v14: extra server = FAKTA SAJA ==========
    # Arahan owner (25 Nov 2026): "entah mengapa masih banyak teks sampah,
    # dan gak rapi . banyak tombol juga tapi tombol sampah".
    # Semua komentar server ("GATE-PASS", "owner editable", nama kode internal,
    # penjelasan endpoint) DIHAPUS dari field extra — sisa: respCT / src= /
    # gate saat ditolak. JSON di log pretty-print.
    st, hd, bd = get400("/logs?limit=1000")
    lg = json.loads(bd)
    BANNED = ("GATE-PASS", "cointhreat", "psalmicvolleyball", "owner editable",
              "GUEST button", "REGISTER button", "LOGIN button", "dishbesideshockey",
              "loginTypes drives", "payTypes required", "GET-TEST", "try/catch safe",
              "fast-path", "anti-race", "Account Center", "honest answer",
              "uid stable across restarts")
    def _extra_of(pred):
        for e2 in lg["entries"]:
            if pred(e2):
                return e2
        return None
    e_init = _extra_of(lambda e2: e2.get("port") == 9530 and e2.get("kind") == "HTTP" and "init" in (e2.get("path") or ""))
    if e_init is not None:
        x = e_init.get("extra") or ""
        check("v51-init-extra-fakta", "src=sdk/v1/system/init.json" in x
              and "respCT=text/json" in x, x[:160])
        for w in BANNED:
            check("v51-init-no-comment " + w, w not in x, x[:160])
    e_reg = _extra_of(lambda e2: e2.get("port") == 9530 and e2.get("kind") == "HTTP" and "registerVisitor" in (e2.get("path") or ""))
    if e_reg is not None:
        x = e_reg.get("extra") or ""
        check("v51-register-extra-fakta", x.strip() == "respCT=text/json", x[:160])
    e_bin = _extra_of(lambda e2: e2.get("port") == 9527 and e2.get("kind") == "HTTP" and ".bin" in (e2.get("path") or ""))
    if e_bin is not None:
        x = e_bin.get("extra") or ""
        check("v51-bin-extra-fakta", x.startswith("src=assets | respCT=")
              or x.startswith("respCT="), x[:160])
        check("v51-bin-no-fastpath", "fast-path" not in x, x[:160])
    # tidak ada lagi teks Indonesia di pesan server (CrashHook/Overlay/Migration)
    for w in ("diganti pihak lain", "ditekan", "MIGRASI tamu", "MIGRASI akun",
              "SESI-GUARD", "tombol dashboard", "dipotong 16KB"):
        check("v51-no-indo-server " + w, not any(
            w in ((e2.get("path") or "") + (e2.get("extra") or "") + (e2.get("error") or ""))
            for e2 in lg["entries"]), "")

    # ================= Bagian 42 — popup "pengingat" DIHAPUS TOTAL ==========
    # Laporan owner (kata-kata persis):
    #  "maksud saya gini kok adaa teks popup mengingat. padahal setahu saya
    #   aslinya itu emang gak ada.. dan saya mohon jika emang gk ada jangan
    #   di paksakan untuk ada"
    # Akar (bukti smali APK v5, dilacak hari ini): parser sesi SDK
    #   psalmicvolleyball/cointhreat.cointhreat(String) membaca key "tips"
    #   BILA ADA (json.has) → feedtonight.worktopichardtails(QGUserHolder)
    #   menampilkan AlertDialog judul hw_network_dialog_title ("提醒"/
    #   Notification) berisi teks tips — syarat: authToken non-empty (selalu
    #   utk sesi sukses) + userData != null + tips != null. Response lama
    #   kita mengirim "tips":"" (string kosong, tapi BUKAN null) → popup
    #   muncul SETIAP selesai register/login. Server ASLI TIDAK PERNAH
    #   mengirim key ini (verify-artifacts live capture 20261122 = nol
    #   kemunculan "tips"). Fix v6: key tips dihapus dari sessionJsonOf.
    # GUARD REGRESI: response sesi APA PUN tidak boleh memuat key "tips".
    st, hd, bd = sdk_post("/v1/user/registerVisitor", must_params())
    check("v42-tips-tidak-ada-registervisitor", '"tips"' not in bd, bd[:120])
    st, hd, bd = sdk_post("/v1/user/autoLogin", must_params())
    check("v42-tips-tidak-ada-autologin", '"tips"' not in bd, bd[:120])
    p42 = must_params(email="baru@db.local"); p42["password"] = PW2
    st, hd, bd = sdk_post("/v1/user/loginByName", p42)
    check("v42-tips-tidak-ada-loginemail", '"tips"' not in bd, bd[:120])
finally:
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)

print("\n===== SMOKE v2: %d PASS, %d FAIL =====" % (len(PASS), len(FAIL)))
if FAIL:
    print("GAGAL:", FAIL)
    sys.exit(1)
