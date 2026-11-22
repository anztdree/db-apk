package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bagian 28 — MULTI-ACCOUNT STORE untuk SDK FULL-FITUR.
 *
 * Backing  : <serverRoot>/data/accounts.json — daftar akun terdaftar
 *            (email + passMd5 + uid + token + createTime), atomic tmp+rename
 *            (pola sama dengan AccountStore).
 * Sumber kontrak (bukti smali, BUKAN karangan):
 *  - dishbesideshockey$ironoriginhoblike  -> POST /v1/user/registerUser
 *        params: vcode (boleh absen), username (= EMAIL UI daftar),
 *                password = MD5 hex (unnecessarysperrylites.seventygenom.singersmooth)
 *  - dishbesideshockey$gangclothing       -> POST /v1/user/loginByName
 *        params: username, password (MD5 hex)
 *  - QuickGameSdkService                  -> POST /v1/user/autoLogin
 *        params: authToken (token tersimpan klien)
 *  - dishbesideshockey$reweavingsiamesedpropertylessnesses -> /v1/auth/bindMail
 *        params: uid, email, code, password (MD5)  [bind akun tamu -> email]
 *  - dishbesideshockey$famediscussionsmall -> /v1/auth/changePassword
 *        params: uid, oldPassword (MD5), newPassword (MD5)
 *  - Envelope error = format live server asli:
 *        {"result":false,"data":[],"error":{"id":N,"message":"..."}}
 *  - Session = kontrak parser psalmicvolleyball/cointhreat (authToken+userData
 *        wajib; username/uid/displayUid/token getString WAJIB).
 *
 * Catatan keamanan-akal: password disimpan PERSIS MD5 yang dikirim klien
 * (server lokal, single device, bukan produksi).
 */
public final class UserAccounts {

    public static final class Rec {
        public String email;     // lowercase email / username
        public String uid;
        public String token;
        public String passMd5;
        public long createTime;
    }

    private static final Object LOCK = new Object();

    /* ============================ READ ============================ */

