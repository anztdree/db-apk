import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;

import com.db.local.server.Config;
import com.db.local.server.Db;
import com.db.local.server.LoginData;
import com.db.local.server.LoginServer;
import com.db.local.server.SdkData;
import com.db.local.server.SettingBin;

/**
 * HARNESS 0.4.3 — DRAGONBALL.DB (SQLite) + regresi penuh 0.4.2.
 *
 * Uji ujung-ke-ujung: migrasi txt lama → dragonball.db, keutuhan data setelah
 * "reboot" (buka ulang DB), SQL sungguhan di harness (JDBC), lalu seluruh
 * jalur SDK (init/registerVisitor/autoLogin/registerUser/loginByName/
 * ckRegVistor/loginchecknative) + loginGame (socket.io) + rewrite setting bin.
 *
 * Jalankan (dari root repo, $TOOLS berisi sqlite-jdbc.jar):
 *   java -jar tools/ecj-3.33.0.jar -source 8 -target 8 -nowarn \
 *     -cp "build/classes:$TOOLS/sqlite-jdbc.jar" -d build/harness \
 *     local-server/harness/HarnessSdk.java
 *   java -cp "build/harness:build/classes:$TOOLS/sqlite-jdbc.jar" \
 *     HarnessSdk /tmp/dbtest-root
 */
public class HarnessSdk {

    static int pass = 0;
    static int fail = 0;

    static final String SECRET = "0b2a18e45d7df321";
    static final String LEGACY_GUEST = "guest11054530";
    static final String LEGACY_TOKEN = "tokensemilama0123456789abcdef";
    static final String LEGACY_DEV = "DEVLEGACY0001";
    static final String LEGACY_ACC = "penjagatua";
    static final String LEGACY_ACC_PASS = md5s("rahasialama");

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        rmrf(root);
        File conf = new File(root, "config");
        conf.mkdirs();
        write(new File(conf, "server.json"),
                "{\"mode\":\"local\",\"gamePort\":8080,\"debugPort\":4000,\"loginPort\":8610,\"useSdk\":false}");
        write(new File(conf, "servers.json"),
                "{\"errorCode\":0,\"serverList\":[{\"serverId\":\"2109\",\"url\":\"https://s2109-bs.popoh5.com:8061\","
                        + "\"name\":\"S2109\",\"online\":true,\"new\":false,\"hot\":false,\"openDate\":\"\",\"offlineReason\":\"\",\"merge\":false,\"mergeTo\":\"\"}],\"history\":[]}");

        // ---------------- TAHAP 1: MIGRASI txt lama → dragonball.db ----------------
        write(new File(conf, "local-user.txt"), LEGACY_GUEST + "\n");
        write(new File(conf, "accounts.txt"),
                LEGACY_ACC + "\t" + LEGACY_ACC_PASS + "\t2026-11-02 21:00:00\t2026-11-02 22:00:00\t7\n");
        write(new File(conf, "sdk-guests.txt"), "# tanpa baris (kondisi 0.4.2 segel)\n");
        write(new File(conf, "sdk-session.txt"), LEGACY_TOKEN + "\t" + LEGACY_GUEST + "\t1\t2026-11-02 21:31:00\n");
        write(new File(conf, "sdk-last.txt"), LEGACY_GUEST + "\t" + LEGACY_GUEST + "\t2026-11-02 21:31:05\n");

        Db.boot(conf);
        LoginData.init(conf);
        SdkData.init(conf);

        check("engine = SQLite sungguhan (JDBC) — SQL beneran diuji", Db.i().sqlite());
        check("migrasi local-user.txt → kv local_user", LEGACY_GUEST.equals(LoginData.localUserId()));
        check("migrasi accounts.txt → tabel accounts (password MD5 utuh)",
                LEGACY_ACC_PASS.equalsIgnoreCase(LoginData.accountPassword(LEGACY_ACC)));
        check("migrasi sdk-session.txt → tabel sdk_sessions (uid+isGuest)",
                (LEGACY_GUEST + "\t1").equals(Db.i().session(LEGACY_TOKEN)));
        check("migrasi sdk-last.txt → kv last_uid (userid url H5)",
                LEGACY_GUEST.equals(SdkData.lastKnownUid()));
        check("migrasi: jumlah akun = 1", LoginData.accountCount() == 1);
        check("file lama di-rename *.imported (tidak diimpor dobel)",
                !new File(conf, "local-user.txt").exists()
                        && new File(conf, "local-user.txt.imported").exists());

