package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config server lokal — <root>/.env — SATU FILE PENGATURAN (0.0.4).
 *
 * Arahan owner 2026-11-15: "server json bisa di gantikah jadi .env" —
 * config/server.json (+ config/game-upstream.txt) DIGANTIKAN satu file
 * .env di root server: format KEY=VALUE, bisa disunting di file manager
 * perangkat tanpa build ulang, komentar menjelaskan tiap kunci.
 *
 * Kunci (bawaan — port pola 5-server asli, disetujui owner):
 *   MODE=local          local = UTAMANYA LOCAL (bawaan APK baru; arahan owner
 *                       0.5.5) | online = teruskan ke server asli (tombol melayang)
 *   SDK_PORT=8610       SDK-SERVER — HTTP murni /v1/* + loginchecknative (QHinfo)
 *   LOGIN_PORT=8611     LOGIN-SERVER — engine.io v3 + socket.io v2 murni
 *   MAIN_PORT=8080      MAIN-SERVER — port yang ditunjuk endpoint client
 *   CHAT_PORT=8612      CHAT-SERVER — service discovery registChat (siap)
 *   DUNGEON_PORT=8613   DUNGEON-SERVER — service discovery team (siap)
 *   DASBOR_PORT=4000    dasbor debug developer (game tidak tahu port ini)
 *   GAME_UPSTREAM=      relay OPSIONAL ke server game asli (kosong = lokal
 *                       murni, nol kontak keluar) — pengganti game-upstream.txt
 *   USE_SDK=false       false = login guest lokal via url ?userid=… (PR3)
 *   BIND=127.0.0.1      alamat layanan lokal
 *   MONITOR_SEC=60      denyut pemantauan server asli (detik)
 *   TOGGLE_X/-Y=-1      posisi terakhir tombol melayang
 *   LOG_TECH=false      false = log BERSIH | true = RINCI
 *   REKAM=true          perekam payload mentah (bawaan ON)
 *
 * Sumber kebenaran mode: file ini. Tombol melayang mengubah mode → .env
 * ditulis-ulang → aplikasi direstart → game membaca config sesuai mode baru.
 * Migrasi nilai dari config/server.json + game-upstream.txt era 0.0.3 ke
 * bawah: migrateLegacyValues() (dipanggil Starter sebelum Cleaner menyapu
 * folder lama; dipanggil juga defensif oleh load bila .env belum ada).
 */
public final class Config {
    private static final Object LOCK = new Object();
    private static volatile File file = null;
    // 0.5.5 — UTAMANYA LOCAL: bawaan saat APK baru di-install dan dibuka (arahan
    // owner). .env belum ada → mode = LOCAL. Pilihan ONLINE tetap dihormati
    // selama .env menyimpannya (pemilik pilih lewat tombol melayang).
    private static volatile String mode = "local";
    private static volatile int mainPort = 8080;
    private static volatile int dasborPort = 4000;
    // 0.0.2 — PEMISAHAN PORT (pola 5-server asli; owner: "pola server aslinya
    // punya 5 server utama.. SDK, login, main, chat, dungeon, terpisah dengan
    // berbagai port"). SDK HTTP = 8610 (QHinfo), LOGIN socket = 8611.
    private static volatile int sdkPort = 8610;
    private static volatile int loginPort = 8611;
    // 0.0.4 — port CHAT + DUNGEON dideklarasikan di .env sejak awal (folder
    // layanan sudah dibuat; layananya menyusul sesuai urutan kerja owner).
    private static volatile int chatPort = 8612;
    private static volatile int dungeonPort = 8613;
    private static volatile boolean useSdk = false;
    private static volatile int monitorSec = 60;
    private static volatile int toggleX = -1;
    private static volatile int toggleY = -1;
    private static volatile boolean logTech = false;
    private static volatile boolean rekam = true;
    // 0.0.4 — upstream relay (pengganti game-upstream.txt; kosong = lokal murni)
    private static volatile String gameUpstream = "";

    private Config() {
    }

