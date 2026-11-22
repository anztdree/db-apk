package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Bagian 21 — penyimpanan akun tamu lokal yang PERSISTEN.
 *
 * Kontrak dari smali (bukti, bukan karangan):
 *  - psalmicvolleyball/cointhreat.cointruth(String) (parser session):
 *      authToken  getString  WAJIB
 *      tips       opsional
 *      userData   getJSONObject WAJIB → bean/QGUserData.generateFromJson:
 *                   username/uid/displayUid/token = getString WAJIB,
 *                   isGuest = getString("1"=guest), openType = optString,
 *                   isNewUser = optInt, isTrash = optInt
 *      checkRealName optString default "0"
 *  - service/QuickGameSdkService: /v1/user/registerVisitor (daftar tamu) &
 *    /v1/user/autoLogin (login token tersimpan) → kedua-duanya baca
 *    getBoolean("result") lalu getString("data") (STRING JSON session).
 *  - pursetruechild/nationalcommunitymissing (loginchecknative): body mentah
 *    disimpan sebagai thirdParams untuk JS, lalu divalidasi getInt("code")==0.
 *
 * Akun disimpan di <serverRoot>/data/account.json sehingga UID/TOKEN tetap sama
 * antar restart — perilaku "online" yang wajar (akun tamu menetap).
 */
public final class AccountStore {

    public String uid;
    public String token;
    public String username;
    public long createTime;
    public boolean isNewUser;

    /* ====================================================================
     * Bagian 28 — AKUN AKTIF SAAT INI (untuk lapisan game).
     * Diisi dari query params loginchecknative (uid/username/token yang
     * dikirim SDK = PERSIS akun yang login: tamu ATAU email). enterGame /
     * loginGame memakai ini supaya payload game sesuai akun yang benar.
     * Single-device: statik volatile cukup; fallback = akun tamu.
     * ==================================================================== */
    public static volatile String curUid      = null;
    public static volatile String curUsername = null;
    public static volatile String curToken    = null;
    public static volatile String curUsermode = null;   // "1"=tamu, "0"=email
    public static volatile long   curAt       = 0L;

    /** set akun aktif dari param loginchecknative (params boleh kosong). */
    public static void setCurrent(String uid, String username, String token, String usermode) {
        if (uid == null || uid.isEmpty()) return;
        curUid = uid;
        curUsername = (username == null || username.isEmpty()) ? uid : username;
        curToken = (token == null || token.isEmpty()) ? "" : token;
        curUsermode = (usermode == null || usermode.isEmpty()) ? "1" : usermode;
        curAt = System.currentTimeMillis();
    }

    /** akun aktif, fallback ke akun tamu bila belum ada. */
    public static AccountStore currentOrDefault() {
        AccountStore g = loadOrCreate();
        if (curUid == null || curUid.equals(g.uid)) return g;
        // akun email yang aktif
        AccountStore a = new AccountStore();
        a.uid = curUid;
        a.username = curUsername;
        a.token = curToken == null || curToken.isEmpty() ? g.token : curToken;
        a.createTime = 0L;
        a.isNewUser = false;
        return a;
    }

    private static final Object LOCK = new Object();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static AccountStore loadOrCreate() {
        synchronized (LOCK) {
            AccountStore a = read();
            if (a != null) {
                a.isNewUser = false;
                return a;
            }
            a = new AccountStore();
            a.createTime = System.currentTimeMillis();
            a.uid = String.valueOf(a.createTime);
            a.token = randomToken();
            a.username = "Guest" + (1000 + new Random().nextInt(9000));
            a.isNewUser = true;
            write(a);
            log("AKUN-BARU uid=" + a.uid + " username=" + a.username);
            return a;
        }
    }

