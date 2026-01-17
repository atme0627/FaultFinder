package experiment.util.internal.finder;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.core.domain.port.SuspiciousExpressionFactory;
import jisd.fl.infra.javaparser.JavaParserSuspiciousExpressionFactory;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.susp.SuspiciousReturnValue;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.core.entity.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LineMethodCallWatcherTest {
    MethodElementName targetTestClassName;
    final SuspiciousExpressionFactory factory = new JavaParserSuspiciousExpressionFactory();
    MethodElementName getTargetTestMethod(String shortMethodName){
        return new MethodElementName(targetTestClassName.getFullyQualifiedClassName() + "#" + shortMethodName + "()");
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

        targetTestClassName = new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTest");
    }

    @Test
    void simpleValueReturn() {
        MethodElementName targetTestMethod = getTargetTestMethod("simpleValueReturn");
        LineMethodCallWatcher watcher = new LineMethodCallWatcher(targetTestMethod);

        SuspiciousReturnValue expected = factory.createReturnValue(
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

        SuspiciousReturnValue expected1 = factory.createReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#simpleValueReturn()"),
                17,
                "25"
        );

        SuspiciousReturnValue expected2 = factory.createReturnValue(
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

        SuspiciousReturnValue expected1 = factory.createReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#simpleValueReturn()"),
                17,
                "25"
        );

        SuspiciousReturnValue expected2 = factory.createReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#methodCallReturn(int)"),
                21,
                "35"
        );

        SuspiciousReturnValue expected3 = factory.createReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#nestedMethodCallReturn()"),
                26,
                "360"
        );

        SuspiciousReturnValue expected4 = factory.createReturnValue(
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

        SuspiciousReturnValue expected1 = factory.createReturnValue(
                targetTestMethod,
                new MethodElementName("experiment.util.internal.finder.LineMethodCallWatcherTarget#simpleValueReturn()"),
                17,
                "25"
        );

        SuspiciousReturnValue expected2 = factory.createReturnValue(
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

        SuspiciousReturnValue expected = factory.createReturnValue(
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