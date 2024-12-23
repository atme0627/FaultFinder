package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import jisd.debug.Debugger;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.probe.assertinfo.VariableInfo;
import jisd.fl.util.JavaParserUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.NoSuchFileException;
import java.util.List;

class ProbeExTest {
    String testMethod = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";
    String locateMethod = "org.apache.commons.math.optimization.linear.SimplexTableau#getSolution()";
    String variableName = "coefficients";
    String variableType = "double[]";
    String actual = "0.0";

    VariableInfo probeVariable = new VariableInfo(
            locateMethod,
            variableName,
            false,
            false,
            true,
            0,
            actual,
            null
    );

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testMethod,
            actual,
            probeVariable);


    @Test
    void searchNextProbeTargetTest() {
        ProbeEx prbEx = new ProbeEx(fai);
        ProbeResult pr = prbEx.run(3000);

        List<VariableInfo> vis = prbEx.searchNextProbeTargets(pr);
        for(VariableInfo vi : vis){
            System.out.println(vi.getVariableName(true, true) + ": " + vi.getActualValue());
        }
    }

    @Test
    void test(){
        String src = "                coefficients[i] =\n" +
                "                    (basicRow == null ? 0 : getEntry(basicRow, getRhsOffset())) -\n" +
                "                    (restrictToNonNegative ? 0 : mostNegative);";

        Statement stmt = StaticJavaParser.parseStatement(src);
        List<SimpleName> variableNames = stmt.findAll(SimpleName.class);
        for(SimpleName s : variableNames){
            System.out.println(s);
        }
    }
}