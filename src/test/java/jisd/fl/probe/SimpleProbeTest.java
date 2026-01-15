package jisd.fl.probe;

import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.usecase.SimpleProbe;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SimpleProbeTest {
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

        SuspiciousVariable probeVariable = new SuspiciousVariable(
                new MethodElementName(testMethodName),
                locate,
                variableName,
                actual,
                isPrimitive,
                isField
        );

        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/main/java");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/test/java");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/test-classes");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/classes");
        }

        @Test
        void runTest() {
            SimpleProbe prbEx = new SimpleProbe(probeVariable);
            //ProbeExResult pr = prbEx.run(3000);
            //pr.print();
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

        SuspiciousVariable probeVariable = new SuspiciousVariable(
                new MethodElementName(testMethodName),
                locate,
                variableName,
                actual,
                isPrimitive,
                isField,
                arrayNth
        );

        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");
        }

        @Test
        void runTest() {
            SimpleProbe prbEx = new SimpleProbe(probeVariable);
            //ProbeExResult pr = prbEx.run(2000);
            //pr.print();
        }
    }

    @Nested
    class methodCallTest {
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

        SuspiciousVariable probeVariable = new SuspiciousVariable(
                new MethodElementName(testMethodName),
                locate,
                variableName,
                actual,
                isPrimitive,
                isField
        );

        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");

            TestUtil.compileForDebug(new MethodElementName("sample.MethodCallTest"));
        }

        @Test
        void runTest() {
            SimpleProbe prbEx = new SimpleProbe(probeVariable);
            //ProbeExResult pr = prbEx.run(2000);
            //pr.print();
        }
    }
}


