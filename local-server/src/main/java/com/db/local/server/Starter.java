package com.db.local.server;

import android.content.Context;

import java.io.File;

/**
 * Titik nyalakan server lokal. Dipanggil otomatis lewat BootProvider
 * (provider diinisialisasi Android SEBELUM Application.onCreate → jalan duluan).
 *
 * Yang dinyalakan:
 * 1) Folder root Android/data/<paket>/server (config, logs, tmp, www)
 * 2) Dasbor debug di port 4000 (hanya developer — game tidak tahu port ini)
 * 3) Port GAME di port 8080 — endpoint client menunjuk ke sini (LOCAL/ONLINE)
 * 4) Tombol melayang switch mode (overlay)
 * 5) Monitor kesehatan server asli (log hanya saat berubah + denyut 10 menit)
 *
 * Semua langkah dibungkus try/catch: server lokal TIDAK PERNAH boleh
 * membuat game gagal jalan.
 */
public final class Starter {
    private static final Object BOOTLOCK = new Object();
    private static volatile boolean bootRequested = false;
    private static volatile Context appCtx;
    private static volatile File serverRoot;
    private static volatile File tmpDir;
    private static volatile LocalServer dashboard;
    private static volatile GameServer gameServer;
    private static volatile Monitor monitor;

    private static final String[] DEFAULT_TARGETS = {
            "dragon.sjmobilegame.com:82",
            "configus.sjmobilegame.com:80",
            "qsdk.t4game.com:443"
    };

    private Starter() {
    }

