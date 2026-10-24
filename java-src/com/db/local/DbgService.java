package com.db.local;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Floating debug overlay (panel + bubble minifikasi) — translasi smali -> Java (Task 4-e).
 * Semua API overlay bersifat static: bisa jalan tanpa startService (tidak butuh
 * deklarasi manifest); Service instance hanya dipakai bila benar-benar di-start.
 */
public class DbgService extends Service {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable TICKER = new Runnable() {
        public void run() {
            try {
                if (shown && !minimized && !paused && tvBody != null) {
                    tvBody.setText(Dbg.tail(150));
                    if (scroll != null) {
                        scroll.fullScroll(130);
                    }
                }
            } catch (Throwable t) {
            }
            try {
                if (shown) {
                    HANDLER.postDelayed(this, 600L);
                }
            } catch (Throwable t) {
            }
        }
    };
    private static WindowManager WM;
    private static View bubble;
    private static WindowManager.LayoutParams bubbleParams;
    private static LinearLayout footerBar;
    private static LinearLayout headerBar;
    public static volatile DbgService instance;
    private static int lastWidth = -2;
    private static boolean minimized;
    private static View panel;
    private static WindowManager.LayoutParams panelParams;
    private static boolean paused;
    private static ScrollView scroll;
    public static volatile boolean shown;
    private static TextView tvBody;

    public DbgService() {
        super();
    }

