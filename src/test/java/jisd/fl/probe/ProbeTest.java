package jisd.fl.probe;

import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.debug.Point;
import jisd.debug.value.ValueInfo;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

class ProbeTest {
    @Test
    void runTest(){
        String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
        String variableName = "solution";
        String variableType = "org.apache.commons.math.optimization.RealPointValuePair";
        String locateMethod = "";
        String fieldName = "point";
        String fieldType = "double[]";
        String actual = "0.0";

        VariableInfo field = new VariableInfo(
                variableType,
                locateMethod,
                fieldName,
                fieldType,
                true,
                0,
                null
                );

        VariableInfo probeVariable = new VariableInfo(
                testClassName,
                "",
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
        String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
        String locateClass = "org.apache.commons.math.optimization.linear.SimplexTableau";
        String locateMethod = "getSolution()";
        String variableName = "coefficients";
        String variableType = "double[]";
        String actual = "0.0";

        VariableInfo field = new VariableInfo(
                locateClass,
                locateMethod,
                variableName,
                variableType,
                false,
                0,
                null
        );

        VariableInfo probeVariable2 = new VariableInfo(
                testClassName,
                "",
                variableName,
                variableType,
                false,
                -1,
                field
        );

        FailedAssertInfo fai2 = new FailedAssertEqualInfo(
                testClassName,
                testMethodName,
                actual,
                probeVariable2);

        Probe prb = new Probe(fai2);
        ProbeResult result = prb.run(3000);
        System.out.println("probe method");
        System.out.println(result.getProbeMethod());
        System.out.println("caller method");
        System.out.println(result.getCallerMethod());
    }

    @Test
    void debug() {
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
        String locateClass = "org.apache.commons.math.optimization.linear.SimplexTableau";
        String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
        Debugger dbg = TestUtil.testDebuggerFactory(testMethodName);
        dbg.setMain(locateClass);
        dbg.setSrcDir(targetSrcDir);
        Optional<Point> p = dbg.stopAt(343);
        dbg.run(3000);
        dbg.locals();

        DebugResult dr = p.get().getResults("coefficients").get();
        ValueInfo vi = dr.lv();
        vi = vi.ch().get(0);
        System.out.println(vi.getName() + ": " + vi.getValue());
    }

     @Test
    void getCalleeMethodsOfMethod() {
        String testMethod = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
        String locateMethod = "org.apache.commons.math.optimization.linear.SimplexTableau#getSolution()";

        String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
        String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
        String locateClass = "org.apache.commons.math.optimization.linear.SimplexTableau";
        String variableName = "coefficients";
        String variableType = "double[]";
        String actual = "0.0";

         VariableInfo field = new VariableInfo(
                 locateClass,
                 locateMethod,
                 variableName,
                 variableType,
                 false,
                 0,
                 null
         );

         VariableInfo probeVariable = new VariableInfo(
                 testClassName,
                 "",
                 variableName,
                 variableType,
                 false,
                 -1,
                 field
         );

         FailedAssertInfo fai2 = new FailedAssertEqualInfo(
                 testClassName,
                 testMethodName,
                 actual,
                 probeVariable);

         Probe prb = new Probe(fai2);
         Set<String> callee = prb.getCalleeMethods(testMethod, locateMethod);

         for(String c: callee){
             System.out.println(c);
         }
    }
}