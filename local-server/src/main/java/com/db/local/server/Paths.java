package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * PATHS — SUMBER TUNGGAL SEMUA JALUR FOLDER (0.0.3).
 *
 * Keluhan owner 2026-11-15: "berantakan juga struktur folder pembacaan file
 * di folder server Android/data.. struktur folder local di android/data harus
 * juga jelas". Akar keberantakan (audit 0.0.2 — bukti kode):
 *   1) dragonball.db (DATA PEMAIN) tinggal di config/ — campur dengan config.
 *   2) status.json (state runtime dasbor) + www.stamp (penanda mirror) ikut
 *      menumpuk di config/.
 *   3) config/servers.json (snapshot mati era 0.4.4) masih dilindungi whitelist
 *      padahal TIDAK dibaca lagi sejak 0.5.3 (serverlist kini dari paket manual
 *      config-offline/ — harness 18a membuktikan) — dua servers.json = bingung.
 *   4) capture/ masih di whitelist root padahal REKAM mati total 0.5.4 —
 *      sisa sampah menetap di perangkat (komentar lama: "dihapus manual oleh
 *      pemilik" = kontradiksi dengan anti-sampah).
 *   5) String path tersebar di 10+ kelas tanpa sumber tunggal.
 *   6) Tidak ada peta folder di perangkat — pemilik buka file manager =
 *      tebak-tebakan.
 *
 * STRUKTUR BARU Android/data/com.db.local/server/ (tiap folder SATU makna):
 *
 *   server/
 *   ├── PETA.txt          peta folder seluruh isi (dibuat server, dijaga Cleaner)
 *   ├── config/           PENGATURAN — boleh pemilik sunting
 *   │   ├── server.json       mode/port/logTech/rekam/toggle
 *   │   └── game-upstream.txt relay OPSIONAL (kosong = lokal murni)
 *   ├── db/               DATA PEMAIN — dragonball.db — JANGAN DIHAPUS
 *   ├── state/            keadaan runtime — dibuat ulang otomatis
 *   │   ├── status.json       snapshot status dasbor :4000
 *   │   └── www.stamp         penanda versi mirror www/
 *   ├── www/              MIRROR STATIS GAME (dilayani :8080) — jangan disunting
 *   ├── config-offline/   PAKET MANUAL klien — ditimpa dari APK tiap boot
 *   ├── logs/             human.log · events.jsonl · crash.log (nyawa)
 *   └── tmp/              kerja sementara
 *
 * MIGRASI LAYOUT LAMA → BARU (otomatis tiap boot SEBELUM Cleaner, terekam log;
 * perangkat 0.0.2 ke bawah pindah tanpa kehilangan data):
 *   config/dragonball.db (+-journal/-wal/-shm/.corrupt) → db/
 *   config/status.json   → state/
 *   config/www.stamp     → state/
 *   config/servers.json (mati) + capture/ (mati) → DIHAPUS Cleaner (log jelas).
 *
 * SEMUA kelas WAJIB lewat Paths.* — dilarang membuat jalur sendiri
 * (new File(root, "config") tersebar = akar keberantakan, tidak diulang).
 * Harness juga memanggil Paths.init(root) agar perilaku identik dengan device.
 */
public final class Paths {
    /** Versi PETA.txt — naikkan bila struktur berubah (PETA ditulis ulang). */
    public static final String PETA_VERSION = "1";
    /** Nama file peta folder di root server. */
    public static final String PETA_NAME = "PETA.txt";

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

    // ---------------- folder (SATU makna per folder) ----------------

    /** config/ — PENGATURAN server, boleh pemilik sunting (server.json, game-upstream.txt). */
    public static File config() {
        return child("config");
    }

    /** db/ — DATA PEMAIN (dragonball.db + jurnal SQLite) — jangan dihapus. */
    public static File db() {
        return child("db");
    }

    /** state/ — keadaan runtime yang dibuat server (status.json, www.stamp) — dibuat ulang otomatis. */
    public static File state() {
        return child("state");
    }

    /** www/ — MIRROR statis game (dilayani :8080) — jangan disunting. */
    public static File www() {
        return child("www");
    }

    /** config-offline/ — PAKET MANUAL config klien (kanonik, ditimpa dari APK tiap boot). */
    public static File offline() {
        return child("config-offline");
    }

