package jisd.fl.probe;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class ProbeExTest {
    String project = "Math";
    int bugId = 10;

    String testClassName = "org.apache.commons.math3.analysis.differentiation.DerivativeStructureTest";
    String shortTestMethodName = "testAtan2SpecialCases";

    String testMethodName = testClassName + "#" + shortTestMethodName + "()";

    String variableName = "data";
    boolean isPrimitive = true;
    boolean isField = true;
    boolean isArray = true;
    int arrayNth = 0;
    String actual = "NaN";

    String locate = "org.apache.commons.math3.analysis.differentiation.DerivativeStructure";


    String dir = "src/main/resources/probeExResult/Math/" + project + bugId + "_buggy";
    String fileName = testMethodName + "_" + variableName;

    VariableInfo probeVariable = new VariableInfo(
            locate,
            variableName,
            isPrimitive,
            isField,
            isArray,
            arrayNth,
            actual,
            null
    );

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testMethodName,
            actual,
            probeVariable);


    @Test
    void runTest() {
        Defects4jUtil.changeTargetVersion(project, bugId);
        ProbeEx prbEx = new ProbeEx(fai);
        ProbeExResult pr = prbEx.run(5000);
        pr.print();

        pr.save(dir, fileName);


        Path src = Paths.get("src/test/java/jisd/fl/probe/ProbeExTest.java");
        Path target = Paths.get(dir + "/" + fileName + ".java_data");
        try {
            Files.copy(src, target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}


