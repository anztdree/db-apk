package com.db.local.server;

import java.io.InputStream;

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
 * Dipanggil sekali di boot (LocalServerStarter) — hasil masuk log SYS/EXITINFO:
 * di dashboard terlihat APA YANG MEMATIKAN SESI SEBELUMNYA tanpa adb.
 */
public final class ExitInfo {

    private ExitInfo() {}

    public static void logPrevious(android.content.Context ctx) {
        try {
            if (ctx == null) {
                entry("desktop — dilewati (fitur Android 11+)");
                return;
            }
            Object am = ctx.getSystemService("activity");
            if (am == null) { entry("ActivityManager tidak tersedia"); return; }
            java.lang.reflect.Method m = am.getClass().getMethod(
                    "getHistoricalProcessExitReasons", String.class, int.class, int.class);
            Object raw = m.invoke(am, ctx.getPackageName(), 0, 5);
            if (!(raw instanceof java.util.List)) { entry("format tidak dikenal"); return; }
            java.util.List<?> list = (java.util.List<?>) raw;
            if (list.isEmpty()) {
                entry("tidak ada riwayat exit (normal utk install baru)");
                return;
            }
            Class<?> clsExit = Class.forName("android.app.ApplicationExitInfo");
            java.lang.reflect.Method mReason = clsExit.getMethod("getReason");
            java.lang.reflect.Method mDesc = clsExit.getMethod("getDescription");
            java.lang.reflect.Method mTs = clsExit.getMethod("getTimestamp");
            java.lang.reflect.Method mTrace = null;
            try { mTrace = clsExit.getMethod("getTraceInputStream"); } catch (Throwable ignore) {}

            int n = list.size();
            for (int i = 0; i < n; i++) {
                Object info = list.get(i);
                int reason = (Integer) mReason.invoke(info);
                String desc = (String) mDesc.invoke(info);
                long ts = (Long) mTs.invoke(info);
                String rn = reasonName(reason);
                String trace = "";
                if (mTrace != null) {
                    try {
                        Object st = mTrace.invoke(info);
                        if (st instanceof InputStream) trace = readCap((InputStream) st, 16384);
                    } catch (Throwable t) {
                        trace = "(trace tidak tersedia: " + t.getClass().getSimpleName() + ")";
                    }
                }
                boolean bad = rn.indexOf("CRASH") >= 0 || "ANR".equals(rn)
                        || "LOW_MEMORY".equals(rn) || "SIGNALED".equals(rn);
                LoggerCore.Entry e = new LoggerCore.Entry();
                e.port = LoggerCore.PORT_LOGGER;
                e.kind = "SYS";
                e.method = "EXITINFO";
                e.path = "exit lalu[" + (n - 1 - i) + "/" + n + "] reason=" + rn
                        + (desc == null || desc.length() == 0 ? "" : " desc=" + desc)
                        + " waktu=" + (ts > 0 ? LoggerCore.Entry.fmtTime(ts) : "?")
                        + (bad ? "  <-- SESI SEBELUMNYA MATI ABNORMAL" : "");
                e.status = bad ? 500 : 0;
                if (trace.length() > 0) e.respPreview = trace;
                LoggerCore.log(e);
            }
        } catch (Throwable t) {
            entry("tidak tersedia di device ini: " + t);
        }
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
                if (tot >= cap) { b.write("\n…[trace dipotong 16KB]".getBytes("UTF-8")); break; }
            }
            in.close();
            return b.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static void entry(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGGER;
        e.kind = "SYS";
        e.method = "EXITINFO";
        e.path = msg;
        e.status = 0;
        LoggerCore.log(e);
    }
}
