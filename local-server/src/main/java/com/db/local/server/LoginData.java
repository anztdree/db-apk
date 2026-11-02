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
 * Data LOGIN LOKAL — sisi penyimpanan untuk server login lokal (LoginServer).
 * Semua file hidup di &lt;root&gt;/config/ — dalam whitelist folder bersih (README §2):
 *
 *   config/local-user.txt  — id guest lokal (dibuat sekali, tetap dipakai lagi)
 *   config/accounts.txt    — daftar akun lokal, 1 baris = 1 akun (dipisah TAB):
 *                            userId · password · dibuat · login terakhir · jumlah login
 *   config/servers.json    — daftar server untuk action GetServerList; dibawa APK
 *                            (assets/server/login/servers.json — snapshot ASLI server
 *                            login popoh5, 2109 server) & diekstrak sekali oleh Payload;
 *                            file ini BOLEH diubah pemilik tanpa rebuild.
 *
 * Tanpa dependensi — parser/penulis manual, tahan salah format (fallback aman).
 */
public final class LoginData {
    private static final Object LOCK = new Object();
    private static volatile File confDir = null;
    private static volatile String cachedServersJson = null;
    private static volatile int cachedServerCount = -1;

    private LoginData() {
    }

    /** Folder config (dipanggil Starter setelah root siap). */
    public static void init(File configDir) {
        synchronized (LOCK) {
            confDir = configDir;
            cachedServersJson = null;
            cachedServerCount = -1;
        }
    }

    // ---------------- user guest lokal ----------------

    /**
     * Id guest lokal yang TAHAN LAMA: dibuat sekali, tersimpan config/local-user.txt,
     * dipakai lagi setiap boot — progres akun lokal pemilik tidak lepas.
     */
    public static String localUserId() {
        synchronized (LOCK) {
            try {
                File f = new File(confDir, "local-user.txt");
                if (f.exists()) {
                    String s = readText(f).trim();
                    if (s.length() >= 4 && s.length() <= 64) {
                        return s;
                    }
                }
                String uid = "guest" + (10000000L + (long) (Math.random() * 89999999L));
                writeText(f, uid + "\n");
                return uid;
            } catch (Throwable t) {
                return "guest" + (10000000L + (long) (Math.random() * 89999999L));
            }
        }
    }

    // ---------------- akun lokal ----------------

