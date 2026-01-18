package jisd.fl.sbfl.coverage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.io.PrintStream;
import java.util.*;

import jisd.fl.core.entity.coverage.Granularity;
import jisd.fl.util.JsonIO;

/**
あるテストクラス実行時のSBFL用カバレッジ情報を保持するクラス
 */
public class CoverageCollection {

    public Set<String> targetClassNames;

    //クラスごとのカバレッジ
    final private Set<CoverageOfTarget> coverages = new HashSet<>();

    @JsonCreator
    public CoverageCollection(
            @JsonProperty("targetClassName")        Set<String> targetClassNames) {
        this.targetClassNames = targetClassNames;
    }


    public void printCoverages(Granularity granularity){
        printCoverages(System.out, granularity);
    }

    public void printCoverages(PrintStream out, Granularity granularity){
        for(CoverageOfTarget cov : getCoverages()){
            cov.printCoverage(out, granularity);
        }
    }

    public Set<CoverageOfTarget> getCoverages(){
        return coverages;
    }

    //Jackson シリアライズ用メソッド
    @JsonProperty
    public Set<String> getTargetClassNames() {
        return targetClassNames;
    }

    @JsonProperty
    private void setCoverages(Set<CoverageOfTarget> coveragesFromJson) {
        coverages.clear();
        coverages.addAll(coveragesFromJson);
    }

    //Jackson デシリアライズ用メソッド
    public static CoverageCollection loadFromJson(File f){
        return JsonIO.importCoverage(f);
    }

    public void free(){
        targetClassNames.clear();
        coverages.clear();
    }
}
