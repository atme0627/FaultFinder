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

public class FaultFinderTest {
    FaultFinder faultFinder;

    @BeforeEach
    void init(){
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.ProjectConfig config = new PropertyLoader.ProjectConfig(
                testProjectDir,
                Path.of("src/main/java"),
                Path.of("src/test/java"),
                Path.of("build/classes/java/main"),
                Path.of("build/classes/java/test")
        );
        PropertyLoader.setProjectConfig(config);

        String testClassName = "org.sample.CalcTest";
        // カバレッジを分析
        CoverageAnalyzer ca = new CoverageAnalyzer();
        ca.analyze(testClassName);
        CoverageCollection coverageCollection = ca.result();
        // FaultFinderForStmtのインスタンス化
        faultFinder = new FaultFinder(coverageCollection, Granularity.LINE, Formula.OCHIAI);
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
}
