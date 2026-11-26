package com.db.local.server;

import java.io.File;

/**
 * Bagian 67 — PERSIST HISTORY per-akun → login-server/data/history.sqlite.
 *
 * L1 (docs §5.1): file TERPISAH dari sdk/data/db.sqlite (tabel accounts =
 * MILIK SDK — login TIDAK PERNAH menulis di sana). Atlas §12.7: history
 * murni server-side (client TIDAK punya state) — sqlite = padanan DB server
 * asli (keputusan owner Bagian 29: "jika php berat kita cari yg setara
 * misal sqlite").
 *
 * HANYA disentuh bila GetServerList.json `historyMode:"persist"` (K3):
 *   - SaveHistory  → put(accountToken, serverId)  (atlas §4.3 request)
 *   - GetServerList→ list(userId) → history[] (paling baru dulu — atlas
 *     §11.4: history[0] dicocokkan loose `==` ke serverId)
 * Mode "auto" → kelas ini tidak pernah dipanggil → folder data/ TIDAK
 * pernah dibuat (kriteria smoke §3.6 no. 5).
 *
 * Skema (1 tabel):
 *   history(token TEXT PRIMARY KEY, server_id TEXT NOT NULL DEFAULT '',
 *           updated INTEGER NOT NULL DEFAULT 0)
 * token = userId (GetServerList) / accountToken (SaveHistory) — nilai sama
 * di jalur nyata (capture live 26 Nov). Tidak ada userId → "guest".
 *
 * Android: android.database.sqlite bawaan (pola DbSqlite — API 1+).
 * Desktop JVM: android.* tidak ada → NoClassDefFoundError di CALLER
 * (LoginAck menangkap Throwable) → no-op + WARN sekali ke log global.
 * Nol file fallback JSON dibuat (tree device = persis struktur oke owner).
 */
public final class LoginHistoryDb {

    private static final Object LOCK = new Object();
    private static volatile boolean warned = false;

    private LoginHistoryDb() {}

    public static void put(String token, String serverId) {
        synchronized (LOCK) {
            android.database.sqlite.SQLiteDatabase db = open();
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS history ("
                        + "token TEXT PRIMARY KEY NOT NULL,"
                        + "server_id TEXT NOT NULL DEFAULT '',"
                        + "updated INTEGER NOT NULL DEFAULT 0)");
                db.execSQL("INSERT OR REPLACE INTO history(token,server_id,updated)"
                        + " VALUES(?,?,?)",
                        new Object[]{token == null ? "" : token,
                                serverId == null ? "" : serverId,
                                System.currentTimeMillis()});
            } finally {
                try { db.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /** serverId paling baru dulu (history[0] = target resume — atlas §11.4). */
    public static java.util.ArrayList<String> list(String token) {
        synchronized (LOCK) {
            java.util.ArrayList<String> out = new java.util.ArrayList<String>();
            android.database.sqlite.SQLiteDatabase db = open();
            try {
                android.database.Cursor c = db.rawQuery(
                        "SELECT server_id FROM history WHERE token=?"
                                + " ORDER BY updated DESC LIMIT 20",
                        new String[]{token == null ? "" : token});
                try {
                    boolean ok = c.moveToFirst();
                    while (ok && !c.isAfterLast()) {
                        out.add(c.getString(0));
                        ok = c.moveToNext();
                    }
                } finally {
                    try { c.close(); } catch (Throwable ignored) {}
                }
            } finally {
                try { db.close(); } catch (Throwable ignored) {}
            }
            return out;
        }
    }

    private static android.database.sqlite.SQLiteDatabase open() {
        // folder data/ dibuat LAZY di sini — HANYA bila persist benar dipakai
        File dir = new File(LoginServerConfig.dir(), "data");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, "history.sqlite");
        return android.database.sqlite.SQLiteDatabase
                .openOrCreateDatabase(f.getAbsolutePath(), null);
    }

    /** WARN sekali (dipanggil caller saat Throwable) — log GLOBAL, bukan
     *  file log login-server (oke owner: log global jadi 1). */
    public static void warnOnce(Throwable t) {
        if (warned) return;
        warned = true;
        Db.log("LOGIN-HISTORY tidak tersedia — persist no-op (" + t + ")");
    }
}
