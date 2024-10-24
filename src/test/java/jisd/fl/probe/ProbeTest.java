package jisd.fl.probe;

import jisd.debug.Debugger;
import jisd.fl.util.TestDebuggerFactory;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;

class ProbeTest {
    TestDebuggerFactory factory = new TestDebuggerFactory();
    String srcDir = "src/test/java";
    String binDir = "build/classes/java/test";
    String testClassName = "src4test.SampleTest";
    String testMethodName = "sample2()";
    int assertLineNum = 33;
    String actual = "8";
    AssertExtractor ae = new AssertExtractor(srcDir, binDir);
    FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, actual);

    String testJavaFilePath = "src/test/java/src4test/SampleTest.java";
    String mainBinDir = "build/classes/java/main";
    String junitStandaloneDir = "./locallib";
    Debugger dbg = factory.create(testClassName, testMethodName, testJavaFilePath, mainBinDir, junitStandaloneDir);
    Probe prb = new Probe(dbg, fai);

    @Test
    void getLineWithVarTest() {
        ArrayList<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
    }

    @Test
    void runTest() {
        ArrayList<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
        prb.run();
    }
}