package com.db.local.server;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Pemicu auto-start: Android membuat ContentProvider SEBELUM Application.onCreate,
 * jadi server lokal selalu hidup duluan tanpa mengubah kode game sama sekali.
 * Semua operasi content dibuat no-op (provider ini tidak pernah dipakai sebagai content).
 */
public class BootProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        try {
            Starter.boot(getContext());
        } catch (Throwable ignored) {
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
