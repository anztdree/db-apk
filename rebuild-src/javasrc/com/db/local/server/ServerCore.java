package com.db.local.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

/**
 * Inti mini-server: SATU proses, 5 listener, SEMUA bind 127.0.0.1 (peta 16.5):
 *   9527 → StaticHandler (config bin, XOR DragonBall decode di log)
 *   9528 → StaticHandler (CDN game + upgrade + serversetting.json)
 *   5050 → SocketIoHandler (socket.io v2.1.2 EIO=3 — config utama gameplay)
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
        final StaticHandler cdnHandler = new StaticHandler(docroot, null, readyEarly, LoggerCore.PORT_CDN);
        final SdkHandler sdk = new SdkHandler();
        final SocketIoHandler login = new SocketIoHandler();

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

        bindAndServe(LoggerCore.PORT_CONFIG, configHandler, "config bin (mirror configus…:443)");
        bindAndServe(LoggerCore.PORT_CDN, cdnHandler, "CDN game (mirror dragonh5cdn…:443)");
        bindAndServe(LoggerCore.PORT_LOGIN, login, "login socket.io v2.1.2 EIO=3 (mirror login…:610)");
        bindAndServe(LoggerCore.PORT_SDK, sdk, "API SDK t4game (mirror qsdk.t4game.com:80)");

        LoggerCore.fallbackLogger = fallback;
        if (fLoggerSock != null) {
            Thread t = new Thread(() -> serveLoop(fLoggerSock, fLoggerHandler, fLoggerPort),
                    "logger-" + fLoggerPort);
            t.setDaemon(true);
            t.start();
            LoggerCore.BINDS.put(String.valueOf(fLoggerPort),
                    "logger 2 mode" + (fallback ? " (fallback)" : "") + " — AKTIF");
        } else {
            LoggerCore.BINDS.put("4000", "GAGAL bind (4000 dan 4001)");
        }
        return new StartResult(loggerPort, fallback);
    }

    private static void bindAndServe(int port, HttpWorker.Handler handler, String label) {
        ServerSocket ss = tryBind(port);
        if (ss == null) {
            LoggerCore.BINDS.put(String.valueOf(port), label + " — GAGAL bind (port dipakai?)");
            return;
        }
        Thread t = new Thread(() -> serveLoop(ss, handler, port), "listener-" + port);
        t.setDaemon(true);
        t.start();
        LoggerCore.BINDS.put(String.valueOf(port), label + " — AKTIF");
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
                        "serveLoop berhenti: " + ioe.getMessage());
                break;
            } catch (Throwable t) {
                // jangan pernah matikan listener karena 1 koneksi error
            }
        }
    }
}
