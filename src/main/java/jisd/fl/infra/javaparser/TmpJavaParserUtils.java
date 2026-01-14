package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.util.analyze.JavaParserUtil;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

class TmpJavaParserUtils {
    static public Statement extractStmt(MethodElementName locateMethod, int locateLine) {
        try {
            return JavaParserUtil.getStatementByLine(locateMethod, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + locateLine + "].");
        }
    }

    //対象の引数の演算の前に何回メソッド呼び出しが行われるかを計算する。
    //まず、stmtでのメソッド呼び出しをJava の実行時評価順でソートしたリストを取得
    //メソッドの呼び出し順に探し、子にtargetExprを持つものがあったら、その時のindexが求めたい値
    public static int getCallCountBeforeTargetArgEval(Statement stmt, int callCountAfterTargetInLine, int argIndex, MethodElementName calleeMethodName){
        List<Expression> calls = new ArrayList<>();
        Expression targetExpr = JavaParserExpressionExtractor.extractExprArg(false, stmt, callCountAfterTargetInLine, argIndex, calleeMethodName);
        stmt.accept(new StatementEvalOrderVisitor(), calls);
        for(Expression call : calls){
            if(!call.findAll(Node.class, anc -> anc == targetExpr).isEmpty()){
                return calls.indexOf(call) + 1;
            }
        }


        throw new RuntimeException("Something is wrong. (stmt: " + stmt + ", callCountAfterTargetInLine: " + callCountAfterTargetInLine + ", argIndex: " + argIndex + ", calleeMethodName: " + calleeMethodName + " ) ");
    }
}
