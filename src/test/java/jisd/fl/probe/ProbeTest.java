package jisd.fl.probe;

import com.github.javaparser.ast.observer.PropagatingAstObserver;
import jisd.debug.Debugger;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestDebuggerFactory;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbeTest {
    TestDebuggerFactory factory = new TestDebuggerFactory();
    String srcDir = PropertyLoader.getProperty("testSrcDir");
    String binDir = PropertyLoader.getProperty("testBinDir");
    String testClassName = "demo.SampleTest";
    String testMethodName = "sample2()";
    int assertLineNum = 33;
    String actual = "8";
    AssertExtractor ae = new AssertExtractor(srcDir, binDir);
    FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, actual);

    Debugger dbg = factory.create(testClassName, testMethodName);
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
        int result = prb.run();
        assertEquals(24, result);
    }
}