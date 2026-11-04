package com.db.local.server;

/**
 * 0.5.2 AKAR CONFIG — PEMBEDAH ISI CONFIG.
 *
 * Jawaban tegas atas laporan owner: "pakai config online murni untuk server
 * lokal, padahal akarnya aja gak tau .. Saya sudah TEGASKAN log yg jelas rinci
 * itu penting".
 *
 * Dulu (0.5.1) log hanya MENGATAKAN "endpoint login ditulis-ulang ke server
 * lokal" — isi config-nya sendiri tidak pernah tampil, jadi owner tidak bisa
 * melihat akarnya. Sekarang SETIAP config yang disajikan (mode mana pun)
 * DIBEDAH FIELD-PER-FIELD dan ISINYA DITULIS DI LOG:
 * nilai ASLI dari server → nilai yang benar-benar diterima klien.
 *
 * Anatomi setting bin (hasil bedah penuh — file bs/db/android 711 B dan
 * lzceshi/native_setting/Android 350 B, keduanya XOR "DragonBall" → JSON;
 * README §2.20):
 *
 *   testMode       angka   1 = flag mode uji bawaan server
 *   url            string  URL H5 game yang dibuka shell di WebView (boot game)
 *   tsUrl          string  URL varian uji (jalur produksi tidak memakainya)
 *   update         string  endpoint paket pembaruan (zip + upgrade.json)
 *   checkTime      angka   jarak cek pembaruan (detik — asli 300)
 *   loginServer    string  HOST login — shell menyambung sendiri
 *                          loginServer:loginPort (socket.io login)
 *                          dan loginServer:loginWebPort (loginchecknative SDK)
 *   tsLoginServer  string  HOST login varian uji
 *   loginPort      angka   port socket.io login (asli 610)
 *   loginWebPort   angka   port web login SDK (asli 510 — loginchecknative)
 *   clientParams   objek   hideList (daftar harga disembunyikan),
 *                          supportLang (bahasa), showCurChannel
 *
 * serversetting.json (dibaca H5 main.min connectToLogin — README §2.10):
 *   loginserver    string  host:port login untuk H5 (asli https://login.popoh5.com:610)
 *   requireSDK     bool    flag SDK (asli false)
 *
 * Kelas ini MURNI FORMAT TEKS — tidak menyentuh byte config, tidak melempar
 * exception; field yang tidak ditemukan dilaporkan "(tidak ada di config)".
 * Semua nilai dilog UTUH (aturan 0.4.9: tidak boleh disingkat).
 */
public final class ConfigAkar {

    private ConfigAkar() {
    }

    /** Lebar label kolom kiri di blok rincian (rata kiri, titik dua sejajar). */
    private static final int LABEL_W = 13;

    // ------------------------------------------------ setting bin (XOR → JSON)

    /**
     * Bedah setting bin: {@code originalPlain} = teks JSON hasil dekripsi file
     * ASLI (sebelum tulis-ulang), {@code rewrittenPlain} = teks yang BENAR-BENAR
     * disajikan ke klien. Keduanya boleh null/pecah — pembedah tetap menghasilkan
     * rincian dari yang ada (tidak pernah melempar).
     */
    public static String settingBin(String originalPlain, String rewrittenPlain) {
        try {
            StringBuilder sb = new StringBuilder(1024);
            line(sb, "Enkripsi", "XOR kunci \"DragonBall\" — dibedah field-per-field sebelum disajikan");
            field(sb, originalPlain, rewrittenPlain, "testMode", "flag mode uji bawaan server");
            fieldUrl(sb, originalPlain, rewrittenPlain, "url",
                    "URL H5 game dibuka shell WebView (boot game)");
            fieldUrl(sb, originalPlain, rewrittenPlain, "tsUrl",
                    "varian uji — jalur produksi tidak memakainya");
            fieldUrl(sb, originalPlain, rewrittenPlain, "update",
                    "endpoint paket pembaruan (zip + upgrade.json)");
            field(sb, originalPlain, rewrittenPlain, "checkTime",
                    "jarak cek pembaruan (detik)");
            fieldLogin(sb, originalPlain, rewrittenPlain, "loginServer",
                    "HOST login — shell menambah :loginPort (socket.io) dan :loginWebPort"
                            + " (loginchecknative) sendiri");
            fieldLogin(sb, originalPlain, rewrittenPlain, "tsLoginServer",
                    "HOST login varian uji");
            field(sb, originalPlain, rewrittenPlain, "loginPort", "port socket.io login (asli 610)");
            field(sb, originalPlain, rewrittenPlain, "loginWebPort",
                    "port web login SDK (asli 510 — loginchecknative)");
            clientParams(sb, rewrittenPlain == null ? originalPlain : rewrittenPlain);
            return sb.toString();
        } catch (Throwable t) {
            return "Pembedah config gagal (file tetap disajikan): " + t;
        }
    }

    // ------------------------------------------------------ serversetting.json

    /** Bedah serversetting.json (teks polos, bukan XOR): loginserver + requireSDK. */
    public static String serversetting(String originalText, String rewrittenText) {
        try {
            StringBuilder sb = new StringBuilder(512);
            line(sb, "Enkripsi", "JSON polos (tanpa enkripsi) — dibedah field-per-field");
            fieldLogin(sb, originalText, rewrittenText, "loginserver",
                    "host:port login yang di-connect H5 main.min (connectToLogin)");
            field(sb, originalText, rewrittenText, "requireSDK", "flag SDK");
            return sb.toString();
        } catch (Throwable t) {
            return "Pembedah config gagal (file tetap disajikan): " + t;
        }
    }

    // ------------------------------------------------------------ builder inti

