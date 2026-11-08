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
 * Isi mirror v4 (resource H5 Egret — CONFIG TIDAK LAGI di sini):
 *   bs/index-native.html · bs/manifest.json · bs/js/* (15 file H5 Egret)
 *   bs/resource/properties/clientversion.json
 *   bs/upgrade/base.version · resource.version · upgrade.json · size.json
 *   bs/upgrade/11386–11389_11390.zip (zip resource; base.zip/all.zip via tools/snapshot.sh)
 *
 * 0.5.3 — DUA JALUR 100% TERPISAH (owner: "online tebaca itu cuma jadikan jejak
 * arah bukan untuk dijadikan config asli ke offline"):
 *   - CONFIG OFFLINE = paket MANUAL assets/server/config-offline/ → <root>/config-offline/
 *     (extractOfflineConfig — SELALU ditimpa: paket kanonik bawaan, bukan cermin online).
 *   - Cermin online (setting bin popoh5, snapshot 2109 server) DIHAPUS dari assets —
 *     jalur ONLINE mengambil langsung dari server asli saat passthrough.
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
    public static final String VERSION = "4";

    private static final String ASSET_DIR = "server/www";
    private static final String OFFLINE_ASSET_DIR = "server/config-offline";
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
            collect(am, ASSET_DIR, ASSET_DIR, rel);
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

    // ---------- 0.5.3: paket CONFIG OFFLINE manual ----------

    /**
     * 0.5.3 — ekstrak paket CONFIG OFFLINE MANUAL: assets/server/config-offline/*
     * → &lt;root&gt;/config-offline/. SELALU DITIMPA (berbeda dari www/): ini paket
     * kanonik bawaan APK — bukan cermin online, bukan data pemilik. Nilainya
     * disiapkan manual saat build (lihat manifest.txt di dalam paket); online
     * hanya jejak arah format/protokol, BUKAN sumber nilai.
     *
     * Isi: setting.json (sumber terbaca), setting_BS_Android.bin (XOR DragonBall
     * dari setting.json — protokol shell asli), serversetting.json, servers.json
     * (1 entri "Server Lokal" url 127.0.0.1), manifest.txt (kunci sha256).
     *
     * return ringkasan untuk log, atau null bila paket tidak ada.
     */
    public static String extractOfflineConfig(Context ctx, File root) {
        try {
            if (root == null) {
                return null;
            }
            File outDir = new File(root, "config-offline");
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            AssetManager am = ctx.getAssets();
            List<String> rel = new ArrayList<String>();
            collect(am, OFFLINE_ASSET_DIR, OFFLINE_ASSET_DIR, rel);
            if (rel.isEmpty()) {
                return null;
            }
            int ok = 0;
            long bytes = 0;
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < rel.size(); i++) {
                String relPath = rel.get(i);
                try {
                    File out = new File(outDir, relPath);
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    copyStream(am.open(OFFLINE_ASSET_DIR + "/" + relPath), out);
                    ok++;
                    bytes += out.length();
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(relPath);
                } catch (Throwable ignored) {
                }
            }
            return (ok > 0)
                    ? ("config-offline/ (" + ok + " file · " + fmt(bytes) + ") — paket MANUAL "
                            + "(setting.json, bin XOR, serversetting, servers 1 entri lokal, manifest sha256)")
                    : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String fmt(long n) {
        if (n < 1024) {
            return n + " B";
        }
        if (n < 1048576) {
            return (n / 1024) + " KB";
        }
        return (n / 1048576) + " MB";
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

    /** Kumpulkan semua file di bawah dir (rekursif) sebagai path relatif dari base. */
    private static void collect(AssetManager am, String base, String dir, List<String> out) {
        try {
            String[] items = am.list(dir);
            if (items == null || items.length == 0) {
                InputStream test = null;
                try {
                    test = am.open(dir); // bisa dibuka = file
                    out.add(dir.substring(base.length() + 1));
                } catch (Throwable notFile) {
                    // folder kosong — abaikan
                } finally {
                    closeQuietly(test);
                }
                return;
            }
            for (int i = 0; i < items.length; i++) {
                collect(am, base, dir + "/" + items[i], out);
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
