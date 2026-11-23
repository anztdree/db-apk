package com.db.local.server;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * v17 — DETEKSI + PERBAIKAN IZIN "Display over other apps" (SYSTEM_ALERT_WINDOW).
 *
 * Kandidat utama penyebab "setelah sdk selesai LOADING force close" (23 Nov):
 * jalur auto-login SDK menampilkan DialogFragment d/e yang window-nya
 * TYPE_APPLICATION_OVERLAY (2008) — bukti smali com/quickgame/android/sdk/d/e
 * onCreateView. Di Android 6+ (device user = API 31) window type ini WAJIB
 * izin overlay; SDK asli TIDAK PERNAH mengecek canDrawOverlays (float view
 * type 1003 gagalnya ditelan try/catch — makanya diam-diam tidak muncul).
 * Tanpa izin -> WindowManager.BadTokenException saat dialog muncul -> force
 * close tepat "setelah loading selesai". Karena classes.dex forbidden zone,
 * yang bisa kami lakukan: deteksi di boot + banner + TOMBOL 1-KLIK di
 * dashboard yang membuka halaman Settings izin overlay (dari server thread).
 */
public final class OverlayFixer {

    /** Application context (diisi LocalServerStarter di boot; null di desktop). */
    public static volatile Context ctx;

    /**
     * sejak cek terakhir: false = izin OFF. Bagian 39 — dashboard menampilkan
     * ini sebagai INFO netral ("OFF (normal)"), BUKAN banner error: game
     * asli tidak memerlukan overlay dan SDK tetap berjalan normal.
     */
    public static volatile boolean lastCheckOk = true;
    public static volatile boolean checked = false;

    private OverlayFixer() {}

    /**
     * canDrawOverlays = API 23+. Device lebih tua / desktop / stub -> true
     * (window type 2008 pada API <= 22 tidak butuh izin).
     */
    public static boolean hasPermission() {
        Context c = ctx;
        if (c == null) return true;              // desktop smoke — netral
        try {
            return android.provider.Settings.canDrawOverlays(c);
        } catch (Throwable t) {
            return true;                          // NoSuchMethodError API 21/22 dll.
        }
    }

    /** Cek + catat status ke LoggerCore (anti-spam: 1 log per boot). */
    public static void checkAndLog() {
        try {
            boolean ok = hasPermission();
            lastCheckOk = ok;
            checked = true;
            LoggerCore.overlayChecked = true;
            LoggerCore.overlayGranted = ok;
            if (!ok) {
                LoggerCore.Entry e = new LoggerCore.Entry();
                e.port = LoggerCore.PORT_LOGGER;
                e.kind = "SYS";
                e.method = "OVERLAY";
                // Bagian 39 — arahan owner: izin overlay BUKAN error. Game
                // asli tidak pernah meminta overlay dan SDK tetap berjalan
                // normal — entri ini info netral; dashboard menampilkannya
                // sebagai "ℹ️ INFO" (eksklusi eksplisit di isProblem).
                e.path = "Izin overlay OFF — NORMAL. Game asli tidak memerlukan "
                        + "izin overlay; SDK tetap berjalan normal.";
                e.status = 0;
                LoggerCore.log(e);
            }
        } catch (Throwable t) {
            LoggerCore.overlayGranted = true;
        }
    }

    /**
     * Buka halaman Settings izin overlay utk package ini (tombol dashboard).
     * Return null bila OK, atau pesan error.
     */
    public static String openSettings() {
        Context c = ctx;
        if (c == null) return "server bukan Android (desktop) — tidak ada Settings";
        try {
            Intent i = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION",
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            sysLog("tombol dashboard BUKA SETTINGS OVERLAY ditekan — halaman izin dibuka");
            return null;
        } catch (Throwable t) {
            return String.valueOf(t);
        }
    }

    private static void sysLog(String msg) {
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGGER;
        e.kind = "SYS";
        e.method = "OVERLAY";
        e.path = msg;
        LoggerCore.log(e);
    }
}
