import com.db.local.server.LoggerHandler;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * v28 — helper smoke DESKTOP (BUKAN bagian dex): dump HTML dashboard 4000
 * dari LoggerHandler ke file, dipakai verify_formatter_v28.js (uji formatter
 * v5 teks-per-teks pola readme 81.3).
 */
public class FormatterDump {
    public static void main(String[] args) throws Exception {
        LoggerHandler h = new LoggerHandler(4000);
        Method m = LoggerHandler.class.getDeclaredMethod("dashboardHtml");
        m.setAccessible(true);
        String html = (String) m.invoke(h);
        Files.write(Paths.get(args[0]), html.getBytes(StandardCharsets.UTF_8));
        System.out.println("dashboard html -> " + args[0] + " (" + html.length() + " chars)");
    }
}
