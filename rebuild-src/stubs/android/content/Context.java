package android.content;

import java.io.File;

/**
 * STUB kompilasi — HANYA untuk membangun classes2.dex di desktop.
 * Implementasi asli ada di runtime Android (API 21+). JANGAN dipakai saat run.
 */
public class Context {
    public File getExternalFilesDir(String type) {
        throw new RuntimeException("stub");
    }

    public File getFilesDir() {
        throw new RuntimeException("stub");
    }

    public android.content.res.AssetManager getAssets() {
        throw new RuntimeException("stub");
    }
}