        // ---------------- TAHAP 2: REBOOT — buka ulang DB (simulasi mati/hidup) ----------------
        Db.boot(conf);
        check("REBOOT: guest pemilik tetap (dulu txt terhapus Cleaner — bug 0.4.2)",
                LEGACY_GUEST.equals(LoginData.localUserId()));
        check("REBOOT: akun tetap", LoginData.accountPassword(LEGACY_ACC) != null);
        check("REBOOT: sesi tetap", Db.i().session(LEGACY_TOKEN) != null);
        check("REBOOT: tidak ada impor dobel (jumlah akun tetap 1)", LoginData.accountCount() == 1);

        // ---------------- TAHAP 3: server login + seluruh jalur SDK (regresi 0.4.2) ----------------
        Config.load(root); // mode LOCAL dari server.json (tanpa ini default = online → proxy)
        LoginServer ls = new LoginServer("127.0.0.1", 8610);
        ls.start(500, false);
        Thread.sleep(700);

        String base = "http://127.0.0.1:8610";
        String dev = "DEVTEST0123456789";

        // 1) /v1/system/init — envelope persis SDK
        String initParams = "{\\\"sdkVersion\\\":223,\\\"gameVersion\\\":4,\\\"deviceId\\\":\\\"" + dev
                + "\\\",\\\"productCode\\\":\\\"75880787318053043365515388491588\\\",\\\"channelCode\\\":\\\"unknown\\\","
                + "\\\"authToken\\\":\\\"\\\",\\\"clientLang\\\":\\\"in\\\",\\\"platform\\\":1}";
        String r = post(base + "/v1/system/init", envelope(initParams));
        check("init result:true", r.contains("\"result\":true"));
        check("init loginTypes [\"1\",\"13\"]", r.contains("loginTypes") && r.contains("13"));
        check("init version empty (tanpa paksa update)", r.contains("versionNo\":\"empty"));

        // 2) registerVisitor — tamu baru perangkat baru = migrasi guest pemilik
        String rv = "{\\\"deviceId\\\":\\\"" + dev + "\\\",\\\"authToken\\\":\\\"\\\",\\\"clientLang\\\":\\\"in\\\","
                + "\\\"sdkVersion\\\":223,\\\"gameVersion\\\":4,\\\"platform\\\":1,\\\"channelCode\\\":\\\"unknown\\\"}";
        r = post(base + "/v1/user/registerVisitor", envelope(rv));
        check("registerVisitor result:true", r.contains("\"result\":true"));
        check("registerVisitor tamu pertama = guest pemilik (migrasi)", r.contains(LEGACY_GUEST));
        check("registerVisitor isGuest 1", r.contains("\"isGuest\":1"));
        check("registerVisitor isNewUser 1", r.contains("\"isNewUser\":1"));
        String authToken = jsonStr(r, "authToken");
        check("registerVisitor authToken 32 hex", authToken != null && authToken.length() == 32);

        // 3) registerVisitor ulang — deviceId sama = tamu sama (server asli: isNewUser 0)
        r = post(base + "/v1/user/registerVisitor", envelope(rv));
        check("registerVisitor ulang uid sama", r.contains(LEGACY_GUEST));
        check("registerVisitor ulang isNewUser 0", r.contains("\"isNewUser\":0"));
        check("DB: tamu tercatat per perangkat (guestCount 1)", Db.i().guestCount() == 1);