    /** Baris akun: [0]=userId [1]=password [2]=dibuat [3]=login terakhir [4]=jumlah login */
    private static String[] findAccount(String userId) throws Exception {
        File f = new File(confDir, "accounts.txt");
        if (!f.exists()) {
            return null;
        }
        String[] lines = readText(f).split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\t");
            if (p.length >= 1 && p[0].equals(userId)) {
                String[] out = new String[5];
                for (int j = 0; j < 5; j++) {
                    out[j] = (j < p.length && p[j].length() > 0) ? p[j] : "";
                }
                return out;
            }
        }
        return null;
    }

    private static void upsertAccount(String userId, String password, boolean created) throws Exception {
        File f = new File(confDir, "accounts.txt");
        StringBuilder sb = new StringBuilder();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String[] old = findAccount(userId);
        if (old != null) {
            String[] lines = readText(f).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.startsWith(userId + "\t")) {
                    String pass = (password != null && password.length() > 0) ? password : old[1];
                    long n = 1;
                    try {
                        n = Long.parseLong(old[4]) + 1;
                    } catch (Throwable ignored) {
                    }
                    sb.append(userId).append('\t').append(pass).append('\t')
                            .append(old[2]).append('\t').append(now).append('\t').append(n).append('\n');
                } else {
                    sb.append(line).append('\n');
                }
            }
        } else {
            String existing = "";
            try {
                existing = readText(f);
            } catch (Throwable ignored) {
            }
            sb.append(existing);
            if (existing.length() > 0 && !existing.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append(userId).append('\t')
                    .append(password == null ? "" : password).append('\t')
                    .append(now).append('\t').append(now).append("\t1\n");
        }
        writeText(f, sb.toString());
    }

    /** Jumlah akun lokal (untuk dasbor). */
    public static int accountCount() {
        synchronized (LOCK) {
            try {
                File f = new File(confDir, "accounts.txt");
                if (!f.exists()) {
                    return 0;
                }
                String[] lines = readText(f).split("\n");
                int n = 0;
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.length() > 0 && !line.startsWith("#")) {
                        n++;
                    }
                }
                return n;
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    // ---------------- action loginGame ----------------

    /**
     * Jawaban action "loginGame" — SKEMA PERSIS dari server asli (probe 2026-11-02,
     * README §2.10): loginToken, userId, nickName, headerImage, isCreated, errorCode,
     * errorMessage, channelCode. Ditambah sign/sdk/newUser yang dibaca jalur login
     * tanpa-SDK di main.min (doOriginLoginRequest → loginUserInfo.sign/sdk).
     * Login = daftar otomatis (akun baru dibuat saat pertama kali masuk).
     */
    public static String loginGameResponse(String userId, String password) {
        synchronized (LOCK) {
            boolean isNew = false;
            try {
                isNew = (findAccount(userId) == null);
                upsertAccount(userId, password, isNew);
            } catch (Throwable t) {
                try {
                    HumanLog.ev('⚠', "Penyimpanan akun login lokal gagal — login tetap dilayani tanpa catatan akun",
                            String.valueOf(t));
                } catch (Throwable ignored) {
                }
            }
            String sign = md5(userId + "|" + (password == null ? "" : password) + "|db-local");
            StringBuilder d = new StringBuilder(320);
            d.append('{')
                    .append(Json.str("loginToken", "local-" + sign.substring(0, Math.min(16, sign.length())))).append(',')
                    .append(Json.str("userId", userId)).append(',')
                    .append(Json.str("nickName", userId)).append(',')
                    .append(Json.str("headerImage", "")).append(',')
                    .append(Json.bool("isCreated", isNew)).append(',')
                    .append(Json.num("errorCode", 0)).append(',')
                    .append(Json.str("errorMessage", "")).append(',')
                    .append(Json.str("channelCode", "game_origin")).append(',')
                    .append(Json.str("sign", sign)).append(',')
                    .append(Json.str("sdk", "game_origin")).append(',')
                    .append(Json.bool("newUser", isNew))
                    .append('}');
            return d.toString();
        }
    }

    // ---------------- action GetServerList ----------------

    /**
     * Isi data untuk action "GetServerList" — baca config/servers.json (bawaan APK =
     * snapshot asli 2109 server hasil probe; BOLEH diganti pemilik tanpa rebuild).
     * Format yang dibaca H5 (main.min): {errorCode, serverList:[{serverId,url,name,
     * online,new,hot,openDate,offlineReason,merge,mergeTo,...}], history}.
     */
    public static String serverListData() {
        synchronized (LOCK) {
            if (cachedServersJson != null) {
                return cachedServersJson;
            }
            String raw = null;
            try {
                File f = new File(confDir, "servers.json");
                if (f.exists() && f.length() > 2) {
                    raw = readText(f);
                }
            } catch (Throwable ignored) {
            }
            if (raw == null || raw.indexOf("\"serverList\"") < 0) {
                // fallback darurat: satu server terbaru yang diketahui hidup
                raw = "{\"errorCode\":0,\"serverList\":[{\"serverId\":\"2109\","
                        + "\"url\":\"https://s2109-bs.popoh5.com:8061\",\"new\":true,\"hot\":false,"
                        + "\"name\":\"Server 2109\",\"online\":true,\"openDate\":\"2026-10-30T07:00:06.409Z\","
                        + "\"offlineReason\":\"\",\"merge\":false,\"mergeTo\":\"\","
                        + "\"urlInner\":\"http://127.0.0.1:8071\",\"chatInner\":\"\"}],\"history\":[]}";
                try {
                    HumanLog.ev('⚠', "config/servers.json tidak ada atau salah format — memakai daftar darurat 1 server",
                            null);
                } catch (Throwable ignored) {
                }
            }
            cachedServersJson = raw;
            cachedServerCount = countOccurrences(raw, "\"serverId\"");
            return raw;
        }
    }

    /** Jumlah server pada daftar aktif (untuk log & dasbor). */
    public static int serverCount() {
        synchronized (LOCK) {
            if (cachedServerCount < 0) {
                serverListData();
            }
            return cachedServerCount;
        }
    }

    // ---------------- util ----------------

    private static int countOccurrences(String s, String needle) {
        int n = 0;
        int i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private static String md5(String s) {
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

    private static void writeText(File f, String content) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
        w.write(content);
        w.close();
    }

    private static String readText(File f) throws Exception {
        Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
        StringBuilder sb = new StringBuilder(1024);
        char[] buf = new char[4096];
        int n;
        while ((n = r.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        r.close();
        return sb.toString();
    }
}
