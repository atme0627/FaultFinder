package jisd.fl.sbfl.coverage;

import jisd.fl.infra.jacoco.ClassFileCache;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.ICoverageVisitor;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JacocoUtil {


    //junit console launcherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり

    public static void analyzeExecData(ExecutionDataStore executionData, ICoverageVisitor cv, ClassFileCache cache) throws IOException {
        //jacocoによるテスト対象の解析
        final Analyzer analyzer = new Analyzer(executionData, cv);
        for(ExecutionData execData : executionData.getContents()) {
            byte[] classBytes = cache.get(execData.getName());
            if(classBytes != null) {
                analyzer.analyzeClass(classBytes, execData.getName());
            }
        }
    }

    public static ExecutionDataStore execDataFromBytes(byte[] execBytes) throws IOException {
        ExecFileLoader loader = new ExecFileLoader();
        try (InputStream in = new ByteArrayInputStream(execBytes)) {
            loader.load(in);
        }
        return loader.getExecutionDataStore();
    }

}
