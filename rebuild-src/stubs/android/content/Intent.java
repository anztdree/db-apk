package android.content;

import android.net.Uri;

/**
 * STUB kompilasi — HANYA untuk membangun classes2.dex di desktop.
 * Implementasi asli ada di runtime Android (API 21+). JANGAN dipakai saat run.
 */
public class Intent {
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;

    public Intent(String action, Uri data) {
        throw new RuntimeException("stub");
    }

    public Intent addFlags(int flags) {
        throw new RuntimeException("stub");
    }
}
