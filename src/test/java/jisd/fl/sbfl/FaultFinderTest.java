package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class FaultFinderTest {
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
    String variableType = "org.apache.commons.math.optimization.RealPointValuePair";
    String fieldName = "point";
    String actual = "0.0";
    String rootDir = "src/main/resources/coverages";

    VariableInfo field = new VariableInfo(
            variableType,
            fieldName,
            false,
            true,
            true,
            0,
            actual,
            null
    );

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testMethodName,
            actual,
            field);
    FaultFinderTest() throws IOException {
    }

    private String outputDir(String project, int bugId){
        return rootDir + "/" + project + "/" + project + bugId + "_buggy/" + testClassName;
    }

    @Test
    void printFLResultsTest() throws Exception {
        String project = "Math";
        int bugId = 87;

        CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(project, bugId));
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        SbflResult result = ff.getFLResults();
        result.printFLResults();
    }

    @Test
    void removeTest() throws Exception {
        String project = "Math";
        int bugId = 87;

        CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(project, bugId));
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.remove(1);
    }

    @Test
    void suspTest() throws Exception {
        String project = "Math";
        int bugId = 87;

        CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(project, bugId));
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.susp(2);
    }

    @Test
    void probeTest() throws Exception {
        String project = "Math";
        int bugId = 87;

        CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(project, bugId));
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.probe(fai, 3000);
    }

    @Test
    void probeExTest() throws Exception {
        String project = "Math";
        int bugId = 87;

        CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(project, bugId));
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.probeEx(fai, 3000);
    }

    @Test
    void debugTest() throws Exception {
        String project = "Math";
        int bugId = 87;

        CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(project, bugId));
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.probe(fai, 3000);
        ff.remove(1);
        ff.susp(1);
    }
}