    /** Baca .env dari root server (tulis bawaan bila belum ada; nilai legacy
     *  config/server.json + game-upstream.txt ikut dimigrasikan bila ada).
     *  0.0.4 — jalur = Paths.envFile() (SUMBER TUNGGAL); parameter tetap
     *  diterima utk kompatibilitas pemanggil lama/harness tanpa Paths. */
    public static void load(File serverRoot) {
        synchronized (LOCK) {
            try {
                File root = Paths.ready() ? Paths.root() : serverRoot;
                File f = Paths.ready() ? Paths.envFile()
                        : new File(serverRoot, Paths.ENV_NAME);
                // .env belum ada + ada config lama → migrasikan NILAI dulu
                // (perangkat 0.0.3 ke bawah — pilihan pemilik tidak hilang)
                if (!f.exists() && root != null) {
                    migrateLegacyValues(root);
                }
                file = f;
                if (!f.exists()) {
                    writeText(f, env());
                } else {
                    Map<String, String> kv = parseEnv(readText(f));
                    String m = kv.get("MODE");
                    if ("local".equalsIgnoreCase(m) || "online".equalsIgnoreCase(m)) {
                        mode = m.toLowerCase();
                    }
                    mainPort = clampPort(kv.get("MAIN_PORT"), mainPort);
                    dasborPort = clampPort(kv.get("DASBOR_PORT"), dasborPort);
                    sdkPort = clampPort(kv.get("SDK_PORT"), sdkPort);
                    loginPort = clampPort(kv.get("LOGIN_PORT"), loginPort);
                    chatPort = clampPort(kv.get("CHAT_PORT"), chatPort);
                    dungeonPort = clampPort(kv.get("DUNGEON_PORT"), dungeonPort);
                    // 0.0.2 migrasi tetap dijaga: bila .env bawaan salah salin
                    // sehingga SDK == LOGIN, SDK memegang port itu (QHinfo),
                    // login digeser — keputusan TIDAK diam-diam (log).
                    if (sdkPort == loginPort) {
                        int gerak = 8611;
                        while (gerak == sdkPort || gerak == chatPort || gerak == dungeonPort) {
                            gerak++;
                        }
                        loginPort = gerak;
                        HumanLog.ev("⚠️", ".env lama: SDK_PORT == LOGIN_PORT ("
                                + sdkPort + ") — port itu dipertahankan untuk SDK (QHinfo); "
                                + "port LOGIN digeser otomatis ke " + loginPort
                                + " (pemisahan 0.0.2 — pola 5-server asli)", null);
                    }
                    useSdk = parseBool(kv.get("USE_SDK"), useSdk);
                    int ms = parseInt(kv.get("MONITOR_SEC"), monitorSec);
                    if (ms >= 10 && ms <= 3600) {
                        monitorSec = ms;
                    }
                    int tx = parseInt(kv.get("TOGGLE_X"), toggleX);
                    int ty = parseInt(kv.get("TOGGLE_Y"), toggleY);
                    if (tx >= -1 && ty >= -1) {
                        toggleX = tx;
                        toggleY = ty;
                    }
                    logTech = parseBool(kv.get("LOG_TECH"), logTech);
                    rekam = parseBool(kv.get("REKAM"), rekam);
                    String up = kv.get("GAME_UPSTREAM");
                    if (up != null) {
                        up = up.trim();
                        if (up.startsWith("http://") || up.startsWith("https://")) {
                            gameUpstream = up;
                        }
                    }
                }
            } catch (Throwable t) {
                HumanLog.ev("⚠️", ".env gagal dibaca — memakai nilai bawaan (mode LOCAL, "
                        + "MAIN 8080, SDK 8610, LOGIN 8611, CHAT 8612, DUNGEON 8613, dasbor 4000)",
                        String.valueOf(t));
            }
        }
    }

