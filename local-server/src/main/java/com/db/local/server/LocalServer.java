package com.db.local.server;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Server HTTP lokal (NanoHTTPD) untuk DASBOR DEBUG di port 4000 — khusus developer.
 * Port ini TIDAK diketahui game; permintaan client game dilayani port game terpisah
 * (GameServer, default 8080). Polling dasbor (/api/*) TIDAK dicatat sebagai event
 * agar log tetap tenang — hanya kejadian nyata yang dicatat.
 *
 * Endpoint (README §6):
 *   GET  /                → halaman dasbor
 *   GET  /api/status      → status server + mode (baca-saja) + port game + 5 path terakhir
 *   GET  /api/logs?since= → log baru sejak id tertentu (mode BERSIH: penting saja)
 *   GET  /api/groups      → snapshot GRUP batch (Mirror CDN/Request game/404/
 *                           Passthrough) 0.4.6 — judul + statistik + rincian item
 *   GET  /api/logs/clear  → bersihkan log di dasbor (file tetap; grup ikut kosong)
 *   GET  /api/logs/file   → EKOR human.log penuh (penting+teknis) — sumber "Copy penuh" 0.4.5
 *   GET  /api/logtech     → mode log RINCI on/off (?on=1|0) — tersimpan di server.json 0.4.5
 *   GET  /api/config      → 0.5.4 ISI CONFIG utk seksi CONFIG dasbor (show/hide):
 *                           bedah field-per-field + teks config yang disajikan
 *                           (ConfigView — setting bin, serversetting, servers)
 *   GET  /api/probe/game  → 0.5.4 uji kesehatan upstream server game (LOCAL)
 *   GET  /api/www/list    → isi folder www/ (bukti folder bersih & lengkap)
 *   Endpoint /api/rekam* DIHAPUS 0.5.4 — REKAM dihapus total (CONFIG bukan
 *   rekam jejak); kebutuhan tangkapan trafik dipenuhi baris 🌐/📄/🎮 di feed.
 *   POST /api/mode DIHAPUS (koreksi owner 0.3.2 — "website pindah mode itu salah
 *   cuma menuhin tempat"): website TANPA switch mode; ganti mode cukup lewat
 *   tombol melayang di layar game. Endpoint /api/apk/*, /api/analyze,
 *   /api/monitor/run versi 0.1.0 juga sudah DIHAPUS (sumber sampah Android/data).
 */
public final class LocalServer extends NanoHTTPD {
    private final Context appCtx;
    private volatile byte[] dashboard = null;

    private static final String FALLBACK_HTML =
            "<!doctype html><html lang=\"id\"><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<title>DB LOCAL · Server Lokal</title></head>"
                    + "<body style=\"font-family:sans-serif;padding:24px\">"
                    + "<h2>Dasbor debug HIDUP</h2>"
                    + "<p>Halaman dasbor utama gagal dimuat dari APK. "
                    + "API tetap tersedia: <code>/api/status</code>, <code>/api/logs</code>. "
                    + "Port game terpisah — lihat /api/status.</p>"
                    + "</body></html>";

    public LocalServer(Context appCtx, String bind, int port) throws java.io.IOException {
        super(bind, port);
        this.appCtx = appCtx;
        setTempFileManagerFactory(new TempFileManagerFactory() {
            public TempFileManager create() {
                return new DefaultTempFileManager();
            }
        });
    }

    public void loadDashboard() {
        try {
            InputStream in = appCtx.getAssets().open("server/dashboard.html");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            dashboard = bos.toByteArray();
            // 0.4.5: ukuran dasbor = TEKNIS (bukan berita penting)
            HumanLog.evTech("ℹ️", "Dasbor dimuat dari APK (assets/server/dashboard.html · "
                    + HumanLog.fmtBytes(dashboard.length) + ")", null);
        } catch (Throwable t) {
            dashboard = FALLBACK_HTML.getBytes();
            HumanLog.ev("⚠️", "Dasbor utama gagal dimuat dari APK — memakai halaman darurat sederhana",
                    String.valueOf(t));
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        long t0 = System.currentTimeMillis();
        Method m = session.getMethod();
        String uri = session.getUri();
        int status = 200;
        long bytes = 0;
        try {
            if (Method.GET != m && Method.HEAD != m && Method.POST != m) {
                status = 405;
                return jsonResp(Response.Status.METHOD_NOT_ALLOWED,
                        "{\"error\":\"Metode " + m + " tidak didukung — dasbor butuh GET/POST\"}");
            }
            if ("/".equals(uri) || "/index.html".equals(uri)) {
                byte[] d = dashboard;
                if (d == null) {
                    d = FALLBACK_HTML.getBytes();
                }
                bytes = d.length;
                return htmlResp(d);
            }
            if ("/api/status".equals(uri)) {
                String j = StatusStore.toJson();
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            if ("/api/logs".equals(uri)) {
                long since = parseLong(session.getParameters().get("since"));
                String j = HumanLog.snapshotJson(since);
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            // 0.4.6 — grup batch untuk dasbor (judul + statistik hidup + rincian)
            if ("/api/groups".equals(uri)) {
                String j = LogGroups.toJson();
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            if ("/api/logs/clear".equals(uri)) {
                HumanLog.clearMem();
                LogGroups.clear();
                ConfigView.clear();
                HumanLog.info("Log di dasbor dibersihkan oleh pengguna (file human.log tetap tersimpan)");
                String j = "{\"ok\":true}";
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            // 0.4.5 — mode log RINCI on/off (toggle dasbor; tersimpan di server.json)
            // 0.4.8: TIDAK lagi jadi baris log — itu umpan balik UI, bukan laporan
            // server (statusnya sudah terlihat di tombol RINCI + tersimpan server.json;
            // tiga baris "Mode log dasbor: …" hanya menambah sampah di alur).
            if ("/api/logtech".equals(uri)) {
                boolean on = parseBool(session.getParameters().get("on"), Config.logTech());
                Config.setLogTech(on);
                HumanLog.setTechEnabled(on);
                String j = "{\"ok\":true,\"tech\":" + on + "}";
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            // 0.4.5 — ekor human.log PENUH (penting + teknis) untuk "Copy penuh"
            if ("/api/logs/file".equals(uri)) {
                int tail = (int) parseLong(session.getParameters().get("tail"));
                if (tail <= 0 || tail > 500) {
                    tail = 300;
                }
                String t = HumanLog.tailText(tail);
                bytes = t.length();
                Response r = newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", t);
                r.addHeader("Cache-Control", "no-store");
                return r;
            }
            if ("/api/www/list".equals(uri)) {
                String j = wwwListJson();
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            // ---------------- 0.5.4 — seksi CONFIG dasbor + probe game ----------------
            if ("/api/config".equals(uri)) {
                String j = ConfigView.toJson();
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            if ("/api/probe/game".equals(uri)) {
                // uji UPSTREAM server game SEKARANG (LOCAL; maks ±5 dtk)
                String res = Monitor.probeGameServers(1);
                String j = "{\"ok\":true,\"result\":\"" + Json.esc(res == null ? "" : res.replace("\n", " | ")) + "\"}";
                bytes = j.length();
                StatusStore.persist();
                return jsonResp(Response.Status.OK, j);
            }
            status = 404;
            return jsonResp(Response.Status.NOT_FOUND,
                    "{\"error\":\"Endpoint tidak dikenal: " + m + " " + Json.esc(uri)
                            + " — ini DASBOR debug di port " + Config.debugPort()
                            + "; port GAME terpisah di port " + Config.gamePort()
                            + " (client menunjuk ke sana)\"}");
        } catch (Throwable t) {
            status = 500;
            StatusStore.countErr();
            try {
                HumanLog.ev("⚠️", "Permintaan dasbor " + uri + " gagal diproses — dijawab 500",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
            return jsonResp(Response.Status.INTERNAL_ERROR,
                    "{\"error\":\"Kesalahan internal: " + Json.esc(String.valueOf(t)) + "\"}");
        } finally {
            long ms = System.currentTimeMillis() - t0;
            StatusStore.countReq();
            // Log tenang: polling dasbor (/api/*, /) TIDAK dicatat — itu napas dasbor,
            // bukan kejadian. Yang dicatat: 500 (gagal) dan path aneh (jarang).
            boolean dashboardBreath = uri.startsWith("/api/") || "/".equals(uri)
                    || "/index.html".equals(uri);
            if (!dashboardBreath || status >= 500) {
                try {
                    HumanLog.req(m.toString(), uri, joinParams(session.getParameters()), status, ms, bytes);
                } catch (Throwable ignored) {
                }
            }
            if (StatusStore.requestTick()) {
                StatusStore.persist();
            }
        }
    }

    /** JSON isi folder www/ — daftar file + ukuran; bukti folder root bersih & lengkap. */
    private String wwwListJson() {
        File root = Starter.serverRoot();
        File www = (root != null) ? new File(root, "www") : null;
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\"files\":[");
        final int[] count = {0};
        final long[] total = {0};
        if (www != null && www.exists()) {
            walk(www, www, sb, count, total);
        }
        sb.append("],")
                .append(Json.num("count", count[0])).append(',')
                .append(Json.num("bytes", total[0])).append(',')
                .append(Json.str("path", www != null ? www.getAbsolutePath() : ""))
                .append('}');
        return sb.toString();
    }

    private static void walk(File www, File dir, StringBuilder sb, int[] count, long[] total) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (int i = 0; i < kids.length; i++) {
            File k = kids[i];
            if (k.isDirectory()) {
                walk(www, k, sb, count, total);
            } else if (k.isFile()) {
                if (count[0] > 0) {
                    sb.append(',');
                }
                String rel = www.toURI().relativize(k.toURI()).getPath();
                sb.append("{\"p\":\"").append(Json.esc(rel)).append('\"')
                        .append(",\"s\":").append(k.length()).append('}');
                count[0]++;
                total[0] += k.length();
            }
        }
    }

    private static String joinParams(Map<String, List<String>> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(128);
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            String v = first(e.getValue());
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(e.getKey()).append("=").append(v == null ? "" : v);
        }
        String s = sb.toString();
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

    private static Response jsonResp(Response.IStatus st, String json) {
        Response r = newFixedLengthResponse(st, "application/json; charset=utf-8", json);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private Response htmlResp(byte[] d) {
        Response r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8",
                new ByteArrayInputStream(d), d.length);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private static long parseLong(List<String> vals) {
        String v = first(vals);
        if (v == null) {
            return 0;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean parseBool(List<String> vals, boolean def) {
        String v = first(vals);
        if (v == null || v.trim().length() == 0) {
            return def;
        }
        return "1".equals(v.trim()) || "true".equalsIgnoreCase(v.trim());
    }

    private static String first(List<String> vals) {
        return (vals == null || vals.isEmpty()) ? null : vals.get(0);
    }
}
