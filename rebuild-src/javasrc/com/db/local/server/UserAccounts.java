package com.db.local.server;

import java.util.ArrayList;
import java.util.List;

/**
 * Bagian 28->29 — MULTI-ACCOUNT STORE (akun email terdaftar), kini di atas
 * lapisan Db (SQLite di device — keputusan owner "cari yg setara misal sqlite").
 *
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
 * Perubahan v29: storage pindah ke Db; bind = UPDATE in-place (uid/token tetap);
 * file legacy data/accounts.json dimigrasi DbSqlite otomatis. Password disimpan
 * PERSIS MD5 yang dikirim klien (server lokal, single device, bukan produksi).
 */
public final class UserAccounts {

    public static final class Rec {
        public String email;     // lowercase email / username
        public String uid;
        public String token;
        public String passMd5;
        public long createTime;
    }

     private static Db.Row toRow(Rec r) {
        Db.Row w = new Db.Row();
        w.uid = r.uid;
        w.username = r.email;
        w.email = r.email;
        w.passMd5 = r.passMd5;
        w.token = r.token;
        w.isGuest = false;
        w.createTime = r.createTime;
        w.lastLogin = r.createTime;
        return w;
    }

    private static Rec toRec(Db.Row w) {
        Rec r = new Rec();
        r.email = w.email;
        r.uid = w.uid;
        r.token = w.token;
        r.passMd5 = w.passMd5;
        r.createTime = w.createTime;
        return r;
    }

    private static Db db() {
        return Db.open(LoggerCore.serverRootPath);
    }

    /* ============================ READ ============================ */

    public static List<Rec> all() {
        List<Rec> out = new ArrayList<UserAccounts.Rec>();
        for (Db.Row w : db().accounts()) out.add(toRec(w));
        return out;
    }

    public static Rec find(String emailLower) {
        if (emailLower == null) return null;
        Db.Row w = db().byEmail(emailLower.trim().toLowerCase());
        return w == null ? null : toRec(w);
    }

    public static Rec findByUid(String uid) {
        if (uid == null || uid.isEmpty()) return null;
        Db.Row w = db().byUid(uid);
        return (w == null || w.isGuest) ? null : toRec(w);
    }

    public static Rec findByToken(String token) {
        if (token == null || token.isEmpty()) return null;
        Db.Row w = db().byToken(token);
        return (w == null || w.isGuest) ? null : toRec(w);
    }

    /* ============================ WRITE ============================ */

    /** Daftar akun baru. return null bila email sudah terdaftar / param salah. */
    public static Rec create(String emailLower, String passMd5) {
        synchronized (Db.LOCK) {
            String e = emailLower == null ? "" : emailLower.trim().toLowerCase();
            if (e.isEmpty() || e.indexOf('@') < 0 || passMd5 == null || passMd5.isEmpty()) return null;
            if (db().byEmail(e) != null) return null;
            Rec r = new Rec();
            r.createTime = System.currentTimeMillis();
            r.uid = newUid(r.createTime);
            r.token = AccountStore.randomToken();
            r.email = e;
            r.passMd5 = passMd5;
            if (!db().insertAccount(toRow(r))) return null;
            return r;
        }
    }

    /**
     * Bind email+sandi ke akun yang SUDAH ada (akun tamu -> email).
     * v29: UPDATE in-place di Db — uid+token PEMAIN TIDAK berubah (progres
     * game aman); setelah bind akun tamu menjadi akun email (is_guest=0).
     */
    public static Rec bindToExistingUid(String uid, String emailLower, String passMd5) {
        synchronized (Db.LOCK) {
            String e = emailLower == null ? "" : emailLower.trim().toLowerCase();
            if (uid == null || e.isEmpty() || e.indexOf('@') < 0
                    || passMd5 == null || passMd5.isEmpty()) return null;
            Db db = db();
            if (db.byEmail(e) != null) return null;        // email sudah dipakai akun lain
            // keamanan utk akun EMAIL yang sudah punya sandi: sandi lama harus cocok.
            // Akun tamu (isGuest) boleh bind dgn sandi BARU — semantik v13.
            // (byUid di DbJson tak melihat baris tamu; guest fallback ada di Db.bindToUid)
            Db.Row src = db.byUid(uid);
            if (src != null && !src.isGuest && src.passMd5 != null && !src.passMd5.isEmpty()
                    && !src.passMd5.equals(passMd5)) return null;
            if (!db.bindToUid(uid, e, passMd5)) return null;
            Db.Row w = db.byEmail(e);
            if (w == null) return null;
            Rec r = new Rec();
            r.email = w.email;
            r.uid = w.uid;
            r.token = w.token;
            r.passMd5 = w.passMd5;
            r.createTime = w.createTime;
            return r;
        }
    }

    /** Ganti sandi (uid + oldPassword MD5 + newPassword MD5). return false bila gagal. */
    public static boolean changePassword(String uid, String oldMd5, String newMd5) {
        synchronized (Db.LOCK) {
            if (uid == null || newMd5 == null || newMd5.isEmpty()) return false;
            return db().changePassword(uid, oldMd5 == null ? "" : oldMd5.toLowerCase(),
                    newMd5.toLowerCase());
        }
    }

    /* ============================ HELPER ============================ */

    private static String newUid(long now) {
        // uid numerik seperti akun tamu (ms), pastikan unik
        Db db = db();
        String uid = String.valueOf(now);
        while (db.byUid(uid) != null) uid = uid + "1";
        return uid;
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
