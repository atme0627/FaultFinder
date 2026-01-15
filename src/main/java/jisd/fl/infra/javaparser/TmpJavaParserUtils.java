package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.util.analyze.JavaParserUtil;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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

    //対象の引数の演算の前に何回メソッド呼び出しが行われるかを計算する。
    //まず、stmtでのメソッド呼び出しをJava の実行時評価順でソートしたリストを取得
    //メソッドの呼び出し順に探し、子にtargetExprを持つものがあったら、その時のindexが求めたい値
    //TODO: このへんは対象のメソッドのみをカウントするようにすればいい気がしてきた。
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

    //TODO: refactor
    public static List<Integer> valueChangingLine(SuspiciousVariable vi) {
        //代入行の特定
        //unaryExpr(ex a++)も含める
        MethodElementName locateElement = vi.getLocateMethodElement();
        List<Integer> result = new ArrayList<>();
        List<AssignExpr> aes;
        List<UnaryExpr> ues;
        if (vi.isField()) {
            try {
                aes = JavaParserUtil.extractAssignExpr(locateElement);
                CompilationUnit unit = JavaParserUtil.parseClass(locateElement);
                ues = unit.findAll(UnaryExpr.class, (n) -> {
                    UnaryExpr.Operator ope = n.getOperator();
                    return ope == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                            ope == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                            ope == UnaryExpr.Operator.PREFIX_DECREMENT ||
                            ope == UnaryExpr.Operator.PREFIX_INCREMENT;
                });
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
        } else {
            BlockStmt bs = null;
            try {
                bs = JavaParserUtil.searchBodyOfMethod(locateElement);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
            aes = bs.findAll(AssignExpr.class);
            ues = bs.findAll(UnaryExpr.class, (n) -> {
                UnaryExpr.Operator ope = n.getOperator();
                return ope == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                        ope == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                        ope == UnaryExpr.Operator.PREFIX_DECREMENT ||
                        ope == UnaryExpr.Operator.PREFIX_INCREMENT;
            });
        }

        for (AssignExpr ae : aes) {
            //対象の変数に代入されているか確認
            Expression target = ae.getTarget();
            String targetName;
            if (target.isArrayAccessExpr()) {
                targetName = target.asArrayAccessExpr().getName().toString();
            } else if (target.isFieldAccessExpr()) {
                targetName = target.asFieldAccessExpr().getName().toString();
            } else {
                targetName = target.toString();
            }

            if (targetName.equals(vi.getSimpleVariableName())) {
                if (vi.isField() == target.isFieldAccessExpr())
                    for (int i = ae.getBegin().get().line; i <= ae.getEnd().get().line; i++) {
                        result.add(i);
                    }
            }
        }
        for (UnaryExpr ue : ues) {
            //対象の変数に代入されているか確認
            Expression target = ue.getExpression();
            String targetName = target.toString();

            if (targetName.equals(vi.getSimpleVariableName())) {
                if (vi.isField() == target.isFieldAccessExpr())
                    for (int i = ue.getBegin().get().line; i <= ue.getEnd().get().line; i++) {
                        result.add(i);
                    }
            }
        }
        return result;
    }
}
