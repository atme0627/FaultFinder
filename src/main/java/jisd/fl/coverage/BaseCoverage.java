package jisd.fl.coverage;

import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;

import java.util.HashMap;
//ターゲットクラスのカバレッジ（Testee）
//行単位のカバレッジのみ対応

public abstract class BaseCoverage {
    String targetClassName;
    int targetClassFirstLine;
    int targetClassLastLine;
    final String targetClassPath;
    final Granularity granularity;
    //各行のカバレッジ情報を保持 (行番号 or メソッド名 or クラス名) --> coverage status
    final HashMap<String, Integer> coverage = new HashMap<>();

    public BaseCoverage(String targetClassName, String targetClassPath, int targetClassFirstLine, int targetClassLastLine, Granularity granularity) {
        this.targetClassName = targetClassName;
        this.targetClassPath = targetClassPath;
        this.targetClassFirstLine = targetClassFirstLine;
        this.targetClassLastLine = targetClassLastLine;
        this.granularity = granularity;
    }

    //1つのテストケース(not テストスイート)を実行したときのIClassCoverageを渡す。
    abstract public void processCoverage(IClassCoverage cc);

    //標準出力にカバレッジ情報を表示
    abstract  public void printCoverage();

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


    public String getTargetClassName() {
        return targetClassName;
    }

    public String getTargetClassPath() {
        return targetClassPath;
    }

    public int getTargetClassFirstLine() {
        return targetClassFirstLine;
    }

    public Granularity getGranularity() {
        return granularity;
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

    protected void putCoverage(String line, Integer coverageInfo){
        getCoverage().put(line, coverageInfo);
    }

    public HashMap<String, Integer> getCoverage() {
        return coverage;
    }
}
