package com.db.local.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Pantauan kesehatan SERVER ASLI (remote) dari sisi perangkat.
 * Mode M1: hanya melihat (TCP connect), TIDAK pernah melayani / mengubah traffic game.
 */
public final class Monitor {
    private final String[] targets;
    private final int intervalSec;
    private final Object RUNLOCK = new Object();
    private volatile boolean running = true;

    public Monitor(String[] targets, int intervalSec) {
        this.targets = (targets == null || targets.length == 0)
                ? new String[]{"dragon.sjmobilegame.com:82"}
                : targets;
        this.intervalSec = intervalSec <= 5 ? 60 : intervalSec;
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
            for (int i = 0; i < targets.length; i++) {
                String tgt = targets[i];
                String[] hp = split(tgt);
                String host = hp[0];
                int port = parseIntSafe(hp[1], 80);
                long t0 = System.currentTimeMillis();
                try {
                    InetAddress[] addrs = InetAddress.getAllByName(host);
                    if (addrs == null || addrs.length == 0) {
                        throw new UnknownHostException(host);
                    }
                    Socket s = new Socket();
                    try {
                        s.connect(new InetSocketAddress(addrs[0], port), 5000);
                    } finally {
                        try {
                            s.close();
                        } catch (Throwable ignored) {
                        }
                    }
                    long ms = System.currentTimeMillis() - t0;
                    StatusStore.putOriginal(tgt, true, "bisa dihubungi", ms);
                    HumanLog.ev('✔', "Server asli " + tgt + " bisa dihubungi · " + HumanLog.fmtMs(ms),
                            "DNS " + host + " → " + addrs[0].getHostAddress());
                } catch (Throwable ex) {
                    long ms = System.currentTimeMillis() - t0;
                    boolean dnsFail = (ex instanceof UnknownHostException);
                    String reason = dnsFail
                            ? "DNS gagal (nama domain tidak ditemukan)"
                            : "koneksi gagal (" + ex.getClass().getSimpleName() + ")";
                    StatusStore.putOriginal(tgt, false, reason, -1);
                    HumanLog.ev('⚠', "Server asli " + tgt + " TIDAK bisa dihubungi — " + reason
                                    + "; ini pantauan saja, traffic game tetap tidak diganggu",
                            "waktu coba: " + HumanLog.fmtMs(ms));
                }
            }
            StatusStore.persist();
        }
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
