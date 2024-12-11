package jisd.fl.probe;

import com.github.javaparser.ast.Node;
import jisd.debug.Debugger;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

class ProbeExTest {
    String targetSrcDir = PropertyLoader.getProperty("d4jTargetSrcDir");
    String testSrcDir = PropertyLoader.getProperty("d4jTestSrcDir");
    String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
    String testMethodName = "org.apache.commons.math.optimization.linear.SimplexSolverTest#testSingleVariableAndConstraint";
    String variableName = "solution";
    String typeName = "org.apache.commons.math.optimization.RealPointValuePair";
    String fieldName = "point";
    String fieldType = "double[]";
    String actual = "0.0";

    FailedAssertInfo fai = new FailedAssertEqualInfo(
            testClassName,
            testMethodName,
            actual,
            null);

    @Test
    void runD4jTest() {
        ProbeEx prb = new ProbeEx(fai);
        ProbeResult result = prb.run(2000);
        System.out.println(result.getProbeMethod());
    }

    @Test
    void tmpTest(){
        Debugger dbg = TestUtil.testDebuggerFactory(testMethodName);
        dbg.setMain(testClassName);
        dbg.stopAt(75);
        dbg.run(2000);
        dbg.locals();
    }

    void printNodeInfo(Node node, int offset){
        String indent = "    ".repeat(Math.max(0, offset));

        System.out.println(indent + node);
        System.out.println(indent + node.getMetaModel().getTypeName());
    }

    void printTreeInfo(Node root, int offset){
        printNodeInfo(root, offset);
        List<Node> child = root.getChildNodes();
        if(child.isEmpty()) return;
        for(Node n : child){
            printTreeInfo(n, offset + 1);
        }
    }
}