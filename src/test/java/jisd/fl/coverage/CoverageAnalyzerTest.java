package jisd.fl.coverage;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CoverageAnalyzerTest {
    String testMethodName = "demo.SortTest#test1";
    CoverageAnalyzer ca = new CoverageAnalyzer("/Users/ezaki/IdeaProjects/proj4test/build/classes/java/main");

    @Test
    void execTestMethodTest() throws IOException, InterruptedException {
        ca.execTestMethod(testMethodName);
    }

    @Test
    void analyzeLineCoverageTest() throws IOException, InterruptedException {
        CoverageForTestCase<LineCoverage> coverges = ca.analyzeLineCoverage(testMethodName);
        coverges.printCoverages();
    }
}