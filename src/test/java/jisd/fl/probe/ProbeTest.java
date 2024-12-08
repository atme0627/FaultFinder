package jisd.fl.probe;

import jisd.debug.Debugger;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Set;

class ProbeTest {
    String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
    String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
    String variableName = "solution";
    String typeName = "org.apache.commons.math.optimization.RealPointValuePair";
    String fieldName = "point";
    String actual = "0.0";

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testClassName,
            testMethodName,
            variableName,
            typeName,
            fieldName,
            actual,
            0);

    @Test
    void runTest(){
        Probe prb = new Probe(fai);
        ProbeResult result = prb.run(3000);
        System.out.println("probe method");
        System.out.println(result.getProbeMethod());
        System.out.println("caller method");
        System.out.println(result.getCallerMethod());
        Set<String> siblingMethods = result.getSiblingMethods();
        System.out.println("sibling methods");
        for(String sibling : siblingMethods){
            System.out.println(sibling);
        }
    }

    @Test
    void getCallStack(){
        Debugger dbg = TestUtil.testDebuggerFactory(testMethodName);
        PrintStream stdout = System.out;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);

        dbg.setMain(fai.getTypeName());
        dbg.setSrcDir(targetSrcDir, testSrcDir);
        dbg.stopAt(50);
        dbg.run(2000);
        System.setOut(ps);
        dbg.where();
        System.setOut(stdout);

        String[] stackTrace = bos.toString().split("\\n");
        String calleeMethod = stackTrace[2].substring(stackTrace[2].indexOf("]") + 1, stackTrace[2].indexOf("(")).trim();
        System.out.println("Callee method: " + calleeMethod);
    }
}