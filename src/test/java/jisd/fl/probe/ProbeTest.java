package jisd.fl.probe;

import com.github.javaparser.ast.observer.PropagatingAstObserver;
import jisd.debug.Debugger;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestDebuggerFactory;
import jisd.info.StaticInfoFactory;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbeTest {


    @Test
    void getLineWithVarTest() {
        TestDebuggerFactory factory = new TestDebuggerFactory();
        String srcDir = PropertyLoader.getProperty("testSrcDir");
        String binDir = PropertyLoader.getProperty("testBinDir");
        String testClassName = "demo.SampleTest";
        String testMethodName = "sample2()";
        int assertLineNum = 33;
        int nthArg = 1;
        String actual = "8";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, nthArg, actual);

        Debugger dbg = factory.create(testClassName, testMethodName);
        Probe prb = new Probe(dbg, fai);
        ArrayList<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
    }

    @Test
    void runTest() {
        TestDebuggerFactory factory = new TestDebuggerFactory();
        String srcDir = PropertyLoader.getProperty("testSrcDir");
        String binDir = PropertyLoader.getProperty("testBinDir");
        String testClassName = "demo.SampleTest";
        String testMethodName = "sample2()";
        int assertLineNum = 33;
        int nthArg = 1;
        String actual = "8";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, nthArg, actual);

        Debugger dbg = factory.create(testClassName, testMethodName);
        Probe prb = new Probe(dbg, fai);
        ArrayList<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
        int result = prb.run();
        assertEquals(24, result);
    }

    @Test
    void getLineWithVarD4jTest() {
        TestDebuggerFactory factory = new TestDebuggerFactory();
        String srcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String binDir = PropertyLoader.getProperty("d4jTestBinDir");
        String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
        String testMethodName = "testSingleVariableAndConstraint()";
        int assertLineNum = 75;
        int nthArg = 2;
        String actual = "0.0";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, nthArg, actual);

        Debugger dbg = factory.create(testClassName, testMethodName);
        Probe prb = new Probe(dbg, fai);
        ArrayList<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
    }

    @Test
    void runD4jTest() {
        TestDebuggerFactory factory = new TestDebuggerFactory();
        String srcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String binDir = PropertyLoader.getProperty("d4jTestBinDir");
        String testClassName = "org.apache.commons.math.analysis.integration.RombergIntegratorTest";
        String testMethodName = "testSinFunction";
        int assertLineNum = 53;
        int nthArg = 2;
        String actual = "-0.5";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName + "()", assertLineNum, nthArg, actual);

        Debugger dbg = factory.create(testClassName, testMethodName);
        Probe prb = new Probe(dbg, fai);
        ArrayList<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
        int result = prb.run();
        assertEquals(50, result);
    }
}