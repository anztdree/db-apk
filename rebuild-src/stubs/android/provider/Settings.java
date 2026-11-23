package android.provider;

import android.content.Context;

/**
 * STUB kompilasi — HANYA untuk membangun classes2.dex di desktop.
 * Implementasi asli ada di runtime Android (canDrawOverlays = API 23;
 * pemakaian nyata di OverlayFixer dibungkus try/catch — API 21/22 tidak
 * butuh izin overlay untuk window type 2008).
 */
public final class Settings {
    public static boolean canDrawOverlays(Context context) {
        throw new RuntimeException("stub");
    }
}
