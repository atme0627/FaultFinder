package jisd.fl.util;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.core.entity.MethodElementName;
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
            String project = "Lang";
            int bugId = 11;
            Defects4jUtil.changeTargetVersion(project, bugId);
            MethodElementName targetTestClass = new MethodElementName("org.apache.commons.lang3.RandomStringUtilsTest");
            TestUtil.compileForDebug(targetTestClass);
            Set<MethodElementName> testMethods = TestUtil.getTestMethods(targetTestClass);
            for(MethodElementName ce : testMethods){
                System.out.println(ce);
            }
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
           MethodElementName targetMethod = new MethodElementName("sample.SampleTest#case2()");
           TestUtil.compileForDebug(targetMethod);
       }
    }
}