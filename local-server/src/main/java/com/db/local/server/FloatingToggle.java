package com.db.local.server;

import android.app.Application;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tombol melayang (overlay) di atas game untuk SWITCH MODE LOCAL ⇄ ONLINE.
 *
 * v0.3.0 — koreksi pemilik: "namanya floating kok gak bisa di geser".
 * - BISA DIGESER: ikut jari (drag bebas), dibatasi tetap di dalam layar,
 *   posisi terakhir DISIMPAN ke .env (TOGGLE_X/TOGGLE_Y) dan
 *   dipulihkan saat tampil lagi.
 * - GESER vs TEKAN dibedakan lewat ambang touch-slop: gerakan kecil = tekan
 *   (buka panel mode), gerakan besar = geser posisi.
 * - Panel mode: tombol ganti mode dengan konfirmasi dua-tahap (tekan sekali →
 *   "tekan lagi untuk restart") → mode disimpan → aplikasi direstart
 *   (RestartHelper) → BootProvider menyalakan server dengan mode baru.
 * - Hanya tampil saat game di depan: ActivityLifecycleCallbacks memasang /
 *   melepas overlay saat activity resumed / paused — tanpa mengubah kode game.
 * - Izin overlay (SYSTEM_ALERT_WINDOW) sudah ada di manifest original; Android
 *   6+ pemilik mengaktifkannya sekali lewat Setelan. Belum aktif → tombol
 *   tidak tampil dan kejadiannya dicatat di log; game tidak pernah terganggu.
 */
public final class FloatingToggle {
    private static volatile boolean added = false;
    private static volatile boolean panelAdded = false;
    private static volatile boolean panelArmed = false; // konfirmasi dua-tahap
    private static volatile boolean shownOnceLogged = false; // log "tampil" sekali per proses
    private static WindowManager wm;
    private static TextView btn;
    private static LinearLayout panel;
    private static WindowManager.LayoutParams lp;
    private static WindowManager.LayoutParams panelLp;
    private static Handler main;

    private FloatingToggle() {
    }

