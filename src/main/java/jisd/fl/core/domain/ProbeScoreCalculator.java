package jisd.fl.core.domain;

import jisd.fl.core.entity.FLRanking;
import jisd.fl.core.entity.element.CodeElementIdentifier;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.sbfl.Granularity;
import jisd.fl.core.entity.susp.CauseTreeNode;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * CauseTreeNodeから深さベースでスコア乗数を計算し、ランキングに適用する。
 * 各ノードの深さ (root=1) をもとに multiplier = 1 + Math.pow(baseFactor, depth) を計算する。
 */
public class ProbeScoreCalculator {
    private final double baseFactor;
    private final Granularity granularity;

    /**
     * @param baseFactor 0<baseFactor<=1 の定数
     * @param granularity ランキング要素の粒度
     */
    public ProbeScoreCalculator(double baseFactor, Granularity granularity) {
        if (baseFactor <= 0.0 || baseFactor > 1.0) {
            throw new IllegalArgumentException("baseFactor must be in (0,1]");
        }
        this.baseFactor = baseFactor;
        this.granularity = granularity;
    }

    /**
     * CauseTreeNodeを探索し、深さに基づいてスコアを更新する。
     * @param causeTree 原因追跡ツリー
     * @param ranking 更新対象のランキング
     */
    public void apply(CauseTreeNode causeTree, FLRanking ranking) {
        Map<CodeElementIdentifier<?>, Double> multipliers = calculateMultipliers(causeTree);

        Map<CodeElementIdentifier<?>, Double> newScores = new HashMap<>();
        for (var entry : multipliers.entrySet()) {
            double current = ranking.getScore(entry.getKey());
            newScores.put(entry.getKey(), current * entry.getValue());
        }
        ranking.setScores(newScores);
    }

    /**
     * CauseTreeNodeを探索し、各要素の深さに基づく乗数を計算する。
     */
    Map<CodeElementIdentifier<?>, Double> calculateMultipliers(CauseTreeNode root) {
        Map<CodeElementIdentifier<?>, Integer> minDepth = new HashMap<>();
        Queue<CauseTreeNode> queue = new LinkedList<>();
        Queue<Integer> depths = new LinkedList<>();

        queue.add(root);
        depths.add(1);

        while (!queue.isEmpty()) {
            CauseTreeNode node = queue.poll();
            int depth = depths.poll();

            if (node.locateMethod() == null) {
                // locationがnullのノード（ルート）はスキップ
                for (CauseTreeNode child : node.children()) {
                    queue.add(child);
                    depths.add(depth);
                }
                continue;
            }

            CodeElementIdentifier<?> elem = convertToCodeElementName(node.locateMethod(), node.locateLine());

            // 最小深さをマージ
            minDepth.merge(elem, depth, Math::min);

            for (CauseTreeNode child : node.children()) {
                queue.add(child);
                depths.add(depth + 1);
            }
        }

        Map<CodeElementIdentifier<?>, Double> multipliers = new HashMap<>();
        for (var entry : minDepth.entrySet()) {
            double multiplier = 1 + Math.pow(baseFactor, entry.getValue());
            multipliers.put(entry.getKey(), multiplier);
        }
        return multipliers;
    }

    private CodeElementIdentifier<?> convertToCodeElementName(MethodElementName locateMethod, int locateLine) {
        return switch (granularity) {
            case LINE -> locateMethod.toLineElementName(locateLine);
            case METHOD, CLASS -> locateMethod;
        };
    }
}
