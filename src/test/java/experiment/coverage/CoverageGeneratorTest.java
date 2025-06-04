package experiment.coverage;

import experiment.defect4j.Defects4jUtil;
import experiment.sbfl.RankingEvaluator;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.probe.info.ProbeExResult;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class CoverageGeneratorTest {
    @BeforeEach

    @Test
    void genTest(){
        String project = "Math";
        int bugId = 6;

        Defects4jUtil.changeTargetVersion(project, bugId);
        Defects4jUtil.CompileBuggySrc(project, bugId);
        List<String> testMethods = Defects4jUtil.getFailedTestMethods(project, bugId);

        Set<String> executed = new HashSet<>();
        for(String testMethodName : testMethods) {
            String testClassName = testMethodName.split("#")[0];

            if(executed.contains(testClassName)) continue;
            executed.add(testClassName);

            CoverageGenerator cg = new CoverageGenerator(testClassName, project, bugId);
            cg.generate();
        }
    }

    @Test
    void getTestNums() throws NoSuchFileException {
        String project = "Math";
        int bugId = 5;

        Defects4jUtil.changeTargetVersion(project, bugId);
        List<String> failed = Defects4jUtil.getFailedTestMethods(project, bugId);

        Set<String> testClass = new HashSet<>();

        System.out.println("[FAILED]");
        for(String m : failed){
            testClass.add(m.split("#")[0]);
            System.out.println(m);
        }

        System.out.println();
        System.out.println("[TEST NUMS]");
        for(String t : testClass){
            System.out.println(t);
            Set<String> testMethods = TestUtil.getTestMethods(t);
            System.out.println(testMethods.size());
        }

    }

    @Test
    void loadTest() {
        String project = "Math";
        int bugId = 46;
        CoverageCollection cov = CoverageGenerator.loadAll(project, bugId);
        cov.printCoverages(Granularity.METHOD);

    }

    @Test
    void countFindButDecrease() throws NoSuchFileException {
        String project = "Math";
        Set<Integer> decreasedBugId =
                new HashSet<>(List.of(
                2, 16, 28, 29, 33,
                41, 43, 46, 63, 67,
                76, 80, 84, 85, 95,
                96));

        Set<Integer> stay1BugId =
                new HashSet<>(List.of(
                        3, 15, 27, 36, 53, 55, 91, 94));

        Set<Integer> notChangedBugId =
        new HashSet<>(List.of(
                5, 6, 9, 10, 11,
                17, 18, 22, 37, 39,
                40, 47, 49, 57, 60,
                65, 70, 72, 74, 75,
                82, 98, 103));

        Set<Integer> increasedBugId =
                new HashSet<>(List.of(
                        1, 4, 23, 24, 30,
                        31, 42, 44, 51, 52,
                        54, 56, 59, 61, 62,
                        66, 69, 71, 77, 78,
                        79, 81, 83, 87, 88,
                        92, 93, 97, 100, 101,
                        102, 105));

        Set<Integer> found = new HashSet<>();


        Set<Integer> target = notChangedBugId;
        for(int id : target){
            List<ProbeExResult> results = RankingEvaluator.loadProbeEx(project, id);
            Set<String> bugMethods = RankingEvaluator.loadBugMethods(project, id);

            for(String bug : bugMethods){
                boolean f = false;
                for(ProbeExResult r : results){
                    if(r.markingMethods().contains(bug)) {
                        found.add(id);
                        f = true;
                        break;
                    }
                }
                if(f) break;
            }
        }

        target.removeAll(found);
        System.out.println(Arrays.toString((target.stream().sorted().toArray())));
    }
}