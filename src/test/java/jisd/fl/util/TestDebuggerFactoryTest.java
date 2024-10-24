package jisd.fl.util;

import jisd.debug.Debugger;
import org.junit.jupiter.api.Test;

class TestDebuggerFactoryTest {
    TestDebuggerFactory td = new TestDebuggerFactory();

    @Test
    void createTest() {
        String testClassName = "src4test.SampleTest";
        String testMethodName = "sample2";
        String javaFilePath = "./src/test/java/src4test/SampleTest.java";
        String binDir = "./build/classes/java/main/";
        String junitStandaloneDir = "./locallib";

        Debugger dbg = td.create(testClassName, testMethodName, javaFilePath, binDir, junitStandaloneDir);
        dbg.run(1000);
    }

}