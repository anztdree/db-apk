package com.db.local.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * POST /log/client (Bagian 72) — penerima event CAPTURE CLIENT dari halaman
 * game (script injeksi ClientCapture yang dipasang pada index-native.html).
 *
 * Arahan owner (kata-kata persis, 26 Nov 2026):
 *   "kalau loading ya dia itu loading apa ! lihat log eruda sebagai contoh
 *    goblok !!"
 *
 * Tiap kejadian sisi client (console.*, jserror, pemuatan script/media,
 * XHR/fetch — termasuk yang dijawab shell EgretNative dari penyimpanan
 * lokal dan KARENA ITU tidak pernah sampai ke listener HTTP server) menjadi
 * SATU baris NDJSON di file global yang SAMA:
 *   kind=CLIENT · port=9528 (chip CDN di dashboard) · method=jenis kejadian ·
 *   path=teks apa adanya dari halaman · level=ERROR (field error terisi)
 * bila kejadian itu memang error (console.error / jserror / GAGAL muat /
 * XHR gagal atau 4xx/5xx).
 *
 * JUJUR:
 *  - isi baris = PERSIS teks dari halaman — nol karangan, nol pelengkap;
 *  - request transport POST /log/client milik capture sendiri TIDAK jadi
 *    baris access log (countSelf — anti-pollution, pola logger sejak
 *    Bagian 18); yang jadi baris adalah ISI kejadiannya;
 *  - body rusak / bukan format = 400 + baris error jujur (bukan diam).
 */
public final class ClientLogHandler implements HttpWorker.Handler {

    /** cap body batch: 256 KB (client membatasi buffer 400 event × 1200 char) */
    static final int MAX_BATCH = 262144;

