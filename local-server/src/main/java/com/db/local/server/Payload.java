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
 * Payload config ASLI — dibawa DI DALAM APK (assets/server/payload/) dan
 * diekstrak OTOMATIS ke &lt;root&gt;/www/ saat aplikasi pertama kali hidup.
 *
 * Koreksi pemilik (v2.8): TIDAK ADA langkah manual — "semua itu terekstrak ke sana".
 * - Struktur folder www/ = path URL server asli, sehingga port game lokal menjawab
 *   di alamat yang sama seperti server asli (configus / dragon).
 * - Ekstraksi ulang otomatis bila: penanda belum ada, versi payload naik, atau
 *   ada file payload yang hilang dari www/ (mis. setelah sebagian dihapus).
 * - File tambahan di luar payload TIDAK dihapus (mis. isi tambahan dari owner).
 * - Gagal ekstraksi TIDAK PERNAH menggagalkan boot — game tetap jalan (fallback 404,
 *   terbukti dari analisis dex §2.8: "Server base.version is Not Exist" → lanjut boot).
 */
public final class Payload {
    /** Versi isi payload — NAIKKAN bila isi file payload berubah, www/ ikut diperbarui. */
    public static final String VERSION = "1";

    private static final String ASSET_DIR = "server/payload";
    private static final String MARKER_FILE = ".payload.txt";

    private Payload() {
    }

    /** Ringkasan hasil ekstraksi untuk log/dasbor. */
    public static final class Result {
        public int files;
        public boolean extracted;
        public String note = "";
    }

    /**
     * Pastikan www/ berisi payload terbaru. Dipanggil Starter SEBELUM server hidup.
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
                r.note = "payload kosong di APK (assets/" + ASSET_DIR + " tidak berisi file)";
                return r;
            }
            if (!needsExtract(wwwRoot) && allPresent(wwwRoot, rel)) {
                r.extracted = false;
                r.note = "penanda " + MARKER_FILE + " cocok (versi " + VERSION + ", "
                        + rel.size() + " file utuh)";
                return r;
            }
            int ok = 0;
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
                } catch (Throwable t) {
                    if (gagal.length() > 0) {
                        gagal.append(", ");
                    }
                    gagal.append(relPath);
                }
            }
            r.files = ok;
            writeMarker(wwwRoot, ok);
            r.extracted = true;
            r.note = "sumber: assets/" + ASSET_DIR + " · versi payload " + VERSION
                    + (gagal.length() > 0 ? " · gagal: " + gagal : "");
            return r;
        } catch (Throwable t) {
            r.note = "kesalahan: " + t;
            return r;
        }
    }

    // ---------- internal ----------

    /** Ekstraksi diperlukan bila penanda tidak ada / versi atau jumlah file tidak cocok. */
    private static boolean needsExtract(File wwwRoot) {
        try {
            File m = new File(wwwRoot, MARKER_FILE);
            if (!m.exists()) {
                return true;
            }
            String txt = readText(m);
            return !(txt.contains("payload-version=" + VERSION));
        } catch (Throwable t) {
            return true;
        }
    }

    /** true bila SEMUA file payload sudah ada di www/ (walau versi penanda cocok). */
    private static boolean allPresent(File wwwRoot, List<String> rel) {
        for (int i = 0; i < rel.size(); i++) {
            File f = new File(wwwRoot, rel.get(i));
            if (!f.exists() || !f.isFile()) {
                return false;
            }
        }
        return true;
    }

    private static void writeMarker(File wwwRoot, int count) {
        try {
            OutputStream w = new FileOutputStream(new File(wwwRoot, MARKER_FILE), false);
            w.write(("payload-version=" + VERSION + "\nfiles=" + count + "\n").getBytes("UTF-8"));
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
        byte[] buf = new byte[8192];
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
