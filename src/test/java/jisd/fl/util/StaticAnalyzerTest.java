package jisd.fl.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.tuple.Pair;
import static org.junit.jupiter.api.Assertions.*;

class StaticAnalyzerTest {
    String targetSrcPath = "/Users/ezaki/Desktop/tools/defects4j/tmp/math87_buggy/src/java";
    @Test
    void getClassNameTest() throws IOException {
        ArrayList<String> classNames = new ArrayList<>(StaticAnalyzer.getClassNames(targetSrcPath));
        Collections.sort(classNames);
        for(String className : classNames){
            System.out.println(className);
        }
    }

    @Test
    void getMethodNameTest() throws IOException {
        String targetClassName = "demo.SortTest";
        Set<String> methodNames = StaticAnalyzer.getMethodNames(targetSrcPath, targetClassName);
        for(String methodName : methodNames){
            System.out.println(methodName);
        }
    }

    @Test
    void getRangeOfMethodsTest() throws IOException {
        String targetClassName = "demo.SortTest";
        Map<String, Pair<Integer, Integer>> methodNames = StaticAnalyzer.getRangeOfMethods(targetSrcPath, targetClassName);
        methodNames.forEach((k, v)->{
            System.out.println("method name: " + k + ", start: " + v.getLeft() + ", end: " + v.getRight());
        });
    }

    @Test
    void getMethodCallGraphTest() throws IOException {

        MethodCallGraph mcg = StaticAnalyzer.getMethodCallGraph(targetSrcPath);
        mcg.printCallGraph();
    }
}