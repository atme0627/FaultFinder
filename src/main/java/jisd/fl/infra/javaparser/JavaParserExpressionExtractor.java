package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.NoSuchElementException;
import java.util.Optional;

class JavaParserExpressionExtractor {
    public static Expression extractExprAssign(boolean deleteParentNode, Statement stmt) {
        try {
            Expression result = extractAssigningExprFromStatement(stmt);
            if (!deleteParentNode) {
                return result;
            }
            Expression clonedResult = result.clone();
            clonedResult.setParentNode(null);
            return clonedResult;
        } catch (NoSuchElementException e) {
            throw new RuntimeException(
                    String.format("Cannot extract expression from [%s].", stmt));
        }
    }

    private static Expression extractAssigningExprFromStatement(Statement stmt) {
        //更新式は1つであると仮定
        if (stmt instanceof ForStmt forStmt) {
            return forStmt.getUpdate().getFirst().get();
        }
        // Try to extract from assignment expression
        Optional<AssignExpr> assignExpr = stmt.findFirst(AssignExpr.class);
        if (assignExpr.isPresent()) {
            AssignExpr assignExpr1 = assignExpr.get();
            return assignExpr1.getOperator() == AssignExpr.Operator.ASSIGN
                    ? assignExpr1.getValue()
                    : assignExpr1;
        }

        // Try to extract from variable declaration
        Optional<VariableDeclarationExpr> vdExpr = stmt.findFirst(VariableDeclarationExpr.class);
        if (vdExpr.isPresent()) {
            // 代入文がひとつであると仮定
            VariableDeclarator var = vdExpr.get().getVariable(0);
            return var.getInitializer().orElseThrow();
        }

        // Try to extract from unary expression
        Optional<UnaryExpr> unaryExpr = stmt.findFirst(UnaryExpr.class);
        if (unaryExpr.isPresent()) {
            return unaryExpr.get().getExpression();
        }

        throw new RuntimeException(
                String.format("Cannot extract expression from [%s].", stmt));
    }

}
