package probe;

import org.junit.jupiter.api.Test;

class AssertExtractorTest {

    @Test
    void displaySrc() {
        String srcDir = "src/test/java";
        String binDir = "build/classes/java/test";
        String className = "src4test.SampleTest";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        String src = ae.getSource(className);
        System.out.println(src);
    }
}

