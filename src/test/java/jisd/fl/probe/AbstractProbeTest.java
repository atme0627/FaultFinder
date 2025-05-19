package jisd.fl.probe;

import jisd.debug.EnhancedDebugger;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.CodeElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AbstractProbeTest {
    String testClassName = "sample.MethodCallTest";
    String shortTestMethodName = "methodCall1";
    String testMethodName = testClassName + "#" + shortTestMethodName + "()";

    String variableName = "result";
    boolean isPrimitive = true;
    boolean isField = false;
    boolean isArray = false;
    int arrayNth = -1;
    String actual = "11";
    String locate = "sample.MethodCall#methodCalling(int, int)";

    VariableInfo probeVariable = new VariableInfo(
            locate,
            variableName,
            isPrimitive,
            isField,
            isArray,
            arrayNth,
            actual,
            null
    );

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testMethodName,
            actual,
            probeVariable);

    @BeforeEach
    void initProperty() {
        PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
        PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
        PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
        PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");

        TestUtil.compileForDebug(new CodeElementName("sample.MethodCallTest"));
    }

    @Test
    void getCalleeMethodsTest() {
        String main = TestUtil.getJVMMain(new CodeElementName(fai.getTestMethodName()));
        String options = TestUtil.getJVMOption();
        EnhancedDebugger dbg = new EnhancedDebugger(main, options);
        Set<String> result = dbg.getCalleeMethods(new CodeElementName(locate).getFullyQualifiedClassName(), 9);
        result.forEach(System.out::println);
    }

    @Test
    void getReturnLineOfCalleeMethodTest() {
        String main = TestUtil.getJVMMain(new CodeElementName(fai.getTestMethodName()));
        String options = TestUtil.getJVMOption();
        EnhancedDebugger dbg = new EnhancedDebugger(main, options);
        Map<String, Integer> result =
                dbg.getReturnLineOfCalleeMethod(new CodeElementName(locate).getFullyQualifiedClassName(), 9);
        result.forEach((name, line) -> System.out.println("method: " + name + " line: " + line));
    }
}