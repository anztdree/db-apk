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

    /** Bagian 30 (koreksi owner): QHinfo masuk folder sdk/ — sumber kebenaran
     *  endpoint SDK milik owner. Catatan jujur: client asli membaca QHinfo dari
     *  DALAM APK (QuickGameManager.initThirdDataSDK, xref 1 pemakai —
     *  verify-artifacts/verify_total.txt A) — berarti nilai file ini dibakar ke
     *  assets/QHinfo pada rebuild APK berikutnya, bukan live-reload. */
    public static final String DEFAULT_QHINFO = "http://127.0.0.1:9530";

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
            "SDK CONFIG — server lokal (Bagian 30)\n"
            + "============================================================\n"
            + "Folder ini = SEMUA milik SDK dalam satu folder (koreksi owner\n"
            + "23 Nov): CONFIG + DATA + QHinfo. File bisa diedit di file\n"
            + "manager ATAU lewat panel SDK CONFIG di dashboard 4000\n"
            + "(browser HP: http://127.0.0.1:4000/sdkconfig).\n"
            + "Path lengkap di device: Android/data/com.db.local/server/sdk/\n"
            + "Semua file kontrak dibaca ULANG setiap request — edit = langsung\n"
            + "berlaku TANPA restart game (kecuali profile.json ttg sign).\n"
            + "Kalau file dihapus, dibuat ulang otomatis dgn isi default.\n"
            + "\n"
            + "ISI:\n"
            + " QHinfo                  — endpoint SDK (sumber kebenaran owner).\n"
            + "                           CATATAN: client asli membaca QHinfo dari\n"
            + "                           DALAM APK (QuickGameManager.initThirdDataSDK\n"
            + "                           — xref 1 pemakai), jadi nilai file ini\n"
            + "                           dibakar ke assets/QHinfo saat rebuild APK\n"
            + "                           berikutnya (bukan live-reload). Nilai\n"
            + "                           sekarang = nilai yang sudah di APK.\n"
            + " data/                   — db.sqlite (tabel accounts) + legacy\n"
            + "                           account.json/accounts.json (Bagian 30:\n"
            + "                           pindah dari server/data/; migrasi otomatis\n"
            + "                           dari lokasi lama, uid/token pemain tetap)\n"
            + " profile.json            — profil kawat PHP (header X-Powered-By,\n"
            + "                           Content-Type text/json, cek sign, daftar\n"
            + "                           productCode yang dianggap valid)\n"
            + " v1/system/init.json     — jawaban /v1/system/init. loginTypes MENENTUKAN\n"
            + "                           TAMPILAN LAYAR LOGIN:\n"
            + "                             [\"1\"]     = form email+TAMU (v13 sekarang)\n"
            + "                             [\"12\"]   = mode tamu-only (tampilan v11/v12)\n"
            + "                             [\"1\",\"13\"] = mirror persis replay server asli\n"
            + "                           EKSPERIMEN CRASH (bila tombol login/register/\n"
            + "                           guest masih force close): ganti isi array ini\n"
            + "                           (BISA KLIK dari panel SDK CONFIG di dashboard\n"
            + "                           4000 — tombol preset E1/E2/E3), restart game,\n"
            + "                           catat hasil ke log dashboard.\n"
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
            + "diagnostik — tanpa cek sign/product. Alur game (POST sv=v2&data=..&sign=..)\n"
            + "SELALU lewat cek kontrak lengkap.\n"
            + "\n"
            + "PANEL SDK CONFIG: http://127.0.0.1:4000/sdkconfig (browser HP) —\n"
            + "edit 4 file kontrak + QHinfo, tombol preset E1/E2/E3, lihat isi\n"
            + "tabel accounts + backend aktif. Setiap edit tercatat SDK-CONFIG-EDIT.\n";

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
            // Bagian 30 (koreksi owner): QHinfo masuk folder sdk/
            File qh = new File(dir(), "QHinfo");
            if (!qh.exists()) {
                writeFile(qh, DEFAULT_QHINFO);
                Db.log("SDK-CONFIG seed: QHinfo (" + DEFAULT_QHINFO.length() + " B)");
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
