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

    /** DB untuk <serverRoot> — backend dipilih otomatis; idempotent. */
    public static Db open(String serverRoot) {
        synchronized (LOCK) {
            if (sDb != null) return sDb;
            File data = new File(serverRoot == null || serverRoot.isEmpty()
                    ? "." : serverRoot, "data");
            if (!data.exists()) data.mkdirs();
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
