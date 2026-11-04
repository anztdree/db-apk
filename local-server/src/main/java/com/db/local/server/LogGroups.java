package com.db.local.server;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GRUP LOG BATCH (0.4.6 — model final log, hasil kesepakatan dengan owner:
 * "group itu kalau terdeteksi emang config/resource yg banyak. kalau alur ya
 * gk usah di group").
 *
 * PEMBAGIAN TUGAS:
 * - ALUR (boot → SDK → login → masuk game) = baris linear HumanLog.ev —
 *   TIDAK pernah masuk grup.
 * - AKTIVITAS BATCH (mirror CDN per-file, request game per-file, 404
 *   fallback, passthrough ONLINE) = GRUP di sini: satu baris judul dengan
 *   statistik hidup; dasbor bisa membukanya untuk rincian per-file.
 *
 * ATURAN YANG DISEPAKATI:
 *   1. Grup kosong TIDAK ditampilkan (tidak ada item → tidak ada baris).
 *   2. Baris GAGAL naik sebagai ⚠ biasa di log linear — laporan PERTAMA
 *      tampil, beruntun sesudahnya hanya menaikkan hitungan "N gagal" di
 *      judul grup (CDN mati tidak membanjiri log 100 baris kembar).
 *   3. human.log = cerita untuk ditempel: grup ditulis SEKALI sebagai
 *      ringkasan saat aktivitas diam ±45 dtk, contoh:
 *      "Mirror CDN: 470 file · 84,2 MB · 0 gagal (3 dtk) — rincian di dasbor".
 *      Rincian per-file tidak masuk file — tinggal di dasbor.
 *   4. Memori dasbor: maksimal 600 item per grup (tertua dibuang).
 *
 * Item disimpan sebagai String[]{waktu, teks, ok(1/0)} — kecil dan cukup
 * untuk daftar rincian dasbor. Semua akses dijaga satu LOCK.
 */
public final class LogGroups {

    /** Batas item per grup yang disimpan di memori dasbor. */
    private static final int ITEM_CAP = 600;
    /** Grup dianggap "selesai sendirinya" setelah selama ini tanpa item baru. */
    private static final long QUIET_MS = 45000L;
    /** Jeda minimum laporan ⚠ naik-log antar kegagalan satu grup. */
    private static final long FLOAT_THROTTLE_MS = 30000L;

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, Group> GROUPS = new LinkedHashMap<String, Group>();
    private static volatile boolean flusherOn = false;

    private static final class Group {
        final String id;
        final String title;
        final String unit;
        long firstAt = 0;
        long lastAt = 0;
        long lastFloatAt = 0;
        boolean flushed = true; // false sejak item pertama burst aktif
        int count = 0;
        int fail = 0;
        long bytes = 0;
        final ArrayDeque<String[]> items = new ArrayDeque<String[]>();

        Group(String id, String title, String unit) {
            this.id = id;
            this.title = title;
            this.unit = unit;
        }
    }

    private LogGroups() {
    }

    /**
     * Catat satu item batch.
     *
     * @param id    kunci grup stabil ("mirror", "req", "404", "pass")
     * @param title judul baris grup di dasbor ("Mirror CDN")
     * @param unit  satuan penghitung ("file" / "request")
     * @param ok    false = GAGAL → naik ⚠ ke log linear (dijeda 30 dtk)
     * @param text  teks baris rincian, sudah diformat ramah
     * @param bytes ukuran item (0 bila tak relevan)
     */
    public static void item(String id, String title, String unit,
                            boolean ok, String text, long bytes) {
        if (id == null || id.length() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            Group g = GROUPS.get(id);
            if (g == null) {
                g = new Group(id, title == null ? id : title, unit == null ? "item" : unit);
                GROUPS.put(id, g);
            }
            if (g.firstAt == 0) {
                g.firstAt = now;
                g.flushed = false;
            }
            g.lastAt = now;
            if (ok) {
                g.count++;
                g.bytes += Math.max(0, bytes);
            } else {
                g.fail++;
            }
            g.items.addLast(new String[]{nowStr(), (ok ? "" : "GAGAL — ") + text, ok ? "1" : "0"});
            while (g.items.size() > ITEM_CAP) {
                g.items.removeFirst();
            }
        }
        if (!ok) {
            floatUp(g(id), now, text);
        }
        ensureFlusher();
    }

    /** Naikkan kegagalan sebagai ⚠ log linear — laporan pertama saja per 30 dtk. */
    private static void floatUp(Group g, long now, String text) {
        boolean mayFloat;
        synchronized (LOCK) {
            mayFloat = now - g.lastFloatAt >= FLOAT_THROTTLE_MS;
            if (mayFloat) {
                g.lastFloatAt = now;
            }
        }
        if (mayFloat) {
            try {
                HumanLog.ev("⚠️", g.title + " gagal — " + text
                        + " (lanjutannya dihitung di judul grup)", null);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Group g(String id) {
        synchronized (LOCK) {
            return GROUPS.get(id);
        }
    }

    /** Snapshot JSON semua grup untuk dasbor (/api/groups). Grup kosong tidak disertakan. */
    public static String toJson() {
        StringBuilder sb = new StringBuilder(1024);
        long now = System.currentTimeMillis();
        sb.append("{\"groups\":[");
        boolean first = true;
        synchronized (LOCK) {
            for (Map.Entry<String, Group> e : GROUPS.entrySet()) {
                Group g = e.getValue();
                if (g.count + g.fail == 0) {
                    continue; // ATURAN 1: grup kosong tidak tampil
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                long dur = (g.lastAt > g.firstAt) ? (g.lastAt - g.firstAt) : 0;
                sb.append("{\"id\":\"").append(Json.esc(g.id)).append('"')
                        .append(",\"title\":\"").append(Json.esc(g.title)).append('"')
                        .append(",\"unit\":\"").append(Json.esc(g.unit)).append('"')
                        .append(",\"count\":").append(g.count)
                        .append(",\"fail\":").append(g.fail)
                        .append(",\"bytes\":").append(g.bytes)
                        .append(",\"durMs\":").append(dur)
                        .append(",\"active\":").append(now - g.lastAt < QUIET_MS)
                        .append(",\"items\":[");
                Iterator<String[]> it = g.items.iterator();
                boolean fi = true;
                while (it.hasNext()) {
                    String[] it3 = it.next();
                    if (!fi) {
                        sb.append(',');
                    }
                    fi = false;
                    sb.append("[\"").append(Json.esc(it3[0])).append('"')
                            .append(",\"").append(Json.esc(it3[1])).append('"')
                            .append(',').append(it3[2]).append(']');
                }
                sb.append("]}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Bersihkan tampilan (dasbor "Bersihkan") — penghitung & item di-reset. */
    public static void clear() {
        synchronized (LOCK) {
            GROUPS.clear();
        }
    }

    // ---------- penulis ringkasan ke human.log ----------

    /**
     * Flusher latar: tiap 15 dtk periksa grup yang sudah diam >45 dtk dan
     * belum ditulis ringkasannya → satu baris Σ ke human.log. Item baru
     * sesudahnya memulai burst baru (ringkasan lagi nanti).
     */
    private static void ensureFlusher() {
        if (flusherOn) {
            return;
        }
        synchronized (LogGroups.class) {
            if (flusherOn) {
                return;
            }
            flusherOn = true;
        }
        try {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(15000L);
                        } catch (InterruptedException ie) {
                            return;
                        }
                        flushQuiet();
                    }
                }
            }, "dblsf-logflush");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        } catch (Throwable ignored) {
            flusherOn = false;
        }
    }

    private static void flushQuiet() {
        long now = System.currentTimeMillis();
        StringBuilder line = null;
        synchronized (LOCK) {
            for (Map.Entry<String, Group> e : GROUPS.entrySet()) {
                Group g = e.getValue();
                if (g.flushed || g.count + g.fail == 0) {
                    continue;
                }
                if (now - g.lastAt < QUIET_MS) {
                    continue; // masih aktif
                }
                g.flushed = true;
                String l = g.title + ": " + g.count + " " + g.unit
                        + (g.fail > 0 ? " · " + g.fail + " gagal" : " OK")
                        + (g.bytes > 0 ? " · " + HumanLog.fmtBytes(g.bytes) : "")
                        + " (" + HumanLog.fmtMs(g.lastAt - g.firstAt) + ") — rincian di dasbor";
                line = (line == null) ? new StringBuilder() : line;
                if (line.length() > 0) {
                    line.append('\n');
                }
                line.append("[Σ] ").append(l);
            }
        }
        if (line != null) {
            String[] rows = line.toString().split("\n");
            for (String row : rows) {
                HumanLog.writeRawLine(dateStr() + " " + nowStr() + " " + row);
            }
        }
    }

    private static String nowStr() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private static String dateStr() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }
}
