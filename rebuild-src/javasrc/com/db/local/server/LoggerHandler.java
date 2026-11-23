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
 * Bagian 44 — KOREKSI OWNER (kata-kata persis):
 *   "hadeeeh malah makin cacat. ini maunya saya di percantik bukan di
 *    pangkas.. teks yg muncul itu seperti teks layaknya server original..
 *    bukan teks yg output nya minimalis"
 * CONSOLE v8 — SERVER CONSOLE PREMIUM (dipercantik, TIDAK dipangkas):
 *   - teks log kini VERBOSE seperti console server asli: TIAP PERISTIWA
 *     dijabarkan penuh — kalimat deskripsi lengkap + baris detail ↳ berisi
 *     METHOD PATH · STATUS · req/resp · durasi (contoh:
 *     "Registrasi akun tamu berhasil — uid … diterbitkan, token tersimpan
 *     permanen di database lokal" + "↳ POST /sdk/v1/user/registerVisitor ·
 *     status 200 · req 118 B · resp 342 B · 12 ms");
 *   - format baris ala server asli: WAKTU(ms) LEVEL [KOMPONEN] pesan —
 *     LEVEL = INFO / DEBUG / WARN / ERROR (warna seperti log server asli:
 *     hijau/cyan/amber/merah); KOMPONEN = [CORE][CONFIG][CDN][SDK][LOGIN]
 *     [GAME][MONITOR] (warna khas per layanan, seperti tag komponen server);
 *   - UI DIPERCANTIK (bukan dipangkas): header premium (titik status
 *     bercahaya + judul + badge versi), banner boot ala server saat stream
 *     dibuka, kursor blok berkedip, tombol ikon rapi, warna level jelas,
 *     baris detail terpisah, baris masalah berlatar merah gelap;
 *   - SALIN LOG = teks PERSIS format server (WAKTU LEVEL [KOMPONEN] pesan +
 *     baris ↳) — saat ditempel ke mana pun terlihat seperti log server
 *     sungguhan, bukan output minimalis;
 *   - yang TIDAK berubah (semua arahan lama tetap): overlay = INFO bukan
 *     error (Bagian 39); tombol IKON saja (Bagian 41); port 4000 murni log
 *     monitoring — rute /sdkconfig* + /fix/overlay tetap 404 (Bagian 41/43);
 *     tanpa popup "tips" (Bagian 42); poll incremental ?since= 1.2 dtk,
 *     MAXC 3000, filter tahap/port/status/cari/limit, Salin JSON, unduh
 *     arsip NDJSON, banner CRASH + salin trace (Bagian 32).
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

    /* ================= console SERVER PREMIUM (Bagian 44) =================
     * Arahan owner 24 Nov 2026 (kata-kata persis):
     *   "hadeeeh malah makin cacat. ini maunya saya di percantik bukan di
     *    pangkas.. teks yg muncul itu seperti teks layaknya server original..
     *    bukan teks yg output nya minimalis"
     * Jawaban v8 — konsol server premium (keseimbangan tepat atas dua arahan
     * berturut-turut: Bagian 39 "stylish tapi gak natural" vs Bagian 44
     * "dipercantik bukan dipangkas, teks seperti server original"):
     *  1. TEKS VERBOSE seperti server asli — kalimat deskripsi penuh per
     *     peristiwa + baris detail ↳ (METHOD PATH · STATUS · SIZE · DURASI);
     *  2. FORMAT baris = WAKTU(ms) LEVEL [KOMPONEN] pesan:
     *     - LEVEL: INFO hijau / DEBUG cyan / WARN amber / ERROR merah
     *       (persis konvensi log server: DEBUG=traffic halus seperti aset
     *       & heartbeat; WARN=4xx/redirect/perlu perhatian; ERROR=gagal);
     *     - KOMPONEN: [CORE] [CONFIG] [CDN] [SDK] [LOGIN] [GAME] [MONITOR]
     *       — tag berwarna khas per layanan seperti komponen server asli;
     *  3. UI DIPERCANTIK: header premium + titik status bercahaya + badge
     *     versi, banner boot server saat console dibuka, kursor blok
     *     berkedip, baris detail terpisah rapi, baris masalah merah gelap;
     *  4. SALIN LOG = teks format server persis (siap tempel);
     *  5. yang TIDAK berubah: overlay=INFO (39), tombol ikon (41), port
     *     4000 murni monitoring + rute sdkconfig/fix 404 (41/43), tanpa
     *     "tips" (42), poll incremental + filter lengkap + Salin JSON +
     *     unduh arsip + banner CRASH (32).
     */

    private String dashboardHtml() {
        StringBuilder b = new StringBuilder(32768);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>DB LOCAL SERVER — live console</title>\n")
         .append("<style>\n")
         .append("*{box-sizing:border-box}\n")
         .append("html,body{height:100%}\n")
         .append("body{font-family:'Roboto Mono',Menlo,Consolas,'Droid Sans Mono',monospace;background:#070b11;color:#c6d2de;margin:0;font-size:12.5px;line-height:1.55;display:flex;flex-direction:column;user-select:text;-webkit-user-select:text}\n")
         .append("#hdr{padding:10px 14px 9px;border-bottom:1px solid #1b2836;background:linear-gradient(180deg,#0e1621,#0a1018)}\n")
         .append("#l1{display:flex;align-items:center;gap:9px;flex-wrap:wrap}\n")
         .append("#dot{width:9px;height:9px;border-radius:50%;background:#43d17c;box-shadow:0 0 10px rgba(67,209,124,.85);animation:pl 2.4s ease-in-out infinite;flex:none}\n")
         .append("@keyframes pl{50%{opacity:.55}}\n")
         .append("#title{color:#e8f0f8;font-weight:700;font-size:14px;letter-spacing:.6px}\n")
         .append("#ver{background:#0f2418;border:1px solid #2f6e44;color:#7ee2a8;border-radius:4px;padding:1px 7px;font-size:10.5px;font-weight:700;letter-spacing:.5px}\n")
         .append("#versub{color:#5f7183;font-size:11px}\n")
         .append("#sp{flex:1}\n")
         .append(".lbtn{background:#0e1620;border:1px solid #243447;color:#9fb2c4;border-radius:6px;padding:4px 8px;min-height:31px;min-width:31px;font-size:13px;cursor:pointer;font-family:inherit;transition:border-color .15s,background .15s}\n")
         .append(".lbtn:hover{border-color:#3d5268;background:#13202c}\n")
         .append(".lbtn.on{border-color:#2f6e44;color:#7ee2a8;background:#0a170f}\n")
         .append("#stline{margin-top:7px;font-size:11.5px;color:#64778a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}\n")
         .append("#stline .up{color:#43d17c;font-weight:600}\n")
         .append("#stline .dw{color:#3d4a56}\n")
         .append("#stline b{color:#43d17c;font-weight:700}\n")
         .append("#stline b.bad{color:#e05d5d}\n")
         .append("#ctl{display:none;padding:8px 14px;border-bottom:1px solid #1b2836;background:#0a1018;flex-wrap:wrap;gap:6px 16px;align-items:center}\n")
         .append("#ctl label{color:#64778a;font-size:11.5px;display:inline-flex;align-items:center;gap:6px}\n")
         .append("select,input{background:#0d151d;color:#c6d2de;border:1px solid #243447;border-radius:5px;padding:4px 8px;font-family:inherit;font-size:12px}\n")
         .append("input{min-width:150px}\n")
         .append("#crashbn{display:none;padding:9px 14px;border-bottom:1px solid #4a262c;background:#130a0c}\n")
         .append("#crashbn .ch{color:#e05d5d;font-weight:700;font-size:12px}\n")
         .append("#crashbn .cd{color:#9fb2c4;font-size:11.5px;margin-top:2px}\n")
         .append("#crashbn pre{margin:6px 0 4px;padding:8px;background:#070b11;border:1px solid #2a1a1e;border-radius:5px;max-height:180px;overflow:auto;white-space:pre-wrap;word-break:break-word;font-size:11px;color:#d8c2bf}\n")
         .append("#notices{padding:0 14px}\n")
         .append(".nt{font-size:11.5px;padding:2px 0}\n")
         .append(".nt-w{color:#e0a93e}.nt-e{color:#e05d5d}\n")
         .append("#stream{flex:1;overflow-y:auto;padding:10px 14px}\n")
         .append("#stream::-webkit-scrollbar{width:10px;height:10px}\n")
         .append("#stream::-webkit-scrollbar-thumb{background:#243447;border-radius:5px}\n")
         .append("#banner{white-space:pre;color:#3f8f5c;font-size:11.5px;line-height:1.5;margin:0 0 8px}\n")
         .append("#banner .dim{color:#3d5547}\n")
         .append(".ln{white-space:pre-wrap;word-break:break-word;padding:2px 5px;border-radius:4px}\n")
         .append(".ln:hover{background:#0e1722}\n")
         .append(".ln.bad{background:#160a0e}\n")
         .append(".sq{display:inline-block;width:5.5ch;color:#39434d}\n")
         .append(".tm{display:inline-block;width:13.5ch;color:#5b6c7d}\n")
         .append(".lv{display:inline-block;width:6ch;font-weight:700;letter-spacing:.5px}\n")
         .append(".l-g{color:#43d17c}.l-d{color:#54c6d8}.l-w{color:#e0a93e}.l-r{color:#e05d5d}.l-i{color:#64778a;font-weight:400}\n")
         .append(".cp{display:inline-block;width:9.5ch;font-weight:700}\n")
         .append(".c-CORE{color:#8fa0b0}.c-CONFIG{color:#d9c377}.c-CDN{color:#52d0b0}.c-SDK{color:#b899e8}.c-LOGIN{color:#f0a35e}.c-GAME{color:#37c9a6}.c-MONITOR{color:#8a97a5}\n")
         .append(".ev{color:#c6d2de}\n")
         .append(".ev.w{color:#e0a93e}.ev.r{color:#e05d5d}.ev.d{color:#7f919e}\n")
         .append(".ev .f{color:#e05d5d;font-weight:700}\n")
         .append(".dt{display:block;color:#51626f;font-size:11px;margin-left:30.5ch;white-space:pre-wrap;word-break:break-word}\n")
         .append("#cursor{display:inline-block;width:8px;height:14px;background:#43d17c;vertical-align:-2px;animation:bl 1.1s step-end infinite;box-shadow:0 0 8px rgba(67,209,124,.6)}\n")
         .append("@keyframes bl{50%{opacity:0}}\n")
         .append("#pausemark{color:#64778a;font-style:italic}\n")
         .append("#ftr{padding:8px 14px;border-top:1px solid #1b2836;background:linear-gradient(0deg,#0e1621,#0a1018);display:flex;gap:8px;align-items:center;flex-wrap:wrap}\n")
         .append("#sum{color:#64778a;font-size:11.5px;flex:1;min-width:200px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}\n")
         .append("#sum b{color:#c6d2de;font-weight:600}\n")
         .append("#toast{position:fixed;bottom:16px;left:50%;transform:translateX(-50%);background:#0a170f;color:#7ee2a8;border:1px solid #2f6e44;padding:7px 16px;border-radius:6px;display:none;z-index:50;font-size:12px;box-shadow:0 4px 18px rgba(0,0,0,.45)}\n")
         .append("#toast.err{background:#130a0c;color:#e05d5d;border-color:#4a262c}\n")
         .append("#modal{position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(3,6,10,.86);display:none;z-index:40}\n")
         .append("#modalbox{background:#0a1018;border:1px solid #243447;border-radius:8px;max-width:780px;margin:30px auto;padding:12px;max-height:85vh;box-shadow:0 8px 40px rgba(0,0,0,.5)}\n")
         .append("#modalhead{display:flex;justify-content:space-between;gap:8px;color:#43d17c;margin-bottom:8px;font-size:12px;align-items:center}\n")
         .append("#modalpre{max-height:64vh;overflow:auto;white-space:pre-wrap;word-break:break-word;background:#070b11;padding:10px;font-size:12px;color:#c6d2de;border:1px solid #1b2836;border-radius:5px}\n")
         .append("@media(max-width:640px){.sq{display:none}.dt{margin-left:2px}body{font-size:12px}#banner{font-size:9.5px}}\n")
         .append("</style></head><body>\n")

         .append("<div id=hdr><div id=l1>")
         .append("<span id=dot></span><span id=title>DB LOCAL SERVER</span><span id=ver>v8</span><span id=versub>· live console · com.db.local</span>")
         .append("<span id=sp></span>")
         .append("<button id=vimp class=lbtn title='Tampilkan penting saja'>⭐</button>")
         .append("<button id=vmal class=lbtn title='Tampilkan masalah saja'>❌</button>")
         .append("<button id=vall class=\"lbtn on\" title='Tampilkan semua log'>📜</button>")
         .append("<button id=pf class=lbtn title='Filter log'>⚙️</button>")
         .append("<button id=pause class=lbtn title='Jeda / lanjut pembaruan otomatis'>⏸️</button>")
         .append("<button id=reset class=lbtn title='Bersihkan tampilan'>🧹</button>")
         .append("<button id=guidebtn class=lbtn title='Panduan singkat'>❓</button>")
         .append("</div>\n")
         .append("<div id=stline>menghubungkan …</div></div>\n")

         .append("<div id=ctl>\n")
         .append("<label>cari <input id=q placeholder='kata kunci …'></label>\n")
         .append("<label>tahap <select id=stage><option value=''>semua</option><option>KONFIG</option><option>CDN</option><option>SDK</option><option>LOGIN</option><option>GAME</option><option>SISTEM</option><option>LOGGER</option></select></label>\n")
         .append("<label>port <select id=port><option value=0>semua</option><option value=9527>9527 konfig</option><option value=9528>9528 CDN</option>")
         .append("<option value=5050>5050 login</option><option value=5051>5051 game</option><option value=9530>9530 SDK</option><option value=4000>4000 logger</option></select></label>\n")
         .append("<label>status <select id=st><option value=''>semua</option><option value=2>sukses (2xx)</option><option value=4>gagal (4xx)</option><option value=5>rusak (5xx)</option><option value=e>bermasalah</option></select></label>\n")
         .append("<label>jumlah <select id=limit><option>100</option><option selected>500</option><option>2000</option><option value=0>semua buffer</option></select></label>\n")
         .append("</div>\n")

         .append("<div id=crashbn></div>\n")
         .append("<div id=notices></div>\n")
         .append("<div id=stream>")
         .append("<div id=banner>╔══════════════════════════════════════════════════════════╗\n")
         .append("║  DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v8      ║\n")
         .append("║  console log real-time · http://127.0.0.1:4000            ║\n")
         .append("╚══════════════════════════════════════════════════════════╝\n")
         .append("<span class=dim>server berjalan — log dari semua layanan mengalir otomatis (pembaruan tiap 1,2 dtk)</span></div>\n")
         .append("<div id=tb></div><div id=tail><span id=pausemark></span><span id=cursor> </span></div></div>\n")

         .append("<div id=ftr>")
         .append("<button id=cpt class=lbtn title='Salin log (teks ala server)'>📋</button>")
         .append("<button id=cpjs class=lbtn title='Salin JSON log'>📄</button>")
         .append("<button id=dl class=lbtn title='Unduh arsip NDJSON'>💾</button>")
         .append("<span id=sum>…</span></div>\n")

         .append("<div id=toast></div>\n")
         .append("<div id=modal><div id=modalbox><div id=modalhead><span id=modaltitle></span><span><button id=modalcp class=lbtn title='Salin isi detail'>📋</button> <button id=modalx class=lbtn title='Tutup detail'>✖️</button></span></div><pre id=modalpre></pre></div></div>\n")

         .append("<script>\n")
         .append(Js())
         .append("</script>\n")
         .append("</body></html>");
        return b.toString();
    }

    private static String Js() {
        StringBuilder s = new StringBuilder(24576);
        s.append("var S={since:0,view:'all',stage:'',st:'',q:'',limit:500,auto:true,port:0};\n")
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
         .append("/* ---- tahap, komponen, level, detail (SERVER PREMIUM v8) ---- */\n")
         .append("function stageOf(e){\n")
         .append(" if(e.kind=='BOOT'||e.kind=='SYS'||e.kind=='EXTRACT')return 'SISTEM';\n")
         .append(" if(e.kind=='CRASH')return 'SISTEM';\n")
         .append(" if(e.port==9527)return 'KONFIG'; if(e.port==9528)return 'CDN';\n")
         .append(" if(e.port==9530)return 'SDK'; if(e.port==5050)return 'LOGIN';\n")
         .append(" if(e.port==5051)return 'GAME'; return 'LOGGER'}\n")
         .append("function compOf(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='BOOT'||k=='SYS'||k=='EXTRACT'||k=='CRASH'||k=='LOGCAT')return 'CORE';\n")
         .append(" if(e.port==9527)return 'CONFIG'; if(e.port==9528)return 'CDN';\n")
         .append(" if(e.port==9530)return 'SDK'; if(e.port==5050)return 'LOGIN';\n")
         .append(" if(e.port==5051)return 'GAME'; return 'MONITOR'}\n")
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
         .append("/* ---- deskripsi VERBOSE ala server asli (v8 — bukan output minimalis) ---- */\n")
         .append("function describe(e){\n")
         .append(" var p=pathOf(e),k=e.kind||'HTTP',m=e.method||'',st=e.status||0,extra=e.extra||'';\n")
         .append(" if(k=='CRASH')return '<b>CRASH terdeteksi</b> — proses game berhenti mendadak, trace lengkap tersimpan di logs/'+esc(p)+(e.error?'<br><span class=f>'+esc(e.error)+'</span>':'');\n")
         .append(" if(k=='BOOT')return '<b>Server engine berjalan</b> — '+esc(p);\n")
         .append(" if(k=='SYS'&&m=='BIND')return (st==0?'<b>Layanan diaktifkan</b> — ':'<span class=f><b>Layanan GAGAL</b></span> — ')+esc(p);\n")
         .append(" if(k=='SYS'&&m=='OVERLAY')return '<b>Izin overlay</b> — '+esc(p);\n")
         .append(" if(k=='SYS'){var t=esc(p);if(/gagal|ditolak|rusak/i.test(p))t='<span class=f>'+t+'</span>';return t}\n")
         .append(" if(k=='EXTRACT')return '<b>Ekstraksi aset game</b> — '+esc(p);\n")
         .append(" if(k=='LOGCAT')return '<b>Rekaman logcat</b> — diagnostik otomatis sistem (aman diabaikan)';\n")
         .append(" if(k=='WS'){\n")
         .append("  var evw=evName(e.extra);\n")
         .append("  if(evw&&evw.a=='enterGame')return (e.respPreview&&e.respPreview.indexOf('ret\\\":0')>=0)?'<b>Pemain masuk server game</b> — data karakter lengkap diterima ('+ms2(e.respLen||0)+', ret=0)':'Pemain masuk server — data '+ms2(e.respLen||0)+' diterima';\n")
         .append("  if(evw)return 'Pesan protokol '+(e.port==5050?'login':'game')+' diteruskan — event <b>'+esc(evw.a)+'</b>'+(evw.ack?' — dijawab server (ACK 43)':' — dikirim client (EVENT 42)');\n")
         .append("  if(e.respPreview=='pong'||extra.indexOf('ping→pong')>=0)return 'Heartbeat koneksi — ping→pong normal, sesi tetap hidup';\n")
         .append("  return 'Koneksi WebSocket '+(e.port==5050?'login':'game')+' — '+esc(extra.substring(0,120))}\n")
         .append(" if(e.port==9527){\n")
         .append("  if(p.indexOf('setting_BS')>=0)return '<b>Konfigurasi game dimuat</b> (setting_BS_Android.bin) — seluruh alamat server dialihkan ke 127.0.0.1 (server lokal)';\n")
         .append("  if(p.indexOf('com_db_local')>=0)return 'Konfigurasi aplikasi dibaca (com_db_local) — isi kosong, persis perilaku server asli';\n")
         .append("  return 'Konfigurasi dibaca — '+esc(p)}\n")
         .append(" if(e.port==9528){\n")
         .append("  if(p.indexOf('base.version')>=0)return '<b>Versi dasar diperiksa</b> — terkirim '+esc((e.respPreview||'').trim()||'?')+' (resource lokal dipakai, tanpa pembaruan)';\n")
         .append("  if(p.indexOf('resource.version')>=0)return '<b>Versi resource diperiksa</b> — terkirim '+esc((e.respPreview||'').trim()||'?')+' (aset lokal sinkron)';\n")
         .append("  if(p.indexOf('clientversion')>=0)return '<b>Versi client diperiksa</b> — terkirim '+esc((e.respPreview||'').trim()||'?')+' (popup pengingat tidak muncul — persis server asli)';\n")
         .append("  if(p.indexOf('upgrade.json')>=0)return '<b>Daftar paket pembaruan dibaca</b> — tidak ada pembaruan yang diperlukan';\n")
         .append("  if(p.indexOf('size.json')>=0)return '<b>Ukuran paket pembaruan dibaca</b> — resource sinkron dengan CDN lokal';\n")
         .append("  if(p.indexOf('all.zip')>=0)return '<b>Paket pembaruan diunduh</b> — '+ms2(e.respLen||0)+' dalam '+e.durMs+' ms (CDN lokal)';\n")
         .append("  if(p.indexOf('serversetting')>=0)return '<b>Alamat server login dibaca</b> (serversetting) — login server diarahkan ke 127.0.0.1:9528';\n")
         .append("  if(p.indexOf('index-native')>=0)return '<b>UI game dimuat</b> (index-native.html) — mesin Egret menyiapkan tampilan';\n")
         .append("  if(p.indexOf('manifest.json')>=0)return 'Manifest game dibaca — daftar modul game dimuat';\n")
         .append("  if(/\\.js($|\\?)/.test(p))return 'Skrip game dikirim — '+esc(p.split('/').pop().substring(0,44))+' ('+ms2(e.respLen||0)+' dalam '+e.durMs+' ms)';\n")
         .append("  if(p.indexOf('/resource/')>=0)return 'Aset game dikirim — '+esc(p.split('/').pop().substring(0,44))+' ('+ms2(e.respLen||0)+' dalam '+e.durMs+' ms)';\n")
         .append("  return 'CDN lokal: '+esc(p)}\n")
         .append(" if(e.port==9530){\n")
         .append("  var er=sdkErr(e),bad=(e.respPreview||'').indexOf('\\\"result\\\":false')>=0;\n")
         .append("  if(st>=400||bad){var nm=epName(p);return '<span class=f><b>'+nm+' GAGAL</b></span> — '+errWhy(er.id,er.msg)+(er.id?' (error id '+er.id+')':'')}\n")
         .append("  if(p.indexOf('/v1/system/init')>=0)return '<b>SDK inisialisasi selesai</b> — kontrak init.json penuh diterima, layar login disiapkan sesuai loginTypes';\n")
         .append("  if(p.indexOf('registerVisitor')>=0)return '<b>Registrasi akun tamu berhasil</b> — uid '+esc(uin(extra))+' diterbitkan, token tersimpan permanen di database lokal';\n")
         .append("  if(p.indexOf('registerUser')>=0)return '<b>Pendaftaran akun baru berhasil</b> — akun '+esc(una(extra))+' dibuat dan tersimpan permanen';\n")
         .append("  if(p.indexOf('loginByName')>=0)return '<b>Login akun berhasil</b> — akun '+esc(una(extra))+' terautentikasi (email/password valid)';\n")
         .append("  if(p.indexOf('autoLogin')>=0)return '<b>Login otomatis berhasil</b> — akun '+esc(una(extra))+' terautentikasi memakai sesi tersimpan';\n")
         .append("  if(p.indexOf('getNotice')>=0)return 'Pengumuman SDK diperiksa — tidak ada notifikasi untuk pemain';\n")
         .append("  if(p.indexOf('setGameRoleInfo')>=0)return 'Data karakter pemain dilaporkan ke SDK — profil game tersinkron';\n")
         .append("  if(p.indexOf('ckRegVistor')>=0)return '<b>Kode migrasi diperiksa</b> — isReged:0, persis perilaku server asli (belum terdaftar)';\n")
         .append("  return epName(p)+' — sukses (kontrak SDK dipenuhi)'}\n")
         .append(" if(e.port==5050){\n")
         .append("  if(p.indexOf('loginchecknative')>=0)return '<b>Tiket login terverifikasi</b> (loginchecknative) — akses game diberikan, sesi dilanjutkan ke server game';\n")
         .append("  if((e.path||'').indexOf('transport=polling')>=0&&e.respPreview&&e.respPreview.indexOf(':0{')>=0)return '<b>Handshake server login diterima</b> — sesi socket dibuat (EIO=3, pingInterval 25 dtk)';\n")
         .append("  var ev=evName(e.extra);if(ev)return 'Pesan login diteruskan — event <b>'+esc(ev.a)+'</b>'+(ev.ack?' — dijawab server (ACK 43)':' — dikirim client (EVENT 42)');\n")
         .append("  if(extra.indexOf('ping→pong')>=0)return 'Heartbeat server login — ping→pong normal';\n")
         .append("  return 'Server login: '+esc(p.substring(0,60))}\n")
         .append(" if(e.port==5051){\n")
         .append("  var ev=evName(e.extra);\n")
         .append("  if(ev&&ev.a=='enterGame')return (e.respPreview&&e.respPreview.indexOf('ret\\\":0')>=0)?'<b>Pemain masuk server game</b> — data karakter lengkap diterima ('+ms2(e.respLen||0)+', ret=0)':'Pemain masuk server — data '+ms2(e.respLen||0)+' diterima';\n")
         .append("  if(ev)return 'Pesan game diteruskan — event <b>'+esc(ev.a)+'</b>'+(ev.ack?' — dijawab server (ACK 43)':' — dikirim client (EVENT 42)');\n")
         .append("  return 'Server game: '+esc(p.substring(0,60))}\n")
         .append(" return esc(p)}\n")
         .append("function uin(x){var m=/uid[=\\\":]+([0-9]+)/.exec(''+x);return m?m[1]:'?'}\n")
         .append("function una(x){var m=/username[=\\\":\\s]+([A-Za-z0-9_]+)/.exec(''+x);return m?m[1]:'tamu'}\n")
         .append("function evName(extra){var s=''+(extra||'');var i=s.indexOf('EVENT: 42');if(i<0)return null;\n")
         .append(" var m=/action\":\"([A-Za-z]+)/.exec(s.substring(i));var ack=s.indexOf('ACK: 43',i)>=0;\n")
         .append(" return m?{a:m[1],ack:ack}:{a:'?',ack:ack}}\n")
         .append("/* ---- level ala log server asli: INFO/DEBUG/WARN/ERROR ---- */\n")
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
         .append(" if(k=='CRASH')return ['ERROR','r'];\n")
         .append(" if(k=='LOGCAT')return ['DEBUG','d'];\n")
         .append(" if(k=='SYS'&&m=='OVERLAY')return ['INFO','i'];\n")
         .append(" if(isProblem(e))return (e.status||0)>=500?['ERROR','r']:['WARN','w'];\n")
         .append(" if((e.status||0)>=300)return ['WARN','w'];\n")
         .append(" if((e.extra||'').indexOf('ping→pong')>=0||e.respPreview=='pong')return ['DEBUG','d'];\n")
         .append(" if(k=='HTTP'||k=='WS'){\n")
         .append("  if(e.port==9527&&p.indexOf('com_db_local')>=0)return ['DEBUG','d'];\n")
         .append("  if(e.port==9528&&(p.indexOf('/resource/')>=0||/\\.js($|\\?)/.test(p)||p.indexOf('manifest.json')>=0))return ['DEBUG','d'];\n")
         .append("  return ['INFO','g']}\n")
         .append(" return ['INFO','g']}\n")
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
         .append("function detailOf(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='HTTP'){return (e.method||'GET')+' '+pathOf(e)+' · status '+(e.status||0)+' · req '+fmtB(e.reqLen||0)+' · resp '+fmtB(e.respLen||0)+(e.durMs?' · '+durStr(e):'')}\n")
         .append(" if(k=='WS'){return 'socket.io port '+e.port+' · resp '+fmtB(e.respLen||0)+(e.durMs?' · '+durStr(e):'')}\n")
         .append(" return 'port '+e.port}\n")
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
         .append("function durStr(e){if(!e.durMs)return '';return e.durMs>=1000?(e.durMs/1000).toFixed(1)+'s':e.durMs+'ms'}\n")
         .append("function lnHtml(e){\n")
         .append(" var lv=levelOf(e),comp=compOf(e),d=describe(e),er=e.error||'';\n")
         .append(" var ec=lv[1]=='r'?'r':lv[1]=='w'?'w':lv[1]=='d'?'d':'';\n")
         .append(" return '<div class=\"ln'+(lv[1]=='r'?' bad':'')+'\" data-seq=\"'+e.seq+'\">'\n")
         .append("  +'<span class=sq>#'+e.seq+'</span>'\n")
         .append("  +'<span class=tm>'+esc((e.time||'').substring(11))+'</span>'\n")
         .append("  +'<span class=\"lv l-'+lv[1]+'\">'+lv[0]+'</span>'\n")
         .append("  +'<span class=\"cp c-'+comp+'\">['+comp+']</span>'\n")
         .append("  +'<span class=\"ev'+(ec?' '+ec:'')+'\">'+d+(er?'<br><span class=f>ERROR: '+esc(er.length>110?er.substring(0,110)+'…':er)+'</span>':'')+'</span>'\n")
         .append("  +'<div class=dt>↳ '+esc(detailOf(e))+'</div></div>'}\n")
         .append("function important(e){var p=pathOf(e);\n")
         .append(" if((e.kind||'')=='CRASH')return 'CRASH — '+plain(describe(e));\n")
         .append(" if(e.status>=400)return 'GAGAL — '+plain(describe(e));\n")
         .append(" if(e.port==9530&&p.indexOf('registerVisitor')>=0)return 'AKUN — '+plain(describe(e));\n")
         .append(" if(e.port==5050&&p.indexOf('loginchecknative')>=0)return 'LOGIN — '+plain(describe(e));\n")
         .append(" if(e.port==5051&&(e.extra||'').indexOf('enterGame')>=0)return 'GAME — '+plain(describe(e));\n")
         .append(" if(e.port==9528&&p.indexOf('index-native')>=0)return 'UI — '+plain(describe(e));\n")
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
         .append(" if(n==0){b.textContent='NORMAL';b.className=''}\n")
         .append(" else{b.textContent=n+' MASALAH';b.className='bad'}}\n")
         .append("function renderCrash(){fetch('/crash').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var w=$('crashbn');if(!j||!j.latest){w.style.display='none';w.innerHTML='';return}\n")
         .append(" if(j.latest.name==CRASHLATEST&&w.style.display=='block')return;\n")
         .append(" CRASHLATEST=j.latest.name;var t=j.latest.text||'';\n")
         .append(" w.innerHTML='<div class=ch>CRASH TERTANGKAP — '+esc(j.latest.name)+' ('+Math.round(j.latest.bytes/1024)+' KB)</div><div class=cd>crash Java di dalam proses game (termasuk kode SDK/game) — terekam CrashHook. Salin trace untuk analisis akar.</div><pre>'+esc(t)+'</pre><button id=cpcrash class=lbtn title=\"Salin trace crash\">📋</button>';\n")
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
         .append("/* ---- salin log = TEKS PERSIS FORMAT SERVER ASLI (v8) ---- */\n")
         .append("function copyTable(){var v=visible();var out=[];\n")
         .append(" for(var i=0;i<v.length;i++){var e=v[i],lv=levelOf(e);\n")
         .append("  out.push((e.time||'')+'  '+lv[0]+'  ['+compOf(e)+']  '+plain(describe(e)));\n")
         .append("  out.push('        ↳ '+plain(detailOf(e)));\n")
         .append("  if(e.error)out.push('        ERROR: '+(''+e.error).substring(0,120))}\n")
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
         .append(" else openModal('Salin manual — Ctrl+C',t)}\n")
         .append("function openModal(t2,t){MODTXT=t;$('modaltitle').textContent=t2;$('modalpre').textContent=t;$('modal').style.display='block';\n")
         .append(" var r=document.createRange();r.selectNodeContents($('modalpre'));\n")
         .append(" var sc=window.getSelection();sc.removeAllRanges();try{sc.addRange(r)}catch(x){}}\n")
         .append("var tt=null;\n")
         .append("function toast(m,err){var el=$('toast');el.textContent=m;el.className=err?'err':'';el.style.display='block';clearTimeout(tt);tt=setTimeout(function(){el.style.display='none'},2600)}\n")
         .append("$('stream').addEventListener('click',function(ev){\n")
         .append(" var tr=ev.target.closest?ev.target.closest('div.ln'):null;if(!tr)return;\n")
         .append(" var e=ENT[tr.getAttribute('data-seq')];if(!e)return;\n")
         .append(" var d=detOf(e);var s2='';\n")
         .append(" for(var i=0;i<d.length;i++)s2+='===== '+d[i].l+' =====\\n'+d[i].t+'\\n\\n';\n")
         .append(" openModal('#'+e.seq+' · '+e.time+' · port '+e.port,s2)});\n")
         .append("$('modalx').onclick=function(){$('modal').style.display='none'};\n")
         .append("$('modalcp').onclick=function(){copyText(MODTXT)};\n")
         .append("$('modal').onclick=function(ev){if(ev.target.id=='modal')$('modal').style.display='none'};\n")
         .append("function sync(){S.st=$('st').value;S.q=$('q').value.toLowerCase();S.limit=parseInt($('limit').value);S.port=parseInt($('port').value)}\n")
         .append("function reset(){$('tb').innerHTML='';ENT={};S.since=0;LASTSUM='';$('notices').innerHTML='';refreshStatus()}\n")
         .append("function setView(v){S.view=v;\n")
         .append(" $('vimp').className='lbtn'+(v=='penting'?' on':'');\n")
         .append(" $('vmal').className='lbtn'+(v=='masalah'?' on':'');\n")
         .append(" $('vall').className='lbtn'+(v=='all'?' on':'');\n")
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
         .append(" $('pause').textContent=S.auto?'⏸️':'▶️';\n")
         .append(" $('cursor').style.display=S.auto?'inline-block':'none';\n")
         .append(" $('pausemark').textContent=S.auto?'':'‖ jeda — pembaruan otomatis berhenti'};\n")
         .append("$('guidebtn').onclick=function(){\n")
         .append(" openModal('PANDUAN SINGKAT',\n")
         .append("  'Alur normal boot:\\n  konfigurasi 9527 -> CDN 9528 -> SDK 9530 -> layar login\\n  -> TAMU / DAFTAR / LOGIN -> verifikasi 5050 -> game 5051.\\n\\nWarna level log (ala server asli):\\n  INFO hijau = alur normal · DEBUG cyan = traffic halus\\n  (aset game, heartbeat, konfigurasi kosong)\\n  WARN kuning = perlu perhatian (4xx/redirect/product)\\n  ERROR merah = gagal / crash\\n\\nIzin overlay OFF = NORMAL (game asli tidak memerlukan overlay;\\nSDK tetap berjalan normal).\\n\\nLayar login SDK: TAMU = langsung main; DAFTAR = akun baru;\\nLOGIN = akun terdaftar.\\n\\nCRASH: banner merah di atas console menampilkan trace lengkap —\\nsalin dan kirim untuk analisis akar.\\n\\nKlik baris log = detail REQUEST/RESPON/EXTRA (JSON rapi).\\nTombol kiri bawah: salin log ala server / salin JSON / unduh arsip.\\nFilter lanjutan: tombol gerigi di kanan atas.')};\n")
         .append("$('reset').onclick=reset;\n")
         .append("$('cpt').onclick=copyTable;\n")
         .append("$('cpjs').onclick=copyJSON;\n")
         .append("$('dl').onclick=function(){var a=document.createElement('a');a.href='/download?latest=1';a.download='';document.body.appendChild(a);a.click();document.body.removeChild(a)};\n")
         .append("function note(c2,txt){var d=document.createElement('div');d.className='nt '+c2;d.textContent=txt;$('notices').appendChild(d)}\n")
         .append("function url(){return '/logs?mode=all&limit='+S.limit+'&since='+S.since}\n")
         .append("function uptimeStr(ms){var s2=Math.floor(ms/1000);return Math.floor(s2/3600)+'h'+Math.floor(s2%3600/60)+'m'+(s2%60)+'s'}\n")
         .append("function refreshStats(){fetch('/stats').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var ids=[['9527','konfig bin'],['9528','CDN game + login'],['5050','login socket.io'],['5051','server game'],['9530','API SDK'],['4000','logger']];\n")
         .append(" var h='LISTEN ';\n")
         .append(" for(var i=0;i<ids.length;i++){var cc=j.ports[ids[i][0]];\n")
         .append("  h+='<span class=\"'+(cc&&cc.requests>0?'up':'dw')+'\" title=\"'+ids[i][1]+'\">'+ids[i][0]+'</span> '}\n")
         .append(" h+='· uptime '+uptimeStr(j.uptimeMs)+' · seq '+j.seq;\n")
         .append(" if(j.disk&&j.disk.enabled)h+=' · arsip '+j.disk.file+' '+(j.disk.bytes>=1048576?(j.disk.bytes/1048576).toFixed(1)+'MB':Math.round(j.disk.bytes/1024)+'KB')+' · '+j.disk.lines+' baris';\n")
         .append(" if(j.overlay&&j.overlay.checked)h+=j.overlay.granted?' · overlay ON':' · overlay OFF (normal)';\n")
         .append(" h+=' · status: <b id=stbadge>…</b>';\n")
         .append(" $('stline').innerHTML=h;refreshStatus();renderCrash()}).catch(function(){})}\n")
         .append("function poll(){\n")
         .append(" if(!S.auto){setTimeout(poll,500);return}\n")
         .append(" fetch(url()).then(function(r){return r.json()}).then(function(j){\n")
         .append("  if(j.gap&&j.entries.length)note('nt-w','GAP: sebagian entri buffer terbuang — arsip disk tetap utuh (unduh arsip di kiri bawah)');\n")
         .append("  if(j.entries.length){append(j.entries);S.since=j.seqNewest}\n")
         .append("  var tot=(j.droppedGlobal||0)+(j.droppedPort||0);\n")
         .append("  if(tot>0&&tot!==lastDrop){lastDrop=tot;note('nt-e','buffer dropped '+tot+' — arsip disk tetap utuh')}\n")
         .append("  pollN++;if(pollN%3==0)refreshStats();\n")
         .append("  setTimeout(poll,1200);\n")
         .append(" }).catch(function(){setTimeout(poll,3000)})}\n")
         .append("document.title='DB LOCAL SERVER :'+location.port;\n")
         .append("sync();setView('all');refreshStats();poll();\n")
         .append("setTimeout(refreshStats,800);\n");
        return s.toString();
    }
}
