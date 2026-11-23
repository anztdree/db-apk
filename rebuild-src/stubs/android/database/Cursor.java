package android.database;

/**
 * STUB kompilasi — HANYA untuk membangun classes2.dex di desktop.
 * Implementasi asli ada di runtime Android (API 21+). JANGAN dipakai saat run.
 */
public interface Cursor {
    boolean moveToFirst();
    boolean moveToNext();
    boolean isAfterLast();
    int getColumnIndexOrThrow(String name);
    String getString(int column);
    long getLong(int column);
    int getCount();
    void close();
}
