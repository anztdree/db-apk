import java.io.File;
import java.util.concurrent.CountDownLatch;
import com.db.local.server.ServerCore;
import com.db.local.server.LoggerCore;

public class SmokeMain {
    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : "/tmp/v13root";
        String doc  = args.length > 1 ? args[1] : "/home/z/ws9/server_root";
        LoggerCore.serverRootPath = root;
        new File(root, "data").mkdirs();
        ServerCore.start(new File(doc), null, new CountDownLatch(0));
        Thread.currentThread().join();
    }
}
