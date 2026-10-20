package com.db.local;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GENERATOR sesi lokal + DATABASE AKUN PENUH (1.0.5-A — mandat user:
 * "semua fitur SDK wajib bisa di local ... natural"; tanpa jaring, tanpa
 * catch-all: setiap endpoint diterapkan sungguhan).
 *
 * 1.0.4 sudah benar urus DATABASE AKUN + validasi password (accounts.txt).
 * 1.0.5-A menambahkan 8 endpoint AKUN & KEAMANAN dari dex asli
 * (bukti: docs/sdk/bukti/dex-evidence/dump105/, kontrak per endpoint di
 * RENCANA-1.0.5.md):
 *
 *   sendCodeByEmail   {email, sendType}                 → kode 6 digit
 *   findPassByEmail   {email, code, newPassword}        → SESI REFRESH
 *   changePassword    {uid, oldPassword, newPassword}   → result
 *   bindMail          {uid, email, code, password}      → SESI REFRESH
 *   viewMigCode       {uid}                             → data.migCode
 *   cUserTrash        {uid, tStatus 1|2}                → result (trash/hapus-restore)
 *   unBindOtherInfo   {uid, openType, userOpenId}       → data.status
 *   userBindOtherSdk  {uid, openType, userOpenId, otherAccountName,
 *                      access_token, appToken}          → data.status
 *
 * BUKTI BENTUK RESPONS (dex, bukan karangan):
 *   - bindMail/findPassByEmail sukses → getString("data") → parser sesi
 *     psalmicvolleyball (authToken + userData{username,uid,token,isGuest,wajib})
 *     → SDK MENGGANTI SESI (callback 12). Bentuk = templat registerUser
 *     (terbukti di perangkat): data = objek {authToken, userData, checkRealName};
 *     Android org.json lenient — getString di objek mengembalikan JSON text-nya.
 *   - sendCodeByEmail/viewMigCode/unBind/userBind sukses → getJSONObject("data")
 *     → data WAJIB objek.
 *   - changePassword/cUserTrash → hanya getBoolean("result").
 *   - Semua fail path → getJSONObject("error") → error SELALU objek.
 *   - Password klien = MD5 (helper unnecessarysperrylites.seventygenom.singersmooth
 *     = MessageDigest MD5 — dump105/helper-password.txt).
 *
 * DATABASE TAMBAHAN (di <external>/server/ — TSV, re-sync versi MENJAGA):
 *   accounts.txt : name<TAB>pass<TAB>uid<TAB>isGuest<TAB>email<TAB>trash
 *                  (kolom 5-6 BARU 1.0.5; baris lama 4 kolom tetap sah —
 *                  email="" trash=0; kompatibel install-over)
 *   codes.txt    : email<TAB>code<TAB>sendType<TAB>createdMs<TAB>used
 *   bindings.txt : uid<TAB>openType<TAB>userOpenId<TAB>otherAccountName
 *   migcodes.txt : code<TAB>uid<TAB>createdMs<TAB>used
 *   outbox.txt   : "email keluar" lokal (jurnal kode verifikasi; SMTP tidak ada
 *                  di lokal — ini bentuk naturalnya, dibaca user/website 1.0.5-C)
 *
 * KEBIJAKAN (setia server asli):
 *   - bindMail: akun guest/terdaftar → identitas login MENJADI email (nama baris
 *     diganti email, persis pola akun asli "vifen44624@juicehour.com"), isGuest
 *     menjadi 0, bindEmail terisi di templat sesi.
 *   - findPassByEmail: kode sah → password baru + sesi refresh (auto-login
 *     sesi baru, persis parser dex).
 *   - cUserTrash tStatus=1 → akun trash (loginByName/autoLogin DITOLAK);
 *     tStatus=2 → dipulihkan (dex: TrashAccountActivity=1, AccountRecover=2).
 *   - loginByName: nama ATAU email cocok + password sah; trash → ditolak.
 *   - authToken masuk TIDAK divalidasi ketat di 8 endpoint ini (server asli
 *     memvalidasi; lokal memakai uid dari sesi SDK — diakui jujur di
 *     BUILD-1.0.5.md).
 */
