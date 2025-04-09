package jisd.fl.util;

import com.sun.jdi.*;
import experiment.defect4j.Defects4jUtil;
import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.fl.probe.ProbeEx;
import jisd.fl.probe.ProbeExResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

class TestLauncherTest {
    @BeforeEach
    void initProperty() {
        PropertyLoader.setProperty("targetSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/main/java");
        PropertyLoader.setProperty("testSrcDir", "src/test/resources/d4jProject/Math_2_buggy/src/test/java");
        PropertyLoader.setProperty("testBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/test-classes");
        PropertyLoader.setProperty("targetBinDir", "src/test/resources/d4jProject/Math_2_buggy/target/classes");
    }

    @Test
    void launchTest() throws IOException, InterruptedException {
        String testClassName = "org.apache.commons.math3.distribution.HypergeometricDistributionTest";
        String shortTestMethodName = "testMath1021";
        String testMethodName = testClassName + "#" + shortTestMethodName + "()";

        Process proc = Runtime.getRuntime().exec(
                "java -cp ./build/classes/java/main"
                        + ":" + PropertyLoader.getProperty("testBinDir")
                        + ":" + PropertyLoader.getProperty("targetBinDir")
                        + ":" + PropertyLoader.getJunitClassPaths()
                        + " jisd.fl.util.TestLauncher " + testMethodName
        );

        proc.waitFor();
        String line = null;
        System.out.println("STDOUT---------------");
        try (var buf = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            while ((line = buf.readLine()) != null) System.out.println(line);
        }
        System.out.println("STDERR---------------");
        try (var buf = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
            while ((line = buf.readLine()) != null) System.err.println(line);
        }
    }
}