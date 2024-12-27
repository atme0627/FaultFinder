package jisd.fl.probe;

import jisd.fl.probe.assertinfo.AssertType;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssertExtractorTest {

    @Test
    void getAssertByLineNumTest() {
        String srcDir = PropertyLoader.getProperty("testSrcDir");
        String binDir = PropertyLoader.getProperty("testBinDir");
        String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
        String testMethodName = "testSingleVariableAndConstraint()";
        int assertLineNum = 75;
        int nthArg = 2;
        String actual = "0.0";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);

        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, nthArg, actual);

        assertEquals(fai.getAssertType(), AssertType.EQUAL);
        assertEquals(fai.getTestClassName(), testClassName);
        assertEquals(fai.getTestMethodName(), testMethodName);
        assertTrue(fai.eval(actual));
        assertFalse(fai.eval("2"));
        assertFalse(fai.eval("3"));
    }


}

