package jisd.fl.probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;

public class FailedAssertInfoFactory {
    public FailedAssertInfoFactory() {
    }

    public FailedAssertInfo create(String assertLine, String actual, String srcDir, String binDir, String testClassName, String testMethodName, int line, int nthArg) {
        //parse statement
        Statement assertStmt = StaticJavaParser.parseStatement(assertLine);
        MethodCallExpr methodCallExpr = new MethodCallExpr();

        methodCallExpr = assertStmt.findAll(methodCallExpr.getClass()).get(0);
        String methodName = methodCallExpr.getName().getIdentifier();
        //NodeList<Expression> args = methodCallExpr.getArguments();
        Expression arg = methodCallExpr.getArguments().get(nthArg - 1);

        switch(methodName) {
            case "assertEquals":
                return createFailedAssertEqualInfo(arg, actual, srcDir, binDir, testClassName, testMethodName, line);
            default:
                throw new IllegalArgumentException("Unsupported assertType: " + methodName);
        }
    }

    // void assertEquals(Object expected, Object actual) のみ想定　
    private FailedAssertEqualInfo createFailedAssertEqualInfo(Expression arg, String actual, String srcDir, String binDir, String testClassName, String testMethodName, int line){
        String variableName = getVariableNameFromArgs(arg);
        return new FailedAssertEqualInfo(variableName, actual, srcDir, binDir, testClassName, testMethodName, line);
    }

    //何番目に対象の変数があるかを指定
    //変数のみに限らない
    private String getVariableNameFromArgs(Expression arg){
            return arg.toString();
    }

    //argの中にliteralは1つしかない想定
    private String getLiteralFromArgs(NodeList<Expression> args){
        for(Expression arg : args){
            if(arg.isLiteralExpr()){
                return getNumberLiteralFromLiteralExpr(arg);
            }
        }
        throw new IllegalArgumentException("There is no literal in args.");
    }

    private String getNumberLiteralFromLiteralExpr(Expression expr){
            return expr.toString();
    }
}
