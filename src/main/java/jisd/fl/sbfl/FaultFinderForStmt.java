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

    public void probe(){

    }
}
