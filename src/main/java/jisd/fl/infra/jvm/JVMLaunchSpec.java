package jisd.fl.infra.jvm;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

//JVM起動仕様
public record JVMLaunchSpec (
        String mainClass,
        List<String> programArgs,
        List<Path> classpathEntries,
        List<String> jvmArgs,
        Map<String, String> env,
        Path workingDir
) {
    public JVMLaunchSpec {
        Objects.requireNonNull(mainClass, "mainClass");
        programArgs = List.copyOf(programArgs == null ? List.of() : programArgs);
        classpathEntries = List.copyOf(classpathEntries == null ? List.of() : classpathEntries);
        jvmArgs = List.copyOf(jvmArgs == null ? List.of() : jvmArgs);
        env = Map.copyOf(env == null ? Map.of() : env);
        // workingDir は null 可
    }
}