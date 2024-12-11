package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import org.junit.jupiter.api.Test;

import java.util.Set;

class ProbeTest {
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
    String variableName = "solution";
    String variableType = "org.apache.commons.math.optimization.RealPointValuePair";
    String fieldName = "point";
    String fieldType = "double[]";
    String actual = "0.0";

    VariableInfo field = new VariableInfo(
            variableType,
            fieldName,
            fieldType,
            true,
            0,
            null
            );

    VariableInfo probeVariable = new VariableInfo(
            testClassName,
            variableName,
            variableType,
            false,
            -1,
            field
    );

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testClassName,
            testMethodName,
            actual,
            probeVariable);

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
}