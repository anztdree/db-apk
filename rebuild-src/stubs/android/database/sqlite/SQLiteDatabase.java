package android.database.sqlite;

import android.database.Cursor;

/**
 * STUB kompilasi — HANYA untuk membangun classes2.dex di desktop.
 * Implementasi asli ada di runtime Android (SQLiteDatabase = API 1+, tersedia
 * di minSdk 21). JANGAN dipakai saat run.
 */
public class SQLiteDatabase {
    public interface CursorFactory {
        Cursor newCursor();
    }

    public static SQLiteDatabase openOrCreateDatabase(String path, CursorFactory factory) {
        throw new RuntimeException("stub");
    }

    public void execSQL(String sql) { throw new RuntimeException("stub"); }

    public void execSQL(String sql, Object[] bindArgs) { throw new RuntimeException("stub"); }

    public Cursor rawQuery(String sql, Object[] bindArgs) { throw new RuntimeException("stub"); }

    public void close() { throw new RuntimeException("stub"); }
}
