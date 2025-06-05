package jisd.fl.probe.info;

import jisd.debug.EnhancedDebugger;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.CodeElementName;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SuspiciousExpressionTest {

    @Nested
    class searchSuspiciousReturns {
        String testFqcn = "org.sample.MethodCallingTest";
        private String getFqmn(String testMethodName){
            return testFqcn + "#" + testMethodName + "()";
        }

        @BeforeEach
        void initProperty() {
            PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
            PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
        }

        @Test
        void polymorphism(){
            String testMethodName = "polymorphism";
            CodeElementName locateClass = new CodeElementName(testFqcn);
            int locateLine = 19;
            SuspiciousVariable suspVariable = new SuspiciousVariable(
                    getFqmn(testMethodName),
                    "totalArea",
                    "32.0",
                    true,
                    false
            );

            SuspiciousAssignment suspAssignment = new SuspiciousAssignment(
                    new CodeElementName(getFqmn(testMethodName)),
                    locateClass,
                    locateLine,
                    suspVariable
            );

            List<SuspiciousReturnValue> result = suspAssignment.searchSuspiciousReturns();
            result.forEach(System.out::println);
        }

        @Test
        void polymorphismLoop(){
            String testMethodName = "polymorphismLoop";
            CodeElementName locateClass = new CodeElementName(testFqcn);
            int locateLine = 34;
            SuspiciousVariable suspVariable = new SuspiciousVariable(
                    getFqmn(testMethodName),
                    "totalArea",
                    "32.0",
                    true,
                    false
            );

            SuspiciousAssignment suspAssignment = new SuspiciousAssignment(
                    new CodeElementName(getFqmn(testMethodName)),
                    locateClass,
                    locateLine,
                    suspVariable
            );

            List<SuspiciousReturnValue> result = suspAssignment.searchSuspiciousReturns();
            result.forEach(System.out::println);
        }

        @Test
        void chaining(){

        }
        @Test
        void nestedCalling(){

        }
        @Test
        void chainingAndNested(){

        }
        @Test
        void staticMethodCalling(){

        }
        @Test
        void createObjAndCallMethod(){

        }
        @Test
        void arrayAccess(){

        }
        @Test
        void callingViaCast(){

        }
        @Test
        void lambdaInArgument(){

        }
        @Test
        void recursion(){

        }
    }
}