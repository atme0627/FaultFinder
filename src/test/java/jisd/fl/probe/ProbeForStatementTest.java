package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.info.ProbeExResult;
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
        SuspiciousVariable vi = new SuspiciousVariable(
                new CodeElementName("org.sample.CalcTest#methodCall1()"),
                "org.sample.util.Calc#methodCalling(int, int)",
                "result",
                "11",
                true,
                false
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo("org.sample.CalcTest#methodCall1()", vi);
        ProbeForStatement pfs = new ProbeForStatement(fai);
        ProbeExResult pr = pfs.run(2000);
        pr.print();
    }
}