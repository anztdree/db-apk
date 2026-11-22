package com.db.local.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Satu koneksi HTTP: parse request line + header + body (Content-Length ATAU
 * Transfer-Encoding: chunked — v2 mengabaikan chunked sehingga body POST bisa
 * hilang dan log tidak lengkap; diperbaiki Bagian 18), dispatch ke handler per
 * port, tulis respons. Keep-alive HTTP/1.1 sederhana (loop baca berikutnya bila
 * client minta keep-alive). Header request ikut dicatat (fulfil spec 16.6
 * "header") via req.headersStr.
 */
public final class HttpWorker implements Runnable {
    public static final int MAX_BODY = 8 * 1024 * 1024;
    public static final int MAX_HEADER = 64 * 1024;

    public static final class Request {
        public String method, rawPath, path, query;
        public Map<String, String> headers = new HashMap<>();
        public byte[] body = new byte[0];
        public String headersStr = "";   // kompakt "k=v; k2=v2" — diisi setelah parse
        public boolean bodyTruncated = false;

        public String header(String name) {
            return headers.get(name.toLowerCase());
        }
        public String q(String name) {
            if (query == null) return null;
            String[] parts = query.split("&");
            for (String p : parts) {
                int eq = p.indexOf('=');
                if (eq > 0 && p.substring(0, eq).equals(name)) return p.substring(eq + 1);
            }
            return null;
        }
        boolean isHead() { return "HEAD".equals(method); }
    }

    public interface Handler {
        /** tulis respons ke out; kembalikan true bila respons terkirim dan keep-alive boleh lanjut */
        boolean handle(Request req, OutputStream out, Socket sock) throws IOException;
    }

    private final Socket sock;
    private final Handler handler;
    private final int listenerPort;

    public HttpWorker(Socket sock, Handler handler, int listenerPort) {
        this.sock = sock;
        this.handler = handler;
        this.listenerPort = listenerPort;
    }

