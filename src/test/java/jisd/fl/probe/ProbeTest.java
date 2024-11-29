package jisd.fl.probe;

import com.sun.jdi.VMDisconnectedException;
import jisd.debug.Debugger;
import jisd.debug.Point;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProbeTest {
    String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
    String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String testMethodName = "testSingleVariableAndConstraint";
    String variableName = "solution";
    String typeName = "org.apache.commons.math.optimization.RealPointValuePair";
    String fieldName = "point";
    String actual = "0.0";

//    String testClassName = "org.apache.commons.math.analysis.integration.RombergIntegratorTest";
//    String testMethodName = "testSinFunction";
//    String variableName = "result";
//    String typeName = "double";
//    String fieldName = "primitive";
//    String actual = "-0.5000000001514787";

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testClassName,
            testMethodName,
            variableName,
            typeName,
            fieldName,
            actual,
            0);

    Debugger dbg = TestUtil.testDebuggerFactory(testClassName, testMethodName);

    @Test
    void runTest(){
        Probe prb = new Probe(dbg, fai);
        ProbeResult result = prb.run(3000);
        System.out.println(result.getProbeMethod());
    }
}