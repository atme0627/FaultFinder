package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import jisd.debug.Debugger;
import jisd.fl.probe.assertinfo.FailedAssertInfo;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class NewProbeTest {

    @Test
    void getLineWithVarTest() {
        String srcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String binDir = PropertyLoader.getProperty("d4jTestBinDir");
        String testClassName = "demo.SampleTest";
        String testMethodName = "sample2()";
        int assertLineNum = 33;
        int nthArg = 1;
        String actual = "8";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, nthArg, actual);

        Debugger dbg = TestUtil.testDebuggerFactory(testClassName, testMethodName);
        NewProbe prb = new NewProbe(dbg, fai);
        List<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
    }

    @Test
    void getLineWithVarD4jTest() {
        String srcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String binDir = PropertyLoader.getProperty("d4jTestBinDir");
        String testClassName = "org.apache.commons.math.optimization.linear.SimplexSolverTest";
        String testMethodName = "testSingleVariableAndConstraint()";
        int assertLineNum = 75;
        int nthArg = 2;
        String actual = "0.0";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName, assertLineNum, nthArg, actual);

        Debugger dbg = TestUtil.testDebuggerFactory(testClassName, testMethodName);
        NewProbe prb = new NewProbe(dbg, fai);
        List<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
    }

    @Test
    void runD4jTest() {
        String srcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String binDir = PropertyLoader.getProperty("d4jTestBinDir");
        String testClassName = "org.apache.commons.math.analysis.integration.RombergIntegratorTest";
        String testMethodName = "testSinFunction";
        int assertLineNum = 53;
        int nthArg = 2;
        String actual = "-0.5000000001514787";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName + "()", assertLineNum, nthArg, actual);

        Debugger dbg = TestUtil.testDebuggerFactory(testClassName, testMethodName);
        NewProbe prb = new NewProbe(dbg, fai);
        List<Integer> lineWithVar = prb.getLineWithVar();
        System.out.println(Arrays.toString(lineWithVar.toArray()));
//        List<String> result = prb.run(2000);
 //       System.out.println(Arrays.toString(result.toArray()));
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

    @Test
    //後で消す
    void tmpTest1(){
        String assertLine = "x = A.B.add(a, b);";
        //parse statement
        Statement assertStmt = StaticJavaParser.parseStatement(assertLine);
        List<MethodCallExpr> methodCallExprs = assertStmt.findAll(MethodCallExpr.class);
        MethodCallExpr mce = methodCallExprs.get(0);

        //printTreeInfo(mce, 0);
        Optional<Expression> exp = mce.getScope();
        printNodeInfo(exp.get(), 0);
    }

    @Test
    void probeLineParserTest(){
        String srcDir = PropertyLoader.getProperty("d4jTestSrcDir");
        String binDir = PropertyLoader.getProperty("d4jTestBinDir");
        String testClassName = "org.apache.commons.math.analysis.integration.RombergIntegratorTest";
        String testMethodName = "testSinFunction";
        int assertLineNum = 53;
        int nthArg = 2;
        String actual = "-0.5000000001514787";
        AssertExtractor ae = new AssertExtractor(srcDir, binDir);
        FailedAssertInfo fai = ae.getAssertByLineNum(testClassName, testMethodName + "()", assertLineNum, nthArg, actual);

        Debugger dbg = TestUtil.testDebuggerFactory(testClassName, testMethodName);
        NewProbe prb = new NewProbe(dbg, fai);

        List<String> probeMethod = prb.probeLineParser(47);

        System.out.println(Arrays.toString(probeMethod.toArray()));
    }


}