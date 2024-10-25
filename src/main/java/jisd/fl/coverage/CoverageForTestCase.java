package jisd.fl.coverage;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//あるテストケースを実行したときの、ターゲットのクラスごとのカバレッジ (Tester)
public class CoverageForTestCase {
    protected final String testBinPath;
    protected final String testClassName;
    protected final String testMethodName;
    protected final boolean isPassed;
    protected final Granularity granularity;

    //実行されたターゲットクラスの集合
    Set<String> targetClassNames = new HashSet<>();

    //各クラスのカバレッジインスタンスを保持 (クラス名) --> BaseCoverage
    private final Map<String, CoverageOfTarget> coverages = new LinkedHashMap<>();

    public CoverageForTestCase(String testBinPath, String testClassName, String testMethodName, boolean isPassed, Granularity granularity) {
        this.testBinPath = testBinPath;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.isPassed = isPassed;
        this.granularity = granularity;
    }

    public void putCoverageOfTarget(CoverageOfTarget cc){
        coverages.put(cc.getTargetClassName(), cc);
    }

    public Map<String, CoverageOfTarget> getCoverages() {
        return coverages;
    }

    //各要素単位(行、メソッド、クラス)でカバレッジを取得
    public int getCoverageByElement(String targetClassName, String element, Granularity granularity){
        switch (granularity){
            case LINE:
                return coverages.get(targetClassName).lineCoverage.get(element);
            case METHOD:
                return coverages.get(targetClassName).methodCoverage.get(element);
            case CLASS:
                return coverages.get(targetClassName).classCoverage.get(element);
        }
        return 0;
    }

    public void printCoverages(Granularity granularity){
        for(CoverageOfTarget cov : coverages.values()){
            cov.printCoverage(granularity);
        }
    }

    public Set<String> getTargetClassNames() {
        return targetClassNames;
    }

    void setTargetClassNames(Set<String> targetClassNames) {
        this.targetClassNames = targetClassNames;
    }
}
