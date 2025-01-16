package jisd.fl.probe;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.FileUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class ProbeExTest {
    String project = "Math";
    int bugId = 1;

    String testMethodName = "org.apache.commons.math3.fraction.BigFractionTest#testDigitLimitConstructor()";
    String locate = "org.apache.commons.math3.fraction.BigFraction#BigFraction(double, double, int, int)";
    String variableName = "p2";
    boolean isPrimitive = true;
    boolean isField = false;
    boolean isArray = false;
    int arrayNth = -1;
    String actual = "2499999794";


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

        pr.generateJson(dir, fileName);

        Path src = Paths.get("src/test/java/jisd/fl/probe/ProbeExTest.java");
        Path target = Paths.get(dir + "/" + fileName + ".java_data");
        FileUtil.initFile(dir, fileName + ".java_data");
        try {
            Files.copy(src, target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}