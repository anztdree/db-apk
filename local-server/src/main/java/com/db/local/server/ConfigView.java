package com.db.local.server;

import java.util.ArrayList;

/**
 * 0.5.4 — ISI CONFIG UTK DASBOR (show/hide), KELUAR DARI FEED LOG.
 *
 * Koreksi owner: "log isi config di ikutkan ngapain di pisah, kalau isi config
 * besar ya bisa show hide" — 0.5.2 membuang bedah field-per-field (ConfigAkar)
 * ke FEED log; feed jadi banjir belasan baris per config, bahkan muncul 4×
 * beruntun saat boot. Sekarang:
 *
 *   - FEED log   : SATU baris padat per config yang disajikan (📦 — nama file,
 *                  ukuran, ms, arah tulis-ulang). Isi TIDAK dibuang ke feed.
 *   - Seksi CONFIG dasbor: bedah field-per-field + teks config murni, TERSIMPAN
 *                  di sini, tampil dengan show/hide (diklik baru terbuka).
 *
 * Yang menyimpan: GameServer (setting bin + serversetting — mode LOCAL & ONLINE).
 * API dasbor: GET /api/config (LocalServer) → JSON daftar entri.
 * Ring (memori): maks 12 entri per id (terbaru menang) — cukup untuk semua
 * file config aktif; tidak menulis file (isi config juga tetap utuh di
 * human.log lewat baris TEKNIS ringkas, dan payload file ada di disk).
 */
public final class ConfigView {

    private static final int RING = 12;
    private static final Object LOCK = new Object();
    private static final ArrayList<Entry> ENTRIES = new ArrayList<Entry>(RING + 4);

    private static final class Entry {
        final String id;      // kunci stabil ("setting-bin", "serversetting", ...)
        final String title;   // judul di dasbor ("setting_BS_Android.bin")
        final String sub;     // satu baris sumber ("OFFLINE · paket manual config-offline/ · 668 B · 1 ms")
        final String body;    // isi penuh (bedah field-per-field / teks config)
        final long at;
        Entry(String id, String title, String sub, String body) {
            this.id = id;
            this.title = title;
            this.sub = sub;
            this.body = body;
            this.at = System.currentTimeMillis();
        }
    }

    private ConfigView() {
    }

    /**
     * Simpan/perbarui satu entri config (terbaru menang per id).
     * Body null/kosong → entri tetap dibuat (dasbor menampilkan "(kosong)").
     */
    public static void put(String id, String title, String sub, String body) {
        if (id == null || id.length() == 0) {
            return;
        }
        synchronized (LOCK) {
            for (int i = ENTRIES.size() - 1; i >= 0; i--) {
                if (id.equals(ENTRIES.get(i).id)) {
                    ENTRIES.remove(i);
                    break;
                }
            }
            ENTRIES.add(new Entry(id, title == null ? id : title,
                    sub == null ? "" : sub, body == null ? "" : body));
            while (ENTRIES.size() > RING) {
                ENTRIES.remove(0);
            }
        }
    }

    /** Snapshot JSON utk dasbor (/api/config) — urut waktu pasang (tertua dulu). */
    public static String toJson() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"entries\":[");
        boolean first = true;
        synchronized (LOCK) {
            for (int i = 0; i < ENTRIES.size(); i++) {
                Entry e = ENTRIES.get(i);
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"id\":\"").append(Json.esc(e.id)).append('"')
                        .append(",\"title\":\"").append(Json.esc(e.title)).append('"')
                        .append(",\"sub\":\"").append(Json.esc(e.sub)).append('"')
                        .append(",\"body\":\"").append(Json.esc(e.body)).append('"')
                        .append('}');
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Kosongkan tampilan (dasbor "Bersihkan"). */
    public static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
        }
    }
}
