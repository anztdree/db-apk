package com.db.local.server;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pembaca APK sendiri (fitur "APK Reader" dasbor):
 * - daftar isi APK + cari file
 * - pratinjau isi file (membantu melihat config asli game)
 * - analisis config: nilai Base64 di config.properties / config_BS.properties / QHinfo didekode otomatis
 */
public final class ApkReader {
    private static final Object LOCK = new Object();
    private static volatile ZipFile zip = null;
    private static volatile String zipPath = null;

    private static final String[] CONFIG_PATHS = {
            "assets/config.properties",
            "assets/config_BS.properties",
            "assets/QHinfo"
    };

    private ApkReader() {
    }

    public static void init(Context ctx) {
        try {
            String p = ctx.getPackageCodePath();
            if (p == null) {
                return;
            }
            synchronized (LOCK) {
                if (zip != null && p.equals(zipPath)) {
                    return;
                }
                if (zip != null) {
                    try {
                        zip.close();
                    } catch (Throwable ignored) {
                    }
                }
                zip = new ZipFile(new File(p));
                zipPath = p;
            }
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Pembaca APK tidak bisa dibuka — fitur APK Reader terbatas",
                    String.valueOf(t));
        }
    }

    private static ZipFile ensure() throws Exception {
        ZipFile z = zip;
        if (z == null) {
            throw new Exception("APK belum terbuka");
        }
        return z;
    }

    /** {"total":n,"shown":[{path,size,packed}]} */
    public static String listJson(String prefix, int limit) {
        try {
            ZipFile zf = ensure();
            int lim = (limit <= 0) ? 300 : Math.min(limit, 2000);
            int total = 0;
            StringBuilder sb = new StringBuilder(8192);
            sb.append("{\"total\":");
            StringBuilder items = new StringBuilder(8192);
            boolean first = true;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (prefix != null && prefix.length() > 0 && !name.contains(prefix)) {
                    continue;
                }
                total++;
                if (total <= lim) {
                    if (!first) {
                        items.append(',');
                    }
                    first = false;
                    items.append("{\"path\":\"").append(Json.esc(name)).append('"')
                            .append(",\"size\":").append(e.getSize())
                            .append(",\"packed\":").append(e.getCompressedSize())
                            .append('}');
                }
            }
            sb.append(total).append(",\"shown\":[").append(items).append("]}");
            return sb.toString();
        } catch (Throwable t) {
            return "{\"error\":\"Gagal membaca isi APK: " + Json.esc(String.valueOf(t)) + "\"}";
        }
    }

    /** byte isi file; null = tidak ada; array kosong = terlalu besar. */
    public static byte[] get(String path) {
        try {
            ZipFile zf = ensure();
            ZipEntry e = zf.getEntry(path);
            if (e == null) {
                return null;
            }
            if (e.getSize() > 8L * 1024 * 1024) {
                return new byte[0];
            }
            InputStream in = zf.getInputStream(e);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Analisis file config: teks mentah + dekode Base64 per baris. */
    public static String analyzeJson() {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\"files\":[");
        boolean firstFile = true;
        for (int f = 0; f < CONFIG_PATHS.length; f++) {
            String path = CONFIG_PATHS[f];
            if (!firstFile) {
                sb.append(',');
            }
            firstFile = false;
            sb.append("{\"path\":\"").append(Json.esc(path)).append('"');
            byte[] b = get(path);
            if (b == null) {
                sb.append(",\"error\":\"tidak ada di APK\"}");
                continue;
            }
            if (b.length == 0) {
                sb.append(",\"error\":\"terlalu besar untuk dibaca (>8 MB)\"}");
                continue;
            }
            String raw = new String(b, 0, Math.min(b.length, 40000), java.nio.charset.Charset.forName("UTF-8"));
            sb.append(",\"size\":").append(b.length)
                    .append(",\"raw\":\"").append(Json.esc(trunc(raw, 40000))).append('"')
                    .append(",\"lines\":[");
            String[] lines = raw.split("\\r?\\n");
            boolean firstLine = true;
            int shown = 0;
            for (int i = 0; i < lines.length && shown < 120; i++) {
                String ln = lines[i];
                if (ln.trim().length() == 0) {
                    continue;
                }
                shown++;
                String key = ln;
                String val = "";
                int idx = ln.indexOf('=');
                if (idx < 0) {
                    idx = ln.indexOf(':');
                }
                if (idx >= 0) {
                    key = ln.substring(0, idx).trim();
                    val = ln.substring(idx + 1).trim();
                }
                if (!firstLine) {
                    sb.append(',');
                }
                firstLine = false;
                sb.append("{\"k\":\"").append(Json.esc(key)).append('"')
                        .append(",\"v\":\"").append(Json.esc(trunc(val, 2000))).append('"');
                String dec = tryDecode(val);
                if (dec != null) {
                    sb.append(",\"dec\":\"").append(Json.esc(trunc(dec, 2000))).append('"');
                }
                sb.append('}');
            }
            sb.append("]}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Kembalikan hasil dekode Base64 jika nilai terlihat seperti Base64 dan hasilnya terbaca. */
    private static String tryDecode(String v) {
        if (v == null || v.length() < 8 || v.length() > 8192) {
            return null;
        }
        final int len = v.length();
        for (int i = 0; i < len; i++) {
            char c = v.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=' || c == '_';
            if (!ok) {
                return null;
            }
        }
        try {
            byte[] d = Base64.decode(v, Base64.DEFAULT);
            String s = new String(d, "UTF-8");
            int printable = 0;
            final int sl = s.length();
            for (int i = 0; i < sl; i++) {
                char c = s.charAt(i);
                if ((c >= 0x20 && c < 0x7F) || c == '\n' || c == '\r' || c == '\t') {
                    printable++;
                }
            }
            if (sl > 0 && printable * 100 / sl >= 90) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String trunc(String s, int n) {
        return (s.length() <= n) ? s : s.substring(0, n) + " …(dipotong)";
    }
}
