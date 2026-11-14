package com.db.local.server;

import java.io.File;

/**
 * DRAGONBALL.DB — DATABASE SQLITE LOKAL (0.4.3; 0.0.3 pindah folder).
 *
 * Satu file <root>/db/dragonball.db — engine SQLite bawaan Android
 * (android.database.sqlite — engine yang sama dipakai aplikasi sistem, nol
 * tambahan APK). Menggantikan penyimpanan txt/tab yang rapuh (tulis-ulang
 * penuh = korup bila mati di tengah, tanpa lock, tanpa indeks, parsing tab
 * pecah oleh karakter khusus). SQLite memberi: transaksi ACID, atomic write,
 * journal crash-safe, PRIMARY KEY beneran, dan bisa diperiksa dengan tools
 * standar (sqlite3 dragonball.db "SELECT * FROM accounts").
 *
 * 0.0.3 — DATA PEMAIN KINI DI db/ (bukan config/): keluhan owner "struktur
 * folder pembacaan file di folder server Android/data.. berantakan" — config/
 * SATU FILE .env di root (pengaturan; 0.0.4), db = dragonball.db di root.
 * Perangkat lama dipindah otomatis Paths.migrateToServiceLayout() SEBELUM
 * boot() dipanggil (config/dragonball.db → db/dragonball.db, terekam log) —
 * data tidak hilang.
 *
 * SKEMA (final — 4 tabel):
 *
 *   accounts     (username TEXT PRIMARY KEY, pass_md5 TEXT, created_at TEXT,
 *                 last_login TEXT, login_count INTEGER)
 *                 — akun tahap SDK (daftar/masuk) = akun tahap game (loginGame):
 *                   satu identitas, username PK.
 *   sdk_guests   (device_id TEXT PRIMARY KEY, uid TEXT, created_at TEXT)
 *                 — pemetaan tamu per perangkat (server asli: deviceId sama =
 *                   tamu sama, isNewUser 0). Baris pertama = tamu pemilik
 *                   (migrasi guest lama — progres lekat).
 *   sdk_sessions (auth_token TEXT PRIMARY KEY, uid TEXT, is_guest INTEGER,
 *                 created_at TEXT, last_seen TEXT)
 *                 — sesi SDK (registerVisitor/autoLogin/loginByName/
 *                   registerUser/loginchecknative).
 *   kv           (key TEXT PRIMARY KEY, value TEXT)
 *                 — guest lokal pemilik ("local_user"), uid sesi terakhir
 *                   ("last_uid") untuk userid url H5.
 *
 * ENGINE (rantai pilihan saat boot):
 *   1. DbAndroid — android.database.sqlite (device — jalur utama)
 *   2. DbJdbc    — org.sqlite JDBC (HANYA harness desktop; class ini tidak
 *                  dimasukkan ke classes2.dex)
 *   3. DbMem     — memori (jaring pengaman terakhir; server TIDAK PERNAH
 *                  boleh membuat game gagal jalan)
 *
 * MIGRASI (sekali, saat boot, SEBELUM Cleaner): isi file txt warisan 0.4.2
 * dibaca lalu diimpor ke dragonball.db (data lama tidak hilang):
 *   local-user.txt → kv local_user · accounts.txt → accounts ·
 *   sdk-guests.txt → sdk_guests · sdk-session.txt → sdk_sessions ·
 *   sdk-last.txt → kv last_uid.  Setelah impor, file di-rename "*.imported"
 *   (Cleaner boleh menghapusnya — isinya sudah pindah).
 *
 * File yang TETAP berupa file (sengaja, bukan data transaksional):
 *   .env (config aplikasi, 0.0.4) · servers.json (snapshot daftar server,
 *   boleh disunting pemilik) · status.json (dasbor) · log (log, bukan data).
 */
public final class Db {

    /** Skema — dipakai semua engine (urutan tetap). */
    static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS accounts ("
                    + "username TEXT PRIMARY KEY, pass_md5 TEXT NOT NULL DEFAULT '',"
                    + " created_at TEXT NOT NULL DEFAULT '', last_login TEXT NOT NULL DEFAULT '',"
                    + " login_count INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS sdk_guests ("
                    + "device_id TEXT PRIMARY KEY, uid TEXT NOT NULL,"
                    + " created_at TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE IF NOT EXISTS sdk_sessions ("
                    + "auth_token TEXT PRIMARY KEY, uid TEXT NOT NULL,"
                    + " is_guest INTEGER NOT NULL DEFAULT 0,"
                    + " created_at TEXT NOT NULL DEFAULT '', last_seen TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE IF NOT EXISTS kv ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '')"
    };

