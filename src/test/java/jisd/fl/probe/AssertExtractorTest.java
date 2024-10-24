package jisd.fl.probe;

import jisd.fl.probe.AssertExtractor;
import jisd.fl.probe.AssertType;
import jisd.fl.probe.FailedAssertInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssertExtractorTest {

    @Test
    void getAssertByLineNumTest() {
        String srcDir = "src/test/java";
        String binDir = "build/classes/java/test";
        String testClassName = "src4test.SampleTest";
        String testMethodName = "sample1";
        int assertLineNum = 14;
        String expected = "3";
        String actual = "1";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, actual);

        assertEquals(fai.getAssertType(), AssertType.EQUAL);
        assertEquals(fai.getVariableName(), "c");
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

