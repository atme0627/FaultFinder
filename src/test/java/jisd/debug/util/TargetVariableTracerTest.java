package jisd.debug.util;

import io.github.cdimascio.dotenv.Dotenv;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.core.entity.TracedValue;
import jisd.fl.infra.jdi.TargetVariableTracer;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.core.entity.element.MethodElementName;
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
        var cfg = new PropertyLoader.ProjectConfig(
                testProjectDir,
                Path.of("src/main/java"),
                Path.of("src/test/java"),
                Path.of("build/classes/java/main"),
                Path.of("build/classes/java/test")
        );
        PropertyLoader.setProjectConfig(cfg);
    }

    @Test
    public void simple() {
        MethodElementName failedTest = new MethodElementName("jisd.debug.util.TargetVariableTracerTest#test1()");
        MethodElementName locateMethod = new MethodElementName("jisd.debug.util.TargetVariableTracerTarget#run(int)");
        SuspiciousVariable target = new SuspiciousVariable(failedTest, locateMethod.fullyQualifiedName(), "x", "13", true, false);
        TargetVariableTracer targetVariableTracer = new TargetVariableTracer(target);

        List<TracedValue> actual = targetVariableTracer.traceValuesOfTarget();
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