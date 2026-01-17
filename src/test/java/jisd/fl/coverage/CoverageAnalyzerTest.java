package jisd.fl.coverage;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.sbfl.coverage.CoverageAnalyzer;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.util.JsonIO;
import jisd.fl.core.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

class CoverageAnalyzerTest {
    @BeforeEach
    void initProperty() {
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.ProjectConfig config = new PropertyLoader.ProjectConfig(
                testProjectDir,
                Path.of("src/main/java"),
                Path.of("src/test/java"),
                Path.of("build/classes/java/main"),
                Path.of("build/classes/java/test")
        );
        PropertyLoader.setProjectConfig(config);
    }

    @Nested
    class conditionalTest {
        String testClassName = "org.sample.coverage.ConditionalTest";
        CoverageCollection cov;

        @BeforeEach
        void init(){
            CoverageAnalyzer ca = new CoverageAnalyzer();
            ca.analyze(testClassName);
            cov = ca.result();
        }
        @Test
        void lineCoverage() {
            cov.printCoverages(Granularity.LINE);
        }

        @Test
        void methodCoverage() {
            cov.printCoverages(Granularity.METHOD);
        }

        @Test
        void classCoverage() {
            cov.printCoverages(Granularity.CLASS);
        }

        @Test
        void jsonExportTest(){
            Path projRoot = Paths.get("").toAbsolutePath();
            File f = new File(projRoot + "/src/test/resources/json/coverage/ConditionalTest.json");
            JsonIO.export(cov, f);
        }

        @Test
        void jsonImportTest(){
            Path projRoot = Paths.get("").toAbsolutePath();
            File f = new File(projRoot + "/src/test/resources/json/coverage/ConditionalTest.json");
            CoverageCollection cc = CoverageCollection.loadFromJson(f);
            cc.printCoverages(Granularity.LINE);
        }
    }

    @Nested
    class LoopTest {
        String testClassName = "org.sample.coverage.LoopTest";
        CoverageCollection cov;

        @BeforeEach
        void init(){
            CoverageAnalyzer ca = new CoverageAnalyzer();
            ca.analyze(testClassName);
            cov = ca.result();
        }

        @Test
        void lineCoverage() {
            cov.printCoverages(Granularity.LINE);
        }

        @Test
        void methodCoverage() {
            cov.printCoverages(Granularity.METHOD);
        }

        @Test
        void classCoverage() {
            cov.printCoverages(Granularity.CLASS);
        }
    }

    @Nested
    class InnerClassTest {
        String testClassName = "org.sample.coverage.InnerClassTest";
        CoverageCollection cov;

        @BeforeEach
        void init(){
            CoverageAnalyzer ca = new CoverageAnalyzer();
            ca.analyze(testClassName);
            cov = ca.result();
        }

        @Test
        void lineCoverage() {
            cov.printCoverages(Granularity.LINE);
        }

        @Test
        void methodCoverage() {
            cov.printCoverages(Granularity.METHOD);
        }

        @Test
        void classCoverage() {
            cov.printCoverages(Granularity.CLASS);
        }
    }
}