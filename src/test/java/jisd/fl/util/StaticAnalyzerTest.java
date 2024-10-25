package jisd.fl.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class StaticAnalyzerTest {
    @Test
    void getMethodNameTest() throws IOException {
        String targetSrcPath = "../proj4test/src/test/java";
        String targetClassName = "demo.SortTest";
        ArrayList<String> methodNames = StaticAnalyzer.getMethodNames(targetSrcPath, targetClassName);
        System.out.println(Arrays.toString(methodNames.toArray()));
    }
}