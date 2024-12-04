package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.probe.assertinfo.FailedAssertEqualInfo;
import jisd.fl.probe.assertinfo.FailedAssertInfo;

public class FailedAssertInfoFactory {
    public FailedAssertInfoFactory() {
    }

    public FailedAssertInfo create(String assertLine,
                                   String actual,
                                   String srcDir,
                                   String binDir,
                                   String testClassName,
                                   String testMethodName,
                                   int line,
                                   int nthArg) {
        //parse statement
        Statement assertStmt = StaticJavaParser.parseStatement(assertLine);
        MethodCallExpr methodCallExpr = assertStmt.findAll(MethodCallExpr.class).get(0);
        String methodName = methodCallExpr.getName().getIdentifier();
        Expression arg = methodCallExpr.getArguments().get(nthArg - 1);

        switch(methodName) {
            case "assertEquals":
                return createFailedAssertEqualInfo(arg, actual, srcDir, binDir, testClassName, testMethodName, line);
            default:
                throw new IllegalArgumentException("Unsupported assertType: " + methodName);
        }
    }

    // void assertEquals(Object expected, Object actual) のみ想定　
    private FailedAssertEqualInfo createFailedAssertEqualInfo(Expression arg,
                                                              String actual,
                                                              String srcDir,
                                                              String binDir,
                                                              String testClassName,
                                                              String testMethodName,
                                                              int line){
        String variableName = arg.toString();
        //return new FailedAssertEqualInfo(coverageCollectionName, testMethodName, line, variableName,
        //        , , actual, , );
        return null;
    }
}
