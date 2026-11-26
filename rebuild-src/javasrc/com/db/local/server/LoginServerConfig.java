package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

/**
 * Bagian 67 — LOGIN-SERVER CONFIG: 1 EVENT ATLAS = 1 FILE JAWABAN.
 *
 * Struktur DISETUJUI OWNER (oke implementasi 26 Nov: "implementasi dengan ini
 * tanpa log !! log global jdi 1") = tabel 6 event atlas Bedah Total
 * (docs/LOGIN-SERVER-BEDAH-TOTAL.md §4) dibuat file — nama file = action
 * string PERSIS yang client kirim (offset verbatim):
 *
 *   <serverRoot>/login-server/
 *   ├── GetServerList.json     (793433  — atlas §4.1)
 *   ├── loginGame.json         (792556  — atlas §4.2)
 *   ├── SaveHistory.json       (1453497 — atlas §4.3)
 *   ├── LoginAnnounce.json     (1458983 — atlas §4.4)
 *   ├── SaveLanguage.json      (790564  — atlas §4.5)
 *   └── data/history.sqlite    (persist saja — atlas §6/§12.7; dibuat LAZY
 *                               oleh LoginHistoryDb HANYA bila historyMode
 *                               "persist" — mode auto = folder tak pernah ada)
 *
 *   (event ke-6 SaveUserEnterInfo 794977 = TANPA FILE — konstanta kode di
 *    LoginAck: isi ACK TIDAK dibaca client, jawab {} supaya socket login
 *    di-destroy rapi — atlas §4.6/§11 baris 11)
 *
 * ATURAN ISI/DIAM — PER EVENT (oke owner Bagian 66):
 *   - file BERISI       = event DIJAWAB dgn isi file (dibaca ulang tiap
 *                         event — edit owner berlaku tanpa restart).
 *   - file DIKOSONGKAN  (0 byte / whitespace / "{}" / "{ }") = event DIAM
 *                         (NO ACK — atlas §11 baris 9: client diam TANPA
 *                         crash). Isi kosong DIHORMATI — seed TIDAK menimpa.
 *   - file DIHAPUS      = seed default kembali.
 *   - file RUSAK        (bukan JSON) = reseed default + WARN log.
 *
 * LOG = GLOBAL 1 (oke owner: "tanpa log !! log global jdi 1") — TIDAK ADA
 * folder logs/ di login-server/; semua kejadian masuk NDJSON GLOBAL
 * <serverRoot>/logs/ (DiskWriter) lewat Db.log / entri HTTP-WS SocketIoHandler
 * yang sudah ada. Nol file log baru pernah dibuat.
 *
 * Pola = SdkConfig (Bagian 29): self-seed boot, baca ulang tiap request,
 * tulis atomik (.tmp + fsync + rename), tanpa dependensi org.json.
 */
public final class LoginServerConfig {

    private static final Object LOCK = new Object();

    /* ===== action string PERSIS yang client kirim (atlas §4, verbatim) ===== */
    public static final String A_GETSERVERLIST = "GetServerList";
    public static final String A_LOGINGAME     = "loginGame";
    public static final String A_SAVEHISTORY   = "SaveHistory";
    public static final String A_LOGINANNOUNCE = "LoginAnnounce";
    public static final String A_SAVELANGUAGE  = "SaveLanguage";

    /** whitelist file event (anti path-traversal — action tak pernah jadi
     *  nama file langsung). SaveUserEnterInfo TANPA file. */
    public static boolean isEventFile(String action) {
        return A_GETSERVERLIST.equals(action) || A_LOGINGAME.equals(action)
                || A_SAVEHISTORY.equals(action) || A_LOGINANNOUNCE.equals(action)
                || A_SAVELANGUAGE.equals(action);
    }

    /* ================= DEFAULT SEED (PLAN §4.2 — oke owner) =================
     * Kunci = PERSIS yang dibaca parser di baris atlas event itu:
     *  - GetServerList: serverList[] 12-field (urutan = prioritas auto §11.3),
     *    history[] (§11.4), offlineReason top-level (§6 changeServerInfo),
     *    language top-level (§2 — dibaca SEMUA ACK); historyMode = kunci
     *    server KITA (parser abaikan kunci asing); errorCode TIDAK ada
     *    (atlas §4.1/§12.5 — parser tidak membacanya).
     *  - loginGame: {"jawab":"sukses"} (K2) — echo userId+channelCode.
     *  - SaveHistory: todayLoginCount (§11.8 analytics) + loginToken ""
     *    (pintu injeksi §11.7 — "" = tak kirim).
     *  - LoginAnnounce: {} = DIAM (kosong = belum ada isi, §4.2b).
     *  - SaveLanguage: whitelist ["en","fr","kr"] (K5 — [] = terima semua). */
    public static final String DEFAULT_GETSERVERLIST =
            "{\n"
            + "  \"serverList\": [\n"
            + "    {\n"
            + "      \"serverId\": \"1\",\n"
            + "      \"url\": \"http://127.0.0.1:9528\",\n"
            + "      \"new\": true,\n"
            + "      \"hot\": false,\n"
            + "      \"name\": \"DragonBall Local\",\n"
            + "      \"online\": true,\n"
            + "      \"openDate\": \"2026-11-26T00:00:00.000Z\",\n"
            + "      \"offlineReason\": \"\",\n"
            + "      \"merge\": false,\n"
            + "      \"mergeTo\": \"\",\n"
            + "      \"urlInner\": \"\",\n"
            + "      \"chatInner\": \"\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"history\": [],\n"
            + "  \"historyMode\": \"auto\",\n"
            + "  \"offlineReason\": \"\",\n"
            + "  \"language\": \"\"\n"
            + "}\n";

