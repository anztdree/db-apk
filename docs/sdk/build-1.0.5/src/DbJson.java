package com.db.local;

/**
 * Util JSON MINI untuk MiniServer 1.0.2 — TANPA org.json (dipilih agar kompilasi
 * tidak butuh dependensi tambahan; envelope SDK terbukti FLAT dari tiket HAR:
 * semua field level-atas berupa string/number — lihat docs/sdk/bukti device-har).
 *
 * Fungsi:
 *   1. flatGet(json, key)  — ambil nilai level-atas dgn kunci tepat
 *                            (string dgn escape \n\r\t\"\\, number, bool, null)
 *   2. esc(s)              — escape string utk dibangun ke JSON (output)
 *   3. mimeB64(s)          — decode Base64 gaya SDK (MIME: ada CRLF wrap per 76 char,
 *                            terbukti dari tiket registerVisitor HAR "%0D%0A")
 *   4. formValue(body, k)  — ambil nilai form "k=v&..." (envelope sv=v2&data=...&sign=...)
 */
public final class DbJson {

    private DbJson() {
    }

    /** Ambil nilai level-atas dgn kunci tepat; null bila tidak ada. */
    public static String flatGet(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int i = 0;
        while (true) {
            i = json.indexOf(needle, i);
            if (i < 0) {
                return null;
            }
            int j = i + needle.length();
            while (j < json.length() && json.charAt(j) == ' ') {
                j++;
            }
            if (j >= json.length() || json.charAt(j) != ':') {
                i += needle.length(); // kecocokan palsu (mis. di dalam string nilai) → lanjut
                continue;
            }
            j++;
            while (j < json.length() && json.charAt(j) == ' ') {
                j++;
            }
            if (j >= json.length()) {
                return null;
            }
            char c = json.charAt(j);
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                j++;
                while (j < json.length()) {
                    char d = json.charAt(j);
                    if (d == '\\' && j + 1 < json.length()) {
                        char e = json.charAt(j + 1);
                        if (e == 'n') {
                            sb.append('\n');
                        } else if (e == 'r') {
                            sb.append('\r');
                        } else if (e == 't') {
                            sb.append('\t');
                        } else {
                            sb.append(e); // \" \\ \/ dst.
                        }
                        j += 2;
                        continue;
                    }
                    if (d == '"') {
                        return sb.toString();
                    }
                    sb.append(d);
                    j++;
                }
                return null; // string tidak tertutup
            }
            // number / bool / null: sampai pemisah berikutnya
            int k = j;
            while (k < json.length() && ",}] \r\n\t".indexOf(json.charAt(k)) < 0) {
                k++;
            }
            return json.substring(j, k);
        }
    }

    /** Escape string agar sah sebagai nilai JSON (output UTF-8). */
    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Decode Base64 gaya SDK: Base64.encode(DEFAULT) Android membungkus per 76 char
     * dgn CRLF (terbukti di tiket HAR: %0D%0A). Lenient: whitespace dibuang,
     * padding dilengkapi bila kurang.
     */
    public static String mimeB64(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\r' && c != '\n' && c != ' ' && c != '\t') {
                sb.append(c);
            }
        }
        String t = sb.toString();
        int pad = t.length() % 4;
        if (pad == 2) {
            t = t + "==";
        } else if (pad == 3) {
            t = t + "=";
        }
        byte[] raw = android.util.Base64.decode(t, android.util.Base64.DEFAULT);
        return new String(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Nilai form-urlencoded "k=v&k2=v2" (envelope). null bila tidak ada. */
    public static String formValue(String body, String key) {
        if (body == null || key == null) {
            return null;
        }
        String[] parts = body.split("&");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (p.substring(0, eq).equals(key)) {
                try {
                    return java.net.URLDecoder.decode(p.substring(eq + 1), "UTF-8");
                } catch (Throwable t) {
                    return p.substring(eq + 1);
                }
            }
        }
        return null;
    }
}
