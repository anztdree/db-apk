package com.db.local.server;

import java.io.File;

/**
 * 0.5.4 — REKAM DIHAPUS TOTAL (keputusan owner: "CONFIG bukan REKAM jejak").
 *
 * Semua metode menjadi NO-OP (tanpa menulis file, tanpa memori) — signatur
 * dipertahankan agar pemanggil (LoginServer/SdkApi/GameServer) tidak perlu
 * diubah. Dulu kelas ini merekam payload mentah ke capture/rekam.jsonl +
 * capture/payload/ (tab REKAM dasbor 0.5.0); tab REKAM sudah dihapus dari
 * dasbor di 0.5.3 dan kini seluruh jalurnya mati:
 *   - tidak ada lagi folder capture/ di device (Cleaner tetap melindunginya
 *     bila ada sisa dari versi lama — akan terhapus manual oleh pemilik);
 *   - tidak ada lagi baris log REKAM (dulu "[📼] REKAM aktif …" di SEMUA
 *     mode = log campur aduk);
 *   - kebutuhan "HttpCanary" kini dipenuhi baris 🌐/📄/🎮 di feed log (0.5.4).
 */
public final class Capture {

    private Capture() {
    }

    /** No-op (REKAM dihapus 0.5.4). */
    public static void init(File root) {
    }

    /** No-op — selalu false (REKAM dihapus 0.5.4). */
    public static boolean on() {
        return false;
    }

    /** No-op — selalu 0 (REKAM dihapus 0.5.4). */
    public static long recordedCount() {
        return 0;
    }

    /** No-op (REKAM dihapus 0.5.4). */
    public static void login(String d, String name, String info, String payload, int size, long ms) {
    }

    /** No-op (REKAM dihapus 0.5.4). */
    public static void sdk(String d, String name, String info, String payload, int size, long ms) {
    }

    /** No-op (REKAM dihapus 0.5.4). */
    public static void game(String d, String name, String info, String payload, int size, long ms) {
    }

    /** No-op (REKAM dihapus 0.5.4). */
    public static void proxy(String d, String name, String info, String payload, int size, long ms) {
    }

    /** No-op — JSON kosong (REKAM dihapus 0.5.4). */
    public static String snapshotJson(long sinceId) {
        return "[]";
    }

    /** No-op — teks kosong (REKAM dihapus 0.5.4). */
    public static String payloadById(long id) {
        return "";
    }

    /** No-op (REKAM dihapus 0.5.4). */
    public static void clearMem() {
    }

    /** No-op — teks kosong (REKAM dihapus 0.5.4). */
    public static String tailText() {
        return "(REKAM sudah dihapus — CONFIG bukan rekam jejak)";
    }
}
