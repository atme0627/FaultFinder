package jisd.fl.util;

import experiment.defect4j.Defects4jUtil;
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
    class getTestMethodsTest {
        @BeforeEach
        void initProperty(){
            PropertyLoader.setTargetSrcDir("src/test/resources");
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

        static void printTestIdentifiers(TestPlan plan, TestIdentifier id, int level) {
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
}