package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * DATA TAHAP SDK — simpanan untuk API quickgame/zhuhuan (server SDK lokal, 0.4.2).
 *
 * SEMUA SKEMA RESPONS = HASIL PROBE SERVER ASLI qsdk.t4game.com (2026-11-02,
 * body {"sv":"v2","data":<base64 JSON param>,"sign":<md5>}, rahasia tanda
 * "0b2a18e45d7df321" dari dex — README §2.11). Tidak ada yang dikarang:
 *
 *   /v1/system/init        → data persis respons asli (versi "empty", productConfig,
 *                            loginTypes ["1","13"], nodes, dst — salinan respons asli)
 *   /v1/user/registerVisitor → tamu: {authToken, userData:{uid, username, token,
 *                            isGuest, isNewUser, mobile, bindInfo{12 entri}}, checkRealName}
 *                            — server asli: deviceId sama = tamu SAMA (isNewUser 0)
 *   /v1/user/autoLogin     → validasi authToken sesi tersimpan
 *   /v1/user/loginByName   → username + password (MD5) vs config/accounts.txt
 *   /v1/user/registerUser  → buat akun lokal (password MD5 disimpan)
 *   /v1/user/ckRegVistor   → {isReged:1} bila authToken dikenal
 *
 * File (di <root>/config/ — whitelist folder bersih):
 *   sdk-guests.txt  — pemetaan deviceId → guest (deviceId · uid · dibuat)
 *   sdk-session.txt — sesi SDK terakhir (authToken · uid · username · isGuest · waktu)
 *   sdk-last.txt    — hasil loginchecknative terakhir (uid · username · waktu)
 *                     — dipakai SettingBin untuk userid url H5 (identitas SDK).
 *
 * Identitas: uid == username (satu nama untuk tahap SDK & tahap game) sehingga
 * loginGame(username=<uid>, password "game_origin") di server login lokal selalu
 * mendarat di akun yang SAMA. Tamu pertama MENG migratedkan config/local-user.txt
 * (guest lama pemilik tidak hilang — progres lekat).
 */
public final class SdkData {
    private static final Object LOCK = new Object();
    private static volatile File confDir = null;

    private SdkData() {
    }

    /** Dipanggil Starter setelah folder config siap. */
    public static void init(File configDir) {
        synchronized (LOCK) {
            confDir = configDir;
        }
    }

    // ---------------- sesi & token ----------------

    /** Token sesi stabil per (akun, perangkat) — tahan proses mati/hidup (model.b client menyimpan last_token). */
    private static String tokenFor(String uid, String deviceId) {
        return md5("sdk-local|" + uid + "|" + (deviceId == null ? "" : deviceId));
    }

    /** JSON "userData" + authToken — SKEMA PERSIS respons asli (probe §2.11). */
    private static String sessionData(String authToken, String uid, boolean guest, boolean newUser) {
        StringBuilder d = new StringBuilder(1024);
        d.append('{')
                .append(Json.str("authToken", authToken)).append(',')
                .append("\"userData\":{")
                .append(Json.str("uid", uid)).append(',')
                .append(Json.str("username", uid)).append(',')
                .append(Json.str("token", authToken)).append(',')
                .append("\"isGuest\":").append(guest ? 1 : 0).append(',')
                .append("\"isNewUser\":").append(newUser ? 1 : 0).append(',')
                .append(Json.str("mobile", "")).append(',')
                .append("\"bindInfo\":{")
                .append("\"bindFB\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":6,\"buid\":\"\"},").append("\"bindGameCenter\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":7,\"buid\":\"\"},").append("\"bindGoogle\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":8,\"buid\":\"\"},").append("\"bindEmail\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":13,\"buid\":\"\"},").append("\"bindNaver\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":9,\"buid\":\"\"},").append("\"bindTwitter\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":10,\"buid\":\"\"},").append("\"bindLine\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":11,\"buid\":\"\"},").append("\"bindVK\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":14,\"buid\":\"\"},").append("\"signApple\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":16,\"buid\":\"\"},").append("\"bindPlay\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":17,\"buid\":\"\"},").append("\"bd94hi\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":18,\"buid\":\"\"},").append("\"bdhw\":{\"isBind\":0,\"otherAccountName\":\"\",\"bid\":19,\"buid\":\"\"}")
                .append('}')
                .append('}').append(',')
                .append("\"checkRealName\":0")
                .append('}');
        saveSession(authToken, uid, guest);
        return d.toString();
    }

