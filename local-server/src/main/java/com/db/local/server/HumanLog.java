package com.db.local.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Log bahasa manusia (aturan README §3):
 * - satu event = satu kalimat: "siapa minta apa → jawab apa · berapa lama"
 * - simbol: ▶ masuk, ◀ jawaban, ⚠ perlu perhatian, ✔ sehat, ℹ info
 * - angka ramah: 1,4 KB / 12 ms / jam lokal
 * Setiap event juga ditulis ke logs/human.log (teks) dan logs/events.jsonl (mesin).
 */
public final class HumanLog {
    private static final Object LOCK = new Object();
    private static final int CAP = 800;
    private static long seq = 0;
    private static File logDir = null;
    private static File humanFile = null;
    private static File jsonlFile = null;
    private static final ArrayList<String> mem = new ArrayList<String>(CAP + 8);

    private HumanLog() {
    }

    public static void init(File dir) {
        synchronized (LOCK) {
            logDir = dir;
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            if (dir != null && dir.exists()) {
                humanFile = new File(dir, "human.log");
                jsonlFile = new File(dir, "events.jsonl");
                rotateIfBig(humanFile, 4L * 1024 * 1024);
                rotateIfBig(jsonlFile, 4L * 1024 * 1024);
            }
        }
    }

    private static void rotateIfBig(File f, long max) {
        try {
            if (f != null && f.exists() && f.length() > max) {
                File bak = new File(f.getParentFile(), f.getName() + ".1");
                if (bak.exists()) {
                    bak.delete();
                }
                f.renameTo(bak);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Catat event dengan simbol + kalimat manusia + detail teknis (opsional). */
    public static void ev(char icon, String sentence, String detail) {
        evInternal(icon, sentence, detail);
    }

    public static void ok(String sentence) {
        ev('✔', sentence, null);
    }

    public static void warn(String sentence, String detail) {
        ev('⚠', sentence, detail);
    }

    public static void info(String sentence) {
        ev('ℹ', sentence, null);
    }

    /** Event permintaan dasbor: satu kalimat utuh + detail query. */
    public static void req(String method, String uri, String query, int status, long ms, long bytes) {
        String sentence = "Dasbor " + method + " " + uri + " — dijawab " + status
                + " · " + fmtMs(ms) + " · " + fmtBytes(bytes);
        char icon = (status >= 500) ? '⚠' : '▶';
        evInternal(icon, sentence, query == null || query.length() == 0 ? "-" : query);
    }

    private static void evInternal(char icon, String sentence, String detail) {
        long id;
        String t;
        String jo;
        synchronized (LOCK) {
            id = ++seq;
            t = nowStr();
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"id\":").append(id)
                    .append(",\"t\":\"").append(Json.esc(t)).append('"')
                    .append(",\"icon\":\"").append(icon).append('"')
                    .append(",\"text\":\"").append(Json.esc(sentence)).append('"')
                    .append(",\"detail\":\"").append(Json.esc(detail == null ? "" : detail)).append("\"}");
            jo = sb.toString();
            mem.add(jo);
            while (mem.size() > CAP) {
                mem.remove(0);
            }
        }
        if (logDir != null && humanFile != null) {
            String line = dateStr() + " " + t + " [" + icon + "] " + sentence
                    + (detail == null || detail.length() == 0 ? "" : "  ||  " + detail);
            appendLine(humanFile, line);
            appendLine(jsonlFile, jo);
        }
    }

    /** Semua event dengan id > sinceId, sebagai array JSON. */
    public static String snapshotJson(long sinceId) {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder(4096);
            sb.append('[');
            boolean first = true;
            for (int i = 0; i < mem.size(); i++) {
                String jo = mem.get(i);
                long id = extractId(jo);
                if (id <= sinceId) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(jo);
            }
            sb.append(']');
            return sb.toString();
        }
    }

    private static long extractId(String jo) {
        int i = jo.indexOf("\"id\":");
        if (i < 0) {
            return 0;
        }
        int j = i + 5;
        int k = j;
        final int len = jo.length();
        while (k < len && jo.charAt(k) >= '0' && jo.charAt(k) <= '9') {
            k++;
        }
        try {
            return Long.parseLong(jo.substring(j, k));
        } catch (Exception e) {
            return 0;
        }
    }

    public static void clearMem() {
        synchronized (LOCK) {
            mem.clear();
        }
    }

    public static long lastId() {
        synchronized (LOCK) {
            return seq;
        }
    }

    // ---------- format ramah ----------

    public static String fmtBytes(long n) {
        if (n < 0) {
            return "?";
        }
        if (n < 1024) {
            return n + " B";
        }
        double kb = n / 1024.0;
        if (kb < 1024) {
            return satuDesimal(kb) + " KB";
        }
        return satuDesimal(kb / 1024.0) + " MB";
    }

    public static String fmtMs(long ms) {
        if (ms < 0) {
            return "-";
        }
        if (ms < 1000) {
            return ms + " ms";
        }
        return satuDesimal(ms / 1000.0) + " dtk";
    }

    private static String satuDesimal(double d) {
        String s = String.format(Locale.US, "%.1f", d);
        return s.replace('.', ',');
    }

    private static String nowStr() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private static String dateStr() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    private static void appendLine(File f, String line) {
        try {
            if (f == null) {
                return;
            }
            synchronized (LOCK) {
                Writer w = new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8");
                w.write(line);
                w.write("\n");
                w.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
