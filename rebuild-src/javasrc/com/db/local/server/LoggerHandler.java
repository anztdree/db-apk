package com.db.local.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;

/**
 * Listener 4000 (fallback 4001 bila bind gagal) — logger/monitoring 2 MODE (16.6):
 *   GET /                             → dashboard HTML (status listener + cara pakai)
 *   GET /stats                        → JSON counter per layanan
 *   GET /health                       → JSON status bind semua port + info ekstraksi
 *   GET /logs?mode=all&limit=N        → mode ALL: kronologis lintas port layanan
 *   GET /logs?mode=port&port=P&limit=N → mode PORT: rincian satu port
 *                                       (9527 menyertakan extra = JSON config ter-decode)
 * Body preview dibatasi 2 KB (LoggerCore.BODY_PREVIEW_MAX).
 */
public final class LoggerHandler implements HttpWorker.Handler {
    private final int port;

    public LoggerHandler(int port) { this.port = port; }

    @Override
    public boolean handle(HttpWorker.Request req, OutputStream out, Socket sock) throws IOException {
        long t0 = System.currentTimeMillis();
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = port;
        e.method = req.method;
        e.path = req.rawPath;
        e.reqLen = req.body.length;

        String p = req.path;
        Map<String, String> h;
        byte[] body;
        int status;

        if ("/".equals(p) || "".equals(p)) {
            status = 200;
            h = HttpWorker.baseHeaders("text/html; charset=utf-8");
            body = HttpWorker.text(dashboardHtml());
        } else if ("/stats".equals(p)) {
            status = 200;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(LoggerCore.statsJson());
        } else if ("/health".equals(p)) {
            status = 200;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(LoggerCore.healthJson());
        } else if ("/logs".equals(p)) {
            String mode = req.q("mode") == null ? "all" : req.q("mode");
            int limit = 200;
            try {
                if (req.q("limit") != null) limit = Integer.parseInt(req.q("limit"));
            } catch (NumberFormatException nfe) { limit = 200; }
            limit = Math.max(1, Math.min(limit, 2000));
            int portFilter = 0;
            try {
                if (req.q("port") != null) portFilter = Integer.parseInt(req.q("port"));
            } catch (NumberFormatException nfe) { portFilter = 0; }

            ArrayList<LoggerCore.Entry> list;
            if ("port".equals(mode)) {
                if (portFilter != LoggerCore.PORT_CONFIG && portFilter != LoggerCore.PORT_CDN
                        && portFilter != LoggerCore.PORT_LOGIN && portFilter != LoggerCore.PORT_SDK) {
                    status = 400;
                    h = HttpWorker.baseHeaders("application/json");
                    body = HttpWorker.text("{\"error\":\"port harus 9527|9528|5050|9530\"}");
                } else {
                    status = 200;
                    h = HttpWorker.baseHeaders("application/json");
                    list = LoggerCore.latestPort(portFilter, limit);
                    body = HttpWorker.text(entriesJson(mode, portFilter, list));
                }
            } else {
                status = 200;
                h = HttpWorker.baseHeaders("application/json");
                list = LoggerCore.latestGlobal(limit);
                body = HttpWorker.text(entriesJson(mode, 0, list));
            }
        } else {
            status = 404;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text("{\"error\":\"gunakan / , /stats , /health , /logs?mode=all|port&port=..&limit=N\"}");
        }

        h.put("Access-Control-Allow-Origin", "*");
        HttpWorker.writeResponse(out, status, status == 200 || status == 400 ? "OK" : "Not Found",
                h, body);
        e.status = status;
        e.respLen = body.length;
        if (body.length <= LoggerCore.BODY_PREVIEW_MAX) e.respPreview = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        else e.respPreview = new String(body, 0, LoggerCore.BODY_PREVIEW_MAX, java.nio.charset.StandardCharsets.UTF_8) + "…";
        e.durMs = System.currentTimeMillis() - t0;
        LoggerCore.log(e);
        return true;
    }

    private String entriesJson(String mode, int port, ArrayList<LoggerCore.Entry> list) {
        StringBuilder b = new StringBuilder(64 + list.size() * 256);
        b.append("{\"mode\":\"").append(mode).append('"');
        if (port > 0) b.append(",\"port\":").append(port);
        b.append(",\"count\":").append(list.size())
         .append(",\"order\":\"kronologis naik (lama → baru)\",\"entries\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(',');
            b.append(list.get(i).toJson());
        }
        b.append("]}");
        return b.toString();
    }

    private String dashboardHtml() {
        LoggerCore.Entry[] all = LoggerCore.latestGlobal(50)
                .toArray(new LoggerCore.Entry[0]);
        StringBuilder rows = new StringBuilder();
        for (int i = all.length - 1; i >= 0; i--) {
            LoggerCore.Entry x = all[i];
            rows.append("<tr><td>").append(x.time()).append("</td><td>").append(x.port)
                .append("</td><td>").append(x.method).append(" ").append(x.path)
                .append("</td><td>").append(x.status).append("</td><td>").append(x.durMs)
                .append("ms</td></tr>");
        }
        StringBuilder b = new StringBuilder(2048);
        b.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>db-local mini-server logger</title>")
         .append("<style>body{font-family:monospace;background:#111;color:#ddd;padding:16px}")
         .append("table{border-collapse:collapse;width:100%}td,th{border:1px solid #444;padding:4px 8px;font-size:12px}")
         .append("h2{color:#9c9}code{color:#fc6}</style></head><body>")
         .append("<h2>db-local mini-server — logger (komponen BARU, bukan fakta APK asli)</h2>")
         .append("<p>Listener: 9527 config bin · 9528 CDN game · 5050 login socket.io EIO=3 · 9530 API SDK · ")
         .append("port ini = logger (").append(port).append(LoggerCore.fallbackLogger ? ", FALLBACK dari 4000" : "").append(")</p>")
         .append("<p>Mode 2 arah (readme 16.6):<br>")
         .append("mode ALL (alur lintas port): <code>GET /logs?mode=all&amp;limit=200</code><br>")
         .append("mode PORT (rincian satu port): <code>GET /logs?mode=port&amp;port=9527&amp;limit=200</code> — ")
         .append("9527 menyertakan JSON config ter-decode (XOR DragonBall)</p>")
         .append("<p><a href=\"/stats\">/stats</a> · <a href=\"/health\">/health</a></p>")
         .append("<h3>50 request terakhir (baru → lama)</h3>")
         .append("<table><tr><th>waktu</th><th>port</th><th>request</th><th>status</th><th>durasi</th></tr>")
         .append(rows)
         .append("</table></body></html>");
        return b.toString();
    }
}
