package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

/**
 * Java の実行時評価順で MethodCallExpr を収集する Visitor
 */
public class StatementEvalOrderVisitor extends VoidVisitorAdapter<List<Expression>> {
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
