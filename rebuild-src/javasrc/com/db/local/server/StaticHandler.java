package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Listener 9527 (config bin mirror configus.sjmobilegame.com:443) dan 9528
 * (CDN mirror dragonh5cdn.popoh5.com:443) — file statis dari docroot server/.
 * Khusus 9527: /bs/db/android/*.bin dilayani LANGSUNG dari assets (fallback cepat,
 * anti-race dengan ekstraksi pertama) dan log menyertakan JSON ter-decode pasca-XOR
 * "DragonBall" (readme 15.6/16.6).
 * v4 (Bagian 19) — ANTI-RACE EKSTRAKSI TOTAL (akar popup "Server base.version is
 * Not Exist" — smali LaunchActivity memetakan HTTP 404 → string "404" lalu popup):
 * SEMUA file yang belum terekstrak ke server/ dilayani LANGSUNG dari assets
 * (fallback generik: disk dulu → assets). Dua listener kini punya AssetsSource.
 * Log v3 (Bagian 18): teks (js/json/html/…) preview s.d. 2048 B; file biner DILOG
 * KOMPAK "[bin N B]" + respCT (ribuan file resource tidak membanjiri buffer dengan
 * hex — metadata ukuran/status/durasi tetap utuh, arsip NDJSON disk tetap lengkap);
 * Content-Type respons dicatat di extra (diagnosis MIME WebView); HEAD tanpa body.
 */
public final class StaticHandler implements HttpWorker.Handler {
    private final File docroot;
    private final AssetsSource assets;   // null di desktop test
    private final CountDownLatch readyEarly;
    private final int xorLogPort;        // PORT_CONFIG bila log decode, -1 bila tidak

    /** sumber asset Android (diabstraksi agar bisa dites di desktop) */
    public interface AssetsSource {
        /** kembalikan isi file asset "server_root/<rel>" atau null */
        byte[] read(String rel);
    }

    public StaticHandler(File docroot, AssetsSource assets, CountDownLatch readyEarly, int xorLogPort) {
        this.docroot = docroot;
        this.assets = assets;
        this.readyEarly = readyEarly;
        this.xorLogPort = xorLogPort;
    }

