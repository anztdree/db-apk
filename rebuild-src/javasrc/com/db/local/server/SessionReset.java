package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * v18 — RESET SESI TERSIMPAN SDK = JALUR AMAN TANPA IZIN OVERLAY.
 *
 * Jawaban owner: "apk original cuma kita edit endpoint ... mini server yg
 * tidak mengganggu apk original di dalamnya .. bisa force close?" —
 * classes.dex 100% original (byte-identik); yang berubah adalah DATA yang
 * diterima client: v16 pertama kali membuat /v1/system/init LOLOS gerbang
 * 30001 sehingga SDK maju ke jalur yang di device ini BARU PERTAMA KALI
 * dieksekusi sejak APK mod dipasang (fresh install, signature beda dari
 * original -> SEMUA izin reset, termasuk "Display over other apps"):
 * auto-login sesi tersimpan -> dialog d/e -> crash.
 *
 * BUKTI SMALI (APK original, d/e.smali + HWLoginActivity$singersmooth):
 *  - onServiceConnected: action ACTION_LOGIN + extra autologin=true +
 *    username & token tersimpan tidak kosong -> new d/e(...).show(...)
 *    TANPA cek izin (grep canDrawOverlays di seluruh SDK = 0 pemakai).
 *  - d/e onCreateView: WindowManager.LayoutParams.type = 0x7d8 (2008 =
 *    TYPE_APPLICATION_OVERLAY) -> Android 6+ tanpa izin = BadTokenException
 *    tepat "setelah sdk selesai LOADING".
 *  - sesi KOSONG -> jalur ironoriginhoblike() = UI LOGIN MANUAL (d/f) —
 *    TERBUKTI jalan di device ini (login email 06:08 tanpa crash).
 *
 * PENYIMPANAN SESI (bukti singersmooth&lt;init&gt;(ctx,"qg_Users")):
 *  1) prefs framework: getSharedPreferences("qg_Users", 0), key
 *     "qg_Users" (JSON sesi) + "update_time" (long).
 *  2) file info.qg (API 29+ = device kita): &lt;extFiles&gt;/Documents/info.qg
 *     berisi root JSON {"qg_Users":{...sesi..., "update_time":...}, ...namespace lain}.
 *     File MENANG bila update_time-nya > prefs (makanya KEDUANYA dihapus).
 *  3) legacy (dibolehkan best-effort, biasanya gagal di scoped storage):
 *     /sdcard/quickgame/linfo/&lt;pkg&gt;/info.qg.
 *
 * ATURAN AUTO-GUARD (boot): reset HANYA bila (izin overlay OFF) &&
 * (ada laporan crash belum ditangani) && (sesi tersimpan ada). Sesi pemain
 * yang normal / izin aktif TIDAK pernah disentuh (reset manual saja tidak
 * disediakan lagi — port 4000 murni log monitoring, Bagian 43).
 * Akun di database server/sdk/data TIDAK dihapus — pemain cukup login ulang.
 */
public final class SessionReset {

    /** nama prefs + root key info.qg (bukti smali — sama satu-satunya). */
    public static final String PREFS_NAME = "qg_Users";
    public static final String KEY_USERS = "qg_Users";
    public static final String KEY_UPDATE_TIME = "update_time";
    private static final String LEGACY_BASE = "/storage/emulated/0/quickgame/linfo/";

    /** v18 — status terakhir utk dashboard (banner + panel). */
    public static volatile boolean autoResetDone = false;
    public static volatile String lastResetInfo = "";

    private SessionReset() {}

    /* ==================== API utama ==================== */

