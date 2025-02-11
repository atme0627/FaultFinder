package jisd.fl.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.Statement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserUtilTest {

    @Test
    void isConstructorTest() {
        String methodName = "org.apache.commons.math.optimization.RealPointValuePair#RealPointValuePair(double[], double)";
        Assertions.assertTrue(JavaParserUtil.isConstructor(methodName));
    }

    @Test
    void debug(){
        String src = "this.coefficient = 1;";
        Statement stmt = StaticJavaParser.parseStatement(src);
        System.out.println(stmt.asExpressionStmt().getExpression().asAssignExpr().getTarget().getClass());

        stmt.walk((n) -> {
            System.out.println(n.toString());
            System.out.println(n.getClass().toString());
            System.out.println();
        });

    }
}