    @Override
    public void run() {
        try {
            sock.setSoTimeout(8000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            OutputStream out = sock.getOutputStream();
            while (true) {
                Request req;
                try {
                    req = readRequest(in);
                } catch (SocketTimeoutException ste) {
                    break; // idle timeout keep-alive
                }
                if (req == null) break;
                req.headers.put("__listenerport", String.valueOf(listenerPort));
                captureHeaders(req);
                boolean keep = !"close".equalsIgnoreCase(req.header("connection"))
                        && !"HTTP/1.0".equalsIgnoreCase(req.header("__proto"))
                        && !req.bodyTruncated;
                boolean ok;
                try {
                    ok = handler.handle(req, out, sock);
                } catch (IOException ioe) {
                    break;
                } catch (Throwable t) {
                    LoggerCore.Entry e = new LoggerCore.Entry();
                    e.port = listenerPort;
                    e.kind = "SYS";
                    e.method = req.method; e.path = req.rawPath;
                    e.status = 500; e.error = "handler: " + t;
                    e.headers = req.headersStr;
                    e.reqLen = req.body.length;
                    LoggerCore.log(e);
                    ok = false;
                }
                if (!ok || !keep) break;
            }
        } catch (Throwable ignored) {
        } finally {
            try { sock.close(); } catch (IOException ignored) {}
        }
    }

    /** header request → string kompakt (cap HEADERS_MAX) untuk log */
    private static void captureHeaders(Request req) {
        StringBuilder hs = new StringBuilder(256);
        for (Map.Entry<String, String> en : req.headers.entrySet()) {
            String k = en.getKey();
            if (k.startsWith("__")) continue;
            if (hs.length() > 0) hs.append("; ");
            hs.append(k).append('=').append(en.getValue());
        }
        if (hs.length() > LoggerCore.HEADERS_MAX) hs.setLength(LoggerCore.HEADERS_MAX);
        req.headersStr = hs.toString();
    }

    /** null = koneksi ditutup / EOF; lempar SocketTimeoutException bila idle */
    private Request readRequest(InputStream in) throws IOException {
        // baca sampai CRLFCRLF
        StringBuilder head = new StringBuilder(512);
        int c;
        int total = 0;
        boolean sawAny = false;
        while ((c = in.read()) != -1) {
            sawAny = true;
            head.append((char) c);
            if (++total > MAX_HEADER) return null;
            int len = head.length();
            if (len >= 4 && head.charAt(len - 1) == '\n' && head.charAt(len - 2) == '\r'
                    && head.charAt(len - 3) == '\n' && head.charAt(len - 4) == '\r') {
                break;
            }
        }
        if (!sawAny) return null;
        if (head.length() < 4) return null;

        String headStr = head.toString();
        String[] lines = headStr.split("\r\n");
        String[] rl = lines[0].split(" ");
        if (rl.length < 2) {
            LoggerCore.Entry e = new LoggerCore.Entry();
            e.port = listenerPort;
            e.kind = "SYS";
            e.method = "BAD";
            e.path = lines[0].length() > 200 ? lines[0].substring(0, 200) : lines[0];
            e.status = 400;
            e.error = "request line tidak valid";
            LoggerCore.log(e);
            try {
                HttpWorker.writeResponse(out(), 400, "Bad Request",
                        HttpWorker.baseHeaders("text/plain; charset=utf-8"),
                        HttpWorker.text("bad request line"));
            } catch (IOException ignored) {}
            return null;
        }
        Request req = new Request();
        req.method = rl[0];
        req.rawPath = rl[1];
        int qi = rl[1].indexOf('?');
        req.path = qi >= 0 ? rl[1].substring(0, qi) : rl[1];
        req.query = qi >= 0 ? rl[1].substring(qi + 1) : null;
        for (int i = 1; i < lines.length; i++) {
            int ci = lines[i].indexOf(':');
            if (ci > 0) {
                req.headers.put(lines[i].substring(0, ci).trim().toLowerCase(),
                        lines[i].substring(ci + 1).trim());
            }
        }
        readBody(in, req);
        return req;
    }

    private OutputStream out() throws IOException { return sock.getOutputStream(); }

    /** body: Content-Length atau chunked (baru — Bagian 18); > MAX_BODY → truncate + close */
    private void readBody(InputStream in, Request req) throws IOException {
        long cl = 0;
        String clen = req.header("content-length");
        if (clen != null) {
            try { cl = Long.parseLong(clen.trim()); } catch (NumberFormatException nfe) { cl = 0; }
        }
        String te = req.header("transfer-encoding");
        boolean chunked = te != null && te.toLowerCase().contains("chunked");

        if (chunked) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            boolean done = false;
            while (!done && buf.size() <= MAX_BODY) {
                String sizeLine = readLineAscii(in);
                if (sizeLine == null) break;
                int semi = sizeLine.indexOf(';');
                if (semi >= 0) sizeLine = sizeLine.substring(0, semi);
                sizeLine = sizeLine.trim();
                int size;
                try { size = Integer.parseInt(sizeLine, 16); }
                catch (NumberFormatException nfe) { break; }
                if (size == 0) {
                    // trailer sampai baris kosong
                    String t;
                    while ((t = readLineAscii(in)) != null && !t.isEmpty()) { /* skip trailer */ }
                    done = true;
                    break;
                }
                int take = (int) Math.min(size, MAX_BODY - buf.size());
                byte[] chunk = new byte[take];
                if (!readFully(in, chunk)) break;
                buf.write(chunk, 0, chunk.length);
                if (take < size) { // sisanya diabaikan — tandai truncate
                    req.bodyTruncated = true;
                    drain(in, size - take);
                }
                in.read(); in.read(); // CRLF setelah chunk
            }
            req.body = buf.toByteArray();
            req.headers.put("__reqLen", String.valueOf(req.body.length));
            return;
        }

        if (cl > MAX_BODY) {
            // terlalu besar: baca sebanyak cap, sisanya dibuang, koneksi ditutup setelah respons
            cl = MAX_BODY;
            req.bodyTruncated = true;
        }
        if (cl > 0) {
            byte[] body = new byte[(int) cl];
            int off = 0;
            while (off < body.length) {
                int r = in.read(body, off, body.length - off);
                if (r < 0) break;
                off += r;
            }
            req.body = body;
            req.headers.put("__reqLen", String.valueOf(cl));
        }
    }

    private static void drain(InputStream in, long n) throws IOException {
        byte[] sink = new byte[65536];
        while (n > 0) {
            int r = in.read(sink, 0, (int) Math.min(sink.length, n));
            if (r < 0) return;
            n -= r;
        }
    }

    /** baca 1 baris ASCII (s.d. \n, cap 8 KB) — untuk framing chunked */
    private static String readLineAscii(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder(32);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.append((char) c);
            if (b.length() > 8192) return null;
        }
        if (c == -1 && b.length() == 0) return null;
        return b.toString();
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

    /* ---------- penulis respons ---------- */

    public static void writeResponse(OutputStream out, int status, String statusText,
                                     Map<String, String> headers, byte[] body) throws IOException {
        writeResponse(out, status, statusText, headers, body, false);
    }

    /** omitBody=true untuk HEAD (header tetap, tanpa body — baru Bagian 18) */
    public static void writeResponse(OutputStream out, int status, String statusText,
                                     Map<String, String> headers, byte[] body,
                                     boolean omitBody) throws IOException {
        StringBuilder h = new StringBuilder(256);
        h.append("HTTP/1.1 ").append(status).append(' ').append(statusText).append("\r\n");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        h.append("Content-Length: ").append(body == null ? 0 : body.length).append("\r\n");
        h.append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (body != null && body.length > 0 && !omitBody) out.write(body);
        out.flush();
    }

    public static Map<String, String> baseHeaders(String contentType) {
        Map<String, String> m = new HashMap<>();
        if (contentType != null) m.put("Content-Type", contentType);
        m.put("Connection", "keep-alive");
        m.put("Server", "db-local-mini");
        return m;
    }

    public static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
