package com.db.local.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Pantauan kesehatan SERVER ASLI (remote) dari sisi perangkat.
 * 0.4.5 (log bersih): hasil probe ✔ dan denyut rutin = event TEKNIS (evTech)
 * — status terbaru SELALU terlihat di dasbor tab "Data" tanpa membanjiri log;
 * kegagalan (⚠) tetap PENTING karena butuh tindakan.
 */
public final class Monitor {
    private final String[] targets;
    private final int intervalSec;
    private final Object RUNLOCK = new Object();
    private volatile boolean running = true;
    private final Boolean[] lastOk; // null = belum pernah dicek (run pertama dicatat)
    private long lastBeatAt = 0L;

    public Monitor(String[] targets, int intervalSec) {
        this.targets = (targets == null || targets.length == 0)
                ? new String[]{"dragon.sjmobilegame.com:82"}
                : targets;
        this.intervalSec = intervalSec <= 5 ? 60 : intervalSec;
        this.lastOk = new Boolean[this.targets.length];
        lastBeatAt = System.currentTimeMillis();
    }

    public void startBackground() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "dblsf-monitor");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        while (running) {
            runOnce();
            try {
                Thread.sleep(intervalSec * 1000L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Cek semua target sekarang (aman dipakai berulang; diserialisasi). */
    public void runOnce() {
        synchronized (RUNLOCK) {
            boolean allOk = true;
            for (int i = 0; i < targets.length; i++) {
                String tgt = targets[i];
                String[] hp = split(tgt);
                String host = hp[0];
                int port = parseIntSafe(hp[1], 80);
                long t0 = System.currentTimeMillis();
                boolean nowOk = false;
                String note;
                String dnsNote = null;
                try {
                    InetAddress[] addrs = InetAddress.getAllByName(host);
                    if (addrs == null || addrs.length == 0) {
                        throw new UnknownHostException(host);
                    }
                    dnsNote = "DNS " + host + " → " + addrs[0].getHostAddress();
                    Socket s = new Socket();
                    try {
                        s.connect(new InetSocketAddress(addrs[0], port), 5000);
                    } finally {
                        try {
                            s.close();
                        } catch (Throwable ignored) {
                        }
                    }
                    nowOk = true;
                    note = "bisa dihubungi";
                } catch (Throwable ex) {
                    boolean dnsFail = (ex instanceof UnknownHostException);
                    note = dnsFail
                            ? "DNS gagal (nama domain tidak ditemukan)"
                            : "koneksi gagal (" + ex.getClass().getSimpleName() + ")";
                }
                long ms = System.currentTimeMillis() - t0;
                StatusStore.putOriginal(tgt, nowOk, note, nowOk ? ms : -1);
                if (!nowOk) {
                    allOk = false;
                }
                // log hanya saat KONDISI BERUBAH (atau run pertama); 0.4.5: ✔ = TEKNIS
                if (lastOk[i] == null || lastOk[i].booleanValue() != nowOk) {
                    if (nowOk) {
                        HumanLog.evTech("📡", "Server asli " + tgt + " bisa dihubungi · " + HumanLog.fmtMs(ms),
                                dnsNote);
                    } else {
                        HumanLog.ev("⚠️", "Server asli " + tgt + " TIDAK bisa dihubungi — " + note
                                        + "; bila mode ONLINE, permintaan ke target ini akan gagal",
                                "Waktu coba : " + HumanLog.fmtMs(ms) + "\n"
                                        + (dnsNote == null ? "" : dnsNote + "\n")
                                        + "Lanjutan   : mode LOCAL tidak terpengaruh (semua dilayani lokal)");
                    }
                }
                lastOk[i] = Boolean.valueOf(nowOk);
            }
            // denyut 10 menit: satu baris ringkasan agar pemilik tahu pantauan masih hidup
            long now = System.currentTimeMillis();
            if (now - lastBeatAt >= 10L * 60L * 1000L) {
                lastBeatAt = now;
                HumanLog.evTech("ℹ️", "Pantauan rutin berjalan — " + summary() + " (rincian selalu ada di dasbor)", null);
            }
            StatusStore.persist();
        }
    }

    // ---------------- 0.5.0 — probe SERVER GAME dari daftar (Tahap A + REKAM) ----------------

    private static final Object GAMEPROBELOCK = new Object();
    /** Target yang sudah pernah diuji — kunci log "pertama kali" (penting) vs rutin (teknis). */
    private static final java.util.HashSet<String> gameSeen = new java.util.HashSet<String>();

    /**
     * Uji kesehatan SERVER GAME (url serverList — cth s2109-bs.popoh5.com:8061).
     * Klien menyambung LANGSUNG ke sini setelah pemain memilih server (di luar
     * server lokal) — kegagalan di sini TIDAK terlihat di log mana pun (game
     * diam di layar menyambung tanpa satu baris pun). Kini diuji EKSPLISIT:
     * boot = N server teratas (thread belakang); dasbor = tombol "Tes server game".
     * Hasil: StatusStore.original (tab Data) + log 📡/⚠️ + return ringkasan.
     */
    public static String probeGameServers(int n) {
        synchronized (GAMEPROBELOCK) {
            StringBuilder out = new StringBuilder(256);
            String raw;
            try {
                raw = LoginData.serverListData();
            } catch (Throwable t) {
                return "(daftar server tidak tersedia)";
            }
            int pos = 0;
            int tested = 0;
            for (int k = 0; k < n; k++) {
                int a = raw.indexOf("\"serverId\"", pos);
                if (a < 0) {
                    break;
                }
                String sid = jsonVal(raw, a);
                int b = raw.indexOf("\"url\"", a);
                if (b < 0) {
                    break;
                }
                String url = jsonVal(raw, b);
                pos = b + 4;
                if (sid == null || url == null || url.length() < 6) {
                    continue;
                }
                String host;
                int port;
                try {
                    java.net.URL u = new java.net.URL(url);
                    host = u.getHost();
                    port = u.getPort() > 0 ? u.getPort() : ("https".equals(u.getProtocol()) ? 443 : 80);
                } catch (Throwable t) {
                    continue;
                }
                String tgt = host + ":" + port;
                tested++;
                long t0 = System.currentTimeMillis();
                boolean ok = false;
                String note;
                String dnsNote = null;
                try {
                    java.net.InetAddress[] addrs = java.net.InetAddress.getAllByName(host);
                    if (addrs == null || addrs.length == 0) {
                        throw new java.net.UnknownHostException(host);
                    }
                    dnsNote = "DNS     : " + host + " → " + addrs[0].getHostAddress();
                    java.net.Socket s = new java.net.Socket();
                    try {
                        s.connect(new java.net.InetSocketAddress(addrs[0], port), 5000);
                    } finally {
                        try {
                            s.close();
                        } catch (Throwable ignored) {
                        }
                    }
                    ok = true;
                    note = "bisa dihubungi";
                } catch (Throwable ex) {
                    boolean dnsFail = (ex instanceof java.net.UnknownHostException);
                    note = dnsFail ? "DNS gagal (nama domain tidak ditemukan)"
                            : "koneksi gagal (" + ex.getClass().getSimpleName() + ")";
                }
                long ms = System.currentTimeMillis() - t0;
                StatusStore.putOriginal("Server game " + tgt + " · S" + sid, ok, note, ok ? ms : -1);
                boolean first = !gameSeen.contains(tgt);
                gameSeen.add(tgt);
                String label = "S" + sid + " — " + tgt;
                if (ok) {
                    String detail = "Server  : " + sid + " — urutan #" + tested
                            + " daftar (pilihan paling mungkin)\n"
                            + dnsNote + "\n"
                            + "Lanjutan: klien io.connect LANGSUNG ke sini setelah pemain memilih"
                            + " (bukan lewat server lokal)";
                    if (first) {
                        HumanLog.ev("📡", "Server game bisa dihubungi — " + label + " · " + HumanLog.fmtMs(ms), detail);
                    } else {
                        HumanLog.evTech("📡", "Server game " + label + " bisa dihubungi · " + HumanLog.fmtMs(ms), dnsNote);
                    }
                    out.append("✓ ").append(label).append(" · ").append(HumanLog.fmtMs(ms)).append("\n");
                } else {
                    HumanLog.ev("⚠️", "Server game TIDAK bisa dihubungi — " + label + " (" + note + ")",
                            "Server     : " + sid + " — urutan #" + tested + " daftar\n"
                                    + "Waktu coba : " + HumanLog.fmtMs(ms) + "\n"
                                    + (dnsNote == null ? "" : dnsNote + "\n")
                                    + "Lanjutan   : bila pemain memilih server ini, game DIAM di layar menyambung —"
                                    + " tidak ada log lain yang muncul (proxy gameplay 0.5.1 pun tidak menolong"
                                    + " bila host ini yang terhalang)");
                    out.append("✗ ").append(label).append(" — ").append(note).append("\n");
                }
            }
            if (tested == 0) {
                return "(tidak ada server valid di daftar)";
            }
            return out.toString().trim();
        }
    }

    /** Nilai string JSON tepat setelah posisi kunci ("key":"value") — util kecil. */
    private static String jsonVal(String raw, int keyIdx) {
        try {
            int c = raw.indexOf(':', keyIdx);
            if (c < 0) {
                return null;
            }
            int a = raw.indexOf('"', c);
            if (a < 0) {
                return null;
            }
            int b = a + 1;
            while (b < raw.length() && raw.charAt(b) != '"') {
                if (raw.charAt(b) == '\\') {
                    b++;
                }
                b++;
            }
            if (b >= raw.length()) {
                return null;
            }
            return raw.substring(a + 1, b);
        } catch (Throwable t) {
            return null;
        }
    }

    private String summary() {
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < targets.length; i++) {
            if (i > 0) {
                sb.append(" · ");
            }
            sb.append(shortName(targets[i])).append(lastOk[i] != null && lastOk[i].booleanValue() ? " ✓" : " ✗");
        }
        return sb.toString();
    }

    private static String shortName(String tgt) {
        int c = tgt.indexOf('.');
        return (c > 0) ? tgt.substring(0, c) : tgt;
    }

    private static String[] split(String tgt) {
        int i = tgt.lastIndexOf(':');
        if (i < 0) {
            return new String[]{tgt, "80"};
        }
        try {
            Integer.parseInt(tgt.substring(i + 1));
            return new String[]{tgt.substring(0, i), tgt.substring(i + 1)};
        } catch (Exception e) {
            return new String[]{tgt, "80"};
        }
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
