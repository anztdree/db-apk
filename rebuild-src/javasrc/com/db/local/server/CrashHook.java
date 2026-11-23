package com.db.local.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * v17 — PENANGKAP CRASH (jawaban "setelah sdk selesai LOADING force close").
 *
 * Diagnosis 23 Nov 2026: v16 pertama kali membuat /v1/system/init LOLOS
 * gerbang (v13-v15 SDK selalu mati di 30001) sehingga SDK maju ke jalur yang
 * belum pernah dieksekusi di device: sesi login tersimpan (prefs QGSp) ->
 * HWLoginActivity auto-login. Crash terjadi DI KODE SDK/GAME (classes.dex
 * asli — forbidden zone, tidak boleh disentuh) TANPA satu pun request HTTP
 * berikutnya, jadi logger HTTP tidak bisa melihatnya.
 *
 * Solusi: Thread.setDefaultUncaughtExceptionHandler — SEMUA crash Java di
 * seluruh proses (termasuk kode SDK/game) ditangkap SEBELUM proses mati:
 *  - laporan ditulis ke <serverRoot>/logs/crash-<waktu>.txt
 *    (thread, exception + FULL stack trace, 50 entri log terakhir sebagai
 *    konteks alur sebelum crash)
 *  - satu entri kind=CRASH masuk arsip NDJSON + ring buffer -> banner MERAH
 *    di dashboard 4000 dengan trace, tombol salin + unduh
 *  - handler bawaan Android tetap dipanggil (dialog crash sistem normal)
 * Murni java.* — aman di desktop smoke (instal + report bisa diuji).
 */
public final class CrashHook {

    private static volatile boolean installed = false;

    private CrashHook() {}

    /** Pasang hook sekali di boot. Tidak pernah throw. */
    public static void install() {
        if (installed) return;
        installed = true;
        try {
            final Thread.UncaughtExceptionHandler prev =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread t, Throwable e) {
                            try {
                                report(e, t);
                            } catch (Throwable ignored) {
                                // laporan gagal — jangan ganggu proses mati
                            }
                            if (prev != null) prev.uncaughtException(t, e);
                        }
                    });
            boot("CrashHook terpasang — crash Java seluruh proses terekam ke logs/crash-*.txt + banner dashboard");
        } catch (Throwable t) {
            boot("CrashHook gagal dipasang: " + t);
        }
    }

    /** Buat laporan crash (file + entri logger). Return nama file atau "". */
    public static String report(Throwable e, Thread t) {
        String name = "";
        try {
            File dir = new File(LoggerCore.serverRootPath, "logs");
            if (!dir.exists()) dir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            name = "crash-" + ts + ".txt";
            File f = new File(dir, name);
            StringBuilder b = new StringBuilder(8192);
            b.append("MOD-LOCALSERVER v17 — LAPORAN CRASH\n")
             .append("waktu : ").append(LoggerCore.Entry.fmtTime(System.currentTimeMillis())).append('\n')
             .append("thread: ").append(t == null ? "?" : t.getName()).append('\n')
             .append("root  : ").append(LoggerCore.serverRootPath).append('\n')
             .append('\n');
            if (e != null) {
                b.append("exception: ").append(e.getClass().getName()).append('\n');
                String msg = e.getMessage();
                b.append("message  : ").append(msg == null ? "(kosong)" : msg).append('\n');
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                pw.flush();
                b.append('\n').append("===== STACK TRACE =====\n").append(sw.toString());
            } else {
                b.append("exception: (null)\n");
            }
            // konteks: 50 entri log terakhir (alur apa yang berjalan sebelum crash)
            b.append('\n').append("===== 50 LOG TERAKHIR (seq waktu kind port status path) =====\n");
            try {
                ArrayList<LoggerCore.Entry> tail = LoggerCore.latestGlobal(50, 0);
                for (int i = 0; i < tail.size(); i++) {
                    LoggerCore.Entry en = tail.get(i);
                    b.append('#').append(en.seq).append(' ').append(en.time())
                     .append(' ').append(en.kind)
                     .append(' ').append(en.port)
                     .append(' ').append(en.status)
                     .append(' ').append(en.path == null ? "" : en.path)
                     .append('\n');
                }
            } catch (Throwable ignored) {}
            b.append('\n').append("AKHIR LAPORAN — kirim/unduh dari dashboard 4000 (banner merah CRASH)\n");

            byte[] data = b.toString().getBytes(StandardCharsets.UTF_8);
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(data);
                out.flush();
                out.getFD().sync();
            } finally {
                out.close();
            }

            // entri ke ring buffer + NDJSON — dashboard menampilkan banner
            LoggerCore.Entry e2 = new LoggerCore.Entry();
            e2.port = LoggerCore.PORT_LOGGER;
            e2.kind = "CRASH";
            e2.method = "CRASH";
            e2.path = name;
            e2.status = 0;
            e2.error = e == null ? "(null)" : e.getClass().getName() + ": " + e.getMessage();
            String trace = b.toString();
            e2.respPreview = trace.length() > 4000 ? trace.substring(0, 4000) : trace;
            e2.extra = "CRASH TERTANGKAP — file logs/" + name
                    + " (banner merah di dashboard berisi trace + tombol salin/unduh)";
            LoggerCore.log(e2);
            return name;
        } catch (Throwable tt) {
            try {
                LoggerCore.Entry e3 = new LoggerCore.Entry();
                e3.port = LoggerCore.PORT_LOGGER;
                e3.kind = "CRASH";
                e3.method = "CRASH";
                e3.path = "(laporan gagal)";
                e3.error = String.valueOf(tt);
                LoggerCore.log(e3);
            } catch (Throwable ignored) {}
            return "";
        }
    }

    private static void boot(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGGER;
        e.kind = "BOOT";
        e.method = "BOOT";
        e.path = msg;
        LoggerCore.log(e);
    }
}
