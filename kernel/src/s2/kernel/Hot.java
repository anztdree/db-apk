// ============================================================
// HOT — loader handler hot-swap (Loader A Tier-1 JSON + Loader B
// Tier-2 JS Rhino). Prioritas: Tier-1 dulu, lalu Tier-2 (terakhir
// menang). Tidak match -> hold-connection (Hukum 2: NOL popup).
// Fingerprint mtime (handlers + config + engine.json), cek 2 detik,
// build gagal -> set lama tetap aktif.
// ============================================================
package s2.kernel;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Hot {
    public static class Route {
        public String kind, path, type, action, file;
        public Json.Obj respond;
        public boolean hasRespond;
        public String body;
        public String bodyFile;
        public String transform;
        public String contentType;
        public int status = -1;
        public boolean compress;
    }

    public static class T2 {
        public String file, id, kind, path, type, action;
        public Function fn;
        public Scriptable scope;
    }

    public static class Set {
        public long builtAt;
        public boolean ok;
        public String error;
        public final List<Route> tier1 = new ArrayList<Route>();
        public final List<String> tier1Files = new ArrayList<String>();
        public final List<T2> tier2 = new ArrayList<T2>();
        public final List<String> tier2Files = new ArrayList<String>();
        public final List<String> libFiles = new ArrayList<String>();
        public final Map<String, Route> t1Http = new LinkedHashMap<String, Route>();
        public final Map<String, Route> t1Sock = new LinkedHashMap<String, Route>();
        public final Map<String, T2> t2Http = new LinkedHashMap<String, T2>();
        public final Map<String, T2> t2Sock = new LinkedHashMap<String, T2>();
        public final Map<String, Js.LibEntry> libMap = new LinkedHashMap<String, Js.LibEntry>();
    }

    public static class Match {
        public final int tier;
        public final Route route;
        public final T2 handler;

        Match(int tier, Route route, T2 handler) {
            this.tier = tier;
            this.route = route;
            this.handler = handler;
        }
    }

    public static class HttpResult {
        public int status;
        public String contentType;
        public byte[] body;
        public String source;
    }

    private static volatile Set current;
    private static volatile int reloads = 0;
    private static String lastFingerprint = "";
    private static volatile String currentReqBodyJson;
    private static final Object BUILD_LOCK = new Object();
    private static final Object EXEC_LOCK = new Object();

    private Hot() {}

    public static Set current() {
        return current;
    }

    public static int reloads() {
        return reloads;
    }

    public static String currentReqBodyJson() {
        return currentReqBodyJson;
    }

    // ---------- fingerprint zona panas ----------
    public static String hotFingerprint() {
        StringBuilder parts = new StringBuilder();
        File root = K.root();
        File ej = new File(root, "engine.json");
        if (ej.isFile()) parts.append(ej.getAbsolutePath()).append(':').append(ej.lastModified()).append('|');
        walkMtime(new File(root, "handlers"), parts);
        walkMtime(new File(root, "config"), parts);
        return parts.toString();
    }

    private static void walkMtime(File dir, StringBuilder parts) {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) walkMtime(f, parts);
            else parts.append(f.getAbsolutePath()).append(':').append(f.lastModified()).append('|');
        }
    }

    public static boolean fingerprintChanged() {
        String fp = hotFingerprint();
        if (!fp.equals(lastFingerprint)) {
            lastFingerprint = fp;
            return true;
        }
        return false;
    }

    public static boolean fingerprintIsDirty() {
        return !hotFingerprint().equals(lastFingerprint);
    }

    public static void resetFingerprint() {
        lastFingerprint = hotFingerprint();
    }

    // ---------- build set baru (gagal -> set lama tetap) ----------
    public static void scanAndBuild(boolean fresh) {
        synchronized (BUILD_LOCK) {
            Set ns = tryBuild();
            if (ns.ok) {
                ns.builtAt = System.currentTimeMillis();
                current = ns;
                reloads++;
                Logr.write("hot", "hot", "hot-swap #" + reloads + ": tier1=" + ns.tier1.size()
                        + " route, tier2=" + ns.tier2.size() + " handler, lib=" + ns.libFiles.size());
            } else if (fresh && current == null) {
                // boot pertama gagal — tetap pasang agar status terlihat
                ns.builtAt = System.currentTimeMillis();
                current = ns;
                Logr.write("error", "hot", "build set handler awal GAGAL: " + ns.error);
            } else {
                Logr.write("error", "hot", "build set handler GAGAL (set lama tetap aktif): " + ns.error);
            }
        }
    }

    public static void maybeRescan() {
        if (fingerprintChanged()) scanAndBuild(false);
    }

    private static Set tryBuild() {
        Set ns = new Set();
        ns.ok = true;
        ns.error = null;
        File handlersDir = new File(K.root(), "handlers");
        try {
            // --- lib/*.js duluan (urut nama) ---
            File libDir = new File(handlersDir, "lib");
            File[] libs = listSorted(libDir);
            if (libs != null) {
                for (File f : libs) {
                    if (!f.getName().endsWith(".js")) continue;
                    String rel = K.rel(f);
                    runJs(f, ns, true);
                    ns.libFiles.add(rel);
                }
            }
            // --- *.json Tier-1 (urut nama) ---
            File[] jsons = listSorted(handlersDir);
            if (jsons != null) {
                for (File f : jsons) {
                    if (!f.getName().endsWith(".json")) continue;
                    String rel = K.rel(f);
                    String content = Util.readTextFile(f);
                    Json.Arr arr = Json.parseArr(content);
                    for (int i = 0; i < arr.length(); i++) {
                        Object roRaw = arr.opt(i);
                        if (!(roRaw instanceof Json.Obj)) {
                            throw new IllegalStateException("route bukan objek: " + i);
                        }
                        Json.Obj ro = (Json.Obj) roRaw;
                        Route r = new Route();
                        r.kind = ro.optString("kind", "");
                        if (!"http".equals(r.kind) && !"sock".equals(r.kind)) {
                            throw new IllegalStateException("kind tidak dikenal: " + r.kind);
                        }
                        r.path = ro.has("path") ? ro.optString("path", null) : null;
                        r.type = ro.has("type") ? ro.optString("type", null) : null;
                        r.action = ro.has("action") ? ro.optString("action", null) : null;
                        r.respond = ro.optObj("respond");
                        r.hasRespond = r.respond != null;
                        r.body = ro.has("body") ? ro.optString("body", null) : null;
                        r.bodyFile = ro.has("bodyFile") ? ro.optString("bodyFile", null) : null;
                        r.transform = ro.has("transform") ? ro.optString("transform", null) : null;
                        r.contentType = ro.has("contentType") ? ro.optString("contentType", null) : null;
                        r.status = ro.has("status") ? ro.optInt("status", 200) : -1;
                        r.compress = ro.optBoolean("compress", false);
                        r.file = rel;
                        ns.tier1.add(r);
                        if ("http".equals(r.kind) && r.path != null) ns.t1Http.put(r.path, r);
                        if ("sock".equals(r.kind) && r.type != null && r.action != null) {
                            ns.t1Sock.put(r.type + "/" + r.action, r);
                        }
                    }
                    ns.tier1Files.add(rel);
                }
            }
            // --- *.js Tier-2 (urut nama, reg terakhir menang) ---
            File[] jsFiles = listSorted(handlersDir);
            if (jsFiles != null) {
                for (File f : jsFiles) {
                    if (!f.getName().endsWith(".js")) continue;
                    runJs(f, ns, false);
                }
            }
        } catch (Throwable t) {
            ns.ok = false;
            ns.error = String.valueOf(t);
        }
        return ns;
    }

    private static File[] listSorted(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        Arrays.sort(files);
        return files;
    }

    /** eksekusi file JS dengan bare names (lib mode: hanya terima kind lib). */
    private static void runJs(final File f, final Set ns, final boolean libMode) throws Throwable {
        String code = Util.readTextFile(f);
        final String rel = K.rel(f);
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_1_8);
            ScriptableObject scope = cx.initStandardObjects();

            BaseFunctionReg reg = new BaseFunctionReg(libMode, ns, rel, f.getName(), scope);
            Object[] args = new Object[]{
                    reg,
                    new Js.StateApi(scope),
                    Js.makeFn("md5", scope),
                    Js.makeFn("b64enc", scope),
                    Js.makeFn("b64dec", scope),
                    Js.makeFn("xorDragon", scope),
                    Js.makeFn("readFile", scope),
                    Js.makeFn("time", scope),
                    Js.makeFn("date", scope),
                    Js.buildLibObj(cx, scope, ns.libMap),
            };
            StringBuilder src = new StringBuilder("(function(");
            for (int i = 0; i < Js.API_NAMES.length; i++) {
                if (i > 0) src.append(',');
                src.append(Js.API_NAMES[i]);
            }
            src.append("){\n").append(code).append("\n})");
            Object r = cx.evaluateString(scope, src.toString(), rel, 1, null);
            if (r instanceof Function) {
                ((Function) r).call(cx, scope, scope, args);
            }
        } finally {
            Context.exit();
        }
    }

    /** reg(...) sebagai fungsi Rhino (BaseFunction). */
    private static class BaseFunctionReg extends org.mozilla.javascript.BaseFunction {
        private final boolean libMode;
        private final Set ns;
        private final String file;
        private final String fileName;
        private final Scriptable scope;

        BaseFunctionReg(boolean libMode, Set ns, String file, String fileName, Scriptable scope) {
            this.libMode = libMode;
            this.ns = ns;
            this.file = file;
            this.fileName = fileName;
            this.scope = scope;
        }

        @Override
        public Object call(Context cx, Scriptable sc, Scriptable thisObj, Object[] args) {
            Object defArg = args.length > 0 ? args[0] : null;
            if (!(defArg instanceof Scriptable)) return Undefined.instance;
            Scriptable def = (Scriptable) defArg;
            String id = Js.optStr(def, "id");
            String kind = Js.optStr(def, "kind");
            Object fnObj = def.get("fn", def);
            if (!(fnObj instanceof Function)) {
                Logr.write("warn", "hot", fileName + ": reg tanpa fn function (id=" + id + ")");
                return Undefined.instance;
            }
            Function fn = (Function) fnObj;
            if (libMode) {
                if ("lib".equals(kind)) {
                    ns.libMap.put(id, new Js.LibEntry(id, fn, scope));
                }
                return Undefined.instance;
            }
            if (!"http".equals(kind) && !"sock".equals(kind)) return Undefined.instance;
            T2 h = new T2();
            h.file = file;
            h.id = id.length() > 0 ? id : "(tanpa id)";
            h.kind = kind;
            h.path = Js.optStr(def, "path");
            h.type = Js.optStr(def, "type");
            h.action = Js.optStr(def, "action");
            h.fn = fn;
            h.scope = scope;
            ns.tier2.add(h);
            if ("http".equals(kind) && h.path.length() > 0) ns.t2Http.put(h.path, h);
            if ("sock".equals(kind) && h.type.length() > 0 && h.action.length() > 0) {
                ns.t2Sock.put(h.type + "/" + h.action, h);
            }
            return Undefined.instance;
        }
    }

    // ---------- matcher ----------
    public static Match matchHttp(String path) {
        Set s = current;
        if (s == null) return null;
        Route t1 = s.t1Http.get(path);
        if (t1 != null) return new Match(1, t1, null);
        T2 t2 = s.t2Http.get(path);
        if (t2 != null) return new Match(2, null, t2);
        return null;
    }

    public static Match matchSock(String type, String action) {
        Set s = current;
        if (s == null) return null;
        Route t1 = s.t1Sock.get(type + "/" + action);
        if (t1 != null) return new Match(1, t1, null);
        T2 t2 = s.t2Sock.get(type + "/" + action);
        if (t2 != null) return new Match(2, null, t2);
        return null;
    }

    // ---------- executor HTTP ----------
    public static HttpResult executeHttp(Match m, String path, String queryJson, String bodyJson) throws Throwable {
        synchronized (EXEC_LOCK) {
            currentReqBodyJson = bodyJson;
            try {
                if (m.tier == 1 && m.route != null) return execHttpT1(m.route);
                if (m.tier == 2 && m.handler != null) return execHttpT2(m.handler, path, queryJson, bodyJson);
            } finally {
                currentReqBodyJson = null;
            }
            return null;
        }
    }

    private static HttpResult execHttpT1(Route route) throws Throwable {
        byte[] body;
        if (route.bodyFile != null) {
            File f = K.safeFile(route.bodyFile);
            body = f != null ? Util.readBytes(f) : null;
            if (body == null) throw new IllegalStateException("bodyFile tidak ada: " + route.bodyFile);
        } else if (route.body != null) {
            body = Util.template(route.body).getBytes("UTF-8");
        } else if (route.hasRespond) {
            body = Util.template(route.respond.toString()).getBytes("UTF-8");
        } else {
            body = "{}".getBytes("UTF-8");
        }
        if ("xorDragonBall".equals(route.transform)) {
            body = Codec.xorBuf(body, Codec.XOR_KEY);
        }
        HttpResult r = new HttpResult();
        r.status = route.status >= 0 ? route.status : 200;
        r.contentType = route.contentType != null ? route.contentType : "application/json";
        r.body = body;
        r.source = "tier1";
        return r;
    }

    private static HttpResult execHttpT2(T2 handler, String path, String queryJson, String bodyJson) throws Throwable {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_1_8);
            Scriptable scope = handler.scope;
            NativeReply reply = new NativeReply();
            Json.Obj req = new Json.Obj();
            req.put("kind", "http");
            req.put("path", path);
            req.put("query", queryJson != null && queryJson.length() > 0 ? Json.parseObj(queryJson) : new Json.Obj());
            req.put("body", parseBodyOrRaw(bodyJson));
            Object reqObj = Js.jsonToRhino(cx, scope, req);
            try {
                handler.fn.call(cx, scope, scope, new Object[]{reqObj, reply});
            } catch (Throwable t) {
                Logr.write("error", "http", "handler " + handler.id + " throw: " + t + " — koneksi di-HOLD");
                return null;
            }
            if (!reply.captured) {
                Logr.write("warn", "http", "handler " + handler.id + " tidak membalas — koneksi di-HOLD");
                return null;
            }
            HttpResult r = new HttpResult();
            r.status = 200;
            if (reply.mode == ReplyMode.RAW) {
                r.contentType = reply.contentType != null ? reply.contentType : "text/plain";
                r.body = reply.rawStr != null ? reply.rawStr.getBytes("UTF-8") : new byte[0];
            } else {
                r.contentType = "application/json";
                r.body = reply.jsonObj == null ? "{}".getBytes("UTF-8") : reply.jsonObj.toString().getBytes("UTF-8");
            }
            r.source = "tier2:" + handler.id;
            return r;
        } finally {
            Context.exit();
        }
    }

    private static Object parseBodyOrRaw(String bodyJson) {
        if (bodyJson == null || bodyJson.length() == 0) return Json.NULL;
        try {
            return Util.parseJsonSmart(bodyJson);
        } catch (Throwable t) {
            return bodyJson;
        }
    }

    enum ReplyMode { JSON, RAW }

    public static class NativeReply {
        ReplyMode mode = ReplyMode.JSON;
        Json.Obj jsonObj;
        String rawStr;
        String contentType;
        boolean captured;
        Json.Obj ackObj;

        public void json(Object o) {
            Object jv = Js.rhinoToJson(o);
            if (jv instanceof Json.Obj) {
                this.jsonObj = (Json.Obj) jv;
            } else {
                this.jsonObj = new Json.Obj();
                this.jsonObj.put("data", jv == null ? Json.NULL : jv);
            }
            this.mode = ReplyMode.JSON;
            this.captured = true;
        }

        public void raw(String s) {
            raw(s, null);
        }

        public void raw(String s, String contentType) {
            this.rawStr = s;
            this.contentType = contentType;
            this.mode = ReplyMode.RAW;
            this.captured = true;
        }

        public void ack(Object o) {
            Object jv = Js.rhinoToJson(o);
            if (jv instanceof Json.Obj) {
                this.ackObj = (Json.Obj) jv;
            } else {
                this.ackObj = new Json.Obj();
                this.ackObj.put("data", jv == null ? Json.NULL : jv);
            }
            this.captured = true;
        }
    }

    // ---------- executor SOCK -> ACK {ret, data, compress, serverTime, server0Time} ----------
    public static String executeSock(Match m, String type, String action, String payloadJson, String sid) {
        synchronized (EXEC_LOCK) {
            long t = System.currentTimeMillis();
            try {
                if (m.tier == 1 && m.route != null) return sockT1(m.route, t);
                if (m.tier == 2 && m.handler != null) return sockT2(m.handler, type, action, payloadJson, sid, t);
            } catch (Throwable e) {
                Logr.write("error", "socket", "dispatch " + type + "/" + action + " error: " + e);
            }
            return null;
        }
    }

    private static String sockT1(Route route, long t) throws Throwable {
        Json.Obj src = route.respond != null ? route.respond : new Json.Obj();
        String data = "";
        if (src.has("dataFile") && !src.isNull("dataFile")) {
            String df = src.optString("dataFile", "");
            File f = K.safeFile(df);
            String content = f != null ? Util.readTextFile(f) : null;
            if (content == null) throw new IllegalStateException("dataFile tidak ada: " + df);
            data = content;
        } else if (src.has("data")) {
            Object d = src.get("data");
            data = d instanceof String ? (String) d : String.valueOf(d);
        }
        data = Util.template(data);
        boolean compress = route.compress || src.optBoolean("compress", false);
        int ret = src.optInt("ret", 0);
        if (compress) data = Codec.lzCompressUtf16(data);
        Json.Obj ack = new Json.Obj();
        ack.put("ret", ret);
        ack.put("data", data);
        ack.put("compress", compress);
        ack.put("serverTime", t);
        ack.put("server0Time", t);
        return ack.toString();
    }

    private static String sockT2(T2 handler, String type, String action, String payloadJson, String sid, long t) {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_1_8);
            Scriptable scope = handler.scope;
            NativeReply reply = new NativeReply();
            Json.Obj req = new Json.Obj();
            req.put("kind", "sock");
            req.put("type", type);
            req.put("action", action);
            req.put("body", parseBodyOrRaw(payloadJson));
            req.put("query", new Json.Obj());
            req.put("sid", sid != null ? sid : Json.NULL);
            Object reqObj = Js.jsonToRhino(cx, scope, req);
            try {
                handler.fn.call(cx, scope, scope, new Object[]{reqObj, reply});
            } catch (Throwable e) {
                Logr.write("error", "socket", "handler " + handler.id + " throw: " + e + " — ACK ditahan");
                return null;
            }
            if (reply.ackObj == null) {
                Logr.write("warn", "socket", "handler " + handler.id + " tidak membalas — ACK ditahan");
                return null;
            }
            int ret = reply.ackObj.optInt("ret", 0);
            String data;
            Object d = reply.ackObj.opt("data");
            if (d == null || d == org.json.JSONObject.NULL) data = "";
            else data = d instanceof String ? (String) d : String.valueOf(d);
            boolean compress = reply.ackObj.optBoolean("compress", false);
            Json.Obj ack = new Json.Obj();
            ack.put("ret", ret);
            ack.put("data", data);
            ack.put("compress", compress);
            ack.put("serverTime", t);
            ack.put("server0Time", t);
            return ack.toString();
        } finally {
            Context.exit();
        }
    }

    // ---------- deskripsi set (dashboard handlers) ----------
    public static Json.Obj describeSet() {
        Set s = current;
        Json.Obj out = new Json.Obj();
        Json.Arr t1 = new Json.Arr();
        Json.Arr t2 = new Json.Arr();
        Json.Arr libs = new Json.Arr();
        if (s != null) {
            for (Route r : s.tier1) {
                Json.Obj o = new Json.Obj();
                o.put("file", r.file);
                o.put("kind", r.kind);
                o.put("route", "http".equals(r.kind) ? String.valueOf(r.path) : r.type + "/" + r.action);
                o.put("source", r.bodyFile != null ? ("bodyFile:" + r.bodyFile) : (r.hasRespond ? "respond:inline" : "body"));
                t1.put(o);
            }
            for (T2 h : s.tier2) {
                Json.Obj o = new Json.Obj();
                o.put("file", h.file);
                o.put("id", h.id);
                o.put("kind", h.kind);
                o.put("route", "http".equals(h.kind) ? String.valueOf(h.path) : h.type + "/" + h.action);
                t2.put(o);
            }
            for (String f : s.libFiles) {
                Json.Obj o = new Json.Obj();
                o.put("file", f);
                libs.put(o);
            }
            out.put("lastBuild", Util.isoMs(s.builtAt));
            out.put("buildOk", s.ok);
        } else {
            out.put("lastBuild", Json.NULL);
            out.put("buildOk", false);
        }
        out.put("ok", true);
        out.put("tier1", t1);
        out.put("tier2", t2);
        out.put("lib", libs);
        return out;
    }
}
