package com.db.local.server;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bagian 29 — backend SQLite (android.database.sqlite, bawaan Android).
 *
 * Ini "setara realistis" dari MySQL server asli (keputusan owner 23 Nov).
 * Migrasi legacy otomatis: bila tabel masih kosong dan file
 * data/account.json (tamu) / data/accounts.json (akun email) dari v8-v13
 * ada -> diimpor UTUH (uid/token/pemain tidak berubah) lalu dicatat ke log.
 *
 * Semua query pakai bind args (anti SQL injection); akses dari handler
 * HTTP multi-thread -> LOCK global sama seperti file store lama.
 */
public final class DbSqlite extends Db {

    private static final Object LOCK = new Object();
    private final SQLiteDatabase db;
    private final File dataDir;

    public DbSqlite(File dataDir) {
        this.dataDir = dataDir;
        File f = new File(dataDir, "db.sqlite");
        db = SQLiteDatabase.openOrCreateDatabase(f.getAbsolutePath(), null);
        db.execSQL("CREATE TABLE IF NOT EXISTS accounts ("
                + "uid TEXT PRIMARY KEY NOT NULL,"
                + "username TEXT NOT NULL DEFAULT '',"
                + "email TEXT NOT NULL DEFAULT '',"
                + "pass_md5 TEXT NOT NULL DEFAULT '',"
                + "token TEXT NOT NULL DEFAULT '',"
                + "is_guest INTEGER NOT NULL DEFAULT 0,"
                + "create_time INTEGER NOT NULL DEFAULT 0,"
                + "last_login INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_email"
                + " ON accounts(email)");
        migrateLegacy();
    }

    @Override
    public String backend() { return "sqlite"; }

    /* ==================== migrasi legacy ==================== */

