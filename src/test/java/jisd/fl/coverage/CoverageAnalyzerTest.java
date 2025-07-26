package jisd.fl.coverage;

import jisd.fl.sbfl.coverage.CoverageAnalyzer;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.util.JsonIO;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

class CoverageAnalyzerTest {
    @BeforeEach
    void initProperty() {
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
    }

    @Nested
    class conditionalTest {
        String testClassName = "org.sample.coverage.ConditionalTest";
        CoverageCollection cov;

        @BeforeEach
        void init(){
            CoverageAnalyzer ca = new CoverageAnalyzer();
            cov = ca.analyzeAll(testClassName);
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
            JsonIO.exportCoverage(cov, f);
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
            cov = ca.analyzeAll(testClassName);
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
            cov = ca.analyzeAll(testClassName);
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