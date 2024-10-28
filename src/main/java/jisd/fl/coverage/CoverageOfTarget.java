package jisd.fl.coverage;

import jisd.fl.sbfl.Formula;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.util.StaticAnalyzer;

import java.io.IOException;
import java.util.*;

public class CoverageOfTarget {
    protected final String targetSrcPath;
    protected String targetClassName;
    protected Set<String> targetMethodNames;

    //各行のカバレッジ情報 (行番号, メソッド名, クラス名) --> lineCoverage status
    protected Map<String, SbflStatus> lineCoverage = new LinkedHashMap<>();
    protected Map<String, SbflStatus> methodCoverage = new LinkedHashMap<>();
    protected Map<String, SbflStatus> classCoverage = new LinkedHashMap<>();

    public CoverageOfTarget(String targetClassName, String targetSrcPath) throws IOException {
        this.targetClassName = targetClassName;
        this.targetSrcPath = targetSrcPath;
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

    void combineCoverages(CoverageOfTarget cov){
        this.lineCoverage = combineCoverage(this.lineCoverage, cov.lineCoverage);
        this.methodCoverage = combineCoverage(this.methodCoverage,  cov.methodCoverage);
        this.classCoverage = combineCoverage(this.classCoverage, cov.classCoverage);
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
