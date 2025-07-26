package jisd.fl.sbfl.coverage;

import jisd.fl.util.*;
import jisd.fl.util.analyze.MethodElementName;
import jisd.fl.util.analyze.StaticAnalyzer;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    final String targetSrcDir = PropertyLoader.getProperty("targetSrcDir");;
    String outputDir;
    Set<String> targetClassNames;
    Set<MethodElementName> failedTests;
    MyCoverageVisitor visitor;

    public CoverageAnalyzer(){
        this("./.coverage_data");
    }

    public CoverageAnalyzer(String outputDir){
        this(outputDir, null);
    }

    public CoverageAnalyzer(String outputDir, Set<String> failedTests) {
        this.outputDir = outputDir;
        this.failedTests = failedTests.stream().map(MethodElementName::new).collect(Collectors.toSet());
        targetClassNames = StaticAnalyzer.getClassNames();
        visitor = new MyCoverageVisitor(targetClassNames);
    }

    // 1) 結果を格納するレコードクラス
    record TestResult(MethodElementName methodName,
                      String execFileName,
                      boolean passed) {}

    public CoverageCollection analyzeAll(String tmp){
        MethodElementName testClassName = new MethodElementName(tmp);
        FileUtil.createDirectory(jacocoExecFilePath);
        Set<MethodElementName> testMethodNames = TestUtil.getTestMethods(testClassName);
        if(testMethodNames.isEmpty()) throw new RuntimeException("test method is not found. [CLASS] " + testMethodNames);
        TestUtil.compileForDebug(testClassName);

        // 並列にテストを実行して TestResult を集める
        List<TestResult> results = testMethodNames
                .parallelStream()
                .map(testMethodName -> {
                    try {
                        String jacocoExecName = testMethodName + ".jacocoexec";
                        boolean isTestPassed = TestUtil.execTestCaseWithJacocoAgent(testMethodName, jacocoExecName);
                        return new TestResult(testMethodName, jacocoExecName, isTestPassed);
                    } catch (IOException | InterruptedException e) {
                        throw new UncheckedIOException(new IOException(e));
                    }
                })
                .toList();

        int failedCount = 0;
        for (TestResult r : results) {
            //テストの成否が想定と一致しているか確認
            validateTestResult(r.methodName(), r.passed());
            //失敗テスト数カウント
            if(!r.passed()) failedCount++;
            visitor.setTestsPassed(r.passed());
            try {
                ExecutionDataStore execData = JacocoUtil.execFileLoader(r.execFileName());
                JacocoUtil.analyzeWithJacoco(execData, visitor);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        //失敗テストの実行回数が想定と一致しているか確認
        validateFailedTestCount(testClassName, failedCount);

        FileUtil.deleteDirectory(new File(jacocoExecFilePath));
        return visitor.getCoverages();
    }

    private long failedTestsCountInClass(MethodElementName testClassName){
        return failedTests
                .stream()
                .filter((ft) -> ft.getFullyQualifiedClassName().equals(testClassName.getFullyQualifiedClassName()))
                .count();
    }

    private void validateTestResult(MethodElementName testMethodName, boolean isTestPassed){
        if(failedTests == null) return;
        if((isTestPassed && failedTests.contains(testMethodName))
                || (!isTestPassed && !failedTests.contains(testMethodName))){
            throw new RuntimeException("Execution result is wrong. [testcase] " + testMethodName);
        }
    }

    private void validateFailedTestCount(MethodElementName testClassName, int failedCount){
        if(failedTests == null) return;
        if (failedTestsCountInClass(testClassName) != failedCount) {
            throw new RuntimeException("failed test count is not correct.");
        }
    }
}

