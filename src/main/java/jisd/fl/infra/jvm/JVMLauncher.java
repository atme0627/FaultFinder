package jisd.fl.infra.jvm;

import jisd.fl.core.util.ToolPaths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JVMLauncher {
    static public JVMProcess launch(JVMLaunchSpec spec) throws IOException {
        String javaExe = resolveJavaExe();

        // classpath を OS 依存の区切り（; or :）で連結
        String cp = String.join(
                File.pathSeparator,
                spec.classpathEntries().stream().map(Path::toString).toList()
        );

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.addAll(spec.jvmArgs());

        if (!cp.isEmpty()) {
            cmd.add("-cp");
            cmd.add(cp);
        }

        cmd.add(spec.mainClass());
        cmd.addAll(spec.programArgs());

        ProcessBuilder pb = new ProcessBuilder(cmd);

        // 作業ディレクトリ
        if (spec.workingDir() != null) {
            pb.directory(spec.workingDir().toFile());
        }

        // 環境変数
        if (!spec.env().isEmpty()) {
            pb.environment().putAll(spec.env());
        }

//        //デバッグ用。起動したプロセス内を見る。
        File outFile = ToolPaths.projectRoot().resolve("jacoco-server.out").toFile();
        File errFile = ToolPaths.projectRoot().resolve("jacoco-server.err").toFile();

        pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));

        Process p = pb.start();
        return JVMProcess.fromProcess(p);
    }

    private static String resolveJavaExe() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        String exe = isWindows() ? "java.exe" : "java";
        return javaHome.resolve("bin").resolve(exe).toString();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }
}
