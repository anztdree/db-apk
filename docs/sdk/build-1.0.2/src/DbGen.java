package com.db.local;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Random;

/**
 * GENERATOR sesi lokal 1.0.2 (koreksi user 2026-10-20):
 * uid/token DIBANGKITKAN BARU — BUKAN sisa jejak panen.
 *
 * Basis sah (docs/sdk/bukti/riset-login/dump-generator-basis.txt):
 *   - token = string OPAQUE di seluruh rantai klien (parser SDK hanya getString,
 *     gerbang game hanya baca getInt("code")), jadi server lokal berhak generate.
 *   - Pola server asli ditiru: uid stabil per perangkat, loginToken baru per
 *     panggilan, sign STABIL per uid (bukti panen: register & login uid sama → sign sama).
 *
 * SOURCE CONFIG JELAS:
 *   - Semua BENTUK respons berasal dari file template di mirror
 *     (qsdk/v1/…template + qsdk/game/loginchecknative.template) — KODE TIDAK
 *     MENULIS BENTUK RESPONS. Kode hanya mengisi placeholder {{…}}.
 *   - Semua ATURAN generate berasal dari server/generator.json (external files,
 *     bisa diedit user; copyDir tidak menimpa file ada — edit bertahan).
 *
 * State:
 *   uid/username : di-seed sekali (kosong → auto) lalu persisten di generator.json
 *   authToken    : sesi SDK — BARU per boot
 *   token        : BARU per panggilan (registerVisitor/registerUser/loginByName)
 *   loginToken   : BARU per panggilan gerbang
 *   sign         : md5(uid + salt) — STABIL per uid
 */
public final class DbGen {

    private static final Object LOCK = new Object();

    /** Nama file state generator di <external>/server/ (bisa diedit user). */
    public static final String GEN_FILE = "generator.json";

    private static volatile boolean loaded = false;
    private static volatile String uid = "";
    private static volatile String username = "";
    private static volatile String salt = "dblocal-102";
    private static volatile String authToken = ""; // sesi SDK: baru per boot

    private DbGen() {
    }

