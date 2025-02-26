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
    int bugId = 87;

    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String shortTestMethodName = "testSingleVariableAndConstraint";

    String testMethodName = testClassName + "#" + shortTestMethodName + "()";

    String variableName = "point";
    boolean isPrimitive = true;
    boolean isField = true;
    boolean isArray = true;
    int arrayNth = 0;
    String actual = "0.0";
    String locate ="org.apache.commons.math.optimization.RealPointValuePair";




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


