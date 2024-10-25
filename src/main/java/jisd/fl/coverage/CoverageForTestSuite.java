package jisd.fl.coverage;

import org.jacoco.core.analysis.ICounter;

import java.util.*;

//対象クラスのテストスイートによるカバレッジ
public class CoverageForTestSuite {
    protected final String testClassName;
    protected final Granularity granularity;

    //実行されたターゲットクラスの集合
    Set<String> targetClassNames = new HashSet<>();

    //各テストケースのカバレッジインスタンスを保持 (メソッド名 ex.) demo.SortTest#test1) --> CoverageForTestCase
    final Map<String, CoverageForTestCase> coverages = new LinkedHashMap<>();

    public CoverageForTestSuite(String testClassName, Granularity granularity) {
        this.testClassName = testClassName;
        this.granularity = granularity;
    }

    public void printCoverages(Granularity granularity){
        System.out.println("Test class: " + testClassName);
        System.out.println();
        System.out.println("List of test cases");
        for(CoverageForTestCase cov : coverages.values()){
            System.out.println(cov.testMethodName);
        }
        System.out.println("------------------------------------");
        System.out.println("Coverages");
        for(String targetClassName : getTargetClassNames()){
            Map<String, Map<String, Integer>> allCov = getAllCoverageByElement(targetClassName, granularity);
            System.out.println("Target class: " + targetClassName);
            allCov.forEach((element, MapFromMethodToCov) ->{
                 ArrayList<String> covMarkForTestSuite = new ArrayList<>();
                 MapFromMethodToCov.forEach((methodName, data) -> {
                     String mark = (coverages.get(methodName).isPassed) ? "o" : "x";
                     if (data == ICounter.NOT_COVERED || data == ICounter.EMPTY) {
                         covMarkForTestSuite.add(" ");
                     } else {
                         covMarkForTestSuite.add(mark);
                     }
                 });
                System.out.println(element + ": " + Arrays.toString(covMarkForTestSuite.toArray()));
            });
        }
    }

    //テストケースすべてのカバレッジ (行 or メソッド or クラス) --> hashmap(testMethod --> カバレッジ情報)
    public Map<String, Map<String, Integer>> getAllCoverageByElement(String targetClassName, Granularity granularity) {
        Map<String, Map<String, Integer>> allCov = new LinkedHashMap<>();
        List<String> elements = getElementsOfTarget(targetClassName, granularity);
        for (String e : elements) {
            Map<String, Integer> map = new LinkedHashMap<>();
            for (CoverageForTestCase cov : coverages.values()) {
                map.put(cov.testMethodName, cov.getCoverageByElement(targetClassName, e, granularity));
            }
            allCov.put(e, map);
        }
        return allCov;
    }

    public void putCoverage(String testMethodName, CoverageForTestCase cov){
        coverages.put(testMethodName, cov);
    }

    public String getTestClassName() {
        return testClassName;
    }

    public Granularity getGranularity() {
        return granularity;
    }


    public Map<String, CoverageForTestCase> getCoverages() {
        return coverages;
    }


    //ターゲットのカバレッジの粒度に応じた単位の集合を返す ex.) line -> targetの行番号, method -> targetに含まれるmethod名, class -> class名
    private List<String> getElementsOfTarget(String targetClassName, Granularity granularity){
        List<String> elements = new ArrayList<>();

        //適当に1つCoverageForTestcaseを持ってくる
        //(coverageForTestCaseはすべてのターゲットクラスの情報を持っているはず)
        CoverageOfTarget covOfTargetClass = null;
        for(CoverageForTestCase covForTest : coverages.values()){
            covOfTargetClass = covForTest.getCoverages().get(targetClassName);
            break;
        }

        switch (granularity){
            case LINE:
                for(int i = covOfTargetClass.targetClassFirstLine; i <= covOfTargetClass.targetClassLastLine; i++) {
                    elements.add(Integer.toString(i));
                }
                break;
            case METHOD:
                elements = covOfTargetClass.targetMethodNames;
                break;
            case CLASS:
                elements.add(targetClassName);
                break;
        }
        return elements;
    }

    public Set<String> getTargetClassNames() {
        return targetClassNames;
    }

    public void setTargetClassNames(Set<String> targetClassNames) {
        this.targetClassNames = targetClassNames;
    }
}
