package jisd.fl.sbfl.coverage;

import jisd.fl.core.util.PropertyLoader;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.ICoverageVisitor;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class JacocoUtil {


    //junit console launcherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり

    public static void analyzeWithJacoco(ExecutionDataStore executionData, ICoverageVisitor cv) throws IOException {
        final String targetBinDir = PropertyLoader.getTargetBinDir().toString();
        //jacocoによるテスト対象の解析
        final Analyzer analyzer = new Analyzer(executionData, cv);
        File classFilePath = new File(targetBinDir);
        analyzer.analyzeAll(classFilePath);
    }

    public static ExecutionDataStore execDataFromBytes(byte[] execBytes) throws IOException {
        ExecFileLoader loader = new ExecFileLoader();
        try (InputStream in = new ByteArrayInputStream(execBytes)) {
            loader.load(in);
        }
        return loader.getExecutionDataStore();
    }

}
