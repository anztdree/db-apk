package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Penulis-ulang setting bin — SATU-SATUNYA isi mirror yang diubah saat disajikan
 * di mode LOCAL (README §4: "rewrite sisi server, bukan edit client").
 *
 * Latar (hasil analisis 2025, terverifikasi probe langsung):
 * - assets bs/db/android/setting_BS_Android.bin dari server asli TERENKRIPSI
 *   XOR dengan kunci "DragonBall" (711 byte); didekripsi berisi JSON peta server:
 *     url      → https://dragonh5cdn.popoh5.com/bs/index-native.html?v=…   (H5 game)
 *     tsUrl    → http://dragon.sjmobilegame.com:82/bs103/index-native.html  (varian uji)
 *     update   → https://dragonh5cdn.popoh5.com/bs/upgrade                  (zip + versi)
 *     loginServer / tsLoginServer → TIDAK disentuh (fase login berikutnya)
 *
 * Mode LOCAL: host dragonh5cdn.popoh5.com diganti http://127.0.0.1:<gamePort>
 * supaya H5 + resource + zip semuanya dilayani folder www/ milik sendiri.
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
        String key = f.length() + ":" + f.lastModified() + ":" + gamePort;
        byte[] c = cache;
        if (c != null && key.equals(cacheKey)) {
            return c;
        }
        synchronized (LOCK) {
            c = cache;
            if (c != null && key.equals(cacheKey)) {
                return c;
            }
            byte[] out = compute(f, gamePort);
            cache = out;
            cacheKey = key;
            return out;
        }
    }

    private static byte[] compute(File f, int gamePort) {
        try {
            byte[] enc = readAll(f);
            byte[] plain = xor(enc);
            String s = new String(plain, "UTF-8");
            String replaced = s.replace(HOST_ORIGINAL, "http://127.0.0.1:" + gamePort);
            if (replaced.equals(s)) {
                // tidak ada host yang cocok — sampaikan apa adanya (defensif)
                return enc;
            }
            return xor(replaced.getBytes("UTF-8"));
        } catch (Throwable t) {
            try {
                HumanLog.ev('⚠', "Penulisan-ulang setting bin gagal — file asli disajikan tanpa diubah",
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
