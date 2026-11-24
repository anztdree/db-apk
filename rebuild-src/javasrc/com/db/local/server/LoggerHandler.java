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
 * Bagian 49 — KOREKSI OWNER (kata-kata persis):
 *   "itu yg annda pakai untuk website Masuh emoji bukan icon !! dari tatat
 *    letak warna dan tampilan ataupun isi teks log ya bagi saya masih cacat !!
 *    rombak lahh !! jangan gitu-gitu muluuu!!"
 *   "di bagian log teks itu gak jelas teksnya !! bahasa manusia rinci jelas !!!
 *    emoji jelas !! pemisah jelas !!"
 * CONSOLE v12 — ICON PREMIUM (ROMBAK TOTAL atas v10; supersede Bagian 48):
 *  1. ICON SUNGGUHAN (SVG VECTOR), BUKAN EMOJI — kecantikan UI datang dari
 *     ikon: registry 30+ ikon SVG inline (gaya feather/lucide, stroke 2,
 *     viewBox 24) dijinkan lewat fungsi svg() + elemen [data-ic] + tombol
 *     .ib yang digambar lewat paintIcons(): logo API (flame), tombol view
 *     (star / alert / list), filter (sliders), pause/play, bersihkan (trash),
 *     panduan (help), salin (copy), JSON (braces), unduh (download), chip
 *     status (server + settings/globe/shield/gamepad/card/radio + clock/hash/
 *     archive/layers/activity), chip level (check/search/warn/x), chip
 *     komponen (cpu/settings/globe/card/shield/gamepad/radio), ikon sub-baris
 *     (corner-down-right / arrow-up / arrow-down / tag), banner (flame/
 *     terminal/check), crash (alert-octagon). NOL emoji di chrome UI (header,
 *     tombol, chip, footer, banner) — emoji HANYA di TEKS LOG.
 *  2. TATA LETAK + WARNA BARU (dipercantik total, bukan ditambal):
 *     header kaca blur + garis glow; kartu log (card) bersudut bulat dengan
 *     SPINE warna level di tepi kiri; header baris = chip level berikon +
 *     chip komponen berikon + meta kanan (#seq · jam); blok detail berhierarki
 *     dengan garis kiri; palet diperhalang (emerald/cyan/amber/red di atas
 *     slate pekat); scrollbar bulat; fade-in; toast naik; crash banner kaca
 *     merah.
 *  3. TEKS LOG JELAS (bahasa manusia rinci + emoji jelas + pemisah jelas):
 *     baris utama = emoji pemandu + JUDUL tebal + " — " + kalimat SIAPA-APA-
 *     HASIL; blok detail berLABEL manusia dengan pemisah " · " dan " : ":
 *       Endpoint : POST /sdk/v1/system/init · Status : 200 OK · Kirim :
 *       118 B · Terima : 342 B · Durasi : 12 ms
 *     lalu "Kirim (request) : {…}", "Jawaban (response) : {…}", "Frame
 *     socket : …", "Data protokol : …" — SEMUA tampil INLINE (warisan v9:
 *     modal klik-detail tetap dihapus). Status diberi kata manusia (OK /
 *     ALIH / GAGAL / RUSAK) lewat stLbl().
 *  4. Salin log = teks server PENUH EMOJI (✅INFO [⚙️CORE] … + ↳ detail +
 *     → Kirim + ← Jawaban + · Data protokol) — ditempel di mana pun tetap
 *     seperti log server sungguhan.
 *  5. yang TIDAK berubah (semua arahan lama tetap): overlay = INFO bukan
 *     error (Bagian 39); tombol IKON saja (Bagian 41); port 4000 murni log
 *     monitoring — rute /sdkconfig* + /fix/overlay tetap 404 (Bagian 41/43);
 *     tanpa popup "tips" (Bagian 42); tanpa modal klik (Bagian 47); poll
 *     incremental ?since= 1.2 dtk, MAXC 3000, filter tahap/port/status/
 *     cari/limit, Salin JSON, unduh arsip NDJSON, banner CRASH + salin trace
 *     (Bagian 32).
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
            return "(gagal membaca: " + t + ")";
        }
    }

    /* ================= console ICON PREMIUM (Bagian 49, v12) =================
     * Arahan owner 24 Nov 2026 (kata-kata persis):
     *   "itu yg annda pakai untuk website Masuh emoji bukan icon !!"
     *   "dari tatat letak warna dan tampilan ataupun isi teks log ya bagi
     *    saya masih cacat !! rombak lahh !!"
     *   "di bagian log teks itu gak jelas teksnya !! bahasa manusia rinci
     *    jelas !!! emoji jelas !! pemisah jelas !!"
     * Jawaban v12:
     *  1. ICON = SVG VECTOR sungguhan di SELURUH chrome UI (registry IC +
     *     fungsi svg() + slot [data-ic] + paintIcons()); NOL emoji di header/
     *     tombol/chip/footer/banner — emoji HANYA di teks log (pemandu
     *     peristiwa) sesuai arahan Bagian 48.
     *  2. TATA LETAK BARU: kartu log + spine level, chip level/komponen
     *     berikon, meta kanan, blok detail berhierarki, palet baru.
     *  3. LOG JELAS: label manusia (Endpoint/Status/Kirim/Terima/Durasi) +
     *     pemisah " · " + " : " + status berkata (OK/ALIH/GAGAL/RUSAK).
     *  4. Warisan tetap: inline tanpa klik (47), overlay=INFO (39), port 4000
     *     murni monitoring (41/43), tanpa tips (42), poll 1.2 dtk (32).
     */

    private String dashboardHtml() {
        StringBuilder b = new StringBuilder(40000);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>DB LOCAL SERVER — live console</title>\n")
         .append("<style>\n")
         .append("*{box-sizing:border-box}\n")
         .append("html,body{height:100%}\n")
         .append("body{font-family:'Inter','Segoe UI',system-ui,-apple-system,sans-serif;background:#04070c;color:#dce6f1;margin:0;font-size:13px;line-height:1.6;display:flex;flex-direction:column;user-select:text;-webkit-user-select:text}\n")
         .append("body::before{content:'';position:fixed;inset:0;pointer-events:none;z-index:0;background:radial-gradient(640px 320px at 10% -8%,rgba(61,220,132,.07),transparent 60%),radial-gradient(700px 340px at 96% -10%,rgba(79,195,221,.05),transparent 60%)}\n")
         .append("/* ---- ikon SVG (ICONS v12) ---- */\n")
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
         .append("/* ---- stream kartu log ---- */\n")
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
         .append(".ev{color:#dce6f1;font-family:'JetBrains Mono','Roboto Mono',Menlo,Consolas,'Droid Sans Mono',monospace;font-size:12.5px;white-space:pre-wrap;word-break:break-word}\n")
         .append(".ev b{color:#f2f8fd}\n")
         .append(".ev.w{color:#ffd9a0}.ev.r{color:#ffb3b3}.ev.d{color:#8fa3b5}\n")
         .append(".ev .f{color:#ff8484;font-weight:700}\n")
         .append(".dtl{margin-top:7px;border-left:2px solid #1b2836;padding-left:10px;display:flex;flex-direction:column;gap:4px}\n")
         .append(".ln.r .dtl{border-left-color:#3a2026}\n")
         .append(".sub{display:flex;gap:7px;align-items:baseline;font-family:'JetBrains Mono','Roboto Mono',Menlo,Consolas,'Droid Sans Mono',monospace;font-size:11.5px;line-height:1.6;color:#7e919f}\n")
         .append(".sub .ic{width:12px;height:12px;position:relative;top:2px;color:#4c5f70}\n")
         .append(".sub>span{white-space:pre-wrap;word-break:break-word;flex:1;min-width:0}\n")
         .append(".sub b{color:#b9cbd9;font-weight:700}\n")
         .append(".lbl{color:#647a8d;font-weight:800}\n")
         .append(".s-det{color:#8298ab}\n")
         .append(".s-det b{color:#a9efc6}\n")
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
         .append("@media(max-width:680px){#stream{padding:10px 10px 14px}#hdr{padding:9px 12px}#ctl{padding:10px 12px}#ftr{padding:8px 12px}#notices{padding:0 12px}#versub{display:none}.sq{display:none}.ln{padding:8px 9px 8px 11px;border-radius:8px}.dtl{padding-left:8px}.sub{font-size:11px}.rmeta{font-size:10px}#banner{padding:11px 13px}}\n")
         .append("</style></head><body>\n")

         .append("<div id=hdr><div id=l1>")
         .append("<span id=logo><span data-ic=flame></span></span><span id=dot></span>")
         .append("<span id=title>DB LOCAL SERVER</span><span id=ver>v12</span><span id=versub>· live console · com.db.local</span>")
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
         .append("<div id=banner><span id=blogo><span data-ic=flame></span></span><div><div class=b1>DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v12</div><div class=bl><span data-ic=terminal></span> console log real-time · http://127.0.0.1:4000 — seluruh layanan server lokal mengalir ke sini<br><span data-ic=check></span> server berjalan — log diperbarui otomatis tiap 1,2 dtk · keterangan tiap peristiwa tampil LENGKAP tanpa klik: kalimat peristiwa + Endpoint / Status / Kirim / Terima / Durasi + isi request &amp; response</div></div></div>\n")
         .append("<div id=tb></div><div id=tail><span id=pausemark></span><span id=cursor> </span></div></div>\n")

         .append("<div id=ftr>")
         .append("<button id=cpt class=ib title='Salin log — teks server penuh emoji'></button>")
         .append("<button id=cpjs class=ib title='Salin JSON log'></button>")
         .append("<button id=dl class=ib title='Unduh arsip NDJSON — log lengkap tersimpan di disk'></button>")
         .append("<span id=sum>…</span></div>\n")

         .append("<div id=toast></div>\n")
         .append("<div id=guide>")
         .append("<div class=gsec><span class=gh><span data-ic=terminal></span>ALUR NORMAL BOOT</span><div class=gt>🧩 9527 konfigurasi → 🌐 9528 CDN → 💳 9530 SDK → layar login → 🙋 TAMU / 📝 DAFTAR / 🔐 LOGIN → 🎫 verifikasi 5050 → 🎮 masuk game 5051</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=check></span>LEVEL LOG</span><div class=gt><b>✅ INFO</b> alur normal · <b>🔍 DEBUG</b> traffic halus (aset game, heartbeat, config kosong) · <b>⚠️ WARN</b> perlu perhatian (4xx / redirect / product ditolak) · <b>❌ ERROR</b> gagal / crash</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=list></span>FORMAT TIAP ENTRI — definisi server log yang benar</span><div class=gt>baris utama = 📌 emoji peristiwa + judul tebal + kalimat penjelasan manusia; blok detail = <b>Endpoint</b> · <b>Status</b> · <b>Kirim</b> · <b>Terima</b> · <b>Durasi</b> (pemisah \" · \"); lalu <b>Kirim (request)</b>, <b>Jawaban (response)</b>, <b>Data protokol</b> bila ada — SEMUA tampil penuh inline, bukan disembunyikan di balik klik.</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=shield></span>LAYAR LOGIN SDK</span><div class=gt>🙋 TAMU = langsung main · 📝 DAFTAR = buat akun baru · 🔐 LOGIN = akun terdaftar. Izin overlay OFF itu NORMAL (🪟 game asli tidak memerlukan overlay; SDK tetap berjalan).</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=octagon></span>CRASH</span><div class=gt>💥 banner merah di atas console menampilkan trace lengkap — tombol salin untuk dikirim ke analisis akar.</div></div>\n")
         .append("<div class=gsec><span class=gh><span data-ic=copy></span>TOMBOL BAWAH</span><div class=gt>salin log (teks server penuh emoji) · salin JSON · unduh arsip NDJSON. Filter lanjutan: tombol ikon sliders di kanan atas.</div></div>\n")
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
         .append("/* ---- REGISTRY ICON SVG (v12 — icon sungguhan, bukan emoji) ---- */\n")
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
         .append("/* ---- tahap, komponen, level (ICON v12) ---- */\n")
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
         .append("function ms2(n){var mb=n/1048576;return mb>=1?mb.toFixed(1)+'MB':Math.round(n/1024)+'KB'}\n")
         .append("function fmtB(n){n=n||0;return n>=1048576?(n/1048576).toFixed(1)+' MB':n>=1024?Math.round(n/1024)+' KB':n+' B'}\n")
         .append("function sdkErr(e){var m=/\\\"error\\\":\\{\\\"id\\\":(\\d+),\\\"message\\\":\\\"(.*?)\\\"/.exec(''+(e.respPreview||''));\n")
         .append(" return m?{id:+m[1],msg:m[2]}:{id:0,msg:''}}\n")
         .append("function errWhy(id,msg){\n")
         .append(" var m=''+(msg||'');\n")
         .append(" if(id==20000)return 'server tidak bisa membaca data (Params error)';\n")
         .append(" if(id==20002)return 'tanda tangan (sign) salah — signSecret tidak cocok';\n")
         .append(" if(id==20003){var mm=/LostSomeMustParams:(\\w+)/.exec(m);return 'param wajib kosong: '+(mm?mm[1]:'?')}\n")
         .append(" if(id==30001)return 'PRODUCT DITOLAK — productCode game belum ada di daftar products sdk/profile.json — tambahkan lalu force-stop & buka game ulang';\n")
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
         .append("/* ---- status berkata manusia: OK / ALIH / GAGAL / RUSAK (v12) ---- */\n")
         .append("function stLbl(n){n=n||0;if(n>=500)return n+' RUSAK';if(n>=400)return n+' GAGAL';if(n>=300&&n<400)return n+' ALIH';if(n>=200&&n<300)return n+' OK';return ''+n}\n")
         .append("/* ---- deskripsi VERBOSE ala server asli + EMOJI JELAS (v12) ---- */\n")
         .append("function describe(e){\n")
         .append(" var p=pathOf(e),k=e.kind||'HTTP',m=e.method||'',st=e.status||0,extra=e.extra||'';\n")
         .append(" if(k=='CRASH')return '💥 <b>CRASH terdeteksi</b> — proses game berhenti mendadak, trace lengkap tersimpan di logs/'+esc(p)+(e.error?'<br><span class=f>'+esc(e.error)+'</span>':'');\n")
         .append(" if(k=='BOOT')return '🚀 <b>Server engine berjalan</b> — '+esc(p);\n")
         .append(" if(k=='SYS'&&m=='BIND')return (st==0?'✅ <b>Layanan diaktifkan</b> — ':'❌ <span class=f><b>Layanan GAGAL</b></span> — ')+esc(p);\n")
         .append(" if(k=='SYS'&&m=='OVERLAY')return '🪟 <b>Izin overlay</b> — '+esc(p);\n")
         .append(" if(k=='SYS'){var t=esc(p);if(/gagal|ditolak|rusak/i.test(p))t='❌ <span class=f>'+t+'</span>';else t='✅ '+t;return t}\n")
         .append(" if(k=='EXTRACT')return '📦 <b>Ekstraksi aset game</b> — '+esc(p);\n")
         .append(" if(k=='LOGCAT')return '🩺 <b>Rekaman logcat</b> — diagnostik otomatis sistem (aman diabaikan)';\n")
         .append(" if(k=='WS'){\n")
         .append("  var evw=evName(e.extra);\n")
         .append("  if(evw&&evw.a=='enterGame')return (e.respPreview&&e.respPreview.indexOf('ret\\\":0')>=0)?'🎮 <b>Pemain masuk server game</b> — data karakter lengkap diterima ('+ms2(e.respLen||0)+', ret=0)':'🎮 Pemain masuk server — data '+ms2(e.respLen||0)+' diterima';\n")
         .append("  if(evw)return '⚡ Pesan protokol '+(e.port==5050?'login':'game')+' diteruskan — event <b>'+esc(evw.a)+'</b>'+(evw.ack?' — dijawab server (ACK 43)':' — dikirim client (EVENT 42)');\n")
         .append("  if(e.respPreview=='pong'||extra.indexOf('ping→pong')>=0)return '💓 Heartbeat koneksi — ping→pong normal, sesi tetap hidup';\n")
         .append("  return '🔌 Koneksi WebSocket '+(e.port==5050?'login':'game')+' — '+esc(extra.substring(0,120))}\n")
         .append(" if(e.port==9527){\n")
         .append("  if(p.indexOf('setting_BS')>=0)return '🧩 <b>Konfigurasi game dimuat</b> (setting_BS_Android.bin) — seluruh alamat server dialihkan ke 127.0.0.1 (server lokal)';\n")
         .append("  if(p.indexOf('com_db_local')>=0)return '🧾 Konfigurasi aplikasi dibaca (com_db_local) — isi kosong, persis perilaku server asli';\n")
         .append("  return '🗂️ Konfigurasi dibaca — '+esc(p)}\n")
         .append(" if(e.port==9528){\n")
         .append("  if(p.indexOf('base.version')>=0)return '🌐 <b>Versi dasar diperiksa</b> — terkirim '+esc((e.respPreview||'').trim()||'?')+' (resource lokal dipakai, tanpa pembaruan)';\n")
         .append("  if(p.indexOf('resource.version')>=0)return '🗃️ <b>Versi resource diperiksa</b> — terkirim '+esc((e.respPreview||'').trim()||'?')+' (aset lokal sinkron)';\n")
         .append("  if(p.indexOf('clientversion')>=0)return '🔖 <b>Versi client diperiksa</b> — terkirim '+esc((e.respPreview||'').trim()||'?')+' (popup pengingat tidak muncul — persis server asli)';\n")
         .append("  if(p.indexOf('upgrade.json')>=0)return '📑 <b>Daftar paket pembaruan dibaca</b> — tidak ada pembaruan yang diperlukan';\n")
         .append("  if(p.indexOf('size.json')>=0)return '📏 <b>Ukuran paket pembaruan dibaca</b> — resource sinkron dengan CDN lokal';\n")
         .append("  if(p.indexOf('all.zip')>=0)return '📥 <b>Paket pembaruan diunduh</b> — '+ms2(e.respLen||0)+' dalam '+e.durMs+' ms (CDN lokal)';\n")
         .append("  if(p.indexOf('serversetting')>=0)return '🧭 <b>Alamat server login dibaca</b> (serversetting) — login server diarahkan ke 127.0.0.1:9528';\n")
         .append("  if(p.indexOf('index-native')>=0)return '🖼️ <b>UI game dimuat</b> (index-native.html) — mesin Egret menyiapkan tampilan';\n")
         .append("  if(p.indexOf('manifest.json')>=0)return '📋 Manifest game dibaca — daftar modul game dimuat';\n")
         .append("  if(/\\.js($|\\?)/.test(p))return '📜 Skrip game dikirim — '+esc(p.split('/').pop().substring(0,44))+' ('+ms2(e.respLen||0)+' dalam '+e.durMs+' ms)';\n")
         .append("  if(p.indexOf('/resource/')>=0)return '🖼️ Aset game dikirim — '+esc(p.split('/').pop().substring(0,44))+' ('+ms2(e.respLen||0)+' dalam '+e.durMs+' ms)';\n")
         .append("  return '🌐 CDN lokal melayani — '+esc(p)}\n")
         .append(" if(e.port==9530){\n")
         .append("  var er=sdkErr(e),bad=(e.respPreview||'').indexOf('\\\"result\\\":false')>=0;\n")
         .append("  if(st>=400||bad){var nm=epName(p);return '❌ <span class=f><b>'+nm+' GAGAL</b></span> — '+errWhy(er.id,er.msg)+(er.id?' (error id '+er.id+')':'')}\n")
         .append("  if(p.indexOf('/v1/system/init')>=0)return '🧬 <b>SDK inisialisasi selesai</b> — kontrak init.json penuh diterima, layar login disiapkan sesuai loginTypes';\n")
         .append("  if(p.indexOf('registerVisitor')>=0)return '🙋 <b>Registrasi akun tamu berhasil</b> — uid '+esc(uin(extra))+' diterbitkan, token tersimpan permanen di database lokal';\n")
         .append("  if(p.indexOf('registerUser')>=0)return '📝 <b>Pendaftaran akun baru berhasil</b> — akun '+esc(una(extra))+' dibuat dan tersimpan permanen';\n")
         .append("  if(p.indexOf('loginByName')>=0)return '🔐 <b>Login akun berhasil</b> — akun '+esc(una(extra))+' terautentikasi (email/password valid)';\n")
         .append("  if(p.indexOf('autoLogin')>=0)return '🔑 <b>Login otomatis berhasil</b> — akun '+esc(una(extra))+' terautentikasi memakai sesi tersimpan';\n")
         .append("  if(p.indexOf('getNotice')>=0)return '📢 Pengumuman SDK diperiksa — tidak ada notifikasi untuk pemain';\n")
         .append("  if(p.indexOf('setGameRoleInfo')>=0)return '🕹️ Data karakter pemain dilaporkan ke SDK — profil game tersinkron';\n")
         .append("  if(p.indexOf('ckRegVistor')>=0)return '🎫 <b>Kode migrasi diperiksa</b> — isReged:0, persis perilaku server asli (belum terdaftar)';\n")
         .append("  return '✅ '+epName(p)+' — sukses (kontrak SDK dipenuhi)'}\n")
         .append(" if(e.port==5050){\n")
         .append("  if(p.indexOf('loginchecknative')>=0)return '🎫 <b>Tiket login terverifikasi</b> (loginchecknative) — akses game diberikan, sesi dilanjutkan ke server game';\n")
         .append("  if((e.path||'').indexOf('transport=polling')>=0&&e.respPreview&&e.respPreview.indexOf(':0{')>=0)return '🤝 <b>Handshake server login diterima</b> — sesi socket dibuat (EIO=3, pingInterval 25 dtk)';\n")
         .append("  var ev=evName(e.extra);if(ev)return '⚡ Pesan login diteruskan — event <b>'+esc(ev.a)+'</b>'+(ev.ack?' — dijawab server (ACK 43)':' — dikirim client (EVENT 42)');\n")
         .append("  if(extra.indexOf('ping→pong')>=0)return '💓 Heartbeat server login — ping→pong normal';\n")
         .append("  return '🔌 Server login melayani — '+esc(p.substring(0,60))}\n")
         .append(" if(e.port==5051){\n")
         .append("  var ev=evName(e.extra);\n")
         .append("  if(ev&&ev.a=='enterGame')return (e.respPreview&&e.respPreview.indexOf('ret\\\":0')>=0)?'🎮 <b>Pemain masuk server game</b> — data karakter lengkap diterima ('+ms2(e.respLen||0)+', ret=0)':'🎮 Pemain masuk server — data '+ms2(e.respLen||0)+' diterima';\n")
         .append("  if(ev)return '⚡ Pesan game diteruskan — event <b>'+esc(ev.a)+'</b>'+(ev.ack?' — dijawab server (ACK 43)':' — dikirim client (EVENT 42)');\n")
         .append("  return '🔌 Server game melayani — '+esc(p.substring(0,60))}\n")
         .append(" return '🌐 Permintaan dilayani oleh server lokal — '+esc(p)}\n")
         .append("function uin(x){var m=/uid[=\\\":]+([0-9]+)/.exec(''+x);return m?m[1]:'?'}\n")
         .append("function una(x){var m=/username[=\\\":\\s]+([A-Za-z0-9_]+)/.exec(''+x);return m?m[1]:'tamu'}\n")
         .append("function evName(extra){var s=''+(extra||'');var i=s.indexOf('EVENT: 42');if(i<0)return null;\n")
         .append(" var m=/action\":\"([A-Za-z]+)/.exec(s.substring(i));var ack=s.indexOf('ACK: 43',i)>=0;\n")
         .append(" return m?{a:m[1],ack:ack}:{a:'?',ack:ack}}\n")
         .append("/* ---- level + chip berikon: check INFO / search DEBUG / warn WARN / x ERROR ---- */\n")
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
         .append("function levelOf(e){\n")
         .append(" var k=e.kind||'',m=e.method||'',p=pathOf(e);\n")
         .append(" if(k=='CRASH')return {word:'ERROR',cls:'r',icon:'x'};\n")
         .append(" if(k=='LOGCAT')return {word:'DEBUG',cls:'d',icon:'search'};\n")
         .append(" if(k=='SYS'&&m=='OVERLAY')return {word:'INFO',cls:'g',icon:'check'};\n")
         .append(" if(isProblem(e))return (e.status||0)>=500?{word:'ERROR',cls:'r',icon:'x'}:{word:'WARN',cls:'w',icon:'warn'};\n")
         .append(" if((e.status||0)>=300)return {word:'WARN',cls:'w',icon:'warn'};\n")
         .append(" if((e.extra||'').indexOf('ping→pong')>=0||e.respPreview=='pong')return {word:'DEBUG',cls:'d',icon:'search'};\n")
         .append(" if(k=='HTTP'||k=='WS'){\n")
         .append("  if(e.port==9527&&p.indexOf('com_db_local')>=0)return {word:'DEBUG',cls:'d',icon:'search'};\n")
         .append("  if(e.port==9528&&(p.indexOf('/resource/')>=0||/\\.js($|\\?)/.test(p)||p.indexOf('manifest.json')>=0))return {word:'DEBUG',cls:'d',icon:'search'};\n")
         .append("  return {word:'INFO',cls:'g',icon:'check'}}\n")
         .append(" return {word:'INFO',cls:'g',icon:'check'}}\n")
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
         .append("function plain(h){return (''+h).replace(/<[^>]*>/g,'')}\n")
         .append("function durStr(e){if(!e.durMs)return '';return e.durMs>=1000?(e.durMs/1000).toFixed(1)+'s':e.durMs+'ms'}\n")
         .append("/* ---- detail berLABEL manusia + pemisah jelas (v12) ---- */\n")
         .append("function detailOf(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='HTTP'){return 'Endpoint : '+(e.method||'GET')+' '+esc(pathOf(e))+' · Status : <b>'+stLbl(e.status||0)+'</b> · Kirim : '+fmtB(e.reqLen||0)+' · Terima : '+fmtB(e.respLen||0)+(e.durMs?' · Durasi : '+durStr(e):'')}\n")
         .append(" if(k=='WS'){return 'Endpoint : socket.io port '+e.port+' · Terima : '+fmtB(e.respLen||0)+(e.durMs?' · Durasi : '+durStr(e):'')}\n")
         .append(" if(k=='BOOT')return 'Komponen : LocalServerStarter · Mode : server lokal 127.0.0.1';\n")
         .append(" if(k=='CRASH')return 'Komponen : CrashHook · Tersimpan : folder logs/';\n")
         .append(" return 'Komponen : port '+e.port+' · Jenis : '+k}\n")
         .append("/* ---- SEMUA sub-baris tampil INLINE + berikon + berlabel (tanpa klik) ---- */\n")
         .append("function detailHtml(e){\n")
         .append(" var parts=[];\n")
         .append(" parts.push('<div class=\"sub s-det\">'+ic('corner')+'<span>'+detailOf(e)+'</span></div>');\n")
         .append(" if(e.kind=='HTTP'){\n")
         .append("  if(e.reqPreview)parts.push('<div class=\"sub s-req\">'+ic('up')+'<span><span class=lbl>Kirim (request)</span> <b>·</b> '+esc(trunc(e.reqPreview,200))+'</span></div>');\n")
         .append("  else if((e.reqLen||0)>0)parts.push('<div class=\"sub s-req\">'+ic('up')+'<span><span class=lbl>Kirim (request)</span> <b>·</b> ('+fmtB(e.reqLen)+' biner)</span></div>');\n")
         .append("  if(e.respPreview)parts.push('<div class=\"sub s-res\">'+ic('down')+'<span><span class=lbl>Jawaban (response)</span> <b>·</b> '+esc(trunc(e.respPreview,200))+'</span></div>');\n")
         .append("  else if((e.respLen||0)>0)parts.push('<div class=\"sub s-res\">'+ic('down')+'<span><span class=lbl>Jawaban (response)</span> <b>·</b> ('+fmtB(e.respLen)+' biner)</span></div>')}\n")
         .append(" if(e.kind=='WS'){\n")
         .append("  if(e.respPreview)parts.push('<div class=\"sub s-res\">'+ic('down')+'<span><span class=lbl>Frame socket</span> <b>·</b> '+esc(trunc(e.respPreview,200))+'</span></div>');\n")
         .append("  else if((e.respLen||0)>0)parts.push('<div class=\"sub s-res\">'+ic('down')+'<span><span class=lbl>Frame socket</span> <b>·</b> ('+fmtB(e.respLen)+' biner)</span></div>')}\n")
         .append(" if(e.extra)parts.push('<div class=\"sub s-ext\">'+ic('tag')+'<span><span class=lbl>Data protokol</span> <b>·</b> '+esc(trunc(''+e.extra,160))+'</span></div>');\n")
         .append(" if(parts.length==0)return '';\n")
         .append(" return '<div class=dtl>'+parts.join('')+'</div>'}\n")
         .append("function lnHtml(e){\n")
         .append(" var lv=levelOf(e),cp=compOf(e),d=describe(e),er=e.error||'';\n")
         .append(" var evc=lv.cls=='r'?' r':lv.cls=='w'?' w':lv.cls=='d'?' d':'';\n")
         .append(" return '<div class=\"ln '+lv.cls+'\" data-seq=\"'+e.seq+'\">'\n")
         .append("  +'<div class=rh>'\n")
         .append("  +'<span class=\"lv l-'+lv.cls+'\">'+ic(lv.icon)+lv.word+'</span>'\n")
         .append("  +'<span class=\"cp c-'+cp.name+'\">'+ic(cp.icon)+cp.name+'</span>'\n")
         .append("  +'<span class=rmeta><span class=sq>#'+e.seq+'</span><span>'+esc((e.time||'').substring(11))+'</span></span>'\n")
         .append("  +'</div>'\n")
         .append("  +'<div class=\"ev'+evc+'\">'+d+(er?'<br><span class=f>'+ic('x')+' '+esc(er.length>110?er.substring(0,110)+'\\u2026':er)+'</span>':'')+'</div>'\n")
         .append("  +detailHtml(e)\n")
         .append("  +'</div>'}\n")
         .append("function important(e){var p=pathOf(e);\n")
         .append(" if((e.kind||'')=='CRASH')return '💥 CRASH — '+plain(describe(e));\n")
         .append(" if(e.status>=400)return '❌ GAGAL — '+plain(describe(e));\n")
         .append(" if(e.port==9530&&p.indexOf('registerVisitor')>=0)return '🙋 AKUN — '+plain(describe(e));\n")
         .append(" if(e.port==5050&&p.indexOf('loginchecknative')>=0)return '🎫 LOGIN — '+plain(describe(e));\n")
         .append(" if(e.port==5051&&(e.extra||'').indexOf('enterGame')>=0)return '🎮 GAME — '+plain(describe(e));\n")
         .append(" if(e.port==9528&&p.indexOf('index-native')>=0)return '🖼️ UI — '+plain(describe(e));\n")
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
         .append(" w.innerHTML='<div class=ch>'+ic('octagon')+' CRASH TERTANGKAP — '+esc(j.latest.name)+' ('+Math.round(j.latest.bytes/1024)+' KB)</div><div class=cd>💥 crash Java di dalam proses game (termasuk kode SDK/game asli) — terekam CrashHook · tombol kanan = salin trace untuk analisis akar.</div><pre>'+esc(t)+'</pre><button id=cpcrash class=ib title=\"Salin trace crash\">'+ic('copy')+'</button>';\n")
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
         .append("/* ---- salin log = TEKS SERVER PENUH EMOJI (LVE/CPE), PERSIS ALUR LOG ---- */\n")
         .append("var LVE={INFO:'✅',DEBUG:'🔍',WARN:'⚠️',ERROR:'❌'};\n")
         .append("var CPE={CORE:'⚙️',CONFIG:'🧩',CDN:'🌐',SDK:'💳',LOGIN:'🔐',GAME:'🎮',MONITOR:'📡'};\n")
         .append("function copyTable(){var v=visible();var out=[];\n")
         .append(" for(var i=0;i<v.length;i++){var e=v[i],lv=levelOf(e),cp=compOf(e);\n")
         .append("  out.push((e.time||'')+'  '+LVE[lv.word]+lv.word+'  ['+CPE[cp.name]+cp.name+']  '+plain(describe(e)));\n")
         .append("  out.push('    ↳ '+plain(detailOf(e)));\n")
         .append("  if(e.kind=='HTTP'&&e.reqPreview)out.push('    → Kirim (request) : '+trunc(e.reqPreview,200));\n")
         .append("  if(e.kind=='HTTP'&&e.respPreview)out.push('    ← Jawaban (response) : '+trunc(e.respPreview,200));\n")
         .append("  if(e.kind=='WS'&&e.respPreview)out.push('    ← Frame socket : '+trunc(e.respPreview,200));\n")
         .append("  if(e.extra)out.push('    · Data protokol : '+trunc(''+e.extra,160));\n")
         .append("  if(e.error)out.push('    ❌ ERROR: '+(''+e.error).substring(0,120))}\n")
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
