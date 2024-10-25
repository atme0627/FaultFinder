package jisd.fl.coverage;

import org.jacoco.core.analysis.ICounter;

import java.util.*;

//対象クラスのテストスイートによるカバレッジ
public class Coverage<T extends BaseCoverage> {
    protected final String testClassName;
    protected final Granularity granularity;

    //実行されたターゲットクラスの集合
    Set<String> targetClassNames = new HashSet<>();

    //ターゲットクラスの最初の行と最後の行を保持
    Map<String, int[]> targetClassFirstAndLastLineNum = new HashMap<>();

    //各テストケースのカバレッジインスタンスを保持 (メソッド名 ex.) demo.SortTest#test1) --> CoverageForTestCase
    final HashMap<String, CoverageForTestCase<T>> coverages = new HashMap<>();

    public Coverage(String testClassName, Granularity granularity) {
        this.testClassName = testClassName;
        this.granularity = granularity;
    }

    public void printCoverages(){
        System.out.println("Test class: " + testClassName);
        System.out.println();
        System.out.println("List of test cases");
        for(CoverageForTestCase<T> cov : coverages.values()){
            System.out.println(cov.testMethodName);
        }
        System.out.println("------------------------------------");
        System.out.println("Coverages");
        for(String targetClassName : targetClassNames){
            HashMap<String, HashMap<String, Integer>> covData = getCoverageData(targetClassName);
            System.out.println("Target class: " + targetClassName);
            switch (granularity) {
                case LINE:
                    int firstLine = targetClassFirstAndLastLineNum.get(targetClassName)[0];
                    int lastLine = targetClassFirstAndLastLineNum.get(targetClassName)[1];
                    for(int i = firstLine; i <= lastLine; i++){
                         ArrayList<String> lineCovData = new ArrayList<String>();
                         covData.get(Integer.toString(i)).forEach((k, v) -> {
                             String mark = (coverages.get(k).isPassed) ? "o" : "x";
                             if(v == ICounter.NOT_COVERED || v == ICounter.EMPTY){
                                 lineCovData.add(" ");
                             }
                             else{
                                 lineCovData.add(mark);
                             }
                         });
                        System.out.println(i + ": " + Arrays.toString(lineCovData.toArray()));
                    }
            }
        }

    }

    //テストケースすべてのカバレッジ (行 or メソッド or クラス) --> hashmap(testMethod --> カバレッジ情報)
    public HashMap<String, HashMap<String, Integer>> getCoverageData(String targetClassName){
        HashMap<String, HashMap<String, Integer>> covData = new HashMap<>();
        switch (granularity){
            case LINE:
                int firstLine = targetClassFirstAndLastLineNum.get(targetClassName)[0];
                int lastLine = targetClassFirstAndLastLineNum.get(targetClassName)[1];
                for(int i = firstLine; i <= lastLine; i++){
                    HashMap<String, Integer> map = new HashMap<>();
                    for(CoverageForTestCase<T> cov : coverages.values()){
                        map.put(cov.testMethodName, cov.getCoverages().get(targetClassName).getCoverage().get(Integer.toString(i)));
                    }
                    covData.put(Integer.toString(i), map);
                }
        }
        return covData;
    }





    public void putCoverage(String testMethodName, CoverageForTestCase<T> cov){
        coverages.put(testMethodName, cov);
    }

    public String getTestClassName() {
        return testClassName;
    }

    public Granularity getGranularity() {
        return granularity;
    }


    public HashMap<String, CoverageForTestCase<T>> getCoverages() {
        return coverages;
    }

    protected void setTargetClassNames(Set<String> targetClassNames) {
        this.targetClassNames = targetClassNames;
        if(granularity == Granularity.LINE){
            setTargetClassFirstAndLastLineNum();
        }
    }

    // T == LineCoverageの時のみ実行
    private void setTargetClassFirstAndLastLineNum(){
        Map<String, int[]> map = new HashMap<>();
        for(String targetClassName : targetClassNames){
            for(CoverageForTestCase<T> covForTest : coverages.values()){
                if(covForTest.getTargetClassNames().contains(targetClassName)){
                    T covOfTargetClass = covForTest.getCoverages().get(targetClassName);
                    map.put(targetClassName, new int[]{covOfTargetClass.targetClassFirstLine, covOfTargetClass.targetClassLastLine});
                    break;
                }
                throw new RuntimeException("Coverage of " + targetClassName + " not found.");
            }
        }
        this.targetClassFirstAndLastLineNum = map;
    }

    public Set<String> getTargetClassNames() {
        return targetClassNames;
    }
}