    /**
     * 0.0.4 — MIGRASI NILAI config lama → .env (arahan owner: "server json bisa
     * di gantikah jadi .env"). Perangkat 0.0.3 ke bawah punya config/server.json
     * (+ config/game-upstream.txt). Keduanya DIBACA nilainya lalu .env ditulis;
     * file lamanya disapu Cleaner (nama+ukuran di log) — pilihan pemilik
     * (mode, port, logTech, rekam, posisi tombol, upstream) TIDAK hilang.
     * Dipanggil Starter SEBELUM Cleaner; dipanggil juga defensif oleh load().
     * Return ringkasan pindahan ("" bila tidak ada yang dimigrasikan).
     */
    public static String migrateLegacyValues(File root) {
        synchronized (LOCK) {
            StringBuilder r = new StringBuilder();
            try {
                File env = Paths.ready() ? Paths.envFile()
                        : new File(root, Paths.ENV_NAME);
                if (env.exists()) {
                    return ""; // .env sudah ada — jangan timpa pilihan pemilik
                }
                File conf = new File(root, "config");
                File oldJson = new File(conf, "server.json");
                if (oldJson.exists() && oldJson.isFile()) {
                    String raw = readText(oldJson);
                    Map<String, String> j = parseJsonFlat(raw);
                    String m = j.get("mode");
                    if ("local".equalsIgnoreCase(m) || "online".equalsIgnoreCase(m)) {
                        mode = m.toLowerCase();
                    }
                    mainPort = clampPort(j.get("gamePort"), mainPort);
                    dasborPort = clampPort(j.get("debugPort"), dasborPort);
                    sdkPort = clampPort(j.get("sdkPort"), sdkPort);
                    loginPort = clampPort(j.get("loginPort"), loginPort);
                    if (sdkPort == loginPort) {
                        // server.json era 0.0.1: satu port dua peran — SDK
                        // pertahankan (QHinfo), login socket digeser (0.0.2)
                        int gerak = 8611;
                        while (gerak == sdkPort) {
                            gerak++;
                        }
                        loginPort = gerak;
                        add(r, "port login digeser " + loginPort + " (server.json lama: "
                                + "sdkPort == loginPort " + sdkPort + ")");
                    }
                    useSdk = parseBool(j.get("useSdk"), useSdk);
                    int ms = parseInt(j.get("monitorSec"), monitorSec);
                    if (ms >= 10 && ms <= 3600) {
                        monitorSec = ms;
                    }
                    int tx = parseInt(j.get("toggleX"), toggleX);
                    int ty = parseInt(j.get("toggleY"), toggleY);
                    if (tx >= -1 && ty >= -1) {
                        toggleX = tx;
                        toggleY = ty;
                    }
                    logTech = parseBool(j.get("logTech"), logTech);
                    rekam = parseBool(j.get("rekam"), rekam);
                    add(r, "config/server.json → .env (nilai ikut: mode " + mode
                            + ", MAIN " + mainPort + ", SDK " + sdkPort
                            + ", LOGIN " + loginPort + ")");
                }
                File oldUp = new File(conf, "game-upstream.txt");
                if (oldUp.exists() && oldUp.length() > 6) {
                    String s = readText(oldUp).trim();
                    if (s.startsWith("http://") || s.startsWith("https://")) {
                        gameUpstream = s;
                        add(r, "config/game-upstream.txt → .env (GAME_UPSTREAM ikut)");
                    }
                }
                if (r.length() > 0 || oldJson.exists()) {
                    writeText(env, env());
                }
            } catch (Throwable t) {
                try {
                    HumanLog.ev("⚠️", "Migrasi nilai config lama → .env menemui kendala — "
                            + "nilai bawaan dipakai", String.valueOf(t));
                } catch (Throwable ignored) {
                }
            }
            return r.toString();
        }
    }

    public static String mode() {
        return mode;
    }

    /** Port MAIN-SERVER (:8080 bawaan) — endpoint client menunjuk ke sini. */
    public static int mainPort() {
        return mainPort;
    }

    /** Port dasbor debug (4000) — khusus developer, game tidak tahu. */
    public static int dasborPort() {
        return dasborPort;
    }

    /** Port server SDK lokal (HTTP murni /v1/* + /loginchecknative) — bawaan 8610. */
    public static int sdkPort() {
        return sdkPort;
    }

    /** Port server LOGIN lokal (engine.io v3 + socket.io v2 murni) — bawaan 8611. */
    public static int loginPort() {
        return loginPort;
    }

    /** Port server CHAT lokal (8612) — service discovery registChat (siap). */
    public static int chatPort() {
        return chatPort;
    }

    /** Port server DUNGEON lokal (8613) — service discovery team (siap). */
    public static int dungeonPort() {
        return dungeonPort;
    }

    /** true = layar SDK asli dipakai; false (bawaan) = guest lokal via url ?userid=… */
    public static boolean useSdk() {
        return useSdk;
    }

