package com.db.local.server;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Server port GAME — inilah yang "berperan sebagai server" bagi client.
 * Endpoint di assets APK menunjuk ke http://127.0.0.1:&lt;gamePort&gt;, jadi semua
 * permintaan bootstrap client lewat sini. Dua mode (config/server.json):
 *
 * - LOCAL : layani file dari &lt;root&gt;/www/ — hasil ekstraksi OTOMATIS mirror
 *           server ASLI yang dibawa APK (assets/server/www/); struktur folder =
 *           path URL server asli. File setting bin ditulis-ulang hostnya
 *           (SettingBin — satu-satunya konten yang berubah). File yang belum
 *           ada di mirror dicoba diambil SEKALI dari server asli lalu disimpan
 *           ke www/ (mirror melengkapi dirinya sendiri); bila server asli juga
 *           tidak bisa dihubungi → 404 — client melanjutkan boot (fallback dex
 *           terbukti §2.8: "Server base.version is Not Exist").
 * - ONLINE: teruskan permintaan ke server asli (passthrough) apa adanya.
 *
 * Peta passthrough (persis hasil dekode EntryPoint §2.8 + temuan setting bin):
 *   /bs/db/android/… → configus.sjmobilegame.com (fallback COS)
 *   /bs/…            → dragonh5cdn.popoh5.com        (H5 game + folder upgrade)
 *   /lzceshi/…       → dragon.sjmobilegame.com:82
 *
 * Query anti-cache (?rnd= / ?v=) diteruskan ke server asli apa adanya,
 * dan diabaikan saat mencari file lokal.
 *
 * FORMAT LOG REQUEST (koreksi owner 0.3.1 — "jangan tulis LOCAL, tulis URL
 * aslinya"): setiap baris memuat URL utuh yang diminta client, contoh:
 *   GET http://127.0.0.1:8080/bs/upgrade/all.zip → 200 dari www/ · 18,5 MB · 1 ms
 * Tidak ada lagi label "LOCAL"/"ONLINE" di baris request — mode cukup
 * diceritakan di baris status saat boot. Baris yang sama berulang dalam
 * 10 detik TIDAK dicatat ulang (dedupe) — hanya jumlahnya dilaporkan.
 *
 * Halaman root port ini (http://127.0.0.1:&lt;gamePort&gt;/ di browser) sekarang
 * juga berisi TOMBOL GANTI MODE (LOCAL/ONLINE) — koreksi owner "tidak ada mode
 * switch di dalam website": switch tidak hanya di dasbor 4000, tapi juga di
 * halaman port game, jadi di mana pun owner membuka, switch selalu ada.
 */
public final class GameServer extends NanoHTTPD {

    /** Peta passthrough — persis nilai EntryPoint hasil dekode §2.8 + setting bin. */
    private static final String BS_PRIMARY = "https://configus.sjmobilegame.com";
    private static final String BS_FALLBACK = "https://configus-1257066086.cos.na-siliconvalley.myqcloud.com";
    private static final String H5CDN = "https://dragonh5cdn.popoh5.com";
    private static final String DRAGON = "http://dragon.sjmobilegame.com:82";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final Context appCtx;
    private final File wwwRoot;

    public GameServer(Context appCtx, String bind, int port, File wwwRoot) throws java.io.IOException {
        super(bind, port);
        this.appCtx = appCtx;
        this.wwwRoot = wwwRoot;
        setTempFileManagerFactory(new TempFileManagerFactory() {
            public TempFileManager create() {
                return new DefaultTempFileManager();
            }
        });
    }

    @Override
    public Response serve(IHTTPSession session) {
        long t0 = System.currentTimeMillis();
        Method m = session.getMethod();
        String uri = session.getUri(); // path saja, tanpa query
        String query = session.getQueryParameterString();
        String mode = Config.mode();
        Response resp;
        try {
            boolean post = (Method.POST == m);
            if (!post && Method.GET != m && Method.HEAD != m) {
                resp = textResp(Response.Status.METHOD_NOT_ALLOWED,
                        "Metode " + m + " tidak didukung — kontrak bootstrap hanya GET");
            } else if (post && "/api/mode".equals(uri)) {
                resp = handleModeSwitch(session.getParameters());
            } else if ("/".equals(uri) || "/index.html".equals(uri)) {
                resp = rootPage();
            } else if ("local".equals(mode)) {
                resp = serveLocal(uri, query, t0);
            } else {
                resp = servePassthrough(uri, query, t0);
            }
        } catch (Throwable t) {
            StatusStore.countErr();
            try {
                HumanLog.ev('⚠', "Port game gagal memproses " + gameUrl(uri) + " — dijawab 500", String.valueOf(t));
            } catch (Throwable ignored) {
            }
            resp = textResp(Response.Status.INTERNAL_ERROR,
                    "Kesalahan internal port game: " + t);
        }
        int code = 500;
        try {
            code = resp.getStatus().getRequestStatus();
        } catch (Throwable ignored) {
        }
        long ms = System.currentTimeMillis() - t0;
        StatusStore.countReq();
        StatusStore.recordGame(uri, code, ms);
        if (StatusStore.requestTick()) {
            StatusStore.persist();
        }
        return resp;
    }

    // ---------------- halaman root port game: switch mode + status ringkas ----------------

    /** Halaman http://127.0.0.1:&lt;gamePort&gt;/ — switch mode BESAR (koreksi owner 0.3.1). */
    private Response rootPage() {
        String mode = Config.mode();
        String clsLocal = "local".equals(mode) ? " on" : "";
        String clsOnline = "online".equals(mode) ? " on" : "";
        int wwwN = 0;
        try {
            wwwN = StatusStore.wwwFileCount();
        } catch (Throwable ignored) {
        }
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!doctype html><html lang=\"id\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>DB LOCAL · Port Game</title><style>")
                .append("body{background:#f5f5f4;color:#1c1917;font:15px/1.55 -apple-system,\"Segoe UI\",Roboto,Helvetica,Arial,sans-serif;margin:0 auto;padding:20px;max-width:760px}")
                .append("@media(prefers-color-scheme:dark){body{background:#0c0a09;color:#fafaf9}}")
                .append("h1{font-size:19px;margin:8px 0 2px}")
                .append(".sub{color:#78716c;font-size:13.5px;margin-bottom:16px}")
                .append(".btns{display:flex;gap:10px;flex-wrap:wrap}")
                .append(".mbtn{flex:1 1 220px;min-height:76px;border-radius:12px;border:2px solid #e7e5e4;background:#fff;padding:12px 14px;text-align:left;cursor:pointer;font-family:inherit}")
                .append(".mbtn .t{display:block;font-size:22px;font-weight:800;letter-spacing:.5px}")
                .append(".mbtn small{display:block;margin-top:4px;font-size:12.5px;color:#78716c}")
                .append(".mbtn.on{border-color:#059669;background:#ecfdf5}")
                .append(".mbtn.on .t{color:#059669}")
                .append(".mbtn:disabled{opacity:.6;cursor:default}")
                .append("#st{margin-top:14px;font-size:14px;min-height:22px;font-weight:600}")
                .append(".box{background:#fff;border:1px solid #e7e5e4;border-radius:10px;padding:10px 14px;margin-top:16px;font-size:13.5px}")
                .append("@media(prefers-color-scheme:dark){.mbtn{background:#1c1917;border-color:#292524}.mbtn.on{background:rgba(6,78,59,.35)}.box{background:#1c1917;border-color:#292524}}")
                .append("a{color:#059669}")
                .append("</style></head><body>")
                .append("<h1>Port game server lokal</h1>")
                .append("<div class=\"sub\">http://127.0.0.1:").append(Config.gamePort())
                .append(" · endpoint client menunjuk ke sini · mirror www/: ").append(wwwN).append(" file</div>")
                .append("<div class=\"btns\">")
                .append("<button class=\"mbtn").append(clsLocal).append("\" id=\"btn-local\" onclick=\"ganti('local')\">")
                .append("<span class=\"t\">LOCAL</span>")
                .append("<small>dilayani dari folder www/ — mirror server asli (ekstraksi otomatis dari APK)</small></button>")
                .append("<button class=\"mbtn").append(clsOnline).append("\" id=\"btn-online\" onclick=\"ganti('online')\">")
                .append("<span class=\"t\">ONLINE</span>")
                .append("<small>diteruskan apa adanya ke server asli (butuh internet)</small></button>")
                .append("</div>")
                .append("<div id=\"st\"></div>")
                .append("<div class=\"box\">Ganti mode → tersimpan di config/server.json → aplikasi game dibuka ulang otomatis.<br>")
                .append("Tombol melayang di layar game juga bisa: <b>GESER</b> = pindah posisi (tersimpan) · <b>TEKAN</b> = ganti mode.<br>")
                .append("Dasbor lengkap (log, isi www/, pantauan server asli): <a href=\"http://127.0.0.1:")
                .append(Config.debugPort()).append("\">http://127.0.0.1:").append(Config.debugPort()).append("</a></div>")
                .append("<script>")
                .append("var CUR=\"").append(mode).append("\";")
                .append("function ganti(m){")
                .append(" if(m===CUR) return;")
                .append(" if(!confirm('Ganti mode ke '+m.toUpperCase()+'?\\nAplikasi akan dibuka ulang otomatis.')) return;")
                .append(" var st=document.getElementById('st');")
                .append(" st.textContent='mengganti mode…';")
                .append(" fetch('/api/mode?mode='+m,{method:'POST'}).then(function(r){return r.json();}).then(function(j){")
                .append("   st.textContent=j.note||'selesai';")
                .append("   document.getElementById('btn-local').disabled=true;")
                .append("   document.getElementById('btn-online').disabled=true;")
                .append(" }).catch(function(e){st.textContent='gagal: '+e;});")
                .append("}")
                .append("</script></body></html>");
        return htmlResp(sb.toString().getBytes());
    }

    /** POST /api/mode?mode=local|online — sama seperti di dasbor 4000: tulis server.json + restart. */
    private Response handleModeSwitch(Map<String, List<String>> params) {
        String target = (params != null && params.get("mode") != null && !params.get("mode").isEmpty())
                ? params.get("mode").get(0) : null;
        if (!"local".equals(target) && !"online".equals(target)) {
            return jsonResp(Response.Status.BAD_REQUEST,
                    "{\"error\":\"Parameter mode wajib local|online — contoh: POST /api/mode?mode=local\"}");
        }
        String now = Config.mode();
        if (target.equals(now)) {
            return jsonResp(Response.Status.OK,
                    "{\"ok\":true,\"mode\":\"" + target + "\",\"changed\":false,"
                            + "\"note\":\"mode sudah " + target + " — tidak ada restart\"}");
        }
        Config.setMode(target);
        StatusStore.persist();
        HumanLog.ev('ℹ', "SWITCH MODE dari halaman port game (http://127.0.0.1:" + Config.gamePort()
                + "): " + now.toUpperCase() + " → " + target.toUpperCase()
                + " (tersimpan di server.json) — aplikasi direstart agar game membaca mode baru", null);
        Response r = jsonResp(Response.Status.OK,
                "{\"ok\":true,\"mode\":\"" + target + "\",\"changed\":true,"
                        + "\"note\":\"mode diganti ke " + target.toUpperCase() + " — aplikasi direstart otomatis ±1 detik\"}");
        RestartHelper.restart(appCtx);
        return r;
    }

    // ---------------- mode LOCAL: layani dari www/ ----------------

    private Response serveLocal(String uri, String query, long t0) {
        // Setting bin = satu-satunya file yang ditulis-ulang saat disajikan (README §4).
        // Berlaku untuk kedua jalur: bs/db/android (configus) & lzceshi/native_setting/Android
        // (dragon) — keduanya terenkripsi XOR "DragonBall" dan sama-sama menunjuk dragonh5cdn.
        if ((uri.startsWith("/bs/db/android/") || uri.startsWith("/lzceshi/native_setting/Android/"))
                && uri.endsWith(".bin")) {
            return serveSettingBin(uri, query, t0);
        }
        File f = resolveLocal(uri);
        long ms = System.currentTimeMillis() - t0;
        if (f == null) {
            // Mirror belum berisi file ini → ambil SEKALI dari server asli, simpan
            // ke www/ (mirror melengkapi diri), baru disajikan.
            Response seeded = trySeed(uri, query, t0);
            if (seeded != null) {
                return seeded;
            }
            // 404 = perilaku NORMAL (server asli pun tidak memilikinya — client lanjut boot)
            ReqLog.ev('ℹ', "404 " + uri,
                    "GET " + gameUrl(uri) + " → 404 · tidak ada di www/ dan server asli juga tidak memberikannya · "
                            + HumanLog.fmtMs(ms) + " — game melanjutkan boot (perilaku normal)",
                    nullSafe(query));
            return textResp(Response.Status.NOT_FOUND,
                    "Tidak ada di mirror www/" + uri + " — mode LOCAL melayani mirror server asli hasil "
                            + "ekstraksi otomatis dari APK. Game melanjutkan boot saat jawaban tidak ada.");
        }
        return serveFile(f, uri, query, t0, false);
    }

    /** Setting bin: mirror + penulisan-ulang host (satu-satunya konten yang berubah). */
    private Response serveSettingBin(String uri, String query, long t0) {
        File f = resolveLocal(uri);
        if (f == null) {
            // setting_BS_Android.bin = WAJIB ada di mirror (inti bootstrap);
            // .bin lain (mis. com_db_local.bin) memang TIDAK ada di server asli —
            // 404-nya perilaku normal, client melanjutkan boot tanpa error.
            if (uri.endsWith("setting_BS_Android.bin")) {
                ReqLog.ev('⚠', "404 " + uri,
                        "GET " + gameUrl(uri) + " → 404 · mirror www/ TIDAK berisi setting bin — cek ekstraksi mirror",
                        nullSafe(query));
            } else {
                ReqLog.ev('ℹ', "404 " + uri,
                        "GET " + gameUrl(uri) + " → 404 · tidak ada di www/ (di server asli juga tidak ada) · "
                                + HumanLog.fmtMs(System.currentTimeMillis() - t0)
                                + " — game melanjutkan boot (perilaku normal)",
                        nullSafe(query));
            }
            return textResp(Response.Status.NOT_FOUND, "Mirror www/ tidak berisi " + uri);
        }
        try {
            byte[] b = SettingBin.rewrittenLocal(f, Config.gamePort());
            long ms = System.currentTimeMillis() - t0;
            Response r = newFixedLengthResponse(Response.Status.OK, "application/octet-stream",
                    new ByteArrayInputStream(b), b.length);
            r.addHeader("Cache-Control", "no-store");
            ReqLog.ev('◀', "200 " + uri,
                    "GET " + gameUrl(uri) + " → 200 dari www/ · " + HumanLog.fmtBytes(b.length)
                            + " · " + HumanLog.fmtMs(ms) + " — host di dalam bin ditulis-ulang ke http://127.0.0.1:"
                            + Config.gamePort(),
                    nullSafe(query));
            return r;
        } catch (Throwable t) {
            HumanLog.ev('⚠', "GET " + gameUrl(uri) + " gagal menyiapkan setting bin", String.valueOf(t));
            return textResp(Response.Status.INTERNAL_ERROR, "Gagal menyiapkan setting bin: " + uri);
        }
    }

    /** Ambil file yang belum ada di mirror dari server asli, simpan ke www/, sajikan. */
    private Response trySeed(String uri, String query, long t0) {
        String[] base = upstreamBase(uri);
        if (base == null) {
            return null;
        }
        String q = (query != null && query.length() > 0) ? ("?" + query) : "";
        Upstream up = fetchUpstream(base[0] + uri + q);
        if (up.status != 200 || up.in == null) {
            closeQuietly(up.in);
            return null;
        }
        File out = safePath(uri);
        if (out == null) {
            closeQuietly(up.in);
            return null;
        }
        try {
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            OutputStream os = new FileOutputStream(out, false);
            byte[] buf = new byte[65536];
            int n;
            long total = 0;
            while ((n = up.in.read(buf)) > 0) {
                os.write(buf, 0, n);
                total += n;
            }
            os.close();
            closeQuietly(up.in);
            long ms = System.currentTimeMillis() - t0;
            ReqLog.ev('◀', "200 " + uri,
                    "GET " + gameUrl(uri) + " → 200 dari www/ · " + HumanLog.fmtBytes(total)
                            + " · " + HumanLog.fmtMs(ms) + " — file belum ada di mirror: diambil dari "
                            + hostOf(base[0]) + " & disimpan ke www/",
                    nullSafe(query));
            return serveFile(out, uri, query, t0, true);
        } catch (Throwable t) {
            closeQuietly(up.in);
            try {
                HumanLog.ev('⚠', "Gagal menyimpan file hasil ambilan dari server asli ke www/", String.valueOf(t));
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    private Response serveFile(File f, String uri, String query, long t0, boolean seeded) {
        try {
            long len = f.length();
            InputStream in = new FileInputStream(f);
            Response r = newFixedLengthResponse(Response.Status.OK, contentType(uri), in, len);
            r.addHeader("Cache-Control", "no-store");
            long ms = System.currentTimeMillis() - t0;
            ReqLog.ev('◀', "200 " + uri,
                    "GET " + gameUrl(uri) + " → 200 dari www/"
                            + (seeded ? " (baru diambil dari server asli)" : "")
                            + " · " + HumanLog.fmtBytes(len) + " · " + HumanLog.fmtMs(ms),
                    nullSafe(query));
            return r;
        } catch (Throwable t) {
            HumanLog.ev('⚠', "GET " + gameUrl(uri) + " — file ada tapi gagal dibaca", String.valueOf(t));
            return textResp(Response.Status.INTERNAL_ERROR, "File ada tapi gagal dibaca: " + uri);
        }
    }

    /** Resolve file di wwwRoot; path aman (tanpa naik direktori). null bila tidak ada. */
    private File resolveLocal(String uri) {
        File f = safePath(uri);
        if (f == null || !f.exists() || !f.isFile()) {
            return null;
        }
        return f;
    }

    /** Path aman di dalam wwwRoot (blokir ../); boleh belum ada. */
    private File safePath(String uri) {
        try {
            if (wwwRoot == null) {
                return null;
            }
            String path = uri;
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.length() == 0) {
                return null;
            }
            File f = new File(wwwRoot, path);
            String canF = f.getCanonicalPath();
            String canRoot = wwwRoot.getCanonicalPath();
            if (!canF.startsWith(canRoot)) {
                return null; // percobaan keluar folder (../)
            }
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------- mode ONLINE: teruskan ke server asli ----------------

    private Response servePassthrough(String uri, String query, long t0) {
        String[] base = upstreamBase(uri); // [0]=utama, [1]=fallback (boleh null)
        if (base == null) {
            ReqLog.ev('⚠', "404 " + uri,
                    "GET " + gameUrl(uri) + " → 404 · path tidak dipetakan ke server asli — peta: "
                            + "/bs/db/android, /bs, /lzceshi",
                    nullSafe(query));
            return textResp(Response.Status.NOT_FOUND,
                    "Path tidak dipetakan ke server asli: " + uri + " — peta passthrough: bs/db/android, "
                            + "bs (dragonh5cdn), lzceshi.");
        }
        String q = (query != null && query.length() > 0) ? ("?" + query) : "";
        Upstream up = fetchUpstream(base[0] + uri + q);
        String usedHost = base[0];
        if (up.status < 0 || up.status >= 500) {
            if (base[1] != null) {
                HumanLog.ev('⚠', "Server utama gagal (" + (up.status < 0 ? "tak terjangkau" : "HTTP " + up.status)
                        + ") → mencoba fallback COS", null);
                Upstream alt = fetchUpstream(base[1] + uri + q);
                if (alt.status >= 0 && alt.status < 500) {
                    up = alt;
                    usedHost = base[1];
                }
            }
        }
        long ms = System.currentTimeMillis() - t0;
        if (up.status < 0) {
            ReqLog.ev('⚠', "502 " + uri,
                    "GET " + gameUrl(uri) + " → 502 · server asli tidak terjangkau · " + HumanLog.fmtMs(ms)
                            + " — coba mode LOCAL bila www/ sudah terisi",
                    nullSafe(query));
            return textResp(Response.Status.lookup(502),
                    "Server asli tidak terjangkau untuk " + uri + " — coba mode LOCAL bila www/ sudah terisi.");
        }
        ReqLog.ev('◀', up.status + " " + uri,
                "GET " + gameUrl(uri) + " → HTTP " + up.status + " dari server asli · diteruskan ke "
                        + usedHost + uri + (up.length >= 0 ? " · " + HumanLog.fmtBytes(up.length) : "")
                        + " · " + HumanLog.fmtMs(ms),
                nullSafe(query));
        Response r;
        if (up.length >= 0 && up.in != null) {
            r = newFixedLengthResponse(Response.Status.lookup(up.status), up.contentType, up.in, up.length);
        } else if (up.in != null) {
            r = newChunkedResponse(Response.Status.lookup(up.status), up.contentType, up.in);
        } else {
            r = newFixedLengthResponse(Response.Status.lookup(up.status), up.contentType, "");
        }
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private static final class Upstream {
        int status = -1;
        long length = -1;
        String contentType = "application/octet-stream";
        InputStream in;
    }

    private Upstream fetchUpstream(String target) {
        Upstream up = new Upstream();
        HttpURLConnection conn = null;
        try {
            URL u = new URL(target);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            up.status = conn.getResponseCode();
            up.contentType = conn.getContentType();
            if (up.contentType == null || up.contentType.length() == 0) {
                up.contentType = contentType(new URL(target).getPath());
            }
            up.length = conn.getContentLength();
            up.in = (up.status >= 400) ? conn.getErrorStream() : conn.getInputStream();
        } catch (Throwable t) {
            up.status = -1;
            up.in = null;
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Throwable ignored) {
            }
        }
        return up;
    }

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Peta path → server asli, sesuai EntryPoint (README §2.8) + isi setting bin. */
    private static String[] upstreamBase(String uri) {
        if (uri.startsWith("/bs/db/android")) {
            return new String[]{BS_PRIMARY, BS_FALLBACK};
        }
        if (uri.startsWith("/bs/")) {
            return new String[]{H5CDN, null};
        }
        if (uri.startsWith("/lzceshi/")) {
            return new String[]{DRAGON, null};
        }
        return null;
    }

    // ---------------- util ----------------

    /** URL utuh yang diminta client — dipakai di SEMUA baris log request (koreksi 0.3.1). */
    private static String gameUrl(String uri) {
        return "http://127.0.0.1:" + Config.gamePort() + uri;
    }

    private static String hostOf(String base) {
        try {
            return new URL(base).getHost();
        } catch (Throwable t) {
            return base;
        }
    }

    private static String nullSafe(String q) {
        return (q == null || q.length() == 0) ? "-" : q;
    }

    /**
     * Dedupe log request kembar (koreksi owner 0.3.1 — log tidak boleh membanjiri):
     * baris dengan kunci sama (status + path) dalam jendela 10 detik TIDAK dicatat
     * ulang; saat jendela lewat, jumlah baris yang ditenyapkan dilaporkan sekali.
     */
    private static final class ReqLog {
        private static final HashMap<String, long[]> SEEN = new HashMap<String, long[]>();
        private static final long WINDOW_MS = 10000L;

        static void ev(char icon, String key, String sentence, String detail) {
            long now = System.currentTimeMillis();
            long[] e;
            boolean suppress;
            synchronized (SEEN) {
                if (SEEN.size() > 256) {
                    SEEN.clear(); // pengaman memori — kunci hanya status+path
                }
                e = SEEN.get(key);
                if (e == null) {
                    e = new long[2];
                    SEEN.put(key, e);
                }
                suppress = (e[0] > 0 && now - e[0] < WINDOW_MS);
                if (suppress) {
                    e[1]++;
                    return;
                }
                e[0] = now;
            }
            long n = e[1];
            e[1] = 0;
            if (n > 0) {
                sentence = sentence + " · (+" + n + " request kembar dalam 10 dtk tidak dicatat)";
            }
            HumanLog.ev(icon, sentence, detail);
        }
    }

    private static String contentType(String uri) {
        String u = uri.toLowerCase();
        if (u.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (u.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (u.endsWith(".version") || u.endsWith(".txt") || u.endsWith(".properties") || u.endsWith(".cfg")) {
            return "text/plain; charset=utf-8";
        }
        if (u.endsWith(".html") || u.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (u.endsWith(".png")) {
            return "image/png";
        }
        if (u.endsWith(".jpg") || u.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (u.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    private static Response textResp(Response.IStatus st, String msg) {
        Response r = newFixedLengthResponse(st, "text/plain; charset=utf-8", msg);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private static Response jsonResp(Response.IStatus st, String json) {
        Response r = newFixedLengthResponse(st, "application/json; charset=utf-8", json);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private static Response htmlResp(byte[] d) {
        Response r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8",
                new ByteArrayInputStream(d), d.length);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }
}
