package jisd.fl;

import jisd.fl.presenter.FLRankingPresenter;
import jisd.fl.probe.Probe;
import jisd.fl.core.entity.susp.SuspiciousExprTreeNode;
import jisd.fl.core.entity.FLRanking;
import jisd.fl.core.entity.FLRankingElement;
import jisd.fl.ranking.TraceToScoreAdjustmentConverter;
import jisd.fl.sbfl.Formula;
import jisd.fl.sbfl.coverage.CoverageAnalyzer;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.sbfl.coverage.CoverageOfTarget;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.core.entity.susp.SuspiciousVariable;
import jisd.fl.ranking.report.ScoreUpdateReport;
import jisd.fl.core.entity.CodeElementIdentifier;
import jisd.fl.core.entity.MethodElementName;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleFunction;
import java.util.stream.Collectors;

/**
 * テストスイートのカバレッジ情報から疑惑値ランキングを生成・操作するためのクラス。
 * CoverageCollectionを解析し、各対象要素の疑惑値を計算してFLRankingに設定します。
 * remove(), susp(), probe()によってランキングに開発者の知識を与えランキングを更新できる。
 */
public class FaultFinder {
    FLRanking flRanking;
    FLRankingPresenter presenter;
    //remove時に同じクラスの他のメソッドの疑惑値にかける定数
    protected double removeConst = 0.8;
    //susp時に同じクラスの他のメソッドの疑惑値にかける定数
    protected double suspConst = 1.2;
    //probeの疑惑値計算に使用する変数
    protected double probeLambda = 0.8;

    private final int rankingSize = 20;
    final Granularity granularity;

    public FaultFinder(MethodElementName targetTestClassName){
        this.granularity = Granularity.LINE;
        Formula f = Formula.OCHIAI;
        CoverageAnalyzer coverageAnalyzer = new CoverageAnalyzer();
        coverageAnalyzer.analyze(targetTestClassName);
        CoverageCollection sbflCoverage = coverageAnalyzer.result();
        flRanking = new FLRanking();
        presenter = new FLRankingPresenter(flRanking);
        calcSuspiciousness(sbflCoverage, granularity, f);
    }
    public FaultFinder(CoverageCollection covForTestSuite, Granularity granularity, Formula f) {
        this.granularity = granularity;
        flRanking = new FLRanking();
        calcSuspiciousness(covForTestSuite, granularity, f);
    }

    private void calcSuspiciousness(CoverageCollection covForTestSuite, Granularity granularity, Formula f){
        for(CoverageOfTarget coverageOfTarget : covForTestSuite.getCoverages()) {
            coverageOfTarget.getCoverage(granularity).forEach((element, status) -> {
                double suspScore = status.getSuspiciousness(f);
                flRanking.add(element, suspScore);
            });
        }
        flRanking.sort();
    }


    public void printRanking(){
        presenter.printFLResults();
    }

    public void printRanking(int top){
        presenter.printFLResults(top);
    }

    public void remove(int rank) {
        ScoreUpdateReport report = new ScoreUpdateReport();
        FLRankingElement target = flRanking.at(rank);
        if(target == null){
                throw new RuntimeException("rank:" + rank + " is out of bounds. (max rank: " + flRanking.getSize() + ")");
        }

        System.out.println("[  REMOVE  ] " + target);
        report.recordChange(target);

        target.suspScore = 0;
        getNeighborElements(target).forEach(e -> {
            updateSuspiciousnessScore(e, score -> score * this.removeConst);
        });

        report.print();
        flRanking.sort();
        presenter.printFLResults(rankingSize);
    }

    public void susp(int rank) {
        ScoreUpdateReport report = new ScoreUpdateReport();
        FLRankingElement target = flRanking.at(rank);
        if(target == null){
            throw new RuntimeException("rank:" + rank + " is out of bounds. (max rank: " + flRanking.getSize() + ")");
        }

        System.out.println("[  SUSP  ] " + target);
        report.recordChange(target);

        target.suspScore = 0;
        getNeighborElements(target).forEach(e -> {
            updateSuspiciousnessScore(e, score -> score * this.suspConst);
        });

        report.print();
        flRanking.sort();
        presenter.printFLResults(rankingSize);
    }


    public void probe(SuspiciousVariable target){
        Probe prb = new Probe(target);
        SuspiciousExprTreeNode causeTree = prb.run(2000);
        causeTree.print();
        probe(causeTree);
    }

    public void probe(SuspiciousExprTreeNode causeTree){
        TraceToScoreAdjustmentConverter converter = new TraceToScoreAdjustmentConverter(this.probeLambda, granularity);
        Map<CodeElementIdentifier, Double> adjustments = converter.toAdjustments(causeTree);
        adjustAll(adjustments);
        printRanking(10);
    }

    //リファクタリングのための一時メソッド
    @Deprecated
    public Set<CodeElementIdentifier> getNeighborElements(FLRankingElement target){
        return flRanking.getAllElements().stream()
                .filter(e -> e.isNeighbor(target.getCodeElementName()) && !e.equals(target.getCodeElementName()))
                .collect(Collectors.toSet());
    }

    /**
     * ランキングの要素を再計算
     * @param adjustments
     */
    public void adjustAll(Map<CodeElementIdentifier, Double> adjustments) {
        for ( Map.Entry<CodeElementIdentifier, Double> adj : adjustments.entrySet()) {
            Optional<FLRankingElement> target = flRanking.searchElement(adj.getKey());
            if (target.isEmpty()) continue;
            target.get().suspScore *= adj.getValue();
        }
        flRanking.sort();
    }

    public void updateSuspiciousnessScore(CodeElementIdentifier target, DoubleFunction<Double> f){
        FLRankingElement e = flRanking.searchElement(target).get();
        double newScore = f.apply(e.suspScore);
        flRanking.updateSuspiciousnessScore(target, newScore);
    }
}
