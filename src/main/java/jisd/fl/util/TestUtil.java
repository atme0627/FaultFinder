package jisd.fl.util;

import jisd.debug.DebugResult;
import jisd.debug.Debugger;
import jisd.fl.util.analyze.MethodElementName;
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

public  class TestUtil {
    public static String getJVMMain(MethodElementName testMethod){
        compileForDebug(testMethod);
        return "jisd.fl.util.TestLauncher " + testMethod.getFullyQualifiedMethodName();
    }

    public static String getJVMOption(){
        return "-cp " + "./build/classes/java/main"
                + ":" + PropertyLoader.getDebugBinDir()
                + ":" + PropertyLoader.getJunitClassPaths();
    }

    //-gつきでコンパイル
    public static void compileForDebug(MethodElementName targetTestClass) {
        FileUtil.initDirectory(PropertyLoader.getDebugBinDir());
        String classpath = "locallib/junit-dependency/*";
        String sourcepath = PropertyLoader.getTargetSrcDir() + ":" + PropertyLoader.getTestSrcDir();
        String[] cmdArray = {
                "javac",
                "-g",
                "-cp", classpath,
                "-sourcepath", sourcepath,
                "-d", PropertyLoader.getDebugBinDir(),
                targetTestClass.getFilePath(true).toString()
        };
        try {
            ProcessBuilder pb = new ProcessBuilder(cmdArray);
            Process proc = pb.start();
            String line = null;
            try (var buf = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                while ((line = buf.readLine()) != null) System.err.println(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    //TestLauncherにjacoco agentをつけて起動
    //methodNameは次のように指定: org.example.order.OrderTests#test1(int a)
    //先にTestClassCompilerでテストクラスをjunitConsoleLauncherとともにコンパイルする必要がある
    //TODO: execファイルの生成に時間がかかりすぎるため、並列化の必要あり
    public static boolean execTestCaseWithJacocoAgent(MethodElementName testMethod, String execFileName) throws IOException, InterruptedException {
        final String jacocoAgentPath = PropertyLoader.getProperty("jacocoAgentPath");
        final String jacocoExecFilePath = PropertyLoader.getProperty("jacocoExecFilePath");
        final String debugBinDir = PropertyLoader.getDebugBinDir();
        final String junitClassPath = PropertyLoader.getJunitClassPaths();
        String generatedFilePath = jacocoExecFilePath + "/" + execFileName;

        //Junit Console Launcherの終了ステータスは、
        // 1: コンテナやテストが失敗
        // 2: テストが見つからないかつ--fail-if-no-testsが指定されている
        // 0: それ以外
        String cmd =
                "java -javaagent:" + jacocoAgentPath + "=destfile='" + generatedFilePath + "'" +
                " -cp " + "./build/classes/java/main"
                        + ":" + debugBinDir
                        + ":'" + junitClassPath + "'"
                + " jisd.fl.util.TestLauncher '" + testMethod.getFullyQualifiedMethodName() + "'";

        ProcessBuilder pb = new ProcessBuilder("zsh", "-ic", cmd);
        Process proc = pb.start();
        proc.waitFor();

        boolean DEBUG=false;
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

    public static Debugger testDebuggerFactory(MethodElementName testMethod){
        return testDebuggerFactory(testMethod, "");
    }

    public static Debugger testDebuggerFactory(MethodElementName testMethod, String option) {
        compileForDebug(testMethod);
        Debugger dbg;
        while(true) {
            try {
                dbg = new Debugger(
                          "jisd.fl.util.TestLauncher "
                                + testMethod.getFullyQualifiedMethodName(),
                        "-cp " + "./build/classes/java/main"
                                + ":" + PropertyLoader.getDebugBinDir()
                                + ":" + PropertyLoader.getJunitClassPaths()
                                + " " + option
                );

                break;
            } catch (RuntimeException e1) {
                System.err.println(e1);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e2) {
                    throw new RuntimeException(e2);
                }
            }
        }
        dbg.setSrcDir(PropertyLoader.getTargetSrcDir(), PropertyLoader.getTestSrcDir());
        DebugResult.setDefaultMaxRecordNoOfValue(500);
        return dbg;
    }

    public static Set<MethodElementName> getTestMethods(MethodElementName testMethodName){
        //テスト対象クラスの.classを含むディレクトリを動的にロード
        //テストクラスはコンパイル済みと仮定
        URL[] url;
        try {
            url = new URL[]{Paths.get(PropertyLoader.getDebugBinDir()).toUri().toURL()};
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        try (URLClassLoader testClassLoader = new URLClassLoader(url, Thread.currentThread().getContextClassLoader())){
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
                    .map(TestUtil::getFQMNName)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
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