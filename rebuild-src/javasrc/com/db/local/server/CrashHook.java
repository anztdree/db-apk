package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * v18 — PENANGKAP CRASH (jawaban "setelah sdk selesai LOADING force close").
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
 *
 * v18 tambahan:
 *  - Wrapper = kelas bernama + watchdog 10 dtk: bila SDK/crash-reporter lain
 *    memasang handler sendiri SETELAH boot, kita bungkus ULANG agar crash
 *    tetap terekam (chain tetap utuh — handler SDK tetap dipanggil).
 *  - unhandledCrashFiles()/markCrashHandled(): buku besar logs/crash-handled.list
 *    untuk SessionReset.autoGuard — reset sesi HANYA sekali per crash baru.
 */
public final class CrashHook {

    private static volatile boolean installed = false;

    private CrashHook() {}

    /** v18 — wrapper bernama (instansi kita mudah dikenali watchdog). */
    static final class Wrapper implements Thread.UncaughtExceptionHandler {
        final Thread.UncaughtExceptionHandler inner;
        Wrapper(Thread.UncaughtExceptionHandler inner) { this.inner = inner; }
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            try {
                report(e, t);
            } catch (Throwable ignored) {}
            if (inner != null) {
                try { inner.uncaughtException(t, e); } catch (Throwable ignored2) {}
            }
        }
    }

    /** Pasang hook sekali di boot + watchdog anti-timpa. Tidak pernah throw. */
    public static void install() {
        if (installed) return;
        installed = true;
        try {
            final Thread.UncaughtExceptionHandler prev =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Wrapper(prev));
            boot("CrashHook terpasang — crash Java seluruh proses terekam ke logs/crash-*.txt + banner dashboard");
            // v18: watchdog — bila SDK memasang handler sendiri nanti, bungkus ulang
            Thread wd = new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        try { Thread.sleep(10000); } catch (InterruptedException ie) { return; }
                        try {
                            Thread.UncaughtExceptionHandler cur =
                                    Thread.getDefaultUncaughtExceptionHandler();
                            if (!(cur instanceof Wrapper)) {
                                Thread.setDefaultUncaughtExceptionHandler(new Wrapper(cur));
                                sys("CrashHook re-wrap: handler default diganti pihak lain "
                                        + "(crash-reporter SDK?) — dibungkus ulang, crash tetap terekam");
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }, "crashhook-watchdog");
            wd.setDaemon(true);
            wd.start();
        } catch (Throwable t) {
            boot("CrashHook gagal dipasang: " + t);
        }
    }

    /* ============ v18 — buku besar crash belum ditangani (untuk autoGuard) ============ */

    /** daftar crash-*.txt yang BELUM ditangani (tidak ada di crash-handled.list). */
    public static java.util.ArrayList<String> unhandledCrashFiles() {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        try {
            File dir = new File(LoggerCore.serverRootPath, "logs");
            String[] names = dir.list();
            if (names == null) return out;
            java.util.HashSet<String> handled = handledSet();
            for (int i = 0; i < names.length; i++) {
                String n = names[i];
                if (n.startsWith("crash-") && n.endsWith(".txt") && !handled.contains(n))
                    out.add(n);
            }
            java.util.Collections.sort(out);
        } catch (Throwable ignored) {}
        return out;
    }

    /** tandai laporan sudah ditangani (autoGuard) — tidak dihapus, arsip tetap. */
    public static void markCrashHandled(java.util.List<String> names) {
        if (names == null || names.isEmpty()) return;
        try {
            File f = new File(new File(LoggerCore.serverRootPath, "logs"), "crash-handled.list");
            StringBuilder b = new StringBuilder();
            File old = f;
            if (old.exists()) b.append(readFile(old));
            for (int i = 0; i < names.size(); i++)
                b.append(names.get(i)).append('\n');
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(b.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {}
    }

    private static java.util.HashSet<String> handledSet() {
        java.util.HashSet<String> s = new java.util.HashSet<String>();
        try {
            File f = new File(new File(LoggerCore.serverRootPath, "logs"), "crash-handled.list");
            if (!f.exists()) return s;
            String c = readFile(f);
            if (c == null) return s;
            String[] parts = c.split("\\n");
            for (int i = 0; i < parts.length; i++) {
                String t = parts[i].trim();
                if (t.length() > 0) s.add(t);
            }
        } catch (Throwable ignored) {}
        return s;
    }

    private static String readFile(File f) {
        FileInputStream in = null;
        try {
            byte[] buf = new byte[(int) f.length()];
            in = new FileInputStream(f);
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
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
            b.append("MOD-LOCALSERVER v1 — LAPORAN CRASH\n")
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

    private static void sys(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGGER;
        e.kind = "SYS";
        e.method = "CRASHHOOK";
        e.path = msg;
        LoggerCore.log(e);
    }
}
