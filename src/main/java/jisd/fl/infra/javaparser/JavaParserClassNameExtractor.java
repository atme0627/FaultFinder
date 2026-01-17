package jisd.fl.infra.javaparser;

import jisd.fl.util.PropertyLoader;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

public class JavaParserClassNameExtractor {
    //プロジェクト全体
    public static Set<String> getClassNames() {
        return getClassNames(Paths.get(PropertyLoader.getTargetSrcDir()));
    }

    //ディレクトリ指定
    public static Set<String> getClassNames(Path p) {
        ClassExplorer ce = new ClassExplorer(p);
        try {
            Files.walkFileTree(p, ce);
            return ce.result();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class ClassExplorer implements FileVisitor<Path> {
        Path p;
        Set<String> classNames;

        public ClassExplorer(Path targetSrcPath){
            this.p = targetSrcPath;
            this.classNames = new HashSet<>();
        }

        public Set<String> result(){
            return classNames;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if(file.toString().endsWith(".java")){
                classNames.add(p.relativize(file).toString().split("\\.")[0].replace("/", "."));
            }
            return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            System.out.println("failed: " + file.toString());
            return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }
}
