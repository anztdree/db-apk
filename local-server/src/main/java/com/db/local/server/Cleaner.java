package com.db.local.server;

import java.io.File;

/**
 * Pembersih folder server (aturan README §2 — folder root WAJIB bersih).
 *
 * Android/data/<paket>/server/ hanya boleh berisi TEPAT 5 entri:
 *   config/  www/  logs/  tmp/  capture/
 * Apa pun di luar itu (peninggalan build lama, salinan APK, file hasil
 * analisis, dsb.) DIHAPUS otomatis setiap boot — SEBELUM server menyala.
 *
 * Di dalam config/ juga dijaga: server.json, status.json, www.stamp dan
 * DATABASE dragonball.db (+ file jurnal SQLite) — data akun/tamu/sesi kini
 * TAHAN REBOOT (dulu txt-warisan ikut terhapus karena tidak masuk whitelist).
 * Dengan begitu Android/data tidak pernah berisi sampah lagi, di perangkat
 * mana pun, tanpa langkah manual.
 *
 * 0.5.0: + capture/ — perekaman REKAM (rekam.jsonl + payload/ = config asli)
 * masuk whitelist: bukti protokol TIDAK boleh terhapus tiap boot.
 */
public final class Cleaner {

    /** Entri yang diizinkan di root folder server (0.5.0: + capture — perekaman REKAM). */
    private static final String[] ROOT_WHITELIST = {"config", "www", "logs", "tmp", "capture"};

    /** File yang diizinkan di dalam config/ (0.4.3: + DATABASE dragonball.db; 0.4.4: + servers.json). */
    private static final String[] CONFIG_WHITELIST = {"server.json", "status.json", "www.stamp",
            "servers.json",
            "dragonball.db", "dragonball.db-journal", "dragonball.db-wal", "dragonball.db-shm",
            "dragonball.db.corrupt"};

    private Cleaner() {
    }

    /** Hasil pembersihan untuk log. */
    public static final class Result {
        public int removed;
        public long bytes;
        public String names = "";
    }

    public static Result clean(File root) {
        Result r = new Result();
        try {
            if (root == null || !root.exists()) {
                return r;
            }
            File[] children = root.listFiles();
            if (children == null) {
                return r;
            }
            for (int i = 0; i < children.length; i++) {
                File c = children[i];
                if (!inList(ROOT_WHITELIST, c.getName())) {
                    long b = sizeOf(c);
                    if (deleteRc(c)) {
                        r.removed++;
                        r.bytes += b;
                        r.names = append(r.names, c.getName());
                    }
                }
            }
            File conf = new File(root, "config");
            File[] confChildren = conf.listFiles();
            if (confChildren != null) {
                for (int i = 0; i < confChildren.length; i++) {
                    File c = confChildren[i];
                    if (!inList(CONFIG_WHITELIST, c.getName())) {
                        long b = sizeOf(c);
                        if (deleteRc(c)) {
                            r.removed++;
                            r.bytes += b;
                            r.names = append(r.names, "config/" + c.getName());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            try {
                HumanLog.ev("⚠️", "Pembersih folder server menemui kendala — folder tetap dipakai apa adanya",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
        return r;
    }

    private static boolean inList(String[] list, String name) {
        for (int i = 0; i < list.length; i++) {
            if (list[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean deleteRc(File f) {
        try {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (int i = 0; i < kids.length; i++) {
                        deleteRc(kids[i]);
                    }
                }
            }
            return f.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    private static long sizeOf(File f) {
        try {
            if (f.isFile()) {
                return f.length();
            }
            long t = 0;
            File[] kids = f.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    t += sizeOf(kids[i]);
                }
            }
            return t;
        } catch (Throwable t2) {
            return 0;
        }
    }

    private static String append(String cur, String name) {
        String next = (cur == null || cur.length() == 0) ? name : cur + ", " + name;
        return next.length() > 220 ? next.substring(0, 220) + "…" : next;
    }
}
