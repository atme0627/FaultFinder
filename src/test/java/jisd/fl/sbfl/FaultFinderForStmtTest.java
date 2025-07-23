package jisd.fl.sbfl;

import jisd.fl.FaultFinder;
import jisd.fl.FaultFinderForStmt;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.sbfl.coverage.CoverageAnalyzer;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FaultFinderForStmtTest {
    FaultFinderForStmt faultFinder;

    @BeforeEach
    void init(){
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");

        String testClassName = "org.sample.CalcTest";
        // カバレッジを分析
        CoverageAnalyzer ca = new CoverageAnalyzer();
        CoverageCollection coverageCollection = ca.analyzeAll(testClassName);
        // FaultFinderForStmtのインスタンス化
        faultFinder = new FaultFinderForStmt(coverageCollection, Formula.OCHIAI);
    }

    @Test
    public void printRankingTest() {
        assertDoesNotThrow(() -> faultFinder.printRanking());
    }

    @Test
    public void removeTest(){
        faultFinder.remove(3);
    }

    @Test
    public void suspTest(){
        faultFinder.susp(2);
    }

    @Test
    public void probeTest(){
        faultFinder.printRanking();
        File j = new File("/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/resources/json/SuspiciousExpression/CalcTest.json");
        SuspiciousExpression suspExpr = SuspiciousExpression.loadFromJson(j);
        faultFinder.probe(suspExpr);

    }
}
