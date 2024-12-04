package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

class FaultFinderTest {
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String testMethodName = "testSingleVariableAndConstraint";
    String variableName = "solution";
    String typeName = "org.apache.commons.math.optimization.RealPointValuePair";
    String fieldName = "point";
    String actual = "0.0";
    CoverageAnalyzer ca = new CoverageAnalyzer();

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testClassName,
            testMethodName,
            variableName,
            typeName,
            fieldName,
            actual,
            0);

    FaultFinderTest() throws IOException {
    }


    @Test
    void printFLResultsTest() throws IOException, InterruptedException, ExecutionException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        SbflResult result = ff.getFLResults();
        result.printFLResults();
    }

    @Test
    void removeTest() throws IOException, InterruptedException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.remove(1);
    }

    @Test
    void suspTest() throws IOException, InterruptedException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.susp(2);
    }

    @Test
    void probeTest() throws IOException, InterruptedException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.probe(fai);
    }

    @Test
    void debugTest() throws IOException, InterruptedException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.probe(fai);
        ff.susp(1);
        ff.getFLResults().printFLResults();
    }


}