    private void migrateLegacy() {
        synchronized (LOCK) {
            try {
                Cursor c = db.rawQuery("SELECT COUNT(*) FROM accounts", (String[]) null);
                long n = 0;
                if (c.moveToFirst()) n = c.getLong(0);
                c.close();
                if (n > 0) return;   // sudah ada isi — jangan sentuh

                final boolean[] any = {false};
                // v16: tiap sumber legacy try/catch TERPISAH — satu file rusak
                // tidak menggagalkan sumber lain (bug device 23 Nov: exception
                // tunggal menghentikan SEMUA migrasi).
                try {
                    File acc = new File(dataDir, "account.json");
                    if (acc.exists()) {
                        String s = readFile(acc);
                        String uid = AccountStore.jsonStr(s, "uid");
                        String token = AccountStore.jsonStr(s, "token");
                        String uname = AccountStore.jsonStr(s, "username");
                        if (uid != null && !uid.isEmpty() && token != null && !token.isEmpty()) {
                            Row r = new Row();
                            r.uid = uid;
                            r.username = uname == null || uname.isEmpty() ? uid : uname;
                            r.email = "";
                            r.passMd5 = "";
                            r.token = token;
                            r.isGuest = true;
                            r.createTime = AccountStore.jsonLong(s, "createTime");
                            r.lastLogin = r.createTime;
                            putGuest(r);
                            any[0] = true;
                            Db.log("MIGRASI tamu account.json uid=" + uid + " username=" + r.username);
                        }
                    }
                } catch (Throwable t) {
                    Db.log("MIGRATION account.json failed: " + t);
                }
                try {
                    File accs = new File(dataDir, "accounts.json");
                    if (accs.exists()) {
                        String s = readFile(accs);
                        int arr = s.indexOf('[');
                        int end = s.lastIndexOf(']');
                        if (arr >= 0 && end > arr) {
                            int i = arr + 1;
                            while (i < end) {
                                int open = s.indexOf('{', i);
                                if (open < 0 || open >= end) break;
                                int close = closeObj(s, open);
                                if (close < 0) break;
                                String obj = s.substring(open, close + 1);
                                String email = AccountStore.jsonStr(obj, "email");
                                String uid = AccountStore.jsonStr(obj, "uid");
                                String token = AccountStore.jsonStr(obj, "token");
                                String pass = AccountStore.jsonStr(obj, "passMd5");
                                if (email != null && uid != null && token != null
                                        && !email.isEmpty() && !uid.isEmpty()) {
                                    Row r = new Row();
                                    r.uid = uid;
                                    r.username = email;
                                    r.email = email;
                                    r.passMd5 = pass == null ? "" : pass;
                                    r.token = token;
                                    r.isGuest = false;
                                    r.createTime = AccountStore.jsonLong(obj, "createTime");
                                    r.lastLogin = r.createTime;
                                    // bind bila uid sama dgn tamu (hasil bindMail v13);
                                    // INSERT bila uid belum ada
                                    Row ex = byUid(r.uid);
                                    if (ex != null) {
                                        db.execSQL("UPDATE accounts SET email=?, pass_md5=?,"
                                                + " is_guest=0, token=?, create_time=?,"
                                                + " last_login=? WHERE uid=?", new Object[]{
                                                r.email, r.passMd5, r.token, r.createTime,
                                                r.lastLogin, r.uid});
                                    } else {
                                        insertAccount(r);
                                    }
                                    any[0] = true;
                                    Db.log("MIGRASI akun email uid=" + uid + " email=" + email);
                                }
                                i = close + 1;
                            }
                        }
                    }
                } catch (Throwable t) {
                    Db.log("MIGRATION accounts.json failed: " + t);
                }
                if (any[0]) Db.log("MIGRATION legacy JSON -> SQLite DONE (original uid/token kept)");
            } catch (Throwable t) {
                Db.log("MIGRATION failed: " + t);
            }
        }
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
            return new String(buf, 0, off, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static int closeObj(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    /**
     * v16 FIX DEVICE 23 Nov: rawQuery(String, Object[]) TIDAK ADA di runtime
     * Android asli (NoSuchMethodError di API 31 — hanya ada di android.jar
     * API 33+, stub lama salah deklarasi). API framework yang benar sejak
     * API 1 = rawQuery(String, String[]). Semua query lalui sini; argumen
     * dikonversi String.valueOf (execSQL TETAP Object[] — itu API 1 asli).
     */
    private static String[] sa(Object[] a) {
        if (a == null) return null;
        String[] r = new String[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] == null ? null : String.valueOf(a[i]);
        return r;
    }

    /* ==================== mapping row ==================== */

    private static Row map(Cursor c) {
        Row r = new Row();
        r.uid = c.getString(c.getColumnIndexOrThrow("uid"));
        r.username = c.getString(c.getColumnIndexOrThrow("username"));
        r.email = c.getString(c.getColumnIndexOrThrow("email"));
        r.passMd5 = c.getString(c.getColumnIndexOrThrow("pass_md5"));
        r.token = c.getString(c.getColumnIndexOrThrow("token"));
        r.isGuest = c.getLong(c.getColumnIndexOrThrow("is_guest")) == 1;
        r.createTime = c.getLong(c.getColumnIndexOrThrow("create_time"));
        r.lastLogin = c.getLong(c.getColumnIndexOrThrow("last_login"));
        return r;
    }

    private static final String COLS =
            "uid,username,email,pass_md5,token,is_guest,create_time,last_login";

    private Row one(String sql, Object[] args) {
        synchronized (LOCK) {
            Cursor c = db.rawQuery(sql, sa(args));
            try {
                if (!c.moveToFirst()) return null;
                return map(c);
            } finally {
                try { c.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private List<Row> many(String sql, Object[] args) {
        synchronized (LOCK) {
            List<Row> out = new ArrayList<Db.Row>();
            Cursor c = db.rawQuery(sql, sa(args));
            try {
                boolean ok = c.moveToFirst();
                while (ok && !c.isAfterLast()) {
                    out.add(map(c));
                    ok = c.moveToNext();
                }
            } finally {
                try { c.close(); } catch (Throwable ignored) {}
            }
            return out;
        }
    }

    /* ==================== Db ==================== */

    @Override
    public Row guest() {
        return one("SELECT " + COLS + " FROM accounts WHERE is_guest=1 LIMIT 1", null);
    }

    @Override
    public Row byUid(String uid) {
        return one("SELECT " + COLS + " FROM accounts WHERE uid=?", new Object[]{uid});
    }

    @Override
    public Row byEmail(String emailLower) {
        return one("SELECT " + COLS + " FROM accounts WHERE email=?", new Object[]{emailLower});
    }

    @Override
    public Row byToken(String token) {
        return one("SELECT " + COLS + " FROM accounts WHERE token=?", new Object[]{token});
    }

    @Override
    public List<Row> accounts() {
        return many("SELECT " + COLS + " FROM accounts WHERE is_guest=0"
                + " ORDER BY create_time ASC", null);
    }

    @Override
    public void putGuest(Row r) {
        synchronized (LOCK) {
            db.execSQL("INSERT OR REPLACE INTO accounts(" + COLS + ") VALUES(?,?,?,?,?,?,?,?)",
                    new Object[]{r.uid, r.username, "", "", r.token,
                            r.isGuest ? 1L : 0L, r.createTime, r.lastLogin});
        }
    }

    @Override
    public boolean insertAccount(Row r) {
        synchronized (LOCK) {
            if (byEmail(r.email) != null) return false;
            try {
                db.execSQL("INSERT OR REPLACE INTO accounts(" + COLS + ") VALUES(?,?,?,?,?,?,?,?)",
                        new Object[]{r.uid, r.username, r.email, r.passMd5, r.token,
                                0L, r.createTime, r.lastLogin});
                return true;
            } catch (Throwable t) {
                Db.log("DB-INSERT failed: " + t);
                return false;
            }
        }
    }

    @Override
    public boolean bindToUid(String uid, String emailLower, String passMd5) {
        synchronized (LOCK) {
            if (byEmail(emailLower) != null) return false;
            Row src = byUid(uid);
            if (src == null) return false;
            db.execSQL("UPDATE accounts SET email=?, pass_md5=?, is_guest=0,"
                    + " last_login=? WHERE uid=?", new Object[]{
                    emailLower, passMd5, System.currentTimeMillis(), uid});
            return true;
        }
    }

    @Override
    public boolean changePassword(String uid, String oldMd5, String newMd5) {
        synchronized (LOCK) {
            Row src = byUid(uid);
            if (src == null || oldMd5 == null || !src.passMd5.equals(oldMd5)) return false;
            db.execSQL("UPDATE accounts SET pass_md5=? WHERE uid=?",
                    new Object[]{newMd5, uid});
            return true;
        }
    }

    @Override
    public void touchLogin(String uid) {
        synchronized (LOCK) {
            db.execSQL("UPDATE accounts SET last_login=? WHERE uid=?",
                    new Object[]{System.currentTimeMillis(), uid});
        }
    }
}
