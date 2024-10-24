package coverage;

import org.jacoco.core.analysis.IClassCoverage;

import java.util.HashMap;
//ターゲットクラスのカバレッジ（Testee）
//行単位のカバレッジのみ対応

public abstract class BaseCoverage<T> {
    private String targetClassName;
    private int targetClassFirstLine;
    private int targetClassLastLine;
    private final String targetClassPath;
    private final Granularity granularity;
    //各行のカバレッジ情報を保持 (行番号 or メソッド名 or クラス名) --> coverage status
    final HashMap<T, Integer> coverage = new HashMap<>();

    public BaseCoverage(String targetClassName, String targetClassPath, int targetClassStartLine, Granularity granularity) {
        this.targetClassPath = targetClassPath;
        this.granularity = granularity;
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

    protected void putCoverage(T line, Integer coverageInfo){
        coverage.put(line, coverageInfo);
    }

    //1つのテストケース(not テストスイート)を実行したときのIClassCoverageを渡す。
    public abstract void processCoverage(IClassCoverage cc);
}
