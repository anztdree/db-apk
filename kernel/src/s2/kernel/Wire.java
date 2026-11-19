// ============================================================
// WIRE — mesin socket SISTEM 2 (kernel embedded).
// engine.io v3 (EIO=3) polling WAJIB + upgrade WebSocket RFC6455 WAJIB.
// socket.io v2 di atasnya: 40/42/43, ping 5 / pong 6, probe 2probe/3probe.
// Verify: server emit "verify" (challenge) -> client jawab XXTEA
// kunci "verification" -> cek -> emit {ret:0}.
// Dispatcher: "handler.process" -> ACK {ret, data, compress, serverTime,
// server0Time}. Tidak match -> HOLD (tanpa ACK, NOL popup — Hukum 2).
// ============================================================
package s2.kernel;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Wire {
    public static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Map<String, Session> SESSIONS = new HashMap<String, Session>();
    private static final Random RND = new Random();
    private static volatile int held = 0;
    private static volatile int heldTotal = 0;
    public static final long STARTED_MS = System.currentTimeMillis();
    public static final String STARTED_AT = Logr.isoNow();

    private Wire() {}

    // ---------- engine.json ----------
    public static class EngineCfg {
        public int port = 4000;
        public int pingInterval = 25000;
        public int pingTimeout = 60000;
        public int holdTimeoutMs = 8000;
        public int logBuffer = 3000;
    }

    public static EngineCfg engineCfg() {
        EngineCfg c = new EngineCfg();
        try {
            File f = K.safeFile("engine.json");
            if (f != null && f.isFile()) {
                Json.Obj o = Json.parseObj(Util.readTextFile(f));
                c.port = o.optInt("port", c.port);
                c.pingInterval = o.optInt("pingInterval", c.pingInterval);
                c.pingTimeout = o.optInt("pingTimeout", c.pingTimeout);
                c.holdTimeoutMs = o.optInt("holdTimeoutMs", c.holdTimeoutMs);
                c.logBuffer = o.optInt("logBuffer", c.logBuffer);
            }
        } catch (Throwable ignore) {
        }
        return c;
    }

    // ---------- sesi ----------
    public static class Session {
        public final String sid;
        public final List<String> outbox = new ArrayList<String>();
        public volatile WsConn ws;
        public volatile WsConn probe;
        public volatile boolean verified;
        public volatile boolean nspSent;
        public volatile boolean nspConnected;
        public volatile String challenge = "";
        public volatile long lastSeen;
        public final long connectedAt;

        Session(String sid) {
            this.sid = sid;
            this.lastSeen = System.currentTimeMillis();
            this.connectedAt = this.lastSeen;
        }
    }

    private static String newSid() {
        return Long.toString(System.currentTimeMillis(), 36) + "-"
                + Long.toString(Math.abs(RND.nextLong() & 0xffffffffL), 36)
                + Long.toString(Math.abs(RND.nextLong() & 0xffffL), 36);
    }

    private static Session createSession(String sid) {
        Session s = new Session(sid);
        synchronized (SESSIONS) {
            SESSIONS.put(sid, s);
        }
        return s;
    }

    public static Session getSession(String sid) {
        synchronized (SESSIONS) {
            return SESSIONS.get(sid);
        }
    }

    private static String handshakeJson(String sid) {
        EngineCfg c = engineCfg();
        Json.Obj o = new Json.Obj();
        o.put("sid", sid);
        o.put("upgrades", new Json.Arr().put("websocket"));
        o.put("pingInterval", c.pingInterval);
        o.put("pingTimeout", c.pingTimeout);
        return o.toString();
    }

    // ---------- payload engine.io v3: <len>:<packet>... ----------
    private static String encodePayloadV3(List<String> packets) {
        StringBuilder sb = new StringBuilder();
        for (String p : packets) sb.append(p.length()).append(':').append(p);
        return sb.toString();
    }

    private static List<String> decodePayloadV3(String body) {
        List<String> out = new ArrayList<String>();
        int i = 0;
        while (i < body.length()) {
            StringBuilder lenStr = new StringBuilder();
            while (i < body.length() && body.charAt(i) != ':') {
                lenStr.append(body.charAt(i));
                i++;
            }
            if (i >= body.length() || body.charAt(i) != ':') break;
            i++;
            int len;
            try {
                len = Integer.parseInt(lenStr.toString());
            } catch (Throwable t) {
                break;
            }
            if (i + len > body.length()) break;
            String msg = body.substring(i, i + len);
            if (msg.length() > 0) out.add(msg);
            i += len;
        }
        return out;
    }

    // ---------- outbox ----------
    private static void enqueue(Session s, String packet) {
        s.lastSeen = System.currentTimeMillis();
        WsConn ws = s.ws;
        if (ws != null) {
            try {
                ws.sendText(packet);
                return;
            } catch (Throwable t) {
                s.ws = null;
            }
        }
        synchronized (s.outbox) {
            s.outbox.add(packet);
        }
    }

    private static List<String> flushOutbox(Session s) {
        synchronized (s.outbox) {
            if (s.outbox.isEmpty()) return new ArrayList<String>();
            List<String> out = new ArrayList<String>(s.outbox);
            s.outbox.clear();
            return out;
        }
    }

    // ---------- socket.io v2 layer ----------
    private static void sioEmit(Session s, String ev, Object arg) {
        Json.Arr arr = new Json.Arr();
        arr.put(ev);
        arr.put(arg == null ? Json.NULL : arg);
        enqueue(s, "42" + arr.toString());
    }

    private static void sioAck(Session s, String ackId, Object arg) {
        Json.Arr arr = new Json.Arr();
        arr.put(arg == null ? Json.NULL : arg);
        enqueue(s, "43" + ackId + arr.toString());
    }

    private static void sendNamespaceConnect(Session s) {
        if (s.nspSent) return;
        s.nspSent = true;
        s.nspConnected = true;
        // socket.io v2: utk namespace default, SERVER kirim "40" duluan,
        // lalu challenge verify
        enqueue(s, "40");
        s.challenge = Long.toString(Math.abs(RND.nextLong()), 36) + Long.toString(Math.abs(RND.nextLong()), 36);
        sioEmit(s, "verify", s.challenge);
        Logr.write("wire", "socket", "namespace CONNECT \"/\" dikirim server sid=" + s.sid + " — challenge verify siap");
    }

    private static void socketIoLayer(Session s, String rest) {
        char code = rest.length() > 0 ? rest.charAt(0) : '?';
        String tail = rest.length() > 1 ? rest.substring(1) : "";

        if (code == '0') {
            String ns = "";
            if (tail.startsWith("/")) {
                int comma = tail.indexOf(',');
                ns = comma >= 0 ? tail.substring(0, comma) : tail;
                enqueue(s, "40" + ns + ",");
            } else if (!s.nspSent) {
                sendNamespaceConnect(s);
            }
            Logr.write("wire", "socket", "namespace CONNECT client \"" + (ns.length() > 0 ? ns : "/") + "\" sid=" + s.sid);
            return;
        }
        if (code == '1') {
            s.nspConnected = false;
            Logr.write("wire", "socket", "namespace DISCONNECT sid=" + s.sid);
            return;
        }
        if (code == '2' || code == '5' || code == '6') {
            int idx = 0;
            while (idx < tail.length() && tail.charAt(idx) >= '0' && tail.charAt(idx) <= '9') idx++;
            String ackId = idx > 0 ? tail.substring(0, idx) : "";
            Json.Arr parsed;
            try {
                parsed = Json.parseArr(tail.substring(idx));
            } catch (Throwable t) {
                Logr.write("warn", "socket", "event JSON rusak sid=" + s.sid);
                return;
            }
            String ev = parsed.length() > 0 ? String.valueOf(parsed.opt(0)) : "";
            Json.Arr args = new Json.Arr();
            for (int i = 1; i < parsed.length(); i++) args.put(parsed.opt(i));
            onSocketEvent(s, ev, args, ackId);
            return;
        }
        Logr.write("warn", "socket", "packet socket.io tidak dikenal \"" + code + "\" sid=" + s.sid);
    }

    private static void onSocketEvent(final Session s, final String ev, final Json.Arr args, final String ackId) {
        // --- verify handshake (TEA kunci "verification") ---
        // KONTRAK CLIENT (main.min.js TSSocketClient.socketOnVerify):
        //   server EMIT "verify" challenge -> client jawab emit("verify", TEA(challenge), ACK-callback)
        //   -> server WAJIB menjawab lewat ACK (43+ackId) {ret:0} — BUKAN emit baru.
        // Kalau server membalas dengan 42["verify",...] lagi, client menganggapnya
        // challenge BARU -> loop verify tanpa henti -> game tidak pernah lanjut.
        if ("verify".equals(ev)) {
            Object answer = args.length() > 0 ? args.opt(0) : null;
            boolean hasAnswer = answer != null && !(answer == Json.NULL);
            if (hasAnswer) {
                String decrypted = Codec.teaDecrypt(String.valueOf(answer), Codec.TEA_KEY);
                boolean ok = decrypted.length() > 0
                        && (decrypted.equals(s.challenge) || decrypted.length() == s.challenge.length());
                Json.Obj ret = new Json.Obj();
                ret.put("ret", ok ? 0 : 1);
                if (ackId.length() > 0) {
                    sioAck(s, ackId, ret);
                } else {
                    sioEmit(s, "verify", ret);
                }
                if (ok) {
                    s.verified = true;
                    Logr.write("wire", "socket", "verify OK sid=" + s.sid + " (TEA \"verification\" sah)");
                } else {
                    Logr.write("warn", "socket", "verify GAGAL sid=" + s.sid + " (jawaban TEA tidak sah)");
                }
            } else {
                Json.Obj reto = new Json.Obj();
                reto.put("ret", s.verified ? 0 : 1);
                if (ackId.length() > 0) {
                    sioAck(s, ackId, reto);
                } else {
                    sioEmit(s, "verify", reto);
                }
            }
            return;
        }

        // --- dispatcher utama ---
        if ("handler.process".equals(ev)) {
            Object payloadObj = args.length() > 0 ? args.opt(0) : null;
            Json.Obj payload = payloadObj instanceof Json.Obj ? (Json.Obj) payloadObj : new Json.Obj();
            String type = payload.optString("type", "");
            String action = payload.optString("action", "");
            if (type.length() == 0 || action.length() == 0) {
                Logr.write("warn", "socket", "handler.process tanpa type/action sid=" + s.sid);
                if (ackId.length() > 0) {
                    Json.Obj errAck = new Json.Obj();
                    errAck.put("ret", 400);
                    errAck.put("data", "type/action wajib");
                    errAck.put("compress", false);
                    errAck.put("serverTime", System.currentTimeMillis());
                    errAck.put("server0Time", System.currentTimeMillis());
                    sioAck(s, ackId, errAck);
                }
                return;
            }
            Hot.maybeRescan();
            Hot.Match match = Hot.matchSock(type, action);
            if (match == null) {
                // HUKUM 2: hold-connection — tanpa ACK, NOL popup
                held++;
                heldTotal++;
                Logr.write("warn", "socket", "HOLD " + type + "/" + action + " (tidak ada handler) sid=" + s.sid + " — koneksi ditahan");
                return;
            }
            String ack = Hot.executeSock(match, type, action, payload.toString(), s.sid);
            if (ack == null) {
                held++;
                heldTotal++;
                Logr.write("warn", "socket", "HOLD " + type + "/" + action + " (handler tidak membalas) sid=" + s.sid);
                return;
            }
            if (ackId.length() > 0) {
                enqueue(s, "43" + ackId + "[" + ack + "]");
            } else {
                Json.Arr hr = new Json.Arr();
                hr.put("handlerResult");
                try {
                    hr.put(Json.parse(ack));
                } catch (Throwable t) {
                    hr.put(ack);
                }
                enqueue(s, "42" + hr.toString());
            }
            try {
                Json.Obj ackObj = Json.parseObj(ack);
                Logr.write("wire", "socket", "ACK " + type + "/" + action + " ret=" + ackObj.optInt("ret", 0)
                        + " dataLen=" + ackObj.optString("data", "").length()
                        + " compress=" + ackObj.optBoolean("compress", false)
                        + " (tier-" + match.tier + ")");
            } catch (Throwable ignore) {
            }
            return;
        }

        Logr.write("warn", "socket", "event tidak dikenal \"" + ev + "\" sid=" + s.sid);
    }

    // ---------- engine.io layer: polling ----------
    public static String handlePollingGet(Map<String, String> query) {
        Hot.maybeRescan();
        EngineCfg cfg = engineCfg();
        String sid = query.get("sid");
        if (sid == null || sid.length() == 0) {
            Session s = createSession(newSid());
            sendNamespaceConnect(s);
            Logr.write("info", "socket", "handshake polling sid=" + s.sid + " (upgrades: websocket)");
            List<String> pk = new ArrayList<String>();
            pk.add("0" + handshakeJson(s.sid));
            return encodePayloadV3(pk);
        }
        Session s = getSession(sid);
        if (s == null) {
            return "1" + new Json.Obj().toString();
        }
        s.lastSeen = System.currentTimeMillis();
        long maxMs = Math.min(cfg.holdTimeoutMs, cfg.pingInterval - 2000);
        long deadline = System.currentTimeMillis() + Math.max(maxMs, 1500);
        while (System.currentTimeMillis() < deadline) {
            synchronized (s.outbox) {
                if (!s.outbox.isEmpty()) break;
            }
            if (s.ws != null) break;
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                break;
            }
        }
        List<String> packets = flushOutbox(s);
        if (packets.isEmpty()) {
            List<String> noop = new ArrayList<String>();
            noop.add("6");
            return encodePayloadV3(noop);
        }
        return encodePayloadV3(packets);
    }

    public static String handlePollingPost(Map<String, String> query, String bodyText) {
        String sid = query.get("sid");
        Session s = sid != null ? getSession(sid) : null;
        if (s == null) return null; // caller kirim 400
        s.lastSeen = System.currentTimeMillis();
        List<String> packets = decodePayloadV3(bodyText);
        for (String p : packets) {
            if (p.length() == 0) continue;
            processEnginePacket(s, p);
        }
        return "ok";
    }

    private static void processEnginePacket(Session s, String packet) {
        char code = packet.charAt(0);
        String rest = packet.length() > 1 ? packet.substring(1) : "";
        switch (code) {
            case '2':
                enqueue(s, "3"); // pong
                break;
            case '4':
                socketIoLayer(s, rest);
                break;
            case '5':
                enqueue(s, "6");
                break;
            default:
                Logr.write("warn", "socket", "engine.io packet \"" + code + "\" tidak dikenal sid=" + s.sid);
        }
    }

    // ---------- websocket ----------
    /** Upgrade WS: kembalikan true bila socket diambil-alih (101 + loop frame). */
    public static boolean upgrade(Socket socket, Map<String, String> headers, Map<String, String> query) {
        try {
            Hot.maybeRescan();
            String requestedSid = query.get("sid");
            String sid;
            boolean fresh = false;
            Session s = requestedSid != null ? getSession(requestedSid) : null;
            if (s == null) {
                sid = newSid();
                fresh = true;
                s = createSession(sid);
                Logr.write("info", "socket", "handshake websocket sid=" + sid + " (murni WS)");
            } else {
                sid = s.sid;
            }
            String key = headers.get("sec-websocket-key");
            if (key == null || key.length() == 0) return false;
            String accept = wsAccept(key);
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(UTF8));
            out.flush();
            WsConn conn = new WsConn(socket, s);
            if (fresh) {
                s.ws = conn;
                conn.sendText("0" + handshakeJson(sid));
                sendNamespaceConnect(s);
                Logr.write("wire", "socket", "open packet + namespace CONNECT via WS aktif sid=" + sid);
            } else {
                s.probe = conn;
            }
            conn.readLoop();
            return true;
        } catch (Throwable t) {
            Logr.write("error", "socket", "upgrade WS gagal: " + t);
            return false;
        }
    }

    public static String wsAccept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((key + WS_GUID).getBytes("UTF-8"));
            return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
        } catch (Throwable t) {
            return "";
        }
    }

    public static class WsConn {
        public final Socket socket;
        public final Session session;
        private final InputStream in;
        private final OutputStream out;
        private final Object WRITE_LOCK = new Object();
        private volatile boolean closed = false;

        WsConn(Socket socket, Session session) throws IOException {
            this.socket = socket;
            this.session = session;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        private int readByte() throws IOException {
            int b = in.read();
            if (b < 0) throw new IOException("EOF");
            return b;
        }

        private int readExact(byte[] buf) throws IOException {
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) throw new IOException("EOF");
                off += n;
            }
            return off;
        }

        public void readLoop() {
            try {
                while (!closed) {
                    int b0 = readByte();
                    int b1 = readByte();
                    boolean fin = (b0 & 0x80) != 0;
                    int opcode = b0 & 0x0f;
                    boolean masked = (b1 & 0x80) != 0;
                    long len = b1 & 0x7f;
                    if (len == 126) {
                        byte[] ext = new byte[2];
                        readExact(ext);
                        len = ((ext[0] & 0xff) << 8) | (ext[1] & 0xff);
                    } else if (len == 127) {
                        byte[] ext = new byte[8];
                        readExact(ext);
                        len = 0;
                        for (byte b : ext) len = (len << 8) | (b & 0xff);
                    }
                    if (len > 16 * 1024 * 1024) {
                        close();
                        return;
                    }
                    byte[] mask = new byte[4];
                    if (masked) readExact(mask);
                    byte[] payload = new byte[(int) len];
                    readExact(payload);
                    if (masked) {
                        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                    }
                    switch (opcode) {
                        case 0x1: // text
                            onText(new String(payload, UTF8));
                            break;
                        case 0x2: // binary
                            Logr.write("warn", "socket", "frame binari diabaikan sid=" + session.sid);
                            break;
                        case 0x8: // close
                            sendFrame(0x8, new byte[0]);
                            close();
                            return;
                        case 0x9: // ping
                            sendFrame(0xA, payload);
                            break;
                        case 0xA: // pong
                            break;
                        default:
                            break;
                    }
                    if (!fin) {
                        // fragmented frame tidak dipakai handler teks — abaikan sisa
                    }
                }
            } catch (Throwable t) {
                // koneksi putus
            }
            close();
        }

        private void onText(String message) {
            Session s = session;
            s.lastSeen = System.currentTimeMillis();
            if ("2probe".equals(message)) {
                try {
                    sendText("3probe");
                } catch (Throwable ignore) {
                }
                return;
            }
            if ("5".equals(message)) {
                // upgrade SAH: WS menjadi transport aktif + flush outbox
                s.ws = this;
                s.probe = null;
                try {
                    sendText("6");
                } catch (Throwable ignore) {
                }
                List<String> pending = flushOutbox(s);
                for (String p : pending) {
                    try {
                        sendText(p);
                    } catch (Throwable t) {
                        synchronized (s.outbox) {
                            s.outbox.add(p);
                        }
                        break;
                    }
                }
                Logr.write("wire", "socket", "upgrade websocket SAH sid=" + s.sid + " (" + pending.size() + " packet tertahan diflush)");
                return;
            }
            if (message.length() == 0) return;
            processEnginePacket(s, message);
        }

        public void sendText(String text) throws IOException {
            sendFrame(0x1, text.getBytes(UTF8));
        }

        private void sendFrame(int opcode, byte[] payload) throws IOException {
            synchronized (WRITE_LOCK) {
                if (closed) throw new IOException("ws closed");
                ByteArrayOutputStream head = new ByteArrayOutputStream();
                head.write(0x80 | opcode);
                int len = payload.length;
                if (len <= 125) {
                    head.write(len);
                } else if (len <= 0xffff) {
                    head.write(126);
                    head.write((len >> 8) & 0xff);
                    head.write(len & 0xff);
                } else {
                    head.write(127);
                    for (int i = 7; i >= 0; i--) head.write((len >> (8 * i)) & 0xff);
                }
                out.write(head.toByteArray());
                out.write(payload);
                out.flush();
            }
        }

        public void close() {
            if (closed) return;
            closed = true;
            Session s = session;
            if (s.probe == this) s.probe = null;
            if (s.ws == this) {
                s.ws = null;
                Logr.write("info", "socket", "websocket aktif tertutup sid=" + s.sid);
            }
            try {
                socket.close();
            } catch (Throwable ignore) {
            }
        }
    }

    // ---------- janitor sesi mati ----------
    public static void startSessionJanitor() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        EngineCfg cfg = engineCfg();
                        long ttl = cfg.pingInterval + cfg.pingTimeout * 2L;
                        long now = System.currentTimeMillis();
                        List<String> dead = new ArrayList<String>();
                        synchronized (SESSIONS) {
                            Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet().iterator();
                            while (it.hasNext()) {
                                Map.Entry<String, Session> e = it.next();
                                if (now - e.getValue().lastSeen > ttl) {
                                    dead.add(e.getKey());
                                    it.remove();
                                }
                            }
                        }
                        for (String sid : dead) {
                            Logr.write("info", "socket", "sesi kadaluarsa dibuang sid=" + sid);
                        }
                    } catch (Throwable ignore) {
                        // anti-FC
                    }
                }
            }
        }, "s2-kernel-janitor");
        t.setDaemon(true);
        t.start();
    }

    // ---------- statistik ----------
    public static Json.Obj sessionStats() {
        int websocket = 0;
        int verified = 0;
        int size;
        synchronized (SESSIONS) {
            size = SESSIONS.size();
            for (Session s : SESSIONS.values()) {
                if (s.ws != null) websocket++;
                if (s.verified) verified++;
            }
        }
        Json.Obj o = new Json.Obj();
        o.put("engineio", size);
        o.put("websocket", websocket);
        o.put("verified", verified);
        o.put("held", held);
        o.put("heldTotal", heldTotal);
        return o;
    }
}