    @Override
    public boolean handle(HttpWorker.Request req, OutputStream out, Socket sock) throws IOException {
        long t0 = System.currentTimeMillis();
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = req.header("__listenerport") != null
                ? Integer.parseInt(req.header("__listenerport")) : LoggerCore.PORT_CDN;
        e.method = req.method;
        e.path = req.rawPath;
        e.reqLen = req.body.length;
        e.headers = req.headersStr;
        if (req.body.length > 0) e.reqPreview = preview(req.body);
        boolean head = req.isHead();

        if ("OPTIONS".equals(req.method)) {
            Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=utf-8");
            h.put("Access-Control-Allow-Origin", "*");
            h.put("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS");
            h.put("Access-Control-Allow-Headers", req.header("access-control-request-headers") != null
                    ? req.header("access-control-request-headers") : "*");
            byte[] empty = new byte[0];
            HttpWorker.writeResponse(out, 204, "No Content", h, empty);
            e.status = 204; e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return true;
        }
        if (!"GET".equals(req.method) && !"HEAD".equals(req.method)) {
            byte[] b = HttpWorker.text("method not allowed");
            HttpWorker.writeResponse(out, 405, "Method Not Allowed",
                    HttpWorker.baseHeaders("text/plain; charset=utf-8"), b);
            e.status = 405; e.respLen = b.length; e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return true;
        }

        // anti-race: tunggu ekstraksi tahap awal (db/ + html + manifest + js) maks 60 s
        if (readyEarly != null) {
            try {
                if (!readyEarly.await(60, TimeUnit.SECONDS) && !exists(resolve(req.path))) {
                    // lanjut; kalau file tetap tidak ada akan 404 di bawah (ter-log)
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] data = null;
        String fromAssets = null;

        // FAST PATH 9527: bin config langsung dari assets (sebelum ekstraksi selesai)
        if (assets != null && req.path.startsWith("/bs/db/android/")
                && req.path.endsWith(".bin")) {
            data = assets.read("server_root" + req.path);
            if (data != null) fromAssets = "fast";   // fast-path 9527 (selalu assets dulu)
        }

        File f = resolve(req.path);
        if (data == null) {
            if (f != null && f.isFile() && safe(f)) {
                data = readAll(f);
            }
        }

        // v4 (Bagian 19): fallback generik — file belum terekstrak? ambil dari assets.
        // Membunuh seluruh race ekstraksi (base.version/resource.version/upgrade.json/
        // size.json/all.zip/html/js — apa pun yang diminta saat ekstraksi masih jalan).
        if (data == null && assets != null) {
            byte[] ad = assets.read("server_root" + req.path);
            if (ad != null && ad.length > 0) {
                data = ad;
                fromAssets = "race";   // file belum terekstrak → anti-race 19.1
            }
        }

        if (data == null) {
            byte[] b = HttpWorker.text("404 not found: " + req.path);
            Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=utf-8");
            HttpWorker.writeResponse(out, 404, "Not Found", h, b);
            e.status = 404;
            e.respLen = b.length;
            e.error = "file not found under server/";
            e.respPreview = "404 not found: " + req.path;
            e.durMs = System.currentTimeMillis() - t0;
            LoggerCore.log(e);
            return true;
        }

        String ct = JsonUtil.contentType(req.path);
        Map<String, String> h = HttpWorker.baseHeaders(ct);
        h.put("Cache-Control", "no-store");
        h.put("Access-Control-Allow-Origin", "*");
        HttpWorker.writeResponse(out, 200, "OK", h, data, head);

        e.status = 200;
        e.respLen = data.length;
        e.durMs = System.currentTimeMillis() - t0;
        StringBuilder extra = new StringBuilder(64);
        if (fromAssets != null) extra.append("src=assets (")
                .append(fromAssets.equals("fast") ? "fast-path 9527" : "not extracted yet — anti-race")
                .append(") | ");
        extra.append("respCT=").append(ct);
        if (head) extra.append(" (HEAD, no body)");

        if (e.port == LoggerCore.PORT_CONFIG && req.path.endsWith(".bin")) {
            // khusus 9527 (16.6): log menyertakan JSON config TER-DECODE pasca-XOR DragonBall
            String decodedJson = decodeXor(data);
            String cut = decodedJson.length() > LoggerCore.BODY_PREVIEW_MAX
                    ? decodedJson.substring(0, LoggerCore.BODY_PREVIEW_MAX) + "…[+"
                      + (decodedJson.length() - LoggerCore.BODY_PREVIEW_MAX) + "B]" : decodedJson;
            e.extra = extra.append(" | DECODED-XOR: ").append(cut).toString();
            e.respPreview = "[xor " + data.length + " B — decoded in data]";
        } else if (isTextType(ct)) {
            e.extra = extra.toString();
            int n = Math.min(data.length, LoggerCore.STATIC_TEXT_PREVIEW);
            e.respPreview = new String(data, 0, n, java.nio.charset.StandardCharsets.UTF_8);
            if (data.length > n) e.respPreview += "…[+" + (data.length - n) + "B]";
        } else {
            // biner (png/jpg/zip/mp3/…) — kompakt, tetap lengkap di disk (NDJSON)
            e.extra = extra.toString();
            e.respPreview = "[bin " + data.length + " B]";
        }
        LoggerCore.log(e);
        return true;
    }

    private static boolean isTextType(String ct) {
        return ct.startsWith("text/") || ct.contains("javascript")
                || ct.contains("json") || ct.contains("xml") || ct.contains("svg");
    }

    private File resolve(String urlPath) {
        String p = urlPath;
        int qi = p.indexOf('?');
        if (qi >= 0) p = p.substring(0, qi);
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) p = "index-native.html";
        return new File(docroot, p);
    }

    private boolean exists(File f) { return f != null && f.isFile(); }

    /** cegah path traversal keluar docroot */
    private boolean safe(File f) {
        try {
            String root = docroot.getCanonicalPath() + File.separator;
            return f.getCanonicalPath().startsWith(root);
        } catch (IOException ioe) {
            return false;
        }
    }

    private static byte[] readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) Math.min(f.length(), Integer.MAX_VALUE - 8)];
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            if (off != buf.length) {
                byte[] out = new byte[off];
                System.arraycopy(buf, 0, out, 0, off);
                return out;
            }
            return buf;
        } finally {
            in.close();
        }
    }

    /** XOR kunci "DragonBall" — port literal LaunchActivity.java:60 (fakta 5.2) */
    public static String decodeXor(byte[] data) {
        byte[] key = "DragonBall".getBytes();
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ key[i % key.length]);
        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** preview body request: teks apa adanya (cap BODY_PREVIEW_MAX), biner kompakt */
    static String preview(byte[] data) {
        int n = Math.min(data.length, LoggerCore.BODY_PREVIEW_MAX);
        boolean binary = false;
        for (int i = 0; i < n; i++) {
            int c = data[i] & 0xff;
            if (c == 0 || (c < 0x09) || (c > 0x0d && c < 0x20 && c != 0x1b)) { binary = true; break; }
        }
        if (binary) {
            return "[bin " + data.length + " B] " + hex(data, Math.min(n, 64));
        }
        StringBuilder b = new StringBuilder(n);
        b.append(new String(data, 0, n, java.nio.charset.StandardCharsets.UTF_8));
        if (data.length > n) b.append("…[+").append(data.length - n).append("B]");
        return b.toString();
    }

    static String hex(byte[] data, int n) {
        StringBuilder b = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            b.append(String.format("%02x", data[i] & 0xff));
        }
        return b.toString();
    }
}
