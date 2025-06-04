package experiment.util;

import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.CodeElementName;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.util.List;

class SuspiciousVariableFinderTest {

    @Test
    void find1() throws NoSuchFileException {
        PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
        PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
        PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
        PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");

        TestUtil.compileForDebug(new CodeElementName("sample.MethodCallTest"));
        SuspiciousVariableFinder finder
                = new SuspiciousVariableFinder(new CodeElementName("sample.MethodCallTest#methodCall1()"));
        List<SuspiciousVariable> result = finder.find();
        result.forEach(vi -> System.out.println(vi.toString()));
    }

    @Test
    void findD4j1() throws NoSuchFileException {
        PropertyLoader.setProperty("targetSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/main/java");
        PropertyLoader.setProperty("testSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/test/java");
        PropertyLoader.setProperty("testBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/test-classes");
        PropertyLoader.setProperty("targetBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/classes");
        String testClassName = "org.apache.commons.math3.distribution.HypergeometricDistributionTest";
        String shortTestMethodName = "testMath1021";
        String testMethodName = testClassName + "#" + shortTestMethodName + "()";

        TestUtil.compileForDebug(new CodeElementName("org.apache.commons.math3.distribution.HypergeometricDistributionTest"));
        SuspiciousVariableFinder finder
                = new SuspiciousVariableFinder(new CodeElementName(testMethodName));
        List<SuspiciousVariable> result = finder.find();
        result.forEach(vi -> System.out.println(vi.toString()));
    }
}