public final class DbGen {

    private static final Object LOCK = new Object();

    /** Nama file state generator di <external>/server/ (bisa diedit user). */
    public static final String GEN_FILE = "generator.json";

    /** DATABASE akun di <external>/server/ (TSV; lihat header kelas). */
    public static final String ACCOUNTS_FILE = "accounts.txt";

    /** Database tambahan 1.0.5 (semua di <external>/server/). */
    public static final String CODES_FILE = "codes.txt";
    public static final String BINDINGS_FILE = "bindings.txt";
    public static final String MIG_FILE = "migcodes.txt";
    public static final String OUTBOX_FILE = "outbox.txt";

    /** Umur kode verifikasi email (ms) — kebijakan lokal. */
    static final long CODE_TTL_MS = 600000L;

    private static volatile boolean loaded = false;
    private static volatile String uid = "";
    private static volatile String username = "";
    private static volatile String salt = "dblocal-102";

    /** Sesi terakhir yang KITA terbitkan — dasar validasi autoLogin. */
    private static volatile String lastAuthToken = "";
    private static volatile String lastUid = "";
    private static volatile String lastUsername = "";
    private static volatile boolean lastIsGuest = true;

    /** DATABASE akun (mirror accounts.txt). */
    private static final List<Acct> ACCOUNTS = new ArrayList<Acct>();
    private static final List<CodeRow> CODES = new ArrayList<CodeRow>();
    private static final List<BindRow> BINDINGS = new ArrayList<BindRow>();
    private static final List<MigRow> MIGS = new ArrayList<MigRow>();

    /** Satu baris database akun. */
    static final class Acct {
        String name;    // username = identitas login (email utk akun ter-bind)
        String pass;    // md5(password_klien + salt); "" = guest
        final String uid;     // 8-digit unik
        boolean guest;  // true = akun guest
        String email;   // 1.0.5: email terikat ("" = belum)
        boolean trash;  // 1.0.5: status trash (cUserTrash tStatus=1)

        Acct(String name, String pass, String uid, boolean guest,
                String email, boolean trash) {
            this.name = name;
            this.pass = pass;
            this.uid = uid;
            this.guest = guest;
            this.email = email == null ? "" : email;
            this.trash = trash;
        }

        /** Kompatibilitas baris lama 1.0.4 (4 kolom). */
        Acct(String name, String pass, String uid, boolean guest) {
            this(name, pass, uid, guest, "", false);
        }
    }

    /** Baris kode verifikasi email. */
    static final class CodeRow {
        final String email;
        final String code;
        final String sendType;
        final long createdMs;
        boolean used;

        CodeRow(String email, String code, String sendType, long createdMs, boolean used) {
            this.email = email;
            this.code = code;
            this.sendType = sendType;
            this.createdMs = createdMs;
            this.used = used;
        }
    }

    /** Baris bind identitas pihak ketiga (openType 6=FB, 8=Google — dex). */
    static final class BindRow {
        final String uid;
        final String openType;
        final String userOpenId;
        final String otherAccountName;

        BindRow(String uid, String openType, String userOpenId, String otherAccountName) {
            this.uid = uid;
            this.openType = openType;
            this.userOpenId = userOpenId;
            this.otherAccountName = otherAccountName;
        }
    }

    /** Baris kode migrasi akun antar perangkat (dipakai website 1.0.5-C). */
    static final class MigRow {
        final String code;
        final String uid;
        final long createdMs;
        boolean used;

        MigRow(String code, String uid, long createdMs, boolean used) {
            this.code = code;
            this.uid = uid;
            this.createdMs = createdMs;
            this.used = used;
        }
    }

    /** Kebijakan server lokal utk token autoLogin yang tidak dikenal. */
    static final String ERR_AUTO_ID = "1";
    static final String ERR_AUTO_MSG = "sesi telah berakhir, silakan login kembali";