    public static int monitorSec() {
        return monitorSec;
    }

    /** Posisi terakhir tombol melayang; -1 = belum pernah digeser (pakai bawaan). */
    public static int toggleX() {
        return toggleX;
    }

    public static int toggleY() {
        return toggleY;
    }

    /** true = mode log RINCI (baris teknis ikut tampil di dasbor); bawaan false = BERSIH. */
    public static boolean logTech() {
        return logTech;
    }

    /** true = REKAM menyala (perekam payload mentah semua lapisan) — bawaan ON. */
    public static boolean rekam() {
        return rekam;
    }

    /** URL upstream relay game (pengganti game-upstream.txt; "" = lokal murni). */
    public static String gameUpstream() {
        return gameUpstream;
    }

    /** Nyalakan/matikan REKAM dan tulis ke .env (dasbor). */
    public static void setRekam(boolean on) {
        synchronized (LOCK) {
            rekam = on;
            persist("rekam");
        }
    }

    /** Ubah mode log RINCI dan tulis ke .env (dipanggil dasbor /api/logtech). */
    public static void setLogTech(boolean on) {
        synchronized (LOCK) {
            logTech = on;
            persist("logTech");
        }
    }

    /** Simpan posisi terakhir tombol melayang hasil digeser — tahan lama konfigurasi. */
    public static void setTogglePos(int x, int y) {
        synchronized (LOCK) {
            toggleX = x;
            toggleY = y;
            persist("posisi tombol melayang");
        }
    }

    public static File file() {
        return file;
    }

    /** Ubah mode ("local"/"online") dan tulis ke .env — tahan lama konfigurasi. */
    public static void setMode(String newMode) {
        if (!"local".equals(newMode) && !"online".equals(newMode)) {
            return;
        }
        synchronized (LOCK) {
            mode = newMode;
            persist("mode");
        }
    }

    /**
     * 0.0.4 — simpan URL upstream relay ke .env (kunci GAME_UPSTREAM) —
     * pengganti tulis config/game-upstream.txt. Validasi: wajib http:// atau
     * https://, satu baris. KOSONG = HAPUS isian (kembali lokal murni —
     * nol kontak keluar). Berlaku LANGSUNG (tanpa restart).
     * return null = OK; teks lain = pesan galak untuk dasbor.
     */
    public static String setUpstream(String url) {
        if (url == null) {
            url = "";
        }
        url = url.trim().replaceAll("[\\r\\n]+", "");
        if (url.length() > 0 && !url.startsWith("http://") && !url.startsWith("https://")) {
            return "URL harus diawali http:// atau https://";
        }
        synchronized (LOCK) {
            gameUpstream = url;
            persist("GAME_UPSTREAM");
        }
        return null;
    }

