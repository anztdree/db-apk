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
 * EVOLUSI Bagian 74 — capture v2 DARI SEGALA SISI (arahan owner: lanjutkan
 * log debug port 4000, JANGAN bikin sistem baru). Format batch naik ke v2:
 *   {"v":2,"sid":"s-xxx","meta":{...sekali di batch sukses pertama...},
 *    "events":[{ts,type,level?,text,dur?,stack?,body?,resp?}]}
 * Field baru dipetakan apa adanya ke Entry log global (NOL karangan):
 *   sid   → extra "sid=<sid>" pada SETIAP baris CLIENT (identitas sesi
 *           ikut ke NDJSON + dashboard + salinan)
 *   meta  → SATU baris kind CLIENT method "meta" (UA/layar/dpr/memori/
 *           koneksi/egret — fakta device persis dari halaman)
 *   dur   → durMs (durasi kejadian client, mis. XHR selesai N ms)
 *   body  → reqPreview  (request body yang dikirim halaman, ≤300 char)
 *   resp  → respPreview (preview respons yang diterima halaman, ≤300 char)
 *   stack → Entry.stack (field NDJSON baru; dashboard menampilkan sub-baris
 *           "stack:" — dulu stack JS hilang total)
 *
 * Arahan owner (kata-kata persis, 26 Nov 2026):
 *   "kalau loading ya dia itu loading apa ! lihat log eruda sebagai contoh
 *    goblok !!"
 *
 * Tiap kejadian sisi client (console.* 18 metode, jserror, pemuatan
 * script/media, resource timing — termasuk file yang dilayani shell
 * EgretNative dari penyimpanan lokal dan KARENA ITU tidak pernah sampai ke
 * listener HTTP server, longtask, lifecycle, perf, XHR/fetch + body) menjadi
 * SATU baris NDJSON di file global yang SAMA:
 *   kind=CLIENT · port=9528 (chip CDN di dashboard) · method=jenis kejadian ·
 *   path=teks apa adanya dari halaman · level=ERROR (field error terisi)
 * bila kejadian itu memang error (console.error / jserror / GAGAL muat /
 * XHR gagal atau 4xx / fetch gagal / longtask tidak — longtask WARN).
 *
 * JUJUR:
 *  - isi baris = PERSIS teks dari halaman — nol karangan, nol pelengkap;
 *  - request transport POST /log/client milik capture sendiri TIDAK jadi
 *    baris access log (countSelf — anti-pollution, pola logger sejak
 *    Bagian 18); yang jadi baris adalah ISI kejadiannya;
 *  - body rusak / bukan format = 400 + baris error jujur (bukan diam);
 *  - format v1 lama (tanpa sid/dur/stack/body/resp) TETAP diterima —
 *    field baru opsional, parser lama tidak diubah perilakunya.
 */
public final class ClientLogHandler implements HttpWorker.Handler {

    /** cap body batch: 256 KB (client membatasi buffer 400 event × ~1200 char) */
    static final int MAX_BATCH = 262144;
    /** cap field baru (server-side, pengaman — client sudah membatasi) */
    static final int MAX_STACK = 2000;
    static final int MAX_SIDE = 500;
    static final int MAX_SID = 80;

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
        boolean batchOk = false;

        Batch batch;
        try {
            batch = parseBatch(new String(req.body, java.nio.charset.StandardCharsets.UTF_8));
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

        // BARU v74: baris META — fakta device dari halaman, sekali per sesi baru
        if (batch.meta != null) {
            LoggerCore.Entry me = base(req, t0);
            me.kind = "CLIENT";
            me.method = "meta";
            me.path = cap(batch.meta, 1200);
            me.status = 0;
            if (batch.sid != null) me.extra = "sid=" + batch.sid;
            LoggerCore.log(me);
        }

        for (int i = 0; i < batch.events.size(); i++) {
            Event ev = batch.events.get(i);
            LoggerCore.Entry le = base(req, t0);
            le.kind = "CLIENT";
            le.method = ev.type;
            le.path = ev.text;
            le.status = 0;          // kejadian client — status HTTP server tidak berlaku
            if (ev.dur >= 0) le.durMs = ev.dur;
            if (ev.body != null) le.reqPreview = cap(ev.body, MAX_SIDE);
            if (ev.resp != null) le.respPreview = cap(ev.resp, MAX_SIDE);
            if (ev.stack != null) le.stack = cap(ev.stack, MAX_STACK);
            if (batch.sid != null) le.extra = "sid=" + batch.sid;
            if ("ERROR".equals(ev.level)) le.error = causeOf(ev.type);
            LoggerCore.log(le);
        }
        batchOk = true;   // v28 N2: batch valid tercatat — mulai hitung diamnya

        byte[] b = HttpWorker.text("ok\n");
        Map<String, String> h = HttpWorker.baseHeaders("text/plain; charset=utf-8");
        HttpWorker.writeResponse(out, 200, "OK", h, b);
        if (batchOk) noteBatch(batch.sid);   // v28 N2: batch terakhir per sid
        return true;
    }

