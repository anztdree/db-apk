import com.db.local.MiniServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.lang.reflect.Field;

/**
 * Uji fungsional MiniServer+DbGen 1.0.2 (kelas PERSIS yang masuk APK).
 *
 * Fase A (ext segar):
 *   1  setting bin baru (gerbang lokal) byte-exact
 *   2  init/getNotice/getAgreement/onNewToken/dmsg/ckRegVistor/getServerAgreement byte-exact
 *   3  registerVisitor → generator (uid 8-digit persisten, token baru, isGuest 1)
 *   4  loginchecknative → echo + code:0 (8 field live), sign stabil, loginToken berganti
 *   5  registerUser envelope ASLI → echo username, isGuest 0, isNewUser 1
 *   6  loginByName  envelope ASLI → echo username, isGuest 0
 *   7  getUserInfo → nickName = username generator (BUKAN identitas panen)
 *   8  404 + tolak ".."
 *   9  generator.json ter-seed di external
 * Fase B (reset via reflection, ext yang sama):
 *  10  VERSION dirusak + file statik dirusak → start() lagi → re-sync memulihkan
 *  11  uid BERTAHAN (generator.json dijaga saat re-sync)
 *  12  sign tetap sama (uid+salt sama), loginToken beda per panggilan
 */
public class TestServer2 {

    static final String MIRROR = "/home/z/my-project/work-re/build-102/mirror";
    static final String FIX = "/home/z/my-project/work-re/build-102/fixture";
    static final String EXT = FIX + "/ext";
    static int pass = 0;
    static int fail = 0;
    static String genUid = null;
    static String genUser = null;
    static String signPhaseA = null;

    public static void main(String[] args) throws Exception {
        new File(FIX + "/assets").mkdirs();
        File link = new File(FIX + "/assets/mirror");
        if (!link.exists()) {
            java.nio.file.Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(MIRROR));
        }
        rekursiHapus(new File(EXT));

        // ============ FASE A ============
        MiniServer.start(new android.content.Context(FIX + "/assets", EXT));
        waitPort(28110, 8000);
        System.out.println("--- FASE A (segar) ---");

        // 1. setting bin baru
        byte[] binBaru = read(MIRROR + "/bs/db/android/setting_BS_Android.bin");
        check("setting-bin-lokal", "GET /bs/db/android/setting_BS_Android.bin",
                "application/octet-stream", binBaru,
                get("bs/db/android/setting_BS_Android.bin"));

        // 2. statik live byte-exact
        String[][] statik = {
                {"v1/system/init", "qsdk/v1/system/init"},
                {"v1/system/getNotice", "qsdk/v1/system/getNotice"},
                {"v1/system/getAgreement", "qsdk/v1/system/getAgreement"},
                {"v1/system/onNewToken", "qsdk/v1/system/onNewToken"},
                {"v1/system/dmsg", "qsdk/v1/system/dmsg"},
                {"v1/user/ckRegVistor", "qsdk/v1/user/ckRegVistor"},
                {"v1/user/getServerAgreement", "qsdk/v1/user/getServerAgreement"},
        };
        for (String[] s : statik) {
            check("statik-" + s[0], "POST /" + s[0], "text/json", read(MIRROR + "/" + s[1]),
                    post(s[0], 1249));
        }

        // 3. registerVisitor → generator
        byte[] rv = bodyOf(post("v1/user/registerVisitor", 604));
        String rvJson = new String(rv, "UTF-8");
        genUid = DbJsonTest.flatGet(rvJson, "uid");
        genUser = DbJsonTest.flatGet(rvJson, "username");
        String rvToken = DbJsonTest.flatGet(rvJson, "token");
        boolean rvOk = headOf(post("v1/user/registerVisitor", 604))
                .startsWith("HTTP/1.1 200 OK")
                && DbJsonTest.flatGet(rvJson, "result").equals("true")
                && genUid != null && genUid.matches("\\d{8}")
                && genUser.equals("Guest" + genUid)
                && rvToken != null && rvToken.matches("[0-9a-f]{32}")
                && DbJsonTest.flatGet(rvJson, "isGuest").equals("1")
                && rvJson.contains("bindInfo") && rvJson.contains("checkRealName")
                && !rvJson.contains("{{");
        hasil("registerVisitor-generator", rvOk, rvJson);

