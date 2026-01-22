package jisd.fl.core.domain.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
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
    public static List<Integer> find(SuspiciousLocalVariable v) {
        return findBreakpointLines(v);
    }

    /** Cause用: 変更イベントごとの代表行（begin）だけ返す */
    public static List<Integer> findCauseLines(SuspiciousLocalVariable v) {
        List<LineRange> ranges = collectMutationRanges(v);

        List<Integer> out = new ArrayList<>();
        for (LineRange r : ranges) out.add(r.begin);

        return out.stream().distinct().sorted().toList();
    }

    /** BP用: 変更イベントの begin..end をすべて展開して返す */
    public static List<Integer> findBreakpointLines(SuspiciousLocalVariable v) {
        List<LineRange> ranges = collectMutationRanges(v);

        Set<Integer> out = new HashSet<>();
        for (LineRange r : ranges) {
            for (int ln = r.begin; ln <= r.end; ln++) out.add(ln);
        }
        return out.stream().sorted().toList();
    }

    private static List<LineRange> collectMutationRanges(SuspiciousLocalVariable v) {
        MethodElementName locate = v.locateMethod();
        List<LineRange> ranges = new ArrayList<>();

        // 0) ローカル変数の宣言行（フィールドなら空の想定）
        for (int ln : JavaParserUtils.findLocalVariableDeclarationLine(locate, v.variableName())) {
            ranges.add(new LineRange(ln, ln));
        }

        // 1) スコープに応じて Assign/Unary を収集
        List<AssignExpr> assigns;
        List<UnaryExpr> unaries;
        if (v.isField()) {
            try {
                CompilationUnit unit = JavaParserUtils.parseClass(locate.classElementName);
                assigns = unit.findAll(AssignExpr.class);
                unaries = unit.findAll(UnaryExpr.class, ValueChangingLineFinder::isIncDec);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
        } else {
            BlockStmt body;
            try {
                body = JavaParserUtils.extractBodyOfMethod(locate);
            } catch (NoSuchFileException e) {
                throw new RuntimeException(e);
            }
            if (body == null) return ranges;

            assigns = body.findAll(AssignExpr.class);
            unaries = body.findAll(UnaryExpr.class, ValueChangingLineFinder::isIncDec);
        }

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
    private static boolean matchesTarget(SuspiciousLocalVariable v, Expression target) {
        String name = target.isNameExpr() ? target.asNameExpr().getNameAsString()
                : target.isFieldAccessExpr() ? target.asFieldAccessExpr().getNameAsString()
                : target.isArrayAccessExpr() ? target.asArrayAccessExpr().getName().toString()
                : target.toString(); // fallback（最後の手段）

        return name.equals(v.variableName());
    }

    private record LineRange(int begin, int end) {}
}
