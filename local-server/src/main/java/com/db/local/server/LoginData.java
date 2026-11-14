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
 *   config-offline/servers.json — 0.5.3: daftar server GetServerList = paket
 *                            MANUAL bawaan APK (1 entri "Server Lokal" url
 *                            127.0.0.1) — BUKAN snapshot online (owner: online
 *                            hanya jejak arah, bukan config asli offline).
 *                            Dilayani apa adanya; bukan data transaksional.
 *
 * Tanpa dependensi — skema respons mengikuti protokol asli (jejak arah).
 */
public final class LoginData {
    private static final Object LOCK = new Object();
    private static volatile File confDir = null;
    /** 0.5.3 — folder paket CONFIG OFFLINE manual (sumber daftar server). */
    private static volatile File offlineDir = null;
    private static volatile String cachedServersJson = null;
    private static volatile int cachedServerCount = -1;

    private LoginData() {
    }

    /** Folder config (dipanggil Starter setelah root siap). */
    public static void init(File configDir) {
        init(configDir, null);
    }

    /**
     * 0.5.3 — folder config (data transaksional) + folder paket CONFIG OFFLINE
     * manual (sumber daftar server GetServerList). Dipanggil Starter setelah
     * root siap; harness memanggil ini dengan dua folder uji.
     */
    public static void init(File configDir, File offlinePkgDir) {
        synchronized (LOCK) {
            confDir = configDir;
            offlineDir = offlinePkgDir;
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
                    HumanLog.ev("ℹ️", "loginGame minta \"" + userId + "\" — dijawab dengan identitas"
                            + " pilihan layar SDK: " + sdkUid, null);
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
                    HumanLog.ev("⚠️", "Penyimpanan akun login lokal gagal — login tetap dilayani tanpa catatan akun",
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
     * 0.5.3 — isi data untuk action "GetServerList". SATU-SATUNYA sumber: paket
     * CONFIG OFFLINE manual (config-offline/servers.json — 1 entri "Server Lokal"
     * url http://127.0.0.1:&lt;gamePort&gt;). Snapshot online DIHAPUS dari jalur
     * aktif (owner: "online cuma jejak arah bukan config asli ke offline").
     * 0.0.2 — format = KONTRAK KLIEN SAJA (hasil bedah main.min: field yang
     * BENAR-BENAR dibaca): {errorCode, serverList:[{serverId,url,name,online,new,
     * hot,openDate,offlineReason,merge,mergeTo}], history}. Field urlInner/
     * chatInner/upstream DIBUANG — tidak pernah dibaca klien (0 referensi di
     * JS) dan bukan kontrak; urlInner/chatInner warisan respons online,
     * upstream ciptaan era relay.
     */
    public static String serverListData() {
        synchronized (LOCK) {
            if (cachedServersJson != null) {
                return cachedServersJson;
            }
            String raw = null;
            try {
                // 0.5.3 — SATU-SATUNYA sumber: paket CONFIG OFFLINE manual
                // (config-offline/servers.json — 1 entri "Server Lokal" url lokal).
                // Snapshot online TIDAK lagi dibaca di jalur mana pun.
                File f = (offlineDir == null) ? null : new File(offlineDir, "servers.json");
                if (f != null && f.exists() && f.length() > 2) {
                    raw = readText(f);
                }
            } catch (Throwable ignored) {
            }
            if (raw == null || raw.indexOf("\"serverList\"") < 0) {
                // fallback darurat — TETAP LOKAL (tidak pernah popoh5): satu entri
                // manual dengan url port game aktif supaya Play tetap masuk lokal.
                // 0.0.2 — field kontrak klien saja (tanpa upstream/urlInner/chatInner).
                raw = "{\"errorCode\":0,\"serverList\":[{\"serverId\":\"1\","
                        + "\"url\":\"http://127.0.0.1:" + Config.gamePort() + "\",\"new\":true,\"hot\":true,"
                        + "\"name\":\"Server Lokal\",\"online\":true,\"openDate\":\"\","
                        + "\"offlineReason\":\"\",\"merge\":false,\"mergeTo\":\"\"}],\"history\":[]}";
                try {
                    HumanLog.ev("⚠️", "config-offline/servers.json tidak ada atau salah format"
                            + " — memakai daftar darurat 1 server LOKAL (127.0.0.1)",
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

    // ---------------- upstream server game (0.5.4) ----------------

    /**
     * 0.5.4 — URL tujuan relay :8080 (klik Play → io.connect ke url entri daftar
     * = 127.0.0.1:8080 → diteruskan ke sini).
     * KOREKSI FATAL 2026-11-14 (owner: "config online itu cuma jejak agar kita
     * gak tersesat.. bukan malah murni di pakai mentah-mentah") — SATU-SATUNYA
     * sumber: config/game-upstream.txt — SATU baris URL, disunting pemilik DI
     * PERANGKAT tanpa build ulang (dasbor seksi CONFIG / folder config/ =
     * transaksional, tidak pernah ditimpa boot).
     * Field "upstream" di paket manual TIDAK DIBACA LAGI sebagai tujuan relay —
     * dulu 0.6.3 mengisinya dengan alamat server game ASLI mentah-mentah dari
     * jejak online (s2111-bs.popoh5.com:8141) = LOCAL diam-diam bergantung ke
     * server online; kini field itu kosong dan hanya keterangan.
     * Kosong = relay tidak punya tujuan: LOCAL tidak menyentuh server asli
     * otomatis — log memberi tahu PERSIS cara pemilik mengisi (bukan loading
     * diam tanpa penjelasan lagi).
     */
    public static String gameUpstream() {
        String[] r = gameUpstreamEx();
        return r[0];
    }

    /** @return [url, sumber("file"|"")] — publik utk log & dasbor. */
    public static String[] gameUpstreamEx() {
        synchronized (LOCK) {
            try {
                if (confDir != null) {
                    File f = new File(confDir, "game-upstream.txt");
                    if (f.exists() && f.length() > 6) {
                        String s = readText(f).trim();
                        if (s.startsWith("http://") || s.startsWith("https://")) {
                            return new String[]{s, "file"};
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return new String[]{"", ""};
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
