package com.db.local.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Pantauan kesehatan SERVER ASLI (remote) dari sisi perangkat.
 * Log tenang: hanya mencatat saat KONDISI BERUBAH (hidup↔mati) dan satu baris
 * ringkasan setiap 10 menit — pantauan rutin tidak membanjiri log.
 * Status terbaru selalu tersedia di dasbor (StatusStore) tanpa perlu log.
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
                // log hanya saat KONDISI BERUBAH (atau run pertama)
                if (lastOk[i] == null || lastOk[i].booleanValue() != nowOk) {
                    if (nowOk) {
                        HumanLog.ev('✔', "Server asli " + tgt + " bisa dihubungi · " + HumanLog.fmtMs(ms),
                                dnsNote);
                    } else {
                        HumanLog.ev('⚠', "Server asli " + tgt + " TIDAK bisa dihubungi — " + note
                                        + "; bila mode ONLINE, permintaan ke target ini akan gagal",
                                "waktu coba: " + HumanLog.fmtMs(ms));
                    }
                }
                lastOk[i] = Boolean.valueOf(nowOk);
            }
            // denyut 10 menit: satu baris ringkasan agar pemilik tahu pantauan masih hidup
            long now = System.currentTimeMillis();
            if (now - lastBeatAt >= 10L * 60L * 1000L) {
                lastBeatAt = now;
                HumanLog.info("Pantauan rutin berjalan — " + summary() + " (rincian selalu ada di dasbor)");
            }
            StatusStore.persist();
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
