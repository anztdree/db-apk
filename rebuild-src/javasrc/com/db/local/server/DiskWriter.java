package com.db.local.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Arsip log NDJSON ke disk (BARU — Bagian 18, jawaban "log itu utama"):
 *  - setiap entri LoggerCore = 1 baris JSON di <serverRoot>/logs/log-<waktu-boot>.ndjson
 *  - thread tunggal + queue 20000 baris: request handler TIDAK pernah blok I/O
 *  - flush per baris → data selamat walau proses app mati mendadak
 *  - rotasi: file > 16 MB → part berikutnya; maks 8 file (tertua dihapus)
 *  - SEMUA exception ditelan + dicatat di lastError (tidak pernah crash app)
 *  - diunduh via logger: GET /download (list), /download?latest=1, /download?file=NAME
 *  - murni java.io (tanpa java.nio.file — API 26+) agar aman minSdk 21.
 */
public final class DiskWriter implements Runnable {
    public static volatile DiskWriter INSTANCE;
    public static final long ROTATE_BYTES = 16L * 1024 * 1024;
    public static final int MAX_FILES = 8;
    private static final int QUEUE_CAP = 20000;

    private final File dir;
    private final LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>(QUEUE_CAP);
    private BufferedWriter w;
    private File cur;
    private String baseName = "";
    private long curBytes;
    private int part;
    public long lines;
    public long dropped;
    public String lastError = "";
    private volatile boolean running = true;

    private DiskWriter(File dir) { this.dir = dir; }

    /** mulai writer; aman dipanggil ulang (idempotent); gagal → lastError, tanpa throw */
    public static void init(File dir) {
        if (INSTANCE != null) return;
        try {
            if (!dir.exists()) dir.mkdirs();
            INSTANCE = new DiskWriter(dir);
            Thread t = new Thread(INSTANCE, "logger-disk");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            DiskWriter d = INSTANCE;
            if (d != null) d.lastError = "init: " + t;
        }
    }

    public void enqueue(String line) {
        if (!q.offer(line)) dropped++;
    }

    @Override
    public void run() {
        try {
            openNew();
        } catch (Throwable t) {
            lastError = "openNew: " + t;
        }
        while (running) {
            try {
                String line = q.poll(1, TimeUnit.SECONDS);
                if (line == null) continue;
                writeLine(line);
            } catch (InterruptedException ie) {
                // lanjut
            } catch (Throwable t) {
                lastError = "loop: " + t;
                try { if (w != null) w.close(); } catch (IOException ignored) {}
                w = null;
            }
        }
    }

    private void writeLine(String line) throws IOException {
        if (w == null) openNew();
        w.write(line);
        w.write('\n');
        w.flush();
        lines++;
        curBytes += line.length() + 1;
        if (curBytes >= ROTATE_BYTES) rotatePart();
    }

    private static String tsName() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format("log-%04d%02d%02d-%02d%02d%02d",
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH),
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE),
                c.get(java.util.Calendar.SECOND));
    }

    private void openNew() throws IOException {
        baseName = tsName();
        part = 1;
        openCur();
        prune();
    }

    private void rotatePart() throws IOException {
        try { if (w != null) w.close(); } catch (IOException ignored) {}
        w = null;
        part++;
        openCur();
        prune();
    }

    private void openCur() throws IOException {
        String name = part == 1 ? baseName + ".ndjson" : baseName + "-part" + part + ".ndjson";
        cur = new File(dir, name);
        w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cur, true),
                StandardCharsets.UTF_8), 131072);
        curBytes = cur.length();
    }

    /** simpan maks MAX_FILES file arsip; hapus tertua */
    private void prune() {
        try {
            File[] fs = dir.listFiles();
            if (fs == null) return;
            java.util.ArrayList<File> logs = new java.util.ArrayList<>();
            for (File f : fs) if (f.isFile() && f.getName().startsWith("log-") && f.getName().endsWith(".ndjson")) logs.add(f);
            if (logs.size() <= MAX_FILES) return;
            Arrays.sort(logs.toArray(new File[0]), new Comparator<File>() {
                public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
            });
            int over = logs.size() - MAX_FILES;
            for (int i = 0; i < over; i++) logs.get(i).delete();
        } catch (Throwable t) {
            lastError = "prune: " + t;
        }
    }

    /** daftar file arsip (nama, ukuran, waktu-modifikasi) — terurut nama naik */
    public static String listJson() {
        DiskWriter d = INSTANCE;
        StringBuilder b = new StringBuilder(256);
        b.append("{\"dir\":\"");
        b.append(d == null ? "" : JsonUtil.esc(d.dir.getAbsolutePath()));
        b.append("\",\"files\":[");
        if (d != null) {
            File[] fs = d.dir.listFiles();
            java.util.ArrayList<File> logs = new java.util.ArrayList<>();
            if (fs != null) {
                for (File f : fs) if (f.isFile() && f.getName().startsWith("log-") && f.getName().endsWith(".ndjson")) logs.add(f);
            }
            java.util.ArrayList<File> arr = new java.util.ArrayList<>(logs);
            java.util.Collections.sort(arr, new Comparator<File>() {
                public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
            });
            for (int i = 0; i < arr.size(); i++) {
                File f = arr.get(i);
                if (i > 0) b.append(',');
                b.append("{\"name\":\"").append(JsonUtil.esc(f.getName()))
                 .append("\",\"bytes\":").append(f.length())
                 .append(",\"modified\":").append(f.lastModified()).append('}');
            }
        }
        b.append("]}");
        return b.toString();
    }

    /** file arsip TERBARU (untuk /download?latest=1) atau null */
    public static File newestFile() {
        DiskWriter d = INSTANCE;
        if (d == null) return null;
        File[] fs = d.dir.listFiles();
        File newest = null;
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && f.getName().startsWith("log-") && f.getName().endsWith(".ndjson")) {
                    if (newest == null || f.getName().compareTo(newest.getName()) > 0) newest = f;
                }
            }
        }
        return newest;
    }

    /** file arsip berdasar nama tervalidasi (anti path traversal) */
    public static File fileByName(String name) {
        DiskWriter d = INSTANCE;
        if (d == null || name == null) return null;
        if (!name.matches("[A-Za-z0-9._\\-]{1,128}")) return null;
        if (!name.startsWith("log-") || !name.endsWith(".ndjson")) return null;
        File f = new File(d.dir, name);
        try {
            if (!f.getCanonicalPath().startsWith(d.dir.getCanonicalPath() + File.separator)
                    && !f.getCanonicalPath().equals(d.dir.getCanonicalPath())) return null;
        } catch (IOException ioe) { return null; }
        return f.isFile() ? f : null;
    }

    /** fragmen JSON status disk untuk /stats & /health */
    public static String statsJson() {
        DiskWriter d = INSTANCE;
        if (d == null) return "{\"enabled\":false,\"lines\":0,\"dropped\":0,\"error\":\"belum init\"}";
        synchronized (d) {
            return "{\"enabled\":true"
                    + ",\"dir\":\"" + JsonUtil.esc(d.dir.getAbsolutePath()) + '"'
                    + ",\"file\":\"" + JsonUtil.esc(d.cur == null ? "" : d.cur.getName()) + '"'
                    + ",\"bytes\":" + (d.cur == null ? 0 : d.cur.length())
                    + ",\"lines\":" + d.lines
                    + ",\"dropped\":" + d.dropped
                    + ",\"queue\":" + d.q.size()
                    + ",\"error\":\"" + JsonUtil.esc(d.lastError) + "\"}";
        }
    }
}
