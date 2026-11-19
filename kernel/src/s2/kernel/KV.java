// ============================================================
// KV — database KV SQLite (data/state.db), tabel kv(k,v,mtime).
// Semua data ACK = STRING JSON -> KV cukup (keputusan desain tetap).
// ============================================================
package s2.kernel;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KV {
    private static SQLiteDatabase db;
    private static File dbFile;
    private static File dataDir;
    public static long sizeBytes = 0;
    private static final Object LOCK = new Object();

    private KV() {}

    public static void init(File root) {
        synchronized (LOCK) {
            try {
                dataDir = new File(root, "data");
                if (!dataDir.exists()) dataDir.mkdirs();
                dbFile = new File(dataDir, "state.db");
                boolean fresh = !dbFile.exists();
                db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
                db.execSQL("PRAGMA journal_mode = WAL;", new Object[0]);
                db.execSQL("CREATE TABLE IF NOT EXISTS kv(k TEXT PRIMARY KEY, v TEXT NOT NULL, mtime INTEGER NOT NULL);", new Object[0]);
                if (fresh) Logr.write("info", "db", "state.db baru dibuat: " + dbFile.getAbsolutePath());
                refreshSize();
                seed(false);
            } catch (Throwable t) {
                Logr.write("error", "db", "KV init gagal: " + t);
            }
        }
    }

    public static void refreshSize() {
        try {
            sizeBytes = dbFile != null && dbFile.exists() ? dbFile.length() : 0;
        } catch (Throwable t) {
            sizeBytes = 0;
        }
    }

    public static String getRaw(String k) {
        synchronized (LOCK) {
            if (db == null) return null;
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT v FROM kv WHERE k = ?", new String[]{k});
                if (c.moveToFirst()) return c.getString(0);
                return null;
            } catch (Throwable t) {
                return null;
            } finally {
                if (c != null) c.close();
            }
        }
    }

    public static Object get(String k) {
        String raw = getRaw(k);
        if (raw == null) return null;
        try {
            return Json.parseSmart(raw);
        } catch (Throwable t) {
            return raw;
        }
    }

    public static Json.Obj detail(String k) {
        synchronized (LOCK) {
            if (db == null) return null;
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT v, mtime FROM kv WHERE k = ?", new String[]{k});
                if (!c.moveToFirst()) return null;
                String v = c.getString(0);
                long mtime = c.getLong(1);
                Json.Obj o = new Json.Obj();
                o.put("ok", true);
                o.put("k", k);
                o.put("raw", v);
                boolean isJson = false;
                Object parsed = Json.NULL;
                try {
                    parsed = Json.parseSmart(v);
                    isJson = true;
                } catch (Throwable ignore) {
                    parsed = v;
                }
                o.put("json", parsed);
                o.put("isJson", isJson);
                o.put("mtime", Util.isoMs(mtime));
                return o;
            } catch (Throwable t) {
                return null;
            } finally {
                if (c != null) c.close();
            }
        }
    }

    public static void set(String k, String v) {
        synchronized (LOCK) {
            if (db == null) return;
            try {
                db.execSQL("INSERT INTO kv(k, v, mtime) VALUES(?, ?, ?) ON CONFLICT(k) DO UPDATE SET v=excluded.v, mtime=excluded.mtime;",
                        new Object[]{k, v == null ? "null" : v, System.currentTimeMillis()});
            } catch (Throwable t) {
                try {
                    db.execSQL("INSERT OR REPLACE INTO kv(k, v, mtime) VALUES(?, ?, ?);",
                            new Object[]{k, v == null ? "null" : v, System.currentTimeMillis()});
                } catch (Throwable t2) {
                    Logr.write("error", "db", "set gagal: " + t2);
                }
            }
            refreshSize();
        }
    }

    public static boolean del(String k) {
        synchronized (LOCK) {
            if (db == null) return false;
            try {
                int n = db.delete("kv", "k = ?", new String[]{k});
                refreshSize();
                return n > 0;
            } catch (Throwable t) {
                return false;
            }
        }
    }

    public static int incr(String k, int n) {
        String cur = getRaw(k);
        long curNum = 0;
        if (cur != null) {
            try {
                curNum = Long.parseLong(cur.trim());
            } catch (Throwable ignore) {
                curNum = 0;
            }
        }
        long next = curNum + n;
        set(k, String.valueOf(next));
        return (int) next;
    }

    public static int nextUid() {
        return incr("counter:uid", 1);
    }

    public static Json.Obj list(String search, int limit, int offset) {
        synchronized (LOCK) {
            Json.Obj out = new Json.Obj();
            if (db == null) {
                out.put("ok", false);
                out.put("total", 0);
                out.put("keys", new Json.Arr());
                return out;
            }
            if (limit < 1) limit = 1;
            if (limit > 200) limit = 200;
            if (offset < 0) offset = 0;
            try {
                String where = search != null && search.trim().length() > 0 ? "WHERE k LIKE ?" : "";
                String[] args = search != null && search.trim().length() > 0
                        ? new String[]{"%" + search.trim() + "%"} : new String[0];
                long total = 0;
                Cursor c = db.rawQuery("SELECT COUNT(*) AS n FROM kv " + where, args);
                if (c.moveToFirst()) total = c.getLong(0);
                c.close();
                Json.Arr keys = new Json.Arr();
                Cursor r = db.rawQuery(
                        "SELECT k, v, mtime FROM kv " + where + " ORDER BY k LIMIT " + limit + " OFFSET " + offset,
                        args);
                while (r.moveToNext()) {
                    Json.Obj row = new Json.Obj();
                    row.put("k", r.getString(0));
                    row.put("size", r.getString(1).length());
                    row.put("mtime", Util.isoMs(r.getLong(2)));
                    keys.put(row);
                }
                r.close();
                out.put("ok", true);
                out.put("total", total);
                out.put("keys", keys);
            } catch (Throwable t) {
                out.put("ok", false);
                out.put("error", String.valueOf(t));
            }
            return out;
        }
    }

    public static int count() {
        synchronized (LOCK) {
            if (db == null) return 0;
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT COUNT(*) AS n FROM kv", null);
                if (c.moveToFirst()) return (int) c.getLong(0);
                return 0;
            } catch (Throwable t) {
                return 0;
            } finally {
                if (c != null) c.close();
            }
        }
    }

    /** seed.json (data/seed.json) -> import ke KV. force=true = resekan penuh. */
    public static Json.Obj seed(boolean force) {
        Json.Obj out = new Json.Obj();
        try {
            if (dataDir == null) {
                out.put("ok", true);
                out.put("imported", 0);
                return out;
            }
            File seedFile = new File(dataDir, "seed.json");
            if (!force && getRaw("meta:seedDone") != null) {
                out.put("ok", true);
                out.put("imported", 0);
                return out;
            }
            if (!seedFile.exists()) {
                if (force) Logr.write("warn", "db", "seed.json tidak ditemukan: " + seedFile.getAbsolutePath());
                out.put("ok", true);
                out.put("imported", 0);
                return out;
            }
            String content = Util.readTextFile(seedFile);
            Json.Obj seedObj = Json.parseObj(content);
            int n = 0;
            for (String k : seedObj.keys()) {
                set(k, Json.valueToString(seedObj.opt(k)));
                n++;
            }
            set("meta:seedDone", force ? ("reseed-" + System.currentTimeMillis()) : "true");
            Logr.write("info", "db", "seed.json diimpor (" + n + " key, force=" + force + ")");
            refreshSize();
            out.put("ok", true);
            out.put("imported", n);
        } catch (Throwable t) {
            Logr.write("error", "db", "seed.json tidak valid: " + t);
            out.put("ok", false);
            out.put("error", String.valueOf(t));
        }
        return out;
    }

    public static File dataDir() {
        return dataDir;
    }

    public static Map<String, String> snapshot() {
        synchronized (LOCK) {
            Map<String, String> m = new LinkedHashMap<String, String>();
            if (db == null) return m;
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT k, v FROM kv ORDER BY k", null);
                while (c.moveToNext()) m.put(c.getString(0), c.getString(1));
            } catch (Throwable ignore) {
            } finally {
                if (c != null) c.close();
            }
            return m;
        }
    }
}
