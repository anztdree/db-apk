package com.db.local;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public final class Core {

    public static final String TAG = "DBLocal-Core";

    public static final int PORT_CDN = 0x2774;
    public static final int PORT_SDK = 0x2775;
    public static final int PORT_LOGIN = 0x2776;
    public static final int PORT_FB = 0x5190;
    public static final int PORT_AF = 0x5191;

    public static Context APP;
    public static File SERVER_DIR;

    private static volatile boolean ready;
    private static volatile boolean started;

    static final Map<Ws, Sio.Session> WS_MAP = new HashMap<Ws, Sio.Session>();

    private static final byte[] PNG1x1 = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            Base64.DEFAULT);

    private Core() {
    }

    // ======================================================================
    // anonim #1 (Core$1) — Runnable seed besar (zip), thread "DBLocal-seed-big"
    // ======================================================================
    public static void start(Context ctx) {
        if (started) {
            return;
        }
        started = true;
        APP = ctx.getApplicationContext();
        log("mulai: 10100=cdn 10101=sdk 10102=loginserver 20880=facebook 20881=appsflyer");
        initServerDir(APP);
        listen(PORT_CDN);
        listen(PORT_SDK);
        listen(PORT_LOGIN);
        listen(PORT_FB);
        listen(PORT_AF);
        Db.init(APP);
        bootstrapSync(APP);
        File mir = new File(APP.getFilesDir(), "mirror");
        Db.TemplateDirHolder.set(mir);
        seedServerDir(APP);
        ready = true;
        log("siap: server/ terbaca dari Android/data");
        Thread big = new Thread(new Runnable() {
            public void run() {
                seedServerDirBig(APP);
            }
        }, "DBLocal-seed-big");
        big.setDaemon(true);
        big.start();
        Thread gc = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(30000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        Sio.gc();
                    } catch (Throwable t) {
                        log("gc error: " + t);
                    }
                }
            }
        }, "DBLocal-sio-gc");
        gc.setDaemon(true);
        gc.start();
    }

    // ======================================================================
    // anonim #2 (Core$3) — Runnable accept-loop, menangkap val$port
    // ======================================================================
    private static void listen(int port) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                acceptLoop(port);
            }
        }, "DBLocal-accept-" + port);
        t.setDaemon(true);
        t.start();
    }

    // ======================================================================
    // anonim #3 (Core$4) — Runnable koneksi, menangkap val$s + val$port
    // ======================================================================
    private static void acceptLoop(int port) {
        try {
            ServerSocket ss = new ServerSocket(port, 32, InetAddress.getByName("127.0.0.1"));
            log("listen 127.0.0.1:" + port);
            while (true) {
                try {
                    Socket s = ss.accept();
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            handle(s, port);
                        }
                    }, "DBLocal-conn-" + port);
                    t.setDaemon(true);
                    t.start();
                } catch (Throwable t) {
                    log("accept error: " + t);
                }
            }
        } catch (Throwable t) {
            log("listener " + port + " gagal: " + t);
        }
    }

    private static void handle(Socket socket, int port) {
        try {
            socket.setTcpNoDelay(true);
            InputStream in = socket.getInputStream();
            String reqline = readLine(in);
            if (reqline == null || reqline.length() == 0) {
                return;
            }
            String[] parts = reqline.split(" ");
            if (parts.length < 2) {
                return;
            }
            String method = parts[0];
            String target = parts[1];
            String path = "";
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            } else {
                path = target;
            }
            Map<String, String> headers = new HashMap<String, String>();
            while (true) {
                String line = readLine(in);
                if (line == null || line.length() <= 0) {
                    break;
                }
                int c = line.indexOf(':');
                if (c > 0) {
                    headers.put(line.substring(0, c).trim().toLowerCase(), line.substring(c + 1).trim());
                }
            }
            byte[] body = drainRequest(in, headers.get("content-length"));
            logReq(method + " :" + port + " " + path, "len=" + (body == null ? 0 : body.length));
            if ("OPTIONS".equals(method)) {
                respond(socket, 200, "text/plain", Texts.utf8("OK"));
                return;
            }
            if (port == PORT_FB) {
                serveFacebook(socket, method, path);
            } else if (port == PORT_AF) {
                serveAppsflyer(socket, method, path);
            } else if (port == PORT_CDN) {
                waitReady();
                serveMirror(socket, path);
            } else if (port == PORT_SDK) {
                waitReady();
                serveSdk(socket, path, body);
            } else if (port == PORT_LOGIN) {
                waitReady();
                serveLogin(socket, method, path, query, headers, body);
            } else {
                respond(socket, 404, "text/plain", Texts.utf8("not found"));
            }
        } catch (Throwable t) {
            log("handle error: " + t);
            try {
                socket.close();
            } catch (Throwable t2) {
            }
        }
    }

    // urutan server/ -> mirror/ -> mirrorFallback -> 404 (pertahankan apa adanya)
    private static void serveMirror(Socket socket, String path) {
        if (path == null || path.length() == 0 || "/".equals(path)) {
            path = "/bs/index-native.html";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.contains("..")) {
            respond(socket, 400, "text/plain", Texts.utf8("bad request"));
            return;
        }
        File f = new File(new File(APP.getFilesDir(), "mirror"), path);
        File sd = serverDir();
        if (sd != null) {
            File sf = new File(sd, path);
            if (sf.isFile()) {
                f = sf;
            }
        }
        if (!f.isFile()) {
            mirrorFallback(socket, path);
            return;
        }
        String mime = mimeFor(f.getName());
        try {
            long len = f.length();
            OutputStream os = socket.getOutputStream();
            os.write(Texts.utf8("HTTP/1.1 200 OK\r\nContent-Type: " + mime + "\r\nContent-Length: " + len
                    + "\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"));
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[16384];
            int n;
            while ((n = fis.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            fis.close();
            os.flush();
            socket.close();
        } catch (Throwable t) {
            try {
                socket.close();
            } catch (Throwable t2) {
            }
        }
    }

    private static void serveSdk(Socket socket, String path, byte[] body) {
        String lower = path.toLowerCase();
        Map env = Db.decodeEnvelope(body);
        String out;
        try {
            if (lower.contains("v1/user/registervisitor")) {
                out = Db.registerVisitor(env);
            } else if (lower.contains("v1/user/registeruser")) {
                out = Db.registerUser(env);
            } else if (lower.contains("v1/user/loginbyname")) {
                out = Db.loginByName(env);
            } else if (lower.contains("v1/user/autologin")) {
                out = Db.autoLogin(env);
            } else if (lower.contains("v1/auth/getuserinfo")) {
                out = Db.getUserInfo(env);
            } else if (lower.contains("v1/auth/bindmail")) {
                out = Db.bindMail(env);
            } else if (lower.contains("v1/user/sendcodebyemail")) {
                out = Db.sendCodeByEmail(env);
            } else if (lower.contains("v1/user/findpassbyemail")) {
                out = Db.findPassByEmail(env);
            } else if (lower.contains("v1/auth/changepassword")) {
                out = Db.changePassword(env);
            } else if (lower.contains("v1/auth/cusertrash")) {
                out = Db.cUserTrash(env);
            } else if (lower.contains("v1/auth/unbindotherinfo")) {
                out = Db.unBindOtherInfo(env);
            } else if (lower.contains("v1/auth/userbindothersdk")) {
                out = Db.userBindOtherSdk(env);
            } else if (lower.contains("v1/auth/viewmigcode")) {
                out = Db.viewMigCode(env);
            } else if (lower.contains("v1/system/init") || lower.contains("v1/system/getnotice")
                    || lower.contains("v1/system/report")) {
                out = Fx.sdkStatic(lower);
            } else {
                out = Db.errorTemplate("-1", "unknown endpoint: " + path);
            }
        } catch (Throwable t) {
            log("sdk error: " + t);
            out = "{\"result\":false,\"error\":{\"code\":\"-1\",\"message\":\"local error\"}}";
        }
        respond(socket, 200, "text/json", Texts.utf8(out));
    }

    private static void serveLogin(Socket socket, String method, String path, String query,
            Map<String, String> headers, byte[] body) {
        String lower = path.toLowerCase();
        if (lower.contains("loginchecknative")) {
            String uid = Texts.queryValue(query, "uid");
            String user = Texts.queryValue(query, "username");
            String resp = Db.gate(uid, user);
            respond(socket, 200, "text/json", Texts.utf8(resp));
            return;
        }
        if (lower.contains("socket.io")) {
            serveSio(socket, method, query, headers, body);
            return;
        }
        respond(socket, 404, "text/plain", Texts.utf8("404"));
    }

    // ======================================================================
    // anonim #4 (Core$5) — implements Ws.SessionOwner
    // ======================================================================
    private static void serveSio(Socket socket, String method, String query, Map<String, String> headers,
            byte[] body) {
        String transport = Texts.queryValue(query, "transport");
        String sid = Texts.queryValue(query, "sid");
        if ("websocket".equals(transport)) {
            try {
                OutputStream os = socket.getOutputStream();
                String key = headers.get("sec-websocket-key");
                if (key == null || !Ws.handshake(os, key)) {
                    socket.close();
                    return;
                }
                Sio.Session sess = Sio.SESSIONS.get(sid);
                if (sess == null) {
                    socket.close();
                    return;
                }
                Ws ws = new Ws(new Ws.SessionOwner() {
                    public void onWsClosed(Ws w) {
                        Sio.Session s = WS_MAP.remove(w);
                        if (s != null) {
                            s.ws = null;
                        }
                    }

                    public void onWsPacket(Ws w, String data) {
                        Sio.Session s = WS_MAP.get(w);
                        if (s == null) {
                            return;
                        }
                        if ("5".equals(data)) {
                            String queued = Sio.flushQueue(s);
                            if (queued.length() > 0) {
                                String[] pkts = Sio.decodePayload(queued);
                                for (int i = 0; i < pkts.length; i++) {
                                    w.sendText(pkts[i]);
                                }
                            }
                            return;
                        }
                        Sio.onEioPacket(s, data);
                    }
                }, os);
                WS_MAP.put(ws, sess);
                sess.ws = ws;
                log("ws upgrade sid=" + sid);
                ws.readLoop(socket.getInputStream());
                socket.close();
            } catch (Throwable t) {
                try {
                    socket.close();
                } catch (Throwable t2) {
                }
            }
            return;
        }
        if ("GET".equals(method)) {
            Map<String, String> q = new HashMap<String, String>();
            q.put("q", query);
            byte[] resp = Sio.pollGet(q);
            respond(socket, 200, "text/plain", resp);
            return;
        }
        if ("POST".equals(method)) {
            Sio.pollPost(sid, body);
            respond(socket, 200, "text/plain", Texts.utf8("ok"));
            return;
        }
        respond(socket, 400, "text/plain", Texts.utf8("bad request"));
    }

    private static byte[] drainRequest(InputStream in, String lenHeader) {
        long n = Texts.toLong(lenHeader, 0L);
        if (n <= 0L) {
            return new byte[0];
        }
        if (n > 0x400000L) {
            n = 0x400000L;
        }
        int size = (int) n;
        byte[] buf = new byte[size];
        int off = 0;
        while (off < size) {
            try {
                int r = in.read(buf, off, size - off);
                if (r < 0) {
                    break;
                }
                off += r;
            } catch (Throwable t) {
                break;
            }
        }
        return buf;
    }

    private static String readLine(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try {
            while (true) {
                int c = in.read();
                if (c < 0 || c == 10) {
                    return sb.toString();
                }
                if (c != 13) {
                    sb.append((char) c);
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static String reason(int code) {
        if (code == 200) {
            return "OK";
        }
        if (code == 400) {
            return "Bad Request";
        }
        if (code == 404) {
            return "Not Found";
        }
        if (code == 403) {
            return "Forbidden";
        }
        return "OK";
    }

    public static void respond(Socket socket, int code, String type, byte[] data) {
        try {
            OutputStream os = socket.getOutputStream();
            os.write(Texts.utf8("HTTP/1.1 " + code + " " + reason(code)
                    + "\r\nContent-Type: " + type + "\r\nContent-Length: " + data.length
                    + "\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET,POST,OPTIONS\r\nAccess-Control-Allow-Headers: *\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"));
            os.write(data);
            os.flush();
            socket.close();
        } catch (Throwable t) {
            try {
                socket.close();
            } catch (Throwable t2) {
            }
        }
    }

    private static void bootstrapSync(Context ctx) {
        try {
            File dir = new File(ctx.getFilesDir(), "mirror");
            String ver = assetText(ctx, "mirror/VERSION");
            String cur = fileText(new File(dir, "VERSION"));
            if (ver.equals(cur) && new File(dir, "bs/index-native.html").isFile()) {
                return;
            }
            if (dir.exists()) {
                deleteRecursive(dir);
            }
            int count = copyDir(ctx.getAssets(), "mirror", dir);
            fileWrite(new File(dir, "VERSION"), ver);
            log("mirror resync: " + count + " file, versi " + ver);
        } catch (Throwable t) {
            log("bootstrap gagal: " + t);
        }
    }

    private static String assetText(Context ctx, String path) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open(path);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            String s = new String(bos.toByteArray(), "UTF-8").trim();
            try {
                in.close();
            } catch (Throwable t) {
            }
            return s;
        } catch (Throwable t) {
        }
        if (in != null) {
            try {
                in.close();
            } catch (Throwable t) {
            }
        }
        return "";
    }

    private static String fileText(File f) {
        if (!f.isFile()) {
            return "";
        }
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            String s = new String(bos.toByteArray(), "UTF-8").trim();
            try {
                in.close();
            } catch (Throwable t) {
            }
            return s;
        } catch (Throwable t) {
        }
        if (in != null) {
            try {
                in.close();
            } catch (Throwable t) {
            }
        }
        return "";
    }

    private static void fileWrite(File f, String content) {
        try {
            FileWriter w = new FileWriter(f);
            w.write(content);
            w.close();
        } catch (Throwable t) {
        }
    }

    private static int copyDir(AssetManager am, String assetPath, File out) {
        int count = 0;
        try {
            String[] list = am.list(assetPath);
            if (list == null || list.length == 0) {
                InputStream in = am.open(assetPath);
                copyFully(in, new File(out.getParent(), out.getName()));
                return 1;
            }
            out.mkdirs();
            for (int i = 0; i < list.length; i++) {
                count += copyDir(am, assetPath + "/" + list[i], new File(out, list[i]));
            }
            return count;
        } catch (Throwable t) {
            try {
                InputStream in = am.open(assetPath);
                copyFully(in, out);
                return 1;
            } catch (Throwable t2) {
                return count;
            }
        }
    }

    private static void copyFully(InputStream in, File out) {
        try {
            out.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
            fos.close();
            in.close();
        } catch (Throwable t) {
        }
        try {
            in.close();
        } catch (Throwable t) {
        }
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                deleteRecursive(kids[i]);
            }
        }
        f.delete();
    }

    private static String mimeFor(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) {
            return "text/html";
        }
        if (n.endsWith(".js")) {
            return "application/javascript";
        }
        if (n.endsWith(".json")) {
            return "application/json";
        }
        if (n.endsWith(".css")) {
            return "text/css";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".gif")) {
            return "image/gif";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        if (n.endsWith(".zip")) {
            return "application/zip";
        }
        if (n.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (n.endsWith(".wav")) {
            return "audio/wav";
        }
        if (n.endsWith(".bin") || n.endsWith(".thm") || n.endsWith(".data")) {
            return "application/octet-stream";
        }
        return "text/plain";
    }

    // dulunya patch smali — perilaku final, terjemahkan apa adanya
    private static void initServerDir(Context ctx) {
        try {
            File ext = ctx.getExternalFilesDir(null);
            if (ext != null) {
                File dir = new File(ext.getParentFile(), "server");
                SERVER_DIR = dir;
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File readme = new File(dir, "README.TXT");
                if (!readme.exists()) {
                    fileWrite(readme,
                            "DB-LOCAL - FOLDER SERVER\nFile di folder ini MENGGANTIKAN file yang sebelumnya diambil dari server online.\nSemua config bawaan (bs/ upgrade/ qsdk/) SUDAH DISALIN ke sini - edit langsung,\nfile yang ada di folder ini dipakai lebih dulu daripada bawaan APK.\nContoh: bs/resource/properties/clientversion.json\noverride/facebook/  = respons Facebook Graph (graph.facebook.com -> 127.0.0.1:20880)\noverride/appsflyer/ = respons AppsFlyer (*.appsflyer.com -> 127.0.0.1:20881)\nFile hanya disalin sekali: editan Anda tidak pernah ditimpa.\n");
                }
                log("server dir: " + dir.getAbsolutePath());
            }
        } catch (Throwable t) {
            log("server dir gagal: " + t.toString());
        }
    }

    public static File serverDir() {
        return SERVER_DIR;
    }

    // dulunya patch smali — 3 blok JSON verbatim, jangan diubah
    private static void mirrorFallback(Socket socket, String path) {
        String lower = path.toLowerCase();
        if (lower.contains("clientversion.json")) {
            respond(socket, 200, "application/json",
                    Texts.utf8("{\"clientVersion\":\"20260929_180158-EN\"}"));
            return;
        }
        if (lower.contains("serversetting.json")) {
            respond(socket, 200, "application/json",
                    Texts.utf8("{\"loginserver\":\"http://127.0.0.1:10102\",\"requireSDK\":false}"));
            return;
        }
        respond(socket, 404, "text/plain", Texts.utf8("404"));
    }

    // ================== seed server/ dari assets/mirror ==================
    // FIX 1.0.1-E: build 1.0.2-D menulis file ke File(out, basename) padahal
    // pemanggil sudah memberi File(out, nama) — akibatnya setiap file jadi
    // FOLDER bernama file itu yang berisi file bernama sama
    // (contoh: server/upgrade/all.zip/all.zip). Sekarang file ditulis ke
    // `out` langsung, plus perbaikan otomatis pohon rusak peninggalan D.
    private static void seedAssetDir(Context ctx, String assetPath, File out, boolean skipZip) {
        try {
            String[] list = ctx.getAssets().list(assetPath);
            if (list != null && list.length > 0) {
                out.mkdirs();
                for (int i = 0; i < list.length; i++) {
                    String sub = assetPath + "/" + list[i];
                    if (skipZip && list[i].endsWith(".zip")) {
                        continue;
                    }
                    File f = new File(out, list[i]);
                    if (f.isFile() && f.length() > 0L) {
                        continue;
                    }
                    seedAssetDir(ctx, sub, f, skipZip);
                }
                return;
            }
            // cabang file: `out` ADALAH file tujuan (bukan dibungkus folder lagi)
            repairNested(ctx, assetPath, out);
            if (out.isFile() && out.length() > 0L) {
                return;
            }
            if (skipZip && assetPath.endsWith(".zip")) {
                return;
            }
            InputStream in = ctx.getAssets().open(assetPath);
            copyFully(in, out);
        } catch (Throwable t) {
            if (skipZip) {
                log("seed " + assetPath + " gagal: " + t);
            }
        }
    }

    // perbaiki pohon rusak build 1.0.2-D: folder bernama file yang berisi
    // file bernama sama -> dinaikkan jadi file biasa (isi tetap utuh,
    // tidak ada yang ditimpa; idempoten)
    private static void repairNested(Context ctx, String assetPath, File out) {
        try {
            if (!out.isDirectory()) {
                return;
            }
            File inner = new File(out, out.getName());
            if (!inner.isFile() || inner.length() <= 0L) {
                return;
            }
            File tmp = new File(out.getParentFile(), out.getName() + ".fixtmp");
            if (tmp.exists()) {
                tmp.delete();
            }
            if (inner.renameTo(tmp)) {
                deleteRecursive(out);
                tmp.renameTo(out);
                log("seed perbaiki: " + out.getName());
            }
        } catch (Throwable t) {
            log("seed repair gagal: " + out.getName() + " " + t);
        }
    }

    private static void seedServerDir(Context ctx) {
        seedAssetDir(ctx, "mirror", serverDir(), true);
    }

    private static void seedServerDirBig(Context ctx) {
        try {
            seedAssetDir(ctx, "mirror", serverDir(), false);
        } catch (Throwable t) {
            log("seed big gagal: " + t);
        }
    }

    private static void waitReady() {
        long deadline = System.currentTimeMillis() + 5000L;
        while (!ready && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static byte[] readFile(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = fis.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            fis.close();
            return bos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static byte[] canned(String name, byte[] def) {
        try {
            File sd = serverDir();
            if (sd != null) {
                File f = new File(new File(sd, "override"), name);
                if (f.isFile() && f.length() > 0L) {
                    return readFile(f);
                }
            }
            InputStream in = APP.getAssets().open("mirror/override/" + name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            return bos.toByteArray();
        } catch (Throwable t) {
            return def;
        }
    }

    private static void serveFacebook(Socket socket, String method, String path) {
        String lower = path == null ? "" : path.toLowerCase();
        String name;
        int code = 200;
        if (lower.contains("/mobile_sdk_gk")) {
            name = "mobile_sdk_gk";
        } else if (lower.contains("/model_asset")) {
            name = "model_asset";
            code = 400;
        } else if (lower.contains("/activities")) {
            name = "activities";
        } else if (lower.endsWith("/me") || lower.contains("/me?")) {
            name = "me";
            code = 400;
        } else {
            name = "profile";
        }
        byte[] body = canned("facebook/" + name, Texts.utf8("{\"success\":true}"));
        logReq("fb " + method + " " + path, "-> " + code + " " + name);
        respond(socket, code, "application/json", body);
    }

    private static void serveAppsflyer(Socket socket, String method, String path) {
        String lower = path == null ? "" : path.toLowerCase();
        String name = null;
        if (lower.contains("androidevent")) {
            name = "androidevent";
        } else if (lower.contains("register")) {
            name = "register";
        } else if (lower.contains("validate")) {
            name = "validate";
        }
        if (name != null) {
            byte[] body = canned("appsflyer/" + name, null);
            if (body != null) {
                logReq("af " + method + " " + path, "-> 200 " + name);
                respond(socket, 200, "application/json", body);
                return;
            }
        }
        logReq("af " + method + " " + path, "-> 400");
        respond(socket, 400, "text/plain", new byte[0]);
    }

    private static void logReq(String req, String extra) {
        try {
            Log.d(TAG, req + " " + extra);
        } catch (Throwable t) {
        }
        try {
            Dbg.log2(req, extra);
        } catch (Throwable t) {
        }
    }

    private static void log(String msg) {
        try {
            Log.i(TAG, msg);
        } catch (Throwable t) {
        }
        try {
            Dbg.log(msg);
        } catch (Throwable t) {
        }
    }
}
