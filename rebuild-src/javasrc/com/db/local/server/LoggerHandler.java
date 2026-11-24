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
 *
 * Bagian 50 — KOREKSI OWNER (kata-kata persis):
 *   "itu log apa bisa di baca!!!! natural.. kalau log Inggris ya inggirs .
 *    log jangan anda karang lah !!! gmana mau tau apa yg server ini butuhkan
 *    kalau semuanya karangan gak jelas dari anda !!!"
 * CONSOLE v13 — RAW SERVER LOG (ROMBAK TOTAL rendering; supersede v12):
 *  1. LOG = DATA MENTAH, NOL KARANGAN: fungsi describe() (kalimat naratif
 *     "Koneksi WebSocket login — ...") DIHAPUS TOTAL. Baris utama kini
 *     PERSIS protokol, gaya access log server asli:
 *       GET /socket.io/?EIO=3&transport=polling&t=.. 200 · 103 B · 4 ms
 *     — path + QUERY STRING PENUH (tidak dipangkas seperti dulu), status
 *     angka polos, ukuran, durasi. BOOT/SYS/EXTRACT = pesan server apa
 *     adanya; WS frame = "WS sid=.. " + sub-baris recv/send berisi FRAME
 *     MENTAH (2probe/3probe/42[..]/43[..] — verbatim).
 *  2. FULL DISCLOSURE: sub-baris headers / request / response / recv / send /
 *     data / error menampilkan ISI CAPTURE MENTAH apa adanya — TANPA
 *     pemotongan 200/160 karakter (dulu data disembunyikan di balik potongan).
 *     Satu-satunya batas = batas tangkap server (preview 4 KB teks / 2 KB
 *     file statis / binary marker). Body panjang menggulir (max-height).
 *  3. NOL EMOJI DI MANA PUN (teks log termasuk — log server asli tidak
 *     pakai emoji); kecantikan UI tetap dari IKON SVG (registry IC + svg() +
 *     paintIcons() — tombol/chip/banner, gaya feather/lucide).
 *  4. BAHASA LOG: protokol memang English — dibiarkan English persis aslinya
 *     ("kalau log Inggris ya inggirs"); pesan server internal ditulis raw
 *     English ringkas (open sid=.., long-poll timeout, ws upgrade sid=..,
 *     client disconnect (41), ping->pong, session already closed, removed).
 *  5. Salin log = TEKS MENTAH PERSIS PROTOKOL (time #seq port METHOD path
 *     status bytes + headers/request/response/recv/send/data verbatim) —
 *     ditempel di mana pun tetap seperti log server sungguhan; Salin JSON
 *     tetap tersedia.
 *  6. yang TIDAK berubah (semua arahan lama tetap): overlay = INFO bukan
 *     error (Bagian 39); tombol IKON saja (Bagian 41); port 4000 murni log
 *     monitoring — rute /sdkconfig* + /fix/overlay tetap 404 (Bagian 41/43);
 *     tanpa popup "tips" (Bagian 42); tanpa modal klik (Bagian 47); poll
 *     incremental ?since= 1,2 dtk, filter tahap/port/status/cari/limit,
 *     Salin JSON, unduh arsip NDJSON, banner CRASH + salin trace (Bagian 32).
 * Routing:
 *   GET /                    → console HTML live (poll incremental)
 *   GET /stats               → JSON counter per port + dropped + status disk NDJSON
 *   GET /health              → JSON status bind + ekstraksi + disk
 *   GET /logs?mode=all|port&port=P&limit=N&since=SEQ
 *                            → mode ALL/PORT, kronologis naik, INCREMENTAL via since
 *                              (poll efisien — tidak mengirim ulang yang sudah dilihat);
 *                              gap=true bila ada entri terbuang antara since dan buffer
 *   GET /download            → JSON daftar file arsip NDJSON
 *   GET /download?latest=1   → unduh file arsip terbaru
 *   GET /download?file=NAME  → unduh file arsip tertentu (nama tervalidasi)
 *   GET /crash               → daftar + isi laporan crash terbaru
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
        } else if ("/crash".equals(p)) {
            // v17 — daftar + isi laporan crash terbaru (banner merah console)
            status = 200;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text(crashJson());
        } else {
            status = 404;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text("{\"error\":\"gunakan / , /stats , /health , /logs?mode=all|port&port=..&limit=N&since=SEQ , /download , /crash\"}");
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
     * JSON laporan crash: daftar file crash-*.txt di logs/ + isi file
     * terbaru (cap 30000 karakter). Dipakai banner CRASH console.
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
            return "(read failed: " + t + ")";
        }
    }

    /* ================= console RAW SERVER LOG (Bagian 50, v13) =================
     * Rendering log = DATA MENTAH (method + path&query penuh + status + bytes +
     * durasi + headers + body verbatim + frame verbatim). NOL karangan, NOL
     * emoji, NOL pemotongan selain batas tangkap. Chrome UI tetap berikon SVG.
     */

    private String dashboardHtml() {
        StringBuilder b = new StringBuilder(40000);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>DB LOCAL SERVER — raw console</title>\n")
         .append("<style>\n")
         .append("*{box-sizing:border-box}\n")
         .append("html,body{height:100%}\n")
         .append("body{font-family:'Inter','Segoe UI',system-ui,-apple-system,sans-serif;background:#04070c;color:#dce6f1;margin:0;font-size:13px;line-height:1.6;display:flex;flex-direction:column;user-select:text;-webkit-user-select:text}\n")
         .append("body::before{content:'';position:fixed;inset:0;pointer-events:none;z-index:0;background:radial-gradient(640px 320px at 10% -8%,rgba(61,220,132,.07),transparent 60%),radial-gradient(700px 340px at 96% -10%,rgba(79,195,221,.05),transparent 60%)}\n")
         .append("/* ---- ikon SVG (ICONS v13 — chrome UI, bukan emoji) ---- */\n")
         .append(".ic{width:14px;height:14px;flex:none;display:inline-block;vertical-align:-2.5px}\n")
         .append("/* ---- header kaca + garis glow ---- */\n")
         .append("#hdr{position:relative;z-index:2;padding:10px 18px;background:rgba(8,14,22,.92);border-bottom:1px solid #16222f;box-shadow:0 6px 24px rgba(0,0,0,.4)}\n")
         .append("#hdr::after{content:'';position:absolute;left:0;right:0;bottom:-1px;height:1px;background:linear-gradient(90deg,transparent,rgba(61,220,132,.5),transparent)}\n")
         .append("#l1{display:flex;align-items:center;gap:10px;flex-wrap:wrap}\n")
         .append("#logo{width:40px;height:40px;border-radius:11px;display:inline-flex;align-items:center;justify-content:center;color:#3ddc84;background:linear-gradient(145deg,#0b2416,#071409);border:1px solid #1f5c37;box-shadow:0 0 16px rgba(61,220,132,.35),inset 0 0 10px rgba(61,220,132,.12);flex:none}\n")
         .append("#logo .ic{width:22px;height:22px;vertical-align:0}\n")
         .append("#dot{width:9px;height:9px;border-radius:50%;background:#3ddc84;box-shadow:0 0 10px rgba(61,220,132,.9);animation:pl 2.4s ease-in-out infinite;flex:none}\n")
         .append("@keyframes pl{50%{opacity:.55}}\n")
         .append("#title{font-weight:800;font-size:15.5px;letter-spacing:.8px;background:linear-gradient(90deg,#f2f8fd,#8fe6b0);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent}\n")
         .append("#ver{background:linear-gradient(180deg,#0b2416,#071409);border:1px solid #1f5c37;color:#7ee2a8;border-radius:7px;padding:2px 10px;font-size:11px;font-weight:800;letter-spacing:.6px;box-shadow:0 0 10px rgba(61,220,132,.3)}\n")
         .append("#versub{color:#5d7183;font-size:11.5px}\n")
         .append("#sp{flex:1}\n")
         .append(".ib{width:34px;height:34px;border-radius:9px;display:inline-flex;align-items:center;justify-content:center;background:#0b1420;border:1px solid #1c2a3a;color:#93a5b8;cursor:pointer;transition:border-color .18s,background .18s,color .18s,transform .18s,box-shadow .18s;padding:0;font-family:inherit}\n")
         .append(".ib .ic{width:16px;height:16px;vertical-align:0}\n")
         .append(".ib:hover{border-color:#2e4358;color:#dce6f1;background:#101c2a;transform:translateY(-1px);box-shadow:0 4px 12px rgba(0,0,0,.4)}\n")
         .append(".ib.on{border-color:#1f5c37;color:#3ddc84;background:#08150d;box-shadow:0 0 12px rgba(61,220,132,.25)}\n")
         .append(".ibsep{width:1px;height:20px;background:#1c2a3a;margin:0 3px;flex:none}\n")
         .append("/* ---- chip status berikon ---- */\n")
         .append("#stchips{margin-top:10px;display:flex;gap:7px;flex-wrap:wrap;align-items:center}\n")
         .append(".schip{display:inline-flex;align-items:center;gap:6px;background:rgba(13,21,31,.8);border:1px solid #1a2836;border-radius:9px;padding:5px 11px;font-size:11.5px;color:#93a5b8;font-weight:600;letter-spacing:.3px;transition:border-color .18s,color .18s,box-shadow .18s}\n")
         .append(".schip .ic{width:13px;height:13px}\n")
         .append(".schip b{color:#cfe0ef;font-weight:700}\n")
         .append(".schip.up{border-color:rgba(61,220,132,.45);color:#63d990;box-shadow:0 0 10px rgba(61,220,132,.12)}\n")
         .append(".schip.up b{color:#a9efc6}\n")
         .append(".schip.dw{color:#44586a}\n")
         .append("#stbadge.ok{color:#3ddc84}\n")
         .append("#stbadge.bad{color:#ff5d5d}\n")
         .append("/* ---- panel filter berikon ---- */\n")
         .append("#ctl{position:relative;z-index:1;display:none;padding:10px 18px;border-bottom:1px solid #16222f;background:#070d15;flex-wrap:wrap;gap:8px 18px;align-items:center}\n")
         .append("#ctl label{color:#7e919f;font-size:11.5px;font-weight:600;letter-spacing:.3px;display:inline-flex;align-items:center;gap:7px}\n")
         .append("#ctl label .ic{color:#4fc3dd}\n")
         .append("select,input{background:#0b1420;color:#dce6f1;border:1px solid #1c2a3a;border-radius:7px;padding:5px 10px;font-family:inherit;font-size:12px}\n")
         .append("input{min-width:170px}\n")
         .append("input:focus,select:focus{outline:none;border-color:#1f5c37}\n")
         .append("/* ---- crash banner ---- */\n")
         .append("#crashbn{position:relative;z-index:1;display:none;padding:11px 18px;border-bottom:1px solid #4a262c;background:#130a0c;box-shadow:inset 0 0 24px rgba(255,93,93,.07)}\n")
         .append("#crashbn .ch{color:#ff5d5d;font-weight:800;font-size:12px;display:inline-flex;align-items:center;gap:7px}\n")
         .append("#crashbn .cd{color:#9fb2c4;font-size:11.5px;margin-top:3px}\n")
         .append("#crashbn pre{margin:7px 0 4px;padding:10px;background:#070b11;border:1px solid #2a1a1e;border-radius:8px;max-height:180px;overflow:auto;white-space:pre-wrap;word-break:break-word;font-size:11px;color:#d8c2bf}\n")
         .append("#notices{position:relative;z-index:1;padding:0 18px}\n")
         .append(".nt{font-size:11.5px;padding:3px 0;display:flex;align-items:baseline;gap:7px}\n")
         .append(".nt .ic{width:13px;height:13px;position:relative;top:1.5px}\n")
         .append(".nt-w{color:#ffb454}.nt-e{color:#ff5d5d}\n")
         .append("/* ---- stream RAW kartu log ---- */\n")
         .append("#stream{position:relative;z-index:1;flex:1;overflow-y:auto;padding:14px 18px 18px}\n")
         .append("#stream::-webkit-scrollbar{width:10px;height:10px}\n")
         .append("#stream::-webkit-scrollbar-track{background:transparent}\n")
         .append("#stream::-webkit-scrollbar-thumb{background:linear-gradient(180deg,#223143,#16212e);border-radius:6px;border:2px solid #04070c}\n")
         .append("#banner{display:flex;gap:13px;align-items:flex-start;border:1px solid #1f5c37;border-radius:12px;background:linear-gradient(180deg,#0a1410,#081009);box-shadow:0 0 26px rgba(61,220,132,.14),inset 0 0 18px rgba(61,220,132,.05);padding:14px 18px;margin:0 0 12px}\n")
         .append("#blogo{width:42px;height:42px;border-radius:11px;display:inline-flex;align-items:center;justify-content:center;color:#3ddc84;background:linear-gradient(145deg,#0b2416,#071409);border:1px solid #1f5c37;flex:none}\n")
         .append("#blogo .ic{width:23px;height:23px;vertical-align:0;filter:drop-shadow(0 0 8px rgba(61,220,132,.5))}\n")
         .append("#banner .b1{font-weight:800;font-size:13.5px;color:#a9efc6;letter-spacing:.4px}\n")
         .append("#banner .bl{color:#63a97f;font-size:11.5px;line-height:1.75;margin-top:3px}\n")
         .append("#banner .bl .ic{width:13px;height:13px;color:#3ddc84}\n")
         .append("#banner .bl b{color:#a9efc6}\n")
         .append(".ln{position:relative;background:linear-gradient(180deg,#0a121b,#091017);border:1px solid #131f2b;border-left:3px solid #2a3a4c;border-radius:9px;padding:9px 12px 9px 14px;margin-bottom:8px;animation:fadein .2s ease-out;transition:border-color .15s,box-shadow .15s}\n")
         .append("@keyframes fadein{from{opacity:0;transform:translateY(3px)}}\n")
         .append(".ln:hover{border-color:#223142;box-shadow:0 3px 14px rgba(0,0,0,.35)}\n")
         .append(".ln.g{border-left-color:#3ddc84}\n")
         .append(".ln.d{border-left-color:#4fc3dd}\n")
         .append(".ln.w{border-left-color:#ffb454}\n")
         .append(".ln.r{border-left-color:#ff5d5d;background:linear-gradient(180deg,#150b0e,#100a0c);border-color:#3a2026}\n")
         .append(".rh{display:flex;align-items:center;gap:8px;margin-bottom:5px;flex-wrap:wrap}\n")
         .append(".lv{display:inline-flex;align-items:center;gap:5px;font-weight:800;font-size:10.5px;letter-spacing:.8px;border-radius:6px;padding:2.5px 8px}\n")
         .append(".lv .ic{width:12px;height:12px}\n")
         .append(".l-g{color:#3ddc84;background:rgba(61,220,132,.1);border:1px solid rgba(61,220,132,.25)}\n")
         .append(".l-d{color:#4fc3dd;background:rgba(79,195,221,.1);border:1px solid rgba(79,195,221,.25)}\n")
         .append(".l-w{color:#ffb454;background:rgba(255,180,84,.1);border:1px solid rgba(255,180,84,.25)}\n")
         .append(".l-r{color:#ff5d5d;background:rgba(255,93,93,.12);border:1px solid rgba(255,93,93,.3)}\n")
         .append(".cp{display:inline-flex;align-items:center;gap:5px;font-weight:800;font-size:10.5px;letter-spacing:.6px;border-radius:6px;padding:2.5px 8px;border:1px solid transparent}\n")
         .append(".cp .ic{width:12px;height:12px}\n")
         .append(".c-CORE{color:#8fa0b0;background:rgba(143,160,176,.09);border-color:rgba(143,160,176,.22)}\n")
         .append(".c-CONFIG{color:#d9c377;background:rgba(217,195,119,.09);border-color:rgba(217,195,119,.22)}\n")
         .append(".c-CDN{color:#3fd6b4;background:rgba(63,214,180,.09);border-color:rgba(63,214,180,.22)}\n")
         .append(".c-SDK{color:#b899e8;background:rgba(184,153,232,.09);border-color:rgba(184,153,232,.22)}\n")
         .append(".c-LOGIN{color:#f0a35e;background:rgba(240,163,94,.09);border-color:rgba(240,163,94,.22)}\n")
         .append(".c-GAME{color:#37c9a6;background:rgba(55,201,166,.09);border-color:rgba(55,201,166,.22)}\n")
         .append(".c-MONITOR{color:#8a97a5;background:rgba(138,151,165,.09);border-color:rgba(138,151,165,.22)}\n")
         .append(".rmeta{margin-left:auto;display:inline-flex;gap:8px;color:#55677a;font-size:10.5px;font-weight:600;font-variant-numeric:tabular-nums}\n")
         .append(".rmeta .sq{color:#3d4e60}\n")
         .append("/* ---- RAW main line: method /full/path?query status · bytes · dur ---- */\n")
         .append(".ev{color:#dce6f1;font-family:'JetBrains Mono','Roboto Mono',Menlo,Consolas,'Droid Sans Mono',monospace;font-size:12.5px;white-space:pre-wrap;word-break:break-word}\n")
         .append(".ev b{color:#f2f8fd}\n")
         .append(".ev .dim{color:#5d7183}\n")
         .append(".ev .s2{color:#3ddc84;font-weight:800}.ev .s3{color:#ffb454;font-weight:800}.ev .s4{color:#ff8484;font-weight:800}.ev .s5{color:#ff5d5d;font-weight:800}.ev .s0{color:#8fa0b0}\n")
         .append(".ev.w{color:#ffd9a0}.ev.r{color:#ffb3b3}.ev.d{color:#8fa3b5}\n")
         .append(".dtl{margin-top:7px;border-left:2px solid #1b2836;padding-left:10px;display:flex;flex-direction:column;gap:4px}\n")
         .append(".ln.r .dtl{border-left-color:#3a2026}\n")
         .append(".sub{display:flex;gap:7px;align-items:baseline;font-family:'JetBrains Mono','Roboto Mono',Menlo,Consolas,'Droid Sans Mono',monospace;font-size:11.5px;line-height:1.6;color:#7e919f}\n")
         .append(".sub .ic{width:12px;height:12px;position:relative;top:2px;color:#4c5f70}\n")
         .append(".sub>span{white-space:pre-wrap;word-break:break-word;flex:1;min-width:0;max-height:260px;overflow-y:auto}\n")
         .append(".sub>span::-webkit-scrollbar{width:8px;height:8px}\n")
         .append(".sub>span::-webkit-scrollbar-thumb{background:#223143;border-radius:5px}\n")
         .append(".sub b{color:#b9cbd9;font-weight:700}\n")
         .append(".lbl{color:#647a8d;font-weight:800}\n")
         .append(".s-det{color:#8298ab}\n")
         .append(".s-req{color:#7d93a6}\n")
         .append(".s-req .ic{color:#4fc3dd}\n")
         .append(".s-req b{color:#a8cfdd}\n")
         .append(".s-res{color:#7fa98f}\n")
         .append(".s-res .ic{color:#3ddc84}\n")
         .append(".s-res b{color:#a9d8bb}\n")
         .append(".s-ext{color:#5d7183}\n")
         .append(".s-ext .ic{color:#d9c377}\n")
         .append("#cursor{display:inline-block;width:8px;height:14px;background:#3ddc84;vertical-align:-2px;animation:bl 1.1s step-end infinite;box-shadow:0 0 8px rgba(61,220,132,.6)}\n")
         .append("@keyframes bl{50%{opacity:0}}\n")
         .append("#pausemark{color:#64778a;font-style:italic}\n")
         .append("/* ---- footer sticky ---- */\n")
         .append("#ftr{position:relative;z-index:2;padding:9px 18px;border-top:1px solid #16222f;background:rgba(8,14,22,.94);display:flex;gap:8px;align-items:center;flex-wrap:wrap;box-shadow:0 -6px 24px rgba(0,0,0,.4)}\n")
         .append("#sum{color:#64778a;font-size:11.5px;flex:1;min-width:220px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}\n")
         .append("#sum b{color:#cfe0ef;font-weight:700}\n")
         .append("#toast{position:fixed;bottom:18px;left:50%;transform:translateX(-50%);background:#08150d;color:#7ee2a8;border:1px solid #1f5c37;padding:9px 18px;border-radius:9px;display:none;z-index:50;font-size:12px;font-weight:600;box-shadow:0 6px 24px rgba(0,0,0,.5),0 0 14px rgba(61,220,132,.2);animation:rise .2s ease-out}\n")
         .append("#toast.err{background:#130a0c;color:#ff5d5d;border-color:#4a262c;box-shadow:0 6px 24px rgba(0,0,0,.5),0 0 14px rgba(255,93,93,.2)}\n")
         .append("@keyframes rise{from{opacity:0;transform:translate(-50%,6px)}}\n")
         .append("#guide{position:relative;z-index:1;display:none;padding:13px 18px;border-bottom:1px solid #16222f;background:#070d15;color:#93a5b8;font-size:11.5px;line-height:1.8}\n")
         .append(".gsec{margin:7px 0}\n")
         .append(".gsec .gh{display:inline-flex;align-items:center;gap:7px;color:#3ddc84;font-weight:800;font-size:12px;letter-spacing:.5px}\n")
         .append(".gsec .gh .ic{width:14px;height:14px}\n")
         .append(".gsec .gt{color:#8fa0b0;margin-top:2px}\n")
         .append(".gsec .gt b{color:#cfe0ef}\n")
         .append(".gsec .gt code{font-family:'JetBrains Mono','Roboto Mono',Menlo,Consolas,monospace;color:#a9efc6;background:rgba(61,220,132,.07);border:1px solid rgba(61,220,132,.18);border-radius:5px;padding:1px 6px;font-size:11px}\n")
         .append("@media(max-width:680px){#stream{padding:10px 10px 14px}#hdr{padding:9px 12px}#ctl{padding:10px 12px}#ftr{padding:8px 12px}#notices{padding:0 12px}#versub{display:none}.sq{display:none}.ln{padding:8px 9px 8px 11px;border-radius:8px}.dtl{padding-left:8px}.sub{font-size:11px}.rmeta{font-size:10px}#banner{padding:11px 13px}}\n")
         .append("</style></head><body>\n")

         .append("<div id=hdr><div id=l1>")
         .append("<span id=logo><span data-ic=flame></span></span><span id=dot></span>")
         .append("<span id=title>DB LOCAL SERVER</span><span id=ver>v13</span><span id=versub>· raw server log · com.db.local</span>")
         .append("<span id=sp></span>")
         .append("<button id=vimp class=ib title='Penting saja — boot, konfigurasi, SDK, login, game, crash'></button>")
         .append("<button id=vmal class=ib title='Masalah saja — WARN / ERROR'></button>")
         .append("<button id=vall class=\"ib on\" title='Semua log'></button>")
         .append("<span class=ibsep></span>")
         .append("<button id=pf class=ib title='Panel filter lanjutan'></button>")
         .append("<button id=pause class=ib title='Jeda / lanjut pembaruan otomatis'></button>")
         .append("<button id=reset class=ib title='Bersihkan tampilan log'></button>")
         .append("<button id=guidebtn class=ib title='Panduan membaca log'></button>")
         .append("</div>\n")
         .append("<div id=stchips><span class=schip><span data-ic=clock></span>menghubungkan …</span></div></div>\n")

         .append("<div id=ctl>\n")
         .append("<label><span data-ic=search></span>Cari kata kunci <input id=q placeholder='mis. login, crash, all.zip …'></label>\n")
         .append("<label><span data-ic=tag></span>Tahap <select id=stage><option value=''>semua</option><option>KONFIG</option><option>CDN</option><option>SDK</option><option>LOGIN</option><option>GAME</option><option>SISTEM</option><option>LOGGER</option></select></label>\n")
         .append("<label><span data-ic=server></span>Port <select id=port><option value=0>semua</option><option value=9527>9527 konfig</option><option value=9528>9528 CDN</option>")
         .append("<option value=5050>5050 login</option><option value=5051>5051 game</option><option value=9530>9530 SDK</option><option value=4000>4000 logger</option></select></label>\n")
         .append("<label><span data-ic=activity></span>Status <select id=st><option value=''>semua</option><option value=2>sukses (2xx)</option><option value=4>gagal (4xx)</option><option value=5>rusak (5xx)</option><option value=e>bermasalah</option></select></label>\n")
         .append("<label><span data-ic=hash></span>Jumlah <select id=limit><option>100</option><option selected>500</option><option>2000</option><option value=0>semua buffer</option></select></label>\n")
         .append("</div>\n")

         .append("<div id=crashbn></div>\n")
         .append("<div id=notices></div>\n")
         .append("<div id=stream>")
         .append("<div id=banner><span id=blogo><span data-ic=flame></span></span><div><div class=b1>DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v13</div><div class=bl><span data-ic=terminal></span> raw console · http://127.0.0.1:4000 — seluruh trafik game menuju server lokal mengalir ke sini <b>PERSIS PROTOKOLNYA</b><br><span data-ic=check></span> baris utama = <b>METHOD /path?query status · ukuran · durasi</b> — sub-baris <b>headers / request / response / recv / send / data / error</b> berisi isi capture MENTAH apa adanya: tanpa parafrase, tanpa terjemahan, tanpa pemotongan</div></div></div>\n")
         .append("<div id=tb></div><div id=tail><span id=pausemark></span><span id=cursor> </span></div></div>\n")

         .append("<div id=ftr>")
         .append("<button id=cpt class=ib title='Salin log — teks mentah persis protokol'></button>")
         .append("<button id=cpjs class=ib title='Salin JSON log'></button>")
         .append("<button id=dl class=ib title='Unduh arsip NDJSON — log lengkap tersimpan di disk'></button>")
         .append("<span id=sum>…</span></div>\n")

         .append("<div id=toast></div>\n")
         .append("<div id=guide>")
         .append("<div class=gsec><span class=gh><span data-ic=terminal></span>ALUR NORMAL BOOT</span><div class=gt>9527 konfigurasi → 9528 CDN → 9530 SDK → layar login → TAMU / DAFTAR / LOGIN → verifikasi 5050 → masuk game 5051</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=check></span>LEVEL LOG</span><div class=gt><b>INFO</b> alur normal · <b>DEBUG</b> traffic halus (aset game, heartbeat, config kosong) · <b>WARN</b> perlu perhatian (4xx / redirect) · <b>ERROR</b> gagal / crash</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=list></span>FORMAT TIAP ENTRI — RAW SERVER LOG</span><div class=gt>baris utama = <code>GET /socket.io/?EIO=3&amp;transport=polling&amp;t=.. 200 · 103 B · 4 ms</code> — PERSIS request line server: path + <b>query string penuh</b> (tidak dipangkas), status angka polos, ukuran, durasi. Sub-baris: <code>headers:</code> header request mentah · <code>request:</code> / <code>response:</code> body mentah · <code>recv:</code> / <code>send:</code> frame socket mentah · <code>data:</code> metadata protokol · <code>error:</code> exception. Log DITAMPILKAN APA ADANYA — yang Inggris tetap English — satu-satunya batas = batas tangkap preview (4 KB teks / 2 KB file statis).</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=shield></span>WEBSOCKET 5050 / 5051</span><div class=gt>entry <code>GET /socket.io/?…transport=websocket… 101</code> = upgrade WS; <code>WS sid=..</code> = frame socket.io mentah — <b>recv</b> = frame dari game, <b>send</b> = frame dari server (2probe/3probe handshake, 42[..] event, 43[..] ACK, 41 disconnect); <code>ping-&gt;pong</code> = heartbeat engine.io (control frame, tanpa payload).</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=star></span>LAYAR LOGIN SDK</span><div class=gt>TAMU = langsung main · DAFTAR = buat akun baru · LOGIN = akun terdaftar. Izin overlay OFF itu NORMAL (game asli tidak memerlukan overlay; SDK tetap berjalan).</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=octagon></span>CRASH</span><div class=gt>banner merah di atas console menampilkan isi file laporan mentah (stack trace + 50 log terakhir) — tombol salin untuk dikirim ke analisis akar.</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=copy></span>TOMBOL BAWAH</span><div class=gt>salin log (teks mentah persis protokol) · salin JSON · unduh arsip NDJSON. Filter lanjutan: tombol ikon sliders di kanan atas.</div></div>\n")
         .append("</div>\n")

         .append("<script>\n")
         .append(Js())
         .append("</script>\n")
         .append("</body></html>");
        return b.toString();
    }

    private static String Js() {
        StringBuilder s = new StringBuilder(26000);
        s.append("var S={since:0,view:'all',stage:'',st:'',q:'',limit:500,auto:true,port:0};\n")
         .append("var ENT={},MAXC=3000,pollN=0,LASTSUM='',CRASHLATEST='',lastDrop=-1;\n")
         .append("function $(id){return document.getElementById(id)}\n")
         .append("function esc(x){return (x==null?'':''+x).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         .append("function trunc(s,n){s=''+s;return s.length>n?s.substring(0,n)+'\\u2026':s}\n")
         .append("/* ---- REGISTRY ICON SVG (chrome UI — icon sungguhan, NOL emoji) ---- */\n")
         .append("function svg(p){return '<svg class=\"ic\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">'+p+'</svg>'}\n")
         .append("var IC={};\n")
         .append("IC.flame=svg(\"<path d='M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z'/>\");\n")
         .append("IC.server=svg(\"<rect x='2' y='2' width='20' height='8' rx='2' ry='2'/><rect x='2' y='14' width='20' height='8' rx='2' ry='2'/><line x1='6' y1='6' x2='6.01' y2='6'/><line x1='6' y1='18' x2='6.01' y2='18'/>\");\n")
         .append("IC.settings=svg(\"<circle cx='12' cy='12' r='3'/><path d='M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z'/>\");\n")
         .append("IC.globe=svg(\"<circle cx='12' cy='12' r='10'/><line x1='2' y1='12' x2='22' y2='12'/><path d='M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z'/>\");\n")
         .append("IC.shield=svg(\"<path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'/>\");\n")
         .append("IC.gamepad=svg(\"<line x1='6' y1='11' x2='10' y2='11'/><line x1='8' y1='9' x2='8' y2='13'/><line x1='15' y1='12' x2='15.01' y2='12'/><line x1='18' y1='10' x2='18.01' y2='10'/><path d='M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z'/>\");\n")
         .append("IC.card=svg(\"<rect x='1' y='4' width='22' height='16' rx='2' ry='2'/><line x1='1' y1='10' x2='23' y2='10'/>\");\n")
         .append("IC.radio=svg(\"<circle cx='12' cy='12' r='2'/><path d='M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14'/>\");\n")
         .append("IC.clock=svg(\"<circle cx='12' cy='12' r='10'/><polyline points='12 6 12 12 16 14'/>\");\n")
         .append("IC.hash=svg(\"<line x1='4' y1='9' x2='20' y2='9'/><line x1='4' y1='15' x2='20' y2='15'/><line x1='10' y1='3' x2='8' y2='21'/><line x1='16' y1='3' x2='14' y2='21'/>\");\n")
         .append("IC.archive=svg(\"<polyline points='21 8 21 21 3 21 3 8'/><rect x='1' y='3' width='22' height='5'/><line x1='10' y1='12' x2='14' y2='12'/>\");\n")
         .append("IC.layers=svg(\"<polygon points='12 2 2 7 12 12 22 7 12 2'/><polyline points='2 17 12 22 22 17'/><polyline points='2 12 12 17 22 12'/>\");\n")
         .append("IC.activity=svg(\"<polyline points='22 12 18 12 15 21 9 3 6 12 2 12'/>\");\n")
         .append("IC.star=svg(\"<polygon points='12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2'/>\");\n")
         .append("IC.warn=svg(\"<path d='M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z'/><line x1='12' y1='9' x2='12' y2='13'/><line x1='12' y1='17' x2='12.01' y2='17'/>\");\n")
         .append("IC.x=svg(\"<circle cx='12' cy='12' r='10'/><line x1='15' y1='9' x2='9' y2='15'/><line x1='9' y1='9' x2='15' y2='15'/>\");\n")
         .append("IC.check=svg(\"<path d='M22 11.08V12a10 10 0 1 1-5.93-9.14'/><polyline points='22 4 12 14.01 9 11.01'/>\");\n")
         .append("IC.search=svg(\"<circle cx='11' cy='11' r='8'/><line x1='21' y1='21' x2='16.65' y2='16.65'/>\");\n")
         .append("IC.list=svg(\"<line x1='8' y1='6' x2='21' y2='6'/><line x1='8' y1='12' x2='21' y2='12'/><line x1='8' y1='18' x2='21' y2='18'/><line x1='3' y1='6' x2='3.01' y2='6'/><line x1='3' y1='12' x2='3.01' y2='12'/><line x1='3' y1='18' x2='3.01' y2='18'/>\");\n")
         .append("IC.sliders=svg(\"<line x1='4' y1='21' x2='4' y2='14'/><line x1='4' y1='10' x2='4' y2='3'/><line x1='12' y1='21' x2='12' y2='12'/><line x1='12' y1='8' x2='12' y2='3'/><line x1='20' y1='21' x2='20' y2='16'/><line x1='20' y1='12' x2='20' y2='3'/><line x1='1' y1='14' x2='7' y2='14'/><line x1='9' y1='8' x2='15' y2='8'/><line x1='17' y1='16' x2='23' y2='16'/>\");\n")
         .append("IC.pause=svg(\"<rect x='6' y='4' width='4' height='16' rx='1'/><rect x='14' y='4' width='4' height='16' rx='1'/>\");\n")
         .append("IC.play=svg(\"<polygon points='5 3 19 12 5 21 5 3'/>\");\n")
         .append("IC.trash=svg(\"<polyline points='3 6 5 6 21 6'/><path d='M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2'/><line x1='10' y1='11' x2='10' y2='17'/><line x1='14' y1='11' x2='14' y2='17'/>\");\n")
         .append("IC.help=svg(\"<circle cx='12' cy='12' r='10'/><path d='M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3'/><line x1='12' y1='17' x2='12.01' y2='17'/>\");\n")
         .append("IC.copy=svg(\"<rect x='9' y='9' width='13' height='13' rx='2' ry='2'/><path d='M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'/>\");\n")
         .append("IC.braces=svg(\"<path d='M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1'/><path d='M16 21h1a2 2 0 0 0 2-2v-5c0-1.1.9-2 2-2a2 2 0 0 1-2-2V5a2 2 0 0 0-2-2h-1'/>\");\n")
         .append("IC.download=svg(\"<path d='M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4'/><polyline points='7 10 12 15 17 10'/><line x1='12' y1='15' x2='12' y2='3'/>\");\n")
         .append("IC.terminal=svg(\"<polyline points='4 17 10 11 4 5'/><line x1='12' y1='19' x2='20' y2='19'/>\");\n")
         .append("IC.tag=svg(\"<path d='M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.83z'/><line x1='7' y1='7' x2='7.01' y2='7'/>\");\n")
         .append("IC.corner=svg(\"<polyline points='15 10 20 15 15 20'/><path d='M4 4v7a4 4 0 0 0 4 4h12'/>\");\n")
         .append("IC.up=svg(\"<circle cx='12' cy='12' r='10'/><polyline points='16 12 12 8 8 12'/><line x1='12' y1='16' x2='12' y2='8'/>\");\n")
         .append("IC.down=svg(\"<circle cx='12' cy='12' r='10'/><polyline points='8 12 12 16 16 12'/><line x1='12' y1='8' x2='12' y2='16'/>\");\n")
         .append("IC.cpu=svg(\"<rect x='4' y='4' width='16' height='16' rx='2' ry='2'/><rect x='9' y='9' width='6' height='6'/><line x1='9' y1='1' x2='9' y2='4'/><line x1='15' y1='1' x2='15' y2='4'/><line x1='9' y1='20' x2='9' y2='23'/><line x1='15' y1='20' x2='15' y2='23'/><line x1='20' y1='9' x2='23' y2='9'/><line x1='20' y1='14' x2='23' y2='14'/><line x1='1' y1='9' x2='4' y2='9'/><line x1='1' y1='14' x2='4' y2='14'/>\");\n")
         .append("IC.octagon=svg(\"<polygon points='7.86 2 16.14 2 22 7.86 22 16.14 16.14 22 7.86 22 2 16.14 2 7.86 7.86 2'/><line x1='12' y1='8' x2='12' y2='12'/><line x1='12' y1='16' x2='12.01' y2='16'/>\");\n")
         .append("IC.info=svg(\"<circle cx='12' cy='12' r='10'/><line x1='12' y1='16' x2='12' y2='12'/><line x1='12' y1='8' x2='12.01' y2='8'/>\");\n")
         .append("function ic(n){return IC[n]||IC.info}\n")
         .append("function paintIcons(){var all=document.querySelectorAll('[data-ic]');\n")
         .append(" for(var i=0;i<all.length;i++)all[i].innerHTML=ic(all[i].getAttribute('data-ic'));\n")
         .append(" $('vimp').innerHTML=ic('star');$('vmal').innerHTML=ic('warn');$('vall').innerHTML=ic('list');\n")
         .append(" $('pf').innerHTML=ic('sliders');$('pause').innerHTML=ic(S.auto?'pause':'play');$('reset').innerHTML=ic('trash');$('guidebtn').innerHTML=ic('help');\n")
         .append(" $('cpt').innerHTML=ic('copy');$('cpjs').innerHTML=ic('braces');$('dl').innerHTML=ic('download')}\n")
         .append("/* ---- tahap + komponen (chip berikon) ---- */\n")
         .append("function stageOf(e){\n")
         .append(" if(e.kind=='BOOT'||e.kind=='SYS'||e.kind=='EXTRACT')return 'SISTEM';\n")
         .append(" if(e.kind=='CRASH')return 'SISTEM';\n")
         .append(" if(e.port==9527)return 'KONFIG'; if(e.port==9528)return 'CDN';\n")
         .append(" if(e.port==9530)return 'SDK'; if(e.port==5050)return 'LOGIN';\n")
         .append(" if(e.port==5051)return 'GAME'; return 'LOGGER'}\n")
         .append("function compOf(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='BOOT'||k=='SYS'||k=='EXTRACT'||k=='CRASH'||k=='LOGCAT')return {name:'CORE',icon:'cpu'};\n")
         .append(" if(e.port==9527)return {name:'CONFIG',icon:'settings'};\n")
         .append(" if(e.port==9528)return {name:'CDN',icon:'globe'};\n")
         .append(" if(e.port==9530)return {name:'SDK',icon:'card'};\n")
         .append(" if(e.port==5050)return {name:'LOGIN',icon:'shield'};\n")
         .append(" if(e.port==5051)return {name:'GAME',icon:'gamepad'};\n")
         .append(" return {name:'MONITOR',icon:'radio'}}\n")
         .append("function pathOf(e){var p=(''+(e.path||''));var i=p.indexOf('?');return i>=0?p.substring(0,i):p}\n")
         .append("function fmtB(n){n=n||0;return n>=1048576?(n/1048576).toFixed(1)+' MB':n>=1024?Math.round(n/1024)+' KB':n+' B'}\n")
         .append("function durStr(e){if(!e.durMs)return '';return e.durMs>=1000?(e.durMs/1000).toFixed(1)+'s':e.durMs+'ms'}\n")
         .append("/* ---- RAW v13: baris utama = PERSIS protokol (method full-path status bytes dur) ----\n")
         .append("   NOL karangan: tidak ada kalimat naratif, tidak ada terjemahan, tidak ada\n")
         .append("   pemangkasan query string. BOOT/SYS/EXTRACT = pesan server apa adanya;\n")
         .append("   WS frame = WS sid=.. + isi frame mentah di sub-baris recv/send. ---- */\n")
         .append("function stHtml(n){n=n||0;var c='s0';if(n>=500)c='s5';else if(n>=400)c='s4';else if(n>=300)c='s3';else if(n>=200)c='s2';return '<span class=\"'+c+'\">'+n+'</span>'}\n")
         .append("function mainLine(e){\n")
         .append(" var k=e.kind||'HTTP',m=e.method||'',p=e.path||'';\n")
         .append(" if(k=='CRASH')return '<b>CRASH</b> '+esc(p);\n")
         .append(" if(k=='BOOT'||k=='EXTRACT')return '<b>'+esc(k)+'</b> '+esc(p);\n")
         .append(" if(k=='SYS')return '<b>'+esc(m||'SYS')+'</b> '+esc(p);\n")
         .append(" if(k=='LOGCAT')return '<b>LOGCAT</b> '+esc(p);\n")
         .append(" if(m=='WS'){\n")
         .append("  var t='<b>WS</b> '+esc(p);\n")
         .append("  if(p.indexOf('ping->pong')>=0||p.indexOf('close')>=0)return t;\n")
         .append("  return t+' '+stHtml(e.status||0)}\n")
         .append(" var t='<b>'+esc(m||'GET')+'</b> '+esc(p)+' '+stHtml(e.status||0);\n")
         .append(" if((e.respLen||0)>0)t+=' <span class=dim>· '+fmtB(e.respLen)+'</span>';\n")
         .append(" if(e.durMs)t+=' <span class=dim>· '+durStr(e)+'</span>';\n")
         .append(" return t}\n")
         .append("/* ---- RAW v13: isi mentah apa adanya — headers / request / response / recv / send / data / error ----\n")
         .append("   TANPA pemotongan 200/160 karakter (dulu data disembunyikan di balik potongan);\n")
         .append("   satu-satunya batas = batas tangkap preview server (4 KB teks / 2 KB file statis).\n")
         .append("   Body panjang menggulir (max-height). ---- */\n")
         .append("function rowHtml(cls,icn,label,val){\n")
         .append(" return '<div class=\"sub '+cls+'\">'+ic(icn)+'<span><span class=lbl>'+label+'</span> '+val+'</span></div>'}\n")
         .append("function subHtml(e){\n")
         .append(" var k=e.kind||'HTTP',m=e.method||'',parts=[];\n")
         .append(" if(k=='HTTP'||(k=='WS'&&m!='WS')){\n")
         .append("  if(e.headers)parts.push(rowHtml('s-det','corner','headers:',esc(e.headers)));\n")
         .append("  if(e.reqPreview)parts.push(rowHtml('s-req','up','request:',esc(e.reqPreview)));\n")
         .append("  else if((e.reqLen||0)>0)parts.push(rowHtml('s-req','up','request:','('+fmtB(e.reqLen)+' binary)'));\n")
         .append("  if(e.respPreview)parts.push(rowHtml('s-res','down','response:',esc(e.respPreview)));\n")
         .append("  else if((e.respLen||0)>0)parts.push(rowHtml('s-res','down','response:','('+fmtB(e.respLen)+' binary)'))}\n")
         .append(" if(k=='WS'&&m=='WS'){\n")
         .append("  if(e.reqPreview)parts.push(rowHtml('s-req','up','recv:','('+fmtB(e.reqLen||0)+') '+esc(e.reqPreview)));\n")
         .append("  else if((e.reqLen||0)>0)parts.push(rowHtml('s-req','up','recv:','('+fmtB(e.reqLen)+' binary)'));\n")
         .append("  if(e.respPreview)parts.push(rowHtml('s-res','down','send:','('+fmtB(e.respLen||0)+') '+esc(e.respPreview)))}\n")
         .append(" if(e.error)parts.push(rowHtml('s-ext','x','error:',esc(''+e.error)));\n")
         .append(" if(e.extra)parts.push(rowHtml('s-ext','tag','data:',esc(e.extra)));\n")
         .append(" if(parts.length==0)return '';\n")
         .append(" return '<div class=dtl>'+parts.join('')+'</div>'}\n")
         .append("/* ---- level ala server: INFO/DEBUG/WARN/ERROR (chip berikon) ---- */\n")
         .append("function isProblem(e){\n")
         .append(" if((e.kind||'')=='SYS'&&(e.method||'')=='OVERLAY')return false;\n")
         .append(" if((e.kind||'')=='CRASH')return true;\n")
         .append(" if((e.error||'').length)return true;\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='SYS'||k=='BOOT'||k=='EXTRACT'){var t=(''+(e.path||'')).toLowerCase();return /gagal|failed|ditolak|rejected|rusak|broken/.test(t)}\n")
         .append(" if(k=='HTTP'||k=='WS'){\n")
         .append("  if((e.status||0)>=400)return true;\n")
         .append("  if((e.respPreview||'').indexOf('\\\"result\\\":false')>=0)return true}\n")
         .append(" return false}\n")
         .append("function levelOf(e){\n")
         .append(" var k=e.kind||'',m=e.method||'',p=e.path||'';\n")
         .append(" if(k=='CRASH')return {word:'ERROR',cls:'r',icon:'x'};\n")
         .append(" if(k=='LOGCAT')return {word:'DEBUG',cls:'d',icon:'search'};\n")
         .append(" if(k=='SYS'&&m=='OVERLAY')return {word:'INFO',cls:'g',icon:'check'};\n")
         .append(" if(isProblem(e))return (e.status||0)>=500?{word:'ERROR',cls:'r',icon:'x'}:{word:'WARN',cls:'w',icon:'warn'};\n")
         .append(" if((e.status||0)>=300)return {word:'WARN',cls:'w',icon:'warn'};\n")
         .append(" if(p.indexOf('ping->pong')>=0)return {word:'DEBUG',cls:'d',icon:'search'};\n")
         .append(" if(k=='HTTP'||k=='WS'){\n")
         .append("  if(e.port==9527&&p.indexOf('com_db_local')>=0)return {word:'DEBUG',cls:'d',icon:'search'};\n")
         .append("  if(e.port==9528&&(p.indexOf('/resource/')>=0||/\\.js($|\\?)/.test(p)||p.indexOf('manifest.json')>=0))return {word:'DEBUG',cls:'d',icon:'search'};\n")
         .append("  return {word:'INFO',cls:'g',icon:'check'}}\n")
         .append(" return {word:'INFO',cls:'g',icon:'check'}}\n")
         .append("function isImportant(e){\n")
         .append(" var k=e.kind||'',p=e.path||'';\n")
         .append(" if(k=='CRASH')return true;\n")
         .append(" if(k=='BOOT'||k=='SYS'||k=='EXTRACT'||k=='LOGCAT')return true;\n")
         .append(" if(e.port==9527||e.port==9530)return true;\n")
         .append(" if(e.port==9528){return p.indexOf('base.version')>=0||p.indexOf('resource.version')>=0||p.indexOf('clientversion')>=0||p.indexOf('upgrade.json')>=0||p.indexOf('size.json')>=0||p.indexOf('all.zip')>=0||p.indexOf('index-native')>=0||p.indexOf('serversetting')>=0}\n")
         .append(" if(e.port==5050){if(p.indexOf('ping->pong')>=0)return false;return true}\n")
         .append(" if(e.port==5051)return true;\n")
         .append(" return false}\n")
         .append("/* ---- baris mentah satu entri (salin log + ringkasan footer) ---- */\n")
         .append("function plainLine(e){\n")
         .append(" var k=e.kind||'HTTP',m=e.method||'',p=e.path||'';\n")
         .append(" if(k=='CRASH')return 'CRASH '+p;\n")
         .append(" if(k=='BOOT'||k=='EXTRACT'||k=='LOGCAT')return k+' '+p;\n")
         .append(" if(k=='SYS')return (m||'SYS')+' '+p;\n")
         .append(" if(m=='WS')return 'WS '+p+((p.indexOf('ping->pong')>=0||p.indexOf('close')>=0)?'':' '+(e.status||0));\n")
         .append(" return (m||'GET')+' '+p+' '+(e.status||0)+((e.respLen||0)>0?' '+fmtB(e.respLen):'')+(e.durMs?' '+durStr(e):'')}\n")
         .append("function important(e){\n")
         .append(" var k=e.kind||'',p=e.path||'';\n")
         .append(" if(k=='CRASH')return 'CRASH '+p;\n")
         .append(" if((e.status||0)>=400)return plainLine(e);\n")
         .append(" if(e.port==9530&&p.indexOf('registerVisitor')>=0)return plainLine(e);\n")
         .append(" if(e.port==5050&&p.indexOf('loginchecknative')>=0)return plainLine(e);\n")
         .append(" if(e.port==5051&&(''+(e.reqPreview||'')+(e.extra||'')+(e.respPreview||'')).indexOf('enterGame')>=0)return plainLine(e)+' enterGame';\n")
         .append(" if(e.port==9528&&p.indexOf('index-native')>=0)return plainLine(e);\n")
         .append(" return null}\n")
         .append("function lnHtml(e){\n")
         .append(" var lv=levelOf(e),cp=compOf(e);\n")
         .append(" var evc=lv.cls=='r'?' r':lv.cls=='w'?' w':lv.cls=='d'?' d':'';\n")
         .append(" return '<div class=\"ln '+lv.cls+'\" data-seq=\"'+e.seq+'\">'\n")
         .append("  +'<div class=rh>'\n")
         .append("  +'<span class=\"lv l-'+lv.cls+'\">'+ic(lv.icon)+lv.word+'</span>'\n")
         .append("  +'<span class=\"cp c-'+cp.name+'\">'+ic(cp.icon)+cp.name+'</span>'\n")
         .append("  +'<span class=rmeta><span class=sq>#'+e.seq+'</span><span>'+esc((e.time||'').substring(11))+'</span></span>'\n")
         .append("  +'</div>'\n")
         .append("  +'<div class=\"ev'+evc+'\">'+mainLine(e)+'</div>'\n")
         .append("  +subHtml(e)\n")
         .append("  +'</div>'}\n")
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
         .append("function applyFilter(){var rs=$('tb').children;\n")
         .append(" for(var i=0;i<rs.length;i++){var e=ENT[rs[i].getAttribute('data-seq')];\n")
         .append("  rs[i].style.display=(e&&!pass(e))?'none':''}}\n")
         .append("function refreshStatus(){var n=0;for(var k in ENT)if(isProblem(ENT[k]))n++;\n")
         .append(" var b=$('stbadge');if(!b)return;\n")
         .append(" if(n==0){b.innerHTML=ic('check')+' NORMAL';b.className='ok'}\n")
         .append(" else{b.innerHTML=ic('x')+' '+n+' MASALAH';b.className='bad'}}\n")
         .append("function renderCrash(){fetch('/crash').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var w=$('crashbn');if(!j||!j.latest){w.style.display='none';w.innerHTML='';return}\n")
         .append(" if(j.latest.name==CRASHLATEST&&w.style.display=='block')return;\n")
         .append(" CRASHLATEST=j.latest.name;var t=j.latest.text||'';\n")
         .append(" w.innerHTML='<div class=ch>'+ic('octagon')+' CRASH — '+esc(j.latest.name)+' ('+Math.round(j.latest.bytes/1024)+' KB)</div><div class=cd>crash Java di dalam proses game (termasuk kode SDK/game asli) — terekam CrashHook · isi file mentah di bawah · tombol kanan = salin trace.</div><pre>'+esc(t)+'</pre><button id=cpcrash class=ib title=\"Salin trace crash\">'+ic('copy')+'</button>';\n")
         .append(" w.style.display='block';\n")
         .append(" $('cpcrash').onclick=function(){copyText(t)}}).catch(function(){})}\n")
         .append("function append(list){var tb=$('tb'),sc=$('stream');\n")
         .append(" var pin=sc.scrollTop+sc.clientHeight>=sc.scrollHeight-60;\n")
         .append(" var html='',im=null;\n")
         .append(" for(var i=0;i<list.length;i++){var e=list[i];ENT[e.seq]=e;\n")
         .append("  html+=lnHtml(e);var x=important(e);if(x)im=x}\n")
         .append(" if(html)tb.insertAdjacentHTML('beforeend',html);\n")
         .append(" if(im)LASTSUM=im;\n")
         .append(" var rs=tb.children;var over=rs.length-MAXC;\n")
         .append(" for(var j2=0;j2<over;j2++)tb.removeChild(rs[0]);\n")
         .append(" applyFilter();refreshStatus();\n")
         .append(" if(LASTSUM)$('sum').innerHTML='<b>terakhir penting:</b> '+LASTSUM;\n")
         .append(" if(pin)sc.scrollTop=sc.scrollHeight}\n")
         .append("function visible(){var out=[];var rs=$('tb').children;\n")
         .append(" for(var i=0;i<rs.length;i++){if(rs[i].style.display=='none')continue;\n")
         .append("  var e=ENT[rs[i].getAttribute('data-seq')];if(e)out.push(e)}\n")
         .append(" return out}\n")
         .append("/* ---- salin log = TEKS MENTAH PERSIS PROTOKOL (RAW v13) ---- */\n")
         .append("function copyTable(){var v=visible();var out=[];\n")
         .append(" for(var i=0;i<v.length;i++){var e=v[i];\n")
         .append("  var line=(e.time||'')+' #'+e.seq+' '+e.port+' '+plainLine(e);\n")
         .append("  out.push(line);\n")
         .append("  if((e.kind||'HTTP')=='HTTP'||((e.kind||'')=='WS'&&(e.method||'')!='WS')){\n")
         .append("   if(e.headers)out.push('    headers: '+e.headers);\n")
         .append("   if(e.reqPreview)out.push('    request: '+e.reqPreview);\n")
         .append("   else if((e.reqLen||0)>0)out.push('    request: ('+fmtB(e.reqLen)+' binary)');\n")
         .append("   if(e.respPreview)out.push('    response: '+e.respPreview);\n")
         .append("   else if((e.respLen||0)>0)out.push('    response: ('+fmtB(e.respLen)+' binary)')}\n")
         .append("  if((e.kind||'')=='WS'&&(e.method||'')=='WS'){\n")
         .append("   if(e.reqPreview)out.push('    recv: ('+fmtB(e.reqLen||0)+') '+e.reqPreview);\n")
         .append("   if(e.respPreview)out.push('    send: ('+fmtB(e.respLen||0)+') '+e.respPreview)}\n")
         .append("  if(e.extra)out.push('    data: '+e.extra);\n")
         .append("  if(e.error)out.push('    error: '+e.error)}\n")
         .append(" copyText(out.join('\\n'))}\n")
         .append("function copyJSON(){copyText(JSON.stringify(visible(),null,1))}\n")
         .append("function copyText(t){\n")
         .append(" if(navigator.clipboard&&navigator.clipboard.writeText){\n")
         .append("  navigator.clipboard.writeText(t).then(function(){toast('tersalin '+t.length+' karakter')},function(){legacyCopy(t)});\n")
         .append(" }else legacyCopy(t)}\n")
         .append("function legacyCopy(t){\n")
         .append(" var ta=document.createElement('textarea');ta.value=t;\n")
         .append(" ta.style.position='fixed';ta.style.left='-9999px';ta.style.top='0';\n")
         .append(" document.body.appendChild(ta);ta.focus();ta.select();\n")
         .append(" var ok=false;try{ok=document.execCommand('copy')}catch(x){}\n")
         .append(" document.body.removeChild(ta);\n")
         .append(" if(ok)toast('tersalin '+t.length+' karakter');\n")
         .append(" else toast('salin gagal — izin clipboard ditolak, coba tombol Salin JSON',true)}\n")
         .append("var tt=null;\n")
         .append("function toast(m,err){var el=$('toast');el.textContent=m;el.className=err?'err':'';el.style.display='block';clearTimeout(tt);tt=setTimeout(function(){el.style.display='none'},2600)}\n")
         .append("function sync(){S.st=$('st').value;S.q=$('q').value.toLowerCase();S.limit=parseInt($('limit').value);S.port=parseInt($('port').value)}\n")
         .append("function reset(){$('tb').innerHTML='';ENT={};S.since=0;LASTSUM='';$('notices').innerHTML='';refreshStatus()}\n")
         .append("function setView(v){S.view=v;\n")
         .append(" $('vimp').className='ib'+(v=='penting'?' on':'');\n")
         .append(" $('vmal').className='ib'+(v=='masalah'?' on':'');\n")
         .append(" $('vall').className='ib'+(v=='all'?' on':'');\n")
         .append(" applyFilter();if(v=='masalah')$('stream').scrollTop=0}\n")
         .append("$('vimp').onclick=function(){setView('penting')};\n")
         .append("$('vmal').onclick=function(){setView('masalah')};\n")
         .append("$('vall').onclick=function(){setView('all')};\n")
         .append("$('pf').onclick=function(){var c=$('ctl');c.style.display=(c.style.display=='block'?'none':'block')};\n")
         .append("['limit','port'].forEach(function(id){$(id).onchange=function(){sync();reset()}});\n")
         .append("$('st').onchange=function(){sync();applyFilter()};\n")
         .append("$('stage').onchange=function(){S.stage=$('stage').value;applyFilter()};\n")
         .append("$('q').oninput=function(){S.q=$('q').value.toLowerCase();applyFilter()};\n")
         .append("$('pause').onclick=function(){S.auto=!S.auto;\n")
         .append(" $('pause').innerHTML=ic(S.auto?'pause':'play');\n")
         .append(" $('cursor').style.display=S.auto?'inline-block':'none';\n")
         .append(" $('pausemark').textContent=S.auto?'':'jeda — pembaruan otomatis berhenti'};\n")
         .append("$('guidebtn').onclick=function(){var g=$('guide');g.style.display=(g.style.display=='block'?'none':'block')};\n")
         .append("$('reset').onclick=reset;\n")
         .append("$('cpt').onclick=copyTable;\n")
         .append("$('cpjs').onclick=copyJSON;\n")
         .append("$('dl').onclick=function(){var a=document.createElement('a');a.href='/download?latest=1';a.download='';document.body.appendChild(a);a.click();document.body.removeChild(a)};\n")
         .append("function note(c2,icon,txt){var d=document.createElement('div');d.className='nt '+c2;d.innerHTML=ic(icon)+'<span>'+txt+'</span>';$('notices').appendChild(d)}\n")
         .append("function url(){return '/logs?mode=all&limit='+S.limit+'&since='+S.since}\n")
         .append("function uptimeStr(ms){var s2=Math.floor(ms/1000);return Math.floor(s2/3600)+'h'+Math.floor(s2%3600/60)+'m'+(s2%60)+'s'}\n")
         .append("function refreshStats(){fetch('/stats').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var ids=[['9527','settings','KONFIG','konfigurasi bin (setting_BS)'],['9528','globe','CDN','CDN game + login'],['5050','shield','LOGIN','socket.io login'],['5051','gamepad','GAME','server game'],['9530','card','SDK','API SDK'],['4000','radio','LOG','logger']];\n")
         .append(" var h='<span class=\"schip up\">'+ic('server')+'LISTEN</span>';\n")
         .append(" for(var i=0;i<ids.length;i++){var cc=j.ports[ids[i][0]];\n")
         .append("  h+='<span class=\"schip '+(cc&&cc.requests>0?'up':'dw')+'\" title=\"'+ids[i][3]+'\">'+ic(ids[i][1])+ids[i][2]+' <b>'+ids[i][0]+'</b></span>'}\n")
         .append(" h+='<span class=schip>'+ic('clock')+'UPTIME <b>'+uptimeStr(j.uptimeMs)+'</b></span>';\n")
         .append(" h+='<span class=schip>'+ic('hash')+'SEQ <b>'+j.seq+'</b></span>';\n")
         .append(" if(j.disk&&j.disk.enabled)h+='<span class=schip>'+ic('archive')+'ARSIP <b>'+j.disk.file+'</b> · '+(j.disk.bytes>=1048576?(j.disk.bytes/1048576).toFixed(1)+'MB':Math.round(j.disk.bytes/1024)+'KB')+' · '+j.disk.lines+' baris</span>';\n")
         .append(" if(j.overlay&&j.overlay.checked)h+=j.overlay.granted?'<span class=schip>'+ic('layers')+'OVERLAY <b>ON</b></span>':'<span class=schip>'+ic('layers')+'OVERLAY <b>OFF (normal)</b></span>';\n")
         .append(" h+='<span class=schip>'+ic('activity')+'STATUS <b id=stbadge class=ok>…</b></span>';\n")
         .append(" $('stchips').innerHTML=h;refreshStatus();renderCrash()}).catch(function(){})}\n")
         .append("function poll(){\n")
         .append(" if(!S.auto){setTimeout(poll,500);return}\n")
         .append(" fetch(url()).then(function(r){return r.json()}).then(function(j){\n")
         .append("  if(j.gap&&j.entries.length)note('nt-w','warn','GAP: sebagian entri buffer terbuang — arsip disk tetap utuh (unduh di kiri bawah)');\n")
         .append("  if(j.entries.length){append(j.entries);S.since=j.seqNewest}\n")
         .append("  var tot=(j.droppedGlobal||0)+(j.droppedPort||0);\n")
         .append("  if(tot>0&&tot!==lastDrop){lastDrop=tot;note('nt-e','x','buffer dropped '+tot+' — arsip disk tetap utuh')}\n")
         .append("  pollN++;if(pollN%3==0)refreshStats();\n")
         .append("  setTimeout(poll,1200);\n")
         .append(" }).catch(function(){setTimeout(poll,3000)})}\n")
         .append("document.title='DB LOCAL SERVER :'+location.port;\n")
         .append("sync();setView('all');paintIcons();refreshStats();poll();\n")
         .append("setTimeout(refreshStats,800);\n");
        return s.toString();
    }
}
