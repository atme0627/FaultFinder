package jisd.fl.ranking;

import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.CauseTreeNode;
import jisd.fl.core.entity.sbfl.Granularity;
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

    public Map<CodeElementIdentifier<?>, Double> toAdjustments(CauseTreeNode root) {
        // ノードごとの最小深さを保持するマップ
        Map<CodeElementIdentifier<?>, Integer> minDepth   = new HashMap<>();
        Queue<CauseTreeNode>            queue      = new LinkedList<>();
        Queue<Integer>                  depths     = new LinkedList<>();

        queue.add(root);
        depths.add(1);

        while (!queue.isEmpty()) {
            CauseTreeNode node = queue.poll();
            int depth = depths.poll();

            if (node.locateMethod() == null) {
                // root ノード（expression なし）はスキップ
                for (CauseTreeNode child : node.children()) {
                    queue.add(child);
                    depths.add(depth);
                }
                continue;
            }

            CodeElementIdentifier<?> elem = convertToCodeElementName(node.locateMethod(), node.locateLine(), g);

            // 最小深さをマージ
            minDepth.merge(elem, depth, Math::min);

            for (CauseTreeNode child : node.children()) {
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
