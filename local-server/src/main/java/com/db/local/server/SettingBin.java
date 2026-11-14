package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Penulis-ulang setting bin — SATU-SATUNYA isi mirror yang diubah saat disajikan
 * di mode LOCAL (README §4: "rewrite sisi server, bukan edit client").
 *
 * Latar (hasil analisis 2025 + probe 2026-11-02, README §2.10):
 * - assets bs/db/android/setting_BS_Android.bin dari server asli TERENKRIPSI
 *   XOR dengan kunci "DragonBall" (711 byte); didekripsi berisi JSON peta server:
 *     url      → https://dragonh5cdn.popoh5.com/bs/index-native.html?v=…   (H5 game)
 *     tsUrl    → http://dragon.sjmobilegame.com:82/bs103/index-native.html  (varian uji)
 *     update   → https://dragonh5cdn.popoh5.com/bs/upgrade                  (zip + versi)
 *     loginServer   → https://login.popoh5.com  (+loginPort 610, +loginWebPort 510)
 *     tsLoginServer → http://118.24.68.91:7003                   (varian uji login)
 *
 * Mode LOCAL (koreksi PR1 — "endpoint masih menunjuk server pusat"):
 *   url/tsUrl/update  → http://127.0.0.1:<gamePort>     (sudah sejak 0.1.0)
 *   loginServer       → http://127.0.0.1   (TANPA PORT — 0.4.2; dulu salah tulis
 *                       ":8610" sehingga URL loginchecknative shell jadi
 *                       "127.0.0.1:8610:8610" — MalformedURLException, rantai SDK
 *                       tidak pernah selesai; shell menyambung loginServer+":"+
 *                       loginPort/loginWebPort sendiri)
 *   tsLoginServer     → http://127.0.0.1:<loginPort>    (dipakai utuh, varian uji)
 *   loginPort         → <loginPort>                     (asal 610)
 *   loginWebPort      → <loginPort>                     (asal 510 — loginchecknative)
 *   url               → + "&userid=<uid>"               (0.4.0 PR3; 0.4.2 = uid SESI
 *                       SDK terakhir — hasil layar login SDK asli, bukan guest
 *                       karangan; fallback guest lokal bila sesi SDK belum ada.
 *                       H5: checkLastLogin → doOriginLoginRequest — README §2.11)
 * Mode ONLINE (0.5.1 — TUNTUTAN owner "config yg jelas bukan rekam jejak";
 * jawaban atas laporan "config setelah SDK login ini masuk ke loginserver sampai
 * detik ini anda ngeyel"): file DITERUSKAN dari server asli TETAPI jalur LOGIN
 * dan url H5 tetap ditulis-ulang ke SERVER LOKAL — di SEMUA mode:
 *   url/update        → http://127.0.0.1:<gamePort>   (H5 lewat port game;
 *                       serversetting.json ikut lewat → loginserver-nya
 *                       ditulis-ulang ke server login lokal — GameServer)
 *   loginServer       → http://127.0.0.1               (shell loginchecknative
 *                       → :loginWebPort → server login lokal → diteruskan ke
 *                       https://login.popoh5.com:510 — SdkApi.proxyLoginCheckNative)
 *   tsLoginServer     → http://127.0.0.1:<loginPort>
 *   loginPort/loginWebPort → <loginPort>
 *   tsUrl             → ASLI (varian uji — persis perilaku mode LOCAL)
 * Server login lokal meneruskan socket.io & loginchecknative UTUH (byte-per-
 * byte) ke server asli — token & daftar server tetap dikeluarkan SERVER ASLI,
 * progres pemain tetap di sana; tetapi seluruh alur login LEWAT server lokal
 * dan terlihat di log (tidak ada lagi jalur yang langsung ke pusat).
 * TIDAK ada userid di url H5 pada mode ONLINE — identitas boot = mekanisme
 * shell asli (startGame), bukan penggantian kita.
 *
 * Skema XOR simetris: operasi enkripsi = dekripsi. Gagal apa pun → file asli
 * disajikan tanpa diubah (game tetap jalan, fallback terbukti §2.8).
 */
public final class SettingBin {

    /** Kunci hasil pemecahan analisis — file asli didekripsi jadi JSON valid dgn kunci ini. */
    static final String KEY = "DragonBall";

    /** Host yang ditulis-ulang ke server lokal di mode LOCAL. */
    static final String HOST_ORIGINAL = "https://dragonh5cdn.popoh5.com";

