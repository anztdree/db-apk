// ============================================================
// KERNELPROVIDER — entrypoint SISTEM 2.
// ContentProvider: jalan OTOMATIS SEBELUM Application.onCreate,
// sesuai entri AndroidManifest (com.db.local.kernel, exported=false).
// Boot chain (sekali jalan, lalu diam):
//   1. Seed zona TERTANAM di dex -> <root> (SEBELAH files/, file
//      device menang kalau sudah ada — seed TIDAK PERNAH menimpa
//      editan owner, dan TIDAK PERNAH menyentuh folder files/)
//   2. Init KV SQLite + logger mirror
//   3. Build handler set awal (Tier-1 JSON + Tier-2 JS + lib)
//   4. Engine.start -> bind 127.0.0.1:4000
//   5. Timer hot-swap 2 detik + janitor sesi wire
// ============================================================
package s2.kernel;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class KernelProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        try {
            Context ctx = getContext();
            if (ctx == null) {
                return true;
            }
            K.init(ctx);
            Thread boot = new Thread(new Runnable() {
                public void run() {
                    try {
                        boot();
                    } catch (Throwable t) {
                        Logr.write("error", "engine", "boot error (kernel tetap hidup): " + t);
                    }
                }
            }, "s2-kernel-boot");
            boot.setDaemon(true);
            boot.start();
        } catch (Throwable t) {
            // kernel tidak boleh membuat app crash
        }
        return true;
    }

    private static void boot() {
        // 1. seed zona tertanam -> root (SEBELAH files/); file device menang
        Seed.apply(K.root());
        Logr.write("info", "engine", "kernel " + K.ENGINE_VERSION + " root=" + K.root().getAbsolutePath()
                + " (kind=" + K.rootKind() + ", SEBELAH files/ — folder files/ client tidak disentuh)");

        // 2. KV + logger
        Logr.attachMirror(new FileAdapter(K.root(), "data").file());
        KV.init(K.root());

        // 3. handler set awal
        Hot.resetFingerprint();
        Hot.scanAndBuild(true);

        // 4. engine
        Http.start();

        // 5. hot-swap timer + janitor
        startHotSwapTimer();
        Wire.startSessionJanitor();

        Logr.write("info", "engine", "SISTEM 2 (mini-server) hidup di port " + Wire.engineCfg().port
                + " — dashboard UI /dash — API /dash/api/* — wire /socket.io/ (EIO3)");
        Logr.write("info", "engine", "self-test TEA \"verification\": " + (Codec.selfTestTea() ? "LOLOS" : "GAGAL"));
    }

    /** adapter kecil utk path data/ (file() -> <root>/data). */
    private static class FileAdapter {
        private final java.io.File f;

        FileAdapter(java.io.File root, String child) {
            this.f = new java.io.File(root, child);
        }

        java.io.File file() {
            return f;
        }
    }

    private static void startHotSwapTimer() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        Hot.maybeRescan();
                    } catch (Throwable ignore) {
                        // catch(Throwable) — kontrak anti-FC
                    }
                }
            }
        }, "s2-kernel-hotswap");
        t.setDaemon(true);
        t.start();
    }

    // ---------- stub ContentProvider ----------
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
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
