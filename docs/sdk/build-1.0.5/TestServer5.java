import com.db.local.MiniServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.lang.reflect.Field;
import java.security.MessageDigest;

/**
 * Uji fungsional MiniServer+DbGen 1.0.5-A — AKUN & KEAMANAN PENUH
 * (kelas PERSIS yang masuk APK).
 *
 * Mandat user: "semua fitur SDK wajib bisa di local ... natural" — tanpa
 * jaring. 8 endpoint baru diuji kontrak dex-nya (dump105/RENCANA-1.0.5.md):
 * sendCodeByEmail, findPassByEmail (seksi refresh), changePassword, bindMail
 * (identitas jadi email + sesi refresh), viewMigCode, cUserTrash (trash/restore),
 * unBindOtherInfo, userBindOtherSdk.
 *
 * Warisan yang tetap diuji: statik byte-exact, gate echo+sign per akun,
 * registerVisitor/registerUser/loginByName/autoLogin Opsi A, database akun,
 * re-sync versi menjaga DB, guest baru setelah bind.
 */
public class TestServer5 {

    static final String MIRROR = "/home/z/my-project/work-re/repo-102/docs/sdk/build-1.0.5/mirror";
    static final String FIX = "/home/z/my-project/work-re/build-105/fixture";
    static final String EXT = FIX + "/ext";
    static final String SALT = "dblocal-102";
    static int pass = 0;
    static int fail = 0;
    static String genUid = null;
    static String genUser = null;
    static String vifenUid = null;
    static String vifenName = "vifen44624@juicehour.com";
    static String vifenPass = "22dc0c1e8cb89b5d906baf15a8fe3fbb"; // md5 klien (panen)
    static String vifenNewPass = "e10adc3949ba59abbe56e057f20f883e"; // md5("123456") HAR #28
    static String bindEmail = "player01@local.test";
    static String bindPass = "5f4dcc3b5aa765d61d8327deb882cf99";  // md5("password")
    static String migCode = null;
    static String afterFindPassToken = null;

