// ============================================================
// K — konteks kernel + ROOT server.
// HUKUM ROOT (permanen): root server WAJIB SEBELAH folder files/:
//   <external>/Android/data/com.db.local/server   (bukan files/server!)
// Kernel NOL PERNAH menulis apa pun ke dalam files/ milik client.
// Fallback internal: /data/data/com.db.local/server (kalau storage
// eksternal tidak tersedia).
// ============================================================
package s2.kernel;

import android.content.Context;

import java.io.File;

public final class K {
    public static final int PORT = 4000;
    public static final String AUTHORITY = "com.db.local.kernel";
    public static final String ENGINE_VERSION = "v7-kernel";

    private static Context appCtx;
    private static File rootDir;      // <...>/com.db.local/server
    private static String rootKind;   // "external" | "internal"

    private K() {}

    public static void init(Context ctx) {
        appCtx = ctx.getApplicationContext();
        rootDir = resolveRoot(appCtx);
    }

    private static File resolveRoot(Context ctx) {
        // 1) SEBELAH files/: parent dari getExternalFilesDir -> Android/data/com.db.local
        try {
            File ext = ctx.getExternalFilesDir(null);
            if (ext != null) {
                File parent = ext.getParentFile();
                if (parent != null) {
                    File root = new File(parent, "server");
                    if (root.mkdirs() || root.isDirectory()) {
                        return root.getCanonicalFile();
                    }
                }
            }
        } catch (Throwable t) {
            // lanjut ke fallback
        }
        // 2) fallback internal: /data/data/com.db.local/server
        try {
            File files = ctx.getFilesDir();
            File parent = files.getParentFile();
            if (parent != null) {
                File root = new File(parent, "server");
                if (root.mkdirs() || root.isDirectory()) {
                    return root.getCanonicalFile();
                }
            }
            return new File(files, "server").getCanonicalFile();
        } catch (Throwable t) {
            return new File("/data/data/com.db.local/server");
        }
    }

    public static Context ctx() { return appCtx; }

    /** Root zona server — SEBELAH folder files/ (Android/data/com.db.local/server). */
    public static File root() { return rootDir; }

    public static String rootKind() { return rootKind; }

    public static void markRootKind(boolean external) {
        rootKind = external ? "external" : "internal";
    }

    /** Path aman di dalam root (anti path-escape). null = tidak valid. */
    public static File safeFile(String rel) {
        if (rel == null) return null;
        try {
            File root = rootDir.getCanonicalFile();
            File f = new File(root, rel);
            String cp = f.getCanonicalPath();
            if (!cp.equals(root.getPath()) && !cp.startsWith(root.getPath() + File.separator)) return null;
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String rel(File f) {
        try {
            return rootDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
        } catch (Throwable t) {
            return f.getName();
        }
    }
}
