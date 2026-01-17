package jisd.fl.sbfl.coverage;

import jisd.fl.infra.jacoco.JacocoTestUtil;
import jisd.fl.infra.javaparser.JavaParserClassNameExtractor;
import jisd.fl.util.*;
import jisd.fl.core.entity.MethodElementName;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    String jacocoExecFilePath = "./.jacoco_exec_data";
    Set<String> targetClassNames;
    Set<MethodElementName> failedTests;
    MyCoverageVisitor visitor;

    public CoverageAnalyzer(){
        this(new HashSet<>());
    }

    public CoverageAnalyzer(Set<MethodElementName> failedTests) {
        this.failedTests = failedTests;
        targetClassNames = JavaParserClassNameExtractor.getClassNames();
        visitor = new MyCoverageVisitor(targetClassNames);
    }

    // 1) 結果を格納するレコードクラス
    record TestResult(MethodElementName methodName,
                      String execFileName,
                      boolean passed) {}

    @Deprecated
    public void analyze(String tmp){
        analyze(new MethodElementName(tmp));
    }

    public void analyze(MethodElementName testClassName){
        FileUtil.createDirectory(jacocoExecFilePath);
        Set<MethodElementName> testMethodNames = JacocoTestUtil.getTestMethods(testClassName);
        if(testMethodNames.isEmpty()) throw new RuntimeException("test method is not found. [CLASS] " + testMethodNames);

        //固定長スレッドプール
        List<TestResult> results;
        try(ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<TestResult>> futures = testMethodNames.stream()
                    .map(testMethodName -> executor.submit(() -> {
                        String jacocoExecName = testMethodName + ".jacocoexec";
                        boolean isTestPassed = JacocoTestUtil.execTestCaseWithJacocoAgent(testMethodName, jacocoExecName);
                        return new TestResult(testMethodName, jacocoExecName, isTestPassed);
                    }))
                    .toList();

            results = futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        }

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
    }

    //TODO: 複数テストクラスを計測した際の挙動をテスト
    public CoverageCollection result(){
        return visitor.getCoverages();
    }

    private long failedTestsCountInClass(MethodElementName testClassName){
        return failedTests
                .stream()
                .filter((ft) -> ft.getFullyQualifiedClassName().equals(testClassName.getFullyQualifiedClassName()))
                .count();
    }

    private void validateTestResult(MethodElementName testMethodName, boolean isTestPassed){
        if(failedTests.isEmpty()) return;
        if((isTestPassed && failedTests.contains(testMethodName))
                || (!isTestPassed && !failedTests.contains(testMethodName))){
            throw new RuntimeException("Execution result is wrong. [testcase] " + testMethodName);
        }
    }

    private void validateFailedTestCount(MethodElementName testClassName, int failedCount){
        if(failedTests.isEmpty()) return;
        if (failedTestsCountInClass(testClassName) != failedCount) {
            throw new RuntimeException("failed test count is not correct.");
        }
    }
}

