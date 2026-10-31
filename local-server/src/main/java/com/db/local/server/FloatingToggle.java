package com.db.local.server;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Tombol melayang (overlay) di atas game untuk SWITCH MODE LOCAL ⇄ ONLINE.
 *
 * - Izin overlay (SYSTEM_ALERT_WINDOW) SUDAH dideklarasikan di manifest original;
 *   di Android 6+ pemilik harus mengaktifkannya sekali lewat Setelan
 *   ("Tampil di atas aplikasi lain"). Bila belum, tombol tidak tampil dan
 *   kejadian ini dicatat di log — game tidak pernah terganggu.
 * - Ditekan → mode disimpan ke config/server.json → aplikasi direstart
 *   (RestartHelper) → saat hidup lagi, game membaca config sesuai mode baru.
 */
public final class FloatingToggle {
    private static volatile boolean added = false;

    private FloatingToggle() {
    }

    public static void setup(final Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) {
                HumanLog.ev('⚠', "Tombol melayang switch mode TIDAK tampil — izin 'Tampil di atas aplikasi lain' "
                        + "belum aktif. Aktifkan sekali di: Setelan → Aplikasi → DB Local → 'Tampil di atas aplikasi lain'",
                        null);
                return;
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    addToggle(ctx);
                }
            });
        } catch (Throwable t) {
            try {
                HumanLog.ev('⚠', "Gagal menyiapkan tombol melayang switch mode — game tetap jalan normal",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
    }

    private static void addToggle(final Context ctx) {
        try {
            if (added) {
                return;
            }
            final WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return;
            }
            final TextView btn = new TextView(ctx);
            btn.setText(label(Config.mode()));
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            btn.setTextSize(17f);
            btn.setGravity(Gravity.CENTER);
            btn.setAlpha(0.78f);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xEE1F2937); // abu gelap netral — beda dari warna game, mudah dilihat
            btn.setBackground(bg);

            // API 26+ wajib TYPE_APPLICATION_OVERLAY; 23–25 memakai TYPE_PHONE
            // (dengan izin overlay); < 23 TYPE_PHONE cukup (izin masa install).
            final int type = (Build.VERSION.SDK_INT >= 26)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(46), dp(46), type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = dp(10);
            lp.y = dp(190);

            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    onToggle(ctx, btn);
                }
            });
            wm.addView(btn, lp);
            added = true;
            HumanLog.ev('✔', "Tombol melayang switch mode tampil di layar — tekan untuk ganti LOCAL/ONLINE "
                    + "(aplikasi restart otomatis setelahnya)", null);
        } catch (Throwable t) {
            try {
                HumanLog.ev('⚠', "Tombol melayang gagal tampil — mode tetap bisa dipahami dari log/dasbor",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
    }

    private static void onToggle(final Context ctx, final TextView btn) {
        try {
            String now = Config.mode();
            String next = "local".equals(now) ? "online" : "local";
            Config.setMode(next);
            btn.setText(label(next));
            HumanLog.ev('ℹ', "SWITCH MODE: " + now.toUpperCase() + " → " + next.toUpperCase()
                            + " (tersimpan di server.json) — aplikasi dimulai ulang agar game membaca config sesuai mode baru",
                    null);
        } catch (Throwable t) {
            try {
                HumanLog.ev('⚠', "Switch mode gagal diproses", String.valueOf(t));
            } catch (Throwable ignored) {
            }
            return;
        }
        RestartHelper.restart(ctx);
    }

    private static String label(String mode) {
        return "local".equals(mode) ? "L" : "O";
    }

    private static int dp(int v) {
        float d = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        return Math.max(1, Math.round(v * d));
    }
}
