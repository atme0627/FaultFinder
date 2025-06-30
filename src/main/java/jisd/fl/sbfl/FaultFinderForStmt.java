package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;

public class FaultFinderForStmt extends FaultFinder{
    public FaultFinderForStmt(CoverageCollection covForTestSuite, Formula f) {
        super(covForTestSuite, Granularity.LINE, f);

    }
}
