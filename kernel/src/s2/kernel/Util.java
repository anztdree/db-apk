// ============================================================
// UTIL — pembantu umum kernel (file, MIME, JSON smart, ISO time).
// ============================================================
package s2.kernel;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public final class Util {
    private Util() {}

    public static String isoMs(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    public static byte[] readBytes(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String readTextFile(File f) {
        byte[] b = readBytes(f);
        if (b == null) return null;
        try {
            return new String(b, "UTF-8");
        } catch (Throwable t) {
            return new String(b);
        }
    }

    public static boolean writeTextFile(File f, String content) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
            w.write(content);
            w.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean writeBytes(File f, byte[] data) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream out = new FileOutputStream(f, false);
            out.write(data);
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String mimeOf(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=utf-8";
        if (p.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".md")) return "text/markdown; charset=utf-8";
        return "application/octet-stream";
    }

    /** JSON smart: objek/array -> Json.Obj/Json.Arr; primitif -> String/Number/Boolean. */
    public static Object parseJsonSmart(String raw) throws Exception {
        if (raw == null) throw new IllegalArgumentException("null");
        return Json.parseSmart(raw);
    }

    public static String jsonValueToString(Object v) {
        return Json.valueToString(v);
    }

    public static String template(String s) {
        if (s == null) return "";
        s = replaceAll(s, "{{time}}", String.valueOf(System.currentTimeMillis() / 1000L));
        s = replaceAll(s, "{{date}}", Logr.isoDate());
        s = replaceAll(s, "{{state.nextUid}}", String.valueOf(KV.nextUid()));
        String lastUid = KV.getRaw("last:uid");
        String counterUid = KV.getRaw("counter:uid");
        s = replaceAll(s, "{{uid}}", lastUid != null ? lastUid : (counterUid != null ? counterUid : "0"));
        s = replaceStateVars(s);
        s = replaceReqVars(s);
        s = replaceFileVars(s);
        return s;
    }

    private static String replaceAll(String s, String from, String to) {
        if (from.length() == 0) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int idx;
        while ((idx = s.indexOf(from, i)) >= 0) {
            sb.append(s, i, idx).append(to);
            i = idx + from.length();
        }
        sb.append(s, i, s.length());
        return sb.toString();
    }

    private static String replaceStateVars(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int start = s.indexOf("{{state.", i);
            if (start < 0) {
                out.append(s, i, s.length());
                break;
            }
            int end = s.indexOf("}}", start);
            if (end < 0) {
                out.append(s, i, s.length());
                break;
            }
            String key = s.substring(start + 8, end);
            out.append(s, i, start);
            String raw = KV.getRaw(key);
            out.append(raw == null ? "" : raw);
            i = end + 2;
        }
        return out.toString();
    }

    private static String replaceReqVars(String s) {
        // {{req.x}} di prototipe mengambil dari ctx.req (body request HTTP).
        // Pada tier-1 HTTP, body jarang dipakai — diisi kosong bila tidak ada.
        String reqJson = Hot.currentReqBodyJson();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int start = s.indexOf("{{req.", i);
            if (start < 0) {
                out.append(s, i, s.length());
                break;
            }
            int end = s.indexOf("}}", start);
            if (end < 0) {
                out.append(s, i, s.length());
                break;
            }
            String key = s.substring(start + 6, end);
            out.append(s, i, start);
            String val = "";
            try {
                if (reqJson != null) {
                    Object parsed = Util.parseJsonSmart(reqJson);
                    if (parsed instanceof Json.Obj) {
                        Object v = ((Json.Obj) parsed).opt(key);
                        if (v != null && v != Json.NULL) val = Json.valueToString(v);
                    }
                }
            } catch (Throwable ignore) {
            }
            out.append(val);
            i = end + 2;
        }
        return out.toString();
    }

    private static String replaceFileVars(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int start = s.indexOf("{{file:", i);
            if (start < 0) {
                out.append(s, i, s.length());
                break;
            }
            int end = s.indexOf("}}", start);
            if (end < 0) {
                out.append(s, i, s.length());
                break;
            }
            String p = s.substring(start + 7, end).trim();
            out.append(s, i, start);
            File f = K.safeFile(p);
            String content = f != null ? Util.readTextFile(f) : null;
            out.append(content == null ? "" : content);
            i = end + 2;
        }
        return out.toString();
    }
}
