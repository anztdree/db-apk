package com.db.local.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Arsip log NDJSON ke disk (BARU — Bagian 18, jawaban "log itu utama").
 *
 * v22 (Bagian 70) — LOG = 1 FILE DI-TUMPUK (arahan owner, kata-kata persis:
 * "LOG ITU 1 FILE DI TUMPUK !! BUKAN MALAH BIKIN BARU TIAP MASUK APLIKASI!!"):
 *   - SALAH v18-v21: tiap open APK membuat FILE BARU log-<waktu-open>.ndjson
 *     (+ part rotasi 16 MB, + prune 8 file) → 4+ file log tergantung banyaknya
 *     buka APK — tidak efektif, tidak efisien.
 *   - BENAR v22: SATU file <serverRoot>/logs/server.ndjson, nama TETAP tanpa
 *     timestamp; tiap buka APK TIDAK membuat file baru — APPEND ke file yang
 *     SAMA (baris tersusun kronologis lintas sesi: BOOT menandai batas sesi).
 *   - NOL rotasi, NOL part, NOL prune: file ditumpuk terus; pemilik bebas
 *     menghapus/mengunduhnya kapan pun (dashboard 4000 → unduh).
 *   - Penulisan = SATU syscall write() per baris pada fd APPEND yang tetap
 *     terbuka: atomik per baris (aman antar-proses), selamat app mati mendadak
 *     tanpa flush eksplisit (BufferWriter lama dibuang — buffer parsial bisa
 *     terpotong di tengah baris bila proses mati).
 *   - file log-*.ndjson LAMA (v18-v21) TIDAK disentuh — tetap terbaca via
 *     /download?file=NAME (nama legacy diizinkan), tapi tidak pernah ditulis lagi.
 *  - thread tunggal + queue 20000 baris: request handler TIDAK pernah blok I/O
 *  - SEMUA exception ditelan + dicatat di lastError (tidak pernah crash app)
 *  - diunduh via logger: GET /download (list), /download?latest=1, /download?file=NAME
 *  - murni java.io (tanpa java.nio.file — API 26+) agar aman minSdk 21.
 */
public final class DiskWriter implements Runnable {
    public static volatile DiskWriter INSTANCE;
    /** v22: SATU nama file tetap — di-tumpuk (append) lintas sesi/APK open */
    public static final String SERVER_LOG = "server.ndjson";
    private static final int QUEUE_CAP = 20000;

    private final File dir;
    private final LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>(QUEUE_CAP);
    private FileOutputStream out;   // v22: fd append tetap terbuka — 1 write per baris
    private File cur;
    private long curBytes;
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
        while (running) {
            try {
                String line = q.poll(1, TimeUnit.SECONDS);
                if (line == null) continue;
                writeLine(line);
            } catch (InterruptedException ie) {
                // lanjut
            } catch (Throwable t) {
                lastError = "loop: " + t;
                try { if (out != null) out.close(); } catch (IOException ignored) {}
                out = null;
            }
        }
    }

    private void writeLine(String line) throws IOException {
        if (out == null) openCur();
        byte[] b = (line + "\n").getBytes(StandardCharsets.UTF_8);
        out.write(b);          // SATU syscall per baris (fd APPEND) — atomik per baris
        lines++;
        curBytes += b.length;
    }

    private void openCur() throws IOException {
        cur = new File(dir, SERVER_LOG);   // v22: nama TETAP — bukan file baru per open
        out = new FileOutputStream(cur, true);   // APPEND — baris lama dihormati
        curBytes = cur.length();
    }

    /** daftar arsip (nama, ukuran, waktu-modifikasi) — v22: hanya server.ndjson */
    public static String listJson() {
        DiskWriter d = INSTANCE;
        StringBuilder b = new StringBuilder(256);
        b.append("{\"dir\":\"");
        b.append(d == null ? "" : JsonUtil.esc(d.dir.getAbsolutePath()));
        b.append("\",\"files\":[");
        if (d != null) {
            File f = new File(d.dir, SERVER_LOG);
            if (f.isFile()) {
                b.append("{\"name\":\"").append(JsonUtil.esc(f.getName()))
                 .append("\",\"bytes\":").append(f.length())
                 .append(",\"modified\":").append(f.lastModified()).append('}');
            }
        }
        b.append("]}");
        return b.toString();
    }

    /** file arsip TERBARU (untuk /download?latest=1) = server.ndjson, atau null */
    public static File newestFile() {
        DiskWriter d = INSTANCE;
        if (d == null) return null;
        File f = new File(d.dir, SERVER_LOG);
        return f.isFile() ? f : null;
    }

    /** file arsip berdasar nama tervalidasi (anti path traversal).
     *  v22: server.ndjson + legacy log-*.ndjson (baca-saja, sisa v18-v21). */
    public static File fileByName(String name) {
        DiskWriter d = INSTANCE;
        if (d == null || name == null) return null;
        if (!name.matches("[A-Za-z0-9._\\-]{1,128}")) return null;
        boolean legacy = name.startsWith("log-") && name.endsWith(".ndjson");
        if (!SERVER_LOG.equals(name) && !legacy) return null;
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
                    + ",\"file\":\"" + JsonUtil.esc(d.cur == null ? SERVER_LOG : d.cur.getName()) + '"'
                    + ",\"bytes\":" + (d.cur == null ? 0 : d.cur.length())
                    + ",\"lines\":" + d.lines
                    + ",\"dropped\":" + d.dropped
                    + ",\"queue\":" + d.q.size()
                    + ",\"error\":\"" + JsonUtil.esc(d.lastError) + "\"}";
        }
    }
}
