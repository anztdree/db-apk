package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Bagian 29 — CONFIG SDK SEBAGAI FILE (permintaan owner: config harus seperti
 * bs/ dan logs/ — file yang bisa diedit, BUKAN hardcode di dex).
 *
 * Dir: <serverRoot>/sdk/  (Android/data/com.db.local/server/sdk/)
 *  - TIDAK dikirim via assets — dibuat self-seeding saat request pertama.
 *    Alasan: ekstraksi assets jalan TIAP boot dan menimpa file — editan owner
 *    akan hilang. Self-seeding = file hanya dibuat bila TIDAK ada; kalau
 *    owner hapus -> dibuat ulang dgn default; kalau owner edit -> dipakai.
 *  - Dibaca ULANG setiap request (edit langsung berlaku tanpa restart app).
 *
 * File kontrak:
 *  sdk/profile.json               — profil kawat PHP: poweredBy/contentType/
 *                                   verifySign/signSecret/products
 *  sdk/v1/system/init.json        — jawaban init (loginTypes! eksperimen crash)
 *  sdk/v1/user/ckRegVistor.json   — kontrak isReged (mirror server asli)
 *  sdk/fallback.json              — jawaban path yang belum dipetakan
 *  sdk/errors.json                — katalog error (dokumentasi live 23 Nov)
 *  sdk/QHinfo                     — endpoint SDK (dibakar ke assets/QHinfo)
 *  sdk/data/                      — db.sqlite (tabel accounts)
 *  (v16: index.txt DIHAPUS — keluhan owner "berasa jadi sampah";
 *   sisa instal lama dibersihkan otomatis saat boot.)
 */
public final class SdkConfig {

    private static final Object LOCK = new Object();

    public static String poweredBy()   { return profileString("poweredBy", "PHP/5.4.37"); }
    public static String contentType() { return profileString("contentType", "text/json"); }

    /** Bagian 30 (koreksi owner): QHinfo masuk folder sdk/ — sumber kebenaran
     *  endpoint SDK milik owner. Catatan jujur: client asli membaca QHinfo dari
     *  DALAM APK (QuickGameManager.initThirdDataSDK, xref 1 pemakai —
     *  verify-artifacts/verify_total.txt A) — berarti nilai file ini dibakar ke
     *  assets/QHinfo pada rebuild APK berikutnya, bukan live-reload. */
    public static final String DEFAULT_QHINFO = "http://127.0.0.1:9530";

    /* v16 — tracking productCode terakhir yang lolos gerbang (untuk panel):
     * lastProductSeen = terakhir dilihat di request game (boleh ditolak),
     * lastProductOk = apakah itu diterima, lastRejectLogged = anti-spam log. */
    public static volatile String lastProductSeen = "";
    public static volatile boolean lastProductOk = false;
    public static volatile String lastRejectLogged = "";

    /** Nama file yang boleh diedit lewat panel /sdkconfig (anti path-traversal).
     *  Kunci = nama API panel; nilai = path relatif di sdk/. */
    public static final String[] EDITABLE_NAMES = {
            "profile.json", "init.json", "ckRegVistor.json", "fallback.json", "QHinfo"
    };

    public static String editableRelPath(String name) {
        if ("profile.json".equals(name))     return "profile.json";
        if ("init.json".equals(name))        return "v1/system/init.json";
        if ("ckRegVistor.json".equals(name)) return "v1/user/ckRegVistor.json";
        if ("fallback.json".equals(name))    return "fallback.json";
        if ("QHinfo".equals(name))           return "QHinfo";
        return null;
    }

    public static final String DEFAULT_INIT =
            "{\"result\":true,\"data\":{\"payTypes\":[],\"loginTypes\":[\"1\"]}}";
    public static final String DEFAULT_CKREG =
            "{\"result\":true,\"data\":{\"isReged\":0},\"error\":{\"id\":0,\"message\":\"\"}}";
    public static final String DEFAULT_FALLBACK =
            "{\"result\":false,\"data\":[],\"error\":{\"id\":20000,\"message\":\"Params error\"}}";

    /** v16 — productCode ASLI yang dikirim client game di device (rekam log
     *  device 23 Nov 2026 16:54, /v1/system/init). Server asli sempat menolak
     *  SEMUA kandidat (30001) karena DB product dikosongkan shutdown — jadi
     *  satu-satunya kebenaran = kode yang dikirim client asli ini. Server
     *  lokal MENERIMA kode client (perilaku server asli sebelum shutdown)
     *  + legacy 會員帳戶註冊 (kandidat smali hw_tomato_register). */
    public static final String PRODUCT_CLIENT_ASLI = "75880787318053043365515388491588";