        // 4) registerUser + loginByName (benar/salah) — tabel accounts
        String md5pass = md5s("rahasia123");
        String ru = "{\\\"username\\\":\\\"penjagabaru\\\",\\\"password\\\":\\\"" + md5pass + "\\\","
                + "\\\"authToken\\\":\\\"\\\",\\\"deviceId\\\":\\\"" + dev + "\\\",\\\"sdkVersion\\\":223,"
                + "\\\"gameVersion\\\":4,\\\"platform\\\":1,\\\"channelCode\\\":\\\"unknown\\\"}";
        r = post(base + "/v1/user/registerUser", envelope(ru));
        check("registerUser result:true", r.contains("\"result\":true") && r.contains("penjagabaru"));
        r = post(base + "/v1/user/registerUser", envelope(ru));
        check("registerUser dobel → ditolak", r.contains("\"result\":false"));
        check("DB: akun baru ada di tabel accounts (legacy + tamu + baru = 3)",
                LoginData.accountCount() == 3);
        String[] legacyAccRow = Db.i().account(LEGACY_ACC);
        check("DB: kolom login_count warisan utuh (7)",
                legacyAccRow != null && "7".equals(legacyAccRow[3]));

        String lb = "{\\\"username\\\":\\\"penjagabaru\\\",\\\"password\\\":\\\"" + md5pass + "\\\","
                + "\\\"authToken\\\":\\\"\\\",\\\"deviceId\\\":\\\"" + dev + "\\\",\\\"sdkVersion\\\":223,"
                + "\\\"gameVersion\\\":4,\\\"platform\\\":1,\\\"channelCode\\\":\\\"unknown\\\"}";
        r = post(base + "/v1/user/loginByName", envelope(lb));
        check("loginByName benar result:true", r.contains("\"result\":true") && r.contains("penjagabaru"));
        r = post(base + "/v1/user/loginByName", envelope(lb.replace(md5pass, md5s("salah"))));
        check("loginByName salah → 40101", r.contains("40101"));
        String[] baruRow = Db.i().account("penjagabaru");
        check("DB: loginByName naikkan login_count",
                baruRow != null && baruRow[3] != null && !"1".equals(baruRow[3]));

        // 5) autoLogin token sesi benar / token asal → 40005
        String al = "{\\\"authToken\\\":\\\"" + authToken + "\\\",\\\"deviceId\\\":\\\"" + dev + "\\\","
                + "\\\"sdkVersion\\\":223,\\\"gameVersion\\\":4,\\\"platform\\\":1,\\\"channelCode\\\":\\\"unknown\\\"}";
        r = post(base + "/v1/user/autoLogin", envelope(al));
        check("autoLogin token sesi benar → uid sama", r.contains(LEGACY_GUEST));
        r = post(base + "/v1/user/autoLogin", envelope(al.replace(authToken, "ffffffffffffffffffffffffffffffff")));
        check("autoLogin token asal → 40005", r.contains("40005") && r.contains("Otorisasi gagal"));

        // 6) ckRegVistor
        r = post(base + "/v1/user/ckRegVistor", envelope(
                "{\\\"authToken\\\":\\\"" + authToken + "\\\",\\\"deviceId\\\":\\\"" + dev + "\\\","
                        + "\\\"sdkVersion\\\":223,\\\"gameVersion\\\":4,\\\"platform\\\":1,\\\"channelCode\\\":\\\"unknown\\\"}"));
        check("ckRegVistor isReged 1", r.contains("isReged\":1"));

        // 7) loginchecknative — skema respons ASLI (sesi dari DB)
        String qs = "?uid=" + LEGACY_GUEST + "&username=" + LEGACY_GUEST + "&token="
                + URLEncoder.encode(authToken, "UTF-8")
                + "&usermode=1&osVersion=29&packageName=com.db.local&appVersion=4&sourceVersion=4"
                + "&brand=Android&model=Android&os=Android&sdk=BS";
        r = get(base + "/loginchecknative" + qs);
        check("loginchecknative code 0", r.contains("\"code\":0"));
        check("loginchecknative sdk BSNative", r.contains("BSNative"));
        check("loginchecknative sdkType BS", r.contains("\"sdkType\":\"BS\""));
        check("loginchecknative userId benar", r.contains(LEGACY_GUEST));
        check("loginchecknative loginToken ada", r.contains("loginToken"));

