package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProbeExTest {
    @Nested
    class d4jTest {
        String project = "Math";
        int bugId = 2;

        String testClassName = "org.apache.commons.math3.distribution.HypergeometricDistributionTest";
        String shortTestMethodName = "testMath1021";
        String testMethodName = testClassName + "#" + shortTestMethodName + "()";

        String variableName = "tmp2";
        boolean isPrimitive = true;
        boolean isField = false;
        boolean isArray = false;
        int arrayNth = -1;
        String actual = "-50";
        String locate = "org.apache.commons.math3.distribution.AbstractIntegerDistribution#inverseCumulativeProbability(double)";

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
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/main/java");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/test/java");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/test-classes");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/classes");
        }

        @Test
        void runTest() {
            ProbeEx prbEx = new ProbeEx(fai);
            ProbeExResult pr = prbEx.run(3000);
            pr.print();
        }
    }

    @Nested
    class simpleCaseTest {
        String testClassName = "sample.SampleTest";
        String shortTestMethodName = "case2";
        String testMethodName = testClassName + "#" + shortTestMethodName + "()";

        String variableName = "actual";
        boolean isPrimitive = true;
        boolean isField = false;
        boolean isArray = true;
        int arrayNth = 1;
        String actual = "3";
        String locate = testMethodName;

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
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/src/main");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/src/test");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/build/main");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/build/test");
        }

        @Test
        void runTest() {
            ProbeEx prbEx = new ProbeEx(fai);
            ProbeExResult pr = prbEx.run(2000);
            pr.print();
        }
    }
}


