package jisd.fl.util;

import com.github.javaparser.Range;
import jisd.fl.util.analyze.MethodElementName;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.nio.file.NoSuchFileException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticAnalyzerTest {
    @BeforeEach
    void initProperty(){
        PropertyLoader.setTargetSrcDir("src/test/resources/jisd/fl/util");
    }

    @Nested
    class getMethodNamesTest{
        @Test
        void SimpleCase() {
            MethodElementName targetClass = new MethodElementName("StaticAnalyzerTest.getMethodNamesTest.SimpleCase");
            try {
                Set<String> actual =
                        StaticAnalyzer.getMethodNames(targetClass);
                assertThat(actual, hasSize(5));
                assertThat(actual, hasItems(
                        "StaticAnalyzerTest.getMethodNamesTest.SimpleCase#SimpleCase()",
                        "StaticAnalyzerTest.getMethodNamesTest.SimpleCase#SimpleCase(int)",
                        "StaticAnalyzerTest.getMethodNamesTest.SimpleCase#methodA()",
                        "StaticAnalyzerTest.getMethodNamesTest.SimpleCase#methodB()",
                        "StaticAnalyzerTest.getMethodNamesTest.SimpleCase#methodC()"));
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void AbstractCase() {
            MethodElementName targetClass = new MethodElementName("StaticAnalyzerTest.getMethodNamesTest.AbstractCase");
            try {
                Set<String> actual =
                        StaticAnalyzer.getMethodNames(targetClass);
                assertThat(actual, hasSize(4));
                assertThat(actual, hasItems(
                        "StaticAnalyzerTest.getMethodNamesTest.AbstractCase#AbstractCase(int)",
                        "StaticAnalyzerTest.getMethodNamesTest.AbstractCase#methodA()",
                        "StaticAnalyzerTest.getMethodNamesTest.AbstractCase#abstractMethod1()",
                        "StaticAnalyzerTest.getMethodNamesTest.AbstractCase#abstractMethod2(int, int)"
                ));
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Nested
    class getRangeOfStatementTest {
        @Test
        void simpleCase1() throws NoSuchFileException {
            MethodElementName targetClass = new MethodElementName("StaticAnalyzerTest.getRangeOfStatementTest.SimpleCase");
            Optional<Range> actual = StaticAnalyzer.getRangeOfStatement(targetClass, 9);
            assertTrue(actual.isPresent());
            assertEquals(9, actual.get().begin.line);
            assertEquals(9, actual.get().end.line);
        }

        @Test
        void simpleCase2() throws NoSuchFileException {
            MethodElementName targetClass = new MethodElementName("StaticAnalyzerTest.getRangeOfStatementTest.SimpleCase");
            Optional<Range> actual = StaticAnalyzer.getRangeOfStatement(targetClass, 14);
            assertTrue(actual.isPresent());
            assertEquals(14, actual.get().begin.line);
            assertEquals(14, actual.get().end.line);
        }

        @Test
        void simpleCase3() throws NoSuchFileException {
            MethodElementName targetClass = new MethodElementName("StaticAnalyzerTest.getRangeOfStatementTest.SimpleCase");
            Optional<Range> actual = StaticAnalyzer.getRangeOfStatement(targetClass, 23);
            assertTrue(actual.isPresent());
            assertEquals(22, actual.get().begin.line);
            assertEquals(24, actual.get().end.line);
        }

        @Test
        void simpleCase4() throws NoSuchFileException {
            MethodElementName targetClass = new MethodElementName("StaticAnalyzerTest.getRangeOfStatementTest.SimpleCase");
            Optional<Range> actual = StaticAnalyzer.getRangeOfStatement(targetClass, 27);
            assertTrue(actual.isPresent());
            assertEquals(26, actual.get().begin.line);
            assertEquals(27, actual.get().end.line);
        }
    }

    @Nested
    class getMethodNameFromLineTest {
        @Test
        void simpleCase1() throws NoSuchFileException {
            MethodElementName targetClass = new MethodElementName("StaticAnalyzerTest.getMethodNameFromLineTest.SimpleCase");
            String actual = StaticAnalyzer.getMethodNameFormLine(targetClass, 12);
            assertEquals("StaticAnalyzerTest.getMethodNameFromLineTest.SimpleCase#methodA(int)", actual);
        }

        @Test
        void simpleCase2() throws NoSuchFileException {
            MethodElementName targetClass = new MethodElementName("StaticAnalyzerTest.getMethodNameFromLineTest.SimpleCase");
            String actual = StaticAnalyzer.getMethodNameFormLine(targetClass, 21);
            assertEquals("StaticAnalyzerTest.getMethodNameFromLineTest.SimpleCase#methodB()", actual);
        }
    }

    @Nested
    class getAssignLineTest {
        @Test
        void d4jMath87_SimplexTableau1() {
            MethodElementName locateMethod
                    = new MethodElementName("StaticAnalyzerTest.getAssertLineTest.d4jMath87_SimplexTableau#getSolution()");
            List<Integer> actual = StaticAnalyzer.getAssignLine(locateMethod, "coefficients");
            assertThat(actual, hasSize(1));
            assertThat(actual, hasItems(325));
        }
        @Test
        void d4jMath87_SimplexTableau2() {
            MethodElementName locateMethod
                    = new MethodElementName("StaticAnalyzerTest.getAssertLineTest.d4jMath87_SimplexTableau#getSolution()");
            List<Integer> actual = StaticAnalyzer.getAssignLine(locateMethod, "basicRow");
            assertThat(actual, hasSize(2));
            assertThat(actual, hasItems(327, 331));
        }
        @Test
        void d4jMath87_SimplexTableau3() {
            MethodElementName locateMethod
                = new MethodElementName("StaticAnalyzerTest.getAssertLineTest.d4jMath87_SimplexTableau#discardArtificialVariables()");
            List<Integer> actual = StaticAnalyzer.getAssignLine(locateMethod, "this.numArtificialVariables");
            assertThat(actual, hasSize(2));
            assertThat(actual, hasItems(112, 303));
        }

        //フィールドでの宣言のみ
        @Test
        void d4jMath87_SimplexTableau4() {
            MethodElementName locateMethod
                    = new MethodElementName("StaticAnalyzerTest.getAssertLineTest.d4jMath87_SimplexTableau#discardArtificialVariables()");
            List<Integer> actual = StaticAnalyzer.getAssignLine(locateMethod, "numArtificialVariables");
            assertThat(actual, hasSize(1));
            assertThat(actual, hasItems(87));
        }
    }

    @Nested
    class getMethodCallingLineTest {
        @Test
        void d4jMath87_SimplexTableau() throws NoSuchFileException {
            MethodElementName locateMethod
                    = new MethodElementName("StaticAnalyzerTest.getMethodCallingLineTest.d4jMath87_SimplexTableau#getSolution()");
            List<Integer> actual = StaticAnalyzer.getMethodCallingLine(locateMethod);
            assertThat(actual, hasSize(8));
            assertThat(actual, hasItems(325, 327, 328, 331, 332, 337, 339, 343));
        }
    }
}