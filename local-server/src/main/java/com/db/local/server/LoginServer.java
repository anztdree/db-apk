package com.db.local.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;

/**
 * SERVER LOGIN LOKAL — port 8610 (config loginPort). Menerapkan protokol yang
 * SAMA dengan server asli login.popoh5.com:610, hasil rekayasa balik + probe
 * langsung 2026-11-02 (README §2.10):
 *
 *   engine.io v3 (EIO=3) transport polling + socket.io v2.1 (client game memakai
 *   socket.io.min v2.1.0 di bundle H5). Framing polling: "<panjang>:<paket>"
 *   berurutan. Event: "handler.process" (pesan, callback-ack) — jawaban lewat
 *   ACK socket.io "43<id>[{ret,data}]".
 *
 *   TIDAK ada websocket: handshake mengumumkan "upgrades":[] — client v2.1
 *   tetap di polling (di server asli, POST polling malah 502 dari nginx; di
 *   lokal POST polling sehat karena server kita sendiri).
 *
 *   Action yang dilayani (semua yang dipakai H5 — hasil bedah main.min):
 *     loginGame          → masuk/daftar otomatis (akun lokal, LoginData)
 *     GetServerList      → daftar server (config/servers.json — snapshot asli)
 *     LoginAnnounce      → pengumuman (kosong)
 *     SaveHistory / SaveLanguage / SaveUserEnterInfo → diterima, dijawab ret 0
 *
 * Catatan desain:
 * - Sesi engine.io = sid 16 karakter; kedaluwarsa 90 dtk tanpa aktivitas
 *   (client ping tiap 25 dtk — jawab PONG "3").
 * - Long-poll GET menunggu antrean sampai 20 dtk; jawaban boleh kosong
 *   (client mengulang sendiri — perilaku engine.io standar).
 * - Semua jawaban dibungkus {ret:0, data:"<json-string>"} — H5 mem-parsing
 *   data sebagai string JSON (processHandlerWithLogin di main.min).
 * - Log: hanya kejadian nyata (login/daftar server) — ping & koneksi senyap.
 */
public final class LoginServer extends NanoHTTPD {

    private static final long SESSION_TTL_MS = 90000L;
    private static final long POLL_WAIT_MS = 20000L;
    private static final int MAX_SESSIONS = 64;
    private static final String HANDSHAKE_JSON =
            "{\"sid\":\"%SID%\",\"upgrades\":[],\"pingInterval\":25000,\"pingTimeout\":5000}";

    private final ConcurrentHashMap<String, Sess> sessions = new ConcurrentHashMap<String, Sess>();
    private final Object SWEEPLOCK = new Object();

    /** Sesi engine.io satu client — antrean paket keluar + waktu terakhir hidup. */
    private static final class Sess {
        final String sid;
        final LinkedBlockingQueue<String> out = new LinkedBlockingQueue<String>();
        volatile long lastSeen = System.currentTimeMillis();
        volatile boolean closed = false;

        Sess(String sid) {
            this.sid = sid;
        }
    }

    public LoginServer(String bind, int port) throws java.io.IOException {
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
        if (!"/socket.io/".equals(uri) && !"/socket.io".equals(uri)) {
            return text(Response.Status.NOT_FOUND,
                    "Server login lokal hanya melayani /socket.io/ (protokol game) — bukan halaman web. "
                            + "Dasbor developer ada di port " + Config.debugPort() + ".");
        }
        try {
            if (Method.GET == m) {
                String sid = parm(parms, "sid");
                if (sid == null || sid.length() == 0) {
                    return handshake();
                }
                return poll(sid);
            }
            if (Method.POST == m) {
                return receive(session, parm(parms, "sid"));
            }
            return text(Response.Status.METHOD_NOT_ALLOWED, "Metode tidak didukung — hanya GET/POST polling");
        } catch (Throwable t) {
            try {
                HumanLog.ev('⚠', "Server login lokal gagal memproses permintaan — dijawab 500", String.valueOf(t));
            } catch (Throwable ignored) {
            }
            return text(Response.Status.INTERNAL_ERROR, "kesalahan server login lokal: " + t);
        }
    }

    // ---------------- engine.io v3 polling ----------------

    /** Handshake: buat sesi + kirim OPEN engine.io dan OPEN socket.io (framing v3). */
    private Response handshake() {
        sweepExpired();
        String sid = newSid();
        Sess s = new Sess(sid);
        if (sessions.size() >= MAX_SESSIONS) {
            sessions.clear(); // pengaman — client akan menyambung ulang sendiri
        }
        sessions.put(sid, s);
        String open = HANDSHAKE_JSON.replace("%SID%", sid);
        String body = frame("0" + open) + frame("40");
        try {
            HumanLog.ev('ℹ', "Login lokal: client tersambung (sesi " + sid.substring(0, 6) + "…)", null);
        } catch (Throwable ignored) {
        }
        return text(Response.Status.OK, body);
    }

