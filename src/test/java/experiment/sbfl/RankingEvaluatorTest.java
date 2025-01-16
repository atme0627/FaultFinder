package experiment.sbfl;

import experiment.coverage.CoverageGenerator;
import experiment.defect4j.Defects4jUtil;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.ProbeExResult;
import jisd.fl.sbfl.Formula;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;


class RankingEvaluatorTest {

    @Test
    void loadProbeExTest() {
        String project = "Math";
        int bugId = 1;

        List<ProbeExResult> results = RankingEvaluator.loadProbeEx(project, bugId);
        for(ProbeExResult result : results) {
            result.print();
        }
    }

    @Test
    void calcMWETest(){
        String project = "Math";
        int bugId = 6;
        Defects4jUtil.changeTargetVersion(project, bugId);
        Set<String> bugMethods = RankingEvaluator.loadBugMethods(project, bugId);

        CoverageAnalyzer ca = new CoverageAnalyzer();
        CoverageCollection cov = CoverageGenerator.loadAll(project, bugId);
        RankingEvaluator re = new RankingEvaluator(cov, Granularity.METHOD, Formula.OCHIAI);
        re.ff.setHighlightMethods(bugMethods);
        double mwe = re.calcMWE(bugMethods);
    }
}