package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;

/**
 * Listener 4000 (fallback 4001 bila bind gagal) — logger/monitoring v4 (Bagian 19):
 *   GET /                    → dashboard HTML live v4 (TANPA teks penjelasan; tabel
 *                              bersih; klik baris = blok detail terstruktur REQUEST/
 *                              RESPON/EXTRA/ERROR + JSON auto-pretty; SEMUA teks bisa
 *                              disalin: per-baris, per-blok, Salin TSV, Salin JSON;
 *                              fallback clipboard untuk browser HTTP biasa; filter
 *                              mode/port/kind/status/cari/limit; arsip NDJSON diunduh)
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
        } else {
            status = 404;
            h = HttpWorker.baseHeaders("application/json");
            body = HttpWorker.text("{\"error\":\"gunakan / , /stats , /health , /logs?mode=all|port&port=..&limit=N&since=SEQ , /download\"}");
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

    /* ================= dashboard v6 (Bagian 31) =================
     * Keluhan owner 23 Nov 2026 (setelah v5): "log monitoring port 4000 itu
     * bagi saya juga masih terasa sulit di baca dan gak jelas". Jawaban v6:
     *  - STATUS SATU KALIMAT: pill hijau "SEMUA NORMAL" / merah "N MASALAH".
     *  - BANNER MASALAH teratas: kartu merah bahasa manusia + tombol FIX
     *    langsung (30001 -> satu klik "TERIMA PRODUCT INI" dari banner).
     *  - TIGA MODE TAMPILAN: PENTING (default — alur sesi, noise aset CDN
     *    disembunyikan), MASALAH, SEMUA (teknis lengkap = perilaku v5).
     *  - VERDICT KATA bukan angka: OK / GAGAL / CEK / SISTEM.
     *  - Deskripsi error SDK dari envelope (error.id+message) + petunjuk
     *    aksi per kode (30001/20003/20002/...).
     *  - Chip STATUS: DB backend + productCode terakhir (dilihat/diterima).
     *  - FILTER LANJUTAN dilipat (details) — tahap/status/cari/limit/port,
     *    salin TSV/JSON, unduh arsip tetap ada.
     */

    private String dashboardHtml() {
        StringBuilder b = new StringBuilder(32768);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>MONITOR SERVER</title>\n")
         .append("<style>\n")
         .append("*{box-sizing:border-box}\n")
         .append("body{font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#0f141a;color:#dbe4ec;margin:0;padding:10px;font-size:14px;line-height:1.45;user-select:text;-webkit-user-select:text}\n")
         .append("#top{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:8px}\n")
         .append("#title{color:#8fd48f;font-weight:700;font-size:18px}\n")
         .append("#title .ver{color:#7f8c9a;font-size:12px;font-weight:400;margin-left:4px}\n")
         .append("#pill{font-size:12px;font-weight:700;border-radius:14px;padding:3px 12px}\n")
         .append("#pill.g{background:#123a1f;color:#9fe6a8;border:1px solid #2f7a44}\n")
         .append("#pill.r{background:#3a1214;color:#ff9e94;border:1px solid #7a2f34}\n")
         .append("#statsbar{color:#8a97a5;font-size:12px;flex-basis:100%}\n")
         .append(".panel{background:#161d26;border:1px solid #2a333d;border-radius:10px;padding:10px 12px;margin-bottom:10px}\n")
         .append("#chips{display:flex;flex-wrap:wrap;gap:8px}\n")
         .append(".chip{display:inline-flex;align-items:center;gap:6px;background:#1b2530;border:1px solid #2a333d;border-radius:20px;padding:4px 12px;font-size:12px}\n")
         .append(".chip b{font-weight:600}\n")
         .append(".chip.bad{border-color:#7a2f34;color:#ff9e94}\n")
         .append(".chip.ok{border-color:#2f7a44;color:#9fe6a8}\n")
         .append("#sysline{color:#8a97a5;font-size:12px;margin-top:6px}\n")
         .append("#sysline b{color:#c7d1da}\n")
         .append("#banner{margin-bottom:10px}\n")
         .append(".prob{background:#1e0e10;border:1px solid #7a2f34;border-radius:10px;padding:8px 12px;margin-bottom:8px;font-size:13.5px}\n")
         .append(".prob .ph{color:#ff9e94;font-weight:600}\n")
         .append(".prob .pm{color:#c7d1da;margin-top:2px;font-size:12.5px}\n")
         .append(".prob button{margin-top:6px;background:#3a1214;color:#ff9e94;border:1px solid #7a2f34;font-weight:600}\n")
         .append(".prob button:hover{border-color:#a04a50}\n")
         .append("#ctl{display:flex;flex-wrap:wrap;gap:8px;align-items:center}\n")
         .append("label{color:#9fb0c0;font-size:12px}\n")
         .append("select,input,button{background:#1b222b;color:#dbe4ec;border:1px solid #2a333d;border-radius:6px;padding:6px 9px;font-family:inherit;font-size:13px}\n")
         .append("button{cursor:pointer;min-height:32px}button:hover{border-color:#4a5a6a}\n")
         .append(".vb{font-weight:600}\n")
         .append(".vb.on{background:#123a1f;border-color:#2f7a44;color:#9fe6a8}\n")
         .append("#adv summary{color:#9fb0c0;font-size:12px;cursor:pointer}\n")
         .append("#adv .in{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-top:8px}\n")
         .append("#summary{font-size:14px;color:#cfe3cf}\n")
         .append("#summary b{color:#8fd48f}\n")
         .append("#flow{max-height:calc(100vh - 300px);overflow-y:auto;border:1px solid #2a333d;border-radius:10px}\n")
         .append("#flow::-webkit-scrollbar{width:10px}#flow::-webkit-scrollbar-thumb{background:#2a333d;border-radius:5px}\n")
         .append(".card{border-bottom:1px solid #1d2530;padding:8px 12px;display:flex;gap:10px;align-items:flex-start}\n")
         .append(".card:hover{background:#1a222c;cursor:pointer}\n")
         .append(".card .main{flex:1;min-width:0}\n")
         .append(".stage{display:inline-block;font-size:10px;font-weight:700;letter-spacing:.5px;border-radius:4px;padding:1px 7px;margin-right:6px;vertical-align:1px}\n")
         .append(".st-SISTEM{background:#2b3a4a;color:#9fc2e0}.st-KONFIG{background:#3a3a2b;color:#e0d29f}\n")
         .append(".st-CDN{background:#2b4a3a;color:#9fe0c2}.st-SDK{background:#3a2b4a;color:#c29fe0}\n")
         .append(".st-LOGIN{background:#4a3a2b;color:#e0b89f}.st-GAME{background:#24403a;color:#7fe0c2}\n")
         .append(".st-LOGGER{background:#333c46;color:#aebbc9}\n")
         .append(".desc{font-size:14px}\n")
         .append(".desc .fail{color:#ff8a80;font-weight:600}\n")
         .append(".desc .warn{color:#ffd180}\n")
         .append(".desc .m{color:#8a97a5;font-size:12px}\n")
         .append(".meta{color:#7f8c9a;font-size:11.5px;margin-top:2px}\n")
         .append(".card .st{font-size:11px;font-weight:700;width:44px;text-align:right;flex-shrink:0}\n")
         .append(".card .st.g{color:#4cd964}.card .st.w{color:#ffd180}.card .st.r{color:#ff5252}.card .st.m{color:#8a97a5}\n")
         .append(".sec{margin:0 0 8px;border:1px solid #232b34;border-radius:6px}\n")
         .append(".sech{background:#161c23;color:#8fd48f;padding:3px 9px;font-size:12px;display:flex;justify-content:space-between;align-items:center;gap:8px;border-radius:6px 6px 0 0}\n")
         .append(".sec pre{margin:0;padding:7px 9px;white-space:pre-wrap;word-break:break-word;max-height:320px;overflow:auto;font-size:12px;color:#c7d1da;font-family:Menlo,Consolas,monospace}\n")
         .append(".cp{font-size:11px;padding:1px 8px}\n")
         .append("#notices{font-size:12px;margin:4px 0}\n")
         .append(".n-warn{color:#ffd180}.n-err{color:#ff8a80}\n")
         .append("#toast{position:fixed;bottom:14px;left:50%;transform:translateX(-50%);background:#123a1f;color:#9fe6a8;border:1px solid #2f7a44;padding:7px 16px;border-radius:8px;display:none;z-index:50;font-size:13px}\n")
         .append("#toast.err{background:#3a1214;color:#ff9e94;border-color:#7a2f34}\n")
         .append("#modal{position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,.72);display:none;z-index:40}\n")
         .append("#modalbox{background:#12181f;border:1px solid #2a333d;border-radius:10px;max-width:760px;margin:40px auto;padding:10px}\n")
         .append("#modalhead{display:flex;justify-content:space-between;color:#8fd48f;margin-bottom:6px;font-size:13px}\n")
         .append("#modalpre{max-height:60vh;overflow:auto;white-space:pre-wrap;word-break:break-word;background:#0b0f14;padding:8px;font-size:12px;color:#c7d1da;font-family:Menlo,Consolas,monospace}\n")
         .append("</style></head><body>\n")
         .append("<div id=top><span id=title>MONITOR SERVER<span class=ver>v16</span></span>")
         .append("<span id=pill class=g>cek …</span>")
         .append("<a href=/sdkconfig><button>SDK CONFIG</button></a>")
         .append("<span id=statsbar>memuat …</span></div>\n")

         .append("<div class=panel><div id=chips></div><div id=sysline></div></div>\n")
         .append("<div id=banner></div>\n")
         .append("<div class=panel id=summary>…</div>\n")

         .append("<div class=panel id=ctl>\n")
         .append("<button id=vimp class=vb on>TAMPILAN PENTING</button>")
         .append("<button id=vmal class=vb>MASALAH</button>")
         .append("<button id=vall class=vb>SEMUA</button>")
         .append("<button id=pause>Jeda</button> <button id=reset>Bersihkan</button> ")
         .append("<button id=guidebtn>Panduan login</button>\n")
         .append("<details id=adv><summary>filter lanjutan + salin / unduh</summary><div class=in>\n")
         .append("<label>Tahap <select id=stage><option value=''>semua</option><option>SISTEM</option><option>KONFIG</option><option>CDN</option><option>SDK</option><option>LOGIN</option><option>GAME</option></select></label>\n")
         .append("<label>Status <select id=st><option value=''>semua</option><option value=2>sukses (2xx)</option><option value=4>gagal (4xx)</option><option value=5>rusak (5xx)</option><option value=e>bermasalah</option></select></label>\n")
         .append("<label>Cari <input id=q size=14 placeholder=subset></label>\n")
         .append("<label>Jumlah <select id=limit><option>100</option><option selected>500</option><option>2000</option><option value=0>semua buffer</option></select></label>\n")
         .append("<label>Port <select id=port><option value=9527>9527 konfig</option><option value=9528>9528 CDN</option>")
         .append("<option value=5050>5050 login</option><option value=5051>5051 game</option><option value=9530>9530 SDK</option><option value=4000>4000 logger</option></select></label>\n")
         .append("<button id=cpts>Salin daftar</button> <button id=cpjs>Salin JSON</button> ")
         .append("<button id=dl>Unduh arsip</button>\n")
         .append("</div></details>\n")
         .append("<div class=panel id=guide style=display:none;margin-top:8px>\n")
         .append("<b style=color:#8fd48f>CARA LOGIN YANG BENAR (server lokal)</b>\n")
         .append("<ul style=margin:6px 0 2px 18px;padding:0;font-size:12.5px;color:#c7d1da>\n")
         .append("<li>Di layar login SDK: tekan tombol <b>TAMU (Guest)</b> — langsung main.</li>\n")
         .append("<li><b>DAFTAR</b> = buat akun email baru; <b>LOGIN</b> = masuk akun terdaftar.</li>\n")
         .append("<li>Jika SDK crash/tombol mati: buka <b>SDK CONFIG</b> → preset E1/E2/E3 (loginTypes) → force-stop &amp; buka game, satu variabel per coba.</li>\n")
         .append("<li>Alur normal: konfigurasi 9527 → resource 9528 → SDK 9530 → layar login → TAMU → verifikasi 5050 → game 5051.</li>\n")
         .append("</ul>\n</div>\n")
         .append("</div>\n")

         .append("<div id=notices></div>\n")
         .append("<div id=flow></div>\n")

         .append("<div id=toast></div>\n")
         .append("<div id=modal><div id=modalbox><div id=modalhead><span id=modaltitle></span><button id=modalx>Tutup</button></div><pre id=modalpre></pre></div></div>\n")

         .append("<script>\n")
         .append(Js())
         .append("</script>\n")
         .append("</body></html>");
        return b.toString();
    }

    private static String Js() {
        StringBuilder s = new StringBuilder(20480);
        s.append("var S={since:0,view:'penting',stage:'',st:'',q:'',limit:500,auto:true};\n")
         .append("var ENT={},DET={};var MAXC=3000;var pollN=0;var LASTSUM='';var API=null;\n")
         .append("function $(id){return document.getElementById(id)}\n")
         .append("function esc(x){return (x==null?'':''+x).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         .append("function attr(x){return esc(x).replace(/\\\"/g,'&quot;')}\n")
         .append("function hdrLines(h){return h?(''+h).split('; ').join('\\n'):''}\n")
         .append("function pretty(s){var t=(''+s).trim();var c=t.charAt(0);var e=t.charAt(t.length-1);\n")
         .append(" if((c=='{'&&e=='}')||(c=='['&&e==']')){try{return JSON.stringify(JSON.parse(t),null,2)}catch(x){}}\n")
         .append(" return ''+s}\n")
         .append("function prettyExtra(x){var i=(''+x).indexOf('DECODED-XOR: ');\n")
         .append(" if(i>=0){try{return x.substring(0,i+13)+'\\n'+JSON.stringify(JSON.parse(x.substring(i+13).trim()),null,2)}catch(y){}}\n")
         .append(" return ''+x}\n")
         .append("/* ---- tahap + deskripsi manusia (v5, diperluas v6) ---- */\n")
         .append("function stageOf(e){\n")
         .append(" if(e.kind=='BOOT'||e.kind=='SYS'||e.kind=='EXTRACT')return 'SISTEM';\n")
         .append(" if(e.port==9527)return 'KONFIG'; if(e.port==9528)return 'CDN';\n")
         .append(" if(e.port==9530)return 'SDK'; if(e.port==5050)return 'LOGIN';\n")
         .append(" if(e.port==5051)return 'GAME'; return 'LOGGER'}\n")
         .append("function pathOf(e){var p=(''+(e.path||''));var i=p.indexOf('?');return i>=0?p.substring(0,i):p}\n")
         .append("function ms2(n){var mb=n/1048576;return mb>=1?mb.toFixed(1)+'MB':Math.round(n/1024)+'KB'}\n")
         .append("function sdkErr(e){var m=/\\\"error\\\":\\{\\\"id\\\":(\\d+),\\\"message\\\":\\\"(.*?)\\\"/.exec(''+(e.respPreview||''));\n")
         .append(" return m?{id:+m[1],msg:m[2]}:{id:0,msg:''}}\n")
         .append("function errWhy(id,msg){\n")
         .append(" var m=''+(msg||'');\n")
         .append(" if(id==20000)return 'server tidak bisa membaca data (Params error)';\n")
         .append(" if(id==20002)return 'tanda tangan (sign) salah — cek signSecret di SDK CONFIG';\n")
         .append(" if(id==20003){var mm=/LostSomeMustParams:(\\w+)/.exec(m);return 'param wajib kosong: <b>'+(mm?mm[1]:'?')+'</b>'}\n")
         .append(" if(id==30001)return 'PRODUCT TIDAK DITERIMA — productCode game belum ada di daftar profile.json. Tekan tombol MERAH TERIMA PRODUCT INI di atas, lalu force-stop & buka game';\n")
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
         .append(" if(k=='BOOT')return '<b>Server mulai</b> — '+esc(p);\n")
         .append(" if(k=='SYS'&&m=='BIND')return (st==0?'<b>Port aktif:</b> ':'<b style=color:#ff8a80>Port GAGAL:</b> ')+esc(p);\n")
         .append(" if(k=='SYS'){var t=esc(p);if(/gagal|ditolak|rusak/i.test(p))t='<span class=fail>'+t+'</span>';return t}\n")
         .append(" if(k=='EXTRACT')return '<b>Ekstraksi aset game</b> — '+esc(p);\n")
         .append(" if(k=='LOGCAT')return '<b>Rekaman logcat</b> — diagnostik otomatis (aman diabaikan)';\n")
         .append(" if(k=='WS')return 'Koneksi WebSocket (login/game) — '+(e.respPreview=='pong'?'ping-pong sehat':esc((e.extra||'').substring(0,120)));\n")
         .append(" if(e.port==9527){\n")
         .append("  if(p.indexOf('setting_BS')>=0)return 'Baca <b>konfigurasi game</b> (setting) — alamat server diarahkan ke 127.0.0.1';\n")
         .append("  if(p.indexOf('com_db_local')>=0)return 'Baca konfigurasi aplikasi (com_db_local) — kosong (normal)';\n")
         .append("  return 'Konfigurasi: '+esc(p)}\n")
         .append(" if(e.port==9528){\n")
         .append("  if(p.indexOf('base.version')>=0)return 'Cek <b>versi dasar</b> — '+esc((e.respPreview||'').trim()||'?');\n")
         .append("  if(p.indexOf('resource.version')>=0)return 'Cek <b>versi resource</b> — '+esc((e.respPreview||'').trim()||'?');\n")
         .append("  if(p.indexOf('upgrade.json')>=0)return 'Baca <b>daftar paket update</b>';\n")
         .append("  if(p.indexOf('size.json')>=0)return 'Baca <b>ukuran paket update</b>';\n")
         .append("  if(p.indexOf('all.zip')>=0)return 'Unduh <b>paket update</b> — '+ms2(e.respLen||0)+' dalam '+e.durMs+'ms';\n")
         .append("  if(p.indexOf('serversetting')>=0)return 'Baca <b>alamat server login</b> (serversetting)';\n")
         .append("  if(p.indexOf('index-native')>=0)return 'Muat <b>UI game</b> (index-native.html)';\n")
         .append("  if(p.indexOf('manifest.json')>=0)return 'Muat manifest game';\n")
         .append("  if(/\\.js($|\\?)/.test(p))return 'Muat skrip game — '+esc(p.split('/').pop());\n")
         .append("  if(p.indexOf('/resource/')>=0)return 'Muat aset game — '+esc(p.split('/').pop().substring(0,40));\n")
         .append("  return 'CDN: '+esc(p)}\n")
         .append(" if(e.port==9530){\n")
         .append("  var er=sdkErr(e),bad=(e.respPreview||'').indexOf('\\\"result\\\":false')>=0;\n")
         .append("  if(fail||bad){var nm=epName(p);return '<span class=fail>'+nm+' GAGAL</span> — '+errWhy(er.id,er.msg)+(er.id?' <span class=m>(id '+er.id+')</span>':'')}\n")
         .append("  if(p.indexOf('/v1/system/init')>=0)return '<b>Inisialisasi SDK</b> — sukses, layar login mengikuti loginTypes';\n")
         .append("  if(p.indexOf('registerVisitor')>=0)return '<b>Buat akun tamu</b> — uid '+esc(uin(extra))+' tersimpan permanen';\n")
         .append("  if(p.indexOf('autoLogin')>=0)return '<b>Login otomatis</b> — akun '+esc(una(extra))+' (tersimpan)';\n")
         .append("  if(p.indexOf('getNotice')>=0)return 'Cek pengumuman SDK — tidak ada';\n")
         .append("  if(p.indexOf('setGameRoleInfo')>=0)return 'Lapor data karakter ke SDK';\n")
         .append("  if(p.indexOf('ckRegVistor')>=0)return 'Cek <b>pendaftaran</b> (Kode Migrasi) — isReged:0 = persis server asli';\n")
         .append("  return epName(p)+' — sukses'}\n")
         .append(" if(e.port==5050){\n")
         .append("  if(p.indexOf('loginchecknative')>=0)return '<b>Verifikasi akun</b> (loginchecknative) — game dilanjutkan';\n")
         .append("  if((e.path||'').indexOf('transport=polling')>=0&&e.respPreview&&e.respPreview.indexOf(':0{')>=0)return 'Handshake <b>server login</b> — sesi dibuat';\n")
         .append("  var ev=evName(e.extra);if(ev)return 'Event login <b>'+esc(ev.a)+'</b>'+(ev.ack?' — dijawab':' — dikirim');\n")
         .append("  if((e.extra||'').indexOf('ping→pong')>=0)return 'Keep-alive login (ping-pong)';\n")
         .append("  return 'Server login: '+esc(p.substring(0,60))}\n")
         .append(" if(e.port==5051){\n")
         .append("  var ev=evName(e.extra);\n")
         .append("  if(ev&&ev.a=='enterGame')return (e.respPreview&&e.respPreview.indexOf('ret\\\":0')>=0||e.respPreview&&e.respPreview.indexOf('\\\"ret\\\":0')>=0)?'<b>Masuk server 1</b> — data pemain diterima ('+ms2(e.respLen||0)+')':'Masuk server — '+ms2(e.respLen||0);\n")
         .append("  if(ev)return 'Event game <b>'+esc(ev.a)+'</b>'+(ev.ack?' — dijawab':' — dikirim');\n")
         .append("  return 'Server game: '+esc(p.substring(0,60))}\n")
         .append(" return esc(p)}\n")
         .append("function uin(x){var m=/uid[=\\\":]+([0-9]+)/.exec(''+x);return m?m[1]:'?'}\n")
         .append("function una(x){var m=/username[=\\\":\\s]+([A-Za-z0-9_]+)/.exec(''+x);return m?m[1]:'tamu'}\n")
         .append("function evName(extra){var s=''+(extra||'');var i=s.indexOf('EVENT: 42');if(i<0)return null;\n")
         .append(" var m=/action\":\"([A-Za-z]+)/.exec(s.substring(i));var ack=s.indexOf('ACK: 43',i)>=0;\n")
         .append(" return m?{a:m[1],ack:ack}:{a:'?',ack:ack}}\n")
         .append("/* ---- v6: masalah / penting / verdict ---- */\n")
         .append("function isProblem(e){\n")
         .append(" if((e.error||'').length)return true;\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='SYS'||k=='BOOT'||k=='EXTRACT'){var t=(''+(e.path||'')).toLowerCase();return t.indexOf('gagal')>=0||t.indexOf('ditolak')>=0||t.indexOf('rusak')>=0}\n")
         .append(" if(k=='HTTP'||k=='WS'){\n")
         .append("  if((e.status||0)>=400)return true;\n")
         .append("  if((e.respPreview||'').indexOf('\\\"result\\\":false')>=0)return true}\n")
         .append(" return false}\n")
         .append("function isImportant(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='BOOT'||k=='SYS'||k=='EXTRACT'||k=='LOGCAT')return true;\n")
         .append(" if(e.port==9527||e.port==9530)return true;\n")
         .append(" if(e.port==9528){var p=pathOf(e);\n")
         .append("  return p.indexOf('base.version')>=0||p.indexOf('resource.version')>=0||p.indexOf('upgrade.json')>=0||p.indexOf('size.json')>=0||p.indexOf('all.zip')>=0||p.indexOf('index-native')>=0||p.indexOf('serversetting')>=0}\n")
         .append(" if(e.port==5050){if((e.extra||'').indexOf('ping→pong')>=0)return false;return true}\n")
         .append(" if(e.port==5051)return true;\n")
         .append(" return false}\n")
         .append("function verdictOf(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k!='HTTP'&&k!='WS')return isProblem(e)?['CEK','w']:['SISTEM','m'];\n")
         .append(" if(isProblem(e))return ['GAGAL','r'];\n")
         .append(" if((e.status||0)>=300)return ['CEK','w'];\n")
         .append(" if(e.status==0)return ['-','m'];\n")
         .append(" return ['OK','g']}\n")
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
         .append(" if(e.kind!='HTTP'){d.push({l:'INFO',t:(e.path||'')+(e.respPreview?'\\n'+e.respPreview:'')});}\n")
         .append(" else{var t=(e.method||'')+' http://127.0.0.1:'+e.port+(e.path||'')+'\\n'+hdrLines(e.headers);\n")
         .append("  if(e.reqPreview)t+='\\n[body '+(e.reqLen||0)+'B]\\n'+pretty(e.reqPreview);\n")
         .append("  d.push({l:'REQUEST',t:t});\n")
         .append("  if(e.respPreview!=null)d.push({l:'RESPON',t:'status '+(e.status||0)+'\\n'+pretty(e.respPreview)});}\n")
         .append(" if(e.extra)d.push({l:'EXTRA',t:prettyExtra(e.extra)});\n")
         .append(" if(e.error)d.push({l:'ERROR',t:e.error});\n")
         .append(" d.push({l:'SEMUA (teks polos)',t:allText(e)});\n")
         .append(" return d}\n")
         .append("function stCls(e){if(e.kind!='HTTP'&&e.kind!='WS')return 'm';\n")
         .append(" if(e.port==9530&&(e.respPreview||'').indexOf('\\\"result\\\":false')>=0)return 'r';\n")
         .append(" if(e.error)return 'r'; if(e.status>=500)return 'r'; if(e.status>=400)return 'r';\n")
         .append(" if(e.status>=300)return 'w'; if(e.status==0)return 'm'; return 'g'}\n")
         .append("function important(e){var p=pathOf(e);\n")
         .append(" if(e.status>=400)return '! '+plain(describe(e));\n")
         .append(" if(e.port==9530&&p.indexOf('registerVisitor')>=0)return 'OK '+describe(e);\n")
         .append(" if(e.port==5050&&p.indexOf('loginchecknative')>=0)return 'OK '+describe(e);\n")
         .append(" if(e.port==5051&&(e.extra||'').indexOf('enterGame')>=0)return 'OK '+describe(e);\n")
         .append(" if(e.port==9528&&p.indexOf('index-native')>=0)return 'OK '+describe(e);\n")
         .append(" return null}\n")
         .append("function cardHtml(e){\n")
         .append(" var sg=stageOf(e),d=describe(e),c=stCls(e),v=verdictOf(e);\n")
         .append(" var h='<div class=card data-seq='+e.seq+'>+'\n")
         .append("  +'<div class=st '+v[1]+'>'+v[0]+'</div>'\n")
         .append("  +'<div class=main><div class=desc><span class=\"stage st-'+sg+'\">'+sg+'</span>'+d+'</div>'\n")
         .append("  +'<div class=meta>#'+e.seq+' · '+esc(e.time)+' · '+e.durMs+'ms · kirim '+(e.reqLen||0)+'B terima '+(e.respLen||0)+'B'+(e.kind=='HTTP'||e.kind=='WS'?' · HTTP '+(e.status||0):'')+(e.error?' · <span style=color:#ff8a80>error</span>':'')+'</div></div>'\n")
         .append("  +'<button class=cp data-seq='+e.seq+' data-k=all>salin</button></div>';\n")
         .append(" var det=detOf(e);DET[e.seq]=det;\n")
         .append(" var dd='<div class=det style=display:none><div style=padding:8px 12px>';\n")
         .append(" for(var i=0;i<det.length;i++)dd+='<div class=sec><div class=sech><span>'+esc(det[i].l)+'</span><button class=cp data-seq='+e.seq+' data-k='+i+'>salin</button></div><pre>'+esc(det[i].t)+'</pre></div>';\n")
         .append(" dd+='</div></div>';\n")
         .append(" return h+dd}\n")
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
         .append("function applyFilter(){var cs=$('flow').querySelectorAll('.card');\n")
         .append(" for(var i=0;i<cs.length;i++){var e=ENT[cs[i].getAttribute('data-seq')];\n")
         .append("  var show=e?pass(e):true;cs[i].style.display=show?'':'none';\n")
         .append("  var det=cs[i].nextSibling;\n")
         .append("  if(det&&det.className=='det')det.style.display=(show&&det.style.display!='none')?'':'none';}}\n")
         .append("function refreshPill(){var n=0;for(var k in ENT)if(isProblem(ENT[k]))n++;\n")
         .append(" var p=$('pill');if(n==0){p.textContent='SEMUA NORMAL';p.className='g'}\n")
         .append(" else{p.textContent=n+' MASALAH';p.className='r'}}\n")
         .append("function fixCode(e){var m=/GERBANG-30001\\(product=([0-9A-Za-z]+)\\)/.exec(''+(e.extra||''));\n")
         .append(" if(m)return m[1];\n")
         .append(" if(API&&API.productCode&&!API.productOk)return API.productCode;\n")
         .append(" return ''}\n")
         .append("function renderBanner(){var w=$('banner');var list=[];\n")
         .append(" for(var k in ENT){if(isProblem(ENT[k]))list.push(ENT[k])}\n")
         .append(" list.sort(function(a,b){return b.seq-a.seq});list=list.slice(0,3);\n")
         .append(" if(!list.length){w.innerHTML='';return}\n")
         .append(" var h='';for(var i=0;i<list.length;i++){var e=list[i];\n")
         .append("  h+='<div class=prob><div class=ph>MASALAH #'+e.seq+' · '+esc(e.time)+'</div><div class=pm>'+plain(describe(e))+'</div>';\n")
         .append("  var fc=fixCode(e);if(fc)h+='<button data-fix=\\\"'+esc(fc)+'\\\">TERIMA PRODUCT INI ('+esc(fc.substring(0,10))+'…)</button>';\n")
         .append("  h+='</div>'}\n")
         .append(" w.innerHTML=h}\n")
         .append("function append(list){var w=$('flow');\n")
         .append(" var pin=w.scrollTop+w.clientHeight>=w.scrollHeight-60;\n")
         .append(" for(var i=0;i<list.length;i++){var e=list[i];ENT[e.seq]=e;\n")
         .append("  var tmp=document.createElement('div');tmp.innerHTML=cardHtml(e);\n")
         .append("  while(tmp.firstChild)w.appendChild(tmp.firstChild);\n")
         .append("  var im=important(e);if(im)LASTSUM=im;}\n")
         .append(" var cs=w.querySelectorAll('.card');var over=cs.length-MAXC*2;\n")
         .append(" for(var j=0;j<over;j++)w.removeChild(w.firstChild);\n")
         .append(" applyFilter();refreshPill();renderBanner();\n")
         .append(" if(LASTSUM)$('summary').innerHTML='<b>Terakhir penting:</b> '+LASTSUM;if(pin)w.scrollTop=w.scrollHeight}\n")
         .append("function visible(){var out=[];var cs=$('flow').querySelectorAll('.card');\n")
         .append(" for(var i=0;i<cs.length;i++){if(cs[i].style.display=='none')continue;\n")
         .append("  var e=ENT[cs[i].getAttribute('data-seq')];if(e)out.push(e)}\n")
         .append(" return out}\n")
         .append("function copyTSV(){var v=visible();\n")
         .append(" var L=['seq\\twaktu\\ttahap\\tport\\tstatus\\tdurMs\\tpath\\terror'];\n")
         .append(" for(var i=0;i<v.length;i++){var e=v[i];\n")
         .append("  L.push([e.seq,e.time,stageOf(e),e.port,e.status||0,e.durMs,(e.path||'').replace(/\\t/g,' '),(e.error||'').replace(/\\t/g,' ')].join('\\t'))}\n")
         .append(" copyText(L.join('\\n'))}\n")
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
         .append(" else openModal('Salin manual — Ctrl+C',t)}\n")
         .append("function openModal(t2,t){$('modaltitle').textContent=t2;$('modalpre').textContent=t;$('modal').style.display='block';\n")
         .append(" var r=document.createRange();r.selectNodeContents($('modalpre'));\n")
         .append(" var sc=window.getSelection();sc.removeAllRanges();try{sc.addRange(r)}catch(x){}}\n")
         .append("var tt=null;\n")
         .append("function toast(m,err){var el=$('toast');el.textContent=m;el.className=err?'err':'';el.style.display='block';clearTimeout(tt);tt=setTimeout(function(){el.style.display='none'},2600)}\n")
         .append("function post(url,body){return fetch(url,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body}).then(function(r){return r.json()})}\n")
         .append("$('flow').addEventListener('click',function(ev){\n")
         .append(" var n=ev.target;\n")
         .append(" while(n&&n.tagName!='BUTTON'&&!(n.className||'').toString().indexOf('card')>=0)n=n.parentNode;\n")
         .append(" if(n&&n.tagName=='BUTTON'){var seq=n.getAttribute('data-seq'),k=n.getAttribute('data-k');\n")
         .append("  var d=DET[seq];if(d){var t=(k=='all')?d[d.length-1].t:d[parseInt(k)].t;copyText(t)}\n")
         .append("  ev.stopPropagation();return}\n")
         .append(" var card=ev.target.closest?ev.target.closest('.card'):null;\n")
         .append(" if(!card)return;\n")
         .append(" var det=card.nextSibling;\n")
         .append(" if(det&&det.className=='det')det.style.display=(det.style.display=='none'?'':'none');});\n")
         .append("$('banner').addEventListener('click',function(ev){\n")
         .append(" var b2=ev.target.closest?ev.target.closest('[data-fix]'):null;\n")
         .append(" if(!b2)return;var code=b2.getAttribute('data-fix');\n")
         .append(" post('/sdkconfig/product-add','code='+encodeURIComponent(code)).then(function(j){\n")
         .append("  if(j.result){toast('productCode DITERIMA — force-stop & buka game');renderBanner()}\n")
         .append("  else toast(j.error||'gagal',true)}).catch(function(){toast('jaringan gagal',true)})});\n")
         .append("$('modalx').onclick=function(){$('modal').style.display='none'};\n")
         .append("$('modal').onclick=function(ev){if(ev.target.id=='modal')$('modal').style.display='none'};\n")
         .append("function sync(){S.stage=$('stage').value;S.st=$('st').value;S.q=$('q').value.toLowerCase();S.limit=parseInt($('limit').value);S.port=parseInt($('port').value)}\n")
         .append("function reset(){$('flow').innerHTML='';ENT={};DET={};S.since=0;LASTSUM='';$('notices').innerHTML='';refreshPill();renderBanner()}\n")
         .append("function setView(v){S.view=v;\n")
         .append(" $('vimp').className='vb'+(v=='penting'?' on':'');\n")
         .append(" $('vmal').className='vb'+(v=='masalah'?' on':'');\n")
         .append(" $('vall').className='vb'+(v=='all'?' on':'');\n")
         .append(" applyFilter();if(v=='masalah')$('flow').scrollTop=0}\n")
         .append("$('vimp').onclick=function(){setView('penting')};\n")
         .append("$('vmal').onclick=function(){setView('masalah')};\n")
         .append("$('vall').onclick=function(){setView('all')};\n")
         .append("['stage','limit','port'].forEach(function(id){$(id).onchange=function(){\n")
         .append(" if(id=='stage'){S.stage=$('stage').value;applyFilter();return}\n")
         .append(" sync();reset()}});\n")
         .append("['st'].forEach(function(id){$(id).onchange=function(){sync();applyFilter()}});\n")
         .append("$('q').oninput=function(){S.q=$('q').value.toLowerCase();applyFilter()};\n")
         .append("$('pause').onclick=function(){S.auto=!S.auto;$('pause').textContent=S.auto?'Jeda':'Lanjut'};\n")
         .append("$('guidebtn').onclick=function(){var g=$('guide');g.style.display=(g.style.display=='none'?'':'none')};\n")
         .append("$('reset').onclick=reset;\n")
         .append("$('cpts').onclick=copyTSV;\n")
         .append("$('cpjs').onclick=copyJSON;\n")
         .append("$('dl').onclick=function(){var a=document.createElement('a');a.href='/download?latest=1';a.download='';document.body.appendChild(a);a.click();document.body.removeChild(a)};\n")
         .append("var lastDrop=-1;\n")
         .append("function note(c2,txt){var d=document.createElement('div');d.className=c2;d.textContent=txt;$('notices').appendChild(d)}\n")
         .append("function url(){return '/logs?mode=all&limit='+S.limit+'&since='+S.since}\n")
         .append("function chips(j){var c=$('chips');var ids=[['9527','KONFIG'],['9528','CDN (update/UI)'],['5050','LOGIN'],['5051','GAME'],['9530','SDK'],['4000','LOGGER']];\n")
         .append(" var h='';for(var i=0;i<ids.length;i++){var p=ids[i][0],cc=j.ports[p];\n")
         .append("  h+='<span class=chip><span class=\"dot '+((cc&&cc.requests>0)?'g':'m')+'\"></span><b>'+ids[i][1]+'</b> '+p+(cc?(' · '+cc.requests+' req'+(cc.errors?' · <span style=color:#ff8a80>'+cc.errors+' gagal</span>':'')):' · 0 req')+'</span>'}\n")
         .append(" c.innerHTML=h}\n")
         .append("function sysline(){fetch('/sdkconfig/api').then(function(r){return r.json()}).then(function(j){API=j;\n")
         .append(" var h='DB <b>'+esc(j.backend)+'</b> ('+esc(j.dbDir)+') · PRODUCT: ';\n")
         .append(" if(j.productCode){h+='<b class='+(j.productOk?'ok':'bad')+'>'+esc(j.productCode)+'</b> '+(j.productOk?'DITERIMA':'DITOLAK — lihat banner merah')}\n")
         .append(" else h+='<span class=m>belum ada request game</span>';\n")
         .append(" h+=' · tamu: '+(j.guest?esc(j.guest.uid):'-')+' · akun email: '+j.accounts.length;\n")
         .append(" $('sysline').innerHTML=h}).catch(function(){})}\n")
         .append("function poll(){\n")
         .append(" if(!S.auto){setTimeout(poll,500);return}\n")
         .append(" fetch(url()).then(function(r){return r.json()}).then(function(j){\n")
         .append("  if(j.gap&&j.entries.length)note('n-warn','GAP: sebagian entri buffer terbuang — arsip disk tetap utuh (Unduh arsip)');\n")
         .append("  if(j.entries.length){append(j.entries);S.since=j.seqNewest}\n")
         .append("  var tot=(j.droppedGlobal||0)+(j.droppedPort||0);\n")
         .append("  if(tot>0&&tot!==lastDrop){lastDrop=tot;note('n-err','buffer dropped '+tot+' — arsip disk tetap utuh')}\n")
         .append("  pollN++;if(pollN%3==0)refreshStats();\n")
         .append("  setTimeout(poll,1200);\n")
         .append(" }).catch(function(){setTimeout(poll,3000)})}\n")
         .append("function refreshStats(){fetch('/stats').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var up=Math.floor(j.uptimeMs/1000);\n")
         .append(" var t='uptime '+Math.floor(up/3600)+'h'+Math.floor(up%3600/60)+'m'+(up%60)+'s · seq '+j.seq;\n")
         .append(" if(j.disk&&j.disk.enabled)t+=' · arsip '+j.disk.file+' '+(j.disk.bytes>=1048576?(j.disk.bytes/1048576).toFixed(1)+'MB':Math.round(j.disk.bytes/1024)+'KB')+' · '+j.disk.lines+' baris';\n")
         .append(" $('statsbar').textContent=t;chips(j);sysline()}).catch(function(){})}\n")
         .append("document.title='MONITOR SERVER :'+location.port;\n")
         .append("refreshStats();sync();poll();\n")
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
        b.append("{\"ver\":\"v16\"")
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
         .append("function post(url,body){return fetch(url,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body}).then(function(r){return r.json()})}\n")
         .append("function preset(types){post('/sdkconfig/preset','types='+encodeURIComponent(types)).then(function(j){if(j.result){toast('init.json -> loginTypes ['+j.loginTypes+'] — force-stop & buka game');load()}else toast(j.error||'gagal',true)}).catch(function(){toast('jaringan gagal',true)})}\n")
         .append("function prodAdd(){var v=$('prodin').value.trim();if(!v){toast('isi productCode dulu',true);return}post('/sdkconfig/product-add','code='+encodeURIComponent(v)).then(function(j){if(j.result){toast('productCode DITERIMA — force-stop & buka game');$('prodin').value='';load()}else toast(j.error||'gagal',true)}).catch(function(){toast('jaringan gagal',true)})}\n")
         .append("function saveFile(name,tid){var v=$(tid).value;post('/sdkconfig/save','name='+encodeURIComponent(name)+'&content='+encodeURIComponent(v)).then(function(j){if(j.result){toast(name+' tersimpan');load()}else toast(j.error||'gagal',true)}).catch(function(){toast('jaringan gagal',true)})}\n")
         .append("function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         .append("function load(){fetch('/sdkconfig/api').then(function(r){return r.json()}).then(function(j){\n")
         .append(" $('statsbar').textContent='backend '+j.backend+' · '+j.dbDir;\n")
         .append(" $('chips').innerHTML='<span class=chip><b>DB-BACKEND</b>&nbsp;'+esc(j.backend)+'</span><span class=chip><b>tamu</b>&nbsp;'+(j.guest?esc(j.guest.uid)+' ('+esc(j.guest.username)+')':'belum ada')+'</span><span class=chip><b>akun email</b>&nbsp;'+j.accounts.length+'</span>';\n")
         .append(" $('prodchip').textContent=j.productCode?('terakhir dikirim: '+j.productCode+(j.productOk?' — DITERIMA':' — DITOLAK (30001)')):'belum ada request game';\n")
         .append(" $('prodchip').className=j.productCode?(j.productOk?'g':'r'):'m';\n")
         .append(" var pl='';for(var pi=0;pi<(j.products||[]).length;pi++)pl+='<span class=chip style=margin:2px 4px 2px 0>'+esc(j.products[pi])+'</span>';\n")
         .append(" $('prodl').innerHTML=pl||'<span class=m>kosong</span>';\n")
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
