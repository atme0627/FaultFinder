package experiment.main;

import experiment.defect4j.Defects4jUtil;
import jisd.fl.probe.ProbeEx;
import jisd.fl.probe.ProbeExResult;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;

public class probeEx {
    static String project = "Math";
    static int bugId = 87;
    static String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";

    static String dir = "src/main/resources/probeExResult/Math/" + project + bugId + "_buggy";
    static String fileName = testMethodName.split("#")[1];
    static String locateClass = "org.apache.commons.math.optimization.RealPointValuePair";
    static String variableName = "point";
    static String actual = "0.0";

    static VariableInfo probeVariable = new VariableInfo(
            locateClass,
            variableName,
            false,
            true,
            true,
            0,
            actual,
            null
    );

    static FailedAssertInfo fai = new FailedAssertEqualInfo(
            testMethodName,
            actual,
            probeVariable);

    public static void main(String[] args){
        Defects4jUtil.changeTargetVersion(project, bugId);
        ProbeEx prbEx = new ProbeEx(fai);
        ProbeExResult per = prbEx.run(3000);
        per.generateJson(dir, fileName);
    }
}
