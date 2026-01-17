package jisd.fl.infra.jacoco;

import jisd.fl.core.entity.MethodElementName;
import jisd.fl.util.PropertyLoader;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public  class JacocoTestUtil {
    //TestLauncherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1(int a)
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public static boolean execTestCaseWithJacocoAgent(MethodElementName testMethod, String execFileName) throws IOException, InterruptedException {
        final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
        final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
        final String targetBinDir = PropertyLoader.getTargetBinDir();
        final String testBinDir = PropertyLoader.getTestBinDir();
        final String junitClassPath = PropertyLoader.getJunitClassPaths();
        String generatedFilePath = jacocoExecFilePath + "/" + execFileName;

        //Junit Console Launcherの終了ステータスは、
        // 1: コンテナやテストが失敗
        // 2: テストが見つからないかつ--fail-if-no-testsが指定されている
        // 0: それ以外
        String cmd =
                "java " +
                "--add-opens java.base/java.lang=ALL-UNNAMED " +
                "--add-opens java.base/java.lang.reflect=ALL-UNNAMED " +
                "-javaagent:" + jacocoAgentPath + "=destfile='" + generatedFilePath + "'" +
                " -cp " + "./build/classes/java/main"
                        + ":" + targetBinDir
                        + ":" + testBinDir
                        + ":'" + junitClassPath + "'"
                + " jisd.fl.infra.junit.JUnitTestLauncher '" + testMethod.getFullyQualifiedMethodName() + "'";

        ProcessBuilder pb = new ProcessBuilder("zsh", "-ic", cmd);
        Process proc = pb.start();
        proc.waitFor();

        boolean DEBUG=true;
        if(DEBUG) {
            String line = null;
            System.out.println("STDOUT---------------");
            try (var buf = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                while ((line = buf.readLine()) != null) System.out.println(line);
            }
            System.out.println("STDERR---------------");
            try (var buf = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                while ((line = buf.readLine()) != null) System.err.println(line);
            }
        }

        //ファイルの生成が行われたことを出力
        System.out.println("Success to generate " + generatedFilePath + ".");
        System.out.println("testResult " + (proc.exitValue() == 0 ? "o" : "x"));
        return proc.exitValue() == 0;
    }

    public static Set<MethodElementName> getTestMethods(MethodElementName testMethodName){
        //テスト対象クラスの.classを含むディレクトリを動的にロード
        //テストクラスはコンパイル済みと仮定
        URL[] url;
        try {
            url = new URL[]{Paths.get(PropertyLoader.getTestBinDir()).toUri().toURL()};
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader testClassLoader = new URLClassLoader(url, original)){
            //ClassLoaderを切り替え
            Thread.currentThread().setContextClassLoader(testClassLoader);
            //対象のテストクラスをロード
            Class<?> targetTestClass = testClassLoader.loadClass(testMethodName.getFullyQualifiedClassName());

            // JUnit Launcher API を使用してテストを検出
            DiscoverySelector selector = DiscoverySelectors.selectClass(targetTestClass);
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selector)
                    .build();

            Launcher launcher = LauncherFactory.create();
            TestPlan testPlan = launcher.discover(request);

            return testPlan.getRoots().stream()
                    .flatMap(root -> testPlan.getDescendants(root).stream())
                    .filter(TestIdentifier::isTest)
                    .map(JacocoTestUtil::getFQMNName)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static Optional<MethodElementName> getFQMNName(TestIdentifier id) {
        if(id.getSource().isEmpty()) return Optional.empty();
        MethodSource source = (MethodSource) id.getSource().get();
        String className = source.getClassName();
        String methodName = source.getMethodName();
        return Optional.of(new MethodElementName(className + "#" + methodName + "()"));
    }
}