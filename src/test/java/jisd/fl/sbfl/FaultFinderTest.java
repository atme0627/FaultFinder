package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

class FaultFinderTest {
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    CoverageAnalyzer ca = new CoverageAnalyzer();

    FaultFinderTest() throws IOException {
    }

    @Test
    void printFLResultsTest() throws IOException, InterruptedException, ExecutionException {
        CoverageCollection cov = ca.analyze(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.printFLResults(100);
    }
}