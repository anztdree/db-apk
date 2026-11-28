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
 * CONSOLE v28 — FORMAT v5 (Bagian 81, oke owner; Bagian 83: NOL baris buatan).
 * Evolusi console v13..v18 (semua arahan lama tetap): raw server log, URL
 * penuh nol pangkas, rincian LANGSUNG tampil tanpa klik, NOL referensi
 * internal, tombol tetap 4 (cari, filter error, salin, jeda).
 * FORMAT v5 (kata owner: "GAME INI HANYA TAU RUNNING KALAU ERROR YA DIA
 * PASTI TERIAK LAHHH" + "BOOT AJA 3X TEKS DASAR GK EFEKTIF"):
 *  1. R15 SATU ALUR NOL TAB — semua entri satu tumpukan urutan NDJSON persis
 *     (termasuk pasangan seq yang tersimpan tak berurutan — ikut dump).
 *  2. Identitas server DI BARIS KEJADIANNYA: emoji peta + nomor port.
 *     4000 → 🚀 BOOT · 🌐 BIND · 🧩 EXTRACT · 🐞 CrashHook · 📱 overlay ·
 *     📜 CLIENT; 9527 → 🧾; 9528 → 📥 aset(zip/mp3/png) · 📄 html/manifest ·
 *     🖼️ resource properties · 🔑 socket.io polling · 🌐 WS/upgrade · ⏳
 *     timeout; 5050 → 🔑; 5051 → 🕹️; 9530 → 📲; WATCH N1 → ⏳ · N2 → 🛑.
 *  3. R16 TEKS = KATA SERVER NOL PARAFRASE: baris utama =
 *     "METHOD URL → status · durasi · ukuran" (ukuran desimal: 19385098 B →
 *     "19.4 MB"); BOOT/SYS/EXTRACT/WATCH = kalimat server apa adanya.
 *  4. R18 BOOT = 1 BARIS: kalimat "up" milik server sendiri ("mini-server
 *     up; logger port 4000; NDJSON archive: …") = baris utama; entri boot
 *     lain (starter/exit/CrashHook/overlay/DB/seed/BIND) = ↳ detail apa
 *     adanya dengan waktu + emoji + port masing-masing.
 *  5. R17 DETAIL APA ADANYA POTONGAN NOL: extra verbatim (separator "|"
 *     milik server dipertahankan), DECODED-XOR penuh sebagai blok, ACK 43x
 *     frame penuh, preview yang membawa fakta baru tampil (upgrade
 *     *.version/*.json, resource properties, getNotice, registerVisitor
 *     dengan token dimask 6+4); body besar tanpa fakta baru (html, manifest,
 *     all.zip, init.json) tidak dicetak — ukurannya sudah di baris; preview
 *     404 tidak diulang (= barisnya); payload ulangan identik tidak dicetak
 *     ulang (setting bin/com_db_local/clientversion ?rnd/?v berbeda).
 *  6. Tag ERROR merah HANYA dari field `level` LoggerCore sendiri (v22) —
 *     baris normal tanpa tag (access log alami).
 *  7. N3/N4/N5/R19 (82.2) DICABUT Bagian 83 — nol baris ⚠️ ANALYSIS: log =
 *     entri NDJSON apa adanya; missing field = diperbaiki di server, bukan
 *     dilaporkan di log; main.min.js yang menjadi hakim (R24).
 * Routing:
 *   GET /                    → console HTML live (poll incremental)
 *   GET /stats               → JSON counter per port + dropped + status disk NDJSON
 *   GET /health              → JSON status bind + ekstraksi + disk
 *   GET /logs?mode=all|port&port=P&limit=N&since=SEQ
 *                            → mode ALL/PORT, kronologis naik, INCREMENTAL via since
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

    private String dashboardHtml() {
        StringBuilder b = new StringBuilder(36000);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>DB LOCAL SERVER — server log</title>\n")
         .append("<style>\n")
         .append("*{box-sizing:border-box}\n")
         .append("html,body{height:100%}\n")
         .append("body{font-family:'Inter','Segoe UI',system-ui,-apple-system,sans-serif;background:#04070c;color:#dce6f1;margin:0;font-size:13px;line-height:1.6;display:flex;flex-direction:column;user-select:text;-webkit-user-select:text}\n")
         .append("body::before{content:'';position:fixed;inset:0;pointer-events:none;z-index:0;background:radial-gradient(640px 320px at 10% -8%,rgba(61,220,132,.07),transparent 60%),radial-gradient(700px 340px at 96% -10%,rgba(79,195,221,.05),transparent 60%)}\n")
         .append(".ic{width:14px;height:14px;flex:none;display:inline-block;vertical-align:-2.5px}\n")
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
         .append(".ib.erron{border-color:#5c2630;color:#ff8484;background:#150b0e;box-shadow:0 0 12px rgba(255,93,93,.22)}\n")
         .append("#qwrap{display:inline-flex;align-items:center;gap:7px;background:#0b1420;border:1px solid #1c2a3a;border-radius:9px;padding:6px 11px;color:#7e919f;transition:border-color .18s}\n")
         .append("#qwrap:focus-within{border-color:#1f5c37}\n")
         .append("#qwrap .ic{width:14px;height:14px;color:#4fc3dd}\n")
         .append("input{background:transparent;color:#dce6f1;border:none;outline:none;font-family:inherit;font-size:12px;min-width:110px;max-width:220px}\n")
         .append("input::placeholder{color:#44586a}\n")
         .append("#crashbn{position:relative;z-index:1;display:none;padding:11px 18px;border-bottom:1px solid #4a262c;background:#130a0c}\n")
         .append("#crashbn .ch{color:#ff5d5d;font-weight:800;font-size:12px;display:inline-flex;align-items:center;gap:7px}\n")
         .append("#crashbn .cd{color:#9fb2c4;font-size:11.5px;margin-top:3px}\n")
         .append("#crashbn pre{margin:7px 0 4px;padding:10px;background:#070b11;border:1px solid #2a1a1e;border-radius:8px;max-height:180px;overflow:auto;white-space:pre-wrap;word-break:break-word;font-size:11px;color:#d8c2bf}\n")
         .append("#notices{position:relative;z-index:1;padding:0 18px}\n")
         .append(".nt{font-size:11.5px;padding:3px 0;display:flex;align-items:baseline;gap:7px}\n")
         .append(".nt .ic{width:13px;height:13px;position:relative;top:1.5px}\n")
         .append(".nt-w{color:#ffb454}.nt-e{color:#ff5d5d}\n")
         .append("#stream{position:relative;z-index:1;flex:1;overflow-y:auto;padding:14px 18px 18px}\n")
         .append("#stream::-webkit-scrollbar{width:10px;height:10px}\n")
         .append("#stream::-webkit-scrollbar-track{background:transparent}\n")
         .append("#stream::-webkit-scrollbar-thumb{background:linear-gradient(180deg,#223143,#16212e);border-radius:6px;border:2px solid #04070c}\n")
         .append("#banner{display:flex;gap:13px;align-items:center;border:1px solid #1f5c37;border-radius:12px;background:linear-gradient(180deg,#0a1410,#081009);box-shadow:0 0 26px rgba(61,220,132,.14);padding:12px 18px;margin:0 0 12px}\n")
         .append("#blogo{width:42px;height:42px;border-radius:11px;display:inline-flex;align-items:center;justify-content:center;color:#3ddc84;background:linear-gradient(145deg,#0b2416,#071409);border:1px solid #1f5c37;flex:none}\n")
         .append("#blogo .ic{width:23px;height:23px;vertical-align:0}\n")
         .append("#banner .b1{font-weight:800;font-size:13.5px;color:#a9efc6;letter-spacing:.4px}\n")
         .append("#banner .bl{color:#63a97f;font-size:11.5px;margin-top:2px;display:flex;align-items:center;gap:7px}\n")
         .append("#banner .bl .ic{width:13px;height:13px;color:#3ddc84}\n")
         .append(".ln{position:relative;background:linear-gradient(180deg,#0a121b,#091017);border:1px solid #131f2b;border-left:3px solid #2a3a4c;border-radius:9px;padding:8px 12px 8px 13px;margin-bottom:8px;animation:fadein .2s ease-out;transition:border-color .15s,box-shadow .15s}\n")
         .append("@keyframes fadein{from{opacity:0;transform:translateY(3px)}}\n")
         .append(".ln:hover{border-color:#223142;box-shadow:0 3px 14px rgba(0,0,0,.35)}\n")
         .append(".ln.g{border-left-color:#3ddc84}\n")
         .append(".ln.r{border-left-color:#ff5d5d;background:linear-gradient(180deg,#150b0e,#100a0c);border-color:#3a2026}\n")
         .append(".ev{color:#dce6f1;font-family:'JetBrains Mono','Roboto Mono',Menlo,Consolas,'Droid Sans Mono',monospace;font-size:12.5px;white-space:pre-wrap;word-break:break-word}\n")
         .append(".ev .tm{color:#55677a;font-weight:600}\n")
         .append(".ev .pt{color:#4fc3dd;font-weight:800}\n")
         .append(".ev .etag{color:#ff5d5d;font-weight:800}\n")
         .append(".ev.r{color:#ffb3b3}\n")
         .append(".dtl{margin-top:6px;display:flex;flex-direction:column;gap:3px;max-height:420px;overflow-y:auto}\n")
         .append(".dtl::-webkit-scrollbar{width:8px;height:8px}\n")
         .append(".dtl::-webkit-scrollbar-thumb{background:#223143;border-radius:5px}\n")
         .append(".dl{font-family:'JetBrains Mono','Roboto Mono',Menlo,Consolas,'Droid Sans Mono',monospace;font-size:11.5px;color:#93a7b9;white-space:pre-wrap;word-break:break-word;border-left:2px solid #1b2836;padding:1px 0 1px 8px;margin-left:2px}\n")
         .append("#cursor{display:inline-block;width:8px;height:14px;background:#3ddc84;vertical-align:-2px;animation:bl 1.1s step-end infinite;box-shadow:0 0 8px rgba(61,220,132,.6)}\n")
         .append("@keyframes bl{50%{opacity:0}}\n")
         .append("#pausemark{color:#64778a;font-style:italic}\n")
         .append("#ftr{position:relative;z-index:2;padding:9px 18px;border-top:1px solid #16222f;background:rgba(8,14,22,.94);display:flex;gap:8px;align-items:center;box-shadow:0 -6px 24px rgba(0,0,0,.4)}\n")
         .append("#sum{color:#64778a;font-size:11.5px;flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}\n")
         .append("#sum b{color:#cfe0ef;font-weight:700}\n")
         .append("@media(max-width:680px){#stream{padding:10px 10px 14px}#hdr{padding:9px 12px}#ftr{padding:8px 12px}#notices{padding:0 12px}#versub{display:none}.ln{padding:7px 8px 7px 10px;border-radius:8px}.ev{font-size:12px}#banner{padding:10px 13px}input{min-width:70px;max-width:150px}}\n")
         .append("</style></head><body>\n")

         .append("<div id=hdr><div id=l1>")
         .append("<span id=logo><span data-ic=flame></span></span><span id=dot></span>")
         .append("<span id=title>DB LOCAL SERVER</span><span id=ver>v19</span><span id=versub>· raw server log · com.db.local</span>")
         .append("<span id=sp></span>")
         .append("<label id=qwrap><span data-ic=search></span><input id=q placeholder='cari…'></label>")
         .append("<button id=errf class=ib title='Filter: tampil warn/error saja'></button>")
         .append("<button id=copy class=ib title='Salin log — tempel ke chat dev'></button>")
         .append("<button id=pause class=ib title='Jeda / lanjut pembaruan otomatis'></button>")
         .append("</div></div>\n")

         .append("<div id=crashbn></div>\n")
         .append("<div id=notices></div>\n")
         .append("<div id=stream>")
         .append("<div id=banner><span id=blogo><span data-ic=flame></span></span><div><div class=b1>DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v19</div><div class=bl><span data-ic=terminal></span>raw server log — http://127.0.0.1:4000 — satu alur · kata server apa adanya · rincian terbuka tanpa klik</div></div></div>\n")
         .append("<div id=tb></div><div id=tail><span id=pausemark></span><span id=cursor> </span></div></div>\n")

         .append("<div id=ftr><span id=sum>…</span></div>\n")

         .append("<script>\n")
         .append(Js())
         .append("</script>\n")
         .append("</body></html>");
        return b.toString();
    }

    /* ================= v28 — FORMATTER FORMAT v5 (Bagian 81 + 83) =================
     * Teks log = kata server apa adanya. Rangkaian fungsi murni (textLines)
     * dipakaiSalin + uji smoke (render dump device = teks-per-teks readme 81.3),
     * DOM memakai helper yang sama. NOL baris karangan. */

    private static String Js() {
        StringBuilder s = new StringBuilder(24000);
        s.append("var S={since:0,q:'',auto:true,err:false};\n")
         .append("var ENT={},MAXC=3000,pollN=0,LASTSUM='',CRASHLATEST='',lastDrop=-1;\n")
         .append("var SEEN={},BBUF=[],BUP=false,BOOTCARD={};\n")
         .append("function $(id){return document.getElementById(id)}\n")
         .append("function esc(x){return (x==null?'':''+x).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         /* ---- REGISTRY ICON SVG (chrome UI — ikon tombol, NOL emoji di tombol) ---- */
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
         .append("IC.warn=svg(\"<path d='M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z'/><line x1='12' y1='9' x2='12' y2='13'/><line x1='12' y1='17' x2='12.01' y2='17'/>\");\n")
         .append("IC.x=svg(\"<circle cx='12' cy='12' r='10'/><line x1='15' y1='9' x2='9' y2='15'/><line x1='9' y1='9' x2='15' y2='15'/>\");\n")
         .append("IC.check=svg(\"<path d='M22 11.08V12a10 10 0 1 1-5.93-9.14'/><polyline points='22 4 12 14.01 9 11.01'/>\");\n")
         .append("IC.search=svg(\"<circle cx='11' cy='11' r='8'/><line x1='21' y1='21' x2='16.65' y2='16.65'/>\");\n")
         .append("IC.pause=svg(\"<rect x='6' y='4' width='4' height='16' rx='1'/><rect x='14' y='4' width='4' height='16' rx='1'/>\");\n")
         .append("IC.play=svg(\"<polygon points='5 3 19 12 5 21 5 3'/>\");\n")
         .append("IC.trash=svg(\"<polyline points='3 6 5 6 21 6'/><path d='M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2'/><line x1='10' y1='11' x2='10' y2='17'/><line x1='14' y1='11' x2='14' y2='17'/>\");\n")
         .append("IC.copy=svg(\"<rect x='9' y='9' width='13' height='13' rx='2' ry='2'/><path d='M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'/>\");\n")
         .append("IC.download=svg(\"<path d='M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4'/><polyline points='7 10 12 15 17 10'/><line x1='12' y1='15' x2='12' y2='3'/>\");\n")
         .append("IC.terminal=svg(\"<polyline points='4 17 10 11 4 5'/><line x1='12' y1='19' x2='20' y2='19'/>\");\n")
         .append("IC.tag=svg(\"<path d='M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.83z'/><line x1='7' y1='7' x2='7.01' y2='7'/>\");\n")
         .append("IC.corner=svg(\"<polyline points='15 10 20 15 15 20'/><path d='M4 4v7a4 4 0 0 0 4 4h12'/>\");\n")
         .append("IC.up=svg(\"<circle cx='12' cy='12' r='10'/><polyline points='16 12 12 8 8 12'/><line x1='12' y1='16' x2='12' y2='8'/>\");\n")
         .append("IC.down=svg(\"<circle cx='12' cy='12' r='10'/><polyline points='8 12 12 16 16 12'/><line x1='12' y1='8' x2='12' y2='16'/>\");\n")
         .append("IC.cpu=svg(\"<rect x='4' y='4' width='16' height='16' rx='2' ry='2'/><rect x='9' y='9' width='6' height='6'/><line x1='9' y1='1' x2='9' y2='4'/><line x1='15' y1='1' x2='15' y2='4'/><line x1='9' y1='20' x2='9' y2='23'/><line x1='15' y1='20' x2='15' y2='23'/><line x1='20' y1='9' x2='23' y2='9'/><line x1='20' y1='14' x2='23' y2='14'/><line x1='1' y1='9' x2='4' y2='9'/><line x1='1' y1='14' x2='4' y2='14'/>\");\n")
         .append("IC.octagon=svg(\"<polygon points='7.86 2 16.14 2 22 7.86 22 16.14 16.14 22 7.86 22 2 16.14 2 7.86 7.86 2'/><line x1='12' y1='8' x2='12' y2='12'/><line x1='12' y1='16' x2='12.01' y2='16'/>\");\n")
         .append("IC.info=svg(\"<circle cx='12' cy='12' r='10'/><line x1='12' y1='16' x2='12' y2='12'/><line x1='12' y1='8' x2='12.01' y2='8'/>\");\n")
         .append("IC.chev=svg(\"<polyline points='6 9 12 15 18 9'/>\");\n")
         .append("function ic(n){return IC[n]||IC.check}\n")
         .append("function paintIcons(){var all=document.querySelectorAll('[data-ic]');\n")
         .append(" for(var i=0;i<all.length;i++)all[i].innerHTML=ic(all[i].getAttribute('data-ic'));\n")
         .append(" $('pause').innerHTML=ic(S.auto?'pause':'play');\n")
         .append(" $('errf').innerHTML=ic('octagon');\n")
         .append(" $('copy').innerHTML=ic('copy')}\n")
         /* ---- emoji peta (identitas server DI BARIS KEJADIANNYA) ---- */
         .append("function emojiOf(e){\n")
         .append(" var k=e.kind||'',m=e.method||'',p=''+(e.path||''),x=''+(e.extra||'');\n")
         .append(" if(k=='WATCH')return m=='N2'?'\\uD83D\\uDED1':'\\u23F3';\n")
         .append(" if(k=='CLIENT')return '\\uD83D\\uDCDC';\n")
         .append(" if(k=='CRASH')return '\\uD83D\\uDC1E';\n")
         .append(" if(e.port==9527)return '\\uD83E\\uDDFE';\n")
         .append(" if(e.port==9530)return '\\uD83D\\uDCF2';\n")
         .append(" if(e.port==5050)return '\\uD83D\\uDD11';\n")
         .append(" if(e.port==5051)return '\\uD83D\\uDD79';\n")
         .append(" if(e.port==4000){\n")
         .append("  if(k=='EXTRACT')return '\\uD83E\\uDDE9';\n")
         .append("  if(m=='CRASHHOOK'||p.indexOf('CrashHook')==0)return '\\uD83D\\uDC1E';\n")
         .append("  if(m=='OVERLAY')return '\\uD83D\\uDCF1';\n")
         .append("  if(m=='BIND')return '\\uD83C\\uDF10';\n")
         .append("  return '\\uD83D\\uDE80'}\n")
         .append(" if(x.indexOf('long-poll timeout')==0)return '\\u23F3';\n")
         .append(" if(k=='WS')return '\\uD83C\\uDF10';\n")
         .append(" if(p.indexOf('/socket.io/')>=0)return '\\uD83D\\uDD11';\n")
         .append(" if(/\\.(zip|mp3|png|jpg|jpeg|gif|webp|mp4)(\\?|$)/.test(p))return '\\uD83D\\uDCE5';\n")
         .append(" if(p.indexOf('/bs/upgrade/')>=0)return '\\uD83C\\uDF10';\n")
         .append(" if(/\\.html?(\\?|$)/.test(p)||p.indexOf('manifest.json')>=0)return '\\uD83D\\uDCC4';\n")
         .append(" if(p.indexOf('/bs/resource/properties/')>=0)return '\\uD83D\\uDDBC\\uFE0F';\n")
         .append(" return '\\uD83D\\uDCE5'}\n")
         /* ---- ukuran desimal (19385098 B → 19.4 MB — pola 81.3) ---- */
         .append("function fmtB(n){n=n||0;if(n>=1e6)return (n/1e6).toFixed(1)+' MB';if(n>=1000)return (n/1000).toFixed(1)+' KB';return n+' B'}\n")
         .append("function maskTok(v){return (''+v).replace(/\\b[0-9a-f]{32}\\b/g,function(m){return m.slice(0,6)+'…'+m.slice(-4)})}\n")
         .append("function flatPreview(p){var f=(''+p).replace(/\\s*\\n\\s*/g,' ').trim();\n")
         .append(" if(f.length>1&&f.charAt(0)=='{'&&f.charAt(f.length-1)=='}')f='{'+f.slice(1,-1).trim()+'}';\n")
         .append(" return f}\n")
         /* ---- baris utama = kata server (R16: NOL parafrase) ---- */
         .append("function mainText(e){\n")
         .append(" var k=e.kind||'',m=e.method||'',p=''+(e.path||'');\n")
         .append(" if(k=='WATCH'||k=='BOOT'||k=='EXTRACT'||k=='LOGCAT')return p;\n")
         .append(" if(k=='SYS')return (m=='BIND'?'BIND ':'')+p;\n")
         .append(" if(k=='CRASH')return 'CRASH '+p;\n")
         .append(" if(k=='CLIENT')return 'CLIENT '+(m||'event')+' — '+p;\n")
         .append(" var t=(m||'GET')+' '+p+' → '+(e.status||0)+' · '+(e.durMs||0)+' ms';\n")
         .append(" var n=e.respLen||0;\n")
         .append(" if(k!='WS'&&n>=0)t+=' · '+fmtB(n);\n")
         .append(" else if(n>0)t+=' · '+fmtB(n);\n")
         .append(" return t}\n")
         .append("function lineHead(e){\n")
         .append(" var t=(''+(e.time||'')).substring(11);\n")
         .append(" var lv=(e.level||'')=='ERROR'?' ERROR':'';\n")
         .append(" return t+' '+emojiOf(e)+lv+' '+e.port+' '}\n")
         /* ---- ↳ detail: apa adanya, potongan NOL, ulangan identik tidak dicetak ---- */
         .append("function normPath(p){return (''+p).replace(/[?&](rnd|v)=[^&]*/g,'')}\n")
         .append("function dupKey(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k!='HTTP'&&k!='WS')return null;\n")
         .append(" if((e.status||0)>=400)return null;\n")
         .append(" var pv=e.respPreview;if(!pv)return null;\n")
         .append(" return e.port+' '+normPath(e.path)+' '+(e.respLen||0)+' '+pv}\n")
         .append("function isDup(e){var k=dupKey(e);if(k==null)return false;\n")
         .append(" if(SEEN[k])return true;SEEN[k]=1;return false}\n")
         .append("function detailsOf(e){\n")
         .append(" var k=e.kind||'',m=e.method||'',p=''+(e.path||'');\n")
         .append(" if(k=='WATCH'||k=='BOOT'||k=='SYS'||k=='EXTRACT'||k=='CRASH')return [];\n")
         .append(" if((e.status||0)>=400)return [];\n")
         .append(" var out=[],extra=maskTok(e.extra||'');\n")
         .append("/* CLIENT v3 — rincian capture langsung tampil: body/response/stack/sid; worker watchdog/script-start/ei */\n")
         .append(" if(k=='CLIENT'){\n")
         .append("  if(e.reqPreview)out.push(['request: '+e.reqPreview]);\n")
         .append("  if(e.respPreview)out.push(['response: '+e.respPreview]);\n")
         .append("  if(e.stack)out.push(['stack: '+e.stack]);\n")
         .append("  if(extra)out.push(['sid: '+extra]);\n")
         .append("  return out}\n")
         /* CLIENT v3 — rincian capture langsung tampil; worker watchdog/script-start/ei */
         .append(" var isSockio=p.indexOf('/socket.io/')>=0;\n")
         .append(" var isProp=e.port==9528&&p.indexOf('/bs/resource/properties/')>=0;\n")
         .append(" var showPrev=/\\/bs\\/upgrade\\/(base\\.version|resource\\.version|upgrade\\.json|size\\.json)(\\?|$)/.test(p)||isProp\n")
         .append("  ||(e.port==9530&&(p.indexOf('/v1/system/getNotice')>=0||p.indexOf('/v1/user/registerVisitor')>=0));\n")
         .append(" if(k=='WS'&&m=='WS'){\n")
         .append("  if(extra)out.push([extra]);\n")
         .append("  else if(e.respPreview)out.push([maskTok(e.respPreview)]);\n")
         .append("  return out}\n")
         .append(" if(isSockio){\n")
         .append("  if(extra)out.push([extra]);\n")
         .append("  return out}\n")
         .append(" if(showPrev&&e.respPreview!=null&&e.respPreview!==''){\n")
         .append("  var pv=maskTok(flatPreview(e.respPreview));\n")
         .append("  if(isProp){out.push([pv]);return out}\n")
         .append("  if(extra)out.push([extra+' · '+pv]);else out.push([pv]);\n")
         .append("  return out}\n")
         .append(" if(extra.indexOf(' | DECODED-XOR: ')>=0){\n")
         .append("  var i=extra.indexOf(' | DECODED-XOR: '),head=extra.substring(0,i),\n")
         .append("      payload=extra.substring(i+' | DECODED-XOR: '.length);\n")
         .append("  if(payload.indexOf('\\n')>=0){\n")
         .append("   if(head)out.push([head]);\n")
         .append("   var blk=['DECODED-XOR:'],ls=payload.split('\\n');\n")
         .append("   for(var j=0;j<ls.length;j++)blk.push('      '+ls[j]);\n")
         .append("   out.push(blk);\n")
         .append("  }else{\n")
         .append("   out.push([(head?head+' | DECODED-XOR: ':'DECODED-XOR: ')+payload])\n")
         .append("  }\n")
         .append("  return out}\n")
         .append(" if(extra)out.push([extra]);\n")
         .append(" return out}\n")
         /* ---- boot = 1 baris "up" milik server + ↳ detail (R18) ---- */
         .append("function isBootish(e){var k=e.kind||'';return k=='BOOT'||k=='SYS'||k=='EXTRACT'}\n")
         .append("function isBootUp(e){return (e.kind||'')=='BOOT'&&(''+(e.path||'')).indexOf('mini-server up')==0}\n")
         .append("function entryLines(e,dup){\n")
         .append(" var out=[lineHead(e)+mainText(e)];\n")
         .append(" if(dup)return out;\n")
         .append(" var d=detailsOf(e);\n")
         .append(" for(var i=0;i<d.length;i++){\n")
         .append("  out.push('↳ '+d[i][0]);\n")
         .append("  for(var j=1;j<d[i].length;j++)out.push(d[i][j]);\n")
         .append(" }\n")
         .append(" return out}\n")
         .append("function textLines(list){\n")
         .append(" var out=[],boot=[],up=false;\n")
         .append(" function flushBoot(){for(var j=0;j<boot.length;j++){\n")
         .append("  var ls=entryLines(boot[j],isDup(boot[j]));\n")
         .append("  for(var q=0;q<ls.length;q++)out.push(ls[q])}\n")
         .append("  boot=[]}\n")
         .append(" for(var i=0;i<list.length;i++){\n")
         .append("  var e=list[i];\n")
         .append("  if(!up&&isBootish(e)){\n")
         .append("   if(isBootUp(e)){\n")
         .append("    out.push(lineHead(e)+mainText(e));\n")
         .append("    for(var j=0;j<boot.length;j++)out.push('↳ '+lineHead(boot[j])+mainText(boot[j]));\n")
         .append("    boot=[];up=true;\n")
         .append("   }else boot.push(e);\n")
         .append("   continue;\n")
         .append("  }\n")
         .append("  if(boot.length)flushBoot();\n")
         .append("  up=true;\n")
         .append("  var ls=entryLines(e,isDup(e));\n")
         .append("  for(var q2=0;q2<ls.length;q2++)out.push(ls[q2]);\n")
         .append(" }\n")
         .append(" if(boot.length)flushBoot();\n")
         .append(" return out}\n")
         /* ---- DOM render — helper yang sama, satu alur (R15) ---- */
         .append("function cardHtml(e,dup){\n")
         .append(" var err=(e.level||'')=='ERROR';\n")
         .append(" var h='<div class=\"ev'+(err?' r':'')+'\"><span class=tm>'+esc((''+(e.time||'')).substring(11))+'</span> <span>'+emojiOf(e)+'</span>';\n")
         .append(" if(err)h+=' <span class=etag>ERROR</span>';\n")
         .append(" h+=' <span class=pt>'+e.port+'</span> '+esc(mainText(e))+'</div>';\n")
         .append(" var d=entryLines(e,dup);\n")
         .append(" if(d.length>1){h+='<div class=dtl>';\n")
         .append("  for(var j=1;j<d.length;j++)h+='<div class=dl>'+esc(d[j])+'</div>';\n")
         .append("  h+='</div>'}\n")
         .append(" return h}\n")
         .append("function emitCard(e,cls){\n")
         .append(" var tb=$('tb');var div=document.createElement('div');\n")
         .append(" div.className='ln'+(cls?' '+cls:((e.level||'')=='ERROR'?' r':''));\n")
         .append(" div.setAttribute('data-seq',e.seq);\n")
         .append(" div.innerHTML=cardHtml(e,isDup(e));\n")
         .append(" tb.appendChild(div)}\n")
         .append("function emitBootCard(up,buf){\n")
         .append(" var tb=$('tb');var div=document.createElement('div');\n")
         .append(" div.className='ln g';div.setAttribute('data-seq',up.seq);\n")
         .append(" var h='<div class=ev><span class=tm>'+esc((''+(up.time||'')).substring(11))+'</span> \\uD83D\\uDE80 <span class=pt>'+up.port+'</span> '+esc(mainText(up))+'</div>';\n")
         .append(" h+='<div class=dtl>';\n")
         .append(" for(var j=0;j<buf.length;j++)h+='<div class=dl>'+esc('↳ '+lineHead(buf[j])+mainText(buf[j]))+'</div>';\n")
         .append(" h+='</div>';\n")
         .append(" div.innerHTML=h;tb.appendChild(div);BOOTCARD[up.seq]=1}\n")
         .append("function append(list){var tb=$('tb'),sc=$('stream');\n")
         .append(" var pin=sc.scrollTop+sc.clientHeight>=sc.scrollHeight-60;\n")
         .append(" for(var i=0;i<list.length;i++){\n")
         .append("  var e=list[i];ENT[e.seq]=e;\n")
         .append("  if(!BUP&&isBootish(e)){\n")
         .append("   if(isBootUp(e)){emitBootCard(e,BBUF);BBUF=[];BUP=true;}\n")
         .append("   else{BBUF.push(e);}\n")
         .append("   continue;\n")
         .append("  }\n")
         .append("  if(BBUF.length){for(var j=0;j<BBUF.length;j++)emitCard(BBUF[j]);BBUF=[];}\n")
         .append("  BUP=true;\n")
         .append("  emitCard(e);\n")
         .append("  var x=plainText(e);if(x)LASTSUM=x;\n")
         .append(" }\n")
         .append(" var rs=tb.children;var over=rs.length-MAXC;\n")
         .append(" for(var j2=0;j2<over;j2++)tb.removeChild(rs[0]);\n")
         .append(" applyFilter();\n")
         .append(" if(LASTSUM)$('sum').innerHTML='<b>terakhir penting:</b> '+esc(LASTSUM);\n")
         .append(" if(pin)sc.scrollTop=sc.scrollHeight}\n")
         .append("function plainText(e){return lineHead(e)+mainText(e)}\n")
         /* ---- filter & salin (teks = PERSIS teksLines — pola 81.3) ---- */
         .append("function isBad(e){\n")
         .append(" if((e.level||'')=='ERROR')return true;\n")
         .append(" if((e.kind||'')=='CRASH')return true;\n")
         .append(" if((e.kind||'')=='CLIENT'){\n")
         .append("  if((e.error||'').length)return true;\n")
         .append("  var m=e.method||'',p=''+(e.path||'');\n")
         .append("  if(m=='longtask'||m.indexOf('warn')>=0)return true;\n")
         .append("  if(m=='watchdog'&&p.indexOf('tidak merespons')>=0)return true;\n")
         .append(" }\n")
         .append(" return false}\n")
         .append("function pass(e){\n")
         .append(" if(S.err&&!isBad(e))return false;\n")
         .append(" if(S.q){var t=((e.method||'')+' '+(e.path||'')+' '+(e.reqPreview||'')+' '+(e.respPreview||'')+' '+(e.extra||'')+' '+(e.error||'')+' '+(e.stack||'')).toLowerCase();if(t.indexOf(S.q)<0)return false}\n")
         .append(" return true}\n")
         .append("function applyFilter(){var rs=$('tb').children;\n")
         .append(" for(var i=0;i<rs.length;i++){var e=ENT[rs[i].getAttribute('data-seq')];\n")
         .append("  rs[i].style.display=(e&&!pass(e))?'none':''}}\n")
         .append("function copyLines(e){\n")
         .append(" var up=e;\n")
         .append(" if(BOOTCARD[e.seq]){var ls=[lineHead(e)+mainText(e)];return ls}\n")
         .append(" return entryLines(e,isDup(e))}\n")
         .append("function renderCrash(){fetch('/crash').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var w=$('crashbn');if(!j||!j.latest){w.style.display='none';w.innerHTML='';return}\n")
         .append(" if(j.latest.name==CRASHLATEST&&w.style.display=='block')return;\n")
         .append(" CRASHLATEST=j.latest.name;var t=j.latest.text||'';\n")
         .append(" w.innerHTML='<div class=ch>'+ic('octagon')+' CRASH — '+esc(j.latest.name)+' ('+Math.round(j.latest.bytes/1024)+' KB)</div><div class=cd>crash Java di proses game — laporan mentah:</div><pre>'+esc(t)+'</pre>';\n")
         .append(" w.style.display='block'}).catch(function(){})}\n")
         .append("function flashSum(msg){$('sum').innerHTML='<b>'+esc(msg)+'</b>';\n")
         .append(" setTimeout(function(){if(LASTSUM)$('sum').innerHTML='<b>terakhir penting:</b> '+esc(LASTSUM)},4000)}\n")
         .append("function fallbackCopy(txt,done){\n")
         .append(" var ta=document.createElement('textarea');ta.value=txt;\n")
         .append(" ta.style.position='fixed';ta.style.opacity='0';document.body.appendChild(ta);\n")
         .append(" ta.focus();ta.select();\n")
         .append(" try{if(document.execCommand('copy'))done();else flashSum('gagal salin — pakai /download arsip')}\n")
         .append(" catch(e){flashSum('gagal salin — pakai /download arsip')}\n")
         .append(" document.body.removeChild(ta)}\n")
         .append("function doCopy(){var rows=$('tb').children,out=[],n=0;\n")
         .append(" for(var i=0;i<rows.length;i++){var e=ENT[rows[i].getAttribute('data-seq')];\n")
         .append("  if(e&&pass(e)){var ls=copyLines(e);for(var j=0;j<ls.length;j++)out.push(ls[j]);n++}}\n")
         .append(" if(!out.length){flashSum('kosong — tidak ada baris yang lolos filter');return}\n")
         .append(" var txt=out.join('\\n');\n")
         .append(" var done=function(){flashSum('tersalin '+n+' baris — tempel ke chat dev')};\n")
         .append(" if(navigator.clipboard&&navigator.clipboard.writeText){\n")
         .append("  navigator.clipboard.writeText(txt).then(done,function(){fallbackCopy(txt,done)})}\n")
         .append(" else fallbackCopy(txt,done)}\n")
         .append("$('q').oninput=function(){S.q=$('q').value.toLowerCase();applyFilter()};\n")
         .append("$('errf').onclick=function(){S.err=!S.err;\n")
         .append(" $('errf').classList.toggle('erron',S.err);applyFilter();\n")
         .append(" flashSum(S.err?'filter ERROR aktif — tampil error saja':'filter ERROR mati — tampil semua')};\n")
         .append("$('copy').onclick=doCopy;\n")
         .append("$('pause').onclick=function(){S.auto=!S.auto;\n")
         .append(" $('pause').innerHTML=ic(S.auto?'pause':'play');\n")
         .append(" $('cursor').style.display=S.auto?'inline-block':'none';\n")
         .append(" $('pausemark').textContent=S.auto?'':'jeda — pembaruan otomatis berhenti'};\n")
         .append("function note(c2,icon,txt){var d=document.createElement('div');d.className='nt '+c2;d.innerHTML=ic(icon)+'<span>'+txt+'</span>';$('notices').appendChild(d)}\n")
         .append("function url(){return '/logs?mode=all&limit=1000&since='+S.since}\n")
         .append("function poll(){\n")
         .append(" if(!S.auto){setTimeout(poll,500);return}\n")
         .append(" fetch(url()).then(function(r){return r.json()}).then(function(j){\n")
         .append("  if(j.gap&&j.entries.length)note('nt-w','warn','GAP: sebagian entri tidak muat di memori — arsip disk tetap utuh');\n")
         .append("  if(j.entries.length){append(j.entries);S.since=j.seqNewest}\n")
         .append("  var tot=(j.droppedGlobal||0)+(j.droppedPort||0);\n")
         .append("  if(tot>0&&tot!==lastDrop){lastDrop=tot;note('nt-e','x','buffer penuh: '+tot+' entri terbuang — arsip disk tetap utuh')}\n")
         .append("  pollN++;if(pollN%3==0)renderCrash();\n")
         .append("  setTimeout(poll,1200);\n")
         .append(" }).catch(function(){setTimeout(poll,3000)})}\n")
         .append("document.title='DB LOCAL SERVER :'+location.port;\n")
         .append("paintIcons();renderCrash();poll();\n");
        return s.toString();
    }
}
