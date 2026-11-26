import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import com.db.local.server.Db;
import com.db.local.server.ServerCore;
import com.db.local.server.LoggerCore;
import com.db.local.server.DiskWriter;
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

        // ================= v22 (Bagian 70) — LOG 1 FILE DI-TUMPUK =================
        // Arahan owner (kata-kata persis): "LOG ITU 1 FILE DI TUMPUK !! BUKAN
        // MALAH BIKIN BARU TIAP MASUK APLIKASI!!" — simulasi open ke-2: file
        // server.ndjson SUDAH ADA (sisa sesi sebelumnya), init harus APPEND ke
        // file YANG SAMA — bukan membuat file baru log-<waktu>.ndjson.
        File logsDir = new File(root, "logs");
        if (!logsDir.exists()) logsDir.mkdirs();
        File serverLog = new File(logsDir, DiskWriter.SERVER_LOG);
        FileOutputStream fo = new FileOutputStream(serverLog, true);
        try {
            fo.write(("{\"seq\":1,\"ts\":1700000000000,\"time\":\"2026-11-14 22:13:20.000\",\"port\":0,\"kind\":\"BOOT\",\"method\":\"BOOT\",\"path\":\"uji-sesi-lama-baris-1 (simulasi open sebelumnya — v22 harus APPEND, bukan file baru)\",\"status\":0,\"durMs\":0,\"reqLen\":0,\"respLen\":0}\n").getBytes(StandardCharsets.UTF_8));
            fo.write(("{\"seq\":2,\"ts\":1700000000500,\"time\":\"2026-11-14 22:13:20.500\",\"port\":0,\"kind\":\"BOOT\",\"method\":\"BOOT\",\"path\":\"uji-sesi-lama-baris-2 (simulasi open sebelumnya)\",\"status\":0,\"durMs\":0,\"reqLen\":0,\"respLen\":0}\n").getBytes(StandardCharsets.UTF_8));
        } finally {
            fo.close();
        }
        // v22 — init DiskWriter PERSIS LocalServerStarter (setelah baris lama ada)
        DiskWriter.init(logsDir);
        // v22 — ExitInfo: entri EXIT masuk log GLOBAL yang SAMA + DEDUP exitTime
        // (4 panggilan → 3 baris EXIT; panggilan ke-2 duplikat → TIDAK ditulis)
        ExitInfo.appendExitEntry("EXIT_SELF", "uji-exit-smoke-v22", 1700000000100L, "trace-uji-exit-normal");
        ExitInfo.appendExitEntry("EXIT_SELF", "uji-exit-smoke-v22", 1700000000100L, "trace-uji-exit-normal"); // dup → skip
        ExitInfo.appendExitEntry("USER_REQUESTED", "uji-exit-smoke-v22-b", 1700000000200L, "");
        ExitInfo.appendExitEntry("CRASH", "uji-exit-smoke-v22-crash", 1700000000300L, "trace-crash-uji-v22");
        // v22 — baris SESI BARU via LoggerCore.log → harus masuk FILE YANG SAMA
        LoggerCore.Entry e = new LoggerCore.Entry();
        e.port = LoggerCore.PORT_LOGGER;
        e.kind = "BOOT";
        e.method = "BOOT";
        e.path = "uji-append-smoke-v22-sesi-baru (open ke-2 — tetap server.ndjson)";
        LoggerCore.log(e);
        // tunggu writer thread flush (maks 5 dtk) — append lama+baru satu file
        for (int i = 0; i < 100; i++) {
            String c = new String(java.nio.file.Files.readAllBytes(serverLog.toPath()),
                    StandardCharsets.UTF_8);
            if (c.indexOf("uji-append-smoke-v22-sesi-baru") >= 0
                    && c.indexOf("\"kind\":\"EXIT\"") >= 0) break;
            Thread.sleep(50);
        }
        // v18 — ExitInfo jalur desktop (BOOT line "desktop — skipped")
        ExitInfo.logPrevious(null);

        new File(root, "sdk").mkdirs();
        Db.open(root);   // Bagian 30 — migrasi lokasi lama di boot (persis APK)
        // Bagian 67 — mirror APK boot: self-seed login-server 5 file event
        // (L2 — tepat setelah Db.open, persis LocalServerStarter)
        com.db.local.server.LoginServerConfig.ensure();
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
