package jisd.fl.coverage;

import jisd.fl.util.DirectoryUtil;
import jisd.fl.util.PropertyLoader;
import jisd.fl.util.StaticAnalyzer;
import jisd.fl.util.TestClassCompiler;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.File;
import java.io.IOException;
import java.util.Set;

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
    Set<String> targetClassNames;
    public CoverageAnalyzer() throws IOException {
        DirectoryUtil.initDirectory(jacocoExecFilePath);
        targetClassNames = StaticAnalyzer.getClassNames(targetSrcDir);
    }


    public CoveragesForTestSuite analyze(String testClassName) throws IOException, InterruptedException {

        Set<String> testMethodNames = StaticAnalyzer.getMethodNames(testSrcDir, testClassName);
        CoveragesForTestSuite coverages = new CoveragesForTestSuite(testClassName, targetClassNames);

        //テストクラスをコンパイル
        TestClassCompiler tcc = new TestClassCompiler();
        //TODO: 要修正
        tcc.compileTestClass(testSrcDir + "/" + testClassName.replace(".", "/") + ".java", targetBinDir);

        for(String testMethodName : testMethodNames){
            CoveragesForTestCase covForTestCase = analyzeCoveragesForTestCase(testMethodName);
            coverages.addCoveragesForTestCase(covForTestCase);
        }
        return coverages;
    }

    //testMethodNameはクラス名とともに入力 ex.) demo.SortTest#test1
    CoveragesForTestCase analyzeCoveragesForTestCase(String testMethodName) throws IOException, InterruptedException {
        String testClassName = testMethodName.split("#")[0];
        int exitValue = execTestMethod(testMethodName);
        boolean isTestPassed = (exitValue == 0);
        CoveragesForTestCase coverages = new CoveragesForTestCase(testClassName, targetClassNames, testMethodName);

        //ターゲットクラスの静的解析
        ExecutionDataStore executionData = execFileLoader(testMethodName);
        CoverageBuilder coverageBuilder = new CoverageBuilder();
        analyzeWithJacoco(executionData, coverageBuilder);

        //クラス毎のカバレッジの計測
        for (final IClassCoverage cc : coverageBuilder.getClasses()) {
            String targetClassName = cc.getName().replace("/", ".");
            //内部クラスなど、{targetClassName}.javaが存在しないクラスを弾く処理
            if(!targetClassNames.contains(targetClassName)) continue;
            CoverageOfTargetForTestCase lc = new CoverageOfTargetForTestCase(targetClassName, targetSrcDir, isTestPassed);
            lc.processCoverage(cc);
            coverages.putCoverageOfTarget(lc);
        }

        return coverages;
    }

    //junit console launcherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public int execTestMethod(String testMethodName) throws IOException, InterruptedException {
        String generatedFilePath = jacocoExecFilePath + "/" + testMethodName+ ".exec";

        String cmd = "java -javaagent:" + jacocoAgentPath + "=destfile=" + generatedFilePath +
                " -jar " + junitConsoleLauncherPath + " -cp " + targetBinDir + ":" +
                        compiledWithJunitFilePath + " --select-method " + testMethodName;


        //Junit Console Launcherの終了ステータスは、
        // 1: コンテナやテストが失敗
        // 2: テストが見つからないかつ--fail-if-no-testsが指定されている
        // 0: それ以外
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();

        //execファイルが生成されるまで待機
        while(true){
            File f = new File(generatedFilePath);
            if(f.exists()){
                break;
            }
        }
        //ファイルの生成が行われたことを出力
        System.out.println("Success to generate " + generatedFilePath + ".");

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

