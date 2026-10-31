package com.db.local.server;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.text.SimpleDateFormat;

/**
 * CERMIN BOOTSTRAP — jawaban atas permintaan owner:
 * "config_BS.properties — kalau pertama kali server baca itu, local kita juga harus punya
 *  itu yg membaca local, bukan di ganti tp bersebelahan."
 *
 * Yang dilakukan (semua PENAMBAHAN — file asli di APK tidak disentuh sama sekali):
 * 1) Membaca file config yang DIBACA GAME PERTAMA KALI (README §2.8 Tahap A) langsung
 *    dari APK yang sedang jalan: config_BS.properties (dibaca dulu), config.properties,
 *    QHinfo, game/base.version, game/infoinfo.bin → disalin byte-identik ke
 *    data/mirror/lokal/ — salinan bersebelahan.
 * 2) Mendekode EntryPoint1/EntryPoint2 (Base64) dari config_BS.properties — inilah
 *    alamat server bootstrap yang game tuju saat pertama terhubung.
 * 3) Mode LOCAL: melayani kontrak bootstrap Tahap B dari cermin:
 *      {EntryPointPath}/setting_BS_Android.bin  (ignore ?rnd=)
 *      {EntryPointPath}/base.version + /resource.version
 *      {EntryPointPath}/upgrade.json + /size.json
 *      {EntryPointPath}/*.bin (lainnya)
 *    plus /config_BS.properties (salinan lokal mentah).
 * 4) Sinkron: menarik jawaban ASLI dari server asli (EntryPoint1, fallback EntryPoint2)
 *    memakai pola request persis dari smali — sehingga cermin diisi data nyata,
 *    bukan tebakan (fondasi paritas M2/M3).
 */
public final class Mirror {
    private static final Object LOCK = new Object();
    private static final String CHANNEL = "BS";
    private static final String DEFAULT_BS_PATH = "/bs/db/android";

    /** File config yang dibaca game saat boot (Tahap A §2.8) — disalin ke cermin lokal. */
    private static final String[] LOCAL_SOURCES = {
            "assets/config_BS.properties",
            "assets/config.properties",
            "assets/QHinfo",
            "assets/game/base.version",
            "assets/game/infoinfo.bin"
    };

    /** Kontrak bootstrap Tahap B §2.8 — pola file yang diminta game ke EntryPoint. */
    private static final String[] BOOT_FILES = {
            "setting_BS_Android.bin",
            "base.version",
            "resource.version",
            "upgrade.json",
            "size.json"
    };

    private static File mirrorDir;    // data/mirror
    private static File localDir;     // data/mirror/lokal  (salinan config APK)
    private static File bsDir;        // data/mirror/bs     (hasil sinkron dari server asli)
    private static volatile String entry1 = "";
    private static volatile String entry2 = "";
    private static volatile String bsPath = DEFAULT_BS_PATH;
    private static volatile long lastSyncAt = 0;
    private static volatile String lastSyncNote = "belum pernah disinkron";
    private static final HashSet<String> warned404 = new HashSet<String>();

    private Mirror() {
    }

    // ------------------------------------------------------------- init

    /** Dipanggil Starter (thread boot) setelah ApkReader.init. Fail-safe total. */
    public static void init() {
        try {
            File root = Starter.serverRoot();
            mirrorDir = new File(root, "data/mirror");
            localDir = new File(mirrorDir, "lokal");
            bsDir = new File(mirrorDir, "bs");
            mk(mirrorDir);
            mk(localDir);
            mk(bsDir);
        } catch (Throwable t) {
            HumanLog.ev('⚠', "Folder cermin tidak bisa dibuat — fitur cermin bootstrap nonaktif",
                    String.valueOf(t));
            return;
        }

        int copied = 0;
        for (int i = 0; i < LOCAL_SOURCES.length; i++) {
            String src = LOCAL_SOURCES[i];
            try {
                byte[] b = ApkReader.get(src);
                if (b == null || b.length == 0) {
                    continue;
                }
                String name = src.substring(src.lastIndexOf('/') + 1);
                write(new File(localDir, name), b);
                copied++;
            } catch (Throwable ignored) {
            }
        }

        try {
            parseEntryPoints();
        } catch (Throwable t) {
            HumanLog.ev('⚠', "EntryPoint gagal didekode dari config_BS.properties — pakai alamat bawaan "
                    + DEFAULT_BS_PATH, String.valueOf(t));
        }

        if (copied > 0) {
            HumanLog.ev('✔', "Cermin bootstrap siap — " + copied + " file config yang dibaca game saat boot "
                    + "tersalin BERSEBELAHAN ke data/mirror/lokal/ (file asli di APK tidak disentuh)", null);
        } else {
            HumanLog.ev('⚠', "Cermin bootstrap kosong — config game tidak bisa dibaca dari APK; "
                    + "fitur cermin terbatas", null);
        }
        if (entry1.length() > 0) {
            HumanLog.ev('ℹ', "Alamat bootstrap channel " + CHANNEL + " dari config lokal: " + entry1
                    + (entry2.length() > 0 ? "  (cadangan: " + shortHost(entry2) + ")" : ""), null);
        }
    }

