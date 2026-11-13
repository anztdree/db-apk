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
import com.db.local.server.ConfigAkar;
import com.db.local.server.Db;
import com.db.local.server.GameServer;
import com.db.local.server.HumanLog;
import com.db.local.server.LogGroups;
import com.db.local.server.LoginData;
import com.db.local.server.LoginServer;
import com.db.local.server.SdkData;
import com.db.local.server.SettingBin;

/**
 * HARNESS 0.4.6 — MODEL FINAL LOG (grup batch) + regresi penuh 0.4.2–0.4.5.
 *
 * Baru 0.4.6 (kesepakatan owner "group itu kalau terdeteksi emang
 * config/resource yg banyak. kalau alur ya gk usah di group"): LogGroups —
 * mirror/request/404/passthrough masuk GRUP (judul + statistik hidup di
 * dasbor, diklik = rincian per-file; human.log = ringkasan Σ satu baris);
 * kegagalan naik ⚠ linear (jeda 30 dtk); grup kosong tak tampil;
 * loginchecknative OK ganda didedupe; HumanLog.writeRawLine (Σ → file saja).
 *
 * Baru 0.4.5 (koreksi owner "jujur terus terang log anda bagi saya cuma sampah
 * apalagi UI website banyak informasi yg tidak bermanfaat"): log DUA TINGKAT —
 * PENTING selalu tampil (boot dipadatkan 5 baris, mode, tamu/daftar/masuk,
 * server list, ⚠), TEKNIS (request per-file 200/404/seed, probe DNS, dsb.) hanya
 * saat mode RINCI (toggle dasbor → server.json "logTech"); file human.log TETAP
 * lengkap (bukti tidak hilang) + API /api/logs/file (Copy penuh); dasbor dirombak
 * — Log pusat + tab Data/Teknis.
 *
 * Baru 0.4.4 (bedah dex: MASUK/DAFTAR layar SDK ASLI memakai EMAIL sebagai
 * nama akun — regex \w+@\w+.\w+, sandi minimal 6): daftar/masuk email diterima
 * server lokal (dulu registerUser menolak '@' → selalu 40102; tanpa akun email
 * → MASUK selalu 40101), tidak peka huruf besar/kecil, uid email mengalir ke
 * loginchecknative dan loginGame.
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

        // ---------------- 0.5.3: paket CONFIG OFFLINE MANUAL (folder config-offline/) ----------------
        File off = new File(root, "config-offline");
        off.mkdirs();
        write(new File(off, "servers.json"),
                "{\"errorCode\":0,\"serverList\":[{\"serverId\":\"1\",\"url\":\"http://127.0.0.1:8080\","
                        + "\"new\":true,\"hot\":true,\"name\":\"Server Lokal\",\"online\":true,\"openDate\":\"\","
                        + "\"offlineReason\":\"\",\"merge\":false,\"mergeTo\":\"\",\"urlInner\":\"\",\"chatInner\":\"\"}],\"history\":[]}");

        // ---------------- TAHAP 1: MIGRASI txt lama → dragonball.db ----------------
        write(new File(conf, "local-user.txt"), LEGACY_GUEST + "\n");
        write(new File(conf, "accounts.txt"),
                LEGACY_ACC + "\t" + LEGACY_ACC_PASS + "\t2026-11-02 21:00:00\t2026-11-02 22:00:00\t7\n");
        write(new File(conf, "sdk-guests.txt"), "# tanpa baris (kondisi 0.4.2 segel)\n");
        write(new File(conf, "sdk-session.txt"), LEGACY_TOKEN + "\t" + LEGACY_GUEST + "\t1\t2026-11-02 21:31:00\n");
        write(new File(conf, "sdk-last.txt"), LEGACY_GUEST + "\t" + LEGACY_GUEST + "\t2026-11-02 21:31:05\n");

        Db.boot(conf);
        LoginData.init(conf, off); // 0.5.3 — dua folder: config/ + config-offline/
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
        check("GetServerList 1 server (paket MANUAL)", r.contains("serverId"));
        check("GetServerList OFFLINE: url entri = 127.0.0.1:8080 (Play masuk lokal)",
                r.contains("http://127.0.0.1:8080"));
        check("GetServerList OFFLINE: TIDAK ada url popoh5 di jawaban",
                !r.contains("popoh5.com"));

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

        // ---------------- TAHAP 4b: AKUN EMAIL (0.4.4 — bedah dex MASUK/DAFTAR) ----------------
        String md5mail = md5s("rahasia999");
        // daftar dengan email campur huruf besar/titik → disimpan huruf kecil
        String re = "{\\\"username\\\":\\\"Budi.New@GMail.com\\\",\\\"password\\\":\\\"" + md5mail + "\\\","
                + "\\\"authToken\\\":\\\"\\\",\\\"deviceId\\\":\\\"" + dev + "\\\",\\\"sdkVersion\\\":223,"
                + "\\\"gameVersion\\\":4,\\\"platform\\\":1,\\\"channelCode\\\":\\\"unknown\\\"}";
        r = post(base + "/v1/user/registerUser", envelope(re));
        check("DAFTAR email (Budi.New@GMail.com) result:true", r.contains("\"result\":true") && r.contains("budi.new@gmail.com"));
        check("DB: akun email tersimpan HURUF KECIL",
                LoginData.accountPassword("budi.new@gmail.com") != null);
        r = post(base + "/v1/user/registerUser", envelope(re));
        check("DAFTAR email dobel → ditolak (40102)", r.contains("\"result\":false") && r.contains("40102"));
        r = post(base + "/v1/user/registerUser", envelope(re.replace("Budi.New@GMail.com", "budi+tes@gmail.com!x")));
        check("DAFTAR email karakter liar ('!') → ditolak", r.contains("\"result\":false"));
        // masuk dengan email HURUF BESAR → mendarat di akun huruf kecil (tidak peka kasus)
        String lbm = "{\\\"username\\\":\\\"BUDI.NEW@GMAIL.COM\\\",\\\"password\\\":\\\"" + md5mail + "\\\","
                + "\\\"authToken\\\":\\\"\\\",\\\"deviceId\\\":\\\"" + dev + "\\\",\\\"sdkVersion\\\":223,"
                + "\\\"gameVersion\\\":4,\\\"platform\\\":1,\\\"channelCode\\\":\\\"unknown\\\"}";
        r = post(base + "/v1/user/loginByName", envelope(lbm));
        check("MASUK email HURUF BESAR → result:true (tidak peka kasus)",
                r.contains("\"result\":true") && r.contains("budi.new@gmail.com"));
        r = post(base + "/v1/user/loginByName", envelope(lbm.replace(md5mail, md5s("salahlagi"))));
        check("MASUK email kata sandi salah → 40101", r.contains("40101"));
        r = post(base + "/v1/user/loginByName", envelope(lbm.replace("BUDI.NEW@GMAIL.COM", "takada@sekali.com")));
        check("MASUK email tidak terdaftar → 40101", r.contains("40101"));
        // loginchecknative dengan uid email (mode akun) → code 0
        qs = "?uid=budi.new@gmail.com&username=budi.new%40gmail.com&token="
                + URLEncoder.encode(authToken, "UTF-8")
                + "&usermode=0&os=Android&sdk=BS";
        r = get(base + "/loginchecknative" + qs);
        check("loginchecknative uid EMAIL → code 0",
                r.contains("\"code\":0") && r.contains("budi.new@gmail.com"));
        check("DB: kv last_uid = email pilihan layar SDK",
                "budi.new@gmail.com".equals(SdkData.lastKnownUid()));
        // loginGame mengikuti sesi SDK → identitas email mengalir ke tahap game
        frame = "420[\"handler.process\",{\"action\":\"loginGame\",\"userId\":\"" + LEGACY_GUEST + "\","
                + "\"password\":\"game_origin\",\"sign\":\"\",\"sdk\":\"\"}]";
        r = socketIoPost(frame);
        check("loginGame mengikuti sesi SDK (email)", r.contains("budi.new@gmail.com"));
        check("DB: akun email tercatat di tabel accounts (loginGame)",
                Db.i().account("budi.new@gmail.com") != null);
        // 12) whitelist Cleaner — servers.json tidak boleh dihapus lagi (0.4.4)
        java.lang.reflect.Method mClean = Class.forName("com.db.local.server.Cleaner")
                .getDeclaredMethod("clean", File.class);
        Object cr = mClean.invoke(null, root);
        String cleaned = cr.getClass().getField("names").get(cr).toString();
        check("Cleaner: servers.json TIDAK dihapus (boleh disunting pemilik)", !cleaned.contains("servers.json"));
        check("Cleaner: dragonball.db tetap dilindungi", !cleaned.contains("dragonball.db"));
        check("Cleaner: folder config-offline/ TIDAK dihapus (paket MANUAL dilindungi 0.5.3)",
                new File(root, "config-offline").exists()
                        && new File(root, "config-offline/servers.json").exists());

        // 13) LOG BERSIH 0.4.5 — dua tingkat (penting selalu / teknis hanya RINCI);
        //     file human.log TETAP lengkap — mode bersih tidak menghilangkan bukti
        HumanLog.init(new File(root, "logs"));
        HumanLog.clearMem();
        HumanLog.setTechEnabled(false);
        HumanLog.ev("✅", "HRNESS penting selalu tampil", null);
        HumanLog.evTech("ℹ️", "HRNESS teknis tersembunyi di mode bersih", null);
        String snap = HumanLog.snapshotJson(0);
        check("log BERSIH: event PENTING tampil di dasbor", snap.contains("penting selalu tampil"));
        check("log BERSIH: event TEKNIS tidak memenuhi dasbor", !snap.contains("teknis tersembunyi"));
        HumanLog.setTechEnabled(true);
        HumanLog.evTech("ℹ️", "HRNESS teknis tampil saat rinci", null);
        String snap2 = HumanLog.snapshotJson(0);
        check("log RINCI: event TEKNIS ikut tampil", snap2.contains("teknis tampil saat rinci"));
        check("log RINCI: baris teknis diberi penanda v:1", snap2.contains("\"v\":1"));
        // file tetap lengkap (penting + teknis tertulis semuanya)
        File hf = new File(new File(root, "logs"), "human.log");
        String fileTxt = readAll(new java.io.FileInputStream(hf));
        check("FILE human.log tetap berisi baris teknis (bukti tidak hilang)",
                fileTxt.contains("teknis tersembunyi"));
        String tail = HumanLog.tailText(100);
        check("Copy penuh (tailText) memuat penting+teknis",
                tail.contains("penting selalu tampil") && tail.contains("teknis tersembunyi"));
        HumanLog.setTechEnabled(false);

        // 14) config logTech — pilihan toggle dasbor tahan reboot (server.json)
        Config.setLogTech(true);
        Config.load(new File(root, "config"));
        check("config: logTech=true tersimpan di server.json", Config.logTech());
        Config.setLogTech(false);
        Config.load(new File(root, "config"));
        check("config: logTech=false batal tersimpan", !Config.logTech());

        // ---------------- TAHAP 15: GRUP LOG BATCH (0.4.6 — model final owner:
        // "group itu kalau terdeteksi emang config/resource yg banyak") ----------------
        LogGroups.clear();
        HumanLog.clearMem();
        // grup kosong tidak tampil (aturan 1)
        check("grup: kosong tidak tampil di /api/groups",
                LogGroups.toJson().equals("{\"groups\":[]}"));
        // item sukses masuk grup + statistik judul (count/bytes), bukan baris linear
        LogGroups.item("grph", "GRPTES Mirror", "file", true,
                "config/a.json — 1,0 KB dari cdn", 1024);
        LogGroups.item("grph", "GRPTES Mirror", "file", true,
                "config/b.json — 2,0 KB dari cdn", 2048);
        String gj = LogGroups.toJson();
        check("grup: item sukses tampil di /api/groups dengan statistik",
                gj.contains("GRPTES Mirror") && gj.contains("\"count\":2")
                        && gj.contains("\"bytes\":3072"));
        check("grup: item sukses TIDAK jadi baris log linear",
                !HumanLog.snapshotJson(0).contains("config/a.json"));
        // item gagal naik ⚠ ke log linear (aturan 2) + dihitung di grup
        LogGroups.item("grph", "GRPTES Mirror", "file", false,
                "config/c.json — server asli tidak terjangkau", 0);
        check("grup: kegagalan NAIK sebagai ⚠ linear",
                HumanLog.snapshotJson(0).contains("GRPTES Mirror gagal")
                        && HumanLog.snapshotJson(0).contains("config/c.json"));
        check("grup: kegagalan dihitung di judul (fail:1)",
                LogGroups.toJson().contains("\"fail\":1"));
        // item gagal berikutnya TIDAK membanjiri log (jeda 30 dtk) — hanya hitung
        int warnBefore = HumanLog.snapshotJson(0).split("GRPTES Mirror gagal", -1).length - 1;
        LogGroups.item("grph", "GRPTES Mirror", "file", false,
                "config/d.json — server asli tidak terjangkau", 0);
        int warnAfter = HumanLog.snapshotJson(0).split("GRPTES Mirror gagal", -1).length - 1;
        check("grup: gagal beruntun tidak membanjiri log linear (jeda 30 dtk)",
                warnAfter == warnBefore);
        // writeRawLine: Σ ringkasan ke human.log SAJA (bukan event dasbor)
        HumanLog.writeRawLine("2026-11-03 00:00:00 [Σ] GRPTES: ringkasan uji — rincian di dasbor");
        check("grup: Σ ringkasan masuk human.log (cerita tempel)",
                readAll(new java.io.FileInputStream(hf)).contains("ringkasan uji"));
        check("grup: Σ ringkasan TIDAK masuk memori dasbor",
                !HumanLog.snapshotJson(0).contains("ringkasan uji"));
        // dedupe loginchecknative ganda (0.4.6; pesan 0.4.8 "Sesi … disahkan
        // (loginchecknative)") — denyut dua kali = satu baris
        LoginData.ensureAccount("dupuid", "");
        HumanLog.clearMem();
        SdkData.checkNative("dupuid", "dupuid", null, "1");
        SdkData.checkNative("dupuid", "dupuid", null, "1");
        int okLines = HumanLog.snapshotJson(0).split("disahkan", -1).length - 1;
        check("grup: loginchecknative ganda didedupe (uid sama < 10 dtk = 1 baris)",
                okLines == 1);
        LogGroups.clear();

        // ---------------- TAHAP 16: CONFIG LOGIN SEMUA MODE (0.5.1 — tuntutan owner
        // "config setelah SDK login masuk ke loginserver" — bukan rekam jejak) ----------------
        // 16a) setting bin mode ONLINE: HANYA jalur login + url H5 → server lokal;
        //      tsUrl (varian uji) tetap ASLI; TANPA userid di url H5.
        String binOnlineJson = "{\"url\":\"https://dragonh5cdn.popoh5.com/bs/index-native.html?v=9\","
                + "\"tsUrl\":\"http://dragon.sjmobilegame.com:82/bs103/index-native.html\","
                + "\"update\":\"https://dragonh5cdn.popoh5.com/bs/upgrade\","
                + "\"loginServer\":\"https://login.popoh5.com\","
                + "\"tsLoginServer\":\"http://118.24.68.91:7003\","
                + "\"loginPort\":\"610\",\"loginWebPort\":\"510\"}";
        byte[] binOnlineEnc = xor(binOnlineJson.getBytes("UTF-8"));
        byte[] binOnlineOut = SettingBin.rewrittenOnline(binOnlineEnc, 8080, 8610);
        String binOnlinePlain = new String(xor(binOnlineOut), "UTF-8");
        check("0.5.1 ONLINE bin: url H5 → port game lokal (8080)",
                "http://127.0.0.1:8080/bs/index-native.html?v=9".equals(jsonStr(binOnlinePlain, "url")));
        check("0.5.1 ONLINE bin: update → port game lokal (H5 lewat server — serversetting ikut)",
                "http://127.0.0.1:8080/bs/upgrade".equals(jsonStr(binOnlinePlain, "update")));
        check("0.5.1 ONLINE bin: loginServer → server login lokal (TANPA port — gaya 0.4.2)",
                "http://127.0.0.1".equals(jsonStr(binOnlinePlain, "loginServer")));
        check("0.5.1 ONLINE bin: loginPort + loginWebPort → 8610",
                "8610".equals(jsonStr(binOnlinePlain, "loginPort"))
                        && "8610".equals(jsonStr(binOnlinePlain, "loginWebPort")));
        check("0.5.1 ONLINE bin: tsLoginServer → server login lokal",
                "http://127.0.0.1:8610".equals(jsonStr(binOnlinePlain, "tsLoginServer")));
        check("0.5.1 ONLINE bin: tsUrl tetap ASLI (varian uji — persis gaya LOCAL)",
                "http://dragon.sjmobilegame.com:82/bs103/index-native.html"
                        .equals(jsonStr(binOnlinePlain, "tsUrl")));
        check("0.5.1 ONLINE bin: TIDAK ada userid di url H5 (identitas boot = mekanisme shell asli)",
                !binOnlinePlain.contains("userid="));
        check("0.5.1 ONLINE bin: hasil tetap bin terenkripsi valid (bukan JSON polos)",
                !new String(binOnlineOut).contains("loginServer"));
        // 16b) serversetting.json mode ONLINE (GameServer passthrough): loginserver → lokal
        String ssAsli = "{\n\t\"loginserver\": \"https://login.popoh5.com:610\",\n\t\"requireSDK\": false\n}";
        String ssBaru = SettingBin.setJsonValue(ssAsli, "loginserver", "http://127.0.0.1:8610");
        check("0.5.1 ONLINE serversetting: loginserver → http://127.0.0.1:8610",
                ssBaru.contains("http://127.0.0.1:8610") && !ssBaru.contains("login.popoh5.com"));
        check("0.5.1 ONLINE serversetting: field lain UTUH (requireSDK)",
                ssBaru.contains("requireSDK") && ssBaru.contains("false"));
        // 16c) passthrough engine.io — parser framing (dipakai log alur ONLINE)
        java.util.ArrayList<String> fr = LoginServer.parseFrames("14:0{\"sid\":\"abc\"}2:40");
        check("0.5.1 passthrough: framing engine.io terurai (OPEN + CONNECT)",
                fr.size() == 2 && fr.get(0).startsWith("0{") && "40".equals(fr.get(1)));
        java.util.ArrayList<String> fr2 = LoginServer.parseFrames("8:420[\"x\"]6:43bad:8:410[\"y\"]");
        check("0.5.1 passthrough: framing beruntun + panjang berdigit (tahan framing asal)",
                fr2.size() == 3 && fr2.get(1).equals("43bad:") && fr2.get(2).equals("410[\"y\"]"));
        check("0.5.1 passthrough: framing rusak tidak meledak (aman)",
                LoginServer.parseFrames(null).size() == 0
                        && LoginServer.parseFrames("abc:def").size() == 0);
        // 16d) hitung server dalam payload daftar ASLI (passthrough)
        String ack = "43p[{\"ret\":0,\"data\":\"{\\\"errorCode\\\":0,\\\"serverList\\\":["
                + "{\\\"serverId\\\":\\\"2109\\\"},{\\\"serverId\\\":\\\"2108\\\"},{\\\"serverId\\\":\\\"2107\\\"}]}\"}]";
        check("0.5.1 passthrough: jumlah server dalam ACK daftar = 3",
                LoginServer.countServerIds(ack) == 3);
        check("0.5.1 passthrough: ACK tanpa daftar = 0",
                LoginServer.countServerIds("43p[{\"ret\":0,\"data\":\"{}\"}]") == 0
                        && LoginServer.countServerIds(null) == 0);
        check("0.5.1 passthrough: upstream login ASLI = HTTPS (probe: HTTP polos ditolak server)",
                LoginServer.UPSTREAM_LOGIN.startsWith("https://"));

        // ---------------- TAHAP 17: AKAR CONFIG (0.5.2 — tegasan owner "log yg
        // jelas rinci itu penting"; jawaban "pakai config online murni .. akarnya
        // aja gak tau") — isi config DIBEDAH FIELD-PER-FIELD di log ----------------
        // 17a) pembedah setting bin: nilai ASLI → nilai yang diterima klien
        String akar = ConfigAkar.settingBin(binOnlineJson, binOnlinePlain);
        check("0.5.2 akar bin: enkripsi disebut (XOR \"DragonBall\")",
                akar.contains("DragonBall"));
        check("0.5.2 akar bin: url ASLI ditulis PENUH (tidak disingkat)",
                akar.contains("https://dragonh5cdn.popoh5.com/bs/index-native.html?v=9"));
        check("0.5.2 akar bin: url KLIEN ditulis penuh dengan tanda →",
                akar.contains("→  http://127.0.0.1:8080/bs/index-native.html?v=9"));
        check("0.5.2 akar bin: tsUrl ditandai (asli) — varian uji tidak disentuh",
                akar.contains("http://dragon.sjmobilegame.com:82/bs103/index-native.html")
                        && akar.contains("(asli)"));
        check("0.5.2 akar bin: loginServer ASLI → lokal ditulis dua-duanya",
                akar.contains("https://login.popoh5.com")
                        && akar.contains("http://127.0.0.1"));
        check("0.5.2 akar bin: port 610 → 8610 terlihat",
                akar.contains("610") && akar.contains("8610"));
        check("0.5.2 akar bin: peran loginWebPort dijelaskan (loginchecknative)",
                akar.contains("loginchecknative"));
        check("0.5.2 akar bin: config tanpa clientParams dilaporkan jujur",
                akar.contains("clientParams") && akar.contains("tidak ada di config"));
        // 17b) getJsonRaw — nilai mentah string/angka/bool
        check("0.5.2 getJsonRaw: angka MURNI bin asli (loginPort 610 tanpa kutip)",
                "610".equals(SettingBin.getJsonRaw(
                        "{\"loginPort\":610,\"checkTime\":300}", "loginPort")));
        check("0.5.2 getJsonRaw: angka MURNI bin asli (loginWebPort 510 tanpa kutip)",
                "510".equals(SettingBin.getJsonRaw(
                        "{\"loginWebPort\":510}", "loginWebPort")));
        check("0.5.2 getJsonRaw: string berangka (fixture tahap 16) = mentah dgn kutip",
                "\"610\"".equals(SettingBin.getJsonRaw(binOnlineJson, "loginPort")));
        check("0.5.2 getJsonRaw: string loginServer dengan kutip dibuang",
                "\"https://login.popoh5.com\"".equals(SettingBin.getJsonRaw(binOnlineJson, "loginServer")));
        check("0.5.2 getJsonRaw: bool false terbaca",
                "false".equals(SettingBin.getJsonRaw(ssAsli, "requireSDK")));
        check("0.5.2 getJsonRaw: kunci tak ada = null",
                SettingBin.getJsonRaw(binOnlineJson, "tidakAda") == null);
        // 17c) pembedah clientParams (bin ASLI 711 B memuat objek ini)
        String binCp = "{\"testMode\":1,\"checkTime\":300,"
                + "\"clientParams\":{\"hideList\":[\"1.99\",\"2.99\"],"
                + "\"supportLang\":[{\"languageName\":\"English\",\"lang\":\"en\"},"
                + "{\"languageName\":\"Français\",\"lang\":\"fr\"}],"
                + "\"showCurChannel\":\"toFacebook\"}}";
        String akarCp = ConfigAkar.settingBin(binCp, binCp);
        check("0.5.2 akar clientParams: hideList 2 harga",
                akarCp.contains("hideList 2 harga"));
        check("0.5.2 akar clientParams: supportLang 2 bahasa (en, fr)",
                akarCp.contains("supportLang 2 bahasa (en, fr)"));
        check("0.5.2 akar clientParams: showCurChannel toFacebook",
                akarCp.contains("showCurChannel toFacebook"));
        check("0.5.2 akar angka: testMode & checkTime tampil (asli)",
                akarCp.contains("testMode") && akarCp.contains("300"));
        // 17d) pembedah serversetting.json
        String akarSs = ConfigAkar.serversetting(ssAsli, ssBaru);
        check("0.5.2 akar serversetting: loginserver ASLI → klien (tanda →)",
                akarSs.contains("https://login.popoh5.com:610")
                        && akarSs.contains("→") && akarSs.contains("http://127.0.0.1:8610"));
        check("0.5.2 akar serversetting: requireSDK ditandai (asli)",
                akarSs.contains("requireSDK") && akarSs.contains("(asli)"));
        check("0.5.2 akar serversetting: enkripsi disebut (JSON polos)",
                akarSs.contains("JSON polos"));
        // 17e) gerbang pertama & terakhir dari daftar server ASLI (passthrough)
        String ackGw = "{\"serverList\":["
                + "{\"serverId\":\"1\",\"url\":\"https://s1-bs.popoh5.com:8001\"},"
                + "{\"serverId\":\"2\",\"url\":\"https://s2-bs.popoh5.com:8002\"},"
                + "{\"serverId\":\"2109\",\"url\":\"https://s2109-bs.popoh5.com:8061\"}]}";
        check("0.6.0 gerbang: pertama/terakhir URL PENUH (format baru — tanpa elipsis)",
                "pertama https://s1-bs.popoh5.com:8001 · terakhir https://s2109-bs.popoh5.com:8061"
                        .equals(LoginServer.firstAndLastGateway(ackGw)));
        check("0.5.2 gerbang: satu url saja → tanpa tanda …",
                "https://s1-bs.popoh5.com:8001".equals(
                        LoginServer.firstAndLastGateway("{\"serverList\":[{\"url\":\"https://s1-bs.popoh5.com:8001\"}]}")));
        check("0.5.2 gerbang: tanpa url = null (tidak meledak)",
                LoginServer.firstAndLastGateway("{}") == null
                        && LoginServer.firstAndLastGateway(null) == null);

        // ---------------- TAHAP 18: DUA JALUR — CONFIG OFFLINE MANUAL (0.5.3 —
        // owner: "100% beda jalur!! offline dan online .. config offline murni
        // disiapkan manual" + "online tebaca itu cuma jadikan jejak arah bukan
        // untuk dijadikan config asli ke offline") ----------------
        // 18a) daftar server HANYA dari config-offline/ — snapshot config/ TIDAK
        //      dibaca lagi (uji: config/ memuat popoh5, jawaban tetap lokal).
        String daftar = LoginData.serverListData();
        check("0.5.3 daftar server: SATU entri manual (Server Lokal)",
                daftar.contains("Server Lokal") && LoginData.serverCount() == 1);
        check("0.5.3 daftar server: url = 127.0.0.1:8080 (bukan snapshot config/)",
                daftar.contains("http://127.0.0.1:8080"));
        check("0.5.3 daftar server: snapshot online (config/ popoh5) TIDAK dibaca lagi",
                !daftar.contains("popoh5.com"));
        // 18b) fallback darurat TANPA paket manual tetap LOKAL (tidak pernah popoh5)
        LoginData.init(conf, null);
        String darurat = LoginData.serverListData();
        check("0.5.3 fallback tanpa paket: tetap 1 entri LOKAL 127.0.0.1", 
                darurat.contains("http://127.0.0.1:8080") && darurat.contains("Server Lokal"));
        check("0.5.3 fallback tanpa paket: TIDAK PERNAH popoh5 (dulu s2109-bs.popoh5.com)",
                !darurat.contains("popoh5.com"));
        LoginData.init(conf, off); // pulihkan sumber manual untuk sisa uji
        // 18c) templat paket manual: semua alamat lokal, nol popoh5 (kunci isi)
        String templatBin = "{\"testMode\":1,"
                + "\"url\":\"http://127.0.0.1:8080/bs/index-native.html\","
                + "\"tsUrl\":\"http://127.0.0.1:8080/bs/index-native.html\","
                + "\"update\":\"http://127.0.0.1:8080/bs/upgrade\","
                + "\"checkTime\":300,\"loginServer\":\"http://127.0.0.1\","
                + "\"tsLoginServer\":\"http://127.0.0.1:8610\","
                + "\"loginPort\":8610,\"loginWebPort\":8610}";
        check("0.5.3 templat bin manual: semua endpoint = 127.0.0.1",
                templatBin.indexOf("popoh5") < 0 && templatBin.indexOf("sjmobilegame") < 0
                        && templatBin.contains("http://127.0.0.1"));
        check("0.5.3 templat bin manual: loginServer TANPA port (shell sambung sendiri)",
                templatBin.contains("\"loginServer\":\"http://127.0.0.1\"")
                        && templatBin.contains("\"loginPort\":8610"));
        // 18d) serversetting manual: loginserver lokal + requireSDK false
        String ssManual = "{\n\t\"loginserver\": \"http://127.0.0.1:8610\",\n\t\"requireSDK\": false\n}";
        check("0.5.3 serversetting manual: loginserver lokal, nol popoh5",
                ssManual.contains("http://127.0.0.1:8610") && ssManual.indexOf("popoh5") < 0);
        check("0.5.3 serversetting manual: requireSDK false (dibaca ConfigAkar)",
                "false".equals(SettingBin.getJsonRaw(ssManual, "requireSDK")));
        // 18e) pembedah akar tetap bekerja utk paket manual (asli == klien)
        String akarManual = ConfigAkar.settingBin(templatBin, templatBin);
        check("0.5.3 akar paket manual: bedah tampil, nilai = paket (tanpa tanda →)",
                akarManual.contains("127.0.0.1") && akarManual.contains("(asli)"));

        // ---------------- TAHAP 19: 0.6.0 — LOG UTUH + RELAY SEMUA MODE ----------------
        // (owner: "log itu nyawa kita — tidak boleh ada singkatan; kalau panjang
        //  wajib tampil dan log txt harus menyimpan" + laporan: klik Play mode
        //  ONLINE gak muncul di log — daftar server ASLI passthrough UTUH membuat
        //  klien io.connect LANGSUNG ke server asli → server lokal BUTA)
        // 19a) gerbang: bentuk ter-escape (framing socket.io) tetap terbaca —
        //      sebab lama "url tidak terbaca": pola \"url\":\" gagal di respons
        //      ber-escape. Scanner baru menangani keduanya.
        check("0.6.0 gerbang: satu url → url penuh saja",
                "https://s1-bs.popoh5.com:8001".equals(
                        LoginServer.firstAndLastGateway("{\"serverList\":[{\"url\":\"https://s1-bs.popoh5.com:8001\"}]}")));
        check("0.6.0 gerbang: bentuk ter-escape (framing socket.io) tetap terbaca",
                "pertama https://s1-bs.popoh5.com:8001 · terakhir https://s2-bs.popoh5.com:8002".equals(
                        LoginServer.firstAndLastGateway(
                                "451-[\"handler.process\",{\\\"data\\\":{\\\"serverList\\\":["
                                        + "{\\\"serverId\\\":\\\"1\\\",\\\"url\\\":\\\"https://s1-bs.popoh5.com:8001\\\"},"
                                        + "{\\\"serverId\\\":\\\"2\\\",\\\"url\\\":\\\"https://s2-bs.popoh5.com:8002\\\"}]}}]")));
        check("0.6.0 gerbang: tanpa url = null (tidak meledak)",
                LoginServer.firstAndLastGateway("{}") == null
                        && LoginServer.firstAndLastGateway(null) == null);
        // 19b) parseServerTable — pasangan (serverId, url ASLI) berurutan utuh
        java.util.ArrayList<String[]> t61 = LoginServer.parseServerTable(ackGw);
        check("0.6.0 tabel daftar: 3 entri (serverId ↔ url ASLI, urut utuh)",
                t61 != null && t61.size() == 3
                        && "1".equals(t61.get(0)[0]) && "https://s1-bs.popoh5.com:8001".equals(t61.get(0)[1])
                        && "2109".equals(t61.get(2)[0]) && "https://s2109-bs.popoh5.com:8061".equals(t61.get(2)[1]));
        check("0.6.0 tabel daftar: bentuk ter-escape terbaca",
                LoginServer.parseServerTable(
                        "{\\\"serverList\\\":[{\\\"serverId\\\":\\\"1\\\",\\\"url\\\":\\\"https://s1-bs.popoh5.com:8001\\\"}]}")
                        != null);
        check("0.6.0 tabel daftar: jumlah tidak cocok → null (passthrough utuh, tetap aman)",
                LoginServer.parseServerTable(
                        "{\"serverList\":[{\"serverId\":\"1\",\"url\":\"https://a\"},{\"serverId\":\"2\"}]}") == null);
        // 19c) rewriteServerListUrls — url entri → relay lokal, field lain UTUH
        String rw61 = LoginServer.rewriteServerListUrls(ackGw);
        check("0.6.0 rewrite: SEMUA url entri → http://127.0.0.1:8080, field lain tak tersentuh",
                rw61 != null && !rw61.contains("s1-bs.popoh5.com") && !rw61.contains("s2109-bs.popoh5.com")
                        && rw61.contains("\"url\":\"http://127.0.0.1:8080\"")
                        && rw61.contains("\"serverId\":\"2109\""));
        check("0.6.0 rewrite: bentuk ter-escape ikut ditulis-ulang",
                LoginServer.rewriteServerListUrls(
                        "{\\\"serverList\\\":[{\\\"url\\\":\\\"https://s1-bs.popoh5.com:8001\\\"}]}")
                        .contains("\\\"url\\\":\\\"http://127.0.0.1:8080\\\""));
        // 19d) upstream relay semua mode: isian owner menang; ONLINE pakai tabel
        try {
            new File(conf, "game-upstream.txt").delete();
        } catch (Throwable ignored) {
        }
        GameServer.setOnlineUpstreamTable(t61);
        GameServer.setOnlineChosenId("2109");
        String[] pick61 = GameServer.resolveGameUpstream("online");
        check("0.6.0 upstream ONLINE: serverId pilihan pemain (2109) → url ASLI penuh",
                pick61 != null && "https://s2109-bs.popoh5.com:8061".equals(pick61[0])
                        && pick61[1].contains("pilihan pemain"));
        GameServer.setOnlineChosenId(null);
        String[] pick62 = GameServer.resolveGameUpstream("online");
        check("0.6.0 upstream ONLINE: tanpa pilihan → entri pertama (url ASLI penuh)",
                pick62 != null && "https://s1-bs.popoh5.com:8001".equals(pick62[0])
                        && pick62[1].contains("entri pertama"));
        GameServer.setOnlineUpstreamTable(new java.util.ArrayList<String[]>());
        String[] pick63 = GameServer.resolveGameUpstream("online");
        check("0.6.0 upstream ONLINE: tabel kosong → null (❌ Play berhenti jelas, bukan loading diam)",
                pick63 == null);

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
