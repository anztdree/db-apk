import java.io.File;
import java.util.concurrent.CountDownLatch;
import com.db.local.server.Db;
import com.db.local.server.ServerCore;
import com.db.local.server.LoggerCore;
import com.db.local.server.CrashHook;
import com.db.local.server.ExitInfo;
import com.db.local.server.SessionReset;

public class SmokeMain {
    public static void main(String[] args) throws Exception {
        // v18 — self test JSON surgery info.qg (throw = smoke gagal total)
        SessionReset.selfTest();
        String root = args.length > 0 ? args[0] : "/tmp/v13root";
        String doc  = args.length > 1 ? args[1] : "/home/z/ws9/server_root";
        LoggerCore.serverRootPath = root;
        // v18 — ExitInfo jalur desktop (entri SYS/EXITINFO "desktop — dilewati")
        ExitInfo.logPrevious(null);
        new File(root, "sdk").mkdirs();
        Db.open(root);   // Bagian 30 — migrasi lokasi lama di boot (persis APK)
        ServerCore.start(new File(doc), null, new CountDownLatch(0));
        // v17 — uji CrashHook: arg ke-3 "synthcrash" = buat laporan crash
        // sintetis 1,5 detik setelah start (hanya smoke desktop, bukan APK).
        if (args.length > 2 && "synthcrash".equals(args[2])) {
            CrashHook.install();
            new Thread(new Runnable() {
                public void run() {
                    try { Thread.sleep(1500); } catch (InterruptedException ie) {}
                    CrashHook.report(
                            new RuntimeException("uji-crash-sintetis-v17: stack ini harus tampil di /crash"),
                            Thread.currentThread());
                }
            }, "uji-crash").start();
        }
        Thread.currentThread().join();
    }
}
