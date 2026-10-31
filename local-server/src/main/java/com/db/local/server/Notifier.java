package com.db.local.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * Notifikasi status "server lokal HIDUP" di HP — jawaban feedback owner:
 * "local running tidak terlihat jelas". Sekarang keberadaan server lokal terlihat
 * langsung di baris notifikasi, tanpa perlu membuka dasbor.
 *
 * - targetSdk 30 → tidak butuh izin runtime (POST_NOTIFICATIONS baru wajib di API 33);
 *   ditambahkan tetap di manifest sebagai penanda.
 * - Ikon memakai resource bawaan platform (android.R.drawable) — TIDAK menambah
 *   resource apa pun ke APK, kode game tetap tak tersentuh.
 * - Gagal membuat notifikasi TIDAK PERNAH memengaruhi game/server (fail-safe).
 */
public final class Notifier {
    private static final String CH_ID = "dblsf_status";
    private static final int NOTIF_ID = 40000;
    private static volatile Context appCtx = null;
    private static volatile boolean posted = false;

    private Notifier() {
    }

    /** Dipanggil Starter saat server berhasil hidup. */
    public static void show(Context ctx, String mode, int port) {
        try {
            if (ctx != null) {
                Context ac = ctx.getApplicationContext();
                appCtx = (ac != null) ? ac : ctx;
            }
            Context c = appCtx;
            if (c == null) {
                return;
            }
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH_ID, "Status server lokal",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Menandakan server lokal DB sedang hidup bersebelahan dengan server asli");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(c, CH_ID);
            } else {
                b = new Notification.Builder(c);
                b.setPriority(Notification.PRIORITY_LOW);
            }
            b.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                    .setContentTitle("DB LOCAL · server lokal HIDUP")
                    .setContentText("port " + port + " · mode " + mode
                            + " · dasbor http://127.0.0.1:" + port)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true);
            nm.notify(NOTIF_ID, b.build());
            if (!posted) {
                posted = true;
                HumanLog.ev('ℹ', "Notifikasi 'server lokal HIDUP' dipasang — lokal running sekarang terlihat langsung di HP, tanpa buka dasbor", null);
            }
        } catch (Throwable t) {
            try {
                HumanLog.ev('⚠', "Notifikasi status tidak bisa dipasang — dasbor tetap berfungsi normal",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
    }

    /** Perbarui teks notifikasi (mis. setelah mode berganti). Pakai context tersimpan. */
    public static void update(String mode) {
        if (posted) {
            int port;
            try {
                port = StatusStore.port();
            } catch (Throwable t) {
                port = 4000;
            }
            show(null, mode, port);
        }
    }
}
