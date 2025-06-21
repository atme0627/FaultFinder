package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.probe.info.ProbeExResult;
import jisd.fl.probe.info.ProbeResult;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.MethodElement;
import jisd.fl.util.analyze.StatementElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.util.List;

class ProbeForStatementTest {
    @BeforeEach
    void initProperty() {
        PropertyLoader.setTargetSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/main/java");
        PropertyLoader.setTestSrcDir("/Users/ezaki/IdeaProjects/Project4Test/src/test/java");
    }

    @Test
    void searchCalleeProbeTargets() {
        SuspiciousVariable vi = new SuspiciousVariable(
                new CodeElementName("org.sample.CalcTest#methodCall1()"),
                "org.sample.util.Calc#methodCalling(int, int)",
                "result",
                "11",
                true,
                false
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo("org.sample.CalcTest#methodCall1()", vi);
        CodeElementName targetFqmn = new CodeElementName("org.sample.util.Calc#methodCalling(int, int)");
        MethodElement locateMethodElement;
        try {
            locateMethodElement = MethodElement.getMethodElementByName(targetFqmn);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        StatementElement probeStmt = locateMethodElement.findStatementByLine(9).get();
        ProbeResult pr = new ProbeResult(vi.getFailedTest(), vi, probeStmt, targetFqmn);
        ProbeForStatement pfs = new ProbeForStatement(fai);
        List<SuspiciousVariable> vis = pfs.searchCalleeProbeTargets(pr);

        for(SuspiciousVariable v : vis){
            System.out.println(v.toString());
        }
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