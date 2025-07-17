package jisd.fl.coverage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import jisd.fl.sbfl.SbflStatus;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jisd.fl.util.FileUtil;

//あるテストケースを実行したときの、ターゲットのクラスごとのカバレッジ (Tester)
public class CoverageCollection {

    protected final String coverageCollectionName;
    public Set<String> targetClassNames;

    //各クラスのカバレッジインスタンスを保持 (ターゲットクラス名) --> CoverageOfTarget
    private Map<String, CoverageOfTarget> coverages;

    @JsonCreator
    private CoverageCollection(@JsonProperty("coverageCollectionName") String coverageCollectionName){
        this.coverageCollectionName = coverageCollectionName;
    }

    public CoverageCollection(String coverageCollectionName, Set<String> targetClassNames) {
        this.coverageCollectionName = coverageCollectionName;
        this.targetClassNames = targetClassNames;
        coverages = new LinkedHashMap<>();
    }

    public Map<String, SbflStatus> getCoverageOfTarget(String targetClassName, Granularity granularity) {
        return getCoverages().get(targetClassName).getCoverage(granularity);
    }



    public void printCoverages(Granularity granularity){
        printCoverages(System.out, granularity);
    }

    public void printCoverages(Granularity granularity, boolean onlyCovered){
        for(CoverageOfTarget cov : getCoverages().values()){
            cov.printCoverage(System.out, granularity);
        }
    }

    public void printCoverages(PrintStream out, Granularity granularity){
        for(CoverageOfTarget cov : getCoverages().values()){
            cov.printCoverage(out, granularity);
        }
    }

    public void generateJson(PrintStream out) throws JsonProcessingException {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        out.println(new ObjectMapper().writer(printer).writeValueAsString(this));
    }

    public Set<String> getTargetClassNames() {
        return targetClassNames;
    }

    //実行されたターゲットクラスの集合を返す
    public Set<String> getExecutedClassNames(){
        return coverages.keySet();
    }

    public void putCoverageOfTarget(CoverageOfTarget covOfTarget) {
        String targetClassName = covOfTarget.getTargetClassName();
        boolean isEmpty = !getCoverages().containsKey(targetClassName);
        //coveragesにない、新しいtargetClassのカバレッジが追加されたとき
        if (isEmpty) {
            getCoverages().put(targetClassName, covOfTarget);
        }
        //すでにtargetClassのカバレッジがあるとき
        else {
            CoverageOfTarget existedCov = getCoverages().get(targetClassName);
            existedCov.combineCoverages(covOfTarget);
        }
    }

    public static CoverageCollection loadJson(String dir){
        File f = new File(dir);
        try {
            return new ObjectMapper().readValue(f, CoverageCollection.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isContainsTargetClass(String targetClassName){
        return targetClassNames.contains(targetClassName);
    }

    public void mergeCoverage(CoverageCollection newCov) {
        newCov.getCoverages().forEach((targetClassName, covOfTarget) -> {
            putCoverageOfTarget(covOfTarget);
        });
    }

    public void setCoverages(Map<String, CoverageOfTarget> coverages) {
        this.coverages = coverages;
    }

    public Map<String, CoverageOfTarget> getCoverages(){
        return coverages;
    }
}