    public static final String DEFAULT_PROFILE =
            "{\n"
            + " \"poweredBy\": \"PHP/5.4.37\",\n"
            + " \"contentType\": \"text/json\",\n"
            + " \"alwaysHttp200\": true,\n"
            + " \"verifySign\": true,\n"
            + " \"signSecret\": \"0b2a18e45d7df321\",\n"
            + " \"products\": [\"" + PRODUCT_CLIENT_ASLI + "\", \"會員帳戶註冊\"],\n"
            + " \"note\": \"profil kawat server asli qsdk.t4game.com. products = daftar"
            + " productCode yang dianggap ada. " + PRODUCT_CLIENT_ASLI
            + " = kode ASLI yang dikirim client game (rekam device 23 Nov) — inilah"
            + " penyebab 30001 'silent error' v15 saat daftar products belum"
            + " memuatnya. verifySign=false = matikan cek sign.\"\n"
            + "}";

    /** v16 — profil default v15 SEBELUM fix 30001. Bila file di device masih
     *  persis ini (artinya owner BELUM PERNAH mengedit) -> otomatis dinaikkan
     *  ke DEFAULT_PROFILE baru dgn productCode client. Bila owner sudah
     *  mengedit -> TIDAK disentuh (hormati editan). */
    public static final String V15_PROFILE =
            "{\n"
            + " \"poweredBy\": \"PHP/5.4.37\",\n"
            + " \"contentType\": \"text/json\",\n"
            + " \"alwaysHttp200\": true,\n"
            + " \"verifySign\": true,\n"
            + " \"signSecret\": \"0b2a18e45d7df321\",\n"
            + " \"products\": [\"會員帳戶註冊\"],\n"
            + " \"note\": \"profil kawat server asli qsdk.t4game.com (rekam live 23 Nov 2026)."
            + " products = daftar productCode yang dianggap ada; client mengirim default"
            + " 會員帳戶註冊 (hw_tomato_register). verifySign=false = matikan cek sign.\"\n"
            + "}";
    public static final String DEFAULT_ERRORS =
            "{\n"
            + " \"note\": \"Katalog error LIVE server asli (rekam 23 Nov 2026)."
            + " IDs lokal server (20001, 20004, 20005) dipilih di luar katalog asli"
            + " supaya tidak bentrok arti. 20002 di server asli = SIGN, jadi salah"
            + " kata sandi lokal pakai 20004.\",\n"
            + " \"live\": {\n"
            + "   \"20000\": \"Params error\",\n"
            + "   \"20002\": \"Params sign error\",\n"
            + "   \"20003\": \"LostSomeMustParams:<nama param>\",\n"
            + "   \"30001\": \"Product doesn't exist\"\n"
            + " },\n"
            + " \"lokal\": {\n"
            + "   \"20001\": \"Email/akun belum terdaftar / sudah terdaftar (tergantung endpoint)\",\n"
            + "   \"20004\": \"Kata sandi salah\",\n"
            + "   \"20005\": \"Fitur tidak tersedia di server lokal\"\n"
            + " }\n"
            + "}";

    /* ===================== API ===================== */