    /** Kunci kv yang diketahui. */
    public static final String KV_LOCAL_USER = "local_user";
    public static final String KV_LAST_UID = "last_uid";

    /** Engine penyimpanan. */
    public interface Impl {
        // ---- accounts: {pass_md5, created_at, last_login, login_count} | null ----
        String[] account(String username);

        void upsertAccount(String username, String passMd5, String createdAt,
                           String lastLogin, long loginCount);

        int accountCount();

        /** Semua nama akun, urutan masuk. */
        String[] accountNames();

        // ---- guests ----
        String guestUid(String deviceId);

        void putGuest(String deviceId, String uid, String when);

        int guestCount();

        /** Baris "deviceId\tuid" urutan masuk (baris pertama = tamu pemilik). */
        String[] guestRows();

        // ---- sessions ----
        void putSession(String authToken, String uid, boolean guest, String when);

        /** "uid\tguest(0/1)" | null. */
        String session(String authToken);

        /** Uid sesi terakhir (last_seen terbaru). */
        String lastSessionUid();

        // ---- kv ----
        String kv(String key);

        void kvPut(String key, String value);

        /** true = SQLite sungguhan (bukan jaring memori). */
        boolean sqlite();
    }

    private static final Object LOCK = new Object();
    private static volatile Impl impl;
    private static volatile String engine = "belum-dibuka";

    private Db() {
    }

    /** Engine aktif (untuk log/dasbor). */
    public static String engine() {
        Impl i = impl;
        return i == null ? engine : engine;
    }

    /** Impl aktif — tidak pernah null (auto-buka jaring memori bila belum boot). */
    public static Impl i() {
        Impl i = impl;
        if (i != null) {
            return i;
        }
        synchronized (LOCK) {
            if (impl == null) {
                impl = new DbMem();
                engine = "memori (belum boot)";
                log("⚠️", "dragonball.db dipakai sebelum boot — memakai jaring memori sementara", null);
            }
            return impl;
        }
    }

    /**
     * Buka dragonball.db + migrasi txt warisan. Dipanggil Starter SEBELUM
     * Cleaner (file txt lama masih ada di boot pertama 0.4.3).
     *
     * 0.0.3 — dua folder terpisah:
     *   dbDir          = folder db/ — tempat dragonball.db SAAT INI;
     *   legacyConfDir  = folder config/ — sumber file txt warisan 0.4.2
     *                    (perangkat lama; dibaca lalu di-rename *.imported —
     *                    isinya sudah pindah ke SQLite).
     * Jalur file database = Paths.dbFile() (sumber tunggal — 0.0.3).
     */
    public static void boot(File dbDir, File legacyConfDir) {
        synchronized (LOCK) {
            try {
                if (!dbDir.exists()) {
                    dbDir.mkdirs();
                }
            } catch (Throwable ignored) {
            }
            File f = new File(dbDir, "dragonball.db");
            Throwable firstErr = null;
            try {
                impl = new DbAndroid(f);
                engine = "SQLite (android.database.sqlite)";
            } catch (Throwable t1) {
                firstErr = t1;
                try {
                    impl = new DbJdbc(f);
                    engine = "SQLite (JDBC — harness)";
                } catch (Throwable t2) {
                    impl = new DbMem();
                    engine = "memori (jaring pengaman)";
                    log("⚠️", "dragonball.db: SQLite tidak tersedia — data dijaga di memori (boot tetap jalan)",
                            firstErr + " / " + t2);
                }
            }
            long imported = 0;
            try {
                imported = migrateLegacy(legacyConfDir);
            } catch (Throwable t) {
                log("⚠️", "Migrasi data lama ke dragonball.db menemui kendala — boot tetap jalan",
                        String.valueOf(t));
            }
            if (i().sqlite()) {
                // 0.4.7: kesiapan DB = TEKNIS (bukan berita — tiap boot sama);
                // yang PENTING justru kegagalannya (⚠ terpisah di Starter).
                // Identitas pemain tetap tampil di baris "Pemain:" boot (Starter).
                HumanLog.evTech("💾", "dragonball.db siap (SQLite, " + HumanLog.fmtBytes(f.length())
                        + ") — akun/tamu/sesi tahan reboot" + (imported > 0
                        ? " · migrasi txt lama: " + imported + " baris" : ""),
                        engine + " · " + f.getAbsolutePath());
            }
        }
    }

