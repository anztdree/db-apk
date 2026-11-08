package com.db.local.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simpanan status dua sisi (dipakai dasbor):
 * 1) server lokal — hidup/mati, uptime, jumlah permintaan, jumlah gagal, port, folder root
 * 2) server asli — hasil pantauan berkala per target (bisa/tidak dihubungi + latensi)
 */
public final class StatusStore {
    private static final Object LOCK = new Object();
    private static final String VERSION = "0.5.3";

    private static String bind = "127.0.0.1";
    private static int port = 4000;
    private static int gamePort = 8080;
    private static volatile boolean gameAlive = false;
    private static int loginPort = 8610;
    private static volatile boolean loginAlive = false;
    private static volatile int wwwFiles = 0;
    private static String mode = "online";
    private static String packageName = "com.db.local";
    private static String rootPath = "";
    private static String loginUserId = "";
    private static long startedAt = 0;
    private static volatile boolean serverAlive = false;
    private static final AtomicLong reqCount = new AtomicLong();
    private static final AtomicLong errCount = new AtomicLong();
    private static volatile long lastReqAt = 0;
    private static long tick = 0;
    private static File statusFile = null;
    private static volatile long wwwBytes = 0;

    /** Penghitung aktivitas mirror (0.4.5 — pengganti banjir baris log request). */
    private static final AtomicLong mirrorSeeded = new AtomicLong();
    private static final AtomicLong mirrorSeedBytes = new AtomicLong();
    private static final AtomicLong notFoundCount = new AtomicLong();

    /** Lima permintaan game terakhir — bukti "local running" di dasbor (tertua di depan). */
    private static final ArrayDeque<String[]> lastPaths = new ArrayDeque<String[]>();

    /** target -> {ok(1/0), note, latencyMs, lastCheck} */
    private static final LinkedHashMap<String, String[]> originals = new LinkedHashMap<String, String[]>();

    private StatusStore() {
    }

    static void configure(int debugP, int gameP, String m, String root, String pkg) {
        synchronized (LOCK) {
            port = debugP;
            gamePort = gameP;
            mode = m;
            rootPath = root;
            packageName = pkg;
        }
    }

    static void setLoginPort(int p) {
        synchronized (LOCK) {
            loginPort = p;
        }
    }

    static void setLoginUser(String uid) {
        synchronized (LOCK) {
            loginUserId = uid == null ? "" : uid;
        }
    }

    static void markLogin(boolean alive) {
        synchronized (LOCK) {
            loginAlive = alive;
        }
    }

    static void markGame(boolean alive) {
        synchronized (LOCK) {
            gameAlive = alive;
        }
    }

    /** Jumlah file mirror server asli yang terpasang di www/ (ekstraksi otomatis). */
    static void markWww(int files, long bytes) {
        synchronized (LOCK) {
            wwwFiles = files;
            wwwBytes = bytes;
        }
    }

    /** Catat permintaan port game (uri, status HTTP, lama proses) — maksimal 6 terakhir. */
    static void recordGame(String uri, int status, long ms) {
        synchronized (LOCK) {
            lastPaths.addLast(new String[]{uri, String.valueOf(status), String.valueOf(ms)});
            while (lastPaths.size() > 6) {
                lastPaths.removeFirst();
            }
        }
    }

    static void markStarted(long ms, boolean alive) {
        synchronized (LOCK) {
            startedAt = ms;
            serverAlive = alive;
        }
    }

    static void countReq() {
        reqCount.incrementAndGet();
        lastReqAt = System.currentTimeMillis();
    }

    static void countErr() {
        errCount.incrementAndGet();
    }

    /** File baru berhasil diambil dari server asli & disimpan ke www/ (mirror melengkapi diri). */
    static void addSeed(long bytes) {
        mirrorSeeded.incrementAndGet();
        mirrorSeedBytes.addAndGet(Math.max(0, bytes));
    }

    /** Permintaan dijawab 404 — normal bagi resource opsional, dihitung bukan dilog. */
    static void count404() {
        notFoundCount.incrementAndGet();
    }

    /** true setiap 25 permintaan — sinyal untuk menulis status.json ke disk. */
    static boolean requestTick() {
        synchronized (LOCK) {
            return (++tick % 25L) == 0L;
        }
    }

