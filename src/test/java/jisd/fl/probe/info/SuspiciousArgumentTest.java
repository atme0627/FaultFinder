package jisd.fl.probe.info;

import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.CodeElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuspiciousArgumentTest {
    @BeforeEach
    void initProperty() {
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
    }

    @Test
    void searchSuspiciousArgument() {
        CodeElementName calleeMethodName = new CodeElementName("org.sample.util.Calc#methodCalling(int, int)");
        SuspiciousVariable suspVar = new SuspiciousVariable(
                new CodeElementName("org.sample.CalcTest#methodCall1()"),
                "org.sample.util.Calc#methodCalling(int, int)",
                "y",
                "3",
                true,
                false
        );

        SuspiciousArgument suspArg = SuspiciousArgument.searchSuspiciousArgument(calleeMethodName, suspVar);
        System.out.println(suspArg);
        System.out.println("expr: " + suspArg.expr);
    }
}