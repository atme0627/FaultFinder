package jisd.fl.infra.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.susp.SuspiciousFieldVariable;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.core.entity.susp.SuspiciousVariable;
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
public class JavaParserValueChangingLineFinder {
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

        // 1) スコープに応じて Node を取得（宣言検索と Assign/Unary 検索で共有）
        Node node;
        try {
            node = (v instanceof SuspiciousLocalVariable local) ?
                    JavaParserUtils.extractBodyOfMethod(local.locateMethod())
                    : JavaParserUtils.parseClass(locate);
        } catch (NoSuchFileException e) {
            throw new RuntimeException(e);
        }
        if (node == null) return ranges;

        // 0-a) ローカル変数の宣言行 — VariableDeclarator の全範囲を使用
        if (v instanceof SuspiciousLocalVariable) {
            node.findAll(VariableDeclarator.class).stream()
                    .filter(vd -> vd.getNameAsString().equals(v.variableName()))
                    .forEach(vd -> vd.getRange().ifPresent(r ->
                            ranges.add(new LineRange(r.begin.line, r.end.line))));
        // 0-b) フィールドの宣言行
        } else {
            for (int ln : JavaParserUtils.findFieldVariableDeclarationLine(locate, v.variableName())) {
                ranges.add(new LineRange(ln, ln));
            }
        }

        // 2) 代入
        for (AssignExpr ae : node.findAll(AssignExpr.class)) {
            if (!matchesTarget(v, ae.getTarget())) continue;
            ae.getRange().ifPresent(r -> ranges.add(new LineRange(r.begin.line, r.end.line)));
        }

        // 3) ++ / --
        for (UnaryExpr ue : node.findAll(UnaryExpr.class, JavaParserValueChangingLineFinder::isIncDec)) {
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
     *
     * TODO: static フィールドへの代入（例: `f = 1` や `ClassName.f = 1`）は
     *       FieldAccessExpr ではなく NameExpr として解析されるため、
     *       現在の実装では検出されない。static フィールドのサポートが必要な場合は
     *       NameExpr でもフィールド名と一致するか確認するロジックの追加が必要。
     */
    private static boolean matchesTarget(SuspiciousVariable v, Expression target) {
        String name = target.isNameExpr() ? target.asNameExpr().getNameAsString()
                : target.isFieldAccessExpr() ? target.asFieldAccessExpr().getNameAsString()
                : target.isArrayAccessExpr() ? target.asArrayAccessExpr().getName().toString()
                : null;

        if(name == null || !name.equals(v.variableName())) return false;
        boolean expectFieldAccess = switch (v) {
            case SuspiciousFieldVariable _ -> true;
            case SuspiciousLocalVariable _ -> false;
        };
        return expectFieldAccess == target.isFieldAccessExpr();
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
