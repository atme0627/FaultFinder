package jisd.fl.util;

import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class TestLauncherTest {
    @Nested
    class D4jCase {
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
                            + ":" + PropertyLoader.getDebugBinDir()
                            + ":" + PropertyLoader.getTargetBinDir()
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

    @Nested
    class SimpleCase {
        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");

            TestUtil.compileForDebug(new MethodElementName("sample.MethodCallTest"));
        }

        @Test
        void launchTest() throws IOException, InterruptedException {
            String testMethodName = "sample.SampleTest#case2()";

            Process proc = Runtime.getRuntime().exec(
                    "java -cp ./build/classes/java/main"
                            + ":" + PropertyLoader.getDebugBinDir()
                            + ":" + PropertyLoader.getTargetBinDir()
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

        @Test
        void launchMethodCallTest() throws IOException, InterruptedException {
            String testMethodName = "sample.MethodCallTest#methodCall1()";

            Process proc = Runtime.getRuntime().exec(
                    "java -cp ./build/classes/java/main"
                            + ":" + PropertyLoader.getDebugBinDir()
                            + ":" + PropertyLoader.getTargetBinDir()
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
}