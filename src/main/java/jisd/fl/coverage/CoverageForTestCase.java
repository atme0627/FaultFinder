package jisd.fl.coverage;

import java.util.HashMap;

//あるテストケースを実行したときの、ターゲットのクラスごとのカバレッジ (Tester)
public class CoverageForTestCase<T extends BaseCoverage> {
    final String testBinPath;
    final String testClassName;
    final String testMethodName;
    final boolean isPassed;
    final Granularity granularity;
    //各クラスのカバレッジインスタンスを保持 (クラス名) --> BaseCoverage
    private final HashMap<String, T> coverages = new HashMap<>();

    public CoverageForTestCase(String testBinPath, String testClassName, String testMethodName, boolean isPassed, Granularity granularity) {
        this.testBinPath = testBinPath;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.isPassed = isPassed;
        this.granularity = granularity;
    }

    public void putClassCoverage(T cc){
        getCoverages().put(cc.getTargetClassName(), cc);
    }

    public HashMap<String, T> getCoverages() {
        return coverages;
    }

    public void printCoverages(){
        for(T cov : coverages.values()){
            cov.printCoverage();
        }
    }
}
