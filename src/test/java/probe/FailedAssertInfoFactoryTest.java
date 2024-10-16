package probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailedAssertInfoFactoryTest {
    FailedAssertInfoFactory factory = new FailedAssertInfoFactory();
    String srcDir = "/Users/ezaki/IdeaProjects/MyFaultFinder/src/test/java";
    String binDir = "/Users/ezaki/IdeaProjects/MyFaultFinder/build/classes/java/";
    String testClassName = "src4test.SampleTest";
    String testMethodName = "sample1";

    @Test
    void genFaeiFromAssertInfoFactory() {
        String assertLine = "assertEquals(10, sum);";
        String actual = "12";
        int line = 8;
        FailedAssertEqualInfo fai = (FailedAssertEqualInfo) factory.create(assertLine, actual, srcDir, binDir, testClassName, testMethodName, line);

        assertEquals(AssertType.EQUAL, fai.getAssertType());
        assertEquals("sum", fai.getVariableName());
        assertEquals("10", fai.getExpectedValue());
        assertEquals("12", fai.getActualValue());
        assertEquals(srcDir, fai.getSrcDir());
        assertEquals(binDir, fai.getBinDir());
        assertEquals(testClassName, fai.getTestClassName());
        assertEquals(testMethodName, fai.getTestMethodName());
        assertEquals(line, fai.getLineOfAssert());
    }
}