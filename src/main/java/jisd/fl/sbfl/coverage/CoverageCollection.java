package jisd.fl.sbfl.coverage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

import java.io.File;
import java.io.PrintStream;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jisd.fl.util.JsonIO;

//あるテストケースを実行したときの、ターゲットのクラスごとのカバレッジ (Tester)
public class CoverageCollection {

    public Set<String> targetClassNames;

    //クラスごとのカバレッジ
    final private Set<CoverageOfTarget> coverages = new HashSet<>();

    public CoverageOfTarget getCoverageOfTarget(String targetClassName){
        CoverageOfTarget coverageOfTarget;
        Optional<CoverageOfTarget> tmp = coverages.stream()
                .filter(e -> e.getTargetClassName().equals(targetClassName))
                .findFirst();

        coverageOfTarget = tmp.orElseGet(() -> new CoverageOfTarget(targetClassName));
        coverages.add(coverageOfTarget);
        return coverageOfTarget;
    }

    @JsonCreator
    public CoverageCollection(
            @JsonProperty("targetClassName")        Set<String> targetClassNames) {
        this.targetClassNames = targetClassNames;
    }



    public void printCoverages(Granularity granularity){
        printCoverages(System.out, granularity);
    }

    public void printCoverages(Granularity granularity, boolean onlyCovered){
        for(CoverageOfTarget cov : getCoverages()){
            cov.printCoverage(System.out, granularity);
        }
    }

    public void printCoverages(PrintStream out, Granularity granularity){
        for(CoverageOfTarget cov : getCoverages()){
            cov.printCoverage(out, granularity);
        }
    }

    public void generateJson(PrintStream out) throws JsonProcessingException {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        out.println(new ObjectMapper().writer(printer).writeValueAsString(this));
    }

    public boolean isContainsTargetClass(String targetClassName){
        return targetClassNames.contains(targetClassName);
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
}
