package jisd.fl.mapper;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuspiciousVariableMapperTest {

    @Test
    void toJson_local() {
        SuspiciousLocalVariable target = new SuspiciousLocalVariable(
                new MethodElementName("org.sample.ExampleTest#test1()"),
                new MethodElementName("org.sample.Example#method(int)"),
                "nums",
                "13",
                true,
                2
        );
        String expected = """
                {"failedTest":"org.sample.ExampleTest#test1()","locateMethod":"org.sample.Example#method(int)","variableName":"nums[2]","actualValue":"13","isPrimitive":true,"type":"local"}""";

        String actual = SuspiciousVariableMapper.toJson(target);
        assertEquals(expected, actual);
    }

    @Test
    void toJson_field() {
        SuspiciousFieldVariable target = new SuspiciousFieldVariable(
                new MethodElementName("org.sample.ExampleTest#test1()"),
                new ClassElementName("org.sample.Example"),
                "count",
                "42",
                true
        );
        String expected = """
                {"failedTest":"org.sample.ExampleTest#test1()","locateClass":"org.sample.Example","variableName":"this.count","actualValue":"42","isPrimitive":true,"type":"field"}""";

        String actual = SuspiciousVariableMapper.toJson(target);
        assertEquals(expected, actual);
    }

    @Test
    void fromJson_local() {
        String json = """
                {
                "failedTest":"org.sample.ExampleTest#test1()",
                "locateMethod":"org.sample.Example#method(int)",
                "variableName":"nums[2]",
                "actualValue":"13",
                "isPrimitive":true,
                "type":"local"
                }
        """;
        SuspiciousLocalVariable expected = new SuspiciousLocalVariable(
                new MethodElementName("org.sample.ExampleTest#test1()"),
                new MethodElementName("org.sample.Example#method(int)"),
                "nums",
                "13",
                true,
                2
        );
        SuspiciousVariable actual = SuspiciousVariableMapper.fromJson(json);
        assertEquals(expected, actual);
    }

    @Test
    void fromJson_field() {
        String json = """
                {
                "failedTest":"org.sample.ExampleTest#test1()",
                "locateClass":"org.sample.Example",
                "variableName":"this.count",
                "actualValue":"42",
                "isPrimitive":true,
                "type":"field"
                }
        """;
        SuspiciousFieldVariable expected = new SuspiciousFieldVariable(
                new MethodElementName("org.sample.ExampleTest#test1()"),
                new ClassElementName("org.sample.Example"),
                "count",
                "42",
                true
        );
        SuspiciousVariable actual = SuspiciousVariableMapper.fromJson(json);
        assertEquals(expected, actual);
    }

    @Test
    void fromJsonArray_mixed() {
        String json = """
                [
                    {
                        "failedTest":"org.sample.ExampleTest#test1()",
                        "locateMethod":"org.sample.Example#method(int)",
                        "variableName":"nums[2]",
                        "actualValue":"13",
                        "isPrimitive":true,
                        "type":"local"
                    },
                    {
                        "failedTest":"org.sample.ExampleTest#test2()",
                        "locateClass":"org.sample.Example",
                        "variableName":"this.answer",
                        "actualValue":"5",
                        "isPrimitive":true,
                        "type":"field"
                    }
                ]
        """;
        List<SuspiciousVariable> expected = List.of(
                new SuspiciousLocalVariable(
                        new MethodElementName("org.sample.ExampleTest#test1()"),
                        new MethodElementName("org.sample.Example#method(int)"),
                        "nums",
                        "13",
                        true,
                        2
                ),
                new SuspiciousFieldVariable(
                        new MethodElementName("org.sample.ExampleTest#test2()"),
                        new ClassElementName("org.sample.Example"),
                        "answer",
                        "5",
                        true
                )
        );
        List<SuspiciousVariable> actual = SuspiciousVariableMapper.fromJsonArray(json);
        assertEquals(expected, actual);
    }
}
