package com.db.local.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * Switch MODE server lokal — pilihan owner di dasbor, dua posisi:
 *
 *  ONLINE — game tetap online ke server ASLI seperti biasa; server lokal hanya
 *           memantau (koresponden dengan fasa M1 di README).
 *  LOCAL  — server lokal SIAP melayani konfigurasi bootstrap dari CERMIN LOKAL
 *           (salinan config_BS.properties + hasil sinkron dari server asli),
 *           bersebelahan — file asli di APK TIDAK diganti sama sekali.
 *           Game sendiri belum diarahkan ke sini (itu fasa M3, perlu persetujuan).
 *
 * Pilihan mode disimpan ke config/server.json ("mode": "ONLINE" | "LOCAL").
 * Nilai lama "M1" dari build 0.1.0 dimaknai ONLINE.
 */
public final class ServerMode {
    public static final String ONLINE = "ONLINE";
    public static final String LOCAL = "LOCAL";

    private static final Object LOCK = new Object();
    private static volatile String current = ONLINE;
    private static volatile File cfgFile = null;

    private ServerMode() {
    }

    /** Dipanggil Starter saat boot setelah server.json dipastikan ada. */
    static void init(File serverJson) {
        synchronized (LOCK) {
            cfgFile = serverJson;
            String val = ONLINE;
            try {
                if (serverJson != null && serverJson.exists()) {
                    String raw = read(serverJson);
                    String m = grepString(raw, "\"mode\"");
                    if (m.length() > 0) {
                        val = m;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (LOCAL.equalsIgnoreCase(val)) {
                current = LOCAL;
            } else if ("M1".equalsIgnoreCase(val)) {
                current = ONLINE; // nilai build 0.1.0 dimaknai ONLINE
            } else {
                current = ONLINE;
            }
            StatusStore.setMode(current);
        }
    }

    public static String get() {
        return current;
    }

    public static boolean isLocal() {
        return LOCAL.equals(current);
    }

    /** Ubah mode + simpan ke server.json + log manusia. Dipanggil dari dasbor (thread HTTP). */
    public static boolean set(String m) {
        if (m == null) {
            return false;
        }
        final String target;
        if (LOCAL.equalsIgnoreCase(m.trim())) {
            target = LOCAL;
        } else if (ONLINE.equalsIgnoreCase(m.trim())) {
            target = ONLINE;
        } else {
            return false;
        }
        String before;
        synchronized (LOCK) {
            before = current;
            if (target.equals(before)) {
                return true;
            }
            current = target;
            persist();
        }
        StatusStore.setMode(target);
        Notifier.update(target);
        if (LOCAL.equals(target)) {
            HumanLog.ev('ℹ', "Mode berubah: " + before + " → LOCAL — bootstrap kini dilayani dari cermin lokal ("
                    + (Mirror.bspathShort()) + "); game belum diarahkan ke sini (itu fasa M3)", null);
        } else {
            HumanLog.ev('ℹ', "Mode berubah: " + before + " → ONLINE — game online ke server asli seperti biasa; "
                    + "server lokal kembali hanya memantau", null);
        }
        StatusStore.persist();
        return true;
    }

    /** Tulis ulang server.json dengan nilai mode terkini (menjaga kunci lain). */
    private static void persist() {
        File f = cfgFile;
        if (f == null) {
            return;
        }
        try {
            String raw = f.exists() ? read(f) : Starter.defaultConfigJson();
            String out = replaceModeValue(raw, current);
            Writer w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
            w.write(out);
            w.close();
        } catch (Throwable t) {
            try {
                HumanLog.ev('⚠', "Mode baru gagal disimpan ke server.json — mode tetap berlaku selama server hidup",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
    }

    static String replaceModeValue(String raw, String mode) {
        try {
            int k = raw.indexOf("\"mode\"");
            if (k < 0) {
                return raw;
            }
            int c = raw.indexOf(':', k);
            if (c < 0) {
                return raw;
            }
            int a = raw.indexOf('"', c);
            if (a < 0) {
                return raw;
            }
            int b = raw.indexOf('"', a + 1);
            if (b < 0) {
                return raw;
            }
            return raw.substring(0, a) + mode + raw.substring(b + 1);
        } catch (Throwable t) {
            return raw;
        }
    }

    private static String grepString(String raw, String key) {
        try {
            int i = raw.indexOf(key);
            if (i < 0) {
                return "";
            }
            int c = raw.indexOf(':', i);
            int a = (c >= 0) ? raw.indexOf('"', c) : -1;
            int b = (a >= 0) ? raw.indexOf('"', a + 1) : -1;
            if (a < 0 || b < 0) {
                return "";
            }
            return raw.substring(a + 1, b).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String read(File f) throws Exception {
        Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
        StringBuilder sb = new StringBuilder(512);
        char[] buf = new char[1024];
        int n;
        while ((n = r.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        r.close();
        return sb.toString();
    }
}