    public void onCreate() {
        try {
            instance = this;
        } catch (Throwable t) {
        }
        try {
            attachOverlay(this);
        } catch (Throwable t) {
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        return 1;
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        try {
            detachOverlay();
        } catch (Throwable t) {
        }
        try {
            instance = null;
        } catch (Throwable t) {
        }
    }

    public static void attachOverlay(Context context) {
        if (context == null) {
            return;
        }
        try {
            if (shown) {
                return;
            }
            Context app = context.getApplicationContext();
            HANDLER.post(new Runnable() {
                public void run() {
                    try {
                        attachOverlayInner(app);
                    } catch (Throwable t) {
                        Log.e("DBLocal-Dbg", "floating debug gagal", t);
                        shown = false;
                    }
                }
            });
        } catch (Throwable t) {
        }
    }

    private static synchronized void attachOverlayInner(Context app) {
        if (shown) {
            return;
        }
        try {
            WM = (WindowManager) app.getSystemService("window");
            if (WM == null) {
                return;
            }
            DisplayMetrics dm = app.getResources().getDisplayMetrics();
            int w = (int) (dm.widthPixels * 0.7f);
            int h = (int) (dm.heightPixels * 0.4f);
            int pad4 = dp(dm, 4);
            int pad6 = dp(dm, 6);
            LinearLayout root = new LinearLayout(app);
            root.setOrientation(1);
            root.setBackgroundColor(Color.parseColor("#CC000000"));
            root.setPadding(pad4, pad4, pad4, pad4);
            LinearLayout header = new LinearLayout(app);
            headerBar = header;
            header.setOrientation(0);
            headerBar.setGravity(16);
            TextView title = new TextView(app);
            title.setText("DB-LOCAL");
            title.setTextColor(-1);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextSize(12.0f);
            title.setPadding(pad6, 0, 0, 0);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1.0f));
            headerBar.addView(chip(app, "\u2013", new Runnable() {
                public void run() {
                    setMinimized(true);
                }
            }));
            headerBar.addView(chip(app, "\u00d7", new Runnable() {
                public void run() {
                    closeAll();
                }
            }));
            root.addView(headerBar, new LinearLayout.LayoutParams(-1, -2));
            ScrollView sv = new ScrollView(app);
            scroll = sv;
            TextView body = new TextView(app);
            tvBody = body;
            body.setTextColor(-1);
            tvBody.setTypeface(Typeface.MONOSPACE);
            tvBody.setTextSize(2, 10.0f);
            tvBody.setText(Dbg.tail(150));
            tvBody.setGravity(80);
            scroll.addView(tvBody, new ViewGroupMw());
            root.addView(scroll, new LinearLayout.LayoutParams(-1, h));
            LinearLayout footer = new LinearLayout(app);
            footerBar = footer;
            footer.setOrientation(0);
            footerBar.setGravity(17);
            footerBar.addView(chip(app, "||", new Runnable() {
                public void run() {
                    paused = true;
                }
            }));
            footerBar.addView(chip(app, "\u25b6", new Runnable() {
                public void run() {
                    paused = false;
                }
            }));
            footerBar.addView(chip(app, "C", new Runnable() {
                public void run() {
                    Dbg.clear();
                }
            }));
            footerBar.addView(chip(app, "CP", new Runnable() {
                public void run() {
                    copyTail(app);
                }
            }));
            root.addView(footerBar, new LinearLayout.LayoutParams(-1, -2));
            WindowManager.LayoutParams lp = baseParams();
            lp.width = w;
            lp.height = -2;
            lp.x = dp(dm, 16);
            lp.y = dp(dm, 60);
            minimized = false;
            paused = false;
            panel = root;
            panelParams = lp;
            headerBar.setOnTouchListener(makeDrag());
            WM.addView(root, lp);
            shown = true;
            Dbg.log("floating debug ON");
        } catch (Throwable t) {
            shown = false;
            return;
        }
        try {
            HANDLER.removeCallbacks(TICKER);
        } catch (Throwable t) {
        }
        try {
            HANDLER.postDelayed(TICKER, 600L);
        } catch (Throwable t) {
            shown = false;
        }
    }

    private static void addBubble() {
        try {
            if (WM == null || bubble != null) {
                return;
            }
            Context ctx = panel == null ? null : panel.getContext();
            if (ctx == null) {
                return;
            }
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            TextView tv = new TextView(ctx);
            tv.setText("DBG");
            tv.setTextColor(-1);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextSize(10.0f);
            tv.setPadding(dp(dm, 8), dp(dm, 4), dp(dm, 8), dp(dm, 4));
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#CC000000"));
            gd.setCornerRadius((float) dp(dm, 10));
            tv.setBackground(gd);
            WindowManager.LayoutParams lp = baseParams();
            lp.width = -2;
            lp.height = -2;
            lp.x = panelParams.x;
            lp.y = panelParams.y;
            bubble = tv;
            bubbleParams = lp;
            tv.setOnTouchListener(makeDrag());
            WM.addView(tv, lp);
        } catch (Throwable t) {
            bubble = null;
        }
    }

    private static void removeBubble() {
        try {
            if (WM != null && bubble != null) {
                WM.removeView(bubble);
            }
        } catch (Throwable t) {
        }
        bubble = null;
        bubbleParams = null;
    }

    private static void setMinimized(boolean min) {
        try {
            minimized = min;
            if (panel != null && panelParams != null && WM != null) {
                if (min) {
                    try {
                        panel.setVisibility(8);
                        try {
                            WM.removeView(panel);
                        } catch (Throwable t) {
                        }
                        panelParams.width = -2;
                        panelParams.height = -2;
                        addBubble();
                    } catch (Throwable t) {
                    }
                } else {
                    try {
                        removeBubble();
                        panelParams.width = lastWidth;
                        panelParams.height = -2;
                        panel.setVisibility(0);
                        WM.addView(panel, panelParams);
                    } catch (Throwable t) {
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    private static void closeAll() {
        try {
            if (instance != null) {
                instance.stopSelf();
            }
        } catch (Throwable t) {
        }
        try {
            detachOverlay();
        } catch (Throwable t) {
        }
    }

    private static WindowManager.LayoutParams baseParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                lp.type = 2038; // TYPE_APPLICATION_OVERLAY
            } else {
                lp.type = 2002; // TYPE_PHONE
            }
        } catch (Throwable t) {
            lp.type = 2002;
        }
        try {
            lp.flags = 40;
            lp.format = -3;
            lp.gravity = 51;
        } catch (Throwable t) {
        }
        return lp;
    }

    private static View.OnTouchListener makeDrag() {
        final float[] downRaw = new float[2];
        final int[] downXY = new int[2];
        final boolean[] moved = new boolean[1];
        return new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                try {
                    int action = event.getActionMasked();
                    if (action == 0) {
                        downRaw[0] = event.getRawX();
                        downRaw[1] = event.getRawY();
                        WindowManager.LayoutParams lp = bubble == v ? bubbleParams : panelParams;
                        if (lp != null) {
                            downXY[0] = lp.x;
                            downXY[1] = lp.y;
                        }
                        moved[0] = false;
                    } else if (action == 1) {
                        if (!moved[0]) {
                            if (bubble == v) {
                                setMinimized(false);
                            } else {
                                setMinimized(true);
                            }
                        }
                    } else if (action == 2) {
                        WindowManager.LayoutParams lp = bubble == v ? bubbleParams : panelParams;
                        if (lp != null && WM != null) {
                            float dx = event.getRawX() - downRaw[0];
                            float dy = event.getRawY() - downRaw[1];
                            if (Math.abs(dx) > 12.0f || Math.abs(dy) > 12.0f) {
                                moved[0] = true;
                            }
                            lp.x = downXY[0] + (int) dx;
                            lp.y = downXY[1] + (int) dy;
                            WM.updateViewLayout(v, lp);
                        }
                    }
                } catch (Throwable t) {
                }
                return true;
            }
        };
    }

    private static TextView chip(Context context, String label, Runnable action) {
        TextView tv = new TextView(context);
        try {
            tv.setText(label);
            tv.setTextColor(-1);
            tv.setTextSize(11.0f);
            tv.setGravity(17);
            tv.setPadding(dp(context.getResources().getDisplayMetrics(), 10), 0,
                    dp(context.getResources().getDisplayMetrics(), 10), 0);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#33FFFFFF"));
            gd.setCornerRadius(6.0f);
            tv.setBackground(gd);
            tv.setOnClickListener(new ClickWrap(action));
        } catch (Throwable t) {
        }
        return tv;
    }

    private static void copyTail(Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService("clipboard");
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("dblocal", Dbg.tail(400)));
                Dbg.log("log dicopy ke clipboard");
            }
        } catch (Throwable t) {
        }
    }

    public static void detachOverlay() {
        try {
            HANDLER.removeCallbacks(TICKER);
        } catch (Throwable t) {
        }
        try {
            if (WM != null && panel != null) {
                WM.removeView(panel);
            }
        } catch (Throwable t) {
        }
        panel = null;
        panelParams = null;
        tvBody = null;
        scroll = null;
        shown = false;
    }

    private static int dp(DisplayMetrics dm, int value) {
        float f = (float) value;
        try {
            f = f * dm.density;
            return (int) f;
        } catch (Throwable t) {
            return value;
        }
    }

    private static final class ClickWrap implements View.OnClickListener {
        private final Runnable r;

        ClickWrap(Runnable run) {
            this.r = run;
        }

        public void onClick(View v) {
            try {
                if (r != null) {
                    r.run();
                }
            } catch (Throwable t) {
            }
        }
    }

    private static final class ViewGroupMw extends ViewGroup.LayoutParams {
        ViewGroupMw() {
            super(-1, -2);
        }
    }
}