    /** Host/alamat login yang ditulis-ulang ke server login lokal (PR1/PR3 0.4.0). */
    static final String LOGIN_ORIGINAL = "https://login.popoh5.com";
    static final String TS_LOGIN_ORIGINAL = "http://118.24.68.91:7003";

    private static final Object LOCK = new Object();
    private static volatile byte[] cache;
    private static volatile String cacheKey = "";

    private SettingBin() {
    }

    /**
     * Isi setting bin siap saji untuk mode LOCAL. Hasil disangga (cache) per
     * kombinasi file+port agar tidak menghitung ulang tiap permintaan.
     */
    public static byte[] rewrittenLocal(File f, int gamePort) {
        return rewrittenLocal(f, gamePort, Config.loginPort(), Config.sdkPort(),
                Config.useSdk() ? null : currentUserIdSuffix());
    }

    /**
     * userid untuk url H5 — uid SESI SDK TERAKHIR (layar login SDK asli; tahan-lama
     * karena tamu SDK dipetakan per deviceId). Fallback: guest lokal tahan-lama.
     * Publik — dipakai GameServer untuk log.
     */
    public static String currentUserIdSuffix() {
        try {
            String sdkUid = SdkData.lastKnownUid();
            if (sdkUid != null && sdkUid.length() >= 4) {
                return sdkUid;
            }
        } catch (Throwable ignored) {
        }
        return LoginData.localUserId();
    }

    /** Versi lengkap — dipanggil harness dgn nilai config aktif.
     *  0.0.2 — loginPort (socket.io login) dan loginWebPort (loginchecknative
     *  → SDK) TERPISAH: shell menyambung loginServer+":"+loginPort untuk
     *  socket login dan loginServer+":"+loginWebPort untuk loginchecknative.
     *  Dulu keduanya dipaksa sama — itulah tumpang-sihid 8610 yang kini dipecah. */
    public static byte[] rewrittenLocal(File f, int gamePort, int loginPort, int loginWebPort,
            String userIdSuffix) {
        String key = f.length() + ":" + f.lastModified() + ":" + gamePort + ":" + loginPort
                + ":" + loginWebPort
                + ":" + (userIdSuffix == null ? "-" : userIdSuffix);
        byte[] c = cache;
        if (c != null && key.equals(cacheKey)) {
            return c;
        }
        synchronized (LOCK) {
            c = cache;
            if (c != null && key.equals(cacheKey)) {
                return c;
            }
            byte[] out = compute(f, gamePort, loginPort, loginWebPort, userIdSuffix);
            cache = out;
            cacheKey = key;
            return out;
        }
    }

