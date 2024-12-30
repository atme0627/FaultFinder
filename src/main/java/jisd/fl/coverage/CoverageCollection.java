package jisd.fl.coverage;

import jisd.fl.sbfl.SbflStatus;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.*;

//あるテストケースを実行したときの、ターゲットのクラスごとのカバレッジ (Tester)
public class CoverageCollection implements Serializable {

    protected final String coverageCollectionName;
    Set<String> targetClassNames;  //実行されたターゲットクラスの集合

    //各クラスのカバレッジインスタンスを保持 (ターゲットクラス名) --> CoverageOfTarget
    HashMap<String, CoverageOfTarget> coverages = new LinkedHashMap<>();

    public CoverageCollection(String coverageCollectionName, Set<String> targetClassNames) {
        this.coverageCollectionName = coverageCollectionName;
        this.targetClassNames = targetClassNames;
    }

    public Map<String, SbflStatus> getCoverageOfTarget(String targetClassName, Granularity granularity) {
        return coverages.get(targetClassName).getCoverage(granularity);
    }

    private HashMap<String, CoverageOfTarget> getCoverages(){
        return coverages;
    }

    public void printCoverages(Granularity granularity){
        printCoverages(System.out, granularity);
    }

    public void printCoverages(PrintStream out, Granularity granularity){
        for(CoverageOfTarget cov : coverages.values()){
            cov.printCoverage(out, granularity);
        }
    }

    public Set<String> getTargetClassNames() {
        return targetClassNames;
    }

    public void putCoverageOfTarget(CoverageOfTarget covOfTarget) {
        String targetClassName = covOfTarget.getTargetClassName();
        boolean isEmpty = !coverages.containsKey(targetClassName);
        //coveragesにない、新しいtargetClassのカバレッジが追加されたとき
        if (isEmpty) {
            coverages.put(targetClassName, covOfTarget);
        }
        //すでにtargetClassのカバレッジがあるとき
        else {
            CoverageOfTarget existedCov = coverages.get(targetClassName);
            existedCov.combineCoverages(covOfTarget);
        }
    }

    public boolean isContainsTargetClass(String targetClassName){
        return targetClassNames.contains(targetClassName);
    }

    protected void mergeCoverage(CoverageCollection newCov) {
        newCov.getCoverages().forEach((targetClassName, covOfTarget) -> {
            putCoverageOfTarget(covOfTarget);
        });
    }
}
