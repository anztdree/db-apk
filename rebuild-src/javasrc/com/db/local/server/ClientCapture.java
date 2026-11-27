package com.db.local.server;

/**
 * CAPTURE CLIENT (Bagian 72) — evolusi Bagian 74: capture v2 DARI SEGALA SISI.
 *
 * Arahan owner (kata-kata persis, 26 Nov 2026):
 *   "sudah di bilang di game itu sedang loading !! artinya ada log yg jelas
 *    gk tertangkap malah log anda itu isinya apa babi ."
 *   "Sudah di bilang log natural masih aja ngeyel !!! kalau loading ya dia
 *    itu loading apa ! lihat log eruda sebagai contoh goblok !!"
 * Arahan owner (27 Nov 2026): lanjutkan log debug port 4000 — JANGAN bikin
 * sistem baru (pola rebuild tetap: javac → D8 → patch marker → raw-copy).
 *
 * YANG BARU di capture v2 (prinsip eruda — tiru CARA KERJA, bukan menjiplak;
 * lebih jauh dari eruda karena hasilnya mengalir ke log global server):
 *  1. CONSOLE LENGKAP (eruda-level): log info warn error debug + DIR, TRACE
 *     (dengan stack), TABLE (JSON), CLEAR, COUNT (hitungan per label),
 *     TIME/TIMEEND (durasi per label), GROUP/GROUPEND (kedalaman).
 *  2. STACK PENUH utk window.onerror (ev.error.stack — dulu hanya message
 *     + filename) dan unhandledrejection (reason.stack).
 *  3. RESOURCE TIMING (perbedaan terbesar vs v72): file js/media yang
 *     dilayani shell EgretNative dari penyimpanan lokal TIDAK PERNAH lewat
 *     listener HTTP — tapi timeline performance WebView TETAP MERECAMNYA.
 *     Sweep performance.getEntriesByType('resource') mengirim SATU baris
 *     per file BARU (nama + initiatorType + durasi + ukuran) — bukti
 *     "loading apa" per file, yang dulu buta total.
 *  4. LONGTASK (PerformanceObserver, bila WebView mendukung): UI macet
 *     >50 ms tercatat sebagai peringatan berdurasi.
 *  5. NAVIGATION TIMING + LIFECYCLE: domInteractive/DCL/load dalam SATU
 *     baris fakta; DOMContentLoaded, visibilitychange tercatat.
 *  6. HEARTBEAT FPS tiap 5 dtk (hanya saat halaman terlihat — request-
 *     AnimationFrame berhenti di background): bukti halaman HIDUP atau
 *     MACET saat loading, tanpa karangan.
 *  7. XHR/FETCH + BODY: request body (string, ≤300 char) dan preview
 *     response (≤300 char) ikut terkirim — dulu hanya status + durasi.
 *  8. RESILIEN (dulu BERHENTI TOTAL setelah 8 POST gagal — log hilang
 *     senyap): JANGAN PERNAH berhenti; backoff 500ms→5 dtk, buffer
 *     PRIORITAS (yang dibuang duluan = DEBUG/INFO terlama; ERROR
 *     dipertahankan selagi mungkin), sendBeacon saat halaman ditutup,
 *     flush ulang saat kembali online/visible.
 *  9. SESI: sid per halaman (sessionStorage — reload sama device = sid
 *     sama), meta device (UA/layar/memori/koneksi/egret) dikirim SEKALI
 *     pada batch sukses pertama → server menulis SATU baris CLIENT meta.
 *
 * TETAP (semua arahan lama): ES5 murni, NOL console sendiri, NOL throw
 * keluar, semua hook try/catch + wrap lewatkan apa adanya; file bs/ di
 * APK/assets byte-identik (injeksi serve-time di memori saja); transport
 * POST /log/client origin sama; /socket.io/* tidak dicapture client
 * (sudah utuh di sisi server — jangan dobel); anti-loop EP.
 */
