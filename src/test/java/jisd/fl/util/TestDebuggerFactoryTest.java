package jisd.fl.util;

import jisd.debug.Debugger;
import org.junit.jupiter.api.Test;

class TestDebuggerFactoryTest {
    TestDebuggerFactory td = new TestDebuggerFactory();

    @Test
    void createTest() {
        String testClassName = "org.apache.commons.math.analysis.integration.RombergIntegratorTest";
        String testMethodName = "testSinFunction";

        Debugger dbg = TestUtil.testDebuggerFactory(testClassName, testMethodName);
        dbg.setMain(testClassName);

        dbg.run(2000);
    }
}