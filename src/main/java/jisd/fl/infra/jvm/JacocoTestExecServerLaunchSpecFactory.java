package jisd.fl.infra.jvm;

import jisd.fl.core.util.PropertyLoader;
import jisd.fl.core.util.ToolPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JacocoTestExecServerLaunchSpecFactory {
    private JacocoTestExecServerLaunchSpecFactory() {}
    private static final String SERVER_MAIN = "jisd.fl.infra.jacoco.exec.JacocoTestExecServerMain";
    /**
     * 固定ポートで JaCoCo agent付きのテスト実行サーバJVMを起動するためのSpec
     */
    public static JVMLaunchSpec defaultSpec(int port) {
        // classpath: tool本体 + 対象bin + テストbin + junit依存 (+ jacoco agent/runtime を入れると安全)
        List<Path> classPath = new ArrayList<>(List.of(
                ToolPaths.projectMain(),
                PropertyLoader.getTargetBinDir(),
                PropertyLoader.getTestBinDir()
        ));
        classPath.addAll(ToolPaths.junitDependencyJarPaths());

        Path jacocoAgentJar = ToolPaths.jacocoAgentJarPath();
        classPath.add(jacocoAgentJar);

        // -javaagent オプション（出力ファイルは不要なので output=none 推奨）
        String agentOpt = "-javaagent:" + jacocoAgentJar + "=output=none,dumponexit=false";

        //親が停止した際に子も停止させるための、親JVMのppid
        long ppid = ProcessHandle.current().pid();
        return new JVMLaunchSpec(
                SERVER_MAIN,
                List.of("--port", Integer.toString(port), "--ppid", Long.toString(ppid)),
                classPath,
                List.of(agentOpt),
                Map.of(),
                ToolPaths.projectRoot()
        );
    }
}
