package jisd.fl.core.domain.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.infra.javaparser.JavaParserUtils;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

public class ValueChangingLineFinder {
    //TODO: refactor
    public static List<Integer> find(SuspiciousVariable vi) {
        //代入行の特定
        //unaryExpr(ex a++)も含める
        MethodElementName locateElement = vi.getLocateMethodElement();
        List<Integer> result = new ArrayList<>();
        List<AssignExpr> aes;
        List<UnaryExpr> ues;

        //値の宣言行も含める。
        //対象の変数を定義している行を追加
        List<Integer> result1 = JavaParserUtils.findLocalVariableDeclarationLine(locateElement, vi.getSimpleVariableName());
        result.addAll(result1);

        if (vi.isField()) {
            try {
                CompilationUnit unit = JavaParserUtils.parseClass(locateElement);
                aes = unit.findAll(AssignExpr.class);
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
                bs = JavaParserUtils.extractBodyOfMethod(locateElement);
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
