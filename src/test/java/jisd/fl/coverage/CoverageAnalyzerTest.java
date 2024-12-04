package jisd.fl.coverage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

class CoverageAnalyzerTest {
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    CoverageAnalyzer ca = new CoverageAnalyzer();

    CoverageAnalyzerTest() throws IOException, InterruptedException {
    }

    @Test
    void analyzeTestMETHOD() throws IOException, InterruptedException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        cov.printCoverages(Granularity.METHOD);
    }
}