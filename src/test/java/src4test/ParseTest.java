package src4test;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import org.junit.jupiter.api.Test;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.Statement;

public class ParseTest {
    @Test
    void assertEqualParseTest() {
        String source = "assertEqual(10.0, sum);";
        Statement stmt = StaticJavaParser.parseStatement(source);
        MethodCallExpr methodCallExpr = new MethodCallExpr();
        methodCallExpr = stmt.findAll(methodCallExpr.getClass()).get(0);
        SimpleName methodName = methodCallExpr.getName();
        NodeList<Expression> args = methodCallExpr.getArguments();
        for (Expression arg : args) {
            System.out.println(arg.toString());
            System.out.println(arg.getMetaModel().getTypeName());
            System.out.println("--------------");
        }
    }

    @Test
    void assertTrueParseTest () {
        String source = "assertTrue(sum >= 10);";
        Statement stmt = StaticJavaParser.parseStatement(source);
        MethodCallExpr methodCallExpr = new MethodCallExpr();
        methodCallExpr = stmt.findAll(methodCallExpr.getClass()).get(0);
        SimpleName methodName = methodCallExpr.getName();
        NodeList<Expression> args = methodCallExpr.getArguments();
        for (Expression arg : args) {
            System.out.println(arg.toString());
            System.out.println(arg.getMetaModel().getTypeName());
            System.out.println("--------------");
        }
    }
}