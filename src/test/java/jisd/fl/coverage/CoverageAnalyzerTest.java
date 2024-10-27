package jisd.fl.coverage;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class CoverageAnalyzerTest {
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    CoverageAnalyzer ca = new CoverageAnalyzer();
    //CoveragesForTestCase coverages = ca.analyzeCoveragesForTestCase(testMethodName);

    CoverageAnalyzerTest() throws IOException, InterruptedException {
    }

    @Test
    void execTestMethodTest() throws IOException, InterruptedException {
        //ca.execTestMethod(testMethodName);
    }

    @Test
    void analyzeLineCoverageForTestCaseTestLINE() {
        //coverages.printCoverages(Granularity.LINE);
    }

    @Test
    void analyzeLineCoverageForTestCaseTestMETHOD() {
        //coverages.printCoverages(Granularity.METHOD);
    }

    @Test
    void analyzeLineCoverageForTestCaseTestCLASS() {
        //coverages.printCoverages(Granularity.CLASS);
    }


    @Test
    void analyzeTestLINE() throws IOException, InterruptedException {
        CoveragesForTestSuite cov = ca.analyze(testClassName);
        cov.printCoverages(Granularity.LINE);
    }

    @Test
    void analyzeTestMETHOD() throws IOException, InterruptedException {
        CoveragesForTestSuite cov = ca.analyze(testClassName);
        cov.printCoverages(Granularity.METHOD);
    }

    @Test
    void analyzeTestCLASS() throws IOException, InterruptedException {
        CoveragesForTestSuite cov = ca.analyze(testClassName);
        cov.printCoverages(Granularity.CLASS);
    }
}