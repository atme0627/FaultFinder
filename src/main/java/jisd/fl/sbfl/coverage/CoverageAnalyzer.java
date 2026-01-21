package jisd.fl.sbfl.coverage;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import jisd.fl.infra.jacoco.exec.JacocoTestExecClient;
import jisd.fl.infra.jacoco.exec.JacocoTestExecServerHandle;
import jisd.fl.core.entity.element.MethodElementName;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.*;
import java.util.List;

//テストケースを実行して、jacoco.execファイルを生成するクラス
public class CoverageAnalyzer {
    NewMyCoverageVisitor visitor;

    public CoverageAnalyzer(){
        visitor = new NewMyCoverageVisitor();
    }

    public void analyze(ClassElementName testClassName){
        try (var handle = JacocoTestExecServerHandle.startDefault(20000)) {
            JacocoTestExecClient client = handle.client();
            List<MethodElementName> testMethodNames = client.listTestMethods(testClassName);
            if(testMethodNames.isEmpty()) throw new RuntimeException("test method is not found. [CLASS] " + testMethodNames);

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

