package com.db.local.server;

/**
 * 0.5.7 — PENANGKAP FORCE CLOSE (laporan owner: "force close setelah muncul
 * serverlist" tanpa petunjuk sebab — tidak ada yang bisa dibedah).
 *
 * Setiap aplikasi Android punya SATU default uncaught exception handler;
 * bila thread mana pun throw tanpa tertangkap, handler itu dipanggil lalu
 * proses mati (dialog "aplikasi berhenti"). Handler bawaan TIDAK menulis
 * apa pun ke disk yang mudah dibaca.
 *
 * CrashGuard memasang handler yang:
 *   1) menulis bukti lengkap ke logs/crash.log  (waktu · thread · stack ·
 *      penyebab berantai — tanpa dipangkas);
 *   2) LALU mendelegasikan ke handler asli — perilaku force close standar
 *      tidak diubah (dialog tetap muncul, proses tetap mati) — CrashGuard
 *      TIDAK menyembunyikan crash, hanya MENINGGALKAN BUKTI.
 *
 * Bukti dibaca SAAT BOOT BERIKUTNYA (Starter) dan tampil sebagai baris ⚠️
 * di dasbor :4000 — pemilik melihat sebab force close tanpa PC/logcat.
 */
public final class CrashGuard {
    private CrashGuard() {
    }

    /** Pasang penjaga — dipanggil SATU kali paling awal di Starter.boot(). */
    public static void install() {
        try {
            final Thread.UncaughtExceptionHandler asli = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    try {
                        writeCrash(t, e);
                    } catch (Throwable ignored) {
                    }
                    if (asli != null) {
                        try {
                            asli.uncaughtException(t, e); // perilaku standar (dialog + proses mati)
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Tulis bukti crash ke logs/crash.log (atau tmp saat logDir belum siap). */
    private static void writeCrash(Thread t, Throwable e) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("=== CRASH ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date())).append(" ===\n");
        sb.append("Thread  : ").append(t == null ? "?" : t.getName())
                .append(" (id ").append(t == null ? -1 : t.getId()).append(")\n");
        sb.append("Jenis   : ").append(e == null ? "?" : e.getClass().getName()).append('\n');
        sb.append("Pesan   : ").append(e == null ? "-" : e.getMessage()).append('\n');
        sb.append("Stack   :\n");
        appendStack(sb, e);
        // penyebab berantai (caused by) — TANPA dipangkas:
        Throwable c = (e == null) ? null : e.getCause();
        int guard = 0;
        while (c != null && guard < 12) {
            sb.append("Caused by: ").append(c.getClass().getName())
                    .append(": ").append(c.getMessage()).append('\n');
            appendStack(sb, c);
            c = c.getCause();
            guard++;
        }
        sb.append("--- akhir crash ---\n");
        String text = sb.toString();
        // Lokasi: folder logs yang sudah di-init; bila belum, folder tmp proses.
        File2.writeAppend(
                (HumanLog.dir() != null) ? new java.io.File(HumanLog.dir(), "crash.log")
                        : new java.io.File(Starter.tmpDir(), "crash.log"),
                text);
    }

    private static void appendStack(StringBuilder sb, Throwable e) {
        StackTraceElement[] st = e.getStackTrace();
        int n = (st == null) ? 0 : st.length;
        for (int i = 0; i < n; i++) {
            sb.append("    at ").append(st[i].toString()).append('\n');
        }
        if (n == 0) {
            sb.append("    (stack kosong)\n");
        }
    }

    /** Baca bukti crash (dipakai Starter saat boot berikutnya). */
    public static String readAll(java.io.File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(
                    (int) Math.max(64, Math.min(f.length(), 512L * 1024L)));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            String s = new String(bos.toByteArray(), "UTF-8");
            if (s.length() > 16 * 1024) {
                // ambil crash TERAKHIR (file bisa memuat beberapa crash) — batas jujur
                int a = s.lastIndexOf("=== CRASH ");
                if (a > 0) {
                    s = "(crash sebelumnya ada di file " + f.getName() + ".read)\n" + s.substring(a);
                }
                if (s.length() > 16 * 1024) {
                    s = s.substring(0, 16 * 1024) + "\n… (potongan pertama 16 KB)";
                }
            }
            return s;
        } catch (Throwable t) {
            return "(bukti crash tidak bisa dibaca: " + t + ")";
        }
    }

    /** Util tulis-append kecil (terpisah agar tanpa ketergantungan HumanLog). */
    private static final class File2 {
        static void writeAppend(java.io.File f, String text) {
            try {
                java.io.File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                java.io.Writer w = new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(f, true), "UTF-8");
                w.write(text);
                w.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
