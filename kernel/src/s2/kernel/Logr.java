// ============================================================
// LOGR — log mesin (ring buffer + mirror jsonl di data/logs.jsonl).
// Hukum: handler TIDAK punya API log — log hanya milik mesin.
// ============================================================
package s2.kernel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public final class Logr {
    public static final int MAX_BUFFER = 3000;

    private static final List<String[]> BUF = new ArrayList<String[]>();
    private static int seq = 0;
    private static int dropped = 0;
    private static String mirrorPath = null;
    private static final Object LOCK = new Object();

    private Logr() {}

    public static void attachMirror(File dataDir) {
        try {
            File f = new File(dataDir, "logs.jsonl");
            if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
            mirrorPath = f.getAbsolutePath();
        } catch (Throwable t) {
            mirrorPath = null;
        }
    }

    public static void write(String level, String category, String msg) {
        synchronized (LOCK) {
            seq++;
            String ts = isoNow();
            String[] e = new String[]{String.valueOf(seq), ts, level, category, msg == null ? "" : msg};
            BUF.add(e);
            if (BUF.size() > MAX_BUFFER) {
                while (BUF.size() > MAX_BUFFER) BUF.remove(0);
                dropped++;
            }
            if (mirrorPath != null) {
                Writer w = null;
                try {
                    Json.Obj o = new Json.Obj();
                    o.put("seq", seq);
                    o.put("ts", ts);
                    o.put("level", level);
                    o.put("category", category);
                    o.put("msg", msg == null ? "" : msg);
                    w = new OutputStreamWriter(new FileOutputStream(mirrorPath, true), "UTF-8");
                    w.write(o.toString());
                    w.write('\n');
                } catch (Throwable ignore) {
                    // best-effort
                } finally {
                    if (w != null) {
                        try {
                            w.close();
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        }
    }

    /** level/category null|""|all = semua. since = seq terakhir yang sudah dilihat. */
    public static Json.Obj list(String level, String category, long since, int limit) {
        synchronized (LOCK) {
            if (limit < 1) limit = 1;
            if (limit > 1000) limit = 1000;
            Json.Arr arr = new Json.Arr();
            int total = 0;
            long last = since;
            for (String[] e : BUF) {
                long s = Long.parseLong(e[0]);
                if (s <= since) continue;
                if (level != null && level.length() > 0 && !"all".equals(level) && !level.equals(e[2])) continue;
                if (category != null && category.length() > 0 && !"all".equals(category) && !category.equals(e[3])) continue;
                total++;
                if (arr.length() < limit) {
                    Json.Obj o = new Json.Obj();
                    o.put("seq", s);
                    o.put("ts", e[1]);
                    o.put("level", e[2]);
                    o.put("category", e[3]);
                    o.put("msg", e[4]);
                    arr.put(o);
                    last = s;
                }
            }
            Json.Obj out = new Json.Obj();
            out.put("ok", true);
            out.put("cursor", last);
            out.put("total", total);
            out.put("logs", arr);
            return out;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            BUF.clear();
            dropped = 0;
        }
    }

    public static Json.Obj stats() {
        synchronized (LOCK) {
            Json.Obj o = new Json.Obj();
            o.put("buffer", BUF.size());
            o.put("dropped", dropped);
            return o;
        }
    }

    public static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    public static String isoDate() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
