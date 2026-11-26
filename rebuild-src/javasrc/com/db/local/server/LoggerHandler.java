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
 * CONSOLE v13 — RAW SERVER LOG (Bagian 50). CONSOLE v14 — SERVER LOG RAPI
 * (Bagian 51; arahan owner: "masih banyak teks sampah, dan gak rapi . banyak
 * tombol juga tapi tombol sampah"): detail kolaps (klik utk isi penuh
 * verbatim), tombol hanya yang berfungsi (jeda/bersihkan/arsip/salin),
 * filter ringkas selalu tampil (cari + level + port), banner 1 baris,
 * extra server dibersihkan ke fakta protokol saja, JSON pretty-print.
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

    /* ================= console v15 — SERVER LOG BERSIH =================
     * Arahan owner (25 Nov 2026, kata-kata persis):
     *   "astaga di atas log itu masih banyak tombol sampah !!!"
     *   "log rinci jelas Natural !! masih ada log yg harus di klik baru
     *    muncul rincian !!"
     *   "lagi running local kalau mode pesawat kok notifikasi error network
     *    dasar gk logis"
     * PERUBAHAN v15 (data tetap MENTAH — NOL karangan):
     *  1. TOMBOL CUMA 2 dan dua-duanya berfungsi: CARI (filter teks) + JEDA
     *     (hentikan/lanjut auto-scroll). DIHAPUS TOTAL: tombol bersihkan,
     *     unduh arsip, salin, select level, select port, dan SELURUH baris
     *     chips status (12 chip tumpuk di atas log = yang owner sebut
     *     "tombol sampah"). Log tetap tersimpan utuh di arsip NDJSON disk.
     *  2. RINCIAN LANGSUNG TAMPIL (NOL klik): sub-baris headers / request /
     *     response / recv / send / data / error SEMUA langsung terbuka
     *     verbatim — tidak ada lagi "klik untuk buka", tidak ada chevron,
     *     tidak ada state kolaps. Yang panjang menggulir sendiri (max-height)
     *     supaya konsol tetap bisa dibaca.
     *  3. AIRPLANE MODE (classes.dex, bedah terpisah): QuickGame SDK cek
     *     NetworkInfo.isConnected sebelum init → AlertDialog "network error"
     *     padahal semua server sudah 127.0.0.1 — patch 4 unit di dex:
     *     feedtonight.hoboismrelationbelow + HWLoginActivity.onCreate
     *     (isConnected dipaksa true, null-check dilewati ke init).
     *  4. yang TIDAK berubah: NOL emoji (ikon SVG registry), baris utama =
     *     PERSIS protokol (METHOD full-path status · bytes · durasi), boot/
     *     crash/logcat pesan server apa adanya, bahasa log mengikuti server
     *     asli, poll incremental ?since=, banner CRASH (tanpa tombol salin),
     *     arsip NDJSON disk tetap jalan.
     */

    /* ================= console v16 — FILTER ERROR + SALIN =================
     * Arahan owner (kata-kata persis):
     *   "filter error gak ada , tombol salin untuk di kirim ke anda juga gak
     *    ada .."
     *   "dan itu kenapa loginserver malah sudah di kerjakan PADAHAL belum di
     *    kasih rencana kesana"
     * PERUBAHAN v16 (console SAJA — server/logic NOL perubahan):
     *  1. FILTER ERROR kembali (tombol oktagon merah): saat aktif hanya
     *     WARN/ERROR yang tampil (status >=400, error, result:false, CRASH)
     *     — berpasangan dgn filter cari. v15 menghapusnya; owner minta ada.
     *  2. TOMBOL SALIN kembali (ikon copy): menyalin SEMUA baris tersaring
     *     sebagai TEKS MENTAH protokol — time #seq port LEVEL COMP method
     *     path status bytes dur + sub-baris headers/request/response/recv/
     *     send/error/data verbatim — siap tempel ke chat dev. Clipboard:
     *     navigator.clipboard bila ada, fallback textarea+execCommand
     *     (aman WebView). NOL popup; konfirmasi lewat footer.
     *  3. Tombol total TETAP 4 dan SEMUA berfungsi: cari, filter error,
     *     salin, jeda. Ikon SVG, NOL emoji.
     *  4. LOGIN SERVER (5050/9528) TIDAK disentuh di v16 — ia aktif sejak
     *     v13 karena config game setting_BS_Android.bin (XOR "DragonBall")
     *     menunjuk loginServer=127.0.0.1 loginPort=9528 loginWebPort=5050;
     *     engine Egret menelepon sendiri saat boot dan SDK langsung auto
     *     login GUEST. Bukan pekerjaan baru — menunggu rencana owner.
     */

    /* ================= console v17 — URL PENUH, NOL PANGKAS ================
     * Arahan owner (kata-kata persis):
     *   "ini log apa gk jelas , masih ada yg di sembunyikan"
     *   "kalau log nampilin url socket misal http://127.0.01:<port>/socket.io/
     *    ?EIO=3&transport=polling&t=Q8Fuv yaaa jangan anda pangkas"
     *   "login server belum boleh di kerjakan!! hanya menentukan endpoint dan
     *    merencanakan strategi struktur foldernya dulu !!"
     * PERUBAHAN v17:
     *  1. URL PENUH di SETIAP baris log (server side, SocketIoHandler/
     *     StaticHandler/SdkHandler): scheme://host:port + path + query apa
     *     adanya — "WS sid=.." dan "GET /socket.io/?.." TELANJANG DIHAPUS.
     *     Contoh baris kini: WS http://127.0.0.1:5050/socket.io/?EIO=3&
     *     transport=websocket&sid=7fKrqxrieUn5FLqhUnyA 200.
     *  2. NOL pangkalan payload: POST packet (cap 1800) + ACK event (cut
     *     400 char) dihapus — packet & ACK tampil PENUH verbatim.
     *  3. Frame open ws-first yang dikirim server kini juga dilog (dulu
     *     tersembunyi).
     *  4. LOGIN SERVER NOL KERJA di v17 — hanya DOKUMEN RENCANA:
     *     endpoint + strategi struktur folder di docs/LOGIN-SERVER-PLAN.md
     *     (repo, bukan di APK). Menunggu keputusan owner sebelum eksekusi.
     *  5. Layout/ikon/tombol console TIDAK berubah (4 kontrol fungsional
     *     v16 tetap: cari · filter error · salin · jeda).
     */

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
         .append(".ev .dim{color:#5d7183}\n")
         .append(".ev .s2{color:#3ddc84;font-weight:800}.ev .s3{color:#ffb454;font-weight:800}.ev .s4{color:#ff8484;font-weight:800}.ev .s5{color:#ff5d5d;font-weight:800}.ev .s0{color:#8fa0b0}\n")
         .append(".ev.w{color:#ffd9a0}.ev.r{color:#ffb3b3}.ev.d{color:#8fa3b5}\n")
         .append(".dtl{margin-top:7px;border-left:2px solid #1b2836;padding-left:8px;display:flex;flex-direction:column;gap:5px}\n")
         .append(".ln.r .dtl{border-left-color:#3a2026}\n")
         .append(".sub{display:flex;gap:7px;align-items:flex-start;font-family:'JetBrains Mono','Roboto Mono',Menlo,Consolas,'Droid Sans Mono',monospace;font-size:11.5px;color:#93a7b9}\n")
         .append(".sub>.ic{width:12px;height:12px;color:#4c5f70;flex:none;margin-top:2.5px}\n")
         .append(".lbl{color:#647a8d;font-weight:800;flex:none}\n")
         .append(".s-req .lbl{color:#4fc3dd}\n")
         .append(".s-res .lbl{color:#3ddc84}\n")
         .append(".s-ext .lbl{color:#d9c377}\n")
         .append(".s-err .lbl{color:#ff8484}\n")
         .append(".txt{white-space:pre-wrap;word-break:break-word;min-width:0;max-height:300px;overflow-y:auto;line-height:1.55}\n")
         .append(".txt::-webkit-scrollbar{width:8px;height:8px}\n")
         .append(".txt::-webkit-scrollbar-thumb{background:#223143;border-radius:5px}\n")
         .append("#cursor{display:inline-block;width:8px;height:14px;background:#3ddc84;vertical-align:-2px;animation:bl 1.1s step-end infinite;box-shadow:0 0 8px rgba(61,220,132,.6)}\n")
         .append("@keyframes bl{50%{opacity:0}}\n")
         .append("#pausemark{color:#64778a;font-style:italic}\n")
         .append("#ftr{position:relative;z-index:2;padding:9px 18px;border-top:1px solid #16222f;background:rgba(8,14,22,.94);display:flex;gap:8px;align-items:center;box-shadow:0 -6px 24px rgba(0,0,0,.4)}\n")
         .append("#sum{color:#64778a;font-size:11.5px;flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}\n")
         .append("#sum b{color:#cfe0ef;font-weight:700}\n")
         .append("@media(max-width:680px){#stream{padding:10px 10px 14px}#hdr{padding:9px 12px}#ftr{padding:8px 12px}#notices{padding:0 12px}#versub{display:none}.sq{display:none}.ln{padding:8px 9px 8px 11px;border-radius:8px}.dtl{padding-left:6px}.ev{font-size:12px}.rmeta{font-size:10px}#banner{padding:10px 13px}input{min-width:70px;max-width:150px}}\n")
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
         .append("<div id=banner><span id=blogo><span data-ic=flame></span></span><div><div class=b1>DB LOCAL SERVER · com.db.local · MOD-LOCALSERVER v19</div><div class=bl><span data-ic=terminal></span>raw server log — http://127.0.0.1:4000 — URL penuh · nol disembunyikan · semua rincian tampil langsung</div></div></div>\n")
         .append("<div id=tb></div><div id=tail><span id=pausemark></span><span id=cursor> </span></div></div>\n")

         .append("<div id=ftr><span id=sum>…</span></div>\n")

         .append("<script>\n")
         .append(Js())
         .append("</script>\n")
         .append("</body></html>");
        return b.toString();
    }

    private static String Js() {
        StringBuilder s = new StringBuilder(22000);
        s.append("var S={since:0,q:'',auto:true,err:false};\n")
         .append("var ENT={},MAXC=3000,pollN=0,LASTSUM='',CRASHLATEST='',lastDrop=-1;\n")
         .append("function $(id){return document.getElementById(id)}\n")
         .append("function esc(x){return (x==null?'':''+x).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         .append("/* ---- REGISTRY ICON SVG (chrome UI — ikon sungguhan, NOL emoji) ---- */\n")
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
         .append("function ic(n){return IC[n]||IC.info}\n")
         .append("function paintIcons(){var all=document.querySelectorAll('[data-ic]');\n")
         .append(" for(var i=0;i<all.length;i++)all[i].innerHTML=ic(all[i].getAttribute('data-ic'));\n")
         .append(" $('pause').innerHTML=ic(S.auto?'pause':'play');\n")
         .append(" $('errf').innerHTML=ic('octagon');\n")
         .append(" $('copy').innerHTML=ic('copy')}\n")
         .append("function compOf(e){\n")
         .append(" var k=e.kind||'';\n")
         .append(" if(k=='BOOT'||k=='SYS'||k=='EXTRACT'||k=='CRASH'||k=='LOGCAT')return {name:'CORE',icon:'cpu'};\n")
         .append(" if(e.port==9527)return {name:'CONFIG',icon:'settings'};\n")
         .append(" if(e.port==9528)return {name:'CDN',icon:'globe'};\n")
         .append(" if(e.port==9530)return {name:'SDK',icon:'card'};\n")
         .append(" if(e.port==5050)return {name:'LOGIN',icon:'shield'};\n")
         .append(" if(e.port==5051)return {name:'GAME',icon:'gamepad'};\n")
         .append(" return {name:'MONITOR',icon:'radio'}}\n")
         .append("function fmtB(n){n=n||0;return n>=1048576?(n/1048576).toFixed(1)+' MB':n>=1024?Math.round(n/1024)+' KB':n+' B'}\n")
         .append("function durStr(e){if(e.durMs==null)return '';return e.durMs>=1000?(e.durMs/1000).toFixed(1)+'s':e.durMs+'ms'}\n")
         .append("/* ---- baris utama = PERSIS protokol (method full-path status bytes dur) ---- */\n")
         .append("function stHtml(n){n=n||0;var c='s0';if(n>=500)c='s5';else if(n>=400)c='s4';else if(n>=300)c='s3';else if(n>=200)c='s2';return '<span class=\"'+c+'\">'+n+'</span>'}\n")
         .append("function mainLine(e){\n")
         .append(" var k=e.kind||'HTTP',m=e.method||'',p=e.path||'';\n")
         .append(" if(k=='CRASH')return '<b>CRASH</b> '+esc(p);\n")
         .append(" if(k=='BOOT'||k=='EXTRACT'||k=='LOGCAT')return '<b>'+esc(k)+'</b> '+esc(p);\n")
         .append(" if(k=='SYS')return '<b>'+esc(m||'SYS')+'</b> '+esc(p);\n")
         .append(" if(m=='WS'){\n")
         .append("  var t='<b>WS</b> '+esc(p);\n")
         .append("  if(p.indexOf('ping->pong')>=0||p.indexOf('close')>=0)return t;\n")
         .append("  return t+' '+stHtml(e.status||0)}\n")
         .append(" var t='<b>'+esc(m||'GET')+'</b> '+esc(p)+' '+stHtml(e.status||0);\n")
         .append(" if((e.respLen||0)>0)t+=' <span class=dim>· '+fmtB(e.respLen)+'</span>';\n")
         .append(" t+=' <span class=dim>· '+durStr(e)+'</span>';\n")
         .append(" return t}\n")
         .append("/* ---- v15: rincian LANGSUNG TAMPIL — NOL klik, NOL kolaps ----\n")
         .append("   isi capture mentah ditampilkan utuh verbatim; yang panjang\n")
         .append("   menggulir sendiri (max-height .txt). ---- */\n")
         .append("function subRow(cls,icn,label,val,n){\n")
         .append(" var raw=''+val;if(n==null||isNaN(n))n=raw.length;\n")
         .append(" return '<div class=\"sub '+cls+'\">'+ic(icn)+'<span class=lbl>'+label+'</span>'\n")
         .append("  +'<span class=txt>'+esc(raw)+'</span></div>'}\n")
         .append("function subHtml(e){\n")
         .append(" var k=e.kind||'HTTP',m=e.method||'',parts=[];\n")
         .append(" if(k=='HTTP'||(k=='WS'&&m!='WS')){\n")
         .append("  if(e.headers)parts.push(subRow('s-det','corner','headers:',e.headers));\n")
         .append("  if(e.reqPreview)parts.push(subRow('s-req','up','request:',e.reqPreview,e.reqLen));\n")
         .append("  else if((e.reqLen||0)>0)parts.push(subRow('s-req','up','request:','('+fmtB(e.reqLen)+' binary)',e.reqLen));\n")
         .append("  if(e.respPreview)parts.push(subRow('s-res','down','response:',e.respPreview,e.respLen));\n")
         .append("  else if((e.respLen||0)>0)parts.push(subRow('s-res','down','response:','('+fmtB(e.respLen)+' binary)',e.respLen))}\n")
         .append(" if(k=='WS'&&m=='WS'){\n")
         .append("  if(e.reqPreview)parts.push(subRow('s-req','up','recv:','('+fmtB(e.reqLen||0)+') '+e.reqPreview,e.reqLen));\n")
         .append("  else if((e.reqLen||0)>0)parts.push(subRow('s-req','up','recv:','('+fmtB(e.reqLen)+' binary)',e.reqLen));\n")
         .append("  if(e.respPreview)parts.push(subRow('s-res','down','send:','('+fmtB(e.respLen||0)+') '+e.respPreview,e.respLen))}\n")
         .append(" if(e.error)parts.push(subRow('s-err','x','error:',e.error));\n")
         .append(" if(e.extra)parts.push(subRow('s-ext','tag','data:',e.extra));\n")
         .append(" if(parts.length==0)return '';\n")
         .append(" return '<div class=dtl>'+parts.join('')+'</div>'}\n")
         .append("/* ---- level ala server: INFO/DEBUG/WARN/ERROR ---- */\n")
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
         .append(" if((e.level||'')=='ERROR')return {word:'ERROR',cls:'r',icon:'x'};\n")
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
         .append("function plainLine(e){\n")
         .append(" var k=e.kind||'HTTP',m=e.method||'',p=e.path||'';\n")
         .append(" if(k=='CRASH')return 'CRASH '+p;\n")
         .append(" if(k=='BOOT'||k=='EXTRACT'||k=='LOGCAT')return k+' '+p;\n")
         .append(" if(k=='SYS')return (m||'SYS')+' '+p;\n")
         .append(" if(m=='WS')return 'WS '+p+((p.indexOf('ping->pong')>=0||p.indexOf('close')>=0)?'':' '+(e.status||0));\n")
         .append(" return (m||'GET')+' '+p+' '+(e.status||0)+((e.respLen||0)>0?' '+fmtB(e.respLen):'')+(e.durMs!=null?' '+durStr(e):'')}\n")
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
         .append(" if(S.err){var w=levelOf(e).word;if(w!='WARN'&&w!='ERROR')return false}\n")
         .append(" if(S.q){var t=((e.method||'')+' '+(e.path||'')+' '+(e.reqPreview||'')+' '+(e.respPreview||'')+' '+(e.extra||'')+' '+(e.error||'')).toLowerCase();if(t.indexOf(S.q)<0)return false}\n")
         .append(" return true}\n")
         .append("function applyFilter(){var rs=$('tb').children;\n")
         .append(" for(var i=0;i<rs.length;i++){var e=ENT[rs[i].getAttribute('data-seq')];\n")
         .append("  rs[i].style.display=(e&&!pass(e))?'none':''}}\n")
         .append("function renderCrash(){fetch('/crash').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var w=$('crashbn');if(!j||!j.latest){w.style.display='none';w.innerHTML='';return}\n")
         .append(" if(j.latest.name==CRASHLATEST&&w.style.display=='block')return;\n")
         .append(" CRASHLATEST=j.latest.name;var t=j.latest.text||'';\n")
         .append(" w.innerHTML='<div class=ch>'+ic('octagon')+' CRASH — '+esc(j.latest.name)+' ('+Math.round(j.latest.bytes/1024)+' KB)</div><div class=cd>crash Java di proses game — laporan mentah:</div><pre>'+esc(t)+'</pre>';\n")
         .append(" w.style.display='block'}).catch(function(){})}\n")
         .append("function append(list){var tb=$('tb'),sc=$('stream');\n")
         .append(" var pin=sc.scrollTop+sc.clientHeight>=sc.scrollHeight-60;\n")
         .append(" var html='',im=null;\n")
         .append(" for(var i=0;i<list.length;i++){var e=list[i];ENT[e.seq]=e;\n")
         .append("  html+=lnHtml(e);var x=important(e);if(x)im=x}\n")
         .append(" if(html)tb.insertAdjacentHTML('beforeend',html);\n")
         .append(" if(im)LASTSUM=im;\n")
         .append(" var rs=tb.children;var over=rs.length-MAXC;\n")
         .append(" for(var j2=0;j2<over;j2++)tb.removeChild(rs[0]);\n")
         .append(" applyFilter();\n")
         .append(" if(LASTSUM)$('sum').innerHTML='<b>terakhir penting:</b> '+esc(LASTSUM);\n")
         .append(" if(pin)sc.scrollTop=sc.scrollHeight}\n")
         .append("/* ---- v16: 4 kontrol, SEMUA berfungsi: cari + filter error + salin + jeda ---- */\n")
         .append("$('q').oninput=function(){S.q=$('q').value.toLowerCase();applyFilter()};\n")
         .append("$('errf').onclick=function(){S.err=!S.err;\n")
         .append(" $('errf').classList.toggle('erron',S.err);applyFilter();\n")
         .append(" flashSum(S.err?'filter ERROR aktif — tampil warn/error saja':'filter ERROR mati — tampil semua')};\n")
         .append("function copyText(e){\n")
         .append(" var k=e.kind||'HTTP',subs=[];\n")
         .append(" var L=(e.time||'')+' #'+e.seq+' '+(e.port||0)+' '+levelOf(e).word+' '+compOf(e).name+' '+plainLine(e);\n")
         .append(" if(e.headers)subs.push('  headers: '+e.headers);\n")
         .append(" if(k=='HTTP'||(k=='WS'&&(e.method||'')!='WS')){\n")
         .append("  if(e.reqPreview)subs.push('  request: '+e.reqPreview);\n")
         .append("  else if((e.reqLen||0)>0)subs.push('  request: ('+fmtB(e.reqLen)+' binary)');\n")
         .append("  if(e.respPreview)subs.push('  response: '+e.respPreview);\n")
         .append("  else if((e.respLen||0)>0)subs.push('  response: ('+fmtB(e.respLen)+' binary)')}\n")
         .append(" if(k=='WS'&&(e.method||'')=='WS'){\n")
         .append("  if(e.reqPreview)subs.push('  recv: ('+fmtB(e.reqLen||0)+') '+e.reqPreview);\n")
         .append("  else if((e.reqLen||0)>0)subs.push('  recv: ('+fmtB(e.reqLen)+' binary)');\n")
         .append("  if(e.respPreview)subs.push('  send: ('+fmtB(e.respLen||0)+') '+e.respPreview)}\n")
         .append(" if(e.error)subs.push('  error: '+e.error);\n")
         .append(" if(e.extra)subs.push('  data: '+e.extra);\n")
         .append(" return L+(subs.length?'\\n'+subs.join('\\n'):'')}\n")
         .append("function flashSum(msg){$('sum').innerHTML='<b>'+esc(msg)+'</b>';\n")
         .append(" setTimeout(function(){if(LASTSUM)$('sum').innerHTML='<b>terakhir penting:</b> '+esc(LASTSUM)},4000)}\n")
         .append("function doCopy(){var rows=$('tb').children,out=[];\n")
         .append(" for(var i=0;i<rows.length;i++){var e=ENT[rows[i].getAttribute('data-seq')];\n")
         .append("  if(e&&pass(e))out.push(copyText(e))}\n")
         .append(" if(!out.length){flashSum('kosong — tidak ada baris yang lolos filter');return}\n")
         .append(" var txt=out.join('\\n'),n=out.length;\n")
         .append(" var done=function(){flashSum('tersalin '+n+' baris — tempel ke chat dev')};\n")
         .append(" if(navigator.clipboard&&navigator.clipboard.writeText){\n")
         .append("  navigator.clipboard.writeText(txt).then(done,function(){fallbackCopy(txt,done)})}\n")
         .append(" else fallbackCopy(txt,done)}\n")
         .append("function fallbackCopy(txt,done){\n")
         .append(" var ta=document.createElement('textarea');ta.value=txt;\n")
         .append(" ta.style.position='fixed';ta.style.opacity='0';document.body.appendChild(ta);\n")
         .append(" ta.focus();ta.select();\n")
         .append(" try{if(document.execCommand('copy'))done();else flashSum('gagal salin — pakai /download arsip')}\n")
         .append(" catch(e){flashSum('gagal salin — pakai /download arsip')}\n")
         .append(" document.body.removeChild(ta)}\n")
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
