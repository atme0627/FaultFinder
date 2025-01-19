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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

class ProbeExTest {
    String project = "Math";
    int bugId = 105;

    String testClassName = "org.apache.commons.math.stat.regression.SimpleRegressionTest";
    String shortTestMethodName = "testSSENonNegative";

    String testMethodName = testClassName + "#" + shortTestMethodName + "()";

    String variableName = "tmp";
    boolean isPrimitive = true;
    boolean isField = false;
    boolean isArray = false;
    int arrayNth = -1;
    String actual = "-7.105427357601002E-15";
    String locate ="org.apache.commons.math.stat.regression.SimpleRegression#getSumSquaredErrors()";




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
;

        if(!FileUtil.isExist("src/main/resources/probeExResult/Math",  project + bugId + "_buggy")){
            FileUtil.createDirectory("src/main/resources/probeExResult/Math/" +  project + bugId + "_buggy");
        }
        pr.save(dir, fileName);
        Path src = Paths.get("src/test/java/jisd/fl/probe/ProbeExTest.java");
        Path target = Paths.get(dir + "/" + fileName + ".java_data");
        try {
            Files.copy(src, target, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}