    private static void saveSession(String authToken, String uid, boolean guest) {
        try {
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            write(new File(confDir, "sdk-session.txt"),
                    authToken + "\t" + uid + "\t" + (guest ? "1" : "0") + "\t" + now + "\n");
        } catch (Throwable ignored) {
        }
    }

    /** Simpan hasil loginchecknative (uid terakhir untuk userid url H5). */
    private static void saveLast(String uid, String username) {
        try {
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            write(new File(confDir, "sdk-last.txt"), uid + "\t" + username + "\t" + now + "\n");
        } catch (Throwable ignored) {
        }
    }

    // ---------------- endpoint: tamu (registerVisitor) ----------------

    /**
     * Tamu baru/lama untuk deviceId — meniru server asli: deviceId sama = tamu sama.
     * Tamu pertama memakai guest tahan-lama config/local-user.txt (migrasi identitas
     * pemilik; progres game yang sudah ada tetap lekat).
     */
    public static String registerVisitor(String deviceId, String clientLang) {
        synchronized (LOCK) {
            try {
                String dev = (deviceId == null || deviceId.length() == 0) ? "unknown" : deviceId;
                File f = new File(confDir, "sdk-guests.txt");
                String[] lines = read(f).split("\n");
                for (String line : lines) {
                    String p = line.trim();
                    if (p.length() == 0 || p.startsWith("#")) {
                        continue;
                    }
                    String[] c = p.split("\t");
                    if (c.length >= 2 && c[0].equals(dev)) {
                        String uid = c[1];
                        LoginData.ensureAccount(uid, "");
                        return sessionData(tokenFor(uid, dev), uid, true, false);
                    }
                }
                // tamu baru — migrasikan guest lokal pemilik untuk perangkat pertama
                boolean first = true;
                for (String line : lines) {
                    String q = line.trim();
                    if (q.length() > 0 && !q.startsWith("#")) {
                        first = false;
                        break;
                    }
                }
                String uid = first ? LoginData.localUserId()
                        : "guest" + (10000000L + (long) (Math.random() * 89999999L));
                LoginData.ensureAccount(uid, "");
                StringBuilder sb = new StringBuilder(read(f));
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                sb.append(dev).append('\t').append(uid).append('\t').append(now).append('\n');
                write(f, sb.toString());
                try {
                    HumanLog.ev('▶', "SDK: tamu BARU " + uid + " terdaftar (perangkat " + shortId(dev) + ")"
                            + (first ? " — memakai guest lokal yang sudah ada" : ""), null);
                } catch (Throwable ignored) {
                }
                return sessionData(tokenFor(uid, dev), uid, true, true);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    // ---------------- endpoint: autoLogin ----------------

    /** Validasi authToken sesi tersimpan; null bila tidak dikenal (error 40005 server asli). */
    public static String autoLogin(String authToken, String deviceId) {
        synchronized (LOCK) {
            if (authToken == null || authToken.length() < 8) {
                return null;
            }
            try {
                String[] p = read(new File(confDir, "sdk-session.txt")).trim().split("\t");
                if (p.length >= 3 && p[0].equals(authToken)) {
                    return sessionData(authToken, p[1], "1".equals(p[2]), false);
                }
                // token lama berbasis (uid, device) — terima bila polanya cocok
                String[] lines = read(new File(confDir, "sdk-guests.txt")).split("\n");
                for (String line : lines) {
                    String q = line.trim();
                    if (q.length() == 0 || q.startsWith("#")) {
                        continue;
                    }
                    String[] c = q.split("\t");
                    if (c.length >= 2 && tokenFor(c[1], c[0]).equals(authToken)) {
                        LoginData.ensureAccount(c[1], "");
                        return sessionData(authToken, c[1], true, false);
                    }
                }
                String[] acc = LoginData.allAccounts();
                for (String line : acc) {
                    int tab = line.indexOf('\t');
                    String uid = tab > 0 ? line.substring(0, tab) : line;
                    if (uid.length() > 0 && tokenFor(uid, deviceId).equals(authToken)) {
                        LoginData.ensureAccount(uid, "");
                        return sessionData(authToken, uid, uid.startsWith("guest"), false);
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    // ---------------- endpoint: loginByName / registerUser ----------------

    /** Login akun+password (password SUDAH MD5 dari client). null = gagal (40101 server asli). */
    public static String loginByName(String username, String passwordMd5) {
        synchronized (LOCK) {
            if (username == null || username.length() == 0) {
                return null;
            }
            String stored = LoginData.accountPassword(username);
            if (stored == null) {
                return null;
            }
            if (stored.length() > 0 && passwordMd5 != null && !stored.equalsIgnoreCase(passwordMd5)) {
                return null;
            }
            LoginData.touchAccount(username);
            saveLast(username, username);
            return sessionData(tokenFor(username, null), username, false, false);
        }
    }

    /** Daftar akun baru; null bila nama sudah dipakai (gagal — pesan Indonesia seperti server asli). */
    public static String registerUser(String username, String passwordMd5) {
        synchronized (LOCK) {
            if (username == null || username.length() < 3 || username.length() > 32) {
                return null;
            }
            for (int i = 0; i < username.length(); i++) {
                char c = username.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                        || c == '_' || c == '.' || c == '-';
                if (!ok) {
                    return null;
                }
            }
            if (LoginData.accountPassword(username) != null) {
                return null;
            }
            LoginData.ensureAccount(username, passwordMd5 == null ? "" : passwordMd5);
            try {
                HumanLog.ev('▶', "SDK: akun BARU terdaftar — " + username, null);
            } catch (Throwable ignored) {
            }
            saveLast(username, username);
            return sessionData(tokenFor(username, null), username, false, true);
        }
    }

    // ---------------- endpoint: ckRegVistor ----------------

    /** {isReged:1} bila authToken dikenal (respons asli: data objek {isReged:1}). */
    public static String ckRegVistor(String authToken) {
        synchronized (LOCK) {
            boolean known = false;
            try {
                String[] p = read(new File(confDir, "sdk-session.txt")).trim().split("\t");
                if (p.length >= 3 && p[0].equals(authToken)) {
                    known = true;
                }
            } catch (Throwable ignored) {
            }
            if (!known && authToken != null && authToken.length() == 32) {
                known = true; // token lokal stabil berformat md5 — periksa pola saja (tahan pembersihan config)
            }
            return known ? "{\"isReged\":1}" : null;
        }
    }

    // ---------------- loginchecknative (validasi shell → url H5) ----------------

    /**
     * Validasi login shell (uid/username/token/usermode) — SKEMA RESPONS PERSIS
     * server asli login.popoh5.com:510 (probe §2.11):
     *   {"code":0,"sdk":"BSNative","nickName":…,"loginToken":…,"userId":…,"sign":…,
     *    "sdkType":"BS","osType":"Android"}
     * Token tidak cocok tapi akun dikenal → tetap diterima (⚠ tercatat) agar boot
     * tidak pernah terkunci; akun tak dikenal → null (shell menampilkan pesan server).
     */
    public static String checkNative(String uid, String username, String token, String usermode) {
        synchronized (LOCK) {
            if (uid == null || uid.length() == 0) {
                return null;
            }
            boolean guest = "1".equals(usermode) || uid.startsWith("guest");
            boolean tokenOk = false;
            try {
                String[] p = read(new File(confDir, "sdk-session.txt")).trim().split("\t");
                if (p.length >= 3 && p[0].equals(token) && p[1].equals(uid)) {
                    tokenOk = true;
                }
            } catch (Throwable ignored) {
            }
            if (!tokenOk && token != null && token.length() >= 8) {
                // token stabil per (uid, device) — hitung ulang dari pemetaan tamu
                try {
                    String[] lines = read(new File(confDir, "sdk-guests.txt")).split("\n");
                    for (String line : lines) {
                        String q = line.trim();
                        if (q.length() == 0 || q.startsWith("#")) {
                            continue;
                        }
                        String[] c = q.split("\t");
                        if (c.length >= 2 && c[1].equals(uid) && tokenFor(c[1], c[0]).equals(token)) {
                            tokenOk = true;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            boolean accountKnown = LoginData.accountPassword(uid) != null || uid.equals(LoginData.localUserId());
            if (!tokenOk && !accountKnown) {
                return null;
            }
            if (!tokenOk) {
                try {
                    HumanLog.ev('⚠', "SDK: token loginchecknative tidak cocok untuk " + uid
                            + " — diterima karena akun dikenal lokal", null);
                } catch (Throwable ignored) {
                }
            }
            LoginData.ensureAccount(uid, "");
            String loginToken = md5("lt|" + uid + "|" + System.currentTimeMillis());
            String sign = md5(uid + "|lsf-local-sign");
            saveSession(token == null || token.length() == 0 ? tokenFor(uid, null) : token, uid, guest);
            saveLast(uid, username == null || username.length() == 0 ? uid : username);
            try {
                HumanLog.ev('✔', "SDK: loginchecknative OK — " + (guest ? "tamu " : "akun ") + uid
                        + " (mode " + (guest ? "tamu" : "akun") + ")", null);
            } catch (Throwable ignored) {
            }
            StringBuilder d = new StringBuilder(256);
            d.append('{')
                    .append("\"code\":0,").append(Json.str("sdk", "BSNative")).append(',')
                    .append(Json.str("nickName", username == null ? uid : username)).append(',')
                    .append(Json.str("loginToken", loginToken)).append(',')
                    .append(Json.str("userId", uid)).append(',')
                    .append(Json.str("sign", sign)).append(',')
                    .append(Json.str("sdkType", "BS")).append(',')
                    .append(Json.str("osType", "Android"))
                    .append('}');
            return d.toString();
        }
    }

    // ---------------- untuk SettingBin & dasbor ----------------

    /** Uid dari sesi/loginchecknative TERAKHIR — dipakai SettingBin (userid url H5). null bila belum ada. */
    public static String lastKnownUid() {
        synchronized (LOCK) {
            try {
                String[] p = read(new File(confDir, "sdk-last.txt")).trim().split("\t");
                if (p.length >= 1 && p[0].length() >= 4) {
                    return p[0];
                }
            } catch (Throwable ignored) {
            }
            try {
                String[] p = read(new File(confDir, "sdk-session.txt")).trim().split("\t");
                if (p.length >= 2 && p[1].length() >= 4) {
                    return p[1];
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    /** Jumlah tamu terdaftar (deviceId unik) — dasbor. */
    public static int guestDeviceCount() {
        synchronized (LOCK) {
            try {
                String[] lines = read(new File(confDir, "sdk-guests.txt")).split("\n");
                int n = 0;
                for (String line : lines) {
                    String q = line.trim();
                    if (q.length() > 0 && !q.startsWith("#")) {
                        n++;
                    }
                }
                return n;
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    /** Uid tamu aktif (baris pertama pemetaan) — dasbor; null bila belum ada. */
    public static String primaryGuest() {
        synchronized (LOCK) {
            try {
                String[] lines = read(new File(confDir, "sdk-guests.txt")).split("\n");
                for (String line : lines) {
                    String q = line.trim();
                    if (q.length() > 0 && !q.startsWith("#")) {
                        String[] c = q.split("\t");
                        if (c.length >= 2) {
                            return c[1];
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    // ---------------- util ----------------

    private static String shortId(String s) {
        if (s == null) {
            return "?";
        }
        return s.length() <= 10 ? s : s.substring(0, 10) + "…";
    }

    static String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < b.length; i++) {
                sb.append(Character.forDigit((b[i] >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b[i] & 0xf, 16));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "00000000000000000000000000000000";
        }
    }

    private static void write(File f, String content) throws Exception {
        if (confDir != null) {
            confDir.mkdirs();
        }
        Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
        w.write(content);
        w.close();
    }

    private static String read(File f) throws Exception {
        if (!f.exists()) {
            return "";
        }
        Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
        StringBuilder sb = new StringBuilder(512);
        char[] buf = new char[2048];
        int n;
        while ((n = r.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        r.close();
        return sb.toString();
    }
}
