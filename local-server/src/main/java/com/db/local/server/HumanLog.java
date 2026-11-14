package com.db.local.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Log bahasa manusia (aturan README §3) — DUA TINGKAT (0.4.5, koreksi owner
 * "jujur terus terang log anda bagi saya cuma sampah"):
 *
 * - TINGKAT PENTING (ev/ok/warn/info) — selalu tampil: boot, mode,
 *   tamu/daftar/masuk/loginchecknative, daftar server, peringatan, galat.
 * - TINGKAT TEKNIS (evTech) — hanya tampil bila mode RINCI dinyalakan
 *   (toggle di dasbor, tersimpan di .env "LOG_TECH"): semua baris
 *   request per-file (200/404/seed/passthrough), probe DNS, denyut pantauan,
 *   geser tombol, dsb. SANGAT banyak — membanjiri log bila dicampur.
 *
 * ATURAN PENTING: mode bersih TIDAK menghilangkan bukti — setiap event
 * (penting + teknis) TETAP ditulis ke logs/human.log (teks) dan
 * logs/events.jsonl (mesin). Yang disaring hanya tampilan dasbor (memori):
 * baris teknis tidak masuk memori saat mode bersih, jadi dasbor tenang
 * dan copy log = cerita penting. Copy penuh (API /api/logs/file) tetap
 * mengambil semuanya dari file.
 *
 * ATURAN 0.4.8 — SATU KEJADIAN SATU BARIS: percobaan yang langsung selesai
 * TIDAK diumumkan dulu (cukup baris hasilnya); baris umpan balik UI dasbor
 * (toggle RINCI/BERSIH) tidak dilog.
 *
 * ATURAN 0.4.9 — EMOJI + BLOK RINCIAN (permintaan owner "emoji sudah bagus,
 * hanya saja masih kurang rinci" + "ingat gak boleh disingkat"):
 *   • Ikon = EMOJI per kategori (bukan simbol tipis ✔/⚠/ℹ yang samar):
 *       🚀 siap/boot · 👤 pemain · 🔑 pengesahan sesi · 📡 koneksi ·
 *       ✅ sukses · ⚠️ masalah (tetap jalan) · ❌ gagal (terhenti) ·
 *       📋 daftar server · 💾 data/db · 🧹 bersih/mirror · ℹ️ info ·
 *       📦 config dibedah (0.5.2 — isi config field-per-field).
 *   • Setiap baris penting membawa BLOK RINCIAN (field "detail", beberapa
 *     baris dipisah \n): fakta nyata yang dimiliki server (angka ms, byte,
 *     sumber data, uid, sebab, akibat/lanjutan). ATURAN: TIDAK BOLEH
 *     DISINGKAT — semua fakta yang relevan ditulis penuh, di dasbor dan
 *     di file human.log. Dasbor & Copy menampilkan rincian sebagai pohon
 *     "├─ … └─ …" (HumanLog.tree saat menulis file, JS setara di dasbor).
 *   • Ikon kini String (emoji = 2 char UTF-16 — dulu char tunggal membuat
 *     ikon terbatas & samar).
 *
 * Ringkasan grup batch di human.log memakai awalan [Σ] (LogGroups).
 * Angka ramah: 1,4 KB / 12 ms / jam lokal.
 */
public final class HumanLog {
    private static final Object LOCK = new Object();
    private static final int CAP = 800;
    private static long seq = 0;
    private static File logDir = null;
    private static File humanFile = null;
    private static File jsonlFile = null;
    private static final ArrayList<String> mem = new ArrayList<String>(CAP + 8);
    /** Mode RINCI: teknis ikut masuk memori dasbor. Bawaan BERSIH (false). */
    private static volatile boolean techEnabled = false;
    /**
     * 0.5.4 — TAG MODE per entri ("local"/"online"; kosong sebelum di-set).
     * Setiap baris log membawa mode proses saat baris lahir ("m":"…" di JSON)
     * — dasbor & human.log bisa membedakan baris OFFLINE dan ONLINE (owner:
     * "kalau sedang local log ya isinya local sesuai, kalau log online ya log
     * aslinya seperti httpcanary" — log campur aduk antar mode = sampah).
     *
     * 0.6.1 — SUB JUDUL JALUR tampil (owner: "sub judul pada log (Online /
     * local) gk ada jelas bikin bingung" + "timeline campur itu sengaja, isi
     * log-nya yang ngawur"): dasbor menampilkan badge [LOCAL]/[ONLINE] per
     * baris dari field "m" (dulu data ada, render dibuang), dan human.log txt
     * membawa tag yang sama di kepala tiap baris: [✅|LOCAL] / [📡·|ONLINE].
     * Timeline tetap SATU layar campur (desain owner) — yang berubah: setiap
     * baris kini jelas lahir dari jalur mana.
     */
    private static volatile String modeTag = "";

    private HumanLog() {
    }

    /** Folder log aktif (logs/) — dipakai CrashGuard menulis bukti crash. */
    public static File dir() {
        synchronized (LOCK) {
            return logDir;
        }
    }

    public static void init(File dir) {
        synchronized (LOCK) {
            logDir = dir;
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            if (dir != null && dir.exists()) {
                humanFile = new File(dir, "human.log");
                jsonlFile = new File(dir, "events.jsonl");
                rotateIfBig(humanFile, 4L * 1024 * 1024);
                rotateIfBig(jsonlFile, 4L * 1024 * 1024);
            }
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

    /** Catat event PENTING — selalu tampil di dasbor (tingkat bawaan).
     *  0.4.9: icon = emoji String (👤 🔑 📡 ✅ ⚠️ ❌ 📋 💾 🧹 🚀 ℹ️). */
    public static void ev(String icon, String sentence, String detail) {
        evInternal(icon, sentence, detail, false, null, null);
    }

    /**
     * Catat event TEKNIS — hanya tampil di dasbor saat mode RINCI.
     * File human.log tetap mencatatnya (aturan: mode bersih tidak menghilangkan bukti).
     */
    public static void evTech(String icon, String sentence, String detail) {
        evInternal(icon, sentence, detail, true, null, null);
    }

    /**
     * 0.5.6 — CAPTURE ISI (HttpCanary di dasbor :4000, owner: "capture nya ya di
     * port 4000"): event PENTING yang membawa ISI komunikasinya — byte/config
     * persis yang dilayani/diteruskan pada komunikasi itu. Label = ukuran+sumber;
     * isi yang memang tidak ada diberi catatan jujur (TIDAK direkonstruksi).
     */
    public static void evIsi(String icon, String sentence, String detail,
                             String isiLabel, String isi) {
        evInternal(icon, sentence, detail, false, isiLabel, isi);
    }

    /** 0.5.6 — sama dengan evIsi, tingkat TEKNIS (baris aset per-file). */
    public static void evTechIsi(String icon, String sentence, String detail,
                                 String isiLabel, String isi) {
        evInternal(icon, sentence, detail, true, isiLabel, isi);
    }

    /**
     * 0.6.0 — isi SELALU UTUH (keputusan owner: "log itu nyawa kita — tidak
     * boleh ada singkatan; kalau log panjang ya wajib tampil dan log txt
     * harus menyimpan"). Dulu 16 KB pertama + catatan; kini tidak ada
     * pemangkasan apa pun di jalur isi.
     */
    public static String capIsi(String isi) {
        return isi;
    }

    /**
     * Format blok rincian sebagai pohon "├─ / └─" (0.4.9) — dipakai penulis
     * file human.log; dasbor (JS) memakai bentuk yang sama supaya tampilan
     * & hasil Copy konsisten. Rincian TIDAK pernah dipangkas.
     */
    public static String tree(String detail) {
        if (detail == null || detail.length() == 0) {
            return null;
        }
        String[] lines = detail.split("\n");
        StringBuilder sb = new StringBuilder(detail.length() + lines.length * 8);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("    ").append(i < lines.length - 1 ? "├─ " : "└─ ").append(lines[i]);
        }
        return sb.toString();
    }

    /** Nyalakan/matikan mode RINCI (dipanggil dasbor via /api/logtech + Starter saat boot). */
    public static void setTechEnabled(boolean on) {
        techEnabled = on;
    }

    /** 0.5.4 — pasang tag mode utk SEMUA baris berikutnya (dipanggil Starter saat boot). */
    public static void setMode(String mode) {
        modeTag = (mode == null) ? "" : mode;
    }

    /** Tag mode aktif ("local"/"online" — bisa kosong sebelum boot selesai). */
    public static String mode() {
        return modeTag;
    }

    public static boolean isTechEnabled() {
        return techEnabled;
    }

    public static void ok(String sentence) {
        ev("✅", sentence, null);
    }

    public static void warn(String sentence, String detail) {
        ev("⚠️", sentence, detail);
    }

    public static void info(String sentence) {
        ev("ℹ️", sentence, null);
    }

    /** Event permintaan dasbor (path aneh di luar /api — jarang): penting. */
    public static void req(String method, String uri, String query, int status, long ms, long bytes) {
        String sentence = "Dasbor " + method + " " + uri + " — dijawab " + status
                + " · " + fmtMs(ms) + " · " + fmtBytes(bytes);
        String icon = (status >= 500) ? "⚠️" : "ℹ️";
        evInternal(icon, sentence, query == null || query.length() == 0 ? "-" : query, false, null, null);
    }

    private static void evInternal(String icon, String sentence, String detail, boolean tech,
                                   String isiLabel, String isi) {
        long id;
        String t;
        String jo;
        isi = capIsi(isi);
        synchronized (LOCK) {
            id = ++seq;
            t = nowStr();
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"id\":").append(id)
                    .append(",\"t\":\"").append(Json.esc(t)).append('"')
                    .append(",\"icon\":\"").append(icon).append('"')
                    .append(",\"text\":\"").append(Json.esc(sentence)).append('"')
                    .append(",\"detail\":\"").append(Json.esc(detail == null ? "" : detail)).append('"');
            if (tech) {
                sb.append(",\"v\":1"); // penanda TEKNIS — dasbor meredupkan baris ini
            }
            if (modeTag.length() > 0) {
                sb.append(",\"m\":\"").append(modeTag).append('\"'); // 0.5.4 — tag mode
            }
            if (isiLabel != null && isi != null) {
                // 0.5.6 — ISI komunikasi ikut pada barisnya (capture HttpCanary)
                // 0.5.8 — FIX AKAR "TIDAK ADA 1 pun muncul di dashboard": dulu
                // kutip penutup nilai isiLabel HILANG → JSON MALFORMED
                // ("isiLabel":"ISI · teks config,"isi":"…") → r.json() dasbor
                // gagal → catch diam → feed kosong selamanya. Kini JSON sah.
                sb.append(",\"isiLabel\":\"").append(Json.esc(isiLabel)).append('"')
                        .append(",\"isi\":\"").append(Json.esc(isi)).append('"');
            }
            sb.append('}');
            jo = sb.toString();
            // ATURAN 0.4.5: baris teknis saat mode BERSIH tidak masuk memori dasbor
            // (dasbor tenang); file tetap lengkap — bukti tidak pernah hilang.
            if (!tech || techEnabled) {
                mem.add(jo);
                while (mem.size() > CAP) {
                    mem.remove(0);
                }
            }
        }
        if (logDir != null && humanFile != null) {
            // 0.4.9 — rincian ditulis sebagai blok pohon penuh (TIDAK dipangkas):
            //   2026-11-04 09:40:34 [✅] Login game sukses — akun lama
            //       ├─ Akun   : guest99599159
            //       └─ Proses : 3 ms · jawaban 1,1 KB (ret 0)
            // 0.6.1 — tag jalur di kepala baris txt: [ICON|LOCAL] / [ICON·|ONLINE]
            // (kosong sebelum setMode → format lama tanpa |). Tidak dipangkas.
            String jm = modeTag == null ? "" : modeTag.trim().toUpperCase(Locale.US);
            String block = dateStr() + " " + t + " [" + icon + (tech ? "·" : "")
                    + (jm.length() > 0 ? "|" + jm : "") + "] " + sentence;
            String treeDetail = tree(detail);
            if (treeDetail != null) {
                block += "\n" + treeDetail;
            }
            // 0.5.6 — ISI komunikasi ikut ke human.log (bukti penuh — aturan 0.4.5
            // "mode bersih tidak menghilangkan bukti" tetap berlaku):
            if (isiLabel != null && isi != null) {
                block += "\n    ▸ ISI · " + isiLabel;
                String[] isiLines = isi.split("\n");
                for (int i = 0; i < isiLines.length; i++) {
                    block += "\n       " + isiLines[i];
                }
            }
            appendLine(humanFile, block);
            appendLine(jsonlFile, jo);
        }
    }

    /**
     * Semua event di memori (sudah tersaring sesuai mode RINCI) dengan id > sinceId.
     * 0.5.8 — bentuk {"lastId":seq,"rows":[…]}: lastId = urutan proses HIDUP ini.
     * Dulu bentuk array polos — dasbor tidak bisa tahu proses restart; klien yang
     * menyimpan since tinggi (proses lama) menyaring habis SEMUA baris proses baru
     * (id mulai 1 lagi) → feed terlihat mati padahal log menulis (laporan owner:
     * "apps running kok gk ada di log dia ngapain aja log diem"). Kini dasbor
     * melihat lastId mundur = proses baru → muat ulang dari awal.
     */
    public static String snapshotJson(long sinceId) {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder(4096);
            sb.append("{\"lastId\":").append(seq).append(",\"rows\":[");
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
            sb.append("]}");
            return sb.toString();
        }
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

    public static void clearMem() {
        synchronized (LOCK) {
            mem.clear();
        }
    }

    /**
     * Tulis baris mentah ke human.log SAJA (tanpa memori dasbor, tanpa
     * events.jsonl) — khusus ringkasan Σ grup batch (LogGroups 0.4.6): satu
     * baris per grup per periode diam, bukan event alur.
     */
    public static void writeRawLine(String line) {
        File f;
        synchronized (LOCK) {
            f = humanFile;
        }
        if (f == null) {
            return;
        }
        appendLine(f, line);
    }

    /** Ekor file human.log (teks mentah) — sumber "Copy penuh" di dasbor (0.4.5). */
    public static String tailText(int maxLines) {
        File f;
        synchronized (LOCK) {
            f = humanFile;
        }
        if (f == null || !f.exists() || maxLines < 1) {
            return "(human.log belum ada)";
        }
        try {
            long keep = Math.min(f.length(), 512L * 1024L); // cukup utk ±500 baris
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
            raf.seek(f.length() - keep);
            byte[] buf = new byte[(int) keep];
            raf.readFully(buf);
            raf.close();
            String[] lines = new String(buf, "UTF-8").split("\n");
            int start = Math.max(0, lines.length - maxLines);
            // buang baris pertama bila terpotong di tengah (awal keep bukan batas baris)
            if (start == 0 && f.length() > keep) {
                start = 1;
            }
            StringBuilder sb = new StringBuilder(4096);
            for (int i = start; i < lines.length; i++) {
                if (lines[i].length() > 0) {
                    sb.append(lines[i]).append('\n');
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "(gagal membaca human.log: " + t + ")";
        }
    }

    public static long lastId() {
        synchronized (LOCK) {
            return seq;
        }
    }

    // ---------- format ramah ----------

    public static String fmtBytes(long n) {
        if (n < 0) {
            return "?";
        }
        if (n < 1024) {
            return n + " B";
        }
        double kb = n / 1024.0;
        if (kb < 1024) {
            return satuDesimal(kb) + " KB";
        }
        return satuDesimal(kb / 1024.0) + " MB";
    }

    public static String fmtMs(long ms) {
        if (ms < 0) {
            return "-";
        }
        if (ms < 1000) {
            return ms + " ms";
        }
        return satuDesimal(ms / 1000.0) + " dtk";
    }

    private static String satuDesimal(double d) {
        String s = String.format(Locale.US, "%.1f", d);
        return s.replace('.', ',');
    }

    private static String nowStr() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private static String dateStr() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    private static void appendLine(File f, String line) {
        try {
            if (f == null) {
                return;
            }
            synchronized (LOCK) {
                Writer w = new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8");
                w.write(line);
                w.write("\n");
                w.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
