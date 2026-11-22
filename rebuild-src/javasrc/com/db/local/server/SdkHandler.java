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
 *  - com/quickgame/android/sdk/psalmicvolleyball/singersmooth: setelah connect("/v1/system/init")
 *    klien baca getBoolean("result"); jika FALSE hanya QGLog lalu RETURN — callback init TIDAK
 *    pernah dipanggil → game menunggu selamanya = stuck tanpa popup (log owner 2026-11-22:
 *    seq 17 result:false, lalu tidak ada request apa pun lagi).
 *  - com/quickgame/android/sdk/model/e.cointhreat(String) (parser data init):
 *      * kunci "payTypes" WAJIB ADA — jika tidak ada → "pay type no key" → return null
 *        → callback tidak dipanggil → stuck;
 *      * field lain semuanya has()-guarded (opsional);
 *      * "version" dilewatkan → model g null → runnable 'version update' (popup download)
 *        tidak pernah dijalankan (kondisional di singersmooth$cointhreat);
 *      * "nodes" dilewatkan → daftar server SDK tidak di-set (GetServerList = jalur socket.io
 *        5050, bukan jalur ini).
 *  - com/quickgame/android/sdk/singersmooth.cointhreat(model e) (callback sukses):
 *      * panggil /v1/system/getNotice (try/catch; result:false atau data <=2 char →
 *        skip notice, aman);
 *      * lalu SELALU singersmooth$seventygenom → "init success three" →
 *        onInitFinished(true) → game lanjut.
 *
 * Respons init sukses (dikarang MINIMAL sesuai ekspektasi parser klien — direkam di extra):
 *   {"result":true,"data":{"payTypes":[],"loginTypes":["12"]}}
 * Envelope capture live 14.A#8 (result:false id 20000) TETAP dipakai untuk path lain yang
 * belum dipetakan — semua request direkam utuh di logger untuk iterasi berikutnya.
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
        } else if (p.equals("/v1/system/getNotice")) {
            body = OK_EMPTY;
            extra = "getNotice kosong: result=true data=\"\" (len 0 <=2 -> callback skip notice, "
                    + "try/catch aman) | respCT=application/json";
        } else if (p.equals("/v1/system/dmsg") || p.equals("/v1/system/onNewToken")
                || p.equals("/v1/system/getAgreement")) {
            body = OK_EMPTY;
            extra = "system path lain -> sukses kosong | respCT=application/json";
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
}
