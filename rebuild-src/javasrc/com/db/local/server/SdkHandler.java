package com.db.local.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Listener 9530 — mirror qsdk.t4game.com:80 (API SDK t4game).
 *
 * Bagian 29 — MESIN KAWAT PHP-FAITHFUL (keputusan owner: "kalau server aslinya
 * php artinya kita juga harus secara php, kalau error ya error ... tidak ada
 * bypass" + "jika php berat kita harus realistis cari yg setara misal sqlite").
 * Engine PHP 5.4.37 diganti engine Java, TETAPI perilaku kawat ditiru
 * SETIA ke rekaman live server asli (23 Nov 2026, server masih hidup):
 *
 *  1. SELALU HTTP 200 — error di dalam body (bukti live).
 *  2. Header mirror live: X-Powered-By: PHP/5.4.37 + Content-Type: text/json
 *     (via sdk/profile.json — bisa diedit owner).
 *  3. Body format: sv=v2&data=<base64(JSON)>&sign=<md5>  (bukti smali
 *     bindcommunist/singersmooth).
 *  4. Urutan validasi = MIROR LIVE (semua kasus yang terobservasi direkap):
 *       a. data kosong/tak bisa diparse  -> 20000 "Params error"
 *       b. sign salah                    -> 20002 "Params sign error"
 *          (algoritma TreeMap k=v&+secret, DIVERIFIKASI diterima server asli)
 *       c. param wajib kosong            -> 20003 "LostSomeMustParams:<nama>"
 *          urutan ASLI dari oracle live: sdkVersion -> productCode -> authToken
 *       d. productCode tak dikenal       -> 30001 "Product doesn't exist"
 *       e. barulah logika endpoint
 *  5. Storage akun = SQLite (DbSqlite, bawaan Android) — "setara realistis"
 *     pilihan owner; file legacy JSON dimigrasi otomatis.
 *  6. Kontrak file (sdk/) — init.json/ckRegVistor.json/fallback.json dibaca
 *     tiap request, bisa diedit owner TANPA rebuild (eksperimen loginTypes!).
 *
 * FORMAT REQUEST (bukti smali bindcommunist/singersmooth.singersmooth):
 *   POST application/x-www-form-urlencoded
 *   body: sv=v2&data=<URLEncoder(Base64(JSON params))>&sign=<md5>
 *   → decode: pecah '&', URLDecoder.decode nilai, Base64-decode "data".
 *
 * GET DIAGNOSTIK: request tanpa body data+sign (curl/dashboard) lewat jalur
 * uji tanpa cek kontrak — dicatat "GET-TEST" di log; alur game (POST) selalu
 * lewat cek kontrak penuh di atas.
 */
public final class SdkHandler implements HttpWorker.Handler {

    /* ===================== kontrak statis (mirror live) ===================== */

    /** Katalog error live — teks PERSIS server asli (rekam 23 Nov 2026). */
    static String errLive(int id, String msg) {
        return "{\"result\":false,\"data\":[],\"error\":{\"id\":" + id
                + ",\"message\":\"" + JsonUtil.esc(msg) + "\"}}";
    }

    /** Sukses kosong — getNotice/dmsg/onNewToken/getAgreement/sendCodeByEmail. */
    public static final String OK_EMPTY = "{\"result\":true,\"data\":\"\"}";

    /* ===================== decode request ===================== */

    /**
     * Body SDK: sv=v2&data=<b64>&sign=... → map
     *  - tiap pasangan key=value, nilai di URLDecoder.decode (utf-8)
     *  - bila key "data": Base64-decode -> JSON string -> map["json"]
     *  - helper baca field: AccountStore.jsonStr(map["json"], field)
     */
    static Map<String, String> decodeSdkBody(byte[] body) {
        Map<String, String> out = new HashMap<String, String>();
        if (body == null || body.length == 0) return out;
        String s = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        String[] parts = s.split("&");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() == 0) continue;
            int eq = p.indexOf('=');
            String k = eq < 0 ? p : p.substring(0, eq);
            String v = eq < 0 ? "" : p.substring(eq + 1);
            try { v = URLDecoder.decode(v, "UTF-8"); } catch (Throwable t) { /* raw */ }
            out.put(k, v);
            if (k.equals("data")) {
                String json = b64decodeStr(v);
                if (json != null) out.put("json", json);
            }
        }
        return out;
    }

    /**
     * Gabung query params (?uid=...&username=...) ke map — jalur GET diagnostik.
     */
    static void mergeQuery(Map<String, String> m, String rawPath) {
        if (rawPath == null) return;
        int qi = rawPath.indexOf('?');
        if (qi < 0) return;
        String[] parts = rawPath.substring(qi + 1).split("&");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            String k = eq < 0 ? p : p.substring(0, eq);
            String v = eq < 0 ? "" : p.substring(eq + 1);
            try { v = URLDecoder.decode(v, "UTF-8"); } catch (Throwable t) { /* raw */ }
            if (!m.containsKey(k)) m.put(k, v);
        }
    }

    static String field(Map<String, String> m, String key) {
        String json = m.get("json");
        if (json != null && !json.isEmpty()) {
            String v = AccountStore.jsonStr(json, key);
            if (v != null && !v.isEmpty()) return v;
        }
        String q = m.get(key);            // query param fallback (GET)
        return q == null ? "" : q;
    }

    /** Base64 decoder sendiri (java.util.Base64 = API 26+, minSdk 21). */
    static String b64decodeStr(String s64) {
        if (s64 == null) return null;
        try {
            String s = s64.replaceAll("[^A-Za-z0-9+/=]", "");
            while (s.length() % 4 != 0) s = s + "=";
            int len = s.length(), pad = 0;
            if (len >= 1 && s.charAt(len - 1) == '=') pad++;
            if (len >= 2 && s.charAt(len - 2) == '=') pad++;
            int outLen = len / 4 * 3 - pad;
            if (outLen < 0) return null;
            byte[] out = new byte[outLen];
            int[] T = new int[128];
            String A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
            for (int i = 0; i < 64; i++) T[A.charAt(i)] = i;
            int o = 0;
            for (int i = 0; i < len; i += 4) {
                int c0 = s.charAt(i) == '=' ? 0 : T[s.charAt(i)];
                int c1 = s.charAt(i + 1) == '=' ? 0 : T[s.charAt(i + 1)];
                int c2 = s.charAt(i + 2) == '=' ? 0 : T[s.charAt(i + 2)];
                int c3 = s.charAt(i + 3) == '=' ? 0 : T[s.charAt(i + 3)];
                int trip = (c0 << 18) | (c1 << 12) | (c2 << 6) | c3;
                if (o < outLen) out[o++] = (byte) ((trip >> 16) & 0xff);
                if (o < outLen) out[o++] = (byte) ((trip >> 8) & 0xff);
                if (o < outLen) out[o++] = (byte) (trip & 0xff);
            }
            return new String(out, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean md5ok(String v) {
        return v != null && v.length() >= 16 && v.matches("[0-9a-fA-F]+");
    }

    /* ===================== gerbang kontrak (mirror live) ===================== */

    /** PHP empty() — null atau "" dianggap kosong. */
    private static boolean empty(String v) {
        return v == null || v.trim().isEmpty();
    }

    /**
     * Gerbang kontrak POST game: data -> sign -> must-params -> product.
     * Return body error, atau null bila LOLOS (lanjut ke logika endpoint).
     * Urutan merekap semua respons live yang terobservasi 23 Nov 2026.
     */
    static String contractGate(Map<String, String> m, StringBuilder trace) {
        String json = m.get("json");
        boolean hasData = m.containsKey("data");
        boolean hasSign = !empty(m.get("sign"));
        if (!hasData && !hasSign) return null;      // GET diagnostik — tanpa gerbang

        // (a) data wajib bisa diparse jadi objek — kalau tidak: 20000
        Map<String, String> params = JsonUtil.scanFlat(json);
        if (params == null) {
            trace.append("GATE-20000(data)");
            return errLive(20000, "Params error");
        }

        // (b) sign — algoritma TreeMap k=v& + secret, MD5 lowercase
        if (SdkConfig.profileBool("verifySign", true)) {
            String secret = SdkConfig.profileString("signSecret", "0b2a18e45d7df321");
            if (!SdkSign.valid(params, m.get("sign"), secret)) {
                trace.append("GATE-20002(sign)");
                return errLive(20002, "Params sign error");
            }
        }

        // (c) must-params urutan ASLI live: sdkVersion -> productCode -> authToken
        String must = mustMissing(params);
        if (must != null) {
            trace.append("GATE-20003(").append(must).append(')');
            return errLive(20003, "LostSomeMustParams:" + must);
        }

        // (d) productCode terdaftar
        String prod = params.get("productCode");
        boolean prodOk = SdkConfig.productKnown(prod);
        SdkConfig.lastProductSeen = prod == null ? "" : prod;
        SdkConfig.lastProductOk = prodOk;
        if (!prodOk) {
            trace.append("GATE-30001(product=").append(prod == null ? "" : prod).append(')');
            String pl = prod == null ? "" : prod;
            if (!pl.equals(SdkConfig.lastRejectLogged)) {
                SdkConfig.lastRejectLogged = pl;
                Db.log("PRODUCT REJECTED 30001: productCode=" + pl
                        + " not in sdk/profile.json (products="
                        + SdkConfig.productsList().toString()
                        + ") — add the productCode to products in sdk/profile.json"
                        + " then force-stop & reopen the game");
            }
            return errLive(30001, "Product doesn't exist");
        }
        trace.append("GATE-PASS");
        return null;
    }

    /** nama param wajib pertama yang kosong, atau null bila lengkap. */
    static String mustMissing(Map<String, String> params) {
        String[] order = {"sdkVersion", "productCode", "authToken"};
        for (int i = 0; i < order.length; i++) {
            if (empty(params.get(order[i]))) return order[i];
        }
        return null;
    }

    /* ===================== handler ===================== */

    @Override
    public boolean handle(HttpWorker.Request req, OutputStream out, Socket sock) throws IOException {
        long t0 = System.currentTimeMillis();
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_SDK;
        e.method = req.method;
        e.path = req.rawPath;
        e.reqLen = req.body.length;
        e.headers = req.headersStr;
        if (req.body.length > 0) e.reqPreview = StaticHandler.preview(req.body);

        SdkConfig.ensureIndex();   // seed sdk/index.txt + errors.json sekali

        String p = (req.path == null || req.path.isEmpty()) ? req.rawPath : req.path;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);

        String body;
        String extra;
        int status = 200;
        Map<String, String> m = decodeSdkBody(req.body);
        mergeQuery(m, req.rawPath);
        StringBuilder trace = new StringBuilder(64);

        // GET diagnostik = tanpa body data DAN tanpa sign — lewat tanpa gerbang
        String signV = m.get("sign");
        boolean getTest = !m.containsKey("data")
                && (signV == null || signV.trim().isEmpty());
        String gate = getTest ? null : contractGate(m, trace);

        if (gate != null) {
            body = gate;
            extra = trace + " | contract mirror live 23 Nov | respCT=text/json";
        } else if (p.equals("/v1/system/init")) {
            body = SdkConfig.initJson();
            extra = "init.json (sdk/v1/system/init.json — owner editable) "
                    + "| loginTypes drives the login screen: [\"1\"]=email form+GUEST, "
                    + "[\"12\"]=guest-only, [\"1\",\"13\"]=live replay mirror "
                    + "| payTypes required by parser model/e | GET-TEST=" + getTest
                    + " | respCT=text/json";
        } else if (p.equals("/v1/user/registerVisitor")) {
            AccountStore a = AccountStore.loadOrCreate();
            body = a.sdkDataResponse(a.isNewUser);
            extra = "registerVisitor (GUEST button) | guest account persistent "
                    + "SQLite (" + Db.open(LoggerCore.serverRootPath).backend() + ") "
                    + "(uid stable across restarts) | session: authToken+userData "
                    + "contract psalmicvolleyball/cointhreat | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/user/registerUser")) {
            // navyshop -> dishbesideshockey$ironoriginhoblike: vcode?, username(email), password(MD5)
            String email = field(m, "username").trim();
            String pass  = field(m, "password").trim();
            String vcode = field(m, "vcode").trim();
            if (email.isEmpty() || email.indexOf('@') < 0) {
                body = errLive(20000, "Params error");
            } else if (!md5ok(pass)) {
                body = errLive(20000, "Params error");
            } else if (UserAccounts.find(email) != null) {
                body = errLive(20001, "Email sudah terdaftar — silakan LOGIN dengan email tersebut.");
            } else {
                UserAccounts.Rec r = UserAccounts.create(email, pass.toLowerCase());
                if (r == null) {
                    body = errLive(20000, "Params error");
                } else {
                    AccountStore.setCurrent(r.uid, r.email, r.token, "0");
                    body = AccountStore.sessionResponse(r.uid, r.email, r.token, "1", "0");
                    UserAccounts.sysLog("new email account uid=" + r.uid + " email=" + r.email);
                }
            }
            extra = "registerUser (REGISTER button, email form) | no email verification "
                    + "(isNeedVerify meta-data absent — vcode optional"
                    + (vcode.isEmpty() ? ", not sent" : ", ignored by server") + ") "
                    + "| success = session isGuest:0 openType:1 | account stored in SQLite "
                    + "| " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/user/loginByName")) {
            // timidcompletely -> dishbesideshockey$gangclothing: username, password(MD5)
            String email = field(m, "username").trim();
            String pass  = field(m, "password").trim();
            if (email.isEmpty() || !md5ok(pass)) {
                body = errLive(20000, "Params error");
            } else {
                UserAccounts.Rec r = UserAccounts.find(email);
                if (r == null) {
                    body = errLive(20001, "Email belum terdaftar — tekan DAFTAR (Register) untuk membuat akun baru.");
                } else if (!r.passMd5.equals(pass.toLowerCase())) {
                    body = errLive(20004, "Kata sandi salah — cek ulang atau gunakan akun lain.");
                } else {
                    AccountStore.setCurrent(r.uid, r.email, r.token, "0");
                    Db.open(LoggerCore.serverRootPath).touchLogin(r.uid);
                    body = AccountStore.sessionResponse(r.uid, r.email, r.token, "1", "0");
                    UserAccounts.sysLog("email login uid=" + r.uid + " email=" + r.email);
                }
            }
            extra = "loginByName (LOGIN button, email form) | email+password verified "
                    + "against SQLite | 20004 = wrong password (20002 LIVE = sign, kept distinct) "
                    + "| success = session isGuest:0 openType:1 | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/user/autoLogin")) {
            // QuickGameSdkService: params authToken (token tersimpan klien)
            String authTok = field(m, "authToken").trim();
            AccountStore g = AccountStore.loadOrCreate();
            if (authTok.length() > 0 && authTok.equals(g.token)) {
                body = g.sdkDataResponse(false);
                extra = "autoLogin (guest) | authToken matches stored guest account "
                        + "(hardlinerspare -> service.cointruth) | " + trace + " | respCT=text/json";
            } else {
                UserAccounts.Rec r = authTok.length() > 0 ? UserAccounts.findByToken(authTok) : null;
                if (r != null) {
                    AccountStore.setCurrent(r.uid, r.email, r.token, "0");
                    body = AccountStore.sessionResponse(r.uid, r.email, r.token, "1", "0");
                    extra = "autoLogin (email) | authToken matches registered account " + r.email
                            + " | session isGuest:0 openType:1 | " + trace + " | respCT=text/json";
                } else {
                    body = g.sdkDataResponse(false);
                    extra = "autoLogin fallback to guest account (authToken "
                            + (authTok.isEmpty() ? "empty" : "unknown")
                            + "; first launch / cleared data path) | " + trace + " | respCT=text/json";
                }
            }
        } else if (p.equals("/v1/user/sendCodeByEmail")) {
            // alur vcode tidak wajib (isNeedVerify false) — balas sukses agar UI tidak macet
            body = OK_EMPTY;
            extra = "sendCodeByEmail | local server sends no email; vcode NOT "
                    + "required (meta-data isNeedVerify absent) — any code accepted "
                    + "| " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/user/findPassByEmail")) {
            String email = field(m, "username").trim();
            if (email.isEmpty()) email = field(m, "email").trim();
            if (email.isEmpty() || UserAccounts.find(email) == null) {
                body = errLive(20001, "Email tidak ditemukan — belum terdaftar di server lokal ini.");
            } else {
                // 20005 = lokal (20003 LIVE = LostSomeMustParams — jangan bentrok)
                body = errLive(20005, "Lupa kata sandi lewat email tidak tersedia di server lokal "
                        + "(tidak ada email keluar). Gunakan Ganti Kata Sandi di Pusat Akun, "
                        + "atau daftar dengan email lain.");
            }
            extra = "findPassByEmail | local server has no outgoing email — honest "
                    + "answer so the UI does not hang | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/auth/getUserInfo")) {
            String uid = field(m, "uid").trim();
            AccountStore g = AccountStore.loadOrCreate();
            if (uid.equals(g.uid)) {
                body = "{\"result\":true,\"data\":{\"uid\":\"" + JsonUtil.esc(g.uid)
                        + "\",\"username\":\"" + JsonUtil.esc(g.username)
                        + "\",\"displayUid\":\"" + JsonUtil.esc(g.uid)
                        + "\",\"token\":\"" + JsonUtil.esc(g.token)
                        + "\",\"isGuest\":\"1\",\"openType\":\"12\"}}";
            } else {
                UserAccounts.Rec r = UserAccounts.findByUid(uid);
                if (r != null) {
                    body = "{\"result\":true,\"data\":{\"uid\":\"" + JsonUtil.esc(r.uid)
                            + "\",\"username\":\"" + JsonUtil.esc(r.email)
                            + "\",\"displayUid\":\"" + JsonUtil.esc(r.uid)
                            + "\",\"token\":\"" + JsonUtil.esc(r.token)
                            + "\",\"isGuest\":\"0\",\"openType\":\"1\"}}";
                } else {
                    body = errLive(20001, "Akun tidak ditemukan di server lokal.");
                }
            }
            extra = "getUserInfo (Account Center) | account info by uid (guest/email) "
                    + "| " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/auth/changePassword")) {
            // dishbesideshockey$famediscussionsmall: uid, oldPassword(MD5), newPassword(MD5)
            String uid = field(m, "uid").trim();
            String oldP = field(m, "oldPassword").trim();
            String newP = field(m, "newPassword").trim();
            if (uid.isEmpty() || !md5ok(newP)) {
                body = errLive(20000, "Params error");
            } else if (AccountStore.loadOrCreate().uid.equals(uid)) {
                body = errLive(20004, "Akun TAMU tidak punya kata sandi. Gunakan Pusat Akun -> "
                        + "Bind Email untuk menambah email+sandi ke akun ini.");
            } else if (!UserAccounts.changePassword(uid, oldP.toLowerCase(), newP.toLowerCase())) {
                body = errLive(20004, "Kata sandi lama salah.");
            } else {
                body = OK_EMPTY;
                UserAccounts.sysLog("password changed uid=" + uid);
            }
            extra = "changePassword (Account Center) | verify oldPassword -> set newPassword "
                    + "in SQLite | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/auth/bindMail")) {
            // dishbesideshockey$reweavingsiamesedpropertylessnesses: uid, email, code, password(MD5)
            String uid = field(m, "uid").trim();
            String email = field(m, "email").trim();
            String code = field(m, "code").trim();
            String pass = field(m, "password").trim();
            if (email.isEmpty() || email.indexOf('@') < 0 || !md5ok(pass)) {
                body = errLive(20000, "Params error");
            } else if (UserAccounts.find(email) != null) {
                body = errLive(20001, "Email sudah dipakai akun lain.");
            } else if (UserAccounts.bindToExistingUid(uid, email, pass.toLowerCase()) == null) {
                body = errLive(20000, "Params error");
            } else {
                body = OK_EMPTY;
                UserAccounts.sysLog("bind mail uid=" + uid + " email=" + email.toLowerCase()
                        + " (uid/token unchanged — game progress safe; code "
                        + (code.isEmpty() ? "empty" : "sent but ignored") + ")");
            }
            extra = "bindMail (Account Center — add email+password to ACTIVE account) | "
                    + "in-place UPDATE in SQLite, uid/token unchanged | verification code "
                    + "ignored (no outgoing email) | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/system/getNotice")) {
            body = OK_EMPTY;
            extra = "getNotice empty: result=true data=\"\" (len 0 <=2 -> callback skips notice, "
                    + "try/catch safe) | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/system/dmsg") || p.equals("/v1/system/onNewToken")
                || p.equals("/v1/system/getAgreement")) {
            body = OK_EMPTY;
            extra = "other system path -> empty success | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/auth/setGameRoleInfo")) {
            body = OK_EMPTY;
            extra = "setGameRoleInfo | game role report (cointhreat.smali setGameRoleInfo) "
                    + "-> result:true empty | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/user/ckRegVistor")) {
            // Bagian 27 — tombol Kode Migrasi (onCDKeyClicked -> MSG_CHECK_REG_VISITOR).
            // Kontrak dari file sdk/v1/user/ckRegVistor.json (mirror PERSIS replay
            // live server asli 23 Nov: result:true, data:{isReged:0}).
            body = SdkConfig.ckRegJson();
            extra = "ckRegVistor (migration code) | exact mirror of the live server "
                    + "(file sdk/v1/user/ckRegVistor.json) | button hidden since "
                    + "loginTypes [\"1\"] | " + trace + " | respCT=text/json";
        } else if (p.equals("/v1/user/getServerAgreement")) {
            body = OK_EMPTY;
            extra = "agreement empty (dishbesideshockey/dishbesideshockey) | "
                    + trace + " | respCT=text/json";
        } else {
            body = SdkConfig.fallbackJson();
            extra = "endpoint NOT mapped (file sdk/fallback.json) | SDK called "
                    + "a feature not yet mapped | " + trace + " | respCT=text/json";
        }

        // Header mirror live server asli (profile.json — bisa diedit owner)
        Map<String, String> h = HttpWorker.baseHeaders(SdkConfig.contentType());
        h.put("X-Powered-By", SdkConfig.poweredBy());
        h.put("Access-Control-Allow-Origin", "*");
        byte[] bb = HttpWorker.text(body);
        HttpWorker.writeResponse(out, status, "OK", h, bb);

        e.status = status;
        e.respLen = bb.length;
        e.respPreview = body;
        e.extra = extra;
        e.durMs = System.currentTimeMillis() - t0;
        LoggerCore.log(e);
        return true;
    }

    /**
     * loginchecknative DILAYANI DI 5050 (bukan di sini) karena URL-nya
     * loginServer + ":" + loginWebPort (bukan QHinfo) — lihat SocketIoHandler.
     * Method ini hanya util untuk menyusun responsnya secara konsisten.
     * Bagian 28: versi parameter-aware dipakai SocketIoHandler langsung
     * (AccountStore.loginCheckNativeOf) — query params SDK dipercaya.
     */
    public static byte[] loginCheckNativeBody() {
        return HttpWorker.text(AccountStore.loadOrCreate().loginCheckNative());
    }
}
