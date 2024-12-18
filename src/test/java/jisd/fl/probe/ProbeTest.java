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
        String variableType = "double[]";
        String actual = "0.0";

        VariableInfo probeVariable = new VariableInfo(
                locateClass,
                variableName,
                variableType,
                true,
                0,
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
        Set<String> siblingMethods = result.getSiblingMethods();
        System.out.println("sibling methods");
        for(String sibling : siblingMethods){
            System.out.println(sibling);
        }
    }

    @Test
    void probeTest() {
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";
        String locateMethod = "org.apache.commons.math.optimization.linear.SimplexTableau#getSolution()";
        String variableName = "coefficients";
        String variableType = "double[]";
        String actual = "0.0";

        VariableInfo probeVariable = new VariableInfo(
                locateMethod,
                variableName,
                variableType,
                false,
                0,
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
    }

     @Test
    void getCalleeMethodsOfMethod() {
        String testMethod = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";
        String locateMethod = "org.apache.commons.math.optimization.linear.SimplexTableau#getSolution()";
        String variableName = "coefficients";
        String variableType = "double[]";
        String actual = "0.0";

         VariableInfo field = new VariableInfo(
                 locateMethod,
                 variableName,
                 variableType,
                 false,
                 0,
                 null
         );

         FailedAssertInfo fai2 = new FailedAssertEqualInfo(
                 testMethod,
                 actual,
                 field);

         Probe prb = new Probe(fai2);
         Set<String> callee = prb.getCalleeMethods(testMethod, locateMethod);

         for(String c: callee){
             System.out.println(c);
         }
    }
}