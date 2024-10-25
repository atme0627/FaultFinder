package jisd.fl.coverage;

import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;

import java.util.HashMap;
import java.util.Map;
//ターゲットクラスのカバレッジ（Testee）
//行単位のカバレッジのみ対応

public class CoverageOfTarget {
    protected String targetClassName;
    protected int targetClassFirstLine;
    protected int targetClassLastLine;
    protected final String targetClassPath;

    //各行のカバレッジ情報 (行番号 or メソッド名 or クラス名) --> lineCoverage status
    final Map<String, Integer> lineCoverage = new HashMap<>();

    public CoverageOfTarget(String targetClassName, String targetClassPath, int targetClassFirstLine, int targetClassLastLine) {
        this.targetClassName = targetClassName;
        this.targetClassPath = targetClassPath;
        this.targetClassFirstLine = targetClassFirstLine;
        this.targetClassLastLine = targetClassLastLine;
    }


    public void processCoverage(IClassCoverage cc) {
        setTargetClassName(cc.getName().replace("/", "."));
        setTargetClassFirstLine(cc.getFirstLine());
        setTargetClassLastLine(cc.getLastLine());

        for(int i = getTargetClassFirstLine(); i <= getTargetClassLastLine(); i++){
            putCoverage(Integer.toString(i), cc.getLine(i).getStatus());
        }
    }

    public void printCoverage(){
        System.out.println("TargetClassName: " + targetClassName);
        System.out.println("--------------------------------------");
        for(int i = targetClassFirstLine; i <= targetClassLastLine; i++){
            System.out.println(i + ": " + getColor(lineCoverage.get(Integer.toString(i))));
        }
        System.out.println("--------------------------------------");
        System.out.println();
    }

    protected String getColor(int status) {
        switch (status) {
            case ICounter.NOT_COVERED:
                return "red";
            case ICounter.PARTLY_COVERED:
                return "yellow";
            case ICounter.FULLY_COVERED:
                return "green";
        }
        return "";
    }

    protected void putCoverage(String line, Integer coverageInfo) {
        lineCoverage.put(line, coverageInfo);
    }

    public String getTargetClassName() {
        return targetClassName;
    }

    public String getTargetClassPath() {
        return targetClassPath;
    }

    public int getTargetClassFirstLine() {
        return targetClassFirstLine;
    }


    public int getTargetClassLastLine() {
        return targetClassLastLine;
    }

    public void setTargetClassName(String targetClassName) {
        this.targetClassName = targetClassName;
    }

    public void setTargetClassFirstLine(int targetClassFirstLine) {
        this.targetClassFirstLine = targetClassFirstLine;
    }

    public void setTargetClassLastLine(int targetClassLastLine) {
        this.targetClassLastLine = targetClassLastLine;
    }

    public Map<String, Integer> getLineCoverage() {
        return lineCoverage;
    }
}
