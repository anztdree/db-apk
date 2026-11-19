// ============================================================
// DASH — API dashboard mini-server di port 4000 (/dash/api/*).
// Alat owner: status, config view+edit, database KV view+edit,
// logs, handlers, reload. UI owner di GET /dash (Ui.HTML).
// ============================================================
package s2.kernel;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

public final class Dash {
    private Dash() {}

    /** return null = route tidak dikenal. */
    public static String handle(String method, String path, Map<String, String> query, byte[] body) {
        try {
            String bodyText = body != null && body.length > 0 ? new String(body, Wire.UTF8) : "";
            Json.Obj bodyJson = null;
            if (bodyText.length() > 0) {
                try {
                    Object o = Util.parseJsonSmart(bodyText);
                    if (o instanceof Json.Obj) bodyJson = (Json.Obj) o;
                } catch (Throwable ignore) {
                }
            }

            // ---------- status ----------
            if (path.equals("/dash/api/status") && method.equals("GET")) {
                return statusJson().toString();
            }

            // ---------- config ----------
            if (path.equals("/dash/api/config") && method.equals("GET")) {
                Json.Obj o = new Json.Obj();
                o.put("ok", true);
                o.put("engine", engineJson());
                o.put("files", listZoneFiles());
                return o.toString();
            }
            if (path.equals("/dash/api/config/file") && method.equals("GET")) {
                String p = query.get("path");
                Json.Obj guard = guardZone(p);
                if (guard != null) return guard.toString();
                File f = K.safeFile(p);
                byte[] data = f != null ? Util.readBytes(f) : null;
                if (data == null) return err(404, "file tidak terbaca atau di luar zona: " + p).toString();
                String content = new String(data, Wire.UTF8);
                Json.Obj o = new Json.Obj();
                o.put("ok", true);
                o.put("path", p);
                o.put("content", content);
                o.put("size", data.length);
                o.put("mtime", Util.isoMs(f.lastModified()));
                o.put("writable", true);
                return o.toString();
            }
            if (path.equals("/dash/api/config/file") && method.equals("PUT")) {
                if (bodyJson == null) return err(400, "body JSON rusak").toString();
                String p = bodyJson.optString("path", "");
                String content = bodyJson.optString("content", "");
                if (p.length() == 0) return err(400, "path dan content wajib").toString();
                Json.Obj guard = guardZone(p);
                if (guard != null) return guard.toString();
                if (p.toLowerCase().endsWith(".json")) {
                    try {
                        Util.parseJsonSmart(content);
                    } catch (Throwable t) {
                        return err(400, "JSON tidak valid: " + t).toString();
                    }
                }
                File f = K.safeFile(p);
                if (f == null) return err(400, "path di luar zona HOME").toString();
                if (!Util.writeTextFile(f, content)) return err(400, "gagal tulis").toString();
                Logr.write("hot", "dash", "file ditulis owner: " + p + " (" + content.length() + " B)");
                Hot.scanAndBuild(false);
                Json.Obj o = new Json.Obj();
                o.put("ok", true);
                o.put("path", p);
                o.put("size", content.length());
                Hot.Set set = Hot.current();
                o.put("buildOk", set != null && set.ok);
                o.put("buildError", set != null && set.error != null ? set.error : Json.NULL);
                return o.toString();
            }

            // ---------- database ----------
            if (path.equals("/dash/api/db") && method.equals("GET")) {
                String search = query.get("search");
                int limit = parseIntOr(query.get("limit"), 50);
                int offset = parseIntOr(query.get("offset"), 0);
                return KV.list(search, limit, offset).toString();
            }
            if (path.equals("/dash/api/db/key") && method.equals("GET")) {
                String key = query.get("key");
                Json.Obj d = KV.detail(key);
                if (d == null) return err(404, "key tidak ada: " + key).toString();
                return d.toString();
            }
            if (path.equals("/dash/api/db/key") && method.equals("PUT")) {
                if (bodyJson == null) return err(400, "body JSON rusak").toString();
                String k = bodyJson.optString("k", "");
                if (k.length() == 0) return err(400, "k wajib").toString();
                Object v = bodyJson.has("v") ? bodyJson.opt("v") : Json.NULL;
                KV.set(k, Json.valueToString(v));
                Logr.write("info", "db", "owner set key \"" + k + "\"");
                return new Json.Obj().put("ok", true).toString();
            }
            if (path.equals("/dash/api/db/key") && method.equals("DELETE")) {
                String key = query.get("key");
                boolean deleted = KV.del(key);
                Logr.write("info", "db", "owner hapus key \"" + key + "\" (" + (deleted ? "ada" : "tidak ada") + ")");
                return new Json.Obj().put("ok", true).put("deleted", deleted).toString();
            }
            if (path.equals("/dash/api/db/seed") && method.equals("POST")) {
                return KV.seed(true).toString();
            }

            // ---------- logs ----------
            if (path.equals("/dash/api/logs") && method.equals("GET")) {
                String level = query.get("level");
                String category = query.get("category");
                long since = parseIntOr(query.get("since"), 0);
                int limit = parseIntOr(query.get("limit"), 300);
                return Logr.list(level, category, since, limit).toString();
            }
            if (path.equals("/dash/api/logs/clear") && method.equals("POST")) {
                Logr.clear();
                Logr.write("info", "dash", "log buffer dibersihkan owner");
                return new Json.Obj().put("ok", true).toString();
            }

            // ---------- handlers + reload ----------
            if (path.equals("/dash/api/handlers") && method.equals("GET")) {
                return Hot.describeSet().toString();
            }
            if (path.equals("/dash/api/reload") && method.equals("POST")) {
                Hot.resetFingerprint();
                Hot.scanAndBuild(false);
                Hot.Set set = Hot.current();
                Json.Obj o = new Json.Obj();
                o.put("ok", set != null && set.ok);
                o.put("buildOk", set != null && set.ok);
                o.put("reloads", Hot.reloads());
                o.put("tier1Routes", set != null ? set.tier1.size() : 0);
                o.put("tier2Handlers", set != null ? set.tier2.size() : 0);
                if (set != null && set.error != null) o.put("error", set.error);
                return o.toString();
            }

            return null;
        } catch (Throwable t) {
            try {
                return err(500, String.valueOf(t)).toString();
            } catch (Throwable t2) {
                return "{\"ok\":false,\"error\":\"internal\"}";
            }
        }
    }

