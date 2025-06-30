package jisd.fl.probe;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.analyze.CodeElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProbeForStatementTest {
    @BeforeEach
    void initProperty() {
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
    }

    @Test
    void runTest() {
        SuspiciousVariable target = new SuspiciousVariable(
                new CodeElementName("org.sample.CalcTest#methodCall1()"),
                "org.sample.CalcTest#methodCall1()",
                "actual",
                "11",
                true,
                false
        );

        ProbeForStatement pfs = new ProbeForStatement(target);
        SuspiciousExpression treeRoot = pfs.run(2000);
    }
}