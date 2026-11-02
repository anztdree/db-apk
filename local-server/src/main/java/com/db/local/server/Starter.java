package com.db.local.server;

import android.content.Context;
import fi.iki.elonen.NanoHTTPD;

import java.io.File;

/**
 * Titik nyalakan server lokal. Dipanggil otomatis lewat BootProvider
 * (provider diinisialisasi Android SEBELUM Application.onCreate → jalan duluan).
 *
 * Yang dinyalakan (urutan):
 * 1) PEMBERSIH folder — Android/data/<paket>/server hanya berisi config/www/logs/tmp
 * 2) Folder root Android/data/<paket>/server + mirror www/ diekstrak OTOMATIS dari APK
 *    + default login (config/servers.json — daftar server snapshot asli) bila belum ada
 * 3) Dasbor debug di port 4000 (hanya developer — game tidak tahu port ini)
 * 4) Port GAME di port 8080 — endpoint client menunjuk ke sini (LOCAL/ONLINE)
 * 5) Server LOGIN lokal di port 8610 — engine.io v3 + socket.io v2 (PR3 0.4.0;
 *    loginGame/GetServerList dilayani dari data lokal — README §2.10)
 * 6) Monitor kesehatan server asli (log hanya saat berubah + denyut 10 menit)
 * 7) Tombol melayang switch mode yang BISA DIGESER (drag) — posisi tersimpan
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
    private static volatile LoginServer loginServer;
    private static volatile Monitor monitor;

    private static final String[] DEFAULT_TARGETS = {
            "dragon.sjmobilegame.com:82",
            "configus.sjmobilegame.com:443",
            "dragonh5cdn.popoh5.com:443",
            "qsdk.t4game.com:443",
            "login.popoh5.com:610"
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

        // ---------- 1a) hook kejadian loop accept (0.4.1) ----------
        // Error tak terduga di loop penerima koneksi NanoHTTPD kini TERCATAT —
        // dulu thread accept bisa mati diam-diam sehingga port “hidup mati”:
        // game hanya melihat koneksi ditolak tanpa satu baris log (gejala dialog
        // “Please Check NetWork” tanpa petunjuk — koreksi 0.4.1).
        try {
            NanoHTTPD.acceptCrashListener = new NanoHTTPD.CrashListener() {
                public void onAcceptLoopCrash(String port, Throwable t) {
                    try {
                        HumanLog.ev('⚠', "Port " + port + " menangkap error tak terduga di loop penerima koneksi — server LANJUT melayani",
                                String.valueOf(t));
                    } catch (Throwable ignored) {
                    }
                }
            };
        } catch (Throwable ignored) {
        }

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

        // ---------- 2b) default LOGIN LOKAL: config/servers.json (PR3 0.4.0) ----------
        // Dibawa APK (assets/server/login/servers.json = snapshot ASLI daftar server
        // login.popoh5.com, hasil probe §2.10) — diekstrak SEKALI; file yang sudah
        // ada TIDAK ditimpa (boleh diubah pemilik tanpa rebuild).
        try {
            File confDir = new File(root, "config");
            String note = Payload.extractLoginDefaults(appCtx, confDir);
            if (note != null) {
                HumanLog.ev('✔', "Default login lokal siap — " + note, null);
            }
            LoginData.init(confDir);
            SdkData.init(confDir);
            HumanLog.ev('ℹ', "Login lokal: guest " + LoginData.localUserId()
                    + " · daftar server " + LoginData.serverCount() + " server", null);
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Persiapan data login lokal gagal — server login tetap dicoba hidup",
                    String.valueOf(t));
        }

        // ---------- 3) config server.json ----------
        Config.load(root);
        StatusStore.configure(Config.debugPort(), Config.gamePort(), Config.mode(),
                root.getAbsolutePath(), c.getPackageName());
        StatusStore.setLoginPort(Config.loginPort());
        try {
            StatusStore.setLoginUser(LoginData.localUserId());
        } catch (Throwable ignored) {
        }
        // status.json hidup di config/ — root folder tetap bersih (aturan §2)
        StatusStore.setStatusFile(new File(root, "config/status.json"));
        HumanLog.ev('ℹ', "Mode: " + Config.mode().toUpperCase()
                + ("local".equals(Config.mode())
                        ? " — permintaan game dilayani dari www/ (mirror server asli)"
                        : " — permintaan game diteruskan ke server asli"), null);

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
            HumanLog.ev('✔', "Port game hidup di " + Config.gamePort() + " (mode " + Config.mode().toUpperCase()
                    + ") — endpoint client menunjuk ke sini", null);
        } catch (Throwable t) {
            StatusStore.markGame(false);
            HumanLog.ev('⚠', "Port game GAGAL hidup di " + Config.gamePort()
                    + " — game tidak akan menemukan server lokal; cek port atau ubah gamePort di server.json",
                    String.valueOf(t));
        }

        // ---------- 5b) server LOGIN lokal (PR3 0.4.0) ----------
        // Protokol SAMA dengan login.popoh5.com:610 (engine.io v3 + socket.io v2);
        // dipakai game bila mode LOCAL menulis-ulang endpoint login. Hidup selalu
        // (bersebelahan) — mode ONLINE tidak menunjuk ke sini.
        try {
            LoginServer ls = new LoginServer("127.0.0.1", Config.loginPort());
            ls.start(2000, true);
            loginServer = ls;
            StatusStore.markLogin(true);
            HumanLog.ev('✔', "Server login lokal hidup di port " + Config.loginPort()
                    + " — protokol engine.io v3 + socket.io v2 (action: loginGame, GetServerList, "
                    + "LoginAnnounce, Save*) + TAHAP SDK (API quickgame /v1/* + loginchecknative — "
                    + "layar login SDK asli dilayani lokal, 0.4.2)", null);
        } catch (Throwable t) {
            StatusStore.markLogin(false);
            HumanLog.ev('⚠', "Server login lokal GAGAL hidup di port " + Config.loginPort()
                    + " — login guest lokal tidak akan berfungsi; cek loginPort di server.json",
                    String.valueOf(t));
        }
        StatusStore.persist();
        if (!dashOk && gameServer == null) {
            return; // tidak ada yang bisa dilanjutkan
        }

        // ---------- 5c) PENJAGA PORT (0.4.1) ----------
        // Probe 8080 + 8610 tiap 5 dtk. Dulu kegagalan level KONEKSI tidak
        // terlihat sama sekali di log (server hanya mencatat permintaan yang
        // sampai) — game menampilkan “Please Check NetWork” tanpa petunjuk.
        // Kini: port mati = ⚠ di log (laporan pertama + tiap ±1 menit),
        // pemulihan = ✔. Probe = koneksi kosong, tidak dihitung permintaan.
        try {
            Thread wd = new Thread(new Runnable() {
                public void run() {
                    int gameMiss = 0;
                    int loginMiss = 0;
                    while (true) {
                        try {
                            Thread.sleep(5000L);
                        } catch (InterruptedException ie) {
                            return;
                        }
                        gameMiss = watchdogProbe(Config.gamePort(), "game", gameMiss);
                        loginMiss = watchdogProbe(Config.loginPort(), "login", loginMiss);
                    }
                }
            }, "dblsf-watchdog");
            wd.setDaemon(true);
            wd.setPriority(Thread.MIN_PRIORITY);
            wd.start();
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Penjaga port gagal dinyalakan — pemantauan kesehatan port tidak aktif", String.valueOf(t));
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

    /**
     * Probe satu port lokal (koneksi kosong, 1,5 dtk timeout). return jumlah
     * kegagalan beruntun — 0 berarti port sehat. Laporan log: kegagalan PERTAMA
     * + tiap 12 kegagalan beruntun (±1 menit) + pemulihan.
     */
    private static int watchdogProbe(int port, String nama, int missCount) {
        boolean ok;
        java.net.Socket s = new java.net.Socket();
        try {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 1500);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        } finally {
            try {
                s.close();
            } catch (Throwable ignored) {
            }
        }
        if (ok) {
            if (missCount >= 3) {
                try {
                    HumanLog.ev('✔', "Port " + nama + " (" + port + ") HIDUP KEMBALI — sebelumnya tidak merespons", null);
                } catch (Throwable ignored) {
                }
            }
            return 0;
        }
        int miss = missCount + 1;
        if (miss == 1 || miss % 12 == 0) {
            try {
                HumanLog.ev('⚠', "Port " + nama + " (" + port + ") TIDAK merespons (" + (miss * 5)
                        + " dtk) — game tidak akan bisa menghubungi server lokal bila berlanjut", null);
            } catch (Throwable ignored) {
            }
        }
        return miss;
    }

    private static void mk(File d) {
        if (!d.exists()) {
            d.mkdirs();
        }
    }

}
