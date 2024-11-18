package jisd.fl.util;

import jisd.debug.Debugger;
import org.junit.jupiter.api.Test;

class TestDebuggerFactoryTest {
    TestDebuggerFactory td = new TestDebuggerFactory();

    @Test
    void createTest() {
        String testClassName = "demo.SampleTest";
        String testMethodName = "sample2";

        Debugger dbg = td.create(testClassName, testMethodName);
        dbg.run(2000);
    }
}