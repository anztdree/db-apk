package com.db.local.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jaring pengaman terakhir dragonball.db — penyimpanan di memori proses.
 * Dipakai HANYA bila SQLite tidak bisa dibuka sama sekali (di device praktis
 * tidak pernah terjadi: android.database.sqlite selalu ada). Data hilang saat
 * proses mati — tapi boot game TETAP JALAN (aturan: server lokal tidak boleh
 * menggagalkan game). Semua semantik = DbAndroid (baris pertama = tamu
 * pemilik, putSession per token, dsb).
 */
final class DbMem implements Db.Impl {

    private final LinkedHashMap<String, String[]> accounts = new LinkedHashMap<String, String[]>();
    private final LinkedHashMap<String, String[]> guests = new LinkedHashMap<String, String[]>();
    private final LinkedHashMap<String, String[]> sessions = new LinkedHashMap<String, String[]>();
    private final LinkedHashMap<String, String> kvs = new LinkedHashMap<String, String>();

    // ---------------- accounts ----------------

    @Override
    public synchronized String[] account(String username) {
        if (username == null || username.length() == 0) {
            return null;
        }
        String[] r = accounts.get(username);
        return r == null ? null : r.clone();
    }

    @Override
    public synchronized void upsertAccount(String username, String passMd5, String createdAt,
                                           String lastLogin, long loginCount) {
        if (username == null || username.length() == 0) {
            return;
        }
        accounts.put(username, new String[]{nz(passMd5), nz(createdAt), nz(lastLogin),
                String.valueOf(loginCount)});
    }

    @Override
    public synchronized int accountCount() {
        return accounts.size();
    }

    @Override
    public synchronized String[] accountNames() {
        return accounts.keySet().toArray(new String[accounts.size()]);
    }

    // ---------------- guests ----------------

    @Override
    public synchronized String guestUid(String deviceId) {
        if (deviceId == null || deviceId.length() == 0) {
            return null;
        }
        String[] r = guests.get(deviceId);
        return r == null ? null : r[0];
    }

    @Override
    public synchronized void putGuest(String deviceId, String uid, String when) {
        if (deviceId == null || deviceId.length() == 0 || uid == null || uid.length() == 0) {
            return;
        }
        guests.put(deviceId, new String[]{uid, nz(when)});
    }

    @Override
    public synchronized int guestCount() {
        return guests.size();
    }

    @Override
    public synchronized String[] guestRows() {
        ArrayList<String> out = new ArrayList<String>(guests.size());
        for (Map.Entry<String, String[]> e : guests.entrySet()) {
            out.add(e.getKey() + "\t" + e.getValue()[0]);
        }
        return out.toArray(new String[out.size()]);
    }

    // ---------------- sessions ----------------

    @Override
    public synchronized void putSession(String authToken, String uid, boolean guest, String when) {
        if (authToken == null || authToken.length() == 0 || uid == null || uid.length() == 0) {
            return;
        }
        String[] old = sessions.get(authToken);
        String created = (old != null && old[2].length() > 0) ? old[2] : nz(when);
        sessions.put(authToken, new String[]{uid, guest ? "1" : "0", created, nz(when)});
    }

    @Override
    public synchronized String session(String authToken) {
        if (authToken == null || authToken.length() == 0) {
            return null;
        }
        String[] r = sessions.get(authToken);
        return r == null ? null : r[0] + "\t" + r[1];
    }

    @Override
    public synchronized String lastSessionUid() {
        String best = null;
        String bestSeen = null;
        for (Map.Entry<String, String[]> e : sessions.entrySet()) {
            String seen = e.getValue()[3];
            if (bestSeen == null || seen.compareTo(bestSeen) >= 0) {
                bestSeen = seen;
                best = e.getValue()[0];
            }
        }
        return best;
    }

    // ---------------- kv ----------------

    @Override
    public synchronized String kv(String key) {
        return key == null ? null : kvs.get(key);
    }

    @Override
    public synchronized void kvPut(String key, String value) {
        if (key == null) {
            return;
        }
        kvs.put(key, value == null ? "" : value);
    }

    // ---------------- meta ----------------

    @Override
    public boolean sqlite() {
        return false;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
