package jisd.fl.coverage;

import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
//ターゲットクラスのカバレッジ（Testee）
//行単位のカバレッジのみ対応

public abstract class BaseCoverage {
    protected String targetClassName;
    protected int targetClassFirstLine;
    protected int targetClassLastLine;
    protected final String targetClassPath;
    protected final Granularity granularity;

    //各行のカバレッジ情報 (行番号 or メソッド名 or クラス名) --> coverage status
    final Map<String, Integer> coverage = new HashMap<>();

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

    protected void putCoverage(String line, Integer coverageInfo) {
        coverage.put(line, coverageInfo);
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

    public Map<String, Integer> getCoverage() {
        return coverage;
    }
}
