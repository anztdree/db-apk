package com.db.local.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logger/monitoring v3 (Bagian 18 — evolusi 15.6/16.6, komponen BARU milik APK mod):
 *  - mode ALL  : kronologis lintas port layanan (ring buffer global, cap 15000)
 *  - mode PORT : ring buffer per port (cap 6000/port) — header request, preview body
 *                req/resp (teks 4096/2048; biner kompakt "[bin N B]"), status, durasi,
 *                error; khusus 9527 menyertakan JSON config TER-DECODE pasca-XOR
 *                "DragonBall" di extra.
 *  - PERSISTEN DISK: setiap entri ditulis NDJSON ke SATU FILE
 *    <serverRoot>/logs/server.ndjson (DiskWriter v22 — 1 file di-tumpuk,
 *    append lintas sesi; v22 Bagian 70: NOL file baru per open APK) — log
 *    selamat dari restart app dan bisa diunduh via logger /download. INI
 *    JAWABAN "log itu utama".
 *  - LOG NATURAL v22 (arahan owner: "KALAU ERROR YA ERROR ! KALAU KURANG YAA
 *    KURANG ! BIAR JELAS KITA ITU KURANGNYA DIMANAAA"): entri bermasalah
 *    (status >= 400 atau ada field error) membawa "level":"ERROR" di NDJSON
 *    — grep level langsung menunjukkan kurangnya di mana; entri normal tanpa
 *    field level (access log server apa adanya).
 *  - akurasi kelengkapan: droppedGlobal/droppedPort menghitung entri yang terbuang
 *    dari buffer (terlihat di /stats & dashboard); logger TIDAK mencatat request
 *    miliknya sendiri (anti-pollution — hanya counter) sehingga alur game tidak
 *    tergeser oleh traffic dashboard.
 *  - kind: HTTP | WS | BOOT | EXTRACT | SYS (pembeda alur game vs sistem).
 */
public final class LoggerCore {
    public static final int PORT_CONFIG = 9527;
    public static final int PORT_CDN    = 9528;
    public static final int PORT_LOGIN  = 5050;
    public static final int PORT_SDK    = 9530;
    public static final int PORT_LOGGER = 4000;
    public static final int PORT_GAME  = 5051;   // Bagian 21 — server game (serverItem.url)
    public static final int PORT_FALLBACK = 4001;
    /** preview teks maksimum (naik dari 2 KB — Bagian 18, prioritas kelengkapan log) */
    public static final int BODY_PREVIEW_MAX = 4096;
    /** preview teks untuk file statis game (js/json/html/…) */
    public static final int STATIC_TEXT_PREVIEW = 2048;
    public static final int GLOBAL_CAP = 15000;
    public static final int PORT_CAP = 6000;
    public static final int HEADERS_MAX = 1536;

    public static final class Entry {
        public long seq;
        public long ts;
        public int port;
        public String kind = "HTTP";   // HTTP | WS | BOOT | EXTRACT | SYS
        public String method;
        public String path;
        public int status;
        public long durMs;
        public long reqLen;
        public long respLen;
        public String headers;   // header request, kompakt "k=v; k2=v2" (cap HEADERS_MAX)
        public String reqPreview;
        public String respPreview;
        public String extra;     // mis. JSON config ter-decode (9527) / event socket.io / respCT
        public String error;

        String esc(String s) { return s == null ? "" : JsonUtil.esc(s); }

        public String toJson() {
            StringBuilder b = new StringBuilder(768);
            b.append("{\"seq\":").append(seq)
             .append(",\"ts\":").append(ts)
             .append(",\"time\":\"").append(esc(fmtTime(ts)))
             .append("\",\"port\":").append(port)
             .append(",\"kind\":\"").append(esc(kind))
             .append("\",\"method\":\"").append(esc(method))
             .append("\",\"path\":\"").append(esc(path))
             .append("\",\"status\":").append(status);
            // v22 LOG NATURAL: level ERROR eksplisit utk entri bermasalah —
            // status >= 400 atau field error terisi. Entri normal TANPA level
            // (access log alami, bukan karangan).
            if (status >= 400 || error != null) b.append(",\"level\":\"ERROR\"");
            b.append(",\"durMs\":").append(durMs)
             .append(",\"reqLen\":").append(reqLen)
             .append(",\"respLen\":").append(respLen);
            if (headers != null)     b.append(",\"headers\":\"").append(esc(headers)).append('"');
            if (reqPreview != null)  b.append(",\"reqPreview\":\"").append(esc(reqPreview)).append('"');
            if (respPreview != null) b.append(",\"respPreview\":\"").append(esc(respPreview)).append('"');
            if (extra != null)       b.append(",\"extra\":\"").append(esc(extra)).append('"');
            if (error != null)       b.append(",\"error\":\"").append(esc(error)).append('"');
            b.append('}');
            return b.toString();
        }

        static String fmtTime(long ms) {
            // ISO lokal sederhana tanpa dependensi kalender eksternal
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(ms);
            return String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d",
                    c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
                    c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.HOUR_OF_DAY),
                    c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND),
                    c.get(java.util.Calendar.MILLISECOND));
        }

        public String time() { return fmtTime(ts); }
    }

    public static final class Counter {
        public long requests, bytesIn, bytesOut, errors;
        public String toJson() {
            return "{\"requests\":" + requests + ",\"bytesIn\":" + bytesIn
                    + ",\"bytesOut\":" + bytesOut + ",\"errors\":" + errors + "}";
        }
    }

    private static final Object LOCK = new Object();
    /** v23: kunci seq+tulis-disk — nomor seq == urutan baris di server.ndjson.
     *  Dulu (v22): seq di-assign sebelum enqueue tanpa kunci — dua thread bisa
     *  saling menyisip → baris bertukar urutan di file (bukti device: seq 59
     *  ditulis SETELAH seq 60 di server.ndjson owner). */
    private static final Object SEQLOCK = new Object();
    private static final ArrayDeque<Entry> GLOBAL = new ArrayDeque<>();
    @SuppressWarnings("unchecked")
    private static final ArrayDeque<Entry>[] PER_PORT = new ArrayDeque[6];
    private static final Map<Integer, Counter> COUNTERS = new LinkedHashMap<>();
    private static final AtomicLong SEQ = new AtomicLong();
    public static final long STARTED_AT = System.currentTimeMillis();
    /** status bind per port (diisi ServerCore) */
    public static final Map<String, String> BINDS = new LinkedHashMap<>();
    public static volatile boolean fallbackLogger = false;
    public static volatile String serverRootPath = "";
    public static volatile boolean extracted = false;
    public static volatile long extractedFiles = 0;
    /** v17 — status izin overlay (kandidat utama crash dialog auto-login SDK). */
    public static volatile boolean overlayChecked = false;
    public static volatile boolean overlayGranted = true;
    /** entri terbuang dari ring buffer (tidak hilang di disk — DiskWriter menulis semua) */
    public static volatile long droppedGlobal = 0;
    public static final long[] droppedPort = new long[6];

    static {
        for (int i = 0; i < PER_PORT.length; i++) PER_PORT[i] = new ArrayDeque<>();
        for (int p : new int[]{PORT_CONFIG, PORT_CDN, PORT_LOGIN, PORT_GAME, PORT_SDK, PORT_LOGGER})
            COUNTERS.put(p, new Counter());
    }

    private static int slot(int port) {
        switch (port) {
            case PORT_CONFIG: return 0;
            case PORT_CDN:    return 1;
            case PORT_LOGIN:  return 2;
            case PORT_SDK:    return 3;
            case PORT_LOGGER: return 4;
            default:          return 5;
        }
    }

    /** catat entri ke ring buffer (memori) + arsip NDJSON disk. Thread-safe. */
    public static void log(Entry e) {
        String json;
        // v23: seq + build JSON + enqueue DALAM SATU kunci — urutan seq ==
        // urutan tulis file (tumpukan kronologis benar-benar berurutan).
        synchronized (SEQLOCK) {
            e.seq = SEQ.incrementAndGet();
            if (e.ts == 0) e.ts = System.currentTimeMillis();
            json = e.toJson();
            DiskWriter w = DiskWriter.INSTANCE;
            if (w != null) w.enqueue(json);
        }
        Counter c = COUNTERS.get(e.port);
        synchronized (LOCK) {
            if (c != null) {
                c.requests++;
                c.bytesIn += e.reqLen;
                c.bytesOut += e.respLen;
                if (e.status >= 400 || e.error != null) c.errors++;
            }
            GLOBAL.addLast(e);
            while (GLOBAL.size() > GLOBAL_CAP) { GLOBAL.pollFirst(); droppedGlobal++; }
            ArrayDeque<Entry> q = PER_PORT[slot(e.port)];
            q.addLast(e);
            while (q.size() > PORT_CAP) { q.pollFirst(); droppedPort[slot(e.port)]++; }
        }
    }

    /**
     * counter saja TANPA menyimpan entri (dipakai listener logger agar traffic
     * dashboard/logger tidak membanjiri ring buffer alur game — Bagian 18).
     */
    public static void countSelf(int port, long reqLen, long respLen, boolean error) {
        Counter c = COUNTERS.get(port);
        if (c == null) return;
        synchronized (LOCK) {
            c.requests++;
            c.bytesIn += reqLen;
            c.bytesOut += respLen;
            if (error) c.errors++;
        }
    }

    private static ArrayList<Entry> tail(ArrayDeque<Entry> src, int limit, long since) {
        ArrayList<Entry> all = new ArrayList<>(src);
        int from = 0;
        if (since > 0) {
            // lewati entri dengan seq <= since (increment seq, cari batas dari kiri)
            int lo = 0, hi = all.size();
            while (lo < hi) { int mid = (lo + hi) >>> 1; if (all.get(mid).seq <= since) lo = mid + 1; else hi = mid; }
            from = lo;
        }
        if (limit > 0 && all.size() - from > limit) from = all.size() - limit;
        return new ArrayList<>(all.subList(from, all.size()));
    }

    /** seq terkecil yang MASIH ada di buffer global (untuk deteksi gap vs since client) */
    public static long oldestGlobalSeq() {
        synchronized (LOCK) {
            return GLOBAL.isEmpty() ? 0 : GLOBAL.peekFirst().seq;
        }
    }

    public static long oldestPortSeq(int port) {
        synchronized (LOCK) {
            ArrayDeque<Entry> q = PER_PORT[slot(port)];
            return q.isEmpty() ? 0 : q.peekFirst().seq;
        }
    }

    /** mode ALL — kronologis naik; limit<=0 = semua buffer; since>0 = hanya seq>since */
    public static ArrayList<Entry> latestGlobal(int limit, long since) {
        synchronized (LOCK) { return tail(GLOBAL, limit, since); }
    }

    /** mode PORT — kronologis naik; limit<=0 = semua buffer; since>0 = hanya seq>since */
    public static ArrayList<Entry> latestPort(int port, int limit, long since) {
        synchronized (LOCK) { return tail(PER_PORT[slot(port)], limit, since); }
    }

    /** kompatibilitas pemanggil lama */
    public static ArrayList<Entry> latestGlobal(int limit) { return latestGlobal(limit, 0); }
    public static ArrayList<Entry> latestPort(int port, int limit) { return latestPort(port, limit, 0); }

    public static long currentSeq() { return SEQ.get(); }

    public static String statsJson() {
        StringBuilder b = new StringBuilder(512);
        b.append("{\"startedAt\":").append(STARTED_AT)
         .append(",\"uptimeMs\":").append(System.currentTimeMillis() - STARTED_AT)
         .append(",\"seq\":").append(SEQ.get())
         .append(",\"droppedGlobal\":").append(droppedGlobal)
         .append(",\"droppedPort\":{");
        boolean first = true;
        for (int p : new int[]{PORT_CONFIG, PORT_CDN, PORT_LOGIN, PORT_GAME, PORT_SDK, PORT_LOGGER}) {
            if (!first) b.append(',');
            first = false;
            b.append('"').append(p).append("\":").append(droppedPort[slot(p)]);
        }
        b.append("},\"ports\":{");
        first = true;
        synchronized (LOCK) {
            for (Map.Entry<Integer, Counter> en : COUNTERS.entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(en.getKey()).append("\":").append(en.getValue().toJson());
            }
        }
        b.append("},\"disk\":").append(DiskWriter.statsJson())
         .append(",\"overlay\":{\"checked\":").append(overlayChecked)
         .append(",\"granted\":").append(overlayGranted).append('}')
         .append(",\"selfLogged\":false}");
        return b.toString();
    }

    public static String healthJson() {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"serverRoot\":\"").append(JsonUtil.esc(serverRootPath)).append('"')
         .append(",\"extracted\":").append(extracted)
         .append(",\"extractedFiles\":").append(extractedFiles)
         .append(",\"fallbackLogger\":").append(fallbackLogger)
         .append(",\"disk\":").append(DiskWriter.statsJson())
         .append(",\"binds\":{");
        boolean first = true;
        synchronized (LOCK) {
            for (Map.Entry<String, String> e : BINDS.entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(e.getKey()).append("\":\"").append(JsonUtil.esc(e.getValue())).append('"');
            }
        }
        b.append("}}");
        return b.toString();
    }
}
