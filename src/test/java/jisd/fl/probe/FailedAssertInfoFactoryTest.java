package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertInfoFactory;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.Test;

class FailedAssertInfoFactoryTest {
    FailedAssertInfoFactory factory = new FailedAssertInfoFactory();
    String srcDir = PropertyLoader.getProperty("testSrcDir");
    String binDir = PropertyLoader.getProperty("testBinDir");
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String testMethodName = "testSingleVariableAndConstraint()";

    @Test
    void genFaeiFromAssertInfoFactory() {
        String actual = "0.0";
        int line = 75;

//        assertEquals(AssertType.EQUAL, fai.getAssertType());
//        assertEquals("sum", fai.getVariableName());
//        assertEquals("12", fai.getActualValue());
//        assertEquals(srcDir, fai.getSrcDir());
//        assertEquals(binDir, fai.getBinDir());
//        assertEquals(coverageCollectionName, fai.getTestClassName());
//        assertEquals(testMethodName, fai.getTestMethodName());
//        assertEquals(line, fai.getLineOfAssert());
    }
}