    /* ================= v28 N2 — pelacak batch per sid =================
     * Batch capture client dipantau per sid (cap 8 — sesi terbaru saja).
     * Sid yang pernah hidup (>= 3 batch) lalu bisu >= 10 dtk dilaporkan
     * watchdog SATU baris pendek gaya server, lalu tiap 10 dtk — fakta
     * server sendiri (watchdog), bukan karangan analis di log. */
    static final int N2_TRACK_CAP = 8;
    static final int N2_MIN_BATCH = 3;
    static final long N2_SILENT_MS = 10000;
    /** sid → {lastBatchMs, count, lastReportMs} — urutan masuk = urutan terpakai */
    static final java.util.LinkedHashMap<String, long[]> N2_TRACK =
            new java.util.LinkedHashMap<String, long[]>();

    static synchronized void noteBatch(String sid) {
        if (sid == null || sid.isEmpty()) return;
        long now = System.currentTimeMillis();
        long[] t = N2_TRACK.get(sid);
        if (t == null) {
            N2_TRACK.put(sid, new long[]{now, 1, 0});
            while (N2_TRACK.size() > N2_TRACK_CAP) {
                String oldest = N2_TRACK.keySet().iterator().next();
                N2_TRACK.remove(oldest);
            }
        } else {
            t[0] = now;
            t[1]++;
        }
    }