    private static byte[] compute(File f, int gamePort, int loginPort, int loginWebPort,
            String userIdSuffix) {
        try {
            byte[] enc = readAll(f);
            byte[] plain = xor(enc);
            String s = new String(plain, "UTF-8");
            String replaced = s.replace(HOST_ORIGINAL, "http://127.0.0.1:" + gamePort);
            // PR1: login endpoint → server login lokal. 0.4.2: TANPA PORT — shell
            // menyambung ":"+loginPort / ":"+loginWebPort sendiri (loginchecknative).
            replaced = replaced.replace(LOGIN_ORIGINAL, "http://127.0.0.1");
            replaced = replaced.replace(TS_LOGIN_ORIGINAL, "http://127.0.0.1:" + loginPort);
            // 0.0.2 — dua port BERBEDA: loginPort (socket.io login, 8611) dan
            // loginWebPort (loginchecknative → server SDK, 8610).
            replaced = setJsonValue(replaced, "loginPort", String.valueOf(loginPort));
            replaced = setJsonValue(replaced, "loginWebPort", String.valueOf(loginWebPort));
            // PR3/0.4.2: userid url H5 = identitas sesi SDK (fallback guest lokal)
            if (userIdSuffix != null && userIdSuffix.length() > 0) {
                String url = getJsonValue(replaced, "url");
                if (url != null && url.indexOf("userid=") < 0) {
                    String sep = (url.indexOf('?') >= 0) ? "&" : "?";
                    replaced = setJsonValue(replaced, "url", url + sep + "userid=" + userIdSuffix);
                }
            }
            if (replaced.equals(s)) {
                // tidak ada host yang cocok — sampaikan apa adanya (defensif)
                return enc;
            }
            return xor(replaced.getBytes("UTF-8"));
        } catch (Throwable t) {
            try {
                HumanLog.ev("⚠️", "Penulisan-ulang setting bin gagal — file asli disajikan tanpa diubah",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
            try {
                return readAll(f);
            } catch (Throwable t2) {
                return new byte[0];
            }
        }
    }

    /**
     * 0.5.1 — mode ONLINE: tulis-ulang jalur LOGIN + url H5 ke server lokal.
     * Input = byte bin ASLI hasil passthrough dari server asli (GameServer
     * servePassthrough memanggil ini sebelum menyajikan). Gagal apa pun →
     * byte asli kembali (perilaku lama — game tetap jalan).
     * 0.0.2 — loginWebPort dipisah (loginchecknative → server SDK).
     */
    public static byte[] rewrittenOnline(byte[] enc, int gamePort, int loginPort, int loginWebPort) {
        String plain = plainOnline(enc, gamePort, loginPort, loginWebPort);
        if (plain == null) {
            return enc;
        }
        try {
            return xor(plain.getBytes("UTF-8"));
        } catch (Throwable t) {
            return enc;
        }
    }

    /**
     * 0.5.1 — teks hasil dekripsi + tulis-ulang mode ONLINE (bahan Capture);
     * null bila gagal. Penggantian = PERSIS rewrittenOnline.
     */
    static String plainOnline(byte[] enc, int gamePort, int loginPort, int loginWebPort) {
        try {
            String s = new String(xor(enc), "UTF-8");
            String replaced = s.replace(HOST_ORIGINAL, "http://127.0.0.1:" + gamePort);
            replaced = replaced.replace(LOGIN_ORIGINAL, "http://127.0.0.1");
            replaced = replaced.replace(TS_LOGIN_ORIGINAL, "http://127.0.0.1:" + loginPort);
            replaced = setJsonValue(replaced, "loginPort", String.valueOf(loginPort));
            replaced = setJsonValue(replaced, "loginWebPort", String.valueOf(loginWebPort));
            if (replaced.equals(s)) {
                return null; // tidak ada yang cocok — biarkan asli
            }
            return replaced;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 0.5.0 REKAM — isi setting bin yang DITERIMA KLIEN dalam bentuk TEKS
     * (didekripsi + sudah ditulis-ulang ke lokal) — config asli untuk
     * capture/payload/. Tidak disangga; dipanggil saat disajikan saja.
     * Skema penggantian = PERSIS compute(); bila suatu hari compute berubah,
     * ubah di sini juga (sengaja tidak digabung agar compute tetap murni byte).
     */
    static String decryptedRewritten(File f) {
        try {
            byte[] enc = readAll(f);
            String s = new String(xor(enc), "UTF-8");
            String replaced = s.replace(HOST_ORIGINAL, "http://127.0.0.1:" + Config.gamePort());
            replaced = replaced.replace(LOGIN_ORIGINAL, "http://127.0.0.1");
            replaced = replaced.replace(TS_LOGIN_ORIGINAL, "http://127.0.0.1:" + Config.loginPort());
            // (0.5.0 — decryptedRewritten tetap gaya LOCAL; mode ONLINE memakai
            //  plainOnline() terpisah agar kedua gaya tidak saling menimpa)
            replaced = setJsonValue(replaced, "loginPort", String.valueOf(Config.loginPort()));
            replaced = setJsonValue(replaced, "loginWebPort", String.valueOf(Config.sdkPort()));
            if (!Config.useSdk()) {
                String uid = currentUserIdSuffix();
                if (uid != null && uid.length() > 0) {
                    String url = getJsonValue(replaced, "url");
                    if (url != null && url.indexOf("userid=") < 0) {
                        String sep = (url.indexOf('?') >= 0) ? "&" : "?";
                        replaced = setJsonValue(replaced, "url", url + sep + "userid=" + uid);
                    }
                }
            }
            return replaced;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Ganti nilai string/angka suatu kunci level atas dalam JSON teks — tanpa
     * dependensi parser. Aman bila kunci tidak ada (teks kembali apa adanya).
     * Publik — dipakai GameServer (serversetting ONLINE 0.5.1) + diuji harness.
     */
    public static String setJsonValue(String json, String key, String newValue) {
        try {
            String needle = "\"" + key + "\"";
            int i = json.indexOf(needle);
            if (i < 0) {
                return json;
            }
            int c = json.indexOf(':', i + needle.length());
            if (c < 0) {
                return json;
            }
            int j = c + 1;
            final int len = json.length();
            while (j < len && (json.charAt(j) == ' ' || json.charAt(j) == '\t')) {
                j++;
            }
            int valStart = j; // posisi karakter pertama nilai (kutip pembuka bila string)
            int end;
            boolean wasString = (j < len && json.charAt(j) == '"');
            if (wasString) {
                j++;
                end = j;
                while (end < len && json.charAt(end) != '"') {
                    if (json.charAt(end) == '\\') {
                        end++;
                    }
                    end++;
                }
            } else {
                end = j;
                while (end < len && json.charAt(end) >= '0' && json.charAt(end) <= '9') {
                    end++;
                }
                if (end == j) {
                    return json; // bukan angka — biarkan
                }
            }
            String replacement = wasString ? ("\"" + newValue + "\"") : newValue;
            // cabang string: end menunjuk kutip PENUTUP — kutip itu ikut diganti (end+1);
            // cabang angka: end sudah satu posisi DI BELAKANG digit terakhir
            return json.substring(0, valStart) + replacement + json.substring(wasString ? end + 1 : end);
        } catch (Throwable t) {
            return json;
        }
    }

    /**
     * 0.5.2 AKAR — teks JSON ASLI hasil dekripsi file bin (TANPA tulis-ulang).
     * Bahan pembedah ConfigAkar di mode LOCAL; null bila gagal.
     */
    static String decryptedPlain(File f) {
        try {
            return new String(xor(readAll(f)), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 0.5.2 AKAR — teks JSON ASLI hasil dekripsi byte bin dari server asli
     * (TANPA tulis-ulang). Bahan pembedah ConfigAkar di mode ONLINE; null bila gagal.
     */
    static String decryptedPlain(byte[] enc) {
        try {
            return new String(xor(enc), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Baca nilai string level atas dari JSON teks; null bila tidak ada. */
    static String getJsonValue(String json, String key) {
        try {
            String needle = "\"" + key + "\"";
            int i = json.indexOf(needle);
            if (i < 0) {
                return null;
            }
            int c = json.indexOf(':', i + needle.length());
            if (c < 0) {
                return null;
            }
            int a = json.indexOf('"', c);
            if (a < 0) {
                return null;
            }
            int b = a + 1;
            final int len = json.length();
            while (b < len && json.charAt(b) != '"') {
                if (json.charAt(b) == '\\') {
                    b++;
                }
                b++;
            }
            if (b >= len) {
                return null;
            }
            return json.substring(a + 1, b);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 0.5.2 AKAR — nilai MENTAH level atas (string dengan kutip ATAU angka
     * polos): "loginPort": 610 → "610". Pembedah ConfigAkar butuh angka
     * (testMode/checkTime/loginPort/loginWebPort) yang tidak tercakup
     * getJsonValue. Null bila kunci tidak ada / nilai bukan string-angka.
     * Publik — diuji harness.
     */
    public static String getJsonRaw(String json, String key) {
        try {
            String needle = "\"" + key + "\"";
            int i = json.indexOf(needle);
            if (i < 0) {
                return null;
            }
            int c = json.indexOf(':', i + needle.length());
            if (c < 0) {
                return null;
            }
            int j = c + 1;
            final int len = json.length();
            while (j < len && (json.charAt(j) == ' ' || json.charAt(j) == '\t')) {
                j++;
            }
            if (j >= len) {
                return null;
            }
            char first = json.charAt(j);
            if (first == '"') {
                int b = j + 1;
                while (b < len && json.charAt(b) != '"') {
                    if (json.charAt(b) == '\\') {
                        b++;
                    }
                    b++;
                }
                if (b >= len) {
                    return null;
                }
                return json.substring(j, b + 1); // dengan kutip — mentah apa adanya
            }
            if (first == '-' || (first >= '0' && first <= '9')) {
                int b = j;
                if (json.charAt(b) == '-') {
                    b++;
                }
                while (b < len && ((json.charAt(b) >= '0' && json.charAt(b) <= '9')
                        || json.charAt(b) == '.')) {
                    b++;
                }
                return json.substring(j, b);
            }
            // true/false/null — ambil sampai koma/kurung tutup/baris baru
            int b = j;
            while (b < len && ",}]\n\r".indexOf(json.charAt(b)) < 0) {
                b++;
            }
            return json.substring(j, b).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    /** XOR byte-per-byte dengan kunci berulang — simetris (sekali jalan = enkripsi/dekripsi). */
    static byte[] xor(byte[] data) throws Exception {
        byte[] k = KEY.getBytes("UTF-8");
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ k[i % k.length]);
        }
        return out;
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(32, f.length()));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }
}
