package com.db.local.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Listener 9530 — mirror qsdk.t4game.com:80 (API SDK t4game: 33 path /v1/* + /v2/purchase,
 * kontrak 14.A#8).
 *
 * Bagian 28 — SDK FULL-FITUR (tamu + DAFTAR + LOGIN email, tanpa konfirmasi email):
 *  1. init loginTypes ["12"] -> ["1"] (mirror server asli qsdk.t4game.com yang
 *     mengirim ["1","13"] — replay 22 Nov). Bukti smali model/e.cointhreat:
 *       - "1"  (contains "\"1\"") -> penggate tombol TAMU (fl_guest/ib_guest)
 *         & tv_freePlay DILOGIN FRAGMENT (f/a/d :cond_298 / :cond_2d1 —
 *         "hide guestFL login" hanya bila "1" ABSEN).
 *       - "12" -> mode tamu-only: email_password_layout + tv_login +
 *         ll_forgot_regist DISIMPAN GONE (rawturn=true :goto_242) dan
 *         cdkey_edit_layout DITAMPILKAN (psalmicvolleyball=true) — persis
 *         layar v11/v12 (tombol TAMU + Kode Migrasi, form email tak ada).
 *       - Dengan ["1"]: form email+sandi MUNCUL (default visible layout
 *         hw_view_login_email_with_password_a), tv_login + tv_register +
 *         tv_forgot muncul, Kode Migrasi hilang (cdkey_edit_layout default
 *         vis=2 GONE — hanya "12" yang menampilkannya).
 *  2. /v1/user/registerUser — daftar email+sandi (navyshop -> ironoriginhoblike:
 *     params username(email), password=MD5, vcode (isNeedVerify tidak ada di
 *     manifest meta-data -> gangclothing=false -> vcode TIDAK diwajibkan;
 *     konfirmasi email tidak ada — permintaan owner).
 *  3. /v1/user/loginByName — login email+sandi (timidcompletely -> gangclothing).
 *  4. /v1/user/autoLogin — match authToken -> akun tamu / akun email.
 *  5. /v1/auth/bindMail, /v1/auth/changePassword, /v1/auth/getUserInfo,
 *     /v1/user/sendCodeByEmail, /v1/user/findPassByEmail.
 *
 * FORMAT REQUEST (bukti smali bindcommunist/singersmooth.singersmooth):
 *   POST application/x-www-form-urlencoded
 *   body: sv=v2&data=<URLEncoder(Base64(JSON params))>&sign=<...>
 *   → decode: pecah '&', URLDecoder.decode nilai, Base64-decode "data".
 */
public final class SdkHandler implements HttpWorker.Handler {

    /** Envelope capture live 14.A#8 — fallback untuk path yang belum dipetakan. */
    public static final String ENVELOPE =
            "{\"result\":false,\"data\":[],\"error\":{\"id\":20000,\"message\":\"Params error\"}}";

    /**
     * Init sukses — Bagian 28: loginTypes ["1"] (bukan lagi ["12"]).
     * payTypes kunci WAJIB parser model/e (tanpa itu -> return null -> stuck).
     * TANPA version (hindari runnable 'version update'), TANPA nodes.
     */
    public static final String INIT_OK =
            "{\"result\":true,\"data\":{\"payTypes\":[],\"loginTypes\":[\"1\"]}}";

    /** Sukses kosong — getNotice/dmsg/onNewToken/getAgreement/sendCodeByEmail. */
    public static final String OK_EMPTY = "{\"result\":true,\"data\":\"\"}";

    /** Bagian 27 — cek pendaftaran kode migrasi: mirror PERSIS respons live server asli. */
    public static final String CKREG_OK =
            "{\"result\":true,\"data\":{\"isReged\":0},\"error\":{\"id\":0,\"message\":\"\"}}";

    /* ===================== util respons ===================== */

    /** envelope error format server asli (capture 14.A#8) dengan pesan manusiawi. */
    static String err(int id, String msg) {
        return "{\"result\":false,\"data\":[],\"error\":{\"id\":" + id
                + ",\"message\":\"" + JsonUtil.esc(msg) + "\"}}";
    }

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
     * Gabung query params (?uid=...&username=...) ke map — agar endpoint
     * tetap bisa diuji via GET curl tanpa body POST (SDK selalu POST form;
     * query hanya jalur kenyamanan uji/dashboard).
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

        String p = (req.path == null || req.path.isEmpty()) ? req.rawPath : req.path;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);

        String body;
        String extra;
        int status = 200;
        Map<String, String> m = decodeSdkBody(req.body);
        mergeQuery(m, req.rawPath);

        if (p.equals("/v1/system/init")) {
            body = INIT_OK;
            extra = "INIT-SUCCESS 28 | loginTypes=[\"1\"] MIRROR SERVER ASLI (replay 22 Nov: "
                    + "[\"1\",\"13\"]; \"1\" = form LOGIN/DAFTAR email + tombol TAMU tampil; "
                    + "\"12\" lama = mode tamu-only yang menyembunyikan form email) "
                    + "| payTypes kunci WAJIB parser model/e | TANPA version/nodes "
                    + "| respCT=application/json";
        } else if (p.equals("/v1/user/registerVisitor")) {
            AccountStore a = AccountStore.loadOrCreate();
            body = a.sdkDataResponse(a.isNewUser);
            extra = "REGISTER-VISITOR (tombol TAMU) | akun tamu PERSISTEN server/data/account.json "
                    + "(uid tetap antar restart) | session: authToken+userData kontrak "
                    + "psalmicvolleyball/cointhreat | respCT=application/json";
        } else if (p.equals("/v1/user/registerUser")) {
            // navyshop -> dishbesideshockey$ironoriginhoblike: vcode?, username(email), password(MD5)
            String email = field(m, "username").trim();
            String pass  = field(m, "password").trim();
            String vcode = field(m, "vcode").trim();
            if (email.isEmpty() || email.indexOf('@') < 0) {
                body = err(20000, "Email tidak valid — isi email yang benar di kolom pendaftaran.");
            } else if (!md5ok(pass)) {
                body = err(20000, "Kata sandi kosong atau tidak valid.");
            } else if (UserAccounts.find(email) != null) {
                body = err(20001, "Email sudah terdaftar — silakan LOGIN dengan email tersebut.");
            } else {
                UserAccounts.Rec r = UserAccounts.create(email, pass.toLowerCase());
                if (r == null) {
                    body = err(20000, "Pendaftaran gagal — coba lagi.");
                } else {
                    AccountStore.setCurrent(r.uid, r.email, r.token, "0");
                    body = AccountStore.sessionResponse(r.uid, r.email, r.token, "1", "0");
                    UserAccounts.sysLog("AKUN-BARU-EMAIL uid=" + r.uid + " email=" + r.email);
                }
            }
            extra = "REGISTER-USER (tombol DAFTAR di form email) | tanpa konfirmasi email "
                    + "(isNeedVerify tidak ada di manifest meta-data — vcode opsional"
                    + (vcode.isEmpty() ? ", tidak dikirim" : ", diabaikan server") + ") "
                    + "| sukses = session isGuest:0 openType:1 isNewUser:0 | "
                    + "akun tersimpan server/data/accounts.json | respCT=application/json";
        } else if (p.equals("/v1/user/loginByName")) {
            // timidcompletely -> dishbesideshockey$gangclothing: username, password(MD5)
            String email = field(m, "username").trim();
            String pass  = field(m, "password").trim();
            if (email.isEmpty()) {
                body = err(20000, "Email masih kosong — isi email dan kata sandi.");
            } else if (!md5ok(pass)) {
                body = err(20000, "Kata sandi masih kosong.");
            } else {
                UserAccounts.Rec r = UserAccounts.find(email);
                if (r == null) {
                    body = err(20001, "Email belum terdaftar — tekan DAFTAR (Register) untuk membuat akun baru.");
                } else if (!r.passMd5.equals(pass.toLowerCase())) {
                    body = err(20002, "Kata sandi salah — cek ulang atau gunakan akun lain.");
                } else {
                    AccountStore.setCurrent(r.uid, r.email, r.token, "0");
                    body = AccountStore.sessionResponse(r.uid, r.email, r.token, "1", "0");
                    UserAccounts.sysLog("AKUN-LOGIN-EMAIL uid=" + r.uid + " email=" + r.email);
                }
            }
            extra = "LOGIN-BY-NAME (tombol LOGIN di form email) | verifikasi email+sandi "
                    + "di server/data/accounts.json | sukses = session isGuest:0 openType:1 "
                    + "| respCT=application/json";
        } else if (p.equals("/v1/user/autoLogin")) {
            // QuickGameSdkService: params authToken (token tersimpan klien)
            String authTok = field(m, "authToken").trim();
            AccountStore g = AccountStore.loadOrCreate();
            if (authTok.length() > 0 && authTok.equals(g.token)) {
                body = g.sdkDataResponse(false);
                extra = "AUTO-LOGIN (tamu) | authToken cocok akun tamu tersimpan "
                        + "(hardlinerspare -> service.cointruth) | respCT=application/json";
            } else {
                UserAccounts.Rec r = authTok.length() > 0 ? UserAccounts.findByToken(authTok) : null;
                if (r != null) {
                    AccountStore.setCurrent(r.uid, r.email, r.token, "0");
                    body = AccountStore.sessionResponse(r.uid, r.email, r.token, "1", "0");
                    extra = "AUTO-LOGIN (email) | authToken cocok akun terdaftar " + r.email
                            + " | session isGuest:0 openType:1 | respCT=application/json";
                } else {
                    body = g.sdkDataResponse(false);
                    extra = "AUTO-LOGIN fallback ke akun tamu (authToken tidak cocok "
                            + (authTok.isEmpty() ? "- kosong" : "- tidak dikenal")
                            + "; jalur launch pertama / clear data) | respCT=application/json";
                }
            }
        } else if (p.equals("/v1/user/sendCodeByEmail")) {
            // alur vcode tidak wajib (isNeedVerify false) — balas sukses agar UI tidak macet
            body = OK_EMPTY;
            extra = "SEND-CODE-BY-EMAIL | server lokal tidak mengirim email; vcode TIDAK "
                    + "diperlukan (meta-data isNeedVerify tidak ada) — kode apapun diterima "
                    + "| respCT=application/json";
        } else if (p.equals("/v1/user/findPassByEmail")) {
            String email = field(m, "username").trim();
            if (email.isEmpty()) email = field(m, "email").trim();
            if (UserAccounts.find(email) == null) {
                body = err(20001, "Email tidak ditemukan — belum terdaftar di server lokal ini.");
            } else {
                body = err(20003, "Lupa kata sandi lewat email tidak tersedia di server lokal "
                        + "(tidak ada email keluar). Gunakan Ganti Kata Sandi di Pusat Akun, "
                        + "atau daftar dengan email lain.");
            }
            extra = "FIND-PASS-BY-EMAIL | server lokal TANPA email keluar — jawaban jujur "
                    + "biar UI tidak menggantung | respCT=application/json";
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
                    body = err(20001, "Akun tidak ditemukan di server lokal.");
                }
            }
            extra = "GET-USER-INFO (Pusat Akun) | info akun by uid (tamu/email) "
                    + "| respCT=application/json";
        } else if (p.equals("/v1/auth/changePassword")) {
            // dishbesideshockey$famediscussionsmall: uid, oldPassword(MD5), newPassword(MD5)
            String uid = field(m, "uid").trim();
            String oldP = field(m, "oldPassword").trim();
            String newP = field(m, "newPassword").trim();
            if (uid.isEmpty()) {
                body = err(20000, "uid kosong — bukan dari sesi yang sah.");
            } else if (AccountStore.loadOrCreate().uid.equals(uid)) {
                body = err(20004, "Akun TAMU tidak punya kata sandi. Gunakan Pusat Akun -> "
                        + "Bind Email untuk menambah email+sandi ke akun ini.");
            } else if (!md5ok(newP)) {
                body = err(20000, "Kata sandi baru tidak valid.");
            } else if (!UserAccounts.changePassword(uid, oldP.toLowerCase(), newP.toLowerCase())) {
                body = err(20002, "Kata sandi lama salah.");
            } else {
                body = OK_EMPTY;
                UserAccounts.sysLog("SANDI-GANTI uid=" + uid);
            }
            extra = "CHANGE-PASSWORD (Pusat Akun) | verifikasi oldPassword -> set newPassword "
                    + "di accounts.json | respCT=application/json";
        } else if (p.equals("/v1/auth/bindMail")) {
            // dishbesideshockey$reweavingsiamesedpropertylessnesses: uid, email, code, password(MD5)
            String uid = field(m, "uid").trim();
            String email = field(m, "email").trim();
            String code = field(m, "code").trim();
            String pass = field(m, "password").trim();
            if (email.isEmpty() || email.indexOf('@') < 0) {
                body = err(20000, "Email tidak valid.");
            } else if (!md5ok(pass)) {
                body = err(20000, "Kata sandi tidak valid.");
            } else if (UserAccounts.find(email) != null) {
                body = err(20001, "Email sudah dipakai akun lain.");
            } else if (UserAccounts.bindToExistingUid(uid, email, pass.toLowerCase()) == null) {
                body = err(20000, "Bind email gagal — akun tidak dikenal.");
            } else {
                body = OK_EMPTY;
                UserAccounts.sysLog("BIND-MAIL uid=" + uid + " email=" + email.toLowerCase()
                        + " (code diabaikan: " + (code.isEmpty() ? "kosong" : "dikirim") + ")");
            }
            extra = "BIND-MAIL (Pusat Akun — tambah email+sandi ke akun AKTIF) | uid/token "
                    + "akun TIDAK berubah (progres game aman), email jadi cara login baru "
                    + "| kode verifikasi diabaikan (tanpa email keluar) | respCT=application/json";
        } else if (p.equals("/v1/system/getNotice")) {
            body = OK_EMPTY;
            extra = "getNotice kosong: result=true data=\"\" (len 0 <=2 -> callback skip notice, "
                    + "try/catch aman) | respCT=application/json";
        } else if (p.equals("/v1/system/dmsg") || p.equals("/v1/system/onNewToken")
                || p.equals("/v1/system/getAgreement")) {
            body = OK_EMPTY;
            extra = "system path lain -> sukses kosong | respCT=application/json";
        } else if (p.equals("/v1/auth/setGameRoleInfo")) {
            body = OK_EMPTY;
            extra = "SET-GAME-ROLE | laporan role game (cointhreat.smali setGameRoleInfo) "
                    + "-> result:true kosong | respCT=application/json";
        } else if (p.equals("/v1/user/ckRegVistor")) {
            // Bagian 27 — tombol Kode Migrasi (onCDKeyClicked -> MSG_CHECK_REG_VISITOR).
            // Mirror PERSIS server asli (replay 23 Nov: result:true, data:{isReged:0}).
            // Dengan loginTypes ["1"] tombol ini tidak tampil lagi di layar login.
            body = CKREG_OK;
            extra = "CK-REG-VISITOR (Kode Migrasi) | mirror PERSIS server asli "
                    + "(result:true isReged:0) | tombol ini sudah TIDAK ditampilkan "
                    + "sejak loginTypes [\"1\"] | respCT=application/json";
        } else if (p.equals("/v1/user/getServerAgreement")) {
            body = OK_EMPTY;
            extra = "agreement kosong (dishbesideshockey/dishbesideshockey) | "
                    + "respCT=application/json";
        } else {
            body = ENVELOPE;
            extra = "endpoint BELUM tersedia di server lokal (result:false, SDK tidak mendapat "
                    + "jawaban valid) | SDK memanggil tombol/fitur yang belum dipetakan "
                    + "| respCT=application/json";
        }

        Map<String, String> h = HttpWorker.baseHeaders("application/json");
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
