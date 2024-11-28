package jisd.fl.probe;

import jisd.fl.probe.assertinfo.AssertType;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssertExtractorTest {

    @Test
    void getAssertByLineNumTest() {
        String srcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String binDir = PropertyLoader.getProperty("d4jTestBinDir");
        String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
        String testMethodName = "testSingleVariableAndConstraint()";
        int assertLineNum = 75;
        int nthArg = 2;
        String actual = "0.0";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);

        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, nthArg, actual);

        assertEquals(fai.getAssertType(), AssertType.EQUAL);
        assertEquals(fai.getVariableName(), "solution.getPoint()[0]");
        assertEquals(fai.getSrcDir(), srcDir);
        assertEquals(fai.getBinDir(), binDir);
        assertEquals(fai.getTestClassName(), testClassName);
        assertEquals(fai.getTestMethodName(), testMethodName);
        assertEquals(fai.getLineOfAssert(), assertLineNum);
        assertTrue(fai.eval(actual));
        assertFalse(fai.eval("2"));
        assertFalse(fai.eval("3"));
    }


}

