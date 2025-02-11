package jisd.fl.sbfl;

import experiment.coverage.CoverageGenerator;
import experiment.defect4j.Defects4jUtil;
import experiment.sbfl.RankingEvaluator;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

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
    void probeExTest() {
        String project = "Math";
        int bugId = 1;

        Defects4jUtil.changeTargetVersion(project, bugId);
        CoverageAnalyzer ca = new CoverageAnalyzer(outputDir(project, bugId));
        CoverageCollection cov = ca.analyzeAll(testClassName);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);
        ff.probeEx(fai, 3000);
    }

    @Test
    void loadTest() throws NoSuchFileException {
        String project = "Math";
        int bugId = 47;
        boolean probe = true;

        CoverageCollection cov = CoverageGenerator.loadAll(project, bugId);
        FaultFinder ff = new FaultFinder(cov, Granularity.METHOD, Formula.OCHIAI);

        ff.setHighlightMethods(RankingEvaluator.loadBugMethods(project, bugId));
        ff.getFLResults().printFLResults(50, cov);
        System.out.println();
        if(probe) {
            RankingEvaluator re = new RankingEvaluator(ff);
            re.loadAndApplyProbeEx(project, bugId);
        }
        ff.getFLResults().printFLResults(50, cov);
        //System.out.println(ff.getFLResults().getNumOfTie(ff.getFLResults().getElementAtPlace(49)));
    }

    @Test
    void printFLResultsTest() {
        String project = "Math";
        int bugId = 42;
        CoverageCollection cov = CoverageGenerator.loadAll(project, bugId);
        FaultFinder ff = new FaultFinder(cov, Formula.OCHIAI);



        ff.printRanking(20);
    }


}