    /** Dipanggil BootProvider.onCreate — cepat, tidak memblok thread utama. */
    public static void boot(Context ctx) {
        synchronized (BOOTLOCK) {
            if (bootRequested) {
                return;
            }
            bootRequested = true;
        }
        try {
            Context ac = ctx.getApplicationContext();
            appCtx = (ac != null) ? ac : ctx;
        } catch (Throwable t) {
            appCtx = ctx;
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    doBoot();
                } catch (Throwable ex) {
                    try {
                        HumanLog.ev('⚠', "Server lokal gagal disiapkan — game TIDAK terpengaruh dan tetap jalan normal",
                                String.valueOf(ex));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "dblsf-boot");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private static void doBoot() {
        Context c = appCtx;
        if (c == null) {
            return;
        }

        // ---------- 1) folder root: Android/data/<paket>/server ----------
        File root = null;
        boolean ext = true;
        try {
            File extFiles = c.getExternalFilesDir(null);
            if (extFiles != null) {
                root = new File(extFiles.getParentFile(), "server");
            }
        } catch (Throwable ignored) {
        }
        if (root == null) {
            ext = false;
            root = new File(c.getFilesDir().getParentFile(), "server");
        }
        File logs = new File(root, "logs");
        File conf = new File(root, "config");
        File tmp = new File(root, "tmp");
        File www = new File(root, "www");
        File wwwBs = new File(www, "bs/db/android");
        File wwwDz = new File(www, "lzceshi/native_setting/Android");
        try {
            mk(logs);
            mk(conf);
            mk(tmp);
            mk(wwwBs);
            mk(wwwDz);
        } catch (Throwable firstTry) {
            try {
                ext = false;
                root = new File(c.getFilesDir(), "server");
                serverRoot = root;
                logs = new File(root, "logs");
                conf = new File(root, "config");
                tmp = new File(root, "tmp");
                www = new File(root, "www");
                wwwBs = new File(www, "bs/db/android");
                wwwDz = new File(www, "lzceshi/native_setting/Android");
                mk(logs);
                mk(conf);
                mk(tmp);
                mk(wwwBs);
                mk(wwwDz);
            } catch (Throwable secondTry) {
                HumanLog.ev('⚠', "Folder server tidak bisa dibuat — server tetap dicoba hidup tanpa file log di disk",
                        String.valueOf(secondTry));
            }
        }
        serverRoot = root;
        tmpDir = tmp;
        try {
            System.setProperty("java.io.tmpdir", tmp.getAbsolutePath());
        } catch (Throwable ignored) {
        }
        HumanLog.init(logs);

        // ---------- 2) www/ = tempat isi config ASLI ditaruh MANUAL ----------
        // Aturan pemilik: BUKAN sistem yang menyalin — salinan dilakukan manual.
        // Yang disiapkan otomatis hanya struktur folder + dua file versi minimum
        // (110 = versi bawaan APK → game tidak minta hot-update; hasil analisis §2.8).
        try {
            writeOnce(new File(wwwBs, "base.version"), "110\n");
            writeOnce(new File(wwwBs, "resource.version"), "110\n");
            writeOnce(new File(www, "README.txt"),
                    "Folder www = tempat ISI config ASLI ditaruh MANUAL (bukan salinan otomatis).\n"
                            + "Struktur folder = path URL server asli, sehingga game yang menunjuk ke port lokal\n"
                            + "menemukan file di alamat yang sama seperti di server asli:\n"
                            + "  www/bs/db/android/...                  <- cermin configus.sjmobilegame.com/bs/db/android\n"
                            + "  www/lzceshi/native_setting/Android/... <- cermin dragon.sjmobilegame.com:82/lzceshi/native_setting/Android\n"
                            + "\n"
                            + "base.version / resource.version diisi '110' (= versi bawaan APK) supaya game\n"
                            + "tidak meminta hot-update. File lain ditaruh manual; yang belum ada dijawab 404\n"
                            + "dan game melanjutkan boot (terbukti dari analisis smali - 'Server base.version is Not Exist').\n");
        } catch (Throwable ignored) {
        }
        HumanLog.ev('✔', "Folder server siap: " + root.getAbsolutePath()
                        + " (" + (ext ? "penyimpanan eksternal" : "penyimpanan internal — folder eksternal tidak tersedia") + ")",
                null);

        // ---------- 3) config server.json ----------
        Config.load(root);
        StatusStore.configure(Config.debugPort(), Config.gamePort(), Config.mode(),
                root.getAbsolutePath(), c.getPackageName());
        StatusStore.setStatusFile(new File(root, "status.json"));
        HumanLog.ev('ℹ', "Mode aktif: " + Config.mode().toUpperCase() + " — ONLINE = permintaan game diteruskan ke "
                + "server asli · LOCAL = dilayani dari folder www/ (isi config asli yang ditaruh manual)", null);

        // ---------- 4) dasbor debug (port 4000) ----------
        boolean dashOk = false;
        try {
            LocalServer ls = new LocalServer(c, "127.0.0.1", Config.debugPort());
            ls.loadDashboard();
            ls.start(2000, true);
            dashboard = ls;
            dashOk = true;
            StatusStore.markStarted(System.currentTimeMillis(), true);
            HumanLog.ev('✔', "Dasbor debug hidup di port " + Config.debugPort()
                    + " (hanya developer) — buka http://127.0.0.1:" + Config.debugPort() + " di browser HP", null);
        } catch (Throwable t) {
            StatusStore.markStarted(System.currentTimeMillis(), false);
            HumanLog.ev('⚠', "Dasbor debug GAGAL hidup di port " + Config.debugPort()
                    + " — kemungkinan port dipakai proses lain; port game tetap dicoba", String.valueOf(t));
        }

        // ---------- 5) port GAME (dipakai client) ----------
        try {
            GameServer gs = new GameServer("127.0.0.1", Config.gamePort(), www);
            gs.start(2000, true);
            gameServer = gs;
            StatusStore.markGame(true);
            HumanLog.ev('✔', "Port game hidup di " + Config.gamePort() + " — endpoint client menunjuk ke sini "
                    + "(mode " + Config.mode().toUpperCase() + ")", null);
        } catch (Throwable t) {
            StatusStore.markGame(false);
            HumanLog.ev('⚠', "Port game GAGAL hidup di " + Config.gamePort()
                    + " — game tidak akan menemukan server lokal; cek port atau ubah gamePort di server.json",
                    String.valueOf(t));
        }
        StatusStore.persist();
        if (!dashOk && gameServer == null) {
            return; // tidak ada yang bisa dilanjutkan
        }

        // ---------- 6) monitor server asli ----------
        try {
            ApkReader.init(c);
        } catch (Throwable ignored) {
        }
        monitor = new Monitor(DEFAULT_TARGETS, Config.monitorSec());
        monitor.startBackground();
        monitor.runOnce();
        StatusStore.persist();

        // ---------- 7) tombol melayang switch mode ----------
        FloatingToggle.setup(c);
    }

    static File tmpDir() {
        File f = tmpDir;
        if (f != null) {
            return f;
        }
        return new File(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    static Monitor monitor() {
        return monitor;
    }

    // ---------- helper kecil ----------

    private static void mk(File d) {
        if (!d.exists()) {
            d.mkdirs();
        }
    }

    private static void writeOnce(File f, String content) {
        try {
            if (!f.exists()) {
                File p = f.getParentFile();
                if (p != null && !p.exists()) {
                    p.mkdirs();
                }
                java.io.Writer w = new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(f, false), "UTF-8");
                w.write(content);
                w.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
