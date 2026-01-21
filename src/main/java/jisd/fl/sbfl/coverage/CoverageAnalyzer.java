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
import java.util.Set;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    String jacocoExecFilePath = "./.jacoco_exec_data";
    Set<String> targetClassNames;
    Set<MethodElementName> failedTests;
    NewMyCoverageVisitor visitor;

    public CoverageAnalyzer(){
        targetClassNames = JavaParserClassNameExtractor.getClassNames();
        visitor = new NewMyCoverageVisitor();
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
                visitor.setTestsPassed(reply.passed());

                ExecutionDataStore execData = JacocoUtil.execDataFromBytes(coverageData);
                JacocoUtil.analyzeWithJacoco(execData, visitor);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //TODO: 複数テストクラスを計測した際の挙動をテスト
    public ProjectSbflCoverage result(){
        return visitor.getCoverages();
    }
}

