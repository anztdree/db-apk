package com.db.local;

import java.util.Arrays;

public final class Xxtea {

    private Xxtea() {
    }

    public static String decrypt(String data, String key) {
        try {
            byte[] bytes = Texts.b64decode(data);
            int[] v = toLongs(bytes);
            if (v.length == 0) {
                return "";
            }
            int[] k = keyLongs(Texts.utf8(key));
            int n = v.length;
            int rounds = (int) (((long) (52 / n + 6)) * 0x9E3779B9L & 0xFFFFFFFFL);
            int y = v[0];
            while (rounds != 0) {
                int e = (rounds >>> 2) & 3;
                int last = n - 1;
                int p = last;
                while (p >= 0) {
                    int z;
                    if (p > 0) {
                        z = v[p - 1];
                    } else {
                        z = v[last];
                    }
                    int mx = (((z >>> 5) ^ (y << 2)) + ((y >>> 3) ^ (z << 4)))
                            ^ ((y ^ rounds) + (z ^ k[(p & 3) ^ e]));
                    y = v[p] - mx;
                    v[p] = y;
                    p = p - 1;
                }
                rounds -= 0x9E3779B9;
            }
            byte[] out = longsToBytes(v);
            int len = out.length;
            while (len > 0 && out[len - 1] == 0) {
                len--;
            }
            return Texts.utf8(Arrays.copyOf(out, len));
        } catch (Exception e) {
            return "";
        }
    }

    private static int[] toLongs(byte[] data) {
        int n = (data.length + 3) / 4;
        int[] out = new int[n];
        int i = 0;
        while (i < n) {
            int j = i * 4;
            int b0 = (j < data.length) ? (data[j] & 0xff) : 0;
            int b1 = (j + 1 < data.length) ? ((data[j + 1] & 0xff) << 8) : 0;
            int b2 = (j + 2 < data.length) ? ((data[j + 2] & 0xff) << 16) : 0;
            j = j + 3;
            int b3 = (j < data.length) ? ((data[j] & 0xff) << 24) : 0;
            out[i] = b0 | b1 | b2 | b3;
            i = i + 1;
        }
        return out;
    }

    private static int[] keyLongs(byte[] key) {
        int len = Math.min(16, key.length);
        int[] k = new int[4];
        int i = 0;
        while (i < (len + 3) / 4) {
            if (i >= 4) {
                break;
            }
            int j = i * 4;
            int b0 = (j < len) ? (key[j] & 0xff) : 0;
            int b1 = (j + 1 < len) ? ((key[j + 1] & 0xff) << 8) : 0;
            int b2 = (j + 2 < len) ? ((key[j + 2] & 0xff) << 16) : 0;
            j = j + 3;
            int b3 = (j < len) ? ((key[j] & 0xff) << 24) : 0;
            k[i] = b0 | b1 | b2 | b3;
            i = i + 1;
        }
        return k;
    }

    private static byte[] longsToBytes(int[] v) {
        byte[] out = new byte[v.length * 4];
        int i = 0;
        while (i < v.length) {
            int val = v[i];
            int j = i * 4;
            out[j] = (byte) (val & 0xff);
            out[j + 1] = (byte) ((val >>> 8) & 0xff);
            out[j + 2] = (byte) ((val >>> 16) & 0xff);
            out[j + 3] = (byte) ((val >>> 24) & 0xff);
            i = i + 1;
        }
        return out;
    }
}