        // 7b) loginchecknative token asal + akun asing → code 500
        String qs2 = "?uid=orangasing123&username=orangasing123&token=tokongasal&usermode=1"
                + "&os=Android&sdk=BS";
        r = get(base + "/loginchecknative" + qs2);
        check("loginchecknative tak sah → code 500", r.contains("\"code\":500"));

        // 8) loginchecknative akun pilihan layar SDK → lastKnownUid ikut
        qs = "?uid=penjagabaru&username=penjagabaru&token=" + URLEncoder.encode(authToken, "UTF-8")
                + "&usermode=0&os=Android&sdk=BS";
        r = get(base + "/loginchecknative" + qs);
        check("loginchecknative akun → code 0", r.contains("\"code\":0"));
        check("DB: kv last_uid = pilihan layar SDK", "penjagabaru".equals(SdkData.lastKnownUid()));

        // 9) loginGame mengikuti sesi SDK (socket.io — identitas DB)
        String frame = "420[\"handler.process\",{\"action\":\"loginGame\",\"userId\":\"" + LEGACY_GUEST + "\","
                + "\"password\":\"game_origin\",\"sign\":\"\",\"sdk\":\"\"}]";
        r = socketIoPost(frame);
        check("loginGame mengikuti sesi SDK (penjagabaru)", r.contains("penjagabaru"));
        check("loginGame skema server asli utuh", r.contains("loginToken") && r.contains("isCreated"));
        check("DB: akun loginGame tercatat", Db.i().account("penjagabaru") != null);

        // 10) socket.io handshake + GetServerList (regresi 0.4.0)
        r = get(base + "/socket.io/?EIO=3&transport=polling&t=1");
        check("handshake engine.io framing 0{...}40", r.contains(":0{") && r.contains(":40"));
        frame = "420[\"handler.process\",{\"action\":\"GetServerList\"}]";
        r = socketIoPost(frame);
        check("GetServerList 1 server (uji)", r.contains("serverId"));

        // 11) rewrite setting bin — loginServer TANPA port + userid sesi SDK
        String binJson = "{\n\t\"testMode\": 1,\n"
                + "\t\"url\": \"https://dragonh5cdn.popoh5.com/bs/index-native.html?v=202109150935\",\n"
                + "\t\"tsUrl\": \"http://dragon.sjmobilegame.com:82/bs103/index-native.html\",\n"
                + "\t\"update\": \"https://dragonh5cdn.popoh5.com/bs/upgrade\",\n"
                + "\t\"checkTime\": 300,\n"
                + "\t\"loginServer\": \"https://login.popoh5.com\",\n"
                + "\t\"tsLoginServer\": \"http://118.24.68.91:7003\",\n"
                + "\t\"loginPort\": 610,\n\t\"loginWebPort\": 510,\n"
                + "\t\"clientParams\": {\"supportLang\": []}\n}";
        File binEnc = new File(conf, "setting-test.bin");
        write(binEnc, new String(xor(binJson.getBytes("UTF-8")), "ISO-8859-1"));
        byte[] out = SettingBin.rewrittenLocal(binEnc, 8080);
        String outJson = new String(xor(out), "UTF-8");
        check("bin loginServer TANPA port (FIX 0.4.2)",
                outJson.contains("\"loginServer\": \"http://127.0.0.1\"")
                        && outJson.contains("\"loginWebPort\": 8610")
                        && !outJson.contains("\"loginServer\": \"http://127.0.0.1:"));
        check("bin loginPort 8610", outJson.contains("\"loginPort\": 8610"));
        check("bin url game → 127.0.0.1:8080", outJson.contains("http://127.0.0.1:8080/bs/index-native.html"));
        check("bin url userid = sesi SDK (penjagabaru — dari dragonball.db)",
                outJson.contains("userid=penjagabaru"));
        check("bin TIDAK ada host asli lagi", !outJson.contains("login.popoh5.com")
                && !outJson.contains("dragonh5cdn.popoh5.com"));

        System.out.println();
        System.out.println("==== HASIL: " + pass + " LULUS, " + fail + " GAGAL ====");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ---------------- util ----------------

