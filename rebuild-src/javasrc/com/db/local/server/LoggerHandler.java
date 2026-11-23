package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;

/**
 * Listener 4000 (fallback 4001 bila bind gagal) — logger/monitoring port 4000.
 * Dashboard TERKINI: MONITOR v3 — redesign total Bagian 39 (arahan owner:
 * overlay = INFO bukan error; teks jelas; layout terstruktur; emoji stylish).
 *   GET /                    → dashboard HTML live (tabel 7 kolom beremoji; klik
 *                              baris = blok detail terstruktur 📨REQUEST/📬RESPON/
 *                              🗂️EXTRA/❌ERROR + JSON auto-pretty; SEMUA teks bisa
 *                              disalin: per-baris, per-blok, Salin tabel, Salin
 *                              JSON; fallback clipboard; filter mode/tahap/port/
 *                              status/cari/limit; arsip NDJSON diunduh)
 *   GET /stats               → JSON counter per port + dropped + status disk NDJSON
 *   GET /health              → JSON status bind + ekstraksi + disk
 *   GET /logs?mode=all|port&port=P&limit=N&since=SEQ
 *                            → mode ALL/PORT, kronologis naik, INCREMENTAL via since
 *                              (poll efisien — tidak mengirim ulang yang sudah dilihat);
 *                              gap=true bila ada entri terbuang antara since dan buffer
 *   GET /download            → JSON daftar file arsip NDJSON
 *   GET /download?latest=1   → unduh file arsip terbaru
 *   GET /download?file=NAME  → unduh file arsip tertentu (nama tervalidasi)
 * Request milik logger TIDAK disimpan ke buffer (countSelf saja — anti-pollution).
 */
public final class LoggerHandler implements HttpWorker.Handler {
    private final int port;

    public LoggerHandler(int port) { this.port = port; }

    @Override
    public boolean handle(HttpWorker.Request req, OutputStream out, Socket sock) throws IOException {
        long t0 = System.currentTimeMillis();
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
            int[] lim = {200};        // limit
            long[] since = {0};
            int[] portFilter = {0};
            parseLogsParams(req, lim, since, portFilter);
            status = 200;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(logsJson(lim[0], since[0], portFilter[0]));
        } else if ("/download".equals(p)) {
            return handleDownload(req, out, t0);
        } else if ("/sdkconfig".equals(p)) {
            // Bagian 30 — panel SDK CONFIG (koreksi owner: semua milik SDK di sdk/)
            status = 200;
            h = HttpWorker.baseHeaders("text/html; charset=utf-8");
            body = HttpWorker.text(sdkConfigHtml());
        } else if ("/sdkconfig/api".equals(p)) {
            status = 200;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(sdkConfigApiJson());
        } else if ("/sdkconfig/save".equals(p) && "POST".equalsIgnoreCase(req.method)) {
            String name = formParam(req, "name");
            String content = formParam(req, "content");
            String err = SdkConfig.writeContractFile(name, content);
            status = err == null ? 200 : 400;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(err == null
                    ? "{\"result\":true,\"saved\":\"" + JsonUtil.esc(name) + "\"}"
                    : "{\"result\":false,\"error\":\"" + JsonUtil.esc(err) + "\"}");
        } else if ("/sdkconfig/preset".equals(p) && "POST".equalsIgnoreCase(req.method)) {
            String types = formParam(req, "types");
            String json = SdkConfig.presetInitJson(types);
            String err;
            if (json == null) err = "types tidak valid (contoh: 1 atau 12 atau 1,13)";
            else err = SdkConfig.writeContractFile("init.json", json);
            status = err == null ? 200 : 400;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(err == null
                    ? "{\"result\":true,\"loginTypes\":\"" + JsonUtil.esc(types) + "\"}"
                    : "{\"result\":false,\"error\":\"" + JsonUtil.esc(err) + "\"}");
        } else if ("/sdkconfig/product-add".equals(p) && "POST".equalsIgnoreCase(req.method)) {
            // v16 — tombol TERIMA PRODUCT (banner masalah + panel SDK CONFIG):
            // tambah productCode yang ditolak 30001 ke products di profile.json.
            String code = formParam(req, "code");
            String err = SdkConfig.productAdd(code);
            status = err == null ? 200 : 400;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(err == null
                    ? "{\"result\":true,\"code\":\"" + JsonUtil.esc(code) + "\"}"
                    : "{\"result\":false,\"error\":\"" + JsonUtil.esc(err) + "\"}");
        } else if ("/sdkconfig/session-reset".equals(p) && "POST".equalsIgnoreCase(req.method)) {
            // v18 — RESET SESI SDK: hapus sesi tersimpan (prefs qg_Users + info.qg)
            // -> game berikutnya UI LOGIN MANUAL (jalur aman tanpa izin overlay)
            String err = SessionReset.reset(OverlayFixer.ctx, false);
            status = err == null ? 200 : 400;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(err == null
                    ? "{\"result\":true,\"info\":\"" + JsonUtil.esc(SessionReset.lastResetInfo) + "\"}"
                    : "{\"result\":false,\"error\":\"" + JsonUtil.esc(err) + "\"}");
        } else if ("/crash".equals(p)) {
            // v17 — daftar + isi laporan crash terbaru (banner merah dashboard)
            status = 200;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(crashJson());
        } else if ("/fix/overlay".equals(p) && "POST".equalsIgnoreCase(req.method)) {
            // v17 — tombol BUKA SETTINGS: halaman izin "Display over other apps"
            String err = OverlayFixer.openSettings();
            OverlayFixer.checkAndLog();
            status = err == null ? 200 : 400;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(err == null
                    ? "{\"result\":true,\"msg\":\"halaman izin dibuka — aktifkan utk 'DB - Local', lalu kembali ke game\"}"
                    : "{\"result\":false,\"error\":\"" + JsonUtil.esc(err) + "\"}");
        } else {
            status = 404;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text("{\"error\":\"gunakan / , /stats , /health , /logs?mode=all|port&port=..&limit=N&since=SEQ , /download , /crash , POST /sdkconfig/session-reset\"}");
        }

