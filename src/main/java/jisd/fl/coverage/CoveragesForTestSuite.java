package jisd.fl.coverage;
import java.util.*;

//対象クラスのテストスイートによるSBFLのためのカバレッジ
//テストケースの区別は行わない
public class CoveragesForTestSuite extends Coverages{

    CoveragesForTestSuite(String testClassName, Set<String> targetClassNames) {
        super(testClassName, targetClassNames);
    }

    protected void addCoveragesForTestCase(CoveragesForTestCase newCovForTestCase) {
        for(String targetClassName : targetClassNames){
            if(newCovForTestCase.coverages.containsKey(targetClassName)){
                CoverageOfTarget covForTarget = newCovForTestCase.coverages.get(targetClassName);
                addCoverageOfTargetForTestCase(covForTarget);
            }
        }
    }

    private void addCoverageOfTargetForTestCase(CoverageOfTarget newCov){
        String targetClassName = newCov.getTargetClassName();
        boolean isEmpty = !coverages.containsKey(targetClassName);

        //coveragesにない、新しいtargetClassのカバレッジが追加されたとき
        if(isEmpty){
            coverages.put(targetClassName, newCov);
        }
        //すでにtargetClassのカバレッジがあるとき
        else {
            CoverageOfTarget existedCov = coverages.get(targetClassName);
            existedCov.combineCoverages(newCov);
        }
    }



    public String getTestClassName() {
        return testClassName;
    }
}
