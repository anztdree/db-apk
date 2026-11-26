package com.db.local.server;

/**
 * CAPTURE CLIENT (Bagian 72) — jembatan aktivitas sisi CLIENT halaman game
 * ke log global server.ndjson.
 *
 * Arahan owner (kata-kata persis, 26 Nov 2026):
 *   "sudah di bilang di game itu sedang loading !! artinya ada log yg jelas
 *    gk tertangkap malah log anda itu isinya apa babi ."
 *   "Sudah di bilang log natural masih aja ngeyel !!! kalau loading ya dia
 *    itu loading apa ! lihat log eruda sebagai contoh goblok !!"
 *
 * FAKTA yang membuat fase loading BUTA di log server (bukti device v22/v23:
 * release-mod/server.ndjson — antara index-native.html dan socket.io TIDAK
 * ADA satu pun request js/resource di log):
 *  1. 15 file js game + mayoritas resource dilayani shell EgretNative dari
 *     penyimpanan lokal (interceptor native) — request itu TIDAK PERNAH
 *     sampai ke listener HTTP kita, jadi log server tidak bisa melihatnya;
 *  2. console.* halaman game ([ERR] showErrorLog, log engine egret, dsb.)
 *     hidup di WebView — tidak pernah melewati server mana pun;
 *  3. error JS (window.onerror / unhandledrejection) hanya saksi client.
 *
 * Prinsip = persis cara kerja eruda (yang owner pakai sebagai contoh):
 * script kecil diinjeksi ke HALAMAN yang dilayani server kita sendiri
 * (index-native.html dari 9528 — origin sama), meng-hook permukaan halaman,
 * lalu mengirim tiap kejadian apa adanya ke POST /log/client → LoggerCore
 * (kind CLIENT, port 9528 = chip CDN di dashboard) → SATU FILE server.ndjson.
 *
 * BATAS INJEKSI (jujur):
 *  - file bs/ di APK/assets TIDAK disentuh — injeksi hanya pada respons HTTP
 *    (serve-time, memori); file di disk byte-identik;
 *  - yang dicapture = apa yang halaman benar-benar kerjakan (console, error,
 *    pemuatan script/media, XHR/fetch) — TANPA karangan, TANPA merubah
 *    perilaku game (semua hook try/catch, wrap lewatkan apa adanya);
 *  - /socket.io/* tidak dicapture client (sudah utuh di sisi server — frames,
 *    headers, timing) supaya tidak dobel;
 *  - request /log/client milik capture sendiri dikecualikan (anti loop).
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

    /** Capture script — ES5 murni (WebView device tua), NOL console sendiri,
     *  NOL throw keluar, flush batch 500 ms via POST /log/client (origin sama). */
    public static final String SCRIPT =
        "<script>(function(){\n" +
        "if(window.__dbClientCapture)return;window.__dbClientCapture=true;\n" +
        "var EP='/log/client';var buf=[];var fails=0;var stopped=false;\n" +
        "var MAXBUF=400;var MAXTEXT=1200;\n" +
        "function cap(type,level,text){if(stopped)return;\n" +
        " try{if(buf.length>=MAXBUF)buf.shift();\n" +
        "  buf.push({ts:(new Date).getTime(),type:type,level:level,\n" +
        "   text:String(text).substring(0,MAXTEXT)});}catch(e){}}\n" +
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
        "function flush(){if(stopped||!buf.length)return;\n" +
        " var body;try{body=JSON.stringify({v:1,events:buf});}catch(e){return;}\n" +
        " buf=[];\n" +
        " try{var x=new XMLHttpRequest();\n" +
        "  x.open('POST',EP,true);\n" +
        "  x.setRequestHeader('Content-Type','application/json');\n" +
        "  x.addEventListener('loadend',function(){\n" +
        "   if(x.status===0||x.status>=400){fails++;if(fails>=8)stopped=true;}\n" +
        "   else fails=0;});\n" +
        "  x.send(body);}catch(e){stopped=true;}}\n" +
        "setInterval(flush,500);\n" +
        "window.addEventListener('pagehide',flush,false);\n" +
        "window.addEventListener('beforeunload',flush,false);\n" +
        "var CON=['log','info','warn','error','debug'];\n" +
        "for(var ci=0;ci<CON.length;ci++)(function(m){\n" +
        " try{var o=console[m];if(!o)return;\n" +
        "  console[m]=function(){\n" +
        "   try{var a=[];\n" +
        "    for(var i=0;i<arguments.length;i++)a.push(txt(arguments[i]));\n" +
        "    cap('console.'+m,m==='error'?'ERROR':(m==='warn'?'WARN':'INFO'),a.join(' '));\n" +
        "   }catch(e){}\n" +
        "   try{return o.apply(console,arguments);}catch(e2){}};\n" +
        " }catch(e){}})(CON[ci]);\n" +
        "window.addEventListener('error',function(ev){\n" +
        " try{var msg=(ev&&ev.message)?txt(ev.message):txt(ev);\n" +
        "  if(ev&&ev.filename)msg+=' @ '+ev.filename+':'+(ev.lineno||0);\n" +
        "  cap('jserror','ERROR',msg);}catch(e){}},true);\n" +
        "window.addEventListener('unhandledrejection',function(ev){\n" +
        " try{cap('unhandledrejection','ERROR',txt(ev&&ev.reason?ev.reason:ev));}catch(e){}},true);\n" +
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
        "    cap(kind,'INFO',kind+' load: '+desc()+' — selesai '+((new Date).getTime()-t0)+' ms');});\n" +
        "   el.addEventListener('error',function(){\n" +
        "    cap(kind,'ERROR',kind+' load: '+desc()+' — GAGAL '+((new Date).getTime()-t0)+' ms');});\n" +
        "  }}catch(e){}\n" +
        " return el;};\n" +
        "var origOpen=XMLHttpRequest.prototype.open;\n" +
        "XMLHttpRequest.prototype.open=function(method,url){\n" +
        " try{var u=String(url||'');\n" +
        "  if(u.indexOf(EP)<0&&u.indexOf('/socket.io')<0){\n" +
        "   var self=this;var m=String(method||'GET');var t0=(new Date).getTime();\n" +
        "   var us=u.substring(0,300);\n" +
        "   this.addEventListener('loadend',function(){\n" +
        "    try{var st=self.status||0;var dur=(new Date).getTime()-t0;\n" +
        "     if(st===0)cap('xhr','ERROR','xhr '+m+' '+us+' — GAGAL (network error/timeout) '+dur+' ms');\n" +
        "     else if(st>=400)cap('xhr','ERROR','xhr '+m+' '+us+' → '+st+' ('+dur+' ms)');\n" +
        "     else cap('xhr','INFO','xhr '+m+' '+us+' → '+st+' ('+dur+' ms)');\n" +
        "    }catch(e){}});\n" +
        "  }}catch(e){}\n" +
        " return origOpen.apply(this,arguments);};\n" +
        "if(window.fetch){var origFetch=window.fetch;\n" +
        " window.fetch=function(input,init){\n" +
        "  var u;var m;\n" +
        "  try{u=(input&&input.url)?String(input.url):String(input);\n" +
        "   m=(init&&init.method)?String(init.method):((input&&input.method)?String(input.method):'GET');\n" +
        "   if(u.indexOf(EP)>=0||u.indexOf('/socket.io')>=0)return origFetch.apply(window,arguments);\n" +
        "  }catch(e){return origFetch.apply(window,arguments);}\n" +
        "  var t0=(new Date).getTime();var us=u.substring(0,300);\n" +
        "  try{return origFetch.apply(window,arguments).then(function(res){\n" +
        "   try{cap('fetch',(res&&res.ok)?'INFO':'ERROR',\n" +
        "    'fetch '+m+' '+us+' → '+(res&&res.status)+' ('+((new Date).getTime()-t0)+' ms)');}catch(e){}\n" +
        "   return res;\n" +
        "  },function(err){\n" +
        "   try{cap('fetch','ERROR','fetch '+m+' '+us+' — GAGAL '+txt(err));}catch(e){}\n" +
        "   throw err;});\n" +
        "  }catch(e){return origFetch.apply(window,arguments);}};\n" +
        "}\n" +
        "})();</script>\n";
}
