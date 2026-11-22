package com.db.local.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logger/monitoring 2 MODE (readme 15.6 / 16.6 — komponen BARU, bukan fakta APK asli):
 *  - mode ALL  : kronologis lintas port layanan (ring buffer global, cap 2000)
 *  - mode PORT : ring buffer per port (cap 400/port) — header, preview body req/resp
 *                (dibatasi 2048 byte), status, durasi, error; khusus 9527 menyertakan
 *                JSON config TER-DECODE pasca-XOR "DragonBall".
 * Event bus = in-memory singleton (satu proses — tanpa HTTP hop antar layanan).
 */
public final class LoggerCore {
    public static final int PORT_CONFIG = 9527;
    public static final int PORT_CDN    = 9528;
    public static final int PORT_LOGIN  = 5050;
    public static final int PORT_SDK    = 9530;
    public static final int PORT_LOGGER = 4000;
    public static final int PORT_FALLBACK = 4001;
    public static final int BODY_PREVIEW_MAX = 2048;
    public static final int GLOBAL_CAP = 2000;
    public static final int PORT_CAP = 400;

    public static final class Entry {
        public long seq;
        public long ts;
        public int port;
        public String method;
        public String path;
        public int status;
        public long durMs;
        public long reqLen;
        public long respLen;
        public String reqPreview;
        public String respPreview;
        public String extra;     // mis. JSON config ter-decode (9527) / event socket.io
        public String error;

        String esc(String s) { return s == null ? "" : JsonUtil.esc(s); }

        public String toJson() {
            StringBuilder b = new StringBuilder(512);
            b.append("{\"seq\":").append(seq)
             .append(",\"ts\":").append(ts)
             .append(",\"time\":\"").append(esc(fmtTime(ts)))
             .append("\",\"port\":").append(port)
             .append(",\"method\":\"").append(esc(method))
             .append("\",\"path\":\"").append(esc(path))
             .append("\",\"status\":").append(status)
             .append(",\"durMs\":").append(durMs)
             .append(",\"reqLen\":").append(reqLen)
             .append(",\"respLen\":").append(respLen);
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

    static {
        for (int i = 0; i < PER_PORT.length; i++) PER_PORT[i] = new ArrayDeque<>();
        for (int p : new int[]{PORT_CONFIG, PORT_CDN, PORT_LOGIN, PORT_SDK, PORT_LOGGER})
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

    public static void log(Entry e) {
        e.seq = SEQ.incrementAndGet();
        if (e.ts == 0) e.ts = System.currentTimeMillis();
        Counter c = COUNTERS.get(e.port);
        synchronized (LOCK) {
            if (c != null) {
                c.requests++;
                c.bytesIn += e.reqLen;
                c.bytesOut += e.respLen;
                if (e.status >= 400 || e.error != null) c.errors++;
            }
            GLOBAL.addLast(e);
            while (GLOBAL.size() > GLOBAL_CAP) GLOBAL.pollFirst();
            ArrayDeque<Entry> q = PER_PORT[slot(e.port)];
            q.addLast(e);
            while (q.size() > PORT_CAP) q.pollFirst();
        }
    }

    /** mode ALL — kronologis naik, entri N terakhir */
    public static ArrayList<Entry> latestGlobal(int limit) {
        synchronized (LOCK) {
            ArrayList<Entry> all = new ArrayList<>(GLOBAL);
            int from = Math.max(0, all.size() - Math.max(1, limit));
            return new ArrayList<>(all.subList(from, all.size()));
        }
    }

    /** mode PORT — kronologis naik, entri N terakhir untuk port tsb */
    public static ArrayList<Entry> latestPort(int port, int limit) {
        synchronized (LOCK) {
            ArrayList<Entry> all = new ArrayList<>(PER_PORT[slot(port)]);
            int from = Math.max(0, all.size() - Math.max(1, limit));
            return new ArrayList<>(all.subList(from, all.size()));
        }
    }

    public static String statsJson() {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"startedAt\":").append(STARTED_AT)
         .append(",\"uptimeMs\":").append(System.currentTimeMillis() - STARTED_AT)
         .append(",\"ports\":{");
        boolean first = true;
        synchronized (LOCK) {
            for (Map.Entry<Integer, Counter> en : COUNTERS.entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(en.getKey()).append("\":").append(en.getValue().toJson());
            }
        }
        b.append("}}");
        return b.toString();
    }

    public static String healthJson() {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"serverRoot\":\"").append(JsonUtil.esc(serverRootPath)).append('"')
         .append(",\"extracted\":").append(extracted)
         .append(",\"extractedFiles\":").append(extractedFiles)
         .append(",\"fallbackLogger\":").append(fallbackLogger)
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
