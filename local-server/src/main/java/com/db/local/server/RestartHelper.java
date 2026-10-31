package com.db.local.server;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

/**
 * Mulai ulang aplikasi secara aman (dipakai switch mode):
 * 1) jadwalkan peluncuran LaunchActivity via AlarmManager (~0,9 dtk lagi)
 * 2) matikan proses sendiri
 * Saat proses hidup lagi: BootProvider otomatis menyalakan server lokal,
 * mode dibaca dari server.json, dan game membaca config sesuai mode baru.
 */
public final class RestartHelper {
    private RestartHelper() {
    }

    public static void restart(final Context ctx) {
        try {
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent pi = pending(ctx, i);
                AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                if (am != null && pi != null) {
                    am.set(AlarmManager.RTC, System.currentTimeMillis() + 900L, pi);
                }
            } else {
                HumanLog.ev('⚠', "Intent peluncur tidak ditemukan — restart otomatis mungkin tidak jalan; "
                        + "buka aplikasi manual untuk menerapkan mode baru", null);
            }
        } catch (Throwable t) {
            try {
                HumanLog.ev('⚠', "Penjadwalan restart gagal — buka aplikasi manual untuk menerapkan mode baru",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                try {
                    Process.killProcess(Process.myPid());
                } catch (Throwable ignored) {
                }
                try {
                    Runtime.getRuntime().exit(0);
                } catch (Throwable ignored) {
                }
            }
        }, 600L);
    }

    private static PendingIntent pending(Context ctx, Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                return PendingIntent.getActivity(ctx, 9001, i,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
            return PendingIntent.getActivity(ctx, 9001, i, PendingIntent.FLAG_UPDATE_CURRENT);
        } catch (Throwable t) {
            return null;
        }
    }
}
