package com.db.local.server;

/**
 * Utilitas kecil untuk menulis JSON tanpa dependensi eksternal.
 * Semua nilai string di-escape agar JSON selalu valid.
 */
public final class Json {
    private Json() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 16);
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                    break;
            }
        }
        return b.toString();
    }

    public static String str(String k, String v) {
        return "\"" + esc(k) + "\":\"" + esc(v) + "\"";
    }

    public static String num(String k, long v) {
        return "\"" + esc(k) + "\":" + v;
    }

    public static String bool(String k, boolean v) {
        return "\"" + esc(k) + "\":" + v;
    }
}
