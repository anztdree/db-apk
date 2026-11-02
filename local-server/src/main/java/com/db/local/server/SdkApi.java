package com.db.local.server;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * API TAHAP SDK — server quickgame/zhuhuan lokal (0.4.2 ALUR SDK).
 *
 * Layar login SDK ASLI (HWLoginActivity dari client — TIDAK diubah) mengirim
 * POST ke host di assets/QHinfo. Di APK LSF, QHinfo ditulis-ulang ke
 * http://127.0.0.1:8610 sehingga seluruh tahap SDK dilayani di sini.
 *
 * KAWAT (semua hasil bedah dex com.quickgame.android.sdk + PROBE LANGSUNG
 * server asli qsdk.t4game.com — README §2.11):
 *   body  = form-urlencoded: sv=v2 & data=<base64(JSON param)> & sign=<md5>
 *   sign  = md5(k=v&… urut abjad + rahasia "0b2a18e45d7df321")
 *   jawab = {"result":bool,"data":…,"error":{"id":N,"message":"…"}}
 *
 * Endpoint (respons = salinan respons asli hasil probe):
 *   POST /v1/system/init | getNotice | getAgreement | onNewToken | dmsg
 *   POST /v1/user/registerVisitor | autoLogin | loginByName | registerUser
 *        | ckRegVistor | findPassByEmail | postGooglePlayVerify
 *   POST /v1/auth/* (bindMail, changePassword, setGameRoleInfo, jianchaLayLx, …)
 *   GET  /loginchecknative — validasi shell setelah layar SDK (login.popoh5.com:510)
 *
 * Mode ONLINE: semua permintaan SDK DITERUSKAN utuh ke qsdk.t4game.com (tanda
 * tangan & isi tidak disentuh) dan loginchecknative ke login.popoh5.com:510 —
 * perilaku online = perilaku asli.
 */
public final class SdkApi {

    /** Rahasia tanda tangan dari dex (com.quickgame.android.sdk.unnecessarysperrylites.seventygenom). */
    static final String SIGN_SECRET = "0b2a18e45d7df321";

    /** Data /v1/system/init — SALINAN respons asli qsdk (probe 2026-11-02), versi "empty" = tanpa paksa update. */
    private static final String INIT_DATA =
            "{\"payTypes\":[],"
                    + "\"version\":{\"versionName\":\"empty\",\"versionNo\":\"empty\",\"versionUrl\":\"empty\","
                    + "\"updateTime\":\"empty\",\"isMust\":\"empty\",\"updateTips\":\"empty\"},"
                    + "\"productConfig\":{\"useServiceCenter\":\"0\",\"isShowFloat\":\"0\",\"showShare\":\"1\","
                    + "\"title\":\"\",\"logo\":\"\",\"useSms\":\"0\",\"skinStyle\":\"0\",\"serviceInfo\":\"\","
                    + "\"guestShowBind\":1,\"single_play\":0,\"adZhifuSwt\":0,\"noticeNode\":0,\"regMailVerify\":0,"
                    + "\"pycenttype\":0,\"onGoToPUrl\":0,\"onGoToPUrlData\":\"\",\"trashTime\":0,"
                    + "\"noFirebaseLog\":0,\"logEvtReport\":0},"
                    + "\"otherpayType\":2,\"payFast\":[],\"isNotGift\":0,\"showShare\":\"1\","
                    + "\"requestIp\":\"127.0.0.1\",\"showDiscount\":0,"
                    + "\"nodes\":{\"deleteAccount\":0,\"serverInfo\":0,\"oneYeaerTips\":0,\"removeEmail\":0},"
                    + "\"loginTypes\":[\"1\",\"13\"],\"countryCode\":\"ID\",\"first_install\":1}";

