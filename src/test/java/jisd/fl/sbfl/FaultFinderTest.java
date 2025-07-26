package jisd.fl.sbfl;

import jisd.fl.FaultFinder;
import jisd.fl.sbfl.coverage.CoverageAnalyzer;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FaultFinderTest {
    FaultFinder faultFinder;

    @BeforeEach
    void init(){
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");

        String testClassName = "org.sample.coverage.ConditionalTest";
        // カバレッジを分析
        CoverageAnalyzer ca = new CoverageAnalyzer();
        ca.analyze(testClassName);
        CoverageCollection coverageCollection = ca.result();
        // FaultFinderForStmtのインスタンス化
        faultFinder = new FaultFinder(coverageCollection, Granularity.METHOD, Formula.OCHIAI);
    }

    @Test
    public void printRankingTest() {
        assertDoesNotThrow(() -> faultFinder.printRanking());
    }

    @Test
    public void removeTest(){
        faultFinder.printRanking();
        faultFinder.remove(1);
    }

    @Test
    public void suspTest(){
        faultFinder.printRanking();
        faultFinder.susp(2);
    }
}
