package jisd.fl.coverage;

import org.jacoco.core.analysis.IClassCoverage;

public class MethodCoverage extends BaseCoverage{


    public MethodCoverage(String targetClassName, String targetClassPath, int targetClassFirstLine, int targetClassLastLine) {
        super(targetClassName, targetClassPath, targetClassFirstLine, targetClassLastLine, Granularity.METHOD);
    }

    @Override
    public void processCoverage(IClassCoverage cc) {

    }

    @Override
    public void printCoverage() {

    }
}
