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
# Bagian 72 — halaman uji capture client (struktur sama index-native.html asli:
# <head>…</head> + inline script body). Demo aksi: console.*, script load
# (sukses+gagal), XHR (sukses+404), window error — semua dijalankan BODY
# SETELAH capture (injeksi </head>) terpasang.
os.makedirs(DOCROOT + "/bs/js", exist_ok=True)
open(DOCROOT + "/bs/index-native.html", "w").write(
    "<!DOCTYPE HTML>\n<html>\n<head>\n<meta charset=\"utf-8\">\n"
    "<title>Uji Capture</title>\n<style>body{background:#888}</style>\n"
    "</head>\n<body>\n<div class=\"egret-player\" data-entry-class=\"Main\"></div>\n"
    "<script>\n"
    "console.log('uji-console-log-v72', {a:1});\n"
    "console.info('uji-console-info-v72');\n"
    "console.warn('uji-console-warn-v72');\n"
    "console.error('uji-console-error-v72', 'detail-gagal');\n"
    "setTimeout(function(){\n"
    " var s1=document.createElement('script');s1.src='js/uji-ada.js';\n"
    " document.body.appendChild(s1);\n"
    " var s2=document.createElement('script');s2.src='js/uji-gagal.js';\n"
    " document.body.appendChild(s2);\n"
    " var x1=new XMLHttpRequest();x1.open('GET','uji-xhr-ok.json',true);x1.send(null);\n"
    " var x2=new XMLHttpRequest();x2.open('GET','uji-xhr-404.json',true);x2.send(null);\n"
    " setTimeout(function(){ throw new Error('uji-jserror-v72: error sengaja untuk uji capture'); }, 60);\n"
    "}, 30);\n"
    "</script>\n</body>\n</html>\n")
open(DOCROOT + "/bs/js/uji-ada.js", "w").write("// uji script load sukses\n")
open(DOCROOT + "/bs/uji-xhr-ok.json", "w").write('{"uji":true}\n')
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
# tunggu server bangun (maks 30 dtk) — /health ada di logger :4000;
# HTTPError dianggap "bangun" (rute merespons)
_dl = 30.0
while _dl > 0:
    try:
        urllib.request.urlopen("http://127.0.0.1:4000/health", timeout=2)
        break
    except urllib.error.HTTPError:
        break
    except Exception:
        time.sleep(0.5); _dl -= 0.5
