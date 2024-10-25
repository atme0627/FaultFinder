package jisd.fl.coverage;

import org.jacoco.core.analysis.IClassCoverage;

public class LineCoverage extends BaseCoverage {

    public LineCoverage(String targetClassName, String targetClassPath, int targetClassFirstLine, int targetClassLastLine) {
        super(targetClassName, targetClassPath, targetClassFirstLine, targetClassLastLine, Granularity.LINE);
    }

    @Override
    public void processCoverage(IClassCoverage cc) {
        setTargetClassName(cc.getName().replace("/", "."));
        setTargetClassFirstLine(cc.getFirstLine());
        setTargetClassLastLine(cc.getLastLine());

        for(int i = getTargetClassFirstLine(); i <= getTargetClassLastLine(); i++){
            putCoverage(Integer.toString(i), cc.getLine(i).getStatus());
        }
    }

    @Override
    public void printCoverage(){
        System.out.println("TargetClassName: " + targetClassName);
        System.out.println("--------------------------------------");
        for(int i = targetClassFirstLine; i <= targetClassLastLine; i++){
            System.out.println(i + ": " + getColor(coverage.get(Integer.toString(i))));
        }
        System.out.println("--------------------------------------");
        System.out.println();
    }
}
