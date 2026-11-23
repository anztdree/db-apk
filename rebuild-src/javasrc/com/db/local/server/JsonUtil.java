package com.db.local.server;

/**
 * Utilitas JSON kecil tanpa dependensi (org.json tidak dipakai agar class bisa
 * diuji di desktop JVM sebelum di-dex). Hanya untuk membangun respons JSON logger.
 */
public final class JsonUtil {
    private JsonUtil() {}

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n");  break;
                case '\r': b.append("\\r");  break;
                case '\t': b.append("\\t");  break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Bagian 29 — scan objek JSON TOP-LEVEL FLAT -> map (urutan kemunculan).
     * Nilai: string (di-unescape), angka (teks literal — cocok dgn
     * Integer.toString/Long.toString yang dipakai client saat menandatangani),
     * true/false (teks literal), null -> "". Objek/array nested dijadikan teks
     * JSON mentah (best effort — client SDK tidak pernah mengirim nested).
     * Return null bila bukan objek ({...}) / tidak bisa diparse.
     */
    public static java.util.LinkedHashMap<String, String> scanFlat(String json) {
        if (json == null) return null;
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) return null;
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<String, String>();
        int i = 1, n = s.length() - 1;
        while (i < n) {
            // lewati pemisah
            while (i < n && (s.charAt(i) == ',' || s.charAt(i) == ' '
                    || s.charAt(i) == '\n' || s.charAt(i) == '\r' || s.charAt(i) == '\t')) i++;
            if (i >= n) break;
            if (s.charAt(i) != '"') return out.isEmpty() && s.charAt(i) == '}' ? out : null;
            // key
            StringBuilder kb = new StringBuilder();
            i++;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < n) { i = unescAt(s, i, kb); continue; }
                if (c == '"') { i++; break; }
                kb.append(c); i++;
            }
            // ':' 
            while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == ':')) i++;
            if (i >= n) return null;
            // value
            String val;
            char c0 = s.charAt(i);
            if (c0 == '"') {
                StringBuilder vb = new StringBuilder();
                i++;
                while (i < n) {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < n) { i = unescAt(s, i, vb); continue; }
                    if (c == '"') { i++; break; }
                    vb.append(c); i++;
                }
                val = vb.toString();
            } else if (c0 == '{' || c0 == '[') {
                int close = rawBlock(s, i);
                if (close < 0) return null;
                val = s.substring(i, close + 1).replaceAll("\\s+", "");
                i = close + 1;
            } else {
                int e = i;
                while (e < n && ",}".indexOf(s.charAt(e)) < 0) e++;
                String lit = s.substring(i, e).trim();
                if (lit.equals("null")) val = "";
                else val = lit;   // angka / true / false — teks literal
                i = e;
            }
            if (kb.length() == 0) return null;
            out.put(kb.toString(), val);
        }
        return out;
    }

    private static char unesc(char c) {
        switch (c) {
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'b': return '\b';
            case 'f': return '\f';
            default: return c;      // \" \\ \/ 
        }
    }

    /** handle escape mulai di posisi '\\' (i); pos balik = index karakter berikut. */
    private static int unescAt(String s, int i, StringBuilder b) {
        char n1 = s.charAt(i + 1);
        if (n1 == 'u' && i + 5 < s.length()) {
            try {
                b.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                return i + 6;
            } catch (Throwable t) { /* jatuh ke escape biasa */ }
        }
        b.append(unesc(n1));
        return i + 2;
    }

    /** index penutup blok {..} / [..] mulai di open (menghitung string). */
    private static int rawBlock(String s, int open) {
        char openC = s.charAt(open);
        char closeC = openC == '{' ? '}' : ']';
        int depth = 0;
        boolean inStr = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == openC) depth++;
            else if (c == closeC) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    /** nama file → Content-Type HTTP (peta minimum untuk isi game egret) */
    public static String contentType(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=utf-8";
        if (p.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".css"))  return "text/css; charset=utf-8";
        if (p.endsWith(".png"))  return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif"))  return "image/gif";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".bmp"))  return "image/bmp";
        if (p.endsWith(".svg"))  return "image/svg+xml";
        if (p.endsWith(".mp3"))  return "audio/mpeg";
        if (p.endsWith(".wav"))  return "audio/wav";
        if (p.endsWith(".ogg"))  return "audio/ogg";
        if (p.endsWith(".mp4"))  return "video/mp4";
        if (p.endsWith(".ttf"))  return "font/ttf";
        if (p.endsWith(".fnt"))  return "text/plain; charset=utf-8";
        if (p.endsWith(".xml"))  return "text/xml; charset=utf-8";
        if (p.endsWith(".txt") || p.endsWith(".version")) return "text/plain; charset=utf-8";
        if (p.endsWith(".zip"))  return "application/zip";
        if (p.endsWith(".bin"))  return "application/octet-stream";
        return "application/octet-stream";
    }
}
