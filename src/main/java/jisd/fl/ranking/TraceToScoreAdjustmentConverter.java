package jisd.fl.ranking;

import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.core.entity.CodeElementIdentifier;

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

    public Map<CodeElementIdentifier, Double> toAdjustments(SuspiciousExpression root) {
        // ノードごとの最小深さを保持するマップ
        Map<CodeElementIdentifier, Integer> minDepth   = new HashMap<>();
        Queue<SuspiciousExpression>   queue      = new LinkedList<>();
        Queue<Integer>                depths     = new LinkedList<>();

        queue.add(root);
        depths.add(1);

        while (!queue.isEmpty()) {
            SuspiciousExpression suspExpr = queue.poll();
            int depth = depths.poll();

            CodeElementIdentifier elem = suspExpr.convertToCodeElementName(g);

            // 最小深さをマージ
            minDepth.merge(elem, depth, Math::min);

            for (SuspiciousExpression child : suspExpr.getChildren()) {
                queue.add(child);
                depths.add(depth + 1);
            }
        }

        // ScoreAdjustment のリスト化
        Map<CodeElementIdentifier, Double> adjustments = new HashMap<>();
        for (var entry : minDepth.entrySet()) {
            double multiplier = 1 + Math.pow(baseFactor, entry.getValue());
            adjustments.put(entry.getKey(), multiplier);
        }
        return adjustments;
    }
}
