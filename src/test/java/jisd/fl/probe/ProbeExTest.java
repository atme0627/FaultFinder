package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import org.junit.jupiter.api.Test;

class ProbeExTest {
    String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";
    String locateClass = "org.apache.commons.math.optimization.RealPointValuePair";
    String variableName = "point";
    String variableType = "double[]";
    String actual = "0.0";

    VariableInfo probeVariable = new VariableInfo(
            locateClass,
            variableName,
            false,
            true,
            true,
            0,
            actual,
            null
    );

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testMethodName,
            actual,
            probeVariable);


    @Test
    void runTest() {
        ProbeEx prbEx = new ProbeEx(fai);
        ProbeExResult pr = prbEx.run(3000);
        pr.print();
    }

    @Test
    void debug(){
        String locate = "org.apache.commons.math.optimization.linear.AbstractLinearOptimizer";
        String variableName = "restrictToNonNegative";
        String actual = "false";

        VariableInfo probeVariable = new VariableInfo(
                locate,
                variableName,
                true,
                true,
                false,
                -1,
                actual,
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo(
                testMethodName,
                actual,
                probeVariable);

    ProbeEx prbEx = new ProbeEx(fai);
    ProbeExResult pr = prbEx.run(4000);
    }
}


