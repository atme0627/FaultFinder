package jisd.fl.util;

import experiment.defect4j.Defects4jUtil;
import jisd.debug.Debugger;
import jisd.fl.util.analyze.CodeElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.Set;

class TestUtilTest {
    @Nested
    class GetTestMethodsTest {
        @BeforeEach
        void initProperty(){
            PropertyLoader.setTargetSrcDir("src/test/resources/jisd/fl/util");
        }

        @Test
        void voidGaussNewtonOptimizerTest() {
            CodeElement testClass = new CodeElement("TestUtilTest.getTestMethodsTest.d4jMath6.GaussNewtonOptimizerTest");
            Set<CodeElement> testMethods = TestUtil.getTestMethods(testClass);
            for(CodeElement ce : testMethods){
                System.out.println(ce);
            }
        }

        @Test
        void getTestMethodsByJunit(){
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(
                            DiscoverySelectors.selectPackage("jisd.fl.util")
                    )
                    .build();

            Launcher launcher = LauncherFactory.create();
            TestPlan testPlan = launcher.discover(request);

            for (TestIdentifier identifier : testPlan.getRoots()) {
                printTestIdentifiers(testPlan, identifier, 0);
            }
        }

        void printTestIdentifiers(TestPlan plan, TestIdentifier id, int level) {
            if (id.isTest()) {
                System.out.println("  ".repeat(level) + "Test: " + id.getDisplayName());
            } else if (id.isContainer()) {
                System.out.println("  ".repeat(level) + "Container: " + id.getDisplayName());
            }

            for (TestIdentifier child : plan.getChildren(id)) {
                printTestIdentifiers(plan, child, level + 1);
            }
        }
    }

    @Nested
    class TestDebuggerFactory {
        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/src/main");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/src/test");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/build/main");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/build/test");
        }

        @Test
        void simpleCase(){
            CodeElement targetMethod = new CodeElement("sample.SampleTest#case2()");
            Debugger dbg = TestUtil.testDebuggerFactory(targetMethod);
            dbg.stopAt("sample.SampleTest", 19);
            dbg.run(1000);
            dbg.locals();
            dbg.exit();
        }
    }

    @Nested
    class compileForDebug{
        @BeforeEach
        void initProperty() {
            PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/src/main");
            PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/src/test");
            PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/build/main");
            PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/build/test");
        }

       @Test
       void simpleCase(){
           CodeElement targetMethod = new CodeElement("sample.SampleTest#case2()");
           TestUtil.compileForDebug(targetMethod);
       }
    }
}