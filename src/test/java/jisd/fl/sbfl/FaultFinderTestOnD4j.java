package jisd.fl.sbfl;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.analyze.MethodElementName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class FaultFinderTestOnD4j {
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
    String variableType = "org.apache.commons.math.optimization.RealPointValuePair";
    String fieldName = "point";
    String actual = "0.0";
    String rootDir = "src/main/resources/coverages";

    SuspiciousVariable field = new SuspiciousVariable(
            new MethodElementName(testMethodName),
            variableType,
            fieldName,
            actual,
            false,
            true
    );

    private String outputDir(String project, int bugId) {
        return rootDir + "/" + project + "/" + project + bugId + "_buggy/" + testClassName;
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
    void probeExTest() {
        String project = "Math";
        int bugId = 87;

        Defects4jUtil.changeTargetVersion(project, bugId);
        CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(project, bugId));
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.probeEx(field, 3000);
    }
}