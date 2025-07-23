package jisd.fl;

import jisd.fl.probe.Probe;
import jisd.fl.probe.info.SuspiciousExpression;
import jisd.fl.probe.info.SuspiciousVariable;
import jisd.fl.ranking.ScoreAdjustment;
import jisd.fl.ranking.TraceToScoreAdjustmentConverter;
import jisd.fl.sbfl.Formula;
import jisd.fl.sbfl.coverage.CoverageCollection;
import jisd.fl.sbfl.coverage.Granularity;

import java.util.List;

public class FaultFinderForStmt extends FaultFinder{
    public FaultFinderForStmt(CoverageCollection covForTestSuite, Formula f) {
        super(covForTestSuite, Granularity.LINE, f);
    }

    public void probe(SuspiciousVariable target){
        Probe prb = new Probe(target);
        probe(prb.run(2000));
    }

    public void probe(SuspiciousExpression causeTree){
        TraceToScoreAdjustmentConverter converter = new TraceToScoreAdjustmentConverter(this.probeLambda, granularity);
        List<ScoreAdjustment> adjustments = converter.toAdjustments(causeTree);
        flRanking.adjustAll(adjustments);
        printRanking(10);
    }
}
