package jisd.fl.mapper;

import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.SuspiciousVariable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuspiciousVariableMapperTest {
    @Test
    void toJson() {
        SuspiciousVariable target = new SuspiciousVariable(
                new MethodElementName("org.sample.ExampleTest#test1()"),
                "org.sample.Example#method(int)",
                "nums",
                "13",
                true,
                false,
                2
        );
        String expected = """
                {
                "failedTest":"org.sample.ExampleTest#test1()",
                "locateMethod":"org.sample.Example#method(int)",
                "variableName":"nums[2]",
                "actualValue":"13",
                "isPrimitive":true,
                "isField":false
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
                "isPrimitive":true,
                "isField":false
                }
        """;
        SuspiciousVariable expected = new SuspiciousVariable(
                new MethodElementName("org.sample.ExampleTest#test1()"),
                "org.sample.Example#method(int)",
                "nums",
                "13",
                true,
                false,
                2
        );
        SuspiciousVariable actual = SuspiciousVariableMapper.fromJson(json);
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
                        "isPrimitive":true,
                        "isField":false
                    },
                    {
                        "failedTest":"org.sample.ExampleTest#test2()",
                        "locateMethod":"org.sample.Example#calc(int)",
                        "variableName":"answer",
                        "actualValue":"5",
                        "isPrimitive":true,
                        "isField":false
                    },
                ]
        """;
        List<SuspiciousVariable> expected = List.of(
                new SuspiciousVariable(
                        new MethodElementName("org.sample.ExampleTest#test1()"),
                        "org.sample.Example#method(int)",
                        "nums",
                        "13",
                        true,
                        false,
                        2
                ),
                new SuspiciousVariable(
                        new MethodElementName("org.sample.ExampleTest#test2()"),
                        "org.sample.Example#calc(int)",
                        "answer",
                        "5",
                        true,
                        false
                )
        );
        List<SuspiciousVariable> actual = SuspiciousVariableMapper.fromJsonArray(json);
        assertEquals(expected, actual);
    }
}