public final class ClientCapture {

    private ClientCapture() {}

    /** sisipkan capture script sebelum </head> pertama (case-insensitive).
     *  Kembalikan array ASLI bila anchor tidak ada — halaman disajikan
     *  tanpa injeksi (jujur), tanpa pernah melempar. */
    public static byte[] inject(byte[] html) {
        try {
            if (html == null || html.length == 0) return html;
            String s = new String(html, java.nio.charset.StandardCharsets.UTF_8);
            int idx = s.toLowerCase().indexOf("</head>");
            if (idx < 0) return html;
            StringBuilder sb = new StringBuilder(s.length() + SCRIPT.length() + 32);
            sb.append(s, 0, idx);
            sb.append(SCRIPT);
            sb.append(s, idx, s.length());
            return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return html;   // jangan pernah menggagalkan penyajian halaman
        }
    }

    /** Capture script v2 — ES5 murni (WebView device tua), NOL console
     *  sendiri, NOL throw keluar, tick tunggal 500 ms dgn backoff + sweep
     *  resource, POST /log/client (origin sama). Format batch v2:
     *  {v:2, sid, meta?, events:[{ts,type,level?,text,dur?,stack?,body?,resp?}]} */
    public static final String SCRIPT =
        "<script>(function(){\n" +
        "if(window.__dbClientCapture)return;window.__dbClientCapture=true;\n" +
        "var EP='/log/client';var buf=[];var fails=0;var inflight=false;var delay=500;\n" +
        "var MAXBUF=400;var MAXTEXT=1200;var sid;var metaSent=false;\n" +
        "try{sid=sessionStorage.getItem('__dbSid');\n" +
        " if(!sid){sid='s-'+(new Date).getTime().toString(36)+'-'+Math.random().toString(36).substring(2,8);\n" +
        "  sessionStorage.setItem('__dbSid',sid);}}catch(e){sid='s-anon-'+(new Date).getTime().toString(36)}\n" +
        "function cap(type,level,text,dur,stack,body,resp){try{\n" +
        " if(buf.length>=MAXBUF){var drop=-1;\n" +
        "  for(var i=0;i<buf.length;i++){var lv=buf[i].level;\n" +
        "   if(lv!=='ERROR'){drop=i;break;}}\n" +
        "  buf.splice(drop>=0?drop:0,1);}\n" +
        " var e={ts:(new Date).getTime(),type:type,\n" +
        "  text:String(text).substring(0,MAXTEXT)};\n" +
        " if(level)e.level=level;\n" +
        " if(typeof dur==='number'&&isFinite(dur))e.dur=Math.round(dur);\n" +
        " if(stack)e.stack=String(stack).substring(0,1000);\n" +
        " if(body)e.body=String(body).substring(0,300);\n" +
        " if(resp)e.resp=String(resp).substring(0,300);\n" +
        " buf.push(e);}catch(e2){}}\n" +
        "function txt(s){try{\n" +
        " if(s===null)return 'null';if(s===undefined)return 'undefined';\n" +
        " var t=typeof s;\n" +
        " if(t==='string')return s;\n" +
        " if(t==='number'||t==='boolean')return ''+s;\n" +
        " if(s instanceof Error){var st=String(s.stack||'');\n" +
        "  return (s.name||'Error')+': '+(s.message||'')+(st?' | '+st.split('\\n')[1]:'');}\n" +
        " if(t==='function')return '[fn '+(s.name||'anon')+']';\n" +
        " if(t==='object'){var j=JSON.stringify(s);\n" +
        "  return (j===undefined)?'[object]':j.substring(0,800);}\n" +
        " return String(s);}catch(e){return '[?]'}}\n" +
        "function stk(){try{var e=new Error();var l=String(e.stack||'').split('\\n');\n" +
        " return l.slice(2,6).join(' <- ');}catch(e){return ''}}\n" +
        "/* ---- console LENGKAP (eruda-level) ---- */\n" +
        "var CON=['log','info','warn','error','debug'];\n" +
        "for(var ci=0;ci<CON.length;ci++)(function(m){\n" +
        " try{var o=console[m];if(!o)return;\n" +
        "  console[m]=function(){\n" +
        "   try{var a=[];\n" +
        "    for(var i=0;i<arguments.length;i++)a.push(txt(arguments[i]));\n" +
        "    cap('console.'+m,m==='error'?'ERROR':null,a.join(' '));\n" +
        "   }catch(e){}\n" +
        "   try{return o.apply(console,arguments);}catch(e2){}};\n" +
        " }catch(e){}})(CON[ci]);\n" +
        "function wrap1(name,fn){try{var o=console[name];\n" +
        " console[name]=function(){try{fn(arguments)}catch(e){}\n" +
        "  try{if(o)return o.apply(console,arguments)}catch(e2){}};}catch(e){}}\n" +
        "wrap1('dir',function(a){cap('console.dir',null,txt(a[0]))});\n" +
        "wrap1('table',function(a){cap('console.table',null,txt(a[0]))});\n" +
        "wrap1('clear',function(){cap('console.clear',null,'console.clear')});\n" +
        "wrap1('trace',function(a){var h=[];\n" +
        " for(var i=0;i<a.length;i++)h.push(txt(a[i]));\n" +
        " var s=stk();cap('console.trace',null,h.join(' ')+(s?' | '+s:''))});\n" +
        "var COUNTS={};\n" +
        "wrap1('count',function(a){var l=a.length?(''+a[0]):'default';\n" +
        " COUNTS[l]=(COUNTS[l]||0)+1;\n" +
        " cap('console.count',null,'console.count '+l+': '+COUNTS[l])});\n" +
        "var TIMES={};\n" +
        "wrap1('time',function(a){var l=a.length?(''+a[0]):'default';\n" +
        " TIMES[l]=(new Date).getTime()});\n" +
        "wrap1('timeEnd',function(a){var l=a.length?(''+a[0]):'default';\n" +
        " var t0=TIMES[l];if(t0){delete TIMES[l];\n" +
        "  cap('console.timeEnd',null,'console.timeEnd '+l+' — selesai '+((new Date).getTime()-t0)+' ms',(new Date).getTime()-t0)}});\n" +
        "var GROUP=0;\n" +
        "wrap1('group',function(a){GROUP++;\n" +
        " cap('console.group',null,'console.group '+txt(a[0])+' — kedalaman '+GROUP)});\n" +
        "wrap1('groupEnd',function(){GROUP=Math.max(0,GROUP-1);\n" +
        " cap('console.groupEnd',null,'console.groupEnd — kedalaman '+GROUP)});\n" +
        "/* ---- jserror: STACK PENUH (dulu message+filename saja) ---- */\n" +
        "window.addEventListener('error',function(ev){\n" +
        " try{var msg=(ev&&ev.message)?txt(ev.message):txt(ev);\n" +
        "  if(ev&&ev.filename)msg+=' @ '+ev.filename+':'+(ev.lineno||0);\n" +
        "  var st=(ev&&ev.error&&ev.error.stack)?String(ev.error.stack):'';\n" +
        "  cap('jserror','ERROR',msg,null,(st.length>0)?st:null);}catch(e){}},true);\n" +
        "window.addEventListener('unhandledrejection',function(ev){\n" +
        " try{var r=ev&&ev.reason?ev.reason:ev;\n" +
        "  cap('unhandledrejection','ERROR',txt(r),null,(r&&r.stack)?String(r.stack):null);}catch(e){}},true);\n" +
        "/* ---- script/media (createElement) — tetap v72 ---- */\n" +
        "var origCreate=document.createElement.bind(document);\n" +
        "document.createElement=function(tag){\n" +
        " var el=origCreate(tag);\n" +
        " try{var t=String(tag).toLowerCase();\n" +
        "  if(el&&(t==='script'||t==='img'||t==='audio'||t==='video')){\n" +
        "   var t0=(new Date).getTime();\n" +
        "   var kind=(t==='script')?'script':'media';\n" +
        "   var desc=function(){var s=(el.getAttribute&&el.getAttribute('src'))||el.src||'(no-src)';\n" +
        "    return t+' '+String(s).substring(0,300);};\n" +
        "   el.addEventListener('load',function(){\n" +
        "    cap(kind,null,kind+' load: '+desc()+' — selesai '+((new Date).getTime()-t0)+' ms',(new Date).getTime()-t0);});\n" +
        "   el.addEventListener('error',function(){\n" +
        "    cap(kind,'ERROR',kind+' load: '+desc()+' — GAGAL '+((new Date).getTime()-t0)+' ms',(new Date).getTime()-t0);});\n" +
        "  }}catch(e){}\n" +
        " return el;};\n" +
        "/* ---- XHR + BODY (dulu hanya status+durasi) ---- */\n" +
        "var origOpen=XMLHttpRequest.prototype.open;\n" +
        "var origSend=XMLHttpRequest.prototype.send;\n" +
        "XMLHttpRequest.prototype.open=function(method,url){\n" +
        " try{var u=String(url||'');\n" +
        "  if(u.indexOf(EP)<0&&u.indexOf('/socket.io')<0){\n" +
        "   var self=this;var m=String(method||'GET');var t0=(new Date).getTime();\n" +
        "   var us=u.substring(0,300);\n" +
        "   this.addEventListener('loadend',function(){\n" +
        "    try{var st=self.status||0;var dur=(new Date).getTime()-t0;\n" +
        "     var rp=null;try{if(!self.responseType||self.responseType==='text')\n" +
        "      rp=String(self.responseText).substring(0,300);}catch(e3){}\n" +
        "     if(st===0)cap('xhr','ERROR','xhr '+m+' '+us+' — GAGAL (network error/timeout) '+dur+' ms',dur,null,self.__dbBody||null,rp);\n" +
        "     else if(st>=400)cap('xhr','ERROR','xhr '+m+' '+us+' → '+st+' ('+dur+' ms)',dur,null,self.__dbBody||null,rp);\n" +
        "     else cap('xhr',null,'xhr '+m+' '+us+' → '+st+' ('+dur+' ms)',dur,null,self.__dbBody||null,rp);\n" +
        "    }catch(e){}});\n" +
        "  }}catch(e){}\n" +
        " return origOpen.apply(this,arguments);};\n" +
        "XMLHttpRequest.prototype.send=function(b){\n" +
        " try{if(b!=null&&typeof b==='string')this.__dbBody=b.substring(0,300);}catch(e){}\n" +
        " return origSend.apply(this,arguments);};\n" +
        "if(window.fetch){var origFetch=window.fetch;\n" +
        " window.fetch=function(input,init){\n" +
        "  var u;var m;\n" +
        "  try{u=(input&&input.url)?String(input.url):String(input);\n" +
        "   m=(init&&init.method)?String(init.method):((input&&input.method)?String(input.method):'GET');\n" +
        "   if(u.indexOf(EP)>=0||u.indexOf('/socket.io')>=0)return origFetch.apply(window,arguments);\n" +
        "  }catch(e){return origFetch.apply(window,arguments);}\n" +
        "  var t0=(new Date).getTime();var us=u.substring(0,300);\n" +
        "  try{return origFetch.apply(window,arguments).then(function(res){\n" +
        "   try{var done=function(rp){\n" +
        "    cap('fetch',(res&&res.ok)?null:'ERROR',\n" +
        "     'fetch '+m+' '+us+' → '+(res&&res.status)+' ('+((new Date).getTime()-t0)+' ms)',\n" +
        "     (new Date).getTime()-t0,null,null,rp);};\n" +
        "    if(res&&res.clone){try{res.clone().text().then(function(t){\n" +
        "     done(t?t.substring(0,300):null);},function(){done(null)});}catch(e4){done(null)}}\n" +
        "    else done(null);}catch(e){}\n" +
        "   return res;\n" +
        "  },function(err){\n" +
        "   try{cap('fetch','ERROR','fetch '+m+' '+us+' — GAGAL '+txt(err),(new Date).getTime()-t0);}catch(e){}\n" +
        "   throw err;});\n" +
        "  }catch(e){return origFetch.apply(window,arguments);}};\n" +
        "}\n" +
        "/* ---- RESOURCE TIMING SWEEP — file yang dilayani shell EgretNative\n" +
        "   dari lokal tidak pernah lewat server, tapi timeline performance\n" +
        "   WebView TETAP MENCATATNYA. Satu baris per file baru. ---- */\n" +
        "var seenRes={};var seenN=0;var tickN=0;var sweepStop=false;\n" +
        "function sweep(){\n" +
        " if(sweepStop)return;\n" +
        " try{var es=performance.getEntriesByType('resource');\n" +
        "  for(var i=0;i<es.length;i++){var e=es[i];var n=e.name||'';\n" +
        "   if(seenRes[n])continue;\n" +
        "   if(n.indexOf(EP)>=0||n.indexOf('/socket.io')>=0){seenRes[n]=1;continue;}\n" +
        "   if(seenN>=3000){sweepStop=true;\n" +
        "    cap('resource',null,'resource sweep dibatasi 3000 entri (arsip disk tetap jalan)');\n" +
        "    return;}\n" +
        "   seenRes[n]=1;seenN++;\n" +
        "   var sz=(e.transferSize!=null&&e.transferSize>0)?(' ('+e.transferSize+' B)'):\n" +
        "    (e.decodedBodySize>0?(' ('+Math.round(e.decodedBodySize/1024)+' KB)'):'');\n" +
        "   cap('resource',null,'resource '+(e.initiatorType||'?')+' '+n.substring(0,300)+\n" +
        "    ' — '+Math.round(e.duration)+' ms'+sz,e.duration);}\n" +
        " }catch(e){}}\n" +
        "/* ---- LONGTASK — UI macet di atas 50 ms (bila didukung) ---- */\n" +
        "try{if(window.PerformanceObserver){\n" +
        " var po=new PerformanceObserver(function(list){\n" +
        "  try{var es=list.getEntries();\n" +
        "   for(var i=0;i<es.length;i++)\n" +
        "    cap('longtask','WARN','longtask '+Math.round(es[i].duration)+' ms — UI macet (main thread terblokir)',es[i].duration);\n" +
        "  }catch(e){}});\n" +
        " po.observe({entryTypes:['longtask']});}}catch(e){}\n" +
        "/* ---- LIFECYCLE + NAVIGATION TIMING ---- */\n" +
        "document.addEventListener('DOMContentLoaded',function(){\n" +
        " cap('lifecycle',null,'lifecycle DOMContentLoaded');},false);\n" +
        "window.addEventListener('load',function(){\n" +
        " setTimeout(function(){\n" +
        "  try{var t=performance.timing;var n0=t.navigationStart;\n" +
        "   cap('lifecycle',null,'lifecycle load — total '+(t.loadEventEnd-n0)+' ms'+\n" +
        "    ' (responseEnd '+(t.responseEnd-n0)+', domInteractive '+(t.domInteractive-n0)+', DCL '+(t.domContentLoadedEventEnd-n0)+')');\n" +
        "  }catch(e){cap('lifecycle',null,'lifecycle load');}},0);},false);\n" +
        "document.addEventListener('visibilitychange',function(){\n" +
        " cap('lifecycle',null,'lifecycle visibility '+(document.hidden?'hidden':'visible'));},false);\n" +
        "/* ---- HEARTBEAT FPS — bukti halaman hidup/macet ---- */\n" +
        "var frames=0;var fpsT0=(new Date).getTime();\n" +
        "function raf(){frames++;try{requestAnimationFrame(raf)}catch(e){}}\n" +
        "try{requestAnimationFrame(raf)}catch(e){}\n" +
        "/* ---- META DEVICE — dikirim SEKALI pada batch sukses pertama ---- */\n" +
        "function buildMeta(){try{\n" +
        " var c=navigator.connection||navigator.webkitConnection;\n" +
        " return {ua:String(navigator.userAgent).substring(0,200),\n" +
        "  sw:screen.width,sh:screen.height,dpr:window.devicePixelRatio||1,\n" +
        "  mem:(performance&&performance.memory)?Math.round(performance.memory.usedJSHeapSize/1048576):-1,\n" +
        "  conn:(c&&c.effectiveType)||'',egret:(typeof egret!=='undefined'),\n" +
        "  lang:navigator.language||''};}catch(e){return {ua:'(meta gagal)'}}}\n" +
        "/* ---- flush: backoff, TIDAK PERNAH berhenti (dulu mati setelah 8 gagal) ---- */\n" +
        "function flush(){\n" +
        " if(inflight||!buf.length)return;\n" +
        " var payload;var withMeta=!metaSent;\n" +
        " try{payload={v:2,sid:sid,events:buf};\n" +
        "  if(withMeta)payload.meta=buildMeta();\n" +
        "  payload=JSON.stringify(payload);}catch(e){return;}\n" +
        " var sent=buf;buf=[];inflight=true;\n" +
        " try{var x=new XMLHttpRequest();\n" +
        "  x.open('POST',EP,true);\n" +
        "  x.setRequestHeader('Content-Type','application/json');\n" +
        "  x.addEventListener('loadend',function(){\n" +
        "   inflight=false;\n" +
        "   if(x.status===0||x.status>=400){fails++;delay=Math.min(delay*2,5000);\n" +
        "    buf=sent.concat(buf);if(buf.length>MAXBUF)buf.splice(0,buf.length-MAXBUF);}\n" +
        "   else{fails=0;delay=500;if(withMeta)metaSent=true;}});\n" +
        "  x.send(payload);}catch(e){inflight=false;fails++;\n" +
        "  buf=sent.concat(buf);if(buf.length>MAXBUF)buf.splice(0,buf.length-MAXBUF);\n" +
        "  delay=Math.min(delay*2,5000);}}\n" +
        "var nextFlush=0;\n" +
        "function tick(){\n" +
        " var now=(new Date).getTime();\n" +
        " tickN++;\n" +
        " if(now>=nextFlush){flush();nextFlush=now+delay;}\n" +
        " if(tickN<30||tickN%20===0)sweep();\n" +
        " if(now-fpsT0>=5000){var fps=Math.round(frames*1000/(now-fpsT0));\n" +
        "  frames=0;fpsT0=now;\n" +
        "  if(fps>0)cap('perf',null,'fps '+fps+(fps<30?' — LAMBAT/macet':' — halaman hidup'));}\n" +
        " setTimeout(tick,500);}\n" +
        "setTimeout(tick,400);\n" +
        "window.addEventListener('pagehide',function(){\n" +
        " try{if(buf.length&&navigator.sendBeacon){\n" +
        "  var b=JSON.stringify({v:2,sid:sid,events:buf.splice(0,buf.length)});\n" +
        "  navigator.sendBeacon(EP,new Blob([b],{type:'application/json'}));}\n" +
        "  else flush();}catch(e){}},false);\n" +
        "window.addEventListener('beforeunload',flush,false);\n" +
        "document.addEventListener('visibilitychange',function(){\n" +
        " if(!document.hidden){flush();}},false);\n" +
        "})();</script>\n";
}
