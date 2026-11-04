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
 * Mode ONLINE: file asli diteruskan apa adanya (tanpa rewrite).
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
        return rewrittenLocal(f, gamePort, Config.loginPort(), Config.useSdk() ? null : currentUserIdSuffix());
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

    /** Versi lengkap — dipanggil GameServer dengan nilai config aktif. */
    public static byte[] rewrittenLocal(File f, int gamePort, int loginPort, String userIdSuffix) {
        String key = f.length() + ":" + f.lastModified() + ":" + gamePort + ":" + loginPort
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
            byte[] out = compute(f, gamePort, loginPort, userIdSuffix);
            cache = out;
            cacheKey = key;
            return out;
        }
    }

    private static byte[] compute(File f, int gamePort, int loginPort, String userIdSuffix) {
        try {
            byte[] enc = readAll(f);
            byte[] plain = xor(enc);
            String s = new String(plain, "UTF-8");
            String replaced = s.replace(HOST_ORIGINAL, "http://127.0.0.1:" + gamePort);
            // PR1: login endpoint → server login lokal. 0.4.2: TANPA PORT — shell
            // menyambung ":"+loginPort / ":"+loginWebPort sendiri (loginchecknative).
            replaced = replaced.replace(LOGIN_ORIGINAL, "http://127.0.0.1");
            replaced = replaced.replace(TS_LOGIN_ORIGINAL, "http://127.0.0.1:" + loginPort);
            // port login asli (610 web socket login, 510 web login SDK) → port login lokal
            replaced = setJsonValue(replaced, "loginPort", String.valueOf(loginPort));
            replaced = setJsonValue(replaced, "loginWebPort", String.valueOf(loginPort));
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
            replaced = setJsonValue(replaced, "loginPort", String.valueOf(Config.loginPort()));
            replaced = setJsonValue(replaced, "loginWebPort", String.valueOf(Config.loginPort()));
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
     */
    static String setJsonValue(String json, String key, String newValue) {
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
