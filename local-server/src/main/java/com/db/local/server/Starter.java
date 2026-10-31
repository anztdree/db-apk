package com.db.local.server;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * Titik nyalakan server lokal. Dipanggil otomatis lewat BootProvider
 * (provider diinisialisasi Android SEBELUM Application.onCreate → jalan duluan).
 * Semua langkah dibungkus try/catch: server lokal TIDAK PERNAH boleh membuat game gagal jalan.
 */
public final class Starter {
    private static final Object BOOTLOCK = new Object();
    private static volatile boolean bootRequested = false;
    private static volatile Context appCtx;
    private static volatile File serverRoot;
    private static volatile File tmpDir;
    private static volatile LocalServer server;
    private static volatile Monitor monitor;

    private static final String[] DEFAULT_TARGETS = {
            "dragon.sjmobilegame.com:82",
            "configus.sjmobilegame.com:80",
            "qsdk.t4game.com:443"
    };

    private static final String DEFAULT_CONFIG_JSON =
            "{\n"
                    + "  \"port\": 4000,\n"
                    + "  \"bind\": \"127.0.0.1\",\n"
                    + "  \"mode\": \"ONLINE\",\n"
                    + "  \"monitorSec\": 60,\n"
                    + "  \"targets\": [\"dragon.sjmobilegame.com:82\", \"configus.sjmobilegame.com:80\", \"qsdk.t4game.com:443\"]\n"
                    + "}\n";

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
        File data = new File(root, "data");
        File resp = new File(root, "responses");
        File tmp = new File(data, "tmp");
        try {
            mk(logs);
            mk(conf);
            mk(data);
            mk(resp);
            mk(tmp);
        } catch (Throwable firstTry) {
            try {
                ext = false;
                root = new File(c.getFilesDir(), "server");
                serverRoot = root;
                logs = new File(root, "logs");
                conf = new File(root, "config");
                data = new File(root, "data");
                resp = new File(root, "responses");
                tmp = new File(data, "tmp");
                mk(logs);
                mk(conf);
                mk(data);
                mk(resp);
                mk(tmp);
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
        writeOnce(new File(resp, "README.txt"),
                "Folder ini dipakai di mode M2/M3 (passthrough & ambil alih) untuk menyimpan jawaban tiruan per endpoint.\n"
                        + "Mode sekarang: M1 — folder masih kosong, itu normal.\n");
        writeOnce(new File(data, "README.txt"),
                "Folder data kerja server lokal (file sementara/buffer).\n");
        HumanLog.ev('✔', "Folder server siap: " + root.getAbsolutePath()
                        + " (" + (ext ? "penyimpanan eksternal" : "penyimpanan internal — folder eksternal tidak tersedia") + ")",
                null);

        // ---------- 2) config ----------
        int port = 4000;
        int monSec = 60;
        String[] targets = DEFAULT_TARGETS;
        File cfg = new File(conf, "server.json");
        try {
            if (!cfg.exists()) {
                write(cfg, DEFAULT_CONFIG_JSON);
            } else {
                String raw = readText(cfg);
                port = (int) grepLong(raw, "\"port\"", port);
                monSec = (int) grepLong(raw, "\"monitorSec\"", monSec);
                String tg = grepArray(raw, "\"targets\"");
                if (tg != null && tg.trim().length() > 0) {
                    targets = tg.split("\\|");
                }
            }
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Config server gagal dibaca — memakai nilai bawaan (port 4000, pantau 60 dtk)",
                    String.valueOf(t));
        }
        if (port <= 0 || port > 65535) {
            port = 4000;
        }
        try {
            ServerMode.init(cfg); // switch mode ONLINE/LOCAL (server.json) — nilai lama "M1" dimaknai ONLINE
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Switch mode gagal dibaca — memakai mode ONLINE", String.valueOf(t));
        }
        StatusStore.configure(port, "127.0.0.1", ServerMode.get(), root.getAbsolutePath(), c.getPackageName());
        StatusStore.setStatusFile(new File(root, "status.json"));
        if (ServerMode.isLocal()) {
            HumanLog.ev('ℹ', "Mode LOCAL aktif — server lokal siap melayani konfigurasi bootstrap dari cermin lokal ("
                    + Mirror.bspathShort() + "); game belum diarahkan ke sini (itu fasa M3)", null);
        } else {
            HumanLog.ev('ℹ', "Mode ONLINE aktif — traffic game TIDAK diubah; server asli tetap melayani seperti biasa. "
                    + "Ganti ke mode LOCAL lewat dasbor bila ingin bootstrap dilayani dari cermin", null);
        }

        // ---------- 3) hidupkan HTTP ----------
        try {
            LocalServer ls = new LocalServer(c, "127.0.0.1", port);
            ls.loadDashboard();
            ls.start(2000, true);
            server = ls;
            StatusStore.markStarted(System.currentTimeMillis(), true);
            HumanLog.ev('✔', "Server lokal hidup di port " + port + " (hanya di HP ini) — buka http://127.0.0.1:"
                    + port + " di browser HP untuk dasbor", null);
            Notifier.show(c, ServerMode.get(), port);
        } catch (Throwable t) {
            StatusStore.markStarted(System.currentTimeMillis(), false);
            HumanLog.ev('⚠', "Server lokal GAGAL hidup di port " + port + " — kemungkinan port sedang dipakai proses lain; "
                    + "game tetap jalan normal tanpa dasbor", String.valueOf(t));
            StatusStore.persist();
            return;
        }

        // ---------- 4) monitor server asli ----------
        try {
            ApkReader.init(c);
        } catch (Throwable ignored) {
        }
        try {
            Mirror.init(); // cermin bootstrap — salinan bersebelahan, asli tidak disentuh
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Cermin bootstrap gagal disiapkan — mode LOCAL tetap bisa dipakai tanpa cermin",
                    String.valueOf(t));
        }
        monitor = new Monitor(targets, monSec);
        HumanLog.ev('▶', "Mulai memantau " + targets.length + " server asli setiap " + monSec
                + " dtk: " + joinShort(targets), null);
        monitor.startBackground();
        monitor.runOnce(false);
        StatusStore.persist();
    }

    static File tmpDir() {
        File f = tmpDir;
        if (f != null) {
            return f;
        }
        return new File(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    static File serverRoot() {
        File f = serverRoot;
        return (f != null) ? f : new File(".");
    }

    static String defaultConfigJson() {
        return DEFAULT_CONFIG_JSON;
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
                write(f, content);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void write(File f, String content) throws Exception {
        Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
        w.write(content);
        w.close();
    }

    private static String readText(File f) throws Exception {
        Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
        StringBuilder sb = new StringBuilder(1024);
        char[] buf = new char[2048];
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

    private static String grepArray(String raw, String key) {
        int i = raw.indexOf(key);
        if (i < 0) {
            return null;
        }
        int a = raw.indexOf('[', i);
        int b = (a >= 0) ? raw.indexOf(']', a) : -1;
        if (a < 0 || b < 0) {
            return null;
        }
        String inner = raw.substring(a + 1, b);
        String[] parts = inner.split(",");
        StringBuilder sb = new StringBuilder(128);
        for (int j = 0; j < parts.length; j++) {
            String p = parts[j].trim().replace("\"", "");
            if (p.length() == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private static String joinShort(String[] arr) {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }
}
