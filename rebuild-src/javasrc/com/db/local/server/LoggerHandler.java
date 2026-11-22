package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;

/**
 * Listener 4000 (fallback 4001 bila bind gagal) — logger/monitoring v3 (Bagian 18):
 *   GET /                    → dashboard HTML live (auto-refresh, filter port/kind/
 *                              search/error-only, klik baris = detail lengkap, arsip
 *                              NDJSON bisa diunduh, indikator gap + dropped)
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

    /* ================= dashboard ================= */

    private String dashboardHtml() {
        StringBuilder b = new StringBuilder(16384);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>db-local logger v3</title>\n")
         .append("<style>\n")
         .append("body{font-family:ui-monospace,Menlo,Consolas,monospace;background:#101418;color:#d7dde3;margin:0;padding:12px;font-size:13px}\n")
         .append("h2{color:#8fd48f;margin:4px 0}h3{color:#8fd48f;margin:10px 0 4px}\n")
         .append(".bar{background:#171d24;border:1px solid #2a333d;border-radius:6px;padding:8px 10px;margin:6px 0}\n")
         .append(".muted{color:#7d8894}.warn{color:#e8b34c}.err{color:#ef6a6a}.ok{color:#7fd17f}.ws{color:#6ec6d8}\n")
         .append("table{border-collapse:collapse;width:100%}\n")
         .append("th{background:#1b222b;color:#9fb0c0;text-align:left;padding:4px 8px;border:1px solid #2a333d;font-size:12px}\n")
         .append("td{padding:3px 8px;border:1px solid #232b34;font-size:12px;white-space:nowrap;max-width:520px;overflow:hidden;text-overflow:ellipsis}\n")
         .append("tr.row:hover{background:#1a2129;cursor:pointer}\n")
         .append("tr.det td{background:#0d1116;white-space:pre-wrap;max-width:none;font-size:12px}\n")
         .append("select,input,button{background:#1b222b;color:#d7dde3;border:1px solid #2a333d;border-radius:4px;padding:4px 8px;font-family:inherit;font-size:12px}\n")
         .append("button{cursor:pointer}button:hover{border-color:#4a5a6a}\n")
         .append("a{color:#6ec6d8}\n")
         .append("#tblwrap{max-height:60vh;overflow-y:auto;border:1px solid #2a333d;border-radius:6px}\n")
         .append("#tblwrap::-webkit-scrollbar{width:10px}#tblwrap::-webkit-scrollbar-thumb{background:#2a333d;border-radius:5px}\n")
         .append("</style></head><body>\n")
         .append("<h2>db-local mini-server — LOGGER v3 (live, arsip NDJSON disk)</h2>\n")

         .append("<div class='bar' id='statsbar'>memuat /stats …</div>\n")

         .append("<div class='bar'>\n")
         .append("<label>Mode <select id='mode'><option value='all'>ALL (lintas port)</option><option value='port'>PORT (satu port)</option></select></label> \n")
         .append("<label id='portsel'>Port <select id='port'>")
         .append("<option value='9527'>9527 config</option><option value='9528'>9528 CDN</option>")
         .append("<option value='5050'>5050 login</option><option value='9530'>9530 SDK</option>")
         .append("</select></label> \n")
         .append("<label>Kind <select id='kind'><option value=''>semua</option><option>HTTP</option><option>WS</option><option>BOOT</option><option>EXTRACT</option><option>SYS</option></select></label> \n")
         .append("<label>Cari <input id='q' size='18' placeholder='subset path/body'></label> \n")
         .append("<label><input type='checkbox' id='errs'> hanya error</label> \n")
         .append("<label>Limit <select id='limit'><option>200</option><option selected>500</option><option>2000</option><option value='0'>semua buffer</option></select></label> \n")
         .append("<button id='pause'>Jeda</button> <button id='reset'>Reset+Resync</button> \n")
         .append("<button onclick=\"fetch('/download').then(r=>r.json()).then(showFiles)\">Arsip NDJSON</button> \n")
         .append("<a href='/download?latest=1'>unduh terbaru</a> <a href='/stats'>/stats</a> <a href='/health'>/health</a>\n")
         .append("</div>\n")

         .append("<div class='bar' id='notices'></div>\n")

         .append("<div id='tblwrap'><table id='tbl'><thead><tr>")
         .append("<th>seq</th><th>waktu</th><th>port</th><th>kind</th><th>req</th><th>status</th><th>dur</th><th>reqLen</th><th>respLen</th>")
         .append("</tr></thead><tbody id='tb'></tbody></table></div>\n")

         .append("<div id='files' class='bar' style='display:none'></div>\n")

         .append("<h3>Cara baca (Bagian 18)</h3>")
         .append("<div class='bar muted'>")
         .append("Alur boot game: <b>9527</b> setting_BS_Android.bin (extra=JSON ter-decode) → <b>9528</b> index-native.html + js + serversetting.json (loginserver=127.0.0.1:5050) → <b>5050</b> socket.io OPEN+login event → <b>9530</b> /v1/* SDK.<br>")
         .append("Popup error saat game berhenti di login = PERILAKU TERDOKUMENTASI 17.7#2 — GetServerList TIDAK dikarang (kesepakatan: tidak ada data game palsu); lihat entry terakhir 5050 sebelum popup untuk event tepatnya.<br>")
         .append("Semua entry juga TERTULIS di disk: <b>Android/data/com.db.local/server/logs/log-*.ndjson</b> (unduh via /download) — buffer penuh pun arsip tetap lengkap.<br>")
         .append("Klik baris = detail (headers/req/resp/extra/error). status>=400 merah; WS biru; 101 cyan.")
         .append("</div>\n")

         .append("<script>\n")
         .append(Js())
         .append("</script>\n")
         .append("</body></html>");
        return b.toString();
    }

    private static String Js() {
        StringBuilder s = new StringBuilder(6144);
        s.append("var S={since:0,mode:'all',port:9527,kind:'',q:'',errs:false,limit:500,auto:true};\n")
         .append("var MAXROWS=8000;\n")
         .append("function $(id){return document.getElementById(id)}\n")
         .append("function esc(x){return (x==null?'':''+x).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         .append("function sync(){S.mode=$('mode').value;S.port=parseInt($('port').value);S.kind=$('kind').value;S.q=$('q').value.toLowerCase();S.errs=$('errs').checked;S.limit=parseInt($('limit').value);$('portsel').style.display=(S.mode=='port'?'inline':'none')}\n")
         .append("['mode','port','kind','limit'].forEach(function(id){$(id).onchange=function(){sync();reset()}});\n")
         .append("$('q').oninput=function(){sync();applyFilter()};$('errs').onchange=function(){sync();applyFilter()};\n")
         .append("$('pause').onclick=function(){S.auto=!S.auto;$('pause').textContent=S.auto?'Jeda':'Lanjut'};\n")
         .append("$('reset').onclick=function(){reset()};\n")
         .append("function reset(){$('tb').innerHTML='';S.since=0;$('notices').innerHTML='';}\n")
         .append("function url(){var u='/logs?mode='+S.mode+'&limit='+S.limit+'&since='+S.since;if(S.mode=='port')u+='&port='+S.port;return u}\n")
         .append("function note(cls,txt){var d=document.createElement('div');d.className=cls;d.textContent=txt;$('notices').appendChild(d)}\n")
         .append("function rowHtml(e){var cls=e.status>=400?'err':(e.status>=300?'warn':'ok');var req=esc(e.method)+' '+esc(e.path);var det='';\n")
         .append(" if(e.headers)det+='<b>headers</b>: '+esc(e.headers)+'\\n';\n")
         .append(" if(e.reqPreview)det+='<b>req</b>: '+esc(e.reqPreview)+'\\n';\n")
         .append(" if(e.respPreview)det+='<b>resp</b>: '+esc(e.respPreview)+'\\n';\n")
         .append(" if(e.extra)det+='<b>extra</b>: '+esc(e.extra)+'\\n';\n")
         .append(" if(e.error)det+='<b class=err>error</b>: '+esc(e.error);\n")
         .append(" return '<tr class=row data-seq='+e.seq+'><td>'+e.seq+'</td><td>'+esc(e.time)+'</td><td>'+e.port+'</td><td>'+esc(e.kind)+'</td><td title=\\''+esc(req).replace(/\\'/g,'')+'\\'>'+req+'</td><td class='+cls+'>'+e.status+'</td><td>'+e.durMs+'ms</td><td>'+e.reqLen+'</td><td>'+e.respLen+'</td></tr><tr class=det style=display:none><td colspan=9>'+det+'</td></tr>'}\n")
         .append("function pass(e){if(S.kind&&e.kind!==S.kind)return false;if(S.errs&&!(e.status>=400||e.error))return false;if(S.q){var t=((e.method||'')+' '+(e.path||'')+' '+(e.reqPreview||'')+' '+(e.respPreview||'')+' '+(e.extra||'')+' '+(e.error||'')).toLowerCase();if(t.indexOf(S.q)<0)return false}return true}\n")
         .append("function applyFilter(){var rows=$('tb').querySelectorAll('tr.row');for(var i=0;i<rows.length;i++){var det=rows[i].nextSibling;var seq=rows[i].getAttribute('data-seq');var e=ENT[seq];var show=e?pass(e):true;rows[i].style.display=show?'':'none';if(det&&det.className=='det')det.style.display=show?'none':'none'}}\n")
         .append("var ENT={};\n")
         .append("function append(list){var tb=$('tb');for(var i=0;i<list.length;i++){var e=list[i];ENT[e.seq]=e;var tmp=document.createElement('tbody');tmp.innerHTML=rowHtml(e);while(tmp.firstChild){tb.appendChild(tmp.firstChild)}if(!pass(e)){tb.lastChild.previousSibling.style.display='none'}}\n")
         .append(" var rows=tb.querySelectorAll('tr');var over=rows.length-MAXROWS*2;for(var j=0;j<over;j++)tb.removeChild(tb.firstChild)}\n")
         .append("var lastDrop=-1;\n")
         .append("function poll(){fetch(url()).then(function(r){return r.json()}).then(function(j){\n")
         .append(" if(j.gap&&j.entries.length)note('warn','GAP: entri seq '+(S.since+1)+'..'+(j.entries[0].seq-1)+' sudah terbuang dari buffer — arsip lengkap di /download');\n")
         .append(" if(j.entries.length){append(j.entries);S.since=j.seqNewest;applyFilter()}\n")
         .append(" if(j.droppedGlobal>0||j.droppedPort>0){var tot=j.droppedGlobal+j.droppedPort;if(tot!==lastDrop){lastDrop=tot;note('err','dropped: global='+j.droppedGlobal+' port='+j.droppedPort+' — tetap utuh di arsip disk')}}\n")
         .append(" refreshStats();setTimeout(poll,1500);\n")
         .append("}).catch(function(){setTimeout(poll,3000)})}\n")
         .append("function refreshStats(){fetch('/stats').then(function(r){return r.json()}).then(function(j){var p=j.ports;var up=Math.floor(j.uptimeMs/1000);var hm=Math.floor(up/3600),mm=Math.floor(up%3600/60),ss=up%60;\n")
         .append(" var t='uptime '+hm+'h'+mm+'m'+ss+'s | seq '+j.seq+' | arsip disk: '+(j.disk&&j.disk.enabled?esc(j.disk.file)+' '+j.disk.bytes+'B, '+j.disk.lines+' baris'+(j.disk.dropped>0?' <span class=err>DROPPED '+j.disk.dropped+'</span>':''):'OFF')+' | ';\n")
         .append(" ['9527','9528','5050','9530','4000'].forEach(function(k){var c=p[k];if(c)t+=k+':'+c.requests+(c.errors?'<span class=err>('+c.errors+'err)</span>':'')+' '});\n")
         .append(" if(j.droppedGlobal>0)t+='<span class=err>| bufferDropped:'+j.droppedGlobal+'</span> ';\n")
         .append(" $('statsbar').innerHTML=t}\n")
         .append(").catch(function(){})}\n")
         .append("function showFiles(j){var el=$('files');el.style.display='';var h='<b>Arsip NDJSON</b> ('+esc(j.dir)+'):<br>';h+='<a href=\"/download?latest=1\">[terbaru]</a> ';for(var i=0;i<j.files.length;i++)h+='<a href=\"/download?file='+encodeURIComponent(j.files[i].name)+'\">'+esc(j.files[i].name)+' ('+j.files[i].bytes+'B)</a> ';$('files').innerHTML=h}\n")
         .append("$('tb').addEventListener('click',function(ev){var tr=ev.target.closest('tr.row');if(!tr)return;var det=tr.nextSibling;if(det&&det.className=='det')det.style.display=(det.style.display=='none'?'':'none')});\n")
         .append("sync();poll();\n");
        return s.toString();
    }
}
