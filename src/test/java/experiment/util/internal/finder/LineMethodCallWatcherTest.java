package experiment.util.internal.finder;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousReturnValue;
import jisd.fl.util.PropertyLoader;
import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LineMethodCallWatcherTest {
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

        targetTestClassName = new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTest");
    }

    @Test
    void simpleValueReturn() {
        MethodElementName targetTestMethod = getTargetTestMethod("simpleValueReturn");
        LineMethodCallWatcher watcher = new LineMethodCallWatcher(targetTestMethod);

        SuspiciousReturnValue expected = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#simpleValueReturn()"),
                17,
                "25"
        );

        List<SuspiciousExpression> result = watcher.searchSuspiciousReturns(13, targetTestMethod);
        assertEquals(1, result.size());
        assertTrue(result.contains(expected));
    }

    @Test
    void methodCallReturn() {
        MethodElementName targetTestMethod = getTargetTestMethod("methodCallReturn");
        LineMethodCallWatcher watcher = new LineMethodCallWatcher(targetTestMethod);

        SuspiciousReturnValue expected1 = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#simpleValueReturn()"),
                17,
                "25"
        );

        SuspiciousReturnValue expected2 = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#methodCallReturn(int)"),
                21,
                "27"
        );

        List<SuspiciousExpression> result = watcher.searchSuspiciousReturns(19, targetTestMethod);
        assertEquals(2, result.size());
        assertTrue(result.contains(expected1));
        assertTrue(result.contains(expected2));
    }

    @Test
    void nestedMethodCallReturn() {
        MethodElementName targetTestMethod = getTargetTestMethod("nestedMethodCallReturn");
        LineMethodCallWatcher watcher = new LineMethodCallWatcher(targetTestMethod);

        SuspiciousReturnValue expected1 = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#simpleValueReturn()"),
                17,
                "25"
        );

        SuspiciousReturnValue expected2 = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#methodCallReturn(int)"),
                21,
                "35"
        );

        SuspiciousReturnValue expected3 = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#nestedMethodCallReturn()"),
                26,
                "360"
        );

        SuspiciousReturnValue expected4 = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#getFieldValue()"),
                11,
                "10"
        );

        List<SuspiciousExpression> result = watcher.searchSuspiciousReturns(25, targetTestMethod);
        assertEquals(4, result.size());
        assertTrue(result.contains(expected1));
        assertTrue(result.contains(expected2));
        assertTrue(result.contains(expected3));
        assertTrue(result.contains(expected4));
    }

    @Test
    void callInArgument() {
        MethodElementName targetTestMethod = getTargetTestMethod("callInArgument");
        LineMethodCallWatcher watcher = new LineMethodCallWatcher(targetTestMethod);

        SuspiciousReturnValue expected1 = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#simpleValueReturn()"),
                17,
                "25"
        );

        SuspiciousReturnValue expected2 = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#methodCallReturn(int)"),
                21,
                "50"
        );

        List<SuspiciousExpression> result = watcher.searchSuspiciousReturns(31, targetTestMethod);
        assertEquals(2, result.size());
        assertTrue(result.contains(expected1));
        assertTrue(result.contains(expected2));
    }

    @Test
    void callStandardLibrary() {
        MethodElementName targetTestMethod = getTargetTestMethod("callStandardLibrary");
        LineMethodCallWatcher watcher = new LineMethodCallWatcher(targetTestMethod);

        SuspiciousReturnValue expected = new SuspiciousReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#getFieldValue()"),
                11,
                "10"
        );

        List<SuspiciousExpression> result = watcher.searchSuspiciousReturns(38, targetTestMethod);
        assertEquals(1, result.size());
        assertTrue(result.contains(expected));
    }

}