package com.db.local.server;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * SERVER SDK LOKAL — port 8610 (config sdkPort). HTTP MURNI — SATU PERAN SATU
 * PORT (pemisahan 0.0.2, pola 5-server asli; owner 2026-11-14: "pola server
 * aslinya punya 5 server utama.. SDK, login, main, chat, dungeon, terpisah
 * dengan berbagai port" dan "SDK dan loginserver harus matang sempurna rapi").
 *
 * Dulu (0.0.1 ke bawah): API SDK ditumpangkan DI DALAM LoginServer :8610 —
 * satu port dua peran (dispatch /v1/* di LoginServer.serve). Kini terpisah:
 *   SdkServer :8610 → HANYA API SDK (layar login SDK asli, QHinfo menunjuk
 *                     ke sini; loginchecknative shell juga di sini).
 *   LoginServer :8611 → HANYA socket.io login (engine.io v3 + socket.io v2).
 *
 * KAWAT (semua hasil bedah dex com.quickgame.android.sdk + PROBE LANGSUNG
 * server asli qsdk.t4game.com — README §2.11) — TIDAK BERUBAH, pindah rumah:
 *   body  = form-urlencoded: sv=v2 & data=<base64(JSON param)> & sign=<md5>
 *   sign  = md5(k=v&… urut abjad + rahasia "0b2a18e45d7df321")
 *   jawab = {"result":bool,"data":…,"error":{"id":N,"message":"…"}}
 *
 * Endpoint (respons = salinan respons asli hasil probe):
 *   POST /v1/system/init | getNotice | getAgreement | onNewToken | dmsg
 *   POST /v1/user/registerVisitor | autoLogin | loginByName | registerUser
 *        | ckRegVistor | findPassByEmail | postGooglePlayVerify
 *   POST /v1/auth/* (bindMail, changePassword, setGameRoleInfo, jianchaLayLx, …)
 *   POST /loginchecknative — validasi shell setelah layar SDK (login.popoh5.com:510)
 *
 * Mode ONLINE: semua permintaan SDK DITERUSKAN utuh ke qsdk.t4game.com —
 * perilaku online = perilaku asli (SdkApi yang memutuskan mode).
 *
 * Alasan body POST dibaca DI SINI (bukan di SdkApi): stream NanoHTTPD hanya
 * bisa dibaca sekali — dibaca dulu di serve() lalu diteruskan sebagai string.
 */
public final class SdkServer extends NanoHTTPD {

    public SdkServer(String bind, int port) throws java.io.IOException {
        super(bind, port);
        setTempFileManagerFactory(new TempFileManagerFactory() {
            public TempFileManager create() {
                return new DefaultTempFileManager();
            }
        });
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method m = session.getMethod();
        String uri = session.getUri();
        Map<String, String> parms = session.getParms();
        try {
            boolean sdkRoute = uri.startsWith("/v1/") || uri.equals("/loginchecknative")
                    || uri.equals("/loginchecknative/");
            if (!sdkRoute) {
                return text(Response.Status.NOT_FOUND,
                        "Server SDK lokal melayani API SDK saja: /v1/* + /loginchecknative.\n"
                                + "Protokol LOGIN (socket.io) ada di port " + Config.loginPort()
                                + " — dasbor developer di port " + Config.dasborPort() + ".");
            }
            String body = (Method.POST == m) ? readBody(session) : "";
            Response r = SdkApi.handle(m, uri, parms, body);
            if (r != null) {
                return r;
            }
            return text(Response.Status.NOT_FOUND,
                    "Rute SDK tidak dikenal: " + uri);
        } catch (Throwable t) {
            try {
                HumanLog.ev("⚠️", "Server SDK lokal gagal memproses permintaan — dijawab 500", String.valueOf(t));
            } catch (Throwable ignored) {
            }
            return text(Response.Status.INTERNAL_ERROR, "kesalahan server SDK lokal: " + t);
        }
    }

    /**
     * Baca body POST utuh — delegasi ke LoginServer.readBody (stream mentah;
     * parseBody bawaan NanoHTTPD vendored memakai aturan form untuk
     * x-www-form-urlencoded sehingga isi bisa hilang — baca langsung tahan
     * SEMUA Content-Type: curl, debugger, WebView varian apa pun).
     */
    private static String readBody(IHTTPSession session) throws Exception {
        return LoginServer.readBody(session);
    }

    private static Response text(Response.Status st, String msg) {
        return newFixedLengthResponse(st, "text/plain; charset=utf-8", msg);
    }
}
