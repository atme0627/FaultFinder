package jisd.fl.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.tuple.Pair;

class StaticAnalyzerTest {
    String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
    @Test
    void getClassNameTest() throws IOException {
        ArrayList<String> classNames = new ArrayList<>(StaticAnalyzer.getClassNames(targetSrcDir));
        Collections.sort(classNames);
        for(String className : classNames){
            System.out.println(className);
        }
    }

    @Test
    void getMethodNameTest() throws IOException {
        String targetClassName = "org.apache.commons.math.optimization.linear.SimplexTableau";
        Set<String> methodNames = StaticAnalyzer.getMethodNames(targetSrcDir, targetClassName, false);
        for(String methodName : methodNames){
            System.out.println(methodName);
        }
    }

    @Test
    void getRangeOfMethodsTest() throws IOException {
//        String targetClassName = "demo.SortTest";
//        Map<String, Pair<Integer, Integer>> methodNames = StaticAnalyzer.getRangeOfMethods(targetSrcDir, targetClassName);
//        methodNames.forEach((k, v)->{
//            System.out.println("method name: " + k + ", start: " + v.getLeft() + ", end: " + v.getRight());
//        });
    }

    @Test
    void getMethodCallGraphTest() throws IOException {

        MethodCallGraph mcg = StaticAnalyzer.getMethodCallGraph(targetSrcDir);
        mcg.printCallGraph();
    }

    @Test
    void getMethodNameFormLineTest() {
        String targetClassName = "org.apache.commons.math.optimization.linear.SimplexTableau";
        int line = 343;
        String methodName = StaticAnalyzer.getMethodNameFormLine(targetSrcDir, targetClassName, line);
        System.out.println(methodName);
    }
}