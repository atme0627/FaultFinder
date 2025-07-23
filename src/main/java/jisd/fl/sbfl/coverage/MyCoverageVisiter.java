package jisd.fl.sbfl.coverage;

import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICoverageVisitor;

import java.util.Set;

//executionDataを処理するvisitor
//CoverageBuilderではCOVERED, NOT_COVEREDなどしかわからない
//各行の実行回数を得られるようにしたい
//execdataに対応するcoverageインスタンスを返せるようにする。

public class MyCoverageVisiter implements ICoverageVisitor {
    //与えるjacocoexecのテストケースの成否を指定
    private Boolean isTestsPassed;
    CoverageCollection coverageCollection;

    public MyCoverageVisiter(String testClassName, Set<String> targetClassNames){
        this.coverageCollection = new CoverageCollection(testClassName, targetClassNames);
    }

    @Override
    public void visitCoverage(IClassCoverage coverage) {
        if(isTestsPassed == null){
            throw new RuntimeException("(isTestPassed) is not initialized.");
        }

        //ターゲットのクラスに含まれないクラスの場合
        String targetClassName = coverage.getName().replace("/", ".");
        //内部クラスを考慮
        if(!coverageCollection.isContainsTargetClass(
                targetClassName.contains("$")
                ? targetClassName.split("\\$")[0]
                : targetClassName)){
            return;
        }

        CoverageOfTarget covOfTarget = CoverageCollection.getCoverageOfTarget(targetClassName);
        covOfTarget.processCoverage(coverage, isTestsPassed);
    }

    public void setTestsPassed(boolean testsPassed) {
        isTestsPassed = testsPassed;
    }

    public CoverageCollection getCoverages(){
        return coverageCollection;
    }
}
