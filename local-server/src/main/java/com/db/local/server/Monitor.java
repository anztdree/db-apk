package com.db.local.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Pantauan kesehatan SERVER ASLI (remote) dari sisi perangkat.
 * Mode ONLINE: hanya melihat (TCP connect), TIDAK pernah melayani / mengubah traffic game.
 *
 * Efisiensi log (README §3.4 — feedback owner "lognya work tapi tidak efisien"):
 * 1) Hasil pantauan hanya dicatat saat ADA PERUBAHAN status (sehat ↔ gagal);
 *    kondisi tetap hanya memperbarui panel status (status.json).
 * 2) Denyut ringkas tiap 10 menit supaya "server lokal masih hidup" terlihat jelas
 *    tanpa membanjiri log:  ✔ Masih hidup · 32 mnt · ping dasbor 214× · server asli 3/3 sehat
 */
public final class Monitor {
    private final String[] targets;
    private final int intervalSec;
    private final Object RUNLOCK = new Object();
    private final HashMap<String, Boolean> prevOk = new HashMap<String, Boolean>();
    private volatile boolean running = true;
    private long lastPulseAt = System.currentTimeMillis();
    private static final long PULSE_EVERY_MS = 10L * 60 * 1000;

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
            runOnce(false);
            try {
                Thread.sleep(intervalSec * 1000L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Cek semua target sekarang. manual=true → hasil ringkas SELALU dicatat (aksi owner). */
    public void runOnce(boolean manual) {
        synchronized (RUNLOCK) {
            int okCount = 0;
            StringBuilder summary = new StringBuilder(160);
            for (int i = 0; i < targets.length; i++) {
                String tgt = targets[i];
                String[] hp = split(tgt);
                String host = hp[0];
                int port = parseIntSafe(hp[1], 80);
                long t0 = System.currentTimeMillis();
                boolean ok;
                String note;
                String detail;
                long ms;
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
                    ms = System.currentTimeMillis() - t0;
                    ok = true;
                    note = "bisa dihubungi";
                    detail = "DNS " + host + " → " + addrs[0].getHostAddress();
                } catch (Throwable ex) {
                    ms = System.currentTimeMillis() - t0;
                    ok = false;
                    boolean dnsFail = (ex instanceof UnknownHostException);
                    note = dnsFail
                            ? "DNS gagal (nama domain tidak ditemukan)"
                            : "koneksi gagal (" + ex.getClass().getSimpleName() + ")";
                    detail = "waktu coba: " + HumanLog.fmtMs(ms);
                }
                Boolean prev = prevOk.get(tgt);
                StatusStore.putOriginal(tgt, ok, note, ok ? ms : -1);
                // log HANYA saat status berubah (atau saat pantauan pertama / manual)
                if (prev == null || prev.booleanValue() != ok) {
                    if (ok) {
                        HumanLog.ev('✔', "Server asli " + tgt + " bisa dihubungi · " + HumanLog.fmtMs(ms)
                                + (prev == null ? " (cek pertama)" : " (pulih)"), detail);
                    } else {
                        HumanLog.ev('⚠', "Server asli " + tgt + " TIDAK bisa dihubungi — " + note
                                + "; ini pantauan saja, traffic game tetap tidak diganggu", detail);
                    }
                }
                if (ok) {
                    okCount++;
                    if (summary.length() > 0) {
                        summary.append(" · ");
                    }
                    summary.append(shortOf(tgt)).append(" ").append(ms).append(" ms");
                }
                prevOk.put(tgt, Boolean.valueOf(ok));
            }
            StatusStore.persist();
            if (manual) {
                HumanLog.ev(okCount == targets.length ? '✔' : '⚠',
                        "Pantauan manual — server asli " + okCount + "/" + targets.length + " sehat"
                                + (summary.length() > 0 ? " (" + summary + ")" : ""), null);
            }
            maybePulse(okCount);
        }
    }

    /** Denyut 10 menit — bukti "local running" yang menonjol tanpa derau (README §3.4 poin 3-4). */
    private void maybePulse(int okCount) {
        long now = System.currentTimeMillis();
        if (now - lastPulseAt < PULSE_EVERY_MS) {
            return;
        }
        lastPulseAt = now;
        long up = StatusStore.uptimeMs();
        HumanLog.ev('✔', "Masih hidup · " + HumanLog.fmtMs(up) + " · ping dasbor " + StatusStore.pings()
                + "× · server asli " + okCount + "/" + targets.length + " sehat · mode " + ServerMode.get(), null);
    }

    private static String shortOf(String tgt) {
        int i = tgt.indexOf(':');
        return (i > 0) ? tgt.substring(0, i) : tgt;
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
