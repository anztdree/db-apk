package com.db.local.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
 */
public final class GameServer extends NanoHTTPD {

    /** Peta passthrough — persis nilai EntryPoint hasil dekode §2.8 + setting bin. */
    private static final String BS_PRIMARY = "https://configus.sjmobilegame.com";
    private static final String BS_FALLBACK = "https://configus-1257066086.cos.na-siliconvalley.myqcloud.com";
    private static final String H5CDN = "https://dragonh5cdn.popoh5.com";
    private static final String DRAGON = "http://dragon.sjmobilegame.com:82";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final File wwwRoot;

    public GameServer(String bind, int port, File wwwRoot) throws java.io.IOException {
        super(bind, port);
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
            if (Method.GET != m && Method.HEAD != m) {
                resp = textResp(Response.Status.METHOD_NOT_ALLOWED,
                        "Metode " + m + " tidak didukung — kontrak bootstrap hanya GET");
            } else if ("/".equals(uri) || "/index.html".equals(uri)) {
                String msg = "<!doctype html><html><head><meta charset=\"utf-8\">"
                        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                        + "<body style=\"font-family:sans-serif;padding:24px\">"
                        + "<h2>Port game server lokal</h2>"
                        + "<p>Endpoint client menunjuk ke port ini. Mode sekarang: <b>" + mode.toUpperCase()
                        + "</b>.</p><p>Mode ONLINE = diteruskan ke server asli · Mode LOCAL = dilayani dari folder "
                        + "www/ (mirror server asli, ekstraksi otomatis dari APK).</p>"
                        + "</body></html>";
                resp = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", msg);
            } else if ("local".equals(mode)) {
                resp = serveLocal(uri, query, t0);
            } else {
                resp = servePassthrough(uri, query, t0);
            }
        } catch (Throwable t) {
            StatusStore.countErr();
            try {
                HumanLog.ev('⚠', "Port game gagal memproses " + uri + " — dijawab 500", String.valueOf(t));
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
            HumanLog.ev('ℹ', "LOCAL Game GET " + uri + " → tidak ada di www/ dan server asli tidak "
                            + "memberikannya (404) · " + HumanLog.fmtMs(ms)
                            + " — game melanjutkan boot (fallback dex terbukti §2.8)", nullSafe(query));
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
            HumanLog.ev('⚠', "LOCAL Game GET " + uri + " → mirror tidak berisi setting bin (404)", nullSafe(query));
            return textResp(Response.Status.NOT_FOUND, "Mirror www/ tidak berisi " + uri);
        }
        try {
            byte[] b = SettingBin.rewrittenLocal(f, Config.gamePort());
            long ms = System.currentTimeMillis() - t0;
            Response r = newFixedLengthResponse(Response.Status.OK, "application/octet-stream",
                    new ByteArrayInputStream(b), b.length);
            r.addHeader("Cache-Control", "no-store");
            HumanLog.ev('◀', "LOCAL Game GET " + uri + " → mirror + host ditulis-ulang ke 127.0.0.1:"
                    + Config.gamePort() + " (" + HumanLog.fmtBytes(b.length) + ") · " + HumanLog.fmtMs(ms),
                    nullSafe(query));
            return r;
        } catch (Throwable t) {
            HumanLog.ev('⚠', "LOCAL Game GET " + uri + " → gagal menyiapkan setting bin", String.valueOf(t));
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
            HumanLog.ev('ℹ', "LOCAL Game GET " + uri + " → belum ada di www/, diambil SEKALI dari "
                    + hostOf(base[0]) + " (" + HumanLog.fmtBytes(total) + ") dan DISIMPAN ke www/ "
                    + "— mirror melengkapi dirinya sendiri", nullSafe(query));
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
            HumanLog.ev('◀', "LOCAL Game GET " + uri + " → dilayani dari www/"
                    + (seeded ? " (baru diambil dari server asli)" : "") + " ("
                    + HumanLog.fmtBytes(len) + ") · " + HumanLog.fmtMs(ms), nullSafe(query));
            return r;
        } catch (Throwable t) {
            HumanLog.ev('⚠', "LOCAL Game GET " + uri + " → file ada tapi gagal dibaca", String.valueOf(t));
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
            HumanLog.ev('⚠', "ONLINE Game GET " + uri + " → path tidak dipetakan ke server asli (404)", nullSafe(query));
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
            HumanLog.ev('⚠', "ONLINE Game GET " + uri + " → server asli TIDAK terjangkau · " + HumanLog.fmtMs(ms),
                    nullSafe(query));
            return textResp(Response.Status.lookup(502),
                    "Server asli tidak terjangkau untuk " + uri + " — coba mode LOCAL bila www/ sudah terisi.");
        }
        HumanLog.ev('◀', "ONLINE Game GET " + uri + " → " + hostOf(usedHost) + " (HTTP " + up.status
                + (up.length >= 0 ? " · " + HumanLog.fmtBytes(up.length) : "") + ") · " + HumanLog.fmtMs(ms),
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
}
