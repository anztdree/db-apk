package com.db.local;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Jx {

    private Jx() {
    }

    public static String obj(String... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Texts.quote(pairs[i]));
            sb.append(':');
            sb.append(pairs[i + 1]);
        }
        sb.append('}');
        return sb.toString();
    }

    public static Object parse(String s) {
        if (s == null) {
            return null;
        }
        int[] pos = new int[1];
        skipWs(s, pos);
        try {
            return parseValue(s, pos);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map parseObj(String s) {
        Object o = parse(s);
        if (o instanceof Map) {
            return (Map) o;
        }
        return null;
    }

    public static String str(Map m, String key) {
        if (m == null) {
            return "";
        }
        Object v = m.get(key);
        if (v == null) {
            return "";
        }
        return v.toString();
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos[0] = pos[0] + 1;
        }
    }

    private static Object parseValue(String s, int[] pos) {
        skipWs(s, pos);
        if (pos[0] >= s.length()) {
            return null;
        }
        char c = s.charAt(pos[0]);
        if (c == '{') {
            return parseObjBody(s, pos);
        }
        if (c == '[') {
            return parseArrBody(s, pos);
        }
        if (c == '"') {
            return parseStr(s, pos);
        }
        if (c == 't') {
            pos[0] = pos[0] + 4;
            return Boolean.TRUE;
        }
        if (c == 'f') {
            pos[0] = pos[0] + 5;
            return Boolean.FALSE;
        }
        if (c == 'n') {
            pos[0] = pos[0] + 4;
            return null;
        }
        return parseNum(s, pos);
    }

    private static Object parseNum(String s, int[] pos) {
        int start = pos[0];
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                pos[0] = pos[0] + 1;
            } else {
                break;
            }
        }
        String num = s.substring(start, pos[0]);
        try {
            if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
                return Double.valueOf(Double.parseDouble(num));
            }
            return Double.valueOf(Double.parseDouble(num));
        } catch (Exception e) {
            return null;
        }
    }

    private static Map parseObjBody(String s, int[] pos) {
        Map obj = new LinkedHashMap();
        pos[0] = pos[0] + 1;
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == '}') {
            pos[0] = pos[0] + 1;
            return obj;
        }
        while (pos[0] < s.length()) {
            skipWs(s, pos);
            String key = parseStr(s, pos);
            skipWs(s, pos);
            if (pos[0] < s.length() && s.charAt(pos[0]) == ':') {
                pos[0] = pos[0] + 1;
            }
            Object val = parseValue(s, pos);
            obj.put(key, val);
            skipWs(s, pos);
            if (!(pos[0] < s.length() && s.charAt(pos[0]) == ',')) {
                break;
            }
            pos[0] = pos[0] + 1;
        }
        if (pos[0] < s.length() && s.charAt(pos[0]) == '}') {
            pos[0] = pos[0] + 1;
        }
        return obj;
    }

    private static List parseArrBody(String s, int[] pos) {
        List list = new ArrayList();
        pos[0] = pos[0] + 1;
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == ']') {
            pos[0] = pos[0] + 1;
            return list;
        }
        while (pos[0] < s.length()) {
            Object val = parseValue(s, pos);
            list.add(val);
            skipWs(s, pos);
            if (!(pos[0] < s.length() && s.charAt(pos[0]) == ',')) {
                break;
            }
            pos[0] = pos[0] + 1;
        }
        if (pos[0] < s.length() && s.charAt(pos[0]) == ']') {
            pos[0] = pos[0] + 1;
        }
        return list;
    }

    private static String parseStr(String s, int[] pos) {
        StringBuilder sb = new StringBuilder();
        pos[0] = pos[0] + 1;
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '"') {
                pos[0] = pos[0] + 1;
                return sb.toString();
            }
            if (c == '\\' && pos[0] + 1 < s.length()) {
                pos[0] = pos[0] + 1;
                char e = s.charAt(pos[0]);
                if (e == 'n') {
                    sb.append('\n');
                } else if (e == 't') {
                    sb.append('\t');
                } else if (e == 'r') {
                    sb.append('\r');
                } else if (e == 'b') {
                    sb.append('\b');
                } else if (e == 'f') {
                    sb.append('\f');
                } else if (e == 'u') {
                    if (pos[0] + 4 < s.length()) {
                        String hex = s.substring(pos[0] + 1, pos[0] + 5);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (Exception ex) {
                            sb.append('?');
                        }
                        pos[0] = pos[0] + 4;
                    } else {
                        sb.append(e);
                    }
                } else {
                    sb.append(e);
                }
                pos[0] = pos[0] + 1;
            } else {
                sb.append(c);
                pos[0] = pos[0] + 1;
            }
        }
        return sb.toString();
    }
}
