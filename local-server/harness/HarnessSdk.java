import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import com.db.local.server.Paths;
import com.db.local.server.SdkData;
import com.db.local.server.SdkServer;
import com.db.local.server.SettingBin;
import com.db.local.server.StatusStore;
import com.db.local.server.Xxtea;

/**
 * HARNESS 0.4.6 — MODEL FINAL LOG (grup batch) + regresi penuh 0.4.2–0.4.5.
 *
 * Baru 0.0.3 — STRUKTUR FOLDER JELAS (keluhan owner: "berantakan juga struktur
 * folder pembacaan file di folder server Android/data.. struktur folder local
 * di android/data harus juga jelas"): TAHAP 1a — Paths sumber tunggal path;
 * migrasi layout lama (config/dragonball.db → db/, status.json + www.stamp →
 * state/) tanpa menimpa; PETA.txt di root (peta folder, idempoten); whitelist
 * Cleaner 0.0.3 per-folder (config/servers.json mati + capture/ mati DIHAPUS,
 * PETA.txt + db/ + state/ dilindungi); StatusStore 0.0.3.
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
        // 0.0.2 — server.json SENGaja bentuk LAMA (loginPort 8610, tanpa
        // "sdkPort") untuk menguji MIGRASI pemisahan port: SDK tetap 8610,
        // login socket digeser otomatis ke 8611 (lihat TAHAP 3).
        write(new File(conf, "server.json"),
                "{\"mode\":\"local\",\"gamePort\":8080,\"debugPort\":4000,\"loginPort\":8610,\"useSdk\":false}");
        write(new File(conf, "servers.json"),
                "{\"errorCode\":0,\"serverList\":[{\"serverId\":\"2109\",\"url\":\"https://s2109-bs.popoh5.com:8061\","
                        + "\"name\":\"S2109\",\"online\":true,\"new\":false,\"hot\":false,\"openDate\":\"\",\"offlineReason\":\"\",\"merge\":false,\"mergeTo\":\"\"}],\"history\":[]}");

        // ---------------- 0.5.3: paket CONFIG OFFLINE MANUAL (folder config-offline/) ----------------
        File off = new File(root, "config-offline");
        off.mkdirs();
        // 0.0.2 — field KONTRAK KLIEN saja (tanpa upstream/urlInner/chatInner)
        write(new File(off, "servers.json"),
                "{\"errorCode\":0,\"serverList\":[{\"serverId\":\"1\",\"url\":\"http://127.0.0.1:8080\","
                        + "\"new\":true,\"hot\":true,\"name\":\"Server Lokal\",\"online\":true,\"openDate\":\"\","
                        + "\"offlineReason\":\"\",\"merge\":false,\"mergeTo\":\"\"}],\"history\":[]}");

        // ---------------- TAHAP 1a: MIGRASI STRUKTUR FOLDER (0.0.3 — keluhan owner
        // "struktur folder pembacaan file di folder server Android/data.. berantakan",
        // "struktur folder local di android/data harus juga jelas") ----------------
        // Simulasi perangkat 0.0.2: dragonball.db + status.json + www.stamp menumpuk
        // di config/. Paths.migrateFromLegacyLayout() memindahkan ke tempat yang
        // benar SEBELUM Db.boot & Cleaner — data tidak hilang, tidak menimpa.
        File legacySim = new File(root, "legacy-sim");
        File lconf = new File(legacySim, "config");
        lconf.mkdirs();
        write(new File(lconf, "dragonball.db"), "FAKE-DB-0.0.2");
        write(new File(lconf, "dragonball.db-journal"), "J");
        write(new File(lconf, "status.json"), "{}");
        write(new File(lconf, "www.stamp"), "mirror-version=4\n");
        Paths.init(legacySim); // Paths menunjuk root simulasi
        String moved = Paths.migrateFromLegacyLayout();
        check("0.0.3 migrasi: config/dragonball.db → db/ (data pemain terpisah dari config)",
                new File(legacySim, "db/dragonball.db").exists()
                        && !new File(lconf, "dragonball.db").exists());
        check("0.0.3 migrasi: jurnal SQLite ikut pindah ke db/",
                new File(legacySim, "db/dragonball.db-journal").exists());
        check("0.0.3 migrasi: config/status.json → state/",
                new File(legacySim, "state/status.json").exists());
        check("0.0.3 migrasi: config/www.stamp → state/",
                new File(legacySim, "state/www.stamp").exists());
        check("0.0.3 migrasi: ringkasan menyebut SEMUA pindahan (tidak diam-diam)",
                moved.contains("config/dragonball.db → db/dragonball.db")
                        && moved.contains("config/status.json → state/status.json")
                        && moved.contains("config/www.stamp → state/www.stamp"));
        write(new File(lconf, "status.json"), "LAMA");
        Paths.migrateFromLegacyLayout();
        check("0.0.3 migrasi aman: target sudah ada → sumber TIDAK menimpa (data baru menang)",
                "LAMA".equals(readAll(new java.io.FileInputStream(new File(lconf, "status.json"))))
                        && "{}".equals(readAll(new java.io.FileInputStream(
                                new File(legacySim, "state/status.json")))));
        Paths.init(root); // kembali ke root utama harness

        // PETA.txt — peta folder di perangkat (0.0.3: "struktur folder local di
        // android/data harus juga jelas") — dibuat bila hilang/versi berubah
        String petaState = Paths.writePetaIfStale();
        check("0.0.3 PETA.txt ditulis di root server (peta folder untuk pemilik)",
                "ditulis".equals(petaState) && Paths.petaTxt().exists());
        String petaTxt = readAll(new java.io.FileInputStream(Paths.petaTxt()));
        check("0.0.3 PETA.txt menjelaskan SATU makna per folder (config/db/state/www/config-offline/logs/tmp)",
                petaTxt.contains("config/") && petaTxt.contains("db/") && petaTxt.contains("state/")
                        && petaTxt.contains("www/") && petaTxt.contains("config-offline/")
                        && petaTxt.contains("logs/") && petaTxt.contains("tmp/")
                        && petaTxt.contains("Peta versi 1"));
        check("0.0.3 PETA.txt idempoten — tidak ditulis-ulang bila tetap",
                "sudah".equals(Paths.writePetaIfStale()));

        // ---------------- TAHAP 1: MIGRASI txt lama → dragonball.db ----------------
        write(new File(conf, "local-user.txt"), LEGACY_GUEST + "\n");
        write(new File(conf, "accounts.txt"),
                LEGACY_ACC + "\t" + LEGACY_ACC_PASS + "\t2026-11-02 21:00:00\t2026-11-02 22:00:00\t7\n");
        write(new File(conf, "sdk-guests.txt"), "# tanpa baris (kondisi 0.4.2 segel)\n");
        write(new File(conf, "sdk-session.txt"), LEGACY_TOKEN + "\t" + LEGACY_GUEST + "\t1\t2026-11-02 21:31:00\n");
        write(new File(conf, "sdk-last.txt"), LEGACY_GUEST + "\t" + LEGACY_GUEST + "\t2026-11-02 21:31:05\n");

        Db.boot(new File(root, "db"), conf); // 0.0.3 — DB di db/, txt warisan dari config/
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
        Db.boot(new File(root, "db"), conf);
        check("REBOOT: guest pemilik tetap (dulu txt terhapus Cleaner — bug 0.4.2)",
                LEGACY_GUEST.equals(LoginData.localUserId()));
        check("REBOOT: akun tetap", LoginData.accountPassword(LEGACY_ACC) != null);
        check("REBOOT: sesi tetap", Db.i().session(LEGACY_TOKEN) != null);
        check("REBOOT: tidak ada impor dobel (jumlah akun tetap 1)", LoginData.accountCount() == 1);

        // ---------------- TAHAP 3: server SDK + server login TERPISAH + seluruh
        // jalur SDK (regresi 0.4.2) — 0.0.2 PEMISAHAN PORT ----------------
        Config.load(root); // server.json LEGACY (loginPort 8610, tanpa sdkPort)
        // 0.0.2 migrasi: sdkPort==loginPort → SDK mempertahankan 8610 (QHinfo),
        // login socket digeser 8611 — keputusan terekam log, tanpa asumsi diam.
        check("0.0.2 migrasi server.json legacy: sdkPort tetap 8610 (QHinfo menunjuk ke sana)",
                Config.sdkPort() == 8610);
        check("0.0.2 migrasi server.json legacy: loginPort digeser otomatis ke 8611 (tidak bentrok)",
                Config.loginPort() == 8611);
        SdkServer ssx = new SdkServer("127.0.0.1", Config.sdkPort());
        ssx.start(500, false);
        LoginServer ls = new LoginServer("127.0.0.1", Config.loginPort());
        ls.start(500, false);
        Thread.sleep(700);

        String base = "http://127.0.0.1:8610";   // SDK HTTP murni (SdkServer)
        String lbase = "http://127.0.0.1:8611";  // login socket murni (LoginServer)
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

        // 10) socket.io handshake + GetServerList (regresi 0.4.0) — 0.0.2: di port LOGIN 8611
        r = get(lbase + "/socket.io/?EIO=3&transport=polling&t=1");
        check("handshake engine.io framing 0{...}40 (port login 8611)", r.contains(":0{") && r.contains(":40"));
        r = get(base + "/socket.io/?EIO=3&transport=polling&t=1");
        check("0.0.2 pemisahan: port SDK 8610 TIDAK melayani /socket.io/ (satu port satu peran)",
                !r.contains(":0{") && !r.contains(":40"));
        r = get(lbase + "/v1/system/init");
        check("0.0.2 pemisahan: port LOGIN 8611 menolak /v1/* dengan penunjuk jalan ke 8610",
                r.contains("pindah ke port 8610"));
        frame = "420[\"handler.process\",{\"action\":\"GetServerList\"}]";
        r = socketIoPost(frame);
        check("GetServerList 1 server (paket MANUAL)", r.contains("serverId"));
        check("GetServerList OFFLINE: url entri = 127.0.0.1:8080 (Play masuk lokal)",
                r.contains("http://127.0.0.1:8080"));
        check("GetServerList OFFLINE: TIDAK ada url popoh5 di jawaban",
                !r.contains("popoh5.com"));
        check("0.0.2 paket serverList: field kontrak klien saja (tanpa upstream/urlInner/chatInner)",
                !r.contains("\"upstream\"") && !r.contains("urlInner") && !r.contains("chatInner"));

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
        // 2-arg — delegasi penuh ke Config (setelah migrasi: login 8611 · SDK 8610)
        // + userid sesi SDK dari dragonball.db/jaring memori.
        byte[] out = SettingBin.rewrittenLocal(binEnc, 8080);
        String outJson = new String(xor(out), "UTF-8");
        check("bin loginServer TANPA port (FIX 0.4.2)",
                outJson.contains("\"loginServer\": \"http://127.0.0.1\"")
                        && outJson.contains("\"loginWebPort\": 8610")
                        && !outJson.contains("\"loginServer\": \"http://127.0.0.1:"));
        check("0.0.2 bin loginPort 8611 (socket) + loginWebPort 8610 (loginchecknative → SDK) TERPISAH",
                outJson.contains("\"loginPort\": 8611") && outJson.contains("\"loginWebPort\": 8610"));
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
        // 12) whitelist Cleaner 0.0.3 — struktur JELAS: config/ murni pengaturan,
        //     db/ data pemain, state/ keadaan runtime; file mati DIHAPUS dengan
        //     nama+ukuran di log (bukti audit 0.0.3):
        //     - config/servers.json (snapshot era 0.4.4) TIDAK dibaca lagi sejak
        //       0.5.3 (serverlist hanya paket manual — tes 18a) → dihapus;
        //     - capture/ mati sejak 0.5.4 (REKAM dihapus total) → sisa disapu;
        //     - PETA.txt + db/dragonball.db + state/* DILINDUNGI.
        java.lang.reflect.Method mClean = Class.forName("com.db.local.server.Cleaner")
                .getDeclaredMethod("clean", File.class);
        Object cr = mClean.invoke(null, root);
        String cleaned = cr.getClass().getField("names").get(cr).toString();
        check("Cleaner 0.0.3: config/servers.json MATI (snapshot 0.4.4) dihapus — dua servers.json sumber bingung",
                cleaned.contains("config/servers.json") && !new File(root, "config/servers.json").exists());
        check("Cleaner 0.0.3: *.imported di config/ disapu (isinya sudah aman di SQLite)",
                cleaned.contains("config/local-user.txt.imported"));
        // db/dragonball.db dilindungi — bila engine SQLite sandbox tak membuat file
        // (jaring memori), buat penanda sendiri agar uji whitelist deterministik
        File dbfGuard = new File(new File(root, "db"), "dragonball.db");
        if (!dbfGuard.exists()) {
            write(dbfGuard, "DB");
        }
        check("Cleaner: dragonball.db tetap dilindungi (kini db/ — data pemain)",
                !cleaned.contains("db/dragonball.db") && dbfGuard.exists());
        check("Cleaner: folder config-offline/ TIDAK dihapus (paket MANUAL dilindungi 0.5.3)",
                new File(root, "config-offline").exists()
                        && new File(root, "config-offline/servers.json").exists());
        // sisa capture/ era REKAM (mati 0.5.4) — dulu menetap di perangkat selamanya
        File cap = new File(root, "capture");
        cap.mkdirs();
        write(new File(cap, "rekam.jsonl"), "[]");
        // file state/ juga dibuat utk membuktikan dilindungi
        File stateDir = new File(root, "state");
        stateDir.mkdirs();
        write(new File(stateDir, "status.json"), "{}");
        write(new File(stateDir, "www.stamp"), "mirror-version=4\n");
        cr = mClean.invoke(null, root);
        cleaned = cr.getClass().getField("names").get(cr).toString();
        check("Cleaner 0.0.3: capture/ sisa era REKAM DIHAPUS otomatis (dulu menetap diam-diam)",
                !new File(root, "capture").exists());
        check("Cleaner 0.0.3: PETA.txt dilindungi (peta folder untuk pemilik)",
                Paths.petaTxt().exists());
        check("Cleaner 0.0.3: state/status.json + state/www.stamp dilindungi (keadaan runtime)",
                new File(root, "state/status.json").exists()
                        && new File(root, "state/www.stamp").exists());

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
        byte[] binOnlineOut = SettingBin.rewrittenOnline(binOnlineEnc, 8080, 8611, 8610);
        String binOnlinePlain = new String(xor(binOnlineOut), "UTF-8");
        check("0.5.1 ONLINE bin: url H5 → port game lokal (8080)",
                "http://127.0.0.1:8080/bs/index-native.html?v=9".equals(jsonStr(binOnlinePlain, "url")));
        check("0.5.1 ONLINE bin: update → port game lokal (H5 lewat server — serversetting ikut)",
                "http://127.0.0.1:8080/bs/upgrade".equals(jsonStr(binOnlinePlain, "update")));
        check("0.5.1 ONLINE bin: loginServer → server login lokal (TANPA port — gaya 0.4.2)",
                "http://127.0.0.1".equals(jsonStr(binOnlinePlain, "loginServer")));
        check("0.0.2 ONLINE bin: loginPort 8611 (socket) + loginWebPort 8610 (SDK) TERPISAH",
                "8611".equals(jsonStr(binOnlinePlain, "loginPort"))
                        && "8610".equals(jsonStr(binOnlinePlain, "loginWebPort")));
        check("0.0.2 ONLINE bin: tsLoginServer → server login lokal :8611",
                "http://127.0.0.1:8611".equals(jsonStr(binOnlinePlain, "tsLoginServer")));
        check("0.5.1 ONLINE bin: tsUrl tetap ASLI (varian uji — persis gaya LOCAL)",
                "http://dragon.sjmobilegame.com:82/bs103/index-native.html"
                        .equals(jsonStr(binOnlinePlain, "tsUrl")));
        check("0.5.1 ONLINE bin: TIDAK ada userid di url H5 (identitas boot = mekanisme shell asli)",
                !binOnlinePlain.contains("userid="));
        check("0.5.1 ONLINE bin: hasil tetap bin terenkripsi valid (bukan JSON polos)",
                !new String(binOnlineOut).contains("loginServer"));
        // 16b) serversetting.json mode ONLINE (GameServer passthrough): loginserver → lokal
        String ssAsli = "{\n\t\"loginserver\": \"https://login.popoh5.com:610\",\n\t\"requireSDK\": false\n}";
        String ssBaru = SettingBin.setJsonValue(ssAsli, "loginserver", "http://127.0.0.1:8611");
        check("0.5.1 ONLINE serversetting: loginserver → http://127.0.0.1:8611 (socket login)",
                ssBaru.contains("http://127.0.0.1:8611") && !ssBaru.contains("login.popoh5.com"));
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
        check("0.5.2 akar bin: port 610 → 8611 (socket) & 8610 (SDK) terlihat",
                akar.contains("610") && akar.contains("8611") && akar.contains("8610"));
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
                        && akarSs.contains("→") && akarSs.contains("http://127.0.0.1:8611"));
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
        // 0.0.2 — loginPort 8611 (socket) + loginWebPort 8610 (loginchecknative → SDK)
        String templatBin = "{\"testMode\":1,"
                + "\"url\":\"http://127.0.0.1:8080/bs/index-native.html\","
                + "\"tsUrl\":\"http://127.0.0.1:8080/bs/index-native.html\","
                + "\"update\":\"http://127.0.0.1:8080/bs/upgrade\","
                + "\"checkTime\":300,\"loginServer\":\"http://127.0.0.1\","
                + "\"tsLoginServer\":\"http://127.0.0.1:8611\","
                + "\"loginPort\":8611,\"loginWebPort\":8610}";
        check("0.5.3 templat bin manual: semua endpoint = 127.0.0.1",
                templatBin.indexOf("popoh5") < 0 && templatBin.indexOf("sjmobilegame") < 0
                        && templatBin.contains("http://127.0.0.1"));
        check("0.5.3 templat bin manual: loginServer TANPA port + loginPort 8611 TERPISAH loginWebPort 8610",
                templatBin.contains("\"loginServer\":\"http://127.0.0.1\"")
                        && templatBin.contains("\"loginPort\":8611")
                        && templatBin.contains("\"loginWebPort\":8610"));
        // 18d) serversetting manual: loginserver lokal (socket login 8611) + requireSDK false
        String ssManual = "{\n\t\"loginserver\": \"http://127.0.0.1:8611\",\n\t\"requireSDK\": false\n}";
        check("0.5.3 serversetting manual: loginserver lokal :8611, nol popoh5",
                ssManual.contains("http://127.0.0.1:8611") && ssManual.indexOf("popoh5") < 0);
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

        // ---------------- TAHAP 20: 0.6.1 — SUB JUDUL JALUR PER BARIS ----------------
        // (owner: "sub judul pada log (Online / local) gk ada jelas bikin
        //  bingung" — timeline campur sengaja, tiap baris wajib jelas jalurnya;
        //  human.log txt kini membawa tag [ICON|LOCAL]/[ICON·|ONLINE])
        File logDir20 = new File(root, "logs-harness");
        rmrf(logDir20);
        HumanLog.init(logDir20);
        HumanLog.setMode("local");
        HumanLog.ev("✅", "uji tag jalur local", "Detail: satu baris");
        HumanLog.setMode("online");
        HumanLog.evTech("ℹ️", "uji tag jalur online", null);
        HumanLog.ev("📡", "uji tag jalur online penting", "Jalur    : ONLINE\nSesi     : uji");
        String txt20 = new String(java.nio.file.Files.readAllBytes(
                new File(logDir20, "human.log").toPath()), "UTF-8");
        check("0.6.1 human.log: baris penting bawa tag [✅|LOCAL]",
                txt20.contains("[✅|LOCAL] uji tag jalur local"));
        check("0.6.1 human.log: baris teknis bawa tag [ℹ️·|ONLINE]",
                txt20.contains("[ℹ️·|ONLINE] uji tag jalur online"));
        check("0.6.1 human.log: rincian ikut utuh setelah tag",
                txt20.contains("uji tag jalur local") && txt20.contains("Detail: satu baris")
                        && txt20.contains("├─ Jalur    : ONLINE"));
        HumanLog.setMode("");

        // ---------------- TAHAP 21: 0.6.2 — FRAMING ENGINE.IO UTUH ----------------
        // (laporan owner 2026-11-13: "Aplikasi jelas bermasalah.. SERVERLIST
        //  tidak muncul dan ini seharusnya gak mgkin terjadi di versi online ..
        //  saya coba pakai versi originalnya aplikasi serverlist muncul dengan
        //  baik". Bukti log perangkat 0.6.1: respons ASLI upstream = frame
        //  "618016:430[...]" (prefix 618016 = panjang paket, selisih 0 di REKAM);
        //  tulis-ulang 2111 url memangkas paket → 597094 tanpa memperbarui
        //  prefix → parser klien (decodePayload: "a != s.length → r(w,0,1)")
        //  menolak seluruh payload → ACK tak sampai ke UI → SERVERLIST kosong.)
        String pkt1 = "0{\"sid\":\"abc\"}";
        String pkt2 = "430[{\"ret\":0,\"data\":\"{\\\"serverList\\\":["
                + "{\\\"serverId\\\":\\\"2111\\\",\\\"url\\\":\\\"https://s2111-bs.popoh5.com:8141\\\"},"
                + "{\\\"serverId\\\":\\\"1\\\",\\\"url\\\":\\\"https://s1-bs.popoh5.com:8041\\\"}]}\"}]";
        String pkt3 = "40";
        String body21 = pkt1.length() + ":" + pkt1 + pkt2.length() + ":" + pkt2
                + pkt3.length() + ":" + pkt3;
        String r21 = LoginServer.rewriteServerListFramed(body21);
        check("0.6.2 framed rewrite: body berubah (url kena, bukan null)",
                r21 != null && !r21.equals(body21));
        check("0.6.2 framed rewrite: url ASLI hilang dari paket terlayani",
                r21 != null && !r21.contains("s2111-bs.popoh5.com") && !r21.contains("s1-bs.popoh5.com")
                        && r21.contains("http://127.0.0.1:8080"));
        check("0.6.2 framed rewrite: serverId (bentuk ter-escape di wire) & data ACK tidak tersentuh",
                r21 != null && r21.contains("\\\"serverId\\\":\\\"2111\\\"")
                        && r21.contains("\\\"url\\\":\\\"http://127.0.0.1:8080\\\"")
                        && r21.contains("\"ret\":0"));
        // parser klien simulasi: tiap frame prefix == panjang paket yang dibaca
        boolean frame21 = true;
        int pos21 = 0;
        int frames21 = 0;
        while (pos21 < r21.length()) {
            int c21 = r21.indexOf(':', pos21);
            if (c21 < 0) { frame21 = false; break; }
            int l21 = Integer.parseInt(r21.substring(pos21, c21));
            if (c21 + 1 + l21 > r21.length()) { frame21 = false; break; }
            pos21 = c21 + 1 + l21;
            frames21++;
        }
        check("0.6.2 framed rewrite: prefix frame == panjang paket (semantik substr parser klien)"
                        + " — 3 frame utuh",
                frame21 && frames21 == 3 && pos21 == r21.length());
        // frame daftar server: prefix HARUS berubah (paket memangkas setelah tulis-ulang)
        String pkt2Rewritten = LoginServer.rewriteServerListUrls(pkt2);
        check("0.6.2 framed rewrite: prefix frame daftar server DIHITUNG ULANG ("
                        + pkt2.length() + " → " + pkt2Rewritten.length() + ", dulu prefix mati)",
                r21 != null && r21.contains(pkt2Rewritten.length() + ":430[")
                        && !r21.contains(pkt2.length() + ":430["));
        // kasus tepi — format tak dikenal & tanpa url = biarkan ASLI (null)
        check("0.6.2 framed rewrite: framing rusak → null (passthrough ASLI, tetap aman)",
                LoginServer.rewriteServerListFramed("abc:def") == null
                        && LoginServer.rewriteServerListFramed("61:430[{\"a\":1}]") == null
                        && LoginServer.rewriteServerListFramed(null) == null
                        && LoginServer.rewriteServerListFramed("") == null);
        check("0.6.2 framed rewrite: frame TERPOTONG (5:40ab — paket 4 < prefix 5) → null"
                        + " (passthrough ASLI, tetap aman)",
                LoginServer.rewriteServerListFramed("5:40ab") == null);
        check("0.6.2 framed rewrite: dua frame utuh tanpa url → null (tidak ada perubahan = ASLI)",
                LoginServer.rewriteServerListFramed("3:402:xx") == null);
        // bukti cacat 0.6.1: rewrite LAMA pada frame = prefix mati
        String pktLama = "430[{\"url\":\"https://s1-bs.popoh5.com:8041\"}]";
        String frameLama = pktLama.length() + ":" + pktLama;
        String rwLama = LoginServer.rewriteServerListUrls(frameLama);
        int prefixLama61 = Integer.parseInt(rwLama.substring(0, rwLama.indexOf(':')));
        int paketBaru61 = rwLama.length() - rwLama.indexOf(':') - 1;
        check("0.6.2 bukti cacat 0.6.1: rewrite lama memangkas paket (" + pktLama.length()
                        + " → " + paketBaru61 + ") tapi prefix tetap " + prefixLama61
                        + " → parser klien menolak (a != s.length)",
                rwLama != null && prefixLama61 == pktLama.length() && paketBaru61 < pktLama.length()
                        && !rwLama.contains("s1-bs.popoh5.com"));

        // ---------------- TAHAP 19: KOREKSI KESALAHAN FATAL UPSTREAM (owner
        // 2026-11-14: "KESALAHAN FATAL TIDAK BISA DI TOLERANSI! ANDA AMBIL
        // MENTAH CONFIG DARI SERVER ASLINYA!! .. CONFIG ONLINE ITU CUMA JEJAK
        // AGAR KITA GAK TERSESAT.. BUKAN MALAH MURNI DI PAKAI MENTAH-MENTAH")
        // — relay hanya isian pemilik; paket manual bebas server asli ----------------
        // 19a) tanpa isian pemilik: LOCAL tidak punya tujuan relay — TIDAK ada
        //      kontak keluar (bukan fallback ke server asli).
        String[] ex19 = LoginData.gameUpstreamEx();
        check("19 koreksi: tanpa game-upstream.txt → sumber kosong (bukan servers.json)",
                ex19[0].length() == 0 && ex19[1].length() == 0);
        check("19 koreksi: resolveGameUpstream LOCAL tanpa isian = null"
                        + " (TIDAK menyentuh server asli otomatis)",
                GameServer.resolveGameUpstream("local") == null);
        // 19b) isian pemilik = SATU-SATUNYA sumber relay (dasbor seksi CONFIG / file).
        write(new File(conf, "game-upstream.txt"), "https://127.0.0.1:9999\n");
        String[] ex19b = LoginData.gameUpstreamEx();
        check("19 koreksi: game-upstream.txt (isian pemilik) = satu-satunya sumber relay",
                "https://127.0.0.1:9999".equals(ex19b[0]) && "file".equals(ex19b[1]));
        check("19 koreksi: resolveGameUpstream LOCAL dgn isian pemilik = file pemilik",
                GameServer.resolveGameUpstream("local") != null
                        && "https://127.0.0.1:9999".equals(GameServer.resolveGameUpstream("local")[0]));
        new File(conf, "game-upstream.txt").delete();
        // 19c) PAKET berisi upstream pun (simulasi paket pelanggaran 0.6.3) — tetap
        //      TIDAK dibaca: field "upstream" paket manual bukan tujuan relay lagi.
        write(new File(off, "servers.json"),
                "{\"errorCode\":0,\"serverList\":[{\"serverId\":\"1\",\"url\":\"http://127.0.0.1:8080\","
                        + "\"name\":\"Server Lokal\",\"upstream\":\"https://s2111-bs.popoh5.com:8141\"}],\"history\":[]}");
        String[] ex19c = LoginData.gameUpstreamEx();
        check("19 koreksi: paket berisi upstream pun TIDAK dibaca (relay hanya isian pemilik;"
                        + " dulu 0.6.3 membaca field ini = kesalahan fatal)",
                ex19c[0].length() == 0 && ex19c[1].length() == 0);
        // 19d) PAKET ASLI di repo (bila harness dijalankan di dalam repo) wajib bebas
        //      host server asli — paket mentah dari online tidak boleh lolos terbit.
        if (args.length > 1 && args[1] != null && args[1].length() > 0) {
            File offPaket = new File(new File(new File(args[1], "local-server"),
                    "assets"), "server");
            offPaket = new File(offPaket, "config-offline");
            if (offPaket.isDirectory()) {
                String[] dilarang = {"popoh5.com", "sjmobilegame.com", "sjflaregame.com",
                        "t4game.com", "parajoymobile.com"};
                boolean bersih = true;
                String kena = "";
                File[] isinya = offPaket.listFiles();
                if (isinya != null) {
                    java.util.Arrays.sort(isinya);
                    for (File f : isinya) {
                        if (!f.isFile()) {
                            continue;
                        }
                        String isi = readAll(new FileInputStream(f));
                        for (String h : dilarang) {
                            if (isi.contains(h)) {
                                bersih = false;
                                kena = f.getName() + " memuat " + h;
                            }
                        }
                    }
                }
                check("19 koreksi: paket ASLI repo bebas host server asli"
                                + " (config online cuma jejak arah, bukan sumber nilai)" + (bersih ? "" : " — " + kena),
                        bersih);
            }
        }

        // ---------------- TAHAP 22: MESIN GAME LOKAL (rebuild handcraft —
        // owner: "local kita ini rebuild bukan hasil jiplak dari online") —
        // kontrak protokol dari KODE KLIEN SENDIRI (main.min TEA/saveUserData),
        // nilai dari dragonball.db; Play LOKAL hidup tanpa relay ----------------
        // 22a) XXTEA — vektor silang: kode TEA klien dieksekusi (bun) →
        //      implementasi Java wajib cocok DUA ARAH.
        check("22a XXTEA: dekripsi vektor klien #1 → uuid asli",
                "b7738641-da52-4493-9107-7739c62d35e9".equals(Xxtea.decrypt(
                        "0HdvUq3yWlKWp8hnC8piuJ9C9EPlIKTLX0X0c5QFOXBntLSC", "verification")));
        check("22a XXTEA: enkripsi uuid #1 → persis vektor klien",
                "0HdvUq3yWlKWp8hnC8piuJ9C9EPlIKTLX0X0c5QFOXBntLSC".equals(Xxtea.encrypt(
                        "b7738641-da52-4493-9107-7739c62d35e9", "verification")));
        check("22a XXTEA: dekripsi vektor klien #2 → uuid asli",
                "0fd1408f-ac6c-474d-8469-9806c0509745".equals(Xxtea.decrypt(
                        "Dq7lZP0R0qOV0uo1sSVolflGww5jrDlF3/ezf4scdkYsedhl", "verification")));
        check("22a XXTEA: enkripsi uuid #2 → persis vektor klien",
                "Dq7lZP0R0qOV0uo1sSVolflGww5jrDlF3/ezf4scdkYsedhl".equals(Xxtea.encrypt(
                        "0fd1408f-ac6c-474d-8469-9806c0509745", "verification")));
        // 22b) mesin game via HTTP NYATA — jalur persis klik Play:
        //      GameServer :8080 LOCAL tanpa isian upstream → GameEngine.
        File www22 = new File(root, "www");
        www22.mkdirs();
        GameServer gs22 = new GameServer(null, "127.0.0.1", 8080, www22);
        gs22.start(500, false);
        Thread.sleep(700);
        String gbase = "http://127.0.0.1:8080";
        String hs22 = get(gbase + "/socket.io/?EIO=3&transport=polling&t=h1");
        String sid22 = jsonStr(hs22, "sid");
        check("22b handshake engine.io v3: sid dibuat server lokal",
                sid22 != null && sid22.length() > 0);
        check("22b handshake: open 0 + socket.io CONNECT 40 (framing v3)",
                hs22.contains("0{\"sid\"") && hs22.contains(":40"));
        String pollA = get(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22);
        String markV = "42[\"verify\",\"";
        int vi = pollA.indexOf(markV);
        String token22 = vi < 0 ? "" : pollA.substring(vi + markV.length(),
                pollA.indexOf("\"]", vi));
        check("22b mesin mengirim event verify dgn token UUID (36)",
                token22.length() == 36);
        // klien simulasi: XXTEA(token, "verification") → 421["verify","<enc>"]
        String enc22 = Xxtea.encrypt(token22, "verification");
        String pktVerify = "421[\"verify\",\"" + enc22 + "\"]";
        post(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22,
                pktVerify.length() + ":" + pktVerify);
        String pollB = get(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22);
        check("22b verify sah: ack ret 0 (protokol lokal hidup)",
                pollB.contains("431[{\"ret\":0}]"));
        // verify SALAH (token lain) — diteruskan ack ret 1 (jujur, tidak diam)
        String pktBad = "421[\"verify\",\"" + Xxtea.encrypt(
                "00000000-0000-0000-0000-000000000000", "verification") + "\"]";
        post(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22,
                pktBad.length() + ":" + pktBad);
        String pollB2 = get(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22);
        check("22b verify salah: ack ret 1 (bukti dekripsi benar-benar dicocokkan)",
                pollB2.contains("\"ret\":1"));
        // enterGame — model pemain dari dragonball.db
        String pktEg = "422[\"handler.process\",{\"type\":\"user\",\"action\":\"enterGame\","
                + "\"loginToken\":\"tokenuji123\",\"userId\":\"" + LEGACY_GUEST + "\","
                + "\"serverId\":1,\"version\":\"1.0\",\"language\":\"en\","
                + "\"gameVersion\":\"20260929_180158-EN\"}]";
        post(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22,
                pktEg.length() + ":" + pktEg);
        String pollC = get(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22);
        check("22b enterGame: ack ret 0 + sinkron waktu (serverTime/server0Time)",
                pollC.contains("\"ret\":0") && pollC.contains("\"serverTime\":")
                        && pollC.contains("\"server0Time\":"));
        check("22b enterGame: model wajib klien utuh (currency/user/hangup/summon/"
                        + "totalProps/superSkill/heros/scheduleInfo/dragonEquiped)",
                pollC.contains("currency") && pollC.contains("_nickName")
                        && pollC.contains("hangup") && pollC.contains("summon")
                        && pollC.contains("totalProps") && pollC.contains("superSkill")
                        && pollC.contains("\"heros") && pollC.contains("scheduleInfo")
                        && pollC.contains("dragonEquiped"));
        check("22b enterGame: Notify battleMedalRefresh push lokal (kontrak klien)",
                pollC.contains("battleMedalRefresh") && pollC.contains("\"ret\":\"SUCCESS\""));
        String model22 = Db.i().kv("game_player:" + LEGACY_GUEST);
        check("22b pemain tersimpan dragonball.db (kv game_player)", model22 != null
                && jsonStr(model22, "_id") != null && jsonStr(model22, "_id").length() == 36);
        // aksi lanjutan (peta alur) — SEMUA dijawab lokal ret 0 dgn kontrak masing-masing
        String[] follow22 = {
                "423[\"handler.process\",{\"type\":\"teamDungeonGame\",\"action\":\"queryMyRecord\",\"userId\":\"" + LEGACY_GUEST + "\"}]",
                "424[\"handler.process\",{\"type\":\"friend\",\"action\":\"friendServerAction\",\"relayAction\":\"queryFriends\",\"userId\":\"" + LEGACY_GUEST + "\",\"version\":\"1.0\"}]",
                "425[\"handler.process\",{\"type\":\"friend\",\"action\":\"friendServerAction\",\"relayAction\":\"queryBlackList\",\"userId\":\"" + LEGACY_GUEST + "\",\"version\":\"1.0\"}]",
                "426[\"handler.process\",{\"type\":\"user\",\"action\":\"registChat\",\"userId\":\"" + LEGACY_GUEST + "\",\"version\":\"1.0\"}]",
                "427[\"handler.process\",{\"type\":\"heroImage\",\"action\":\"getAll\",\"userId\":\"" + LEGACY_GUEST + "\",\"version\":\"1.0\"}]",
                "428[\"handler.process\",{\"type\":\"hero\",\"action\":\"getAttrs\",\"userId\":\"" + LEGACY_GUEST + "\",\"heros\":[],\"version\":\"1.0\"}]",
                "429[\"handler.process\",{\"type\":\"userMsg\",\"action\":\"getMsgList\",\"userId\":\"" + LEGACY_GUEST + "\",\"version\":\"1.0\"}]"};
        StringBuilder batch22 = new StringBuilder();
        for (String pk : follow22) {
            batch22.append(pk.length()).append(":").append(pk);
        }
        post(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22, batch22.toString());
        String pollD = get(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22);
        check("22b aksi lanjutan: 7 ack ret 0 dalam satu polling (queryMyRecord/"
                        + "queryFriends/queryBlackList/registChat/getAll/getAttrs/getMsgList)",
                pollD.contains("433[") && pollD.contains("434[") && pollD.contains("435[")
                        && pollD.contains("436[") && pollD.contains("437[") && pollD.contains("438[")
                        && pollD.contains("439[") && pollD.contains("\"ret\":0"));
        check("22b kontrak aksi lanjutan: _records/_friends/users/_success/_heros/_attrs/_brief ada",
                pollD.contains("_records") && pollD.contains("_friends") && pollD.contains("\"users")
                        && pollD.contains("_success") && pollD.contains("_heros")
                        && pollD.contains("_attrs") && pollD.contains("_brief"));
        // aksi belum dihandcraft — ret 0 kosong (game tidak menggantung)
        String pktUk = "4210[\"handler.process\",{\"type\":\"lain\",\"action\":\"aksiBaru\",\"userId\":\""
                + LEGACY_GUEST + "\"}]";
        post(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22,
                pktUk.length() + ":" + pktUk);
        String pollE = get(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22);
        check("22b aksi belum dihandcraft: ret 0 (tidak menggantung)", pollE.contains("\"ret\":0"));
        // enterGame ulang — _id sesi game STABIL (data tahan reboot)
        String pktEg2 = "4221" + pktEg.substring(3); // ack id berbeda, isi sama
        post(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22,
                pktEg2.length() + ":" + pktEg2);
        get(gbase + "/socket.io/?EIO=3&transport=polling&sid=" + sid22);
        String model22b = Db.i().kv("game_player:" + LEGACY_GUEST);
        check("22b enterGame ulang: _id sesi game tetap (identitas stabil)",
                jsonStr(model22, "_id").equals(jsonStr(model22b, "_id")));
        check("22b paket manual tetap bebas server asli sesudah mesin jalan",
                !pollC.contains("popoh5.com") && !pollD.contains("popoh5.com"));
        gs22.stop();
        gs22 = null;

        // ---------------- TAHAP 23: PEMISAHAN PORT 0.0.2 — SDK :8610 & LOGIN :8611
        // (owner: "pola server aslinya punya 5 server utama.. SDK, login, main,
        // chat, dungeon, terpisah dengan berbagai port" + "SDK dan loginserver
        // harus matang sempurna rapi") ----------------
        // 23a) paket nyata di repo: setting.json = kontrak pemisahan port;
        //      bin = XOR(setting.json) EXACT; serversetting menunjuk login :8611.
        if (args.length > 1 && args[1] != null && args[1].length() > 0) {
            File paketDir = new File(new File(new File(new File(args[1], "local-server"),
                    "assets"), "server"), "config-offline");
            if (paketDir.isDirectory()) {
                try {
                    File setJson = new File(paketDir, "setting.json");
                    File setBin = new File(paketDir, "setting_BS_Android.bin");
                    File srvJson = new File(paketDir, "servers.json");
                    File srvSet = new File(paketDir, "serversetting.json");
                    String setTxt = readAll(new FileInputStream(setJson));
                    byte[] binAsli = new byte[(int) setBin.length()];
                    java.io.DataInputStream din = new java.io.DataInputStream(new FileInputStream(setBin));
                    din.readFully(binAsli);
                    din.close();
                    check("23 paket nyata: setting_BS_Android.bin = XOR(setting.json) EXACT (dua arah)",
                            new String(xor(binAsli), "UTF-8").equals(setTxt));
                    check("23 paket nyata: setting.json loginPort 8611 (login socket)",
                            setTxt.contains("\"loginPort\": 8611"));
                    check("23 paket nyata: setting.json loginWebPort 8610 (loginchecknative → SDK)",
                            setTxt.contains("\"loginWebPort\": 8610"));
                    String srvTxt = readAll(new FileInputStream(srvJson));
                    check("23 paket nyata: servers.json kontrak klien saja (tanpa upstream/urlInner/chatInner)",
                            !srvTxt.contains("\"upstream\"") && !srvTxt.contains("urlInner")
                                    && !srvTxt.contains("chatInner"));
                    check("23 paket nyata: servers.json url entri lokal 127.0.0.1:8080",
                            srvTxt.contains("http://127.0.0.1:8080"));
                    String srvSetTxt = readAll(new FileInputStream(srvSet));
                    check("23 paket nyata: serversetting.json loginserver = http://127.0.0.1:8611",
                            srvSetTxt.contains("http://127.0.0.1:8611"));
                    // 23b) manifest.txt = sha256 SETIAP file cocok (paket tak boleh geser diam-diam)
                    String mani = readAll(new FileInputStream(new File(paketDir, "manifest.txt")));
                    String[] cek = {"setting.json", "setting_BS_Android.bin",
                            "serversetting.json", "servers.json"};
                    boolean shaOk = true;
                    StringBuilder shaBad = new StringBuilder();
                    for (String nm : cek) {
                        java.security.MessageDigest md =
                                java.security.MessageDigest.getInstance("SHA-256");
                        byte[] isi = readAll(new FileInputStream(new File(paketDir, nm)))
                                .getBytes("UTF-8");
                        byte[] h = md.digest(isi);
                        StringBuilder hx = new StringBuilder();
                        for (byte x : h) {
                            hx.append(String.format("%02x", x));
                        }
                        if (!mani.contains(hx.toString())) {
                            shaOk = false;
                            shaBad.append(' ').append(nm);
                        }
                    }
                    check("23 paket nyata: manifest.txt sha256 cocok SEMUA file"
                            + (shaOk ? "" : " — geser:" + shaBad), shaOk);
                } catch (Throwable t23) {
                    check("23 paket nyata: audit paket gagal jalan — " + t23, false);
                }
            }
        }
        // 23c) StatusStore membawa versi 0.0.2 + status SDK terpisah
        String stJson = StatusStore.toJson();
        check("23 StatusStore: version = 0.0.3",
                stJson.contains("\"version\":\"0.0.3\""));
        check("23 StatusStore: sdkPort/sdkAlive + loginPort/loginAlive terpisah",
                stJson.contains("\"sdkPort\"") && stJson.contains("\"sdkAlive\"")
                        && stJson.contains("\"loginPort\"") && stJson.contains("\"loginAlive\""));

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

    /** POST engine.io berframing ke /socket.io/ (payload socket.io event).
     *  0.0.2 — port LOGIN 8611 (socket murni; SDK sudah pindah 8610). */
    static String socketIoPost(String eventJson) throws Exception {
        String hs = get("http://127.0.0.1:8611/socket.io/?EIO=3&transport=polling&t=1");
        int a = hs.indexOf('{');
        int b = hs.indexOf('}', a);
        String sid = jsonStr(hs.substring(a, b + 1), "sid");
        // CONNECT berframing: "2:40"
        String body = 2 + ":" + "40";
        post("http://127.0.0.1:8611/socket.io/?EIO=3&transport=polling&sid=" + URLEncoder.encode(sid, "UTF-8"),
                body);
        // EVENT berframing: "<len>:420[...]"
        body = eventJson.length() + ":" + eventJson;
        post("http://127.0.0.1:8611/socket.io/?EIO=3&transport=polling&sid=" + URLEncoder.encode(sid, "UTF-8"),
                body);
        return get("http://127.0.0.1:8611/socket.io/?EIO=3&transport=polling&sid=" + URLEncoder.encode(sid, "UTF-8"));
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
