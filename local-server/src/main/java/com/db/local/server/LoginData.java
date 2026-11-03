package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Data LOGIN LOKAL — sisi penyimpanan untuk server login lokal (LoginServer).
 *
 * 0.4.3: penyimpanan pindah ke DRAGONBALL.DB (SQLite — lihat Db.java):
 *
 *   kv "local_user"        — id guest lokal (dibuat sekali, tetap dipakai lagi)
 *   tabel accounts         — akun lokal (username PK · pass_md5 · dibuat ·
 *                            login terakhir · jumlah login) — dipakai BERSAMA
 *                            tahap SDK (daftar/masuk) & tahap game (loginGame)
 *   config/servers.json    — TETAP berupa file: daftar server GetServerList,
 *                            snapshot asli 2109 server, BOLEH diubah pemilik
 *                            tanpa rebuild (bukan data transaksional).
 *
 * Tanpa dependensi — skema respons = salinan server asli (README §2.10).
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

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    // ---------------- user guest lokal ----------------

    /**
     * Id guest lokal yang TAHAN LAMA: dibuat sekali, tersimpan di dragonball.db
     * (kv local_user), dipakai lagi setiap boot — progres akun lokal pemilik
     * tidak lepas (dulu file txt — ikut terhapus Cleaner tiap boot; kini aman).
     */
    public static String localUserId() {
        synchronized (LOCK) {
            try {
                String s = Db.i().kv(Db.KV_LOCAL_USER);
                if (s != null && s.length() >= 4 && s.length() <= 64) {
                    return s;
                }
                String uid = "guest" + (10000000L + (long) (Math.random() * 89999999L));
                Db.i().kvPut(Db.KV_LOCAL_USER, uid);
                return uid;
            } catch (Throwable t) {
                return "guest" + (10000000L + (long) (Math.random() * 89999999L));
            }
        }
    }

    // ---------------- akun lokal (tabel accounts) ----------------

    /** Password (sudah MD5) akun lokal; null = akun tidak ada. */
    public static String accountPassword(String userId) {
        synchronized (LOCK) {
            try {
                String[] r = Db.i().account(userId);
                return r == null ? null : r[0];
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /** Pastikan akun ada (tanpa menimpa password lama bila sudah terdaftar). */
    public static void ensureAccount(String userId, String passwordMd5) {
        synchronized (LOCK) {
            try {
                if (Db.i().account(userId) == null) {
                    String n = now();
                    Db.i().upsertAccount(userId, passwordMd5 == null ? "" : passwordMd5, n, n, 1);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** Catat login ulang (waktu terakhir + jumlah). */
    public static void touchAccount(String userId) {
        synchronized (LOCK) {
            try {
                String[] old = Db.i().account(userId);
                long n = 1;
                if (old != null) {
                    try {
                        n = Long.parseLong(old[3]) + 1;
                    } catch (Throwable ignored) {
                    }
                    Db.i().upsertAccount(userId, old[0], old[1], now(), n);
                } else {
                    String n2 = now();
                    Db.i().upsertAccount(userId, "", n2, n2, 1);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** Semua nama akun — untuk pencarian token autoLogin. */
    public static String[] accountNames() {
        synchronized (LOCK) {
            try {
                return Db.i().accountNames();
            } catch (Throwable t) {
                return new String[0];
            }
        }
    }

    /** Jumlah akun lokal (untuk dasbor). */
    public static int accountCount() {
        synchronized (LOCK) {
            try {
                return Db.i().accountCount();
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
            // 0.4.2 ALUR SDK: identitas mengikuti sesi SDK terakhir — H5 membawa userid
            // dari url setting bin (disajikan SEBELUM layar SDK boot ini), sedangkan
            // pemilik bisa memilih akun/tamu lain di layar SDK (loginchecknative sudah
            // dicatat). Jawab dengan identitas sesi SDK agar akun pilihan layar SDK
            // yang dipakai game — client tidak diubah sama sekali (README §2.11).
            String sdkUid = SdkData.lastKnownUid();
            if (sdkUid != null && sdkUid.length() > 0 && !sdkUid.equals(userId)) {
                try {
                    HumanLog.ev('ℹ', "loginGame: url minta \"" + userId + "\" — mengikuti sesi SDK: "
                            + sdkUid + " (pilihan layar SDK)", null);
                } catch (Throwable ignored) {
                }
                userId = sdkUid;
            }
            boolean isNew = false;
            try {
                String[] old = Db.i().account(userId);
                isNew = (old == null);
                String n = now();
                if (isNew) {
                    Db.i().upsertAccount(userId, password == null ? "" : password, n, n, 1);
                } else {
                    String pass = (password != null && password.length() > 0) ? password : old[0];
                    long cnt = 1;
                    try {
                        cnt = Long.parseLong(old[3]) + 1;
                    } catch (Throwable ignored) {
                    }
                    Db.i().upsertAccount(userId, pass, old[1], n, cnt);
                }
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
