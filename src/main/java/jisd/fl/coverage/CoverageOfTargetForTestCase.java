package jisd.fl.coverage;

import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;

import java.io.IOException;
import java.util.Map;

public class CoverageOfTargetForTestCase extends CoverageOfTarget {
    private final boolean isTestPassed;

    public CoverageOfTargetForTestCase(String targetClassName, String targetSrcPath, boolean isTestPassed) throws IOException {
        super(targetClassName, targetSrcPath);
        this.isTestPassed = isTestPassed;
    }

    public void processCoverage(IClassCoverage cc) throws IOException {
        this.targetClassName = cc.getName().replace("/", ".");
        int targetClassFirstLine = cc.getFirstLine();
        int targetClassLastLine = cc.getLastLine();

        //line coverage
        for(int i = targetClassFirstLine; i <= targetClassLastLine; i++){
            if(cc.getLine(i).getStatus() == ICounter.EMPTY) continue;
            boolean isTestExecuted = !(cc.getLine(i).getStatus() == ICounter.NOT_COVERED);
            putCoverageStatus(Integer.toString(i), new SbflStatus(isTestExecuted , isTestPassed), Granularity.LINE);
        }

        //method coverage
        Map<String, Pair<Integer, Integer>> rangeOfMethod = StaticAnalyzer.getRangeOfMethods(targetSrcPath, targetClassName);
        for(String targetMethodName : targetMethodNames){
            Pair<Integer, Integer> range = rangeOfMethod.get(targetMethodName);
            putCoverageStatus(targetMethodName, getMethodSbflStatus(cc, range), Granularity.METHOD);
        }

        //class coverage
        putCoverageStatus(targetClassName, new SbflStatus(true, isTestPassed), Granularity.CLASS);
    }

    private void putCoverageStatus(String element, SbflStatus status, Granularity granularity){
        switch (granularity){
            case LINE:
                lineCoverage.put(element, status);
                break;
            case METHOD:
                methodCoverage.put(element, status);
                break;
            case CLASS:
                classCoverage.put(element, status);
                break;
        }
    }

    private SbflStatus getMethodSbflStatus(IClassCoverage cc, Pair<Integer, Integer> range){
        int methodBegin = range.getLeft();
        int methodEnd = range.getRight();
        boolean isTestExecuted = false;
        for(int i = methodBegin; i <= methodEnd; i++){
            int status = cc.getLine(i).getStatus();
            if(status == ICounter.PARTLY_COVERED || status == ICounter.FULLY_COVERED) {
                isTestExecuted = true;
                break;
            }
        }
        return new SbflStatus(isTestExecuted, isTestPassed);
    }
}