    static void putOriginal(String target, boolean ok, String note, long latencyMs) {
        synchronized (LOCK) {
            String last = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String[] row = originals.get(target);
            if (row == null) {
                originals.put(target, new String[]{ok ? "1" : "0", note, String.valueOf(latencyMs), last});
            } else {
                row[0] = ok ? "1" : "0";
                row[1] = note;
                row[2] = String.valueOf(latencyMs);
                row[3] = last;
            }
        }
    }

    static void setStatusFile(File f) {
        synchronized (LOCK) {
            statusFile = f;
        }
    }

    public static String toJson() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            long up = (serverAlive && startedAt > 0) ? (now - startedAt) : 0;
            StringBuilder sb = new StringBuilder(1024);
            sb.append("{\"server\":{")
                    .append(Json.bool("alive", serverAlive)).append(',')
                    .append(Json.num("port", port)).append(',')
                    .append(Json.str("bind", bind)).append(',')
                    .append(Json.str("mode", mode)).append(',')
                    .append(Json.num("gamePort", gamePort)).append(',')
                    .append(Json.bool("gameAlive", gameAlive)).append(',')
                    .append(Json.num("loginPort", loginPort)).append(',')
                    .append(Json.bool("loginAlive", loginAlive)).append(',')
                    .append(Json.num("loginAccounts", LoginData.accountCount())).append(',')
                    .append(Json.num("loginServers", LoginData.serverCount())).append(',')
                    .append(Json.str("loginUser", loginUserId)).append(',')
                    .append(Json.str("sdkUid", SdkData.lastKnownUid() == null ? "" : SdkData.lastKnownUid())).append(',')
                    .append(Json.str("sdkGuest", SdkData.primaryGuest() == null ? "" : SdkData.primaryGuest())).append(',')
                    .append(Json.num("sdkDevices", SdkData.guestDeviceCount())).append(',')
                    .append(Json.num("wwwFiles", wwwFiles)).append(',')
                    .append(Json.num("wwwBytes", wwwBytes)).append(',')
                    .append(Json.num("mirrorSeeded", mirrorSeeded.get())).append(',')
                    .append(Json.num("mirrorSeedBytes", mirrorSeedBytes.get())).append(',')
                    .append(Json.num("notFound", notFoundCount.get())).append(',')
                    .append(Json.bool("logTech", Config.logTech())).append(',')
                    .append(Json.bool("rekam", Config.rekam())).append(',')
                    .append(Json.num("rekamCount", Capture.recordedCount())).append(',')
                    .append(Json.str("package", packageName)).append(',')
                    .append(Json.str("version", VERSION)).append(',')
                    .append(Json.num("uptimeMs", up)).append(',')
                    .append(Json.num("requests", reqCount.get())).append(',')
                    .append(Json.num("errors", errCount.get())).append(',')
                    .append(Json.num("lastReqAt", lastReqAt)).append(',')
                    .append(Json.str("root", rootPath))
                    .append("},\"last\":[");
            boolean firstLast = true;
            Iterator<String[]> it = lastPaths.descendingIterator();
            while (it.hasNext()) {
                String[] p = it.next();
                if (!firstLast) {
                    sb.append(',');
                }
                firstLast = false;
                sb.append("{\"p\":\"").append(Json.esc(p[0])).append('\"')
                        .append(",\"s\":").append(p[1])
                        .append(",\"ms\":").append(p[2]).append('}');
            }
            sb.append("],\"original\":[");
            boolean first = true;
            for (Map.Entry<String, String[]> e : originals.entrySet()) {
                String[] r = e.getValue();
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"target\":\"").append(Json.esc(e.getKey())).append('"')
                        .append(",\"ok\":").append("1".equals(r[0]))
                        .append(",\"note\":\"").append(Json.esc(r[1])).append('"')
                        .append(",\"latencyMs\":").append(r[2])
                        .append(",\"last\":\"").append(Json.esc(r[3])).append("\"}");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    /** Tulis snapshot status.json di root folder server (alat bantu di luar dasbor). */
    static void persist() {
        File f;
        synchronized (LOCK) {
            f = statusFile;
        }
        if (f == null) {
            return;
        }
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
            w.write(toJson());
            w.close();
        } catch (Throwable ignored) {
        }
    }
}
