package android.content.res;

import java.io.IOException;
import java.io.InputStream;

/**
 * STUB kompilasi — HANYA untuk membangun classes2.dex di desktop.
 * Implementasi asli ada di runtime Android (API 21+). JANGAN dipakai saat run.
 */
public class AssetManager {
    public InputStream open(String fileName) throws IOException {
        throw new RuntimeException("stub");
    }

    public String[] list(String dirName) throws IOException {
        throw new RuntimeException("stub");
    }
}
