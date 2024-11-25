package jisd.fl.util;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.ICoverageVisitor;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.File;
import java.io.IOException;

public class JacocoUtil {
    private static final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
    private static final String targetBinDir = PropertyLoader.getProperty("d4jTargetBinDir");

    //junit console launcherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり

    public static void analyzeWithJacoco(ExecutionDataStore executionData, ICoverageVisitor cv) throws IOException {
        //jacocoによるテスト対象の解析
        final Analyzer analyzer = new Analyzer(executionData, cv);
        File classFilePath = new File(targetBinDir);
        analyzer.analyzeAll(classFilePath);
    }

    public static ExecutionDataStore execFileLoader(String testMethodName) throws IOException {
        File testDatafile = new File(jacocoExecFilePath + "/" + testMethodName);
        ExecFileLoader testFileLoader = new ExecFileLoader();
        testFileLoader.load(testDatafile);
        return testFileLoader.getExecutionDataStore();
    }
}
