package jisd.fl.sbfl.coverage;

import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICoverageVisitor;

import java.util.Set;

//executionDataを処理するvisitor
//CoverageBuilderではCOVERED, NOT_COVEREDなどしかわからない
//各行の実行回数を得られるようにしたい
//execdataに対応するcoverageインスタンスを返せるようにする。

public class NewMyCoverageVisitor implements ICoverageVisitor {
    //与えるjacocoexecのテストケースの成否を指定
    private Boolean isTestsPassed;
    ProjectSbflCoverage projectSbflCoverage;

    public NewMyCoverageVisitor(){
        this.projectSbflCoverage = new ProjectSbflCoverage();
    }

    @Override
    public void visitCoverage(IClassCoverage coverage) {
        if(isTestsPassed == null){
            throw new RuntimeException("(isTestPassed) is not initialized.");
        }
        projectSbflCoverage.accept(coverage, isTestsPassed);
    }

    public void setTestsPassed(boolean testsPassed) {
        isTestsPassed = testsPassed;
    }

    public ProjectSbflCoverage getCoverages() {
        return projectSbflCoverage;
    }
}
