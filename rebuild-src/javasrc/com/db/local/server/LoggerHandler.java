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

    /* ================= dashboard v4 (Bagian 19) =================
     * Keluhan owner 22 Nov 2026: "banyak teks sampah keterangan yg gak perlu .
     * kurang rinci kurang jelas juga, gak bisa copy teks". Jawaban v4:
     *  - TANPA paragraf penjelasan — hanya tabel + kontrol (teks sampah dihapus).
     *  - RINCI: klik baris → blok terstruktur REQUEST (URL penuh + header per
     *    baris + body) / RESPON (status + body, JSON otomatis di-pretty) / EXTRA
     *    (JSON config ter-decode ikut di-pretty) / ERROR / SEMUA (teks polos);
     *    tiap blok punya tombol salin sendiri.
     *  - BISA COPY: seluruh teks selectable (user-select:text) + tombol salin per
     *    baris & per blok + Salin TSV + Salin JSON (array entri utuh) + fallback
     *    clipboard (execCommand → modal pilih-manual Ctrl+C) untuk browser HTTP
     *    biasa yang menolak navigator.clipboard.
     */

    private String dashboardHtml() {
        StringBuilder b = new StringBuilder(24576);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n")
         .append("<meta name='viewport' content='width=device-width,initial-scale=1'>\n")
         .append("<title>LOGGER v4</title>\n")
         .append("<style>\n")
         .append("body{font-family:Menlo,Consolas,monospace;background:#0e1216;color:#d7dde3;margin:0;padding:8px;font-size:12px;user-select:text;-webkit-user-select:text}\n")
         .append("#top{display:flex;flex-wrap:wrap;gap:8px;align-items:baseline;margin-bottom:6px}\n")
         .append("#title{color:#8fd48f;font-weight:bold;font-size:14px}\n")
         .append("#statsbar{color:#7d8894;font-size:11px}\n")
         .append("#ctl{display:flex;flex-wrap:wrap;gap:6px;align-items:center;background:#151b22;border:1px solid #2a333d;border-radius:6px;padding:6px;margin-bottom:6px}\n")
         .append("label{color:#9fb0c0;font-size:11px}\n")
         .append("select,input,button{background:#1b222b;color:#d7dde3;border:1px solid #2a333d;border-radius:4px;padding:3px 6px;font-family:inherit;font-size:11px}\n")
         .append("button{cursor:pointer}button:hover{border-color:#4a5a6a}\n")
         .append("#tblwrap{max-height:calc(100vh - 150px);overflow:auto;border:1px solid #2a333d;border-radius:6px}\n")
         .append("#tblwrap::-webkit-scrollbar{width:10px;height:10px}#tblwrap::-webkit-scrollbar-thumb{background:#2a333d;border-radius:5px}\n")
         .append("table{border-collapse:collapse;width:100%}\n")
         .append("th{position:sticky;top:0;background:#1b222b;color:#9fb0c0;text-align:left;padding:4px 6px;font-size:11px;border-bottom:1px solid #2a333d;z-index:2}\n")
         .append("td{padding:2px 6px;border-bottom:1px solid #1c242d;font-size:11px;white-space:nowrap}\n")
         .append("td.path{max-width:460px;overflow:hidden;text-overflow:ellipsis}\n")
         .append("tr.row:hover{background:#1a2129;cursor:pointer}\n")
         .append("tr.det>td{background:#0b0f14;white-space:normal;max-width:none;padding:6px 10px}\n")
         .append(".err{color:#ef6a6a}.warn{color:#e8b34c}.ok{color:#7fd17f}.ws{color:#6ec6d8}.mut{color:#7d8894}\n")
         .append(".sec{margin:0 0 6px;border:1px solid #232b34;border-radius:4px}\n")
         .append(".sech{background:#161c23;color:#8fd48f;padding:2px 8px;font-size:11px;display:flex;justify-content:space-between;align-items:center;gap:8px}\n")
         .append(".sec pre{margin:0;padding:6px 8px;white-space:pre-wrap;word-break:break-word;max-height:320px;overflow:auto;font-size:11px;color:#c7d1da}\n")
         .append(".cp{font-size:10px;padding:1px 7px}.cpy{font-size:10px;padding:1px 5px}\n")
         .append("#notices{font-size:11px;margin:4px 0}\n")
         .append("#toast{position:fixed;bottom:14px;left:50%;transform:translateX(-50%);background:#123a1f;color:#9fe6a8;border:1px solid #2f7a44;padding:6px 14px;border-radius:6px;display:none;z-index:50;font-size:12px}\n")
         .append("#modal{position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,.72);display:none;z-index:40}\n")
         .append("#modalbox{background:#12181f;border:1px solid #2a333d;border-radius:8px;max-width:760px;margin:40px auto;padding:10px}\n")
         .append("#modalhead{display:flex;justify-content:space-between;color:#8fd48f;margin-bottom:6px;font-size:12px}\n")
         .append("#modalpre{max-height:60vh;overflow:auto;white-space:pre-wrap;word-break:break-word;background:#0b0f14;padding:8px;font-size:11px;color:#c7d1da}\n")
         .append("</style></head><body>\n")
         .append("<div id=top><span id=title>LOGGER v4</span><span id=statsbar>memuat /stats …</span></div>\n")

         .append("<div id=ctl>\n")
         .append("<label>Mode <select id=mode><option value=all>ALL (semua port)</option><option value=port>PORT (satu port)</option></select></label>\n")
         .append("<label id=portsel style=display:none>Port <select id=port>")
         .append("<option value=9527>9527 config</option><option value=9528>9528 CDN</option>")
         .append("<option value=5050>5050 login</option><option value=9530>9530 SDK</option>")
         .append("</select></label>\n")
         .append("<label>Kind <select id=kind><option value=''>semua</option><option>HTTP</option><option>WS</option><option>BOOT</option><option>EXTRACT</option><option>SYS</option></select></label>\n")
         .append("<label>Status <select id=st><option value=''>semua</option><option value=2>2xx</option><option value=3>3xx</option><option value=4>4xx</option><option value=5>5xx</option><option value=e>error</option></select></label>\n")
         .append("<label>Cari <input id=q size=16 placeholder=subset></label>\n")
         .append("<label>Limit <select id=limit><option>200</option><option selected>1000</option><option>5000</option><option value=0>semua buffer</option></select></label>\n")
         .append("<button id=pause>Jeda</button> <button id=reset>Reset</button>\n")
         .append("<button id=cpts>Salin TSV</button> <button id=cpjs>Salin JSON</button>\n")
         .append("<button id=dl>Unduh NDJSON</button>\n")
         .append("</div>\n")

         .append("<div id=notices></div>\n")

         .append("<div id=tblwrap><table><thead><tr>")
         .append("<th>#</th><th>waktu</th><th>port</th><th>kind</th><th>method</th><th>path</th><th>st</th><th>dur</th><th>req</th><th>resp</th><th></th>")
         .append("</tr></thead><tbody id=tb></tbody></table></div>\n")

         .append("<div id=toast></div>\n")
         .append("<div id=modal><div id=modalbox><div id=modalhead><span id=modaltitle></span><button id=modalx>Tutup</button></div><pre id=modalpre></pre></div></div>\n")

         .append("<script>\n")
         .append(Js())
         .append("</script>\n")
         .append("</body></html>");
        return b.toString();
    }

    private static String Js() {
        StringBuilder s = new StringBuilder(12288);
        s.append("var S={since:0,mode:'all',port:9527,kind:'',st:'',q:'',limit:1000,auto:true};\n")
         .append("var ENT={},DET={};var MAXR=5000;var pollN=0;\n")
         .append("function $(id){return document.getElementById(id)}\n")
         .append("function esc(x){return (x==null?'':''+x).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}\n")
         .append("function attr(x){return esc(x).replace(/\"/g,'&quot;')}\n")
         .append("function hdrLines(h){return h?(''+h).split('; ').join('\\n'):''}\n")
         .append("function pretty(s){var t=(''+s).trim();var c=t.charAt(0);var e=t.charAt(t.length-1);\n")
         .append(" if((c=='{'&&e=='}')||(c=='['&&e==']')){try{return JSON.stringify(JSON.parse(t),null,2)}catch(x){}}\n")
         .append(" return ''+s}\n")
         .append("function prettyExtra(x){var i=(''+x).indexOf('DECODED-XOR: ');\n")
         .append(" if(i>=0){try{return x.substring(0,i+13)+'\\n'+JSON.stringify(JSON.parse(x.substring(i+13).trim()),null,2)}catch(y){}}\n")
         .append(" return ''+x}\n")
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
         .append("function cls(e){return e.status>=400?'err':(e.status>=300?'warn':'ok')}\n")
         .append("function rowHtml(e){\n")
         .append(" var r='<tr class=row data-seq='+e.seq+'>'\n")
         .append("  +'<td class=mut>'+e.seq+'</td><td class=mut>'+esc(e.time)+'</td><td>'+e.port+'</td>'\n")
         .append("  +'<td class='+(e.kind=='WS'?'ws':'mut')+'>'+esc(e.kind||'')+'</td>'\n")
         .append("  +'<td>'+esc(e.method||'')+'</td>'\n")
         .append("  +'<td class=path title=\"'+attr(e.path)+'\">'+esc(e.path||'')+'</td>'\n")
         .append("  +'<td class='+cls(e)+'>'+(e.status||0)+'</td>'\n")
         .append("  +'<td class=mut>'+e.durMs+'ms</td><td class=mut>'+(e.reqLen||0)+'</td><td class=mut>'+(e.respLen||0)+'</td>'\n")
         .append("  +'<td><button class=cpy data-seq='+e.seq+' data-k=all>salin</button></td></tr>';\n")
         .append(" var d=detOf(e);DET[e.seq]=d;\n")
         .append(" var h='<tr class=det style=display:none><td colspan=11>';\n")
         .append(" for(var i=0;i<d.length;i++)h+='<div class=sec><div class=sech><span>'+esc(d[i].l)+'</span><button class=cp data-seq='+e.seq+' data-k='+i+'>salin</button></div><pre>'+esc(d[i].t)+'</pre></div>';\n")
         .append(" h+='</td></tr>';\n")
         .append(" return r+h}\n")
         .append("function pass(e){\n")
         .append(" if(S.kind&&e.kind!=S.kind)return false;\n")
         .append(" if(S.st=='2'&&!(e.status>=200&&e.status<300))return false;\n")
         .append(" if(S.st=='3'&&!(e.status>=300&&e.status<400))return false;\n")
         .append(" if(S.st=='4'&&!(e.status>=400&&e.status<500))return false;\n")
         .append(" if(S.st=='5'&&e.status>=500)return false;\n")
         .append(" if(S.st=='e'&&!(e.status>=400||e.error))return false;\n")
         .append(" if(S.q){var t=((e.method||'')+' '+(e.path||'')+' '+(e.reqPreview||'')+' '+(e.respPreview||'')+' '+(e.extra||'')+' '+(e.error||'')).toLowerCase();if(t.indexOf(S.q)<0)return false}\n")
         .append(" return true}\n")
         .append("function applyFilter(){var rows=$('tb').querySelectorAll('tr.row');\n")
         .append(" for(var i=0;i<rows.length;i++){var e=ENT[rows[i].getAttribute('data-seq')];\n")
         .append("  var show=e?pass(e):true;rows[i].style.display=show?'':'none';\n")
         .append("  var det=rows[i].nextSibling;\n")
         .append("  if(det&&det.className=='det')det.style.display=(show&&det.style.display!='none')?'':'none';}}\n")
         .append("function append(list){var w=$('tblwrap');\n")
         .append(" var pin=w.scrollTop+w.clientHeight>=w.scrollHeight-60;\n")
         .append(" var tb=$('tb');\n")
         .append(" for(var i=0;i<list.length;i++){var e=list[i];ENT[e.seq]=e;\n")
         .append("  var tmp=document.createElement('tbody');tmp.innerHTML=rowHtml(e);\n")
         .append("  while(tmp.firstChild)tb.appendChild(tmp.firstChild);}\n")
         .append(" var rows=tb.querySelectorAll('tr');var over=rows.length-MAXR*2;\n")
         .append(" for(var j=0;j<over;j++)tb.removeChild(tb.firstChild);\n")
         .append(" applyFilter();if(pin)w.scrollTop=w.scrollHeight}\n")
         .append("function visible(){var out=[];var rows=$('tb').querySelectorAll('tr.row');\n")
         .append(" for(var i=0;i<rows.length;i++){if(rows[i].style.display=='none')continue;\n")
         .append("  var e=ENT[rows[i].getAttribute('data-seq')];if(e)out.push(e)}\n")
         .append(" return out}\n")
         .append("function copyTSV(){var v=visible();\n")
         .append(" var L=['seq\\twaktu\\tport\\tkind\\tmethod\\tpath\\tstatus\\tdurMs\\treqLen\\trespLen\\terror'];\n")
         .append(" for(var i=0;i<v.length;i++){var e=v[i];\n")
         .append("  L.push([e.seq,e.time,e.port,e.kind,e.method||'',(e.path||'').replace(/\\t/g,' '),e.status||0,e.durMs,e.reqLen,e.respLen,(e.error||'').replace(/\\t/g,' ')].join('\\t'))}\n")
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
         .append("function toast(m){var el=$('toast');el.textContent=m;el.style.display='block';clearTimeout(tt);tt=setTimeout(function(){el.style.display='none'},1800)}\n")
         .append("$('tb').addEventListener('click',function(ev){\n")
         .append(" var n=ev.target;\n")
         .append(" while(n&&n.tagName!='BUTTON'&&n.tagName!='TR')n=n.parentNode;\n")
         .append(" if(n&&n.tagName=='BUTTON'){var seq=n.getAttribute('data-seq'),k=n.getAttribute('data-k');\n")
         .append("  var d=DET[seq];if(d){var t=(k=='all')?d[d.length-1].t:d[parseInt(k)].t;copyText(t)}\n")
         .append("  ev.stopPropagation();return}\n")
         .append(" var tr=ev.target.closest?ev.target.closest('tr.row'):null;\n")
         .append(" if(!tr)return;\n")
         .append(" var det=tr.nextSibling;\n")
         .append(" if(det&&det.className=='det')det.style.display=(det.style.display=='none'?'':'none');});\n")
         .append("$('modalx').onclick=function(){$('modal').style.display='none'};\n")
         .append("$('modal').onclick=function(ev){if(ev.target.id=='modal')$('modal').style.display='none'};\n")
         .append("function sync(){S.mode=$('mode').value;S.port=parseInt($('port').value);S.kind=$('kind').value;\n")
         .append(" S.st=$('st').value;S.q=$('q').value.toLowerCase();S.limit=parseInt($('limit').value);\n")
         .append(" $('portsel').style.display=(S.mode=='port')?'':'none'}\n")
         .append("function reset(){$('tb').innerHTML='';ENT={};DET={};S.since=0;$('notices').innerHTML=''}\n")
         .append("['mode','port','limit'].forEach(function(id){$(id).onchange=function(){sync();reset()}});\n")
         .append("['kind','st'].forEach(function(id){$(id).onchange=function(){sync();applyFilter()}});\n")
         .append("$('q').oninput=function(){S.q=$('q').value.toLowerCase();applyFilter()};\n")
         .append("$('pause').onclick=function(){S.auto=!S.auto;$('pause').textContent=S.auto?'Jeda':'Lanjut'};\n")
         .append("$('reset').onclick=reset;\n")
         .append("$('cpts').onclick=copyTSV;\n")
         .append("$('cpjs').onclick=copyJSON;\n")
         .append("$('dl').onclick=function(){var a=document.createElement('a');a.href='/download?latest=1';a.download='';document.body.appendChild(a);a.click();document.body.removeChild(a)};\n")
         .append("var lastDrop=-1;\n")
         .append("function note(c2,txt){var d=document.createElement('div');d.className=c2;d.textContent=txt;$('notices').appendChild(d)}\n")
         .append("function url(){var u='/logs?mode='+S.mode+'&limit='+S.limit+'&since='+S.since;if(S.mode=='port')u+='&port='+S.port;return u}\n")
         .append("function poll(){\n")
         .append(" if(!S.auto){setTimeout(poll,500);return}\n")
         .append(" fetch(url()).then(function(r){return r.json()}).then(function(j){\n")
         .append("  if(j.gap&&j.entries.length)note('warn','GAP: seq '+(S.since+1)+'..'+(j.entries[0].seq-1)+' terbuang dari buffer — arsip disk tetap utuh (Unduh NDJSON)');\n")
         .append("  if(j.entries.length){append(j.entries);S.since=j.seqNewest}\n")
         .append("  var tot=(j.droppedGlobal||0)+(j.droppedPort||0);\n")
         .append("  if(tot>0&&tot!==lastDrop){lastDrop=tot;note('err','buffer dropped '+tot+' — arsip disk tetap utuh')}\n")
         .append("  pollN++;if(pollN%3==0)refreshStats();\n")
         .append("  setTimeout(poll,1200);\n")
         .append(" }).catch(function(){setTimeout(poll,3000)})}\n")
         .append("function refreshStats(){fetch('/stats').then(function(r){return r.json()}).then(function(j){\n")
         .append(" var up=Math.floor(j.uptimeMs/1000);\n")
         .append(" var t='uptime '+Math.floor(up/3600)+'h'+Math.floor(up%3600/60)+'m'+(up%60)+'s  seq '+j.seq;\n")
         .append(" var ps=['9527','9528','5050','9530','4000'];\n")
         .append(" for(var i=0;i<ps.length;i++){var c=j.ports[ps[i]];if(c)t+='  '+ps[i]+':'+c.requests+(c.errors?'('+c.errors+'err)':'')}\n")
         .append(" if(j.disk&&j.disk.enabled)t+='  | arsip '+j.disk.file+' '+(j.disk.bytes>=1024?Math.round(j.disk.bytes/1024)+'KB':j.disk.bytes+'B')+' '+j.disk.lines+' baris';\n")
         .append(" if(j.droppedGlobal>0)t+='  | dropped '+j.droppedGlobal;\n")
         .append(" $('statsbar').textContent=t}).catch(function(){})}\n")
         .append("document.title='LOGGER v4 :'+location.port;\n")
         .append("$('title').textContent='LOGGER v4 :'+location.port;\n")
         .append("sync();poll();\n");
        return s.toString();
    }
}
