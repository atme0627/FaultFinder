package jisd.fl.usecase;

import jisd.fl.core.entity.element.ClassElementName;
import jisd.fl.core.util.PropertyLoader;
import jisd.fl.infra.jacoco.ClassFileCache;
import jisd.fl.infra.jacoco.ProjectSbflCoverage;
import jisd.fl.infra.jacoco.testexec.JacocoTestExecClient;
import jisd.fl.infra.jacoco.testexec.JacocoTestExecServerHandle;
import jisd.fl.core.entity.element.MethodElementName;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.ICoverageVisitor;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.*;
import java.util.List;

public class CoverageAnalyzer {
    ProjectSbflCoverage coverage;
    ClassFileCache cache;

    public CoverageAnalyzer(){
        this.coverage = new ProjectSbflCoverage();
        try {
            //カバレッジ取得対象のクラスファイルをロード
            this.cache = ClassFileCache.loadFromClassesDir(PropertyLoader.getTargetBinDir());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load class files from target directory: " + e.getMessage(), e);
        }
    }

    public ProjectSbflCoverage analyze(ClassElementName testClassName){
        try (var handle = JacocoTestExecServerHandle.startDefault(20000)) {
            JacocoTestExecClient client = handle.client();
            List<MethodElementName> testMethodNames = client.listTestMethods(testClassName);
            if(testMethodNames.isEmpty()) throw new RuntimeException("test method is not found. [CLASS] " + testMethodNames);

            for(MethodElementName testMethodName : testMethodNames) {
                JacocoTestExecClient.TestExecReply reply = client.runTest(testMethodName);
                byte[] coverageData = reply.execBytes();
                boolean passed = reply.passed();

                ExecutionDataStore execData = execDataFromBytes(coverageData);
                analyzeExecData(execData, (cc) -> coverage.accept(cc, passed));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return coverage;
    }

    private static ExecutionDataStore execDataFromBytes(byte[] execBytes) throws IOException {
        ExecFileLoader loader = new ExecFileLoader();
        try (InputStream in = new ByteArrayInputStream(execBytes)) {
            loader.load(in);
        }
        return loader.getExecutionDataStore();
    }

    private void analyzeExecData(ExecutionDataStore executionData, ICoverageVisitor cv) throws IOException {
        //jacocoによるテスト対象の解析
        final Analyzer analyzer = new Analyzer(executionData, cv);
        for(ExecutionData execData : executionData.getContents()) {
            byte[] classBytes = cache.get(execData.getName());
            if(classBytes != null) {
                analyzer.analyzeClass(classBytes, execData.getName());
            }
        }
    }
}

