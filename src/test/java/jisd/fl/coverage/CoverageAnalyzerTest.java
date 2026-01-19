package jisd.fl.coverage;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import jisd.fl.presenter.SbflCoveragePrinter;
import jisd.fl.sbfl.coverage.CoverageAnalyzer;
import jisd.fl.core.entity.sbfl.Granularity;
import jisd.fl.util.JsonIO;
import jisd.fl.core.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

class CoverageAnalyzerTest {
    SbflCoveragePrinter printer = new SbflCoveragePrinter();
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
        ProjectSbflCoverage cov;

        @BeforeEach
        void init(){
            CoverageAnalyzer ca = new CoverageAnalyzer();
            ca.analyze(testClassName);
            cov = ca.result();
        }
        @Test
        void lineCoverage() {
            printer.print(cov, Granularity.LINE);
        }

        @Test
        void methodCoverage() {
            printer.print(cov, Granularity.METHOD);
        }

        @Test
        void classCoverage() {
            printer.print(cov, Granularity.CLASS);
        }
    }

    @Nested
    class LoopTest {
        String testClassName = "org.sample.coverage.LoopTest";
        ProjectSbflCoverage cov;

        @BeforeEach
        void init(){
            CoverageAnalyzer ca = new CoverageAnalyzer();
            ca.analyze(testClassName);
            cov = ca.result();
        }

        @Test
        void lineCoverage() {
            printer.print(cov, Granularity.LINE);
        }

        @Test
        void methodCoverage() {
            printer.print(cov, Granularity.METHOD);
        }

        @Test
        void classCoverage() {
            printer.print(cov, Granularity.CLASS);
        }
    }

    @Nested
    class InnerClassTest {
        String testClassName = "org.sample.coverage.InnerClassTest";
        ProjectSbflCoverage cov;

        @BeforeEach
        void init(){
            CoverageAnalyzer ca = new CoverageAnalyzer();
            ca.analyze(testClassName);
            cov = ca.result();
        }

        @Test
        void lineCoverage() {
            printer.print(cov, Granularity.LINE);
        }

        @Test
        void methodCoverage() {
            printer.print(cov, Granularity.METHOD);
        }

        @Test
        void classCoverage() {
            printer.print(cov, Granularity.CLASS);
        }
    }
}