    public static File dir() {
        String root = LoggerCore.serverRootPath;
        File d = new File(root == null || root.isEmpty() ? "." : root, "sdk");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Kontrak + self-seed. Tidak pernah null. */
    public static String contract(String relPath, String defaultJson) {
        synchronized (LOCK) {
            File f = new File(dir(), relPath);
            if (f.exists()) {
                String s = readFile(f).trim();
                if (s.startsWith("{") && s.endsWith("}")) return s;
                Db.log("SDK-CONFIG rusak: " + relPath + " -> seed ulang");
            }
            writeFile(f, defaultJson);
            Db.log("SDK-CONFIG seed: " + relPath + " (" + defaultJson.length() + " B)");
            return defaultJson;
        }
    }

    public static String initJson()    { return contract("v1/system/init.json", DEFAULT_INIT); }
    public static String ckRegJson()   { return contract("v1/user/ckRegVistor.json", DEFAULT_CKREG); }
    public static String fallbackJson(){ return contract("fallback.json", DEFAULT_FALLBACK); }
    public static String profileJson() { return contract("profile.json", DEFAULT_PROFILE); }

    /* ================= Bagian 30 — API edit file kontrak (panel dashboard) ================= */

    /** Isi file kontrak apa adanya (untuk GET /sdkconfig/api). Tak pernah null. */
    public static String readContractFile(String name) {
        String rel = editableRelPath(name);
        if (rel == null) return null;
        synchronized (LOCK) {
            File f = new File(dir(), rel);
            if ("QHinfo".equals(name)) {
                return f.exists() ? readFile(f).trim() : DEFAULT_QHINFO;
            }
            return f.exists() && readFile(f).trim().startsWith("{")
                    ? readFile(f).trim() : defaultOf(rel);
        }
    }

    private static String defaultOf(String rel) {
        if ("v1/system/init.json".equals(rel))        return DEFAULT_INIT;
        if ("v1/user/ckRegVistor.json".equals(rel))   return DEFAULT_CKREG;
        if ("fallback.json".equals(rel))              return DEFAULT_FALLBACK;
        if ("profile.json".equals(rel))               return DEFAULT_PROFILE;
        return DEFAULT_QHINFO;
    }

    /**
     * Tulis file kontrak dari panel (POST /sdkconfig/save).
     * Return null bila OK, atau pesan penolakan.
     * Validasi: nama harus whitelist; JSON wajib {..}; init.json wajib
     * berisi payTypes + loginTypes (parser model/e game); QHinfo wajib
     * http(s):// TANPA trailing slash (mirror asli 22 byte).
     */
    public static String writeContractFile(String name, String content) {
        String rel = editableRelPath(name);
        if (rel == null) return "nama file tidak dikenal (whitelist)";
        if (content == null) return "isi kosong";
        content = content.trim();
        if ("QHinfo".equals(name)) {
            if (content.isEmpty()) return "QHinfo kosong";
            if (!content.startsWith("http://") && !content.startsWith("https://"))
                return "QHinfo harus diawali http:// atau https:// (mirror asli)";
            if (content.endsWith("/"))
                return "QHinfo TIDAK boleh trailing slash (concat %s%s klien)";
            if (content.length() > 256)
                return "QHinfo terlalu panjang (asli 22 byte)";
        } else {
            if (!content.startsWith("{") || !content.endsWith("}"))
                return "JSON wajib diawali { dan diakhiri }";
            if ("init.json".equals(name)
                    && (content.indexOf("\"payTypes\"") < 0 || content.indexOf("\"loginTypes\"") < 0))
                return "init.json wajib berisi payTypes + loginTypes (parser model/e)";
        }
        synchronized (LOCK) {
            try {
                File f = new File(dir(), rel);
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                File tmp = new File(dir(), rel + ".tmp");
                writeFile(tmp, content);
                if (!tmp.renameTo(f)) {
                    writeFile(f, content);
                    tmp.delete();
                }
                Db.log("SDK-CONFIG-EDIT " + rel + " (" + content.length() + " B, via panel dashboard)");
                return null;
            } catch (Throwable t) {
                return "gagal menulis: " + t;
            }
        }
    }

    /** Preset eksperimen loginTypes (E1/E2/E3) — membangun init.json utuh. */
    public static String presetInitJson(String typesCsv) {
        if (typesCsv == null) return null;
        String[] parts = typesCsv.split(",");
        StringBuilder arr = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String t = parts[i].trim();
            if (t.isEmpty()) continue;
            if (!t.matches("[0-9]+")) return null;   // hanya angka, anti injeksi
            if (arr.length() > 0) arr.append(',');
            arr.append('"').append(t).append('"');
        }
        if (arr.length() == 0) return null;
        return "{\"result\":true,\"data\":{\"payTypes\":[],\"loginTypes\":["
                + arr + "]}}";
    }

    /**
     * v16 — seed SEMUA kontrak sekali + PEMBERSIHAN:
     *  - index.txt DIHAPUS (keluhan owner 23 Nov: "berasa jadi sampah") —
     *    sisa instal v15 di device dibersihkan otomatis tiap boot.
     *  - profile.json bawaan v15 (products = ["會員帳戶註冊"] saja) otomatis
     *    dinaikkan ke profil v16 (products += productCode asli client) BILA
     *    owner belum pernah mengedit — inilah fix "silent error" 30001.
     * Dipanggil tiap request (murah: compare string + exists).
     */
    public static void ensureIndex() {
        synchronized (LOCK) {
            File f = new File(dir(), "index.txt");
            if (f.exists()) {
                if (f.delete())
                    Db.log("SDK-CONFIG bersih: index.txt dihapus (tidak dipakai lagi)");
            }
            File e = new File(dir(), "errors.json");
            if (!e.exists()) {
                writeFile(e, DEFAULT_ERRORS);
                Db.log("SDK-CONFIG seed: errors.json");
            }
            // QHinfo masuk folder sdk/ (koreksi owner Bagian 30)
            File qh = new File(dir(), "QHinfo");
            if (!qh.exists()) {
                writeFile(qh, DEFAULT_QHINFO);
                Db.log("SDK-CONFIG seed: QHinfo (" + DEFAULT_QHINFO.length() + " B)");
            }
            // seed semua kontrak sejak request pertama — tree lengkap langsung
            // terlihat owner di file manager (init/profile/ckReg/fallback)
            initJson();
            ckRegJson();
            fallbackJson();
            upgradeProfileDefaults();
            profileJson();
        }
    }

