package jisd.fl.util;

import experiment.defect4j.Defects4jUtil;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestUtilTest {
    @Test
    void getTestMethodsTest() {
        Defects4jUtil.changeTargetVersion("Math", 6);
        String testClass = "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizerTest";
        Set<String> testMethods = TestUtil.getTestMethods(testClass);
        for(String m : testMethods){
            System.out.println(m);
        }
    }
}