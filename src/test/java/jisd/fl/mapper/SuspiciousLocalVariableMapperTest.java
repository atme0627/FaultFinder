package jisd.fl.mapper;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuspiciousLocalVariableMapperTest {
    @Test
    void toJson() {
        SuspiciousLocalVariable target = new SuspiciousLocalVariable(
                new MethodElementName("org.sample.ExampleTest#test1()"),
                new MethodElementName("org.sample.Example#method(int)"),
                "nums",
                "13",
                true,
                2
        );
        String expected = """
                {
                "failedTest":"org.sample.ExampleTest#test1()",
                "locateMethod":"org.sample.Example#method(int)",
                "variableName":"nums[2]",
                "actualValue":"13",
                "isPrimitive":true
                }
                """.replace("\n", "");

        String actual = SuspiciousVariableMapper.toJson(target);
        assertEquals(expected, actual);
    }

    @Test
    void fromJson() {
        String json = """
                {
                "failedTest":"org.sample.ExampleTest#test1()",
                "locateMethod":"org.sample.Example#method(int)",
                "variableName":"nums[2]",
                "actualValue":"13",
                "isPrimitive":true
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
        SuspiciousLocalVariable actual = SuspiciousVariableMapper.fromJson(json);
        assertEquals(expected, actual);

    }

    @Test
    void fromJsonArray() {
        String json = """
                [
                    {
                        "failedTest":"org.sample.ExampleTest#test1()",
                        "locateMethod":"org.sample.Example#method(int)",
                        "variableName":"nums[2]",
                        "actualValue":"13",
                        "isPrimitive":true
                    },
                    {
                        "failedTest":"org.sample.ExampleTest#test2()",
                        "locateMethod":"org.sample.Example#calc(int)",
                        "variableName":"answer",
                        "actualValue":"5",
                        "isPrimitive":true
                    },
                ]
        """;
        List<SuspiciousLocalVariable> expected = List.of(
                new SuspiciousLocalVariable(
                        new MethodElementName("org.sample.ExampleTest#test1()"),
                        new MethodElementName("org.sample.Example#method(int)"),
                        "nums",
                        "13",
                        true,
                        2
                ),
                new SuspiciousLocalVariable(
                        new MethodElementName("org.sample.ExampleTest#test2()"),
                        new MethodElementName("org.sample.Example#calc(int)"),
                        "answer",
                        "5",
                        true
                )
        );
        List<SuspiciousLocalVariable> actual = SuspiciousVariableMapper.fromJsonArray(json);
        assertEquals(expected, actual);
    }
}