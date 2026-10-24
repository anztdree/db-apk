package com.db.local;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Translasi 1:1 dari smali2/com/db/local/Db.smali (+ Db$TemplateDirHolder.smali).
 * Semua string SQL/JSON/log disalin VERBATIM dari bytecode.
 */
public final class Db {

    public static final String CHAT_URL = "http://127.0.0.1:10102";
    public static final long CODE_TTL_MS = 0x927c0L;
    private static SQLiteDatabase DB = null;
    public static final String ERR_CODE = "1006";
    public static final String ERR_CODE_MSG = "kode verifikasi salah atau kedaluwarsa";
    public static final String ERR_DATA = "1003";
    public static final String ERR_DATA_MSG = "data tidak lengkap, coba lagi";
    public static final String ERR_LOGIN = "1001";
    public static final String ERR_LOGIN_MSG = "username atau password salah";
    public static final String ERR_MAIL = "1005";
    public static final String ERR_MAIL_MSG = "email sudah terikat pada akun lain";
    public static final String ERR_PASS = "1007";
    public static final String ERR_PASS_MSG = "password lama salah";
    public static final String ERR_REG = "1002";
    public static final String ERR_REG_MSG = "username sudah terdaftar, silakan login";
    public static final String ERR_SESSION = "1004";
    public static final String ERR_SESSION_MSG = "sesi telah berakhir, silakan login kembali";
    public static final String ERR_TRASH = "1008";
    public static final String ERR_TRASH_MSG = "akun dalam status terhapus";
    private static final Object LOCK = new Object();
    private static String SALT = "dblocal-104";
    public static final String SERVER_ID = "101";
    public static final String SERVER_NAME = "DB-Local";
    public static final long SESSION_TTL_MS = 0x240c8400L;
    public static final String TAG = "DBLocal-DB";
    public static final String WORLD_URL = "http://127.0.0.1:10103";

    private Db() {
    }

    private static void DDL() {
        DB.execSQL("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY NOT NULL, v TEXT NOT NULL)");
        DB.execSQL("CREATE TABLE IF NOT EXISTS accounts(uid TEXT PRIMARY KEY NOT NULL, name TEXT UNIQUE, passHash TEXT NOT NULL DEFAULT '',isGuest INTEGER NOT NULL DEFAULT 1, email TEXT NOT NULL DEFAULT '',trash INTEGER NOT NULL DEFAULT 0, channelCode TEXT NOT NULL DEFAULT 'BSNative',headImageUrl TEXT NOT NULL DEFAULT '', nickName TEXT NOT NULL DEFAULT '',createdMs INTEGER NOT NULL, lastLoginMs INTEGER NOT NULL DEFAULT 0)");
        DB.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_name ON accounts(name)");
        DB.execSQL("CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts(email)");
        DB.execSQL("CREATE TABLE IF NOT EXISTS bindings(uid TEXT NOT NULL, openType TEXT NOT NULL, userOpenId TEXT NOT NULL DEFAULT '',otherAccountName TEXT NOT NULL DEFAULT '', PRIMARY KEY(uid, openType))");
        DB.execSQL("CREATE TABLE IF NOT EXISTS verify_codes(email TEXT NOT NULL, code TEXT NOT NULL, sendType TEXT NOT NULL DEFAULT '',createdMs INTEGER NOT NULL, used INTEGER NOT NULL DEFAULT 0)");
        DB.execSQL("CREATE TABLE IF NOT EXISTS mig_codes(code TEXT PRIMARY KEY NOT NULL, uid TEXT NOT NULL,createdMs INTEGER NOT NULL, used INTEGER NOT NULL DEFAULT 0)");
        DB.execSQL("CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY NOT NULL, uid TEXT NOT NULL, name TEXT NOT NULL DEFAULT '',isGuest INTEGER NOT NULL DEFAULT 1, channelCode TEXT NOT NULL DEFAULT 'BSNative',createdMs INTEGER NOT NULL, expiresMs INTEGER NOT NULL)");
        DB.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_uid ON sessions(uid)");
        DB.execSQL("CREATE TABLE IF NOT EXISTS server_history(uid TEXT NOT NULL, serverId TEXT NOT NULL, channelCode TEXT NOT NULL DEFAULT '',securityCode TEXT NOT NULL DEFAULT '', count INTEGER NOT NULL DEFAULT 0,lastMs INTEGER NOT NULL, PRIMARY KEY(uid, serverId))");
        DB.execSQL("CREATE TABLE IF NOT EXISTS announcements(id TEXT PRIMARY KEY NOT NULL, orderNo INTEGER NOT NULL DEFAULT 0,version TEXT NOT NULL DEFAULT '', alwaysPopup INTEGER NOT NULL DEFAULT 0,payload TEXT NOT NULL)");
    }

