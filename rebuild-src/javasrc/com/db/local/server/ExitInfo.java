package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * v18 — LAPORAN AKHIR SESI SEBELUMNYA (ApplicationExitInfo, API 30+).
 *
 * Melengkapi CrashHook (yang hanya menangkap crash JAVA di proses):
 * ApplicationExitInfo mencatat SEMUA cara proses mati sesi lalu — CRASH,
 * CRASH_NATIVE (SIGSEGV di lib native game), ANR, LOW_MEMORY, kill — beserta
 * trace bila tersedia (getTraceInputStream, API 31 = Android 12, device
 * owner itel S665L osVersion=31). Dibaca via REFLEKSI murni agar lolos
 * kompilasi stub (android.jar API21 tidak punya kelas ini).
 *
 * v21 (Bagian 69) — riwayat exit dipisah ke logs/exit-history.ndjson.
 *
 * v22 (Bagian 70) — LOG = 1 FILE DI TUMPUK (arahan owner, kata-kata persis:
 * "LOG ITU 1 FILE DI TUMPUK !! BUKAN MALAH BIKIN BARU TIAP MASUK APLIKASI!!"):
 *   - FILE TERPISAH exit-history.ndjson DIHAPUS dari desain — semua masuk
 *     SATU log global server.ndjson (DiskWriter v22).
 *   - Tiap exit sesi sebelumnya = SATU entri kind=EXIT di log global, APPEND
 *     tepat sebelum BOOT sesi baru → kronologis alami di satu file:
 *     …baris sesi lama… → EXIT (kenapa mati) → BOOT sesi baru → …baris baru…
 *   - DEDUP: ApplicationExitInfo menyimpan sampai 5 exit terakhir dan daftar
 *     itu tetap ada di boot berikutnya — exit yang SUDAH pernah dicatat
 *     (dicocokkan exitTime: dilihat di memori sesi ini + ekor file log)
 *     TIDAK ditulis ulang → tiap exit muncul TEPAT SEKALI dalam sejarah log.
 *   - LOG NATURAL (arahan owner: "KALAU ERROR YA ERROR ! KALAU KURANG YAA
 *     KURANG !"): exit abnormal (CRASH/ANR/LOW_MEMORY/SIGNALED) membawa field
 *     error "abnormal exit: <reason>" → otomatis level=ERROR di NDJSON; exit
 *     normal (EXIT_SELF/USER_REQUESTED) tanpa error = level INFO.
 *   - BOOT ringkas tetap 1 baris (batas sesi): "sesi sebelumnya berakhir:
 *     reason=… time=…" — TANPA pointer file terpisah (tidak ada lagi).
 *
 * Dipanggil sekali di boot (LocalServerStarter).
 */
public final class ExitInfo {

    private ExitInfo() {}

    /** v22: exitTime yang sudah pernah dicatat SESI INI (anti duplikat) */
    private static final java.util.HashSet<String> SEEN_EXIT_TIMES =
            new java.util.HashSet<String>();

    /**
     * v22: tulis SATU entri kind=EXIT ke log global server.ndjson (append).
     * Dedup: exitTime yang sudah pernah tercatat (memori sesi ini + ekor file
     * log) TIDAK ditulis ulang → return "dup". return "" bila ditulis, else
     * pesan error. public static agar smoke desktop bisa mengujinya langsung.
     */
    public static String appendExitEntry(String reason, String desc, long exitTs, String trace) {
        try {
            String timeStr = exitTs > 0 ? LoggerCore.Entry.fmtTime(exitTs) : "?";
            boolean bad = reason != null && (reason.indexOf("CRASH") >= 0
                    || "ANR".equals(reason) || "LOW_MEMORY".equals(reason)
                    || "SIGNALED".equals(reason));
            // dedup hanya utk exitTime yang JELAS; timeStr "?" (ts tak diketahui)
            // selalu ditulis (tidak mungkin dicocokkan)
            if (!"?".equals(timeStr) && alreadyLogged(timeStr)) return "dup";
            LoggerCore.Entry e = new LoggerCore.Entry();
            e.port = LoggerCore.PORT_LOGGER;
            e.kind = "EXIT";
            e.method = "EXITINFO";
            e.path = "sesi sebelumnya berakhir: reason=" + reason + " time=" + timeStr;
            e.extra = "desc=" + (desc == null ? "" : desc) + " abnormal=" + bad;
            if (bad) e.error = "abnormal exit: " + reason;   // LOG NATURAL — ERROR ya ERROR
            if (trace != null && trace.length() > 0) {
                e.respPreview = trace.length() > 4000
                        ? trace.substring(0, 4000) + "…[trace cut at 16KB]"
                        : trace;
            }
            LoggerCore.log(e);
            SEEN_EXIT_TIMES.add(timeStr);
            return "";
        } catch (Throwable t) {
            return String.valueOf(t);
        }
    }

    /**
     * v22: cek exitTime sudah pernah dicatat? Sumber: (1) memori sesi ini,
     * (2) ekor file log global (256 KB terakhir — cukup utk boot-boot terakhir;
     * kalau log dihapus owner, exit lama boleh tercatat lagi — wajar).
     * Murni java.io (minSdk 21).
     */
    private static boolean alreadyLogged(String exitTime) {
        if (SEEN_EXIT_TIMES.contains(exitTime)) return true;
        try {
            String root = LoggerCore.serverRootPath;
            if (root == null || root.isEmpty()) return false;
            File f = new File(new File(root, "logs"), DiskWriter.SERVER_LOG);
            if (!f.isFile()) return false;
            long len = f.length();
            int cap = 262144;
            long skip = Math.max(0, len - cap);
            FileInputStream in = new FileInputStream(f);
            try {
                long s = 0;
                while (s < skip) {
                    long r = in.skip(skip - s);
                    if (r <= 0) break;
                    s += r;
                }
                byte[] buf = new byte[(int) (len - skip)];
                int off = 0;
                while (off < buf.length) {
                    int r = in.read(buf, off, buf.length - off);
                    if (r < 0) break;
                    off += r;
                }
                String tail = new String(buf, 0, off, StandardCharsets.UTF_8);
                String needle = "\"exitTime\":\"" + exitTime + "\"";
                if (tail.indexOf(needle) < 0) return false;
                // needle ada — pastikan barisnya memang entri EXIT (bukan kebetulan)
                for (String ln : tail.split("\n")) {
                    if (ln.indexOf("\"kind\":\"EXIT\"") >= 0 && ln.indexOf(needle) >= 0)
                        return true;
                }
                return false;
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    public static void logPrevious(android.content.Context ctx) {
        try {
            if (ctx == null) {
                bootLine("desktop — skipped (Android 11+ feature)");
                return;
            }
            Object am = ctx.getSystemService("activity");
            if (am == null) { bootLine("ActivityManager unavailable"); return; }
            java.lang.reflect.Method m = am.getClass().getMethod(
                    "getHistoricalProcessExitReasons", String.class, int.class, int.class);
            Object raw = m.invoke(am, ctx.getPackageName(), 0, 5);
            if (!(raw instanceof java.util.List)) { bootLine("unknown format"); return; }
            java.util.List<?> list = (java.util.List<?>) raw;
            if (list.isEmpty()) {
                bootLine("no exit history (normal for a fresh install)");
                return;
            }
            Class<?> clsExit = Class.forName("android.app.ApplicationExitInfo");
            java.lang.reflect.Method mReason = clsExit.getMethod("getReason");
            java.lang.reflect.Method mDesc = clsExit.getMethod("getDescription");
            java.lang.reflect.Method mTs = clsExit.getMethod("getTimestamp");
            java.lang.reflect.Method mTrace = null;
            try { mTrace = clsExit.getMethod("getTraceInputStream"); } catch (Throwable ignore) {}

            int n = list.size();
            String newestReason = null, newestTime = null;
            int logged = 0;
            for (int i = 0; i < n; i++) {
                Object info = list.get(i);
                int reason = (Integer) mReason.invoke(info);
                String desc = (String) mDesc.invoke(info);
                long ts = (Long) mTs.invoke(info);
                String rn = reasonName(reason);
                if (i == 0) {
                    newestReason = rn;
                    newestTime = ts > 0 ? LoggerCore.Entry.fmtTime(ts) : "?";
                }
                String trace = "";
                if (mTrace != null) {
                    try {
                        Object st = mTrace.invoke(info);
                        if (st instanceof InputStream) trace = readCap((InputStream) st, 16384);
                    } catch (Throwable t) {
                        trace = "(trace unavailable: " + t.getClass().getSimpleName() + ")";
                    }
                }
                // v22: entri EXIT ke LOG GLOBAL (server.ndjson) — dedup exitTime;
                // exit yang sudah pernah tercatat di sesi/boot sebelumnya tidak
                // ditulis ulang (tiap exit muncul TEPAT SEKALI dalam sejarah log)
                String err = appendExitEntry(rn, desc == null ? "" : desc, ts, trace);
                if (err.length() == 0) logged++;
                else if (!"dup".equals(err)) {
                    bootLine("entri EXIT gagal ditulis: " + err);
                }
            }
            // v22: SATU baris BOOT ringkas sebagai batas sesi — tanpa pointer
            // file riwayat (tidak ada lagi file terpisah; semuanya di log ini)
            bootLine("sesi sebelumnya berakhir: reason=" + newestReason
                    + " time=" + newestTime
                    + " (" + logged + " exit baru tercatat, total " + n + ")");
        } catch (Throwable t) {
            bootLine("unavailable on this device: " + t);
        }
    }

    /** v22: satu baris BOOT di log global (batas sesi) */
    private static void bootLine(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGGER;
        e.kind = "BOOT";
        e.method = "BOOT";
        e.path = msg;
        e.status = 0;
        LoggerCore.log(e);
    }

    static String reasonName(int r) {
        switch (r) {
            case 0:  return "UNKNOWN";
            case 1:  return "EXIT_SELF";
            case 2:  return "SIGNALED";
            case 3:  return "LOW_MEMORY";
            case 4:  return "CRASH";
            case 5:  return "CRASH_NATIVE";
            case 6:  return "ANR";
            case 7:  return "INITIALIZATION_FAILURE";
            case 8:  return "OTHER";
            case 9:  return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED";
            case 11: return "USER_STOPPED";
            case 12: return "CONNECTION_INIT_TIMEOUT";
            default: return "REASON_" + r;
        }
    }

    private static String readCap(InputStream in, int cap) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r, tot = 0;
            while ((r = in.read(buf)) > 0) {
                b.write(buf, 0, r);
                tot += r;
                if (tot >= cap) { b.write("\n…[trace cut at 16KB]".getBytes("UTF-8")); break; }
            }
            in.close();
            return b.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
