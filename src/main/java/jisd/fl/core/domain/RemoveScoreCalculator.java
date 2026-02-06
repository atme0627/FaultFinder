package jisd.fl.core.domain;

import jisd.fl.core.entity.FLRanking;
import jisd.fl.core.entity.element.CodeElementIdentifier;

import java.util.HashMap;
import java.util.Map;

/**
 * remove操作用のスコア計算器。
 * 対象要素のスコアを0にし、隣接要素のスコアにremoveConstを乗算する。
 */
public class RemoveScoreCalculator {
    private final double removeConst;

    public RemoveScoreCalculator(double removeConst) {
        this.removeConst = removeConst;
    }

    /**
     * 対象要素を0にし、隣接要素にremoveConstを乗算する。
     * @param target 対象要素
     * @param ranking 更新対象のランキング
     */
    public void apply(CodeElementIdentifier<?> target, FLRanking ranking) {
        Map<CodeElementIdentifier<?>, Double> newScores = new HashMap<>();
        newScores.put(target, 0.0);
        for (CodeElementIdentifier<?> neighbor : ranking.getNeighborsOf(target)) {
            double current = ranking.getScore(neighbor);
            newScores.put(neighbor, current * removeConst);
        }
        ranking.setScores(newScores);
    }
}