    private static final String AGREEMENT_DATA =
            "{\"agreement\":\"\",\"privacy\":\"\",\"legal\":\"\",\"eula\":\"\",\"cookieAgree\":\"\","
                    + "\"tsNotAgree\":\"\",\"unRegAgree\":\"\",\"prototalSBCM\":\"\",\"prototalFDML\":\"\"}";

    private static volatile boolean initLogged = false;

    private SdkApi() {
    }

    /** Null bila uri bukan rute SDK — caller (LoginServer) lanjut ke jalur socket.io. */
    public static Response handle(Method m, String uri, Map<String, String> parms, String body) {
        if (uri == null) {
            return null;
        }
        if (uri.equals("/loginchecknative") || uri.equals("/loginchecknative/")) {
            return loginCheckNative(m, uri, parms, body);
        }
        if (uri.startsWith("/v1/")) {
            if (m != Method.POST) {
                return json(NanoHTTPD.Response.Status.OK,
                        "{\"result\":false,\"data\":[],\"error\":{\"id\":20001,\"message\":\"Metode harus POST\"}}");
            }
            return v1(uri, body == null ? "" : body);
        }
        return null;
    }

    // ---------------- loginchecknative (shell → validasi sesi SDK) ----------------

    private static Response loginCheckNative(Method m, String uri, Map<String, String> parms, String body) {
        boolean online = "online".equals(Config.mode());
        if (online) {
            // mode ONLINE: bin asli dipakai shell (login.popoh5.com:510) — bila tetap
            // tiba di sini (sisa bin lokal sebelum ganti mode), teruskan apa adanya.
            return proxyLoginCheckNative(mergeParms(parms, body));
        }
        Map<String, String> all = mergeParms(parms, body);
        String uid = all.get("uid");
        String username = all.get("username");
        String token = all.get("token");
        String usermode = all.get("usermode");
        String data = SdkData.checkNative(uid, username, token, usermode);
        if (data == null) {
            return json(NanoHTTPD.Response.Status.OK, "{\"code\":500,\"msg\":\"登录参数验证失败\"}");
        }
        return json(NanoHTTPD.Response.Status.OK, data);
    }