    /**
     * Hapus sesi tersimpan SDK (prefs + info.qg). Return null bila OK,
     * selain itu pesan error. auto=true berarti dipanggil autoGuard di boot.
     * TIDAK PERNAH throw.
     */
    public static String reset(android.content.Context ctx, boolean auto) {
        try {
            if (ctx == null)
                return "server desktop — tidak ada Context (fitur device Android)";
            StringBuilder d = new StringBuilder(256);
            boolean removed = false;

            // 1) prefs framework (konsisten dgn cache SharedPreferences proses)
            try {
                android.content.SharedPreferences sp =
                        ctx.getSharedPreferences(PREFS_NAME, 0);
                boolean had = sp.contains(KEY_USERS);
                android.content.SharedPreferences.Editor ed = sp.edit();
                ed.remove(KEY_USERS);
                ed.remove(KEY_UPDATE_TIME);
                boolean ok = ed.commit();
                d.append("prefs qg_Users.xml: ").append(had ? "sesi DIHAPUS" : "tidak ada sesi");
                if (!ok) d.append(" (commit=false — cek logcat)");
                if (had) removed = true;
            } catch (Throwable t) {
                d.append("prefs: GAGAL ").append(t);
            }

            // 2) info.qg di Documents (jalur aktif Android Q+ = device kita)
            try {
                File docs = ctx.getExternalFilesDir("Documents");
                File f = docs == null ? null : new File(docs, "info.qg");
                String r = f == null ? null : scrubInfoQg(f);
                if (r == null) d.append("; info.qg/Documents: tidak ada");
                else {
                    d.append("; info.qg/Documents: ").append(r);
                    if (r.startsWith("DIHAPUS")) removed = true;
                }
            } catch (Throwable t) {
                d.append("; info.qg/Documents: GAGAL ").append(t);
            }

            // 3) legacy path — best effort (scoped storage biasanya menolak: wajar)
            try {
                File legacy = new File(LEGACY_BASE + ctx.getPackageName(), "info.qg");
                String r = scrubInfoQg(legacy);
                if (r == null) d.append("; info.qg/legacy: tidak ada");
                else {
                    d.append("; info.qg/legacy: ").append(r);
                    if (r.startsWith("DIHAPUS")) removed = true;
                }
            } catch (Throwable t) {
                d.append("; info.qg/legacy: tidak bisa (wajar Android 11+): ")
                 .append(t.getClass().getSimpleName());
            }

            // Bagian 39 — teks dinetralkan (arahan owner): fokus ke RESET SESI
            // + LOGIN MANUAL, tanpa menyalahkan izin overlay (game asli tidak
            // memerlukannya — SDK tetap berjalan normal).
            String info = (auto ? "OTOMATIS" : "MANUAL via panel") + " — " + d
                    + " — game berikutnya membuka UI LOGIN MANUAL (jalur aman). "
                    + "Akun di sdk/data TIDAK dihapus.";
            lastResetInfo = info;
            if (auto) autoResetDone = true;

            LoggerCore.Entry e = new LoggerCore.Entry();
            e.port = LoggerCore.PORT_LOGGER;
            e.kind = "SYS";
            e.method = "SESI-RESET";
            e.path = info;
            e.status = 0;
            LoggerCore.log(e);
            return null;
        } catch (Throwable t) {
            return "gagal: " + t;
        }
    }

