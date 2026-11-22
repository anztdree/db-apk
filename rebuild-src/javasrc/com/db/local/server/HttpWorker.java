package com.db.local.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Satu koneksi HTTP: parse request line + header + body (Content-Length, cap 8 MB),
 * dispatch ke handler per port, tulis respons. Keep-alive HTTP/1.1 sederhana
 * (loop baca berikutnya bila client minta keep-alive).
 */
public final class HttpWorker implements Runnable {
    public static final int MAX_BODY = 8 * 1024 * 1024;
    public static final int MAX_HEADER = 64 * 1024;

    public static final class Request {
        public String method, rawPath, path, query;
        public Map<String, String> headers = new HashMap<>();
        public byte[] body = new byte[0];

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
                boolean keep = !"close".equalsIgnoreCase(req.header("connection"))
                        && !"HTTP/1.0".equalsIgnoreCase(req.header("__proto"));
                boolean ok;
                try {
                    ok = handler.handle(req, out, sock);
                } catch (IOException ioe) {
                    break;
                } catch (Throwable t) {
                    LoggerCore.Entry e = new LoggerCore.Entry();
                    e.port = sock.getLocalPort();
                    e.method = req.method; e.path = req.rawPath;
                    e.status = 500; e.error = "handler: " + t;
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
        if (rl.length < 2) return null;
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
        long cl = 0;
        String clen = req.header("content-length");
        if (clen != null) {
            try { cl = Long.parseLong(clen.trim()); } catch (NumberFormatException nfe) { cl = 0; }
        }
        if (cl > MAX_BODY) cl = MAX_BODY; // batasi; sisanya diabaikan (dilaporkan di log)
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
        return req;
    }

    /* ---------- penulis respons ---------- */

    public static void writeResponse(OutputStream out, int status, String statusText,
                                     Map<String, String> headers, byte[] body) throws IOException {
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
        if (body != null && body.length > 0) out.write(body);
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
