// ============================================================
// JS — jembatan Rhino (Tier-2 handler).
// Kontrak sama dengan prototipe: kode handler dieksekusi dengan
// API kernel sebagai bare names: reg / state / md5 / b64enc /
// b64dec / xorDragon / readFile / time / date / lib.
// Rhino 1.7.10 (interpretasi, optimizationLevel -1) — android-safe.
// ============================================================
package s2.kernel;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Js {
    public static final String[] API_NAMES =
            new String[]{"reg", "state", "md5", "b64enc", "b64dec", "xorDragon", "readFile", "time", "date", "lib"};

    private Js() {}

    // ---------- konversi org.json -> Rhino ----------
    public static Object jsonToRhino(Context cx, Scriptable scope, Object o) {
        try {
            if (o == null || o == Json.NULL) return null;
            if (o instanceof Json.Obj) {
                Json.Obj jo = (Json.Obj) o;
                NativeObject n = (NativeObject) cx.newObject(scope);
                for (String k : jo.keys()) {
                    n.put(k, n, jsonToRhino(cx, scope, jo.opt(k)));
                }
                return n;
            }
            if (o instanceof Json.Arr) {
                Json.Arr ja = (Json.Arr) o;
                int len = ja.length();
                Object[] elems = new Object[len];
                for (int i = 0; i < len; i++) elems[i] = jsonToRhino(cx, scope, ja.opt(i));
                return cx.newArray(scope, elems);
            }
            if (o instanceof String || o instanceof Number || o instanceof Boolean) return o;
            return String.valueOf(o);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- konversi Rhino -> org.json ----------
    public static Object rhinoToJson(Object o) {
        try {
            if (o == null) return null;
            if (o instanceof Undefined || o == Scriptable.NOT_FOUND) return Json.NULL;
            if (o instanceof Wrapper) return rhinoToJson(((Wrapper) o).unwrap());
            if (o instanceof NativeObject) {
                NativeObject n = (NativeObject) o;
                Json.Obj out = new Json.Obj();
                Object[] ids = n.getIds();
                for (Object id : ids) {
                    Object v = n.get(String.valueOf(id), n);
                    Object jv = rhinoToJson(v);
                    out.put(String.valueOf(id), jv == null ? Json.NULL : jv);
                }
                return out;
            }
            if (o instanceof NativeArray) {
                NativeArray a = (NativeArray) o;
                int len = (int) Context.toNumber(a.get("length", a));
                if (len < 0) len = 0;
                Json.Arr out = new Json.Arr();
                for (int i = 0; i < len; i++) {
                    Object v = rhinoToJson(a.get(i, a));
                    out.put(v == null ? Json.NULL : v);
                }
                return out;
            }
            if (o instanceof String || o instanceof Number || o instanceof Boolean) return o;
            if (o instanceof Json.Obj || o instanceof Json.Arr) return o;
            return String.valueOf(o);
        } catch (Throwable t) {
            return String.valueOf(o);
        }
    }

    public static String str(Object o) {
        if (o == null || o instanceof Undefined) return "";
        if (o instanceof Wrapper) o = ((Wrapper) o).unwrap();
        return Context.toString(o);
    }

    public static String optStr(Scriptable def, String key) {
        try {
            Object v = def.get(key, def);
            if (v == Scriptable.NOT_FOUND || v instanceof Undefined) return "";
            return str(v);
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------- state API (LiveConnect: JS memanggil method Java) ----------
    public static class StateApi {
        private final Scriptable scope;

        StateApi(Scriptable scope) {
            this.scope = scope;
        }

        public Object get(String k) {
            Object v = KV.get(k);
            if (v == null) return null;
            if (v instanceof String || v instanceof Number || v instanceof Boolean) return v;
            Context cx = Context.enter();
            try {
                cx.setOptimizationLevel(-1);
                return jsonToRhino(cx, scope, v);
            } finally {
                Context.exit();
            }
        }

        public boolean set(String k, Object v) {
            Object jv = rhinoToJson(v);
            KV.set(k, Util.jsonValueToString(jv));
            return true;
        }

        public boolean del(String k) {
            return KV.del(k);
        }

        public int incr(String k) {
            return KV.incr(k, 1);
        }

        public int incr(String k, double n) {
            return KV.incr(k, (int) n);
        }

        public Object keys(String prefix) {
            Map<String, String> snap = KV.snapshot();
            int cap = 200;
            java.util.List<String> ks = new java.util.ArrayList<String>();
            for (String k : snap.keySet()) {
                if (ks.size() >= cap) break;
                if (prefix == null || prefix.length() == 0 || k.contains(prefix)) ks.add(k);
            }
            Context cx = Context.enter();
            try {
                cx.setOptimizationLevel(-1);
                Object[] elems = new Object[ks.size()];
                for (int i = 0; i < ks.size(); i++) elems[i] = ks.get(i);
                return cx.newArray(scope, elems);
            } finally {
                Context.exit();
            }
        }

        public int nextUid() {
            return KV.nextUid();
        }
    }

    // ---------- lib map ----------
    public static class LibEntry {
        final String id;
        final Function fn;
        final Scriptable scope;

        LibEntry(String id, Function fn, Scriptable scope) {
            this.id = id;
            this.fn = fn;
            this.scope = scope;
        }
    }

    public static Scriptable buildLibObj(Context cx, Scriptable scope, Map<String, LibEntry> libMap) {
        ScriptableObject lib = (ScriptableObject) cx.newObject(scope);
        for (LibEntry e : libMap.values()) {
            lib.put(e.id, lib, new LibFn(e));
        }
        return lib;
    }

    private static class LibFn extends BaseFunction {
        private final LibEntry e;

        LibFn(LibEntry e) {
            this.e = e;
        }

        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            return e.fn.call(cx, e.scope, e.scope, args);
        }
    }

    // ---------- bare-name fungsi kernel ----------
    public static BaseFunction makeFn(final String id, final Scriptable scope) {
        return new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable sc, Scriptable thisObj, Object[] args) {
                Object a0 = args.length > 0 ? args[0] : null;
                if ("md5".equals(id)) return Codec.md5(str(a0));
                if ("b64enc".equals(id)) return Codec.b64enc(str(a0));
                if ("b64dec".equals(id)) return Codec.b64dec(str(a0));
                if ("readFile".equals(id)) {
                    FileGuard g = readFileGuard(str(a0));
                    return g == null ? null : g.content;
                }
                if ("time".equals(id)) return (int) (System.currentTimeMillis() / 1000L);
                if ("date".equals(id)) return Logr.isoDate();
                if ("xorDragon".equals(id)) {
                    String s = str(a0);
                    char[] kb = Codec.XOR_KEY.toCharArray();
                    int[] out = new int[s.length()];
                    for (int i = 0; i < s.length(); i++) {
                        out[i] = (s.charAt(i) & 0xff) ^ (kb[i % kb.length] & 0xff);
                    }
                    Object[] elems = new Object[out.length];
                    for (int i = 0; i < out.length; i++) elems[i] = out[i];
                    return cx.newArray(sc, elems);
                }
                return Undefined.instance;
            }
        };
    }

    /** baca file zona utk handler (readFile) — di luar zona/state = null. */
    public static class FileGuard {
        final String content;
        FileGuard(String c) {
            this.content = c;
        }
    }

    public static FileGuard readFileGuard(String rel) {
        if (rel == null || rel.length() == 0) return null;
        java.io.File f = K.safeFile(rel);
        if (f == null || !f.isFile()) return null;
        String name = f.getName();
        if (name.endsWith("state.db") || name.endsWith("logs.jsonl")) return null;
        String c = Util.readTextFile(f);
        return c == null ? null : new FileGuard(c);
    }
}
