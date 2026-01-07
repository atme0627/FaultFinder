package jisd.fl.probe.info;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;

import java.util.ArrayList;
import java.util.List;

public class ExtractExprArg {
    static public Expression extractExprArg(boolean deleteParentNode, Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName) {
        int methodCallCount = stmt.findAll(MethodCallExpr.class).size() + stmt.findAll(ObjectCreationExpr.class).size();
        if(isAssert(stmt)) methodCallCount--;
        int nthCallInLine = methodCallCount - callCountAfterTargetInLine;
        if(nthCallInLine <= 0) {
            if(nthCallInLine == 0 && isAssert(stmt)) {
                nthCallInLine = 1;
            } else {
                throw new RuntimeException("something is wrong");
            }
        }

        Expression result;
        List<Expression> calls = new ArrayList<>();
        stmt.accept(new SuspiciousArgument.EvalOrderVisitor(), calls);
        if(calls.get(nthCallInLine - 1) instanceof MethodCallExpr mce) {
            if(!mce.getNameAsString().equals(calleeMethodName.getShortMethodName())) throw new RuntimeException("something is wrong");
            result = mce.getArgument(argIndex);
        }
        else if (calls.get(nthCallInLine - 1) instanceof ObjectCreationExpr oce){
            if(!oce.getType().asString().equals(calleeMethodName.getShortMethodName())) throw new RuntimeException("something is wrong");
            result = oce.getArgument(argIndex);
        }
        else {
            throw new RuntimeException("something is wrong");
        }

        if(deleteParentNode) {
            result = result.clone();
            //親ノードの情報を消す
            result.setParentNode(null);
            return result;
        }
        return result;
    }

    //assert文はMethodExitが起きず、例外で終わることによるCallCountAfterTargetInLine
    //のずれ解消のための処置
    public static boolean isAssert(Statement stmt){
        return stmt.toString().startsWith("assert");
    }
}