    /** Muat/seed state dari <external>/server/generator.json. Dipanggil sekali per boot. */
    public static void load(Context ctx) {
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            loaded = true;
            try {
                File ext = ctx.getExternalFilesDir(null);
                if (ext == null) {
                    log("generator: storage eksternal null");
                    return;
                }
                File f = new File(new File(ext, "server"), GEN_FILE);
                if (f.isFile()) {
                    String j = readFile(f);
                    String v = DbJson.flatGet(j, "uid");
                    if (v != null && !v.trim().isEmpty()) {
                        uid = v.trim();
                    }
                    v = DbJson.flatGet(j, "username");
                    if (v != null && !v.trim().isEmpty()) {
                        username = v.trim();
                    }
                    v = DbJson.flatGet(j, "salt");
                    if (v != null && !v.trim().isEmpty()) {
                        salt = v.trim();
                    }
                }
                boolean dirty = false;
                if (uid.isEmpty()) {
                    // 8 digit acak — pola sama dgn server asli (mis. 28486093)
                    uid = String.valueOf(10000000L + new Random().nextInt(90000000));
                    dirty = true;
                }
                if (username.isEmpty()) {
                    username = "Guest" + uid;
                    dirty = true;
                }
                if (dirty) {
                    // seed pertama: tulis balik agar persisten; edit user berikutnya dihormati
                    persist(f);
                }
                authToken = md5(uid + "|" + System.currentTimeMillis() + "|boot");
                log("generator: uid=" + uid + " username=" + username);
            } catch (Throwable t) {
                log("generator load gagal: " + t);
            }
        }
    }

    public static String uid() {
        return uid;
    }

    public static String username() {
        return username;
    }

    // ================= RESPONS GENERATOR (semua dari template mirror) =================

    /** registerVisitor — template qsdk/v1/user/registerVisitor.template (isGuest 1). */
    public static String registerVisitorJson(Context ctx) {
        String tpl = template(ctx, "qsdk/v1/user/registerVisitor.template");
        if (tpl == null) {
            return null;
        }
        return fillSession(ctx, tpl, md5(uid + "|" + System.nanoTime() + "|" + salt));
    }

    /** registerUser — template qsdk/v1/user/registerUser.template (isGuest 0, isNewUser 1). */
    public static String registerUserJson(Context ctx, byte[] body) {
        String tpl = template(ctx, "qsdk/v1/user/registerUser.template");
        if (tpl == null) {
            return null;
        }
        return fillSession(ctx, tpl, md5(uid + "|" + System.nanoTime() + "|" + salt + "|reg"),
                usernameFromEnvelope(body));
    }

    /** loginByName — template qsdk/v1/user/loginByName.template (isGuest 0). */
    public static String loginByNameJson(Context ctx, byte[] body) {
        String tpl = template(ctx, "qsdk/v1/user/loginByName.template");
        if (tpl == null) {
            return null;
        }
        return fillSession(ctx, tpl, md5(uid + "|" + System.nanoTime() + "|" + salt + "|log"),
                usernameFromEnvelope(body));
    }

    /** getUserInfo — template qsdk/v1/auth/getUserInfo.template (nickName = username). */
    public static String getUserInfoJson(Context ctx) {
        String tpl = template(ctx, "qsdk/v1/auth/getUserInfo.template");
        if (tpl == null) {
            return null;
        }
        return tpl.replace("{{USERNAME}}", DbJson.esc(username));
    }

    /**
     * Jawaban gerbang loginchecknative (bentuk 8 field live — template
     * qsdk/game/loginchecknative.template). Echo uid/username dari query,
     * loginToken baru per panggilan, sign stabil per uid.
     */
    public static String gateJson(Context ctx, String qUid, String qUsername) {
        String u = (qUid != null && !qUid.trim().isEmpty()) ? qUid.trim() : uid;
        String n = (qUsername != null && !qUsername.trim().isEmpty()) ? qUsername.trim() : username;
        String tpl = template(ctx, "qsdk/game/loginchecknative.template");
        if (tpl == null) {
            // DARURAT SAJA (template hilang = tidak seharusnya): bentuk 8 field live.
            tpl = "{\"code\":0,\"sdk\":\"BSNative\",\"nickName\":\"{{USERNAME}}\","
                    + "\"loginToken\":\"{{LOGIN_TOKEN}}\",\"userId\":\"{{UID}}\",\"sign\":\"{{SIGN}}\","
                    + "\"sdkType\":\"BS\",\"osType\":\"Android\"}";
        }
        return tpl
                .replace("{{LOGIN_TOKEN}}",
                        md5(u + "|" + System.nanoTime() + "|" + salt + "|"
                                + Long.toHexString(System.currentTimeMillis())))
                .replace("{{SIGN}}", md5(u + salt))
                .replace("{{UID}}", DbJson.esc(u))
                .replace("{{USERNAME}}", DbJson.esc(n));
    }

    /** Respons error bila envelope tak terbaca (registerUser/loginByName) — tanpa identitas. */
    public static String errorJson(String id, String message) {
        return "{\"result\":false,\"data\":[],\"error\":{\"id\":" + id
                + ",\"message\":\"" + DbJson.esc(message) + "\"}}";
    }

    // ================= INTERNAL =================

    /** Isi placeholder sesi; username = hasil envelope (fallback: generator). */
    private static String fillSession(Context ctx, String tpl, String token, String envUsername) {
        String n = (envUsername != null && !envUsername.trim().isEmpty()) ? envUsername.trim()
                : username;
        return tpl
                .replace("{{AUTH_TOKEN}}", authToken)
                .replace("{{TOKEN}}", token)
                .replace("{{UID}}", DbJson.esc(uid))
                .replace("{{USERNAME}}", DbJson.esc(n));
    }

    private static String fillSession(Context ctx, String tpl, String token) {
        return fillSession(ctx, tpl, token, null);
    }

    /**
     * Baca template: external <server>/<path> DULU (edit user menang),
     * lalu assets mirror/<path>. null bila tidak ketemu.
     */
    private static String template(Context ctx, String path) {
        try {
            File ext = ctx.getExternalFilesDir(null);
            if (ext != null) {
                File f = new File(new File(ext, "server"), path);
                if (f.isFile()) {
                    return readFile(f);
                }
            }
            InputStream in = ctx.getAssets().open("mirror/" + path);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            in.close();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ambil "username" dari envelope SDK (POST body sv=v2&data=Base64…). null bila gagal. */
    private static String usernameFromEnvelope(byte[] body) {
        try {
            if (body == null || body.length == 0 || body.length > 1048576) {
                return null;
            }
            String form = new String(body, "UTF-8");
            String data = DbJson.formValue(form, "data");
            if (data == null) {
                return null;
            }
            return DbJson.flatGet(DbJson.mimeB64(data), "username");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void persist(File f) {
        try {
            String j = "{\n"
                    + "  \"uid\": \"" + DbJson.esc(uid) + "\",\n"
                    + "  \"username\": \"" + DbJson.esc(username) + "\",\n"
                    + "  \"salt\": \"" + DbJson.esc(salt) + "\"\n"
                    + "}\n";
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(j.getBytes("UTF-8"));
            out.close();
            tmp.renameTo(f);
        } catch (Throwable t) {
            log("generator persist gagal: " + t);
        }
    }

    private static String readFile(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            in.close();
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** md5 hex lowercase — pola sama dgn server asli (sign/loginToken 32 hex). */
    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                sb.append(Integer.toString((d[i] & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(System.nanoTime()); // praktis mustahil
        }
    }

    private static void log(String m) {
        try {
            Log.i("MiniServer", m);
        } catch (Throwable ignored) {
        }
    }
}
