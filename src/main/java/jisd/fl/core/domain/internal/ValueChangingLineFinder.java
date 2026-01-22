package jisd.fl.core.domain.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.infra.javaparser.JavaParserUtils;

import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「値が変わりうる行」を静的に推定する。
 *
 * - Cause用: 変更イベントの代表行（基本は begin 行）のみ
 * - BP用: 変更イベントの begin..end を展開（複数行式の保険）
 */
public class ValueChangingLineFinder {
    /** 互換: 従来 find は BP用（範囲展開）として扱う */
    public static List<Integer> find(SuspiciousVariable v) {
        return findBreakpointLines(v);
    }

    /** Cause用: 変更イベントごとの代表行（begin）だけ返す */
    public static List<Integer> findCauseLines(SuspiciousVariable v) {
        List<LineRange> ranges = collectMutationRanges(v);

        List<Integer> out = new ArrayList<>();
        for (LineRange r : ranges) out.add(r.begin);

        return out.stream().distinct().sorted().toList();
    }

    /** BP用: 変更イベントの begin..end をすべて展開して返す */
    public static List<Integer> findBreakpointLines(SuspiciousVariable v) {
        List<LineRange> ranges = collectMutationRanges(v);

        Set<Integer> out = new HashSet<>();
        for (LineRange r : ranges) {
            for (int ln = r.begin; ln <= r.end; ln++) out.add(ln);
        }
        return out.stream().sorted().toList();
    }

    private static List<LineRange> collectMutationRanges(SuspiciousVariable v) {
        ClassElementName locate = v.locateClass();
        List<LineRange> ranges = new ArrayList<>();

        // 0) ローカル変数の宣言行（フィールドなら空の想定）
        if(v instanceof SuspiciousLocalVariable localVariable) {
            for (int ln : JavaParserUtils.findLocalVariableDeclarationLine(localVariable.locateMethod(), v.variableName())) {
                ranges.add(new LineRange(ln, ln));
            }
        }

        // 1) スコープに応じて Assign/Unary を収集
        Node node;
        List<AssignExpr> assigns;
        List<UnaryExpr> unaries;
        try {
            node = (v instanceof SuspiciousLocalVariable local) ?
                    JavaParserUtils.extractBodyOfMethod(local.locateMethod())
                    : JavaParserUtils.parseClass(locate);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        if (node == null) return ranges;
        assigns = node.findAll(AssignExpr.class);
        unaries = node.findAll(UnaryExpr.class, ValueChangingLineFinder::isIncDec);

        // 2) 代入
        for (AssignExpr ae : assigns) {
            if (!matchesTarget(v, ae.getTarget())) continue;
            ae.getRange().ifPresent(r -> ranges.add(new LineRange(r.begin.line, r.end.line)));
        }

        // 3) ++ / --
        for (UnaryExpr ue : unaries) {
            if (!matchesTarget(v, ue.getExpression())) continue;
            ue.getRange().ifPresent(r -> ranges.add(new LineRange(r.begin.line, r.end.line)));
        }

        return ranges;
    }

    private static boolean isIncDec(UnaryExpr n) {
        UnaryExpr.Operator op = n.getOperator();
        return op == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                op == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                op == UnaryExpr.Operator.PREFIX_DECREMENT ||
                op == UnaryExpr.Operator.PREFIX_INCREMENT;
    }

    /**
     * 対象変数への書き込みかどうかを判定
     * - a[0] = ... は name 部分(a)で一致
     * - this.f = ... は field名で一致
     */
    private static boolean matchesTarget(SuspiciousVariable v, Expression target) {
        String name = target.isNameExpr() ? target.asNameExpr().getNameAsString()
                : target.isFieldAccessExpr() ? target.asFieldAccessExpr().getNameAsString()
                : target.isArrayAccessExpr() ? target.asArrayAccessExpr().getName().toString()
                : null;

        if(name == null || !name.equals(v.variableName())) return false;
        return (v.isField() ==  target.isFieldAccessExpr());
    }

    private record LineRange(int begin, int end) {
        LineRange {
            if (begin > end) {
                int tmp = begin;
                begin = end;
                end = tmp;
            }
        }
    }
}