    static synchronized void watchdogScan() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, long[]> en : N2_TRACK.entrySet()) {
            long[] t = en.getValue();
            if (t[1] < N2_MIN_BATCH) continue;
            long silentMs = now - t[0];
            if (silentMs < N2_SILENT_MS) continue;
            if (t[2] != 0 && now - t[2] < N2_SILENT_MS) continue;
            t[2] = now;
            LoggerCore.Entry we = new LoggerCore.Entry();
            we.port = LoggerCore.PORT_CDN;
            we.kind = "WATCH";
            we.method = "N2";
            we.path = "client bisu " + (silentMs / 1000) + " dtk — batch terakhir sid "
                    + en.getKey() + " — worker beat mati";
            we.status = 200;
            we.durMs = 0;
            LoggerCore.log(we);
        }
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
        if (type.startsWith("console.assert")) return "console.assert dari halaman game";
        if ("jserror".equals(type)) return "window.onerror dari halaman game";
        if ("unhandledrejection".equals(type)) return "unhandledrejection dari halaman game";
        if ("script".equals(type)) return "script halaman game gagal dimuat di client";
        if ("media".equals(type)) return "media (img/audio/video) halaman game gagal dimuat di client";
        if ("xhr".equals(type)) return "XHR halaman game gagal/4xx di client — mungkin tidak pernah sampai ke server";
        if ("fetch".equals(type)) return "fetch halaman game gagal di client";
        if ("resource".equals(type)) return "resource halaman game gagal dimuat di client";
        if ("perf".equals(type)) return "perf halaman game — indikasi masalah loading";
        return "kejadian error dari halaman game";
    }

    /* ---------- format batch v2: {"v":2,"sid":"..","meta":{..},"events":[..]} ----------
       v1 lama {"v":1,"events":[..]} tetap diterima — sid/meta/dur/stack/body/resp
       opsional; field tidak dikenal dilewati apa adanya. */

    static final class Event {
        String type = "unknown";
        String level = "INFO";
        String text = "(kosong)";
        long dur = -1;        // -1 = tidak ada
        String stack;
        String body;
        String resp;
    }

    static final class Batch {
        String sid;
        String meta;          // baris meta siap-tulis (null bila batch tanpa meta)
        List<Event> events = new ArrayList<Event>();
    }

    static Batch parseBatch(String body) {
        P p = new P(body);
        Batch out = new Batch();
        p.ws();
        p.expect('{');
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
                        out.events.add(parseEvent(p));
                        p.ws();
                        int sep = p.next();
                        if (sep == ',') continue;
                        if (sep == ']') break;
                        throw new IllegalArgumentException("events: pemisah tak terduga '" + (char) sep + "'");
                    }
                }
            } else if ("sid".equals(key)) {
                out.sid = cap(p.string(), MAX_SID);
            } else if ("meta".equals(key)) {
                out.meta = parseMeta(p);
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

    /** meta object → SATU baris fakta device (NOL karangan — hanya nilai asli):
     *  "sid=<sid> ua=<ua> screen=<sw>x<sh>@<dpr> mem=<mem>MB conn=<conn> egret=<bool> lang=<lang>" */
    private static String parseMeta(P p) {
        p.ws();
        p.expect('{');
        String ua = null, conn = null, lang = null;
        long sw = -1, sh = -1, dpr = -1, mem = -1;
        boolean egret = false;
        int c = p.peek();
        if (c == '}') { p.next(); return "(meta kosong)"; }
        while (true) {
            p.ws();
            String key = p.string();
            p.ws();
            p.expect(':');
            p.ws();
            if ("ua".equals(key)) ua = p.string();
            else if ("conn".equals(key)) conn = p.string();
            else if ("lang".equals(key)) lang = p.string();
            else if ("sw".equals(key)) sw = p.rawLong();
            else if ("sh".equals(key)) sh = p.rawLong();
            else if ("dpr".equals(key)) dpr = p.rawLong();
            else if ("mem".equals(key)) mem = p.rawLong();
            else if ("egret".equals(key)) egret = p.bool();
            else p.skipValue();
            p.ws();
            int sep = p.next();
            if (sep == ',') continue;
            if (sep == '}') break;
            throw new IllegalArgumentException("meta: pemisah tak terduga '" + (char) sep + "'");
        }
        StringBuilder b = new StringBuilder(160);
        b.append("device halaman game: ua=").append(ua == null ? "?" : ua)
         .append(" | screen=").append(sw >= 0 ? sw : '?').append('x').append(sh >= 0 ? sh : '?')
         .append('@').append(dpr >= 0 ? dpr : '?')
         .append(" | mem=").append(mem >= 0 ? String.valueOf(mem) + "MB" : "?")
         .append(" | conn=").append(conn == null || conn.length() == 0 ? "?" : conn)
         .append(" | egret=").append(egret ? "true" : "false")
         .append(" | lang=").append(lang == null || lang.length() == 0 ? "?" : lang);
        return b.toString();
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
            else if ("dur".equals(key)) ev.dur = p.rawLong();
            else if ("stack".equals(key)) ev.stack = p.string();
            else if ("body".equals(key)) ev.body = p.string();
            else if ("resp".equals(key)) ev.resp = p.string();
            else p.skipValue();
            p.ws();
            int sep = p.next();
            if (sep == ',') continue;
            if (sep == '}') break;
            throw new IllegalArgumentException("event: pemisah tak terduga '" + (char) sep + "'");
        }
        return ev;
    }

    private static String cap(String s, int n) {
        if (s == null) return null;
        return s.length() > n ? s.substring(0, n) : s;
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

        /** angka mentah (integer) — token dibaca apa adanya sampai delimiter */
        long rawLong() {
            StringBuilder b = new StringBuilder(16);
            while (true) {
                int g = peek();
                if (g == -1 || g == ',' || g == '}' || g == ']' || g == ' ' || g == '\n' || g == '\t' || g == '\r') break;
                b.append((char) next());
            }
            try {
                return Long.parseLong(b.toString());
            } catch (NumberFormatException nfe) {
                // desimal (mis. dpr 2.5) — ambil bagian bulat
                int dot = b.indexOf(".");
                try {
                    return dot >= 0 ? Long.parseLong(b.substring(0, dot)) : -1;
                } catch (NumberFormatException nfe2) {
                    return -1;
                }
            }
        }

        boolean bool() {
            if (s.startsWith("true", i)) { i += 4; return true; }
            if (s.startsWith("false", i)) { i += 5; return false; }
            throw new IllegalArgumentException("diharapkan boolean");
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
