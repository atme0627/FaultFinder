package probe;

import org.junit.jupiter.api.Test;

class AssertExtractorTest {

    @Test
    void getAssertByLineNumTest() {
        String srcDir = "src/test/java";
        String binDir = "build/classes/java/test";
        String className = "src4test.SampleTest";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        //ae.getAssertByLineNum(className, 13);
    }
}

