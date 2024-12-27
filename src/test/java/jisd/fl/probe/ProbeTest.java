package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import org.junit.jupiter.api.Test;

import java.util.*;

class ProbeTest {
    @Test
    void runTest(){
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";
        String locateClass = "org.apache.commons.math.optimization.RealPointValuePair";
        String variableName = "point";
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

        Probe prb = new Probe(fai);
        ProbeResult result = prb.run(3000);
        System.out.println("probe method");
        System.out.println(result.getProbeMethod());
        System.out.println("caller method");
        System.out.println(result.getCallerMethod());
        Map<String, String> neighbor = result.getValuesInLine();
        System.out.println("neighbor variables");
        neighbor.forEach((variable, value) -> {
                System.out.println(variable + ": " + value);
            }
        );
    }

    @Test
    void runTest2() {
        String testMethod = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";
        String locateMethod = "org.apache.commons.math.optimization.linear.SimplexTableau#getSolution()";
        String variableName = "coefficients";
        String variableType = "double[]";
        String actual = "0.0";

        VariableInfo probeVariable = new VariableInfo(
                locateMethod,
                variableName,
                false,
                false,
                true,
                0,
                actual,
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo(
                testMethod,
                actual,
                probeVariable);

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

    //probe対象がメソッドの引数だった時
    @Test
    void runTest3() {
        String testMethod = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";
        String locateMethod = "org.apache.commons.math.optimization.RealPointValuePair#RealPointValuePair(double[], double)";
        String variableName = "point";
        String variableType = "double[]";
        String actual = "0.0";

        VariableInfo probeVariable = new VariableInfo(
                locateMethod,
                variableName,
                false,
                false,
                true,
                0,
                actual,
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo(
                testMethod,
                actual,
                probeVariable);

        Probe prb = new Probe(fai);
        ProbeResult result = prb.run(3000);

        System.out.println("probe method");
        System.out.println(result.getProbeMethod());
    }
}