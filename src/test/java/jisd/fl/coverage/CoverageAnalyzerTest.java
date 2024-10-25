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
    void analyzeLineCoverageForTestCaseTest() throws IOException, InterruptedException {
        String testMethodName = "demo.SortTest#test1";
        CoverageAnalyzer ca = new CoverageAnalyzer();
        CoverageForTestCase coverges = ca.analyzeLineCoverageForTestCase(testMethodName);
        coverges.printCoverages();
    }

    @Test
    void analyzeLineCoverageTest() throws IOException, InterruptedException {
        CoverageAnalyzer ca = new CoverageAnalyzer();
        CoverageForTestSuite cov = ca.analyzeLineCoverage("demo.SortTest");
        cov.printCoverages();
    }
}