    /** Satu baris label : nilai (diawali '\n' kecuali baris pertama). */
    private static void line(StringBuilder sb, String label, String value) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(pad(label)).append(": ").append(value);
    }

    /**
     * Bedah satu field string/angka: nilai asli → nilai klien, plus keterangan
     * peran field. Bila sama → "(asli)". Bila field tidak ada → dilaporkan.
     * Kutip JSON dilepas supaya nilai tampil bersih (isinya tetap utuh).
     */
    private static void field(StringBuilder sb, String orig, String rewr,
                              String key, String role) {
        String a = unquote((orig == null) ? null : SettingBin.getJsonRaw(orig, key));
        String b = unquote((rewr == null) ? null : SettingBin.getJsonRaw(rewr, key));
        boolean ada = (a != null) || (b != null);
        if (!ada) {
            line(sb, key, "(tidak ada di config) — " + role);
            return;
        }
        boolean beda = (a != null && b != null && !a.equals(b))
                || (a == null && b != null) || (a != null && b == null);
        if (beda) {
            line(sb, key, (a == null ? "(belum ada)" : a) + "  →  " + (b == null ? "(dibuang)" : b));
            sb.append('\n').append(pad("")).append(": ").append(role);
        } else {
            line(sb, key, (b == null ? a : b) + "  (asli) — " + role);
        }
    }

    /** Field URL: sama dengan field() + keterangan khusus jalur login. */
    private static void fieldUrl(StringBuilder sb, String orig, String rewr,
                                 String key, String role) {
        field(sb, orig, rewr, key, role);
    }

    /**
     * Field HOST/PORT login — keterangan tambahan menegaskan ke mana akhirnya
     * klien menyambung (server login LOKAL yang meneruskan).
     */
    private static void fieldLogin(StringBuilder sb, String orig, String rewr,
                                   String key, String role) {
        field(sb, orig, rewr, key, role);
    }

    /** Ringkas objek clientParams: hideList / supportLang / showCurChannel. */
    private static void clientParams(StringBuilder sb, String plain) {
        try {
            if (plain == null || plain.indexOf("clientParams") < 0) {
                line(sb, "clientParams", "(tidak ada di config)");
                return;
            }
            StringBuilder isi = new StringBuilder(160);
            int hide = countItemsInArray(plain, "hideList");
            if (hide > 0) {
                isi.append("hideList ").append(hide).append(" harga disembunyikan");
            }
            String lang = arrayIndex(plain, "supportLang");
            int nLang = countOccurrences(lang, "\"lang\"");
            if (nLang > 0) {
                if (isi.length() > 0) {
                    isi.append(" · ");
                }
                isi.append("supportLang ").append(nLang).append(" bahasa (")
                        .append(collectLangs(lang)).append(')');
            }
            String ch = SettingBin.getJsonValue(plain, "showCurChannel");
            if (ch != null) {
                if (isi.length() > 0) {
                    isi.append(" · ");
                }
                isi.append("showCurChannel ").append(ch);
            }
            line(sb, "clientParams", isi.length() == 0 ? "(objek kosong)" : isi.toString());
        } catch (Throwable t) {
            line(sb, "clientParams", "(gagal diringkas: " + t + ")");
        }
    }

    /** Hitung butir larik top-level sederhana: "kunci": [ ... , ... ] → jumlah koma+1. */
    private static int countItemsInArray(String json, String key) {
        try {
            String needle = "\"" + key + "\"";
            int i = json.indexOf(needle);
            if (i < 0) {
                return 0;
            }
            int a = json.indexOf('[', i);
            if (a < 0) {
                return 0;
            }
            int depth = 0;
            int n = 0;
            boolean inStr = false;
            for (int j = a; j < json.length(); j++) {
                char c = json.charAt(j);
                if (inStr) {
                    if (c == '\\') {
                        j++;
                    } else if (c == '"') {
                        inStr = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inStr = true;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                } else if (c == ',' && depth == 1) {
                    n++;
                }
            }
            return n + 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Ambil teks larik top-level "kunci": [ ... ] (termasuk kurung). */
    private static String arrayIndex(String json, String key) {
        try {
            String needle = "\"" + key + "\"";
            int i = json.indexOf(needle);
            if (i < 0) {
                return "";
            }
            int a = json.indexOf('[', i);
            if (a < 0) {
                return "";
            }
            int depth = 0;
            boolean inStr = false;
            for (int j = a; j < json.length(); j++) {
                char c = json.charAt(j);
                if (inStr) {
                    if (c == '\\') {
                        j++;
                    } else if (c == '"') {
                        inStr = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inStr = true;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(a, j + 1);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static int countOccurrences(String haystack, String needle) {
        if (haystack == null || haystack.length() == 0) {
            return 0;
        }
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    /** Kumpulkan nilai "lang" dari larik supportLang → "en, fr, kr". */
    private static String collectLangs(String arrayText) {
        StringBuilder sb = new StringBuilder(24);
        int i = 0;
        while ((i = arrayText.indexOf("\"lang\"", i)) >= 0) {
            int a = arrayText.indexOf('"', i + 6);
            if (a < 0) {
                break;
            }
            int b = arrayText.indexOf('"', a + 1);
            if (b < 0) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(arrayText, a + 1, b);
            i = b;
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- helper

    /** Lepas kutip pembuka/penutup JSON dari nilai mentah (tampilan bersih). */
    private static String unquote(String raw) {
        if (raw != null && raw.length() >= 2 && raw.charAt(0) == '"'
                && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static String pad(String label) {
        StringBuilder sb = new StringBuilder(LABEL_W + 2);
        sb.append(label);
        while (sb.length() < LABEL_W) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
