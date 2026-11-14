package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * PATHS — SUMBER TUNGGAL SEMUA JALUR FOLDER/FILE (0.0.4).
 *
 * Arahan owner 2026-11-15 (pes langsung):
 *   "server json bisa di gantikah jadi .env"
 *   "lalu SDK-SERVER, LOGIN-SERVER, MAIN-SERVER, CHAT-SERVER,
 *    DUNGEON-SERVER dan LOG !!  nama nya jelas"
 *   + kendala sebelumnya: "banyak folder sampah yg gak jelas.. isi root
 *     folder gak jelas.. PETA.TXT GK PENTING.. TMP GK PENTING".
 *
 * STRUKTUR BARU Android/data/com.db.local/server/ (root = 3 file + 6 folder,
 * SEMUA bernama jelas — nama folder = nama layanannya):
 *
 *   server/
 *   ├── .env              PENGATURAN — SEMUA seting server SATU file
 *   │                     (pengganti config/server.json + game-upstream.txt)
 *   ├── dragonball.db     DATA PEMAIN (SQLite + jurnal) — JANGAN DIHAPUS
 *   ├── status.json       keadaan server saat ini (dibuat ulang otomatis)
 *   ├── SDK-SERVER/       milik SDK :8610 (HTTP /v1/* + loginchecknative — QHinfo)
 *   ├── LOGIN-SERVER/     milik LOGIN :8611 (engine.io v3 + socket.io v2)
 *   ├── MAIN-SERVER/      milik MAIN :8080 — SEMUA yang dilayani ke klien:
 *   │   ├── bs/               mirror statis game (path = URL server asli)
 *   │   ├── config-offline/   paket MANUAL config klien (ditimpa dari APK tiap boot)
 *   │   └── www.stamp         penanda versi mirror (bookkeeping MAIN)
 *   ├── CHAT-SERVER/      milik CHAT :8612 (service discovery registChat — siap)
 *   ├── DUNGEON-SERVER/   milik DUNGEON :8613 (myTeamServerSocketUrl — siap)
 *   └── LOG/              LOG — nyawa (human.log · events.jsonl · crash.log)
 *
 * Yang DIHAPUS dari struktur lama 0.0.3 (kendala owner):
 *   PETA.txt (tidak penting), tmp/ (tidak penting), folder config/ + db/ +
 *   state/ + www/ + config-offline/ + logs/ di root (isi pindah ke folder
 *   layanan masing-masing — dulu 8 entri root tidak semua jelas).
 *
 * MIGRASI otomatis perangkat 0.0.3 ke bawah (SEBELUM Db.boot &amp; Cleaner,
 * terekam log; TIDAK PERNAH menimpa target yang sudah ada — data baru menang):
 *   db/dragonball.db* (+jurnal)      → dragonball.db* (root)
 *   config/dragonball.db* (0.0.2)    → dragonball.db* (root)
 *   state/status.json · config/status.json → status.json (root)
 *   state/www.stamp                  → MAIN-SERVER/www.stamp
 *   www/bs/**                        → MAIN-SERVER/bs/**
 *   config-offline/**                → MAIN-SERVER/config-offline/**
 *   logs/**                          → LOG/**
 *   config/server.json + game-upstream.txt → NILAINYA diambil ke .env
 *       (Config.migrateLegacyValues — nilai tidak hilang; file lama disapu
 *        Cleaner dengan nama+ukuran di log).
 *
 * SEMUA kelas WAJIB lewat Paths.* — dilarang membuat jalur sendiri
 * (new File(root, "…") tersebar = akar keberantakan, tidak diulang).
 * Harness juga memanggil Paths.init(root) agar perilaku identik dengan device.
 */
public final class Paths {
    /** Nama file pengaturan di root server (format .env — KEY=VALUE). */
    public static final String ENV_NAME = ".env";
    /** Nama folder LOG (nyawa server). */
    public static final String LOG_DIR = "LOG";
    /** Nama folder layanan (nama = layanannya — arahan owner). */
    public static final String SDK_DIR = "SDK-SERVER";
    public static final String LOGIN_DIR = "LOGIN-SERVER";
    public static final String MAIN_DIR = "MAIN-SERVER";
    public static final String CHAT_DIR = "CHAT-SERVER";
    public static final String DUNGEON_DIR = "DUNGEON-SERVER";

    private static final Object LOCK = new Object();
    private static volatile File root = null;

    private Paths() {
    }

    // ---------------- inisialisasi ----------------

    /**
     * Set root folder server (Android/data/&lt;paket&gt;/server). Dipanggil Starter
     * paling awal dan harness sebelum apa pun; tanpa init, Paths jatuh ke
     * folder relatif proses (harness lama tanpa init tetap aman).
     */
    public static void init(File serverRoot) {
        synchronized (LOCK) {
            root = serverRoot;
        }
    }

    /** Root server (null bila belum init). */
    public static File root() {
        return root;
    }

    /** true bila init() sudah dipanggil (Paths siap dipakai sebagai sumber tunggal). */
    public static boolean ready() {
        return root != null;
    }

    // ---------------- folder layanan (nama = layanannya) ----------------

    /** SDK-SERVER/ — milik SDK :8610 (HTTP murni /v1/* + loginchecknative). */
    public static File sdkServer() {
        return child(SDK_DIR);
    }

    /** LOGIN-SERVER/ — milik LOGIN :8611 (engine.io v3 + socket.io v2 murni). */
    public static File loginServer() {
        return child(LOGIN_DIR);
    }

    /** MAIN-SERVER/ — milik MAIN :8080 (mirror bs/ + paket config-offline/ + stamp). */
    public static File mainServer() {
        return child(MAIN_DIR);
    }

    /** CHAT-SERVER/ — milik CHAT :8612 (service discovery registChat — siap). */
    public static File chatServer() {
        return child(CHAT_DIR);
    }

    /** DUNGEON-SERVER/ — milik DUNGEON :8613 (service discovery team — siap). */
    public static File dungeonServer() {
        return child(DUNGEON_DIR);
    }

    /** LOG/ — log bahasa manusia + mesin + crash (nyawa — tidak boleh dipangkas). */
    public static File logs() {
        return child(LOG_DIR);
    }

    /** MAIN-SERVER/bs/ — mirror statis game (path = URL server asli). */
    public static File mirror() {
        return new File(mainServer(), "bs");
    }

    /** MAIN-SERVER/config-offline/ — PAKET MANUAL config klien (kanonik, ditimpa tiap boot). */
    public static File offline() {
        return new File(mainServer(), "config-offline");
    }

    // ---------------- file penting (satu tempat mendefinisikan) ----------------

    /** .env — SEMUA pengaturan server SATU file (pengganti config/server.json + game-upstream.txt). */
    public static File envFile() {
        return new File(child(""), ENV_NAME);
    }

    /** dragonball.db — DATABASE pemain di root (akun/tamu/sesi/data game). */
    public static File dbFile() {
        return new File(child(""), "dragonball.db");
    }

    /** status.json — keadaan server saat ini (root — sekali lihat langsung jelas). */
    public static File statusJson() {
        return new File(child(""), "status.json");
    }

    /** MAIN-SERVER/www.stamp — penanda versi mirror (bookkeeping MAIN). */
    public static File wwwStamp() {
        return new File(mainServer(), "www.stamp");
    }

    /** LOG/human.log. */
    public static File humanLog() {
        return new File(logs(), "human.log");
    }

    /** LOG/events.jsonl. */
    public static File eventsJsonl() {
        return new File(logs(), "events.jsonl");
    }

    /** LOG/crash.log — bukti force close (dibaca saat boot lalu di-rename .read). */
    public static File crashLog() {
        return new File(logs(), "crash.log");
    }

    // ---------------- folder siap pakai ----------------

    /** Pastikan SEMUA folder standar ada (idempoten). Gagal per folder tidak melempar. */
    public static void ensureDirs() {
        File[] all = {sdkServer(), loginServer(), mainServer(), chatServer(),
                dungeonServer(), logs()};
        for (int i = 0; i < all.length; i++) {
            try {
                if (!all[i].exists()) {
                    all[i].mkdirs();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------- migrasi layout lama → layanan (0.0.4) ----------------

    /**
     * Pindahkan isi layout 0.0.3 ke bawah ke folder layanan yang benar (SEBELUM
     * Cleaner &amp; SEBELUM Db.boot). Hanya memindah bila sumber ada DAN target
     * belum ada (tidak pernah menimpa data baru dengan lama). Return teks
     * ringkasan untuk baris log ("" bila tidak ada yang pindah).
     *
     * Nilai config (config/server.json + config/game-upstream.txt) TIDAK dipindah
     * di sini — nilainya diambil ke .env oleh Config.migrateLegacyValues(root)
     * (dipanggil Starter sebelum Cleaner menyapu folder config/).
     */
    public static String migrateToServiceLayout() {
        StringBuilder r = new StringBuilder();
        // 1) DATABASE pemain: db/dragonball.db* (0.0.3) + config/dragonball.db*
        //    (0.0.2 ke bawah) → ROOT (dragonball.db* — satu file jelas di root)
        String[] dbFiles = {"dragonball.db", "dragonball.db-journal", "dragonball.db-wal",
                "dragonball.db-shm", "dragonball.db.corrupt"};
        for (int i = 0; i < dbFiles.length; i++) {
            File dst = new File(child(""), dbFiles[i]);
            if (moveOne(new File(child("db"), dbFiles[i]), dst)) {
                add(r, "db/" + dbFiles[i] + " → " + dbFiles[i]);
            }
            if (moveOne(new File(child("config"), dbFiles[i]), dst)) {
                add(r, "config/" + dbFiles[i] + " → " + dbFiles[i]);
            }
        }
        // 2) status.json: state/status.json (0.0.3) + config/status.json (0.0.2) → ROOT
        File dstStatus = statusJson();
        if (moveOne(new File(child("state"), "status.json"), dstStatus)) {
            add(r, "state/status.json → status.json");
        }
        if (moveOne(new File(child("config"), "status.json"), dstStatus)) {
            add(r, "config/status.json → status.json");
        }
        // 3) penanda mirror: state/www.stamp (0.0.3) + config/www.stamp (0.0.2)
        //    → MAIN-SERVER/www.stamp (bookkeeping milik MAIN)
        File dstStamp = wwwStamp();
        if (moveOne(new File(child("state"), "www.stamp"), dstStamp)) {
            add(r, "state/www.stamp → MAIN-SERVER/www.stamp");
        }
        if (moveOne(new File(child("config"), "www.stamp"), dstStamp)) {
            add(r, "config/www.stamp → MAIN-SERVER/www.stamp");
        }
        // 4) mirror game: www/bs → MAIN-SERVER/bs (www/ hanya berisi bs/ —
        //    bukti assets/server/www/). Anak www/ lain ikut dipindah apa adanya.
        File www = child("www");
        File[] wwwKids = www.listFiles();
        if (wwwKids != null) {
            for (int i = 0; i < wwwKids.length; i++) {
                File dst = new File(mainServer(), wwwKids[i].getName());
                if (moveOne(wwwKids[i], dst)) {
                    add(r, "www/" + wwwKids[i].getName() + " → MAIN-SERVER/" + wwwKids[i].getName());
                }
            }
        }
        // 5) paket manual klien: config-offline/** → MAIN-SERVER/config-offline/**
        File off = child("config-offline");
        File[] offKids = off.listFiles();
        if (offKids != null) {
            File dstDir = offline();
            if (!dstDir.exists()) {
                dstDir.mkdirs();
            }
            for (int i = 0; i < offKids.length; i++) {
                if (moveOne(offKids[i], new File(dstDir, offKids[i].getName()))) {
                    add(r, "config-offline/" + offKids[i].getName()
                            + " → MAIN-SERVER/config-offline/" + offKids[i].getName());
                }
            }
        }
        // 6) LOG: logs/** → LOG/** (nyawa TIDAK hilang saat naik versi)
        File lg = child("logs");
        File[] lgKids = lg.listFiles();
        if (lgKids != null) {
            File dstDir = logs();
            if (!dstDir.exists()) {
                dstDir.mkdirs();
            }
            for (int i = 0; i < lgKids.length; i++) {
                if (moveOne(lgKids[i], new File(dstDir, lgKids[i].getName()))) {
                    add(r, "logs/" + lgKids[i].getName() + " → LOG/" + lgKids[i].getName());
                }
            }
        }
        return r.toString();
    }

    private static void add(StringBuilder r, String line) {
        if (r.length() > 0) {
            r.append("\n");
        }
        r.append(line);
    }

    /** Pindah satu file/folder bila sumber ada dan target belum ada; return true bila pindah. */
    private static boolean moveOne(File src, File dst) {
        try {
            if (!src.exists()) {
                return false;
            }
            if (dst.exists()) {
                return false; // target baru sudah ada — jangan timpa (data baru menang)
            }
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            return src.renameTo(dst) || copyMove(src, dst);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Fallback pindah antar-volume: salin rekursif lalu hapus sumber. */
    private static boolean copyMove(File src, File dst) {
        try {
            if (src.isDirectory()) {
                File[] kids = src.listFiles();
                if (kids != null) {
                    if (!dst.exists() && !dst.mkdirs()) {
                        return false;
                    }
                    boolean all = true;
                    for (int i = 0; i < kids.length; i++) {
                        all = copyMove(kids[i], new File(dst, kids[i].getName())) && all;
                    }
                    deleteRc(src);
                    return all;
                }
                return src.delete();
            }
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
            return src.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void deleteRc(File f) {
        try {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    deleteRc(kids[i]);
                }
            }
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    // ---------------- internal ----------------

    private static File child(String name) {
        File r = root;
        if (r != null) {
            return name.length() == 0 ? r : new File(r, name);
        }
        // Paths belum init — jatuh ke folder proses (harness lama tetap aman)
        return new File(name.length() == 0 ? "." : name);
    }
}
