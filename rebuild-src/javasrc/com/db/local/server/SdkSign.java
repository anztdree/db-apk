package com.db.local.server;

import java.security.MessageDigest;
import java.util.TreeMap;

/**
 * Bagian 29 — VERIFIKASI SIGN ala server asli.
 *
 * Algoritma dibedah dari smali client
 *   unnecessarysperrylites.seventygenom.nationalcommunitymissing(HashMap):
 *     1. param dituang ke TreeMap (sort by key, urutan String.compareTo)
 *     2. string = untuk tiap entry:  key + "=" + (value==null ? "" : value) + "&"
 *     3. string += secret  ("0b2a18e45d7df321" — hardcode di smali)
 *     4. sign = MD5(string) hex lowercase
 *   (map kosong -> client mengirim sign=null)
 *
 * VERIFIKASI LIVE 23 Nov 2026: request ber-sign hasil rekonstruksi ini
 * DITERIMA server asli (lanjut ke cek param, BUKAN 20002) — bukti algoritma
 * + secret benar. Param ditandatangani = seluruh map = isi JSON `data`.
 *
 * Rekonstruksi server: flatten JSON `data` (JsonUtil.scanFlat) -> TreeMap
 * -> k=v& + secret -> MD5 -> bandingkan dgn param sign (case-insensitive).
 */
public final class SdkSign {

    private SdkSign() {}

    public static String expected(java.util.Map<String, String> params, String secret) {
        TreeMap<String, String> t = new TreeMap<String, String>(params);
        StringBuilder b = new StringBuilder(params.size() * 24 + 64);
        for (java.util.Map.Entry<String, String> e : t.entrySet()) {
            b.append(e.getKey()).append('=')
             .append(e.getValue() == null ? "" : e.getValue())
             .append('&');
        }
        b.append(secret == null ? "" : secret);
        return md5hex(b.toString());
    }

    public static boolean valid(java.util.Map<String, String> params, String given,
                                String secret) {
        if (given == null || given.isEmpty()) return false;
        String exp = expected(params, secret);
        return exp.equalsIgnoreCase(given.trim());
    }

    /** MD5 hex lowercase (mirror seventygenom.singersmooth). */
    public static String md5hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(d.length * 2);
            for (int i = 0; i < d.length; i++) {
                int v = d[i] & 0xff;
                if (v < 16) b.append('0');
                b.append(Integer.toHexString(v));
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
