package jisd.fl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestRunnerTest {

    @Test
    void compileTestClassTest() {
        String testClassName = "org.apache.commons.math.analysis.integration.RombergIntegratorTest";
        TestRunner.compileTestClass(testClassName);
    }
}