else:
    print("GAGAL: server smoke tidak bangun dalam 30 dtk")
    sys.exit(1)

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
          and "MOD-LOCALSERVER v19" in bd, bd[:120])  # console v19
    check("v43-stream-markup", "id=stream" in bd and "id=tb" in bd
          and "id=tail" in bd, bd[:80])
    check("v17-salin-tabel", "function doCopy" in bd and "plainLine(e)" in bd)  # v16: salin teks mentah
    check("v17-banner-crash-js", "renderCrash" in bd and "fetch('/crash')" in bd
          and "id=crashbn" in bd)  # v16: banner crash via #crashbn (nol tombol)
    # Bagian 39 — overlay tetap INFO netral ("overlay OFF (normal)")
    check("v17-banner-overlay-js", "renderOverlay" not in bd
          and "BUKA SETTINGS OVERLAY" not in bd
          and "overlay OFF (normal)" not in bd)  # v16: baris status stbadge dihapus
    check("v19-ver-dashboard", ">v19</span>" in bd and "raw server log" in bd)  # v19

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

    # ============ v19: RESET SESI via log SYS + EXITINFO ===================
    # Bagian 43: rute sdkconfig sudah dihapus — sesi reset kini HANYA terlihat
    # sebagai entri log SYS (SESI-RESET). Panel web di port 4000 tidak ada.
    st, hd, bd = get400("/")
    check("v1-ver-dashboard", "v19" in bd and ">v19</span>" in bd, bd[:80])
    check("v41-banner-sesi-dihapus", "renderSession" not in bd and "sesbn" not in bd
          and "SESI SDK DIRESET OTOMATIS" not in bd)  # sesi reset kini hanya entri log SYS (SESI-RESET)
    check("v41-sysline-dihapus", "sysline" not in bd
          and "sdkconfig" not in bd)  # Bagian 41/43: data SDK CONFIG benar-benar keluar dari port 4000
    check("v41-banner-crash-js-masih", "renderCrash" in bd and "id=crashbn" in bd)  # v16

    # EXITINFO masuk log SYS di desktop (entri "desktop — dilewati")
    st, hd, bd = get400("/logs?limit=200")
    lg = json.loads(bd)
    # v22 (Bagian 70): entri EXIT (kind=EXIT, method=EXITINFO) kini MEMANG ada
    # di log global — uji sintetis SmokeMain (3 baris, dedup). Yang dilarang:
    # backlog gaya lama "last exit[N/M]" (bukan entri, cuma susunan ulang).
    check("v19-exitinfo-desktop", any(
        e.get("kind") == "BOOT" and "desktop — skipped" in (e.get("path") or "")
        for e in lg["entries"])
        and not any("last exit[" in (e.get("path") or "") for e in lg["entries"]),
        str(lg["entries"][:3]))

    # entri SESI-GUARD tidak boleh muncul di desktop (ctx null)
    check("v19-autoguard-desktop-skip", not any(
        e.get("method") in ("SESI-GUARD", "SESSION-GUARD") for e in lg["entries"]))

    # bootstrap: crash-handled.list boleh tidak ada, tapi unhandledCrashFiles
    # harus tidak menganggap handled file (uji tak langsung: /crash tetap
    # menampilkan crash sintetis v17 sebagai latest)
    st, hd, bd = get400("/crash")
    cj = json.loads(bd)
    check("v19-crash-route-masih", st == 200 and ("files" in cj), bd[:120])

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

    # 4. GetServerList via 9528 → Bagian 67 (oke owner "implementasi dgn ini"):
    #    ACK PENUH dari GetServerList.json (seed = serverList 1 entri lokal
    #    "DragonBall Local" + history [] + TANPA errorCode + TANPA historyMode
    #    — aturan kunci-konsumen Bagian 65/66). Envelope = 43<ackId> [{"ret":0,
    #    "data":"<JSON string>"}] TANPA compress (atlas §2/§11 baris 1).
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
            timeout=15) as r:
        plst, plbd = r.status, r.read().decode()
    # payload polling = <len>:<packet>... — packet ACK mulai "431" (echo ackId 1);
    # isi data diverifikasi setelah di-unescape (data = JSON string di envelope)
    ack_pkt = ""
    _i = 0
    while _i < len(plbd):
        _c = plbd.find(":", _i)
        if _c < 0:
            break
        try:
            _n = int(plbd[_i:_c])
        except ValueError:
            break
        _p = plbd[_c + 1:_c + 1 + _n]
        if _p.startswith("43"):
            ack_pkt = _p
            break
        _i = _c + 1 + _n
    _gs_env, _gs_data = {}, ""
    _m2 = re.match(r'^43\d+\[(.+)\]$', ack_pkt, re.S)
    if _m2:
        try:
            _gs_env = json.loads(_m2.group(1))
            _gs_data = _gs_env.get("data") or ""
        except ValueError:
            pass
    check("v67-sockio-ack-serverlist", plst == 200 and ack_pkt.startswith("431[")
          and _gs_env.get("ret") == 0 and "compress" not in _gs_env
          and '"serverList":[' in _gs_data
          and '"DragonBall Local"' in _gs_data
          and '"history":[]' in _gs_data
          and '"errorCode"' not in _gs_data
          and '"historyMode"' not in _gs_data
          and '"compress"' not in ack_pkt,
          repr(ack_pkt[:220]))

    # 5. bind label 9528 baru (BIND tercatat di BOOT — ambil arsip awal)
    st, hd, bd = get400("/logs?limit=2000")
    lg = json.loads(bd)
    check("v3-bind-label-cdn-chain", any(
        "CDN game + socket.io login" in (e.get("path") or "")
        for e in lg["entries"]), "")

    # ============ v17 — URL PENUH di log, NOL pangkas ============
    # Arahan owner (kata-kata persis):
    #  "ini log apa gk jelas , masih ada yg di sembunyikan"
    #  "kalau log nampilin url socket misal http://127.0.01:<port>/socket.io/
    #   ?EIO=3&transport=polling&t=Q8Fuv yaaa jangan anda pangkas"
    # 1. OPEN handshake 9528 di-log DENGAN URL PENUH (bukan "/socket.io/?.." telanjang)
    check("v17-url-penuh-open9528", any(
        e.get("kind") == "HTTP" and e.get("method") == "GET"
        and (e.get("path") or "").startswith(
            "http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=")
        for e in lg["entries"]),
        next((e.get("path") for e in lg["entries"]
              if "socket.io" in (e.get("path") or "")) or "tidak ada", ""))
    # 2. POST GetServerList juga URL PENUH dgn sid (host:port + query utuh)
    check("v17-url-penuh-post9528", any(
        e.get("method") == "POST"
        and (e.get("path") or "").startswith(
            "http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&sid=")
        for e in lg["entries"]), "")
    # 3. Bagian 67: ACK GetServerList tercatat VERBATIM PENUH di extra
    #    (URL penuh + frame penuh — nol pangkas; log GLOBAL 1 — tidak ada
    #    logs/ di login-server)
    ack_ent = [e for e in lg["entries"]
               if "GetServerList" in (e.get("extra") or "")]
    check("v67-ack-getserverlist-extra", bool(ack_ent)
          and any(("ACK 431 — GetServerList" in (e.get("extra") or "")
                   and '"ret":0' in (e.get("extra") or "")
                   and "frame: 431[" in (e.get("extra") or ""))
                  for e in ack_ent)
          and all("…" not in (e.get("extra") or "") for e in ack_ent),
          (ack_ent[0].get("extra") or "")[:200] if ack_ent else "tidak ada")
    # 4. WS upgrade → frame WS membawa URL PENUH koneksi (bukan "sid=.." telanjang)
    import socket as _sock, base64 as _b64
    ws = _sock.create_connection(("127.0.0.1", 5050), timeout=10)
    wskey = _b64.b64encode(os.urandom(16)).decode()
    ws.sendall(("GET /socket.io/?EIO=3&transport=websocket&t=v17ws%d HTTP/1.1\r\n"
                "Host: 127.0.0.1:5050\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n"
                % (int(time.time() * 1000) % 100000, wskey)).encode())
    wshdr = b""
    while b"\r\n\r\n" not in wshdr:
        wshdr += ws.recv(4096)
    check("v17-ws-101", b"101 Switching Protocols" in wshdr,
          wshdr[:60].decode("utf-8", "replace"))
    def _wsframe(payload):
        b = bytearray([0x81, 0x80 | len(payload)])
        mk = os.urandom(4)
        b += mk
        for i, ch in enumerate(payload):
            b.append(ch ^ mk[i & 3])
        return bytes(b)
    ws.sendall(_wsframe(b"2probe"))
    wsresp = b""
    ws.settimeout(3)
    try:
        while b"3probe" not in wsresp:
            chunk = ws.recv(4096)
            if not chunk:
                break
            wsresp += chunk
    except Exception:
        pass
    ws.close()
    check("v17-ws-3probe", b"3probe" in wsresp, repr(wsresp[:24]))
    time.sleep(0.8)
    st, hd, bd = get400("/logs?limit=600")
    lg = json.loads(bd)
    wsent = [e for e in lg["entries"] if e.get("kind") == "WS"
             and "transport=websocket" in (e.get("path") or "")]
    # upgrade + open ws-first + 2probe = minimal 3 entri WS dgn URL penuh
    check("v17-ws-url-penuh", len(wsent) >= 3, "WS entri=%d" % len(wsent))
    check("v17-ws-open-frame-dilog", any(
        (e.get("respPreview") or "").startswith("0{")
        and "pingInterval" in (e.get("respPreview") or "")
        and "sid" in (e.get("respPreview") or "") for e in wsent),
        "open ws-first harus dilog (dulu tersembunyi)")
    check("v17-ws-probe-penuh", any(
        (e.get("reqPreview") or "") == "2probe"
        and (e.get("respPreview") or "") == "3probe" for e in wsent), "")
    check("v17-ws-semua-url-penuh", all(
        (e.get("path") or "").startswith(
            "http://127.0.0.1:5050/socket.io/?EIO=3&transport=websocket")
        for e in wsent),
        next((e.get("path") for e in wsent) or "kosong", ""))
    # 5. tidak ada lagi path telanjang "/socket.io/.." di entri HTTP 5050/9528
    #    (semua wajib berawalan scheme://host:port)
    naked = [e for e in lg["entries"]
             if e.get("kind") == "HTTP" and "socket.io" in (e.get("path") or "")
             and not (e.get("path") or "").startswith("http://")]
    check("v17-nol-path-telanjang", len(naked) == 0,
          str(naked[0].get("path")[:80]) if naked else "semua URL penuh")

    # ================= Bagian 67 — LOGIN-SERVER ACK-GATE (oke owner) =================
    # Oke owner 26 Nov (kata-kata persis): "implementasi dengan ini tanpa
    # log !! log global jdi 1" — struktur = tabel event atlas dibuat file
    # (Bagian 66): 1 event = 1 file <action>.json di <serverRoot>/login-server/;
    # file kosong {} = DIAM (NO ACK — atlas §11 baris 9); SaveUserEnterInfo
    # SELALU dijawab TANPA file (konstanta — atlas §4.6/§11 baris 11); log
    # GLOBAL 1 (logs/ NDJSON — TIDAK ADA logs/ di dalam login-server/).
    LS = ROOT + "/login-server"

    def _frames(payload):
        out = []
        _i = 0
        while _i < len(payload):
            _c = payload.find(":", _i)
            if _c < 0:
                break
            try:
                _n = int(payload[_i:_c])
            except ValueError:
                break
            out.append(payload[_c + 1:_c + 1 + _n])
            _i = _c + 1 + _n
        return out

    def _sockio(obj, ack_id, port=9528):
        # sesi BARU per kasus — alur persis probe_live_login.py:
        # GET open → POST event 42<ackId> → GET poll (terima 43<ackId>)
        _base = "http://127.0.0.1:%d" % port
        with urllib.request.urlopen(_base + "/socket.io/?EIO=3&transport=polling"
                                    + "&t=v67%d%d" % (port, int(time.time() * 100000) % 100000000),
                                    timeout=10) as r:
            _op = r.read().decode()
        _m = re.search(r'"sid":"([A-Za-z0-9]+)"', _op)
        _s2 = _m.group(1) if _m else ""
        _pkt = '42%d["handler.process",%s]' % (ack_id, json.dumps(obj, separators=(",", ":")))
        _body = "%d:%s" % (len(_pkt), _pkt)
        _req = urllib.request.Request(
            _base + "/socket.io/?EIO=3&transport=polling&sid=" + _s2,
            data=_body.encode(), method="POST")
        _req.add_header("Content-Type", "text/plain; charset=UTF-8")
        with urllib.request.urlopen(_req, timeout=10) as r:
            r.read()
        with urllib.request.urlopen(
                _base + "/socket.io/?EIO=3&transport=polling&sid=" + _s2,
                timeout=15) as r:
            _pl = r.read().decode()
        _acks = [p for p in _frames(_pl) if p.startswith("43")]
        return _acks[0] if _acks else ""

    def _data(ack_pkt):
        # envelope 43<id>[{"ret":0,"data":"<JSON string>"}] → dict data
        # (parse 2x: envelope JSON, lalu string data JSON di dalamnya)
        _m = re.match(r'^43\d+\[(.+)\]$', ack_pkt, re.S)
        if not _m:
            return None
        try:
            _env = json.loads(_m.group(1))
        except ValueError:
            return None
        if not isinstance(_env, dict) or _env.get("ret") != 0 or "compress" in _env:
            return None
        try:
            return json.loads(_env.get("data") or "{}")
        except ValueError:
            return None

    # 0. seed BOOT — 5 file event PERSIS; TANPA data/ & logs/; tanpa nama lama
    #    yang DITOLAK owner (config.json/serverlist.json/announce.json)
    _names = sorted(os.listdir(LS)) if os.path.isdir(LS) else []
    check("v67-seed-5file", _names == ["GetServerList.json", "LoginAnnounce.json",
          "SaveHistory.json", "SaveLanguage.json", "loginGame.json"], str(_names))
    check("v67-tanpa-logs-dir", not os.path.isdir(LS + "/logs")
          and not os.path.isdir(LS + "/data"), str(_names))
    check("v67-tanpa-nama-lama", all(not os.path.exists(LS + "/" + _n) for _n in
          ("config.json", "serverlist.json", "announce.json", "errors.json")), "")

    # 1. loginGame sukses (seed) → ECHO userId+channelCode (atlas §4.2 — dua
    #    kunci itu SATU-SATUNYA yang dibaca client; errorCode tidak dikirim)
    _d = _data(_sockio({"type": "User", "action": "loginGame", "userId": "tester01",
                        "password": "game_origin", "fromChannel": "game_origin",
                        "channelName": "", "headImageUrl": "", "nickName": "",
                        "subChannel": "", "version": "1.0"}, 0))
    check("v67-logingame-echo", _d == {"userId": "tester01", "channelCode": "game_origin"},
          json.dumps(_d) if _d else "no ack")

    # 2. loginGame tolak103 (K2 dibalik OWNER via edit file) → identitas kosong
    open(LS + "/loginGame.json", "w").write('{\n  "jawab": "tolak103"\n}\n')
    _d = _data(_sockio({"type": "User", "action": "loginGame", "userId": "tester01",
                        "fromChannel": "game_origin"}, 1))
    check("v67-logingame-tolak103", _d == {"userId": "", "channelCode": ""},
          json.dumps(_d) if _d else "no ack")
    os.remove(LS + "/loginGame.json")   # hapus file = seed default kembali

    # 3. SaveLanguage — whitelist seed ["en","fr","kr"] (K5): "in" → 111,
    #    "en" → 0 (parser MEMBACA errorCode — atlas §4.5)
    _d = _data(_sockio({"type": "User", "action": "SaveLanguage",
                        "userid": "1700000000001", "sdk": "quick",
                        "appid": "", "language": "in"}, 2))
    check("v67-savelanguage-111", _d and _d.get("errorCode") == 111,
          json.dumps(_d) if _d else "no ack")
    _d = _data(_sockio({"type": "User", "action": "SaveLanguage",
                        "userid": "1700000000001", "sdk": "quick",
                        "appid": "", "language": "en"}, 3))
    check("v67-savelanguage-0", _d and _d.get("errorCode") == 0,
          json.dumps(_d) if _d else "no ack")

    # 4. SaveUserEnterInfo — SELALU dijawab {} TANPA file (konstanta kode,
    #    atlas §4.6/§11 baris 11) — frame EXACT + file tidak pernah dibuat
    _ack = _sockio({"type": "User", "action": "SaveUserEnterInfo",
                    "accountToken": "1700000000001", "channelCode": "quick",
                    "subChannel": "", "createTime": 1795625000,
                    "userLevel": 1, "version": "1.0"}, 4)
    check("v67-saveuserenterinfo-frame", _ack.endswith('[{"ret":0,"data":"{}"}]')
          and _ack.startswith("434["), repr(_ack[:80]))
    check("v67-saveuserenterinfo-tanpa-file",
          not os.path.exists(LS + "/SaveUserEnterInfo.json"), "")

    # 5. SaveHistory (seed) → {"todayLoginCount":1} TANPA loginToken ("" = tak
    #    dikirim — pintu injeksi §11.7 tetap tertutup); errorCode tidak dikirim
    _d = _data(_sockio({"type": "User", "action": "SaveHistory",
                        "accountToken": "1700000000001", "channelCode": "quick",
                        "serverId": "1", "securityCode": "", "subChannel": "",
                        "version": "1.0"}, 5))
    check("v67-savehistory-ack", _d == {"todayLoginCount": 1},
          json.dumps(_d) if _d else "no ack")

    # 6. loginToken ISI → pintu injeksi TERBUKA (owner edit file — K6)
    open(LS + "/SaveHistory.json", "w").write(
        '{\n  "todayLoginCount": 1,\n  "loginToken": "TOKEN-COBA-V67"\n}\n')
    _d = _data(_sockio({"type": "User", "action": "SaveHistory",
                        "accountToken": "1700000000001", "channelCode": "quick",
                        "serverId": "1"}, 6))
    check("v67-savehistory-logintoken",
          _d and _d.get("todayLoginCount") == 1
          and _d.get("loginToken") == "TOKEN-COBA-V67",
          json.dumps(_d) if _d else "no ack")
    open(LS + "/SaveHistory.json", "w").write(
        '{\n  "todayLoginCount": 1,\n  "loginToken": ""\n}\n')  # restore seed

    # 7. LoginAnnounce seed {} → DIAM (NO ACK — atlas §11 baris 9; poll 10 dtk
    #    tanpa data; visual = diam, kontrak getNotice §4.4)
    _ack = _sockio({"type": "User", "action": "LoginAnnounce"}, 0)
    check("v67-announce-diam", _ack == "", repr(_ack[:80]))

    # 8. action di luar atlas → NO ACK (6 event TOTAL — atlas §11 baris 13)
    _ack = _sockio({"type": "User", "action": "FooBar", "userId": "x"}, 0)
    check("v67-unknown-action-noack", _ack == "", repr(_ack[:80]))

    # 9. file event DIKOSONGKAN {} oleh owner → event DIAM; lalu file DIHAPUS
    #    → seed default kembali (aturan folder Bagian 66)
    open(LS + "/GetServerList.json", "w").write("{}")
    _ack = _sockio({"type": "User", "action": "GetServerList",
                    "userId": "1700000000001", "channel": "quick"}, 7)
    check("v67-getserverlist-kosong-diam", _ack == "", repr(_ack[:80]))
    os.remove(LS + "/GetServerList.json")
    _d = _data(_sockio({"type": "User", "action": "GetServerList",
                        "userId": "1700000000001", "channel": "quick"}, 8))
    check("v67-hapus-file-reseed", bool(_d) and _d.get("serverList")
          and _d["serverList"][0].get("name") == "DragonBall Local"
          and _d.get("history") == []
          and os.path.exists(LS + "/GetServerList.json"),
          json.dumps(_d)[:160] if _d else "no ack")
    # loginGame.json (dihapus di kasus 2) di-reseed lazy oleh event berikutnya
    _d = _data(_sockio({"type": "User", "action": "loginGame", "userId": "tester01",
                        "fromChannel": "game_origin"}, 12))
    check("v67-hapus-logingame-reseed", bool(_d)
          and _d.get("userId") == "tester01"
          and os.path.exists(LS + "/loginGame.json"),
          json.dumps(_d) if _d else "no ack")

    # 10. file RUSAK → reseed + WARN (aturan folder 4); SaveLanguage kembali
    #     seed → "en" diterima (errorCode 0)
    open(LS + "/SaveLanguage.json", "w").write("SAMPAH{{{ bukan json")
    _d = _data(_sockio({"type": "User", "action": "SaveLanguage",
                        "userid": "1700000000001", "sdk": "quick",
                        "appid": "", "language": "en"}, 9))
    check("v67-corrupted-reseed", _d and _d.get("errorCode") == 0
          and '"whitelist"' in open(LS + "/SaveLanguage.json").read(),
          json.dumps(_d) if _d else "no ack")

    # 11. historyMode persist (K3): SaveHistory → tulis history.sqlite;
    #     GetServerList → history dari DB. Desktop JVM tanpa android sqlite =
    #     no-op + WARN sekali (device = sqlite bawaan, kriteria §3.6 no. 5)
    _gs = json.load(open(LS + "/GetServerList.json"))
    _gs["historyMode"] = "persist"
    json.dump(_gs, open(LS + "/GetServerList.json", "w"), indent=2)
    _d = _data(_sockio({"type": "User", "action": "SaveHistory",
                        "accountToken": "777000", "channelCode": "quick",
                        "serverId": "1"}, 10))
    check("v67-persist-savehistory", _d == {"todayLoginCount": 1},
          json.dumps(_d) if _d else "no ack")
    _d = _data(_sockio({"type": "User", "action": "GetServerList",
                        "userId": "777000", "channel": "quick"}, 11))
    check("v67-persist-getserverlist-desktop", bool(_d) and _d.get("history") == [],
          json.dumps(_d)[:160] if _d else "no ack")
    os.remove(LS + "/GetServerList.json")   # balik seed (auto)

    # 12. listener GAME 5051 = TRANSPORT ONLY (file login ADA pun tetap NO ACK)
    _ack = _sockio({"type": "user", "action": "enterGame", "userId": "1700000000001",
                    "serverId": 1, "version": "1.0"}, 0, port=5051)
    check("v67-game-5051-transport-only", _ack == "", repr(_ack[:80]))

    # 13. LOG GLOBAL 1 — semua jejak login di logs/ GLOBAL, NOL file log baru
    #     di login-server/; penanda di entri logger
    st, hd, bd = get400("/logs?limit=4000")
    lg = json.loads(bd)
    _extras = [e.get("extra") or "" for e in lg["entries"]]
    check("v67-log-ack-userenterinfo", any(
        "ACK 434 — SaveUserEnterInfo" in x and "konstanta" in x for x in _extras), "")
    check("v67-log-diam-kosong", any(
        "kosong {} = DIAM" in x for x in _extras), "")
    check("v67-log-corrupted-warn", any(
        "LOGIN-SERVER corrupted: SaveLanguage.json" in (e.get("path") or "")
        for e in lg["entries"]), "")
    check("v67-log-history-warn-desktop", any(
        "LOGIN-HISTORY tidak tersedia" in (e.get("path") or "")
        for e in lg["entries"]), "")
    check("v67-log-seed-boot", any(
        "LOGIN-SERVER seed: GetServerList.json" in (e.get("path") or "")
        and e.get("kind") == "SYS" for e in lg["entries"]), "")
    check("v67-tanpa-log-baru-di-loginserver", not os.path.isdir(LS + "/logs"),
          "logs/ di login-server tidak pernah dibuat (log global 1)")

    # ================= Bagian 69 — FIX SERVERLIST (KASUS DEVICE) + LOG PER-OPEN =================
    # Laporan owner 26 Nov malam (kata-kata persis): "1. serverlist gak muncul
    # . tapi log lancar semua jelas ini kesalahan pada log anda yg kek tai"
    # Bukti device v20 (release-mod/log-20261126-181501.ndjson):
    #   seq 58 WS upgrade 101 @13.836 → seq 59 POST emit GetServerList @13.839
    #   (ACK 430 dibangun — tapi v20 menulisnya ke socket WS PRA-PROBE →
    #   dibuang client yang masih polling) → seq 60 2probe/3probe @13.964 →
    #   seq 61 poll 10 dtk KOSONG @23.855 → ACK hilang → serverlist tak muncul.
    # Dulu smoke hanya polling murni (upgrade WS tak pernah dilatih bareng
    # emit) — KASUS PERSIS DEVICE diuji sekarang, urutan identik.
    import socket as _v69sock, base64 as _v69b64

    def _wsframe69(payload):
        b = bytearray([0x81, 0x80 | len(payload)])
        mk = os.urandom(4)
        b += mk
        for i, ch in enumerate(payload):
            b.append(ch ^ mk[i & 3])
        return bytes(b)

    def _wsread69(s_, want, tmo=6):
        s_.settimeout(tmo)
        buf = b""
        try:
            while want not in buf:
                chunk = s_.recv(4096)
                if not chunk:
                    break
                buf += chunk
        except Exception:
            pass
        return buf

    # 1. polling open di 9528 (persis device: serversetting loginserver=9528)
    with urllib.request.urlopen("http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=v69a", timeout=10) as r:
        openbd69 = r.read().decode()
    _ms69 = re.search(r'"sid":"([A-Za-z0-9]+)"', openbd69)
    s69 = _ms69.group(1) if _ms69 else ""
    check("v69-open-9528-sid", len(s69) == 20, openbd69[:120])

    # 2. WS upgrade DGN sid yang sama (persis device) — v21: TIDAK dianggap upgraded
    ws69 = _v69sock.create_connection(("127.0.0.1", 9528), timeout=10)
    wskey69 = _v69b64.b64encode(os.urandom(16)).decode()
    ws69.sendall(("GET /socket.io/?EIO=3&transport=websocket&sid=" + s69 + "&t=v69ws HTTP/1.1\r\n"
                  "Host: 127.0.0.1:9528\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  "Sec-WebSocket-Key: " + wskey69 + "\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    wshdr69 = b""
    while b"\r\n\r\n" not in wshdr69:
        wshdr69 += ws69.recv(4096)
    check("v69-ws-101-same-sid", b"101 Switching Protocols" in wshdr69,
          wshdr69[:60].decode("utf-8", "replace"))

    # 3. POST emit GetServerList via polling SEBELUM probe (urutan persis device)
    msg69 = json.dumps({"type": "User", "action": "GetServerList",
                        "userId": "1795691706847", "subChannel": "",
                        "channel": "quick"}, separators=(",", ":"))
    pkt69 = '421["handler.process",' + msg69 + ']'
    body69 = "%d:%s" % (len(pkt69), pkt69)
    req69 = urllib.request.Request(
        "http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&sid=" + s69,
        data=body69.encode(), method="POST")
    req69.add_header("Content-Type", "text/plain; charset=UTF-8")
    with urllib.request.urlopen(req69, timeout=10) as r:
        post69 = r.read().decode()
    check("v69-post-ok", post69 == "ok", repr(post69[:40]))

    # 4. poll SEGERA harus membawa ACK 431 (dulu: hilang — poll 10 dtk kosong)
    t69 = time.time()
    with urllib.request.urlopen(
            "http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&sid=" + s69,
            timeout=15) as r:
        pl69 = r.read().decode()
    dt69 = time.time() - t69
    _ack69 = ""
    _i69 = 0
    while _i69 < len(pl69):
        _c69 = pl69.find(":", _i69)
        if _c69 < 0:
            break
        try:
            _n69 = int(pl69[_i69:_c69])
        except ValueError:
            break
        _p69 = pl69[_c69 + 1:_c69 + 1 + _n69]
        if _p69.startswith("43"):
            _ack69 = _p69
            break
        _i69 = _c69 + 1 + _n69
    check("v69-ack-dipolling-cepat", _ack69.startswith("431[") and dt69 < 3.0,
          "dt=%.1fs ack=%r" % (dt69, _ack69[:70]))
    # isi ACK diverifikasi SETELAH envelope di-parse (data = JSON string
    # ter-escape — substring mentah \"DragonBall Local\" tidak mungkin cocok
    # di frame mentah; pola sama dgn uji v67)
    _env69, _data69 = {}, ""
    _m69 = re.match(r'^43\d+\[(.+)\]$', _ack69, re.S)
    if _m69:
        try:
            _env69 = json.loads(_m69.group(1))
            _data69 = _env69.get("data") or ""
        except ValueError:
            pass
    check("v69-ack-isi-serverlist", _env69.get("ret") == 0
          and '"DragonBall Local"' in _data69
          and '"serverList":[' in _data69,
          repr(_ack69[:140]))

    # 5. probe WS (2probe→3probe tetap bekerja pada sesi yang sama)
    ws69.sendall(_wsframe69(b"2probe"))
    probe69 = _wsread69(ws69, b"3probe")
    check("v69-ws-probe", b"3probe" in probe69, repr(probe69[:40]))

    # 6. kirim '5' = upgrade RESMI → noop '6' ditutupkan ke antre polling
    ws69.sendall(_wsframe69(b"5"))
    time.sleep(0.5)
    t69b = time.time()
    with urllib.request.urlopen(
            "http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&sid=" + s69,
            timeout=15) as r:
        noop69 = r.read().decode()
    dtn69 = time.time() - t69b
    check("v69-upgrade-noop-poll", noop69.strip() == "1:6" and dtn69 < 3.0,
          "dt=%.1fs body=%r" % (dtn69, noop69[:40]))

    # 7. emit via WS SETELAH upgrade → ACK lewat WS langsung (frame di socket WS)
    pktws69 = '422["handler.process",' + json.dumps(
        {"type": "User", "action": "loginGame", "userId": "v69wsuser",
         "channelCode": "quick"}, separators=(",", ":")) + ']'
    ws69.sendall(_wsframe69(pktws69.encode()))
    wsack69 = _wsread69(ws69, b"432")
    check("v69-ws-emit-ack-ws-langsung", b"432[" in wsack69
          and b'"ret":0' in wsack69 and b"v69wsuser" in wsack69,
          repr(wsack69[:90]))
    ws69.close()
    time.sleep(0.8)

    # ================= Bagian 70 — LOG = 1 FILE DI-TUMPUK (append lintas sesi) =================
    # Arahan owner (kata-kata persis): "ANDA KOK NGEYEL SIH !! ITU LOG KENAPA
    # MASIH ADA BANYAK TIAP MASUK APLIKASI BIKIN LOG BARU LGI ! KEK TAI ! LOG
    # ITU 1 FILE DI TUMPUK !! BUKAN MALAH BIKIN BARU TIAP MASUK APLIKASI!!"
    # v22: SATU file logs/server.ndjson, nama TETAP — tiap open APK APPEND ke
    # file yang SAMA (simulasi open ke-2 ada di SmokeMain: 2 baris sesi lama
    # ditulis SEBELUM DiskWriter.init, lalu init harus menambah, bukan menimpa).
    sl70 = os.path.join(ROOT, "logs", "server.ndjson")
    nd70 = sorted(f for f in os.listdir(ROOT + "/logs") if f.endswith(".ndjson"))
    check("v70-log-satu-file", nd70 == ["server.ndjson"],
          "file ndjson di logs/: %s" % nd70)
    st, hd, bd = get400("/logs?limit=4000")   # dibutuhkan uji v69 log jujur di bawah
    lg = json.loads(bd)
    check("v70-tanpa-log-timestamp", not any(
        f.startswith("log-") for f in os.listdir(ROOT + "/logs")),
        "v18-v21 membuat log-<waktu-open>.ndjson — v22 TIDAK boleh")
    check("v70-tanpa-exit-history", not os.path.exists(
        os.path.join(ROOT, "logs", "exit-history.ndjson")),
        "file terpisah exit-history.ndjson DIHAPUS dari desain (semuanya 1 file)")
    sl_txt70 = open(sl70, encoding="utf-8", errors="replace").read() \
        if os.path.isfile(sl70) else ""
    i_lama70 = sl_txt70.find("uji-sesi-lama-baris-1")
    i_baru70 = sl_txt70.find("uji-append-smoke-v22-sesi-baru")
    check("v70-append-lama-dan-baru-1file", i_lama70 >= 0 and i_baru70 > i_lama70,
          "sesi-lama@%d sesi-baru@%d — baris lama HARUS selamat (append, bukan overwrite)"
          % (i_lama70, i_baru70))
    i_lama2_70 = sl_txt70.find("uji-sesi-lama-baris-2")
    check("v70-append-dua-baris-lama", i_lama2_70 >= 0 and i_lama2_70 < i_baru70,
          "lama2@%d" % i_lama2_70)

    # entri EXIT di log global + DEDUP: 4 panggilan appendExitEntry → 3 baris
    # (EXIT_SELF duplikat exitTime TIDAK ditulis ulang); exit abnormal (CRASH)
    # membawa level ERROR (LOG NATURAL — error ya error), normal tanpa error.
    exit70 = [ln for ln in sl_txt70.splitlines() if '"kind":"EXIT"' in ln]
    check("v70-exit-3baris-dedup", len(exit70) == 3,
          "jumlah entri EXIT=%d (harus 3 — 1 duplikat skip)" % len(exit70))
    check("v70-exit-dup-skip", sum(
        1 for ln in exit70 if "desc=uji-exit-smoke-v22 " in ln) == 1,
        "exitTime sama TIDAK boleh tercatat dua kali")
    check("v70-exit-abnormal-level-error", any(
        '"abnormal exit: CRASH"' in ln and '"level":"ERROR"' in ln for ln in exit70),
        "exit CRASH/ANR/LOW_MEMORY/SIGNALED = ERROR (LOG NATURAL)")
    check("v70-exit-normal-tanpa-error", any(
        '"path":"sesi sebelumnya berakhir: reason=EXIT_SELF' in ln
        and '"level"' not in ln for ln in exit70),
        "exit normal (EXIT_SELF) = tanpa level ERROR")
    check("v70-exit-trace-di-log", any(
        "trace-crash-uji-v22" in ln for ln in exit70),
        "trace exit masuk respPreview entri EXIT di log global")

    # /download memantul pada 1 file: list = server.ndjson saja
    st, hd, bd = get400("/download")
    dj70 = json.loads(bd)
    check("v70-dl-list-1file", dj70.get("files") and len(dj70["files"]) == 1
          and dj70["files"][0]["name"] == "server.ndjson", str(dj70)[:160])
    st, hd, bd = get400("/download?latest=1")
    check("v70-dl-latest", st == 200 and "uji-sesi-lama-baris-1" in bd,
          ("status=%s" % st))
    st, hd, bd = get400("/download?file=server.ndjson")
    check("v70-dl-file-server-ndjson", st == 200
          and "uji-append-smoke-v22-sesi-baru" in bd, ("status=%s" % st))

    # ============ Bagian 70 — 404 NATURAL: ERROR YA ERROR, NOL PLACEHOLDER ============
    # Arahan owner (kata-kata persis): "JANGAN PERNAH KASIHS PLACEHOLDER !!
    # KALAU ERROR YA ERROR !! ... BIAR JELAS KITA ITU KURANGNYA DIMANAAA"
    # Path diambil PERSIS bukti device v20 (log-20261126-181501.ndjson seq 51-57):
    # bs/Instagram_png dkk — tidak pernah ada di APK/all.zip → 404 tetap 404.
    def get9528(path):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9528" + path,
                                        timeout=10) as r:
                return r.status, r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e2:
            return e2.code, e2.read().decode("utf-8", "replace")
    st, bd = get9528("/bs/Instagram_png")
    check("v70-404-tetap-404", st == 404 and "404 not found: /bs/Instagram_png" in bd,
          "status=%s body=%r" % (st, bd[:60]))
    # entri 404 ditulis async (DiskWriter thread) — poll maks 5 dtk sampai flush
    line404 = []
    for _i70 in range(100):
        sl_txt70 = open(sl70, encoding="utf-8", errors="replace").read()
        line404 = [ln for ln in sl_txt70.splitlines()
                   if "/bs/Instagram_png" in ln and '"status":404' in ln]
        if line404:
            break
        time.sleep(0.05)
    check("v70-404-di-log-dengan-level-error", len(line404) >= 1
          and '"level":"ERROR"' in line404[-1]
          and "404 MISSING" in line404[-1]
          and "tidak ada di server/" in line404[-1],
          line404[-1][-260:] if line404 else "tidak ada baris 404 Instagram_png")
    # entri 200 normal TANPA level (access log alami — bukan semua entry diberi label)
    line200 = [ln for ln in sl_txt70.splitlines()
               if "clientversion.json" in ln and '"status":200' in ln]
    check("v70-200-tanpa-level", len(line200) >= 1
          and '"level"' not in line200[-1],
          line200[-1][-160:] if line200 else "tidak ada baris 200 clientversion.json")

    # ================= Bagian 71 — LOG NATURAL FASE LOADING (socket game) =================
    # Laporan owner (kata-kata persis): "heee itu hlo loading tanpa ada
    # keterangan apapun di log yg sudah saya update !!!" + "KALAU ERROR YA
    # ERROR ! KALAU KURANG YAA KURANG ! BIAR JELAS KITA ITU KURANGNYA DIMANAAA"
    # Bukti device v22 (release-mod/server.ndjson owner): login BERES (ACK 430
    # serverlist + ACK 431 SaveHistory SAMPAI ke client), lalu client buka
    # sesi ke-2 (socket game, serverItem.url=9528), probe WS, lalu DIAM 10+
    # dtk — log v22 hanya "long-poll timeout, no data" TANPA keterangan, dan
    # WS putus (IOException) ditelan SENYAP total. v23: setiap titik buta
    # kini diberi fakta apa adanya (nol karangan).
    def _waitrow71(pred, tries=100):
        for _i71 in range(tries):
            txt71 = open(sl70, encoding="utf-8", errors="replace").read()
            cand71 = [ln for ln in txt71.splitlines() if pred(ln)]
            if cand71:
                return cand71[-1]
            time.sleep(0.05)
        return ""

    # 1. sesi login pertama + emit GetServerList + poll → ACK SAMPAI
    with urllib.request.urlopen("http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=v71a", timeout=10) as r:
        openbd71a = r.read().decode()
    _m71a = re.search(r'"sid":"([A-Za-z0-9]+)"', openbd71a)
    s71a = _m71a.group(1) if _m71a else ""
    pkt71a = '421["handler.process",' + json.dumps(
        {"type": "User", "action": "GetServerList", "userId": "v71user",
         "subChannel": "", "channel": "quick"}, separators=(",", ":")) + ']'
    req71a = urllib.request.Request(
        "http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&sid=" + s71a,
        data=("%d:%s" % (len(pkt71a), pkt71a)).encode(), method="POST")
    req71a.add_header("Content-Type", "text/plain; charset=UTF-8")
    with urllib.request.urlopen(req71a, timeout=10) as r:
        check("v71-post-ok", r.read().decode() == "ok", "")
    with urllib.request.urlopen(
            "http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&sid=" + s71a,
            timeout=15) as r:
        pl71a = r.read().decode()
    check("v71-ack-di-poll", "43" in pl71a[:8] or ":431[" in pl71a or pl71a.startswith("4:431["),
          repr(pl71a[:60]))
    row71 = _waitrow71(lambda ln: '"berisi: ACK' in ln and "GetServerList" in ln)
    check("v71-serverlist-sampai", "serverlist SAMPAI ke client" in row71
          and "berisi: ACK" in row71,
          row71[-240:] if row71 else "baris berisi ACK GetServerList tidak ada")

    # 2. OPEN sesi ke-2 → keterangan "sesi aktif lain" + pola socket GAME
    with urllib.request.urlopen("http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=v71b", timeout=10) as r:
        openbd71 = r.read().decode()
    _ms71 = re.search(r'"sid":"([A-Za-z0-9]+)"', openbd71)
    s71 = _ms71.group(1) if _ms71 else ""
    row_open71 = _waitrow71(lambda ln: ('open sid=' + s71) in ln)
    check("v71-open-sesi-2-fakta-lain", ("sesi aktif lain" in row_open71
          and ("sid=" + s71a) in row_open71 and "GetServerList" in row_open71),
          row_open71[-260:] if row_open71 else "baris open sesi ke-2 tidak ada")
    check("v71-open-sesi-2-pola-socket-game", ("pola socket GAME" in row_open71
          and "'verify'" in row_open71 and "gameserver belum dibangun" in row_open71),
          row_open71[-260:] if row_open71 else "")

    # 3. noop '6' pasca-upgrade (dari sesi v69) kini diberi keterangan
    row_noop71 = _waitrow71(lambda ln: '"berisi: noop 6' in ln)
    check("v71-noop-berisi", "upgrade selesai — transport pindah WS" in row_noop71,
          row_noop71[-220:] if row_noop71 else "baris noop 6 tidak ada")

    # 4. WS close frame → baris close dengan fakta sesi
    ws71c = _v69sock.create_connection(("127.0.0.1", 9528), timeout=10)
    wskey71 = _v69b64.b64encode(os.urandom(16)).decode()
    ws71c.sendall(("GET /socket.io/?EIO=3&transport=websocket&sid=" + s71
                   + "&t=v71ws HTTP/1.1\r\nHost: 127.0.0.1:9528\r\nUpgrade: websocket\r\n"
                   "Connection: Upgrade\r\nSec-WebSocket-Key: " + wskey71
                   + "\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    wshdr71 = b""
    while b"\r\n\r\n" not in wshdr71:
        wshdr71 += ws71c.recv(4096)
    ws71c.sendall(_wsframe69(b"2probe"))
    _wsread69(ws71c, b"3probe")
    _cl71 = bytearray([0x88, 0x80 | 2]); _mk71 = os.urandom(4)
    _cl71 += _mk71
    for _i71, _ch71 in enumerate(b"\x03\xe8"):
        _cl71.append(_ch71 ^ _mk71[_i71 & 3])
    ws71c.sendall(bytes(_cl71))
    time.sleep(0.8)
    row_close71 = _waitrow71(lambda ln: '"status":1000' in ln
                             and ('sid=' + s71) in ln)
    check("v71-ws-close-fakta", ("WS close dari client" in row_close71
          and "diterima" in row_close71 and "upgrade WS belum" in row_close71),
          row_close71[-240:] if row_close71 else "baris close tidak ada")

    # 5. EOF/putus TANPA close frame → baris "WS berakhir" (dulu: senyap total)
    ws72 = _v69sock.create_connection(("127.0.0.1", 9528), timeout=10)
    wskey72 = _v69b64.b64encode(os.urandom(16)).decode()
    ws72.sendall(("GET /socket.io/?EIO=3&transport=websocket&t=v71ws2 HTTP/1.1\r\n"
                  "Host: 127.0.0.1:9528\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  "Sec-WebSocket-Key: " + wskey72 + "\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    wshdr72 = b""
    while b"\r\n\r\n" not in wshdr72:
        wshdr72 += ws72.recv(4096)
    ws72.sendall(_wsframe69(b"2probe"))
    _wsread69(ws72, b"3probe")
    ws72.close()   # TCP close TANPA close frame → EOF di server
    time.sleep(0.8)
    row_eof71 = _waitrow71(lambda ln: "WS berakhir (EOF/koneksi putus" in ln)
    check("v71-ws-eof-tidak-senyap", ("WS berakhir" in row_eof71
          and "hanya probe WS" in row_eof71),
          row_eof71[-240:] if row_eof71 else "baris WS berakhir tidak ada")

    # 6. long-poll timeout → fakta sesi: probe jalan tapi '5' tak pernah datang
    with urllib.request.urlopen("http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&t=v71c", timeout=10) as r:
        openbd71b = r.read().decode()
    _ms71b = re.search(r'"sid":"([A-Za-z0-9]+)"', openbd71b)
    s71b = _ms71b.group(1) if _ms71b else ""
    ws73 = _v69sock.create_connection(("127.0.0.1", 9528), timeout=10)
    wskey73 = _v69b64.b64encode(os.urandom(16)).decode()
    ws73.sendall(("GET /socket.io/?EIO=3&transport=websocket&sid=" + s71b
                  + "&t=v71ws3 HTTP/1.1\r\nHost: 127.0.0.1:9528\r\nUpgrade: websocket\r\n"
                  "Connection: Upgrade\r\nSec-WebSocket-Key: " + wskey73
                  + "\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    wshdr73 = b""
    while b"\r\n\r\n" not in wshdr73:
        wshdr73 += ws73.recv(4096)
    ws73.sendall(_wsframe69(b"2probe"))   # probe TANPA '5' — persis device v22
    _wsread69(ws73, b"3probe")
    t71 = time.time()
    with urllib.request.urlopen(
            "http://127.0.0.1:9528/socket.io/?EIO=3&transport=polling&sid=" + s71b,
            timeout=15) as r:
        pl71b = r.read().decode()
    dt71 = time.time() - t71
    row_to71 = _waitrow71(lambda ln: '"long-poll timeout 10 dtk' in ln and s71b in ln)
    check("v71-poll-timeout-fakta", (dt71 >= 9.0 and row_to71 != ""
          and "umur" in row_to71 and "tidak pernah mengirim '5'" in row_to71),
          "dt=%.1fs %s" % (dt71, row_to71[-220:] if row_to71 else "baris timeout tidak ada"))

    # 7. seq = urutan tulis file (monotonik — dulu swap 59/60 di log device v22)
    #    Catatan: 2 baris pertama = simulasi sesi lama milik uji v70 (seq 1-2,
    #    ditulis SmokeMain sebelum init) — seq adalah per-proses dan BOOT
    #    menandai batas sesi (desain v22). Yang diuji: DALAM sesi baru,
    #    urutan baris di file == urutan seq (nol swap, nol duplikat).
    seqs71 = []
    for ln in open(sl70, encoding="utf-8", errors="replace").read().splitlines():
        m71 = re.match(r'^\{"seq":(\d+)', ln)
        if m71:
            seqs71.append(int(m71.group(1)))
    check("v71-seq-monotonik", len(seqs71) >= 3
          and seqs71[:2] == [1, 2]
          and seqs71[2:] == sorted(seqs71[2:])
          and len(set(seqs71[2:])) == len(seqs71[2:]),
          "n=%d sim=%s naik=%s unik=%s" % (len(seqs71), seqs71[:2],
                                           seqs71[2:] == sorted(seqs71[2:]),
                                           len(set(seqs71[2:])) == len(seqs71[2:])))

    # log jujur — extra mencatat transport pengiriman ACK yang sebenarnya
    _v69acks = [e for e in lg["entries"]
                if (e.get("extra") or "").find("ACK 431 — GetServerList") >= 0
                and "dikirim" in (e.get("extra") or "")]
    check("v69-log-dikirim-polling", len(_v69acks) >= 1
          and "→ dikirim: antre polling" in _v69acks[-1].get("extra"),
          (_v69acks[-1].get("extra") or "")[-110:] if _v69acks else "tidak ada")
    check("v69-log-dikirim-ws", any(
        (e.get("extra") or "").find("ACK 432 — loginGame") >= 0
        and "→ dikirim: WS langsung" in (e.get("extra") or "")
        for e in lg["entries"]), "")
    check("v69-log-upgrade-5", any(
        e.get("kind") == "WS" and "upgrade selesai" in (e.get("extra") or "")
        for e in lg["entries"]), "")
    # port di log = listener sebenarnya (dulu entri 9528 salah label 5050)
    _ent9528 = [e for e in lg["entries"]
                if "127.0.0.1:9528/socket.io/" in (e.get("path") or "")]
    check("v69-port-9528-benar", len(_ent9528) > 0
          and all(e.get("port") == 9528 for e in _ent9528),
          "entri=%d salah_port=%d" % (len(_ent9528),
          sum(1 for e in _ent9528 if e.get("port") != 9528)))

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
          and "DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v19" in bd, "")
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
    check("v49-trunc", "max-height:300px" in bd and "overflow-y:auto" in bd)  # v16: body panjang menggulir
    check("v60-sub-style", ".dtl{margin-top:7px" in bd
          and ".ln.r .dtl{border-left-color:#3a2026}" in bd
          and "max-height:300px" in bd, "")  # v16: 300px
    # v14 — detail kolaps: header .sh klik + isi .sc tersembunyi default
    check("v14-sub-collapse", ".sh{display:flex" not in bd
          and ".sc{display:none" not in bd and "data-o=" not in bd
          and ".dtl{" in bd)  # v15/v16: kolaps dihapus — rincian langsung tampil
    check("v14-sub-sum", "id=sum>" in bd and "id=ftr" in bd
          and "terakhir penting:" in bd, "")  # v16: footer ringkas
    # 5. salin log = teks mentah persis protokol (time #seq port METHOD path status)
    check("v63-salin-raw", "'  headers: '+e.headers" in bd
          and "'  request: '+e.reqPreview" in bd
          and "'  response: '+e.respPreview" in bd
          and "'  recv: ('+fmtB(e.reqLen||0)+') " in bd
          and "'  send: ('+fmtB(e.respLen||0)+') " in bd
          and "plainLine(e)" in bd, "")  # v16: indent 2 spasi
    # 6. v14 — panel "Panduan" DIHAPUS (narasi karangan, arahan owner)
    check("v49-guide-panel", "id=guide" not in bd and "guidebtn" not in bd
          and "ALUR NORMAL BOOT" not in bd, "")
    # 7. versi v13 di badge + banner
    check("v49-ver", ">v19</span>" in bd and "MOD-LOCALSERVER v19" in bd, "")  # v19

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
          and "$('errf').innerHTML=ic('octagon')" in bd
          and "$('copy').innerHTML=ic('copy')" in bd, "")  # v16: 3 tombol ikon + cari
    # 4. chip status berikon + label manusia
    check("v60-chips", "class=schip" not in bd and "id=stchips" not in bd
          and "KONFIG'" not in bd)  # v15/v16: chip dihapus — tombol sampah
    check("v60-chip-ports", "'9527','settings','KONFIG'" not in bd
          and "id=stchips" not in bd)  # v16: chip port tidak ada
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
    check("v60-toast-rise", "@keyframes rise" not in bd and "@keyframes fadein" in bd, "")  # v16
    check("v60-title-grad", "linear-gradient(90deg,#f2f8fd,#8fe6b0)" in bd, "")
    # 10. salin log = RAW (LVE/CPE emoji map DIHAPUS)
    check("v63-no-emoji-copy", "LVE={" not in bd and "CPE={" not in bd, "")
    check("v60-stbadge", "id=stbadge" not in bd
          and "MASALAH'" not in bd)  # v16: badge status dihapus
    # 11. v14 — panduan DIHAPUS; petunjuk singkat berpindah ke banner
    check("v63-guide", "ALUR NORMAL BOOT" not in bd
          and "klik untuk buka" not in bd
          and "semua rincian tampil langsung" in bd, "")  # v16: nol klik
    check("v39-overlay-banner-dihapus",
          "IZIN Display over other apps OFF" not in bd
          and "BUKA SETTINGS OVERLAY" not in bd, "")
    # 12. baris status ala server: LISTEN + uptime + arsip + status badge
    check("v43-stline-server", "id=sum" in bd and "terakhir penting:" in bd
          and "id=stbadge" not in bd, "")  # v16: footer ringkas
    # 13. live feel: kursor blok berkedip + penanda jeda
    check("v43-live-cursor", "id=cursor" in bd and "@keyframes bl" in bd
          and "id=pausemark" in bd, "")
    # 14. v14 — filter ringkas SELALU TAMPIL: cari + level + port
    #     (tahap/status/jumlah DIHAPUS — tombol sampah, arahan owner)
    check("v43-filter-lengkap", "id=q" in bd and "id=errf" in bd
          and "id=lv" not in bd and "id=port" not in bd
          and "id=stage" not in bd and "id=limit" not in bd, "")  # v16: cari + filter error


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
          and "id=errf class=ib" in bd and "id=copy class=ib" in bd
          and "id=reset class=ib" not in bd and "id=dl class=ib" not in bd
          and "id=cpt class=ib" not in bd
          and "id=vimp" not in bd and "id=vmal" not in bd and "id=vall" not in bd
          and "id=pf" not in bd and "id=guidebtn" not in bd
          and "PENTING</button>" not in bd
          and "MASALAH</button>" not in bd and "Salin tabel</button>" not in bd, "")  # v16
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
    check("v41-salin-icon", "id=copy class=ib" in bd
          and "id=cpt class=ib" not in bd and "id=dl class=ib" not in bd
          and "id=cpjs" not in bd, "")  # v16: tombol salin tunggal


    # ================= Bagian 53 — v16: FILTER ERROR + TOMBOL SALIN =========
    # Arahan owner (kata-kata persis):
    #  "filter error gak ada , tombol salin untuk di kirim ke anda juga gak
    #   ada .."
    #  "dan itu kenapa loginserver malah sudah di kerjakan PADAHAL belum di
    #   kasih rencana kesana"
    # console v16 = 4 kontrol SEMUA fungsional (cari, filter error, salin,
    # jeda); rincian tetap langsung tampil; loginserver TIDAK disentuh.
    st, hd, bd = get400("/")
    check("v16-filter-error-ada", "id=errf" in bd
          and "S.err=!S.err" in bd and "erron" in bd
          and "if(w!='WARN'&&w!='ERROR')return false" in bd, "")
    check("v16-salin-ada", "$('copy').onclick=doCopy" in bd
          and "navigator.clipboard" in bd
          and "execCommand('copy')" in bd
          and "tempel ke chat dev" in bd, "")
    check("v16-4-kontrol", "id=q" in bd and "id=errf" in bd
          and "id=copy" in bd and "id=pause" in bd, "")


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

    # ============ Bagian 72 — CAPTURE CLIENT (cara kerja eruda → log global) ==========
    # Laporan owner (kata-kata persis):
    #  "sudah di bilang di game itu sedang loading !! artinya ada log yg jelas
    #   gk tertangkap malah log anda itu isinya apa babi ."
    #  "Sudah di bilang log natural masih aja ngeyel !!! kalau loading ya dia
    #   itu loading apa ! lihat log eruda sebagai contoh goblok !!"
    # Akar buta-log (bukti device v22: antara index-native.html dan socket.io
    # TIDAK ADA satu pun request js/resource di log): js/resource dilayani
    # shell EgretNative dari penyimpanan lokal — TIDAK PERNAH sampai ke
    # listener HTTP; console.*/jserror hanya ada di WebView. v72: script
    # capture diinjeksi ke index-native.html yang dilayani server sendiri →
    # tiap kejadian client = baris kind CLIENT di server.ndjson yang SAMA.
    def _client_rows():
        st, hd, bd = get400("/logs?mode=port&port=9528&limit=900")
        return [e for e in json.loads(bd)["entries"] if e.get("kind") == "CLIENT"]

    def _wait_client(pred, tries=120):
        for _i72 in range(tries):
            rows72 = [r for r in _client_rows() if pred(r)]
            if rows72:
                return rows72[-1]
            time.sleep(0.05)
        return None

    # 1. injeksi serve-time index-native.html — capture ada, SEBELUM </head>
    with urllib.request.urlopen("http://127.0.0.1:9528/bs/index-native.html?rnd=0.72", timeout=10) as r:
        bd_inj = r.read().decode("utf-8", "replace")
        hd_inj = dict(r.headers)
    check("v72-injeksi-capture-ada", "__dbClientCapture" in bd_inj
          and "/log/client" in bd_inj, "len=%d" % len(bd_inj))
    _he72 = bd_inj.find("</head>")
    _ia72 = bd_inj.find("__dbClientCapture")
    check("v72-injeksi-sebelum-head-tutup", 0 < _ia72 < _he72,
          "%d < %d" % (_ia72, _he72))
    check("v72-cache-control-no-store", hd_inj.get("Cache-Control") == "no-store",
          str(hd_inj.get("Cache-Control")))
    # 2. file non-HTML TIDAK disentuh
    with urllib.request.urlopen("http://127.0.0.1:9528/bs/resource/properties/clientversion.json", timeout=10) as r:
        bd_cv = r.read().decode("utf-8", "replace")
    check("v72-non-html-tanpa-injeksi", "__dbClientCapture" not in bd_cv, "")
    # 3. file di DISK byte tetap asli (injeksi hanya di respons HTTP — bs/ utuh)
    check("v72-file-disk-tetap-asli", "__dbClientCapture" not in open(
        DOCROOT + "/bs/index-native.html", encoding="utf-8").read(), "")
    # 4. baris 200 halaman ber-extra "capture client terinjeksi"
    row_inj72 = _waitrow71(lambda ln: "index-native" in ln and '"status":200' in ln
                           and "capture client terinjeksi" in ln)
    check("v72-extra-injeksi-di-log", row_inj72 != "",
          row_inj72[-200:] if row_inj72 else "baris injeksi tidak ada")

    # 5. POST /log/client — console.error → kind CLIENT level ERROR, teks verbatim
    _ts72 = int(time.time() * 1000)
    batch72a = json.dumps({"v": 1, "events": [
        {"ts": _ts72, "type": "console.error", "level": "ERROR",
         "text": "[ERR] uji-console-error-v72 detail-gagal"},
        {"ts": _ts72, "type": "console.info", "level": "INFO",
         "text": "uji-console-info-v72"},
    ]}, ensure_ascii=False).encode()
    req72a = urllib.request.Request("http://127.0.0.1:9528/log/client",
                                    data=batch72a, method="POST")
    req72a.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req72a, timeout=10) as r:
        check("v72-logclient-post-200", r.read().decode().strip() == "ok", "")
    row_ce72 = _wait_client(lambda r: r.get("method") == "console.error"
                            and "uji-console-error-v72" in (r.get("path") or ""))
    check("v72-client-console-error", row_ce72 is not None
          and row_ce72.get("level") == "ERROR"
          and "console.error dari halaman game" in (row_ce72.get("error") or "")
          and row_ce72.get("port") == 9528,
          str(row_ce72)[:240] if row_ce72 else "tidak ada")
    row_ci72 = _wait_client(lambda r: r.get("method") == "console.info"
                            and "uji-console-info-v72" in (r.get("path") or ""))
    check("v72-client-info-tanpa-level", row_ci72 is not None
          and "level" not in row_ci72,
          str(row_ci72)[:180] if row_ci72 else "tidak ada")

    # 6. jserror / script GAGAL / xhr 404 → level ERROR dgn sebab jujur;
    #    script sukses & XHR 200 → tanpa level (access log alami)
    batch72b = json.dumps({"v": 1, "events": [
        {"ts": _ts72, "type": "jserror", "level": "ERROR",
         "text": "uji-jserror-v72: error sengaja untuk uji capture"},
        {"ts": _ts72, "type": "script", "level": "ERROR",
         "text": "script load: script http://127.0.0.1:9528/bs/js/uji-gagal.js — GAGAL 12 ms"},
        {"ts": _ts72, "type": "script", "level": "INFO",
         "text": "script load: script http://127.0.0.1:9528/bs/js/uji-ada.js — selesai 8 ms"},
        {"ts": _ts72, "type": "xhr", "level": "ERROR",
         "text": "xhr GET http://127.0.0.1:9528/bs/uji-xhr-404.json → 404 (5 ms)"},
    ]}, ensure_ascii=False).encode()
    req72b = urllib.request.Request("http://127.0.0.1:9528/log/client",
                                    data=batch72b, method="POST")
    req72b.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req72b, timeout=10) as r:
        r.read()
    row_js72 = _wait_client(lambda r: r.get("method") == "jserror"
                            and "uji-jserror-v72" in (r.get("path") or ""))
    check("v72-client-jserror", row_js72 is not None
          and row_js72.get("level") == "ERROR"
          and "window.onerror dari halaman game" in (row_js72.get("error") or ""),
          str(row_js72)[:240] if row_js72 else "tidak ada")
    row_sg72 = _wait_client(lambda r: r.get("method") == "script"
                            and "uji-gagal.js" in (r.get("path") or ""))
    check("v72-client-script-gagal", row_sg72 is not None
          and row_sg72.get("level") == "ERROR"
          and "script halaman game gagal dimuat di client" in (row_sg72.get("error") or "")
          and "GAGAL" in row_sg72.get("path", ""),
          str(row_sg72)[:240] if row_sg72 else "tidak ada")
    row_so72 = _wait_client(lambda r: r.get("method") == "script"
                            and "uji-ada.js" in (r.get("path") or ""))
    check("v72-client-script-sukses-tanpa-level", row_so72 is not None
          and "level" not in row_so72 and "selesai" in row_so72.get("path", ""),
          str(row_so72)[:200] if row_so72 else "tidak ada")
    row_xg72 = _wait_client(lambda r: r.get("method") == "xhr"
                            and "uji-xhr-404.json" in (r.get("path") or ""))
    check("v72-client-xhr-404", row_xg72 is not None
          and row_xg72.get("level") == "ERROR"
          and "XHR halaman game gagal/4xx di client" in (row_xg72.get("error") or "")
          and "→ 404" in row_xg72.get("path", ""),
          str(row_xg72)[:240] if row_xg72 else "tidak ada")

    # 7. jujur di error juga: body rusak → 400 + baris error; GET → 405;
    #    batch kosong → 200 tanpa baris; oversize → 413
    req72c = urllib.request.Request("http://127.0.0.1:9528/log/client",
                                    data=b'{"v":1,"events":[ TIDAK-VALID', method="POST")
    try:
        with urllib.request.urlopen(req72c, timeout=10) as r:
            st72c = r.status
    except urllib.error.HTTPError as e72:
        st72c = e72.code
    check("v72-logclient-rusak-400", st72c == 400, str(st72c))
    # baris 400 = access log HTTP (bukan event client) — jujur: request rusak
    # ditolak dan tercatat dgn sebab, bukan diam
    row_bad72 = None
    for _i72 in range(100):
        st, hd, bd = get400("/logs?mode=port&port=9528&limit=900")
        cand72 = [e for e in json.loads(bd)["entries"]
                  if e.get("kind") == "HTTP" and (e.get("path") or "") == "/log/client"
                  and "batch log client tidak valid" in (e.get("error") or "")]
        if cand72:
            row_bad72 = cand72[-1]
            break
        time.sleep(0.05)
    check("v72-logclient-rusak-terlog", row_bad72 is not None
          and row_bad72.get("status") == 400,
          str(row_bad72)[:240] if row_bad72 else "tidak ada")
    try:
        with urllib.request.urlopen("http://127.0.0.1:9528/log/client", timeout=10) as r:
            st72d = r.status
    except urllib.error.HTTPError as e72:
        st72d = e72.code
    check("v72-logclient-get-405", st72d == 405, str(st72d))
    req72e = urllib.request.Request("http://127.0.0.1:9528/log/client",
                                    data=b'{"v":1,"events":[]}', method="POST")
    req72e.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req72e, timeout=10) as r:
        check("v72-logclient-kosong-200", r.read().decode().strip() == "ok", "")
    _n_before72 = len(_client_rows())
    req72f = urllib.request.Request("http://127.0.0.1:9528/log/client",
                                    data=b'{"v":1,"events":[]}' + b" " * 270000,
                                    method="POST")
    try:
        with urllib.request.urlopen(req72f, timeout=10) as r:
            st72f = r.status
    except urllib.error.HTTPError as e72:
        st72f = e72.code
    check("v72-logclient-oversize-413", st72f == 413, str(st72f))
    time.sleep(0.6)
    check("v72-logclient-oversize-tanpa-baris", len(_client_rows()) == _n_before72, "")

    # 8. warisan v71: baris timeout sesi pola socket GAME kini BER-ALASAN
    #    (setiap timeout membawa alasan loading macet — owner menonton log
    #    SAAT loading; satu baris penjelasan di open saja tidak cukup)
    rows_to72 = [ln for ln in open(sl70, encoding="utf-8", errors="replace").read().splitlines()
                 if '"long-poll timeout 10 dtk' in ln and s71b in ln]
    check("v72-timeout-gamepattern-beralasan", len(rows_to72) >= 1
          and "pola socket GAME: client menunggu event 'verify'" in rows_to72[-1]
          and "loading macet di sini" in rows_to72[-1],
          rows_to72[-1][-260:] if rows_to72 else "baris timeout sesi game tidak ada")

    # 9. E2E BROWSER NYATA (Playwright — pola verify_console_v16): halaman +
    #    injeksi + hook + POST /log/client + baris NDJSON, satu rantai utuh
    node72 = subprocess.run(["node", WS + "/verify_capture_v24.js"], timeout=180,
                            capture_output=True, text=True)
    print((node72.stdout or "")[-1600:])
    check("v72-browser-e2e-capture", node72.returncode == 0,
          (node72.stderr or "")[-400:])

finally:
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)

print("\n===== SMOKE v2: %d PASS, %d FAIL =====" % (len(PASS), len(FAIL)))
if FAIL:
    print("GAGAL:", FAIL)
    sys.exit(1)
