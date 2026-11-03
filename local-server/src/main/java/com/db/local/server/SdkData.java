package com.db.local.server;

/**
 * DATA TAHAP SDK — simpanan untuk API quickgame/zhuhuan (server SDK lokal).
 *
 * 0.4.3: penyimpanan pindah ke DRAGONBALL.DB (SQLite — lihat Db.java):
 *
 *   tabel sdk_guests   — pemetaan deviceId → tamu (deviceId sama = tamu sama,
 *                        isNewUser 0; baris pertama = tamu pemilik — migrasi
 *                        guest lama, progres tidak lekat)
 *   tabel sdk_sessions — sesi SDK (authToken PK → uid, isGuest)
 *   tabel accounts     — akun (dipakai bersama LoginData — identitas SDK =
 *                        identitas game)
 *   kv "local_user"    — guest lama pemilik
 *   kv "last_uid"      — uid sesi terakhir (userid url H5)
 *
 * SEMUA SKEMA RESPONS = HASIL PROBE SERVER ASLI qsdk.t4game.com (2026-11-02,
 * body {"sv":"v2","data":<base64 JSON param>,"sign":<md5>}, rahasia tanda
 * "0b2a18e45d7df321" dari dex — README §2.11). Tidak ada yang dikarang:
 *
 *   /v1/system/init        → data persis respons asli (versi "empty", productConfig,
 *                            loginTypes ["1","13"], nodes, dst — salinan respons asli)
 *   /v1/user/registerVisitor → tamu: {authToken, userData:{uid, username, token,
 *                            isGuest, isNewUser, mobile, bindInfo{12 entri}}, checkRealName}
 *   /v1/user/autoLogin     → validasi authToken sesi tersimpan
 *   /v1/user/loginByName   → username + password (MD5) vs tabel accounts
 *   /v1/user/registerUser  → buat akun lokal (password MD5 disimpan)
 *   /v1/user/ckRegVistor   → {isReged:1} bila authToken dikenal
 *
 * 0.4.4 BEDAH DEX (HWLoginActivity → mesoamericanhochmagandies.cointhreat.
 * timidcompletely/navyshop + feedtonight.singersmooth): tombol MASUK/DAFTAR
 * layar SDK ASLI memakai EMAIL sebagai nama akun — divalidasi client dgn regex
 * "\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*" (huruf besar ditolak) dan
 * kata sandi "^.{6,}$" (minimal 6 karakter). Yang tiba di server: username =
 * alamat email (bisa berisi @ . + -), password = MD5. Server lokal kini menerima
 * skema itu: charset [a-z0-9_.+-@], panjang 3-64, disimpan huruf kecil agar
 * MASUK tidak peka huruf besar/kecil (domain email tidak peka kasus).
 *
 * Identitas: uid == username (satu nama untuk tahap SDK & tahap game) sehingga
 * loginGame(username=<uid>, password "game_origin") di server login lokal selalu
 * mendarat di akun yang SAMA.
 */
public final class SdkData {
    private static final Object LOCK = new Object();

    private SdkData() {
    }

    /** Dipanggil Starter — kompatibilitas API (data kini di dragonball.db). */
    public static void init(java.io.File configDir) {
        // data pindah ke dragonball.db (Db.boot) — tidak ada lagi file txt
    }

