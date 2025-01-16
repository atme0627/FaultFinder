package jisd.fl.coverage;

import jisd.fl.util.*;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.*;
import java.util.Set;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    final String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");;
    String outputDir;
    Set<String> targetClassNames;
    Set<String> failedTests;

    public CoverageAnalyzer(){
        this("./.coverage_data");
    }

    public CoverageAnalyzer(String outputDir){
        this(outputDir, null);
    }

    public CoverageAnalyzer(String outputDir, Set<String> failedTests) {
        this.outputDir = outputDir;
        this.failedTests = failedTests;
        targetClassNames = StaticAnalyzer.getClassNames(targetSrcDir);
    }

    public CoverageCollection analyzeAll(String testClassName){
        FileUtil.createDirectory(jacocoExecFilePath);
        Set<String> testMethodNames = TestUtil.getTestMethods(testClassName);
        if(testMethodNames.isEmpty()) throw new RuntimeException("test method is not found. [CLASS] " + testMethodNames);

        //テストクラスをコンパイル
        TestUtil.compileTestClass(testClassName);
        MyCoverageVisiter cv = new MyCoverageVisiter(testClassName, targetClassNames);

        for(String testMethodName : testMethodNames){
            //execファイルの生成
            //テストケースをjacocoAgentつきで実行
            String jacocoExecName = testMethodName + ".jacocoexec";
            boolean isTestPassed = false;
            try {
                isTestPassed = TestUtil.execTestCaseWithJacocoAgent(testMethodName, jacocoExecName);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            //テストの成否が想定と一致しているか確認
            if(failedTests != null){
                if((isTestPassed && failedTests.contains(testMethodName))
                || (!isTestPassed && !failedTests.contains(testMethodName))){
                    throw new RuntimeException("Execution result is wrong. [testcase] " + testMethodName);
                }
            }

            cv.setTestsPassed(isTestPassed);
            try {
                ExecutionDataStore execData = JacocoUtil.execFileLoader(jacocoExecName);
                JacocoUtil.analyzeWithJacoco(execData, cv);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        FileUtil.deleteDirectory(new File(jacocoExecFilePath));
        return cv.getCoverages();
    }

    private boolean isCovDataExist(String coverageCollectionName){
        String covFileName = outputDir + "/" + coverageCollectionName + ".cov";
        File data = new File(covFileName);
        return data.exists();
    }
}

