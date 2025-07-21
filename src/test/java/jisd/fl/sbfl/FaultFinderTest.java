package jisd.fl.sbfl;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        CoverageCollection coverageCollection = ca.analyzeAll(testClassName);
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
