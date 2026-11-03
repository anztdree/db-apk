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
 * Log bahasa manusia (aturan README §3) — DUA TINGKAT (0.4.5, koreksi owner
 * "jujur terus terang log anda bagi saya cuma sampah"):
 *
 * - TINGKAT PENTING (ev/ok/warn/info) — selalu tampil: boot (5 baris), mode,
 *   tamu/daftar/masuk/loginchecknative, daftar server, peringatan, galat.
 * - TINGKAT TEKNIS (evTech) — hanya tampil bila mode RINCI dinyalakan
 *   (toggle di dasbor, tersimpan di server.json "logTech"): semua baris
 *   request per-file (200/404/seed/passthrough), probe DNS, denyut pantauan,
 *   geser tombol, dsb. SANGAT banyak — membanjiri log bila dicampur.
 *
 * ATURAN PENTING: mode bersih TIDAK menghilangkan bukti — setiap event
 * (penting + teknis) TETAP ditulis ke logs/human.log (teks) dan
 * logs/events.jsonl (mesin). Yang disaring hanya tampilan dasbor (memori):
 * baris teknis tidak masuk memori saat mode bersih, jadi dasbor tenang
 * dan copy log = cerita penting. Copy penuh (API /api/logs/file) tetap
 * mengambil semuanya dari file.
 *
 * Simbol: ▶ masuk, ◀ jawaban, ⚠ perlu perhatian, ✔ sehat, ℹ info.
 * Angka ramah: 1,4 KB / 12 ms / jam lokal.
 */
public final class HumanLog {
    private static final Object LOCK = new Object();
    private static final int CAP = 800;
    private static long seq = 0;
    private static File logDir = null;
    private static File humanFile = null;
    private static File jsonlFile = null;
    private static final ArrayList<String> mem = new ArrayList<String>(CAP + 8);
    /** Mode RINCI: teknis ikut masuk memori dasbor. Bawaan BERSIH (false). */
    private static volatile boolean techEnabled = false;

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

    /** Catat event PENTING — selalu tampil di dasbor (tingkat bawaan). */
    public static void ev(char icon, String sentence, String detail) {
        evInternal(icon, sentence, detail, false);
    }

    /**
     * Catat event TEKNIS — hanya tampil di dasbor saat mode RINCI.
     * File human.log tetap mencatatnya (aturan: mode bersih tidak menghilangkan bukti).
     */
    public static void evTech(char icon, String sentence, String detail) {
        evInternal(icon, sentence, detail, true);
    }

    /** Nyalakan/matikan mode RINCI (dipanggil dasbor via /api/logtech + Starter saat boot). */
    public static void setTechEnabled(boolean on) {
        techEnabled = on;
    }

    public static boolean isTechEnabled() {
        return techEnabled;
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

    /** Event permintaan dasbor (path aneh di luar /api — jarang): penting. */
    public static void req(String method, String uri, String query, int status, long ms, long bytes) {
        String sentence = "Dasbor " + method + " " + uri + " — dijawab " + status
                + " · " + fmtMs(ms) + " · " + fmtBytes(bytes);
        char icon = (status >= 500) ? '⚠' : '▶';
        evInternal(icon, sentence, query == null || query.length() == 0 ? "-" : query, false);
    }

    private static void evInternal(char icon, String sentence, String detail, boolean tech) {
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
                    .append(",\"detail\":\"").append(Json.esc(detail == null ? "" : detail)).append('"');
            if (tech) {
                sb.append(",\"v\":1"); // penanda TEKNIS — dasbor meredupkan baris ini
            }
            sb.append('}');
            jo = sb.toString();
            // ATURAN 0.4.5: baris teknis saat mode BERSIH tidak masuk memori dasbor
            // (dasbor tenang); file tetap lengkap — bukti tidak pernah hilang.
            if (!tech || techEnabled) {
                mem.add(jo);
                while (mem.size() > CAP) {
                    mem.remove(0);
                }
            }
        }
        if (logDir != null && humanFile != null) {
            String line = dateStr() + " " + t + " [" + icon + (tech ? "·" : "") + "] " + sentence
                    + (detail == null || detail.length() == 0 ? "" : "  ||  " + detail);
            appendLine(humanFile, line);
            appendLine(jsonlFile, jo);
        }
    }

    /** Semua event di memori (sudah tersaring sesuai mode RINCI) dengan id > sinceId. */
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

    /** Ekor file human.log (teks mentah) — sumber "Copy penuh" di dasbor (0.4.5). */
    public static String tailText(int maxLines) {
        File f;
        synchronized (LOCK) {
            f = humanFile;
        }
        if (f == null || !f.exists() || maxLines < 1) {
            return "(human.log belum ada)";
        }
        try {
            long keep = Math.min(f.length(), 512L * 1024L); // cukup utk ±500 baris
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
            raf.seek(f.length() - keep);
            byte[] buf = new byte[(int) keep];
            raf.readFully(buf);
            raf.close();
            String[] lines = new String(buf, "UTF-8").split("\n");
            int start = Math.max(0, lines.length - maxLines);
            // buang baris pertama bila terpotong di tengah (awal keep bukan batas baris)
            if (start == 0 && f.length() > keep) {
                start = 1;
            }
            StringBuilder sb = new StringBuilder(4096);
            for (int i = start; i < lines.length; i++) {
                if (lines[i].length() > 0) {
                    sb.append(lines[i]).append('\n');
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "(gagal membaca human.log: " + t + ")";
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
