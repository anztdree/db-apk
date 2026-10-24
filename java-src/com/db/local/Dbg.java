package com.db.local;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Floating debug logger (gaya eruda) — ring buffer log + gate izin overlay.
 * Translasi smali -> Java (Task 4-e). Perilaku final hasil patch:
 * attachOverlay LANGSUNG (tanpa startService) + auto-buka izin overlay
 * sekali per install (SharedPreferences "dblocal" / "overlayAsk") + Toast.
 */
public final class Dbg {
    private static final Object LOCK = new Object();
    private static final String[] RING = new String[400];
    private static int ringCount;
    private static boolean toastShown;

    private Dbg() {
        super();
    }

    public static void checkOverlay(Context context) {
        if (context == null) {
            return;
        }
        Context app = context;
        try {
            app = context.getApplicationContext();
            boolean can = true;
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    can = Settings.canDrawOverlays(app);
                }
            } catch (Throwable t) {
                can = true;
            }
            if (can) {
                try {
                    if (DbgService.instance != null || DbgService.shown) {
                        return;
                    }
                    try {
                        // sisa Intent dari patch (startService diganti attachOverlay langsung)
                        new Intent(app, DbgService.class);
                        DbgService.attachOverlay(app);
                    } catch (Throwable t) {
                        DbgService.attachOverlay(app);
                    }
                    return;
                } catch (Throwable t) {
                    // jatuh ke blok pengajuan izin overlay
                }
            } else {
                try {
                    if (Dbg.toastShown) {
                        return;
                    }
                    Dbg.toastShown = true;
                    Toast.makeText(app, "Aktifkan izin 'Tampil di atas aplikasi lain' agar floating debug muncul", 1).show();
                } catch (Throwable t) {
                    // jatuh ke blok pengajuan izin overlay
                }
            }
        } catch (Throwable t) {
            // getApplicationContext gagal -> langsung ke blok pengajuan izin overlay
        }
        try {
            SharedPreferences sp = context.getSharedPreferences("dblocal", 0);
            boolean asked = sp.getBoolean("overlayAsk", false);
            if (!asked) {
                SharedPreferences.Editor ed = sp.edit();
                ed.putBoolean("overlayAsk", true);
                ed.apply();
                Intent askIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                askIntent.setData(Uri.parse("package:" + context.getPackageName()));
                askIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(askIntent);
            }
        } catch (Throwable t) {
        }
    }

    public static void clear() {
        try {
            synchronized (LOCK) {
                for (int i = 0; i < RING.length; i++) {
                    RING[i] = null;
                }
                ringCount = 0;
            }
        } catch (Throwable t) {
        }
    }

    private static String hhmmss() {
        try {
            long secs = System.currentTimeMillis() % 86400000L;
            secs = secs / 1000L;
            int h = (int) (secs / 3600L) % 24;
            int m = (int) ((secs % 3600L) / 60L);
            int s = (int) (secs % 60L);
            StringBuilder sb = new StringBuilder(8);
            two(sb, h);
            sb.append(':');
            two(sb, m);
            sb.append(':');
            two(sb, s);
            return sb.toString();
        } catch (Throwable t) {
            return "--:--:--";
        }
    }

    public static void log(String msg) {
        if (msg == null) {
            msg = "(null)";
        }
        try {
            String line = String.valueOf(hhmmss()) + " " + msg;
            synchronized (LOCK) {
                RING[(int) (ringCount % 400L)] = line;
                ringCount = ringCount + 1;
            }
        } catch (Throwable t) {
        }
        try {
            Log.i("DBLOCAL", msg);
        } catch (Throwable t) {
        }
    }

    public static void log2(String a, String b) {
        try {
            if (a == null) {
                a = "";
            }
            if (b == null) {
                b = "";
            }
            log(String.valueOf(a) + " " + b);
        } catch (Throwable t) {
        }
    }

    public static String tail(int n) {
        if (n <= 0) {
            return "";
        }
        try {
            synchronized (LOCK) {
                int avail = ringCount < 400 ? ringCount : 400;
                if (n >= avail) {
                    n = avail;
                }
                StringBuilder sb = new StringBuilder(n * 64);
                int start = ringCount - n;
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        sb.append('\n');
                    }
                    sb.append(RING[(int) ((long) (start + i) % 400L)]);
                }
                return sb.toString();
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private static void two(StringBuilder sb, int v) {
        if (v < 10) {
            sb.append('0');
        }
        sb.append(v);
    }
}
