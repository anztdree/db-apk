package com.db.local.server;

import java.io.File;

/**
 * Pembersih folder server (aturan README §2 — folder root WAJIB bersih).
 *
 * 0.0.4 — STRUKTUR FOLDER LAYANAN (arahan owner 2026-11-15: "server json bisa
 * di gantikah jadi .env . lalu SDK-SERVER, LOGIN-SERVER, MAIN-SERVER,
 * CHAT-SERVER, DUNGEON-SERVER dan LOG !!  nama nya jelas" + kendala sebelumnya
 * "banyak folder sampah yg gak jelas.. PETA.TXT GK PENTING.. TMP GK PENTING").
 *
 * Root Android/data/&lt;paket&gt;/server/ hanya boleh berisi TEPAT entri berikut
 * — 3 file + 6 folder, SEMUA bernama jelas (nama folder = nama layanannya):
 *
 *   .env              PENGATURAN (semua seting server satu file)
 *   dragonball.db     DATA PEMAIN (+ -journal/-wal/-shm/.corrupt saat SQLite aktif)
 *   status.json       keadaan server saat ini (dibuat ulang otomatis)
 *   SDK-SERVER/       milik SDK :8610
 *   LOGIN-SERVER/     milik LOGIN :8611
 *   MAIN-SERVER/      milik MAIN :8080 (bs/ + config-offline/ + www.stamp)
 *   CHAT-SERVER/      milik CHAT :8612 (siap — layanan menyusul)
 *   DUNGEON-SERVER/   milik DUNGEON :8613 (siap — layanan menyusul)
 *   LOG/              human.log · events.jsonl · crash.log
 *
 * Yang berubah dari 0.0.3 (kendala owner + isi pindah ke folder layanan):
 *   - PETA.txt TIDAK dilindungi lagi (owner: "PETA.TXT GK PENTING") — sisa di
 *     perangkat disapu otomatis dengan nama+ukuran di log.
 *   - tmp/ TIDAK ada lagi (owner: "TMP GK PENTING") — kerja sementara pindah ke
 *     cache internal aplikasi (di luar folder server — tak terlihat, tak sampah).
 *   - config/ db/ state/ www/ config-offline/ logs/ HILANG dari root — isi
 *     sudah pindah (Paths.migrateToServiceLayout): config/server.json +
 *     game-upstream.txt → NILAINYA di .env; db/dragonball.db → root;
 *     state/status.json → root; www/bs → MAIN-SERVER/bs; config-offline →
 *     MAIN-SERVER/config-offline; logs → LOG. Folder lama kosong disapu.
 *   - capture/ tetap DIHAPUS (REKAM mati total 0.5.4).
 *
 * Setiap folder terjaga whitelist-NYA sendiri; apa pun di luar DIHAPUS
 * otomatis tiap boot SEBELUM server menyala (jumlah + ukuran + nama tercatat
 * di log — tidak ada hapus diam-diam). Folder SDK-SERVER/LOGIN-SERVER/
 * CHAT-SERVER/DUNGEON-SERVER dijaga kosong (layanannya menyimpan state di
 * dragonball.db; berkas nyasar di sana = sampah, disapu).
 */
public final class Cleaner {

    /** Entri diizinkan di root folder server (0.0.4 — 3 file + 6 folder layanan). */
    private static final String[] ROOT_WHITELIST = {
            Paths.ENV_NAME,
            "dragonball.db", "dragonball.db-journal", "dragonball.db-wal",
            "dragonball.db-shm", "dragonball.db.corrupt",
            "status.json",
            Paths.SDK_DIR, Paths.LOGIN_DIR, Paths.MAIN_DIR,
            Paths.CHAT_DIR, Paths.DUNGEON_DIR, Paths.LOG_DIR
    };

    /** File diizinkan di MAIN-SERVER/ — mirror bs/, paket config-offline/, penanda versi. */
    private static final String[] MAIN_WHITELIST = {"bs", "config-offline", "www.stamp"};

    /** File diizinkan di MAIN-SERVER/config-offline/ — paket MANUAL 5 file (kanonik APK). */
    private static final String[] OFFLINE_WHITELIST = {
            "setting.json", "setting_BS_Android.bin", "servers.json",
            "serversetting.json", "manifest.txt"
    };

    /** File diizinkan di LOG/ — nyawa + rotasi + bukti crash yang sudah dibaca. */
    private static final String[] LOG_WHITELIST = {
            "human.log", "human.log.1", "events.jsonl", "events.jsonl.1",
            "crash.log", "crash.log.read"
    };

    /** Folder layanan tanpa file (SDK/LOGIN/CHAT/DUNGEON — state di dragonball.db). */
    private static final String[] EMPTY_WHITELIST = {};

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
            // 0.0.4 — folder layanan dijaga whitelist isinya sendiri:
            sweep(new File(root, Paths.MAIN_DIR), MAIN_WHITELIST, Paths.MAIN_DIR + "/", r);
            sweep(new File(new File(root, Paths.MAIN_DIR), "config-offline"), OFFLINE_WHITELIST,
                    Paths.MAIN_DIR + "/config-offline/", r);
            sweep(new File(root, Paths.LOG_DIR), LOG_WHITELIST, Paths.LOG_DIR + "/", r);
            // SDK-SERVER/ LOGIN-SERVER/ CHAT-SERVER/ DUNGEON-SERVER/ — kosong
            // (state layanan hidup di dragonball.db); berkas nyasar = sampah.
            sweep(new File(root, Paths.SDK_DIR), EMPTY_WHITELIST, Paths.SDK_DIR + "/", r);
            sweep(new File(root, Paths.LOGIN_DIR), EMPTY_WHITELIST, Paths.LOGIN_DIR + "/", r);
            sweep(new File(root, Paths.CHAT_DIR), EMPTY_WHITELIST, Paths.CHAT_DIR + "/", r);
            sweep(new File(root, Paths.DUNGEON_DIR), EMPTY_WHITELIST, Paths.DUNGEON_DIR + "/", r);
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
