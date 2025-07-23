package jisd.fl.ranking;

import jisd.fl.util.analyze.CodeElementName;

import java.util.List;

class ScoreAdjuster {

    /**
     * rankingに対して、adjustmentsで指定された全要素の疑惑値倍率を適用する
     *
     * @param ranking     更新対象のFLRanking
     * @param adjustments 調整項目（要素＋倍率）のリスト
     */
    public static void applyAll(FLRanking ranking, List<ScoreAdjustment> adjustments) {
        for (ScoreAdjustment adj : adjustments) {
            FLRankingElement target = ranking.searchElement(adj.getElement()).orElseThrow();
            target.multipleSuspiciousnessScore(adj.getMultiplier());
        }
        ranking.sort();
    }
}
