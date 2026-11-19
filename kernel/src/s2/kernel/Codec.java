// ============================================================
// CODEC — codec kit kernel, identik dengan client (terbukti dari
// sisi client): XXTEA (kelas TEA client) + XOR DragonBall + LZString
// UTF16 + md5 + base64. Port setia dari prototipe server-core.
// Vektor silang terverifikasi:
//   encrypt("b7738641-da52-4493-9107-7739c62d35e9","verification")
//     = "0HdvUq3yWlKWp8hnC8piuJ9C9EPlIKTLX0X0c5QFOXBntLSC"
// ============================================================
package s2.kernel;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public final class Codec {
    private static final int DELTA = (int) 0x9e3779b9L;
    public static final String TEA_KEY = "verification";
    public static final String XOR_KEY = "DragonBall";

    private Codec() {}

    // ---------- md5 ----------
    public static String md5(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("MD5");
            byte[] h = d.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------- base64 ----------
    public static String b64enc(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static String b64enc(String s) {
        try {
            return b64enc(s.getBytes("UTF-8"));
        } catch (Throwable t) {
            return "";
        }
    }

    public static byte[] b64decBytes(String s) {
        try {
            return Base64.decode(s, Base64.DEFAULT);
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static String b64dec(String s) {
        try {
            return new String(b64decBytes(s), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------- XOR DragonBall ----------
    public static byte[] xorBuf(byte[] buf, String key) {
        byte[] kb = key.getBytes();
        byte[] out = new byte[buf.length];
        for (int i = 0; i < buf.length; i++) out[i] = (byte) (buf[i] ^ kb[i % kb.length]);
        return out;
    }

    public static byte[] xorText(String text) {
        try {
            return xorBuf(text.getBytes("UTF-8"), XOR_KEY);
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    // ---------- XXTEA (delta 0x9E3779B9, longs LITTLE-ENDIAN, kunci slice(0,16)) ----------
    private static int[] keyLongsLE(String key) {
        byte[] kb;
        try {
            kb = key.getBytes("UTF-8");
        } catch (Throwable t) {
            kb = key.getBytes();
        }
        int[] k = new int[]{0, 0, 0, 0};
        int n = Math.min(kb.length, 16);
        for (int i = 0; i < n; i++) {
            k[i / 4] |= (kb[i] & 0xff) << ((i % 4) * 8);
        }
        return k;
    }

    private static int[] strToLongsLE(byte[] bytes) {
        int n = Math.max((bytes.length + 3) / 4, 2);
        int[] v = new int[n];
        for (int i = 0; i < bytes.length; i++) {
            v[i / 4] |= (bytes[i] & 0xff) << ((i % 4) * 8);
        }
        return v;
    }

    private static byte[] longsToStrLE(int[] v) {
        byte[] out = new byte[v.length * 4];
        for (int i = 0; i < v.length; i++) {
            out[i * 4] = (byte) (v[i] & 0xff);
            out[i * 4 + 1] = (byte) ((v[i] >>> 8) & 0xff);
            out[i * 4 + 2] = (byte) ((v[i] >>> 16) & 0xff);
            out[i * 4 + 3] = (byte) ((v[i] >>> 24) & 0xff);
        }
        return out;
    }

    public static String teaEncrypt(String plain, String key) {
        if (plain == null || plain.length() == 0) return "";
        byte[] p;
        try {
            p = plain.getBytes("UTF-8");
        } catch (Throwable t) {
            p = plain.getBytes();
        }
        int[] v = strToLongsLE(p);
        int[] k = keyLongsLE(key);
        int i = v.length;
        int z = v[i - 1];
        int y = v[0];
        int sum = 0;
        int rounds = (int) Math.floor(6 + 52 / (double) i);
        for (int c = rounds; c-- > 0;) {
            sum += DELTA;
            int e = (sum >>> 2) & 3;
            for (int d = 0; d < i; d++) {
                y = v[(d + 1) % i];
                int mx = (((z >>> 5) ^ (y << 2)) + ((y >>> 3) ^ (z << 4))) ^ ((sum ^ y) + (k[(d & 3) ^ e] ^ z));
                z = v[d] += mx;
            }
        }
        return b64enc(longsToStrLE(v));
    }

    public static String teaDecrypt(String b64, String key) {
        if (b64 == null || b64.length() == 0) return "";
        byte[] raw = b64decBytes(b64);
        if (raw.length == 0 || raw.length % 4 != 0) return "";
        int[] v = new int[raw.length / 4];
        for (int i = 0; i < raw.length; i++) {
            v[i / 4] |= (raw[i] & 0xff) << ((i % 4) * 8);
        }
        int[] k = keyLongsLE(key);
        int i = v.length;
        int rounds = (int) Math.floor(6 + 52 / (double) i);
        int sum = rounds * DELTA;
        int z = v[i - 1];
        int y = v[0];
        while (sum != 0) {
            int e = (sum >>> 2) & 3;
            for (int d = i - 1; d >= 0; d--) {
                z = v[d > 0 ? d - 1 : i - 1];
                int mx = (((z >>> 5) ^ (y << 2)) + ((y >>> 3) ^ (z << 4))) ^ ((sum ^ y) + (k[(d & 3) ^ e] ^ z));
                y = v[d] -= mx;
            }
            sum -= DELTA;
        }
        byte[] out = longsToStrLE(v);
        int end = out.length;
        while (end > 0 && out[end - 1] == 0) end--;
        try {
            return new String(out, 0, end, "UTF-8");
        } catch (Throwable t) {
            return new String(out, 0, end);
        }
    }

    public static boolean selfTestTea() {
        String challenge = "selftest-123";
        return teaDecrypt(teaEncrypt(challenge, TEA_KEY), TEA_KEY).equals(challenge);
    }

    // ---------- LZString compressToUTF16 / decompressFromUTF16 ----------
    private static String lzCompress(String uncompressed, int bitsPerChar, int charOffset) {
        if (uncompressed == null || uncompressed.length() == 0) return "";
        Map<String, Integer> context_dictionary = new HashMap<String, Integer>();
        Map<String, Boolean> context_dictionaryToCreate = new HashMap<String, Boolean>();
        StringBuilder context_data = new StringBuilder();
        int context_enlargeIn = 2;
        int context_dictSize = 3;
        int context_numBits = 2;
        int context_data_val = 0;
        int context_data_position = 0;
        String context_w = "";

        for (int ii = 0; ii < uncompressed.length(); ii++) {
            String context_c = String.valueOf(uncompressed.charAt(ii));
            if (!context_dictionary.containsKey(context_c)) {
                context_dictionary.put(context_c, context_dictSize++);
                context_dictionaryToCreate.put(context_c, Boolean.TRUE);
            }
            String context_wc = context_w + context_c;
            if (context_dictionary.containsKey(context_wc)) {
                context_w = context_wc;
            } else {
                if (context_dictionaryToCreate.containsKey(context_w)) {
                    if (context_w.charAt(0) < 256) {
                        for (int j = 0; j < context_numBits; j++) {
                            context_data_val = context_data_val << 1;
                            if (context_data_position == bitsPerChar - 1) {
                                context_data_position = 0;
                                context_data.append((char) (context_data_val + charOffset));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                        }
                        int value = context_w.charAt(0);
                        for (int j = 0; j < 8; j++) {
                            context_data_val = (context_data_val << 1) | (value & 1);
                            if (context_data_position == bitsPerChar - 1) {
                                context_data_position = 0;
                                context_data.append((char) (context_data_val + charOffset));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = value >> 1;
                        }
                    } else {
                        int value = 1;
                        for (int j = 0; j < context_numBits; j++) {
                            context_data_val = (context_data_val << 1) | value;
                            if (context_data_position == bitsPerChar - 1) {
                                context_data_position = 0;
                                context_data.append((char) (context_data_val + charOffset));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = 0;
                        }
                        value = context_w.charAt(0);
                        for (int j = 0; j < 16; j++) {
                            context_data_val = (context_data_val << 1) | (value & 1);
                            if (context_data_position == bitsPerChar - 1) {
                                context_data_position = 0;
                                context_data.append((char) (context_data_val + charOffset));
                                context_data_val = 0;
                            } else {
                                context_data_position++;
                            }
                            value = value >> 1;
                        }
                    }
                    context_enlargeIn--;
                    if (context_enlargeIn == 0) {
                        context_enlargeIn = (int) Math.pow(2, context_numBits);
                        context_numBits++;
                    }
                    context_dictionaryToCreate.remove(context_w);
                } else {
                    int value = context_dictionary.get(context_w);
                    for (int j = 0; j < context_numBits; j++) {
                        context_data_val = (context_data_val << 1) | (value & 1);
                        if (context_data_position == bitsPerChar - 1) {
                            context_data_position = 0;
                            context_data.append((char) (context_data_val + charOffset));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = value >> 1;
                    }
                }
                context_enlargeIn--;
                if (context_enlargeIn == 0) {
                    context_enlargeIn = (int) Math.pow(2, context_numBits);
                    context_numBits++;
                }
                context_dictionary.put(context_wc, context_dictSize++);
                context_w = context_c;
            }
        }

        if (context_w.length() > 0) {
            if (context_dictionaryToCreate.containsKey(context_w)) {
                if (context_w.charAt(0) < 256) {
                    for (int j = 0; j < context_numBits; j++) {
                        context_data_val = context_data_val << 1;
                        if (context_data_position == bitsPerChar - 1) {
                            context_data_position = 0;
                            context_data.append((char) (context_data_val + charOffset));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                    }
                    int value = context_w.charAt(0);
                    for (int j = 0; j < 8; j++) {
                        context_data_val = (context_data_val << 1) | (value & 1);
                        if (context_data_position == bitsPerChar - 1) {
                            context_data_position = 0;
                            context_data.append((char) (context_data_val + charOffset));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = value >> 1;
                    }
                } else {
                    int value = 1;
                    for (int j = 0; j < context_numBits; j++) {
                        context_data_val = (context_data_val << 1) | value;
                        if (context_data_position == bitsPerChar - 1) {
                            context_data_position = 0;
                            context_data.append((char) (context_data_val + charOffset));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = 0;
                    }
                    value = context_w.charAt(0);
                    for (int j = 0; j < 16; j++) {
                        context_data_val = (context_data_val << 1) | (value & 1);
                        if (context_data_position == bitsPerChar - 1) {
                            context_data_position = 0;
                            context_data.append((char) (context_data_val + charOffset));
                            context_data_val = 0;
                        } else {
                            context_data_position++;
                        }
                        value = value >> 1;
                    }
                }
                context_enlargeIn--;
                if (context_enlargeIn == 0) {
                    context_enlargeIn = (int) Math.pow(2, context_numBits);
                    context_numBits++;
                }
                context_dictionaryToCreate.remove(context_w);
            } else {
                int value = context_dictionary.get(context_w);
                for (int j = 0; j < context_numBits; j++) {
                    context_data_val = (context_data_val << 1) | (value & 1);
                    if (context_data_position == bitsPerChar - 1) {
                        context_data_position = 0;
                        context_data.append((char) (context_data_val + charOffset));
                        context_data_val = 0;
                    } else {
                        context_data_position++;
                    }
                    value = value >> 1;
                }
            }
            context_enlargeIn--;
            if (context_enlargeIn == 0) {
                context_enlargeIn = (int) Math.pow(2, context_numBits);
                context_numBits++;
            }
        }

        int value2 = 2;
        for (int j = 0; j < context_numBits; j++) {
            context_data_val = (context_data_val << 1) | value2;
            if (context_data_position == bitsPerChar - 1) {
                context_data_position = 0;
                context_data.append((char) (context_data_val + charOffset));
                context_data_val = 0;
            } else {
                context_data_position++;
            }
            value2 = 0;
        }
        // flush char terakhir
        while (true) {
            context_data_val = context_data_val << 1;
            if (context_data_position == bitsPerChar - 1) {
                context_data.append((char) (context_data_val + charOffset));
                break;
            } else {
                context_data_position++;
            }
        }
        return context_data.toString();
    }

    public static String lzCompressUtf16(String input) {
        if (input == null || input.length() == 0) return "";
        return lzCompress(input, 15, 32) + " ";
    }

    private static String lzDecompress(int length, int resetValue, String compressed, int charOffset) {
        String[] dictionary = new String[4096];
        for (int i = 0; i < 3; i++) dictionary[i] = String.valueOf((char) i);
        int enlargeIn = 4;
        int dictSize = 4;
        int numBits = 3;
        StringBuilder result = new StringBuilder();
        int[] val = new int[]{compressed.charAt(0) - charOffset};
        int[] position = new int[]{resetValue};
        int[] index = new int[]{1};
        String w = "";
        String entry;

        class Bits {
            int read(int count) {
                int r = 0;
                int p = 1;
                int maxpower = (int) Math.pow(2, count);
                int power = 1;
                while (power != maxpower) {
                    int resb = val[0] & position[0];
                    position[0] >>= 1;
                    if (position[0] == 0) {
                        position[0] = resetValue;
                        val[0] = (index[0] < compressed.length() ? compressed.charAt(index[0]) : 0) - charOffset;
                        index[0]++;
                    }
                    r |= (resb > 0 ? 1 : 0) * p;
                    p <<= 1;
                    power <<= 1;
                }
                return r;
            }
        }
        Bits bits = new Bits();

        int next = bits.read(2);
        int c;
        switch (next) {
            case 0:
                c = bits.read(8);
                break;
            case 1:
                c = bits.read(16);
                break;
            case 2:
                return "";
            default:
                return "";
        }
        dictionary[3] = String.valueOf((char) c);
        w = String.valueOf((char) c);
        result.append(w);
        while (true) {
            if (index[0] > length) return "";
            c = bits.read(numBits);
            switch (c) {
                case 0:
                    if (dictSize >= dictionary.length) {
                        String[] nd = new String[dictionary.length * 2];
                        System.arraycopy(dictionary, 0, nd, 0, dictionary.length);
                        dictionary = nd;
                    }
                    dictionary[dictSize++] = String.valueOf((char) bits.read(8));
                    c = dictSize - 1;
                    enlargeIn--;
                    break;
                case 1:
                    if (dictSize >= dictionary.length) {
                        String[] nd = new String[dictionary.length * 2];
                        System.arraycopy(dictionary, 0, nd, 0, dictionary.length);
                        dictionary = nd;
                    }
                    dictionary[dictSize++] = String.valueOf((char) bits.read(16));
                    c = dictSize - 1;
                    enlargeIn--;
                    break;
                case 2:
                    return result.toString();
                default:
                    break;
            }
            if (enlargeIn == 0) {
                enlargeIn = (int) Math.pow(2, numBits);
                numBits++;
            }
            String dictVal = (c >= 0 && c < dictionary.length) ? dictionary[c] : null;
            if (dictVal != null) {
                entry = dictVal;
            } else {
                if (c == dictSize) {
                    entry = w + w.charAt(0);
                } else {
                    return null;
                }
            }
            result.append(entry);
            if (dictSize >= dictionary.length) {
                String[] nd = new String[dictionary.length * 2];
                System.arraycopy(dictionary, 0, nd, 0, dictionary.length);
                dictionary = nd;
            }
            dictionary[dictSize++] = w + entry.charAt(0);
            enlargeIn--;
            w = entry;
            if (enlargeIn == 0) {
                enlargeIn = (int) Math.pow(2, numBits);
                numBits++;
            }
        }
    }

    public static String lzDecompressUtf16(String compressed) {
        if (compressed == null || compressed.length() == 0) return "";
        if (compressed.length() == 1 && compressed.charAt(0) == ' ') return "";
        String r = lzDecompress(compressed.length(), 16384, compressed, 32);
        return r == null ? "" : r;
    }

    public static byte[] concat(byte[] a, byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(a.length + b.length);
        out.write(a, 0, a.length);
        out.write(b, 0, b.length);
        return out.toByteArray();
    }
}
