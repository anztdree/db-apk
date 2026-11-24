package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bagian 29 — backend JSON (FALLBACK desktop smoke test).
 * Semantik PERSIS DbSqlite: satu tabel accounts, tamu = is_guest=1, akun email
 * = is_guest=0, bind = update in-place. File: data/account.json (tamu,
 * format legacy v8-v13 dipertahankan) + data/accounts.json (daftar akun email,
 * format legacy). Di device backend ini TIDAK terpakai (DbSqlite menang);
 * backend aktif selalu dilaporkan "DB-BACKEND ..." di log SYS.
 */
public final class DbJson extends Db {

    private static final Object LOCK = new Object();
    private final File dataDir;

    public DbJson(File dataDir) {
        this.dataDir = dataDir;
        if (!dataDir.exists()) dataDir.mkdirs();
    }

    @Override
    public String backend() { return "json"; }

    /* ==================== baca ==================== */

    @Override
    public Row guest() {
        synchronized (LOCK) {
            File f = new File(dataDir, "account.json");
            if (!f.exists()) return null;
            String s = readFile(f);
            Row r = new Row();
            r.uid = AccountStore.jsonStr(s, "uid");
            r.token = AccountStore.jsonStr(s, "token");
            r.username = AccountStore.jsonStr(s, "username");
            r.createTime = AccountStore.jsonLong(s, "createTime");
            r.email = "";
            r.passMd5 = "";
            r.isGuest = true;
            r.lastLogin = r.createTime;
            if (r.uid == null || r.token == null || r.uid.isEmpty() || r.token.isEmpty()) {
                return null;
            }
            return r;
        }
    }

    @Override
    public Row byUid(String uid) {
        if (uid == null || uid.isEmpty()) return null;
        for (Row r : accounts()) if (r.uid.equals(uid)) return r;
        return null;
    }

    @Override
    public Row byEmail(String emailLower) {
        if (emailLower == null || emailLower.isEmpty()) return null;
        for (Row r : accounts()) if (r.email.equals(emailLower)) return r;
        return null;
    }

    @Override
    public Row byToken(String token) {
        if (token == null || token.isEmpty()) return null;
        for (Row r : accounts()) if (r.token.equals(token)) return r;
        return null;
    }

    @Override
    public List<Row> accounts() {
        synchronized (LOCK) {
            List<Row> out = new ArrayList<Db.Row>();
            File f = new File(dataDir, "accounts.json");
            if (!f.exists()) return out;
            String s = readFile(f);
            int arr = s.indexOf('[');
            int end = s.lastIndexOf(']');
            if (arr < 0 || end <= arr) return out;
            int i = arr + 1;
            while (i < end) {
                int open = s.indexOf('{', i);
                if (open < 0 || open >= end) break;
                int close = closeObj(s, open);
                if (close < 0) break;
                String obj = s.substring(open, close + 1);
                Row r = new Row();
                r.email      = AccountStore.jsonStr(obj, "email");
                r.uid        = AccountStore.jsonStr(obj, "uid");
                r.token      = AccountStore.jsonStr(obj, "token");
                r.passMd5    = AccountStore.jsonStr(obj, "passMd5");
                r.username   = r.email;
                r.createTime = AccountStore.jsonLong(obj, "createTime");
                r.lastLogin  = AccountStore.jsonLong(obj, "lastLogin");
                r.isGuest    = false;
                if (r.email != null && r.uid != null && r.token != null
                        && !r.email.isEmpty() && !r.uid.isEmpty()) {
                    out.add(r);
                }
                i = close + 1;
            }
            return out;
        }
    }

    /* ==================== tulis ==================== */

    @Override
    public void putGuest(Row r) {
        synchronized (LOCK) {
            String s = "{\"uid\":\"" + JsonUtil.esc(r.uid) + "\",\"token\":\""
                    + JsonUtil.esc(r.token) + "\",\"username\":\""
                    + JsonUtil.esc(r.username) + "\",\"createTime\":" + r.createTime + "}";
            writeFile(new File(dataDir, "account.json"), s);
        }
    }

    @Override
    public boolean insertAccount(Row r) {
        synchronized (LOCK) {
            if (byEmail(r.email) != null) return false;
            List<Row> list = accounts();
            list.add(r);
            save(list);
            return true;
        }
    }

    @Override
    public boolean bindToUid(String uid, String emailLower, String passMd5) {
        synchronized (LOCK) {
            if (byEmail(emailLower) != null) return false;
            Row src = byUid(uid);
            boolean wasGuest = false;
            if (src == null) {
                // uid bisa milik TAMU (barisnya di account.json, bukan accounts.json)
                Row g = guest();
                if (g != null && g.uid.equals(uid)) { src = g; wasGuest = true; }
                else return false;
            }
            src.email = emailLower;
            src.passMd5 = passMd5;
            src.username = emailLower;
            src.isGuest = false;
            src.lastLogin = System.currentTimeMillis();
            List<Row> list = accounts();
            List<Row> dedup = new ArrayList<Db.Row>();
            for (Row x : list) if (!x.uid.equals(src.uid)) dedup.add(x);
            dedup.add(src);
            save(dedup);
            if (wasGuest) {
                // tamu menjadi akun email — baris tamu lama dihapus (semantik
                // sama dengan DbSqlite: guest() jadi null, TAMU baru = uid baru)
                new File(dataDir, "account.json").delete();
            }
            return true;
        }
    }

    @Override
    public boolean changePassword(String uid, String oldMd5, String newMd5) {
        synchronized (LOCK) {
            if (uid == null || newMd5 == null || newMd5.isEmpty()) return false;
            List<Row> list = accounts();
            for (Row x : list) {
                if (x.uid.equals(uid)) {
                    if (oldMd5 == null || !x.passMd5.equals(oldMd5)) return false;
                    x.passMd5 = newMd5;
                    save(list);
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void touchLogin(String uid) {
        synchronized (LOCK) {
            if (uid == null || uid.isEmpty()) return;
            List<Row> list = accounts();
            boolean changed = false;
            for (Row x : list) {
                if (x.uid.equals(uid)) { x.lastLogin = System.currentTimeMillis(); changed = true; }
            }
            if (changed) save(list);
        }
    }

    /* ==================== IO ==================== */

    private void save(List<Row> list) {
        synchronized (LOCK) {
            StringBuilder b = new StringBuilder(64 + list.size() * 150);
            b.append("{\"accounts\":[");
            for (int i = 0; i < list.size(); i++) {
                Row r = list.get(i);
                if (i > 0) b.append(',');
                b.append("{\"email\":\"").append(JsonUtil.esc(r.email)).append('"')
                 .append(",\"uid\":\"").append(JsonUtil.esc(r.uid)).append('"')
                 .append(",\"token\":\"").append(JsonUtil.esc(r.token)).append('"')
                 .append(",\"passMd5\":\"").append(JsonUtil.esc(r.passMd5)).append('"')
                 .append(",\"createTime\":").append(r.createTime)
                 .append(",\"lastLogin\":").append(r.lastLogin).append('}');
            }
            b.append("]}");
            writeFile(new File(dataDir, "accounts.json"), b.toString());
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
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static void writeFile(File f, String s) {
        FileOutputStream out = null;
        try {
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            out = new FileOutputStream(tmp);
            out.write(s.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
            out.close();
            out = null;
            if (f.exists()) f.delete();
            tmp.renameTo(f);
        } catch (Throwable t) {
            Db.log("DB-WRITE failed: " + t);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
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
}
