package jisd.fl.core.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * ユーザが手でpropertiesを書かなくてよい前提の、最小PropertyLoader。
 *
 * - 初回起動時に ~/.faultfinder/project.properties を自動生成
 * - 以後「最近使った対象プロジェクト設定」を読み書きする
 *
 * 保存するキー（project.properties）:
 *   projectRoot=/path/to/targetProject
 *   targetBinPath=build/classes/java/main
 *   testBinPath=build/classes/java/test
 *   targetSrcPath=src/main/java
 *   testSrcPath=src/test/java
 *
 * ※ *Path は「root相対（推奨）または絶対」のどちらでも許容。
 */
public final class PropertyLoader {

    private static final Path USER_DIR =
            Path.of(System.getProperty("user.home"), ".faultfinder");
    private static final Path PROJECT_FILE =
            USER_DIR.resolve("project.properties");

    private static final Properties PROJECT = new Properties();

    static {
        initProjectFileIfAbsent();
        loadFromFile(PROJECT_FILE, PROJECT);
    }

    private PropertyLoader() {}

    // ====== Public API ======

    /** 起動時点の “対象プロジェクト設定” を不変オブジェクトとして取得 */
    public static ProjectConfig getTargetProjectConfig() {
        Path root = Path.of(requireNonBlank(PROJECT, "projectRoot"));

        return new ProjectConfig(
                root,
                Path.of(requireNonBlank(PROJECT, "targetSrcPath")),
                Path.of(requireNonBlank(PROJECT, "testSrcPath")),
                Path.of(requireNonBlank(PROJECT, "targetBinPath")),
                Path.of(requireNonBlank(PROJECT, "testBinPath"))
        );
    }

    /** 対象プロジェクトを切り替えたときに呼ぶ（最近使った対象を保存） */
    public static void setProjectConfig(ProjectConfig cfg) {
        Objects.requireNonNull(cfg);

        PROJECT.setProperty("projectRoot", cfg.projectRoot().toString());
        PROJECT.setProperty("targetBinPath", cfg.targetBinPath().toString());
        PROJECT.setProperty("testBinPath", cfg.testBinPath().toString());
        PROJECT.setProperty("targetSrcPath", cfg.targetSrcPath().toString());
        PROJECT.setProperty("testSrcPath", cfg.testSrcPath().toString());

        storeToFile(PROJECT_FILE, PROJECT);
    }

    // Convenience
    public static Path getProjectRoot() { return getTargetProjectConfig().projectRoot(); }
    public static Path getTargetBinDir() { return getTargetProjectConfig().targetBinDir(); }
    public static Path getTestBinDir() { return getTargetProjectConfig().testBinDir(); }
    public static Path getTargetSrcDir() { return getTargetProjectConfig().targetSrcDir(); }
    public static Path getTestSrcDir() { return getTargetProjectConfig().testSrcDir(); }

    // ====== Record ======

    public record ProjectConfig(
            Path projectRoot,
            Path targetSrcPath,
            Path testSrcPath,
            Path targetBinPath,
            Path testBinPath
    ) {
        public ProjectConfig {
            Objects.requireNonNull(projectRoot);
            Objects.requireNonNull(targetBinPath);
            Objects.requireNonNull(testBinPath);
            Objects.requireNonNull(targetSrcPath);
            Objects.requireNonNull(testSrcPath);
        }

        /** root相対なら絶対パス化、絶対ならそのまま */
        public Path targetBinDir() { return resolve(targetBinPath); }
        public Path testBinDir()   { return resolve(testBinPath); }
        public Path targetSrcDir() { return resolve(targetSrcPath); }
        public Path testSrcDir()   { return resolve(testSrcPath); }

        private Path resolve(Path relOrAbs) {
            return relOrAbs.isAbsolute() ? relOrAbs : projectRoot.resolve(relOrAbs);
        }
    }

    // ====== Init (no manual editing) ======

    /**
     * project.properties が無い場合に自動生成する。
     * 初期値は “よくあるGradle規約 + カレントディレクトリ”。
     */
    private static void initProjectFileIfAbsent() {
        try {
            Files.createDirectories(USER_DIR);
            if (Files.exists(PROJECT_FILE)) return;

            Properties init = new Properties();

            // 初期はカレントを対象プロジェクトとみなす（運用に合わせて変更OK）
            init.setProperty("projectRoot", System.getProperty("user.dir"));

            // よくあるGradle規約（あとで saveTargetProjectConfig で更新される想定）
            init.setProperty("targetBinPath", "build/classes/java/main");
            init.setProperty("testBinPath", "build/classes/java/test");
            init.setProperty("targetSrcPath", "src/main/java");
            init.setProperty("testSrcPath", "src/test/java");

            storeToFile(PROJECT_FILE, init);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ====== IO helpers ======

    private static void loadFromFile(Path file, Properties out) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            out.load(r);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void storeToFile(Path file, Properties p) {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String requireNonBlank(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return v.trim();
    }
}
