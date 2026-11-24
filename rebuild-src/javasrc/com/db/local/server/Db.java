package com.db.local.server;

import java.io.File;
import java.util.List;

/**
 * Bagian 29 — LAPISAN STORAGE AKUN.
 *
 * Keputusan owner (23 Nov): "jika php berat kita harus realistis cari yg setara
 * misal sqlite" -> engine PHP asli diganti engine Java dengan PERILAKU kawat
 * identik 100% (bukan bypass), dan storage MySQL dugaan server asli diganti
 * SQLite — bawaan Android (android.database.sqlite, API 1+), nol dependensi,
 * nol berat. Diuji awal: file legacy data/account.json + data/accounts.json
 * DIMIGRASI otomatis ke DB pada boot pertama (uid/token PEMAIN TIDAK berubah —
 * progres game aman).
 *
 * Dua backend, SATU kontrak (interface ini):
 *  - DbSqlite : device (android.database.sqlite). Pilihan utama.
 *  - DbJson   : fallback desktop smoke test (file JSON, format legacy sama).
 * Pemilihan backend: `new DbSqlite(...)` di dalam try/catch Throwable — di
 * desktop JVM class android.* tidak ada -> NoClassDefFoundError -> fallback
 * DbJson. Di device SQLite selalu ada. Backend aktif dilaporkan ke log SYS
 * ("DB-BACKEND sqlite|json") agar bisa diverifikasi owner via dashboard/log.
 *
 * Skema (satu tabel, satu baris per akun):
 *   accounts(uid TEXT PRIMARY KEY, username TEXT, email TEXT UNIQUE,
 *            pass_md5 TEXT, token TEXT, is_guest INTEGER,
 *            create_time INTEGER, last_login INTEGER)
 *  - tamu       : is_guest=1, email=""
 *  - akun email : is_guest=0, email lowercase
 *  - bind email = UPDATE baris tamu in-place -> uid+token TIDAK berubah
 */
public abstract class Db {

    /** Satu baris akun. */
    public static final class Row {
        public String uid;
        public String username;
        public String email;      // lowercase; "" utk tamu
        public String passMd5;
        public String token;
        public boolean isGuest;
        public long createTime;
        public long lastLogin;
    }

    public abstract Row guest();                    // baris tamu, atau null bila belum ada
    public abstract Row byUid(String uid);
    public abstract Row byEmail(String emailLower);
    public abstract Row byToken(String token);
    public abstract java.util.List<Row> accounts(); // semua akun email (is_guest=0), urut create_time

    public abstract void putGuest(Row r);           // insert-or-update TAMU (selalu 1 baris tamu)
    public abstract boolean insertAccount(Row r);   // false bila email sudah dipakai
    public abstract boolean bindToUid(String uid, String emailLower, String passMd5); // tamu->email in-place
    public abstract boolean changePassword(String uid, String oldMd5, String newMd5);
    public abstract void touchLogin(String uid);

    public abstract String backend();               // "sqlite" | "json"

    /* ================= pabrik ================= */

    public static final Object LOCK = new Object();
    private static volatile Db sDb;

    /** DB untuk <serverRoot> — backend dipilih otomatis; idempotent.
     *
     * Bagian 30 (koreksi owner): semua milik SDK masuk folder sdk/ —
     * lokasi DB pindah dari <serverRoot>/data/ ke <serverRoot>/sdk/data/.
     * Migrasi otomatis dari lokasi lama (instalan v13/v14): db.sqlite
     * + legacy account.json/accounts.json disalin ke lokasi baru bila
     * target belum ada — uid/token PEMAIN TETAP. */
    public static Db open(String serverRoot) {
        synchronized (LOCK) {
            if (sDb != null) return sDb;
            File base = new File(serverRoot == null || serverRoot.isEmpty()
                    ? "." : serverRoot);
            File data = new File(base, "sdk" + File.separator + "data");
            if (!data.exists()) data.mkdirs();
            migrateFromOldLocation(base, data);
            Db d;
            try {
                d = new DbSqlite(data);
            } catch (Throwable t) {
                // desktop JVM: android.database.sqlite tidak ada -> fallback
                d = new DbJson(data);
            }
            sDb = d;
            log("DB-BACKEND " + d.backend() + " dir=" + data.getAbsolutePath());
            return d;
        }
    }

    /**
     * Bagian 30 — salin berkas data dari lokasi lama <serverRoot>/data/
     * (instalan v13/v14) ke lokasi baru <serverRoot>/sdk/data/ BILA target
     * belum ada. db.sqlite = DB v14; account.json = tamu legacy v8-v13;
     * accounts.json = akun email legacy v13. Tidak pernah menimpa data baru.
     */
    private static void migrateFromOldLocation(File base, File data) {
        File oldData = new File(base, "data");
        if (!oldData.isDirectory()) return;
        String[] files = {"db.sqlite", "account.json", "accounts.json"};
        boolean any = false;
        for (int i = 0; i < files.length; i++) {
            File src = new File(oldData, files[i]);
            File dst = new File(data, files[i]);
            if (!src.isFile() || dst.exists()) continue;
            try {
                copyFile(src, dst);
                any = true;
                log("LOCATION-MIGRATION " + files[i] + " : server/data/ -> sdk/data/ ("
                        + dst.length() + " B)");
            } catch (Throwable t) {
                log("LOCATION-MIGRATION FAILED " + files[i] + " : " + t);
            }
        }
        if (any) log("LOCATION-MIGRATION done — player uid/token kept");
    }

    private static void copyFile(File src, File dst) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(src);
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    /** HANYA untuk smoke test — reset instance pabrik. */
    public static void resetForTest() {
        synchronized (LOCK) {
            sDb = null;
        }
    }

    static void log(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_SDK;
        e.kind = "SYS";
        e.method = "SYS";
        e.path = msg;
        e.status = 0;
        LoggerCore.log(e);
    }
}