    private static String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }

    // ---------------- sesi & token ----------------

    /** Token sesi stabil per (akun, perangkat) — tahan proses mati/hidup (model.b client menyimpan last_token). */
    private static String tokenFor(String uid, String deviceId) {
        return SdkData.md5("sdk-local|" + uid + "|" + (deviceId == null ? "" : deviceId));
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
        try {
            Db.i().putSession(authToken, uid, guest, now());
        } catch (Throwable ignored) {
        }
        return d.toString();
    }

    /** Catat uid sesi terakhir (kv last_uid — dipakai userid url H5). */
    private static void saveLast(String uid) {
        try {
            if (uid != null && uid.length() >= 4) {
                Db.i().kvPut(Db.KV_LAST_UID, uid);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- endpoint: tamu (registerVisitor) ----------------

    /**
     * Tamu baru/lama untuk deviceId — meniru server asli: deviceId sama = tamu sama.
     * Tamu pertama memakai guest tahan-lama (kv local_user — migrasi identitas
     * pemilik; progres game yang sudah ada tetap lekat).
     */
    public static String registerVisitor(String deviceId, String clientLang) {
        synchronized (LOCK) {
            try {
                String dev = (deviceId == null || deviceId.length() == 0) ? "unknown" : deviceId;
                String existing = Db.i().guestUid(dev);
                if (existing != null) {
                    LoginData.ensureAccount(existing, "");
                    return sessionData(tokenFor(existing, dev), existing, true, false);
                }
                // tamu baru — migrasikan guest lokal pemilik untuk perangkat pertama
                boolean first = Db.i().guestCount() == 0;
                String uid = first ? LoginData.localUserId()
                        : "guest" + (10000000L + (long) (Math.random() * 89999999L));
                LoginData.ensureAccount(uid, "");
                Db.i().putGuest(dev, uid, now());
                try {
                    // 0.4.4: kata-kata jelas — yang BARU adalah ikatan perangkat,
                    // bukan tamunya (tamunya guest lokal pemilik, migrasi identitas)
                    HumanLog.ev('▶', "SDK: perangkat " + shortId(dev) + " diikat ke tamu " + uid
                            + (first ? " — guest lokal pemilik (migrasi identitas)" : " — tamu baru"), null);
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
                String ses = Db.i().session(authToken);
                if (ses != null) {
                    int tab = ses.indexOf('\t');
                    String uid = tab > 0 ? ses.substring(0, tab) : ses;
                    boolean guest = tab > 0 && "1".equals(ses.substring(tab + 1));
                    return sessionData(authToken, uid, guest, false);
                }
                // token lama berbasis (uid, device) — terima bila polanya cocok
                String[] rows = Db.i().guestRows();
                for (String line : rows) {
                    String[] c = line.split("\t");
                    if (c.length >= 2 && tokenFor(c[1], c[0]).equals(authToken)) {
                        LoginData.ensureAccount(c[1], "");
                        return sessionData(authToken, c[1], true, false);
                    }
                }
                String[] names = LoginData.accountNames();
                for (String uid : names) {
                    if (uid != null && uid.length() > 0 && tokenFor(uid, deviceId).equals(authToken)) {
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

    /**
     * Login akun+password (password SUDAH MD5 dari client). null = gagal (40101 server asli).
     * 0.4.4: layar SDK mengirim EMAIL — pencarian tidak peka huruf besar/kecil
     * (coba persis dulu, lalu huruf kecil) agar "Budi@GMail.com" mendarat di
     * akun "budi@gmail.com" yang sama.
     */
    public static String loginByName(String username, String passwordMd5) {
        synchronized (LOCK) {
            if (username == null || username.trim().length() == 0) {
                return null;
            }
            String typed = username.trim();
            String name = typed;
            String stored = LoginData.accountPassword(typed);
            if (stored == null) {
                String lower = typed.toLowerCase(java.util.Locale.US);
                if (!lower.equals(typed)) {
                    stored = LoginData.accountPassword(lower);
                    if (stored != null) {
                        name = lower;
                    }
                }
            }
            if (stored == null) {
                return null;
            }
            if (stored.length() > 0 && passwordMd5 != null && !stored.equalsIgnoreCase(passwordMd5)) {
                return null;
            }
            LoginData.touchAccount(name);
            saveLast(name);
            return sessionData(tokenFor(name, null), name, false, false);
        }
    }

    /**
     * Daftar akun baru; null bila nama tidak valid atau sudah dipakai.
     * 0.4.4: MENERIMA EMAIL (akun layar SDK = email — bedah dex timidcompletely/
     * navyshop/feedtonight.singersmooth). Aturan: huruf kecil (dinormalisasi),
     * charset [a-z0-9_.+-@], panjang 3-64. Contoh diterima: "budi@gmail.com".
     * Nama lama tanpa @ (warisan) tetap diterima agar kompatibel.
     */
    public static String registerUser(String username, String passwordMd5) {
        synchronized (LOCK) {
            if (username == null) {
                return null;
            }
            String name = username.trim().toLowerCase(java.util.Locale.US);
            if (name.length() < 3 || name.length() > 64) {
                return null;
            }
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                        || c == '_' || c == '.' || c == '-' || c == '+' || c == '@';
                if (!ok) {
                    return null;
                }
            }
            if (LoginData.accountPassword(name) != null) {
                return null;
            }
            LoginData.ensureAccount(name, passwordMd5 == null ? "" : passwordMd5);
            try {
                HumanLog.ev('▶', "SDK: akun BARU terdaftar — " + name, null);
            } catch (Throwable ignored) {
            }
            saveLast(name);
            return sessionData(tokenFor(name, null), name, false, true);
        }
    }

    // ---------------- endpoint: ckRegVistor ----------------

    /** {isReged:1} bila authToken dikenal (respons asli: data objek {isReged:1}). */
    public static String ckRegVistor(String authToken) {
        synchronized (LOCK) {
            boolean known = false;
            try {
                known = Db.i().session(authToken) != null;
            } catch (Throwable ignored) {
            }
            if (!known && authToken != null && authToken.length() == 32) {
                known = true; // token lokal stabil berformat md5 — periksa pola saja (tahan pembersihan)
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
                String ses = Db.i().session(token == null ? "" : token);
                tokenOk = ses != null && ses.startsWith(uid + "\t");
            } catch (Throwable ignored) {
            }
            if (!tokenOk && token != null && token.length() >= 8) {
                // token stabil per (uid, device) — hitung ulang dari pemetaan tamu
                try {
                    String[] rows = Db.i().guestRows();
                    for (String line : rows) {
                        String[] c = line.split("\t");
                        if (c.length >= 2 && c[1].equals(uid) && tokenFor(c[1], c[0]).equals(token)) {
                            tokenOk = true;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            boolean accountKnown = LoginData.accountPassword(uid) != null
                    || uid.equals(LoginData.localUserId());
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
            Db.i().putSession(token == null || token.length() == 0 ? tokenFor(uid, null) : token,
                    uid, guest, now());
            saveLast(uid);
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
                String v = Db.i().kv(Db.KV_LAST_UID);
                if (v != null && v.length() >= 4) {
                    return v;
                }
                String uid = Db.i().lastSessionUid();
                if (uid != null && uid.length() >= 4) {
                    return uid;
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
                return Db.i().guestCount();
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    /** Uid tamu aktif (baris pertama pemetaan) — dasbor; null bila belum ada. */
    public static String primaryGuest() {
        synchronized (LOCK) {
            try {
                String[] rows = Db.i().guestRows();
                if (rows.length > 0) {
                    String[] c = rows[0].split("\t");
                    if (c.length >= 2) {
                        return c[1];
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
}
