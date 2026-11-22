package com.db.local.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

/**
 * Listener 9530 — mirror qsdk.t4game.com:80 (API SDK t4game: 33 path /v1/* + /v2/purchase,
 * kontrak 14.A#8).
 *
 * Bagian 20 — FIX "stuck tanpa popup" (bukti smali klien):
 *  - init result:true + payTypes + loginTypes ["12"] tanpa version/nodes.
 *
 * Bagian 21 — LOGIN NATURAL TANPA LAYAR "MIGRATION CODE" (bukti smali + JS):
 *  1. /v1/user/registerVisitor  — guest auto-register (HWLoginActivity jalur
 *     ACTION_AUTO_REGISTER_AND_LOGIN → dishbesideshockey$nationalcommunitymissing$
 *     seventygenom). Respons {result:true, data:"<session>"} diparse
 *     psalmicvolleyball/cointhreat.cointruth → authToken WAJIB + userData WAJIB.
 *  2. /v1/user/autoLogin — login token tersimpan (launch berikutnya,
 *     dishbesideshockey$hardlinerspare → service.cointruth(token)).
 *  3. /loginchecknative (GET, loginServer:loginWebPort = 127.0.0.1:5050) —
 *     dipanggil game wrapper pursetruechild/feedtonight/singersmooth.onLoginFinished
 *     dengan query uid/username/token/usermode; body mentah menjadi thirdParams
 *     untuk JS (getSdkLoginInfo), lalu divalidasi getInt("code")==0 →
 *     MainActivity.customreading() → WebView index-native.html lanjut.
 *  4. Akun tamu PERSISTEN (AccountStore → <serverRoot>/data/account.json) —
 *     UID/token sama antar restart seperti server nyata.
 * Path lain yang belum dipetakan tetap envelope capture 14.A#8 dan terekam utuh.
 */
public final class SdkHandler implements HttpWorker.Handler {

    /** Envelope capture live 14.A#8 — fallback untuk path yang belum dipetakan. */
    public static final String ENVELOPE =
            "{\"result\":false,\"data\":[],\"error\":{\"id\":20000,\"message\":\"Params error\"}}";

    /** Init sukses — minimal & cukup menurut parser model/e.cointhreat(String) di smali. */
    public static final String INIT_OK =
            "{\"result\":true,\"data\":{\"payTypes\":[],\"loginTypes\":[\"12\"]}}";

    /** Sukses kosong — getNotice/dmsg/onNewToken/getAgreement (tidak ada isi untuk ditampilkan). */
    public static final String OK_EMPTY = "{\"result\":true,\"data\":\"\"}";

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

        if (p.equals("/v1/system/init")) {
            body = INIT_OK;
            extra = "INIT-SUCCESS 20.1 | data dari parsing smali model/e.cointhreat: "
                    + "payTypes kunci WAJIB (tanpa itu parser return null -> callback tidak "
                    + "dipanggil -> stuck); loginTypes=[12] tanpa cek GMS; TANPA version "
                    + "(hindari runnable 'version update'), TANPA nodes (GetServerList=jalur 5050) "
                    + "| respCT=application/json";
        } else if (p.equals("/v1/user/registerVisitor")) {
            AccountStore a = AccountStore.loadOrCreate();
            body = a.sdkDataResponse(a.isNewUser);
            extra = "REGISTER-VISITOR 21 | guest auto-register (HWLoginActivity "
                    + "ACTION_AUTO_REGISTER_AND_LOGIN -> dishbesideshockey$nationalcommunitymissing"
                    + "$seventygenom); session: authToken+userData (uid/username/displayUid/"
                    + "token/isGuest/openType) persis parser psalmicvolleyball/cointhreat; "
                    + "akun PERSISTEN di server/data/account.json (uid tetap antar restart) "
                    + "| respCT=application/json";
        } else if (p.equals("/v1/user/autoLogin")) {
            AccountStore a = AccountStore.loadOrCreate();
            body = a.sdkDataResponse(false);
            extra = "AUTO-LOGIN 21 | token tersimpan (dishbesideshockey$hardlinerspare -> "
                    + "service.cointruth authToken) -> session sama seperti registerVisitor "
                    + "| respCT=application/json";
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
            extra = "SET-GAME-ROLE 21 | laporan role game (cointhreat.smali setGameRoleInfo) "
                    + "-> result:true kosong | respCT=application/json";
        } else if (p.equals("/v1/user/getServerAgreement")) {
            body = OK_EMPTY;
            extra = "agreement kosong (dishbesideshockey/dishbesideshockey) | "
                    + "respCT=application/json";
        } else {
            body = ENVELOPE;
            extra = "envelope capture live 14.A#8 (result:false — TIDAK dikarang) | "
                    + "path belum dipetakan — direkam untuk iterasi berikutnya | respCT=application/json";
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
     */
    public static byte[] loginCheckNativeBody() {
        return HttpWorker.text(AccountStore.loadOrCreate().loginCheckNative());
    }
}
