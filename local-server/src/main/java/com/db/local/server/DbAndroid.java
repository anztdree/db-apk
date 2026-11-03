package com.db.local.server;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

/**
 * Engine dragonball.db di DEVICE — android.database.sqlite (SQLite bawaan
 * Android; tanpa Context: openOrCreateDatabase(path) langsung ke file).
 *
 * Semua tulisan atomic (SQLite journal), semua bacaan lewat PRIMARY KEY /
 * COUNT — tanpa parsing teks, tanpa scan file. Bila file korup (mis. storage
 * penuh saat menulis): pindah ke "*.corrupt" lalu dibuat ulang sekali.
 */
final class DbAndroid implements Db.Impl {

    private final SQLiteDatabase db;

    DbAndroid(File file) {
        SQLiteDatabase d;
        try {
            d = open(file);
        } catch (Throwable first) {
            // PULIHKAN hanya bila korupsi SQLite SUNGUHAN (file rusak). Galat lain
            // (engine tidak tersedia / lock sesaat / storage penuh) TIDAK BOLEH
            // menyentuh file — dulu rename otomatis membuat data "hilang" setiap
            // boot di engine tanpa android (harness) dan berisiko di device.
            if (!isCorruption(first)) {
                throw new RuntimeException(first);
            }
            try {
                File bad = new File(file.getAbsolutePath() + ".corrupt");
                if (bad.exists()) {
                    bad.delete();
                }
                file.renameTo(bad);
                d = open(file);
            } catch (Throwable second) {
                throw new RuntimeException(second);
            }
        }
        this.db = d;
        for (String sql : Db.SCHEMA) {
            db.execSQL(sql);
        }
    }

    /** true hanya bila galat = korupsi database SQLite (bukan engine/lock/IO lain). */
    private static boolean isCorruption(Throwable t) {
        while (t != null) {
            String cn = t.getClass().getName();
            if (cn.indexOf("SQLiteDatabaseCorruptException") >= 0
                    || cn.indexOf("SQLiteDiskIOException") >= 0) {
                return true;
            }
            String m = String.valueOf(t.getMessage()).toLowerCase();
            if (m.contains("malformed") || m.contains("not a database")
                    || m.contains("file is encrypted") || m.contains("corrupt")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static SQLiteDatabase open(File file) {
        return SQLiteDatabase.openOrCreateDatabase(file.getAbsolutePath(), null);
    }

    // ---------------- accounts ----------------

    @Override
    public String[] account(String username) {
        if (username == null || username.length() == 0) {
            return null;
        }
        Cursor c = db.rawQuery(
                "SELECT pass_md5, created_at, last_login, login_count FROM accounts WHERE username=?",
                new String[]{username});
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            return new String[]{s(c, 0), s(c, 1), s(c, 2), s(c, 3)};
        } finally {
            c.close();
        }
    }

    @Override
    public void upsertAccount(String username, String passMd5, String createdAt,
                              String lastLogin, long loginCount) {
        db.execSQL("INSERT OR REPLACE INTO accounts (username, pass_md5, created_at,"
                        + " last_login, login_count) VALUES (?,?,?,?,?)",
                new Object[]{username, nz(passMd5), nz(createdAt), nz(lastLogin), loginCount});
    }

    @Override
    public int accountCount() {
        return count("SELECT COUNT(*) FROM accounts");
    }

    @Override
    public String[] accountNames() {
        Cursor c = db.rawQuery("SELECT username FROM accounts ORDER BY rowid", null);
        try {
            String[] out = new String[c.getCount()];
            int i = 0;
            while (c.moveToNext()) {
                out[i++] = s(c, 0);
            }
            return out;
        } finally {
            c.close();
        }
    }

    // ---------------- guests ----------------

    @Override
    public String guestUid(String deviceId) {
        if (deviceId == null || deviceId.length() == 0) {
            return null;
        }
        Cursor c = db.rawQuery("SELECT uid FROM sdk_guests WHERE device_id=?",
                new String[]{deviceId});
        try {
            return c.moveToFirst() ? s(c, 0) : null;
        } finally {
            c.close();
        }
    }

    @Override
    public void putGuest(String deviceId, String uid, String when) {
        db.execSQL("INSERT OR REPLACE INTO sdk_guests (device_id, uid, created_at)"
                        + " VALUES (?,?,?)",
                new Object[]{deviceId, uid, nz(when)});
    }

    @Override
    public int guestCount() {
        return count("SELECT COUNT(*) FROM sdk_guests");
    }

    @Override
    public String[] guestRows() {
        Cursor c = db.rawQuery(
                "SELECT device_id, uid FROM sdk_guests ORDER BY rowid", null);
        try {
            String[] out = new String[c.getCount()];
            int i = 0;
            while (c.moveToNext()) {
                out[i++] = s(c, 0) + "\t" + s(c, 1);
            }
            return out;
        } finally {
            c.close();
        }
    }

    // ---------------- sessions ----------------

    @Override
    public void putSession(String authToken, String uid, boolean guest, String when) {
        if (authToken == null || authToken.length() == 0 || uid == null || uid.length() == 0) {
            return;
        }
        Cursor c = db.rawQuery("SELECT auth_token FROM sdk_sessions WHERE auth_token=?",
                new String[]{authToken});
        boolean exists = c.moveToFirst();
        c.close();
        if (exists) {
            db.execSQL("UPDATE sdk_sessions SET uid=?, is_guest=?, last_seen=? WHERE auth_token=?",
                    new Object[]{uid, guest ? 1 : 0, nz(when), authToken});
        } else {
            db.execSQL("INSERT INTO sdk_sessions (auth_token, uid, is_guest, created_at, last_seen)"
                            + " VALUES (?,?,?,?,?)",
                    new Object[]{authToken, uid, guest ? 1 : 0, nz(when), nz(when)});
        }
    }

    @Override
    public String session(String authToken) {
        if (authToken == null || authToken.length() == 0) {
            return null;
        }
        Cursor c = db.rawQuery("SELECT uid, is_guest FROM sdk_sessions WHERE auth_token=?",
                new String[]{authToken});
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            return s(c, 0) + "\t" + (c.getInt(1) != 0 ? "1" : "0");
        } finally {
            c.close();
        }
    }

    @Override
    public String lastSessionUid() {
        Cursor c = db.rawQuery(
                "SELECT uid FROM sdk_sessions ORDER BY last_seen DESC, rowid DESC LIMIT 1", null);
        try {
            return c.moveToFirst() ? s(c, 0) : null;
        } finally {
            c.close();
        }
    }

    // ---------------- kv ----------------

    @Override
    public String kv(String key) {
        if (key == null) {
            return null;
        }
        Cursor c = db.rawQuery("SELECT value FROM kv WHERE key=?", new String[]{key});
        try {
            return c.moveToFirst() ? s(c, 0) : null;
        } finally {
            c.close();
        }
    }

    @Override
    public void kvPut(String key, String value) {
        db.execSQL("INSERT OR REPLACE INTO kv (key, value) VALUES (?,?)",
                new Object[]{key, value == null ? "" : value});
    }

    // ---------------- meta ----------------

    @Override
    public boolean sqlite() {
        return true;
    }

    // ---------------- util ----------------

    private int count(String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            return c.moveToFirst() ? (int) c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    private static String s(Cursor c, int col) {
        String v = c.getString(col);
        return v == null ? "" : v;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
