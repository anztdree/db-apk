// ============================================================
// JSON — parser + writer internal kernel (NON-THROWING API).
// Pengganti org.json di dalam kernel agar API put/opt tidak
// melempar checked exception. Spesifikasi JSON standar.
// ============================================================
package s2.kernel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Json {

    /** penanda null eksplisit (seperti JSONObject.NULL). */
    public static final Object NULL = new Object();

    private Json() {}

    // ============================================================
    // JSON OBJECT
    // ============================================================
    public static final class Obj {
        private final LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();

        public Obj put(String k, Object v) {
            map.put(k, v == null ? NULL : v);
            return this;
        }

        public Obj put(String k, boolean v) {
            return put(k, (Object) (v ? Boolean.TRUE : Boolean.FALSE));
        }

        public Obj put(String k, int v) {
            return put(k, (Object) Integer.valueOf(v));
        }

        public Obj put(String k, long v) {
            return put(k, (Object) Long.valueOf(v));
        }

        public Obj put(String k, double v) {
            return put(k, (Object) Double.valueOf(v));
        }

        public boolean has(String k) {
            return map.containsKey(k);
        }

        public boolean isNull(String k) {
            Object v = map.get(k);
            return v == null || v == NULL;
        }

        public Object opt(String k) {
            Object v = map.get(k);
            return v == null ? NULL : v;
        }

        public Object get(String k) {
            if (!map.containsKey(k)) throw new IllegalStateException("key tidak ada: " + k);
            return opt(k);
        }

        public String optString(String k, String dflt) {
            Object v = map.get(k);
            if (v == null || v == NULL) return dflt;
            return v instanceof String ? (String) v : String.valueOf(v);
        }

        public String optString(String k) {
            return optString(k, "");
        }

        public int optInt(String k, int dflt) {
            Object v = map.get(k);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) {
                try {
                    return (int) Double.parseDouble((String) v);
                } catch (Throwable ignore) {
                }
            }
            if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
            return dflt;
        }

        public long optLong(String k, long dflt) {
            Object v = map.get(k);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v instanceof String) {
                try {
                    return (long) Double.parseDouble((String) v);
                } catch (Throwable ignore) {
                }
            }
            return dflt;
        }

        public boolean optBoolean(String k, boolean dflt) {
            Object v = map.get(k);
            if (v instanceof Boolean) return ((Boolean) v).booleanValue();
            if (v instanceof String) return Boolean.parseBoolean((String) v);
            return dflt;
        }

        public Obj optObj(String k) {
            Object v = map.get(k);
            return v instanceof Obj ? (Obj) v : null;
        }

        public Arr optArr(String k) {
            Object v = map.get(k);
            return v instanceof Arr ? (Arr) v : null;
        }

        public int size() {
            return map.size();
        }

        public Set<String> keys() {
            return map.keySet();
        }

        public Map<String, Object> asMap() {
            return map;
        }

        public Obj remove(String k) {
            map.remove(k);
            return this;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                encodeString(sb, e.getKey());
                sb.append(':');
                encodeValue(sb, e.getValue());
            }
            sb.append('}');
            return sb.toString();
        }
    }

    // ============================================================
    // JSON ARRAY
    // ============================================================
    public static final class Arr {
        private final List<Object> list = new ArrayList<Object>();

        public Arr put(Object v) {
            list.add(v == null ? NULL : v);
            return this;
        }

        public Arr put(boolean v) {
            return put((Object) (v ? Boolean.TRUE : Boolean.FALSE));
        }

        public Arr put(int v) {
            return put((Object) Integer.valueOf(v));
        }

        public Arr put(long v) {
            return put((Object) Long.valueOf(v));
        }

        public Arr put(double v) {
            return put((Object) Double.valueOf(v));
        }

        public int length() {
            return list.size();
        }

        public Object opt(int i) {
            if (i < 0 || i >= list.size()) return NULL;
            return list.get(i);
        }

        public List<Object> asList() {
            return list;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                encodeValue(sb, list.get(i));
            }
            sb.append(']');
            return sb.toString();
        }
    }

    // ============================================================
    // PARSER
    // ============================================================
    public static Object parse(String text) throws IllegalArgumentException {
        if (text == null) throw new IllegalArgumentException("null");
        P p = new P(text.trim());
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.pos < p.len) throw new IllegalArgumentException("sisa karakter setelah nilai JSON");
        return v;
    }

    public static Obj parseObj(String text) throws IllegalArgumentException {
        Object v = parse(text);
        if (!(v instanceof Obj)) throw new IllegalArgumentException("bukan objek JSON");
        return (Obj) v;
    }

    public static Arr parseArr(String text) throws IllegalArgumentException {
        Object v = parse(text);
        if (!(v instanceof Arr)) throw new IllegalArgumentException("bukan array JSON");
        return (Arr) v;
    }

    private static final class P {
        final String s;
        final int len;
        int pos;

        P(String s) {
            this.s = s;
            this.len = s.length();
        }

        void ws() {
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++;
                else break;
            }
        }

        Object value() {
            if (pos >= len) throw new IllegalArgumentException("nilai hilang");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return str();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return NULL;
                default: return num();
            }
        }

        void expect(String word) {
            if (!s.startsWith(word, pos)) throw new IllegalArgumentException("token tidak valid di posisi " + pos);
            pos += word.length();
        }

        Obj obj() {
            Obj o = new Obj();
            pos++; // {
            ws();
            if (pos < len && s.charAt(pos) == '}') {
                pos++;
                return o;
            }
            while (true) {
                ws();
                if (pos >= len || s.charAt(pos) != '"') throw new IllegalArgumentException("kunci objek bukan string");
                String k = str();
                ws();
                if (pos >= len || s.charAt(pos) != ':') throw new IllegalArgumentException(" ':' hilang");
                pos++;
                ws();
                o.put(k, value());
                ws();
                if (pos >= len) throw new IllegalArgumentException("objek belum ditutup");
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return o;
                }
                throw new IllegalArgumentException("',' atau '}' hilang");
            }
        }

        Arr arr() {
            Arr a = new Arr();
            pos++; // [
            ws();
            if (pos < len && s.charAt(pos) == ']') {
                pos++;
                return a;
            }
            while (true) {
                ws();
                a.put(value());
                ws();
                if (pos >= len) throw new IllegalArgumentException("array belum ditutup");
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return a;
                }
                throw new IllegalArgumentException("',' atau ']' hilang");
            }
        }

        String str() {
            StringBuilder sb = new StringBuilder();
            pos++; // "
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    if (pos >= len) throw new IllegalArgumentException("escape terpotong");
                    char e = s.charAt(pos);
                    switch (e) {
                        case '"': sb.append('"'); pos++; break;
                        case '\\': sb.append('\\'); pos++; break;
                        case '/': sb.append('/'); pos++; break;
                        case 'b': sb.append('\b'); pos++; break;
                        case 'f': sb.append('\f'); pos++; break;
                        case 'n': sb.append('\n'); pos++; break;
                        case 'r': sb.append('\r'); pos++; break;
                        case 't': sb.append('\t'); pos++; break;
                        case 'u':
                            if (pos + 4 >= len) throw new IllegalArgumentException("\\u terpotong");
                            String hex = s.substring(pos + 1, pos + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 5;
                            break;
                        default:
                            throw new IllegalArgumentException("escape tidak dikenal: \\" + e);
                    }
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw new IllegalArgumentException("string belum ditutup");
        }

        Object num() {
            int start = pos;
            while (pos < len) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') pos++;
                else break;
            }
            if (pos == start) throw new IllegalArgumentException("nilai tidak valid di posisi " + pos);
            String num = s.substring(start, pos);
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                return Double.valueOf(num);
            }
            try {
                return Long.valueOf(num);
            } catch (Throwable t) {
                return Double.valueOf(num);
            }
        }
    }

    // ============================================================
    // ENCODER
    // ============================================================
    public static void encodeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    public static void encodeValue(StringBuilder sb, Object v) {
        if (v == null || v == NULL) {
            sb.append("null");
        } else if (v instanceof String) {
            encodeString(sb, (String) v);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Obj) {
            sb.append(((Obj) v).toString());
        } else if (v instanceof Arr) {
            sb.append(((Arr) v).toString());
        } else {
            encodeString(sb, String.valueOf(v));
        }
    }

    // ============================================================
    // SMART PARSER (untuk nilai KV / body — primitif boleh telanjang)
    // ============================================================
    public static Object parseSmart(String raw) throws IllegalArgumentException {
        if (raw == null) throw new IllegalArgumentException("null");
        String s = raw.trim();
        if (s.length() == 0) return "";
        char c0 = s.charAt(0);
        if (c0 == '{' || c0 == '[' || c0 == '"') return parse(s);
        if ("true".equals(s)) return Boolean.TRUE;
        if ("false".equals(s)) return Boolean.FALSE;
        if ("null".equals(s)) return NULL;
        try {
            if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
                return Double.valueOf(s);
            }
            return Long.valueOf(s);
        } catch (Throwable t) {
            return s; // teks biasa
        }
    }

    public static String valueToString(Object v) {
        if (v == null || v == NULL) return "null";
        if (v instanceof String) return (String) v;
        if (v instanceof Obj || v instanceof Arr) return v.toString();
        return String.valueOf(v);
    }

    public static Iterator<String> objKeys(Obj o) {
        return o.keys().iterator();
    }
}
