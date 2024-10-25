package jisd.fl.coverage;

import java.util.HashMap;
import java.util.HashSet;
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
    private final HashMap<String, CoverageOfTarget> coverages = new HashMap<>();

    public CoverageForTestCase(String testBinPath, String testClassName, String testMethodName, boolean isPassed, Granularity granularity) {
        this.testBinPath = testBinPath;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.isPassed = isPassed;
        this.granularity = granularity;
    }

    public void putClassCoverage(CoverageOfTarget cc){
        getCoverages().put(cc.getTargetClassName(), cc);
    }

    public HashMap<String, CoverageOfTarget> getCoverages() {
        return coverages;
    }

    //各要素単位(行、メソッド、クラス)でカバレッジを取得
    public int getCoverageByElement(String targetClassName, String element){
        return coverages.get(targetClassName).lineCoverage.get(element);
    }

    public void printCoverages(){
        for(CoverageOfTarget cov : coverages.values()){
            cov.printCoverage();
        }
    }

    public Set<String> getTargetClassNames() {
        return targetClassNames;
    }

    protected void setTargetClassNames(Set<String> targetClassNames) {
        this.targetClassNames = targetClassNames;
    }
}