    // ---------------- migrasi txt warisan (0.4.2 → 0.4.3) ----------------

    /**
     * Import file txt lama → SQLite. Return jumlah baris yang benar-benar
     * diimpor. 0.0.3: sumber tetap folder config/ (file warisan 0.4.2 hanya
     * pernah hidup di sana); setelah impor di-rename *.imported — kini config/
     * whitelist-nya murni pengaturan, jadi *.imported disapu Cleaner
     * (isinya sudah aman di SQLite — bukan data hilang).
     */
    private static long migrateLegacy(File confDir) {
        long n = 0;
        // 1) local-user.txt → kv local_user
        File f = new File(confDir, "local-user.txt");
        if (f.exists()) {
            String uid = txt(f).trim();
            if (uid.length() >= 4 && uid.length() <= 64
                    && (i().kv(KV_LOCAL_USER) == null || i().kv(KV_LOCAL_USER).length() == 0)) {
                i().kvPut(KV_LOCAL_USER, uid);
                n++;
            }
            done(f);
        }
        // 2) accounts.txt → accounts (kolom: userId·password·dibuat·login-terakhir·jumlah)
        f = new File(confDir, "accounts.txt");
        if (f.exists()) {
            String[] lines = txt(f).split("\n");
            for (String line0 : lines) {
                String line = line0.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\t");
                if (p.length < 1 || p[0].length() == 0) {
                    continue;
                }
                if (i().account(p[0]) == null) {
                    i().upsertAccount(p[0],
                            p.length > 1 ? p[1] : "",
                            p.length > 2 ? p[2] : "",
                            p.length > 3 ? p[3] : "",
                            parseLong(p.length > 4 ? p[4] : "1", 1L));
                    n++;
                }
            }
            done(f);
        }
        // 3) sdk-guests.txt → sdk_guests (kolom: deviceId·uid·dibuat)
        f = new File(confDir, "sdk-guests.txt");
        if (f.exists()) {
            String[] lines = txt(f).split("\n");
            for (String line0 : lines) {
                String line = line0.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\t");
                if (p.length >= 2 && p[0].length() > 0 && p[1].length() > 0
                        && i().guestUid(p[0]) == null) {
                    i().putGuest(p[0], p[1], p.length > 2 ? p[2] : "");
                    n++;
                }
            }
            done(f);
        }
        // 4) sdk-session.txt → sdk_sessions (kolom: token·uid·guest·waktu)
        f = new File(confDir, "sdk-session.txt");
        if (f.exists()) {
            String[] lines = txt(f).split("\n");
            for (String line0 : lines) {
                String line = line0.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\t");
                if (p.length >= 3 && p[0].length() > 0 && p[1].length() > 0
                        && i().session(p[0]) == null) {
                    i().putSession(p[0], p[1], "1".equals(p[2]), p.length > 3 ? p[3] : "");
                    n++;
                }
            }
            done(f);
        }
        // 5) sdk-last.txt → kv last_uid (kolom: uid·username·waktu)
        f = new File(confDir, "sdk-last.txt");
        if (f.exists()) {
            String[] lines = txt(f).split("\n");
            for (String line0 : lines) {
                String line = line0.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\t");
                if (p.length >= 1 && p[0].length() >= 4
                        && (i().kv(KV_LAST_UID) == null || i().kv(KV_LAST_UID).length() == 0)) {
                    i().kvPut(KV_LAST_UID, p[0]);
                    n++;
                }
            }
            done(f);
        }
        return n;
    }

    /** Tandai file warisan sudah diimpor (rename — Cleaner boleh menghapus). */
    private static void done(File f) {
        try {
            File mark = new File(f.getAbsolutePath() + ".imported");
            if (mark.exists()) {
                mark.delete();
            }
            f.renameTo(mark);
        } catch (Throwable ignored) {
        }
    }

    private static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static String txt(File f) {
        try {
            java.io.Reader r = new java.io.InputStreamReader(
                    new java.io.FileInputStream(f), "UTF-8");
            StringBuilder sb = new StringBuilder(512);
            char[] buf = new char[2048];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            r.close();
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void log(String icon, String msg, String detail) {
        try {
            HumanLog.ev(icon, msg, detail);
        } catch (Throwable ignored) {
        }
    }
}
