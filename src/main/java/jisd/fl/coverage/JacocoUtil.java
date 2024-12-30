package jisd.fl.coverage;

import jisd.fl.util.PropertyLoader;
import jisd.fl.util.TestLauncherForJacocoAPI;
import org.apache.commons.lang3.tuple.Pair;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.ICoverageVisitor;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class JacocoUtil {


    //junit console launcherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり

    public static void analyzeWithJacoco(ExecutionDataStore executionData, ICoverageVisitor cv) throws IOException {
        final String targetBinDir = PropertyLoader.getProperty("targetBinDir");
        //jacocoによるテスト対象の解析
        final Analyzer analyzer = new Analyzer(executionData, cv);
        File classFilePath = new File(targetBinDir);
        analyzer.analyzeAll(classFilePath);
    }

    public static ExecutionDataStore execFileLoader(String testMethodName) throws IOException {
        final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
        File testDatafile = new File(jacocoExecFilePath + "/" + testMethodName);
        ExecFileLoader testFileLoader = new ExecFileLoader();
        testFileLoader.load(testDatafile);
        return testFileLoader.getExecutionDataStore();
    }

    public static class MemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> definitions = new HashMap<>();
        public void addDefinition(final String name, final byte[] bytes) {
            definitions.put(name, bytes);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            final byte[] bytes = definitions.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.loadClass(name, resolve);
        }
    }

    private InputStream getTargetClass(final String name) {
        final String resource = '/' + name.replace('.', '/') + ".class";
        return getClass().getResourceAsStream(resource);
    }

    public Pair<Boolean, ExecutionDataStore> execTestCaseWithJacocoAPI(String testMethodName) throws Exception {
        String testLauncherName = TestLauncherForJacocoAPI.class.getName();

        final IRuntime runtime = new LoggerRuntime();
        final Instrumenter instrumenter = new Instrumenter(runtime);
        InputStream originalTestLauncher = getTargetClass(testLauncherName);
        final byte[] instrumentedTestLauncher = instrumenter.instrument(originalTestLauncher, testLauncherName);
        originalTestLauncher.close();
        final RuntimeData data = new RuntimeData();
        runtime.startup(data);

        final MemoryClassLoader classLoader = new MemoryClassLoader();
        classLoader.addDefinition(testLauncherName, instrumentedTestLauncher);
        final Class<?> testLauncher = classLoader.loadClass(testLauncherName);
        final BooleanSupplier testLauncherInstance = (BooleanSupplier) testLauncher.getConstructor(String.class).newInstance(testMethodName);
        boolean isTestPassed = testLauncherInstance.getAsBoolean();

        final ExecutionDataStore executionData = new ExecutionDataStore();
        final SessionInfoStore sessionInfos = new SessionInfoStore();
        data.collect(executionData, sessionInfos, false);
        runtime.shutdown();
        return Pair.of(isTestPassed, executionData);
    }
}