    private static int parseIntOr(String s, int dflt) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return dflt;
        }
    }

    /** guard zona tulis: state.db / logs.jsonl dilarang lewat config/file. */
    private static Json.Obj guardZone(String rel) {
        if (rel == null || rel.length() == 0) {
            return err(400, "path wajib");
        }
        String lower = rel.toLowerCase();
        if (lower.endsWith("state.db") || lower.endsWith("state.db-wal") || lower.endsWith("state.db-shm")) {
            return err(400, "state.db hanya boleh lewat /dash/api/db");
        }
        if (lower.endsWith("logs.jsonl")) {
            return err(400, "logs.jsonl milik mesin");
        }
        File f = K.safeFile(rel);
        if (f == null) {
            return err(400, "path di luar zona HOME");
        }
        return null;
    }

    private static Json.Obj err(int code, String msg) {
        Json.Obj o = new Json.Obj();
        o.put("ok", false);
        o.put("error", msg);
        return o;
    }

    private static Json.Obj engineJson() {
        Wire.EngineCfg c = Wire.engineCfg();
        Json.Obj o = new Json.Obj();
        o.put("version", K.ENGINE_VERSION);
        o.put("port", c.port);
        o.put("startedAt", Wire.STARTED_AT);
        o.put("uptimeMs", System.currentTimeMillis() - Wire.STARTED_MS);
        o.put("runtime", "java-android (kernel classes2.dex)");
        return o;
    }

    // ---------- daftar file zona ----------
    public static Json.Arr listZoneFiles() {
        Json.Arr out = new Json.Arr();
        File root = K.root();
        addFile(out, new File(root, "engine.json"), "engine.json");
        walkZone(out, new File(root, "config"), "config");
        walkZone(out, new File(root, "handlers"), "handlers");
        walkZone(out, new File(root, "static"), "static");
        File seed = new File(new File(root, "data"), "seed.json");
        if (seed.isFile()) {
            Json.Obj o = new Json.Obj();
            o.put("path", "data/seed.json");
            o.put("size", seed.length());
            o.put("mtime", Util.isoMs(seed.lastModified()));
            out.put(o);
        }
        return out;
    }

    private static void walkZone(Json.Arr out, File dir, String rel) {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) {
                walkZone(out, f, rel + "/" + f.getName());
            } else {
                Json.Obj o = new Json.Obj();
                o.put("path", rel + "/" + f.getName());
                o.put("size", f.length());
                o.put("mtime", Util.isoMs(f.lastModified()));
                out.put(o);
            }
        }
    }

    private static void addFile(Json.Arr out, File f, String rel) {
        if (!f.isFile()) return;
        Json.Obj o = new Json.Obj();
        o.put("path", rel);
        o.put("size", f.length());
        o.put("mtime", Util.isoMs(f.lastModified()));
        out.put(o);
    }

    private static Json.Obj zoneStats() {
        Json.Arr files = listZoneFiles();
        Json.Obj o = new Json.Obj();
        o.put("root", K.root().getAbsolutePath());
        o.put("rootKind", K.rootKind());
        o.put("config", countPrefix(files, "config"));
        o.put("handlers", countPrefix(files, "handlers"));
        o.put("static", countPrefix(files, "static"));
        o.put("data", countPrefix(files, "data"));
        return o;
    }

    private static int countPrefix(Json.Arr files, String prefix) {
        int n = 0;
        for (int i = 0; i < files.length(); i++) {
            Object fo = files.opt(i);
            String p = fo instanceof Json.Obj ? ((Json.Obj) fo).optString("path", "") : "";
            if (p.startsWith(prefix)) n++;
        }
        return n;
    }

    // ---------- status lengkap ----------
    public static Json.Obj statusJson() {
        Hot.Set set = Hot.current();
        Json.Obj o = new Json.Obj();
        o.put("ok", true);
        o.put("engine", engineJson());
        o.put("zones", zoneStats());

        Json.Obj handlers = new Json.Obj();
        handlers.put("tier1Routes", set != null ? set.tier1.size() : 0);
        handlers.put("tier2Handlers", set != null ? set.tier2.size() : 0);
        handlers.put("libModules", set != null ? set.libFiles.size() : 0);
        handlers.put("lastScan", set != null ? Util.isoMs(set.builtAt) : Json.NULL);
        handlers.put("reloads", Hot.reloads());
        handlers.put("lastBuildOk", set != null && set.ok);
        handlers.put("lastError", set != null && set.error != null ? set.error : Json.NULL);
        o.put("handlers", handlers);

        o.put("sessions", Wire.sessionStats());

        Json.Obj db = new Json.Obj();
        db.put("keys", KV.count());
        db.put("file", "data/state.db");
        db.put("sizeBytes", KV.sizeBytes);
        o.put("db", db);

        o.put("logs", Logr.stats());
        o.put("tea", Codec.selfTestTea());
        return o;
    }
}
