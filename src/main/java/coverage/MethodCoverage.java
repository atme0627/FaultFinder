package coverage;

import org.jacoco.core.analysis.IClassCoverage;

public class MethodCoverage extends BaseCoverage<String> {

    public MethodCoverage(String targetClassName, String targetClassPath, int targetClassLineNum) {
        super(targetClassName, targetClassPath, targetClassLineNum, Granularity.METHOD);
    }

    @Override
    public void processCoverage(IClassCoverage cc) {

    }
}
