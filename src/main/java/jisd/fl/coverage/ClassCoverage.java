package jisd.fl.coverage;

import org.jacoco.core.analysis.IClassCoverage;

public class ClassCoverage extends BaseCoverage {


    public ClassCoverage(String targetClassName, String targetClassPath, int targetClassFirstLine, int targetClassLastLine) {
        super(targetClassName, targetClassPath, targetClassFirstLine, targetClassLastLine, Granularity.CLASS);
    }

    @Override
    public void processCoverage(IClassCoverage cc) {

    }

    @Override
    public void printCoverage() {

    }
}

