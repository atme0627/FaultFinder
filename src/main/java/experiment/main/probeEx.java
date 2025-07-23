package experiment.main;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.probe.SimpleProbe;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.util.FileUtil;
import jisd.fl.util.analyze.MethodElementName;
//import org.junit.jupiter.api.Test;

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

    SuspiciousVariable probeVariable =
            isArray ?
            new SuspiciousVariable(
                    new MethodElementName(testMethodName),
                    locate,
                    variableName,
                    actual,
                    isPrimitive,
                    isField,
                    arrayNth
            )
            :
            new SuspiciousVariable(
            new MethodElementName(testMethodName),
            locate,
            variableName,
            actual,
            isPrimitive,
            isField
            );

    //@Test
    void runTest() {
        Defects4jUtil.changeTargetVersion(project, bugId);
        SimpleProbe prbEx = new SimpleProbe(probeVariable);
        SuspiciousExpression root = prbEx.run(5000);
        //pr.print();

        if(!FileUtil.isExist("src/main/resources/probeExResult/Math",  project + bugId + "_buggy")){
            FileUtil.createDirectory("src/main/resources/probeExResult/Math/" +  project + bugId + "_buggy");
        }
        //pr.save(dir, fileName);
        Path src = Paths.get("src/test/java/jisd/fl/probe/ProbeExTest.java");
        Path target = Paths.get(dir + "/" + fileName + ".java_data");
        try {
            Files.copy(src, target, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}


