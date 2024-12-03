package jisd.fl.coverage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

class CoverageAnalyzerTest {
    String testClassName = "org.apache.commons.math.analysis.integration.RombergIntegratorTest";
    CoverageAnalyzer ca = new CoverageAnalyzer();

    CoverageAnalyzerTest() throws IOException, InterruptedException {
    }

    @Test
    void analyzeAllTestLINE() throws IOException, InterruptedException, ExecutionException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        cov.printCoverages(Granularity.LINE);
    }

    @Test
    void analyzeTestMETHOD() throws IOException, InterruptedException, ExecutionException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        cov.printCoverages(Granularity.METHOD);
    }

    @Test
    void analyzeAllTestCLASS() throws IOException, InterruptedException, ExecutionException {
        CoverageCollection cov = ca.analyzeAll(testClassName);
        cov.printCoverages(Granularity.CLASS);
    }
}