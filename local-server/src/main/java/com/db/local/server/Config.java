package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * Config server lokal — <root>/config/server.json, disimpan DI LUAR APK
 * supaya bisa dilihat dan diubah tanpa rebuild. 0.0.3: jalur selalu lewat
 * Paths.serverJson() (sumber tunggal — config/ murni PENGATURAN: hanya
 * server.json + game-upstream.txt; data pemain pindah db/, state runtime
 * pindah state/).
 *
 * Isi default (0.5.5 — arahan owner: "saat apk di install dan di buka adalah
 * Utamanya Local" — dulu bawaan ONLINE):
 * {
 *   "mode": "local",      // "local" = UTAMANYA LOCAL (bawaan) — layani dari www/;
 *                         // "online" = teruskan ke server asli (pilihan pemilik via tombol melayang)
 *   "gamePort": 8080,     // port GAME — endpoint di assets client menunjuk ke sini
 *   "debugPort": 4000,    // port dasbor debug developer (game tidak tahu port ini)
 *   "sdkPort": 8610,      // 0.0.2 — port server SDK lokal (HTTP murni: /v1/* +
 *                         // /loginchecknative) — QHinfo menunjuk ke sini.
 *                         // Pemisahan port pola 5-server asli (owner 2026-11-14):
 *                         // SDK, login, main, chat, dungeon — TERPISAH port.
 *   "loginPort": 8611,    // 0.0.2 — port server LOGIN lokal (engine.io v3 +
 *                         // socket.io v2) — murni socket, TANPA API SDK (dulu
 *                         // 8610 diganda dua peran; kini terpisah rapi).
 *   "useSdk": false,      // false = login guest lokal via url ?userid=… (PR3);
 *                         // true = layar SDK asli dipakai (SDK tetap ke server asli)
 *   "bind": "127.0.0.1",
 *   "monitorSec": 60,
 *   "toggleX": -1,        // posisi terakhir tombol melayang (drag); -1 = posisi bawaan
 *   "toggleY": -1,
 *   "logTech": false,    // 0.4.5: false = log BERSIH (penting saja);
 *                         // true = RINCI (baris request per-file ikut tampil)
 *   "rekam": true         // 0.5.0 REKAM: perekam payload mentah semua lapisan
 *                         // (login/sdk/game) — memori + capture/rekam.jsonl +
 *                         // capture/payload/ (config asli). Bawaan ON.
 * }
 *
 * Sumber kebenaran mode: file ini. Tombol melayang mengubah mode → file ditulis
 * → aplikasi direstart → game membaca config sesuai mode baru.
 */
public final class Config {
    private static final Object LOCK = new Object();
    private static volatile File file = null;
    // 0.5.5 — UTAMANYA LOCAL: bawaan saat APK baru di-install dan dibuka (arahan
    // owner). server.json belum ada → mode = LOCAL. Pilihan ONLINE tetap dihormati
    // selama server.json menyimpannya (pemilik pilih lewat tombol melayang).
    private static volatile String mode = "local";
    private static volatile int gamePort = 8080;
    private static volatile int debugPort = 4000;
    // 0.0.2 — PEMISAHAN PORT (pola 5-server asli; owner: "pola server aslinya
    // punya 5 server utama.. SDK, login, main, chat, dungeon, terpisah dengan
    // berbagai port"). SDK HTTP = 8610 (QHinfo), LOGIN socket = 8611.
    private static volatile int sdkPort = 8610;
    private static volatile int loginPort = 8611;
    private static volatile boolean useSdk = false;
    private static volatile int monitorSec = 60;
    private static volatile int toggleX = -1;
    private static volatile int toggleY = -1;
    private static volatile boolean logTech = false;
    private static volatile boolean rekam = true;

    public static final String DEFAULT_JSON =
            "{\n"
                    + "  \"mode\": \"local\",\n"
                    + "  \"gamePort\": 8080,\n"
                    + "  \"debugPort\": 4000,\n"
                    + "  \"sdkPort\": 8610,\n"
                    + "  \"loginPort\": 8611,\n"
                    + "  \"useSdk\": false,\n"
                    + "  \"bind\": \"127.0.0.1\",\n"
                    + "  \"monitorSec\": 60,\n"
                    + "  \"toggleX\": -1,\n"
                    + "  \"toggleY\": -1,\n"
                    + "  \"logTech\": false,\n"
                    + "  \"rekam\": true\n"
                    + "}\n";

    private Config() {
    }

    /** Baca server.json dari folder config (tulis default bila belum ada).
     *  0.0.3 — jalur = Paths.serverJson() (SUMBER TUNGGAL); parameter tetap
     *  diterima utk kompatibilitas pemanggil lama/harness tanpa Paths. */
    public static void load(File serverRoot) {
        synchronized (LOCK) {
            try {
                File conf = Paths.ready() ? Paths.config() : new File(serverRoot, "config");
                if (!conf.exists()) {
                    conf.mkdirs();
                }
                File f = Paths.ready() ? Paths.serverJson() : new File(conf, "server.json");
                file = f;
                if (!f.exists()) {
                    writeText(f, DEFAULT_JSON);
                } else {
                    String raw = readText(f);
                    String m = grepStr(raw, "\"mode\"");
                    if ("local".equalsIgnoreCase(m) || "online".equalsIgnoreCase(m)) {
                        mode = m.toLowerCase();
                    }
                    int gp = (int) grepLong(raw, "\"gamePort\"", gamePort);
                    if (gp >= 1024 && gp <= 65535) {
                        gamePort = gp;
                    }
                    int dp = (int) grepLong(raw, "\"debugPort\"", debugPort);
                    if (dp >= 1024 && dp <= 65535) {
                        debugPort = dp;
                    }
                    int sp = (int) grepLong(raw, "\"sdkPort\"", sdkPort);
                    if (sp >= 1024 && sp <= 65535) {
                        sdkPort = sp;
                    }
                    int lp = (int) grepLong(raw, "\"loginPort\"", loginPort);
                    if (lp >= 1024 && lp <= 65535) {
                        loginPort = lp;
                    }
                    // 0.0.2 MIGRASI — server.json LAMA (0.0.1 ke bawah) tidak punya
                    // "sdkPort" dan "loginPort"-nya bernilai 8610 (dulu satu port
                    // dua peran). Bila kini sama → SDK tetap memegang port itu
                    // (QHinfo + loginchecknative shell menunjuk ke sana), login
                    // socket digeser ke port terdekat yang bebas — TANPA asumsi
                    // diam-diam: keputusan terekam di log.
                    if (sdkPort == loginPort) {
                        int gerak = 8611;
                        while (gerak == sdkPort) {
                            gerak++;
                        }
                        loginPort = gerak;
                        HumanLog.ev("⚠️", "server.json lama: sdkPort == loginPort ("
                                + sdkPort + ") — port itu dipertahankan untuk SDK (QHinfo); "
                                + "port LOGIN digeser otomatis ke " + loginPort
                                + " (pemisahan 0.0.2 — pola 5-server asli)", null);
                    }
                    // 0.0.3 — boolean dibaca DENGAN parse nilai benar (grepBool):
                    // dulu grepStr dipakai utk boolean tanpa kutip — malah menangkap
                    // nama kunci BERIKUTNYA ("logTech" → "rekam", "useSdk" → "bind")
                    // sehingga pilihan dasbor bisa lupa setelah reboot (bug lama yang
                    // terbongkar saat server.json dibaca-ulang sungguhan).
                    useSdk = grepBool(raw, "\"useSdk\"", useSdk);
                    int ms = (int) grepLong(raw, "\"monitorSec\"", monitorSec);
                    if (ms >= 10 && ms <= 3600) {
                        monitorSec = ms;
                    }
                    int tx = (int) grepLong(raw, "\"toggleX\"", toggleX);
                    int ty = (int) grepLong(raw, "\"toggleY\"", toggleY);
                    if (tx >= -1 && ty >= -1) {
                        toggleX = tx;
                        toggleY = ty;
                    }
                    logTech = grepBool(raw, "\"logTech\"", logTech);
                    rekam = grepBool(raw, "\"rekam\"", rekam);
                }
            } catch (Throwable t) {
                HumanLog.ev("⚠️", "server.json gagal dibaca — memakai nilai bawaan (mode LOCAL, port game 8080, dasbor 4000)",
                        String.valueOf(t));
            }
        }
    }

    public static String mode() {
        return mode;
    }

    public static int gamePort() {
        return gamePort;
    }

    public static int debugPort() {
        return debugPort;
    }

    /** Port server SDK lokal (HTTP murni /v1/* + /loginchecknative) — bawaan 8610. */
    public static int sdkPort() {
        return sdkPort;
    }

    /** Port server LOGIN lokal (engine.io v3 + socket.io v2 murni) — bawaan 8611. */
    public static int loginPort() {
        return loginPort;
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

    /** Nyalakan/matikan REKAM dan tulis ke server.json (dasbor /api/rekam/toggle). */
    public static void setRekam(boolean on) {
        synchronized (LOCK) {
            rekam = on;
            try {
                if (file != null) {
                    writeText(file, json());
                }
            } catch (Throwable t) {
                try {
                    HumanLog.ev("⚠️", "Gagal menulis rekam ke server.json — pilihan tetap dipakai sampai proses mati",
                            String.valueOf(t));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Ubah mode log RINCI dan tulis ke server.json (dipanggil dasbor /api/logtech). */
    public static void setLogTech(boolean on) {
        synchronized (LOCK) {
            logTech = on;
            try {
                if (file != null) {
                    writeText(file, json());
                }
            } catch (Throwable t) {
                try {
                    HumanLog.ev("⚠️", "Gagal menulis logTech ke server.json — pilihan tetap dipakai sampai proses mati",
                            String.valueOf(t));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Simpan posisi terakhir tombol melayang hasil digeser — tahan lama konfigurasi. */
    public static void setTogglePos(int x, int y) {
        synchronized (LOCK) {
            toggleX = x;
            toggleY = y;
            try {
                if (file != null) {
                    writeText(file, json());
                }
            } catch (Throwable t) {
                try {
                    HumanLog.ev("⚠️", "Posisi tombol melayang gagal disimpan ke server.json — tetap dipakai sampai proses mati",
                            String.valueOf(t));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static File file() {
        return file;
    }

    /** Ubah mode ("local"/"online") dan tulis ke server.json — tahan lama konfigurasi. */
    public static void setMode(String newMode) {
        if (!"local".equals(newMode) && !"online".equals(newMode)) {
            return;
        }
        synchronized (LOCK) {
            mode = newMode;
            try {
                if (file != null) {
                    writeText(file, json());
                }
            } catch (Throwable t) {
                HumanLog.ev("⚠️", "Gagal menulis mode ke server.json — mode baru tetap dipakai sampai proses mati",
                        String.valueOf(t));
            }
        }
    }

    private static String json() {
        return "{\n"
                + "  \"mode\": \"" + mode + "\",\n"
                + "  \"gamePort\": " + gamePort + ",\n"
                + "  \"debugPort\": " + debugPort + ",\n"
                + "  \"sdkPort\": " + sdkPort + ",\n"
                + "  \"loginPort\": " + loginPort + ",\n"
                + "  \"useSdk\": " + useSdk + ",\n"
                + "  \"bind\": \"127.0.0.1\",\n"
                + "  \"monitorSec\": " + monitorSec + ",\n"
                + "  \"toggleX\": " + toggleX + ",\n"
                + "  \"toggleY\": " + toggleY + ",\n"
                + "  \"logTech\": " + logTech + ",\n"
                + "  \"rekam\": " + rekam + "\n"
                + "}\n";
    }

    // ---------- helper file & parse ringan (tanpa dependensi) ----------

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

    private static long grepLong(String raw, String key, long def) {
        try {
            int i = raw.indexOf(key);
            if (i < 0) {
                return def;
            }
            int c = raw.indexOf(':', i);
            if (c < 0) {
                return def;
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
            String v = raw.substring(c + 1, end < 0 ? raw.length() : end).trim().replace("\"", "");
            return Long.parseLong(v);
        } catch (Throwable t) {
            return def;
        }
    }

    private static String grepStr(String raw, String key) {
        try {
            int i = raw.indexOf(key);
            if (i < 0) {
                return null;
            }
            int a = raw.indexOf('"', i + key.length());
            int b = raw.indexOf('"', a + 1);
            if (a < 0 || b < 0) {
                return null;
            }
            return raw.substring(a + 1, b);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 0.0.3 — baca nilai boolean JSON (true/false tanpa kutip) DENGAN BENAR:
     * ambil teks antara ':' sampai ',' / '}' lalu cek awalan "true". Dulu
     * boolean dibaca grepStr (untuk nilai berkutip) — salah menangkap nama
     * kunci berikutnya → pilihan toggle dasbor bisa lupa saat reboot.
     */
    private static boolean grepBool(String raw, String key, boolean def) {
        try {
            int i = raw.indexOf(key);
            if (i < 0) {
                return def;
            }
            int c = raw.indexOf(':', i);
            if (c < 0) {
                return def;
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
            String v = raw.substring(c + 1, end < 0 ? raw.length() : end).trim();
            if (v.length() == 0) {
                return def;
            }
            return v.startsWith("true");
        } catch (Throwable t) {
            return def;
        }
    }
}
