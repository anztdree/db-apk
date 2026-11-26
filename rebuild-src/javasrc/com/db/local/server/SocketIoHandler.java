package com.db.local.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HashMap;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Listener 5050 (LOGIN) & 5051 (GAME) — socket.io v2.1.2 (EIO=3).
 *
 * Basis polling/WS 1:1 capture live server asli (Bagian 17, logs backup):
 *   - open: 96 char JSON sid 20 + pingInterval 25000 + pingTimeout 5000, 103 byte
 *   - POST → "ok" text/html; GET long-poll hold; sid unknown → 400 code:1; close → 1
 *   - WS upgrade 101, ping 0x9 → pong 0xA
 *
 * v18 — TRANSPORT ONLY, NOL ACK EVENT (arahan owner, kata-kata persis):
 *   "muncul serverlist harusnya itu sudah bagian dari loginserver yg belum
 *    kita buat , artinya anda tidak memisahkan tugas sebagai mana mestinya..
 *    SDK ya fokus di SDK bukan mlah ngurusin loginserver walaupun itu emang
 *    terkait . dan loginserver pun belum kita rencanakan lebih lanjut ."
 *   Kesalahan 2 v17: listener ini menjawab ACK `User/GetServerList` dengan
 *   serverList fabrikasi ("DragonBall Local") — ITU PEKERJAAN LOGIN SERVER
 *   yang belum diberi rencana. v18: SEMUA ACK event bisnis DIHAPUS
 *   (routeEvent/GameData/ack dihapus). Listener kini murni TRANSPORT:
 *   - engine.io handshake/open/poll/WS upgrade/probe/ping-pong/close —
 *     1:1 capture live server asli (Bagian 17) — transport, bukan loginServer.
 *   - /loginchecknative tetap (glue SDK native: pursetruechild/feedtonight/
 *     singersmooth.onLoginFinished — getInt("code")==0 → WebView lanjut) —
 *     SCOPE SDK, bukan loginServer.
 *   - Setiap frame event `42…` handler.process dicatat VERBATIM PENUH (nol
 *     pangkas) di logger dengan penanda "EVENT (no ack) ... loginServer scope
 *     — not implemented (plan only)" — NOL balasan. Callback JS tidak pernah
 *     dipanggil; game berhenti sebelum layar server list — batas wajar
 *     selama loginServer belum dibangun. Data verbatim ini = bahan perencanaan
 *     login server (docs/LOGIN-SERVER-PLAN.md).
 *
 * LOGIN (5050) — sisa scope SDK:
 *   - /loginchecknative (GET): body raw = thirdParams JS;
 *     divalidasi getInt("code")==0 → WebView lanjut.
 *
 * v20 (Bagian 67) — ACK-GATE LOGIN-SERVER AKTIF di listener login (oke owner
 *   "implementasi dengan ini — tanpa log !! log global jdi 1"): event
 *   handler.process dijawab per file <action>.json (1 event atlas = 1 file,
 *   LoginAck + LoginServerConfig); file kosong {} = NO ACK; SaveUserEnterInfo
 *   selalu dijawab tanpa file (konstanta). Listener GAME (5051, gameMode=true)
 *   tetap TRANSPORT ONLY sampai fase gameServer. Log tetap GLOBAL 1 (NDJSON
 *   logs/ — TIDAK ada logs/ di login-server/).
 *
 * v21 (Bagian 69) — FIX "SERVERLIST TIDAK MUNCUL" (bukti device owner:
 *   release-mod/log-20261126-181501.ndjson seq 58-61):
 *   BUG v20: handleWebSocket menandai s.upgraded=true SEKETIKA saat handshake
 *   101, SEBELUM engine.io selesai upgrade (probe 2probe/3probe bahkan belum
 *   dikirim). Emit GetServerList datang via POST-polling 3 ms kemudian →
 *   deliver() menulis frame 430 ke socket WS → client (MASIH MODE POLLING)
 *   membuangnya → upgrade pun batal (client tak pernah kirim '5') → poll
 *   berikutnya 10 detik KOSONG (seq 61 "long-poll timeout, no data") → ACK
 *   HILANG → serverlist tidak pernah tampil. Log v20 lama mengaku "ACK 430"
 *   padahal frame tidak sampai = log bohong.
 *   FIX v21 (aturan transport 1:1 engine.io):
 *   1. upgraded=true HANYA saat client mengirim packet upgrade '5' via WS
 *      (bukan saat 101) — probe tidak lagi menandai upgrade.
 *   2. ACK routing = transport ASAL emit: emit via POST-polling → ACK SELALU
 *      masuk antre polling (deliverPolling); emit via WS setelah upgrade →
 *      ACK lewat WS langsung (deliverWs). NOL penulisan WS sebelum '5'.
 *   3. Saat '5' diterima: semua packet yang sempat mengantre pindah ke WS
 *      (migrasi), lalu noop '6' masuk antre polling agar long-poll menggantung
 *      segera kembali (perilaku engine.io asli).
 *   4. Semua tulisan WS dikunci wsWriteLock (deliverWs / migrasi / probe /
 *      pong tidak boleh saling menyela frame).
 *   5. LOG JUJUR: extra mencatat KEMANA frame benar-benar dikirim
 *      ("→ dikirim: antre polling" / "→ dikirim: WS langsung") — tidak ada
 *      lagi klaim ACK tanpa bukti transport.
 *   6. Port di log = port listener SEBENARNYA (header __listenerport dari
 *      HttpWorker) — bukti device v20: entri socket.io di 9528 selama ini
 *      salah label "port":5050.
 *
 * v17 — LOG URL PENUH, NOL PANGKAS (arahan owner: "kalau log nampilin url
 *   socket misal http://127.0.0.1:<port>/socket.io/?EIO=3&transport=polling&t=…
 *   yaaa jangan anda pangkas" + "masih ada yg di sembunyikan"):
 *   - SEMUA entri kini membawa URL LENGKAP scheme://host:port + path + query
 *     (fullUrl() — Host header client; fallback 127.0.0.1:<listener>), bukan
 *     lagi path telanjang "/socket.io/?..." ataupun "sid=..." telanjang.
 *   - Frame WS (open/probe/ping/event/close) memakai URL koneksi WS-nya
 *     sendiri; frame open ws-first yang dikirim server kini DILOG juga.
 *   - Pangkalan data packet dihapus: POST packet tampil PENUH verbatim.
 */
public final class SocketIoHandler implements HttpWorker.Handler {
    public static final int PING_INTERVAL = 25000;
    public static final int PING_TIMEOUT = 5000;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final boolean gameMode;

    public SocketIoHandler() { this(false); }
    public SocketIoHandler(boolean gameMode) { this.gameMode = gameMode; }

    public static final class Session {
        public final String sid;
        public final long createdAt = System.currentTimeMillis();
        public final ArrayDeque<String> queue = new ArrayDeque<>();
        public final ReentrantLock lock = new ReentrantLock();
        public volatile boolean closed = false;
        public volatile boolean closePendingNotified = false;

        /* ---- v23 LOG NATURAL (perintah owner: "hlo loading tanpa ada
         * keterangan apapun di log" + "BIAR JELAS KITA ITU KURANGNYA
         * DIMANAAA") — FAKTA sesi yang terlihat di sisi server, ditulis
         * apa adanya di keterangan log: apa yang diterima, apa yang
         * dikirim, upgrade selesai atau tidak. Nol karangan. ---- */
        public int framesIn = 0;              // paket engine.io/socket.io DITERIMA dari client
        public int packetsOut = 0;            // paket yang dikirim/diantrekan ke client
        public final java.util.List<String> eventsSeen =
                new java.util.ArrayList<String>();   // action 42-handler.process (max 20)
        public volatile boolean probeDone = false;   // 2probe diterima (probe WS jalan)
        /** deskripsi payload terakhir yang mengantre polling — muncul di baris
         *  GET yang membawanya ("berisi: ACK 43x … — serverlist SAMPAI …"). */
        public volatile String lastQueuedDesc = null;
        /** v21: true HANYA setelah client mengirim '5' (upgrade engine.io
         *  selesai) — bukan lagi saat handshake 101. */
        public volatile boolean upgraded = false;
        public volatile OutputStream wsOut = null;   // v6: ACK bisa lewat WS langsung
        /** v21: kunci tulis WS — deliverWs / migrasi upgrade / probe / pong
         *  tidak boleh saling menyela frame di socket yang sama. */
        public final Object wsWriteLock = new Object();

        Session(String sid) { this.sid = sid; }

        void offer(String pkt) {
            lock.lock();
            try {
                queue.addLast(pkt);
                if (queue.size() > 64) queue.pollFirst();
            } finally {
                lock.unlock();
            }
        }

        /** v21: ACK utk emit yang datang via WS — lewat WS langsung, TAPI
         *  hanya bila upgrade BENAR-BENAR selesai (client sudah kirim '5').
         *  Bila belum (client masih polling) → antre polling; migrasi di
         *  completeUpgrade() yang meneruskan. */
        String deliverWs(String pkt) {
            OutputStream ws = wsOut;
            if (upgraded && ws != null) {
                try {
                    synchronized (wsWriteLock) { wsSendText(ws, pkt); }
                    return "WS langsung";
                } catch (Throwable t) {
                    // WS putus → jatuh ke antre polling
                }
            }
            offer(pkt);
            return "antre polling";
        }

        /** v21: ACK utk emit yang datang via POST-polling — SELALU antre
         *  polling. Client yang meng-emit lewat polling mendengarkan lewat
         *  polling; frame ditulis ke WS sebelum '5' = dibuang client
         *  (bukti bug v20 di device owner). */
        String deliverPolling(String pkt) {
            offer(pkt);
            return "antre polling";
        }

        /** v21: client selesai upgrade (kirim '5' via WS) — pindahkan semua
         *  packet yang masih mengantre polling ke WS, lalu noop '6' masuk
         *  antre polling agar long-poll menggantung segera kembali
         *  (perilaku engine.io asli: server menutup transport lama dgn noop). */
        String completeUpgrade(OutputStream out) {
            upgraded = true;
            wsOut = out;
            int moved = 0;
            lock.lock();
            try {
                String p;
                while ((p = queue.pollFirst()) != null) {
                    try {
                        synchronized (wsWriteLock) { wsSendText(out, p); }
                        moved++;
                    } catch (Throwable t) {
                        offer(p);   // WS gagal — kembalikan ke antre
                    }
                }
            } finally {
                lock.unlock();
            }
            offer("6");   // noop di transport lama — tanda upgrade selesai
            // v23: noop '6' = payload polling berikutnya — keterangannya
            // muncul di baris GET yang membawanya.
            packetsOut += moved + 1;
            lastQueuedDesc = "noop 6 (upgrade selesai — transport pindah WS)";
            return "upgrade selesai — " + moved + " antre pindah ke WS + noop 6";
        }

        String drain(long waitMs) {
            long deadline = System.currentTimeMillis() + waitMs;
            StringBuilder b = new StringBuilder();
            while (System.currentTimeMillis() < deadline) {
                String p = null;
                lock.lock();
                try {
                    p = queue.pollFirst();
                } finally {
                    lock.unlock();
                }
                if (p != null) {
                    b.append(frame(p));
                    // kumpulkan packet lain yang sudah tersedia tanpa menunggu
                    lock.lock();
                    try {
                        String q;
                        while ((q = queue.pollFirst()) != null) b.append(frame(q));
                    } finally {
                        lock.unlock();
                    }
                    return b.toString();
                }
                try { Thread.sleep(10); } catch (InterruptedException ie) { break; }
            }
            return "";
        }
    }

    static String frame(String pkt) {
        byte[] b = pkt.getBytes(StandardCharsets.UTF_8);
        return String.valueOf(b.length) + ":" + pkt;
    }

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Random RND = new Random();

    static Session createSession() {
        StringBuilder sb = new StringBuilder(20);
        String al = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < 20; i++) sb.append(al.charAt(RND.nextInt(al.length())));
        String sid = sb.toString();
        Session s = new Session(sid);
        SESSIONS.put(sid, s);
        // buang sesi tua bila kelebihan kapasitas
        if (SESSIONS.size() > 256) {
            Session oldest = null;
            for (Session x : SESSIONS.values()) {
                if (oldest == null || x.createdAt < oldest.createdAt) oldest = x;
            }
            if (oldest != null) SESSIONS.remove(oldest.sid);
        }
        return s;
    }

    static String openJson(String sid) {
        // PERSIS format capture live: {"sid":"..20..","upgrades":["websocket"],
        // "pingInterval":25000,"pingTimeout":5000} → 96 char dengan sid 20 char
        return "{\"sid\":\"" + sid + "\",\"upgrades\":[\"websocket\"],\"pingInterval\":"
                + PING_INTERVAL + ",\"pingTimeout\":" + PING_TIMEOUT + "}";
    }

    /**
     * v17 — URL PENUH di log, NOL pangkas (arahan owner: "kalau log nampilin
     * url socket misal http://127.0.0.1:<port>/socket.io/?EIO=3&transport=polling&t=…
     * yaaa jangan anda pangkas"). path log = scheme://host:port + path + query
     * apa adanya — host diambil dari header Host client (127.0.0.1:<listener>),
     * bila tidak ada fallback ke 127.0.0.1:<port listener>.
     */
    static String fullUrl(HttpWorker.Request req, int port) {
        String host = req.header("host");
        if (host == null || host.isEmpty()) host = "127.0.0.1:" + port;
        String rp = req.rawPath == null ? "" : req.rawPath;
        if (!rp.startsWith("/")) rp = "/" + rp;
        return "http://" + host + rp;
    }

    @Override
    public boolean handle(HttpWorker.Request req, OutputStream out, Socket sock) throws IOException {
        long t0 = System.currentTimeMillis();
        LoggerCore.Entry e = new LoggerCore.Entry();
        // v21 (Bagian 69): port di log = port listener SEBENARNYA. Handler
        // login yang sama dilayani di 5050 DAN di-chain di 9528 (ServerCore
        // cdnChain) — v20 lama selalu menulis PORT_LOGIN (5050) sehingga entri
        // socket.io di 9528 salah label (bukti log device: seq 56-61 URL
        // 127.0.0.1:9528 tapi "port":5050 = log membingungkan owner).
        int realPort = -1;
        try {
            String lp = req.header("__listenerport");
            if (lp != null && !lp.isEmpty()) realPort = Integer.parseInt(lp.trim());
        } catch (Throwable ignore) {}
        e.port = realPort > 0 ? realPort : (gameMode ? LoggerCore.PORT_GAME : LoggerCore.PORT_LOGIN);
        e.method = req.method;
        e.path = fullUrl(req, e.port);   // v17: URL PENUH — tanpa pangkas
        e.reqLen = req.body.length;
        e.headers = req.headersStr;
        if (req.body.length > 0) e.reqPreview = StaticHandler.preview(req.body);

        boolean isWs = "websocket".equalsIgnoreCase(req.header("upgrade"));

        String raw = (req.path == null || req.path.isEmpty()) ? req.rawPath : req.path;
        String pNoQ = raw;
        int qi = pNoQ.indexOf('?');
        if (qi >= 0) pNoQ = pNoQ.substring(0, qi);

        // ===== v6: /loginchecknative — hanya mode LOGIN (5050) =====
        // Bagian 28: query params SDK (uid/username/token/usermode) = akun
        // yang BENAR-BENAR login (tamu atau email) — bukti smali
        // pursetruechild/feedtonight/singersmooth.onLoginFinished memetakan
        // getUid()/getUserName()/getToken()/isGuest(). Respons thirdParams
        // memakai nilai tsb, dan disimpan sebagai AKUN AKTIF untuk enterGame.
        if (!gameMode && pNoQ.equals("/loginchecknative")) {
            // req.rawPath = path?query (req.path SUDAH tanpa query!) — parse dari rawPath
            Map<String, String> qp = parseQuery(req.rawPath);
            String uid = qp.get("uid");
            String uname = qp.get("username");
            String tok = qp.get("token");
            String umode = qp.get("usermode");
            AccountStore.setCurrent(uid, uname, tok, umode);
            byte[] bb = HttpWorker.text(AccountStore.loginCheckNativeOf(uid, uname, tok,
                    umode == null || umode.isEmpty() ? "1" : umode));
            Map<String, String> h = HttpWorker.baseHeaders("application/json");
            h.put("Access-Control-Allow-Origin", "*");
            HttpWorker.writeResponse(out, 200, "OK", h, bb);
            e.status = 200;
            e.respLen = bb.length;
            e.respPreview = new String(bb, StandardCharsets.UTF_8);
            e.extra = "loginchecknative uid=" + uid + " username=" + uname
                    + " usermode=" + umode + " respCT=application/json";
            e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return true;
        }

        if ("OPTIONS".equals(req.method)) {
            Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=utf-8");
            h.put("Access-Control-Allow-Origin", "*");
            h.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            h.put("Access-Control-Allow-Headers", "*");
            HttpWorker.writeResponse(out, 204, "No Content", h, new byte[0]);
            e.status = 204; e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return true;
        }

        if (isWs) {
            e.kind = "WS";
            return handleWebSocket(req, out, sock, e, t0);
        }

        String transport = req.q("transport");
        String sid = req.q("sid");

        if (!"polling".equals(transport)) {
            byte[] b = HttpWorker.text("{\"code\":0,\"message\":\"Transport unknown\"}");
            Map<String, String> h = HttpWorker.baseHeaders("application/json");
            h.put("Access-Control-Allow-Origin", "*");
            HttpWorker.writeResponse(out, 400, "Bad Request", h, b);
            e.status = 400; e.respLen = b.length; e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return true;
        }

        // --- POLLING ---
        if ("GET".equals(req.method)) {
            Session s = sid == null ? null : SESSIONS.get(sid);
            if (s == null && sid == null) {
                // OPEN — persis capture live: open + socket.io CONNECT 40 dalam body sama
                // v23: fakta sesi lain yang masih hidup — keterangan koneksi ke-2
                // (bukti device v22: open sesi ke-2 = socket game, log v22 tidak
                // memberi keterangan apa pun — owner: "hlo loading tanpa ada
                // keterangan apapun di log")
                StringBuilder others = new StringBuilder();
                int nOthers = 0;
                boolean loginTerjawab = false;
                for (Session x : SESSIONS.values()) {
                    if (x.closed) continue;
                    nOthers++;
                    if (others.length() > 0) others.append(" | ");
                    others.append("sid=").append(x.sid).append(" (")
                          .append(sessionFacts(x)).append(')');
                    synchronized (x.eventsSeen) {
                        if (!x.eventsSeen.isEmpty()) loginTerjawab = true;
                    }
                }
                s = createSession();
                String body = frame("0" + openJson(s.sid)) + frame("40");
                Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=UTF-8");
                h.put("Access-Control-Allow-Origin", "*");
                h.put("Set-Cookie", "io=" + s.sid + "; Path=/; HttpOnly");
                byte[] bb = body.getBytes(StandardCharsets.UTF_8);
                HttpWorker.writeResponse(out, 200, "OK", h, bb);
                e.status = 200; e.respLen = bb.length;
                e.respPreview = body;
                e.extra = "open sid=" + s.sid;
                if (nOthers > 0) {
                    e.extra += " — sesi aktif lain: " + nOthers + " → " + others;
                    if (loginTerjawab) {
                        e.extra += "; koneksi ke-2 setelah login dijawab = pola socket GAME"
                                + " (atlas §4.3: clientStartGame → socket game ke serverItem.url"
                                + " = port ini dari serverList) — listener game TRANSPORT ONLY:"
                                + " fase gameserver belum dibangun (LOGIN-SERVER-PLAN §7.5),"
                                + " client menunggu event 'verify' dari server (atlas §1) —"
                                + " loading game tidak lanjut sampai fase itu ada";
                    }
                }
                e.durMs = System.currentTimeMillis() - t0;
                LoggerCore.log(e);
                return true;
            }
            if (s == null) {
                byte[] b = HttpWorker.text("{\"code\":1,\"message\":\"Session ID unknown\"}");
                Map<String, String> h = HttpWorker.baseHeaders("application/json");
                h.put("Access-Control-Allow-Origin", "*");
                HttpWorker.writeResponse(out, 400, "Bad Request", h, b);
                e.status = 400; e.respLen = b.length; e.extra = "sid unknown";
                e.durMs = System.currentTimeMillis() - t0;
                LoggerCore.log(e);
                return true;
            }
            // poll — hold s.d. ada packet / timeout (1:1 long-poll server asli)
            String payload;
            if (s.closed && !s.closePendingNotified) {
                s.closePendingNotified = true;
                payload = frame("1");           // 1:1 capture live: close packet
            } else if (s.closed) {
                SESSIONS.remove(s.sid);
                byte[] b = HttpWorker.text("{\"code\":1,\"message\":\"Session ID unknown\"}");
                Map<String, String> h = HttpWorker.baseHeaders("application/json");
                h.put("Access-Control-Allow-Origin", "*");
                HttpWorker.writeResponse(out, 400, "Bad Request", h, b);
                e.status = 400; e.respLen = b.length; e.extra = "session already closed, removed";
                e.durMs = System.currentTimeMillis() - t0;
                LoggerCore.log(e);
                return true;
            } else {
                payload = s.drain(10000);
            }
            Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=UTF-8");
            h.put("Access-Control-Allow-Origin", "*");
            h.put("Set-Cookie", "io=" + s.sid + "; Path=/; HttpOnly");
            byte[] bb = payload.getBytes(StandardCharsets.UTF_8);
            HttpWorker.writeResponse(out, 200, "OK", h, bb);
            e.status = 200; e.respLen = bb.length;
            if (payload.isEmpty()) {
                // v23 LOG NATURAL — keterangan penuh saat client menunggu sia-sia:
                // umur sesi, apa yang sudah diterima/dikirim, upgrade selesai atau
                // tidak (bukti device v22: sesi game YfX diam 10+ dtk setelah probe
                // dan log v22 hanya menulis "long-poll timeout, no data" tanpa
                // keterangan apa pun — owner: "tanpa ada keterangan apapun").
                e.extra = "long-poll timeout 10 dtk tanpa data — sesi " + s.sid + ": "
                        + sessionFacts(s);
                if (!s.probeDone) {
                    e.extra += "; client belum memulai probe WS";
                } else if (!s.upgraded) {
                    e.extra += "; client tidak pernah mengirim '5' — upgrade WS tidak selesai";
                }
            } else {
                e.respPreview = payload;
                String d = s.lastQueuedDesc;
                e.extra = "berisi: " + (d == null ? "paket engine.io/socket.io" : d);
                if (d != null && d.contains("GetServerList")) {
                    e.extra += " — serverlist SAMPAI ke client (client lanjut: "
                            + "LoginAnnounce/SaveHistory/clientStartGame — atlas §4)";
                }
            }
            e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return true;
        }

        if ("POST".equals(req.method)) {
            Session s = sid == null ? null : SESSIONS.get(sid);
            if (s == null || s.closed) {
                byte[] b = HttpWorker.text("{\"code\":1,\"message\":\"Session ID unknown\"}");
                Map<String, String> h = HttpWorker.baseHeaders("application/json");
                h.put("Access-Control-Allow-Origin", "*");
                HttpWorker.writeResponse(out, 400, "Bad Request", h, b);
                e.status = 400; e.respLen = b.length; e.extra = "sid unknown";
                e.durMs = System.currentTimeMillis() - t0;
                LoggerCore.log(e);
                return true;
            }
            boolean dead = false;
            StringBuilder seen = new StringBuilder();
            for (String pkt : parseFramed(new String(req.body, StandardCharsets.UTF_8))) {
                s.framesIn++;   // v23: fakta — paket dari client dihitung
                if (seen.length() > 0) seen.append(" | ");
                seen.append(pkt);   // v17: PAKET PENUH — nol pangkas (1800 dihapus)
                if ("41".equals(pkt)) { s.closed = true; dead = true; }
                else if ("2probe".equals(pkt)) { s.probeDone = true; s.offer("3probe"); }
                else if ("2".equals(pkt)) s.offer("3");
                else if ("5".equals(pkt)) {
                    // v21: '5' lewat polling = client menutup transport lama
                    // (jarang) — tidak perlu balasan; upgrade resmi dicatat
                    // lewat frame WS di handleWebSocket.
                }
                else if ("40".equals(pkt)) { /* connect namespace — open sudah mengirim 40 */ }
                else if (pkt.startsWith("42")) {
                    // Bagian 67 — ACK-GATE login-server (oke owner "implementasi
                    // dengan ini"): listener login (gameMode=false) menjawab
                    // event per file <action>.json (1 event atlas = 1 file,
                    // struktur Bagian 66; file kosong {} = DIAM — atlas §11
                    // baris 9; SaveUserEnterInfo selalu dijawab tanpa file).
                    // Listener game (gameMode=true) TETAP TRANSPORT ONLY.
                    // Log = GLOBAL (NDJSON logs/ — oke owner "log global jdi 1"):
                    // ACK penuh / no-ack dicatat di extra entri ini (v17: nol
                    // pangkas).
                    if (gameMode) {
                        noteEvent(s, actionOf(pkt));   // v23: fakta event client
                        if (e.extra == null) e.extra = "EVENT (no ack): " + pkt
                                + " | gameServer transport-only (fase gameServer"
                                + " — docs/LOGIN-SERVER-PLAN.md §6)";
                    } else {
                        noteEvent(s, actionOf(pkt));   // v23: fakta event client
                        String ack = LoginAck.build(pkt, e);
                        if (ack != null) {
                            // v21: ACK emit-polling SELALU antre polling —
                            // klien mendengarkan di transport yang ia pakai
                            String via = s.deliverPolling(ack);
                            s.packetsOut++;   // v23
                            if ("antre polling".equals(via)) {
                                s.lastQueuedDesc = shortAckDesc(e.extra);   // v23
                            }
                            e.extra = (e.extra == null ? "" : e.extra + " ")
                                    + "→ dikirim: " + via;
                        }
                    }
                } else if (pkt.startsWith("2")) {
                    s.offer("3");
                }
            }
            // 1:1 capture live: POST selalu "ok" (text/html, 2 byte)
            Map<String, String> h = HttpWorker.baseHeaders("text/html; charset=utf-8");
            h.put("Access-Control-Allow-Origin", "*");
            byte[] ok = HttpWorker.text("ok");
            HttpWorker.writeResponse(out, 200, "OK", h, ok);
            e.status = 200; e.respLen = 2;
            e.respPreview = "ok";
            if (e.extra == null && seen.length() > 0) e.extra = "PACKET: " + seen;
            if (dead) e.extra = (e.extra == null ? "" : e.extra + " ") + "[client disconnect 41]";
            e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return true;
        }

        byte[] b = HttpWorker.text("method not allowed");
        HttpWorker.writeResponse(out, 405, "Method Not Allowed",
                HttpWorker.baseHeaders("text/plain; charset=utf-8"), b);
        e.status = 405; e.respLen = b.length; e.durMs = System.currentTimeMillis() - t0;
        LoggerCore.log(e);
        return true;
    }

    /* ---------- v23 LOG NATURAL — helper fakta sesi ----------
     * Perintah owner: "heee itu hlo loading tanpa ada keterangan apapun
     * di log" + "BIAR JELAS KITA ITU KURANGNYA DIMANAAA". Fakta yang
     * terlihat di server ditulis apa adanya — nol karangan. */

    /** nama action dari packet 42…["handler.process",{…,"action":"X",…}] */
    static String actionOf(String pkt) {
        int i = pkt.indexOf("\"action\":\"");
        if (i < 0) return null;
        int a = i + 10;
        int b = pkt.indexOf('"', a);
        if (b < 0 || b - a > 64 || b - a == 0) return null;
        return pkt.substring(a, b);
    }

    static void noteEvent(Session s, String act) {
        if (act == null) return;
        synchronized (s.eventsSeen) {
            if (s.eventsSeen.size() < 20 && !s.eventsSeen.contains(act)) s.eventsSeen.add(act);
        }
    }

    /** ringkasan fakta satu sesi — satu baris utk keterangan log */
    static String sessionFacts(Session s) {
        long ageS = Math.max(0, (System.currentTimeMillis() - s.createdAt) / 1000);
        StringBuilder b = new StringBuilder(160);
        b.append("umur ").append(ageS).append(" dtk, diterima ").append(s.framesIn)
         .append(" paket");
        synchronized (s.eventsSeen) {
            if (!s.eventsSeen.isEmpty()) {
                b.append(" (event: ");
                for (int i = 0; i < s.eventsSeen.size(); i++) {
                    if (i > 0) b.append(',');
                    b.append(s.eventsSeen.get(i));
                }
                b.append(')');
            } else if (s.probeDone) {
                b.append(" (hanya probe WS — nol event dari client)");
            } else {
                b.append(" (nol event dari client)");
            }
        }
        b.append(", dikirim ").append(s.packetsOut)
         .append(", upgrade WS ").append(s.upgraded ? "selesai" : "belum ('5' tak diterima)");
        return b.toString();
    }

    /** v23 — potong deskripsi ACK dari extra (bagian sebelum " | frame:") */
    static String shortAckDesc(String extra) {
        if (extra == null) return null;
        int i = extra.indexOf(" | frame:");
        String d = i >= 0 ? extra.substring(0, i) : extra;
        return d.length() > 160 ? d.substring(0, 160) : d;
    }

    /** parse payload polling "<len>:<packet>..." → daftar packet */
    static java.util.List<String> parseFramed(String body) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int i = 0;
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        while (i < raw.length) {
            int colon = i;
            while (colon < raw.length && raw[colon] != ':') colon++;
            if (colon >= raw.length) break;
            int len;
            try {
                len = Integer.parseInt(new String(raw, i, colon - i, StandardCharsets.UTF_8));
            } catch (NumberFormatException nfe) {
                break;
            }
            int start = colon + 1;
            int end = Math.min(raw.length, start + len);
            out.add(new String(raw, start, end - start, StandardCharsets.UTF_8));
            i = end;
        }
        return out;
    }

    /* ==================== v18: LAPISAN EVENT handler.process DIHAPUS ====================
     * routeEvent / GameData (serverListJson "DragonBall Local", enterGameResponse) /
     * ack / EventMsg / parseEvent DIHAPUS SELURUHNYA — pekerjaan loginServer,
     * belum diberi rencana (owner: "login server belum boleh di kerjakan!! hanya
     * menentukan endpoint dan merencanakan strategi struktur foldernya dulu !!").
     * Frame event `42…` kini hanya dicatat verbatim (nol ACK). ================= */

    /** Bagian 28 — parse query string ?a=1&b=2 (URLDecoder utf-8). */
    static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<String, String>();
        int qi = raw == null ? -1 : raw.indexOf('?');
        if (qi < 0) return out;
        String qs = raw.substring(qi + 1);
        String[] parts = qs.split("&");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            String k = eq < 0 ? p : p.substring(0, eq);
            String v = eq < 0 ? "" : p.substring(eq + 1);
            try { v = java.net.URLDecoder.decode(v, "UTF-8"); } catch (Throwable t) { /* raw */ }
            out.put(k, v);
        }
        return out;
    }

    /* ---------- WebSocket (upgrade EIO=3) ---------- */

    private boolean handleWebSocket(HttpWorker.Request req, OutputStream out, Socket sock,
                                    LoggerCore.Entry e, long t0) throws IOException {
        String key = req.header("sec-websocket-key");
        if (key == null) {
            HttpWorker.writeResponse(out, 400, "Bad Request",
                    HttpWorker.baseHeaders("text/plain"), HttpWorker.text("missing Sec-WebSocket-Key"));
            e.status = 400; e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return false;
        }
        // v17: URL PENUH koneksi WS — dipakai SEMUA entri frame di koneksi ini
        // (bukan lagi "sid=.." telanjang — owner: jangan dipangkas)
        final String wsUrl = e.path;
        Session s = null;
        String qs = req.q("sid");
        if (qs != null) s = SESSIONS.get(qs);
        if (s == null) {
            // v21: koneksi WS-FIRST (tanpa polling sebelumnya) — sesi baru,
            // memang langsung WS murni → upgraded=true benar utk kasus ini.
            s = createSession();
            s.upgraded = true;
        }
        // v21 FIX BUG SERVERLIST: sesi polling yang membuka WS untuk probe
        // TIDAK lagi ditandai upgraded di sini. upgraded=true hanya setelah
        // client mengirim '5' (completeUpgrade di loop frame di bawah).
        // v20 lama: s.upgraded = true di sini → ACK emit-polling ditulis ke
        // WS pra-probe → frame hilang → serverlist tidak muncul (bukti
        // log-20261126-181501.ndjson seq 58-61).

        String accept = wsAccept(key);
        StringBuilder h = new StringBuilder(200);
        h.append("HTTP/1.1 101 Switching Protocols\r\n")
         .append("Upgrade: websocket\r\n")
         .append("Connection: Upgrade\r\n")
         .append("Sec-WebSocket-Accept: ").append(accept).append("\r\n")
         .append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();

        // bila sesi baru (tanpa polling sebelumnya) → kirim open packet sebagai frame pertama
        if (qs == null) {
            String openPkt = "0" + openJson(s.sid);
            wsSendText(out, openPkt);
            // v17: frame open yang BENAR-BENAR dikirim juga dilog (nol disembunyikan)
            LoggerCore.Entry oe = new LoggerCore.Entry();
            oe.port = e.port; oe.kind = "WS"; oe.method = "WS";
            oe.path = wsUrl;
            oe.status = 200;
            oe.respPreview = openPkt;
            oe.respLen = openPkt.getBytes(StandardCharsets.UTF_8).length;
            oe.extra = "open (ws-first) sid=" + s.sid;
            oe.durMs = 0;
            LoggerCore.log(oe);
        }
        s.wsOut = out;   // v6: ACK event bisa dikirim langsung lewat WS

        e.status = 101;
        e.extra = "ws upgrade sid=" + s.sid;
        e.durMs = System.currentTimeMillis() - t0;
        LoggerCore.log(e);

        // ambil alih socket — loop frame WS
        sock.setSoTimeout(70000); // pingInterval 25 s; 70 s tanpa apa pun = mati
        InputStream in = sock.getInputStream();
        try {
            while (true) {
                int[] hdr = readWsHeader(in);
                if (hdr == null) break;
                int opcode = hdr[0];
                boolean masked = hdr[1] != 0;
                long len = hdr[2];
                byte[] maskKey = null;
                if (masked) {
                    maskKey = new byte[4];
                    if (!readFully(in, maskKey)) break;
                }
                byte[] payload = new byte[(int) Math.min(len, 4 * 1024 * 1024)];
                // hati-hati bila len > cap: baca-buang sisanya
                if (len > payload.length) {
                    if (!readFully(in, payload)) break;
                    long rest = len - payload.length;
                    byte[] sink = new byte[65536];
                    while (rest > 0) {
                        int n = in.read(sink, 0, (int) Math.min(sink.length, rest));
                        if (n < 0) break;
                        rest -= n;
                    }
                } else if (!readFully(in, payload)) {
                    break;
                }
                if (masked && maskKey != null) {
                    for (int i = 0; i < payload.length; i++) payload[i] ^= maskKey[i & 3];
                }
                String text = new String(payload, StandardCharsets.UTF_8);
                s.framesIn++;   // v23: fakta — setiap frame dari client dihitung

                if (opcode == 0x8) { // close
                    LoggerCore.Entry ce = new LoggerCore.Entry();
                    ce.port = e.port;
                    ce.kind = "WS";
                    ce.method = "WS";
                    ce.path = wsUrl + " close";
                    ce.status = 1000; ce.reqLen = payload.length;
                    // v23 LOG NATURAL — keterangan akhir sesi: apa yang sempat
                    // diterima/dikirim (dulu baris close tanpa keterangan)
                    ce.extra = "WS close dari client — sesi " + s.sid + ": "
                            + sessionFacts(s);
                    LoggerCore.log(ce);
                    try { sock.close(); } catch (IOException ignored) {}
                    s.closed = true;
                    SESSIONS.remove(s.sid);
                    return false;
                } else if (opcode == 0x9) { // ping control frame -> pong control frame
                    wsSendControl(out, 0xA, payload);
                    LoggerCore.Entry pe = new LoggerCore.Entry();
                    pe.port = e.port;
                    pe.kind = "WS";
                    pe.method = "WS";
                    pe.path = wsUrl + " ping->pong";
                    pe.status = 200; pe.reqLen = payload.length;
                    pe.durMs = 0;
                    LoggerCore.log(pe);
                } else if (opcode == 0xA) { // pong — abaikan
                } else if (opcode == 0x1 || opcode == 0x2) { // text/binary = packet engine.io
                    LoggerCore.Entry le = new LoggerCore.Entry();
                    le.port = e.port;
                    le.kind = "WS";
                    le.method = "WS";
                    le.path = wsUrl;
                    le.reqLen = payload.length;
                    le.reqPreview = text;
                    if ("2probe".equals(text)) {
                        s.probeDone = true;   // v23: fakta probe
                        synchronized (s.wsWriteLock) { wsSendText(out, "3probe"); }
                        le.respPreview = "3probe"; le.respLen = 6; le.status = 200;
                    }
                    else if ("2".equals(text)) {
                        synchronized (s.wsWriteLock) { wsSendText(out, "3"); }
                        le.respPreview = "3"; le.respLen = 1; le.status = 200;
                    }
                    else if ("5".equals(text) && qs != null && !s.upgraded) {
                        // v21 — packet upgrade engine.io: BARU SEKARANG client
                        // resmi pindah transport ke WS. Migrasi antre polling
                        // → WS + noop '6' ke antre polling (tutup long-poll
                        // lama). Ini satu-satunya pintu upgraded=true utk
                        // sesi polling.
                        le.status = 200;
                        le.reqPreview = text;
                        le.extra = s.completeUpgrade(out) + " sid=" + s.sid;
                    }
                    else if (text.startsWith("42")) {
                        // Bagian 67 — ACK-GATE login-server (pintu WS yang sama
                        // dgn POST-polling): file <action>.json per event;
                        // gameMode tetap TRANSPORT ONLY.
                        le.status = 200;
                        noteEvent(s, actionOf(text));   // v23: fakta event client
                        if (gameMode) {
                            le.extra = "EVENT (no ack) — gameServer transport-only"
                                    + " (fase gameServer — docs/LOGIN-SERVER-PLAN.md §6)";
                        } else {
                            String ack = LoginAck.build(text, le);
                            if (ack != null) {
                                // v21: ACK emit-WS lewat WS langsung — tapi
                                // HANYA bila upgrade benar-benar selesai; bila
                                // belum, antre polling (migrasi '5' meneruskan)
                                String via = s.deliverWs(ack);
                                s.packetsOut++;   // v23
                                if ("antre polling".equals(via)) {
                                    s.lastQueuedDesc = shortAckDesc(le.extra);   // v23
                                }
                                le.respPreview = ack;
                                le.respLen = ack.getBytes(StandardCharsets.UTF_8).length;
                                le.extra = (le.extra == null ? "" : le.extra + " ")
                                        + "→ dikirim: " + via;
                            }
                        }
                    }
                    else if ("41".equals(text)) {
                        le.status = 200; le.extra = "client disconnect (41)";
                        s.closed = true;
                        SESSIONS.remove(s.sid);
                        try { sock.close(); } catch (IOException ignored) {}
                        LoggerCore.log(le);
                        return false;
                    }
                    else { le.status = 200; }
                    le.durMs = 0;
                    LoggerCore.log(le);
                }
            }
        } catch (IOException ioe) {
            // v23 LOG NATURAL — koneksi putus TIDAK lagi ditelan senyap
            // (dulu: komentar "normal saat game mati" dan NOL baris log —
            // persis fase yang tidak diberi keterangan apa pun pada log v22).
        } finally {
            s.closed = true;
            SESSIONS.remove(s.sid);
            try { sock.close(); } catch (IOException ignored) {}
        }
        // v23: baris akhir sesi WS (EOF / koneksi putus tanpa close frame).
        // Jalur close-frame (opcode 0x8) sudah mencatat sendiri dan langsung
        // return false — sampai di sini hanya bila loop berakhir tanpa itu.
        LoggerCore.Entry de = new LoggerCore.Entry();
        de.port = e.port;
        de.kind = "WS";
        de.method = "WS";
        de.path = wsUrl + " berakhir";
        de.status = 200; de.reqLen = 0;
        de.extra = "WS berakhir (EOF/koneksi putus tanpa close frame) — sesi "
                + s.sid + ": " + sessionFacts(s)
                + " — normal saat aplikasi ditutup / koneksi hilang";
        LoggerCore.log(de);
        return false;
    }

    static String wsAccept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((key + WS_GUID).getBytes(StandardCharsets.ISO_8859_1));
            return b64(hash);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Base64 standar (RFC 4648) — java.util.Base64 baru ada di Android API 26,
     *  minSdk=21 → implementasi sendiri */
    static String b64(byte[] data) {
        char[] T = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
        for (int i = 0; i < data.length; i += 3) {
            int b0 = data[i] & 0xff;
            int b1 = (i + 1 < data.length) ? data[i + 1] & 0xff : 0;
            int b2 = (i + 2 < data.length) ? data[i + 2] & 0xff : 0;
            int n = (b0 << 16) | (b1 << 8) | b2;
            out.append(T[(n >> 18) & 63]).append(T[(n >> 12) & 63]);
            out.append((i + 1 < data.length) ? T[(n >> 6) & 63] : '=');
            out.append((i + 2 < data.length) ? T[n & 63] : '=');
        }
        return out.toString();
    }

    /** kembalikan {opcode, masked, len} atau null bila EOF */
    static int[] readWsHeader(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 == -1) return null;
        int b1 = in.read();
        if (b1 == -1) return null;
        int opcode = b0 & 0x0f;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7f;
        if (len == 126) {
            int hi = in.read(), lo = in.read();
            if (lo == -1) return null;
            len = ((hi & 0xff) << 8) | (lo & 0xff);
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) {
                int c = in.read();
                if (c == -1) return null;
                len = (len << 8) | (c & 0xff);
            }
        }
        return new int[]{opcode, masked ? 1 : 0, (int) len};
    }

    static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) return false;
            off += r;
        }
        return true;
    }

    static void wsSendText(OutputStream out, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        wsSendFrame(out, (byte) 0x81, data);
    }

    static void wsSendControl(OutputStream out, int opcode, byte[] data) throws IOException {
        wsSendFrame(out, (byte) (0x80 | opcode), data);
    }

    static void wsSendFrame(OutputStream out, byte first, byte[] data) throws IOException {
        java.io.ByteArrayOutputStream h = new java.io.ByteArrayOutputStream();
        h.write(first & 0xff);
        int len = data.length;
        if (len < 126) {
            h.write(len);
        } else if (len < 65536) {
            h.write(126);
            h.write((len >> 8) & 0xff);
            h.write(len & 0xff);
        } else {
            h.write(127);
            for (int i = 7; i >= 0; i--) h.write((int) ((len >> (8 * i)) & 0xff));
        }
        out.write(h.toByteArray());
        out.write(data);
        out.flush();
    }
}
