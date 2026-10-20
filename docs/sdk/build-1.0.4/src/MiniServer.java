package com.db.local;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * MiniServer 1.0.4 — SDK lokal sampai GERBANG LOGINSERVER (tahap depan+tengah
 * +pintu belakang; dunia JS di luar pintu = R1').
 *
 * Perubahan vs 1.0.3 (keberatan user: "masuk ranah database !! password
 * cocok login !!" — loginByName/registerUser 1.0.3 menerima apa pun tanpa
 * memeriksa; tidak setia server asli):
 *   DATABASE AKUN (accounts.txt di external/server, TSV; uid per akun — bukti
 *   HAR #32 uid=16722253 utk akun yihonew48…) + validasi password (dex:
 *   registerUser/loginByName params {username, password}; envelope live:
 *   password = MD5 klien):
 *     - loginByName: tak dikenal / password salah → result:false bentuk error
 *       live → SDK tampilkan pesan; cocok → sesi akun itu (uid akun)
 *     - registerUser: duplikat ditolak; baru → baris akun + uid unik
 *     - autoLogin: Opsi A 1.0.3 + lastUid (sesi akun utuh antar boot)
 *     - getUserInfo: params {authToken, uid} → jawab per sesi/akun
 *   Bukti lengkap: docs/sdk/BUILD-1.0.4.md
 *
 * Perubahan vs 1.0.2 (bug dari HAR perangkat nyata, log/sdk.har @ 5a580c3):
 *   POST /v1/user/autoLogin di perangkat dijawab 404 (4x: entri 26,27,75,76)
 *   → boot berikutnya SDK tidak bisa masuk otomatis, terdampar di layar login.
 *   Fix: rute autoLogin → generator (Opsi A, perilaku setia server asli):
 *     - authToken yang masuk DIPERIKSA terhadap token yang server ini terbitkan
 *       (persist di generator.json); cocok → sesi akun itu (login bertahan
 *       antar boot); token asing/kosong → result:false (bentuk error live
 *       panen 40008) → SDK membuka layar login, persis server asli saat token
 *       kadaluarsa. Bukti parser dex: dump-login-endpoint-parser.txt —
 *       autoLogin params = {authToken}, parser = getBoolean("result") →
 *       getString("data") → parser sesi sama registerVisitor.
 *
 * Perubahan vs tes1 (semua berjangkar bukti, lihat docs/sdk/RENCANA-GERBANG-LOGIN.md):
 *   1. RE-SYNC VERSI MIRROR: mirror/VERSION ditandai; bila versi di external
 *      beda → seluruh server/ di-sync ulang (file lama — termasuk panenan tes1
 *      & bin gerbang lama — diganti), generator.json DIPERTAHANKAN.
 *      (Lubang upgrade: copyDir lama melewati file ada → config lama bertahan.)
 *   2. GENERATOR sesi (DbGen): registerVisitor/registerUser/loginByName/autoLogin/
 *      getUserInfo dijawab dari TEMPLATE mirror + token baru (koreksi user:
 *      generate, bukan jejak). 1.0.3: autoLogin memvalidasi authToken terbitan
 *      (persist sesi) — login bertahan antar boot.
 *   3. GERBANG loginchecknative dijawab LOKAL: echo uid/username dari query,
 *      loginToken baru per panggilan, sign stabil per uid (pola server asli).
 *      Alamat gerbang = config setting bin (loginServer→127.0.0.1:28110).
 *   4. Body POST DITANGKAP (sebelumnya hanya dibuang) untuk decode envelope.
 *
 * Tidak berubah vs tes1/1.0.1/1.0.2: port 28110, bootstrapSync deadline 8 dtk +
 * .tmp+rename, urutan resolve (external files → assets), parsePath (buang
 * query, reject ".."), Connection: close, setSoTimeout(8000), rute qsdk ganda,
 * mime v1/* → text/json.
 */
public class MiniServer implements Runnable {

    private static Context APP = null;
    private static boolean started = false;
    private static long DEADLINE = 0L;

    /** Batas tangkap body POST (envelope SDK ≤ ~2 KB; jaga-jaga). */
    private static final long MAX_BODY = 1048576L;

    public static void start(Context ctx) {
        try {
            if (started) {
                return;
            }
            started = true;
            Context app = ctx.getApplicationContext();
            APP = app;
            bootstrapSync(app);
            DbGen.load(app);
            Thread t = new Thread(new MiniServer());
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
        }
    }

    // ================= BOOTSTRAP + RE-SYNC VERSI =================

    private static void bootstrapSync(Context ctx) {
        try {
            DEADLINE = System.currentTimeMillis() + 8000L;
            File ext = ctx.getExternalFilesDir(null);
            if (ext == null) {
                Log.i("MiniServer", "bootstrap: storage eksternal null");
                return;
            }
            File serverDir = new File(ext, "server");
            String want = assetText(ctx, "mirror/VERSION");       // mis. "1.0.2"
            String cur = fileText(new File(serverDir, "VERSION")); // null bila belum ada
            if (want != null && cur != null && want.equals(cur)) {
                int n = copyDir(ctx.getAssets(), "mirror", serverDir);
                Log.i("MiniServer", "bootstrap: " + n + " file baru (versi sama: " + want + ")");
            } else {
                // Pertama / upgrade versi: sinkron penuh supaya config 1.0.4 pasti
                // dipakai (file sisa instalasi lama TIDAK membekas), KECUALI
                // generator.json (state identitas/sesi) dan accounts.txt
                // (DATABASE akun milik user — 1.0.4; re-sync TIDAK boleh
                // menghapus akun terdaftar).
                File gen = new File(serverDir, DbGen.GEN_FILE);
                File accts = new File(serverDir, DbGen.ACCOUNTS_FILE);
                byte[] keepGen = gen.isFile() ? readFileBytes(gen) : null;
                byte[] keepAcct = accts.isFile() ? readFileBytes(accts) : null;
                deleteRecursive(serverDir);
                serverDir.mkdirs();
                int n = copyDir(ctx.getAssets(), "mirror", serverDir);
                Log.i("MiniServer", "re-sync " + (cur == null ? "(kosong)" : cur)
                        + " -> " + want + ": " + n + " file");
                if (keepGen != null) {
                    writeFileBytes(gen, keepGen);
                }
                if (keepAcct != null) {
                    writeFileBytes(accts, keepAcct);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int copyDir(AssetManager am, String assetPath, File target) {
        int count = 0;
        try {
            if (System.currentTimeMillis() > DEADLINE) {
                return count;
            }
            String[] children = am.list(assetPath);
            if (children == null) {
                return count;
            }
            if (children.length == 0) {
                if (System.currentTimeMillis() > DEADLINE) {
                    return count;
                }
                if (!target.exists()) {
                    File parent = target.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    File tmp = new File(target.getAbsolutePath() + ".tmp");
                    InputStream in = am.open(assetPath);
                    FileOutputStream out = new FileOutputStream(tmp);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                    out.close();
                    in.close();
                    if (tmp.renameTo(target)) {
                        count++;
                    }
                }
            } else {
                for (int i = 0; i < children.length; i++) {
                    count += copyDir(am, assetPath + "/" + children[i],
                            new File(target, children[i]));
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    private static void deleteRecursive(File f) {
        try {
            if (f == null || !f.exists()) {
                return;
            }
            if (f.isDirectory()) {
                File[] ch = f.listFiles();
                if (ch != null) {
                    for (int i = 0; i < ch.length; i++) {
                        deleteRecursive(ch[i]);
                    }
                }
            }
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    private static String assetText(Context ctx, String asset) {
        try {
            InputStream in = ctx.getAssets().open(asset);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            in.close();
            return new String(bos.toByteArray(), "UTF-8").trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String fileText(File f) {
        try {
            if (!f.isFile()) {
                return null;
            }
            return new String(readFileBytes(f), "UTF-8").trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readFileBytes(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            in.close();
            return bos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static void writeFileBytes(File f, byte[] b) {
        try {
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(b);
            out.close();
            tmp.renameTo(f);
        } catch (Throwable ignored) {
        }
    }

    // ================= SERVER =================

    @Override
    public void run() {
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress("127.0.0.1", 28110));
            while (true) {
                Thread t = new Thread(new ConnRun(ss.accept()));
                t.setDaemon(true);
                t.start();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void handle(Socket sock) {
        try {
            sock.setSoTimeout(8000);
            InputStream in = sock.getInputStream();
            String reqLine = readLine(in);
            byte[] body = drainRequest(in);
            String path = reqLine == null ? null : parsePath(reqLine);
            if (path != null) {
                // ---- GERBANG LOGINSERVER (dilayani lokal; alamat dari setting bin) ----
                if (path.equals("loginchecknative")) {
                    String res = DbGen.gateJson(APP,
                            queryValue(reqLine, "uid"),
                            queryValue(reqLine, "username"));
                    respond(sock, true, res.getBytes("UTF-8"), "text/json");
                    logReq("200 gate", path);
                    return;
                }
                // ---- ENDPOINT SESI GENERATOR (bentuk dari template mirror) ----
                if (path.equals("v1/user/registerVisitor")) {
                    serveJson(sock, DbGen.registerVisitorJson(APP), path);
                    return;
                }
                if (path.equals("v1/user/registerUser")) {
                    serveJson(sock, DbGen.registerUserJson(APP, body), path);
                    return;
                }
                if (path.equals("v1/user/loginByName")) {
                    serveJson(sock, DbGen.loginByNameJson(APP, body), path);
                    return;
                }
                if (path.equals("v1/user/autoLogin")) {
                    serveJson(sock, DbGen.autoLoginJson(APP, body), path);
                    return;
                }
                if (path.equals("v1/auth/getUserInfo")) {
                    serveJson(sock, DbGen.getUserInfoJson(APP, body), path);
                    return;
                }
                // ---- STATIK (config boot & asuransi R2) ----
                byte[] st = resolve(path);
                if (st != null) {
                    String mime = path.startsWith("v1/") ? "text/json" : mimeFor(path);
                    respond(sock, true, st, mime);
                    logReq("200", path);
                    return;
                }
            }
            respond404(sock);
            logReq("404", path == null ? "(malformed)" : path);
        } catch (Throwable t) {
            try {
                sock.shutdownOutput();
            } catch (Throwable ignored) {
            }
        } finally {
            try {
                sock.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void serveJson(Socket sock, String body, String label) throws Exception {
        if (body == null) {
            // template hilang (tidak seharusnya) → 404 agar tak mengirim sampah
            respond404(sock);
            logReq("404 template", label);
            return;
        }
        respond(sock, true, body.getBytes("UTF-8"), "text/json");
        logReq("200 gen", label);
    }

    // ================= PARSER REQUEST =================

    /** Habiskan header + tangkap body request (utk decode envelope sesi). */
    private static byte[] drainRequest(InputStream in) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            long contentLength = -1L;
            boolean chunked = false;
            String line;
            while ((line = readLine(in)) != null && line.length() != 0) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    String v = line.substring(15).trim();
                    try {
                        contentLength = Long.parseLong(v);
                    } catch (Throwable ignored) {
                    }
                } else if (lower.startsWith("transfer-encoding:")) {
                    if (line.substring(18).toLowerCase().contains("chunked")) {
                        chunked = true;
                    }
                }
            }
            if (chunked) {
                while (true) {
                    String sizeLine = readLine(in);
                    if (sizeLine == null) {
                        break;
                    }
                    String hex = sizeLine.trim();
                    int sp = hex.indexOf(';');
                    if (sp >= 0) {
                        hex = hex.substring(0, sp);
                    }
                    long sz;
                    try {
                        sz = Long.parseLong(hex, 16);
                    } catch (Throwable t) {
                        break;
                    }
                    if (sz <= 0) {
                        break;
                    }
                    copyFully(in, bos, sz);
                    readLine(in); // CRLF sesudah chunk
                }
                String t;
                while ((t = readLine(in)) != null && t.length() != 0) {
                    // trailer
                }
            } else if (contentLength > 0) {
                copyFully(in, bos, Math.min(contentLength, MAX_BODY));
            }
        } catch (Throwable ignored) {
        }
        return bos.toByteArray();
    }

    private static void copyFully(InputStream in, OutputStream out, long n) {
        byte[] buf = new byte[8192];
        try {
            while (n > 0) {
                int want = (int) Math.min((long) buf.length, n);
                int r = in.read(buf, 0, want);
                if (r == -1) {
                    return;
                }
                out.write(buf, 0, r);
                n -= r;
            }
        } catch (Throwable ignored) {
        }
    }

    private static String parsePath(String reqLine) {
        try {
            String[] parts = reqLine.split(" ");
            if (parts.length >= 2) {
                String p = parts[1];
                int q = p.indexOf('?');
                if (q >= 0) {
                    p = p.substring(0, q);
                }
                if (p.length() > 0 && p.charAt(0) == '/') {
                    p = p.substring(1);
                }
                if (!p.isEmpty() && !p.contains("..")) {
                    return p;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Nilai query dgn kunci tepat dari request line (URL-decode per nilai). */
    private static String queryValue(String reqLine, String key) {
        try {
            int a = reqLine.indexOf('?');
            if (a < 0) {
                return null;
            }
            int b = reqLine.indexOf(' ', a);
            if (b < 0) {
                b = reqLine.length();
            }
            String q = reqLine.substring(a + 1, b);
            String[] parts = q.split("&");
            for (int i = 0; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                if (parts[i].substring(0, eq).equals(key)) {
                    try {
                        return java.net.URLDecoder.decode(parts[i].substring(eq + 1), "UTF-8");
                    } catch (Throwable t) {
                        return parts[i].substring(eq + 1);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Kandidat ganda utk path qsdk ("v1/*" → "qsdk/v1/*"), external → assets. */
    private static byte[] resolve(String path) {
        try {
            if (APP == null) {
                return null;
            }
            String[] candidates;
            if (path.startsWith("v1/")) {
                candidates = new String[]{path, "qsdk/" + path};
            } else {
                candidates = new String[]{path};
            }
            File ext = APP.getExternalFilesDir(null);
            if (ext != null) {
                for (int i = 0; i < candidates.length; i++) {
                    File f = new File(new File(ext, "server"), candidates[i]);
                    if (f.isFile()) {
                        FileInputStream fis = new FileInputStream(f);
                        byte[] b = readAll(fis);
                        fis.close();
                        return b;
                    }
                }
            }
            for (int i = 0; i < candidates.length; i++) {
                try {
                    InputStream in = APP.getAssets().open("mirror/" + candidates[i]);
                    byte[] b = readAll(in);
                    in.close();
                    return b;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String mimeFor(String path) {
        if (path.endsWith(".json")) {
            return "application/json";
        }
        if (path.endsWith(".html")) {
            return "text/html";
        }
        if (path.endsWith(".js")) {
            return "application/javascript";
        }
        if (path.endsWith(".version")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c == -1) {
                break;
            }
            if (c == 10) {
                return sb.toString();
            }
            if (c != 13) {
                sb.append((char) c);
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    private static void respond(Socket sock, boolean ok, byte[] body, String mime)
            throws Exception {
        OutputStream os = sock.getOutputStream();
        StringBuilder sb = new StringBuilder();
        if (ok) {
            sb.append("HTTP/1.1 200 OK");
        } else {
            sb.append("HTTP/1.1 404 Not Found");
        }
        sb.append("\r\nContent-Type: ").append(mime);
        sb.append("\r\nContent-Length: ").append(body.length);
        sb.append("\r\nConnection: close\r\n\r\n");
        os.write(sb.toString().getBytes("UTF-8"));
        os.write(body);
        os.flush();
    }

    private static void respond404(Socket sock) {
        try {
            respond(sock, false, new byte[0], "application/octet-stream");
        } catch (Throwable ignored) {
        }
    }

    private static void logReq(String code, String path) {
        try {
            Log.i("MiniServer", code + " " + path);
        } catch (Throwable ignored) {
        }
    }
}
