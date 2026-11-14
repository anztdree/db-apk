package com.db.local.server;

import java.io.File;

/**
 * Pembersih folder server (aturan README §2 — folder root WAJIB bersih).
 *
 * 0.0.3 — STRUKTUR FOLDER JELAS (keluhan owner: "berantakan juga struktur
 * folder pembacaan file di folder server Android/data.. struktur folder local
 * di android/data harus juga jelas"). Root Android/data/<paket>/server/ hanya
 * boleh berisi TEPAT entri berikut — masing-masing SATU makna (peta lengkap
 * ada di PETA.txt yang ikut dijaga):
 *
 *   PETA.txt          peta folder (file — dibuat server)
 *   config/           PENGATURAN (server.json, game-upstream.txt)
 *   db/               DATA PEMAIN (dragonball.db + jurnal SQLite)
 *   state/            keadaan runtime (status.json, www.stamp)
 *   www/              mirror statis game
 *   config-offline/   paket MANUAL config klien
 *   logs/             human.log · events.jsonl · crash.log
 *   tmp/              kerja sementara
 *
 * Yang berubah dari 0.0.2 (bukti audit Paths.java):
 *   - capture/ DIHAPUS dari whitelist — REKAM mati total sejak 0.5.4; sisa
 *     folder capture/ di perangkat kini disapu otomatis (dulu "dihapus manual
 *     oleh pemilik" = kontradiksi anti-sampah, sisa menetap diam-diam).
 *   - config/servers.json (snapshot mati era 0.4.4) DIHAPUS dari whitelist —
 *     serverlist dibaca HANYA dari paket manual config-offline/ sejak 0.5.3
 *     (harness 18a membuktikan snapshot config/ tidak dibaca lagi); dua
 *     servers.json di dua folder = sumber bingung owner.
 *   - dragonball.db TIDAK lagi di config/ — kini db/dragonball.db (migrasi
 *     otomatis Paths.migrateFromLegacyLayout() sebelum pembersih).
 *   - status.json + www.stamp pindah ke state/ (config/ murni pengaturan).
 *
 * Setiap folder terjaga whitelist-NYA sendiri; apa pun di luar DIHAPUS
 * otomatis tiap boot SEBELUM server menyala (jumlah + ukuran + nama tercatat
 * di log — tidak ada hapus diam-diam).
 */
public final class Cleaner {

    /** Entri diizinkan di root folder server (0.0.3 — struktur jelas, +PETA.txt, +db/, +state/; -capture/ mati). */
    private static final String[] ROOT_WHITELIST = {
            Paths.PETA_NAME,
            "config", "db", "state", "www", "config-offline", "logs", "tmp"
    };

    /** File diizinkan di config/ — MURNI pengaturan (0.0.3: dragonball.db/status.json/www.stamp pindah; servers.json mati dihapus). */
    private static final String[] CONFIG_WHITELIST = {"server.json", "game-upstream.txt"};

    /** File diizinkan di db/ — DATA pemain + jurnal SQLite (0.0.3 pindah dari config/). */
    private static final String[] DB_WHITELIST = {
            "dragonball.db", "dragonball.db-journal", "dragonball.db-wal", "dragonball.db-shm",
            "dragonball.db.corrupt"
    };

    /** File diizinkan di state/ — keadaan runtime yang dibuat ulang otomatis. */
    private static final String[] STATE_WHITELIST = {"status.json", "www.stamp"};

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
            sweep(root, ROOT_WHITELIST, "", r);
            // 0.0.3 — sub-folder yang dijaga whitelist isinya sendiri:
            sweep(new File(root, "config"), CONFIG_WHITELIST, "config/", r);
            sweep(new File(root, "db"), DB_WHITELIST, "db/", r);
            sweep(new File(root, "state"), STATE_WHITELIST, "state/", r);
        } catch (Throwable t) {
            try {
                HumanLog.ev("⚠️", "Pembersih folder server menemui kendala — folder tetap dipakai apa adanya",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
        return r;
    }

    /** Hapus entri di folder yang tidak masuk whitelist; prefix utk nama di log. */
    private static void sweep(File dir, String[] whitelist, String prefix, Result r) {
        try {
            File[] children = dir.listFiles();
            if (children == null) {
                return;
            }
            for (int i = 0; i < children.length; i++) {
                File c = children[i];
                if (!inList(whitelist, c.getName())) {
                    long b = sizeOf(c);
                    if (deleteRc(c)) {
                        r.removed++;
                        r.bytes += b;
                        r.names = append(r.names, prefix + c.getName());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
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
