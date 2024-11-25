package jisd.fl.probe;

import jisd.fl.probe.AssertType;
import jisd.fl.probe.FailedAssertEqualInfo;
import jisd.fl.probe.FailedAssertInfoFactory;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedAssertInfoFactoryTest {
    FailedAssertInfoFactory factory = new FailedAssertInfoFactory();
    String srcDir = PropertyLoader.getProperty("d4jTestSrcDir");
    String binDir = PropertyLoader.getProperty("d4jTestBinDir");
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
//        assertEquals(testClassName, fai.getTestClassName());
//        assertEquals(testMethodName, fai.getTestMethodName());
//        assertEquals(line, fai.getLineOfAssert());
    }
}