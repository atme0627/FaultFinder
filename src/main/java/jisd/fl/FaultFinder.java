package jisd.fl;

import jisd.fl.core.domain.ProbeScoreCalculator;
import jisd.fl.core.domain.RemoveScoreCalculator;
import jisd.fl.core.domain.SuspScoreCalculator;
import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.entity.element.CodeElementIdentifier;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import jisd.fl.presenter.FLRankingPresenter;
import jisd.fl.usecase.Probe;
import jisd.fl.core.entity.susp.CauseTreeNode;
import jisd.fl.core.entity.FLRanking;
import jisd.fl.core.entity.FLRankingElement;
import jisd.fl.core.entity.sbfl.Formula;
import jisd.fl.usecase.CoverageAnalyzer;
import jisd.fl.core.entity.sbfl.Granularity;
import jisd.fl.core.entity.susp.SuspiciousLocalVariable;
import jisd.fl.presenter.ProbeReporter;
import jisd.fl.presenter.ScoreUpdateReport;

import java.util.function.BiConsumer;

/**
 * テストスイートのカバレッジ情報から疑惑値ランキングを生成・操作するためのクラス。
 * CoverageCollectionを解析し、各対象要素の疑惑値を計算してFLRankingに設定します。
 * remove(), susp(), probe()によってランキングに開発者の知識を与えランキングを更新できる。
 */
public class FaultFinder {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

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
    public ProjectSbflCoverage coverage;

    public FaultFinder(ClassElementName targetTestClassName){
        this.granularity = Granularity.LINE;
        Formula f = Formula.OCHIAI;
        CoverageAnalyzer coverageAnalyzer = new CoverageAnalyzer();
        coverage = coverageAnalyzer.analyze(targetTestClassName);
        flRanking = new FLRanking();
        presenter = new FLRankingPresenter(flRanking);
        calcSuspiciousness(coverage, granularity, f);
    }

    private void calcSuspiciousness(ProjectSbflCoverage sbflCoverage, Granularity granularity, Formula f){
        switch (granularity){
            case CLASS -> sbflCoverage.classCoverageEntries().forEach(entry -> {
                double suspScore = entry.counts().getSuspiciousness(f);
                flRanking.add(entry.e(), suspScore);
            });
            case METHOD -> sbflCoverage.methodCoverageEntries(true).forEach(entry -> {
                double suspScore = entry.counts().getSuspiciousness(f);
                flRanking.add(entry.e(), suspScore);
            });
            case LINE -> sbflCoverage.lineCoverageEntries(true).forEach(entry -> {
                double suspScore = entry.counts().getSuspiciousness(f);
                flRanking.add(entry.e(), suspScore);
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
        RemoveScoreCalculator calc = new RemoveScoreCalculator(removeConst);
        applyScoreUpdate(rank, "REMOVE", calc::apply);
    }

    public void susp(int rank) {
        SuspScoreCalculator calc = new SuspScoreCalculator(suspConst);
        applyScoreUpdate(rank, "SUSP", calc::apply);
    }

    private void applyScoreUpdate(int rank, String operationName,
                                  BiConsumer<CodeElementIdentifier<?>, FLRanking> calculator) {
        ScoreUpdateReport report = new ScoreUpdateReport();
        FLRankingElement target = flRanking.at(rank);
        if (target == null) {
            throw new RuntimeException("rank:" + rank + " is out of bounds. (max rank: " + flRanking.getSize() + ")");
        }

        System.out.println(BOLD + "[  " + operationName + "  ]" + RESET + " " + target.getCodeElementName());
        report.recordChange(target);
        for (var neighbor : flRanking.getNeighborsOf(target.getCodeElementName())) {
            flRanking.searchElement(neighbor).ifPresent(report::recordChange);
        }

        calculator.accept(target.getCodeElementName(), flRanking);

        report.print();
        presenter.printFLResults(rankingSize);
    }


    public void probe(SuspiciousLocalVariable target){
        Probe prb = new Probe(target);
        CauseTreeNode causeTree = prb.run(2000);
        new ProbeReporter().printCauseTree(causeTree);
        probe(causeTree);
    }

    public void probe(CauseTreeNode causeTree) {
        ProbeScoreCalculator calc = new ProbeScoreCalculator(probeLambda, granularity);
        calc.apply(causeTree, flRanking);
        printRanking(10);
    }
}