    /** Tulis .env dari nilai hidup (satu bentuk — komentar ikut, idempoten). */
    private static void persist(String apa) {
        try {
            if (file != null) {
                writeText(file, env());
            }
        } catch (Throwable t) {
            try {
                HumanLog.ev("⚠️", "Gagal menulis " + apa + " ke .env — pilihan tetap "
                        + "dipakai sampai proses mati", String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
    }

    /** Isi .env — KOMENTAR menjelaskan tiap kunci (pemilik sunting di file manager). */
    public static String env() {
        return "# ============================================================\n"
                + "# db-apk — PENGATURAN SERVER LOKAL (satu file)\n"
                + "# Sunting nilai, lalu buka ulang aplikasi. Nama folder = layanan:\n"
                + "# SDK-SERVER (" + sdkPort + ") LOGIN-SERVER (" + loginPort
                + ") MAIN-SERVER (" + mainPort + ")\n"
                + "# CHAT-SERVER (" + chatPort + ") DUNGEON-SERVER (" + dungeonPort
                + ") DASBOR (" + dasborPort + ") LOG\n"
                + "# ============================================================\n"
                + "\n"
                + "# local = utamanya LOCAL (bawaan) | online = teruskan ke server asli\n"
                + "MODE=" + mode + "\n"
                + "\n"
                + "# PORT server lokal (pola 5-server asli — satu port satu layanan)\n"
                + "SDK_PORT=" + sdkPort + "\n"
                + "LOGIN_PORT=" + loginPort + "\n"
                + "MAIN_PORT=" + mainPort + "\n"
                + "CHAT_PORT=" + chatPort + "\n"
                + "DUNGEON_PORT=" + dungeonPort + "\n"
                + "DASBOR_PORT=" + dasborPort + "\n"
                + "\n"
                + "# Relay OPSIONAL ke server game asli (satu baris URL; kosong = lokal\n"
                + "# murni — nol kontak keluar). Diisi dari dasbor seksi CONFIG atau di sini.\n"
                + "GAME_UPSTREAM=" + gameUpstream + "\n"
                + "\n"
                + "# true = layar SDK asli dipakai | false (bawaan) = guest lokal via url\n"
                + "USE_SDK=" + useSdk + "\n"
                + "\n"
                + "# Alamat layanan lokal\n"
                + "BIND=127.0.0.1\n"
                + "\n"
                + "# Denyut pemantauan server asli (detik)\n"
                + "MONITOR_SEC=" + monitorSec + "\n"
                + "\n"
                + "# Posisi terakhir tombol melayang (-1 = posisi bawaan)\n"
                + "TOGGLE_X=" + toggleX + "\n"
                + "TOGGLE_Y=" + toggleY + "\n"
                + "\n"
                + "# true = log RINCI (baris teknis ikut tampil) | false = BERSIH\n"
                + "LOG_TECH=" + logTech + "\n"
                + "\n"
                + "# true = REKAM menyala (perekam payload mentah) — bawaan ON\n"
                + "REKAM=" + rekam + "\n";
    }

    // ---------- helper parse ringan (tanpa dependensi) ----------

    /** Parse .env: baris "KEY=VALUE"; "#" = komentar; spasi diabaikan. */
    private static Map<String, String> parseEnv(String raw) {
        Map<String, String> kv = new LinkedHashMap<String, String>();
        if (raw == null) {
            return kv;
        }
        String[] lines = raw.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            if (k.length() > 0) {
                kv.put(k, v);
            }
        }
        return kv;
    }

    /** Parse JSON DATAR sederhana (config/server.json legacy 0.0.3 ke bawah). */
    private static Map<String, String> parseJsonFlat(String raw) {
        Map<String, String> kv = new LinkedHashMap<String, String>();
        if (raw == null) {
            return kv;
        }
        String[] keys = {"mode", "gamePort", "debugPort", "sdkPort", "loginPort",
                "useSdk", "monitorSec", "toggleX", "toggleY", "logTech", "rekam"};
        for (int i = 0; i < keys.length; i++) {
            String k = "\"" + keys[i] + "\"";
            int idx = raw.indexOf(k);
            if (idx < 0) {
                continue;
            }
            int c = raw.indexOf(':', idx + k.length());
            if (c < 0) {
                continue;
            }
            int e1 = raw.indexOf(',', c);
            int e2 = raw.indexOf('}', c);
            int end;
            if (e1 < 0) {
                end = e2;
            } else if (e2 < 0) {
                end = e1;
            } else {
                end = Math.min(e1, e2);
            }
            if (end < 0) {
                end = raw.length();
            }
            String v = raw.substring(c + 1, end).trim();
            if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                v = v.substring(1, v.length() - 1);
            }
            kv.put(keys[i], v);
        }
        return kv;
    }

    private static int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static int clampPort(String v, int def) {
        int p = parseInt(v, def);
        return (p >= 1024 && p <= 65535) ? p : def;
    }

    private static boolean parseBool(String v, boolean def) {
        if (v == null) {
            return def;
        }
        v = v.trim();
        if (v.length() == 0) {
            return def;
        }
        if (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes")) {
            return true;
        }
        if (v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("no")) {
            return false;
        }
        return def;
    }

    private static void writeText(File f, String content) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
        w.write(content);
        w.close();
    }

    private static String readText(File f) throws Exception {
        Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
        StringBuilder sb = new StringBuilder(512);
        char[] buf = new char[1024];
        int n;
        while ((n = r.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        r.close();
        return sb.toString();
    }

    private static void add(StringBuilder r, String line) {
        if (r.length() > 0) {
            r.append("\n");
        }
        r.append(line);
    }
}
