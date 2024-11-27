package jisd.fl.util;

import org.junit.jupiter.api.Test;

class TestRunnerTest {

    @Test
    void compileTestClassTest() {
        String testClassName = "org.apache.commons.math.analysis.integration.RombergIntegratorTest";
        TestUtil.compileTestClass(testClassName);
    }
}