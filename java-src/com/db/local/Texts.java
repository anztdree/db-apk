package com.db.local;

import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class Texts {

    private static final String B64A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final SecureRandom RNG = new SecureRandom();

    private Texts() {
    }

    public static byte[] b64decode(String s) {
        if (s == null) {
            return new byte[0];
        }
        char[] chars = new char[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                continue;
            }
            chars[n] = c;
            n++;
        }
        int pad = 0;
        while (n - pad > 0 && chars[n - 1 - pad] == '=') {
            pad++;
        }
        int outLen = (n / 4) * 3;
        if (pad == 1) {
            outLen -= 1;
        }
        if (pad == 2) {
            outLen -= 2;
        }
        byte[] out = new byte[outLen];
        int[] val = new int[4];
        int block = 0;
        int oi = 0;
        while (block + 3 < n) {
            for (int k = 0; k < 4; k++) {
                char c = chars[block + k];
                if (c >= 'A' && c <= 'Z') {
                    val[k] = c - 'A';
                } else if (c >= 'a' && c <= 'z') {
                    val[k] = c - 'a' + 26;
                } else if (c >= '0' && c <= '9') {
                    val[k] = c - '0' + 52;
                } else if (c == '+') {
                    val[k] = 62;
                } else if (c == '/') {
                    val[k] = 63;
                } else {
                    val[k] = 0;
                }
            }
            int bits = (val[0] << 18) | (val[1] << 12) | (val[2] << 6) | val[3];
            if (oi < outLen) {
                out[oi] = (byte) ((bits >>> 16) & 0xff);
                oi++;
            }
            if (oi < outLen) {
                out[oi] = (byte) ((bits >>> 8) & 0xff);
                oi++;
            }
            if (oi < outLen) {
                out[oi] = (byte) (bits & 0xff);
                oi++;
            }
            block += 4;
        }
        return out;
    }

    public static String b64encode(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i < data.length) {
            int bits = (data[i] & 0xff) << 16;
            int j = i + 1;
            if (j < data.length) {
                bits |= (data[j] & 0xff) << 8;
            }
            int k = i + 2;
            if (k < data.length) {
                bits |= data[k] & 0xff;
            }
            sb.append(B64A.charAt((bits >>> 18) & 0x3f));
            sb.append(B64A.charAt((bits >>> 12) & 0x3f));
            if (j < data.length) {
                sb.append(B64A.charAt((bits >>> 6) & 0x3f));
            } else {
                sb.append('=');
            }
            if (k < data.length) {
                sb.append(B64A.charAt(bits & 0x3f));
            } else {
                sb.append('=');
            }
            i += 3;
        }
        return sb.toString();
    }

    public static String formValue(String form, String key) {
        String value = "";
        if (form == null || form.length() == 0) {
            return value;
        }
        String[] parts = form.split("&");
        for (int i = 0; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            String name;
            if (eq < 0) {
                name = parts[i];
            } else {
                name = parts[i].substring(0, eq);
            }
            if (!name.equals(key)) {
                continue;
            }
            if (eq >= 0) {
                value = parts[i].substring(eq + 1);
            }
            return urlDecode(value);
        }
        return value;
    }

    public static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = 0; i < data.length; i++) {
            sb.append(HEX[(data[i] >> 4) & 0xf]);
            sb.append(HEX[data[i] & 0xf]);
        }
        return sb.toString();
    }

    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (int i = 0; i < d.length; i++) {
                sb.append(HEX[(d[i] >> 4) & 0xf]);
                sb.append(HEX[d[i] & 0xf]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String queryValue(String query, String key) {
        if (query == null || query.length() == 0) {
            return "";
        }
        return formValue(query, key);
    }

    public static String quote(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t') {
                sb.append("\\t");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c < ' ') {
                sb.append(String.format("\\u%04x", Integer.valueOf(c)));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String randHex(int len) {
        byte[] bytes = new byte[len];
        RNG.nextBytes(bytes);
        return hex(bytes);
    }

    public static long toLong(String s, long def) {
        if (s == null) {
            return def;
        }
        try {
            if (s.length() == 0) {
                return def;
            }
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static String urlDecode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    public static String utf8(byte[] b) {
        try {
            return new String(b, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] utf8(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
