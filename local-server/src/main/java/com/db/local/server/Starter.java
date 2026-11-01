package com.db.local.server;

import android.content.Context;

import java.io.File;

/**
 * Titik nyalakan server lokal. Dipanggil otomatis lewat BootProvider
 * (provider diinisialisasi Android SEBELUM Application.onCreate → jalan duluan).
 *
 * Yang dinyalakan (urutan):
 * 1) PEMBERSIH folder — Android/data/<paket>/server hanya berisi config/www/logs/tmp
 * 2) Folder root Android/data/<paket>/server + mirror www/ diekstrak OTOMATIS dari APK
 * 3) Dasbor debug di port 4000 (hanya developer — game tidak tahu port ini)
 * 4) Port GAME di port 8080 — endpoint client menunjuk ke sini (LOCAL/ONLINE)
 * 5) Monitor kesehatan server asli (log hanya saat berubah + denyut 10 menit)
 * 6) Tombol melayang switch mode yang BISA DIGESER (drag) — posisi tersimpan
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
            "configus.sjmobilegame.com:443",
            "dragonh5cdn.popoh5.com:443",
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
        try {
            mk(logs);
            mk(conf);
            mk(tmp);
            mk(www);
        } catch (Throwable firstTry) {
            try {
                ext = false;
                root = new File(c.getFilesDir(), "server");
                serverRoot = root;
                logs = new File(root, "logs");
                conf = new File(root, "config");
                tmp = new File(root, "tmp");
                www = new File(root, "www");
                mk(logs);
                mk(conf);
                mk(tmp);
                mk(www);
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

        // ---------- 1b) PEMBERSIH: folder server hanya boleh config/www/logs/tmp ----------
        // Aturan folder bersih (README §2): apa pun di luar whitelist — peninggalan
        // build lama, salinan APK, file analisis, status.json nyasar — DIHAPUS.
        try {
            Cleaner.Result cr = Cleaner.clean(root);
            if (cr.removed > 0) {
                HumanLog.ev('✔', "Folder server dibersihkan: " + cr.removed + " entri di luar whitelist "
                        + "dihapus (" + HumanLog.fmtBytes(cr.bytes) + ") — " + cr.names, null);
            }
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Pembersih folder gagal jalan — folder tetap dipakai apa adanya",
                    String.valueOf(t));
        }

        // ---------- 2) www/ = mirror server ASLI diekstrak OTOMATIS dari APK ----------
        // Seluruh isi fisik server asli (setting bin, H5 Egret, js, resource
        // properties, version, upgrade.json, size.json, base.zip, all.zip, zip
        // incremental) dibawa di assets/server/www/ dan terekstrak sendiri ke
        // www/ sebelum server menyala — TIDAK ADA langkah manual. Penanda versi
        // disimpan di config/www.stamp — www/ tetap murni cermin server.
        try {
            Payload.Result pr = Payload.extract(appCtx, www);
            if (pr.extracted) {
                HumanLog.ev('✔', "Mirror server asli diekstrak OTOMATIS dari APK → www/ ("
                                + pr.files + " file · " + HumanLog.fmtBytes(pr.bytes)
                                + ") — tanpa langkah manual", pr.note);
            } else {
                HumanLog.ev('ℹ', "Mirror www/ sudah terpasang (" + pr.files
                        + " file) — tidak ada yang perlu diulang", pr.note);
            }
            StatusStore.markWww(pr.files, pr.bytes);
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Ekstraksi mirror www/ gagal — mode LOCAL melayani apa yang ada; game tetap boot (fallback 404)",
                    String.valueOf(t));
        }
        HumanLog.ev('✔', "Folder server siap: " + root.getAbsolutePath()
                        + " (" + (ext ? "penyimpanan eksternal" : "penyimpanan internal — folder eksternal tidak tersedia") + ")",
                null);

        // ---------- 3) config server.json ----------
        Config.load(root);
        StatusStore.configure(Config.debugPort(), Config.gamePort(), Config.mode(),
                root.getAbsolutePath(), c.getPackageName());
        // status.json hidup di config/ — root folder tetap bersih (aturan §2)
        StatusStore.setStatusFile(new File(root, "config/status.json"));
        HumanLog.ev('ℹ', "Mode aktif: " + Config.mode().toUpperCase() + " — ONLINE = permintaan game diteruskan ke "
                + "server asli · LOCAL = dilayani dari folder www/ (mirror server asli, ekstraksi otomatis dari APK)", null);

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
            GameServer gs = new GameServer(c, "127.0.0.1", Config.gamePort(), www);
            gs.start(2000, true);
            gameServer = gs;
            StatusStore.markGame(true);
            HumanLog.ev('✔', "Port game hidup di " + Config.gamePort() + " — endpoint client menunjuk ke sini "
                    + "(mode " + Config.mode().toUpperCase() + ") · halaman http://127.0.0.1:" + Config.gamePort()
                    + " di browser berisi tombol ganti mode", null);
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
        monitor = new Monitor(DEFAULT_TARGETS, Config.monitorSec());
        monitor.startBackground();
        monitor.runOnce();
        StatusStore.persist();

        // ---------- 7) tombol melayang switch mode (BISA DIGESER) ----------
        FloatingToggle.setup(c);
    }

    static File tmpDir() {
        File f = tmpDir;
        if (f != null) {
            return f;
        }
        return new File(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    /** Root folder server (Android/data/<paket>/server) — dipakai dasbor (/api/www/list). */
    static File serverRoot() {
        return serverRoot;
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

}
