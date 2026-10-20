package com.db.local;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GENERATOR sesi lokal + DATABASE AKUN (1.0.4 — keberatan user: "ini kan masuk
 * ranah database !! password cocok login !!").
 *
 * 1.0.3 sudah benar urus SESI (autoLogin memvalidasi authToken terbitan, Opsi A)
 * tapi masih TIDAK punya database akun: username/password dari envelope dibaca
 * lalu DIBUANG — loginByName/registerUser menerima apa pun. Itu tidak setia
 * server asli. 1.0.4 memperbaiki dengan BUKTI (lihat BUILD-1.0.4.md):
 *
 *   - dex (const-string): registerUser = {vcode, username, password};
 *     loginByName = {username, password}
 *   - envelope live panen: password dikirim SDK = MD5 hex klien
 *     ("22dc0c1e…" panen; HAR perangkat #28 "e10adc3949…" = md5("123456"))
 *   - HAR #32: gerbang membawa uid PER AKUN (16722253 utk yihonew48…) →
 *     server asli memberi uid per akun, bukan satu uid global
 *
 * DESAIN DATABASE AKUN (accounts.txt di <external>/server/ — TSV, edit user
 * dihormati, copyDir re-sync TIDAK menimpa file ada):
 *
 *   username <TAB> passHash <TAB> uid <TAB> isGuest
 *
 *   - passHash = md5(password_dari_SDK + salt). Password klien TIDAK PERNAH
 *     disimpan mentah (bukan plaintext, bukan md5 klien apa adanya).
 *   - passHash kosong = akun guest (tidak bisa login by name).
 *   - uid 8-digit UNIK per akun (pola server asli, bukti HAR #32).
 *   - Seed pertama: 1 baris guest {username generator, uid generator} —
 *     uid guest stabil antar boot persis perilaku 1.0.1-1.0.3.
 *
 * KEBIJAKAN LOGIN (setia server asli):
 *   - loginByName: username tak dikenal ATAU password salah → result:false
 *     (bentuk error live 40008, panen live__user__registerUser.bin) → SDK
 *     tampilkan pesan; username+password cocok → sesi akun ITU (uid akun).
 *   - registerUser: username sudah ada → result:false (duplikat ditolak);
 *     baru → buat baris akun + uid baru + sesi akun.
 *   - autoLogin: tetap Opsi A 1.0.3 — authToken cocok last* → sesi akun itu
 *     (kini termasuk lastUid), tidak cocok → result:false.
 *   - getUserInfo: params dex/HAR = {authToken, uid} → jawab per sesi:
 *     authToken cocok last* → lastUsername; else uid → akun; else generator.
 *
 * SOURCE CONFIG JELAS:
 *   - BENTUK respons selalu dari template mirror (kode hanya isi placeholder).
 *   - qsdk/v1/user/error.template (BARU 1.0.4) = BENTUK panen error live 40008
 *     utk penolakan login/register; ISI id+message = kebijakan lokal (diakui
 *     jujur — tidak ada materi live error login/register).
 *   - autoLogin.error.template tetap dipakai utk autoLogin (1.0.3).
 *
 * State:
 *   uid/username   : generator (guest) — seed sekali, persisten
 *   salt           : dblocal-102 (editable generator.json)
 *   ACCOUNTS       : database akun (accounts.txt)
 *   last*          : sesi terakhir yang diterbitkan (autoLogin Opsi A) + lastUid
 */
public final class DbGen {

    private static final Object LOCK = new Object();

    /** Nama file state generator di <external>/server/ (bisa diedit user). */
    public static final String GEN_FILE = "generator.json";

    /** DATABASE akun di <external>/server/ (TSV; lihat header kelas). */
    public static final String ACCOUNTS_FILE = "accounts.txt";

    private static volatile boolean loaded = false;
    private static volatile String uid = "";
    private static volatile String username = "";
    private static volatile String salt = "dblocal-102";

    /** Sesi terakhir yang KITA terbitkan — dasar validasi autoLogin. */
    private static volatile String lastAuthToken = "";
    private static volatile String lastUid = ""; // 1.0.4: uid akun sesi terakhir
    private static volatile String lastUsername = "";
    private static volatile boolean lastIsGuest = true;

    /** DATABASE akun (mirror accounts.txt). */
    private static final List<Acct> ACCOUNTS = new ArrayList<Acct>();

    /** Satu baris database akun. */
    static final class Acct {
        final String name;    // username (tanpa \t \n \r)
        final String pass;    // md5(password_klien + salt); "" = guest
        final String uid;     // 8-digit unik
        final boolean guest;  // true = akun guest

        Acct(String name, String pass, String uid, boolean guest) {
            this.name = name;
            this.pass = pass;
            this.uid = uid;
            this.guest = guest;
        }
    }

    /** Kebijakan server lokal utk token autoLogin yang tidak dikenal.
     *  Bentuk envelope = panen error live 40008; ISI ini TIDAK ada di materi
     *  live (diakui jujur di BUILD-1.0.3.md) — SDK hanya membaca result=false
     *  (dex: p6.obj = v5_1 → layar login); message sekadar tampilan bila ada. */
    static final String ERR_AUTO_ID = "1";
    static final String ERR_AUTO_MSG = "sesi telah berakhir, silakan login kembali";

    /** Kebijakan lokal 1.0.4 — penolakan login/register (bentuk error.template). */
    static final String ERR_LOGIN_ID = "2";
    static final String ERR_LOGIN_MSG = "username atau password salah";
    static final String ERR_REG_ID = "3";
    static final String ERR_REG_MSG = "username sudah terdaftar, silakan login";
    static final String ERR_DATA_ID = "4";
    static final String ERR_DATA_MSG = "data tidak lengkap, coba lagi";

    private DbGen() {
    }

    /** Muat/seed state dari <external>/server/. Dipanggil sekali per boot. */
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
                    // sesi terakhir (boleh kosong — file lama 1.0.2)
                    v = DbJson.flatGet(j, "lastAuthToken");
                    if (v != null) {
                        lastAuthToken = v.trim();
                    }
                    v = DbJson.flatGet(j, "lastUid"); // 1.0.4
                    if (v != null) {
                        lastUid = v.trim();
                    }
                    v = DbJson.flatGet(j, "lastUsername");
                    if (v != null) {
                        lastUsername = v.trim();
                    }
                    v = DbJson.flatGet(j, "lastIsGuest");
                    if (v != null) {
                        lastIsGuest = !"0".equals(v.trim());
                    }
                }
                boolean dirty = false;
                if (uid.isEmpty()) {
                    // 8 digit acak — pola sama dgn server asli (mis. 16722253)
                    uid = String.valueOf(10000000L + new Random().nextInt(90000000));
                    dirty = true;
                }
                if (username.isEmpty()) {
                    username = "Guest" + uid;
                    dirty = true;
                }
                // 1.0.4: migrasi last* era 1.0.3 (satu uid global) → lastUid = uid
                if (!lastUsername.isEmpty() && lastUid.isEmpty()) {
                    lastUid = uid;
                    dirty = true;
                }
                if (dirty) {
                    persist(f); // seed pertama; edit user berikutnya dihormati
                }
                loadAccounts(new File(new File(ext, "server"), ACCOUNTS_FILE));
                log("generator: uid=" + uid + " username=" + username
                        + " akun=" + ACCOUNTS.size());
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

    /** registerVisitor — sesi akun GUEST dari database (uid stabil per perangkat). */
    public static String registerVisitorJson(Context ctx) {
        synchronized (LOCK) {
            Acct g = guestRow();
            String tpl = template(ctx, "qsdk/v1/user/registerVisitor.template");
            if (tpl == null) {
                return null;
            }
            return session(ctx, tpl, g.uid, g.name, true);
        }
    }

    /**
     * registerUser 1.0.4 — DATABASE + validasi:
     *   username/password wajib (dex: params = {vcode, username, password});
     *   duplikat ditolak; baru → baris akun + uid 8-digit unik + sesi akun.
     */
    public static String registerUserJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String name = cleanName(valueFromEnvelope(body, "username"));
            String pass = rawOrNull(valueFromEnvelope(body, "password"));
            if (name == null || pass == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            if (accountBy(name) != null) {
                return errorJsonCtx(ctx, ERR_REG_ID, ERR_REG_MSG);
            }
            Acct a = new Acct(name, md5(pass + salt), uniqueUid(), false);
            ACCOUNTS.add(a);
            persistAccounts(accountsFile(ctx));
            String tpl = template(ctx, "qsdk/v1/user/registerUser.template");
            if (tpl == null) {
                return null;
            }
            return session(ctx, tpl, a.uid, a.name, false);
        }
    }

    /**
     * loginByName 1.0.4 — DATABASE + validasi password (inti keberatan user):
     *   tak dikenal / password salah / akun guest → result:false bentuk error
     *   live → SDK tampilkan pesan; cocok → sesi akun itu (uid akun).
     */
    public static String loginByNameJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String name = cleanName(valueFromEnvelope(body, "username"));
            String pass = rawOrNull(valueFromEnvelope(body, "password"));
            if (name == null || pass == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            Acct a = accountBy(name);
            if (a == null || a.guest || !md5(pass + salt).equals(a.pass)) {
                return errorJsonCtx(ctx, ERR_LOGIN_ID, ERR_LOGIN_MSG);
            }
            String tpl = template(ctx, "qsdk/v1/user/loginByName.template");
            if (tpl == null) {
                return null;
            }
            return session(ctx, tpl, a.uid, a.name, false);
        }
    }

    /**
     * autoLogin — Opsi A 1.0.3, kini dengan lastUid: sesi akun yang dipulihkan
     * membawa uid akun itu (bukan uid global), persis server asli.
     */
    public static String autoLoginJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String incoming = rawOrNull(valueFromEnvelope(body, "authToken"));
            boolean ok = incoming != null && !incoming.isEmpty()
                    && !lastAuthToken.isEmpty()
                    && lastAuthToken.equals(incoming);
            if (!ok) {
                String etpl = template(ctx, "qsdk/v1/user/autoLogin.error.template");
                if (etpl != null) {
                    return etpl
                            .replace("{{ERROR_ID}}", ERR_AUTO_ID)
                            .replace("{{ERROR_MESSAGE}}", DbJson.esc(ERR_AUTO_MSG));
                }
                return errorJson(ERR_AUTO_ID, ERR_AUTO_MSG); // darurat saja
            }
            String tpl = template(ctx, "qsdk/v1/user/autoLogin.template");
            if (tpl == null) {
                return null; // serveJson → 404 (template hilang = tidak seharusnya)
            }
            String n = lastUsername.trim().isEmpty() ? username : lastUsername.trim();
            String u = lastUid.trim().isEmpty() ? uid : lastUid.trim();
            return session(ctx, tpl, u, n, lastIsGuest);
        }
    }

    /**
     * getUserInfo 1.0.4 — params dex/HAR #42 = {authToken, uid} → jawab per
     * sesi: authToken cocok last* → lastUsername; else uid → akun; else generator.
     */
    public static String getUserInfoJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String tpl = template(ctx, "qsdk/v1/auth/getUserInfo.template");
            if (tpl == null) {
                return null;
            }
            String at = rawOrNull(valueFromEnvelope(body, "authToken"));
            String n = null;
            if (at != null && !at.isEmpty() && at.equals(lastAuthToken)
                    && !lastUsername.isEmpty()) {
                n = lastUsername;
            } else {
                String qUid = rawOrNull(valueFromEnvelope(body, "uid"));
                Acct a = qUid == null ? null : accountByUid(qUid);
                if (a != null) {
                    n = a.name;
                }
            }
            if (n == null || n.isEmpty()) {
                n = username;
            }
            return tpl.replace("{{USERNAME}}", DbJson.esc(n));
        }
    }

    /**
     * Jawaban gerbang loginchecknative (bentuk 8 field live — template
     * qsdk/game/loginchecknative.template). Echo uid/username dari query
     * (uid per akun — bukti HAR #32), loginToken baru per panggilan, sign
     * md5(uid + salt) stabil per uid.
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

    /** Respons error bila envelope tak terbaca — tanpa identitas. */
    public static String errorJson(String id, String message) {
        return "{\"result\":false,\"data\":[],\"error\":{\"id\":" + id
                + ",\"message\":\"" + DbJson.esc(message) + "\"}}";
    }

    /** Error via template qsdk/v1/user/error.template (bentuk live 40008). */
    static String errorJsonCtx(Context ctx, String id, String message) {
        String tpl = template(ctx, "qsdk/v1/user/error.template");
        if (tpl == null) {
            return errorJson(id, message); // darurat saja
        }
        return tpl
                .replace("{{ERROR_ID}}", id)
                .replace("{{ERROR_MESSAGE}}", DbJson.esc(message));
    }

    // ================= INTERNAL =================

    /**
     * Isi placeholder sesi dari DATA AKUN + authToken sesi BARU (rotasi seperti
     * server asli: respons sesi sukses = sesi baru). Mencatat last* (termasuk
     * lastUid) agar autoLogin boot berikutnya memulihkan akun itu.
     */
    private static String session(Context ctx, String tpl, String acctUid, String acctName,
            boolean guest) {
        String at = md5("auth|" + acctUid + "|" + System.nanoTime() + "|" + salt);
        String token = md5(acctUid + "|" + System.nanoTime() + "|" + salt + "|tok");
        String out = tpl
                .replace("{{AUTH_TOKEN}}", at)
                .replace("{{TOKEN}}", token)
                .replace("{{UID}}", DbJson.esc(acctUid))
                .replace("{{USERNAME}}", DbJson.esc(acctName))
                .replace("{{IS_GUEST}}", guest ? "1" : "0");
        rememberSession(ctx, at, acctUid, acctName, guest);
        return out;
    }

    /** Catat sesi terakhir (state + persist ke generator.json). */
    private static void rememberSession(Context ctx, String at, String acctUid, String n,
            boolean guest) {
        synchronized (LOCK) {
            lastAuthToken = (at == null) ? "" : at.trim();
            lastUid = (acctUid == null) ? "" : acctUid.trim();
            lastUsername = (n == null) ? "" : n.trim();
            lastIsGuest = guest;
            try {
                File ext = ctx.getExternalFilesDir(null);
                if (ext != null) {
                    persist(new File(new File(ext, "server"), GEN_FILE));
                }
            } catch (Throwable t) {
                log("rememberSession gagal: " + t);
            }
        }
    }

    // ================= DATABASE AKUN (accounts.txt) =================

    private static File accountsFile(Context ctx) {
        File ext = ctx.getExternalFilesDir(null);
        return new File(new File(ext, "server"), ACCOUNTS_FILE);
    }

    /** Muat accounts.txt; hilang → seed 1 baris guest (uid/username generator). */
    private static void loadAccounts(File f) {
        ACCOUNTS.clear();
        try {
            if (f.isFile()) {
                String[] lines = readFile(f).split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] c = line.split("\t", -1);
                    if (c.length != 4) {
                        log("accounts.txt baris " + (i + 1) + " rusak, dilewati");
                        continue;
                    }
                    String name = cleanName(c[0]);
                    if (name == null || c[2].trim().isEmpty()) {
                        continue;
                    }
                    ACCOUNTS.add(new Acct(name, c[1].trim(), c[2].trim(),
                            "1".equals(c[3].trim())));
                }
            }
        } catch (Throwable t) {
            log("loadAccounts gagal: " + t);
        }
        if (guestRow() == null) {
            // guest wajib ada (uid/username generator = identitas perangkat)
            ACCOUNTS.add(new Acct(username, "", uid, true));
            persistAccounts(f);
        }
    }

    private static void persistAccounts(File f) {
        try {
            if (f == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# DB akun lokal (1.0.4) — username<TAB>passHash<TAB>uid<TAB>isGuest\n");
            sb.append("# passHash = md5(password_sdk + salt); kosong = guest (tak bisa login by name)\n");
            sb.append("# edit dihormati; berlaku setelah app tutup-buka\n");
            for (int i = 0; i < ACCOUNTS.size(); i++) {
                Acct a = ACCOUNTS.get(i);
                sb.append(a.name).append('\t').append(a.pass).append('\t')
                        .append(a.uid).append('\t').append(a.guest ? "1" : "0").append('\n');
            }
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
            tmp.renameTo(f);
        } catch (Throwable t) {
            log("persistAccounts gagal: " + t);
        }
    }

    private static Acct guestRow() {
        for (int i = 0; i < ACCOUNTS.size(); i++) {
            Acct a = ACCOUNTS.get(i);
            if (a.guest) {
                return a;
            }
        }
        return null;
    }

    private static Acct accountBy(String name) {
        for (int i = 0; i < ACCOUNTS.size(); i++) {
            Acct a = ACCOUNTS.get(i);
            if (a.name.equals(name)) {
                return a;
            }
        }
        return null;
    }

    private static Acct accountByUid(String u) {
        for (int i = 0; i < ACCOUNTS.size(); i++) {
            Acct a = ACCOUNTS.get(i);
            if (a.uid.equals(u)) {
                return a;
            }
        }
        return null;
    }

    /** uid 8-digit unik di database akun (pola server asli, HAR #32). */
    private static String uniqueUid() {
        Random r = new Random();
        for (int i = 0; i < 100; i++) {
            String c = String.valueOf(10000000L + r.nextInt(90000000));
            if (accountByUid(c) == null) {
                return c;
            }
        }
        return String.valueOf(System.nanoTime()).substring(0, 8); // praktis tak terpakai
    }

    /** Nama akun aman-TSV: buang \t/\n/\r, trim; null bila kosong. */
    private static String cleanName(String s) {
        if (s == null) {
            return null;
        }
        String c = s.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim();
        return c.isEmpty() ? null : c;
    }

    private static String rawOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ================= ENVELOPE / TEMPLATE / IO =================

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

    /** Ambil satu field JSON dari envelope SDK (POST body sv=v2&data=Base64…). */
    private static String valueFromEnvelope(byte[] body, String key) {
        try {
            if (body == null || body.length == 0 || body.length > 1048576) {
                return null;
            }
            String form = new String(body, "UTF-8");
            String data = DbJson.formValue(form, "data");
            if (data == null) {
                return null;
            }
            return DbJson.flatGet(DbJson.mimeB64(data), key);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void persist(File f) {
        try {
            String j = "{\n"
                    + "  \"uid\": \"" + DbJson.esc(uid) + "\",\n"
                    + "  \"username\": \"" + DbJson.esc(username) + "\",\n"
                    + "  \"salt\": \"" + DbJson.esc(salt) + "\",\n"
                    + "  \"lastAuthToken\": \"" + DbJson.esc(lastAuthToken) + "\",\n"
                    + "  \"lastUid\": \"" + DbJson.esc(lastUid) + "\",\n"
                    + "  \"lastUsername\": \"" + DbJson.esc(lastUsername) + "\",\n"
                    + "  \"lastIsGuest\": " + (lastIsGuest ? "1" : "0") + "\n"
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
