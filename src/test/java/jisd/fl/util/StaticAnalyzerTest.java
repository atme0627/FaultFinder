package jisd.fl.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import static org.junit.jupiter.api.Assertions.*;

class StaticAnalyzerTest {
    @Test
    void getMethodNameTest() throws IOException {
        String targetSrcPath = "../proj4test/src/test/java";
        String targetClassName = "demo.SortTest";
        ArrayList<String> methodNames = StaticAnalyzer.getMethodNames(targetSrcPath, targetClassName);
        System.out.println(Arrays.toString(methodNames.toArray()));
    }

    @Test
    void getRangeOfMethodsTest() throws IOException {
        String targetSrcPath = "../proj4test/src/test/java";
        String targetClassName = "demo.SortTest";
        Map<String, Pair<Integer, Integer>> methodNames = StaticAnalyzer.getRangeOfMethods(targetSrcPath, targetClassName);
        methodNames.forEach((k, v)->{
            System.out.println("method name: " + k + ", start: " + v.getLeft() + ", end: " + v.getRight());
        });
    }
}