        // 4. loginchecknative
        String q = "loginchecknative?uid=" + genUid + "&usermode=1&username=" + genUser
                + "&token=%40154%40171%40167%40";
        byte[] g1 = raw("GET /" + q + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\nConnection: close\r\n\r\n");
        String g1h = headOf(g1), g1b = new String(bodyOf(g1), "UTF-8");
        signPhaseA = DbJsonTest.flatGet(g1b, "sign");
        boolean g1Ok = g1h.startsWith("HTTP/1.1 200 OK") && g1h.contains("text/json")
                && DbJsonTest.flatGet(g1b, "code").equals("0")
                && DbJsonTest.flatGet(g1b, "sdk").equals("BSNative")
                && DbJsonTest.flatGet(g1b, "sdkType").equals("BS")
                && DbJsonTest.flatGet(g1b, "osType").equals("Android")
                && DbJsonTest.flatGet(g1b, "userId").equals(genUid)
                && DbJsonTest.flatGet(g1b, "nickName").equals(genUser)
                && DbJsonTest.flatGet(g1b, "loginToken").matches("[0-9a-f]{32}")
                && signPhaseA.matches("[0-9a-f]{32}");
        hasil("gate-echo", g1Ok, g1b);
        String lt1 = DbJsonTest.flatGet(g1b, "loginToken");

        // 4b. gate lagi: sign SAMA, loginToken BEDA
        byte[] g2 = raw("GET /" + q + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\nConnection: close\r\n\r\n");
        String g2b = new String(bodyOf(g2), "UTF-8");
        boolean g2Ok = DbJsonTest.flatGet(g2b, "sign").equals(signPhaseA)
                && !DbJsonTest.flatGet(g2b, "loginToken").equals(lt1);
        hasil("gate-sign-stabil-token-berganti", g2Ok, g2b);

        // 4c. gate tanpa username → fallback generator
        byte[] g3 = raw("GET /loginchecknative?uid=" + genUid + "&os=Android&sdk=223 HTTP/1.1\r\n"
                + "Host: 127.0.0.1:28110\r\n\r\n");
        String g3b = new String(bodyOf(g3), "UTF-8");
        hasil("gate-fallback-username",
                DbJsonTest.flatGet(g3b, "nickName").equals(genUser)
                        && DbJsonTest.flatGet(g3b, "code").equals("0"), g3b);

        // 5. registerUser envelope ASLI (dari HAR perangkat)
        String envReg = readText(FIX + "/envelopes.json");
        String bodyReg = DbJsonTest.jsonGet(envReg, "registerUser");
        byte[] ru = raw("POST /v1/user/registerUser HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + bodyReg.length() + "\r\nConnection: close\r\n\r\n" + bodyReg);
        String ruB = new String(bodyOf(ru), "UTF-8");
        boolean ruOk = DbJsonTest.flatGet(ruB, "result").equals("true")
                && DbJsonTest.flatGet(ruB, "username").equals("vifen44624@juicehour.com")
                && DbJsonTest.flatGet(ruB, "uid").equals(genUid)
                && DbJsonTest.flatGet(ruB, "isGuest").equals("0")
                && DbJsonTest.flatGet(ruB, "isNewUser").equals("1")
                && !ruB.contains("28506361"); // identitas panen TIDAK bocor
        hasil("registerUser-envelope-asli", ruOk, ruB);

        // 6. loginByName envelope ASLI
        String bodyLog = DbJsonTest.jsonGet(envReg, "loginByName");
        byte[] lb = raw("POST /v1/user/loginByName HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + bodyLog.length() + "\r\nConnection: close\r\n\r\n" + bodyLog);
        String lbB = new String(bodyOf(lb), "UTF-8");
        boolean lbOk = DbJsonTest.flatGet(lbB, "result").equals("true")
                && DbJsonTest.flatGet(lbB, "username").equals("vifen44624@juicehour.com")
                && DbJsonTest.flatGet(lbB, "uid").equals(genUid)
                && DbJsonTest.flatGet(lbB, "isGuest").equals("0")
                && !lbB.contains("28506361");
        hasil("loginByName-envelope-asli", lbOk, lbB);

        // 7. getUserInfo → nickName generator (bukan "KdddFRi")
        byte[] gi = bodyOf(post("v1/auth/getUserInfo", 1877));
        String giB = new String(gi, "UTF-8");
        hasil("getUserInfo-generator",
                DbJsonTest.flatGet(giB, "nickName").equals("Guest" + genUid)
                        && !giB.contains("KdddFRi"), giB);

        // 8. 404 + tolak ..
        check404("404-unknown", get("bsfake/tidak-ada.json"));
        check404("404-dots", get("../etc/passwd"));

        // 9. generator.json ter-seed di external
        String genFile = readText(EXT + "/server/generator.json");
        hasil("generator-json-persist",
                DbJsonTest.flatGet(genFile, "uid").equals(genUid)
                        && DbJsonTest.flatGet(genFile, "username").equals(genUser), genFile);

        // ============ FASE B: re-sync versi + state bertahan ============
        System.out.println("--- FASE B (re-sync) ---");
        // rusak: VERSION + satu file statik + generator.json TIDAK disentuh
        new java.io.FileWriter(EXT + "/server/VERSION").write("0.0.9-lama");
        new java.io.FileWriter(EXT + "/server/qsdk/v1/system/getNotice").write("RUSAK");
        resetStatics();

        // 10. re-sync memulihkan file
        byte[] noticeB = bodyOf(post("v1/system/getNotice", 604));
        hasil("resync-mulihkan-statik",
                java.util.Arrays.equals(noticeB, read(MIRROR + "/qsdk/v1/system/getNotice")),
                new String(noticeB, "UTF-8"));
        hasil("resync-versi",
                readText(EXT + "/server/VERSION").equals("1.0.2"),
                readText(EXT + "/server/VERSION"));

        // 11. uid bertahan
        byte[] rv2 = bodyOf(post("v1/user/registerVisitor", 604));
        String rv2B = new String(rv2, "UTF-8");
        hasil("uid-bertahan-sesudah-resync",
                DbJsonTest.flatGet(rv2B, "uid").equals(genUid)
                        && DbJsonTest.flatGet(rv2B, "username").equals(genUser), rv2B);

        // 12. sign stabil lintas boot, loginToken beda
        byte[] g4 = raw("GET /" + q + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\nConnection: close\r\n\r\n");
        String g4b = new String(bodyOf(g4), "UTF-8");
        hasil("sign-stabil-lintas-boot",
                DbJsonTest.flatGet(g4b, "sign").equals(signPhaseA)
                        && !DbJsonTest.flatGet(g4b, "loginToken")
                                .equals(DbJsonTest.flatGet(g2b, "loginToken")), g4b);

        System.out.println("\n================ HASIL: " + pass + " PASS, " + fail + " FAIL ================");
        System.exit(fail == 0 ? 0 : 1);
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
        for (String fn : new String[]{"loaded", "uid", "username", "salt", "authToken"}) {
            Field f = g.getDeclaredField(fn);
            f.setAccessible(true);
            if (f.getType() == boolean.class) {
                f.setBoolean(null, false);
            } else {
                f.set(null, fn.equals("salt") ? "dblocal-102" : "");
            }
        }
        MiniServer.start(new android.content.Context(FIX + "/assets", EXT));
        Thread.sleep(300);
    }

    // ---------- mini JSON utk test (nilai string top-level) ----------
    static class DbJsonTest {
        static String jsonGet(String json, String key) {
            String needle = "\"" + key + "\":";
            int i = json.indexOf(needle);
            if (i < 0) {
                return null;
            }
            int j = i + needle.length();
            while (j < json.length() && json.charAt(j) == ' ') {
                j++; // json.dump pakai ": " — lewati spasi
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

    static void check(String label, String req, String wantCT, byte[] wantBody, byte[] resp)
            throws Exception {
        String head = headOf(resp);
        byte[] body = bodyOf(resp);
        boolean ok = head.startsWith("HTTP/1.1 200 OK")
                && head.contains("Content-Type: " + wantCT)
                && java.util.Arrays.equals(body, wantBody);
        hasil(label + " (" + body.length + " B)", ok, "header=" + head);
    }

    static void check404(String label, byte[] resp) {
        String head = headOf(resp);
        hasil(label, head.startsWith("HTTP/1.1 404"), head);
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
        System.arraycopy(a, from, out, 0, out.length);
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
