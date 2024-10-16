package probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;

public class FailedAssertInfoFactory {
    public FailedAssertInfoFactory() {
    }

    public FailedAssertInfo create(String assertLine, String actual, String srcDir, String binDir, String testClassName, String testMethodName, int line) {
        //parse statement
        Statement assertStmt = StaticJavaParser.parseStatement(assertLine);
        MethodCallExpr methodCallExpr = new MethodCallExpr();

        methodCallExpr = assertStmt.findAll(methodCallExpr.getClass()).get(0);
        String methodName = methodCallExpr.getName().getIdentifier();
        NodeList<Expression> args = methodCallExpr.getArguments();

        switch(methodName) {
            case "assertEquals":
                return createFailedAssertEqualInfo(args, actual, srcDir, binDir, testClassName, testMethodName, line);
            default:
                throw new IllegalArgumentException("Unsupported assertType: " + methodName);
        }
    }

    // void assertEquals(Object expected, Object actual) のみ想定　
    private FailedAssertEqualInfo createFailedAssertEqualInfo(NodeList<Expression> args, String actual, String srcDir, String binDir, String testClassName, String testMethodName, int line){
        String variableName = getVariableNameFromArgs(args);
        String expected = getLiteralFromArgs(args);
        return new FailedAssertEqualInfo(variableName, expected, actual, srcDir, binDir, testClassName, testMethodName, line);
    }

    private String getVariableNameFromArgs(NodeList<Expression> args){
        for(Expression arg : args){
            if(arg.isNameExpr()){
                return arg.asNameExpr().getNameAsString();
            }
        }
        throw new IllegalArgumentException("Invalid assert args (No variable).");
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