    public static String[] accountByEmail(String email) {
        return accountRow("SELECT uid,name,passHash,isGuest,email,trash FROM accounts WHERE email=?", email);
    }

    public static String[] accountByName(String name) {
        return accountRow("SELECT uid,name,passHash,isGuest,email,trash FROM accounts WHERE name=?", name);
    }

    public static String[] accountByUid(String uid) {
        return accountRow("SELECT uid,name,passHash,isGuest,email,trash FROM accounts WHERE uid=?", uid);
    }

    private static String[] accountRow(String sql, String arg) {
        Cursor c = null;
        try {
            c = DB.rawQuery(sql, new String[]{arg == null ? "" : arg});
            if (!c.moveToFirst()) {
                close(c);
                return null;
            }
            String[] out = new String[6];
            out[0] = c.getString(0);
            out[1] = c.getString(1);
            out[2] = c.getString(2);
            out[3] = String.valueOf(c.getInt(3));
            out[4] = c.getString(4);
            out[5] = String.valueOf(c.getInt(5));
            close(c);
            return out;
        } catch (Throwable t) {
        }
        close(c);
        return null;
    }

    private static void appendOutbox(String line) {
        try {
            File dir = TemplateDirHolder.dir();
            File f = new File(dir, "outbox.txt");
            synchronized (LOCK) {
                FileWriter w = new FileWriter(f, true);
                StringBuilder sb = new StringBuilder();
                sb.append(new Date());
                sb.append(" ");
                sb.append(line);
                sb.append("\n");
                w.write(sb.toString());
                w.close();
            }
        } catch (Throwable t) {
        }
    }

    public static String autoLogin(Map params) {
        String[] s = sessionByToken(Jx.str(params, "authToken"));
        if (s == null) {
            String t = template("qsdk/v1/user/autoLogin.error.template");
            t = t.replace("{{ERROR_ID}}", "1004");
            t = t.replace("{{ERROR_MESSAGE}}", "sesi telah berakhir, silakan login kembali");
            return t;
        }
        touchLogin(s[1]);
        String[] ns = newSession(s[1], s[2], "1".equals(s[3]), s[4]);
        String t = template("qsdk/v1/user/autoLogin.template");
        t = t.replace("{{AUTH_TOKEN}}", ns[0]);
        t = t.replace("{{UID}}", ns[1]);
        t = t.replace("{{USERNAME}}", ns[2]);
        t = t.replace("{{TOKEN}}", sign(ns[1], ns[0]));
        String ig;
        if (ns[3].equals("1")) {
            ig = "1";
        } else {
            ig = "0";
        }
        t = t.replace("{{IS_GUEST}}", ig);
        return t;
    }

    public static String bindMail(Map params) {
        String[] s = sessionByToken(Jx.str(params, "authToken"));
        String email = Jx.str(params, "email").trim();
        if (s == null) {
            return errorTemplate("1004", "sesi telah berakhir, silakan login kembali");
        }
        if (email.length() == 0) {
            return errorTemplate("1003", "data tidak lengkap, coba lagi");
        }
        String[] byEmail = accountByEmail(email);
        if (byEmail != null && !byEmail[0].equals(s[1])) {
            return errorTemplate("1005", "email sudah terikat pada akun lain");
        }
        synchronized (LOCK) {
            DB.execSQL("UPDATE accounts SET email=? WHERE uid=?", new Object[]{email, s[1]});
            DB.execSQL("INSERT OR REPLACE INTO bindings(uid,openType,userOpenId,otherAccountName) VALUES(?,?,?,?)",
                    new Object[]{s[1], "email", email, email});
        }
        return okGeneric();
    }

