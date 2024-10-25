package jisd.fl.coverage;

import jisd.fl.util.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;

import java.io.IOException;
import java.util.*;
//ターゲットクラスのカバレッジ（Testee）
//行単位のカバレッジのみ対応

public class CoverageOfTarget {
    protected String targetClassName;
    protected int targetClassFirstLine;
    protected int targetClassLastLine;
    protected final String targetSrcPath;
    protected final String targetClassPath;

    //各行のカバレッジ情報 (行番号, メソッド名, クラス名) --> lineCoverage status
    protected final Map<String, Integer> lineCoverage = new LinkedHashMap<>();
    protected final Map<String, Integer> methodCoverage = new TreeMap<>();
    protected final Map<String, Integer> classCoverage = new TreeMap<>();
    protected final List<String> targetMethodNames;

    public CoverageOfTarget(String targetClassName, String targetSrcPath, String targetClassPath, int targetClassFirstLine, int targetClassLastLine) throws IOException {
        this.targetClassName = targetClassName;
        this.targetSrcPath = targetSrcPath;
        this.targetClassPath = targetClassPath;
        this.targetClassFirstLine = targetClassFirstLine;
        this.targetClassLastLine = targetClassLastLine;

        this.targetMethodNames = StaticAnalyzer.getMethodNames(targetSrcPath, targetClassName);
    }


    public void processCoverage(IClassCoverage cc) throws IOException {
        setTargetClassName(cc.getName().replace("/", "."));
        setTargetClassFirstLine(cc.getFirstLine());
        setTargetClassLastLine(cc.getLastLine());

        //line coverage
        for(int i = getTargetClassFirstLine(); i <= getTargetClassLastLine(); i++){
            putLineCoverage(Integer.toString(i), cc.getLine(i).getStatus());
        }

        //method coverage
        Map<String, Pair<Integer, Integer>> rangeOfMethod = StaticAnalyzer.getRangeOfMethods(targetSrcPath, targetClassName);
        for(String targetMethodName : targetMethodNames){
            Pair<Integer, Integer> range = rangeOfMethod.get(targetMethodName);
            putMethodCoverage(targetMethodName, isMethodCovered(range) ? ICounter.FULLY_COVERED : ICounter.NOT_COVERED);
        }

        //class coverage
        putClassCoverage(targetClassName, ICounter.FULLY_COVERED);
    }

    public void printCoverage(Granularity granularity) {
        Map<String, Integer> cov = null;
        switch (granularity) {
            case LINE:
                cov = lineCoverage;
                break;
            case METHOD:
                cov = methodCoverage;
                break;
            case CLASS:
                cov = classCoverage;
                break;
        }

        System.out.println("TargetClassName: " + targetClassName);
        System.out.println("--------------------------------------");
        cov.forEach((k, v)->{
            System.out.println(k + ": " + getColor(v));
        });
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

    private boolean isMethodCovered(Pair<Integer, Integer> range){
        int methodBegin = range.getLeft();
        int methodEnd = range.getRight();

        for(int i = methodBegin; i <= methodEnd; i++){
            if(lineCoverage.get(Integer.toString(i)) == ICounter.FULLY_COVERED ||
                    lineCoverage.get(Integer.toString(i)) == ICounter.PARTLY_COVERED ){
                return true;
            }
        }
        return false;
    }

    private void putLineCoverage(String line, Integer coverageInfo) {
        lineCoverage.put(line, coverageInfo);
    }

    private void putMethodCoverage(String methodName, Integer coverageInfo) {
        methodCoverage.put(methodName, coverageInfo);
    }

    private void putClassCoverage(String className, Integer coverageInfo) {
        classCoverage.put(className, coverageInfo);
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

    public Map<String, Integer> getMethodCoverage() {
        return methodCoverage;
    }

    public Map<String, Integer> getClassCoverage() {
        return classCoverage;
    }
}
