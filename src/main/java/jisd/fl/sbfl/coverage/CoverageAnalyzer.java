package jisd.fl.sbfl.coverage;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.infra.jacoco.JacocoTestUtil;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import jisd.fl.infra.jacoco.exec.JacocoTestExecClient;
import jisd.fl.infra.jacoco.exec.JacocoTestExecServerHandle;
import jisd.fl.infra.javaparser.JavaParserClassNameExtractor;
import jisd.fl.util.*;
import jisd.fl.core.entity.element.MethodElementName;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    String jacocoExecFilePath = "./.jacoco_exec_data";
    Set<String> targetClassNames;
    Set<MethodElementName> failedTests;
    NewMyCoverageVisitor visitor;

    public CoverageAnalyzer(){
        this(new HashSet<>());
    }

    public CoverageAnalyzer(Set<MethodElementName> failedTests) {
        this.failedTests = failedTests;
        targetClassNames = JavaParserClassNameExtractor.getClassNames();
        visitor = new NewMyCoverageVisitor();
    }

    @Deprecated
    public void analyze(String tmp){
        analyze(new ClassElementName(tmp));
    }

    public void analyze(ClassElementName testClassName){
        FileUtil.createDirectory(jacocoExecFilePath);
        Set<MethodElementName> testMethodNames = JacocoTestUtil.getTestMethods(testClassName);
        if(testMethodNames.isEmpty()) throw new RuntimeException("test method is not found. [CLASS] " + testMethodNames);

        try (var handle = JacocoTestExecServerHandle.startDefault(20000)) {
            JacocoTestExecClient client = handle.client();
            for(MethodElementName testMethodName : testMethodNames) {
                JacocoTestExecClient.TestExecReply reply = client.runTest(testMethodName);
                byte[] coverageData = reply.execBytes();

                int failedCount = 0;
                //テストの成否が想定と一致しているか確認
                validateTestResult(testMethodName, reply.passed());
                //失敗テスト数カウント
                if (!reply.passed()) failedCount++;
                visitor.setTestsPassed(reply.passed());

                ExecutionDataStore execData = JacocoUtil.execDataFromBytes(coverageData);
                JacocoUtil.analyzeWithJacoco(execData, visitor);
                //失敗テストの実行回数が想定と一致しているか確認
                validateFailedTestCount(testClassName, failedCount);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //TODO: 複数テストクラスを計測した際の挙動をテスト
    public ProjectSbflCoverage result(){
        return visitor.getCoverages();
    }

    private long failedTestsCountInClass(ClassElementName testClassName){
        return failedTests
                .stream()
                .filter((ft) -> ft.fullyQualifiedClassName().equals(testClassName.fullyQualifiedClassName()))
                .count();
    }

    private void validateTestResult(MethodElementName testMethodName, boolean isTestPassed){
        if(failedTests.isEmpty()) return;
        if((isTestPassed && failedTests.contains(testMethodName))
                || (!isTestPassed && !failedTests.contains(testMethodName))){
            throw new RuntimeException("Execution result is wrong. [testcase] " + testMethodName);
        }
    }

    private void validateFailedTestCount(ClassElementName testClassName, int failedCount){
        if(failedTests.isEmpty()) return;
        if (failedTestsCountInClass(testClassName) != failedCount) {
            throw new RuntimeException("failed test count is not correct.");
        }
    }
}

