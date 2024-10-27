package jisd.fl.coverage;

import jisd.fl.sbfl.SbflStatus;

import java.util.*;

//あるテストケースを実行したときの、ターゲットのクラスごとのカバレッジ (Tester)
public abstract class Coverages {

    protected final String testClassName;
    Set<String> targetClassNames;  //実行されたターゲットクラスの集合

    //各クラスのカバレッジインスタンスを保持 (ターゲットクラス名) --> CoverageOfTarget
    HashMap<String, CoverageOfTargetForTestCase> coverages = new LinkedHashMap<>();

    public Coverages(String testClassName, Set<String> targetClassNames) {
        this.testClassName = testClassName;
        this.targetClassNames = targetClassNames;
    }

    public Map<String, SbflStatus> getCoverageOfTarget(String targetClassName, Granularity granularity) {
        return coverages.get(targetClassName).getCoverage(granularity);
    }

    public void printCoverages(Granularity granularity){
        for(CoverageOfTargetForTestCase cov : coverages.values()){
            cov.printCoverage(granularity);
        }
    }

    public Set<String> getTargetClassNames() {
        return targetClassNames;
    }

    protected void putCoverageOfTarget(CoverageOfTargetForTestCase cov){
        coverages.put(cov.getTargetClassName(), cov);
    }
}