    @Override
    public boolean handle(HttpWorker.Request req, OutputStream out, Socket sock) throws IOException {
        long t0 = System.currentTimeMillis();

        if (!"POST".equals(req.method)) {
            byte[] b = HttpWorker.text("405 method not allowed — endpoint ini untuk POST JSON dari capture client (ClientCapture)");
            Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=utf-8");
            HttpWorker.writeResponse(out, 405, "Method Not Allowed", h, b);
            LoggerCore.Entry e = base(req, t0);
            e.status = 405;
            e.respLen = b.length;
            e.error = "endpoint /log/client hanya POST";
            LoggerCore.log(e);
            return true;
        }

        if (req.body.length > MAX_BATCH) {
            byte[] b = HttpWorker.text("413 batch terlalu besar (maks 262144 B)");
            Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=utf-8");
            HttpWorker.writeResponse(out, 413, "Payload Too Large", h, b);
            LoggerCore.Entry e = base(req, t0);
            e.status = 413;
            e.reqLen = req.body.length;
            e.respLen = b.length;
            e.error = "batch capture client " + req.body.length + " B melewati batas 262144 B";
            LoggerCore.log(e);
            return true;
        }

        // transport capture sendiri: hitung, JANGAN jadi baris access log
        LoggerCore.countSelf(LoggerCore.PORT_CDN, req.body.length, 4, false);

        List<Event> evs;
        try {
            evs = parseBatch(new String(req.body, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable t) {
            byte[] b = HttpWorker.text("400 body bukan JSON batch yang valid: " + t.getMessage());
            Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=utf-8");
            HttpWorker.writeResponse(out, 400, "Bad Request", h, b);
            LoggerCore.Entry e = base(req, t0);
            e.status = 400;
            e.reqLen = req.body.length;
            e.respLen = b.length;
            e.error = "batch log client tidak valid — " + t.getMessage();
            e.reqPreview = StaticHandler.preview(req.body);
            LoggerCore.log(e);
            return true;
        }

        for (int i = 0; i < evs.size(); i++) {
            Event ev = evs.get(i);
            LoggerCore.Entry le = base(req, t0);
            le.kind = "CLIENT";
            le.method = ev.type;
            le.path = ev.text;
            le.status = 0;          // kejadian client — status HTTP server tidak berlaku
            if ("ERROR".equals(ev.level)) le.error = causeOf(ev.type);
            LoggerCore.log(le);
        }

        byte[] b = HttpWorker.text("ok\n");
        Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=utf-8");
        HttpWorker.writeResponse(out, 200, "OK", h, b);
        return true;
    }

    private static LoggerCore.Entry base(HttpWorker.Request req, long t0) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_CDN;   // origin halaman game = 9528 → chip CDN
        e.method = req.method;
        e.path = "/log/client";
        e.reqLen = req.body.length;
        e.durMs = System.currentTimeMillis() - t0;
        return e;
    }

    /** field error = penjelasan JUJUR asal kejadian (teks lengkap tetap di path) */
    private static String causeOf(String type) {
        if (type == null) return "kejadian error dari halaman game";
        if (type.startsWith("console.error")) return "console.error dari halaman game";
        if ("jserror".equals(type)) return "window.onerror dari halaman game";
        if ("unhandledrejection".equals(type)) return "unhandledrejection dari halaman game";
        if ("script".equals(type)) return "script halaman game gagal dimuat di client";
        if ("media".equals(type)) return "media (img/audio/video) halaman game gagal dimuat di client";
        if ("xhr".equals(type)) return "XHR halaman game gagal/4xx di client — mungkin tidak pernah sampai ke server";
        if ("fetch".equals(type)) return "fetch halaman game gagal di client";
        return "kejadian error dari halaman game";
    }

    /* ---------- format batch: {"v":1,"events":[{"ts":..,"type":..,"level":..,"text":..}]} ---------- */

    static final class Event {
        String type = "unknown";
        String level = "INFO";
        String text = "(kosong)";
    }

    static List<Event> parseBatch(String body) {
        P p = new P(body);
        p.ws();
        p.expect('{');
        List<Event> out = new ArrayList<Event>();
        String key = null;
        int c = p.peek();
        if (c == '}') { p.next(); return out; }
        while (true) {
            p.ws();
            key = p.string();
            p.ws();
            p.expect(':');
            p.ws();
            if ("events".equals(key)) {
                p.expect('[');
                p.ws();
                if (p.peek() == ']') { p.next(); }
                else {
                    while (true) {
                        p.ws();
                        out.add(parseEvent(p));
                        p.ws();
                        int sep = p.next();
                        if (sep == ',') continue;
                        if (sep == ']') break;
                        throw new IllegalArgumentException("events: pemisah tak terduga '" + (char) sep + "'");
                    }
                }
            } else {
                p.skipValue();   // field lain (mis. "v") dilewati apa adanya
            }
            p.ws();
            int sep = p.next();
            if (sep == ',') continue;
            if (sep == '}') break;
            throw new IllegalArgumentException("objek batch: pemisah tak terduga '" + (char) sep + "'");
        }
        return out;
    }

    private static Event parseEvent(P p) {
        p.expect('{');
        Event ev = new Event();
        p.ws();
        int c = p.peek();
        if (c == '}') { p.next(); return ev; }
        while (true) {
            p.ws();
            String key = p.string();
            p.ws();
            p.expect(':');
            p.ws();
            if ("type".equals(key)) ev.type = p.string();
            else if ("level".equals(key)) ev.level = p.string();
            else if ("text".equals(key)) ev.text = p.string();
            else p.skipValue();
            p.ws();
            int sep = p.next();
            if (sep == ',') continue;
            if (sep == '}') break;
            throw new IllegalArgumentException("event: pemisah tak terduga '" + (char) sep + "'");
        }
        return ev;
    }

    /** parser JSON minimal (string/number/bool/null; objek/array dilewati struktur,
     *  cukup utk format batch milik capture sendiri). */
    private static final class P {
        final String s;
        int i = 0;

        P(String s) { this.s = s; }

        int peek() { return i < s.length() ? s.charAt(i) : -1; }

        int next() {
            if (i >= s.length()) throw new IllegalArgumentException("JSON berakhir tak terduga");
            return s.charAt(i++);
        }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        void expect(char c) {
            int g = next();
            if (g != c) throw new IllegalArgumentException("diharapkan '" + c + "' dapet '" + (char) g + "'");
        }

        String string() {
            if (next() != '"') throw new IllegalArgumentException("diharapkan '\"' (string JSON)");
            StringBuilder b = new StringBuilder();
            while (true) {
                int c = next();
                if (c == '"') return b.toString();
                if (c == '\\') {
                    int e = next();
                    if (e == 'n') b.append('\n');
                    else if (e == 't') b.append('\t');
                    else if (e == 'r') b.append('\r');
                    else if (e == 'b') b.append('\b');
                    else if (e == 'f') b.append('\f');
                    else if (e == 'u') {
                        int v = 0;
                        for (int k = 0; k < 4; k++) {
                            int h = next();
                            int d = Character.digit((char) h, 16);
                            if (d < 0) throw new IllegalArgumentException("\\u tidak valid");
                            v = (v << 4) | d;
                        }
                        b.append((char) v);
                    } else b.append((char) e);   // \" \\ / — verbatim
                } else b.append((char) c);
            }
        }

        void skipValue() {
            ws();
            int c = peek();
            if (c == '"') { string(); return; }
            if (c == '{' || c == '[') {
                int depth = 0;
                while (true) {
                    int g = next();
                    if (g == '"') { i--; string(); continue; }
                    if (g == '{' || g == '[') depth++;
                    else if (g == '}' || g == ']') { depth--; if (depth == 0) return; }
                }
            }
            while (true) {   // number/true/false/null
                int g = peek();
                if (g == -1 || g == ',' || g == '}' || g == ']' || g == ' ' || g == '\n' || g == '\t' || g == '\r') return;
                i++;
            }
        }
    }
}