    /** Kebijakan lokal 1.0.4 — penolakan login/register (bentuk error.template). */
    static final String ERR_LOGIN_ID = "2";
    static final String ERR_LOGIN_MSG = "username atau password salah";
    static final String ERR_REG_ID = "3";
    static final String ERR_REG_MSG = "username sudah terdaftar, silakan login";
    static final String ERR_DATA_ID = "4";
    static final String ERR_DATA_MSG = "data tidak lengkap, coba lagi";

    /** Kebijakan lokal 1.0.5-A — akun & keamanan. */
    static final String ERR_CODE_ID = "5";
    static final String ERR_CODE_MSG = "kode verifikasi salah atau kedaluwarsa";
    static final String ERR_MAIL_ID = "6";
    static final String ERR_MAIL_MSG = "email sudah terikat pada akun lain";
    static final String ERR_TRASH_ID = "7";
    static final String ERR_TRASH_MSG = "akun dalam status terhapus";
    static final String ERR_PASS_ID = "8";
    static final String ERR_PASS_MSG = "password lama salah";

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
                    v = DbJson.flatGet(j, "lastAuthToken");
                    if (v != null) {
                        lastAuthToken = v.trim();
                    }
                    v = DbJson.flatGet(j, "lastUid");
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
                    uid = String.valueOf(10000000L + new Random().nextInt(90000000));
                    dirty = true;
                }
                if (username.isEmpty()) {
                    username = "Guest" + uid;
                    dirty = true;
                }
                if (!lastUsername.isEmpty() && lastUid.isEmpty()) {
                    lastUid = uid;
                    dirty = true;
                }
                if (dirty) {
                    persist(f);
                }
                File serverDir = new File(ext, "server");
                loadAccounts(new File(serverDir, ACCOUNTS_FILE));
                loadCodes(new File(serverDir, CODES_FILE));
                loadBindings(new File(serverDir, BINDINGS_FILE));
                loadMigs(new File(serverDir, MIG_FILE));
                log("generator: uid=" + uid + " username=" + username
                        + " akun=" + ACCOUNTS.size() + " kode=" + CODES.size()
                        + " bind=" + BINDINGS.size() + " mig=" + MIGS.size());
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

    // ================= RESPONS GENERATOR (sesi & akun 1.0.4) =================

    /** registerVisitor — sesi akun GUEST dari database (uid stabil per perangkat).
     *  1.0.5: bila baris guest tak ada (mis. guest tadi di-bind jadi akun email),
     *  seed guest BARU dgn uid unik — persis server asli yang membuat guest baru. */
    public static String registerVisitorJson(Context ctx) {
        synchronized (LOCK) {
            Acct g = guestRow();
            if (g == null) {
                String gu = uniqueUid();
                g = new Acct("Guest" + gu, "", gu, true, "", false);
                ACCOUNTS.add(g);
                persistAccounts(accountsFile(ctx));
                uid = gu;
                username = g.name;
                try {
                    persist(new File(new File(ctx.getExternalFilesDir(null), "server"), GEN_FILE));
                } catch (Throwable t) {
                    log("persist guest baru gagal: " + t);
                }
            }
            String tpl = template(ctx, "qsdk/v1/user/registerVisitor.template");
            if (tpl == null) {
                return null;
            }
            return session(ctx, tpl, g.uid, g.name, true);
        }
    }

    /**
     * registerUser 1.0.4/1.0.5 — DATABASE + validasi; email duplikat juga
     * ditolak (nama baris akun ter-bind = email).
     */
    public static String registerUserJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String name = cleanName(valueFromEnvelope(body, "username"));
            String pass = rawOrNull(valueFromEnvelope(body, "password"));
            if (name == null || pass == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            if (accountBy(name) != null || accountByEmail(name) != null) {
                return errorJsonCtx(ctx, ERR_REG_ID, ERR_REG_MSG);
            }
            Acct a = new Acct(name, md5(pass + salt), uniqueUid(), false, "", false);
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
     * loginByName 1.0.5 — DATABASE + validasi; kini menerima NAMA ATAU EMAIL
     * (konsekuensi natural bindMail) dan MENOLAK akun trash.
     */
    public static String loginByNameJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String name = cleanName(valueFromEnvelope(body, "username"));
            String pass = rawOrNull(valueFromEnvelope(body, "password"));
            if (name == null || pass == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            Acct a = accountBy(name);
            if (a == null) {
                a = accountByEmail(name);
            }
            if (a == null || a.guest || !md5(pass + salt).equals(a.pass)) {
                return errorJsonCtx(ctx, ERR_LOGIN_ID, ERR_LOGIN_MSG);
            }
            if (a.trash) {
                return errorJsonCtx(ctx, ERR_TRASH_ID, ERR_TRASH_MSG);
            }
            String tpl = template(ctx, "qsdk/v1/user/loginByName.template");
            if (tpl == null) {
                return null;
            }
            return session(ctx, tpl, a.uid, a.name, false);
        }
    }

    /**
     * autoLogin — Opsi A; akun sesi yang TRASH ditolak (1.0.5).
     */
    public static String autoLoginJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String incoming = rawOrNull(valueFromEnvelope(body, "authToken"));
            boolean ok = incoming != null && !incoming.isEmpty()
                    && !lastAuthToken.isEmpty()
                    && lastAuthToken.equals(incoming);
            if (ok) {
                Acct a = accountByUid(lastUid);
                if (a != null && a.trash) {
                    ok = false;
                }
            }
            if (!ok) {
                String etpl = template(ctx, "qsdk/v1/user/autoLogin.error.template");
                if (etpl != null) {
                    return etpl
                            .replace("{{ERROR_ID}}", ERR_AUTO_ID)
                            .replace("{{ERROR_MESSAGE}}", DbJson.esc(ERR_AUTO_MSG));
                }
                return errorJson(ERR_AUTO_ID, ERR_AUTO_MSG);
            }
            String tpl = template(ctx, "qsdk/v1/user/autoLogin.template");
            if (tpl == null) {
                return null;
            }
            String n = lastUsername.trim().isEmpty() ? username : lastUsername.trim();
            String u = lastUid.trim().isEmpty() ? uid : lastUid.trim();
            return session(ctx, tpl, u, n, lastIsGuest);
        }
    }

    /**
     * getUserInfo — params {authToken, uid}; akun trash dilaporkan apa adanya.
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

    // ================= 1.0.5-A: AKUN & KEAMANAN (8 endpoint, kontrak dex) =================

    /**
     * sendCodeByEmail {email, sendType} — kode 6 digit, TTL 10 menit, satu
     * pakai. "Email keluar" lokal = outbox.txt + log (SMTP tidak ada di lokal).
     * Respons: result + data OBJEK (dex: getJSONObject("data")).
     */
    public static String sendCodeByEmailJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String email = cleanName(valueFromEnvelope(body, "email"));
            String sendType = rawOrNull(valueFromEnvelope(body, "sendType"));
            if (email == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            String code = String.format("%06d", new Random().nextInt(1000000));
            CODES.add(new CodeRow(email, code, sendType == null ? "" : sendType,
                    System.currentTimeMillis(), false));
            while (CODES.size() > 200) {
                CODES.remove(0);
            }
            persistCodes(codesFile(ctx));
            appendOutbox(outboxFile(ctx), email, code, sendType);
            return okDataObj(ctx, "{\"status\":1}");
        }
    }

    /**
     * findPassByEmail {email, code, newPassword} — kode sah → password baru +
     * SESI REFRESH (dex: getString("data") → parser sesi → SDK ganti sesi).
     */
    public static String findPassByEmailJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String email = cleanName(valueFromEnvelope(body, "email"));
            String code = rawOrNull(valueFromEnvelope(body, "code"));
            String newPass = rawOrNull(valueFromEnvelope(body, "newPassword"));
            if (email == null || code == null || newPass == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            Acct a = accountByEmail(email);
            if (a == null) {
                a = accountBy(email);
            }
            if (a == null || a.guest) {
                return errorJsonCtx(ctx, ERR_CODE_ID, ERR_CODE_MSG);
            }
            if (!consumeCode(email, code)) {
                return errorJsonCtx(ctx, ERR_CODE_ID, ERR_CODE_MSG);
            }
            a.pass = md5(newPass + salt);
            persistAccounts(accountsFile(ctx));
            String tpl = template(ctx, "qsdk/v1/user/findPassByEmail.template");
            if (tpl == null) {
                return null;
            }
            return session(ctx, tpl, a.uid, a.name, false);
        }
    }

    /**
     * changePassword {uid, oldPassword, newPassword} — verifikasi password
     * lama (dex: hanya result dibaca).
     */
    public static String changePasswordJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String u = rawOrNull(valueFromEnvelope(body, "uid"));
            String oldPass = rawOrNull(valueFromEnvelope(body, "oldPassword"));
            String newPass = rawOrNull(valueFromEnvelope(body, "newPassword"));
            if (u == null || oldPass == null || newPass == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            Acct a = accountByUid(u);
            if (a == null || a.guest) {
                return errorJsonCtx(ctx, ERR_PASS_ID, ERR_PASS_MSG);
            }
            if (!md5(oldPass + salt).equals(a.pass)) {
                return errorJsonCtx(ctx, ERR_PASS_ID, ERR_PASS_MSG);
            }
            a.pass = md5(newPass + salt);
            persistAccounts(accountsFile(ctx));
            return okDataObj(ctx, "{\"status\":1}");
        }
    }

    /**
     * bindMail {uid, email, code, password} — identitas login akun MENJADI
     * email (pola akun asli), isGuest → 0; SESI REFRESH (dex: parser sesi,
     * callback 12). Kode wajib sah utk email itu.
     */
    public static String bindMailJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String u = rawOrNull(valueFromEnvelope(body, "uid"));
            String email = cleanName(valueFromEnvelope(body, "email"));
            String code = rawOrNull(valueFromEnvelope(body, "code"));
            String pass = rawOrNull(valueFromEnvelope(body, "password"));
            if (u == null || email == null || code == null || pass == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            Acct a = accountByUid(u);
            if (a == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            if (accountBy(email) != null || (accountByEmail(email) != null
                    && !email.equalsIgnoreCase(a.email))) {
                return errorJsonCtx(ctx, ERR_MAIL_ID, ERR_MAIL_MSG);
            }
            if (!consumeCode(email, code)) {
                return errorJsonCtx(ctx, ERR_CODE_ID, ERR_CODE_MSG);
            }
            a.name = email;
            a.email = email;
            a.pass = md5(pass + salt);
            a.guest = false;
            persistAccounts(accountsFile(ctx));
            String tpl = template(ctx, "qsdk/v1/user/bindMail.template");
            if (tpl == null) {
                return null;
            }
            return session(ctx, tpl, a.uid, a.name, false);
        }
    }

    /**
     * viewMigCode {uid} — kode migrasi akun antar perangkat; dipakai ulang
     * selama belum terpakai. data OBJEK (dex: getJSONObject("data")).
     */
    public static String viewMigCodeJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String u = rawOrNull(valueFromEnvelope(body, "uid"));
            if (u == null || accountByUid(u) == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            String existing = null;
            for (int i = 0; i < MIGS.size(); i++) {
                MigRow m = MIGS.get(i);
                if (m.uid.equals(u) && !m.used) {
                    existing = m.code;
                    break;
                }
            }
            String code = existing;
            if (code == null) {
                code = "DB" + md5(u + "|" + System.nanoTime() + "|" + salt)
                        .substring(0, 8).toUpperCase();
                MIGS.add(new MigRow(code, u, System.currentTimeMillis(), false));
                persistMigs(migFile(ctx));
            }
            return okDataObj(ctx, "{\"migCode\":\"" + DbJson.esc(code) + "\"}");
        }
    }

    /**
     * cUserTrash {uid, tStatus} — tStatus=1 trash (dex: TrashAccountActivity),
     * tStatus=2 pulihkan (dex: AccountRecoverActivity "恢复"). result saja.
     */
    public static String cUserTrashJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String u = rawOrNull(valueFromEnvelope(body, "uid"));
            String st = rawOrNull(valueFromEnvelope(body, "tStatus"));
            if (u == null || st == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            Acct a = accountByUid(u);
            if (a == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            if ("1".equals(st)) {
                a.trash = true;
            } else if ("2".equals(st)) {
                a.trash = false;
            } else {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            persistAccounts(accountsFile(ctx));
            return okDataObj(ctx, "{\"status\":" + ("1".equals(st) ? 1 : 0) + "}");
        }
    }

    /**
     * unBindOtherInfo {uid, openType, userOpenId} — hapus baris binding.
     * data OBJEK (dex: getJSONObject("data")).
     */
    public static String unBindOtherInfoJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String u = rawOrNull(valueFromEnvelope(body, "uid"));
            String openType = rawOrNull(valueFromEnvelope(body, "openType"));
            String openId = rawOrNull(valueFromEnvelope(body, "userOpenId"));
            if (u == null || openType == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            boolean removed = false;
            for (int i = BINDINGS.size() - 1; i >= 0; i--) {
                BindRow b = BINDINGS.get(i);
                if (b.uid.equals(u) && b.openType.equals(openType)
                        && (openId == null || openId.isEmpty()
                        || b.userOpenId.equals(openId))) {
                    BINDINGS.remove(i);
                    removed = true;
                }
            }
            if (removed) {
                persistBindings(bindingsFile(ctx));
            }
            return okDataObj(ctx, "{\"status\":" + (removed ? 1 : 0) + "}");
        }
    }

    /**
     * userBindOtherSdk {uid, openType, userOpenId, otherAccountName,
     * access_token, appToken} — catat binding pihak ketiga (identitas lokal).
     * data OBJEK (dex: getJSONObject("data")).
     */
    public static String userBindOtherSdkJson(Context ctx, byte[] body) {
        synchronized (LOCK) {
            String u = rawOrNull(valueFromEnvelope(body, "uid"));
            String openType = rawOrNull(valueFromEnvelope(body, "openType"));
            String openId = rawOrNull(valueFromEnvelope(body, "userOpenId"));
            String otherName = rawOrNull(valueFromEnvelope(body, "otherAccountName"));
            if (u == null || openType == null || openId == null) {
                return errorJsonCtx(ctx, ERR_DATA_ID, ERR_DATA_MSG);
            }
            for (int i = 0; i < BINDINGS.size(); i++) {
                BindRow b = BINDINGS.get(i);
                if (b.uid.equals(u) && b.openType.equals(openType)) {
                    BINDINGS.set(i, new BindRow(u, openType, openId,
                            otherName == null ? "" : otherName));
                    persistBindings(bindingsFile(ctx));
                    return okDataObj(ctx, "{\"status\":1}");
                }
            }
            BINDINGS.add(new BindRow(u, openType, openId,
                    otherName == null ? "" : otherName));
            persistBindings(bindingsFile(ctx));
            return okDataObj(ctx, "{\"status\":1}");
        }
    }

    /** Respons sukses generik: result + data objek + error objek 0. */
    private static String okDataObj(Context ctx, String dataObjJson) {
        return "{\"result\":true,\"data\":" + dataObjJson
                + ",\"error\":{\"id\":0,\"message\":\"\"}}";
    }

    /**
     * Jawaban gerbang loginchecknative (bentuk 8 field live — template
     * qsdk/game/loginchecknative.template). Echo uid/username dari query,
     * loginToken baru per panggilan, sign md5(uid + salt) stabil per uid.
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

    private static File serverDir(Context ctx) {
        return new File(ctx.getExternalFilesDir(null), "server");
    }

    private static File accountsFile(Context ctx) {
        return new File(serverDir(ctx), ACCOUNTS_FILE);
    }

    private static File codesFile(Context ctx) {
        return new File(serverDir(ctx), CODES_FILE);
    }

    private static File bindingsFile(Context ctx) {
        return new File(serverDir(ctx), BINDINGS_FILE);
    }

    private static File migFile(Context ctx) {
        return new File(serverDir(ctx), MIG_FILE);
    }

    private static File outboxFile(Context ctx) {
        return new File(serverDir(ctx), OUTBOX_FILE);
    }

    /** Muat accounts.txt; kolom 5-6 (email, trash) opsional — baris 1.0.4 sah. */
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
                    if (c.length < 4) {
                        log("accounts.txt baris " + (i + 1) + " rusak, dilewati");
                        continue;
                    }
                    String name = cleanName(c[0]);
                    if (name == null || c[2].trim().isEmpty()) {
                        continue;
                    }
                    String email = c.length >= 5 ? c[4].trim() : "";
                    boolean trash = c.length >= 6 && "1".equals(c[5].trim());
                    ACCOUNTS.add(new Acct(name, c[1].trim(), c[2].trim(),
                            "1".equals(c[3].trim()), email, trash));
                }
            }
        } catch (Throwable t) {
            log("loadAccounts gagal: " + t);
        }
        if (guestRow() == null) {
            // guest wajib ada; bila uid generator sudah dipakai akun lain
            // (kasus 1.0.5: guest lama di-bind jadi akun email) → identitas baru
            String gu = (accountByUid(uid) == null) ? uid : uniqueUid();
            String gn = (gu.equals(uid) && !username.isEmpty()) ? username : ("Guest" + gu);
            ACCOUNTS.add(new Acct(gn, "", gu, true));
            uid = gu;
            username = gn;
            persistAccounts(f);
        }
    }

    private static void persistAccounts(File f) {
        try {
            if (f == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# DB akun lokal (1.0.5) — name<TAB>passHash<TAB>uid<TAB>isGuest<TAB>email<TAB>trash\n");
            sb.append("# passHash = md5(password_sdk + salt); kosong = guest (tak bisa login by name)\n");
            sb.append("# email = email terikat (bindMail); trash=1 → login ditolak sampai dipulihkan\n");
            sb.append("# edit dihormati; berlaku setelah app tutup-buka\n");
            for (int i = 0; i < ACCOUNTS.size(); i++) {
                Acct a = ACCOUNTS.get(i);
                sb.append(a.name).append('\t').append(a.pass).append('\t')
                        .append(a.uid).append('\t').append(a.guest ? "1" : "0")
                        .append('\t').append(a.email == null ? "" : a.email)
                        .append('\t').append(a.trash ? "1" : "0").append('\n');
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

    // ================= DATABASE KODE / BINDING / MIG =================

    private static void loadCodes(File f) {
        CODES.clear();
        try {
            if (f.isFile()) {
                String[] lines = readFile(f).split("\n");
                for (String line : lines) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    String[] c = t.split("\t", -1);
                    if (c.length < 4) {
                        continue;
                    }
                    CODES.add(new CodeRow(c[0], c[1], c[2],
                            parseLong(c[3]), c.length >= 5 && "1".equals(c[4])));
                }
            }
        } catch (Throwable t) {
            log("loadCodes gagal: " + t);
        }
    }

    private static void persistCodes(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# kode verifikasi email (1.0.5) — email<TAB>code<TAB>sendType<TAB>createdMs<TAB>used\n");
            for (int i = 0; i < CODES.size(); i++) {
                CodeRow c = CODES.get(i);
                sb.append(c.email).append('\t').append(c.code).append('\t')
                        .append(c.sendType).append('\t').append(c.createdMs)
                        .append('\t').append(c.used ? "1" : "0").append('\n');
            }
            writeText(f, sb.toString());
        } catch (Throwable t) {
            log("persistCodes gagal: " + t);
        }
    }

    /** Kode sah = belum terpakai + umur ≤ TTL + email cocok; sah → ditandai used. */
    private static boolean consumeCode(String email, String code) {
        long now = System.currentTimeMillis();
        for (int i = CODES.size() - 1; i >= 0; i--) {
            CodeRow c = CODES.get(i);
            if (!c.used && c.email.equals(email) && c.code.equals(code)
                    && now - c.createdMs <= CODE_TTL_MS) {
                c.used = true;
                return true;
            }
        }
        return false;
    }

    private static void loadBindings(File f) {
        BINDINGS.clear();
        try {
            if (f.isFile()) {
                String[] lines = readFile(f).split("\n");
                for (String line : lines) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    String[] c = t.split("\t", -1);
                    if (c.length < 3) {
                        continue;
                    }
                    BINDINGS.add(new BindRow(c[0], c[1], c[2],
                            c.length >= 4 ? c[3] : ""));
                }
            }
        } catch (Throwable t) {
            log("loadBindings gagal: " + t);
        }
    }

    private static void persistBindings(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# binding pihak ketiga (1.0.5) — uid<TAB>openType<TAB>userOpenId<TAB>otherAccountName\n");
            for (int i = 0; i < BINDINGS.size(); i++) {
                BindRow b = BINDINGS.get(i);
                sb.append(b.uid).append('\t').append(b.openType).append('\t')
                        .append(b.userOpenId).append('\t').append(b.otherAccountName).append('\n');
            }
            writeText(f, sb.toString());
        } catch (Throwable t) {
            log("persistBindings gagal: " + t);
        }
    }

    private static void loadMigs(File f) {
        MIGS.clear();
        try {
            if (f.isFile()) {
                String[] lines = readFile(f).split("\n");
                for (String line : lines) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    String[] c = t.split("\t", -1);
                    if (c.length < 3) {
                        continue;
                    }
                    MIGS.add(new MigRow(c[0], c[1], parseLong(c[2]),
                            c.length >= 4 && "1".equals(c[3])));
                }
            }
        } catch (Throwable t) {
            log("loadMigs gagal: " + t);
        }
    }

    private static void persistMigs(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# kode migrasi akun (1.0.5) — code<TAB>uid<TAB>createdMs<TAB>used\n");
            for (int i = 0; i < MIGS.size(); i++) {
                MigRow m = MIGS.get(i);
                sb.append(m.code).append('\t').append(m.uid).append('\t')
                        .append(m.createdMs).append('\t').append(m.used ? "1" : "0").append('\n');
            }
            writeText(f, sb.toString());
        } catch (Throwable t) {
            log("persistMigs gagal: " + t);
        }
    }

    /** Jurnal "email keluar" lokal — bentuk natural pengganti SMTP. */
    private static void appendOutbox(File f, String email, String code, String sendType) {
        try {
            FileWriter w = new FileWriter(f, true);
            w.append(System.currentTimeMillis() + "\t" + email + "\t"
                    + code + "\t" + (sendType == null ? "" : sendType) + "\n");
            w.close();
            log("OUTBOX email=" + email + " kode=" + code);
        } catch (Throwable t) {
            log("appendOutbox gagal: " + t);
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static void writeText(File f, String s) {
        try {
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(s.getBytes("UTF-8"));
            out.close();
            tmp.renameTo(f);
        } catch (Throwable t) {
            log("writeText gagal: " + t);
        }
    }

    // ================= LOOKUP =================

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

    private static Acct accountByEmail(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }
        for (int i = 0; i < ACCOUNTS.size(); i++) {
            Acct a = ACCOUNTS.get(i);
            if (email.equalsIgnoreCase(a.email)) {
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
        return String.valueOf(System.nanoTime()).substring(0, 8);
    }

    /** Nama/email aman-TSV: buang \t/\n/\r, trim; null bila kosong. */
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
            Log.i("DbGen", m);
        } catch (Throwable ignored) {
        }
    }
}
