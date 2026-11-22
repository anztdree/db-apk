package com.db.local.server;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Titik masuk mini-server di APK modifikasi — dipanggil dari
 * Application.onCreate (patch smali com/zhuhuan/game/Application.smali)
 * SEBELUM jalur boot downloader config jalan (peta 16.5).
 *
 * - ekstraksi assets/server_root/**  →  Android/data/com.db.local/server/**
 *   (root SEBELAH files/, fakta 13.1; files/ milik client — TIDAK disentuh)
 *   urutan ekstraksi: db/ → file kecil root → js/ → resource/ → upgrade/;
 *   latch readyEarly dilepas setelah js/ selesai (anti-blokir boot CDN 9528)
 * - 9527 melayani /bs/db/android/*.bin LANGSUNG dari assets (anti-race boot)
 * - 5 listener bind 127.0.0.1: 9527/9528/5050/9530/4000 (fallback 4001)
 * - SEMUA exception ditelan (dilaporkan ke logger) — jangan pernah crash app
 */
public final class LocalServerStarter {
    private static volatile boolean started = false;

    private LocalServerStarter() {}

    public static void start(final Context ctx) {
        if (started) return;
        started = true;
        try {
            final File extBase = ctx.getExternalFilesDir(null);
            final File base = extBase != null ? extBase.getParentFile() : null;
            final File serverRoot = new File(base != null ? base : ctx.getFilesDir(), "server");
            LoggerCore.serverRootPath = serverRoot.getAbsolutePath();

            final CountDownLatch readyEarly = new CountDownLatch(1);
            final Thread extractor = new Thread(() -> {
                try {
                    long n = extractRec(ctx.getAssets(), "server_root", serverRoot, readyEarly, 0);
                    LoggerCore.extracted = true;
                    LoggerCore.extractedFiles = n;
                    readyEarly.countDown(); // jaga-jaga bila folder js/ tidak ditemukan
                } catch (Throwable t) {
                    logBoot("ekstraksi gagal: " + t);
                    readyEarly.countDown();
                }
            }, "server-extract");
            extractor.setDaemon(true);
            extractor.start();

            final StaticHandler.AssetsSource assets = rel -> {
                try {
                    InputStream in = ctx.getAssets().open(rel);
                    try {
                        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
                        return b.toByteArray();
                    } finally {
                        in.close();
                    }
                } catch (IOException ioe) {
                    return null;
                }
            };

            ServerCore.StartResult res = ServerCore.start(serverRoot, assets, readyEarly);
            logBoot("mini-server aktif; logger di port " + res.loggerPort
                    + (res.loggerFallback ? " (fallback)" : "") + "; serverRoot=" + serverRoot);
        } catch (Throwable t) {
            logBoot("start gagal: " + t);
        }
    }

    private static void logBoot(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGGER;
        e.method = "BOOT";
        e.path = msg;
        e.status = 0;
        LoggerCore.log(e);
    }

    /** ekstraksi rekursif; latch dilepas setelah folder js/ pada bs/ selesai */
    static long extractRec(AssetManager am, String assetPath, File targetDir,
                           CountDownLatch early, long count) throws IOException {
        String[] list;
        try {
            list = am.list(assetPath);
        } catch (IOException ioe) {
            return count;
        }
        if (list == null || list.length == 0) {
            copyAsset(am, assetPath, targetDir);
            return count + 1;
        }
        if (!targetDir.exists()) targetDir.mkdirs();
        List<String> sorted = new ArrayList<>();
        Collections.addAll(sorted, list);
        Collections.sort(sorted, (a, b) -> rank(a) - rank(b));
        for (String name : sorted) {
            String childAsset = assetPath + "/" + name;
            File childTarget = new File(targetDir, name);
            String[] sub;
            try {
                sub = am.list(childAsset);
            } catch (IOException ioe) {
                sub = null;
            }
            if (sub != null && sub.length > 0) {
                count = extractRec(am, childAsset, childTarget, early, count);
            } else {
                copyAsset(am, childAsset, childTarget);
                count++;
            }
        }
        if ("js".equals(targetDir.getName()) && "bs".equals(targetDir.getParentFile().getName())) {
            early.countDown();
        }
        return count;
    }

    private static int rank(String name) {
        if ("db".equals(name)) return 0;
        if ("index-native.html".equals(name) || "manifest.json".equals(name)
                || "index.html".equals(name) || "manifest_ts.json".equals(name)
                || "base.version".equals(name) || "resource.version".equals(name)) return 1;
        if ("js".equals(name)) return 2;
        if ("resource".equals(name)) return 3;
        return 4;
    }

    private static void copyAsset(AssetManager am, String assetPath, File target) {
        InputStream in = null;
        OutputStream out = null;
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            in = am.open(assetPath);
            out = new FileOutputStream(target);
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } catch (IOException ioe) {
            LoggerCore.Entry e = new LoggerCore.Entry();
            e.port = LoggerCore.PORT_LOGGER;
            e.method = "EXTRACT";
            e.path = assetPath;
            e.status = 500;
            e.error = String.valueOf(ioe);
            LoggerCore.log(e);
        } finally {
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (out != null) out.close(); } catch (IOException ignored) {}
        }
    }
}
