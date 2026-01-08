package jisd.fl.probe.info;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.NoSuchElementException;
import java.util.Optional;

public class JavaParserSuspAssign {
    public static Expression extractExprAssign(boolean deleteParentNode, Statement stmt) {
        try {
            Expression result = JavaParserSuspAssign.extractExpressionFromStatement(stmt);
            return JavaParserSuspAssign.finalizeResult(result, deleteParentNode);
        } catch (NoSuchElementException e) {
            throw new RuntimeException(
                    String.format("Cannot extract expression from [%s].", stmt));
        }
    }

    static Expression extractExpressionFromStatement(Statement stmt) {
        //更新式は1つであると仮定
        if (stmt instanceof ForStmt forStmt) {
            return forStmt.getUpdate().getFirst().get();
        }
        // Try to extract from assignment expression
        Optional<AssignExpr> assignExpr = stmt.findFirst(AssignExpr.class);
        if (assignExpr.isPresent()) {
            return JavaParserSuspAssign.extractFromAssignExpr(assignExpr.get());
        }

        // Try to extract from variable declaration
        Optional<VariableDeclarationExpr> vdExpr = stmt.findFirst(VariableDeclarationExpr.class);
        if (vdExpr.isPresent()) {
            // 代入文がひとつであると仮定
            return JavaParserSuspAssign.extractFromVariableDeclaration(vdExpr.get());
        }

        // Try to extract from unary expression
        Optional<UnaryExpr> unaryExpr = stmt.findFirst(UnaryExpr.class);
        if (unaryExpr.isPresent()) {
            return unaryExpr.get().getExpression();
        }

        throw new RuntimeException(
                String.format("Cannot extract expression from [%s].", stmt));
    }

    static Expression extractFromAssignExpr(AssignExpr assignExpr) {
        return assignExpr.getOperator() == AssignExpr.Operator.ASSIGN
                ? assignExpr.getValue()
                : assignExpr;
    }

    static Expression extractFromVariableDeclaration(VariableDeclarationExpr vdExpr) {
        // 代入文がひとつであると仮定
        VariableDeclarator var = vdExpr.getVariable(0);
        return var.getInitializer().orElseThrow();
    }

    static Expression finalizeResult(Expression result, boolean deleteParentNode) {
        if (!deleteParentNode) {
            return result;
        }
        Expression clonedResult = result.clone();
        clonedResult.setParentNode(null);
        return clonedResult;
    }
}
