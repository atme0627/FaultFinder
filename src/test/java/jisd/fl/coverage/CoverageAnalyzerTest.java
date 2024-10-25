package jisd.fl.coverage;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class CoverageAnalyzerTest {
    @Test
    void execTestMethodTest() throws IOException, InterruptedException {
        String testMethodName = "demo.SortTest#test1";
        CoverageAnalyzer ca = new CoverageAnalyzer();
        ca.execTestMethod(testMethodName);
    }

    @Test
    void analyzeCoverageForTestCaseTest() throws IOException, InterruptedException {
        String testMethodName = "demo.SortTest#test1";
        CoverageAnalyzer ca = new CoverageAnalyzer();
        CoverageForTestCase coverages = ca.analyzeCoverageForTestCase(testMethodName);
        coverages.printCoverages(Granularity.METHOD);
    }

    @Test
    void analyzeTest() throws IOException, InterruptedException {
        CoverageAnalyzer ca = new CoverageAnalyzer();
        CoverageForTestSuite cov = ca.analyze("demo.SortTest");
        cov.printCoverages(Granularity.LINE);
    }
}