package experiment.util.internal.finder;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.core.entity.element.MethodElementName;
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
        return new MethodElementName(targetTestClassName.fullyQualifiedClassName() + "#" + shortMethodName + "()");
    }

    @BeforeEach
    void setUp(){
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.ProjectConfig config = new PropertyLoader.ProjectConfig(
                testProjectDir,
                Path.of("src/main/java"),
                Path.of("src/test/java"),
                Path.of("build/classes/java/main"),
                Path.of("build/classes/java/test")
        );
        PropertyLoader.setProjectConfig(config);

        targetTestClassName = new MethodElementName("experiment.util.internal.finder.LineValueWatcherTest");
    }

    @Test
    void primitiveVariables() {
        MethodElementName targetTestMethod = getTargetTestMethod("primitiveVariables");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);

        List<Integer> assertLines = List.of(21, 22, 23, 24, 25, 26, 27, 28);
        List<SuspiciousLocalVariable> expectedSuspiciousLocalVariables = List.of(
            new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constByte", "127", true, false),
            new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constChar", "x", true, false),
            new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constShort", "16", true, false),
            new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constInteger", "32", true, false),
            new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constFloat", "2.0", true, false),
            new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constLong", "5", true, false),
            new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constDouble", "1.2", true, false),
            new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constBoolean", "true", true, false)
        );

        for(int i = 0; i < 8; i++) {
            List<SuspiciousLocalVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLines.get(i), targetTestMethod);
            assertTrue(
            watchedValuesInLine.contains(expectedSuspiciousLocalVariables.get(i)),
                    "Line " + assertLines.get(i) + " should contain: " + expectedSuspiciousLocalVariables.get(i)
            );
        }
    }

    @Test
    void stringVariable() {
        MethodElementName targetTestMethod = getTargetTestMethod("stringVariable");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 34;
        List<SuspiciousLocalVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);
        SuspiciousLocalVariable expected = new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constString", "\"const string\"", true, false);
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
        List<SuspiciousLocalVariable> expectedSuspiciousLocalVariables = List.of(
                new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constByte", "127", true, false),
                new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constChar", "x", true, false),
                new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constShort", "16", true, false),
                new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constInteger", "32", true, false),
                new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constFloat", "2.0", true, false),
                new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constLong", "5", true, false),
                new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constDouble", "1.2", true, false),
                new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "constBoolean", "true", true, false)
        );

        for (int i = 0; i < assertLines.size(); i++) {
            List<SuspiciousLocalVariable> watchedValuesInLine =
                    valueWatcher.watchAllValuesInAssertLine(assertLines.get(i), targetTestMethod);
            assertFalse(watchedValuesInLine.contains(expectedSuspiciousLocalVariables.get(i)));
        }
    }

    @Test
    void fieldVariable() {
        MethodElementName targetTestMethod = getTargetTestMethod("fieldVariable");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 60;

        List<SuspiciousLocalVariable> watchedValuesInLine =
                valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousLocalVariable expected =
                new SuspiciousLocalVariable(
                        targetTestClassName,
                        targetTestClassName.fullyQualifiedClassName(),
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

        List<SuspiciousLocalVariable> watchedValuesInLine =
                valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousLocalVariable expected =
                new SuspiciousLocalVariable(
                        targetTestClassName,
                        targetTestMethod.fullyQualifiedName(),
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
        List<SuspiciousLocalVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousLocalVariable expectedAlpha = new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "alpha", "2", true, false);
        SuspiciousLocalVariable expectedBeta = new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "beta", "3", true, false);
        assertTrue(watchedValuesInLine.contains(expectedAlpha));
        assertTrue(watchedValuesInLine.contains(expectedBeta));
    }

    @Test
    void multipleAssertExecution() {
        MethodElementName targetTestMethod = getTargetTestMethod("multipleAssertExecution");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 80;
        List<SuspiciousLocalVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);
        SuspiciousLocalVariable expected = new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "i", "3", true, false);
        assertTrue(watchedValuesInLine.contains(expected));
    }

    @Test
    void crash() {
        MethodElementName targetTestMethod = getTargetTestMethod("crash");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 88;
        List<SuspiciousLocalVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousLocalVariable expectedAlpha = new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "alpha", "2", true, false);
        SuspiciousLocalVariable expectedBeta = new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "beta", "0", true, false);
        assertTrue(watchedValuesInLine.contains(expectedAlpha));
        assertTrue(watchedValuesInLine.contains(expectedBeta));
    }

    @Test
    void crashInMultipleExecution() {
        MethodElementName targetTestMethod = getTargetTestMethod("crashInMultipleExecution");
        LineValueWatcher valueWatcher = new LineValueWatcher(targetTestMethod);
        int assertLine = 96;
        List<SuspiciousLocalVariable> watchedValuesInLine = valueWatcher.watchAllValuesInAssertLine(assertLine, targetTestMethod);

        SuspiciousLocalVariable expectedAlpha = new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "alpha", "2", true, false);
        SuspiciousLocalVariable expectedBeta = new SuspiciousLocalVariable(targetTestClassName, targetTestMethod.fullyQualifiedName(), "i", "0", true, false);
        assertTrue(watchedValuesInLine.contains(expectedAlpha));
        assertTrue(watchedValuesInLine.contains(expectedBeta));
    }


}