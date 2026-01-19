package jisd.fl.core.util;

import jisd.fl.core.entity.element.CodeElementIdentifier;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class ToolPaths {
    //build/はプロジェクトルート直下にある想定
    public static Path projectRoot() {
        Path loc = codeSourcePath(ToolPaths.class).toAbsolutePath().normalize();
        while(loc != null){
            if(loc.getFileName() != null && loc.getFileName().toString().equals("build")){
                return loc.getParent();
            }
            loc = loc.getParent();
        }
        return loc;
    }

    public static Path projectMain(){
        return projectRoot().resolve("build/classes/java/main");
    }

    public static List<Path> junitDependencyJarPaths(){
        Path junitDir =  projectRoot().resolve("locallib/junit-dependency");
        try (var s = Files.list(junitDir)){
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Optional<Path> findSourceFilePath(CodeElementIdentifier<?> e){
        Path p;
        //まずプロダクションコード内を探す
        p = PropertyLoader.getTargetSrcDir().resolve(e.fullyQualifiedClassName().replace('.', '/') + ".java");
        if(Files.exists(p)) return Optional.of(p);
        //なかったらテストコード内を探す
        p = PropertyLoader.getTestSrcDir().resolve(e.fullyQualifiedClassName().replace('.', '/') + ".java");
        if(Files.exists(p)) return Optional.of(p);
        return Optional.empty();
    }
    private static Path codeSourcePath(Class<?> clazz){
        try {
            var cs = clazz.getProtectionDomain().getCodeSource();
            if (cs == null) throw new IllegalStateException("CodeSource is null: " + clazz.getName());
            return Path.of(cs.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path jacocoAgentJarPath() {
        return projectRoot().resolve("locallib/jacocoagent.jar");
    }
}