    public static final String DEFAULT_LOGINGAME =
            "{\n"
            + "  \"jawab\": \"sukses\"\n"
            + "}\n";

    public static final String DEFAULT_SAVEHISTORY =
            "{\n"
            + "  \"todayLoginCount\": 1,\n"
            + "  \"loginToken\": \"\"\n"
            + "}\n";

    public static final String DEFAULT_LOGINANNOUNCE = "{}";

    public static final String DEFAULT_SAVELANGUAGE =
            "{\n"
            + "  \"whitelist\": [\"en\", \"fr\", \"kr\"]\n"
            + "}\n";

    /* ===================== API ===================== */

    public static File dir() {
        String root = LoggerCore.serverRootPath;
        File d = new File(root == null || root.isEmpty() ? "." : root, "login-server");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** L2 — seed BOOT 5 file event (dipanggil LocalServerStarter tepat
     *  setelah Db.open). Hanya bila BELUM ada — file kosong (DIAM) juga
     *  dihormati, tidak pernah ditimpa. Nol file dokumentasi pernah dibuat. */
    public static void ensure() {
        synchronized (LOCK) {
            dir();
            seedIfMissing(A_GETSERVERLIST);
            seedIfMissing(A_LOGINGAME);
            seedIfMissing(A_SAVEHISTORY);
            seedIfMissing(A_LOGINANNOUNCE);
            seedIfMissing(A_SAVELANGUAGE);
        }
    }

    private static void seedIfMissing(String action) {
        File f = new File(dir(), action + ".json");
        if (f.exists()) return;
        writeFile(f, defaultOf(action));
        Db.log("LOGIN-SERVER seed: " + action + ".json ("
                + defaultOf(action).trim().length() + " B)");
    }

    public static String defaultOf(String action) {
        if (A_GETSERVERLIST.equals(action)) return DEFAULT_GETSERVERLIST;
        if (A_LOGINGAME.equals(action))     return DEFAULT_LOGINGAME;
        if (A_SAVEHISTORY.equals(action))   return DEFAULT_SAVEHISTORY;
        if (A_LOGINANNOUNCE.equals(action)) return DEFAULT_LOGINANNOUNCE;
        if (A_SAVELANGUAGE.equals(action))  return DEFAULT_SAVELANGUAGE;
        return "{}";
    }

    /**
     * Baca file event — DIBACA ULANG TIAP EVENT (aturan 3; pola
     * SdkConfig.contract). Return SELALU non-null:
     *   ""       = file kosong / {} / { } → DIAM (NO ACK; dihormati, bukan
     *              dianggap rusak — seed tidak menimpa).
     *   isi      = teks JSON file apa adanya.
     * File hilang → seed default. File rusak (bukan JSON objek) → reseed
     * default + WARN. Folder data/ TIDAK pernah dibuat di sini (lazy oleh
     * LoginHistoryDb, hanya mode persist).
     */
    public static String eventRaw(String action) {
        if (!isEventFile(action)) return null;
        synchronized (LOCK) {
            File f = new File(dir(), action + ".json");
            if (f.exists()) {
                String s = readFile(f);
                String compact = stripWs(s);
                if (compact.isEmpty() || compact.equals("{}")) return "";
                if (mapTop(s) != null) return s;
                Db.log("LOGIN-SERVER corrupted: " + action + ".json -> reseeded");
                String d = defaultOf(action);
                writeFile(f, d);
                return d;
            }
            String d = defaultOf(action);
            writeFile(f, d);
            Db.log("LOGIN-SERVER seed (lazy): " + action + ".json ("
                    + d.trim().length() + " B)");
            return d;
        }
    }

    /** mode history dari GetServerList.json — "persist" atau "auto" (default). */
    public static String historyMode() {
        LinkedHashMap<String, String> m = mapTop(eventRaw(A_GETSERVERLIST));
        if (m == null) return "auto";
        String v = m.get("historyMode");
        return "persist".equals(unquote(v)) ? "persist" : "auto";
    }

    /* ===================== parser JSON mini (tanpa dependensi) =====================
     * mapTop: scan objek TOP-LEVEL; nilai nested ({..} / [..]) = VERBATIM
     * (spasi di dalam block dihormati — berbeda dr JsonUtil.scanFlat yang
     * menghapus whitespace, karena serverList berisi "name":"DragonBall
     * Local" dgn spasi). String top-level di-unescape (isi murni).
     * Return null = BUKAN objek JSON valid. */

    public static LinkedHashMap<String, String> mapTop(String json) {
        if (json == null) return null;
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) return null;
        LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
        int i = 1, n = s.length() - 1;
        while (i < n) {
            while (i < n && ", \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
            if (i >= n) break;
            if (s.charAt(i) != '"') return null;
            StringBuilder kb = new StringBuilder();
            i++;
            boolean ok = false;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < n) { i = unescAt(s, i, kb); continue; }
                if (c == '"') { i++; ok = true; break; }
                kb.append(c); i++;
            }
            if (!ok || kb.length() == 0) return null;
            while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == ':')) i++;
            if (i >= n) return null;
            char c0 = s.charAt(i);
            String val;
            if (c0 == '"') {
                StringBuilder vb = new StringBuilder();
                i++;
                ok = false;
                while (i < n) {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < n) { i = unescAt(s, i, vb); continue; }
                    if (c == '"') { i++; ok = true; break; }
                    vb.append(c); i++;
                }
                if (!ok) return null;
                val = vb.toString();               // isi string, TANPA kutip
            } else if (c0 == '{' || c0 == '[') {
                int close = rawBlock(s, i);
                if (close < 0) return null;
                val = s.substring(i, close + 1);   // nested VERBATIM
                i = close + 1;
            } else {
                int e = i;
                while (e < n && ",}".indexOf(s.charAt(e)) < 0) e++;
                val = s.substring(i, e).trim();    // literal angka/true/false/null
                i = e;
            }
            out.put(kb.toString(), val);
        }
        return out;
    }

    /** isi string dari map (nilai sudah di-unescape oleh mapTop). */
    public static String str(LinkedHashMap<String, String> m, String key) {
        if (m == null) return "";
        String v = m.get(key);
        return v == null ? "" : v;
    }

    /** raw nested ({..}/[..]) dari map — VERBATIM; null bila tidak ada. */
    public static String raw(LinkedHashMap<String, String> m, String key) {
        if (m == null) return null;
        String v = m.get(key);
        if (v == null) return null;
        v = v.trim();
        return (v.startsWith("{") || v.startsWith("[")) ? v : null;
    }

    /** literal tanpa kutip utk nilai string sederhana (mis. "auto" → auto). */
    public static String unquote(String v) {
        return v == null ? "" : v;
    }

    /** array-of-string dari raw "[...]" — elemen non-string dihentikan. */
    public static java.util.ArrayList<String> strArray(String rawArr) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        if (rawArr == null) return out;
        String s = rawArr.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) return out;
        int i = 1, n = s.length() - 1;
        while (i < n) {
            while (i < n && ", \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
            if (i >= n) break;
            if (s.charAt(i) != '"') break;
            StringBuilder vb = new StringBuilder();
            i++;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < n) { i = unescAt(s, i, vb); continue; }
                if (c == '"') { i++; break; }
                vb.append(c); i++;
            }
            out.add(vb.toString());
        }
        return out;
    }

    private static char unesc(char c) {
        switch (c) {
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'b': return '\b';
            case 'f': return '\f';
            default: return c;      // \" \\ \/
        }
    }

    private static int unescAt(String s, int i, StringBuilder b) {
        char n1 = s.charAt(i + 1);
        if (n1 == 'u' && i + 5 < s.length()) {
            try {
                b.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                return i + 6;
            } catch (Throwable t) { /* jatuh ke escape biasa */ }
        }
        b.append(unesc(n1));
        return i + 2;
    }

    /** index penutup blok {..}/[..] mulai di open (menghitung string). */
    private static int rawBlock(String s, int open) {
        char openC = s.charAt(open);
        char closeC = openC == '{' ? '}' : ']';
        int depth = 0;
        boolean inStr = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == openC) depth++;
            else if (c == closeC) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static String stripWs(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') b.append(c);
        }
        return b.toString();
    }

    /* ===================== IO (atomik — pola SdkConfig) ===================== */

    private static String readFile(File f) {
        FileInputStream in = null;
        try {
            byte[] buf = new byte[(int) f.length()];
            in = new FileInputStream(f);
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static void writeFile(File f, String s) {
        FileOutputStream out = null;
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            out = new FileOutputStream(tmp);
            out.write(s.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
            out.close();
            out = null;
            if (f.exists()) f.delete();
            tmp.renameTo(f);
        } catch (Throwable t) {
            Db.log("LOGIN-SERVER write failed: " + f.getName() + " -> " + t);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }
}
