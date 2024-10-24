package jisd.fl.coverage;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    final String execDataPath = "./.jacoco_exec_data";

    final String jacocoAgentPath = "./locallib";
    final String junitStandaloneDir = "./locallib";
    final String junitStandaloneName = "junit-platform-console-standalone-1.10.0.jar";
    final String targetBinPath = "/Users/ezaki/IdeaProjects/proj4test/build/classes/java/main";
    final String testBinPath = "./.probe_test_classes";

    public CoverageAnalyzer(){
        Path p = Paths.get(execDataPath);
        //create dir
        if(!Files.exists(p)){
            try {
                Files.createDirectory(p);
            } catch (IOException e) {
                System.out.println();
            }
        }
    }

    //junit console launcherにjacoco agentをつけて起動
    //methodnameは次のように指定: org.example.order.OrderTests#test1
    public int execTestMethod(String testMethodName) throws IOException, InterruptedException {
        String cmd = "java -javaagent:" + jacocoAgentPath + "/jacocoagent.jar=destfile=" + execDataPath + "/"
        + testMethodName+ ".exec -jar " + junitStandaloneDir + "/" + junitStandaloneName + " -cp " +
                targetBinPath + ":" + testBinPath + " --select-method " + testMethodName;
        System.out.println(cmd);

        //Junit Console Launcherの終了ステータスは、
        // 1: コンテナやテストが失敗
        // 2: テストが見つからないかつ--fail-if-no-testsが指定されている
        // 0: それ以外
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        return proc.exitValue();
    }

    public CoverageForTestCase<LineCoverage> analyzeLineCoverage(String testMethodName) throws IOException, InterruptedException {
        String testClassName = testMethodName.split("#")[0];
        int exitValue = execTestMethod(testMethodName);
        boolean isTestPassed = (exitValue == 0);
        CoverageForTestCase<LineCoverage> coverages =
                new CoverageForTestCase<>(testBinPath, testClassName, testMethodName, isTestPassed, Granularity.LINE);

        //ターゲットクラスの静的解析
        ExecutionDataStore executionData = execFileLoader(testMethodName);
        CoverageBuilder coverageBuilder = new CoverageBuilder();
        analyzeWithJacoco(executionData, coverageBuilder);

        //クラス毎のカバレッジの計測
        for (final IClassCoverage cc : coverageBuilder.getClasses()) {
            String targetClassName = cc.getName();
            LineCoverage lc = new LineCoverage(targetClassName, targetBinPath, cc.getFirstLine(), cc.getLastLine());
            lc.processCoverage(cc);
            coverages.putClassCoverage(lc);
        }

        return coverages;
    }

    public CoverageForTestCase<MethodCoverage> analyzeMethodCoverage(){
        return null;
    }

    public CoverageForTestCase<ClassCoverage> analyzeClassCoverage(){
        return null;
    }

    private void analyzeWithJacoco(ExecutionDataStore executionData, CoverageBuilder coverageBuilder) throws IOException {
        //jacocoによるテスト対象の解析
        final Analyzer analyzer = new Analyzer(executionData, coverageBuilder);
        File classFilePath = new File(targetBinPath);
        analyzer.analyzeAll(classFilePath);
    }

    private ExecutionDataStore execFileLoader(String testMethodName) throws IOException {
        File testDatafile = new File(execDataPath + "/" + testMethodName + ".exec");
        ExecFileLoader testFileLoader = new ExecFileLoader();
        testFileLoader.load(testDatafile);
        return testFileLoader.getExecutionDataStore();
    }

}

