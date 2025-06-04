package jisd.fl.probe.info;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.JavaParserUtil;
import jisd.fl.util.analyze.TestMethodElement;

import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.NoSuchElementException;

public class SuspiciousReturnValue extends SuspiciousExpression {
    private final CodeElementName invokedMethodName;
    protected SuspiciousReturnValue(CodeElementName failedTest, CodeElementName locateClass, int locateLine, String actualValue, CodeElementName invokedMethodName) {
        super(failedTest, locateClass, locateLine, actualValue);
        this.invokedMethodName = invokedMethodName;
    }

    @Override
    public List<SuspiciousReturnValue> searchSuspiciousReturns() {
        return List.of();
    }

    @Override
    public List<SuspiciousVariable> neighborSuspiciousVariables() {
        return List.of();
    }

    @Override
    protected Expression extractExpr() {
        try {
            if(!stmt.isReturnStmt()) throw new NoSuchElementException();
            return stmt.asReturnStmt().getExpression().orElseThrow();
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract expression from [" + locateClass + ":" + locateLine + "].");
        }
    }

    @Override
    public String toString(){
        return "[INVOKED METHOD] " + invokedMethodName + "\n" + super.toString();
    }
}
