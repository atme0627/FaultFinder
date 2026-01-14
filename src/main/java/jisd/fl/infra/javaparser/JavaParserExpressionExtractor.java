package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import jisd.fl.core.entity.MethodElementName;

import java.util.ArrayList;
import java.util.List;
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

    public static Expression extractExprReturnValue(Statement stmt) {
        try {
            if(!stmt.isReturnStmt()) throw new NoSuchElementException();
            return stmt.asReturnStmt().getExpression().orElseThrow();
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract expression from [" + stmt + "].");
        }
    }

    public static Expression extractExprArg(boolean deleteParentNode, Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName) {
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
        stmt.accept(new EvalOrderVisitor(), calls);
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
    //TODO: このへんは対象のメソッドのみをカウントするようにすればいい気がしてきた。
    static boolean isAssert(Statement stmt){
        return stmt.toString().startsWith("assert");
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

    /**
     * Java の実行時評価順で MethodCallExpr を収集する Visitor
     */
    private static class EvalOrderVisitor extends VoidVisitorAdapter<List<Expression>> {
        @Override
        public void visit(MethodCallExpr mce, List<Expression> collector) {
            // 1) レシーバ（scope）があれば先に評価
            mce.getScope().ifPresent(scope -> scope.accept(this, collector));
            // 2) 引数を左から順に評価
            for (Expression arg : mce.getArguments()) {
                arg.accept(this, collector);
            }
            // 3) 最後に「呼び出しイベント」として自分自身を追加
            collector.add(mce);
        }

        @Override
        public void visit(ObjectCreationExpr oce, List<Expression> collector) {
            // 1) コンストラクタのスコープ（new Outer.Inner() の Outer など）がある場合は先に評価
            oce.getScope().ifPresent(scope -> scope.accept(this, collector));
            // 2) 引数を左から順に評価
            for (Expression arg : oce.getArguments()) {
                arg.accept(this, collector);
            }
            // 3) 匿名クラスボディ内にある式（必要なら追加）
            if (oce.getAnonymousClassBody().isPresent()) {
                oce.getAnonymousClassBody().get().forEach(body -> body.accept(this, collector));
            }

            // 3) 最後に「呼び出しイベント」として自分自身を追加
            collector.add(oce);
        }
    }
}
