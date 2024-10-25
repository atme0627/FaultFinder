package jisd.fl.coverage;

import jisd.fl.util.DirectoryUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static java.lang.System.exit;
import static java.nio.file.StandardWatchEventKinds.*;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    final String junitConsoleLauncherPath = PropertyLoader.getProperty("junitConsoleLauncherPath");
    final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
    final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    final String compiledWithJunitFilePath = PropertyLoader.getProperty("compiledWithJunitFilePath");
    final String testSrcDir = PropertyLoader.getProperty("testSrcDir");
    final String testBinDir = PropertyLoader.getProperty("testBinDir");
    final String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");
    final String targetBinDir = PropertyLoader.getProperty("targetBinDir");

    public CoverageAnalyzer(){
        DirectoryUtil.initDirectory(jacocoExecFilePath);
    }


    public Coverage<LineCoverage> analyzeLineCoverage(String testClassName) throws IOException, InterruptedException {
        Coverage<LineCoverage> coverages = new Coverage<>(testClassName, Granularity.LINE);
        Set<String> targetClassNames = new HashSet<>();
        ArrayList<String> testMethodNameList = StaticAnalyzer.getMethodNames(testSrcDir, testClassName);
        for(String testMethodName : testMethodNameList){
            CoverageForTestCase<LineCoverage> covForTestCase = analyzeLineCoverageForTestCase(testClassName + "#" + testMethodName);
            targetClassNames.addAll(covForTestCase.getTargetClassNames());
            coverages.putCoverage(testClassName + "#" + testMethodName, covForTestCase);
        }
        coverages.setTargetClassNames(targetClassNames);
        return coverages;
    }

    //testMethodNameはクラス名とともに入力 ex.) demo.SortTest#test1
    CoverageForTestCase<LineCoverage> analyzeLineCoverageForTestCase(String testMethodName) throws IOException, InterruptedException {
        String testClassName = testMethodName.split("#")[0];
        int exitValue = execTestMethod(testMethodName);
        boolean isTestPassed = (exitValue == 0);
        CoverageForTestCase<LineCoverage> coverages =
                new CoverageForTestCase<>(compiledWithJunitFilePath, testClassName, testMethodName, isTestPassed, Granularity.LINE);

        //ターゲットクラスの静的解析
        ExecutionDataStore executionData = execFileLoader(testMethodName);
        CoverageBuilder coverageBuilder = new CoverageBuilder();
        analyzeWithJacoco(executionData, coverageBuilder);

        //クラス毎のカバレッジの計測
        Set<String> targetClassNames = new HashSet<>();
        for (final IClassCoverage cc : coverageBuilder.getClasses()) {
            String targetClassName = cc.getName();
            LineCoverage lc = new LineCoverage(targetClassName, targetBinDir, cc.getFirstLine(), cc.getLastLine());
            lc.processCoverage(cc);
            coverages.putClassCoverage(lc);
            targetClassNames.add(lc.getTargetClassName());
        }
        coverages.setTargetClassNames(targetClassNames);
        return coverages;
    }

    CoverageForTestCase<MethodCoverage> analyzeMethodCoverageForTestCase(){
        return null;
    }

    CoverageForTestCase<ClassCoverage> analyzeClassCoverageForTestCase(){
        return null;
    }

    //junit console launcherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public int execTestMethod(String testMethodName) throws IOException, InterruptedException {
        String cmd = "java -javaagent:" + jacocoAgentPath + "=destfile=" + jacocoExecFilePath + "/"
                + testMethodName+ ".exec -jar " + junitConsoleLauncherPath + " -cp " +
                targetBinDir + ":" + compiledWithJunitFilePath + " --select-method " + testMethodName;



        //execファイルが生成されるまで待機するための処理
        WatchService watcher = null;
        WatchKey watchKey = null;
        try {
            watcher = FileSystems.getDefault().newWatchService();

            Watchable path = Paths.get(jacocoExecFilePath);
            path.register(watcher, ENTRY_CREATE);
        } catch (IOException e) {
            e.printStackTrace();
            exit(1);
        }

        //Junit Console Launcherの終了ステータスは、
        // 1: コンテナやテストが失敗
        // 2: テストが見つからないかつ--fail-if-no-testsが指定されている
        // 0: それ以外
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();

        //execファイルが生成されるまで待機
        try {
            watchKey = watcher.take();
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            exit(1);
        }

        //ファイルの生成が行われたことを出力
        for (WatchEvent<?> event : watchKey.pollEvents()) {
            Object context = event.context();
            System.out.println("Success to generate " + context + ".");
        }
        watchKey.reset();

        return proc.exitValue();
    }

    private void analyzeWithJacoco(ExecutionDataStore executionData, CoverageBuilder coverageBuilder) throws IOException {
        //jacocoによるテスト対象の解析
        final Analyzer analyzer = new Analyzer(executionData, coverageBuilder);
        File classFilePath = new File(targetBinDir);
        analyzer.analyzeAll(classFilePath);
    }

    private ExecutionDataStore execFileLoader(String testMethodName) throws IOException {
        File testDatafile = new File(jacocoExecFilePath + "/" + testMethodName + ".exec");
        ExecFileLoader testFileLoader = new ExecFileLoader();
        testFileLoader.load(testDatafile);
        return testFileLoader.getExecutionDataStore();
    }

}

