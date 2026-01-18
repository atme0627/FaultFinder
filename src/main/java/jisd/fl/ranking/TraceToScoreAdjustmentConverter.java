package jisd.fl.ranking;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.SuspiciousExprTreeNode;
import jisd.fl.core.entity.susp.SuspiciousExpression;
import jisd.fl.core.entity.coverage.Granularity;
import jisd.fl.core.entity.element.CodeElementIdentifier;

import java.util.*;

/**
 * SuspiciousExpression の木からランキング更新リストに変換
 * 各ノードの深さ (root=1) をもとに
 * multiplier = 1 + Math.pow(baseFactor, depth) の ScoreAdjustment を作成する
 */
public class TraceToScoreAdjustmentConverter {
    private final double baseFactor;
    private final Granularity g;

    /**
     * @param baseFactor 0<baseFactor<=1 の定数
     * @param granularity ランキング要素の粒度
     */
    public TraceToScoreAdjustmentConverter(double baseFactor, Granularity granularity) {
        if (baseFactor <= 0.0 || baseFactor > 1.0) {
            throw new IllegalArgumentException("baseFactor must be in (0,1]");
        }
        this.baseFactor = baseFactor;
        this.g = granularity;
    }

    public Map<CodeElementIdentifier<?>, Double> toAdjustments(SuspiciousExprTreeNode root) {
        // ノードごとの最小深さを保持するマップ
        Map<CodeElementIdentifier<?>, Integer> minDepth   = new HashMap<>();
        Queue<SuspiciousExprTreeNode>   queue      = new LinkedList<>();
        Queue<Integer>                depths     = new LinkedList<>();

        queue.add(root);
        depths.add(1);

        while (!queue.isEmpty()) {
            SuspiciousExprTreeNode suspExprNode = queue.poll();
            SuspiciousExpression suspExpr = suspExprNode.suspExpr;
            int depth = depths.poll();

            CodeElementIdentifier<?> elem = convertToCodeElementName(new MethodElementName(suspExpr.locateMethod.toString()), suspExpr.locateLine, g);

            // 最小深さをマージ
            minDepth.merge(elem, depth, Math::min);

            for (SuspiciousExprTreeNode child : suspExprNode.childSuspExprs) {
                queue.add(child);
                depths.add(depth + 1);
            }
        }

        // ScoreAdjustment のリスト化
        Map<CodeElementIdentifier<?>, Double> adjustments = new HashMap<>();
        for (var entry : minDepth.entrySet()) {
            double multiplier = 1 + Math.pow(baseFactor, entry.getValue());
            adjustments.put(entry.getKey(), multiplier);
        }
        return adjustments;
    }

    public static CodeElementIdentifier<?> convertToCodeElementName(MethodElementName locateMethod, int locateLine, Granularity granularity){
        return switch (granularity){
            case LINE -> locateMethod.toLineElementName(locateLine);
            case METHOD, CLASS -> locateMethod;
        };
    }
}