    /** Ada sesi tersimpan? (prefs ATAU info.qg manapun). Desktop = false. */
    public static boolean sessionExists(android.content.Context ctx) {
        try {
            if (ctx == null) return false;
            try {
                if (ctx.getSharedPreferences(PREFS_NAME, 0).contains(KEY_USERS)) return true;
            } catch (Throwable ignored) {}
            try {
                File docs = ctx.getExternalFilesDir("Documents");
                if (docs != null) {
                    String s = readFile(new File(docs, "info.qg"));
                    if (s != null && findRootKey(s, KEY_USERS) >= 0) return true;
                }
            } catch (Throwable ignored) {}
            try {
                String s = readFile(new File(LEGACY_BASE + ctx.getPackageName(), "info.qg"));
                if (s != null && findRootKey(s, KEY_USERS) >= 0) return true;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * AUTO-GUARD di boot (v18): putus siklus crash auto-login TANPA perlu
     * izin apa pun — game berikutnya langsung UI LOGIN MANUAL.
     * Syarat ketat: overlay OFF + crash belum ditangani + sesi ada.
     */
    public static void autoGuard(android.content.Context ctx) {
        try {
            if (ctx == null) return;
            if (OverlayFixer.hasPermission()) return;   // jalur normal — jangan ganggu
            ArrayList<String> unhandled = CrashHook.unhandledCrashFiles();
            boolean hasSession = sessionExists(ctx);
            if (!unhandled.isEmpty() && hasSession) {
                String err = reset(ctx, true);
                if (err == null) {
                    CrashHook.markCrashHandled(unhandled);
                    guardLog("SESI SDK DIRESET OTOMATIS — crash terekam sebelumnya "
                            + "+ sesi tersimpan. Buka game: LOGIN MANUAL (jalur aman). "
                            + "Akun tetap ada. Trace crash: banner CRASH dashboard.");
                } else {
                    guardLog("SESI-GUARD gagal reset: " + err);
                }
            } else if (hasSession) {
                guardLog("Izin overlay OFF (normal — info, bukan error) + sesi SDK "
                        + "tersimpan — game memakai sesi tersimpan. Bila crash setelah "
                        + "loading: buka game lagi — sesi direset otomatis lalu LOGIN MANUAL.");
            } else if (!unhandled.isEmpty()) {
                guardLog("Crash terekam sebelumnya (tanpa sesi tersimpan) — buka dashboard 4000: "
                        + "banner CRASH berisi trace penyebab persisnya.");
            }
        } catch (Throwable t) {
            try { guardLog("SESI-GUARD error (ditelan): " + t); } catch (Throwable ignored) {}
        }
    }

    /* ============ JSON surgery info.qg (murni java, deterministic) ============ */

    /**
     * Cari posisi AWAL key "key" di ROOT object (depth 1, escape-aware).
     * Return -1 bila tidak ada. Key nested (depth &gt; 1) TIDAK dianggap.
     */
    static int findRootKey(String s, String key) {
        if (s == null || key == null) return -1;
        int depth = 0, i = 0, n = s.length(), tokStart = -1;
        boolean inStr = false, esc = false;
        while (i < n) {
            char ch = s.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (ch == '\\') esc = true;
                else if (ch == '"') inStr = false;   // tokStart DIPERTAHANKAN (key = string)
                i++;
                continue;
            }
            if (ch == '"') { inStr = true; esc = false; tokStart = i; i++; continue; }
            if (ch == '{' || ch == '[') { depth++; tokStart = -1; i++; continue; }
            if (ch == '}' || ch == ']') { depth--; tokStart = -1; i++; continue; }
            if (ch == ',' || ch == ':') {
                if (ch == ':' && depth == 1 && tokStart >= 0) {
                    String tok = s.substring(tokStart, i).trim();
                    if (tok.equals("\"" + key + "\"")) return tokStart;
                }
                tokStart = -1;
                i++;
                continue;
            }
            if (tokStart < 0 && !Character.isWhitespace(ch)) tokStart = i;
            i++;
        }
        return -1;
    }

    /** indeks delimiter penutup yang cocok utk { [ " terbuka di posisi open. */
    private static int matchDelim(String s, int open) {
        char openCh = s.charAt(open);
        if (openCh == '"') {
            // value string: kembalikan quote penutup yang tidak di-escape
            boolean esc = false;
            for (int i = open + 1; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (esc) { esc = false; continue; }
                if (ch == '\\') { esc = true; continue; }
                if (ch == '"') return i;
            }
            return -1;
        }
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (ch == '\\') esc = true;
                else if (ch == '"') inStr = false;
                continue;
            }
            if (ch == '"') { inStr = true; continue; }
            if (ch == '{' || ch == '[') depth++;
            else if (ch == '}' || ch == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }

    /**
     * Hapus pasangan "key":&lt;value&gt; di ROOT object (escape-aware).
     * Value boleh object/array/string/skalar. Return JSON baru atau null
     * bila key tidak ada / struktur tidak bisa dipastikan (aman: dibiarkan).
     */
    static String removeRootKey(String json, String key) {
        int i = findRootKey(json, key);
        if (i < 0) return null;
        String needle = "\"" + key + "\"";
        int c = json.indexOf(':', i + needle.length());
        if (c < 0) return null;
        int vs = c + 1;
        while (vs < json.length() && isSpace(json.charAt(vs))) vs++;
        if (vs >= json.length()) return null;
        int ve;
        char v0 = json.charAt(vs);
        if (v0 == '{' || v0 == '[' || v0 == '"') {
            ve = matchDelim(json, vs);
            if (ve < 0) return null;
        } else {
            int e1 = json.indexOf(',', vs), e2 = json.indexOf('}', vs);
            ve = (e1 < 0 || (e2 >= 0 && e2 < e1)) ? e2 : e1;
            if (ve < 0) return null;
        }
        int start = i, end = ve + 1;
        int after = end;
        while (after < json.length() && isSpace(json.charAt(after))) after++;
        if (after < json.length() && json.charAt(after) == ',') {
            end = after + 1;
        } else {
            int before = start - 1;
            while (before >= 0 && isSpace(json.charAt(before))) before--;
            if (before >= 0 && json.charAt(before) == ',') start = before;
        }
        return json.substring(0, start) + json.substring(end);
    }

    /**
     * Operasi info.qg: hapus namespace qg_Users, key lain DIPERTAHANKAN.
     * Return: null (file tidak ada) / "tidak ada sesi" / "kosong — dibiarkan"
     * / "DIHAPUS (...)" / "gagal parse — dibiarkan (aman)" / "gagal tulis: ..".
     */
    static String scrubInfoQg(File f) {
        if (f == null || !f.exists()) return null;
        String s = readFile(f);
        if (s == null || s.trim().isEmpty()) return "kosong — dibiarkan";
        if (findRootKey(s, KEY_USERS) < 0) return "tidak ada sesi";
        String ns = removeRootKey(s, KEY_USERS);
        if (ns == null) return "gagal parse — dibiarkan (aman)";
        // sanity: hasil tetap diawali { dan diakhiri }
        String t = ns.trim();
        if (!t.startsWith("{") || !t.endsWith("}")) return "gagal parse — dibiarkan (aman)";
        if (!writeFile(f, t)) return "gagal tulis";
        return "DIHAPUS (namespace qg_Users; file tetap JSON valid)";
    }

    /* ==================== SELF TEST (smoke desktop) ==================== */

    /** Dipanggil SmokeMain sebelum server start — throw bila ada yg rusak. */
    public static void selfTest() {
        String j1 = "{\"qg_Users\":{\"a\":\"{x}\",\"b\":[1,2]},\"Lain\":true}";
        String r1 = removeRootKey(j1, "qg_Users");
        check(r1 != null && r1.contains("\"Lain\":true") && !r1.contains("qg_Users"),
                "removeRootKey dasar (object, string berisi brace)");
        String j2 = "{\"Lain\":{\"qg_Users\":1},\"qg_Users\":{\"u\":\"tes@mail.com\"}}";
        String r2 = removeRootKey(j2, "qg_Users");
        check(r2 != null && r2.contains("\"Lain\":{\"qg_Users\":1}")
                && !r2.contains("tes@mail.com"), "nested qg_Users tidak kena");
        String j3 = "{\"qg_Users\":{\"p\":\"a\\\"b}c\",\"q\":1},\"Z\":2}";
        String r3 = removeRootKey(j3, "qg_Users");
        check(r3 != null && r3.contains("\"Z\":2") && r3.indexOf("a\\\"b") < 0,
                "escape-aware (value berisi \\\" dan })");
        String r4 = removeRootKey("{\"qg_Users\":{\"x\":1}}", "qg_Users");
        check(r4 != null && r4.trim().equals("{}"), "root jadi {} tetap valid");
        String r5 = removeRootKey("{\"A\":1,\"qg_Users\":\"abc\",\"Z\":2}", "qg_Users");
        check(r5 != null && r5.contains("\"A\":1") && r5.contains("\"Z\":2")
                && !r5.contains("abc"), "value scalar");
        check(removeRootKey("{\"A\":1}", "qg_Users") == null, "key tidak ada -> null");
        // scrub file di folder temp
        try {
            File d = File.createTempFile("srt", "t").getParentFile();
            File f = new File(d, "info.qg-selftest");
            writeFile(f, "{\"qg_Users\":{\"username\":\"tes@mail.com\",\"update_time\":99},"
                    + "\"Lainnya\":{\"n\":5}}");
            String r = scrubInfoQg(f);
            String back = readFile(f);
            check(r != null && r.startsWith("DIHAPUS") && back.contains("\"Lainnya\"")
                    && back.contains("\"n\":5") && !back.contains("tes@mail.com"),
                    "scrubInfoQg file nyata (key lain selamat)");
            check("tidak ada sesi".equals(scrubInfoQg(f)), "scrub ulang -> tidak ada sesi");
            f.delete();
        } catch (Throwable t) {
            throw new RuntimeException("SELFTEST GAGAL (file): " + t);
        }
        System.out.println("SELFTEST SessionReset OK (8 kasus)");
    }

    private static void check(boolean b, String what) {
        if (!b) throw new RuntimeException("SELFTEST GAGAL: " + what);
    }

    /* ==================== IO kecil ==================== */

    private static String readFile(File f) {
        FileInputStream in = null;
        try {
            byte[] buf = new byte[(int) Math.min(f.length(), 1 << 20)];
            in = new FileInputStream(f);
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static boolean writeFile(File f, String s) {
        FileOutputStream out = null;
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File tmp = new File(f.getAbsolutePath() + ".reset.tmp");
            out = new FileOutputStream(tmp);
            out.write(s.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
            out.close();
            out = null;
            if (f.exists()) f.delete();
            return tmp.renameTo(f);
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    private static void guardLog(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGGER;
        e.kind = "SYS";
        e.method = "SESI-GUARD";
        e.path = msg;
        e.status = 0;
        LoggerCore.log(e);
    }
}
