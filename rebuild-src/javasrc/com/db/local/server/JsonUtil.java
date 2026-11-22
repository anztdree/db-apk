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
