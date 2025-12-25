package jisd.fl.ranking;

import jisd.fl.ranking.report.ScoreUpdateReport;

import java.util.List;
import java.util.Optional;

class ScoreAdjuster {

    /**
     * rankingに対して、adjustmentsで指定された全要素の疑惑値倍率を適用する
     *
     * @param ranking     更新対象のFLRanking
     * @param adjustments 調整項目（要素＋倍率）のリスト
     */
    public static void applyAll(FLRanking ranking, List<ScoreAdjustment> adjustments) {
        ScoreUpdateReport report = new ScoreUpdateReport();
        for (ScoreAdjustment adj : adjustments) {
            Optional<FLRankingElement> target = ranking.searchElement(adj.getElement());
            if(target.isEmpty()) continue;
            report.recordChange(target.get());
            target.get().sbflScore *= adj.getMultiplier();
        }
        report.print();
        ranking.sort();
    }
}
