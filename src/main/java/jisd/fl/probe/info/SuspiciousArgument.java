package jisd.fl.probe.info;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.util.analyze.CodeElementName;
import jisd.fl.util.analyze.JavaParserUtil;

import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.NoSuchElementException;

public class SuspiciousArgument extends SuspiciousExpression {
    //何番目の引数に与えられたexprかを指定
    private final int argIndex;
    protected SuspiciousArgument(CodeElementName failedTest, CodeElementName locateClass, int locateLine, String actualValue, int argIndex) {
        super(failedTest, locateClass, locateLine, actualValue);
        this.argIndex = argIndex;
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
            return stmt.findFirst(MethodCallExpr.class).orElseThrow()
                    .getArguments().get(argIndex);
        } catch (NoSuchElementException | IndexOutOfBoundsException e){
            throw new RuntimeException("Cannot extract expression from [" + locateClass + ":" + locateLine + "].");
        }
    }
}