    /** Gabungkan parameter query + field form body POST (body menang). */
    private static Map<String, String> mergeParms(Map<String, String> parms, String body) {
        java.util.HashMap<String, String> out = new java.util.HashMap<String, String>();
        if (parms != null) {
            out.putAll(parms);
        }
        if (body != null && body.length() > 0 && body.indexOf('&') >= 0 || (body != null && body.indexOf('=') >= 0)) {
            String[] parts = body.split("&");
            for (String part : parts) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                out.put(urlDec(part.substring(0, eq)), urlDec(part.substring(eq + 1)));
            }
        }
        return out;
    }

    // ---------------- /v1/* ----------------

    private static Response v1(String uri, String body) {
        boolean online = "online".equals(Config.mode());
        if (online) {
            return proxyV1(uri, body);
        }
        Map<String, String> p = parseData(body);
        String authToken = p.get("authToken");
        String deviceId = p.get("deviceId");
        String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        String data;
        boolean ok = true;
        String errMsg = "";

        if (path.equals("/v1/system/init")) {
            data = INIT_DATA;
            if (!initLogged) {
                initLogged = true;
                log('ℹ', "SDK: server SDK lokal siap — layar login SDK ASLI dilayani di sini"
                        + " (init dari perangkat " + shortId(deviceId) + ")", null);
            }
        } else if (path.equals("/v1/system/getNotice")) {
            data = "[]";
        } else if (path.equals("/v1/system/getAgreement")) {
            data = AGREEMENT_DATA;
        } else if (path.equals("/v1/system/onNewToken") || path.equals("/v1/system/dmsg")
                || path.equals("/v1/auth/unBindOtherInfo") || path.equals("/v1/auth/fbLikeEvent")
                || path.equals("/v1/auth/fbUserClaimEvent") || path.equals("/v1/auth/setGameRoleInfo")
                || path.equals("/v1/auth/jianchaLayLx") || path.equals("/v1/auth/bindMail")
                || path.equals("/v1/user/ckRegVistor") || path.equals("/v1/user/postGooglePlayVerify")) {
            // endpoint pelengkap — jawab sukses kosong (persis pola respons asli untuk
            // keadaan "tidak ada data": data [] / objek kecil). rincian di bawah bila khusus.
            if (path.equals("/v1/user/ckRegVistor")) {
                String r = SdkData.ckRegVistor(authToken);
                data = r == null ? "{\"isReged\":0}" : r;
            } else if (path.equals("/v1/auth/bindMail")) {
                String mail = p.get("email");
                String user = p.get("username");
                LoginData.ensureAccount(user == null ? String.valueOf(SdkData.primaryGuest()) : user, "");
                data = "[]";
                log('ℹ', "SDK: ikat email " + (mail == null ? "(kosong)" : mail) + " → "
                        + (user == null ? "(tamu)" : user) + " — diterima lokal", null);
            } else if (path.equals("/v1/auth/setGameRoleInfo")) {
                data = "[]";
                log('ℹ', "SDK: info peran — uid " + shortId(p.get("uid")) + " server "
                        + p.get("serverId") + " peran " + p.get("roleId"), null);
            } else if (path.equals("/v1/auth/changePassword")) {
                String user = p.get("username");
                String pass = p.get("password");
                if (user != null && LoginData.accountPassword(user) != null && pass != null && pass.length() == 32) {
                    LoginData.ensureAccount(user, pass);
                    data = "[]";
                    log('ℹ', "SDK: kata sandi akun " + user + " diganti", null);
                } else {
                    ok = false;
                    errMsg = "Akun tidak dikenal atau parameter kurang";
                    data = "[]";
                }
            } else if (path.equals("/v1/auth/jianchaLayLx")) {
                data = "[]";
            } else {
                data = "[]";
            }
        } else if (path.equals("/v1/user/registerVisitor")) {
            String r = SdkData.registerVisitor(deviceId, p.get("clientLang"));
            if (r == null) {
                ok = false;
                errMsg = "Gagal mendaftarkan tamu";
                data = "[]";
            } else {
                data = r;
            }
        } else if (path.equals("/v1/user/autoLogin")) {
            String r = SdkData.autoLogin(authToken, deviceId);
            if (r == null) {
                ok = false;
                data = "[]";
                return err(40005, "Otorisasi gagal");
            } else {
                data = r;
                log('▶', "SDK: masuk otomatis — " + jsonStr(r, "uid"), null);
            }
        } else if (path.equals("/v1/user/loginByName")) {
            String r = SdkData.loginByName(p.get("username"), p.get("password"));
            if (r == null) {
                return err(40101, "akun atau password tidak ada");
            }
            data = r;
            log('▶', "SDK: akun masuk — " + p.get("username"), null);
        } else if (path.equals("/v1/user/registerUser")) {
            String r = SdkData.registerUser(p.get("username"), p.get("password"));
            if (r == null) {
                return err(40102, "nama akun tidak valid atau sudah dipakai");
            }
            data = r;
        } else if (path.equals("/v1/user/findPassByEmail")) {
            return err(20010, "Server lokal: lupa kata sandi tidak didukung — daftar akun baru saja");
        } else if (path.equals("/v1/auth/createOrder")) {
            return err(50001, "Pembayaran tidak tersedia di server lokal");
        } else {
            // rute SDK tak dikenal — jawab sukses kosong agar UI tidak menggantung
            data = "[]";
            log('ℹ', "SDK: rute tak dikenal " + path + " — dijawab sukses kosong", null);
        }
        StringBuilder sb = new StringBuilder(data.length() + 64);
        sb.append("{\"result\":").append(ok).append(",\"data\":").append(data)
                .append(",\"error\":{\"id\":").append(ok ? 0 : 20000)
                .append(",\"message\":\"").append(Json.esc(errMsg)).append("\"}}");
        verifySignQuietly(p, body);
        return json(NanoHTTPD.Response.Status.OK, sb.toString());
    }

    private static Response err(int id, String msg) {
        return json(NanoHTTPD.Response.Status.OK,
                "{\"result\":false,\"data\":[],\"error\":{\"id\":" + id
                        + ",\"message\":\"" + Json.esc(msg) + "\"}}");
    }

    // ---------------- proxy mode ONLINE ----------------

    private static Response proxyV1(String uri, String body) {
        try {
            byte[] out = http("http://qsdk.t4game.com" + uri, body == null ? "" : body, 8000);
            log('↔', "SDK: permintaan diteruskan ke server asli qsdk.t4game.com" + uri, null);
            return json(NanoHTTPD.Response.Status.OK, new String(out, "UTF-8"));
        } catch (Throwable t) {
            log('⚠', "SDK: penerusan ke qsdk.t4game.com gagal — " + t, null);
            return err(20000, "Connection failed, please check your network.");
        }
    }

    private static Response proxyLoginCheckNative(Map<String, String> parms) {
        StringBuilder qs = new StringBuilder();
        if (parms != null) {
            for (Map.Entry<String, String> e : parms.entrySet()) {
                if (qs.length() > 0) {
                    qs.append('&');
                }
                qs.append(urlEnc(e.getKey())).append('=').append(urlEnc(e.getValue()));
            }
        }
        try {
            byte[] out = http("https://login.popoh5.com:510/loginchecknative" + (qs.length() > 0 ? "?" + qs : ""),
                    "", 8000);
            return json(NanoHTTPD.Response.Status.OK, new String(out, "UTF-8"));
        } catch (Throwable t) {
            log('⚠', "SDK: penerusan loginchecknative gagal — " + t, null);
            return json(NanoHTTPD.Response.Status.OK, "{\"code\":500,\"msg\":\"no sdk\"}");
        }
    }

    private static byte[] http(String url, String body, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestMethod(body.length() > 0 ? "POST" : "GET");
        if (body.length() > 0) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] b = body.getBytes("UTF-8");
            c.setRequestProperty("Content-Length", String.valueOf(b.length));
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.flush();
            os.close();
        }
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (in != null && (n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        if (in != null) {
            in.close();
        }
        c.disconnect();
        return bos.toByteArray();
    }

    // ---------------- parsing body {"sv","data","sign"} ----------------

    /**
     * Urai body form (sv=v2&data=<b64>&sign=…) → ambil "data", base64-decode,
     * lalu ambil field flat (string/angka) dari JSON param.
     */
    static Map<String, String> parseData(String body) {
        java.util.HashMap<String, String> out = new java.util.HashMap<String, String>();
        try {
            String data = null;
            String sign = null;
            String sv = null;
            if (body != null && body.length() > 0) {
                String[] parts = body.split("&");
                for (String part : parts) {
                    int eq = part.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String k = urlDec(part.substring(0, eq));
                    String v = urlDec(part.substring(eq + 1));
                    if (k.equals("data")) {
                        data = v;
                    } else if (k.equals("sign")) {
                        sign = v;
                    } else if (k.equals("sv")) {
                        sv = v;
                    }
                }
            }
            if (sv != null) {
                out.put("__sv", sv);
            }
            if (sign != null) {
                out.put("__sign", sign);
            }
            if (data == null || data.length() == 0) {
                return out;
            }
            String json;
            try {
                json = new String(android.util.Base64.decode(data, android.util.Base64.DEFAULT), "UTF-8");
            } catch (Throwable t2) {
                json = new String(java.util.Base64.getDecoder().decode(data), "UTF-8");
            }
            out.put("__json", json);
            // field flat: "k":"v" atau "k":angka
            int i = 0;
            final int len = json.length();
            while (i < len) {
                int q1 = json.indexOf('"', i);
                if (q1 < 0) {
                    break;
                }
                int q2 = json.indexOf('"', q1 + 1);
                if (q2 < 0) {
                    break;
                }
                String key = json.substring(q1 + 1, q2);
                int colon = json.indexOf(':', q2 + 1);
                if (colon < 0) {
                    break;
                }
                int j = colon + 1;
                while (j < len && json.charAt(j) == ' ') {
                    j++;
                }
                if (j < len && json.charAt(j) == '"') {
                    int k = j + 1;
                    StringBuilder sb = new StringBuilder();
                    while (k < len && json.charAt(k) != '"') {
                        if (json.charAt(k) == '\\' && k + 1 < len) {
                            sb.append(json.charAt(k + 1));
                            k += 2;
                            continue;
                        }
                        sb.append(json.charAt(k));
                        k++;
                    }
                    out.put(key, sb.toString());
                    i = k + 1;
                } else {
                    int k = j;
                    while (k < len && json.charAt(k) != ',' && json.charAt(k) != '}') {
                        k++;
                    }
                    out.put(key, json.substring(j, k).trim());
                    i = k;
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Verifikasi tanda tangan SDK — hanya mencatat ⚠ bila tidak cocok (tidak menolak). */
    private static void verifySignQuietly(Map<String, String> p, String body) {
        try {
            String json = p.get("__json");
            String sign = p.get("__sign");
            if (json == null || sign == null || json.length() == 0) {
                return;
            }
            java.util.TreeMap<String, String> sorted = new java.util.TreeMap<String, String>();
            // pakai field yang sudah diurai (tanpa kunci internal __*)
            for (Map.Entry<String, String> e : p.entrySet()) {
                String k = e.getKey();
                if (k.startsWith("__")) {
                    continue;
                }
                sorted.put(k, e.getValue());
            }
            StringBuilder buf = new StringBuilder();
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                buf.append(e.getKey()).append('=').append(e.getValue() == null ? "" : e.getValue()).append('&');
            }
            buf.append(SIGN_SECRET);
            String expect = SdkData.md5(buf.toString());
            if (!expect.equalsIgnoreCase(sign)) {
                log('⚠', "SDK: tanda tangan permintaan tidak cocok (diterima — server lokal)", "sign=" + sign);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- util ----------------

    private static String parm(Map<String, String> parms, String key) {
        if (parms == null) {
            return null;
        }
        String v = parms.get(key);
        return (v == null || v.length() == 0) ? null : v;
    }

    /** Nilai string level atas dari JSON (untuk log — ambil "uid" dari respons sesi). */
    private static String jsonStr(String obj, String key) {
        if (obj == null || key == null) {
            return "?";
        }
        String needle = "\"" + key + "\":";
        int i = obj.indexOf(needle);
        if (i < 0) {
            return "?";
        }
        int a = obj.indexOf('"', i + needle.length());
        if (a < 0) {
            return "?";
        }
        int b = a + 1;
        while (b < obj.length() && obj.charAt(b) != '"') {
            b++;
        }
        if (b >= obj.length()) {
            return "?";
        }
        return obj.substring(a + 1, b);
    }

    private static String shortId(String s) {
        if (s == null) {
            return "?";
        }
        return s.length() <= 10 ? s : s.substring(0, 10) + "…";
    }

    private static String urlEnc(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Throwable t) {
            return s == null ? "" : s;
        }
    }

    private static String urlDec(String s) {
        try {
            return java.net.URLDecoder.decode(s == null ? "" : s, "UTF-8");
        } catch (Throwable t) {
            return s == null ? "" : s;
        }
    }

    private static void log(char icon, String msg, String detail) {
        try {
            HumanLog.ev(icon, msg, detail);
        } catch (Throwable ignored) {
        }
    }

    private static Response json(NanoHTTPD.Response.IStatus st, String body) {
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(st, "application/json; charset=utf-8", body);
        r.addHeader("Cache-Control", "no-store");
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }
}
