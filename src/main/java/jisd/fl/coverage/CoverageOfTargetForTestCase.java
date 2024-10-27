package jisd.fl.coverage;

import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.StaticAnalyzer;
import org.apache.commons.lang3.tuple.Pair;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;

import java.io.IOException;
import java.util.*;

public class CoverageOfTargetForTestCase {
    protected final String targetSrcPath;
    private final boolean isTestPassed;
    protected String targetClassName;
    protected Set<String> targetMethodNames;

    //各行のカバレッジ情報 (行番号, メソッド名, クラス名) --> lineCoverage status
    protected Map<String, SbflStatus> lineCoverage = new LinkedHashMap<>();
    protected Map<String, SbflStatus> methodCoverage = new LinkedHashMap<>();
    protected Map<String, SbflStatus> classCoverage = new LinkedHashMap<>();

    public CoverageOfTargetForTestCase(String targetClassName, String targetSrcPath, boolean isTestPassed) throws IOException {
        this.targetClassName = targetClassName;
        this.targetSrcPath = targetSrcPath;
        this.isTestPassed = isTestPassed;
        this.targetMethodNames = StaticAnalyzer.getMethodNames(targetSrcPath, targetClassName);
    }

    public Map<String, SbflStatus> getCoverage(Granularity granularity){
        switch (granularity){
            case LINE:
                return lineCoverage;
            case METHOD:
                return methodCoverage;
            case CLASS:
                return classCoverage;
        }
        return null;
    }

    public String getTargetClassName() {
        return targetClassName;
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

    public void printCoverage(Granularity granularity) {
        Map<String, SbflStatus> cov = null;
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

        if(granularity != Granularity.CLASS) {
            System.out.println("TargetClassName: " + targetClassName);
            System.out.println("EP  EF  NP  NF");
            System.out.println("--------------------------------------");
        }
        List<String> keys = getSortedKeys(cov.keySet(), granularity);
        for(String key : keys){
            System.out.println(key + ": " + cov.get(key));
        }
        if(granularity != Granularity.CLASS) {
            System.out.println("--------------------------------------");
            System.out.println();
        }
    }

    void combineCoverages(CoverageOfTargetForTestCase cov){
        this.lineCoverage = combineCoverage(this.lineCoverage, cov.lineCoverage);
        this.methodCoverage = combineCoverage(this.methodCoverage,  cov.methodCoverage);
        this.classCoverage = combineCoverage(this.classCoverage, cov.classCoverage);
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

    private Map<String, SbflStatus> combineCoverage(Map<String, SbflStatus> thisCov, Map<String, SbflStatus> otherCov){
        Map<String, SbflStatus> newCoverage = new HashMap<>(otherCov);
        thisCov.forEach((k,v)->{
            if(newCoverage.containsKey(k)){
                newCoverage.put(k, v.combine(newCoverage.get(k)));
            }
            else {
                newCoverage.put(k, v);
            }
        });
        return newCoverage;
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

    private List<String> getSortedKeys(Set<String> keyset, Granularity granularity){
        ArrayList<String> keys =  new ArrayList<>(keyset);
        if(granularity == Granularity.LINE){
            //行数のStringをソートするための処理
            keys.sort((o1, o2) -> Integer.parseInt(o1) - Integer.parseInt(o2));
        }
        else {
            Collections.sort(keys);
        }
        return keys;
    }
}