        h.put("Access-Control-Allow-Origin", "*");
        HttpWorker.writeResponse(out, status, status == 200 || status == 400 ? "OK" : "Not Found",
                h, body, req.isHead());
        // anti-pollution (Bagian 18): request logger hanya counter, TIDAK masuk buffer
        LoggerCore.countSelf(port, req.body.length, body.length, status >= 400);
        return true;
    }

    private boolean handleDownload(HttpWorker.Request req, OutputStream out, long t0) throws IOException {
        Map<String, String> h = HttpWorker.baseHeaders("application/json");
        h.put("Access-Control-Allow-Origin", "*");
        byte[] body;
        int status;
        if (req.q("latest") != null) {
            File f = DiskWriter.newestFile();
            if (f == null) { status = 404; body = HttpWorker.text("{\"error\":\"belum ada arsip\"}"); }
            else return serveFile(f, out, t0, req);
        } else if (req.q("file") != null) {
            File f = DiskWriter.fileByName(req.q("file"));
            if (f == null) { status = 404; body = HttpWorker.text("{\"error\":\"file tidak ditemukan\"}"); }
            else return serveFile(f, out, t0, req);
        } else {
            status = 200;
            body = HttpWorker.text(DiskWriter.listJson());
        }
        HttpWorker.writeResponse(out, status, status == 200 ? "OK" : "Not Found", h, body, req.isHead());
        LoggerCore.countSelf(port, req.body.length, body.length, status >= 400);
        return true;
    }

    private boolean serveFile(File f, OutputStream out, long t0, HttpWorker.Request req) throws IOException {
        Map<String, String> h = HttpWorker.baseHeaders("application/x-ndjson");
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
        h.remove("Connection");
        h.put("Connection", "close");
        byte[] buf = new byte[(int) Math.min(f.length(), Integer.MAX_VALUE - 8)];
        FileInputStream in = new FileInputStream(f);
        try {
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            byte[] data = (off == buf.length) ? buf : java.util.Arrays.copyOf(buf, off);
            HttpWorker.writeResponse(out, 200, "OK", h, data, false);
            LoggerCore.countSelf(port, req.body.length, data.length, false);
        } finally {
            in.close();
        }
        return false; // Connection: close — tutup koneksi
    }

    private void parseLogsParams(HttpWorker.Request req, int[] lim, long[] since, int[] portFilter) {
        lim[0] = 500;
        try {
            if (req.q("limit") != null) lim[0] = Integer.parseInt(req.q("limit"));
        } catch (NumberFormatException nfe) { lim[0] = 500; }
        lim[0] = Math.max(0, Math.min(lim[0], LoggerCore.GLOBAL_CAP));
        try {
            if (req.q("since") != null) since[0] = Long.parseLong(req.q("since"));
        } catch (NumberFormatException nfe) { since[0] = 0; }
        try {
            if (req.q("port") != null) portFilter[0] = Integer.parseInt(req.q("port"));
        } catch (NumberFormatException nfe) { portFilter[0] = 0; }
    }

    private String logsJson(int limit, long since, int portFilter) {
        String mode = "all";
        ArrayList<LoggerCore.Entry> list;
        long oldest = 0;
        long dropped = LoggerCore.droppedGlobal;
        int portIdx = 5;
        if (portFilter == LoggerCore.PORT_CONFIG) portIdx = 0;
        else if (portFilter == LoggerCore.PORT_CDN) portIdx = 1;
        else if (portFilter == LoggerCore.PORT_LOGIN) portIdx = 2;
        else if (portFilter == LoggerCore.PORT_SDK) portIdx = 3;
        else if (portFilter == LoggerCore.PORT_LOGGER) portIdx = 4;
        if (portFilter != 0) {
            mode = "port";
            list = LoggerCore.latestPort(portFilter, limit, since);
            oldest = LoggerCore.oldestPortSeq(portFilter);
            dropped = LoggerCore.droppedPort[portIdx];
        } else {
            list = LoggerCore.latestGlobal(limit, since);
            oldest = LoggerCore.oldestGlobalSeq();
        }
        // gap ALL  : ada entri global sebelum oldest yang tidak terlihat client
        // gap PORT : hanya bila ring buffer port itu pernah meluap (droppedPort>0)
        boolean gap = portFilter != 0
                ? LoggerCore.droppedPort[portIdx] > 0 && oldest > 0 && oldest > since + 1
                : oldest > 0 && oldest > since + 1;
        long seqOldest = list.isEmpty() ? 0 : list.get(0).seq;
        long seqNewest = list.isEmpty() ? since : list.get(list.size() - 1).seq;
        StringBuilder b = new StringBuilder(96 + list.size() * 320);
        b.append("{\"mode\":\"").append(mode).append('"');
        if (portFilter > 0) b.append(",\"port\":").append(portFilter);
        b.append(",\"count\":").append(list.size())
         .append(",\"gap\":").append(gap)
         .append(",\"droppedGlobal\":").append(LoggerCore.droppedGlobal)
         .append(",\"droppedPort\":").append(dropped)
         .append(",\"seqOldest\":").append(seqOldest)
         .append(",\"seqNewest\":").append(seqNewest)
         .append(",\"disk\":").append(DiskWriter.statsJson())
         .append(",\"order\":\"kronologis naik (lama → baru)\",\"entries\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(',');
            b.append(list.get(i).toJson());
        }
        b.append("]}");
        return b.toString();
    }

    /**
     * v17 — JSON laporan crash: daftar file crash-*.txt di logs/ + isi file
     * terbaru (cap 30000 karakter). Dipakai banner merah CRASH dashboard.
     */
    private String crashJson() {
        StringBuilder b = new StringBuilder(512);
        b.append("{\"dir\":\"");
        File dir = new File(LoggerCore.serverRootPath, "logs");
        b.append(JsonUtil.esc(dir.getAbsolutePath())).append("\",\"files\":[");
        File newest = null;
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                String n = f.getName();
                if (f.isFile() && n.startsWith("crash-") && n.endsWith(".txt")) {
                    names.add(n);
                    if (newest == null || n.compareTo(newest.getName()) > 0) newest = f;
                }
            }
            java.util.Collections.sort(names);
        }
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) b.append(',');
            b.append('"').append(JsonUtil.esc(names.get(i))).append('"');
        }
        b.append("],\"latest\":");
        if (newest == null) {
            b.append("null");
        } else {
            String txt = readTextCap(newest, 30000);
            b.append("{\"name\":\"").append(JsonUtil.esc(newest.getName()))
             .append("\",\"bytes\":").append(newest.length())
             .append(",\"text\":\"").append(JsonUtil.esc(txt)).append("\"}");
        }
        b.append('}');
        return b.toString();
    }

    private static String readTextCap(File f, int cap) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                int n = (int) Math.min(f.length(), cap);
                byte[] buf = new byte[n];
                int off = 0;
                while (off < n) {
                    int r = in.read(buf, off, n - off);
                    if (r < 0) break;
                    off += r;
                }
                return new String(buf, 0, off, java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return "(gagal membaca: " + t + ")";
        }
    }

    /* ================= dashboard MONITOR v3 (Bagian 39) =================
     * Arahan owner 23 Nov 2026 (pembenahan logger monitoring port 4000):
     *  - "rencanakan ulang pembenahan pada loggers monitoring di port4000
     *    tampilan sungguh jelek dan tidak efisien"
     *  - "overlay izin gk perlu di tandai error karena emang game aslinya
     *    gak pernah perntahkan untuk overlay dan sdk tetap berjalan normal"
     *  - "teks harus jelas, mudah di baca tata letak terstruktur dan rapi,
     *    emoji stylish"
     * Jawaban v3 — REDESIGN total dashboard (routing + API tidak berubah):
     *  1. IZIN OVERLAY = INFO, BUKAN error:
     *     - entri SYS/OVERLAY tampil verdict "ℹ️ INFO" (bukan merah);
     *     - isProblem() eksklusi EKSPLISIT method OVERLAY — tidak pernah
     *       masuk hitungan "N MASALAH" maupun banner merah;
     *     - banner merah "IZIN Display over other apps OFF" DIHAPUS;
     *     - teks log OverlayFixer dinetralkan (game asli tidak meminta
     *       overlay; SDK tetap berjalan normal).
     *  2. Tampilan baru — teks jelas, terstruktur, emoji stylish:
     *     - header 🐉 DB MONITOR + pill 🟢 SEMUA NORMAL / 🔴 N MASALAH;
     *     - chip layanan beremoji (⚙️9527 🌐9528 🔑5050 🎮5051 🛡️9530 📋4000);
     *     - tabel 7 kolom bersih: # ⏰ TAHAP PORT STATUS ⏱️ PERISTIWA,
     *       badge tahap beremoji, verdict beremoji (✅ ❌ ⚠️ ℹ️ ⚙️);
     *     - banner sesi direset = info amber 🔄 (bukan merah);
     *     - banner CRASH tetap merah 💥 (itu memang error).
     *  3. Efisiensi dipertahankan: poll incremental ?since= (1.2 dtk),
     *     /stats tiap 3 poll, MAXC 3000 baris, mode PENTING/MASALAH/SEMUA,
     *     filter tahap/port/status/cari, Salin tabel/JSON, unduh arsip.
     */

    private String dashboardHtml() {
        StringBuilder b = new StringBuilder(32768);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>DB MONITOR</title>\n")
         .append("<style>\n")
         .append("*{box-sizing:border-box}\n")
         .append("body{font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#0b1015;color:#dde5ee;margin:0;padding:12px;font-size:14px;line-height:1.5;user-select:text;-webkit-user-select:text}\n")
         .append("#top{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin-bottom:10px}\n")
         .append("#title{color:#43d17c;font-weight:800;font-size:20px;letter-spacing:.3px}\n")
         .append("#title .ver{color:#6b7887;font-size:12px;font-weight:600;margin-left:6px}\n")
         .append("#pill{font-size:12.5px;font-weight:800;border-radius:999px;padding:5px 14px}\n")
         .append("#pill.g{background:#122b1d;color:#7ee2a8;border:1px solid #2f7a44}\n")
         .append("#pill.r{background:#2b1214;color:#ffb3a7;border:1px solid #7a2f34}\n")
         .append("#statsbar{color:#94a3b2;font-size:12.5px;flex-basis:100%}\n")
         .append(".panel{background:#141b23;border:1px solid #232e3a;border-radius:12px;padding:12px;margin-bottom:10px}\n")
         .append("#chips{display:flex;flex-wrap:wrap;gap:8px}\n")
         .append(".chip{display:inline-flex;align-items:center;gap:6px;background:#1a222c;border:1px solid #2a3542;border-radius:999px;padding:5px 12px;font-size:12.5px}\n")
         .append(".chip b{font-weight:700;color:#dde5ee}\n")
         .append(".dot{width:8px;height:8px;border-radius:50%;display:inline-block}\n")
         .append(".dot.g{background:#43d17c;box-shadow:0 0 6px rgba(67,209,124,.7)}.dot.m{background:#4a5561}\n")
         .append(".bad{color:#f87171}.ok{color:#7ee2a8}.m{color:#94a3b2}\n")
         .append("#bwrap{margin-bottom:10px}\n")
         .append(".prob{background:#2b1214;border:1px solid #7a2f34;border-radius:12px;padding:10px 14px;margin-bottom:8px;font-size:13.5px}\n")
         .append(".prob .ph{color:#ffb3a7;font-weight:800}\n")
         .append(".prob .pm{color:#e8d5d2;margin-top:3px;font-size:13px}\n")
         .append(".prob button{margin-top:8px;background:#3a1214;color:#ffb3a7;border:1px solid #7a2f34;font-weight:700}\n")
         .append(".prob button:hover{border-color:#a04a50}\n")
         .append(".prob pre{margin:8px 0 0;padding:8px 10px;background:#0b0f14;border:1px solid #232e3a;border-radius:8px;white-space:pre-wrap;word-break:break-word;max-height:220px;overflow:auto;font-size:11.5px;color:#c7d1da;font-family:Menlo,Consolas,monospace}\n")
         .append("#ctl{display:flex;flex-wrap:wrap;gap:8px;align-items:center}\n")
         .append("label{color:#9fb0c0;font-size:12.5px}\n")
         .append("select,input,button{background:#1a222c;color:#dde5ee;border:1px solid #2a3542;border-radius:8px;padding:7px 11px;font-family:inherit;font-size:13px}\n")
         .append("input{padding:6px 11px;min-width:140px}\n")
         .append("button{cursor:pointer;min-height:36px}button:hover{border-color:#4a5a6a;background:#202933}\n")
         .append(".vb{font-weight:700}\n")
         .append(".vb.on{background:#122b1d;border-color:#2f7a44;color:#7ee2a8}\n")
         .append("#stagechips{display:flex;flex-wrap:wrap;gap:6px}\n")
         .append(".sc{font-size:12px;font-weight:700;border-radius:999px;padding:4px 12px;min-height:30px}\n")
         .append(".sc.on{background:#223041;border-color:#4a6a8a;color:#bcd6ee}\n")
         .append("#adv summary{color:#9fb0c0;font-size:12.5px;cursor:pointer}\n")
         .append("#adv .in{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-top:8px}\n")
         .append("#summary{font-size:13px;color:#cfe3cf;flex:1;min-width:220px}\n")
         .append("#summary b{color:#7ee2a8}\n")
         .append("#flow{max-height:calc(100vh - 330px);overflow:auto;border:1px solid #232e3a;border-radius:12px;background:#10161d}\n")
         .append("#flow::-webkit-scrollbar{width:10px;height:10px}\n")
         .append("#flow::-webkit-scrollbar-thumb{background:#2a3542;border-radius:5px}\n")
         .append("#tbl{width:100%;border-collapse:collapse}\n")
         .append("#tbl thead th{position:sticky;top:0;background:#1a222c;color:#7ee2a8;font-size:11px;letter-spacing:.6px;text-align:left;padding:8px 10px;border-bottom:1px solid #2a3542;z-index:2;white-space:nowrap}\n")
         .append("#tbl tbody tr{border-bottom:1px solid #1a222b}\n")
         .append("#tbl tbody tr:nth-child(even){background:#121820}\n")
         .append("#tbl tbody tr:hover{background:#1a222c;cursor:pointer}\n")
         .append("#tbl tbody tr.bad{background:#1d1012}\n")
         .append("#tbl td{padding:7px 10px;vertical-align:top;font-size:13px}\n")
         .append(".c-seq{color:#5d6b79;font-size:11.5px;white-space:nowrap}\n")
         .append(".c-time{font-family:Menlo,Consolas,monospace;color:#9fb0c0;font-size:12px;white-space:nowrap}\n")
         .append(".c-port{color:#7f8c9a;font-size:12px;white-space:nowrap}\n")
         .append(".c-st{font-weight:800;font-size:12px;white-space:nowrap}\n")
         .append(".v-g{color:#43d17c}.v-r{color:#f87171}.v-w{color:#f5b942}.v-i{color:#a8b3bf}.v-m{color:#8a97a5}\n")
         .append(".c-dur{color:#7f8c9a;font-size:11.5px;text-align:right;white-space:nowrap}\n")
         .append(".c-desc{font-size:13px;min-width:180px;line-height:1.5}\n")
         .append(".c-desc .fail{color:#f87171;font-weight:700}\n")
         .append(".c-desc .m{color:#94a3b2;font-size:12px}\n")
         .append(".stage{display:inline-block;font-size:10.5px;font-weight:800;letter-spacing:.4px;border-radius:6px;padding:2px 8px;white-space:nowrap}\n")
         .append(".st-KONFIG{background:#2e2b1a;color:#d9c377}.st-CDN{background:#1a2e26;color:#37c9a6}\n")
         .append(".st-SDK{background:#261e33;color:#b899e8}.st-LOGIN{background:#33261a;color:#f0a35e}\n")
         .append(".st-GAME{background:#162e2b;color:#52d0b0}.st-SISTEM{background:#232b33;color:#9fb0c0}\n")
         .append(".st-LOGGER{background:#282d33;color:#8a97a5}\n")
         .append("#foot{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-top:10px}\n")
         .append("#notices{font-size:12.5px;margin:4px 0}\n")
         .append(".n-warn{color:#f5b942}.n-err{color:#f87171}\n")
         .append("#toast{position:fixed;bottom:16px;left:50%;transform:translateX(-50%);background:#122b1d;color:#7ee2a8;border:1px solid #2f7a44;padding:8px 18px;border-radius:10px;display:none;z-index:50;font-size:13px;font-weight:600}\n")
         .append("#toast.err{background:#2b1214;color:#ffb3a7;border-color:#7a2f34}\n")
         .append("#modal{position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(4,8,12,.78);display:none;z-index:40}\n")
         .append("#modalbox{background:#10161d;border:1px solid #2a3542;border-radius:14px;max-width:780px;margin:36px auto;padding:12px;max-height:85vh}\n")
         .append("#modalhead{display:flex;justify-content:space-between;gap:8px;color:#7ee2a8;margin-bottom:8px;font-size:13px;align-items:center}\n")
         .append("#modalpre{max-height:64vh;overflow:auto;white-space:pre-wrap;word-break:break-word;background:#0b0f14;padding:10px;font-size:12.5px;color:#c7d1da;font-family:Menlo,Consolas,monospace;border-radius:8px}\n")
         .append("@media(max-width:640px){#tbl .c-seq,#tbl .c-dur{display:none}#flow{max-height:calc(100vh - 310px)}}\n")
         .append("@media(max-width:480px){#tbl .c-port{display:none}#tbl td,#tbl thead th{padding:5px 7px}body{padding:8px}}\n")
         .append("</style></head><body>\n")
         .append("<div id=top><span id=title>🐉 DB MONITOR<span class=ver>v6</span></span>")
         .append("<span id=pill class=g>🟢 cek …</span>")
         .append("<span id=statsbar>memuat …</span></div>\n")

         .append("<div id=bwrap><div id=crashbn></div><div id=probn></div></div>\n")
         .append("<div class=panel><div id=chips></div></div>\n")

         .append("<div class=panel id=ctl>\n")
         .append("<button id=vimp class=\"vb on\" title='Tampilkan penting saja'>⭐</button>")
         .append("<button id=vmal class=vb title='Tampilkan masalah saja'>❌</button>")
         .append("<button id=vall class=vb title='Tampilkan semua log'>📜</button>")
         .append("<span id=stagechips></span>")
         .append("<input id=q placeholder='🔍' title='Cari di log'>")
         .append("<button id=pause title='Jeda / lanjut pembaruan otomatis'>⏸️</button> <button id=reset title='Bersihkan tampilan log'>🧹</button> ")
         .append("<button id=guidebtn title='Panduan cepat'>📖</button>\n")
         .append("<details id=adv><summary title='Filter lanjutan'>⚙️</summary><div class=in>\n")
         .append("<label>Status <select id=st><option value=''>semua</option><option value=2>sukses (2xx)</option><option value=4>gagal (4xx)</option><option value=5>rusak (5xx)</option><option value=e>bermasalah</option></select></label>\n")
         .append("<label>Jumlah <select id=limit><option>100</option><option selected>500</option><option>2000</option><option value=0>semua buffer</option></select></label>\n")
         .append("<label>Port <select id=port><option value=0>semua</option><option value=9527>9527 konfig</option><option value=9528>9528 CDN</option>")
         .append("<option value=5050>5050 login</option><option value=5051>5051 game</option><option value=9530>9530 SDK</option><option value=4000>4000 logger</option></select></label>\n")
         .append("</div></details>\n")
         .append("<div class=panel id=guide style=display:none;margin-top:8px>\n")
         .append("<b style=color:#7ee2a8>📖 PANDUAN CEPAT (server lokal)</b>\n")
         .append("<ul style=margin:6px 0 2px 18px;padding:0;font-size:13px;color:#c7d1da>\n")
         .append("<li>Alur normal: konfigurasi 9527 → resource 9528 → SDK 9530 → layar login → TAMU/DAFTAR/LOGIN → verifikasi 5050 → game 5051.</li>\n")
         .append("<li>💥 CRASH setelah loading? Cek banner merah CRASH di atas (trace lengkap + tombol 📋). Bila sesi direset otomatis: tercatat di tabel (🔄) → buka game → LOGIN MANUAL.</li>\n")
         .append("<li>ℹ️ Izin overlay OFF = <b>NORMAL</b> — game asli tidak memerlukan overlay dan SDK tetap berjalan normal (tidak ditandai error).</li>\n")
         .append("<li>Di layar login SDK: <b>TAMU</b> = langsung main; <b>DAFTAR</b> = akun baru; <b>LOGIN</b> = akun terdaftar.</li>\n")
         .append("</ul>\n</div>\n")
         .append("</div>\n")

         .append("<div id=notices></div>\n")
         .append("<div id=flow><table id=tbl><thead><tr><th>#</th><th>⏰ WAKTU</th><th>TAHAP</th><th>PORT</th><th>STATUS</th><th>⏱️</th><th>PERISTIWA</th></tr></thead><tbody id=tb></tbody></table></div>\n")
         .append("<div id=foot><span id=summary>…</span>")
         .append("<button id=cpt title='Salin tabel (teks rapi)'>📋</button> <button id=cpjs title='Salin JSON log'>📄</button> ")
         .append("<button id=dl title='Unduh arsip NDJSON'>💾</button></div>\n")

         .append("<div id=toast></div>\n")
         .append("<div id=modal><div id=modalbox><div id=modalhead><span id=modaltitle></span><span><button id=modalcp title='Salin isi detail'>📋</button> <button id=modalx title='Tutup detail'>✖️</button></span></div><pre id=modalpre></pre></div></div>\n")

         .append("<script>\n")
         .append(Js())
         .append("</script>\n")
         .append("</body></html>");
        return b.toString();
    }

    private static String Js() {
        StringBuilder s = new StringBuilder(24576);
        s.append("var S={since:0,view:'penting',stage:'',st:'',q:'',limit:500,auto:true,port:0};\n")
         .append("var ENT={},MAXC=3000,pollN=0,LASTSUM='',CRASHLATEST='',MODTXT='',lastDrop=-1;\n")
         .append("function $(id){return document.getElementById(id)}\n")
         .append("function esc(x){return (x==null?'':''+x).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         .append("function hdrLines(h){return h?(''+h).split('; ').join('\\n'):''}\n")
         .append("function pretty(s){var t=(''+s).trim();var c=t.charAt(0);var e=t.charAt(t.length-1);\n")
         .append(" if((c=='{'&&e=='}')||(c=='['&&e==']')){try{return JSON.stringify(JSON.parse(t),null,2)}catch(x){}}\n")
         .append(" return ''+s}\n")
         .append("function prettyExtra(x){var i=(''+x).indexOf('DECODED-XOR: ');\n")
         .append(" if(i>=0){try{return x.substring(0,i+13)+'\\n'+JSON.stringify(JSON.parse(x.substring(i+13).trim()),null,2)}catch(y){}}\n")
         .append(" return ''+x}\n")
         .append("/* ---- tahap + deskripsi manusia (emoji stylish — Bagian 39) ---- */\n")
         .append("function stageOf(e){\n")
         .append(" if(e.kind=='BOOT'||e.kind=='SYS'||e.kind=='EXTRACT')return 'SISTEM';\n")
         .append(" if(e.kind=='CRASH')return 'SISTEM';\n")
         .append(" if(e.port==9527)return 'KONFIG'; if(e.port==9528)return 'CDN';\n")
         .append(" if(e.port==9530)return 'SDK'; if(e.port==5050)return 'LOGIN';\n")
         .append(" if(e.port==5051)return 'GAME'; return 'LOGGER'}\n")
         .append("function stageIcon(sg){return sg=='KONFIG'?'⚙️':sg=='CDN'?'🌐':sg=='SDK'?'🛡️':sg=='LOGIN'?'🔑':sg=='GAME'?'🎮':sg=='SISTEM'?'🧭':'📋'}\n")
         .append("function pathOf(e){var p=(''+(e.path||''));var i=p.indexOf('?');return i>=0?p.substring(0,i):p}\n")
         .append("function ms2(n){var mb=n/1048576;return mb>=1?mb.toFixed(1)+'MB':Math.round(n/1024)+'KB'}\n")
         .append("function sdkErr(e){var m=/\\\"error\\\":\\{\\\"id\\\":(\\d+),\\\"message\\\":\\\"(.*?)\\\"/.exec(''+(e.respPreview||''));\n")
         .append(" return m?{id:+m[1],msg:m[2]}:{id:0,msg:''}}\n")
         .append("function errWhy(id,msg){\n")
         .append(" var m=''+(msg||'');\n")
         .append(" if(id==20000)return 'server tidak bisa membaca data (Params error)';\n")
         .append(" if(id==20002)return 'tanda tangan (sign) salah — signSecret tidak cocok';\n")
         .append(" if(id==20003){var mm=/LostSomeMustParams:(\\w+)/.exec(m);return 'param wajib kosong: <b>'+(mm?mm[1]:'?')+'</b>'}\n")
         .append(" if(id==30001)return 'PRODUCT TIDAK DITERIMA — productCode game belum ada di daftar products profile.json — force-stop & buka game ulang';\n")
         .append(" if(id)return m||('error '+id);\n")
         .append(" return 'request gagal'}\n")
         .append("function epName(p){\n")
         .append(" if(p.indexOf('/v1/system/init')>=0)return 'Inisialisasi SDK';\n")
         .append(" if(p.indexOf('registerVisitor')>=0)return 'Buat akun TAMU';\n")
         .append(" if(p.indexOf('registerUser')>=0)return 'Pendaftaran (DAFTAR)';\n")
         .append(" if(p.indexOf('loginByName')>=0)return 'Login (LOGIN)';\n")
         .append(" if(p.indexOf('autoLogin')>=0)return 'Login otomatis';\n")
         .append(" if(p.indexOf('getUserInfo')>=0)return 'Info akun';\n")
         .append(" if(p.indexOf('changePassword')>=0)return 'Ganti sandi';\n")
         .append(" if(p.indexOf('bindMail')>=0)return 'Bind email';\n")
         .append(" if(p.indexOf('ckRegVistor')>=0)return 'Cek Kode Migrasi';\n")
         .append(" if(p.indexOf('sendCodeByEmail')>=0)return 'Kirim kode email';\n")
         .append(" if(p.indexOf('findPassByEmail')>=0)return 'Lupa sandi';\n")
         .append(" return 'SDK '+p.substring(0,40)}\n")
         .append("function describe(e){\n")
         .append(" var p=pathOf(e),k=e.kind||'HTTP',m=e.method||'',st=e.status||0,fail=st>=400,extra=e.extra||'';\n")
         .append(" if(k=='CRASH')return '💥 <b>CRASH proses game</b> — '+esc(p)+(e.error?'<br><span class=fail>'+esc(e.error)+'</span>':'');\n")
         .append(" if(k=='BOOT')return '🚀 <b>Server mulai</b> — '+esc(p);\n")
         .append(" if(k=='SYS'&&m=='BIND')return (st==0?'🔌 <b>Port aktif:</b> ':'❌ <b>Port GAGAL:</b> ')+esc(p);\n")
         .append(" if(k=='SYS'&&m=='OVERLAY')return 'ℹ️ <b>Izin overlay</b> — '+esc(p);\n")
         .append(" if(k=='SYS'){var t=esc(p);if(/gagal|ditolak|rusak/i.test(p))t='❌ <span class=fail>'+t+'</span>';\n")
         .append("  else if(m=='SESI-RESET')t='🔄 '+t;else if(m=='SESI-GUARD')t='🧭 '+t;\n")
         .append("  else if(m=='EXITINFO')t='🌙 '+t;else t='🧭 '+t;return t}\n")
         .append(" if(k=='EXTRACT')return '📦 <b>Ekstraksi aset game</b> — '+esc(p);\n")
         .append(" if(k=='LOGCAT')return '📟 <b>Rekaman logcat</b> — diagnostik otomatis (aman diabaikan)';\n")
         .append(" if(k=='WS'){\n")
         .append("  var evw=evName(e.extra);\n")
         .append("  if(evw&&evw.a=='enterGame')return (e.respPreview&&e.respPreview.indexOf('ret\\\":0')>=0||e.respPreview&&e.respPreview.indexOf('\\\"ret\\\":0')>=0)?'🎮 <b>Masuk server 1</b> — data pemain diterima ('+ms2(e.respLen||0)+')':'🎮 Masuk server — '+ms2(e.respLen||0);\n")
         .append("  if(evw)return '📨 Event game/login <b>'+esc(evw.a)+'</b>'+(evw.ack?' — dijawab':' — dikirim');\n")
         .append("  if(e.respPreview=='pong'||(e.extra||'').indexOf('ping→pong')>=0)return '💓 Keep-alive (ping-pong) — sehat';\n")
         .append("  return '🔗 Koneksi WebSocket (login/game) — '+esc((e.extra||'').substring(0,120))}\n")
         .append(" if(e.port==9527){\n")
         .append("  if(p.indexOf('setting_BS')>=0)return '🧾 Baca <b>konfigurasi game</b> (setting) — alamat server diarahkan ke 127.0.0.1';\n")
         .append("  if(p.indexOf('com_db_local')>=0)return '🧾 Baca konfigurasi aplikasi (com_db_local) — kosong (normal)';\n")
         .append("  return '🧾 Konfigurasi: '+esc(p)}\n")
         .append(" if(e.port==9528){\n")
         .append("  if(p.indexOf('base.version')>=0)return '🔢 Cek <b>versi dasar</b> — '+esc((e.respPreview||'').trim()||'?');\n")
         .append("  if(p.indexOf('resource.version')>=0)return '🔢 Cek <b>versi resource</b> — '+esc((e.respPreview||'').trim()||'?');\n")
         .append("  if(p.indexOf('clientversion')>=0)return '🔖 Cek <b>versi client</b> — popup pengingat tidak muncul lagi';\n")
         .append("  if(p.indexOf('upgrade.json')>=0)return '📥 Baca <b>daftar paket update</b>';\n")
         .append("  if(p.indexOf('size.json')>=0)return '📏 Baca <b>ukuran paket update</b>';\n")
         .append("  if(p.indexOf('all.zip')>=0)return '📥 Unduh <b>paket update</b> — '+ms2(e.respLen||0)+' dalam '+e.durMs+'ms';\n")
         .append("  if(p.indexOf('serversetting')>=0)return '🧭 Baca <b>alamat server login</b> (serversetting)';\n")
         .append("  if(p.indexOf('index-native')>=0)return '🖼️ Muat <b>UI game</b> (index-native.html)';\n")
         .append("  if(p.indexOf('manifest.json')>=0)return '📄 Muat manifest game';\n")
         .append("  if(/\\.js($|\\?)/.test(p))return '📄 Muat skrip game — '+esc(p.split('/').pop());\n")
         .append("  if(p.indexOf('/resource/')>=0)return '🎨 Muat aset game — '+esc(p.split('/').pop().substring(0,40));\n")
         .append("  return '🌐 CDN: '+esc(p)}\n")
         .append(" if(e.port==9530){\n")
         .append("  var er=sdkErr(e),bad=(e.respPreview||'').indexOf('\\\"result\\\":false')>=0;\n")
         .append("  if(fail||bad){var nm=epName(p);return '❌ <span class=fail>'+nm+' GAGAL</span> — '+errWhy(er.id,er.msg)+(er.id?' <span class=m>(id '+er.id+')</span>':'')}\n")
         .append("  if(p.indexOf('/v1/system/init')>=0)return '🛡️ <b>Inisialisasi SDK</b> — sukses, layar login mengikuti loginTypes';\n")
         .append("  if(p.indexOf('registerVisitor')>=0)return '👤 <b>Buat akun tamu</b> — uid '+esc(uin(extra))+' tersimpan permanen';\n")
         .append("  if(p.indexOf('autoLogin')>=0)return '🔐 <b>Login otomatis</b> — akun '+esc(una(extra))+' (tersimpan)';\n")
         .append("  if(p.indexOf('getNotice')>=0)return '📣 Cek pengumuman SDK — tidak ada';\n")
         .append("  if(p.indexOf('setGameRoleInfo')>=0)return '🎭 Lapor data karakter ke SDK';\n")
         .append("  if(p.indexOf('ckRegVistor')>=0)return '🔄 Cek <b>pendaftaran</b> (Kode Migrasi) — isReged:0 = persis server asli';\n")
         .append("  return epName(p)+' — sukses'}\n")
         .append(" if(e.port==5050){\n")
         .append("  if(p.indexOf('loginchecknative')>=0)return '✅ <b>Verifikasi akun</b> (loginchecknative) — game dilanjutkan';\n")
         .append("  if((e.path||'').indexOf('transport=polling')>=0&&e.respPreview&&e.respPreview.indexOf(':0{')>=0)return '🤝 Handshake <b>server login</b> — sesi dibuat';\n")
         .append("  var ev=evName(e.extra);if(ev)return '📨 Event login <b>'+esc(ev.a)+'</b>'+(ev.ack?' — dijawab':' — dikirim');\n")
         .append("  if((e.extra||'').indexOf('ping→pong')>=0)return '💓 Keep-alive login (ping-pong)';\n")
         .append("  return '🔑 Server login: '+esc(p.substring(0,60))}\n")
         .append(" if(e.port==5051){\n")
         .append("  var ev=evName(e.extra);\n")
         .append("  if(ev&&ev.a=='enterGame')return (e.respPreview&&e.respPreview.indexOf('ret\\\":0')>=0||e.respPreview&&e.respPreview.indexOf('\\\"ret\\\":0')>=0)?'🎮 <b>Masuk server 1</b> — data pemain diterima ('+ms2(e.respLen||0)+')':'🎮 Masuk server — '+ms2(e.respLen||0);\n")
         .append("  if(ev)return '📨 Event game <b>'+esc(ev.a)+'</b>'+(ev.ack?' — dijawab':' — dikirim');\n")
         .append("  return '🎮 Server game: '+esc(p.substring(0,60))}\n")
         .append(" return esc(p)}\n")
         .append("function uin(x){var m=/uid[=\\\":]+([0-9]+)/.exec(''+x);return m?m[1]:'?'}\n")
         .append("function una(x){var m=/username[=\\\":\\s]+([A-Za-z0-9_]+)/.exec(''+x);return m?m[1]:'tamu'}\n")
         .append("function evName(extra){var s=''+(extra||'');var i=s.indexOf('EVENT: 42');if(i<0)return null;\n")
         .append(" var m=/action\":\"([A-Za-z]+)/.exec(s.substring(i));var ack=s.indexOf('ACK: 43',i)>=0;\n")
         .append(" return m?{a:m[1],ack:ack}:{a:'?',ack:ack}}\n")
         .append("/* ---- masalah / penting / verdict ---- */\n")
         .append("function isProblem(e){\n")
         .append(" if((e.kind||'')=='SYS'&&(e.method||'')=='OVERLAY')return false;\n")
         .append(" if((e.kind||'')=='CRASH')return true;\n")
         .append(" if((e.error||'').length)return true;\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='SYS'||k=='BOOT'||k=='EXTRACT'){var t=(''+(e.path||'')).toLowerCase();return t.indexOf('gagal')>=0||t.indexOf('ditolak')>=0||t.indexOf('rusak')>=0}\n")
         .append(" if(k=='HTTP'||k=='WS'){\n")
         .append("  if((e.status||0)>=400)return true;\n")
         .append("  if((e.respPreview||'').indexOf('\\\"result\\\":false')>=0)return true}\n")
         .append(" return false}\n")
         .append("function isImportant(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='CRASH')return true;\n")
         .append(" if(k=='BOOT'||k=='SYS'||k=='EXTRACT'||k=='LOGCAT')return true;\n")
         .append(" if(e.port==9527||e.port==9530)return true;\n")
         .append(" if(e.port==9528){var p=pathOf(e);\n")
         .append("  return p.indexOf('base.version')>=0||p.indexOf('resource.version')>=0||p.indexOf('clientversion')>=0||p.indexOf('upgrade.json')>=0||p.indexOf('size.json')>=0||p.indexOf('all.zip')>=0||p.indexOf('index-native')>=0||p.indexOf('serversetting')>=0}\n")
         .append(" if(e.port==5050){if((e.extra||'').indexOf('ping→pong')>=0)return false;return true}\n")
         .append(" if(e.port==5051)return true;\n")
         .append(" return false}\n")
         .append("function verdictOf(e){\n")
         .append(" var k=e.kind||'',m=e.method||'';\n")
         .append(" if(k=='CRASH')return ['GAGAL','r'];\n")
         .append(" if(k=='SYS'&&m=='OVERLAY')return ['INFO','i'];\n")
         .append(" if(k!='HTTP'&&k!='WS')return isProblem(e)?['CEK','w']:['SISTEM','m'];\n")
         .append(" if(isProblem(e))return ['GAGAL','r'];\n")
         .append(" if((e.status||0)>=300)return ['CEK','w'];\n")
         .append(" if(e.status==0)return ['INFO','m'];\n")
         .append(" return ['OK','g']}\n")
         .append("function vEmoji(t){return t=='OK'?'✅':t=='GAGAL'?'❌':t=='CEK'?'⚠️':t=='INFO'?'ℹ️':'⚙️'}\n")
         .append("function sv(e){var v=verdictOf(e);return v[0]=='SISTEM'?'SYS':v[0]}\n")
         .append("function plain(h){return (''+h).replace(/<[^>]*>/g,'')}\n")
         .append("function allText(e){var L=[];\n")
         .append(" L.push('#'+e.seq+'  '+e.time+'  port '+e.port+'  '+e.kind);\n")
         .append(" if(e.kind=='HTTP')L.push((e.method||'')+' http://127.0.0.1:'+e.port+(e.path||''));else L.push(e.method||e.kind);\n")
         .append(" L.push('status '+(e.status||0)+'  '+e.durMs+'ms  req '+(e.reqLen||0)+'B  resp '+(e.respLen||0)+'B');\n")
         .append(" if(e.headers)L.push('headers:\\n'+hdrLines(e.headers));\n")
         .append(" if(e.reqPreview)L.push('req:\\n'+e.reqPreview);\n")
         .append(" if(e.respPreview)L.push('resp:\\n'+e.respPreview);\n")
         .append(" if(e.extra)L.push('extra:\\n'+e.extra);\n")
         .append(" if(e.error)L.push('error: '+e.error);\n")
         .append(" return L.join('\\n')}\n")
         .append("function detOf(e){var d=[];\n")
         .append(" if(e.kind!='HTTP'){d.push({l:'ℹ️ INFO',t:(e.path||'')+(e.respPreview?'\\n'+e.respPreview:'')});}\n")
         .append(" else{var t=(e.method||'')+' http://127.0.0.1:'+e.port+(e.path||'')+'\\n'+hdrLines(e.headers);\n")
         .append("  if(e.reqPreview)t+='\\n[body '+(e.reqLen||0)+'B]\\n'+pretty(e.reqPreview);\n")
         .append("  d.push({l:'📨 REQUEST',t:t});\n")
         .append("  if(e.respPreview!=null)d.push({l:'📬 RESPON',t:'status '+(e.status||0)+'\\n'+pretty(e.respPreview)});}\n")
         .append(" if(e.extra)d.push({l:'🗂️ EXTRA',t:prettyExtra(e.extra)});\n")
         .append(" if(e.error)d.push({l:'❌ ERROR',t:e.error});\n")
         .append(" d.push({l:'📃 SEMUA (teks polos)',t:allText(e)});\n")
         .append(" return d}\n")
         .append("function durStr(e){if(!e.durMs)return '';return e.durMs>=1000?(e.durMs/1000).toFixed(1)+'s':e.durMs+'ms'}\n")
         .append("function rowHtml(e){\n")
         .append(" var sg=stageOf(e),v=verdictOf(e),d=describe(e),er=e.error||'';\n")
         .append(" return '<tr class=\"row'+(v[1]=='r'?' bad':'')+'\" data-seq=\"'+e.seq+'\">'\n")
         .append("  +'<td class=c-seq>'+e.seq+'</td>'\n")
         .append("  +'<td class=c-time>'+esc((e.time||'').substring(11))+'</td>'\n")
         .append("  +'<td class=c-stage><span class=\"stage st-'+sg+'\">'+stageIcon(sg)+' '+sg+'</span></td>'\n")
         .append("  +'<td class=c-port>'+e.port+'</td>'\n")
         .append("  +'<td class=\"c-st v-'+v[1]+'\">'+vEmoji(v[0])+' '+v[0]+'</td>'\n")
         .append("  +'<td class=c-dur>'+durStr(e)+'</td>'\n")
         .append("  +'<td class=c-desc>'+d+(er?'<br><span class=fail>❌ '+esc(er.length>110?er.substring(0,110)+'…':er)+'</span>':'')+'</td></tr>'}\n")
         .append("function important(e){var p=pathOf(e);\n")
         .append(" if((e.kind||'')=='CRASH')return '💥 CRASH: '+plain(describe(e));\n")
         .append(" if(e.status>=400)return '❌ '+plain(describe(e));\n")
         .append(" if(e.port==9530&&p.indexOf('registerVisitor')>=0)return '👤 '+plain(describe(e));\n")
         .append(" if(e.port==5050&&p.indexOf('loginchecknative')>=0)return '✅ '+plain(describe(e));\n")
         .append(" if(e.port==5051&&(e.extra||'').indexOf('enterGame')>=0)return '🎮 '+plain(describe(e));\n")
         .append(" if(e.port==9528&&p.indexOf('index-native')>=0)return '🖼️ '+plain(describe(e));\n")
         .append(" return null}\n")
         .append("function pass(e){\n")
         .append(" if(S.view=='masalah'&&!isProblem(e))return false;\n")
         .append(" if(S.view=='penting'&&!isImportant(e))return false;\n")
         .append(" if(S.stage&&stageOf(e)!=S.stage)return false;\n")
         .append(" if(S.st=='2'&&!(e.status>=200&&e.status<300))return false;\n")
         .append(" if(S.st=='4'&&!(e.status>=400&&e.status<500))return false;\n")
         .append(" if(S.st=='5'&&e.status<500)return false;\n")
         .append(" if(S.st=='e'&&!(e.status>=400||e.error))return false;\n")
         .append(" if(S.q){var t=((e.method||'')+' '+(e.path||'')+' '+(e.reqPreview||'')+' '+(e.respPreview||'')+' '+(e.extra||'')+' '+(e.error||'')).toLowerCase();if(t.indexOf(S.q)<0)return false}\n")
         .append(" return true}\n")
         .append("function applyFilter(){var rs=$('tb').querySelectorAll('tr');\n")
         .append(" for(var i=0;i<rs.length;i++){var e=ENT[rs[i].getAttribute('data-seq')];\n")
         .append("  rs[i].style.display=(e&&!pass(e))?'none':''}}\n")
         .append("function refreshPill(){var n=0;for(var k in ENT)if(isProblem(ENT[k]))n++;\n")
         .append(" var p=$('pill');if(n==0){p.textContent='🟢 SEMUA NORMAL';p.className='g'}\n")
         .append(" else{p.textContent='🔴 '+n+' MASALAH';p.className='r'}}\n")
         .append("function renderBanner(){var w=$('probn');var list=[];\n")
         .append(" for(var k in ENT){if(isProblem(ENT[k]))list.push(ENT[k])}\n")
         .append(" list.sort(function(a,b){return b.seq-a.seq});list=list.slice(0,3);\n")
         .append(" if(!list.length){w.innerHTML='';return}\n")
         .append(" var h='';for(var i=0;i<list.length;i++){var e=list[i];\n")
         .append("  h+='<div class=prob><div class=ph>❌ MASALAH #'+e.seq+' · '+esc(e.time)+'</div><div class=pm>'+plain(describe(e))+'</div>';\n")
         .append("  h+='</div>'}\n")
         .append(" w.innerHTML=h}\n")         .append("function renderCrash(){fetch('/crash').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var w=$('crashbn');if(!j||!j.latest){w.innerHTML='';return}\n")
         .append(" if(j.latest.name==CRASHLATEST&&w.innerHTML)return;\n")
         .append(" CRASHLATEST=j.latest.name;var t=j.latest.text||'';\n")
         .append(" w.innerHTML='<div class=prob><div class=ph>💥 CRASH TERTANGKAP — '+esc(j.latest.name)+' ('+Math.round(j.latest.bytes/1024)+' KB)</div><div class=pm>Trace crash Java DI DALAM proses game (termasuk kode SDK/game) — terekam CrashHook. Salin dan kirim agar akar crash ketemu persis.</div><pre>'+esc(t)+'</pre><button id=cpcrash>📋</button></div>';\n")
         .append(" $('cpcrash').onclick=function(){copyText(t)}}).catch(function(){})}\n")
         .append("function append(list){var tb=$('tb'),sc=$('flow');\n")
         .append(" var pin=sc.scrollTop+sc.clientHeight>=sc.scrollHeight-80;\n")
         .append(" var html='',im=null;\n")
         .append(" for(var i=0;i<list.length;i++){var e=list[i];ENT[e.seq]=e;\n")
         .append("  html+=rowHtml(e);var x=important(e);if(x)im=x}\n")
         .append(" if(html)tb.insertAdjacentHTML('beforeend',html);\n")
         .append(" if(im)LASTSUM=im;\n")
         .append(" var rs=tb.querySelectorAll('tr');var over=rs.length-MAXC;\n")
         .append(" for(var j2=0;j2<over;j2++)tb.removeChild(rs[j2]);\n")
         .append(" applyFilter();refreshPill();renderBanner();\n")
         .append(" if(LASTSUM)$('summary').innerHTML='<b>Terakhir penting:</b> '+LASTSUM;\n")
         .append(" if(pin)sc.scrollTop=sc.scrollHeight}\n")
         .append("function visible(){var out=[];var rs=$('tb').querySelectorAll('tr');\n")
         .append(" for(var i=0;i<rs.length;i++){if(rs[i].style.display=='none')continue;\n")
         .append("  var e=ENT[rs[i].getAttribute('data-seq')];if(e)out.push(e)}\n")
         .append(" return out}\n")
         .append("function sp(n){var s='';while(n-->0)s+=' ';return s}\n")
         .append("function copyTable(){var v=visible();var W=[6,13,7,5,6,7];\n")
         .append(" var mk=function(row){var s='';for(var c=0;c<6;c++){var cell=''+row[c];if(cell.length>W[c])cell=cell.substring(0,W[c]);s+=cell+sp(W[c]-cell.length)}var ev=''+row[6];if(ev.length>95)ev=ev.substring(0,95);s+=ev;if(row[7])s+='  || '+((''+row[7]).length>90?(''+row[7]).substring(0,90):''+row[7]);return s};\n")
         .append(" var out=[mk(['#','WAKTU','TAHAP','PORT','ST','DUR','PERISTIWA'])];\n")
         .append(" for(var i=0;i<v.length;i++){var e=v[i];\n")
         .append("  out.push(mk([e.seq,(e.time||'').substring(11),stageOf(e),e.port,sv(e),durStr(e)||'-',plain(describe(e)),e.error||'']))}\n")
         .append(" copyText(out.join('\\n'))}\n")
         .append("function copyJSON(){copyText(JSON.stringify(visible(),null,1))}\n")
         .append("function copyText(t){\n")
         .append(" if(navigator.clipboard&&navigator.clipboard.writeText){\n")
         .append("  navigator.clipboard.writeText(t).then(function(){toast('✅ tersalin '+t.length+' karakter')},function(){legacyCopy(t)});\n")
         .append(" }else legacyCopy(t)}\n")
         .append("function legacyCopy(t){\n")
         .append(" var ta=document.createElement('textarea');ta.value=t;\n")
         .append(" ta.style.position='fixed';ta.style.left='-9999px';ta.style.top='0';\n")
         .append(" document.body.appendChild(ta);ta.focus();ta.select();\n")
         .append(" var ok=false;try{ok=document.execCommand('copy')}catch(x){}\n")
         .append(" document.body.removeChild(ta);\n")
         .append(" if(ok)toast('✅ tersalin '+t.length+' karakter');\n")
         .append(" else openModal('📋 Salin manual — Ctrl+C',t)}\n")
         .append("function openModal(t2,t){MODTXT=t;$('modaltitle').textContent=t2;$('modalpre').textContent=t;$('modal').style.display='block';\n")
         .append(" var r=document.createRange();r.selectNodeContents($('modalpre'));\n")
         .append(" var sc=window.getSelection();sc.removeAllRanges();try{sc.addRange(r)}catch(x){}}\n")
         .append("var tt=null;\n")
         .append("function toast(m,err){var el=$('toast');el.textContent=m;el.className=err?'err':'';el.style.display='block';clearTimeout(tt);tt=setTimeout(function(){el.style.display='none'},2600)}\n")
         .append("$('flow').addEventListener('click',function(ev){\n")
         .append(" var tr=ev.target.closest?ev.target.closest('tr.row'):null;if(!tr)return;\n")
         .append(" var e=ENT[tr.getAttribute('data-seq')];if(!e)return;\n")
         .append(" var d=detOf(e);var s2='';\n")
         .append(" for(var i=0;i<d.length;i++)s2+='===== '+d[i].l+' =====\\n'+d[i].t+'\\n\\n';\n")
         .append(" openModal('#'+e.seq+' · '+e.time+' · port '+e.port,s2)});\n")
         .append("$('modalx').onclick=function(){$('modal').style.display='none'};\n")
         .append("$('modalcp').onclick=function(){copyText(MODTXT)};\n")
         .append("$('modal').onclick=function(ev){if(ev.target.id=='modal')$('modal').style.display='none'};\n")
         .append("var STAGES=['','KONFIG','CDN','SDK','LOGIN','GAME','SISTEM'];\n")
         .append("function renderStages(){var h='';for(var i=0;i<STAGES.length;i++){var s3=STAGES[i];h+='<button class=\"sc'+(S.stage==s3?' on':'')+'\" data-st=\"'+s3+'\" title=\"'+(s3==''?'Semua tahap':s3)+'\">'+(s3==''?'🌐':stageIcon(s3))+'</button>'}$('stagechips').innerHTML=h}\n")
         .append("$('stagechips').addEventListener('click',function(ev){\n")
         .append(" var b3=ev.target.closest?ev.target.closest('.sc'):null;if(!b3)return;\n")
         .append(" S.stage=b3.getAttribute('data-st');renderStages();applyFilter()});\n")
         .append("function sync(){S.st=$('st').value;S.q=$('q').value.toLowerCase();S.limit=parseInt($('limit').value);S.port=parseInt($('port').value)}\n")
         .append("function reset(){$('tb').innerHTML='';ENT={};S.since=0;LASTSUM='';$('notices').innerHTML='';refreshPill();renderBanner()}\n")
         .append("function setView(v){S.view=v;\n")
         .append(" $('vimp').className='vb'+(v=='penting'?' on':'');\n")
         .append(" $('vmal').className='vb'+(v=='masalah'?' on':'');\n")
         .append(" $('vall').className='vb'+(v=='all'?' on':'');\n")
         .append(" applyFilter();if(v=='masalah')$('flow').scrollTop=0}\n")
         .append("$('vimp').onclick=function(){setView('penting')};\n")
         .append("$('vmal').onclick=function(){setView('masalah')};\n")
         .append("$('vall').onclick=function(){setView('all')};\n")
         .append("['limit','port'].forEach(function(id){$(id).onchange=function(){sync();reset()}});\n")
         .append("$('st').onchange=function(){sync();applyFilter()};\n")
         .append("$('q').oninput=function(){S.q=$('q').value.toLowerCase();applyFilter()};\n")
         .append("$('pause').onclick=function(){S.auto=!S.auto;$('pause').textContent=S.auto?'⏸️':'▶️'};\n")
         .append("$('guidebtn').onclick=function(){var g=$('guide');g.style.display=(g.style.display=='none'?'':'none')};\n")
         .append("$('reset').onclick=reset;\n")
         .append("$('cpt').onclick=copyTable;\n")
         .append("$('cpjs').onclick=copyJSON;\n")
         .append("$('dl').onclick=function(){var a=document.createElement('a');a.href='/download?latest=1';a.download='';document.body.appendChild(a);a.click();document.body.removeChild(a)};\n")
         .append("function note(c2,txt){var d=document.createElement('div');d.className=c2;d.textContent=txt;$('notices').appendChild(d)}\n")
         .append("function url(){return '/logs?mode=all&limit='+S.limit+'&since='+S.since}\n")
         .append("function chips(j){var c=$('chips');var ids=[['9527','⚙️ KONFIG'],['9528','🌐 CDN'],['5050','🔑 LOGIN'],['5051','🎮 GAME'],['9530','🛡️ SDK'],['4000','📋 LOGGER']];\n")
         .append(" var h='';for(var i=0;i<ids.length;i++){var p=ids[i][0],cc=j.ports[p];\n")
         .append("  h+='<span class=chip><span class=\"dot '+((cc&&cc.requests>0)?'g':'m')+'\"></span><b>'+ids[i][1]+'</b> '+p+(cc?(' · '+cc.requests+' req'+(cc.errors?' · <span class=bad>'+cc.errors+' gagal</span>':'')):' · 0 req')+'</span>'}\n")
         .append(" c.innerHTML=h}\n")
         .append("function poll(){\n")
         .append(" if(!S.auto){setTimeout(poll,500);return}\n")
         .append(" fetch(url()).then(function(r){return r.json()}).then(function(j){\n")
         .append("  if(j.gap&&j.entries.length)note('n-warn','⚠️ GAP: sebagian entri buffer terbuang — arsip disk tetap utuh (💾 Unduh arsip)');\n")
         .append("  if(j.entries.length){append(j.entries);S.since=j.seqNewest}\n")
         .append("  var tot=(j.droppedGlobal||0)+(j.droppedPort||0);\n")
         .append("  if(tot>0&&tot!==lastDrop){lastDrop=tot;note('n-err','❌ buffer dropped '+tot+' — arsip disk tetap utuh')}\n")
         .append("  pollN++;if(pollN%3==0)refreshStats();\n")
         .append("  setTimeout(poll,1200);\n")
         .append(" }).catch(function(){setTimeout(poll,3000)})}\n")
         .append("function refreshStats(){fetch('/stats').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var up=Math.floor(j.uptimeMs/1000);\n")
         .append(" var t='⏱️ uptime '+Math.floor(up/3600)+'h'+Math.floor(up%3600/60)+'m'+(up%60)+'s · seq '+j.seq;\n")
         .append(" if(j.disk&&j.disk.enabled)t+=' · 💾 arsip '+j.disk.file+' '+(j.disk.bytes>=1048576?(j.disk.bytes/1048576).toFixed(1)+'MB':Math.round(j.disk.bytes/1024)+'KB')+' · '+j.disk.lines+' baris';\n")
         .append(" if(j.overlay&&j.overlay.checked)t+=j.overlay.granted?' · 🛡️ overlay ON':' · 🛡️ overlay OFF (normal)';\n")
         .append(" $('statsbar').textContent=t;chips(j);renderCrash()}).catch(function(){})}\n")
         .append("document.title='DB MONITOR :'+location.port;\n")
         .append("renderStages();refreshStats();sync();poll();\n")
         .append("setTimeout(refreshStats,800);\n");
        return s.toString();
    }

    /* ====================================================================
     * Bagian 30 — PANEL SDK CONFIG (koreksi owner: semua milik SDK di sdk/).
     * Edit kontrak + QHinfo + preset eksperimen loginTypes + tabel accounts,
     * semuanya dari browser HP (http://127.0.0.1:4000/sdkconfig) — tanpa PC,
     * tanpa file manager. Edit tercatat SDK-CONFIG-EDIT di log SYS.
     * ==================================================================== */

    private String rowJson(Db.Row r, boolean withEmail) {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"uid\":\"").append(JsonUtil.esc(r.uid)).append('"')
         .append(",\"username\":\"").append(JsonUtil.esc(r.username)).append('"');
        if (withEmail) b.append(",\"email\":\"").append(JsonUtil.esc(r.email)).append('"');
        b.append(",\"token\":\"").append(JsonUtil.esc(r.token)).append('"')
         .append(",\"isGuest\":").append(r.isGuest)
         .append(",\"createTime\":").append(r.createTime)
         .append(",\"lastLogin\":").append(r.lastLogin)
         .append('}');
        return b.toString();
    }

    /** State panel dalam satu JSON: backend, akun, isi semua file kontrak, status product. */
    private String sdkConfigApiJson() {
        SdkConfig.ensureIndex();
        Db db = Db.open(LoggerCore.serverRootPath);
        StringBuilder b = new StringBuilder(8192);
        b.append("{\"ver\":\"v3\"")
         .append(",\"backend\":\"").append(db.backend()).append('"')
         .append(",\"dbDir\":\"").append(JsonUtil.esc(LoggerCore.serverRootPath))
         .append("/sdk/data\"");
        Db.Row g = db.guest();
        b.append(",\"guest\":").append(g == null ? "null" : rowJson(g, false));
        java.util.List<Db.Row> accs = db.accounts();
        b.append(",\"accounts\":[");
        for (int i = 0; i < accs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(rowJson(accs.get(i), true));
        }
        b.append(']');
        // v16 — status product (gerbang 30001)
        b.append(",\"productCode\":\"").append(JsonUtil.esc(SdkConfig.lastProductSeen)).append('"')
         .append(",\"productOk\":").append(SdkConfig.lastProductOk)
         .append(",\"products\":[");
        java.util.ArrayList<String> prods = SdkConfig.productsList();
        for (int i = 0; i < prods.size(); i++) {
            if (i > 0) b.append(',');
            b.append('"').append(JsonUtil.esc(prods.get(i))).append('"');
        }
        b.append(']');
        // v18 — status izin overlay + sesi SDK (banner + panel dashboard)
        b.append(",\"overlay\":{\"checked\":").append(LoggerCore.overlayChecked)
         .append(",\"granted\":").append(LoggerCore.overlayGranted).append('}');
        boolean sesSaved = SessionReset.sessionExists(OverlayFixer.ctx);
        b.append(",\"session\":{\"saved\":").append(sesSaved)
         .append(",\"resetAuto\":").append(SessionReset.autoResetDone)
         .append(",\"resetInfo\":\"").append(JsonUtil.esc(SessionReset.lastResetInfo)).append("\"}");
        b.append(",\"files\":{");
        String[] names = SdkConfig.EDITABLE_NAMES;
        for (int i = 0; i < names.length; i++) {
            if (i > 0) b.append(',');
            b.append('"').append(names[i]).append("\":\"")
             .append(JsonUtil.esc(SdkConfig.readContractFile(names[i]))).append('"');
        }
        b.append('}');
        b.append('}');
        return b.toString();
    }

    /** Satu parameter form-urlencoded dari body POST. */
    private String formParam(HttpWorker.Request req, String key) {
        if (req.body == null || req.body.length == 0) return null;
        String s = new String(req.body, java.nio.charset.StandardCharsets.UTF_8);
        String[] parts = s.split("&");
        for (int i = 0; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            String k = eq < 0 ? parts[i] : parts[i].substring(0, eq);
            if (!k.equals(key)) continue;
            String v = eq < 0 ? "" : parts[i].substring(eq + 1);
            try { return java.net.URLDecoder.decode(v, "UTF-8"); }
            catch (Throwable t) { return v; }
        }
        return null;
    }

    /** Halaman panel SDK CONFIG — gaya visual sama dengan MONITOR SERVER. */
    private String sdkConfigHtml() {
        StringBuilder b = new StringBuilder(16384);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>SDK CONFIG</title>\n")
         .append("<style>\n")
         .append("*{box-sizing:border-box}\n")
         .append("body{font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#0f141a;color:#dbe4ec;margin:0;padding:10px;font-size:14px;line-height:1.45;user-select:text;-webkit-user-select:text}\n")
         .append("#top{display:flex;flex-wrap:wrap;gap:10px;align-items:baseline;margin-bottom:8px}\n")
         .append("#title{color:#8fd48f;font-weight:700;font-size:18px}\n")
         .append("#statsbar{color:#8a97a5;font-size:12px}\n")
         .append(".panel{background:#161d26;border:1px solid #2a333d;border-radius:10px;padding:10px 12px;margin-bottom:10px}\n")
         .append(".chip{display:inline-flex;align-items:center;gap:6px;background:#1b2530;border:1px solid #2a333d;border-radius:20px;padding:4px 12px;font-size:12px}\n")
         .append(".chip b{font-weight:600}\n")
         .append("textarea{width:100%;background:#0b0f14;color:#c7d1da;border:1px solid #2a333d;border-radius:6px;padding:7px 9px;font-family:Menlo,Consolas,monospace;font-size:12px;min-height:90px}\n")
         .append("textarea.small{min-height:38px}\n")
         .append("button{background:#1b222b;color:#dbe4ec;border:1px solid #2a333d;border-radius:6px;padding:6px 12px;font-family:inherit;font-size:13px;cursor:pointer;min-height:34px}\n")
         .append("button:hover{border-color:#4a5a6a}\n")
         .append("button.pr{border-color:#3a5a3a;color:#8fd48f;font-weight:600}\n")
         .append("button.on{background:#123a1f;border-color:#2f7a44;color:#9fe6a8}\n")
         .append(".sec{margin:0 0 10px;border:1px solid #232b34;border-radius:8px;overflow:hidden}\n")
         .append(".sech{background:#161c23;color:#8fd48f;padding:5px 10px;font-size:12px;display:flex;justify-content:space-between;align-items:center;gap:8px}\n")
         .append(".sec .bd{padding:8px 10px}\n")
         .append(".hint{color:#7f8c9a;font-size:11.5px;margin-top:4px}\n")
         .append("table{width:100%;border-collapse:collapse;font-size:12px}\n")
         .append("th{color:#9fb0c0;text-align:left;padding:4px 6px;border-bottom:1px solid #2a333d}\n")
         .append("td{padding:4px 6px;border-bottom:1px solid #1d2530;word-break:break-all}\n")
         .append(".g{color:#4cd964}.r{color:#ff8a80}.m{color:#8a97a5}\n")
         .append("#toast{position:fixed;bottom:14px;left:50%;transform:translateX(-50%);background:#123a1f;color:#9fe6a8;border:1px solid #2f7a44;padding:7px 16px;border-radius:8px;display:none;z-index:50;font-size:13px}\n")
         .append("#toast.err{background:#3a1214;color:#ff9e94;border-color:#7a2f34}\n")
         .append("</style></head><body>\n")
         .append("<div id=top><span id=title>SDK CONFIG</span><span id=statsbar>memuat …</span></div>\n")

         .append("<div class=panel><div id=chips></div>\n")
         .append("<div class=hint>Eksperimen crash: klik preset, lalu <b>force-stop &amp; buka game</b> satu variabel per restart. Hasil dilihat di MONITOR SERVER (log SDK).</div></div>\n")

         .append("<div class=sec><div class=sech><span>PRODUCT (gerbang 30001)</span><span id=prodchip class=m></span></div>\n")
         .append("<div class=bd><div id=prodl></div>\n")
         .append("<div style='display:flex;gap:6px;margin-top:6px;flex-wrap:wrap'><input id=prodin placeholder='productCode game' style='flex:1;min-width:180px'>\n")
         .append("<button class=pr onclick=prodAdd()>TERIMA PRODUCT</button></div>\n")
         .append("<div class=hint>productCode ASLI game (rekam device 23 Nov): 75880787318053043365515388491588 — sudah dimuat otomatis di profil v16. Tombol ini untuk menambah kode lain bila gerbang 30001 menolak lagi. Berlaku TANPA restart server (game tetap perlu force-stop &amp; buka).</div></div></div>\n")

         .append("<div class=panel>\n")
         .append("<div style='font-weight:700;color:#8fd48f;margin-bottom:6px'>PRESET loginTypes</div>\n")
         .append("<button id=p1 onclick=preset('1')>E1 [\"1\"] form email+TAMU</button> ")
         .append("<button id=p2 onclick=preset('12')>E2 [\"12\"] tamu-only (v11/v12)</button> ")
         .append("<button id=p3 onclick=preset('1,13')>E3 [\"1\",\"13\"] mirror server asli</button>\n")
         .append("</div>\n")

         .append("<div class=sec><div class=sech><span>SESI SDK (auto-login — v1)</span><span id=seschip class=m></span></div>\n")
         .append("<div class=bd><button class=pr onclick=sesReset()>RESET SESI SDK (keluar akun SDK)</button>\n")
         .append("<div class=hint>Menghapus sesi login tersimpan SDK (prefs qg_Users + info.qg) — AKUN DI DATABASE TIDAK DIHAPUS. Fungsi: memutus siklus crash auto-login (dialog window type 2008 butuh izin overlay; SDK asli tidak pernah mengecek). Setelah reset, game membuka UI LOGIN MANUAL yang terbukti aman. v1 juga mereset OTOMATIS saat boot bila: izin overlay OFF + crash terekam + sesi ada.</div></div></div>\n")

         .append("<div class=sec><div class=sech><span>v1/system/init.json</span><button class=pr onclick=saveFile('init.json','t_init')>SIMPAN</button></div>\n")
         .append("<div class=bd><textarea id=t_init spellcheck=false></textarea>\n")
         .append("<div class=hint>payTypes + loginTypes WAJIB ada (parser model/e game).</div></div></div>\n")

         .append("<div class=sec><div class=sech><span>profile.json</span><button class=pr onclick=saveFile('profile.json','t_profile')>SIMPAN</button></div>\n")
         .append("<div class=bd><textarea id=t_profile spellcheck=false></textarea>\n")
         .append("<div class=hint>Header PHP, verifySign, signSecret, daftar products.</div></div></div>\n")

         .append("<div class=sec><div class=sech><span>v1/user/ckRegVistor.json</span><button class=pr onclick=saveFile('ckRegVistor.json','t_ckreg')>SIMPAN</button></div>\n")
         .append("<div class=bd><textarea id=t_ckreg spellcheck=false></textarea></div></div>\n")

         .append("<div class=sec><div class=sech><span>fallback.json</span><button class=pr onclick=saveFile('fallback.json','t_fallback')>SIMPAN</button></div>\n")
         .append("<div class=bd><textarea id=t_fallback spellcheck=false></textarea></div></div>\n")

         .append("<div class=sec><div class=sech><span>QHinfo</span><button class=pr onclick=saveFile('QHinfo','t_qh')>SIMPAN</button></div>\n")
         .append("<div class=bd><textarea id=t_qh class=small spellcheck=false></textarea>\n")
         .append("<div class=hint>Dibaca client dari DALAM APK (bukan file ini) — nilai di sini dibakar ke assets/QHinfo saat rebuild APK berikutnya. Tanpa trailing slash.</div></div></div>\n")

         .append("<div class=sec><div class=sech><span>TABEL ACCOUNTS (sdk/data)</span><span id=dbchip class=m></span></div>\n")
         .append("<div class=bd style='max-height:280px;overflow-y:auto'><table id=accs></table></div></div>\n")

         .append("<div class=panel><a href='/' style='color:#8fd48f'>&larr; MONITOR SERVER</a></div>\n")
         .append("<div id=toast></div>\n")

         .append("<script>\n")
         .append("function $(id){return document.getElementById(id)}\n")
         .append("function toast(msg,err){var t=$('toast');t.textContent=msg;t.className=err?'err':'';t.style.display='block';clearTimeout(t._h);t._h=setTimeout(function(){t.style.display='none'},2600)}\n")
         .append("function preset(types){post('/sdkconfig/preset','types='+encodeURIComponent(types)).then(function(j){if(j.result){toast('init.json -> loginTypes ['+j.loginTypes+'] — force-stop & buka game');load()}else toast(j.error||'gagal',true)}).catch(function(){toast('jaringan gagal',true)})}\n")
         .append("function prodAdd(){var v=$('prodin').value.trim();if(!v){toast('isi productCode dulu',true);return}post('/sdkconfig/product-add','code='+encodeURIComponent(v)).then(function(j){if(j.result){toast('productCode DITERIMA — force-stop & buka game');$('prodin').value='';load()}else toast(j.error||'gagal',true)}).catch(function(){toast('jaringan gagal',true)})}\n")
         .append("function sesReset(){if(!confirm('RESET SESI SDK?\\nGame berikutnya membuka UI LOGIN MANUAL (akun di database tetap ada). Lanjut?'))return;post('/sdkconfig/session-reset','').then(function(j){if(j.result){toast('sesi SDK dihapus — force-stop & buka game: LOGIN MANUAL');load()}else toast(j.error||'gagal',true)}).catch(function(){toast('jaringan gagal',true)})}\n")
         .append("function saveFile(name,tid){var v=$(tid).value;post('/sdkconfig/save','name='+encodeURIComponent(name)+'&content='+encodeURIComponent(v)).then(function(j){if(j.result){toast(name+' tersimpan');load()}else toast(j.error||'gagal',true)}).catch(function(){toast('jaringan gagal',true)})}\n")
         .append("function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         .append("function load(){fetch('/sdkconfig/api').then(function(r){return r.json()}).then(function(j){\n")
         .append(" $('statsbar').textContent='backend '+j.backend+' · '+j.dbDir;\n")
         .append(" $('chips').innerHTML='<span class=chip><b>DB-BACKEND</b>&nbsp;'+esc(j.backend)+'</span><span class=chip><b>tamu</b>&nbsp;'+(j.guest?esc(j.guest.uid)+' ('+esc(j.guest.username)+')':'belum ada')+'</span><span class=chip><b>akun email</b>&nbsp;'+j.accounts.length+'</span>';\n")
         .append(" $('prodchip').textContent=j.productCode?('terakhir dikirim: '+j.productCode+(j.productOk?' — DITERIMA':' — DITOLAK (30001)')):'belum ada request game';\n")
         .append(" $('prodchip').className=j.productCode?(j.productOk?'g':'r'):'m';\n")
         .append(" var pl='';for(var pi=0;pi<(j.products||[]).length;pi++)pl+='<span class=chip style=margin:2px 4px 2px 0>'+esc(j.products[pi])+'</span>';\n")
         .append(" $('prodl').innerHTML=pl||'<span class=m>kosong</span>';\n")
         .append(" var ov=j.overlay||{},ss=j.session||{};\n")
         .append(" $('seschip').textContent=(ss.saved?'SESI TERSIMPAN':'tanpa sesi')+' · overlay '+(ov.checked?(ov.granted?'ON':'OFF (normal)'):'?')+(ss.resetAuto?' · DIRESET OTOMATIS':'');\n")
         .append(" $('seschip').className=ss.resetAuto?'g':'m';\n")
         .append(" $('t_init').value=j.files['init.json'];$('t_profile').value=j.files['profile.json'];$('t_ckreg').value=j.files['ckRegVistor.json'];$('t_fallback').value=j.files['fallback.json'];$('t_qh').value=j.files['QHinfo'];\n")
         .append(" var lt='';try{lt=JSON.parse(j.files['init.json']).data.loginTypes.join(',')}catch(e){}\n")
         .append(" $('p1').className='pr'+(lt=='1'?' on':'');$('p2').className='pr'+(lt=='12'?' on':'');$('p3').className='pr'+(lt=='1,13'?' on':'');\n")
         .append(" var h='<tr><th>uid</th><th>nama</th><th>email</th><th>tipe</th><th>login terakhir</th></tr>';\n")
         .append(" if(j.guest)h+='<tr><td>'+esc(j.guest.uid)+'</td><td>'+esc(j.guest.username)+'</td><td class=m>-</td><td class=g>TAMU</td><td class=m>'+new Date(j.guest.lastLogin).toLocaleString()+'</td></tr>';\n")
         .append(" for(var i=0;i<j.accounts.length;i++){var a=j.accounts[i];h+='<tr><td>'+esc(a.uid)+'</td><td>'+esc(a.username)+'</td><td>'+esc(a.email)+'</td><td>email</td><td class=m>'+new Date(a.lastLogin).toLocaleString()+'</td></tr>'}\n")
         .append(" $('accs').innerHTML=h;$('dbchip').textContent=j.backend;\n")
         .append("}).catch(function(){toast('API gagal dimuat',true)})}\n")
         .append("document.title='SDK CONFIG :'+location.port;\n")
         .append("load();\n")
         .append("</script></body></html>");
        return b.toString();
    }
}
