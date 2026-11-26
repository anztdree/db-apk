package com.db.local.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Inti mini-server: SATU proses, 5 listener, SEMUA bind 127.0.0.1 (peta 16.5):
 *   9527 → StaticHandler (config bin, XOR DragonBall decode di log)
 *   9528 → StaticHandler + SocketIoHandler-login (path /socket.io/ saja —
 *          v3 FIX STUCK: runtime XHR Egret terbukti log device 6 sesi HANYA
 *          pernah mencapai origin halaman (9528); io.connect ke 127.0.0.1:5050
 *          TIDAK PERNAH menghasilkan satu pun request di log = game stuck
 *          "selesai loading" selamanya. serversetting.json loginserver +
 *          config bin loginPort kini 9528 (origin halaman) — socket.io dijawab
 *          DI PORT YANG SAMA, apa pun perilaku parser URL runtime.
 *   5050 → SocketIoHandler LOGIN (socket.io v2.1.2 EIO=3 + /loginchecknative —
 *          tetap: URL native Java = loginServer + loginWebPort, HttpURLConnection
 *          terbukti sampai 5050 di semua log device)
 *   5051 → SocketIoHandler GAME  (socket.io v2.1.2 EIO=3 — serverItem.url, Bagian 21)
 *   9530 → SdkHandler (33 path API SDK, envelope capture live)
 *   4000 → LoggerHandler (fallback 4001 bila bind gagal — 15.2/16.2)
 * Murni java.* (tanpa import android.*) agar bisa diuji di desktop JVM sebelum
 * di-dex ke classes2.dex.
 */
public final class ServerCore {

    public static final class StartResult {
        public final int loggerPort;
        public final boolean loggerFallback;
        public StartResult(int loggerPort, boolean loggerFallback) {
            this.loggerPort = loggerPort; this.loggerFallback = loggerFallback;
        }
    }

    /**
     * @param docroot        folder server/ hasil ekstraksi (isi = bs/…)
     * @param assets         sumber asset fallback (null pada desktop) untuk 9527
     * @param readyEarly     latch ekstraksi tahap awal (db/ + html + manifest + js)
     */
    public static StartResult start(final java.io.File docroot,
                                    final StaticHandler.AssetsSource assets,
                                    final CountDownLatch readyEarly) {
        final StaticHandler configHandler = new StaticHandler(docroot, assets, null, LoggerCore.PORT_CONFIG);
        // v4 (Bagian 19): CDN handler JUGA dapat AssetsSource — fallback generik
        // anti-race ekstraksi (base.version dkk. dilayani dari assets saat ekstraksi
        // belum selesai → popup "Server base.version is Not Exist" tidak pernah terjadi)
        final StaticHandler cdnHandler = new StaticHandler(docroot, assets, readyEarly, LoggerCore.PORT_CDN);
        // Bagian 72 CAPTURE CLIENT — penerima event halaman game (console/js
        // error/pemuatan script & media/XHR yang TIDAK PERNAH sampai ke
        // listener HTTP karena dijawab shell EgretNative dari penyimpanan
        // lokal). Arahan owner: "kalau loading ya dia itu loading apa !
        // lihat log eruda sebagai contoh goblok !!"
        final ClientLogHandler clientLog = new ClientLogHandler();
        final SdkHandler sdk = new SdkHandler();
        final SocketIoHandler login = new SocketIoHandler(false);
        // v20 (Bagian 67, oke owner "implementasi dengan ini"): 5050/9528
        // socket.io = ACK-GATE LOGIN-SERVER — event dijawab per file
        // <action>.json (LoginAck; file kosong {} = NO ACK; SaveUserEnterInfo
        // selalu dijawab tanpa file). Log = GLOBAL 1 (logs/ NDJSON).
        // 5051 tetap TRANSPORT ONLY (fase gameServer berikutnya).
        final SocketIoHandler game = new SocketIoHandler(true);

        int loggerPort = LoggerCore.PORT_LOGGER;
        boolean fallback = false;
        ServerSocket loggerSock = tryBind(LoggerCore.PORT_LOGGER);
        if (loggerSock == null) {
            loggerSock = tryBind(LoggerCore.PORT_FALLBACK);
            fallback = true;
            loggerPort = LoggerCore.PORT_FALLBACK;
        }
        final int fLoggerPort = loggerPort;
        final ServerSocket fLoggerSock = loggerSock;
        final LoggerHandler fLoggerHandler = new LoggerHandler(fLoggerPort);

        // v3 FIX STUCK — chain path-based di 9528: /socket.io/ → SocketIoHandler
        // (login), sisanya → StaticHandler. Alasan: satu-satunya host:port yang
        // runtime XHR Egret bukti tercapai = origin halaman (semua request JS di
        // 6 log device = 9528); socket.io client mengirim URL absolut
        // http://127.0.0.1:9528/socket.io/ — tercapai baik bila runtime
        // menghormati URL absolut MAUPUN bila ia memaksa origin halaman.
        final HttpWorker.Handler cdnChain = (req, out, sock) -> {
            String p = (req.path == null || req.path.isEmpty()) ? req.rawPath : req.path;
            if (p != null && p.startsWith("/log/client")) {
                return clientLog.handle(req, out, sock);
            }
            if (p != null && (p.startsWith("/socket.io/") || p.startsWith("/socket.io?"))) {
                return login.handle(req, out, sock);
            }
            return cdnHandler.handle(req, out, sock);
        };
        bindAndServe(LoggerCore.PORT_CONFIG, configHandler, "config bin (mirror configus…:443)");
        bindAndServe(LoggerCore.PORT_CDN, cdnChain, "CDN game + socket.io login + /log/client capture (mirror dragonh5cdn…:443)");
        bindAndServe(LoggerCore.PORT_LOGIN, login, "login socket.io v2.1.2 EIO=3 + loginchecknative (mirror login…:610)");
        bindAndServe(LoggerCore.PORT_GAME, game, "server game socket.io v2.1.2 EIO=3 (serverItem.url — Bagian 21)");
        bindAndServe(LoggerCore.PORT_SDK, sdk, "API SDK t4game (mirror qsdk.t4game.com:80)");

        LoggerCore.fallbackLogger = fallback;
        if (fLoggerSock != null) {
            Thread t = new Thread(() -> serveLoop(fLoggerSock, fLoggerHandler, fLoggerPort),
                    "logger-" + fLoggerPort);
            t.setDaemon(true);
            t.start();
            LoggerCore.BINDS.put(String.valueOf(fLoggerPort),
                    "logger 2-mode" + (fallback ? " (fallback)" : "") + " — OK");
        } else {
            LoggerCore.BINDS.put("4000", "bind FAILED (4000 and 4001 both busy)");
        }
        logBinds();
        return new StartResult(loggerPort, fallback);
    }

    /** v3: hasil bind ikut masuk log (kind SYS) — kelihatan di dashboard & disk */
    private static void logBinds() {
        synchronized (LoggerCore.BINDS) {
            for (Map.Entry<String, String> en : LoggerCore.BINDS.entrySet()) {
                LoggerCore.Entry e = new LoggerCore.Entry();
                e.port = LoggerCore.PORT_LOGGER;
                e.kind = "SYS";
                e.method = "BIND";
                e.path = "port " + en.getKey() + " — " + en.getValue();
                e.status = en.getValue().contains("OK") ? 0 : 500;
                LoggerCore.log(e);
            }
        }
    }

    private static void bindAndServe(int port, HttpWorker.Handler handler, String label) {
        ServerSocket ss = tryBind(port);
        if (ss == null) {
            LoggerCore.BINDS.put(String.valueOf(port), label + " — bind FAILED (port in use?)");
            return;
        }
        Thread t = new Thread(() -> serveLoop(ss, handler, port), "listener-" + port);
        t.setDaemon(true);
        t.start();
        LoggerCore.BINDS.put(String.valueOf(port), label + " — OK");
    }

    private static ServerSocket tryBind(int port) {
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 64);
            return ss;
        } catch (IOException ioe) {
            return null;
        }
    }

    private static void serveLoop(final ServerSocket ss, final HttpWorker.Handler handler, final int port) {
        while (true) {
            try {
                final Socket sock = ss.accept();
                sock.setTcpNoDelay(true);
                Thread w = new Thread(() -> new HttpWorker(sock, handler, port).run(), "conn-" + port);
                w.setDaemon(true);
                w.start();
            } catch (IOException ioe) {
                LoggerCore.BINDS.put(String.valueOf(port),
                        "serveLoop stopped: " + ioe.getMessage());
                break;
            } catch (Throwable t) {
                // jangan pernah matikan listener karena 1 koneksi error
            }
        }
    }
}
