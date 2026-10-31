package com.db.local.server;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Server HTTP lokal (NanoHTTPD) yang menghidupkan dasbor di port 4000.
 * Mode M1: server ini HANYA melayani dasbor & API penunjang;
 * traffic game tidak pernah lewat sini dan tidak diubah sama sekali.
 */
public final class LocalServer extends NanoHTTPD {
    private final Context appCtx;
    private volatile byte[] dashboard = null;

    private static final String FALLBACK_HTML =
            "<!doctype html><html lang=\"id\"><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<title>DB LOCAL · Server Lokal</title></head>"
                    + "<body style=\"font-family:sans-serif;padding:24px\">"
                    + "<h2>Server lokal HIDUP</h2>"
                    + "<p>Mode M1 — traffic game tidak diubah.</p>"
                    + "<p>Halaman dasbor utama gagal dimuat dari APK. "
                    + "API tetap tersedia: <code>/api/status</code>, <code>/api/logs</code>.</p>"
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
            HumanLog.ev('✔', "Dasbor dimuat dari APK (assets/server/dashboard.html · "
                    + HumanLog.fmtBytes(dashboard.length) + ")", null);
        } catch (Throwable t) {
            dashboard = FALLBACK_HTML.getBytes();
            HumanLog.ev('⚠', "Dasbor utama gagal dimuat dari APK — memakai halaman darurat sederhana",
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
            if (Method.GET != m && Method.HEAD != m) {
                status = 405;
                return jsonResp(Response.Status.METHOD_NOT_ALLOWED,
                        "{\"error\":\"Metode " + m + " tidak didukung — dasbor hanya butuh GET\"}");
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
            if ("/api/logs/clear".equals(uri)) {
                HumanLog.clearMem();
                HumanLog.info("Log di dasbor dibersihkan oleh pengguna (file human.log tetap tersimpan)");
                String j = "{\"ok\":true}";
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            if ("/api/monitor/run".equals(uri)) {
                Monitor mtr = Starter.monitor();
                if (mtr != null) {
                    mtr.runOnce();
                }
                String j = StatusStore.toJson();
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            if ("/api/apk/list".equals(uri)) {
                String prefix = first(session.getParameters().get("prefix"));
                int limit = (int) parseLong(session.getParameters().get("limit"));
                String j = ApkReader.listJson(prefix, limit);
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            if ("/api/apk/get".equals(uri)) {
                String path = first(session.getParameters().get("path"));
                if (path == null || path.length() == 0) {
                    status = 400;
                    return jsonResp(Response.Status.BAD_REQUEST,
                            "{\"error\":\"Parameter path wajib — contoh: /api/apk/get?path=assets/config.properties\"}");
                }
                byte[] b = ApkReader.get(path);
                if (b == null) {
                    status = 404;
                    return jsonResp(Response.Status.NOT_FOUND,
                            "{\"error\":\"File tidak ada di APK: " + Json.esc(path) + "\"}");
                }
                if (b.length == 0) {
                    status = 403;
                    return jsonResp(Response.Status.FORBIDDEN,
                            "{\"error\":\"File terlalu besar untuk pratinjau (>8 MB): " + Json.esc(path) + "\"}");
                }
                bytes = b.length;
                return fileResp(b, path);
            }
            if ("/api/analyze".equals(uri)) {
                String j = ApkReader.analyzeJson();
                bytes = j.length();
                return jsonResp(Response.Status.OK, j);
            }
            status = 404;
            return jsonResp(Response.Status.NOT_FOUND,
                    "{\"error\":\"Endpoint tidak dikenal: " + m + " " + Json.esc(uri)
                            + " — ini server DASBOR lokal (mode M1), bukan pengganti server game; "
                            + "traffic game tidak pernah lewat sini\"}");
        } catch (Throwable t) {
            status = 500;
            StatusStore.countErr();
            try {
                HumanLog.ev('⚠', "Permintaan dasbor " + uri + " gagal diproses — dijawab 500",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
            return jsonResp(Response.Status.INTERNAL_ERROR,
                    "{\"error\":\"Kesalahan internal: " + Json.esc(String.valueOf(t)) + "\"}");
        } finally {
            long ms = System.currentTimeMillis() - t0;
            StatusStore.countReq();
            try {
                HumanLog.req(m.toString(), uri, joinParams(session.getParameters()), status, ms, bytes);
            } catch (Throwable ignored) {
            }
            if (StatusStore.requestTick()) {
                StatusStore.persist();
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

    private static Response fileResp(byte[] b, String path) {
        Response r = newFixedLengthResponse(Response.Status.OK, "application/octet-stream",
                new ByteArrayInputStream(b), b.length);
        String fname = path.substring(path.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        r.addHeader("Content-Disposition", "attachment; filename=\"" + fname + "\"");
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

    private static String first(List<String> vals) {
        return (vals == null || vals.isEmpty()) ? null : vals.get(0);
    }
}