    private static File file() {
        String root = LoggerCore.serverRootPath;
        File dir = new File(root == null || root.isEmpty()
                ? "." : root, "data");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "account.json");
    }

    private static AccountStore read() {
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
            String s = new String(buf, 0, off, StandardCharsets.UTF_8);
            AccountStore a = new AccountStore();
            a.uid = jsonStr(s, "uid");
            a.token = jsonStr(s, "token");
            a.username = jsonStr(s, "username");
            a.createTime = jsonLong(s, "createTime");
            if (a.uid == null || a.token == null || a.uid.isEmpty() || a.token.isEmpty()) {
                return null;
            }
            log("AKUN-LOAD uid=" + a.uid + " username=" + a.username);
            return a;
        } catch (Throwable t) {
            log("AKUN-READ-GAGAL " + t);
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static void write(AccountStore a) {
        FileOutputStream out = null;
        try {
            String s = "{\"uid\":\"" + JsonUtil.esc(a.uid) + "\",\"token\":\""
                    + JsonUtil.esc(a.token) + "\",\"username\":\""
                    + JsonUtil.esc(a.username) + "\",\"createTime\":" + a.createTime + "}";
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            File tmp = new File(file().getAbsolutePath() + ".tmp");
            out = new FileOutputStream(tmp);
            out.write(b);
            out.flush();
            out.getFD().sync();
            out.close();
            out = null;
            File f = file();
            if (f.exists()) f.delete();
            tmp.renameTo(f);
        } catch (Throwable t) {
            log("AKUN-WRITE-GAGAL " + t);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    /** Ambil satu nilai string dari JSON kecil tanpa parser penuh. */
    static String jsonStr(String s, String key) {
        int i = s.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        i = s.indexOf('"', s.indexOf(':', i) + 1);
        if (i < 0) return null;
        StringBuilder b = new StringBuilder();
        for (int k = i + 1; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\') { k++; if (k < s.length()) b.append(s.charAt(k)); continue; }
            if (c == '"') break;
            b.append(c);
        }
        return b.toString();
    }

    static long jsonLong(String s, String key) {
        try {
            int i = s.indexOf("\"" + key + "\"");
            if (i < 0) return 0;
            i = s.indexOf(':', i) + 1;
            int e = i;
            while (e < s.length() && ",}".indexOf(s.charAt(e)) < 0) e++;
            return Long.parseLong(s.substring(i, e).trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    static String randomToken() {
        Random r = new Random();
        StringBuilder b = new StringBuilder(32);
        for (int i = 0; i < 32; i++) b.append(HEX[r.nextInt(16)]);
        return b.toString();
    }

    /** Session JSON — PERSIS kontrak parser psalmicvolleyball/cointhreat. */
    public String sessionJson(boolean newUser) {
        return sessionJsonOf(uid, username, token, "12", "1", newUser);
    }

    /**
     * Bagian 28 — session untuk akun APA PUN.
     * isGuest: "1"=tamu (openType 12) | "0"=email (openType 1).
     * isNewUser SELALU 0 (flag tracking SDK — jalur AppsFlyer/Adjust/FB
     * dilewati SDK sendiri; terbukti v8: isNewUser=1 offline = popup stuck).
     */
    public static String sessionJsonOf(String uidX, String uname, String tok,
                                       String openType, String isGuest, boolean newUser) {
        return "{\"authToken\":\"" + JsonUtil.esc(tok) + "\",\"tips\":\"\","
                + "\"checkRealName\":\"0\",\"userData\":{"
                + "\"uid\":\"" + JsonUtil.esc(uidX) + "\","
                + "\"username\":\"" + JsonUtil.esc(uname) + "\","
                + "\"displayUid\":\"" + JsonUtil.esc(uidX) + "\","
                + "\"token\":\"" + JsonUtil.esc(tok) + "\","
                + "\"isGuest\":\"" + isGuest + "\",\"openType\":\"" + openType + "\","
                + "\"isNewUser\":" + (newUser ? 1 : 0) + ",\"isTrash\":0}}";
    }

    /** Respons SDK standar utk akun apa pun: {result:true, data:"<session string>"}. */
    public static String sessionResponse(String uidX, String uname, String tok,
                                         String openType, String isGuest) {
        String inner = sessionJsonOf(uidX, uname, tok, openType, isGuest, false);
        return "{\"result\":true,\"data\":\"" + JsonUtil.esc(inner) + "\"}";
    }

    /** Respons SDK standar: {result:true, data:"<session string>"}
     *  PENTING: esc() SEKALI saja — data adalah STRING JSON; klien parse
     *  getString("data") → new JSONObject(data) (psalmicvolleyball/cointhreat). */
    public String sdkDataResponse(boolean newUser) {
        String inner = sessionJson(newUser);
        return "{\"result\":true,\"data\":\"" + JsonUtil.esc(inner) + "\"}";
    }

    /**
     * thirdParams loginchecknative — body mentah disimpan Java sebagai
     * thirdParams untuk JS (index-native.html getSdkLoginInfo):
     *   flat → {sdk,userid,userId,nickname,nickName,securityCode,loginToken,sign}
     * code:0 WAJIB (getInt("code")==0 → customreading() → WebView lanjut).
     */
    public String loginCheckNative() {
        return loginCheckNativeOf(uid, username, token, "1");
    }

    /**
     * Bagian 28 — thirdParams utk akun APA PUN. uid/username/token datang
     * dari QUERY PARAMS SDK (pursetruechild/feedtonight/singersmooth
     * .onLoginFinished mengirim PERSIS userData akun yang login — bukti smali
     * cointhreat/cointhreat/cointhreat/pursetruechild/feedtonight/singersmooth
     * : uid=getUid(), username=getUserName(), token=getToken(),
     * usermode = isGuest()?"1":"0").
     */
    public static String loginCheckNativeOf(String uidX, String uname, String tok, String usermode) {
        if (uidX == null || uidX.isEmpty()) { AccountStore g = loadOrCreate(); uidX = g.uid; }
        if (uname == null || uname.isEmpty()) uname = uidX;
        if (tok == null || tok.isEmpty()) tok = "local";
        if (usermode == null || usermode.isEmpty()) usermode = "1";
        return "{\"code\":0,\"sdk\":\"quick\","
                + "\"userid\":\"" + JsonUtil.esc(uidX) + "\","
                + "\"userId\":\"" + JsonUtil.esc(uidX) + "\","
                + "\"nickname\":\"" + JsonUtil.esc(uname) + "\","
                + "\"nickName\":\"" + JsonUtil.esc(uname) + "\","
                + "\"securityCode\":\"" + JsonUtil.esc(tok) + "\","
                + "\"security\":\"" + JsonUtil.esc(tok) + "\","
                + "\"loginToken\":\"" + JsonUtil.esc(tok) + "\","
                + "\"sign\":\"local\",\"usermode\":\"" + usermode + "\",\"osType\":\"android\"}";
    }

    private static void log(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_SDK;
        e.kind = "SYS";
        e.method = "SYS";
        e.path = msg;
        e.status = 0;
        LoggerCore.log(e);
    }
}