    public static void main(String[] args) throws Exception {
        new File(FIX + "/assets").mkdirs();
        File link = new File(FIX + "/assets/mirror");
        if (!link.exists()) {
            java.nio.file.Files.createSymbolicLink(link.toPath(),
                    java.nio.file.Paths.get(MIRROR));
        }
        rekursiHapus(new File(EXT));

        // ============ FASE A (segar) ============
        MiniServer.start(new android.content.Context(FIX + "/assets", EXT));
        waitPort(28110, 8000);
        System.out.println("--- FASE A (segar) ---");

        // 1. setting bin + statik live byte-exact (warisan)
        byte[] binBaru = read(MIRROR + "/bs/db/android/setting_BS_Android.bin");
        check("setting-bin-lokal", "application/octet-stream", binBaru,
                get("bs/db/android/setting_BS_Android.bin"));
        check("statik-init", "text/json", read(MIRROR + "/qsdk/v1/system/init"),
                post("v1/system/init", 1249));

        // 2. registerVisitor → guest
        String rvJson = new String(bodyOf(post("v1/user/registerVisitor", 604)), "UTF-8");
        genUid = DbJsonTest.flatGet(rvJson, "uid");
        genUser = DbJsonTest.flatGet(rvJson, "username");
        hasil("registerVisitor-guest", genUid != null && genUid.matches("\\d{8}")
                && genUser.equals("Guest" + genUid)
                && DbJsonTest.flatGet(rvJson, "isGuest").equals("1"), rvJson);

        // 3. gate guest
        String g1b = gate(genUid, genUser, "1");
        hasil("gate-guest-echo-sign", DbJsonTest.flatGet(g1b, "code").equals("0")
                && DbJsonTest.flatGet(g1b, "userId").equals(genUid)
                && DbJsonTest.flatGet(g1b, "sign").equals(md5(genUid + SALT)), g1b);

        // 4. registerUser envelope asli panen → akun vifen
        String bodyReg = DbJsonTest.jsonGet(readText(FIX + "/envelopes.json"), "registerUser");
        String ruB = postJson("v1/user/registerUser", bodyReg);
        vifenUid = DbJsonTest.flatGet(ruB, "uid");
        hasil("registerUser-akun-uid-per-akun",
                DbJsonTest.flatGet(ruB, "result").equals("true")
                        && vifenUid != null && vifenUid.matches("\\d{8}")
                        && !vifenUid.equals(genUid)
                        && DbJsonTest.flatGet(ruB, "isGuest").equals("0"), ruB);

        // 5. loginByName password SALAH → result:false
        String bad1 = postJson("v1/user/loginByName", env("{\"username\":\"" + vifenName
                + "\",\"password\":\"00000000000000000000000000000000\"}"));
        hasil("login-password-salah-ditolak",
                DbJsonTest.flatGet(bad1, "result").equals("false")
                        && "2".equals(DbJsonTest.flatGet(bad1, "id")), bad1);

        // 6. loginByName cocok → sesi akun
        String ok1 = postJson("v1/user/loginByName", env("{\"username\":\"" + vifenName
                + "\",\"password\":\"" + vifenPass + "\"}"));
        hasil("login-cocok-sesi-akun",
                DbJsonTest.flatGet(ok1, "result").equals("true")
                        && vifenUid.equals(DbJsonTest.flatGet(ok1, "uid")), ok1);

        // ====== 1.0.5-A: EMAIL & PASSWORD ======

        // 7. sendCodeByEmail → data OBJEK + kode masuk outbox.txt (email lokal).
        //    Email akun = username-asli vifen (pola akun asli: username = email).
        String sc = postJson("v1/user/sendCodeByEmail", env("{\"email\":\"" + vifenName
                + "\",\"sendType\":\"1\"}"));
        hasil("sendcode-data-objek",
                DbJsonTest.flatGet(sc, "result").equals("true")
                        && DbJsonTest.flatGet(sc, "status").equals("1")
                        && DbJsonTest.flatGet(sc, "id").equals("0"), sc);
        String outbox = readText(EXT + "/server/outbox.txt");
        String kode1 = kodeTerakhir(outbox, vifenName);
        hasil("sendcode-outbox-berisi-kode",
                kode1 != null && kode1.matches("\\d{6}"), outbox);

        // 8. findPassByEmail kode SALAH → result:false (id 5)
        String fpBad = postJson("v1/user/findPassByEmail", env("{\"email\":\"" + bindEmail
                + "\",\"code\":\"000000\",\"newPassword\":\"" + vifenNewPass + "\"}"));
        hasil("findpass-kode-salah-ditolak",
                DbJsonTest.flatGet(fpBad, "result").equals("false")
                        && "5".equals(DbJsonTest.flatGet(fpBad, "id")), fpBad);

        // 9. findPassByEmail kode SAH → SESI REFRESH (data authToken+userData)
        String fp = postJson("v1/user/findPassByEmail", env("{\"email\":\"" + vifenName
                + "\",\"code\":\"" + kode1 + "\",\"newPassword\":\"" + vifenNewPass + "\"}"));
        afterFindPassToken = DbJsonTest.flatGet(fp, "authToken");
        hasil("findpass-sesi-refresh",
                DbJsonTest.flatGet(fp, "result").equals("true")
                        && afterFindPassToken != null
                        && vifenName.equals(DbJsonTest.flatGet(fp, "username"))
                        && DbJsonTest.flatGet(fp, "isGuest").equals("0")
                        && fp.contains("bindEmail"), fp);

        // 10. kode terpakai → tak bisa dipakai lagi
        String fpRe = postJson("v1/user/findPassByEmail", env("{\"email\":\"" + vifenName
                + "\",\"code\":\"" + kode1 + "\",\"newPassword\":\"" + vifenPass + "\"}"));
        hasil("findpass-kode-sekali-pakai",
                DbJsonTest.flatGet(fpRe, "result").equals("false"), fpRe);

        // 11. autoLogin token BARU hasil reset → OK (rotasi tersimpan)
        String al = postJson("v1/user/autoLogin", env("{\"authToken\":\""
                + afterFindPassToken + "\"}"));
        hasil("autologin-token-rotasi",
                DbJsonTest.flatGet(al, "result").equals("true")
                        && vifenUid.equals(DbJsonTest.flatGet(al, "uid")), al);

        // 12. changePassword: lama salah → id 8
        String cpBad = postJson("v1/auth/changePassword", env("{\"uid\":\"" + vifenUid
                + "\",\"oldPassword\":\"00000000000000000000000000000000\","
                + "\"newPassword\":\"" + bindPass + "\"}"));
        hasil("changepassword-lama-salah",
                DbJsonTest.flatGet(cpBad, "result").equals("false")
                        && "8".equals(DbJsonTest.flatGet(cpBad, "id")), cpBad);

        // 13. changePassword: sah → login dgn password BARU ok, LAMA ditolak
        String cp = postJson("v1/auth/changePassword", env("{\"uid\":\"" + vifenUid
                + "\",\"oldPassword\":\"" + vifenNewPass + "\","
                + "\"newPassword\":\"" + bindPass + "\"}"));
        String loginBaru = postJson("v1/user/loginByName", env("{\"username\":\"" + vifenName
                + "\",\"password\":\"" + bindPass + "\"}"));
        String loginLama = postJson("v1/user/loginByName", env("{\"username\":\"" + vifenName
                + "\",\"password\":\"" + vifenNewPass + "\"}"));
        hasil("changepassword-berlaku",
                DbJsonTest.flatGet(cp, "result").equals("true")
                        && DbJsonTest.flatGet(loginBaru, "result").equals("true")
                        && DbJsonTest.flatGet(loginLama, "result").equals("false"), cp + " | " + loginLama);

        // ====== 1.0.5-A: BIND MAIL (guest → akun email) ======

        // 14. bindMail tanpa kode sah → id 5
        String bmBad = postJson("v1/auth/bindMail", env("{\"uid\":\"" + genUid
                + "\",\"email\":\"" + bindEmail + "\",\"code\":\"999999\","
                + "\"password\":\"" + bindPass + "\"}"));
        hasil("bindmail-kode-salah",
                DbJsonTest.flatGet(bmBad, "result").equals("false")
                        && "5".equals(DbJsonTest.flatGet(bmBad, "id")), bmBad);

        // 15. bindMail email yang sudah terikat (vifen) → id 6
        String sc2 = postJson("v1/user/sendCodeByEmail", env("{\"email\":\"" + vifenName
                + "\",\"sendType\":\"1\"}"));
        String kodeVifen = kodeTerakhir(readText(EXT + "/server/outbox.txt"), vifenName);
        String bmDup = postJson("v1/auth/bindMail", env("{\"uid\":\"" + genUid
                + "\",\"email\":\"" + vifenName + "\",\"code\":\"" + kodeVifen + "\","
                + "\"password\":\"" + bindPass + "\"}"));
        hasil("bindmail-email-duplikat-ditolak",
                DbJsonTest.flatGet(bmDup, "result").equals("false")
                        && "6".equals(DbJsonTest.flatGet(bmDup, "id")), bmDup);

        // 16. bindMail guest dgn kode sah → identitas jadi email, isGuest 0
        postJson("v1/user/sendCodeByEmail", env("{\"email\":\"" + bindEmail
                + "\",\"sendType\":\"1\"}"));
        String kodeGuest = kodeTerakhir(readText(EXT + "/server/outbox.txt"), bindEmail);
        hasil("sendcode-kedua-untuk-guest", kodeGuest != null
                && !kodeGuest.equals(kode1), "kode=" + kodeGuest);
        String bm = postJson("v1/auth/bindMail", env("{\"uid\":\"" + genUid
                + "\",\"email\":\"" + bindEmail + "\",\"code\":\"" + kodeGuest + "\","
                + "\"password\":\"" + bindPass + "\"}"));
        hasil("bindmail-guest-jadi-akun-email",
                DbJsonTest.flatGet(bm, "result").equals("true")
                        && bindEmail.equals(DbJsonTest.flatGet(bm, "username"))
                        && genUid.equals(DbJsonTest.flatGet(bm, "uid"))
                        && DbJsonTest.flatGet(bm, "isGuest").equals("0")
                        && bm.contains("\"isBind\":1"), bm);

        // 17. login by EMAIL (identitas baru) → sukses
        String loginEmail = postJson("v1/user/loginByName", env("{\"username\":\"" + bindEmail
                + "\",\"password\":\"" + bindPass + "\"}"));
        hasil("login-by-email-setelah-bind",
                DbJsonTest.flatGet(loginEmail, "result").equals("true")
                        && genUid.equals(DbJsonTest.flatGet(loginEmail, "uid")), loginEmail);

        // 18. guest BARU otomatis (uid baru ≠ uid ter-bind)
        String rv3 = new String(bodyOf(post("v1/user/registerVisitor", 604)), "UTF-8");
        String genUid2 = DbJsonTest.flatGet(rv3, "uid");
        hasil("guest-baru-setelah-bind",
                genUid2 != null && genUid2.matches("\\d{8}")
                        && !genUid2.equals(genUid)
                        && DbJsonTest.flatGet(rv3, "isGuest").equals("1"), rv3);

        // ====== 1.0.5-A: MIG CODE ======
        String vm = postJson("v1/auth/viewMigCode", env("{\"uid\":\"" + vifenUid + "\"}"));
        migCode = DbJsonTest.flatGet(vm, "migCode");
        String vm2 = postJson("v1/auth/viewMigCode", env("{\"uid\":\"" + vifenUid + "\"}"));
        hasil("viewmigcode-format-dan-reuse",
                migCode != null && migCode.matches("DB[0-9A-F]{8}")
                        && migCode.equals(DbJsonTest.flatGet(vm2, "migCode"))
                        && new File(EXT + "/server/migcodes.txt").isFile(), vm + " | " + vm2);

        // ====== 1.0.5-A: TRASH / RESTORE ======
        // (sesi segar dulu — token lama sudah dirotasi oleh sesi-sesi di atas)
        String loginPre = postJson("v1/user/loginByName", env("{\"username\":\"" + vifenName
                + "\",\"password\":\"" + bindPass + "\"}"));
        String freshTok = DbJsonTest.flatGet(loginPre, "authToken");
        String trash = postJson("v1/auth/cUserTrash", env("{\"uid\":\"" + vifenUid
                + "\",\"tStatus\":\"1\"}"));
        String loginTrash = postJson("v1/user/loginByName", env("{\"username\":\"" + vifenName
                + "\",\"password\":\"" + bindPass + "\"}"));
        String alTrash = postJson("v1/user/autoLogin", env("{\"authToken\":\""
                + freshTok + "\"}"));
        hasil("cusertrash-login-ditolak",
                DbJsonTest.flatGet(trash, "result").equals("true")
                        && DbJsonTest.flatGet(loginTrash, "result").equals("false")
                        && "7".equals(DbJsonTest.flatGet(loginTrash, "id"))
                        && DbJsonTest.flatGet(alTrash, "result").equals("false"),
                trash + " | " + loginTrash + " | " + alTrash);

        String restore = postJson("v1/auth/cUserTrash", env("{\"uid\":\"" + vifenUid
                + "\",\"tStatus\":\"2\"}"));
        String loginRestore = postJson("v1/user/loginByName", env("{\"username\":\"" + vifenName
                + "\",\"password\":\"" + bindPass + "\"}"));
        hasil("cusertrash-restore-login-lagi",
                DbJsonTest.flatGet(restore, "result").equals("true")
                        && DbJsonTest.flatGet(loginRestore, "result").equals("true"), loginRestore);

        // ====== 1.0.5-A: BINDING PIHAK KETIGA ======
        String ub = postJson("v1/auth/userBindOtherSdk", env("{\"uid\":\"" + vifenUid
                + "\",\"openType\":\"8\",\"userOpenId\":\"google-123\","
                + "\"otherAccountName\":\"player@gmail.com\","
                + "\"access_token\":\"tok\",\"appToken\":\"app\"}"));
        String bindFile = readText(EXT + "/server/bindings.txt");
        hasil("userbindothersdk-tercatat",
                DbJsonTest.flatGet(ub, "result").equals("true")
                        && DbJsonTest.flatGet(ub, "status").equals("1")
                        && bindFile.contains(vifenUid + "\t8\tgoogle-123\tplayer@gmail.com"), ub);

        String ub2 = postJson("v1/auth/unBindOtherInfo", env("{\"uid\":\"" + vifenUid
                + "\",\"openType\":\"8\",\"userOpenId\":\"google-123\"}"));
        String bindFile2 = readText(EXT + "/server/bindings.txt");
        String ub3 = postJson("v1/auth/unBindOtherInfo", env("{\"uid\":\"" + vifenUid
                + "\",\"openType\":\"8\",\"userOpenId\":\"google-123\"}"));
        hasil("unbindothersdk-hapus-berulang-nol",
                DbJsonTest.flatGet(ub2, "status").equals("1")
                        && !bindFile2.contains("google-123")
                        && DbJsonTest.flatGet(ub3, "status").equals("0"), ub2 + " | " + ub3);

        // ============ FASE B (re-sync penuh + lintas boot) ============
        System.out.println("--- FASE B (re-sync + boot ulang) ---");
        resetStatics();

        // 19. DB akun + sesi bertahan
        String loginB = postJson("v1/user/loginByName", env("{\"username\":\"" + vifenName
                + "\",\"password\":\"" + bindPass + "\"}"));
        hasil("resync-akun-bertahan",
                DbJsonTest.flatGet(loginB, "result").equals("true")
                        && vifenUid.equals(DbJsonTest.flatGet(loginB, "uid")), loginB);

        // 20. autoLogin lintas boot — SESI TERAKHIR (belum dirotasi uji lain)
        String alB = postJson("v1/user/autoLogin", env("{\"authToken\":\""
                + DbJsonTest.flatGet(loginB, "authToken") + "\"}"));
        hasil("autologin-lintas-boot",
                DbJsonTest.flatGet(alB, "result").equals("true")
                        && vifenUid.equals(DbJsonTest.flatGet(alB, "uid")), alB);

        // 20. kode email + outbox + mig + binding bertahan
        hasil("resync-db-tambahan-bertahan",
                new File(EXT + "/server/codes.txt").isFile()
                        && new File(EXT + "/server/outbox.txt").isFile()
                        && new File(EXT + "/server/migcodes.txt").isFile()
                        && new File(EXT + "/server/bindings.txt").isFile()
                        && readText(EXT + "/server/outbox.txt").contains(bindEmail),
                "db files");

        String vm3 = postJson("v1/auth/viewMigCode", env("{\"uid\":\"" + vifenUid + "\"}"));
        hasil("resync-migcode-reuse-lintas-boot",
                migCode.equals(DbJsonTest.flatGet(vm3, "migCode")), vm3);

        // 21. guest email akun + guest baru bertahan
        String loginEmailB = postJson("v1/user/loginByName", env("{\"username\":\"" + bindEmail
                + "\",\"password\":\"" + bindPass + "\"}"));
        hasil("resync-akun-email-bertahan",
                DbJsonTest.flatGet(loginEmailB, "result").equals("true")
                        && genUid.equals(DbJsonTest.flatGet(loginEmailB, "uid")), loginEmailB);

        // 22. gate per akun tetap
        String g2b = gate(vifenUid, vifenName, "0");
        hasil("gate-per-akun-lintas-boot",
                DbJsonTest.flatGet(g2b, "code").equals("0")
                        && DbJsonTest.flatGet(g2b, "userId").equals(vifenUid)
                        && DbJsonTest.flatGet(g2b, "sign").equals(md5(vifenUid + SALT)), g2b);

        System.out.println("\n================ HASIL: " + pass + " PASS, " + fail + " FAIL ================");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ---------- gate ----------
    static String gate(String uid, String user, String mode) throws Exception {
        String q = "loginchecknative?uid=" + uid + "&usermode=" + mode + "&username=" + user
                + "&token=%40154%40171%40167%40";
        return new String(bodyOf(raw("GET /" + q + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Connection: close\r\n\r\n")), "UTF-8");
    }

    // ---------- kode terakhir utk email dari outbox.txt ----------
    static String kodeTerakhir(String outbox, String email) {
        String[] lines = outbox.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String[] c = lines[i].trim().split("\t");
            if (c.length >= 3 && c[1].equals(email)) {
                return c[2];
            }
        }
        return null;
    }

    // ---------- envelope builder (gaya SDK: b64 wrap CRLF 76, urlencoded) ----------
    static String env(String json) throws Exception {
        String b64 = java.util.Base64.getEncoder().encodeToString(json.getBytes("UTF-8"));
        StringBuilder w = new StringBuilder();
        for (int i = 0; i < b64.length(); i += 76) {
            if (i > 0) {
                w.append("\r\n");
            }
            w.append(b64, i, Math.min(b64.length(), i + 76));
        }
        return "sv=v2&data=" + java.net.URLEncoder.encode(w.toString(), "UTF-8")
                + "&sign=" + repeat("0", 30);
    }

    static String postJson(String path, String body) throws Exception {
        byte[] resp = raw("POST /" + path + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body);
        return new String(bodyOf(resp), "UTF-8");
    }

    // ---------- reset statics via reflection (hanya utk test) ----------
    static void resetStatics() throws Exception {
        Field f1 = MiniServer.class.getDeclaredField("started");
        f1.setAccessible(true);
        f1.setBoolean(null, false);
        Field f2 = MiniServer.class.getDeclaredField("APP");
        f2.setAccessible(true);
        f2.set(null, null);
        Class<?> g = Class.forName("com.db.local.DbGen");
        for (String fn : new String[]{"loaded", "uid", "username", "salt", "lastAuthToken",
                "lastUid", "lastUsername"}) {
            Field f = g.getDeclaredField(fn);
            f.setAccessible(true);
            if (f.getType() == boolean.class) {
                f.setBoolean(null, false);
            } else {
                f.set(null, fn.equals("salt") ? "dblocal-102" : "");
            }
        }
        Field fg = g.getDeclaredField("lastIsGuest");
        fg.setAccessible(true);
        fg.setBoolean(null, true);
        for (String ln : new String[]{"ACCOUNTS", "CODES", "BINDINGS", "MIGS"}) {
            Field fl = g.getDeclaredField(ln);
            fl.setAccessible(true);
            ((java.util.List<?>) fl.get(null)).clear();
        }
        MiniServer.start(new android.content.Context(FIX + "/assets", EXT));
        Thread.sleep(300);
    }

    // ---------- md5 (uji recompute) ----------
    static String md5(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] d = md.digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) {
            sb.append(Integer.toString((d[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    // ---------- mini JSON utk test ----------
    static class DbJsonTest {
        static String jsonGet(String json, String key) {
            String needle = "\"" + key + "\":";
            int i = json.indexOf(needle);
            if (i < 0) {
                return null;
            }
            int j = i + needle.length();
            while (j < json.length() && json.charAt(j) == ' ') {
                j++;
            }
            if (json.charAt(j) == '"') {
                int k = json.indexOf('"', j + 1);
                return json.substring(j + 1, k);
            }
            int k = j;
            while (k < json.length() && ",}] \r\n\t".indexOf(json.charAt(k)) < 0) {
                k++;
            }
            return json.substring(j, k).trim();
        }

        static String flatGet(String json, String key) {
            return jsonGet(json, key);
        }
    }

    // ---------- HTTP client minim ----------
    static byte[] post(String path, int bodyLen) throws Exception {
        String body = repeat("x", bodyLen);
        return raw("POST /" + path + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + bodyLen + "\r\nConnection: close\r\n\r\n" + body);
    }

    static byte[] get(String path) throws Exception {
        return raw("GET /" + path + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Connection: close\r\n\r\n");
    }

    static byte[] raw(String req) throws Exception {
        Socket s = new Socket("127.0.0.1", 28110);
        s.setSoTimeout(8000);
        OutputStream os = s.getOutputStream();
        os.write(req.getBytes("UTF-8"));
        os.flush();
        InputStream in = s.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        s.close();
        return bos.toByteArray();
    }

    static String headOf(byte[] resp) {
        try {
            return new String(resp, 0, resp.length, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] bodyOf(byte[] resp) {
        String head = headOf(resp);
        int sep = head.indexOf("\r\n\r\n");
        return sep >= 0 ? slice(resp, sep + 4) : new byte[0];
    }

    static void hasil(String label, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("PASS  " + label);
        } else {
            fail++;
            System.out.println("FAIL  " + label);
            System.out.println("      " + detail.substring(0, Math.min(400, detail.length())));
        }
    }

    static void check(String label, String wantCT, byte[] wantBody, byte[] resp)
            throws Exception {
        String head = headOf(resp);
        byte[] body = bodyOf(resp);
        boolean ok = head.startsWith("HTTP/1.1 200 OK")
                && head.contains("Content-Type: " + wantCT)
                && java.util.Arrays.equals(body, wantBody);
        hasil(label + " (" + body.length + " B)", ok, "header=" + head);
    }

    static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    static byte[] slice(byte[] a, int from) {
        byte[] out = new byte[a.length - from];
        System.arraycopy(a, from, out, 0, a.length - from);
        return out;
    }

    static void waitPort(int port, long ms) throws Exception {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket s = new Socket("127.0.0.1", port);
                s.close();
                return;
            } catch (Exception e) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("port " + port + " tidak buka");
    }

    static byte[] read(String p) throws Exception {
        FileInputStream fis = new FileInputStream(p);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        fis.close();
        return bos.toByteArray();
    }

    static String readText(String p) throws Exception {
        return new String(read(p), "UTF-8");
    }

    static void rekursiHapus(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] ch = f.listFiles();
        if (ch != null) {
            for (File c : ch) {
                rekursiHapus(c);
            }
        }
        f.delete();
    }
}
