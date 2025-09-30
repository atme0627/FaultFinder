package experiment.util.internal.finder;

import experiment.util.SuspiciousVariableFinder;
import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
            assertTrue(watchedValuesInLine.contains(expectedSuspiciousVariables.get(i)));
        }
    }
}