    /** Dekode EntryPoint1/EntryPoint2 dari salinan config_BS.properties (nilai Base64). */
    private static void parseEntryPoints() {
        try {
            File f = new File(localDir, "config_BS.properties");
            if (!f.exists()) {
                return;
            }
            byte[] raw = read(f);
            String[] lines = new String(raw, "UTF-8").split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String ln = lines[i].trim();
                int eq = ln.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = ln.substring(0, eq).trim();
                String val = ln.substring(eq + 1).trim();
                String url = tryDecode(val);
                if (url == null) {
                    url = val.startsWith("http") ? val : null;
                }
                if (url == null) {
                    continue;
                }
                if ("EntryPoint1".equals(key) || "EntryPoint1_debug".equals(key)) {
                    if (entry1.length() == 0) {
                        entry1 = url;
                    }
                } else if ("EntryPoint2".equals(key) || "EntryPoint2_debug".equals(key)) {
                    if (entry2.length() == 0) {
                        entry2 = url;
                    }
                }
            }
            String path = pathOf(entry1);
            if (path.length() > 0) {
                bsPath = path;
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------- sinkron

    /**
     * Tarik jawaban ASLI dari server bootstrap (pola request persis smali §2.8 Tahap B,
     * termasuk query anti-cache ?rnd=). Jalur pertama EntryPoint1, gagal → EntryPoint2.
     * Dipanggil dari dasbor (aksi owner) → dipantau di thread HTTP, bukan thread UI.
     */
    public static String sync() {
        int ok = 0;
        int fail = 0;
        StringBuilder detail = new StringBuilder(256);
        List<String> done = new ArrayList<String>(BOOT_FILES.length);
        for (int i = 0; i < BOOT_FILES.length; i++) {
            String name = BOOT_FILES[i];
            String[] res = fetchFirst(name);
            if (res != null) {
                ok++;
                done.add(name + " " + HumanLog.fmtBytes(Long.parseLong(res[1])));
                detail.append(name).append("=").append(res[0]).append(" ");
            } else {
                fail++;
                detail.append(name).append("=").append("tidak-dapat ").append(" ");
            }
        }
        lastSyncAt = System.currentTimeMillis();
        lastSyncNote = ok + "/" + BOOT_FILES.length + " file dari server asli"
                + (fail > 0 ? " · " + fail + " tidak tersedia (404/tak terjangkau — pola asli juga begitu)" : "");
        HumanLog.ev(ok > 0 ? '✔' : '⚠', "Sinkron cermin: " + lastSyncNote,
                done.isEmpty() ? "tidak ada file yang berhasil ditarik — cek pantauan server asli"
                        : joinStr(done, " · ") + "  ||  " + detail.toString().trim());
        StatusStore.persist();
        return statusJson();
    }

    /** Coba EntryPoint1 lalu EntryPoint2; simpan hasil 200 OK ke cermin. */
    private static String[] fetchFirst(String name) {
        String[] urls = (entry1.length() > 0)
                ? new String[]{entry1, entry2}
                : new String[]{entry2};
        for (int i = 0; i < urls.length; i++) {
            String base = urls[i];
            if (base == null || base.length() == 0) {
                continue;
            }
            byte[] body = httpGet(base + "/" + name + "?rnd=" + Math.random());
            if (body != null && body.length > 0) {
                try {
                    write(new File(bsDir, name), body);
                    return new String[]{base, String.valueOf(body.length)};
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static byte[] httpGet(String urlStr) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(urlStr);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code != 200) {
                return null;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > 8L * 1024 * 1024) {
                    break; // pengaman ukuran
                }
            }
            in.close();
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------- layani (mode LOCAL)

    /**
     * Layani kontrak bootstrap dari cermin. uri SUDAH path murni (query terpisah).
     * Kembalikan byte jawaban + status HTTP, atau null bila bukan path bootstrap.
     */
    public static Object[] serve(String uri) {
        if ("/config_BS.properties".equals(uri)) {
            byte[] b = readOrNull(new File(localDir, "config_BS.properties"));
            if (b != null) {
                return new Object[]{b, Integer.valueOf(200)};
            }
            return new Object[]{new byte[0], Integer.valueOf(404)};
        }
        String prefix = bsPath.endsWith("/") ? bsPath : bsPath + "/";
        if (!uri.startsWith(prefix)) {
            return null;
        }
        String name = uri.substring(prefix.length());
        if (name.length() == 0 || name.indexOf('/') >= 0 || name.contains("..")
                || !name.matches("[A-Za-z0-9._-]+")) {
            return new Object[]{new byte[0], Integer.valueOf(404)};
        }
        // zip hot-update besar tidak dicerminkan — server asli juga tidak punya (game lanjut pakai bundle lokal)
        if (name.equals("base.zip") || name.equals("all.zip")) {
            note404(name, "hot-update besar tidak dicerminkan — game akan lanjut memakai bundle bawaan APK");
            return new Object[]{new byte[0], Integer.valueOf(404)};
        }
        byte[] b = readOrNull(new File(bsDir, name));
        if (b != null) {
            return new Object[]{b, Integer.valueOf(200)};
        }
        // base.version punya jawaban dari salinan lokal APK (Tahap A3 — versi bundle dasar)
        if (name.equals("base.version")) {
            byte[] local = readOrNull(new File(localDir, "base.version"));
            if (local != null) {
                return new Object[]{local, Integer.valueOf(200)};
            }
        }
        note404(name, "belum ada di cermin — jalankan Sinkron dari dasbor, atau game lanjut dengan data bawaan (perilaku sama seperti server asli yang 404)");
        return new Object[]{new byte[0], Integer.valueOf(404)};
    }

    /** Peringatan 404 hanya SEKALI per nama per boot — log tetap manusiawi, tidak spam. */
    private static void note404(String name, String saran) {
        synchronized (LOCK) {
            if (warned404.contains(name)) {
                return;
            }
            warned404.add(name);
        }
        HumanLog.ev('⚠', "Bootstrap '" + name + "' diminta tapi cermin belum punya isinya — dijawab 404 seperti server asli; "
                + saran, null);
    }

    // ------------------------------------------------------------- status JSON

    public static String bspathShort() {
        return bsPath;
    }

    public static String statusJson() {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder(2048);
            sb.append("{\"channel\":\"").append(Json.esc(CHANNEL)).append('"')
                    .append(",\"entry1\":\"").append(Json.esc(entry1)).append('"')
                    .append(",\"entry2\":\"").append(Json.esc(entry2)).append('"')
                    .append(",\"path\":\"").append(Json.esc(bsPath)).append('"')
                    .append(",\"lastSync\":").append(lastSyncAt)
                    .append(",\"lastSyncNote\":\"").append(Json.esc(lastSyncNote)).append('"')
                    .append(",\"synced\":[");
            appendFileList(sb, bsDir);
            sb.append("],\"local\":[");
            appendFileList(sb, localDir);
            sb.append("]}");
            return sb.toString();
        }
    }

    private static void appendFileList(StringBuilder sb, File dir) {
        File[] fs = dir.listFiles();
        boolean first = true;
        if (fs != null) {
            java.util.Arrays.sort(fs);
            for (int i = 0; i < fs.length; i++) {
                File f = fs[i];
                if (!f.isFile()) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"name\":\"").append(Json.esc(f.getName())).append('"')
                        .append(",\"size\":").append(f.length())
                        .append(",\"at\":\"").append(Json.esc(new SimpleDateFormat("HH:mm:ss").format(new Date(f.lastModified()))))
                        .append("\"}");
            }
        }
    }

    // ------------------------------------------------------------- util

    /** Isi file cermin untuk unduhan dasbor; cari di bs/ lalu lokal/; null bila tidak ada. */
    public static byte[] readMirror(String name) {
        if (name == null || name.length() == 0 || name.contains("..") || name.contains("/")) {
            return null;
        }
        byte[] b = readOrNull(new File(bsDir, name));
        if (b != null) {
            return b;
        }
        return readOrNull(new File(localDir, name));
    }

    private static String pathOf(String url) {
        try {
            URL u = new URL(url);
            String p = u.getPath();
            return (p == null || p.length() == 0) ? "" : p;
        } catch (Throwable t) {
            return "";
        }
    }

    private static String shortHost(String url) {
        try {
            URL u = new URL(url);
            return u.getHost();
        } catch (Throwable t) {
            return url;
        }
    }

    /** Dekode Base64 jika terlihat seperti Base64 dan hasilnya URL http(s). */
    private static String tryDecode(String v) {
        if (v == null || v.length() < 8 || v.length() > 4096) {
            return null;
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=';
            if (!ok) {
                return null;
            }
        }
        try {
            byte[] d = Base64.decode(v, Base64.DEFAULT);
            String s = new String(d, "UTF-8").trim();
            if (s.startsWith("http://") || s.startsWith("https://")) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static String joinStr(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static byte[] readOrNull(File f) {
        try {
            if (f == null || !f.exists() || f.length() > 8L * 1024 * 1024) {
                return null;
            }
            return read(f);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    private static void write(File f, byte[] b) throws Exception {
        OutputStream w = new FileOutputStream(f, false);
        w.write(b);
        w.close();
    }

    private static void mk(File d) {
        if (d != null && !d.exists()) {
            d.mkdirs();
        }
    }
}
