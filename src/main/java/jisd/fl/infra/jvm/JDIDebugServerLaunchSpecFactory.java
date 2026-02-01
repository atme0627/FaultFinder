package jisd.fl.infra.jvm;

import jisd.fl.core.util.PropertyLoader;
import jisd.fl.core.util.ToolPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDI デバッグ用テスト実行サーバー JVM の起動仕様を生成するファクトリ。
 *
 * TCP ポート（テスト実行指示用）と JDWP ポート（デバッグ制御用）の両方を使用する。
 * JDWP は {@code suspend=n} で起動し、サーバーが TCP listen を開始した後にデバッガーがアタッチする。
 */
public class JDIDebugServerLaunchSpecFactory {
    private JDIDebugServerLaunchSpecFactory() {}

    private static final String SERVER_MAIN = "jisd.fl.infra.jdi.testexec.JDIDebugServerMain";

    /**
     * @param tcpPort     TCP サーバーポート（RUN/QUIT コマンド用）
     * @param jdwpAddress JDWP アドレス（例: "localhost:5005"）
     */
    public static JVMLaunchSpec create(int tcpPort, String jdwpAddress) {
        List<Path> classPath = new ArrayList<>(List.of(
                ToolPaths.projectMain(),
                PropertyLoader.getTargetBinDir(),
                PropertyLoader.getTestBinDir()
        ));
        classPath.addAll(ToolPaths.junitDependencyJarPaths());

        String jdwpOpt = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + jdwpAddress;

        long ppid = ProcessHandle.current().pid();
        return new JVMLaunchSpec(
                SERVER_MAIN,
                List.of("--port", Integer.toString(tcpPort), "--ppid", Long.toString(ppid)),
                classPath,
                List.of(jdwpOpt),
                Map.of(),
                ToolPaths.projectRoot()
        );
    }
}
