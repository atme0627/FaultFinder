package jisd.fl.probe.info;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.util.analyze.JavaParserUtil;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class TmpJavaParserUtils {
    static public Statement extractStmt(MethodElementName locateMethod, int locateLine) {
        try {
            return JavaParserUtil.getStatementByLine(locateMethod, locateLine).orElseThrow();
        } catch (NoSuchFileException e) {
            throw new RuntimeException("Class [" + locateMethod + "] is not found.");
        } catch (NoSuchElementException e){
            throw new RuntimeException("Cannot extract Statement from [" + locateMethod + ":" + locateLine + "].");
        }
    }

    /**
     * exprから次に探索の対象となる変数の名前を取得する。
     * exprの演算に直接用いられている変数のみが対象で、引数やメソッド呼び出しの対象となる変数は除外する。
     * @return 変数名のリスト
     */
    static List<String> extractNeighborVariableNames(Expression expr, boolean includeIndirectUsedVariable){
        return expr.findAll(NameExpr.class).stream()
                //引数やメソッド呼び出しに用いられる変数を除外
                .filter(nameExpr -> includeIndirectUsedVariable || nameExpr.findAncestor(MethodCallExpr.class).isEmpty())
                .map(NameExpr::toString)
                .collect(Collectors.toList());
    }

    /**
     * exprにメソッド呼び出しが含まれているかを判定
     */
    protected static boolean hasMethodCalling(Expression expr){
        return !expr.findAll(MethodCallExpr.class).isEmpty();
    }
}
