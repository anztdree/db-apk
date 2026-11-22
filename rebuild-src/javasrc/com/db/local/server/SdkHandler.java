package com.db.local.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

/**
 * Listener 9530 — mirror qsdk.t4game.com:80 (API SDK t4game: 33 path /v1/* + /v2/purchase,
 * kontrak 14.A#8). Respons = envelope asli hasil capture live (backup verify-artifacts):
 *   {"result":false,"data":[],"error":{"id":20000,"message":"Params error"}}  (HTTP 200)
 * Request yang masuk SEMUA di-log — v3 (Bagian 18): body preview s.d. 4 KB +
 * header request + respCT di extra (mode PORT memperlihatkan parameter yang dikirim
 * game — kunci diagnosis popup karena /v1/system/init result:false).
 * Tidak ada data game yang dikarang — hanya envelope yang benar-benar tertangkap.
 */
public final class SdkHandler implements HttpWorker.Handler {
    public static final String ENVELOPE =
            "{\"result\":false,\"data\":[],\"error\":{\"id\":20000,\"message\":\"Params error\"}}";

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

        Map<String, String> h = HttpWorker.baseHeaders("application/json");
        h.put("Access-Control-Allow-Origin", "*");
        byte[] body = HttpWorker.text(ENVELOPE);
        int status = 200;
        HttpWorker.writeResponse(out, status, "OK", h, body);

        e.status = status;
        e.respLen = body.length;
        e.respPreview = ENVELOPE;
        e.extra = "respCT=application/json | envelope capture live 14.A#8 (result:false — TIDAK dikarang)";
        e.durMs = System.currentTimeMillis() - t0;
        LoggerCore.log(e);
        return true;
    }
}
