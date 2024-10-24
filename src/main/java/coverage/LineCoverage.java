package coverage;

import org.jacoco.core.analysis.IClassCoverage;

public class LineCoverage extends BaseCoverage<Integer> {

    public LineCoverage(String targetClassName, String targetClassPath, int targetClassLineNum) {
        super(targetClassName, targetClassPath, targetClassLineNum, Granularity.LINE);
    }

    @Override
    public void processCoverage(IClassCoverage cc) {
        setTargetClassName(cc.getName());
        setTargetClassFirstLine(cc.getFirstLine());
        setTargetClassLastLine(cc.getLastLine());

        for(int i = getTargetClassFirstLine(); i <= getTargetClassLastLine(); i++){
            putCoverage(i, cc.getLine(i).getStatus());
        }
    }
}