    public static List<Rec> all() {
        synchronized (LOCK) {
            List<Rec> out = new ArrayList<UserAccounts.Rec>();
            String s = readFile();
            if (s == null) return out;
            // {"accounts":[{...},{...}]} — parser kecil tanpa org.json
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
                Rec r = new Rec();
                r.email      = AccountStore.jsonStr(obj, "email");
                r.uid        = AccountStore.jsonStr(obj, "uid");
                r.token      = AccountStore.jsonStr(obj, "token");
                r.passMd5    = AccountStore.jsonStr(obj, "passMd5");
                r.createTime = AccountStore.jsonLong(obj, "createTime");
                if (r.email != null && r.uid != null && r.token != null
                        && !r.email.isEmpty() && !r.uid.isEmpty()) {
                    out.add(r);
                }
                i = close + 1;
            }
            return out;
        }
    }

    public static Rec find(String emailLower) {
        if (emailLower == null) return null;
        String e = emailLower.trim().toLowerCase();
        for (Rec r : all()) if (r.email.equals(e)) return r;
        return null;
    }

    public static Rec findByUid(String uid) {
        if (uid == null || uid.isEmpty()) return null;
        for (Rec r : all()) if (r.uid.equals(uid)) return r;
        return null;
    }

    public static Rec findByToken(String token) {
        if (token == null || token.isEmpty()) return null;
        for (Rec r : all()) if (r.token.equals(token)) return r;
        return null;
    }

    /* ============================ WRITE ============================ */

    /** Daftar akun baru. return null bila email sudah terdaftar / param salah. */
    public static Rec create(String emailLower, String passMd5) {
        synchronized (LOCK) {
            String e = emailLower == null ? "" : emailLower.trim().toLowerCase();
            if (e.isEmpty() || e.indexOf('@') < 0 || passMd5 == null || passMd5.isEmpty()) return null;
            if (find(e) != null) return null;
            Rec r = new Rec();
            r.createTime = System.currentTimeMillis();
            r.uid = newUid(r.createTime);
            r.token = AccountStore.randomToken();
            r.email = e;
            r.passMd5 = passMd5;
            List<Rec> list = all();
            list.add(r);
            save(list);
            return r;
        }
    }

    /** Bind email+sandi ke akun yang SUDAH ada (akun tamu -> email, uid/token tetap). */
    public static Rec bindToExistingUid(String uid, String emailLower, String passMd5) {
        synchronized (LOCK) {
            String e = emailLower == null ? "" : emailLower.trim().toLowerCase();
            if (uid == null || e.isEmpty() || e.indexOf('@') < 0 || passMd5 == null || passMd5.isEmpty()) return null;
            if (find(e) != null) return null;               // email sudah dipakai akun lain
            Rec src = findByUid(uid);
            if (src != null && !src.passMd5.equals(passMd5)) return null; // sandi tidak cocok
            Rec r = new Rec();
            r.email = e;
            r.passMd5 = passMd5;
            if (src != null) { r.uid = src.uid; r.token = src.token; r.createTime = src.createTime; }
            else {
                AccountStore g = AccountStore.loadOrCreate();
                if (uid.equals(g.uid)) { r.uid = g.uid; r.token = g.token; r.createTime = System.currentTimeMillis(); }
                else return null;
            }
            List<Rec> list = all();
            list.add(r);
            save(list);
            return r;
        }
    }

    /** Ganti sandi (uid + oldPassword MD5 + newPassword MD5). return false bila gagal. */
    public static boolean changePassword(String uid, String oldMd5, String newMd5) {
        synchronized (LOCK) {
            if (uid == null || newMd5 == null || newMd5.isEmpty()) return false;
            List<Rec> list = all();
            for (Rec r : list) {
                if (r.uid.equals(uid)) {
                    if (oldMd5 == null || !r.passMd5.equals(oldMd5)) return false;
                    r.passMd5 = newMd5;
                    save(list);
                    return true;
                }
            }
            return false;
        }
    }

    /* ============================ HELPER ============================ */

    private static String newUid(long now) {
        // uid numerik seperti akun tamu (ms), pastikan unik
        String uid = String.valueOf(now);
        while (findByUid(uid) != null) uid = uid + "1";
        return uid;
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

    private static File file() {
        String root = LoggerCore.serverRootPath;
        File dir = new File(root == null || root.isEmpty() ? "." : root, "data");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "accounts.json");
    }

    private static String readFile() {
        File f = file();
        if (!f.exists()) return null;
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
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static void save(List<Rec> list) {
        FileOutputStream out = null;
        try {
            StringBuilder b = new StringBuilder(64 + list.size() * 140);
            b.append("{\"accounts\":[");
            for (int i = 0; i < list.size(); i++) {
                Rec r = list.get(i);
                if (i > 0) b.append(',');
                b.append("{\"email\":\"").append(JsonUtil.esc(r.email)).append('"')
                 .append(",\"uid\":\"").append(JsonUtil.esc(r.uid)).append('"')
                 .append(",\"token\":\"").append(JsonUtil.esc(r.token)).append('"')
                 .append(",\"passMd5\":\"").append(JsonUtil.esc(r.passMd5)).append('"')
                 .append(",\"createTime\":").append(r.createTime).append('}');
            }
            b.append("]}");
            byte[] bb = b.toString().getBytes(StandardCharsets.UTF_8);
            File tmp = new File(file().getAbsolutePath() + ".tmp");
            out = new FileOutputStream(tmp);
            out.write(bb);
            out.flush();
            out.getFD().sync();
            out.close();
            out = null;
            File f = file();
            if (f.exists()) f.delete();
            tmp.renameTo(f);
        } catch (Throwable t) {
            // diam — server lokal
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    static void sysLog(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_SDK;
        e.kind = "SYS";
        e.method = "SYS";
        e.path = msg;
        e.status = 0;
        LoggerCore.log(e);
    }
}
