package jisd.fl.probe.info;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.javaparser.JavaParserExpressionExtractor;

import java.util.ArrayList;
import java.util.List;

public class JavaParserSuspArg {

    //対象の引数の演算の前に何回メソッド呼び出しが行われるかを計算する。
    //まず、stmtでのメソッド呼び出しをJava の実行時評価順でソートしたリストを取得
    //メソッドの呼び出し順に探し、子にtargetExprを持つものがあったら、その時のindexが求めたい値
    public static int getCallCountBeforeTargetArgEval(Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName){
        List<Expression> calls = new ArrayList<>();
        Expression targetExpr = JavaParserExpressionExtractor.extractExprArg(false, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
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
