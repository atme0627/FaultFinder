package experiment.util.internal.finder;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.core.entity.SuspiciousVariable;
import jisd.fl.util.PropertyLoader;
import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineValueWatcherTest {
    MethodElementName targetTestClassName;

    MethodElementName getTargetTestMethod(String shortMethodName){
        return new MethodElementName(targetTestClassName.getFullyQualifiedClassName() + "#" + shortMethodName + "()");
    }

    @BeforeEach
    void setUp(){
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.setTargetSrcDir(testProjectDir.resolve("src/main/java").toString());
        PropertyLoader.setTestSrcDir(testProjectDir.resolve("src/test/java").toString());
        PropertyLoader.setTargetBinDir(testProjectDir.resolve("build/classes/java/main").toString());
        PropertyLoader.setTestBinDir(testProjectDir.resolve("build/classes/java/test").toString());

        targetTestClassName = new MethodElementName("experiment.util.internal.finder.LineValueWatcherTest");
    }

    @Test
    void primitiveVariables() {
        MethodElementName targetTestMethod = getTargetTestMethod("primitiveVariables");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);

        List<Integer> assertLines = List.of(21, 22, 23, 24, 25, 26, 27, 28);
        List<SuspiciousVariable> expectedSuspiciousVariables = List.of(
            new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constByte", "127", true, false),
            new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constChar", "x", true, false),
            new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constShort", "16", true, false),
            new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constInteger", "32", true, false),
            new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constFloat", "2.0", true, false),
            new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constLong", "5", true, false),
            new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constDouble", "1.2", true, false),
            new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constBoolean", "true", true, false)
        );

        for(int i = 0; i < 8; i++) {
            List<SuspiciousVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLines.get(i), targetTestMethod);
            assertTrue(
            watchedValuesInLine.contains(expectedSuspiciousVariables.get(i)),
                    "Line " + assertLines.get(i) + " should contain: " + expectedSuspiciousVariables.get(i)
            );
        }
    }

    @Test
    void stringVariable() {
        MethodElementName targetTestMethod = getTargetTestMethod("stringVariable");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 34;
        List<SuspiciousVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);
        SuspiciousVariable expected = new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constString", "\"const string\"", true, false);
        assertTrue(
                watchedValuesInLine.contains(expected),
                "Line " + assertLine + " should contain: " + expected
        );
    }

    @Test
    //現時点ではprimitive型のラッパークラスには非対応
    void primitiveWrapperVariables() {
        MethodElementName targetTestMethod = getTargetTestMethod("primitiveWrapperVariables");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);

        List<Integer> assertLines = List.of(48, 49, 50, 51, 52, 53, 54, 55);
        List<SuspiciousVariable> expectedSuspiciousVariables = List.of(
                new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constByte", "127", true, false),
                new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constChar", "x", true, false),
                new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constShort", "16", true, false),
                new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constInteger", "32", true, false),
                new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constFloat", "2.0", true, false),
                new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constLong", "5", true, false),
                new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constDouble", "1.2", true, false),
                new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "constBoolean", "true", true, false)
        );

        for (int i = 0; i < assertLines.size(); i++) {
            List<SuspiciousVariable> watchedValuesInLine =
                    valueWatcher.watchAllValuesInAssertLine(assertLines.get(i), targetTestMethod);
            assertFalse(watchedValuesInLine.contains(expectedSuspiciousVariables.get(i)));
        }
    }

    @Test
    void fieldVariable() {
        MethodElementName targetTestMethod = getTargetTestMethod("fieldVariable");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 60;

        List<SuspiciousVariable> watchedValuesInLine =
                valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousVariable expected =
                new SuspiciousVariable(
                        targetTestClassName,
                        targetTestClassName.getFullyQualifiedClassName(),
                        "fieldVariable",
                        "10",
                        true,
                        true
                );
        assertTrue(
                watchedValuesInLine.contains(expected),
                "Line " + assertLine + " should contain: " + expected
        );
    }

    @Test
    void arrayAccess() {
        MethodElementName targetTestMethod = getTargetTestMethod("arrayAccess");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 66;

        List<SuspiciousVariable> watchedValuesInLine =
                valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousVariable expected =
                new SuspiciousVariable(
                        targetTestClassName,
                        targetTestMethod.getFullyQualifiedMethodName(),
                        "constIntArray",
                        "1",
                        true,
                        false,
                        0
                );
        assertTrue(
                watchedValuesInLine.contains(expected),
                "Line " + assertLine + " should contain: " + expected
        );
    }

    @Test
    void multipleVariablesInAssertLine() {
        MethodElementName targetTestMethod = getTargetTestMethod("multipleVariablesInAssertLine");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 73;
        List<SuspiciousVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousVariable expectedAlpha = new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "alpha", "2", true, false);
        SuspiciousVariable expectedBeta = new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "beta", "3", true, false);
        assertTrue(watchedValuesInLine.contains(expectedAlpha));
        assertTrue(watchedValuesInLine.contains(expectedBeta));
    }

    @Test
    void multipleAssertExecution() {
        MethodElementName targetTestMethod = getTargetTestMethod("multipleAssertExecution");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 80;
        List<SuspiciousVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);
        SuspiciousVariable expected = new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "i", "3", true, false);
        assertTrue(watchedValuesInLine.contains(expected));
    }

    @Test
    void crash() {
        MethodElementName targetTestMethod = getTargetTestMethod("crash");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 88;
        List<SuspiciousVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousVariable expectedAlpha = new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "alpha", "2", true, false);
        SuspiciousVariable expectedBeta = new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "beta", "0", true, false);
        assertTrue(watchedValuesInLine.contains(expectedAlpha));
        assertTrue(watchedValuesInLine.contains(expectedBeta));
    }

    @Test
    void crashInMultipleExecution() {
        MethodElementName targetTestMethod = getTargetTestMethod("crashInMultipleExecution");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 96;
        List<SuspiciousVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousVariable expectedAlpha = new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "alpha", "2", true, false);
        SuspiciousVariable expectedBeta = new SuspiciousVariable(targetTestClassName, targetTestMethod.getFullyQualifiedMethodName(), "i", "0", true, false);
        assertTrue(watchedValuesInLine.contains(expectedAlpha));
        assertTrue(watchedValuesInLine.contains(expectedBeta));
    }


}