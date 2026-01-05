package jisd.fl.probe.info;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.util.analyze.JavaParserUtil;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class TmpJavaParserUtils {
    //対象の引数の演算の前に何回メソッド呼び出しが行われるかを計算する。
    //まず、stmtでのメソッド呼び出しをJava の実行時評価順でソートしたリストを取得
    //はじめのノードから順に探し、親にexprを持つものがあったら、その時のindexが求めたい値
    static int getCallCountBeforeTargetArgEval(Statement stmt, Expression targetExpr){
        List<Expression> calls = new ArrayList<>();
        Expression targetExpr = extractExpr(false);
        stmt.accept(new SuspiciousArgument.EvalOrderVisitor(), calls);
        for(Expression call : calls){
            if(call == targetExpr || call.findAncestor(Node.class, anc -> anc == targetExpr).isPresent()){
                return calls.indexOf(call) + 1;
            }
        }
        throw new RuntimeException("Something is wrong.");
    }


    static public Statement extractStmt(MethodElementName locateMethod, int locateLine) {
        try {
            return JavaParserUtil.getStatementByLine(locateMethod, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + locateLine + "].");
        }
    }
}
