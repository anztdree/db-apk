package com.db.local.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Listener 5050 — login socket.io v2.1.2 (EIO=3) mirror login.popoh5.com:610,
 * CONFIG UTAMA GAMEPLAY (peta 16.2). Perilaku 1:1 hasil capture live server asli
 * (22 Nov 2026, backup logs):
 *   - poll pertama (tanpa sid)  : 200 text/plain; charset=UTF-8, Access-Control-Allow-Origin: *,
 *                                 Set-Cookie io=<sid>; Path=/; HttpOnly,
 *                                 body = 96:0{"sid":"…20char","upgrades":["websocket"],
 *                                        "pingInterval":25000,"pingTimeout":5000}2:40  (103 byte)
 *   - POST (sid valid)          : 200 text/html "ok"
 *   - GET poll (sid valid)      : long-poll di-hold s.d. ada packet / timeout, body kosong bila sunyi
 *   - sid tidak dikenal         : 400 application/json {"code":1,"message":"Session ID unknown"}
 *   - sesi ditutup              : poll berikut → 1:1 (close), lalu sesi dihapus
 *   - 2probe → 3probe; 2 → 3; 42[event] → HANYA di-log (tidak ada data game dikarang);
 *     41 → tutup sesi.
 *   - transport=websocket: upgrade 101 (Sec-WebSocket-Accept), frame text EIO=3,
 *     ping WS (0x9) → pong (0xA), close (0x8) → tutup.
 */
public final class SocketIoHandler implements HttpWorker.Handler {
    public static final int PING_INTERVAL = 25000;
    public static final int PING_TIMEOUT = 5000;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public static final class Session {
        public final String sid;
        public final long createdAt = System.currentTimeMillis();
        public final ArrayDeque<String> queue = new ArrayDeque<>();
        public final ReentrantLock lock = new ReentrantLock();
        public volatile boolean closed = false;
        public volatile boolean closePendingNotified = false;
        public volatile boolean upgraded = false;

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

    @Override
    public boolean handle(HttpWorker.Request req, OutputStream out, Socket sock) throws IOException {
        long t0 = System.currentTimeMillis();
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGIN;
        e.method = req.method;
        e.path = req.rawPath;
        e.reqLen = req.body.length;
        e.headers = req.headersStr;
        if (req.body.length > 0) e.reqPreview = StaticHandler.preview(req.body);

        boolean isWs = "websocket".equalsIgnoreCase(req.header("upgrade"));

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
                s = createSession();
                String body = frame("0" + openJson(s.sid)) + frame("40");
                Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=UTF-8");
                h.put("Access-Control-Allow-Origin", "*");
                h.put("Set-Cookie", "io=" + s.sid + "; Path=/; HttpOnly");
                byte[] bb = body.getBytes(StandardCharsets.UTF_8);
                HttpWorker.writeResponse(out, 200, "OK", h, bb);
                e.status = 200; e.respLen = bb.length;
                e.respPreview = body;   // v3: body OPEN tercatat penuh (103 byte, 1:1 capture live)
                e.extra = "OPEN sid=" + s.sid + " (open+40, 1:1 capture live)";
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
                e.status = 400; e.respLen = b.length; e.extra = "sesi sudah ditutup → dihapus";
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
            if (payload.isEmpty()) e.extra = "poll kosong (long-poll hold)";
            else e.respPreview = payload;
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
                if (seen.length() > 0) seen.append(" | ");
                if (seen.length() < 1800) seen.append(pkt);
                if ("41".equals(pkt)) { s.closed = true; dead = true; }
                else if ("2probe".equals(pkt)) s.offer("3probe");
                else if ("2".equals(pkt)) s.offer("3");
                else if ("5".equals(pkt)) { /* upgrade ack — tidak perlu balasan */ }
                else if ("40".equals(pkt)) { /* connect namespace — open sudah mengirim 40 */ }
                else if (pkt.startsWith("42")) {
                    e.extra = "EVENT: " + pkt;   // di-log apa adanya; TIDAK ada data dikarang
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
            if (dead) e.extra = (e.extra == null ? "" : e.extra + " ") + "[sesi ditutup oleh client 41]";
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
        Session s = null;
        String qs = req.q("sid");
        if (qs != null) s = SESSIONS.get(qs);
        if (s == null) {
            s = createSession();
            s.upgraded = true;
        } else {
            s.upgraded = true;
        }

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
            wsSendText(out, "0" + openJson(s.sid));
        }

        e.status = 101;
        e.extra = "WS upgrade sid=" + s.sid;
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

                if (opcode == 0x8) { // close
                    LoggerCore.Entry ce = new LoggerCore.Entry();
                    ce.port = LoggerCore.PORT_LOGIN;
                    ce.kind = "WS";
                    ce.method = "WS";
                    ce.path = "/socket.io ws close sid=" + s.sid;
                    ce.status = 1000; ce.reqLen = payload.length;
                    LoggerCore.log(ce);
                    try { sock.close(); } catch (IOException ignored) {}
                    s.closed = true;
                    SESSIONS.remove(s.sid);
                    return false;
                } else if (opcode == 0x9) { // ping → pong (terlog — v3 kelengkapan)
                    wsSendControl(out, 0xA, payload);
                    LoggerCore.Entry pe = new LoggerCore.Entry();
                    pe.port = LoggerCore.PORT_LOGIN;
                    pe.kind = "WS";
                    pe.method = "WS";
                    pe.path = "/socket.io ws sid=" + s.sid + " ping→pong";
                    pe.status = 200; pe.reqLen = payload.length;
                    pe.respPreview = "pong";
                    pe.durMs = 0;
                    LoggerCore.log(pe);
                } else if (opcode == 0xA) { // pong — abaikan
                } else if (opcode == 0x1 || opcode == 0x2) { // text/binary = packet engine.io
                    LoggerCore.Entry le = new LoggerCore.Entry();
                    le.port = LoggerCore.PORT_LOGIN;
                    le.kind = "WS";
                    le.method = "WS";
                    le.path = "/socket.io ws sid=" + s.sid;
                    le.reqLen = payload.length;
                    le.reqPreview = text;
                    if ("2probe".equals(text)) { wsSendText(out, "3probe"); le.respPreview = "3probe"; le.status = 200; }
                    else if ("2".equals(text)) { wsSendText(out, "3"); le.respPreview = "3"; le.status = 200; }
                    else if (text.startsWith("42")) { le.status = 200; le.extra = "EVENT: " + text; }
                    else if ("41".equals(text)) {
                        le.status = 200; le.extra = "client disconnect (41)";
                        s.closed = true;
                        SESSIONS.remove(s.sid);
                        try { sock.close(); } catch (IOException ignored) {}
                        LoggerCore.log(le);
                        return false;
                    } else { le.status = 200; le.extra = "PACKET: " + text; }
                    le.durMs = 0;
                    LoggerCore.log(le);
                }
            }
        } catch (IOException ioe) {
            // koneksi putus — normal saat game mati
        } finally {
            s.closed = true;
            SESSIONS.remove(s.sid);
            try { sock.close(); } catch (IOException ignored) {}
        }
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