    /** Long-poll: tunggu paket keluar sampai 20 dtk; boleh jawab kosong. */
    private Response poll(String sid) {
        Sess s = sessions.get(sid);
        if (s == null || s.closed) {
            return unknownSession();
        }
        s.lastSeen = System.currentTimeMillis();
        StringBuilder body = new StringBuilder(256);
        try {
            String first = s.out.poll(POLL_WAIT_MS, TimeUnit.MILLISECONDS);
            if (first != null) {
                body.append(frame(first));
                String next;
                while ((next = s.out.poll(20, TimeUnit.MILLISECONDS)) != null) {
                    body.append(frame(next));
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        Response r = text(Response.Status.OK, body.toString());
        r.setKeepAlive(false);
        return r;
    }

    /** Terima flush paket client (POST) — proses lalu antrekan jawaban. */
    private Response receive(IHTTPSession session, String sid) {
        Sess s = (sid == null) ? null : sessions.get(sid);
        if (s == null || s.closed) {
            return unknownSession();
        }
        s.lastSeen = System.currentTimeMillis();
        String body;
        try {
            body = readBody(session);
        } catch (Throwable t) {
            return text(Response.Status.BAD_REQUEST, "bad request");
        }
        int i = 0;
        final int len = body.length();
        while (i < len) {
            int c = body.indexOf(':', i);
            if (c < 0) {
                break;
            }
            int plen;
            try {
                plen = Integer.parseInt(body.substring(i, c));
            } catch (NumberFormatException nfe) {
                break;
            }
            if (plen < 0 || c + 1 + plen > len) {
                break;
            }
            String pkt = body.substring(c + 1, c + 1 + plen);
            handlePacket(s, pkt);
            i = c + 1 + plen;
        }
        return text(Response.Status.OK, "ok");
    }

    /**
     * Baca body POST LANGSUNG dari stream (persis Content-Length) — TIDAK lewat
     * parseBody. Alasan: vendored NanoHTTPD memakai aturan form untuk
     * Content-Type "application/x-www-form-urlencoded" (isi masuk ke parms,
     * files["postData"] TIDAK diisi) sehingga framing engine.io bisa hilang.
     * Client game asli mengirim text/plain, tetapi dengan baca langsung server
     * ini tahan SEMUA Content-Type (curl, debugger, WebView varian apa pun).
     */
    private static String readBody(IHTTPSession session) throws Exception {
        String cl = session.getHeaders().get("content-length");
        int n = 0;
        if (cl != null) {
            try {
                n = Integer.parseInt(cl.trim());
            } catch (NumberFormatException nfe) {
                n = 0;
            }
        }
        if (n <= 0) {
            return "";
        }
        if (n > 1_048_576) {
            n = 1_048_576; // pengaman — flush engine.io polling tak perlu lebih besar
        }
        java.io.InputStream in = session.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(n);
        byte[] buf = new byte[8192];
        int left = n;
        while (left > 0) {
            int r = in.read(buf, 0, Math.min(buf.length, left));
            if (r < 0) {
                break;
            }
            bos.write(buf, 0, r);
            left -= r;
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    // ---------------- socket.io v2 paket ----------------

    private void handlePacket(Sess s, String pkt) {
        if (pkt == null || pkt.length() == 0) {
            return;
        }
        char t = pkt.charAt(0);
        switch (t) {
            case '2': // PING engine.io → PONG
                s.out.offer("3");
                return;
            case '3': // PONG (tidak diharapkan dari client v3) — abaikan
                return;
            case '4': // engine.io MESSAGE → payload socket.io
                if (pkt.length() < 2) {
                    return;
                }
                handleSio(s, pkt.substring(1));
                return;
            default: // 0/1/5/6 dari client → abaikan (tanpa websocket/binary)
                return;
        }
    }

    private void handleSio(Sess s, String p) {
        if (p.length() == 0) {
            return;
        }
        char type = p.charAt(0);
        if (type == '0') {
            return; // CONNECT namespace "/" — sudah terbuka lewat OPEN
        }
        if (type == '1') {
            s.closed = true; // DISCONNECT
            sessions.remove(s.sid);
            return;
        }
        if (type != '2') {
            return; // 3=ACK dari client? 4=ERROR? — tidak dipakai game
        }
        // EVENT: "42<ackid>[\"handler.process\",{pesan}]"
        int i = 1;
        while (i < p.length() && p.charAt(i) >= '0' && p.charAt(i) <= '9') {
            i++;
        }
        String ackId = p.substring(1, i);
        String json = p.substring(i);
        long t0 = System.currentTimeMillis();
        String msg = firstArgJson(json);
        String action = strField(msg, "action");
        String data;
        if ("loginGame".equals(action)) {
            String userId = strField(msg, "userId");
            if (userId == null || userId.length() == 0) {
                userId = LoginData.localUserId();
            }
            String password = strField(msg, "password");
            data = LoginData.loginGameResponse(userId, password);
            boolean baru = data.indexOf("\"isCreated\":true") >= 0;
            HumanLog.ev('▶', "Login lokal: " + userId + (baru ? " masuk — akun BARU dibuat" : " masuk — akun lama")
                            + " · " + HumanLog.fmtMs(System.currentTimeMillis() - t0) + " — channel game_origin",
                    null);
        } else if ("GetServerList".equals(action)) {
            data = LoginData.serverListData();
            HumanLog.ev('◀', "Daftar server dikirim: " + LoginData.serverCount() + " server · "
                            + HumanLog.fmtMs(System.currentTimeMillis() - t0), null);
        } else if ("LoginAnnounce".equals(action)) {
            data = "{}";
        } else if ("SaveHistory".equals(action) || "SaveLanguage".equals(action)
                || "SaveUserEnterInfo".equals(action)) {
            data = "{}";
        } else {
            // aksi tak dikenal — jawab ret 0 kosong agar game tidak menggantung
            data = "{}";
            HumanLog.ev('ℹ', "Aksi login lokal tak dikenal: " + (action == null ? "(kosong)" : action)
                    + " — dijawab ret 0 kosong", null);
        }
        if (ackId.length() > 0) {
            String resp = "{\"ret\":0,\"data\":" + quote(data) + "}";
            s.out.offer("43" + ackId + "[" + resp + "]");
        }
    }

    // ---------------- parser JSON mini (cukup untuk pesan game) ----------------

    /** Ambil objek pesan dari array JSON event '["handler.process",{pesan}]' — args[1]. */
    private static String firstArgJson(String jsonArray) {
        if (jsonArray == null) {
            return "{}";
        }
        int a = jsonArray.indexOf('[');
        int b = jsonArray.lastIndexOf(']');
        if (a < 0 || b <= a) {
            return "{}";
        }
        String inner = jsonArray.substring(a + 1, b);
        // cari '{' pertama DI LUAR string (arg[0] = nama event "handler.process")
        int start = -1;
        boolean inStr = false;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (inStr) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inStr = false;
                }
            } else if (ch == '"') {
                inStr = true;
            } else if (ch == '{') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return "{}";
        }
        // ekstrak objek berimbang dari start
        int depth = 0;
        boolean inStr2 = false;
        for (int i = start; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (inStr2) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inStr2 = false;
                }
            } else if (ch == '"') {
                inStr2 = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return inner.substring(start, i + 1);
                }
            }
        }
        return "{}";
    }

    /** Nilai string level atas dari objek JSON — cukup untuk kunci pesan game. */
    private static String strField(String obj, String key) {
        if (obj == null || key == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int i = obj.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int c = obj.indexOf(':', i + needle.length());
        if (c < 0) {
            return null;
        }
        int j = c + 1;
        final int len = obj.length();
        while (j < len && obj.charAt(j) == ' ') {
            j++;
        }
        if (j >= len || obj.charAt(j) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        j++;
        while (j < len) {
            char ch = obj.charAt(j);
            if (ch == '\\' && j + 1 < len) {
                char nx = obj.charAt(j + 1);
                if (nx == 'n') {
                    sb.append('\n');
                } else if (nx == 't') {
                    sb.append('\t');
                } else if (nx == 'u' && j + 5 < len) {
                    try {
                        sb.append((char) Integer.parseInt(obj.substring(j + 2, j + 6), 16));
                    } catch (Throwable ignored) {
                    }
                    j += 6;
                    continue;
                } else {
                    sb.append(nx);
                }
                j += 2;
                continue;
            }
            if (ch == '"') {
                break;
            }
            sb.append(ch);
            j++;
        }
        return sb.toString();
    }

    private static String quote(String s) {
        return "\"" + Json.esc(s) + "\"";
    }

    // ---------------- util ----------------

    /** Framing polling engine.io v3: "<panjang>:<paket>". */
    private static String frame(String pkt) {
        return pkt.length() + ":" + pkt;
    }

    private static String newSid() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private static String parm(Map<String, String> parms, String key) {
        if (parms == null) {
            return null;
        }
        String v = parms.get(key);
        return (v == null || v.length() == 0) ? null : v;
    }

    private Response unknownSession() {
        return text(Response.Status.lookup(400), "{\"code\":1,\"message\":\"Session ID unknown\"}");
    }

    private void sweepExpired() {
        synchronized (SWEEPLOCK) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, Sess>> it = sessions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Sess> e = it.next();
                if (e.getValue().closed || now - e.getValue().lastSeen > SESSION_TTL_MS) {
                    e.getValue().closed = true;
                    it.remove();
                }
            }
        }
    }

    private static Response text(Response.IStatus st, String msg) {
        Response r = newFixedLengthResponse(st, "text/plain; charset=utf-8", msg);
        r.addHeader("Cache-Control", "no-store");
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }
}
