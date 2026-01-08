package jisd.fl.probe.info;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;

import java.util.NoSuchElementException;

public class JavaParserSuspReturn {
    public static Expression extractExprReturnValue(Statement stmt) {
        try {
            if(!stmt.isReturnStmt()) throw new NoSuchElementException();
            return stmt.asReturnStmt().getExpression().orElseThrow();
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract expression from [" + stmt + "].");
        }
    }
}
