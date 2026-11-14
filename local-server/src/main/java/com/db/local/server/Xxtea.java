package com.db.local.server;

import java.nio.charset.StandardCharsets;

/**
 * XXTEA — klon PERSIS perilaku TEA klien H5 (main.min_777039fc.js milik
 * proyek, kelas TEA + Utf8 + Base64). Ini bagian handcraft mesin game lokal:
 * klien menegaskan kontraknya di kode sendiri — server lokal memahami bahasa
 * yang sama TANPA menyalin apa pun dari server asli.
 *
 * Kontrak protokol "verify" (dari kode klien):
 *   socket.on("verify", function(n){ var o=(new TEA).encrypt(n,"verification");
 *   socket.emit("verify", o, function(n){ 0==n.ret && lanjut }) })
 *   → server kirim EVENT "verify" dgn satu argumen teks (sid sesi game),
 *     klien balik EVENT "verify" berisi Base64(XXTEA(arg, "verification"))
 *     dgn ACK callback, server jawab ACK ret 0 bila dekripsi cocok.
 *
 * Rincian algoritma yang direplikasi mentah-mentah dari klien (bukti bun —
 * vektor acuan dibangkitkan dari kode klien sendiri, lihat harness TAHAP 20):
 *   - data  : longs little-endian dari byte UTF-8; len(data)==1 → pad ke 2
 *   - kunci : byte UTF-8 dipotong 16 → longs; "verification" = 12 byte →
 *             3 longs; indeks ke-4 di klien = undefined → 0 (emulasi: array
 *             kunci selalu 4 slot, slot tanpa nilai = 0)
 *   - MX    : ((s>>>5 ^ l<<2) + (l>>>3 ^ s<<4)) ^ ((p ^ l) + (k[(3&d)^a] ^ s))
 *             (presedensi JS: + lebih kuat dari ^)
 *   - round : floor(6 + 52/len(data)) — presedensi JS: 52/len dulu, lalu +6
 *             (BUKAN (6+52)/len — jejak trace klien membuktikannya);
 *             DELTA 0x9E3779B9; aritmetika int32 (float64 JS ≡ int32 Java
 *             pada tiap operasi bitwise/penjumlahan)
 *   - hasil : longs → byte LE → Base64 (encode) / sebaliknya (decode)
 */
public final class Xxtea {

    private static final int DELTA = (int) 0x9E3779B9L;

    private Xxtea() {
    }

    // ---------------- longs ----------------

    /** strToLongs klien: byte UTF-8 → longs LE (a[n] = b[4n] + b[4n+1]<<8 + …). */
    private static int[] strToLongs(byte[] b) {
        int n = (b.length + 3) / 4;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int p = 4 * i;
            out[i] = (b[p] & 0xFF)
                    + (((p + 1 < b.length) ? (b[p + 1] & 0xFF) : 0) << 8)
                    + (((p + 2 < b.length) ? (b[p + 2] & 0xFF) : 0) << 16)
                    + (((p + 3 < b.length) ? (b[p + 3] & 0xFF) : 0) << 24);
        }
        return out;
    }

    /** longsToStr klien: longs → byte LE. */
    private static byte[] longsToStr(int[] v) {
        byte[] out = new byte[4 * v.length];
        for (int i = 0; i < v.length; i++) {
            int p = 4 * i;
            out[p] = (byte) (v[i] & 0xFF);
            out[p + 1] = (byte) ((v[i] >>> 8) & 0xFF);
            out[p + 2] = (byte) ((v[i] >>> 16) & 0xFF);
            out[p + 3] = (byte) ((v[i] >>> 24) & 0xFF);
        }
        return out;
    }

    /**
     * Kunci klien: Utf8.encode(k).slice(0,16) → longs. "verification" = 12
     * byte → 3 longs; JS membaca slot ke-4 = undefined → 0. Emulasi: selalu
     * array 4 slot — slot tanpa byte = 0 (persis perilaku JS bitwise).
     */
    private static int[] keyLongs(String key) {
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);
        int take = Math.min(kb.length, 16);
        byte[] cut = new byte[take];
        System.arraycopy(kb, 0, cut, 0, take);
        int[] k = new int[4]; // slot 4 = 0 — persis r[3] undefined di JS
        int[] raw = strToLongs(cut);
        System.arraycopy(raw, 0, k, 0, raw.length);
        return k;
    }

    // ---------------- encrypt (arah klien) ----------------

    /** TEA.encrypt klien — masukan teks, keluaran Base64. */
    public static String encrypt(String plain, String key) {
        if (plain == null || plain.length() == 0) {
            return "";
        }
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        int[] v = strToLongs(data);
        if (v.length <= 1) {
            int[] pad = new int[2];
            pad[0] = v[0];
            pad[1] = 0;
            v = pad;
        }
        int[] k = keyLongs(key);
        int n = v.length;
        int s = v[n - 1], l = v[0];
        int rounds = 6 + 52 / n; // floor(6 + 52/n) — presedensi JS klien
        int p = 0;
        for (int c = rounds; c-- > 0;) {
            p += DELTA;
            int a = (p >>> 2) & 3;
            for (int d = 0; d < n; d++) {
                l = v[(d + 1) % n];
                int mx = ((s >>> 5 ^ l << 2) + (l >>> 3 ^ s << 4))
                        ^ ((p ^ l) + (k[(3 & d) ^ a] ^ s));
                s = (v[d] += mx);
            }
        }
        return java.util.Base64.getEncoder().encodeToString(longsToStr(v));
    }

    // ---------------- decrypt (arah server lokal) ----------------

    /** TEA.decrypt klien — masukan Base64, keluaran teks (trailing \0 dibuang). */
    public static String decrypt(String b64, String key) {
        if (b64 == null || b64.length() == 0) {
            return "";
        }
        byte[] raw = java.util.Base64.getDecoder().decode(b64);
        int[] v = strToLongs(raw);
        int[] k = keyLongs(key);
        int n = v.length;
        int s = v[n - 1], l = v[0];
        int rounds = 6 + 52 / n; // floor(6 + 52/n) — presedensi JS klien
        int p = rounds * DELTA;
        while (p != 0) {
            int a = (p >>> 2) & 3;
            for (int d = n - 1; d >= 0; d--) {
                s = v[d > 0 ? d - 1 : n - 1];
                int mx = ((s >>> 5 ^ l << 2) + (l >>> 3 ^ s << 4))
                        ^ ((p ^ l) + (k[(3 & d) ^ a] ^ s));
                l = (v[d] -= mx);
            }
            p -= DELTA;
        }
        byte[] out = longsToStr(v);
        int end = out.length;
        while (end > 0 && out[end - 1] == 0) {
            end--; // klien: g.replace(/\0+$/,"")
        }
        return new String(out, 0, end, StandardCharsets.UTF_8);
    }
}
