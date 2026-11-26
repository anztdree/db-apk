package com.db.local.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
 * v21 (Bagian 69) — PERBAIKAN "LOG GLOBAL DITERUSKAN" (arahan owner, kata-kata
 * persis: "log itu global.. di teruskan!! kalau jangan di pisahkan tiap waktu!!
 * gk efektif gk efisien!! kalau log bisa sampai ada 4 atau lebih tergantung
 * apk di buka!"):
 *   SALAH v18: semua riwayat exit sesi LAMA (sampai 5 — lintas hari!) ditulis
 *   ke log NDJSON per-open yang BARU dibuat — setiap buka APK, log baru dibuka
 *   dgn jejak 4-5 sesi lama → terasa "log global yang diteruskan", tidak
 *   efektif/efisien (bukti device v20: log-20261126-181501.ndjson seq 2-6
 *   memuat exit 18:14:59 / 18:12:35 / 18:11:48 / 18:11:03 hari itu + exit
 *   25 Nov — 5 sesi lain di dalam log satu open).
 *   BENAR v21:
 *   - Log per-open = MURNI sesi itu saja. NOL baris sesi lama.
 *   - Seluruh riwayat exit pindah ke FILE TERPISAH
 *     <serverRoot>/logs/exit-history.ndjson (append lintas sesi — inilah
 *     wadah "masa lalu", tidak pernah tercampur log open).
 *   - Log per-open hanya mendapat SATU baris BOOT ringkas (exit TERBARU saja
 *     + pointer ke file riwayat) supaya info "kenapa sesi kemarin mati" tidak
 *     hilang tanpa menimpa log.
 *   - File riwayat ditulis APPEND (java.io murni — minSdk 21), satu baris
 *     NDJSON per exit: loggedAt/reason/desc/exitTime/abnormal/trace.
 *
 * Dipanggil sekali di boot (LocalServerStarter).
 */
public final class ExitInfo {

    private ExitInfo() {}

    /** v21: nama file riwayat exit (relatif terhadap serverRoot) */
    public static final String HISTORY_FILE = "logs/exit-history.ndjson";

    /**
     * v21: tambahkan SATU baris NDJSON ke logs/exit-history.ndjson (append).
     * return "" bila sukses, else pesan error. public static agar smoke
     * desktop bisa mengujinya langsung.
     */
    public static String appendHistoryLine(String jsonLine) {
        try {
            String root = LoggerCore.serverRootPath;
            if (root == null || root.isEmpty()) return "serverRoot kosong";
            File logsDir = new File(root, "logs");
            if (!logsDir.exists()) logsDir.mkdirs();
            File f = new File(logsDir, "exit-history.ndjson");
            OutputStream out = new FileOutputStream(f, true);
            try {
                out.write((jsonLine + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                out.close();
            }
            return "";
        } catch (Throwable t) {
            return String.valueOf(t);
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
            for (int i = 0; i < n; i++) {
                Object info = list.get(i);
                int reason = (Integer) mReason.invoke(info);
                String desc = (String) mDesc.invoke(info);
                long ts = (Long) mTs.invoke(info);
                String rn = reasonName(reason);
                String timeStr = ts > 0 ? LoggerCore.Entry.fmtTime(ts) : "?";
                if (i == 0) { newestReason = rn; newestTime = timeStr; }
                String trace = "";
                if (mTrace != null) {
                    try {
                        Object st = mTrace.invoke(info);
                        if (st instanceof InputStream) trace = readCap((InputStream) st, 16384);
                    } catch (Throwable t) {
                        trace = "(trace unavailable: " + t.getClass().getSimpleName() + ")";
                    }
                }
                boolean bad = rn.indexOf("CRASH") >= 0 || "ANR".equals(rn)
                        || "LOW_MEMORY".equals(rn) || "SIGNALED".equals(rn);
                // v21: riwayat LENGKAP (semua exit + trace) → file terpisah,
                // TIDAK pernah ke log per-open
                StringBuilder jb = new StringBuilder(256);
                jb.append("{\"loggedAt\":\"").append(JsonUtil.esc(LoggerCore.Entry.fmtTime(
                                System.currentTimeMillis())))
                 .append("\",\"reason\":\"").append(JsonUtil.esc(rn))
                 .append("\",\"desc\":\"").append(JsonUtil.esc(desc == null ? "" : desc))
                 .append("\",\"exitTime\":\"").append(JsonUtil.esc(timeStr))
                 .append("\",\"abnormal\":").append(bad)
                 .append(",\"trace\":\"").append(JsonUtil.esc(trace))
                 .append("\"}");
                String err = appendHistoryLine(jb.toString());
                if (err.length() > 0) {
                    bootLine("exit-history gagal ditulis: " + err);
                }
            }
            // v21: SATU baris BOOT ringkas di log per-open — exit TERBARU
            // (index 0 = paling baru) + pointer file riwayat. Bukan lagi
            // backlog 5 sesi lama.
            bootLine("sesi sebelumnya berakhir: reason=" + newestReason
                    + " time=" + newestTime
                    + " — riwayat lengkap (" + n + " exit): " + HISTORY_FILE);
        } catch (Throwable t) {
            bootLine("unavailable on this device: " + t);
        }
    }

    /** v21: satu baris BOOT di log per-open (bukan lagi kind SYS/EXITINFO) */
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
