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
    String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint()";
    String locateClass = "org.apache.commons.math.optimization.RealPointValuePair";
    String variableName = "point";
    String variableType = "double[]";
    String actual = "0.0";

    VariableInfo probeVariable = new VariableInfo(
            locateClass,
            variableName,
            false,
            true,
            true,
            0,
            actual,
            null
    );

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testMethodName,
            actual,
            probeVariable);


    @Test
    void runTest() {
        ProbeEx prbEx = new ProbeEx(fai);
        ProbeExResult pr = prbEx.run(3000);
        pr.print();
    }

    @Test
    void debug(){
        String locate = "org.apache.commons.math.optimization.linear.AbstractLinearOptimizer";
        String variableName = "restrictToNonNegative";
        String actual = "false";

        VariableInfo probeVariable = new VariableInfo(
                locate,
                variableName,
                true,
                true,
                false,
                -1,
                actual,
                null
        );

        FailedAssertInfo fai = new FailedAssertEqualInfo(
                testMethodName,
                actual,
                probeVariable);

    ProbeEx prbEx = new ProbeEx(fai);
    ProbeExResult pr = prbEx.run(4000);
    }
}


