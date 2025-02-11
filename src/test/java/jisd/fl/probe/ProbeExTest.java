package jisd.fl.probe;

import experiment.coverage.CoverageGenerator;
import experiment.defect4j.Defects4jUtil;
import experiment.sbfl.RankingEvaluator;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.sbfl.FaultFinder;
import jisd.fl.sbfl.Formula;
import jisd.fl.util.FileUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

class ProbeExTest {
    String project = "Math";
    int bugId = 2;

    String testClassName = "org.apache.commons.math3.distribution.HypergeometricDistributionTest";
    String shortTestMethodName = "testMath1021";

    String testMethodName = testClassName + "#" + shortTestMethodName + "()";

    String variableName = "tmp2";
    boolean isPrimitive = true;
    boolean isField = false;
    boolean isArray = false;
    int arrayNth = -1;
    String actual = "-50";

    String locate = "org.apache.commons.math3.distribution.AbstractIntegerDistribution#inverseCumulativeProbability(double)";


    String dir = "src/main/resources/probeExResult/Math/" + project + bugId + "_buggy";
    String fileName = testMethodName + "_" + variableName;

    VariableInfo probeVariable = new VariableInfo(
            locate,
            variableName,
            isPrimitive,
            isField,
            isArray,
            arrayNth,
            actual,
            null
    );

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testMethodName,
            actual,
            probeVariable);


    @Test
    void runTest() {
        Defects4jUtil.changeTargetVersion(project, bugId);
        ProbeEx prbEx = new ProbeEx(fai);
        ProbeExResult pr = prbEx.run(3000);
        pr.print();

       // pr.generateJson(dir, fileName);
    }

//    @Test
//    void testForPaper() {
//        CoverageAnalyzer ca = new CoverageAnalyzer();
//        CoverageCollection cov = ca.analyzeAll(testClassName);
//        FaultFinder ff = new FaultFinder(cov, Formula.OCHIAI);
//        ff.printRanking(10);
//
//
//        VariableInfo probeVariable = new VariableInfo(
//                "org.apache.commons.math.optimization.RealPointValuePair", "point",
//                true, true, true, 1, "-1.0", null
//        );
//
//        FailedAssertInfo fai = new FailedAssertEqualInfo(
//                "org.apache.commons.math.optimization.linear.SimplexSolverTest#estMath713NegativeVariable()",
//                "-1.0", probeVariable);
//
//        ff.probeEx(fai, 3000);
//
//    }


}


