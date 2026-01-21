package jisd.fl.infra.jacoco;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * visitorによるカバレッジ計算時のクラスファイルの重複ロードを防ぐためのクラス。
 * あらかじめクラスファイルをロードする。
 */
public final class ClassFileCache {
    private final Map<String, byte[]> byInternalName;

    private ClassFileCache(Map<String, byte[]> byInternalName) {
        this.byInternalName = byInternalName;
    }

    public static ClassFileCache loadFromClassesDir(Path classesDir) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        try (var s = Files.walk(classesDir)) {
            s.filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> {
                        try {
                            byte[] bytes = Files.readAllBytes(p);

                            // classesDir からの相対パスを internal name にする
                            // e.g. .../classes/org/foo/Bar.class -> org/foo/Bar
                            String rel = classesDir.relativize(p).toString();
                            String internal = rel
                                    .replace(File.separatorChar, '/')
                                    .replaceAll("\\.class$", "");

                            map.put(internal, bytes);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return new ClassFileCache(map);
    }

    public byte[] get(String internalName) {
        return byInternalName.get(internalName);
    }

    public boolean contains(String internalName) {
        return byInternalName.containsKey(internalName);
    }
}