    /** logs/ — log bahasa manusia + mesin + crash (nyawa — tidak boleh dipangkas). */
    public static File logs() {
        return child("logs");
    }

    /** tmp/ — kerja sementara (boleh dihapus kapan saja). */
    public static File tmp() {
        return child("tmp");
    }

    // ---------------- file penting (satu tempat mendefinisikan) ----------------

    /** config/server.json — mode + port + pilihan dasbor. */
    public static File serverJson() {
        return new File(config(), "server.json");
    }

    /** config/game-upstream.txt — relay OPSIONAL isian pemilik (1 baris URL; kosong = lokal murni). */
    public static File gameUpstream() {
        return new File(config(), "game-upstream.txt");
    }

    /** db/dragonball.db — DATABASE pemain (akun/tamu/sesi/data game). */
    public static File dbFile() {
        return new File(db(), "dragonball.db");
    }

    /** state/status.json — snapshot status untuk dasbor. */
    public static File statusJson() {
        return new File(state(), "status.json");
    }

    /** state/www.stamp — penanda versi mirror www/ (bukan di dalam www/). */
    public static File wwwStamp() {
        return new File(state(), "www.stamp");
    }

    /** PETA.txt — peta folder di root server (dibuat/diperbarui server saat boot). */
    public static File petaTxt() {
        return new File(child(""), PETA_NAME);
    }

    /** logs/human.log. */
    public static File humanLog() {
        return new File(logs(), "human.log");
    }

    /** logs/events.jsonl. */
    public static File eventsJsonl() {
        return new File(logs(), "events.jsonl");
    }

    /** logs/crash.log — bukti force close (dibaca saat boot lalu di-rename .read). */
    public static File crashLog() {
        return new File(logs(), "crash.log");
    }

    // ---------------- folder siap pakai ----------------

