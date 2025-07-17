package jisd.fl.sbfl;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.coverage.CoverageAnalyzer;
import jisd.fl.coverage.CoverageCollection;
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

public class FaultFinderForStmtTest {
    @BeforeEach
    void initProperty() {
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
    }

    @Test
    public void testPrintRanking() {
        String testClassName = "org.sample.coverage.ConditionalTest";
        // カバレッジを分析
        CoverageAnalyzer ca = new CoverageAnalyzer();
        CoverageCollection coverageCollection = ca.analyzeAll(testClassName);

        // FaultFinderForStmtのインスタンス化
        FaultFinderForStmt faultFinder = new FaultFinderForStmt(coverageCollection, Formula.OCHIAI);

        // printRankingのテスト
        assertDoesNotThrow(() -> faultFinder.printRanking(10));
    }
}
