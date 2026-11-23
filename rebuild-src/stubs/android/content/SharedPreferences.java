package android.content;

/**
 * STUB kompilasi — HANYA untuk membangun classes2.dex di desktop.
 * Signature = ASLI framework (API 1): runtime ART menyediakan implementasi
 * nyata; dipakai SessionReset (v18) untuk menghapus sesi tersimpan SDK via
 * API framework resmi (bukan edit XML manual — konsisten dgn cache proses).
 */
public interface SharedPreferences {

    /** Signature asli: interface Editor di dalam SharedPreferences. */
    interface Editor {
        Editor putString(String key, String value);
        Editor putLong(String key, long value);
        Editor remove(String key);
        Editor clear();
        boolean commit();
        void apply();
    }

    Editor edit();

    String getString(String key, String defValue);

    long getLong(String key, long defValue);

    boolean contains(String key);
}
