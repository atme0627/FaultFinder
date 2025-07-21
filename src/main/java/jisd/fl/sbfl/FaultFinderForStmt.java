package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import jisd.fl.report.ScoreUpdateReport;
import jisd.fl.util.analyze.MethodElementName;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.Set;

public class FaultFinderForStmt extends FaultFinder{
    public FaultFinderForStmt(CoverageCollection covForTestSuite, Formula f) {
        super(covForTestSuite, Granularity.LINE, f);
    }

    @Override
    public void remove(int rank) {
        ScoreUpdateReport report = new ScoreUpdateReport("REMOVE");
        FLRankingElement target = flRanking.getElementAtPlace(rank).orElseThrow(
                () -> new RuntimeException("rank:" + rank + " is out of bounds. (max rank: " + flRanking.getSize() + ")"));

        System.out.println("[  REMOVE  ] " + target);
        report.recordChange(target);

        target.updateSuspiciousnessScore(0);
        flRanking.getNeighborElements(target).forEach(e -> {
            report.recordChange(e);
            e.updateSuspiciousnessScore(this.removeConst);
        });

        report.print();
        flRanking.sort();
        flRanking.printFLResults(getRankingSize());
    }

    @Override
    public void susp(int rank) {
        ScoreUpdateReport report = new ScoreUpdateReport("SUSP");
        FLRankingElement target = flRanking.getElementAtPlace(rank).orElseThrow(
                () -> new RuntimeException("rank: " + rank + " is out of bounds. (max rank: " + flRanking.getSize() + ")"));

        System.out.println("[  SUSP  ] " + target);
        report.recordChange(target);

        target.updateSuspiciousnessScore(0);
        flRanking.getNeighborElements(target).forEach(e -> {
                report.recordChange(e);
                e.updateSuspiciousnessScore(this.suspConst);
        });

        report.print();
        flRanking.sort();
        flRanking.printFLResults(getRankingSize());
    }
}
