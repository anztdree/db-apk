package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REKAM 0.5.0 — perekam PAYLOAD MENTAH semua lapisan, di dalam server sendiri.
 *
 * Latar (permintaan owner): "seperti capture HttpCanary, tapi kita buat ini
 * lebih canggih — HttpCanary itu cuma jejaknya, bukan config aslinya."
 * Tiga keunggulan yang HttpCanary TIDAK mungkin punya:
 *   1) HttpCanary buta terhadap LOOPBACK — trafik ke 127.0.0.1:8080/8610 (justru
 *      seluruh alur LOCAL) tidak pernah terlihat VPN-capture; rekam dari dalam
 *      server melihat 100%.
 *   2) Payload DIDEKODE: body SDK dibuka dari base64+sign, setting bin didekripsi
 *      XOR "DragonBall" — yang direkam adalah config asli yang diterima klien.
 *   3) Config asli TERSIMPAN FILE: setiap jawaban config ditulis utuh ke
 *      capture/payload/ — bisa dipakai ulang sebagai aset (pola yang sama
 *      dengan servers.json snapshot 0.3.x yang kini dilayani GetServerList).
 *
 * Lapisan (arah dari sudut pandang server lokal):
 *   →  = masuk (request klien / frame klien)      ←  = keluar (jawaban server)
 *   login — engine.io/socket.io :8610 (handshake, event, ack, ping, disconnect)
 *   sdk   — /v1/* + loginchecknative (body didekode + jawaban; ONLINE = hasil asli)
 *   game  — port 8080: config/JSON diteruskan penuh (setting bin DIDEKRIPSI,
 *           serversetting.json, upgrade.json, versi, properties); resource
 *           biner (png/zip/js) dicatat barisnya saja — bukan config.
 *   proxy — disiapkan untuk proxy gameplay 0.5.1 (masih kosong).
 *
 * Penyimpanan dua sisi:
 *   1) memori — cincin CAP entri terakhir (tab REKAM dasbor; preview ≤ PREVIEW).
 *   2) file   — capture/rekam.jsonl (jejak lengkap, embed ≤ EMBED, rotasi 8 MB)
 *               + capture/payload/NNNNNN-arah-nama.ext (utuh ≤ FILE_MAX; config
 *               asli). File lama dipangkas saat melewati FILE_CAP.
 *
 * Payload utuh selalu bisa diambil dasbor: /api/rekam/payload?id=N (baca file).
 * Toggle ON/OFF = server.json "rekam" (Config.setRekam — dasbor /api/rekam/toggle).
 * Semua kegagalan rekam DITELAN — perekam tidak boleh pernah mengganggu game.
 */
public final class Capture {
    private static final Object LOCK = new Object();
    /** Entri ring memori dasbor. */
    private static final int CAP = 600;
    /** Payload ≤ ini ikut tertanam penuh di rekam.jsonl. */
    private static final int EMBED_MAX = 32 * 1024;
    /** Potongan payload di baris dasbor. */
    private static final int PREVIEW = 400;
    /** Payload ≤ ini disimpan file utuh (di luar itu hanya baris header). */
    private static final int FILE_MAX = 2 * 1024 * 1024;
    /** Batas jumlah file payload — yang terlama dihapus. */
    private static final int FILE_CAP = 400;

    private static long seq = 0;
    private static long recorded = 0;
    private static File dir = null;
    private static File payloadDir = null;
    private static File jsonlFile = null;
    private static final ArrayList<String> mem = new ArrayList<String>(CAP + 8);
    /** id → path file payload (relatif capture/) — untuk /api/rekam/payload. */
    private static final LinkedHashMap<Long, String> fileRefs = new LinkedHashMap<Long, String>();
    private static int payloadFiles = 0;

    private Capture() {
    }

