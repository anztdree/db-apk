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
 *  sdk/index.txt                  — panduan manusiawi (dibuat sekali, tak dibaca)
 *  sdk/profile.json               — profil kawat PHP: poweredBy/contentType/
 *                                   verifySign/signSecret/products
 *  sdk/v1/system/init.json        — jawaban init (loginTypes! eksperimen crash)
 *  sdk/v1/user/ckRegVistor.json   — kontrak isReged (mirror server asli)
 *  sdk/fallback.json              — jawaban path yang belum dipetakan
 *  sdk/errors.json                — katalog error (dokumentasi live 23 Nov)
 */
public final class SdkConfig {

    private static final Object LOCK = new Object();

    public static String poweredBy()   { return profileString("poweredBy", "PHP/5.4.37"); }
    public static String contentType() { return profileString("contentType", "text/json"); }

    public static final String DEFAULT_INIT =
            "{\"result\":true,\"data\":{\"payTypes\":[],\"loginTypes\":[\"1\"]}}";
    public static final String DEFAULT_CKREG =
            "{\"result\":true,\"data\":{\"isReged\":0},\"error\":{\"id\":0,\"message\":\"\"}}";
    public static final String DEFAULT_FALLBACK =
            "{\"result\":false,\"data\":[],\"error\":{\"id\":20000,\"message\":\"Params error\"}}";
    public static final String DEFAULT_PROFILE =
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
    public static final String DEFAULT_INDEX =
            "SDK CONFIG — server lokal (Bagian 29)\n"
            + "============================================================\n"
            + "Folder ini = CONFIG SDK dalam bentuk FILE yang bisa diedit\n"
            + "(permintaan owner: config harus seperti bs/ dan logs/).\n"
            + "Path lengkap di device: Android/data/com.db.local/server/sdk/\n"
            + "Semua file di sini dibaca ULANG setiap request — edit = langsung\n"
            + "berlaku TANPA restart game (kecuali profile.json ttg sign).\n"
            + "Kalau file dihapus, dibuat ulang otomatis dgn isi default.\n"
            + "\n"
            + "ISI:\n"
            + " profile.json            — profil kawat PHP (header X-Powered-By,\n"
            + "                           Content-Type text/json, cek sign, daftar\n"
            + "                           productCode yang dianggap valid)\n"
            + " v1/system/init.json     — jawaban /v1/system/init. loginTypes MENENTUKAN\n"
            + "                           TAMPILAN LAYAR LOGIN:\n"
            + "                             [\"1\"]     = form email+TAMU (v13 sekarang)\n"
            + "                             [\"12\"]   = mode tamu-only (tampilan v11/v12)\n"
            + "                             [\"1\",\"13\"] = mirror persis replay server asli\n"
            + "                           EKSPERIMEN CRASH (bila tombol login/register/\n"
            + "                           guest masih force close): ganti isi array ini,\n"
            + "                           restart game, catat hasil ke log dashboard.\n"
            + "                           payTypes WAJIB ADA (parser model/e game).\n"
            + " v1/user/ckRegVistor.json— jawaban tombol Kode Migrasi (mirror server asli)\n"
            + " fallback.json           — jawaban endpoint yang belum dipetakan\n"
            + " errors.json             — katalog error: live server asli vs lokal\n"
            + "\n"
            + "PERILAKU KAWAT (fidelity PHP 5.4.37, bukan bypass):\n"
            + "  - Selalu HTTP 200; error di dalam body {result:false,data:[],error:{}}\n"
            + "  - Header X-Powered-By: PHP/5.4.37 + Content-Type: text/json\n"
            + "  - Sign request diverifikasi: MD5(\"k=v&\" sort + secret) — algoritma\n"
            + "    dari smali client, DIVERIFIKASI diterima server asli 23 Nov\n"
            + "  - Param wajib kosong -> 20003 LostSomeMustParams:<nama> (urutan asli:\n"
            + "    sdkVersion -> productCode -> authToken)\n"
            + "  - productCode tak dikenal -> 30001 Product doesn't exist\n"
            + "\n"
            + "GET TEST: akses GET tanpa body (browser/dashboard/curl) lewat jalur\n"
            + "diagnostik — tanpa cek sign/product. Laluins game (POST sv=v2&data=..&sign=..)\n"
            + "SELALU lewat cek kontrak lengkap.\n";

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

    /** panduan index.txt + seed SEMUA kontrak — dipanggil tiap request (murah). */
    public static void ensureIndex() {
        synchronized (LOCK) {
            File f = new File(dir(), "index.txt");
            if (!f.exists()) {
                writeFile(f, DEFAULT_INDEX);
                Db.log("SDK-CONFIG seed: index.txt");
            }
            File e = new File(dir(), "errors.json");
            if (!e.exists()) {
                writeFile(e, DEFAULT_ERRORS);
                Db.log("SDK-CONFIG seed: errors.json");
            }
            // seed semua kontrak sejak request pertama — tree lengkap langsung
            // terlihat owner di file manager (init/profile/ckReg/fallback)
            initJson();
            profileJson();
            ckRegJson();
            fallbackJson();
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
