package jisd.fl.sbfl;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.FaultFinder;
import jisd.fl.sbfl.coverage.CoverageAnalyzer;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FaultFinderTest {
    FaultFinder faultFinder;

    @BeforeEach
    void init(){
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.setTargetSrcDir(testProjectDir.resolve("src/main/java").toString());
        PropertyLoader.setTestSrcDir(testProjectDir.resolve("src/test/java").toString());

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
