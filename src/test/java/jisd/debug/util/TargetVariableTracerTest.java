package jisd.debug.util;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.record.TracedValue;
import jisd.fl.probe.record.TracedValueCollection;
import jisd.fl.probe.internal.TargetVariableTracer;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.MethodElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;


class TargetVariableTracerTest {

    @BeforeEach
    void setUp(){
        Dotenv dotenv = Dotenv.load();
        Path testProjectDir = Paths.get(dotenv.get("TEST_PROJECT_DIR"));
        PropertyLoader.setTargetSrcDir(testProjectDir.resolve("src/main/java").toString());
        PropertyLoader.setTestSrcDir(testProjectDir.resolve("src/test/java").toString());
        PropertyLoader.setTargetBinDir(testProjectDir.resolve("build/classes/java/main").toString());
        PropertyLoader.setTestBinDir(testProjectDir.resolve("build/classes/java/test").toString());
    }

    @Test
    public void simple() {
        MethodElementName failedTest = new MethodElementName("jisd.debug.util.TargetVariableTracerTest#test1()");
        MethodElementName locateMethod = new MethodElementName("jisd.debug.util.TargetVariableTracerTarget#run(int)");
        SuspiciousVariable target = new SuspiciousVariable(failedTest, locateMethod.getFullyQualifiedMethodName(), "x", "13", true, false);
        TargetVariableTracer targetVariableTracer = new TargetVariableTracer(target);
        TracedValueCollection tracedValues = targetVariableTracer.traceValuesOfTarget();

        List<TracedValue> actual = tracedValues.getAll();
        record Expected(int line, String name, String value) {}
        List<Expected> expected = List.of(
                new Expected(9,  "x", "null"),
                new Expected(10, "x", "0"),
                new Expected(11, "x", "0"),
                new Expected(11, "x", "1"),
                new Expected(11, "x", "2"),
                new Expected(13, "x", "3")
        );
        assertEquals(expected.size(), actual.size(), "size mismatch");
        // 各要素をまとめて検証
        assertAll(
            IntStream.range(0, expected.size())
                .mapToObj(i -> () -> {
                    var e = expected.get(i);
                    var a = actual.get(i);
                    assertEquals(e.line(),  a.lineNumber,   "line @" + i);
                    assertEquals(e.name(),  a.variableName, "name @" + i);
                    assertEquals(e.value(), a.value,        "value @" + i);
                })
        );
    }
}