package probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedAssertInfoFactoryTest {
    FailedAssertInfoFactory factory = new FailedAssertInfoFactory();
    String path = "/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/java/src4test";
    String testName = "SampleTest";

    @Test
    void genFaeiFromAssertInfoFactory() {
        String assertLine = "assertEquals(10, sum);";
        Object actual = 12;
        int line = 8;
        FailedAssertEqualInfo fai = (FailedAssertEqualInfo) factory.create(assertLine, actual, path, testName, line);

        assertEquals(AssertType.EQUAL, fai.getAssertType());
        assertEquals("sum", fai.getVariableName());
        assertEquals(10, fai.getExpectedValue());
        assertEquals(12, fai.getActualValue());
        assertEquals(path, fai.getPath());
        assertEquals(testName, fai.getTestName());
        assertEquals(line, fai.getLineOfAssert());
    }

    @Test
    void genFatiFromAssertInfoFactory() {
        String assertLine = "assertTrue(sum >= 20);";
        Object actual = false;
        int line = 8;
        FailedAssertTrueInfo fai = (FailedAssertTrueInfo) factory.create(assertLine, actual, path, testName, line);

        assertEquals(AssertType.TRUE, fai.getAssertType());
        assertEquals("sum", fai.getVariableName());
        assertEquals(path, fai.getPath());
        assertEquals(testName, fai.getTestName());
        assertFalse(fai.eval(Integer.valueOf(10)));
        assertTrue(fai.eval(Integer.valueOf(20)));
        assertTrue(fai.eval(Integer.valueOf(30)));
        assertEquals(line, fai.getLineOfAssert());
    }
}