    /** Siapkan folder capture/ — dipanggil Starter setelah root & config siap. */
    public static void init(File root) {
        synchronized (LOCK) {
            dir = (root == null) ? null : new File(root, "capture");
            payloadDir = (dir == null) ? null : new File(dir, "payload");
            jsonlFile = (dir == null) ? null : new File(dir, "rekam.jsonl");
            try {
                if (dir != null) {
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    if (!payloadDir.exists()) {
                        payloadDir.mkdirs();
                    }
                    rotateIfBig(jsonlFile, 8L * 1024 * 1024);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** true bila perekam menyala (server.json "rekam", bawaan ON). */
    public static boolean on() {
        return Config.rekam();
    }

    /** Total entri terekam sejak boot (dasbor). */
    public static long recordedCount() {
        synchronized (LOCK) {
            return recorded;
        }
    }

    // ---------------- pintu per lapisan ----------------

    /** Lapisan LOGIN (engine.io/socket.io :8610). */
    public static void login(String d, String name, String info, String payload, int size, long ms) {
        record("login", d, name, info, payload, size, ms);
    }

    /** Lapisan SDK (/v1/* + loginchecknative) — payload = body DIDEKODE. */
    public static void sdk(String d, String name, String info, String payload, int size, long ms) {
        record("sdk", d, name, info, payload, size, ms);
    }

    /** Lapisan GAME (port 8080) — config asli + header resource. */
    public static void game(String d, String name, String info, String payload, int size, long ms) {
        record("game", d, name, info, payload, size, ms);
    }

    /** Lapisan PROXY GAMEPLAY (0.5.1 — disiapkan). */
    public static void proxy(String d, String name, String info, String payload, int size, long ms) {
        record("proxy", d, name, info, payload, size, ms);
    }

    // ---------------- inti perekam ----------------

    private static void record(String layer, String d, String name, String info,
                               String payload, int size, long ms) {
        if (!Config.rekam()) {
            return;
        }
        try {
            long id;
            String t;
            String memJo;
            String jsonlJo;
            synchronized (LOCK) {
                id = ++seq;
                recorded++;
                t = now();

                String fileRef = null;
                String embed = null;
                String preview = "";
                boolean binary = false;
                if (payload != null && payload.length() > 0) {
                    binary = !looksText(payload);
                    if (payload.length() <= FILE_MAX && size <= FILE_MAX) {
                        fileRef = savePayload(id, d, layer, name, payload, binary);
                    }
                    if (!binary && payload.length() <= EMBED_MAX) {
                        embed = payload;
                    }
                    // biner TIDAK pernah masuk preview — char mentah merusak JSON
                    preview = binary ? "" : previewOf(payload);
                }

                // entri MEMORI (dasbor): preview pendek + penunjuk file — ring tetap ringan
                StringBuilder sb = new StringBuilder(200 + preview.length());
                sb.append("{\"id\":").append(id)
                        .append(",\"t\":\"").append(t).append('"')
                        .append(",\"d\":\"").append(d).append('"')
                        .append(",\"l\":\"").append(layer).append('"')
                        .append(",\"n\":\"").append(Json.esc(name)).append('"');
                if (info != null && info.length() > 0) {
                    sb.append(",\"i\":\"").append(Json.esc(info)).append('"');
                }
                sb.append(",\"s\":").append(size).append(",\"ms\":").append(ms);
                if (preview.length() > 0) {
                    sb.append(",\"p\":\"").append(Json.esc(preview)).append('"');
                    if (embed == null && payload != null && payload.length() > PREVIEW) {
                        sb.append(",\"cut\":true");
                    }
                }
                if (binary) {
                    sb.append(",\"bin\":true");
                }
                if (fileRef != null) {
                    sb.append(",\"f\":\"").append(Json.esc(fileRef)).append('"');
                }
                sb.append('}');
                memJo = sb.toString();
                mem.add(memJo);
                while (mem.size() > CAP) {
                    mem.remove(0);
                }

                // baris JSONL (jejak penuh): payload kecil ikut utuh
                if (embed != null) {
                    jsonlJo = withPayload(memJo, embed, false);
                } else {
                    jsonlJo = memJo;
                }
                appendJsonl(jsonlJo);
            }
        } catch (Throwable ignored) {
            // perekam TIDAK PERNAH boleh mengganggu server
        }
    }

    /** Sisipkan payload penuh ke baris JSONL (menggantikan preview). */
    private static String withPayload(String memJo, String full, boolean bin) {
        int p = memJo.indexOf("\"p\":");
        if (p < 0) {
            int i = memJo.lastIndexOf('}');
            return memJo.substring(0, i) + ",\"p\":\"" + Json.esc(full) + (bin ? "\",\"bin\":true" : "\"") + "}";
        }
        int q1 = memJo.indexOf('"', p + 4);
        int q2 = memJo.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return memJo;
        }
        String head = memJo.substring(0, p);
        String tail = memJo.substring(q2 + 1);
        String mid = "\"p\":\"" + Json.esc(full) + "\"";
        if (bin) {
            mid += ",\"bin\":true";
        } else {
            mid = mid.replace(",\"cut\":true", "");
        }
        String out = head + mid + tail;
        return out.replace(",\"cut\":true", "");
    }

    // ---------------- snapshot utk dasbor ----------------

    /** Semua entri ring dengan id > sinceId — array JSON (bentuk /api/logs). */
    public static String snapshotJson(long sinceId) {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder(4096);
            sb.append('[');
            boolean first = true;
            for (int i = 0; i < mem.size(); i++) {
                String jo = mem.get(i);
                long id = extractId(jo);
                if (id <= sinceId) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(jo);
            }
            sb.append(']');
            return sb.toString();
        }
    }

    /** Payload UTUH satu entri (baca file capture/payload) — utk /api/rekam/payload. */
    public static String payloadById(long id) {
        String ref;
        synchronized (LOCK) {
            ref = fileRefs.get(Long.valueOf(id));
        }
        if (ref == null) {
            return "(payload tidak tersimpan — terlalu besar, biner, atau rekam mati saat itu)";
        }
        if (ref.endsWith(".bin")) {
            return "(payload BINER — tidak ditampilkan; file utuh: capture/" + ref + ")";
        }
        File f = (dir == null) ? null : new File(dir, ref);
        if (f == null || !f.exists()) {
            return "(file payload sudah tidak ada — dipangkas perekap " + FILE_CAP + " file: capture/" + ref + ")";
        }
        try {
            FileInputStream in = new FileInputStream(f);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream((int) Math.min(f.length(), FILE_MAX));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "(gagal membaca capture/" + ref + ": " + t + ")";
        }
    }

    /** Kosongkan ring dasbor (file & jsonl tetap — bukti tidak pernah dihapus). */
    public static void clearMem() {
        synchronized (LOCK) {
            mem.clear();
        }
    }

    /** Ekor rekam.jsonl (teks mentah) — sumber tombol Unduh. */
    public static String tailText() {
        File f;
        synchronized (LOCK) {
            f = jsonlFile;
        }
        if (f == null || !f.exists() || f.length() == 0) {
            return "(rekam.jsonl belum ada — nyalakan REKAM lalu mainkan alurnya)";
        }
        try {
            long keep = Math.min(f.length(), 1024L * 1024L);
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
            raf.seek(f.length() - keep);
            byte[] buf = new byte[(int) keep];
            raf.readFully(buf);
            raf.close();
            String s = new String(buf, "UTF-8");
            if (keep < f.length()) {
                int nl = s.indexOf('\n');
                if (nl >= 0) {
                    s = s.substring(nl + 1);
                }
            }
            return s;
        } catch (Throwable t) {
            return "(gagal membaca rekam.jsonl: " + t + ")";
        }
    }

    // ---------------- simpan file ----------------

    /** Simpan payload utuh → capture/payload/NNNNNN-arah-nama.ext; return path relatif. */
    private static String savePayload(long id, String d, String layer, String name,
                                      String payload, boolean binary) {
        if (payloadDir == null) {
            return null;
        }
        try {
            if (!payloadDir.exists()) {
                payloadDir.mkdirs();
            }
            String base = sanitize(name) + "-" + sanitize(layer);
            if (base.length() == 0) {
                base = "data";
            }
            String ext = binary ? ".bin" : (looksJson(payload) ? ".json" : ".txt");
            String fn = String.format(java.util.Locale.US, "%06d-%s-%s%s",
                    id, "in".equals(d) ? "in" : "out", base, ext);
            File f = new File(payloadDir, fn);
            if (binary) {
                FileOutputStream out = new FileOutputStream(f, false);
                out.write(payload.getBytes("ISO-8859-1")); // biner diselamatkan utuh
                out.close();
            } else {
                Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
                w.write(payload);
                w.close();
            }
            payloadFiles++;
            String ref = "payload/" + fn;
            fileRefs.put(Long.valueOf(id), ref);
            // pangkas file terlama bila melewati batas
            if (payloadFiles > FILE_CAP) {
                prunePayload(payloadFiles - FILE_CAP + 20);
            }
            return ref;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Hapus file payload bernomor terkecil sampai sisa ≤ target. */
    private static void prunePayload(int removeCount) {
        try {
            File[] kids = payloadDir.listFiles();
            if (kids == null || kids.length == 0) {
                payloadFiles = 0;
                return;
            }
            java.util.Arrays.sort(kids, new java.util.Comparator<File>() {
                public int compare(File a, File b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            int removed = 0;
            for (int i = 0; i < kids.length && removed < removeCount; i++) {
                if (kids[i].delete()) {
                    removed++;
                }
            }
            payloadFiles = kids.length - removed;
            if (payloadFiles < 0) {
                payloadFiles = 0;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void appendJsonl(String jo) {
        try {
            if (jsonlFile == null) {
                return;
            }
            if (jsonlFile.exists() && jsonlFile.length() > 8L * 1024 * 1024) {
                rotateIfBig(jsonlFile, 8L * 1024 * 1024);
            }
            Writer w = new OutputStreamWriter(new FileOutputStream(jsonlFile, true), "UTF-8");
            w.write(jo);
            w.write("\n");
            w.close();
        } catch (Throwable ignored) {
        }
    }

    private static void rotateIfBig(File f, long max) {
        try {
            if (f != null && f.exists() && f.length() > max) {
                File bak = new File(f.getParentFile(), f.getName() + ".1");
                if (bak.exists()) {
                    bak.delete();
                }
                f.renameTo(bak);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- util ----------------

    private static boolean looksJson(String s) {
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    /** Heuristik teks vs biner — biner jarang; cukup sampling awal. */
    private static boolean looksText(String s) {
        int n = Math.min(s.length(), 512);
        int weird = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == 0 || (c < 9) || (c > 13 && c < 32)) {
                weird++;
                if (weird > 4) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String previewOf(String s) {
        if (s.length() <= PREVIEW) {
            return s;
        }
        return s.substring(0, PREVIEW) + "…(" + s.length() + " char)";
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length() && sb.length() < 48; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.') {
                sb.append(c);
            } else if (c == '/' || c == '?' || c == '&' || c == '=') {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    private static long extractId(String jo) {
        int i = jo.indexOf("\"id\":");
        if (i < 0) {
            return 0;
        }
        int j = i + 5;
        int k = j;
        final int len = jo.length();
        while (k < len && jo.charAt(k) >= '0' && jo.charAt(k) <= '9') {
            k++;
        }
        try {
            return Long.parseLong(jo.substring(j, k));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
}
