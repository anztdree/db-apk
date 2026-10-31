package com.db.local.server;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * MIRROR SERVER ASLI — dibawa DI DALAM APK (assets/server/www/) dan diekstrak
 * OTOMATIS ke &lt;root&gt;/www/ saat aplikasi hidup (BootProvider), SEBELUM server
 * menyala. TIDAK ADA langkah manual — "semua itu terekstrak ke sana".
 *
 * Isi mirror v3 (snapshot nyata server asli yang masih hidup, hasil probe 2025):
 *   bs/db/android/setting_BS_Android.bin      (cermin configus, XOR "DragonBall")
 *   bs/index-native.html · bs/manifest.json · bs/js/* (15 file H5 Egret)
 *   bs/resource/properties/clientversion.json · serversetting.json
 *   bs/upgrade/base.version · resource.version · upgrade.json · size.json
 *   bs/upgrade/base.zip · all.zip · 11386–11389_11390.zip (zip resource)
 *   lzceshi/native_setting/Android/*          (cermin dragon, bila tersedia)
 *
 * - Struktur folder www/ = path URL server asli, sehingga port game lokal
 *   menjawab di alamat yang sama seperti server asli.
 * - Ekstraksi ulang otomatis bila: penanda versi berubah, atau ada file mirror
 *   yang hilang (mis. sebagian dihapus / clear data sebagian).
 * - PENANDA disimpan di config/www.stamp (BUKAN di dalam www/) — www/ tetap
 *   murni cermin server, tanpa file asing (aturan folder bersih §2).
 * - File tambahan di luar mirror TIDAK dihapus (mis. hasil self-seed).
 * - Gagal ekstraksi TIDAK PERNAH menggagalkan boot — game tetap jalan
 *   (fallback 404, terbukti dari analisis dex §2.8).
 */
public final class Payload {
    /** Versi isi mirror — NAIKKAN bila isi berubah, www/ ikut diperbarui otomatis. */
    public static final String VERSION = "3";

    private static final String ASSET_DIR = "server/www";
    private static final String MARKER_NAME = "www.stamp";

    private Payload() {
    }

    /** Ringkasan hasil ekstraksi untuk log/dasbor. */
    public static final class Result {
        public int files;
        public long bytes;
        public boolean extracted;
        public String note = "";
    }

    /**
     * Pastikan www/ berisi mirror terbaru. Dipanggil Starter SEBELUM server hidup.
     * Tidak pernah melempar exception — kegagalan dilaporkan lewat note.
     */
    public static Result extract(Context ctx, File wwwRoot) {
        Result r = new Result();
        try {
            if (wwwRoot == null) {
                r.note = "folder www tidak tersedia";
                return r;
            }
            if (!wwwRoot.exists()) {
                wwwRoot.mkdirs();
            }
            AssetManager am = ctx.getAssets();
            List<String> rel = new ArrayList<String>();
            collect(am, ASSET_DIR, rel);
            r.files = rel.size();
            if (rel.isEmpty()) {
                r.note = "mirror kosong di APK (assets/" + ASSET_DIR + " tidak berisi file)";
                return r;
            }
            if (!needsExtract(wwwRoot) && allPresent(wwwRoot, rel)) {
                r.extracted = false;
                r.note = "penanda cocok (versi " + VERSION + ", " + rel.size() + " file utuh)";
                return r;
            }
            int ok = 0;
            long bytes = 0;
            StringBuilder gagal = new StringBuilder();
            for (int i = 0; i < rel.size(); i++) {
                String relPath = rel.get(i);
                try {
                    File out = new File(wwwRoot, relPath);
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    copyStream(am.open(ASSET_DIR + "/" + relPath), out);
                    ok++;
                    bytes += out.length();
                } catch (Throwable t) {
                    if (gagal.length() > 0) {
                        gagal.append(", ");
                    }
                    gagal.append(relPath);
                }
            }
            r.files = ok;
            r.bytes = bytes;
            writeMarker(wwwRoot, ok, bytes);
            r.extracted = true;
            r.note = "sumber: assets/" + ASSET_DIR + " · versi mirror " + VERSION
                    + (gagal.length() > 0 ? " · gagal: " + gagal : "");
            return r;
        } catch (Throwable t) {
            r.note = "kesalahan: " + t;
            return r;
        }
    }

    // ---------- internal ----------

    private static File markerFile(File wwwRoot) {
        File parent = wwwRoot.getParentFile();
        File conf = (parent != null) ? new File(parent, "config") : new File("config");
        return new File(conf, MARKER_NAME);
    }

    /** Ekstraksi diperlukan bila penanda tidak ada / versi tidak cocok. */
    private static boolean needsExtract(File wwwRoot) {
        try {
            File m = markerFile(wwwRoot);
            if (!m.exists()) {
                return true;
            }
            String txt = readText(m);
            return !(txt.contains("mirror-version=" + VERSION));
        } catch (Throwable t) {
            return true;
        }
    }

    /** true bila SEMUA file mirror sudah ada di www/ (walau versi penanda cocok). */
    private static boolean allPresent(File wwwRoot, List<String> rel) {
        for (int i = 0; i < rel.size(); i++) {
            File f = new File(wwwRoot, rel.get(i));
            if (!f.exists() || !f.isFile()) {
                return false;
            }
        }
        return true;
    }

    private static void writeMarker(File wwwRoot, int count, long bytes) {
        try {
            File m = markerFile(wwwRoot);
            File parent = m.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            OutputStream w = new FileOutputStream(m, false);
            w.write(("mirror-version=" + VERSION + "\nfiles=" + count + "\nbytes=" + bytes + "\n")
                    .getBytes("UTF-8"));
            w.close();
        } catch (Throwable ignored) {
        }
    }

    /** Kumpulkan semua file di bawah dir (rekursif) sebagai path relatif dari ASSET_DIR. */
    private static void collect(AssetManager am, String dir, List<String> out) {
        try {
            String[] items = am.list(dir);
            if (items == null || items.length == 0) {
                InputStream test = null;
                try {
                    test = am.open(dir); // bisa dibuka = file
                    out.add(dir.substring(ASSET_DIR.length() + 1));
                } catch (Throwable notFile) {
                    // folder kosong — abaikan
                } finally {
                    closeQuietly(test);
                }
                return;
            }
            for (int i = 0; i < items.length; i++) {
                collect(am, dir + "/" + items[i], out);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void copyStream(InputStream in, File out) throws Exception {
        OutputStream os = new FileOutputStream(out, false);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            os.write(buf, 0, n);
        }
        os.close();
        in.close();
    }

    private static String readText(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        StringBuilder sb = new StringBuilder(64);
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, "UTF-8"));
        }
        in.close();
        return sb.toString();
    }

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
