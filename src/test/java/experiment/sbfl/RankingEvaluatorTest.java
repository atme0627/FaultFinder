package experiment.sbfl;

import experiment.coverage.CoverageGenerator;
import experiment.defect4j.Defects4jUtil;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.ProbeExResult;
import jisd.fl.sbfl.Formula;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Set;


class RankingEvaluatorTest {

    @Test
    void loadProbeExTest() throws NoSuchFileException {
        String project = "Math";
        int bugId = 2;

        List<ProbeExResult> results = RankingEvaluator.loadProbeEx(project, bugId);
        for(ProbeExResult result : results) {
            result.print();
        }
    }


    @Test
    void calcMWETest() throws NoSuchFileException {
        String project = "Math";
        int bugId = 2;
        Defects4jUtil.changeTargetVersion(project, bugId);
        Set<String> bugMethods = RankingEvaluator.loadBugMethods(project, bugId);


        CoverageCollection cov = CoverageGenerator.loadAll(project, bugId);
        RankingEvaluator re = new RankingEvaluator(cov, Granularity.METHOD, Formula.OCHIAI);
        re.ff.setHighlightMethods(bugMethods);

//        double sbfl = 100000000;
//        for(String bugMethod : re.loadBugMethods(project, bugId)) {
//            System.out.println(bugMethod + " " + re.ff.getFLResults().getRankOfElement(bugMethod));
//            sbfl = Math.min(sbfl, re.ff.getFLResults().getRankOfElement(bugMethod));
//        }
//        System.out.println("SBFL: " + sbfl);

        double mweBefore = re.calcMWE(bugMethods);

        re = new RankingEvaluator(cov, Granularity.METHOD, Formula.OCHIAI);
        re.ff.setHighlightMethods(bugMethods);
        re.loadAndApplyProbeEx(project, bugId);
        double mweAfter = re.calcMWE(bugMethods);

        System.out.println("[  MWE  ] " + mweBefore + "--> " + mweAfter);
    }

    @Test
    void countNotModified(){
        int count = 0;
        String project = "Math";
        int numOfBugs = 106;

        for(int i = 1; i <= numOfBugs; i++){
            Set<String> bugMethods = RankingEvaluator.loadBugMethods(project, i);
            if(bugMethods.size() == 1){
                for(String b : bugMethods){
                    if(b.equals("Not modified.")){
                        System.out.println("id: " + i);
                        count++;
                    }

                }
            }
        }

        System.out.println("count: " + count);
    }

    @Test
    void countCannotProbe(){
        int count = 0;
        String project = "Math";
        int numOfBugs = 106;

        for(int i = 1; i <= numOfBugs; i++){
            try {
                RankingEvaluator.loadProbeEx(project, i);
            } catch (NoSuchFileException e) {
                System.out.println("id: " + i);
                count++;
            }
        }
        System.out.println("count: " + count);
    }

    @Test
    void testForPaper(){
        String project = "Math";
        int bugId = 1;
        Defects4jUtil.changeTargetVersion(project, bugId);
        Set<String> bugMethods = RankingEvaluator.loadBugMethods(project, bugId);

        CoverageCollection cov = CoverageGenerator.loadAll(project, bugId);
        RankingEvaluator re = new RankingEvaluator(cov, Granularity.METHOD, Formula.OCHIAI);
        re.ff.setHighlightMethods(bugMethods);
        re.ff.getFLResults().printFLResults(20);
    }
}

