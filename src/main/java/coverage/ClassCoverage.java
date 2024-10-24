package coverage;

import org.jacoco.core.analysis.IClassCoverage;

public class ClassCoverage extends BaseCoverage<String> {
    public ClassCoverage(String targetClassName, String targetClassPath, int targetClassLineNum) {
        super(targetClassName, targetClassPath, targetClassLineNum, Granularity.CLASS);
    }

    @Override
    public void processCoverage(IClassCoverage cc) {

    }
}

