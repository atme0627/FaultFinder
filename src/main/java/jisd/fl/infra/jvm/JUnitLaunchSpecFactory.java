package jisd.fl.infra.jvm;

import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.core.util.ToolPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JUnitLaunchSpecFactory {
    private static final String JUNIT_LAUNCHER = "jisd.fl.infra.junit.JUnitTestLauncher";

    public static JVMLaunchSpec defaultSpec(MethodElementName targetTestName){
        return build(targetTestName, List.of());
    }

    public static JVMLaunchSpec withJDWP(MethodElementName targetTestName, String address){
        String jdwp = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + address;
        return build(targetTestName, List.of(jdwp));
    }

    private static JVMLaunchSpec build(MethodElementName targetTestName, List<String> JVMArgs){
        List<Path> classPath = new ArrayList<>(List.of(
                ToolPaths.projectRoot(),
                PropertyLoader.getTargetBinDir(),
                PropertyLoader.getTestBinDir())
        );
        classPath.addAll(ToolPaths.junitDependencyJarPaths());

        return new JVMLaunchSpec(
                JUNIT_LAUNCHER,
                List.of(targetTestName.fullyQualifiedName()),
                classPath,
                JVMArgs,
                Map.of(),
                ToolPaths.projectRoot()
        );
    }
}
