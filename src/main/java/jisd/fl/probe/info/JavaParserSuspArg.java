package jisd.fl.probe.info;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import jisd.fl.core.entity.MethodElementName;

import java.util.ArrayList;
import java.util.List;

public class JavaParserSuspArg {
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
    static boolean isAssert(Statement stmt){
        return stmt.toString().startsWith("assert");
    }

    //対象の引数の演算の前に何回メソッド呼び出しが行われるかを計算する。
    //まず、stmtでのメソッド呼び出しをJava の実行時評価順でソートしたリストを取得
    //メソッドの呼び出し順に探し、子にtargetExprを持つものがあったら、その時のindexが求めたい値
    static int getCallCountBeforeTargetArgEval(Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName){
        List<Expression> calls = new ArrayList<>();
        Expression targetExpr = extractExprArg(false, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
        stmt.accept(new EvalOrderVisitor(), calls);
        for(Expression call : calls){
            if(!call.findAll(Node.class, anc -> anc == targetExpr).isEmpty()){
                return calls.indexOf(call) + 1;
            }
        }


        throw new RuntimeException("Something is wrong. (stmt: " + stmt + ", callCountAfterTargetInLine: " + callCountAfterTargetInLine + ", argIndex: " + argIndex + ", calleeMethodName: " + calleeMethodName + " ) ");
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