    public static void setup(final Context ctx) {
        try {
            main = new Handler(Looper.getMainLooper());
            // Tampil hanya saat game di depan — pasang lewat lifecycle, tanpa
            // mengubah kode game sama sekali (client tetap tidak disentuh).
            Context ac = ctx.getApplicationContext();
            if (ac instanceof Application) {
                ((Application) ac).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {
                    }

                    public void onActivityResumed(android.app.Activity a) {
                        show(a);
                    }

                    public void onActivityPaused(android.app.Activity a) {
                        hide(a);
                    }

                    public void onActivityStarted(android.app.Activity a) {
                    }

                    public void onActivityStopped(android.app.Activity a) {
                    }

                    public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {
                    }

                    public void onActivityDestroyed(android.app.Activity a) {
                    }
                });
            } else {
                // Cadangan: tampilkan langsung bila lifecycle tidak tersedia
                show(ctx);
            }
        } catch (Throwable t) {
            try {
                HumanLog.ev("⚠️", "Gagal menyiapkan tombol melayang switch mode — game tetap jalan normal",
                        String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------- tampil / sembunyi ----------------

    private static void show(final Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) {
                // dicatat sekali per proses (saat tombol belum pernah tampil)
                if (!added) {
                    HumanLog.ev("⚠️", "Tombol melayang switch mode TIDAK tampil — izin 'Tampil di atas aplikasi "
                            + "lain' belum aktif. Aktifkan sekali di: Setelan → Aplikasi → DB Local → "
                            + "'Tampil di atas aplikasi lain'", null);
                }
                return;
            }
            main.post(new Runnable() {
                public void run() {
                    addToggle(ctx);
                }
            });
        } catch (Throwable t) {
            logSilent("Tombol melayang gagal tampil", t);
        }
    }

    private static void hide(Context ctx) {
        try {
            main.post(new Runnable() {
                public void run() {
                    try {
                        if (panelAdded && wm != null && panel != null) {
                            wm.removeView(panel);
                        }
                    } catch (Throwable ignored) {
                    }
                    panelAdded = false;
                    panelArmed = false;
                    try {
                        if (added && wm != null && btn != null) {
                            wm.removeView(btn);
                        }
                    } catch (Throwable ignored) {
                    }
                    added = false;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- pemasangan tombol ----------------

    private static void addToggle(final Context ctx) {
        try {
            if (added) {
                return;
            }
            WindowManager w = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (w == null) {
                return;
            }
            wm = w;
            if (btn == null) {
                btn = buildButton(ctx);
                btn.setOnTouchListener(buildDrag(ctx));
            }
            if (lp == null) {
                lp = buildParams();
            }
            wm.addView(btn, lp);
            added = true;
            // Log "tampil" HANYA SEKALI per proses (koreksi owner 0.3.1 — game Egret
            // me-pause/resume activity beberapa kali saat boot, dulu lognya berulang).
            if (!shownOnceLogged) {
                shownOnceLogged = true;
                // 0.4.7: tombol tampil = TEKNIS (bukan hasil/berita; tiap boot sama).
                // Yang tetap PENTING: gagal tampil (⚠) & pergantian mode (ℹ).
                HumanLog.evTech("ℹ️", "Tombol melayang tampil", null);
            }
        } catch (Throwable t) {
            logSilent("Tombol melayang gagal tampil", t);
        }
    }

    private static TextView buildButton(Context ctx) {
        TextView b = new TextView(ctx);
        b.setText(label(Config.mode()));
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(18f);
        b.setGravity(Gravity.CENTER);
        b.setAlpha(0.85f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(modeColor());
        b.setBackground(bg);
        return b;
    }

    private static WindowManager.LayoutParams buildParams() {
        // API 26+ wajib TYPE_APPLICATION_OVERLAY; 23–25 memakai TYPE_PHONE
        // (dengan izin overlay); < 23 TYPE_PHONE cukup (izin masa install).
        int type = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                dp(48), dp(48), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        int tx = Config.toggleX();
        int ty = Config.toggleY();
        if (tx >= 0 && ty >= 0) {
            p.x = tx; // posisi hasil geseran terakhir (tersimpan di .env TOGGLE_X/Y)
            p.y = ty;
        } else {
            p.x = dp(10);
            p.y = dp(190);
        }
        return p;
    }

    // ---------------- geser (drag) ----------------

    private static View.OnTouchListener buildDrag(final Context ctx) {
        final int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        return new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;
            boolean moved;

            public boolean onTouch(View v, MotionEvent ev) {
                try {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downRawX = ev.getRawX();
                            downRawY = ev.getRawY();
                            startX = lp.x;
                            startY = lp.y;
                            moved = false;
                            return true;
                        case MotionEvent.ACTION_MOVE: {
                            float dx = ev.getRawX() - downRawX;
                            float dy = ev.getRawY() - downRawY;
                            if (!moved && Math.abs(dx) > slop || Math.abs(dy) > slop) {
                                moved = true;
                                hidePanel(); // mulai geser → panel ditutup
                            }
                            if (moved) {
                                lp.x = clamp(startX + (int) dx, 0, screenW() - v.getWidth());
                                lp.y = clamp(startY + (int) dy, 0, screenH() - v.getHeight());
                                wm.updateViewLayout(v, lp);
                            }
                            return true;
                        }
                        case MotionEvent.ACTION_UP:
                            if (moved) {
                                Config.setTogglePos(lp.x, lp.y); // posisi tersimpan permanen
                                // 0.4.5: geser tombol = TEKNIS (bukan berita penting)
                                HumanLog.evTech("ℹ️", "Tombol melayang digeser ke (" + lp.x + ", " + lp.y
                                        + ") — posisi tersimpan di .env", null);
                            } else {
                                togglePanel(v.getContext()); // gerakan kecil = tekan
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            return true;
                        default:
                            return false;
                    }
                } catch (Throwable t) {
                    logSilent("Geser tombol melayang kendala", t);
                    return false;
                }
            }
        };
    }

    private static int clamp(int v, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static int screenW() {
        try {
            return android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels;
        } catch (Throwable t) {
            return 1080;
        }
    }

    private static int screenH() {
        try {
            return android.content.res.Resources.getSystem().getDisplayMetrics().heightPixels;
        } catch (Throwable t) {
            return 1920;
        }
    }

    // ---------------- panel mode ----------------

    private static void togglePanel(final Context ctx) {
        if (panelAdded) {
            hidePanel();
            return;
        }
        try {
            if (panel == null) {
                panel = buildPanel(ctx);
            }
            refreshPanel();
            panelLp = buildPanelParams();
            wm.addView(panel, panelLp);
            panelAdded = true;
            panelArmed = false;
        } catch (Throwable t) {
            logSilent("Panel mode gagal tampil", t);
        }
    }

    private static void hidePanel() {
        try {
            if (panelAdded && wm != null && panel != null) {
                wm.removeView(panel);
            }
        } catch (Throwable ignored) {
        }
        panelAdded = false;
        panelArmed = false;
    }

    private static LinearLayout buildPanel(final Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0xF21F2937);
        box.setBackground(bg);
        return box;
    }

    private static void refreshPanel() {
        Context ctx = panel.getContext();
        panel.removeAllViews();

        TextView title = new TextView(ctx);
        title.setText("Mode sekarang: " + Config.mode().toUpperCase());
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(14f);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, row());

        String next = "local".equals(Config.mode()) ? "online" : "local";
        Button go = new Button(ctx);
        go.setAllCaps(false);
        go.setTextSize(13f);
        go.setTextColor(Color.WHITE);
        GradientDrawable gb = new GradientDrawable();
        gb.setCornerRadius(dp(9));
        gb.setColor(0xEE059669);
        go.setBackground(gb);
        go.setText(panelArmed
                ? ("Tekan SEKALI LAGI untuk restart dengan " + next.toUpperCase())
                : ("Ganti ke " + next.toUpperCase() + " + restart aplikasi"));
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!panelArmed) {
                    panelArmed = true;
                    refreshPanel(); // konfirmasi dua-tahap — cegah salah tekan
                    return;
                }
                String now = Config.mode();
                String target = "local".equals(now) ? "online" : "local";
                Config.setMode(target);
                hidePanel();
                try {
                    wm.removeView(btn);
                } catch (Throwable ignored) {
                }
                added = false;
                HumanLog.ev("ℹ️", "SWITCH MODE: " + now.toUpperCase() + " → "
                        + target.toUpperCase() + " — aplikasi direstart",
                        "Mode : " + now.toUpperCase() + " → " + target.toUpperCase()
                                + " (tersimpan di .env)");
                RestartHelper.restart(panel.getContext());
            }
        });
        panel.addView(go, row());

        Button close = new Button(ctx);
        close.setAllCaps(false);
        close.setTextSize(12f);
        close.setTextColor(0xFFE7E5E4);
        GradientDrawable cb = new GradientDrawable();
        cb.setCornerRadius(dp(9));
        cb.setColor(0x33FFFFFF);
        close.setBackground(cb);
        close.setText("Tutup");
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                hidePanel();
            }
        });
        panel.addView(close, row());
    }

    private static LinearLayout.LayoutParams row() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        return p;
    }

    private static WindowManager.LayoutParams buildPanelParams() {
        int type = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                dp(230), ViewGroup.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        int px = lp.x + dp(56);
        int py = lp.y;
        px = clamp(px, 0, Math.max(0, screenW() - dp(240)));
        py = clamp(py, 0, Math.max(0, screenH() - dp(180)));
        p.x = px;
        p.y = py;
        return p;
    }

    // ---------------- util ----------------

    private static String label(String mode) {
        return "local".equals(mode) ? "L" : "O";
    }

    private static int modeColor() {
        return "local".equals(Config.mode()) ? 0xEE059669 : 0xEE1F2937;
    }

    private static int dp(int v) {
        float d = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        return Math.max(1, Math.round(v * d));
    }

    private static void logSilent(String msg, Throwable t) {
        try {
            HumanLog.ev("⚠️", msg + " — mode tetap bisa dipahami dari log/dasbor", String.valueOf(t));
        } catch (Throwable ignored) {
        }
    }
}
