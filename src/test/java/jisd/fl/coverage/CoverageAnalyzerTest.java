package jisd.fl.coverage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

class CoverageAnalyzerTest {
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    CoverageAnalyzer ca = new CoverageAnalyzer();

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