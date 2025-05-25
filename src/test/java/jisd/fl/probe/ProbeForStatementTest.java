package jisd.fl.probe;

import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
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
        PropertyLoader.setProperty("targetSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/main/java");
        PropertyLoader.setProperty("testSrcDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/src/test/java");
        PropertyLoader.setProperty("testBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/main");
        PropertyLoader.setProperty("targetBinDir", "src/test/resources/jisd/fl/probe/ProbeExTest/SampleProject/build/classes/java/test");

        TestUtil.compileForDebug(new CodeElementName("sample.MethodCallTest"));
    }

    @Test
    void searchCalleeProbeTargets() {
        VariableInfo vi = new VariableInfo(
                "sample.MethodCall#methodCalling()",
                "result",
                true,
                false,
                false,
                -1,
                "11",
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo("sample.MethodCallTest#methodCall1()", vi);
        CodeElementName targetFqmn = new CodeElementName("sample.MethodCall#methodCalling(int, int)");
        MethodElement locateMethodElement;
        try {
            locateMethodElement = MethodElement.getMethodElementByName(targetFqmn);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        StatementElement probeStmt = locateMethodElement.findStatementByLine(9).get();
        ProbeResult pr = new ProbeResult(vi, probeStmt, targetFqmn);
        ProbeForStatement pfs = new ProbeForStatement(fai);
        List<VariableInfo> vis = pfs.searchCalleeProbeTargets(pr);

        for(VariableInfo v : vis){
            System.out.println(v.toInfoString());
        }
    }

    @Test
    void runTest() {
        VariableInfo vi = new VariableInfo(
                "sample.MethodCallTest#methodCall1()",
                "actual",
                true,
                false,
                false,
                -1,
                "11",
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo("sample.MethodCallTest#methodCall1()", vi);
        ProbeForStatement pfs = new ProbeForStatement(fai);
        ProbeExResult pr = pfs.run(2000);
        pr.print();
    }
}