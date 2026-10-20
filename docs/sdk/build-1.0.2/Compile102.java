import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher compile build-102 (single-file launcher, sandbox tanpa javac biner):
 *   RELEASE=8 java Compile102.java <sumber .java ...> [-o classes|classes-test] [-cp ...]
 */
public class Compile102 {
    public static void main(String[] args) throws Exception {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) {
            System.err.println("FATAL: jdk.compiler tidak tersedia");
            System.exit(2);
        }
        String base = "/home/z/my-project/work-re/build-102";
        String out = base + "/classes";
        List<String> opt = new ArrayList<>();
        String rel = System.getenv("RELEASE");
        if (rel != null && !rel.trim().isEmpty()) {
            opt.add("--release");
            opt.add(rel.trim());
        }
        List<String> files = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o") && i + 1 < args.length) {
                out = args[++i];
            } else if (args[i].equals("-cp") && i + 1 < args.length) {
                opt.add("-classpath");
                opt.add(args[++i]);
            } else {
                files.add(args[i]);
            }
        }
        if (!opt.contains("-classpath")) {
            opt.add("-classpath");
            opt.add(base + "/stubs");
        }
        opt.add("-d");
        opt.add(out);
        String[] all = new String[opt.size() + files.size()];
        for (int i = 0; i < opt.size(); i++) {
            all[i] = opt.get(i);
        }
        for (int i = 0; i < files.size(); i++) {
            all[opt.size() + i] = files.get(i);
        }
        int r = jc.run(null, null, null, all);
        System.exit(r);
    }
}