    /**
     * v16 — naikkan profile.json lama ke profil baru (products += kode client)
     * HANYA bila isinya masih persis default v15 (owner belum mengedit).
     * Return true bila upgrade dilakukan.
     */
    public static boolean upgradeProfileDefaults() {
        File f = new File(dir(), "profile.json");
        if (!f.exists()) return false;
        String cur = readFile(f);
        if (!cur.trim().equals(V15_PROFILE.trim())) return false;
        writeFile(f, DEFAULT_PROFILE);
        Db.log("SDK-CONFIG-UPGRADE profile.json: products += " + PRODUCT_CLIENT_ASLI
                + " (productCode ASLI client — FIX silent error 30001)");
        return true;
    }

    /** daftar productCode di profile.json (urutan file). Tak pernah null. */
    public static java.util.ArrayList<String> productsList() {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        String s = profileJson();
        int i = s.indexOf("\"products\"");
        if (i < 0) return out;
        int a = s.indexOf('[', i);
        int b = s.indexOf(']', a);
        if (a < 0 || b <= a) return out;
        String arr = s.substring(a + 1, b);
        int j = 0;
        while ((j = arr.indexOf('\"', j)) >= 0) {
            int k = arr.indexOf('\"', j + 1);
            if (k < 0) break;
            out.add(arr.substring(j + 1, k));
            j = k + 1;
        }
        return out;
    }

    /**
     * v16 — tambah productCode ke products di profile.json (tombol
     * "TERIMA PRODUCT" di panel SDK CONFIG). Return null bila OK.
     */
    public static String productAdd(String code) {
        if (code == null) return "kode kosong";
        code = code.trim();
        if (code.isEmpty()) return "kode kosong";
        if (code.length() > 64) return "kode terlalu panjang (maks 64)";
        if (code.indexOf('"') >= 0 || code.indexOf('\\') >= 0 || code.indexOf(',') >= 0
                || code.indexOf('{') >= 0 || code.indexOf('}') >= 0
                || code.indexOf('[') >= 0 || code.indexOf(']') >= 0
                || code.indexOf('&') >= 0 || code.indexOf(' ') >= 0)
            return "kode mengandung karakter tidak sah";
        synchronized (LOCK) {
            if (productKnown(code)) return null;   // sudah ada — idempotent
            String s = profileJson();
            int i = s.indexOf("\"products\"");
            int a = i < 0 ? -1 : s.indexOf('[', i);
            int b = a < 0 ? -1 : s.indexOf(']', a);
            if (a < 0 || b <= a) return "profile.json tidak punya array products";
            String arr = s.substring(a + 1, b);
            String ins = arr.trim().isEmpty()
                    ? "\"" + code + "\"" : arr + ", \"" + code + "\"";
            String ns = s.substring(0, a + 1) + ins + s.substring(b);
            writeFile(new File(dir(), "profile.json"), ns);
            Db.log("SDK-CONFIG-EDIT profile.json: products += " + code
                    + " (via panel dashboard — productCode kini DITERIMA)");
            return null;
        }
    }

    public static String profileString(String key, String def) {
        String v = AccountStore.jsonStr(profileJson(), key);
        return v == null || v.isEmpty() ? def : v;
    }

    public static boolean profileBool(String key, boolean def) {
        String s = profileJson();
        int i = s.indexOf("\"" + key + "\"");
        if (i < 0) return def;
        int c = s.indexOf(':', i);
        if (c < 0) return def;
        String rest = s.substring(c + 1).trim();
        if (rest.startsWith("true")) return true;
        if (rest.startsWith("false")) return false;
        return def;
    }

    /** daftar productCode valid (array string di profile.json). */
    public static boolean productKnown(String productCode) {
        if (productCode == null) return false;
        String s = profileJson();
        int i = s.indexOf("\"products\"");
        if (i < 0) return false;
        int a = s.indexOf('[', i);
        int b = s.indexOf(']', a);
        if (a < 0 || b <= a) return false;
        String arr = s.substring(a + 1, b);
        int j = 0;
        while ((j = arr.indexOf('\"', j)) >= 0) {
            int k = arr.indexOf('\"', j + 1);
            if (k < 0) break;
            String v = arr.substring(j + 1, k);
            if (v.equals(productCode)) return true;
            j = k + 1;
        }
        return false;
    }

    /* ===================== IO ===================== */

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
            Db.log("SDK-CONFIG write gagal: " + rel(f) + " -> " + t);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    private static String rel(File f) {
        try {
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return "?";
        }
    }
}
