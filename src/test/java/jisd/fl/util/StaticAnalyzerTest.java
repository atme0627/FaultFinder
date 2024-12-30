package jisd.fl.util;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.nio.file.NoSuchFileException;
import java.util.*;

class StaticAnalyzerTest {
    String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
    String targetBinDir = PropertyLoader.getProperty("targetBinDir");
    @Test
    void getClassNameTest() {
        ArrayList<String> classNames = new ArrayList<>(StaticAnalyzer.getClassNames(targetSrcDir));
        Collections.sort(classNames);
        for(String className : classNames){
            System.out.println(className);
        }
    }

    @Test
    void getMethodNameTest() throws NoSuchFileException {
        String targetClassName = "org.apache.commons.math.optimization.linear.SimplexTableau";
        Set<String> methodNames = StaticAnalyzer.getMethodNames(targetClassName, false, false, false, true);
        for(String methodName : methodNames){
            System.out.println(methodName);
        }
    }

    //@Testのみとれているか確認
    @Test
    void getMethodNameTest2() throws NoSuchFileException {
        String targetClassName = "org.apache.commons.math3.distribution.HypergeometricDistributionTest";
        Set<String> methodNames = StaticAnalyzer.getMethodNames(targetClassName, true, true, false, true);
        for(String methodName : methodNames){
            System.out.println(methodName);
        }
    }

    //@Testのみとれているか確認
    @Test
    void getMethodNameTest3() throws NoSuchFileException {
        String targetClassName = "org.apache.commons.math3.complex.ComplexTest";
        Set<String> methodNames = StaticAnalyzer.getMethodNames(targetClassName, true, true, false, true);
        for(String methodName : methodNames){
            System.out.println(methodName);
        }
    }

    @Test
    void getRangeOfAllStatementsTest() throws NoSuchFileException {
        String targetClassName = "org.apache.commons.math.optimization.linear.SimplexTableau";
        Map<Integer, Pair<Integer, Integer>> methodNames = StaticAnalyzer.getRangeOfAllStatements(targetClassName);
        methodNames.forEach((k, v)->{
            System.out.println("line: " + k + ", start: " + v.getLeft() + ", end: " + v.getRight());
        });
    }

    @Test
    void getMethodNameFormLineTest() throws NoSuchFileException {
        String targetClassName = "org.apache.commons.math.optimization.linear.SimplexTableau";
        int line = 343;
        String methodName = StaticAnalyzer.getMethodNameFormLine(targetClassName, line);
        System.out.println(methodName);
    }

    @Nested
    class getAssertLineTest {
        @Test
        void test1() throws NoSuchFileException {
            String locateMethod = "org.apache.commons.math.optimization.linear.SimplexTableau#getSolution()";
            List<Integer> result = StaticAnalyzer.getAssignLine(locateMethod, "coefficients");
            System.out.println(Arrays.toString(result.toArray()));
        }
        @Test
        void test2() throws NoSuchFileException {
            String locateMethod = "org.apache.commons.math.optimization.linear.SimplexTableau#getSolution()";
            List<Integer> result = StaticAnalyzer.getAssignLine(locateMethod, "basicRow");
            System.out.println(Arrays.toString(result.toArray()));
        }
        @Test
        void test3() throws NoSuchFileException {
            String locateMethod = "org.apache.commons.math.optimization.RealPointValuePair#RealPointValuePair(double[], double)";
            List<Integer> result = StaticAnalyzer.getAssignLine(locateMethod, "this.point");
            System.out.println(Arrays.toString(result.toArray()));
        }
    }

    @Test
    void getAllMethodTest() throws NoSuchFileException {
        Set<String> allMethods = StaticAnalyzer.getAllMethods(targetSrcDir, false, false);
        for(String methodNames : allMethods){
            System.out.println(methodNames);
        }
    }

    @Test
    void getMethodCallingLineTest() throws NoSuchFileException {
        String locateMethod = "org.apache.commons.math.optimization.linear.SimplexTableau#getSolution()";
        List<Integer> result = StaticAnalyzer.getMethodCallingLine(locateMethod);
        System.out.println(Arrays.toString(result.toArray()));
    }
}