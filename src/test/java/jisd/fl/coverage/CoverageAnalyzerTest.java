package jisd.fl.coverage;

import jisd.fl.sbfl.coverage.CoverageAnalyzer;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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