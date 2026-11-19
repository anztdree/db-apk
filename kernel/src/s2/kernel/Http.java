// ============================================================
// HTTP — server loop kernel (bind 127.0.0.1:4000, loopback saja).
// Router: /socket.io/ (wire) + /dash (UI owner) + /dash/api/*
// (API owner) + dispatch HTTP panas (tier-1/tier-2) + static/
// zona + HOLD (Hukum 2: ditahan sampai timeout — NOL popup).
// ============================================================
package s2.kernel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Http {
    private static volatile ServerSocket server;
    private static volatile boolean running = false;

    private Http() {}

    public static void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    int port = Wire.engineCfg().port;
                    ServerSocket ss = new ServerSocket();
                    ss.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 128);
                    server = ss;
                    Logr.write("info", "engine", "HTTP engine hidup di 127.0.0.1:" + port + " (loopback)");
                    while (running) {
                        try {
                            final Socket s = ss.accept();
                            s.setSoTimeout(90000);
                            Thread conn = new Thread(new Runnable() {
                                public void run() {
                                    handleConnection(s);
                                }
                            }, "s2-conn");
                            conn.setDaemon(true);
                            conn.start();
                        } catch (Throwable acceptErr) {
                            if (!running) return;
                            Logr.write("error", "engine", "accept error: " + acceptErr);
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }
                } catch (Throwable t) {
                    running = false;
                    Logr.write("error", "engine", "engine HTTP gagal start: " + t);
                }
            }
        }, "s2-kernel-engine");
        t.setDaemon(true);
        t.start();
    }

    // ---------- satu koneksi ----------
    private static void handleConnection(Socket socket) {
        try {
            socket.setSoTimeout(90000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            byte[] head = readHead(in);
            if (head == null) {
                socket.close();
                return;
            }
            Parsed p = parseRequest(head);
            if (p == null) {
                writeSimple(out, 400, "text/plain", "bad request");
                socket.close();
                return;
            }
            byte[] body = new byte[0];
            int contentLength = p.contentLength;
            if (contentLength > 0) {
                if (contentLength > 10 * 1024 * 1024) contentLength = 10 * 1024 * 1024;
                body = readBody(in, contentLength);
            }

            // --- preflight CORS ---
            if ("OPTIONS".equals(p.method)) {
                writePreflight(out);
                socket.close();
                return;
            }

            // --- upgrade websocket (mesin wire) ---
            String upgrade = p.headers.get("upgrade");
            if (upgrade != null && upgrade.toLowerCase().contains("websocket")
                    && p.path.startsWith("/socket.io/")) {
                boolean ok = Wire.upgrade(socket, p.headers, p.query);
                if (!ok) {
                    writeSimple(out, 400, "text/plain", "upgrade gagal");
                    socket.close();
                }
                return; // socket diambil-alih wire
            }

            // --- dashboard UI (port 4000) ---
            if (("GET".equals(p.method) || "HEAD".equals(p.method))
                    && (p.path.equals("/") || p.path.equals("/dash") || p.path.equals("/dash/"))) {
                writeSimple(out, 200, "text/html; charset=utf-8", Ui.HTML);
                socket.close();
                return;
            }

            // --- dashboard API ---
            if (p.path.startsWith("/dash/api/")) {
                String resp = Dash.handle(p.method, p.path, p.query, body);
                if (resp == null) {
                    writeSimple(out, 404, "application/json", "{\"ok\":false,\"error\":\"route dashboard tidak dikenal\"}");
                } else {
                    writeSimple(out, 200, "application/json", resp);
                }
                socket.close();
                return;
            }

            // --- mesin wire polling ---
            if (p.path.equals("/socket.io/")) {
                String eio = p.query.get("EIO");
                if (eio == null || !"3".equals(eio)) {
                    Logr.write("warn", "socket", "EIO bukan v3: " + eio + " — socket.io v2/EIO3 WAJIB (protokol game)");
                    writeSimple(out, 400, "text/plain", "socket.io v2 (EIO=3) wajib");
                    socket.close();
                    return;
                }
                String transport = p.query.get("transport");
                if ("websocket".equals(transport)) {
                    boolean ok = Wire.upgrade(socket, p.headers, p.query);
                    if (!ok) {
                        writeSimple(out, 400, "text/plain", "upgrade gagal");
                        socket.close();
                    }
                    return;
                }
                if ("polling".equals(transport)) {
                    if ("GET".equals(p.method)) {
                        String resp = Wire.handlePollingGet(p.query);
                        writeRaw(out, 200, "text/plain; charset=utf-8", resp == null ? "" : resp);
                    } else if ("POST".equals(p.method)) {
                        String bodyText = new String(body, Wire.UTF8);
                        String resp = Wire.handlePollingPost(p.query, bodyText);
                        if (resp == null) {
                            writeRaw(out, 400, "text/plain", "");
                        } else {
                            writeRaw(out, 200, "text/plain; charset=utf-8", resp);
                        }
                    } else {
                        writeSimple(out, 400, "text/plain", "transport polling|websocket wajib");
                    }
                } else {
                    writeSimple(out, 400, "text/plain", "transport polling|websocket wajib");
                }
                socket.close();
                return;
            }

            // --- dispatch HTTP panas (game + sdk) ---
            if ("GET".equals(p.method) || "POST".equals(p.method) || "PUT".equals(p.method)) {
                dispatchHttp(out, socket, p, body);
                return;
            }

            writeSimple(out, 405, "text/plain", "method tidak didukung");
            socket.close();
        } catch (Throwable t) {
            try {
                socket.close();
            } catch (Throwable ignore) {
            }
        }
    }

    // ---------- dispatch zona panas ----------
    private static void dispatchHttp(OutputStream out, Socket socket, Parsed p, byte[] body) throws IOException {
        Hot.maybeRescan();
        String bodyText = body.length > 0 ? new String(body, Wire.UTF8) : null;
        Hot.Match m = Hot.matchHttp(p.path);
        if (m != null) {
            try {
                Hot.HttpResult r = Hot.executeHttp(m, p.path, jsonQuery(p.query), bodyText);
                if (r != null) {
                    Logr.write("wire", "http", p.method + " " + p.path + " -> " + r.status + " "
                            + r.contentType + " (" + r.source + ")");
                    writeBytes(out, r.status, r.contentType, r.body);
                    socket.close();
                    return;
                }
            } catch (Throwable e) {
                Logr.write("error", "http", p.method + " " + p.path + " executor error: " + e + " — HOLD");
            }
        }

        // zona static/ menang atas fallback (entry shell, override).
        // Sumber: disk (server/static/ — editan owner) dulu, lalu fallback
        // APK assets/static/** (shell game + JS bundle — NOL copy saat boot).
        String relPath = p.path.startsWith("/") ? p.path.substring(1) : p.path;
        byte[] statik = null;
        String statikSrc = null;
        File f = K.safeFile("static/" + relPath);
        if (f != null && f.isFile()) {
            statik = Util.readBytes(f);
            if (statik != null) statikSrc = "static/ zona disk";
        }
        if (statik == null) {
            statik = readAssetStatic(relPath);
            if (statik != null) statikSrc = "static/ apk assets";
        }
        if (statik != null) {
            Logr.write("wire", "http", p.method + " " + p.path + " -> " + statikSrc + " (" + statik.length + " B)");
            writeBytes(out, 200, Util.mimeOf(relPath), statik);
            socket.close();
            return;
        }

        // HUKUM 2: hold-connection — diterima lalu DITAHAN, NOL popup
        int holdMs = Wire.engineCfg().holdTimeoutMs;
        Logr.write("warn", "http", "HOLD " + p.method + " " + p.path + " (tidak ada handler/static) — ditahan " + holdMs + "ms");
        try {
            Thread.sleep(holdMs);
        } catch (InterruptedException ignore) {
        }
        writeBytes(out, 204, null, new byte[0]);
        try {
            socket.close();
        } catch (Throwable ignore) {
        }
    }

    /** fallback zona static dari APK assets/static/** (null = tidak ada). */
    private static byte[] readAssetStatic(String relPath) {
        try {
            android.content.Context ctx = K.ctx();
            if (ctx == null) return null;
            android.content.res.AssetManager am = ctx.getAssets();
            InputStream in = am.open("static/" + relPath);
            try {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] buf8 = new byte[8192];
                int n;
                while ((n = in.read(buf8)) > 0) buf.write(buf8, 0, n);
                return buf.toByteArray();
            } finally {
                try {
                    in.close();
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static String jsonQuery(Map<String, String> query) {
        Json.Obj o = new Json.Obj();
        for (Map.Entry<String, String> e : query.entrySet()) {
            o.put(e.getKey(), e.getValue());
        }
        return o.toString();
    }

    // ---------- parsing request ----------
    static class Parsed {
        String method;
        String path;
        Map<String, String> query = new LinkedHashMap<String, String>();
        Map<String, String> headers = new LinkedHashMap<String, String>();
        int contentLength;
    }

    private static byte[] readHead(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        int c;
        int total = 0;
        while ((c = in.read()) >= 0) {
            total++;
            if (total > 65536) return null;
            buf.write(c);
            if (prev == '\r' && c == '\n') {
                // cek CRLFCRLF
                byte[] data = buf.toByteArray();
                int n = data.length;
                if (n >= 4 && data[n - 4] == '\r' && data[n - 3] == '\n' && data[n - 2] == '\r' && data[n - 1] == '\n') {
                    return data;
                }
            }
            prev = c;
        }
        return buf.size() > 0 ? buf.toByteArray() : null;
    }

    private static Parsed parseRequest(byte[] head) {
        try {
            String text = new String(head, Wire.UTF8);
            int end = text.indexOf("\r\n\r\n");
            if (end < 0) return null;
            String headPart = text.substring(0, end);
            String[] lines = headPart.split("\r\n");
            if (lines.length == 0) return null;
            String[] rl = lines[0].split(" ");
            if (rl.length < 2) return null;
            Parsed p = new Parsed();
            p.method = rl[0].toUpperCase();
            String target = rl[1];
            int qi = target.indexOf('?');
            String path = qi >= 0 ? target.substring(0, qi) : target;
            String qs = qi >= 0 ? target.substring(qi + 1) : "";
            p.path = URLDecoder.decode(path, "UTF-8");
            for (String kv : qs.split("&")) {
                if (kv.length() == 0) continue;
                int eq = kv.indexOf('=');
                String k = eq >= 0 ? kv.substring(0, eq) : kv;
                String v = eq >= 0 ? kv.substring(eq + 1) : "";
                try {
                    p.query.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
                } catch (Throwable ignore) {
                }
            }
            for (int i = 1; i < lines.length; i++) {
                int ci = lines[i].indexOf(':');
                if (ci <= 0) continue;
                String k = lines[i].substring(0, ci).trim().toLowerCase();
                String v = lines[i].substring(ci + 1).trim();
                p.headers.put(k, v);
            }
            String cl = p.headers.get("content-length");
            p.contentLength = cl != null ? parseIntSafe(cl) : 0;
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static byte[] readBody(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n < 0) break;
            off += n;
        }
        return buf;
    }

    // ---------- penulis respons ----------
    private static void writeHead(OutputStream out, int status, String contentType, int bodyLen) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        if (contentType != null) sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(bodyLen).append("\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS\r\n");
        sb.append("Access-Control-Allow-Headers: Content-Type\r\n");
        sb.append("Connection: close\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes(Wire.UTF8));
    }

    private static void writeBytes(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        writeHead(out, status, contentType, body == null ? 0 : body.length);
        if (body != null && body.length > 0 && status != 204) out.write(body);
        out.flush();
    }

    private static void writeSimple(OutputStream out, int status, String contentType, String text) throws IOException {
        byte[] b = text == null ? new byte[0] : text.getBytes(Wire.UTF8);
        writeHead(out, status, contentType, b.length);
        if (b.length > 0 && status != 204) out.write(b);
        out.flush();
    }

    private static void writeRaw(OutputStream out, int status, String contentType, String text) throws IOException {
        writeSimple(out, status, contentType, text);
    }

    private static void writePreflight(OutputStream out) throws IOException {
        writeHead(out, 204, null, 0);
        out.flush();
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 101: return "Switching Protocols";
            default: return "Status";
        }
    }
}
