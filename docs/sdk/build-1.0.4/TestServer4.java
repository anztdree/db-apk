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
 * Uji fungsional MiniServer+DbGen 1.0.4 — DATABASE AKUN + VALIDASI PASSWORD
 * (kelas PERSIS yang masuk APK).
 *
 * Keberatan user: "ini kan masuk ranah database !! password cocok login !!"
 * 1.0.3 menerima username/password APA PUN (tidak setia server asli). 1.0.4:
 *
 *   - loginByName: password SALAH / username ASING / akun guest → result:false
 *     (bentuk error live 40008) → SDK tampilkan pesan; COCOK → sesi akun itu
 *   - registerUser: duplikat ditolak; baru → baris akun + uid 8-digit unik
 *   - uid PER AKUN (bukti HAR #32), passHash = md5(password_klien + salt)
 *   - autoLogin Opsi A + lastUid; getUserInfo per sesi/akun
 *
 * Warisan yang tetap diuji (1.0.2/1.0.3): statik byte-exact, gate echo+sign,
 * re-sync versi, uid guest stabil, autoLogin lintas boot.
 */
public class TestServer4 {

    static final String MIRROR = "/home/z/my-project/work-re/repo-102/docs/sdk/build-1.0.4/mirror";
    static final String FIX = "/home/z/my-project/work-re/build-104/fixture";
    static final String EXT = FIX + "/ext";
    static final String SALT = "dblocal-102";
    static int pass = 0;
    static int fail = 0;
    static String genUid = null;    // uid GUEST (generator)
    static String genUser = null;
    static String signPhaseA = null;
    static String vifenUid = null;  // uid AKUN vifen44624 (registerUser)
    static String vifenName = "vifen44624@juicehour.com";
    static String vifenPass = "22dc0c1e8cb89b5d906baf15a8fe3fbb"; // md5 klien, envelope panen
    static String signVifen = null;

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

        // 3. registerVisitor → sesi GUEST dari database (uid stabil)
        byte[] rv = bodyOf(post("v1/user/registerVisitor", 604));
        String rvJson = new String(rv, "UTF-8");
        genUid = DbJsonTest.flatGet(rvJson, "uid");
        genUser = DbJsonTest.flatGet(rvJson, "username");
        boolean rvOk = headOf(post("v1/user/registerVisitor", 604))
                .startsWith("HTTP/1.1 200 OK")
                && DbJsonTest.flatGet(rvJson, "result").equals("true")
                && genUid != null && genUid.matches("\\d{8}")
                && genUser.equals("Guest" + genUid)
                && DbJsonTest.flatGet(rvJson, "isGuest").equals("1")
                && rvJson.contains("bindInfo") && !rvJson.contains("{{");
        hasil("registerVisitor-generator", rvOk, rvJson);

        // 4. loginchecknative (guest)
        String q = "loginchecknative?uid=" + genUid + "&usermode=1&username=" + genUser
                + "&token=%40154%40171%40167%40";
        byte[] g1 = raw("GET /" + q + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\nConnection: close\r\n\r\n");
        String g1b = new String(bodyOf(g1), "UTF-8");
        signPhaseA = DbJsonTest.flatGet(g1b, "sign");
        boolean g1Ok = headOf(g1).startsWith("HTTP/1.1 200 OK")
                && DbJsonTest.flatGet(g1b, "code").equals("0")
                && DbJsonTest.flatGet(g1b, "userId").equals(genUid)
                && DbJsonTest.flatGet(g1b, "nickName").equals(genUser)
                && DbJsonTest.flatGet(g1b, "loginToken").matches("[0-9a-f]{32}")
                && signPhaseA.equals(md5(genUid + SALT));
        hasil("gate-echo-sign-recompute", g1Ok, g1b);

        // 5. registerUser envelope ASLI panen → AKUN BARU + uid BARU per akun
        String envReg = readText(FIX + "/envelopes.json");
        String bodyReg = DbJsonTest.jsonGet(envReg, "registerUser");
        byte[] ru = raw("POST /v1/user/registerUser HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + bodyReg.length() + "\r\nConnection: close\r\n\r\n" + bodyReg);
        String ruB = new String(bodyOf(ru), "UTF-8");
        vifenUid = DbJsonTest.flatGet(ruB, "uid");
        boolean ruOk = DbJsonTest.flatGet(ruB, "result").equals("true")
                && DbJsonTest.flatGet(ruB, "username").equals(vifenName)
                && vifenUid != null && vifenUid.matches("\\d{8}")
                && !vifenUid.equals(genUid) // uid PER AKUN (bukti HAR #32)
                && DbJsonTest.flatGet(ruB, "isGuest").equals("0")
                && DbJsonTest.flatGet(ruB, "isNewUser").equals("1")
                && !ruB.contains("28506361");
        hasil("registerUser-akun-baru-uid-baru", ruOk, ruB);

        // 5b. registerUser DUPLIKAT → ditolak (id=3)
        byte[] ru2 = raw("POST /v1/user/registerUser HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + bodyReg.length() + "\r\nConnection: close\r\n\r\n" + bodyReg);
        String ru2B = new String(bodyOf(ru2), "UTF-8");
        hasil("registerUser-duplikat-ditolak",
                DbJsonTest.flatGet(ru2B, "result").equals("false")
                        && "3".equals(DbJsonTest.flatGet(ru2B, "id"))
                        && DbJsonTest.flatGet(ru2B, "message").contains("terdaftar"), ru2B);

        // 5c. registerUser tanpa password → ditolak (id=4)
        String envTanpaPass = env("{\"username\":\"baru123@x.com\",\"time\":1}");
        byte[] ru3 = raw("POST /v1/user/registerUser HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envTanpaPass.length() + "\r\nConnection: close\r\n\r\n" + envTanpaPass);
        String ru3B = new String(bodyOf(ru3), "UTF-8");
        hasil("registerUser-tanpa-password-ditolak",
                DbJsonTest.flatGet(ru3B, "result").equals("false")
                        && "4".equals(DbJsonTest.flatGet(ru3B, "id")), ru3B);

        // 6. loginByName username+password COCOK → sesi akun itu (uid akun)
        String bodyLog = DbJsonTest.jsonGet(envReg, "loginByName");
        byte[] lb = raw("POST /v1/user/loginByName HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + bodyLog.length() + "\r\nConnection: close\r\n\r\n" + bodyLog);
        String lbB = new String(bodyOf(lb), "UTF-8");
        String lbAuthToken = DbJsonTest.flatGet(lbB, "authToken");
        boolean lbOk = DbJsonTest.flatGet(lbB, "result").equals("true")
                && DbJsonTest.flatGet(lbB, "username").equals(vifenName)
                && DbJsonTest.flatGet(lbB, "uid").equals(vifenUid) // uid AKUN
                && DbJsonTest.flatGet(lbB, "isGuest").equals("0")
                && lbAuthToken != null && lbAuthToken.matches("[0-9a-f]{32}")
                && !lbB.contains("28506361");
        hasil("loginByName-password-cocok-uid-akun", lbOk, lbB);

        // 6b. loginByName password SALAH → ditolak (id=2) — INTI KEBERATAN USER
        String envSalah = env("{\"username\":\"" + vifenName + "\",\"password\":\""
                + md5("password-salah-9x") + "\",\"time\":1}");
        byte[] lb2 = raw("POST /v1/user/loginByName HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envSalah.length() + "\r\nConnection: close\r\n\r\n" + envSalah);
        String lb2B = new String(bodyOf(lb2), "UTF-8");
        hasil("loginByName-password-salah-ditolak",
                DbJsonTest.flatGet(lb2B, "result").equals("false")
                        && "2".equals(DbJsonTest.flatGet(lb2B, "id"))
                        && DbJsonTest.flatGet(lb2B, "message").contains("salah"), lb2B);

        // 6c. loginByName username ASING → ditolak (id=2)
        String envAsing = env("{\"username\":\"tidak-ada@x.com\",\"password\":\"" + vifenPass
                + "\",\"time\":1}");
        byte[] lb3 = raw("POST /v1/user/loginByName HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envAsing.length() + "\r\nConnection: close\r\n\r\n" + envAsing);
        String lb3B = new String(bodyOf(lb3), "UTF-8");
        hasil("loginByName-username-asing-ditolak",
                DbJsonTest.flatGet(lb3B, "result").equals("false")
                        && "2".equals(DbJsonTest.flatGet(lb3B, "id")), lb3B);

        // 6d. loginByName dengan USERNAME GUEST → ditolak (guest tak punya password)
        String envGuest = env("{\"username\":\"" + genUser + "\",\"password\":\"" + vifenPass
                + "\",\"time\":1}");
        byte[] lb4 = raw("POST /v1/user/loginByName HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envGuest.length() + "\r\nConnection: close\r\n\r\n" + envGuest);
        String lb4B = new String(bodyOf(lb4), "UTF-8");
        hasil("loginByName-username-guest-ditolak",
                DbJsonTest.flatGet(lb4B, "result").equals("false")
                        && "2".equals(DbJsonTest.flatGet(lb4B, "id")), lb4B);

        // ===== autoLogin (Opsi A 1.0.3 + lastUid 1.0.4) =====
        // 6e. envelope ASLI perangkat (authToken e963968f… tak pernah kita terbitkan)
        String bodyAuto = DbJsonTest.jsonGet(envReg, "autoLogin");
        byte[] a0 = raw("POST /v1/user/autoLogin HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + bodyAuto.length() + "\r\nConnection: close\r\n\r\n" + bodyAuto);
        String a0B = new String(bodyOf(a0), "UTF-8");
        hasil("autoLogin-asing-ditolak",
                headOf(a0).startsWith("HTTP/1.1 200 OK")
                        && DbJsonTest.flatGet(a0B, "result").equals("false")
                        && "1".equals(DbJsonTest.flatGet(a0B, "id"))
                        && !a0B.contains("e963968f"), a0B);

        // 6f. autoLogin authToken KITA (dari loginByName) → sesi AKUN (uid akun)
        String envCocok = env("{\"authToken\":\"" + lbAuthToken + "\",\"time\":1}");
        byte[] a1 = raw("POST /v1/user/autoLogin HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envCocok.length() + "\r\nConnection: close\r\n\r\n" + envCocok);
        String a1B = new String(bodyOf(a1), "UTF-8");
        String a1Token = DbJsonTest.flatGet(a1B, "token");
        String a1AuthToken = DbJsonTest.flatGet(a1B, "authToken");
        boolean a1Ok = headOf(a1).startsWith("HTTP/1.1 200 OK")
                && DbJsonTest.flatGet(a1B, "result").equals("true")
                && DbJsonTest.flatGet(a1B, "username").equals(vifenName)
                && DbJsonTest.flatGet(a1B, "uid").equals(vifenUid)
                && DbJsonTest.flatGet(a1B, "isGuest").equals("0")
                && a1Token != null && a1Token.matches("[0-9a-f]{32}")
                && a1AuthToken != null && !a1AuthToken.equals(lbAuthToken) // rotasi sesi
                && !a1B.contains("{{");
        hasil("autoLogin-cocok-sesi-akun-uid-akun", a1Ok, a1B);

        // 6g. autoLogin rotasi → token sesi BEDA
        String envRot = env("{\"authToken\":\"" + a1AuthToken + "\",\"time\":1}");
        byte[] a2 = raw("POST /v1/user/autoLogin HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envRot.length() + "\r\nConnection: close\r\n\r\n" + envRot);
        String a2B = new String(bodyOf(a2), "UTF-8");
        hasil("autoLogin-rotasi-token-sesi",
                DbJsonTest.flatGet(a2B, "result").equals("true")
                        && !DbJsonTest.flatGet(a2B, "token").equals(a1Token)
                        && DbJsonTest.flatGet(a2B, "username").equals(vifenName), a2B);
        String a2AuthToken = DbJsonTest.flatGet(a2B, "authToken");

        // 6h. registerVisitor → autoLogin → sesi guest dipulihkan
        byte[] rv3 = bodyOf(post("v1/user/registerVisitor", 604));
        String rv3B = new String(rv3, "UTF-8");
        String guestAuthToken = DbJsonTest.flatGet(rv3B, "authToken");
        String envGuestAuto = env("{\"authToken\":\"" + guestAuthToken + "\",\"time\":1}");
        byte[] a3 = raw("POST /v1/user/autoLogin HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envGuestAuto.length() + "\r\nConnection: close\r\n\r\n" + envGuestAuto);
        String a3B = new String(bodyOf(a3), "UTF-8");
        String a3AuthToken = DbJsonTest.flatGet(a3B, "authToken");
        boolean a3Ok = DbJsonTest.flatGet(a3B, "result").equals("true")
                && DbJsonTest.flatGet(a3B, "isGuest").equals("1")
                && DbJsonTest.flatGet(a3B, "username").equals("Guest" + genUid)
                && DbJsonTest.flatGet(a3B, "uid").equals(genUid);
        hasil("autoLogin-sesi-guest", a3Ok, a3B);

        // 6i. envelope rusak → ditolak
        byte[] a4 = raw("POST /v1/user/autoLogin HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: 604\r\n"
                + "Connection: close\r\n\r\n" + repeat("x", 604));
        String a4B = new String(bodyOf(a4), "UTF-8");
        hasil("autoLogin-envelope-rusak-ditolak",
                DbJsonTest.flatGet(a4B, "result").equals("false")
                        && DbJsonTest.flatGet(a4B, "message") != null, a4B);

        // 7. getUserInfo per sesi: authToken guest STALE + uid AKUN → row-lookup
        String envGi = env("{\"authToken\":\"" + lbAuthToken + "\",\"uid\":\"" + vifenUid
                + "\",\"time\":1}");
        byte[] gi1 = raw("POST /v1/auth/getUserInfo HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envGi.length() + "\r\nConnection: close\r\n\r\n" + envGi);
        String gi1B = new String(bodyOf(gi1), "UTF-8");
        hasil("getUserInfo-per-akun-uid",
                DbJsonTest.flatGet(gi1B, "nickName").equals(vifenName), gi1B);

        // 7b. getUserInfo fallback: body sampah → username generator
        byte[] gi2 = bodyOf(post("v1/auth/getUserInfo", 1877));
        String gi2B = new String(gi2, "UTF-8");
        hasil("getUserInfo-fallback-generator",
                DbJsonTest.flatGet(gi2B, "nickName").equals("Guest" + genUid)
                        && !gi2B.contains("KdddFRi"), gi2B);

        // 8. 404 + tolak ..
        check404("404-unknown", get("bsfake/tidak-ada.json"));
        check404("404-dots", get("../etc/passwd"));

        // 9. generator.json persist + lastUid
        String genFile = readText(EXT + "/server/generator.json");
        hasil("generator-json-persist-lastuid",
                DbJsonTest.flatGet(genFile, "uid").equals(genUid)
                        && DbJsonTest.flatGet(genFile, "lastAuthToken").equals(a3AuthToken)
                        && DbJsonTest.flatGet(genFile, "lastUid").equals(genUid)
                        && DbJsonTest.flatGet(genFile, "lastUsername").equals("Guest" + genUid)
                        && DbJsonTest.flatGet(genFile, "lastIsGuest").equals("1"), genFile);

        // 9b. accounts.txt: DB akun = 2 baris (guest + vifen), TSV 4 kolom
        String accts = readText(EXT + "/server/accounts.txt");
        int baris = 0;
        String barisGuest = null;
        String barisVifen = null;
        for (String line : accts.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            baris++;
            if (line.startsWith(genUser + "\t")) {
                barisGuest = line;
            }
            if (line.startsWith(vifenName + "\t")) {
                barisVifen = line;
            }
        }
        hasil("accounts-txt-seed",
                baris == 2 && barisGuest != null && barisVifen != null
                        && barisGuest.split("\t", -1).length == 4
                        && barisGuest.split("\t", -1)[2].equals(genUid)
                        && barisGuest.split("\t", -1)[3].equals("1")
                        && barisVifen.split("\t", -1)[2].equals(vifenUid)
                        && barisVifen.split("\t", -1)[3].equals("0"), accts);

        // 9c. passHash = md5(password_klien + salt) — BUKAN nilai mentah klien
        String hashTersimpan = barisVifen.split("\t", -1)[1];
        hasil("passhash-doublehash-salt",
                !hashTersimpan.equals(vifenPass)
                        && hashTersimpan.equals(md5(vifenPass + SALT))
                        && hashTersimpan.matches("[0-9a-f]{32}"), hashTersimpan);

        // ============ FASE B: re-sync + LINTAS BOOT ============
        System.out.println("--- FASE B (re-sync + boot ulang) ---");
        new java.io.FileWriter(EXT + "/server/VERSION").write("0.0.9-lama");
        new java.io.FileWriter(EXT + "/server/qsdk/v1/system/getNotice").write("RUSAK");
        resetStatics();

        // 10. re-sync memulihkan file; VERSION 1.0.4; DB akun BERTAHAN
        byte[] noticeB = bodyOf(post("v1/system/getNotice", 604));
        hasil("resync-mulihkan-statik",
                java.util.Arrays.equals(noticeB, read(MIRROR + "/qsdk/v1/system/getNotice")),
                new String(noticeB, "UTF-8"));
        hasil("resync-versi",
                readText(EXT + "/server/VERSION").equals("1.0.4"),
                readText(EXT + "/server/VERSION"));
        String accts2 = readText(EXT + "/server/accounts.txt");
        hasil("resync-db-akun-bertahan",
                accts2.contains(genUser + "\t") && accts2.contains(vifenName + "\t")
                        && accts2.contains(vifenUid), accts2);

        // 10c. autoLogin LINTAS BOOT — replika HAR sesi-2: panggilan PERTAMA,
        //      token guest dari boot lama → sesi guest dipulihkan
        String envBoot = env("{\"authToken\":\"" + a3AuthToken + "\",\"time\":1}");
        byte[] a5 = raw("POST /v1/user/autoLogin HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envBoot.length() + "\r\nConnection: close\r\n\r\n" + envBoot);
        String a5B = new String(bodyOf(a5), "UTF-8");
        hasil("autoLogin-lintas-boot-guest",
                DbJsonTest.flatGet(a5B, "result").equals("true")
                        && DbJsonTest.flatGet(a5B, "isGuest").equals("1")
                        && DbJsonTest.flatGet(a5B, "username").equals("Guest" + genUid)
                        && DbJsonTest.flatGet(a5B, "uid").equals(genUid), a5B);

        // 10d. loginByName LINTAS BOOT: akun terdaftar + password benar →
        //      SUKSES dgn uid SAMA (DB akun persisten); sesi terakhir = vifen
        String envLbBoot = env("{\"username\":\"" + vifenName + "\",\"password\":\""
                + vifenPass + "\",\"time\":1}");
        byte[] lb5 = raw("POST /v1/user/loginByName HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envLbBoot.length() + "\r\nConnection: close\r\n\r\n" + envLbBoot);
        String lb5B = new String(bodyOf(lb5), "UTF-8");
        String lb5AuthToken = DbJsonTest.flatGet(lb5B, "authToken");
        hasil("loginByName-lintas-boot-uid-sama",
                DbJsonTest.flatGet(lb5B, "result").equals("true")
                        && DbJsonTest.flatGet(lb5B, "uid").equals(vifenUid)
                        && lb5AuthToken != null, lb5B);

        // 10e. BOOT C: autoLogin token AKUN dari boot B → sesi akun dipulihkan
        //      (login bertahan antar boot untuk akun terdaftar — Opsi A utuh)
        resetStatics();
        String envAutoAkun = env("{\"authToken\":\"" + lb5AuthToken + "\",\"time\":1}");
        byte[] a6 = raw("POST /v1/user/autoLogin HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envAutoAkun.length() + "\r\nConnection: close\r\n\r\n" + envAutoAkun);
        String a6B = new String(bodyOf(a6), "UTF-8");
        hasil("autoLogin-lintas-boot-akun",
                DbJsonTest.flatGet(a6B, "result").equals("true")
                        && DbJsonTest.flatGet(a6B, "username").equals(vifenName)
                        && DbJsonTest.flatGet(a6B, "uid").equals(vifenUid)
                        && DbJsonTest.flatGet(a6B, "isGuest").equals("0"), a6B);

        // 10f. REPLIKA HAR #28 PERSIS: akun perangkat (yihonew485 + md5("123456"))
        //      di server SEGAR → DITOLAK (tak terdaftar) — dulu 1.0.3 "sukses" palsu;
        //      lalu register → login ulang → sukses (perilaku server asli DB baru)
        String envHar = env("{\"username\":\"yihonew485@juicehour.com\","
                + "\"password\":\"e10adc3949ba59abbe56e057f20f883e\",\"time\":1}");
        byte[] h1 = raw("POST /v1/user/loginByName HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envHar.length() + "\r\nConnection: close\r\n\r\n" + envHar);
        String h1B = new String(bodyOf(h1), "UTF-8");
        hasil("replika-har28-login-terdaftar-ditolak",
                DbJsonTest.flatGet(h1B, "result").equals("false")
                        && "2".equals(DbJsonTest.flatGet(h1B, "id")), h1B);
        String envHarReg = env("{\"username\":\"yihonew485@juicehour.com\","
                + "\"password\":\"e10adc3949ba59abbe56e057f20f883e\",\"time\":1}");
        byte[] h2 = raw("POST /v1/user/registerUser HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envHarReg.length() + "\r\nConnection: close\r\n\r\n" + envHarReg);
        String h2B = new String(bodyOf(h2), "UTF-8");
        String harUid = DbJsonTest.flatGet(h2B, "uid");
        hasil("replika-har28-register-uid-baru",
                DbJsonTest.flatGet(h2B, "result").equals("true")
                        && harUid != null && harUid.matches("\\d{8}")
                        && !harUid.equals(genUid) && !harUid.equals(vifenUid), h2B);
        byte[] h3 = raw("POST /v1/user/loginByName HTTP/1.1\r\nHost: 127.0.0.1:28110\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                + envHar.length() + "\r\nConnection: close\r\n\r\n" + envHar);
        String h3B = new String(bodyOf(h3), "UTF-8");
        String h3AuthToken = DbJsonTest.flatGet(h3B, "authToken");
        hasil("replika-har28-login-setelah-register",
                DbJsonTest.flatGet(h3B, "result").equals("true")
                        && DbJsonTest.flatGet(h3B, "uid").equals(harUid)
                        && h3AuthToken != null, h3B);

        // 14. generator.json: last* = sesi akun terakhir (login yihonew485, 10f)
        //     — DICEK SEBELUM registerVisitor (11) menerbitkan sesi guest baru
        String genFile2 = readText(EXT + "/server/generator.json");
        hasil("generator-json-lintas-boot-akun",
                DbJsonTest.flatGet(genFile2, "lastAuthToken").equals(h3AuthToken)
                        && DbJsonTest.flatGet(genFile2, "lastUid").equals(harUid)
                        && DbJsonTest.flatGet(genFile2, "lastUsername")
                                .equals("yihonew485@juicehour.com")
                        && DbJsonTest.flatGet(genFile2, "lastIsGuest").equals("0"), genFile2);

        // 11. uid guest bertahan
        byte[] rv2 = bodyOf(post("v1/user/registerVisitor", 604));
        String rv2B = new String(rv2, "UTF-8");
        hasil("uid-guest-bertahan-sesudah-resync",
                DbJsonTest.flatGet(rv2B, "uid").equals(genUid)
                        && DbJsonTest.flatGet(rv2B, "username").equals(genUser), rv2B);

        // 12. sign stabil lintas boot, loginToken beda
        byte[] g4 = raw("GET /" + q + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\nConnection: close\r\n\r\n");
        String g4b = new String(bodyOf(g4), "UTF-8");
        hasil("sign-stabil-lintas-boot",
                DbJsonTest.flatGet(g4b, "sign").equals(signPhaseA)
                        && !DbJsonTest.flatGet(g4b, "loginToken")
                                .equals(DbJsonTest.flatGet(g1b, "loginToken")), g4b);

        // 15. gerbang PER AKUN: uid vifen → sign = md5(uid vifen + salt)
        String qV = "loginchecknative?uid=" + vifenUid + "&usermode=0&username="
                + vifenName + "&token=abc";
        byte[] g5 = raw("GET /" + qV + " HTTP/1.1\r\nHost: 127.0.0.1:28110\r\nConnection: close\r\n\r\n");
        String g5b = new String(bodyOf(g5), "UTF-8");
        signVifen = DbJsonTest.flatGet(g5b, "sign");
        hasil("gerbang-per-akun-sign",
                DbJsonTest.flatGet(g5b, "code").equals("0")
                        && DbJsonTest.flatGet(g5b, "userId").equals(vifenUid)
                        && DbJsonTest.flatGet(g5b, "nickName").equals(vifenName)
                        && signVifen.equals(md5(vifenUid + SALT))
                        && !signVifen.equals(signPhaseA), g5b);

        System.out.println("\n================ HASIL: " + pass + " PASS, " + fail + " FAIL ================");
        System.exit(fail == 0 ? 0 : 1);
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
        Field fa = g.getDeclaredField("ACCOUNTS");
        fa.setAccessible(true);
        ((java.util.List<?>) fa.get(null)).clear();
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
