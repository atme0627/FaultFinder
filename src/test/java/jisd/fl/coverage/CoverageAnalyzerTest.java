package jisd.fl.coverage;

import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

class CoverageAnalyzerTest {
    String testClassName = "org.apache.commons.math3.distribution.HypergeometricDistributionTest";;
    CoverageAnalyzer ca = new CoverageAnalyzer();

    @BeforeEach
    void initProperty() {
        PropertyLoader.setProperty("targetSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/main/java");
        PropertyLoader.setProperty("testSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/test/java");
        PropertyLoader.setProperty("testBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/test-classes");
        PropertyLoader.setProperty("targetBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/classes");
    }

    @Test
    void analyzeTestCLASS() throws Exception {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        cov.printCoverages(Granularity.CLASS);
    }

    @Test
    void analyzeTestMETHOD() throws Exception {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        cov.printCoverages(Granularity.METHOD);
    }

    @Test
    void analyzeTestLINE() throws Exception {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        cov.printCoverages(Granularity.LINE);
    }
}