package jisd.fl.coverage;

import java.util.HashMap;

//あるテストケースを実行したときの、ターゲットのクラスごとのカバレッジ (Tester)
public class CoverageForTestCase {
    final String testBinPath;
    final String testClassName;
    final String testMethodName;
    final boolean isPassed;
    final Granularity granularity;
    //各クラスのカバレッジインスタンスを保持 (クラス名) --> BaseCoverage
    final HashMap<String,? super BaseCoverage<?>> coverages = new HashMap<>();

    public CoverageForTestCase(String testBinPath, String testClassName, String testMethodName, boolean isPassed, Granularity granularity) {
        this.testBinPath = testBinPath;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.isPassed = isPassed;
        this.granularity = granularity;
    }

    public void putClassCoverage(BaseCoverage<?> cc){
        coverages.put(cc.getTargetClassName(), cc);
    }

}
