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

import fi.iki.elonen.NanoHTTPD;

/**
 * Server port GAME — inilah yang "berperan sebagai server" bagi client.
 * Endpoint di assets APK menunjuk ke http://127.0.0.1:&lt;gamePort&gt;, jadi semua
 * permintaan bootstrap client lewat sini. Dua mode (config/server.json):
 *
 * - LOCAL : layani file dari &lt;root&gt;/www/ — hasil ekstraksi OTOMATIS mirror
 *           server ASLI yang dibawa APK (assets/server/www/); struktur folder =
 *           path URL server asli. File yang ditulis-ulang (PR1/PR3 0.4.0):
 *           1) setting bin — host H5 + endpoint LOGIN (SettingBin)
 *           2) serversetting.json — "loginserver" → server login lokal
 *           (dua-duanya satu-satunya konten yang berubah). File yang belum
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
 * FORMAT LOG REQUEST (0.4.6 — model final log, kesepakatan owner:
 * "group itu kalau terdeteksi emang config/resource yg banyak. kalau alur ya
 * gk usah di group"): SEMUA aktivitas batch per-file TIDAK PERNAH jadi baris
 * log linear — masuk GRUP LogGroups (dasbor: satu baris judul dengan statistik
 * hidup, bisa dibuka untuk rincian; human.log: ringkasan Σ satu baris):
 *   "mirror" → Mirror CDN   (file baru diambil dari server asli → www/)
 *   "req"    → Request game (setiap 200 dari www/, termasuk setting bin)
 *   "404"    → 404 fallback (normal — game melanjutkan boot)
 *   "pass"   → Passthrough ONLINE (jawaban server asli mode ONLINE)
 * Kegagalan (CDN tak terjangkau, upstream 5xx) NAIK OTOMATIS sebagai ⚠ linear
 * (jeda 30 dtk antar laporan — lanjutannya hanya menaikkan hitung grup).
 * Yang tetap PENTING (linear): mirror tanpa setting bin, 404 path tak
 * terpetakan, 502 server asli tak terjangkau, file gagal dibaca, 500.
 *
 * Halaman root port ini (http://127.0.0.1:&lt;gamePort&gt;/ di browser) = STATUS
 * MINI tanpa tombol apa pun (koreksi owner 0.3.2 — "website pindah mode itu
 * salah cuma menuhin tempat"): switch mode DIHAPUS dari semua halaman web.
 * Satu-satunya cara ganti mode = tombol melayang di layar game (FloatingToggle).
 * Endpoint POST /api/mode juga dihapus karena tidak ada lagi pemakainya.
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
                HumanLog.ev("⚠️", "Port game gagal memproses " + gameUrl(uri) + " — dijawab 500", String.valueOf(t));
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

    // ---------------- halaman root port game: status mini tanpa tombol ----------------

    /**
     * Halaman http://127.0.0.1:&lt;gamePort&gt;/ — status mini (koreksi owner 0.3.2:
     * "website pindah mode itu salah cuma menuhin tempat"). TANPA tombol, TANPA
     * JavaScript — cuma tiga baris keterangan supaya developer bisa memastikan
     * port game hidup. Ganti mode = tombol melayang di layar game.
     */
    private Response rootPage() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("<!doctype html><html lang=\"id\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>DB LOCAL · Port Game</title><style>")
                .append("body{font:15px/1.6 -apple-system,\"Segoe UI\",Roboto,Helvetica,Arial,sans-serif;")
                .append("color:#1c1917;background:#f5f5f4;margin:0;padding:24px}")
                .append("@media(prefers-color-scheme:dark){body{background:#0c0a09;color:#fafaf9}}")
                .append("</style></head><body>")
                .append("Server lokal hidup — mode <b>").append(Config.mode().toUpperCase())
                .append("</b> · port game ").append(Config.gamePort())
                .append(" · endpoint client menunjuk ke sini.<br>")
                .append("Login lokal: http://127.0.0.1:").append(Config.loginPort())
                .append(" (engine.io v3 + socket.io v2) · dasbor developer: http://127.0.0.1:")
                .append(Config.debugPort())
                .append(" · ganti mode: tombol melayang di layar game.")
                .append("</body></html>");
        return htmlResp(sb.toString().getBytes());
    }

    // ---------------- mode LOCAL: layani dari www/ ----------------

    private Response serveLocal(String uri, String query, long t0) {
        // Setting bin = ditulis-ulang saat disajikan (README §4 + PR1 0.4.0).
        // Berlaku untuk kedua jalur: bs/db/android (configus) & lzceshi/native_setting/Android
        // (dragon) — keduanya terenkripsi XOR "DragonBall" dan sama-sama menunjuk dragonh5cdn.
        if ((uri.startsWith("/bs/db/android/") || uri.startsWith("/lzceshi/native_setting/Android/"))
                && uri.endsWith(".bin")) {
            return serveSettingBin(uri, query, t0);
        }
        // serversetting.json = peta login yang dibaca H5 (main.min connectToLogin →
        // serversetting.json, karena bridge native getLoginServer TIDAK ada di dex — §2.10).
        // "loginserver" ditulis-ulang ke server login lokal (PR1 — endpoint tak lagi ke pusat).
        if (uri.endsWith("/serversetting.json")) {
            return serveServersetting(uri, query, t0);
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
            // 404 = perilaku NORMAL (server asli pun tidak memilikinya — client lanjut
            // boot). 0.4.6: masuk GRUP "404 fallback" — bukan baris log linear.
            StatusStore.count404();
            LogGroups.item("404", "404 fallback", "file", true,
                    "GET " + uri + " — normal", 0);
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
                ReqLog.ev("⚠️", "404 " + uri,
                        "GET " + gameUrl(uri) + " → 404 · mirror www/ TIDAK berisi setting bin — cek ekstraksi mirror",
                        nullSafe(query));
            } else {
                StatusStore.count404();
                LogGroups.item("404", "404 fallback", "file", true,
                        "GET " + uri + " — normal", 0);
            }
            return textResp(Response.Status.NOT_FOUND, "Mirror www/ tidak berisi " + uri);
        }
        try {
            String uidSuffix = SettingBin.currentUserIdSuffix();
            byte[] b = SettingBin.rewrittenLocal(f, Config.gamePort(), Config.loginPort(),
                    Config.useSdk() ? null : uidSuffix);
            long ms = System.currentTimeMillis() - t0;
            Response r = newFixedLengthResponse(Response.Status.OK, "application/octet-stream",
                    new ByteArrayInputStream(b), b.length);
            r.addHeader("Cache-Control", "no-store");
            LogGroups.item("req", "Request game", "request", true,
                    "GET " + uri + " — " + HumanLog.fmtBytes(b.length) + " · " + HumanLog.fmtMs(ms)
                            + " · endpoint di dalam bin ditulis-ulang ke lokal",
                    b.length);
            return r;
        } catch (Throwable t) {
            HumanLog.ev("⚠️", "GET " + gameUrl(uri) + " gagal menyiapkan setting bin", String.valueOf(t));
            return textResp(Response.Status.INTERNAL_ERROR, "Gagal menyiapkan setting bin: " + uri);
        }
    }

    /**
     * serversetting.json — nilai "loginserver" (asal https://login.popoh5.com:610)
     * ditulis-ulang ke http://127.0.0.1:&lt;loginPort&gt; saat disajikan di mode LOCAL
     * (PR1). File asli di www/ tidak pernah disentuh — hanya jawaban server ini.
     */
    private Response serveServersetting(String uri, String query, long t0) {
        File f = resolveLocal(uri);
        if (f == null) {
            trySeed(uri, query, t0);
            f = resolveLocal(uri);
        }
        if (f == null) {
            StatusStore.count404();
            LogGroups.item("404", "404 fallback", "file", true,
                    "GET " + uri + " — normal", 0);
            return textResp(Response.Status.NOT_FOUND, "Mirror www/ tidak berisi " + uri);
        }
        try {
            String text = readFileText(f);
            String rewritten = SettingBin.setJsonValue(text, "loginserver",
                    "http://127.0.0.1:" + Config.loginPort());
            byte[] out = rewritten.getBytes("UTF-8");
            long ms = System.currentTimeMillis() - t0;
            Response r = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8",
                    new ByteArrayInputStream(out), out.length);
            r.addHeader("Cache-Control", "no-store");
            LogGroups.item("req", "Request game", "request", true,
                    "GET " + uri + " — " + HumanLog.fmtBytes(out.length) + " · " + HumanLog.fmtMs(ms)
                            + " · loginserver ditulis-ulang ke lokal",
                    out.length);
            return r;
        } catch (Throwable t) {
            HumanLog.ev("⚠️", "GET " + gameUrl(uri) + " gagal menyiapkan serversetting.json", String.valueOf(t));
            return textResp(Response.Status.INTERNAL_ERROR, "Gagal menyiapkan serversetting.json: " + uri);
        }
    }

    private static String readFileText(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream((int) Math.max(32, f.length()));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
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
            if (up.status < 0) {
                // CDN/server asli TIDAK TERJANGKAU (bukan sekadar 404) — gagal
                // nyata: naik ⚠ linear (jeda 30 dtk), lanjutannya dihitung grup.
                LogGroups.item("mirror", "Mirror CDN", "file", false,
                        uri + " — server asli tidak terjangkau (dicoba ulang saat diminta lagi)", 0);
            }
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
            StatusStore.addSeed(total);
            LogGroups.item("mirror", "Mirror CDN", "file", true,
                    uri + " — " + HumanLog.fmtBytes(total) + " dari " + hostOf(base[0])
                            + " → disimpan ke www/ · " + HumanLog.fmtMs(ms),
                    total);
            return serveFile(out, uri, query, t0, true);
        } catch (Throwable t) {
            closeQuietly(up.in);
            try {
                HumanLog.ev("⚠️", "Gagal menyimpan file hasil ambilan dari server asli ke www/", String.valueOf(t));
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
            LogGroups.item("req", "Request game", "request", true,
                    "GET " + uri + " — " + HumanLog.fmtBytes(len) + " · " + HumanLog.fmtMs(ms),
                    len);
            return r;
        } catch (Throwable t) {
            HumanLog.ev("⚠️", "GET " + gameUrl(uri) + " — file ada tapi gagal dibaca", String.valueOf(t));
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
            ReqLog.ev("⚠️", "404 " + uri,
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
                HumanLog.ev("⚠️", "Server utama gagal (" + (up.status < 0 ? "tak terjangkau" : "HTTP " + up.status)
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
            ReqLog.ev("⚠️", "502 " + uri,
                    "GET " + gameUrl(uri) + " → 502 · server asli tidak terjangkau · " + HumanLog.fmtMs(ms)
                            + " — coba mode LOCAL bila www/ sudah terisi",
                    nullSafe(query));
            return textResp(Response.Status.lookup(502),
                    "Server asli tidak terjangkau untuk " + uri + " — coba mode LOCAL bila www/ sudah terisi.");
        }
        // 0.4.6: jawaban server asli = GRUP "Passthrough ONLINE" — 5xx naik ⚠.
        LogGroups.item("pass", "Passthrough ONLINE", "request", up.status < 500,
                "GET " + uri + " — HTTP " + up.status
                        + (up.length >= 0 ? " · " + HumanLog.fmtBytes(up.length) : "")
                        + " · " + HumanLog.fmtMs(ms) + " · dari " + hostOf(usedHost),
                Math.max(0, up.length));
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
     * Dedupe baris request PENTING kembar (404 tak terpetakan / 502 — koreksi
     * 0.3.1): kunci sama dalam jendela 10 dtk tidak dicatat ulang; saat jendela
     * lewat, jumlah yang ditenyapkan dilaporkan sekali. 0.4.6: hanya baris
     * penting — baris batch sudah pindah ke LogGroups (grup dasbor).
     */
    private static final class ReqLog {
        private static final HashMap<String, long[]> SEEN = new HashMap<String, long[]>();
        private static final long WINDOW_MS = 10000L;

        /** Baris request PENTING (404 tak terpetakan, 502) — selalu tampil. */
        static void ev(String icon, String key, String sentence, String detail) {
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