    /** Pastikan SEMUA folder standar ada (idempoten). Gagal per folder tidak melempar. */
    public static void ensureDirs() {
        File[] all = {config(), db(), state(), www(), offline(), logs(), tmp()};
        for (int i = 0; i < all.length; i++) {
            try {
                if (!all[i].exists()) {
                    all[i].mkdirs();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------- migrasi layout lama → baru (0.0.3) ----------------

    /**
     * Pindahkan isi config/ era 0.0.2 ke bawah ke tempat yang benar (SEBELUM
     * Cleaner &amp; SEBELUM Db.boot). Hanya memindah bila sumber ada DAN target
     * belum ada (tidak pernah menimpa data baru dengan lama). Return teks
     * ringkasan untuk baris log ("" bila tidak ada yang pindah).
     */
    public static String migrateFromLegacyLayout() {
        StringBuilder r = new StringBuilder();
        // 1) DATABASE pemain: config/dragonball.db* → db/
        String[] dbFiles = {"dragonball.db", "dragonball.db-journal", "dragonball.db-wal",
                "dragonball.db-shm", "dragonball.db.corrupt"};
        for (int i = 0; i < dbFiles.length; i++) {
            File src = new File(config(), dbFiles[i]);
            File dst = new File(db(), dbFiles[i]);
            if (moveOne(src, dst)) {
                if (r.length() > 0) {
                    r.append("\n");
                }
                r.append("config/").append(dbFiles[i]).append(" → db/").append(dbFiles[i]);
            }
        }
        // 2) state runtime: config/status.json + config/www.stamp → state/
        String[] stateFiles = {"status.json", "www.stamp"};
        for (int i = 0; i < stateFiles.length; i++) {
            File src = new File(config(), stateFiles[i]);
            File dst = new File(state(), stateFiles[i]);
            if (moveOne(src, dst)) {
                if (r.length() > 0) {
                    r.append("\n");
                }
                r.append("config/").append(stateFiles[i]).append(" → state/").append(stateFiles[i]);
            }
        }
        return r.toString();
    }

    /** Pindah satu file bila sumber ada dan target belum ada; return true bila pindah. */
    private static boolean moveOne(File src, File dst) {
        try {
            if (!src.exists() || !src.isFile()) {
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

    /** Fallback pindah antar-volume: salin byte-per-byte lalu hapus sumber. */
    private static boolean copyMove(File src, File dst) {
        try {
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

    // ---------------- PETA.txt ----------------

    /**
     * Isi PETA.txt — handcraft, SATU makna per folder, tanpa singkatan.
     * Ditulis di root server saat boot (bila hilang / versi peta berubah) —
     * pemilik buka file manager langsung paham apa isi tiap folder.
     */
    public static String petaText() {
        return "============================================================\n"
                + "PETA FOLDER — SERVER LOKAL DB (com.db.local)\n"
                + "Peta versi " + PETA_VERSION + " (APK 0.0.3)\n"
                + "Folder ini : /storage/emulated/0/Android/data/com.db.local/server/\n"
                + "Ditulis otomatis oleh server saat boot.\n"
                + "Entri lain di luar peta ini DIHAPUS otomatis (anti-sampah).\n"
                + "============================================================\n"
                + "\n"
                + "config/             PENGATURAN SERVER — boleh disunting pemilik\n"
                + "  server.json         mode (local/online), port game 8080, sdk 8610,\n"
                + "                      login 8611, dasbor 4000, logTech, rekam, toggleX/Y\n"
                + "  game-upstream.txt   relay OPSIONAL ke server game asli (satu baris URL).\n"
                + "                      Tidak ada / kosong = lokal murni (nol kontak keluar).\n"
                + "\n"
                + "db/                 DATA PEMAIN — JANGAN DIHAPUS (tahan reboot)\n"
                + "  dragonball.db       akun, tamu, sesi SDK, data game (SQLite)\n"
                + "                      (+ -journal/-wal/-shm muncul saat SQLite aktif)\n"
                + "\n"
                + "state/              KEADAAN SERVER — dibuat ulang otomatis, aman dihapus\n"
                + "  status.json         snapshot status untuk dasbor port 4000\n"
                + "  www.stamp           penanda versi mirror www/ (bukan di dalam www/)\n"
                + "\n"
                + "www/                MIRROR STATIS GAME — dilayani port 8080 — JANGAN DISUNTING\n"
                + "  bs/                 H5 Egret: index-native.html, manifest.json, js/,\n"
                + "                      resource/, upgrade/ (version + zip incremental)\n"
                + "                      Diekstrak otomatis dari APK; hilang = terisi ulang.\n"
                + "\n"
                + "config-offline/     PAKET CONFIG MANUAL KLIEN — ditimpa dari APK tiap boot\n"
                + "  setting.json        config login terbaca (sumber setting_BS_Android.bin)\n"
                + "  setting_BS_Android.bin  bin XOR DragonBall (protokol shell asli)\n"
                + "  servers.json        satu entri \"Server Lokal\" (hanya field kontrak klien)\n"
                + "  serversetting.json  loginserver http://127.0.0.1:8611\n"
                + "  manifest.txt        kunci sha256 seluruh isi paket (tak bisa geser diam)\n"
                + "\n"
                + "logs/               LOG — NYAWA (timeline tunggal, tidak dipangkas)\n"
                + "  human.log           log bahasa manusia (rotasi 4 MB -> human.log.1)\n"
                + "  events.jsonl        log mesin (satu JSON per baris)\n"
                + "  crash.log           bukti force close (dibaca saat boot lalu .read)\n"
                + "\n"
                + "tmp/                KERJA SEMENTARA — boleh dihapus kapan saja\n"
                + "============================================================\n"
                + "Sumber kebenaran: README.md — repo github.com/anztdree/db-apk\n"
                + "============================================================\n";
    }

    /**
     * Tulis PETA.txt bila hilang atau versi peta berubah (idempoten — tidak
     * menulis-ulang tiap boot bila tetap). Return "ditulis"/"sudah"/"gagal: …".
     */
    public static String writePetaIfStale() {
        try {
            File f = petaTxt();
            if (f.exists() && f.isFile()) {
                String cur = readText(f);
                if (cur.contains("Peta versi " + PETA_VERSION + " ")) {
                    return "sudah";
                }
            }
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
            w.write(petaText());
            w.close();
            return "ditulis";
        } catch (Throwable t) {
            return "gagal: " + t;
        }
    }

    private static String readText(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            StringBuilder sb = new StringBuilder(2048);
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            in.close();
            return sb.toString();
        } catch (Throwable t) {
            return "";
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