    static void rmrf(File f) {
        if (!f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                rmrf(k);
            }
        }
        f.delete();
    }

    /** Body SDK asli: sv=v2&data=<b64 json>&sign=<md5> (nilai urut abjad + rahasia). */
    static String envelope(String escapedJson) throws Exception {
        String json = escapedJson.replace("\\\"", "\"");
        TreeMap<String, String> p = new TreeMap<String, String>();
        int i = 0;
        while (i < json.length()) {
            int q1 = json.indexOf('"', i);
            if (q1 < 0) break;
            int q2 = json.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String k = json.substring(q1 + 1, q2);
            int c = json.indexOf(':', q2 + 1);
            if (c < 0) break;
            int j = c + 1;
            while (j < json.length() && json.charAt(j) == ' ') j++;
            if (j < json.length() && json.charAt(j) == '"') {
                int k2 = json.indexOf('"', j + 1);
                p.put(k, json.substring(j + 1, k2));
                i = k2 + 1;
            } else {
                int k2 = j;
                while (k2 < json.length() && json.charAt(k2) != ',' && json.charAt(k2) != '}') k2++;
                p.put(k, json.substring(j, k2).trim());
                i = k2;
            }
        }
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            buf.append(e.getKey()).append('=').append(e.getValue()).append('&');
        }
        buf.append(SECRET);
        String sign = md5s(buf.toString());
        String data = java.util.Base64.getEncoder().encodeToString(json.getBytes("UTF-8"));
        return "sv=v2&data=" + URLEncoder.encode(data, "UTF-8") + "&sign=" + sign;
    }

    static String post(String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] b = body.getBytes("UTF-8");
        c.setRequestProperty("Content-Length", String.valueOf(b.length));
        c.getOutputStream().write(b);
        c.getOutputStream().flush();
        c.getOutputStream().close();
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        String out = readAll(in);
        c.disconnect();
        return out;
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        String out = readAll(in);
        c.disconnect();
        return out;
    }

    /** POST engine.io berframing ke /socket.io/ (payload socket.io event). */
    static String socketIoPost(String eventJson) throws Exception {
        String hs = get("http://127.0.0.1:8610/socket.io/?EIO=3&transport=polling&t=1");
        int a = hs.indexOf('{');
        int b = hs.indexOf('}', a);
        String sid = jsonStr(hs.substring(a, b + 1), "sid");
        // CONNECT berframing: "2:40"
        String body = 2 + ":" + "40";
        post("http://127.0.0.1:8610/socket.io/?EIO=3&transport=polling&sid=" + URLEncoder.encode(sid, "UTF-8"),
                body);
        // EVENT berframing: "<len>:420[...]"
        body = eventJson.length() + ":" + eventJson;
        post("http://127.0.0.1:8610/socket.io/?EIO=3&transport=polling&sid=" + URLEncoder.encode(sid, "UTF-8"),
                body);
        return get("http://127.0.0.1:8610/socket.io/?EIO=3&transport=polling&sid=" + URLEncoder.encode(sid, "UTF-8"));
    }

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("LULUS  - " + name);
        } else {
            fail++;
            System.out.println("GAGAL  - " + name);
        }
    }

    static String jsonStr(String obj, String key) {
        String needle = "\"" + key + "\":";
        int i = obj.indexOf(needle);
        if (i < 0) return null;
        int a = obj.indexOf('"', i + needle.length());
        if (a < 0) return null;
        int b = a + 1;
        while (b < obj.length() && obj.charAt(b) != '"') b++;
        if (b >= obj.length()) return null;
        return obj.substring(a + 1, b);
    }

    static byte[] xor(byte[] data) {
        byte[] k = "DragonBall".getBytes();
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ k[i % k.length]);
        }
        return out;
    }

    static String md5s(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            return "00000000000000000000000000000000";
        }
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (in != null && (n = in.read(buf)) > 0) bos.write(buf, 0, n);
        if (in != null) in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    static void write(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        Writer w = new OutputStreamWriter(new java.io.FileOutputStream(f, false), "UTF-8");
        w.write(content);
        w.close();
    }
}