    public static String cUserTrash(Map params) {
        String[] s = sessionByToken(Jx.str(params, "authToken"));
        if (s == null) {
            return errorTemplate("1004", "sesi telah berakhir, silakan login kembali");
        }
        synchronized (LOCK) {
            DB.execSQL("UPDATE accounts SET trash=1 WHERE uid=?", new Object[]{s[1]});
        }
        return okGeneric();
    }

    public static String changePassword(Map params) {
        String[] s = sessionByToken(Jx.str(params, "authToken"));
        if (s == null) {
            return errorTemplate("1004", "sesi telah berakhir, silakan login kembali");
        }
        String oldPass = Jx.str(params, "oldPassword");
        String newPass = Jx.str(params, "newPassword");
        String[] a = accountByUid(s[1]);
        if (a == null) {
            return errorTemplate("1004", "sesi telah berakhir, silakan login kembali");
        }
        if (a[2].length() > 0 && !a[2].equals(hash(oldPass))) {
            return errorTemplate("2", "password lama salah");
        }
        synchronized (LOCK) {
            DB.execSQL("UPDATE accounts SET passHash=?, isGuest=0 WHERE uid=?", new Object[]{hash(newPass), a[0]});
        }
        return okGeneric();
    }

    private static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        name = name.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            if (sb.length() >= 0x40) {
                break;
            }
            char ch = name.charAt(i);
            if (ch >= 0x20 && ch != 0x7f) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static void close(Cursor c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Throwable t) {
        }
    }

    private static boolean consumeCode(String email, String code) {
        synchronized (LOCK) {
            boolean ok = false;
            Cursor c = null;
            try {
                long since = System.currentTimeMillis() - 0x927c0L;
                c = DB.rawQuery("SELECT rowid FROM verify_codes WHERE email=? AND code=? AND used=0 AND createdMs>=?",
                        new String[]{email, code, String.valueOf(since)});
                ok = c.moveToFirst();
            } catch (Throwable t) {
                ok = false;
            }
            close(c);
            if (ok) {
                DB.execSQL("UPDATE verify_codes SET used=1 WHERE email=? AND code=?", new Object[]{email, code});
            }
            return ok;
        }
    }

    public static Map decodeEnvelope(byte[] body) {
        if (body == null) {
            body = new byte[0];
        }
        String raw = Texts.utf8(body);
        Map m = Jx.parseObj(raw);
        if (m != null) {
            return m;
        }
        raw = Texts.formValue(raw, "data");
        if (raw.length() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch != '\n' && ch != '\r' && ch != ' ' && ch != '\t') {
                sb.append(ch);
            }
        }
        try {
            String json = new String(Texts.b64decode(sb.toString()), "UTF-8");
            return Jx.parseObj(json);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String err(int ret, String msg) {
        StringBuilder sb = new StringBuilder("{\"__ret\":");
        sb.append(ret);
        sb.append(",\"__msg\":");
        sb.append(Texts.quote(msg));
        sb.append("}");
        return sb.toString();
    }

    public static String errorTemplate(String id, String msg) {
        String t = template("qsdk/v1/user/error.template");
        t = t.replace("{{ERROR_ID}}", id);
        t = t.replace("{{ERROR_MESSAGE}}", msg);
        return t;
    }

    public static String findPassByEmail(Map params) {
        String email = Jx.str(params, "email").trim();
        String code = Jx.str(params, "code").trim();
        String pass = Jx.str(params, "password");
        String[] a = accountByEmail(email);
        if (a == null) {
            return errorTemplate("1003", "data tidak lengkap, coba lagi");
        }
        if (!consumeCode(email, code)) {
            return errorTemplate("1006", "kode verifikasi salah atau kedaluwarsa");
        }
        synchronized (LOCK) {
            DB.execSQL("UPDATE accounts SET passHash=? WHERE uid=?", new Object[]{hash(pass), a[0]});
        }
        return okGeneric();
    }

    public static String gate(String uid, String name) {
        String[] a;
        if (uid.length() > 0) {
            a = accountByUid(uid);
        } else {
            a = null;
        }
        if (a == null) {
            String newUid = nextUid();
            long now = System.currentTimeMillis();
            if (name.length() <= 0) {
                name = "Guest" + newUid;
            }
            synchronized (LOCK) {
                DB.execSQL("INSERT OR IGNORE INTO accounts(uid,name,passHash,isGuest,email,trash,channelCode,headImageUrl,nickName,createdMs,lastLoginMs) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{newUid, name, "", Integer.valueOf(1), "", Integer.valueOf(0), "BSNative", "", name, Long.valueOf(now), Long.valueOf(now)});
            }
            a = new String[]{newUid, name, "", "1", "", "0"};
        }
        touchLogin(a[0]);
        String[] ns = newSession(a[0], a[1], "1".equals(a[3]), "BSNative");
        String sig = sign(a[0], ns[0]);
        String t = template("qsdk/game/loginchecknative.template");
        t = t.replace("{{USERNAME}}", a[1]);
        t = t.replace("{{UID}}", a[0]);
        t = t.replace("{{LOGIN_TOKEN}}", ns[0]);
        t = t.replace("{{SIGN}}", sig);
        return t;
    }

    public static String getServerList(String uid) {
        StringBuilder hist = new StringBuilder();
        Cursor c = null;
        try {
            c = DB.rawQuery("SELECT serverId FROM server_history WHERE uid=? ORDER BY lastMs DESC LIMIT 10",
                    new String[]{uid == null ? "" : uid});
            while (c.moveToNext()) {
                if (hist.length() > 0) {
                    hist.append(',');
                }
                hist.append(Texts.quote(c.getString(0)));
            }
        } catch (Throwable t) {
        }
        close(c);
        String chatUrl = "http://127.0.0.1:10102";
        StringBuilder sb = new StringBuilder("{\"serverList\":[{\"serverId\":");
        sb.append(Texts.quote("101"));
        sb.append(",\"name\":");
        sb.append(Texts.quote("DB-Local"));
        sb.append(",\"url\":");
        sb.append(Texts.quote("http://127.0.0.1:10103"));
        sb.append(",\"port\":\"10103\",\"online\":true,\"hot\":true,\"new\":true,\"chaturl\":");
        sb.append(Texts.quote(chatUrl));
        sb.append(",\"dungeonurl\":");
        sb.append(Texts.quote(chatUrl));
        sb.append("}],\"history\":[");
        sb.append((Object) hist);
        sb.append("],\"offlineReason\":\"\"}");
        return sb.toString();
    }

    public static String getUserInfo(Map params) {
        String[] s = sessionByToken(Jx.str(params, "authToken"));
        String name;
        if (s != null) {
            name = s[2];
        } else {
            name = "Guest";
        }
        return template("qsdk/v1/auth/getUserInfo.template").replace("{{USERNAME}}", name);
    }

    private static String hash(String s) {
        return Texts.md5(String.valueOf(s) + SALT);
    }

    private static int histCount(String uid, String serverId) {
        int count = 0;
        Cursor c = null;
        try {
            c = DB.rawQuery("SELECT count FROM server_history WHERE uid=? AND serverId=?", new String[]{uid, serverId});
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
        } catch (Throwable t) {
        }
        close(c);
        return count;
    }

    public static synchronized void init(Context ctx) {
        try {
            DB = ctx.openOrCreateDatabase("dblocal.db", 0, null);
            setWal(DB);
            DDL();
            loadSalt();
            migrateLegacy(ctx);
            log("init ok salt=" + SALT);
        } catch (Throwable t) {
            log("init gagal: " + t);
        }
    }

    private static void loadSalt() {
        String s = metaGet("salt");
        if (s == null || s.length() == 0) {
            s = SALT;
            metaSet("salt", s);
        }
        SALT = s;
    }

    private static void log(String msg) {
        try {
            Log.i("DBLocal-DB", msg);
        } catch (Throwable t) {
        }
        try {
            Dbg.log(msg);
        } catch (Throwable t) {
        }
    }

    public static String loginAnnounce() {
        StringBuilder sb = new StringBuilder("{");
        Cursor c = null;
        try {
            c = DB.rawQuery("SELECT id,orderNo,payload FROM announcements ORDER BY orderNo ASC", null);
            boolean first = true;
            while (c.moveToNext()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(Texts.quote(c.getString(0)));
                sb.append(':');
                sb.append(c.getString(2));
            }
        } catch (Throwable t) {
        }
        close(c);
        sb.append('}');
        return sb.toString();
    }

    public static String loginByName(Map params) {
        String user = Jx.str(params, "username");
        String pass = Jx.str(params, "password");
        String[] a = accountByName(user);
        if (a == null || a[2].length() == 0 || !a[2].equals(hash(pass))) {
            return errorTemplate("2", "username atau password salah");
        }
        if ("1".equals(a[5])) {
            return errorTemplate("7", "akun dalam status terhapus");
        }
        touchLogin(a[0]);
        String[] ns = newSession(a[0], a[1], "1".equals(a[3]), "BSNative");
        String t = template("qsdk/v1/user/loginByName.template");
        t = t.replace("{{AUTH_TOKEN}}", ns[0]);
        t = t.replace("{{UID}}", a[0]);
        t = t.replace("{{USERNAME}}", a[1]);
        t = t.replace("{{TOKEN}}", sign(a[0], ns[0]));
        return t;
    }

    public static String loginGame(String uid, String password, String headImageUrl, String nickName, String channelCode) {
        String[] a = accountByUid(uid);
        if (a == null) {
            return err(1, "akun tidak ditemukan");
        }
        if ("1".equals(a[5])) {
            return err(1, "akun dalam status terhapus");
        }
        if (a[2].length() > 0 && password.length() > 0 && !a[2].equals(hash(password))) {
            return err(1, "username atau password salah");
        }
        touchLogin(a[0]);
        String[] ns = newSession(a[0], a[1], "1".equals(a[3]), channelCode.length() > 0 ? channelCode : "BSNative");
        String nick = nickName.length() > 0 ? nickName : a[1];
        synchronized (LOCK) {
            DB.execSQL("UPDATE accounts SET nickName=?, headImageUrl=? WHERE uid=?", new Object[]{nick, headImageUrl, a[0]});
        }
        return Jx.obj(new String[]{"userId", Texts.quote(a[0]),
                "channelCode", Texts.quote("BSNative"),
                "loginToken", Texts.quote(ns[0]),
                "nickName", Texts.quote(nick),
                "headImageUrl", Texts.quote(headImageUrl),
                "securityCode", Texts.quote(sign(a[0], ns[0]))});
    }

    private static String metaGet(String key) {
        String res = null;
        Cursor c = null;
        try {
            c = DB.rawQuery("SELECT v FROM meta WHERE k=?", new String[]{key});
            if (c.moveToFirst()) {
                res = c.getString(0);
            }
        } catch (Throwable t) {
        }
        close(c);
        return res;
    }

    private static void metaSet(String key, String val) {
        synchronized (LOCK) {
            DB.execSQL("INSERT OR REPLACE INTO meta(k,v) VALUES(?,?)", new Object[]{key, val});
        }
    }

    private static void migrateLegacy(Context ctx) {
        Cursor c = null;
        int count = 0;
        try {
            c = DB.rawQuery("SELECT COUNT(*) FROM accounts", null);
            if (c.moveToFirst()) {
                count = c.getInt(0);
            }
        } catch (Throwable t) {
        }
        close(c);
        if (count > 0) {
            return;
        }
        Map gen = Jx.parseObj(readFile(new File(new File(ctx.getFilesDir(), "mirror"), "generator.json")));
        if (gen != null) {
            String salt = Jx.str(gen, "salt");
            if (salt.length() > 0) {
                SALT = salt;
                metaSet("salt", salt);
            }
        }
        List<String[]> accounts = readTabFile(new File(new File(ctx.getFilesDir(), "mirror"), "accounts.txt"));
        synchronized (LOCK) {
            for (int i = 0; i < accounts.size(); i++) {
                String[] row = (String[]) accounts.get(i);
                if (row.length < 6) {
                    continue;
                }
                try {
                    DB.execSQL("INSERT OR IGNORE INTO accounts(uid,name,passHash,isGuest,email,trash,channelCode,headImageUrl,nickName,createdMs,lastLoginMs) VALUES(?,?,?,?,?,?, 'BSNative','',?, ?, ?)",
                            new Object[]{row[2], row[0], row[1],
                                Integer.valueOf("1".equals(row[3]) ? 1 : 0),
                                row[4],
                                Integer.valueOf("1".equals(row[5]) ? 1 : 0),
                                row[0],
                                Long.valueOf(System.currentTimeMillis()),
                                Long.valueOf(0L)});
                } catch (Throwable t) {
                }
            }
        }
        List<String[]> bindings = readTabFile(new File(new File(ctx.getFilesDir(), "mirror"), "bindings.txt"));
        synchronized (LOCK) {
            for (int i = 0; i < bindings.size(); i++) {
                String[] row = (String[]) bindings.get(i);
                if (row.length < 4) {
                    continue;
                }
                try {
                    DB.execSQL("INSERT OR IGNORE INTO bindings(uid,openType,userOpenId,otherAccountName) VALUES(?,?,?,?)",
                            new Object[]{row[0], row[1], row[2], row[3]});
                } catch (Throwable t) {
                }
            }
        }
        if (bindings.size() > 0) {
            log("migrasi legacy selesai");
        }
    }

    private static String[] newSession(String uid, String name, boolean isGuest, String channelCode) {
        String token = Texts.randHex(0x10);
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            DB.execSQL("DELETE FROM sessions WHERE uid=?", new Object[]{uid});
            DB.execSQL("INSERT INTO sessions(token,uid,name,isGuest,channelCode,createdMs,expiresMs) VALUES(?,?,?,?,?,?,?)",
                    new Object[]{token, uid, name, Integer.valueOf(isGuest ? 1 : 0), channelCode,
                        Long.valueOf(now), Long.valueOf(now + 0x240c8400L)});
        }
        return new String[]{token, uid, name, channelCode};
    }

    private static String nextUid() {
        synchronized (LOCK) {
            long v = Texts.toLong(metaGet("uid_counter"), 0x1b2da44L) + 1;
            DB.execSQL("INSERT OR REPLACE INTO meta(k,v) VALUES('uid_counter',?)", new Object[]{String.valueOf(v)});
            return String.format("%06d", new Object[]{Long.valueOf(v)});
        }
    }

    public static String okGeneric() {
        return "{\"result\":true,\"data\":[],\"error\":{\"id\":0,\"message\":\"\"}}";
    }

    private static String readFile(File f) {
        FileInputStream in;
        try {
            in = new FileInputStream(f);
        } catch (Throwable t) {
            in = null;
        }
        if (in != null) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[0x2000];
                while (true) {
                    int n = in.read(buf);
                    if (n <= 0) {
                        String s = new String(bos.toByteArray(), "UTF-8");
                        try {
                            in.close();
                        } catch (Throwable t2) {
                        }
                        return s;
                    }
                    bos.write(buf, 0, n);
                }
            } catch (Throwable t) {
            }
        }
        if (in != null) {
            try {
                in.close();
            } catch (Throwable t) {
            }
        }
        return "{}";
    }

    private static List<String[]> readTabFile(File f) {
        List<String[]> out = new ArrayList<String[]>();
        String content = readFile(f);
        if (content.length() == 0 || content.equals("{}")) {
            return out;
        }
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() != 0 && !line.startsWith("#")) {
                out.add(line.split("\t"));
            }
        }
        return out;
    }

    public static String registChat(String unused) {
        StringBuilder sb = new StringBuilder("{\"_success\":true,\"_chatServerUrl\":");
        sb.append(Texts.quote("http://127.0.0.1:10102"));
        sb.append(",\"_worldRoomId\":\"db-world\",\"_guildRoomId\":\"db-guild\",\"_teamDungeonChatRoom\":\"db-dungeon\",\"_teamChatRoom\":\"db-team\"}");
        return sb.toString();
    }

    public static String registerUser(Map params) {
        String user = cleanName(Jx.str(params, "username"));
        String pass = Jx.str(params, "password");
        if (user.length() == 0 || pass.length() == 0) {
            return errorTemplate("1003", "data tidak lengkap, coba lagi");
        }
        if (accountByName(user) != null) {
            return errorTemplate("1002", "username sudah terdaftar, silakan login");
        }
        String uid = nextUid();
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            DB.execSQL("INSERT INTO accounts(uid,name,passHash,isGuest,email,trash,channelCode,headImageUrl,nickName,createdMs,lastLoginMs) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    new Object[]{uid, user, hash(pass), Integer.valueOf(0), "", Integer.valueOf(0), "BSNative", "", user, Long.valueOf(now), Long.valueOf(now)});
        }
        String[] ns = newSession(uid, user, false, "BSNative");
        String t = template("qsdk/v1/user/registerUser.template");
        t = t.replace("{{AUTH_TOKEN}}", ns[0]);
        t = t.replace("{{UID}}", uid);
        t = t.replace("{{USERNAME}}", user);
        t = t.replace("{{TOKEN}}", sign(uid, ns[0]));
        return t;
    }

    public static String registerVisitor(Map params) {
        String token = Jx.str(params, "authToken");
        String[] s = null;
        if (token.length() > 0) {
            s = sessionByToken(token);
        }
        String uid;
        String name;
        boolean isGuest;
        if (s != null) {
            uid = s[1];
            name = s[2];
            isGuest = "1".equals(s[3]);
            touchLogin(s[1]);
        } else {
            uid = nextUid();
            name = "Guest" + uid;
            long now = System.currentTimeMillis();
            synchronized (LOCK) {
                DB.execSQL("INSERT INTO accounts(uid,name,passHash,isGuest,email,trash,channelCode,headImageUrl,nickName,createdMs,lastLoginMs) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{uid, name, "", Integer.valueOf(1), "", Integer.valueOf(0), "BSNative", "", name, Long.valueOf(now), Long.valueOf(now)});
            }
            touchLogin(uid);
            isGuest = true;
        }
        String[] ns = newSession(uid, name, isGuest, "BSNative");
        String t = template("qsdk/v1/user/registerVisitor.template");
        t = t.replace("{{AUTH_TOKEN}}", ns[0]);
        t = t.replace("{{UID}}", uid);
        t = t.replace("{{USERNAME}}", name);
        t = t.replace("{{TOKEN}}", sign(uid, ns[0]));
        return t;
    }

    public static String saveHistory(String uid, String channelCode, String serverId, String securityCode) {
        if (uid == null) {
            uid = "";
        }
        String[] a = accountByUid(uid);
        if (a == null) {
            return err(1, "akun tidak ditemukan");
        }
        long now = System.currentTimeMillis();
        int count;
        synchronized (LOCK) {
            count = histCount(uid, serverId) + 1;
            DB.execSQL("INSERT OR REPLACE INTO server_history(uid,serverId,channelCode,securityCode,count,lastMs) VALUES(?,?,?,?,?,?)",
                    new Object[]{uid, serverId, channelCode, securityCode, Integer.valueOf(count), Long.valueOf(now)});
        }
        String[] a2 = accountByUid(uid);
        String[] ns = newSession(uid, a2[1], "1".equals(a2[3]), "BSNative");
        return Jx.obj(new String[]{"loginToken", Texts.quote(ns[0]), "todayLoginCount", String.valueOf(count)});
    }

    public static String sendCodeByEmail(Map params) {
        String email = Jx.str(params, "email").trim();
        if (email.length() == 0) {
            return errorTemplate("1003", "data tidak lengkap, coba lagi");
        }
        String code = String.format("%06d", new Object[]{Integer.valueOf((int) (Math.random() * 1000000.0))});
        synchronized (LOCK) {
            DB.execSQL("INSERT INTO verify_codes(email,code,sendType,createdMs,used) VALUES(?,?,?,?,0)",
                    new Object[]{email, code, Jx.str(params, "sendType"), Long.valueOf(System.currentTimeMillis())});
        }
        appendOutbox("email=" + email + " kode=" + code);
        return okGeneric();
    }

    public static String[] sessionByToken(String token) {
        Cursor c = null;
        try {
            c = DB.rawQuery("SELECT token,uid,name,isGuest,channelCode,expiresMs FROM sessions WHERE token=?",
                    new String[]{token == null ? "" : token});
            if (!c.moveToFirst()) {
                close(c);
                return null;
            }
            if (c.getLong(5) < System.currentTimeMillis()) {
                close(c);
                return null;
            }
            String[] out = new String[5];
            out[0] = c.getString(0);
            out[1] = c.getString(1);
            out[2] = c.getString(2);
            out[3] = String.valueOf(c.getInt(3));
            out[4] = c.getString(4);
            close(c);
            return out;
        } catch (Throwable t) {
        }
        close(c);
        return null;
    }

    private static void setWal(SQLiteDatabase db) {
        try {
            close(db.rawQuery("PRAGMA journal_mode=WAL", null));
        } catch (Throwable t) {
            log("WAL skip: " + t);
        }
    }

    public static String sign(String uid, String token) {
        return Texts.md5(String.valueOf(uid) + "|" + token + "|" + SALT);
    }

    public static String template(String name) {
        try {
            File dir = Core.serverDir();
            if (dir != null) {
                File f = new File(dir, name);
                if (f.isFile()) {
                    return readFile(f);
                }
            }
        } catch (Throwable t) {
        }
        try {
            return readFile(new File(TemplateDirHolder.dir(), name));
        } catch (Throwable t) {
            return "{}";
        }
    }

    private static void touchLogin(String uid) {
        synchronized (LOCK) {
            DB.execSQL("UPDATE accounts SET lastLoginMs=? WHERE uid=?",
                    new Object[]{Long.valueOf(System.currentTimeMillis()), uid});
        }
    }

    private static boolean trash(String uid) {
        boolean res = false;
        Cursor c = null;
        try {
            c = DB.rawQuery("SELECT trash FROM accounts WHERE uid=?", new String[]{uid});
            if (c.moveToFirst() && c.getInt(0) != 0) {
                res = true;
            }
        } catch (Throwable t) {
        }
        close(c);
        return res;
    }

    public static String unBindOtherInfo(Map params) {
        String[] s = sessionByToken(Jx.str(params, "authToken"));
        if (s == null) {
            return errorTemplate("1004", "sesi telah berakhir, silakan login kembali");
        }
        String openType = Jx.str(params, "openType");
        synchronized (LOCK) {
            DB.execSQL("DELETE FROM bindings WHERE uid=? AND openType=?", new Object[]{s[1], openType});
        }
        return okGeneric();
    }

    public static String userBindOtherSdk(Map params) {
        String[] s = sessionByToken(Jx.str(params, "authToken"));
        if (s == null) {
            return errorTemplate("1004", "sesi telah berakhir, silakan login kembali");
        }
        String openType = Jx.str(params, "openType");
        String userOpenId = Jx.str(params, "userOpenId");
        String otherAccountName = Jx.str(params, "otherAccountName");
        synchronized (LOCK) {
            DB.execSQL("INSERT OR REPLACE INTO bindings(uid,openType,userOpenId,otherAccountName) VALUES(?,?,?,?)",
                    new Object[]{s[1], openType, userOpenId, otherAccountName});
        }
        return okGeneric();
    }

    public static String viewMigCode(Map params) {
        String[] s = sessionByToken(Jx.str(params, "authToken"));
        if (s == null) {
            return errorTemplate("1004", "sesi telah berakhir, silakan login kembali");
        }
        String code = Texts.randHex(4).toUpperCase();
        synchronized (LOCK) {
            DB.execSQL("INSERT INTO mig_codes(code,uid,createdMs,used) VALUES(?,?,?,0)",
                    new Object[]{code, s[1], Long.valueOf(System.currentTimeMillis())});
        }
        StringBuilder sb = new StringBuilder("{\"migCode\":");
        sb.append(Texts.quote(code));
        sb.append("}");
        return sb.toString();
    }

    public static final class TemplateDirHolder {

        private static volatile File DIR;

        public TemplateDirHolder() {
        }

        public static File dir() {
            return DIR;
        }

        public static void set(File f) {
            DIR = f;
        }
    }
}
