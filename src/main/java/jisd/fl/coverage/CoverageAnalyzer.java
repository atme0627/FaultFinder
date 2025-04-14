package jisd.fl.coverage;

import jisd.fl.util.*;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
        targetClassNames = StaticAnalyzer.getClassNames();
    }

    public CoverageCollection analyzeAll(String testClassName){
        FileUtil.createDirectory(jacocoExecFilePath);
        Set<String> failedTestsInClass = new HashSet<>();
        if(failedTests != null) {
            failedTestsInClass = failedTests
                    .stream()
                    .filter((ft) -> ft.split("#")[0].equals(testClassName))
                    .collect(Collectors.toSet());
        }

        Set<String> testMethodNames = TestUtil.getTestMethods(testClassName);
        if(testMethodNames.isEmpty()) throw new RuntimeException("test method is not found. [CLASS] " + testMethodNames);

        //テストクラスをコンパイル
        TestUtil.compileForDebug(testClassName);
        MyCoverageVisiter cv = new MyCoverageVisiter(testClassName, targetClassNames);
        int failedCount = 0;
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
                if(!isTestPassed) failedCount++;
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

        if(failedTests != null){
            if (failedTestsInClass.size() != failedCount) throw  new RuntimeException("failed test count is not correct.");
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

